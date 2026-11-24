#!/usr/bin/env node
/**
 * Escribe `www/assets/config.json` despues de `ng build` a partir de variables
 * de entorno del proveedor que construye el frontend.
 *
 * Por que existe: la URL de la API depende del destino de despliegue y NO
 * debe decidirse editando el JSON versionado.
 *   - Cloudflare Pages (produccion): la SPA es estatica y la API vive en otro
 *     origen (Railway detras de Cloudflare). Pages responde 405 a cualquier POST,
 *     asi que una ruta relativa `/api/v1` rompe login y refresh.
 *     -> API_URL=https://api.brydyan.me/api/v1 (variable de entorno de Pages)
 *   - Imagen Docker con nginx (compose / k8s de referencia): nginx hace proxy de
 *     `/api/`, asi que la ruta relativa es la correcta.
 *     -> API_URL=/api/v1 (ARG del Dockerfile)
 *
 * Variables:
 *   API_URL     URL base de la API. Opcional: si falta se conserva el
 *               config.json versionado (que apunta a produccion).
 *   SENTRY_DSN  DSN de Sentry. Opcional.
 *
 * `assets/config.json` esta excluido del hash del service worker
 * (ngsw-config.json -> excludeFiles), por eso puede reescribirse tras el build.
 */
import { existsSync, readFileSync, writeFileSync } from "node:fs";
import { dirname, resolve } from "node:path";
import { fileURLToPath } from "node:url";

const root = resolve(dirname(fileURLToPath(import.meta.url)), "..");
const target = resolve(root, "www", "assets", "config.json");

if (!existsSync(target)) {
  console.error(`[runtime-config] No existe ${target}. Corre ng build antes.`);
  process.exit(1);
}

const current = JSON.parse(readFileSync(target, "utf8"));
const apiUrl = (process.env.API_URL ?? "").trim();
const sentryDsn = process.env.SENTRY_DSN;

if (!apiUrl && sentryDsn === undefined) {
  console.log(
    `[runtime-config] Sin API_URL: se conserva apiUrl=${current.apiUrl}`,
  );
  process.exit(0);
}

if (apiUrl && !/^(https:\/\/[^/\s]+|\/)[^\s]*$/.test(apiUrl)) {
  console.error(
    `[runtime-config] API_URL invalida: "${apiUrl}". ` +
      "Debe ser https://<host>/... (API en otro origen) o /... (proxy nginx).",
  );
  process.exit(1);
}

const next = {
  ...current,
  ...(apiUrl ? { apiUrl: apiUrl.replace(/\/+$/, "") } : {}),
  ...(sentryDsn !== undefined ? { sentryDsn } : {}),
};

writeFileSync(target, `${JSON.stringify(next, null, 2)}\n`);
console.log(`[runtime-config] config.json -> apiUrl=${next.apiUrl}`);
