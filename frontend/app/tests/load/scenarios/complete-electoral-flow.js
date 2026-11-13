/**
 * K6 Load Test: Complete Electoral Flow
 *
 * Simula el flujo electoral completo:
 * - 14 OBSERVERs suben 20 actas c/u (280 total) con fotos reales
 * - 5 COORDINATORs validan/rechazan/disputan (paralelo)
 * - 1 ADMIN ve resultados en tiempo real (dashboard polling)
 *
 * Ejecución local:
 *   k6 run scenarios/complete-electoral-flow.js \
 *     --vus 20 --duration 10m -e API_HOST=localhost:8080
 *
 * Con fotos reales (280 fotos × 250KB ≈ 70MB):
 * - Tiempo estimado: 10-12 min
 * - Network: fast 3G recomendado
 */

import http from 'k6/http';
import { check, sleep, group } from 'k6';
import { randomIntBetween } from 'https://jslib.k6.io/k6-utils/1.4.0/index.js';
import fs from 'k6/fs';

// ============================================================================
// CONFIGURACIÓN
// ============================================================================

const API_HOST = __ENV.API_HOST || 'localhost:8080';
const API_BASE = `http://${API_HOST}/api/v1`;

// Fotos disponibles en documents/pictures
// Formatos soportados: jpg, jpeg, png, webp
// Pasadas por run-local.sh como: PHOTO_LIST="foto1.jpg,foto2.png,foto3.webp"
const PHOTO_SAMPLES = (__ENV.PHOTO_LIST || '')
  .split(',')
  .map((f) => f.trim())
  .filter((f) => f.length > 0);

// Fallback: si no hay fotos, usar dummy (esperamos que run-local.sh las haya encontrado)
if (PHOTO_SAMPLES.length === 0) {
  console.warn(
    'No hay fotos reales. Usa: ./scripts/run-local.sh o PHOTO_LIST="..." k6 run ...'
  );
}

// Rango de VUs: 1-14 OBSERVER, 15-19 COORDINATOR, 20+ ADMIN
const OBSERVER_VU_START = 1;
const OBSERVER_VU_END = 14;
const COORDINATOR_VU_START = 15;
const COORDINATOR_VU_END = 19;
const ADMIN_VU_START = 20;

// ============================================================================
// STAGES: Rampup gradual para no saturar backend
// ============================================================================

export const options = {
  stages: [
    { duration: '30s', target: 14 }, // Rampup 14 OBSERVERs
    { duration: '4m', target: 14 },  // 14 OBSERVERs subiendo actas
    { duration: '30s', target: 19 }, // Rampup 5 COORDINATORs
    { duration: '2m', target: 19 },  // 5 COORDINATORs validando
    { duration: '1m', target: 20 },  // 1 ADMIN viendo dashboard
    { duration: '1m', target: 0 },   // Cooldown rampdown
  ],
  thresholds: {
    'http_req_duration': ['p(95)<2000', 'p(99)<3000'],
    'http_req_failed': ['rate<0.1'],
  },
};

// ============================================================================
// MAIN FUNCTION
// ============================================================================

export default function () {
  const vu = __VU;

  if (vu >= OBSERVER_VU_START && vu <= OBSERVER_VU_END) {
    observerUploadFlow(vu);
  } else if (vu >= COORDINATOR_VU_START && vu <= COORDINATOR_VU_END) {
    coordinatorValidateFlow(vu);
  } else if (vu >= ADMIN_VU_START) {
    adminDashboardFlow(vu);
  }
}

// ============================================================================
// OBSERVER FLOW: Login + Upload 20 actas c/u
// ============================================================================

function observerUploadFlow(vu) {
  group(`OBSERVER-${vu}: Login + Upload 20 actas`, () => {
    // 1. Login
    const loginRes = http.post(
      `${API_BASE}/auth/login`,
      JSON.stringify({
        email: `obs${vu}@electoral.ec`,
        password: 'SecurePass123!',
      }),
      { headers: { 'Content-Type': 'application/json' } }
    );

    check(loginRes, {
      'observer login 200': (r) => r.status === 200,
    });

    if (loginRes.status !== 200) {
      console.error(`Observer ${vu} login failed: ${loginRes.status}`);
      return;
    }

    const token = loginRes.json('accessToken');
    const headers = {
      Authorization: `Bearer ${token}`,
      'Content-Type': 'application/json',
    };

    // 2. Obtener JRVs asignadas
    const jrvsRes = http.get(`${API_BASE}/observer/jrvs`, { headers });
    check(jrvsRes, {
      'get jrvs 200': (r) => r.status === 200,
    });

    if (jrvsRes.status !== 200) {
      console.error(`Observer ${vu} get jrvs failed: ${jrvsRes.status}`);
      return;
    }

    const jrvs = jrvsRes.json('data');
    if (!jrvs || jrvs.length === 0) {
      console.warn(`Observer ${vu} has no JRVs assigned`);
      return;
    }

    // 3. Subir 20 actas
    for (let i = 0; i < 20; i++) {
      const jrv = jrvs[i % jrvs.length];
      uploadActaWithPhoto(headers, jrv.id, vu, i);

      // Pequeño delay para no saturar
      sleep(randomIntBetween(0.2, 0.5));
    }
  });
}

/**
 * Helper: Subir una acta con foto
 * Flujo: Borrador → Upload foto → Enviar
 */
