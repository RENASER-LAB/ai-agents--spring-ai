# Las APIs del sistema

Sistema de selección de personal — Renaser Consulting
Versión 1.9 · 2026-09-06 · Cubre **las cinco etapas del embudo**: postulación, Perfil Integral,
prueba del puesto, simulación de trabajo, validación práctica y decisión final

Este documento explica las APIs para quien las va a consumir: el panel de la empresa y el
portal del candidato, las dos caras del frontend `RenaserOsPostulantes`. **La referencia viva es Swagger**, en `http://localhost:8081/swagger-ui.html`
cuando la aplicación corre en local: ahí están los cuerpos exactos, se prueban las llamadas y
siempre está al día porque se genera del código. **Es el 8081, no el 8080** — el perfil `local`
mueve la aplicación de puerto porque el 8080 suele estar ocupado por Adminer, que responde 200 a
todo y hace que un frontend mal apuntado parezca que funciona. Este documento cuenta lo que Swagger no cuenta: cómo entrar,
qué puerta usar y las reglas que no se ven en un esquema.

---

## Las dos puertas

Todo vive bajo `/api/v1/`, en dos zonas con reglas distintas:

| Puerta | Quién la usa | Cómo se identifica |
|---|---|---|
| `/api/v1/portal/**` | El candidato | Token propio, de crear cuenta y entrar con correo y contraseña |
| `/api/v1/panel/**` | El equipo de cada empresa, Renaser incluida | Token de equipo, con correo y contraseña. Las cuentas nacen **solo por invitación**: el panel no tiene registro público |

El token va en cada llamada, en la cabecera `Authorization: Bearer <token>`.

Un token de candidato **no abre** el panel, ni al revés. Y dentro del panel, cada acción exige su
permiso: quien no lo tiene recibe un **403 con explicación**, no un error opaco. Además el
permiso tiene **alcance**: el responsable de un área solo ve las postulaciones de sus vacantes,
aunque llame al mismo endpoint que Talento.

Los tres alcances se comportan igual en todos los endpoints del panel que cuelgan de **una
postulación o de una vacante concreta**, porque **lo decide un solo sitio**
(`AlcanceSobreLaVacante`): con `TODO` se ve todo lo de la empresa; con `SUS_VACANTES`, solo lo
de las vacantes que esa persona dirige, y lo demás responde **404** y no 403 —un 403
confirmaría que ese `{id}` existe—; y con **`PROPIO` no se alcanza ninguna fila**: las listas
salen vacías y cualquier `{id}` es 404. `PROPIO` es el alcance del portal, donde sí hay algo
que sea de quien mira; en el panel quien mira nunca es el candidato de la fila. Ningún rol lo
tiene sembrado así en un permiso que se acote por vacante —el único `PROPIO` que la V9 le da a
un rol de panel es `crear_solicitud`, que ni siquiera mira su alcance—, pero desde la V40 se
pone con un solo `PUT`, así que los endpoints lo contemplan.

Dos familias tienen su propia regla y se explican donde toca: la **solicitud de talento**, cuyo
dueño es alguien del equipo y no una vacante, y las **sesiones de simulación**, que sirven a
varias vacantes a la vez —así que «es tuya» no se pregunta de la misma forma, y el conteo de
inscritos tiene además un cuarto caso: quien no tiene `ver_inscritos_simulacion`—.

## Cómo entrar

**El candidato:** `POST /portal/cuentas` para crear la cuenta (exige aceptar el tratamiento de
datos **con Renaser** —no el de ninguna vacante, que se firma al postular— y decir en qué ciudad
vive; el consentimiento de futuros contactos es aparte y opcional), y
`POST /portal/auth/login` con correo y contraseña. Si no cuadran, responde **401** con el mismo
texto tanto si el correo no existe como si la contraseña es otra: decir cuál de las dos falló le
regalaría a un atacante la lista de correos registrados. Tras varios intentos fallidos seguidos (configurable, arranca
en 5), la entrada se bloquea unos minutos y responde **429** con la cabecera `Retry-After` y el
campo `segundosDeEspera`, para que la pantalla pueda decir cuánto falta en vez de adivinarlo.

**El candidato que llega por correo:** `POST /portal/auth/acceso` canjea el **enlace de acceso**
que va en el aviso de que su postulación avanza (V26), sin contraseña. Es de un solo uso y es la
única puerta para quien fue cargado desde una carpeta de currículums y nunca creó una cuenta. El
equipo puede generar uno nuevo con `POST /panel/postulaciones/{id}/enlace-acceso`.

**El equipo:** `POST /panel/auth/login` con correo y contraseña. Solo entran cuentas de
equipo: un candidato con su contraseña correcta recibe el mismo 401 que un correo que no
existe — la contraseña del portal no abre el panel. El bloqueo por intentos es el mismo que
el del candidato. RENASER OS quedó dormido: cuando se retome será añadir un proveedor de
identidad, no rehacer este login.

**La cuenta de equipo nace por invitación.** Un administrador invita
(`POST /panel/usuarios/invitaciones`) y el invitado abre el enlace del correo y canjea el
token en `POST /panel/auth/invitacion`, poniendo su nombre y su contraseña — mínimo doce
caracteres, porque una cuenta de panel ve los datos de muchas personas. El token es de un
solo uso y caduca (parámetro `dias_invitacion`, 7 por defecto); una invitación vencida,
revocada o ya canjeada responde siempre el mismo 401.

**El login de desarrollo** (`POST /panel/auth/dev-login`) sigue existiendo para local y para
las pruebas, y está **apagado por defecto** (`app.seguridad.dev-login-activo: false`): solo
`application-local.yaml` y las pruebas de integración lo encienden.

## Los errores hablan claro

Todos los errores salen en el mismo formato (RFC 7807): un `title`, un `status` y un `detail`
en lenguaje normal.

| Código | Qué significa |
|---|---|
| 400 | La petición incumple una regla: «toda transición manual exige un motivo escrito» |
| 401 | Falta el token, venció, o el correo y la contraseña no cuadran al entrar |
| 403 | El token vale, pero ese permiso no lo tienes |
| 404 | No existe, **o no te toca verlo**: el alcance también responde 404 |
| 409 | El estado actual no lo permite: «ya postulaste a esta vacante» |
| 413 | El archivo pasa de 10 MB |
| 429 | Demasiados intentos de entrar seguidos. Trae `Retry-After` con los segundos que faltan |

---

## El portal del candidato (`/api/v1/portal`)

| Método y ruta | Qué hace | Quién |
|---|---|---|
| GET `/vacantes` | Las vacantes publicadas **de todas las empresas activas**, cada una con el nombre de la suya. Las de una empresa suspendida no salen. Cada una trae su `remuneracion` | Cualquiera, sin token |
| GET `/vacantes/{id}` | El detalle público, con los requisitos indispensables y la `remuneracion` | Cualquiera |
| GET `/vacantes/{id}/consentimiento` | El texto que se firma al postular a esa vacante, **ya compuesto con el nombre de la empresa** que la publica, más ese nombre aparte. Es **un solo texto para todas las empresas**, con un hueco donde va el nombre: ninguna tiene el suyo. Lo pide la pantalla de postular y también la política de privacidad cuando se llega a ella desde una vacante | Cualquiera |
| GET `/consentimientos/textos` | Los **tres** textos vigentes de la plataforma: `PLATAFORMA` (el obligatorio de crear la cuenta), `PROCESO` (el de postular) y `FUTUROS_CONTACTOS` (el opcional). **Desde el 14/09 son tres y no dos**, y `PLATAFORMA` ocupa el sitio que tenía `PROCESO` en el registro. El de `PROCESO` sale compuesto con «la empresa que publica la vacante», porque aquí no hay ninguna concreta: **el hueco no cruza la frontera del backend**. Lo lee la política de privacidad pública, que los enseña enteros | Cualquiera |
| GET `/catalogos/ubigeo` | Dónde se puede decir que uno vive: las **196 provincias** del Perú y «Fuera del Perú», cada una con su departamento, ordenadas por departamento y nombre. Es `{codigo, nombre, departamento}`, y el departamento viene vacío solo en `EXT` | Cualquiera, sin token |
| POST `/cuentas` | Crear la cuenta y registrar los consentimientos. Desde el 31/08 pide además **la ciudad** (`ciudadUbigeo`), obligatoria: un código que el catálogo no ofrezca es un 400. **Desde el 14/09 la casilla obligatoria se llama `aceptaPlataforma`** y no `aceptaProceso`: es un cambio de contrato, y el portal salió con él | Cualquiera |
| POST `/auth/login` | Entrar; devuelve el token | Cualquiera |
| GET `/auth/sesion` | Cómo se llama quien tiene el token. El portal entra una vez y guarda el token; en la segunda visita nadie le había dicho el nombre (06/09) | Candidato |
| POST `/postulaciones` | Postular: CV (PDF o Word, máx. 10 MB; **desde el 06/09 opcional si el perfil ya tiene uno**: sin adjuntar se usa el del perfil, copiado a la empresa de la vacante, y adjuntando otro ese vale solo para esa vacante y el del perfil no cambia), enlaces, el resultado del que se siente orgulloso, la confirmación de los requisitos y `aceptaTratamiento` (obligatorio): la aceptación queda firmada con IP y navegador, a nombre de esa postulación, y **con el texto tal como se le enseñó**, con el nombre de la empresa dentro. **Desde el 15/09 el portal ya no pinta una casilla para esto** —enviar la candidatura es el acto—, pero el dato se sigue exigiendo para cortarle el paso a quien llame a la API por su cuenta. **Lleva además `pretensionMonto` y `pretensionMoneda`**: obligatorios si la vacante publica lo que paga —faltando, 400—, e ignorados si no | Candidato |
| GET `/postulaciones` | Sus postulaciones, con la empresa de cada una, estado, días sin cambio, **qué rendirá en la etapa técnica** (`instrumentoEtapaTecnica`: la prueba del puesto o el cuestionario), lo que paga la vacante hoy (`remuneracion`, con su `actualizadaEn`), lo que él pidió aquí (`miPretension`) y cuántos avisos de ese proceso sigue sin ver (`avisosSinLeer`) | Candidato |
| GET `/postulaciones/{uuid}` | El detalle de una suya, con el historial completo | Candidato |
| POST `/postulaciones/{uuid}/retiro` | Retirarla. **No borra sus datos**: eso se pide aparte | Candidato |
| GET `/avisos` | La campana (`V56`): sus avisos con los nuevos arriba, y cuántos le quedan sin ver (`{sinLeer, avisos}`). **Sin permiso propio**: todo candidato con sesión tiene campana, y el servicio filtra por el usuario del token | Candidato |
| POST `/avisos/lectura` | Marcar leídos todos los suyos. Devuelve `{marcados}` | Candidato |
| POST `/avisos/{id}/lectura` | Marcar leído uno | Candidato |
| POST `/consentimientos/futuros/retiro` | Retirar el consentimiento de futuros contactos | Candidato |
| POST `/solicitudes-borrado` | Pedir el borrado de sus datos | Candidato |
| GET `/evaluacion/{uuid}` | Su evaluación: las preguntas en **su** orden y lo que lleva respondido | Candidato |
| POST `/evaluacion/{uuid}/inicio` | Empezar. La primera vez elige qué preguntas le tocan | Candidato |
| PUT `/evaluacion/{uuid}/respuestas/{preguntaId}` | Guardar una respuesta. **Mandarla vacía la borra** (16/09/2026): esa pregunta se queda sin responder, y eso no es un error | Candidato |
| POST `/evaluacion/{uuid}/entrega` | Entregar. Ya no se cambia, y pasa a calificarse | Candidato |
| GET `/cuestionario-tecnico/{uuid}` | Su cuestionario técnico, cuando la vacante rinde ese instrumento: las preguntas **sin la PRESENCIAL** y sin la guía de calificación | Candidato |
| POST `/cuestionario-tecnico/{uuid}/inicio` | Empezar. Aquí arranca el reloj, si la vacante fijó minutos | Candidato |
| PUT `/cuestionario-tecnico/{uuid}/respuestas/{preguntaId}` | Guardar una respuesta. Solo texto: aquí no se suben archivos. **Mandarla vacía la borra**, igual que en la evaluación | Candidato |
| POST `/cuestionario-tecnico/{uuid}/entrega` | Entregar. Pasa a `PRUEBA_CALIFICANDO` y lo califica el agente EVALUADOR_TECNICO | Candidato |

