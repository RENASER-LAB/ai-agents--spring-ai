# Qué cambia con el documento nuevo

`nuevo_doc_requisitos_funcionales.docx` · «RENASER TALENT INTELLIGENCE · Requisitos
funcionales definitivos · Versión 1.1 FINAL · 2026-08-14»

Este documento compara lo que llegó del cliente contra lo que ya está escrito, y **registra lo
que se decidió** en cada punto.

Hay un análisis anterior del mismo archivo en
[Qué documento manda](ANALISIS-DOCUMENTOS.md), hecho el 14 de agosto sobre el original que
estaba en `Descargas`. Se comprobó que la copia guardada en `insumos/` es **el mismo archivo**
—coinciden las huellas SHA-256— y que ambos análisis llegan a las mismas conclusiones. Aquel
documento fija qué manda sobre qué; este dice qué se hizo al respecto.

Las referencias tipo `RF-034` son del documento del cliente, no de los nuestros. Van con una
frase corta al lado para que sigan sirviendo si el cliente renumera.

---

## Lo primero: cambia qué documento manda

Hasta hoy la regla era que mandaba **Sistema_Completo_Talento_RENASER**, y que
**Sistema_RENASER_Talent_Intelligence** estaba descartado salvo tres cosas.

El documento nuevo **es** Talent Intelligence, se declara «definitivo», dice que reemplaza a
la V1.0 y que debe usarse como fuente funcional para desarrollo. La regla de precedencia que
está escrita en `CLAUDE.MD` quedó al revés y hay que corregirla.

No dice cuál es la «V1.0» que reemplaza. Puede ser el documento de Talent Intelligence
anterior, puede ser Sistema_Completo, o pueden ser nuestros propios documentos —que también
van por «Versión 1.1» y con la misma fecha—. Conviene preguntarlo.

---

## Lo que confirma sin cambios

Buena parte del trabajo hecho sobrevive intacto. Lo que sigue coincide palabra por palabra o
con las mismas cifras:

| Qué | Estado |
|---|---|
| Cinco etapas visibles, sin añadir «entrevista» como etapa aparte | Igual |
| Los cuatro momentos de la prueba: comprende, produce, explica, se adapta | Igual |
| Bancos D01–D90, S01–S60, O01–O50, más el de alineación personal | Igual |
| La guía de 0 a 4 para respuestas abiertas | Idéntica, ancla por ancla |
| Los ocho criterios del CV, con los mismos puntos por nivel | Idénticos |
| Los diez criterios de la prueba del puesto, con los mismos puntos | Idénticos |
| Los diez criterios de la simulación, con los mismos puntos | Idénticos |
| Las nueve métricas de la validación, con los mismos pesos | Idénticas |
| Una versión publicada no se toca; las notas viejas no se recalculan | Igual |
| Sin detectores de IA y sin vigilancia por cámara | Igual |
| No puntuar edad, sexo, embarazo, raza, religión, discapacidad ni salud | Igual |
| Seguimiento a los 30, 90 y 180 días | Igual |
| Auditoría con actor, fecha, valor anterior, valor nuevo y razón | Igual |
| La decisión final siempre la toma una persona | Igual |

---

## Lo que resuelve

Cinco cosas que estaban en la lista de pendientes quedan cerradas por el propio cliente:

**El CV no descarta.** Era la contradicción con la reunión del 8 de agosto. El documento la
zanja: lo único que puede detener una postulación de forma automática son los requisitos
objetivos indispensables configurados de antemano —una licencia legalmente necesaria,
disponibilidad geográfica, un requisito técnico que no se aprende a tiempo— y hay que guardar
la regla exacta que se aplicó (RF-034, «detener por requisitos objetivos»). Todos los demás
pasan al Perfil Integral (RF-035).

**Son once pruebas, no doce.** Quedan nombradas una por una (RF-073, «plantillas iniciales»).

**El plazo de conservación de datos es configuración, no un número en el código.** Y al
vencer, la política decide: borrar, anonimizar o pedir que renueven el consentimiento
(RF-029 y RF-030).

