# Alcance del MVP

Sistema de selección de personal — Renaser Consulting
Versión 1.0 · 2026-08-15

Renaser no quiere el sistema completo todavía. Quiere **algo que pueda usar**, y quiere saber
**si esto va a funcionar** antes de construirlo entero. Este documento dice qué se construye
primero, en qué orden, y qué pregunta responde cada parte.

Lo que hace el sistema completo está en [Qué hace el sistema](00-QUE-HACE-EL-SISTEMA.md).

---

## Lo primero: qué puede y qué no puede responder un MVP

Hay que decirlo antes que nada, porque si no el MVP se juzga contra una pregunta que no puede
contestar.

**«¿Funciona?» de verdad significa: ¿la gente que contratamos así rinde mejor?** Y eso no se sabe
hasta 90 días después de contratar a alguien. Ningún MVP lo responde dentro de su propia vida.
Lo que sí se puede hacer es dejar el gancho puesto y esperar.

Lo que el MVP **sí** responde, y desde la primera vacante:

| Pregunta | Cómo se mide |
|---|---|
| ¿Ahorra horas de trabajo? | Horas por candidato, contra cómo lo hacen hoy |
| ¿El equipo se fía del orden que propone la máquina? | Cuántas veces cambian el grupo que puso la IA, y hacia dónde |
| ¿Las contradicciones que marca son de verdad? | Se revisan una por una y se apunta cuántas eran reales |
| ¿La gente termina la evaluación o la abandona? | Cuántos empiezan y cuántos acaban |
| ¿Tarda menos en llegar a un finalista? | Días desde publicar hasta tener el primero |

⚠️ **Las tres primeras necesitan una línea base que hoy no existe.** Hay que medir cómo lo hacen
hoy **antes** de encender nada. Está en «Condiciones previas», más abajo.

---

## Paso 0 · Comprobar que la IA califica como una persona

**Se puede hacer ya, con lo que Renaser tiene, y no necesita que se programe nada.**

La idea original era aplicar el método a gente ya contratada de la que se sabe cómo le fue. Eso
no se puede: **Renaser no tiene ese historial.** Pero tiene algo casi tan útil — cinco pruebas
del puesto reales, ya enviadas a candidatos, en `insumos/pruebas-tecnicas/`.

Con eso, el paso 0 es:

1. Coger **una** de las cinco pruebas, la que más se vaya a repetir.
2. Que Renaser le ponga **peso a los diez o doce criterios** de su «Qué valoraremos». Hoy son una
   lista sin números, y el sistema necesita que sumen 100. Es una tarde de trabajo.
   ⚠️ **Esto todavía no existe, y es lo único que frena el paso 0.** Nadie ha puesto esos pesos.
3. Calificar a mano cinco o seis entregas reales con esa rúbrica.
4. Pedirle a la IA que califique las mismas con la misma rúbrica, y comparar.

**Qué se aprende:** si la IA se parece a un evaluador de Renaser cuando los dos usan la misma
regla. Es la pregunta que decide si el hito 2 y el hito 3 tienen sentido, y se responde en días.

De paso quedan hechas dos cosas que hacen falta igual: la primera rúbrica con pesos, y los
ejemplos calibrados que llevan pendientes desde el principio.

⚠️ **Lo que este paso 0 no responde** es si la gente contratada así rinde mejor. Para eso hace
falta el historial que Renaser no tiene, así que esa respuesta empieza a construirse ahora: desde
la primera contratación del MVP, con el seguimiento a los 90 días.

---

## La escalera · Tres hitos, cada uno se puede usar solo

La idea no es construir un sistema pequeño. Es construir el sistema **por peldaños**, y que
Renaser pueda usar cada peldaño en una vacante real antes de subir al siguiente.

| Hito | Qué permite hacer | Tablas | Pantallas |
|---|---|:--:|:--:|
| **1 · Que entre gente** | Solicitud aprobada, vacante publicada, postulaciones, mover a mano | 34 | 10 |
| **2 · Que la máquina ordene** | La IA lee, puntúa y ordena en grupos | 62 | 13 |
| **3 · Que se vea lo que hacen** | La prueba del puesto, y comparar dicho con hecho | 73 | 15 |

