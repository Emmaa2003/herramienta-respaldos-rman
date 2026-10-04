# Documento de análisis

Herramienta para la gestión de estrategias de respaldo de bases de datos Oracle
EIF402 – Administración de Bases de Datos, II ciclo 2026

---

## 1. Problema

En muchas organizaciones el respaldo de una base de datos Oracle se reduce a un script
RMAN que alguien escribió una vez y que se ejecuta con `cron` o a mano. Ese enfoque
tiene problemas que no se ven hasta el día en que hace falta recuperar:

- **No hay una estrategia explícita.** Nadie dejó escrito *qué* se protege, *por qué* con
  ese tipo de respaldo ni *cuándo* debe correr. La decisión vive en la cabeza de una
  persona o enterrada en un script.
- **Se confunde "el script terminó" con "el respaldo sirve".** Un script puede terminar
  sin errores y aun así no producir un respaldo utilizable (archivo borrado, pieza
  incompleta, destino equivocado, base en NOARCHIVELOG sin forma de recuperar a un punto
  en el tiempo).
- **No hay evidencia.** Si se pregunta "¿se respaldó la base el martes?", no hay un
  historial confiable con inicio, fin, resultado, archivos y mensajes de RMAN.
- **Las fallas se descubren tarde.** Una tarea programada que dejó de correr, un disco
  lleno o una estrategia desactivada pasan desapercibidos durante días.
- **Los cambios son riesgosos.** Modificar la estrategia significa editar scripts y tareas
  sueltas a mano, sin revisión ni trazabilidad.

**Problema central:** falta una capa de gestión que convierta la necesidad de proteger la
información en una estrategia definida, validada, automatizada y verificable, con
evidencia y alertas. RMAN resuelve la parte técnica del respaldo; no resuelve la gestión.

## 2. Riesgos asociados

El proyecto se plantea como un **control preventivo**: actuar antes de que ocurra el
evento que afecta la información. Los riesgos se agrupan en las dos dimensiones que pide
la asignación.

### 2.1 Riesgo de disponibilidad

Posibilidad de que la base de datos o su información no esté disponible cuando se
necesita.

| Riesgo | Causa típica | Consecuencia |
|---|---|---|
| No existe un respaldo reciente | Estrategia inactiva, sin programación, tarea que no corrió | No hay desde dónde restaurar, o se pierde mucho trabajo |
| El respaldo programado no se ejecutó | Servidor o aplicación apagados, script no aprobado | Hueco de protección que nadie nota |
| Recuperación demasiado lenta | Tipo de respaldo mal elegido (muchos diferenciales que aplicar) | Tiempo fuera de servicio más largo |
| Falta de espacio en el destino | Crecimiento de la base, respaldos acumulados | El respaldo falla a mitad de camino |
| Respaldo en el mismo disco que los datos | Destino mal ubicado | Si se pierde el disco se pierden base y respaldos |
| Respaldo ejecutado fuera de horario | Programación sin ventana | Afecta el rendimiento de la operación normal |

### 2.2 Riesgo de integridad

Posibilidad de pérdida, corrupción o alteración que impida recuperar un estado correcto.

| Riesgo | Causa típica | Consecuencia |
|---|---|---|
| Respaldo corrupto o incompleto | Error de E/S, pieza truncada | La restauración falla justo cuando se necesita |
| Archivo de respaldo inexistente | Borrado manual, ruta equivocada | El catálogo dice que hay respaldo, pero no existe |
| Pérdida de cambios entre respaldos | NOARCHIVELOG o archived redo logs sin respaldar | Solo se puede volver al último respaldo, no a un punto en el tiempo |
| Falta el control file o el SPFILE | Estrategia parcial | No se puede montar ni arrancar la instancia tras una pérdida total |
| Script alterado o distinto al revisado | Cambio después de aprobarlo | Se ejecuta algo que nadie revisó |
| Estrategia mal configurada | Tablespace inexistente, nivel 1 sin nivel 0 | Respaldo que no cubre lo que se cree |

## 3. Justificación

- Los respaldos son la última línea de defensa ante fallas de hardware, errores humanos,
  corrupción y ataques. Si fallan, no hay otra.
- La asignación pide pasar de "ejecutar un script" a **gestionar una estrategia**: planificada,
  automatizada y verificable. Eso es exactamente lo que falta en el enfoque de scripts
  sueltos.
