## Documentacion Generada
Generar la documentacion detallada, con palabras basicas del español, breve. Diseñado para que el usuario lo pueda leer y entender. El objetivo de los documentos no es que sean grandes, sino, entendibles.

## Respuestas
Responde en el chat de manera breve, corta y directa, eliminando puntos innecesarios y ambiguedades, tambien eliminando partes de código amenos que sea muy necesario o la pregunta del usuario sea muy especifica del código. Si para 
implementar algo es necesario alguna acción por parte de mi, como por ejemplo: Ejecutar un script en la base de datos. Entonces me das una sección que se llamará "Flujo de Implementación" en la cual me darás los pasos especificos que 
debo de cumplir en orden para poder realizar la implementación adecuadamente. Si tengo que tomar decisiones damelos en una nueva sección que diga "Decisiones" para asi evitar confundirlo con texto normal, dentro de esa sección me dirás lo que tengo que decidir o la informacion que tengo que darte.

## Como preguntar
No uses el vocabulario de los documentos del cliente al hacer preguntas: sus terminos son
ambiguos (ej. "corte", "gate", "avanzar"). Interpreta el significado con todo el contexto y
pregunta en palabras propias y concretas, describiendo la situacion real.

## El backend
El repositorio del backend es este mismo directorio: `ai-engine`, un solo proyecto para todo
el sistema (reclutamiento y agentes de IA juntos).

| | |
|---|---|
| Java | 25 |
| Spring Boot | 4.1 |
| Base de datos | PostgreSQL propio con pgvector, en `docker-compose.yml`, puerto 5433 |
| Cola | RabbitMQ, para calificar en segundo plano |
| IA · chat | Spring AI con **DeepSeek** (`deepseek-v4-flash`), API externa |
| IA · embeddings | **Google Gemini** (`gemini-embedding-2`, 1536 dimensiones), API externa |
| Otros | MapStruct, Lombok, hilos virtuales |

Las claves viven en `application-secrets.yaml`, en la raiz y fuera de git. El
`application.yaml` lo carga con `spring.config.import`, asi que no hacen falta variables de
entorno. Flyway, Security, JWT, Validation y Swagger ya estan en el `pom.xml` y compilan.

**Donde salen los datos.** Ya no queda nada local: el chat va a DeepSeek y los embeddings a
Google. **Renaser acepto que los datos de candidatos salgan hacia esos dos** (decision del
18/08/2026), asi que la pregunta ya no es si salen, sino que el candidato lo sepa.

⚠️ **Los textos de consentimiento todavia no nombran a ningun tercero, y esto ya es urgente.**
Antes no rompia nada porque la IA no leia a nadie. **Desde el 18/08/2026 si lee**: los tres
agentes del hito 2 corren de verdad y el curriculum sale hacia DeepSeek. Renaser tiene que
aprobar un texto que nombre a las dos empresas **antes de que pase por ahi el primer candidato
real**. Hay un borrador en [docs/BORRADOR-CONSENTIMIENTO-v1.1.md](docs/BORRADOR-CONSENTIMIENTO-v1.1.md).

El curriculum se anonimiza antes de salir: edad, sexo y estado civil quedan tapados, y se
guardan las dos versiones para poder demostrar que la regla se cumplio.

## RENASER OS
Es un proyecto avanzado y este backend es un modulo suyo. Son **dos servicios separados que
hablan por HTTP**: no comparten base de datos, asi que no hay claves foraneas contra sus tablas.

| Que | Como |
|---|---|
| Identidad del equipo de Renaser | RENASER OS emite el token; aqui solo se valida. No guardamos su contrasena |
| Identidad de los candidatos | Cuenta propia en esta base. No son usuarios de RENASER OS |
| Permisos del modulo | Nuestros. Su sistema no conoce acciones como «publicar una version del banco» |
| Archivos y entregables | Almacen propio, con enlaces firmados de corta duracion |
| Tareas, tiempos, bloqueos, retrabajo | Se leen de su API. Alimentan las metricas de la validacion |
| Objetivos y resultados del personal | Se leen de su API. Es la fuente del seguimiento 30/90/180 |

## Donde estamos (18/08/2026)
**El embudo esta completo de punta a punta**, sin ningun salto manual obligado: postulacion →
Perfil Integral → prueba del puesto → simulacion → validacion → decision.

| Hito | Estado |
|---|---|
| **1** · Solicitud, vacante, postulacion, estados, permisos | Completo |
| **2** · Perfil Integral (curriculum, evaluacion, Perfil de Talento) | Completo, **con IA corriendo de verdad** |
| **3** · Prueba del puesto y decision | Completo, **con IA corriendo de verdad** |
| **Simulacion y validacion** (estaban fuera del MVP) | Construidas |

| | |
|---|---|
| Base de datos | **Flyway es el dueno del esquema**: V1-V48 en `src/main/resources/db/migration`, **102 tablas de este modulo** (105 en la base contando `flyway_schema_history` y las dos del motor de agentes, `agent_run` y `vector_store`). De la V41 a la V48 nacen **dos tablas** (`ficha_vacante`, V42, y `ubigeo`, V47); el resto son columnas y datos —la V48 solo reetiqueta filas—, asi que el conteo de arriba se quedo en la V40 y hay que recontarlo. `ddl-auto: validate` |
| Codigo | Veintiun paquetes de dominio bajo `ai_engine` (ver «Como esta organizado el codigo») |
| Tests | **1150 en verde (02/09/2026)**, `./mvnw verify` completo: 1007 unitarios y 143 de integracion (de esos 143, 6 se saltan: los que gastan saldo o mandan correo de verdad). Ver «Correr los tests»: unitarios de la maquina y del anonimizador, migraciones contra Postgres real (Testcontainers) y flujos de punta a punta: hito 1, evaluacion, prueba, simulacion/validacion, calificacion con IA, la vacante sin banco, las areas, las dos empresas y el tope de IA de la plataforma |
| IA que califica | **Seis de los diez agentes del catalogo estan implementados**: datos del curriculum, evidencia, evaluador, potencial/riesgo, prueba del puesto y las preguntas de la conversacion final. Todos corren por cola de RabbitMQ contra DeepSeek. Ver [Calificacion con IA](docs/CALIFICACION-CON-IA.md) |
| Correo | **Ya sabe salir de verdad** (`EnviadorCorreoSmtp`), pero **por defecto no sale**: `renaser.correo.transporte` vale `log` salvo que se ponga `smtp`, y entonces solo se registra en `correo_enviado`. Para encenderlo hacen falta `CORREO_TRANSPORTE=smtp`, `CORREO_REMITENTE` y una cuenta que autentique. El RNF-65 pide que salga **desde el dominio propio de Renaser**: confirmar con ellos si ya lo tienen antes de encenderlo |
| Acceso del candidato | Entra al portal **con un enlace, sin contrasena** (`EnlaceAcceso`, `ServicioEnlaceAcceso`). El enlace se le manda por correo, asi que depende de lo de arriba |
| Lo configurable | El plazo para responder la evaluacion dejo de estar en el codigo: es el parametro `dias_plazo_evaluacion` (14 por defecto, migracion `V22`). Se cuenta desde que sale la invitacion, no desde que se creo la evaluacion |
| Identidad equipo | **Correo y contrasena** (`POST /panel/auth/login`), cuentas solo por invitacion; RENASER OS quedo dormido y el `dev-login` esta **apagado por defecto** (lo encienden `application-local.yaml` y las pruebas). Ver «Multiempresa» |
| Frontend | **Dos, y hacen cosas distintas.** Ver «Las dos pantallas» mas abajo |
| Mockups | Los mantiene otra persona y **describen la version anterior** |

**Lo que falta de codigo:** de los diez agentes del catalogo quedan cuatro sin clase, y
ninguno es del MVP: `NECESIDAD_TALENTO`, `CAZATALENTOS`, `DESEMPENO` y `APRENDIZAJE`.

Los dos del hito 3 ya corren (20/08/2026), y los dos se **piden a mano** desde el panel, igual
que la criba de curriculums: cada llamada al modelo cuesta dinero y a quien se califica lo
decide quien lleva la vacante.

| Agente | Que hace | Por donde se pide |
|---|---|---|
| `PRUEBA_PUESTO` | Puntua los criterios de la rubrica **marcados como `AGENTE`**; los de `PERSONA` los sigue poniendo alguien. Al terminar mueve la postulacion a `PRUEBA_POR_CONFIRMAR` y, **si la rubrica quedo entera, deja tambien la nota de la etapa** (28/08/2026, ver abajo) | `POST /api/v1/panel/postulaciones/{id}/prueba/calificacion-ia` |
| `SIMULACION` | Escribe entre tres y cinco preguntas para la conversacion final, sacadas de las contradicciones entre lo que dijo y lo que se le vio hacer. **No puntua nada** | `POST /api/v1/panel/postulaciones/{id}/conversacion-final/generar` |

Dos cosas que no son obvias:

- **Lo que no se puede leer no se puntua.** Una entrega en video, en imagen o en un enlace no
  da texto, y esos criterios se quedan sin nota para que los mire una persona. Es a proposito:
  un modelo al que se le exige una nota siempre da una nota, y despues nadie distingue la
  fundada de la inventada.
- **Una pregunta ya contestada no se borra.** Volver a pedir las preguntas rehace solo las que
  nadie llego a hacer.

### El panel ve lo que el candidato entrego (02/09/2026 · V48)

`GET /panel/postulaciones/{id}/prueba/entregables`. Hasta hoy los entregables los leian **dos**:
el propio candidato en su portal, y el agente al armar su insumo. Quien tiene que poner a mano la
nota de un criterio —los que la rubrica reserva a una `PERSONA`, que son justo los que el modelo
no puede leer: un video, un enlace— **no tenia donde ver el video**. Se le pedia un puntaje sobre
algo que ninguna pantalla le enseñaba.

- **Salen todos los pedidos de su version, entregados o no**, y de cada uno **la ultima**: se
  puede entregar tres veces antes de que cierre el plazo. Un hueco en la lista no se lee; se lee
  una lista mas corta, que parece completa, y que falte un obligatorio es justo lo que hay que ver
  antes de poner una nota.
- ⚠️ **Son DOS permisos, no uno.** Listar pide `abrir_ficha_candidato`; el `enlace` y el
  `archivoId` viajan **solo con `descargar_entregables`**. El archivo ya lo pedia —las dos rutas
  de `/archivos` lo exigen—, y **el enlace tenia que pedir lo mismo**: en la prueba de marketing
  la sustentacion en video se entrega pegando una direccion, asi que repartirla con el permiso
  flojo abre por la puerta de al lado lo que la otra cierra. Sin ese permiso se sigue diciendo
  QUE entrego y CUANDO, y `porQueNoSeVe` explica el hueco con palabras: un hueco callado y un «no
  entrego» se leen igual y no son lo mismo.
- **Con el cuestionario tecnico devuelve lista vacia, no 404.** Esa modalidad se contesta
  escribiendo y no tiene nada que subir; ni siquiera se busca el intento. Lo mismo que hace
  `laRubricaDe`.
- **Repite las tres guardas de `PuentePruebaIaImpl.loQueEntrego`** —archivo borrado, sin ruta, o
  que ya no existe—. Un archivo que no esta se dice; no se ofrece una descarga que va a contestar
  404.
