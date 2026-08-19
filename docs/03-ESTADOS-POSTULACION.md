# Estados de la postulación

Sistema de selección de personal — Renaser Consulting
Versión 2.0 · 2026-08-15

Este documento define **todos** los estados por los que pasa una postulación y cómo se pasa
de uno a otro. Es la base del backend: ningún estado que no esté aquí puede existir.

> ⚠️ **Esta parte la propusimos nosotros, no Renaser.** Se revisaron los cuatro documentos que
> mandó el cliente y ninguno enumera estados: dan las cinco etapas y sus metas de
> automatización, nada más. Todo lo de este documento —los estados, los motivos de cierre, el
> tope de pruebas adicionales, el cierre por inactividad y el botón de retirarse— es una
> propuesta de ingeniería para poder programarlo. Renaser puede confirmarla o cambiarla.

## El contexto en cuatro líneas

Un candidato postula en el portal de Renaser y atraviesa cinco etapas: se junta su Perfil
Integral —currículum, módulo psicométrico y evaluación, interpretados juntos—, hace una prueba
del puesto cronometrada, asiste a una simulación de trabajo y trabaja un periodo de validación
práctica. Al final, una persona decide.

El detalle está en [Requisitos funcionales](01-REQUISITOS-FUNCIONALES.md).

---

## La idea que organiza todo

Cada estado responde una sola pregunta: **¿de quién se está esperando algo?**

| Espera a | Qué significa |
|---|---|
| 🔵 **Candidato** | La pelota está en su cancha. El sistema no hace nada |
| ⚙️ **Sistema** | La IA está calificando. Nadie tiene que intervenir |
| 🟡 **Equipo de Talento** | Está frenado esperando a quien lleva el proceso |
| 🟢 **Responsable del área** | Está frenado esperando al responsable del puesto |
| ⬛ **Nadie** | La postulación terminó |

Esto no es teoría: la pantalla de inicio del equipo (ver «Panel de gestión» en los requisitos
funcionales) muestra exactamente todo lo que está en 🟡 y 🟢. Si un estado no dice a quién
espera, no sirve.

**Y una segunda regla, igual de importante:** el estado dice *de quién se espera algo*, y nada
más. Si ya empezó, cuánto tiempo le queda, si asistió a la sesión — eso vive en la tabla de esa
etapa, con su fecha. Antes las dos cosas estaban mezcladas y por eso hacían falta veinticinco
estados. Ahora son dieciocho.

---

## La forma: cinco etapas, cuatro momentos

Cada etapa se recorre con los mismos cuatro momentos, siempre en el mismo orden:

| Momento | Espera a | Qué significa |
|---|---|---|
| `POR_HABILITAR` | 🟡 | Alguien de Renaser tiene que abrir la puerta antes de que el candidato entre |
| `TURNO_CANDIDATO` | 🔵 | Le toca a él |
| `CALIFICANDO` | ⚙️ | La IA está trabajando |
| `POR_CONFIRMAR` | 🟡 🟢 | Le toca a una persona decidir si avanza |

No todas las etapas usan los cuatro:

| Etapa | POR_HABILITAR | TURNO_CANDIDATO | CALIFICANDO | POR_CONFIRMAR |
|---|:--:|:--:|:--:|:--:|
| Perfil Integral | — | ● | ● | ● |
| Prueba del puesto | — | ● | ● | ● |
| Simulación de trabajo | ● | ● | — | ● |
| Validación práctica | ● | ● | — | ● |
| Decisión | — | ● | — | ● |

La simulación y la validación no tienen `CALIFICANDO` porque las califica una persona, nunca la
máquina. La decisión usa `TURNO_CANDIDATO` para un solo caso: cuando sale ámbar y se le pide
evidencia adicional.

**Por qué importa esta forma.** El nombre de cada estado es `ETAPA_MOMENTO`, y la tabla de
estados guarda la etapa y el momento como columnas aparte. Así, «cuál es el siguiente estado»
se **calcula** —el momento siguiente de esta etapa, o el primer momento de la siguiente— en vez
de buscarse en una tabla de transiciones que hay que mantener a mano.

---

## Los 18 estados

### Entrada

| Estado | Espera a | Qué pasó |
|---|:--:|---|
| `POSTULADA` | ⚙️ | Postuló y subió su currículum. El sistema comprueba los requisitos objetivos indispensables |

Es el único momento en que el sistema puede detener a alguien solo, y únicamente por un
requisito configurado de antemano: una licencia legalmente necesaria, disponibilidad geográfica
indispensable, un requisito técnico que no se aprende a tiempo. Se guarda la regla exacta que
se aplicó. **El currículum no descarta a nadie.**

