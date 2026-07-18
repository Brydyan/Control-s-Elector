# T-FE-09 — Resiliencia Offline con IndexedDB

**Dificultad:** Avanzado
**Tiempo estimado:** 4-5 horas
**Depende de:** T-FE-07

---

## ¿Qué vas a construir?

Cuando el observer pierde la conexión al enviar un acta, el sistema guarda el acta localmente y la sincroniza automáticamente cuando vuelve la conexión.

**Dos capas de fallback:**
1. **IndexedDB** — base de datos del navegador. Reintento automático cada 30 segundos.
2. **Capacitor Filesystem** — si IndexedDB falla, guarda la imagen en el dispositivo para subir manualmente.

---

## Conceptos clave

### IndexedDB — base de datos del navegador
Es como una base de datos SQL pero dentro del navegador. Persiste aunque cierres la app. Perfecta para guardar datos temporales grandes (como actas con imagen).

Usamos la librería `idb` que hace más amigable la API:
```bash
npm install idb
```

### navigator.onLine — detectar conexión
```typescript
if (navigator.onLine) {
  // Hay conexión
} else {
  // Sin conexión → guardar offline
}
```

También puedes escuchar eventos:
```typescript
window.addEventListener('online', () => this.sincronizar());
window.addEventListener('offline', () => console.log('Sin conexión'));
```

### Capacitor Filesystem — guardar en el dispositivo
Para la imagen cuando IndexedDB no es suficiente:
```typescript
import { Filesystem, Directory } from '@capacitor/filesystem';

await Filesystem.writeFile({
  path: 'JRV-42_20270302_1430_7.webp',
  data: base64Data,
  directory: Directory.Data  // directorio privado de la app
});
```

---

## Archivos a crear

```
src/app/core/services/
  offline-queue.service.ts
```

---

## Código completo

### offline-queue.service.ts

```typescript
// src/app/core/services/offline-queue.service.ts
import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { openDB, IDBPDatabase } from 'idb';
import { Filesystem, Directory } from '@capacitor/filesystem';
import { environment } from '../../../environments/environment';

// Estructura de un item pendiente de sync
interface ActaPendiente {
  id?: number;              // ID auto-generado por IndexedDB
  jrvId: number;
  cargoId: number;
  payload: any;             // el JSON del acta
  imagenBase64: string | null;  // la imagen en base64
  fechaCreacion: number;    // timestamp
  intentos: number;         // cuántas veces intentó sincronizar
}

@Injectable({
  providedIn: 'root'
})
export class OfflineQueueService {

  private db: IDBPDatabase | null = null;
  private syncInterval: any = null;

  // Cuántas actas pendientes — para mostrar en la UI
  pendientes = 0;

  constructor(private http: HttpClient) {
    this.inicializarDB();
  }

  /**
   * Inicializa la base de datos IndexedDB.
   * Se crea si no existe.
   */
  private async inicializarDB(): Promise<void> {
    this.db = await openDB('electoral-offline', 1, {
      upgrade(db) {
        // Crear la "tabla" (object store) para las actas pendientes
        if (!db.objectStoreNames.contains('actas-pendientes')) {
          const store = db.createObjectStore('actas-pendientes', {
            keyPath: 'id',
            autoIncrement: true
          });
          store.createIndex('jrvId', 'jrvId');
        }
      }
    });

    // Contar pendientes al iniciar
    await this.actualizarConteo();

    // Empezar el ciclo de sincronización
    this.iniciarSyncAutomatico();

    // Sincronizar cuando vuelve la conexión
    window.addEventListener('online', () => {
      console.log('Conexión restaurada — sincronizando...');
      this.sincronizar();
    });
  }

  /**
   * Guarda un acta pendiente en IndexedDB.
   * Se llama cuando falla el envío al backend.
   */
  async guardarPendiente(
    jrvId: number,
    cargoId: number,
    payload: any,
    imagenBlob: Blob | null
  ): Promise<void> {

    if (!this.db) {
      console.error('IndexedDB no inicializada');
      return;
    }

    // Convertir el Blob de imagen a base64 para almacenar en IndexedDB
    let imagenBase64: string | null = null;
    if (imagenBlob) {
      imagenBase64 = await this.blobToBase64(imagenBlob);
    }

    const item: ActaPendiente = {
      jrvId,
      cargoId,
      payload,
      imagenBase64,
      fechaCreacion: Date.now(),
      intentos: 0
    };

    await this.db.add('actas-pendientes', item);
    await this.actualizarConteo();

    console.log(`Acta guardada offline: JRV=${jrvId}, cargo=${cargoId}`);

    // Si la imagen no se pudo guardar en IndexedDB (muy grande), guardar en filesystem
    if (imagenBlob && imagenBlob.size > 5 * 1024 * 1024) {  // >5MB
      await this.guardarImagenEnFilesystem(jrvId, cargoId, imagenBase64!);
    }
  }

  /**
   * Intenta sincronizar todos los items pendientes.
   * Se llama cada 30 segundos y cuando vuelve la conexión.
   */
  async sincronizar(): Promise<void> {
    if (!this.db || !navigator.onLine) return;

    const pendientes: ActaPendiente[] = await this.db.getAll('actas-pendientes');

    for (const item of pendientes) {
      try {
        // 1. Enviar el acta al backend
        const acta = await this.http.post<{ id: number }>(
          `${environment.apiUrl}/observer/actas`,
          item.payload
        ).toPromise();

        // 2. Si hay imagen, subirla
        if (item.imagenBase64 && acta?.id) {
          const blob = await this.base64ToBlob(item.imagenBase64, 'image/webp');
          const formData = new FormData();
          formData.append('imagen', blob, `acta-${acta.id}.webp`);

          await this.http.post(
            `${environment.apiUrl}/observer/actas/${acta.id}/imagen`,
            formData
          ).toPromise();
        }

        // 3. Eliminar de IndexedDB al sincronizar exitosamente
        await this.db.delete('actas-pendientes', item.id!);
        console.log(`Acta sincronizada: JRV=${item.jrvId}`);

      } catch (error) {
        // Incrementar contador de intentos
        item.intentos = (item.intentos || 0) + 1;
        await this.db.put('actas-pendientes', item);
        console.warn(`Error al sincronizar acta JRV=${item.jrvId}, intento ${item.intentos}`);
      }
    }

    await this.actualizarConteo();
  }

  /**
   * Guarda la imagen en el directorio privado de la app (Capacitor).
   * Fallback cuando IndexedDB no es suficiente.
   */
  private async guardarImagenEnFilesystem(
    jrvId: number,
    cargoId: number,
    base64: string
  ): Promise<void> {
    try {
      const ahora = new Date();
      const fecha = ahora.toISOString().slice(0, 10).replace(/-/g, '');
      const hora = `${ahora.getHours().toString().padStart(2, '0')}${ahora.getMinutes().toString().padStart(2, '0')}`;
      const filename = `JRV-${jrvId}_${fecha}_${hora}_offline.webp`;

      await Filesystem.writeFile({
        path: filename,
        data: base64.split(',')[1],  // quitar el prefijo "data:image/webp;base64,"
        directory: Directory.Data
      });

      console.log(`Imagen guardada en filesystem: ${filename}`);
    } catch (error) {
      console.error('No se pudo guardar en filesystem:', error);
    }
  }

  /**
   * Inicia el ciclo de sync automático cada 30 segundos.
   */
  private iniciarSyncAutomatico(): void {
    this.syncInterval = setInterval(() => {
      if (navigator.onLine && this.pendientes > 0) {
        this.sincronizar();
      }
    }, 30000);  // 30 segundos
  }

  private async actualizarConteo(): Promise<void> {
    if (!this.db) return;
    const items = await this.db.getAll('actas-pendientes');
    this.pendientes = items.length;
  }

  // ─── Utilidades de conversión ──────────────────────────────────────────────

  private blobToBase64(blob: Blob): Promise<string> {
    return new Promise((resolve, reject) => {
      const reader = new FileReader();
      reader.onload = () => resolve(reader.result as string);
      reader.onerror = reject;
      reader.readAsDataURL(blob);
    });
  }

  private async base64ToBlob(base64: string, type: string): Promise<Blob> {
    const res = await fetch(base64);
    return res.blob();
  }
}
```

