# Flujo 03.2 — Obtener Datos Usuario Actual (GET /api/v1/auth/me)

Escenario: FE obtiene información del usuario autenticado (parseado desde JWT en SecurityContext).

```mermaid
sequenceDiagram
    actor USER as Usuario
    participant APP as App Angular/Ionic
    participant API as Spring Boot API
    participant PG as PostgreSQL

    USER->>APP: Necesita info del usuario actual
    APP->>API: GET /api/v1/auth/me<br/>(Authorization: Bearer access_token)

    API->>API: JwtAuthFilter valida token → SecurityContext.getAuthentication()
    API->>API: Extrae email del Principal
    API->>PG: SELECT * FROM usuario WHERE email = ?
    PG-->>API: Usuario {id, email, rol, publicId, ...}

    API->>API: UsuarioMapper.toDto(usuario)<br/>→ CurrentUserDto {id, email, rol, permisos[]}
    
    API-->>APP: 200 OK {id, email, rol, permisos: ['usuario:read', ...]}
    APP->>APP: Guarda en AuthStore o local component state
    APP-->>USER: ✅ Info del usuario disponible para UI
```

---

## Response DTO

```typescript
// CurrentUserDto (frontend/app/src/app/core/auth/data/auth.dto.ts)
{
  id: number;           // PK interno de PostgreSQL
  email: string;        // Email único
  rol: string;          // 'MASTER' | 'COORDINATOR' | 'ADMIN' | 'OBSERVER'
  permisos: string[];   // ['usuario:read', 'acta:approve', ...] (basado en rol + rol_permiso)
}
```

---

## Casos de Uso

1. **Verificar identidad post-login**
2. **Mostrar perfil del usuario** (email, rol) en UI
3. **Validar permisos en FE** antes de mostrar opciones
4. **Refrescar datos usuario** después de cambios de admin