### Etapa 1 y 2 — Perfil Integral de Preselección

| Estado | Espera a | Qué pasó |
|---|:--:|---|
| `PERFIL_TURNO_CANDIDATO` | 🔵 | Debe ver la ficha real del puesto, aceptar el proceso y responder la evaluación |
| `PERFIL_CALIFICANDO` | ⚙️ | Terminó. La IA puntúa el currículum y las respuestas abiertas, y arma el Perfil Integral |
| `PERFIL_POR_CONFIRMAR` | 🟡 | Ya tiene grupo de prioridad. Una persona confirma si avanza, sola o por lote |

El currículum, el módulo psicométrico y la evaluación son **una sola etapa**, no tres filtros.
Para el candidato es una sola experiencia.

Esta etapa **sí tiene fecha límite**: quien crea la convocatoria fija hasta cuándo se puede
empezar y hasta cuándo se puede terminar.

### Etapa 3 — Prueba del puesto

| Estado | Espera a | Qué pasó |
|---|:--:|---|
| `PRUEBA_TURNO_CANDIDATO` | 🔵 | Está habilitada. Cuando entra y confirma, **el cronómetro corre** |
| `PRUEBA_CALIFICANDO` | ⚙️ | Entregó. La IA califica el entregable y su explicación |
| `PRUEBA_POR_CONFIRMAR` | 🟡 | Una persona confirma si avanza |

Que el cronómetro esté corriendo no es un estado: el intento guarda cuándo empezó y cuándo
vence. Un candidato que aún no ha entrado y otro con el reloj corriendo están en el mismo
estado, y la diferencia la dice el intento.

### Etapa 4 — Simulación de trabajo

| Estado | Espera a | Qué pasó |
|---|:--:|---|
| `SIMULACION_POR_HABILITAR` | 🟡 | Aprobó, pero no hay ninguna sesión con cupo para su vacante. O faltó a la suya |
| `SIMULACION_TURNO_CANDIDATO` | 🔵 | Elige fecha, espera el día y trabaja en la sesión |
| `SIMULACION_POR_CONFIRMAR` | 🟡 | Pasó la sesión. Falta calificarla y hacer la conversación final |

Los tres momentos que antes eran estados —elegir fecha, esperar el día, estar en la sesión— son
ahora uno solo, porque en los tres se está esperando al candidato. Cuál de los tres es se sabe
mirando su inscripción y la fecha de la sesión, y la bandeja del equipo los distingue para
mandar el aviso que toca.

Faltar a la sesión tampoco es un estado: la inscripción guarda que no asistió y la postulación
vuelve a `SIMULACION_POR_HABILITAR`, donde el equipo decide darle otra fecha o cerrarla.

⚠️ **Estos dos estados se mueven solos en las dos direcciones**, y hay que programar el
movimiento: no lo dispara nada que haga el candidato.

| Cuándo | Qué pasa |
|---|---|
| Se publica una sesión, o se amplía su cupo | Todas las postulaciones en `SIMULACION_POR_HABILITAR` que sirvan para esa sesión pasan a `SIMULACION_TURNO_CANDIDATO`, y se les avisa |
| Se llena el cupo de la última sesión disponible | Las que estén en `SIMULACION_TURNO_CANDIDATO` **sin inscripción** vuelven a `SIMULACION_POR_HABILITAR` |
| Se cancela una sesión | Los inscritos vuelven a `SIMULACION_TURNO_CANDIDATO`, o a `SIMULACION_POR_HABILITAR` si ya no queda ninguna |

Es el único punto del recorrido donde el estado de una postulación depende de lo que pase en
**otra tabla**, y no de una acción sobre ella misma. Sin esas tres reglas, un candidato se queda
esperando una sesión que ya existe, o eligiendo una que ya no tiene cupo.

### Etapa 5 — Validación práctica

| Estado | Espera a | Qué pasó |
|---|:--:|---|
| `VALIDACION_POR_HABILITAR` | 🟡 | Falta registrar el tipo de vinculación, el responsable y el visto bueno legal |
| `VALIDACION_TURNO_CANDIDATO` | 🔵 | Está trabajando los días configurados para esa vacante |
| `VALIDACION_POR_CONFIRMAR` | 🟢 | Terminó. Faltan las métricas que no se alimentaron solas |

La duración ya no son siete días fijos: se configura por vacante.

El sistema **no habilita la modalidad de trabajo productivo** hasta que la vacante tenga
registrada la figura contractual. La otra modalidad —una simulación extendida, sin trabajo
productivo— no necesita eso y se puede usar desde el primer día.