**La figura legal de los siete días ya no bloquea el desarrollo.** El sistema construye la
capacidad configurable; lo que espera al visto bueno legal es solo habilitar la modalidad de
trabajo productivo, y el sistema no la deja activar hasta que la vacante tenga registrado el
tipo de vinculación y la confirmación legal (RF-087). Además la duración deja de ser siete
días fijos (RF-088).

**pgvector encuentra para qué sirve.** Antes de publicar una vacante hay que mostrar los
candidatos del Radar que podrían encajar (RF-016). Eso es búsqueda por parecido.

También aparece «Compatibilidad y permanencia» en el glosario, que era el *Retention Fit* que
estaba sin definir: es una hipótesis sobre si el entorno y el rol pueden sostener la relación,
y el documento aclara que **no** es una predicción automática de renuncia.

---

## Lo que contradice

Veinte puntos donde el documento nuevo dice algo distinto de lo que está escrito. Ordenados
por lo que cuesta arreglarlos.

### Contradicciones que mueven el modelo de datos

| Tema | Lo que está escrito hoy | Lo que dice el documento nuevo |
|---|---|---|
| **Pesos de las etapas** | Cinco pesos que cambian según el nivel: CV 5%, evaluación 20/20/15, prueba 30/30/40, simulación 25/25/20, validación 20% | Cuatro pesos iguales para todos los niveles: Perfil Integral 40%, prueba 30%, simulación 15%, validación 15%. El CV ya no es una etapa con peso: son 10 de los 40 del Perfil Integral (RF-038, RF-092) |
| **Reutilizar respuestas** | Si postula a otro puesto del **mismo nivel**, no vuelve a responder | El mismo nivel **no basta**. Solo se reutilizan componentes vigentes cuando el puesto es de la misma familia o de una declarada afín, y cada componente tiene vigencia (RF-058, RF-059, RF-060) |
| **Roles y permisos** | 53 permisos propios, con alcance de tres valores | Reutilizar el sistema de roles que ya tiene RENASER OS. «No duplicar un sistema de permisos si RENASER OS ya tiene uno» (RF-120, RF-121) |
| **Métricas de los 7 días** | Se cargan a mano | Se alimentan solas desde RENASER OS siempre que el dato exista; la persona solo completa lo que no se puede observar, y hay que ver de dónde salió cada dato (RF-089, RF-090) |
| **Prueba psicométrica** | Se compra a un tercero, vale 30%, y mientras tanto ese 30% se reparte | Es un módulo propio de Renaser, vale 5%, arranca experimental y versionado, y mientras no esté calibrado no puede frenar a nadie por sí solo (RF-039, RF-040) |
| **Fallos graves** | Un catálogo por nivel de puesto | Barreras críticas que define **cada vacante** (RF-094) |
| **Semáforo** | Cuatro valores: verde, ámbar, rojo, sin datos | Cinco: se añade **Reserva / Radar** —no es el mejor para esta vacante pero interesa para otra— |
| **Consentimiento** | Uno solo | Dos separados: el del proceso actual y el de guardar los datos para futuras oportunidades. El segundo no se puede dar por supuesto (RF-025). Y el registro debe guardar además identificador de sesión y huella del documento, y poder exportarse (RF-026) |
| **Preguntas después de producir** | 17 fijas para todo el mundo | Entre 8 y 10 universales más 3 a 5 del puesto, elegidas por la plantilla. Dice textualmente «no fijar 17 preguntas obligatorias para todos» (RF-068). Y añade preguntas **antes** de producir, que hoy no existen (RF-067) |
| **Cambio inesperado** | Un texto y un minuto fijos por prueba | Varias variantes y el momento dentro de un rango, «para que todos no aprendan el patrón» (RF-065) |
| **Tramos de la simulación** | Seis tramos fijos: ejecución 20–85, cambio 85–105, entrega 105–115, conversación 115–120 | Configurables. La plantilla recomendada es otra: ejecución 20–80, cambio 80–100, entrega 100–105, conversación 105–120 (RF-078) |
| **Marcas de tiempo de la simulación** | Se registra «detección de un bloqueo» | Prohibido: solo se registran actos observables. «No registrar el momento en que detectó mentalmente un bloqueo» salvo que el candidato lo declare con una acción (RF-079, RF-080) |

### Contradicciones de comportamiento

