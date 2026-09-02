# El banco de preguntas RENASER — Etapa 1

La primera de las dos pruebas del sistema CAZATALENTOS. **Es fija**: todos los candidatos
del mismo nivel responden exactamente las mismas preguntas, con la misma redacción, en el
mismo orden.

Este documento existe porque la etapa 1 y la etapa 2 se estaban leyendo juntas y no se
construyen igual. El banco RENASER se escribe **una vez** y sirve para todas las vacantes.
La prueba técnica se escribe **por vacante**. Son dos piezas de software distintas y dos
conversaciones distintas con el cliente.

Fuente: los cuatro archivos de la clienta, en `docs/insumos/`. **Cuando el `.md` y un xlsx se
contradicen, manda el `.md`** — es la decisión tomada, y las correcciones aplicadas están
anotadas en la hoja «Correcciones» de cada libro. Lo de la etapa 2 está en
[la prueba técnica](CAZATALENTOS-PRUEBA-TECNICA.md).

---

## Qué es y qué no es

| | |
|---|---|
| Varía por | **Nivel** del puesto: DIR (18) · SUP (15) · OPE (12) |
| No varía por | Puesto, empresa, rubro, familia |
| Quién la redacta | Nadie: está escrita. El xlsx lo instruye en la cabecera de la columna del enunciado: *«Se aplica textualmente. No se reformula.»* |
| Cuándo se envía | A todos los que postulan |
| Corte | Índice ≥ 60 · integridad ≠ 0 · máximo 1 bandera activa |
| Tiempo | DIR 50–60 min (en dos sesiones) · SUP 40–45 · OPE 25–35 |

**La IA no formula nada aquí.** Solo califica. La redacción de cada pregunta es parte del
instrumento: cambiarla cambia lo que mide.

---

## El método de calificación

Lo mismo para las tres versiones. Un evaluador lee una respuesta buscando **cuatro cosas,
presentes o ausentes**. No juzga si la decisión fue buena: dos jefes buenos resuelven
distinto el mismo caso.

| | Criterio | Qué busca | Por qué |
|---|---|---|---|
| **C1** | EPISODIO | ¿Cuenta algo que **pasó**, con momento y lugar identificables? | La teoría se puede leer. Un episodio hay que haberlo vivido. |
| **C2** | AUTORÍA | ¿Dice qué hizo o decidió **él**, en primera persona del singular? | Separa al que produjo del que estuvo cerca de quien produjo. |
| **C3** | DATO DURO | ¿Aparece el dato concreto que esa pregunta debía producir? | Es lo que se puede verificar después. |
| **C4** | LA PARTE INCÓMODA | ¿Aparece lo que salió mal, quién se opuso, qué le costó? | Todo hecho real tiene una parte fea. Quien lo vivió la recuerda; quien lo construyó, no la inventa. |

### La escala, en dos pasos

1. ¿La respuesta cumple la **SEÑAL DE 0** de esa pregunta? → puntaje **0**. Fin del cálculo.
2. Si no: **el puntaje es el número de criterios presentes**, de 0 a 4.

| Puntaje | Qué significa |
|---:|---|
| 0 | No hay episodio, o cumple la señal de 0 |
| 1 | Hay episodio, pero no es suyo o no tiene nada concreto |
| 2 | Episodio suyo, sin dato duro |
| 3 | Episodio suyo con el dato duro |
| 4 | Todo lo anterior + la parte incómoda |

**Cada pregunta declara qué cuenta como C3 y qué cuenta como C4 en ella.** El evaluador no
decide qué es un dato duro: está escrito en las tablas de abajo.

### Las tres reglas que evitan el error del evaluador

1. **Ante duda entre dos niveles se asigna el MENOR** y se marca `AMBIGUO`. Nunca se resuelve
   hacia arriba.
2. **Un criterio ausente no se supone presente.** Si no dijo el plazo, no lo dijo — aunque
   «seguramente lo hizo».
3. **Cada puntaje se acompaña de la cita textual** que lo sustenta. Un puntaje sin cita es
   inválido.

### El ejemplo que lo explica todo

Sobre R15 (bajo rendimiento). C3 esperado: el plazo y el número de personas. C4 esperado:
el nombre, el documento o la conversación incómoda.

