**Auditoría de RNF y agentes — RENASER RECLUTAMIENTO**

Fecha: 4 de septiembre de 2026. Base: commit `06d2de4` y árbol de trabajo local. Auditoría técnica del backend, configuración de despliegue, migraciones, prompts y pruebas. No se modificó la lógica de la aplicación ni se consultaron datos de producción.

**Dictamen:** el sistema tiene controles útiles y una suite amplia, pero no hay evidencia suficiente para aprobar los seis RNF. Hay defectos concretos en aislamiento del motor de agentes generales, concurrencia y coordinación de agentes. Los agentes de selección tienen una integración funcional más completa; los 15 agentes generales están implementados como generadores de análisis estructurado con conexiones parciales, y no como trabajadores autónomos completos para todas sus misiones.

**Alcance y método**

Se consultó el grafo existente mediante Graphify, se contrastaron sus relaciones con el código y se usó la guía Java/Spring Boot. La referencia contractual es [02-REQUISITOS-NO-FUNCIONALES.md](/home/n4nd0/Documentos/RENASER-RECLUTAMIENTO/docs/02-REQUISITOS-NO-FUNCIONALES.md:1). Se distingue entre **reproducido localmente**, **confirmado por código** y **no acreditado con evidencia operativa**. P1 significa prioridad alta; P2, prioridad media. Las prioridades no son puntuaciones de CVSS.

No se hicieron llamadas pagadas a modelos, envíos reales de correo, pruebas de carga, pentest sobre el servidor, restauraciones ni análisis exhaustivo de vulnerabilidades de dependencias. No se certifica cumplimiento legal ni calidad psicométrica de las evaluaciones.

| RNF solicitado | Resultado | Controles presentes | Brecha principal |
|---|---|---|---|
| Seguridad | Parcial; bloqueo para uso multiempresa del motor general | JWT, BCrypt, permisos del panel, aislamiento del portal, archivos privados, auditoría con triggers | Las API de agentes generales/RAG/Supabase no aplican alcance por empresa ni permisos finos |
| Concurrencia | Insuficiente en operaciones críticas | Toma atómica de trabajos; barrera con bloqueo para el retrato; índice único del redactor | Sobreventa de cupos, actualizaciones de estado concurrentes y creación duplicada de trabajos por postulación |
| Disponibilidad | No acreditada para RNF-45 | Reinicio de contenedores, comprobación posterior al despliegue, persistencia en BD | Una sola instancia y un único host para aplicación/proxy/broker; respaldos y restauración sin evidencia verificada |
| Escalabilidad | Parcial; capacidad objetivo sin demostrar | Colas, consumidores configurables, hilos virtuales, bucket compartido | Límites por instancia, consultas sin paginar y fan-out que supera su presupuesto; falta ensayo de carga |
| Resiliencia | Parcial, mejor en selección | Trabajos persistidos, recuperación programada, timeouts y DLQ | Motor general sin deduplicación ni recuperación equivalente; ventanas entre persistir y publicar/encadenar |
| Mantenibilidad | Base favorable, con brechas | Dominios separados, Flyway, pruebas, ArchUnit y CI | Dos motores con garantías distintas, documentación contradictoria y pruebas ausentes para fallos reproducidos |

**Hallazgos y correcciones recomendadas**

**H01 · P1 · Seguridad: el motor general permite acceso sin aislamiento por empresa. Confirmado por código.**

La cadena exige `TIPO_EQUIPO`, pero `agent-runs` consulta corridas por ID, agente o entidad sin organización; `AgentRun` ni siquiera tiene organización propietaria. Un usuario de equipo de otra empresa puede alcanzar listados globales y la aprobación. RAG hace búsqueda vectorial sin filtro de organización, y las consultas Supabase usan una credencial de servicio compartida sin alcance derivado del usuario. La exposición de datos concretos depende del contenido almacenado, pero la ausencia del control es verificable.

