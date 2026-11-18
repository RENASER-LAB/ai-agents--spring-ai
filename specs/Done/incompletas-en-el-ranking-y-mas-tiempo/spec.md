# Las pruebas incompletas, dentro del ranking y con «Darle más tiempo»
Estado: TERMINADO

Escrita el 21/10/2026. Revisada el 18/11/2026 contra `origin/main`: backend `f365b43` (#143,
migraciones hasta la V85) y frontend `7124173` (#108, panel en Northstar desde el 14/11/2026).
Decisiones de producto aprobadas por el usuario el 23/10/2026 y el 18/11/2026; la maqueta se
rehízo el 18/11/2026 sobre el panel actual. Pendiente de autorización para implementar.

## Objetivo

Cuando a alguien se le acaba el tiempo de la prueba del puesto con algo sin responder o sin
subir, hoy desaparece de la tabla. Su nombre queda en una lista plegada debajo, y lo único que se
puede hacer con él es cerrar su proceso. Si la persona tuvo un problema y el equipo quiere dejarle
terminar, no hay forma de hacerlo.

Esta spec hace tres cosas:

- **Las devuelve a la tabla, solo en «Toda la tanda»**, con la etiqueta «No la completó» y sin
  nota.
- **Añade un filtro por el estado de la prueba** para verlas solas.
- **Añade en la ficha «Darle más tiempo»**: se fija una fecha nueva y se escribe un motivo
  obligatorio, y la persona sigue con lo que ya había respondido. Se le avisa por correo, campana
  y WhatsApp.

Es para el equipo de selección. El candidato solo nota que, si le dan más tiempo, recibe un aviso
y puede volver a entrar a su prueba.

## Flujo principal

1. A un candidato se le acaba el tiempo con respuestas o archivos pendientes, o se le pasa la
   fecha sin abrir la prueba. Queda como «No la completó», sin calificarse.
2. El equipo puede encontrarlo en «Prueba del puesto» → «Toda la tanda», buscando por nombre
   o usando «Filtros» → «Prueba» → «No la completó». Ya no hay una lista aparte y no aparece en
   «Pendiente» ni en «Le toca al candidato».
3. Si el candidato contacta al equipo por un canal externo, por ejemplo WhatsApp, el equipo
   abre su ficha y revisa cuándo venció y qué le faltó. Ese contacto se gestiona fuera del sistema:
   **el bot de WhatsApp no puede dar más tiempo**. Sus puertas hacia EX solo registran vínculos,
   novedades y bajas, o leen el contexto; ninguna toca la prueba ni su plazo.
4. Si decide permitirle terminar, pulsa «Darle más tiempo», elige una fecha y hora límite futura
   (hora de Lima), escribe el motivo obligatorio y decide si avisarle.
   Se permite con el proceso abierto, aunque la vacante esté cerrada; no si la vacante está
   archivada o eliminada, ni sin permiso.
5. Al guardar queda la auditoría y la prueba vuelve a «En curso» o «Sin empezar». Conserva
   respuestas y archivos, vuelve a «Le toca al candidato» y deja de coincidir con el filtro
   «No la completó». La fecha nueva es propia de esa persona.
6. Con el aviso marcado, le llegan un correo, la campana del portal y, si tiene WhatsApp
   vinculado, un mensaje. Más tarde puede llegarle el recordatorio de «24 horas antes de que
   venza» con la fecha nueva.
7. El candidato entra en «Mis procesos» y continúa. Si completa y entrega, se califica; si llega
   el nuevo límite con todo respondido, se entrega sola y se califica. Si vuelve a vencer con
   huecos, regresa a «No la completó», visible solo en «Toda la tanda».

Dar más tiempo es una decisión puntual del equipo, no una tarea pendiente obligatoria por cada
prueba que no se completó. También se puede descartar con el flujo habitual.

## Contexto

### Comportamiento actual, en el recorrido

1. **Se le acaba el tiempo con huecos.** El barrido de cada minuto cierra la prueba como «no
   completada»: no se califica, no gasta IA y la postulación se queda en «le toca al candidato».
   El candidato ve «Tu tiempo terminó y la prueba quedó sin completar… Quien lleva tu proceso lo
   ve y te escribirá». El bot de WhatsApp, si le preguntan, la da por vencida.
2. **El equipo abre el ranking de «Prueba del puesto».** La persona no está en la tabla ni en el
   total. Debajo hay una lista plegada, «No completaron la prueba (N)», con el nombre, qué le
   faltó, la fecha y el botón «Cerrar su proceso», que es el descarte de siempre.
3. **El equipo abre su ficha.** Lee «Su tiempo terminó y la prueba quedó sin completar: … No
   tiene nota ni sale en el ranking; su proceso espera a que alguien lo cierre». Debajo ya se ven
   sus respuestas, las opciones que marcó y lo que alcanzó a subir, sin nota. **No se le puede
   dar más tiempo**: el bloque «El plazo de esta persona» dice «Entregó el … El plazo ya no
   cambia» —falso para quien no entregó— y el backend rechaza otra fecha («Esta prueba ya se
   entregó»). El informe en PDF de la prueba sí está disponible y dice «No la completó».
4. **El Excel no la lleva**, aunque el panel le pase su id. Además, su pie la cuenta como «no son
   de esta vacante o ya no se alcanzan», cosa que no es cierta.

### Dos casos que hoy salen mal

- **Quien nunca abrió la prueba y se le pasó la fecha.** El barrido solo cierra las pruebas ya
  empezadas, así que esta persona se queda para siempre en «le toca al candidato». No puede
  empezar («El tiempo para esta prueba ya se agotó»). En la tabla sale como «Prueba incompleta»,
  igual que quien la está haciendo, y no aparece en la lista de no completadas. Sí se le puede dar
  más tiempo con «El plazo de esta persona», pero nada avisa de que hace falta.
- **«Prueba incompleta» ya existe y significa otra cosa.** Hoy ese texto se pone a quien no ha
  empezado, a quien la está haciendo y a quien se le entregó sola al vencer. Si las que de verdad
  quedaron sin completar vuelven con ese mismo nombre, no se distinguirían de las que van por la
  mitad. Por eso esta spec usa «No la completó», que es lo que ya dice el informe en PDF.

### Lo técnico que hay debajo

Rutas del backend bajo `src/main/java/com/renaser/ai/ai_engine/`; las del panel, bajo
`Frontend-EX/src/`.

- **Barrido:** `prueba/service/impl/ServicioPruebaImpl.java`, `entregarVencidos` (387-436) y
  `cerrarSinCompletar` (962-969). Este último pone `entregado_en`, `es_entrega_automatica` y
  `no_completada`. La consulta `findByEntregadoEnIsNullAndIniciadoEnIsNotNullAndVenceEnBefore`
  (389) deja fuera las pruebas que nunca se abrieron. «No completada» solo existe en la prueba del
  editor (V67; CHECK `NOT no_completada OR entregado_en IS NOT NULL`). `exigirAbierto` (709-720)
  es lo que impide seguir respondiendo.
- **Ranking:** `perfilintegral/service/impl/ServicioPerfilIntegralPanelImpl.java` junta las «no
  completada» (566-570), las salta al armar las filas (694-697) y no las cuenta en `total`.
  También las quita del cálculo de la prueba del editor (927).
  - `prueba/service/EstadoPruebaDelPuesto.de` devuelve `CALIFICADA | PENDIENTE_CALIFICACION |
    INCOMPLETA | NO_APLICA`. `INCOMPLETA` junta «sin entregar» y «entregada sola».
  - El panel lo pinta en `LO_DE_LA_PRUEBA` y `loDeLaPrueba` (`panel/vacantes/ranking.ts`
    327-375) y en `porQueNoHayNotaCorto` (488), como texto pequeño bajo el «—» de la nota.
  - Los cortes los deciden `filtrar` (175) y `corteDondeSeVe` (188) solo con el estado de la
    postulación. `corteDondeSeVe` lo usa «Abrir ficha» del visor de informes.
- **Lista aparte:**
  - Endpoint `GET /vacantes/{id}/prueba-propia/no-completaron`
    (`prueba/controller/PruebaPropiaController.java` 202-208), servido por
    `ServicioCalificacionPruebaPropiaImpl.noCompletaron` (355-390).
  - En el panel, `panel/vacantes/NoCompletaron.tsx`, montado en `panel/vacantes/Vacante.tsx:838`.
- **Ficha de la prueba del editor:** `panel/vacantes/PruebaDelCandidato.tsx` (estado
  `NO_COMPLETADA`, 69-72), montado en `Vacante.tsx:2640` junto a `PlazoDeUnaPersona`. El
  backend arma ese estado en `ServicioCalificacionPruebaPropiaImpl.armar` (113-117).
- **El plazo de una persona:**
  - `POST /postulaciones/{id}/prueba/plazo` (`CalificacionPruebaController` 84,
    `mover_postulacion`, motivo obligatorio, auditoría `definir_plazo_prueba`) llega a
    `ServicioCalificacionPruebaImpl.definirPlazo` (540-572). Rechaza si hay `entregado_en`
    (546-548) o si la fecha ya pasó. Solo exige que la vacante no esté eliminada
    (`laQueSePuedeTocar`, 689-693): **no mira si está archivada**.
  - En el panel, `PlazoDeUnaPersona` (`panel/vacantes/CierreDePrueba.tsx` 623-863) deja de
    ofrecer el control con `entregadoEn !== null` (706).
- **Cortes del ranking:** «Pendiente» es `PRUEBA_POR_CONFIRMAR` y «Le toca al candidato» es
  `PRUEBA_TURNO_CANDIDATO` (`ranking.ts:83-84`).
- ⚠️ **«Avanzar» movería a una incompleta.** `confirmarAvance`
  (`postulacion/service/impl/ServicioPostulacionesPanelImpl.java:238`) calcula el siguiente
  momento, que desde `PRUEBA_TURNO_CANDIDATO` es `PRUEBA_CALIFICANDO`. Si una incompleta vuelve a
  la tabla y alguien la marca y pulsa «Avanzar», pasaría a «calificando» sin prueba entregada. Hoy
  no ocurre solo porque nadie puede marcarla. La barra ya dice «No avanzaron: …»
  (`Vacante.tsx:1248`).
- **Excel:** `perfilintegral/service/impl/ServicioExcelRankingImpl.generar` (145) usa las filas
  del ranking. Su pie (864-867) cuenta las ausentes como ajenas. La spec en borrador «El Excel del
  ranking, resumido» está aparcada: esta spec toca el Excel **actual**.
- **El reloj del portal ya cuenta hasta `venceEn`** (`paginas/prueba/PruebaDelEditor.tsx:425`),
  así que una fecha nueva se ve sin tocar el reloj.
- **Nada antes del reloj:** `ServicioPruebaImpl.empezada` (635) decide que no se envíe el caso,
  las preguntas ni la consigna hasta «Empezar» (PR #119/#80).
- **Informe en PDF de la prueba** (`informe/service/ArmadoDeLaPrueba.java`, #137/#138): con
  `no_completada` el informe está disponible y dice «No la completó» (105-115, 145, 371). Sin
  entrega y sin esa marca, da «sin calificar» y el botón sale apagado. Los textos de la IA llevan
  una huella (V84): si los datos cambian, el informe vuelve a la explicación de la IA hasta que se
  reescriban.
- **Recordatorios (V70):** `postulacion/service/ServicioRecordatorios.java`,
  `CalendarioDeRecordatorios.java` y la consulta de `postulacion/repository/TurnosParaRecordar.java`.
  Salen por correo y campana, de 8:00 a 21:00 de Lima: uno a las 24 h de que le toque
  (`TRAS_ENTRAR`) y otro 24 h antes de que venza (`ANTES_DEL_PLAZO`). **Solo para quien no ha
  abierto la prueba** (`ip.iniciado_en IS NULL AND ip.entregado_en IS NULL AND NOT
  ip.no_completada`). Se anotan en `recordatorio_enviado`: `TRAS_ENTRAR` una vez por transición;
  `ANTES_DEL_PLAZO` una vez por transición **y por fecha** (índice único sobre
  `transicion_estado_id, plazo_en`).
- **WhatsApp v2 (encendido desde el 09/11/2026):**
  - `whatsapp/service/AvisosPorWhatsapp.java` encola avisos solo para `PRUEBA_DISPONIBLE` y
    `POSTULACION_AVANZA`, ligados a una transición. La tabla `aviso_whatsapp` (V73) exige
    `transicion_estado_id NOT NULL`, CHECK `tipo IN ('PRUEBA_DISPONIBLE', 'POSTULACION_AVANZA')`
    y `UNIQUE (transicion_estado_id, tipo)`. Solo hay aviso con vínculo ACTIVO, número sin baja y
    la empresa dentro de la beta.
  - El bot (repositorio `Renaser/crm`, rama `new-chatbot-harness`) manda el texto si la ventana
    de 24 h está abierta. Fuera de ella solo puede usar una plantilla aprobada por Meta; hoy hay
    `prueba_disponible_postulante` y `avance_postulacion`. Sin plantilla, omite el aviso con
    motivo `sin_plantilla`.
  - El contexto que lee el bot (`whatsapp/service/LoQueLeToca.java`, 135-138 y 254-256) toma la
    prueba con `no_completada` como VENCIDA.
- **Cierre de la vacante (V78, #132):** `vacante/service/CierreDeLaVacante.java` cierra la
  vacante sola a las 23:59 de Lima de su fecha límite, y la prueba del puesto conserva su propia
  fecha. Por eso la prueba suele rendirse con la vacante ya CERRADA.

### Por qué no entra el Perfil Integral (ni el cuestionario ni la plantilla)

- **Perfil Integral (con el banco de RENASER o con preguntas propias):**
  - No tiene reloj y no se puede entregar a medias.
  - Desde la [V78](../perfil-integral-hasta-el-cierre-de-la-vacante/spec.md) ya no vence
    a los 14 días: dura hasta el cierre de la vacante, y quien va a medias sale en «Le toca al
    candidato». Al cerrarse la vacante, quien no terminó queda cerrado con sus avisos. El caso «se
    le acabó el tiempo y quiero dárselo» no existe ahí.
- **Cuestionario técnico (CAZATALENTOS):** al vencer se entrega solo, lo vacío vale 0 y se
  califica. No hay pruebas sin completar.
- **Prueba de plantilla (vacantes viejas):** también se entrega sola al vencer y se califica.
  Aquí solo cambian las etiquetas (punto 1).

- Pantallas o procesos afectados: ranking de «Prueba del puesto» (tabla, cortes, filtros, línea
  de cifras, Excel); ficha del candidato en esa pestaña; «Abrir ficha» del visor de informes;
  portal «Mis procesos» y la prueba; un correo, un aviso de campana y un aviso de WhatsApp nuevos;
  el recordatorio previo al plazo.
- Referencias:
  - [Prueba del puesto con el editor](../prueba-del-puesto-con-el-editor.md): la
    decisión 11 y el punto 13, que esta spec revisa.
  - [Etiquetas del ranking](../etiquetas-ranking-prueba-puesto.md).
  - [Plazo de la prueba del puesto](../plazo-de-la-prueba-del-puesto.md).
  - [Filtros y selección en lote](../filtros-y-seleccion-en-lote-ranking.md).
  - [La prueba al instante y los avisos de etapa](../la-prueba-al-instante-y-avisos-de-etapa.md):
    los recordatorios.
  - [Novedades por WhatsApp](../novedades-por-whatsapp.md) y
    [Contexto y resultados por WhatsApp](../contexto-y-resultados-por-whatsapp.md).
  - [Informe en PDF por etapa](../informe-pdf-por-etapa/spec.md).

## Alcance

- Incluye:
  - Quitar la lista «No completaron la prueba» y llevar a esas personas a la tabla, al total y
    al Excel actual.
  - Etiquetas nuevas para la prueba: «Sin empezar», «En curso», «No la completó», «Pendiente de
    calificación» y la nota.
  - Tratar como «No la completó» también a quien no abrió la prueba antes de que se le pasara la
    fecha.
  - Una sección «Prueba» en el panel de «Filtros» de esa pestaña.
  - «Darle más tiempo» en la ficha, para quien no la completó y para quien no la abrió a tiempo.
    La persona sigue con lo que había respondido y subido.
  - El aviso al candidato (correo, campana y WhatsApp) y que el portal le deje continuar.
  - Que el recordatorio previo al plazo vuelva a salir con la fecha nueva.
  - Que «Abrir ficha» del visor de informes encuentre a la persona en el corte correcto.
  - Impedir que «Avanzar» mueva a quien no la completó.
- Queda fuera:
  - Perfil Integral, cuestionario técnico y la calificación de las pruebas de plantilla (ver
    Contexto).
  - **Empezar de cero.** Quien ya vio el caso y las preguntas no vuelve a la casilla de salida:
    se le da tiempo para terminar lo que dejó.
  - Reabrir un proceso ya cerrado o descartado. De un estado final no se sale, como hoy.
  - Darle más tiempo a varias personas a la vez.
  - Que el candidato pida más tiempo desde el portal o por WhatsApp. El bot solo lee la
    situación nueva y entrega el aviso.
  - El texto que ve en el portal quien no abrió la prueba a tiempo y aún no recibió más tiempo.
  - La versión resumida del Excel (spec aparcada).

## Comportamiento esperado

### La tabla

1. En la pestaña «Prueba del puesto», cada fila dice en qué va su prueba, con este orden de
   prioridad. Se pinta como hoy: la nota, o «—» con el rótulo pequeño debajo.
   - **La nota**, si tiene (un 0 es nota).
   - **«Pendiente de calificación»**: la prueba se entregó, a mano o sola al vencer con todo
     respondido, y aún no tiene nota. En la prueba de plantilla, también la que se entregó sola.
   - **«No la completó»**: se le acabó el tiempo con algo sin responder o sin subir, o se le pasó
     la fecha sin haberla abierto. Al pasar el ratón: «Se le acabó el tiempo el 18 nov: le
     faltaron 4 preguntas y «Tablero.xlsx»», o «Se le acabó el tiempo el 18 nov sin abrirla».
   - **«En curso»**: la empezó y su tiempo sigue corriendo.
   - **«Sin empezar»**: la tiene y no la ha abierto, con plazo vigente o sin fecha.
   - Lo de siempre cuando no tiene prueba (en otra etapa, proceso cerrado…).
2. «Prueba incompleta» deja de usarse. Ningún texto llama incompleta a una prueba que alguien
   sigue haciendo.
3. Quienes no la completaron van al final, como toda fila sin nota, y cuentan en el total y en
   «Se ven X de Y».
4. **Cortes:**
   - Quien no la completó sale **solo en «Toda la tanda»**, tenga el proceso abierto o cerrado.
   - **No sale en «Pendiente» ni en «Le toca al candidato»**. El equipo la consulta cuando lo
     necesita; no obliga a gestionar cada prueba que quedó sin completar.
   - `filtrar` y `corteDondeSeVe` (`ranking.ts`) miran también el estado de la prueba. Así, «Abrir
     ficha» desde el visor de informes abre la ficha en «Toda la tanda», donde sí está.
   - Con el proceso abierto puede recibir más tiempo o descartarse. Un proceso cerrado no se
     reabre.
5. La línea de cifras de la tanda añade «· N no la completaron» cuando hay alguna.

### El filtro

6. En el panel «Filtros», **solo en la pestaña «Prueba del puesto»**, hay una sección
   **«Prueba»**. Va antes de «Ciudad».
   - Sus casillas son «Calificada», «Pendiente de calificación», «No la completó», «En curso» y
     «Sin empezar». Se pueden marcar varias, y vacío es todas.
   - Cada casilla muestra cuántas hay en el corte, como «Calificación con IA».
   - Funciona como «Calificación con IA»: se combina con el corte, deja su etiqueta con «×»
     («Prueba: No la completó») y se borra con «Borrar filtros».
   - Para ver a quienes no la completaron se elige «Toda la tanda». Marcar «No la completó» no
     cambia el corte ni hace que esas filas aparezcan en «Pendiente» o «Le toca al candidato».
   - Al pasar a otra pestaña no se aplica, y la sección no sale.
7. **Cerrar a varias a la vez:** en «Toda la tanda», con el filtro «No la completó», «Marcar
   todas las que se ven» y «Descartar…» (la ventana con los nombres y el motivo de siempre).

### Avanzar

8. **«Avanzar» no mueve a quien no la completó**, ni en lote ni desde la ficha. Lo impide el
   backend. En la barra queda en «No avanzaron: Beto Gil (no completó la prueba)». A los demás
   marcados sí los avanza.

### La ficha: «Darle más tiempo»

9. En la ficha de quien no la completó, en la pestaña «Prueba del puesto», arriba se lee:
   **«No la completó · se le acabó el tiempo el 18 nov, 16:40»** y en la línea siguiente qué le
   faltó («Le faltaron 4 preguntas y «Tablero.xlsx»», o «No la abrió»). La frase «No tiene nota
   ni sale en el ranking…» desaparece.
   - Se abre la ficha habitual del postulante, con toda la información disponible, los mismos
     permisos y sus informes en PDF. No se reduce a un resumen de lo que le faltó.
   - Se pueden revisar las respuestas guardadas, las opciones marcadas y los archivos o enlaces
     que alcanzó a subir. Se distinguen las preguntas sin responder y los entregables que
     faltan. Eso ya existe y se conserva; no hace falta darle más tiempo para consultarlo.
   - Abrir archivos o enlaces conserva el permiso `descargar_entregables`. La prueba sigue en «No
     la completó», sin nota final ni calificación con IA por haber abierto la ficha.
10. Debajo de esas dos líneas está **«Darle más tiempo»**, en el sitio donde hoy va «El plazo de
    esta persona». El «Descartar» de siempre sigue en su columna de «La ficha». «Darle más tiempo»
    abre una ventana, como «Descartar», con:
    - **«Hasta»**: fecha y hora, en la hora de Lima. Llega vacía y tiene que ser futura.
    - **«Motivo»**, obligatorio.
    - **«Avisar a {nombre}»**, marcada. Cubre el correo, la campana y WhatsApp.
    - Los botones «Darle más tiempo» y «Cancelar».
11. Al guardar:
    - La prueba vuelve a **«En curso»**, con lo que ya había respondido y subido. Si no la había
      abierto, vuelve a **«Sin empezar»** y su reloj arranca cuando la abra, sin pasarse de la
      fecha nueva.
    - Vuelve al corte «Le toca al candidato» y sigue en «Toda la tanda». Si está aplicado el
      filtro «No la completó», la fila deja de verse al actualizarse, sin recargar.
    - La fecha queda como propia de la persona: si después cambia la de la vacante, a ella no se
      la toca. Es lo mismo que hace hoy «El plazo de esta persona».
    - Queda en la auditoría: quién, cuándo, la fecha anterior, la nueva y el motivo.
    - La ficha muestra «Le cierra el jue 20 nov, 18:00 · más tiempo dado el 18 nov por Ana:
      {motivo}». La fila de la tabla cambia a «En curso» o «Sin empezar» sin recargar.
    - El informe en PDF de la prueba deja de estar disponible: vuelve a «sin calificar», con su
      botón apagado y el motivo al pasar el ratón, como cualquier prueba sin entregar. Los textos
      que la IA hubiera escrito para el informe de «No la completó» no se borran: la huella de la
      V84 deja de coincidir y se reescriben cuando haya entrega y nota.
12. Si llega la fecha nueva y vuelve a faltar algo, la prueba vuelve a quedar sin completar y sin
    calificar. Se le puede dar más tiempo otra vez, siempre con motivo.
13. Con todo respondido, al vencer se entrega sola y se califica, como siempre.
14. «Darle más tiempo» exige el permiso `mover_postulacion`, el mismo que «El plazo de esta
    persona». Quien no lo tiene no ve el botón.
15. **Vacante cerrada, archivada o eliminada** (decisión del 18/11/2026):
    - **Cerrada:** sí se puede. El cierre automático de la V78 cierra la vacante antes de que se
      rinda la prueba, y la prueba conserva su propia fecha.
    - **Archivada:** no. El botón sale desactivado, con el motivo como chip al lado («Vacante
      archivada»), y la API responde 409 «La vacante está archivada: desarchívala para darle más
      tiempo». La regla vale también para «El plazo de esta persona», que hoy no la mira.
    - **Eliminada:** la ficha ya no se alcanza (404), como hoy.
16. Para una prueba sin empezar o en curso con plazo vigente se sigue usando «El plazo de esta
    persona», sin cambios. En la ficha de quien no la completó, ese bloque no se pinta: lo
    sustituye «Darle más tiempo».

### Nada antes del reloj

17. Volver a «Sin empezar» no adelanta nada. Hasta que pulse «Empezar», el portal no le envía el
    caso, las preguntas, la consigna ni el detalle de los entregables. Es la regla de
    `empezada(intento)`, que sigue mirando solo si la abrió. Quien vuelve a «En curso» ya la
    había empezado y ve su prueba como la dejó.

### El candidato

18. Con «Avisar» marcada recibe tres avisos. Los tres dicen lo mismo: «Tienes más tiempo para la
    prueba de {vacante}, hasta el {fecha} (hora de Lima). Entra a Mis procesos para continuarla».
    - **Correo:** una plantilla nueva, `PRUEBA_MAS_TIEMPO`, editable como las demás y sin enlace
      con token (lleva la dirección de «Mis procesos», como los recordatorios).
    - **Campana:** un aviso de tipo `PRUEBA_MAS_TIEMPO`, con el mismo texto.
    - **WhatsApp:** un aviso nuevo en la cola de v2, tipo `PRUEBA_MAS_TIEMPO`, con las mismas
      condiciones que los de hoy: vínculo ACTIVO, número sin baja y la empresa dentro de la beta.
      Va en el formato de la regla 19, como `TextosDeWhatsapp.pruebaDisponible`:

      ```
      Hola, Beto 👋

      📌 *Analista de operaciones*
      Tienes *más tiempo* para tu prueba del puesto.
      ⏰ Hasta el *jue 20 nov, 18:00* (hora de Lima).

      👉 https://…/procesos
      Inicia sesión con tu correo y contraseña y abre «Mis procesos».
      ```

      Como no hay transición de estado, el aviso no se liga a una: se identifica por el intento y
      la fecha nueva. Dar más tiempo dos veces con la misma fecha no repite el aviso. Fuera de la
      ventana de 24 h, el bot necesita plantilla: ver «Decisiones pendientes».
    - Todo se envía después de confirmar el guardado y en su propia transacción, como la campana
      de las etapas: un fallo del aviso no deshace el plazo nuevo.
19. En «Mis procesos» y en la prueba, el aviso «Tu tiempo terminó…» deja de verse y vuelve a
    aparecer la prueba con lo que había hecho. El reloj cuenta hasta la fecha nueva. El contexto
    que lee el bot de WhatsApp deja de darla por vencida y dice que la tiene habilitada hasta la
    fecha nueva, sin cambios en el bot.

### Recordatorios

20. **«A las 24 h de que le toque» no se repite.** Dar más tiempo no es una transición de estado:
    el turno sigue siendo el mismo y ese recordatorio ya salió o se dio por gastado.
21. **«24 h antes de que venza» vuelve a salir con la fecha nueva**, una sola vez, por correo y
    campana, en su franja de 8:00 a 21:00. La deduplicación de hoy no lo impide: su clave ya
    incluye la fecha (`transicion_estado_id, plazo_en`).
    - **Quien vuelve a «Sin empezar»** ya entra en la consulta de los recordatorios. No hay que
      cambiar nada.
    - **Quien vuelve a «En curso»** hoy no recibiría ninguno, porque la consulta solo incluye
      pruebas sin abrir. La spec pide incluir las pruebas abiertas que recibieron más tiempo
      (`plazo_propio` y una marca de reapertura), solo para `ANTES_DEL_PLAZO`.
    - **Sin duplicar el aviso:** si la fecha nueva queda a menos de 24 h, el momento del
      recordatorio ya pasó y saldría justo después del aviso de «más tiempo». En ese caso, al
      guardar se escribe ese `ANTES_DEL_PLAZO` como OMITIDO para la fecha nueva: el aviso ya cumple
      su papel.

## Referencia visual

Maqueta rehecha el 18/11/2026 sobre el panel actual (Northstar). Copia las piezas reales de
`Vacante.tsx` y `Vacante.module.css`, `Tabla.module.css`, `FiltrosDelRanking`,
`PruebaDelCandidato`, `Modal`, `Armazon`, `menu.ts`, `piezas.module.css` y `mundo.css`: Onest y
Syne, tinta `#111312`, azul `#1E3A8A`, bruma `#F5F7FB`/`#E9EDFF`, verde `#0B7A63` y rojo
`#C22A3F`. Usa datos ficticios. Las páginas son autocontenidas: se abren con doble clic y solo
cargan las fuentes de Google Fonts.

- [1 · Ranking de la prueba con quienes no la completaron y el filtro «Prueba»](maqueta/1-ranking.html).
- [2 · Ficha de quien no la completó y la ventana «Darle más tiempo»](maqueta/2-ficha-dar-mas-tiempo.html).
- [3 · La ficha después de darle más tiempo](maqueta/3-ficha-tras-mas-tiempo.html).
- [4 · Vacante archivada: el botón desactivado y su motivo](maqueta/4-vacante-archivada.html).
- [5 · El portal del candidato con la prueba reabierta](maqueta/5-portal-prueba-reabierta.html).

Rigen el `DESIGN.md` de `Frontend-EX` y los componentes que ya existen: `FiltrosDelRanking`,
`BarraDeLaSeleccion`, `DescartarPostulacion`, `Modal`, `PruebaDelCandidato` y los campos de
`PlazoDeUnaPersona`. Como en el resto del panel, no se añaden líneas de ayuda bajo los campos.

**1. Tabla, corte «Toda la tanda», con el filtro puesto**

```
[ Buscar por nombre ] [ Filtros · 1 ] [ Columnas ] [ Borrar filtros ]     [ Descargar Excel (2) ]
  Prueba: No la completó ×

 ☐   #   Candidato        Nota
 ☐  14   Beto Gil          —  No la completó
 ☐  15   Carla Soto        —  No la completó
```

**2. Sección del panel «Filtros» (solo en «Prueba del puesto»)**

```
Prueba
 ☐ Calificada 12
 ☐ Pendiente de calificación 3
 ☐ No la completó 2
 ☐ En curso 4
 ☐ Sin empezar 6
```

**3. Ventana «Darle más tiempo»**

```
Darle más tiempo a Beto Gil                                   ×
  Hasta                 [ 20/11/2026 18:00 ]
  Motivo
  [ Se le cortó la luz a mitad de la prueba          ]
  ☑ Avisar a Beto
                                    [ Cancelar ] [ Darle más tiempo ]
```

- Escritorio 1440×900 y móvil 390×844. En móvil la ventana ocupa la pantalla y los botones se
  apilan.
- Fidelidad obligatoria: las etiquetas exactas, la sección «Prueba» con sus cinco casillas y sus
  cifras, y en la ventana los tres campos y los dos botones. Puede adaptarse el espaciado y la
  posición del bloque dentro de la ficha. El estilo lo manda `DESIGN.md`.

## Criterios de aceptación

- AC-01: Dada una prueba del editor que venció con una pregunta sin responder y el proceso
  abierto, cuando el equipo abre «Prueba del puesto», entonces la persona está en la tabla con
  «No la completó», solo en el corte «Toda la tanda» y en el total, y ya no existe la lista
  «No completaron la prueba». No aparece en «Pendiente» ni en «Le toca al candidato».
- AC-02: Dada una prueba con fecha que nadie abrió y cuya fecha pasó, entonces sale como «No la
  completó» («sin abrirla» al pasar el ratón), solo en «Toda la tanda», y no en «Pendiente» ni en
  «Le toca al candidato».
- AC-03: Dadas una prueba sin abrir con plazo vigente y otra empezada con el reloj corriendo,
  entonces dicen «Sin empezar» y «En curso». Ninguna fila dice «Prueba incompleta».
- AC-04: Dada una prueba del editor entregada sola con todo respondido y aún sin nota, entonces
  dice «Pendiente de calificación».
- AC-05: Desde «Toda la tanda», cuando se marca «No la completó» en la sección «Prueba», solo
  quedan esas personas, sale la etiqueta «Prueba: No la completó ×» y cada casilla muestra su
  cifra. En la pestaña «Perfil integral» la sección no aparece. En «Pendiente» o «Le toca al
  candidato», marcar solo «No la completó» no devuelve filas ni cambia el corte.
- AC-06: Dada una persona que no la completó, marcada junto a una calificada, cuando se pulsa
  «Avanzar», entonces avanza solo la calificada, la otra sigue donde estaba y la barra dice por
  qué. Tampoco avanza llamando a la API directamente.
- AC-07: Dada una persona que no la completó, con el proceso abierto, cuando alguien con
  `mover_postulacion` le da más tiempo con fecha futura y motivo, entonces su prueba vuelve a «En
  curso» con sus respuestas y archivos y la fecha queda como propia. La auditoría guarda la fecha
  anterior, la nueva y el motivo, y la fila cambia sin recargar. Vuelve a «Le toca al candidato» y
  deja de aparecer si está aplicado el filtro «No la completó».
- AC-08: Con «Avisar» marcada, el candidato recibe el correo (sin enlace con token) y la
  notificación de la campana. Si tiene vínculo de WhatsApp ACTIVO y la empresa está en la beta,
  queda en la cola un aviso `PRUEBA_MAS_TIEMPO` con la fecha nueva. En el portal la prueba
  aparece con lo que había hecho y el reloj llega hasta la fecha nueva. Con la casilla desmarcada
  no recibe nada por ningún canal. Guardar dos veces la misma fecha no deja dos avisos.
- AC-09: Dada una prueba reabierta, cuando el candidato completa todo y entrega, entonces se
  califica como cualquier otra. Si vuelve a vencer con huecos, vuelve a «No la completó».
- AC-10: Dada una persona que no había abierto la prueba, cuando se le da más tiempo, entonces
  vuelve a «Sin empezar», puede abrirla y su reloj no pasa de la fecha nueva.
- AC-11: Sin motivo, con una fecha pasada, con el proceso ya cerrado o sin el permiso, «Darle más
  tiempo» no guarda nada y dice por qué en español. Con la vacante archivada, el botón sale
  desactivado con el chip «Vacante archivada» y la API responde 409. Con la vacante **cerrada**
  sí guarda. Sin el permiso, el botón ni sale.
- AC-12: Dado el corte «Toda la tanda» con quienes no la completaron visibles, cuando se descarga
  el Excel, entonces los lleva con la nota en blanco. El pie ya no los cuenta como «no son de esta
  vacante».
- AC-13: Desde «Toda la tanda», con el filtro «No la completó», «Marcar todas» y «Descartar…»
  cierran los procesos abiertos seleccionados con la ventana de nombres y el motivo de siempre.
  Los ya cerrados conservan su estado.
- AC-14: En 1440×900 y en 390×844, la tabla, la sección «Prueba» y la ventana de la ficha llevan
  las etiquetas, los campos y los botones de la maqueta. El estilo es el de `DESIGN.md`.
- AC-15: Dada una persona que no la completó, con respuestas y entregables guardados, al abrir su
  ficha desde «Toda la tanda» se ve la ficha habitual. Se pueden revisar las respuestas abiertas,
  las opciones marcadas y, con el permiso correspondiente, los archivos y enlaces disponibles. Se
  distinguen las preguntas sin responder y los entregables que faltan, sin reabrir la prueba ni
  generar una nota final o pedir calificación con IA.
- AC-16: Dada una persona que no la completó, con su informe de la prueba disponible («No la
  completó»), cuando se le da más tiempo, entonces el botón «Prueba» de sus informes pasa a
  apagado con el motivo «sin calificar». Cuando entrega y tiene nota, el informe vuelve a estar
  disponible, con textos escritos para los datos nuevos.
- AC-17: Dada una persona que no había abierto la prueba y recibe más tiempo, cuando entra al
  portal antes de pulsar «Empezar», entonces la respuesta del servidor no trae el caso, las
  preguntas, la consigna ni el detalle de los entregables.
- AC-18: Dada una prueba reabierta con la fecha nueva a más de 24 h, cuando llega «24 h antes»
  dentro de la franja, entonces sale un recordatorio previo al plazo con la fecha nueva, también
  si la persona ya la había empezado. El de «a las 24 h de que le toque» no se repite. Con la
  fecha nueva a menos de 24 h, no sale ningún recordatorio además del aviso de «más tiempo».
- AC-19: Desde el visor de informes, «Abrir ficha» de una persona que no la completó abre su
  ficha en «Toda la tanda» de «Prueba del puesto».

## Casos límite y errores

- **Datos vacíos o inválidos:** falta el motivo, la fecha es pasada o la hora no es válida. Se
  muestra el error junto al campo y no se envía nada.
- **Usuario sin permisos:** sin `mover_postulacion` no se ve «Darle más tiempo» y la API
  responde 403. Con `ver_embudo` sí se ven la tabla y el filtro.
- **Doble envío:** el botón se bloquea mientras guarda. Un segundo envío para la misma persona ya
  no encuentra la prueba sin completar: responde con un mensaje claro y no duplica los avisos.
- **Carreras:**
  - El barrido cierra la prueba mientras alguien le da más tiempo: si el guardado llega después,
    la reabre con la fecha nueva.
  - Se descarta el proceso mientras otro le da más tiempo: gana lo que llegue primero. Con el
    proceso ya cerrado no se reabre nada.
- **Vacante cerrada con su fecha ya pasada:** la fecha propia manda, como hoy con «El plazo de
  esta persona». El cierre automático no toca la prueba.
- **Vacante archivada:** no se da más tiempo (punto 15). Quien no la completó se sigue viendo en
  la tabla y en su ficha, en solo lectura.
- **WhatsApp sin vínculo, con baja o fuera de la beta:** no se encola nada; correo y campana salen
  igual.
- **Vacantes con prueba de plantilla o con cuestionario:** solo cambian las etiquetas, y «No la
  completó» se usa solo para quien no abrió la prueba a tiempo. «Darle más tiempo» se ofrece solo
  en ese caso, porque la plantilla al vencer se entrega sola.

## Datos y compatibilidad

- Lo que se guarda:
  - Al dar más tiempo, la prueba deja de estar marcada como no completada y entregada. Recibe la
    fecha nueva como propia (`plazo_propio`).
  - Lo que hace falta para que la ficha diga quién, cuándo y por qué, y para que los recordatorios
    reconozcan la reapertura. Sale de la auditoría o de columnas en la prueba; eso lo decide quien
    implemente.
  - La plantilla de correo `PRUEBA_MAS_TIEMPO`, sembrada en todas las organizaciones como las de
    la V70.
  - En `aviso_whatsapp`: el tipo `PRUEBA_MAS_TIEMPO` en el CHECK, `transicion_estado_id`
    admitiendo nulo solo para ese tipo, y una clave única para él (intento o postulación + fecha
    nueva).
  - Todo en **una migración, la V89**: V85 está en main, V86 la usa el brochure, V87 la
    convocatoria y V88 el video. Si al construirla alguna sigue libre, se toma la siguiente libre
    por encima de la mayor aplicada. Los huecos V81–V83 no se usan: Flyway no aplica fuera de
    orden.
- Registros existentes:
  - Las «no completada» de hoy con el proceso abierto aparecen en la tabla, y se les puede dar más
    tiempo. Salen solo en «Toda la tanda».
  - Las que ya se cerraron con «Cerrar su proceso» salen solo en «Toda la tanda».
  - Las pruebas sin abrir y con fecha pasada, que hoy están atascadas, aparecen como «No la
    completó» solo en «Toda la tanda».
- Compatibilidad:
  - `estadoPrueba` gana valores (`EN_CURSO`, `SIN_EMPEZAR`) e `INCOMPLETA` cambia de significado
    (pasa a ser «No la completó»; el nombre del valor puede quedarse o renombrarse a
    `NO_COMPLETADA`, como en `PruebaDelCandidato`). El panel y el backend se despliegan juntos.
  - El endpoint `/prueba-propia/no-completaron` queda sin uso en el panel. Se retira junto con
    `NoCompletaron.tsx`, y se actualiza el e2e 48
    (`herramientas/e2e/48-prueba-del-puesto-con-el-editor.spec.ts`), que hoy comprueba la lista y
    que esas personas no van al Excel.
  - El bot de WhatsApp (`Renaser/crm`) tiene que aceptar el tipo `PRUEBA_MAS_TIEMPO`. Hasta que lo
    acepte, el aviso queda FALLIDO o pendiente en la cola de EX sin afectar a lo demás. El bot no
    se despliega solo: va a mano.

## Verificación

- Actor y datos: alguien de Talento con `mover_postulacion` y otra persona sin ese permiso. Una
  vacante con prueba del editor cronometrada (pocos minutos), cerrada por fecha, y cuatro
  candidatos en la etapa:
  - uno que la entrega completa;
  - uno que deja vencer el tiempo con una pregunta sin responder;
  - uno que nunca la abre, con fecha límite pasada;
  - uno que la está haciendo.
- E2E repetibles en headless:
  1. Vencer con huecos: aparece «No la completó» solo en «Toda la tanda», el filtro la aísla y la
     lista aparte no existe. No aparece en «Pendiente» ni en «Le toca al candidato», tampoco al
     marcar el filtro en esos cortes.
  2. Darle más tiempo con motivo en la vacante cerrada: el portal la deja continuar con sus
     respuestas, entrega y se califica. Al reabrirse vuelve a «Le toca al candidato» y sale del
     filtro «No la completó».
  3. Al que nunca la abrió: darle más tiempo, comprobar que no ve el caso antes de «Empezar», que
     la abra y que la entregue.
  4. «Avanzar» en lote con una persona que no la completó: no la mueve. Por API tampoco.
  5. «Descartar…» en lote desde el filtro.
  6. El Excel con quienes no la completaron.
  7. Los rechazos: sin motivo, con fecha pasada, con el proceso cerrado, con la vacante archivada
     y sin permiso.
  8. El botón «Prueba» de los informes, antes y después de darle más tiempo.
  9. «Abrir ficha» desde el visor de informes.
  10. Actualizar el e2e 48.
- Pruebas de integración: el aviso de WhatsApp encolado con y sin vínculo, y sin duplicar; el
  recordatorio previo con la fecha nueva (a más y a menos de 24 h), y que el de «a las 24 h» no
  se repita.
- Riesgos que QA explora con Chrome visible:
  - el reloj del portal tras reabrir una prueba cronometrada;
  - la cifra de cada casilla del filtro al cambiar de corte;
  - la etiqueta al pasar el ratón;
  - la ventana en móvil.
- Comparación visual: las pantallas de la maqueta en 1440×900 y 390×844.
- Lo que quiero comprobar personalmente: en una vacante real, ver a quien se le acabó el tiempo
  dentro de la tabla, filtrarlo, darle más tiempo y ver que el candidato recibe el aviso y puede
  seguir.

## Dependencias y dudas

- Requiere:
  - [Qué buscar en el CV](../que-buscar-en-el-cv/spec.md): **cumplida** (PR #135/#97,
    V80). Esta spec parte del ranking, la ficha y el Excel que dejó.
  - [Perfil Integral hasta el cierre de la vacante](../perfil-integral-hasta-el-cierre-de-la-vacante/spec.md):
    **cumplida** (PR #132/#93, V78). De ahí sale la regla de la vacante cerrada (punto 15).
  - El cambio en el bot de `Renaser/crm` para el tipo `PRUEBA_MAS_TIEMPO`, y su plantilla fuera
    de ventana (ver la decisión pendiente 1).
- Choca con:
  - «El Excel del ranking, resumido» (`specs/Draft/excel-del-ranking-resumido/spec.md`), aparcada: si se
    retoma, debe partir del Excel con estas filas incluidas.
  - «El informe del grupo» (`specs/Draft/informe-del-grupo.md`) y
    «Seguimiento de pendientes entre etapas» (`specs/Draft/seguimiento-de-pendientes-entre-etapas.md`) hablan
    de quien «no completó la prueba»: conviene que usen el mismo rótulo, «No la completó».
- Decisiones confirmadas por el usuario:
  1. (23/10/2026) Quienes no la completaron salen **solo en «Toda la tanda»**, accesibles por
     búsqueda o filtro. No aparecen en «Pendiente»: el equipo las atiende cuando lo necesita, por
     ejemplo si el candidato le escribe por WhatsApp.
  2. (23/10/2026) «Darle más tiempo» fija **una fecha y hora como nuevo límite**, en la hora de
     Lima. No se añade una modalidad de N minutos desde que vuelva a entrar.
  3. (23/10/2026) Se confirma el cambio de la decisión 11 del 01/10/2026 («quien deja huecos no
     sale en el ranking»): vuelve a la tabla, **sin calificarse**.
  4. (18/11/2026) Se bloquea solo con la vacante **archivada o eliminada**, no con la vacante
     cerrada.
  5. (18/11/2026) Además del correo y la campana, se avisa **por WhatsApp** con la cola de v2.
- Decisiones pendientes: ninguna.
- Decidido el 18/11/2026 (recomendaciones aceptadas por el usuario):
  1. **WhatsApp fuera de la ventana de 24 h:** hasta que Meta apruebe la plantilla nueva
     `prueba_mas_tiempo_postulante`, el bot usa fuera de ventana la ya aprobada
     `prueba_disponible_postulante` («tu prueba ya está disponible», que es cierto). La nueva se
     pide en paralelo y, cuando esté aprobada, la usa en su lugar.
  2. **Quien ya la había empezado** también recibe el recordatorio de «24 h antes» de la fecha
     nueva, solo tras darle más tiempo (punto 21).
  3. **La fecha nueva no tiene tope**: solo tiene que ser futura, como hoy en «El plazo de esta
     persona». Lo decide quien la da, con su motivo, y queda en el historial.
- Pendiente: autorizar la spec para implementar mediante `/harness ready specs/Draft/incompletas-en-el-ranking-y-mas-tiempo/spec.md`.
