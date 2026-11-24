# Contrato: novedades de la postulación por WhatsApp (EX ↔ bot)

Lo que se dicen EX (este backend) y el bot de RENASER (el servicio de conversación del CRM, rama
`new-chatbot-harness`). Repite «El contrato entre EX y el bot» de `specs/Done/novedades-por-whatsapp.md`,
que es el mismo que el de la spec 007 del CRM (`specs/007-novedades-de-postulantes/spec.md`). Si
uno cambia, cambian los dos, y este documento con ellos.

Es el contrato **v1, el que está activo**. La ampliación —el bot consulta la ficha, el
procedimiento y el resultado, y EX le manda los resultados finales— es el
[contrato v2](CONTRATO-CONTEXTO-Y-RESULTADOS-POR-WHATSAPP-V2.md), que hereda de aquí la firma,
los límites, los errores y los reintentos, y **no está activo** hasta su C-10.

## La idea en una línea

El candidato enciende WhatsApp en «Notificaciones» (`V92`; antes, «Escribir por WhatsApp» en cada
proceso). Si su número ya está comprobado —esta cuenta escribió alguna vez desde él—, EX le pide al
bot que **vincule sin mensaje** todos sus procesos vivos. Si no, EX le **anuncia** al bot ese número
para cada proceso vivo; el candidato escribe primero desde él (diga lo que diga), el bot le pregunta
a EX, EX lo **vincula** y le da sus novedades. Desde entonces EX le manda al bot los **avisos** de «te toca»,
y le cuenta cuando el vínculo se **revoca**. Si el candidato escribe «BAJA» (solo esa palabra), el
bot se lo cuenta a EX.

## Reglas comunes

- `telefono` va siempre en E.164 con `+` (`+51987654321`).
- `postulacionRef` es una referencia **opaca** de EX, de hasta 64 caracteres. No es el id
  interno ni el uuid del portal. EX la escribe como `pw_` y 32 cifras hexadecimales, una por
  vínculo: si el candidato deja de recibir y vuelve a pulsar, o cambia de celular, nace otra.
- **Límites:**

  | Campo | Hasta |
  |---|---|
  | `nombre` | 60 caracteres |
  | `vacante` | 60 caracteres |
  | `titulo` | 80 caracteres |
  | `novedades[].texto` | 300 caracteres |
  | `texto` de un aviso o de un error | 1000 caracteres |
  | `novedades` por respuesta | 8 |

  El `texto` de `/vinculos` puede venir vacío (un Flow o un adjunto). EX corta con «…» lo que
  pase de su límite y, con más de 8 procesos vinculados, cuenta los 8 más recientes.
- Fechas (`caducaEn`, `aceptadoEn`, `recibidoEn`): ISO 8601 con zona (`2026-10-13T15:04:05Z`).
- **Errores.** Los del bot llegan con su formato: `{code, message, correlation_id, retryable}`,
  con `code` en minúsculas (`401 firma_invalida`, `400 validacion`, `409 aviso_distinto`). Los
  de EX llevan `{codigo, texto}`: los de `/vinculos` (abajo), `400 VALIDACION` en cualquier ruta
  con un cuerpo que no vale y `401 FIRMA_INVALIDA` sin firma válida.
- **Reintentos.** Un `5xx`, un `429`, un `401` (puede ser un cambio de secreto a medias), un
  tiempo agotado o un error de red son reintentables. Cualquier otro `4xx` es definitivo.

## La firma (las dos direcciones)

Toda llamada lleva dos cabeceras:

| Cabecera | Valor |
|---|---|
| `X-Renaser-Marca` | Segundos Unix del momento en que se manda, en texto: `1760000000` |
| `X-Renaser-Firma` | HMAC-SHA256 en hexadecimal de `marca + "." + ruta + "." + cuerpo` |

- `ruta` es el path completo, sin host ni query: `/v1/ex/avisos` hacia el bot y
  `/api/v1/integraciones/whatsapp/vinculos` hacia EX. La firma de una ruta no vale en otra.