- `ServicioCalificacionPruebaImpl#pintarEntrega` entra en la lista `LLAMADAS_SIN_DUENO_ACORDADAS`
  de `ArquitecturaTest`: se llega desde `verEntregables`, que ya paso por `laVisible`, y desde ahi
  solo se leen archivos que cuelgan de un `entregable` del intento de ESA postulacion. El dueno lo
  fija la cadena, no el `findById`.

#### ⚠️ La V48: el archivo del entregable era de la plataforma, no de la empresa

`subirEntregableArchivo` sellaba el archivo con la organizacion de **quien lo sube**, y quien lo
sube es el candidato: todas las cuentas del portal nacen en la organizacion plataforma. El panel,
en cambio, lo busca con `findByIdAndOrganizacionId` usando la organizacion **de la empresa de la
vacante** (`ServicioPostulacionesPanelImpl.elVisible`). Para cualquier empresa que no sea la
plataforma, abrir o descargar un entregable respondia **404 sobre un archivo que el candidato si
subio**.

El curriculum nunca lo tuvo: `ServicioPostulacionPortalImpl` sella con la organizacion de la
vacante, y lo dice en un comentario. Al escribir la subida de entregables se perdio esa regla.

⚠️ **Es la fuga que solo aparece con la segunda empresa.** RENASER es a la vez la plataforma y la
unica empresa con vacantes, asi que las dos organizaciones son la misma y los dos caminos
coinciden por casualidad. **La pantalla de entregables del panel es la primera que la habria
destapado**, y llega en esta misma rama.

⚠️ **Su test usa DOS organizaciones distintas a proposito** (`ServicioPruebaImplTest`). Con la
misma en los dos lados pasaria en verde con el fallo dentro, que es exactamente lo que llevaba
pasando desde que se escribio la subida.

La migracion **reetiqueta solo los archivos que cuelgan de un `entregable`, y solo cuando
difieren**: no toca los curriculums, que ya estan bien, ni los materiales de las plantillas de
prueba, que son de la empresa que escribio la plantilla y no de ninguna vacante. Es idempotente y
**no añade ninguna tabla**.

#### ⚠️ El enlace firmado no falla en local: contesta 200 con una url que nadie abre

`AlmacenArchivosEnMemoria.urlDeDescarga` devuelve `Optional.of("memoria://" + ruta)`, asi que
`GET /archivos/{id}/enlace` responde **200**. El `IllegalStateException` de
`enlaceDeArchivo` —«el almacen de archivos de este entorno no reparte enlaces»— **no se dispara
nunca con el perfil `local`**, que es el de por defecto. Quien llame a ese endpoint tiene que
mirar **el esquema de la url** y caer a `/archivos/{id}/descarga` cuando no sea `http`/`https`;
esperar un error es esperar algo que no llega. `application-local.yaml` ya lo decia en el bloque
de `app.archivos`, y ahora lo dice tambien `docs/09-APIS.md` en las dos filas.

⚠️ **Y por eso el camino del bucket queda sin comprobar de punta a punta.** Lo verificado es la
descarga, que es la unica que existe en local; que el enlace firmado sirva bytes de verdad se
afirma por construccion. Un fallo en la firma de Supabase pasaria por aqui sin que nadie se
entere.

⚠️ **Pendiente: con formato `CUALQUIERA` se quedan dos entregas y solo viaja una.** Quien pega un
enlace y luego sube un archivo deja **dos filas del mismo pedido**, y de cada pedido sale la
ultima version: la anterior no sale por ningun sitio, ni en el panel ni para el agente.
Arreglarlo pide mandar el historial del pedido, no su ultima fila.

### Los minutos de la vacante rigen los DOS instrumentos (31/08/2026)

`vacante.minutos_etapa_tecnica` existe desde la V43, pero **cuando la vacante rendia la prueba
del puesto era un no-op**: se guardaba, se auditaba, el panel lo enseñaba y **no lo leia nadie**.
Ahora lo leen los dos instrumentos de la etapa: el cuestionario tecnico y la prueba del puesto.

- ⚠️ **Se resuelven AL EMPEZAR el examen, no al crearlo.** Es la decision que hace todo lo demas:
  corregir el numero en la ficha alcanza a **todo el que aun no haya abierto el suyo**, tenga ya
  su examen creado o no. Congelarlo al crear era lo que hacia el cuestionario tecnico y dejaba a
  media tanda con el valor viejo sin que nadie pudiera verlo desde el panel.
- ⚠️ **Gana el plazo mas cercano, y solo un cronometro puede acercarlo.** Entre una fecha de
  cierre ya fijada y `ahora + minutos`, manda el que caiga antes. **Los dias de un plazo abierto
  NO acercan nada**: la fecha de cierre existe para decir «esta convocatoria cierra el 30» a
  todos a la vez (V32), y compararla contra los dias del plazo se la recortaria a quien abriera
  pronto —el dia 2 se le cerraria el 9— devolviendo los vencimientos distintos por candidato que
  la V32 vino a eliminar. **Fue una regresion, la encontro un QA y esta arreglada**; el caso vive
  en `RelojDeLaEtapaTecnicaQaTest`. `plazoPropio` tampoco se toca.
- **Con minutos de vacante la prueba es cronometrada de hecho**, diga lo que diga la plantilla, y
  eso es lo que se le enseña al candidato. La fila de la plantilla no se toca: la misma version
  la puede rendir otra vacante que no fije nada. Y viajan a la pantalla **los dos datos** —los
  minutos y la fecha— siempre que existan los dos: elegir uno y callar el otro era el fallo.
- **Se retiro la validacion de 60-120 minutos** al publicar una version de prueba. Lo decidio
  Renaser: manda el tiempo que ponga la empresa. Queda un **piso de 5 minutos**, y ahora hace mas
  falta que antes — cualquier prueba puede volverse cronometrada, y con un minuto el barrido la
  entrega sola antes de que la pantalla acabe de cargar. Cinco es el mismo numero en los dos
  sitios: publicar la version, y fijar los minutos de la vacante.
- **La guarda de «la misma vara para todos» corrio su linea**: de «hay alguna postulacion» a
  **«alguien ya EMPEZO su etapa tecnica»**. Postular no es rendir, y quien no ha abierto nada no
  ha visto ningun enunciado ni ningun reloj. Con la linea vieja, un solo curriculum dentro dejaba
  la vacante congelada hasta la siguiente convocatoria. ⚠️ Se preguntan **los dos** instrumentos,
  no el que la vacante tenga puesto: una vacante que ya cambio antes puede tener gente que
  empezo con el otro.
- ⚠️ **`evaluacion.minutos_objetivo` es desde hoy un vestigio.** Nadie la escribe ni la lee en
  esta etapa. Se conserva por las filas anteriores que la traen llena. **No apoyarse en ella ni
  como respaldo**: vaciar los minutos en la vacante es valido —«usa los del instrumento»— y un
  respaldo asi resolveria al numero congelado en vez de caer al cuestionario.
- ⚠️ **Lo que el rango 60-120 sostenia sin decirlo**: que el cambio inesperado siempre cupiera
  dentro del reloj. Hoy no lo garantiza nada. Ver [Defectos conocidos](docs/DEFECTOS-CONOCIDOS.md).

### Una version de prueba se compone entera antes de publicarla (31/08/2026 · V46)

Una version en borrador solo se podia **añadir**: cada pregunta, entregable, criterio o variante
mal puesto se quedaba ahi, y una rubrica que se pasaba de 100 no habia forma de deshacerla.
Ahora hay **doce endpoints** de correccion y borrado, mas dos que no lo son:

| Que | Ruta |
|---|---|
| Las versiones de una plantilla, borradores incluidos | `GET /plantillas-prueba/{id}/versiones` |
| Subir el ENUNCIADO en PDF o Word | `POST /plantillas-prueba/versiones/{id}/consigna` |
| Reemplazar los datos de la version | `PUT /versiones/{id}` |
| Quitar una pregunta elegida | `DELETE /versiones/{id}/preguntas/{preguntaId}` |
| Corregir y quitar cada pieza | `PUT` y `DELETE` sobre `/entregables/{id}`, `/rubrica/{id}`, `/variantes/{id}` |
| Reordenar, la lista entera de una vez | `PUT /versiones/{id}/{preguntas,entregables,variantes,rubrica}/orden` |

- ⚠️ **Todo esto es SOLO en `BORRADOR`.** Publicada responde 409 y la salida a un error sigue
  siendo una version nueva: no hay «despublicar» (RF-138). Lo que cambia es que **antes** de
  publicar ya no hay operaciones irreversibles.
- ⚠️ **Quitar una pregunta borra la ELECCION, no la pregunta.** `pregunta_prueba` es un catalogo
  que varias versiones eligen a la vez, y hay respuestas apuntandola por clave ajena.
- **Estas si se auditan una por una**, aunque los `agregar*` de al lado no lo hagan: lo que se
  añade se ve en la version y se puede quitar; lo que se quita no deja rastro en ninguna parte.
- **El enunciado subido no exime de nada**: publicar sigue exigiendo preguntas, entregables y
  rubrica, porque de ese archivo no sale ninguna nota. ⚠️ Su enlace **caduca a los 180 dias** y
  solo se puede volver a subir en borrador: ver [Defectos conocidos](docs/DEFECTOS-CONOCIDOS.md).

### La prueba le dice al agente que la califica que mirar (31/08/2026 · V46)

`instruccion_ia` es **una para todo el mundo** —esa tabla no tiene `organizacion_id`— asi que la
instruccion de `PRUEBA_PUESTO` solo puede hablar del oficio de calificar. Quien sabe que
distingue un buen tablero de control de uno malo en el rubro de ESTA empresa es quien escribio la
prueba. Ahora lo puede escribir: `version_plantilla_prueba.guia_calificacion`, texto libre de
hasta 2000 caracteres.

- **Vive en la VERSION**, no en la organizacion ni en la vacante, por lo mismo que la rubrica: se
  congela al publicar y viaja con la copia entre empresas. Una prueba publicada califica igual
  hoy que dentro de un año, y eso incluye con que guia.
- ⚠️ **Orienta, no sustituye.** La rubrica sigue siendo la unica fuente de los 100 y el agente
  sigue devolviendo nota **por criterio**. Un texto que pida «califica sobre 100» no tiene donde
  escribirse: `PuentePruebaIaImpl.guardarNotasPrueba` descarta codigos que no esten en la rubrica,
  los reservados a una persona y la nota sin explicacion, y acota el puntaje al maximo. **Esa es
  la red de verdad**, no el envoltorio del prompt.
- ⚠️ **Va en el `system`, cerrada con un rotulo que lleva una marca sorteada en cada
  calificacion.** Quien escribe la guia —dias antes, sin verla nunca— no puede reproducir el
  rotulo, asi que no puede fingir el cierre y escribir detras haciendolo pasar por instruccion
  del sistema. **Sustituye al saneado de rayas**, que no servia de dos maneras: solo miraba el
  guion ASCII, y **degradaba en vez de borrar** —`--- FIN ---` quedaba en `-- FIN --`, el mismo
  rotulo con las mismas palabras—. Enumerar caracteres es una carrera que se pierde.