Las tablas son acumuladas. El sistema completo son 93 tablas y 21 pantallas base.

---

### Hito 1 · Que entre gente

**Ya es usable de verdad.** Renaser publica una vacante, la gente postula, y el equipo lleva el
proceso a mano desde el panel. Sin inteligencia artificial todavía.

Qué entra:

- La Solicitud de Talento con sus resultados esperados: Talento prepara, Dirección aprueba
- Publicar una vacante y cerrarla (solo desde una solicitud aprobada)
- El portal del candidato: ver la vacante, crear cuenta, postular, subir el currículum
- Los dos consentimientos por separado, con su texto guardado
- La comprobación de los requisitos indispensables — el único descarte automático
- La bandeja del equipo, ordenada por **a quién se está esperando**
- Mover una postulación a mano, siempre con motivo
- Correos al candidato en cada paso
- Auditoría completa desde el primer día
- Borrado de datos a petición

**Qué pregunta responde:** si el portal se entiende, si la gente termina de postular, y cuántas
postulaciones llegan de verdad por vacante — que hoy nadie sabe.

---

### Hito 2 · Que la máquina ordene

Aquí entra el Perfil Integral, que es la etapa más grande y la que más trabajo humano puede
ahorrar.

Qué se añade:

- El banco de preguntas con versiones, y la receta que elige cuáles le tocan a cada puesto
- La evaluación del candidato, con el orden de las preguntas mezclado
- La IA puntúa el currículum y las respuestas abiertas, y **explica siempre por qué**
- Detecta contradicciones entre lo que dijo en un sitio y lo que dijo en otro
- Arma el retrato de la persona: fortalezas y riesgos, con su evidencia
- Ordena a todos en cuatro grupos de prioridad
- El equipo confirma, uno por uno o **por lote**, y cada uno conserva su razón
- La pantalla de resultados, con las medidas de arriba

**Qué pregunta responde:** si ahorra horas, y si el equipo se fía del orden que propone. Esta es
la medición que más le importa a Renaser.

---

### Hito 3 · Que se vea lo que hacen

Aquí se prueba la tesis del proyecto: que mirar lo que alguien **hace** predice mejor que leer lo
que **dice**.

Qué se añade:

- La prueba del puesto **cronometrada**, con el reloj corriendo en el servidor — o, si la
  vacante lo eligió, el **cuestionario técnico** que la IA escribe para ella
- El cambio a mitad, con varias variantes y en un minuto que no se puede adivinar
- Los entregables que se piden, cada uno con su regla, y el aviso de que falta alguno
- La explicación de cómo lo hizo
- La rúbrica con pesos, y la IA calificando contra ella
- **El semáforo de alineación**: lo que dijo contra lo que hizo
- La decisión, con sus cinco resultados

⚠️ **Este hito necesita que Renaser reescriba al menos una prueba** en formato de dos horas. Las
cinco que existen no caben en ese tiempo (ver «Decisiones»).
**Qué pregunta responde:** si la evaluación y la prueba dicen lo mismo de una persona. Cuando no
coinciden, ahí está el valor: es el caso que un currículum y una entrevista nunca habrían
detectado.

---

## Lo que queda fuera, y por qué

> **Al 18/08/2026 los tres hitos están construidos, y dos cosas que estaban fuera entraron
> igual: la simulación de trabajo y la validación práctica.** El embudo va de punta a punta.
> Esta sección ya no describe el trabajo pendiente, sino lo que sigue sin construirse.

### Ya no está fuera

| Qué | Qué pasó |
|---|---|
| **Simulación de trabajo** (7 tablas + `sesion_responsable`) | Se construyó el 18/08. El facilitador no hizo falta como rol nuevo: quién puede facilitar es un parámetro editable desde el panel |
| **Validación práctica** | Se construyó el 18/08. La figura contractual sigue sin definirse, pero eso ya no bloquea: solo impide habilitar la modalidad de trabajo productivo. La no productiva se usa desde el primer día |

### Sigue fuera, por decisión ya tomada

