## Documentacion Generada
Generar la documentacion detallada, con palabras basicas del español, breve. Diseñado para que el usuario lo pueda leer y entender. El objetivo de los documentos no es que sean grandes, sino, entendibles.

## Respuestas
Habla en cuestion de flujos, sin tecnisismos, como para explicarle al cliente, sin mencionar partes de código amenos que sea muy necesario o la pregunta del usuario sea muy especifica del código. Si para implementar algo es necesario alguna acción por parte de mi, como por ejemplo: Ejecutar un script en la base de datos. Entonces me das una sección que se llamará "Flujo de Implementación" en la cual me darás los pasos especificos que debo de cumplir en orden para poder realizar la implementación adecuadamente. Si tengo que tomar decisiones damelos en una nueva sección que diga "Decisiones" para asi evitar confundirlo con texto normal, dentro de esa sección me dirás lo que tengo que decidir o la informacion que tengo que darte. 

Keep responses focused, brief, and concise. Keep disclaimers and caveats short, and spend most of the response on the main answer. When asked to explain something, give a high-level summary unless an in-depth explanation is specifically requested.

Before your first tool call, say in one sentence what you're about to do. While working, give a brief update only when you find something important or change direction. When you finish, lead with the outcome: your first sentence should answer "what happened" or "what did you find," with supporting detail after it for readers who want it.

## Como preguntar
No uses el vocabulario de los documentos del cliente al hacer preguntas: sus terminos son
ambiguos (ej. "corte", "gate", "avanzar"). Interpreta el significado con todo el contexto y
pregunta en palabras propias y concretas, describiendo la situacion real.

## Sistema susceptible a cambios
El cliente realiza cambios recurrentemente, ya sea en métricas, en datos, en permisos de roles, diseñar el sistema para que admita todo tipo de cambios inesperados, y que todo sea personalizable para el rol de dirección o admin. Que pueda ver y editar todo. Prioriza que el codigo tenga bajo acoplamiento y alta cohesion.

## Git
No mergees o pushees directamente, los PR y su fusión los ejecutaré yo.

## Workflow de los worktrees
En cada workflow generalmente trabajaremos un modulo distinto. Al finalizar el desarrollo lanza siempre un Agente QA que analice el código en busca de bugs, errores de lógica, errores con otros componentes preguntandose siempre si los cambios o lo implementado rompería algo en el sistema. Que ejecute los tests, pruebas de integracion y flujos. Cuando termine, se crea un PR, se espera a que el CI/CD de github actions termine, si está todo correcto sin errores entonces actualizas la Documentacion en /docs con los cambios. Una vez terminado eso me avisas para hacer yo el merge.

Al estar probando ya sea para test/pruebas o que yo quiera que ejecutes el backend creas una base de datos en docker temporal, de manera que no se toque o modifique la principal que es reaser-reclutamiento, esto con el objetivo de evitar que se apliquen migraciones y haya conflictos. Si el worktree es de frontend entonces se crea tambien pruebas e2e con playwright.

## Donde esta cada cosa
Este archivo no guarda estado ni reglas del codigo: todo eso vive en `docs/` y se lee ahi.
Empezar por [docs/README.md](docs/README.md). Lo que cambie al terminar una funcionalidad se
escribe en [docs/ESTADO-DEL-PROYECTO.md](docs/ESTADO-DEL-PROYECTO.md), no aqui.

