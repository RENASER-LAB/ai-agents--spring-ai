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

El candidato pulsa «Escribir por WhatsApp» en el portal. EX le **anuncia** al bot ese número. El
candidato escribe primero desde ese número (diga lo que diga), el bot le pregunta a EX, EX lo
**vincula** y le da sus novedades. Desde entonces EX le manda al bot los **avisos** de «te toca»,
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

Cuando el candidato pulsa el botón. `caducaEn` es **24 horas** después. `consentimiento` es el que
acaba de aceptar: tipo `WHATSAPP`, su versión, el texto exacto que vio y la fecha. Si el bot no
responde, EX lo reintenta (30 s, 2 min, 8 min, 32 min) mientras no caduque.

### `POST /v1/ex/avisos` — le toca algo

```json
{"avisoId": "0199d5a2-…-7…", "telefono": "+51987654321", "postulacionRef": "pw_9f…",
 "tipo": "PRUEBA_DISPONIBLE", "nombre": "Ana", "vacante": "Asistente Contable",
 "texto": "¡Felicidades, Ana! 🎉\n\n📌 *Asistente Contable*\nTu *prueba del puesto* ya está disponible.\n⏰ Puedes empezarla hasta el *20 de octubre a las 23:59* (hora de Lima).\n\n👉 https://…/procesos\nInicia sesión con tu correo y contraseña y abre «Mis procesos»."}
```

- `tipo`: `PRUEBA_DISPONIBLE` o `POSTULACION_AVANZA`. Nunca recordatorios, cierres, rechazos ni
  movidos «sin avisar».
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

### `POST /bajas`

```json
{"telefono": "+51987654321"}
```

Desvincula todas las postulaciones de ese número y lo marca como suprimido. Responde `204`,
también si no había nada que desvincular o si se repite. El portal ya no ofrece el botón con ese
número («Este número se dio de baja de nuestros mensajes»).

### Las novedades

`texto` ya trae las palabras del portal (ver `docs/contratos/textos-del-momento.json`) y es lo que
el bot pone tras «Tu postulación a {vacante}: »; `titulo` es el nombre corto del momento, y el bot
solo lo usa en el registro.

## Lo que no está en el contrato

- **El bot falso**: con `WHATSAPP_MODO=falso` (desarrollo y E2E), EX no llama a nadie. Contesta
  como el bot de verdad —`400 validacion` si a una espera le falta el consentimiento o una
  revocación trae otro motivo, `409 aviso_distinto` si un `avisoId` vuelve con otro contenido— y
  apunta lo que acepta (esperas con su consentimiento, avisos, revocaciones y, desde la `V76`, los
  eventos v2 de `/v1/ex/resultados`) en
  `GET /api/v1/integraciones/whatsapp/falso/registro?telefono=…`, firmado como el resto. En
  producción esa ruta no existe. Contesta como un bot en la versión 2; las pruebas pueden ponerlo
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