| Qué | Por qué |
|---|---|
| **Radar de Talento** (3 tablas) | Decidido el 15/08. Ni las tablas se crearon: entra cuando haga falta. Es el caso de uso de pgvector, que hoy está instalado y sin usar en selección |
| **Seguimiento de desempeño** (2 tablas) | Es la medición de los 90 días. No cabe en la vida del MVP. **Ojo: el gancho tampoco está puesto**, así que cuando entre habrá que añadirlo |

### Sigue fuera, y el cliente lo notaría

Estas **no las decidimos nosotros**: están en «Decisiones», más abajo.

- Evaluador de Estándar, y su opinión escrita
- La repregunta cuando una respuesta es superficial
- La reutilización de respuestas entre puestos de familias afines. El gancho sí está puesto:
  `evaluacion.reutiliza_de_evaluacion_id` existe y espera la regla de qué familias son afines
- El módulo psicométrico propio, que Renaser todavía no ha decidido si se construye

---

## Decisiones

Seis cosas que había que consultar. Dos ya están resueltas y otras tres están en su documento
nuevo, así que apartarse de ellas es decisión suya. La sexta no sale de ningún documento: la
encontramos mirando el código, y no tiene respuesta del cliente todavía.

**1 · ~~¿Se puede crear una vacante directamente en el MVP?~~ RESUELTO EL 15/08: no.** Se
respeta el flujo completo del cliente desde el día 1: la Solicitud de Talento entra en el MVP
(+3 tablas, +1 pantalla), Talento prepara, Dirección aprueba, y solo una solicitud aprobada
admite vacante.

**2 · ¿El Evaluador de Estándar entra en el MVP?** Fue una de las contradicciones que Renaser
resolvió **a su favor**: pasó a poder bloquear, con poder configurable. Si en el MVP no hay
simulación ni validación, su papel se reduce mucho. Nuestra recomendación: dejarlo fuera del MVP
y decidirlo cuando exista la simulación.

**3 · ¿Hace falta la repregunta desde el principio?** Es distintiva, pero añade una ida y vuelta
más al candidato y una llamada más a la IA. Nuestra recomendación: fuera del MVP; se añade sin
tocar nada, porque son dos tablas nuevas colgando de la respuesta.

**4 · Encoger el encargo de la prueba. ~~¿Lleva cronómetro?~~ RESUELTO EL 15/08: sí.** El
cronómetro y el cambio a mitad son la mejora que Renaser quiere. Las cinco pruebas enviadas son
anteriores y valen como modelo de contenido, no de formato.

Pero eso deja una decisión nueva encima de la mesa, y es de Renaser: **una prueba cronometrada
dura de 60 a 120 minutos, y lo que piden esas cinco no cabe ahí.** «Un MVP funcional más un video
de 5 minutos más un documento» no se hace en dos horas. Ni un documento de cinco páginas con
plano e imágenes de referencia.

Ponerle reloj a estas pruebas no es cronometrarlas: es **rehacer el encargo mucho más pequeño**.
Alguien de Renaser tiene que reescribir al menos una, y decidir qué se conserva de la original.
Sin eso el hito 3 no tiene contenido que ejecutar.

**5 · Cómo se puntúa cuando solo existen 70 de los 100 puntos.** En el hito 3 hay Perfil Integral
(40) y prueba (30); faltan simulación y validación (15 y 15). Hay dos formas: mostrar 70 sobre
100 y decir que faltan, o repartir esos 30 entre lo que sí existe. Nuestra recomendación:
**repartir**, porque lo que se está midiendo es si el orden que sale coincide con el que pondría
una persona, y para eso hace falta una escala completa. Queda apuntado en la versión de pesos, así
que cuando entren las dos etapas que faltan es un cambio de datos, no de código.

**6 · ¿Hace falta un ranking general por vacante?** Apuntado el 21/08, y desde el 25/08 medio
respondido. Los **rankings por etapa ya existen**: `GET /panel/vacantes/{id}/ranking?etapa=`
cambia la nota con la que se ordena, sin tocar las ocho notas del currículum de cada fila. Lo
que sigue sin existir es el ranking **general**, el que ordena por la Puntuación Global con las
cuatro etapas dentro. Es lo primero que pregunta cualquiera que ve la pantalla: ¿y el orden con
todo lo demás dentro?
primero que pregunta cualquiera que ve la pantalla: *¿y el orden con todo lo demás dentro?*

