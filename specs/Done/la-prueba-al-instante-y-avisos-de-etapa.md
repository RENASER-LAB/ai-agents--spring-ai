# La prueba al instante, la campana en cada etapa y los recordatorios
Estado: TERMINADO

## Objetivo

Que el candidato sepa cuándo le toca hacer algo aunque no tenga el portal abierto, y que no
tenga que esperar cuando no hay nada que esperar. Hoy muchos se pierden entre una etapa y la
siguiente: entregan el banco, ven «Te avisaremos cuando avance», cierran la página y no vuelven.

Cuatro cambios:

- **Parte A — la prueba al instante.** En las vacantes con pase automático, el candidato ya no
  espera a que la IA califique su CV y su banco: al entregar el banco (o al postular, si la vacante
  no tiene banco) la prueba del puesto se le abre en ese mismo momento. La IA sigue calificando en
  el servidor y la nota aparece en el panel cuando termina.
- **Parte B — la campana en cada etapa.** Todo cambio de etapa que hoy manda correo deja también
  un aviso en la campana del portal.
- **Parte C — los recordatorios.** Si el candidato no ha entregado el banco o no ha empezado la
  prueba, recibe un recordatorio al día siguiente y otro antes de que venza el plazo.
- **Parte D — el correo de la prueba sale de verdad.** Comprobar que el aviso de «tu prueba está
  disponible» llega a la bandeja del candidato, con sus datos completos.

El bot de WhatsApp que avisa de lo mismo va en una spec aparte, después de esta (ver
«Dependencias y dudas»).

## Contexto

### Comportamiento actual

**El camino del banco a la prueba** (vacante con banco):

1. El candidato entrega el banco → pasa a «Perfil Integral · calificando».
2. La IA califica el CV y las respuestas abiertas en segundo plano (minutos; si falla, reintenta).
3. Al terminar → «Perfil Integral · por confirmar».
4. Si la vacante tiene **pase automático** (`vacante.calificacion_automatica`) y prueba montada,
   `PaseAutomatico` la pasa sola a «Prueba · turno del candidato» y sale el correo
   `PRUEBA_DISPONIBLE`. Si no, espera a que alguien del equipo confirme.

Sin banco, al postular va directo a «por confirmar» y, con pase automático, se encola la nota
del CV; el pase llega al terminar esa nota.

- **El pase automático no mira la nota.** Sus guardas son: vacante con pase automático, no
  archivada ni eliminada, banco entregado (si lo lleva) y prueba montada. Con el pase activado
  pasan todos; la nota solo decide el grupo de prioridad del ranking. La espera no filtra a nadie.
- **La calificación ya contempla que el candidato se haya movido.** Al terminar,
  `PuenteCalificacionIaImpl.cerrarPerfilIntegral` guarda la nota siempre y solo mueve la
  postulación si sigue en el Perfil Integral.
- **Si la IA nunca termina, el candidato se queda atascado.** Un CV escaneado (4 de cada 116) o
  la IA apagada lo dejan en «calificando» sin que nadie lo vea.
- **Al entregar el banco** el portal solo dice «Evaluación entregada. Te avisaremos cuando
  avance.» y lo devuelve a la página del proceso.

**Los avisos:**

- **Correo.** `MaquinaEstados.avisarAlCandidato` manda correo al entrar en un estado que espera al
  candidato (`PRUEBA_DISPONIBLE` para la prueba, `POSTULACION_AVANZA` para las demás) y al cerrar
  (`POSTULACION_NO_CONTINUA`, `POSTULACION_CERRADA`, `RETIRO_CONFIRMADO`). Si sale de verdad
  depende de `CORREO_TRANSPORTE` en el servidor (`log` solo lo anota, `smtp` lo envía).
- **El plazo del correo de la prueba.** `{{plazo}}`, que se rellena en `MaquinaEstados` al
  armar las variables de `PRUEBA_DISPONIBLE`, solo dice los minutos de una cronometrada o los
  días de un plazo abierto:
  - nunca dice la fecha límite, que desde el editor nuevo es la que cierra la prueba;
  - en una prueba «Sin cronómetro», que ya no tiene días, sale **vacío**;
  - el texto original (V29) dice «Tienes {{plazo}} desde este correo.», y eso ya no es verdad:
    el cronómetro arranca al abrir la prueba, no al recibir el correo. La versión activa en
    producción (v3) se editó con `textos-de-correo.py` y hay que leerla para saber si aún lo
    dice.
