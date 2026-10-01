# Herramienta de gestión de estrategias de respaldo Oracle (EIF402)

## Qué es
Aplicación que permite definir estrategias de respaldo (QUÉ - CÓMO - CUÁNDO),
generar el script RMAN, programarlo, ejecutarlo y guardar evidencia y alertas.
Es un control preventivo de riesgos de disponibilidad e integridad.
RMAN es solo el motor; el valor está en la gestión que lo rodea.
Los PDFs de la asignación están en /docs.

## Stack
- Backend: Java + Spring Boot con Maven, en la raíz del proyecto
- Frontend: React + TypeScript, irá en /frontend
- Base de la app: Oracle XE, esquema `respaldos` en XEPDB1

## Ambiente de pruebas (Docker)
- Contenedor: `oracle-xe` (imagen gvenzl/oracle-xe:21)
- Puerto del host: 1522 (el 1521 lo usa otra Oracle local: NO tocarla)
- JDBC: jdbc:oracle:thin:@//localhost:1522/XEPDB1
- Usuario de la app: respaldos / Respaldos123
- Usuario admin: sys as sysdba / Admin123
- RMAN corre DENTRO del contenedor: `docker exec oracle-xe rman target /`
- Base en modo ARCHIVELOG
- Destino persistente de respaldos: /opt/oracle/oradata/respaldos

## Comandos RMAN ya probados en este ambiente
- BACKUP DATABASE
- BACKUP INCREMENTAL LEVEL 0 DATABASE
- BACKUP INCREMENTAL LEVEL 1 DATABASE (diferencial)
- BACKUP INCREMENTAL LEVEL 1 CUMULATIVE DATABASE
- BACKUP AS COMPRESSED BACKUPSET DATABASE
- BACKUP ARCHIVELOG ALL
- CROSSCHECK BACKUP
- RESTORE DATABASE VALIDATE
- LIST BACKUP SUMMARY
  Salidas a tener en cuenta:
- "skipping datafile ... has not changed" y "backup cancelled because all
  files were skipped" NO son errores.
- El éxito se detecta con "Finished backup"; los errores, con líneas RMAN- / ORA-.
- Un nivel 1 diferencial y uno acumulativo se ven igual en LIST BACKUP
  SUMMARY; la app guarda cuál comando generó.

## Reglas del proyecto (obligatorias)
1. Probar solo en el contenedor oracle-xe. Nunca sobre la Oracle local.
2. Validar la configuración antes de generar o ejecutar un script.
3. Mostrar el script y exigir aprobación del administrador antes de ejecutarlo.
4. Distinguir tres tipos de mensaje: informativo, advertencia y recomendación.
5. Una recomendación nunca se aplica automáticamente. La app no cambia el
   modo de archivado.
6. Un script sin errores no prueba que la estrategia sea correcta: verificar
   que el archivo de respaldo exista y usar CROSSCHECK / VALIDATE.
7. Resultado de ejecución: Exitoso / Con advertencias / Fallido.
8. Flujo: estrategia → validación → script → aprobación → programación →
   ejecución → evidencia → alertas.

## Forma de trabajo
- Un módulo por vez, con commit al terminar cada uno.
- Explicar qué se hizo, sobre todo en el generador de scripts.
- No avanzar a otra fase sin que yo lo apruebe.