| Tema | Lo que está escrito hoy | Lo que dice el documento nuevo |
|---|---|---|
| **Cerrar una vacante** | Cierra también las postulaciones que iban a mitad | Detiene las nuevas postulaciones, pero las activas **no se cierran solas**: el equipo decide continuar, detener o mandar al Radar (RF-011) |
| **Plazo de la evaluación** | No hay plazo para empezar ni para terminar | El creador de la convocatoria fija fecha límite para iniciar y para completar (RF-046) |
| **Duración de la evaluación** | Entre 86 y 126 preguntas, sin objetivo de tiempo | Objetivo por nivel: 40–50 min en Dirección, 35–45 en Supervisión, 25–35 en Ejecución. Si la configuración pasa de 60 minutos, el sistema avisa antes de publicar (RF-044) |
| **Bar Raiser** | No puede bloquear una contratación | Se llama **Evaluador de Estándar RENASER** y su poder de aprobación o bloqueo es configurable por organización. En la V1 interna puede quedarse en recomendación registrada (RF-098) |
| **Métrica de Dirección** | Una de las cuatro es «qué porcentaje de decisiones tomó la IA sola» | Ese indicador se prohíbe expresamente: la automatización se mide por horas humanas ahorradas, tiempo hasta finalista, calidad de contratación y precisión de la predicción (RF-101) |
| **Nombres de los niveles** | Dirección · Supervisión · Ejecución | Dirección · Coordinación/Supervisión · Operación/Ejecución |
| **Vocabulario del glosario** | «Bar Raiser», «Realistic Job Preview», «Role Fit», «kickoff», «trial» | Todos con nombre español obligatorio en la interfaz: Evaluador de Estándar, Presentación realista del puesto, Adecuación al puesto, Simulación de Trabajo, Validación práctica |
| **Publicación de la vacante** | Se publica y ya | Tres formas: con fecha de cierre, hasta cubrir un número de plazas, o permanente para alimentar el Radar (RF-008) |

---

## Lo que es nuevo

Capacidades que no existen en ningún documento nuestro y que no tienen dónde guardarse.

### Antes de la vacante

**Solicitud de Talento** (RF-001 a RF-007). Toda vacante tiene que colgar de una solicitud.
Puede nacer de dos sitios: alguien autorizado que sabe que necesita contratar, o RENASER OS
que detecta sobrecarga, retrasos o pérdida de calidad. La solicitud responde qué resultado
falta, qué pasa si no se contrata, cuándo se necesita y qué parte podría eliminarse,
automatizarse o redistribuirse. Tiene urgencia —normal, prioritaria o urgente—. Cuando la
detecta el sistema, hay que guardar la evidencia que originó la recomendación.

**Radar de Talento** (RF-013 a RF-016). Una base viva de gente interesante que existe **sin
vacante**: candidatos de procesos anteriores que aceptaron futuras oportunidades, referidos,
prospectos añadidos a mano y los que llegan por convocatorias permanentes. De cada uno se
guardan capacidades, evidencia, fuente, familias compatibles, nivel estimado, disponibilidad,
historial de contacto, última evaluación vigente y su consentimiento.

**Familias de trabajo** (§2.2). Siete: Dirección/Negocio, Operaciones/Control,
Crecimiento/Ventas, Tecnología/Producto, Creativo/Experiencia, Talento/Personas y Seguridad
crítica. Hoy solo existe el nivel. La familia es lo que decide qué preguntas se seleccionan y
si una evaluación se puede reutilizar.

### Multiempresa

**Toda entidad principal lleva organización** (RF-129 a RF-132): vacantes, candidatos,
evaluaciones, pruebas, decisiones, plantillas, consentimiento, agentes y métricas. La interfaz
de la V1 puede quedarse en Renaser, pero el modelo de datos no puede asumir una sola empresa.
El aislamiento por organización es regla de seguridad desde la primera versión. Los bancos de
Renaser son una biblioteca global, y cada organización puede tener sus propias plantillas sin
tocar el original.

### Dentro de la evaluación

**Plantilla de evaluación por vacante** (RF-043). Cada vacante genera su propia versión con
preguntas elegidas por nivel, familia, capacidades críticas y preguntas de verificación
sacadas del CV. El banco es un repositorio, no un cuestionario que se aplica entero.

