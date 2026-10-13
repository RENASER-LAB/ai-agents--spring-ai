# Contrato: novedades de la postulación por WhatsApp (EX ↔ bot)

Lo que se dicen EX (este backend) y el bot de RENASER (el servicio de conversación del CRM,
rama `new-chatbot`). Sale de `specs/novedades-por-whatsapp.md` y tiene que quedar **igual** en el
contrato generado del servicio (`contracts/openapi-conversation.yaml`). Si algo cambia aquí,
cambia allí, y al revés.

## La idea en una línea

El candidato pulsa «Escribir por WhatsApp» en el portal. EX le **anuncia** al bot ese número. El
candidato escribe primero desde ese número (diga lo que diga), el bot le pregunta a EX, EX lo
**vincula** y le da sus novedades. Desde entonces EX le manda al bot los **avisos** de «te toca»,
y le cuenta cuando el vínculo se **revoca**. Si el candidato escribe «BAJA», el bot se lo cuenta a
EX.

## La firma (las dos direcciones)

Toda llamada lleva dos cabeceras:

| Cabecera | Valor |
|---|---|
| `X-Renaser-Marca` | Segundos Unix del momento en que se manda, en texto: `1760000000` |
| `X-Renaser-Firma` | HMAC-SHA256 en hexadecimal (minúsculas) de `marca + "." + cuerpo` |

- `cuerpo` son los bytes exactos del cuerpo HTTP. En un `GET` el cuerpo es vacío: se firma
  `marca + "."`.
- Se compara en tiempo constante. Una marca a más de **5 minutos** de la hora del que recibe
  (antes o después) se rechaza.
- **Un secreto por dirección**: EX firma con `WHATSAPP_SECRETO_HACIA_EL_BOT` y el bot con
  `WHATSAPP_SECRETO_DESDE_EL_BOT`. Si uno se filtra, el otro lado sigue cerrado.
- Sin firma, con firma mala o con marca vieja: **401** y no se hace nada.
- Vector de prueba: secreto `un-secreto-de-pruebas-de-32-bytes!!`, marca `1760000000`, cuerpo
  `{"telefono":"+51987654321"}` → firma
  `ca8e8cc5655e95a1a9f28815a04f2fc18a5c56bd53f5ca9bdfecba6679ee9f2f`.

⚠️ En el `GET /novedades` la firma no cubre la dirección (`?telefono=…`), solo la marca: así lo
define la spec. La ventana de 5 minutos acota el riesgo.

## Los datos que viajan

- `telefono`: E.164 con «+» (`+51987654321`). EX acepta también sin «+» (`51987654321`, como lo
  manda Meta).
- `postulacionRef`: referencia **opaca** de EX (`pw_` y 32 cifras hexadecimales). No es el id
  interno ni el uuid del portal. Es una por vínculo: si el candidato deja de recibir y vuelve a
  pulsar, o cambia de celular, nace otra.
- Fechas: ISO-8601 en UTC (`2026-10-13T15:04:05Z`).

## EX llama al bot

Bajo `/v1/ex/…` del servicio (es lo que enruta su Caddy). `Content-Type: application/json`.
Tiempo máximo de espera: 3 s.

### `POST /v1/ex/esperas` — ese número va a escribir

```json
{"telefono": "+51987654321", "postulacionRef": "pw_9f…", "caducaEn": "2026-10-13T15:04:05Z"}
```

Cuando el candidato pulsa el botón. El anuncio dura **24 horas**; repetido, renueva `caducaEn`.
Si el bot no responde, EX lo reintenta (30 s, 2 min, 8 min, 32 min) mientras no caduque.

### `POST /v1/ex/avisos` — le toca algo

```json
{"avisoId": "0199d5a2-…-7…", "telefono": "+51987654321", "postulacionRef": "pw_9f…",
 "tipo": "PRUEBA_DISPONIBLE", "nombre": "Ana", "vacante": "Asistente Contable",
 "texto": "¡Felicidades, Ana! Tu prueba del puesto para Asistente Contable ya está disponible.\nEntra en el portal con tu correo y tu contraseña y abre «Mis procesos»."}
```

- `tipo`: `PRUEBA_DISPONIBLE` o `POSTULACION_AVANZA`. Nunca recordatorios, cierres, rechazos ni
  movidos «sin avisar».
- `avisoId` (UUIDv7) es la clave de idempotencia. EX reintenta **siempre con el mismo** y el mismo
  contenido.
- Respuesta esperada: `202 {"avisoId": "…"}`. Repetido con el mismo contenido, el resultado ya
  guardado. Con otro contenido, `409`.
