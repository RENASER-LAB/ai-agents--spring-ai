# Documentación · Sistema de selección Renaser

Backend en Java + Spring Boot para el módulo de selección de personal de Renaser Consulting.
El frontend es un repositorio aparte, `RenaserOsPostulantes` (React con Vite), con dos caras:
el portal del candidato y el panel de la empresa. Ambas llaman a este backend por su API.

**Si vas a tocar código, tres documentos antes que nada**: [Estado del proyecto](ESTADO-DEL-PROYECTO.md)
(qué está hecho y qué falta), [Reglas del código](REGLAS-DEL-CODIGO.md) (cómo está organizado y
qué no se negocia) y [Trabajar en local](TRABAJAR-EN-LOCAL.md) (levantarlo, perfiles, tests).

---

## Por dónde empezar

**Lee primero [Qué hace el sistema](00-QUE-HACE-EL-SISTEMA.md).** Son cinco minutos y no tiene
una sola palabra técnica: qué problema resuelve y qué le pasa a un candidato de principio a fin.
Sirve también para enseñárselo al cliente.

Después, [Requisitos funcionales](01-REQUISITOS-FUNCIONALES.md) desarrolla cada cosa de ese
recorrido, y [Alcance del MVP](08-ALCANCE-DEL-MVP.md) dice cuál de ellas se construye primero.

Si vienes de la versión anterior, empieza por
[Qué cambia con el documento nuevo](insumos/CAMBIOS-DEL-DOCUMENTO-NUEVO.md): el cliente mandó
requisitos nuevos el 14 de agosto y cambian bastante.

---

## Los documentos

