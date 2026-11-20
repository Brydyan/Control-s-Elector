# Shortcut Ticket Management

Generador de tickets automático para Shortcut desde ODDs/SDDs.

## Setup (una sola vez)

### 1. Obtener API Key

1. Ve a https://app.shortcut.com/settings/account/api-tokens
2. Crea un nuevo token (read-write)
3. Cópialo

### 2. Configurar .env

```bash
# Copiar template
cp config/shortcut.env.template .env

# Editar .env y pegar tu API key
nano .env
```

```env
SHORTCUT_API_KEY=sct_rw_upse_XXXXXXXXXXXXXXXXXXXXXXXXXXXX
EPIC_ID=108
WORKFLOW_STATE_ID=500000006
```

**⚠️ IMPORTANTE:** Nunca commitear `.env` a git. Ya está en `.gitignore`.

## Crear Tickets

### Opción 1: Desde archivo JSON

```bash
./scripts/create-shortcut-tickets-generic.sh data/tickets/odd-consolidate-modal.json
```

**Output esperado:**
```
ℹ️  Validating API key...
✅ API key valid (user: Andy Alejandro)
ℹ️  Validating epic ID 108...
✅ Epic found: Admin › Gestión de Usuarios

ℹ️  Creating tickets from data/tickets/odd-consolidate-modal.json...
ℹ️  Epic: Admin › Gestión de Usuarios (ID: 108)

  [1] ODD — Consolidate Usuario Invitation Modal ... ✅ ID: 12345

✅ 1/1 tickets created successfully

📊 View in Shortcut: https://app.shortcut.com/epics/108
```

### Opción 2: Con epic diferente

```bash
./scripts/create-shortcut-tickets-generic.sh data/tickets/odds.json --epic=109
```

### Opción 3: Usar API key de variable de entorno

```bash
SHORTCUT_API_KEY=sct_rw_... ./scripts/create-shortcut-tickets-generic.sh data/tickets/odds.json
```

## Formato de Tickets (JSON)

```json
{
  "meta": {
    "date": "2026-11-19",
    "epic": "Admin › Gestión de Usuarios",
    "feature": "T-AUTH-INV-02 — User Invitations",
    "description": "Brief overview"
  },
  "tickets": [
    {
      "name": "ODD/SDD — Title",
      "description": "Long description with **markdown**\n\nMultiline supported.",
      "estimate": "3"
    },
    {
      "name": "Task 2",
      "description": "...",
      "estimate": "5"
    }
  ]
}
```

**Campos:**
- `name` (required) — Título del ticket
- `description` (optional) — Descripción (Markdown soportado, con `\n` para saltos)
- `estimate` (optional) — Story points (numérico)

## Estructura de Carpetas

```
├── config/
│   └── shortcut.env.template          # Template (no editar)
├── data/tickets/
│   ├── odd-consolidate-modal.json     # Ejemplo: ODD modal consolidation
│   ├── odds-batch-2026-11-19.json     # Batch de múltiples ODDs
│   └── sdds-compliance.json           # SDDs de compliance
└── scripts/
    ├── create-shortcut-tickets-generic.sh  # Script principal
    ├── create-shortcut-tickets.sh          # ⚠️ DEPRECATED (volver a verificar)
    └── create-new-sdd-tickets.sh           # ⚠️ DEPRECATED (volver a verificar)
```

## Crear Nuevos Archivos de Tickets

**Para cada ODD/SDD:**

1. Crear archivo en `data/tickets/`:
   ```bash
   cp data/tickets/odd-consolidate-modal.json data/tickets/odd-mi-nueva-tarea.json
   ```

2. Editar con tus tickets:
   ```json
   {
     "meta": { ... },
     "tickets": [ { "name": "...", "description": "..." } ]
   }
   ```

3. Ejecutar:
   ```bash
   ./scripts/create-shortcut-tickets-generic.sh data/tickets/odd-mi-nueva-tarea.json
   ```

## Validaciones

El script valida automáticamente:

✅ API key válida (antes de hacer nada)  
✅ Epic exists en Shortcut  
✅ Archivo JSON válido  
✅ Campos requeridos en cada ticket  

Si algo falla, muestra el error específico:

```
❌ Failed (HTTP 400: "Name must not be blank")
❌ Invalid API key or connection failed.
❌ Epic not found: 999
```

## Troubleshooting

### "Invalid API key"

```bash
# Verificar que .env existe
cat .env | grep SHORTCUT_API_KEY

# O pasar token directamente
SHORTCUT_API_KEY=sct_rw_... ./scripts/create-shortcut-tickets-generic.sh data/tickets/odds.json
```

### "Epic not found"

```bash
# Ver URL en Shortcut: https://app.shortcut.com/epics/108
# El número es el EPIC_ID

./scripts/create-shortcut-tickets-generic.sh data/tickets/odds.json --epic=108
```

### "Failed (HTTP 400: ...)"

Ver mensaje de error. Generalmente:
- Nombre vacío
- JSON mal formado
- Workflow state no existe

## Legacy Scripts

**Deprecados (pero pueden seguir usándose):**
- `scripts/create-shortcut-tickets.sh` — API key hardcodeada ⚠️
- `scripts/create-new-sdd-tickets.sh` — API key desde .env, pero específico

**Recomendar usar:** `scripts/create-shortcut-tickets-generic.sh` + JSON

## Próximos Pasos

- [ ] Crear `data/tickets/odds-batch-2026-11-19.json` con múltiples ODDs
- [ ] Integrar con CI/CD (auto-crear tickets en cada SDD?)
- [ ] Validar workflow states disponibles en tu Shortcut workspace
- [ ] Documentar API IDs reales (epic, estado, etc)
