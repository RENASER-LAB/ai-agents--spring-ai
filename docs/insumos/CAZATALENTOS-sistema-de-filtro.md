# RENASER CAZATALENTOS — SISTEMA DE FILTRO
## Prueba RENASER + Prueba Técnica + Método de calificación

---

# PARTE 1 · CÓMO CALIFICA UN CONSULTOR CON AÑOS DE EXPERIENCIA

Esta es la parte que decide si el sistema funciona o no. Todo lo demás es contenido.

## 1.1 Lo que un veterano NO hace

No califica por si la respuesta está bien escrita. No califica por si la persona parece capaz. No califica por si la respuesta "le gustó". **No juzga el contenido de la decisión** — dos jefes buenos pueden resolver distinto el mismo caso.

## 1.2 Lo que sí hace

Un consultor con experiencia lee una respuesta buscando **cuatro cosas, presentes o ausentes**. Nada más. Las busca en este orden y es casi automático para él:

| | Criterio | Qué busca | Por qué |
|---|---|---|---|
| **C1** | **EPISODIO** | ¿Cuenta algo que **pasó**, con momento y lugar identificables? ¿O explica cómo actúa "en general"? | La teoría se puede leer. Un episodio hay que haberlo vivido. |
| **C2** | **AUTORÍA** | ¿Dice qué hizo o decidió **él**, en primera persona del singular? ¿O todo es "nosotros" y "se hizo"? | Separa al que produjo del que estuvo cerca de quien produjo. |
| **C3** | **DATO DURO** | ¿Aparece el dato concreto que esa pregunta debía producir? Cifra, plazo, nombre, documento, frecuencia. | Es lo que se puede verificar después. |
| **C4** | **LA PARTE INCÓMODA** | ¿Aparece lo que salió mal, quién se opuso, qué le costó, qué dejó sin hacer? | **Todo hecho real tiene una parte fea. Quien lo vivió la recuerda; quien lo construyó, no la inventa.** |

**C4 es el criterio que separa un sistema de selección de un sistema de cazatalentos.** Es el más difícil de fingir y el que ningún cuestionario tradicional mide.

## 1.3 La escala — dos pasos, sin interpretación

**PASO 1.** ¿La respuesta cumple la SEÑAL DE 0 de esa pregunta? → **puntaje 0**. Fin del cálculo.

**PASO 2.** Si no: **el puntaje es el número de criterios presentes**, de 0 a 4.

| Puntaje | Qué significa |
|---|---|
| **0** | No hay episodio, o cumple la señal de 0 |
| **1** | Hay episodio, pero no es suyo o no tiene nada concreto |
| **2** | Episodio suyo, sin dato duro |
| **3** | Episodio suyo con el dato duro |
| **4** | Todo lo anterior + la parte incómoda |

**Cada pregunta declara qué cuenta como C3 y qué cuenta como C4 en ella.** El evaluador no decide qué es un dato duro: está escrito.

## 1.4 Ejemplo trabajado

Pregunta R15: *«Una persona de bajo rendimiento que gestionaste: ¿qué detectaste, qué hiciste, cuánto tomó y cómo terminó? ¿Cuánta gente tenías a cargo entonces?»*
- **C3 esperado:** el plazo que le puso y el número de personas a cargo.
- **C4 esperado:** el nombre, el documento, o la conversación incómoda.

**Candidato A** — *«Siempre trabajo el bajo rendimiento con retroalimentación continua y acompañamiento. Creo mucho en el potencial de las personas y en darles herramientas para mejorar.»*
→ C1 ✗ C2 ✗ C3 ✗ C4 ✗ = **0**. No hay episodio. Es una declaración de filosofía.

**Candidato B** — *«Tuvimos un caso en el equipo de un colaborador que no llegaba a meta. Trabajamos con él un plan de mejora y logramos que se recuperara en un par de meses.»*
→ C1 ✓ C2 ✗ (todo en "nosotros") C3 ✗ C4 ✗ = **1**.

**Candidato C** — *«Tenía 14 personas. Uno de los vendedores llevaba tres meses bajo cuota. Lo llamé, le puse un plan por escrito con corte al 30, y seguimiento semanal los viernes. A los dos meses estaba en meta.»*
→ C1 ✓ C2 ✓ C3 ✓ (plazo y número) C4 ✗ = **3**.

