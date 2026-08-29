# La prueba técnica — Etapa 2

La segunda de las dos pruebas del sistema CAZATALENTOS. **No es fija**: no existe hasta que
existe una vacante, y se escribe para esa vacante.

Este documento está separado del [banco RENASER](CAZATALENTOS-BANCO-RENASER.md) porque las
dos etapas no se construyen igual, no se mantienen igual y no cuestan igual. El banco se
escribe una vez y sirve para siempre. **Esta prueba se escribe cada vez**, y esa diferencia
es la que decide el tamaño del proyecto.

Fuente: `CAZATALENTOS-DIR.xlsx`, `-SUP.xlsx`, `-OPE.xlsx` (hojas «Prueba técnica»,
«Textura», «Vacante», «Repreguntas», «Cálculo») y `CAZATALENTOS-sistema-de-filtro.md`,
partes 6 a 8.

---

## Qué es y qué no es

| | |
|---|---|
| Varía por | **Puesto** × **Tipo de empresa** × Nivel |
| Cuántas preguntas | DIR 12 · SUP 10 · OPE 8 |
| Quién la redacta | **Una persona, con el dueño del negocio, en 30 minutos** |
| Cuándo se envía | **Solo a quien aprobó la prueba RENASER** |
| Corte | Índice ≥ 65 **y** eliminatoria con textura ≥ 6 términos |

**Esto no es un problema de software: es un problema de proceso.** Cada vacante necesita
media hora con el dueño antes de que exista una sola pregunta. El sistema puede guardar,
estructurar y versionar el resultado de esa media hora — no puede sustituirla.

---

## La regla de oro: dos entradas, no una

| Entrada | Qué define | Ejemplo |
|---|---|---|
| **PUESTO** | Los riesgos del cargo | Administrador → caja, personal, control |
| **TIPO DE EMPRESA** | El contenido de esos riesgos | Casa de cambio → divisas y tipo de cambio · Retail → inventario y mermas · Constructora → valorizaciones y avance de obra |

**El mismo puesto en dos empresas comparte la estructura y cambia el contenido.** Un
administrador de casa de cambio y uno de una clínica responden ambos sobre caja y personal,
pero la pregunta de margen es sobre tipo de cambio en uno y sobre convenios y tarifas en el
otro.

---

## La ficha de vacante: la entrada obligatoria

Se llena **con el dueño, hablando, antes de evaluar a nadie**. Sin lenguaje de recursos
humanos y con sus palabras. 20 minutos.

| Campo | Lo que se le pregunta al dueño | Qué produce |
|---|---|---|
| **Q1 Resultado** | Dentro de un año, ¿qué tiene que haber pasado para que digas que contratar a esta persona fue un acierto? Dímelo en números si se puede. | Resultado esperado. Base de la muestra de trabajo |
| **Q2 Riesgo** | Si contratas a la persona equivocada, ¿en qué te vas a dar cuenta primero? ¿Qué es lo primero que empieza a fallar? | **Los riesgos de la prueba, ordenados por velocidad de daño** |
| **Q3 Día real** | Descríbeme un día normal de esa persona, de principio a fin. ¿Qué hace durante ocho horas? | Confirma la familia y el nivel |
| **Q4 Época dorada** | ¿Alguien ocupó este puesto antes y lo hizo bien? ¿Qué hacía que las demás no hicieron? ¿Y el que lo hizo mal, en qué falló? | El perfil real del puesto y la segunda eliminatoria |
| **Q5 Estructura** | ¿Cuánta gente hay en la empresa? ¿Cuántas va a tener a cargo? ¿Alguna de ellas tiene gente a su cargo? | **NIVEL** (DIR/SUP/OPE) y **TAMAÑO** (MICRO/MEDIA/GRANDE) |
| **Q6 Autonomía** | ¿Qué va a poder decidir sin preguntarte a ti? | Confirma el nivel real del puesto |
| **Q7 Jefe directo** | ¿A quién le va a reportar? ¿Cómo trabaja esa persona? ¿Qué tipo de persona no funciona con ese jefe? | Se valida en la entrevista humana |
| **Q8 Lo incómodo** | ¿Qué tiene de difícil o incómodo este puesto que un candidato debería saber antes de aceptar? | Se le dice al candidato. Evita salidas tempranas |
| **Q9 Requerimientos** | ¿Hay algo específico que tenga que saber o poder hacer, sin lo cual no sirve? (máximo 3) | Las preguntas de requerimiento |
| **Q10 Espejo** | Si ya contrataste antes para este puesto y no funcionó, ¿qué crees TÚ que falló en la elección? | Evidencia. Se escucha sin corregir |