| Documento | Qué contiene |
|---|---|
| [00 · Qué hace el sistema](00-QUE-HACE-EL-SISTEMA.md) | El sistema entero sin nada técnico. Cinco minutos |
| [01 · Requisitos funcionales](01-REQUISITOS-FUNCIONALES.md) | RF-01 a RF-155. Qué hace el sistema |
| [02 · Requisitos no funcionales](02-REQUISITOS-NO-FUNCIONALES.md) | RNF-01 a RNF-66. Tecnología, seguridad, rendimiento |
| [03 · Estados de la postulación](03-ESTADOS-POSTULACION.md) | Los 18 estados de una postulación y sus transiciones |
| [04 · Roles y permisos](04-ROLES-Y-PERMISOS.md) | Los 77 permisos, acción por acción. En la base hay 71 sembrados |
| [05 · Modelo de datos](05-MODELO-DE-DATOS.md) | Las 106 tablas (V1-V51) por área y por qué el modelo es así. Se lee |
| [06 · Inventario de pantallas](06-INVENTARIO-DE-PANTALLAS-MOCKUPS.md) | Las 21 pantallas base, estados, ventanas, campos y datos de los mockups |
| [07 · Diccionario de datos](07-DICCIONARIO-DE-DATOS.md) | Cada tabla con todas sus columnas, tipos y claves. Se consulta |
| [08 · Alcance del MVP](08-ALCANCE-DEL-MVP.md) | Qué se construye primero, en tres hitos, y qué queda fuera |
| [09 · Las APIs](09-APIS.md) | Las dos puertas, cómo entrar y qué hace cada endpoint. La referencia viva es Swagger |
| [Estado del proyecto](ESTADO-DEL-PROYECTO.md) | **El presente**: hitos, tests, agentes, lo que falta de código y lo que decide Renaser. Se corrige, no se acumula |
| [Reglas del código](REGLAS-DEL-CODIGO.md) | Los dos módulos, los paquetes de dominio, la frontera con el motor de agentes, y las reglas que no se negocian (estados, alcance, migraciones, Jackson 3). Qué documento se actualiza con cada cambio |
| [Trabajar en local](TRABAJAR-EN-LOCAL.md) | Perfiles de configuración, arrancar el backend, correr los tests, un backend propio por rama, dónde están las pantallas, los guiones de `scripts/` |
| [Curso del backend](CURSO-BACKEND.md) | Ruta para entender el código que existe, en orden. Para quien entra al proyecto |
| [Calificación con IA](CALIFICACION-CON-IA.md) | Cómo la IA lee el currículum, califica lo abierto y arma el Perfil de Talento. Y qué pasa si falla |
| [La rúbrica de la prueba](RUBRICA-DE-LA-PRUEBA.md) | Cómo se reparten los 100 puntos de una prueba y **quién pone cada nota**: el sistema, un agente o una persona |
| [La prueba del puesto, por dentro](PRUEBA-DEL-PUESTO.md) | Los dos instrumentos de la etapa técnica; componer una versión antes de publicarla; la guía de calificación; los minutos de la vacante; el cierre en dos niveles; la vacante sin banco; la nota de la etapa; lo que el panel ve de los entregables |
| [Diseño de la prueba técnica](DISENO-PRUEBA-TECNICA-FICHA-Y-REDACTOR.md) · [Una vacante, una versión](DECISION-UNA-VACANTE-UNA-VERSION.md) | La ficha del puesto (las 10 preguntas al dueño) y el redactor que arma el cuestionario; y por qué una vacante rinde una sola versión |
| [CAZATALENTOS · banco](CAZATALENTOS-BANCO-RENASER.md) · [prueba técnica](CAZATALENTOS-PRUEBA-TECNICA.md) | Los bancos por nivel del método CAZATALENTOS (V41) y su prueba técnica |
| [Criba de currículums](CRIBA-DE-CURRICULUMS.md) | Cargar una convocatoria con una carpeta de currículums, pedir que la IA los lea y ver quién es el más apto |
| [Fallos corregidos de la criba](FALLOS-CORREGIDOS-CRIBA.md) | Los cinco fallos que salieron al pasar 190 currículums reales. Cuatro no daban error |
| [Los siete fallos de la auditoría](FALLOS-CORREGIDOS-AUDITORIA.md) | Lo que encontró la revisión del 18/08: cuatro que tocaban dinero o decisiones de contratación, y ninguno daba error |
| [Comprobaciones automáticas](COMPROBACIONES-AUTOMATICAS.md) | Qué se comprueba solo: las pruebas (1150 el 02/09/2026; 1248 esperadas tras los commits del 03 al 06/09), las reglas de arquitectura y Semgrep |
| [Defectos conocidos](DEFECTOS-CONOCIDOS.md) | Lo que está roto, se sabe y sigue abierto: qué le pasa a una persona de verdad y qué haría falta para arreglarlo |
| `AUDITORIA-CODIGO-2026-09-03.xlsx` | La auditoría de código muerto, sin usar y sin conectar, backend y frontend: una fila por pieza, qué pasa en palabras del cliente, y la columna DECISIÓN para elegir qué se borra, qué se conecta y qué se queda. Puesta al día el 05/09 con los commits del 04/09 |
| [Auditoría de RNF y agentes](AUDITORIA-RNF-Y-AGENTES-2026-09-04.md) | La revisión del 04/09 de seguridad, concurrencia, disponibilidad, escalabilidad, resiliencia y mantenibilidad, con sus hallazgos P1/P2 y la evidencia reproducible en `auditoria-2026-09-04/` |
| [Banco de preguntas v3](AVANCE-BANCO-V3-2026-08-19.md) | El banco nuevo del cliente: 190 ítems, ocho formatos y su motor de puntuación. Qué está hecho y qué falta |
| [El perfil del candidato](PROPUESTA-PERFIL-DEL-CANDIDATO.md) | El perfil único de la persona: requisitos, historias y tablas. Implementado; los RF esperan la validación de Renaser |
| [APIs del perfil](APIS-PERFIL-DEL-CANDIDATO.md) | El contrato para quien construya las pantallas del perfil, con las reglas que Swagger no cuenta; desde el 05/09 también foto, portada, currículum propio y diplomas |
| [APIs del multiempresa](APIS-MULTIEMPRESA.md) | Qué cambia para el frontend con las empresas: lo que rompe (postular exige aceptar el tratamiento), los dos logins y la plataforma |
| Multiempresa · [Renaser dueña de la plataforma](superpowers/specs/2026-08-25-renaser-duena-de-la-plataforma-design.md) · [qué es de cada empresa](superpowers/specs/2026-08-25-instrumental-por-empresa-design.md) · [el aislamiento](superpowers/specs/2026-08-25-aislamiento-entre-empresas-design.md) · [la identidad y el alta](superpowers/specs/2026-08-25-identidad-y-alta-de-empresas-design.md) · [el candidato ante varias empresas](superpowers/specs/2026-08-25-el-candidato-ante-varias-empresas-design.md) · [el coste de la IA por empresa](superpowers/specs/2026-08-25-el-coste-de-la-ia-por-empresa-design.md) | Las seis piezas del cambio a plataforma (25/08): la plataforma como dato, banderas de personalización con su resolutor, lo ajeno responde «no existe», el panel entra con correo y contraseña por invitación, el consentimiento por empresa, y el tope de gasto de IA |
| [El perfil, para Renaser](PARA-RENASER-EL-PERFIL-DEL-CANDIDATO.md) | La explicación sin tecnicismos, para decidir si merece la pena |
| [CI/CD](CI-CD.md) | Qué corre en cada cambio, cómo se despliega a Pruebas en AWS y qué variables de entorno hacen falta |
| [Conectar la base a Supabase](CONEXION-SUPABASE.md) | **Retirado.** El perfil `supabase` se borró el 21/08: apuntaba tu máquina a la única base que hay, la de producción. Queda la explicación de por qué |
| [Los currículums dejan de vivir en el backend](ARCHIVOS-EN-BUCKET.md) | El bucket, los dos enlaces firmados, y por qué un PDF en el disco del backend se pierde en el primer despliegue |

