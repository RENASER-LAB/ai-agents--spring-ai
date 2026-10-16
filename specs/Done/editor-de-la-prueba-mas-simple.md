# Un editor de la prueba técnica más simple
Estado: TERMINADO

## Objetivo

El editor de la prueba técnica (`/admin/vacantes/:id/prueba`, fase 2, PR #110/#71, en producción
desde el 02/10/2026) cumple, pero quien arma la prueba lo encuentra poco intuitivo y con pasos de
más:

- **Pedir un archivo para una pregunta** lleva cuatro pasos: crear el entregable arriba, abrir el
  lápiz del criterio, marcar su casilla en «Mira» y guardar. Si se olvida el tercero, sale «No está
  en ningún criterio: así no se publica» sin decir cómo se arregla.
- **El caso obliga**: con un solo entregable, el enunciado pasa a ser obligatorio aunque la prueba
  no gire en torno a un escenario.
- **El tiempo, los materiales y las herramientas** ocupan el centro de la pantalla aunque se tocan
  una vez.
- **La fecha en que cierra la prueba** está en otra pantalla (la vacante) y solo se puede poner
  después de publicar la prueba; sin ella, una prueba cronometrada se puede empezar semanas más
  tarde.

Esta spec reordena el editor, quita la obligación del caso, hace que los archivos se pidan donde
se usan y que el sistema deduzca qué mira cada criterio, mueve el tiempo y la fecha límite a un
panel de configuración y hace obligatoria esa fecha. La forma de calificar no cambia.

Las tres mejoras de comodidad (criterios plegables, faltas que llevan a donde se arreglan y
cabecera fija con la barra de progreso) se llevan también al **editor del banco de preguntas**
(las preguntas propias del Perfil Integral, `/admin/vacantes/:id/preguntas`), para que los dos
editores se vean y se usen igual. En el banco solo cambia la interfaz.

## Contexto

### Comportamiento actual

- **El editor** (`EditorDeLaPrueba.tsx`) pinta, en este orden: el caso (enunciado, PDF o Word,
  materiales y herramientas), el tiempo (cronometrada en minutos o plazo abierto en días), los
  entregables, la guía para la IA y los criterios con sus preguntas. Con cero entregables la
  cabecera dice «cuestionario».
- **Los criterios** (`CriterioDePrueba.tsx`) se editan con el lápiz: nombre, «Qué evalúa», la
  parte calificada (puntos y quién la califica) y «Mira», una casilla por entregable. Lo que mira
  se guarda en `criterio_banco_entregable` (V67).
- **Las reglas de publicación** (`ReglasDeLaPrueba`) exigen, entre otras:
  - el enunciado si hay entregables (línea 147);
  - que todo entregable esté en algún criterio (líneas 185-187);
  - los días de un plazo abierto (líneas 82-94 y 212-220).
- **Las recomendaciones por IA** (`RecetaRecomendacionPrueba`) proponen el caso cuando hay
  entregables (línea 77) y, por criterio, qué entregables mira.
- **La fecha de cierre** vive en la vacante: `vacante.prueba_cierra_en`, el bloque plegable «Plazos
  de la prueba» de `Vacante.tsx` (4061-4093) con `CierreDeLaVacante` y
  `POST /vacantes/{id}/cierre-prueba` (fecha y motivo obligatorio, auditado). Para una prueba del
  editor solo se puede fijar **después de publicarla** (`definirCierrePrueba`, «publícala antes»).
  Mover la fecha mueve también los intentos abiertos, salvo los de plazo propio.
- **El plazo de una persona** («El plazo de esta persona», `PlazoDeUnaPersona`) está en su ficha y
  manda sobre la fecha de la vacante.
- **Al abrir la prueba** (`ServicioPruebaImpl.iniciarDelEditor`), el intento vence en la fecha de
  la vacante si la hay. Si es cronometrada y los minutos se acaban antes, vence por el reloj. **Si
  hay fecha, los días del plazo abierto no se usan**; solo cuentan cuando no hay fecha.
- **El portal** (`src/paginas/prueba/Prueba.tsx`) pinta todos los entregables juntos, en su propio
  bloque, y el caso como «El encargo» o «De qué va».
