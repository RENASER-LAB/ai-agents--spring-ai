# Requisitos no funcionales

Sistema de selección de personal — Renaser Consulting
Versión 2.0 · 2026-08-15

Este documento dice **cómo** debe funcionar el sistema: con qué se construye, qué tan rápido,
qué tan seguro y qué pasa cuando algo falla.

El **qué hace** está en [Requisitos funcionales](01-REQUISITOS-FUNCIONALES.md).

---

## El sistema en una página

Un portal público donde la gente postula, y un panel dentro de RENASER OS donde el equipo de
Renaser trabaja. Entre los dos, cinco etapas de evaluación que van de leer un currículum a
verla sostener un periodo de trabajo real.

Este backend **es un módulo de RENASER OS**, no un sistema aparte. El frontend ya existe y lo
llama por su API. Son dos servicios separados: **no comparten base de datos**.

```
   CANDIDATO                      EQUIPO RENASER
   portal público                 panel en RENASER OS
        |                                |
        +----------------+---------------+
                         |
                 React + Vite
                         |
                   API REST / JSON
                         |
                   Spring Boot  <----> API de RENASER OS
                         |             identidad del equipo
                         |             tareas y tiempos
                         |             desempeño 30/90/180
        +----------------+----------------+
        |                |                |
   PostgreSQL         RabbitMQ      Almacén de
   (propio)           (la cola)      archivos
                         |             (propio)
                         |
                 DeepSeek (califica)
                 Google (busca por significado)
                      servicios de fuera
```

Lo que tarda —puntuar un CV, corregir una prueba— no se hace mientras el candidato espera.
Se deja en una cola y se atiende aparte. Por eso hay una cola en el dibujo.

### Las decisiones que ya están tomadas

| | |
|---|---|
| Backend | Java 25 con Spring Boot 4.1 |
| Frontend | React con Vite, el de RENASER OS que ya existe |
| Base de datos | PostgreSQL propio, con la extensión pgvector |
| Archivos | Almacén propio, fuera de la base de datos |
| Cola de trabajo en segundo plano | RabbitMQ |
| Comunicación con el frontend | API REST con JSON |
| Modelo que busca por significado | **Google Gemini**, un servicio de fuera |
| Modelo que conversa y califica | **DeepSeek**, un servicio de fuera |
| Correo | Desde el dominio propio de Renaser |

**Ningún modelo corre dentro del servidor de Renaser.** Los dos son de otras empresas, y
Renaser aceptó que los datos de candidatos salgan hacia ellos (decisión del 18/08/2026). Se
eligió así porque un modelo propio exige una máquina cara y dedicada, y la calidad de los de
fuera es mejor.

⚠️ **Los textos de consentimiento todavía no nombran a ninguna de las dos empresas, y esto ya
es urgente** (18/08/2026). Antes no rompía nada porque la IA no leía a nadie; **ahora sí lee**:
los tres agentes del Perfil Integral corren de verdad y el currículum sale hacia DeepSeek,
anonimizado —sin edad, sexo ni estado civil—. **Renaser tiene que aprobar un texto que nombre a
las dos empresas y diga qué se les manda, antes de que pase por ahí el primer candidato real.**
Hay un borrador en [BORRADOR-CONSENTIMIENTO-v1.1.md](BORRADOR-CONSENTIMIENTO-v1.1.md).

### Cuánta gente lo va a usar

| | |
|---|---|
| Vacantes abiertas a la vez | Hasta 10 |
| Postulantes por vacante | Hasta 500 |
| Usuarios internos | Menos de 20 |
| Candidatos a la vez en la simulación | Hasta 50 |
| Contrataciones al año | Decenas, no cientos |

Es una empresa pequeña que contrata de forma continua. No hace falta diseñar para miles de
usuarios simultáneos, pero sí dejar el sistema separado por partes para poder crecer sin
rehacerlo.

---

# 1. Tecnología

**RNF-01** El backend se construye en **Java con Spring Boot**.

**RNF-02** El frontend es el proyecto de RENASER OS que ya existe, hecho en **React con Vite**.
Sus pantallas de selección de personal se están construyendo desde el 18/08/2026.

**RNF-03** Los dos se comunican por **API REST con JSON**. Son proyectos separados: cada uno
se despliega por su cuenta y el contrato entre ellos es la API.

**RNF-04** La base de datos es **PostgreSQL propio**, con la extensión **pgvector** instalada.
Para desarrollar se levanta en un contenedor; en producción, en el servidor de Renaser.

**RNF-05** Los archivos se guardan **fuera de la base de datos**. La base solo guarda la ruta.

