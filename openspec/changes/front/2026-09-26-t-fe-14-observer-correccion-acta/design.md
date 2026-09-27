# Design — T-FE-14: Corrección de Acta Disputada en Campo (OBSERVER)

---

## D1 — Estrategia de Carga y Validación de Estado Inicial

**Decisión**:
Al inicializar `CorreccionActaPage`, se invoca `ObserverService.getActa(id)`. Si el acta no está en estado `EN_DISPUTA`, el componente cancela la renderización y redirige inmediatamente a `/observer/mis-jrvs` mostrando un mensaje: *"El acta no se encuentra en estado de disputa para corrección"*.

**Alternativa descartada — Permitir abrir el formulario en cualquier estado y bloquear solo al guardar**:
Descartada porque permite al veedor editar datos de actas ya aprobadas o anuladas, generando confusión operativa en campo cuando el backend inevitablemente rechace el `PUT` con error `400 / 409 Conflict`.

---

## D2 — Manejo Dual de Evidencia Fotográfica (Conservar vs Reemplazar)

**Decisión**:
El veedor dispone de dos modos mediante un selector binario:
1. `Conservar Foto`: mantiene la imagen existente referenciada por `urlEvidencia` (caso estándar: solo hubo error de digitación numérica).
2. `Reemplazar Foto`: activa el componente de cámara/archivo, comprime la nueva imagen a WebP en memoria (300-500 KB) reutilizando `ImageCompressionService` y la envía vía `POST /api/v1/observer/actas/{id}/imagen` tras el éxito del `PUT`.

**Alternativa descartada — Forzar obligatoriamente a subir una foto nueva en toda corrección**:
Descartada porque si el veedor está en un recinto con baja señal o batería crítica, y el coordinador solo pidió corregir un número que ya era legible en la foto anterior, forzar una segunda subida multimedia duplica el consumo de datos y multiplica la probabilidad de fallo por timeout de red.

---

## D3 — Validación Matemática y Bloqueo de Envío en Cliente

**Decisión**:
El botón *"Enviar Corrección"* permanece deshabilitado (`disabled`) a nivel de UI mientras:
- `checksumValido === false`, o
- `totalSufragantes <= 0 || totalSufragantes > 500`.

La fórmula de validación es:
```typescript
get sumaVotos(): number {
  const votosCandidatos = this.detalles.reduce((acc, d) => acc + (Number(d.votos) || 0), 0);
  return votosCandidatos + (Number(this.votosBlancos) || 0) + (Number(this.votosNulos) || 0);
}

get checksumValido(): boolean {
  if (this.esCpccs) {
    return this.sumaVotos <= (this.totalSufragantes * 3);
  }
  return this.sumaVotos === this.totalSufragantes;
}
```

**Alternativa descartada — Permitir enviar con descuadre y mostrar el error que devuelva la API**:
Descartada porque el backend rechaza con 400 Bad Request por violación de integridad electoral. Validar en cliente de forma instantánea ahorra una petición de red inútil en conexiones móviles intermitentes.
