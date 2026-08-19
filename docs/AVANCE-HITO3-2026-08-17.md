# Avance del hito 3 — 17/08/2026

Resumen de lo que se construyó del hito 3 («Que se vea lo que hacen»). Los hitos 1 y 2
—salvo la calificación con IA, que construirá Ricardo— ya estaban hechos antes de
empezar. El detalle completo del modelo de datos sigue viviendo en
[07-DICCIONARIO-DE-DATOS.md](07-DICCIONARIO-DE-DATOS.md) y el alcance en
[08-ALCANCE-DEL-MVP.md](08-ALCANCE-DEL-MVP.md).

## En una frase

**Un candidato ya puede rendir la prueba del puesto con su cronómetro real, y una
persona puede calificarla y tomar la decisión final. Lo que falta es que la IA
califique la rúbrica sola, y el contenido real de una prueba — eso último depende de
que Renaser reescriba una prueba a formato de dos horas.**

---

## Alcance: qué entró y qué se dejó fuera, y por qué

El hito 3, tal como lo define la escalera de tres hitos, es **la prueba del puesto y la
decisión final** — no las cinco etapas completas del sistema. Quedan fuera, deliberada
y explícitamente, por las mismas razones que ya estaban documentadas:

| Qué | Por qué queda fuera |
|---|---|
| **Simulación de trabajo** (7 tablas) | Es caro de operar, no de programar: necesita sesiones con cupo, facilitador y gente coordinada |
| **Validación práctica** | Bloqueada: Renaser no ha definido la figura contractual del periodo productivo |
| **Evaluador de Estándar** | Ya se había acordado dejarlo para cuando exista Simulación |

Esto crea un hueco real en la máquina de estados que había que resolver: sin
Simulación ni Validación construidas, «confirmar avance» desde `PRUEBA_POR_CONFIRMAR`
calcularía `SIMULACION_POR_HABILITAR` — una etapa que no existe. **Se decidió no
tocar la máquina de estados**, que está bien probada y es la misma que usarán
Simulación y Validación el día que se construyan. En su lugar, `confirmarAvance`
ahora reconoce cuándo el siguiente paso calculado cae en una etapa que todavía no
existe, y falla con un mensaje que dice exactamente qué hacer: usar la transición
manual (que ya existía) directo a `DECISION_POR_CONFIRMAR`, con un motivo escrito.
Ningún candidato queda esperando en un estado fantasma.

---

## Lo que ya funciona

### 1. Trece tablas nuevas

`plantilla_prueba`, `version_plantilla_prueba`, `variante_cambio`, `pregunta_prueba`,
`pregunta_version_plantilla`, `entregable_requerido`, `intento_prueba`, `entregable`,
`respuesta_prueba`, `barrera_critica`, `decision`, `barrera_detectada`,
`evidencia_adicional`. Se aplicaron contra la base real y quedaron probadas.

La rúbrica de la prueba **no necesitó una tabla nueva**: reutiliza `criterio` y
`peso_criterio`, que ya estaban diseñados para colgar de una versión de plantilla de
prueba desde el hito 2.

### 2. La administración de la prueba

Talento (o Dirección) ya puede, por la API: crear una plantilla, darle una versión con
su enunciado y modalidad, agregar las variantes del cambio inesperado, elegir sus
preguntas del catálogo (previas, universales, específicas), definir los entregables que
pide —cada uno con su regla— y montar la rúbrica. Con las reglas del sistema aplicadas
por código:

- **Entre 8 y 10 preguntas universales y entre 3 y 5 específicas** (RF-83). Publicar
  con menos o más no pasa.
- **La rúbrica suma 100**, y se comprueba al publicar, no al guardar el borrador
  (RF-89): se puede ir armando a medias sin que el sistema se queje en cada paso.
- **Una prueba cronometrada dura de 60 a 120 minutos** (RF-76).

### 3. El candidato rinde la prueba con su cronómetro real

El agujero que se cerró para el hito 2 —postular y quedar esperando algo sin
endpoint— no se repitió aquí: se construyó completo desde el principio.

| Endpoint | Qué hace |
|---|---|
| `GET /portal/prueba/{codigo}` | Su prueba: enunciado, entregables pedidos, sus respuestas |
| `POST /portal/prueba/{codigo}/inicio` | Empezar. Arranca el reloj y sortea la variante del cambio y el minuto exacto |
| `PUT /portal/prueba/{codigo}/respuestas/{id}` | Responder una pregunta |
| `POST /portal/prueba/{codigo}/entregables/{id}/archivo` \| `/enlace` | Subir un entregable |
| `POST /portal/prueba/{codigo}/entrega` | Entregar. Exige los obligatorios |

- **El reloj lo lleva el servidor.** `venceEn` se calcula y se guarda al empezar; no
  hay pausas, cerrar la página no lo detiene.
- **El cambio inesperado no viaja de antemano.** Se sortea un minuto dentro del rango
  configurado, y solo aparece en la respuesta cuando ya toca — nunca antes (RF-77).
- **No existe entregar tarde.** Un sondeo programado (cada minuto) entrega solo los
  intentos vencidos, marcándolos como automáticos.

