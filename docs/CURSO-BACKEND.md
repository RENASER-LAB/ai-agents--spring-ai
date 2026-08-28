# Ruta de lectura del backend

Los archivos del backend en el orden en que conviene abrirlos: del más fácil al más difícil.
Cada uno se entiende con lo que viste en los anteriores. No hace falta leer nada más.

Contado sobre la rama `panel/notas-por-etapa`, commit `6bc1f3e` (25/08/2026). Las líneas
de los archivos se recontaron aquí: algunas llevaban tiempo desfasadas y se atribuían mal.

| | |
|---|---|
| Clases Java | **480**: 136 del motor de agentes y 344 de selección de personal |
| Migraciones | V1 a V36, **101 tablas** (100 de selección más `agent_run`, que es del motor) |
| Controladores | 21 de selección, 5 del motor de agentes |
| Tests | 66 archivos de prueba —69 `.java`, tres son ayudantes—, **645 pruebas** |

Este censo estaba antes atado a `feat/talentov3`, commit `c7c095a`. **Ese commit ya no cuelga
de `main`**: la historia se rehizo y quien clone hoy no lo tiene, así que el ancla no servía
para comprobar nada. Por eso ahora apunta a `main`.

**El censo es una foto del 25/08 y no se ha vuelto a tomar entera**, porque los números de
línea de cada archivo que se citan más abajo son de ese día y recontar la cabecera sin
recontarlos todos engañaría más que ayudar. Lo que ha cambiado desde entonces, para que nadie
se lleve una sorpresa: las migraciones llegan a la **`V40`** y las tablas de selección son
**102** —`invitacion` y `tarifa_modelo`, del multiempresa, más las que ya había—, y las pruebas
son **877 en total**: 770 unitarias (las 645 de la tabla son de ese día) y 107 de integración.
El desglose al día está en [Comprobaciones automáticas](COMPROBACIONES-AUTOMATICAS.md).

⚠️ Los documentos de `docs/` describen el sistema completo (93 tablas, 77 permisos). El código
va por detrás y por otro camino en algunos puntos —de esos 77 permisos hay **71 sembrados** en
la base, y al revés también: hay cuatro sembrados que la matriz del doc 04 no enumera—.
**Cuando se contradigan, manda el código.**

**La excepción es el [diccionario de datos](07-DICCIONARIO-DE-DATOS.md)**, que desde el
25/08/2026 sigue al código: están **las tablas de selección que existen de verdad** —100 de las
102 de hoy; a `invitacion` y `tarifa_modelo` les falta la ficha— y las columnas que fueron
añadiendo las migraciones, cada añadido con la suya marcada. Sigue llevando además las diez que
solo están diseñadas, así que ahí es al revés: le sobran más de las que le faltan. Lo demás de
`docs/` sigue siendo el diseño.

---

## Cómo usar esta ruta

Abres el archivo, lo lees, y preguntas lo que no entiendas. El número de líneas está para que
sepas a qué te enfrentas: nada de lo primero pasa de 50 líneas.

**Las rutas son relativas** a `src/main/java/com/renaser/ai/ai_engine/` si empiezan por un
dominio (`solicitud/`, `seguridad/`, `prueba/`…) y a `src/test/java/com/renaser/ai/ai_engine/`
si empiezan por `test/` o `integracion/`. Las demás salen de la raíz del repositorio.

Las etapas 0 a 11 son el camino obligatorio y se leen en orden. De la 12 en adelante son los
tres hitos, lo transversal y lo que fue llegando después: ahí ya puedes saltar al que te toque
tocar.

Marca aquí lo que vayas terminando.

---

## Etapa 0 · Qué hace el sistema, sin código

Media hora sin abrir un solo `.java`. Sin esto, la máquina de estados de la etapa 6 no se
entiende: son 18 estados que sólo tienen sentido si sabes qué le pasa a un candidato.