function uploadActaWithPhoto(headers, jrvId, observerVu, actaIndex) {
  group(`Upload Acta #${actaIndex}`, () => {
    // 3a. Guardar borrador
    const borradorRes = http.post(
      `${API_BASE}/observer/actas/borrador`,
      JSON.stringify({
        jrvId,
        cargoId: 1,
        totalSufragantes: 300,
      }),
      { headers }
    );

    check(borradorRes, {
      'save draft 201': (r) => r.status === 201,
    });

    if (borradorRes.status !== 201) {
      console.error(
        `Observer ${observerVu} save draft failed: ${borradorRes.status}`
      );
      return;
    }

    const actaId = borradorRes.json('id');

    // 3b. Subir foto (leer del filesystem)
    let photoBase64;
    let mimeType = 'image/jpeg';

    if (PHOTO_SAMPLES.length > 0) {
      const photoFilename = PHOTO_SAMPLES[actaIndex % PHOTO_SAMPLES.length];
      const photoPath = `../../documents/pictures/${photoFilename}`;

      try {
        const photoData = fs.readFileSync(photoPath, 'b');

        // Detectar MIME type por extensión
        if (photoFilename.endsWith('.png')) {
          mimeType = 'image/png';
        } else if (photoFilename.endsWith('.webp')) {
          mimeType = 'image/webp';
        }
        // jpg, jpeg → image/jpeg (default)

        photoBase64 = http.file(photoData, photoFilename, mimeType);
      } catch (err) {
        console.warn(
          `Photo not found at ${photoPath}, using dummy: ${err.message}`
        );
        photoBase64 = generateDummyPhotoBase64();
      }
    } else {
      // Sin fotos disponibles, usar dummy
      photoBase64 = generateDummyPhotoBase64();
    }

    const imgRes = http.post(
      `${API_BASE}/observer/actas/${actaId}/imagen`,
      { image: photoBase64 },
      { headers }
    );

    check(imgRes, {
      'upload image 200': (r) => r.status === 200 || r.status === 201,
    });

    if (imgRes.status < 200 || imgRes.status >= 300) {
      console.error(`Observer ${observerVu} upload image failed: ${imgRes.status}`);
      return;
    }

    // 3c. Enviar acta completada
    const enviarRes = http.post(
      `${API_BASE}/observer/actas`,
      JSON.stringify({
        actaId,
        votos: [
          { candidatoId: 101, votos: 180 },
          { candidatoId: 102, votos: 100 },
          { blancos: 10, nulos: 10 },
        ],
      }),
      { headers }
    );

    check(enviarRes, {
      'send acta 201': (r) => r.status === 201,
    });

    if (enviarRes.status !== 201) {
      console.error(
        `Observer ${observerVu} send acta failed: ${enviarRes.status}`
      );
    }
  });
}

/**
 * Helper: Generar foto dummy en Base64 (JPEG mínimo)
 * Fallback si las fotos no existen
 */
