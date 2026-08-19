# Avance del hito 2 — 17/08/2026

Resumen de lo que se construyó del hito 2 («Que la máquina ordene»). El hito 1 ya estaba
hecho antes de empezar. Este documento es solo un resumen — el detalle completo del modelo
de datos sigue viviendo en [07-DICCIONARIO-DE-DATOS.md](07-DICCIONARIO-DE-DATOS.md) y el
alcance en [08-ALCANCE-DEL-MVP.md](08-ALCANCE-DEL-MVP.md).

## En una frase

**El candidato ya puede responder su evaluación y el sistema la puntúa solo. Lo que falta es
que la inteligencia artificial lea el currículum, califique las respuestas abiertas y arme el
Perfil de Talento.**

> ⚠️ **Actualizado el 18/08/2026: eso ya está hecho.** Los tres agentes se ejecutan, el
> currículum se lee sin foto, edad, sexo ni estado civil, y la postulación avanza sola hasta
> «por confirmar». Lo cuenta [CALIFICACION-CON-IA.md](CALIFICACION-CON-IA.md); lo de abajo se
> conserva como estaba el 17.

---

## Lo que ya funciona

### 1. Las 28 tablas nuevas

Todo lo que el hito 2 necesita para guardar datos: el banco de preguntas, las plantillas de
evaluación, la evaluación de un candidato, el Perfil de Talento con sus hallazgos, y los
agentes de IA con su historial. Se aplicó contra la base real y quedó probado.

### 2. Cuatro paneles de administración

Un administrador ya puede, por la API:

| Qué se administra | Quién puede |
|---|---|
| **Banco de preguntas** — crear versiones, agregar preguntas y opciones, publicar | Talento y Dirección |
| **Plantillas de evaluación** — cuántas preguntas de cada tipo le tocan a un puesto | Talento y Dirección |
| **Pesos** — cómo se reparte la nota entre etapas, componentes, dimensiones y criterios | Solo Dirección |
| **Agentes de IA** — los 9 agentes y sus instrucciones, con versión e historial | Solo Dirección |

### 3. El contenido real del cliente, ya cargado

Hasta ahora se podía administrar un banco **vacío**. Con la migración `V14` entran:

- Las **200 preguntas del Banco Maestro** de Renaser con sus claves de puntuación
  (90 Dirección, 60 Supervisión, 50 Ejecución), importadas del documento del cliente
- Los **pesos por dimensión y nivel**, que suman 100 en los tres niveles
- Los **ocho criterios del currículum** con su peso por nivel
- Una **plantilla de evaluación por nivel**, con sus cuotas
- Las **instrucciones de los tres agentes** de calificación

Se importan con `scripts/importar-banco-maestro.py`, que lee el `.docx` y **emite SQL para
revisar**, no escribe en la base. Copiar 200 preguntas a mano garantiza erratas; el script
además avisa de todo lo que no supo interpretar, y así encontró tres fallos del documento
original (columnas desplazadas, una tabla mal atribuida y títulos de caso que parecían
códigos de dimensión).

### 4. La evaluación del candidato

**Esto es lo que arregla un agujero que había en producción.** Al postular, la postulación
pasaba a «turno del candidato»… y el portal no tenía ninguna pantalla ni endpoint de
evaluación. El candidato quedaba esperando algo que no tenía forma de hacer.

Ahora el camino está completo:

| Endpoint | Qué hace |
|---|---|
| `GET /portal/evaluacion/{codigo}` | Su evaluación, con las preguntas en **su** orden |
| `POST /portal/evaluacion/{codigo}/inicio` | Empezar. La primera vez le arma su examen |
| `PUT /portal/evaluacion/{codigo}/respuestas/{id}` | Guardar una respuesta |
| `POST /portal/evaluacion/{codigo}/entrega` | Entregar, y pasar a calificarse |

Con las reglas del sistema aplicadas por código, no por confianza:

- **El banco no es el examen.** De las 50 preguntas del banco de Ejecución, la plantilla
  elige entre 20 y 27. Cada candidato recibe una selección distinta.
- **Su orden se guarda**, junto con el barajado de las opciones. Es lo que permite reproducir
  el examen exacto meses después.
- **La clave nunca viaja al portal.** Ni puntajes, ni lógica interna, ni códigos de dimensión.
  Los contratos no tienen ese campo, así que no hay forma de que salgan por descuido.