| | Archivo | Qué vas a ver |
|---|---|---|
| 1 | `docs/00-QUE-HACE-EL-SISTEMA.md` | El sistema entero sin una palabra técnica. Cinco minutos |
| 2 | `docs/03-ESTADOS-POSTULACION.md` | Los 18 estados y sus transiciones |
| 3 | `docs/diagramas/*.html` | Ábrelos en el navegador: embudo, estados y modelo de datos |

Al terminar sabrás: **las cinco etapas del embudo y cuánto pesa cada una.**

## Etapa 1 · Verlo corriendo

Antes de leer código, tenerlo encendido. Si algo falla aquí, todo lo demás es teoría.

| | Archivo | Líneas | Qué vas a ver |
|---|---|---|---|
| 4 | `docker-compose.yml` | — | Postgres con pgvector en el 5433 y RabbitMQ en el 5672 |
| 5 | `src/main/resources/application.yaml` | — | Base de datos, puertos, claves. Aquí se configura todo |
| 6 | `AiEngineApplication.java` | 13 | El punto de entrada. Todo Spring Boot empieza así |

```bash
docker-compose up -d && ./mvnw spring-boot:run
```

Con eso arriba, abre `http://localhost:8080/swagger-ui/index.html`: ahí está cada endpoint que
vas a leer en las etapas siguientes, con su cuerpo de ejemplo.

Las claves no van en `application.yaml` sino en `application-secrets.yaml`, que no se versiona
(copia `application-secrets.yaml.example`). **La aplicación arranca sin ninguna de las dos**:
`spring.ai.deepseek.api-key` tiene valor por defecto vacío y el fallo llega como un 401 en la
primera calificación, no al encender. Comprobado: arranca en ~12 s con Postgres y RabbitMQ
arriba, y `/swagger-ui/index.html` responde.

Al terminar sabrás: **qué necesita estar encendido y qué claves hacen falta antes de tocar nada.**

## Etapa 2 · Las tres piezas mínimas

Una entidad, un repositorio y un DTO. Con esto ya puedes leer el 60% del proyecto.

| | Archivo | Líneas | Qué vas a ver |
|---|---|---|---|
| 7 | `organizacion/entity/Organizacion.java` | 21 | Una clase Java atada a una tabla |
| 8 | `organizacion/repository/OrganizacionRepository.java` | 11 | Una interfaz vacía que ya sabe consultar. Nadie escribe el SQL |
| 9 | `solicitud/entity/ResultadoEsperado.java` | 23 | Otra entidad, con una relación a su padre |
| 10 | `catalogo/dto/DtosCatalogo.java` | 32 | Los `record` que viajan al frontend |

Ábrelos junto a `db/migration/V2__identidad_y_permisos.sql`: ahí está la tabla que la entidad
copia. **La tabla manda, la clase obedece.**

## Etapa 3 · Tu primer dominio entero

`solicitud` es el dominio más pequeño y completo. Léelo en este orden exacto: es el mismo
recorrido que hace una petición HTTP.

| | Archivo | Líneas | Qué vas a ver |
|---|---|---|---|
| 11 | `solicitud/entity/SolicitudTalento.java` | 41 | La entidad principal |
| 12 | `solicitud/repository/SolicitudTalentoRepository.java` | 15 | Consultas propias, escritas como nombres de método |
| 13 | `solicitud/dto/DtosSolicitud.java` | 48 | Lo que entra y lo que sale |
| 14 | `solicitud/service/ServicioSolicitudes.java` | 21 | La interfaz: qué se puede hacer |
| 15 | `solicitud/controller/SolicitudesController.java` | 62 | Los endpoints |
| 16 | `solicitud/service/impl/ServicioSolicitudesImpl.java` | 137 | **La lógica de verdad.** El primero difícil |

Al terminar sabrás: **por qué el servicio tiene interfaz e implementación separadas.**

## Etapa 4 · Quién es quién

Seis entidades planas, ninguna con lógica. La etapa 5 no se entiende sin ellas: `ServicioContexto`
y `FiltroIdentidad` las cargan en cada petición.

