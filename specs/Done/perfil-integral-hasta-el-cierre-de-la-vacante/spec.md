# El Perfil Integral se responde mientras la vacante esté abierta
Estado: TERMINADO

## Objetivo

Que nadie pierda su postulación por tardar en responder el Perfil Integral mientras la vacante
sigue abierta. Hoy la evaluación vence a los 14 días de postular y el sistema cierra la
postulación aunque la vacante siga recibiendo gente: el equipo pierde candidatos que todavía
podían responder.

Con este cambio desaparecen los 14 días. El Perfil Integral se puede responder mientras la
vacante esté publicada, y termina cuando la vacante se cierra. Al crear la vacante, el equipo
elige cómo se cierra:

- **Permanente:** alguien del equipo la cierra a mano, por ejemplo cuando ya contrató.
- **Fecha límite:** se cierra sola en la fecha que el equipo fijó.

Como en los portales de empleo habituales, el candidato no ve ninguna fecha: la publicación
está ahí mientras se pueda postular.

## Contexto

- Comportamiento actual (recorrido del candidato):
  1. Postula a una vacante con banco de preguntas (el banco del nivel o las preguntas propias
     de la vacante). Su evaluación nace con vencimiento = hoy + 14 días
     (`dias_plazo_evaluacion`).
  2. En la portada de la evaluación ve «14 días · para entregarla» y, en la barra de arriba,
     «N días para entregarla», «Se entrega hoy» o, en la última hora, una cuenta atrás.
  3. Recibe un recordatorio al poco de postular y otro 24 h antes de que venza.
  4. Si no entrega a tiempo, un barrido que corre cada 60 s marca la evaluación `VENCIDA` y
     cierra la postulación con `PLAZO_VENCIDO`. Esto pasa aunque la vacante siga abierta.
  5. El cierre le llega por correo (`POSTULACION_CERRADA`: «Tu postulación a X quedó
     cerrada») y por la campana del portal («Tu postulación a X se cerró · El proceso
     terminó. Gracias por el tiempo que le dedicaste.»). Por WhatsApp solo llegará cuando se
     encienda el productor v2 (hoy apagado); con v1 el cierre no manda mensaje.
- Recorrido del equipo:
  1. Al crear la vacante elige «Cómo se cierra»: Permanente (por defecto), Por plazas o Por
     fecha. Solo «Por fecha» pide «Fecha de cierre», y es informativa: **nada cierra la
     vacante al llegar ese día** ni el portal la muestra.
  2. Cierra la vacante a mano desde el panel. Hoy eso solo frena las postulaciones nuevas:
     quien está respondiendo conserva sus 14 días y puede terminar después del cierre.
- Lo técnico que hay debajo:
  - La fecha se fija en `ServicioEvaluacionImpl.crearAlPostular` (banco del nivel) y
    `crearDePreguntasPropias` (preguntas propias), con `diasDePlazo(organizacionId)`. La
    cierra `cerrarVencidas`, llamado por `SondeoVencimientos` cada minuto.
  - Cerrar la vacante es `ServicioVacantesPanelImpl.cerrar` (comentario «Cerrar NO arrastra
    las postulaciones en marcha»), que además deja el aviso de WhatsApp
    `CONVOCATORIA_CERRADA` («tu postulación sigue») a quien sigue en carrera.
  - El formulario está en `CamposDeLaVacante.tsx` (Frontend-EX). La fecha viaja como
    `new Date('AAAA-MM-DD').toISOString()`, o sea las 00:00 UTC, que en Lima son **las 19:00
    del día anterior**. Hoy no importa porque la fecha no hace nada; en cuanto cierre la
    vacante, la cerraría cinco horas antes de lo que eligió el equipo.
  - El portal pinta el plazo en `src/paginas/evaluacion/Evaluacion.tsx` (portada y barra) con
    `diasHasta` de `src/dominio/reloj.ts`. La banda de datos de la portada se dibuja siempre,
    aunque no tenga nada dentro.
  - `reabrirEvaluacion` (`ServicioPerfilIntegralPanelImpl`) vuelve a fijar fecha con días.
    No tiene pantalla en el panel.
  - **El mismo parámetro rige el cuestionario técnico** CAZATALENTOS
    (`crearTecnicaAlEntrar`), que no es el Perfil Integral y no cambia.
  - La política de privacidad pública (`PoliticaPublica.tsx`) dice «Cierra tu proceso si se
    te pasa el plazo que tenías para responder una evaluación o una prueba».
