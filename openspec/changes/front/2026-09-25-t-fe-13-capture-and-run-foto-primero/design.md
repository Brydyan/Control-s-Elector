# Design — T-FE-13: Modo "Foto Primero" (Capture & Run) y Cotas Electorales

---

## D1 — Reordenamiento del Wizard: Foto Primero

**Decisión**: La variable `currentStep` inicia en `'IMAGEN'`:
```
Paso 1: IMAGEN (Toma de foto, previsualización, guardado preventivo local)
   ↓
Paso 2: VOTOS (Padrón de candidatos, sufragantes, invariante)
   ↓
Paso 3: REVISION (Resumen final, confirmación de transmisión)
```
Si el usuario desea omitir temporalmente la foto porque no tiene cámara y digitará los votos primero, puede pulsar "Digitar votos primero" como excepción, pero el default operacional es siempre la captura fotográfica inmediata (salvaguarda indispensable ante desalojo militar o incidentes de mesa).

---

## D2 — Lógica de Cotas y Alertas de Sufragantes

**Decisión**:
```typescript
get alertaSufragantes(): string | null {
  if (this.totalSufragantes > 500) {
    return 'Límite CNE excedido: ninguna JRV puede superar 500 sufragantes.';
  }
  if (this.totalSufragantes > 350) {
    return 'Advertencia: El padrón estándar es de 350 electores. Verifique si es una mesa especial.';
  }
  return null;
}

get esSufragantesValido(): boolean {
  return this.totalSufragantes > 0 && this.totalSufragantes <= 500;
}
```

---

## D3 — Regla Matemática de Invariante Estricto

**Decisión**:
En el backend (`RegistrarActaUseCase`) y en la base de datos el invariante es estricto y sin excepciones para cada acta `(jrv_id, cargo_id)`:
$$\sum \text{detalle.votos} + \text{votos\_blancos} + \text{votos\_nulos} = \text{totalSufragantes}$$

Cualquier discrepancia provoca rechazo con `HTTP 400 Bad Request` en el backend. Se elimina la fórmula de hasta $3\times$ en el cliente. Las dignidades pluripersonales o CPCCS se gestionan con actas unívocas por cargo o lista cerrada donde cada voto individual cuadra 1:1 contra los sufragantes registrados en esa acta.

---

## D4 — Persistencia de la Foto y Estado en Borrador Local vía IndexedDB (`acta_drafts`)

**Decisión**: El borrador local se persiste de forma asíncrona y estructurada en IndexedDB dentro del almacén dedicado `'acta_drafts'` de `ElectoralOfflineDB` (coordinado con `OfflineStorageService` / `ActaStorageService`).
- Se almacena el `Blob` binario puro de la foto (o identificador/clave local), metadatos de JRV, cargo y sufragantes.
- Se prohíbe terminantemente el uso de `localStorage` sincrónico: `localStorage` bloquea el hilo principal de la UI, no soporta Blobs de manera eficiente (requiriendo conversión a Base64 que infla la memoria y precipita cierres por Android LMK) y arroja `QuotaExceededError` al superar el límite de 5MB.
- Es 100% compatible con el patrón de guardado preventivo ("Guardar antes de capturar"), asegurando que si el navegador o la pestaña se cierran o reinician por desalojo de memoria, el borrador se recupere íntegro al volver a abrir la pantalla.

**Alternativa descartada — `localStorage` sincrónico**: Incompatible con Blobs, genera picos de memoria si se usa Base64 y bloquea el hilo principal durante lecturas/escrituras.
**Alternativa descartada — Mantener solo en memoria del componente**: La foto y el avance se perderían de inmediato si Android LMK recicla la pestaña de Chrome durante la captura de cámara.
