# Los tres papeles — índice

Reparto de trabajo con agentes en Control Electoral. Método: `SDD-PLAYBOOK.md` (raíz).
Adaptación al proyecto: `openspec/ROADMAP.md`.

Regla que sostiene todo lo demás: **nadie verifica su propio trabajo.**

---

## Quién es quién

| Papel | Archivo | Quién | Modelo |
|---|---|---|---|
| **Arquitecto / QA** | `01-arquitecto-qa.md` | agente principal — el hilo con el que hablás | opus |
| **Builder** (Implementador) | `02-builder.md` | subagente `sdd-apply`, contexto sólo del prompt | sonnet |
| **Verify** (Verificador) | `03-verify.md` | subagente **nuevo**, contexto limpio | sonnet |

```
Arquitecto ──escribe──► tasks.md ──► Builder ──escribe──► apply-progress.md
     ▲                                                            │
     │                                                            ▼
     └──────── verify-report.md ◄──── Verify ◄────── lee y RE-EJECUTA
                fixes-required.md
```

El Builder no decide si su trabajo está bien. Verify no arregla lo que encuentra. El
Arquitecto no escribe la funcionalidad. Cada frontera existe para que el hueco de uno lo vea
otro: quien escribe algo no puede ver el hueco que dejó, porque el hueco tiene la forma de su
propia suposición.

---

## Por qué Verify va con contexto limpio

Si ve la conversación donde se decidió la implementación, hereda las suposiciones que hay que
poner a prueba. Se lanza **siempre nuevo**, con un prompt que se sostiene solo.

Un subagente al que le decís «ya verificamos X, saltátelo» deja de ser un verificador.

---

## Bloques que van pegados en TODO prompt de subagente

Los subagentes **no leen** `CLAUDE.md`, ni este archivo, ni el registro de skills. Todo llega
digerido, como **texto pegado, no rutas**. Así el lanzamiento sobrevive a una compactación:
cada delegación vuelve a resolverlo.

### Trampas verificadas de este repo

Copiar tal cual. Cada una cuesta una ronda si no se avisa.

```
- `mvn test | tail` devuelve el exit de `tail`, NO el de Maven. Redirigí a archivo
  y leé target/surefire-reports/*.xml. Hay 78 tests en ERROR ahora mismo: si tu comando
  da "exit 0", desconfiá.
- El working directory del backend es backend/electoral-api-quarkus/, no la raíz.
- El shell es fish: `$status`, no `$?`. Un glob sin match aborta el comando.
- `ng test` cuelga sin `--watch=false --browsers=ChromeHeadless` (karma.conf.js:40-41).
- La BD de dev ya está migrada: las migraciones rotas SOLO se ven en contenedor limpio.
- CI corre `-DskipTests`: hoy la compuerta de merge no ejecuta ni un test.
```

### Estándares del proyecto

Se pegan **sólo los que apliquen** a los archivos que el subagente va a tocar:

```
- Sólo OBSERVER registra actas. Sólo MASTER revoca tokens y anula actas APROBADAS.
  ADMIN nunca escribe — ni actas, ni correcciones, ni aprobaciones.
- Auditoría siempre AuditLogService → JPA → PG. Nunca escribir AuditLog directo.
- Los DTOs exponen publicId (UUIDv7), nunca el id interno.
- MapStruct sin componentModel="spring": mappers como @Bean en MapperConfig.java.
- Locking pesimista por jrv_id + cargo_id. Una acta por par.
- Invariante: SUM(acta_detalle.votos) + votos_nulos + votos_blancos = total_sufragantes.
- Estados: BORRADOR → ENVIADA → EN_DISPUTA → CORREGIDA → APROBADA → ANULADA.
  ANULADA es final. Los datos nunca se borran al anular.
- Access token sólo en memoria JS, nunca en localStorage.
```

### Reglas de mutación

Van en el prompt del Builder y en el de Verify, sin excepción:

```
git status --short limpio ANTES y DESPUÉS.
Respaldo con `cp archivo archivo.bak`. Restaurar con `cp` desde el .bak.
NUNCA `git checkout --` para restaurar: se lleva por delante cualquier cambio
sin commitear que hubiera alrededor.
Anotá el NOMBRE del test que cayó. Si no cae ninguno, el test es decorativo.
```

### Uso de RTK (Rust Token Killer) por rol

Para ahorrar tokens y prevenir saturación de contexto:

| Rol | Cuándo usar `rtk` | Comandos recomendados |
|---|---|---|
| **Arquitecto / QA** | Inspección ágil sin desbordar el hilo principal | `rtk git status`, `rtk git diff`, `rtk smart <archivo>` |
| **Builder (`sdd-apply`)** | Compilación, mutaciones y verificación rápida | `rtk err mvn clean compile`, `rtk err pnpm build`, `rtk test mvn test`, `rtk test pnpm test:ci`, `rtk git status --short` |
| **Verify (Contexto limpio)** | Auditoría de mutaciones y formateo | `rtk test <cmd>`, `rtk err mvn spotless:check`. *(Nota: la compuerta final SIEMPRE cuenta totales exactos desde XMLs/Vitest)* |

---

## Qué NO se delega

| Acción | Inline | Delegar |
|---|---|---|
| Leer 1-3 archivos para decidir o confirmar | ✅ | — |
| Explorar 4+ archivos para entender un área | — | ✅ |
| Leer como preparación para escribir | — | ✅ junto con la escritura |
| Editar un archivo, mecánico, ya sabés cómo queda | ✅ | — |
| Escribir en varios archivos, con lógica nueva | — | ✅ |
| `git status`, `git log`, `gh pr view` | ✅ | — |
| Correr build, tests o instalación | — | ✅ |

Criterio único: **¿esto infla mi contexto sin necesidad?**

---

## Realimentación

Si un subagente reporta que no recibió estándares o que fue a buscarlos por su cuenta, el
Arquitecto perdió la caché —normalmente por compactación—. Hay que releerlos y volver a
inyectarlos en **todas** las delegaciones siguientes. No es un detalle: es la señal de que el
orquestador dejó caer contexto.