- Bajo «Subir PDF o Word», el editor dice «Va también en el correo que avisa de la prueba». Ya no es
  cierto: el aviso de la prueba no lleva el enlace desde el 03/10/2026.

### Pantallas o procesos afectados

- **Panel**: el editor de la prueba; el bloque «Plazos de la prueba» de la vacante; la pestaña
  «Prueba» de la ficha del candidato (cómo enseña los entregables); y, solo en la interfaz, el
  editor del banco (`EditorDePreguntas.tsx` con `BloqueCriterio.tsx`).
  - Hoy su cabecera enseña «N/100 · avisos» y la lista «Lo que frena la publicación».
  - Sus criterios salen siempre desplegados.
- **Portal**: la prueba (pantalla previa, preguntas y entrega).
- **Backend**: entregables y su alcance, qué mira cada criterio, reglas de publicación, fecha de
  cierre, recomendaciones por IA y copia de otra vacante.

### Referencias

- Maqueta navegable, con el panel interactivo y el portal en móvil:
  https://claude.ai/artifact/Wy6L3HaAZdKh8abuaB9tc1
- `specs/Done/prueba-del-puesto-con-el-editor.md` (fase 2): reglas, decisiones y calificación.
- `specs/pesos-con-candidatos-y-fin-del-prestamo.md`, Parte C: deja de fabricar el enlace del PDF
  del enunciado.
- [DESIGN.md del panel](../../../RenaserOsPostulantes/DESIGN.md).

### Decisiones tomadas (05/10/2026)

1. **No hay tipo que elegir.** No hay «caso» ni «cuestionario»: una sola prueba. El nombre
   «cuestionario» desaparece del editor, de la vacante y del portal.
2. **El escenario es opcional**, aunque haya entregables, y va arriba, plegado.
3. **Los archivos se piden donde se usan**: dentro de una pregunta o como entregable general que
   cubre toda la prueba o algunas preguntas.
4. **«Mira» lo deduce el sistema**; nadie lo marca a mano.
5. **El tiempo, los materiales, las herramientas y la fecha límite** van a un panel de
   configuración. **La guía para la IA se queda en la página.**
6. **La fecha límite es obligatoria para publicar** y sale de la vacante.
7. **Se quitan los días del plazo abierto**: quedan «Cronometrada» y «Sin cronómetro».
8. **Comodidad**: criterios plegables, faltas que llevan a donde se arreglan y cabecera fija con
   la barra de progreso.
9. **Las mismas mejoras de comodidad en el editor del banco**, solo en la interfaz. Sus reglas, sus
   datos, su cálculo y su API no cambian.

## Alcance

**Incluye:**

- El nuevo orden y aspecto del editor, con todo lo de la referencia visual.
- El alcance de los entregables (de una pregunta o generales) y «Mira» deducido.
- La configuración con la fecha límite y su paso desde la vacante.
- Las reglas de publicación nuevas, en el servidor y en el panel.
- El portal: pantalla previa, archivos dentro de la pregunta y entregables generales al final.
- La ficha del candidato: los entregables de pregunta junto a su pregunta.
- Las recomendaciones por IA y la copia de otra vacante con el modelo nuevo.
- Quitar el texto «Va también en el correo que avisa de la prueba».
- En el editor del banco: los criterios plegables, las faltas clicables y la cabecera fija con la
  barra de progreso (punto 19).

**Queda fuera:**

- La lógica del banco: sus reglas de publicación, sus datos, cómo califica y su API.

- La calificación: lo que recibe la IA por criterio, la red de seguridad, el ajuste a mano y la
  recalificación no cambian. Solo cambia de dónde sale la lista de lo que mira cada criterio.
- Las vacantes con `PLANTILLA` o `CUESTIONARIO_TECNICO`: siguen con su bloque «Plazos de la
  prueba» y sus pantallas de siempre.
- Editar en la propia tarjeta con autoguardado, duplicar, arrastrar para reordenar y elegir el tipo
  antes de agregar una pregunta (propuestos y descartados por ahora).
- El plazo propio de una persona: se queda en su ficha, igual que hoy.

## Comportamiento esperado

1. **El orden del editor** es: cabecera fija → «Escenario o caso práctico (opcional)» → «Criterios
   y preguntas» → «Guía de calificación para la IA» → «Entregables generales (opcional)». No hay
   selector de tipo ni la palabra «cuestionario».