### 4. Calificar la rúbrica, criterio a criterio

A diferencia de las preguntas cerradas del hito 2 —que se puntúan solas contra una
clave—, la rúbrica de la prueba (RF-85) es mayormente cualitativa: comprensión,
calidad, criterio, capacidad de explicar. **No hay una fórmula que la calcule sola.**

Lo que sí es determinístico es *ponderar* lo que ya se calificó, y eso es lo que se
construyó: una persona pone la nota de cada criterio con su explicación obligatoria, y
el sistema suma los puntos —que ya están pensados para sumar 100— en cuanto están
todos. Si falta alguno, lo dice con nombre y apellido, no deja calcular a medias.

### 5. La decisión final

- `GET /panel/postulaciones/{id}/semaforo` — la Puntuación Global (suma ponderada de
  las notas de etapa que existan) y una propuesta de semáforo, si hay con qué calcular.
- `POST /panel/postulaciones/{id}/decision` — la decisión de una persona, con motivo
  siempre obligatorio (RF-119). Contratar transiciona a `CONTRATADO`; rojo o reserva
  cierran la postulación; ámbar la manda de vuelta al candidato a esperar evidencia
  adicional.
- `POST /panel/vacantes/{id}/barreras-criticas` y
  `POST /panel/postulaciones/{id}/barreras-detectadas` — las barreras que ningún
  promedio alto compensa (RF-115). Sin IA todavía, una persona las reporta
  directamente, ya confirmadas: no hay «detección pendiente» que confirmar.
- `POST /panel/postulaciones/{id}/evidencia-adicional` — pedir una ronda más cuando
  sale ámbar, con el tope configurable (2 por defecto) que ya existía.

**Quién decide, y quién no.** RF-119 es explícito: la decisión de contratar es del
responsable del área o de Dirección, **nunca de Talento**. Es la primera vez en el
sistema que Talento no tiene el permiso de escritura más importante de un flujo, y el
test de integración prueba exactamente eso: con el token de Talento, decidir da 403.

### 6. Un bug de verdad encontrado y corregido

Al construir la decisión —que necesita los pesos exactos de la vacante para calcular
la Puntuación Global— se encontró que la calificación del hito 2 usaba **la última
versión de pesos publicada en la organización**, no la que la vacante tiene fijada.
RF-114 es explícito: *"la vacante debe tener una versión de pesos aprobada antes de
que empiecen los candidatos. Nunca se redistribuyen pesos a mano por persona."* Si se
publicara una versión nueva mientras hay candidatos en proceso con una vacante vieja,
sus notas se habrían calculado con el reparto equivocado. Ya está corregido, y la
prueba de la prueba (con dos versiones de pesos distintas conviviendo, `v2` y `v3`)
lo habría hecho fallar si hubiera vuelto a pasar.

### 7. Siete pruebas nuevas, con lo más delicado cubierto

El camino completo: armar una prueba con su rúbrica → publicar una vacante que la
exige → un candidato la rinde con entregable obligatorio → calificarla → el salto
manual a Decisión → la decisión final → verificación de que un intento vencido se
entrega solo. Más el guardia del callejón sin salida, y que Talento no pueda decidir.

**32 tests en verde**, contra 25 antes.

---

## Lo que falta

### La calificación con IA

Es lo único que falta para que la prueba se autocalifique: el agente
`PRUEBA_PUESTO`, ya sembrado y con su papel descrito (RF-144: *"Analiza entregables y
su defensa con la rúbrica, usando los verificadores objetivos que existan"*), pero sin
código que lo ejecute — la misma situación que los tres agentes del hito 2.

### El contenido real de una prueba

**Este es el bloqueo real del cliente**, y es distinto al del hito 2. Las cinco
pruebas que Renaser ya tiene (`insumos/pruebas-tecnicas/`) no caben en el formato de
60 a 120 minutos: piden entregables como *"un MVP funcional más un video de 5 minutos
más un documento"*, que no se hacen en dos horas. **Alguien de Renaser tiene que
reescribir al menos una prueba, mucho más pequeña**, antes de que haya algo real que
un candidato pueda rendir. Sin eso, todo lo construido aquí queda probado con
contenido de prueba, no con el contenido que se usará de verdad.

### Simulación y Validación práctica

Sin construir, por las razones ya explicadas arriba. El hueco que dejan en la máquina
de estados ya está resuelto: el salto manual a Decisión funciona hoy, y cuando se
construyan, quitar el guardia de `confirmarAvance` es un cambio de una línea.

---

## Dónde está el código

- `prueba` — la plantilla, la rendición del candidato y la calificación por criterio
- `decision` — barreras críticas, el semáforo y la decisión final
- `comun.programado` — el sondeo que cierra evaluaciones y pruebas vencidas

La migración nueva es `V15` (V1 a V14 son de los hitos 1 y 2, no se tocaron).

## Cómo probarlo

`./mvnw test` recorre el camino completo contra una base real, incluido el
vencimiento del cronómetro. O `POST /api/v1/panel/auth/dev-login` y los endpoints
están en Swagger (`/swagger-ui.html`).