## Reglas de la base de datos y los archivos

Cinco reglas que evitan problemas conocidos:

**RNF-06** **La identidad del equipo viene de RENASER OS; los permisos de este módulo son de
este módulo.** RENASER OS emite el token y este backend solo valida su firma: no guardamos la
contraseña de nadie del equipo. Los candidatos sí tienen cuenta y contraseña aquí, porque no son
usuarios de RENASER OS.
*Por qué:* dos contraseñas para la misma persona es justo lo que RENASER OS quiere evitar. Pero
su sistema no conoce acciones como «publicar una versión del banco», así que los permisos finos
tienen que vivir donde existen esas acciones.

**RNF-06b** El identificador que un usuario tiene en RENASER OS se guarda como **columna suelta,
sin clave foránea**, porque son dos servicios separados.
*Por qué:* una clave foránea contra una tabla que vive en otra base de datos no existe. Fingir
que sí lleva a un sistema que se cae cuando el otro cambia algo.

**RNF-06c** **Ningún usuario ve datos de una organización que no sea la suya.** No es un permiso
que se pueda marcar ni desmarcar: es una condición que el backend aplica en cada consulta.
*Por qué:* el sistema se diseñó para admitir clientes de consultoría más adelante. Un
aislamiento que se añade después nunca cubre todas las consultas que ya se escribieron.

**RNF-07** La base de datos **nunca se expone a internet**. Solo la alcanza el backend, desde
la red interna del servidor.
*Por qué:* es la única copia de los datos personales de todos los candidatos. Un puerto
abierto de PostgreSQL se encuentra con un escaneo automático en cuestión de horas.

**RNF-08** La estructura de la base se maneja con **Flyway**, en archivos versionados dentro
del repositorio. Nadie crea ni modifica tablas a mano.
*Por qué:* si el esquema cambia por fuera, deja de coincidir con lo que espera el backend y
el problema aparece en producción.

**RNF-09** El almacén de archivos es **privado**. El acceso se da con enlaces firmados de
corta duración que genera el backend cuando alguien abre el archivo.
*Por qué:* un CV contiene nombre, teléfono e historial laboral. Un almacén público lo expone
a cualquiera que tenga el enlace.

**RNF-10** Todo se aloja en **servidores propios de Renaser**, en Perú si es posible: la base
de datos, los archivos y el modelo que busca por significado. Los datos de los candidatos no
salen a servicios de terceros.

El sistema convive con un motor de agentes que sí usa un modelo de fuera (DeepSeek) para
conversar, pero la selección de personal no lo llama. **Mantener separadas esas dos cosas es
parte del requisito**, no un detalle de implementación.

---

# 2. Datos personales

**RNF-11** El sistema cumple la **Ley 29733** de protección de datos personales del Perú.

**RNF-12** **Son dos consentimientos separados**, y el segundo nunca se da por supuesto:

| Consentimiento | Para qué |
|---|---|
| Del proceso | Evaluar su postulación a **esta** vacante |
| De futuros contactos | Conservar sus datos y avisarle de otras convocatorias |

De cada aceptación se guarda: usuario autenticado, nombre registrado, versión del texto,
fecha y hora, dirección desde donde se aceptó, **identificador de sesión** y **huella del
documento**. La evidencia se puede exportar.

**RNF-12b** El candidato puede retirar el consentimiento de futuros contactos **sin que eso
afecte** a los registros que haya obligación de conservar, y sin cerrar sus postulaciones en
curso. Son tres cosas distintas: retirar una postulación, retirar el consentimiento de futuros
contactos, y pedir el borrado de datos.

**RNF-13** El texto de consentimiento dice **tres cosas** de forma clara:
1. Que sus datos se usan para evaluar su postulación, y que participan agentes de inteligencia
   artificial en esa evaluación.
2. Qué se hace con sus entregables y qué confidencialidad aplica.
3. Dónde se guardan sus datos y **cuánto tiempo**.
4. **A qué empresas de fuera se envían y qué se les manda.** La base de datos y los archivos se
   quedan en Renaser, pero los modelos que califican y buscan por significado son de otras
   empresas y están fuera del país, así que hay **flujo transfronterizo** que declarar.

**RNF-13b** El **periodo de conservación es configuración**, fijada según la política aprobada.
**No se escribe un número de meses en el código.** Al vencer, el sistema ejecuta la política
definida: eliminar, anonimizar o pedir que renueve el consentimiento.
*Por qué:* la ley obliga a fijar un plazo y a decirlo en el texto. Escribirlo en el código
significa un despliegue cada vez que el abogado cambie de opinión.