### Lo que la ficha tiene que producir

| Salida | Valores |
|---|---|
| **Nivel** | DIR / SUP / OPE |
| **Familia** | F1 a F7 (puede ser más de una) — decide qué diccionario de textura se usa |
| **Tamaño** | MICRO (≤30) / MEDIA (31–200) / GRANDE (200+) — decide los pesos de pilar de la etapa 1 |
| **Eliminatoria 1** | Validar: «si fuera excelente en todo menos en esto, ¿lo contratarías igual?» Si dice NO, es eliminatoria |
| **Eliminatoria 2** | **Máximo 2.** Si el dueño marca más, repetir la validación hasta reducir a 2 |

⚠️ **El TAMAÑO sale de aquí y lo consume la etapa 1.** Es la única dependencia de la etapa 2
hacia atrás: los pesos de pilar de DIR cambian según sea MICRO o MEDIA/GRANDE.

---

## Cómo se genera — 30 minutos con el dueño

**Paso 1 · Riesgos.** *«Si contratas a la persona equivocada, ¿en qué te vas a dar cuenta
primero?»* Listar 4 riesgos y **ordenarlos por velocidad de daño**, no por importancia.

**Paso 2 · Dos preguntas por riesgo.**
- **Experiencia:** *«¿Has tenido responsabilidad sobre [riesgo]? Indica [magnitudes].»*
- **Procedimiento:** *«Si ocurre [peor escenario], ¿cuál sería tu procedimiento exacto?»*

> **La de procedimiento no se puede fingir:** quien lo hizo lo describe paso a paso; quien lo
> leyó dice «revisaría bien».

**Paso 3 · Rubro.** Insertar en cada pregunta el vocabulario del rubro de la empresa. Es lo
que la vuelve específica y lo que activa el diccionario de textura.

**Paso 4 · Dilema.** Una pregunta donde **dos virtudes del negocio se contradicen**. No hay
respuesta correcta; hay criterio.

**Paso 5 · Muestra de trabajo.** Se marca `PRESENCIAL` y **nunca se envía en el formulario**:
regala el diagnóstico del negocio a todo el que postule.

---

## La estructura fija

| Bloque | DIR (12) | SUP (10) | OPE (8) | Cómo se genera |
|---|---:|---:|---:|---|
| Experiencia y escala | 2 | 2 | 2 | Empresa · cargo · tiempo · trabajadores · sedes · volumen · de qué respondía |
| Riesgo 1 (el más rápido) | 3 | 3 | 2 | Una de experiencia con magnitudes, una de procedimiento exacto, una de control preventivo |
| Riesgo 2 | 2 | 2 | 2 | Una de experiencia con magnitudes y una de procedimiento exacto |
| Riesgo 3 | 2 | 1 | 1 | Una de experiencia con magnitudes |
| Requerimientos específicos | 1 | 1 | — | De lo que el dueño marcó como imprescindible (Q9) |
| Dilema del negocio | 1 | 1 | 1 | Dos virtudes del negocio que se contradicen |
| Muestra de trabajo `PRESENCIAL` | 1 | — | — | Nunca se envía en el formulario |

**La estructura no se negocia; el contenido sí.** Es lo que permite comparar candidatos de
puestos distintos sin comparar peras con manzanas.

---