function generateDummyPhotoBase64() {
  // JPEG mínimo válido (~100 bytes)
  return (
    '/9j/4AAQSkZJRgABAQEAYABgAAD/2wBDAAIBAQIBAQICAgICAgICAwUDAwwDAwwDAwwDAwwDAwwDAwwD' +
    'AwwDAwwDAwwDAwwDAwwDAwwDAwwDAwwDAwwDAwwDAwwDAwwDAwwDAwwDAwwDAwwDAwwDAwwDAwwDAwwDAwwD' +
    'AwwDAwwDAwwDAwwDAwwDAwwDAwwD/2wBDAQICAgICAwUDAwUDAwwDAwwDAwwDAwwDAwwDAwwDAwwDAwwD' +
    'AwwDAwwDAwwDAwwDAwwDAwwDAwwDAwwDAwwDAwwDAwwDAwwDAwwDAwwDAwwDAwwDAwwDAwwDAwwDAwwDAwwD' +
    'AwwDAwwDAwwDAwwDAwwDAwwDAwwDAwwDAwwDAwwDAwwDAwwDAwwDAf/AABEIAAQABAMBEQACEQEDEQH/' +
    'xAAZAAADAQEAAAAAAAAAAAAAAAABAgMEBf/EABsQAAEDBAMAAAAAAAAAAAAAAAEAAhEDEiExMUEi' +
    '/8QAFQEBAQAAAAAAAAAAAAAAAAAAAAX/xAAbEQEAAwEBAAAAAAAAAAAAAQAREiExQf/aAAwDAQAC' +
    'AQIDA/Cj+WokpK9RQQkpKSEkpKSglJSUkpJSSlJJSSUFBKSUkpJSUFBKSUkpJSSlJJSSUFBKSUkpJSSU' +
    'klBKSUkpJSUklBKSUkpJSUklBKSUkpJSUklBKSUkpJSUklBKSUkpJSUklBKSUkpJSUklBKSUkpJSSUkpJS' +
    'SlBKSUkpJSUklBKSUkpJSUklBKSUkpJSUklBKSUkpJSUklBKSUkpJSUklBKSUkpJSUklBKSUkpJSUklBKS' +
    'UkpJSUklBKSUkpJSUklBKSUkpJSUklBKSUkpJSUklBKSUkpJSUklBKSUkpJSUklBKSUkpJSUklBKSUkpJSU' +
    'klBKSUkpJSUklBKSUkpJSUklBKSUkpJSUklBKSUkpJSUklBKSUkpJSUklBKSUkpJSUklBKSUkpJSUklBKSU' +
    'kpJSUklBKSUkpJSUklBKSUkpJSUklBKSUkpJSUklBKSUkpJSUklBKSUkpJSUklBKSUkpJSUklBKSUkpJSUk' +
    'lBKSUkpJSUklBKSUkpJSUklBKSUkpJSUklBKSUkpJSUklBKSUkpJSUklBKSUkpJSUklBKSUkpJSUklBKSUk' +
    'pJSUklBKSUkpJSUklBKSUkpJSUklBKSUkpJSUklBKSUkpJSUklBKSUkpJSUklBKSUkpJSUklBKSUkpJSUklB' +
    'KSUkpJSUklBKSUkpJSUklBKSUkpJSUklBKSUkpJSUklBKSUkpJSUklBKSUkpJSUklBKSUkpJSUklBKSUkpJ' +
    'SUklBKSUkpJSUklBKSUkpJSUklBKSUkpJSUklBKSUkpJSUklBKSUkpJSUklBKSUkpJSUklBKSUkpJSUklBKSU' +
    'klBKSUkpJSUklBKSUkpJSUklBKSUkpJSUklBKSUkpJSUklBKSUkpJSUklBKSUkpJSUklBKSUkpJSUklBKSUk' +
    'pJSUklBKSUkpJSUklBKSUkpJSUklBKSUkpJSUklBKSUkpJSUklBKSUkpJSUklBKSUkpJSUklBKSUkpJSUklB' +
    'KSUkpJSUklBKSUkpJSUklBKSUkpJSUklBKSUkpJSUklBKSUkpJSUklBKSUkpJSUklBKSUkpJSUklBKSUkpJ' +
    'SUklBKSUkpJSUklBKSUkpJSUklBKSUkpJSUklBKSUkpJSUklBKSUkpJSUklBKSUkpJSUklBKSUkpJSUklBKSU' +
    'klBKSUkpJSUklBKSUkpJSUklBKSUkpJSUklBKSUkpJSUklBKSUkpJSUklBKSUkpJSUklBKSUkpJSUklBKSUk' +
    'pJSUklBKSUkpJSUklBKSUkpJSUklBKSUkpJSUklBKSUkpJSUklBKSUkpJSUklBKSUkpJSUklBKSUkpJSUklB' +
    'KSUkpJSUklBKSUkpJSUklBKSUkpJSUklBKSUkpJSUklBKSUkpJSUklBKSUkpJSUklBKSUkpJSUklBKSUkpJ' +
    'SUklBKSUkpJSUklBKSUkpJSUklBKSUkpJSUklBKSUkpJSUklBKSUkpJSUklBKSUkpJSUklBKSUkpJSUklBKSU' +
    'klBKSUkpJSUklBKSUkpJSUklBKSUkpJSUklBKSUkpJSUklBKSUkpJSUklBKSUkpJSUklBKSUkpJSUklBKSUk' +
    'pJSUklBKSUkpJSUklBKSUkpJSUklBKSUkpJSUklBKSUkpJSUklBKSUkpJSUklBKSUkpJSUklBKSUkpJSUklB' +
    'KSUkpJSUklBKSUkpJSUklBKSUkpJSUklBKSUkpJSUklBKSUkpJSUklBKSUkpJSUklBKSUkpJSUklBKSUkpJ' +
    'SUklBKSUkpJSUklBKSUkpJSUklBKSUkpJSUklBKSUkpJSUklBKSUkpJSUklBKSUkpJSUklBKSUkpJSUklBKSU' +
    'klBKSUkpJSUklBKSUkpJSUklBKSUkpJSUklBKSUkpJSUklBKSUkpJSUklBKSUkpJSUklBKSUkpJSUklBKSUk' +
    'pJSUklBKSUkpJSUklBKSUkpJSUklBKSUkpJSUklBKSUkpJSUklBKSUkpJSUklBKSUkpJSUklBKSUkpJSUklB' +
    'KSUkpJSUklBKSUkpJSUklBKSUkpJSUklBKSUkpJSUklBKSUkpJSUklBKSUkpJSUklBKSUkpJSUklBKSUkpJ' +
    'SUklBKSUkpJSUklBKSUkpJSUklBKSUkpJSUklBKSUkpJSUklBKSUkpJSUklBKSUkpJSUklBKSUkpJSUklBKSU' +
    'klBKSUkpJSUklBKSUkpJSUklBKSUkpJSUklBKSUkpJSUklBKSUkpJSUklBKSUkpJSUklBKSUkpJSUklBKSUk' +
    'pJSUklBKSUkpJSUklBKSUkpJSUklBKSUkpJSUklBKSUkpJSUklBKSUkpJSUklBKSUkpJSUklBKSUkpJSUklB' +
    'KSUkpJSUklBKSUkpJSUklBKSUkpJSUklBKSUkpJSUklBKSUkpJSUklBKSUkpJSUklBKSUkpJSUklBKSUkpJ' +
    'SUklBKSUkpJSUklBKSUkpJSUklBKSUkpJSUklBKSUkpJSUklBKSUkpJSUklBKSUkpJSUklBKSUkpJSUklBKSU' +
    'klBKSUkpJSUklBKSUkpJSUklBKSUkpJSUklBKSUkpJSUklBKSUkpJSUklBKSUkpJSUklBKSUkpJSUklBKSUk' +
    'pJSUklBKSUkpJSUklBKSUkpJSUklBKSUkpJSUklBKSUkpJSUklBKSUkpJSUklBKSUkpJSUklBKSUkpJSUklB' +
    'KSUkpJSUklBKSUkpJSUklBKSUkpJSUklBKSUkpJSUklBKSUkpJSUklBKSUkpJSUklBKSUkpJSUklBKSUkpJ' +
    'SUklBKSUkpJSUklBKSUkpJSUklBKSUkpJSUklBKSUkpJSUklBKSUkpJSUklBKSUkpJSUklBKSUkpJSUklBKSU' +
    'klBKSUkpJSUklBKSUkpJSUklBKSUkpJSUklBKSUkpJSUklBKSUkpJSUklBKSUkpJSUklBKSUkpJSUklBKSUk' +
    'pJSUklBKSUkpJSUklBKSUkpJSUklBKSUkpJSUklBKSUkpJSUklBKSUkpJSUklBKSUkpJSUklBKSUkpJSUklB' +
    'KSUkpJSUklBKSUkpJSUklBKSUkpJSUklBKSUkpJSUklBKSUkpJSUklBKSUkpJSUklBKSUkpJSUklBKSUkpJ' +
    'SUklBKSUkpJSUklBKSUkpJSUklBKSUkpJSUklBKSUkpJSUklBKSUkpJSUklBKSUkpJSUklBKSUkpJSUklBKSU' +
    'klBKSUkpJSUklBKSUkpJSUklBKSUkpJSUklBKSUkpJSUklBKSUkpJSUklBKSUkpJSUklBKSUkpJSUklBKSUk' +
    'pJSUklBKSUkpJSUklBKSUkpJSUklBKSUkpJSUklBKSUkpJSUklBKSUkpJSUklBKSUkpJSUklBKSUkpJSUklB' +
    'KSUkpJSUklBKSUkpJSUklBKSUkpJSUklBKSUkpJSUklBKSUkpJSUklBKSUkpJSUklBKSUkpJSUklBKSUkpJ' +
    'SUklBKSUkpJSUklBKSUkpJSUklBKSUkpJSUklBKSUkpJSUklBKSUkpJSUklBKSUkpJSUklBKSUkpJSUklBKSU' +
    'klBKSUkpJSUklBKSUkpJSUklBKSUkpJSUklBKSUkpJSUklBKSUkpJSUklBKSUkpJSUklBKSUkpJSUklBKSUk' +
    'pJSUklBKSUkpJSUklBKSUkpJSUklBKSUkpJSUklBKSUkpJSUklBKSUkpJSUklBKSUkpJSUklBKSUkpJSUklB' +
    'KSUkpJSUklBKSUkpJSUklBKSUkpJSUklBKSUkpJSUklBKSUkpJSUklBKSUkpJSUklBKSUkpJSUklBKSUkpJ' +
    'SUklBKSUkpJSUklBKSUkpJSUklBKSUkpJSUklBKSUkpJSUklBKSUkpJSUklBKSUkpJSUklBKSUkpJSUklBKSU' +
    'klBKSUkpJSUklBKSUkpJSUklBKSUkpJSUklBKSUkpJSUklBKSUkpJSUklBKSUkpJSUklBKSUkpJSUklBKSUk' +
    'pJSUklBKSUkpJSUklBKSUkpJSUklBKSUkpJSUklBKSUkpJSUklBKSUkpJSUklBKSUkpJSUklBKSUkpJSUklB' +
    'KSUkpJSUklBKSUkpJSUklBKSUkpJSUklBKSUkpJSUklBKSUkpJSUklBKSUkpJSUklBKSUkpJSUklBKSUkpJ' +
    'SUklBKSUkpJSUklBKSUkpJSUklBKSUkpJSUklBKSUkpJSUklBKSUkpJSUklBKSUkpJSUklBKSUkpJSUklBKSU' +
    'klBKSUkpJSUklBKSUkpJSUklBKSUkpJSUklBKSUkpJSUklBKSUkpJSUklBKSUkpJSUklBKSUkpJSUklBKSUk' +
    'pJSUklBKSUkpJSUklBKSUkpJSUklBKSUkpJSUklBKSUkpJSUklBKSUkpJSUklBKSUkpJSUklBKSUkpJSUklB' +
    'KSUkpJSUklBKSUkpJSUklBKSUkpJSUklBKSUkpJSUklBKSUkpJSUklBKSUkpJSUklBKSUkpJSUklBKSUkpJ' +
    'SUklBKSUkpJSUklBKSUkpJSUklBKSUkpJSUklBKSUkpJSUklBKSUkpJSUklBKSUkpJSUklBKSUkpJSUklBKSU' +
    'klBKSUkpJSUklBKSUkpJSUklBKSUkpJSUklBKSUkpJSUklBKSUkpJSUklBKSUkpJSUklBKSUkpJSUklBKSUk' +
    'pJSUklBKSUkpJSUklBKSUkpJSUklBKSUkpJSUklBKSUkpJSUklBKSUkpJSUklBKSUkpJSUklBKSUkpJSUklB' +
    'KSUkpJSUklBKSUkpJSUklBKSUkpJSUklBKSUkpJSUklBKSUkpJSUklBKSUkpJSUklBKSUkpJSUklBKSUkpJ' +
    'SUklBKSUkpJSUklBKSUkpJSUklBKSUkpJSUklBKSUkpJSUklBKSUkpJSUklBKSUkpJSUklBKSUkpJSUklBKSU' +
    'klBKSUkpJSUklBKSUkpJSUklBKSUkpJSUklBKSUkpJSUklBKSUkpJSUklBKSUkpJSUklBKSUkpJSUklBKSUk' +
    'pJSUklBKSUkpJSUklBKSUkpJSUklBKSUkpJSUklBKSUkpJSUklBKSUkpJSUklBKSUkpJSUklBKSUkpJSUklB' +
    'KSUkpJSUklBKSUkpJSUklBKSUkpJSUklBKSUkpJSUklBKSUkpJSUklBKSUkpJSUklBKSUkpJSUklBKSUkpJ' +
    'SUklBKSUkpJSUklBKSUkpJSUklBKSUkpJSUklBKSUkpJSUklBKSUkpJSUklBKSUkpJSUklBKSUkpJSUklBKSU' +
    'klBKSUkpJSUklBKSUkpJSUklBKSUkpJSUklBKSUkpJSUklBKSUkpJSUklBKSUkpJSUklBKSUkpJSUklBKSUk' +
    'pJSUklBKSUkpJSUklBKSUkpJSUklBKSUkpJSUklBKSUkpJSUklBKSUkpJSUklBKSUkpJSUklBKSUkpJSUklB' +
    'KSUkpJSUklBKSUkpJSUklBKSUkpJSUklBKSUkpJSUklBKSUkpJSUklBKSUkpJSUklBKSUkpJSUklBKSUkpJ' +
    'SUklBKSUkpJSUklBKSUkpJSUklBKSUkpJSUklBKSUkpJSUklBKSUkpJSUklBKSUkpJSUklBKSUkpJSUklBKSU' +
    'klBKSUkpJSUklBKSUkpJSUklBKSUkpJSUklBKSUkpJSUklBKSUkpJSUklBKSUkpJSUklBKSUkpJSUklBKSUk' +
    'pJSUklBKSUkpJSUklBKSUkpJSUklBKSUkpJSUklBKSUkpJSUklBKSUkpJSUklBKSUkpJSUklBKSUkpJSUklB' +
    'KSUkpJSUklBKSUkpJSUklBKSUkpJSUklBKSUkpJSUklBKSUkpJSUklBKSUkpJSUklBKSUkpJSUklBKSUkpJ' +
    'SUklBKSUkpJSUklBKSUkpJSUklBKSUkpJSUklBKSUkpJSUklBKSUkpJSUklBKSUkpJSUklBKSUkpJSUklBKSU' +
    'klBKSUkpJSUklBKSUkpJSUklBKSUkpJSUklBKSUkpJSUklBKSUkpJSUklBKSUkpJSUklBKSUkpJSUklBKSUk' +
    'pJSUklBKSUkpJSUklBKSUkpJSUklBKSUkpJSUklBKSUkpJSUklBKSUkpJSUklBKSUkpJSUklBKSUkpJSUklB' +
    'KSUkpJSUklBKSUkpJSUklBKSUkpJSUklBKSUkpJSUklBKSUkpJSUklBKSUkpJSUklBKSUkpJSUklBKSUkpJ' +
    'SUklBKSUkpJSUklBKSUkpJSUklBKSUkpJSUklBKSUkpJSUklBKSUkpJSUklBKSUkpJSUklBKSUkpJSUklBKSU' +
    'klBKSUkpJSUklBKSUkpJSUklBKSUkpJSUklBKSUkpJSUklBKSUkpJSUklBKSUkpJSUklBKSUkpJSUklBKSUk' +
    'pJSUklBKSUkpJSUklBKSUkpJSUklBKSUkpJSUklBKSUkpJSUklBKSUkpJSUklBKSUkpJSUklBKSUkpJSUklB' +
    'KSUkpJSUklBKSUkpJSUklBKSUkpJSUklBKSUkpJSUklBKSUkpJSUklBKSUkpJSUklBKSUkpJSUklBKSUkpJ' +
    'SUklBKSUkpJSUklBKSUkpJSUklBKSUkpJSUklBKSUkpJSUklBKSUkpJSUklBKSUkpJSUklBKSUkpJSUklBKSU' +
    'klBKSUkpJSUklBKSUkpJSUklBKSUkpJSUklBKSUkpJSUklBKSUkpJSUklBKSUkpJSUklBKSUkpJSUklBKSUk' +
    'pJSUklBKSUkpJSUklBKSUkpJSUklBKSUkpJSUklBKSUkpJSUklBKSUkpJSUklBKSUkpJSUklBKSUkpJSUklB' +
    'KSUkpJSUklBKSUkpJSUklBKSUkpJSUklBKSUkpJSUklBKSUkpJSUklBKSUkpJSUklBKSUkpJSUklBKSUkpJ' +
    'SUklBKSUkpJSUklBKSUkpJSUklBKSUkpJSUklBKSUkpJSUklBKSUkpJSUklBKSUkpJSUklBKSUkpJSUklBKSU' +
    'klBKSUkpJSUklBKSUkpJSUklBKSUkpJSUklBKSUkpJSUklBKSUkpJSUklBKSUkpJSUklBKSUkpJSUklBKSUk' +
    'pJSUklBKSUkpJSUklBKSUkpJSUklBKSUkpJSUklBKSUkpJSUklBKSUkpJSUklBKSUkpJSUklBKSUkpJSUklB' +
    'KSUkpJSUklBKSUkpJSUklBKSUkpJSUklBKSUkpJSUklBKSUkpJSUklBKSUkpJSUklBKSUkpJSUklBKSUkpJ' +
    'SUklBKSUkpJSUklBKSUkpJSUklBKSUkpJSUklBKSUkpJSUklBKSUkpJSUklBKSUkpJSUklBKSUkpJSUklBKSU' +
    'klBKSUkpJSUklBKSUkpJSUklBKSUkpJSUklBKSUkpJSUklBKSUkpJSUklBKSUkpJSUklBKSUkpJSUklBKSUk' +
    'pJSUklBKSUkpJSUklBKSUkpJSUklBKSUkpJSUklBKSUkpJSUklBKSUkpJSUklBKSUkpJSUklBKSUkpJSUklB' +
    'KSUkpJSUklBKSUkpJSUklBKSUkpJSUklBKSUkpJSUklBKSUkpJSUklBKSUkpJSUklBKSUkpJSUklBKSUkpJ' +
    'SUklBKSUkpJSUklBKSUkpJSUklBKSUkpJSUklBKSUkpJSUklBKSUkpJSUklBKSUkpJSUklBKSUkpJSUklBKSU' +
    'klBKSUkpJSUklBKSUkpJSUklBKSUkpJSUklBKSUkpJSUklBKSUkpJSUklBKSUkpJSUklBKSUkpJSUklBKSUk' +
    'pJSUklBKSUkpJSUklBKSUkpJSUklBKSUkpJSUklBKSUkpJSUklBKSUkpJSUklBKSUkpJSUklBKSUkpJSUklB' +
    'KSUkpJSUklBKSUkpJSUklBKSUkpJSUklBKSUkpJSUklBKSUkpJSUklBKSUkpJSUklBKSUkpJSUklBKSUkpJ' +
    'SUklBKSUkpJSUklBKSUkpJSUklBKSUkpJSUklBKSUkpJSUklBKSUkpJSUklBKSUkpJSUklBKSUkpJSUklBKSU' +
    'klBKSUkpJSUklBKSUkpJSUklBKSUkpJSUklBKSUkpJSUklBKSUkpJSUklBKSUkpJSUklBKSUkpJSUklBKSUk' +
    'pJSUklBKSUkpJSUklBKSUkpJSUklBKSUkpJSUklBKSUkpJSUklBKSUkpJSUklBKSUkpJSUklBKSUkpJSUklB' +
    'KSUkpJSUklBKSUkpJSUklBKSUkpJSUklBKSUkpJSUklBKSUkpJSUklBKSUkpJSUklBKSUkpJSUklBKSUkpJ' +
    'SUklBKSUkpJSUklBKSUkpJSUklBKSUkpJSUklBKSUkpJSUklBKSUkpJSUklBKSUkpJSUklBKSUkpJSUklBKSU' +
    'klBKSUkpJSUklBKSUkpJSUklBKSUkpJSUklBKSUkpJSUklBKSUkpJSUklBKSUkpJSUklBKSUkpJSUklBKSUk' +
    'pJSUklBKSUkpJSUklBKSUkpJSUklBKSUkpJSUklBKSUkpJSUklBKSUkpJSUklBKSUkpJSUklBKSUkpJSUklB' +
    'KSUkpJSUklBKSUkpJSUklBKSUkpJSUklBKSUkpJSUklBKSUkpJSUklBKSUkpJSUklBKSUkpJSUklBKSUkpJ' +
    'SUklBKSUkpJSUklBKSUkpJSUklBKSUkpJSUklBKSUkpJSUklBKSUkpJSUklBKSUkpJSUklBKSUkpJSUklBKSU' +
    'klBKSUkpJSUklBKSUkpJSUklBKSUkpJSUklBKSUkpJSUklBKSUkpJSUklBKSUkpJSUklBKSUkpJSUklBKSUk' +
    'pJSUklBKSUkpJSUklBKSUkpJSUklBKSUkpJSUklBKSUkpJSUklBKSUkpJSUklBKSUkpJSUklBKSUkpJSUklB' +
    'KSUkpJSUklBKSUkpJSUklBKSUkpJSUklBKSUkpJSUklBKSUkpJSUklBKSUkpJSUklBKSUkpJSUklBKSUkpJ' +
    'SUklBKSUkpJSUklBKSUkpJSUklBKSUkpJSUklBKSUkpJSUklBKSUkpJSUklBKSUkpJSUklBKSUkpJSUklBKSU' +
    'klBKSUkpJSUklBKSUkpJSUklBKSUkpJSUklBKSUkpJSUklBKSUkpJSUklBKSUkpJSUklBKSUkpJSUklBKSUk' +
    'pJSUklBKSUkpJSUklBKSUkpJSUklBKSUkpJSUklBKSUkpJSUklBKSUkpJSUklBKSUkpJSUklBKSUkpJSUklB' +
    'KSUkpJSUklBKSUkpJSUklBKSUkpJSUklBKSUkpJSUklBKSUkpJSUklBKSUkpJSUklBKSUkpJSUklBKSUkpJ' +
    'SUklBKSUkpJSUklBKSUkpJSUklBKSUkpJSUklBKSUkpJSUklBKSUkpJSUklBKSUkpJSUklBKSUkpJSUklBKSU' +
    'klBKSUkpJSUklBKSUkpJSUklBKSUkpJSUklBKSUkpJSUklBKSUkpJSUklBKSUkpJSUklBKSUkpJSUklBKSUk' +
    'pJSUklBKSUkpJSUklBKSUkpJSUklBKSUkpJSUklBKSUkpJSUklBKSUkpJSUklBKSUkpJSUklBKSUkpJSUklB' +
    'KSUkpJSUklBKSUkpJSUklBKSUkpJSUklBKSUkpJSUklBKSUkpJSUklBKSUkpJSUklBKSUkpJSUklBKSUkpJ' +
    'SUklBKSUkpJSUklBKSUkpJSUklBKSUkpJSUklBKSUkpJSUklBKSUkpJSUklBKSUkpJSUklBKSUkpJSUklBKSU' +
    'klBKSUkpJSUklBKSUkpJSUklBKSUkpJSUklBKSUkpJSUklBKSUkpJSUklBKSUkpJSUklBKSUkpJSUklBKSUk' +
    'pJSUklBKSUkpJSUklBKSUkpJSUklBKSUkpJSUklBKSUkpJSUklBKSUkpJSUklBKSUkpJSUklBKSUkpJSUklB' +
    'KSUkpJSUklBKSUkpJSUklBKSUkpJSUklBKSUkpJSUklBKSUkpJSUklBKSUkpJSUklBKSUkpJSUklBKSUkpJ' +
    'SUklBKSUkpJSUklBKSUkpJSUklBKSUkpJSUklBKSUkpJSUklBKSUkpJSUklBKSUkpJSUklBKSUkpJSUklBKSU' +
    'klBKSUkpJSUklBKSUkpJSUklBKSUkpJSUklBKSUkpJSUklBKSUkpJSUklBKSUkpJSUklBKSUkpJSUklBKSUk' +
    'pJSUklBKSUkpJSUklBKSUkpJSUklBKSUkpJSUklBKSUkpJSUklBKSUkpJSUklBKSUkpJSUklBKSUkpJSUklB' +
    'KSUkpJSUklBKSUkpJSUklBKSUkpJSUklBKSUkpJSUklBKSUkpJSUklBKSUkpJSUklBKSUkpJSUklBKSUkpJ' +
    'SUklBKSUkpJSUklBKSUkpJSUklBKSUkpJSUklBKSUkpJSUklBKSUkpJSUklBKSUkpJSUklBKSUkpJSUklBKSU' +
    'klBKSUkpJSUklBKSUkpJSUklBKSUkpJSUklBKSUkpJSUklBKSUkpJSUklBKSUkpJSUklBKSUkpJSUklBKSUk' +
    'pJSUklBKSUkpJSUklBKSUkpJSUklBKSUkpJSUklBKSUkpJSUklBKSUkpJSUklBKSUkpJSUklBKSUkpJSUklB' +
    'KSUkpJSUklBKSUkpJSUklBKSUkpJSUklBKSUkpJSUklBKSUkpJSUklBKSUkpJSUklBKSUkpJSUklBKSUkpJ' +
    'SUklBKSUkpJSUklBKSUkpJSUklBKSUkpJSUklBKSUkpJSUklBKSUkpJSUklBKSUkpJSUklBKSUkpJSUklBKSU' +
    'klBKSUkpJSUklBKSUkpJSUklBKSUkpJSUklBKSUkpJSUklBKSUkpJSUklBKSUkpJSUklBKSUkpJSUklBKSUk' +
    'pJSUklBKSUkpJSUklBKSUkpJSUklBKSUkpJSUklBKSUkpJSUklBKSUkpJSUklBKSUkpJSUklBKSUkpJSUklB' +
    'KSUkpJSUklBKSUkpJSUklBKSUkpJSUklBKSUkpJSUklBKSUkpJSUklBKSUkpJSUklBKSUkpJSUklBKSUkpJ' +
    'SUklBKSUkpJSUklBKSUkpJSUklBKSUkpJSUklBKSUkpJSUklBKSUkpJSUklBKSUkpJSUklBKSUkpJSUklBKSU' +
    'klBKSUkpJSUklBKSUkpJSUklBKSUkpJSUklBKSUkpJSUklBKSUkpJSUklBKSUkpJSUklBKSUkpJSUklBKSUk' +
    'pJSUklBKSUkpJSUklBKSUkpJSUklBKSUkpJSUklBKSUkpJSUklBKSUkpJSUklBKSUkpJSUklBKSUkpJSUklB' +
    'KSUkpJSUklBKSUkpJSUklBKSUkpJSUklBKSUkpJSUklBKSUkpJSUklBKSUkpJSUklBKSUkpJSUklBKSUkpJ' +
    'SUklBKSUkpJSUklBKSUkpJSUklBKSUkpJSUklBKSUkpJSUklBKSUkpJSUklBKSUkpJSUklBKSUkpJSUklBKSU' +
    'klBKSUkpJSUklBKSUkpJSUklBKSUkpJSUklBKSUkpJSUklBKSUkpJSUklBKSUkpJSUklBKSUkpJSUklBKSUk' +
    'pJSUklBKSUkpJSUklBKSUkpJSUklBKSUkpJSUklBKSUkpJSUklBKSUkpJSUklBKSUkpJSUklBKSUkpJSUklB' +
    'KSUkpJSUklBKSUkpJSUklBKSUkpJSUklBKSUkpJSUklBKSUkpJSUklBKSUkpJSUklBKSUkpJSUklBKSUkpJ' +
    'SUklBKSUkpJSUklBKSUkpJSUklBKSUkpJSUklBKSUkpJSUklBKSUkpJSUklBKSUkpJSUklBKSUkpJSUklBKSU' +
    'klBKSUkpJSUklBKSUkpJSUklBKSUkpJSUklBKSUkpJSUklBKSUkpJSUklBKSUkpJSUklBKSUkpJSUklBKSUk' +
    'pJSUklBKSUkpJSUklBKSUkpJSUklBKSUkpJSUklBKSUkpJSUklBKSUkpJSUklBKSUkpJSUklBKSUkpJSUklB' +
    'KSUkpJSUklBKSUkpJSUklBKSUkpJSUklBKSUkpJSUklBKSUkpJSUklBKSUkpJSUklBKSUkpJSUklBKSUkpJ' +
    'SUklBKSUkpJSUklBKSUkpJSUklBKSUkpJSUklBKSUkpJSUklBKSUkpJSUklBKSUkpJSUklBKSUkpJSUklBKSU' +
    'klBKSUkpJSUklBKSUkpJSUklBKSUkpJSUklBKSUkpJSUklBKSUkpJSUklBKSUkpJSUklBKSUkpJSUklBKSUk' +
    'pJSUklBKSUkpJSUklBKSUkpJSUklBKSUkpJSUklBKSUkpJSUklBKSUkpJSUklBKSUkpJSUklBKSUkpJSUklB' +
    'KSUkpJSUklBKSUkpJSUklBKSUkpJSUklBKSUkpJSUklBKSUkpJSUklBKSUkpJSUklBKSUkpJSUklBKSUkpJ' +
    'SUklBKSUkpJSUklBKSUkpJSUklBKSUkpJSUklBKSUkpJSUklBKSUkpJSUklBKSUkpJSUklBKSUkpJSUklBKSU' +
    'klBKSUkpJSUklBKSUkpJSUklBKSUkpJSUklBKSUkpJSUklBKSUkpJSUklBKSUkpJSUklBKSUkpJSUklBKSUk' +
    'pJSUklBKSUkpJSUklBKSUkpJSUklBKSUkpJSUklBKSUkpJSUklBKSUkpJSUklBKSUkpJSUklBKSUkpJSUklB' +
    'KSUkpJSUklBKSUkpJSUklBKSUkpJSUklBKSUkpJSUklBKSUkpJSUklBKSUkpJSUklBKSUkpJSUklBKSUkpJ' +
    'SUklBKSUkpJSUklBKSUkpJSUklBKSUkpJSUklBKSUkpJSUklBKSUkpJSUklBKSUkpJSUklBKSUkpJSUklBKSU' +
    'klBKSUkpJSUklBKSUkpJSUklBKSUkpJSUklBKSUkpJSUklBKSUkpJSUklBKSUkpJSUklBKSUkpJSUklBKSUk' +
    'pJSUklBKSUkpJSUklBKSUkpJSUklBKSUkpJSUklBKSUkpJSUklBKSUkpJSUklBKSUkpJSUklBKSUkpJSUklB' +
    'KSUkpJSUklBKSUkpJSUklBKSUkpJSUklBKSUkpJSUklBKSUkpJSUklBKSUkpJSUklBKSUkpJSUklBKSUkpJ' +
    'SUklBKSUkpJSUklBKSUkpJSUklBKSUkpJSUklBKSUkpJSUklBKSUkpJSUklBKSUkpJSUklBKSUkpJSUklBKSU' +
    'klBKSUkpJSUklBKSUkpJSUklBKSUkpJSUklBKSUkpJSUklBKSUkpJSUklBKSUkpJSUklBKSUkpJSUklBKSUk' +
    'pJSUklBKSUkpJSUklBKSUkpJSUklBKSUkpJSUklBKSUkpJSUklBKSUkpJSUklBKSUkpJSUklBKSUkpJSUklB' +
    'KSUkpJSUklBKSUkpJSUklBKSUkpJSUklBKSUkpJSUklBKSUkpJSUklBKSUkpJSUklBKSUkpJSUklBKSUkpJ' +
    'SUklBKSUkpJSUklBKSUkpJSUklBKSUkpJSUklBKSUkpJSUklBKSUkpJSUklBKSUkpJSUklBKSUkpJSUklBKSU' +
    'klBKSUkpJSUklBKSUkpJSUklBKSUkpJSUklBKSUkpJSUklBKSUkpJSUklBKSUkpJSUklBKSUkpJSUklBKSUk' +
    'pJSUklBKSUkpJSUklBKSUkpJSUklBKSUkpJSUklBKSUkpJSUklBKSUkpJSUklBKSUkpJSUklBKSUkpJSUklB' +
    'KSUkpJSUklBKSUkpJSUklBKSUkpJSUklBKSUkpJSUklBKSUkpJSUklBKSUkpJSUklBKSUkpJSUklBKSUkpJ' +
    'SUklBKSUkpJSUklBKSUkpJSUklBKSUkpJSUklBKSUkpJSUklBKSUkpJSUklBKSUkpJSUklBKSUkpJSUklBKSU' +
    'klBKSUkpJSUklBKSUkpJSUklBKSUkpJSUklBKSUkpJSUklBKSUkpJSUklBKSUkpJSUklBKSUkpJSUklBKSUk' +
    'pJSUklBKSUkpJSUklBKSUkpJSUklBKSUkpJSUklBKSUkpJSUklBKSUkpJSUklBKSUkpJSUklBKSUkpJSUklB' +
    'KSUkpJSUklBKSUkpJSUklBKSUkpJSUklBKSUkpJSUklBKSUkpJSUklBKSUkpJSUklBKSUkpJSUklBKSUkpJ' +
    'SUklBKSUkpJSUklBKSUkpJSUklBKSUkpJSUklBKSUkpJSUklBKSUkpJSUklBKSUkpJSUklBKSUkpJSUklBKSU' +
    'klBKSUkpJSUklBKSUkpJSUklBKSUkpJSUklBKSUkpJSUklBKSUkpJSUklBKSUkpJSUklBKSUkpJSUklBKSUk' +
    'pJSUklBKSUkpJSUklBKSUkpJSUklBKSUkpJSUklBKSUkpJSUklBKSUkpJSUklBKSUkpJSUklBKSUkpJSUklB' +
    'KSUkpJSUklBKSUkpJSUklBKSUkpJSUklBKSUkpJSUklBKSUkpJSUklBKSUkpJSUklBKSUkpJSUklBKSUkpJ' +
    'SUklBKSUkpJSUklBKSUkpJSUklBKSUkpJSUklBKSUkpJSUklBKSUkpJSUklBKSUkpJSUklBKSUkpJSUklBKSU' +
    'klBKSUkpJSUklBKSUkpJSUklBKSUkpJSUklBKSUkpJSUklBKSUkpJSUklBKSUkpJSUklBKSUkpJSUklBKSUk'
  );
}

