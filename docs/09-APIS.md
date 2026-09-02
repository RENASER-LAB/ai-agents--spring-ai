# Las APIs del sistema

Sistema de selección de personal — Renaser Consulting
Versión 1.6 · 2026-08-26 · Cubre **las cinco etapas del embudo**: postulación, Perfil Integral,
prueba del puesto, simulación de trabajo, validación práctica y decisión final

Este documento explica las APIs para quien las va a consumir: el frontend de RENASER OS y el
portal del candidato. **La referencia viva es Swagger**, en `http://localhost:8081/swagger-ui.html`
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
datos y decir en qué ciudad vive; el consentimiento de futuros contactos es aparte y opcional), y
`POST /portal/auth/login` con correo y contraseña. Si no cuadran, responde **401** con el mismo
texto tanto si el correo no existe como si la contraseña es otra: decir cuál de las dos falló le
regalaría a un atacante la lista de correos registrados. Tras varios intentos fallidos seguidos (configurable, arranca
en 5), la entrada se bloquea unos minutos y responde **429** con la cabecera `Retry-After` y el
campo `segundosDeEspera`, para que la pantalla pueda decir cuánto falta en vez de adivinarlo.

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
| GET `/vacantes` | Las vacantes publicadas **de todas las empresas activas**, cada una con el nombre de la suya. Las de una empresa suspendida no salen | Cualquiera, sin token |
| GET `/vacantes/{id}` | El detalle público, con los requisitos indispensables | Cualquiera |
| GET `/vacantes/{id}/consentimiento` | El texto de tratamiento de datos **de la empresa de esa vacante**: lo que se acepta al postular, con el nombre de quien tratará los datos | Cualquiera |
| GET `/consentimientos/textos` | Los textos vigentes de los dos consentimientos de la plataforma (los de crear la cuenta) | Cualquiera |
| GET `/catalogos/ubigeo` | Dónde se puede decir que uno vive: las **196 provincias** del Perú y «Fuera del Perú», cada una con su departamento, ordenadas por departamento y nombre. Es `{codigo, nombre, departamento}`, y el departamento viene vacío solo en `EXT` | Cualquiera, sin token |
| POST `/cuentas` | Crear la cuenta y registrar los consentimientos. Desde el 31/08 pide además **la ciudad** (`ciudadUbigeo`), obligatoria: un código que el catálogo no ofrezca es un 400 | Cualquiera |
| POST `/auth/login` | Entrar; devuelve el token | Cualquiera |
| POST `/postulaciones` | Postular: CV (PDF o Word, máx. 10 MB), enlaces, el resultado del que se siente orgulloso, la confirmación de los requisitos y `aceptaTratamiento` (obligatorio): la aceptación del texto de la empresa queda firmada con IP y navegador, a nombre de esa postulación | Candidato |
| GET `/postulaciones` | Sus postulaciones, con la empresa de cada una, estado, días sin cambio y **qué rendirá en la etapa técnica** (`instrumentoEtapaTecnica`: la prueba del puesto o el cuestionario) | Candidato |
| GET `/postulaciones/{uuid}` | El detalle de una suya, con el historial completo | Candidato |
| POST `/postulaciones/{uuid}/retiro` | Retirarla. **No borra sus datos**: eso se pide aparte | Candidato |
| POST `/consentimientos/futuros/retiro` | Retirar el consentimiento de futuros contactos | Candidato |
| POST `/solicitudes-borrado` | Pedir el borrado de sus datos | Candidato |
| GET `/evaluacion/{uuid}` | Su evaluación: las preguntas en **su** orden y lo que lleva respondido | Candidato |
| POST `/evaluacion/{uuid}/inicio` | Empezar. La primera vez elige qué preguntas le tocan | Candidato |
| PUT `/evaluacion/{uuid}/respuestas/{preguntaId}` | Guardar una respuesta | Candidato |
| POST `/evaluacion/{uuid}/entrega` | Entregar. Ya no se cambia, y pasa a calificarse | Candidato |
| GET `/cuestionario-tecnico/{uuid}` | Su cuestionario técnico, cuando la vacante rinde ese instrumento: las preguntas **sin la PRESENCIAL** y sin la guía de calificación | Candidato |
| POST `/cuestionario-tecnico/{uuid}/inicio` | Empezar. Aquí arranca el reloj, si la vacante fijó minutos | Candidato |
| PUT `/cuestionario-tecnico/{uuid}/respuestas/{preguntaId}` | Guardar una respuesta. Solo texto: aquí no se suben archivos | Candidato |
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
| GET `/vacantes` · `/{id}` | Todas las vacantes | `ver_vacantes` |
| POST `/vacantes` | Crear en borrador. **Exige una solicitud aprobada** | `crear_vacante` |
| PUT `/vacantes/{id}` | Editar mientras no esté cerrada | `editar_vacante` |
| GET/POST `/vacantes/{id}/requisitos` · DELETE `/{requisitoId}` | Los requisitos indispensables. No se borran: se desactivan | `definir_requisitos_objetivos` |
| POST `/vacantes/{id}/plantilla-evaluacion` | Qué evaluación responderá quien postule. **Hace falta antes de publicar** | `elegir_plantilla_evaluacion` |
| POST `/vacantes/{id}/plantilla-prueba` | Qué prueba del puesto rendirá quien llegue a esa etapa. **Hace falta antes de publicar** | `elegir_plantilla_prueba` |
| POST `/vacantes/{id}/aplicacion-evaluacion` | Encender o apagar la evaluación del banco en esta vacante. Apagada, quien postule cae directo en la bandeja del equipo y su única evaluación es la prueba; publicar deja de exigir plantilla de evaluación | `elegir_plantilla_evaluacion` |
| POST `/vacantes/{id}/version-pesos` | Qué versión de pesos (publicada) rige la decisión de esta vacante. No recalcula nada hacia atrás | `publicar_version_pesos` |
| POST `/vacantes/{id}/cierre-prueba` | Fijar cuándo cierra la prueba de esta vacante, para todos. **Mueve también los intentos ya abiertos**, salvo los de quien tenga fecha propia. Con `cierraEn` vacío se quita y se vuelven a contar los días de la plantilla | `elegir_plantilla_prueba` |
| GET/PUT `/vacantes/{id}/ficha` | La ficha del método CAZATALENTOS: las 10 preguntas al dueño, guardable a medias (BORRADOR). El tamaño (MICRO/MEDIA/GRANDE) se deriva de la gente en la empresa y la respuesta sugiere la `version_pesos` que toca; COMPLETA es lo que permite generar el cuestionario. Los riesgos van en orden de velocidad de daño y no admiten huecos | `ver_vacantes` / `editar_vacante` |
| GET `/vacantes/{id}/cuestionario-tecnico` | El cuestionario de la vacante: el borrador si hay, si no la publicada, con el estado de la generación (SIN_PEDIR·EN_CURSO·FALLIDA·LISTA) y si quedó desactualizado respecto a la ficha | `ver_vacantes` |
| POST `/vacantes/{id}/cuestionario-tecnico/generacion` | Pedir al agente REDACTOR el borrador (202). Exige la ficha COMPLETA; con una generación viva o la IA apagada responde `encolada=false`. Cuenta contra el tope mensual de IA | `editar_vacante` |
| PUT `/vacantes/{id}/cuestionario-tecnico/preguntas/{preguntaId}` | Corregir una pregunta del borrador con las palabras del dueño (enunciado y guía C3/C4/señal) | `editar_vacante` |
| GET `/vacantes/{id}` | La vacante con su configuración: qué evaluación y qué prueba tiene, sus pesos, y **qué instrumento y cuántos minutos** rigen su etapa técnica | `ver_vacantes` |
| POST `/vacantes/{id}/instrumento-tecnico` | Qué se rinde en la etapa técnica de esta vacante —`PLANTILLA` (la prueba del puesto) o `CUESTIONARIO_TECNICO` (el cuestionario CAZATALENTOS)— y en cuántos minutos. **Uno de los dos, nunca los dos**: publicar exige tener listo el que se eligió. **Se frena en cuanto alguien EMPEZÓ su etapa técnica**, no al recibir la primera postulación: postular no es rendir, y quien no ha abierto nada no tiene nada que moverle debajo. Minutos vacíos = los del instrumento; si se ponen, **al menos 5**, rigen los DOS instrumentos y se leen al empezar el examen, así que corregirlos alcanza a todo el que aún no lo haya abierto | `elegir_plantilla_prueba` |
| POST `/vacantes/{id}/cuestionario-tecnico/publicacion` | Publicar el borrador: el acto humano que vuelve real el cuestionario. Re-pasa la aduana entera (cantidades del nivel, presencial donde toca, guía completa, temas prohibidos) y archiva la publicada anterior **de esta vacante** — los bancos por nivel ni se miran | `editar_vacante` |
| GET `/vacantes/{id}/plantillas-correo` | Qué avisos manda esta vacante con texto propio. Vacío = los de siempre | `ver_vacantes` |
| POST `/vacantes/{id}/plantillas-correo` · DELETE `/{avisoCodigo}` | Hacer que esta vacante mande otro texto en lugar del aviso que le tocaba, y devolverlo al de siempre. **Una plantilla es una por organización**: sin esto, cambiar el texto de una convocatoria se lo cambia a todas | `editar_textos_correo` |
| GET/POST `/vacantes/{id}/barreras-criticas` | Las capacidades que ningún promedio alto compensa | `definir_barreras_criticas` |
| POST `/vacantes/{id}/publicacion` | Publicar: aparece en el portal | `publicar_vacante` |
| POST `/vacantes/{id}/cierre` | Cerrar: frena postulaciones nuevas, **no arrastra las que van en marcha** | `cerrar_vacante` |

