# La rúbrica de la prueba del puesto

Cómo se reparten los 100 puntos de una prueba, y **quién pone cada nota**: el sistema, un
agente de IA o una persona.

Este documento existe porque esa segunda pregunta se decide una vez y se hereda en todas las
convocatorias. Si se elige mal, o la IA califica cosas que no puede ver, o una persona termina
poniendo a mano cien notas que la máquina podía poner sola.

---

## Los 100 puntos

Es la rúbrica base que define el documento de requisitos (ver «Puntuación de la prueba» en los
requisitos funcionales). Cada puesto puede tener la suya, pero **también tiene que sumar 100**,
y queda versionada: una prueba ya calificada conserva la rúbrica con la que se calificó.

| Qué se mide | Puntos | Quién pone la nota |
|---|---:|---|
| Resultado producido | 25 | **Agente**, y una persona lo revisa en los finalistas |
| Calidad | 15 | **Agente** |
| Comprensión del problema | 10 | **Agente** |
| Velocidad y manejo del tiempo | 10 | **Sistema** |
| Criterio en las decisiones | 10 | **Agente** |
| Capacidad de explicar lo hecho | 10 | **Agente** |
| Uso inteligente y verificado de IA y herramientas | 5 | **Agente** |
| Orientación a resultados medibles | 5 | **Agente** |
| Adaptación al cambio | 5 | **Persona** |
| Aprendizaje | 5 | **Persona** |

**70 puntos los pone la IA, 20 una persona, 10 el sistema.** Y el trozo más pesado —los 25 del
resultado— pasa por ojo humano antes de decidir.

---

## Por qué cada uno donde está

La regla de fondo es una sola: **no se le pide a la IA que juzgue lo que no puede ver.** El
documento de requisitos lo dice con estas palabras: «no se asume que la IA puede observar todo
con igual fiabilidad».

### Lo que mide el sistema

**Velocidad y manejo del tiempo.** El cronómetro ya está corriendo en el servidor: sabe cuándo
empezó, cuándo entregó y cuánto tardó en cada tramo. Es un dato exacto. Pedirle a un modelo que
opine sobre la velocidad de alguien, teniendo el reloj al lado, sería cambiar un número cierto
por una impresión.

### Lo que califica un agente

Todo lo que se lee del entregable y de lo que el candidato escribió para defenderlo:

- **Resultado producido, Calidad, Comprensión del problema** salen de mirar lo entregado.
- **Criterio en las decisiones** y **Capacidad de explicar lo hecho** salen de sus respuestas a
  las preguntas posteriores. Es argumentación escrita, que es el ejemplo que el propio
  documento pone como trabajo de agente.
- **Uso inteligente de IA** y **Orientación a resultados medibles** son comprobables leyendo:
  qué herramientas declaró, qué dato corrigió, qué números puso.

El agente **cita la evidencia de cada nota**. Una nota sin explicación no se guarda.

### Lo que se queda con una persona

**Adaptación al cambio.** Mide cómo reaccionó al giro inesperado a mitad de la prueba. Hoy
ninguna plantilla declara esa variante, así que **no hay nada que observar**: el agente no
tendría de dónde sacar la nota y se la inventaría. Vuelve a la IA el día que las pruebas
declaren su cambio.

**Aprendizaje.** Es qué se llevó de la experiencia, y se ve mejor conversando que leyendo.

### Y por qué el resultado lo revisa alguien

Son 25 de 100 puntos: es el criterio que más mueve la nota final. El documento pide revisión
humana en los finalistas justamente donde más pesa. Una persona puede cambiar cualquier nota
que puso un agente, **siempre justificando el cambio**, y queda auditado.

---

## Lo que hay hoy en la base, y por qué no es esto

⚠️ **Las seis versiones de plantilla que hay cargadas tienen otra rúbrica**, de cuatro
criterios, y **los cuatro marcados «persona»**:

| Criterio | Puntos |
|---|---:|
| Criterio de descarte | 40 |
| Uso de la evidencia | 30 |
| Calidad de las preguntas | 20 |
| Claridad | 10 |

No es una decisión que alguien tomara. Viene del script de demostración
`scripts/cargar-convocatoria.py`, que escribe `"metodoVerificacion": "PERSONA"` fijo para todos
los criterios que crea. Es la rúbrica de una prueba de ejemplo —la de «ordena veinte
currículums»—, no la del cliente.

**Consecuencia:** el agente que califica la prueba solo mira los criterios marcados «agente».
Con la configuración de hoy arranca, no encuentra ninguno suyo, y no llama al modelo. No falla:
sencillamente no tiene nada que hacer.

---

## Cómo se cambia

**No se edita la rúbrica de una versión ya publicada.** Es a propósito: cambiarla movería la
nota de quien ya fue calificado con ella. Se crea una versión nueva de la plantilla, se le carga
esta rúbrica, se publica y la vacante pasa a apuntar a la nueva. La anterior se queda como está,
con sus candidatos y sus notas intactos.

Al publicar, el sistema **exige que sume 100** y no deja continuar si no cuadra. Mientras la
versión está en borrador sí deja guardar sin cuadrar, porque uno puede estar a mitad de un
ajuste.

---

## Lo que sigue faltando, y no es código

- **La rúbrica propia de cada puesto.** La de arriba es la base. Un ingeniero civil y un
  arquitecto no se miden igual, y el documento permite que cada puesto tenga la suya.
- **La variante de cambio.** Mientras ninguna prueba declare el giro inesperado, «Adaptación al
  cambio» seguirá siendo cinco puntos que pone una persona a ojo.
- **Que Renaser firme estos números.** El reparto de 100 puntos viene del documento de
  requisitos; el reparto de *quién califica qué* es propuesta nuestra y hay que confirmarla.

---

Ver también: [Calificación con IA](CALIFICACION-CON-IA.md) y los requisitos funcionales
(secciones «Puntuación de la prueba» y «Los agentes»).
