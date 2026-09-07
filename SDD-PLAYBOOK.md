# SDD con agentes — cómo llevarlo a otro proyecto

Este documento describe el método que se usa en TASE para desarrollar con agentes: cómo se
reparte el trabajo, qué artefactos se escriben, y —lo más importante— **qué falla y cómo se
detecta**.

Está escrito para que lo pegues en otro repositorio y tu agente lo adapte. Separa
deliberadamente dos cosas:

- **El método**, que se lleva tal cual a cualquier proyecto.
- **Los hechos de TASE**, que sirven de ejemplo y hay que **redescubrir** en el proyecto
  nuevo. Van marcados como `[EJEMPLO DE TASE]`.

Si copiás los hechos sin redescubrirlos, te llevás una lista de trampas que no son las
tuyas, y eso es peor que no tener ninguna: da falsa confianza.

---

## 1. El reparto de roles

Tres papeles, y **ninguno se verifica a sí mismo**. Ésta es la regla de la que cuelga todo
lo demás.

| Papel | Quién | Qué hace | Qué NO hace |
|---|---|---|---|
| **Arquitecto / QA** | agente principal | Escribe proposal, design, spec, tasks. Audita. Decide si algo está cerrado | No implementa la funcionalidad |
| **Implementador** | otro agente (en TASE: Minimax) | Escribe el código y sus tests siguiendo `tasks.md` | No decide si su trabajo está bien |
| **Verificador** | subagente con contexto limpio | Ejecuta, muta, mide y reporta | No arregla lo que encuentra |

**Por qué el verificador necesita contexto limpio**: si ve la conversación donde se decidió
la implementación, hereda las suposiciones que hay que poner a prueba. Lanzalo siempre
nuevo, con un prompt que se sostenga solo.

**Por qué el implementador no se verifica**: en TASE, el implementador se auto-archivó tres
veces escribiendo `archive-report.md` y `ready_for_production: true` sin que nada se hubiera
ejecutado. No por mala fe: quien escribe algo no puede ver el hueco que dejó, porque el
hueco tiene la forma de su propia suposición.

---

## 2. La metodología central: verificación por mutación

**Un test que pasa no prueba nada hasta que lo viste fallar.**

Es la única regla que hay que llevarse si no te llevás ninguna otra. El procedimiento:

```
1. Rompé el código a propósito, en el punto exacto que el test dice cubrir
2. Corré el test
3. ¿Cayó? ¿Cuál, por nombre?
4. Restaurá
```

Si no cae ninguno, el test es decorativo: existe, está verde, y no protege nada.

**Cada tarea que introduce una garantía lleva su mutación en `tasks.md`**, con esta forma:

```markdown
- [ ] **C.3** — Verificación por mutación, ejecutada por quien implementa.
  Quitá `X` y comprobá que **cae** el test de C.2. Restaurá.
  Anotá en `apply-progress.md` **el nombre del test que cayó**.
  Si no cae ninguno, C.2 es decorativo y hay que rehacerlo antes de seguir.
```

Pedir el **nombre** del test no es burocracia: es lo que distingue «lo hice» de «lo hice y
miré el resultado». Y el verificador **vuelve a correr las mutaciones por su cuenta** — una
mutación que sólo consta en un `.md` no es evidencia.

### Reglas de seguridad para mutar

Esto costó destruir trabajo sin commitear una vez:

```bash
git status --short            # limpio antes
cp archivo.ts archivo.ts.bak  # respaldo
# … mutar, correr, observar …
cp archivo.ts.bak archivo.ts  # restaurar
rm archivo.ts.bak
git status --short            # limpio después
```

**Nunca `git checkout --`** para restaurar: se lleva por delante cualquier cambio sin
commitear que hubiera alrededor.

---

## 3. Los artefactos

Un *change* es una carpeta con todo lo que se sabe sobre un trabajo. En TASE viven en
`openspec/changes/{back,front}/<fecha>-<slug>/` y al cerrarse se mueven a
`openspec/changes/archive/<fecha>-<slug>/` **en plano**, conservando la fecha del change (no
la del archivado).

