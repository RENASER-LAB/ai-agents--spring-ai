# Las APIs del sistema

Sistema de selección de personal — Renaser Consulting
Versión 1.5 · 2026-08-25 · Cubre **las cinco etapas del embudo**: postulación, Perfil Integral,
prueba del puesto, simulación de trabajo, validación práctica y decisión final

Este documento explica las APIs para quien las va a consumir: el frontend de RENASER OS y el
portal del candidato. **La referencia viva es Swagger**, en `http://localhost:8080/swagger-ui.html`
cuando la aplicación corre: ahí están los cuerpos exactos, se prueban las llamadas y siempre está
al día porque se genera del código. Este documento cuenta lo que Swagger no cuenta: cómo entrar,
qué puerta usar y las reglas que no se ven en un esquema.

---

## Las dos puertas

Todo vive bajo `/api/v1/`, en dos zonas con reglas distintas:

| Puerta | Quién la usa | Cómo se identifica |
|---|---|---|
| `/api/v1/portal/**` | El candidato | Token propio, de crear cuenta y entrar con correo y contraseña |
| `/api/v1/panel/**` | El equipo de Renaser | Token de equipo. Lo emitirá RENASER OS; mientras no exista ese contrato, el login de desarrollo |

El token va en cada llamada, en la cabecera `Authorization: Bearer <token>`.

Un token de candidato **no abre** el panel, ni al revés. Y dentro del panel, cada acción exige su
permiso: quien no lo tiene recibe un **403 con explicación**, no un error opaco. Además el
permiso tiene **alcance**: el responsable de un área solo ve las postulaciones de sus vacantes,
aunque llame al mismo endpoint que Talento.

## Cómo entrar

**El candidato:** `POST /portal/cuentas` para crear la cuenta (exige aceptar el tratamiento de
datos; el consentimiento de futuros contactos es aparte y opcional), y `POST /portal/auth/login`
con correo y contraseña. Si no cuadran, responde **401** con el mismo texto tanto si el correo
no existe como si la contraseña es otra: decir cuál de las dos falló le regalaría a un atacante
la lista de correos registrados. Tras varios intentos fallidos seguidos (configurable, arranca
en 5), la entrada se bloquea unos minutos y responde **429** con la cabecera `Retry-After` y el
campo `segundosDeEspera`, para que la pantalla pueda decir cuánto falta en vez de adivinarlo.

**El equipo, mientras no hay RENASER OS:** `POST /panel/auth/dev-login` con el id de RENASER OS.
El primer id que entre en una base recién creada se registra solo, con los roles completos del
equipo — es el arranque de desarrollo. En producción este login se apaga con
`app.seguridad.dev-login-activo: false`.

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
| GET `/vacantes` | Las vacantes publicadas | Cualquiera, sin token |
| GET `/vacantes/{id}` | El detalle público, con los requisitos indispensables | Cualquiera |
| GET `/consentimientos/textos` | Los textos vigentes de los dos consentimientos | Cualquiera |
| POST `/cuentas` | Crear la cuenta y registrar los consentimientos | Cualquiera |
| POST `/auth/login` | Entrar; devuelve el token | Cualquiera |
| POST `/postulaciones` | Postular: CV (PDF o Word, máx. 10 MB), enlaces, el resultado del que se siente orgulloso, y la confirmación de los requisitos | Candidato |
| GET `/postulaciones` | Sus postulaciones, con estado y días sin cambio | Candidato |
| GET `/postulaciones/{uuid}` | El detalle de una suya, con el historial completo | Candidato |
| POST `/postulaciones/{uuid}/retiro` | Retirarla. **No borra sus datos**: eso se pide aparte | Candidato |
| POST `/consentimientos/futuros/retiro` | Retirar el consentimiento de futuros contactos | Candidato |
| POST `/solicitudes-borrado` | Pedir el borrado de sus datos | Candidato |
| GET `/evaluacion/{uuid}` | Su evaluación: las preguntas en **su** orden y lo que lleva respondido | Candidato |
| POST `/evaluacion/{uuid}/inicio` | Empezar. La primera vez elige qué preguntas le tocan | Candidato |
| PUT `/evaluacion/{uuid}/respuestas/{preguntaId}` | Guardar una respuesta | Candidato |
| POST `/evaluacion/{uuid}/entrega` | Entregar. Ya no se cambia, y pasa a calificarse | Candidato |

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
| GET `/vacantes/{id}/ranking?etapa=` | La tanda ordenada de más apto a menos, con las ocho notas del currículum de cada uno. **Incluye a quien todavía no tiene nota**. Sin `etapa` ordena por la del Perfil Integral; con ella, por la nota de esa etapa | `ver_embudo` |
| GET `/postulaciones/{id}` · `/historial` | La ficha completa y el recorrido | `abrir_ficha_candidato` |
| POST `/postulaciones/{id}/transiciones` | Mover a cualquier estado. **El motivo es obligatorio, sin excepción** | `mover_postulacion` |
| POST `/postulaciones/{id}/confirmacion-avance` | Confirmar que avanza: el sistema calcula el estado siguiente | `confirmar_avance` |
| GET `/postulaciones/{id}/perfil-integral` | El retrato de la IA: notas del currículum, hallazgos y avisos | `ver_perfil_integral` |
| GET `/postulaciones/{id}/evaluacion` | El desglose del banco: cada respuesta abierta con su nota, la explicación y la evidencia que citó la IA, el promedio de lo cerrado y los semáforos de alineación. **Sin evaluación asignada devuelve vacíos, no 404**. ⚠️ `alineacion` sale vacía siempre: nadie escribe esa tabla todavía | `ver_respuestas_evaluacion` |
| POST `/postulaciones/{id}/criba-cv` | Que la IA lea **solo el currículum** y arme el retrato con eso. Es lo que se pide con una tanda recién llegada | `ajustar_nota` |
| POST `/postulaciones/{id}/calificacion-perfil-integral` | Calificar con todo: currículum y evaluación. Exige evaluación entregada | `ajustar_nota` |
| POST `/postulaciones/{id}/cv` | Reemplazar el currículum desde el panel | `ajustar_nota` |
| GET `/archivos/{id}/descarga` | Descargar el CV | `descargar_entregables` |

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

