# Decisiones, elementos de prueba y pendientes

Registro de decisiones del proyecto que no se deducen del código, de lo que se dejó
creado en el ambiente para pruebas y de las recomendaciones que quedan a decisión del
administrador.

## Elementos de prueba creados en el ambiente

### Carpeta de solo lectura para la falla controlada

| Dato | Valor |
|---|---|
| Ruta (dentro del contenedor `oracle-xe`) | `/opt/oracle/oradata/respaldos_sin_permiso` |
| Permisos | `dr-xr-xr-x` (555), dueño `oracle:oinstall` |
| Creada con | `docker exec oracle-xe sh -c 'mkdir /opt/oracle/oradata/respaldos_sin_permiso && chmod 555 /opt/oracle/oradata/respaldos_sin_permiso'` |
| Fecha | 2026-10-01 (módulo 8) |
| Para qué | Evidencia de "ejecución fallida o simulación controlada de error" (entregable 4 del PDF) |
| Estrategia que la usa | "XE falla controlada" (solo SPFILE, respaldo completo, sin compresión) |

Por qué así: el validador acepta el destino porque la carpeta existe (`df` funciona),
pero RMAN corre como `oracle` y no puede crear archivos ahí. El respaldo falla con
ORA-19504 / ORA-27040 sin tocar la base ni los respaldos reales. Se eligió SPFILE porque
es el respaldo más pequeño y rápido.

Resultado registrado: ejecución 19 (script 87, aprobado por Emmanuel Rodriguez) quedó
**FALLIDO** en 2 s con `RMAN-03009`, `ORA-19504: failed to create file
"/opt/oracle/oradata/respaldos_sin_permiso/..."` y `ORA-27040`, verificación `NO_APLICA`, y
generó la alerta `EJECUCION_FALLIDA`. Como contraste, la ejecución 18 (estrategia "XE nivel
0 completo", script 86) quedó **EXITOSO** y **VERIFICADO**.

Para quitarla cuando ya no se necesite:

```
docker exec oracle-xe sh -c 'chmod 755 /opt/oracle/oradata/respaldos_sin_permiso && rmdir /opt/oracle/oradata/respaldos_sin_permiso'
```

Antes de quitarla, desactive la estrategia "XE falla controlada" (no se puede borrar
porque tiene historial, y así debe ser: es evidencia).

## Recomendaciones pendientes (no aplicadas)

La aplicación nunca aplica recomendaciones por su cuenta (regla 5). Estas quedan para
que el administrador decida.

### Autorespaldo del control file fuera del volumen persistente

- **Situación:** RMAN tiene `CONFIGURE CONTROLFILE AUTOBACKUP ON` con formato `'%F'`
  (valor por defecto). Por eso los autorespaldos del control file y del SPFILE se
  guardan en `$ORACLE_HOME/dbs` (`/opt/oracle/homes/OraDBHome21cXE/dbs/c-...`).
- **Riesgo:** el único volumen persistente del contenedor es `/opt/oracle/oradata`. Si
  se recrea el contenedor, esos autorespaldos se pierden, y con ellos una forma de
  recuperar el control file.
- **Recomendación:** dirigir el autorespaldo al destino de respaldos, por ejemplo:
  `CONFIGURE CONTROLFILE AUTOBACKUP FORMAT FOR DEVICE TYPE DISK TO '/opt/oracle/oradata/respaldos/%F';`
- **Estado:** pendiente. Por decisión del administrador no se cambió la configuración.
  Mientras tanto, las estrategias pueden incluir el elemento CONTROLFILE, que sí se
  guarda en el destino de la estrategia.

### Destino de respaldos en el mismo disco que los datafiles

- **Situación:** `/opt/oracle/oradata/respaldos` está en el mismo sistema de archivos
  que los datafiles (`/dev/sdd` montado en `/opt/oracle/oradata`). El validador lo
  informa como recomendación `DESTINO_MISMO_DISCO`.
- **Riesgo:** si se pierde ese disco se pierden la base y sus respaldos.
- **Estado:** aceptado para el ambiente de pruebas; en un ambiente real el destino debe
  estar en otro dispositivo.

## Decisiones de diseño

| Tema | Decisión | Motivo |
|---|---|---|
| Esquema de la base | Flyway (`db/migration`); Hibernate solo valida | Cambios versionados y repetibles |
| Automatización | Programador propio de la aplicación (revisa cada 60 s) | Mantiene la relación estrategia → programación → script → ejecución dentro de la app |
| Ejecución atrasada | Si se pasó más de 30 min (tolerancia), no se ejecuta tarde: alerta `EJECUCION_OMITIDA` | Evitar salirse de la ventana de respaldo |
| Una programación por estrategia | Un esquema "nivel 0 semanal + nivel 1 diario" son dos estrategias | Modelo más simple |
| Retención | No va en el script | `DELETE OBSOLETE` borra respaldos de toda la base, no solo de la estrategia |
| Cambio de redo log antes de archivelogs | No se incluye | Usar solo comandos probados en el ambiente |
| Aprobación | Se aprueba con la huella SHA-256 del texto mostrado; un cambio de base, QUÉ, CÓMO o destino invalida el script | El texto ejecutado es exactamente el revisado |
| Verificación | Existencia de cada pieza (`stat`) + `CROSSCHECK BACKUPPIECE` + `RESTORE ... VALIDATE` | Regla 6: un script sin errores no prueba que el respaldo sirva |
| Activación | Activar una estrategia no exige script aprobado; el programador solo ejecuta con script aprobado, vigente e íntegro | Separar la configuración de la autorización |

## Limitaciones conocidas

- **Sin autenticación.** "Aprobado por", "rechazado por" y "atendida por" registran el
  nombre enviado, sin verificar identidad.
- **Catálogo limitado a XEPDB1.** El catálogo se lee con la conexión de la app
  (`respaldos@XEPDB1`); no ve los tablespaces de la raíz del CDB. `BACKUP DATABASE` sí
  respalda todo el CDB.
- **Estimación de espacio.** Es una cota superior con los datafiles de XEPDB1.
- **"Hay nivel 0".** Solo cuenta niveles 0 ejecutados por la app.
- **Tiempo agotado.** Si se agota el tiempo máximo se corta el cliente `docker exec`,
  pero RMAN puede seguir dentro del contenedor.