- RMAN ya ofrece los mecanismos técnicos (respaldos completos e incrementales, compresión,
  `CROSSCHECK`, `RESTORE ... VALIDATE`). La herramienta no reemplaza a RMAN: lo usa como
  motor y agrega validación, aprobación, programación, evidencia y alertas.
- Un control preventivo es más barato que uno correctivo: detectar hoy que la estrategia
  no tiene programación cuesta un clic; descubrirlo el día de una caída cuesta los datos.

## 4. Objetivos

### Objetivo general

Desarrollar una herramienta que, usando RMAN como motor de ejecución, permita definir,
validar, automatizar y verificar estrategias de respaldo de bases de datos Oracle, como
control preventivo de los riesgos de disponibilidad e integridad de la información.

### Objetivos específicos

1. Modelar una estrategia con tres componentes: **QUÉ** respaldar (elementos y prioridad),
   **CÓMO** (tipo de respaldo, compresión, destino) y **CUÁNDO** (fecha, hora, frecuencia,
   días, intervalo y ventana).
2. Validar la estrategia contra la base real antes de generar el script (objetos, modo de
   archivado, destino, espacio, coherencia de la programación).
3. Generar automáticamente el script RMAN y explicar paso a paso cómo se tradujo cada
   decisión.
4. Exigir la revisión y aprobación del administrador antes de ejecutar un script, y
   garantizar que lo ejecutado sea exactamente lo aprobado.
5. Automatizar la ejecución con un programador propio que mantenga la relación
   estrategia → programación → script → ejecución.
6. Registrar evidencia completa de cada ejecución y verificar el respaldo con la existencia
   de los archivos, `CROSSCHECK` y `RESTORE ... VALIDATE`.
7. Detectar condiciones de riesgo y generar mensajes diferenciados: informativo,
   advertencia y recomendación, sin aplicar nunca una recomendación de forma automática.

## 5. Requerimientos

### 5.1 Requerimientos funcionales

| Id | Requerimiento |
|---|---|
| RF-01 | Registrar, modificar, eliminar e **inspeccionar** bases de datos (modo de archivado, tablespaces, datafiles). |
| RF-02 | Crear, modificar, activar, desactivar, consultar y eliminar estrategias. |
| RF-03 | Información general: nombre, descripción, base, responsable, prioridad, estado. |
| RF-04 | QUÉ: base completa, tablespaces, datafiles, control file, SPFILE y archived redo logs. |
| RF-05 | CÓMO: completo, incremental nivel 0, nivel 1 diferencial, nivel 1 acumulativo; compresión; retención. |
| RF-06 | CUÁNDO: fecha de inicio, hora, frecuencia (una vez, cada N horas, diaria, semanal, mensual), días, intervalo y ventana. |
| RF-07 | Destino: ruta, dispositivo (DISK) y espacio disponible. |
| RF-08 | Validar la estrategia y clasificar cada hallazgo como bloqueante, advertencia, recomendación o informativo. |
| RF-09 | Generar el script RMAN, revisarlo con `rman checksyntax` y mostrarlo con su explicación. |
| RF-10 | Aprobar o rechazar un script (con nombre y comentario); invalidarlo si la estrategia cambia. |
| RF-11 | Programar la ejecución automática y mostrar las próximas ejecuciones. |
| RF-12 | Ejecutar manualmente o de forma programada solo scripts aprobados, vigentes e íntegros. |
| RF-13 | Registrar evidencia: estrategia, base, fechas, duración, tipo, script, salida de RMAN, archivos, tamaño, errores. |
| RF-14 | Verificar cada respaldo (archivos + `CROSSCHECK` + `RESTORE VALIDATE`) y clasificar el resultado como Exitoso, Con advertencias o Fallido. |
| RF-15 | Consultar el historial de ejecuciones. |
| RF-16 | Generar alertas ante condiciones de riesgo y permitir atenderlas, descartarlas o aplicar recomendaciones por decisión del administrador. |

### 5.2 Requerimientos no funcionales

| Id | Requerimiento |
|---|---|
| RNF-01 | Probar solo en el ambiente de pruebas (contenedor `oracle-xe`); la app rechaza cualquier otro contenedor. |
| RNF-02 | La aplicación no cambia el modo de archivado ni ejecuta operaciones destructivas o de recuperación. |
| RNF-03 | Trazabilidad: cada ejecución guarda una copia del script ejecutado y queda ligada al script aprobado. |
| RNF-04 | Integridad del script: huella SHA-256 del texto aprobado; si no coincide, no se ejecuta. |
| RNF-05 | Interfaz web en español, sin necesidad de escribir RMAN a mano. |
| RNF-06 | Esquema de la base versionado con migraciones (Flyway). |