| | Respuesta | Puntaje |
|---|---|---:|
| A | «Siempre trabajo el bajo rendimiento con retroalimentación continua y acompañamiento.» | **0** — no hay episodio, es filosofía |
| B | «Tuvimos un caso… trabajamos un plan de mejora y logramos que se recuperara.» | **1** — hay episodio, todo en «nosotros» |
| C | «Tenía 14 personas. Uno llevaba tres meses bajo cuota. Le puse plan por escrito con corte al 30 y seguimiento los viernes. A los dos meses estaba en meta.» | **3** — suyo y con dato duro |
| D | «…Me costó porque era amigo del dueño y me pidió que lo dejara pasar. A los dos meses estaba en meta, pero perdí relación con el dueño un tiempo.» | **4** |

**C y D dicen exactamente lo mismo sobre lo que hicieron.** La diferencia es que D estuvo ahí.

---

## Instrucciones al candidato (texto literal)

Para DIR y SUP:

> Cada pregunta indica qué estamos evaluando. No buscamos respuestas perfectas: queremos
> saber **qué has hecho** y **cómo decides**.
> Responde con casos reales. Di qué pasó, qué hiciste **tú**, y cómo terminó. Los números
> aproximados sirven.
> Si algo nunca te ha pasado, dilo. Preferimos eso a un ejemplo forzado.

Para OPE:

> Responde con ejemplos reales de tu trabajo. No buscamos respuestas perfectas: queremos
> saber cómo trabajas. Si algo nunca te ha pasado, dilo.

---

## Nivel DIR — 18 preguntas

Códigos `R01`–`R18` más el cierre `Z01`–`Z03`.

### Pilar 1 · Iniciativa

**R01** *(peso 1)* — **Cuéntanos algo que hayas mejorado o puesto en marcha sin que nadie te lo pidiera. ¿Quién se opuso y sigue funcionando hoy?**
- `C3` Qué cambió: cifra, tiempo ahorrado, o desde cuándo opera.
- `C4` Quién se opuso o qué tuvo que ceder.
- `SEÑAL DE 0` La idea nunca se implementó, o no sabe si sigue vigente.

**R02** *(peso 1)* — **Cuando terminas antes de tiempo todo lo que te asignaron, ¿qué haces? Cuéntanos la última vez que te pasó.**
- `C3` Cuándo fue: mes o fecha.
- `C4` A quién le sirvió o qué produjo.
- `SEÑAL DE 0` «Busco en qué ayudar» sin ejemplo, o «no me suele pasar».

**R03** *(peso 1 · solo DIR)* — **Cuéntanos de un problema que viste venir antes de que ocurriera. ¿Qué señal lo anticipó y qué hiciste con esa información?**
- `C3` Con cuánto tiempo de anticipación.
- `C4` Qué habría pasado si no actuaba.
- `SEÑAL DE 0` No tiene caso; describe cómo se anticipa en general.

### Pilar 2 · Resolución de problemas

**R04** *(peso 1)* — **El problema laboral más difícil que has resuelto: ¿cómo supiste cuál era la causa real y no otra?**
- `C3` Cuánto tiempo tomó resolverlo.
- `C4` Menciona una hipótesis que resultó equivocada.
- `SEÑAL DE 0` Va directo a la causa correcta, sin proceso.

**R05** *(peso 1)* — **Menciona los tres últimos problemas que resolviste en tus últimas 48 horas de trabajo. De cada uno, dinos si era la primera vez que ocurría.**
- `C3` Los tres problemas identificables, cada uno con su frecuencia.
- `C4` Al menos uno no era emergencia, sino algo que él decidió mejorar.
- `SEÑAL DE 0` Los tres son emergencias y dos o más se repiten todos los meses.
- ⚠️ La señal de 0 levanta la bandera `REACTIVO`. **Sensor declarado en abstracto a
  propósito**: al candidato no se le dice que se mide comportamiento reactivo.

**R06** *(peso 1)* — **Cinco cosas importantes al mismo tiempo y todas urgentes. ¿Cómo decides cuál va primero? Explícalo con un caso real que hayas vivido.**
- `C3` El caso, con las cosas concretas que competían.
- `C4` Qué dejó sin hacer y qué costó esa decisión.
- `SEÑAL DE 0` Nombra un método de priorización sin ningún caso.

### Pilar 3 · Excelencia

