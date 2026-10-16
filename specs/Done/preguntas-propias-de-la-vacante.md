# Preguntas propias de cada vacante: el banco y la prueba en un solo editor
Estado: TERMINADO
spec, cuando la fase 1 esté en producción.

## Objetivo

Hoy, para que un candidato responda preguntas, alguien tiene que llenar un Excel con los 15
formatos del método de RENASER e importarlo. El panel no tiene dónde escribir una pregunta. Eso
sirve a RENASER, pero no a las demás empresas que van a usar la plataforma.

Esta spec deja que **cualquier empresa escriba, desde el panel y para cada vacante, las preguntas
que responde quien postula**:

- con cuatro tipos de pregunta que cualquiera entiende;
- **agrupadas en criterios con nombre**, que suman 100 puntos y son las columnas del reporte;
- con ayuda de la IA para redactarlas;
- con una guía propia para que la IA califique lo abierto.

En la **fase 2**, ese mismo editor arma también la prueba del puesto. Solo le suma entregables y
reloj, y sustituye a los dos instrumentos que hay hoy.

## Contexto

### Comportamiento actual

- **El banco del Perfil Integral es por empresa y por nivel, no por vacante.**
  - Al postular, `ServicioEvaluacionImpl.crearAlPostular` fija la última versión publicada del
    nivel del puesto (`VersionBancoRepository.laPublicadaDelNivel`).
  - **Una empresa sin banco propio rinde el de RENASER.** De quién es el banco lo decide
    `DuenoDelInstrumento`: con `organizacion.banco_propio` apagada (el valor por defecto, V37),
    devuelve el de la plataforma. Para tener uno propio, la empresa «personaliza» el banco
    (`ServicioPersonalizacionImpl`), que copia el de RENASER y enciende la bandera.
  - También exige una plantilla de evaluación publicada. El CHECK de la V43 sobre `evaluacion`
    la pide siempre que `proposito = 'PERFIL_INTEGRAL'`.
- **El banco solo se carga por Excel.**
  - El backend tiene `POST /panel/banco-preguntas/importaciones` y endpoints para crear y editar
    preguntas en borrador (`BancoPreguntasController`).
  - El panel, `src/panel/configuracion/BancoDePreguntas.tsx`, solo importa, publica, archiva,
    descarta y renombra. Su propio comentario dice que el editor de preguntas no está
    construido.
- **Admite 15 formatos** (`DtosBancoPreguntas.CrearPregunta`, CHECK `pregunta_tipo_check`):
  - los seis del v0.1;
  - los ocho del v3;
  - `ABIERTA`.
- **Hay dos motores de calificación**, según `version_banco.metodo_calificacion`:
  - `NULL`: claves versionadas del v3, en `ServicioCalificacionImpl.puntuarItem`. Su
    `switch (p.getTipo())` termina en `default -> null`, así que **un tipo que no conoce se
    queda sin puntuar sin avisar**.
  - `CRITERIOS`: el método C1–C4 de RENASER, en `CalificacionCriterios`. `calificarLoCerrado`
    sale antes con cero para este método.
- **El peso es un multiplicador, no un puntaje.** `pregunta.peso` es un `Short` con CHECK 0..2:
  1 vale hasta 3 puntos y 2 hasta 6.
- **La IA califica las abiertas de 0 a 4.**
  - Lo hace `AgenteEvaluador`, con `FORMATO` o `FORMATO_CRITERIOS`.
  - Guarda en `nota_respuesta`, cuyo `puntaje` tiene CHECK 0..4 (V12).
- **La nota del Perfil Integral mezcla currículum y evaluación.**
  - La calcula `PuenteCalificacionIaImpl.recalcularNotaDeLaEtapa`, con los pesos de
    `peso_componente_perfil`.
  - La parte de evaluación sale de `ServicioCalificacion.notaCombinada`, que pondera cerradas y
    abiertas **por cuántas preguntas tiene cada mitad**.
- **El cuestionario técnico ya es un banco por vacante.**
  - Es una `version_banco` con `tipo_banco = 'VACANTE'` y `vacante_id`.
  - Los índices parciales de la V42 permiten, por vacante, un solo `BORRADOR` y una sola
    `PUBLICADA`.
  - Lo redacta el agente `REDACTOR` desde la ficha del puesto (`AgenteRedactor`, con
    validación y un reintento).
  - Lo rinde una `evaluacion` con `proposito = 'CUESTIONARIO_TECNICO'`, que se crea en
    `crearTecnicaAlEntrar`. Ese método lo busca con `findFirstByVacanteIdAndEstado`, **sin
    mirar el propósito**.
- **La prueba del puesto es otro modelo.**
  - Se compone de `version_plantilla_prueba`, el catálogo `pregunta_prueba`, `entregable_requerido`
    y una rúbrica en `criterio`.
  - Cada criterio tiene `puntos` y `metodo_verificacion` (`AGENTE` o `PERSONA`), y la rúbrica
    suma 100.
  - La califica `AgentePruebaPuesto`, criterio por criterio, leyendo todas las respuestas y
    entregas.
  - **La red de seguridad está en `PuentePruebaIaImpl.guardarNotasPrueba`**: descarta códigos
    desconocidos, criterios reservados a una persona y notas sin explicación, y acota cada nota
    a su máximo.
  - La nota de la etapa solo se escribe si la rúbrica está entera
    (`ponderarSiLaRubricaEstaEntera`).
  - La guía `guia_calificacion` (hasta 2000 caracteres) viaja en el `system`, cerrada con un
    rótulo que lleva una marca sorteada en cada calificación.
  - El catálogo `pregunta_prueba` se lee sin filtrar por empresa. Es una fuga conocida (ver
    `docs/PRUEBA-DEL-PUESTO.md`).
- **La vacante elige el instrumento en la sección «Qué responderá quien postule»** de
  `src/panel/vacantes/Vacante.tsx`.
  - `aplicaEvaluacion` enciende o apaga el banco.
  - `instrumentoEtapaTecnica` es `PLANTILLA` o `CUESTIONARIO_TECNICO`.
  - Desde la primera postulación, la guarda `exigirVaraQuieta` de `ServicioVacantesPanelImpl`
    no deja cambiar nada (`docs/DECISION-UNA-VACANTE-UNA-VERSION.md`).
- **El ranking de «Prueba del puesto» pinta una columna por criterio de la rúbrica**, con celdas
  como «0/20», «0/15» o «10/10» teñidas según cuánto cubren (`src/panel/vacantes/ranking.ts`).
  - Un criterio que no pondera no se tiñe.
  - Un criterio de `PERSONA` queda en blanco hasta que alguien le pone nota.

### Los criterios, en las dos fases

**Un criterio es lo que se califica y lo que se ve como columna**: tiene nombre, qué evalúa,
puntos y quién lo califica. Los criterios de una versión suman 100. **No hay categorías**: eran
un criterio sin puntos ni calificador, y tener las dos capas a la vez haría que acabaran diciendo
cosas distintas.

Las dos fases comparten la misma regla, con una sola excepción:

> **Los puntos suben de las preguntas al criterio.** El criterio vale la suma de sus
> preguntas. **La excepción es lo abierto de la fase 2**: allí las preguntas abiertas y los
> entregables no llevan puntos, y el criterio se califica entero, porque un mismo entregable se
> mira desde varios criterios («Claridad del tablero» e «Indicadores correctos»).

| | Fase 1 · Banco del Perfil Integral | Fase 2 · Prueba del puesto |
|---|---|---|
| Pregunta cerrada | Tiene sus puntos y la califica el sistema | Igual |
| Pregunta abierta | **Tiene sus puntos** y la IA la califica **una por una** | **No tiene puntos**: la IA califica el criterio entero |
| Entregable | No existe | No tiene puntos; lo califica el criterio (IA o persona) |
| Puntos del criterio | La suma de sus preguntas | La suma de sus cerradas + los de su parte calificada |
| Quién califica | Se deduce: sistema en las cerradas, IA en las abiertas | Sistema en las cerradas; IA o persona en lo demás, a elección |

**Quien implemente no debe unificar los dos caminos de calificación.** El banco es un
cuestionario y se califica pregunta a pregunta. La prueba se califica por criterio, como hoy.

### Decisiones ya tomadas con el usuario (11/09, 28/09 y 29/09/2026)

1. **Cuatro tipos**: abierta, opción única, opción múltiple y escala.
2. **El banco actual de RENASER no se toca.**
   - Siguen igual el método `CRITERIOS`, los bancos por nivel, el importador de Excel y los 15
     formatos.
   - Hay postulantes rindiéndolo.
   - El editor nuevo vive al lado.
3. **Todo suma 100.** El multiplicador 0/1/2 no se usa en lo nuevo.
4. **La nota final la calcula el sistema, nunca la IA.**
   - La IA solo pone la nota de lo abierto, entre 0 y el máximo que le toca.
   - Nunca ve la puntuación de las cerradas.
5. **Sin banderas, repreguntas automáticas ni pares de consistencia.** Son del método RENASER.
6. **Guía de calificación por versión**, con la misma envoltura anti-inyección de la prueba del
   puesto.
7. **Un botón de recomendaciones por IA.** La IA propone preguntas y criterios; una persona
   decide.
8. **Las preguntas son de la vacante**, no del nivel.
   - Ni las preguntas propias ni la prueba se vinculan a un nivel (29/09).
   - El nivel sigue siendo un dato del puesto, porque de él dependen los pesos del currículum
     (`peso_dimension` y `peso_criterio`) y el contexto que reciben los agentes de la prueba y
     de la simulación. No se quita ni se hace editable por empresa.
   - Los pesos de la decisión y el desplegable «Pesos de la decisión» de la vacante se quedan
     como están: RENASER elige ahí versiones como «CAZATALENTOS · MICRO».
9. **La nota del banco entra en el Perfil Integral junto al currículum, como hoy.**
10. **Una sola prueba del puesto (fase 2).** Si no pide entregables, es un cuestionario.
11. **Cada abierta tiene un campo opcional «Qué debe tener una buena respuesta».**
    - Es donde RENASER puede escribir su dato duro, su parte incómoda y su señal de cero.
    - Así el cuestionario técnico no pierde su método al unificarse.
12. **Criterios en lugar de categorías, en las dos fases.**
    - Son de la versión y se congelan al publicar.
    - Se reutilizan copiando de otra vacante; no hay catálogo por empresa.