2. **La cabecera fija** se queda arriba al bajar y enseña:
   - el estado (BORRADOR o PUBLICADA), el balance «80 de 100 pts» y el resumen «3 criterios ·
     5 preguntas · 2 entregables»;
   - el chip del tiempo, «90 min · hasta vie 10/10, 23:59» o «Sin cronómetro · hasta …». En ámbar
     si faltan los minutos o la fecha;
   - los botones «Configuración» y «Publicar la prueba»;
   - una barra de progreso hasta 100, con un tramo por criterio en tonos distintos de índigo y lo
     que falta en gris; al pasar el cursor, cada tramo dice su criterio y sus puntos;
   - las faltas como botones ámbar con flecha, que llevan a donde se arreglan:
     - los puntos, a los criterios;
     - un criterio, a ese criterio abierto;
     - los minutos o la fecha, a la configuración abierta con el cursor en el campo.
3. **El escenario o caso práctico** sale plegado, con «Agregar un caso».
   - Al abrirlo: el enunciado (hasta 10000 caracteres) y el PDF o Word opcional.
   - «Quitar el caso» con algo escrito pide confirmación («Se borran el enunciado y el PDF. Las
     preguntas y los entregables se quedan como están») y solo borra eso.
   - Un caso abierto sin enunciado ni PDF no se guarda: al volver, sale plegado.
   - **Nunca es obligatorio**, haya o no entregables.
4. **Los criterios son plegables.**
   - Plegado, cada uno ocupa una línea: «▸ Análisis financiero · 30 pts (sistema 5 + IA 25) ·
     2 preguntas · 1 archivo».
   - Si le falta algo, el borde y un punto se ponen en ámbar, con la falta escrita al lado.
   - «Desplegar todo» y «Plegar todo» sobre la lista.
   - Al entrar, todo está plegado salvo el criterio recién creado y los que tienen una falta.
   - Desplegado: «Qué evalúa», «Parte calificada», «Mira» (con el chip «automático») y sus
     preguntas.
5. **El formulario del criterio** (lápiz) pide nombre, «Qué evalúa» y la parte calificada (puntos
   y quién la califica). **Ya no tiene «Mira».**
6. **Pedir un archivo para una pregunta**: cada pregunta tiene «Pedir un archivo para esta
   pregunta».
   - Abre, dentro de la tarjeta, el nombre, el formato (archivo, enlace o los dos), si es
     obligatorio y su «Qué debe tener una buena entrega» (opcional, hasta 1000 caracteres).
   - Una pregunta pide como mucho un archivo. Se quita con ✕.
   - Si se elige «Enlace» y el criterio de la pregunta lo califica la IA, sale en el momento el
     aviso «La IA no abre enlaces: este criterio lo califica la IA, así que solo leerá la respuesta
     escrita».
7. **Los entregables generales** son un archivo que reúne respuestas de varias preguntas o el
   resultado del caso.
   - Llevan lo mismo que el de una pregunta, más «Cubre»: «Toda la prueba» o «Estas preguntas»
     (casillas con el número de cada pregunta).
   - Sin ninguno, la sección dice «Ninguno. Úsalo solo si un archivo reúne varias respuestas».
8. **«Mira» se deduce**: un criterio mira el archivo de cada una de sus preguntas y los
   entregables generales que cubren toda la prueba o alguna de sus preguntas. Se recalcula al
   cambiar preguntas, criterios o entregables, sin que nadie lo marque.
9. **La configuración** se abre en un panel lateral desde «Configuración».
   - **Tiempo**: «Cronometrada [N] min», de 5 minutos en adelante, o «Sin cronómetro».
   - **Fecha límite para dar la prueba**: fecha y hora, sin paréntesis ni textos de ayuda.
   - Materiales y herramientas permitidas (opcionales, con los límites de hoy).
   - Junto a «Tiempo» y a la fecha va un botón «!» en un círculo. Al pasar el cursor, al llegar
     con el teclado o al tocarlo, enseña la guía:
     - **Tiempo**: «Cronometrada: tiene esos minutos desde que abre la prueba. Si la abre tarde,
       el reloj se corta en la fecha límite. Sin cronómetro: puede trabajar en ella hasta la fecha
       límite».
     - **Fecha**: «El día y la hora en que la prueba se cierra para todos: después nadie puede
       entregar. Es de esta convocatoria y no se copia con la prueba. Para dar más tiempo a una
       persona, se hace desde su ficha».
   - Si la fecha está vacía, bajo el campo: «Obligatoria para publicar la prueba».