### Postulaciones

| Método y ruta | Qué hace | Permiso |
|---|---|---|
| GET `/bandeja?espera_a=` | La bandeja: todo lo que espera a `CANDIDATO`, `SISTEMA`, `TALENTO` o `AREA` | `ver_candidatos` |
| GET `/vacantes/{id}/embudo` | Cuántas postulaciones hay en cada estado | `ver_embudo` |
| GET `/vacantes/{id}/ranking?etapa=` | La tanda ordenada de más apto a menos, con las ocho notas del currículum de cada uno. **Incluye a quien todavía no tiene nota**. Sin `etapa` ordena por la del Perfil Integral; con ella, por la nota de esa etapa. Cada fila trae además **dónde vive** (`ciudad`, ya escrito «Departamento — Provincia», y `ciudadCodigo`) y **su pretensión salarial**, que solo viaja con `ver_pretension`. Cada una de las ocho notas trae **los dos nombres del criterio**: el largo en `criterio` y el corto en `codigo` | `ver_embudo` |
| POST `/vacantes/{id}/ranking/excel` | La tanda seleccionada, en un `.xlsx` de dos hojas —Resumen y Detalle— que se descarga como adjunto. Se le pasan `etapa`, los `postulacionIds` **ya ordenados por quien llama** y `filtroDescrito`, la frase que se pintó encima de la tabla. Solo hay columnas para `PERFIL_INTEGRAL` y `PRUEBA_PUESTO`; otra etapa es un 400 | `ver_embudo` |
| GET `/postulaciones/{id}` · `/historial` | La ficha completa y el recorrido | `abrir_ficha_candidato` |
| POST `/postulaciones/{id}/transiciones` | Mover a cualquier estado. **El motivo es obligatorio, sin excepción** | `mover_postulacion` |
| POST `/postulaciones/{id}/confirmacion-avance` | Confirmar que avanza: el sistema calcula el estado siguiente | `confirmar_avance` |
| GET `/postulaciones/{id}/perfil-integral` | El retrato de la IA: notas del currículum, hallazgos y avisos. Cada nota lleva su explicación, **su confianza —de 0 a 100, la misma escala del puntaje, no de 0 a 1—** y el **motivo del ajuste**, que solo tiene valor cuando esa nota la corrigió una persona | `ver_perfil_integral` |
| GET `/postulaciones/{id}/evaluacion` | El desglose del banco: cada respuesta abierta con su nota, la explicación y la evidencia que citó la IA, el promedio de lo cerrado y los semáforos de alineación. **Sin evaluación asignada devuelve vacíos, no 404**. ⚠️ `alineacion` sale vacía siempre: nadie escribe esa tabla todavía | `ver_respuestas_evaluacion` |
| POST `/postulaciones/{id}/criba-cv` | Que la IA lea **solo el currículum** y arme el retrato con eso. Es lo que se pide con una tanda recién llegada | `ajustar_nota` |
| POST `/postulaciones/{id}/calificacion-perfil-integral` | Calificar con todo: currículum y evaluación. Exige evaluación entregada | `ajustar_nota` |
| POST `/postulaciones/{id}/cv` | Reemplazar el currículum desde el panel | `ajustar_nota` |
| GET `/archivos/{id}/descarga` | Descargar el CV | `descargar_entregables` |