13. **Un criterio puede mezclar cerradas y abiertas** (29/09).
    - Su nota es la suma de las dos partes.
    - La ficha enseña de dónde sale cada punto («Sistema 8/10 + IA 16/20»).
    - En el ranking, un criterio mixto sigue pendiente mientras falte su parte abierta.
14. **Los criterios los propone la IA al armar la prueba, no al calificar.** Si los inventara al
    calificar, cada candidato se mediría con una vara distinta.
15. **Una persona puede ajustar a mano la nota de una abierta** (29/09).
    - Lo hace quien tiene `ajustar_nota`, con un motivo obligatorio.
    - La nota de la IA queda a la vista, y la IA ya no vuelve a tocar la ajustada.
    - También sirve para calificar a mano una abierta que la IA no pudo calificar.
16. **Sin tope de preguntas** (29/09). La empresa pone las que quiera.
17. **Las instrucciones de la IA se pueden corregir con candidatos dentro, y entonces se
    recalifica a todos** (29/09).
    - Son la guía de calificación, el «qué debe tener» de cada abierta y el «qué evalúa» de
      cada criterio.
    - Al guardar el cambio, la IA vuelve a calificar a todos los que ya tienen nota, para que
      nadie quede medido con una guía distinta.
    - Es una de las dos excepciones a «una vacante, una versión» (la otra es la decisión 20):
      la vara cambia, pero para todos a la vez. Preguntas, tipos, textos, opciones y el
      criterio de cada pregunta siguen congelados.
18. **En la fase 2, las vacantes nuevas solo usan el editor** (29/09). También las de RENASER:
    sus plantillas y el cuestionario CAZATALENTOS dejan de ofrecerse a vacantes nuevas.
19. **Cada empresa usa solo su propio banco** (29/09).
    - El banco de RENASER deja de prestarse a las demás empresas en sus vacantes nuevas. Una
      empresa sin banco propio elige entre «Sin evaluación» y «Preguntas propias».
    - RENASER conserva su banco por nivel porque es suyo, no por ser una excepción: la regla es
      la misma para todas.
    - Ninguna empresa puede ya copiarse el banco de RENASER («personalizar el banco»). Las que
      ya lo copiaron conservan su copia, que es suya.
    - Las vacantes que ya rinden el banco prestado siguen igual hasta cerrarse.
20. **Los puntos se pueden cambiar con candidatos dentro, y se recalcula a todos al instante,
    sin IA** (29/09).
    - Son los puntos de cada pregunta y los de cada opción o nivel. Sirve, sobre todo, para
      arreglar una clave mal puesta.
    - Las cerradas se recalculan con los puntos nuevos; las abiertas ya calificadas se
      escalan en proporción (12 de 20 pasa a 9 de 15). Nadie cambia de etapa.
21. **Sin erratas** (29/09). Desde la primera postulación no se toca ningún texto: ni el
    enunciado, ni el de las opciones, ni el nombre de un criterio. Con «corregir una errata»
    se podía reescribir una pregunta que alguien ya había respondido.
22. **Puntos enteros; las abiertas de 0 puntos no van a la IA** (30/09).
    - Los puntos de preguntas, opciones y niveles son enteros. Así «suma exactamente 100» es
      una cuenta sin redondeos, y la IA no falla la validación por un 33,33. La nota que pone
      la IA, y la que sale de escalar en proporción, sí lleva hasta dos decimales.
    - Una abierta de 0 puntos no gasta IA: no puede sumar nada.

### Pantallas o procesos afectados

- **Panel**:
  - la sección «Qué responderá quien postule» de la vacante;
  - una pantalla nueva, el editor de preguntas de la vacante;
  - la ficha del candidato, pestaña Perfil Integral: el desglose de la evaluación;
  - la configuración del banco (`BancoDePreguntas.tsx`), solo para una empresa sin banco
    propio.
- **Portal del candidato**: la evaluación (`src/paginas/evaluacion/Evaluacion.tsx` y
  `Formatos.tsx`).
- **Backend**:
  - creación de la evaluación al postular;
  - validación al guardar respuestas (`validarDetalle`);
  - calificación de lo cerrado;
  - agente evaluador;
  - nota del Perfil Integral;
  - desglose `GET /panel/postulaciones/{id}/evaluacion`;
  - publicación de la vacante;
  - personalización del banco (`POST /panel/organizacion/personalizacion` y
    `POST /panel/plataforma/empresas/{id}/personalizacion/{instrumento}`).
- **Fase 2**: la etapa técnica entera (intento, entregas, calificación, ranking y Excel) y las
  columnas de criterio del ranking del Perfil Integral.

### Referencias

- `docs/CAZATALENTOS-BANCO-RENASER.md`: el método que no se toca.
- `docs/PRUEBA-DEL-PUESTO.md`: la guía de calificación, la red de seguridad y la fuga del
  catálogo.
- `docs/DECISION-UNA-VACANTE-UNA-VERSION.md`: por qué la vara no se mueve con candidatos dentro.
- `docs/DISENO-PRUEBA-TECNICA-FICHA-Y-REDACTOR.md`: el patrón del agente que redacta.
- [DESIGN.md del panel](../../../RenaserOsPostulantes/DESIGN.md). **No** el `DESIGN.md` de este
  repositorio, que es del mundo anterior.

## Alcance

### Fase 1 — las preguntas del Perfil Integral, por vacante (este trabajo)

**Incluye:**

- **Elegir en cada vacante de dónde salen sus preguntas**, entre tres opciones:
  - «Sin evaluación»;
  - «El banco de la empresa para su nivel», que solo se ofrece si la empresa tiene uno
    **propio** publicado para ese nivel;
  - «Preguntas propias de esta vacante».
- **Dejar de prestar el banco de RENASER** a las vacantes nuevas de otras empresas, y dejar de
  ofrecer que una empresa se lo copie (decisión 19).
- **El editor de preguntas de la vacante, agrupado por criterios**, con:
  - los criterios: nombre, qué evalúa y los puntos, que se suman solos;
  - los cuatro tipos de pregunta, cada una con sus puntos y dentro de un criterio;
  - «Qué debe tener una buena respuesta» en las abiertas;
  - la guía de calificación de la versión;
  - los minutos estimados;
  - el orden de criterios y preguntas;
  - un balance siempre visible (cuántos puntos van de 100);
  - el botón de publicar.
- **«Copiar de otra vacante»** de la misma empresa, con sus criterios.
- **«Recomendaciones por IA»**: propone criterios y preguntas a partir de los datos de la
  vacante, del puesto y de la solicitud de talento.
- **Cambiar los puntos después de publicar**, también con candidatos dentro, recalculando a
  todos al instante (decisión 20).
- **El portal del candidato responde los cuatro tipos.**
- **La calificación**:
  - las cerradas, el sistema;
  - las abiertas, la IA, pregunta a pregunta, en puntos y con la red de seguridad del backend;
  - la nota del banco entra en el Perfil Integral.
- **El desglose en la ficha del candidato**: cada criterio con su nota, de dónde sale cada punto
  y sus preguntas.
- **Ajustar a mano la nota de una abierta** desde ese desglose, con motivo, o ponerla si la IA
  no pudo.
- **Corregir las instrucciones de la IA con candidatos dentro**, recalificando a todos.

**Queda fuera:**

- La etapa técnica (fase 2).
- Cualquier cambio en el banco de RENASER:
  - el método `CRITERIOS`;
  - los bancos por nivel;
  - el importador y la pantalla de configuración del banco (salvo lo que ve una empresa sin
    banco propio, comportamiento 1);
  - los 15 formatos.
- Borrar del código el préstamo del banco (`DuenoDelInstrumento` con `Instrumento.BANCO`).
  Sigue vivo para las vacantes que ya rinden el banco prestado; se retira en una limpieza
  aparte cuando ya no quede ninguna.
- Los pesos. El multiplicador 0/1/2 de cada pregunta no existe en el editor (decisión 3), pero
  los pesos entre etapas (`peso_etapa`) y entre currículum y evaluación
  (`peso_componente_perfil`) siguen como están, y se siguen prestando igual que hoy.
- Banderas, repreguntas, pares de consistencia y pilares con peso.
- Columnas de criterio del banco en el ranking y en su Excel (van en la fase 2).
- Ajustar a mano una cerrada o un criterio entero del banco: lo cerrado sale de los puntos de la
  versión, y el criterio de la suma de sus preguntas.
- Ajustar las abiertas del banco de RENASER (`CRITERIOS`): sigue como está.
- Criterios del banco que califique una persona de entrada: en la fase 1 las abiertas las
  califica la IA, y una persona solo ajusta o completa lo que la IA dejó (punto 15).
- Barajar las opciones al candidato.
- Un catálogo de criterios por empresa.
- Pasar las vacantes existentes a preguntas propias. Siguen como están.
- Repartir los puntos automáticamente hasta llegar a 100.

### Fase 2 — la prueba del puesto con el mismo editor (siguiente trabajo)

**Incluye:**

- **El mismo editor en la etapa técnica de la vacante, más entregables.** Cada entregable
  tiene:
  - nombre, regla y formato (como hoy);
  - si es obligatorio;
  - «Qué debe tener una buena entrega».
  
  Los entregables no llevan puntos: los califican los criterios.
- **Criterios con parte automática y parte calificada.** Cada criterio tiene:
  - sus cerradas, cuyos puntos suman solos: es la **parte automática**;
  - los puntos de su **parte calificada**: lo abierto y los entregables;
  - **quién califica la parte calificada**: la IA o una persona.
  
  Un criterio puede ser solo de cerradas, solo calificado o mixto. **Todos suman 100.**
- **El reloj**: la modalidad, los minutos y el cambio inesperado se comportan como hoy.
- **Sin entregables es un cuestionario**, sin que nadie tenga que elegirlo.
- **Las vacantes nuevas solo arman su prueba con el editor**, también las de RENASER. Ya no
  eligen entre «plantilla» y «cuestionario técnico».
- **La sección «Prueba técnica» de la vacante se simplifica** en las vacantes nuevas:
  desaparecen los desplegables «Qué rendirá en la etapa técnica» y «Qué prueba del puesto
  rendirá», «Cuánto tiempo tendrá» pasa al editor, y la tarjeta «Preparar la prueba técnica»
  (ficha y cuestionario del `REDACTOR`) se sustituye por el estado de la prueba y un botón.
  «Pesos de la decisión» se queda.
