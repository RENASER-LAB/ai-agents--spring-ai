# APIs del multiempresa · para quien construya las pantallas

> ✅ **Implementado desde el 26/08/2026.** La referencia viva es **Swagger**
> (`/swagger-ui.html`), que se genera del código y siempre está al día. Este documento
> cuenta lo que Swagger no cuenta: qué cambia, qué rompe y qué reglas hay detrás.

El diseño completo está en `docs/superpowers/specs/2026-08-25-*.md` (seis piezas). El
detalle endpoint por endpoint, en [09-APIS.md](09-APIS.md).

---

## ⚠️ Lo que ROMPE el frontend actual

**`POST /portal/postulaciones` ahora exige `aceptaTratamiento=true`.** Sin ese parámetro
responde **400**. El formulario de postular necesita una casilla nueva: «Acepto que
[empresa] trate mis datos», con el texto que devuelve el endpoint nuevo de abajo. Es la ley
29733: el candidato consiente con **cada empresa** a la que postula, no solo con la
plataforma al crear su cuenta.

Es el único cambio que rompe. Todo lo demás es aditivo.

---

## El portal del candidato: qué cambia

**El tablón ahora mezcla empresas — a propósito.** `GET /portal/vacantes` devuelve las
vacantes publicadas de **todas** las empresas activas, y cada una trae `nombreEmpresa`.
Píntalo: el candidato debe saber a quién le está postulando. Las vacantes de una empresa
suspendida desaparecen del tablón **y su detalle da 404** — un enlace guardado puede dejar
de existir; la pantalla debe tolerarlo sin romperse.

**El flujo de postular gana un paso:**

1. `GET /portal/vacantes/{id}/consentimiento` (público) → el texto legal que se firma al postular
   a esa vacante, **ya compuesto con el nombre de la empresa** que la publica.
2. `POST /portal/postulaciones` con `aceptaTratamiento=true` → la aceptación queda firmada
   (texto compuesto, versión, fecha, IP, navegador) a nombre de esa postulación.

**«Mis postulaciones» cruza empresas.** `GET /portal/postulaciones` sigue devolviendo todas
las del candidato, ahora cada una con su empresa. La cuenta es una sola (de la plataforma);
los procesos son de cada empresa.

**Y ese texto ya no repite lo de la cuenta** (14/09/2026). Dice solo lo que es de esa empresa
—que ella publica la vacante y ella decide sobre la candidatura, y que el permiso no alcanza a
las demás del portal—, porque lo técnico ya se aceptó con Renaser al crear la cuenta.

⚠️ **Es un solo texto para todas las empresas**, con un hueco donde va el nombre de quien publica
la vacante. El hueco se rellena en el backend y no cruza la frontera: lo que llega al portal es
el texto listo para enseñar y para firmar. Ninguna empresa tiene texto propio ni puede
publicarlo.

⚠️ **Y desde el 15/09/2026 el portal ya no pinta una casilla ahí.** Encima del botón se dice
quién recibirá la candidatura y se enlaza el texto entero; enviar es el acto. El backend **sigue
exigiendo `aceptaTratamiento`** —es lo que corta el paso a quien llame a la API por su cuenta— y
la firma se sigue guardando igual. Lo que cambió es cómo se da el permiso, no que se dé.

Lo que SÍ cambia al crear cuenta: la casilla obligatoria de `POST /portal/cuentas` pasó a
llamarse **`aceptaPlataforma`** (era `aceptaProceso`) y `GET /portal/consentimientos/textos`
devuelve ahora **los tres** textos de la plataforma —`PLATAFORMA`, `PROCESO` y
`FUTUROS_CONTACTOS`— donde antes devolvía dos. El de `PROCESO` sale compuesto con «la empresa
que publica la vacante», porque ahí no hay ninguna concreta. Es un cambio de contrato: back y
front salieron juntos.

Lo que NO cambia: el login del candidato, la evaluación, el perfil
([APIS-PERFIL-DEL-CANDIDATO.md](APIS-PERFIL-DEL-CANDIDATO.md)) — todo igual.

---

## El panel de empresas: el mundo nuevo

**Dos logins separados, dos pantallas.** El portal del candidato tiene el suyo (con
registro público, como siempre). El panel tiene el suyo propio y **sin registro**: las
cuentas nacen solo por invitación.

