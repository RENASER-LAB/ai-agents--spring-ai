# Requisitos funcionales

Sistema de selección de personal — Renaser Consulting
Versión 2.0 · 2026-08-15

> Este documento se lee solo. No hace falta abrir ningún otro para entenderlo.
> Los detalles largos están en documentos aparte y se enlazan donde corresponde.
>
> Los números RF de aquí son de este documento. El documento del cliente tiene su propia
> numeración y **no se corresponden**.

---

## Para qué existe este sistema

Renaser quiere contratar **por evidencia de lo que la persona puede hacer**, no por lo que dice
su currículum. Hoy esa evidencia se junta a mano y tarde: se descubre cómo trabaja alguien
recién cuando ya está contratado.

El sistema adelanta esa evidencia y la ordena. Antes de decidir, Renaser ya vio a la persona
resolver un problema real del puesto, adaptarse a un cambio inesperado y sostener un periodo de
trabajo.

La pregunta que el sistema ayuda a responder es siempre la misma:

> *¿Puedo confiarle a esta persona un resultado, un equipo o una parte de Renaser y encontrarla
> mejor de como se la entregué, sin tener que perseguirla ni descubrir tarde sus problemas?*

Cuatro ideas guían todo el diseño:

**No se selecciona por antigüedad.** Diez años repitiendo el mismo trabajo pesa menos que dos
años con resultados demostrables. El currículum aporta evidencia, no decide.

**El trabajo real manda sobre el examen.** Si alguien saca 95 en la evaluación pero no produce
en la prueba ni en la validación, gana lo que hizo, no lo que respondió.

**La máquina ordena, la persona decide.** La inteligencia artificial procesa a todos, los
prioriza y explica por qué, para que el tiempo de las personas se gaste donde de verdad hace
falta. Ninguna contratación ni ningún rechazo definitivo salen de una máquina.

**Un riesgo no es un descarte.** El sistema tiene que decir si el talento se puede canalizar,
desarrollar o encajar mejor en otro puesto, en vez de limitarse a un sí o un no.

---

## Qué es este sistema

Un módulo de RENASER OS que acompaña a un candidato desde que ve una vacante hasta que es
contratado, y que después comprueba si la persona rindió como el sistema predijo.

Tiene **dos caras**:

| Cara | Quién la usa | Dónde vive |
|---|---|---|
| Portal de Talento | Candidatos y prospectos invitados | Portal público de Renaser |
| Panel de Talento | Equipo de Talento, responsables de área y Dirección | Dentro de RENASER OS |

Renaser publica **solo sus propias vacantes**. No es una bolsa de trabajo para terceros.

**Va dentro de RENASER OS.** El frontend ya existe y llama a este backend por su API. El equipo
de Renaser entra con la cuenta que ya tiene; los candidatos, que no son usuarios de RENASER OS,
tienen cuenta en este sistema.

---

## Alcance

### Qué entra

| | |
|---|---|
| Solicitud de Talento | Registrar por qué hace falta contratar, antes de que exista la vacante |
| Publicar vacantes | Crear, editar, publicar y cerrar, con tres formas de cierre |
| Portal público | Ver vacantes, crear cuenta, postular, subir currículum |
| Evaluación completa | Las cinco etapas, del Perfil Integral a la validación práctica |
| Calificación con IA | Currículum, respuestas abiertas y entregables, con explicación |
| Agenda de la simulación | Fechas, cupos, confirmación y asistencia |
| Decisión | Semáforo, barreras críticas, ajustes de una persona |
| Seguimiento | Cómo rindió a los 30, 90 y 180 días |
| Panel y métricas | Embudo, tiempos, aciertos de la predicción |
| Administración | Preguntas, pruebas, pesos, roles, textos de correo |

### Qué no entra en la primera versión

| | Por qué |
|---|---|
| Radar de Talento | Se deja el modelo listo, pero es un producto aparte con su propio buscador |
| Buscar candidatos en bolsas externas | Renaser quiere su propio portal. El Radar admitirá esas fuentes después |
| Detectar si el candidato usó inteligencia artificial | Usarla no está prohibido; se evalúa si entiende lo que produjo |
| Vigilancia por cámara durante las pruebas | No se pide y es invasivo |
| Nómina, contratos, vacaciones | Es otro módulo de RENASER OS |
| Un modelo de IA entrenado desde cero | El valor está en las reglas, los bancos y la evidencia, no en el modelo |
| Interfaz multiempresa | El modelo de datos sí la soporta; la pantalla se queda en Renaser |

### Lo que el sistema nunca hace

- No puntúa edad, sexo, embarazo, raza, religión, discapacidad ni orientación sexual.
- No usa la familia, la pareja ni la salud para predecir el desempeño.
- No diagnostica nada psicológico ni infiere salud a partir de foto, voz o respuestas.
- No descarta a nadie por las preguntas de alineación personal.
- No inventa una nota cuando la inteligencia artificial falla: la deja pendiente.
- No inventa un dato que falta. Distingue hecho, declaración, hipótesis y contradicción.

---

## El recorrido completo

```
  ANTES DE LA VACANTE

  alguien la pide          ------>  SOLICITUD DE TALENTO
  o el sistema la detecta            que resultado falta, que pasa
                                     si no se contrata, que se podria
                                     eliminar o automatizar
                                            |
                                            v
                                       VACANTE

  EL CANDIDATO                       EL SISTEMA

  ve la vacante
  crea su cuenta
  postula y sube CV      ------>    revisa los requisitos objetivos
                                    (lo unico que descarta solo)
        |
        v
  acepta el proceso
  responde la
  evaluacion             ------>    corrige lo cerrado
  (con fecha limite)                la IA califica lo abierto
        |                           arma el PERFIL INTEGRAL
        v                           y ordena a todos en 4 grupos
                                            |
                                    una persona confirma
                                    (uno por uno o por lote)
        |
        v
  hace la prueba
  del puesto             ------>    cronometro corriendo
  (los minutos los                  cambio inesperado en un momento
   pone la empresa)                 que no siempre es el mismo
        |
        v
  asiste a la
  simulacion             ------>    registra lo que se puede observar
  (hasta 2 h)                       conversacion humana al final
        |
        v
  validacion practica    ------>    RENASER OS alimenta lo que sabe
  (dias configurables)              el responsable completa el resto
        |
        v
                                    DECISION
                          verde / ambar / rojo / sin datos / reserva
        |
        v
  contratado             ------>    seguimiento a 30, 90 y 180 dias
                                    ¿acerto la prediccion?
```

**Los pesos de la nota final** son iguales para los tres niveles de puesto:

| Etapa | Peso | Qué lo compone |
|---|---|---|
| **Perfil Integral de Preselección** | **40%** | Currículum y evidencia 10 · Módulo psicométrico 5 · Evaluación Renaser 25 |
| **Prueba del puesto** | **30%** | El entregable, su explicación y la adaptación al cambio |
| **Simulación de trabajo** | **15%** | Cómo trabaja dentro de un entorno controlado |
| **Validación práctica** | **15%** | Lo que produce en un periodo de trabajo |

