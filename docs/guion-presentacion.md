# Guion de la presentación

Demostración de la herramienta de gestión de estrategias de respaldo Oracle.
Duración sugerida: 15 a 20 minutos. Lenguaje simple, como si se lo explicáramos a alguien
que no ha usado RMAN.

La asignación pide contar la demo en este orden:
**Qué se configuró → Cómo se configuró → Cuándo se ejecuta → Qué script RMAN se genera →
Cómo se ejecuta → Qué evidencia queda.** El guion sigue ese orden.

---

## Antes de empezar (checklist)

- [ ] `docker start oracle-xe` y esperar "DATABASE IS READY TO USE!".
- [ ] Backend corriendo (`.\mvnw.cmd spring-boot:run` con JDK 25).
- [ ] Frontend corriendo (`npm run dev` en `frontend/`).
- [ ] Navegador abierto en **http://localhost:5173**.
- [ ] Nombre escrito en el campo **Administrador**.
- [ ] Tener a mano las ejecuciones 18 (exitosa) y 19 (fallida) por si algo tarda.
- [ ] Tener la estrategia de la demo ya creada como respaldo (ver `escenarios.md`, E1).

---

## 1. El problema (1–2 min)

> "Casi todos hemos visto esto: alguien escribe un script de respaldo, lo pone en una
> tarea programada y se olvida. El día que se cae la base descubrimos que el script dejó
> de correr hace un mes, o que corrió pero el archivo no está, o que no se puede
> recuperar a la hora que necesitamos."

> "Tener un script no es tener una estrategia. Una estrategia responde tres preguntas:
> **qué** protejo, **cómo** lo protejo y **cuándo**. Y agrega una cuarta: **¿cómo sé que
> de verdad se hizo y que sirve?**"

> "Nuestra herramienta usa RMAN, que es el programa de Oracle para hacer respaldos, como
> motor. Pero el valor está en todo lo que pusimos alrededor: validar, pedir aprobación,
> programar, comprobar y avisar cuando algo anda mal."

## 2. Los riesgos que queremos evitar (1 min)

> "Pensamos en dos riesgos:"
>
> - "**Disponibilidad**: que la base no esté cuando la necesitamos. Por ejemplo, porque no
>   hay un respaldo reciente desde dónde restaurar."
> - "**Integridad**: que la información se pierda o se dañe y no podamos volver a un
>   estado correcto. Por ejemplo, porque el respaldo está incompleto."

> "Por eso la herramienta es un **control preventivo**: actúa antes de que pase el
> problema, no después."

## 3. Recorrido rápido de la arquitectura (1 min)

Mostrar el diagrama de `diseno.md` (sección 1).

> "Hay una página web hecha en React, un servidor en Spring Boot y una base Oracle XE
> corriendo en Docker. Cuando hay que respaldar, el servidor le pide a RMAN, dentro del
> contenedor, que ejecute el script. La app solo puede tocar ese contenedor de pruebas;
> así nunca tocamos una base real por error."

## 4. La base de datos (1 min)

Pantalla **Bases de datos** → **Inspeccionar**.

> "Primero registramos la base que queremos proteger y la inspeccionamos. La app lee
> cosas importantes: que está en modo **ARCHIVELOG**, qué tablespaces y qué archivos de
> datos tiene."

> "¿Por qué importa ARCHIVELOG? Porque en ese modo Oracle guarda una copia de todos los
> cambios. Eso nos deja recuperar hasta un minuto exacto. Si la base estuviera en
> NOARCHIVELOG, solo podríamos volver a la foto del último respaldo y la app nos mostraría
> una **advertencia**. Ojo: la app **nunca** cambia ese modo; eso lo decide el
> administrador."

## 5. Qué se configuró (2 min)

Pantalla **Estrategias → Nueva estrategia**. Llenar o mostrar la ya creada.

> "Creamos la estrategia. Primero la información general: nombre, quién es el
> responsable y la **prioridad**."

> "La prioridad la definimos así: **alta** si perder esa información detiene la operación;
> **media** si se aguanta perder un día; **baja** si se puede reconstruir. Con prioridad
> alta esperamos un respaldo al menos cada 24 horas; si no lo hay, la app avisa."

> "Después el **QUÉ**: marcamos la base completa, el control file, que es como el índice
> de la base, el SPFILE, que es la configuración para arrancarla, y los archived redo logs,
> que son la copia de los cambios."