### Lo que se hizo cada día

Documentos de avance y reportes, del más nuevo al más viejo. Cuentan lo que pasó ese día; el
estado presente está en [Estado del proyecto](ESTADO-DEL-PROYECTO.md).

| Fecha | Documento |
|---|---|
| 04/09 | [REPORTE-CAMBIOS-2026-09-04](REPORTE-CAMBIOS-2026-09-04.md), [REPORTE-COMPLETO-CAMBIOS-2026-09-04](REPORTE-COMPLETO-CAMBIOS-2026-09-04.md), [AUDITORIA-RNF-Y-AGENTES-2026-09-04](AUDITORIA-RNF-Y-AGENTES-2026-09-04.md) |
| 03/09 | [REPORTE-CAMBIOS-2026-09-03](REPORTE-CAMBIOS-2026-09-03.md), `AUDITORIA-CODIGO-2026-09-03.xlsx` |
| 01/09 | [REPORTE-CAMBIOS-2026-09-01](REPORTE-CAMBIOS-2026-09-01.md) |
| 31/08 | [REPORTE-WORKTREES-2026-08-31](REPORTE-WORKTREES-2026-08-31.md) |
| 28/08 | [REPORTE-CAMBIOS-2026-08-28](REPORTE-CAMBIOS-2026-08-28.md) |
| 27/08 | [REPORTE-TRABAJO-2026-08-27](REPORTE-TRABAJO-2026-08-27.md) |
| 26/08 | [AVANCE-2026-08-26](AVANCE-2026-08-26.md) |
| 23/08 | [AVANCE-IMPORTADOR-BANCO](AVANCE-IMPORTADOR-BANCO-2026-08-23.md) |
| 19/08 | [AVANCE-BANCO-V3](AVANCE-BANCO-V3-2026-08-19.md) |
| 18/08 | [AVANCE-SIMULACION-VALIDACION](AVANCE-SIMULACION-VALIDACION-2026-08-18.md), [FALLOS-CORREGIDOS-AUDITORIA](FALLOS-CORREGIDOS-AUDITORIA.md), [FALLOS-CORREGIDOS-CRIBA](FALLOS-CORREGIDOS-CRIBA.md) |
| 17/08 | [AVANCE-HITO2](AVANCE-HITO2-2026-08-17.md), [AVANCE-HITO3](AVANCE-HITO3-2026-08-17.md), [PLAN-PRUEBA-TECNICA-CICLO-1](PLAN-PRUEBA-TECNICA-CICLO-1.md) |
| 14/08 | [AUDITORIA_2026-08-14](AUDITORIA_2026-08-14.md) |

