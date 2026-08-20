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
| Base de datos | **Flyway es el dueno del esquema**: V1-V23 en `src/main/resources/db/migration`, **85 tablas de este modulo** (88 en la base contando `flyway_schema_history` y las dos del motor de agentes, `agent_run` y `vector_store`). `ddl-auto: validate` |
| Codigo | Veintiun paquetes de dominio bajo `ai_engine` (ver «Como esta organizado el codigo») |
| Tests | **381, todos en verde (20/08/2026)**: 331 unitarios y 50 de integracion. Ver «Correr los tests»: unitarios de la maquina y del anonimizador, migraciones contra Postgres real (Testcontainers) y flujos de punta a punta: hito 1, evaluacion, prueba, simulacion/validacion y calificacion con IA |
| IA que califica | **Seis de los diez agentes del catalogo estan implementados**: datos del curriculum, evidencia, evaluador, potencial/riesgo, prueba del puesto y las preguntas de la conversacion final. Todos corren por cola de RabbitMQ contra DeepSeek. Ver [Calificacion con IA](docs/CALIFICACION-CON-IA.md) |
| Correo | Se registra en `correo_enviado` pero NO se envia: falta el dominio de Renaser (`EnviadorCorreoLog`) |
| Identidad equipo | `dev-login` de desarrollo; el contrato de RENASER OS no existe aun (`ProveedorIdentidadEquipo`) |
| Frontend | **Dos, y hacen cosas distintas.** Ver «Las dos pantallas» mas abajo |
| Mockups | Los mantiene otra persona y **describen la version anterior** |

**Lo que falta de codigo:** de los diez agentes del catalogo quedan cuatro sin clase, y
ninguno es del MVP: `NECESIDAD_TALENTO`, `CAZATALENTOS`, `DESEMPENO` y `APRENDIZAJE`.

Los dos del hito 3 ya corren (20/08/2026), y los dos se **piden a mano** desde el panel, igual
que la criba de curriculums: cada llamada al modelo cuesta dinero y a quien se califica lo
decide quien lleva la vacante.

| Agente | Que hace | Por donde se pide |
|---|---|---|
| `PRUEBA_PUESTO` | Puntua los criterios de la rubrica **marcados como `AGENTE`**; los de `PERSONA` los sigue poniendo alguien. Al terminar mueve la postulacion a `PRUEBA_POR_CONFIRMAR` | `POST /api/v1/panel/postulaciones/{id}/prueba/calificacion-ia` |
| `SIMULACION` | Escribe entre tres y cinco preguntas para la conversacion final, sacadas de las contradicciones entre lo que dijo y lo que se le vio hacer. **No puntua nada** | `POST /api/v1/panel/postulaciones/{id}/conversacion-final/generar` |

Dos cosas que no son obvias:

- **Lo que no se puede leer no se puntua.** Una entrega en video, en imagen o en un enlace no
  da texto, y esos criterios se quedan sin nota para que los mire una persona. Es a proposito:
  un modelo al que se le exige una nota siempre da una nota, y despues nadie distingue la
  fundada de la inventada.
- **Una pregunta ya contestada no se borra.** Volver a pedir las preguntas rehace solo las que
  nadie llego a hacer.

**Lo que falta y no es codigo** — son decisiones de Renaser que los documentos defieren a
proposito, no trabajo olvidado:

| Que falta | Que bloquea |
|---|---|
| Un **facilitador designado** para la simulacion | La persona que observa la sesion presencial y marca los eventos. El sistema ya sabe que *roles* pueden hacerlo, y es cambiable desde el panel sin desplegar |
| La **matriz de informacion critica** de una sesion real | La lista de datos que no se le dan al candidato salvo que pregunte. La mecanica esta construida; el contenido lo escribe Renaser |
| La **figura contractual** de la validacion productiva | El requisito dice literalmente «la figura contractual aprobada por Renaser». Hoy hay un placeholder: `CONTRATO_TEMPORAL_EN_REVISION` |
| El **contenido real de una prueba del puesto** | Renaser tiene que reescribir una en formato de dos horas |