10. **La fecha límite es la de la vacante** (`prueba_cierra_en`): se edita desde el editor y no
    viaja al copiar la prueba.
    - **Se puede poner antes de publicar la prueba.**
    - No se acepta una fecha pasada, como hoy.
    - Cambiarla mueve los intentos abiertos que no tienen plazo propio, como hoy.
    - Con la prueba publicada y alguien ya en la etapa técnica, cambiarla pide un motivo, que queda
      en la auditoría. Antes de eso no lo pide.
    - En las vacantes con prueba del editor desaparece el bloque «Plazos de la prueba» de la
      vacante. En las que rinden una plantilla sigue igual.
11. **El reloj al abrir** la prueba:
    - **Cronometrada**: vence a los N minutos o en la fecha límite, lo que llegue antes.
    - **Sin cronómetro**: vence en la fecha límite.
    - El plazo propio de una persona sigue mandando, como hoy.
12. **Publicar la prueba** exige, y el servidor devuelve la lista entera de faltas:
    - que todo sume exactamente 100;
    - las reglas por tipo de pregunta y de criterio de la fase 2 (calificador, parte calificada que
      mira algo, criterio de IA que no mire solo enlaces sin abiertas);
    - que todo entregable lo mire al menos un criterio con parte calificada mayor que 0. Un general
      que cubre preguntas cuyos criterios no tienen parte calificada no lo califica nadie;
    - que un entregable general cubra toda la prueba o al menos una pregunta;
    - los minutos, si es cronometrada;
    - **la fecha límite**, futura.

    **Ya no exige** el enunciado ni los días.
13. **La guía para la IA** se queda en la página. Bajo su título dice: «Vale para todo lo que
    califica la IA en esta prueba: las abiertas y los entregables de los criterios de IA. No toca
    las cerradas ni los criterios que califica una persona».
14. **El candidato**, en el portal:
    - **Pantalla previa** (antes de «Empezar»):
      - la fecha límite («Fecha límite: viernes 10/10 a las 23:59»);
      - el tiempo («Tendrás 90 minutos desde que pulses Empezar» o «Puedes trabajar en ella hasta
        la fecha límite»);
      - cuántas preguntas y cuántos archivos hay que subir;
      - los materiales y las herramientas si los hay, aunque no haya caso.
    - **El caso** sale solo si existe, con su PDF.
    - **El archivo de una pregunta** se sube dentro de esa pregunta, «Archivo de esta pregunta:
      Flujo de caja.xlsx · obligatorio».
    - **Los entregables generales** van en «Entregables», al final, antes de «Entregar».
    - **«Entregar»** sigue exigiendo todas las respuestas y los archivos obligatorios, dice qué
      falta y lleva a lo primero que falta.
    - Nunca le llegan puntos, criterios, calificadores ni qué mira cada criterio (RF-53).
15. **La ficha del candidato** (pestaña «Prueba») enseña el archivo de una pregunta junto a su
    respuesta, y los generales en su sitio. «Mira» de cada criterio sale de la misma regla.
16. **Recomendaciones por IA**: proponen preguntas, criterios, caso opcional y entregables con su
    alcance (de una pregunta o generales con «Cubre»). Nunca proponen «Mira». Se quita la regla «con
    entregables hace falta caso». Las validaciones nuevas del punto 12 se aplican a la propuesta,
    con su reintento.
17. **Copiar de otra vacante** copia el caso, el tiempo, los materiales, las herramientas y los
    entregables con su alcance. La fecha límite no se copia. La vista previa enseña el alcance de
    cada entregable.
18. **Sin `editar_vacante`**: el editor y la configuración se ven en solo lectura, sin botones que
    acaben en 403. Plegar, desplegar y las guías «!» sí funcionan.