- **La calificación de la prueba**:
  - las cerradas, el sistema;
  - la parte calificada de cada criterio, la IA leyendo todas las respuestas abiertas y
    entregas legibles, como hoy `PRUEBA_PUESTO`, con los mismos datos de la vacante que en la
    fase 1;
  - los criterios de persona y lo que no da texto (video, imagen, enlace), una persona.
  - **La nota de la etapa solo existe cuando todos los criterios están enteros.**
- **Los rankings y sus Excel**: una columna por criterio en «Prueba del puesto», como hoy, y
  las columnas de los criterios del banco en «Perfil Integral».
- **Las recomendaciones por IA** también proponen entregables y la rúbrica.
- **«Copiar de otra vacante»** copia también los entregables, las partes calificadas con su
  calificador y el tiempo, con las mismas reglas de la fase 1 (cerradas y archivadas incluidas).
  La vista previa enseña también los entregables y el tiempo.
- **Ajustar a mano la parte calificada de un criterio**, con motivo, como hoy en la prueba del
  puesto. En la fase 2 el ajuste es por criterio y no por pregunta, porque ahí la IA califica
  por criterio.
- **Corregir las instrucciones de la IA con candidatos dentro**, como en la fase 1: recalifica a
  todos con el agente de la prueba.
- **Cambiar los puntos con candidatos dentro**, como en la fase 1: los de las cerradas y los
  de la parte calificada de cada criterio, que se escala en proporción. Sin IA.

**Queda fuera:**

- Las vacantes que ya rinden `PLANTILLA` o `CUESTIONARIO_TECNICO`: siguen funcionando igual, sin
  migrar su contenido.
- Reutilizar el catálogo `pregunta_prueba`. Las preguntas son de la vacante, y eso cierra por
  construcción la fuga entre empresas.
- Pasar las plantillas de RENASER al editor. Para sus vacantes nuevas, RENASER escribe la prueba
  en el editor, o la copia de otra vacante que ya la tenga. Los scripts
  `cargar-prueba-de-la-empresa.py` y `asignar-prueba-a-vacante.py` dejan de servir para
  vacantes nuevas.

## Comportamiento esperado

### Fase 1

1. **La vacante elige de dónde salen sus preguntas.**
   - En «Qué responderá quien postule», el usuario ve las tres opciones del alcance.
     - «El banco de la empresa para su nivel» solo aparece si la empresa tiene uno **propio**
       publicado para ese nivel. El de RENASER no cuenta para las demás empresas.
     - Una vacante nueva viene con **«El banco de la empresa para su nivel»** marcada si la
       empresa tiene uno propio publicado para ese nivel. Si no, viene con **«Preguntas
       propias»**. Así una vacante nueva de RENASER sigue rindiendo su banco calibrado, salvo
       que alguien elija otra cosa, y la de otra empresa nace con sus propias preguntas.
     - Las existentes conservan lo que tenían: banco del nivel, o sin evaluación si estaba
       apagada.
     - **Una vacante existente que rinde el banco prestado** lo enseña marcado y con su nombre
       real, «El banco de RENASER para su nivel», para no llamarlo «de la empresa». Si se cambia
       a otra opción, esa desaparece y no se puede volver a elegir.
   - **Personalizar el banco deja de ofrecerse.**
     - Encender la personalización del banco se rechaza, la pida la empresa o la plataforma. Los
       otros tres instrumentos (pesos, plantillas de evaluación y pruebas) siguen como están.
     - Una empresa que ya lo tiene personalizado conserva su banco, que es suyo.
     - En la configuración del banco, una empresa sin banco propio ya no ve el de RENASER en
       solo lectura ni la invitación a personalizarlo. Ve «Tu empresa no tiene banco por nivel:
       las preguntas se escriben en cada vacante».
   - Con «Preguntas propias» sale el estado de sus preguntas («Sin preguntas», «Borrador · 68 de
     100 puntos», «Publicadas · 4 criterios · 12 preguntas») y el enlace «Escribir las
     preguntas».
2. **El editor**, en `/admin/vacantes/:id/preguntas`, está **agrupado por criterios**.
   - Arriba, un balance siempre visible: puntos de 100, número de criterios y preguntas, y los
     avisos que frenan la publicación.
   - Junto a la guía de calificación, una línea fija dice con qué califica la IA: «La IA
     califica con esta guía, con lo que debe tener cada respuesta y con los datos de la
     vacante que se llenaron al crearla».
   - Debajo, un bloque por criterio con su nombre, qué evalúa y sus puntos («30 pts · sistema 10
     + IA 20»). Dentro van sus preguntas.
   - Cada criterio y cada pregunta se puede editar, mover arriba o abajo y quitar. Una pregunta
     se cambia de criterio desde un selector en la propia pregunta, no arrastrando.
   - Botones: «Agregar criterio», «Recomendaciones por IA», «Copiar de otra vacante» y
     «Publicar». Cada criterio tiene su «Agregar pregunta».
   - **Quien no quiera criterios no está obligado a pensar en ellos.** Si agrega una pregunta
     sin haber creado ninguno, se crea solo un criterio «General», que se puede renombrar.
3. **Escribir un criterio.**
   - Tiene un nombre y «Qué evalúa», que es opcional (hasta 1000 caracteres) y llega a la IA.
   - Sus puntos no se escriben: son la suma de sus preguntas.
   - Quién lo califica tampoco se elige: el sistema sus cerradas y la IA sus abiertas.
4. **Escribir una pregunta.** El usuario llena:
   - el tipo;
   - el enunciado;
   - los puntos, en enteros;
   - su criterio.
   
   Y, según el tipo:
   - **Abierta**: «Qué debe tener una buena respuesta» (opcional, hasta 1000 caracteres).
   - **Opción única**: entre 2 y 10 opciones, cada una con sus puntos.
   - **Opción múltiple**: entre 2 y 10 opciones, cada una con sus puntos. Pueden ser negativos
     para castigar marcar lo que no va.
   - **Escala**: entre 3 y 10 niveles. Cada nivel es un número y un rótulo opcional («Nada»,
     «Mucho»), y tiene sus puntos.
   
   Una pregunta con **0 puntos** se hace pero no suma, igual que hoy las de estilo. Si es
   abierta, no se manda a la IA: la respuesta se guarda y se ve en el desglose, sin nota.
5. **Cómo se puntúa cada tipo.** Lo calcula el sistema; la IA nunca pone la nota de una cerrada.

   | Tipo | Nota de la pregunta | Para poder publicar |
   |---|---|---|
   | Abierta | La que ponga la IA, entre 0 y los puntos de la pregunta (hasta dos decimales), con explicación y cita | Nada más |
   | Opción única | Los puntos de la opción elegida | Ninguna opción negativa ni por encima de la pregunta, y al menos una que dé el máximo |
   | Escala | Los puntos del nivel elegido | Igual que opción única |
   | Opción múltiple | La suma de lo marcado, sin bajar de 0 ni pasar de los puntos de la pregunta | Cada opción entre −puntos y +puntos, y la suma de las positivas llega al máximo |
   | Sin responder | 0 | — |

   - **La nota de un criterio** es la suma de lo que sacó en sus preguntas.
   - **La nota del banco** es la suma de los criterios, de 0 a 100. No se reparte entre cerradas
     y abiertas por cantidad, como hace `notaCombinada`.
6. **Recomendaciones por IA.**
   - El usuario pulsa «Recomendaciones por IA». Puede escribir una indicación opcional (por
     ejemplo, «4 criterios, 12 preguntas, énfasis en Excel»).
   - La IA recibe:
     - **los datos de la vacante**: todo lo que se llena al crearla y describe el trabajo.
       - De la vacante: título, descripción, propósito, responsabilidades y requisitos.
       - Del puesto: su nombre, su nivel y su familia.
       - De la solicitud de talento, si la hay: resultado principal y capacidades
         indispensables y aprendibles.
       - **No viaja lo administrativo ni las condiciones**: modalidad, horario, ubicación,
         ciudad, remuneración, responsable, forma de cierre, plazas, fechas y el motivo de la
         solicitud. No dicen qué debe saber hacer la persona, y no deben mover una nota.
       - Un campo nuevo de la vacante que describa el trabajo se suma a este conjunto; uno
         administrativo, no.
     - los criterios y preguntas que ya hay en el borrador, con sus puntos, para no repetirlos.
   - **La IA completa lo que falta, no empieza de cero.** Su propuesta suma exactamente los
     puntos que le faltan al borrador para llegar a 100: 100 si está vacío, 50 si el usuario ya
     hizo la mitad. El diálogo lo dice antes de pedirla («La IA completará los 50 puntos que
     faltan»).
   - Puede poner preguntas en los criterios que ya creó el usuario (por ejemplo, llenar un
     criterio vacío) o proponer criterios nuevos.
   - Con el borrador en 100 o más, el botón no llama a la IA: dice que no queda sitio y que hay
     que quitar puntos o preguntas para que complete.
   - Como hoy el cuestionario técnico, contesta 202 y el panel va mirando con un sondeo acotado.
   - Llega una **propuesta**: criterios con su nombre y qué evalúan, y dentro de cada uno sus
     preguntas con tipo, enunciado, puntos, opciones con puntos y «qué debe tener». Las
     preguntas para un criterio que ya existe aparecen bajo ese criterio, marcadas como nuevas.
   - El usuario agrega lo que quiera: un criterio con sus preguntas, una pregunta suelta a un
     criterio existente, o todo. **Nunca se reemplaza ni se publica nada solo.** Si agrega solo
     una parte, el balance dice cuántos puntos faltan, como siempre.
   - Lo que devuelve la IA pasa una **validación**, igual que el `REDACTOR`:
     - los puntos son enteros y suman exactamente lo que faltaba;
     - cada pregunta está en exactamente un criterio, nuevo o del borrador;
     - no hay criterios nuevos vacíos;
     - los tipos son válidos y las cerradas traen sus opciones.
     
     Si no la pasa, se reintenta una vez con los errores delante; si sigue fallando, se dice que
     falló.
   - Cada petición cuenta contra el tope de IA de la empresa.
