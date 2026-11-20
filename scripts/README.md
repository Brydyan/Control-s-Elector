# Scripts Directory

## Shortcut Ticket Generation

### ✅ Current (Recommended)

**`create-shortcut-tickets-generic.sh`**
- Genérico y reutilizable
- API key desde `.env` (seguro)
- Validación de API key antes de crear
- Manejo de errores mejorado
- Soporta múltiples tickets desde JSON

**Usage:**
```bash
./create-shortcut-tickets-generic.sh data/tickets/odd-consolidate-modal.json
```

**Documentation:** `../SHORTCUT_TICKETS.md`

---

### ⚠️ Deprecated (Legacy)

**`create-shortcut-tickets.sh`** (2026-11-05)
- 🔴 API key hardcodeada en el código
- 🔴 Solo para 8 tickets específicos (compliance SDDs)
- 🔴 No reutilizable
- ❌ No muestra detalles de error si falla

**Status:** DO NOT USE  
**Reason:** Security risk + not maintainable

---

**`create-new-sdd-tickets.sh`** (2026-11-06)
- ✅ API key desde `.env`
- ✅ Muestra detalles de error
- 🔴 Solo para 2 tickets específicos (infrastructure + legal)
- 🔴 No reutilizable

**Status:** Puede usarse como fallback, pero preferir `create-shortcut-tickets-generic.sh`  
**Migration:** Pasar tickets a `data/tickets/*.json` y usar script genérico

---

## Archivos de Ticket (JSON)

Ubicación: `../data/tickets/`

### Existentes

- `odd-consolidate-modal.json` — ODD modal consolidation (2026-11-19)
- `odds-batch-2026-11-19.json` — Batch de múltiples ODDs

### Crear nuevos

```bash
# Copiar template
cp data/tickets/odd-consolidate-modal.json data/tickets/mi-nueva-tarea.json

# Editar
nano data/tickets/mi-nueva-tarea.json

# Validar
./scripts/validate-shortcut-json.sh data/tickets/mi-nueva-tarea.json

# Crear tickets en Shortcut
./scripts/create-shortcut-tickets-generic.sh data/tickets/mi-nueva-tarea.json
```

---

## Utilities

**`validate-shortcut-json.sh`**
- Valida JSON files antes de crear tickets
- Chequea estructura requerida
- Reporta errores específicos

**Usage:**
```bash
./scripts/validate-shortcut-json.sh data/tickets/*.json
```

---

## Configuration

**`.env`** (no commitear — ya está en `.gitignore`)
```bash
SHORTCUT_API_KEY=sct_rw_...
EPIC_ID=108
WORKFLOW_STATE_ID=500000006
```

**Template:** `../config/shortcut.env.template`

---

## Migration Path

### De `create-shortcut-tickets.sh`:

1. Extraer tickets de `create-shortcut-tickets.sh` (líneas 57-89)
2. Crear `data/tickets/sdds-compliance.json` con estructura JSON
3. Ejecutar: `./create-shortcut-tickets-generic.sh data/tickets/sdds-compliance.json`

### De `create-new-sdd-tickets.sh`:

1. Extraer tickets (líneas 64-70)
2. Crear `data/tickets/sdds-infrastructure-legal.json`
3. Ejecutar: `./create-shortcut-tickets-generic.sh data/tickets/sdds-infrastructure-legal.json`

---

## Next Steps

- [ ] Migrar tickets de scripts deprecated → JSON files
- [ ] Eliminar scripts deprecated (una vez migrados)
- [ ] Integrar con CI/CD (auto-crear tickets en cada SDD?)
- [ ] Documentar workflow state IDs disponibles
- [ ] Crear script para auto-generar JSON desde ODD/SDD markdown?