- `cuerpo` son los bytes exactos del cuerpo HTTP.
- Se compara en tiempo constante. Una marca que se aleje más de **5 minutos** de la hora del que
  recibe, hacia atrás o hacia delante, se rechaza.
- **Un secreto por dirección.** Lo que EX manda al bot se firma con
  `WHATSAPP_SECRETO_HACIA_EL_BOT` en EX, que es `CONVERSATION_EX_SECRETO_ENTRADA` en el bot. Lo que
  el bot manda a EX, con `CONVERSATION_EX_SECRETO_SALIDA` en el bot, que es
  `WHATSAPP_SECRETO_DESDE_EL_BOT` en EX. Si uno se filtra, el otro lado sigue cerrado.
- Sin firma, con firma mala, de otra ruta o con la marca fuera de la ventana: **401** y no se
  hace nada.
- Vectores de prueba, con el secreto `un-secreto-de-pruebas-de-32-bytes!!`, la marca
  `1760000000` y el cuerpo `{"telefono":"+51987654321"}`:

  | Ruta | Firma |
  |---|---|
  | `/api/v1/integraciones/whatsapp/novedades` | `281f8f90bf130653a7b1a2b886a020a8beed745f7a73850c5f44aabaaa1ee1fd` |
  | `/v1/ex/esperas` | `e029b47a383ecd10d71d15b33c036b4a96a61da298ee2433c4d70dec27d45fdf` |

## EX llama al bot (`/v1/ex/…`)

Bajo `/v1/` porque es lo que enruta el Caddy del servicio. `Content-Type: application/json`.
Tiempo máximo de espera: 3 s.

| Ruta | Cuerpo | Respuesta |
|---|---|---|
| `POST /v1/ex/esperas` | `{telefono, postulacionRef, caducaEn, consentimiento: {tipo, version, texto, aceptadoEn}}` | `204`. Repetida, renueva la caducidad y el consentimiento. |
| `POST /v1/ex/avisos` | `{avisoId, telefono, postulacionRef, tipo, nombre, vacante, texto}` | `202 {avisoId}`. Repetida igual: `202 {avisoId}`. Repetida distinta: `409 aviso_distinto`. |
| `POST /v1/ex/revocaciones` | `{telefono, postulacionRef, motivo}` | `204`, aunque no hubiera nada que cerrar. |
| `POST /v1/ex/vinculaciones` (`V92`) | `{telefono, nombre, motivo, postulaciones: [{postulacionRef, vacante}], consentimiento: {texto, version, aceptadoEn}}` | `200 {postulaciones: [{postulacionRef}]}`. Si no, `404` con `code: "SIN_CONVERSACION"` o `409` con `code: "SUPRIMIDO"`, en el formato de error de siempre del bot. |

Con el productor v2 encendido (`WHATSAPP_PRODUCTOR_V2`, apagado por defecto), EX llama además a
`POST /v1/ex/resultados`: ver el [contrato v2](CONTRATO-CONTEXTO-Y-RESULTADOS-POR-WHATSAPP-V2.md),
C-4 a C-7.

### `POST /v1/ex/esperas` — ese número va a escribir

```json
{"telefono": "+51987654321", "postulacionRef": "pw_9f…", "caducaEn": "2026-10-14T15:04:05Z",
 "consentimiento": {"tipo": "WHATSAPP", "version": "1.0",
   "texto": "Al escribirnos aceptas recibir por WhatsApp las novedades de este proceso. Puedes dejarlo cuando quieras.",
   "aceptadoEn": "2026-10-13T15:04:05Z"}}
```