| | Archivo | Líneas | Qué vas a ver |
|---|---|---|---|
| 17 | `usuario/entity/Persona.java` | 27 | La persona, separada de su cuenta |
| 18 | `usuario/entity/Usuario.java` | 29 | La cuenta que inicia sesión |
| 19 | `usuario/entity/Rol.java` · `Permiso.java` | 24 · 24 | Los dos catálogos |
| 20 | `usuario/entity/UsuarioRol.java` · `RolPermiso.java` | 29 · 31 | Las dos tablas puente que los unen |

Léelas contra `db/migration/V2__identidad_y_permisos.sql`.

Al terminar sabrás: **por qué el permiso no cuelga del usuario sino de su rol.**

## Etapa 5 · Seguridad

Nueve archivos, de menor a mayor. Es el dominio que más se malentiende.

| | Archivo | Líneas | Qué vas a ver |
|---|---|---|---|
| 21 | `seguridad/dto/FiltroAlcance.java` | 18 | Hasta dónde puede ver alguien |
| 22 | `seguridad/config/PropiedadesSeguridad.java` | 23 | La configuración con tipos |
| 23 | `seguridad/dto/ContextoUsuario.java` | 25 | Quién es el usuario durante una petición |
| 24 | `seguridad/service/ServicioContexto.java` | 45 | Quién lo pone ahí |
| 25 | `seguridad/service/Permisos.java` | 46 | **`alcanceDe`: lo importante del dominio** |
| 26 | `seguridad/service/ServicioToken.java` | 47 | Firmar y verificar el JWT |
| 27 | `seguridad/filter/FiltroIdentidad.java` | 66 | Lo que corre *antes* del controlador |
| 28 | `seguridad/controller/PanelAuthController.java` | 84 | El `dev-login` provisional |
| 29 | `seguridad/config/ConfiguracionSeguridad.java` | 94 | Qué URL es pública y cuál no |

Con el `dev-login` sacas un token y ya puedes probar cualquier endpoint desde Swagger.

Falta una pieza que no está en este paquete: `vacante/service/AlcanceSobreLaVacante.java` (124
líneas). `FiltroAlcance` dice **qué es** un alcance; este dice **qué filas alcanza** cada uno en
el panel, y es el único sitio donde eso se decide. Vive en `vacante` y no en `seguridad` porque
para contestar necesita la vacante y su responsable. Léelo justo después del 25 y con
`AlcanceSobreLaVacanteTest` al lado: hasta el 27/08 esa regla estaba copiada en catorce sitios
y en doce se dejaba `PROPIO` sin tratar, que es el tipo de fallo que no rompe nada y solo se
nota cuando alguien ve lo que no debía.

Al terminar sabrás: **por qué el alcance se aplica dentro de la consulta y no filtrando después.**

## Etapa 6 · La máquina de estados

El corazón del negocio. 18 estados, y una sola puerta para cambiar de uno a otro.

| | Archivo | Líneas | Qué vas a ver |
|---|---|---|---|
| 30 | `postulacion/entity/EstadoPostulacion.java` | 29 | Los estados como filas, no como enum |
| 31 | `postulacion/entity/TransicionEstado.java` | 32 | El historial que no se borra |
| 32 | `postulacion/entity/Postulacion.java` | 34 | La entidad central del sistema |
| 33 | `postulacion/service/MaquinaEstados.java` | 185 | **Las reglas.** Léelo con el test al lado |
| 34 | `test/postulacion/service/MaquinaEstadosTest.java` | 98 | Las reglas escritas como ejemplos |

Al terminar sabrás: **por qué el siguiente estado se calcula en vez de escribirse.**

## Etapa 7 · El recorrido del candidato

| | Archivo | Líneas | Qué vas a ver |
|---|---|---|---|
| 35 | `portal/dto/DtosPortal.java` | 50 | Lo que ve el candidato |
| 36 | `portal/controller/PortalController.java` | 119 | Los endpoints públicos y con token de candidato |
| 37 | `portal/service/impl/ServicioPostulacionPortalImpl.java` | 322 | Postular, subir CV, consultar el avance |