| Pantalla | Endpoint | Notas |
|---|---|---|
| Login del panel | `POST /panel/auth/login` `{correo, contrasena}` | Devuelve `{token, usuarioId}`. Mismo 401 genérico exista o no el correo; **401 con mensaje claro si la organización está suspendida**; 429 con `Retry-After` tras demasiados intentos |
| Aceptar invitación | `POST /panel/auth/invitacion` `{token, nombre, apellidos, contrasena}` | El token llega en el enlace del correo (`?token=...`); el frontend lo manda **en el cuerpo**, nunca en la URL de la API. Contraseña mínima **12**. Un solo uso; vencida/usada/revocada dan el mismo error genérico. Devuelve sesión: el invitado entra directo |
| Invitar al equipo | `POST /panel/usuarios/invitaciones` `{correo, roles}` | La respuesta trae la **URL del enlace** por si se quiere mostrar/copiar además del correo que sale solo. `GET` lista, `DELETE /{id}` revoca |
| Pedir enlace de contraseña nueva | `POST /panel/auth/recuperacion` `{correo}` | **202 vacío siempre**, exista o no la cuenta. Si el correo tiene cuenta en varias empresas, sale un enlace por cada una |
| Elegir la contraseña nueva | `POST /panel/auth/restablecer` `{token, contrasena}` | El token llega en el enlace del correo (`/admin/restablecer?token=...`) y va **en el cuerpo**. Mínimo **12**. 204 sin sesión; el mismo 401 para cualquier enlace que no sirva |

**«Olvidé mi contraseña» existe desde el 22/09/2026**, en el panel y en el portal. Las reglas
completas —topes, qué pasa con cada caso, qué cuentas no pueden usarlo— están en
[09-APIS.md](09-APIS.md), «Cómo entrar». Si el correo no llega o la cuenta está desactivada,
el camino sigue siendo que su administrador lo invite de nuevo.

**El login de desarrollo (`dev-login`) está apagado en producción.** Ningún flujo del
frontend debe depender de él.

## El panel de la empresa: lo nuevo para su administrador

- **Textos legales**: `GET/POST /panel/textos-consentimiento`. El POST crea la versión
  nueva **y la publica**. Desde el 14/09/2026 **solo la plataforma puede usarlo**: los tres
  textos son suyos, y una empresa que intente publicar cualquiera de los tres recibe un **400**
  con un mensaje que lo explica. La pantalla de textos legales de una empresa no tiene por qué
  ofrecer el formulario: no hay nada que publique.
  Y con esto se cayó un freno que había: **publicar una vacante ya no exige tener texto propio**,
  porque el texto general ya existe y nombra a la empresa. La pantalla de vacantes puede dejar
  de avisar de eso.
- **Personalización de instrumentos**: `POST/DELETE /panel/organizacion/personalizacion/{instrumento}`
  (BANCO, PESOS, PLANTILLA_EVALUACION, PRUEBA). Apagada = usa el método de Renaser (solo
  lectura); encendida = copia propia editable. La pantalla debe distinguir «esto es de
  Renaser, se mira» de «esto es tuyo, se edita».
- El parámetro `tope_mensual_ia` **se ve pero no se edita** desde la empresa: lo administra
  la plataforma. No pintar el lápiz.

## La plataforma (solo Renaser)

Sección aparte del panel, visible solo con el permiso `administrar_plataforma` **y** siendo
la organización plataforma:

- `GET /panel/plataforma/empresas` — la ficha de cada empresa: estado, tope, banderas,
  consumo del mes. **No hay ni habrá** una vista de los candidatos de otra empresa: Renaser
  administra el continente, no el contenido.
- `POST /panel/plataforma/empresas` — alta con invitación al primer administrador.
- `POST …/{id}/suspension` y `…/reactivacion` (con motivo), `PUT …/{id}/tope-ia`,
  `POST/DELETE …/{id}/personalizacion/{instrumento}`.
- `GET /panel/plataforma/consumo?mes=YYYY-MM` — el gasto de IA por empresa y agente.

## Reglas transversales que la pantalla debe respetar

1. **Lo ajeno es 404, no 403.** Un recurso de otra empresa responde «no existe». No
   construyas lógica sobre distinguir «prohibido» de «inexistente».
2. **La calificación puede quedar «en espera» sin ser un error.** Si una empresa agota su
   tope mensual de IA, los trabajos nuevos esperan y el candidato ve su proceso «en curso»
   — que es la verdad. Ninguna pantalla debe pintar eso como fallo.
3. **Errores en RFC 7807** como todo el sistema: `title`, `status`, `detail` en lenguaje
   normal.
4. Un mismo correo puede existir como candidato y como equipo sin chocar: **son cuentas y
   puertas distintas**. No unifiques sesiones entre portal y panel.

## Para quien toca el código (26/08/2026)

Los seis specs con el porqué de cada decisión están en `superpowers/specs/2026-08-25-*.md`. Lo
que hay que saber antes de escribir una línea:

- **La plataforma es un dato, no un literal**: `organizacion.es_plataforma` (solo una puede
  serlo). El `findByCodigo("RENASER")` quemado ya no existe: se resuelve con
  `findByEsPlataformaTrue`.