> **Cada nota de criterio viaja con dos nombres, y no sobra ninguno.** `criterio` es el largo
> —«Resultados demostrables»— y `codigo` el corto —`CV_RESULTADOS`, `CV_EVIDENCIA`—, el mismo que
> la tabla `criterio` ya guardaba desde que se sembraron los ocho. Salen los dos en `/ranking` y en
> `/perfil-integral`, y el corto **no reemplaza al largo**: el corto rotula y el largo explica.
>
> El corto existe porque comparar candidatos se hace en una tabla con **una columna por criterio**,
> y la cabecera es lo que decide el ancho de esa columna. Poner «Resultados demostrables» encima de
> una celda que dice `40` le da a la columna el ancho del título y no el del dato; con los ocho
> nombres largos, la tabla se sale de la ventana. Con el código cabe.
>
> Y el largo no se puede tirar: `CV_EVIDENCIA` no le dice nada a quien no se sabe la rúbrica de
> memoria, y una pantalla que solo enseñe el código obliga a adivinar qué se está puntuando.
> Quien pinte una columna estrecha usa el código y **deja el nombre para el título emergente**.
>
> El código sirve de rótulo porque es estable dentro de su rúbrica: `criterio` lo tiene único por
> `codigo` + `version_plantilla_prueba_id`. **No es un identificador global**: dos rúbricas de
> prueba distintas pueden tener cada una su `CAJA` sin ser el mismo criterio, así que sirve para
> nombrar dentro de la tabla que se está mirando y no para casar criterios entre rúbricas. En
> estos dos endpoints los ocho son siempre los del currículum, que son globales.