19. **El editor del banco** (`/admin/vacantes/:id/preguntas`) gana lo mismo que el de la prueba en
    comodidad, con el mismo aspecto y las mismas piezas:
    - **Cabecera fija** con:
      - el estado y el balance;
      - la barra de progreso hasta 100 por criterio;
      - «Publicar las preguntas»;
      - sus avisos de hoy convertidos en botones que llevan a donde se arreglan:
        - los puntos, a los criterios;
        - un criterio, a ese criterio abierto;
        - una pregunta, a su criterio abierto y a esa pregunta.
    - **Criterios plegables**:
      - plegado, cada uno en una línea con nombre, puntos, desglose y número de preguntas;
      - un punto ámbar si le falta algo;
      - «Desplegar todo» y «Plegar todo»;
      - al entrar, plegados salvo el recién creado y los que tienen una falta.
    - **No gana** la configuración, la fecha límite, el caso, los entregables ni «Mira», que no
      existen en el banco.
    - **Su guía para la IA y sus minutos** se quedan donde están.
    - **Lo demás no cambia**: lo que exige para publicar, los textos de los avisos, la
      recalificación, el cambio de puntos y la copia.

## Referencia visual

La maqueta es la referencia: https://claude.ai/artifact/Wy6L3HaAZdKh8abuaB9tc1. Los textos de
esta spec mandan sobre los de la maqueta.

### El editor (escritorio)

```
← La vacante                                          Ver como candidato →
Administrador(a) de empresas
Prueba técnica
┌ (fija al bajar) ────────────────────────────────────────────────────────────┐
│ BORRADOR  80 de 100 pts  3 criterios · 5 preguntas · 2 entregables          │
│ ⏱ 90 min · hasta vie 10/10, 23:59          [⚙ Configuración] [Publicar]    │
│ ███████████▌▌▌▌▌▌▌▌▌▌██████████░░░░░░░░                                      │
│ [Faltan 20 puntos →]                                                         │
└──────────────────────────────────────────────────────────────────────────────┘
Escenario o caso práctico (opcional)                        [+ Agregar un caso]
Criterios y preguntas              [Recomendaciones por IA] [Copiar de otra vacante]
  Desplegar todo · Plegar todo
  ▸ Análisis financiero · 30 pts (sistema 5 + IA 25) · 2 preguntas            ✎
  ▾ Manejo de Excel · 30 pts (sistema 10 + IA 10) · 2 preguntas · 1 archivo  ✎
      Qué evalúa: Arma reportes y flujos de caja con fórmulas.
      Parte calificada: 10 pts · la califica la IA
      Mira [automático] Flujo de caja.xlsx (pregunta 4) · Informe final (general)
      ┌ Pregunta 4 · Abierta ──────────────────────────────────────── ✎ ┐
      │ Arma un flujo de caja mensual para una tienda pequeña.           │
      │ 📎 Flujo de caja.xlsx · archivo · obligatorio                  ✕ │
      └──────────────────────────────────────────────────────────────────┘
  ▸ Comunicación con gerencia · 20 pts (persona 20) · 1 pregunta            ✎
  [+ Agregar criterio]
Guía de calificación para la IA (opcional · 0 de 2000)
Entregables generales (opcional)
  📎 Informe final · archivo · obligatorio · Cubre: toda la prueba          ✕
  [+ Agregar entregable general]
```

### La configuración (panel lateral)

```
Configuración de la prueba                                   ✕
Tiempo (!)
  (•) Cronometrada [ 90 ] min
  ( ) Sin cronómetro
Fecha límite para dar la prueba (!)
  [ 10/10/2026 23:59 ]
Materiales (opcional)            [ … ]
Herramientas permitidas (opcional) [ … ]
                                              [ Listo ]
```

### Estados a dibujar

- **Editor vacío**: sin criterios, el caso plegado y faltas de puntos y fecha.
- **Criterio con falta**: borde y punto ámbar plegado.
- **Formulario de archivo** con el aviso de enlace en un criterio de IA.
- **Guía «!» abierta** en el tiempo y en la fecha.
- **Publicada**: solo lectura salvo puntos, instrucciones de la IA y fecha, como en la fase 2.
- **Portal**: pantalla previa con y sin caso; pregunta con su archivo; «Entregar» con faltas.
- **Datos largos**: un caso de 10000 caracteres, 12 criterios plegados y un general que cubre
  20 preguntas.