Mientras el periodo corre, las métricas que RENASER OS ya conoce —tareas, tiempos, bloqueos,
retrabajo— se alimentan solas. La persona responsable solo completa lo que no se puede observar
con datos.

### Decisión

| Estado | Espera a | Qué pasó |
|---|:--:|---|
| `DECISION_TURNO_CANDIDATO` | 🔵 | Salió **ámbar**. Se le pidió evidencia adicional |
| `DECISION_POR_CONFIRMAR` | 🟢 | Todo evaluado. Falta la decisión final |

### Finales

| Estado | Espera a | Qué pasó |
|---|:--:|---|
| `CONTRATADO` | ⬛ | Se le contrató |
| `NO_CONTINUA` | ⬛ | No sigue en esta vacante |
| `CERRADA` | ⬛ | Terminó sin llegar a una decisión de fondo |

---

## Por qué solo dos estados finales de cierre

`NO_CONTINUA` y `CERRADA` parecen iguales pero no lo son, y hay que separarlos por dos razones:

**El candidato recibe mensajes distintos.** A quien no pasó se le agradece la participación
(ver «Avisos»). A quien se cerró la postulación por otra causa se le explica eso. Decirle a
alguien que "no pasó la evaluación" cuando en realidad se retiró es un error feo.

**Las métricas necesitan distinguirlos.** El embudo por vacante (ver «Panel de gestión») mide
dónde se cae la gente. Si mezclas al descartado con el que se retiró, el embudo miente.

Cada uno guarda además su **motivo**:

| Estado | Motivos posibles |
|---|---|
| `NO_CONTINUA` | Requisito objetivo no cumplido · Barrera crítica confirmada · Decisión roja · Decisión de una persona · **Pasa a reserva** |
| `CERRADA` | Sin avanzar 60 días (configurable) · Cierre manual · **El candidato se retiró** · Se acabó el plazo de la evaluación · Pidió borrar sus datos |

Así son 2 estados con motivo, en vez de 10 estados.

**«Pasa a reserva» es un motivo, no un estado.** Significa que la persona no era la mejor para
*esta* vacante pero interesa para otra. La postulación termina igual; lo que sigue vivo es la
persona, que entra al Radar de Talento si dio su consentimiento para futuros contactos.

---

## El recorrido normal

```
   POSTULADA
      |   falla un requisito objetivo ---> NO_CONTINUA
      v
   PERFIL_TURNO_CANDIDATO
      |   acepta el proceso y responde
      v
   PERFIL_CALIFICANDO
      |   la IA arma el Perfil Integral y asigna grupo
      v
   PERFIL_POR_CONFIRMAR  ----- no priorizado ----> NO_CONTINUA
      |                        (se confirma por lote)
      v
   PRUEBA_TURNO_CANDIDATO   <-- cronometro al entrar
      |
      v
   PRUEBA_CALIFICANDO
      |
      v
   PRUEBA_POR_CONFIRMAR  ---------------------> NO_CONTINUA
      |
      v
   SIMULACION_POR_HABILITAR  <----- falto a la sesion ----+
      |   hay sesion con cupo                             |
      v                                                   |
   SIMULACION_TURNO_CANDIDATO -------------------------- +
      |   asistio y termino
      v
   SIMULACION_POR_CONFIRMAR  ------------------> NO_CONTINUA
      |
      v
   VALIDACION_POR_HABILITAR
      |   figura contractual registrada
      v
   VALIDACION_TURNO_CANDIDATO
      |
      v
   VALIDACION_POR_CONFIRMAR
      |
      v
   DECISION_POR_CONFIRMAR
      |
      +---- verde ------> CONTRATADO
      |
      +---- ambar ------> DECISION_TURNO_CANDIDATO --+
      |                                              |
      |    <-----------------------------------------+
      |
      +---- rojo -------> NO_CONTINUA
      |
      +---- reserva ----> NO_CONTINUA (motivo: pasa a reserva)
```

Desde **cualquier** estado que no sea final se puede llegar a `CERRADA`.

---

## Las tres reglas que gobiernan las transiciones

### Regla 1 · Nadie se descarta solo, salvo por un requisito objetivo

Lo único que puede cerrar una postulación sin que intervenga una persona es un requisito
objetivo indispensable, configurado antes de publicar la vacante y guardado con la regla exacta
que se aplicó.

Todo lo demás se **ordena**, no se descarta. Cuando la IA termina de calificar una etapa,
coloca a cada candidato en uno de cuatro grupos:

