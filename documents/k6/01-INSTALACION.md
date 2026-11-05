# 01: Instalación de k6

**Tiempo:** 10-15 minutos  
**Dificultad:** Fácil

---

## Opción A: npm (Recomendado si tienes Node.js)

### Paso 1: Instalar k6 globalmente

```bash
npm install -g k6
```

### Paso 2: Verificar instalación

```bash
k6 version
```

**Esperado:**
```
k6 vX.XX.X (linux/amd64)
```

---

## Opción B: Homebrew (macOS)

```bash
brew install k6

# Verificar
k6 version
```

---

## Opción C: Chocolatey (Windows)

```powershell
choco install k6

# Verificar
k6 version
```

---

## Opción D: Docker (No Installation)

Si no quieres instalar nada localmente:

```bash
docker pull grafana/k6:latest

# Verificar
docker run --rm grafana/k6:latest version
```

Luego, usar en comandos:
```bash
# En lugar de: k6 run scenarios/login.js
# Usar: docker run --rm -v $(pwd):/workspace grafana/k6:latest run /workspace/scenarios/login.js
```

O settear variable:
```bash
export K6_DOCKER=true
# Luego los scripts usan Docker automáticamente
```

---

## Opción E: Descargar Binario

Si prefieres binario standalone:

1. Visita: https://grafana.com/products/cloud/k6/download/
2. Descarga para tu OS
3. Descomprimir
4. Añadir a PATH

```bash
# macOS/Linux
tar -xzf k6-vX.XX.X-linux-amd64.tar.gz
mv k6 /usr/local/bin/

# Windows: extraer y añadir carpeta a PATH
```

---

## ✅ Verificación

Después de instalar (cualquier opción), verificar:

```bash
k6 version
# Output: k6 vX.XX.X

k6 run --help
# Output: Debe mostrar opciones de k6

which k6
# Output: Debe mostrar path a k6
```

---

## 🆘 Troubleshooting

### "k6 command not found"

```bash
# Verificar si realmente se instaló
npm list -g k6

# Si no está, reinstalar
npm uninstall -g k6
npm install -g k6

# Verificar path
echo $PATH
which k6

# Si sigue fallando, usar Docker
export K6_DOCKER=true
```

### "Permission denied" en Linux/Mac

```bash
# Dar permisos ejecutables
sudo chmod +x /usr/local/bin/k6

# O reinstalar con sudo
sudo npm install -g k6
```

### "npm not found"

```bash
# Instalar Node.js primero
# macOS:
brew install node

# Ubuntu:
sudo apt-get install nodejs npm

# Windows: Descargar desde https://nodejs.org/
```

---

## ✅ Próximo Paso

Cuando k6 esté instalado y verificado:

→ Ir a `02-SEED-DATOS.md` (crear test data en BD)

---

**Last Updated:** 2026-11-05