**R07** *(peso 1)* — **El estándar de calidad más alto que has exigido —a ti o a tu equipo—: ¿cómo verificabas que se cumpliera?**
- `C3` El criterio concreto de verificación: checklist, medición, revisor, muestra.
- `C4` Qué pasaba cuando no se cumplía.
- `SEÑAL DE 0` «Siempre busco la excelencia», sin estándar concreto.

**R08** *(peso 1)* — **Algo que entregaste y no quedó como tú querías. ¿Qué pasó y qué hiciste después?**
- `C3` Qué faltó exactamente.
- `C4` Si lo dijo o lo calló, y qué cambió después.
- `SEÑAL DE 0` No tiene ningún caso así.

### Pilar 4 · Servicio

**R09** *(peso 1)* — **Lo último que hiciste por un cliente o un compañero que nadie te pidió y que no te iba a beneficiar. ¿Qué fue y cuándo?**
- `C3` Cuándo fue y cuánto tiempo le tomó.
- `C4` Nadie se enteró, o no lo hizo para que se supiera.
- `SEÑAL DE 0` «No recuerdo», o la última vez fue hace años.

**R10** *(peso 1)* — **Un cliente o un área interna quedó mal atendido y la falla no era tuya. ¿Qué hiciste?**
- `C3` Qué hizo concretamente y en cuánto tiempo.
- `C4` Se hizo cargo de la solución sin que le correspondiera.
- `SEÑAL DE 0` Dedica la respuesta a explicar de quién era la culpa.

### Pilar 5 · Responsabilidad y resultados

**R11** *(peso 2)* — **¿Cuál ha sido el resultado del que te sientes más orgulloso? ¿Cómo estaba antes y cómo quedó? Indica quién podría confirmarlo: nombre, cargo y contacto.**
- `C3` Cifra antes y cifra después.
- `C4` Verificador con nombre, cargo y contacto.
- `SEÑAL DE 0` Todo en «nosotros» y sin ninguna cifra.
- ⚠️ **REGLA DURA:** sin ninguna cifra, el máximo de esta pregunta es 2.

**R12** *(peso 1 · solo DIR)* — **Un problema del que te hiciste cargo aunque no era tu responsabilidad. ¿Por qué lo tomaste y cómo terminó?**
- `C3` Cómo terminó, concretamente.
- `C4` Qué le costó tomarlo.
- `SEÑAL DE 0` «Siempre ayudo en todo», sin caso.

**R13** *(peso 1)* — **Un error importante que cometiste en el trabajo: ¿qué hiciste en las primeras 24 horas, a quién avisaste y cuánto tiempo después de detectarlo?**
- `C3` El plazo entre detectarlo y avisar.
- `C4` Qué cambió después para que no se repitiera.
- `SEÑAL DE 0` No tiene errores que contar.

### Pilar 6 · Dirección de personas *(solo DIR y SUP)*

**R14** *(peso 2)* — **Dame un ejemplo de una persona que producía muy bien, otra que estaba creciendo y otra que era deficiente. ¿Qué hiciste diferente con cada una y por qué?**
- `C3` La frecuencia y el formato de seguimiento de cada una.
- `C4` El tratamiento es claramente distinto en los tres casos, no solo más o menos intenso.
- `SEÑAL DE 0` Trata igual a los tres, o no logra distinguirlos.

**R15** *(peso 2)* — **Una persona de bajo rendimiento que gestionaste: ¿qué detectaste, qué hiciste, cuánto tomó y cómo terminó? ¿Cuánta gente tenías a cargo entonces?**
- `C3` El plazo que le puso y el número de personas a cargo.
- `C4` El nombre, el documento, o la conversación incómoda.
- `SEÑAL DE 0` «Le di retroalimentación y mejoró».

**R16** *(peso 1)* — **Uno de tus mejores consigue resultados excelentes pero genera conflictos constantes con el equipo. ¿Qué haces?**
- `C3` Qué le diría y qué condición le pondría.
- `C4` Separa el resultado del comportamiento y actúa sobre el segundo, con condición y plazo.
- `SEÑAL DE 0` «Lo dejo, produce» o «lo saco».