El antiguo `ServicioPortalImpl` se partió en tres por tema: la cuenta
(`ServicioCuentaPortalImpl`), el tablón público (`ServicioTablonPortalImpl`) y la
postulación — la fila 37 apunta a este último, que es el del recorrido.

Al terminar sabrás: **por qué el portal y el panel son dos puertas distintas.**

## Etapa 8 · El panel del equipo

| | Archivo | Líneas | Qué vas a ver |
|---|---|---|---|
| 38 | `postulacion/repository/PostulacionRepository.java` | 48 | El alcance metido en las consultas |
| 39 | `postulacion/controller/PostulacionesPanelController.java` | 115 | La otra puerta |
| 40 | `postulacion/service/impl/ServicioPostulacionesPanelImpl.java` | 231 | Confirmar, ordenar, avanzar por lote |

Al terminar sabrás: **por qué nadie se descarta solo: se ordena y una persona confirma.**

## Etapa 9 · Vacantes y pesos

De dónde sale la vacante y cómo se reparten los cien puntos entre las cinco etapas.

| | Archivo | Líneas | Qué vas a ver |
|---|---|---|---|
| 41 | `vacante/entity/Vacante.java` · `RequisitoObjetivo.java` | — | Lo único que puede descartar sin humano |
| 42 | `vacante/controller/VacantesPanelController.java` | 126 | Publicar, cerrar, listar |
| 43 | `vacante/service/impl/ServicioVacantesPanelImpl.java` | 311 | La vacante nace de una solicitud |
| 44 | `pesos/service/impl/ServicioPesosImpl.java` | 195 | Versiones de pesos: **nada se recalcula hacia atrás** |

Al terminar sabrás: **por qué cada candidato queda atado a una versión de pesos.**

## Etapa 10 · Archivos y currículum

Pequeño y prerrequisito duro de la etapa 13: si la IA no tiene texto, no tiene qué leer.

| | Archivo | Líneas | Qué vas a ver |
|---|---|---|---|
| 45 | `archivo/entity/Archivo.java` | 27 | El archivo como fila, el contenido en el bucket |
| 46 | `archivo/service/AlmacenArchivos.java` | 20 | La interfaz que aísla dónde se guarda |
| 47 | `archivo/service/impl/AlmacenArchivosSupabase.java` | 278 | La implementación de hoy: el bucket privado de Supabase |
| 48 | `postulacion/service/impl/ExtractorTextoCv.java` | 109 | Saca el texto del PDF y del `.docx` |
| 49 | `postulacion/service/impl/ServicioTextoCvImpl.java` | 74 | Quién lo dispara y dónde lo deja |

Al terminar sabrás: **por qué el `.docx` se lee sin librería y el PDF con PDFBox.**

## Etapa 11 · Las dos clases frontera

| | Archivo | Líneas | Qué vas a ver |
|---|---|---|---|
| 50 | `comun/exception/ManejadorErrores.java` | 180 | Tus errores en vez de un 500 mudo |
| 51 | `comun/config/ConfiguracionSwagger.java` | 104 | El candado, solo en tus endpoints |

Las dos llevan **una lista de controladores escrita a mano**. Un controlador nuevo que no se
sume aquí falla en silencio.

---

Hasta aquí el camino obligatorio. Lo que sigue son los tres hitos: el mismo patrón de la
etapa 3, repetido con más volumen.

---

## Etapa 12 · Hito 2 · el Perfil Integral

88 clases en `perfilintegral`. Las etapas 1 y 2 del embudo —currículum, psicométrico y
evaluación— leídas juntas y con un 40% del total. Migraciones V10 a V14.