El currículum **ya no es una etapa con peso propio**: son 10 de los 40 puntos del Perfil
Integral, que se lee entero y no como tres filtros sueltos.

Cuando una etapa no aplica a un puesto, la vacante apunta a otra versión de pesos aprobada de
antemano. **Nunca se reparten pesos a mano para un candidato concreto.**

### Las mismas etapas, con otros nombres

Renaser fijó un vocabulario en español y es el que debe aparecer en la interfaz. En
conversación el cliente todavía usa las palabras viejas.

| Como se dice aquí | Como se decía antes | Qué es |
|---|---|---|
| Perfil Integral de Preselección | Filtro de CV + Evaluación Integral | Etapas 1 y 2, juntas |
| Prueba del puesto | Challenge · Work sample | Etapa 3 |
| **Simulación de trabajo** | **Kickoff** | Etapa 4 |
| **Validación práctica** | **Trial** · Los 7 días | Etapa 5 |
| Evaluador de Estándar | **Bar Raiser** | Quien revisa que la urgencia no baje el nivel |
| Presentación realista del puesto | Realistic Job Preview | Lo que se le enseña antes de evaluarlo |
| Adecuación al puesto | Role Fit | Qué tan compatible es su evidencia con el cargo |

⚠️ **La simulación dura hasta 2 horas; la validación dura días.** Es fácil confundirlas porque
el cliente las menciona juntas. Van en este orden: primero la sesión de un solo día, después el
periodo de trabajo.

---

## Dónde trabaja una persona y dónde trabaja la máquina

Esta es la parte que más se pregunta. Hay tres categorías, y conviene no mezclarlas.

### 1 · La máquina sola, siempre

Nadie interviene en esto:

- Comprobar los requisitos objetivos indispensables al postular
- Puntuar el currículum y extraer sus afirmaciones
- Corregir las preguntas cerradas contra la clave
- Calificar las respuestas escritas con la guía de 0 a 4, y repreguntar cuando son superficiales
- Calificar el entregable de la prueba del puesto
- Detectar contradicciones y respuestas demasiado ideales
- Ordenar a todos los candidatos en grupos de prioridad, con su razón
- Controlar los cronómetros y soltar el cambio inesperado
- Registrar los tiempos durante la simulación
- Redactar las preguntas de la conversación final
- Enviar todos los avisos
- Cerrar postulaciones abandonadas

### 2 · Una persona, siempre

Aquí la inteligencia artificial no decide nunca:

| Qué | Quién |
|---|---|
| Confirmar que un candidato avanza o no avanza | Equipo de Talento |
| Confirmar una barrera crítica | Equipo de Talento o Dirección |
| Calificar la simulación de trabajo | Equipo de Talento o responsable del área |
| La conversación final de unos 15 minutos | Equipo de Talento o responsable del área |
| Completar las métricas de la validación que no se alimentaron solas | Responsable del área |
| **Decidir la contratación** | Responsable del área o Dirección |
| Publicar una versión del banco de preguntas o de los pesos | Dirección |

### 3 · Una persona, solo si hace falta

Estas no pasan siempre. Son excepciones, y son las que aparecen en la pantalla de inicio:

| Cuándo | Qué hace |
|---|---|
| Un candidato queda en **alto potencial con riesgo** | Decide si avanza y qué hay que validar |
| No está de acuerdo con una nota | La ajusta, escribiendo por qué |
| Alguien **falta a la simulación** | Le da otra fecha o cierra su postulación |
| La decisión sale **ámbar** | Pide una evidencia dirigida |
| Se cierra la convocatoria con gente a mitad | Decide uno por uno: seguir, parar o al Radar |

### La forma que tiene esto

Con 100 postulantes, en números aproximados:

```
100 postulan
      |   ~8 no cumplen un requisito objetivo -> se cierran solas
 92 pasan al Perfil Integral
      |
      |   la IA califica a los 92 y los ordena:
      |
      |     Alta prioridad ................ 12  -> se revisan uno por uno
      |     Alto potencial con riesgo ......  8  -> se revisan uno por uno
      |     No priorizados ................ 62  -> se confirman en bloque
      |     Incompatibilidad objetiva ..... 10  -> se confirman en bloque
      v
 20 siguen a la prueba del puesto
      |   la IA califica y vuelve a ordenar
      v
 12 llegan a la simulacion    ---> una persona califica 12 y conversa con 12
      v
  3 llegan a la validacion    ---> el jefe completa lo que falte de 3
      v
  3 decisiones                ---> el jefe o Direccion deciden 3
```

Los números son un ejemplo, no una promesa. Pero la forma es la real: **de 100 postulantes, una
persona mira a unos 20 uno por uno y despacha a los otros 72 en dos confirmaciones por lote.**

Esto cumple las metas de automatización que fijó Renaser: 90–95% en la primera etapa, 95–100%
en la segunda, 80–95% en la tercera y 70–90% en la simulación.

**Ningún candidato queda sin procesar.** La diferencia con un filtro es que aquí todos reciben
una calificación y una razón explicable, incluso los que no se priorizan.

---

# 1. Solicitud de Talento

**RF-01** Toda vacante debe colgar de una Solicitud de Talento. No se puede publicar una
vacante suelta.

**RF-02** Una solicitud nace de dos sitios:

| Entrada | Cuándo | Qué hace el sistema |
|---|---|---|
| **La pide una persona** | Dirección, Talento o un responsable autorizado sabe que necesita contratar | Registra la solicitud. No la bloquea por no haber sido «descubierta», pero le exige responder lo mismo |
| **La detecta RENASER OS** | Los datos muestran sobrecarga, retrasos, pérdida de calidad o crecimiento planificado | Analiza si se puede eliminar, simplificar, automatizar o redistribuir. Si queda brecha, recomienda la solicitud |

**RF-03** La solicitud responde como mínimo: qué resultado falta, por qué se necesita la
persona, qué ocurre si no se contrata, cuándo se requiere, y qué parte podría eliminarse,
automatizarse o redistribuirse.

**RF-04** Una solicitud se marca **normal, prioritaria o urgente**. La urgencia cambia
prioridades y plazos internos, pero **no elimina ningún requisito ni la trazabilidad**.

**RF-05** La solicitud define internamente: resultado principal del cargo, entre 3 y 5
resultados esperados, indicadores de desempeño, nivel, familia de trabajo, capacidades
indispensables, capacidades aprendibles, modalidad, horario, compensación cuando corresponda, y
quién es el responsable de contratar.

**RF-06** Cuando la detecta el sistema, se guarda la evidencia que originó la recomendación:
carga, retrasos, indicadores, tareas, capacidad o retrabajo.

**RF-07** Los resultados e indicadores internos **no se publican necesariamente** con cifras
exactas. El responsable decide qué se muestra en la convocatoria pública y qué se revela
durante la preselección.

---

# 2. Vacantes

**RF-08** Cada vacante define: puesto, **nivel**, **familia de trabajo**, descripción,
requisitos, qué prueba se aplica, qué plantilla de evaluación usa y qué versión de pesos rige.

Los tres niveles son:

| Nivel | Qué responde |
|---|---|
| Dirección | Convertir estrategia, personas y recursos en resultados de empresa o unidad |
| Coordinación / Supervisión | Convertir planes en ejecución controlada y elevar el desempeño de personas y procesos |
| Operación / Ejecución | Producir entregables con calidad, velocidad, autonomía y comunicación |

Las siete familias son: Dirección/Negocio, Operaciones/Control, Crecimiento/Ventas,
Tecnología/Producto, Creativo/Experiencia, Talento/Personas y Seguridad crítica.

**RF-09** Una vacante se cierra de tres formas: **con fecha** de inicio y cierre, **hasta cubrir
un número de plazas**, o **permanente** para alimentar el Radar de Talento.

**RF-10** El nivel y la familia deciden qué preguntas responde el candidato. Los bancos
disponibles son 90 preguntas para Dirección, 60 para Supervisión y 50 para Ejecución, más el
banco de alineación personal.

**RF-11** Al crear la vacante, el responsable elige una plantilla de prueba y una plantilla de
evaluación ya cargadas, y puede crear una variante propia de esa vacante **sin alterar las
plantillas publicadas**.

**RF-12** La vacante pública incluye: puesto, propósito, responsabilidades, capacidades
esperadas, requisitos indispensables, modalidad, horario, ubicación, cultura de trabajo, y
compensación solo si Renaser decide publicarla.

**RF-13** La vacante pública **nunca** expone claves de evaluación, pesos, preguntas,
indicadores confidenciales ni puntuaciones mínimas internas.

**RF-14** Cerrar una vacante **detiene las postulaciones nuevas pero no cierra las que están en
marcha**. El equipo decide candidato por candidato: continuar, detener, o mandar al Radar
cuando hay consentimiento válido.

**RF-15** El sistema permite varias vacantes abiertas a la vez.

**RF-16** Los requisitos objetivos indispensables se configuran por vacante antes de publicarla.
Son lo único que puede detener una postulación sin que intervenga una persona, y se guarda la
regla exacta que se aplicó.

---

# 3. Radar de Talento

⚠️ **Se modela ahora y se construye después.** Las tablas quedan previstas para que añadirlo no
obligue a rehacer nada, pero no hay pantallas en la primera versión.

**RF-17** Existe un Radar de Talento independiente de las vacantes activas.

**RF-18** El Radar recibe: candidatos de procesos anteriores que aceptaron futuras
oportunidades, referidos, prospectos añadidos a mano y candidatos de convocatorias permanentes.

**RF-19** De cada persona del Radar se guarda: capacidades, evidencia, fuente, familias y roles
compatibles, nivel estimado, disponibilidad e interés, historial de contacto, última evaluación
vigente y su consentimiento para futuros contactos.

**RF-20** Antes de publicar una vacante, el sistema muestra los candidatos del Radar que podrían
encajar, **sin impedir que se publique**.

---

# 4. Portal del candidato

**RF-21** Cualquier persona puede ver las vacantes abiertas sin registrarse.

**RF-22** Para postular hay que crear una cuenta. Los candidatos no son usuarios de RENASER OS:
su cuenta vive en este sistema.

**RF-23** El candidato ve en todo momento su etapa actual, qué le falta, la fecha límite cuando
exista y cuánto tiempo le tomará lo siguiente.

**RF-24** Todas las evaluaciones se responden **dentro del portal**. El correo solo avisa que
hay algo pendiente; nunca contiene las preguntas.

**RF-25** El candidato puede postular a varias vacantes. Cada postulación tiene su propio
estado, su propia versión de criterios y su propio proceso.

**RF-26** El portal del candidato está **aislado** de los datos internos y de los demás
candidatos. Nunca sabe cuántos hay ni quiénes son.

## Antes de empezar a evaluarse

**RF-27** Antes de la evaluación, el candidato ve dos cosas: la **Ficha Real del Puesto** y el
documento de **Aceptación del Proceso**.

**RF-28** La Ficha Real del Puesto explica qué se espera del cargo, responsabilidades, forma de
trabajo, nivel de autonomía, modalidad, horario, herramientas, cómo se medirá su trabajo y las
etapas del proceso. Puede dar más detalle que la convocatoria pública.

**RF-29** La aceptación informa como mínimo: etapas, tiempos aproximados, que participan agentes
de inteligencia artificial, tratamiento de datos, uso de sus entregables, confidencialidad,
cuánto tiempo se conservan sus datos y sus derechos de retiro y actualización.

**RF-30** **Son dos consentimientos separados.** Uno para el proceso actual; otro para conservar
sus datos y contactarlo por futuras oportunidades. El segundo **nunca se da por supuesto**.

**RF-31** El registro de aceptación guarda: usuario autenticado, nombre registrado, versión del
texto aceptado, fecha y hora, dirección desde donde se aceptó, identificador de sesión y huella
del documento. La evidencia se puede exportar.

**RF-32** La aceptación electrónica **no sustituye** las obligaciones legales que correspondan
si la validación práctica implica trabajo real. Los textos legales y la forma exacta de firma
los aprueba el responsable legal antes de producción.

## Conservación de datos

**RF-33** El periodo de conservación se configura según la política aprobada. **No se escribe un
número de meses en el código.**

**RF-34** Al vencer el periodo, el sistema ejecuta la política definida: eliminar, anonimizar o
pedir que renueve el consentimiento.

**RF-35** El candidato puede retirar el consentimiento de futuras oportunidades sin que eso
afecte a los registros que haya obligación de conservar.

---

# 5. Etapas 1 y 2 — Perfil Integral de Preselección (40%)

El currículum, el módulo psicométrico y la evaluación **no son tres filtros aislados**. Para
todos los que cumplen los requisitos objetivos se genera un Perfil Integral que se lee entero.

| Componente | Peso | Estado |
|---|---|---|
| Currículum y evidencia | 10% | Operativo |
| Módulo psicométrico propio | 5% | Experimental y versionado |
| Evaluación Renaser: banco por nivel y familia, más alineación personal | 25% | Operativo |
| **Total** | **40%** | Se interpreta en conjunto |

**RF-36** El reparto interno 10 / 5 / 25 es configurable por versión y **solo cambia creando una
versión nueva aprobada**. Nunca cambia hacia atrás.

**RF-37** Mientras el módulo psicométrico no exista, su 5% se reparte entre los otros dos. El
día que exista, se conecta sin rehacer nada.

**RF-38** Mientras el módulo psicométrico no esté calibrado **no puede frenar a nadie por sí
solo** ni ser la única razón para no avanzar.

**RF-39** El sistema debe admitir en el futuro instrumentos psicométricos externos sin cambiar
el flujo ni el modelo de datos.

## El currículum

**RF-40** El candidato sube su currículum y puede agregar portafolio, repositorio, productos,
publicaciones u otras evidencias.

**RF-41** Antes de que la IA lea el currículum, el sistema **oculta foto, edad, sexo, estado
civil** y cualquier otro dato marcado como no utilizable para puntuar. Se guarda cuál de las dos
versiones se envió, para poder demostrar que la regla se cumplió.

**RF-42** El equipo sí ve el currículum completo cuando lo abre.