**R17** *(peso 1 · solo DIR)* — **Una decisión que tu equipo rechazaba y sostuviste igual. ¿A cuánta gente afectaba y qué pasó en los meses siguientes?**
- `C3` A cuánta gente afectaba.
- `C4` Quién se molestó y qué costó sostenerla.
- `SEÑAL DE 0` Todos lo entendieron después, o nunca ha tomado una decisión así.

### Pilar 7 · Integridad *(eliminatoria)*

**R18** *(peso 1 · ELIMINATORIA)* — **Descubres una irregularidad que puede costarle dinero a la empresa. Tu superior te dice que lo dejes pasar porque nadie se va a enterar. ¿Qué haces y por qué?**
- `C3` Qué haría concretamente: a quién acude, cómo lo deja registrado.
- `C4` Distingue entre insubordinarse y dejar constancia.
- `SEÑAL DE 0` Acepta dejarlo pasar → **DESCARTE AUTOMÁTICO**.

### Cierre *(no puntúa)*

| Código | Pregunta | Para qué |
|---|---|---|
| `Z01` | ¿Por qué deberíamos elegirte a ti y qué evidencia de tu trayectoria lo demuestra? | La elección de qué mostrar **es** la información |
| `Z02` | ¿A quién admiras de tu competencia? ¿Quién es el mejor que has visto haciendo este trabajo? | Se registra el nombre para la base de caza |
| `Z03` | ¿Autorizas que contactemos a tus referencias y verifiquemos las cifras que declaraste? Indica nombre, cargo y contacto de dos jefes anteriores. | **Eliminatoria**: responder NO sin justificación = descarte automático |

---

## Nivel SUP — 15 preguntas

**Son las mismas preguntas de DIR, con los mismos códigos, la misma redacción y los mismos
C3, C4, señal de 0, peso y marca de eliminatoria.** Verificado ítem por ítem contra el xlsx:
no hay ni una diferencia.

Lo único que cambia es **qué se quita**:

| Pilar | Preguntas | Respecto a DIR |
|---|---|---|
| 1 Iniciativa | R01, R02 | sale R03 |
| 2 Resolución | R04, R05, R06 | iguales |
| 3 Excelencia | R07, R08 | iguales |
| 4 Servicio | R09, R10 | iguales |
| 5 Responsabilidad | R11, R13 | sale R12 |
| 6 Dirección de personas | R14, R15, R16 | sale R17 |
| 7 Integridad | R18 | igual |

✅ **R11 ya está corregida** en `docs/insumos/CAZATALENTOS-SUP.xlsx`: dice «el mejor resultado
que has conseguido», como manda el `.md` (parte 4). El xlsx original traía la redacción de DIR.

⚠️ **R14 y R15: interpretación nuestra, no instrucción literal.** El `.md` pide para SUP «tu
equipo» en vez de «tu área», pero **ninguna de las dos preguntas contiene la palabra «área»** —
ni en DIR ni en SUP. La instrucción está escrita contra un borrador anterior.

Se aplicó su **intención declarada** —«las mismas preguntas del nivel DIR, ajustadas en
alcance»: DIR dirige un área, SUP dirige un equipo— anclando el alcance con la mínima
intervención (`scripts/ajusta-sup-r14-r15.py`):

- **R14** · «Dame un ejemplo, **dentro de tu equipo**, de una persona que producía muy bien…»
- **R15** · «Una persona **de tu equipo** con bajo rendimiento que gestionaste…»

Lo que la pregunta pide no cambia: los C3 y los C4 son idénticos a los de DIR. **Si la clienta
prefiere otra redacción, gana la suya** — y hay que aplicarla antes de publicar el banco de SUP,
porque cambiar un enunciado después invalida la comparación con quien ya respondió.

---

## Nivel OPE — 12 preguntas

Códigos propios `P01`–`P12`. No es un subconjunto de DIR: está reescrito para gente sin
personal a cargo. **No tiene pilar de Dirección de personas.**