**El candidato es de la plataforma.** Una sola cuenta, y con ella postula a la vacante de
cualquier empresa: su postulación nace en la empresa de la vacante, que es la que la ve en su
panel. El tablón de vacantes es la única pantalla que mezcla empresas — a propósito.

**La ciudad se pregunta una sola vez, y es la única del alta.** `ciudadUbigeo` es el único campo
nuevo del formulario de registro, y **a quien ya tiene cuenta no se le pide nunca**: los que se
registraron antes se quedan sin ciudad, que es un estado normal y que el panel sabe pintar. Por
eso el catálogo es la única ruta de `/catalogos` que responde sin token —el desplegable tiene que
cargar antes de que exista la cuenta—, y el permiso se abrió con esa ruta exacta y no con un
comodín, para no destapar de paso los otros dos catálogos.

**La evaluación es de quien la responde.** Todo entra por el código de la postulación, no por
el id de la evaluación, y una que no es suya responde 404 — un 403 ya confirmaría que existe.

**Lo que nunca sale al portal:** el puntaje de cada opción, la lógica interna de la pregunta y
el código de dimensión que mide. No es que se filtren al serializar: los contratos no tienen
ese campo. Si la clave llega al navegador, el banco entero queda inutilizado.

**La regla que importa al postular:** el formulario pregunta por cada requisito indispensable de
la vacante y el candidato confirma cuáles cumple. Cualquier requisito activo no confirmado cierra
la postulación en el acto (`NO_CONTINUA`), con la regla exacta escrita en su historial. Es el
**único** descarte automático de todo el sistema.

**El sueldo se pide solo si la vacante enseña el suyo.** Es un trato simétrico y va en el mismo
campo de la vacante: publicar lo que paga obliga a quien postula a declarar cuánto quiere ganar
—`pretensionMonto` y `pretensionMoneda`, o 400—, y esconderlo lo libera, tanto que lo que mande
se ignora. La cifra es **entera**, entre 100 y 1 000 000, en `PEN` o `USD`; sin decimales, porque
con ellos no hay forma de distinguir «3,50» de un 3500 mal tecleado. Una vacante que no lo
publica devuelve `remuneracion.tipo: "OCULTA"` y **no un hueco**: el portal tiene que poder
decirlo en voz alta, porque un hueco se lee como un fallo de carga. El detalle está en
[El sueldo, de los dos lados](EL-SUELDO-DE-LOS-DOS-LADOS.md).

**La campana es del usuario, no de la postulación.** `GET /avisos` no acepta filtros ni ids de
nadie más: son los del token. El punto se apaga al pulsar cada aviso o todos de una vez, **no al
abrir la campana**: enterarse de que hay algo no es lo mismo que haberlo leído, y apagarlo al
abrir apagaba también el punto de cada fila de «Mis procesos» sin que nadie hubiera leído nada.

**Una vacante eliminada (`V60`) no existe para el portal.** No sale en `GET /vacantes`; su
detalle y su `/consentimiento` responden 404, igual que `POST /postulaciones` sobre ella —también
si el formulario se abrió antes, porque la vacante se bloquea en modo compartido mientras dura la
postulación y una eliminación simultánea no deja ninguna a medias—. Sus procesos salen de
`GET /postulaciones` y `GET /postulaciones/{uuid}` responde 404, sea cual sea el estado en que
quedaron; el portal lo traduce a «Esta vacante ya no está disponible». En `GET /avisos` los avisos
anteriores de esa vacante se conservan pero llegan **sin enlace** —vacíos el de la postulación y
el de la vacante—, y el nuevo `VACANTE_ELIMINADA` nace así.

**Lo mismo en todas las puertas que entran por el código de la postulación** (22/09/2026):
`/postulaciones/{uuid}/retiro`; `/evaluacion/{uuid}` y `/cuestionario-tecnico/{uuid}` —ver,
`/inicio`, `/respuestas` y `/entrega`—; `/prueba/{uuid}` —ver, `/inicio`, responder, subir
entregables y `/entrega`—, y `/simulacion/{uuid}` —las fechas, inscribirse y la suya—. Sobre una
vacante eliminada responden **404 como postulación inexistente** («Postulación» por su código, el
mismo texto que una ajena), antes de mirar si hay examen, así que ni el reloj arranca ni se guarda
nada. Es una sola pregunta, `VacanteEliminada.exigirQueSuProcesoSigaExistiendo`, porque el enlace
de cualquiera de ellas sigue en el correo, el historial del navegador y los avisos viejos.
⚠️ **Un 404 de esas pantallas no basta para decir «vacante retirada»**: en la simulación también
es «todavía no elegiste fecha» y en la prueba, «todavía no te toca». El portal lo confirma
pidiendo `GET /postulaciones/{uuid}`: si ese también responde 404, la vacante ya no está.

### Guardar lo que el candidato escribe (16/09/2026)

Las tres pantallas donde alguien escribe —la evaluación del Perfil Integral, el cuestionario
técnico y la prueba del puesto— guardan desde hoy con la misma regla, y las diferencias que
quedan se dicen aquí abajo. Lo que ve el candidato está en
[Requisitos funcionales](01-REQUISITOS-FUNCIONALES.md), RF-52b y RF-52c; esto es lo que necesita
saber quien llama a los tres endpoints.

**Mandar una respuesta vacía la borra.** Antes se rechazaba con un 400 y el rechazo dejaba
**el texto anterior guardado**, así que la pantalla decía «vacía», el servidor la contaba como
respondida y no había forma de cuadrar las dos cifras. Ahora el vacío significa lo que parece:
esa pregunta se queda sin responder.

En la evaluación y en el cuestionario técnico eso no puede perderse en silencio, porque
**entregar se sigue rechazando mientras falte alguna respuesta**: una borrada sin querer se ve
antes de entregar, no después. ⚠️ **En la prueba del puesto el servidor no tiene esa red**: su
entrega comprueba los entregables obligatorios, nunca que estén todas las preguntas contestadas.
Ahí lo que protege al candidato es la pantalla, que antes de entregar le dice cuántas van en
blanco —y le deja entregar igual, porque en una prueba donde lo que se evalúa es el entregable
dejar una pregunta sin responder puede ser a propósito—. Y mientras responde, la línea de estado
la llama «Sin responder».

⚠️ **Los seis formatos del banco v3 son la excepción: en ellos vaciar no borra.** Son los que se
responden con varias piezas a la vez, y ahí una respuesta a medio armar no se distingue de una
que se está reordenando; la comprobación de forma existe justamente para que algo mal armado no
acabe convertido en una nota. Hoy no se nota, porque el portal ni siquiera manda una de esas a
medias — pero quien llame a la API por su cuenta sí verá la diferencia.

**Dos guardados de la misma pregunta a la vez chocan**, porque una pregunta tiene una sola
respuesta. El portal no manda el segundo hasta que vuelve el primero. En la evaluación y en el
cuestionario técnico el servidor cubre además las dos carreras que quedan —que otra petición
escriba primero, y que otra **borre** la fila justo mientras se guarda, que es nueva desde que
el vacío borra—: ninguna de las dos le sale al candidato como error.

⚠️ **Dos pestañas del mismo examen tardan hasta medio minuto en enterarse la una de la otra.**
Cada una se pone al día cuando vuelve a mirar, y si el candidato escribe nada más volver a una
pestaña esa noticia puede tardar dos vueltas: antes de guardar lo suyo se descarta cualquier
consulta que venga en camino, que es lo que impide que una foto anterior pise una respuesta
recién confirmada. **No se pierde nada** —lo confirmado manda—, pero explica por qué una pestaña
puede enseñar una versión más vieja que la otra durante unos segundos.

## El panel del equipo (`/api/v1/panel`)

### Solicitudes de Talento — Talento prepara, Dirección aprueba

| Método y ruta | Qué hace | Permiso |
|---|---|---|
| POST `/solicitudes` | Registrar una solicitud, con sus 3 a 5 resultados esperados y el análisis de capacidad (obligatorio) | `crear_solicitud` |
| GET `/solicitudes` · `/{id}` | Verlas, según el alcance de quien mira | `ver_solicitudes` |
| POST `/solicitudes/{id}/aprobacion` | Aprobar: queda ABIERTA y ya admite vacante | `aprobar_solicitud` |
| POST `/solicitudes/{id}/rechazo` | Rechazar, con motivo | `aprobar_solicitud` |

⚠️ **La solicitud es el único sitio del panel donde `PROPIO` sí alcanza algo**, y por eso su
alcance no lo resuelve el guardián de las vacantes: aquí «lo suyo» son las solicitudes que esa
persona pidió, y el vínculo es `solicitud_talento.responsable_usuario_id`. Con `ver_solicitudes`
en cualquier alcance que no sea `TODO` —`SUS_VACANTES` es lo que siembra la V9 al responsable
del área— se ven solo las propias, y una ajena responde 404.

### Vacantes

