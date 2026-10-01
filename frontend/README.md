# Frontend (React + TypeScript + Vite)

Interfaz de la herramienta de gestión de estrategias de respaldo.

## Ejecutar en desarrollo

1. Backend (desde la raíz del proyecto, con Java 25):
   `./mvnw spring-boot:run` → http://localhost:8080
2. Frontend (desde `frontend/`):
   `npm install` (solo la primera vez) y luego `npm run dev` → http://localhost:5173

Vite envía las llamadas a `/api` al backend (ver `vite.config.ts`).

## Pantallas

| Pantalla | Qué permite |
|---|---|
| Panel | Alertas abiertas por tipo, estado de cada programación y últimas ejecuciones |
| Bases de datos | Registrar una base e inspeccionarla (modo de archivado, tablespaces, datafiles y mensajes) |
| Estrategias | Crear y modificar estrategias: información general, QUÉ, CÓMO, CUÁNDO y destino |
| Detalle de estrategia | Flujo completo: validar → generar script (con la traducción paso a paso) → aprobar o rechazar → programación → ejecutar → evidencia |
| Ejecuciones | Historial y evidencia de cada ejecución (archivos, script, salida de RMAN y verificación) |
| Alertas | Atender, descartar o aplicar recomendaciones (siempre por decisión del administrador) y lanzar la revisión preventiva |

El nombre del administrador (arriba a la derecha) se usa para aprobar scripts y atender
alertas. Se guarda en el navegador; no hay autenticación (ver `docs/decisiones.md`).

## Compilar

`npm run build` revisa los tipos (`tsc -b`) y genera `dist/`.
