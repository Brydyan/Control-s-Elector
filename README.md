# Control Electoral - Provincia de Santa Elena 🇪🇨

Sistema de misión crítica para el control, registro y validación de actas electorales en tiempo real, diseñado específicamente para la geografía electoral de la provincia de Santa Elena (Cantones: Santa Elena, La Libertad, Salinas). El objetivo principal es permitir a los observadores políticos digitalizar los resultados de las Juntas Receptoras del Voto (JRV), adjuntar evidencias fotográficas optimizadas y consolidar los datos de manera inmediata para evitar inconsistencias en el escrutinio general.

---

## 🏗️ Arquitectura del Sistema

El ecosistema está dividido en tres componentes desacoplados e independientes para garantizar alta disponibilidad y tolerancia a fallos durante la ventana de alta concurrencia del día del proceso electoral:

```
              ┌──────────────────────────────────────────────┐
              │   App Observador (Angular + Ionic / Vercel)  │
              └──────────────────────┬───────────────────────┘
                                     │ (Peticiones HTTP / JSON)
                                     ▼
              ┌──────────────────────────────────────────────┐
              │       API Backend (Spring Boot / Railway)    │
              └──────────────┬───────────────────────┬───────┘
                             │                       │
 (Persistencia Relacional)   ▼                       ▼   (Object Storage)
              ┌──────────────────────┐       ┌───────────────────────┐
              │ PostgreSQL /Supabase │       │   Supabase Storage    │
              └──────────────────────┘       │     (.webp images)    │
                                             └───────────────────────┘
                                                          ▲
                                     │ (Lectura de Datos) │
                                     └───────────┬────────┘
                                                 ▼
              ┌──────────────────────────────────────────────┐
              │    Dashboard Conteo (Angular / Vercel)       │
              └──────────────────────────────────────────────┘
```

1. **Captura en Campo (Frontend Mobile/Web):** Aplicación optimizada para dispositivos móviles orientada al observador de mesa. Implementa una estrategia *Offline-First* con compresión nativa de imágenes en el cliente.
2. **Núcleo de Negocio (Backend API):** API REST robusta encargada de procesar las transacciones numéricas, validar la seguridad basada en roles, controlar la concurrencia y orquestar el guardado de evidencias.
3. **Centro de Mando (Frontend Dashboard):** Panel administrativo y público para la visualización del conteo de votos consolidado en tiempo real, protegido contra cuellos de botella mediante distribución por CDN.

---

## 🛠️ Stack Tecnológico

### Lenguajes y Frameworks
*   **Frontend (App Movil/Web):** Angular 17+ & Ionic Framework (TypeScript).
*   **Backend API:** Java 17 / Spring Boot (Spring Web, Spring Security, Spring Data JPA).
*   **Frontend Dashboard:** Angular 17+ (TypeScript).

### Persistencia y Almacenamiento
*   **Base de Datos Transaccional:** PostgreSQL (Alojado en Supabase) + Motor de migraciones estructurado.
*   **Almacenamiento de Evidencias:** Supabase Object Storage (Para el almacenamiento optimizado de archivos multimedia).
*   **Optimización de Archivos:** Transformación y redimensión en formato `.webp` de alta fidelidad ejecutado en el cliente antes de la transmisión de datos para el ahorro estricto de consumo de red móvil (3G/4G).

---

## 🌐 Gestión de Entornos y Despliegue

Para garantizar el aseguramiento de la calidad (QA) y pruebas de estrés eficaces antes del día cero, el proyecto se divide en dos topologías de red:

### 🟢 1. Entorno de Staging (Servidor Local)
Entorno de pruebas locales y simulación de carga masiva de datos:
*   **Base de Datos & Storage:** Contenedor Dockerizado con PostgreSQL + emulación local.
*   **API Backend:** Instancia de Spring Boot corriendo localmente en servidor casero de desarrollo.
*   **Acceso y Red:** Enrutamiento mediante túnel seguro con **Tailscale** para pruebas de stress directas desde dispositivos móviles externos hacia el servidor local sin exposición de puertos públicos.

### 🔵 2. Entorno de Producción (Nube)
Infraestructura de despliegue final mapeada bajo el dominio principal (`twintailcs.xyz`):
*   **API Backend:** Hospedado en **Railway** (Plan Pro) integrado a despliegues automáticos (CI/CD) desde la rama principal de GitHub.
*   **Persistencia:** Instancia Cloud dedicada en **Supabase** (Base de Datos Relacional y Buckets de Storage).
*   **Frontends (App y Dashboard):** Desplegados de forma global en **Vercel**, enlazados mediante los siguientes subdominios:
    *   `api.twintailcs.xyz` ➡️ Microservicio Backend (Railway).
    *   `control.twintailcs.xyz` ➡️ Aplicación de Captura para Observadores (Vercel).
    *   `resultados.twintailcs.xyz` ➡️ Dashboard de Resultados en Vivo (Vercel).

---

## 🛡️ Consideraciones de QA y Seguridad Obligatorias

*   **Row Level Security (RLS):** Implementación estricta de políticas a nivel de fila en PostgreSQL para aislar los datos del observador a sus respectivas juntas asignadas.
*   **Concurrencia de Datos:** Bloqueos optimistas/pesimistas controlados en JPA para prevenir condiciones de carrera durante la sumatoria simultánea de actas.
*   **Control de Carga:** Compresión restrictiva del payload reduciendo imágenes pesadas de cámaras nativas (~5MB) a ficheros WebP estables (~350KB).