**Candidato D** — *«Tenía 14 personas. Marco llevaba tres meses bajo cuota. Lo llamé a la oficina, le puse plan por escrito con corte al 30 y seguimiento los viernes. Me costó porque era amigo del dueño y me pidió que lo dejara pasar. A los dos meses estaba en meta, pero perdí relación con el dueño un tiempo.»*
→ C1 ✓ C2 ✓ C3 ✓ C4 ✓ = **4**.

**Nótese que C y D dicen exactamente lo mismo sobre lo que hicieron.** La diferencia es que D estuvo ahí.

## 1.5 Las tres reglas que evitan el error del evaluador

1. **Ante duda entre dos niveles: se asigna el MENOR** y se marca `AMBIGUO` para revisión humana. Nunca se resuelve hacia arriba.
2. **Un criterio ausente no se supone presente.** Si el candidato no dijo el plazo, no lo dijo — aunque "seguramente lo hizo".
3. **Cada puntaje se acompaña de la cita textual** que lo sustenta. Un puntaje sin cita es inválido.

## 1.6 Las cuatro banderas que un veterano levanta sin pensar

Se marcan sobre el cuestionario completo, no sobre una pregunta:

| Bandera | Condición | Qué significa |
|---|---|---|
| `SIN_INCOMODIDAD` | Ninguna de las respuestas contiene C4 | Es la señal más confiable de relato construido |
| `SOLO_NOSOTROS` | Más de la mitad de las respuestas sin C2 | Estuvo cerca del resultado, no lo produjo |
| `REACTIVO` | En la pregunta de las 48 horas: los tres son emergencias y dos o más se repiten | Restricción de anticipación. Viaja con la persona |
| `SIN_FRACASO` | Ningún error, ningún conflicto, nadie se opuso, nada salió mal | Nadie con trayectoria real tiene esa hoja de vida |

**Dos o más banderas activas = revisión humana obligatoria antes de avanzar**, sin importar el puntaje.

---

# PARTE 2 · ARQUITECTURA DEL FILTRO

```
ETAPA 1 · PRUEBA RENASER          ← todos los candidatos del mismo nivel, idéntica
          DIR 18 · SUP 15 · OPE 12 preguntas
          Mide: iniciativa · resolución · excelencia · servicio ·
                responsabilidad y resultados · dirección de personas · integridad
          Corte: índice ≥ 60 y la pregunta de integridad ≠ 0

ETAPA 2 · PRUEBA TÉCNICA          ← solo los que pasaron
          DIR 12 · SUP 10 · OPE 8 preguntas
          Cambia según PUESTO y según TIPO DE EMPRESA
          Corte: índice ≥ 65 y eliminatoria cubierta

          + hasta 3 repreguntas disparadas por condición objetiva
```

## Carga total para el candidato

| Nivel | RENASER | Técnica | **Total** | Tiempo |
|---|---:|---:|---:|---|
| **DIR** | 18 | 12 | **30** | 50–60 min, en dos sesiones |
| **SUP** | 15 | 10 | **25** | 40–45 min |
| **OPE** | 12 | 8 | **20** | 25–35 min |

**Nunca más de 30 preguntas en total.** La técnica se envía solo después de aprobar la RENASER: nadie invierte dos horas antes de saber si sigue en carrera.

---

# PARTE 3 · PRUEBA RENASER — NIVEL DIR (18)

**Instrucciones al candidato (texto literal):**

> Cada pregunta indica qué estamos evaluando. No buscamos respuestas perfectas: queremos saber **qué has hecho** y **cómo decides**.
> Responde con casos reales. Di qué pasó, qué hiciste **tú**, y cómo terminó. Los números aproximados sirven.
> Si algo nunca te ha pasado, dilo. Preferimos eso a un ejemplo forzado.

---

## PILAR 1 · INICIATIVA

**R01** · *Evaluamos si además de proponer, empujas hasta el final.*
> **Cuéntanos algo que hayas mejorado o puesto en marcha sin que nadie te lo pidiera. ¿Quién se opuso y sigue funcionando hoy?**

`C3 DATO:` qué cambió — cifra, tiempo ahorrado, o desde cuándo opera.
`C4 INCOMODIDAD:` quién se opuso o qué tuvo que ceder.
`SEÑAL DE 0:` la idea nunca se implementó, o no sabe si sigue vigente.