> **Hay un ranking por etapa, y es el mismo endpoint.** `?etapa=PERFIL_INTEGRAL` —que equivale
> a no pasarlo—, `PRUEBA_PUESTO`, `SIMULACION`, `VALIDACION` o `DECISION` cambia **solo la nota con la que se ordena**: las ocho notas del
> currículum de cada fila siguen siendo las del Perfil Integral, porque son de esa etapa siempre.
> Sin el parámetro se comporta exactamente como antes —así lo llama la criba fina, que decide a
> quién recalificar por la nota de preselección—, y una etapa que no esté en el catálogo es un 400.
> Quien no tiene nota en la etapa pedida sale al final, sin heredar la de otra.
>
> Sigue sin haber un ranking **general** que mezcle las cuatro etapas en una sola nota. La
> Puntuación Global está calculada —sale en `/postulaciones/{id}/semaforo`—, pero nunca como lista
> ordenada. Está apuntado como decisión 6 en [Alcance del MVP](08-ALCANCE-DEL-MVP.md), con lo que
> habría que decidir antes de montarlo.

> **Quién ordena y quién filtra el Excel: el cliente.** El volcado no filtra ni reordena nada. Le
> llegan los `postulacionIds` en el orden en que se quieren las filas y los escribe en ese orden,
> porque la tanda del ranking viene ordenada por grupo de prioridad y nota —otro orden, también
> válido, y no el que la persona estaba mirando—. Se piden por POST y no por GET porque ochenta
> ids y la frase del filtro no caben en una URL. **No escribe nada**: lo único que crea es el
> archivo.
>
> Los ids que no son de esa vacante **se descartan y se dicen al pie de la hoja**, con su número
> y su lista; si ninguno de los pedidos es de la vacante, es un 400. La hoja **Resumen** lleva una
> fila por candidato —con el puesto que ocupa en el ranking, no la posición en el archivo— y la
> hoja **Detalle** una línea por criterio; no hay hoja de respuestas. El archivo baja como adjunto
> y se llama `ranking-{etapa}-vacante-{id}-{fecha}.xlsx`, con la fecha dentro porque estas hojas
> se guardan.
>
> **La columna de pretensión se explica sola.** Vacía significa dos cosas opuestas —que nadie la
> declaró, o que el rol de quien descarga no puede verla— y solo una es verdad cada vez, así que
> el pie lo dice. Por eso mismo el ranking devuelve `puedeVerPretension` en su cabecera: sin ese
> booleano, la pantalla tendría que nombrar las dos posibilidades sin afirmar ninguna. Sin
> `ver_pretension` el dato ni se consulta. Y quien tiene `ver_embudo` pero no `ajustar_nota` se
> lleva el Detalle de la prueba resumido en una línea que explica qué permiso le falta, en vez de
> un archivo a medio escribir.

### La prueba del puesto (hito 3)