Evidencia: [ConfiguracionSeguridad.java](/home/n4nd0/Documentos/RENASER-RECLUTAMIENTO/src/main/java/com/renaser/ai/ai_engine/seguridad/config/ConfiguracionSeguridad.java:82), [AgentRunServiceImpl.java](/home/n4nd0/Documentos/RENASER-RECLUTAMIENTO/src/main/java/com/renaser/ai/ai_engine/ai/service/impl/AgentRunServiceImpl.java:47), [AgentRun.java](/home/n4nd0/Documentos/RENASER-RECLUTAMIENTO/src/main/java/com/renaser/ai/ai_engine/ai/model/AgentRun.java:1), [DocumentRetrievalServiceImpl.java](/home/n4nd0/Documentos/RENASER-RECLUTAMIENTO/src/main/java/com/renaser/ai/ai_engine/ai/rag/impl/DocumentRetrievalServiceImpl.java:18), [SupabaseConfig.java](/home/n4nd0/Documentos/RENASER-RECLUTAMIENTO/src/main/java/com/renaser/ai/ai_engine/ai/config/SupabaseConfig.java:19). Afecta RNF-06c y RNF-21. Corregir propagando organización/actor por petición, trabajo y consulta; aplicar permisos de acción y filtrar metadatos del RAG. La autenticación no sustituye autorización sobre el objeto, como señala [OWASP API1](https://owasp.org/API-Security/editions/2023/en/0xa1-broken-object-level-authorization/).

**H02 · P1 · Concurrencia: dos candidatos pueden ocupar la última plaza. Confirmado por código.**

`inscribirse` lee sesión, cuenta inscripciones y luego inserta. Dos transacciones pueden leer el mismo cupo disponible e insertar ambas. No hay bloqueo de la sesión, actualización condicional de cupo ni versión optimista. El conteo SQL de `disponiblesPara` tampoco serializa reservas. Afecta RNF-42 y la operación de simulaciones.

Evidencia: [ServicioSimulacionImpl.java](/home/n4nd0/Documentos/RENASER-RECLUTAMIENTO/src/main/java/com/renaser/ai/ai_engine/simulacion/service/impl/ServicioSimulacionImpl.java:442), [SesionSimulacionRepository.java](/home/n4nd0/Documentos/RENASER-RECLUTAMIENTO/src/main/java/com/renaser/ai/ai_engine/simulacion/repository/SesionSimulacionRepository.java:1). Corregir con bloqueo de la sesión o reserva atómica. Verificar con dos transacciones reales compitiendo por una plaza: exactamente una debe confirmar.

**H03 · P1 · Concurrencia: las transiciones pueden sobrescribir una decisión concurrente. Confirmado por código.**

`MaquinaEstados.transicionar` valida el estado de la entidad recibida y después la guarda, sin `@Version` ni comparación atómica contra el estado vigente en BD. Si una operación lee antes de que otra retire/cierre al candidato, puede confirmar un cambio basado en ese estado antiguo. `@Transactional` por sí solo no protege ese escenario; los triggers de auditoría preservan el historial pero no resuelven la carrera.

Evidencia: [MaquinaEstados.java](/home/n4nd0/Documentos/RENASER-RECLUTAMIENTO/src/main/java/com/renaser/ai/ai_engine/postulacion/service/MaquinaEstados.java:164), [Postulacion.java](/home/n4nd0/Documentos/RENASER-RECLUTAMIENTO/src/main/java/com/renaser/ai/ai_engine/postulacion/entity/Postulacion.java:1). Corregir con control optimista o transición condicional `WHERE estado = esperado`, auditando solo la transición confirmada. Afecta la coherencia de decisiones y RNF-27/46.

**H04 · P1 · Concurrencia/resiliencia: la deduplicación de selección es por fila, no por trabajo lógico. Confirmado por código.**

`crearSiHaceFalta` realiza consulta y después inserción sin exclusión mutua ni índice único para postulaciones. Dos peticiones pueden crear dos filas para la misma postulación/agente/modo. La toma atómica evita ejecutar dos veces *una fila*, pero no evita ejecutar ambas filas. El índice de V42 solo aplica cuando `postulacion_id IS NULL`, por lo que protege al REDACTOR y no a estos trabajos.

Evidencia: [RegistroTrabajosIa.java](/home/n4nd0/Documentos/RENASER-RECLUTAMIENTO/src/main/java/com/renaser/ai/ai_engine/ai/service/impl/RegistroTrabajosIa.java:55), [V42__prueba_tecnica_ficha_y_redactor.sql](/home/n4nd0/Documentos/RENASER-RECLUTAMIENTO/src/main/resources/db/migration/V42__prueba_tecnica_ficha_y_redactor.sql:150). Añadir unicidad de trabajo vivo por postulación/agente/modo y resolver el conflicto de inserción. Al recuperar trabajos largos, añadir además una concesión de ejecución con identificador de intento: el sondeo devuelve a pendiente tras 15 minutos sin comprobar si el consumidor original sigue vivo.

**H05 · P1 · Agentes generales: el enrutamiento documentado no funciona con `AG-xx`. Reproducido.**

El prompt ofrece `AG-05`; `resolveAgentType` lo transforma en `AG_05` y solo compara con nombres del enum como `CLIENT_SUCCESS`. El destino se ignora. La comprobación local publicó cero mensajes con la entrada del propio contrato. Otros alias comentados, como `Finance Agent`, tampoco están cubiertos por un mapa explícito.

Evidencia: [base-system-prompt.md](/home/n4nd0/Documentos/RENASER-RECLUTAMIENTO/src/main/resources/prompts/base-system-prompt.md:75), [AgentHandoffPublisher.java](/home/n4nd0/Documentos/RENASER-RECLUTAMIENTO/src/main/java/com/renaser/ai/ai_engine/ai/messaging/AgentHandoffPublisher.java:73). Definir un catálogo único `AG-00`…`AG-14` ↔ enum, validar la salida y probar los 15 identificadores.

**H06 · P1 · Escalabilidad: el máximo de corridas no es global. Reproducido.**

Cada mensaje transporta un contador que las ramas incrementan independientemente. La comprobación con cinco destinos por nodo produjo **31 corridas**, incluida la raíz, aunque `MAX_AGENT_RUNS=6`. Esto aumenta consumo del modelo y ocupación de consumidores. El límite de profundidad sí corta, pero no cumple el presupuesto total anunciado.

Evidencia: [AgentHandoffPublisher.java](/home/n4nd0/Documentos/RENASER-RECLUTAMIENTO/src/main/java/com/renaser/ai/ai_engine/ai/messaging/AgentHandoffPublisher.java:38), [AgentChainLimits.java](/home/n4nd0/Documentos/RENASER-RECLUTAMIENTO/src/main/java/com/renaser/ai/ai_engine/ai/messaging/AgentChainLimits.java:1). Reservar cupos de ejecución de forma atómica por `flowId`, antes de crear/publicar cada hijo; probar fan-out concurrente y redelivery.

**H07 · P1 · Agentes generales: dependencias e intercambio de resultados incompletos. Dependencias reproducidas; contexto confirmado por código.**

`dependsOn` existe en el contrato pero el publicador solo ordena por prioridad: publica incluso si una dependencia está pendiente. Además, el hijo recibe tipo, entidad y objetivo original; su `AgentRunRequest` no transporta el resultado del padre, y el resolver no consulta `parentRunId`. La traza almacena parentesco, pero el razonamiento del siguiente agente no recibe automáticamente el análisis anterior.

Evidencia: [RoutingItem.java](/home/n4nd0/Documentos/RENASER-RECLUTAMIENTO/src/main/java/com/renaser/ai/ai_engine/ai/dto/RoutingItem.java:1), [AgentHandoffListener.java](/home/n4nd0/Documentos/RENASER-RECLUTAMIENTO/src/main/java/com/renaser/ai/ai_engine/ai/messaging/AgentHandoffListener.java:25), [AgentContextResolverImpl.java](/home/n4nd0/Documentos/RENASER-RECLUTAMIENTO/src/main/java/com/renaser/ai/ai_engine/ai/context/impl/AgentContextResolverImpl.java:40). Persistir dependencias y un insumo versionado con resultados/evidencia autorizada; ejecutar únicamente cuando estén satisfechas. Actualmente esto limita el uso como proceso colaborativo de varios agentes.

**H08 · P1 · Resiliencia: el motor general repite ejecuciones y puede dejar corridas pendientes. Repetición reproducida; publicación confirmada por código.**

`completeExecution` no comprueba que la corrida ya tenga resultado ni la reclama de forma atómica: dos entregas del mismo mensaje volvieron a invocar al modelo. `enqueue` guarda y publica sin un outbox. En las llamadas internas a la sobrecarga, `@Transactional` no se aplica a través del proxy; en el camino invocado desde otro bean, la publicación ocurre dentro de la transacción y puede adelantarse al commit. No existe un recuperador de `agent_run` equivalente al de `trabajo_ia`.

Evidencia: [AgentExecutionServiceImpl.java](/home/n4nd0/Documentos/RENASER-RECLUTAMIENTO/src/main/java/com/renaser/ai/ai_engine/ai/service/impl/AgentExecutionServiceImpl.java:71), [AgentExecutionServiceImpl.java](/home/n4nd0/Documentos/RENASER-RECLUTAMIENTO/src/main/java/com/renaser/ai/ai_engine/ai/service/impl/AgentExecutionServiceImpl.java:95), [AgentExecutionRequestPublisher.java](/home/n4nd0/Documentos/RENASER-RECLUTAMIENTO/src/main/java/com/renaser/ai/ai_engine/ai/messaging/AgentExecutionRequestPublisher.java:1). Usar estados reclamables, deduplicación, outbox y confirmaciones de publicación. Véanse las reglas de [transacciones de Spring](https://docs.spring.io/spring-framework/reference/data-access/transaction/declarative/annotations.html) y [fiabilidad de RabbitMQ](https://www.rabbitmq.com/docs/reliability).

En selección hay una ventana adicional: después de marcar un paso `TERMINADO` y antes de crear su retrato dependiente. Una caída ahí deja una etapa incompleta; el sondeo solo busca pendientes, en curso y en espera, no dependencias faltantes de trabajos terminados. Revisar [ColaCalificacionIaImpl.java](/home/n4nd0/Documentos/RENASER-RECLUTAMIENTO/src/main/java/com/renaser/ai/ai_engine/ai/service/impl/ColaCalificacionIaImpl.java:250) y reconciliar también el flujo de negocio.

**H09 · P1 · Calidad/seguridad de selección: la anonimización elimina evidencia válida y deja datos familiares. Reproducido.**

Entrada sintética: `Tengo 10 años de experiencia en Java. Con 2 hijos.` Salida real: `Tengo [DATO NO UTILIZABLE] de experiencia en Java. Con 2 hijos.` El patrón confunde duración laboral con edad y no cubre una forma común de dato familiar. La lista de patrones es estática pese a que RNF-16 pide configuración. No se afirma anonimización completa de nombres/contactos: DATOS_CV está diseñado para extraerlos.

Evidencia: [AnonimizadorCv.java](/home/n4nd0/Documentos/RENASER-RECLUTAMIENTO/src/main/java/com/renaser/ai/ai_engine/postulacion/service/impl/AnonimizadorCv.java:69). Separar edad de experiencia con contexto, probar variantes del español y documentar una política configurable de ocultación. Validar los insumos que realmente llegan al proveedor, no solo frases aisladas. Afecta RNF-16/17 y la calidad de DATOS_CV/EVIDENCIA_CV.

**H10 · P2 · Función del evaluador: la recalificación solicitada no se crea. Reproducido.**

`reencolarEvaluador` permite continuar cuando el anterior está terminado, pero llama `crearSiHaceFalta` con `alimentadoPor=null`; este rechaza el terminado. El método devolvió `false` sin crear una nueva ejecución. Su comentario promete expresamente recalibrar aunque ya esté terminado.

Evidencia: [ColaCalificacionIaImpl.java](/home/n4nd0/Documentos/RENASER-RECLUTAMIENTO/src/main/java/com/renaser/ai/ai_engine/ai/service/impl/ColaCalificacionIaImpl.java:144), [RegistroTrabajosIa.java](/home/n4nd0/Documentos/RENASER-RECLUTAMIENTO/src/main/java/com/renaser/ai/ai_engine/ai/service/impl/RegistroTrabajosIa.java:214). Introducir una operación explícita de recalificación versionada que permita un terminado previo, impida dos trabajos vivos y regenere después el retrato dependiente.

**H11 · P2 · Gobierno de agentes: Human Gate es información, no un flujo de autorización completo. Reproducido y confirmado por código.**

Se persiste `required`, pero se publica fan-out aun con `required=true` y `approved=false`. `approve` solo cambia un booleano: no comprueba el rol indicado por el modelo, no registra aprobador/fecha/motivo y no reanuda una acción suspendida. El estado del flujo tampoco distingue espera de aprobación.

Evidencia: [AgentRunServiceImpl.java](/home/n4nd0/Documentos/RENASER-RECLUTAMIENTO/src/main/java/com/renaser/ai/ai_engine/ai/service/impl/AgentRunServiceImpl.java:67), [AgentExecutionServiceImpl.java](/home/n4nd0/Documentos/RENASER-RECLUTAMIENTO/src/main/java/com/renaser/ai/ai_engine/ai/service/impl/AgentExecutionServiceImpl.java:41), [FlowServiceImpl.java](/home/n4nd0/Documentos/RENASER-RECLUTAMIENTO/src/main/java/com/renaser/ai/ai_engine/ai/flow/impl/FlowServiceImpl.java:82). **Continuar análisis de lectura puede ser legítimo porque el gate es por acción.** No se encontró ejecución de herramientas de escritura: la reproducción no demuestra un pago, contratación o mensaje no autorizado. Antes de conectar esas acciones se necesita autorización determinística por acción y auditoría; `approved=true` no acredita aprobación válida.

**H12 · P1 · Disponibilidad: la topología versionada tiene puntos únicos de fallo. Confirmado en configuración; SLA no medido.**

Compose define una aplicación, un proxy y un broker en el mismo host. Un fallo del host o la recreación de la aplicación interrumpe el servicio. `restart: always` ayuda a recuperar, pero no ofrece continuidad de una simulación de dos horas. El despliegue comprueba una ruta de negocio y sale con error si falla, sin volver automáticamente a la imagen anterior. No se halló monitorización operativa suficiente para acreditar RNF-45.

Evidencia: [docker-compose.yml](/home/n4nd0/Documentos/RENASER-RECLUTAMIENTO/despliegue/docker-compose.yml:15), [desplegar.sh](/home/n4nd0/Documentos/RENASER-RECLUTAMIENTO/despliegue/desplegar.sh:32). Definir objetivos medibles de disponibilidad, RTO y RPO; probar fallo/reinicio con examen en curso; separar dominios de fallo y desplegar con capacidad de continuidad acorde al requisito. **RNF-47/48 no acreditados:** no se verificaron copias diarias, retención de 30 días ni restauración/PITR de Supabase. La ausencia de evidencia en este repositorio no prueba que el servicio contratado carezca de ellas.

**H13 · P2 · Rendimiento/escalabilidad: el contrato de archivos y la capacidad no están validados. Confirmado y no medido, respectivamente.**

RNF-44 permite archivos de 200 MB; la configuración establece 10 MB por archivo y 12 MB por petición. La entrega usa el almacén que materializa `MultipartFile.getBytes()`: aumentar solo el límite elevaría memoria por subida concurrente. Las consultas de historial de agentes y varias bandejas retornan listas sin paginación; el contador de login también vive en memoria local sin expiración/eliminación general de claves. Añadir instancias divide ese límite de intentos y reiniciar lo borra.

Evidencia: [application.yaml](/home/n4nd0/Documentos/RENASER-RECLUTAMIENTO/src/main/resources/application.yaml:53), [AlmacenArchivosSupabase.java](/home/n4nd0/Documentos/RENASER-RECLUTAMIENTO/src/main/java/com/renaser/ai/ai_engine/archivo/service/impl/AlmacenArchivosSupabase.java:92), [IntentosLogin.java](/home/n4nd0/Documentos/RENASER-RECLUTAMIENTO/src/main/java/com/renaser/ai/ai_engine/seguridad/service/IntentosLogin.java:18). Usar subida en streaming o directa firmada, paginación y limitación compartida con TTL. No hay resultados de esta auditoría que acrediten panel <2 s, guardado <500 ms, 50 simulaciones simultáneas o evaluación <10 min (RNF-40…43).

**H14 · P2 · Resiliencia: SMTP se ejecuta dentro del flujo de negocio. Confirmado por código.**

La transición llama al correo antes de terminar su transacción; el servicio invoca el transporte síncronamente y guarda el resultado. Con SMTP lento, se retienen recursos de la operación y conexiones a BD. Si SMTP acepta el mensaje y luego la transacción revierte, puede enviarse un aviso sin que el estado correspondiente quede confirmado. No se encontró un consumidor que reintente automáticamente las entregas fallidas de correo.

Evidencia: [MaquinaEstados.java](/home/n4nd0/Documentos/RENASER-RECLUTAMIENTO/src/main/java/com/renaser/ai/ai_engine/postulacion/service/MaquinaEstados.java:217), [ServicioCorreo.java](/home/n4nd0/Documentos/RENASER-RECLUTAMIENTO/src/main/java/com/renaser/ai/ai_engine/notificacion/service/ServicioCorreo.java:26). Usar outbox transaccional de correo, entrega posterior al commit y reintento idempotente. Los timeouts existentes limitan la espera, pero no resuelven estas garantías.

**H15 · P2 · Configuración: arrancar sin perfil explícito activa el login de desarrollo. Confirmado; condicionado al arranque.**

`spring.profiles.default=local` selecciona un perfil que activa `dev-login-activo=true`. El proveedor de desarrollo autentica por identificador de RENASER OS sin contraseña. El Compose auditado fija `pruebas`, por lo que **este hallazgo no afirma que ese despliegue tenga el login abierto**; el riesgo aparece en un arranque manual o nuevo despliegue que omita el perfil. También existe fallback HTTP si no se configura dominio.

Evidencia: [application.yaml](/home/n4nd0/Documentos/RENASER-RECLUTAMIENTO/src/main/resources/application.yaml:15), [application-local.yaml](/home/n4nd0/Documentos/RENASER-RECLUTAMIENTO/src/main/resources/application-local.yaml:35), [ProveedorIdentidadEquipoDesarrollo.java](/home/n4nd0/Documentos/RENASER-RECLUTAMIENTO/src/main/java/com/renaser/ai/ai_engine/seguridad/service/impl/ProveedorIdentidadEquipoDesarrollo.java:1), [docker-compose.yml](/home/n4nd0/Documentos/RENASER-RECLUTAMIENTO/despliegue/docker-compose.yml:175). Hacer que los valores por defecto fallen de forma segura y exigir activación explícita del acceso de desarrollo. Afecta RNF-19/21/26.

**H16 · P2 · Mantenibilidad y trazabilidad: dos estándares técnicos y un contrato desactualizado. Confirmado por código/documentación.**

El motor de selección guarda entradas, respuesta, versión de instrucción, proveedor y coste. El general guarda la respuesta estructurada y una versión constante `v2`, pero no el prompt/contexto exactos enviados ni uso/coste del proveedor en `AgentRun`. Tener JSON tipado tampoco valida semánticamente referencias, evidencias, confianza o acciones. Las pruebas de arquitectura son valiosas, pero no sustituyen las pruebas de autorización/concurrencia/enrutamiento faltantes.

Evidencia: [EjecutorAgenteIaImpl.java](/home/n4nd0/Documentos/RENASER-RECLUTAMIENTO/src/main/java/com/renaser/ai/ai_engine/ai/service/impl/EjecutorAgenteIaImpl.java:125), [AgentInvokerImpl.java](/home/n4nd0/Documentos/RENASER-RECLUTAMIENTO/src/main/java/com/renaser/ai/ai_engine/ai/service/impl/AgentInvokerImpl.java:96), [AgentRun.java](/home/n4nd0/Documentos/RENASER-RECLUTAMIENTO/src/main/java/com/renaser/ai/ai_engine/ai/model/AgentRun.java:1). Unificar garantías compartidas sin obligar a fusionar ambos dominios. Versionar prompts y entradas efectivas; medir calidad con ejemplos etiquetados y revisión humana.

El documento RNF contiene contradicciones: RNF-10 exige datos locales sin terceros, mientras otros apartados autorizan DeepSeek/Gemini; RNF-06/20 describe identidad externa sin contraseñas del equipo y el código también implementa cuentas locales. Debe aprobarse una única especificación vigente antes de declarar conformidad. No se debe presentar una decisión de diseño posterior como si cumpliera automáticamente el requisito antiguo.

**¿Los agentes están correctamente implementados para su función?**

**Selección: integración sustancial, conformidad parcial.** Las clases ejecutan trabajo real mediante insumos, modelo y persistencia de resultados. Son componentes especializados de un proceso controlado por el backend; no necesitan ser agentes autónomos para cumplir esa función. Los problemas H04/H08/H09/H10 impiden calificarlos como completamente correctos. Que una prueba con dobles pase tampoco demuestra precisión de notas o ausencia de sesgos del modelo real.

| Agente de selección | Función implementada | Evaluación |
|---|---|---|
| DATOS_CV | Extrae ficha y trayectoria; no puntúa | Conectado. H09 puede quitar experiencia útil; revisar extracción con CV representativos |
| EVIDENCIA_CV | Puntúa criterios con explicaciones a partir del CV | Conectado. Recibe el mismo texto afectado por H09; validar cobertura y evidencia de cada criterio |
| EVALUADOR | Evalúa respuestas abiertas; el backend calcula criterios cerrados | Conectado. H10 rompe recalificación; existe comprobación de pertenencia de respuestas y protección de ajustes humanos |
| POTENCIAL_RIESGO | Construye Perfil de Talento y cierra la etapa correspondiente | Conectado. Usa barrera para insumos; si alguno falla puede continuar con evidencia parcial, lo que requiere calibración y señalización al revisor |
| PRUEBA_PUESTO | Evalúa criterios asignados a IA sobre entregables/respuestas | Conectado. Respeta verificación humana y acota lectura; no equivale a ejecutar programas o verificar todo formato/enlace externo |
| EVALUADOR_TECNICO | Califica el cuestionario técnico de la postulación | Conectado. Reutiliza validaciones/cálculo del backend; falta evaluación empírica de calidad del modelo |
| SIMULACION | Genera preguntas para conversación final | Conectado. No califica la sesión; su salida es un guion para una persona |
| REDACTOR | Genera preguntas técnicas desde ficha de vacante | Conectado, con validación de receta y reintento correctivo; dispone de unicidad de trabajo vivo por vacante |

Evidencia funcional: [AgenteDatosCv.java](/home/n4nd0/Documentos/RENASER-RECLUTAMIENTO/src/main/java/com/renaser/ai/ai_engine/ai/service/impl/AgenteDatosCv.java:1), [AgenteEvidenciaCv.java](/home/n4nd0/Documentos/RENASER-RECLUTAMIENTO/src/main/java/com/renaser/ai/ai_engine/ai/service/impl/AgenteEvidenciaCv.java:1), [AgenteEvaluador.java](/home/n4nd0/Documentos/RENASER-RECLUTAMIENTO/src/main/java/com/renaser/ai/ai_engine/ai/service/impl/AgenteEvaluador.java:1), [AgentePotencialRiesgo.java](/home/n4nd0/Documentos/RENASER-RECLUTAMIENTO/src/main/java/com/renaser/ai/ai_engine/ai/service/impl/AgentePotencialRiesgo.java:1), [AgentePruebaPuesto.java](/home/n4nd0/Documentos/RENASER-RECLUTAMIENTO/src/main/java/com/renaser/ai/ai_engine/ai/service/impl/AgentePruebaPuesto.java:1), [AgenteCuestionarioTecnico.java](/home/n4nd0/Documentos/RENASER-RECLUTAMIENTO/src/main/java/com/renaser/ai/ai_engine/ai/service/impl/AgenteCuestionarioTecnico.java:1), [AgenteSimulacion.java](/home/n4nd0/Documentos/RENASER-RECLUTAMIENTO/src/main/java/com/renaser/ai/ai_engine/ai/service/impl/AgenteSimulacion.java:1), [AgenteRedactor.java](/home/n4nd0/Documentos/RENASER-RECLUTAMIENTO/src/main/java/com/renaser/ai/ai_engine/ai/service/impl/AgenteRedactor.java:1).

**Agentes generales de RENASER OS: implementación parcial respecto de sus misiones.** Los 15 tienen tipo, prompt y payload. Solo 8 tienen proveedor de contexto específico registrado. No se encontró conexión de herramientas de escritura ni un motor que ejecute `nextActions.toolProposal`. El prompt base reconoce que faltan herramientas operativas y motores determinísticos. Por ello pueden producir análisis y propuestas, pero no acreditar por sí solos ejecución, seguimiento o cierre de procesos. El agente general TALENT_INTELLIGENCE no es el conjunto de ocho agentes de selección anterior.

| Agente general | Contexto específico conectado | Límite para su misión |
|---|---|---|
| ORCHESTRATOR | No | Routing con H05/H06/H07; no coordina correctamente el contrato completo |
| CEO | No | Recibe objetivo; no estado integral operativo verificable |
| AUDITOR | Entregables pendientes | Revisión parcial; listado no equivale a contenido completo ni verificación del entregable |
| DIAGNOSTIC | No | Sin datos de diagnóstico operativos dedicados |
| OPERATIONS | Actividades bloqueadas | No ejecuta ni valida capacidad/calendario o cambios de tareas |
| CLIENT_SUCCESS | No | Sin vista Customer 360 dedicada |
| FINANCE | No | Sin estado financiero/forecast verificable conectado |
| COLLECTIONS | Cobros por cliente | Puede proponer; no envía ni realiza conciliación/cobro |
| TALENT_INTELLIGENCE | No | Headcount Gate, sourcing y Quality of Hire no conectados en este motor |
| GROWTH | Prospectos agregados | La consulta limita a 1.000 y el contexto lo presenta como total: cifras parciales cuando se supera ese volumen |
| EVENT | Eventos por nombre | Consulta puntual; no operación completa de eventos |
| CONSULTING | Motores estratégicos | Interpreta sus datos; no implementa motores determinísticos |
| KNOWLEDGE | Recuperación vectorial | Búsqueda conectada; requiere aislamiento de H01 y evaluación de pertinencia |
| QA_GOVERNANCE | No | Sin controles operativos propios ni ejecución de medidas |
| NARRATIVE_MESSAGE | Avisos activos | Redacta; no demuestra entrega del mensaje |

Evidencia: [AgentType.java](/home/n4nd0/Documentos/RENASER-RECLUTAMIENTO/src/main/java/com/renaser/ai/ai_engine/ai/model/AgentType.java:1), [AgentContextResolverImpl.java](/home/n4nd0/Documentos/RENASER-RECLUTAMIENTO/src/main/java/com/renaser/ai/ai_engine/ai/context/impl/AgentContextResolverImpl.java:1), [base-system-prompt.md](/home/n4nd0/Documentos/RENASER-RECLUTAMIENTO/src/main/resources/prompts/base-system-prompt.md:81), [ProspectosContextProvider.java](/home/n4nd0/Documentos/RENASER-RECLUTAMIENTO/src/main/java/com/renaser/ai/ai_engine/ai/context/impl/ProspectosContextProvider.java:52), [SupabaseDataServiceImpl.java](/home/n4nd0/Documentos/RENASER-RECLUTAMIENTO/src/main/java/com/renaser/ai/ai_engine/ai/supabase/impl/SupabaseDataServiceImpl.java:25).

**Pruebas ejecutadas y límites de evidencia**

- Unitarias: **1.032 reportadas; 0 fallos, 0 errores, 1 omitida**. `BUILD SUCCESS`. Primera ejecución con autoadjunción de Mockito falló por restricciones del entorno; se repitió cargando Mockito con `-javaagent`, sin cambiar el proyecto. No se atribuyen esos errores iniciales a la aplicación.
- Integración: **19/19 correctas**, 8 de `SeguridadAgentesIaIT` y 11 de `FlujoDosEmpresasIT`. PostgreSQL/pgvector y RabbitMQ efímeros en Docker; configuración de secretos desactivada, clave ficticia y correo de log. Estas suites comprueban las rutas/escenarios que contienen, no el aislamiento multiempresa del motor general de H01.
- Siete comprobaciones focalizadas ejecutaron clases reales con colaboradores simulados y entradas sintéticas: identificadores AG, dependencias, presupuesto del flujo, gate informativo, redelivery, anonimización y recalificación. Todos los comportamientos descritos se reprodujeron. No son siete arreglos ni siete pruebas de cumplimiento: son evidencia de defectos/limitaciones que sobreviven a la suite existente.
- No se midieron cobertura global nueva ni latencias/throughput; no se ejecutaron todas las suites de integración, las pruebas de modelo real ni una batería estadística de evaluación de IA. Las carreras de BD descritas requieren pruebas paralelas específicas para cuantificar su frecuencia.

Las [instrucciones para reproducir las comprobaciones](/home/n4nd0/Documentos/RENASER-RECLUTAMIENTO/docs/auditoria-2026-09-04/README.md) y los [resultados resumidos](/home/n4nd0/Documentos/RENASER-RECLUTAMIENTO/docs/auditoria-2026-09-04/resultados.txt) se entregan junto a este informe. No contienen CV reales, credenciales ni mensajes destinados a personas.

**Orden recomendado y criterios de aceptación**

1. **Cerrar aislamiento y proteger decisiones:** H01, H02 y H03. Dos empresas deben obtener 403/404 al consultar/aprobar recursos ajenos; dos candidatos por una plaza deben producir una sola inscripción; una transición obsoleta debe rechazarse sin sobrescribir estado ni emitir aviso contradictorio.
2. **Corregir el procesamiento de IA:** H04…H10. Todos los AG-xx deben resolverse, cada flujo debe respetar seis corridas globales, las dependencias deben esperar, duplicar mensajes no debe duplicar inferencias/efectos y un reinicio entre pasos debe recuperarse. La recalificación debe crear exactamente un nuevo trabajo y renovar su retrato.
3. **Completar gobierno y continuidad:** H11/H12/H14/H15. Aprobación por acción con rol y auditoría; entrega de correo independiente; recuperación ensayada, respaldos/restauración demostrables, configuración de producción segura y métricas/alertas útiles.
4. **Acreditar capacidad y calidad:** H13/H16. Ensayar 50 candidatos, hasta 500 postulantes por vacante y 10 vacantes con datos ficticios; registrar latencias y errores contra RNF-40…44. Medir calificación desde encolado hasta resultado, también bajo carga y fallo del proveedor. Aprobar objetivos de disponibilidad/RTO/RPO y una evaluación humana de las salidas de cada agente antes de automatizar decisiones relevantes.

El resultado de esta auditoría es un diagnóstico con evidencia reproducible y prioridades; las correcciones quedan pendientes de implementación.
