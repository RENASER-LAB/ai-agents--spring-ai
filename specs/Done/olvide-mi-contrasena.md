# Me olvidé mi contraseña (candidatos y equipo)

Estado: TERMINADO

Estado previo (referencia histórica): lista para implementar

## Objetivo

Que quien olvidó su contraseña pueda recuperar el acceso por sí mismo, con un enlace que
llega a su correo y le permite elegir una contraseña nueva. Sirve a dos públicos: los
candidatos del portal y el equipo de empresa del panel.

## Contexto

- Comportamiento actual:
  - **Portal del candidato:** el enlace «¿Olvidaste tu contraseña?» de `/ingresar` lleva a
    `/clave` (`paginas/cuenta/Clave.tsx`), una pantalla que **no restablece nada**: explica
    que se busque un enlace viejo en el correo o que se escriba a `talento@renaser.pe`.
    Quien la olvida a mitad de un proceso queda fuera, con el plazo de su evaluación corriendo.
  - **Panel de empresa:** `panel/entrar/Entrar.tsx` no tiene enlace de recuperación. La única
    salida es que un administrador invite de nuevo a la persona.
  - **Backend:** no existe ninguna ruta de recuperación. Sí existe la tubería de correo
    (`ServicioCorreo` con plantillas por organización, `EnviadorCorreo` con transporte
    `log` o `smtp`), la de invitaciones del equipo (token aleatorio, solo se guarda su hash,
    vence, se canjea una vez) y el bloqueo de intentos de login (`IntentosLogin`,
    5 fallos = 15 minutos, por correo).
  - Contraseñas: BCrypt en `usuario.contrasena_hash`. El portal exige mínimo 8 caracteres y
    el panel mínimo 12 (invitación).
  - Hay cuentas de candidatos creadas por carga masiva de currículums con un correo
    inventado (`…@cv-convocatoria.local`); a esas no llega ningún correo.
- Pantallas o procesos afectados: `/ingresar` y `/clave` del portal; `/admin/entrar` del
  panel; dos pantallas nuevas de «elegir contraseña nueva»; endpoints públicos de
  autenticación del portal y del panel; plantillas de correo; auditoría.
- Referencias existentes: [plantilla de spec](../plantilla-spec.md),
  [roles y permisos](../../docs/04-ROLES-Y-PERMISOS.md), invitaciones del equipo
  (`ServicioInvitacionesImpl`), enlaces de acceso del candidato (`ServicioEnlaceAcceso`).

## Alcance

- Incluye:
  - **Pedir el restablecimiento** con el correo, en portal y en panel.
  - **Correo con enlace** de un solo uso y vida corta que lleva a una pantalla donde se
    elige la contraseña nueva.
  - **Elegir la contraseña nueva** (con confirmación) y volver a la pantalla de entrada con
    un aviso de éxito. No se inicia sesión automáticamente.
  - Enlace «¿Olvidaste tu contraseña?» también en el login del panel.
  - Convertir `/clave` en el formulario real, conservando debajo la alternativa de escribir
    a `talento@renaser.pe` para quien no reciba el correo.
  - Límite de solicitudes, registro en auditoría y plantillas de correo sembradas.
- Queda fuera:
  - Cambiar la contraseña estando dentro de la sesión («Mi cuenta»): no existe hoy y es otra
    funcionalidad.
  - Restablecer por SMS, preguntas de seguridad o verificación en dos pasos.
  - Revocar las sesiones ya abiertas: los tokens de sesión son JWT sin lista de revocación
    y siguen valiendo hasta su vencimiento (120 min candidato, 480 min equipo).
  - Recuperar cuentas de carga masiva con correo inventado: el candidato debe escribir a
    talento o recibir un enlace de acceso del equipo, como hoy.
  - Cambiar las reglas de contraseña (mínimo 8 en portal, 12 en panel) o el bloqueo por
    intentos de login.
  - Cambiar el flujo de invitación o el de enlace de acceso (`/acceso`).

## Comportamiento esperado

### Pedir el enlace

1. En `/clave` (portal) o en la pantalla nueva del panel, el usuario escribe su correo y
   pulsa «Enviar enlace». Se valida el formato del correo en el navegador.