## Referencia completa: Administrador · casa de cambio · 3 sedes · MICRO (DIR, 12)

*Riesgos ordenados por velocidad de daño: caja → margen en divisas → control a distancia →
personal.*

| Código | Bloque | Pregunta |
|---|---|---|
| **T01** | Experiencia y escala | ¿Cuántos años llevas administrando empresas o unidades operativas? Indica: empresa · cargo · tiempo · número de trabajadores · número de sedes · de qué respondías. |
| **T02** | Experiencia y escala | ¿Cuál es la operación más grande que has administrado directamente? Número de trabajadores, sedes, volumen de dinero administrado y operaciones por día. |
| **T03** | Riesgo 1 — Caja y efectivo | ¿Has tenido responsabilidad directa sobre cajas, efectivo o bancos? Explica qué controlabas y de qué monto o volumen hablamos. |
| **T04** | Riesgo 1 — Caja y efectivo | Explícanos paso a paso cómo hacías un cierre y cuadre de caja: qué revisabas, con qué documentos, cómo identificabas diferencias. |
| **T05** | Riesgo 1 — Caja y efectivo | Si al cerrar el día una sede presenta un faltante, ¿cuál sería tu procedimiento exacto para determinar qué ocurrió? |
| **T06** | Riesgo 2 — Margen en divisas | ¿Has trabajado en casa de cambio, empresa financiera o negocio con manejo intensivo de efectivo? Indica volumen aproximado de operaciones. Si no, explica qué experiencia consideras transferible. |
| **T07** | Riesgo 2 — Margen en divisas | Si una sede sigue operando pero su margen está bajando, ¿qué información revisas primero para encontrar la causa? |
| **T08** | Riesgo 3 — Control a distancia | ¿Has administrado más de una sede a la vez? Cuántas, a qué distancia, cuántos trabajadores y cómo hacías el control. |
| **T09** | Riesgo 3 — Control a distancia | Si tuvieras tres sedes y no pudieras estar en todas todos los días, ¿cómo organizarías la supervisión? Qué indicadores, con qué frecuencia y qué información exigirías. |
| **T10** | Requerimiento | ¿Qué experiencia tienes coordinando con contadores y revisando información contable? ¿Qué reportes revisabas personalmente y qué decisión tomabas con ellos? |
| **T11** | **DILEMA** | Un trabajador de una sede tiene los mejores resultados comerciales, pero constantemente comete errores de caja e incumple procedimientos. ¿Cómo actúas y qué evalúas? |
| **T12** | **PRESENCIAL — no se envía** | Si asumieras la administración y te pidiéramos aumentar la rentabilidad de las tres sedes en 12 meses, ¿qué necesitarías conocer en tus primeros 30 días antes de proponer un plan? |

---

## Cómo se puntúa

Se usa **el mismo método de la etapa 1**: 0 a 4 contando C1 (episodio), C2 (autoría),
C3 (dato duro) y C4 (la parte incómoda), con la señal de 0 como compuerta previa. El método
completo está en [el documento del banco](CAZATALENTOS-BANCO-RENASER.md).

```
Índice técnico = puntos obtenidos ÷ (4 × n.º de preguntas) × 100
```

Encima de eso, esta etapa tiene un mecanismo propio que la etapa 1 no tiene: **la textura**.

---

## Los diccionarios de textura

**Se cuentan los términos que aparecen espontáneamente. El candidato nunca ve la lista.**
Ese es todo el mecanismo: si se le enseña la lista, la señal muere.

La familia sale de la ficha de vacante (Q3 la confirma). Puede ser más de una.

### F1 · Mando y dirección
nombres propios de personas · lo llamé a la oficina · plazo con fecha · memorando · acta ·
dejar constancia · el que se fue · me equivoqué en esperar tanto · reunión uno a uno ·
plan de mejora
**Señal máxima:** aparece un momento incómodo dentro de la historia.