**RNF-14** El texto de consentimiento se versiona. Si cambia, los que ya aceptaron quedan
ligados a la versión que firmaron.

**RNF-15** El candidato puede **pedir que se borren sus datos**. El sistema debe poder
hacerlo sin romper los registros de auditoría.

**RNF-16** Antes de que la IA lea un CV, el sistema **oculta** foto, edad, sexo, estado civil y
cualquier otro dato marcado como no utilizable para puntuar. Esa versión oculta es la única que
se envía al modelo. Qué se oculta es configurable, y la lista arranca con esos cuatro.

**RNF-17** El sistema guarda qué versión del CV se envió a la IA, para poder demostrar que la
regla se cumplió.

**RNF-18** A un candidato **no se le mete en un proceso al que no postuló**, y **ninguna
persona ajena a Renaser ve sus datos**. Nadie aparece como candidato de una vacante sin
haberse postulado a ella.

Lo que sí sale, y solo eso, es lo que necesitan los modelos para calificar: el currículum ya
recortado y las respuestas, hacia las dos empresas nombradas en «Inteligencia artificial». Va
por máquina, no lo lee nadie, y el candidato lo acepta antes en el texto de consentimiento.

Distinto es lo que pasa **dentro de sus propias postulaciones**: parte de lo que ya respondió
puede reutilizarse para no hacérselo repetir. Eso es una comodidad para él, no un uso de sus
datos a sus espaldas.

**RNF-18b** **Que dos puestos sean del mismo nivel no basta** para reutilizar una evaluación.
Solo se reutilizan los componentes vigentes cuando el puesto nuevo es de la misma familia de
trabajo o de una declarada afín. Las preguntas propias del puesto se vuelven a generar. Tanto la
vigencia de cada componente como qué familias son afines son configurables y versionadas.
*Por qué:* un director de tecnología y un director comercial son el mismo nivel y no se
parecen en nada. Reutilizar por nivel evalúa a uno con las preguntas del otro.

---

# 3. Seguridad

**RNF-19** Todo el tráfico va por HTTPS.

**RNF-20** Las contraseñas de los candidatos se guardan cifradas de forma irreversible, nunca en
texto plano. Del equipo de Renaser no se guarda ninguna: su token lo emite RENASER OS.

**RNF-21** Cada llamada a la API verifica **tres** cosas: quién es el usuario, si tiene permiso
para eso, y que los datos que pide sean de su organización.

**RNF-22** Un candidato solo puede ver **sus propias** postulaciones y respuestas. El portal
está aislado de los datos internos y de los demás candidatos.

**RNF-23** El responsable del área solo ve las solicitudes, vacantes y candidatos de **su** área.

**RNF-24** Las claves de puntuación **nunca** se envían al portal del candidato. Ni siquiera
escondidas en el código de la página.
*Por qué:* si viajan al navegador, cualquiera puede leerlas y el banco de preguntas queda
inutilizado.

**RNF-25** Las contraseñas de la base de datos, de la cola y del correo van en variables de
entorno, nunca en el código.

**RNF-26** El sistema limita cuántas veces seguidas se puede intentar entrar, para frenar
ataques de fuerza bruta.

---

# 4. Auditoría

**RNF-27** Toda acción que cambie una decisión queda registrada: quién, cuándo, qué cambió,
qué valor tenía antes y por qué.

**RNF-28** Los registros de auditoría **no se pueden modificar ni borrar**.

**RNF-29** Cada nota calculada guarda con qué versión de preguntas y de pesos se calculó.

**RNF-30** El sistema puede **reproducir cualquier evaluación pasada** tal como se vio en su
momento: mismas preguntas, mismo orden, mismos pesos.

**RNF-31** Se guarda la respuesta completa de la IA, no solo la nota que produjo.
*Por qué:* si alguien reclama una calificación, hay que poder revisar en qué se basó.

**RNF-31b** Cada ejecución de un agente guarda además: **qué agente fue y con qué versión**, el
objetivo, las entradas y evidencias que recibió, el modelo y proveedor, la versión de las
instrucciones o la rúbrica, y **el nivel de confianza** de su salida.
*Por qué:* sin la versión del agente no se puede saber si un error viene del modelo o de un
cambio en sus instrucciones. Y sin la confianza no se puede distinguir una nota firme de una
que el propio modelo dio con dudas.

**RNF-32** Se registra cuánto costó cada llamada a la IA, para poder controlar el gasto.

---

# 5. Inteligencia artificial