Cuando el candidato enciende WhatsApp con un número sin comprobar (una por cada proceso vivo; sin
procesos vivos, una sola con una referencia que no lleva postulación detrás) o, antes de la `V92`,
cuando pulsaba el botón de un proceso. `caducaEn` es **24 horas** después. `consentimiento` es el
que acaba de aceptar: tipo `WHATSAPP` —también el `WHATSAPP_CUENTA` de la `V92`, que para el bot es
el mismo permiso—, su versión, el texto exacto que vio y la fecha. Si el bot no
responde, EX lo reintenta (30 s, 2 min, 8 min, 32 min) mientras no caduque.

### `POST /v1/ex/avisos` — le toca algo

```json
{"avisoId": "0199d5a2-…-7…", "telefono": "+51987654321", "postulacionRef": "pw_9f…",
 "tipo": "PRUEBA_DISPONIBLE", "nombre": "Ana", "vacante": "Asistente Contable",
 "texto": "¡Felicidades, Ana! 🎉\n\n📌 *Asistente Contable*\nTu *prueba del puesto* ya está disponible.\n⏰ Puedes empezarla hasta el *20 de octubre a las 23:59* (hora de Lima).\n\n👉 https://…/procesos\nInicia sesión con tu correo y contraseña y abre «Mis procesos»."}
```

- `tipo`: `PRUEBA_DISPONIBLE` o `POSTULACION_AVANZA` y, desde el 18/11/2026 (`V89` de EX),
  `PRUEBA_MAS_TIEMPO`. Nunca recordatorios, cierres, rechazos ni movidos «sin avisar».
- **`PRUEBA_MAS_TIEMPO`**: el equipo le dio más tiempo para una prueba del puesto que no había
  completado. No nace de una transición sino de la fecha nueva, y sale una vez por prueba y fecha:
  dar más tiempo dos veces con la misma fecha no lo repite. Mismo cuerpo, misma firma y misma
  idempotencia que los otros dos; su `texto`:

  ```
  Hola, Beto 👋

  📌 *Analista de operaciones*
  Tienes *más tiempo* para tu prueba del puesto.
  ⏰ Hasta el *jue 20 nov, 18:00* (hora de Lima).

  👉 https://…/procesos
  Inicia sesión con tu correo y contraseña y abre «Mis procesos».
  ```

  La fecha lleva el año solo si no es el de hoy. ⚠️ **El bot todavía no acepta este tipo**: hasta
  que el bot de `Renaser/crm` lo aprenda y se despliegue (va a mano), el aviso se queda pendiente
  o `FALLIDO` en la cola de EX, sin afectar a lo demás. **Fuera de la ventana de 24 h** el bot usará la
  plantilla ya aprobada `prueba_disponible_postulante` («tu prueba ya está disponible», que es
  cierto) hasta que Meta apruebe `prueba_mas_tiempo_postulante`, que se pide en paralelo.
- **El `texto`** (desde el 19/10/2026, regla 19 de la spec de EX
  `contexto-y-resultados-por-whatsapp.md`) va en bloques con las negritas de WhatsApp: el saludo, la vacante en negrita tras 📌, lo que toca y, al final, el
  enlace fijo a «Mis procesos» con «Inicia sesión con tu correo y contraseña y abre «Mis
  procesos».». En `PRUEBA_DISPONIBLE` dice «Tu *prueba del puesto*» —«Tu *prueba técnica*» con el
  cuestionario técnico— y la línea ⏰ solo sale si EX sabe hasta cuándo puede empezarla y esa
  fecha no pasó (con el año, si no es el de hoy). `POSTULACION_AVANZA` lleva el título y el texto
  del momento que pinta el portal (`TextosDelMomento`, sin cambiarlos):

  ```
  Hola, Ana 👋

  📌 *Contador*
  Te toca: *{título del momento}*
  {texto del momento}

  👉 https://…/procesos
  Inicia sesión con tu correo y contraseña y abre «Mis procesos».
  ```

  Sin nombre, «¡Felicidades! 🎉» u «Hola 👋». El enlace es `WHATSAPP_ENLACE_MIS_PROCESOS`, igual
  para todos y **sin token**: si no es `https`, lleva usuario, parámetros (`?`) o fragmento (`#`),
  o pasa de 300 caracteres, la línea 👉 no sale y queda solo la de cómo entrar. Los tipos, la
  firma, el límite de 1000 caracteres y la plantilla de fuera de ventana no cambian.
