#!/usr/bin/env node

/**
 * generate-licenses.js — Build-time script to generate licenses.json
 *
 * Extrae información de dependencias desde node_modules o lock files
 * y genera un archivo JSON con nombre, versión, licencia y repositorio.
 *
 * Uso: node scripts/generate-licenses.js
 */

const fs = require('fs');
const path = require('path');

const PNPM_LOCK_FILE = path.join(__dirname, '../pnpm-lock.yaml');
const NPM_LOCK_FILE = path.join(__dirname, '../package-lock.json');
const OUTPUT_DIR = path.join(__dirname, '../src/assets/legal');
const OUTPUT_FILE = path.join(OUTPUT_DIR, 'licenses.json');
const NODE_MODULES_PATH = path.join(__dirname, '../node_modules');

/**
 * Asegura que el directorio de output existe
 */
function ensureOutputDir() {
  if (!fs.existsSync(OUTPUT_DIR)) {
    fs.mkdirSync(OUTPUT_DIR, { recursive: true });
  }
}

/**
 * Extrae licencias de node_modules leyendo package.json de cada paquete
 */
function extractLicensesFromNodeModules() {
  const licenses = [];
  const seenPackages = new Set();

  if (!fs.existsSync(NODE_MODULES_PATH)) {
    console.warn('Warning: node_modules not found');
    return licenses;
  }

  try {
    // Lee directorios top-level
    const topLevelDirs = fs.readdirSync(NODE_MODULES_PATH);

    topLevelDirs.forEach((topDir) => {
      const topPath = path.join(NODE_MODULES_PATH, topDir);

      // Si es un scope (comienza con @), lee sus subdirectorios
      if (topDir.startsWith('@')) {
        try {
          const scopedDirs = fs.readdirSync(topPath);
          scopedDirs.forEach((scopedPkg) => {
            const pkgPath = path.join(topPath, scopedPkg);
            tryExtractLicense(pkgPath, `${topDir}/${scopedPkg}`, licenses, seenPackages);
          });
        } catch (err) {
          // Ignore errors reading scoped directories
        }
      } else {
        // Paquete normal
        tryExtractLicense(topPath, topDir, licenses, seenPackages);
      }
    });
  } catch (err) {
    console.error('Error reading node_modules:', err.message);
  }

  // Ordena por nombre
  licenses.sort((a, b) => a.name.localeCompare(b.name));

  return licenses;
}

/**
 * Intenta extraer información de licencia de un directorio de paquete
 */
function tryExtractLicense(pkgPath, pkgName, licenses, seenPackages) {
  try {
    const pkgJsonPath = path.join(pkgPath, 'package.json');

    if (!fs.existsSync(pkgJsonPath)) {
      return;
    }

    const pkgJson = JSON.parse(fs.readFileSync(pkgJsonPath, 'utf8'));
    const name = pkgJson.name || pkgName;

    if (seenPackages.has(name)) {
      return;
    }

    seenPackages.add(name);

    const license = pkgJson.license || 'UNLICENSED';
    const version = pkgJson.version || 'unknown';

    // Determina si la licencia es restrictiva
    const isRestrictive = license.toUpperCase().includes('GPL') ||
                         license.toUpperCase().includes('AGPL');

    // Extrae repositorio
    let repository = null;
    if (pkgJson.repository) {
      if (typeof pkgJson.repository === 'string') {
        repository = pkgJson.repository;
      } else if (typeof pkgJson.repository === 'object' && pkgJson.repository.url) {
        repository = pkgJson.repository.url;
      }
    }

    licenses.push({
      name,
      version,
      license,
      repository,
      restrictive: isRestrictive,
    });
  } catch (err) {
    // Silencia errores individuales, continúa con otros paquetes
  }
}

/**
 * Escribe el archivo licenses.json
 */
function writeLicensesFile(licenses) {
  try {
    fs.writeFileSync(OUTPUT_FILE, JSON.stringify(licenses, null, 2) + '\n');
    console.log(`✓ Generated ${OUTPUT_FILE} (${licenses.length} packages)`);
  } catch (err) {
    console.error('Error writing licenses.json:', err.message);
    process.exit(1);
  }
}

/**
 * Punto de entrada
 */
function main() {
  try {
    ensureOutputDir();
    const licenses = extractLicensesFromNodeModules();

    if (licenses.length === 0) {
      console.warn('Warning: No licenses found');
    }

    writeLicensesFile(licenses);
    process.exit(0);
  } catch (err) {
    console.error('Unexpected error:', err.message);
    process.exit(1);
  }
}

main();
