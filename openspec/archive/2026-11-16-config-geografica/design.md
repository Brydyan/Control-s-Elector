# Diseño — Configuración Geográfica Jerárquica

**Versión:** 1.0  
**Estado:** 📋 Propuesta de Decisiones

---

## D1 — Estructura del Árbol (Recursivo vs Component)

### Decisión
**Tree component de PrimeNG (p-tree)**

### Alternativa Descartada
Recursión manual con `ng-container` y `*ngFor`:
- ✅ Control total
- ❌ 2x líneas de código
- ❌ Keyboard navigation manual (accesibilidad rota)
- ❌ Expand/collapse state management complejo

### Por qué
- PrimeNG Tree ya implementa WCAG 2.2 keyboard navigation
- Expand/collapse state incluido
- Lazy load de nodos (si lo necesitamos después)
- Badges, iconos, contexto menu built-in

### Implementación
```typescript
// Backend devuelve:
{
  key: 'provincia-24',
  label: 'Santa Elena',
  children: [
    {
      key: 'canton-2401',
      label: 'Santa Elena',
      icon: 'pi pi-fw pi-building',
      children: [...]
    }
  ]
}

// Frontend:
<p-tree [value]="geografiaTree" [contextMenu]="cm"></p-tree>
```

---

## D2 — Modales (Por Nivel vs Genérico)

### Decisión
**Modales especializados por nivel (crear-provincia, crear-canton, crear-parroquia, crear-jrv)**

Cada modal conoce:
- Nivel de entidad
- Parent_id automático
- Campos específicos (ej: JRV tiene `total_electores`, Parroquia tiene `es_urbana`)

### Alternativa Descartada
Modal genérico reutilizable con `[type]` input:
- ✅ Código DRY
- ❌ Lógica condicional compleja
- ❌ Validaciones entrelazadas
- ❌ Difícil de mantener cuando campos difieren

### Por qué
- Cada entidad tiene campos distintos (JRV: `total_electores`, Parroquia: `es_urbana`, Cantón: `codigo`)
- Validaciones diferentes por nivel
- Errors específicos ("No puede haber 2 cantones con mismo código en provincia")
- Modales especializados = código más legible

### Componentes
- `modal-crear-provincia.component`
- `modal-crear-canton.component`
- `modal-crear-parroquia.component`
- `modal-crear-jrv.component`

---

## D3 — Validaciones (Cascada vs Individual)

### Decisión
**Cascada (no permitir eliminar si tiene hijos)**

```
No puedo eliminar Cantón Santa Elena
↓ Tiene 2 parroquias: Santa Elena, Ballenita
↓ Santa Elena tiene 2 JRVs
→ Error: "Elimine todas las parroquias primero"
```

### Alternativa Descartada
Eliminar en cascada (eliminar cantón = eliminar parroquias = eliminar JRVs):
- ✅ Rápido
- ❌ **Destructivo sin confirmación explícita**
- ❌ Pierde datos sin opción de arrepentirse
- ❌ Electoral context: data de JRVs es crítica

### Por qué
- Control electoral = datos críticos
- Master debe ver exactamente qué va a eliminar
- Confirmación explícita en cada nivel
- Reversible: editor tiene control total

### Implementación
```typescript
// DELETE /api/v1/master/cantones/{id}
// Response: 409 Conflict si tiene parroquias
{
  "error": "Cannot delete canton with 2 children parroquias",
  "childrenCount": 2,
  "hint": "Delete children first or use cascade flag"
}
```

---

## D4 — Persistencia de Estado (Expandir/Colapsar)

### Decisión
**Estado en memoria (session, NO localStorage)**

Cuando Master navega a otra página y vuelve, árbol se reiniializa (colapsa todo).

### Alternativa Descartada
Guardar estado en localStorage (remembers "qué estaba expandido"):
- ✅ User experience: vuelvo y está donde dejé
- ❌ localStorage no es seguro para admin context
- ❌ Bug: stale state si geografía cambió en otro navegador/admin
- ❌ Complejidad: sincronizar estado

### Por qué
- Geografía es **data crítica** (coordinación electoral)
- Si otro admin eliminó un cantón, localStorage estaría "expandiendo" nodo que no existe
- Session ephemeral es más seguro
- Master reconocimiento: "Volvá al árbol, necesito expandir de nuevo" = OK para admin tool

### Implementación
```typescript
// Solo en memory, NO localStorage
expandedNodes: Set<string> = new Set();

onNodeExpand(event: any) {
  this.expandedNodes.add(event.node.key);
}

onNodeCollapse(event: any) {
  this.expandedNodes.delete(event.node.key);
}
```

---

## Resumen de Decisiones

| Decisión | Elegida | Razón |
|----------|---------|-------|
| **D1** | PrimeNG Tree | Accesibilidad + expansión automática |
| **D2** | Modales especializados | Validaciones claras por nivel |
| **D3** | Cascada (bloquear) | Safety data crítica |
| **D4** | Session memory | Coherencia si geografía cambia |

---

## Próximos Pasos

→ Pasar a **specs/** para definir:
- Backend endpoints exactos (request/response)
- Frontend tree structure y modales
