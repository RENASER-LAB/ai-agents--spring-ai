# Documentación · Sistema de selección Renaser

Backend en Java + Spring Boot para el módulo de selección de personal de Renaser Consulting.
**Va dentro de RENASER OS**: su frontend ya existe —React con Vite— y llama a este backend por
su API. Sus pantallas de selección se están construyendo ahora.

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
| [01 · Requisitos funcionales](01-REQUISITOS-FUNCIONALES.md) | RF-01 a RF-154. Qué hace el sistema |
| [02 · Requisitos no funcionales](02-REQUISITOS-NO-FUNCIONALES.md) | RNF-01 a RNF-66. Tecnología, seguridad, rendimiento |
| [03 · Estados de la postulación](03-ESTADOS-POSTULACION.md) | Los 18 estados de una postulación y sus transiciones |
| [04 · Roles y permisos](04-ROLES-Y-PERMISOS.md) | Los 73 permisos, acción por acción |
| [05 · Modelo de datos](05-MODELO-DE-DATOS.md) | Las 93 tablas por área y por qué el modelo es así. Se lee |
| [06 · Inventario de pantallas](06-INVENTARIO-DE-PANTALLAS-MOCKUPS.md) | Las 21 pantallas base, estados, ventanas, campos y datos de los mockups |
| [07 · Diccionario de datos](07-DICCIONARIO-DE-DATOS.md) | Cada tabla con todas sus columnas, tipos y claves. Se consulta |
| [08 · Alcance del MVP](08-ALCANCE-DEL-MVP.md) | Qué se construye primero, en tres hitos, y qué queda fuera |
| [09 · Las APIs](09-APIS.md) | Las dos puertas, cómo entrar y qué hace cada endpoint. La referencia viva es Swagger |
| [Curso del backend](CURSO-BACKEND.md) | Ruta para entender el código que existe, en orden. Para quien entra al proyecto |
| [Calificación con IA](CALIFICACION-CON-IA.md) | Cómo la IA lee el currículum, califica lo abierto y arma el Perfil de Talento. Y qué pasa si falla |
| [Criba de currículums](CRIBA-DE-CURRICULUMS.md) | Cargar una convocatoria con una carpeta de currículums, pedir que la IA los lea y ver quién es el más apto |
| [Fallos corregidos de la criba](FALLOS-CORREGIDOS-CRIBA.md) | Los cinco fallos que salieron al pasar 190 currículums reales. Cuatro no daban error |
| [Los siete fallos de la auditoría](FALLOS-CORREGIDOS-AUDITORIA.md) | Lo que encontró la revisión del 18/08: cuatro que tocaban dinero o decisiones de contratación, y ninguno daba error |
| [Comprobaciones automáticas](COMPROBACIONES-AUTOMATICAS.md) | Qué se comprueba solo: 238 pruebas, 7 reglas de arquitectura y Semgrep |
| [Banco de preguntas v3](AVANCE-BANCO-V3-2026-08-19.md) | El banco nuevo del cliente: 190 ítems, ocho formatos y su motor de puntuación. Qué está hecho y qué falta |
| [CI/CD](CI-CD.md) | Qué corre en cada cambio, cómo se despliega a Pruebas en Render y qué variables de entorno hacen falta |
| [Conectar la base a Supabase](CONEXION-SUPABASE.md) | Cómo hacer que la base de datos deje de vivir en tu máquina y pase a Supabase. Paso a paso, y cómo volver atrás |
- [Los curriculums dejan de vivir en el backend](ARCHIVOS-EN-BUCKET.md) — el bucket, los dos enlaces firmados, y por que un PDF en el disco del backend se pierde en el primer despliegue

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
| [`COMPROBACION-SIN-TECNICA.md`](insumos/COMPROBACION-SIN-TECNICA.md) | El sistema en dos páginas sin nada técnico, y las 93 tablas rastreadas contra él. **Su primera parte se lee sola** |
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
| Frontend | React con Vite, el de RENASER OS |
| Identidad del equipo | RENASER OS emite el token; aquí solo se valida |

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