## 6. Cómo se configuró (1–2 min)

> "El **CÓMO** es el tipo de respaldo. Hay cuatro:"
>
> - "**Completo**: copia todo. Sencillo, pero pesado."
> - "**Nivel 0**: también copia todo, pero sirve de punto de partida para los siguientes."
> - "**Nivel 1 diferencial**: copia solo lo que cambió desde el último respaldo. Es el más
>   pequeño, pero para recuperar hay que aplicar todos uno tras otro."
> - "**Nivel 1 acumulativo**: copia lo que cambió desde el nivel 0. Es un poco más grande,
>   pero para recuperar basta el nivel 0 y el último acumulativo."

> "Elegimos **nivel 0 comprimido**, y el destino es una carpeta dentro del contenedor."

## 7. Cuándo se ejecuta (1 min)

> "El **CUÁNDO**: fecha de inicio, hora, frecuencia, días e intervalo, y la **ventana de
> respaldo**, que es el horario en que no molestamos a los usuarios, por ejemplo de 10 de
> la noche a 5 de la mañana. Aquí: todos los domingos a las 11 de la noche."

> "Lo típico en producción sería un nivel 0 el domingo y un nivel 1 todos los días. En
> nuestra app eso son dos estrategias. De hecho, como pusimos prioridad alta y un solo
> respaldo por semana, la validación nos va a advertir que no alcanza."

## 8. Validación (1–2 min)

Detalle de la estrategia → **Validar contra la base**.

> "Antes de generar nada, la app revisa la estrategia contra la base real: que los
> objetos existan, que haya espacio en el destino, que la hora caiga dentro de la ventana,
> que la frecuencia alcance para la prioridad."

> "Fíjense que los mensajes tienen colores distintos. Hay tres tipos:"
>
> - "**Informativo**: un dato, por ejemplo cuánto espacio hay."
> - "**Advertencia**: algo que hay que revisar, por ejemplo que la estrategia está inactiva."
> - "**Recomendación**: una mejora sugerida, como poner el destino en otro disco. La app
>   **nunca** la aplica sola."
>
> "Y si algo es grave, como un tablespace que no existe, es **bloqueante**: no deja seguir."

## 9. Qué script RMAN se genera (2–3 min) — la parte más importante

**Generar script.**

> "Ahora la app escribe el script de RMAN por nosotros. Nadie tiene que saber la sintaxis
> de memoria."

Señalar en pantalla:

> - "Todo va dentro de un bloque `RUN`. Si un paso falla, los siguientes no se ejecutan."
> - "Hay un `BACKUP` por cada cosa que marcamos en el QUÉ."
> - "El tipo de respaldo, `INCREMENTAL LEVEL 0`, solo va en la línea de los datos. Los
>   demás elementos siempre se copian completos."
> - "`AS COMPRESSED BACKUPSET` es porque marcamos comprimido."
> - "`FORMAT` dice dónde se guarda y cómo se llama el archivo; `TAG` es una etiqueta para
>   saber de qué estrategia vino."
> - "El control file va **después** de los datos, para que registre el respaldo que se
>   acaba de hacer."

> "Debajo está la tabla de **traducción paso a paso**: cada línea del script con la
> decisión que la generó. Además, la app le pidió a RMAN revisar la sintaxis y dice que no
> hay errores."

## 10. Aprobación (1 min)

**Aprobar script.**

> "Ningún script se ejecuta sin que el administrador lo apruebe. La app guarda una huella
> digital del texto. Si alguien cambia la estrategia después, por ejemplo agrega un
> tablespace, el script aprobado queda **invalidado** y hay que aprobar uno nuevo. Así
> siempre se ejecuta exactamente lo que alguien revisó."

## 11. Cómo se ejecuta (2 min)

> "Hay dos formas. La normal es **automática**: un programador dentro de la app revisa
> cada minuto si a alguna estrategia le toca. Si la app estuvo apagada y se pasó la hora
> por más de media hora, no lo ejecuta tarde: deja una advertencia de que se omitió."

> "Para la demo usamos **Ejecutar ahora**."

**Ejecutar ahora** (tarda ~2 min). Mientras tanto:

> "Mientras corre, una idea clave: que RMAN termine sin errores **no prueba** que el
> respaldo sirva. Por eso, al terminar, la app hace tres comprobaciones: mira que cada
> archivo exista en el disco, le pide a RMAN un `CROSSCHECK` para confirmar que las piezas
> están disponibles, y un `RESTORE VALIDATE`, que lee el respaldo como si fuera a
> restaurar pero sin restaurar nada."

## 12. Qué evidencia queda (2 min)

Abrir la ejecución.

> "Esta es la evidencia: estrategia, base, hora de inicio y fin, duración, tipo, el script
> exacto que se ejecutó, la salida completa de RMAN, la lista de archivos con su tamaño y
> si existen, y el resultado de la verificación."

> "El resultado puede ser **Exitoso**, **Con advertencias** o **Fallido**. Este quedó
> Exitoso y Verificado."

**Ejecuciones** (historial):

> "Y aquí está el historial de todas las ejecuciones, como el ejemplo de la asignación."

## 13. Cuando algo sale mal (2 min)

Abrir la ejecución 19 (o ejecutar "XE falla controlada").

> "Para mostrar una falla sin dañar nada, preparamos una carpeta donde Oracle no tiene
> permiso de escribir. La estrategia apunta ahí. RMAN falla con `ORA-19504`, la app lo
> marca como **Fallido**, guarda el error y crea una **alerta**."

## 14. Alertas y recomendaciones (2 min)

Pantalla **Alertas** → **Revisar ahora**.

> "Cada 15 minutos la app revisa sola si hay riesgos: estrategias inactivas o sin
> programación, respaldos que no se hicieron, falta de espacio, bases en NOARCHIVELOG,
> ejecuciones fallidas o estrategias sin respaldo reciente. Si el problema se resuelve, la
> alerta se cierra sola."

Mostrar una recomendación `INCLUIR_ARCHIVELOG` (escenario E6):

> "Esta es una **recomendación**: la estrategia es de prioridad alta y no respalda los
> archived redo logs. La app no la aplica por su cuenta. Si yo, como administrador, decido
> aplicarla, pulso **Aplicar a la estrategia**: se agrega el elemento, el script anterior
> queda invalidado y tengo que generar y aprobar uno nuevo. La base de Oracle no se tocó."

## 15. Cierre: respuesta a la pregunta del proyecto (1 min)

> "La pregunta era: ¿cómo puede una herramienta usar RMAN para crear controles
> preventivos que reduzcan los riesgos de disponibilidad e integridad?"

> "Nuestra respuesta: RMAN sabe **hacer** un respaldo. La herramienta se asegura de que se
> haga **el respaldo correcto** (validación y aprobación), **a tiempo** (programación),
> que **de verdad sirva** (verificación y evidencia) y que, si algo falla, **alguien se
> entere antes** de necesitar recuperar (alertas)."

> "Gracias. ¿Preguntas?"

---

## Preguntas probables y respuestas cortas

| Pregunta | Respuesta |
|---|---|
| ¿Por qué no usaron cron u Oracle Scheduler? | Con un programador propio la app sabe qué estrategia, qué script aprobado y qué ejecución van juntos, y puede validar justo antes de ejecutar. |
| ¿Por qué no borran respaldos viejos con la retención? | `DELETE OBSOLETE` borra respaldos de toda la base, no solo de una estrategia. Preferimos no generar comandos que borren; la retención queda registrada para el administrador. |
| ¿Qué pasa si cambio la estrategia después de aprobar? | Si cambia la base, el QUÉ, el CÓMO o el destino, el script queda invalidado. Cambiar solo el CUÁNDO no lo invalida, porque el texto del script no cambia. |
| ¿Cómo distinguen un diferencial de un acumulativo si RMAN los muestra igual? | La app guarda el comando exacto de cada ejecución. |
| ¿Hay usuarios y contraseñas? | No; es un prototipo. El nombre del administrador se registra, pero no se verifica. Está documentado como limitación. |
| ¿Por qué el destino está en el mismo disco? | Es el ambiente de pruebas. La app lo marca como recomendación: en un ambiente real debe ir en otro dispositivo. |
| ¿Y la recuperación? | La verificación con `RESTORE VALIDATE` comprueba que se podría restaurar. Las operaciones de recuperación reales no se ejecutan desde la app, por seguridad. |