2. **La respuesta es siempre la misma**, exista o no la cuenta, esté activa o no, sea de
   carga masiva o no: «Si ese correo tiene una cuenta, te enviamos un enlace. Revisa también
   tu carpeta de spam. Vale por 60 minutos.» Así nadie puede descubrir qué correos están
   registrados. Tampoco cambia el tiempo de respuesta de forma apreciable.
3. Si la cuenta existe, está activa y su correo es real, el sistema:
   - invalida los enlaces de restablecimiento anteriores sin usar de esa cuenta;
   - crea uno nuevo (token aleatorio de 32 bytes, solo se guarda su hash, vida de
     60 minutos configurable, un solo uso);
   - envía el correo con el enlace por la plantilla correspondiente;
   - registra la solicitud en auditoría (sin guardar el token).
4. La pantalla muestra el mensaje de confirmación y un botón «Reenviar» deshabilitado 60
   segundos, con cuenta regresiva.
5. **Límite de solicitudes:** como máximo 3 solicitudes por correo por hora (y un tope por
   dirección IP). Al superarlo el usuario ve el mismo mensaje de siempre, pero no se envía
   otro correo ni se crea otro token.
6. La pantalla del portal sigue mostrando debajo «¿No te llega? Escríbenos a
   talento@renaser.pe».

### Elegir la contraseña nueva

7. El enlace del correo abre la pantalla de restablecer con el token en la URL
   (`/restablecer?token=…` en el portal, `/admin/restablecer?token=…` en el panel). El
   token se quita de la barra de direcciones al cargar (`replace`), como hace `/acceso`.
8. La pantalla pide «Contraseña nueva» y «Repetir contraseña», con ojo para mostrar/ocultar
   y con la regla visible (8 caracteres en portal, 12 en panel). Los campos no aceptan
   pegado de espacios sobrantes al inicio o al final (se avisa, no se recorta en silencio).
9. Al enviar, el sistema comprueba que el token existe, no venció, no se usó y no fue
   invalidado por una solicitud posterior. Si es válido:
   - guarda el nuevo hash BCrypt;
   - marca el token como usado;
   - limpia el bloqueo por intentos fallidos de ese correo;
   - registra el cambio en auditoría.
10. Tras el éxito, el usuario ve «Contraseña cambiada exitosamente» y va a la
    pantalla de entrada de su mundo (portal: `/ingresar`; panel: `/admin/entrar`), con el
    aviso encima. No queda con sesión iniciada.
11. **Token inválido, vencido, usado o invalidado:** un solo mensaje que no distingue el
    motivo — «Este enlace ya no sirve. Pide uno nuevo.» — con botón hacia la pantalla de
    pedir enlace. El backend responde igual en los cuatro casos.
12. Si la contraseña nueva no cumple la regla o las dos no coinciden, el error aparece en
    el campo, sin gastar el token.
13. La contraseña nueva **no puede ser igual a la actual**: se avisa «Elige una contraseña
    distinta a la anterior».

### Reglas por público

14. **Portal (candidato):** solo cuentas de candidato (`es_equipo = false`) de la
    organización plataforma. **Panel (equipo):** solo cuentas `es_equipo = true`. Un correo
    de candidato pedido en el panel (o al revés) recibe el mensaje neutro y no se envía nada.
15. Si un mismo correo tiene cuentas de equipo en más de una organización (el login del
    panel ya lo admite), se genera un enlace por cuenta y el correo dice a qué empresa
    corresponde cada uno.
16. El enlace del correo de equipo cae siempre en la pantalla del panel, y el del
    candidato en la del portal. Ninguno puede aterrizar en la pantalla del otro público.

## Referencia visual

Los bocetos fijan la **disposición**, no medidas ni colores. Rigen el `DESIGN.md` del
repositorio `RenaserOsPostulantes` (acento único violeta `--activo`, «EX · Portal del
candidato»), además de los componentes de formulario existentes (campos, botones y avisos de
`Ingresar.tsx` y `Entrar.tsx`). **No** el `DESIGN.md` de este repositorio
(`RENASER-RECLUTAMIENTO`): describe un sistema distinto («RENASER OS», monocromático) y no
gobierna el panel ni el portal vivos.

**1. Portal · pedir enlace (`/clave`, sustituye la pantalla actual)**