La Puntuación Global de RF-113 **ya está calculada**: la nota ponderada de las etapas que pese la
versión de la vacante —cuatro en la v1, dos en la v3— sale en
`GET /panel/postulaciones/{id}/semaforo`. Lo que no existe es una lista ordenada por ella. El
trabajo de código es pequeño; lo que falta son tres decisiones que no son nuestras.

*Qué hay que decidir:*

- **Qué se muestra de quien va a mitad del proceso.** `notaGlobal` viene vacía mientras falte
  cualquier etapa: hoy, un ranking general sería una lista de huecos, porque casi nadie ha
  terminado todas las que pesan. Es el mismo problema del punto 5, visto desde la lista en vez de desde
  el candidato. Si allí se decide repartir, aquí se resuelve solo.
- **Si ordena por nota o por grupo.** El de la etapa 2 manda el grupo por delante del número, a
  propósito: quien saca 92 con un riesgo crítico no va por delante de quien saca 88 sin ninguno.
  Un ranking general que ordene por Puntuación Global pelada pierde justo eso.
- **Quién lo ve.** `/ranking` pide `ver_embudo` y `/semaforo` pide `ver_semaforo_decision`. Una
  lista que junte las dos cosas mezcla dos ámbitos de permiso, y el Jefe del área no debería
  verla igual que Dirección.

*Dos límites que no se negocian:*

- **Tiene que ser por vacante, nunca transversal.** Cada vacante fija su versión de pesos, y dos
  versiones no son comparables: la v1 reparte 40/30/15/15 sobre cuatro etapas y la v3 reparte
  57.14/42.86 sobre dos, porque una etapa no aplicaba (RF-114). Las dos suman 100. Ordenar en una
  misma lista a alguien medido con la v1 y a alguien medido con la v3 es medir con dos varas
  distintas, el mismo fallo que ya nos pasó al mezclar notas de la criba rápida con las finas.
- **Los rankings de la prueba, la simulación y la validación ordenarían notas escritas a mano.**
  Esas tres las califica una persona, criterio por criterio. Un ranking de esas
  etapas no es «lo que dijo la IA»: es lo que teclearon los evaluadores, y estará casi vacío
  mientras el embudo no baje gente hasta ahí.

*Nuestra recomendación:* el ranking general por vacante sí, ordenado igual que el de la etapa 2
—grupo primero, nota después— y en cuanto el punto 5 esté resuelto.

Sobre los rankings por etapa suelta **recomendábamos que no, y se construyeron igual** el 25/08,
porque el equipo necesitaba ordenar por la nota vigente de cada candidato y no por una que para
él ya era vieja. La razón de la recomendación sigue en pie, y pasa de ser un motivo para no
hacerlo a una advertencia para quien monte la pantalla: `?etapa=PRUEBA_PUESTO`, `SIMULACION` y
`VALIDACION` ordenan **notas escritas a mano por los evaluadores**, no de la IA, y estarán casi
vacías mientras el embudo no baje gente hasta ahí. Una lista corta ordenada por notas escasas
invita a decidir con menos evidencia de la que parece. Para comparar a dos personas a fondo
sigue estando el expediente (A13–A18).

---

## Condiciones previas

Sin esto el MVP se puede construir, pero **no se puede saber si funcionó**.

### Hay que medir antes de encender

| Qué medir | Por qué |
|---|---|
| Cuántas horas dedica hoy el equipo por vacante | Sin esto, «ahorra horas» no se puede comparar con nada |
| Cuántas postulaciones reciben hoy por vacante | Hoy se está diseñando para 500 sin saber si son 50 o 5000 |
| Qué tasa de gente que termina la evaluación consideraría buena Renaser | Si de 100 la terminan 15, nadie sabe si eso está bien o mal |

### Insumos que hacen falta del cliente

Los tres primeros son también los del paso 0.