## 6. Modelo de estrategia (QUÉ – CÓMO – CUÁNDO)

```
Necesidad de protección
   └─ QUÉ  : elementos (base, tablespaces, datafiles, control file, SPFILE, archivelogs) + prioridad
   └─ CÓMO : tipo de respaldo + compresión + destino
   └─ CUÁNDO: fecha, hora, frecuencia, días, intervalo, ventana
        ↓
   validación → script RMAN → aprobación → programación → ejecución → evidencia → alertas
```

### 6.1 Criterios de prioridad (definidos por el grupo)

La prioridad no decide por sí sola el tipo de respaldo; define **cuánto tiempo puede pasar
sin un respaldo exitoso** antes de alertar y qué se recomienda.

| Prioridad | Criterio | Máximo sin respaldo exitoso | Ejemplo |
|---|---|---|---|
| Alta | Su pérdida detiene o afecta gravemente la operación | 24 h; se recomienda incluir archived redo logs | Producción, transacciones, datos financieros |
| Media | La operación tolera perder horas o un día de cambios | 7 días | Sistemas internos, reportes |
| Baja | Se puede reconstruir o recuperar por otros medios | 30 días | Pruebas, datos cargados desde otra fuente |

El validador usa ese máximo para advertir si la programación deja más tiempo entre
respaldos (`FRECUENCIA_INSUFICIENTE`), y el monitor para alertar si no hubo respaldo
reciente (`SIN_RESPALDO_RECIENTE`).

## 7. Análisis de tipos de respaldo

### 7.1 Descripción

- **Completo (`BACKUP DATABASE`)**: copia todos los bloques usados. Es independiente, pero
  **no sirve como base** de una cadena incremental.
- **Incremental nivel 0 (`BACKUP INCREMENTAL LEVEL 0`)**: copia lo mismo que un completo,
  pero queda registrado como **punto de partida** de los nivel 1.
- **Incremental nivel 1 diferencial (`BACKUP INCREMENTAL LEVEL 1`)**: copia los bloques que
  cambiaron desde el **último incremental** (nivel 0 o nivel 1).
- **Incremental nivel 1 acumulativo (`BACKUP INCREMENTAL LEVEL 1 CUMULATIVE`)**: copia los
  bloques que cambiaron desde el **último nivel 0**.

### 7.2 Comparación

| Criterio | Completo | Nivel 0 | Nivel 1 diferencial | Nivel 1 acumulativo |
|---|---|---|---|---|
| Espacio de almacenamiento | Alto en cada ejecución | Alto (una vez por ciclo) | El menor | Medio; crece durante el ciclo |
| Tiempo de respaldo | Largo | Largo | El más corto | Corto, aumenta cada día del ciclo |
| Frecuencia típica | Semanal o antes de operaciones críticas | Semanal | Diaria (o varias veces al día) | Diaria |
| Volumen de cambios adecuado | Cualquiera; base pequeña | Cualquiera | Pocos cambios entre ejecuciones | Cambios moderados |
| Complejidad de recuperación | La menor: un solo respaldo | Baja | La mayor: nivel 0 + **todos** los nivel 1 | Media: nivel 0 + **el último** acumulativo |
| Necesidad de disponibilidad (tiempo de recuperación) | Buena | Buena | Recuperación más lenta | Recuperación más rápida que diferencial |
| Protección de la información | Copia independiente | Base de la cadena | Si se pierde un nivel 1 intermedio se rompe la cadena | Más tolerante: cada acumulativo reemplaza a los anteriores |

### 7.3 Cuándo conviene cada uno

- **Completo**: bases pequeñas, copia de referencia antes de una actualización o migración,
  o cuando se quiere una copia que no dependa de ninguna otra.
- **Nivel 0**: inicio de cada ciclo incremental (por ejemplo, el domingo).
- **Diferencial**: bases grandes con pocos cambios diarios y poco espacio o ventana corta;
  se acepta una recuperación más laboriosa.
- **Acumulativo**: cuando importa recuperar rápido (prioridad alta) y hay espacio para
  respaldos diarios algo más grandes.