```
← Volver a entrar
Correo  [ tu@correo.com          ]
                    [Enviar enlace]
¿No te llega? Escríbenos a talento@renaser.pe
```

**2. Enlace enviado (mismo mensaje exista o no la cuenta)**

```
Revisa tu correo. Vale por 60 minutos.
[Reenviar enlace (en 45 s)]  [Usar otro correo]
```

**3. Panel · login con enlace nuevo (pantalla de pedir enlace, sin la línea de talento)**

```
Correo       [        ]
Contraseña   [        ]
[Entrar]
¿Olvidaste tu contraseña?  ← nuevo
```

**4. Elegir la contraseña nueva (portal `/restablecer`, panel `/admin/restablecer`)**

```
Contraseña nueva     [••••••••] 👁
Repetir contraseña   [••••••••] 👁
              [Guardar contraseña]
```

Con error se marca el campo y se dice el motivo debajo (no coinciden, menos de 8/12
caracteres, igual a la anterior, espacios de sobra).

**5. Enlace vencido, usado o inválido (un solo texto para los tres casos)**

```
Este enlace ya no sirve.
[Pedir un enlace nuevo]
```

**6. Éxito (va a la pantalla de entrada de su mundo, sin sesión iniciada)**

```
✓ Contraseña cambiada exitosamente
Correo       [        ]
Contraseña   [        ]
[Entrar]
```

**Estados a dibujar:** normal, enviando, enviado, con error de campo, error de red, enlace
inválido y éxito.

**Móvil:** las cuatro pantallas se apilan a ancho completo con los botones al 100 %; el ojo
queda dentro del campo y el teclado no tapa el botón.

**Queda a criterio de quien implementa:** espaciado, iconos y animaciones, siempre dentro del
`DESIGN.md` de `RenaserOsPostulantes` y sin cambiar los textos ni el comportamiento de la
spec.

## Criterios de aceptación

- AC-01: Dado un candidato con cuenta activa y correo real, cuando pide el enlace en
  `/clave`, entonces recibe un correo con un enlace a `/restablecer?token=…` y ve el mensaje
  neutro de confirmación.
- AC-02: Dado un correo que no existe en el sistema, cuando se pide el enlace, entonces ve
  exactamente el mismo mensaje que en AC-01, no se crea ningún token y no se envía correo.
- AC-03: Dada una cuenta desactivada, o una de carga masiva con correo inventado, cuando se
  pide el enlace, entonces ve el mismo mensaje neutro y no se crea ningún token.
- AC-04: Dado un usuario de equipo activo, cuando pide el enlace desde el login del panel,
  entonces recibe un correo cuyo enlace abre la pantalla de restablecer **del panel**.
- AC-05: Dado un correo de candidato pedido desde el panel (o uno de equipo desde el portal),
  cuando se envía, entonces se ve el mensaje neutro y no se envía nada.
- AC-06: Dado un enlace válido, cuando el usuario escribe una contraseña nueva válida dos
  veces iguales, entonces la contraseña cambia, ve el aviso de éxito y llega a la pantalla
  de entrada sin sesión iniciada.
- AC-07: Dado el cambio de AC-06, cuando entra con la contraseña nueva, entonces accede; y
  con la contraseña vieja, no accede.
- AC-08: Dado el mismo enlace ya usado, cuando se intenta usar otra vez, entonces se muestra
  «Este enlace ya no sirve. Pide uno nuevo.» y la contraseña no cambia.
- AC-09: Dado un enlace vencido (más de 60 minutos), cuando se usa, entonces el mismo
  mensaje de AC-08.
- AC-10: Dadas dos solicitudes seguidas para la misma cuenta, cuando se usa el enlace del
  primer correo, entonces falla con el mensaje de AC-08 y el del segundo funciona.
- AC-11: Dada una contraseña nueva de menos de 8 caracteres en el portal (o de menos de 12
  en el panel), cuando se envía, entonces el error aparece en el campo y el enlace sigue
  siendo válido.
- AC-12: Dadas dos contraseñas que no coinciden, cuando se envía, entonces se avisa en el
  campo de confirmación y no se llama al servidor.
- AC-13: Dada una contraseña nueva igual a la actual, cuando se envía, entonces se rechaza
  con el aviso correspondiente y el enlace sigue siendo válido.