- ⚠️ **Y ya no viaja tambien cruda en el insumo.** El insumo se serializa como el mensaje del
  usuario: dejarla ahi mandaba el texto **dos veces**, una envuelta y otra entera y sin tocar,
  con lo que todo el envoltorio del `system` era decorativo. Lo demostro un QA leyendo el
  volcado. No se pierde el rastro: `EjecucionIa.envio` guarda el `system` entero, guia incluida.
- **Los rotulos de la bitacora llevan la misma clase de marca** (`EjecutorAgenteIaImpl`). No
  protegen al modelo —nunca se le mandan—: protegen a quien abre el registro meses despues, para
  que un texto de usuario no pueda inventarse secciones dentro de el.
- **El tope de 2000 esta en cuatro sitios y ninguno sobra**: el `@Size` del contrato, el CHECK de
  la V46 (el unico en los caminos que no pasan por el DTO —la copia entre empresas y las cargas
  por SQL—), el mensaje entendible del servicio, y el recorte al armar el prompt.

⚠️ **Fuga conocida y abierta**: el catalogo `pregunta_prueba` se lee **sin filtrar por empresa** y
las preguntas especificas SON el texto del examen. Ver
[Defectos conocidos](docs/DEFECTOS-CONOCIDOS.md).

### Las areas se administran desde el panel (31/08/2026 · sin migracion)

Las areas son la estructura de la empresa y **sin una no se puede registrar una Solicitud de
Talento**, pero solo se podian listar y crear: no habia como renombrarlas, retirarlas ni
borrarlas. Ahora el CRUD esta entero en `AdministracionController` y en
`ServicioAdministracionImpl`. **No hizo falta ninguna migracion**: `area.es_activa` existe desde
la V2 y solo estaba a medio usar —se escribia `true` al crear y nadie lo volvia a tocar—.

| Verbo y ruta | Permiso |
|---|---|
| `GET /panel/areas` (solo activas, como siempre) | `ver_solicitudes` |
| `GET /panel/areas/todas` | `crear_usuarios_y_asignar_roles` |
| `POST /panel/areas` | `crear_usuarios_y_asignar_roles` |
| `PUT /panel/areas/{id}` | `crear_usuarios_y_asignar_roles` |
| `POST /panel/areas/{id}/desactivacion` · `/reactivacion` | `crear_usuarios_y_asignar_roles` |
| `GET /panel/areas/{id}/impacto` | `crear_usuarios_y_asignar_roles` |
| `POST /panel/areas/{id}/borrado` | `crear_usuarios_y_asignar_roles` |

- ⚠️ **`GET /areas` no cambio, y no debe cambiar.** Sigue filtrando por activa porque es la
  lista que llena el desplegable de la solicitud: una area retirada ahi seria una solicitud
  nueva colgada de algo que la empresa dio por cerrado. La lista con las retiradas es **una
  ruta aparte** y no un parametro de la primera, para que ese contrato no se pueda aflojar por
  descuido. Sin esa segunda ruta, desactivar seria un viaje sin retorno.
- ⚠️ **Borrar exige reasignar, y esto sale de las claves ajenas.** `solicitud_talento.area_id`
  (NOT NULL) y `usuario.area_id` (admite NULL) apuntan a `area(id)` y **ninguna declara
  `ON DELETE`**: Postgres aplica NO ACTION y el DELETE falla mientras quede una fila, tambien
  contra la que admite nulo. `borrarArea` mueve primero las dos cosas al area de destino y borra
  despues, todo en una transaccion. Sin destino solo se admite si el area esta vacia, y si no lo
  esta responde **409 con los dos recuentos dentro**, nunca un error crudo de clave ajena.
  Vaciar `usuario.area_id` esta descartado a proposito: contenta a Postgres y pierde el dato.
- ⚠️ **La ultima area activa no se puede retirar.** Registrar una Solicitud de Talento exige un
  area, y la solicitud es el paso previo a cualquier vacante: sin ninguna activa, la empresa no
  puede volver a contratar y no hay pantalla que lo explique.
- ⚠️ **Un area es de una empresa y solo de una, y las claves ajenas no lo comprueban.** Solo
  exigen que el area exista. Dar de alta a alguien (`crearUsuario`) o registrar una solicitud
  (`ServicioSolicitudesImpl`) contra un area ajena daba un 201 y dejaba una fila **invisible para
  todo lo que consulta por organizacion** —incluido el recuento que decide si un area se puede
  borrar—. Las dos puertas lo comprueban ahora y responden «no existe».
- ⚠️ **Los tres `flush()` de `borrarArea` NO son lo que salva el borrado**, y conviene saberlo
  antes de apoyarse en ellos: se probo quitandolos y `FlujoAreasIT` siguio en verde, porque
  Hibernate ya vuelca los UPDATE antes que los DELETE. Quien impide el error de clave ajena es
  la guarda que rechaza el borrado mientras quede algo por mover. Se quedan por explicitud y
  porque el margen es estrecho —un `@Modifying` en lote, o un insert en la mezcla (ahi Hibernate
  SI inserta antes de actualizar), cambian el orden—, no como red.
- **Los permisos se quedan como estaban a falta de una migracion.** Un `administrar_areas`
  propio seria mas fino, pero el catalogo de permisos solo crece con una migracion y los
  numeros estan repartidos a mano entre ramas. Escribir es `crear_usuarios_y_asignar_roles`,
  que es con lo que ya se administra el resto de la estructura del equipo —usuarios, roles e
  invitaciones—. Si algun dia se anade el permiso propio, el unico sitio a tocar es la seccion
  de areas del controlador.
- Auditoria en las cinco escrituras (`crear_area`, `renombrar_area`, `desactivar_area`,
  `reactivar_area`, `borrar_area`). La del borrado lleva el nombre del area y los dos
  recuentos porque **es lo unico que sobrevive**: despues del DELETE nada permite reconstruir
  de donde venian esas solicitudes. Renombrar al mismo nombre, o apagar lo ya apagado, no
  escriben nada.
- El nombre repetido se rechaza antes de llegar al `UNIQUE (organizacion_id, nombre)` de la V2,
  y la comprobacion es **sensible a mayusculas igual que la restriccion**: una `IgnoreCase`
  seria mas estricta que la base y rechazaria nombres que si caben.
- Pruebas, en dos capas. `AreasDeLaOrganizacionTest` (19 unitarias): con dobles no hay clave
  ajena que reventar, asi que comprueban que el guardian corta antes (`never().delete(...)`),
  que es lo que hace que nadie vea el error crudo. Y **`FlujoAreasIT` (10 casos, Postgres real)**
  es la red de debajo: ademas del 409 con sus recuentos y del borrado con reasignacion de punta
  a punta, **borra un area por SQL directo saltandose el servicio entero** y exige que la base se
  niegue por las DOS claves ajenas. Ese caso es el que avisaria si algun dia alguien relaja esas
  restricciones y el borrado deja solicitudes huerfanas.

### La prueba queda con nota en cuanto su rubrica esta entera (28/08/2026)

El agente guardaba la nota de **cada criterio**, y la de la **etapa** —la suma ponderada, la que
sale en el ranking y con la que se ordena— **no se calculaba nunca**. En la base de produccion
eso dejo **19 pruebas corregidas sin nota de etapa**, que en el panel se ven igual que las que
nadie ha corregido.

Ahora `ponderarSiLaRubricaEstaEntera` suma **solo si todos los criterios tienen puntaje**; si
falta alguno no suma y **nombra cual** en el registro. La decision vieja no se revierte: casi
ninguna rubrica esta completa cuando el modelo acaba —los de metodo `PERSONA` siguen vacios por
diseno— y media rubrica da un numero bajo que parece un juicio y es un hueco. Lo que cambia es
que «casi ninguna» no es «ninguna»: una rubrica de puros criterios de agente queda entera en
cuanto el modelo termina.

⚠️ **Se comprueba antes de sumar, no se intenta y se atrapa.** `calcularNotaEtapa` lanza cuando
falta un criterio, y esto corre dentro de la transaccion que acaba de guardar las notas del
modelo: dejar salir esa excepcion marcaria rollback y **se perderian esas notas**.

⚠️ **Un cero es una nota puesta, y una fila de nota sin puntaje cuenta como que falta.** La
guarda nueva es mas estricta que la del propio `calcularNotaEtapa`, que solo mira si la fila
existe: con el `puntaje` en nulo su suma reventaria.

⚠️ **Hay una carrera conocida y se reporta, no se arregla.** El agente y el boton del panel
(`POST /postulaciones/{id}/prueba/calificacion`) pueden insertar la nota a la vez; uno choca con
la clave unica y su transaccion cae con las notas dentro. **Se cura sola**: el trabajo se
republica y el reintento las vuelve a guardar. Es una ventana de milisegundos y arreglarla cuesta
mas de lo que evita.

⚠️ **La rama que suma no la guardaba ningun test, y por eso hay uno de integracion.** Los 39
tests que ya existian del puente nunca preparaban `notasCriterio.findByPostulacionId`, asi que el
doble devolvia lista vacia y **todos** iban por la rama de «la rubrica no esta entera»; y los
cinco unitarios nuevos llaman al metodo por reflexion, asi que seguirian verdes aunque nadie lo
llamara. `FlujoPruebaIT` (`@Order(8)`) lo comprueba contra la base de verdad. **No lo borres
pensando que los unitarios lo cubren.**

**Esto arregla las pruebas que se corrijan de aqui en adelante.** Las 19 ya atascadas siguen
necesitando que alguien las pondere una vez desde el panel.

**Lo que falta y no es codigo** — son decisiones de Renaser que los documentos defieren a
proposito, no trabajo olvidado:

| Que falta | Que bloquea |
|---|---|
| Un **facilitador designado** para la simulacion | La persona que observa la sesion presencial y marca los eventos. El sistema ya sabe que *roles* pueden hacerlo, y es cambiable desde el panel sin desplegar |
| La **matriz de informacion critica** de una sesion real | La lista de datos que no se le dan al candidato salvo que pregunte. La mecanica esta construida; el contenido lo escribe Renaser |
| La **figura contractual** de la validacion productiva | El requisito dice literalmente «la figura contractual aprobada por Renaser». Hoy hay un placeholder: `CONTRATO_TEMPORAL_EN_REVISION` |
| El **contenido real de una prueba del puesto** | Renaser tiene que reescribir una pensada para rendirse de una sentada — **y decidir cuantos minutos**, desde que el rango 60-120 se retiro (31/08/2026) |

Otros placeholders puestos a proposito: los **umbrales de los grupos de prioridad (80 y 65)** son
parametro editable y Renaser no los ha confirmado.

**El alcance de una fila lo decide un solo sitio**: `AlcanceSobreLaVacante` (paquete `vacante`).
Antes «con `SUS_VACANTES` solo alcanzas lo de tus vacantes» estaba escrito catorce veces en
nueve clases de siete modulos, y doce de ellas **dejaban pasar `PROPIO`** —solo probaban «es
`SUS_VACANTES`»—, o sea acceso completo justo para quien menos alcance tiene. Con la V40 eso
dejo de ser inalcanzable: `administrar_permisos` acepta los tres valores por `PUT`, sin
desplegar. Hoy el guardian trata los tres casos con un `switch` sobre el enum, para que un
cuarto valor rompa la compilacion en vez de colarse.