| Código | Pilar | Peso | Pregunta |
|---|---|---:|---|
| **P01** | 1 Iniciativa | 1 | Algo que mejoraste en tu trabajo por tu cuenta: qué era, qué cambió y si se sigue haciendo así hoy. |
| **P02** | 1 Iniciativa | 1 | Cuando terminas todo lo que te asignaron antes de tiempo, ¿qué haces? Cuéntanos la última vez. |
| **P03** | 2 Resolución | 1 | Menciona los tres últimos problemas que resolviste en tus últimas 48 horas de trabajo. ¿Cuáles se repiten seguido? |
| **P04** | 2 Resolución | 1 | Te piden tres cosas urgentes al mismo tiempo y no alcanzas a hacer las tres. ¿Qué haces? Cuéntanos un caso real. |
| **P05** | 3 Excelencia | **2** | ¿Cómo sabes que un trabajo tuyo quedó bien terminado? ¿Qué revisas antes de entregarlo? |
| **P06** | 3 Excelencia | 1 | ¿Qué es lo que más se tiene que volver a hacer en tu trabajo y por qué? |
| **P07** | 3 Excelencia | 1 | Algo que entregaste y no quedó como tú querías. ¿Qué hiciste? |
| **P08** | 4 Servicio | 1 | La última vez que ayudaste a alguien sin que te lo pidieran y sin que te beneficiara. ¿Qué hiciste y cuándo? |
| **P09** | 4 Servicio | 1 | Un cliente o compañero está molesto y el error no fue tuyo. ¿Qué hiciste? |
| **P10** | 5 Responsabilidad | 1 | Una vez que te equivocaste en el trabajo: qué pasó, qué hiciste apenas te diste cuenta, a quién avisaste y cuánto tiempo después. |
| **P11** | 5 Responsabilidad | 1 | Una vez que te comprometiste a algo y no pudiste cumplir. ¿Qué hiciste? |
| **P12** | 7 Integridad | 1 · **ELIM** | Encuentras dinero o material de la empresa que nadie reclama. ¿Qué haces? |

Sus C3, C4 y señales de 0:

| | C3 · dato duro | C4 · incomodidad | Señal de 0 |
|---|---|---|---|
| P01 | Qué cambió concretamente | Alguien se opuso o le costó que lo aceptaran | Nada, o nunca se aplicó |
| P02 | Cuándo fue | A quién le sirvió | «Espero» o «no me pasa» |
| P03 | Los tres identificables con su frecuencia | Al menos uno no era emergencia | Los tres emergencias y dos o más se repiten → bandera `REACTIVO` |
| P04 | El caso concreto | Qué dejó sin hacer y a quién avisó | Sin caso real |
| P05 | Qué revisa concretamente | Una vez que revisó y encontró algo mal | «Cuando nadie reclama» |
| P06 | La falla concreta y su frecuencia | Qué hizo o propuso para que dejara de pasar | «Casi nunca hay que rehacer nada» |
| P07 | Qué faltó | Si lo dijo o lo calló | No tiene ningún caso así |
| P08 | Cuándo y a quién | Nadie se enteró | «No recuerdo» |
| P09 | Qué hizo concretamente | Se hizo cargo sin que le correspondiera | Explica que no fue él |
| P10 | El plazo | Qué cambió después | No tiene errores, o lo corrigió callado |
| P11 | Si avisó y cuándo | Qué le costó | Avisó después del plazo, o nunca le ha pasado |
| P12 | A quién lo entrega y cómo | Lo hace aunque nadie se hubiera enterado | Se lo queda, o «si es poca cosa no vale la pena avisar» → **DESCARTE** |

Cierre: los mismos `Z01`–`Z03` de DIR.

---

## Los pilares y sus pesos

```
Puntaje de pilar (%) = puntos obtenidos ÷ (4 × n.º de preguntas del pilar) × 100
Índice RENASER       = Σ (puntaje de pilar × peso del pilar) ÷ 100
```

| Pilar | DIR · MICRO | DIR · MEDIA/GRANDE | SUP | OPE |
|---|---:|---:|---:|---:|
| 1 Iniciativa | 20 | 15 | 15 | 15 |
| 2 Resolución de problemas | 25 | 22 | 22 | 20 |
| 3 Excelencia | 15 | 15 | 15 | 30 |
| 4 Servicio | 10 | 10 | 10 | 20 |
| 5 Responsabilidad y resultados | 20 | 18 | 18 | 15 |
| 6 Dirección de personas | 10 | 20 | 20 | — |
| 7 Integridad | elim. | elim. | elim. | elim. |
| **Total** | **100** | **100** | **100** | **100** |

*En empresa pequeña pesan más iniciativa y resolución, porque hay que armar lo que no
existe. En empresa grande pesa más dirección de personas, porque hay que mover una
estructura. En operativos pesa la excelencia, que es la calidad del trabajo mismo.*