Otros placeholders puestos a proposito: los **umbrales de los grupos de prioridad (80 y 65)** son
parametro editable y Renaser no los ha confirmado.

Conocido y aceptado: `FiltroAlcance.responsableOFiltroNulo()` trata el alcance `PROPIO` igual que
`TODO` —devuelve nulo y no filtra—. Hoy no es alcanzable porque ninguna semilla da un permiso de
panel con ese alcance, pero si alguien configura un rol asi, veria de mas.

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

### La criba de curriculums (18/08/2026)
Contesta «llegaron cien curriculums, a quien invito primero». Antes la IA solo arrancaba
cuando el candidato entregaba su evaluacion, asi que con una carpeta recien llegada no habia
forma de pedirla. Cuatro endpoints en `PostulacionesPanelController`:

| Para que | Llamada |
|---|---|
| La tanda entera, ordenada de mas apto a menos | `GET /api/v1/panel/vacantes/{id}/ranking` |
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
- El esquema solo cambia con una migracion nueva. **Nunca** editar una ya aplicada.
- **Antes de numerar una migracion, mirar cual es la mas alta que existe.** Ricardo tambien crea
  migraciones: la de simulacion y validacion tuvo que pasar de `V16` a `V18` porque el ya habia
  tomado esos numeros, y Flyway no arranca con dos del mismo numero.
- Nada se recalcula hacia atras: cada nota queda atada a la version de pesos de su vacante.

### Correr los tests
**381 tests, todos en verde (20/08/2026).** Son dos tandas y el comando decide cual corre:

| Comando | Que corre | Cuantos | Necesita |
|---|---|---:|---|
| `./mvnw test` | unitarias con dobles y las de arquitectura | 331 | nada |
| `./mvnw verify` | lo anterior mas las de integracion | 381 | Docker |

Las de integracion levantan su propio PostgreSQL y su propio RabbitMQ con Testcontainers: hace
falta el demonio de Docker encendido. El desglose completo esta en
[docs/COMPROBACIONES-AUTOMATICAS.md](docs/COMPROBACIONES-AUTOMATICAS.md).

Entre las 331 hay **7 reglas de arquitectura con ArchUnit** (la frontera del motor, las capas y
que el estado solo lo mueva la maquina — las mismas reglas que este archivo tiene en prosa).

**El test que gasta dinero no corre solo**: `CalificacionIaRealIT` esta detras de
`@EnabledIfEnvironmentVariable`, asi que se salta sola si no esta la variable. Ni `test` ni
`verify` llaman a DeepSeek. Para pedirla a proposito:

```
RENASER_IA_REAL=si ./mvnw verify -Dit.test=CalificacionIaRealIT
```

**Ruido esperado que NO es un fallo**: `Failed to check/redeclare auto-delete queue(s)` de
RabbitMQ, y los cinco tests saltados de `CalificacionIaRealIT`. Si termina en `BUILD SUCCESS`,
esta bien.

## El banco de preguntas v3 (19/08/2026)
El cliente reemplazo el banco entero. Son **190 items** (85 directivo, 55 coordinacion, 50
operativo) con **ocho formatos nuevos** —`EF-4`, `SJT-R`, `SEC`, `INV`, `DE`, `CD`, `V`, `PC`—
y ninguno coincide con los seis de antes. El banco v0.1 y sus 200 preguntas **ya no existen**:
la migracion `V20` los borra. Se pudo porque no habia ni una respuesta guardada.

**El examen ya no se muestrea: se aplica el banco completo del nivel**, en el orden del
documento. Ese orden importa — separa cada par de consistencia de su pareja al menos quince
items — asi que **no se baraja**.

El insumo es `docs/insumos/banco-renaser-v3-completo.pdf` y lo convierte
`scripts/importar-banco-v3.py`, que comprueba cuatro totales que el propio documento declara y
no genera nada si alguno no cuadra. **No transcribir items a mano.**

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
| En cada PR y en cada push a `main` | `./mvnw verify` (los 381 tests), Semgrep, Gitleaks y la cobertura del codigo nuevo en SonarCloud |
| Al fusionar a `main`, si todo quedo verde | Se avisa a Render por su Deploy Hook, que construye la imagen con el `Dockerfile` y publica en Pruebas |
| De noche, sin bloquear a nadie | PIT (mutation testing) y fuzzing de la API con Schemathesis |

