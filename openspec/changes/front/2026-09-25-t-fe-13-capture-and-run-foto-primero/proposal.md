# Proposal — T-FE-13: Modo "Foto Primero" (Capture & Run) y Cotas Electorales

## Qué se construye
Adaptación de la interfaz del veedor electoral para responder a la realidad física del cierre de mesas en Santa Elena:
1. **Flujo "Foto Primero" en Wizard de Registro (`RegistrarActaPage`)**:
   - Reordenamiento de pasos: `IMAGEN (EVIDENCIA)` (Paso 1) -> `VOTOS` (Paso 2) -> `REVISION` (Paso 3).
   - El veedor toma la fotografía del acta física al inicio. Si los militares o la policía desalojan el aula antes de terminar la digitación numérica, la evidencia fotográfica ya está capturada y almacenada en el dispositivo.
2. **Botón de Captura Rápida en `JrvCargosPage`**:
   - Acceso directo para fotografiar en ráfaga las actas de todas las dignidades de la mesa en menos de 1 minuto tras el escrutinio.
3. **Cotas de Sufragantes y Regla de Invariante CNE**:
   - **Alerta preventiva de padrón**: Advertencia visual si `totalSufragantes > 350` (capacidad estándar CNE).
   - **Tope duro de seguridad**: Bloqueo estricto si `totalSufragantes > 500` (previene error de tipeo tipo 2500 o 3500 en mesas especiales).
   - **Invariante Matemático Estricto**: $\sum \text{votos candidatos} + \text{blancos} + \text{nulos} = \text{totalSufragantes}$ para todas las dignidades.
   - **Soporte CPCCS y Pluripersonales**: Gestión de dignidades pluripersonales con actas unívocas por cargo o sub-lista donde cada voto cuadra 1:1 contra los sufragantes.

## Por qué
- Exigir que el veedor digite primero decenas de candidatos antes de tomar la foto es un riesgo operativo grave en recintos donde se corta la energía o las autoridades ordenan evacuar el aula.
- Limitar el total de sufragantes a un rango electoral verosímil (< 500) evita que se transmitan actas con errores de teclado desmedidos.

## Alcance

| Elemento | Incluye | No incluye |
|----------|---------|-----------|
| Wizard | Pasos reordenados: 1: Foto, 2: Votos, 3: Revisión | OCR automático en cliente |
| Cotas | Warning > 350 sufragantes, error duro > 500 | Modificación del padrón electoral |
| Invariante | Chequeo estricto $\sum = \text{sufragantes}$ (cuadre 1:1) | Distribución D'Hondt |
| Almacenamiento | Almacenamiento previo de la foto en borrador local | Envío a servidores no autorizados |

## Criterio de aceptación

- [ ] Al entrar a registrar un acta, el Paso 1 solicita la captura de la fotografía de evidencia.
- [ ] Permite avanzar al Paso 2 de votos con la foto ya asegurada y previsualizada.
- [ ] Muestra badge de advertencia si sufragantes supera 350.
- [ ] Bloquea el botón de envío si sufragantes supera 500.
- [ ] Valida que la suma de votos coincida exactamente con el total de sufragantes.
- [ ] Permite guardar borrador con solo la foto capturada.

## Dependencias

- `T-FE-07` y `T-FE-08`.