**RNF-33** El modelo que califica a un candidato es **un servicio de fuera** (DeepSeek), y el
que busca por significado también (Google Gemini). La lógica de evaluación no depende de cuál
sea: cambiar de modelo, o volver a uno propio, no obliga a reescribirla.
*Por qué:* un modelo dentro del servidor exige una máquina cara y dedicada, y hoy los de fuera
califican mejor. Renaser aceptó el intercambio el 18/08/2026. Que el modelo sea intercambiable
es lo que deja la puerta abierta a rehacer esa elección sin tocar la evaluación.

**RNF-33.1** El candidato tiene que **saber a quién se mandan sus datos**. El texto de
consentimiento nombra las empresas y dice qué se les envía. Sin ese texto aprobado, la
calificación con IA no se enciende para candidatos reales.
*Por qué:* es lo único que sostiene legalmente al requisito anterior. Un modelo propio evitaba
tener que contarlo; uno de fuera obliga.

**RNF-33.2** Antes de salir hacia el modelo, el currículum se recorta: **sin foto, edad, sexo
ni estado civil**. Son dos archivos, no uno, y lo que sale es siempre la versión recortada,
nunca el original que subió la persona.

⚠️ **Nada de esto está probado en código todavía, porque la IA aún no califica a nadie.** Los
tres agentes del hito 2 son los que lo vuelven real. Mientras no existan, ningún dato de
candidato sale de Renaser.

**RNF-34** Los textos de instrucción que se envían a la IA se administran como configuración,
con versiones, y se pueden cambiar sin volver a desplegar el sistema.

**RNF-35** Si la IA no responde o falla, la calificación queda **pendiente** y se reintenta.
Nunca se guarda una nota inventada ni un cero.

**RNF-36** Si un dato no existe, la IA debe decir que **falta el dato**. Nunca inventarlo.

**RNF-37** Toda calificación de la IA viene con su explicación. Una nota sin explicación no
se acepta.

**RNF-38** El sistema tiene un tope de gasto mensual configurable. Al acercarse, avisa.

**RNF-39** Las calificaciones se procesan en segundo plano. El candidato no espera a que la
IA termine para seguir.

---

# 6. Rendimiento

**RNF-40** Las pantallas del panel cargan en menos de 2 segundos con hasta 500 candidatos por
vacante.

**RNF-41** Guardar una respuesta del examen tarda menos de 500 milisegundos.
*Por qué:* el candidato responde más de 100 preguntas seguidas. Si cada una demora, abandona.

**RNF-42** El sistema soporta **50 candidatos rindiendo la simulación al mismo tiempo**, ya
que es una sesión grupal.

**RNF-43** Calificar una evaluación completa con IA no debe tardar más de 10 minutos.

**RNF-44** Subir un archivo de hasta 200 MB debe funcionar sin cortarse.
*Por qué:* la prueba de edición de video entrega archivos pesados.

---

# 7. Disponibilidad y respaldo

**RNF-45** Durante una simulación de 2 horas el sistema **no puede caerse**. Es una sesión
presencial con varias personas y no se puede repetir.

**RNF-46** Si el sistema se cae durante un examen, el candidato retoma donde quedó. Nada de
lo respondido se pierde.

**RNF-47** La base se respalda a diario y los respaldos se guardan al menos 30 días.

**RNF-48** Se puede restaurar la base a un momento anterior.

---

# 8. Cronómetros

**RNF-49** El tiempo lo controla el **servidor**, no el navegador.
*Por qué:* si el reloj vive en la página, se puede manipular desde el navegador.

**RNF-50** Si el candidato cierra la página durante una prueba cronometrada, **el tiempo
sigue corriendo**.

**RNF-51** El cambio inesperado se dispara solo, aunque el candidato no esté mirando la
pantalla. **El minuto exacto se sortea dentro de un rango configurable** y se guarda en el
intento, junto con qué variante del cambio le tocó.
*Por qué:* si siempre aparece a la mitad, el segundo candidato ya lo sabe.

**RNF-52** Las horas registradas durante la simulación se guardan con precisión de segundos y
con zona horaria.

---

# 9. Usabilidad

**RNF-53** El portal funciona en celular, tablet y computadora. La postulación y las
preguntas deben poder responderse desde el teléfono.

**RNF-54** La prueba del puesto y la simulación están pensadas para computadora.

**RNF-55** Todo el texto que ve el candidato está en español simple, sin términos internos ni
palabras en inglés.

**RNF-56** El candidato ve en todo momento en qué etapa está y qué le toca hacer.

**RNF-57** Durante un examen largo se muestra el avance (por ejemplo, "pregunta 47 de 90").

**RNF-58** Los mensajes de error dicen qué hacer, no muestran detalles técnicos.

---

# 10. Mantenimiento