| Método y ruta | Qué hace | Permiso |
|---|---|---|
| POST `/plantillas-prueba` · `/{id}/versiones` | Crear la plantilla y una versión en borrador | `editar_plantillas_prueba` |
| GET `/plantillas-prueba/{id}/versiones` | Las versiones de esa plantilla, de la más nueva a la más vieja. Vienen todas, borradores incluidos: el estado dice cuál se puede usar | `elegir_plantilla_prueba` |
| PUT `/plantillas-prueba/versiones/{id}` · POST `…/{id}/consigna` · PUT y DELETE sobre `/entregables/{id}`, `/rubrica/{id}`, `/variantes/{id}` · DELETE `…/preguntas/{id}` · PUT `…/orden` | **Componer un borrador**: corregir, quitar y reordenar cada pieza, y subir el ENUNCIADO como PDF o Word. **Solo en `BORRADOR`**; sobre una versión publicada responden 409 y la salida a un error sigue siendo una versión nueva | `editar_plantillas_prueba` |
| POST `/plantillas-prueba/versiones/{id}/publicacion` | Publicar: exige 8-10 preguntas universales, 3-5 específicas, y la rúbrica sumando 100. **Una versión sin entregables es un cuestionario**: la cuota no rige y basta con una pregunta. La duración, si es cronometrada, **al menos 5 minutos y sin techo** (el rango 60-120 se retiró el 31/08/2026) | `editar_plantillas_prueba` |
| POST `/postulaciones/{id}/prueba/plazo` | Fijarle a ESE candidato su fecha de cierre, normalmente para darle más horas. **Queda marcada como suya**: mover después la fecha de la vacante no se la toca. Antes de empezar, la fecha puesta manda sobre el cálculo por días | `mover_postulacion` |
| GET `/postulaciones/{id}/prueba/respuestas` | Lo que contestó, pregunta a pregunta. Las preguntas son **las de la versión que él vio**, en su orden, no las del catálogo de hoy: una versión publicada después puede llevar otras | `abrir_ficha_candidato` |
| GET `/postulaciones/{id}/prueba/notas` | La rúbrica entera con lo que lleva puesto cada criterio: puntaje, explicación y **de quién viene la nota**, si de la IA o de una persona. Lo que aún no tiene nota sale en nulo | `ajustar_nota` |
| POST `/postulaciones/{id}/prueba/criterios/{criterioId}/nota` | Poner la nota de un criterio, con explicación obligatoria | `ajustar_nota` |
| POST `/postulaciones/{id}/prueba/calificacion-ia` | Pedirle al agente `PRUEBA_PUESTO` los criterios que la rúbrica le reserva. Tarda decenas de segundos y **no pisa ningún ajuste hecho a mano**. Al acabar, si la rúbrica quedó entera **deja también la nota de la etapa** | `ajustar_nota` |
| POST `/postulaciones/{id}/prueba/calificacion` | Ponderar las notas ya puestas. Exige que estén todos los criterios. **Escribe**: deja la nota guardada, no es una consulta. Desde el 28/08 **ya no es el único camino**: si el agente deja la rúbrica entera, la nota sale sola y esta llamada solo la reescribe con lo mismo. Sigue haciendo falta cuando los últimos criterios los pone una persona | `ajustar_nota` |

**El portal del candidato es `/api/v1/portal/prueba/{codigo}`**: ver, iniciar (arranca el
reloj), responder, subir entregables y entregar. Mismas reglas que la evaluación: nada de
lo interno viaja, y una prueba ajena responde 404.

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
| GET `/postulaciones/{id}/semaforo` | La Puntuación Global y una propuesta de semáforo | `ver_semaforo_decision` |
| POST `/postulaciones/{id}/decision` | Decidir. El motivo es siempre obligatorio (RF-119) | `decidir_contratacion` la primera vez, `cambiar_decision` para corregir |
| POST `/postulaciones/{id}/evidencia-adicional` | Pedir evidencia adicional cuando sale ámbar. Tope configurable | `pedir_evidencia_adicional` |

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

### Administración

| Método y ruta | Qué hace | Permiso |
|---|---|---|
| GET `/areas` · POST `/areas` | Las áreas de la organización: hace falta una para registrar una solicitud | `ver_solicitudes` / `crear_usuarios_y_asignar_roles` |
| GET/PUT `/parametros` | Los valores que Renaser cambia sin programar. `tope_mensual_ia` se ve pero no se edita desde aquí: lo administra la plataforma | `editar_parametros` |
| GET/POST `/plantillas-correo` | Los textos de correo. Editar = crear versión nueva | `editar_textos_correo` |
| GET/POST `/textos-consentimiento` | Los textos legales de la organización, con su historia. El POST crea la versión nueva **y la publica**: es lo que abre la puerta de publicar vacantes — sin texto PROCESO publicado no se reciben candidatos | `editar_textos_correo` |
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
| GET `/portal/catalogos/niveles-educativos` · `/niveles-idioma` | Los desplegables, para no escribirlos a mano. Devuelven `codigo` y `nombre` ya ordenados: no hay campo `orden`. El tercero del grupo, `/catalogos/ubigeo`, **es el único que responde sin token**, porque su desplegable sale en el registro | Token de candidato |
| GET `/panel/postulaciones/{id}/perfil` | La trayectoria del candidato sin abrir su archivo. **No puntúa** | `ver_perfil_candidato`; la pretensión solo con `ver_pretension` |

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