| | Archivo | Líneas | Qué vas a ver |
|---|---|---|---|
| 52 | `perfilintegral/controller/BancoPreguntasController.java` | 82 | Las preguntas viven en la base, no en el código |
| 53 | `perfilintegral/controller/PlantillasEvaluacionController.java` | 61 | Qué preguntas le tocan a cada puesto |
| 54 | `perfilintegral/controller/EvaluacionPortalController.java` | 67 | Lo que responde el candidato |
| 55 | `perfilintegral/service/impl/ServicioEvaluacionImpl.java` | 384 | Armar el cuestionario y recibirlo |
| 56 | `perfilintegral/service/impl/ServicioCalificacionImpl.java` | 240 | Calificar lo cerrado, sin IA |
| 56b | `perfilintegral/service/ServicioCalificacion.java` | 69 | La nota de lo cerrado, y `notaCombinada`: la única cuenta que mezcla lo cerrado con lo abierto |
| 57 | `perfilintegral/service/impl/ServicioPerfilIntegralPanelImpl.java` | 811 | El perfil terminado y el ranking, que desde el 25/08 se puede pedir por etapa |
| 57b | `perfilintegral/service/impl/ServicioDesgloseEvaluacionImpl.java` | 153 | La evaluación del banco abierta por dentro, para el panel |

Al terminar sabrás: **por qué las preguntas están versionadas.**

## Etapa 13 · La calificación con IA

Lo último construido y lo más grande de selección. Léelo con `docs/CALIFICACION-CON-IA.md`
delante y la migración `V16__cv_anonimizado_y_cola_ia.sql` al lado.

| | Archivo | Líneas | Qué vas a ver |
|---|---|---|---|
| 58 | `postulacion/service/impl/AnonimizadorCv.java` | 65 | Quita edad, sexo y estado civil antes de salir de Renaser |
| 59 | `test/postulacion/service/AnonimizadorCvTest.java` | 63 | Qué se considera dato sensible, con ejemplos |
| 60 | `perfilintegral/dto/DtosCalificacionIa.java` | 116 | El contrato con el modelo |
| 61 | `perfilintegral/service/CalificacionPorCriterio.java` | 197 | Cómo la nota de la IA se vuelve puntos |
| 62 | `perfilintegral/service/impl/PuenteCalificacionIaImpl.java` | 752 | **El archivo más grande de selección.** La cola, el reintento y qué pasa si la IA falla |

Al terminar sabrás: **qué le pasa a un candidato cuando DeepSeek no responde.**

## Etapa 14 · Hito 3 · la prueba del puesto y la decisión

Etapa 3 del embudo (30%) y el cierre. Migración V15.

| | Archivo | Líneas | Qué vas a ver |
|---|---|---|---|
| 63 | `prueba/controller/PruebaPortalController.java` | 71 | La prueba cronometrada, desde el candidato |
| 64 | `prueba/service/impl/ServicioPruebaImpl.java` | 339 | El reloj y el cambio inesperado a mitad |
| 65 | `prueba/service/impl/ServicioPlantillaPruebaImpl.java` | 303 | Las cinco pruebas reales, configurables |
| 66 | `prueba/controller/CalificacionPruebaController.java` | 53 | Por donde el equipo la califica |
| 67 | `prueba/service/impl/ServicioCalificacionPruebaImpl.java` | 161 | Cómo se puntúa |
| 68 | `decision/entity/BarreraCritica.java` · `BarreraDetectada.java` | 23 · 28 | Lo que frena una contratación |
| 69 | `decision/controller/DecisionPanelController.java` | 75 | Donde se registra la decisión final |
| 70 | `decision/service/impl/ServicioDecisionImpl.java` | 291 | Verde, ámbar, rojo, sin datos, reserva |

Al terminar sabrás: **qué evidencia exige el sistema antes de dejar decidir.**

## Etapa 15 · Las dos últimas etapas del embudo

Simulación de trabajo (15%) y validación práctica (15%), las dos calificadas por personas.
Migración V18.