- **Campana del portal** (`ServicioAvisosPortal`, V56). Existe y se ve en el portal
  (`src/ui/Campana.tsx` y el contador de «Mis procesos»), pero solo recibe tres cosas: cambios en
  la vacante, vacante eliminada y reseñas. **Ningún cambio de etapa llega a la campana.**
- **Recordatorios: no hay ninguno.** Lo único que corre solo es `SondeoVencimientos` (cada 60 s),
  que cierra lo vencido: un banco sin entregar a su `vence_en` termina en «No continúa» por
  `PLAZO_VENCIDO`.

### Decisiones tomadas con el usuario (07/10/2026)

1. **Dos specs:** esta, sin dependencias externas, y el bot de WhatsApp después.
2. **Con pase automático, la prueba se abre al instante** y la nota se calcula por detrás.
   Sustituye la idea de una pantalla que espera unos minutos y salta sola.
3. **Entran los recordatorios**, por correo y campana.
4. Quedan fuera por ahora: el panel de «quién se quedó parado» con el embudo por etapa, y el aviso
   al equipo cuando una vacante sin prueba tiene gente esperando.

### Decisiones de diseño de esta spec

5. **La campana recibe lo mismo que el correo, ni más ni menos.** Si la transición manda correo,
   deja aviso; si se movió «sin avisar» (`AvisoDeLaTransicion.NINGUNO`), no deja nada. El cierre
   por vacante eliminada (`POR_LA_CAMPANA`) sigue con su aviso propio y no se duplica.
6. **Dos recordatorios como máximo por turno:** uno a las 24 h si no ha entregado el banco o no
   ha empezado la prueba, y otro 24 h antes de que venza el plazo. Si los dos caen a menos de 12 h
   el uno del otro, solo sale el del plazo.
7. **Los recordatorios no salen de noche:** solo entre las 8:00 y las 21:00, hora de Lima. Si
   tocaba fuera de esa franja, sale a las 8:00.
8. **Los recordatorios se pueden apagar por empresa** con un parámetro (encendidos por defecto).
   Las horas (24 y 24) también son parámetros.
9. **Quien se movió «sin avisar» no recibe recordatorios** de ese turno: el equipo decidió no
   contárselo y un recordatorio se lo contaría.

### Pantallas o procesos afectados

- Portal: entrega del banco, postular, portada de la prueba y del cuestionario técnico, campana.
- Panel: ficha del candidato y ranking (la nota del perfil «en camino»).
- Backend: entrega del banco, postulación, máquina de estados, sondeo programado, textos de correo.

### Referencias

- [Estados de postulación](../../docs/03-ESTADOS-POSTULACION.md).
- [Plazo de la prueba del puesto](plazo-de-la-prueba-del-puesto.md): qué plazo rige
  (fecha de la vacante, fecha propia o reloj).
- `scripts/textos-de-correo.py`: lista y muestra los textos de correo de producción.

## Alcance

- Incluye:
  - **Parte A:** abrir la prueba al instante en vacantes con pase automático, con la nota por
    detrás; mostrar «nota en camino» en el panel mientras tanto.
  - **Parte B:** aviso en la campana en cada transición que manda correo.
  - **Parte C:** dos recordatorios (correo y campana) para el banco y la prueba del puesto, con
    dos textos de correo nuevos y editables.
  - **Parte D:** comprobar que `PRUEBA_DISPONIBLE` sale completo; que `{{plazo}}` diga el
    tiempo y la fecha límite de la prueba de cada vacante; corregir el texto que lo rodea si
    choca; y verificarlo en producción.
- Queda fuera:
  - El bot de WhatsApp (spec aparte).
  - Recordatorios de simulación, validación y decisión.
  - El panel de «quién se quedó parado», el embudo por etapa y el aviso al equipo de vacantes sin
    prueba.
  - Cambiar las vacantes **sin** pase automático: ahí el equipo sigue confirmando.
  - Notificaciones push de la app Android y SMS.

## Comportamiento esperado

### Parte A — la prueba al instante