### La prueba del puesto (hito 3)

| Método y ruta | Qué hace | Permiso |
|---|---|---|
| POST `/plantillas-prueba` · `/{id}/versiones` | Crear la plantilla y una versión en borrador | `editar_plantillas_prueba` |
| POST `/plantillas-prueba/versiones/{id}/publicacion` | Publicar: exige 8-10 preguntas universales, 3-5 específicas, y la rúbrica sumando 100. **Una versión sin entregables es un cuestionario**: la cuota no rige y basta con una pregunta | `editar_plantillas_prueba` |
| POST `/postulaciones/{id}/prueba/plazo` | Fijarle a ESE candidato su fecha de cierre, normalmente para darle más horas. **Queda marcada como suya**: mover después la fecha de la vacante no se la toca. Antes de empezar, la fecha puesta manda sobre el cálculo por días | `mover_postulacion` |
| GET `/postulaciones/{id}/prueba/respuestas` | Lo que contestó, pregunta a pregunta. Las preguntas son **las de la versión que él vio**, en su orden, no las del catálogo de hoy: una versión publicada después puede llevar otras | `abrir_ficha_candidato` |
| GET `/postulaciones/{id}/prueba/notas` | La rúbrica entera con lo que lleva puesto cada criterio: puntaje, explicación y **de quién viene la nota**, si de la IA o de una persona. Lo que aún no tiene nota sale en nulo | `ajustar_nota` |
| POST `/postulaciones/{id}/prueba/criterios/{criterioId}/nota` | Poner la nota de un criterio, con explicación obligatoria | `ajustar_nota` |
| POST `/postulaciones/{id}/prueba/calificacion-ia` | Pedirle al agente `PRUEBA_PUESTO` los criterios que la rúbrica le reserva. Tarda decenas de segundos y **no pisa ningún ajuste hecho a mano** | `ajustar_nota` |
| POST `/postulaciones/{id}/prueba/calificacion` | Ponderar las notas ya puestas. Exige que estén todos los criterios. **Escribe**: deja la nota guardada, no es una consulta | `ajustar_nota` |

**El portal del candidato es `/api/v1/portal/prueba/{codigo}`**: ver, iniciar (arranca el
reloj), responder, subir entregables y entregar. Mismas reglas que la evaluación: nada de
lo interno viaja, y una prueba ajena responde 404.

### Simulación de trabajo