### Diagramas

Archivos HTML que se abren en el navegador:

- [Etapas y pesos](diagramas/embudo-seleccion.html) — los cien puntos repartidos en cuatro etapas,
  con el ancho de cada bloque igual a su peso
- [Estados de la postulación](diagramas/estados-postulacion.html) — la rejilla de cinco etapas por
  cuatro momentos
- [Modelo de datos](diagramas/modelo-de-datos.html) — el mapa de la base de datos

### Mockups

Prototipos de pantalla. **Los mantiene otra persona**, no se editan desde aquí:

- [Panel de gestión](mockups/renaser-os-reclutamiento.html) — la vista del equipo
- [Portal del candidato](mockups/portal-candidato.html) — la vista pública

### Insumos

Material de origen. Solo se consulta:

| Archivo | Qué es |
|---|---|
| `nuevo_doc_requisitos_funcionales.docx` | **Documento vigente del cliente.** Se declara definitivo y reemplaza a los anteriores |
| `Sistema_Completo_Talento_RENASER_Seleccion_2026_2029.docx` | Vale para el texto completo de bancos, pruebas y ejemplos que el vigente no reproduce |
| `Banco_Maestro_Preguntas...docx` | Las preguntas con sus claves y dimensiones |
| `Sistema_RENASER_Talent_Intelligence...docx` | Versión anterior del vigente. Descartada |
| `CAMBIOS-DEL-DOCUMENTO-NUEVO.md` | Qué cambió con el documento nuevo y qué se decidió |
| `ANALISIS-DOCUMENTOS.md` | Qué documento manda sobre cuál y por qué |
| [`COMPROBACION-SIN-TECNICA.md`](insumos/COMPROBACION-SIN-TECNICA.md) | El sistema en dos páginas sin nada técnico, y las 93 tablas de entonces rastreadas contra él (hoy son 105). **Su primera parte se lee sola** |
| [`CAZATALENTOS-sistema-de-filtro.md`](insumos/CAZATALENTOS-sistema-de-filtro.md) | El método CAZATALENTOS tal como lo describió la clienta: los bancos por nivel y su filtro |
| `NOTAS-TEMPORALES.md` | Lo que sigue pendiente |
| `entrevista-cliente-2026-08-08.md` | Transcripción de la reunión |
| `pruebas-tecnicas/` | **Las cinco pruebas del puesto reales** (ARQ, BIO, CIVIL, CX, SIS), tal como se enviaron a candidatos |

---

## El sistema en corto

Antes de que exista una vacante, alguien registra una **Solicitud de Talento**: qué resultado
falta y qué pasa si no se contrata. De ahí sale la vacante. Un candidato postula en el portal
público y atraviesa cinco etapas:

| Etapa | Qué pasa | Quién califica | Peso |
|---|---|---|---|
| 1 y 2 · Perfil Integral | Currículum, psicométrico y evaluación, leídos juntos | IA | 40% |
| 3 · Prueba del puesto | Cronometrada, con un cambio inesperado | IA | 30% |
| 4 · Simulación de trabajo | Hasta 2 h, con conversación humana al final | Persona | 15% |
| 5 · Validación práctica | Un periodo de trabajo, con duración configurable | Persona | 15% |

Al final, una decisión: **verde** (contrata), **ámbar** (falta averiguar algo), **rojo** (no
pasa), **sin datos** (falta evidencia) o **reserva** (no para esta vacante, pero interesa).

---

## Con qué está hecho

| | |
|---|---|
| Backend | Java 25 con Spring Boot 4.1 |
| Base de datos | PostgreSQL propio, con pgvector |
| Trabajo en segundo plano | RabbitMQ |
| Inteligencia artificial · conversación y calificación | DeepSeek, que es un servicio externo |
| Inteligencia artificial · búsqueda por significado | Google Gemini, que es un servicio externo |
| Frontend | React con Vite, en el repositorio `RenaserOsPostulantes` (portal del candidato y panel de la empresa) |
| Identidad del equipo | Correo y contraseña, cuentas por invitación (25/08); RENASER OS quedó dormido como integración futura |

