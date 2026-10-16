# La prueba del puesto con el editor de preguntas (fase 2)

Estado: TERMINADO

## Objetivo

La fase 1 (`specs/Done/preguntas-propias-de-la-vacante.md`, PR #108 y panel #69, en producción
desde el 01/10/2026) dejó que cualquier empresa escriba en el panel, por vacante, las preguntas
del Perfil Integral: criterios con nombre, cuatro tipos de pregunta, puntos que suman 100, guía
para la IA, recomendaciones, copiar de otra vacante, ajuste a mano y recalificación.

La etapa técnica sigue con los dos instrumentos de antes, y ninguno sirve a una empresa cliente:

- **La prueba del puesto** sale de un catálogo de plantillas que se carga con scripts de Python.
- **El cuestionario CAZATALENTOS** es el método de RENASER, redactado por la IA desde una ficha.

Esta spec hace que **la prueba técnica de cada vacante nueva se escriba en el mismo editor**, con
lo que le falta a una prueba: el caso, los entregables y el tiempo (sin cambio inesperado).
**Sin entregables es un cuestionario**, sin que nadie lo elija. Sustituye a los dos
instrumentos en las vacantes nuevas, también en las de RENASER.

## Contexto

### Comportamiento actual

**La vacante elige el instrumento** (`vacante.instrumento_etapa_tecnica`, `PLANTILLA` o
`CUESTIONARIO_TECNICO`).
- En el panel, el bloque «Prueba técnica» de `Vacante.tsx` (3824-3911) tiene dos desplegables
  («Qué rendirá en la etapa técnica» y «Qué prueba del puesto rendirá») y «Cuánto tiempo tendrá»
  (`MinutosDeLaEtapa`, 4042-4108).
- La tarjeta «Preparar la prueba técnica» (`prueba-tecnica/EstadoDeLaPruebaTecnica.tsx`) sale
  siempre y lleva a la ficha y al cuestionario del `REDACTOR`.
- Publicar exige el instrumento listo (`ServicioVacantesPanelImpl.exigirInstrumentoTecnico`,
  y en el panel `loQueFaltaParaPublicar.ts` y `listaParaPublicar`, que repiten la regla).

**La prueba del puesto (`PLANTILLA`)**
- **Contenido**: `version_plantilla_prueba` (enunciado, consigna en PDF o Word, materiales,
  herramientas, modalidad `CRONOMETRADA` o `PLAZO_ABIERTO`, minutos o días, minuto del cambio
  inesperado y minutos extra, guía de calificación), `variante_cambio`, `entregable_requerido`
  (nombre, detalle, formato `ARCHIVO`/`ENLACE`/`CUALQUIERA`, obligatorio), el catálogo global
  `pregunta_prueba` (solo texto) y la rúbrica en la tabla compartida `criterio` (puntos y
  `metodo_verificacion` `AGENTE`/`PERSONA`).
- **Rendición**: `EntradaEtapaTecnica.crearAlEntrar` crea el `intento_prueba`. El reloj, el
  cambio inesperado (`sortearCambio`, `revelarCambioSiToca`), las entregas y el cierre por plazo
  (`entregarVencidos`, cada 60 s) viven en `ServicioPruebaImpl`. Los minutos de la vacante mandan
  sobre los de la plantilla y se leen al empezar.
- **Entregas**: cada nueva reemplaza a la anterior y se califica la última. **No hay tope de tres**
  (ni en el backend ni en el portal), aunque la spec de la fase 1 lo decía. Entregar solo exige los
  entregables obligatorios.
- **Calificación**: `AgentePruebaPuesto` califica criterio por criterio con todo lo legible. Su
  insumo lleva un `duracionMinutos` que es el de la plantilla, no el efectivo, y un
  `queBuscaLaVacante` propio (`ContextoDeLaVacante`), distinto de los datos de la vacante de la
  fase 1. La red `PuentePruebaIaImpl.guardarNotasPrueba` trabaja **por código** de criterio. La
  nota de la etapa solo se escribe con la rúbrica entera (`ponderarSiLaRubricaEstaEntera`), y ese
  es el único sitio que pasa a «por confirmar».
- **A mano**: `ServicioCalificacionPruebaImpl.ponerNota` (0..puntos del criterio, con
  explicación), que no recalcula la nota de la etapa.