7. **Copiar de otra vacante.** Es la biblioteca de la empresa: las vacantes que ya lanzó son
   de donde se reutiliza, sin un catálogo aparte.
   - El usuario elige una vacante **de su misma empresa** que tenga preguntas propias
     publicadas.
     - **Se ofrecen también las cerradas y las archivadas**, porque lo habitual es repetir una
       contratación que ya terminó. Las eliminadas no, porque se crearon por error.
     - Cada vacante de la lista se reconoce por su nombre, el nivel de su puesto, su fecha de
       publicación, su estado (activa, cerrada o archivada) y un resumen («4 criterios · 12
       preguntas»).
     - Hay un buscador por nombre y un filtro por nivel, y las más recientes van primero. El
       nivel solo ayuda a encontrar: copiar de otro nivel está permitido.
   - **Antes de copiar se ve la prueba entera, en solo lectura.** Al tocar una vacante de la
     lista, al lado (debajo en móvil) aparecen sus criterios con sus puntos, las preguntas con
     sus opciones y puntos, los «qué debe tener», los «qué evalúa» y la guía de calificación.
     - No se copia nada hasta pulsar «Copiar esta prueba».
     - Es el mismo modo de solo lectura del editor (versión publicada, o usuario sin
       `editar_vacante`), no una pantalla aparte.
     - La lectura va por el servidor con el mismo corte por empresa que la copia: la vacante de
       otra empresa contesta 404.
   - Se copia la versión publicada: los criterios, las preguntas, las opciones, los «qué debe
     tener», la guía de calificación y los minutos, en un **borrador** de esta vacante. Las
     instrucciones de la IA van como estén en ese momento, con sus correcciones.
   - Es una copia, no un enlace: cambiar después cualquiera de las dos no toca la otra.
   - Si ya había un borrador, antes se pide confirmar que se reemplaza.
8. **Publicar.**
   - El servidor exige:
     - que los puntos sumen **exactamente 100**;
     - que **toda pregunta esté en un criterio**;
     - que **ningún criterio esté vacío**;
     - las reglas por tipo del punto 5;
     - que no haya enunciados ni nombres de criterio vacíos.
   - Si algo falla, contesta **la lista entera de lo que falta**, no solo lo primero. El panel la
     pinta como lista.
   - Publicar congela el contenido.
9. **Después de publicar.**
   - **Mientras nadie haya postulado**: se puede abrir un borrador nuevo desde la publicada.
     Publicarlo reemplaza a la anterior, que se archiva, no se borra.
   - **Desde la primera postulación**: la vara no se mueve. No se publica otra versión, y el
     servidor contesta 409 con un mensaje que dice por qué. Solo se cambian las instrucciones
     de la IA (punto 16) y los puntos (punto 17). Ningún texto: ni enunciados, ni opciones,
     ni el nombre de un criterio (decisión 21).
   - Tampoco se puede cambiar el origen de las preguntas de la vacante. Esa regla ya existe
     (`exigirVaraQuieta`).
10. **La vacante no se publica sin sus preguntas.**
    - Con «Preguntas propias», publicar la vacante exige una versión publicada.
    - El cartel «Todo listo: ya se puede publicar» y el botón miran la misma regla.
11. **El candidato responde.**
    - Al postular se le fija la versión publicada de la vacante. Queda atado a ella (RF-138).
    - Ve una pregunta por pantalla, como hoy:
      - abierta: un cuadro de texto;
      - opción única: botones de radio;
      - opción múltiple: casillas, con al menos una marcada para darla por respondida;
      - escala: los niveles en fila, del menor al mayor, con los rótulos de los extremos.
    - **Nunca le llegan los puntos, la clave ni los criterios** (RF-53).
12. **La calificación.**
    - Al entregar, el sistema puntúa las cerradas.
    - Las abiertas las califica el agente evaluador por el mismo camino que hoy: cuando se pide,
      o sola si la vacante califica sola.
    - La IA recibe:
      - **los datos de la vacante**, el mismo conjunto que las recomendaciones (punto 6): todo
        lo que se llenó al crearla y describe el trabajo. Así la IA juzga la respuesta contra
        lo que la vacante pide de verdad;
      - la guía de la versión, en el `system`, con la envoltura y la marca sorteada;
      - cada abierta con su enunciado, su «qué debe tener», su máximo y el criterio al que
        pertenece, con su «qué evalúa»;
      - la respuesta.
    - **Los datos de la vacante son contexto, no la vara.** Se leen como estén al calificar.
      Editar la vacante (la descripción o los requisitos se pueden cambiar con postulantes
      dentro) no recalifica a nadie. Si el cambio altera lo que se exige, lo que se corrige es
      la guía o el «qué debe tener», y eso sí recalifica a todos (punto 16).
    - Devuelve, por respuesta: nota, explicación y la cita literal en que se basa.
    - **El backend es la red de seguridad:**
      - descarta respuestas que no son de esta evaluación;
      - descarta notas sin explicación;
      - acota cada nota entre 0 y el máximo de su pregunta;
      - nunca guarda una nota global que devuelva el modelo.
13. **La nota del Perfil Integral.**
    - La del banco solo existe cuando **todas sus preguntas con puntos tienen nota**.
    - Mientras falte la de alguna abierta, **no se escribe la nota del Perfil Integral**: ni con
      el currículum solo, ni con un cero provisional.
    - Con la del banco completa, el Perfil Integral se calcula como hoy: currículum y evaluación
      con los pesos de la versión de pesos de la vacante.
14. **El desglose en la ficha.** En la pestaña Perfil Integral del candidato:
    - cada criterio con su nota sobre su máximo y de dónde sale («Conocimiento contable 24/30 ·
      Sistema 8/10 + IA 16/20»);
    - al abrirlo, sus preguntas con el tipo, la respuesta, los puntos obtenidos sobre el máximo
      y, en las abiertas, la explicación y la cita de la IA;
    - arriba, el total.
    
    Un criterio con abiertas sin calificar se ve como pendiente, no con la nota parcial.
15. **Ajustar a mano la nota de una abierta.**
    - En el desglose, cada abierta tiene «Ajustar nota», y la que está pendiente, «Calificar a
      mano». Solo aparecen para quien tiene `ajustar_nota` (hoy Talento y Dirección); el dato
      viaja en el DTO del desglose, porque el panel no sabe sus permisos.
    - Se abre en su sitio con dos campos: la nota nueva, entre 0 y los puntos de la pregunta, y
      el motivo, obligatorio (hasta 1000 caracteres).
    - Al guardar:
      - la nota ajustada es la que vale;
      - la nota y la explicación de la IA se quedan y se ven al lado;
      - se recalculan el criterio, la nota del banco, la del Perfil Integral y el grupo de
        prioridad, **sin volver a llamar a la IA**;
      - si la IA vuelve a calificar, no la pisa. Eso ya pasa hoy.
    - **Sirve también cuando la IA no pudo.** Si era la última abierta que faltaba, se escribe la
      nota del Perfil Integral.
    - Se puede volver a ajustar. Queda el último ajuste, como en la prueba del puesto.
    - Las reglas de acceso son las del ajuste de la prueba del puesto (`laQueSePuedeTocar` con
      `ajustar_nota`).
    - **Ajustar no mueve al candidato de etapa.** Moverlo sigue siendo decisión de una persona
      desde el panel.
    - El candidato nunca ve ni la nota ni el ajuste (RF-53).
16. **Corregir las instrucciones de la IA después de publicar.**
    - Con la versión publicada, y aunque ya haya postulaciones, se pueden cambiar:
      - la guía de calificación;
      - el «qué debe tener» de cada abierta;
      - el «qué evalúa» de cada criterio.
    - Lo demás sigue congelado, salvo los puntos (punto 17): preguntas, tipos, textos, opciones
      y a qué criterio pertenece
      cada pregunta.
    - Si hay candidatos con abiertas calificadas o calificándose, antes de guardar se pide
      confirmar: «Se volverá a calificar a las 23 personas que ya tienen nota. Las notas
      ajustadas a mano no cambian. Cuenta contra el tope de IA».
    - Al confirmar:
      - se guarda el cambio y se encola el evaluador para cada una de esas postulaciones;
      - cada una conserva su nota anterior hasta que llega la nueva, y la ficha y el ranking
        dicen «Recalificando con la guía nueva»;
      - **las notas nuevas de una persona se aplican juntas**, cuando han llegado todas sus
        abiertas (también si se repartieron en varias llamadas). Nunca queda una persona con
        unas abiertas de la guía vieja y otras de la nueva;
      - al aplicarse, se recalculan el criterio, la nota del banco, la del Perfil Integral y el
        grupo de prioridad, **como en el ajuste a mano: sin mover a nadie de etapa**;
      - las abiertas ajustadas a mano no se tocan;
      - el resumen, los hallazgos y el potencial del perfil no se rehacen.
    - Quien responda después ya se califica con la guía nueva.
    - **Cada guía tiene un número de versión**, y cada nota de una abierta guarda con cuál se
      calculó. Un resultado que llega calculado con una guía anterior se descarta y esa persona
      se vuelve a encolar. Así, quien se estaba calificando justo al cambiar la guía no se queda
      con la vieja (la cola no admite un segundo trabajo mientras hay uno vivo).
    - **Sin saldo o con la IA apagada no se guarda nada.** Si al guardar la IA está apagada, o
      la empresa llegó a su tope del mes, se abre una ventana que lo dice («La IA no tiene
      saldo: no se puede recalificar ahora») y el cambio no se guarda. Guardarlo sin
      recalificar dejaría candidatos medidos con dos guías. No se estima de antemano si el
      saldo alcanza para todos.
    - **Si el saldo se acaba a mitad**, o la IA falla con alguien:
      - cada persona sin terminar conserva su nota anterior, marcada «Pendiente de
        recalificar»;
      - el editor y la ficha dicen por qué, con el mismo texto que ya da el backend cuando el
        proveedor contesta 402 («la cuenta del proveedor no tiene saldo») o cuando la empresa
        llegó a su tope;
      - un botón «Reintentar» vuelve a encolar solo a esas personas.
    - Mientras hay una recalificación en curso, la guía no se puede volver a cambiar. Una
      recalificación detenida por saldo o por fallo no cuenta como en curso: se puede
      reintentar o cambiar la guía otra vez, y en ese caso se recalifica a todos con la última.
    - Cada cambio queda registrado con quién lo hizo, cuándo y el texto anterior, para poder
      reconstruir con qué guía se calificó a cada uno.
    - Requiere `editar_vacante`.