### F2 · Comercial y ventas
ciclo en semanas o meses · la objeción exacta · quién autorizaba el precio · me bajaron el
margen · cuota y cuánto faltó · seguimiento · cartera · recompra · el competidor por nombre
**Señal máxima:** cuenta una venta que perdió y por qué.

### F3 · Operación técnica
marcas y modelos de equipo · tolerancias · tiempos reales de la tarea · se descalibra ·
qué falla primero · EPP concreto · entrega de turno · el repuesto que faltó · perdimos X horas
**Señal máxima:** sabe qué sale mal y con qué frecuencia.

### F4 · Administración y finanzas
arqueo · corte de caja · denominaciones · sobrante · faltante · tolerancia · vouchers ·
fondo fijo · conteo a ciegas · cuadre contra sistema · quién firma · rendición · caja chica
**Señal máxima:** menciona qué pasó con la persona cuando hubo un faltante.

### F5 · Servicio y atención
qué dijo el cliente textualmente · qué le ofreció · hasta X podía resolver yo · escalamiento ·
si volvió o no · el reclamo que se repetía · tiempo de respuesta
**Señal máxima:** sabe hasta dónde podía decidir solo.

### F6 · Proyectos y creación
quién aprobaba · rondas de corrección · cambio de alcance · la fecha que se movió · qué
recortó para llegar · el número que miró después · el proveedor que falló · brief · hito
**Señal máxima:** menciona qué midió después de entregar.

### F7 · Soporte y asistencia
qué le quitaba de encima al jefe · dónde apuntaba lo pendiente · se me pasó una vez ·
qué podía decidir sin consultar · el pendiente heredado
**Señal máxima:** cuenta algo que se le pasó y qué cambió después.

### La escala de textura

| Términos espontáneos | Nivel |
|---|---:|
| 0–2 | **0** — no pisó ese terreno |
| 3–5 | **1** — estuvo cerca; probablemente lo supervisó |
| 6–9 | **3** — lo hizo |
| 10+ **y** aparece la señal máxima | **4** — lo domina |

⚠️ **La escala salta el 2.** Es así en el original y no se debe normalizar.

**Una eliminatoria se da por cubierta con 6 términos o más.** Por debajo se activa la
repregunta 1.

---

## La repregunta de esta etapa

Es la **prioridad 1** de las diez del sistema — va antes que cualquier repregunta de la
etapa 1.

| # | Condición | Repregunta |
|---:|---|---|
| **1** | La eliminatoria técnica tiene **menos de 6 términos de textura** | *(se usa la pregunta de procedimiento de ese riesgo, del propio cuestionario técnico)* |

**Si tras la repregunta la condición persiste, el ítem baja un nivel y queda la bandera** —
y aquí eso es descarte, no solo bandera (ver abajo).

Las otras nueve repreguntas son de la etapa 1 y están en
[su documento](CAZATALENTOS-BANCO-RENASER.md). **El tope de 3 por candidato es conjunto**,
no por etapa.

---

## El corte de esta etapa

| Condición | Valor |
|---|---|
| Índice técnico | **≥ 65** |
| Eliminatoria | **textura ≥ 6 términos** |

---

## El cierre del filtro

Aquí se juntan las dos etapas. Este cálculo no puede hacerse antes: necesita las dos notas.

```
Índice combinado = (Índice RENASER × 0.45) + (Índice técnico × 0.55)
```

*La técnica pesa más porque mide si sabe hacer el trabajo. La RENASER decide si vale la pena
enseñárselo.*

| Paso | Criterio |
|---|---|
| **A entrevista** | Los **5 mejores** del combinado |
| **A inducción** | Los **3 mejores** tras muestra de trabajo y referencias |

### Los cuatro descartes automáticos

1. **Integridad = 0** *(etapa 1)*
2. **Eliminatoria con textura < 6 tras la repregunta** *(etapa 2)*
3. **No autoriza la verificación de referencias** *(etapa 1, Z03)*
4. **Dos o más banderas activas y contradicción confirmada tras repregunta**