| Método y ruta | Qué hace | Permiso |
|---|---|---|
| GET/POST `/puestos` | El catálogo de puestos | `ver_vacantes` / `crear_vacante` |
| GET `/vacantes?archivadas=` · `/{id}` | Todas las vacantes. Desde el 19/09 cada una trae **el texto entero de la convocatoria** (descripción, propósito, responsabilidades, requisitos, modalidad, horario, ubicación, plazas y fechas), **`postulantesEnCarrera`** —cuántas postulaciones no están contratadas, cerradas ni en «no continúa»— y **`puedeEditar`**: si quien pregunta tiene `editar_vacante`, su alcance llega a esa vacante y no está cerrada. Es lo que usa el lápiz de la lista; no hay endpoint de «mis permisos». Desde el 21/09 la lista tiene **dos caras y el corte lo hace el servidor**: sin parámetro devuelve solo las **no archivadas** —por eso una archivada no reaparece al buscar, filtrar por estado ni paginar—, y con `archivadas=true` solo las archivadas, de la más reciente a la más antigua. Cada fila suma **`archivadaEn`** (cuándo se archivó, vacío si no lo está), **`puedeArchivar`** —`cerrar_vacante`, alcance, `CERRADA` y sin archivar; **no mira quién sigue en carrera**, a propósito: el icono aparece para que el modal pueda explicar por qué no se puede— y **`puedeDesarchivar`**. Desde la `V60` **ninguna de las dos caras trae eliminadas**, y cada fila suma **`puedeEliminar`**: `eliminar_vacante` y alcance, en cualquier estado, archivada incluida | `ver_vacantes` |
| GET `/vacantes/archivadas/conteo` | `{archivadas}`: el número del botón «Archivadas (N)» de la cabecera, contado en el servidor sobre el mismo universo que la lista —sin las eliminadas—. **Va con el permiso de lectura y no con el de archivar**: consultar lo guardado es leer | `ver_vacantes` |
| POST `/vacantes` | Crear en borrador. **Exige una solicitud aprobada** | `crear_vacante` |
| PUT `/vacantes/{id}` | **Corregir una vacante que no esté cerrada** (RF-14b). El cuerpo es el formulario del alta entero, **sueldo incluido** (`remuneracion` completa) y, si el sueldo cambia, `motivoRemuneracion`, obligatorio en una publicada. Compara campo a campo sin contar los espacios de los extremos y devuelve **`{huboCambios, postulantesAvisados}`**. Sin cambios no guarda, no audita y no avisa: reenviar la misma edición es inofensivo. Con cambios, audita `editar_vacante` con el valor anterior y el nuevo de cada campo, y el motivo del sueldo. Si la vacante está publicada y cambió algo que ve el candidato, deja **un solo aviso `VACANTE_ACTUALIZADA` en la campana** de cada postulación en carrera, **sin correo**; lo interno (responsable, forma de cierre, plazas, fecha de cierre) no avisa. Lo que el formulario no enseña no se toca: **la fecha de apertura nunca cambia al editar**; plazas y fecha de cierre solo cambian si su forma de cierre las enseña o si la forma de cierre cambió, y la fecha de cierre se compara por día y conserva la hora guardada si el día es el mismo. Los textos se guardan recortados y lo vacío como nulo. Todo valida antes de escribir: un sueldo incoherente, sin motivo o que oculte uno ya publicado no deja nada guardado. Un aviso que falla no deshace el cambio ni frena a los demás. **Respeta el alcance del rol**: fuera de él, 404 (por ejemplo, con `SUS_VACANTES` y una vacante que dirige otro). Cerrada, o cerrada a mitad de la edición, **409** sin cambios; **archivada, o archivada a mitad de la edición, 409** también, y se mira antes que el estado | `editar_vacante` |
| GET/POST `/vacantes/{id}/requisitos` · DELETE `/{requisitoId}` | Los requisitos indispensables. No se borran: se desactivan | `definir_requisitos_objetivos` |
| POST `/vacantes/{id}/plantilla-evaluacion` | Qué evaluación responderá quien postule. **Hace falta antes de publicar** | `elegir_plantilla_evaluacion` |
| POST `/vacantes/{id}/plantilla-prueba` | Qué prueba del puesto rendirá quien llegue a esa etapa. **Hace falta antes de publicar** | `elegir_plantilla_prueba` |
| POST `/vacantes/{id}/aplicacion-evaluacion` | Encender o apagar la evaluación del banco en esta vacante. Apagada, quien postule cae directo en la bandeja del equipo y su única evaluación es la prueba; publicar deja de exigir plantilla de evaluación | `elegir_plantilla_evaluacion` |
| POST `/vacantes/{id}/version-pesos` | Qué versión de pesos (publicada) rige la decisión de esta vacante. No recalcula nada hacia atrás | `publicar_version_pesos` |
| POST `/vacantes/{id}/cierre-prueba` | Fijar cuándo cierra la prueba de esta vacante, para todos. **Mueve también los intentos ya abiertos**, salvo los de quien tenga fecha propia. Con `cierraEn` vacío se quita y se vuelven a contar los días de la plantilla. **Una plantilla CRONOMETRADA también admite fecha** (desde el 22/09/2026): el reloj y la fecha conviven y al empezar rige el que caiga antes, así que la fecha es lo que impide abrir el examen después de que cerrara la convocatoria. Sigue rechazando una fecha ya pasada, una vacante cerrada y una vacante sin prueba del puesto | `elegir_plantilla_prueba` |
| GET/PUT `/vacantes/{id}/ficha` | La ficha del método CAZATALENTOS: las 10 preguntas al dueño, guardable a medias (BORRADOR). El tamaño (MICRO/MEDIA/GRANDE) se deriva de la gente en la empresa y la respuesta sugiere la `version_pesos` que toca; COMPLETA es lo que permite generar el cuestionario. Los riesgos van en orden de velocidad de daño y no admiten huecos | `ver_vacantes` / `editar_vacante` |
| GET `/vacantes/{id}/cuestionario-tecnico` | El cuestionario de la vacante: el borrador si hay, si no la publicada, con el estado de la generación (SIN_PEDIR·EN_CURSO·FALLIDA·LISTA) y si quedó desactualizado respecto a la ficha | `ver_vacantes` |
| POST `/vacantes/{id}/cuestionario-tecnico/generacion` | Pedir al agente REDACTOR el borrador (202). Exige la ficha COMPLETA; con una generación viva o la IA apagada responde `encolada=false`. Cuenta contra el tope mensual de IA | `editar_vacante` |
| PUT `/vacantes/{id}/cuestionario-tecnico/preguntas/{preguntaId}` | Corregir una pregunta del borrador con las palabras del dueño (enunciado y guía C3/C4/señal) | `editar_vacante` |
| GET `/vacantes/{id}` | La vacante con su configuración: qué evaluación y qué prueba tiene, sus pesos, y **qué instrumento y cuántos minutos** rigen su etapa técnica. Trae además **el plazo que rige hoy en su prueba**: `pruebaCierraEn`, la `modalidadPrueba` efectiva (con minutos propios de la vacante, cualquier prueba es CRONOMETRADA), `minutosPruebaVigentes`, `diasPruebaVigentes` y cuántos exámenes abiertos movería un cambio de fecha (`intentosAbiertosSinPlazoPropio`) y cuántos no (`intentosAbiertosConPlazoPropio`). ⚠️ **Esos cinco solo viajan aquí, no en la lista**: resolverlos cuesta dos consultas por vacante y la lista es la pantalla que más se abre; ahí llegan vacíos y el panel los lee como «sin dato». `pruebaCierraEn` sí viaja en las dos. Trae además lo mismo que cada fila de la lista: el texto de la convocatoria, `postulantesEnCarrera`, `puedeEditar`, `archivadaEn`, `puedeArchivar`, `puedeDesarchivar` y `puedeEliminar`. **Una archivada se consulta por aquí igual que cualquier otra**: es lo que sostiene su detalle de solo lectura. **Una eliminada responde 404** | `ver_vacantes` |
| POST `/vacantes/{id}/calificacion-automatica` | Encender o apagar el recorrido automático. Encendido, quien postule se califica solo y llega hasta la prueba del puesto sin que nadie confirme nada; la primera persona que hace falta decide quién va a la simulación. **Apagado de fábrica**: en automático cada postulante gasta una llamada al modelo desde que postula | `elegir_plantilla_evaluacion` |
| POST `/vacantes/{id}/instrumento-tecnico` | Qué se rinde en la etapa técnica de esta vacante —`PLANTILLA` (la prueba del puesto) o `CUESTIONARIO_TECNICO` (el cuestionario CAZATALENTOS)— y en cuántos minutos. **Uno de los dos, nunca los dos**: publicar exige tener listo el que se eligió. **Se frena en cuanto alguien EMPEZÓ su etapa técnica**, no al recibir la primera postulación: postular no es rendir, y quien no ha abierto nada no tiene nada que moverle debajo. Minutos vacíos = los del instrumento; si se ponen, **al menos 5**, rigen los DOS instrumentos y se leen al empezar el examen, así que corregirlos alcanza a todo el que aún no lo haya abierto | `elegir_plantilla_prueba` |
| POST `/vacantes/{id}/cuestionario-tecnico/publicacion` | Publicar el borrador: el acto humano que vuelve real el cuestionario. Re-pasa la aduana entera (cantidades del nivel, presencial donde toca, guía completa, temas prohibidos) y archiva la publicada anterior **de esta vacante** — los bancos por nivel ni se miran | `editar_vacante` |
| GET `/vacantes/{id}/plantillas-correo` | Qué avisos manda esta vacante con texto propio. Vacío = los de siempre | `ver_vacantes` |
| POST `/vacantes/{id}/plantillas-correo` · DELETE `/{avisoCodigo}` | Hacer que esta vacante mande otro texto en lugar del aviso que le tocaba, y devolverlo al de siempre. **Una plantilla es una por organización**: sin esto, cambiar el texto de una convocatoria se lo cambia a todas. Un texto retirado —hoy `REMUNERACION_ACTUALIZADA`— no se admite ni como aviso ni como sustituto: **400** | `editar_textos_correo` |
| GET/POST `/vacantes/{id}/barreras-criticas` | Las capacidades que ningún promedio alto compensa | `definir_barreras_criticas` |
| POST `/vacantes/{id}/remuneracion` | Cambiar **solo** lo que paga la vacante, desde la tarjeta del sueldo del detalle, con **motivo obligatorio**. Si está publicada, deja un aviso `REMUNERACION_ACTUALIZADA` **en la campana** de cada postulación en carrera; **desde el 19/09 no manda correo**. Quien cambia el sueldo desde el formulario lo hace por el `PUT` de la vacante, y entonces sale un solo aviso con todo lo que cambió: nunca dos avisos por un guardado. Guardar lo mismo que ya había no cuenta como cambio y no avisa a nadie. Devuelve `{antes, ahora, candidatosAvisados}`, que cuenta los avisos que de verdad se publicaron. **En una vacante publicada no se puede cambiar la decisión de publicar el sueldo o no** (409); el monto sí, y moverse entre fija y rango también. Respeta el alcance del rol, igual que el `PUT`: fuera de él, 404 | `editar_vacante` |
| POST `/vacantes/{id}/publicacion` | Publicar: aparece en el portal | `publicar_vacante` |
| POST `/vacantes/{id}/cierre` | Cerrar: frena postulaciones nuevas, **no arrastra las que van en marcha** | `cerrar_vacante` |
| POST `/vacantes/{id}/archivo` | **Archivar** (21/09): la vacante sale de la lista habitual y se consulta en «Archivadas». Exige que esté **`CERRADA`** —no la cierra de rebote: si no lo está, **409** diciendo en qué estado está— y que **no quede nadie en carrera**: si quedan, 409 con cuántos son. **Las dos condiciones se vuelven a mirar aquí** aunque el panel ya las enseñara en su modal, y la marca se escribe con un `UPDATE` condicional, así que un doble clic no deja dos archivos ni dos filas de auditoría. No cierra postulaciones, no libera la solicitud y no avisa a nadie. Archivar una ya archivada: 409 sin efectos. Fuera del alcance del rol, 404 | `cerrar_vacante` |
| DELETE `/vacantes/{id}/archivo` | **Desarchivar**: vuelve a la lista habitual, sigue `CERRADA` y **no reabre ninguna postulación**; el candidato ve su proceso igual antes y después. Lo que se borra es la marca de archivo, no la vacante —eliminarla es el `DELETE` de abajo—. Sobre una que no está archivada, 409 | `cerrar_vacante` |
| DELETE `/vacantes/{id}` | **Eliminar por borrado lógico** (21/09, RF-14d), en cualquier estado y también archivada. Cuerpo **`{motivo}`**, obligatorio: vacío o solo espacios, **400**. En una sola transacción marca `eliminada_en` con un `UPDATE` condicional, cierra cada postulación en carrera —`CERRADA`, motivo `VACANTE_ELIMINADA`, transición de persona y **sin correo**—, devuelve a `ABIERTA` la solicitud que la respaldaba si seguía en `CON_VACANTE` y audita `eliminar_vacante` con el motivo; si algo falla, no queda nada. Después deja a cada cerrada un aviso **`VACANTE_ELIMINADA` sin enlace** en la campana. Devuelve **`{postulacionesCerradas, postulantesAvisados}`**, que se cuentan por separado: un aviso que falla no deshace la eliminación, y el panel no dice que se avisó a quien no se avisó. Las postulaciones ya terminadas no cambian. Sin el permiso, **403**; fuera del alcance del rol, **404**; repetirla sobre una ya eliminada —o perder la carrera con otra eliminación simultánea—, **404** sin volver a cerrar, auditar ni avisar. No hay endpoint para restaurar | `eliminar_vacante` |

⚠️ **Una vacante archivada se lee entera y no se toca.** El detalle, el ranking, las fichas, el
historial y las descargas siguen contestando con los permisos de siempre; **toda entrada que
escriba responde 409** —`PUT /vacantes/{id}`, `/remuneracion`, `/publicacion`, `/cierre`, los
requisitos, los instrumentos técnicos, los pesos, el cierre de la prueba, las plantillas de
correo, la ficha y su cuestionario, `/calificar-tanda`, las barreras y las transiciones de sus
postulaciones—, sin efectos y sin dejar auditoría. La regla está escrita **una sola vez**
(`VacanteArchivada`) porque son quince entradas y la que se olvide no daría ninguna señal: el
panel es un cliente más del API, y el formulario que alguien dejó abierto antes de que otro la
archivara sigue sabiendo la URL del `PUT`.

⚠️ **Una vacante eliminada no existe para el API, y eso es un 404, no un 409.** Archivada está
ahí y no se mueve; eliminada no está (`V60`). Toda entrada del panel sobre ella —leer el detalle,
el ranking, las fichas, la exportación, o escribir: editar, sueldo, publicar, cerrar, archivar,
desarchivar, reconfigurar, mover sus postulaciones— responde **404** con el mismo texto que un id
que nunca existió. También sale de la selección de vacantes de una sesión de simulación, y el
pase automático, los vencimientos y la calificación no la procesan. El guardián es la búsqueda
por id, que ya filtra `eliminada_en IS NULL`, y `VacanteEliminada` para los caminos que llegan
desde una postulación.

⚠️ **Sobre las postulaciones de una eliminada, el panel lee pero no escribe** (22/09/2026). Toda
escritura que llega por una de ellas responde **404 «Vacante»**, aunque la pantalla se hubiera
abierto antes: el plazo de la prueba de esa persona, la nota de un criterio y la de la etapa,
calificar la prueba con IA, recalificar el Perfil Integral, cribar y reemplazar el currículum,
reabrir la evaluación, corregir el contacto, generar un enlace de acceso, poner y ponderar las
notas de la simulación, marcar eventos y asistencia, decidir sobre un ausente, registrar,
responder y generar las preguntas de la conversación final, la validación —habilitar, iniciar,
completar una métrica y cerrar— y la decisión —decidir, registrar una barrera y pedir evidencia—.
Se comprueba después de resolver la postulación y su alcance, con
`AlcanceSobreLaVacante.exigirQueSuVacanteSigaExistiendo`, y no dentro de `laPostulacionVisible`
a propósito: **las lecturas por id siguen en 200, por decisión del producto** —la ficha, el
historial, la prueba (respuestas, entregables y notas) y su plazo—, aunque ninguna lista del
panel lleve ya a ellas.