**Render no despliega solo**: su auto-deploy esta apagado a proposito, para que no publique con
la tuberia en rojo.

**Semgrep** corre con las reglas de serie de Java mas tres propias en `.semgrep/`: que la IA no
lea el curriculum sin anonimizar, que no aparezcan rutas publicas fuera de
`ConfiguracionSeguridad`, y que ningun error se trague por consola.

### Los tres perfiles de configuracion
| Archivo | Cuando se usa | Que lo activa |
|---|---|---|
| `application.yaml` | siempre, es la base: Postgres local en el 5433, RabbitMQ sin TLS | nada |
| `application-pruebas.yaml` | la aplicacion corriendo en Render (Supabase + CloudAMQP) | `SPRING_PROFILES_ACTIVE=pruebas` |
| `application-supabase.yaml` | tu backend local apuntando a Supabase | `-Dspring-boot.run.profiles=supabase` |

Los perfiles son aditivos: cada uno solo escribe encima lo que cambia.

**Cuidado con el nombre**: el perfil `pruebas` es el entorno de Pruebas, **no** los tests. Los
tests no usan ningun perfil — cada uno se inyecta sus valores con `@DynamicPropertySource`.

En Render **no existe `application-secrets.yaml`**: el `Dockerfile` no lo copia y el import es
`optional:`, asi que alli todo llega por variable de entorno. La lista completa esta en
[docs/CI-CD.md](docs/CI-CD.md).

## Como esta organizado el codigo
Un solo proyecto para dos cosas: el modulo de seleccion de personal y el motor de agentes de
IA. **Los mantienen dos personas distintas**, asi que no comparten paquetes.

**El motor de agentes** (lo lleva Ricardo) vive entero bajo `ai_engine/ai/`, organizado por
capa: `config`, `controller`, `service`, `repository`, `dto`, `model`, `mapper`, `exception`,
`context`, `flow`, `messaging`, `prompt`, `rag`, `supabase`. **No tocar nada de ahi dentro.**

Nuestro codigo usa una sola clase suya, `ai/exception/ResourceNotFoundException`, en cinco
servicios. Es la unica dependencia que cruza la frontera, y conviene saberlo: si el la mueve
otra vez, esos cinco imports se rompen.

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
| `organizacion` | La organizacion y sus areas |
| `solicitud` | La Solicitud de Talento y sus resultados esperados |
| `vacante` | Vacante, puesto, catalogos y requisitos objetivos |
| `postulacion` | La postulacion, su historial, el CV y **la maquina de estados** |
| `pesos` | Las versiones de pesos del embudo |
| `consentimiento` | Textos legales, consentimientos y solicitudes de borrado |
| `notificacion` | Plantillas y registro de correos |
| `archivo` | El almacen de archivos en disco |
| `auditoria` | El registro inmutable de quien hizo que |
| `parametro` | Los valores que Renaser cambia sin programar |
| `portal` | La cara del candidato: un controlador y un servicio para todo el recorrido publico |
| `administracion` | La cara de configuracion del panel |
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
- [Roles y permisos](docs/04-ROLES-Y-PERMISOS.md) — los **73** permisos, accion por accion.
  Un rol es un conjunto de permisos guardado en base de datos, no algo fijo en codigo.
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
- [CI/CD](docs/CI-CD.md) — que se comprueba solo en cada cambio, como se despliega a Pruebas y
  que variables de entorno pide Render. **Leerlo antes de tocar `.github/workflows/`.**
- [Comprobaciones automaticas](docs/COMPROBACIONES-AUTOMATICAS.md) — las 381 pruebas y las siete
  reglas de arquitectura, una por una. Que se mira solo y que no.
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

⚠️ **Una prueba cronometrada dura 60-120 minutos y lo que piden esas cinco no cabe ahi.** Un
producto funcional mas un video no se hace en dos horas. Ponerles reloj obliga a **encoger el
encargo**, y eso lo tiene que reescribir Renaser: sin eso el hito 3 no tiene contenido.

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





