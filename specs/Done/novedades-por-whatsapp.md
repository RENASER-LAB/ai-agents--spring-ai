# Novedades de la postulación por WhatsApp

Estado: TERMINADO (13/10/2026; PR #121 backend y #83 portal; arreglos de Sonar en el PR #122)

## Objetivo

Que el candidato se entere de cómo va su proceso, y de que le toca algo, aunque no tenga el
portal ni el correo abiertos.

Mientras espera, el portal le ofrece «Recibe tus novedades por WhatsApp». Si pulsa «Escribir por
WhatsApp», **él escribe primero** al bot de RENASER desde el celular de su cuenta, y el bot le
contesta con el estado de su proceso. Desde ese momento, cuando le toque hacer algo (por ejemplo,
«¡Felicidades! Tu prueba del puesto ya está disponible»), el bot también se lo dice. El bot lo
reconoce **solo por el número**: no hay códigos y da igual lo que escriba.

Para eso, el celular pasa a ser un dato **obligatorio al crear la cuenta** y editable en «Mi
cuenta».

Es una **prueba beta solo para las vacantes de RENASER**. Toca dos proyectos:

- **EX** (este repositorio y RenaserOsPostulantes): el celular en la cuenta, el bloque del
  portal, el vínculo con el número, las novedades que se cuentan, los avisos que se mandan y el
  texto legal.
- **El CRM** (`/home/n4nd0/Documentos/Renaser/crm`, repo RENASER-LAB/crm), en el servicio de
  conversación de la rama `new-chatbot-harness`: el bot que atiende a quienes llegan desde anuncios y
  landings de Meta aprende a reconocer postulantes por su número, a contarles sus novedades y a
  enviar los avisos que le pida EX.

## Contexto

### Comportamiento actual en EX

- **Registro.** `Registro.tsx` (portal web y app de Android, que comparten `src/`) y
  `POST /api/v1/portal/cuentas` (`DtosPortal.CrearCuenta`) piden nombre, apellidos, correo,
  contraseña, ciudad y los consentimientos. **No piden celular.**
- **Teléfono.** `persona.telefono` existe y nadie lo escribe; el teléfono que la IA saca del CV es
  texto libre y no sirve para esto. Ninguna cuenta tiene hoy celular.
- **«Mi cuenta».** En la barra del portal lleva a la pantalla del perfil. No hay ningún sitio
  donde editar el teléfono.
- **El texto legal** (consentimiento `PLATAFORMA` 1.0, V54, y la página pública
  `PoliticaPublica.tsx`) dice: «Al crear la cuenta: mi nombre, mis apellidos, mi correo y la
  provincia donde vivo. Después, si decido darlos: mi teléfono, …», y que los datos se usan
  «para escribirme sobre el estado de mis procesos». No menciona WhatsApp.
- **La espera del portal.** En «Mis procesos» y en el detalle del proceso, cuando el candidato
  no tiene nada que hacer, el recuadro de espera (`src/paginas/procesos/Seguimiento.tsx`,
  textos en `MOMENTOS` de `src/dominio/estados.ts`) dice qué pasa y «No tienes que hacer
  nada.», sin ningún botón. Hay once momentos así, desde «Estamos revisando tu postulación»
  hasta «Decisión final en curso». En «Estamos revisando tu postulación» la frase «No tienes que
  hacer nada.» sale dos veces.
- **Los textos de espera viven solo en el portal.** El servidor no sabe decir «en qué va» un
  proceso con las palabras de la pantalla.
- **Avisos al candidato.** Toda transición pasa por `MaquinaEstados.transicionar`. Las que
  avisan llaman a `avisarAlCandidato`, que manda el correo (`PRUEBA_DISPONIBLE` al pasar a la
  prueba; `POSTULACION_AVANZA` cuando le toca al candidato; y los de cierre) y después deja el
  aviso en la campana. Los movidos «sin avisar» no avisan.
- **Integraciones.** EX no tiene ninguna ruta para que otro sistema le consulte nada. Sus
  cadenas de seguridad son la del portal (token de candidato) y la del panel (token de equipo).
- **Consentimientos.** Son textos versionados (`texto_consentimiento`, tipos `PLATAFORMA`,
  `PROCESO` y `FUTUROS_CONTACTOS`). Quien firmó una versión queda ligado a ella.
- **RENASER** es la organización con `es_plataforma = true`.
- **Otro número de WhatsApp.** El correo `PRUEBA_DISPONIBLE` usa `{{whatsapp}}` con el parámetro
  `whatsapp_evidencia` (982255360), para mandar evidencias. No es el del bot y no se toca.

### Comportamiento actual en el CRM (rama `new-chatbot`)

La parte del CRM se construye sobre la rama `new-chatbot-harness`, que sale de `new-chatbot`
(decisión del 13/10/2026; `new-chatbot` no se toca) y **no está en producción**. Lo de abajo
describe `new-chatbot` (`origin/new-chatbot`, 33940a3, 09/10/2026).

- **Pila.** El CRM de Spring sigue como en `main`. A su lado, un servicio de conversación en
  Python (`conversation-service`: FastAPI, psycopg, base propia con esquema `conversacion`) y su
  bandeja de atención (`conversation-ui`). El CI solo prueba y despliega `main`: no corre ni
  construye el servicio de Python.
- **Número.** El bot usa el **51928439930**. Un solo número.
- **Quién atiende depende de la fase del número**, no de cada persona
  (`deploy/chatbot-corte-retorno.md`):
  - en LEGADO, SOMBRA, PREPARADO y RETORNO, el webhook va a Java y Java envía (hoy está en
    LEGADO);
  - en NUEVO, Meta manda los mensajes al servicio de Python, que es el único que envía, y Java le
    pide sus envíos con `POST /v1/envios`.
- **Entrada en Python.** Guarda cada mensaje una sola vez (por cuenta y `wamid`) y en ese momento
  anota los enlaces de campaña `(c:…)` y la palabra BAJA. Después, por turnos de conversación,
  decide en este orden: audios y adjuntos → quien llega desde redes → botones e inscripción a
  eventos (el slug se busca como subcadena del texto) → IA.
- **Un postulante que escriba hoy** lo atendería la IA de eventos, saldría en la bandeja y en las
  métricas, y podría mandar su nombre y ciudad al CRM de Java como observaciones.
- **Salida en Python.** Todo envío pasa por la misma política al aceptarlo y otra vez al mandarlo
  (`politica.py`): número en NUEVO, baja, pausa (solo para respuestas), atención humana, 30 por
  hora y conversación, autorización según la finalidad, plantilla aprobada en su registro y
  ventana de 24 h. Cualquier mensaje entrante deja obsoletos los envíos pendientes de esa
  conversación. No hay tope diario de destinatarios (el tier de 250 solo existe en Java).
- **`POST /v1/envios`** existe, pero es solo para Java: pide referencias de conversación y
  mensaje que EX no tiene, y se autentica con una credencial de servicio (Bearer con scopes) que
  caduca a los 30 días.
- **Plantillas.** Se aprueban en Meta como siempre (`meta/plantillas/`) y además se registran a
  mano en el servicio (`plantilla_autorizada`), con sus variables y su finalidad. Hay cinco de
  utilidad aprobadas, todas de eventos. Meta rechaza al instante las que dicen «código»,
  «acceso», «verificación» o «PIN». El idioma tiene que ser `es`.
- **BAJA.** Python solo reconoce la palabra exacta «BAJA» (Java también acepta «stop», «dar de
  baja», «darme de baja» y «no me escribas»). Es global e irreversible, y hoy no se comunica a
  Java.
- **Convenciones.** Spec Kit (siguiente número libre: 007), migraciones del servicio en SQL
  numerado con su propio runner (la siguiente es la 017), contratos `openapi-*.yaml` generados
  con `conversation-service/tools/exportar_openapi.py`, configuración con prefijo
  `CONVERSATION_*`, errores `{code, message, correlation_id, retryable}`, UUIDv7 y Conventional
  Commits sin líneas de atribución.
- **La rama choca con `main` en Flyway:** usa V20–V26 y `main` ya tiene V20–V23 (y su V26 es
  idéntica a la V21 de `main`).
- **Para pasar a NUEVO falta** (`specs/002-chatbot-independiente/CONTINUAR.md` y `piloto.md`): el
  error 131042 de Meta (pago de la cuenta, abierto al 01/10/2026: con él no sale ninguna
  plantilla), los secretos en el servidor, el dominio del servicio, el texto de privacidad, un
  ciclo en SOMBRA, el ensayo de vuelta atrás y la aprobación del responsable del piloto. El piloto
  dice además que el servicio «no inicia conversaciones por su cuenta» y que solo habla con el
  equipo. **No hay fecha.**

### Pantallas o procesos afectados

- Portal de EX: el registro, «Mi cuenta», el recuadro de espera de «Mis procesos» y del detalle
  del proceso, y la página pública de privacidad.
- Backend de EX: el alta de cuenta, el teléfono de la persona, la máquina de estados (avisos),
  una cadena de seguridad nueva para la integración, el borrado de datos, los textos de espera y
  el texto legal.
- CRM (servicio de conversación): el turno, la política, tres rutas nuevas para EX, el registro de
  plantillas, la bandeja y las métricas.

### Referencias

- `specs/Done/la-prueba-al-instante-y-avisos-de-etapa.md`: avisos de etapa, campana y
  recordatorios.
- `specs/umbral-del-perfil-integral.md`: decide cuándo se pasa a la prueba del puesto (nota
  mínima) y también publica una versión nueva del texto `PLATAFORMA`.
- CRM, rama `new-chatbot`: `specs/002-chatbot-independiente/` (`CONTINUAR.md`, `piloto.md`,
  `contracts/integracion-v1.md`), `deploy/chatbot-corte-retorno.md`,
  `.specify/memory/constitution.md` (2.0.0) y `conversation-service/src/renaser_conversacion/`.

## Alcance

**Incluye:**

- **EX, la cuenta:**
  - el celular obligatorio en el registro (web y app);
  - verlo y cambiarlo en «Mi cuenta»;
  - pedirlo en el bloque de WhatsApp a las cuentas que ya existen y no lo tienen;
  - la versión nueva del texto legal y de la página de privacidad.
- **EX, portal:** el bloque de WhatsApp en el recuadro de espera, con sus estados, el
  consentimiento y «Dejar de recibir». Y quitar la frase repetida de «Estamos revisando tu
  postulación».
- **EX, backend:**
  - el anuncio del número al bot y el vínculo con cada postulación;
  - los textos de espera pasan al servidor para que el bot diga lo mismo que la pantalla;
  - una integración firmada para el bot: vincular, contar novedades y dar de baja;
  - la cola de avisos hacia el bot, con reintentos;
  - el interruptor de la beta y la configuración del bot;
  - el borrado del número con el resto de datos.
- **CRM, en el servicio de conversación de la rama `new-chatbot-harness`:** reconocer postulantes por su
  número, contarles sus novedades, recibir y enviar los avisos de EX, dos plantillas de utilidad,
  mantener a los postulantes fuera de eventos y ventas, y avisar a EX de las bajas.
- El contrato entre los dos, escrito igual en los dos repositorios.

**Queda fuera:**

- Vacantes de otras empresas: solo RENASER mientras dure la beta. (El celular en la cuenta sí es
  para todos.)
- Que el bot escriba primero: nunca escribe a un número que no le haya escrito antes.
- Recordatorios por WhatsApp (siguen por correo y campana), avisos de rechazo o de cierre, y
  cualquier aviso que no sea «te toca».
- Conversación libre con IA para postulantes: el bot solo cuenta novedades y da un texto fijo
  para lo demás.
- Que el equipo de RR.HH. conteste a los candidatos desde la bandeja del CRM.
- Obligar a las cuentas que ya existen a dar su celular al entrar.
- Verificar el celular con un SMS o un código: lo verifica el propio mensaje de WhatsApp.
- La nota mínima para pasar a la prueba del puesto (`specs/umbral-del-perfil-integral.md`).
- El bot de Java de `main`: no se le añade nada.
- Pasar el número a NUEVO: es trabajo de la spec 002 del CRM.

## Comportamiento esperado

### El celular de la cuenta

1. **Registro.** El formulario gana el campo **«Celular (WhatsApp)»**, obligatorio, con +51
   delante. Perú: 9 cifras que empiezan por 9. De otro país, se escribe completo con su prefijo.
   Si no vale: «Escribe un celular de 9 cifras que empiece por 9, o el número completo con su
   prefijo». Se guarda en `persona.telefono` en formato internacional (E.164). El backend lo exige
   también (400 sin él o con un formato que no vale).
2. **«Mi cuenta».** Muestra «Celular: +51 987 654 321» con «Cambiar», que abre el campo con la
   misma validación.
3. **Cuentas que ya existen.** No se les obliga al entrar. Si pulsan el bloque de WhatsApp sin
   celular, el bloque les pide el número (punto 6) y se guarda en su cuenta.
4. **Cambiar el celular** cierra los vínculos y anuncios del número anterior (EX avisa al bot con
   `/revocaciones`). En sus procesos en espera, el bloque vuelve a ofrecer «Escribir por
   WhatsApp» desde el número nuevo.

### El recorrido del candidato

5. **Ve la opción mientras espera.** En cualquiera de los once momentos de espera, si la vacante
   es de RENASER y la beta está encendida, el recuadro de espera muestra debajo una tarjeta:
   - «Recibe tus novedades por WhatsApp»;
   - «Escríbenos desde tu celular **+51 987 654 321** y te contaremos cómo va tu proceso.»;
   - el botón verde «Escribir por WhatsApp»;
   - «¿No es tu número? Cámbialo en Mi cuenta.»;
   - el consentimiento: «Al escribirnos aceptas recibir por WhatsApp las novedades de este
     proceso. Puedes dejarlo cuando quieras.».

   Sale en la tarjeta de «Mis procesos» y en el detalle, porque los dos usan el mismo recuadro.
   Cuando le toca hacer algo, no sale: manda la acción.
6. **Sin celular en la cuenta** (cuentas anteriores a esta spec), la tarjeta pide «Tu celular
   (WhatsApp)» con la validación del punto 1, y el botón es «Guardar y escribir por WhatsApp».
7. **Pulsa «Escribir por WhatsApp».** EX guarda su consentimiento (texto, versión y fecha) y le
   **anuncia al bot** que ese número va a escribir para esa postulación. Se abre WhatsApp (la app
   en el móvil, WhatsApp Web en escritorio) con el chat del bot y un mensaje ya escrito:

   ```
   Hola, soy Ana Pérez. Quiero saber cómo va mi postulación a Asistente Contable.
   ```

   El título de la vacante se corta a 60 caracteres. **Puede cambiar el mensaje o borrarlo
   entero:** lo único que cuenta es que escriba desde ese número.
8. **Envía el mensaje.** El bot ve que el número estaba anunciado, EX lo vincula a esa postulación
   y el bot contesta al momento con su novedad, con las mismas palabras del portal:

   ```
   ¡Hola, Ana! Ya estamos conectados.
   Tu postulación a Asistente Contable: Estamos calificando tus respuestas. Suele tardar poco.
   Escríbenos cuando quieras para saber cómo va. Y cuando te toque hacer algo, te lo diremos por
   aquí. Si quieres dejar de recibir estos mensajes, escribe BAJA.
   ```
9. **Vuelve al portal.**
   - Si ya escribió: «Conectado por WhatsApp (+51 9•• ••• 321). Escríbenos cuando quieras para
     saber cómo va tu proceso.», con «Dejar de recibir».
   - Si pulsó el botón y todavía no escribió: «Aún no recibimos tu mensaje. Escríbenos desde el
     +51 987 654 321, el celular de tu cuenta.», con «Escribir por WhatsApp» y «¿Usas otro
     número? Cámbialo en Mi cuenta.».
10. **Le toca algo.** Cuando su postulación pasa a un momento en que le toca a él (la prueba del
    puesto disponible, elegir fecha de simulación, empezar su validación), además del correo y la
    campana le llega por WhatsApp. Solo pasa si ya escribió antes. Por ejemplo, al pasar a la
    prueba:

    ```
    ¡Felicidades, Ana! Tu prueba del puesto para Asistente Contable ya está disponible.
    Entra en el portal con tu correo y tu contraseña y abre «Mis procesos».
    ```

    Si han pasado más de 24 h desde su último mensaje, WhatsApp no deja mandar texto libre: sale
    la plantilla aprobada equivalente.
11. **Escribe cuando quiera.** Cualquier mensaje suyo desde el número vinculado recibe la novedad
    de cada proceso activo que tenga vinculado. Si pregunta otra cosa, el bot añade: «Para otras
    dudas sobre tu proceso, responde al último correo que te enviamos.».
12. **Deja de recibir.** Puede hacerlo con «Dejar de recibir» en el portal o escribiendo «BAJA»
    al bot.
    - Desde el portal: se desvincula esa postulación y el bot deja de avisarle de ella.
    - Con «BAJA» en WhatsApp (solo esa palabra, como hoy): rige la
      regla del CRM, que suprime ese número para todo y para siempre. El CRM se lo comunica a EX,
      que desvincula todas las postulaciones de ese número. En el portal sale «Este número se dio
      de baja de nuestros mensajes» y no se vuelve a ofrecer el botón con ese número.
13. **El proceso termina.** Cuando la postulación llega a un estado final, el vínculo se cierra
    sin avisar por WhatsApp (los cierres no van por aquí). Si escribe después, el bot contesta:
    «No tienes procesos activos con avisos por WhatsApp. Puedes ver todo en el portal, en «Mis
    procesos».».

### Reglas del número y del vínculo

14. **Solo el número.** Al pulsar el botón, EX anuncia al bot el celular de la cuenta para esa
    postulación (`POST /v1/ex/esperas`). El primer mensaje que llegue desde ese número, diga lo
    que diga, vincula todas las postulaciones anunciadas con ese número. No hay códigos.
15. **El anuncio dura 24 horas.** Si no escribe en ese tiempo, caduca; volver a pulsar el botón lo
    renueva. Así, un número mal escrito no queda esperando.
16. **Sin anuncio no hay vínculo.** Si escribe desde otro número, el bot no puede saber que es un
    postulante y lo atiende como hoy. El estado pendiente del portal le recuerda desde qué número
    escribir.
17. **Un número puede vincular varias postulaciones.** Las novedades las cuenta todas.
18. **Solo RENASER y solo con la beta encendida.** Con la beta apagada, o en vacantes de otra
    empresa, EX no anuncia, no vincula y no manda avisos.

### Qué avisos van por WhatsApp

19. Solo los «te toca»: el paso a la prueba del puesto (`PRUEBA_DISPONIBLE`) y los avances en
    que espera al candidato (`POSTULACION_AVANZA`). Ni recordatorios, ni cierres, ni rechazos, ni
    los movidos «sin avisar».
20. Salen en la misma transición que el correo y la campana, y son independientes: si falla uno,
    los otros salen igual y la transición no se deshace.
21. EX no manda dos veces el mismo aviso para la misma transición, aunque reintente.
22. Si el aviso no se puede entregar (sin plantilla, número suprimido, número que aún no está en
    NUEVO, tope por hora), el candidato lo tiene igualmente por correo y campana. No se reintenta
    por WhatsApp más allá del límite.

### El contrato entre EX y el bot

Es el mismo que el de la spec 007 del CRM (`specs/007-novedades-de-postulantes/spec.md`, rama
`new-chatbot-harness`): si uno cambia, cambian los dos. En EX se describe en `docs/`.

**Reglas comunes:**

- `telefono` va siempre en E.164 con `+` (`+51987654321`).
- `postulacionRef` es una referencia opaca de EX (no el id interno ni el UUID del portal), de
  hasta 64 caracteres.
- Límites: `nombre` y `vacante` hasta 60 caracteres, `titulo` hasta 80, `novedades[].texto`
  hasta 300, el `texto` de un aviso o de un error hasta 1000, y como mucho 8 novedades por
  respuesta. El `texto` de `/vinculos` puede venir vacío.
- Todas las llamadas van firmadas en las dos direcciones (ver «Datos y compatibilidad»). El bot
  responde sus errores con su formato (`{code, …}` en minúsculas): `401 firma_invalida`,
  `400 validacion`. EX responde los de `/vinculos` con `{codigo, texto}`.
- Un `5xx`, un `429`, un `401` (puede ser un cambio de secreto a medias), un tiempo agotado o un
  error de red son reintentables; otro `4xx` es definitivo.

**EX llama al bot** (`/v1/ex/…` del servicio de conversación; van bajo `/v1/` porque es lo que
enruta su Caddy):

- `POST /esperas` con `{telefono, postulacionRef, caducaEn, consentimiento: {tipo, version, texto,
  aceptadoEn}}` al pulsar el botón: el bot apunta que ese número va a escribir. `caducaEn` es 24 h
  después; `consentimiento` es el que acaba de aceptar (tipo `WHATSAPP`, su versión, el texto
  exacto y la fecha). Responde `204`; repetido, renueva la caducidad y el consentimiento.
- `POST /avisos` con `{avisoId, telefono, postulacionRef, tipo, nombre, vacante, texto}`.
  `avisoId` (UUIDv7) es la clave de idempotencia; `tipo` es `PRUEBA_DISPONIBLE` o
  `POSTULACION_AVANZA`; `nombre` es el nombre de pila; `vacante`, el título cortado a 60
  caracteres; `texto`, el mensaje completo para la ventana abierta. Responde `202 {avisoId}`;
  repetido con el mismo contenido, igual; con otro contenido, `409 aviso_distinto`.
- `POST /revocaciones` con `{telefono, postulacionRef, motivo}`, con `motivo` en
  `DEJAR_DE_RECIBIR`, `CAMBIO_CELULAR`, `PROCESO_TERMINADO` o `BORRADO_DATOS`. Anula también el
  anuncio, si lo había. Responde `204`, aunque no hubiera nada que cerrar.

**El bot llama a EX** (`/api/v1/integraciones/whatsapp/…`):

- `POST /vinculos` con `{telefono, wamid, texto, recibidoEn}`: vincula las postulaciones
  anunciadas para ese número. Responde:
  - `200` con `{postulaciones: [{postulacionRef}], nombre, novedades: [{vacante, titulo,
    texto}]}` si vincula, o si ya estaba vinculado. Es idempotente: `postulaciones` son todas las
    postulaciones activas vinculadas a ese número después de la llamada (también las que ya lo
    estaban), `novedades` las de todos esos procesos, y repetida devuelve lo mismo;
  - `404 {codigo: "SIN_ANUNCIO", texto}`, `409 {codigo: "PROCESO_TERMINADO", texto}` o
    `409 {codigo: "BETA_APAGADA", texto}`, donde `texto` es lo que el bot contesta tal cual.
- `POST /novedades` con `{telefono}`: `200 {novedades: [{vacante, titulo, texto}]}` con cada
  proceso activo vinculado a ese número, o la lista vacía. Es `POST` para que el teléfono no vaya
  en la URL ni quede en los registros de acceso.
- `POST /bajas` con `{telefono}`: desvincula todas las postulaciones de ese número y lo marca
  como suprimido. Responde `204`, también si no había nada que desvincular.

En las novedades, `texto` ya trae las palabras del portal (punto 26) y es lo que el bot pone tras
«Tu postulación a {vacante}: »; `titulo` es el nombre corto del momento.

### Lo que hace EX

23. **El celular en la cuenta.** `CrearCuenta` gana `telefono` obligatorio con la validación del
    punto 1. Un `PUT /api/v1/portal/cuenta/telefono` lo cambia (y dispara el punto 4).
    `persona.telefono` se guarda siempre en E.164.
24. **Botón.** `POST /api/v1/portal/postulaciones/{uuid}/whatsapp` (candidato dueño de la
    postulación): exige vacante de RENASER, beta encendida, que la postulación no haya terminado
    y que la cuenta tenga celular (si no, `409 SIN_CELULAR` y el portal muestra el punto 6);
    guarda el consentimiento; anuncia el número al bot (`/esperas`, con reintentos si no
    responde); devuelve el enlace `wa.me` completo y el estado. `DELETE` en la misma ruta es
    «Dejar de recibir».
25. **Estado en el portal.** `MiPostulacion` gana `whatsapp: {disponible, estado, numero}`, con
    `estado` en `SIN_CELULAR`, `SIN_VINCULAR`, `PENDIENTE`, `ACTIVO`, `REVOCADO` o `SUPRIMIDO`. El
    número va completo en `SIN_VINCULAR` y `PENDIENTE` (es el de su cuenta) y enmascarado en
    `ACTIVO`.
26. **Textos de espera en el servidor.** Los títulos y textos de los once momentos de espera, y
    el de cada «te toca», pasan a un único sitio del backend. El portal y el bot dicen lo mismo.
    El portal puede seguir pintándolos desde su copia mientras los dos coincidan, y una prueba
    lo comprueba.
27. **Cola de avisos.** En `avisarAlCandidato`, después del correo y la campana, si el aviso es
    de los del punto 19 y la postulación tiene un vínculo `ACTIVO`, se guarda un aviso pendiente
    ligado a esa transición. Lo envía, después del commit, un sondeo que reintenta con espera
    creciente hasta 5 veces y luego lo da por fallido. Se registra el resultado.
28. **Cierre del vínculo.** Al llegar a un estado final, al «Dejar de recibir», al cambiar el
    celular y al borrar los datos del candidato, el vínculo se cierra y EX avisa al bot con
    `/revocaciones`.
29. **Borrado de datos.** El borrado de datos del candidato borra también su celular y sus
    vínculos.
30. **Texto legal.** Una versión nueva del consentimiento `PLATAFORMA` y de la página pública:
    - «Al crear la cuenta: mi nombre, mis apellidos, mi correo, mi celular y la provincia donde
      vivo. Después, si decido darlos: mi documento de identidad, …» (el teléfono sale de la
      lista de «después»);
    - «para escribirme sobre el estado de mis procesos por correo, en el portal y, si yo escribo
      primero, por WhatsApp desde el número de Renaser».

    Si la spec del umbral ya publicó la 1.1, esta es la 1.2. Quien firmó una versión anterior
    queda ligado a ella.

### Lo que hace el CRM (en el servicio de conversación de `new-chatbot-harness`)

Lo define la spec 007 del CRM (`specs/007-novedades-de-postulantes/spec.md`, rama
`new-chatbot-harness`), que implementa el harness de ese repositorio. Este harness no la construye ni la
verifica. En resumen:

31. Un interruptor `CONVERSATION_EX_MODE` (`disabled` por defecto, `fake`, `real`); apagado, el
    bot es el de hoy.
32. Guarda los números anunciados y solo consulta a EX por ellos o por los ya vinculados.
33. Un paso nuevo, el primero del turno, contesta a los postulantes con textos fijos: sin IA, sin
    atención humana y sin mandar nada al CRM de Java. Con vínculo activo gana la postulación,
    salvo botones, respuestas a plantillas o Flows, y enlaces de campaña.
34. Envía los avisos de EX: texto con la ventana abierta y al menos 1 h de margen; si no, las
    plantillas `prueba_disponible_postulante` y `avance_postulacion`. Salen aunque el bot esté en
    pausa, no se anulan porque el candidato escriba y tienen un tope de 5 por postulación y día.
35. Si EX no responde: «Ahora no podemos consultar tu proceso. Inténtalo en unos minutos o revisa
    el portal.».
36. Las conversaciones de postulantes no salen en la bandeja por defecto (filtro «Postulantes») ni
    en las métricas.
37. La baja sigue siendo solo «BAJA», como hoy, y además avisa a EX con `POST /bajas`,
    reintentando.

## Referencia visual

Maqueta: https://claude.ai/artifact/VQpVCBbGzviFHQseZhz7f9, filas «Portal · WhatsApp en la
espera» (escritorio interactivo, los estados del bloque, móvil y el registro) y «WhatsApp · la
conversación con el bot». Aprobada por el usuario el 12/10/2026. El boceto de abajo resume la tarjeta; el detalle
usa el mismo recuadro.

```
┌──────────────────────────────────────────────────────────────┐
│ Estamos calificando tus respuestas                           │
│ Se juntan tu currículum y tu evaluación en un solo perfil.   │
│ Suele tardar poco. No tienes que hacer nada.                 │
│ ┌ Recibe tus novedades por WhatsApp ───────────────────────┐ │
│ │ Escríbenos desde tu celular +51 987 654 321 y te          │ │
│ │ contaremos cómo va tu proceso.                            │ │
│ │ [ (verde) Escribir por WhatsApp ]  ¿No es tu número?      │ │
│ │                                    Cámbialo en Mi cuenta. │ │
│ │ Al escribirnos aceptas recibir por WhatsApp las novedades │ │
│ │ de este proceso. Puedes dejarlo cuando quieras.           │ │
│ └───────────────────────────────────────────────────────────┘ │
└──────────────────────────────────────────────────────────────┘
```

- **Estados:**
  - sin vincular, como el boceto;
  - sin celular en la cuenta: el campo «Tu celular (WhatsApp)» y «Guardar y escribir por
    WhatsApp»;
  - número inválido: el mensaje de error junto al campo;
  - pendiente: «Aún no recibimos tu mensaje. Escríbenos desde el +51 987 654 321, el celular de tu
    cuenta.», con «Escribir por WhatsApp» y «¿Usas otro número? Cámbialo en Mi cuenta.»;
  - conectado: «Conectado por WhatsApp (+51 9•• ••• 321). Escríbenos cuando quieras para saber
    cómo va tu proceso.» y el enlace «Dejar de recibir»;
  - suprimido: «Este número se dio de baja de nuestros mensajes», sin botón;
  - cargando y con error al pulsar: el botón se desactiva mientras se prepara, y si falla sale
    «No pudimos preparar el enlace. Inténtalo de nuevo.».
- **Registro:** el campo «Celular (WhatsApp)» va después del correo, con +51 delante.
- **Escritorio y móvil:** la tarjeta ocupa el ancho del recuadro; en móvil el botón ocupa todo el
  ancho. El enlace se abre en una pestaña nueva.
- **Diseño existente:** el `DESIGN.md` del portal (repositorio RenaserOsPostulantes; `frontend`
  en el worktree del harness). La tarjeta es blanca con borde gris, y el único verde de la
  pantalla es «Escribir por WhatsApp», relleno del verde de WhatsApp (`#25D366`) con el texto en
  tinta oscura (el blanco sobre ese verde no llega al contraste mínimo) y el logotipo de
  WhatsApp delante, en el mismo color del texto. Es una
  excepción a «la regla de la voz única» de DESIGN.md, decidida por el usuario el 12/10/2026: el
  verde dice «WhatsApp» y no compite con el índigo de lo que le toca al candidato.
- **Puede adaptarse** el tamaño del texto de consentimiento y la posición de «¿No es tu número?».

## Criterios de aceptación

### EX

- **AC-01:** Dado el registro, cuando se envía sin celular o con uno que no vale, entonces no se
  crea la cuenta y el campo dice el error; con un celular válido, queda guardado en E.164. La API
  responde 400 en los mismos casos.
- **AC-02:** Dado «Mi cuenta», cuando el candidato cambia su celular, entonces queda guardado, sus
  vínculos y anuncios del número anterior se cierran y EX llama a `/revocaciones`.
- **AC-03:** Dado un candidato de una vacante de RENASER en un momento de espera, con celular y la
  beta encendida, cuando abre «Mis procesos» o el detalle, entonces ve la tarjeta con su número,
  «Escribir por WhatsApp» y el consentimiento.
- **AC-04:** Dada una vacante de otra empresa, o la beta apagada, o un momento en que le toca al
  candidato, entonces la tarjeta no sale.
- **AC-05:** Dada una cuenta sin celular, cuando pulsa la tarjeta, entonces se le pide el celular,
  se guarda en su cuenta y sigue el flujo normal.
- **AC-06:** Dada la tarjeta, cuando pulsa «Escribir por WhatsApp», entonces se guarda su
  consentimiento, se anuncia su número al bot para esa postulación y se abre `wa.me/51928439930`
  con el mensaje del punto 7.
- **AC-07:** Dado un número anunciado, cuando el bot llama a `POST /vinculos` desde ese número con
  cualquier texto, entonces queda vinculado y la respuesta trae la novedad con el mismo título y
  texto que el portal. Desde un número no anunciado, `404 SIN_ANUNCIO`.
- **AC-08:** Dado un vínculo activo, cuando la postulación pasa a la prueba del puesto, entonces
  se guarda un aviso para el bot, sale una sola vez aunque se reintente, y el correo y la
  campana salen igual.
- **AC-09:** Dado un candidato que pulsó el botón pero nunca escribió, cuando la postulación pasa a
  la prueba, entonces no se manda nada al bot.
- **AC-10:** Dado un vínculo activo, cuando hay una transición de cierre, un recordatorio o un
  movimiento «sin avisar», entonces no se manda nada al bot.
- **AC-11:** Dado que el bot no responde, cuando se intenta enviar un aviso, entonces se
  reintenta con espera creciente hasta 5 veces y queda como fallido, sin tocar la transición.
- **AC-12:** Dado un vínculo activo, cuando el candidato pulsa «Dejar de recibir», cuando la
  postulación termina o cuando se borran sus datos, entonces el vínculo se cierra y EX llama a
  `/revocaciones`.
- **AC-13:** Dado un `POST /bajas` del bot, entonces todas las postulaciones de ese número quedan
  desvinculadas y el portal muestra «Este número se dio de baja de nuestros mensajes».
- **AC-14:** Dada una llamada a `/api/v1/integraciones/whatsapp/…` sin firma, con firma mala o
  con una marca de tiempo de más de 5 minutos, entonces responde 401 y no hace nada.
- **AC-15:** Dado el borrado de datos de un candidato, entonces no queda su celular ni sus
  vínculos.
- **AC-16:** Dada una cuenta nueva, cuando acepta el consentimiento, entonces firma la versión
  nueva de `PLATAFORMA`, que nombra el celular y WhatsApp; la página pública dice lo mismo.
- **AC-17:** «Estamos revisando tu postulación» dice «No tienes que hacer nada.» una sola vez.

### CRM

Los criterios del CRM están en su spec 007 (AC-01 a AC-24). Este harness no los verifica.

## Casos límite y errores

- **El candidato cambia o borra el mensaje:** se vincula igual, porque cuenta el número.
- **Escribe desde otro número:** el bot no puede reconocerlo y lo atiende como hoy. El portal
  sigue en «Aún no recibimos tu mensaje», con el número de su cuenta y «¿Usas otro número?
  Cámbialo en Mi cuenta.».
- **Celular mal escrito en la cuenta:** nadie escribe desde él y el anuncio caduca a las 24 h. Si
  justo en ese tiempo escribiera al bot la persona dueña de ese número, recibiría el nombre de
  pila, la vacante y la etapa del candidato. El riesgo es bajo y lo acota la caducidad corta; el
  pendiente muestra el número para que el candidato lo corrija.
- **Dos cuentas con el mismo celular:** se permite (una familia que comparte teléfono). Ese número
  recibe las novedades de las postulaciones que cada cuenta vinculó.
- **Proceso terminado entre el botón y el mensaje:** `409 PROCESO_TERMINADO` con el texto del
  punto 13.
- **Doble envío del mismo mensaje:** el ingreso del servicio es idempotente por cuenta y `wamid`,
  y `POST /vinculos` también.
- **Doble clic en «Escribir por WhatsApp»:** un solo anuncio vigente; el consentimiento se guarda
  una vez por postulación y versión de texto.
- **Usuario sin permisos:** el portal solo deja pedir el enlace al dueño de la postulación (404
  si no lo es). Las rutas de integración solo aceptan la firma del bot.
- **EX caído:** el bot da el texto del punto 35. Los avisos esperan en la cola de EX.
- **CRM caído:** EX reintenta los avisos (punto 27) y los anuncios (punto 24); el candidato tiene
  correo y campana. Si el anuncio no llegó cuando el candidato escribe, el bot no lo reconoce:
  volver a pulsar el botón lo arregla.
- **Error 131042 o plantilla no aprobada:** los avisos fuera de la ventana no salen; quedan
  auditados en el CRM.
- **El bot está en pausa:** no contesta a nadie, tampoco a postulantes; los avisos de EX sí
  salen (punto 34).
- **El número sigue en LEGADO:** el servicio de Python no recibe ni envía nada. El botón del
  portal no debe encenderse hasta que el número esté en NUEVO (interruptor de la beta apagado).
- **Una vacante deja de ser de RENASER o se apaga la beta:** no se ofrece el botón ni se mandan
  avisos nuevos; los vínculos activos siguen recibiendo novedades si escriben.

## Datos y compatibilidad

### EX

- **Migración**, la siguiente libre (V73 si la del umbral, que va antes, toma la V72):
  - `persona.telefono` en E.164 con un `CHECK` de formato; sigue admitiendo nulo para las cuentas
    que ya existen. Las cuentas nuevas lo traen siempre.
  - `vinculo_whatsapp`: postulación, teléfono (E.164), estado, fecha del anuncio, consentimiento
    (texto, versión, fecha, IP), `wamid` del primer mensaje, fechas de vínculo y de cierre, y
    motivo del cierre. Un solo vínculo vivo por postulación.
  - `aviso_whatsapp`: id (el `avisoId`), postulación, transición, tipo, estado (`PENDIENTE`,
    `ENVIADO`, `FALLIDO` u `OMITIDO`), intentos, último error y fechas. Único por transición y
    tipo.
  - El texto de consentimiento nuevo, tipo `WHATSAPP` versión 1.0, solo para RENASER (el `CHECK`
    de `texto_consentimiento.tipo` se amplía), y la versión nueva de `PLATAFORMA` (punto 30).
  - Parámetro `whatsapp_novedades_activo` (booleano), solo para RENASER, en `false`.
- **Configuración** (no en la base): número del bot (`51928439930`), URL base del servicio de
  conversación, y dos secretos, uno por dirección, en los secretos del despliegue.
- **Firma**, igual en las dos direcciones: cabeceras `X-Renaser-Marca` (segundos Unix) y
  `X-Renaser-Firma` = HMAC-SHA256 en hexadecimal de `marca + "." + ruta + "." + cuerpo`, donde
  `ruta` es el path sin host ni query (por ejemplo `/api/v1/integraciones/whatsapp/vinculos`) y
  `cuerpo` son los bytes exactos del cuerpo; comparada en tiempo constante; se rechaza una marca
  que se aleje más de 5 minutos de la hora del servidor. Un secreto por dirección.
- **Seguridad:** una cadena nueva para `/api/v1/integraciones/**` que solo acepta esa firma. Hoy
  la cadena por defecto deja pasar lo que no es portal ni panel: la ruta nueva no puede caer en
  ella.
- **Registros existentes:** ninguna cuenta tiene celular y ninguna postulación tiene vínculo. El
  bloque aparece al encender la beta y pide el celular a quien no lo tenga.
- **Pruebas que cambian:** `Registro.test.tsx` y los E2E y unitarios que crean cuentas (todos
  tienen que mandar un celular), `ServicioCuentaPortalImplTest` y `PoliticaPublica.test.tsx`.

### CRM (servicio de conversación)

Lo define la spec 007 del CRM: su migración 017, su configuración (`CONVERSATION_EX_*`) y el
reconocimiento de las bajas. No hay ninguna migración de Flyway.

## Verificación

- **Actor y datos:**
  - una cuenta nueva (con celular) y una anterior (sin celular);
  - una vacante de RENASER con la beta encendida y un candidato en «Estamos calificando tus
    respuestas»;
  - una vacante de otra empresa con un candidato esperando;
  - para los E2E, EX con un bot falso: un doble local del servicio de conversación que acepta
    las llamadas firmadas a `/v1/ex/*` y deja ver qué esperas, avisos y revocaciones recibió (EX
    no arranca el servicio real del CRM);
  - dos números reales del equipo para la prueba de punta a punta.
- **E2E repetibles en headless** (EX, número siguiente libre):
  - registrarse sin celular (error) y con celular;
  - cambiar el celular en «Mi cuenta» y ver la revocación en el servicio falso;
  - ver la tarjeta, pulsar «Escribir por WhatsApp» y comprobar el enlace, el mensaje y el anuncio;
  - la cuenta sin celular: dar el número desde la tarjeta;
  - simular la llamada firmada del bot a `/vinculos` desde el número anunciado y ver el estado
    conectado en el portal;
  - pasar la postulación a la prueba y comprobar que se encola un solo aviso;
  - «Dejar de recibir» y ver que llega la revocación;
  - la vacante de otra empresa sin tarjeta.
- **CRM:** lo verifica el harness del CRM con su spec 007.
- **QA con navegador:** el registro y la tarjeta en móvil y escritorio, el enlace en WhatsApp Web,
  y los estados sin celular, pendiente, conectado y suprimido.
- **Comprobación personal**, con los dos números del equipo (hay inicio de sesión en el portal,
  así que la hace el usuario):
  1. crear una cuenta con el celular, postular y pulsar el botón;
  2. borrar el mensaje ya escrito, escribir otra cosa y recibir la novedad;
  3. pasar la postulación a la prueba desde el panel y recibir el «felicidades»;
  4. esperar más de 24 h y repetir con otra postulación para ver la plantilla;
  5. escribir «BAJA» y ver el portal.

## Dependencias y dudas

- **Requiere:**
  - **Que el 51928439930 pase a NUEVO** (spec 002 del CRM). Hasta entonces la parte del CRM se
    puede construir y probar, pero no llega a ningún candidato: el botón del portal sigue
    apagado. (El celular en el registro sí puede salir antes.)
  - En Meta: resolver el error 131042 y que aprueben las dos plantillas. Sin eso, solo salen los
    avisos dentro de la ventana de 24 h.
  - Un dominio estable para el servicio de conversación, que es al que llama EX (hoy no tiene).
  - `specs/umbral-del-perfil-integral.md`, que va antes: con ella, el «felicidades» solo le llega
    a quien alcanza la nota mínima.
- **Decisiones tomadas el 12/10/2026:**
  - solo vacantes de RENASER (beta); EX guarda el número; el número del bot es el 51928439930;
  - por WhatsApp van los «te toca» y el «felicidades», sin recordatorios;
  - la parte del CRM va sobre la rama `new-chatbot-harness` (13/10/2026; `new-chatbot` no se
    toca);
  - el celular es obligatorio al crear la cuenta, editable en «Mi cuenta», con el texto legal
    cambiado;
  - el candidato escribe primero y se le reconoce solo por el número, sin códigos;
  - el único verde de la pantalla es el botón, con el logotipo de WhatsApp;
  - el texto legal nuevo vale para las cuentas nuevas; a quien firmó una versión anterior no se
    le pide aceptar otra vez;
  - **se construye ya**, con el interruptor de la beta apagado, para que todo esté listo cuando el
    número pase a NUEVO; ese día se enciende;
  - la parte del CRM la coordina el propio usuario.
- **Propuestas de esta spec** (cámbialas si no te sirven): el anuncio dura 24 h; no se obliga a
  las cuentas existentes a dar el celular al entrar; el consentimiento se guarda al pulsar el
  botón; BAJA global, solo con la palabra «BAJA» (13/10/2026); texto fijo para otras dudas; sin aviso de entrega de
  vuelta a EX; los avisos salen aunque el bot esté en pausa; con vínculo activo gana la
  postulación salvo botones, respuestas a plantillas o Flows y enlaces de campaña (la inscripción escrita no cuenta; ver la spec 007 del CRM); firma HMAC en las dos direcciones (la rama
  usa credenciales Bearer que caducan a los 30 días, y para dos servidores un secreto compartido
  es más simple).
- **Cómo se implementa:** el harness de este repositorio hace la parte de EX (backend y portal).
  La parte del CRM la hace el harness de su repositorio con su spec 007 (solo `spec.md`), sobre
  `new-chatbot-harness`, sin tocar `new-chatbot` ni fusionar `main` (decisión del 13/10/2026), con el contrato de esta spec
  como fuente.
- **Decisiones pendientes:** ninguna.
