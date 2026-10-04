# Escenarios de evidencia

Pasos para reproducir cada evidencia que pide la asignación (entregable 4). Todos se
hacen desde la interfaz en **http://localhost:5173**, sobre el contenedor de pruebas
`oracle-xe`. Ninguno toca la Oracle local ni cambia el modo de archivado.

| # | Evidencia de la asignación | Escenario |
|---|---|---|
| 1 | Generación del script | [E1](#e1-generación-del-script) |
| 2 | Una ejecución exitosa | [E2](#e2-ejecución-exitosa) |
| 3 | Una ejecución fallida o simulación controlada de error | [E3](#e3-ejecución-fallida-controlada) |
| 4 | Consulta del historial | [E4](#e4-consulta-del-historial) |
| 5 | Detección de una condición de advertencia | [E5](#e5-detección-de-una-advertencia) |
| 6 | Aplicación de una recomendación | [E6](#e6-aplicación-de-una-recomendación) |
| extra | Ejecución automática (programada) | [E7](#e7-ejecución-programada-automatización) |

Qué capturar en cada uno está al final de cada escenario, en **Evidencia**.

---

## Preparación (una sola vez)

1. Levantar el contenedor: `docker start oracle-xe` y esperar en `docker logs oracle-xe`
   la línea `DATABASE IS READY TO USE!`.
2. Backend, desde la raíz del proyecto (con JDK 25):
   ```powershell
   $env:JAVA_HOME = "C:\Users\emmar\.jdks\openjdk-25.0.2"
   .\mvnw.cmd spring-boot:run
   ```
3. Frontend, desde `frontend/`: `npm run dev` y abrir **http://localhost:5173**.
4. Escribir su nombre en el campo **Administrador** (arriba a la derecha). Se usa para
   aprobar scripts y atender alertas.
5. En **Bases de datos** debe existir "XE contenedor de pruebas" (contenedor `oracle-xe`,
   servicio `XEPDB1`, ambiente PRUEBAS). Si no existe, registrarla con esos datos.
   Pulsar **Inspeccionar**: debe mostrar modo **ARCHIVELOG**, los tablespaces y datafiles
   de XEPDB1 y el mensaje informativo/recomendación correspondiente.

> Ya existen dos estrategias de pruebas: **"XE nivel 0 completo"** (id 191) y **"XE falla
> controlada"** (id 192), con sus ejecuciones 18 (exitosa) y 19 (fallida). Se pueden usar
> como evidencia directa o repetir los pasos para generar ejecuciones nuevas.

---

## E1. Generación del script

**Objetivo:** mostrar cómo la estrategia configurada se transforma en un script RMAN.

1. **Estrategias → Nueva estrategia.**
2. Información general: nombre `Demo nivel 0 semanal`, base *XE contenedor de pruebas*,
   responsable (su nombre), prioridad **ALTA**.
3. Qué respaldar: **Base de datos**, **Control file**, **SPFILE**, **Archived redo logs**.
4. Cómo respaldar: **Incremental nivel 0**, marcar **Comprimido**, retención `7`.
5. Cuándo respaldar: marcar **Programar ejecución automática**, inicio = mañana, hora `23:00`, frecuencia
   **SEMANAL**, día **DOM**, intervalo `1`, ventana `22:00` a `05:00`.
6. Destino: `/opt/oracle/oradata/respaldos`. **Guardar**. En el detalle pulsar **Activar**.
7. En el detalle, sección **1. Validación → Validar contra la base**. Debe quedar sin bloqueantes.
   Aparecerán, por ejemplo, *Informativo: espacio disponible*, *Recomendación: destino en
   el mismo disco que los datafiles* y *Advertencia `FRECUENCIA_INSUFICIENTE`* (prioridad
   ALTA con un solo respaldo por semana). Ninguna bloquea: en producción ese nivel 0
   semanal se complementa con otra estrategia de nivel 1 diario.
8. Sección **2. Script RMAN → Generar script**.

**Resultado esperado:** el script dentro de `RUN { }` con cuatro `BACKUP` (datafiles nivel
0, archivelogs, control file, SPFILE), todos con `AS COMPRESSED BACKUPSET`, `TAG
'EST<id>_...'` y `FORMAT '/opt/oracle/oradata/respaldos/%d_EST<id>_%T_%U'`; el mensaje
"RMAN checksyntax: el script no tiene errores de sintaxis"; la **traducción paso a paso**;
la huella SHA-256 y el estado **GENERADO**.

**Evidencia:** captura de la validación, del script y de la tabla de traducción.

---

## E2. Ejecución exitosa

**Objetivo:** ejecutar un respaldo real, verificarlo y dejar evidencia.

1. Partir de la estrategia de E1 (o de "XE nivel 0 completo").
2. Sección **3. Aprobación** → revisar el script → **Aprobar script** (opcionalmente con
   comentario). El estado pasa a **APROBADO** con su nombre y fecha.
3. Sección **5. Ejecución y evidencia → Ejecutar ahora**. Tarda alrededor de 2 minutos
   (respaldo + verificación).
4. Abrir la ejecución en la tabla.

**Resultado esperado:**

- Estado **EXITOSO**, verificación **VERIFICADO**.
- Inicio, fin, duración, tipo (*Incremental nivel 0*), origen MANUAL, ruta de destino y
  tamaño total.
- Lista de **archivos** generados con ruta, tamaño y **Existe = sí** (incluye el
  autorespaldo del control file).
- Script ejecutado, salida completa de RMAN (con `Finished backup`) y salida de la
  verificación (`CROSSCHECK BACKUPPIECE` → `AVAILABLE`, `RESTORE DATABASE VALIDATE` sin
  errores).

Comprobación opcional fuera de la app:

```powershell
docker exec oracle-xe ls -l /opt/oracle/oradata/respaldos
docker exec -i oracle-xe rman target / <<< "LIST BACKUP SUMMARY;"
```

**Evidencia:** captura del detalle de la ejecución (encabezado, archivos y salidas).
Referencia existente: **ejecución 18**.

---

## E3. Ejecución fallida controlada

**Objetivo:** provocar un error real y controlado, sin riesgo para la base ni los respaldos.

Se usa la carpeta de solo lectura `/opt/oracle/oradata/respaldos_sin_permiso` (permisos
555, ver `docs/decisiones.md`). El validador la acepta porque existe, pero RMAN, que corre
como `oracle`, no puede escribir en ella.

1. Si la carpeta no existe, crearla:
   ```
   docker exec oracle-xe sh -c 'mkdir -p /opt/oracle/oradata/respaldos_sin_permiso && chmod 555 /opt/oracle/oradata/respaldos_sin_permiso'
   ```
2. Usar la estrategia **"XE falla controlada"** (QUÉ: solo SPFILE; CÓMO: completo, sin
   compresión; destino: la carpeta anterior), o crear una igual.
3. **Validar contra la base → Generar script → Aprobar script → Ejecutar ahora.** Tarda unos segundos.
4. Abrir la ejecución.

**Resultado esperado:**

- Estado **FALLIDO**, verificación **NO_APLICA**.
- Mensaje de error con `RMAN-03009`, `ORA-19504: failed to create file
  "/opt/oracle/oradata/respaldos_sin_permiso/..."` y `ORA-27040`.
- Salida completa de RMAN guardada.
- En **Alertas** aparece una **advertencia** `EJECUCION_FALLIDA` ligada a la ejecución.

**Evidencia:** detalle de la ejecución fallida y la alerta. Referencia existente:
**ejecución 19**.

---

## E4. Consulta del historial

1. Menú **Ejecuciones**.
2. La tabla muestra cada ejecución con fecha, estrategia, base, tipo, origen, inicio,
   fin, duración, resultado y verificación, de la más reciente a la más antigua.
3. Pulsar una fila para ver su evidencia completa.
4. En el **Panel** aparecen las últimas ejecuciones; en el detalle de cada estrategia,
   su propio historial.

**Resultado esperado:** al menos una ejecución **EXITOSO** y una **FALLIDO**, como en el
ejemplo de historial del PDF.

**Evidencia:** captura de la lista de ejecuciones.

---

## E5. Detección de una advertencia

Hay varias formas; se recomienda la **A** porque se ve en dos lugares (validación y alertas).

### A. Estrategia inactiva o sin programación

1. Crear una estrategia `Demo advertencia` con QUÉ = **Base de datos**, CÓMO = completo,
   destino `/opt/oracle/oradata/respaldos`, retención `7`, **sin** marcar "Programar ejecución automática". Al guardar queda **inactiva**.
2. **Validar contra la base**: aparece **Advertencia `ESTRATEGIA_INACTIVA`** ("no se ejecutará de forma
   programada hasta activarla") y **Advertencia `SIN_PROGRAMACION`** ("no se ejecutará
   automáticamente").
3. Pulsar **Activar** en el detalle de la estrategia (sigue sin programación).
4. **Alertas → Revisar ahora** (o esperar el monitor, cada 15 min).
5. Aparece la **advertencia `SIN_PROGRAMACION`**: "La estrategia ... no tiene
   programación: no se ejecutará automáticamente".

### B. Hora fuera de la ventana de respaldo

1. En una estrategia, programar frecuencia **DIARIA** a las `14:00` con ventana `22:00`
   a `05:00`.
2. **Validar contra la base** → **Advertencia `FUERA_DE_VENTANA`**.

### C. Frecuencia insuficiente para la prioridad

1. Prioridad **ALTA** con programación **SEMANAL** de un solo día.
2. **Validar contra la base** → **Advertencia `FRECUENCIA_INSUFICIENTE`** ("deja hasta 168 h entre
   respaldos; para prioridad ALTA se espera como máximo 24 h").

### Sobre NOARCHIVELOG

La advertencia de NOARCHIVELOG que propone el PDF está implementada (`NOARCHIVELOG` en
validación y como alerta de la base), pero **no se reproduce en este ambiente** porque la
base está en ARCHIVELOG y la aplicación no cambia el modo de archivado (regla 5). Se puede
mostrar el texto en el código (`ReglasValidacion.archivado`).

**Evidencia:** captura de la validación con la etiqueta **Advertencia** y de la alerta en
la pantalla Alertas. Las tres etiquetas (Informativo, Advertencia, Recomendación) deben
verse con colores distintos.

---

## E6. Aplicación de una recomendación

**Objetivo:** demostrar que una recomendación se detecta, se muestra como tal y **solo se
aplica por decisión del administrador**.

1. Crear una estrategia `Demo recomendación`: prioridad **ALTA**, QUÉ =
   **Base de datos** y **Control file** (sin Archived redo logs), CÓMO = nivel 0, destino
   `/opt/oracle/oradata/respaldos`, retención `7`, programación DIARIA a las `23:00`. Guardar y pulsar **Activar** (el monitor solo revisa estrategias activas).
2. **Validar contra la base** → aparece **Recomendación `INCLUIR_ARCHIVELOG`**: "La estrategia tiene
   prioridad ALTA y la base está en ARCHIVELOG. Considere incorporar el respaldo periódico
   de los archived redo logs...". La validación sigue siendo válida: la recomendación no
   bloquea ni se aplica sola.
3. **Generar script** y **Aprobar**. Anotar la versión (por ejemplo, v1 APROBADO).
4. **Alertas → Revisar ahora.** Aparece la alerta tipo **Recomendación**
   `INCLUIR_ARCHIVELOG` para la estrategia.
5. Pulsar **Aplicar a la estrategia** (el administrador decide; puede dejar un comentario).

**Resultado esperado:**

- La alerta queda **APLICADA**, con su nombre, fecha y el comentario "Se agregó ARCHIVELOG
  a la estrategia ... por decisión del administrador. Se invalidaron 1 script(s)...".
- En la estrategia, el QUÉ ahora incluye **ARCHIVELOG**.
- El script v1 pasa a **INVALIDADO**: el botón Ejecutar ahora se deshabilita hasta generar
  y aprobar uno nuevo.
- **Generar script** → v2 incluye `BACKUP ... TAG 'EST<id>_ARC' ... ARCHIVELOG ALL;`.
- Al validar de nuevo, la recomendación ya no aparece; en la siguiente revisión preventiva
  la alerta abierta (si quedara alguna) se cierra sola.
- La base de datos Oracle **no se modificó**: solo cambió la estrategia.

**Evidencia:** captura de la recomendación en la validación, de la alerta antes y después
de aplicarla, del script invalidado y del nuevo script con `ARCHIVELOG ALL`.

---

## E7. Ejecución programada (automatización)

**Objetivo:** mostrar que el programador ejecuta sin intervención del administrador.

1. Tomar una estrategia con script **APROBADO** (por ejemplo, la de E2).
2. **Modificar** solo la programación: frecuencia **UNA_VEZ**, fecha de hoy, hora = dentro
   de 3 minutos, sin ventana. **Guardar.** (Cambiar el CUÁNDO no invalida el script: solo
   lo hacen base, QUÉ, CÓMO o destino.)
3. Comprobar que la estrategia y su programación estén **activas**. En el **Panel** se ve
   la próxima ejecución.
4. Esperar. El programador revisa cada 60 s; al llegar la hora dispara la ejecución.

**Resultado esperado:** nueva ejecución con **origen PROGRAMADA** y **fecha programada**, con
la misma evidencia que E2. La validación avisará `SIN_REPETICION` (una sola vez no mantiene
la protección), lo cual es correcto para esta demostración.

Variante de control: si la app estaba apagada más de 30 minutos después de la hora, no
ejecuta tarde y registra la advertencia `EJECUCION_OMITIDA`. Si el script no está aprobado,
no ejecuta y registra `SIN_SCRIPT_EJECUTABLE`.

---

## Limpieza (opcional)

- Desactivar las estrategias de demostración para que el programador no las ejecute.
  No se pueden borrar si tienen ejecuciones: son evidencia.
- Quitar la carpeta de la falla controlada solo cuando ya no se necesite:
  ```
  docker exec oracle-xe sh -c 'chmod 755 /opt/oracle/oradata/respaldos_sin_permiso && rmdir /opt/oracle/oradata/respaldos_sin_permiso'
  ```