1. **Cuándo aplica:** la vacante tiene pase automático, no está archivada ni eliminada, tiene
   prueba montada (prueba del puesto o cuestionario técnico) y el candidato ya hizo lo suyo
   (entregó el banco, o la vacante no lleva banco).
2. **Al entregar el banco:** la postulación pasa en ese mismo momento a «Prueba · turno del
   candidato» y se crea su prueba, igual que hoy lo hace el pase automático. La calificación con
   IA se encola como hoy.
3. **Al postular sin banco:** si cumple los requisitos, pasa en ese momento a la prueba. La nota
   del CV se encola como hoy. Si no cumple un requisito, «No continúa» como hoy.
4. **El portal lo lleva a la portada de su prueba** (o del cuestionario técnico) con el mensaje
   «Tu prueba del puesto ya está disponible». Abrir la portada no arranca el reloj: arranca al
   confirmar, como hoy.
5. **Cuando la IA termina**, la nota del Perfil Integral y el grupo de prioridad se guardan y
   aparecen en el panel. El candidato no se mueve.
6. **Mientras tanto**, la ficha y el ranking del panel muestran la nota del Perfil Integral como
   «en camino», no como vacía ni como 0.
7. **Si la IA falla todos sus intentos**, el candidato sigue en su prueba; el panel muestra el
   estado de «no se pudo calificar» que ya tiene hoy.
8. **Si no aplica** (vacante sin pase automático o sin prueba montada), todo sigue como hoy: el
   candidato espera y el portal dice «Evaluación entregada. Te avisaremos por correo y en la
   campana cuando te toque la prueba.». El pase automático de siempre, al terminar la nota, sigue
   existiendo para ese caso.
9. **El historial** de la ficha muestra los pasos con un motivo legible, por ejemplo «Pase
   automático al entregar: la nota se calcula después».
10. **Un solo correo:** `PRUEBA_DISPONIBLE`. Los pasos intermedios no mandan nada, como hoy.

### Parte B — la campana en cada etapa

11. Cada transición que manda correo deja también un aviso en la campana del candidato, ligado a
    su proceso: al pulsarlo, abre ese proceso.
12. El aviso dice lo mismo que el correo en corto. Por ejemplo, «Tu prueba del puesto está
    disponible · Administrador de tienda», o «Te toca la simulación · …».
13. El correo y el aviso son independientes: si uno falla, el otro sale igual y la transición no se
    deshace.
14. Movido «sin avisar»: ni correo ni aviso. Vacante eliminada: el aviso propio de hoy, uno solo.

### Parte C — los recordatorios

15. **Banco** («Perfil Integral · turno del candidato»):
    - a las 24 h de entrar en el turno, si no lo ha entregado: «Aún no has respondido tu
      evaluación para {{vacante}}»;
    - 24 h antes de su vencimiento, si no lo ha entregado: «Tu evaluación para {{vacante}} vence
      el {{vence}}».
16. **Prueba del puesto** («Prueba · turno del candidato»):
    - a las 24 h de entrar en el turno, si no la ha empezado;
    - 24 h antes del plazo que rija (fecha de la vacante o fecha propia), si no la ha empezado.
      Si ya la empezó, no se le recuerda: está dentro.
17. Cada recordatorio sale por correo y campana, una sola vez por turno, y lleva la dirección
    de su proceso en el portal, que pide su correo y su contraseña: **sin enlace personal**, como
    dice el punto 24. *(Corregido el 15/10/2026: decía «el enlace de acceso al portal como los
    avisos de hoy», se implementó así y los recordatorios salieron con un enlace que abría la
    cuenta sin contraseña.)*
18. **No sale si:** ya entregó o empezó, cambió de estado, la postulación se cerró, la vacante se
    archivó o se eliminó, se le movió «sin avisar», o la empresa los tiene apagados.
19. Si los dos recordatorios caen a menos de 12 h, solo sale el del plazo. Si la hora cae entre
    las 21:00 y las 8:00 de Lima, sale a las 8:00.
20. Reiniciar el servidor no los repite: queda registrado qué recordatorio salió para qué turno.
21. Los textos son dos plantillas de correo nuevas (`RECORDATORIO_EVALUACION` y
    `RECORDATORIO_PRUEBA`), editables como las demás y visibles en `textos-de-correo.py`.