**Qué sale de Renaser y qué no.** La base de datos y los archivos viven en servidores de
Renaser. Los dos modelos son de fuera: DeepSeek califica y Google busca por significado.
Renaser aceptó que los datos de candidatos salgan hacia ellos el 18 de agosto de 2026.

⚠️ **Desde el 18/08/2026 los datos de candidatos sí salen.** Los tres agentes que califican el
Perfil Integral ya corren, y el currículum viaja hacia DeepSeek —anonimizado: sin edad, sexo ni
estado civil—. Antes de que pase por ahí el primer candidato real, **Renaser tiene que aprobar un
texto de consentimiento nuevo** que nombre a las dos empresas y diga qué se les envía: el actual
no menciona a ninguna. Hay un borrador en
[BORRADOR-CONSENTIMIENTO-v1.1.md](BORRADOR-CONSENTIMIENTO-v1.1.md).

---

## Antes de programar

Seis cosas que definen el diseño y conviene tener presentes:

**Los estados mandan.** Ningún estado fuera de la lista del documento 03 puede existir. Cada
cambio se guarda como un registro aparte que no se modifica ni se borra. Los 18 estados tienen
forma de rejilla —cinco etapas por cuatro momentos—, así que el siguiente estado se calcula.

**Nadie se descarta solo.** Lo único que cierra una postulación sin intervención humana es un
requisito objetivo configurado de antemano. Todo lo demás se **ordena** en cuatro grupos de
prioridad, y una persona confirma, sola o por lote.

**Casi todo es configurable.** Preguntas, pruebas, tiempos, pesos, barreras críticas, roles y
textos de correo viven en la base de datos, no en el código. El cliente cambia estas cosas
seguido.

**Nada se recalcula hacia atrás.** Cada candidato queda atado a la versión de preguntas y pesos
con la que se le evaluó. Su nota nunca cambia sola.

**Los permisos se verifican en el servidor.** Ocultar un botón no es seguridad. Cada llamada a
la API comprueba quién es el usuario, si puede hacer eso, y que los datos sean de su
organización.

**Toda entidad de negocio lleva organización.** Hoy solo existe Renaser, pero el aislamiento es
una regla de seguridad desde la primera versión, no algo que se añada después.

---

## Pendiente del cliente

| Qué falta | Bloquea |
|---|---|
| Figura contractual de la validación práctica productiva | Solo esa modalidad. La otra se puede usar ya |
| Aprobar los textos legales de consentimiento y conservación | Producción, no el desarrollo |
| Fijar el periodo de conservación de datos | No: es configuración, arranca con un valor |
| Decir qué familias de trabajo son afines y cuánta vigencia tiene cada componente | No: arranca sin reutilizar nada, que es lo seguro |
| Decidir si se construye el módulo psicométrico propio | No: su 5% se reparte mientras tanto |
| Confirmar el catálogo de puestos y sus nombres definitivos | No: hay once plantillas nombradas |
| Confirmar la máquina de estados, que es propuesta nuestra | No: está construida y es coherente |
| **Un tope de gasto para DeepSeek y para Google** | No hoy, pero conviene ponerlo: los modelos ya no corren en una máquina de Renaser, **cada consulta se paga** |
| **Aprobar el texto de consentimiento que nombre a DeepSeek y a Google** | **Sí, a usar el sistema con gente real.** Ya está decidido que la IA corre fuera y ya lee currículums; falta el texto que se lo diga al candidato |
| **Medir cómo lo hacen hoy**: horas por vacante, postulaciones por vacante y qué tasa de finalización considerarían buena | **Sí, a la medición.** El MVP se puede construir, pero sin línea base no se puede decir si funcionó |
| **Currículums y pruebas ya corregidos a mano** | **Sí, al paso 0** y a saber si la IA califica igual que una persona |

Las dos últimas son nuevas y salen de [Alcance del MVP](08-ALCANCE-DEL-MVP.md), en «Condiciones
previas». No bloquean programar, bloquean **saber si el MVP funcionó**, que es justo lo que el
cliente quiere averiguar.
