# Design — T-FE-13: Modo "Foto Primero" (Capture & Run) y Cotas Electorales

---

## D1 — Reordenamiento del Wizard: Foto Primero

**Decisión**: La variable `currentStep` inicia en `'IMAGEN'`:
```
Paso 1: IMAGEN (Toma de foto, previsualización, guardado local)
   ↓
Paso 2: VOTOS (Padrón de candidatos, sufragantes, invariante)
   ↓
Paso 3: REVISION (Resumen final, confirmación de transmisión)
```
Si el usuario desea omitir temporalmente la foto porque no tiene cámara y digitará los votos primero, puede pulsar "Digitar votos primero" como excepción, pero el default operacional es siempre la captura fotográfica inmediata.

---

## D2 — Lógica de Cotas y Alertas de Sufragantes

**Decisión**:
```typescript
get alertaSufragantes(): string | null {
  if (this.totalSufragantes > 500) {
    return 'Límite CNE excedido: ninguna JRV puede superar 500 sufragantes.';
  }
  if (this.totalSufragantes > 350) {
    return 'Advertencia: El padrón estándar es de 350 electores. Verifique si es una mesa especial.';
  }
  return null;
}

get esSufragantesValido(): boolean {
  return this.totalSufragantes > 0 && this.totalSufragantes <= 500;
}
```

---

## D3 — Regla Matemática Diferenciada: Unipersonal vs CPCCS

**Decisión**:
- Para `Prefecto`, `Alcalde`, `Concejales`:
  $\sum \text{votos} = \text{totalSufragantes}$
- Para `CPCCS` (tres sub-listas: hombres, mujeres, pueblos):
  $\sum \text{votos} \le \text{totalSufragantes} \times 3$
  (Cada elector tiene derecho a votar hasta por 3 consejeros).

---

## D4 — Persistencia de la Foto en Borrador Local

**Decisión**: `ActaStorageService` extiende el modelo `LocalDraft` para almacenar el dataURL o identificador de archivo en IndexedDB/Capacitor Filesystem, permitiendo que la foto capturada no se pierda aunque el navegador se cierre antes de digitar los números.