- **El editor del banco**: plegado, desplegado y con un criterio con falta, con la misma cabecera
  fija. La maqueta solo dibuja el de la prueba; el banco sigue el mismo patrón, sin configuración
  ni entregables.

### Móvil y escritorio

- En móvil, la cabecera fija se compacta: estado, balance y «Publicar» en una línea, con el chip y
  las faltas debajo, que se pueden desplazar.
- El panel de configuración ocupa toda la pantalla en móvil.
- Las casillas de «Cubre» se acomodan en varias filas.

### Reglas visuales

Las del `DESIGN.md` del panel:
- una acción principal (Publicar);
- ámbar solo para lo que frena la publicación;
- chips neutros para tipo, puntos y calificador;
- controles de 44 px;
- las guías «!» con el foco visible.

## Criterios de aceptación

- **AC-01**: Dado el editor de una vacante nueva, entonces el orden es caso, criterios, guía y
  entregables generales; no hay selector de tipo ni la palabra «cuestionario» en el editor, la
  vacante ni el portal.
- **AC-02**: Dada una prueba con un entregable y sin enunciado, cuando se publica con todo lo
  demás en regla, entonces se publica.
- **AC-03**: Dado un caso abierto, cuando se pulsa «Quitar el caso» con texto, entonces pide
  confirmación y solo borra el enunciado y el PDF; las preguntas y los entregables siguen.
- **AC-04**: Dada una pregunta abierta de un criterio, cuando se pide un archivo desde la pregunta,
  entonces el criterio lo mira sin tocar nada más y el formulario del criterio no tiene «Mira».
- **AC-05**: Dado un entregable general que cubre las preguntas 2 y 4, de criterios distintos,
  entonces los dos criterios lo miran; si cubre toda la prueba, lo miran todos.
- **AC-06**: Dado un entregable general que cubre solo preguntas de criterios sin parte calificada,
  cuando se publica, entonces 400 con «nadie lo califica».
- **AC-07**: Dada una pregunta de un criterio de IA, cuando se le pide un archivo «Enlace», entonces
  sale el aviso en el momento, y publicar sigue rechazando un criterio de IA que solo mire enlaces
  sin abiertas.
- **AC-08**: Dada una prueba sin fecha límite, cuando se intenta publicar, entonces 400 con la
  falta, el panel no dice «Todo listo» y la falta abre la configuración con el cursor en la fecha.
- **AC-09**: Dado un borrador sin publicar, cuando se pone la fecha límite desde la configuración,
  entonces se guarda en la vacante sin pedir motivo.
- **AC-10**: Dada una prueba publicada con alguien en la etapa técnica, cuando se cambia la fecha,
  entonces pide motivo, lo audita y mueve los intentos abiertos sin plazo propio.
- **AC-11**: Dada una prueba cronometrada de 90 minutos con fecha límite, cuando alguien la abre
  30 minutos antes de la fecha, entonces vence en la fecha; si la abre con un día de margen, vence
  a los 90 minutos.
- **AC-12**: Dada una prueba «Sin cronómetro», cuando alguien la abre, entonces vence en la fecha
  límite, y el editor no tiene ningún campo de días.
- **AC-13**: Dada una vacante con prueba del editor, entonces su configuración no tiene el bloque
  «Plazos de la prueba»; una vacante con `PLANTILLA` lo sigue teniendo y funciona igual.
- **AC-14**: Dada la vacante A con fecha límite, cuando la vacante B copia su prueba, entonces B
  trae los entregables con su alcance y no trae la fecha.
- **AC-15**: Dado el editor con criterios, entonces al entrar están plegados (salvo los que tienen
  falta), cada línea dice puntos, desglose, preguntas y archivos, y «Desplegar todo» y «Plegar
  todo» funcionan.
- **AC-16**: Dada cualquier falta de la cabecera, cuando se pulsa, entonces lleva a donde se arregla
  (criterios, el criterio, el caso o la configuración con el campo enfocado).
- **AC-17**: Dada una página larga, cuando se baja, entonces la cabecera sigue a la vista con el
  balance, la barra por criterio, el chip del tiempo y «Publicar».