| Grupo | Qué significa |
|---|---|
| **Alta prioridad** | Evidencia consistente. Se revisa primero |
| **Alto potencial con riesgo** | Vale la pena, pero hay una contradicción que mirar |
| **No priorizados** | No destaca frente a los demás. Con su razón explicada |
| **Incompatibilidad objetiva** | Falla algo indispensable del puesto |

El equipo revisa primero los dos primeros grupos. A los no priorizados los puede confirmar **en
bloque**, sin abrir uno por uno, y aun así cada uno conserva su razón individual y su
trazabilidad.

Esto reemplaza a la «zona dudosa» que estos documentos usaban antes. Aquella miraba solo a
quienes caían cerca de una nota mínima; esta ordena a todos. La diferencia práctica es que
nadie se cae sin que quede escrito por qué.

⚠️ **Una posible barrera crítica nunca cierra una postulación sola.** La máquina la detecta y
explica en qué se basa; una persona autorizada la confirma antes de que se convierta en una
decisión negativa.

### Regla 2 · Una persona puede mover una postulación a donde quiera

Cualquier persona con permiso puede cambiar una decisión del sistema, en cualquier dirección.
Eso significa que **todas** las transiciones existen manualmente, incluso las que el sistema
nunca haría solo: devolver a alguien ya descartado, saltarse una etapa, reabrir una postulación
cerrada.

Toda transición manual guarda quién, cuándo, de qué estado a cuál y por qué (ver «Auditoría» en
los no funcionales). El motivo es **obligatorio**.

### Regla 3 · Los estados en ⚙️ tienen que avanzar solos

`PERFIL_CALIFICANDO` y `PRUEBA_CALIFICANDO` dependen de que la IA responda. Si la IA falla, la
postulación **no se mueve y no se inventa una nota** (ver «Inteligencia artificial» en los no
funcionales).

Por eso:
- Se reintenta automáticamente.
- Si lleva demasiado tiempo ahí, se avisa al equipo.
- Nunca se guarda un cero por un fallo técnico.

⚠️ Sin esta regla, un problema de la IA se convierte en un candidato descartado en silencio.

---

## Casos especiales

### El cronómetro se agota

Si vence el tiempo del intento, el sistema **entrega solo** lo que haya y pasa a
`PRUEBA_CALIFICANDO`, marcando que fue por tiempo agotado.

No hay estado de "no terminó a tiempo": entregar tarde no existe, se entrega lo que hay. El
tiempo lo controla el servidor, así que cerrar el navegador no lo detiene (ver «Cronómetros» en
los no funcionales).

El intento guarda su propia fecha de vencimiento, calculada al empezar. Así el barrido que
busca relojes agotados es una consulta directa y no depende de que la plantilla siga igual.

### El candidato ya respondió las preguntas antes

**Que dos puestos sean del mismo nivel no basta.** Solo se reutilizan los componentes que
siguen vigentes cuando el puesto nuevo es de la misma familia de trabajo o de una declarada
afín. El núcleo común se reutiliza dentro de su periodo de vigencia; las preguntas propias del
puesto se vuelven a generar.

Cuando hay algo que reutilizar, la postulación entra directo a la parte que le falta responder,
y lo reutilizado queda atado a la versión con que se obtuvo.

Tanto la vigencia de cada componente como qué familias son afines entre sí son configurables y
versionadas.

### La postulación lleva mucho tiempo sin moverse

Hay tres formas de cerrarla sola o a mano:

1. Automática, tras **60 días sin actividad** (configurable).
2. Manual, por alguien del equipo con permiso.
3. **El candidato la retira** desde su portal.

En los tres casos termina en `CERRADA` con su motivo. Y hay una cuarta que sí depende del
candidato: si se acaba el plazo para completar la evaluación sin que la haya terminado.

### Se cierra la convocatoria

Cerrar una vacante **detiene las postulaciones nuevas, pero no cierra las que están en marcha**.
El equipo decide, candidato por candidato, si continúa el proceso, lo detiene, o lo manda al
Radar de Talento cuando hay consentimiento válido para futuros contactos.

Esto cambió respecto de versiones anteriores de este documento, donde el cierre de la vacante
arrastraba a todas las postulaciones a la vez.

### El candidato se retira

Desde su portal puede retirar cualquier postulación en curso, con un botón. Pasa a `CERRADA`
con ese motivo y deja de recibir avisos de esa vacante.

Retirarse **no borra sus datos**: sus respuestas y notas se conservan. Para borrarlos hay que
pedirlo aparte (ver «Datos personales» en los no funcionales), que es otra cosa. Y retirar el
consentimiento de futuras oportunidades es una tercera cosa distinta de las dos anteriores.

### Salió ámbar

**Qué es el ámbar.** Es uno de los cinco resultados de la decisión final (ver «La decisión»):