**R02** · *Evaluamos qué haces con el espacio que nadie te asigna.*
> **Cuando terminas antes de tiempo todo lo que te asignaron, ¿qué haces? Cuéntanos la última vez que te pasó.**

`C3:` cuándo fue — mes o fecha.
`C4:` a quién le sirvió o qué produjo.
`SEÑAL DE 0:` «busco en qué ayudar» sin ejemplo, o «no me suele pasar».

**R03** · *Evaluamos si ves los problemas venir.*
> **Cuéntanos de un problema que viste venir antes de que ocurriera. ¿Qué señal lo anticipó y qué hiciste con esa información?**

`C3:` con cuánto tiempo de anticipación.
`C4:` qué habría pasado si no actuaba.
`SEÑAL DE 0:` no tiene caso; describe cómo se anticipa en general.

---

## PILAR 2 · RESOLUCIÓN DE PROBLEMAS

**R04** · *Evaluamos si atacas la causa o el síntoma.*
> **El problema laboral más difícil que has resuelto: ¿cómo supiste cuál era la causa real y no otra?**

`C3:` cuánto tiempo tomó resolverlo.
`C4:` menciona una hipótesis que resultó equivocada.
`SEÑAL DE 0:` va directo a la causa correcta, sin proceso.

**R05** · *Evaluamos cómo funciona tu operación cotidiana.*
> **Menciona los tres últimos problemas que resolviste en tus últimas 48 horas de trabajo. De cada uno, dinos si era la primera vez que ocurría.**

`C3:` los tres problemas identificables, cada uno con su frecuencia.
`C4:` al menos uno no era emergencia, sino algo que él decidió mejorar.
`SEÑAL DE 0:` los tres son emergencias y dos o más se repiten todos los meses → **bandera `REACTIVO`**.
*Sensor declarado en abstracto a propósito: no se le dice que se mide comportamiento reactivo.*

**R06** · *Evaluamos con qué criterio decides cuando no puedes con todo.*
> **Cinco cosas importantes al mismo tiempo y todas urgentes. ¿Cómo decides cuál va primero? Explícalo con un caso real que hayas vivido.**

`C3:` el caso, con las cosas concretas que competían.
`C4:` qué dejó sin hacer y qué costó esa decisión.
`SEÑAL DE 0:` nombra un método de priorización sin ningún caso.

---

## PILAR 3 · EXCELENCIA

**R07** · *Evaluamos qué tan alto pones la vara y cómo la haces cumplir.*
> **El estándar de calidad más alto que has exigido —a ti o a tu equipo—: ¿cómo verificabas que se cumpliera?**

`C3:` el criterio concreto de verificación — checklist, medición, revisor, muestra.
`C4:` qué pasaba cuando no se cumplía.
`SEÑAL DE 0:` «siempre busco la excelencia», sin estándar concreto.

**R08** · *Evaluamos tu relación con tu propio trabajo cuando no sale bien.*
> **Algo que entregaste y no quedó como tú querías. ¿Qué pasó y qué hiciste después?**

`C3:` qué faltó exactamente.
`C4:` si lo dijo o lo calló, y qué cambió después.
`SEÑAL DE 0:` no tiene ningún caso así.

---

## PILAR 4 · SERVICIO

**R09** · *Evaluamos si generas valor más allá de tu función.*
> **Lo último que hiciste por un cliente o un compañero que nadie te pidió y que no te iba a beneficiar. ¿Qué fue y cuándo?**

`C3:` cuándo fue y cuánto tiempo le tomó.
`C4:` nadie se enteró, o no lo hizo para que se supiera.
`SEÑAL DE 0:` «no recuerdo», o la última vez fue hace años.

**R10** · *Evaluamos cómo respondes cuando la falla no es tuya.*
> **Un cliente o un área interna quedó mal atendido y la falla no era tuya. ¿Qué hiciste?**

`C3:` qué hizo concretamente y en cuánto tiempo.
`C4:` se hizo cargo de la solución sin que le correspondiera.
`SEÑAL DE 0:` dedica la respuesta a explicar de quién era la culpa.

---

## PILAR 5 · RESPONSABILIDAD Y RESULTADOS