| Archivo | Responde a | Lo escribe |
|---|---|---|
| `proposal.md` | Qué se construye y por qué. Alcance y **fuera de alcance** | Arquitecto |
| `design.md` | Decisiones (D1, D2…), **con las alternativas descartadas y su motivo** | Arquitecto |
| `specs/<dominio>/spec.md` | Requisitos y escenarios en Gherkin flojo (GIVEN/THEN) | Arquitecto |
| `tasks.md` | Casillas ejecutables, con sus mutaciones y sus trampas | Arquitecto |
| `apply-progress.md` | Qué se hizo y qué se midió | Implementador |
| `verify-report.md` | Qué se ejecutó y qué se encontró | Verificador |
| `fixes-required.md` | Sólo lo que bloquea, con reproducción | Verificador |
| `archive-report.md` | La historia completa, para quien la lea en un año | Arquitecto |

Los delta specs se fusionan en `openspec/specs/<dominio>/spec.md` al archivar.

### Sobre `design.md`: escribí las alternativas descartadas

Una decisión sin su alternativa descartada no es una decisión, es una preferencia. Y en seis
meses alguien va a «arreglarla» hacia la alternativa que ya se evaluó.

```markdown
## D2 — Quitar los casts es parte del arreglo, no limpieza opcional

**Decisión**: los casts salen en el mismo cambio que introduce las plantillas.

**Alternativa descartada — dejarlos y añadir sólo las plantillas.** Arregla el síntoma de
hoy y conserva el mecanismo que lo produjo: el próximo caso se topa con el mismo error del
compilador y tiene el mismo atajo a mano.
```

### Sobre `fixes-required.md`: sólo lo que bloquea

Es la lista que recibe el implementador. Si mezclás lo bloqueante con lo deseable, se
atienden en desorden. Cada entrada lleva:

1. **Síntoma** — qué se observa
2. **Causa raíz** — con archivo y línea
3. **Reproducción** — los comandos exactos, y el comando que demuestra la causa
4. **Fix propuesto** — concreto
5. **Verificación de cierre** — cómo se sabrá que quedó

Y lo que **no** hay que rehacer, para que nadie toque lo que ya está bien.

---

## 4. Los patrones de defecto que se repiten

Esto es lo más transferible del documento. No son bugs: son **formas** que los bugs adoptan,
y aparecen en cualquier proyecto con agentes.

### 4.1 Los tests cubren las piezas y nadie recorre el camino

El lado A se prueba con el lado B simulado. El lado B se prueba con sus propios datos. Nadie
prueba que la salida de A sea una entrada válida de B.

```
enqueue({template})  ──►  cola  ──►  consumidor  ──►  render(template)
      ▲                                                     ▲
   probado con                                          probado con
   el servicio simulado                                 sus plantillas
                     nadie prueba que el de la izquierda
                     sea uno de los de la derecha
```

`[EJEMPLO DE TASE]` El correo de verificación **nunca se pudo enviar** desde que la fase
anterior se dio por cerrada: se encolaban dos plantillas que no existían en el registro.
Sobrevivió diez rondas de verificación.

**Cómo se caza**: por cada frontera entre módulos, preguntá quién prueba que lo que sale de
uno es válido para el otro. Si la respuesta es «los dos, por separado», ahí hay un hueco.

### 4.2 Una regla aplicada en un sitio y no en su vecino

`[EJEMPLO DE TASE]`

```
password-reset.service.spec.ts:58      { to, template: 'password-reset' }   ← asserta la plantilla
email-verification.service.spec.ts:99  { to }                               ← no la asserta
```

Dos servicios hermanos, la misma clase de test, uno comprueba y el otro no.

**Cómo se caza**: cuando encuentres una regla, buscá sus hermanos con `grep` y comprobá que
todos la aplican. Un guard, una validación o una aserción que existe en un sitio debería
existir en todos los equivalentes.

### 4.3 El comentario que promete la garantía contraria a la del código