⚠️ Lo que queda de aquello: `FiltroAlcance.responsableOFiltroNulo()` sigue devolviendo nulo con
`PROPIO` igual que con `TODO`. Su unico uso —la bandeja, en `ServicioPostulacionesPanelImpl`—
ya lo trata aparte con `noAlcanzaANadieEnElPanel()`, y el javadoc del metodo avisa. Quien lo
use en un sitio nuevo tiene que hacer lo mismo.

**Los agentes de la calificacion ya NO corren en fila** (20/08/2026): los tres que no
dependen de nadie —datos del CV, evidencia y evaluador— van a la vez, y el que arma el
Perfil de Talento espera a los tres. Si uno falla, los demas siguen: antes un curriculum
escaneado dejaba sin calificar un examen de 50 preguntas, para siempre.

**El aviso de que la postulacion avanza lleva un enlace de acceso** (V26). Antes decia
«entra a tu panel» sin dar ni la direccion ni la forma, y los candidatos cargados desde
una carpeta de curriculums no tienen contrasena: recibian un correo que no podian atender.

⚠️ **El correo exige `starttls` y va en `application.yaml`.** Sin eso Gmail rechaza todo
con «530-5.7.0 Must issue a STARTTLS command first», que no nombra ni el TLS ni la
propiedad que falta.

Conocido y sin arreglar: **una ruta que no existe devuelve 500, no 404.** Se ve con cualquier
direccion inventada:

```
GET /no-existe  ->  500
{"detail":"Ha ocurrido un error inesperado...",
 "type":"https://api.motoragentes.com/errors/internal"}
```

Ese `motoragentes.com` delata la causa: no lo contesta nuestro `ManejadorErrores`, sino el
`@ExceptionHandler(Exception.class)` del motor de agentes, que atrapa lo que cae fuera de
nuestros controladores. El commit «una direccion que no existe es un 404, no una averia del
sistema» arreglo los casos que pasan por los nuestros; este no.

**Por que importa mas de lo que parece.** Desde que el backend esta en una IP publica lo
escanean bots todo el rato —se vieron intentos contra `/trace.axd`, `/.vscode/sftp.json` y
`/?rest_route=/wp/v2/users/` a los pocos minutos de levantarlo—. Cada uno deja un 500 en el
registro, asi que un 500 **de verdad** se pierde entre el ruido. Y un 500 le dice a quien
escanea que ahi hay algo que revienta, en vez de un 404 aburrido.

**Como se arregla**: un `@ExceptionHandler(NoResourceFoundException.class)` en
`comun/exception/ManejadorErrores` que devuelva 404, y que se registre antes que el generico
del otro paquete.

### La vacante sin banco de preguntas (22/08/2026)
Renaser pidio que en la vacante de **Administrador** el cuestionario tecnico de 20 preguntas
valga por las dos etapas de preguntas: se carga como prueba del puesto, lo califica el agente
`PRUEBA_PUESTO`, y el banco queda apagado **solo en esa vacante**.

⚠️ **La convocatoria esta EN MARCHA.** El 23/08/2026 salieron 93 invitaciones a la prueba
(vacante 14 en el servidor de pruebas), con cierre el **lunes 24 a las 23:59 de Lima**.
Quedaron fuera once: tres CV cargados dos veces, tres con el correo mal escrito en el propio
curriculum, y cinco sin contacto legible —a esos hay que llamarlos—. Antes de tocar nada de
esta vacante, mirar en que estado esta la gente.

Lo que existe para eso:

- `vacante.aplica_evaluacion` (V30). Apagado, quien postula **no recibe evaluacion** y cae
  directo en `PERFIL_POR_CONFIRMAR`: el equipo criba los curriculums y confirma el avance,
  que crea el intento y manda el correo con el enlace de acceso.
- Endpoints nuevos: `POST /panel/vacantes/{id}/aplicacion-evaluacion` y
  `POST /panel/vacantes/{id}/version-pesos` (los pesos de UNA vacante; no recalcula nada).
- **Una version de prueba sin entregables es un cuestionario**: al publicarla no rige la cuota
  de 8-10 universales y 3-5 especificas, basta una pregunta. Con entregables la cuota sigue.
- **La fecha de cierre de la prueba se fija en dos niveles** (V32). El plazo de la plantilla
  se cuenta en dias y desde que cada uno empieza, asi que no servia para decir «esta
  convocatoria cierra el domingo».
  - **Por vacante**: `POST /panel/vacantes/{id}/cierre-prueba`. Ademas de fijarla, **mueve
    los intentos ya abiertos**; si no, valdria solo para quien entrara despues y la tanda
    quedaria partida en dos.
  - **Por candidato**: `POST /panel/postulaciones/{id}/prueba/plazo`, para dar mas horas a
    quien las pide. Marca `intento_prueba.plazo_propio`, y **a esos no les afecta mover la
    fecha de la vacante**: perder esa excepcion habria sido silencioso.
  - Empezar la prueba no recalcula una fecha ya puesta.
- **El correo de la prueba es el de Ricardo** (`PRUEBA_DISPONIBLE`, migracion V29): lleva el
  PDF del enunciado por vacante, el plazo segun la modalidad y el WhatsApp como parametro.
- **Cada vacante puede elegir sus textos de correo** (V31, tabla `plantilla_correo_vacante`).
  Una fila dice «para esta vacante, donde ibas a mandar el aviso X manda la plantilla Y»;
  sin fila, sale el de siempre. Vale para cualquier aviso, no solo el de la prueba. Antes una
  plantilla era una por organizacion y cambiar el texto de una convocatoria se lo cambiaba a
  todas — que es lo que obligaba a publicar, mandar y restaurar a mano.
  Se configura con `POST /panel/vacantes/{id}/plantillas-correo`.
- El guion `scripts/cargar-prueba-administrador.py` deja todo montado por la API: preguntas
  ADMIN_Q01..Q20, plantilla publicada con rubrica 100% de agente, pesos "la prueba vale todo"
  asignados solo a esa vacante (y re-publica el reparto estandar para que las vacantes nuevas
  no hereden el especial), el texto del correo de invitacion a la prueba, y el banco apagado.
  Invitar en lote: `scripts/invitar.py --a prueba` (el correo sale del backend, con la cuenta
  de la empresa que ya esta en sus variables de entorno). Admite `--saltar 1,2,3` para dejar
  fuera a alguien concreto: un CV cargado dos veces es la misma persona y recibiria dos
  enlaces, y un correo mal escrito en el curriculum solo sirve para que rebote.
- Calificar en lote: `scripts/calificar-pruebas.py --vacante N --de-verdad`. Pide la
  calificacion de todas las pruebas **entregadas** y enseña la tabla de notas al terminar.
  Sin `--de-verdad` solo dice a quien se le pediria: cada una cuesta una llamada al modelo.
  `--cuantos N` limita la tanda, que sirve para probar con una antes de lanzar noventa.

El flujo entero esta cubierto por `FlujoSinBancoIT`.

**Un cuestionario sin entregables SI se puede calificar** (comprobado el 23/08/2026 contra
DeepSeek de verdad): el agente puntuo los siete criterios leyendo solo las respuestas, sin
ningun archivo que mirar, y con notas bajas y justificadas donde el texto no decia nada. La
duda era razonable —su instruccion habla de «la entrega»— pero no se cumplio.

### La criba de curriculums (18/08/2026)
Contesta «llegaron cien curriculums, a quien invito primero». Antes la IA solo arrancaba
cuando el candidato entregaba su evaluacion, asi que con una carpeta recien llegada no habia
forma de pedirla. Cuatro endpoints en `PostulacionesPanelController`:

| Para que | Llamada |
|---|---|
| La tanda entera, ordenada de mas apto a menos | `GET /api/v1/panel/vacantes/{id}/ranking?etapa=` · sin `etapa`, la nota de la preseleccion |
| Primera pasada sobre todos los que no tienen nota | `POST /api/v1/panel/vacantes/{id}/criba-rapida` |
| Segunda pasada, solo sobre la parte alta | `POST /api/v1/panel/vacantes/{id}/criba-fina` |
| Releer un curriculum suelto | `POST /api/v1/panel/postulaciones/{id}/criba-cv` |

Dos cosas que no son obvias y cuestan dinero si se ignoran:

- **La primera pasada usa el modelo rapido y no razona.** Sirve para ordenar, no para decidir.
  Su nota viaja marcada como `RAPIDA` y es provisional.
- **La segunda pasada se niega a correr si todavia no hay ninguna nota**, porque «los de
  arriba» no existirian y elegiria por orden alfabetico. Cuanta parte entra lo dice el
  parametro `porcentaje_criba_fina` (50 por defecto).

Un agente nuevo, `AgenteDatosCv`, saca del curriculum quien es la persona (nombre, ultimo
puesto, anos de experiencia) y lo guarda en `dato_cv` (migracion `V19`). **No puntua**: por eso
puede leer datos que los agentes que califican no ven.

**Un PDF escaneado no se puede leer.** Sin texto no hay nada que mandar al modelo, y el sistema
lo deja fallido en vez de inventar una nota. Los CVs de las semillas son de ese tipo, asi que
en una base recien sembrada la criba los marca como fallidos: es correcto, no es un fallo.

Ver [La criba de curriculums](docs/CRIBA-DE-CURRICULUMS.md).

### El ranking por etapa y el desglose de la evaluacion (25/08/2026)

Dos endpoints mas en `PostulacionesPanelController`:

| Para que | Llamada |
|---|---|
| Ordenar la tanda por la nota de otra etapa | `GET /panel/vacantes/{id}/ranking?etapa=PRUEBA_PUESTO` |
| Abrir la evaluacion del banco por dentro | `GET /panel/postulaciones/{id}/evaluacion` |

- **`?etapa=` cambia solo con que nota se ordena.** Las ocho notas del curriculum de cada fila
  siguen siendo las del Perfil Integral, porque son de esa etapa siempre. Los codigos salen del
  catalogo `etapa`; uno que no este da 400. Quien no tenga nota en esa etapa sale al final, sin
  heredar la de otra.
- **Es una sobrecarga, no un cambio de firma, a proposito**: `cribaFina()` decide a quien
  recalificar por la nota de preseleccion y tenia que seguir haciendolo. Sin el parametro el
  camino es el de antes, sin una consulta de mas.
- **El desglose es de solo lectura y nunca da 404.** Sin evaluacion asignada devuelve vacios: una
  vacante publicada con el banco apagado es un caso normal. Lo guarda
  `ver_respuestas_evaluacion`, que estaba en el catalogo desde V12 y **no lo comprobaba nadie**;
  este es el primer endpoint que lo usa.
- ⚠️ **`alineacion` sale vacia siempre**: la tabla `resultado_alineacion` existe, el panel ya la
  lee y **ningun agente la escribe todavia**. Vacia significa «no calculado», no «todo en verde».
- **La cuenta de la nota de la evaluacion vive en un solo sitio**,
  `ServicioCalificacion.notaCombinada`. Estaba copiada en `PuenteCalificacionIaImpl`. Como
  mezclar lo cerrado con lo abierto **es una interpretacion sin confirmar con el cliente**: el dia
  que cambie tiene que cambiar a la vez para la nota de la etapa y para lo que ve el panel. No
  volver a copiarla.

Dos guiones para mirar el panel con datos y no con tablas vacias. **Escriben en la base local** y
se pueden relanzar:

| Guion | Que hace |
|---|---|
| `scripts/sembrar-evaluacion-local.py` | Postula, responde los ocho formatos y entrega, y deja que la IA califique. Cada abierta se responde **segun lo que pide su enunciado**: contestar lo mismo a todo daba ceros, y con razon |
| `scripts/escenario-etapas-local.py` | Reparte candidatos por las cinco etapas calificando a mano por el camino, para que ninguna pestaña del panel se vea vacia |

### Las dos pantallas
Ninguna vive en este repositorio: el `frontend/` de demostracion que habia aqui se borro a proposito (2e7b176) cuando el panel de verdad quedo en RENASER OS.

| | Donde | Que hace | Como se levanta |
|---|---|---|---|
| `RenaserTalentoFrontend` | `~/Documentos/RenaserTalentoFrontend`, fuera de git | Una pagina sola: elegir puesto, **arrastrar curriculums**, lanzar las dos pasadas y ver la tabla. Para ensenar el flujo sin tocar la terminal | `node servidor.js` (3000), sin `npm install` |
| RENASER OS | `/home/n4nd0/Documentos/RenaserOs`, rama `feat/talento` | El panel de verdad: solicitudes, vacantes, bandeja, ficha, embudo y administracion. React con Vite, **no Next.js** como decian los documentos | Proxy de Vite hacia `localhost:8080` |

Las dos consultan el mismo backend, asi que ensenan el mismo orden.

### Reglas que no se negocian al tocar el codigo
- Toda transicion pasa por `MaquinaEstados.transicionar`. **Nunca** tocar
  `postulacion.estado_codigo` a mano.
- El alcance se aplica dentro de las consultas con `Permisos.alcanceDe`. Un 404 tambien
  significa «esto no es tuyo».
- **Que una postulacion o una vacante sea tuya lo decide `AlcanceSobreLaVacante`, y solo el.**
  Un servicio nuevo que necesite acotar por vacante lo inyecta y llama a
  `laPostulacionVisible`, `laVacanteVisible` o `alcanzaA` — **no** vuelve a escribir el
  `== SUS_VACANTES`. Esa linea estuvo copiada catorce veces y doce se dejaban `PROPIO` sin
  tratar; la version del guardian es un `switch` sobre el enum, asi que un alcance nuevo rompe
  la compilacion en un sitio en vez de colarse en doce. Lo que **no** pasa por ahi, y no es un
  descuido: la **sesion de simulacion**, que sirve a varias vacantes a la vez y lleva su propia
  regla (`tocaUnaVacanteSuya`, y el conteo con su cuarto caso en `contarInscritos`); y la
  **solicitud de talento**, cuyo dueno es alguien del equipo (`ServicioSolicitudesImpl`).
- El esquema solo cambia con una migracion nueva. **Nunca** editar una ya aplicada.
- **Antes de numerar una migracion, mirar cual es la mas alta que existe** — y no solo en tu
  rama. Ricardo tambien crea migraciones: la de simulacion y validacion tuvo que pasar de `V16`
  a `V18`, y la de los inscritos de `V37` a `V40` porque `main` reclamo ese numero mientras la
  rama estaba abierta. **Git no ve ese choque**: `V40__una.sql` y `V40__otra.sql` son dos
  archivos distintos y los fusiona sin avisar; quien se entera es Flyway al arrancar. Ahora hay
  una prueba que lo caza (`MigracionesSinChoqueTest`), pero cada rama pasa el CI en verde por
  su cuenta: solo salta cuando la segunda se fusiona.
- Nada se recalcula hacia atras: cada nota queda atada a la version de pesos de su vacante.

### Correr los tests
**1007 unitarios en verde (02/09/2026).** Son dos tandas y el comando decide cual corre:

| Comando | Que corre | Cuantos | Necesita |
|---|---|---:|---|
| `./mvnw test` | unitarias con dobles y las de arquitectura | 1007 | nada |
| `./mvnw verify` | lo anterior mas las de integracion | 1007 + 143 | Docker |

⚠️ **El numero es el que Maven imprime al final, no el que sale de sumar los informes.** Con `-q`
esa linea no aparece y hay que ir a `target/*-reports/`, donde los `.txt` y el atributo `tests=`
del `<testsuite>` dan cifras mas bajas: las clases anidadas y las parametrizadas no se cuentan
igual en cada sitio. Correrlo sin `-q` es mas barato que reconstruirlo.

Las de integracion levantan su propio PostgreSQL y su propio RabbitMQ con Testcontainers: hace
falta el demonio de Docker encendido. El desglose completo esta en
[docs/COMPROBACIONES-AUTOMATICAS.md](docs/COMPROBACIONES-AUTOMATICAS.md).

Entre las unitarias hay **9 reglas de arquitectura con ArchUnit** (la frontera del motor, las capas,
que el estado solo lo mueva la maquina, y que ningun servicio del panel busque por id suelto en un
agregado con dueno — las mismas reglas que este archivo tiene en prosa).

**El test que gasta dinero no corre solo**: `CalificacionIaRealIT` esta detras de
`@EnabledIfEnvironmentVariable`, asi que se salta sola si no esta la variable. Ni `test` ni
`verify` llaman a DeepSeek. Para pedirla a proposito:

```
RENASER_IA_REAL=si ./mvnw verify -Dit.test=CalificacionIaRealIT
```

**Ruido esperado que NO es un fallo**: `Failed to check/redeclare auto-delete queue(s)` de
RabbitMQ, y los cinco tests saltados de `CalificacionIaRealIT`. Si termina en `BUILD SUCCESS`,
esta bien.

⚠️ **Hay ademas UNA unitaria saltada a proposito, y no es ruido: es un defecto abierto.** El
metodo `elCambioInesperadoCabeDentroDelReloj` de `RelojDeLaEtapaTecnicaQaTest` — la clase **no**
esta saltada entera, sus otros dos casos pasan y son la red de los dos defectos hermanos que si
se arreglaron. Cuando se decida que hacer se le quita el `@Disabled` y tiene que pasar. Ver
[Defectos conocidos](docs/DEFECTOS-CONOCIDOS.md).

### Un backend propio para una rama, y sus cuatro trampas (02/09/2026)

Los tests levantan lo suyo con Testcontainers, pero **comprobar una rama a mano —el panel contra
esta API— escribe en la base**, y la del 5433 es la que comparte todo el mundo. Levantar un
backend solo para la rama cuesta cuatro tropiezos que no estaban escritos en ningun sitio:

- ⚠️ **La imagen es `pgvector/pgvector:pg16`, no `postgres:16`.** `vector_store` no la crea Flyway
  a proposito: la escribe Spring AI al arrancar (`initialize-schema: true`) con la extension
  `vector`, y sin ella la aplicacion no llega a levantar.
- ⚠️ **Un worktree no tiene `application-secrets.yaml`.** Se importa por ruta **relativa**
  (`spring.config.import: optional:file:./application-secrets.yaml`) y no se versiona, asi que
  desde un worktree el archivo no existe: `api-key: ${google.gemini.api-key}` se queda sin
  resolver, la aplicacion no arranca y la base se queda sin migrar. Se apunta al del repositorio
  principal **por ruta absoluta**.
- ⚠️ **El vhost de RabbitMQ se fija en la linea de comandos**, que es lo unico que gana sobre el
  perfil. `application-local.yaml` clava `virtual-host: /`, y **dos backends de dos ramas en el
  mismo vhost se roban los mensajes**: el trabajo de IA de uno se lo come el otro proceso y la
  calificacion no vuelve nunca. La base tambien va por argumento, no editando el perfil.
- ⚠️ **El `dev-login` solo crea al PRIMER usuario del equipo.** `arrancarPrimerUsuario` mira si ya
  hay alguien de equipo en la organizacion plataforma; si lo hay, cualquier otro id recibe «Ese id
  de RENASER OS no esta registrado en el sistema». En una base recien migrada **el primero que
  entre se queda el cupo**, y el guion de siembra entra el primero: despues hay que usar su mismo
  id, no inventar otro.

## El perfil del candidato (25/08/2026)
Cada persona tiene **un perfil, unico y transversal a organizaciones**: cuelga de `persona`,
no de `usuario` (el usuario existe una vez por organizacion). Se llena solo con lo que el
agente `DATOS_CV` lee del curriculum, el candidato lo corrige, y se reutiliza en cada
postulacion. Paquete `perfil/` completo (V36: 6 tablas + 2 catalogos), CRUD en
`/api/v1/portal/perfil/**`, lectura en el panel con el permiso `ver_perfil_candidato`.

Reglas que no se negocian:
- **La IA propone, nunca pisa** (RF-159): lo leido entra `origen=CURRICULUM` sin confirmar;
  lo escrito o confirmado por la persona no se toca. El merge vive en
  `ServicioPropuestaPerfilImpl` y sus tests son la especificacion.
- **Una lectura por archivo** (RF-161): `archivo.contenido_hash` (SHA-256). Postular con el
  mismo PDF copia la ficha `dato_cv` en vez de pagar otra llamada. El disparo al postular se
  apaga con `renaser.perfil.lectura-al-postular=false` (las pruebas de calificacion lo usan).
- **El perfil NO puntua**: no entra en notas ni rankings.
- **La pretension salarial** va detras del permiso `ver_pretension` y sin el **no viaja ni el
  nombre del campo** en el JSON del panel. Nunca en listas ni rankings.
- **El borrado 29733 se lleva el perfil entero** (paso 5b de `ejecutarBorrado`); `dato_cv` se
  queda porque sostiene lo ya evaluado. Retencion: parametro `meses_conservar_perfil` (24) y
  el barrido diario `BarridoRetencionPerfil`.
- Una lectura de `DATOS_CV` **sin hermanos en la tanda no arma el retrato**: la barrera de la
  cola lo ignora hasta que una criba o una entrega encolen a los demas.

El contrato para el frontend esta en [docs/APIS-PERFIL-DEL-CANDIDATO.md](docs/APIS-PERFIL-DEL-CANDIDATO.md)
y el diseño en [docs/PROPUESTA-PERFIL-DEL-CANDIDATO.md](docs/PROPUESTA-PERFIL-DEL-CANDIDATO.md).
**Los RF-155..170 no entran en `01-REQUISITOS-FUNCIONALES.md` hasta que Renaser los valide.**

⚠️ **No encender con candidatos reales sin el texto de consentimiento nuevo**: el vigente
cubre una postulacion concreta, no un perfil que se conserva entre convocatorias (RF-169).

## Multiempresa (26/08/2026 · completo, piezas A-F)
El sistema paso de «Renaser contrata para Renaser» a plataforma tipo Indeed: cada empresa
se registra por invitacion de Renaser, publica sus vacantes y ve solo a sus candidatos.
Los seis specs con el porque de cada decision estan en `docs/superpowers/specs/2026-08-25-*.md`.
Lo que hay que saber para tocar codigo:

- **La plataforma es un dato, no un literal**: `organizacion.es_plataforma` (solo una puede
  serlo). El `findByCodigo("RENASER")` quemado ya no existe: se resuelve con
  `findByEsPlataformaTrue`.
- **Cuatro banderas de personalizacion** por organizacion (banco, pesos, plantillas de
  evaluacion, pruebas), apagadas por defecto = leer el instrumento de la plataforma;
  encender = copiarlo (`POST /panel/organizacion/personalizacion`). El UNICO punto que las
  interpreta es `organizacion/service/DuenoDelInstrumento`: toda consulta de instrumentos
  pasa por el. **Leer resuelve, editar no**: con la bandera apagada se VE el metodo de la
  plataforma en solo lectura; mutar algo ajeno responde 404. Apagar archiva el banco
  propio, nunca lo borra (RF-138).