**R11** · *Evaluamos tu capacidad de convertir trabajo en resultado medible.*
> **¿Cuál ha sido el resultado del que te sientes más orgulloso? ¿Cómo estaba antes y cómo quedó? Indica quién podría confirmarlo: nombre, cargo y contacto.**

`C3:` cifra antes y cifra después.
`C4:` verificador con nombre, cargo y contacto.
`SEÑAL DE 0:` todo en «nosotros» y sin ninguna cifra.
**REGLA DURA:** sin ninguna cifra, el máximo de esta pregunta es 2.

**R12** · *Evaluamos si te haces cargo de un resultado o de una función.*
> **Un problema del que te hiciste cargo aunque no era tu responsabilidad. ¿Por qué lo tomaste y cómo terminó?**

`C3:` cómo terminó, concretamente.
`C4:` qué le costó tomarlo.
`SEÑAL DE 0:` «siempre ayudo en todo», sin caso.

**R13** · *Evaluamos qué haces con tus propios errores.*
> **Un error importante que cometiste en el trabajo: ¿qué hiciste en las primeras 24 horas, a quién avisaste y cuánto tiempo después de detectarlo?**

`C3:` el plazo entre detectarlo y avisar.
`C4:` qué cambió después para que no se repitiera.
`SEÑAL DE 0:` no tiene errores que contar.

---

## PILAR 6 · DIRECCIÓN DE PERSONAS *(solo DIR y SUP)*

**R14** · *Evaluamos si diriges distinto a quien rinde y a quien no.*
> **Dame un ejemplo de una persona que producía muy bien, otra que estaba creciendo y otra que era deficiente. ¿Qué hiciste diferente con cada una y por qué?**

`C3:` la frecuencia y el formato de seguimiento de cada una.
`C4:` el tratamiento es claramente distinto en los tres casos, no solo más o menos intenso.
`SEÑAL DE 0:` trata igual a los tres, o no logra distinguirlos.

**R15** · *Evaluamos si sabes recuperar a una persona o solo reemplazarla.*
> **Una persona de bajo rendimiento que gestionaste: ¿qué detectaste, qué hiciste, cuánto tomó y cómo terminó? ¿Cuánta gente tenías a cargo entonces?**

`C3:` el plazo que le puso y el número de personas a cargo.
`C4:` el nombre, el documento, o la conversación incómoda.
`SEÑAL DE 0:` «le di retroalimentación y mejoró».

**R16** · *Evaluamos cómo resuelves cuando resultado y orden se contradicen.*
> **Uno de tus mejores consigue resultados excelentes pero genera conflictos constantes con el equipo. ¿Qué haces?**

`C3:` qué le diría y qué condición le pondría.
`C4:` separa el resultado del comportamiento y actúa sobre el segundo, con condición y plazo.
`SEÑAL DE 0:` «lo dejo, produce» o «lo saco».

**R17** · *Evaluamos si sostienes lo correcto cuando cuesta.*
> **Una decisión que tu equipo rechazaba y sostuviste igual. ¿A cuánta gente afectaba y qué pasó en los meses siguientes?**

`C3:` a cuánta gente afectaba.
`C4:` quién se molestó y qué costó sostenerla.
`SEÑAL DE 0:` todos lo entendieron después, o nunca ha tomado una decisión así.

---

## PILAR 7 · INTEGRIDAD *(eliminatoria)*

**R18** · *Evaluamos qué haces cuando lo correcto tiene costo.*
> **Descubres una irregularidad que puede costarle dinero a la empresa. Tu superior te dice que lo dejes pasar porque nadie se va a enterar. ¿Qué haces y por qué?**

`C3:` qué haría concretamente — a quién acude, cómo lo deja registrado.
`C4:` distingue entre insubordinarse y dejar constancia.
`SEÑAL DE 0:` acepta dejarlo pasar → **DESCARTE AUTOMÁTICO**.

---

## CIERRE *(no puntúa)*

- ¿Por qué deberíamos elegirte a ti y qué evidencia de tu trayectoria lo demuestra?
- **¿A quién admiras de tu competencia? ¿Quién es el mejor que has visto haciendo este trabajo?** → se registra el nombre.
- Expectativa salarial · Disponibilidad · **¿Autorizas que contactemos referencias y verifiquemos las cifras que declaraste? Sí/No + contactos.**

