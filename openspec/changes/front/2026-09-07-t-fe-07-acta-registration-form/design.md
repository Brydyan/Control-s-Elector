# Design — T-FE-07: Formulario de Registro de Acta (Wizard 3 Pasos)

---

## D1 — Arquitectura de Wizard (Paso a Paso)

**Decisión**: La vista se descompone en 3 pasos secuenciales controlados por la variable `currentStep: 'VOTOS' | 'IMAGEN' | 'REVISION'`:
- `VOTOS`: Digitación y verificación de votos por lista y subtotales.
- `IMAGEN`: Respaldo fotográfico y previsualización.
- `REVISION`: Inspección global de datos y transmisión oficial.

**Alternativa descartada — Formulario de una sola página vertical interminable**: En pantallas móviles pequeñas, un formulario de 15+ candidatos más inputs de nulos, blancos y subida de archivos genera scroll excesivo y propicia errores de omisión.

---

## D2 — Validación en Tiempo Real del Invariante Electoral

**Decisión**: Cálculo aritmético reactivo en el componente:
```typescript
get sumaVotosCandidatos(): number {
  let total = 0;
  this.votosPorCandidato.forEach((v) => { total += v; });
  return total;
}

get totalVotosComputados(): number {
  return this.sumaVotosCandidatos + (this.votosBlancos || 0) + (this.votosNulos || 0);
}

get isChecksumValid(): boolean {
  return this.totalSufragantes > 0 && this.totalVotosComputados === this.totalSufragantes;
}
```
Si `isChecksumValid` es falso, se muestra un banner de advertencia con la diferencia exacta (`totalVotosComputados - totalSufragantes`) y se bloquea la llamada a `enviarActaFinal()`.

**Alternativa descartada — Validar únicamente en el backend al enviar**: Retrasa el feedback al usuario hasta el viaje por la red, frustrando al veedor si la conexión es lenta o intermitente.

---

## D3 — Carga Dinámica de Candidatos y Partidos

**Decisión**: Los candidatos se consultan de la API oficial mediante `forkJoin`:
- `GET /api/v1/configuracion/candidatos?cargoId={cargoId}`
- `GET /api/v1/configuracion/partidos`
El resultado mapea cada candidato con sus siglas de partido y orden de papeleta.

**Alternativa descartada — Candidatos estáticos en el frontend**: Inflexible ante cambios de candidaturas de última hora (inhabilitaciones, reemplazos CNE).

---

## D4 — Persistencia Inmediata de Borrador Local

**Decisión**: Cada pulsación de tecla o cambio en los inputs invoca `guardarBorradorLocal()` sobre `ActaStorageService`, serializando el estado en el almacenamiento del navegador. Al recargar la vista, los datos se restauran inmediatamente.

**Alternativa descartada — Guardar únicamente al presionar un botón**: Si el teléfono del veedor se apaga o la pestaña se cierra accidentalmente, se perdería el trabajo ingresado.

---

## D5 — Contrato DTO con Backend y Secuencia de Transmisión

**Decisión**: El payload emitido es exactamente `ActaRequest`:
```json
{
  "jrvId": 12,
  "cargoId": 3,
  "totalSufragantes": 250,
  "votosBlancos": 5,
  "votosNulos": 10,
  "detalles": [
    { "candidatoId": 101, "votos": 120 },
    { "candidatoId": 102, "votos": 115 }
  ]
}
```
Tras obtener la respuesta con el `actaId`, si existe un archivo de imagen seleccionado, se encadena inmediatamente la subida a `POST /api/v1/observer/actas/{id}/imagen` con clave `'imagen'`.