- **AC-18**: Dado el botón «!» del tiempo o de la fecha, cuando se pasa el cursor, se llega con el
  teclado o se toca, entonces enseña su guía, y desaparece al salir.
- **AC-19**: Dado un candidato sin caso en su prueba, cuando abre la pantalla previa, entonces ve la
  fecha límite, el tiempo, cuántas preguntas y archivos hay, y los materiales y herramientas si los
  hay; no ve ningún bloque de caso.
- **AC-20**: Dada una pregunta con archivo obligatorio, cuando el candidato pulsa «Entregar» sin
  subirlo, entonces no entrega, dice qué falta y lleva a esa pregunta; el servidor también lo
  rechaza. La respuesta de la API no trae puntos, criterios ni qué mira cada uno.
- **AC-21**: Dada una recomendación de la IA, entonces propone entregables con alcance y nunca
  «Mira», y una propuesta que deje un entregable sin nadie que lo califique, dos veces seguidas,
  queda fallida.
- **AC-22**: Dada una prueba ya publicada antes de esta spec, con «Mira» marcado a mano y
  candidatos calificados, entonces sus notas, su ficha y su ranking no cambian.
- **AC-23**: Dado un usuario sin `editar_vacante`, entonces ve el editor y la configuración en solo
  lectura, sin botones que acaben en 403, y puede plegar, desplegar y leer las guías.
- **AC-24**: Dado el adjunto del caso, entonces bajo «Subir PDF o Word» ya no dice «Va también en
  el correo que avisa de la prueba».
- **AC-25**: Dado el editor del banco con criterios, entonces al entrar están plegados (salvo los
  que tienen falta), cada línea dice puntos, desglose y preguntas, «Desplegar todo» y «Plegar todo»
  funcionan, la cabecera sigue a la vista al bajar con la barra por criterio, y cada aviso lleva a
  donde se arregla.
- **AC-26**: Dado el editor del banco, entonces lo que exige para publicar, sus datos, su cálculo y
  su API no cambian: sus pruebas de integración y su E2E (`46-preguntas-propias-qa`) pasan sin
  tocar lo que comprueban, solo los selectores de pantalla que cambien.

## Casos límite y errores

- **Datos inválidos**: los de la fase 2, más un entregable general sin «Cubre», una pregunta con
  dos archivos, unos minutos por debajo de 5 y una fecha pasada. Todos dan 400 con la lista entera.
- **Quitar una pregunta** que pedía un archivo quita también ese archivo. Si era la única que
  cubría un general, el general queda sin cubrir y sale como falta.
- **Mover una pregunta de criterio** mueve con ella su archivo y lo que mira cada criterio.
- **Después de la primera rendición**: el contenido queda congelado como en la fase 2, también el
  alcance de los entregables. La fecha, los puntos y las instrucciones de la IA sí se cambian.
- **Doble envío**: publicar dos veces no crea dos versiones; dos cambios de fecha seguidos dejan el
  último; pedir un archivo dos veces en la misma pregunta no crea dos.
- **Fecha sin prueba publicada** que vence: no pasa nada hasta que haya intentos; el barrido de
  vencidos solo mira intentos abiertos.
- **Permisos**: editar, publicar, copiar, recomendar y fijar la fecha exigen `editar_vacante`; ver
  exige `ver_vacantes`; descargar entregables, `descargar_entregables`.

## Datos y compatibilidad

### Qué hay que guardar

La forma la decide quien implemente, sin fijar el número de migración: main va por la V67 y hay
specs en paralelo. Ver la memoria de colisiones de migraciones.

- **El alcance de cada entregable** de la prueba del editor: de una pregunta, como mucho uno por
  pregunta, o general, que cubre toda la prueba o una lista de preguntas de la misma versión.
- **Lo que mira cada criterio** se sigue pudiendo leer como hoy, de modo que la calificación, la
  ficha y la red de seguridad no cambien. Puede seguir en `criterio_banco_entregable` si lo escribe
  el sistema a partir del alcance en vez de la persona, o calcularse al leer. En los dos casos debe
  ser siempre coherente con el alcance.
- **La modalidad «Sin cronómetro»** puede reutilizar `PLAZO_ABIERTO` con `plazo_dias` vacío. Lo
  importante es que el editor no pida días y que el reloj siga el punto 11.