- `texto` es para la ventana de 24 h. Fuera de ella el bot usa la plantilla aprobada con
  `{{1}} = nombre` y `{{2}} = vacante`.
- EX solo manda avisos a vínculos **activos** (el candidato ya escribió). Reintenta hasta 5
  veces con espera creciente; un `4xx` es definitivo.

### `POST /v1/ex/revocaciones` — ese vínculo se acabó

```json
{"telefono": "+51987654321", "postulacionRef": "pw_9f…", "motivo": "DEJAR_DE_RECIBIR"}
```

`motivo`: `DEJAR_DE_RECIBIR` (desde el portal), `CAMBIO_DE_CELULAR`, `PROCESO_TERMINADO` o
`BORRADO_DE_DATOS`. Anula también el anuncio, si lo había. Tras una baja (`BAJA`) EX no revoca:
fue el bot quien se lo contó.

## El bot llama a EX

Bajo `/api/v1/integraciones/whatsapp/…`. Solo con la firma del bot; ningún token de EX abre esta
puerta.

### `POST /vinculos` — un número anunciado escribió

```json
{"telefono": "+51987654321", "wamid": "wamid.HBg…", "texto": "lo que escribió", "recibidoEn": "2026-10-12T15:04:05Z"}
```

Vincula **todas** las postulaciones anunciadas (y vigentes) para ese número. El texto da igual.

| Respuesta | Cuándo | Cuerpo |
|---|---|---|
| `200` | Vinculó, o ya estaba vinculado | `{"postulaciones": [{"postulacionRef": "pw_…"}], "nombre": "Ana", "novedades": [{"postulacionRef": "pw_…", "vacante": "Asistente Contable", "titulo": "Estamos calificando tus respuestas", "texto": "Se juntan tu currículum y tu evaluación en un solo perfil. Suele tardar poco."}]}` |
| `404` | Ese número no tiene un anuncio vigente | `{"codigo": "SIN_ANUNCIO", "texto": "…"}` |
| `409` | El proceso terminó entre el botón y el mensaje | `{"codigo": "PROCESO_TERMINADO", "texto": "No tienes procesos activos con avisos por WhatsApp. Puedes ver todo en el portal, en «Mis procesos»."}` |
| `409` | La beta está apagada | `{"codigo": "BETA_APAGADA", "texto": "…"}` |

En los rechazos, `texto` es lo que el bot contesta tal cual. `titulo` y `texto` de cada novedad son
**las mismas palabras del portal** (ver `docs/contratos/textos-del-momento.json`).

### `GET /novedades?telefono=…`

```json
{"nombre": "Ana", "novedades": [{"postulacionRef": "pw_…", "vacante": "…", "titulo": "…", "texto": "…"}]}
```

Las de cada proceso **activo** vinculado a ese número. `novedades` puede ir vacía y `nombre`
nulo. Funciona aunque la beta esté apagada: los vínculos activos siguen recibiendo novedades si
escriben.

### `POST /bajas`

```json
{"telefono": "+51987654321"}
```

Desvincula todas las postulaciones de ese número y lo marca como suprimido. Responde
`200 {"desvinculadas": 1}`. Repetido, no hace nada nuevo. El portal ya no ofrece el botón con ese
número («Este número se dio de baja de nuestros mensajes»).

## Lo que no está en el contrato

- El bot falso: con `WHATSAPP_MODO=falso` (desarrollo y E2E), EX no llama a nadie y apunta lo que
  mandaría en `GET /api/v1/integraciones/whatsapp/falso/registro?telefono=…` (firmado como el
  resto). En producción esa ruta no existe.

## Configuración de EX

| Variable | Qué es | Por defecto |
|---|---|---|
| `WHATSAPP_MODO` | `apagado`, `falso` o `real` | `apagado` (`falso` en el perfil local) |
| `WHATSAPP_NUMERO_BOT` | El número del bot, como va en `wa.me` | `51928439930` |
| `WHATSAPP_URL_SERVICIO` | La dirección HTTPS del servicio de conversación | (vacía; obligatoria en `real`) |
| `WHATSAPP_SECRETO_HACIA_EL_BOT` | Con lo que firma EX (32 caracteres o más) | (vacío; obligatorio en `real`) |
| `WHATSAPP_SECRETO_DESDE_EL_BOT` | Con lo que firma el bot | (vacío: todo 401) |

Además, el portal solo ofrece el botón con el parámetro `whatsapp_novedades_activo` de la
plataforma encendido (se cambia desde el panel) y en vacantes de RENASER.