El más peligroso, porque desactiva al siguiente que audite.

`[EJEMPLO DE TASE]` Tres veces en el mismo proyecto:

```ts
// «Misma transacción: si la inserción falla, el INSERT se revierte»
// …mientras el repositorio abría otra conexión y no se revertía nada

// «El test NO enumera los nombres a mano»
// …mientras los enumeraba un archivo más allá

// «La aserción no compara contra una hora exacta»
// …mientras comparaba contra una hora exacta
```

**Cómo se caza**: cuando un comentario afirma una garantía, verificá la garantía, no el
comentario. Y si el comentario es la única evidencia de que algo funciona, no hay evidencia.

### 4.4 El control que compara contra un nombre

Un guard que decide con `if (role.name === 'admin')` se abre renombrando el rol. Sin error y
sin log.

`[EJEMPLO DE TASE]` Apareció tres veces. La última vez el sistema permitía renombrar
cualquier rol a `master` y después concederle el permiso reservado — escalada en dos pasos.

**Cómo se caza**: buscá comparaciones contra cadenas que representen identidad
(`=== 'admin'`, `name === …`). Preguntá: ¿quién puede cambiar ese nombre? Si alguien puede,
el control no existe.

### 4.5 Comportamiento que depende de dónde corre

El más traicionero, porque **cada lado sólo mira el suyo**.

`[EJEMPLO DE TASE]` Tres veces en un solo change:

| Qué | Lado permisivo | Por qué |
|---|---|---|
| Variable de entorno en el arnés de tests | **CI** | `.env` está en `.gitignore`: en CI no existe y el test pasa |
| `pnpm run … -- --flag` | local | pnpm reenvía el `--` literal; sólo se ve al ejecutar la línea real del workflow |
| Zona horaria de un correo | **local** | el helper leía la zona del runtime; en la máquina del dev el error se cancelaba |

**Cómo se caza**:

```bash
TZ=UTC <tu comando de test>      # CI casi siempre corre en UTC
git stash && <correr sin .env>    # como el checkout limpio de CI
<copiar la línea EXACTA del workflow y ejecutarla>
```

Esa última es la que más rinde: no leas el paso del CI, **ejecutalo**.

**La regla que sale de acá**: un valor que forma parte del producto —una hora, un
remitente, un límite— no puede depender de dónde se calcula.

### 4.6 La compuerta que promete una garantía que no tiene

`[EJEMPLO DE TASE]` Una comprobación de CI recorría los archivos de migración *del
checkout* y verificaba que cada uno estuviera registrado. Detectaba una migración **sin
registrar**, nunca una **ausente**: un cambio podía integrarse en verde sin su migración.

**Cómo se caza**: por cada compuerta, escribí el defecto que dice prevenir y preguntá si
realmente lo detectaría. Muchas comprueban la presencia de lo que hay, no la ausencia de lo
que falta.

---

## 5. Cómo se lanza un verificador

El prompt del verificador es donde se gana o se pierde la ronda. Estructura probada:

```markdown
Verificá <change> en <ruta>. Rama <X>, HEAD <sha>, árbol limpio.

Leé <artefactos> antes de nada.

## MATERIAL NUEVO DESDE LA RONDA ANTERIOR
[qué cambió — para que no repita lo ya cerrado]

## REGLAS DE MUTACIÓN
[git status limpio, .bak con cp, NUNCA git checkout --]

## TRAMPAS VERIFICADAS DE ESTE REPO
[la lista de §6 — cada una cuesta una ronda si no se avisa]

## LAS PREGUNTAS QUE DECIDEN
[3 a 6 preguntas concretas, cada una con su mutación]

## COMPUERTAS
[los comandos exactos, con los números de la ronda anterior para comparar]

## SALIDA
[qué archivos escribir; y "no archives, no commitees, no pushees"]
```

### Las preguntas se formulan para que no se puedan contestar de memoria

Mal: *«¿está bien implementada la atomicidad?»*
Bien:

> **¿`create()` recibe el `manager`, o el parámetro quedó opcional y sin usar en la
> llamada?** Un parámetro que existe y nadie pasa es peor que no tenerlo: parece arreglado.
>
> **Mutación, ejecutada por vos**: hacé que el segundo `INSERT` falle y comprobá con SQL
> que la primera fila **no** queda. Si queda, el arreglo no cerró nada.

### Pedile desconfianza explícita cuando el trabajo es tuyo

Si el arreglo lo escribió el agente principal, hay que decirlo:

> El arreglo lo escribí yo, y **ya me equivoqué una vez en esa misma línea**. Auditalo con
> desconfianza explícita. No me des el beneficio de la duda.

`[EJEMPLO DE TASE]` Esa instrucción hizo que el verificador encontrara que la línea de CI
que yo había escrito dejaba la suite sin ejecutar un solo test.

### Estrategia para suites lentas

`[EJEMPLO DE TASE]` La suite e2e son 54 archivos y **cada uno levanta sus propios
contenedores**, en serie: ~11 minutos.

- **Para iterar y mutar**: el subconjunto de archivos que toca el change (6 de 54 → ~2 min).
  En tres rondas no dio un solo falso negativo frente a la completa.
- **Al cerrar**: la suite completa, una vez, en segundo plano.
- **En CI**: **particionar, no recortar** (`jest --shard=N/4` en una matriz). La compuerta de
  merge es el único sitio donde nadie va a mirar lo que se saltó.

---

## 6. Las trampas del repositorio: descubrilas, no las copies

`[EJEMPLO DE TASE]` — cada una costó una ronda antes de anotarse:

```
rtk tsc                          da falsos negativos → usar npx tsc crudo
npx tsc -p frontend/tsconfig.json compila CERO archivos ("files": []) → tsc -b --noEmit
nest build                       usa tsconfig.build.json, que EXCLUYE test/
pnpm run x -- --flag             pnpm reenvía el `--` literal (npm no)
$? en un pipe                    es el exit del último comando, no el de jest
reset() del arnés                no trunca la tabla de roles
```

**Ninguna de éstas aplica a tu proyecto.** Lo que aplica es el hábito: cada vez que un
comando mienta, anotalo en un sitio permanente y pasalo en todos los prompts siguientes.

### Cómo encontrar las tuyas, el primer día

```bash
# ¿el typecheck compila algo?
<comando de typecheck> --listFiles | wc -l     # si da 0, no comprueba nada

# ¿el build cubre los tests?
cat tsconfig.build.json                         # buscar "exclude"

# ¿el runner recibe las banderas?
<comando de test> -- --listTests                # ¿las ve, o las trata como patrón?

# ¿el arnés aísla todo?
grep -n "process.env" <arnés de tests>          # ¿qué NO sobreescribe?
```

Esa última pregunta —**qué no sobreescribe el arnés**— encontró dos defectos en TASE.

---

## 7. Los agentes fallan de formas concretas

Observado, no teórico. Guardá contra esto:

| Fallo | Frecuencia | Guarda |
|---|---|---|
| El agente de archivado **copia en vez de mover** y pide `rm -rf` del origen | 4 veces | Archivá a mano con `git mv`; el borrado ocurre después de comparar |
| El agente **reescribe** archivos que debía copiar textualmente | 1 vez | `diff` byte a byte antes de borrar el origen |
| El agente **deja un marcador** donde debía ir el contenido | 1 vez | `grep` buscando marcadores; comparar tamaños de archivo |
| Reporta **números que no midió** | varias | Recontá lo decisivo (requisitos, escenarios, tests) |
| El implementador **se auto-archiva** sin pasar verificación | 3 veces | Antes de cada ronda, comprobar que no aparecieron artefactos de cierre |
| Reporta un CRITICAL **mal calificado**, con un arreglo que empeora | 1 vez | Verificá el diagnóstico antes de pasarlo; corregí el `fixes-required` |

El peor fue el del marcador. Un archivado contenía:

```
[... design.md content included in archive ...]
```

