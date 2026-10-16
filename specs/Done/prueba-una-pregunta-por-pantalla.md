# La prueba técnica y la evaluación, una pregunta por pantalla con el mismo diseño
Estado: TERMINADO

## Objetivo

En el portal, el candidato vive dos experiencias distintas en el mismo proceso:

- **La evaluación del Perfil Integral** (las preguntas del banco de la vacante) sale **una pregunta
  por pantalla**, con «Anterior» y «Siguiente», un mapa con todas las preguntas, «Te faltan N por
  terminar» y «Siguiente sin responder».
- **La prueba técnica del editor** sale **toda en vertical**: el caso a un lado, todas las preguntas
  seguidas y los entregables debajo.

Esta spec lleva las dos al **mismo diseño**, el de la maqueta aprobada por el usuario el 06/10/2026.
La prueba técnica pasa a una pregunta por pantalla con mapa, y la evaluación adopta el mismo aspecto
y las mismas piezas. Así el candidato se mueve igual en las dos y no se pierde en una prueba larga.
Solo cambia el portal.

## Contexto

### Comportamiento actual

- **La evaluación** (`src/paginas/evaluacion/Evaluacion.tsx`, `Formatos.tsx`, `bancoV3.ts`,
  `useColaDeRespuestas.ts`):
  - **Navegación:** una pregunta por pantalla, con vuelta atrás libre.
  - **Mapa:** marca tres estados (`lista`, `a-medias` = «sin terminar», `vacia` = «sin
    responder»), que se distinguen por la forma y no solo por el color: lista rellena de tinta,
    a medias con media casilla horizontal, sin responder con borde fino, y la actual con anillo
    índigo. En escritorio va a la derecha y empieza cerrado.
  - **Ayudas para moverse:** «Siguiente sin responder», que da la vuelta al llegar al final;
    «Volver a la N», que sale después de un salto y recuerda **el primer** salto (a propósito);
    «Ir al final»; «Te faltan N por terminar»; y una barra de progreso con «N de M respondidas».
  - **Plazo:** enseña su plazo en días y, por debajo de una hora, un aviso gris con la cuenta
    atrás («Queda poco plazo: hh:mm:ss»).
  - **Formatos:** los ocho del banco del modelo y los cuatro de las preguntas propias (abierta,
    opción única, opción múltiple y escala), cada uno con su forma de responder. Hoy no se
    enseña el nombre del tipo.
  - **Entregar:** solo en la última pregunta, con una ventana que cuenta lo que falta («Faltan N
    preguntas») y deja «Entregar» desactivado hasta completarlo.
  - **Guardado:** lo escrito no sale de su cola (`useColaDeRespuestas`) hasta que el servidor lo
    confirma. Esa cola la comparte el cuestionario técnico.
  - **Al vencer:** la evaluación queda vencida y la postulación se cierra por plazo vencido; no se
    entrega nada. El aviso de plazo dice lo contrario: «Cuando se acabe, se entregará lo que
    tengas respondido».
