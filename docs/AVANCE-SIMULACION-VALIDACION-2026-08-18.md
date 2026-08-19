# Simulación y Validación práctica — 18/08/2026

Las dos etapas que estaban fuera del MVP. Con esto **el embudo queda completo de punta a
punta**: postulación → Perfil Integral → prueba del puesto → simulación → validación →
decisión, sin ningún salto manual obligado.

## En una frase

**Se construyeron las dos etapas que faltaban, y desapareció el parche que las tapaba.** Lo que
sigue pendiente no es código: es un facilitador designado y la figura contractual definitiva.

---

## Por qué se pudieron construir ahora

No estaban «pendientes»: estaban **fuera del MVP a propósito**, cada una por un motivo
distinto, y ninguno era técnico.

| | Por qué estaba fuera | Qué lo destrabó |
|---|---|---|
| **Simulación** | Necesita un facilitador, sala y gente coordinada | No hace falta un rol nuevo: la documentación ya reparte esas acciones entre Talento, Responsable de área y Dirección |
| **Validación** | Renaser no ha definido la figura contractual | Entra un valor provisional, y el sistema sigue impidiendo el trabajo real sin figura registrada |

El facilitador quedó resuelto como pediste: **configurable desde el panel**. Dos parámetros
nuevos deciden quién puede hacer qué, y cambiarlos es editar una fila, sin desplegar nada:

- `roles_facilitador_simulacion` — arranca en `TALENTO,DIRECCION`
- `roles_completan_metricas_validacion` — arranca en `TALENTO,RESPONSABLE_AREA`, que es
  literalmente lo que pide el documento: *«puede ser solo el responsable del área, solo Talento,
  o ambos. Arranca con ambos habilitados»*

---

## Lo que ya funciona

### 1. Nueve tablas nuevas

Las siete de simulación (`sesion_simulacion`, `sesion_vacante`, `inscripcion_sesion`,
`tramo_simulacion`, `informacion_critica`, `marca_tiempo_simulacion`, `pregunta_generada`), la
de validación, y una que el esquema no tenía: **`sesion_responsable`**. RF-91 habla de
«responsables» en plural y solo se guardaba quién creó la sesión, que no es lo mismo que quién
la conduce.

Las notas reutilizan `criterio`/`peso_criterio`/`nota_criterio` sin cambio de esquema: los diez
criterios de simulación (RF-100) y las nueve métricas de validación (RF-109) entran como
criterios globales, igual que los ocho del currículum.

### 2. Las sesiones, con sus tres reglas automáticas

El equipo crea las sesiones que necesite, con fecha, cupo, modalidad y sede o enlace. Y el
estado del candidato se mueve solo, en las dos direcciones:

| Cuándo | Qué pasa |
|---|---|
| Se publica una sesión o se amplía su cupo | Los que esperaban pasan a elegir, y se les avisa |
| Se llena la última sesión | Los que no se inscribieron vuelven a esperar |
| Se cancela una sesión | Sus inscritos vuelven a elegir, o a esperar si no queda ninguna |

**Es el único punto del sistema donde el estado de una postulación depende de lo que pase en otra
tabla**, y es donde más fácil se cuela un error silencioso: un candidato esperando una sesión que
ya existe no genera ningún error, simplemente no avanza. Por eso las tres reglas tienen test
propio, con dos candidatos peleando por una sola plaza.

**Cancelar no borra el historial**: la inscripción vieja queda marcada como no vigente, así no
parece que esa persona nunca eligió fecha.

### 3. El facilitador marca lo que ocurre, y solo lo observable

Los diez eventos de la sesión —inicio, primera pregunta, aparece el cambio, lo abre, entrega,
autocrítica…— se marcan en vivo. Marcar dos veces el mismo evento corrige la hora, no duplica.

⚠️ **Solo se registra lo que alguien hizo, nunca lo que se supone que pensó.** El evento
«detectó el bloqueo» se eliminó del modelo a propósito: el cliente lo prohíbe por escrito
(RF-98). Lo que queda es «apareció el cambio» y «lo abrió», que son dos actos que se pueden ver.
Un evento fuera de la lista de diez se rechaza.

### 4. Faltar a la sesión no reinscribe solo

Si no asistió, la inscripción guarda que no asistió y la postulación vuelve a la bandeja del
equipo. **Nadie le da otra fecha automáticamente**: una persona decide entre darle otra
oportunidad o cerrar su postulación, con su motivo escrito. Tiene permiso propio
(`decidir_sobre_ausente`), y el responsable de área no lo tiene — tal como dice la matriz.