- **La fecha límite** sigue en `vacante.prueba_cierra_en`.

### Registros existentes

- **Versiones ya publicadas o con candidatos**: conservan su «Mira» tal cual, y sus notas no se
  recalculan por esta spec.
- **Borrador abierto desde una de ellas, o copia de ella**: sus entregables pasan a generales que
  cubren las preguntas de los criterios que los miraban. Si alguno de esos criterios no tenía
  preguntas, el general cubre toda la prueba.
- **Pruebas del editor publicadas sin fecha** antes de esta spec: siguen funcionando como hoy (los
  días cuentan desde que se abre). La fecha se les exige al publicar otra versión, y se les puede
  poner desde la configuración.
- **Las vacantes con `PLANTILLA`** no cambian.

### Compatibilidad

- **RF-53**: puntos, criterios, calificadores y «Mira» nunca viajan al portal.
- **RF-138**: el candidato queda atado a la versión con que empezó.
- **Una vacante, una versión** desde la primera rendición, con las excepciones de la fase 2
  (puntos e instrucciones de la IA) y la fecha límite, que es de la vacante.

## Verificación

- **Actor, rol y datos**:
  - un usuario con `editar_vacante` y otro solo con `ver_vacantes`;
  - una cuenta de candidato;
  - dos vacantes nuevas de la misma empresa;
  - una vacante con `PLANTILLA`;
  - una prueba del editor publicada antes de esta spec, con «Mira» marcado y candidatos
    calificados.
- **Recorridos E2E repetibles**:
  1. Armar una prueba sin caso:
     - tres criterios;
     - un archivo pedido desde una pregunta;
     - un general que cubre dos preguntas;
     - intentar publicar sin fecha y seguir la falta hasta la configuración;
     - poner la fecha y publicar.
  2. Ver que «Mira» de cada criterio sale solo y que el formulario del criterio no lo tiene.
  3. Abrir el caso, escribirlo, quitarlo con confirmación; la prueba sigue publicable.
  4. Plegar y desplegar criterios; seguir cada falta de la cabecera.
  5. Candidato:
     - ve la pantalla previa sin caso;
     - sube el archivo dentro de la pregunta;
     - intenta entregar sin el general obligatorio (no deja);
     - lo sube y entrega.
  6. Copiar la prueba a otra vacante: trae los alcances y no la fecha.
  7. La prueba publicada antes de esta spec: notas, ficha y ranking iguales.
  8. La vacante con `PLANTILLA`: su bloque «Plazos de la prueba» funciona como hoy.
  9. El editor del banco:
     - plegar y desplegar criterios;
     - seguir un aviso hasta su pregunta;
     - publicar como hoy.

  La IA se prueba con el agente simulado.
- **Riesgos que QA explora con navegador**:
  - la cabecera fija en móvil;
  - la guía «!» con teclado y al tocarla;
  - quitar una pregunta que pedía un archivo;
  - la fecha en la zona horaria de Lima;
  - un general que deja de estar cubierto;
  - la frontera de la primera rendición con el alcance de los entregables.
- **Comprobación personal**: el editor, la configuración y el portal. Hay inicio de sesión de por
  medio y **esa comprobación la hace el usuario**; se avisa ahora y no al entregar.
- **Avisos para quien verifique**:
  - `verify` corre las pruebas de integración y `test` no;
  - la suite E2E trae fallos de fondo por datos, así que el rojo se compara contra una corrida de
    referencia;
  - SonarCloud exige en lo nuevo cobertura ≥ 80 % (líneas y ramas) y duplicación ≤ 3 %.

## Dependencias y dudas

- **Requiere**: la fase 2 en producción (lo está desde el 02/10/2026).
- **Se cruza con**:
  - `specs/pesos-con-candidatos-y-fin-del-prestamo.md`, Parte C (el enlace del PDF): da igual cuál
    vaya antes, pero las dos tocan el adjunto del caso.
  - `specs/pruebas-modelo.md`, que dice «una prueba con entregables necesita un caso»: hay que
    actualizarla. Un modelo no tiene vacante, así que no tiene fecha límite: la fecha se exige al
    publicar la prueba de la vacante, no el modelo.
- **Decisiones pendientes**: ninguna.