| | Qué significa |
|---|---|
| 🟢 Verde | Evidencia consistente y sin barreras críticas sin resolver. Se contrata |
| 🟡 Ámbar | La persona vale, **pero hay una contradicción o un riesgo** que hay que validar |
| 🔴 Rojo | Hay evidencia confirmada de que falla algo indispensable del puesto |
| ⬜ Sin datos | Falta evidencia. Se pide más, no se asume que falla |
| 🔵 Reserva | No es la mejor para esta vacante, pero interesa para otra |

**Un ejemplo.** Ana saca 86 de 100. En las preguntas dijo que avisa los riesgos temprano. Pero
en la simulación detectó un bloqueo a las 10:41 y lo comunicó a las 10:49: ocho minutos callada.
Su nota es buena, pero **lo que dijo y lo que hizo no coinciden**.

No se la puede descartar por eso, ni contratar sin saber qué pasó. Sale ámbar.

**Qué hace el sistema.** No descarta ni contrata. Pide una evidencia dirigida a esa duda
concreta y la postulación pasa a `DECISION_TURNO_CANDIDATO`. Cuando la entrega, vuelve a
`DECISION_POR_CONFIRMAR` con la evidencia nueva.

**Cuántas veces puede repetirse.** Puede salir ámbar otra vez, y otra. Por eso hay un tope:
**máximo 2 rondas de evidencia adicional**, configurable. Al llegar al tope, el sistema ya no
permite otra y obliga a decidir con lo que hay.

Sin ese tope, una postulación puede quedar dando vueltas para siempre.

### El candidato pide borrar sus datos

Los requisitos no funcionales le dan ese derecho en «Datos personales». La postulación pasa a
`CERRADA` con ese motivo, se borran sus datos personales y sus respuestas de texto libre, pero
**el registro de auditoría se conserva** sin datos que identifiquen a la persona.

Así se cumple la ley sin destruir la trazabilidad de las decisiones que ya se tomaron.

---

## Decisiones tomadas sobre estos casos

Ninguna viene de Renaser. Las cuatro son propuesta nuestra y se pueden cambiar.

| Qué | Decisión |
|---|---|
| Si alguien no va a la simulación | **Lo decide el equipo.** Vuelve a `SIMULACION_POR_HABILITAR` con las dos opciones: otra fecha o cerrar |
| Cuánto tiempo sin avanzar antes de cerrar sola | **Configurable**, con 60 días por defecto |
| Si el candidato puede retirarse solo | **Sí.** Tiene un botón en su portal |
| Cuántas veces puede repetirse el ámbar | **Máximo 2** rondas de evidencia adicional, configurable |

---

## Cómo se guarda esto

**Una postulación tiene un solo estado a la vez.** Nunca dos.

La tabla de estados es un catálogo cerrado que solo cambia con una migración: no hay pantalla
para inventar un estado nuevo. Cada fila guarda su **etapa**, su **momento** y **a quién
espera**, que es lo que permite calcular el siguiente estado y armar la bandeja de trabajo.

Cada cambio de estado se guarda como un registro aparte que nunca se modifica ni se borra (ver
«Auditoría» en los no funcionales), con: postulación, estado anterior, estado nuevo, quién lo
hizo —una persona o el sistema—, cuándo, y por qué.

Eso permite tres cosas que los requisitos piden:
- Reconstruir el recorrido completo de cualquier candidato.
- Calcular cuánto tardó cada etapa, para las métricas de Dirección (ver «Panel de gestión»).
- Medir cuántas horas de trabajo humano se ahorraron y cuánto se tarda en llegar a un finalista
  fuerte.

---

# Documentos relacionados

| Documento | Qué contiene |
|---|---|
| [Qué hace el sistema](00-QUE-HACE-EL-SISTEMA.md) | El sistema entero, sin nada técnico. **Empieza por aquí** |
| [Requisitos funcionales](01-REQUISITOS-FUNCIONALES.md) | Qué hace el sistema, etapa por etapa |
| [Requisitos no funcionales](02-REQUISITOS-NO-FUNCIONALES.md) | Tecnología, seguridad, rendimiento |
| [Roles y permisos](04-ROLES-Y-PERMISOS.md) | Quién puede hacer qué, acción por acción |
| [Modelo de datos](05-MODELO-DE-DATOS.md) | Las tablas por área y por qué existe cada una |
| [Estados, en un dibujo](diagramas/estados-postulacion.html) | La rejilla de cinco etapas por cuatro momentos |
| [Alcance del MVP](08-ALCANCE-DEL-MVP.md) | Qué se construye primero, en qué orden y por qué |