**Requisitos objetivos indispensables** (RF-034). Configurables por vacante, y son lo único
que puede detener una postulación solo.

**Repreguntas del agente** (RF-049). Si una respuesta abierta es superficial, el agente puede
repreguntar de forma limitada para sacar número, contexto, acción propia y resultado.

### En la salida

**Perfil de Talento** (RF-053, RF-096). No es una nota: es adecuación al puesto, potencial,
alto rendimiento, fortalezas, riesgos, talento canalizable, evidencia que falta y confianza
de la evidencia.

**Riesgos con tipo** (RF-055). Crítico, desarrollable, preferencia o estilo, y falta de
evidencia. Prohibido mezclarlos.

**Sugerir otro puesto** (RF-054). Cuando hay fortalezas claras pero mal encaje, el sistema lo
propone. No mueve nada sin que una persona lo haga.

**Grupos de prioridad y decisión por lote** (RF-056, RF-057, RF-099, RF-100). Todos los
candidatos se procesan y se ordenan en cuatro grupos: alta prioridad, alto potencial con
riesgo, no priorizados e incompatibilidad objetiva. Los no priorizados se pueden confirmar en
bloque, y aun así cada uno conserva su razón individual.

### En la simulación y después

**Matriz de información crítica** (RF-081). Cada simulación declara qué preguntas debería
hacer un candidato fuerte, qué datos son opcionales y cuáles hay que descubrir. Sirve para
evaluar la calidad de las preguntas sin tener que adivinar lo que no preguntó.

**Método de verificación por criterio** (RF-070, RF-071). Cada criterio de una rúbrica declara
cómo se comprueba: lo mide el sistema, lo califica un agente, o lo revisa una persona.

**Módulo de desempeño** (RF-104, RF-105). Para cada contratado, con resultado esperado y
porcentaje logrado, y un diagnóstico corto cuando hay desviación: qué obstáculo hubo y si la
causa fue claridad, capacidad, proceso, dependencia, decisión o algo externo.

### Los agentes

**Son nueve, no cinco.** Se añaden el de Necesidad de Talento, el Cazatalentos, el de
Desempeño y el de Aprendizaje. Cada ejecución debe guardar además organización, identificador
del agente, **versión del agente**, objetivo y **confianza** (RF-124) — cuatro datos que hoy
no se guardan.

---

## Qué le pasa al modelo de datos

Estimación, no cuenta cerrada. Depende de las decisiones de abajo.

| Bloque | Tablas nuevas |
|---|---|
| Organización | 1, más una columna en unas 40 tablas |
| Solicitud de Talento | 3 |
| Radar y prospectos | 4 |
| Familias y familias afines | 2 |
| Plantilla de evaluación por vacante | 2 |
| Requisitos objetivos de la vacante | 1 |
| Repreguntas | 1 |
| Perfil de Talento, riesgos y sugerencias | 3 |
| Preguntas de la prueba: previas, universales y del puesto | 3 (reemplazan a una) |
| Variantes del cambio inesperado | 1 |
| Matriz de información crítica | 1 |
| Política de conservación | 1 |
| Desempeño y su diagnóstico | 2 |
| Catálogo de agentes con versión | 1 |

**De 71 tablas a unas 95.** Y hay cambios de columna en muchas de las que ya existen: la clave
de los pesos, el catálogo de dimensiones, el registro de consentimiento, la ejecución de los
agentes, las marcas de tiempo de la simulación.

La buena noticia es que **la base está vacía**. Todavía no hay una sola migración escrita, así
que todo esto es reescribir dos documentos, no migrar datos reales.

---

---

## Decisiones tomadas · 15 de agosto de 2026

### Qué manda

El documento nuevo gana en todo lo que define. Donde calla, sigue vigente lo que ya estaba
escrito, **marcado como propuesta nuestra** para que Renaser lo confirme. Nada verificado se
tira a la basura por silencio.

### RENASER OS

Es un proyecto avanzado y ya tiene frontend. Nuestro módulo va dentro, y ese frontend llama a
nuestra API. Son **dos servicios separados que hablan por HTTP**, no una base compartida.