17. **Cambiar los puntos después de publicar.**
    - Con la versión publicada, y aunque ya haya postulaciones, se pueden cambiar los puntos de
      cada pregunta y los de cada opción o nivel. El total tiene que seguir en 100 y valen las
      reglas por tipo del punto 5; si no, 400 con la lista entera.
    - Si hay candidatos con nota, antes de guardar se pide confirmar: «Se recalcularán las
      notas de las 23 personas que ya tienen nota. No se llama a la IA».
    - Al confirmar, en la misma transacción:
      - las cerradas de todos se vuelven a puntuar con los puntos nuevos;
      - cada abierta ya calificada se escala en proporción a su máximo nuevo: la nota que vale
        y la que puso la IA. Un ajuste a mano se escala igual y conserva su motivo;
      - se recalculan los criterios, la nota del banco, la del Perfil Integral y el grupo de
        prioridad, **sin mover a nadie de etapa**.
    - Las abiertas aún sin calificar reciben después su nota sobre el máximo nuevo.
    - No se pueden cambiar mientras hay una recalificación de la IA en curso.
    - Cada cambio queda registrado con quién, cuándo y los puntos anteriores.
    - Requiere `editar_vacante`.
18. **Si algo falla**, se dice qué pasó y no se miente sobre lo guardado:
    - la IA está apagada o sin cupo: la petición no se encola y se dice;
    - la IA no pasa la validación: la generación queda como fallida;
    - error de red: nada se da por guardado hasta que el servidor lo confirma.

### Fase 2

0. **La sección «Prueba técnica» de una vacante nueva** ya no tiene nada que elegir.
   - Desaparecen «Qué rendirá en la etapa técnica» y «Qué prueba del puesto rendirá»: cada
     vacante escribe la suya.
   - Desaparece «Cuánto tiempo tendrá» de la vacante: el tiempo se pone en el bloque de tiempo
     del editor. Puede seguir guardándose en `vacante.minutos_etapa_tecnica`; lo decide quien
     implemente. Se puede cambiar hasta la primera rendición, como hoy
     (`exigirVaraQuietaDelInstrumento`), aunque la prueba esté publicada.
   - La tarjeta «Preparar la prueba técnica» se sustituye por el estado de la prueba («Sin
     prueba», «Borrador · 70 de 100 puntos · 2 entregables», «Publicada · 5 criterios · 2
     entregables · 90 min») y el botón «Armar la prueba», que abre el editor.
   - «Pesos de la decisión» se queda como está.
   - Publicar la vacante exige la prueba publicada, como hoy exige el instrumento de la etapa
     técnica.
   - Las vacantes existentes siguen viendo los desplegables y la tarjeta de hoy.
1. En la etapa técnica de una vacante nueva, el usuario abre el mismo editor, en
   `/admin/vacantes/:id/prueba`. Tiene todo lo de la fase 1, con tres diferencias:
   - la sección «Entregables»;
   - el bloque de tiempo: modalidad, minutos y cambio inesperado;
   - **en cada criterio, «Parte calificada»**: sus puntos y quién la califica (IA o persona).
     Las abiertas no llevan puntos.
2. El balance suma, por criterio, la parte automática (sus cerradas) más la parte calificada,
   hasta 100.
3. Sin entregables, la pantalla y el candidato lo presentan como cuestionario. Con entregables,
   como prueba.
4. El candidato rinde como hoy la prueba del puesto: reloj, entregas hasta tres veces y cierre
   por plazo. Las preguntas vienen en los cuatro tipos.
5. Calificación:
   - La parte automática, el sistema, al entregar.
   - La parte calificada de los criterios de IA, la IA. Lee todas las respuestas abiertas y
     entregas legibles, y pone una nota por criterio **entre 0 y los puntos de su parte
     calificada, nunca entre 0 y el total del criterio**. Aplica la misma red de seguridad.
   - La de los criterios de persona, y todo lo que no da texto (video, imagen, enlace), una
     persona. Lo ve con `GET /panel/postulaciones/{id}/prueba/entregables`.
   - **Un criterio mixto con solo su parte automática cuenta como que falta.** La nota de la
     etapa solo existe cuando todos los criterios están enteros.
   - Una persona con `ajustar_nota` puede ajustar la parte calificada de un criterio, entre 0 y
     sus puntos, con motivo. La parte automática no se ajusta.
6. Rankings y Excel:
   - «Prueba del puesto»: una columna por criterio, como hoy («24/30», con su tono). Un criterio
     mixto pendiente no enseña la parte automática como si fuera su nota.
   - «Perfil Integral»: las columnas de los criterios del banco de la vacante, junto a las del
     currículum.
7. «Recomendaciones por IA» propone también los entregables y la rúbrica: qué criterios, qué
   cerradas van en cada uno, los puntos de cada parte calificada y quién la califica. Pasa la
   misma validación de la fase 1, más que el calificador sea coherente con el contenido: un
   criterio que solo mira un video no puede ser de IA.

## Referencia visual

La fase 1 crea una pantalla nueva (el editor) y cambia tres: la sección de la vacante, la
evaluación del portal y el desglose en la ficha. La fase 2 cambia la sección «Prueba técnica»
de la vacante y el ranking.

### La vacante nueva de una empresa cliente, con las dos fases

```
Qué responderá quien postule
  (•) Preguntas propias de esta vacante    ( ) Sin evaluación
  Borrador · 68 de 100 puntos                    [ Escribir las preguntas → ]

Prueba técnica                                                     (fase 2)
  Borrador · 70 de 100 puntos · 2 entregables    [ Armar la prueba → ]

Pesos de la decisión
  [ CAZATALENTOS · MICRO                ▾ ]
```

- En RENASER, «Qué responderá quien postule» suma la opción de su banco por nivel, marcada de
  entrada.
- Durante la fase 1, «Prueba técnica» sigue como hoy.

### El editor de preguntas, agrupado por criterios (escritorio)

```
← La vacante                                     Asistente Contable · Preguntas
┌──────────────────────────────────────────────────────────────────────────────┐
│ BORRADOR        85 de 100 puntos  ·  3 criterios  ·  9 preguntas  ·  2 avisos │
│ ▸ Faltan 15 puntos   ▸ «Manejo de Excel» no tiene preguntas                  │
│                                                   [ Publicar las preguntas ] │
└──────────────────────────────────────────────────────────────────────────────┘
[ + Agregar criterio ]   [ Recomendaciones por IA ]   [ Copiar de otra vacante ]

Guía de calificación para la IA  (opcional · 0 de 2000)           Minutos estimados [ 25 ]
┌──────────────────────────────────────────────────────────────────────────────┐
│ Qué distingue una buena respuesta en este puesto…                            │
└──────────────────────────────────────────────────────────────────────────────┘

┏ Conocimiento contable ───────────── 30 pts · sistema 10 + IA 20 ── ↑ ↓ ✎ ✕ ┓
┃ Qué evalúa: domina el registro y el cierre mensual.                          ┃
┃ ┌ Opción única · 4 pts ─────────────────────────────────────── ↑ ↓ ✎ ✕ ┐   ┃
┃ │ ¿Qué libro registra primero una venta al crédito?                     │   ┃
┃ │  ○ Libro diario ..... 4   ○ Libro mayor ..... 0   ○ Caja ..... 0      │   ┃
┃ └────────────────────────────────────────────────────────────────────────┘   ┃
┃ ┌ Abierta · 20 pts ────────────────────────────────────────── ↑ ↓ ✎ ✕ ┐   ┃
┃ │ Cuéntanos un cierre con un descuadre. ¿Cómo lo hallaste?              │   ┃
┃ │ Qué debe tener: el monto, la cuenta y en cuántos días lo cerró.        │   ┃
┃ └────────────────────────────────────────────────────────────────────────┘   ┃
┃ [ + Agregar pregunta ]                                                       ┃
┗━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━┛
┏ Manejo de Excel ──────────────────────────────── 0 pts ── ↑ ↓ ✎ ✕ ┓
┃ ⚠ Sin preguntas: así no se publica.   [ + Agregar pregunta ]        ┃
┗━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━┛
```

- El criterio es una superficie blanca y sus preguntas van hundidas dentro, en `nube-hundida`.
- Al editar, la tarjeta se abre en su sitio con los campos del tipo. Se reutiliza la `Tarjeta`
  editable de `CuestionarioTecnico.tsx`.
- Cada pregunta tiene un selector «Criterio» para moverla a otro.
- Los botones mover, editar y quitar son de 44 px.
- **Fase 2**: la cabecera de cada criterio suma una línea «Parte calificada: [ 20 ] pts ·
  [IA ▾]», y aparece un bloque «Entregables» y otro «Tiempo».

### El ranking (fase 2)

```
Candidato      │ Conocimiento contable │ Manejo de Excel │ Priorización │ Video │ Nota
───────────────┼───────────────────────┼─────────────────┼──────────────┼───────┼──────
Ana Rojas      │ 24/30                 │ 10/10           │ 22/30        │ 25/30 │ 81
Luis Paredes   │ pendiente             │ 6/10            │ —            │  —    │  —
```

- Todas las columnas usan el formato y las reglas de tono de hoy (`ranking.ts`).
- Un criterio de persona queda en blanco hasta que alguien le pone nota.
- Un criterio mixto dice «pendiente» mientras falte su parte calificada.
- La columna la identifica el **id** del criterio, nunca su código.

### Estados a dibujar

- **Vacío**: «Todavía no hay preguntas», con los tres botones de entrada (agregar criterio,
  recomendar, copiar).
- **Cargando**: esqueleto de dos criterios con dos preguntas cada uno.
- **La IA trabajando**: una línea de estado con el sondeo («La IA está proponiendo criterios y
  preguntas… 2 de 6»), igual que en el cuestionario técnico.
- **Propuesta de la IA**: un panel con cada criterio propuesto y sus preguntas, con «Agregar» en
  cada criterio y en cada pregunta, y «Agregar todo».
- **Copiar de otra vacante**: la lista con buscador a la izquierda y la vista previa en solo
  lectura a la derecha, con «Copiar esta prueba» como única acción. Sin nada elegido, la
  vista previa dice «Elige una vacante para ver sus preguntas». En móvil, la vista previa
  se abre debajo de la vacante tocada.

  ```
  Copiar de otra vacante                         [ Buscar…          ]
  ┌───────────────────────────────┬─────────────────────────────────────┐
  │ Asistente contable · 03/2026  │ Conocimiento contable · 30 pts      │
  │ archivada · 4 crit · 12 preg  │   Opción única · 4 pts              │
  │ ▸ Analista de datos · 08/2026 │   ¿Qué libro registra primero…?     │
  │ activa · 3 crit · 9 preg      │   ○ Libro diario ..... 4            │
  │                               │   Abierta · 20 pts                  │
  │                               │   Cuéntanos un cierre con un…       │
  │                               │            [ Copiar esta prueba ]   │
  └───────────────────────────────┴─────────────────────────────────────┘
  ```
