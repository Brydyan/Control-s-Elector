#!/usr/bin/env node

/**
 * generate-licenses.js — Build-time script to generate licenses.json
 *
 * Lee package-lock.json y package.json para extraer información de
 * dependencias y genera un archivo JSON con nombre, versión, licencia y
 * repositorio de cada paquete.
 *
 * Uso: node scripts/generate-licenses.js
 */

const fs = require('fs');
const path = require('path');

const LOCK_FILE = path.join(__dirname, '../package-lock.json');
const PACKAGE_FILE = path.join(__dirname, '../package.json');
const OUTPUT_DIR = path.join(__dirname, '../src/assets/legal');
const OUTPUT_FILE = path.join(OUTPUT_DIR, 'licenses.json');

/**
 * Asegura que el directorio de output existe.
 */
function ensureOutputDir() {
  if (!fs.existsSync(OUTPUT_DIR)) {
    fs.mkdirSync(OUTPUT_DIR, { recursive: true });
  }
}

/**
 * Lee y parsea el package-lock.json
 */
function readLockFile() {
  try {
    const content = fs.readFileSync(LOCK_FILE, 'utf8');
    return JSON.parse(content);
  } catch (err) {
    console.error('Error reading package-lock.json:', err.message);
    process.exit(1);
  }
}

/**
 * Extrae las licencias de las dependencias.
 * Retorna un array de objetos con { name, version, license, repository, restrictive }
 */
function extractLicenses(lockFile) {
  const licenses = [];
  const seenPackages = new Set();

  // Itera sobre todos los paquetes en el lock file
  const packages = lockFile.packages || {};

  Object.entries(packages).forEach(([key, pkg]) => {
    // Salta el paquete raíz (key vacía)
    if (!key || key === '') {
      return;
    }

    // Extrae el nombre del paquete del key
    // key format: "node_modules/package-name" o "node_modules/@scope/package-name"
    const packageName = pkg.name;

    if (
      !packageName ||
      !pkg.version ||
      !pkg.license ||
      seenPackages.has(packageName)
    ) {
      return;
    }

    seenPackages.add(packageName);

    // Determina si la licencia es restrictiva (GPL o AGPL)
    const isRestrictive = pkg.license
      ? ['GPL', 'AGPL'].some((l) =>
          pkg.license.toUpperCase().includes(l),
        )
      : false;

    // Extrae el repositorio si existe
    let repository = null;
    if (pkg.repository) {
      if (typeof pkg.repository === 'string') {
        repository = pkg.repository;
      } else if (typeof pkg.repository === 'object' && pkg.repository.url) {
        repository = pkg.repository.url;
      }
    }

    licenses.push({
      name: packageName,
      version: pkg.version,
      license: pkg.license,
      repository: repository,
      restrictive: isRestrictive,
    });
  });

  // Ordena por nombre de paquete para consistencia
  licenses.sort((a, b) => a.name.localeCompare(b.name));

  return licenses;
}

/**
 * Escribe el archivo licenses.json
 */
function writeLicensesFile(licenses) {
  try {
    fs.writeFileSync(OUTPUT_FILE, JSON.stringify(licenses, null, 2) + '\n');
    console.log(
      `✓ Generated ${OUTPUT_FILE} (${licenses.length} packages)`,
    );
  } catch (err) {
    console.error('Error writing licenses.json:', err.message);
    process.exit(1);
  }
}

/**
 * Punto de entrada del script
 */
function main() {
  try {
    ensureOutputDir();
    const lockFile = readLockFile();
    const licenses = extractLicenses(lockFile);

    if (licenses.length === 0) {
      console.warn('Warning: No licenses found in package-lock.json');
    }

    writeLicensesFile(licenses);
    process.exit(0);
  } catch (err) {
    console.error('Unexpected error:', err);
    process.exit(1);
  }
}

main();