✅ **Ya corregido.** El xlsx que envió la clienta traía **solo la columna MEDIA/GRANDE**; la de
MICRO existía únicamente en el `.md`. La hoja «Cálculo» de `docs/insumos/CAZATALENTOS-DIR.xlsx`
tiene ahora las dos columnas. Los dos juegos suman 100.

El tamaño sale de la **Q5 de la ficha de vacante** —*«¿cuánta gente hay en la empresa?»*—, que
se levanta con el dueño en la etapa 2: **MICRO ≤ 30 personas · MEDIA 31–200 · GRANDE 200+**.
Es la única entrada de este documento que no se decide aquí; la ficha completa está en
[el documento de la prueba técnica](CAZATALENTOS-PRUEBA-TECNICA.md).

---

## Las banderas

Se marcan sobre **el cuestionario completo**, no sobre una pregunta.

| Bandera | Condición | Qué significa | Qué dispara |
|---|---|---|---|
| `SIN_INCOMODIDAD` | Ninguna respuesta contiene C4 | La señal más confiable de relato construido | Repregunta 7 |
| `SOLO_NOSOTROS` | Más de la mitad de las respuestas sin C2 | Estuvo cerca del resultado; no lo produjo | Repregunta 4 |
| `REACTIVO` | En R05/P03: los tres son emergencias y dos o más se repiten | Restricción de anticipación. Viaja con la persona | Se reporta siempre al que contrata |
| `SIN_FRACASO` | Ningún error, ningún conflicto, nadie se opuso, nada salió mal | Nadie con trayectoria real tiene esa hoja de vida | Repregunta 7 |
| `AMBIGUO` | El evaluador dudó entre dos niveles en esa pregunta | Se asignó el menor | No descarta por sí sola |

**Dos o más banderas activas = revisión humana obligatoria antes de avanzar**, sin importar
el puntaje.

### Qué de esto está construido (02/09/2026)

**Dos de las cinco, y como aviso, no como puerta.** `GET /panel/postulaciones/{id}/evaluacion`
calcula `SIN_INCOMODIDAD` y `SOLO_NOSOTROS` contando las columnas `c1_episodio`…`c4_incomodidad`
de `nota_respuesta`: sin IA, sin coste y sobre lo que ya estaba guardado. Se devuelven como
`patrones`, con la frase que dice **de cuántas respuestas sale** cada uno, porque con pocas
respuestas el patrón salta fácil y quien lo lee tiene que poder juzgarlo.

Lo que **no** existe todavía: `REACTIVO`, `SIN_FRACASO` y `AMBIGUO` no se calculan; ninguna bandera
dispara ninguna repregunta —las repreguntas del cuadro de abajo no están automatizadas—; y la
regla de «dos o más banderas = revisión humana obligatoria» **no bloquea nada**: nada en el código
frena un avance por esto. Hoy los dos patrones son avisos que se leen en la ficha —preguntas para
la conversación final, como las alertas—, no una condición que el sistema haga cumplir.

⚠️ **`SOLO_NOSOTROS` está implementada con «la mitad o más», no con «más de la mitad».** El
insumo de la clienta dice *más de* la mitad; el comentario de la `V41` la abrevió a «mitad sin C2»
y el código quedó en `sinAutoria * 2 >= total`. La diferencia es un solo caso —exactamente la
mitad de las respuestas sin C2, que solo puede darse con un número par— y ahí lo que corre levanta
el patrón que la clienta no pidió. **No se ha tocado el código**: queda anotado aquí para
que se decida cuál de las dos reglas manda antes de que alguien lo lea como si fuera el requisito.

Solo cuentan las respuestas que traen las cuatro señales. Mezclar las que no las tienen daría
«nunca se incomodó» en cualquier evaluación de un banco anterior a CAZATALENTOS, que no midió nada
de esto.

---

## Las repreguntas que le tocan a esta etapa

Máximo **3 por candidato**, contando las de la etapa 2. **No las elige el evaluador: se
disparan por condición objetiva**, en este orden. **No puntúan**: confirman o levantan
bandera.