**RF-43** La IA lo puntúa sobre 100 con ocho criterios, cuyo peso cambia según el nivel:

| Criterio | Dirección | Coordinación / Supervisión | Operación / Ejecución |
|---|---|---|---|
| Resultados demostrables | 25 | 20 | 20 |
| Complejidad y alcance | 15 | 10 | 5 |
| Sistemas o procesos creados | 15 | 15 | 10 |
| Desarrollo de personas | 15 | 15 | 0 |
| Velocidad de aprendizaje | 10 | 10 | 15 |
| Iniciativa o creación | 5 | 10 | 10 |
| Habilidades del puesto | 5 | 10 | 25 |
| Calidad de la evidencia | 10 | 10 | 15 |

Ese puntaje es **interno, explicable y versionado**, y aporta 10 de los 40 puntos del Perfil
Integral.

**RF-44** La antigüedad no da puntos por sí sola.

**RF-45** Cada afirmación del currículum se clasifica en una de cuatro: **demostrada**,
**declarada sin verificar**, **contradicha** o **falta información**. «No verificado» nunca
equivale a mentira: es algo que hay que repreguntar.

**RF-46** El currículum **no descarta a nadie**. Todos los que pasan los requisitos objetivos
llegan al Perfil Integral.

## El banco no es el examen

**RF-47** Los bancos son repositorios de preguntas, **no cuestionarios que se apliquen enteros**.

**RF-48** Cada vacante genera su propia versión de evaluación, con preguntas elegidas por nivel,
familia de trabajo, capacidades críticas, la plantilla de esa vacante y preguntas de
verificación sacadas del currículum cuando haga falta.

**RF-49** La duración objetivo es: **40–50 minutos** en Dirección, **35–45** en Coordinación y
**25–35** en Ejecución. Si la configuración pasa de 60 minutos, el sistema **avisa antes de
publicarla**.

**RF-50** Quien crea la convocatoria fija **hasta cuándo se puede empezar y hasta cuándo se
puede terminar** la evaluación.

**RF-51** Las preguntas y sus opciones se muestran en orden aleatorio dentro de los límites de
la plantilla, distinto para cada candidato, y el sistema guarda ese orden para poder reproducir
el examen exacto.

**RF-52** Cada respuesta se guarda al momento. Si se corta la conexión, retoma donde quedó.

**RF-53** El candidato **nunca** ve claves, puntajes por opción, nombres internos de dimensiones
ni la lógica de cálculo.

## Tipos de pregunta

**RF-54** Hay cinco tipos y cada uno se corrige distinto:

| Tipo | Cómo se corrige | ¿Suma nota? |
|---|---|---|
| Elección forzada de estilo | No hay respuesta correcta; dibuja un perfil de tradeoffs | **No.** No puede ser filtro |
| Situación con opciones | Contra una clave versionada, de 0 a 4 | Sí |
| Caso real conductual | La IA con rúbrica de 0 a 4, y puede repreguntar por evidencia | Sí |
| Microcaso | Rúbrica por dimensiones | Sí |
| Consistencia | Compara respuestas relacionadas | Genera alertas, no descarta |

**RF-55** Las respuestas abiertas se califican de 0 a 4 con esta guía:

| Puntos | Cuándo |
|---|---|
| 0 | No da un caso, responde en abstracto o evade |
| 1 | Cuenta un caso pero fue pasivo, sin contribución propia clara |
| 2 | Hubo acción clara, pero poca medición, evidencia o aprendizaje |
| 3 | Actuó por iniciativa, con criterio y resultado verificable |
| 4 | Anticipó, priorizó, comunicó, actuó, midió y convirtió el aprendizaje en sistema |

**RF-56** Cada calificación automática guarda: rúbrica usada, evidencia citada de la propia
respuesta, puntaje, explicación, nivel de confianza, qué agente la hizo y con qué versión.

**RF-57** Si una respuesta es superficial, el agente puede **repreguntar de forma limitada** para
obtener número, contexto, acción propia, herramienta, resultado y aprendizaje. No puede
convertir cada pregunta en una entrevista interminable.

**RF-58** Para Dirección y Coordinación, la evaluación incluye obligatoriamente evidencia sobre
control de personas y actividad, supervisión diferenciada y comunicación preventiva.

## Alineación personal

Mide comportamientos de trabajo, no vida privada ni salud.

**RF-59** Son tres bloques:

| Bloque | Qué busca | Qué nunca puede ser |
|---|---|---|
| Relación con el dinero y el trabajo | Cómo decide frente a incentivos, recursos y largo plazo | Preguntas sobre patrimonio o juicios morales por querer ganar dinero |
| Madurez relacional en el trabajo | Cómo entra al conflicto, recibe correcciones, repara y pone límites | Evaluación de pareja, familia o estado civil |
| Autogobierno y sostenibilidad | Disciplina, gestión de carga, avisar antes de fallar | Diagnóstico de salud, apariencia, peso o enfermedades |

**RF-60** **No hace falta aplicar las 36 preguntas a todo el mundo.** La plantilla elige las que
correspondan al nivel y al rol, dejando suficientes para poder comprobar consistencia.

**RF-61** El resultado es un semáforo por bloque.

**RF-62** **Un rojo no descarta a nadie.** Genera hipótesis y preguntas de validación para la
conversación final.

## Alertas

**RF-63** El sistema detecta cuando alguien se contradice entre dos preguntas que miden lo mismo
desde ángulos distintos, y cuando siempre elige la respuesta más ideal sin reconocer ningún
límite.

**RF-64** Las alertas **no descartan a nadie**. Quedan visibles en la ficha y se convierten en
preguntas para la conversación final.

## Lo que sale de aquí

**RF-65** El resultado **no es solo una nota**. Es un Perfil de Talento con, como mínimo:
adecuación al puesto, potencial, alto rendimiento, principales fortalezas, riesgos, talentos
canalizables, evidencia que falta y **confianza de la evidencia**.

**RF-66** Los riesgos se distinguen por tipo y **no se mezclan**: riesgo crítico, riesgo
desarrollable, preferencia o estilo, y falta de evidencia.

**RF-67** Cuando hay fortalezas claras pero mala adecuación a esta vacante, el sistema puede
sugerir un encaje mejor en otro puesto o familia. **La sugerencia no mueve la postulación** sin
que una persona lo haga.

**RF-68** La IA ordena a todos los candidatos en cuatro grupos: **alta prioridad**, **alto
potencial con riesgo**, **no priorizados** e **incompatibilidad objetiva**.

**RF-69** Los no priorizados tienen una razón explicable y su evidencia. Una persona autorizada
puede confirmarlos **por lote**, sin abrir uno por uno, y cada uno conserva su razón individual.

## Reutilizar lo ya respondido

**RF-70** **Que dos puestos sean del mismo nivel no basta** para reutilizar una evaluación
completa.

**RF-71** Solo se reutilizan los componentes vigentes cuando el puesto nuevo es de la misma
familia o de una declarada afín. El núcleo común se reutiliza dentro de su vigencia; las
preguntas propias del puesto se vuelven a generar.

**RF-72** La vigencia de cada componente y qué familias son afines entre sí son configurables y
versionadas.

