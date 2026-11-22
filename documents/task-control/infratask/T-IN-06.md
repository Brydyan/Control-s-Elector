# T-IN-06 — Supabase Realtime

**Estado:** TODO
**Dificultad:** Intermedio
**Tiempo estimado:** 1.5 horas
**Depende de:** T-IN-05 ✅


> **⚠️ HISTÓRICO — ya no describe producción.** El realtime de este proyecto **no depende de
> Supabase**: el mecanismo (WebSocket) está **por definir** y no se ha implementado. El contenido
> de abajo se conserva como registro; lo vigente está en `documents/task-control/infra.md`.

---

## ¿Qué vas a construir?

Vas a habilitar **Supabase Realtime**: WebSocket que notifica cambios en tiempo real cuando se registran/actualizan actas o notificaciones. El dashboard verá nuevas actas sin refrescar.

---

## Conceptos clave

**¿Qué es Realtime?**
Tecnología que permite que múltiples clientes reciban updates automáticamente cuando datos cambian en BD. WebSocket, no polling.

**¿LISTENING vs POLLING?**
- Polling: cliente pregunta cada 5s "¿hay cambios?". Lento, ineficiente.
- Listening (Realtime): BD notifica al cliente apenas hay cambio. Instantáneo.

**¿Qué es "broadcast" en Realtime?**
Notificación a todos los clientes suscritos a un canal (ej: "actas-channel").

---

## Pasos

### Paso 1 — Habilitar Realtime en Supabase

1. Supabase Dashboard → **Realtime**
2. Haz clic en **Enable**
3. Elige las tablas: `acta`, `notificacion`
4. Guarda

### Paso 2 — Configurar en Frontend (Angular)

Instala cliente Supabase:
```bash
cd frontend/app
npm install @supabase/supabase-js
```

Crea servicio:
```typescript
// src/app/services/realtime.service.ts
import { Injectable } from '@angular/core';
import { createClient } from '@supabase/supabase-js';

@Injectable({ providedIn: 'root' })
export class RealtimeService {
  private supabase = createClient(
    environment.supabaseUrl,
    environment.supabaseAnonKey
  );

  listenToActas() {
    return this.supabase
      .channel('actas')
      .on('postgres_changes', 
        { event: '*', schema: 'public', table: 'acta' },
        (payload) => console.log('Acta changed:', payload)
      )
      .subscribe();
  }

  listenToNotifications(observerId: number) {
    return this.supabase
      .channel(`notifications:${observerId}`)
      .on('postgres_changes',
        { event: 'INSERT', schema: 'public', table: 'notificacion', 
          filter: `observer_id=eq.${observerId}` },
        (payload) => console.log('New notification:', payload)
      )
      .subscribe();
  }
}
```

En tu componente:
```typescript
constructor(private realtime: RealtimeService) {
  this.realtime.listenToActas();
  this.realtime.listenToNotifications(currentUserId);
}
```

### Paso 3 — Verificar en Supabase Dashboard

1. Ve a **Realtime** → **Inspect**
2. Abre el dashboard en otra ventana
3. Registra una acta desde otra pestaña
4. Deberías ver el evento en **Inspect** en vivo

---

## Checklist

- [ ] Habilitar Realtime en Supabase (tablas acta, notificacion)
- [ ] Instalar @supabase/supabase-js en frontend
- [ ] Crear RealtimeService
- [ ] Verificar que eventos llegan en dashboard