| | Archivo | Líneas | Qué vas a ver |
|---|---|---|---|
| 71 | `simulacion/dto/DtosSimulacion.java` | 103 | Sesiones, tramos y marcas de tiempo |
| 72 | `simulacion/controller/SimulacionPortalController.java` | 52 | Lo que el candidato ve y agenda |
| 73 | `simulacion/service/ServicioDisponibilidadSimulacion.java` | 134 | Qué horarios quedan libres |
| 74 | `simulacion/service/impl/ServicioSimulacionImpl.java` | 496 | La sesión de hasta 2 h, tramo a tramo |
| 75 | `simulacion/controller/SimulacionPanelController.java` | 172 | El controlador más grande del proyecto: todo lo que hace el equipo |
| 76 | `simulacion/service/ServicioCalificacionSimulacion.java` | 72 | La nota que pone la persona |
| 77 | `validacion/controller/ValidacionPanelController.java` | 66 | Abrir, seguir y cerrar el periodo de trabajo |
| 78 | `validacion/service/impl/ServicioValidacionImpl.java` | 233 | El periodo de trabajo real, con duración configurable |

Al terminar sabrás: **por qué estas dos etapas no las califica la IA.**

## Etapa 16 · Lo transversal

Pequeño, se usa desde todas partes y explica cosas que ya viste sin entender.

| | Archivo | Líneas | Qué vas a ver |
|---|---|---|---|
| 79 | `parametro/service/ServicioParametros.java` | 48 | Casi todo es configurable: los valores viven en la base |
| 80 | `auditoria/service/ServicioAuditoria.java` | 64 | Quién hizo qué |
| 81 | `notificacion/service/ServicioCorreo.java` | 63 | Las plantillas de correo |
| 82 | `notificacion/service/impl/EnviadorCorreoLog.java` | 17 | **Hoy el correo se registra, no se envía** |
| 83 | `consentimiento/entity/Consentimiento.java` | 28 | Lo que el candidato aceptó, y en qué versión |
| 84 | `comun/programado/SondeoVencimientos.java` | 53 | Lo que corre solo cada tanto |
| 85 | `administracion/service/impl/ServicioAdministracionImpl.java` | 311 | Usuarios, roles y parámetros desde el panel |

El borrado 29733 ya no vive aquí: se mudó a
`administracion/service/impl/ServicioBorradoDatosImpl.java`, porque lo más destructivo
del sistema no comparte techo con editar un parámetro.

Al terminar sabrás: **dónde tocar cuando el cliente pide cambiar un tiempo, un peso o un texto.**

## Etapa 17 · Los tests

La forma más rápida de ver el sistema funcionando sin abrir el navegador. Corren contra un
Postgres real levantado por Testcontainers.

| Archivo | Líneas | Qué vas a ver |
|---|---|---|
| `integracion/MigracionesIT.java` | 239 | No cuenta migraciones: comprueba **lo que dejaron puesto** contra un Postgres real. Que las semillas den los 18 estados, que la V19 deje la ficha del CV sin edad ni sexo, y que los textos del banco v3 no lleguen cortados |
| `integracion/FlujoHito1IT.java` | 514 | El hito 1 entero, de una pieza |
| `integracion/FlujoEvaluacionIT.java` | 457 | El Perfil Integral |
| `integracion/FlujoPruebaIT.java` | 550 | La prueba del puesto |
| `integracion/FlujoCalificacionIaIT.java` | 1119 | La calificación con IA, con el modelo simulado. El más grande |
| `integracion/FlujoSimulacionValidacionIT.java` | 576 | Simulación y validación |
| `integracion/FlujoPerfilIT.java` | 627 | El perfil del candidato de punta a punta (etapa 19) |
| `integracion/CalificacionIaRealIT.java` | 328 | Contra DeepSeek de verdad: cuatro llamadas reales |

**Los `*IT` no los corre `./mvnw test`.** Surefire solo toma `*Test.java` y `*Tests.java`; de
los de integración se encarga failsafe en `./mvnw verify`, que necesita Docker.

