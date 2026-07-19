# T-FE-08 — Captura y Compresión de Imagen (WebP)

**Dificultad:** Intermedio
**Tiempo estimado:** 3-4 horas
**Depende de:** T-FE-07

---

## ¿Qué vas a construir?

Un servicio que captura la foto del acta física desde la cámara del dispositivo, la comprime al formato WebP, y la sube al backend.

**Flujo:**
1. Observer toca "Tomar foto"
2. Se abre la cámara (con Capacitor)
3. La imagen se comprime a WebP (calidad 0.82)
4. Si >500KB, recomprimir a calidad 0.65
5. Subir al backend (que la sube a Supabase)

---

## Conceptos clave

### Capacitor Camera
Capacitor es la capa que conecta Angular/Ionic con las APIs nativas del dispositivo (cámara, filesystem, etc.):

```bash
npm install @capacitor/camera
npx cap sync
```

### Canvas API — manipular imágenes en el navegador
El Canvas nos permite:
1. Dibujar la imagen en un canvas
2. Redimensionarla si es muy grande
3. Exportarla en formato WebP con la calidad deseada

```typescript
const canvas = document.createElement('canvas');
const ctx = canvas.getContext('2d')!;
canvas.width = imagen.width;
canvas.height = imagen.height;
ctx.drawImage(imagen, 0, 0);

// Exportar como WebP calidad 0.82
const blob = await new Promise<Blob>((resolve) => {
  canvas.toBlob(blob => resolve(blob!), 'image/webp', 0.82);
});
```

### FormData — enviar archivo con HttpClient
Para enviar un archivo (Blob) con HttpClient:
```typescript
const formData = new FormData();
formData.append('imagen', blob, 'foto.webp');
this.http.post(url, formData).subscribe();
```

---

## Archivos a crear

```
src/app/core/services/
  image.service.ts
src/app/features/observer/registrar-acta/
  registrar-acta.page.ts    ← agregar botón de cámara
  registrar-acta.page.html  ← agregar UI de foto
```

---

## Pasos

### Paso 1 — Instalar Capacitor Camera

```bash
npm install @capacitor/camera
npx cap sync android  # o ios
```

### Paso 2 — ImageService

```typescript
// src/app/core/services/image.service.ts
import { Injectable } from '@angular/core';
import { Camera, CameraResultType, CameraSource } from '@capacitor/camera';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';

// Tamaño máximo en bytes antes de recomprimir
const MAX_SIZE_BYTES = 500 * 1024;  // 500KB
const MAX_WIDTH = 1920;
const MAX_HEIGHT = 1080;

@Injectable({
  providedIn: 'root'
})
export class ImageService {

  constructor(private http: HttpClient) {}

  /**
   * Abre la cámara, captura la foto y la comprime a WebP.
   * Retorna el Blob listo para subir.
   */
  async capturarYComprimir(): Promise<Blob> {

    // 1. Abrir la cámara (o galería en web)
    const foto = await Camera.getPhoto({
      quality: 90,
      allowEditing: false,
      resultType: CameraResultType.DataUrl,  // Base64 URL
      source: CameraSource.Camera
    });

    if (!foto.dataUrl) {
      throw new Error('No se pudo capturar la foto');
    }

    // 2. Convertir dataUrl a imagen HTML
    const imagen = await this.dataUrlToImage(foto.dataUrl);

    // 3. Redimensionar si es más grande que 1920x1080
    const canvas = this.redimensionar(imagen);

    // 4. Comprimir a WebP con calidad 0.82
    let blob = await this.canvasToBlob(canvas, 0.82);

    // 5. Si sigue siendo >500KB, recomprimir con calidad 0.65
    if (blob.size > MAX_SIZE_BYTES) {
      blob = await this.canvasToBlob(canvas, 0.65);
    }

    return blob;
  }

  /**
   * Sube la imagen al backend.
   */
  subirImagen(actaId: number, blob: Blob): Observable<{ storageUrl: string }> {
    const formData = new FormData();
    formData.append('imagen', blob, `acta-${actaId}.webp`);

    return this.http.post<{ storageUrl: string }>(
      `${environment.apiUrl}/observer/actas/${actaId}/imagen`,
      formData
    );
  }

  // ─── Métodos privados de utilidad ─────────────────────────────────────────

  private dataUrlToImage(dataUrl: string): Promise<HTMLImageElement> {
    return new Promise((resolve, reject) => {
      const img = new Image();
      img.onload = () => resolve(img);
      img.onerror = reject;
      img.src = dataUrl;
    });
  }

  private redimensionar(imagen: HTMLImageElement): HTMLCanvasElement {
    let { width, height } = imagen;

    // Calcular las nuevas dimensiones manteniendo el aspect ratio
    if (width > MAX_WIDTH || height > MAX_HEIGHT) {
      const ratioW = MAX_WIDTH / width;
      const ratioH = MAX_HEIGHT / height;
      const ratio = Math.min(ratioW, ratioH);  // usar el ratio más pequeño
      width = Math.round(width * ratio);
      height = Math.round(height * ratio);
    }

    const canvas = document.createElement('canvas');
    canvas.width = width;
    canvas.height = height;
    const ctx = canvas.getContext('2d')!;
    ctx.drawImage(imagen, 0, 0, width, height);

    return canvas;
  }

  private canvasToBlob(canvas: HTMLCanvasElement, calidad: number): Promise<Blob> {
    return new Promise((resolve, reject) => {
      canvas.toBlob(
        blob => {
          if (blob) resolve(blob);
          else reject(new Error('No se pudo convertir a blob'));
        },
        'image/webp',
        calidad
      );
    });
  }
}
```