**Esquema recomendado para prioridad alta:** nivel 0 semanal + nivel 1 acumulativo diario
+ archived redo logs con frecuencia (varias veces al día) + control file y SPFILE en cada
ejecución. En la herramienta esto son dos estrategias (una por programación).

### 7.4 Observaciones del ambiente

- Un nivel 1 diferencial y uno acumulativo se ven igual en `LIST BACKUP SUMMARY`; por eso
  la aplicación guarda el comando exacto que generó en cada ejecución.
- Si no existe un nivel 0, RMAN hace el primer nivel 1 como nivel 0. El validador lo
  advierte (`SIN_NIVEL_0`).
- Mensajes como "skipping datafile ... has not changed" o "backup cancelled because all
  files were skipped" **no son errores**; la ejecución queda *Con advertencias*.

## 8. ARCHIVELOG vs NOARCHIVELOG

### 8.1 Diferencias

| Aspecto | ARCHIVELOG | NOARCHIVELOG |
|---|---|---|
| Redo logs llenos | Se copian (archivan) antes de reutilizarse | Se sobrescriben |
| Respaldo con la base abierta (en caliente) | Sí | No: solo con la base en MOUNT (ORA-19602) |
| Recuperación | Completa, o hasta un punto en el tiempo (PITR) | Solo al momento del último respaldo |
| Pérdida de datos ante una falla de medios | Mínima o nula | Todo lo ocurrido desde el último respaldo |
| Respaldos incrementales | Útiles en caliente | Solo consistentes, con la base montada |
| Espacio adicional | Archived redo logs que hay que respaldar y depurar | No |
| Uso típico | Producción | Pruebas, bases que se pueden recrear |

### 8.2 Implicaciones para la estrategia

- **En NOARCHIVELOG** la estrategia solo puede volver a la foto del último respaldo, y
  además necesita detener la base para respaldar datafiles. La herramienta:
  - muestra la **advertencia** preventiva que pide la asignación;
  - **bloquea** incluir ARCHIVELOG (no existen) y respaldar datafiles con la base abierta;
  - genera una alerta persistente `NOARCHIVELOG` para la base.
- **En ARCHIVELOG** la herramienta reconoce que los archived redo logs pueden y deben
  formar parte de la estrategia y, si no están, muestra la **recomendación** de incluirlos.
- **La aplicación nunca cambia el modo de archivado.** Pasar a ARCHIVELOG requiere
  reiniciar la base y planificar espacio: es una decisión del administrador.

El ambiente de pruebas está en ARCHIVELOG.

## 9. Controles preventivos propuestos

La asignación pide explicar cómo cada funcionalidad contribuye al control preventivo.

| Funcionalidad | Qué previene | Riesgo que reduce |
|---|---|---|
| Inspección de la base | Estrategias construidas sobre supuestos falsos (modo de archivado, objetos) | Integridad |
| Validación previa | Scripts que fallarían o que no cubren lo que se cree (objetos inexistentes, tablespace temporal, destino inexistente, NOARCHIVELOG con base abierta) | Integridad y disponibilidad |
| Clasificación de mensajes | Que una recomendación se confunda con un error o con una acción automática | Ambos |
| Generador de scripts | Errores al escribir RMAN a mano; omisión del control file después de los datos | Integridad |
| `rman checksyntax` | Scripts con errores de sintaxis | Disponibilidad |
| Aprobación con huella SHA-256 | Ejecutar un texto distinto al revisado o una configuración que cambió | Integridad |
| Programador propio | Depender de que alguien recuerde ejecutar el respaldo | Disponibilidad |
| Tolerancia de 30 min | Respaldos ejecutados tarde, fuera de la ventana | Disponibilidad |
| Ventana de respaldo | Afectar la operación con respaldos en horario pico | Disponibilidad |
| Evidencia por ejecución | No poder demostrar qué se respaldó, cuándo y con qué resultado | Ambos (trazabilidad) |
| Verificación (archivo + `CROSSCHECK` + `RESTORE VALIDATE`) | Creer que hay respaldo cuando el archivo no existe o está dañado | Integridad |
| Clasificación Exitoso / Con advertencias / Fallido | Ocultar problemas parciales | Ambos |
| Monitor preventivo (cada 15 min) | Que una condición de riesgo pase días sin detectarse | Ambos |
| Alertas persistentes | Olvidar un problema detectado; se cierran solas cuando la condición desaparece | Ambos |
| Recomendaciones bajo decisión del administrador | Cambios automáticos no deseados | Integridad |

