# Notificaciones del candidato: un solo ajuste para WhatsApp, correo y portal
Estado: TERMINADO

Crear, completar o revisar esta spec no autoriza su implementación. Solo el usuario
puede habilitar la versión revisada mediante `/harness ready <ruta-spec>` y la
confirmación `LISTA PARA IMPLEMENTAR <código>` que devuelve el controlador.
El controlador cambia el estado; no marcarla lista manualmente. Esta autorización
no inicia un trabajo ni sustituye la aprobación de la entrega después del QA.
Antes de iniciar un trabajo, si se modifica una spec ya autorizada, devolverla a
borrador con `spec-draft` y solicitar una nueva autorización.

Escrita el 24/11/2026 contra `origin/main` del backend `7d9e120` (#145, migraciones hasta la V89)
y del portal `315e1c4`. Abarca backend y portal. Sustituye al borrador
«volver-a-recibir-por-whatsapp», que activaba WhatsApp proceso por proceso.
**Depende del endpoint `POST /v1/ex/vinculaciones` del CRM** (rama `feat/vinculaciones-ex`; su
contrato está en `CRM/specs/Draft/034-avisos-ex-activados-desde-el-portal/spec.md`), que se despliega
antes.

Maqueta: [`maqueta.html`](maqueta.html) en esta carpeta (también publicada como artifact). Es la
referencia de textos, estados y comportamiento; la barra negra de abajo solo sirve para cambiar de
situación y no se construye.

## Objetivo

Hoy WhatsApp se activa en cada proceso con su tarjeta, «Dejar de recibir» cierra uno, y quien
escribió BAJA no puede volver nunca. El correo y la campana no se pueden apagar.

Esta spec deja **un solo ajuste por cuenta**:

- Un botón «Notificaciones» junto al título de «Mis procesos» abre una ventana con tres
  interruptores: **WhatsApp** (primero, marcado «Recomendado»), **Correo** y **En el portal**.
- WhatsApp vale para todos sus procesos, los de hoy y los que vengan. Quien ya no quiere saber de un
  proceso se retira de él; no hay apagado por proceso.
- Correo y portal vienen encendidos y se pueden apagar.
- Quien escribió BAJA puede volver con un clic, porque su número ya está comprobado.
- Un mensaje flotante invita a activar WhatsApp tras postular, como mucho dos veces.
- Un número de celular pertenece a una sola cuenta.

## Flujo principal

### Ver el estado
1. Al abrir «Mis procesos», el portal pide `GET /api/v1/portal/notificaciones` y pinta el botón con
   el estado de WhatsApp debajo de «Notificaciones» (tabla de estados más abajo). Con WhatsApp
   activo, la campana del botón lleva un punto verde.
2. Desaparecen la tarjeta de WhatsApp de cada proceso, su línea corta y «Dejar de recibir» de
   `Procesos.tsx` y `Seguimiento.tsx`.

### Activar WhatsApp con un clic (número ya comprobado)
3. El número está comprobado si **esa cuenta** tuvo alguna vez un vínculo con **ese mismo
   teléfono** y `vinculado_en` no nulo (el candidato escribió desde él).
4. Al encender el interruptor (o «Activar WhatsApp» del mensaje flotante), el portal llama a
   `POST /api/v1/portal/notificaciones/whatsapp`. El backend:
   - firma el consentimiento `WHATSAPP_CUENTA` 1.0 (IP, agente, fecha), uno por persona y versión;
   - toma sus postulaciones vivas (no finales, vacante en pie, de la beta) sin vínculo vivo;
   - llama al CRM `POST /v1/ex/vinculaciones` con motivo `ACTIVACION` y esas postulaciones;
   - con `200`, borra el número de `whatsapp_baja` si estaba, crea un vínculo `ACTIVO` por cada
     postulación devuelta y deja `whatsapp_activo = true` en las preferencias.
5. La ventana muestra «Activando…» y luego «Te enviamos un mensaje para confirmarlo. Te avisamos de
   todos tus procesos y puedes escribirnos tus dudas.» Al candidato le llega la plantilla
   `avisos_activados` del CRM.

### Activar WhatsApp con mensaje (número sin comprobar)
6. Mismo botón y misma firma, pero el backend crea un vínculo `PENDIENTE` con su anuncio de 24 h
   para **cada** postulación viva (lo que hoy hace `preparar()` para una sola) y devuelve el enlace
   `wa.me` del bot.
7. El portal abre el chat y la ventana dice «Te abrimos WhatsApp. Envía el mensaje desde el
   +51 … para terminar.». El botón dice «Esperando tu mensaje».
8. Cuando el bot llama a `/vinculos`, los pendientes del número pasan a `ACTIVO` (ya ocurre: el
   vínculo es por número), `whatsapp_activo = true` y, si el número estaba en `whatsapp_baja`, sale
   de ahí.
9. Si pasan 24 h sin mensaje, el estado es `NO_LLEGO`: «No nos llegó tu mensaje desde el +51 ….
   Reintentar». Reintentar repite el paso 6.

### Postulaciones nuevas con WhatsApp activo
10. Al postular con `whatsapp_activo = true`, el backend crea el vínculo de la postulación nueva y
    llama a `/v1/ex/vinculaciones` con motivo `NUEVA_POSTULACION` (sin plantilla). Si el CRM falla,
    el vínculo queda `PENDIENTE` de reintento con el sondeo de siempre; la postulación no se frena.

### Apagar WhatsApp desde el portal
11. Al apagar, `DELETE /api/v1/portal/notificaciones/whatsapp`: cierra todos sus vínculos vivos con
    motivo `DEJAR_DE_RECIBIR`, omite sus avisos pendientes, avisa al CRM por `/revocaciones` y deja
    `whatsapp_activo = false`. El número sigue comprobado: volver a encender es con un clic.

### BAJA escrita en el chat
12. `/bajas` sigue igual (cierra con `BAJA` y guarda el número en `whatsapp_baja`) y además deja
    `whatsapp_activo = false`. El portal lo muestra al cargar: «WhatsApp apagado por BAJA».
13. La BAJA reinicia el contador del mensaje flotante a cero (ver «Mensaje flotante»).

### Correo y portal
14. `PATCH /api/v1/portal/notificaciones` con `{correo?: boolean, portal?: boolean}`.
15. Con correo apagado no salen los correos **de sus procesos**: los de `MaquinaEstados`
    (cambio de etapa), `POSTULACION_RECIBIDA`, disponibilidad de simulación, recordatorios y
    «más tiempo» de la prueba. Siguen saliendo siempre: `CUENTA_CREADA`, recuperar la clave,
    borrado de datos e invitaciones del equipo.
16. Con portal apagado, `ServicioAvisosPortal.publicar` no publica avisos de sus procesos en la
    campana. Las pantallas siguen mostrando el estado de cada proceso.
17. Al apagar cualquiera de los dos, la ventana muestra el aviso de la maqueta debajo del interruptor
    («No te llegarán por correo los plazos de tus pruebas…» / «La campana dejará de marcar
    novedades…»). Se puede apagar todo.

### Mensaje flotante
18. Sale bajo el botón (forma «centrada» de la maqueta: ícono verde, título, una línea, botón verde a
    lo ancho y una × arriba a la derecha para cerrarlo) cuando **todo** esto se cumple:
    - WhatsApp no está activo ni esperando, y la cuenta tiene número;
    - tiene al menos un proceso vivo;
    - se ha mostrado menos de 2 veces;
    - hay una postulación posterior a la última vez que se mostró (la primera vez: cualquiera).
19. Se cuenta como mostrado al pintarse (`POST /api/v1/portal/notificaciones/mensaje-visto`); la ×
    solo lo cierra. Activar desde él cuenta igual.
20. Textos según la situación:
    | Situación | Título | Línea |
    |---|---|---|
    | Nunca activó | Recibe tus avisos por WhatsApp | Y pregúntanos tus dudas. |
    | Escribió BAJA | Vuelve a recibir tus avisos por WhatsApp | Los apagaste con BAJA. |
    | Cambió de número | Activa WhatsApp en tu número nuevo | Te pediremos un mensaje para comprobarlo. |

### Un número por cuenta
21. `persona.telefono` pasa a ser único (índice único parcial sobre los no nulos).
22. Al guardar un celular (registro, Mi cuenta o la ventana de notificaciones) que ya usa otra
    cuenta, responde `409 TELEFONO_EN_USO` y el portal muestra «Ese número ya está en otra cuenta.
    Usa otro o entra con esa cuenta.»
23. Sin número, la fila de WhatsApp no tiene interruptor: muestra «Agrega tu celular para recibir
    avisos por WhatsApp.» con el campo y «Guardar», que usa el mismo guardado de Mi cuenta.

### Cambio de número
24. `alCambiarElCelular` ya revoca los vínculos; además deja `whatsapp_activo = false`. El número
    nuevo no está comprobado, así que activar sigue el camino con mensaje. La ventana dice
    «Cambiaste tu número. Actívalo y envía un mensaje desde el nuevo para comprobarlo.»

## Estados de WhatsApp que devuelve el backend

`GET /api/v1/portal/notificaciones` →

```json
{
  "whatsapp": {
    "disponible": true,
    "estado": "APAGADO",
    "numero": "+51 987 654 321",
    "comprobado": true,
    "porBaja": true,
    "bajaEn": "2026-11-12",
    "cambioDeNumero": false
  },
  "correo": { "activo": true, "destino": "lucia.paredes@correo.com" },
  "portal": { "activo": true },
  "mensaje": { "mostrar": true, "situacion": "TRAS_BAJA" }
}
```

`mensaje.situacion` es `PRIMERA_VEZ`, `TRAS_BAJA` o `CAMBIO_DE_NUMERO` (los tres textos del paso 20).

| `estado` | Cuándo | Texto bajo «Notificaciones» | Fila de WhatsApp |
|---|---|---|---|
| `SIN_NUMERO` | la cuenta no tiene celular | WhatsApp sin número | campo + Guardar |
| `APAGADO` | no activo; `porBaja` dice si fue por BAJA | WhatsApp apagado / WhatsApp apagado por BAJA | interruptor apagado + consentimiento o texto de BAJA |
| `ESPERANDO` | hay anuncios vigentes sin mensaje | Esperando tu mensaje | interruptor encendido + «Envía el mensaje…» |
| `NO_LLEGO` | el último anuncio caducó sin mensaje | No llegó tu mensaje | interruptor apagado + «Reintentar» |
| `ACTIVO` | `whatsapp_activo` | WhatsApp activo | interruptor verde, número enmascarado |

`disponible = false` (beta apagada para todas sus organizaciones) oculta la fila de WhatsApp y el
mensaje flotante; correo y portal siguen.

## Referencia visual

[`maqueta.html`](maqueta.html). Pantalla «Mis procesos» en lista; botón junto al título; ventana
(`<dialog>`) con los tres canales; mensaje flotante «centrado». Northstar con los tokens de
`mundo.css`; el verde solo para WhatsApp. Comprobar en 1280×800 y 390×844, y con movimiento
reducido (solo opacidad).

## Criterios de aceptación

- AC-01: Dada una cuenta sin WhatsApp, con número y un proceso vivo, cuando abre «Mis procesos» tras
  postular, entonces ve el mensaje flotante «Recibe tus avisos por WhatsApp» y no hay tarjeta de
  WhatsApp en ningún proceso.
- AC-02: Dado AC-01 y número sin comprobar, cuando pulsa «Activar WhatsApp», entonces se firma
  `WHATSAPP_CUENTA`, hay un vínculo `PENDIENTE` por cada proceso vivo, se abre el chat y el botón
  dice «Esperando tu mensaje».
- AC-03: Dado AC-02, cuando el bot llama a `/vinculos`, entonces todos pasan a `ACTIVO` y el botón
  dice «WhatsApp activo».
- AC-04: Dado AC-02 y 24 h sin mensaje, cuando abre la ventana, entonces ve «No nos llegó tu mensaje…
  Reintentar».
- AC-05: Dada una cuenta que escribió BAJA tras tener vínculos activos con ese número, cuando
  enciende WhatsApp, entonces se llama a `/v1/ex/vinculaciones` con motivo `ACTIVACION`, el número
  sale de `whatsapp_baja`, sus procesos vivos quedan `ACTIVO` y el botón dice «WhatsApp activo» sin
  abrir el chat.
- AC-06: Dado WhatsApp activo, cuando postula a otra vacante, entonces esa postulación queda
  vinculada sin hacer nada y se llama a `/vinculaciones` con motivo `NUEVA_POSTULACION`.
- AC-07: Dado WhatsApp activo, cuando lo apaga en la ventana, entonces se cierran todos sus vínculos
  con `DEJAR_DE_RECIBIR` y, al volver a encender, se activa con un clic.
- AC-08: Dado correo apagado, cuando el equipo mueve su postulación, entonces no sale el correo de la
  etapa pero sí el aviso de WhatsApp (si está activo) y la campana (si está encendida). Recuperar la
  clave sigue mandando correo.
- AC-09: Dado portal apagado, cuando el equipo mueve su postulación, entonces la campana no recibe
  aviso y el proceso muestra su estado nuevo.
- AC-10: Dado el mensaje flotante mostrado dos veces, cuando vuelve a postular, entonces ya no sale.
  Dado que escribe BAJA después, cuando vuelve a postular, entonces sale «Vuelve a recibir tus avisos
  por WhatsApp».
- AC-11: Dado un celular que ya usa otra cuenta, cuando lo guarda en la ventana o en Mi cuenta,
  entonces responde `409 TELEFONO_EN_USO` y se muestra «Ese número ya está en otra cuenta…».
- AC-12: Dado WhatsApp activo, cuando cambia su número en Mi cuenta, entonces queda `APAGADO` con
  `cambioDeNumero`, y activar abre el chat (camino con mensaje).
- AC-13: Si el CRM responde un error o no contesta en el paso 4, no se borra nada de `whatsapp_baja`,
  no se crean vínculos y la ventana muestra «No pudimos activarlo. Vuelve a intentarlo en un
  momento.»

## Casos límite y errores

- Doble clic al activar: un consentimiento por persona y versión; la llamada al CRM es idempotente;
  la fila de preferencias se bloquea mientras se activa.
- Sin procesos vivos: el interruptor se puede encender (queda activo para las postulaciones
  futuras); en el camino con mensaje no hay nada que anunciar, así que se anuncia el número sin
  postulación y `/vinculos` responde `SIN_PROCESOS_ACTIVOS` como hoy. El mensaje flotante no sale.
- Postulación que termina: su vínculo se cierra como hoy (`PROCESO_TERMINADO`); el ajuste sigue
  activo.
- Activar con un clic y el CRM responde `404 SIN_CONVERSACION` (el número nunca habló con el bot):
  cae al camino con mensaje.
- Vínculos que ya existían por proceso al desplegar: se respetan. Una cuenta con al menos un vínculo
  `ACTIVO` empieza con `whatsapp_activo = true`.
- Teléfonos duplicados existentes: se resuelven a mano antes de desplegar (ver «Datos y
  compatibilidad»).

## Datos y compatibilidad

- Migración **V92**. La V90 es del seguimiento de pendientes (autorizada) y la V91 del video; se
  confirma que la V92 sigue libre antes de empezar y, si no, se usa la siguiente libre. Lleva:
  - tabla `preferencia_aviso` (`persona_id` PK, `correo boolean NOT NULL DEFAULT true`,
    `portal boolean NOT NULL DEFAULT true`, `whatsapp_activo boolean NOT NULL DEFAULT false`,
    `mensaje_vistas smallint NOT NULL DEFAULT 0`, `mensaje_visto_en timestamptz`,
    `actualizado_en`). Sin fila = todo por defecto.
  - relleno: `whatsapp_activo = true` para quien tiene un vínculo `ACTIVO`.
  - texto de consentimiento `WHATSAPP_CUENTA` 1.0 (y su valor en el CHECK de tipo). El de
    `WHATSAPP` 1.0 no se toca. Texto propuesto (el que muestra la maqueta):
    > «Al activarlo aceptas recibir por WhatsApp las novedades de tus procesos. Escribe BAJA cuando
    > quieras dejarlo.»
  - índice único parcial `persona (telefono) WHERE telefono IS NOT NULL`.
- Antes de desplegar la V92, contar duplicados en producción:
  `SELECT telefono, count(*) FROM persona WHERE telefono IS NOT NULL GROUP BY 1 HAVING count(*) > 1;`
  Si hay, el usuario los resuelve a mano antes de desplegar; la migración no los toca y falla si
  quedan.
- Endpoints nuevos del portal: `GET`/`PATCH /api/v1/portal/notificaciones`,
  `POST`/`DELETE /api/v1/portal/notificaciones/whatsapp`,
  `POST /api/v1/portal/notificaciones/mensaje-visto`.
- Endpoints que quedan sin uso en el portal: `POST /postulaciones/{uuid}/whatsapp` y
  `.../dejar-de-recibir`. Se mantienen una versión por compatibilidad y se retiran después.
- `ClienteConversacion`: llamada nueva a `/v1/ex/vinculaciones` (contrato en la spec 034).
- `WhatsappDelProceso` deja de usarse en el portal.
- Documentación: en el backend, el diccionario de datos y el contrato de WhatsApp; en el portal,
  `docs/02-QUE-VE-EL-CANDIDATO.md`, `docs/06-FLUJO-COMPLETO.md`, `docs/PENDIENTES.md` y DESIGN.md
  (el botón, la ventana y el mensaje flotante).

## Verificación

- Datos de prueba: un candidato sin número; uno con número sin comprobar y dos procesos vivos; uno con
  número en `whatsapp_baja` y vínculo antiguo con `vinculado_en`; uno con WhatsApp activo; dos cuentas
  para el número duplicado. Bot falso (`BotFalsoWhatsappController`) respondiendo `/vinculaciones`.
- E2E repetibles en headless: AC-01, AC-02, AC-05, AC-06, AC-08, AC-10, AC-11.
- QA con Chrome visible: el botón, la ventana y el mensaje flotante en escritorio y móvil, en las
  seis situaciones de la maqueta, y el error del CRM.
- Resultado que quiero comprobar personalmente: con mi número de prueba dado de baja, encender
  WhatsApp en la ventana y recibir la confirmación sin escribir nada; postular a otra vacante y
  recibir su aviso por WhatsApp.

## Dependencias y dudas

- Requiere `feat/vinculaciones-ex` del CRM desplegada y la plantilla `avisos_activados` aprobada en Meta (sin
  ella, la activación funciona pero no llega la confirmación).
- Hoy hay un solo número de bot para todas las empresas; cuando cada empresa tenga el suyo, el
  ajuste de WhatsApp y la BAJA tendrán que separarse por número de empresa.