### Parte D — el correo de la prueba

22. `PRUEBA_DISPONIBLE` sale con `{{nombre}}`, `{{vacante}}` y `{{plazo}}` rellenos, tanto en el
    pase al instante como cuando confirma una persona, y sin ninguna `{{…}}` a la vista.
23. **`{{plazo}}` se arma con los datos de la prueba de esa vacante** y con el plazo que rija para
    esa persona. La plantilla es una sola para todas las vacantes; lo que cambia es la frase que
    pone el sistema:

    | La prueba de la vacante | Lo que dice `{{plazo}}` |
    |---|---|
    | Cronometrada, con fecha límite | «90 minutos desde que la empieces, hasta el vie 10/10 a las 23:59» |
    | Sin cronómetro, con fecha límite | «hasta el vie 10/10 a las 23:59» |
    | Cronometrada, sin fecha (publicada antes del editor nuevo) | «90 minutos desde que la empieces» |
    | Plazo abierto con días, sin fecha (publicada antes del editor nuevo) | «7 días desde que la empieces» |

    - **La fecha es la que rija para esa persona:** su plazo propio si lo tiene y, si no, la fecha
      límite de la vacante. Cuando sale el correo, su intento ya existe y esa fecha es su
      `vence_en`.
    - **La fecha va en hora de Lima**, con el mismo formato que `{{vence}}` de los recordatorios
      (día abreviado, día/mes y hora).
    - **Nunca sale vacío.** Si una prueba no tuviera ni minutos, ni días, ni fecha, dice «sin
      fecha límite».
    - **Las vacantes con `PLANTILLA` o `CUESTIONARIO_TECNICO`** siguen rellenándolo como hoy.
24. **El texto que rodea a `{{plazo}}` tiene que encajar con la frase nueva.** Al implementar se
    leen las versiones activas de `PRUEBA_DISPONIBLE` de cada organización y las plantillas
    propias que alguna vacante use para ese aviso (`plantilla_correo_vacante`, con
    `aviso_codigo = 'PRUEBA_DISPONIBLE'`). Si alguna dice «Tienes {{plazo}} desde este correo.» u
    otra frase que choque con la tabla del punto 23, se publica una versión nueva con la frase
    corregida. Las versiones viejas se conservan, como siempre que se edita un texto. Se hace con
    una migración que solo toca los textos que contienen esa frase, para que valga igual en local,
    en los tests y en producción. El texto de referencia, aprobado por el usuario el 07/10/2026:

    ```
    Asunto: Tu prueba del puesto para {{vacante}}

    Hola {{nombre}}:

    Pasaste a la prueba del puesto para «{{vacante}}». ¡Enhorabuena!

    Tienes {{plazo}}.

    Para hacerla, entra en el portal con tu correo y tu contraseña y abre «Mis procesos».
    Ahí verás la prueba y podrás subir lo que te pida.

    Si tienes cualquier duda, responde a este correo.

    Equipo de Talento — Renaser
    ```

    - Así se lee la línea del plazo: «Tienes 90 minutos desde que la empieces, hasta el vie 10/10
      a las 23:59.» con cronómetro, y «Tienes hasta el vie 10/10 a las 23:59.» sin él.
    - La línea de cómo entrar al portal conserva lo que diga la v3 de producción (por ejemplo, la
      dirección de entrada si la lleva escrita). Sin enlace personal: el usuario quitó los enlaces
      de los correos el 03/10/2026.
    - Ni enunciado adjunto ni WhatsApp: en la prueba del editor el enunciado y los archivos están
      en el portal.
25. En producción, el usuario comprueba que el correo llega a una bandeja real (ver Verificación).

## Referencia visual

No aplica. Se reutilizan la campana, la portada de la prueba y el aviso flotante del portal; en
el panel solo cambia el texto de la nota pendiente («en camino»).

## Criterios de aceptación

### Parte A

- **AC-1:** Vacante con pase automático, banco y prueba del puesto: al entregar el banco, en la
  misma respuesta la postulación queda en «Prueba · turno del candidato», existe su prueba y el
  portal muestra la portada con el reloj sin arrancar.