| Si vas a... | Lee |
|---|---|
| Entender que hace el sistema, sin nada tecnico | [00-QUE-HACE-EL-SISTEMA](docs/00-QUE-HACE-EL-SISTEMA.md) |
| Saber que esta hecho, que falta y que decide Renaser | [ESTADO-DEL-PROYECTO](docs/ESTADO-DEL-PROYECTO.md) |
| Tocar codigo: paquetes, frontera con el motor de agentes, migraciones, alcance, Jackson 3, que documento actualizar | [REGLAS-DEL-CODIGO](docs/REGLAS-DEL-CODIGO.md) |
| Levantar el backend, perfiles, correr tests, backend propio por rama, donde esta el frontend, guiones | [TRABAJAR-EN-LOCAL](docs/TRABAJAR-EN-LOCAL.md) |
| Un endpoint: que hace y que permiso pide | [09-APIS](docs/09-APIS.md), y Swagger en `/swagger-ui.html` |
| Que requisito cubre algo | [01-REQUISITOS-FUNCIONALES](docs/01-REQUISITOS-FUNCIONALES.md), [02-REQUISITOS-NO-FUNCIONALES](docs/02-REQUISITOS-NO-FUNCIONALES.md), [08-ALCANCE-DEL-MVP](docs/08-ALCANCE-DEL-MVP.md) |
| Los estados de la postulacion y sus transiciones | [03-ESTADOS-POSTULACION](docs/03-ESTADOS-POSTULACION.md) |
| Permisos, roles y como se editan | [04-ROLES-Y-PERMISOS](docs/04-ROLES-Y-PERMISOS.md) |
| Una tabla o una columna | [05-MODELO-DE-DATOS](docs/05-MODELO-DE-DATOS.md), [07-DICCIONARIO-DE-DATOS](docs/07-DICCIONARIO-DE-DATOS.md) |
| La prueba del puesto y el cuestionario tecnico: componer, minutos, cierre, entregables, nota, guia de calificacion | [PRUEBA-DEL-PUESTO](docs/PRUEBA-DEL-PUESTO.md), [DISENO-PRUEBA-TECNICA-FICHA-Y-REDACTOR](docs/DISENO-PRUEBA-TECNICA-FICHA-Y-REDACTOR.md), [RUBRICA-DE-LA-PRUEBA](docs/RUBRICA-DE-LA-PRUEBA.md), [DECISION-UNA-VACANTE-UNA-VERSION](docs/DECISION-UNA-VACANTE-UNA-VERSION.md) |
| La criba de curriculums, el ranking por etapa y el desglose de la evaluacion | [CRIBA-DE-CURRICULUMS](docs/CRIBA-DE-CURRICULUMS.md) |
| Como califica la IA, los agentes y que pasa si falla | [CALIFICACION-CON-IA](docs/CALIFICACION-CON-IA.md) |
| El banco de preguntas v3, su motor y su importador | [AVANCE-BANCO-V3](docs/AVANCE-BANCO-V3-2026-08-19.md), [AVANCE-IMPORTADOR-BANCO](docs/AVANCE-IMPORTADOR-BANCO-2026-08-23.md) |
| Los bancos CAZATALENTOS y su prueba tecnica | [CAZATALENTOS-BANCO-RENASER](docs/CAZATALENTOS-BANCO-RENASER.md), [CAZATALENTOS-PRUEBA-TECNICA](docs/CAZATALENTOS-PRUEBA-TECNICA.md) |
| El perfil del candidato | [APIS-PERFIL-DEL-CANDIDATO](docs/APIS-PERFIL-DEL-CANDIDATO.md), [PROPUESTA-PERFIL-DEL-CANDIDATO](docs/PROPUESTA-PERFIL-DEL-CANDIDATO.md) |
| Multiempresa: empresas, plataforma, tope de IA, consentimiento por empresa | [APIS-MULTIEMPRESA](docs/APIS-MULTIEMPRESA.md), `docs/superpowers/specs/2026-08-25-*.md` |
| Simulacion, validacion, inscritos | [AVANCE-SIMULACION-VALIDACION](docs/AVANCE-SIMULACION-VALIDACION-2026-08-18.md) |
| Lo que esta roto, se sabe y sigue abierto | [DEFECTOS-CONOCIDOS](docs/DEFECTOS-CONOCIDOS.md) |
| Qué código está muerto, sin usar o sin conectar a una pantalla, y qué se decidió | `docs/AUDITORIA-CODIGO-2026-09-03.xlsx` (hojas Hallazgos, Endpoints y Documentación; la columna DECISIÓN la llena el usuario) |
| Seguridad, concurrencia, disponibilidad y los agentes: qué se auditó el 04/09 y qué quedó abierto | [AUDITORIA-RNF-Y-AGENTES-2026-09-04](docs/AUDITORIA-RNF-Y-AGENTES-2026-09-04.md) |
| Que comprueban los tests y las reglas de arquitectura | [COMPROBACIONES-AUTOMATICAS](docs/COMPROBACIONES-AUTOMATICAS.md) |
| La tuberia, el despliegue en AWS y las variables del servidor | [CI-CD](docs/CI-CD.md), [ARCHIVOS-EN-BUCKET](docs/ARCHIVOS-EN-BUCKET.md) |
| Que documento del cliente manda y que se decidio | [insumos/ANALISIS-DOCUMENTOS](docs/insumos/ANALISIS-DOCUMENTOS.md), [insumos/CAMBIOS-DEL-DOCUMENTO-NUEVO](docs/insumos/CAMBIOS-DEL-DOCUMENTO-NUEVO.md), [insumos/NOTAS-TEMPORALES](docs/insumos/NOTAS-TEMPORALES.md) |
| El texto de consentimiento pendiente | [BORRADOR-CONSENTIMIENTO-v1.1](docs/BORRADOR-CONSENTIMIENTO-v1.1.md) |
| Lo que se hizo un dia concreto | `docs/AVANCE-*.md`, `docs/REPORTE-*.md`, `docs/FALLOS-*.md` |
| Diagramas y mockups | `docs/diagramas/` (HTML), `docs/mockups/` (los mantiene otra persona; describen la version anterior) |

`DESIGN.md` y `PRODUCT.md` en la raiz son de la herramienta de mockups, no de este trabajo.