| Método y ruta | Qué hace | Permiso |
|---|---|---|
| GET/POST `/sesiones-simulacion` | Las sesiones con fecha y cupo. Publicar una mueve a quien estaba esperando | `crear_sesiones_simulacion` |
| POST `/sesiones-simulacion/{id}/cupo` · `/cancelacion` | Ampliar o cancelar. Al cancelar se avisa a los inscritos | `crear_sesiones_simulacion` |
| POST `/sesiones-simulacion/{id}/responsables` | Quién conduce la sesión | `crear_sesiones_simulacion` |
| GET/POST `/sesiones-simulacion/{id}/informacion-critica` | Qué debería preguntar un candidato fuerte | `definir_informacion_critica` |
| GET/POST `/inscripciones/{id}/marcas` | Los diez eventos observables, marcados en vivo | `marcar_eventos_simulacion` |
| POST `/inscripciones/{id}/asistencia` | Si asistió. Si no, vuelve a la bandeja del equipo | `marcar_asistencia` |
| POST `/postulaciones/{id}/ausencia-simulacion` | Qué hacer con quien faltó: otra fecha o cerrar | `decidir_sobre_ausente` |
| POST `/postulaciones/{id}/simulacion/...` | Poner notas y ponderarlas, como en la prueba | `calificar_simulacion` |
| GET/POST `/postulaciones/{id}/conversacion-final` | Las 3-5 preguntas y lo que se respondió | `hacer_conversacion_final` |

**El portal del candidato es `/portal/simulacion/{codigo}`**: ver las fechas de su vacante que
tengan cupo, elegir una, y consultar la que eligió.

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
| GET/POST `/banco-preguntas/versiones/{id}/preguntas` | Las preguntas: los 14 formatos (6 del v0.1 + 8 del v3) con peso, ítem clave y eliminatorio | `ver` / `editar_banco_preguntas` |
| GET/POST `/banco-preguntas/preguntas/{id}/opciones` | Las opciones con su clave: puntaje, valor oculto (EF-4), distractor (INV/DE), orden correcto (SEC) | `ver` / `editar_banco_preguntas` |
| GET/POST `/banco-preguntas/preguntas/{id}/rangos` | Los tramos de puntaje de los ítems V | `ver` / `editar_banco_preguntas` |
| GET/POST `/banco-preguntas/preguntas/{id}/campos-caso` | Los campos de los casos descompuestos (CD) | `ver` / `editar_banco_preguntas` |
| GET/POST `/banco-preguntas/versiones/{id}/pares-consistencia` | Emparejar dos preguntas de la versión para vigilar contradicciones | `ver` / `editar_banco_preguntas` |
| POST `/banco-preguntas/importaciones` | **Subir la plantilla Excel** (multipart: `archivo`, `nivelPuestoCodigo`, `etiqueta`). Crea una versión en borrador con todo el archivo; si algo no cuadra, 400 con la lista `{hoja, fila, mensaje}` y no se importa nada | `editar_banco_preguntas` |
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
| GET/PUT `/parametros` | Los valores que Renaser cambia sin programar | `editar_parametros` |
| GET/POST `/plantillas-correo` | Los textos de correo. Editar = crear versión nueva | `editar_textos_correo` |
| GET `/auditoria` | El registro, paginado. No se puede modificar ni borrar | `ver_auditoria` |
| GET `/solicitudes-borrado` · POST `/{id}/ejecucion` | Ver y ejecutar los borrados: la persona queda vacía, la trazabilidad queda | `ejecutar_borrado_datos` |
| GET/POST `/usuarios` · POST `/{id}/roles` · GET `/roles` | El equipo y sus roles. El último administrador no se puede quitar | `crear_usuarios_y_asignar_roles` |

### El perfil del candidato

Nuevo desde el 25/08/2026. El candidato tiene un perfil único —de la persona, no de la
postulación— que se llena solo con su currículum y que él corrige. **El contrato completo,
con las reglas que Swagger no cuenta, está en
[APIS-PERFIL-DEL-CANDIDATO.md](APIS-PERFIL-DEL-CANDIDATO.md).** En corto:

| Método y ruta | Qué hace | Permiso |
|---|---|---|
| GET/PUT `/portal/perfil` · POST/PUT/DELETE `/portal/perfil/{lista}[/{id}]` · POST `…/{id}/confirmacion` · PUT `…/orden` · GET `…/descarga` | El dueño ve, edita, confirma, reordena y descarga lo suyo. Vacío responde 200, nunca 404 | El propio token; lo ajeno es 404 |
| GET `/portal/catalogos/niveles-educativos` · `/niveles-idioma` | Los desplegables, para no escribirlos a mano | Token de candidato |
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