- **Dos logins separados**: el portal del candidato (registro publico, como siempre) y el
  panel de empresas (`POST /panel/auth/login`), que solo autentica cuentas con
  `usuario.es_equipo` — un candidato con su contrasena correcta recibe el mismo 401 que un
  correo inexistente. Las cuentas del panel nacen SOLO por invitacion (tabla `invitacion`,
  token de un solo uso, se guarda el hash). El candidato es de la plataforma: una cuenta,
  postula a cualquier empresa, y **su postulacion nace en la organizacion de la vacante**.
- **El alta de empresas** (`POST /panel/plataforma/empresas`, permiso
  `administrar_plataforma` + ser la plataforma) siembra roles y matriz de permisos (sin
  `administrar_plataforma`), parametros, textos legales en borrador y correos activos, e
  invita al primer administrador.
- **El aislamiento tiene dos vigilantes**: la regla de ArchUnit que prohibe `findById`
  suelto sobre repositorios de agregados con dueno (lista `LLAMADAS_SIN_DUENO_ACORDADAS`
  en `ArquitecturaTest`; al anadir una llamada legitima hay que escribirla ahi con su
  porque) y `FlujoDosEmpresasIT`, que recorre el alta, el aislamiento en ambos sentidos,
  el metodo compartido, el viaje completo del candidato en la empresa nueva (rinde el
  examen del banco de la plataforma y la empresa ve nota, bandeja y embudo) y el borrado
  de punta a punta. La unica pantalla que mezcla empresas es el tablon publico de
  vacantes, a proposito.
- **El borrado 29733 es de la plataforma**: los candidatos son cuentas de plataforma y la
  anonimizacion cruza empresas. Desde una empresa responde 403.
- **El consentimiento se firma con cada empresa** (pieza D, V38): al crear la cuenta se
  consiente con la plataforma; al postular se acepta el texto PROCESO de LA EMPRESA de la
  vacante, y la fila de `consentimiento` queda con `postulacion_id`, IP y navegador.
  Postular exige `aceptaTratamiento` (400 sin el), el texto se lee sin token en
  `GET /portal/vacantes/{id}/consentimiento`, y **publicar una vacante exige el texto
  PROCESO publicado de la organizacion** — se publica por `POST /panel/textos-consentimiento`
  (permiso `editar_textos_correo`; crea la version Y la publica). Renaser lo tiene desde la
  V9; a las empresas nuevas el alta se lo copia en borrador.
- **Cada llamada al modelo tiene precio** (pieza E, V38-V39): `ejecucion_ia.costo` se escribe
  al cerrar con la tarifa vigente de `tarifa_modelo` (por proveedor+modelo, vigencia por
  fecha; las sembradas son PROVISIONALES). La bitacora guarda el modelo QUE EL PROVEEDOR
  REPORTA (o el pedido en esa llamada si calla), y por eso **todo modelo de
  `application.yaml` necesita su tarifa** — la V39 sembro la del modelo rapido
  (`deepseek-chat`, la lectura de CV) que a la V38 se le quedo fuera y dejaba ese gasto en
  NULL; un IT recorre los modelos configurados y exige tarifa vigente para cada uno. Tope
  mensual por organizacion: parametro `tope_mensual_ia` (ausente = sin tope; lo administra
  Renaser, `editarParametro` se lo niega a la empresa). Al 80% un correo `TOPE_IA_AVISO`
  una vez por mes (la marca es el parametro `aviso_tope_enviado_mes`; el aviso corre en su
  PROPIA transaccion y su fallo se traga: una campana jamas tumba una postulacion); al 100%
  los trabajos nuevos nacen **EN_ESPERA** —no fallan, no se publican, el candidato ve «en
  curso»— y el sondeo de atascados los despierta cuando vuelve el cupo (tope subido o mes
  nuevo, hora de Lima). El retrato que cierra una tanda no pasa por el tope: sus insumos ya
  se pagaron. Consumo por empresa/mes/agente en `GET /panel/plataforma/consumo?mes=YYYY-MM`.
- **Renaser suspende y reactiva empresas** (pieza F): `organizacion.es_activa` ahora lo leen
  el login del panel (mensaje claro: solo se llega con la contrasena correcta), el
  `FiltroIdentidad` (corta los tokens vivos de 8h, solo tipo EQUIPO; cuesta una consulta de
  organizacion por peticion de panel, aceptado), el tablon publico (lista, detalle, texto
  legal y el POST directo de postular) y **la cola de IA**: suspendida es congelada tambien
  para el gasto — lo en vuelo termina, los trabajos NUEVOS de una suspendida nacen
  EN_ESPERA y el barrido no la despierta ni con cupo; al reactivar, el mismo barrido lo
  suelta todo (nada queda zombi). Los candidatos que ya estaban dentro NO se tocan: siguen
  viendo lo suyo, y sus correos de transicion siguen saliendo.
  Endpoints de plataforma, todos con doble llave y auditados con motivo: suspension,
  reactivacion (la plataforma no puede suspenderse a si misma), tope-ia (en blanco = sin
  tope) y personalizacion de otra empresa. La ficha del continente
  (`GET /panel/plataforma/empresas`) trae estado, tope, banderas y consumo del mes — y ahi
  se acaba lo que Renaser ve: ninguna via nueva a los datos de las empresas, a proposito.
- Los ITs del multiempresa son dos: `FlujoDosEmpresasIT` (alta, aislamiento, viaje del
  candidato, consentimiento por postulacion, borrado —tambien del papel firmado al
  postular—, suspension y reactivacion) y `FlujoPlataformaIT` (la vida entera del tope con
  la calificacion encendida y un doble del modelo que reporta lo que el proveedor real:
  costo exacto de la pasada rapida, campana del 80, EN_ESPERA al 100, la suspendida que no
  despierta ni con cupo, despertar al reactivar y al subir el tope, y tarifa vigente para
  todo modelo configurado).

## Los inscritos de una sesion y los permisos editables (27/08/2026 · V40)
Quien conduce una simulacion no sabia a quien esperaba: el nombre estaba en la base
—`inscripcion_sesion` → `postulacion` → `usuario` → `persona`— pero no salia por ningun
endpoint, y sin el no habia forma de llegar a las `inscripcionId` que piden
`/inscripciones/{id}/marcas` y `/asistencia`. La V40 **no anade ninguna tabla**: solo dos
permisos y su reparto inicial. Lo que hay que saber para tocar codigo:

- **`GET /panel/sesiones-simulacion/{id}/inscritos`** (permiso `ver_inscritos_simulacion`)
  devuelve `inscripcionId`, `postulacionId`, `candidato`, `vacante`, `inscritaEn` y `asistio`,
  recortado por el alcance. Y **los dos GET de sesiones aceptan los dos permisos**
  (`crear_sesiones_simulacion` **o** `ver_inscritos_simulacion`): sin eso, el responsable del
  area tendria los inscritos de un `{id}` que no hay forma de averiguar.
- **`contarInscritos` es el UNICO sitio que decide como se cuentan los inscritos**
  (`ServicioSimulacionImpl`). La lista, el detalle y `/inscritos` se separaron tres veces
  seguidas; mientras hubiera dos implementaciones, arreglar una dejaba la otra rota. Sus cuatro
  casos: alcance nulo (no tiene el permiso) cuenta la sesion entera, `TODO` cuenta todo,
  `SUS_VACANTES` cuenta con un `WHERE` en la base y `PROPIO` cuenta cero sin preguntar. **Con
  que alcance se cuenta lo decide siempre `ver_inscritos_simulacion`**, no el permiso con el
  que se llego a la sesion: contar inscritos es verlos.
- **Las marcas y la asistencia aplican el alcance** (`verMarcas`, `marcarEvento`,
  `marcarAsistencia`): antes pedian su permiso y lo tiraban, asi que un `SUS_VACANTES` valia
  tanto como un `TODO`. Y `laInscripcion` comprueba la empresa **por sus dos lados** —la sesion
  la lleva como columna y la postulacion tambien—, porque la inscripcion no guarda organizacion
  y marcar asistencia sobre una ajena transiciona la postulacion de la otra empresa. Ajena
  responde 404, igual que una que no existe.
- **El reparto se edita por API**: `GET /panel/roles/{id}/permisos`, `PUT …/{codigo}`,
  `POST …/{codigo}/revocacion`, con `administrar_permisos`. Motivo obligatorio y auditoria en
  los tres. **El ultimo rol de la organizacion con `administrar_permisos` no se puede quedar
  sin el** —el candado cuenta por organizacion, no en total—. Va aparte de
  `crear_usuarios_y_asignar_roles` a proposito: dar un rol es una cosa, redefinir lo que ese
  rol significa es otra. Cambiar el alcance **no** reescribe `rol_permiso.creado_en`: cuando
  cambio lo dice la auditoria.
- **`NombresDeUsuarios`** resuelve el nombre respetando la anonimizacion: sin usuario, sin
  persona o con la persona borrada devuelve `(anonimizado)`, nunca una cadena vacia, y una
  tanda entera cuesta dos consultas.

## El banco de preguntas v3 (19/08/2026)
El cliente reemplazo el banco entero. Son **190 items** (85 directivo, 55 coordinacion, 50
operativo) con **ocho formatos nuevos** —`EF-4`, `SJT-R`, `SEC`, `INV`, `DE`, `CD`, `V`, `PC`—
y ninguno coincide con los seis de antes. El banco v0.1 y sus 200 preguntas **ya no existen**:
la migracion `V20` los borra. Se pudo porque no habia ni una respuesta guardada.

**El examen ya no se muestrea: se aplica el banco completo del nivel**, en el orden del
documento. Ese orden importa — separa cada par de consistencia de su pareja al menos quince
items — asi que **no se baraja**.

El insumo es `docs/insumos/banco-renaser-v3-completo.pdf` y lo convierte
`scripts/importar-banco-v3.py`, que comprueba cuatro totales que el propio documento declara,
mas una quinta: **ningun item que se responda eligiendo puede quedarse sin opciones**. Si algo
no cuadra, no genera nada. **No transcribir items a mano.**

**Lo que arreglo la migracion `V25` (20/08/2026).** La carga original dejo 28 items sin una
sola fila en `opcion` —los 13 `INV`, los 6 `DE` y 9 de los 10 `PC`— porque su contenido esta
maquetado distinto en el PDF y el importador no lo reconocia: la lista de un `INV` va corrida
y separada por `·`, la de un `DE` en una tabla de numeros en circulo, y un `PC` dice sus
alternativas dentro de la frase (`Si / No`). La quinta comprobacion existe para que no vuelva
a pasar.

**Decision pendiente de Renaser:** cuatro items `EF-4` (`D51`, `D60`, `C40`, `O37`) **no
tienen enunciado propio en el documento**: arrancan directos en su tabla de opciones. La `V25`
les pone la instruccion del formato que el propio documento define en su seccion 0.2. Si
Renaser quiere una pregunta propia para alguno, se cambia desde el panel.