- `avisoId` (UUIDv7) es la clave de idempotencia. EX reintenta **siempre con el mismo** y el mismo
  contenido; un `409 aviso_distinto` es definitivo y el aviso queda fallido.
- `nombre` es el nombre de pila; `vacante`, el título cortado a 60 caracteres; `texto`, el
  mensaje completo para la ventana de 24 h. Fuera de ella el bot usa la plantilla aprobada con
  `{{1}} = nombre` y `{{2}} = vacante`.
- EX solo manda avisos a vínculos **activos** (el candidato ya escribió). Reintenta hasta 5
  veces con espera creciente.

### `POST /v1/ex/revocaciones` — ese vínculo se acabó

```json
{"telefono": "+51987654321", "postulacionRef": "pw_9f…", "motivo": "DEJAR_DE_RECIBIR"}
```

`motivo`: `DEJAR_DE_RECIBIR` (desde el portal), `CAMBIO_CELULAR`, `PROCESO_TERMINADO` o
`BORRADO_DATOS`. Anula también el anuncio, si lo había. Tras una baja EX no revoca: fue el bot
quien se lo contó.

Apagar WhatsApp en «Notificaciones» (`V92`) es un `DEJAR_DE_RECIBIR` por cada vínculo vivo de la
persona: el número no queda dado de baja y volver a encenderlo es con un clic.

### `POST /v1/ex/vinculaciones` — vincular sin mensaje (`V92`)

```json
{"telefono": "+51987654321", "nombre": "Lucía", "motivo": "ACTIVACION",
 "postulaciones": [{"postulacionRef": "pw_9f…", "vacante": "Analista de Datos"}],
 "consentimiento": {"texto": "Al activarlo aceptas recibir por WhatsApp las novedades de tus procesos. Escribe BAJA cuando quieras dejarlo.",
   "version": "1.0", "aceptadoEn": "2026-11-24T15:04:05Z"}}
```

Solo con números que esa cuenta ya comprobó escribiendo al bot. La spec 034 del CRM es su pareja.

- `ACTIVACION`: encendió WhatsApp en el portal. Van sus postulaciones vivas sin vínculo activo (las
  que esperaban su mensaje, con su misma referencia). El bot levanta su BAJA si la tenía y le manda
  la plantilla `avisos_activados`. Con el `200`, EX saca el número de `whatsapp_baja`, deja
  `ACTIVO` cada postulación devuelta y enciende el interruptor. **La llamada va dentro de la
  transacción**: si el bot falla o no contesta, no cambia nada y el portal recibe `503
  NO_SE_PUDO_ACTIVAR`. Sin postulaciones que vincular no se llama (el bot pide de 1 a 100): el
  interruptor queda encendido y, si el número tenía la BAJA, la levanta la vinculación de su
  próxima postulación, que va entonces como `ACTIVACION`.
- `NUEVA_POSTULACION`: postuló con el interruptor encendido. Después del commit de la postulación,
  con su vínculo ya guardado como `PENDIENTE`; con el `200` pasa a `ACTIVO`. Si el bot falla, se
  queda `PENDIENTE` y el sondeo de siempre anuncia el número: la postulación no se frena.
- `404 SIN_CONVERSACION` (en `code`): el bot no conoce el número. EX cae al camino con mensaje:
  anuncia el número para cada proceso vivo y devuelve el enlace al chat.
- `409 SUPRIMIDO` (en `code`): solo con `NUEVA_POSTULACION` y el número dado de baja en el bot. El
  vínculo se queda `PENDIENTE`.
- Una referencia que el bot no devuelve (la tenía cerrada por `PROCESO_TERMINADO` o
  `BORRADO_DATOS`) no se vincula.