| Insumo | Para qué |
|---|---|
| 10 a 20 currículums reales, sin datos personales, marcados como apto o no apto | Saber si la IA puntúa igual que un reclutador |
| El resultado de las contrataciones pasadas, si aparece | **Hoy no existe.** Sin él, la validez predictiva solo se puede empezar a medir desde el MVP |
| **Pesos para los criterios de «Qué valoraremos»** de al menos una prueba real | Hoy son una lista de 10 a 12 sin números. El sistema exige que una rúbrica publicada sume 100 |
| Entregas reales de esa prueba, **ya corregidas a mano** | Comparar la nota de la IA contra la de una persona con la misma rúbrica |
| 2 o 3 descripciones reales de oferta de empleo | Escribir la rúbrica del currículum con casos reales y no a ciegas |
| El marco de Renaser sobre no victimismo, no culpa y no vergüenza, por escrito | Convertirlo en criterios que se puedan observar |
| El dominio de correo de Renaser, bien configurado | Si los avisos caen en spam, se pierden candidatos que sí querían seguir |
| El texto de consentimiento que nombre a DeepSeek y a Google | **Desde el 18/08/2026 la IA es de fuera y ya lee currículums.** Sin ese texto no puede pasar por ahí el primer candidato real |
| Un tope de gasto para DeepSeek y para Google | La IA ya no corre en una máquina de Renaser: **cada consulta se paga**. Sin saber cuántas postulaciones llegan por vacante, el gasto no se puede estimar |

---

## Lo que se deja preparado para no reescribir después

El cliente cambia de opinión a menudo, así que el MVP se construye para que lo que falta se
enchufe sin romper nada.

| Se deja puesto desde el hito 1 | Si no, después duele |
|---|---|
| La columna de organización en todas las tablas raíz | Añadir multiempresa con datos dentro es una migración larga y arriesgada |
| Los 18 estados completos en el catálogo, aunque en el MVP solo se alcancen unos diez | Añadir estados después obliga a revisar todo lo que ya se movió |
| Los criterios y sus notas, genéricos para cualquier etapa | Así añadir la simulación es meter filas, no tablas |
| La versión de pesos con las cuatro etapas, aunque dos vayan a cero | Cuando entren, es cambiar datos |
| Cada nota atada a la versión con que se calculó | Sin esto no se puede reconstruir una decisión vieja, que es media razón de ser del sistema |
| Los agentes con su versión, su objetivo y su confianza, y cada intento guardado | Es lo que se mira cuando un candidato reclama |

---

## Flujo de Implementación

Lo que hay que hacer fuera del código, en orden:

1. **Pedirle a Renaser los insumos del paso 0** y correr el paso 0. Es lo que decide si vale la
   pena todo lo demás.
2. **Medir la línea base** —horas por vacante, postulaciones por vacante, tasa esperada de
   finalización— antes de encender el hito 1.
3. **Llevar las cuatro decisiones de arriba a Renaser.** Las cuatro son suyas.
4. **Conseguir el dominio de correo** y configurarlo, o los avisos caen en spam.
5. **Aprobar el texto de consentimiento** que nombre a DeepSeek y a Google, antes de que pase
   por ahí el primer candidato real. Hay un borrador en
   [BORRADOR-CONSENTIMIENTO-v1.1.md](BORRADOR-CONSENTIMIENTO-v1.1.md).
6. **Poner un tope de gasto** a las dos cuentas de IA, que ahora cobran por consulta.
7. **Avisar a quien mantiene los mockups** de qué pantallas entran en cada hito, porque el
   inventario de pantallas todavía describe la versión anterior.

---

# Documentos relacionados

| Documento | Qué contiene |
|---|---|
| [Qué hace el sistema](00-QUE-HACE-EL-SISTEMA.md) | El sistema entero, sin nada técnico |
| [Requisitos funcionales](01-REQUISITOS-FUNCIONALES.md) | Qué hace el sistema, etapa por etapa |
| [Estados de la postulación](03-ESTADOS-POSTULACION.md) | Los 18 estados y cómo se pasa de uno a otro |
| [Modelo de datos](05-MODELO-DE-DATOS.md) | Las 93 tablas por área y por qué existe cada una |
| [Inventario de pantallas](06-INVENTARIO-DE-PANTALLAS-MOCKUPS.md) | Las 21 pantallas base |
| [La comprobación](insumos/COMPROBACION-SIN-TECNICA.md) | De dónde salen los conteos de tablas de este documento |