### Postulaciones

| Método y ruta | Qué hace | Permiso |
|---|---|---|
| PATCH `/postulaciones/{id}/contacto` | Corregir el correo o el teléfono de una ficha cuando el currículum los traía mal escritos (V27). Queda auditado | `corregir_contacto_candidato` |
| POST `/postulaciones/{id}/reapertura-evaluacion` | Volver a abrir la evaluación de quien no llegó a entregarla en plazo, para darle otra oportunidad | `mover_postulacion` |
| POST `/postulaciones/{id}/enlace-acceso` | Generar un enlace de acceso nuevo para ese candidato (entra sin contraseña; ver «Cómo entrar»). **Desde el 22/09/2026 respeta la empresa y el alcance** de `mover_postulacion`: antes buscaba la postulación solo por su id, de cualquier empresa. Fuera de la empresa o del alcance, y sobre una vacante eliminada, **404** | `mover_postulacion` |
| GET `/bandeja?espera_a=` | La bandeja: todo lo que espera a `CANDIDATO`, `SISTEMA`, `TALENTO` o `AREA` | `ver_candidatos` |
| GET `/vacantes/{id}/embudo` | Cuántas postulaciones hay en cada estado | `ver_embudo` |
| GET `/vacantes/{id}/ranking?etapa=` | La tanda ordenada de más apto a menos, con las ocho notas del currículum de cada uno. **Incluye a quien todavía no tiene nota**. Sin `etapa` ordena por la del Perfil Integral; con ella, por la nota de esa etapa. Cada fila trae además **dónde vive** (`ciudad`, ya escrito «Departamento — Provincia», y `ciudadCodigo`), **su pretensión salarial**, que pide **dos llaves** —`ver_pretension` y que esta vacante publique lo que paga—, y el **`ponderado`** de lo ya rendido (ver la nota de abajo). La respuesta trae además **`puedeMoverPostulacion`**, por lo mismo que la ficha: es el único modo que tiene el panel de saber si ofrecer el descarte en lote, porque no hay endpoint de «mis permisos». Con `etapa=PRUEBA_PUESTO` cada fila trae además **`estadoPrueba`** —`CALIFICADA`, `PENDIENTE_CALIFICACION`, `INCOMPLETA` o `NO_APLICA`—, que es lo que deja distinguir una prueba que nadie terminó (o que cerró el sistema al vencer el plazo) de una entregada a mano que espera calificación; en las otras etapas viaja vacío | `ver_embudo` |
| POST `/vacantes/{id}/ranking/excel` | La tanda seleccionada, en un `.xlsx` de **una sola hoja, llamada «Datos»**, que se descarga como adjunto. Se le pasan `etapa`, los `postulacionIds` **ya ordenados por quien llama** y `filtroDescrito`, la frase que se pintó encima de la tabla. Solo hay columnas para `PERFIL_INTEGRAL` y `PRUEBA_PUESTO`; otra etapa es un 400. **Cada criterio de la rúbrica es una columna** y las explicaciones viajan juntas en la última. ⚠️ La columna «CV» lleva un **enlace firmado que abre el currículum sin pedir sesión** durante unas horas; pide además `descargar_entregables` y, sin ese permiso, va solo el nombre del archivo | `ver_embudo` |
| GET `/postulaciones/{id}` · `/historial` | La ficha completa y el recorrido. La ficha trae además **`puedeMoverPostulacion`**: si quien pregunta tiene `mover_postulacion`. No es un dato del candidato sino una facultad de quien mira —igual que `puedeVerPretension` en el ranking— y existe porque el login solo devuelve token e id, así que el panel no tiene otra forma de saber si pintar el botón de descartar. ⚠️ Dice si el permiso **está**, no hasta dónde llega su alcance: quien pueda abrir fichas de todos y mover solo las suyas verá el botón y recibirá un **404** al pulsarlo. Desde el 14/09 trae también **lo que esa persona pidió ganar al postular aquí**, con las dos llaves de siempre, y cuando no hay nada **dice cuál de los tres motivos es** | `abrir_ficha_candidato` |
| POST `/postulaciones/{id}/transiciones` | Mover a cualquier estado. **El motivo es obligatorio, sin excepción**. Es también lo que usan **«Descartar»** de la ficha del panel y **«Descartar a N personas»** de la mesa de la tabla: mandan `NO_CONTINUA` y el servicio rellena solo `motivoCierre = DECISION_PERSONA` (para `CERRADA` sería `CIERRE_MANUAL`). ⚠️ Hacia un estado final **esto avisa al candidato por correo** — `NO_CONTINUA` dispara la plantilla `POSTULACION_NO_CONTINUA` en la misma transacción—, y el motivo escrito **no viaja en ese correo**: queda en el historial y la auditoría. **`avisar: false` calla ese correo y solo eso**: el estado cambia, la transición se guarda y la auditoría se escribe igual. Nulo o ausente = avisar, que es lo de siempre. ⚠️ Que no se avisó **queda escrito en los dos sitios donde alguien lo va a buscar**: el motivo guardado termina en « · sin avisar al candidato» —es lo único de la transición que pinta el historial de la ficha— y la auditoría lleva `avisoAlCandidato: NO_ENVIADO`. Sin eso, un descarte silencioso y uno normal se leen igual seis meses después, y si el candidato llama preguntando nadie sabría que nunca se le dijo | `mover_postulacion` |
| POST `/postulaciones/{id}/confirmacion-avance` | Confirmar que avanza: el sistema calcula el estado siguiente | `confirmar_avance` |
| GET `/postulaciones/{id}/perfil-integral` | El retrato de la IA: notas del currículum, hallazgos y avisos. Cada nota lleva su explicación, **su confianza —de 0 a 100, la misma escala del puntaje, no de 0 a 1—** y el **motivo del ajuste**, que solo tiene valor cuando esa nota la corrigió una persona | `ver_perfil_integral` |
| GET `/postulaciones/{id}/evaluacion` | El desglose del banco: cada respuesta abierta con su nota, la explicación y la evidencia que citó la IA, el promedio de lo cerrado y los semáforos de alineación. En un banco CAZATALENTOS cada respuesta trae además **qué pilar alimenta** (`pilar`, `pilarCodigo`) y **las cuatro señales** que el agente marcó (`senales`), y el desglose entero trae los `patrones` del cuestionario. **Sin evaluación asignada devuelve vacíos, no 404**. ⚠️ `senales` en nulo significa que **ese banco no las medía**, no que ninguna se cumpliera. ⚠️ `alineacion` sale vacía siempre: nadie escribe esa tabla todavía | `ver_respuestas_evaluacion` |
| POST `/vacantes/{id}/calificar-tanda` | Calificar de una vez a todos los de la tanda a los que les falta la nota. Se salta a quien ya la tiene y a quien está cerrado —retirado, no continúa o contratado—, y a quien ya salió del Perfil Integral, que podría estar rindiendo la prueba con el reloj corriendo. **No devuelve notas: encola**, y tarda alrededor de minuto y medio por cada diez currículums. Sustituye a `criba-rapida` y `criba-fina` desde la V53 | `ajustar_nota` |
| POST `/postulaciones/{id}/criba-cv` | Que la IA lea **solo el currículum** y arme el retrato con eso. Es lo que se pide con una tanda recién llegada | `ajustar_nota` |
| POST `/postulaciones/{id}/calificacion-perfil-integral` | Calificar con todo: currículum y evaluación. Exige evaluación entregada | `ajustar_nota` |
| POST `/postulaciones/{id}/cv` | Reemplazar el currículum desde el panel | `ajustar_nota` |
| GET `/archivos/{id}/descarga` | Bajar un archivo por su id —el CV, o un entregable de la prueba—. **Los bytes pasan por el backend**: es el camino que funciona en todos los entornos, y el único en local | `descargar_entregables` |
| GET `/archivos/{id}/enlace` | Un **enlace firmado** para bajarlo del bucket sin pasar por el backend, con su caducidad. ⚠️ **En local devuelve una url `memoria://` que ningún navegador abre**, y responde 200: quien lo llame tiene que mirar el esquema de la url, no esperar un error, y caer a `/descarga` | `descargar_entregables` |

> **Hay un ranking por etapa, y es el mismo endpoint.** `?etapa=PERFIL_INTEGRAL` —que equivale
> a no pasarlo—, `PRUEBA_PUESTO`, `SIMULACION`, `VALIDACION` o `DECISION` cambia **solo la nota con la que se ordena**: las ocho notas del
> currículum de cada fila siguen siendo las del Perfil Integral, porque son de esa etapa siempre.
> Sin el parámetro se comporta exactamente como antes: ordena por la nota de la preselección.
> Una etapa que no esté en el catálogo es un 400.
> Quien no tiene nota en la etapa pedida sale al final, sin heredar la de otra.
>
> Sigue sin haber un ranking **general** que mezcle las cuatro etapas en una sola nota. La
> Puntuación Global está calculada —sale en `/postulaciones/{id}/semaforo`—, pero nunca como lista
> ordenada. Está apuntado como decisión 6 en [Alcance del MVP](08-ALCANCE-DEL-MVP.md), con lo que
> habría que decidir antes de montarlo.
>
> **Lo que sí mezcla dos etapas es `ponderado`**, que cada fila trae desde el RF-155. Son el
> Perfil Integral y la prueba del puesto —lo único que ha ocurrido a esas alturas del embudo—
> reescalados sobre la suma de SUS pesos, no sobre 100: con el reparto de la v4 son 70 puntos
> que se estiran a 100, y en una vacante que siga en la v3 el divisor es otro. Los pesos salen
> de la versión de **la vacante**, nunca de un 70 escrito en el código.
>
> El objeto trae cuatro cifras: `sobre100` y el desglose `cv`, `perfil` y `prueba`, que son las
> tres que hay detrás de la cifra —enseñan de dónde sale, no permiten recalcularla: los pesos no
> viajan en la respuesta—. `sobre100` viene **vacío mientras falte cualquiera
> de las dos notas de etapa** —reescalar sobre una sola devuelve esa misma nota, con pinta de ser
> comparable con la de quien sí rindió las dos—, y también si la versión de pesos no trae alguna
> de las dos etapas.
>
> **No hay nota del banco de preguntas suelta, y no es un olvido.** Esa nota no se guarda en
> ninguna parte: lo guardado es su mezcla con el currículum, en `nota_etapa`. Despejarla restando
> da un número falso en dos casos reales —quien no tiene evaluación asignada, cuyo Perfil Integral
> **es** su nota de currículum, y las vacantes que califica `CalificacionCriterios`, que escriben
> ahí un índice de pilares que no es CV + banco—. Por eso el desglose enseña el Perfil Integral
> entero, que sí es exacto.
>
> ⚠️ **No es la Puntuación Global y no la sustituye.** No se persiste, no se compara con los
> umbrales del semáforo y no mueve a nadie de estado: es una vista de lo que ya está calculado.

> **`estadoPrueba`: por qué esa prueba no tiene nota** (18/09/2026). Viaja en cada fila **solo con
> `?etapa=PRUEBA_PUESTO`**; en las demás etapas llega nulo, porque ahí la columna Nota no habla de
> la prueba. Cuatro valores, con esta prioridad: `CALIFICADA` si hay nota de la etapa —**un cero es
> una nota**, así que lo que decide es que exista, no que sea mayor que cero—; si no la hay,
> `INCOMPLETA` cuando el intento no tiene `entregadoEn` o lo cerró el sistema al vencer el plazo
> (`esEntregaAutomatica`), y `PENDIENTE_CALIFICACION` cuando la entregó una persona y su rúbrica
> todavía no ha dejado nota. `NO_APLICA` es que no hay intento del que hablar.
>
> **Se calcula al leer y no se guarda.** Ni columna, ni migración, ni estado nuevo: sale de
> `NotaEtapa` y de `IntentoPrueba` tal como estén en ese momento, sobre la **misma consulta de
> intentos** que ya arma las columnas de la rúbrica —ninguna consulta de más—. Consultar o
> descargar el ranking no escribe nada, y volver a abrirlo después de una entrega o de una
> calificación da el valor nuevo. La regla vive en un solo sitio, el enum `EstadoPruebaDelPuesto`.
>
> ⚠️ **No son los estados de la postulación ni se pintan tal cual**: son el insumo con el que el
> panel escribe «Prueba incompleta» o «Pendiente de calificación». Con `NO_APLICA` la celda
> conserva el texto de siempre, y ahí caen también **las vacantes que rinden el cuestionario
> técnico**, que no usan `intento_prueba`. Ver [Defectos conocidos](DEFECTOS-CONOCIDOS.md).
>
> ⚠️ **Si la consulta de los intentos falla, el ranking no falla.** Las filas, el orden y el conteo
> son los mismos; esas filas salen con `NO_APLICA` y el caso queda escrito en el registro.