- **AC-2:** Igual con una vacante sin banco: al postular, cumpliendo los requisitos, queda en la
  prueba.
- **AC-3:** Igual con una vacante que rinde el cuestionario técnico.
- **AC-4:** La calificación con IA corre igual. Al terminar, la nota y el grupo aparecen en el
  panel y la postulación sigue en la prueba (o donde esté).
- **AC-5:** Con la nota pendiente, la ficha y el ranking muestran «en camino», no un 0 ni un vacío
  sin explicación.
- **AC-6:** Si la IA agota sus intentos, el candidato sigue en la prueba y el panel muestra el
  fallo como hoy.
- **AC-7:** Vacante con pase automático **sin** prueba montada, o sin pase automático: el
  comportamiento es el de hoy y el portal dice que se le avisará.
- **AC-8:** Sale un solo correo, `PRUEBA_DISPONIBLE`. El historial muestra el pase con su
  motivo.
- **AC-9:** Un requisito objetivo incumplido sigue llevando a «No continúa», sin prueba.

### Parte B

- **AC-10:** Cada transición que manda correo (te toca, no continúa, cerrada, retiro) deja un
  aviso en la campana ligado al proceso; el contador de «Mis procesos» lo cuenta.
- **AC-11:** Movido «sin avisar»: ni correo ni aviso. Vacante eliminada: un solo aviso, el de hoy.
- **AC-12:** Si el correo falla, el aviso sale; si el aviso falla, el correo sale; la transición se
  queda hecha en los dos casos.

### Parte C

- **AC-13:** Banco sin entregar a las 24 h: un recordatorio por correo y campana, una vez.
- **AC-14:** Banco sin entregar a 24 h de vencer: un recordatorio, una vez.
- **AC-15:** Prueba sin empezar a las 24 h y a 24 h del plazo: un recordatorio cada uno. Si ya la
  empezó, ninguno.
- **AC-16:** Ningún recordatorio en los casos del punto 18.
- **AC-17:** Si los dos caen a menos de 12 h, solo sale el del plazo; de 21:00 a 8:00 de Lima no
  sale ninguno y el pendiente sale a las 8:00.
- **AC-18:** Reiniciar el servidor no repite ningún recordatorio.
- **AC-19:** Las dos plantillas nuevas existen para todas las empresas (o caen a las de la
  plataforma) y se pueden editar.

### Parte D

- **AC-20:** `PRUEBA_DISPONIBLE` se registra como enviado con nombre, vacante y plazo rellenos,
  en el pase al instante y en el manual.
- **AC-21:** Para cada fila de la tabla del punto 23, el correo registrado lleva esa frase en
  `{{plazo}}`, con la fecha en hora de Lima. Con plazo propio, lleva la fecha de la persona y no
  la de la vacante. En ningún caso queda vacío.
- **AC-22:** Tras la migración, ninguna versión activa de `PRUEBA_DISPONIBLE` (ni la plantilla
  propia de una vacante para ese aviso) dice «desde este correo», y las versiones anteriores
  siguen guardadas.

### Toda la spec

- **AC-23:** La suite `verify` pasa entera, sin bajar la cobertura.

## Casos límite y errores

- **La IA termina antes de que acabe la entrega** (carrera): la guarda de hoy decide; la
  postulación no vuelve a «por confirmar».
- **Dos pestañas entregan a la vez:** la segunda entrega falla como hoy; no se crean dos pruebas.
- **El equipo mueve al candidato mientras la IA trabaja:** como hoy, la nota se guarda y no se le
  mueve.
- **La empresa quita el pase automático después:** a quien ya está en la prueba no se le toca.
- **Plazo de la prueba ya vencido al entrar** (fecha de la vacante pasada): no hay recordatorio
  de plazo; lo cierra el sondeo como hoy.
- **El candidato no tiene correo entregable** (cuentas creadas por el cargador de CV): el correo
  usa la dirección de hoy (`DireccionDelCandidato`) y la campana sale igual.
- **Cambio de plazo después del recordatorio:** si la fecha se mueve, el recordatorio de plazo
  puede volver a salir para la fecha nueva, una vez.

## Datos y compatibilidad

### Qué hay que guardar

- Qué recordatorio salió para qué postulación y en qué turno, para no repetirlo (tabla o columnas
  nuevas; lo decide quien implemente).