- AC-14: Dado un correo bloqueado por 5 intentos de login fallidos, cuando restablece su
  contraseña con éxito, entonces puede entrar de inmediato con la nueva sin esperar el
  bloqueo.
- AC-15: Dadas 4 solicitudes seguidas para el mismo correo en una hora, cuando se hace la
  cuarta, entonces se ve el mismo mensaje neutro pero no se crea un token ni se envía correo.
- AC-16: Dado el token en la URL de restablecer, cuando la pantalla carga, entonces el
  token desaparece de la barra de direcciones y del historial (`replace`).
- AC-17: Dado un correo de equipo con cuentas en dos organizaciones, cuando se pide el enlace,
  entonces llegan dos enlaces, cada uno indicando su empresa, y cada uno cambia solo la
  contraseña de su cuenta.
- AC-18: Dadas las solicitudes y los cambios, cuando se consulta la auditoría, entonces
  existe un registro de cada uno con cuenta y momento, y en ningún registro, log ni
  respuesta aparece el token.
- AC-19: Dado el backend con transporte de correo `log`, cuando se pide el enlace, entonces
  la fila de `correo_enviado` queda como NO_ENVIADO con el texto completo (así se prueba en
  local) y el usuario ve el mismo mensaje neutro.
- AC-20: Dado un usuario que navega con teclado y lector de pantalla, cuando usa las tres
  pantallas (pedir, restablecer, éxito), entonces todos los campos tienen etiqueta, los
  errores se anuncian y el foco pasa al mensaje al enviar.
- AC-21: Dada una pantalla de móvil, cuando se usan las pantallas nuevas, entonces se leen y
  se completan sin scroll horizontal.

## Casos límite y errores

- Datos vacíos o inválidos: correo vacío o mal formado, error en el navegador y sin llamada
  al servidor. Token ausente en la URL: «El enlace está incompleto. Cópialo entero desde el
  correo.» con salida a pedir uno nuevo.
- Usuario sin permisos: no aplica; los endpoints son públicos por naturaleza, y por eso no
  revelan si la cuenta existe ni distinguen el motivo de un token inválido.
- Recarga, reintento o doble envío: pulsar «Enviar enlace» varias veces respeta el límite y
  la cuenta regresiva del botón; el formulario de contraseña nueva se deshabilita mientras
  se envía; usar el enlace dos veces (o desde dos pestañas) deja funcionar solo la primera.
- Otros casos relevantes:
  - **Correo fuera de bandeja o retrasado:** el mensaje aconseja mirar spam y ofrece
    «Reenviar» tras 60 segundos; el alternativo `talento@renaser.pe` sigue visible en el
    portal.
  - **Servidor de correo caído:** el transporte no lanza; la solicitud queda registrada como
    FALLIDO en `correo_enviado` y el usuario ve el mismo mensaje neutro (no se le puede
    decir algo distinto sin revelar que la cuenta existe).
  - **Falta la plantilla activa de una organización:** `ServicioCorreo` hoy omite el envío
    en silencio. Para este caso el correo debe salir con la plantilla de la plataforma como
    recambio, y si tampoco existe se registra un error ruidoso; nunca se debe prometer un
    correo que no sale.
  - **Sesiones abiertas** anteriores al cambio siguen válidas hasta su vencimiento (ver
    «Queda fuera»).
  - **Enlaces en clientes de correo que precargan URLs:** abrir el enlace no gasta el token;
    solo lo gasta el envío de la contraseña nueva.

## Datos y compatibilidad

- Información que debe guardarse: una tabla nueva (`recuperacion_clave`) con el usuario, el
  hash del token, cuándo se creó, cuándo vence, cuándo se usó y, si aplica, cuándo se
  invalidó. Solo se guarda el hash. Los registros vencidos se pueden limpiar con la tarea de
  mantenimiento existente o quedarse; no deben acumularse sin límite de consulta.
- Migración Flyway nueva (el siguiente número libre al implementar, hoy V59; comprobar que
  ninguna rama abierta reclame el mismo número). Debe sembrar dos plantillas de correo
  (`RECUPERAR_CLAVE_CANDIDATO` y `RECUPERAR_CLAVE_EQUIPO`) para la organización plataforma,
  con los textos en español y las variables `{{enlace}}`, `{{vence}}`, `{{nombre_empresa}}`.
  No debe reescribir plantillas existentes.
