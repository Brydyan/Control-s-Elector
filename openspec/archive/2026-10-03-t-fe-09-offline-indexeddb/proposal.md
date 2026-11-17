# Proposal — T-FE-09: Resiliencia Offline, Cola IndexedDB y Desbloqueo por PIN

## Qué se construye
Mecanismo integral de resiliencia para el veedor electoral en condiciones de conectividad nula o intermitente:
1. **Desbloqueo Local por PIN de 4 Dígitos (Anti-Lockout)**:
   - Al iniciar sesión con internet, el veedor configura un PIN de 4 dígitos cuyo hash con sal se almacena localmente.
   - Si el JWT (expiración de 15m) vence y el dispositivo está desconectado (`!navigator.onLine`), la app no redirige al login web (lo que dejaría al veedor bloqueado sin poder entrar al sistema). En su lugar, despliega una pantalla de desbloqueo rápido por PIN para mantener la sesión de trabajo abierta.
2. **Cola Persistente de Transmisión (IndexedDB)**:
   - Cuando un envío a `/api/v1/observer/actas` falla por pérdida de red, el payload y la referencia de imagen se almacenan en una cola local `pending_actas`.
3. **Sincronización Automática en Segundo Plano**:
   - Escucha reactiva del evento `window.addEventListener('online')` y reintentos temporizados cada 30 segundos.
   - Al recuperar la señal, transmite las actas pendientes en orden secuencial hacia el backend y notifica al usuario.
4. **Fallback a Almacenamiento Privado de Archivos**:
   - Si la cuota de IndexedDB se supera o el navegador restringe el storage, se utiliza Capacitor Filesystem como respaldo seguro en el sandbox de la aplicación.

## Por qué
- En zonas rurales de la península de Santa Elena (comunas de Manglaralto, Colonche, Chanduy), la señal celular se pierde con frecuencia o se satura durante la jornada electoral.
- Un veedor no puede perder los datos digitados ni quedar bloqueado fuera de la app por vencimiento de token JWT mientras está en el recinto de votación sin internet.

## Alcance

| Elemento | Incluye | No incluye |
|----------|---------|-----------|
| Seguridad | PIN local de 4 dígitos con hash SHA-256 + sal | Autenticación biométrica nativa compleja |
| Almacenamiento | IndexedDB (`pending_actas`) + Capacitor Filesystem | Replicación bidireccional de toda la base de datos |
| Reintentos | Listener `online` + interval 30s | Sincronización P2P entre celulares |
| UI | Banner de estado offline/sincronizando + pantalla de PIN | Dashboard offline para coordinadores |

## Criterio de aceptación

- [ ] Veedor puede configurar y verificar su PIN de 4 dígitos.
- [ ] Con JWT expirado y sin red, el modal de PIN permite continuar trabajando sin pedir credenciales remotas.
- [ ] Envíos fallidos por error de red se encolan en IndexedDB con estado de reintento.
- [ ] Al detectar conexión, sincroniza automáticamente llamando a `POST /api/v1/observer/actas` y subiendo su imagen asociada.
- [ ] Elimina los registros de la cola local únicamente cuando el servidor responde HTTP 200/201.

## Fuera de alcance

- Descarga previa de todas las actas de la provincia para consulta offline.
- Modificación colaborativa offline del mismo registro.

## Dependencias

- T-FE-03 (TokenService / AuthService).
- T-FE-07 (RegistrarActaPage).
- T-FE-08 (Manejo de blobs de imagen).
