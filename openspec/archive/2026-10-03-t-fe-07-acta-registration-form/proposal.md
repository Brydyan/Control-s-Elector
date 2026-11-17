# Proposal — T-FE-07: Formulario de Registro de Acta (Wizard 3 Pasos)

## Qué se construye
Formulario guiado por pasos (Wizard) para el registro y transmisión de actas electorales por el veedor:
- **Paso 1 (VOTOS)**: Carga dinámica del padrón oficial de candidatos por dignidad (`GET /api/v1/configuracion/candidatos?cargoId=...`). Inputs numéricos para cada candidato, votos en blanco, votos nulos y total de sufragantes. Validación en tiempo real del invariante electoral: $\sum \text{votos} = \text{totalSufragantes}$.
- **Paso 2 (IMAGEN)**: Captura fotográfica del acta física de escrutinio con vista previa inmediata.
- **Paso 3 (REVISIÓN)**: Resumen consolidado del escrutinio y confirmación de envío.
- **Resiliencia Local**: Autoguardado en local storage del dispositivo (`ActaStorageService`) para evitar pérdida de datos si la aplicación se cierra inesperadamente.
- **Opciones de Envío**: Guardar como `BORRADOR` (`POST /api/v1/observer/actas/borrador`) o transmisión oficial como `ENVIADA` (`POST /api/v1/observer/actas`).

## Por qué
- El registro de votos bajo presión en la noche electoral requiere una interfaz a prueba de errores de digitación.
- La validación matemática inmediata en el cliente evita que actas con inconsistencias aritméticas lleguen al coordinador sin advertencia.
- El flujo paso a paso asegura que ningún acta sea transmitida sin su correspondiente respaldo fotográfico.

## Alcance

| Elemento | Incluye | No incluye |
|----------|---------|-----------|
| Componente | `RegistrarActaPage` (`/observer/jrv/:jrvId/cargo/:cargoId/registrar`) | Administración global de actas (T-FE-10) |
| Flujo | Wizard 3 pasos: `VOTOS` -> `IMAGEN` -> `REVISION` | Edición post-aprobación |
| Datos | Carga de candidatos y partidos desde `/api/v1/configuracion/*` | Creación manual de candidatos por el veedor |
| Invariante | Cálculo y badge visual de diferencia de cuadre en tiempo real | Anulación de votos en cliente |
| Almacenamiento | Borrador local inmediato en `ActaStorageService` | Sincronización offline en segundo plano (T-FE-09) |
| Transmisión | Creación en borrador o directa + subida de evidencia fotográfica | Reintento automático en cola IndexedDB |

## Criterio de aceptación

- [ ] Carga candidatos del cargo seleccionado vía `ObserverService.getCandidatosPorCargo(cargoId)`.
- [ ] Recupera automáticamente el borrador local si el veedor ya había digitado datos para esa JRV y cargo.
- [ ] Calcula en tiempo real: `sumaVotosCandidatos`, `totalVotosComputados`, `diferenciaChecksum` e `isChecksumValid`.
- [ ] No permite avanzar a transmisión final si la suma no es matemáticamente idéntica al `totalSufragantes`.
- [ ] Permite capturar y previsualizar la fotografía del acta física.
- [ ] `guardarComoBorrador()` envía `ActaRequest` a `/api/v1/observer/actas/borrador` (permite cuadre incompleto).
- [ ] `enviarActaFinal()` exige cuadre estricto y envía a `/api/v1/observer/actas`.
- [ ] Tras el registro exitoso del acta, sube la imagen asociada a `/api/v1/observer/actas/{id}/imagen` con clave multipart `'imagen'`.

## Fuera de alcance

- Compresión avanzada de imagen en WebP (T-FE-08).
- Cola offline persistente con IndexedDB (T-FE-09).
- Resolución de controversias legales (T-FE-10).

## Dependencias

- T-FE-06 (Navegación desde `JrvCargosPage`).
- T-FE-08 (Módulo de compresión/captura de imagen).