### PENDIENTE · el detalle de las respuestas tiene que pasar a tabla
Los formatos nuevos no se responden con una sola opcion: un `SJT-R` califica cada opcion del 1
al 5, un `EF-4` marca dos, un `SEC` ordena cinco pasos. Eso se guarda hoy en
**`respuesta.detalle`, una columna `jsonb`** (migracion `V21`).

**Es deuda tecnica y se tomo a sabiendas.** Lo correcto es una tabla
`respuesta_detalle(respuesta_id, opcion_id, valor, orden)`. Lo que cuesta mientras tanto:

- La base **no comprueba nada**: nada impide apuntar a una opcion de otra pregunta, ni guardar
  un detalle con la forma de otro formato. Esa comprobacion vive ahora en
  `ValidadorDetalleV3`, y **es lo unico** entre una respuesta mal formada y una nota calculada
  sobre ella. Si se toca ese validador, hay que mirar sus tests primero.
- Un informe que cruce respuestas ya no sale con SQL normal.

Al pasarlo a tabla, migrar lo que haya en `detalle` y borrar la columna.

### Cuidado con Jackson: el proyecto va con la 3, no con la 2
Spring Boot 4 trae **Jackson 3** (`tools.jackson.databind.ObjectMapper`), no el
`com.fasterxml.jackson.databind` de toda la vida. El de siempre **compila** —entra como
transitiva— pero **no existe en tiempo de ejecucion**, y el sintoma no dice nada de Jackson:
la clase que lo usa deja de registrarse como bean y salen decenas de errores de contexto
(«No qualifying bean of type ServicioEvaluacion»). Costo 37 tests en rojo averiguarlo.

En codigo de produccion: `tools.jackson`. Las pruebas de integracion siguen con Jackson 2, que
esta en su classpath, asi que `integracion/soporte/RespuestaV3` usa la 2 a proposito.

### El motor de puntuacion v3
`ServicioCalificacionImpl` **ya no es el del banco v0.1**: se reescribio sobre
`FormulasBancoV3`, que tiene las siete formulas del documento con sus tests. Cada item da de 0
a 3 segun su formato, se multiplica por su peso (1 llega a 3 puntos, 2 llega a 6) y la nota es
lo obtenido sobre la suma de los maximos.

**Un item sin responder no cuenta como cero**: se queda fuera del maximo tambien. Inventarle un
cero castigaria dos veces a quien no termino.

**PENDIENTE del motor**, que todavia no esta:
- Los items `V` no se puntuan: hace falta interpretar el dato escrito contra su tabla de tramos.
- En los `CD` solo se mira que el campo venga lleno, no que este dentro de rango ni que sea
  coherente con los demas campos del caso.
- Faltan los multiplicadores por familia, los umbrales de nivel (I, II, III), los cinco filtros
  eliminatorios y las banderas.
- `detectarContradicciones` sigue con la regla del v0.1 (`diferencia_maxima`); la del v3 es
  −5% del global y bandera roja.

Ver [docs/AVANCE-BANCO-V3-2026-08-19.md](docs/AVANCE-BANCO-V3-2026-08-19.md).

## La tuberia (19/08/2026)
Cada cambio pasa por GitHub Actions antes de llegar a nadie. **Si algo sale rojo, no se
fusiona.** El detalle esta en [docs/CI-CD.md](docs/CI-CD.md); aqui lo que hay que saber:

| Cuando | Que corre |
|---|---|
| En cada PR y en cada push a `main` | `./mvnw verify` (la tanda entera de tests), Semgrep, Gitleaks y la cobertura del codigo nuevo en SonarCloud |
| Al fusionar a `main`, si todo quedo verde | Se construye la imagen con el `Dockerfile`, se sube a ECR y se le dice por SSM a la maquina de EC2 que se actualice. Un smoke test comprueba que responde desde internet |
| De noche, sin bloquear a nadie | PIT (mutation testing) y fuzzing de la API con Schemathesis |

**Nada se despliega sin pasar la tuberia**: el trabajo de despliegue lleva `needs: [build,
estatico]`, asi que con cualquiera de los dos en rojo no llega a correr. Y en GitHub no hay
ninguna clave de AWS guardada: se usa OIDC.

**Hasta agosto de 2026 esto iba a Render.** Si encuentras una referencia suelta a Render o a
CloudAMQP en algun comentario, esta desfasada: hoy es EC2 + ECR, con la cola como un
contenedor mas de la propia maquina. **Tambien hubo un broker de Amazon MQ** (`renaser-mq`),
retirado el 23 de agosto de 2026 porque costaba ~$119 al mes —el 77% de la factura— por algo
que el mismo servidor hace gratis; cualquier mencion suya tambien esta desfasada.

**Semgrep** corre con las reglas de serie de Java mas tres propias en `.semgrep/`: que la IA no
lea el curriculum sin anonimizar, que no aparezcan rutas publicas fuera de
`ConfiguracionSeguridad`, y que ningun error se trague por consola.

### Los perfiles de configuracion
| Archivo | Cuando se usa | Que lo activa |
|---|---|---|
| `application.yaml` | siempre, es la base: Postgres local en el 5433, RabbitMQ sin TLS | nada |
| `application-secrets.yaml` | siempre que exista; son tus claves y no se versiona | lo importa `application.yaml` |
| `application-local.yaml` | trabajar en tu maquina sin tocar nada compartido: cola local y archivos en memoria | **nada: es el de por defecto** (`spring.profiles.default`) |
| `application-pruebas.yaml` | la aplicacion corriendo en EC2 (Supabase + la cola de la propia maquina) | `SPRING_PROFILES_ACTIVE=pruebas`, lo pone `despliegue/docker-compose.yml` |