---

# PARTE 4 · PRUEBA RENASER — NIVEL SUP (15)

Mismas instrucciones. Se usan las mismas preguntas del nivel DIR, ajustadas en alcance:

| Pilar | Preguntas | Ajuste respecto a DIR |
|---|---|---|
| Iniciativa | R01, R02 | Sale R03 |
| Resolución | R04, R05, R06 | Iguales |
| Excelencia | R07, R08 | Iguales |
| Servicio | R09, R10 | Iguales |
| Responsabilidad y resultados | R11, R13 | Sale R12 |
| Dirección de personas | R14, R15, R16 | Sale R17 |
| Integridad | R18 | Igual |

**Ajustes de redacción para SUP:** en R11 se dice «el mejor resultado que has conseguido» en vez de «resultado profesional del que te sientes más orgulloso». En R14 y R15, «tu equipo» en vez de «tu área».

---

# PARTE 5 · PRUEBA RENASER — NIVEL OPE (12)

**Instrucciones al candidato:**
> Responde con ejemplos reales de tu trabajo. No buscamos respuestas perfectas: queremos saber cómo trabajas. Si algo nunca te ha pasado, dilo.

**INICIATIVA**

**P01** > **Algo que mejoraste en tu trabajo por tu cuenta: qué era, qué cambió y si se sigue haciendo así hoy.**
`C3:` qué cambió concretamente. `C4:` alguien se opuso o le costó que lo aceptaran. `0:` nada, o nunca se aplicó.

**P02** > **Cuando terminas todo lo que te asignaron antes de tiempo, ¿qué haces? Cuéntanos la última vez.**
`C3:` cuándo fue. `C4:` a quién le sirvió. `0:` «espero» o «no me pasa».

**RESOLUCIÓN DE PROBLEMAS**

**P03** > **Menciona los tres últimos problemas que resolviste en tus últimas 48 horas de trabajo. ¿Cuáles se repiten seguido?**
`C3:` los tres identificables con su frecuencia. `C4:` al menos uno no era emergencia. `0:` los tres emergencias y dos recurrentes → bandera `REACTIVO`.

**P04** > **Te piden tres cosas urgentes al mismo tiempo y no alcanzas a hacer las tres. ¿Qué haces? Cuéntanos un caso real.**
`C3:` el caso concreto. `C4:` qué dejó sin hacer y a quién avisó. `0:` sin caso real.

**EXCELENCIA**

**P05** > **¿Cómo sabes que un trabajo tuyo quedó bien terminado? ¿Qué revisas antes de entregarlo?**
`C3:` qué revisa concretamente. `C4:` una vez que revisó y encontró algo mal. `0:` «cuando nadie reclama».

**P06** > **¿Qué es lo que más se tiene que volver a hacer en tu trabajo y por qué?**
`C3:` la falla concreta y su frecuencia. `C4:` qué hizo o propuso para que dejara de pasar. `0:` «casi nunca hay que rehacer nada».

**P07** > **Algo que entregaste y no quedó como tú querías. ¿Qué hiciste?**
`C3:` qué faltó. `C4:` si lo dijo o lo calló. `0:` no tiene ningún caso así.

**SERVICIO**

**P08** > **La última vez que ayudaste a alguien sin que te lo pidieran y sin que te beneficiara. ¿Qué hiciste y cuándo?**
`C3:` cuándo y a quién. `C4:` nadie se enteró. `0:` «no recuerdo».

**P09** > **Un cliente o compañero está molesto y el error no fue tuyo. ¿Qué hiciste?**
`C3:` qué hizo concretamente. `C4:` se hizo cargo sin que le correspondiera. `0:` explica que no fue él.

**RESPONSABILIDAD**

**P10** > **Una vez que te equivocaste en el trabajo: qué pasó, qué hiciste apenas te diste cuenta, a quién avisaste y cuánto tiempo después.**
`C3:` el plazo. `C4:` qué cambió después. `0:` no tiene errores, o lo corrigió callado.

**P11** > **Una vez que te comprometiste a algo y no pudiste cumplir. ¿Qué hiciste?**
`C3:` si avisó y cuándo. `C4:` qué le costó. `0:` avisó después del plazo, o nunca le ha pasado.

**INTEGRIDAD** *(eliminatoria)*