- **Cuatro banderas de personalización** por organización (banco, pesos, plantillas de
  evaluación, pruebas), apagadas por defecto = leer el instrumento de la plataforma; encender =
  copiarlo. El ÚNICO punto que las interpreta es `organizacion/service/DuenoDelInstrumento`.
  **Leer resuelve, editar no**: con la bandera apagada se VE el método de la plataforma en solo
  lectura; mutar algo ajeno responde 404. Apagar archiva el banco propio, nunca lo borra (RF-138).
- **Dos logins separados**: el portal del candidato y el panel de empresas
  (`POST /panel/auth/login`), que solo autentica cuentas con `usuario.es_equipo`. Las cuentas
  del panel nacen SOLO por invitación (tabla `invitacion`, token de un solo uso, se guarda el
  hash). El candidato es de la plataforma: una cuenta, postula a cualquier empresa, y **su
  postulación nace en la organización de la vacante**.
- **El aislamiento tiene dos vigilantes**: la regla de ArchUnit que prohíbe `findById` suelto
  sobre repositorios de agregados con dueño (lista `LLAMADAS_SIN_DUENO_ACORDADAS` en
  `ArquitecturaTest`) y `FlujoDosEmpresasIT`. La única pantalla que mezcla empresas es el tablón
  público de vacantes, a propósito.
- **El borrado 29733 es de la plataforma**: los candidatos son cuentas de plataforma y la
  anonimización cruza empresas. Desde una empresa responde 403.
- **El consentimiento se firma con cada empresa** (V38): al crear la cuenta se consiente con la
  plataforma; al postular se acepta el texto `PROCESO` a nombre de LA EMPRESA de la vacante, y la
  fila queda con `postulacion_id`, IP, navegador y el texto tal como se leyó.
- **Y son tres tipos, no dos** (V54): `PLATAFORMA` es lo que se acepta con Renaser al crear la
  cuenta, `PROCESO` lo que se firma al postular y `FUTUROS_CONTACTOS` el opcional. **Los tres son
  de la organización plataforma**, incluido el de postular: es **uno solo para todas** las
  empresas, con un hueco donde va el nombre de quien publica la vacante, que se pone al leerlo.
  De ahí que **el alta de una empresa no le copie ningún texto**, que **ninguna empresa pueda
  publicar uno** (400 en los tres tipos) y que **publicar una vacante ya no exija tenerlo**: ese
  freno se retiró con la V54, y antes solo llegaba a servir cuando el alta repartía un borrador
  que nadie publicaba.
- ⚠️ **Con qué empresa se firmó no lo dice el dueño de la fila del texto**, que es siempre la
  plataforma. Lo dicen la postulación (`consentimiento.postulacion_id` → `postulacion`) y el
  texto guardado (`consentimiento.texto_firmado`), que lleva el nombre dentro. Cruzar por
  `texto_consentimiento.organizacion_id` era correcto con un texto por empresa, y dejó de serlo.
- **Cada llamada al modelo tiene precio** (V38-V39, V57): `ejecucion_ia.costo` se escribe al cerrar
  con la tarifa vigente de `tarifa_modelo`. La bitácora guarda el modelo QUE EL PROVEEDOR
  REPORTA, y por eso **todo modelo de `application.yaml` necesita su tarifa**: un IT recorre los
  modelos configurados —y desde el 17/09/2026 también los que elige `AgentModelSelector`— y exige
  tarifa vigente para cada uno. ⚠️ **Ese IT comprueba los nombres que se PIDEN, y el costo se
  anota por el que el proveedor RESPONDE**: el 10/09/2026 DeepSeek renombró su catálogo, dejó los
  nombres viejos como alias, y el costo salió NULL una semana con la prueba en verde. Lo cuenta
  [El modelo cambió de nombre](EL-MODELO-CAMBIO-DE-NOMBRE.md). Al 80% del tope un correo
  `TOPE_IA_AVISO` una vez por mes (en su PROPIA transacción; su fallo se traga); al 100% los
  trabajos nuevos nacen **EN_ESPERA** y el sondeo de atascados los despierta cuando vuelve el
  cupo. El retrato que cierra una tanda no pasa por el tope: sus insumos ya se pagaron.
- **Suspender una empresa la congela también para el gasto**: `organizacion.es_activa` lo leen
  el login del panel, `FiltroIdentidad` (corta los tokens vivos, solo tipo EQUIPO), el tablón
  público y la cola de IA. Los candidatos que ya estaban dentro NO se tocan.
- Los ITs son dos: `FlujoDosEmpresasIT` (alta, aislamiento, viaje del candidato, consentimiento,
  borrado, suspensión) y `FlujoPlataformaIT` (la vida entera del tope).