// ============================================================================
// COORDINATOR FLOW: Validar actas (aprobaciones, rechazos, disputas)
// ============================================================================

function coordinatorValidateFlow(vu) {
  group(`COORDINATOR-${vu}: Login + Validate actas`, () => {
    // 1. Login
    const loginRes = http.post(
      `${API_BASE}/auth/login`,
      JSON.stringify({
        email: `coord${vu}@electoral.ec`,
        password: 'SecurePass123!',
      }),
      { headers: { 'Content-Type': 'application/json' } }
    );

    check(loginRes, {
      'coordinator login 200': (r) => r.status === 200,
    });

    if (loginRes.status !== 200) {
      console.error(`Coordinator ${vu} login failed: ${loginRes.status}`);
      return;
    }

    const token = loginRes.json('accessToken');
    const headers = {
      Authorization: `Bearer ${token}`,
      'Content-Type': 'application/json',
    };

    // 2. Obtener actas enviadas
    const actasRes = http.get(
      `${API_BASE}/coordinator/actas?estado=ENVIADA&limit=100`,
      { headers }
    );

    check(actasRes, {
      'get actas 200': (r) => r.status === 200,
    });

    if (actasRes.status !== 200) {
      console.error(`Coordinator ${vu} get actas failed: ${actasRes.status}`);
      return;
    }

    const actas = actasRes.json('data') || [];
    if (actas.length === 0) {
      console.warn(`Coordinator ${vu} has no actas to validate`);
      return;
    }

    // 3. Validar actas: 1/3 aprueban, 1/3 rechazan evidencia, 1/3 disputan
    actas.slice(0, 20).forEach((acta, idx) => {
      const action = idx % 3;

      if (action === 0) {
        // Aprobar
        group(`Approve acta ${acta.id}`, () => {
          const approveRes = http.patch(
            `${API_BASE}/coordinator/actas/${acta.id}/aprobar`,
            JSON.stringify({}),
            { headers }
          );
          check(approveRes, {
            'approve 200/204': (r) => r.status === 200 || r.status === 204,
          });
        });
      } else if (action === 1) {
        // Rechazar evidencia
        group(`Reject evidencia for acta ${acta.id}`, () => {
          if (!acta.evidenciaId) return;
          const rejectRes = http.patch(
            `${API_BASE}/evidencias/${acta.evidenciaId}/estado`,
            JSON.stringify({
              estado: 'RECHAZADA',
              observaciones: 'Imagen borrosa, acta ilegible',
            }),
            { headers }
          );
          check(rejectRes, {
            'reject 200/204': (r) => r.status === 200 || r.status === 204,
          });
        });
      } else {
        // Marcar en disputa
        group(`Dispute acta ${acta.id}`, () => {
          const disputeRes = http.post(
            `${API_BASE}/coordinator/actas/${acta.id}/disputa`,
            JSON.stringify({ motivo: 'Verificar datos de candidatos' }),
            { headers }
          );
          check(disputeRes, {
            'dispute 200/201': (r) => r.status === 200 || r.status === 201,
          });
        });
      }

      sleep(randomIntBetween(0.3, 0.8));
    });
  });
}

// ============================================================================
// ADMIN FLOW: Dashboard en tiempo real
// ============================================================================

function adminDashboardFlow(vu) {
  group(`ADMIN-${vu}: Login + Dashboard polling`, () => {
    // 1. Login
    const loginRes = http.post(
      `${API_BASE}/auth/login`,
      JSON.stringify({
        email: 'admin@electoral.ec',
        password: 'SecurePass123!',
      }),
      { headers: { 'Content-Type': 'application/json' } }
    );

    check(loginRes, {
      'admin login 200': (r) => r.status === 200,
    });

    if (loginRes.status !== 200) {
      console.error(`Admin login failed: ${loginRes.status}`);
      return;
    }

    const token = loginRes.json('accessToken');
    const headers = {
      Authorization: `Bearer ${token}`,
      'Content-Type': 'application/json',
    };

    // 2. Polling dashboard cada 5 segundos
    for (let i = 0; i < 5; i++) {
      group(`Dashboard poll #${i + 1}`, () => {
        const dashRes = http.get(`${API_BASE}/dashboard/resumen`, {
          headers,
        });

        check(dashRes, {
          'dashboard 200': (r) => r.status === 200,
        });
      });

      sleep(5);
    }
  });
}