### 5. La validación, con su regla legal intacta

- `SIMULACION_EXTENDIDA` se puede usar desde el primer día.
- `TRABAJO_REAL` **no se habilita sin figura contractual registrada**. Es la regla que impide que
  una aceptación digital sustituya una obligación legal, y está comprobada tanto en la base
  (CHECK) como en el servicio, con mensaje claro.
- Los días arrancan en 7 y son configurables por periodo.
- Un periodo vencido **no cierra la postulación**: la pasa a esperar a que una persona complete
  las métricas. Cerrarla del todo es una decisión, no un vencimiento.
- De cada métrica queda registrado **de dónde salió** (RF-111). Hoy todo entra como `PERSONA`;
  el campo está listo para cuando exista la integración con RENASER OS que las alimente solas.

### 6. Dos bugs reales encontrados y corregidos

Ambos del hito 2, y ambos habrían dado problemas silenciosos:

**`validarSumas` agrupaba los pesos de criterio solo por nivel de puesto.** Con tres etapas
calificándose por criterio, una versión de pesos perfectamente válida sumaría 300 y sería
rechazada al publicarse. Ahora agrupa por nivel **y etapa**, que es lo que corresponde: cada
rúbrica suma 100 por sí sola.

**El máximo de un criterio se leía siempre de `criterio.puntos`**, que solo lo tienen los de la
prueba del puesto. Los globales —currículum, simulación, validación— llevan su peso en
`peso_criterio`, porque valen distinto según el nivel. Se resolvió con un método que sabe mirar
en los dos sitios.

### 7. Diez pruebas nuevas

**42 tests en verde**, contra 32 antes. Las nuevas cubren el camino completo y, sobre todo, lo
que más fácil se rompe: las tres reglas de disponibilidad con dos candidatos y una plaza, el
ausente que no se reinscribe, el evento inventado que se rechaza, el trabajo real sin figura
contractual, y el facilitador que no puede marcar hasta que se añade su rol al parámetro —sin
recompilar nada.

---

## Lo que falta

### De estas dos etapas: nada de código

Lo que falta es operativo, y no lo resuelve programar:

- **Un facilitador designado y entrenado.** El sistema ya sabe quién puede facilitar; hace falta
  que alguien lo sea y sepa qué anotar.
- **La matriz de información crítica de una sesión real.** Igual que el contenido de la prueba:
  el sistema la soporta, el contenido lo pone Renaser.
- **La figura contractual definitiva.** Hoy hay un valor provisional; cuando Renaser decida, es
  cambiar un dato.

### Del sistema completo

> **Al cierre del 18/08/2026 esta lista encogió.** Los tres agentes del Perfil Integral
> —evidencia del currículum, evaluador, y potencial y riesgo— ya corren de verdad contra
> DeepSeek, y con ellos entró la extracción y anonimización del currículum. Lo cuenta
> [Calificación con IA](CALIFICACION-CON-IA.md). Queda lo de abajo.

- **Los dos agentes que faltan**: el que califica la prueba del puesto y el que genera las
  preguntas de la conversación final de la simulación. Existen como filas del catálogo, sin
  instrucción sembrada ni clase. Hoy las dos cosas las hace una persona a mano, en las mismas
  tablas que usarán los agentes.
- **El contenido real de una prueba del puesto**, que quedó pendiente de que Renaser reescriba
  una en formato de dos horas.
- Lo que está fuera del MVP por decisión ya tomada: Radar de Talento, seguimiento de desempeño a
  90 días, Evaluador de Estándar, repregunta y reutilización entre familias afines.

---

## Una nota sobre los pesos

La `v3` que creé para el hito 3 repartía entre dos etapas los 30 puntos de simulación y
validación, porque esas etapas no existían y había que puntuar sobre 100 igual. Ahora que
existen, ese apaño sobra: la **`v4`** devuelve el reparto original **40/30/15/15** y las
vacantes migran a ella. La v3 no se toca — una versión publicada es inmutable, y las
postulaciones que se calificaron con ella conservan su nota tal como se calculó.

## Dónde está el código

- `simulacion` — sesiones, inscripciones, eventos y conversación final
- `validacion` — el periodo y sus métricas
- `perfilintegral.service.CalificacionPorCriterio` — la calificación por rúbrica, ahora
  compartida por las tres etapas que la usan
- La migración nueva es `V18`. Nació como `V16`, pero ese número ya lo ocupaban dos
  migraciones de la rama de agentes (`V16` del CV anonimizado y `V17` de los pesos), y
  Flyway no arranca con dos del mismo número.
