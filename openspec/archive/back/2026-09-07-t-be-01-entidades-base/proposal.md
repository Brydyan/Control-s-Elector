# Proposal — T-BE-01 · Entidades base: geografía y usuarios

Origen: `documents/task-control/backendtask/COMPLETADO-T-BE-01.md` · Área: back
Fecha: 2026-09-07 · **Change retrospectivo**

---

## Qué es un change retrospectivo

El código ya existe. Este change **no construye**: captura qué garantías dejó esa
implementación y las somete a mutación. Un `proposal.md` que dijera «vamos a construir las
entidades» sería ficción — ya están.

Lo que sí es verdad y hoy no consta en ninguna parte: **ninguna de esas garantías está
probada**. `UsuarioRepositoryTest` existe con 3 tests, y los 3 están en ERROR.

## Qué cubre

`Provincia`, `Canton`, `Parroquia`, `Jrv`, `Usuario`, `RefreshToken` y la jerarquía
geográfica que las relaciona.

## El hallazgo: `RefreshToken` es código muerto

`RefreshToken.java` existe como entidad JPA, con tabla `refresh_token` creada en
`006-geografia.sql::006-05-create-refresh-token`. `CLAUDE.md` la documenta como el mecanismo
de revocación:

> **`REFRESH_TOKEN`** — tabla. Campos: token (unique), usuario_id (FK), expiresAt, revocado,
> creadoEn. Persistencia en BD permite revocación global (logout).

**`AuthService` no la usa.** Usa `Sesion`, con `refreshHash` (HMAC-SHA256 del token, no el
token plano) y rotación en cada refresh. `RefreshToken` no aparece en ningún flujo vivo.

Son dos mecanismos para lo mismo, uno documentado y muerto, otro vivo y sin documentar. Es el
patrón §4.2 del playbook, y el peligro concreto es que alguien lea `CLAUDE.md`, encuentre la
entidad, y construya la revocación de sesiones de T-BE-09 sobre la tabla equivocada.

## Alcance

- Verificar por mutación las garantías estructurales de estas entidades.
- Resolver `RefreshToken`: borrarla, o documentar por qué convive con `Sesion`.
- Alinear `CLAUDE.md` con el mecanismo que realmente corre.

## Fuera de alcance

- **No** se reescriben las entidades.
- **No** se toca `Sesion` ni el flujo de auth: eso es T-BE-08 y T-BE-09.

## Criterio de cierre

1. `UsuarioRepositoryTest` pasa, no da ERROR.
2. Las garantías del spec tienen su mutación ejecutada con el nombre del test que cayó.
3. `RefreshToken` está borrada o justificada por escrito, y `CLAUDE.md` dice la verdad.

## Depende de

`back/2026-09-07-fix-liquibase-rbac-order`.
