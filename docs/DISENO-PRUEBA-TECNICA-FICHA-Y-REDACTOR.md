# Diseño · Prueba técnica, ciclo 1: la ficha y el REDACTOR

**Estado: aprobado en conversación (2026-08-28). Pendiente de plan de implementación.**

El primer ciclo de la etapa 2 del método CAZATALENTOS
([qué es la etapa completa](CAZATALENTOS-PRUEBA-TECNICA.md)): la ficha de vacante que
llena el dueño y el agente que convierte esa ficha en el borrador del cuestionario
técnico. La rendición del candidato, la calificación, la textura y el cierre del filtro
son ciclos posteriores y este documento no los diseña — solo les deja la puerta abierta.

## Decisiones ya tomadas

1. **Autoservicio completo.** El dueño llena la ficha en su panel y el dueño aprueba el
   borrador. Nadie de RENASER está en el camino. (Se desvía a sabiendas del documento de
   la clienta, que pone a una persona consultora en medio; el formulario guiado hace de
   guion de esa conversación.)
2. **Enfoque A: el cuestionario aprobado se materializa como un banco CRITERIOS ligado a
   la vacante.** Nada de motores nuevos: las preguntas son `ABIERTA` con su C3/C4/señal
   de 0, y la calificación (ciclo 2) será el mismo `CalificacionCriterios` del banco.
   Se descartaron: meterla en la plantilla de prueba del entregable (calificaría con
   rúbrica global, no por criterios) y tablas dedicadas (duplicar el motor: la lección
   del 675/100).
3. **Un solo agente nuevo en todo el proyecto: el REDACTOR.** No puede llamarse
   CAZATALENTOS — ese código ya existe en el catálogo `agente` (V11) y hace otra cosa.
   La calificación reutiliza al EVALUADOR; la textura será conteo mecánico más un juicio
   que puede cargar el propio EVALUADOR; las repreguntas se disparan por umbral.
4. **La prueba del puesto actual (entregable PDF) no se toca.** Quedan los dos caminos y
   cada vacante usa uno.
5. **La regla de la clienta es ley: la IA propone, la persona publica.** Un borrador
   jamás llega solo a un candidato. Una pregunta técnica mal formulada descarta gente en
   silencio; por eso la publicación es un acto humano y auditado.

## Flujo 1 · El dueño llena la ficha

1. Al crear (o abrir) su vacante, el panel le pide la ficha: las 10 preguntas de la
   clienta, textuales, respondidas con sus palabras. Obligatorias todas menos Q10
   (espejo), que solo aplica si ya contrató antes para el puesto.
2. Tres momentos con ayuda especial:
   - **Q5 estructura:** escribe cuánta gente hay en la empresa y cuánta tendrá a cargo
     (números). El sistema deriva el **TAMAÑO** solo: ≤30 MICRO · 31–200 MEDIA ·
     200+ GRANDE — y le sugiere la `version_pesos` CAZATALENTOS que corresponde. Es la
     única dependencia de la etapa 2 hacia la etapa 1.
   - **Q2 riesgos:** no un párrafo — **4 riesgos cortos, ordenados por velocidad de
     daño**. El orden lo decide el dueño y después manda en el cuestionario.
   - **Eliminatorias:** máximo 2. El formulario muestra la pregunta de control de la
     clienta («si fuera excelente en todo menos en esto, ¿lo contratarías igual?») y
     obliga a reducir si marca más.
3. Con todo lo obligatorio, la ficha pasa a **COMPLETA** y se enciende «Generar
   cuestionario técnico». Antes, ese botón no existe.
4. La ficha se corrige libremente mientras ningún candidato haya rendido la prueba
   técnica de esa vacante; desde la primera rendición, vara quieta.
5. Todo cambio queda en la auditoría existente (quién, qué, cuándo).

## Flujo 2 · De la ficha al cuestionario aprobado

1. «Generar» encola un trabajo en la misma fila de IA, con el mismo **tope mensual**. Si
   el tope está agotado, se avisa ahí mismo y no se encola nada.
2. El **REDACTOR** recibe la ficha y la estructura fija del nivel (DIR 12 · SUP 10 ·
   OPE 8) y escribe el borrador según la receta mecánica de la clienta: 2 de experiencia
   y escala; por riesgo —en el orden del dueño— una de experiencia con magnitudes y una
   de procedimiento exacto (el riesgo 1 lleva además una de control preventivo); la de
   requerimiento (Q9); el dilema donde dos virtudes del negocio chocan; y, solo en DIR,
   la muestra de trabajo marcada **PRESENCIAL**, que jamás se envía al candidato.
3. El agente deja lista también la **guía de calificación** de cada pregunta: C3 (dato
   duro esperado), C4 (la parte incómoda) y señal de 0. El candidato nunca la ve.
4. **La aduana del borrador** revisa antes de mostrar: estructura y cantidades exactas
   del nivel, la presencial marcada (y solo en DIR), guía completa en toda pregunta
   puntuable, y ninguna pregunta de la lista prohibida (estado civil, hijos, salud,
   embarazo, religión, política, sindicato, origen étnico). Lo que no pasa vuelve al
   agente con el error explicado; si persiste, es fallo visible — nunca un borrador a
   medias presentado como bueno.