### 9.1 Condiciones que vigila el monitor

| Condición de la asignación | Código | Tipo |
|---|---|---|
| Estrategia sin programación | `SIN_PROGRAMACION` | Advertencia |
| Estrategia inactiva | `ESTRATEGIA_INACTIVA` | Advertencia |
| Respaldo programado que no se ejecutó | `EJECUCION_OMITIDA` | Advertencia |
| Ejecución fallida | `EJECUCION_FALLIDA` | Advertencia |
| Falta de espacio disponible | `FALTA_ESPACIO` | Advertencia |
| Ausencia de archived redo logs cuando son requeridos | `INCLUIR_ARCHIVELOG` | Recomendación |
| Base de datos en NOARCHIVELOG | `NOARCHIVELOG` | Advertencia |
| Estrategia sin respaldo reciente | `SIN_RESPALDO_RECIENTE` | Advertencia |
| Script con configuración incompleta | `CONFIGURACION_INCOMPLETA`, `SIN_SCRIPT_EJECUTABLE` | Advertencia |
| Adicionales | `CONTENEDOR_DETENIDO`, `PROGRAMACION_INACTIVA`, `INCLUIR_CONTROLFILE`, `VALIDACION_FALLIDA` | Advertencia / Recomendación |

### 9.2 Tres tipos de mensaje

- **Informativo**: un dato útil, no requiere acción. Ej.: espacio disponible en el destino,
  cómo se recupera con diferencial o acumulativo.
- **Advertencia**: una condición que puede comprometer la estrategia y debe revisarse. Ej.:
  NOARCHIVELOG, falta de espacio, ejecución fallida, sin respaldo reciente.
- **Recomendación**: una mejora sugerida que **solo se aplica si el administrador lo decide**.
  Ej.: incluir archived redo logs o el control file. Al aplicarla, la app modifica la
  estrategia, invalida el script anterior y exige generar y aprobar uno nuevo.

Además, el validador marca algunos hallazgos como **bloqueantes**: impiden generar o
ejecutar el script hasta corregirlos.

## 10. Respuesta a la pregunta orientadora

> ¿Cómo puede una herramienta de gestión de estrategias de respaldo utilizar RMAN para
> establecer controles preventivos que permitan reducir los riesgos asociados con la
> disponibilidad e integridad de la información de una base de datos Oracle?

La herramienta usa a RMAN **solo como motor** y coloca alrededor de él una cadena de
controles que actúan **antes**, **durante** y **después** de cada respaldo:

1. **Antes de respaldar** (prevenir una mala estrategia): obliga a definir QUÉ, CÓMO y
   CUÁNDO; inspecciona la base real; valida la configuración contra ella (objetos, modo de
   archivado, espacio, ventana, frecuencia según la prioridad); genera el script sin que
   nadie escriba RMAN a mano, revisa su sintaxis con `rman checksyntax` y exige que el
   administrador lo apruebe. La huella SHA-256 garantiza que se ejecuta exactamente lo
   aprobado. Esto reduce el **riesgo de integridad** (respaldos que no cubren lo que se
   cree) y el de **disponibilidad** (scripts que fallarían).
2. **Durante** (prevenir la dependencia humana): el programador propio ejecuta la estrategia
   cuando corresponde, respeta la ventana y no ejecuta tarde ni con scripts no aprobados.
   Esto reduce el **riesgo de disponibilidad** por respaldos olvidados.
3. **Después** (prevenir la falsa seguridad): no da por bueno un respaldo porque RMAN
   terminó; comprueba que cada archivo exista, usa `CROSSCHECK` y `RESTORE ... VALIDATE`
   para confirmar que se podría restaurar, clasifica el resultado y guarda la evidencia
   completa. Esto reduce el **riesgo de integridad** (respaldos inexistentes o dañados).
4. **Continuamente** (prevenir que un problema pase desapercibido): un monitor revisa cada
   15 minutos estrategias inactivas, sin programación, sin respaldo reciente, sin espacio,
   bases en NOARCHIVELOG o ejecuciones fallidas, y genera alertas diferenciadas
   (informativo, advertencia, recomendación). Las recomendaciones nunca se aplican solas:
   la decisión queda en el administrador.

En resumen: RMAN sabe **hacer** un respaldo; la herramienta asegura que el respaldo
**correcto** se haga **a tiempo**, que **sirva** y que, si algo falla, **alguien se entere**
antes de que haga falta recuperar.
