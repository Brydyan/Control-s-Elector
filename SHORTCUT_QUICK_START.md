# Shortcut Tickets — Quick Start

Setup en 3 pasos. Crear tickets automáticos en Shortcut desde JSON.

---

## 1️⃣ Setup (una sola vez)

### Obtener API Key
1. Ve a https://app.shortcut.com/settings/account/api-tokens
2. Crea token "read-write"
3. Cópialo

### Configurar
```bash
# Copiar template
cp config/shortcut.env.template .env

# Editar y pegar tu API key
nano .env
```

**Resultado esperado en `.env`:**
```env
SHORTCUT_API_KEY=sct_rw_upse_XXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXX
EPIC_ID=108
WORKFLOW_STATE_ID=500000006
```

✅ **DONE** — Solo repetir para cada máquina nueva

---

## 2️⃣ Crear Archivo de Tickets

**Archivo:** `data/tickets/mi-tarea.json`

```json
{
  "meta": {
    "date": "2026-11-19",
    "epic": "Admin › Gestión de Usuarios",
    "feature": "Mi nueva feature"
  },
  "tickets": [
    {
      "name": "ODD — Mi primera tarea",
      "description": "Descripción larga...\n\nCon múltiples líneas.",
      "estimate": "3"
    }
  ]
}
```

**Campos:**
- `name` (requerido) — Título
- `description` (opcional) — Descripción con `\n` para saltos
- `estimate` (opcional) — Story points

✅ **DONE** — Puedes editar `data/tickets/odd-consolidate-modal.json` como template

---

## 3️⃣ Crear Tickets en Shortcut

```bash
./scripts/create-shortcut-tickets-generic.sh data/tickets/mi-tarea.json
```

**Output:**
```
ℹ️  Validating API key...
✅ API key valid (user: Andy Alejandro)

ℹ️  Creating tickets from data/tickets/mi-tarea.json...
ℹ️  Epic: Admin › Gestión de Usuarios (ID: 108)

  [1] ODD — Mi primera tarea ... ✅ ID: 12345

✅ 1/1 tickets created successfully

📊 View in Shortcut: https://app.shortcut.com/epics/108
```

✅ **DONE** — Ve a Shortcut y los tickets están listos

---

## Ejemplos Rápidos

### Crear 1 ticket para mi ODD

```bash
# 1. Crear archivo
cat > data/tickets/odd-mi-tarea.json << 'EOF'
{
  "meta": {"date": "2026-11-19"},
  "tickets": [
    {
      "name": "ODD — Mi tarea",
      "description": "Una descripción"
    }
  ]
}
EOF

# 2. Crear en Shortcut
./scripts/create-shortcut-tickets-generic.sh data/tickets/odd-mi-tarea.json
```

### Crear múltiples tickets

```bash
# Editar este archivo (ya existe)
nano data/tickets/odds-batch-2026-11-19.json

# Agregar tus tickets en .tickets[]

# Crear todos
./scripts/create-shortcut-tickets-generic.sh data/tickets/odds-batch-2026-11-19.json
```

### Usar epic diferente

```bash
./scripts/create-shortcut-tickets-generic.sh data/tickets/odds.json --epic=109
```

### Validar JSON antes de crear

```bash
./scripts/validate-shortcut-json.sh data/tickets/*.json
```

---

## Troubleshooting

| Error | Solución |
|-------|----------|
| `SHORTCUT_API_KEY: command not found` | `.env` no existe o no tiene SHORTCUT_API_KEY |
| `Invalid API key` | Verifica que copiaste la API key correctamente de Shortcut |
| `Epic not found: 108` | Verifica el epic ID en la URL de Shortcut: `/epics/108` |
| `jq: command not found` | Instala: `apt install jq` (Linux) o `brew install jq` (Mac) |

---

## Próximos Pasos

📄 **Documentación completa:** `SHORTCUT_TICKETS.md`  
📄 **Scripts details:** `scripts/README.md`

```bash
# Leer docs
cat SHORTCUT_TICKETS.md | less
cat scripts/README.md | less
```

---

## Resumen del Flujo

```
ODD/SDD creado
       ↓
   📄 JSON file
   (data/tickets/xxx.json)
       ↓
   ✅ Validar
   (./scripts/validate-shortcut-json.sh)
       ↓
   📊 Create Tickets
   (./scripts/create-shortcut-tickets-generic.sh)
       ↓
   ✅ Listo en Shortcut
   (https://app.shortcut.com/epics/108)
```
