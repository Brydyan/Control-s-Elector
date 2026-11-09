# Skill: /01-qa-review

**Función:** Decidir si una solicitud es **SDD (Software Design Document)** o **ODD (Implementation Routing)**.

**Entrada:** Descripción del cambio deseado (texto libre, conversación normal).

**Salida:** Recomendación clara + siguiente paso.

---

## Lógica de Decisión

### Preguntas Clasificatorias

1. **¿Es nueva funcionalidad completa o ajuste?**
   - Nueva subsystem (usuarios, actas, resultados) → SDD
   - Ajuste a subsystem existente (cambiar email, 24h expiry) → ODD

2. **¿Afecta arquitectura, contrato de API o schema?**
   - Tabla nueva, endpoint nuevo, cambio request/response → SDD
   - Toggle en UI existente, cambio de número en migration → ODD

3. **¿Cuántos equipos/roles toca?**
   - Múltiples (backend + frontend + design + infra) → SDD
   - Un equipo o uno-dos componentes → ODD

---

## Ejemplos

### SDD (New Feature)
- "Necesito un sistema de observadores que registren actas electorales"
- "La app debe tener resguardo de evidencia fotográfica (S3)"
- "Los usuarios deben poder crear partidos y candidatos"
- "La interface de login debe ser responsive en móvil"

### ODD (Adjustment)
- "El email de invitación muestra contraseña pero no debería"
- "El sidebar marca activos Dashboard Y Resultados al mismo tiempo"
- "Necesitamos skeleton loaders en lugar de spinners"
- "Agregar botón Re-enviar Invitación a tabla de usuarios"
- "Cambiar expiry de invitación de 30 días a 24 horas"

---

## Salida

**Si SDD:**
```
RECOMENDACIÓN: SDD (Software Design Document)

Próximos pasos:
1. Crear directorio: openspec/changes/<área>/<YYYY-MM-DD>-<slug>/
2. Crear proposal.md (qué, por qué, fuera de scope)
3. Crear design.md (decisiones D1, D2, D3 con alternativas descartadas)
4. Crear specs/<dominio>/spec.md (requisitos GIVEN/WHEN/THEN)
5. Crear tasks.md (tareas A-E con mutaciones y compuertas)
6. Lanzar: /02-build-sdd openspec/changes/<área>/<YYYY-MM-DD>-<slug>/
```

**Si ODD:**
```
RECOMENDACIÓN: ODD (Implementation Routing)

Próximos pasos:
1. Crear archivo: odd/tasks/<feature>.md
2. Documentar tasks A-E con criterios de aceptación
3. Especificar SDD padre si aplica (si es ajuste)
4. Lanzar: /02-build-odd odd/tasks/<feature>.md
```

---

## Guardar en Engram

Si detectas cambios visuales/UX que requieran skills adicionales, guarda nota:
```
Tipo: ODD #504
Skills requeridas: /ux-designer (modal de re-envío), /taste-skill (email template)
```