**P12** > **Encuentras dinero o material de la empresa que nadie reclama. ¿Qué haces?**
`C3:` a quién lo entrega y cómo. `C4:` lo hace aunque nadie se hubiera enterado. `0:` se lo queda o «si es poca cosa no vale la pena avisar» → **DESCARTE AUTOMÁTICO**.

*Cierre: pregunta de caza + autorización de verificación.*

---

# PARTE 6 · PRUEBA TÉCNICA

## 6.1 Las dos entradas

La técnica cambia por **dos** motivos, no uno:

| Entrada | Qué define | Ejemplo |
|---|---|---|
| **PUESTO** | Los riesgos del cargo | Administrador → caja, personal, control |
| **TIPO DE EMPRESA** | El contenido de esos riesgos | Casa de cambio → divisas y tipo de cambio · Retail → inventario y mermas · Constructora → valorizaciones y avance de obra |

**El mismo puesto en dos empresas comparte la estructura y cambia el contenido.** Un administrador de casa de cambio y uno de una clínica responden ambos sobre caja y personal, pero la pregunta de margen es sobre tipo de cambio en uno y sobre convenios y tarifas en el otro.

## 6.2 Cómo se genera — 30 minutos con el dueño

**Paso 1 · Riesgos.** *«Si contratas a la persona equivocada, ¿en qué te vas a dar cuenta primero?»* Listar 4 riesgos y **ordenarlos por velocidad de daño**, no por importancia.

**Paso 2 · Dos preguntas por riesgo:**
- **Experiencia:** *«¿Has tenido responsabilidad sobre [riesgo]? Indica [magnitudes].»*
- **Procedimiento:** *«Si ocurre [peor escenario], ¿cuál sería tu procedimiento exacto?»*

*La de procedimiento no se puede fingir: quien lo hizo lo describe paso a paso; quien lo leyó dice «revisaría bien».*

**Paso 3 · Rubro.** Insertar en cada pregunta el vocabulario del rubro de la empresa. Es lo que la vuelve específica y lo que activa el diccionario de textura.

**Paso 4 · Dilema.** Una pregunta donde **dos virtudes del negocio se contradicen**.

**Paso 5 · Muestra de trabajo.** Se marca `PRESENCIAL` y **nunca se envía en el formulario**: regala el diagnóstico del negocio a todo el que postule.

## 6.3 Estructura fija

| Bloque | DIR (12) | SUP (10) | OPE (8) |
|---|---:|---:|---:|
| Experiencia y escala | 2 | 2 | 2 |
| Riesgo 1 (el más rápido) | 3 | 3 | 2 |
| Riesgo 2 | 2 | 2 | 2 |
| Riesgo 3 | 2 | 1 | 1 |
| Requerimientos específicos | 1 | 1 | — |
| Dilema del negocio | 1 | 1 | 1 |
| Muestra de trabajo `PRESENCIAL` | 1 | — | — |

## 6.4 Referencia completa — Administrador, casa de cambio, 3 sedes (DIR, 12)

*Riesgos ordenados por velocidad de daño: caja → margen en divisas → control a distancia → personal.*

**T01** ¿Cuántos años llevas administrando empresas o unidades operativas? Indica: empresa · cargo · tiempo · número de trabajadores · número de sedes · de qué respondías.
**T02** ¿Cuál es la operación más grande que has administrado directamente? Número de trabajadores, sedes, volumen de dinero administrado y operaciones por día.

**T03** ¿Has tenido responsabilidad directa sobre cajas, efectivo o bancos? Explica qué controlabas y de qué monto o volumen hablamos.
**T04** Explícanos paso a paso cómo hacías un cierre y cuadre de caja: qué revisabas, con qué documentos, cómo identificabas diferencias.
**T05** Si al cerrar el día una sede presenta un faltante, ¿cuál sería tu procedimiento exacto para determinar qué ocurrió?

**T06** ¿Has trabajado en casa de cambio, empresa financiera o negocio con manejo intensivo de efectivo? Indica volumen aproximado de operaciones. Si no, explica qué experiencia consideras transferible.
**T07** Si una sede sigue operando pero su margen está bajando, ¿qué información revisas primero para encontrar la causa?