- **Se guarda al momento**: si se corta la conexión, retoma donde quedó.
- **No se entrega a medias**, y una vez entregada no se toca.

Y una regla nueva en el panel: **una vacante no se puede publicar sin plantilla de
evaluación.** El error sale al publicar, no en la cara del primer candidato.

### 5. La calificación que no necesita IA

Al entregar, el sistema puntúa solo lo cerrado: cada opción vale lo que dice su clave
versionada, se compara contra el máximo posible y sale una nota sobre 100. Quedan fuera las
preguntas de estilo, que dibujan un perfil y el cliente prohíbe usar como filtro, y las de
consistencia, que generan alertas.

También detecta contradicciones: si dos preguntas que miden lo mismo se responden con más
diferencia de la tolerada, sale una alerta. **Una alerta no descarta a nadie**: es una
pregunta para la conversación final.

La nota queda atada a la versión de pesos con la que se calculó, así que **no cambia sola**
aunque después se publique otra versión.

### 6. Los pesos suman 100, y ahora hay tests

La comprobación de que una versión de pesos cuadra al publicarse ya existía. Lo que no había
era **ni un solo test del hito 2**. Ahora hay 5 más, que recorren el camino entero: postular,
recibir la evaluación, responderla, entregarla, ver la nota calculada — y comprueban que la
clave no se filtra y que la evaluación de otro responde 404.

**25 tests en verde**, contra 20 antes.

---

## Lo que faltaba: la calificación con IA

**Hecho el 18/08/2026** — ver [CALIFICACION-CON-IA.md](CALIFICACION-CON-IA.md). Era lo único
que quedaba del hito 2:

| Qué | Agente |
|---|---|
| Puntuar el currículum sobre 100 con sus ocho criterios | `EVIDENCIA_CV` |
| Calificar las respuestas abiertas de 0 a 4, con su evidencia citada | `EVALUADOR` |
| Armar el Perfil de Talento y asignar el grupo de prioridad | `POTENCIAL_RIESGO` |

Los tres tienen su instrucción escrita y editable desde el panel, y ya existe el código que
los ejecuta y guarda el resultado.

También estaba pendiente lo que hacía falta antes: **la IA solo puede leer el currículum sin
foto, edad, sexo ni estado civil**. Ahora el sistema saca el texto del archivo y produce esa
versión recortada, y guarda las dos para poder demostrarlo.

### Sobre el «Paso 0»

El documento anterior decía que esta parte estaba bloqueada por un insumo del cliente. **Eso
no era exacto.** El insumo que falta —poner peso a los criterios de una prueba del puesto— es
del **hito 3**. Todo lo que el Perfil Integral necesita ya está escrito en los requisitos, y
las preguntas ya estaban en el Banco Maestro.

Lo que el Paso 0 sí aporta, y sigue pendiente, es **saber si la IA califica parecido a una
persona**. Se puede construir sin él; no se puede *confiar* en el resultado sin él.

### Dos decisiones pendientes

1. **Los umbrales de los cuatro grupos de prioridad no están en ningún documento.** Se
   sembraron como parámetro editable (80 para alta prioridad, 65 para no priorizado) a partir
   de las bandas del Banco Maestro. Conviene que Renaser los confirme.
2. **Dónde corre la IA que lee a un candidato.** Hoy se usa un servicio externo. Los textos de
   consentimiento tienen que decirlo, y hoy siguen siendo provisionales. No frena el
   desarrollo, pero sí frena usarlo con gente real.

---

## Dónde está el código

- `perfilintegral` — el banco, las plantillas, la evaluación y la calificación
- `pesos` — la administración de pesos
- `ai.controller.perfilintegral` — la administración de agentes, separada del motor de
  agentes para no tocarlo por accidente

Las migraciones nuevas son `V10` a `V14` (V1 a V9 son del hito 1, no se tocaron).

## Cómo probarlo

1. Levantar la app (necesita Postgres y RabbitMQ de `docker-compose.yml`).
2. Entrar con `POST /api/v1/panel/auth/dev-login`.
3. Los endpoints están en Swagger (`/swagger-ui.html`).

O directamente: `./mvnw test`, que recorre el camino completo contra una base real.
