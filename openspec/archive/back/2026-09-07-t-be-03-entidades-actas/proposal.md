# Proposal — T-BE-03 · Entidades de acta, auditoría y notificación

Origen: `documents/task-control/backendtask/COMPLETADO-T-BE-03.md` · Área: back
Fecha: 2026-09-07 · **Change retrospectivo**

---

## Qué cubre

`Acta`, `ActaDetalle`, `AuditLog`, `Notificacion` y el enum `EstadoActa`. Es el núcleo del
sistema: todo lo demás existe para llenar estas tablas o para leerlas.

## Estado real

Las cuatro entidades existen. **Ningún test las cubre**: `CoordinatorControllerTest` y
`ObserverControllerTest` las ejercitan de refilón y los 22 están en ERROR.

## Lo que este change tiene que aclarar

**1. `Acta` tiene dos nombres para lo mismo.** `ActaRepository.findWithFilters` ordena por
`a.fechaRegistro`; `backend.md:46` describe el campo como `registrada_en`; `T-BE-13.md:375`
lo llama `getRegistradaEn()`. Sólo uno existe. Quien escriba el dashboard contra el nombre
equivocado no compila — o peor, compila contra un getter distinto.

**2. `ANULADA` es final, y eso no lo garantiza nadie.** `CLAUDE.md` dice que es irreversible.
`ActaService` lo consigue porque ningún método acepta ANULADA como estado de partida — es
decir, por ausencia, no por una regla explícita. Funciona hoy; el día que alguien añada una
transición nueva, nada se lo impide.

**3. Los datos no se borran al anular.** `ActaService.anular` cambia el estado y guarda
`motivoAnulacion`; los `ActaDetalle` siguen ahí. Eso es correcto y no está probado.

**4. `Notificacion` se crea en disputa y en anulación, pero no en aprobación.**
`ActaService.aprobar` no llama a `notificacionService`. ¿Es deliberado —al OBSERVER no le
interesa que su acta se aprobó— o es un olvido? `backend.md:117` dice «Notificación automática
al cambiar estado», sin excepciones.

## Alcance

- Verificar por mutación las garantías estructurales de estas cuatro entidades.
- Fijar el nombre real del campo de fecha y corregir los documentos que lo llaman de otra
  forma.
- Resolver si falta la notificación de aprobación.

## Fuera de alcance

- **No** se toca la lógica de `ActaService`: es T-BE-06 y T-BE-07.
- **No** se implementa el envío real de notificaciones (Supabase Realtime): es T-IN-06.

## Criterio de cierre

1. Cada garantía del spec tiene su mutación ejecutada, con el nombre del test que cayó.
2. El campo de fecha tiene un solo nombre, y `backend.md` y `T-BE-13.md` lo usan.
3. La pregunta de la notificación de aprobación está respondida por escrito.

## Depende de

`back/2026-09-07-fix-liquibase-rbac-order`.