Y `CalificacionIaRealIT` es la única que gasta dinero, así que **va apagada salvo que se pida**
—lleva `@EnabledIfEnvironmentVariable`—. Sin la bandera se salta en milisegundos. Para pedirla
a propósito, nombrarla basta: al nombrar la clase se salta la lista de arriba, así que la
corre tanto `test` como `verify`.

```bash
RENASER_IA_REAL=si ./mvnw verify -Dit.test=CalificacionIaRealIT
```

Se apaga por defecto y no al revés a propósito: olvidarse de encenderla no cuesta nada;
olvidarse de apagarla, sí.

Empieza por `FlujoHito1IT`: es el recorrido completo de un candidato en un solo archivo.

## Etapa 18 · El motor de agentes

`ai/`, 136 clases. **No se toca**, pero comparte proceso, base de datos y puerto. Entrada:
`FlowController` (42) → `AgentExecutionServiceImpl` (119) → los prompts en `resources/prompts/`.

Dentro viven los 15 agentes y su respuesta estructurada, el encadenamiento por RabbitMQ con
`routing[]` y tope de profundidad, y el RAG con pgvector más los embeddings de Google Gemini.

El único punto donde selección y motor se tocan de verdad es
`ai/controller/perfilintegral/AgentesIaPanelController.java` (62), la puerta que usa la etapa 13.

## Etapa 19 · El perfil del candidato

Migración V36 (25/08/2026). Es de selección, no del motor: se lee después de la 18 porque
llegó después, no porque dependa de ella. Ya no es la última: la `V40` trajo después los
inscritos de una sesión y los permisos editables, y esa no tiene etapa propia porque no añade
ninguna tabla.

Hasta aquí, todo lo que sabías de una persona vivía **dentro de su postulación**: volvía a
postular y volvía a escribirlo todo. El perfil cuelga de `persona`, así que **le sobrevive a
cada postulación**.

| | Archivo | Líneas | Qué vas a ver |
|---|---|---|---|
| 86 | `perfil/entity/PerfilCandidato.java` | 39 | La ficha de la persona, con sus cinco listas colgando |
| 87 | `perfil/dto/DtosPerfil.java` | 138 | Experiencia, educación, idiomas, certificaciones y enlaces |
| 88 | `perfil/service/ClaveNatural.java` | 28 | Por qué «Inglés» e «ingles» son el mismo idioma. Pequeño y decide mucho |
| 89 | `perfil/controller/PerfilPortalController.java` | 209 | El dueño edita lo suyo: alta, baja, orden y confirmación |
| 90 | `perfil/service/impl/PintorDePerfil.java` | 147 | Un solo armador del DTO para el portal y el panel. **Lo que ve el equipo es lo mismo que ve el candidato** |
| 91 | `perfil/service/impl/ServicioPerfilPortalImpl.java` | 487 | El CRUD entero, con el 404 de lo ajeno |
| 92 | `perfil/service/impl/ServicioPropuestaPerfilImpl.java` | 405 | Lo que la IA saca del currículum entra sin confirmar y **nunca pisa lo que puso la persona**. Los dos campos que mandan son `origen` y `confirmadoEn` |
| 93 | `perfil/service/impl/BarridoRetencionPerfil.java` | 88 | El plazo de conservación de la ley 29733: sin actividad, se borra |
| 94 | `perfil/controller/PerfilPanelController.java` | 39 | La trayectoria vista por el equipo. **No puntúa**, y la pretensión pide su propio permiso |

Al terminar sabrás: **por qué lo que lee la IA no se guarda directo, sino que espera un sí.**

---

## Lo que no existe todavía

| Hueco | Estado |
|---|---|
| Identidad del equipo | Hay un `dev-login`; el contrato con RENASER OS no existe |
| Correo | Se registra en `correo_enviado`, no se envía |
| Módulo psicométrico propio | Sin decidir por el cliente; su 5% se reparte mientras tanto |
| Reutilización entre vacantes afines | Falta que el cliente diga qué familias son afines |