- Pantallas o procesos afectados: formulario de la vacante; cierre de la vacante (a mano y
  por fecha); evaluación del Perfil Integral en el portal; recordatorios del banco; política
  de privacidad pública.
- Referencias existentes: [estados de postulación](../../../docs/03-ESTADOS-POSTULACION.md)
  («Se cierra la convocatoria», recordatorios y transiciones sin persona),
  [contrato WhatsApp v2](../../../docs/CONTRATO-CONTEXTO-Y-RESULTADOS-POR-WHATSAPP-V2.md).

## Alcance

- Incluye:
  - En el formulario de la vacante, «Cómo se cierra» con dos opciones: **Permanente** y
    **Fecha límite**. Desaparecen «Por plazas» y «Cuántas plazas».
  - Con fecha límite, la vacante se cierra sola ese día a las 23:59 (hora de Lima).
  - Las evaluaciones del Perfil Integral nacen sin fecha de vencimiento y terminan cuando la
    vacante se cierra, a mano o por su fecha.
  - Al cerrarse la vacante, las evaluaciones sin terminar vencen y las postulaciones que
    seguían esperándolas se cierran, con los avisos de siempre.
  - El portal deja de mostrar cualquier plazo del Perfil Integral, sin poner nada en su
    lugar.
  - Las evaluaciones que hoy tienen sus 14 días pasan a la regla nueva.
  - `reabrirEvaluacion` deja de usar días.
  - La política de privacidad dice cuándo se cierra el proceso.
- Queda fuera:
  - Avisos nuevos al candidato o al equipo, y textos nuevos en el portal.
  - Mostrar al candidato la fecha de cierre de la vacante.
  - El cuestionario técnico CAZATALENTOS: sigue con sus días y su cronómetro.
  - Borrar el parámetro `dias_plazo_evaluacion`: lo sigue usando ese cuestionario.
  - La prueba del puesto: sigue con su «Fecha límite para dar la prueba».
  - Reabrir de oficio las postulaciones que ya se cerraron por `PLAZO_VENCIDO`.
  - Una pantalla en el panel para reabrir evaluaciones.

## Comportamiento esperado

### Flujo 1 · El equipo crea o edita la vacante

1. «Cómo se cierra» ofrece dos opciones:
   - **Permanente** (la de por defecto): no pide nada más. La vacante sigue publicada hasta
     que alguien la cierre.
   - **Fecha límite**: aparece «Fecha de cierre» (selector de día). Sin día no se guarda:
     «Elige el día en que cierra la vacante.» Un día pasado tampoco: «Esa fecha ya pasó:
     elige un día a partir de hoy.»
2. La fecha se guarda como ese día a las 23:59:59, hora de Lima. Donde el panel la muestre,
   sale con la hora: «vie 31/10, 23:59».
3. Con la vacante publicada, la fecha se puede mover o pasar a Permanente (y al revés) como
   cualquier otro dato de la vacante.

### Flujo 2 · El candidato postula y responde

1. Postula a una vacante publicada con banco de preguntas. Su evaluación nace sin fecha de
   vencimiento.
2. En la portada de la evaluación desaparece el dato «14 días · para entregarla» y no se pone
   nada en su lugar. Queda el de los minutos de referencia, si los hay. Si no queda ningún
   dato, tampoco se dibuja la banda con sus dos líneas.
3. En la barra de arriba desaparecen «N días para entregarla», «Se entrega hoy» y la cuenta
   atrás.
4. Puede salir y volver cuantas veces quiera; lo respondido se conserva como hoy.
5. Recibe solo el primer recordatorio («Aún no has respondido tu evaluación para X»). El de
   «vence el …» no sale, porque no hay fecha.
6. Al entregar, todo sigue como hoy.

### Flujo 3 · La vacante se cierra

1. Se cierra de una de dos formas:
   - **A mano** (Permanente o Fecha límite): como hoy, con su motivo.
   - **Por su fecha** (Fecha límite): a las 23:59 del día fijado (como mucho, un minuto
     después), con el sistema como autor y el motivo «Llegó la fecha de cierre» en la
     auditoría.