## El bot llama a EX (`/api/v1/integraciones/whatsapp/…`)

Solo con la firma del bot; ningún token de EX abre esta puerta.

| Ruta | Cuerpo | Respuesta |
|---|---|---|
| `POST /vinculos` | `{telefono, wamid, texto, recibidoEn}` | `200 {postulaciones: [{postulacionRef}], nombre, novedades: [{vacante, titulo, texto}]}`. Si no, `404 {codigo: "SIN_ANUNCIO", texto}`, `409 {codigo: "PROCESO_TERMINADO", texto}` o `409 {codigo: "BETA_APAGADA", texto}`. |
| `POST /novedades` | `{telefono}` | `200 {novedades: [{vacante, titulo, texto}]}`, con la lista vacía si no hay procesos activos vinculados. |
| `POST /bajas` | `{telefono}` | `204`, también si no había nada que desvincular. |

Sin `telefono`, con uno que no es un número internacional o con un cuerpo que no es JSON, las tres
responden `400 {codigo: "VALIDACION", texto}`. (EX tolera el número sin el `+`, como lo escribe
Meta, pero el bot lo manda siempre con él.)

### `POST /vinculos` — un número anunciado escribió

```json
{"telefono": "+51987654321", "wamid": "wamid.HBg…", "texto": "lo que escribió", "recibidoEn": "2026-10-13T15:04:05Z"}
```

Vincula **todas** las postulaciones anunciadas (y vigentes) para ese número. El texto da igual.
Desde la `V92` además enciende el interruptor de WhatsApp de esa persona y, si el número estaba en
`whatsapp_baja`, lo saca de ahí (el bot ya levantó su baja al recibir el mensaje con la espera
vigente). El anuncio de un número sin procesos vivos se cierra como `PROCESO_TERMINADO` y responde
el `409` de abajo, como siempre.
| Respuesta | Cuándo | Cuerpo |
|---|---|---|
| `200` | Vinculó, o ya estaba vinculado | `{"postulaciones": [{"postulacionRef": "pw_…"}], "nombre": "Ana", "novedades": [{"vacante": "Asistente Contable", "titulo": "Estamos calificando tus respuestas", "texto": "Se juntan tu currículum y tu evaluación en un solo perfil. Suele tardar poco."}]}` |
| `404` | Ese número no tiene un anuncio vigente | `{"codigo": "SIN_ANUNCIO", "texto": "…"}` |
| `409` | El proceso terminó entre el botón y el mensaje | `{"codigo": "PROCESO_TERMINADO", "texto": "No tienes procesos activos con avisos por WhatsApp. Puedes ver todo en el portal, en «Mis procesos»."}` |
| `409` | La beta está apagada | `{"codigo": "BETA_APAGADA", "texto": "…"}` |

- **Es idempotente.** `postulaciones` son todas las postulaciones activas vinculadas a ese número
  después de la llamada, también las que ya lo estaban; `novedades`, las de todos esos procesos.
  Repetida, devuelve lo mismo.
- En los rechazos, `texto` es lo que el bot contesta tal cual.

### `POST /novedades`

```json
{"telefono": "+51987654321"}
```

Responde `{"novedades": [{"vacante": "…", "titulo": "…", "texto": "…"}]}`, una por cada proceso
**activo** vinculado a ese número, o la lista vacía. Es `POST` para que el teléfono no vaya en la
URL ni quede en los registros de acceso. Funciona aunque la beta esté apagada: los vínculos
activos siguen recibiendo novedades si escriben.

### `POST /anuncios` — los anuncios que no le llegaron

```json
{"telefono": "+51987654321"}
```

