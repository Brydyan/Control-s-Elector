# Third-Party Licenses

Inventario de las licencias de terceros que usa este proyecto.

**Este archivo se genera desde el árbol de dependencias real, no desde memoria.** Los comandos que
lo producen están en [Cómo actualizarlo](#cómo-actualizarlo). Si una dependencia cambia de
licencia, este archivo queda desactualizado hasta que se vuelve a correr el comando: es una
declaración de transparencia, no un mecanismo de bloqueo.

**Generado:** 2026-11-07 · **Alcance:** `backend/electoral-api-quarkus` (Maven) y
`frontend/app` (pnpm).

---

## Resumen

| Árbol | Paquetes | Licencias presentes |
| ----- | -------- | ------------------- |
| Backend — runtime (`compile` + `runtime`) | 243 | Apache-2.0 (203), EPL-2.0 (17), GPL-2.0-with-CPE (8), EDL-1.0 (8), LGPL-2.1+ (3), BouncyCastle (3), BSD-2-Clause (2), MIT (2), BSD-3-Clause (1), ISC (1), MPL-2.0 (1), Dominio público (1) |
| Backend — `test` | 34 | Apache-2.0, EPL-2.0, MIT, BSD-3-Clause |
| Frontend — `dependencies` (prod) | 26 | MIT (22), BSD-2-Clause (1), ISC (1), Apache-2.0 (1), 0BSD (1) |
| Frontend — `devDependencies` + transitivas | 966 | MIT (780), ISC (67), Apache-2.0 (58), BSD-2-Clause (21), BSD-3-Clause (15), BlueOak-1.0.0 (13), MPL-2.0 (2), CC0-1.0 (2), MIT-0 (2), CC-BY-4.0 (1), CC-BY-3.0 (1), Unlicense (1), Python-2.0 (1), 0BSD (1), Apache-2.0 AND MIT (1) |

Los conteos del backend salen de resolver el POM de cada artefacto, incluyendo la cadena de
`parent` (los artefactos de Quarkus no declaran licencia en su propio POM: la declaran en el
parent). **0 de 243 artefactos quedaron sin licencia resuelta.**

---

## Dependencias copyleft — las que hay que vigilar

Estas son las únicas que restringen cómo se puede distribuir el resultado. No son un problema
hoy, pero son las que cambian el día que se empiece a redistribuir el binario.

| Dependencia | Versión | Licencia | Dónde entra | Nota |
| ----------- | ------- | -------- | ----------- | ---- |
| `com.github.librepdf:openpdf` (+ `-renderer`, `-html`) | 2.2.5 | LGPL-2.1 OR MPL-2.0 | `io.quarkiverse.openpdf:quarkus-openpdf` → PDF del expediente de impugnación | File-lazy: LGPL-2.1 en modo dinámico o MPL-2.0 con obligatoriedad de publicar las modificaciones del archivo. La extensión de Quarkus es Apache-2.0; la librería que empaqueta es la copyleft. |
| `org.bouncycastle:bcprov-jdk18on`, `bcpkix-jdk18on`, `bcutil-jdk18on` | 1.86 | Bouncy Castle Licence | Transitiva de `nimbus-jose-jwt` | Licencia MIT modificada (enlace a la licencia Bouncy Castle). Copia de OpenSSL: compatible con Apache-2.0. |
| `jakarta.ws.rs:jakarta.ws.rs-api` y el resto de las APIs `jakarta.*` | various | EPL-2.0 OR GPL-2.0-with-CPE | APIs de Jakarta (transitivas de Quarkus) | Doble licencia: se puede elegir EPL-2.0 (comercial-friendly) o GPL con excepción de classpath. |
| `io.vertx:vertx-core`, `vertx-web*`, `vertx-auth-common`, `vertx-uri-template` | 4.5.34 | Apache-2.0 OR EPL-2.0 | Núcleo de Vert.x (transitivo de Quarkus) | Doble licencia. |
| `io.vertx:vertx-mail-client` | 4.5.34 | EPL-2.0 | `quarkus-mailer` → invitaciones por SMTP | EPL-2.0: copyleft débil, compatible con distribución propietaria. |
| `org.eclipse.parsson:parsson`, `org.glassfish.expressly:expressly` | — | EPL-2.0 OR GPL-2.0-with-CPE | Implementaciones de JSON-P y EL (transitivas de Hibernate Validator) | Doble licencia. |
| `lightningcss` | 1.30.1 | MPL-2.0 | PostCSS de Tailwind 4 (dev) | MPL-2.0: copyleft por archivo, solo en la cadena de build. |
| `caniuse-lite` | 1.0.30001812 | CC-BY-4.0 | Datos de compatibilidad de navegadores (dev) | Atribución requerida; se entrega con su licencia. |
| `spdx-exceptions` | 2.5.0 | CC-BY-3.0 | Metadatos SPDX (dev) | Atribución requerida. |

### Reglas que aplican a este proyecto

- El backend se distribuye como contenedor y como ejecutable nativo. **No se distribuye código
  fuente de OpenPDF ni de las librerías copyleft**: se consume como dependencia dinámica, que es
  el modo que ambas licencias contemplan para este uso.
- Si alguna vez se publica una imagen que incluya las librerías **modificadas**, hay que publicar
  esas modificaciones. Hoy no hay ninguna: OpenPDF se usa sin tocar.
- Las licencias CC-BY (caniuse-lite, spdx-exceptions) son de datos, no de código: no obligan a
  abrir el proyecto, pero sí a conservar la atribución.

> **Nota sobre el alcance de la distribución.** El repositorio es **público**
> (`github.com/Brydyan/Control-s-Elector`), así que "no se publica" no aplica al código: cualquiera
> puede leerlo. Lo que no se publica es el **producto** (la instancia en
> `control.twintailcs.xyz` y las imágenes del clúster). Eso cambia qué obligaciones pesan:
>
> - El `LICENSE` del proyecto declara bajo qué términos se puede reutilizar el código.
> - Las licencias de terceros **obligan igual** cuando se redistribuye: las imágenes del clúster
>   se distribuyen a los nodos, y las licencias MIT/BSD/Apache exigen conservar sus avisos.
> - Las licencias copyleft (EPL, LGPL, MPL) siguen siendo copyleft aunque el uso sea interno:
>   openPDF (MPL-2.0) obliga a publicar cualquier modificación del archivo, y EPL-2.0 obliga a
>   ofrecer el código fuente de los archivos modificados a quien recibe el binario.
>
> Es decir: omitir este archivo no quita ninguna obligación; solo la deja oculta.

---

## Backend — dependencias directas

### `compile` / `runtime`

| Artefacto | Versión | Licencia |
| --------- | ------- | -------- |
| `io.quarkus:quarkus-rest` | 3.40.1 (BOM) | Apache-2.0 |
| `io.quarkus:quarkus-rest-jackson` | 3.40.1 (BOM) | Apache-2.0 |
| `io.quarkus:quarkus-hibernate-orm-panache` | 3.40.1 (BOM) | Apache-2.0 |
| `io.quarkus:quarkus-hibernate-validator` | 3.40.1 (BOM) | Apache-2.0 |
| `io.quarkus:quarkus-jdbc-postgresql` | 3.40.1 (BOM) | Apache-2.0 → trae `org.postgresql:postgresql` 42.7.13 (BSD-2-Clause) |
| `io.quarkus:quarkus-liquibase` | 3.40.1 (BOM) | Apache-2.0 → trae `org.liquibase:liquibase-core` 4.33.0 (Apache-2.0) |
| `io.quarkus:quarkus-smallrye-health` | 3.40.1 (BOM) | Apache-2.0 |
| `io.quarkus:quarkus-smallrye-openapi` | 3.40.1 (BOM) | Apache-2.0 |
| `io.quarkus:quarkus-mailer` | 3.40.1 (BOM) | Apache-2.0 |
| `com.nimbusds:nimbus-jose-jwt` | 9.40 | Apache-2.0 |
| `software.amazon.awssdk:s3` | 2.30.0 | Apache-2.0 |
| `software.amazon.awssdk:apache-client` | 2.30.0 | Apache-2.0 |
| `org.mindrot:jbcrypt` | 0.4 | ISC |
| `io.quarkiverse.openpdf:quarkus-openpdf` | 3.3.3 | Apache-2.0 → ⚠️ trae OpenPDF (LGPL-2.1/MPL-2.0) |

### `test`

| Artefacto | Versión | Licencia |
| --------- | ------- | -------- |
| `io.quarkus:quarkus-junit` | 3.40.1 (BOM) | Apache-2.0 |
| `io.rest-assured:rest-assured` | 6.0.1 | Apache-2.0 |
| `org.testcontainers:testcontainers-postgresql` | 2.0.5 | MIT |
| `org.testcontainers:testcontainers-junit-jupiter` | 2.0.5 | MIT |
| `org.assertj:assertj-core` | 3.27.7 | Apache-2.0 |
| `org.junit.jupiter:junit-jupiter` (transitiva) | 6.1.3 | EPL-2.0 |

### Transitivas de producción relevantes

| Artefacto | Versión | Licencia |
| --------- | ------- | -------- |
| `org.hibernate.orm:hibernate-core` | 7.4.9.Final | Apache-2.0 |
| `io.vertx:vertx-core` | 4.5.34 | Apache-2.0 OR EPL-2.0 |
| `org.bouncycastle:bcprov-jdk18on` | 1.86 | Bouncy Castle Licence |

---

## Frontend — `dependencies` (lo que va en el bundle de producción)

| Paquete | Versión | Licencia |
| ------- | ------- | -------- |
| `@angular/animations` | 21.2.25 | MIT |
| `@angular/common` | 21.2.25 | MIT |
| `@angular/compiler` | 21.2.25 | MIT |
| `@angular/core` | 21.2.25 | MIT |
| `@angular/forms` | 21.2.25 | MIT |
| `@angular/platform-browser` | 21.2.25 | MIT |
| `@angular/platform-browser-dynamic` | 21.2.25 | MIT |
| `@angular/router` | 21.2.25 | MIT |
| `@angular/service-worker` | 21.2.25 | MIT |
| `@capacitor/android` | 8.4.1 | MIT |
| `@capacitor/core` | 8.4.1 | MIT |
| `@primeuix/themes` | 2.0.3 | MIT |
| `idb` | 8.0.3 | ISC |
| `primeicons` | 7.0.0 | MIT |
| `primeng` | 21.1.10 | MIT |
| `rxjs` | 7.8.2 | Apache-2.0 |
| `tslib` | 2.8.1 | 0BSD |
| `zone.js` | 0.16.3 | MIT |

## Frontend — `devDependencies`

| Paquete | Versión | Licencia |
| ------- | ------- | -------- |
| `@angular/cli`, `@angular/compiler-cli`, `@angular/language-service` | 21.2.27 / 21.2.25 | MIT |
| `@angular-devkit/build-angular` | 21.2.27 | MIT |
| `@angular-eslint/*` (6 paquetes) | 21.4.0 | MIT |
| `@angular/cdk` | 21.2.14 | MIT |
| `@analogjs/vite-plugin-angular` | 3.0.0-alpha.90 | MIT |
| `@capacitor/cli` | 8.4.1 | MIT |
| `@ionic/angular-toolkit` | ^12.0.0 | MIT |
| `@playwright/test` | ^1.64.1 | Apache-2.0 |
| `@tailwindcss/postcss`, `tailwindcss` | 4.1.11 | MIT |
| `tailwindcss-primeui` | 0.6.1 | MIT |
| `@vitest/coverage-v8` | ^5.0.3 | MIT |
| `eslint`, `eslint-plugin-import`, `eslint-plugin-prefer-arrow` | ^9.16.0 / ^2.29.1 / 1.2.2 | MIT |
| `eslint-plugin-jsdoc` | ^48.2.1 | BSD-3-Clause |
| `@typescript-eslint/eslint-plugin`, `@typescript-eslint/parser` | ^8.70.1 | MIT |
| `prettier` | ^3.9.6 | MIT |
| `typescript` | ~5.9.3 | Apache-2.0 |
| `vite`, `vitest` | ^8.1.3 / ^5.0.2 | MIT |
| `jsdom` | ^30.0.1 | MIT |
| `fake-indexeddb` | ^6.2.5 | Apache-2.0 |

---

## Assets propios y de terceros

| Asset | Origen | Licencia |
| ----- | ------ | -------- |
| `frontend/app/src/assets/fonts/ibm-plex-{mono,sans}-latin-*.woff2` | IBM Plex | **SIL Open Font License 1.1** — copia completa en `frontend/app/src/assets/fonts/OFL.txt` |
| `frontend/app/src/assets/icon/*.png` | Propios del proyecto | Primera parte — cubiertos por el `LICENSE` raíz. Se generan con `scripts/generate-pwa-icons.py` |
| `frontend/app/src/assets/shapes.svg` | Propio | Primera parte |
| `primeicons` (tipografía de iconos) | PrimeTek | MIT — `node_modules/primeicons/LICENSE` |

> La OFL de IBM Plex exige que el archivo de licencia accompanies a la fuente. `OFL.txt` está en
> el mismo directorio que los `.woff2` y ambos se copian al bundle: no separarlos.

### Licencias de terceros del bundle de producción

El build de Angular genera `www/3rdpartylicenses.txt`: el texto de licencia de cada paquete que
entra al bundle, con el formato que exige la web (`/3rdpartylicenses.txt`). Se genera solo, no
se versiona, y es la referencia que el navegador ofrece desde el menú de la PWA instalada.

```bash
cd frontend/app
pnpm build && ls -la www/3rdpartylicenses.txt
```

---

## Cómo actualizarlo

### Backend

```bash
cd backend/electoral-api-quarkus
mvn -B dependency:list -DincludeScope=runtime -DoutputFile=/tmp/be-deps.txt
```

Después hay que resolver la licencia de cada artefacto leyendo el `<licenses>` de su POM en
`~/.m2/repository`, y si el POM no lo declara, la del `<parent>`. Es lo que hace el parser que
resolvió las 243 entradas de esta tabla.

Alternativa con plugin (descarga red y genera un reporte completo, no versionado):

```bash
mvn org.codehaus.mojo:license-maven-plugin:aggregate-add-third-party
```

### Frontend

```bash
cd frontend/app
pnpm licenses list --json --prod   # solo dependencias de producción
pnpm licenses list --json          # árbol completo, incluidas devDependencies
```

`pnpm licenses list` lee la licencia declarada en el `package.json` de cada paquete, que es lo que
publica el autor de la dependencia.

### Regla

Al agregar una dependencia:

1. Mirar su licencia (`npm info <pkg> license` / `mvn dependency:tree` + el POM).
2. Si es copyleft o una licencia de datos (CC-BY), agregar la fila a la tabla de "Dependencias
   copyleft" de este archivo, con su versión y por dónde entra.
3. Regenerar este archivo antes del merge.

---

## Correcciones sobre el borrador original

Este archivo reemplaza al borrador de
`odd/archive/VERIFIED-2026-11-07-compliance-cors-license-jacoco.md`,
que listaba licencias **incorrectas**. Los errores, para que no se repitan:

| El borrador decía | Realidad (medido) |
| ----------------- | ---------------- |
| PostgreSQL JDBC Driver — LGPL | **BSD-2-Clause** (`org.postgresql:postgresql` 42.7.13) |
| Nimbus JOSE JWT — MIT | **Apache-2.0** (`com.nimbusds:nimbus-jose-jwt` 9.40) |
| JUnit 5 — MIT | **EPL-2.0** (`org.junit.jupiter:junit-jupiter` 6.1.3) |
| AssertJ — MIT | **Apache-2.0** (`org.assertj:assertj-core` 3.27.7) |
| Hibernate ORM — Apache | Correcto, pero no es dependencia directa: entra por `quarkus-hibernate-orm-panache` (`org.hibernate.orm:hibernate-core` 7.4.9.Final, Apache-2.0) |
| Tailwind CSS — Apache 2.0 | **MIT** |
| PrimeNG — Apache 2.0 | **MIT** |
| Capacitor — MIT / Apache dual | **MIT** |
| Angular Material — listado | **No existe en el proyecto**: el proyecto usa PrimeNG + Tailwind, y `@angular/material` no está en `package.json`. (`@angular/cdk` sí aparece, como dependencia de desarrollo de PrimeNG.) |
| — (no listado) | **OpenPDF 2.2.5** (LGPL-2.1 OR MPL-2.0) — la dependencia copyleft del backend, y no estaba en la lista |
| — (no listado) | **Bouncy Castle 1.86**, **Vert.x 4.5.34** (Apache-2.0 OR EPL-2.0), **Liquibase 4.33.0**, **Hibernate 7.4.9.Final**, **fontes IBM Plex** (SIL OFL 1.1) |

---

*Este archivo describe las licencias de terceros, no las del proyecto. Las del proyecto están en
[`LICENSE`](./LICENSE) (Apache-2.0).*