- **Criterio sin preguntas** y **pregunta sin criterio**: aviso ámbar en el propio bloque y en
  el balance. Frenan la publicación.
- **Con error**: el 400 de publicar, como lista de todo lo que falta. El 403, con el permiso que
  falta.
- **Publicada**: todo en solo lectura salvo los puntos y las instrucciones de la IA, con el
  cartel «Publicadas. Desde la primera postulación solo se cambian los puntos y las
  instrucciones de la IA, y cualquiera de los dos vuelve a calcular la nota de todos».
- **Recalificando** (editor, ficha y ranking): la nota anterior, con la marca «Recalificando con
  la guía nueva» y cuántos faltan («18 de 23»).
- **Datos largos**: un enunciado de 1000 caracteres, diez opciones largas y un nombre de
  criterio de 80 caracteres. El bloque crece y nada se corta.
- **Abierta ajustada** (ficha): la nota ajustada en primer plano; debajo, en tono secundario, la
  de la IA con su explicación, y quién la ajustó, cuándo y por qué:

  ```
  ┌ Abierta · 16/20 ─────────────────────────────────────── [ Ajustar nota ] ┐
  │ Cuéntanos un cierre con un descuadre. ¿Cómo lo hallaste?                  │
  │ «En marzo el mayor no cuadraba por 1.200 soles…»                          │
  │ Ajustada por Ana Pérez el 30/09: «Sí dio el dato; la IA no leyó el anexo.»│
  │ IA: 12/20 · Explica el caso pero no dice en cuántos días lo cerró.        │
  └───────────────────────────────────────────────────────────────────────────┘
  ```
- **Abierta pendiente** (ficha): «Pendiente de la IA» y, para quien puede, «Calificar a mano».

### Móvil y escritorio

- **Móvil**:
  - el balance queda pegado arriba, en una línea («85/100 · 2 avisos»), y se abre al tocarlo;
  - los botones de entrada se apilan;
  - los puntos del criterio bajan debajo del nombre;
  - las opciones van una por línea.
- **Portal, escala**: en móvil los niveles pasan a una columna. Con más de 5 niveles, también en
  escritorio.

### Reglas visuales

Aplica el `DESIGN.md` del panel:

- una sola acción principal por pantalla (Publicar);
- el índigo solo en lo que se pulsa;
- el ámbar solo para avisos que frenan la publicación, nunca para marcar un tipo o un criterio;
- controles de 44 px;
- el tipo y los puntos de una pregunta son chips neutros, sin color.

Queda a criterio de quien implemente el tamaño exacto de los bloques y el orden de los campos
dentro de cada tipo.

## Criterios de aceptación

### Fase 1

- **AC-01**: Dada una vacante nueva de una empresa sin banco propio, que hoy rendiría el de
  RENASER, cuando se abre «Qué responderá quien postule», entonces ve «Sin evaluación» y
  «Preguntas propias» (marcada), y no ve ninguna opción de banco por nivel. El servidor rechaza
  crearla o guardarla con el origen `NIVEL`.
- **AC-01b**: Dada una vacante nueva de una empresa con banco propio publicado para ese
  nivel (RENASER), cuando se abre la sección, entonces viene marcada «El banco de la empresa
  para su nivel» y se ofrecen también las otras dos.
- **AC-01c**: Dada una vacante existente de otra empresa que rinde el banco prestado, cuando se
  despliega esta versión, entonces sigue rindiéndolo, la sección lo llama «El banco de RENASER
  para su nivel» y, si se cambia de opción antes de la primera postulación, ya no se puede
  volver a elegir.
- **AC-01d**: Dada una empresa sin banco propio, cuando ella o la plataforma intentan
  personalizar el banco, entonces el servidor lo rechaza y no se copia nada. Personalizar los
  pesos, las plantillas de evaluación o las pruebas sigue funcionando.
- **AC-02**: Dada una vacante existente con el banco del nivel, cuando se despliega esta
  versión, entonces sigue rindiendo el banco del nivel sin cambios.
- **AC-03**: Dado el editor vacío, cuando el usuario agrega una pregunta sin haber creado
  criterios, entonces aparece el criterio «General» con esa pregunta dentro.
- **AC-04**: Dados dos criterios con preguntas de los cuatro tipos, cuando el usuario cambia
  puntos, entonces el total de cada criterio y el balance cuadran con los del servidor tras
  refrescar.
- **AC-05**: Dada una versión que suma 95, que tiene un criterio vacío y una opción única cuya
  mejor opción no da el máximo, cuando se pide publicar, entonces el servidor contesta 400 con
  **las tres** faltas. El panel las lista y no queda nada publicado.
- **AC-06**: Dada una opción múltiple de 10 puntos con opciones +6, +6 y −4, cuando el candidato
  marca las tres, entonces obtiene 8. Si marca +6 y +6, obtiene 10 (tope). Si marca solo −4,
  obtiene 0 (piso).
- **AC-07**: Dada una pregunta de 0 puntos, cuando el candidato la responde, entonces su
  respuesta se guarda y se ve en el desglose, pero la nota no cambia. Si es abierta, el
  insumo del evaluador no la lleva, no cuenta para «todas sus preguntas con puntos tienen
  nota» y una recalificación no la toca.
- **AC-08**: Dada una versión publicada, cuando el candidato abre la evaluación, entonces ningún
  campo de la respuesta de la API contiene puntos, la clave de las opciones ni los criterios.
- **AC-09**: Dada una evaluación entregada con cerradas y abiertas, cuando la IA aún no califica
  las abiertas, entonces no existe nota del Perfil Integral para esa postulación: no se escribe
  ni con el currículum solo ni como cero.
- **AC-10**: Dada la IA devolviendo 25 para una abierta de 15 puntos, cuando se guarda,
  entonces queda 15. Una nota sin explicación, o para una respuesta de otra evaluación, se
  descarta.
- **AC-11**: Dadas todas las notas completas, cuando se recalcula el Perfil Integral, entonces
  la nota de evaluación es la suma de los criterios, sin ponderar cerradas y abiertas por
  cantidad, y se mezcla con el currículum con los pesos de la vacante.
- **AC-12**: Dada una guía de calificación que intenta cerrar su bloque y dar órdenes, cuando se
  califica, entonces viaja en el `system` con la envoltura y la marca sorteada, y no viaja cruda
  en el mensaje de datos.
- **AC-12b**: Dada una vacante con todos sus campos llenos y una solicitud de talento, cuando
  el evaluador califica sus abiertas, entonces su insumo lleva todos los datos de la vacante
  del punto 6 y ninguno de los excluidos (modalidad, horario, ubicación, ciudad,
  remuneración, responsable, cierre, plazas, fechas, motivo). Si después se edita la descripción, no se encola ninguna recalificación.
- **AC-13**: Dado el botón «Recomendaciones por IA», cuando la IA termina, entonces aparece una
  propuesta de criterios con sus preguntas, y el borrador no cambia hasta que el usuario agrega
  algo.
- **AC-14**: Dada una propuesta de la IA que no suma lo que faltaba, o con una pregunta sin
  criterio, dos veces seguidas, cuando termina, entonces la generación queda fallida y se dice.
  No se agrega nada a medias.
- **AC-14b**: Dado un borrador con 50 puntos y un criterio «Manejo de Excel» sin preguntas,
  cuando se piden recomendaciones, entonces la propuesta suma 50, puede traer preguntas para
  «Manejo de Excel» y no repite ninguna pregunta del borrador. Al agregarla entera, el balance
  queda en 100. Con el borrador en 100, el botón avisa que no queda sitio y no se encola nada.
- **AC-15**: Dada una vacante de la empresa A, cuando un usuario de la empresa B intenta
  copiarla, entonces el servidor contesta 404 y no se crea nada.
- **AC-15b**: Dadas tres vacantes de la empresa con preguntas propias publicadas, una activa,
  una archivada y una eliminada, cuando se abre «Copiar de otra vacante», entonces se ofrecen
  la activa y la archivada, cada una con su nombre, fecha, estado y resumen, y no la
  eliminada. Al copiar la archivada y cambiar una pregunta de la copia, la archivada no
  cambia.
- **AC-15c**: Dado un borrador con preguntas, cuando el usuario abre «Copiar de otra vacante» y
  toca una vacante, entonces ve sus criterios, preguntas, opciones con puntos y guía en solo
  lectura, y el borrador no cambia. Solo al pulsar «Copiar esta prueba» (y confirmar el
  reemplazo) pasa a ser la copia. Pedir la vista previa de una vacante de otra empresa
  contesta 404.
- **AC-15d**: Dada una vacante de nivel Ejecución y otra de Dirección con preguntas propias,
  cuando se filtra por Ejecución en «Copiar de otra vacante», entonces solo aparece la
  primera; sin filtro se puede copiar la de Dirección en una vacante de Ejecución.
- **AC-16**: Dada una versión publicada y una postulación, cuando se intenta publicar otra
  versión o cambiar el origen de las preguntas, entonces el servidor contesta 409 con el motivo.
  Cambiar el texto de un enunciado, de una opción o el nombre de un criterio también da 409.
  Los puntos (AC-32) y las instrucciones de la IA (AC-28) sí se pueden cambiar.
- **AC-17**: Dada una vacante con «Preguntas propias» sin versión publicada, cuando se intenta
  publicar la vacante, entonces el servidor lo rechaza y el panel no muestra «Todo listo».
- **AC-18**: Dada una vacante con cuestionario técnico publicado y preguntas propias publicadas,
  cuando alguien entra en la etapa técnica, entonces rinde el cuestionario técnico y no las
  preguntas del Perfil Integral (el filtro por propósito).
- **AC-19**: Dado el desglose de una candidatura calificada, cuando se abre la ficha, entonces
  cada criterio enseña su nota, de dónde sale («Sistema 8/10 + IA 16/20») y sus preguntas, y la
  suma de los criterios cuadra con la nota.
- **AC-20**: Dado un usuario sin `editar_vacante`, cuando abre el editor, entonces lo ve en solo
  lectura, sin botones que acaben en 403.
