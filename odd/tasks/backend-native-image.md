# Compilar el backend Quarkus como GraalVM Native Image

Fase 2 de la migración. Destraba el build nativo del backend completo (#114), que no cerraba desde
que la migración a Quarkus quedó hecha. Apilado sobre `feat/backend-quarkus-cutover` (#150).

## Specs

- **S1** — "ok listo apra seguir avanzando desde ese ultimo pr 150 agregale otro pr al stack para migrar de la jvm a graalvm"
- **S2** — El PR va apilado sobre #150: `develop → #139 → #140 → #150 → #151`.
- **S3** — El build nativo del backend completo tiene que cerrar. Es el motivo declarado de la migración a Quarkus.
- **S4** — "Puede cambiar — extensión oficial": el PDF del expediente puede cambiar de motor y layout a cambio de usar la extensión oficial de Quarkus para OpenPDF.
- **S5** — El despliegue (compose, CD, Railway) consume la imagen nativa.
- **S6** — El cold start y el RSS se **miden**, no se estiman.

### Specs derivadas de la evidencia

- **S7** — El binario nativo tiene que cumplir el mismo contrato que el de JVM. La prueba es la misma suite de contrato, sin bifurcaciones.
- **S8** — La imagen de runtime nativo no tiene JVM.

## Tasks

| ID | Qué | Specs | Ruta | Commit |
|---|---|---|---|---|
| T1 | Entrar a OpenPDF por la extensión en vez del artifact suelto | S3,S4 | inline | `c5bfc05` |
| T2 | `Dockerfile.native` (builder Mandrel, runtime UBI minimal sin JDK) + job `Backend Native Image` con medición | S3,S6,S8 | inline | `423c781` |
| T3 | `compose.yaml`, `deploy.yml` y `railway.json` a la imagen nativa | S5 | inline | `5f726a5` |
| T4 | Verificación del camino JVM con el swap hecho | S4,S7 | inline | ver L6 |
| T5 | PR + entrada de log | S1,S2 | inline | este PR |

**Estado:** T1-T4 hechas. T5 en curso.

### Frontera de la rebanada

Mismo criterio que #150: el diff de este PR contra su base son 3 commits.

## Log

### L1 — 2026-10-21 — Pedido

> ok listo apra seguir avanzando desde ese ultimo pr 150 agregale otro pr al stack para migrar de la jvm a graalvm

### L2 — 2026-10-21 — El pedido no es un corte, es el trabajo de destrabar #114

La diferencia con #150 hay que tenerla clara antes de mergear: aquel PR cambiaba **qué
implementación corre**; este tiene que hacer que **una cosa que hoy no compila** compile. El
README anunciaba "GraalVM Native Image — startup ~50ms" desde antes de la migración, y era falso:
el backend corría sobre la JVM.

### L3 — 2026-10-21 — El bloqueo, verificado vivo

No se trabajó sobre el error documentado en una sesión anterior: se verificó contra el artefacto
que el proyecto usa hoy.

```
openpdf-2.0.3.jar -> com/lowagie/text/pdf/FopGlyphProcessor.class
  org/apache/fop/complexscripts/util/GlyphSequence.getGlyphs
  org/apache/fop/complexscripts/fonts/GlyphSubstitutionTable.substitute
  org/apache/fop/complexscripts/util/CharScript.dominantScript
  org/apache/fop/fonts/truetype/TTFFile.getGSUB
```

Sigue igual en 2.2.5 y en 3.0.1. **Un bump de versión no lo resuelve** — se bajó el 3.0.1 y se
comprobó que conserva las mismas referencias. Además la serie 3.x renombra el paquete a
`org.openpdf`, así que, sin servir, costaba la reescritura de las 214 líneas.

### L4 — 2026-10-21 — La app no usa FOP, y eso abre la puerta

Que la referencia exista no significa que se use. `FopGlyphProcessor` da tipografía a scripts
complejos (árabe, hebreo, devanagari) vía FOP. El backend genera el PDF del expediente en un solo
endpoint, con una clase de 214 líneas que usa `Document`, `Paragraph`, `PdfPTable`, `PdfWriter` e
`Image`. Cero XML-to-PDF, cero `XmlParser`, cero XMP, cero `RtfWriter`, cero llamadas a FOP.

### L5 — 2026-10-21 — Decisión: extensión oficial, y una corrección

El operador eligió la extensión oficial: *"Puede cambiar — extensión oficial"*.

**Corrección a lo que se le dijo antes de decidir.** Se afirmó que el swap obligaba a cambiar los
13 imports de `com.lowagie` a `org.openpdf` y a adaptar la API. Es falso. La extensión
`quarkus-openpdf:3.3.3` arrastra `openpdf:2.2.5`, que **sigue en el paquete `com.lowagie`**; el
renombre a `org.openpdf` es de la serie 3.x, que esta extensión no usa. El salto real es
2.0.3 → 2.2.5, versiones menores.

Consecuencia práctica: **`c5bfc05` no toca una línea de código**. `mvn clean verify` da 4/4 y
spotless limpio sin tocar `ExpedientePdfService`.

**Por qué la extensión resuelve el bloqueo, y no es un parche.** Su módulo de runtime trae
`FopGlyphProcessorSubstitution`, una sustitución de GraalVM que reemplaza los dos métodos con
dependencia FOP: `isFopSupported()` devuelve `false` y `convertToBytesWithGlyphs(...)` lanza. Como
los cuerpos se reemplazan, los tipos de FOP nunca se resuelven durante el análisis de la imagen. No
hay que excluir clases a mano ni mantener metadata propia.

El límite de la extensión —"no soporta FOP"— no afecta a este backend. Si algún día el acta
necesita arousalábigo, se vuelve a discutir.

### L6 — 2026-10-21 — Verificación: qué se probó y qué no

**Probado localmente:**

| | |
|---|---|
| `mvn -B clean verify` con la extensión | **BUILD SUCCESS** — 4/4, spotless limpio en 174 archivos |
| Imagen JVM con el swap, arranque en dos fases contra base vacía | 25 tablas, y después `validate`: OK |
| Suite de contrato contra esa imagen | **122/122** — incluye `expedientePdfDevuelveUnDocumentoPdf`, que es exactamente lo que cambió de versión de OpenPDF |

**NO probado, y no se puede probar desde acá:**

| | Por qué |
|---|---|
| El build nativo | `native-image` pide 6–8 GB. Esta máquina tiene **3 GB disponibles**. |
| Las imágenes base de Mandrel | `quay.io` devuelve **401** al daemon de Docker local (Docker Hub sí responde). |

Por eso el job `Backend Native Image` no se limita a compilar: **mide**. El cold start se cronometra
desde `docker run` hasta que `/actuator/health` responde, y el RSS se lee de
`/proc/<pid>/status`. El número que se publique en el README tiene que salir de ese job, no de la
guía de Quarkus.

### L7 — 2026-10-21 — Lo que este PR no resuelve

Riesgos de runtime nativo, no del bloqueo. Cada uno necesita su propia prueba:

- AWS SDK v2 contra S3 custom (RustFS en dev, Supabase en prod) y modo path-style.
- Liquibase cargando el changelog maestro desde el classpath.
- `UuidGenerator` propio de UUIDv7. Hibernate ≥ 7.2 trae estrategia nativa y permitiría eliminarlo (ADR 0001).
- HTTPS saliente: `quarkus.ssl.native=true` si el endpoint de S3 es HTTPS en producción.
- Los dos `@Lock(PESSIMISTIC_WRITE)` verificados contra Postgres real, no contra mocks.

### L8 — 2026-10-21 — Un defecto de proceso

El tracking se creó **después** de la primera escritura: `c5bfc05` ya estaba commiteado cuando se
escribió este documento. La regla es trackear antes de la primera línea de código. Se registra en
vez de omitirlo, porque el orden importa justamente cuando el trabajo se puede interrumpir entre
commits.