Responde `{"esperas": [ … ]}`: los anuncios **vigentes** de ese número, cada uno con el mismo cuerpo
que `POST /v1/ex/esperas` (teléfono, `postulacionRef`, `caducaEn` y consentimiento), o la lista
vacía. El bot la llama cuando le escribe un número que no tiene anunciado ni vinculado, antes de
contestar: si EX no pudo empujar el anuncio (el CRM apagado de noche, una caída, un despliegue)
y agotó sus reintentos, el bot lo recoge aquí, guarda la espera y vincula en el mismo turno. Cada
anuncio entregado queda confirmado y el sondeo deja de reintentarlo. Un vínculo sin su
consentimiento no se entrega.

### `POST /bajas`

```json
{"telefono": "+51987654321"}
```

Desvincula todas las postulaciones de ese número y lo marca como suprimido. Responde `204`,
también si no había nada que desvincular o si se repite. Desde la `V92` apaga además el interruptor
de WhatsApp de quien tiene ese número y devuelve a cero el contador del mensaje flotante: el
portal dice «WhatsApp apagado por BAJA» y puede volver a invitarle, ya con «Vuelve a recibir tus
avisos por WhatsApp».

### Las novedades

`texto` ya trae las palabras del portal (ver `docs/contratos/textos-del-momento.json`) y es lo que
el bot pone tras «Tu postulación a {vacante}: »; `titulo` es el nombre corto del momento, y el bot
solo lo usa en el registro.

## Lo que no está en el contrato

- **El bot falso**: con `WHATSAPP_MODO=falso` (desarrollo y E2E), EX no llama a nadie. Contesta
  como el bot de verdad —`400 validacion` si a una espera le falta el consentimiento o una
  revocación trae otro motivo, `409 aviso_distinto` si un `avisoId` vuelve con otro contenido— y
  apunta lo que acepta (esperas con su consentimiento, avisos, revocaciones, desde la `V76` los
  eventos v2 de `/v1/ex/resultados` y desde la `V92` las vinculaciones) en
  `GET /api/v1/integraciones/whatsapp/falso/registro?telefono=…`, firmado como el resto. En
  producción esa ruta no existe. Por defecto conoce todos los números; para el QA, tres mandos
  firmados: `POST …/falso/caido {caido}` (contesta como caído), `POST …/falso/sin-conversacion
  {telefono, sinConversacion}` (`404 SIN_CONVERSACION`) y `POST …/falso/suprimido {telefono,
  suprimido}` (`409 SUPRIMIDO` en una `NUEVA_POSTULACION`). Contesta como un bot en la versión 2; las pruebas pueden ponerlo
  en la 1, y entonces los eventos reciben `404 ruta_no_disponible`.

## Configuración de EX

| Variable | Qué es | Por defecto |
|---|---|---|
| `WHATSAPP_MODO` | `apagado`, `falso` o `real` | `apagado` (`falso` en el perfil local) |
| `WHATSAPP_NUMERO_BOT` | El número del bot, como va en `wa.me` | `51928439930` |
| `WHATSAPP_URL_SERVICIO` | La dirección HTTPS del servicio de conversación | (vacía; obligatoria en `real`) |
| `WHATSAPP_SECRETO_HACIA_EL_BOT` | Con lo que firma EX (32 caracteres o más) | (vacío; obligatorio en `real`) |
| `WHATSAPP_SECRETO_DESDE_EL_BOT` | Con lo que firma el bot | (vacío: todo 401) |
| `WHATSAPP_ENLACE_MIS_PROCESOS` (`V76`) | El enlace fijo a «Mis procesos» de los avisos y de la consulta v2: `https`, sin parámetros, fragmento ni token | `PORTAL_URL` + `/procesos` |
| `WHATSAPP_PRODUCTOR_V2` (`V76`) | Los eventos v2 hacia `/v1/ex/resultados`. Solo se enciende tras el bot, en el orden de C-10 del contrato v2 | `false` (EX se comporta como v1) |

Además, el portal solo ofrece el botón con el parámetro `whatsapp_novedades_activo` de la
plataforma encendido (se cambia desde el panel) y en vacantes de RENASER.