> **Quién ordena y quién filtra el Excel: el cliente.** El volcado no filtra ni reordena nada. Le
> llegan los `postulacionIds` en el orden en que se quieren las filas y los escribe en ese orden,
> porque la tanda del ranking viene ordenada por grupo de prioridad y nota —otro orden, también
> válido, y no el que la persona estaba mirando—. Se piden por POST y no por GET porque ochenta
> ids y la frase del filtro no caben en una URL. **No escribe nada**: lo único que crea es el
> archivo.
>
> Los ids que no son de esa vacante **se descartan y se dicen al pie de la hoja**, con su número
> y su lista; si ninguno de los pedidos es de la vacante, es un 400. La hoja **«Datos»** —la
> única— lleva **una fila por candidato**, con el puesto que ocupa en el ranking y no la posición
> en el archivo; no hay hoja de respuestas. El archivo baja como adjunto y se llama
> `ranking-{etapa}-vacante-{id}-{fecha}.xlsx`, con la fecha dentro porque estas hojas se guardan.
>
> **Las columnas, en el orden de la plantilla que pidió el cliente** (16/09/2026). En la pestaña
> de la prueba del puesto: `#`, Candidato, Correo, CV, Teléfono, `Nota Examen Técnico /100`,
> `Nota Perfil Integral /100`, **una columna por cada criterio de la rúbrica** —rotulada
> `Nombre (pts /N)`, con el techo de ese criterio—, `Nota Combinada /100`, `Justificación
> resumida` y `Justificación detallada`. En la pestaña del Perfil Integral son **las mismas menos
> la nota técnica y la combinada**: a esas alturas del embudo la prueba todavía no existe para
> nadie, y esas dos columnas saldrían huecas para casi toda la tanda; ahí las columnas de criterio
> son las del currículum, que son las notas que esa etapa produce. Las columnas de criterio se
> arman con **la tanda entera y no con el recorte pedido**, igual que las de la tabla: así el
> mismo ranking descargado con dos ordenaciones distintas sale con las mismas columnas y en el
> mismo sitio.
>
> ⚠️ **Dos columnas nunca llevan el mismo rótulo encima**, y hace falta decirlo porque es fácil
> que coincidan: una rúbrica puede tener «Comunicación» oral y «Comunicación» escrita, y una tanda
> que mezcle dos versiones de la misma plantilla puede traer el mismo criterio con distinto techo.
> Cuando dos columnas coincidirían en su rótulo, **todas las que coincidan llevan su código entre
> corchetes** —«Comunicación [COM_ORAL] (pts /20)»—, y si ni con el código se distinguen, un
> ordinal. Lo que decide si dos notas van a la misma columna son las tres cosas juntas: el nombre,
> el código y el techo. ⚠️ Las tres hacen falta: **el código de un criterio solo es único dentro de
> una versión de plantilla**, así que el mismo «C2» puede ser dos criterios distintos en dos
> versiones, y sin el nombre una nota acabaría leyéndose bajo el criterio equivocado.
>
> **De dónde sale cada justificación.** «Justificación resumida» es el resumen que la IA escribió
> del candidato. «Justificación detallada» son las explicaciones de cada criterio unidas en una
> sola celda —una detrás de otra, cada una con su puntaje y su techo—, y cuando una persona
> corrigió una nota, **el motivo de ese ajuste va pegado a su criterio** y no en una columna
> aparte, porque solo significa algo junto a la nota que corrigió. Eso es lo que hacía falta de
> la vieja hoja «Detalle», que ponía una línea por criterio y por candidato: con cada criterio
> convertido en columna, esa hoja se quedaba sin nada propio que contar.
>
> ⚠️ **La columna «CV» lleva un enlace firmado, y ese enlace abre el currículum sin pedir sesión
> ni permiso.** Vale durante el plazo que fije `app.archivos.supabase.horas-enlace-volcado` —ocho
> horas de fábrica— y **cualquiera que reciba el archivo dentro de ese plazo puede abrir esos
> currículums**, aunque no tenga cuenta. La propia hoja lo avisa en su pie, con el plazo que de
> verdad queda. Escribirlo pide `descargar_entregables`, el mismo permiso que la descarga de
> siempre; sin él la columna va con el nombre del archivo y nada más, y el pie lo dice. El texto
> de la celda es siempre el nombre del archivo y nunca la URL: doscientos caracteres de firma no
> se leen, y el nombre sigue sirviendo para dar con el currículum cuando el enlace ya caducó. Un
> currículum que no se pudo firmar —porque el archivo ya no está en el almacén, o porque la firma
> falló— deja su fila con el nombre y **se cuenta al pie**: una firma que falla no puede dejar sin
> archivo a la tanda entera. Los detalles del plazo, y por qué no son los cinco minutos del panel,
> están en [Los currículums dejan de vivir en el backend](ARCHIVOS-EN-BUCKET.md).
>
> **Lo que la hoja ya no trae, y la tabla sí.** Pretensión, Veredicto, Estado, Ciudad y las cifras
> del retrato de la IA (adecuación, potencial, riesgos, alertas, fortalezas) **no van al archivo**:
> la plantilla que mandó el cliente es más corta y se siguió. Siguen en la tabla del panel, que es
> donde se trabaja. Por eso la frase del pie ya no explica por qué salió vacía una columna de
> pretensión o de ciudad —explicar una columna que no está desorienta más que callar—, mientras
> que el ranking sí sigue devolviendo `puedeVerPretension` en su cabecera **para la tabla**: sin
> ese booleano, la pantalla tendría que nombrar las posibilidades sin afirmar ninguna. ⚠️ Ese
> booleano es el permiso a secas, no la conjunción con lo que publica la vacante: son motivos
> distintos para una casilla vacía. Sin `ver_pretension`, o con una vacante que esconde su sueldo,
> el dato ni se consulta.
>
> ⚠️ **La hoja no distingue por qué falta una nota, y la tabla sí.** `filtroDescrito` se escribe
> tal cual en el pie, así que desde el 18/09/2026 dice «Pendiente» donde decía «Por revisar» —lo
> manda el panel, aquí no se redacta—, pero la nota técnica que falta sigue diciendo **«rúbrica
> incompleta»** venga de una prueba que nadie terminó o de una entregada sin calificar:
> `estadoPrueba` no llega al volcado. Quien necesite saber si esa prueba se entregó tiene que
> mirar la pestaña.
>
> **Por qué los rótulos de las notas no son los de la plantilla del cliente.** Dos diferencias, y
> las dos deliberadas. La plantilla llamaba «Nota CV /100» a la nota del Perfil Integral: eso es
> cierto en Administrador y Asistente Administrativo, donde el perfil lo llena el currículum
> porque tienen el banco apagado, pero **en una vacante con banco esa misma cifra es la de la
> prueba RENASER**, así que rotularla «CV» diría de dónde sale un número que sale de otro sitio;
> la hoja la llama «Nota Perfil Integral /100» y el pie lo explica en una línea. Y la plantilla
> traía «Nota Combinada /40» en la cabecera con `=0.55*F+0.45*G` en las celdas, que da una cifra
> **sobre 100** —y una hoja de «Metodología» que hablaba de un 30 % y un 12 %, que no son esos
> pesos—: de las tres versiones se siguió la fórmula, que es la única que coincide con los pesos
> de etapa de esas vacantes (45 el Perfil Integral y 55 la prueba del puesto, desde las
> migraciones `V49` y `V50`).

> **El desglose enseña de dónde sale cada 0–4, y no lo recalcula.** Desde el 02/09
> `/postulaciones/{id}/evaluacion` devuelve, de lo que ya estaba guardado desde que se calificó,
> tres cosas más. Ninguna es otra pasada de IA: son consultas sobre las columnas que la `V41`
> creó justo para esto, y **ninguna nota se mueve**.
>
> **El pilar de cada respuesta abierta** (`pilar`, el nombre para leer —«Iniciativa (pilar)»—, y
> `pilarCodigo`, `PIL_INICIATIVA`). Sin él las abiertas son una lista plana y no se puede
> contestar «¿cuáles de estas sostienen Iniciativa?». Sale de `pregunta_dimension` **filtrando
> por el prefijo `PIL_`**, que es el mismo filtro con el que `CalificacionCriterios` agrupa al
> ponderar, y tiene que ser el mismo: una pregunta puede colgar además de alguna de las 22
> dimensiones del catálogo viejo, y agrupar por una de esas diría que la respuesta sostiene algo
> que **no mueve ninguna nota**. Vienen vacíos si la pregunta no cuelga de ningún pilar. Si una
> pregunta tuviera dos pilares se queda con uno, a propósito —agrupar es repartir cada respuesta
> en un sitio—; el banco de hoy no produce ese caso.
>
> **Las cuatro señales** (`senales`: `episodio`, `autoria`, `dato`, `incomodidad`, más
> `cumpleSenalCero`). El agente declara qué vio; **el número lo pone el código**, no la
> aritmética del modelo. ⚠️ Y no es la suma de las cuatro: `episodio` es una puerta y hay un
> tope, como explica el aviso de más abajo. Enseñar el número sin ellas deja «3 de 4»
> sin decir cuál faltó, que es justo lo que hay que poder discutir con la persona en la
> conversación. Van en un objeto propio y no en cuatro campos sueltos para que **el nulo sea uno
> solo**: o el banco las medía —y están las cuatro— o no las medía y no hay ninguna; cuatro
> booleanos sueltos admitirían tres puestas y una vacía, que no significa nada.
>
> ⚠️ **`senales` en nulo no es «no cumplió ninguna»: es «este banco no las medía».** Solo el
> banco CAZATALENTOS puntúa así; las notas de los bancos anteriores las tienen vacías y no se
> inventan. Quien pinte cuatro casillas desmarcadas ahí convierte cada evaluación antigua en un
> cero de cuatro que nadie le puso.
>
> ⚠️ **Un `0` con señales marcadas tampoco es un fallo.** El puntaje no es la suma a secas: es 0
> si `cumpleSenalCero`, y 0 también si falta `episodio` aunque las otras tres estén, porque sin un
> episodio concreto no hay nada que puntuar. Y una pregunta que declare tope lo recorta: la regla
> dura de R11 —«sin ninguna cifra, el máximo de esta pregunta es 2»— sale de la propia pregunta,
> no de un `if` por código. Por eso la señal de cero viaja junto a las cuatro: **es lo que explica
> un cero que de otro modo parecería un error de cálculo.**
>
> **Los patrones del cuestionario completo** (`patrones`, con `codigo`, `titulo`, `descripcion`,
> `deCuantas` y `total`). Son dos, y solo se ven mirando el cuestionario entero, no una respuesta:
> `SIN_INCOMODIDAD` —ninguna respuesta marcó la cuarta señal— y `SOLO_NOSOTROS` —**más de la
> mitad** no distinguen qué hizo él de lo que hizo su equipo—. El corte es el del documento de la
> clienta, y no «la mitad o más»: con el corte flojo, un cuestionario partido por la mitad levanta
> la bandera, y una bandera que salta en la mitad de los candidatos deja de señalar nada. La frase de cada uno **dice de cuántas sale**,
> porque con pocas respuestas el patrón salta fácil y quien lo lee tiene que poder juzgarlo.
> **No descartan a nadie**: son dos preguntas para la conversación final, igual que las alertas.
>
> Solo se cuentan las respuestas que traen señales. Mezclar las que no las tienen daría «nunca se
> incomodó» en cualquier evaluación de un banco anterior, que no midió nada de esto.
>
> ⚠️ **`patrones` vacía se lee de dos maneras y está bien.** Habiendo respuestas abiertas, puede
> ser que el banco no midiera las señales, o que las midiera y no saltara ninguno. La lista sola no
> lo distingue, y no hace falta que lo haga: **el bloque `senales` de cada respuesta ya dice cuál
> de los dos casos es**. Lo que no se puede es leer la lista vacía como «no se encontró nada» sin
> mirar antes las respuestas — y si `abiertas` también viene vacía, no hay nada que leer: o no hay
> evaluación asignada, o ninguna de sus preguntas era puntuable.

### La prueba del puesto (hito 3)