| Qué | Cómo queda |
|---|---|
| Entrada del equipo de Renaser | RENASER OS emite el token; nosotros solo lo validamos. No guardamos su contraseña |
| Entrada de los candidatos | Cuenta propia en nuestra base. RENASER OS no tiene candidatos |
| Permisos del módulo | Nuestros. RENASER OS dice quién eres; nosotros decidimos qué puedes hacer aquí. Se quedan las cuatro tablas y los 53 permisos |
| Archivos y entregables | Almacén propio, con enlaces firmados que duran poco |
| Tareas, tiempos, bloqueos y retrabajo | Se leen de su API. Alimentan solas las métricas de la validación |
| Objetivos y resultados del personal | Se leen de su API. Es la fuente del seguimiento a 30, 90 y 180 días |

Como no hay base compartida, el identificador de RENASER OS se guarda como **columna suelta,
sin clave foránea**, y hay que decidir qué hace el sistema cuando su API no responde.

Esto deja de estar marcado como «módulo que hoy no existe»: existe.

### Las demás decisiones

| Tema | Decisión |
|---|---|
| **Priorización** | Ganan los grupos de prioridad del documento nuevo. **Muere la zona dudosa** y con ella los tres estados de revisión. El grupo pasa a ser una columna de la postulación, y se puede confirmar por lote |
| **Multiempresa** | Se añade ahora y de forma selectiva: la columna va en lo operativo, los catálogos y el banco de Renaser quedan globales. El correo pasa a ser único por organización |
| **Solicitud de Talento** | Entra en la primera versión. Toda vacante cuelga de una |
| **Radar de Talento** | Se modela ahora, se construye después. Es un producto aparte y necesita búsqueda por parecido |
| **Pesos** | Una versión de pesos por defecto con 40/30/15/15, y cada vacante puede apuntar a otra versión aprobada. **El nivel sale de la clave** de la tabla de pesos |
| **Psicométrica** | 5% propio, hueco reservado y vacío. Mientras no exista, ese 5% se reparte. No puede frenar a nadie |
| **Estados** | Se reducen y se rehacen. Ver abajo |
| **Orden del trabajo** | Primero los requisitos, los estados y los roles. El modelo y el diccionario después, porque salen de ellos |

---

## Los estados, rehechos

Hoy son 25 y crecieron por acumulación. Con los cambios de arriba **bajan a 18** y, sobre
todo, pasan a tener una forma que se puede programar con un solo bucle.

### La forma

Cada etapa se recorre con los mismos cuatro momentos, siempre en el mismo orden:

| Momento | Espera a | Qué significa |
|---|---|---|
| `POR_HABILITAR` | 🟡 Renaser | Alguien tiene que abrir la puerta antes de que el candidato entre |
| `TURNO_CANDIDATO` | 🔵 Candidato | Le toca a él |
| `CALIFICANDO` | ⚙️ Máquina | La IA está trabajando |
| `POR_CONFIRMAR` | 🟡🟢 Renaser | Le toca a una persona decidir si avanza |

No todas las etapas usan los cuatro. La tabla de estados guarda **etapa** y **momento** como
columnas, así que «cuál es el siguiente estado» se calcula, no se busca en una tabla de
transiciones.

| Etapa | POR_HABILITAR | TURNO_CANDIDATO | CALIFICANDO | POR_CONFIRMAR |
|---|:--:|:--:|:--:|:--:|
| Perfil Integral | — | ● | ● | ● |
| Prueba del puesto | — | ● | ● | ● |
| Simulación | ● | ● | — | ● |
| Validación práctica | ● | ● | — | ● |
| Decisión | — | ● | — | ● |

La simulación y la validación no tienen `CALIFICANDO` porque las califica una persona. La
decisión usa `TURNO_CANDIDATO` para el caso del ámbar, cuando se le pide evidencia adicional.

### Los 18