- **AC-21**: Dado el banco `CRITERIOS` de RENASER, cuando corre la suite completa, entonces sus
  pruebas de integración siguen pasando sin tocarlas.
- **AC-22**: Dada una escala de 10 niveles, cuando el candidato la abre, entonces ve los niveles
  del 1 al 10 en ese orden, no 1, 10, 2…
- **AC-23**: Dada una abierta de un banco `CRITERIOS` o del v3, cuando llega una nota por encima
  de 4, entonces se rechaza como hoy la rechaza la base (el guardado falla). No se acota ni se
  guarda.
- **AC-24**: Dada una postulación con notas del currículum y criterios del banco calificados,
  cuando se calcula la nota del currículum, entonces es la misma que sin criterios del banco:
  los criterios del banco no se mezclan con los del currículum.
- **AC-25**: Dada una abierta de 20 puntos con 12 de la IA, cuando alguien con `ajustar_nota` la
  ajusta a 16 con motivo, entonces su criterio y la nota del banco suben 4, la del Perfil
  Integral se recalcula, y la ficha enseña las dos notas con quién, cuándo y por qué. Si la IA
  vuelve a calificar, la abierta sigue en 16.
- **AC-26**: Dado un ajuste sin motivo, con 21 o con −1, entonces el servidor contesta 400. Sin
  `ajustar_nota`, contesta 403 y el botón no aparece. Una cerrada no se puede ajustar. La API del
  candidato no enseña ni la nota ni el ajuste.
- **AC-27**: Dada una postulación con una abierta que la IA no pudo calificar, cuando una persona
  la califica a mano, entonces se escriben la nota del Perfil Integral y el grupo de prioridad, y
  la postulación sigue en la etapa en que estaba.
- **AC-28**: Dada una versión publicada con 3 candidatos calificados, uno con una abierta
  ajustada a mano, cuando se cambia la guía y se confirma, entonces se encola el evaluador para
  los 3. Cada uno conserva su nota hasta que llega la nueva, la abierta ajustada no cambia, se
  recalculan las notas y el grupo, y nadie cambia de etapa.
- **AC-29**: Dada una empresa en su tope de IA, o con la IA apagada, cuando se cambia la guía,
  entonces se abre la ventana de «sin saldo» y no se guarda nada. Dada una recalificación de 3
  en la que la tercera falla con 402, entonces las dos primeras quedan con su nota nueva, la
  tercera conserva la anterior marcada «Pendiente de recalificar» con el motivo, y
  «Reintentar» solo la encola a ella. Un resultado calculado con una guía anterior se
  descarta. Cambiar textos, opciones o el criterio de una pregunta con candidatos dentro sigue
  dando 409.
- **AC-30**: Dado un cambio de guía, entonces queda registrado con quién, cuándo y el texto
  anterior.
- **AC-31**: Dada una versión con 80 preguntas y 30 abiertas, cuando el candidato la responde y
  la IA la califica, entonces todas las abiertas reciben nota, aunque haya que repartirlas en
  varias llamadas.
- **AC-32**: Dada una versión publicada con 3 candidatos calificados, una opción única cuya
  clave estaba al revés (A daba 4 y B daba 0) y una abierta de 20 con un 12 de la IA, cuando se
  cambia A a 0, B a 4, la abierta a 15 y otra pregunta sube 5 para seguir en 100, entonces
  quien marcó B gana 4 y quien marcó A pierde 4, el 12/20 pasa a 9/15, se recalculan las notas
  y el grupo, nadie cambia de etapa y no se encola ningún trabajo de IA. Si el total no queda
  en 100, contesta 400 y no cambia nada.
- **AC-33**: Dado un cambio de puntos, entonces queda registrado con quién, cuándo y los puntos
  anteriores. Con una recalificación de la IA en curso, cambiar los puntos contesta 409.

### Fase 2 (se detallan al abrir su trabajo)

- **AC-F2-01**: Las partes automáticas y calificadas de todos los criterios suman 100 para
  publicar.
- **AC-F2-02**: En un criterio mixto de 30 (10 de cerradas + 20 calificados), la IA recibe un
  máximo de 20. Si devuelve 28, se guarda 20.
- **AC-F2-03**: Un criterio mixto con solo su parte automática cuenta como que falta. La nota de
  la etapa no se escribe y su celda dice «pendiente».
- **AC-F2-04**: Un criterio de persona, o uno que solo mira un video, nunca va a la IA.
- **AC-F2-05**: Sin entregables, el candidato ve un cuestionario.
- **AC-F2-06**: Las vacantes con `PLANTILLA` o `CUESTIONARIO_TECNICO` siguen rindiendo lo suyo.
- **AC-F2-07**: Dos vacantes con un criterio del mismo nombre o código no juntan sus columnas en
  el ranking ni en el Excel.
- **AC-F2-08**: Una vacante nueva de RENASER no ofrece plantillas ni el cuestionario técnico:
  solo el editor.
- **AC-F2-09**: En una vacante nueva, la sección «Prueba técnica» no enseña «Qué rendirá en la
  etapa técnica», «Qué prueba del puesto rendirá» ni «Cuánto tiempo tendrá»: enseña el estado
  de la prueba y «Armar la prueba». «Pesos de la decisión» sigue. Una vacante existente sigue
  viendo lo de hoy.
- **AC-F2-10**: Cambiar los minutos en el editor de una prueba publicada funciona hasta la
  primera rendición y después contesta 409, como hoy en la vacante.

## Casos límite y errores

- **Datos vacíos o inválidos**:
  - enunciado o nombre de criterio vacío;
  - una opción sin texto;
  - una cerrada con una sola opción;
  - puntos negativos en una pregunta;
  - puntos con decimales en una pregunta, una opción o un nivel;
  - una escala con menos de 3 niveles;
  - una guía de más de 2000 caracteres;
  - un «qué debe tener» o un «qué evalúa» de más de 1000.
  
  Todos se rechazan con un 400 claro, y el tope se valida en el DTO y en la base.
- **Quitar un criterio con preguntas**: se pide confirmar, y sus preguntas quedan «sin criterio»
  hasta que se muevan. Mientras tanto la versión no se publica.
- **Usuario sin permisos**:
  - editar, publicar, copiar y pedir recomendaciones requieren `editar_vacante`;
  - ajustar o calificar a mano una abierta requiere `ajustar_nota`;
  - cambiar las instrucciones de la IA de una versión publicada requiere `editar_vacante`;
  - ver requiere `ver_vacantes`;
  - el panel no sabe sus permisos (no hay endpoint): el dato de si puede editar viaja en el DTO
    del editor.
- **Recarga, reintento o doble envío**:
  - pedir recomendaciones con una generación en curso no encola otra;
  - dos ajustes seguidos de la misma abierta dejan el último;
  - publicar dos veces no crea dos versiones: lo impiden los índices parciales;
  - el editor refresca la versión entera tras cada cambio y no se parchea a mano, igual que
    `ComponerPrueba.tsx`.
- **Una vacante copiada que después se archiva o elimina**: la copia ya es independiente.
- **La IA sin cupo**: se dice que no se encoló, sin hablar de error.
- **Abierta respondida en blanco**: vale 0. No se manda a la IA.
- **Sin tope de preguntas**: el editor, el portal y el desglose aguantan 100 preguntas sin
  cortar nada ni volverse inusables.

## Datos y compatibilidad

### Qué hay que guardar

Esto describe el contenido de la migración, **sin fijarle número**: V62 es la última en `main`,
y el número se toma al implementar. Ver las memorias de colisiones de migración.

- **`version_banco`**:
  - Un propósito para los bancos de vacante (`PERFIL_INTEGRAL` o `CUESTIONARIO_TECNICO`). Las
    filas actuales con `vacante_id` pasan a `CUESTIONARIO_TECNICO`.
  - Los índices parciales de la V42 incluyen el propósito, para que una vacante pueda tener un
    borrador y una publicada **de cada uno**.
  - El valor `PUNTOS` en `metodo_calificacion`.
  - `guia_calificacion`, texto de hasta 2000 caracteres con CHECK, como en la V46.
- **Los criterios del banco: una tabla nueva, colgada de `version_banco`.**
  - Guarda nombre, «qué evalúa» (hasta 1000 caracteres) y orden. Sus puntos no se guardan: se
    derivan de sus preguntas.
  - Se identifican **por id**. Ninguna lectura agrupa por nombre ni por código.
  - **No se reutiliza la tabla `criterio`.** Ya mezcla los ocho del currículum con los de la
    rúbrica de la prueba, y los del banco también serían de la etapa `PERFIL_INTEGRAL`: cualquier
    lectura «criterios de la etapa» los juntaría con los del currículum (AC-24).
  - En la fase 1 no hay nota por criterio guardada: se calcula con las notas de sus preguntas.
- **`pregunta`**:
  - Los tipos `OPCION_UNICA`, `OPCION_MULTIPLE` y `ESCALA` en `pregunta_tipo_check`. `ABIERTA`
    ya existe.
  - Una columna nueva de **puntos**, entera de 0 a 100. **No se reutiliza `peso`**, que es
    `Short` con CHECK 0..2 y sigue sirviendo al v3.
  - Su criterio. Es obligatorio para publicar, pero puede faltar en un borrador.
  - «Qué debe tener una buena respuesta», texto de hasta 1000 caracteres.
  - **En el método `PUNTOS` decide la columna de puntos**, no `es_puntuable`. El servidor
    guarda `es_puntuable = puntos > 0` para no dejar la columna incoherente, y nada del método
    nuevo la lee.
- **`opcion`**:
  - Sus puntos, en `puntaje`. En opción múltiple pueden ser negativos. En el método
    `PUNTOS` son enteros; como la columna es `numeric(5,2)` y la comparte el v3, se exige en el
    DTO y al publicar, no con un CHECK.
  - **Un orden explícito, en una columna numérica nueva.** Hoy `opcionesDe` ordena por `letra`
    como texto, y con diez niveles sale 1, 10, 2… Los tipos nuevos se leen siempre por ese
    orden; los del v3 siguen como están.
  - En la escala, el orden es el número del nivel y el rótulo va en `texto`; sin rótulo, el
    número.
  - La `letra` de las opciones de los tipos nuevos la pone el servidor. Quien escribe no la
    ve.