- **La prueba** (`src/paginas/prueba/Prueba.tsx`, `PreguntaCerrada.tsx`), en su rama del editor
  (`delEditor`), ya con lo de `specs/Done/editor-de-la-prueba-mas-simple.md` (#113 y #75):
  - **Antes de empezar:** una pantalla previa con la fecha límite, el tiempo, las preguntas y los
    archivos, los materiales y las herramientas, «Empezar prueba» y el diálogo «¿Empezar ahora?».
  - **Después:** el reloj arriba, el caso en un lateral a la izquierda (solo si existe), todas las
    preguntas seguidas con el archivo de cada una dentro de ella, y debajo los entregables
    generales y «Entregar».
  - **El tiempo:** el reloj (`src/ui/Cronometro.tsx`, compartido con el cuestionario técnico) se
    pone rojo por debajo de 10 minutos. Sin cronómetro enseña igualmente una cuenta atrás y «hasta
    viernes, 10 de octubre · 23:59» en la zona del navegador; la pantalla previa usa
    `fechaLargaDeLima` («viernes 10/10 a las 23:59»).
  - **Guardado:** cada pregunta guarda por su cuenta mientras está en pantalla, con su propio
    reintento, y al desmontarse hace un último envío. Lo que falta para entregar lo saben las
    preguntas montadas. **No usa `useColaDeRespuestas`.**
  - **Archivos:** «Seleccionar archivo» (o «Guardar enlace» si el formato lo admite); una vez
    recibido, «Entregado» y «Recibimos «X». Si envías otra cosa, reemplaza a esta.».
  - **Entregar con huecos:** un aviso en la página dice qué falta («Para entregar te falta
    responder 2 preguntas (la 3 y la 7). Te falta subir «Flujo de caja.xlsx» (pregunta 4),
    «Informe final».»), con «Ir a la pregunta N» o «Ir a los entregables», y desplaza la página.
  - **Al vencer:** el backend la entrega sola si está todo listo o la cierra «sin completar»
    (cada 60 s). El portal enseña el tiempo agotado hasta que pasa ese barrido y después una
    pantalla cerrada, sin preguntas.
- **El backend** ya manda todas las preguntas de una vez y acepta guardar cualquiera en cualquier
  orden, en las dos pantallas. No hace falta tocarlo.

### Pantallas o procesos afectados

- **Portal, la prueba técnica** de una vacante con prueba del editor, desde «Empezar» hasta que se
  entrega o vence.
- **Portal, la evaluación del Perfil Integral**, desde que empieza hasta que se entrega o vence.

### Referencias

- **La maqueta aprobada:** https://claude.ai/artifact/Wy6L3HaAZdKh8abuaB9tc1, grupo «Portal del
  candidato · una pregunta por pantalla». Tiene tres pantallas:
  - «Portal · la prueba, una pregunta por pantalla (escritorio)», interactiva;
  - «Portal · móvil, una pregunta»;
  - «Portal · móvil, el mapa abierto».
- `specs/Done/editor-de-la-prueba-mas-simple.md`.
- `specs/la-prueba-al-instante-y-avisos-de-etapa.md`: lo que pasa al entregar la evaluación y el
  formato de la fecha límite.
- `DESIGN.md` del repositorio RenaserOsPostulantes (el `DESIGN.md` de este repositorio es el de
  RENASER OS, abandonado).

## Alcance

**Incluye:**

- **Un mismo marco para las dos pantallas**, con un componente común y no dos copias:
  - la barra fija;
  - el mapa con la leyenda;
  - «Siguiente sin responder» y «Volver a la N»;
  - la tarjeta de la pregunta con «Guardado», «Anterior» y «Siguiente»;
  - en móvil, el mapa en una hoja que sale desde abajo.
- **La prueba técnica del editor**, una pregunta por pantalla con ese marco, con el caso siempre a
  mano y los entregables generales como paso final.
- **El guardado de la prueba pasa a `useColaDeRespuestas`**, la cola de la evaluación, con sus dos
  llamadas (`responderPrueba` y `responderPruebaCerrada`). Con una pregunta por pantalla, las que
  no se ven tienen que seguir reintentando y contar en lo que falta. Es la parte más cara de esta
  spec.
- **La evaluación del Perfil Integral**, con ese marco.
- **«Entregar»** en las dos con el mismo aviso: dice qué falta y lleva a la pregunta o paso que
  falta. La evaluación deja su ventana de recuento y pierde «Ir al final».
- **El aviso de plazo de la evaluación**, que hoy promete entregar lo respondido y es falso.

**Queda fuera:**

- La pantalla previa y lo que hace falta para entregar la prueba, que lo deja
  `specs/Done/editor-de-la-prueba-mas-simple.md`. En la evaluación, lo que pide para entregar, su
  plazo y su aviso de empezar no cambian. Lo que pasa después de entregarla lo fija
  `specs/la-prueba-al-instante-y-avisos-de-etapa.md`.
- Lo que pasa al vencer, en las dos: sigue como hoy. No hay recorrido en solo lectura.
- Las formas de responder: los doce formatos de la evaluación (`Formatos.tsx`) y los cuatro tipos
  de la prueba siguen con sus componentes. Solo cambia el marco que los rodea.
- Las pruebas de plantilla (`PLANTILLA`) y el cuestionario técnico
  (`src/paginas/cuestionario/CuestionarioTecnico.tsx`): siguen como hoy. El cuestionario comparte
  `useColaDeRespuestas` y `Cronometro`; si se tocan, no debe notarlo.
- El backend, la API y el panel de la empresa: el editor donde se arma la prueba no cambia.

## Comportamiento esperado

### El marco común (las dos pantallas)

1. **La barra fija**, arriba y siempre a la vista:
   - **El tiempo:** en la prueba, el reloj si es cronometrada, o «Hasta el viernes 10/10 a las
     23:59» si no (con `fechaLargaDeLima`, en hora de Lima: el formato de la pantalla previa y el
     de `specs/la-prueba-al-instante-y-avisos-de-etapa.md`); en la evaluación, su plazo como hoy
     («Quedan 5 días», o la cuenta atrás por debajo de una hora). Se pone **en rojo** cuando queda
     poco: la prueba por debajo de 10 minutos, como el reloj de hoy; la evaluación por debajo de
     una hora.
   - **Dónde estás:** «Pregunta 3 de 8» (o el nombre del paso).
   - **Lo que falta:** «Te faltan N por terminar», o «Todo listo para entregar». La evaluación
     conserva además su barra de progreso con «N de M respondidas».
   - **Los botones:**
     - «Ver las N» u «Ocultar el mapa»;
     - «Siguiente sin responder»;
     - «Volver a la N», solo después de un salto;
     - «Entregar…» con el nombre de cada pantalla.
2. **El mapa**:
   - **La lista:** una casilla por pregunta, con su número.
   - **Los estados**, que se distinguen por la forma y no solo por el color:
     - **lista**: rellena de índigo;
     - **a medias**: media casilla en diagonal;
     - **sin responder**: borde punteado.
   - **La pregunta actual** lleva un anillo oscuro alrededor.
   - **Debajo, la leyenda:** «Lista · A medias · Sin responder».
   - **Cambia a propósito respecto a la evaluación de hoy** (lista en tinta, actual con anillo
     índigo, a medias horizontal, sin responder con borde fino) y se aparta de «la regla de la voz
     única» de DESIGN.md: lo decidió el usuario el 07/10/2026, siguiendo la maqueta.
   - **Por pantalla:** en escritorio va a la izquierda, a la vista al entrar, y se puede ocultar
     (hoy, en la evaluación, va a la derecha y cerrado). En móvil, «Ver las N» lo abre como una
     hoja desde abajo, con «Te faltan N por terminar» y casillas grandes, y al elegir una se cierra
     y lleva a ella. La hoja aparece sin animación: dentro de la prueba nada se mueve («la regla
     del reloj» de DESIGN.md).
3. **La pregunta** va en una tarjeta:
   - arriba, su etiqueta («Pregunta 3 · Opción múltiple») y «Guardado». El nombre del tipo es nuevo
     en el portal: Abierta, Opción única, Opción múltiple y Escala, en la prueba y en las preguntas
     propias de la evaluación; los formatos del banco del modelo llevan solo «Pregunta 3»;
   - el enunciado grande, que recibe el foco al cambiar de pregunta;
   - su forma de responder;
   - abajo, «Anterior» con contorno y «Siguiente» relleno en índigo. En la evaluación cambia: hoy
     va en tinta, y se corrige el comentario de `Evaluacion.module.css` que lo justificaba.
4. **«Siguiente sin responder»** va a la siguiente que no esté lista y da la vuelta al llegar al
   final. Después de un salto aparece «Volver a la N», que recuerda **el primer** salto, como hoy
   la evaluación (así se van tapando huecos sin perder el sitio del que se salió), y se olvida en
   cuanto se navega a mano con «Anterior» o «Siguiente». «Ir al final» desaparece: lo cubren el
   mapa y «Siguiente sin responder».
5. **Si recarga**, el candidato vuelve a la pregunta donde estaba (se guarda en el navegador por
   intento o evaluación). Si no hay dato, va a la primera sin responder.
6. **Las respuestas se guardan solas** con `useColaDeRespuestas`, que no suelta nada hasta que el
   servidor lo confirma, en las dos pantallas. La prueba deja de guardar pregunta por pregunta: lo
   pendiente se reintenta aunque su pregunta no esté en pantalla, y el mapa y lo que falta salen de
   las respuestas de todas las preguntas, no de las montadas. Si un guardado falla, la pregunta
   sale **a medias** en el mapa con «No se pudo guardar; se reintenta» y nada se pierde.
7. **«Entregar» con huecos** no entrega, y es igual en las dos:
   - un aviso en la página dice exactamente qué falta, con el texto de hoy de la prueba (por
     ejemplo, «Para entregar te falta responder 2 preguntas (la 3 y la 7). Te falta subir «Flujo
     de caja.xlsx» (pregunta 4), «Informe final».»); en la evaluación, solo la parte de las
     preguntas;
   - lleva un botón que va a lo primero que falta («Ir a la pregunta N» o «Ir a los
     entregables»), moviendo el navegador de preguntas y no la página.

   Con todo listo, pide confirmación y entrega. La evaluación deja su ventana de «Faltan N
   preguntas».

   Nunca hay a la vez dos botones con el mismo nombre accesible (por ejemplo, «Entregar la prueba»
   en la barra y en el último paso): se oculta uno o se nombran distinto. Si no, los `getByRole`
   de los E2E fallan.
8. **Accesibilidad**:
   - las casillas, «Anterior» y «Siguiente» son botones de verdad;
   - cada casilla dice su número y estado, por ejemplo «Pregunta 4, a medias, falta su archivo
     (estás aquí)»;
   - controles de 44 px;
   - sin atajos de teclado globales.

### Solo la prueba técnica

9. **Al pulsar «Empezar»** se ve la pregunta 1 con el marco común.
10. **«A medias»** significa respondida pero sin su archivo obligatorio, o pendiente de guardar.
11. **El archivo de una pregunta** va dentro de su tarjeta: «Archivo de esta pregunta: … ·
    obligatorio», con los textos de hoy: «Seleccionar archivo» (o «Guardar enlace» si admite
    enlace) y, una vez recibido, «Entregado» y «Recibimos «X». Si envías otra cosa, reemplaza a
    esta.». Si la pregunta está respondida y falta el archivo, el recuadro va en ámbar.
12. **El lateral de la derecha** tiene tres formas según lo que tenga la prueba:
    - **Con caso:** se titula «El caso». Lleva el enunciado, el PDF, los materiales y las
      herramientas, y, si hay entregables generales, la lista «Por entregar».
    - **Sin caso, pero con materiales, herramientas o entregables generales:** se titula **«Para
      esta prueba»**. Lleva los materiales, las herramientas y «Por entregar», y no ocupa el sitio de
      ningún caso.
    - **Sin caso y sin nada de eso:** no hay lateral ni columna. La pregunta ocupa el centro con un
      ancho cómodo de lectura (unos 760 px como máximo), centrada junto al mapa.

    En escritorio el lateral va a la derecha, con «Ocultar». Oculto, un botón de la barra lo devuelve
    («Ver el caso» o «Para esta prueba»). En móvil es una barra plegable encima de la pregunta, con
    el mismo título.
13. **«Por entregar»** es solo de consulta:
    - cada entregable general lleva una casilla que se marca cuando ya está subido, su estado
      («obligatorio, pendiente» o «subido») y «Ir →», que lleva al paso «Entregables»;
    - desde ahí no se sube nada, así que solo hay un sitio para subir cada archivo.
14. **Los entregables generales**, si los hay:
    - son un paso **«Entregables»** después de la última pregunta, con su casilla al final del mapa;
    - «Siguiente» en la última pregunta dice «Siguiente: entregables»;
    - en ese paso, «Entregar la prueba» sustituye a «Siguiente».

    Sin generales no hay ese paso.
15. **Al vencer**, como hoy: con todo listo se entrega sola; con huecos queda «sin completar». El
    portal enseña el tiempo agotado hasta que pasa el barrido y después la pantalla cerrada de hoy
    (entregada o sin completar). No hay recorrido en solo lectura.

### Solo la evaluación

16. **«A medias»** es su estado «sin terminar» de hoy: un formato con varias partes que no está
    completo.
17. **No hay caso ni entregables.**
18. **«Entregar»** usa el aviso común del punto 7. Lo que exige para entregar, su plazo, su aviso
    al empezar y lo que pasa al vencer (la postulación se cierra por plazo vencido) siguen como
    hoy; lo que pasa después de entregar es lo que fije
    `specs/la-prueba-al-instante-y-avisos-de-etapa.md`.
19. **El aviso de plazo** deja de prometer lo que no pasa. En lugar de «Cuando se acabe, se
    entregará lo que tengas respondido» dirá, por ejemplo, «Cuando se acabe, la evaluación se
    cierra sin entregarse. Entrégala antes».

## Referencia visual

La maqueta es la referencia: https://claude.ai/artifact/Wy6L3HaAZdKh8abuaB9tc1. En la evaluación
se usa el mismo marco, sin la columna del caso ni el paso de entregables.

### Escritorio

```
Administrador(a) de empresas · Fecha límite: viernes 10/10, 23:59
Prueba técnica
┌ (fija) ⏱ 54:12  Pregunta 3 de 8  Te faltan 7 por terminar ─────────────────────────────┐
│        [Ocultar el mapa] [Siguiente sin responder] [Volver a la 6] [Entregar la prueba] │
└──────────────────────────────────────────────────────────────────────────────────────────┘
┌ Las 8 preguntas ┐ ┌ Pregunta 3 · Opción múltiple ─── ✓ Guardado ┐ ┌ El caso   Ocultar ┐
│ ■1 ■2 ⦿3 ◩4     │ │ ¿Qué usarías para consolidar las ventas      │ │ Distribuidora…    │
│ □5 □6 □7 □8     │ │ por mes?                                     │ │ 📎 consigna.pdf    │
│ [□ Entregables] │ │  ☐ SUMAR.SI.CONJUNTO                          │ │ Materiales: …     │
│ ■ Lista         │ │  ☐ BUSCARV  ☐ Tabla dinámica  ☐ CONCATENAR    │ └───────────────────┘
│ ◩ A medias      │ │ ─────────────────────────────────────────────│
│ □ Sin responder │ │ [ Anterior ]                    [ Siguiente ] │
└─────────────────┘ └──────────────────────────────────────────────┘
```

### Móvil

```
⏱ 54:12  4 de 8  Te faltan 7          ┌ Las 8 preguntas            ✕ ┐
[Ver las 8] [Siguiente sin responder]  │ Te faltan 7 por terminar.     │
[ Ver el caso                     ▸ ]  │ ■1 ■2 □3 ◩4                   │
┌ Pregunta 4 · Abierta    Guardado ┐   │ □5 □6 □7 □8                   │
│ Arma el flujo de caja de abril…  │   │ [□ Entregables]               │
│ [ tu respuesta …               ] │   │ ■ Lista ◩ A medias □ Sin resp.│
│ ┆Archivo de esta pregunta: …   ┆ │   └───────────────────────────────┘
│ [Anterior]          [Siguiente]  │          (hoja que sale desde abajo)
└──────────────────────────────────┘
[ Entregar la prueba ]
```

### Estados a dibujar

- **Pregunta:** lista, a medias (sin archivo o sin terminar) y sin responder; la actual con su
  anillo.
- **Mapa:** con «Entregables» y sin él; oculto y visible; la hoja de móvil.
- **Lateral de la derecha:** «El caso», «Para esta prueba» y sin lateral (pregunta centrada); oculto y
  vuelto a abrir.
- **Navegación:** «Volver a la N» después de un salto.
- **Guardado:** uno que falla y se reintenta.
- **Entregar:** con faltas y su botón; la confirmación; la pantalla de entregado.
- **Tiempo:** que se acaba (rojo) y vencido (la pantalla cerrada de hoy).
- **Datos largos:** 60 preguntas en el mapa y un caso de 10000 caracteres en su lateral.

### Reglas visuales

- **Las de la maqueta y el `DESIGN.md` del portal:**
  - «Siguiente» relleno en índigo; «Anterior» y los botones de la barra con contorno;
  - «Entregar…» con borde oscuro en la barra, y relleno solo en el último paso;
  - el estado del mapa por la forma: lista rellena de índigo y la actual anillada en oscuro. Se
    aparta a propósito de «la regla de la voz única» (decisión del usuario del 07/10/2026);
  - rojo solo para el tiempo que se acaba, como el reloj de hoy; ámbar solo para lo que frena la
    entrega (el archivo que falta);
  - nada se anima dentro de la prueba ni de la evaluación;
  - controles de 44 px.
- **Componentes, no copias:** el marco común se saca a componentes que usan las dos pantallas. La
  evaluación ya tiene buena parte de la lógica (mapa, «Siguiente sin responder», «Volver a la N» y
  la cola de respuestas), y se reutiliza.

## Criterios de aceptación

- **AC-01**: Dada una prueba del editor de 8 preguntas, cuando el candidato pulsa «Empezar», entonces
  ve solo la pregunta 1 con la barra fija, el mapa, «Anterior» y «Siguiente».
- **AC-02**: Dada la evaluación del Perfil Integral, entonces se ve con el mismo marco que la prueba:
  la misma barra, el mismo mapa con su leyenda y la misma tarjeta de pregunta, sin caso ni
  entregables.
- **AC-03**: Dado el mapa de cualquiera de las dos, entonces cada casilla enseña su estado por la
  forma (lista, a medias, sin responder), la actual va anillada, su `aria-label` dice número y
  estado, y pulsarla lleva a esa pregunta.
- **AC-04**: Dada una pregunta de la prueba respondida con su archivo obligatorio sin subir, entonces
  el mapa la marca a medias, su recuadro de archivo va en ámbar y la línea de lo que falta la
  cuenta.
- **AC-05**: Dadas las preguntas 2 y 6 sin responder y estando en la 3, cuando se pulsa «Siguiente
  sin responder», entonces va a la 6 y aparece «Volver a la 3»; otra vez, da la vuelta a la 2 y
  sigue «Volver a la 3», porque se recuerda el primer salto. Al pulsar «Anterior» o «Siguiente»,
  desaparece. Igual en las dos pantallas.
- **AC-06**: Dada una prueba con caso, entonces el lateral «El caso» se oculta y vuelve con «Ver el
  caso» desde cualquier pregunta. Sin caso pero con materiales, herramientas o entregables
  generales, el lateral se titula «Para esta prueba». Sin caso ni nada de eso, no hay lateral y la
  pregunta va centrada con un ancho de lectura.
- **AC-07**: Dada una prueba con entregables generales, entonces hay un paso «Entregables» después
  de la última pregunta y una casilla en el mapa; sin generales, ni paso ni casilla.
- **AC-08**: Dada una respuesta escrita, cuando se pulsa «Siguiente» y el guardado falla, entonces no
  se pierde, se reintenta, y el mapa la marca a medias hasta que el servidor la confirma. Igual en
  las dos pantallas.
- **AC-09**: Dado un candidato en la pregunta 5 de cualquiera de las dos, cuando recarga, entonces
  vuelve a la 5.
- **AC-10**: Dadas la 3 y la 7 sin responder y «Informe final» sin subir, cuando se pulsa «Entregar
  la prueba», entonces no entrega, el aviso dice «Para entregar te falta responder 2 preguntas (la 3
  y la 7). Te falta subir «Informe final».» y su botón lleva a la 3. En la evaluación, el mismo
  aviso con sus preguntas, sin ventana de recuento.
- **AC-11**: Dado el tiempo de la prueba por debajo de 10 minutos, o el plazo de la evaluación por
  debajo de una hora, entonces su indicador de la barra va en rojo. Al vencer, cada una hace lo de
  hoy: la prueba enseña su pantalla cerrada y la evaluación cierra la postulación. El aviso de plazo
  de la evaluación ya no promete entregar lo respondido.
- **AC-12**: Dado un móvil, entonces el mapa se abre sin animación como hoja desde abajo y se cierra
  al elegir, y el caso es una barra plegable, sin desplazamiento horizontal.
- **AC-13**: Dada una prueba de plantilla o un cuestionario técnico, entonces se ven y guardan como
  hoy.
- **AC-14**: Dada la evaluación, entonces sus doce formatos, lo que exige para entregar y su plazo
  funcionan como hoy. Sus pruebas siguen comprobando lo mismo, salvo la entrega con huecos, que pasa
  de la ventana al aviso.
- **AC-15**: Dado «Por entregar» en el lateral, entonces cada entregable general dice si está subido
  o pendiente, «Ir →» lleva al paso «Entregables» y desde el lateral no se puede subir nada.
- **AC-16**: Dada una prueba con respuestas guardadas en una sesión anterior, cuando el candidato
  vuelve y solo ve la pregunta 1, entonces el mapa y lo que falta cuentan como listas las que ya
  respondió, aunque no las haya visitado en esta sesión.

## Casos límite y errores

- **Una sola pregunta y sin entregables generales**: el mapa puede seguir saliendo, y «Siguiente» se
  convierte en «Entregar…», sin repetir el nombre del de la barra (punto 7).
- **Pregunta añadida en otra versión**: el candidato está atado a su versión (RF-138); el mapa cuenta
  las de su versión.
- **Dos pestañas abiertas**: la posición es de cada pestaña; las respuestas, del servidor.
- **Navegador sin almacenamiento** (modo privado o bloqueado): al recargar va a la primera sin
  responder.
- **Sin red al cambiar de pregunta**: se cambia igual; lo escrito queda en la cola y se reintenta.
- **Doble pulsación en «Siguiente»**: avanza una sola pregunta.
- **Evaluación de 60 preguntas en móvil**: la hoja del mapa se desplaza por dentro y no tapa la barra.

## Datos y compatibilidad

- **Sin cambios en el backend, la base ni la API.** La posición se guarda solo en el navegador, por
  intento o evaluación, y es una comodidad: si se pierde, se va a la primera sin responder.
- **RF-53**: al portal no llega nada nuevo (ni puntos, ni criterios, ni lo que mira cada uno).
- **Pruebas E2E** que dan por hecha la pantalla de hoy y se ajustan:
  - **prueba del editor:**
    - 48: el recorrido 2 da por hechas las cuatro preguntas a la vista y cuenta los «Guardado.», así
      que cambia su forma y no solo los selectores; el 6 solo mira la pantalla cerrada;
    - 49 (H-01), 52 (recorrido 5) y 53 (AC-20);
  - **evaluación:**
    - 32 y 47 (este comprueba «6 de 6 respondidas»);
    - el 54 de `specs/la-prueba-al-instante-y-avisos-de-etapa.md`, que pulsa «Entregar
      evaluación» y el diálogo «Entregar»;
  - **sin cambios (plantilla):** 31, la parte de prueba del 32 y el recorrido 7 del 48;
  - **pruebas unitarias:** `Evaluacion.test.tsx`, `PruebaDelEditor.test.tsx`, `Prueba.test.tsx` y
    `useColaDeRespuestas.test.ts`;
  - **las nuevas:** con el siguiente número libre tras los de las specs en curso (hoy hay dos 54 y
    un 55).

## Verificación

- **Actor y datos**:
  - una cuenta de candidato;
  - una vacante con prueba del editor con caso, 8 preguntas de los cuatro tipos, una con archivo
    obligatorio, y un entregable general obligatorio;
  - otra vacante sin caso ni generales;
  - una vacante con preguntas propias del Perfil Integral;
  - una vacante con el banco del modelo, porque los formatos de varias partes (SJT-R, SEC, CD,
    EF-4, V) solo existen ahí;
  - una vacante con plantilla.
- **Recorridos E2E repetibles**:
  1. **Prueba, navegación:** empezar, responder 1 y 2, saltar con el mapa a la 6 y volver con
     «Volver a la N».
  2. **Prueba, entrega:** dejar huecos, pulsar «Entregar la prueba», seguir el botón hasta lo primero
     que falta, completarlo todo y entregar.
  3. **Recarga:** recargar en la 5, en la prueba y en la evaluación, y seguir en la 5.
  4. **El caso:** abrirlo y ocultarlo desde una pregunta; en la vacante sin caso, que no salga.
  5. **Vencimiento de la prueba:** dejar vencer el tiempo con un hueco, que queda «sin completar» y
     enseña la pantalla cerrada.
  6. **Evaluación:**
     - responder con el marco nuevo;
     - dejar una de varias partes a medias y ver la casilla a medias;
     - «Siguiente sin responder»;
     - pulsar «Entregar» con huecos, seguir el botón del aviso, completar y entregar.
  7. **Sin cambios:** la vacante con plantilla, igual que hoy.
- **Riesgos que QA explora con navegador**:
  - el guardado al cambiar rápido de pregunta, y que una pregunta que ya no está en pantalla siga
    reintentando;
  - el mapa con 60 preguntas en móvil;
  - el foco y el lector de pantalla al cambiar de pregunta;
  - el reloj al llegar a cero en mitad de un cambio de pregunta;
  - que las dos pantallas se vean iguales.
- **Comprobación personal:** la prueba y la evaluación en el portal con una cuenta de candidato. Hay
  inicio de sesión de por medio y **esa comprobación la hace el usuario**; se avisa ahora y no al
  entregar.

## Dependencias y dudas

- **Requiere:**
  - `specs/Done/editor-de-la-prueba-mas-simple.md`: ya fusionada (#113 y #75).
  - `specs/la-prueba-al-instante-y-avisos-de-etapa.md` fusionada. Cambia lo que pasa al entregar la
    evaluación (`Evaluacion.tsx`), añade pruebas a `Evaluacion.test.tsx`, y su E2E 54 recorre la
    evaluación con los nombres de botón que esta spec cambia. Esta spec hereda su comportamiento al
    entregar y su formato de fecha. Hay que empezar después, o sincronizar antes de verificar.
  - Con `specs/reparto-y-avisos-de-los-puntos.md` no choca: solo toca el panel de la empresa.
- **Decidido por el usuario el 07/10/2026:**
  - el mapa y «Siguiente», como en la maqueta, aunque el mapa se aparte de DESIGN.md;
  - al vencer, como hoy;
  - «Volver a la N» recuerda el primer salto;
  - un solo patrón para entregar, el aviso de la prueba;
  - el tiempo que se acaba, en rojo;
  - la barra de progreso «N de M respondidas» de la evaluación se queda (el E2E 47 la sigue
    comprobando);
  - el aviso del minuto final del reloj («Lo que esté guardado se entrega solo») no se toca en esta
    spec, aunque en la prueba del editor con huecos no sea cierto.
- **Decisiones pendientes:** ninguna.

## Decisiones de la revisión

Lo que el usuario decidió al revisar la implementación, del 09 al 10/10/2026. Se suma a lo de
arriba; donde lo contradice, manda esto.

### Revisión del 09/10/2026

- **Fuera los mensajes «Se guarda sola…»** de las dos pantallas. Se mantiene el aviso de
  caracteres cuando el texto se acerca al límite.
- **El ámbar del archivo obligatorio** (punto 11) solo aparece **después de intentar entregar**.
- **El caso se lee en una vista de lectura** (`ElCaso.tsx`): el lateral enseña el comienzo,
  recortado, y «Leer el caso completo» abre una ventana ancha, de unos 70 caracteres por línea, con
  el PDF. Un caso corto que cabe entero va entero y sin botón. Los enlaces del extracto recortado
  no reciben Tab.
- **`TextoPlano` gana dos props**: `enlacesTabulables` y `listas`. Con `listas`, las líneas que
  empiezan por «- » (o «• », «* ») se pintan como `ul` y las que empiezan por «1. », «2. »… como
  `ol`.

### Revisión del 10/10/2026: nada antes del reloj

Vale para la prueba del editor y para la de plantilla, y **cambia el «sin cambios en el backend ni
en la API»** de «Datos y compatibilidad»:

- **Antes de pulsar «Empezar» el backend no envía** las preguntas ni sus opciones, el caso o
  enunciado, los materiales, la consigna en PDF ni el `detalle` de los entregables.
- **Sí envía** el estado, el tiempo y la modalidad, la fecha límite o el plazo, `totalPreguntas`,
  las herramientas permitidas y los entregables con su nombre, si son obligatorios, su formato y
  `posicionDeLaPregunta`.
- Todo lo demás llega en la respuesta de iniciar y en las lecturas posteriores
  (`ServicioPruebaImpl` con `empezada(intento)`; `DtosPrueba` con `antesDeEmpezar()` y
  `sinDetalle()`).
- **`{{enlacePrueba}}` va siempre vacía** en el correo de la prueba. Solo la usaba el texto viejo
  de la `V29`; el vigente, `PRUEBA_DISPONIBLE` v3, no la lleva.
- **La pantalla previa es una ficha corta** (`FichaPrevia.tsx`), la misma para las dos pruebas:
  los datos en una fila (2×2 en el teléfono), la condición junto a «Empezar prueba», «Lo que
  entregarás» sin detalle y las herramientas.
- **Los textos de la entradilla, la condición y el diálogo se arman con lo que la prueba tiene**
  (`loQueDiceLaPrevia.ts`). La entradilla dice «El contenido y las preguntas se abren al empezar,
  y desde ahí corre el tiempo». La de plantilla mantiene «Si el tiempo termina, se entrega lo que
  hayas guardado.».

### Revisión del 10/10/2026: la doble pulsación

- **`Modal` gana `aPruebaDeDobleClic`.** Con ella ignora el puntero que continúa la serie que abrió
  el modal (`detail > 1`) y toda pulsación que empiece en los primeros 500 ms tras abrir, con un
  escudo transparente de 550 ms. El teclado y Escape funcionan al instante.
- La llevan «¿Empezar ahora?», «Entregar» (plantilla, editor y evaluación) y la ventana de lectura
  del caso.
- **El foco inicial de «¿Empezar ahora?» va a «Aún no».** Un doble clic en «Sí, empezar» manda
  una sola petición `POST /inicio`.
- **Al ocultar o mostrar el lateral del caso**, el foco va a «Ver el caso» o a «Ocultar».
- Dentro del marco, `useUnaSolaPulsacion` descarta la segunda pulsación de una serie que cae sobre
  otra cosa que la primera, para que no marque una opción de la pregunta nueva.

### Quedó fuera

Anotado en el `docs/PENDIENTES.md` del frontend: a 360×640 a «Empezar prueba» le faltan 18 px; al
cerrar «Entregar la evaluación» el foco cae en `<body>` (ya pasaba); en el teléfono la ventana de
lectura tapa el reloj; el ámbar del archivo vuelve a neutro al recargar; todos los enlaces a PDF
dentro del caso se llaman «Abrir el enunciado de la prueba (PDF)»; los correos enviados antes del
03/10/2026 llevan el enlace largo de la consigna, que todavía abre el PDF sin empezar; y en una base
recién migrada el texto de la `V29` deja «DESCARGA AQUI EL ENUNCIADO:» con una línea vacía.