| Estado | Espera a | Qué pasó |
|---|:--:|---|
| `POSTULADA` | ⚙️ | Postuló. El sistema comprueba los requisitos objetivos indispensables |
| `PERFIL_TURNO_CANDIDATO` | 🔵 | Debe aceptar el proceso y responder la evaluación. Con fecha límite |
| `PERFIL_CALIFICANDO` | ⚙️ | La IA puntúa el CV y las respuestas, y arma el Perfil Integral |
| `PERFIL_POR_CONFIRMAR` | 🟡 | Ya tiene grupo de prioridad. Una persona confirma, sola o por lote |
| `PRUEBA_TURNO_CANDIDATO` | 🔵 | Habilitada. Cuando entra, el cronómetro corre |
| `PRUEBA_CALIFICANDO` | ⚙️ | Entregó. La IA califica el entregable |
| `PRUEBA_POR_CONFIRMAR` | 🟡 | Una persona confirma si avanza |
| `SIMULACION_POR_HABILITAR` | 🟡 | No hay sesión con cupo para su vacante, o faltó a la suya |
| `SIMULACION_TURNO_CANDIDATO` | 🔵 | Elige fecha, espera el día y trabaja en la sesión |
| `SIMULACION_POR_CONFIRMAR` | 🟡 | Pasó la sesión. Falta calificarla y hacer la conversación final |
| `VALIDACION_POR_HABILITAR` | 🟡 | Falta el tipo de vinculación, el responsable y el visto bueno legal |
| `VALIDACION_TURNO_CANDIDATO` | 🔵 | Está trabajando los días configurados |
| `VALIDACION_POR_CONFIRMAR` | 🟢 | Terminó. Faltan las métricas que no se alimentaron solas |
| `DECISION_TURNO_CANDIDATO` | 🔵 | Salió ámbar. Se le pidió evidencia adicional |
| `DECISION_POR_CONFIRMAR` | 🟢 | Todo evaluado. Falta la decisión final |
| `CONTRATADO` | ⬛ | Se le contrató |
| `NO_CONTINUA` | ⬛ | No sigue en esta vacante. Con motivo, incluido «pasa a reserva» |
| `CERRADA` | ⬛ | Terminó sin llegar a una decisión de fondo. Con motivo |

### Qué desaparece y por qué

| Estado de hoy | Qué pasa con él |
|---|---|
| `RECIBIDA` | Se llama `POSTULADA` y ahora comprueba los requisitos objetivos, que es lo único que puede detener a alguien solo |
| `CV_CALIFICANDO` | El CV deja de ser etapa. Es 10 de los 40 puntos del Perfil Integral |
| `CV_EN_REVISION`, `EVALUACION_EN_REVISION`, `PRUEBA_EN_REVISION` | Mueren con la zona dudosa. Los reemplaza el grupo de prioridad |
| `EVALUACION_PENDIENTE` y `EVALUACION_EN_CURSO` | Se funden en uno. Que haya empezado lo dice `evaluacion.iniciada_en`, no un estado |
| `PRUEBA_PENDIENTE` y `PRUEBA_EN_CURSO` | Igual: el cronómetro vive en el intento, no en el estado |
| `SIMULACION_POR_CONFIRMAR`, `SIMULACION_AGENDADA`, `SIMULACION_EN_CURSO` | Los tres son «le toca al candidato». Se funden en uno |
| `SIMULACION_AUSENTE` | Deja de ser estado. Vuelve a `SIMULACION_POR_HABILITAR` y la inscripción guarda que no asistió |
| `PRUEBA_ADICIONAL` | Pasa a ser `DECISION_TURNO_CANDIDATO`, que es lo que realmente es |

**La regla nueva:** el estado dice *de quién se está esperando algo*. Lo demás —si ya empezó,
cuánto le queda, si asistió— vive en la tabla de esa etapa, con su fecha. Antes esas dos cosas
estaban mezcladas, y por eso había 25.

---

## Documentos relacionados

- [Requisitos funcionales](../01-REQUISITOS-FUNCIONALES.md) — lo que hay que revisar entero
- [Estados de la postulación](../03-ESTADOS-POSTULACION.md) — el documento nuevo no los enumera
- [Roles y permisos](../04-ROLES-Y-PERMISOS.md) — en duda si sobrevive
- [Modelo de datos](../05-MODELO-DE-DATOS.md) — las 71 tablas de hoy
- [Diccionario de datos](../07-DICCIONARIO-DE-DATOS.md) — columna por columna
- [Qué documento manda](ANALISIS-DOCUMENTOS.md) — la regla de precedencia que hay que corregir