---

# 6. Etapa 3 — Prueba del puesto (30%)

Es **obligatoria para todo puesto**. Un portafolio no sustituye demostrar la capacidad con un
problema nuevo.

**RF-73** Toda prueba tiene cuatro momentos: **comprende**, **produce**, **explica**, **se
adapta**.

**RF-74** La plantilla define duración, materiales, entregables, herramientas permitidas,
preguntas previas, preguntas posteriores, cambio inesperado, rúbrica y método de verificación.

**RF-75** El cronómetro arranca cuando el candidato confirma que empieza, y **no se detiene**,
salvo reglas de accesibilidad aprobadas.

**RF-76** La duración es **configurable**, y quien la fija es la empresa dueña de la prueba. Se
declara en la plantilla, y **la vacante puede decir otra** para su etapa técnica: entonces manda
la de la vacante. Lo único que el sistema impone es un **piso de 5 minutos**.

> ⚠️ **Aquí decía «de 60 a 120 minutos», y ese rango se retiró el 31/08/2026.** Lo decidió
> Renaser: manda el tiempo que ponga la empresa, no un rango que el sistema imponga. Hasta ese
> día, publicar una versión de plantilla con 45 o con 180 minutos se rechazaba, y esa validación
> ya no existe.
>
> **Por qué queda un piso, y por qué son cinco.** Desde que los minutos de la vacante rigen
> también la prueba del puesto, cualquier prueba puede volverse cronometrada — y un uno ahí es
> una prueba que el barrido de vencidos entrega sola sesenta segundos después de que el
> candidato la abra, con la pantalla todavía cargando. Cinco es el mismo número en los dos
> sitios que lo comprueban: publicar la versión, y fijar los minutos de la vacante.
>
> **Lo que el rango sostenía sin que nadie lo dijera:** que el cambio inesperado del RF-77
> siempre cupiera dentro del reloj. Ver [Defectos conocidos](DEFECTOS-CONOCIDOS.md).

**RF-77** El cambio inesperado tiene **una o varias variantes**, y el momento en que aparece se
sortea dentro de un rango configurable. **No se fija «a la mitad»**, para que no se aprenda el
patrón.

> ⚠️ **Hoy nada comprueba que ese rango quepa dentro de la prueba**, ni en la base ni en el
> código, y con pocos minutos el cambio no se revela nunca. Está abierto y descrito en
> [Defectos conocidos](DEFECTOS-CONOCIDOS.md).

**RF-78** El sistema arranca con once plantillas cargadas: Dirección de unidad, Coordinación de
Operaciones, Talento y Recursos Humanos, Crecimiento y Marketing, Compra de Medios, Desarrollo
de Software, Producto y Experiencia de Usuario, Diseño Gráfico, Edición de Video, Ventas, y
Seguimiento y Experiencia del Cliente.

> ⚠️ **Este requisito sigue vigente y hoy NO se cumple así.** Nadie decidió lo contrario: es que
> **ninguna migración inserta ni una sola plantilla de prueba**. Una base recién creada arranca
> con la tabla vacía, y las pruebas que existen se han cargado **por la API, con los guiones de
> `scripts/`** (`cargar-prueba-administrador.py` es el que hay en el repositorio). Los documentos
> del modelo decían durante meses que estaban «sembradas»; no lo estaban.
>
> Que se carguen por guion en vez de por migración no es un descuido a medias: **el contenido de
> una prueba lo escribe Renaser y todavía no está escrito**, así que sembrar once plantillas
> vacías con sus nombres habría dejado once cáscaras que nadie puede rendir. Lo que falta para
> cumplir este requisito de verdad es el contenido, no el `INSERT` — y es la misma decisión
> pendiente que describe [Alcance del MVP](08-ALCANCE-DEL-MVP.md).

**RF-79** Las pruebas se configuran desde el sistema. **No están escritas en el código.**

**RF-80** El candidato entrega subiendo archivos o pegando enlaces. El sistema guarda versiones,
marcas de tiempo y las evidencias relevantes.

**RF-81** Hay pruebas cuyo enunciado simula un horario propio —*"son las 9:00, organiza hasta las
18:00"*—. La pantalla debe **separar visualmente** la hora del caso del tiempo real que le
queda:

```
  TIEMPO RESTANTE  01:12:44        <- reloj real, arriba y destacado
  ----------------------------------
  En el caso son las 9:00 y debes    <- parte del enunciado
  organizar la jornada hasta las 18:00
```

## Las preguntas de la prueba

**RF-82** **Antes de producir**, el candidato responde como mínimo: qué problema entiende, quién
recibe el resultado, qué significa que funcione, qué información falta y qué supuestos hará.

**RF-83** **Después de producir**, la plantilla selecciona entre **8 y 10 preguntas universales**
y entre **3 y 5 específicas del puesto**. No son 17 fijas para todos.

Las universales disponibles son:

| Pregunta | Qué revela |
|---|---|
| ¿Por qué elegiste este enfoque? | Criterio |
| ¿Qué alternativas consideraste y cuál descartaste? | Tradeoffs |
| ¿Qué herramientas e inteligencia artificial usaste y para qué? | Multiplicación de capacidad |
| ¿Qué parte verificaste personalmente? | Comprensión y calidad |
| ¿Cuánto tiempo usaste en comprender, ejecutar y revisar? | Gestión del tiempo |
| ¿Dónde puede fallar tu solución? | Sentido crítico |
| ¿Cómo medirías si realmente funciona? | Orientación a resultado |
| ¿Qué cambiarías con una hora adicional? | Priorización |
| ¿Qué aprendiste durante la prueba? | Aprendizaje |
| ¿Qué parte podrías convertir en sistema o automatización? | Sistematización |

**RF-84** El sistema **no usa detectores de inteligencia artificial**. Usarla no está prohibido:
se evalúa si la persona entiende y verifica lo que produjo. Eso vale 5 puntos.

## Puntuación de la prueba

**RF-85** La rúbrica base reparte 100 puntos:

| Qué se mide | Puntos |
|---|---|
| Resultado producido | 25 |
| Calidad | 15 |
| Comprensión del problema | 10 |
| Velocidad y manejo del tiempo | 10 |
| Criterio en las decisiones | 10 |
| Capacidad de explicar lo hecho | 10 |
| Uso inteligente y verificado de IA y herramientas | 5 |
| Orientación a resultados medibles | 5 |
| Adaptación al cambio | 5 |
| Aprendizaje | 5 |

**RF-86** Cada puesto puede tener su propia rúbrica, que también suma 100 y queda versionada.

**RF-87** **Cada criterio declara cómo se verifica**: lo mide el sistema, lo califica un agente,
o lo revisa una persona. No se asume que la IA puede observar todo con igual fiabilidad.

Por ejemplo: el tiempo lo mide el sistema; las pruebas automáticas de un código las corre el
sistema cuando existen; la argumentación la califica un agente; el criterio visual lo califica
un agente y lo revisa una persona en los finalistas.

**RF-88** Una persona puede ajustar cualquier nota que puso un agente. Debe justificar el cambio
y queda auditoría completa.