5. El dueño revisa en su panel: edita cualquier pregunta con sus palabras, regenera (el
   nuevo borrador reemplaza al anterior; solo hay uno vivo por vacante) o descarta.
6. **«Aprobar y publicar» es del dueño y es el único camino.** Al aprobar, el
   cuestionario queda amarrado a esa vacante. Desde la primera rendición, congelado.

## Flujo 3 · Cuando algo sale mal

| Situación | Qué pasa |
|---|---|
| Tope mensual agotado | Aviso inmediato, nada encolado |
| La generación falla | La fila reintenta (solo FALLIDO reintenta, como hoy); agotados los reintentos, «falló, vuelve a intentar» |
| El borrador no pasa la aduana | Se devuelve al agente con el error; si persiste, fallo visible |
| El dueño regenera | Las veces que quiera antes de aprobar; cada intento cuesta una llamada, el tope frena |
| Cambio tras aprobar, nadie rindió | Vuelve a borrador, edita o regenera libre: nadie fue medido |
| Cambio tras aprobar, alguien rindió | Vara quieta. Tipeo → corrección editorial auditada. Cambio de sentido o de guía → solo recalificando a todos (mecanismo del banco, ya existe) |
| La ficha cambia tras generar | El cuestionario no cambia solo: se marca desactualizado y se ofrece regenerar, sujeto a la regla de arriba |

## Lo técnico que hay debajo

### Modelo de datos (migración nueva — verificar el número libre contra los otros worktrees al implementar: tres ramas ya reclamaron el mismo una vez)

- **`ficha_vacante`** (tabla nueva): `vacante_id` único, `organizacion_id`, las 10
  respuestas en texto (`q1_resultado` … `q10_espejo`, Q10 opcional),
  `gente_en_empresa` y `gente_a_cargo` (enteros), `riesgo_1` … `riesgo_4` (texto corto,
  el orden es la velocidad de daño), `familias` (F1–F7, una o más), `eliminatoria_1`,
  `eliminatoria_2`, `requerimientos` (máx. 3), `tamano` (MICRO/MEDIA/GRANDE, derivado),
  `estado` (BORRADOR/COMPLETA), marcas de auditoría.
- **`version_banco.vacante_id`** (columna nueva, opcional): NULL = banco por nivel como
  hoy; con valor = cuestionario técnico de esa vacante. La selección de banco para la
  etapa técnica mira primero el de la vacante. El scoping multiempresa sale de la
  vacante.
- **`pregunta.presencial`** (booleana nueva): la muestra de trabajo se guarda pero el
  portal jamás la envía; se muestra solo al dueño para su entrevista.
- **Catálogo `agente`:** ampliar el CHECK de V11 con `REDACTOR`.

### El agente REDACTOR

- Entrada: la ficha completa + nivel + estructura del nivel. Salida: JSON con las
  preguntas (código, bloque, enunciado, C3, C4, señal de 0, presencial) — **texto que no
  entra en ninguna nota hasta que un humano publica**, cumpliendo la regla vigente del
  sistema.
- Corre por la cola existente (`trabajo_ia`), nuevo tipo de trabajo por vacante, con
  reintentos y tope mensual como todos.
- El peso de toda pregunta técnica es 1: el índice técnico es obtenidos ÷ (4 × n) × 100,
  sin ponderación por ítem.

### Endpoints (los nuevos, lo demás se reutiliza)

- `GET/PUT /api/v1/panel/vacantes/{id}/ficha` — upsert de la ficha, permisos de quien
  edita la vacante en su organización.
- `POST /api/v1/panel/vacantes/{id}/cuestionario-tecnico/generacion` — encola al
  REDACTOR (exige ficha COMPLETA).
- La revisión, edición y publicación del borrador reutilizan los endpoints del banco de
  preguntas: el borrador ES una `version_banco` en BORRADOR con `metodo_calificacion =
  'CRITERIOS'` y `vacante_id`, así que la aduana de coherencia, la corrección editorial
  y la publicación ya existen. Solo se ajusta el alcance de permisos: el dueño edita y
  publica únicamente los bancos de SUS vacantes, jamás los bancos por nivel de la
  plataforma.

### Cómo se prueba

- La receta (bloques y cantidades por nivel) y la aduana: puras, sin IA ni base, contra
  casos a mano — estilo `FormulasCazatalentos`.
- El recorrido ficha → generar → aprobar: prueba de integración con el agente simulado.
- La regla de vara quieta y el gate del botón: pruebas de servicio.
- Antes del PR: ensayo real con backend levantado, una ficha de verdad y una generación
  de verdad con DeepSeek, más el QA adversario de siempre.

## Fuera de alcance del ciclo 1

La rendición por el candidato y su gate (solo quien aprobó la RENASER), la calificación
técnica, la textura y sus diccionarios, la repregunta, el índice combinado 45/55 y los
descartes automáticos. El diseño de este ciclo les deja los cimientos: el banco por
vacante ya nace CRITERIOS con guía completa, y la familia ya queda guardada en la ficha
para cuando la textura llegue.
