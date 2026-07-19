# Flujo 08b — Diagrama de Casos de Uso

Vista de alto nivel de las acciones disponibles por actor. Orientado a presentación con stakeholders no técnicos.

---

## OBSERVER

```mermaid
graph LR
    OBS([👤 Observer])

    OBS --> UC1[Iniciar sesión]
    OBS --> UC2[Seleccionar JRV y cargo]
    OBS --> UC3[Ingresar votos por candidato]
    OBS --> UC4[Capturar foto del acta física]
    OBS --> UC5[Enviar acta]
    OBS --> UC6[Ver correcciones pendientes]
    OBS --> UC7[Corregir acta solicitada]
    OBS --> UC8[Sincronizar actas offline]
    OBS --> UC9[Exportar evidencia manualmente]

    UC5 -.->|incluye| UC4
    UC8 -.->|extiende| UC5
    UC9 -.->|extiende| UC8
```

---

## COORDINATOR

```mermaid
graph LR
    COORD([👤 Coordinator])

    COORD --> UC1[Iniciar sesión]
    COORD --> UC2[Ver todas las actas]
    COORD --> UC3[Comparar foto vs datos digitados]
    COORD --> UC4[Marcar acta EN DISPUTA]
    COORD --> UC5[Corregir acta directamente]
    COORD --> UC6[Aprobar acta corregida]
    COORD --> UC7[Ver dashboard de resultados]
    COORD --> UC8[Recibir notificación de corrección]

    UC4 -.->|incluye| UC3
    UC6 -.->|incluye| UC3
```

---

## MASTER

```mermaid
graph LR
    MASTER([👤 Master])

    MASTER --> UC1[Iniciar sesión]
    MASTER --> UC2[Registrar cargos electorales]
    MASTER --> UC3[Registrar partidos políticos]
    MASTER --> UC4[Registrar candidatos]
    MASTER --> UC5[Asignar cargos a JRVs]
    MASTER --> UC6[Crear usuarios]
    MASTER --> UC7[Asignar JRVs a Observers]
    MASTER --> UC8[Anular acta aprobada]
    MASTER --> UC9[Revocar sesión de usuario]
    MASTER --> UC10[Ver dashboard de resultados]

    UC4 -.->|depende de| UC2
    UC4 -.->|depende de| UC3
    UC7 -.->|depende de| UC5
```

---

## ADMIN

```mermaid
graph LR
    ADMIN([👤 Admin])

    ADMIN --> UC1[Iniciar sesión]
    ADMIN --> UC2[Ver resultados por cargo]
    ADMIN --> UC3[Ver resultados por cantón]
    ADMIN --> UC4[Ver progreso de actas enviadas]
    ADMIN --> UC5[Ver estadísticas comparativas]
```

---

## Resumen por Actor

| Actor | Escribe datos | Gestiona usuarios | Ve resultados | Aprueba / Anula |
|-------|:---:|:---:|:---:|:---:|
| OBSERVER | ✅ sus JRVs | ❌ | ❌ | ❌ |
| COORDINATOR | ✅ correcciones | ❌ | ✅ | ✅ aprueba |
| MASTER | ✅ configuración | ✅ | ✅ | ✅ anula |
| ADMIN | ❌ | ❌ | ✅ | ❌ |