- **`vacante`**: el origen de sus preguntas, `NIVEL` o `VACANTE`.
  - Las existentes pasan a `NIVEL`.
  - Las nuevas nacen con `NIVEL` si su empresa tiene un banco **propio** publicado para ese
    nivel, y con `VACANTE` si no. Esa comprobación no pasa por `DuenoDelInstrumento`: mira
    solo las filas de la propia empresa.
  - Una vacante nueva solo acepta `NIVEL` en esas mismas condiciones. Una existente que ya lo
    tenía con el banco prestado lo conserva.
  - `aplica_evaluacion` sigue siendo el interruptor.
- **`evaluacion`**:
  - La exigencia de plantilla de evaluación del CHECK de la V43 deja de aplicar a las
    evaluaciones que salen de un banco de vacante.
  - Cómo se exprese (relajar el CHECK y exigirlo en código, u otra forma) lo decide quien
    implemente.
  - Estas evaluaciones no llevan `vigente_hasta`.
- **`nota_respuesta.puntaje`**: su CHECK 0..4 pasa a 0..100.
  - Para los métodos `NULL` y `CRITERIOS`, el 0..4 se exige en código al guardar las notas de
    las abiertas (`PuenteCalificacionIaImpl.guardarNotasAbiertas`), porque la tabla no sabe el
    método.
  - Fuera de rango **se rechaza, no se acota**: es la guarda que hoy pone la base, y protege a
    candidatos que están rindiendo (AC-23).
- **`nota_respuesta`, para el ajuste a mano**:
  - Ya tiene quién ajustó, el motivo (con su CHECK) y cuándo (V12), y la IA ya respeta
    `ajustada_por_usuario_id`. Falta dónde guardar **la nota que puso la IA**: una columna nueva,
    nula, que se llena en el primer ajuste y no se vuelve a tocar.
  - `puntaje` pasa a ser la nota que vale, y todas las sumas siguen leyéndolo sin cambios.
  - Si la abierta no tenía nota de la IA, `explicacion` (que es `NOT NULL`) lleva el motivo y la
    columna nueva queda nula.
  - El endpoint del panel va al estilo de `ServicioCalificacionPruebaImpl.ponerNota`.
- **Las instrucciones de la IA de una versión publicada** (la guía, el «qué debe tener» y el
  «qué evalúa») se pueden reescribir con candidatos dentro. Cada cambio guarda quién, cuándo y
  el texto anterior; la forma (tabla de historial u otra) la decide quien implemente.
- **Cambiar los puntos con candidatos dentro** (punto 17):
  - un historial de cada cambio con quién, cuándo y los puntos anteriores; la forma la decide
    quien implemente, como el de las instrucciones de la IA;
  - el reescalado toca `nota_respuesta.puntaje` y la columna nueva con la nota de la IA;
  - si la puntuación de las cerradas se guarda por respuesta, se reescribe; si se calcula al
    leer, basta con los puntos nuevos. Tiene que dar lo mismo que `resumenDeLoCerrado`.
- **Agentes**:
  - El de recomendaciones es un código nuevo: se amplía `agente_codigo_check` y se siembran su
    fila en `agente` y su instrucción activa en `instruccion_ia`. Sin instrucción activa, el
    ejecutor no llama al modelo.
  - El evaluador de las abiertas es el `EVALUADOR` de siempre, con un tercer formato elegido por
    el método, como ya hace con `CRITERIOS`. Si su instrucción activa habla de la escala 0–4, se
    publica una versión nueva que valga para los tres métodos.
  - **Su insumo crece.** Hoy `InsumoRespuestas` solo lleva el nombre del puesto y el nivel. En
    el método `PUNTOS` suma los datos de la vacante del punto 6, en el
    mensaje de datos y no en el `system`. Los métodos `NULL` y `CRITERIOS` siguen recibiendo
    lo de hoy.
- **Fase 2**: la forma de guardar entregables, partes calificadas y notas por criterio la decide
  quien la implemente, con estas condiciones:
  - la nota de cada criterio se guarda y se lee **por el id del criterio de esa versión**,
    nunca por código ni sumando `nota_criterio` sin filtrar;
  - la IA recibe como máximo **los puntos de la parte calificada**, no el total del criterio;
  - la red de `guardarNotasPrueba` se reproduce contra esos ids.

### Registros existentes

- **Nada existente cambia de comportamiento.**
  - Los bancos por nivel, el método `CRITERIOS`, el importador y los cuestionarios técnicos
    siguen igual.
  - La rúbrica de las pruebas actuales sigue en `criterio`.
  - Las evaluaciones ya rendidas se leen con su método.

### Trampas conocidas

- **Una rama nueva en la calificación de lo cerrado.** `calificarLoCerrado` necesita la rama del
  método `PUNTOS`. Si no, el `default -> null` del v3 deja las cerradas nuevas sin puntuar y sin
  avisar. Y `resumenDeLoCerrado` tiene que devolver lo mismo que esa rama.
- **Buscar el cuestionario técnico por propósito.** `findFirstByVacanteIdAndEstado` (y todo lo
  que busque el banco de una vacante) tiene que filtrar por propósito. Sin eso, el cuestionario
  técnico puede tomar las preguntas del Perfil Integral.
- **`nota_criterio` es de las tres etapas.** En la fase 1 no se escribe nada ahí. En la fase 2,
  toda suma filtra por los criterios de la versión (el caso del 675 sobre 100).
- **El grupo de prioridad necesita el resultado de la IA.** `grupoDe` recibe el
  `ResultadoPerfil` para buscar riesgos críticos. Tras un ajuste no hay resultado: se leen los
  hallazgos ya guardados. Si no, el ajuste deja el grupo viejo o revienta.
- **Recalcular tras un ajuste no es el camino de la IA.** Ese camino termina moviendo la
  postulación a «por confirmar»; el ajuste solo recalcula notas y grupo, y nunca llama a la
  máquina de estados.
- **Recalificar no es el camino completo.** Solo se vuelve a correr el evaluador de las
  abiertas. El agente del perfil (`AgentePotencialRiesgo`) no, porque `cerrarPerfilIntegral`
  mueve la postulación. Y como hoy la nota se recalcula en `cerrarPerfilIntegral`, tras una
  recalificación hay que recalcularla al guardar las notas del evaluador, por el mismo camino
  que el ajuste a mano.
- **Sin tope, el evaluador puede recibir muchas abiertas.** Hoy `insumoRespuestas` manda todas
  las de la postulación en una sola llamada. Con muchas, se reparten en tandas, cada una con la
  misma red de seguridad.
- **El código de un criterio no lo identifica.** Solo es único dentro de su versión, y con
  criterios por vacante habrá muchos con el mismo nombre. Columnas, Excel y notas van por id.

### Restricciones de compatibilidad

- **RF-53**: la clave, los puntos y los criterios nunca viajan al portal.
- **RF-138**: el candidato queda atado a la versión con la que empezó.
- **RF-147**: la IA no toca las cerradas.
- **Una vacante, una versión**, desde la primera postulación. Las dos excepciones cambian la
  vara para todos a la vez: las instrucciones de la IA, que recalifican a todos (decisión 17),
  y los puntos, que recalculan a todos sin IA (decisión 20).

## Verificación

- **Actor, rol y datos de prueba**:
  - un usuario con `editar_vacante` de **una empresa que no sea la plataforma**, para probar el
    corte entre empresas;
  - otro usuario solo con `ver_vacantes`;
  - una cuenta de candidato;
  - dos vacantes de la misma empresa (para copiar) y una de otra empresa.
- **Recorridos que deben quedar como E2E repetibles**:
  1. Crear una vacante con preguntas propias. Agregar una pregunta y ver aparecer «General».
     Crear un segundo criterio vacío, escribir una pregunta de cada tipo, intentar publicar con
     95 y leer la lista entera. Llegar a 100, publicar las preguntas y publicar la vacante.
  2. Postular, responder los cuatro tipos (con la escala en móvil) y entregar. Ver en la ficha
     los criterios con la parte del sistema calculada y la de la IA pendiente, sin nota del
     Perfil Integral.
  3. Copiar las preguntas de una vacante archivada, buscándola por nombre, y ver el borrador
     con sus criterios.
  4. Con la IA simulada, ajustar una abierta con motivo y ver en la ficha las dos notas y el
     total recalculado. Calificar a mano una abierta pendiente y ver aparecer la nota del Perfil
     Integral.
  5. Con candidatos calificados, cambiar la guía, confirmar, ver «Recalificando» y después las
     notas nuevas, con la abierta ajustada intacta.
  
  Las recomendaciones y la calificación de la IA se prueban con el agente simulado, como los
  demás agentes, no contra DeepSeek.
- **Riesgos que QA debe explorar con navegador**:
  - el tope y el piso de la opción múltiple;
  - una pregunta de 0 puntos;
  - quitar un criterio con preguntas dentro;
  - el 409 tras la primera postulación;
  - un ajuste por encima del máximo, sin motivo o sin permiso;
  - cambiar la guía sin saldo de IA, o dos veces seguidas mientras se recalifica;
  - una recalificación que se queda sin saldo a mitad, y su «Reintentar»;
  - arreglar una clave al revés con candidatos dentro, y ver moverse la nota y el ranking sin
    que nadie cambie de etapa;
  - copiar entre empresas (404);
  - ver la vista previa de una vacante con 100 preguntas, y cerrar el diálogo sin copiar;
  - una empresa sin banco propio: que no vea el de RENASER ni al crear la vacante ni en la
    configuración del banco, y que no pueda personalizarlo;
  - la guía con intento de inyección;
  - el balance del panel frente al del servidor tras un fallo de red;
  - la escala con 10 niveles en un teléfono.
- **Resultado que quiero comprobar personalmente**: el editor y el desglose en el panel. Hay
  inicio de sesión de por medio y **esa comprobación visual la hace el usuario**; se avisa ahora
  y no al entregar.
- **Avisos para quien verifique**:
  - `verify` corre las 143 pruebas de integración y `test` no: una migración puede pasar los
    unitarios y tumbarlas;
  - la suite E2E trae ~21 fallos de fondo por datos, así que el rojo se compara contra una
    corrida de referencia.

## Dependencias y dudas

- **Requiere**:
  - el ejecutor de agentes y su cola, como están;
  - el tope mensual de IA por empresa;
  - el patrón de envoltura de `AgentePruebaPuesto.conLaGuiaDeLaPrueba`, que se reutiliza, no se
    copia a mano.
- **Decisiones pendientes**: ninguna.