**RNF-59** El sistema se construye por partes independientes: vacantes, candidatos,
evaluación, calificación, avisos.
*Por qué:* así se puede cambiar una parte sin romper las demás.

**RNF-60** Lo que puede cambiar sin tocar código: preguntas, pruebas, pesos, notas mínimas,
roles, permisos, textos de correo, textos de instrucción de la IA, plazos de cierre
automático.

**RNF-61** El código y la documentación están en español, igual que el resto del proyecto.

**RNF-62** Las reglas de cálculo de notas tienen pruebas automáticas.
*Por qué:* un error ahí descarta gente injustamente y nadie se entera.

**RNF-63** Existe un entorno de pruebas separado del de producción, con datos falsos.

---

# 11. Escala

Las cifras están al inicio, en «El sistema en una página».

**RNF-64** No hace falta diseñar para miles de usuarios simultáneos. Pero la separación por
partes debe permitir crecer sin rehacer el sistema.

⚠️ **Sobre mejorar el sistema con el tiempo:** el documento de Renaser propone recalibrar los
pesos cada 30 a 50 contrataciones. A este ritmo, eso son varios años de datos. El sistema
**guarda** todo desde el primer día y **muestra** la comparación entre lo que predijo y lo
que pasó, pero la recalibración se hace **a mano**, mirando esos datos. No se construye un
ajuste automático que nunca tendría suficiente información para funcionar.

---

# 12. Lo que depende de otros

| De qué depende | Estado | Qué pasa si no está |
|---|---|---|
| API de RENASER OS: identidad del equipo | **Existe** | Nadie del equipo puede entrar. Es la única dependencia dura |
| API de RENASER OS: tareas, tiempos y bloqueos | **Existe** | Las métricas de la validación se completan a mano |
| API de RENASER OS: desempeño 30/90/180 | **Existe** | El seguimiento posterior queda vacío |
| Módulo psicométrico propio | **No construido** | Su 5% se reparte entre las otras dos partes |
| DeepSeek, que califica | **En uso desde el 18/08/2026** | Nadie se queda sin nota: el trabajo se reintenta y la postulación espera. Pero **cada consulta se paga**, y sin saldo o sin clave válida no avanza |
| Google Gemini, que busca por significado | **En uso** | La búsqueda por parecido queda vacía. También se paga por consulta |
| Dominio de correo de Renaser | **Sin confirmar** | Los correos pueden caer en spam |
| Figura contractual de la validación productiva | **Sin definir** | Solo se puede usar la modalidad no productiva |

**RNF-65** El correo sale desde el dominio propio de Renaser, con la configuración de
autenticación correcta.
*Por qué:* si una invitación a la sesión cae en spam, se pierde al candidato.

**RNF-66** Cada dependencia externa se construye de forma que el sistema **siga funcionando sin
ella**, aunque esa parte quede vacía.

**RNF-66b** Cuando la API de RENASER OS no responde, el sistema **no se cae ni inventa el dato**:

| Qué se pedía | Qué hace el sistema |
|---|---|
| Validar el token del equipo | Rechaza la entrada. No hay forma de fingir una identidad |
| Tareas, tiempos y bloqueos | Deja la métrica vacía y marcada como pendiente, para que una persona la complete |
| Desempeño posterior | Reintenta más tarde. Nada del proceso de selección depende de ella |

*Por qué:* el portal del candidato no debe caerse porque el sistema interno esté en
mantenimiento. Un candidato rindiendo una prueba cronometrada no puede perder su intento por
algo que no tiene nada que ver con él.

---

# Documentos relacionados

| Documento | Qué contiene |
|---|---|
| [Qué hace el sistema](00-QUE-HACE-EL-SISTEMA.md) | El sistema entero, sin nada técnico. **Empieza por aquí** |
| [Requisitos funcionales](01-REQUISITOS-FUNCIONALES.md) | Qué hace el sistema, etapa por etapa |
| [Estados de la postulación](03-ESTADOS-POSTULACION.md) | Los 18 estados y cómo se pasa de uno a otro |
| [Roles y permisos](04-ROLES-Y-PERMISOS.md) | Quién puede hacer qué, acción por acción |
| [Modelo de datos](05-MODELO-DE-DATOS.md) | Las tablas por área y por qué existe cada una |
| [Etapas y pesos](diagramas/embudo-seleccion.html) | Los cien puntos, en un dibujo |
| [Estados, en un dibujo](diagramas/estados-postulacion.html) | La rejilla de etapas por momentos |
| [Alcance del MVP](08-ALCANCE-DEL-MVP.md) | Qué se construye primero, en qué orden y por qué |