**RF-89** Mientras la rúbrica está en borrador, el sistema **avisa** si el reparto no suma 100
pero **deja guardar**: el cliente puede estar a mitad de un ajuste. Al **publicar** la versión,
sumar 100 es obligatorio y el sistema no deja continuar si no cuadra.
*Por qué las dos reglas:* trabajar es incómodo si cada guardado exige que cuadre, pero una
rúbrica publicada que no suma 100 califica mal a todo el que la rinda.

**RF-90** Los cambios en una prueba **no afectan a quien ya la rindió**. Cada entrega queda
atada a la versión con la que se hizo.

---

# 7. Etapa 4 — Simulación de trabajo (15%)

El candidato **todavía está siendo evaluado**. La inducción ocurre solo después de contratar.

**RF-91** El equipo crea las sesiones que necesite, con fecha, hora, modalidad, sede o enlace,
cupo y responsables. **No hay que publicar exactamente dos fechas.**

**RF-92** La sesión puede ser grupal o individual. Arranca en grupal, y es configurable.

**RF-93** Cada sesión indica **para qué vacantes sirve**: una, varias o todas.

**RF-94** El candidato ve solo las sesiones de su vacante que tengan cupo, y elige una. Si no hay
ninguna, su postulación aparece en la bandeja del equipo como pendiente de programar.

**RF-95** Cuando una sesión llena su cupo deja de ofrecerse. El equipo puede ampliar el cupo o
publicar otra fecha. Si se cancela, a los inscritos se les avisa y vuelven a elegir.

**RF-96** La simulación dura **hasta 120 minutos** e incluye tiempo para la conversación final.
El reparto es configurable; la plantilla inicial recomendada es:

| Momento | Minutos |
|---|---|
| Contexto | 0–10 |
| Preguntas | 10–20 |
| Ejecución | 20–80 |
| Cambio inesperado | 80–100 |
| Entrega | 100–105 |
| Conversación | 105–120 |

**RF-97** El sistema registra **solo eventos observables**: cuándo aparece un bloqueo, cuándo el
candidato lo abre, cuándo pregunta, cuándo comunica un riesgo, la primera evidencia, la entrega
y la autocrítica.

**RF-98** **No se registra «el momento en que detectó mentalmente un bloqueo»**, salvo que el
candidato lo declare con una acción que quede registrada.

**RF-99** Cada simulación define una **matriz de información crítica**: qué preguntas debería
hacer un candidato fuerte, qué datos son opcionales y cuáles hay que descubrir. Así se puede
evaluar la calidad de sus preguntas sin adivinar lo que no preguntó.

**RF-100** La simulación se califica sobre 100: comprensión del resultado 10, calidad de las
preguntas 10, velocidad de arranque 10, priorización 10, ejecución 15, calidad 15, autonomía 10,
comunicación preventiva 10, uso de recursos e IA 5, evidencia y aprendizaje 5.

**RF-101** Antes de la conversación final, un agente genera **de 3 a 5 preguntas
personalizadas** a partir de las contradicciones entre currículum, evaluación, prueba y
simulación.

Ejemplo: *"En tu evaluación dijiste que avisas los riesgos temprano. Aquí detectaste el bloqueo
a las 10:41 y lo informaste a las 10:49. ¿Qué pasó?"*

**RF-102** La conversación dura unos **15 minutos** y se centra en dudas críticas,
contradicciones y riesgos. **No se vuelve a preguntar lo que ya está demostrado.**

**RF-103** El responsable registra respuesta breve, si el riesgo quedó resuelto o no, y una
observación final. **No hace falta un módulo de entrevista aparte.**

**RF-104** El sistema registra quién asistió y quién no.

---

# 8. Etapa 5 — Validación práctica (15%)

**RF-105** Es el último paso de evidencia antes de la decisión final, cuando el puesto lo
requiera.

**RF-106** Tiene dos modalidades:

| Modalidad | Qué necesita |
|---|---|
| Simulación extendida, sin trabajo productivo | Nada especial. Se puede usar desde el primer día |
| Trabajo real y productivo | La figura contractual aprobada por Renaser |

**RF-107** El sistema **no habilita la modalidad productiva** hasta que la vacante tenga
registrado el tipo de vinculación, el responsable y la confirmación legal.

**RF-108** La duración arranca en siete días pero **es configurable por vacante**, porque no
todos los cargos necesitan el mismo periodo.

**RF-109** Se miden nueve métricas:

| Métrica | Peso |
|---|---|
| Resultado logrado | 25% |
| Calidad al primer intento | 15% |
| Velocidad | 10% |
| Confiabilidad | 10% |
| Autonomía | 10% |
| Comunicación preventiva | 10% |
| Aprendizaje entre iteraciones | 10% |
| Servicio | 5% |
| Aporte al sistema | 5% |

**RF-110** Siempre que RENASER OS ya tenga el dato —tareas, tiempos, evidencias, bloqueos,
retrabajo, cumplimiento— la métrica **se alimenta sola**. La persona responsable solo completa
lo que no se puede observar.

**RF-111** De cada dato se ve **de dónde salió**: automático de RENASER OS, calificación del
responsable, agente, u otra evidencia.

**RF-112** Si la evaluación predijo alto pero el trabajo observado es bajo de forma consistente,
**manda el trabajo observado**, y el sistema muestra esa contradicción explícitamente.

---

# 9. La decisión

No es un promedio ciego, pero tampoco un semáforo sin lógica. Combina puntuación, barreras,
cantidad de evidencia y juicio humano.

**RF-113** El sistema calcula una Puntuación Global con los pesos de la versión que rija esa
vacante.

**RF-114** Si una etapa no aplica legítimamente, la vacante debe tener una **versión de pesos
aprobada antes de que empiecen los candidatos**. Nunca se redistribuyen pesos a mano por
persona.

**RF-115** Además del puntaje, cada vacante define sus **barreras críticas**: capacidades que
hay que demostrar y que **ningún promedio alto puede tapar**.

| Nivel | Ejemplos de barreras críticas |
|---|---|
| Dirección | Falta grave de integridad · No sabe priorizar · Ausencia de control · Incapacidad de decidir · Sin evidencia de liderazgo cuando el cargo lo exige |
| Coordinación / Supervisión | No sabe hacer seguimiento · Comunica tarde de forma persistente · Trata igual a todos · Trabaja sin sistema ni control |
| Operación / Ejecución | Prueba claramente insuficiente · Repite errores tras la corrección · Esconde bloqueos · Calidad incompatible con el resultado esencial |

**RF-116** Un agente puede detectar una posible barrera crítica y explicar su evidencia, pero
**una persona autorizada la confirma** antes de que se convierta en una decisión negativa.

**RF-117** El resultado es uno de cinco:

| Resultado | Qué significa | Qué se hace |
|---|---|---|
| **Verde** | Evidencia consistente y suficiente, sin barreras sin resolver | Avanza o se contrata |
| **Ámbar** | Hay potencial, pero una contradicción o un riesgo pide validación dirigida | Se pide evidencia adicional |
| **Rojo** | Evidencia confirmada de incompatibilidad con una barrera crítica | No avanza en ese puesto |
| **Sin datos** | No hay evidencia suficiente | Se pide más, no se asume que falla |
| **Reserva** | No es la mejor para esta vacante, pero tiene talento relevante | Pasa al Radar, con consentimiento |