| # | Condición | Repregunta |
|---:|---|---|
| 2 | Ninguna respuesta del pilar de personas contiene un nombre propio | ¿Cómo se llamaba esa persona y qué le dijiste exactamente? |
| 3 | R11 no contiene ningún número | ¿Cuál era el número antes y cuál después? Un aproximado sirve. |
| 4 | Más de la mitad de las respuestas en «nosotros» y nunca en «yo» | De todo eso, ¿qué hiciste tú personalmente y qué decidiste tú? |
| 5 | R13 no indica cuánto tiempo pasó hasta avisar | ¿Cuántas horas o días pasaron entre que lo detectaste y lo avisaste? |
| 6 | R04 no menciona ninguna hipótesis descartada | ¿Qué pensaste al principio que resultó no ser la causa? |
| 7 | Ninguna respuesta contiene C4 | Cuéntanos algo que hayas hecho mal en el trabajo y te haya costado caro. |
| 8 | R15 no menciona plazo ni documento | ¿Le pusiste un plazo? ¿Quedó algo por escrito? |
| 9 | La escala declarada no coincide con los casos contados | ¿Cuánta gente tenías a cargo cuando pasó lo que nos contaste? |
| 10 | La empresa anterior es 5 veces mayor o menor que la contratante | Si aquí no tuvieras el equipo o el sistema que tenías allá, ¿cómo lo harías? |

**Si tras la repregunta la condición persiste, el ítem original baja un nivel y queda la
bandera.**

⚠️ La repregunta de **prioridad 1** no es de esta etapa: es la de textura de la prueba
técnica, y va antes que todas estas. Está en [el documento de la etapa 2](CAZATALENTOS-PRUEBA-TECNICA.md).

---

## El corte de esta etapa

| Condición | Valor |
|---|---|
| Índice RENASER | **≥ 60** |
| Integridad (R18 / P12) | **≠ 0** |
| Banderas activas | **máximo 1** |

**Quien no pasa, no recibe la prueba técnica.** Es a propósito: nadie invierte dos horas
antes de saber si sigue en carrera.

Descartes que se deciden aquí:
1. Integridad = 0.
2. No autoriza la verificación de referencias (`Z03`).

**Todo descarte se reporta con su causa y su cita textual**, nunca como un número bajo.

---

## Quién formula y quién califica

| Paso | Quién |
|---|---|
| Redactar las preguntas | **Nadie**: están escritas y no se reformulan |
| Elegir cuáles se envían | El **nivel** del puesto. Sin selección, sin azar, sin cuotas |
| Poner el puntaje 0–4 | **Agente de IA**, con cita textual obligatoria |
| Levantar banderas | **Determinista**, contando C2 y C4 sobre el cuestionario |
| Disparar repreguntas | **Determinista**, por condición objetiva |
| Revisar y ajustar | **Persona**, siempre justificando el cambio |

**La IA no formula ninguna pregunta de esta etapa.** La regla general del sistema es que un
agente solo puede generar texto que no entre en una nota — y estas preguntas son el
instrumento entero.

---

## Estado: sin calibrar

⚠️ La propia clienta lo declara pendiente (parte 10 de su documento):

1. **Que Darren califique con este método** las respuestas de 3 candidatos reales. Si su
   puntaje coincide con el del sistema en **15 de 18 preguntas**, está calibrado. Si no, se
   ajustan las señales — no el método.
2. **Aplicarlo a 8–10 personas del equipo actual** cuyo rendimiento ya se conoce. Si los
   mejores no superan 75 en el índice, las señales están mal.
3. **Cronometrar el primer envío real.** Si un candidato tarda más de 60 minutos, hay
   preguntas que piden demasiado y se acortan.

**Consecuencia para la implementación:** señales de 0, C3 esperado, C4 esperado, pesos y
cortes tienen que vivir como **datos en tablas**, no como código. Si van en tablas,
recalibrar es un `UPDATE`. Si acaban en condicionales, cada ajuste de la clienta es un
despliegue.

---

## Lo que este documento no cubre

- La prueba técnica, sus riesgos, la ficha de vacante y los diccionarios de textura →
  [CAZATALENTOS-PRUEBA-TECNICA.md](CAZATALENTOS-PRUEBA-TECNICA.md).
- El índice combinado y la decisión final: se calculan cuando existen las dos notas, y están
  en el documento de la etapa 2.
- Cómo encaja esto en el sistema que ya está construido (banco v3, versiones, motor de
  puntuación).