### Integrar en RegistrarActaPage

En `onSubmit()`, envuelve el POST en manejo de error offline:

```typescript
// En lugar de mostrar error, guardar offline:
error: async (err) => {
  await loading.dismiss();

  if (!navigator.onLine) {
    // Sin conexión — guardar offline
    await this.offlineQueueService.guardarPendiente(
      this.jrvId,
      this.cargoId,
      payload,
      this.imagenBlob
    );

    const toast = await this.toastCtrl.create({
      message: 'Sin conexión. El acta se sincronizará automáticamente.',
      duration: 4000,
      color: 'warning'
    });
    await toast.present();
    this.router.navigate(['/observer/home']);
  } else {
    // Con conexión pero error del servidor
    const toast = await this.toastCtrl.create({
      message: err.error?.message ?? 'Error al registrar el acta',
      duration: 4000,
      color: 'danger'
    });
    await toast.present();
  }
}
```

### Mostrar badge en la toolbar

```typescript
// Inyectar en ObserverHomePage y mostrar el conteo:
constructor(
  // ...
  public offlineQueue: OfflineQueueService
) {}
```

```html
<!-- En el toolbar del observer -->
<ion-badge *ngIf="offlineQueue.pendientes > 0" color="warning">
  {{ offlineQueue.pendientes }} pendientes
</ion-badge>
```

---

## Cómo verificar que funciona

1. Desactiva la red (modo avión o desconecta WiFi)
2. Intenta registrar un acta
3. Debe mostrar "Sin conexión. El acta se sincronizará automáticamente."
4. Verifica en DevTools → Application → IndexedDB → electoral-offline que aparece el item
5. Activa la red
6. En menos de 30 segundos, el item debe desaparecer de IndexedDB (sincronizado)

---

## Errores comunes

| Error | Causa | Solución |
|-------|-------|----------|
| `openDB is not a function` | `idb` no instalado | Corre `npm install idb` |
| IndexedDB bloqueado | Versión anterior del schema | Incrementa la versión en `openDB('...', 2, ...)` |
| Sync no se detiene | `clearInterval` no llamado | En `ngOnDestroy` llama `clearInterval(this.syncInterval)` |
| Imagen muy grande para IndexedDB | IndexedDB tiene límites (~50MB) | Usa Filesystem para imágenes grandes |
| `navigator.onLine` siempre true | En emulador, la conexión siempre está | Prueba con throttling en DevTools → Network |