**RF-118** El informe final muestra por separado: puntuación global, adecuación al puesto,
potencial, alto rendimiento, fortalezas, riesgos, talento canalizable, barreras críticas,
confianza de la evidencia, sugerencias de desarrollo y posibles roles alternativos.

**RF-119** La persona autorizada decide: contratar, no contratar para este puesto, pasar a
reserva, pedir una evidencia adicional o considerar otro puesto. Toda decisión guarda quién,
cuándo y por qué.

**RF-120** Existe el **Evaluador de Estándar**: alguien ajeno al área que revisa el expediente
para que la urgencia no baje el nivel de contratación. Su poder de aprobar o bloquear es
**configurable**; en la primera versión emite una recomendación registrada.

**RF-121** Cualquier persona con permiso puede cambiar una decisión del sistema, en cualquier
dirección. Debe justificarlo y queda registrado.

---

# 10. Después de contratar

**RF-122** A los **30, 90 y 180 días** el sistema registra el desempeño real y lo compara con la
evidencia de selección.

**RF-123** Los datos se leen de RENASER OS cuando existan: objetivos, tareas, evidencias, plazos,
retrabajo, bloqueos, clientes y resultados.

**RF-124** Cada contratado tiene un módulo de desempeño con resultado esperado, porcentaje
logrado, calidad, velocidad, confiabilidad, autonomía, comunicación preventiva, aprendizaje,
servicio y aporte al sistema.

**RF-125** Cuando hay una desviación relevante se activa un diagnóstico breve: qué resultado
debía producir, qué logró, cuál fue el principal obstáculo, si la causa fue claridad, capacidad,
habilidad, proceso, dependencia, decisión o algo externo, qué acción tomó y qué cambiará.

**RF-126** Las preguntas de desempeño son **cortas y de diagnóstico**, no una encuesta larga.

**RF-127** El sistema analiza qué preguntas, pruebas y pesos se relacionan con buen o mal
desempeño posterior, pero **no cambia las reglas activas solo**. Cualquier recalibración crea
una versión nueva aprobada.

---

# 11. Panel de gestión

**RF-128** La pantalla principal muestra primero **lo que espera acción humana**: solicitudes
urgentes, candidatos ámbar, barreras críticas por confirmar, pruebas y simulaciones pendientes,
sesiones sin programar y decisiones finales pendientes.

**RF-129** Debajo, las vacantes con cuánta gente hay en cada etapa y cuántos días llevan sin
avanzar.

**RF-130** La ficha del candidato tiene un resumen arriba y secciones desplegables por etapa.
**No hay que recorrer varias pantallas** para reconstruir su historia.

| Sección | Qué muestra |
|---|---|
| Identidad y proceso | Puesto, etapa, origen, fechas |
| Perfil Integral | El 40%, dimensiones, potencial, adecuación, alineación (⚠️ los semáforos de alineación todavía no los calcula nadie: esa sección sale vacía) |
| Prueba del puesto | Puntaje, entregables, tiempos, explicación, cambio |
| Simulación | Eventos, puntaje, preguntas humanas y respuestas |
| Validación práctica | Métricas y de dónde salió cada una |
| Riesgos | Crítico, desarrollable o falta de evidencia, con su respaldo |
| Talento canalizable | Fortalezas aprovechables aquí o en otro rol |
| Decisión | Estado, responsable, motivo y auditoría |

**RF-131** Dirección ve al menos siete métricas:

1. Tiempo hasta tener un finalista fuerte
2. Horas humanas por contratación
3. Tasa de avance por etapa y dónde se cae más
4. Calidad de la contratación a 30, 90 y 180 días
5. Precisión entre lo que predijo el sistema y el desempeño real
6. Principales razones de caída
7. Efectividad de las preguntas y pruebas por familia

⚠️ **«Porcentaje de decisiones tomadas por IA» no es un indicador de éxito** y no debe aparecer
como tal. La automatización se mide por horas ahorradas y calidad de contratación.

---

# 12. Avisos

**RF-132** El sistema avisa cuando: se recibe la postulación, hay una acción pendiente, se
habilita una evaluación o prueba, se acerca un vencimiento, se habilitan fechas de simulación,
cambia de etapa, se pide evidencia, y termina el proceso.

**RF-133** El aviso **nunca contiene preguntas, claves ni el contenido de las pruebas**. Lleva
al portal seguro.

**RF-134** El candidato no recibe puntuaciones internas ni razones técnicas detalladas. El
mensaje de no continuidad es breve, respetuoso y consistente.

**RF-135** Si dio consentimiento para futuras oportunidades, el mensaje puede decir que su
perfil queda disponible para convocatorias compatibles. **Si no lo dio, no se afirma.**

**RF-136** Todo aviso enviado queda registrado con plantilla y versión, destinatario, fecha,
canal y estado de entrega.

---

# 13. Administración y versiones

**RF-137** Preguntas, claves, dimensiones, pesos, plantillas de evaluación, pruebas, rúbricas,
barreras críticas y reglas de decisión viven **en datos, no en el código**.

**RF-138** **Una versión publicada es inmutable.** Modificarla crea un borrador nuevo. Los
candidatos ya iniciados quedan ligados a la versión original.

**RF-139** Las notas históricas **no se recalculan** con reglas nuevas. Se debe poder reproducir
una decisión tal como existía en su momento.

**RF-140** Los roles son configurables. Arrancan cinco: Candidato, Equipo de Talento, Responsable
del Área, Dirección y Administrador. Un rol es un conjunto de permisos guardado en base de
datos, no algo fijo en el código.

Quién puede hacer qué está definido acción por acción en
[Roles y permisos](04-ROLES-Y-PERMISOS.md).

**RF-141** **La identidad viene de RENASER OS; los permisos de este módulo son de este módulo.**
RENASER OS dice quién eres; este sistema decide qué puedes hacer aquí, porque su sistema no
conoce acciones como «publicar una versión del banco».

**RF-142** Hay cosas que **nunca** son configurables, porque si aparecen como casilla alguien las
marcará algún día: que un candidato vea a otros candidatos, que las claves lleguen al portal,
que se borre la auditoría, que se salte el consentimiento, o que la máquina contrate sin una
persona.

**RF-143** Toda acción relevante deja auditoría: quién, cuándo, qué entidad, valor anterior y
nuevo, razón, y la versión de la regla o el modelo cuando corresponda.

---

# 14. Los agentes de inteligencia artificial

El sistema **no depende de un proveedor**. El conocimiento está en las reglas, los bancos, las
rúbricas, las instrucciones versionadas y la evidencia de Renaser, no en el modelo que hay
debajo.

**RF-144** Hay nueve agentes:

| Agente | De qué se encarga |
|---|---|
| Necesidad de Talento | Analiza capacidad, eliminación, automatización, redistribución y brecha |
| Cazatalentos | Relaciona el Radar con las necesidades y vacantes |
| Evidencia de Currículum | Extrae resultados, afirmaciones, evidencia, huecos y preguntas de validación |
| Evaluador | Procesa respuestas abiertas, repregunta dentro de límites y aplica rúbricas versionadas |
| Potencial y Riesgo | Integra el perfil e identifica fortalezas, riesgos, talento canalizable y roles alternativos |
| Prueba del Puesto | Analiza entregables y su defensa con la rúbrica, usando los verificadores objetivos que existan |
| Simulación | Analiza eventos, tiempos y comportamiento observable |
| Desempeño | Compara la selección con el desempeño a 30, 90 y 180 días |
| Aprendizaje | Propone qué preguntas y pesos parecen útiles. **Nunca cambia una regla por sí solo** |

**RF-145** Todo agente se ejecuta a través de una interfaz abstracta de proveedor y modelo.
Cambiar de modelo **no obliga a reescribir el flujo**.

**RF-146** Cada ejecución guarda como mínimo: organización, qué agente fue, **versión del
agente**, objetivo, entradas y evidencias, modelo y proveedor usados, versión del modelo,
versión de las instrucciones o la rúbrica, la salida completa, **el nivel de confianza** y la
fecha.

**RF-147** Las preguntas cerradas y las reglas determinísticas se calculan **por código**, nunca
por un modelo generativo.

**RF-148** El modelo generativo **no puede** modificar pesos, claves, barreras ni versiones
activas.

**RF-149** El modelo **no inventa datos que faltan**. Distingue hecho, declaración, hipótesis,
contradicción y falta de información.

**RF-150** Una nota sin explicación no se guarda. Si la IA falla, la calificación queda pendiente
y se reintenta: **nunca se guarda un cero por un problema técnico**.

---

# 15. Preparado para clientes de consultoría

**RF-151** Todas las entidades principales llevan **organización**: vacantes, candidatos,
evaluaciones, pruebas, decisiones, plantillas, consentimiento, agentes y métricas.

**RF-152** La primera versión puede bloquear la interfaz a Renaser, pero **el modelo de datos no
asume una sola empresa**.

**RF-153** Los bancos de Renaser son una **biblioteca global propietaria**. Cada organización
puede tener sus propias plantillas y criterios **sin modificar el original**.

**RF-154** El aislamiento de datos por organización es una **regla de seguridad desde la primera
versión**, no una función que se añade después.

---

# Qué cambia sin programar

Renaser ajusta seguido sus criterios. Todo lo de esta lista se cambia desde una pantalla, sin
tocar código y sin volver a publicar el sistema:

| Qué | Quién |
|---|---|
| Las preguntas: texto, opciones, puntajes | Talento prepara · Dirección publica |
| Las pruebas: enunciado, duración, variantes del cambio, puntos | Talento prepara · Dirección publica |
| Los pesos de cada etapa y el reparto interno del Perfil Integral | Dirección |
| Las barreras críticas de cada vacante | Talento |
| Los requisitos objetivos de cada vacante | Talento |
| Las plantillas de evaluación por nivel y familia | Talento |
| Qué familias son afines y cuánto dura la vigencia de cada componente | Dirección |
| El reparto de tiempos de la simulación | Talento |
| La duración de la validación práctica | Talento |
| Los textos de los correos | Talento |
| Las instrucciones que recibe la inteligencia artificial | Dirección |
| Los roles y sus permisos | Dirección |
| El periodo de conservación de datos y qué se hace al vencer | Dirección |
| Cuántos días sin avanzar antes de cerrar una postulación | Dirección |
| Cuántas veces puede repetirse el ámbar | Dirección |
| Si el Evaluador de Estándar puede bloquear | Dirección |

**Cambiar algo no altera lo ya evaluado.** Cada candidato queda atado a la versión con la que se
le evaluó, así que su nota nunca cambia sola después de habérsela comunicado.

---

# Documentos relacionados

Este documento se lee solo. Estos otros existen para el detalle largo:

| Documento | Qué contiene |
|---|---|
| [Qué hace el sistema](00-QUE-HACE-EL-SISTEMA.md) | El sistema entero, sin nada técnico. **Empieza por aquí** |
| [Requisitos no funcionales](02-REQUISITOS-NO-FUNCIONALES.md) | Tecnología, seguridad, rendimiento, datos personales |
| [Estados de la postulación](03-ESTADOS-POSTULACION.md) | Los 18 estados y cómo se pasa de uno a otro |
| [Roles y permisos](04-ROLES-Y-PERMISOS.md) | Quién puede hacer qué, acción por acción |
| [Modelo de datos](05-MODELO-DE-DATOS.md) | Las tablas por área y por qué existe cada una |
| [Etapas y pesos](diagramas/embudo-seleccion.html) | Los cien puntos repartidos, en un dibujo |

Los documentos originales de Renaser están en `docs/insumos/`. El vigente es
**nuevo_doc_requisitos_funcionales**, que se declara definitivo y reemplaza a los anteriores.
`Banco_Maestro_Preguntas` sigue vigente solo para las preguntas. Qué cambió respecto de la
versión anterior está en
[Qué cambia con el documento nuevo](insumos/CAMBIOS-DEL-DOCUMENTO-NUEVO.md).

---

# Pendiente de Renaser

| Qué falta | Bloquea |
|---|---|
| Definir la figura contractual de la validación práctica productiva | Solo esa modalidad. La otra se puede usar ya |
| Aprobar los textos legales de consentimiento y conservación | Bloquea producción, no el desarrollo |
| Fijar el periodo de conservación de datos | No bloquea: es configuración, arranca con un valor y se ajusta |
| Decidir si el módulo psicométrico propio se construye | No bloquea: su 5% se reparte mientras tanto |
| Confirmar el catálogo de puestos y sus nombres definitivos | No bloquea: hay once plantillas nombradas |
| Enumerar qué preguntas se comparan entre sí para detectar contradicciones | No bloquea: la tabla arranca vacía |
| Confirmar la máquina de estados, que es propuesta nuestra | No bloquea: está construida y es coherente |

**Ninguno frena el desarrollo.** El sistema arranca con valores razonables y Renaser los ajusta
desde la pantalla cuando lo tenga claro. Esa es la razón de hacerlos editables: el cliente
todavía no tiene la respuesta, y probablemente la cambie más de una vez.

---

# Documentos relacionados

| Documento | Qué contiene |
|---|---|
| [Qué hace el sistema](00-QUE-HACE-EL-SISTEMA.md) | El sistema entero, sin nada técnico. **Empieza por aquí** |
| [Requisitos no funcionales](02-REQUISITOS-NO-FUNCIONALES.md) | Tecnología, seguridad, rendimiento |
| [Estados de la postulación](03-ESTADOS-POSTULACION.md) | Los 18 estados y cómo se pasa de uno a otro |
| [Roles y permisos](04-ROLES-Y-PERMISOS.md) | Quién puede hacer qué, acción por acción |
| [Modelo de datos](05-MODELO-DE-DATOS.md) | Las tablas por área y por qué existe cada una |
| [Alcance del MVP](08-ALCANCE-DEL-MVP.md) | Qué se construye primero, en qué orden y por qué |