- **Plazo** (PR #89): cierre de la prueba por vacante y plazo propio por persona.

**El cuestionario técnico (`CUESTIONARIO_TECNICO`)**: una `evaluacion` con propósito propio, sobre
un `version_banco` de la vacante redactado por el `REDACTOR` desde `ficha_vacante`. Exige
responder todas las preguntas. La ficha también deduce el tamaño de la empresa y **sugiere la
versión de pesos** (`ServicioFichaVacanteImpl`).

**Préstamo**: las plantillas de prueba se prestan como el banco (`Instrumento.PRUEBA`,
`DuenoDelInstrumento`). Personalizarlas copia las de RENASER (`CopiadorDeInstrumentosImpl`).
Desde la fase 1 está bloqueado personalizar el banco, pero su código sigue. **El panel no tiene
ningún botón de personalizar**: solo se hace por la API (`PersonalizacionController` y
`PlataformaController`). Aun así, `src/panel/pruebas/borrador.ts:64` le dice a la empresa «hay
que personalizar las pruebas».

**Ranking y Excel**
- La pestaña «Prueba del puesto» pinta una columna por criterio, **agrupada por nombre**
  (`ranking.ts`, `criteriosDeLaTanda` y la clave `criterio:${nombre}`). `NotaCriterioResponse` no
  lleva id.
- El Excel empareja celdas por `(nombre, código, techo)` (`ServicioExcelRankingImpl`).
- Una celda vacía dice «—» y no distingue «pendiente» de «en blanco».
- La pestaña «Perfil Integral» solo tiene las columnas de los ocho criterios del currículum.
- El ranking no tiene la marca «Recalificando» que la fase 1 pedía.

### Lo que deja la fase 1 y se reutiliza

- **Modelo**: `version_banco` con `proposito` (hoy `PERFIL_INTEGRAL` y `CUESTIONARIO_TECNICO`),
  método `PUNTOS`, `guia_calificacion` con `version_guia`; `criterio_banco`; `pregunta.puntos`,
  `criterio_banco_id`, `que_debe_tener`; `opcion.orden`; `nota_respuesta.puntaje_ia` y
  `version_guia`; agente `RECOMENDADOR` con `propuesta_preguntas` (V66).
- **Servicio y API**: `ServicioPreguntasVacanteImpl` y `PreguntasVacanteController`
  (`/vacantes/{id}/preguntas-propias`), con borrador, criterios, preguntas, publicación,
  instrucciones, recalificación, puntos, copiables con vista previa, copia y recomendaciones.
- **Reglas y cálculo**: `faltasParaPublicar` y `ReglasDePuntos`, `CalificacionPorPuntos`,
  `cambiarPuntos` y `escalar`, `corregirInstrucciones` y `reintentarRecalificacion`,
  `guardarNotasPorPuntos` (descarta lo calculado con una guía vieja y aplica todo o nada).
- **Panel**: `EditorDePreguntas.tsx`, `BloqueCriterio.tsx`, `FormularioPregunta.tsx`,
  `PreguntasPublicadas.tsx`, `Recomendaciones.tsx`, `CopiarDeOtraVacante.tsx` y la API
  `src/panel/api/preguntasPropias.ts`. Hoy están atados a una URL, una clave de consulta y unos
  textos fijos, y dan por hecho que toda abierta lleva puntos.
- **Portal**: `Formatos.tsx` ya pinta los cuatro tipos en la evaluación.
- **Diferencia con su spec, por decisión del 30/09/2026**: toda vacante nueva nace con
  «Preguntas propias», también en RENASER. Su banco por nivel se puede elegir después.

### Decisiones ya tomadas (fase 1, del 11/09 al 30/09/2026)

1. **Una sola prueba del puesto.** Sin entregables es un cuestionario.
2. **Criterios en lugar de categorías.** Son de la versión, se congelan al publicar y se
   reutilizan copiando de otra vacante; no hay catálogo.
3. **En la prueba, lo abierto y los entregables no llevan puntos: la IA califica el criterio
   entero.** Cada criterio tiene una **parte automática** (la suma de sus cerradas) y una **parte
   calificada** (sus puntos y quién la califica, la IA o una persona). Todos suman 100. **Quien
   implemente no debe unificar este camino con el del banco**, que califica pregunta a pregunta.
4. **Los criterios los propone la IA al armar la prueba, nunca al calificar.**
5. **Las vacantes nuevas solo usan el editor**, también las de RENASER. Las plantillas y el
   cuestionario CAZATALENTOS dejan de ofrecerse a vacantes nuevas. Las existentes siguen igual.
6. **Las instrucciones de la IA se corrigen con candidatos dentro y se recalifica a todos**, con
   la guía numerada y todo o nada por persona.
7. **Los puntos se cambian con candidatos dentro y se recalcula a todos al instante, sin IA.**
   **Ningún texto se toca** una vez congelado.
8. **Puntos enteros.** La IA recibe los datos de la vacante (título, descripción, propósito,
   responsabilidades y requisitos; el puesto con su nivel y familia; la solicitud de talento sin el
   motivo), nunca modalidad, horario, ubicación, ciudad ni remuneración.

### Decisiones nuevas (01/10/2026)

9. **La prueba se congela en la primera rendición, no en la primera postulación** (confirmada). La prueba se
   rinde en la segunda etapa: hasta que alguien la abra se puede publicar otra versión, como ya
   pasa hoy con los minutos y el instrumento.
10. **Cada criterio dice qué entregables mira** (confirmada). Un entregable puede estar en varios criterios, y
    todo entregable tiene que estar en al menos uno. Así la IA y la persona saben qué leer, y se
    puede impedir que un criterio de IA dependa solo de enlaces, que la IA no lee.
11. **No se puede entregar sin responder todas las preguntas** (confirmada) ni sin los
    entregables obligatorios. Quien deja una pregunta sin responder no muestra interés por el
    puesto.
    - **Al vencer el tiempo con algo sin responder** (confirmada): el intento se
      cierra como «no completada». No se califica, no gasta IA y **no sale en el ranking**, ni
      como pendiente. La persona aparece en una lista aparte, «No completaron la prueba», desde
      donde alguien del equipo cierra su proceso. Nadie cambia de etapa solo.
    - Con todo respondido, al vencer se entrega sola, como hoy.
12. **La ficha y el `REDACTOR` no se ofrecen a vacantes nuevas** (confirmada), y con ellos desaparece la
    sugerencia de pesos que salía de la ficha. «Pesos de la decisión» se queda, con los pesos
    generales de entrada.
13. **Se elimina personalizar el banco y las pruebas** (confirmada), en vez de solo bloquearlo.
    - Desaparecen encender y apagar esos dos instrumentos, en la API de la empresa y en la de la
      plataforma, y el código que copiaba el catálogo de RENASER. Pedirlo contesta que ya no
      existe.
    - Desaparece del panel el texto que invita a personalizar las pruebas.
    - Quien ya los personalizó conserva lo copiado: es suyo.
    - **Se quedan**: personalizar los pesos y las plantillas de evaluación, que siguen
      sirviendo, y el préstamo del banco y de las pruebas para las vacantes existentes que lo
      usan. Ese préstamo se borra en una limpieza aparte cuando ya no quede ninguna.
14. **Entregas como hoy** (confirmada): la nueva reemplaza a la anterior y se califica la
    última, sin tope. Al entregar, o al vencer, ya no se sube nada.
15. **Sin cambio inesperado** (confirmada). La prueba nueva no tiene variantes ni minutos extra.
    Las plantillas existentes lo conservan.

### Pantallas o procesos afectados

- **Panel**: el bloque «Prueba técnica» de la vacante; el editor en modo prueba; la pestaña
  «Prueba» de la ficha del candidato; los rankings «Prueba del puesto» y «Perfil Integral» y su
  Excel; la personalización.
- **Portal**: la prueba (`src/paginas/prueba/Prueba.tsx`) y el enrutado de la etapa
  (`momentoDeLaEtapa`).
- **Backend**: entrada a la etapa, intento, reloj, entregas, cierre, calificación por IA y a mano,
  nota de la etapa, ranking, Excel, publicación de la vacante, recomendaciones y copia.

### Referencias

- `specs/Done/preguntas-propias-de-la-vacante.md`: decisiones, editor y reglas de la fase 1.
- `docs/PRUEBA-DEL-PUESTO.md`: la guía, la red de seguridad y la fuga del catálogo.
- `docs/DECISION-UNA-VACANTE-UNA-VERSION.md`.
- [DESIGN.md del panel](../../RenaserOsPostulantes/DESIGN.md).

## Alcance

**Incluye:**

- El bloque «Prueba técnica» de la vacante nueva, sin nada que elegir: estado y «Armar la prueba».
- El editor en `/admin/vacantes/:id/prueba`: todo lo de la fase 1, más el caso, el tiempo (sin
  cambio inesperado), los entregables y la parte calificada de cada criterio.
- Publicar la prueba y exigirla para publicar la vacante.
- Copiar de otra vacante y la vista previa, con caso, tiempo y entregables.
- Recomendaciones por IA de la prueba entera.
- La rendición en el portal con los cuatro tipos de pregunta.
- La calificación: cerradas por el sistema, parte calificada por la IA o por una persona.
- El ajuste a mano de la parte calificada de un criterio.
- Corregir las instrucciones de la IA y cambiar los puntos con candidatos dentro.
- La pestaña «Prueba» de la ficha con el desglose por criterio.
- Rankings y Excel por id de criterio, con «pendiente» y «Recalificando»; las columnas de los
  criterios del banco en «Perfil Integral».
- Eliminar la personalización del banco y de las pruebas (decisión 13).

**Queda fuera:**

- Las vacantes con `PLANTILLA` o `CUESTIONARIO_TECNICO`: siguen igual, sin migrar contenido.
- Pasar las plantillas de RENASER al editor. Para sus vacantes nuevas, RENASER escribe o copia.
- La pantalla `/admin/pruebas` y los scripts `cargar-prueba-de-la-empresa.py`,
  `asignar-prueba-a-vacante.py`, `excel-de-la-prueba.py` y `calificar-pruebas.py`: siguen para las
  vacantes existentes y no sirven para las nuevas.
- Reutilizar el catálogo `pregunta_prueba`. Las preguntas son de la vacante; eso cierra la fuga
  entre empresas por construcción.
- Cambiar los pesos de la decisión con candidatos dentro (spec aparte, ofrecida).
- Banderas, repreguntas y el método C1–C4.
- El cambio inesperado (decisión 15).

## Comportamiento esperado

1. **El bloque «Prueba técnica» de una vacante nueva.**
   - No tiene «Qué rendirá en la etapa técnica», «Qué prueba del puesto rendirá» ni «Cuánto
     tiempo tendrá». Tampoco la tarjeta de la ficha y el cuestionario.
   - Enseña el estado («Sin prueba», «Borrador · 70 de 100 puntos · 2 entregables», «Publicada ·
     5 criterios · 2 entregables · 90 min» o «Publicada · cuestionario · 12 preguntas · 30 min»)
     y el botón «Armar la prueba».
   - «Pesos de la decisión» sigue igual.
   - Publicar la vacante exige la prueba publicada. El checklist y el botón miran la misma regla.
   - Una vacante existente sigue viendo lo de hoy.
2. **El editor en modo prueba**, en `/admin/vacantes/:id/prueba`. Es el de la fase 1, con estas
   diferencias:
   - **El caso**: el enunciado (obligatorio si hay entregables, opcional si no), el enunciado en
     PDF o Word adjunto (opcional, como hoy la consigna), los materiales y las herramientas
     permitidas.
   - **El tiempo**: la modalidad (cronometrada, con minutos, o plazo abierto, con días), con los
     mismos límites que hoy. Sin cambio inesperado.
   - **Los entregables**: nombre, qué debe contener, formato (archivo, enlace o cualquiera), si es
     obligatorio y «Qué debe tener una buena entrega» (opcional, hasta 1000 caracteres).
   - **En cada criterio, la parte calificada**: sus puntos, quién la califica (IA o persona) y qué
     entregables mira. La cabecera dice «30 pts · sistema 10 + IA 20» o «sistema 10 + persona
     20».
   - **Las abiertas no llevan puntos.** Tienen su «Qué debe tener una buena respuesta».
   - El balance suma, por criterio, la parte automática más la calificada, hasta 100.
   - Con cero entregables, la cabecera y los textos dicen «cuestionario».
3. **Publicar la prueba.** El servidor exige y devuelve la lista entera de lo que falta:
   - que todo sume exactamente 100, con puntos enteros;
   - que toda pregunta esté en un criterio y que ningún criterio quede vacío (sin cerradas ni
     parte calificada);
   - que una parte calificada mayor que 0 tenga calificador y mire algo: al menos una abierta del
     criterio o un entregable;
   - que un criterio con abiertas o entregables tenga parte calificada mayor que 0;
   - que todo entregable esté en al menos un criterio;
   - que un criterio de IA no mire solo entregables de enlace sin abiertas;
   - el enunciado si hay entregables, y un tiempo válido;
   - las reglas por tipo de la fase 1.
4. **Después de publicar** (decisión 9).
   - **Hasta la primera rendición**: se puede abrir un borrador desde la publicada y publicarlo;
     la anterior se archiva.
   - **Desde la primera rendición**: el contenido queda congelado y otra publicación contesta
     409. Solo se pueden corregir las instrucciones de la IA y cambiar los puntos (puntos 10 y 11).
5. **Recomendaciones por IA.**
   - Como en la fase 1: completa lo que falta hasta 100, propone y no publica, y valida con un
     reintento.
   - Además propone el caso, los entregables, la parte calificada de cada criterio, su
     calificador y qué entregables mira.
   - La validación añade las reglas del punto 3: un criterio que solo mira entregables de enlace,
     sin abiertas, tiene que ser de persona.
   - Las recomendaciones de la prueba y las del banco de la misma vacante no chocan entre sí.
6. **Copiar de otra vacante.** Como en la fase 1 (cerradas y archivadas incluidas, filtro por
   nivel y vista previa), con caso, adjunto, tiempo y entregables. Solo se ofrecen
   vacantes con prueba propia publicada.
7. **El candidato rinde.**
   - Como hoy: pantalla previa con el caso, la modalidad y la duración; reloj; entregables que se
     reemplazan; plazo propio por persona. Sin cambio inesperado.
   - Las preguntas vienen en los cuatro tipos, con los mismos componentes que la evaluación.
   - Nunca le llegan puntos, claves, criterios ni calificadores.
   - **Solo puede entregar con todas las preguntas respondidas y los entregables obligatorios
     subidos** (decisión 11). Hasta entonces, «Entregar» dice qué falta y lleva a la primera
     pregunta sin responder. Un texto en blanco no cuenta como respondido; una opción múltiple
     necesita al menos una marcada.
   - **Al vencer el tiempo**: con todo completo se entrega sola; si falta algo, el intento se
     cierra como «no completada», no se califica, no gasta IA y no sale en el ranking. La
     postulación no cambia de etapa sola.
8. **La calificación.**
   - **Las cerradas**, el sistema, al entregar.
   - **La parte calificada de los criterios de IA**, el agente de la prueba, que recibe:
     - los datos de la vacante de la fase 1 (decisión 8);
     - el caso y el tiempo efectivo, no el de una plantilla;
     - la guía en el `system`, con la envoltura y la marca sorteada;
     - por criterio: su nombre, qué evalúa, el máximo **de su parte calificada**, sus abiertas
       con su «qué debe tener» y la respuesta, y los entregables que mira con su «qué debe tener»
       y su texto legible o el motivo por el que no se pudo leer.
   - **La red de seguridad**, por id de criterio: descarta criterios desconocidos, de persona y
     sin explicación; acota cada nota a su parte calificada; nunca pisa un ajuste a mano.
   - **Los criterios de persona**, una persona, desde la ficha.
   - **Nota del criterio** = parte automática + parte calificada. Un criterio con parte
     calificada sin nota está **pendiente**, aunque su parte automática ya exista.
   - **La nota de la etapa** solo existe con todos los criterios enteros. Al completarse pasa a
     «por confirmar», como hoy, la complete la IA o una persona.
9. **Ajuste a mano.** En la ficha, quien tiene `ajustar_nota` ajusta la parte calificada de un
   criterio, de 0 a sus puntos y con motivo, o la pone si es de persona o la IA no pudo. La parte
   automática no se ajusta. La nota de la IA queda a la vista y la IA no vuelve a tocarla. Se
   recalcula la nota de la etapa sin mover a nadie de etapa, salvo el paso a «por confirmar» del
   punto 8 cuando el ajuste completa la rúbrica.
10. **Corregir las instrucciones de la IA**: la guía, el «qué debe tener» de abiertas y
    entregables y el «qué evalúa» de cada criterio. Como en la fase 1: confirmación, guía
    numerada, todo o nada por persona, la nota anterior hasta que llegue la nueva, «Pendiente de
    recalificar» y «Reintentar» si falla o falta saldo, y la ventana de «sin saldo». Solo se
    recalifican los criterios de IA, y los ajustados a mano no se tocan.
11. **Cambiar los puntos**: los de cerradas, opciones y niveles, y los de cada parte calificada.
    Como en la fase 1: el total sigue en 100, las cerradas se recalculan, la parte calificada
    ya puesta (IA, persona o ajuste) se escala en proporción, sin IA y sin mover a nadie de etapa.
12. **La pestaña «Prueba» de la ficha.**
    - Cada criterio con su nota y de dónde sale («Sistema 8/10 + IA 16/20» o «Sistema 8/10 +
      persona pendiente»).
    - Al abrirlo: sus cerradas con la opción marcada y los puntos, sus abiertas con la respuesta y
      los entregables que mira.
    - La explicación de la IA, quién ajustó, cuándo y por qué.
    - Arriba, la nota de la etapa o qué falta para tenerla.
13. **Rankings y Excel.**
    - **Prueba del puesto**: una columna por criterio, identificada por id, con el formato y los
      tonos de hoy («24/30»). Un criterio pendiente dice «pendiente» y uno de persona sin nota
      queda en blanco. Dos vacantes con criterios del mismo nombre no juntan columnas.
    - **Perfil Integral**: en las vacantes con preguntas propias, una columna por criterio del
      banco (por id), junto a las del currículum, con «pendiente» mientras falte alguna abierta.
    - **«Recalificando con la guía nueva»** en ambas pestañas mientras dure una recalificación.
    - El Excel lleva las mismas columnas, emparejadas por id.
    - **Quien no completó la prueba** no sale en el ranking de «Prueba del puesto» ni en su
      Excel. Debajo, una lista plegada «No completaron la prueba (2)» los nombra, con qué les
      faltó y el botón para cerrar su proceso.
14. **Personalizar el banco o las pruebas ya no existe** (decisión 13). Encenderlo o apagarlo,
    desde la empresa o desde la plataforma, contesta 400 con «Esta personalización ya no
    existe» y no copia ni cambia nada. Los pesos y las plantillas de evaluación se siguen
    personalizando como hoy.
15. **Si algo falla**, se dice qué pasó y no se miente sobre lo guardado, como en la fase 1.

## Referencia visual

### La vacante nueva

```
Qué responderá quien postule
  (•) Preguntas propias de esta vacante    ( ) Sin evaluación
  Publicadas · 4 criterios · 12 preguntas        [ Ver las preguntas → ]

Prueba técnica
  Borrador · 70 de 100 puntos · 2 entregables    [ Armar la prueba → ]

Pesos de la decisión
  [ Los pesos generales                  ▾ ]
```

### El editor en modo prueba (escritorio)

```
← La vacante                                Asistente Contable · Prueba técnica
┌──────────────────────────────────────────────────────────────────────────────┐
│ BORRADOR   90 de 100 puntos · 3 criterios · 2 entregables · 2 avisos         │
│ ▸ Faltan 10 puntos   ▸ «Tablero.xlsx» no está en ningún criterio             │
│                                                   [ Publicar la prueba ]     │
└──────────────────────────────────────────────────────────────────────────────┘
[ + Agregar criterio ] [ Recomendaciones por IA ] [ Copiar de otra vacante ]

El caso                                                        (obligatorio)
  Enunciado [ La empresa cerró marzo con un descuadre de…                    ]
  Adjunto   [ Subir PDF o Word ]   Materiales [ … ]   Herramientas [ Excel ]
Tiempo
  (•) Cronometrada [ 90 ] min   ( ) Plazo abierto [ ] días
Entregables
  Tablero.xlsx · archivo · obligatorio                              ↑ ↓ ✎ ✕
  Video de 2 min · enlace · opcional                                ↑ ↓ ✎ ✕
Guía de calificación para la IA (opcional · 0 de 2000)

┏ Conocimiento contable ────────── 30 pts · sistema 10 + IA 20 ── ↑ ↓ ✎ ✕ ┓
┃ Qué evalúa: domina el registro y el cierre mensual.                       ┃
┃ Parte calificada: [ 20 ] pts · [ IA ▾ ] · Mira: [✓] Tablero.xlsx         ┃
┃ ┌ Opción única · 4 pts ──────────────────────────────────── ↑ ↓ ✎ ✕ ┐  ┃
┃ ┌ Abierta ───────────────────────────────────────────────── ↑ ↓ ✎ ✕ ┐  ┃
┃ │ ¿Cómo hallaste el descuadre?  Qué debe tener: la cuenta y el monto. │  ┃
┃ [ + Agregar pregunta ]                                                    ┃
┗━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━┛
┏ Comunicación ─────────────────────────────── 20 pts · persona 20 ┓
┃ Parte calificada: [ 20 ] pts · [ Persona ▾ ] · Mira: [✓] Video   ┃
┗━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━┛
```

- Se reutilizan los bloques, el balance, las publicadas, las recomendaciones y la copia de la
  fase 1. El bloque de tiempo de `DatosDeLaPrueba.tsx` está atado a la plantilla y no se reutiliza
  tal cual.
- «Mira» es una lista de casillas con los entregables. Sin entregables, la línea no sale.

### El portal: una cerrada y la entrega con huecos

```
Pregunta 3 de 8                                        ⏱ 54:12
¿Qué libro registra primero una venta al crédito?
  ○ Libro diario   ○ Libro mayor   ○ Caja
[ Anterior ]                                       [ Siguiente ]

──── al pulsar «Entregar» ────
Para entregar te falta responder 2 preguntas (la 3 y la 7).
Los entregables obligatorios están completos.
[ Ir a la pregunta 3 ]
```

### El ranking

```
Candidato      │ Conocimiento contable │ Manejo de Excel │ Comunicación │ Nota
───────────────┼───────────────────────┼─────────────────┼──────────────┼──────
Ana Rojas      │ 24/30                 │ 10/10           │ 15/20        │ 81
Luis Paredes   │ pendiente             │ 6/10            │              │ —

▸ No completaron la prueba (2)
  Marta Díaz · le faltaron 3 preguntas            [ Cerrar su proceso ]
  Pedro Soto · le faltó «Tablero.xlsx»            [ Cerrar su proceso ]
```

### Estados a dibujar

- **Vacío** del editor: «Todavía no hay prueba», con agregar criterio, recomendar y copiar.
- **Cuestionario**: sin entregables, la cabecera dice «Cuestionario» y el bloque del caso es
  opcional.
- **Publicada**: solo lectura salvo puntos e instrucciones de la IA, con el cartel de la fase 1;
  antes de la primera rendición, además «Abrir un borrador».
- **Ficha**: criterio entero, criterio pendiente de la IA, criterio de persona por calificar,
  criterio ajustado, entregable que no se pudo leer.
- **Portal**: «Entregar» con lo que falta; al vencer con huecos, «Tu tiempo terminó y la
  prueba quedó sin completar»; el resto, como hoy.
- **Ranking**: pendiente, en blanco, Recalificando y la lista «No completaron la prueba».
- **Datos largos**: un enunciado de 5000 caracteres, diez entregables y un criterio que mira
  seis.

### Móvil y escritorio

- Editor: lo de la fase 1; el caso y el tiempo se apilan; «Mira» va una casilla por línea.
- Portal: las cerradas como en la evaluación; la escala en columna.

### Reglas visuales

Las de la fase 1 y el `DESIGN.md` del panel: una acción principal (Publicar), ámbar solo para lo
que frena la publicación, chips neutros para tipo, puntos y calificador, controles de 44 px.

## Criterios de aceptación

- **AC-01**: Dada una vacante nueva, cuando se abre su configuración, entonces el bloque
  «Prueba técnica» no tiene los dos desplegables, ni los minutos, ni la tarjeta de la ficha:
  tiene el estado y «Armar la prueba». «Pesos de la decisión» sigue. En una vacante existente
  con `PLANTILLA` o `CUESTIONARIO_TECNICO` todo sigue como hoy.
- **AC-02**: Dada una vacante nueva de RENASER, entonces no se ofrecen plantillas, el cuestionario
  CAZATALENTOS ni la ficha.
- **AC-03**: Dada una vacante nueva sin prueba publicada, cuando se intenta publicar, entonces el
  servidor lo rechaza y el panel no dice «Todo listo».
- **AC-04**: Dada una prueba con 90 puntos, un entregable en ningún criterio, un criterio de IA
  que solo mira un enlace y entregables sin enunciado, cuando se publica, entonces contesta 400
  con **las cuatro** faltas y no publica nada.
- **AC-05**: Dado un criterio mixto (10 de cerradas + 20 de parte calificada), entonces su
  cabecera dice «30 pts · sistema 10 + IA 20» y el balance cuenta 30. Una abierta no tiene campo
  de puntos.
- **AC-06**: Dada una prueba sin entregables, entonces el editor, la vacante y el portal la llaman
  cuestionario, y el enunciado no es obligatorio.
- **AC-07**: Dada una prueba publicada sin rendiciones, cuando se abre un borrador y se publica,
  entonces sustituye a la anterior. Desde la primera rendición, otra publicación contesta 409 y
  cambiar los puntos o las instrucciones sigue funcionando.
- **AC-08**: Dado un candidato en la etapa técnica de una vacante nueva, cuando abre la prueba,
  entonces ve el caso, el reloj y las preguntas en los cuatro tipos, y la respuesta de la API no
  trae puntos, claves, criterios ni calificadores.
- **AC-09**: Dada una prueba nueva, entonces ni el editor ni el portal tienen cambio inesperado,
  y una plantilla existente que lo tenía lo sigue mostrando.
- **AC-10**: Dadas 2 de 8 preguntas sin responder, cuando el candidato pulsa «Entregar»,
  entonces no se entrega, se le dice cuáles faltan y el servidor rechaza la entrega aunque se
  llame a la API directamente. Una abierta con solo espacios cuenta como sin responder. Sin un
  entregable obligatorio, tampoco se entrega.
- **AC-11**: Dado un vencimiento con todo respondido, entonces se entrega sola. Con una pregunta
  sin responder, el intento queda «no completada»: no se encola IA, no hay nota de la etapa, no
  sale en el ranking ni en su Excel, aparece en «No completaron la prueba» y la postulación
  sigue en su etapa hasta que alguien cierre su proceso.
- **AC-12**: Dada una entrega, entonces las cerradas se puntúan al momento y la IA recibe por cada
  criterio de IA su máximo de parte calificada, sus abiertas y los entregables que mira, los datos
  de la vacante sin los excluidos y los minutos efectivos.
- **AC-13**: Dado un criterio mixto de 30 (10 + 20), cuando la IA devuelve 28, entonces se guarda
  20 y el criterio vale lo de las cerradas + 20.
- **AC-14**: Dada una nota de la IA para un criterio de persona, uno desconocido o sin explicación,
  entonces se descarta. La red trabaja por id: dos criterios con el mismo código en versiones
  distintas no se confunden.
- **AC-15**: Dado un criterio mixto con solo su parte automática, entonces la ficha y el ranking
  dicen «pendiente» y no existe nota de la etapa.
- **AC-16**: Dado un criterio de persona, cuando alguien con `ajustar_nota` pone 15 de 20 con
  motivo y era lo último que faltaba, entonces se escribe la nota de la etapa y la postulación
  pasa a «por confirmar».
- **AC-17**: Dado un ajuste de la parte calificada a 21 de 20, sin motivo o sin permiso, entonces
  400 o 403. La parte automática no se puede ajustar. Si la IA vuelve a calificar, el ajuste
  sigue.
- **AC-18**: Dada una prueba con 3 candidatos calificados, uno con un criterio ajustado, cuando se
  cambia la guía, entonces se recalifica a los 3 solo en los criterios de IA, el ajustado no
  cambia y nadie cambia de etapa. Sin saldo, sale la ventana y no se guarda.
- **AC-19**: Dada una parte calificada de 20 con un 12, cuando se cambia a 15 (y otro criterio
  sube 5), entonces queda 9 de 15, las cerradas se recalculan, no se encola IA y nadie cambia de
  etapa.
- **AC-20**: Dada la vacante A con una prueba con entregables y tiempo, cuando la vacante B la
  copia, entonces el borrador trae caso, adjunto, tiempo, entregables y qué mira cada
  criterio. La vista previa los enseña. Copiar desde otra empresa contesta 404.
- **AC-21**: Dadas recomendaciones de la IA con un criterio de IA que solo mira un enlace, dos
  veces seguidas, entonces la generación queda fallida. Pedir recomendaciones de la prueba con
  una del banco en curso no las mezcla.
- **AC-22**: Dadas dos vacantes con un criterio «Comunicación» cada una, cuando se ven sus
  rankings y Excel, entonces cada columna sale de su propio criterio (por id) y no se juntan.
- **AC-23**: Dada una vacante con preguntas propias, cuando se abre el ranking del Perfil Integral,
  entonces hay una columna por criterio del banco junto a las del currículum, «pendiente» si falta
  una abierta, y el Excel las lleva.
- **AC-24**: Dada una recalificación en curso, entonces el ranking marca «Recalificando con la
  guía nueva» en las filas afectadas.
- **AC-25**: Dada la pestaña «Prueba» de la ficha, entonces cada criterio enseña su nota y de dónde
  sale, sus cerradas con la opción marcada, sus abiertas y sus entregables, y la suma cuadra con
  la nota de la etapa.
- **AC-26**: Dada una empresa cualquiera, cuando ella o la plataforma intentan encender o apagar
  la personalización del banco o de las pruebas, entonces contesta 400, no se copia nada y su
  bandera no cambia. Una empresa que ya las tenía personalizadas sigue usando lo suyo en sus
  vacantes existentes. Personalizar los pesos sigue funcionando. El panel ya no dice «hay que
  personalizar las pruebas».
- **AC-27**: Dadas las vacantes existentes con `PLANTILLA` y `CUESTIONARIO_TECNICO`, cuando corre
  la suite completa, entonces sus pruebas de integración y E2E pasan sin tocarlas.
- **AC-28**: Dado un usuario sin `editar_vacante`, entonces ve el editor en solo lectura, sin
  botones que acaben en 403.
- **AC-29**: Dada una prueba con 60 preguntas y 12 criterios, cuando se rinde y se califica,
  entonces todo recibe nota, aunque la IA necesite varias llamadas.

## Casos límite y errores

- **Datos inválidos**: los de la fase 1, más una parte calificada negativa o con decimales, un
  calificador ausente, un entregable sin nombre o sin formato, un tiempo fuera de límites y un
  adjunto que no sea PDF o Word. Todos dan 400 con la lista entera.
- **Quitar un entregable** que miran criterios: se pide confirmar y desaparece de sus «Mira».
- **Cambiar un criterio de IA a persona** antes de publicar: no pide nada; después de la primera
  rendición no se puede, porque es contenido.
- **Una entrega que no se puede leer**: la IA lo sabe por el motivo y califica con lo demás; una
  persona puede ajustar.
- **Permisos**: editar, publicar, copiar y recomendar exigen `editar_vacante`; ajustar o calificar
  a mano, `ajustar_nota`; ver, `ver_vacantes`; ver el contenido de los entregables,
  `descargar_entregables`. El dato de si se puede editar viaja en el DTO.
- **Doble envío**: publicar dos veces no crea dos versiones; dos ajustes seguidos dejan el último;
  pedir recomendaciones con una en curso no encola otra.
- **El cambio de vacante mientras alguien rinde**: el intento sigue con la versión con que empezó.
- **Plazo propio por persona**: alargarlo antes de que venza evita el «no completada»; después
  de vencer no se reabre desde esta spec.
- **Sin saldo de IA**: las cerradas y los criterios de persona se califican igual; los de IA
  quedan pendientes con el motivo y «Reintentar».

## Datos y compatibilidad

### Qué hay que guardar

La forma la decide quien implemente, sin fijar el número de migración (main va por la V66 y hay
sesiones paralelas: ver las memorias de colisiones). Estas son las condiciones:

- **La vacante** distingue la prueba propia de los dos instrumentos viejos: un tercer valor en
  `instrumento_etapa_tecnica` (por ejemplo `PRUEBA_PROPIA`), que es el que reciben las vacantes
  nuevas. Las existentes conservan el suyo.
- **El contenido**: preguntas, opciones, criterios, guía con número de versión, caso, tiempo,
  entregables, «qué debe tener» de cada entregable, parte calificada, calificador y
  qué entregables mira cada criterio, todo de la versión.
  - Recomendación: un tercer propósito `PRUEBA_PUESTO` en `version_banco`, sobre las tablas de la
    fase 1, con lo que falta añadido. Así se reutilizan el editor, la copia, las recomendaciones,
    la validación, la guía numerada, el cambio de puntos y la recalificación.
  - La rendición reutiliza la maquinaria del `intento_prueba` (reloj, entregas, cierre por
    plazo), que deja de exigir una `version_plantilla_prueba`. El sorteo del cambio inesperado
    no corre para estas pruebas.
- **Las notas por criterio** se guardan y se leen **por el id del criterio de esa versión**,
  separando parte automática y parte calificada, con la nota de la IA aparte cuando hay ajuste y
  el número de guía con que se calculó. Nunca se suma `nota_criterio` sin filtrar.
- **Las respuestas a cerradas** guardan la opción u opciones marcadas.
- **El intento** distingue «entregada» de «no completada» (vencida con huecos).
- **Las recomendaciones** llevan propósito, para que las del banco y las de la prueba de una misma
  vacante no se pisen (hoy hay una sola viva por vacante).

### Registros existentes

- Las vacantes, plantillas, intentos, cuestionarios y notas existentes no cambian de
  comportamiento ni de datos.
- Las vacantes ya creadas, publicadas o no, siguen con su instrumento.

### Trampas conocidas

- **Seis sitios deciden «cuestionario o plantilla»** y un tercer valor caería en la rama de la
  plantilla: `EntradaEtapaTecnica` (62, 95), `ServicioCalificacionPruebaImpl` (120-124, 429),
  `ServicioPerfilIntegralPanelImpl` (793, 1041), `ServicioVacantesPanelImpl` (635, 988, 1034),
  `ServicioPostulacionPortalImpl` (615) y el CHECK de la V43. En el panel: `Vacante.tsx` (436-441,
  3720, 3845, 3856, 3996, 4003), `CierreDePrueba.tsx` (572, 713), `estados.ts` (260) y el tipo
  `InstrumentoTecnico`.
- **El esquema ata a la plantilla**: `intento_prueba.version_plantilla_prueba_id` y
  `entregable` → `entregable_requerido` son NOT NULL hacia ella, y
  `nota_criterio.criterio_id` es FK a `criterio`.
- **La red y el agente trabajan por código**, y el ranking y el Excel por nombre o
  `(nombre, código, techo)`. `NotaCriterioResponse` y `NotaCriterio` del panel no llevan id, y
  `ranking.test.ts` da por hecho la clave por nombre.
- **El método `PUNTOS` cuelga de `postulacion.evaluacionId`** (insumo, guardado, ajuste,
  reescalado y recálculo). La prueba no puede pasar por ahí sin separar el destino.
- **La cola**: `recalificar` está fijo al `EVALUADOR`, y `encolarPruebaPuesto` no vuelve a encolar
  si ya calificó.
- **La guía de la prueba de hoy no tiene número de versión.**
- **`ponerNota` no recalcula la nota de la etapa**, y `GET /prueba/notas` exige `ajustar_nota`
  aunque su comentario diga otra cosa.
- **El insumo de la prueba manda los minutos de la plantilla**, no los efectivos.
- **Un comentario de `ServicioPruebaImpl` (390-396)** dice que el agente pasa a «por confirmar»
  aunque no haya criterios de IA, y `AgentePruebaPuesto` sale antes sin hacerlo. Con criterios
  solo de persona, el paso lo tiene que dar quien complete la rúbrica (punto 8).
- **Hoy el vencimiento entrega siempre** (`entregarVencidos` → `cerrarIntento`), falte lo que
  falte, y lo manda a calificar. La prueba nueva necesita la rama «no completada», y el
  ranking de hoy pinta «Prueba incompleta» en vez de sacarla. El cuestionario técnico ya exige
  responderlo todo al entregar (`entregarTecnico`).
- **Columnas del banco en el Perfil Integral**: calcular `CalificacionPorPuntos` fila a fila son
  unas cinco consultas por persona. Va en bloque.
- **Panel**: `FormularioPregunta` siempre pide puntos (113); `CambiarLosPuntos` suma solo
  preguntas (222); `CalificarCriterioAMano` limita al total del criterio; `RespuestasDePrueba`
  solo pinta texto; `MiPrueba.preguntas` no trae opciones y `responderPrueba` solo manda texto;
  `OPCION_MULTIPLE` ya significa dos cosas en el portal (`Formatos.tsx` 125, `bancoV3.ts` 46).
- **Muchas pruebas fijan los rótulos y el instrumento actuales**: `Vacante.test.tsx`,
  `CierreDePrueba.test.tsx`, `EditarVacante.test.tsx`, `ArchivarVacante.test.tsx`,
  `EliminarVacante.test.tsx` y `CamposDeLaVacante.test.tsx`.

### Restricciones de compatibilidad

- **RF-53**: puntos, claves, criterios y calificadores nunca viajan al portal.
- **RF-138**: el candidato queda atado a la versión con que empezó a rendir.
- **RF-147**: la IA no toca las cerradas.
- **Una vacante, una versión**, desde la primera rendición, con las dos excepciones de la fase 1:
  instrucciones de la IA y puntos, que cambian la vara para todos a la vez.

## Verificación

- **Actor, rol y datos**: un usuario con `editar_vacante` y `ajustar_nota` de una empresa que no
  sea la plataforma; otro solo con `ver_vacantes`; una cuenta de candidato; dos vacantes nuevas de
  la misma empresa y una de otra; una vacante existente con `PLANTILLA`.
- **Recorridos E2E repetibles**:
  1. Crear una vacante nueva, ver el bloque sin desplegables, armar la prueba con caso, tiempo,
     dos entregables y tres criterios (mixto de IA, solo cerradas, de persona), intentar publicar
     con faltas, leer la lista, publicar la prueba y la vacante.
  2. Postular, pasar a la etapa técnica, rendir con reloj, intentar entregar con una pregunta
     sin responder (no deja), responder los cuatro tipos (la escala en móvil), subir los
     entregables y entregar.
  3. Con la IA simulada, ver en la ficha el criterio de IA calificado y el de persona pendiente,
     calificarlo a mano y ver aparecer la nota de la etapa y el paso a «por confirmar».
  4. Copiar la prueba de la otra vacante, con su vista previa.
  5. Con candidatos calificados, cambiar los puntos y después la guía, y ver el ranking
     recalculado y «Recalificando».
  6. Otro candidato deja vencer el tiempo con una pregunta sin responder: no sale en el
     ranking, aparece en «No completaron la prueba» y se cierra su proceso desde ahí.
  7. La vacante existente con `PLANTILLA` rinde y califica como hoy.
  
  La IA se prueba con el agente simulado, no contra DeepSeek.
- **Riesgos que QA explora con navegador**: el reloj con preguntas de opción; el vencimiento con
  huecos; una entrega ilegible; quitar un entregable que miran dos criterios; la frontera de la
  primera rendición; un criterio mixto pendiente en ranking y Excel; dos criterios con el mismo
  nombre en dos vacantes; la vacante existente sin cambios.
- **Comprobación personal**: el editor en modo prueba, la ficha y los rankings. Hay inicio de
  sesión de por medio y **esa comprobación la hace el usuario**; se avisa ahora y no al entregar.
- **Avisos para quien verifique**: `verify` corre las pruebas de integración y `test` no; la
  suite E2E trae fallos de fondo por datos y el rojo se compara contra una corrida de referencia.

## Dependencias y dudas

- **Requiere**: la fase 1 en producción (lo está desde el 01/10/2026).
- **Decisiones pendientes**: ninguna.
