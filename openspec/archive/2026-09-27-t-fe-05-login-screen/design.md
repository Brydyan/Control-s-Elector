# Design — T-FE-05: Pantalla de Login

## D1 — Ubicación de LoginComponent

**Decisión:** `features/auth/login/`. Auth es una feature, no un componente compartido.

**Alternativa descartada — `shared/components/login.component.ts`.** Login no es reutilizable. Única ocurrencia.

## D2 — Type de Password Input

**Decisión:** `type="password"`. Sin toggle de visibilidad (MVP).

**Alternativa descartada — mostrar toggle "Mostrar contraseña".** Funcional pero fuera de scope MVP. Se agrega después si se necesita.

## D3 — Validación en tiempo real vs al enviar

**Decisión:** Validación en tiempo real (dirty + touched). Mostrar errores solo si campo fue tocado.

**Alternativa descartada — validación solo al enviar.** Menos UX feedback.

## D4 — Redirección después del login

**Decisión:** `AuthService.login()` retorna {user}. LoginComponent lee `user.rol` y redirige vía Router.

**Alternativa descartada — backend retorna URL de redirección.** El cliente conoce su propia arquitectura de rutas.