Un archivo con aspecto correcto que **afirmaba contener lo que no contenía**. Si se hubiera
borrado el original —que es lo que pedía la operación— se perdían 532 líneas de diseño.

**La regla**: antes de borrar cualquier origen, comprobá que el destino tiene el contenido.
No que existe: que lo tiene.

```bash
diff -r origen/ destino/         # y mirá el resultado, no el código de salida
```

---

## 8. Cómo adaptar esto a tu proyecto

En orden. Cada paso produce algo que el siguiente usa.

**1 · Elegí el sitio de los artefactos.** `openspec/`, `docs/changes/`, da igual. Lo que
importa es que sea uno solo y que el archivado sea plano.

**2 · Escribí tu ROADMAP.** Un archivo que sea el punto de entrada para una sesión nueva,
con las decisiones ya cerradas *para que nadie las vuelva a discutir*. En TASE captura el
orden de fases, las dependencias y las trampas del repo.

**3 · Descubrí tus trampas** (§6) y anotalas ahí mismo. Media hora, y ahorra una ronda por
cada una.

**4 · Fijá tus compuertas** — los comandos exactos, con los números de la línea base:

```
lint         0 errores (N warnings preexistentes)
typecheck    exit 0
build        exit 0
unit         X suites / Y tests
integración  X suites / Y tests
```

Los números importan: **si no suben al añadir tests, los tests nuevos no se están
ejecutando**. Eso es un fallo de la fase, no un detalle.

**5 · Copiá las definiciones de agente.** En TASE viven en `~/.claude/agents/sdd-*.md` y
`~/.claude/skills/sdd-*/`, con convenciones compartidas en `_shared/`. Son genéricas: lo
que las hace tuyas es el prompt de lanzamiento, no el archivo.

**6 · Empezá por un change pequeño y real.** Un defecto conocido, no una funcionalidad
nueva. Vas a descubrir tus trampas con algo cuyo resultado ya conocés.

**7 · No saltes la verificación aunque el change sea trivial.** En TASE, el arreglo más
pequeño —dos plantillas de correo— destapó cuatro defectos encadenados, incluido uno que
limitaba los registros a cinco por hora **para todo internet**.

---

## 9. Lo que hace que esto funcione

Si tuviera que reducirlo a cinco frases:

**Nadie verifica su propio trabajo.** Ni el implementador, ni el arquitecto, ni el agente
que archiva.

**Un test que pasa no prueba nada hasta que lo viste fallar.** Todo lo demás es
consecuencia de esto.

**Ejecutá, no leas.** «Revisé el código y se ve bien» no es una verificación. Correr la
línea exacta del workflow encontró un defecto que tres lecturas no vieron.

**Los números se recuentan.** Cuando un agente dice «23 escenarios», contálos. En TASE eran
30.

**Escribí por qué, no sólo qué.** Los mensajes de commit y los `design.md` que explican el
razonamiento son los que evitan que alguien deshaga el arreglo dentro de seis meses. Un
comentario que miente es peor que ninguno.

---

## Apéndice — plantilla de `tasks.md`

````markdown
# Tasks: <NOMBRE> — <título>

Historia: <enlace> · Rama: `<rama>`, sacada de `<base>`.

Orden obligatorio: **A antes que B**. <por qué>

---

## A · <bloque>

- [ ] **A.1** — <acción concreta, con archivo y línea>

- [ ] **A.2** — <acción>

  <contexto: por qué así y no de otra forma>

  **Trampa**: <la que aplique de §6>

- [ ] **A.3** — Tests:
  - <qué debe comprobar cada uno>

- [ ] **A.4** — **Verificación por mutación.** Quitá <X> y comprobá que **cae** <test>.
  Anotá en `apply-progress.md` **el nombre del test que cayó**.

---

## Qué NO hacer en esta fase

- **No** <cosa tentadora> — <por qué>

---

## Compuertas antes de dar por terminada la fase

```
lint         0 errores
typecheck    exit 0
unit         suites/tests, comparar con la línea base
```

Si el total de tests **no sube**, los tests nuevos no se están ejecutando.
````