**T08** ¿Has administrado más de una sede a la vez? Cuántas, a qué distancia, cuántos trabajadores y cómo hacías el control.
**T09** Si tuvieras tres sedes y no pudieras estar en todas todos los días, ¿cómo organizarías la supervisión? Qué indicadores, con qué frecuencia y qué información exigirías.

**T10** *(Requerimiento)* ¿Qué experiencia tienes coordinando con contadores y revisando información contable? ¿Qué reportes revisabas personalmente y qué decisión tomabas con ellos?

**T11** *(DILEMA)* Un trabajador de una sede tiene los mejores resultados comerciales, pero constantemente comete errores de caja e incumple procedimientos. ¿Cómo actúas y qué evalúas?

**T12** *(PRESENCIAL — no se envía)* Si asumieras la administración y te pidiéramos aumentar la rentabilidad de las tres sedes en 12 meses, ¿qué necesitarías conocer en tus primeros 30 días antes de proponer un plan?

## 6.5 Diccionarios de textura

Se cuentan los términos que aparecen **espontáneamente**. El candidato nunca ve la lista.

| Familia | Términos | Señal máxima |
|---|---|---|
| **F1 Mando** | nombres propios · lo llamé a la oficina · plazo con fecha · memorando · acta · dejar constancia · el que se fue · me equivoqué en esperar | aparece un momento incómodo |
| **F2 Comercial** | ciclo en semanas · la objeción exacta · quién autorizaba el precio · me bajaron el margen · cuota y cuánto faltó · seguimiento · cartera | cuenta una venta que perdió |
| **F3 Operación** | marcas y modelos · tolerancias · tiempos reales · se descalibra · qué falla primero · EPP · entrega de turno · el repuesto que faltó | sabe qué sale mal y con qué frecuencia |
| **F4 Administración** | arqueo · corte de caja · denominaciones · sobrante · faltante · tolerancia · vouchers · fondo fijo · conteo a ciegas · cuadre contra sistema · quién firma | menciona qué pasó con la persona en un faltante |
| **F5 Servicio** | qué dijo el cliente textualmente · qué ofreció · hasta X resolvía yo · escalamiento · si volvió · el reclamo que se repetía | sabe hasta dónde podía decidir solo |
| **F6 Proyectos** | quién aprobaba · rondas de corrección · cambio de alcance · la fecha que se movió · qué recortó · el número que miró después | menciona qué midió después de entregar |
| **F7 Soporte** | qué le quitaba de encima al jefe · dónde apuntaba lo pendiente · se me pasó una vez · qué decidía sin consultar | cuenta algo que se le pasó y qué cambió |

| Términos espontáneos | Nivel de textura |
|---|---|
| 0–2 | 0 — no pisó ese terreno |
| 3–5 | 1 — estuvo cerca; probablemente lo supervisó |
| 6–9 | 3 — lo hizo |
| 10+ y aparece la señal máxima | 4 — lo domina |

**Una eliminatoria se da por cubierta con 6 términos o más.**

---

# PARTE 7 · REPREGUNTAS

Máximo 3 por candidato. **No las elige el evaluador: se disparan por condición objetiva**, en este orden de prioridad. **No puntúan**: confirman o levantan bandera.

| # | Condición | Repregunta |
|---|---|---|
| 1 | La eliminatoria técnica tiene menos de 6 términos de textura | *(la pregunta de procedimiento de ese riesgo)* |
| 2 | Ninguna respuesta del pilar de personas contiene un nombre propio | «¿Cómo se llamaba esa persona y qué le dijiste exactamente?» |
| 3 | R11 no contiene ningún número | «¿Cuál era el número antes y cuál después? Un aproximado sirve.» |
| 4 | Más de la mitad de las respuestas sin C2 (todo en «nosotros») | «De todo eso, ¿qué hiciste tú personalmente y qué decidiste tú?» |
| 5 | R13 no indica cuánto tiempo pasó hasta avisar | «¿Cuántas horas o días pasaron entre que lo detectaste y lo avisaste?» |
| 6 | R04 no menciona ninguna hipótesis descartada | «¿Qué pensaste al principio que resultó no ser la causa?» |
| 7 | Ninguna respuesta contiene C4 (bandera `SIN_INCOMODIDAD`) | «Cuéntanos algo que hayas hecho mal en el trabajo y te haya costado caro.» |
| 8 | R15 no menciona plazo ni documento | «¿Le pusiste un plazo? ¿Quedó algo por escrito?» |
| 9 | La escala declarada no coincide con los casos contados | «¿Cuánta gente tenías a cargo cuando pasó lo que nos contaste?» |
| 10 | La empresa anterior es 5 veces mayor o menor que la contratante | «Si aquí no tuvieras el equipo o el sistema que tenías allá, ¿cómo lo harías?» |