- Dos plantillas de correo nuevas y los parámetros de recordatorios (encendido, horas).
- Tipos nuevos de aviso de la campana.

### Registros existentes

- Las postulaciones que hoy esperan en «por confirmar» en vacantes con pase automático no se
  mueven solas por esta spec.
- Los turnos que ya llevan más de 24 h cuando se despliegue esto **no** reciben un recordatorio
  de golpe: solo cuentan los turnos que empiecen después del despliegue. Así se evita una ráfaga
  de correos el primer día.

### Trampas conocidas

- **El pase y la creación de la prueba van juntos o no van.** `PaseAutomatico` explica por qué:
  si se separan, un fallo entre medias deja la prueba creada con el candidato fuera de la etapa.
- **La máquina de estados avanza de uno en uno.** Si el pase encadena «calificando → por
  confirmar → prueba», solo el último paso puede mandar correo y aviso.
- **La IA tiene que calificar aunque el candidato ya no esté en el Perfil Integral.** Hoy nada lo
  impide; un test debe fijarlo.
- **Las plantillas nuevas de V61 solo se sembraron en la plataforma**, y las empresas existentes
  caen a ella. Hay que comprobar que `ServicioCorreo` hace esa caída para los códigos nuevos; si
  no, sembrarlas en todas.
- **`--listar` del script de textos marca «con/SIN enlace» mirando solo `{{enlace}}`.**
- **El número de migración** puede chocar con otra spec pendiente (la de pesos también migra).
  Se usa el que toque al implementar.
- **Fechas en Lima:** «8:00» y «21:00» son hora de Lima; comparar en UTC sin convertir desplaza la
  franja 5 horas.

### Restricciones de compatibilidad

- Nada cambia para las vacantes sin pase automático, salvo la campana y los recordatorios.
- Los textos de correo existentes no se tocan, salvo la frase del plazo de `PRUEBA_DISPONIBLE`
  (punto 24), que se corrige con una versión nueva sin borrar las anteriores.

## Verificación

- Tests de integración para AC-1 a AC-22; los recordatorios se prueban fijando la hora, sin
  esperar 24 h.
- Antes de escribir la migración del punto 24, leer el texto activo en producción con
  `python3 scripts/textos-de-correo.py --ver PRUEBA_DISPONIBLE` (y `--vacante N --aviso
  PRUEBA_DISPONIBLE` si alguna vacante usa un texto propio), porque la v3 se editó a mano y no
  está en ninguna migración.
- E2E 1: candidato en una vacante con pase automático entrega el banco y cae en la portada de la
  prueba; la campana tiene el aviso.
- E2E 2: vacante sin pase automático: entrega el banco, ve el mensaje de espera; el equipo
  confirma; aparece el aviso en la campana.
- **Verificación del usuario en producción** (hay login): con una cuenta de prueba, postular a
  una vacante con pase automático, entregar el banco y comprobar que la prueba se abre al
  instante, que la campana avisa y que el correo `PRUEBA_DISPONIBLE` llega a la bandeja (no a
  spam), con el plazo bien escrito (la frase de la tabla del punto 23 y la fecha límite de esa
  vacante). Si no llega, revisar que el servidor tenga `CORREO_TRANSPORTE=smtp`; la columna
  `estado_entrega` de `correo_enviado` dice `NO_ENVIADO` cuando el servidor solo lo anota.

## Dependencias y dudas

- **Spec siguiente: el bot de WhatsApp.** Decidido con el usuario el 07/10/2026: avisa solo
  cuando le toca hacer algo (no rechazos ni cierres) y usa el número 928439930. El candidato le
  escribe con un código ya puesto en el mensaje; el bot responde «tu prueba estará lista pronto,
  te avisaremos por aquí» y luego avisa por plantilla aprobada de Meta. Antes de empezarla, el
  usuario tiene que dejar lista la cuenta de WhatsApp Business Platform de Meta (verificación del
  negocio, el número dado de alta y la plantilla aprobada).
- `specs/editor-de-la-prueba-mas-simple.md` mueve la fecha de cierre a la configuración de la
  prueba. El recordatorio de plazo usa «el plazo que rija», venga de donde venga.