| Método y ruta | Qué hace | Permiso |
|---|---|---|
| POST `/plantillas-prueba` · `/{id}/versiones` | Crear la plantilla y una versión en borrador | `editar_plantillas_prueba` |
| GET `/plantillas-prueba/{id}/versiones` | Las versiones de esa plantilla, de la más nueva a la más vieja. Vienen todas, borradores incluidos: el estado dice cuál se puede usar | `elegir_plantilla_prueba` |
| PUT `/plantillas-prueba/versiones/{id}` · POST `…/{id}/consigna` · PUT y DELETE sobre `/entregables/{id}`, `/rubrica/{id}`, `/variantes/{id}` · DELETE `…/preguntas/{id}` · PUT `…/orden` | **Componer un borrador**: corregir, quitar y reordenar cada pieza, y subir el ENUNCIADO como PDF o Word. **Solo en `BORRADOR`**; sobre una versión publicada responden 409 y la salida a un error sigue siendo una versión nueva | `editar_plantillas_prueba` |
| POST `/plantillas-prueba/versiones/{id}/publicacion` | Publicar: exige 8-10 preguntas universales, 3-5 específicas, y la rúbrica sumando 100. **Una versión sin entregables es un cuestionario**: la cuota no rige y basta con una pregunta. La duración, si es cronometrada, **al menos 5 minutos y sin techo** (el rango 60-120 se retiró el 31/08/2026) | `editar_plantillas_prueba` |
| POST `/postulaciones/{id}/prueba/plazo` | Fijarle a ESE candidato su fecha de cierre, normalmente para darle más horas. **Queda marcada como suya**: mover después la fecha de la vacante no se la toca. Antes de empezar, la fecha puesta manda sobre el cálculo por días | `mover_postulacion` |
| GET `/postulaciones/{id}/prueba/plazo` | **Qué plazo rige hoy para esa persona**, para poder enseñarlo antes de tocarlo: `venceEn`, de dónde sale (`origen`: `VACANTE`, `RELOJ` o `PROPIO`), `iniciadoEn`, `entregadoEn` y el `instrumento` de la vacante. **No contesta 404 cuando no hay prueba**: quien todavía no llegó a la etapa y la vacante que rinde el cuestionario técnico —que no usa `intento_prueba`— salen con `existeIntento: false`, y el instrumento distingue los dos casos. `venceEn` vacío es «se le calculará al abrirla», no un error. Pide el permiso de **leer** la ficha: quien no puede mover el plazo igual lo ve | `abrir_ficha_candidato` |
| GET `/postulaciones/{id}/prueba/respuestas` | Lo que contestó, pregunta a pregunta. Las preguntas son **las de la versión que él vio**, en su orden, no las del catálogo de hoy: una versión publicada después puede llevar otras | `abrir_ficha_candidato` |
| GET `/postulaciones/{id}/prueba/entregables` | **Lo que subió**: los archivos y los enlaces que la prueba pedía, entregados o no —que falte un obligatorio es lo que hay que ver antes de poner una nota—. De cada uno, **la última versión**: pudo entregarlo tres veces. ⚠️ El `enlace` y el `archivoId` **viajan solo con `descargar_entregables`**; sin él se dice qué entregó y cuándo, y `porQueNoSeVe` explica el hueco. Con cuestionario técnico devuelve **lista vacía, no 404** | `abrir_ficha_candidato` |
| GET `/postulaciones/{id}/prueba/notas` | La rúbrica entera, **en su orden**, con lo que lleva puesto cada criterio: puntaje, explicación y **de quién viene la nota**, si de la IA o de una persona. Lo que aún no tiene nota sale en nulo. Desde el 03/09/2026 **leer pide el permiso de abrir la ficha, no el de corregir**: Responsable de Área veía la nota en el embudo y recibía 403 al abrir su desglose | `abrir_ficha_candidato` |
| POST `/postulaciones/{id}/prueba/criterios/{criterioId}/nota` | Poner la nota de un criterio, con explicación obligatoria | `ajustar_nota` |
| POST `/postulaciones/{id}/prueba/calificacion-ia` | Pedirle al agente `PRUEBA_PUESTO` los criterios que la rúbrica le reserva. Tarda decenas de segundos y **no pisa ningún ajuste hecho a mano**. Al acabar, si la rúbrica quedó entera **deja también la nota de la etapa** | `ajustar_nota` |
| POST `/postulaciones/{id}/prueba/calificacion` | Ponderar las notas ya puestas. Exige que estén todos los criterios. **Escribe**: deja la nota guardada, no es una consulta. Desde el 28/08 **ya no es el único camino**: si el agente deja la rúbrica entera, la nota sale sola y esta llamada solo la reescribe con lo mismo. Sigue haciendo falta cuando los últimos criterios los pone una persona | `ajustar_nota` |