**Si tras la repregunta la condición persiste, el ítem original baja un nivel y queda la bandera.**

---

# PARTE 8 · CÁLCULO Y CORTES

## 8.1 Fórmulas

```
Puntaje de pilar (%)  = (puntos obtenidos ÷ puntos máximos del pilar) × 100
Índice RENASER        = Σ (puntaje de pilar × peso del pilar) ÷ 100
Índice técnico        = (puntos obtenidos ÷ puntos máximos) × 100
```
*Puntos máximos = 4 × número de preguntas del pilar.*

## 8.2 Pesos de los pilares

| Pilar | DIR · MICRO | DIR · MEDIA/GRANDE | SUP | OPE |
|---|---:|---:|---:|---:|
| Iniciativa | 20 | 15 | 15 | 15 |
| Resolución de problemas | 25 | 22 | 22 | 20 |
| Excelencia | 15 | 15 | 15 | 30 |
| Servicio | 10 | 10 | 10 | 20 |
| Responsabilidad y resultados | 20 | 18 | 18 | 15 |
| Dirección de personas | 10 | 20 | 20 | — |
| Integridad | eliminatoria | eliminatoria | eliminatoria | eliminatoria |
| **Total** | **100** | **100** | **100** | **100** |

*En empresa pequeña pesan más iniciativa y resolución, porque hay que armar lo que no existe. En empresa grande pesa más dirección de personas, porque hay que mover una estructura. En operativos pesa la excelencia, que es la calidad del trabajo mismo.*

## 8.3 Cortes

| Etapa | Corte | Además |
|---|---|---|
| **Prueba RENASER** | Índice ≥ 60 | Integridad ≠ 0 · máximo 1 bandera activa |
| **Prueba técnica** | Índice ≥ 65 | Eliminatoria con textura ≥ 6 |
| **A entrevista** | Los 5 mejores del combinado | — |
| **A inducción** | Los 3 mejores tras muestra de trabajo y referencias | — |

**Índice combinado = (Índice RENASER × 0.45) + (Índice técnico × 0.55)**
*La técnica pesa más porque mide si sabe hacer el trabajo. La RENASER decide si vale la pena enseñárselo.*

## 8.4 Descartes automáticos

1. Integridad = 0.
2. Eliminatoria con textura < 6 **tras** la repregunta.
3. No autoriza la verificación de referencias.
4. Dos o más banderas activas y contradicción confirmada tras repregunta.

**Todo descarte se reporta con su causa y su cita textual**, nunca como un número bajo.

---

# PARTE 9 · LO QUE NUNCA SE HACE

- No se decide contratar ni descartar: eso es humano. El sistema recomienda.
- No se supone un criterio ausente. Lo que no dijo, no lo dijo.
- No se puntúa sin cita.
- No se hacen más de 3 repreguntas.
- No se emiten juicios de personalidad. Solo conducta declarada.
- No se pregunta ni se registra: estado civil, hijos, salud, embarazo, religión, política, sindicato ni origen étnico.
- No se envía la prueba técnica antes de aprobar la RENASER.
- No se envía la muestra de trabajo en el formulario.
- No se da por cubierta una eliminatoria sin el conteo de textura.

---

# PARTE 10 · ANTES DE USARLO CON EXTERNOS

1. **Que Darren califique con el método de la Parte 1** las respuestas de 3 candidatos reales. Si su puntaje coincide con el del sistema en 15 de 18 preguntas, está calibrado. Si no, se ajustan las señales — no el método.
2. **Aplicarlo a 8–10 personas del equipo actual** cuyo rendimiento ya conoces. Si tus mejores no superan 75 en el índice RENASER, las señales están mal.
3. **Cronometrar el primer envío real.** Si un candidato tarda más de 60 minutos en la RENASER, hay preguntas que están pidiendo demasiado y se acortan.