2. En los dos casos dejan de entrar postulaciones nuevas y la vacante sale del tablón.
3. En el mismo momento (o como mucho al minuto siguiente):
   - cada evaluación del Perfil Integral sin terminar de esa vacante (sin empezar o a medias)
     pasa a `VENCIDA`, con el momento del cierre como su límite. Lo respondido a medias no se
     califica, igual que hoy;
   - las postulaciones que seguían esperando esa evaluación (`PERFIL_TURNO_CANDIDATO`) pasan
     a `CERRADA` con `PLAZO_VENCIDO`, como transición del sistema;
   - quien ya entregó sigue en carrera, como hoy.
4. A quien se le cierra la postulación le llegan los avisos de siempre: el correo
   `POSTULACION_CERRADA` y la campana del portal. Por WhatsApp (con el productor v2
   encendido) recibe el aviso del cierre de su postulación, y no además el de «la
   convocatoria cerró y tu postulación sigue», que ya no sería cierto.

### Flujo 4 · El candidato tenía la evaluación abierta cuando se cerró la vacante

1. Intenta guardar una respuesta o entregar.
2. El servidor lo rechaza. El mensaje que hoy dice «El plazo para responder esta evaluación
   ya pasó» pasa a decir «La vacante se cerró y esta evaluación ya no se puede responder.»
3. Si entregó antes de que el cierre se confirmara, su entrega vale y no se le cierra la
   postulación.

### Flujo 5 · Reabrir una evaluación (solo por la API del panel)

1. `reabrirEvaluacion` deja la evaluación abierta sin fecha, hasta que la vacante se cierre.
   El campo de días desaparece del pedido y de la respuesta.
2. Con la vacante cerrada, se rechaza: «La vacante está cerrada: su evaluación ya no se
   puede reabrir».

## Referencia visual