Los perfiles son aditivos: cada uno solo escribe encima lo que cambia. Quien manda, de menos a
mas fuerza: `application.yaml` -> `application-secrets.yaml` -> el perfil activo -> los
argumentos del comando. La tabla completa con lo que cambia cada uno esta en el
[README](README.md#los-perfiles); esta es solo la lista de archivos.

**Si nadie nombra un perfil se aplica `local`.** Antes «ninguno» era una mezcla peligrosa: base
local, pero la cola la ponia `application-secrets.yaml` —que entonces apuntaba a un broker
compartido— y los curriculums iban al bucket de verdad.

⚠️ Eso incluye a los **tests**, que no nombran perfil: ahora corren con `local` encima. Por eso
`application-local.yaml` solo puede cambiar DONDE se guardan las cosas, nunca lo que el sistema
HACE. Apagar ahi `renaser.ai.calificacion.habilitada` rompe `FlujoCalificacionIaIT` y
`CalificacionIaRealIT`, que dan por hecho que esta encendida.

**Cuidado con el nombre**: el perfil `pruebas` es el entorno de Pruebas, **no** los tests. Cada
test se inyecta ademas sus propios valores con `@DynamicPropertySource`, que gana sobre todo.

En el servidor **no existe `application-secrets.yaml`**: el `Dockerfile` no lo copia y el import
es `optional:`, asi que alli todo llega por variable de entorno. La lista completa esta en
[docs/CI-CD.md](docs/CI-CD.md).

## Como esta organizado el codigo
Un solo proyecto para dos cosas: el modulo de seleccion de personal y el motor de agentes de
IA. **Los mantienen dos personas distintas**, asi que no comparten paquetes.

**El motor de agentes** vive entero bajo `ai_engine/ai/`, organizado por capa: `config`,
`controller`, `service`, `repository`, `dto`, `model`, `mapper`, `exception`, `context`,
`flow`, `messaging`, `prompt`, `rag`, `supabase`.

Hasta el 24/08/2026 lo llevaba Ricardo y aqui ponia **«No tocar nada de ahi dentro»**. Ya no:
Ricardo dejo de trabajar en el motor y ahora es responsabilidad nuestra, asi que se toca como
cualquier otra parte. Lo que **si** sigue en pie es la separacion entre los dos modulos: la
regla de ArchUnit que solo deja cruzar la frontera por las clases de la lista `ACORDADAS`
sigue valiendo, porque es una decision de diseno y no de reparto de trabajo.

Lo que cruza la frontera esta enumerado en la lista `ACORDADAS` de `ArquitecturaTest`, y la
regla falla si aparece algo que no este ahi. La mas usada es
`ai/exception/ResourceNotFoundException`, en cinco servicios. Antes esta frase decia «una sola
clase» y hoy son varias: mirar la lista, que es la que manda.

**La seleccion de personal** vive en un paquete por dominio, cada uno con sus capas dentro:

```
com.renaser.ai.ai_engine.<dominio>
├── controller/    los endpoints
├── service/       la interfaz, y la clase en impl/
├── repository/    los repositorios de Spring Data
├── entity/        las entidades JPA
└── dto/           los contratos de entrada y salida
```

| Dominio | Que guarda |
|---|---|
| `usuario` | Persona, usuario del equipo, roles y permisos |
| `organizacion` | La organizacion, sus areas, la plataforma (alta de empresas) y la personalizacion de instrumentos |
| `solicitud` | La Solicitud de Talento y sus resultados esperados |
| `vacante` | Vacante, puesto, catalogos y requisitos objetivos. Aqui vive tambien `AlcanceSobreLaVacante`, que decide para todo el panel que filas alcanza cada alcance |
| `postulacion` | La postulacion, su historial, el CV y **la maquina de estados** |
| `pesos` | Las versiones de pesos del embudo |
| `consentimiento` | Textos legales, consentimientos y solicitudes de borrado |
| `notificacion` | Plantillas y registro de correos |
| `archivo` | El almacen de archivos en disco |
| `auditoria` | El registro inmutable de quien hizo que |
| `parametro` | Los valores que Renaser cambia sin programar |
| `portal` | La cara del candidato: un controlador y tres servicios por tema (la cuenta y su acceso, el tablon publico, la postulacion) |
| `administracion` | La cara de configuracion del panel; el borrado 29733 vive aparte en `ServicioBorradoDatos` |
| `seguridad` | Las dos puertas, tokens, contexto y permisos con alcance |
| `comun` | El manejador de errores propio y el candado de Swagger |
| `catalogo` | Los catalogos que el panel consulta |
| `perfilintegral` | Evaluacion, criterios, notas y el Perfil de Talento (hito 2) |
| `prueba` | La prueba del puesto: plantillas, intentos y entregables (hito 3) |
| `decision` | La decision final, sus barreras y evidencias (hito 3) |
| `simulacion` | Sesiones, inscripciones, eventos observables y conversacion final |
| `validacion` | El periodo de validacion practica y sus metricas |

Dos clases marcan la frontera con el motor de agentes y **hay que actualizarlas al anadir un
controlador nuevo**, porque enumeran los nuestros: `comun/exception/ManejadorErrores` (para que
sus errores no salgan como 500) y `comun/config/ConfiguracionSwagger` (para que sus endpoints
salgan con candado). Esta escrito en el javadoc de ambas.

## Documentos del proyecto
Todo vive en `docs/`. Empezar por [docs/README.md](docs/README.md).

- [Que hace el sistema](docs/00-QUE-HACE-EL-SISTEMA.md) — el sistema entero **sin una sola palabra
  tecnica**, en cinco minutos. **Empezar por aqui.** Si algo de los demas documentos no se puede
  amarrar a una frase de este, sobra.
- [Requisitos funcionales](docs/01-REQUISITOS-FUNCIONALES.md) — RF-01 a RF-154. Objetivo, alcance,
  que hace el sistema y donde interviene una persona. Desarrolla cada frase del 00. Se lee solo.
- [Requisitos no funcionales](docs/02-REQUISITOS-NO-FUNCIONALES.md) — RNF-01 a RNF-66.
- [Estados de la postulacion](docs/03-ESTADOS-POSTULACION.md) — los **18** estados y sus
  transiciones. Base del backend: ningun estado fuera de esa lista puede existir. Tienen forma
  de rejilla (cinco etapas por cuatro momentos), asi que el siguiente estado se calcula.
  **Ojo: la maquina de estados es propuesta nuestra, no viene de Renaser.**
- [Roles y permisos](docs/04-ROLES-Y-PERMISOS.md) — los **77** permisos, accion por accion.
  Un rol es un conjunto de permisos guardado en base de datos, no algo fijo en codigo. En la
  base hay **71** sembrados: el doc enumera el sistema completo y la base solo lo construido,
  y las dos listas no se contienen del todo (hay cuatro sembrados que el doc no enumera). Desde
  la V40 el reparto se edita por la API con `administrar_permisos`, y `FiltroIdentidad` lo
  relee en cada peticion, asi que un cambio vale sin desplegar y sin volver a entrar. **No le
  pongas cache a `ServicioContexto`**: eso es justo lo que romperia.
- [Modelo de datos](docs/05-MODELO-DE-DATOS.md) — las **93** tablas por area, que impide la base
  y que tiene que vivir en el codigo. Se lee para entender el modelo.
- [Diccionario de datos](docs/07-DICCIONARIO-DE-DATOS.md) — cada tabla con todas sus columnas,
  tipos y claves. Se consulta. **Es la base de las migraciones de Flyway.**
- [Las APIs](docs/09-APIS.md) — las dos puertas (portal/panel), como entrar y que hace cada
  endpoint. La referencia viva es Swagger en `/swagger-ui.html`.
- [Alcance del MVP](docs/08-ALCANCE-DEL-MVP.md) — **el cliente quiere un MVP, no el sistema
  completo.** Tres hitos: 34 tablas, 62 y 73, cada uno usable solo. Dice tambien que se deja
  puesto desde el hito 1 para no reescribir despues. **Leerlo antes de escribir cualquier
  migracion.**
- [Calificacion con IA](docs/CALIFICACION-CON-IA.md) — como la IA lee el curriculum, califica lo
  abierto y arma el Perfil de Talento. Y que pasa cuando falla.
- [Avance de simulacion y validacion](docs/AVANCE-SIMULACION-VALIDACION-2026-08-18.md) — las dos
  ultimas etapas y sus tres reglas automaticas de disponibilidad.
- [CI/CD](docs/CI-CD.md) — que se comprueba solo en cada cambio, como se despliega a AWS y que
  variables de entorno pide el servidor. **Leerlo antes de tocar `.github/workflows/`.**
- [Comprobaciones automaticas](docs/COMPROBACIONES-AUTOMATICAS.md) — las 1150 pruebas y las nueve
  reglas de arquitectura, una por una. Que se mira solo y que no.
- [Defectos conocidos](docs/DEFECTOS-CONOCIDOS.md) — **lo que esta roto, se sabe y sigue
  abierto.** Que le pasa a una persona de verdad, por que pasa y que haria falta para
  arreglarlo. **Mirarlo antes de invitar a una tanda de candidatos.**
- `docs/diagramas/` — HTML autocontenidos, se abren en el navegador.
- `docs/mockups/` — prototipos de pantalla. **Los mantiene otra persona, no editarlos.**
- `docs/insumos/` — documentos originales del cliente y notas de analisis.

`DESIGN.md` y `PRODUCT.md` en la raiz son de la herramienta de mockups, no de este trabajo.

## Al editar los documentos
- Cada documento debe leerse solo: quien lo abra no necesita otro para entenderlo.
  Los 01 a 05 abren con contexto propio y cierran con enlaces a los demas.
- **Nunca citar un requisito por su numero entre documentos.** Los numeros cambian cada vez
  que se inserta uno y las referencias quedan apuntando a otra cosa sin que nada falle.
  Citar el nombre de la seccion: `(ver «Panel de gestion»)`, `(ver «Auditoria» en los no
  funcionales)`. Los numeros RF/RNF solo existen dentro de su propio documento.
- Verificar que las secciones citadas existan:
  `grep -ohE '«[^»]+»' docs/0[3-4]*.md | sort -u`

## Diagramas
Usar la skill `diagram-design`. Paleta tomada de RENASER OS: papel `#f5f5f3`,
tinta `#0d0d0d`, apagado `#70706b`, linea `#e3e3df`, y el semaforo del propio sistema
verde `#2f6e51` / ambar `#9b6a22` / rojo `#a43b36`.
Verificar siempre con `self_check.py` y ademas revisar el render: `google-chrome --headless
--screenshot` genera un PNG que hay que mirar. El verificador no detecta flechas que cruzan
por detras de una caja ni texto desbordado.

## Insumos del cliente
`docs/insumos/` guarda transcripciones, documentos del cliente y notas de analisis.

Documentos clave (leer antes de trabajar):
- [Que cambia con el documento nuevo](docs/insumos/CAMBIOS-DEL-DOCUMENTO-NUEVO.md) — que trajo
  el documento del 14/08, que contradice y **que se decidio en cada punto**.
- [Que documento manda](docs/insumos/ANALISIS-DOCUMENTOS.md) — orden de precedencia, estado de
  cada documento del repositorio y las decisiones anteriores al 14/08. `DECISIONES.md`, la
  bitacora del 14/08 y el reporte del 17/08 se borraron el 18/08/2026: repetian lo que ya dicen
  este documento y los de avance, y ademas lo decian desactualizado.
- [La comprobacion](docs/insumos/COMPROBACION-SIN-TECNICA.md) — el rastreo de las 93 tablas
  contra el documento 00, y de donde salen los conteos de cada hito.
- [Notas temporales](docs/insumos/NOTAS-TEMPORALES.md) — lo que sigue pendiente.
- `docs/insumos/pruebas-tecnicas/` — **las cinco pruebas del puesto reales**, en PDF, tal como se
  enviaron a candidatos. **Son anteriores**: modelo de contenido y de tono, no de formato.
- [Entrevista con el cliente 08/08](docs/insumos/entrevista-cliente-2026-08-08.md) — transcripcion.

Regla de precedencia: manda **nuevo_doc_requisitos_funcionales.docx** (RENASER Talent
Intelligence V1.1 FINAL), que se declara definitivo y reemplaza a los anteriores.
`Sistema_Completo_Talento_RENASER_Seleccion_2026_2029` conserva valor para el texto completo de
bancos, pruebas y ejemplos que el vigente no reproduce, si no lo contradice.
`Banco_Maestro_Preguntas` sigue vigente solo para las preguntas y sus claves.
`Sistema_RENASER_Talent_Intelligence` es la version anterior del vigente y esta descartada: sus
baterias reutilizan los mismos IDs (D01-D90 etc.) para preguntas distintas.

## Lo que Renaser hace hoy, y lo que nunca ha hecho
El proceso **ya existe y lo llevan a mano**: no se esta inventando un metodo, se esta
digitalizando uno que usan. Eso vale para casi todo el recorrido, con **tres excepciones en la
prueba del puesto** que hay que tener presentes porque el modelo las soporta y la realidad no las
usa todavia:

| Lo que pide el documento del cliente | Lo que Renaser hace hoy | Estado |
|---|---|---|
| Prueba cronometrada, con reloj en el servidor | Encargo de varios dias, sin reloj | **Se hara.** Es la mejora que quieren |
| Un cambio inesperado a mitad de la prueba | No existe | **Se hara** |
| Rubrica publicada que sume 100 puntos | Una lista de 10 a 12 criterios **sin numeros** | **Pendiente de Renaser.** Frena el paso 0 |

Las cinco pruebas reales estan en `docs/insumos/pruebas-tecnicas/` (ARQ, BIO, CIVIL, CX, SIS: el
prefijo es la familia de trabajo). Son **anteriores**: valen como modelo de contenido y de tono,
no de formato. De mirarlas salio que los entregables sean una tabla y no un texto —cada prueba
pide de uno a cuatro, cada uno con su regla— y que `version_plantilla_prueba` tenga `modalidad`,
que existe solo para poder cargarlas y adaptarlas.

⚠️ **Lo que piden esas cinco no cabe en una sesion con reloj.** Un producto funcional mas un
video no se hace de una sentada. Ponerles reloj obliga a **encoger el encargo**, y eso lo tiene
que reescribir Renaser: sin eso el hito 3 no tiene contenido.

Esto decia «dura 60-120 minutos», y **ese rango se retiro el 31/08/2026** por decision de
Renaser: manda el tiempo que ponga la empresa y solo queda un piso de 5 minutos. El bloqueo **no
se movio** — ahora Renaser decide ademas cuantos minutos son.

## Sistema susceptible a cambios
El cliente realiza cambios recurrentemente, ya sea en métricas, en datos, en permisos de roles, diseñar el sistema para que admita todo tipo de cambios inesperados, y que todo sea personalizable para el rol de dirección o admin. Que pueda ver y editar todo. 

## Git
No crees commits poro favor, para poder visualizar los cambios que se realizaron, yo creo commits y los pr. No lo hagas amenos que te lo pida explicitamente

## Mantener este archivo al dia
**Este archivo es lo primero que lee una sesion nueva. Si miente, la sesion trabaja sobre una
idea falsa del proyecto** — ya paso: decia «hito 1 implementado, hitos 2 y 3 no» cuando el
embudo ya estaba completo.

**Al terminar de implementar una funcionalidad, actualiza «Donde estamos» antes de dar el
resumen final.** No al empezar ni a mitad: cuando la funcionalidad ya funciona y sus tests
pasan.

Que se actualiza:

| Si cambio... | Actualizar |
|---|---|
| Se implemento una funcionalidad o se cerro un hito | La tabla de hitos y «lo que falta de codigo» |
| Se anadio una migracion | El rango `V1-Vxx` |
| Cambio el numero de tests | La fila de tests |
| Se anadio un paquete de dominio | Su tabla y el conteo de paquetes |
| Un pendiente dejo de serlo, o aparecio uno nuevo | Las tablas de «lo que falta» |
| Un placeholder se reemplazo por el valor definitivo | Donde se nombre ese placeholder |

Reglas al escribirlo:
- **Fechar la seccion** con la fecha del cambio.
- **Borrar lo que dejo de ser cierto**, no acumular. Este archivo se lee entero cada sesion:
  cuanto mas largo, menos se sostiene.
- **Verificar antes de escribir.** El numero de tests sale de correrlos, no de recordarlos.
- Es un mapa para orientarse, no un historial. **Lo que se hizo un dia concreto va en un
  documento de avance en `docs/`**, y aqui solo queda el estado presente.

## PROMPTs para cada respuesta que me des

Keep responses focused, brief, and concise. Keep disclaimers and caveats short, and spend most of the response on the main answer. When asked to explain something, give a high-level summary unless an in-depth explanation is specifically requested.

Before your first tool call, say in one sentence what you're about to do. While working, give a brief update only when you find something important or change direction. When you finish, lead with the outcome: your first sentence should answer "what happened" or "what did you find," with supporting detail after it for readers who want it.