- Parámetro configurable nuevo: `minutos_vida_recuperacion` (por defecto 60), con el
  patrón de `parametros.entero(...)` que ya usan los límites de login.
- Endpoints nuevos (públicos, en `ConfiguracionSeguridad`): `POST /api/v1/portal/auth/recuperacion`,
  `POST /api/v1/portal/auth/restablecer`, `POST /api/v1/panel/auth/recuperacion`,
  `POST /api/v1/panel/auth/restablecer`. La solicitud responde 202 siempre con cuerpo
  vacío. El restablecimiento responde 204 en éxito y el mismo 401 (sin decir el motivo) en
  cualquier token no utilizable.
- Frontend (`RenaserOsPostulantes`): `Clave.tsx` pasa a formulario; nuevas pantallas
  `Restablecer` (portal) y `RestablecerPanel`; rutas nuevas en `rutas.ts`; enlace en
  `Entrar.tsx`; funciones en `api/portal.ts` y `panel/api`. Los textos de `Clave.tsx` y
  `Entrar.tsx` que dicen «no hay recuperación» se actualizan. Pruebas unitarias para cada
  pantalla y regla.
- Comportamiento para registros existentes: ninguno cambia. Las contraseñas actuales siguen
  valiendo hasta que alguien las restablece.
- Restricciones de compatibilidad: el enlace del equipo se arma con `renaser.panel.url` y el
  del candidato con `renaser.portal.url`, como el resto de correos. Portal y panel son la
  misma aplicación web, así que las rutas deben ser distintas (`/restablecer` y
  `/admin/restablecer`), y hay que respetar que `renaser.panel.url` puede no llevar `/admin`
  (ver el comentario de `rutas.ts` sobre `/invitacion`). Añadir endpoints y una tabla es
  compatible hacia atrás.
- Seguridad: comparación del hash del token en tiempo constante o por búsqueda de hash,
  nunca del token en claro; sin registrar el token en logs; el señuelo de tiempo del login
  (`hashSenuelo`) se replica para que responder «no existe» no sea más rápido.

## Verificación

- Actor/rol y datos de prueba necesarios: un candidato con correo real y cuenta activa; una
  cuenta de carga masiva con correo `@cv-convocatoria.local`; una cuenta desactivada; un
  usuario de equipo activo; un correo de equipo con cuentas en dos organizaciones (si la
  base local lo permite; si no, cubrirlo con prueba automatizada). Backend local con
  transporte `log`: el enlace se lee de `correo_enviado`.
- Recorridos que deben quedar como E2E repetibles:
  1. Candidato: pedir enlace → leer el enlace de `correo_enviado` → elegir contraseña →
     entrar con la nueva → fallar con la vieja.
  2. Equipo: el mismo recorrido desde el login del panel.
  3. Enlace usado dos veces y enlace vencido.
  4. Correo inexistente: mensaje idéntico y ninguna fila nueva de token.
  5. Bloqueo por 5 intentos fallidos y liberación tras restablecer.
- Riesgos que QA debe explorar con navegador: enlace del correo de equipo aterrizando en la
  pantalla del portal (o al revés); token que queda en la barra o el historial; doble clic
  en «Enviar enlace» y en «Guardar»; cuenta regresiva del reenvío; móvil y teclado; que
  ningún mensaje distinga entre «no existe» y «existe».
- Resultado que quiero comprobar personalmente: con SMTP real, pedir el enlace a mi propio
  correo y ver que llega, que abre la pantalla correcta y que después entro con la
  contraseña nueva.

## Dependencias y dudas

- Requiere: cambios en los dos repositorios (backend `RENASER-RECLUTAMIENTO` y frontend
  `RenaserOsPostulantes`). **En producción el transporte de correo debe estar en `smtp`**
  (`CORREO_TRANSPORTE`, `CORREO_REMITENTE` y credenciales SMTP); con `log` la funcionalidad
  parece funcionar pero nadie recibe nada. Confirmarlo antes de dar por terminada la
  entrega.
- Decisiones pendientes: ninguna.