### Paso 3 — Integrar en RegistrarActaPage

Agrega el botón y lógica de foto en el componente existente:

```typescript
// En registrar-acta.page.ts — agregar estas propiedades y métodos:

imagenBlob: Blob | null = null;
imagenPreview: string | null = null;  // para mostrar preview
actaId: number | null = null;

// Inyectar ImageService en el constructor
constructor(
  // ... lo que ya tienes ...
  private imageService: ImageService
) {}

// Método para tomar foto
async tomarFoto() {
  try {
    this.imagenBlob = await this.imageService.capturarYComprimir();

    // Crear URL temporal para preview
    this.imagenPreview = URL.createObjectURL(this.imagenBlob);

    const toast = await this.toastCtrl.create({
      message: `Foto capturada (${Math.round(this.imagenBlob.size / 1024)}KB)`,
      duration: 2000,
      color: 'success'
    });
    await toast.present();
  } catch (error) {
    const toast = await this.toastCtrl.create({
      message: 'No se pudo capturar la foto',
      duration: 3000,
      color: 'danger'
    });
    await toast.present();
  }
}

// Modificar onSubmit() para subir la imagen después de registrar el acta:
async onSubmit() {
  // ... validaciones existentes ...

  const loading = await this.loadingCtrl.create({ message: 'Registrando acta...' });
  await loading.present();

  this.http.post<{ id: number }>(`${environment.apiUrl}/observer/actas`, payload).subscribe({
    next: async (acta) => {
      this.actaId = acta.id;

      // Si hay imagen, subirla
      if (this.imagenBlob && this.actaId) {
        loading.message = 'Subiendo foto...';
        this.imageService.subirImagen(this.actaId, this.imagenBlob).subscribe({
          next: async () => {
            await loading.dismiss();
            // ... éxito
          },
          error: async () => {
            await loading.dismiss();
            // El acta se registró pero la imagen falló
            // El sistema guarda en offline para reintento
            const toast = await this.toastCtrl.create({
              message: 'Acta registrada. La foto se sincronizará cuando haya conexión.',
              duration: 4000,
              color: 'warning'
            });
            await toast.present();
            this.router.navigate(['/observer/home']);
          }
        });
      } else {
        await loading.dismiss();
        this.router.navigate(['/observer/home']);
      }
    }
  });
}
```

Agrega en el template HTML (antes del botón de registrar):

```html
<!-- Sección de foto -->
<h3 class="seccion-titulo">Foto del Acta</h3>

<!-- Preview si ya tomó la foto -->
<div class="foto-preview" *ngIf="imagenPreview">
  <img [src]="imagenPreview" alt="Acta" />
</div>

<ion-button expand="block" fill="outline" (click)="tomarFoto()">
  {{ imagenPreview ? 'Retomar Foto' : 'Tomar Foto del Acta' }}
</ion-button>
```

---

## Cómo verificar que funciona

1. En el emulador o dispositivo real, toca "Tomar Foto"
2. Toma la foto
3. Verifica el preview aparece
4. Verifica en la consola el tamaño del blob (debe ser <500KB)
5. Completa el formulario y envía
6. Verifica en Supabase Storage que apareció el archivo

**En el navegador web (sin Capacitor):**
El plugin de cámara abrirá el selector de archivos en vez de la cámara. Es normal.

---

## Errores comunes

| Error | Causa | Solución |
|-------|-------|----------|
| `Camera.getPhoto is not a function` | Plugin no instalado | Corre `npm install @capacitor/camera && npx cap sync` |
| `User cancelled` | El usuario cerró la cámara | Maneja el error silenciosamente (es esperado) |
| Imagen muy grande después de comprimir | WebP no soportado en ese browser | Usa `image/jpeg` como fallback |
| Preview no aparece | `URL.createObjectURL()` no disponible | Verifica que el blob es válido y que estás en browser (no Node) |
| `FormData` no funciona con interceptor | Interceptor modifica Content-Type | Deja que el browser ponga el `Content-Type: multipart/form-data` automáticamente |