**Todo descarte se reporta con su causa y su cita textual**, nunca como un número bajo.

---

## Quién formula y quién califica

| Paso | Quién | Nota |
|---|---|---|
| Levantar la ficha de vacante | **Persona**, con el dueño | 20 min. No hay atajo |
| Ordenar los riesgos por velocidad de daño | **El dueño** | No lo decide quien redacta |
| Redactar las 8–12 preguntas | **Persona** | Según su documento. Ver la nota de abajo |
| Marcar la muestra como `PRESENCIAL` | **Persona** | Nunca se envía en el formulario |
| Poner el puntaje 0–4 | **Agente de IA**, con cita textual | Igual que la etapa 1 |
| Contar términos de textura | **Determinista** (búsqueda de términos) | Excepto la señal máxima |
| Evaluar la señal máxima | **Agente de IA** | Es un juicio, no un término |
| Disparar la repregunta 1 | **Determinista** | Umbral de 6 términos |

### Sobre si la IA podría redactar la técnica

Su documento **no lo contempla**: la redacta una persona con el dueño. Pero la estructura es
mecánica (por riesgo: una de experiencia con magnitudes, una de procedimiento exacto), así
que es generable.

Si se decide hacerlo, la regla que ya rige en el sistema es clara: **un agente puede generar
texto que no entre en una nota**. Una pregunta técnica sí entra en una nota. Por tanto la
única forma admisible es **borrador generado + aprobación humana antes de publicar**, nunca
automático. Una pregunta técnica mal formulada descalifica gente en silencio y sin
posibilidad de auditarlo después.

---

## Lo que nunca se hace

- **No se envía la prueba técnica antes de aprobar la RENASER.**
- **No se envía la muestra de trabajo en el formulario.** Regala el diagnóstico del negocio a
  todo el que postule.
- **No se da por cubierta una eliminatoria sin el conteo de textura.**
- No se enseña el diccionario de textura al candidato, ni se le pide que use esos términos.
- No se decide contratar ni descartar: eso es humano. El sistema recomienda.
- No se puntúa sin cita.
- No se hacen más de 3 repreguntas por candidato, contando las dos etapas.
- No se pregunta ni se registra: estado civil, hijos, salud, embarazo, religión, política,
  sindicato ni origen étnico.

---

## Lo que este documento no cubre

- Las preguntas fijas de la etapa 1, el método C1–C4, los pilares, sus pesos y las banderas →
  [CAZATALENTOS-BANCO-RENASER.md](CAZATALENTOS-BANCO-RENASER.md).
- Nueve de las diez repreguntas (las de la etapa 1), en el mismo documento.
- Cómo encaja esto en el sistema que ya está construido (plantillas de prueba, rúbrica,
  versionado).

---

## Qué está construido, a 28/08/2026

**Ciclo 1 (V42).** La ficha de vacante y el agente REDACTOR: el dueño contesta las diez
preguntas en su panel, la IA escribe el borrador siguiendo la receta de arriba, él lo corrige
con sus palabras y lo publica. El cuestionario aprobado es un banco ligado a esa vacante.

**Ciclo 2 (V43).** Que se pueda rendir. Cada vacante elige, en borrador, qué se rinde en su
etapa técnica —esta prueba o la del puesto de siempre, nunca las dos— y en cuántos minutos;
publicar exige tener listo el que eligió. El candidato la contesta en su portal sin ver la
pregunta presencial ni la guía de calificación, el agente `EVALUADOR_TECNICO` cuenta sus
criterios y el índice queda como nota de la etapa.

**Lo que falta.** La textura y sus siete diccionarios, la repregunta de esta etapa, el índice
combinado (45/55) y el corte automático en 65: hoy el equipo avanza a mano desde el ranking,
que es lo que se pidió mientras el método se calibra. Y en el panel, la palanca para elegir el
instrumento y la pantalla del candidato están pendientes: por ahora el backend lo sirve todo,
pero nadie lo ve.