- Maqueta: [Portada del Perfil Integral](https://claude.ai/artifact/ERzPmwGGpwFuDcQPJKnjmS),
  con la portada de hoy y la del cambio en escritorio (1280 px) y móvil (390 px).
- Versión aprobada: versión 4, aprobada por el usuario el 20/10/2026.
- Capturas locales aprobadas (portada antes de empezar, con 50 min de referencia):
  - Hoy, 1280×900: [portada-hoy-1280.png](capturas/portada-hoy-1280.png)
  - Con el cambio, 1280×900:
    [portada-con-el-cambio-1280.png](capturas/portada-con-el-cambio-1280.png)
  - Con el cambio, 390×1000:
    [portada-con-el-cambio-390.png](capturas/portada-con-el-cambio-390.png)
- Pantallas y estados: portada de la evaluación antes de empezar, con minutos de referencia.
  Sin minutos, la banda de datos no aparece.
- Escritorio y móvil: la portada ya se adapta hoy; el cambio no toca su disposición.
- Diseño existente: los estilos de `Evaluacion.module.css`; no se crea ningún componente.
- Fidelidad requerida: quitar el dato del plazo de la portada y el plazo de la barra, sin
  añadir textos; el resto de la portada no cambia.

## Criterios de aceptación

- AC-01: Dado el formulario de crear vacante, cuando se abre, entonces «Cómo se cierra»
  ofrece solo «Permanente» (marcada) y «Fecha límite», y no aparecen «Por plazas» ni
  «Cuántas plazas».
- AC-02: Dado el formulario con «Fecha límite», cuando se intenta guardar sin día o con un
  día pasado, entonces no se guarda y se lee el mensaje del Flujo 1 (también si el pedido
  llega al servidor sin pasar por el formulario).
- AC-03: Dada una vacante con fecha límite el 31/10, cuando se guarda, entonces la fecha
  guardada es el 31/10 a las 23:59:59 hora de Lima y el panel muestra «vie 31/10, 23:59».
- AC-04: Dado un candidato que postula a una vacante con banco del nivel (y otra con
  preguntas propias), cuando se crea su evaluación, entonces no tiene fecha de vencimiento.
- AC-05: Dado un candidato con su evaluación sin fecha, cuando abre la portada, entonces no
  ve ningún plazo, ni días, ni texto en su lugar, y la portada coincide con la maqueta
  aprobada; dentro de la evaluación, la barra no muestra plazo ni cuenta atrás.
- AC-06: Dada una evaluación sin fecha y sin minutos de referencia, cuando se abre la
  portada, entonces no se dibuja la banda de datos vacía.
- AC-07: Dada una evaluación sin fecha creada hace más de 14 días en una vacante publicada,
  cuando corre el barrido de vencimientos, entonces la evaluación sigue abierta y la
  postulación sigue en `PERFIL_TURNO_CANDIDATO`.
- AC-08: Dada una evaluación sin fecha, cuando tocan los recordatorios, entonces sale solo el
  primero y nunca uno que diga «vence el».
- AC-09: Dada una vacante publicada con dos personas en `PERFIL_TURNO_CANDIDATO` (una sin
  empezar y otra a medias) y una que ya entregó, cuando el equipo la cierra a mano, entonces
  las dos evaluaciones quedan `VENCIDA` con el momento del cierre como límite, las dos
  postulaciones quedan `CERRADA` con `PLAZO_VENCIDO`, reciben el correo y la campana de
  siempre, y la tercera sigue en su estado.
- AC-10: Dada esa misma vacante con fecha límite, cuando pasan las 23:59 de ese día y corre
  el barrido, entonces la vacante queda cerrada (autor: sistema), sale del tablón, rechaza
  postulaciones nuevas y las tres personas quedan como en AC-09.
- AC-11: Dado el cierre de AC-09 con WhatsApp v2 encendido, entonces las dos personas
  cerradas reciben el aviso de cierre de su postulación y no el de «la convocatoria cerró y
  tu postulación sigue»; la tercera recibe este último.
- AC-12: Dado un candidato con la evaluación abierta en el portal, cuando la vacante se
  cierra y él intenta guardar o entregar, entonces ve «La vacante se cerró y esta evaluación
  ya no se puede responder.».
- AC-13: Dada una postulación cuya evaluación inicial no está terminada pero que el equipo ya
  movió a otra etapa, cuando se cierra la vacante, entonces su evaluación pasa a `VENCIDA` y
  su postulación no se cierra.
- AC-14: Dados los datos de antes del cambio, cuando se aplica la migración, entonces se
  cumple cada regla de «Datos y compatibilidad».
- AC-15: Dada una evaluación del Perfil Integral sin terminar, cuando se pide reabrirla con
  la vacante publicada, entonces queda abierta sin fecha; con la vacante cerrada, se rechaza
  con el mensaje del Flujo 5.
- AC-16: Dado el cuestionario técnico de una vacante CAZATALENTOS, cuando se crea, entonces
  sigue venciendo a los días de `dias_plazo_evaluacion`, como hoy.
- AC-17: Dada la política de privacidad pública, cuando se lee lo que el sistema hace sin una
  persona, entonces dice que el proceso se cierra si la vacante se cierra antes de que el
  candidato termine su evaluación, en lugar de «si se te pasa el plazo» para la evaluación.

## Casos límite y errores

- Datos vacíos o inválidos: fecha vacía o pasada con «Fecha límite», rechazada en el panel y
  en el servidor. Una vacante sin banco (`aplica_evaluacion = false`) no tiene evaluación; su
  cierre (a mano o por fecha) se comporta como hoy.
- Usuario sin permisos: cerrar la vacante o cambiar cómo se cierra exige el mismo permiso que
  hoy.
- Recarga, reintento o doble envío: cerrar una vacante ya cerrada se rechaza como hoy. Si el
  barrido corre dos veces, no cierra dos veces la vacante ni avisa dos veces a nadie.
- Postulación que entra en el mismo instante del cierre: no puede quedarse abierta para
  siempre. Como mucho al minuto siguiente, su evaluación queda `VENCIDA` y su postulación
  cerrada.
- Entrega simultánea al cierre: gana lo que se confirme primero. Si la entrega entra antes,
  la evaluación queda `TERMINADA` y la postulación sigue; si no, el Flujo 4.
- Personas que se retiraron o ya fueron descartadas con la evaluación sin terminar: la
  evaluación pasa a `VENCIDA` y no se les manda nada.
- Vacante en borrador cuya fecha límite pasa antes de publicarla: no se cierra sola; al
  publicarla se exige una fecha futura o pasar a Permanente.
- Vacante que se cierra por su fecha con gente en la prueba del puesto: la prueba sigue con
  su fecha límite. Como hoy al cerrar a mano, la fecha común de la prueba ya no se puede
  editar, pero sí la de cada persona.
- Vacante eliminada (V60): sigue su propio camino (`VACANTE_ELIMINADA`).

## Datos y compatibilidad

- Información que debe guardarse: ninguna columna nueva. `vacante.cierra_en` pasa a mandar
  cuando `tipo_cierre = 'FECHA'`. Las vacantes nuevas guardan `'FECHA'` o `'PERMANENTE'`; la
  restricción admite todavía `'PLAZAS'` por las de antes. En la evaluación que vence al
  cerrarse la vacante: estado `VENCIDA` y como límite el momento del cierre, para que el
  contexto de WhatsApp (`entregarAntesDe` en `VENCIDA`) tenga fecha.
- Comportamiento para registros existentes (migración, la siguiente libre; hoy V78):
  - **Vacantes «Por plazas»:** pasan a Permanente.
  - **Fechas guardadas a las 00:00 UTC:** pasan a las 23:59:59 hora de Lima de ese día.
  - **Vacantes publicadas con fecha ya pasada:** pasan a Permanente, para que no se cierren
    solas al desplegar. El equipo les pone otra fecha si la quiere.
  - **Evaluaciones del Perfil Integral abiertas en vacantes publicadas:** se les quitan los
    14 días y quedan abiertas hasta que la vacante se cierre. No se les avisa: solo ganan
    tiempo.
  - **Evaluaciones del Perfil Integral abiertas en vacantes ya cerradas:** se les aplica la
    regla nueva al desplegar: quedan `VENCIDA` y su postulación se cierra con
    `PLAZO_VENCIDO` y los avisos de siempre, igual que si la vacante se cerrara ahora.
  - **Postulaciones ya cerradas por `PLAZO_VENCIDO`:** no se reabren. Ya recibieron el
    correo de cierre. El historial se conserva.
- Restricciones de compatibilidad:
  - `dias_plazo_evaluacion` se queda solo para el cuestionario técnico, y su descripción lo
    dice. El Perfil Integral deja de leerlo.
  - El contrato de WhatsApp v2 ya admite la fecha de entrega vacía, y el motivo de cierre no
    viaja al bot.
  - `ReabrirEvaluacion` y `EvaluacionReabierta` pierden los días; ninguna pantalla los usa.

## Verificación

- Actor/rol y datos de prueba necesarios: usuario del panel con permiso para editar y cerrar
  vacantes; una vacante Permanente y otra con Fecha límite, con banco del nivel o preguntas
  propias; tres candidatos por vacante (sin empezar, a medias, entregado). Para el cierre
  por fecha, en el clon se puede poner la fecha a unos minutos vista. Para AC-13, una
  postulación movida a mano a otra etapa sin terminar la evaluación.
- Recorridos que deben quedar como E2E repetibles en headless:
  - Crear vacante Permanente y con Fecha límite (sin día: rechazo; con día: guardada a las
    23:59 de Lima).
  - Postular y ver la portada sin plazo (AC-05).
  - Cerrar a mano con pendientes (AC-09) y cierre por fecha (AC-10).
  - Candidato con la evaluación abierta tras el cierre (AC-12).
- Riesgos que QA debe explorar con Chrome visible:
  - Que ningún texto del portal siga hablando de días para el Perfil Integral (portada,
    barra, «Mis procesos», campana).
  - Fechas cerca de medianoche y del día 1 (desfase UTC/Lima).
  - Que no quede «Por plazas» en ninguna pantalla de vacantes.
- Comparación visual de QA: la portada de la evaluación en 1280 px y 390 px frente a la
  maqueta aprobada.
- Resultado que quiero comprobar personalmente: ver la portada sin plazo; crear una vacante
  con fecha límite cercana y comprobar en el panel que se cierra sola y que quien no terminó
  queda cerrado.

## Dependencias y dudas

- Requiere: nada pendiente de otra rama.
- Ya decidido con el usuario (20/10/2026):
  - Sin plazo de 14 días: el Perfil Integral dura hasta que la vacante se cierra.
  - «Cómo se cierra» con dos opciones: Permanente y Fecha límite.
  - Sin avisos ni textos nuevos; el candidato no ve fechas.
  - Solo el primer recordatorio.
  - Maqueta de la portada aprobada (versión 4).
  - No se reabren las postulaciones ya cerradas por `PLAZO_VENCIDO`.
  - Al desplegar se cierra a quien sigue con el Perfil Integral abierto en vacantes ya
    cerradas.
  - Las vacantes publicadas con una fecha ya pasada pasan a Permanente.
- Documentación que actualizar tras la aprobación: `docs/03-ESTADOS-POSTULACION.md` («Se
  cierra la convocatoria», recordatorios y lo que el sistema hace sin persona, que gana el
  cierre de la vacante por fecha) y `docs/07-DICCIONARIO-DE-DATOS.md` (`tipo_cierre`,
  `cierra_en`).
- Decisiones pendientes: ninguna.