**El portal del candidato es `/api/v1/portal/prueba/{codigo}`**: ver, iniciar (arranca el
reloj), responder, subir entregables y entregar. Mismas reglas que la evaluación: nada de
lo interno viaja, y una prueba ajena responde 404. **También al guardar**: desde el 16/09/2026
mandar una respuesta vacía la borra en vez de rebotar con un 400 —ver
[Guardar lo que el candidato escribe](#guardar-lo-que-el-candidato-escribe-16092026)—, que aquí
importaba el doble porque el error salía en mitad de una prueba cronometrada.

### Simulación de trabajo

| Método y ruta | Qué hace | Permiso |
|---|---|---|
| POST `/sesiones-simulacion` | Crear una sesión con fecha y cupo. Publicarla mueve a quien estaba esperando | `crear_sesiones_simulacion` |
| GET `/sesiones-simulacion` · `/{id}` | Las sesiones. **También entra quien solo puede ver inscritos**, recortado a las que tocan una vacante suya | `crear_sesiones_simulacion` **o** `ver_inscritos_simulacion` |
| GET `/sesiones-simulacion/{id}/inscritos` | **Quién eligió esta fecha**: nombre, vacante y la `inscripcionId` que piden las marcas y la asistencia. Recortado por el alcance del rol | `ver_inscritos_simulacion` |
| POST `/sesiones-simulacion/{id}/cupo` · `/cancelacion` | Ampliar o cancelar. Al cancelar se avisa a los inscritos | `crear_sesiones_simulacion` |
| POST `/sesiones-simulacion/{id}/responsables` | Quién conduce la sesión | `crear_sesiones_simulacion` |
| GET/POST `/sesiones-simulacion/{id}/informacion-critica` | Qué debería preguntar un candidato fuerte | `definir_informacion_critica` |
| GET/POST `/inscripciones/{id}/marcas` | Los diez eventos observables, marcados en vivo. Una inscripción fuera de alcance responde 404 | `marcar_eventos_simulacion` |
| POST `/inscripciones/{id}/asistencia` | Si asistió. Si no, vuelve a la bandeja del equipo | `marcar_asistencia` |
| POST `/postulaciones/{id}/ausencia-simulacion` | Qué hacer con quien faltó: otra fecha o cerrar | `decidir_sobre_ausente` |
| POST `/postulaciones/{id}/simulacion/...` | Poner notas y ponderarlas, como en la prueba | `calificar_simulacion` |
| GET/POST `/postulaciones/{id}/conversacion-final` | Las 3-5 preguntas y lo que se respondió | `hacer_conversacion_final` |
| POST `/postulaciones/{id}/conversacion-final/generar` | Pedirle al agente `SIMULACION` que escriba las preguntas, sacadas de las contradicciones entre lo que dijo y lo que se le vio hacer. **No puntúa nada**, y una pregunta ya contestada no se rehace | `hacer_conversacion_final` |
| POST `/conversacion-final/{preguntaId}/respuesta` | Anotar lo que el candidato respondió a una pregunta concreta | `hacer_conversacion_final` |

**El portal del candidato es `/portal/simulacion/{codigo}`**: ver las fechas de su vacante que
tengan cupo, elegir una, y consultar la que eligió.

⚠️ **Los dos GET de sesiones admiten dos permisos, y no es una excepción caprichosa.** El
responsable del área no crea sesiones, pero marca la asistencia de sus candidatos y necesita
las `inscripcionId` que ese endpoint le da: con un solo permiso podía leer los inscritos de una
sesión y no había endpoint que le dijera qué sesiones existen, así que tenía la lista de un
`{id}` que no había forma de averiguar. Con el alcance acotado ve solo las sesiones que tocan
una vacante suya, y una que no responde **404**, no 403.

**El conteo `inscritos` se recorta con el mismo criterio en los dos GET.** Una sesión sirve a
varias vacantes a la vez, así que para el responsable del área la cifra que ve es la de sus
candidatos, no la de la sala entera —y es la misma en la lista, en el detalle y en el número de
filas de `/inscritos`—. Decir «6» y luego enseñar dos no se lee como un permiso: se lee como que
faltan cuatro. **El conteo** lo recorta la base y no un filtro en memoria —contar es un
`COUNT` con su `WHERE`, y traerse las filas para descartarlas después sería traer datos que
quien mira no puede ver—; con `PROPIO` ni siquiera se pregunta, porque la respuesta es cero sin
mirar. Y los dos GET deciden con la misma función, `contarInscritos`, que es donde están los
cuatro casos del alcance y el único sitio donde están — no dos copias que se separan.

`/inscritos` sí recorta en memoria, y no es un descuido: para saber si una inscripción es «de
sus vacantes» hay que pasar por su postulación, así que las postulaciones se traen igual. Lo
que se pide **después** de recortar son los nombres, que es el dato personal: de los descartados
no se pregunta ni cómo se llaman.

**Las tres cifras cuadran con cualquier reparto, no solo con el que siembra la V40.** Son dos
preguntas distintas y cada una la contesta su permiso: *qué sesiones veo* lo decide
`crear_sesiones_simulacion` si quien llama lo tiene y si no `ver_inscritos_simulacion`; *a
cuántos inscritos alcanzo* lo decide siempre `ver_inscritos_simulacion`, porque contar inscritos
es verlos. Así, un rol al que se le den los dos permisos **con alcances distintos** —un solo PUT
desde `administrar_permisos`— abre todas las sesiones y sigue contando solo a los suyos. Quien
no tenga el segundo permiso ve el conteo entero: no puede abrir la lista, así que no hay dos
cifras que puedan contradecirse, y un número de inscritos es aforo, no identidades.

**`PROPIO` no alcanza a ningún inscrito**: ninguna fila en `/inscritos`, y lo mismo en las
marcas y la asistencia. Y cuando además es el alcance con el que se miran las sesiones —el de
`crear_sesiones_simulacion`, o el de `ver_inscritos_simulacion` si no se tiene el primero—, la
lista sale vacía y el detalle responde **404**. En el panel nada de esto es de
quien mira —son candidatos—, y `/panel/**` exige un token `TIPO_EQUIPO`, así que quien entra
por ahí no tiene postulación propia que enseñarse a sí mismo.

Y con `PROPIO` el conteo es **cero**, que es lo que `/inscritos` devuelve con ese alcance. Nadie
lo tiene hoy así, pero es un valor válido y se pone con ese mismo PUT: a quien además tuviera
`crear_sesiones_simulacion` en `TODO`, la sesión le diría «6» y la lista le devolvería cero
filas si el conteo no distinguiera los tres casos.

⚠️ **Las marcas y la asistencia también miran el alcance, y la inscripción también mira la
organización.** Los tres endpoints de `/inscripciones/{id}` pedían su permiso y tiraban el
alcance, así que un `SUS_VACANTES` valía tanto como un `TODO`; y la inscripción se buscaba por
id a secas, sin comprobar de qué organización era. Hoy el único que se escapaba de verdad es
`GET /marcas` —`marcar_eventos_simulacion` está sembrado acotado para el responsable del área—;
en `/asistencia` el permiso arranca en `TODO` a propósito, porque quien marca puede estar en la
sala sin dirigir esa vacante, así que ahí el recorte no cambia nada **hoy**: cambia el día que
alguien edite esa fila desde el panel. Una inscripción de otra organización responde lo mismo
que una que no existe.

⚠️ **Tres reglas mueven al candidato solo**, y son el único punto del sistema donde el estado de
una postulación depende de otra tabla: publicar una sesión o ampliar su cupo mueve a quien
esperaba; llenar la última devuelve a quien no se inscribió; cancelar devuelve a los inscritos.
**Faltar a la sesión no reinscribe solo** — eso lo decide una persona.

**Solo se registra lo que se hizo, nunca lo que se supone que pensó.** El evento «detectó el
bloqueo» no existe: quedan «apareció el cambio» y «lo abrió», que son dos actos observables.

### Validación práctica

| Método y ruta | Qué hace | Permiso |
|---|---|---|
| GET `/postulaciones/{id}/validacion` | El periodo, su modalidad y sus fechas | `completar_metricas_validacion` |
| POST `/postulaciones/{id}/validacion/habilitacion` | Modalidad y días. **El trabajo real exige la figura contractual** | `habilitar_validacion` |
| POST `/postulaciones/{id}/validacion/inicio` | Arrancar: fija inicio y fin | `iniciar_validacion` |
| GET/POST `/postulaciones/{id}/validacion/metricas` | Las nueve métricas, con de dónde salió cada valor | `completar_metricas_validacion` |
| POST `/postulaciones/{id}/validacion/cierre` | Ponderar y pasar a la decisión | `cerrar_validacion` |

⚠️ **No se pone a nadie a trabajar de verdad sin figura contractual registrada.** La otra
modalidad —simulación extendida, sin trabajo productivo— no la necesita y se puede usar desde
el primer día.

**Quién facilita y quién completa métricas es configurable** desde
`PUT /panel/parametros/{codigo}`: `roles_facilitador_simulacion` y
`roles_completan_metricas_validacion`. No hace falta un rol nuevo ni tocar código.

### La decisión final (hito 3)

| Método y ruta | Qué hace | Permiso |
|---|---|---|
| GET `/postulaciones/{id}/semaforo` | La Puntuación Global y una propuesta de semáforo. Si la versión de pesos de la vacante no tiene ningún peso de etapa responde «no se puede calcular todavía», nunca un cero en rojo (04/09) | `ver_semaforo_decision` |
| POST `/postulaciones/{id}/decision` | Decidir. El motivo es siempre obligatorio (RF-119) | `decidir_contratacion` la primera vez, `cambiar_decision` para corregir |
| POST `/postulaciones/{id}/evidencia-adicional` | Pedir evidencia adicional cuando sale ámbar. Tope configurable | `pedir_evidencia_adicional` |
| POST `/postulaciones/{id}/barreras-detectadas` | Marcar qué barrera crítica de la vacante disparó este candidato, con su evidencia, antes de decidir. Es distinto de `/vacantes/{id}/barreras-criticas`, que las DEFINE | `decidir_contratacion` |

⚠️ **La decisión de contratar no es de Talento.** Es del responsable del área o de
Dirección (RF-119) — la primera vez en el sistema que Talento no tiene el permiso de
escritura más importante de un flujo.

### El banco de preguntas

El repositorio del que sale el examen del Perfil Integral. Se administra entero desde aquí:
el banco v4 que venga no necesitará una migración. El ciclo es
**BORRADOR → PUBLICADA → ARCHIVADA**, y tres reglas lo sostienen:

- **Solo un borrador se edita.** Una versión publicada no admite ni una opción más: su clave
  no se altera por debajo de un examen en curso.
- **Publicar valida y hace el relevo.** Valida la coherencia de cada formato (un EF-4 sin
  valores, un SEC con huecos o un CD sin denominador se rechazan con el código del ítem) y
  archiva a la versión que reemplaza. Quien tenía una evaluación sin empezar pasa al banco
  nuevo sin notarlo; quien ya empezó conserva el suyo (RF-138).
- **La clave se ve aquí y solo aquí.** El panel devuelve puntajes, valores ocultos y
  distractores —quien edita el banco necesita ver lo que escribió—; al portal del candidato
  no viaja ninguno, y `logicaInterna` entra pero no sale ni por aquí (RF-53).

| Método y ruta | Qué hace | Permiso |
|---|---|---|
| GET `/banco-preguntas/versiones` | Todas las visibles, con su estado; las archivadas también: son historia | `ver_banco_preguntas` |
| POST `/banco-preguntas/versiones` | Crear una versión, en borrador | `editar_banco_preguntas` |
| POST `/banco-preguntas/versiones/{id}/publicacion` | Validar, publicar y archivar a la saliente | `publicar_version_banco` |
| POST `/banco-preguntas/versiones/{id}/archivado` | Retirarla sin reemplazo. Se bloquea si dejaría candidatos esperando sin banco | `publicar_version_banco` |
| GET/POST `/banco-preguntas/versiones/{id}/preguntas` | Las preguntas: los 15 formatos (6 del v0.1 + 8 del v3 + `ABIERTA` del banco CAZATALENTOS, que lleva su guía del evaluador: C3 esperado, C4 esperado y señal de 0) | `ver` / `editar_banco_preguntas` |
| GET/POST `/banco-preguntas/preguntas/{id}/opciones` | Las opciones con su clave: puntaje, valor oculto (EF-4), distractor (INV/DE), orden correcto (SEC) | `ver` / `editar_banco_preguntas` |
| GET/POST `/banco-preguntas/preguntas/{id}/rangos` | Los tramos de puntaje de los ítems V | `ver` / `editar_banco_preguntas` |
| GET/POST `/banco-preguntas/preguntas/{id}/campos-caso` | Los campos de los casos descompuestos (CD) | `ver` / `editar_banco_preguntas` |
| GET/POST `/banco-preguntas/versiones/{id}/pares-consistencia` | Emparejar dos preguntas de la versión para vigilar contradicciones | `ver` / `editar_banco_preguntas` |
| POST `/banco-preguntas/importaciones` | **Subir la plantilla Excel** (multipart: `archivo`, `nivelPuestoCodigo`, `etiqueta`). Crea una versión en borrador con todo el archivo; si algo no cuadra, 400 con la lista `{hoja, fila, mensaje}` y no se importa nada. Traga dos formatos y elige solo: la plantilla v3, o el libro CAZATALENTOS (se delata por su hoja «Prueba RENASER» y entra con método `CRITERIOS`) | `editar_banco_preguntas` |
| GET `/banco-preguntas/dimensiones` | El catálogo de dimensiones: lo que vale escribir en la columna «Qué mide» | `ver_banco_preguntas` |
| PUT/DELETE `/banco-preguntas/preguntas/{id}` | Reemplazar o quitar una pregunta **de un borrador**; borrarla se lleva sus opciones, campos, rangos y pares | `editar_banco_preguntas` |
| PUT/DELETE `/banco-preguntas/opciones/{id}`, `/rangos/{id}`, `/campos-caso/{id}`, `/pares-consistencia/{id}` | Lo mismo para cada pieza de un borrador | `editar_banco_preguntas` |
| DELETE `/banco-preguntas/versiones/{id}` | Descartar un borrador entero: se borra de verdad, con sus preguntas. Solo un borrador, que nunca se le asignó a nadie | `editar_banco_preguntas` |
| PATCH `/banco-preguntas/preguntas/{id}/textos` | **Corregir una errata de lo ya publicado**: enunciado, situación o nota interna. La clave, el peso y la estructura no se tocan por aquí (RF-138) | `publicar_version_banco` |
| PATCH `/banco-preguntas/opciones/{id}/textos`, `/campos-caso/{id}/textos`, `/rangos/{id}/textos`, `/pares-consistencia/{id}/textos` | Igual para el texto de cada pieza publicada; su clave nunca viaja en el cuerpo | `publicar_version_banco` |
| PATCH `/banco-preguntas/versiones/{id}/etiqueta` | Renombrar una versión publicada | `publicar_version_banco` |

### Las plantillas de evaluación (`/api/v1/panel/plantillas-evaluacion`)

La plantilla es lo que una vacante elige en `POST /vacantes/{id}/plantilla-evaluacion`: qué
versión del banco se responde y cuántos ítems de cada dimensión entran (las cuotas). Desde la
V20 el banco v3 se aplica **entero por nivel**, así que las cuotas solo tienen sentido para bancos
que se muestrean.

| Método y ruta | Qué hace | Permiso |
|---|---|---|
| GET `/plantillas-evaluacion` | Las plantillas de la organización (o las de la plataforma, si la bandera de personalización está apagada) | `elegir_plantilla_evaluacion` |
| POST `/plantillas-evaluacion` | Crear una en borrador sobre una versión del banco | `editar_plantillas_evaluacion` |
| GET/POST `/plantillas-evaluacion/{id}/cuotas` | Cuántos ítems de cada dimensión entran | `elegir_plantilla_evaluacion` / `editar_plantillas_evaluacion` |
| POST `/plantillas-evaluacion/{id}/publicacion` | Publicar: desde ahí la eligen las vacantes y ya no se edita | `editar_plantillas_evaluacion` |

### Los pesos del embudo (`/api/v1/panel/pesos`)

Una versión de pesos dice cuánto vale cada etapa (40/30/15/15 en la sembrada), cada dimensión,
cada criterio y cada componente. **Se publica y ya no se edita**: cada nota queda atada a la
versión con la que se calculó y nada se recalcula hacia atrás. Una vacante elige la suya con
`POST /vacantes/{id}/version-pesos`; sin elegir, rige la publicada más reciente de la
organización (o la de la plataforma).

| Método y ruta | Qué hace | Permiso |
|---|---|---|
| GET/POST `/pesos/versiones` | Las versiones y crear una en borrador | `publicar_version_pesos` |
| GET/POST `/pesos/versiones/{id}/etapas` | El reparto entre las cuatro etapas; tiene que sumar 100 | `publicar_version_pesos` |
| GET/POST `/pesos/versiones/{id}/dimensiones` · `/criterios` · `/componentes` | El reparto dentro de cada etapa: dimensiones del banco, criterios del currículum, y componentes del Perfil Integral | `publicar_version_pesos` |
| POST `/pesos/versiones/{id}/publicacion` | Publicar. La aduana exige que todos los repartos estén completos y cuadren | `publicar_version_pesos` |

El guion `scripts/completar-y-publicar-pesos-cazatalentos.py` recorre exactamente estas llamadas.

### Administración

| Método y ruta | Qué hace | Permiso |
|---|---|---|
| GET `/catalogos` | Los catálogos que llenan los desplegables del panel (puestos, niveles, familias, etapas, estados) | cualquier cuenta del equipo |
| GET `/areas` | Las áreas **activas** de la organización: es la lista que llena el desplegable de la solicitud, y hace falta una para registrarla. **No cambia ni debe cambiar**: una área retirada ahí sería una solicitud nueva colgada de algo que la empresa dio por cerrado | `ver_solicitudes` |
| GET `/areas/todas` | Las áreas con las retiradas. Es una ruta aparte y no un parámetro de la anterior, para que ese contrato no se afloje por descuido | `crear_usuarios_y_asignar_roles` |
| POST `/areas` · PUT `/areas/{id}` | Crear y renombrar. El nombre repetido se rechaza antes de llegar al `UNIQUE`, sensible a mayúsculas igual que la restricción | `crear_usuarios_y_asignar_roles` |
| POST `/areas/{id}/desactivacion` · `/reactivacion` | Retirar y volver a activar. **La última área activa no se puede retirar**: sin ninguna, la empresa no puede volver a registrar una solicitud | `crear_usuarios_y_asignar_roles` |
| GET `/areas/{id}/impacto` · POST `/areas/{id}/borrado` | Cuántas solicitudes y usuarios cuelgan del área, y borrarla. **Borrar exige reasignar**: las claves ajenas no declaran `ON DELETE`, así que se mueven primero las solicitudes y los usuarios al área de destino y se borra después, en una transacción. Sin destino solo se admite si está vacía; si no, **409 con los dos recuentos**. La auditoría del borrado lleva el nombre y los recuentos porque es lo único que sobrevive | `crear_usuarios_y_asignar_roles` |
| GET/PUT `/parametros` | Los valores que Renaser cambia sin programar. `tope_mensual_ia` se ve pero no se edita desde aquí: lo administra la plataforma | `editar_parametros` |
| GET/POST `/plantillas-correo` | Los textos de correo. Editar = crear versión nueva. **Los retirados no salen en la lista ni admiten versión nueva (400)**: hoy solo `REMUNERACION_ACTUALIZADA`, que dejó de mandarse el 19/09. Sus filas se conservan para explicar los correos que ya salieron | `editar_textos_correo` |
| GET/POST `/textos-consentimiento` | Los textos legales, con su historia. El POST crea la versión nueva **y la publica**. **Desde el 14/09 solo lo usa la plataforma**: los tres textos son suyos —el de postular es uno solo para todas las empresas, con el nombre de cada una puesto al leerlo— y una empresa que intente publicar cualquiera de los tres recibe un **400**. Con ello se retiró el freno que exigía texto propio para publicar una vacante | `editar_textos_correo` |
| GET `/auditoria` | El registro, paginado. No se puede modificar ni borrar | `ver_auditoria` |
| GET `/solicitudes-borrado` · POST `/{id}/ejecucion` | Ver y ejecutar los borrados: la persona queda vacía, la trazabilidad queda. **Solo desde la plataforma**: los candidatos son cuentas de plataforma y la anonimización cruza empresas | `ejecutar_borrado_datos`, y ser la plataforma |
| GET/POST `/usuarios` · POST `/{id}/roles` · GET `/roles` | El equipo y sus roles. El último administrador no se puede quitar | `crear_usuarios_y_asignar_roles` |
| GET/POST `/usuarios/invitaciones` · DELETE `/{id}` | Invitar a alguien al equipo, ver las invitaciones y revocar una sin canjear. La respuesta del POST devuelve el enlace a quien invita | `crear_usuarios_y_asignar_roles` |
| GET `/roles/{id}/permisos` | La matriz de un rol: el catálogo entero, con el alcance de lo concedido y vacío en lo que no | `administrar_permisos` |
| PUT `/roles/{id}/permisos/{codigo}` · POST `…/revocacion` | Conceder con alcance, o quitar. **Motivo obligatorio** | `administrar_permisos` |

**Qué puede cada rol se edita aquí, no en el código.** El `FiltroIdentidad` relee
`rol_permiso` en cada petición, así que un cambio surte efecto en la siguiente llamada de
cada afectado: sin desplegar y sin que nadie tenga que volver a entrar. Por eso mismo
**`ServicioContexto` no lleva caché**, y ponérsela rompería justo esto.

⚠️ `administrar_permisos` va aparte de `crear_usuarios_y_asignar_roles` a propósito: dar un
rol a alguien es una cosa, redefinir lo que ese rol significa es otra bastante mayor —quien
escribe en `rol_permiso` puede concederse todo—. Y **el último rol de la organización que
conserva `administrar_permisos` no se puede quedar sin él**: revocarlo dejaría el reparto sin
nadie que pudiera volver a tocarlo, y de ahí solo se sale entrando a la base a mano. El
candado cuenta por organización y no en total, para que dos organizaciones no se tapen la una
a la otra.

### Las instrucciones de la IA (`/api/v1/panel/agentes-ia`)

| Método y ruta | Qué hace | Permiso |
|---|---|---|
| GET `/agentes-ia` | El catálogo de agentes: cuáles existen y cuáles tienen clase (hoy seis de diez) | `editar_instrucciones_ia` |
| GET `/agentes-ia/instrucciones` | Las instrucciones de cada agente con su historia de versiones | `editar_instrucciones_ia` |
| POST `/agentes-ia/instrucciones` · POST `…/{id}/publicacion` | Escribir una versión nueva de la instrucción de un agente, y publicarla. Publicada, es la que usa la siguiente calificación | `editar_instrucciones_ia` |

⚠️ **La instrucción es una para todo el mundo**: `instruccion_ia` no tiene `organizacion_id`.
Por eso la del agente `PRUEBA_PUESTO` solo puede hablar del oficio de calificar; lo propio de
cada prueba va en la **guía de calificación** de su versión (`guiaCalificacion`, hasta 2000
caracteres, se congela al publicar). Y la IA de la prueba nota **por criterio**, nunca sobre
100: ver [Prueba del puesto](PRUEBA-DEL-PUESTO.md).

### La plataforma y las empresas

Desde el 25/08/2026 el sistema es multiempresa: cada empresa se registra por invitación de
Renaser, publica sus vacantes y ve solo a sus candidatos. El porqué de cada decisión está en
`docs/superpowers/specs/2026-08-25-*.md`.

| Método y ruta | Qué hace | Permiso |
|---|---|---|
| GET `/plataforma/empresas` | La ficha del continente: cada empresa con su estado (activa o suspendida), su tope de IA, sus banderas de personalización y su consumo del mes corriente | `administrar_plataforma`, y ser la plataforma |
| POST `/plataforma/empresas` | Dar de alta una empresa: nace con roles, parámetros, textos legales en borrador, correos activos y el tope de IA si se pide (`topeMensualIa` opcional), y con la invitación de su primer administrador ya enviada | `administrar_plataforma`, y ser la plataforma |
| POST `/plataforma/empresas/{id}/suspension` | Suspenderla, con motivo: su equipo no entra —ni con tokens vivos—, sus vacantes salen del tablón, y los candidatos que ya estaban dentro conservan acceso y datos. La plataforma no puede suspenderse a sí misma | `administrar_plataforma`, y ser la plataforma |
| POST `/plataforma/empresas/{id}/reactivacion` | Reactivarla, con motivo: todo vuelve tal cual | `administrar_plataforma`, y ser la plataforma |
| PUT `/plataforma/empresas/{id}/tope-ia` | Poner, subir o quitar (`tope` en blanco) el tope mensual de IA. Lo que quedó en espera lo despierta solo el sondeo de la cola | `administrar_plataforma`, y ser la plataforma |
| POST/DELETE `/plataforma/empresas/{id}/personalizacion/{instrumento}` | Encender o apagar la personalización **de otra empresa**, con motivo, cuando ella lo pide fuera del sistema. Misma copia y misma auditoría que si lo hiciera ella | `administrar_plataforma`, y ser la plataforma |
| GET `/plataforma/consumo?mes=YYYY-MM` | El consumo de IA del mes por empresa y por agente: total, tokens y llamadas. Con estos números Renaser factura fuera del sistema | `administrar_plataforma`, y ser la plataforma |
| GET `/organizacion/personalizacion` | Qué instrumentos tiene propios esta organización, bandera por bandera | `personalizar_instrumentos` |
| POST `/organizacion/personalizacion` | Encender una bandera (`BANCO`, `PESOS`, `PLANTILLA_EVALUACION`, `PRUEBA`): copia el instrumento publicado de la plataforma y desde ahí se lee y edita lo propio | `personalizar_instrumentos` |
| DELETE `/organizacion/personalizacion/{instrumento}` | Apagarla: se vuelve a leer el de la plataforma. La copia propia se archiva, nunca se borra | `personalizar_instrumentos` |

Con la bandera apagada la empresa **lee** el instrumento de la plataforma —los listados del
panel enseñan el método de Renaser en solo lectura, y una mejora de Renaser llega sola— pero
no lo edita: mutar algo ajeno responde 404. Lo operativo (vacantes, solicitudes,
postulaciones, sesiones) jamás se comparte: lo de otra empresa responde «no existe».

**Renaser administra el continente, no el contenido.** Los endpoints de plataforma llegan a
la ficha de la empresa —estado, tope, banderas, consumo— y ahí se acaban: no existe ningún
camino desde la plataforma hacia los candidatos, notas, alertas ni decisiones de una
empresa, y esa ausencia es el diseño (pieza F). La única grieta consciente es el borrado de
la ley 29733, que ya se ejecuta desde la plataforma porque el candidato es una cuenta de
plataforma; queda auditado y es el único.

### El perfil del candidato

Nuevo desde el 25/08/2026. El candidato tiene un perfil único —de la persona, no de la
postulación— que se llena solo con su currículum y que él corrige. **El contrato completo,
con las reglas que Swagger no cuenta, está en
[APIS-PERFIL-DEL-CANDIDATO.md](APIS-PERFIL-DEL-CANDIDATO.md).** En corto:

| Método y ruta | Qué hace | Permiso |
|---|---|---|
| GET/PUT `/portal/perfil` · GET `/portal/perfil/descarga` | El dueño ve su perfil entero y lo descarga (ley 29733). Vacío responde 200, nunca 404. El PUT **reemplaza** la cabecera, no la fusiona | El propio token; lo ajeno es 404 |
| POST/PUT/DELETE + POST `/{id}/confirmacion` en `/portal/perfil/experiencia`, `/educacion`, `/idiomas`, `/certificaciones` · PUT `/orden` solo en las dos primeras | Añadir, corregir, borrar y dar por bueno lo que se sacó del currículum | El propio token |
| POST y DELETE `/portal/perfil/enlaces` | **Solo esas dos**: un enlace no lleva origen ni confirmación, así que no se edita — se borra y se crea | El propio token |
| POST/GET/DELETE `/portal/perfil/foto`, `/portada` (y PUT `/portada/galeria`), `/cv`; POST/GET/DELETE `/portal/perfil/certificaciones/{id}/archivo` | Desde el 05/09 (V51): la foto (JPG, PNG o WebP, hasta 2 MB), la portada propia o una del catálogo (nunca las dos), el currículum del perfil (PDF o Word, hasta 10 MB; **se lee al subirlo**, sin esperar a que postule) y el diploma de una certificación (PDF hasta 10 MB, o imagen hasta 2 MB). **Los GET devuelven los bytes**, no un enlace firmado: un `<img src>` no manda cabecera. **Nada de esto llega al panel ni a la IA** | El propio token |
| GET `/portal/catalogos/niveles-educativos` · `/niveles-idioma` | Los desplegables, para no escribirlos a mano. Devuelven `codigo` y `nombre` ya ordenados: no hay campo `orden`. El tercero del grupo, `/catalogos/ubigeo`, **es el único que responde sin token**, porque su desplegable sale en el registro | Token de candidato |
| GET `/panel/postulaciones/{id}/perfil` | La trayectoria del candidato sin abrir su archivo. **No puntúa** | `ver_perfil_candidato`; la pretensión pide además `ver_pretension` **y** que la vacante publique lo que paga |

---

## Lo que conviene saber antes de consumirlas

**Los correos no salen todavía.** Cada aviso al candidato queda guardado con su texto exacto en
la base (`correo_enviado`), pero el envío real espera a que Renaser confirme su dominio de
correo. Cuando exista, se enchufa el transporte y nada más cambia.

**Las tres últimas etapas ya viven aquí, pero les falta contenido** (18/08/2026). La mecánica
está construida y se puede llamar; lo que todavía no existe es lo que va dentro:

| Etapa | Qué ya funciona | Qué falta |
|---|---|---|
| Prueba del puesto | El cronómetro corre en el servidor, el cambio aparece en un minuto sorteado, el candidato sube sus entregables y entrega | **La califica una persona**, criterio por criterio: el agente que lo haría no está escrito. Y falta el enunciado real de una prueba en formato de dos horas, que lo escribe Renaser |
| Simulación de trabajo | Sesiones con fecha y cupo, el candidato elige la suya, el facilitador marca los diez eventos y se califica | **El contenido de la sesión**: el enunciado del encargo y la matriz de información crítica —qué se le oculta al candidato y debería preguntar— los escribe Renaser, sesión por sesión. Y las preguntas de la conversación final se escriben a mano, porque el agente que las generaría tampoco está |
| Validación práctica | Habilitar la modalidad, arrancar el periodo, cargar las nueve métricas y cerrar | **Las métricas se cargan a mano.** El campo dice de dónde salió cada valor y hoy todas dicen `PERSONA`; que RENASER OS las alimente solo es la integración que falta |

Ninguna de esas faltas frena a la de al lado: un candidato puede recorrer las cinco etapas de
punta a punta hoy mismo, con personas poniendo las notas.

**El id público de una postulación es su `uuid`,** no el número interno. Es lo que ve el
candidato y lo que viaja en sus rutas.

**El módulo de agentes IA** (`/api/v1/agent-runs`, `/flows`, `/rag`, `/supabase`) es otra zona,
del proyecto original de agentes. **Desde el 24/08/2026 pide token de equipo**, el mismo del
panel; antes estaba abierta a cualquiera.

Lo que obligó a cerrarla: `POST /api/v1/rag/ingest` recibía una ruta del sistema de ficheros
del servidor, la leía, y su texto quedaba consultable por `GET /api/v1/rag/search`. Sin token.
Cualquier PDF de la máquina se podía sacar desde internet.

Ahora la ingesta pide además que la ruta caiga dentro de `renaser.rag.directorio-base`, y esa
propiedad **viene vacía a propósito**: mientras nadie la configure, la ingesta por ruta está
apagada. No hay ningún cliente que la use.

El contrato (`/v3/api-docs`) y Swagger siguen siendo públicos: el fuzzing nocturno los lee
antes de tener token.

---

# Documentos relacionados

| Documento | Qué contiene |
|---|---|
| [Qué hace el sistema](00-QUE-HACE-EL-SISTEMA.md) | El sistema entero, sin nada técnico |
| [Alcance del MVP](08-ALCANCE-DEL-MVP.md) | Qué se construye primero y por qué |
| [Roles y permisos](04-ROLES-Y-PERMISOS.md) | Quién puede hacer qué, acción por acción |
| [Estados de la postulación](03-ESTADOS-POSTULACION.md) | Los estados que mueve esta API |
| [Diccionario de datos](07-DICCIONARIO-DE-DATOS.md) | Las tablas que hay detrás |
