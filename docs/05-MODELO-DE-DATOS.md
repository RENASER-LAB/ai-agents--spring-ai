# Modelo de datos

Sistema de selección de personal — Renaser Consulting
Versión 2.0 · 2026-08-15

Cómo se guarda la información del sistema: qué tablas existen, cómo se llaman, por qué existe
cada una y qué reglas hace cumplir la base de datos por sí sola.

Este documento se lee solo. No hace falta abrir los otros.

---

## Para qué existe este documento

Es el puente entre lo que el sistema debe hacer y el código que lo hace. Antes de escribir la
primera tabla en Java hay que estar de acuerdo en qué se guarda y cómo se relaciona, porque
cambiar el modelo con datos reales dentro es caro y arriesgado.

Sirve para tres cosas:

- **Escribir las migraciones.** Cada tabla de aquí se convierte en un archivo versionado.
- **Discutir cambios.** Cuando el cliente pida algo nuevo, se mira aquí si cabe o no.
- **Entender el sistema.** Un modelo de datos bien contado explica el negocio mejor que
  cualquier otro documento.

**La base ya está construida.** Las migraciones `V1` a `V40` viven en
`src/main/resources/db/migration` —**102 tablas de este módulo**, 105 en la base contando la de
Flyway y las dos del motor de agentes— y Flyway es el dueño del esquema. Cambiar algo de aquí
ya cuesta una migración nueva, y **una migración aplicada no se edita nunca**: se escribe otra
encima.

La `V36` trae **el perfil del candidato**: seis tablas que cuelgan de `persona` —no de
`usuario`, porque el usuario existe una vez por organización y el perfil es de la persona—
(`perfil_candidato`, `experiencia_perfil`, `educacion_perfil`, `idioma_perfil`,
`certificacion_perfil`, `enlace_perfil`), dos catálogos (`nivel_educativo`, `nivel_idioma`),
la columna `archivo.contenido_hash` (la huella que evita pagar dos lecturas del mismo
currículum) y los permisos `ver_perfil_candidato` y `ver_pretension`. Ver
[PROPUESTA-PERFIL-DEL-CANDIDATO.md](PROPUESTA-PERFIL-DEL-CANDIDATO.md).

La `V37` convierte el esquema en **multiempresa**: `organizacion.es_plataforma` marca a la
dueña de la plataforma (solo una puede serlo) y reemplaza al código `'RENASER'` que estaba
quemado en el código; cuatro banderas por organización dicen qué instrumento es propio y
cuál se lee de la plataforma (`banco_propio`, `pesos_propios`,
`plantillas_evaluacion_propias`, `pruebas_puesto_propias`); `usuario.es_equipo` separa las
cuentas del panel de las del portal —cae el CHECK que distinguía por el id de RENASER OS—;
la tabla nueva `invitacion` es la única puerta de entrada al panel (se guarda el hash del
token, nunca el token, y es de un solo uso); el banco pierde el modelo «`organizacion_id`
vacío = biblioteca global» (esas filas pasan a la plataforma y la columna queda
obligatoria); y `copiada_de_version_id` en los cuatro instrumentos dice de qué versión de
la plataforma salió cada copia. El porqué de cada decisión está en
`docs/superpowers/specs/2026-08-25-*.md`.

La `V38` cierra el multiempresa: `consentimiento.postulacion_id` amarra el consentimiento
del proceso a la postulación —y a la empresa— que lo firmó; la tabla nueva `tarifa_modelo`
pone precio por millón de tokens a cada modelo, con vigencia por fecha; el estado
`EN_ESPERA` entra al catálogo de `trabajo_ia` para los trabajos congelados por el tope
mensual de IA; y se siembra la plantilla de correo del aviso del 80% (`TOPE_IA_AVISO`) para
todas las organizaciones.

Cuatro son del banco de preguntas v3: `V20` reemplaza el banco entero y añade cinco
tablas (`rango_pregunta`, `campo_caso`, `multiplicador_bloque`, `umbral_nivel` y
`filtro_eliminatorio`), `V21` añade `respuesta.detalle`, y `V25` y `V28` reparan lo que la
importación del PDF dejó a medias (opciones que nunca se cargaron; etiquetas y enunciados
cortados por el ancho de página). Ver
[AVANCE-BANCO-V3-2026-08-19.md](AVANCE-BANCO-V3-2026-08-19.md).

`V19__datos_del_cv_y_dos_pasadas.sql` trae `dato_cv`: quién es la persona según su
currículum, sacado por un agente que no puntúa. Ver
[La criba de currículums](CRIBA-DE-CURRICULUMS.md).

---

## Qué guarda este sistema

Renaser Consulting contrata personal para sí misma. Antes de que exista una vacante, alguien
registra una **Solicitud de Talento**: qué resultado falta y qué pasa si no se contrata. De ahí
sale la vacante.

Una persona la ve en el portal público, crea su cuenta, sube su currículum y postula. A partir
de ahí atraviesa cinco etapas:

| Etapa | Qué pasa | Quién califica | Peso |
|---|---|---|---|
| 1 y 2 · Perfil Integral | Currículum, módulo psicométrico y evaluación, leídos juntos | Máquina | 40% |
| 3 · Prueba del puesto | Trabajo cronometrado, con un cambio inesperado | Máquina | 30% |
| 4 · Simulación de trabajo | Sesión de hasta dos horas, con conversación final | Persona | 15% |
| 5 · Validación práctica | Un periodo de trabajo, con duración configurable | Persona | 15% |

Al final hay una decisión: **verde** (se contrata), **ámbar** (falta averiguar algo), **rojo**
(no pasa), **sin datos** (no hay evidencia suficiente) o **reserva** (no para esta vacante, pero
interesa para otra).

Los puestos se agrupan en tres **niveles** —Dirección, Coordinación y Ejecución— y siete
**familias** de trabajo. El nivel decide cuántas preguntas responde el candidato y qué barreras
son imperdonables; la familia decide qué preguntas y si algo se puede reutilizar.

Dentro de la empresa trabajan cinco clases de usuario: **Equipo de Talento**, que lleva el día a
día; **responsable del área**, que ve solo lo suyo; **Dirección**, que decide qué valora Renaser;
**Administrador**, que maneja el sistema; y el **candidato**, que solo ve lo suyo.

---

## Cómo se llaman las cosas

Un estándar de nombres aburrido y aplicado sin excepciones. Su valor no es la belleza: es que
cualquiera pueda adivinar el nombre de una columna sin ir a buscarla.

| Regla | Ejemplo |
|---|---|
| Español, pero **sin tildes ni eñes** en los nombres | `contrasena_hash`, `descripcion`, `anonimizado_en` |
| Todo en minúsculas, palabras unidas con guion bajo | `version_banco`, `marca_tiempo_simulacion` |
| Tablas en **singular**: una fila es una cosa | `usuario`, no `usuarios` |
| Clave primaria: siempre se llama `id` | `usuario.id` |
| Clave foránea: el nombre de la tabla a la que apunta, más `_id` | `postulacion.usuario_id` |
| Tabla que une dos: los dos nombres seguidos | `usuario_rol`, `rol_permiso`, `sesion_vacante` |
| Fecha u hora de algo que pasó: termina en `_en` | `creado_en`, `publicado_en`, `entregado_en` |
| Sí o no: empieza por `es_` | `es_final`, `es_sistema`, `es_entrega_automatica` |
| Una nota o puntaje: empieza por `nota_` | `nota_criterio`, `nota_etapa` |
| Una versión de algo: empieza por `version_` | `version_banco`, `version_pesos` |
| Un peso configurable: empieza por `peso_` | `peso_etapa`, `peso_dimension` |

**Sin prefijos de módulo.** La base es solo de selección, así que `sel_vacante` no aportaría
nada y habría que escribirlo noventa y tres veces.

**Nada de `_tabla`, `_tbl` ni `_catalogo`** al final de un nombre. Ya se sabe que es una tabla.

### Los catálogos se identifican con texto, no con números

Las tablas que son listas fijas —los estados, las etapas, los niveles, las familias, las
dimensiones— no usan un número como clave, sino un código legible:

```
estado_postulacion.codigo = 'PERFIL_POR_CONFIRMAR'
etapa.codigo              = 'PRUEBA_PUESTO'
familia.codigo            = 'TECNOLOGIA'
dimension.codigo          = 'INT'
```

Así, al mirar la tabla de postulaciones se entiende en qué estado está cada una sin cruzarla
con nada. Con un número habría que ir a buscar qué significa el 7 cada vez.

---

## Las nueve decisiones que le dan forma

Son los puntos donde este modelo puede salir mal sin que nada falle a la vista. Cada uno está
aquí porque la alternativa parecía más simple y era peor.

### 1 · Toda entidad de negocio pertenece a una organización

Hoy solo existe Renaser, pero el sistema se va a vender a clientes de consultoría, y el
aislamiento por organización es una regla de seguridad desde la primera versión.

**La columna va en la raíz de cada árbol, no en cada hoja.** `evaluacion` la lleva; `respuesta`,
que cuelga de ella, no —ya se sabe de quién es por su padre—. Así son unas 25 tablas con
`organizacion_id`, no las 93.

Los **catálogos no la llevan**: los permisos, los niveles, las familias, las dimensiones, las
etapas y los estados son iguales para todos. Y el banco de preguntas de Renaser es una
biblioteca global: su `organizacion_id` va vacío, y una organización que quiera el suyo propio
crea una versión con su identificador puesto.

Esto cambia una cosa que es fácil pasar por alto: **el correo ya no es único a secas, sino único
dentro de una organización**. La misma persona puede ser candidata en dos empresas distintas.

Se puso desde la primera migración, cuando todavía era gratis. Añadirlo hoy habría sido migrar
veinticinco tablas con datos dentro y revisar cada consulta ya escrita.

### 2 · La persona está separada de la cuenta, y la cuenta puede venir de fuera

Son tres tablas y no una:

- **`persona`** guarda quién es alguien: nombre, apellidos, teléfono, documento.
- **`usuario`** guarda cómo entra al sistema.
- **`postulacion`** es lo que hace un usuario en una vacante concreta.

Un candidato no es una tabla aparte: **es un usuario que postuló**.

Pero hay una diferencia nueva entre los dos tipos de usuario:

| | Contraseña | Identificador externo |
|---|---|---|
| Equipo de Renaser | **Vacía.** RENASER OS emite su token y aquí solo se valida | El que tiene en RENASER OS |
| Candidato | La suya, cifrada | Vacío. No es usuario de RENASER OS |

Ese identificador externo es una **columna suelta, sin clave foránea**, porque RENASER OS es
otro servicio que habla por HTTP y no comparte base de datos con este. Fingir una clave foránea
contra una tabla que vive en otra base lleva a un sistema que se rompe cuando el otro cambia
algo.

Los datos personales están en **un solo sitio**, así que el día que alguien pida que se borren
no hay que limpiar dos tablas.

### 3 · Las respuestas de la evaluación no cuelgan de la postulación

Cuelgan del usuario, de la plantilla de evaluación y de la versión del banco que respondió. Eso
permite no hacerle repetir lo que ya contestó.

**Pero el mismo nivel no basta para reutilizar.** Un director de tecnología y un director
comercial son el mismo nivel y no se parecen en nada. La regla es: **misma familia, o una
declarada afín, y dentro de la vigencia de cada componente.** Lo propio del puesto se vuelve a
generar siempre.

Y **no es todo o nada.** Cuando alguien postula a un puesto de familia afín se crea una
evaluación **nueva**, que contiene solo las preguntas regeneradas, y que apunta a la anterior
—`reutiliza_de_evaluacion_id`— de donde sale el núcleo ya respondido. Para calcular la nota se
leen las dos. Si la postulación apuntara a una sola evaluación habría que elegir entre reutilizar
todo o nada, y la regla del cliente dice otra cosa.

Por eso hacen falta cuatro cosas que antes no existían: la tabla de familias, la de familias
afines, una fecha de vigencia en la evaluación, y esa referencia de una evaluación a otra.

Si las respuestas colgaran de la postulación habría que copiarlas cada vez, y dos copias de la
misma respuesta terminan diciendo cosas distintas.

### 4 · Una versión publicada nunca se modifica

Ni el banco de preguntas, ni los pesos, ni las plantillas de prueba, ni las de evaluación, ni el
texto de consentimiento, ni las instrucciones de la IA. Editar cualquiera de esas cosas **crea
una versión nueva**; la anterior queda tal como estaba, para siempre.

Esto es lo que permite que la nota de un candidato no cambie sola. Basta con guardar a qué
versión estaba atado: como esa versión no se mueve, el dato tampoco.

La alternativa —copiar los valores sueltos dentro de cada nota— parece más segura pero es peor:
repite el mismo dato miles de veces y, en cuanto alguien arregla un error de tipeo en una
pregunta, unas copias quedan corregidas y otras no.

### 5 · Los pesos ya no dependen del nivel, sino de la vacante

Antes había un peso por cada combinación de etapa y nivel. Ahora los pesos son los mismos para
todos —40 / 30 / 15 / 15— y lo que cambia es **qué versión de pesos rige cada vacante**.

El nivel **sale de la clave** de `peso_etapa`, y `vacante` gana una columna que apunta a la
versión aprobada que le toca. Cuando una etapa no aplica a un puesto, se aprueba otra versión
**antes** de que empiecen los candidatos; nunca se reparte a mano por persona.

Lo que sí sigue dependiendo del nivel es el peso de cada criterio y de cada dimensión: el
currículum de un director no se puntúa igual que el de un operativo.

### 6 · La clave de puntuación tiene tres formas distintas

No caben en una sola columna:

- **Preguntas de estilo.** No hay respuesta correcta. Cada opción reparte puntos entre
  dimensiones: elegir A suma 2 a *velocidad con criterio* y 1 a *iniciativa*.
- **Preguntas de situación y dilemas.** Cada opción vale un puntaje de 0 a 4. A veces solo se
  define la opción buena y las demás quedan sin puntaje.
- **Preguntas de consistencia y las abiertas.** No tienen clave numérica. Tienen una explicación
  en texto de qué se espera, y las califica la inteligencia artificial de 0 a 4.

Por eso el puntaje por opción **admite estar vacío**, y las dimensiones que suma cada opción
viven en su propia tabla.

### 7 · El estado dice de quién se espera algo, y nada más

Existe una tabla con los dieciocho estados posibles, pero **no tiene pantalla de
administración**: solo cambia con una migración.

Cada estado guarda su **etapa** y su **momento** como columnas aparte. Eso permite dos cosas:
armar la bandeja de trabajo —«muéstrame todo lo que me está esperando a mí»— y **calcular** cuál
es el siguiente estado, en vez de mantener a mano una tabla de transiciones.

Lo que **no** está en el estado: si ya empezó, cuánto le queda, si asistió a la sesión. Eso vive
en la tabla de esa etapa, con su fecha. Mezclar las dos cosas es lo que antes obligaba a tener
veinticinco estados.

Por eso `intento_prueba` guarda su propia fecha de vencimiento: el barrido que busca relojes
agotados es una consulta directa sobre esa columna, y no depende de que la plantilla siga igual.

### 8 · «Solo lo suyo» no es un sí o un no

El responsable del área puede ver candidatos, pero solo los de sus vacantes. El candidato puede
ver postulaciones, pero solo las propias. El Equipo de Talento los ve todos.

Es el **mismo permiso con distinto alcance**, así que el alcance es una columna en la relación
entre rol y permiso, con tres valores: propio, sus vacantes, o todo.

Convertirlo en un simple sí o no le abriría al responsable del área los datos de todos los
candidatos de la empresa.

### 9 · Nada se borra, y los agentes de IA guardan cada intento

Cuando alguien ejerce su derecho a que borren sus datos, se vacían los campos que lo identifican
y **se conserva todo lo demás**: el historial de estados, las notas, la auditoría. Un borrado en
cascada destruiría la trazabilidad que el sistema entero existe para tener.

Y para la inteligencia artificial hacen falta tres tablas, no una:

- **`agente`** es el catálogo de los nueve, con su versión.
- **`trabajo_ia`** es el encargo —«califica el entregable de esta postulación»— con su estado y
  cuántas veces se ha intentado.
- **`ejecucion_ia`** es **cada intento por separado**: qué agente, con qué versión, qué se le
  mandó, qué respondió entero, con qué modelo, cuánto tardó, cuánto costó y **con cuánta
  confianza**.

Un encargo reintentado tres veces tiene tres ejecuciones, y las tres quedan. Con una sola tabla
habría que sobrescribir el intento anterior, que es justo lo que hay que mirar cuando un
candidato reclama su nota.

La versión del agente importa tanto como la del modelo: sin ella no se puede distinguir un
error del modelo de un cambio en las instrucciones que le dimos nosotros.

---

## Mapa general

```
  ORGANIZACION  ------------------------------- todo cuelga de aqui

   PERSONAS Y ACCESO              CONFIGURACION
  persona · usuario · rol       versiones de pesos
     permiso · area              parametros · correos
         |                              |
         | (identidad del equipo        | (toda nota apunta
         |  viene de RENASER OS)        |  a una version)
         v                              |
   SOLICITUD DE TALENTO                 |
         |                              |
         v                              |
     VACANTES ---------+                |
  puesto · nivel        |               |
  familia               |               |
         |              |               |
         v              v               v
   +------------------------------------------+
   |            POSTULACION                    |
   |  un estado a la vez · grupo de prioridad  |
   |  historial completo                       |
   +------------------------------------------+
      |        |         |          |
      v        v         v          v
   PERFIL   PRUEBA   SIMULACION  VALIDACION
  INTEGRAL     |         |           |
      ^        |         |           |
      |        v         v           v
   BANCO +  PERFIL DE TALENTO --> DECISION
  PLANTILLA  fortalezas · riesgos   verde · ambar · rojo
      |      sugerencias            sin datos · reserva
      |                                   |
   RADAR DE TALENTO <---- reserva ---------+
                                           v
                                     SEGUIMIENTO
                                   30 · 90 · 180 dias


  AGENTES DE IA                  AUDITORIA
 catalogo · encargo        quien, cuando, que cambio, por que
 ejecucion                            ^
       |                              |
       +-- toda nota automatica ------+
           apunta a su ejecucion


  RENASER OS  <---- HTTP ----  identidad del equipo
  (otro servicio)              tareas y tiempos
                               desempeno 30/90/180
```

Tres cosas que el mapa deja ver y conviene subrayar: **la evaluación cuelga del usuario**, no de
la postulación, por lo dicho en la tercera decisión; **todo lo que califica una máquina apunta a
la ejecución concreta** que produjo esa nota; y **RENASER OS es un servicio aparte**, no una
tabla más.

Hay una versión dibujada de este mismo mapa en
[diagramas/modelo-de-datos.html](diagramas/modelo-de-datos.html), que se abre en el navegador.

---

## Las tablas

Noventa y tres en total, agrupadas por área para poder leerlas de a poco. En cada una se nombran
las columnas que importan para entender qué hace, no todas.

**Para verlas todas, con tipo y clave, está el [Diccionario de datos](07-DICCIONARIO-DE-DATOS.md).**
Ese documento se consulta al escribir las migraciones; este se lee para entender el modelo.

Todas tienen `id` y fecha de creación aunque no se repita cada vez.

---

### Organización · 1 tabla

| Tabla | Para qué existe | Columnas que importan |
|---|---|---|
| `organizacion` | Renaser, y mañana cada cliente de consultoría | codigo, nombre, es_activa |

Arranca con una sola fila. Todo lo demás la referencia, directamente o a través de su padre.

---

### Personas, acceso y permisos · 7 tablas

Un rol es un conjunto de permisos con nombre guardado en la base de datos, no algo fijo en el
código. El Administrador puede crear roles nuevos y repartir permisos sin que nadie programe.

| Tabla | Para qué existe | Columnas que importan |
|---|---|---|
| `persona` | Quién es alguien. Vale para el equipo y para quien postula | nombre, apellidos, telefono, documento, fecha_nacimiento, anonimizado_en |
| `usuario` | Cómo entra al sistema | organizacion_id, persona_id, correo, contrasena_hash, usuario_renaser_os_id, area_id, es_activo |
| `area` | El departamento que contrata. Hace falta para saber qué ve un responsable y para impedir que alguien sea Evaluador de Estándar de su propia área | organizacion_id, nombre |
| `rol` | Un nombre y una lista de permisos | organizacion_id, codigo, nombre, descripcion |
| `permiso` | Una acción suelta que se puede conceder o no. Son 75 diseñados, 69 sembrados | codigo, etiqueta, grupo |
| `usuario_rol` | Una persona puede tener varios roles. Puede hacer lo que le permita cualquiera de ellos | usuario_id, rol_id |
| `rol_permiso` | Qué permisos tiene un rol y **con qué alcance** | rol_id, permiso_id, alcance |

`contrasena_hash` y `usuario_renaser_os_id` son **excluyentes**: quien tiene uno no tiene el
otro. El equipo entra con el token de RENASER OS; los candidatos, con su contraseña.

El `area_id` queda vacío en los candidatos, que no pertenecen a ningún departamento.

El permiso guarda una etiqueta en lenguaje normal —«puede cerrar una vacante»— porque la
pantalla donde se reparten permisos nunca debe mostrar nombres técnicos. Los permisos **no
llevan organización**: son los mismos para todos, y lo que cambia es quién los tiene.

El alcance tiene tres valores: **propio**, **sus vacantes** y **todo**.

---

### Consentimiento y borrado · 4 tablas

| Tabla | Para qué existe | Columnas que importan |
|---|---|---|
| `texto_consentimiento` | El texto que se acepta, versionado y con su huella | organizacion_id, tipo, version, texto, hash, publicado_en |
| `consentimiento` | Que esta persona aceptó esta versión concreta | persona_id, texto_consentimiento_id, postulacion_id, aceptado_en, ip, id_sesion, user_agent, retirado_en |
| `politica_conservacion` | Cuánto se guardan los datos y qué se hace al vencer | organizacion_id, meses, accion_al_vencer, es_activa |
| `solicitud_borrado` | Pedir el borrado y ejecutarlo son dos cosas distintas, con días de por medio | persona_id, solicitado_en, ejecutado_en, ejecutado_por_usuario_id |

**Son dos consentimientos, no uno.** El `tipo` distingue el del **proceso** —evaluar esta
postulación— del de **futuros contactos** —guardar sus datos y avisarle de otras convocatorias—.
El segundo nunca se da por supuesto, y `retirado_en` permite quitarlo sin tocar el primero.

**Y desde el multiempresa, el del proceso se firma con cada empresa.** `postulacion_id` vacío
es un consentimiento de cuenta con la plataforma (crear la cuenta, futuros contactos); lleno,
el texto de la empresa de esa vacante, aceptado al postular. Postular a tres empresas son tres
filas, cada una a nombre de la suya — lo que la ley 29733 espera: cada quien que trata datos,
nombrado y consentido. Por eso la unicidad por persona y texto rige solo en las filas de
cuenta: re-postular a la misma empresa con el mismo texto vigente vuelve a firmarse.

Se guarda la versión del texto aceptado, no un simple «sí acepté», y también su **huella**, el
**identificador de sesión** y el navegador, para poder exportar la evidencia completa.

El plazo de conservación **es un dato, no un número en el código**. Al vencer, la política dice
qué hacer: eliminar, anonimizar o pedir que renueve el consentimiento.

---

### Solicitud de Talento · 3 tablas

| Tabla | Para qué existe | Columnas que importan |
|---|---|---|
| `solicitud_talento` | Por qué hace falta contratar, antes de que exista la vacante | organizacion_id, origen, urgencia, resultado_principal, motivo, consecuencia_no_contratar, requerida_para, analisis_capacidad, area_id, estado, solicitada_por_usuario_id |
| `resultado_esperado` | Los 3 a 5 resultados del cargo, con su indicador | solicitud_talento_id, descripcion, indicador, orden |
| `evidencia_necesidad` | Qué dato hizo que el sistema la recomendara | solicitud_talento_id, tipo, descripcion, valor, ejecucion_ia_id |

`origen` dice si la pidió una persona o la detectó RENASER OS. `urgencia` tiene tres valores, y
**subirla no quita ningún requisito**: solo cambia el orden en la bandeja.

`analisis_capacidad` guarda la respuesta a «qué parte podría eliminarse, automatizarse o
redistribuirse», que es obligatoria en las dos entradas.

La evidencia solo existe cuando la solicitud la detectó el sistema, y apunta a la ejecución del
agente que la produjo.

---

### Vacantes · 8 tablas

| Tabla | Para qué existe | Columnas que importan |
|---|---|---|
| `nivel_puesto` | Los tres niveles. Determinan cuántas preguntas se responden y el tiempo objetivo | codigo, nombre, preguntas_banco, minutos_objetivo_min, minutos_objetivo_max |
| `familia` | Las siete familias de trabajo | codigo, nombre |
| `familia_afin` | Qué familias se parecen lo bastante para reutilizar evaluaciones | familia_codigo, familia_afin_codigo |
| `puesto` | El catálogo de puestos, con su nivel y su familia | organizacion_id, codigo, nombre, nivel_puesto_codigo, familia_codigo |
| `vacante` | Una convocatoria concreta | organizacion_id, solicitud_talento_id, puesto_id, titulo, descripcion, tipo_cierre, plazas, cierra_en, estado, version_pesos_id, version_plantilla_prueba_id, plantilla_evaluacion_id, responsable_usuario_id |
| `requisito_objetivo` | Lo único que puede detener una postulación sin que intervenga nadie | vacante_id, descripcion, regla, es_activo |
| `barrera_critica` | Lo que ningún promedio alto compensa, definido por vacante | vacante_id, descripcion, es_activa |
| `evaluador_estandar` | Quién revisa que la urgencia no baje el nivel, en esta vacante | vacante_id, usuario_id, puede_bloquear, asignado_por_usuario_id |

`tipo_cierre` tiene tres valores: con fecha, hasta cubrir plazas, o permanente para alimentar el
Radar.

Cerrar una vacante **detiene las postulaciones nuevas pero no cierra las que van a mitad**. Eso
lo decide una persona, candidato por candidato.

El `requisito_objetivo` guarda la **regla exacta** que se aplicó, no solo su descripción: hay que
poder demostrar por qué se detuvo esa postulación.

Las barreras críticas eran antes un catálogo por nivel. Ahora las define **cada vacante**, y las
del nivel se cargan como valores iniciales que se pueden cambiar.

El Evaluador de Estándar —antes Bar Raiser— tiene `puede_bloquear`, que arranca en falso: emite
una recomendación registrada. El sistema no deja nombrar a alguien del área que contrata.

---

### Radar de Talento · 3 tablas

⚠️ **Se modelan ahora y se construyen después.** Existen para que añadir el Radar no obligue a
rehacer nada, pero no hay pantallas todavía.

| Tabla | Para qué existe | Columnas que importan |
|---|---|---|
| `prospecto` | Alguien que interesa aunque no haya vacante | organizacion_id, persona_id, fuente, nivel_estimado_codigo, capacidades, disponibilidad, interes, ultima_evaluacion_id, es_activo |
| `prospecto_familia` | Para qué familias encaja, y cuánto | prospecto_id, familia_codigo, compatibilidad |
| `contacto_prospecto` | Cada vez que se habló con esa persona | prospecto_id, usuario_id, canal, resumen, ocurrido_en |

Un prospecto apunta a `persona`, no duplica sus datos. Alguien puede ser prospecto y candidato a
la vez sin tener dos fichas.

Un prospecto **solo existe si dio su consentimiento de futuros contactos**. Si lo retira, deja de
estar activo.

Aquí es donde pgvector encuentra su uso: antes de publicar una vacante hay que mostrar los
prospectos que podrían encajar, y eso es una búsqueda por parecido, no por igualdad.

---

### Postulación y su historia · 3 tablas

| Tabla | Para qué existe | Columnas que importan |
|---|---|---|
| `estado_postulacion` | Catálogo cerrado de los 18 estados | codigo, nombre, etapa_codigo, momento_codigo, espera_a, orden, es_final |
| `postulacion` | Un usuario en una vacante. Tiene un solo estado a la vez, nunca dos | organizacion_id, uuid, usuario_id, vacante_id, estado_codigo, grupo_prioridad, motivo_cierre, evaluacion_id, rondas_evidencia_usadas, movido_en |
| `transicion_estado` | Cada cambio de estado, guardado aparte. **No se modifica ni se borra nunca** | postulacion_id, estado_anterior_codigo, estado_nuevo_codigo, usuario_id, rol_id, es_sistema, es_por_lote, motivo, ocurrida_en |

El estado guarda **etapa** y **momento** aparte, y por eso el siguiente estado se calcula en vez
de buscarse. Los momentos son cuatro: hay que habilitarlo, le toca al candidato, está
calificando, o le toca a una persona.

`grupo_prioridad` es una columna, no un estado: alta prioridad, alto potencial con riesgo, no
priorizado, o incompatibilidad objetiva. Cambia cada vez que se recalifica una etapa.

Solo dos estados llevan motivo de cierre: **no continúa** guarda si fue por requisito objetivo,
barrera crítica, decisión roja, decisión de una persona o **paso a reserva**; **cerrada** guarda
si fue por inactividad, cierre manual, retiro del candidato, plazo vencido o borrado de datos.

Están separadas porque el candidato recibe mensajes distintos, y porque si se mezclaran, el
embudo de cada vacante mentiría: alguien que se retiró no es alguien que no dio la talla.

`es_por_lote` marca las transiciones hechas en bloque. Aunque se despachen cien de una vez,
**cada una guarda su propio motivo**.

---

### Criterios y notas · 2 tablas

Cuatro etapas puntúan repartiendo 100 puntos entre varios criterios: el currículum entre ocho,
la prueba del puesto entre los que defina cada plantilla, la simulación entre diez y la
validación entre nueve métricas. Antes eran ocho tablas con las mismas columnas repetidas cuatro
veces. Ahora son dos.

| Tabla | Para qué existe | Columnas que importan |
|---|---|---|
| `criterio` | Cualquier cosa que se puntúa, de la etapa que sea | codigo, nombre, etapa_codigo, version_plantilla_prueba_id, puntos, metodo_verificacion, orden |
| `nota_criterio` | El puntaje de un criterio para una postulación, y por qué | postulacion_id, criterio_id, puntaje, explicacion, origen, ejecucion_ia_id, calificada_por_usuario_id, ajustada_por_usuario_id, motivo_ajuste |

`metodo_verificacion` es nuevo y dice **quién puede comprobar ese criterio**: el sistema, un
agente, o una persona. El tiempo lo mide el sistema; la argumentación la califica un agente; el
criterio visual lo califica un agente y lo revisa una persona en los finalistas. Sin esta
columna se asume que la IA puede observarlo todo con la misma fiabilidad, y no es cierto.

`origen` en la nota dice de dónde salió el valor: automático de RENASER OS, agente, o persona.

Hay dos clases de criterio. **Los globales** —currículum, simulación, validación— son iguales
para toda la empresa y su peso vive en la versión de pesos. **Los de la prueba del puesto**
pertenecen a una versión de plantilla y varían por puesto; sus puntos van en la propia fila,
porque esa versión ya está congelada.

---

### El currículum · 3 tablas

| Tabla | Para qué existe | Columnas que importan |
|---|---|---|
| `cv` | El currículum de una postulación, en sus dos versiones | postulacion_id, archivo_original_id, archivo_anonimizado_id |
| `enlace_cv` | Portafolio, repositorio, proyectos | cv_id, url, tipo |
| `afirmacion_cv` | Algo que el currículum dice, con su clasificación | cv_id, texto, clasificacion, ejecucion_ia_id |

Son **dos archivos, no uno**. Antes de que la máquina lea un currículum se le quitan foto, edad,
sexo y estado civil, y esa versión recortada es la única que se le envía. El equipo sí abre el
original completo. Se guarda cuál de las dos se mandó, para poder demostrar que la regla se
cumplió.

`clasificacion` tiene cuatro valores y no dos: **demostrada**, **declarada sin verificar**,
**contradicha** y **falta información**. «No verificada» nunca equivale a mentira: es algo que
hace falta repreguntar.

---

### Banco de preguntas · 7 tablas

| Tabla | Para qué existe | Columnas que importan |
|---|---|---|
| `dimension` | Las 22 cosas que se miden: integridad, priorización, calidad, autonomía… | codigo, nombre, definicion, es_obligatoria |
| `version_banco` | Una versión del banco, en borrador o publicada | organizacion_id, tipo_banco, nivel_puesto_codigo, etiqueta, estado, publicada_por_usuario_id, publicada_en |
| `pregunta` | Una pregunta dentro de una versión | version_banco_id, codigo, bloque, tipo, enunciado, situacion, logica_interna, es_puntuable |
| `opcion` | Las opciones de respuesta | pregunta_id, letra, texto, puntaje |
| `opcion_dimension` | Cuánto suma cada opción a cada dimensión | opcion_id, dimension_codigo, incremento |
| `pregunta_dimension` | Qué dimensiones evalúa una pregunta abierta, que no tiene opciones | pregunta_id, dimension_codigo |
| `par_consistencia` | Dos preguntas que miden lo mismo y deberían responderse parecido | version_banco_id, pregunta_a_id, pregunta_b_id, diferencia_maxima |

Son 236 preguntas: 90 para Dirección, 60 para Coordinación, 50 para Ejecución, y 36 de
alineación personal. **El banco no es el examen**: de ahí se selecciona lo que aplique.

`es_obligatoria` en la dimensión es nueva: trece de las veintidós son las que el sistema tiene
que observar siempre. Las otras nueve se usan en preguntas concretas.

`version_banco.organizacion_id` **va vacío en los bancos de Renaser**, que son la biblioteca
global. Una organización que quiera el suyo crea una versión con su identificador puesto, sin
tocar el original.

Los tipos de pregunta son seis: estilo, situación, conductual, microcaso, dilema y consistencia.
Las de **estilo no suman nota**: solo dibujan el perfil, y el documento del cliente prohíbe
expresamente usarlas como filtro. Las de **consistencia** tampoco: generan alertas.

Una advertencia de nomenclatura que ya causó confusión: las preguntas de autogestión se llaman
C01 a C12, pero el «banco C» es el de Ejecución, cuyas preguntas son O01 a O50. No se puede
deducir a qué banco pertenece una pregunta por la letra de su código.

---

### Plantilla de evaluación · 2 tablas

Es lo que decide **qué preguntas del banco le tocan a cada vacante**. Sin esto, el banco entero
se aplicaría a todos, que es justo lo que el cliente pide evitar.

| Tabla | Para qué existe | Columnas que importan |
|---|---|---|
| `plantilla_evaluacion` | Una receta de selección de preguntas, versionada | organizacion_id, nombre, nivel_puesto_codigo, familia_codigo, version, estado, minutos_objetivo, vigencia_meses |
| `cuota_plantilla_evaluacion` | Cuántas preguntas de cada tipo y dimensión entran | plantilla_evaluacion_id, tipo_banco, tipo_pregunta, dimension_codigo, cantidad_min, cantidad_max |

`minutos_objetivo` es lo que permite avisar al creador cuando la configuración pasa de 60
minutos. `vigencia_meses` es lo que decide cuánto tiempo se puede reutilizar lo respondido.

---

### Evaluación · 8 tablas

Esta área es la que cuelga del usuario y no de la postulación.

| Tabla | Para qué existe | Columnas que importan |
|---|---|---|
| `evaluacion` | Las respuestas de un usuario a una plantilla concreta | organizacion_id, usuario_id, plantilla_evaluacion_id, version_banco_nivel_id, version_banco_alineacion_id, reutiliza_de_evaluacion_id, estado, vence_en, iniciada_en, terminada_en, vigente_hasta |
| `orden_pregunta` | En qué orden se le mostró cada pregunta y sus opciones. Sin esto no se puede reproducir el examen | evaluacion_id, pregunta_id, posicion, orden_opciones |
| `respuesta` | Lo que contestó | evaluacion_id, pregunta_id, opcion_id, texto, segundos, respondida_en |
| `nota_respuesta` | El puntaje de esa respuesta y **por qué** | respuesta_id, puntaje, explicacion, evidencia_citada, confianza, ejecucion_ia_id, ajustada_por_usuario_id, motivo_ajuste |
| `repregunta` | Lo que el agente vuelve a preguntar cuando la respuesta es superficial | respuesta_id, texto, orden, ejecucion_ia_id |
| `respuesta_repregunta` | Lo que contestó a esa repregunta | repregunta_id, texto, respondida_en |
| `resultado_alineacion` | El semáforo de cada uno de los tres bloques | evaluacion_id, bloque, semaforo |
| `alerta` | Contradicciones y respuestas demasiado ideales | postulacion_id, tipo, descripcion, pregunta_a_id, pregunta_b_id, confirmada_por_usuario_id |

`vence_en` es nuevo: la evaluación ahora **tiene plazo**, que fija quien crea la convocatoria.
`vigente_hasta` es distinto: dice hasta cuándo se puede reutilizar lo respondido en otra vacante.

Las repreguntas son dos tablas y no columnas sueltas porque puede haber varias por respuesta, y
hay que poder limitarlas: el documento del cliente avisa de no convertir cada pregunta en una
entrevista interminable.

`evidencia_citada` guarda qué parte de la propia respuesta usó el agente para justificar la
nota. Es lo que permite discutir una calificación sin releerlo todo.

Cada respuesta se guarda al momento, así que si se corta la luz el candidato retoma donde quedó.
Las preguntas y las opciones se muestran en orden aleatorio, distinto para cada persona, y ese
orden se guarda: es la única forma de mostrar meses después exactamente el examen que rindió.

Una alerta **nunca descarta a nadie**. Lo mismo vale para un rojo en alineación personal. Lo mismo vale para un rojo en alineación
personal — aunque `resultado_alineacion` sigue vacía: la tabla está y el panel ya la lee, pero
ningún agente la escribe todavía.

---

### Perfil de Talento · 3 tablas

Lo que sale de juntar todo. No es una nota: es un retrato con su respaldo.

| Tabla | Para qué existe | Columnas que importan |
|---|---|---|
| `perfil_talento` | El retrato consolidado de una postulación | postulacion_id, adecuacion, potencial, alto_rendimiento, confianza_evidencia, resumen, version_pesos_id, ejecucion_ia_id |
| `hallazgo_perfil` | Cada fortaleza o riesgo, con su tipo y su evidencia | perfil_talento_id, tipo, descripcion, evidencia, es_canalizable |
| `sugerencia_puesto` | «Encajaría mejor en otro sitio» | perfil_talento_id, puesto_id, familia_codigo, motivo |

`hallazgo_perfil.tipo` tiene cinco valores y **no se pueden mezclar**, que es una regla explícita
del cliente: fortaleza, riesgo crítico, riesgo desarrollable, preferencia o estilo, y falta de
evidencia. Un riesgo desarrollable y una falta de evidencia parecen lo mismo en una lista y
significan cosas opuestas.

`confianza_evidencia` distingue a quien fue evaluado a fondo de quien apenas dejó rastro. Sin
ella, un perfil con dos etapas hechas y otro con cinco se leen igual.

La sugerencia de otro puesto **no mueve nada sola**: es información para que una persona decida.

---

### Prueba del puesto · 9 tablas

| Tabla | Para qué existe | Columnas que importan |
|---|---|---|
| `plantilla_prueba` | La prueba de un puesto | organizacion_id, puesto_id, nombre |
| `version_plantilla_prueba` | Una versión concreta. Si tiene vacante, es una copia privada de esa vacante | plantilla_prueba_id, vacante_id, enunciado, modalidad, duracion_minutos, plazo_dias, minuto_cambio_min, minuto_cambio_max, minutos_extra, estado |
| `variante_cambio` | Las distintas formas que puede tomar el cambio inesperado | version_plantilla_prueba_id, texto, orden |
| `pregunta_prueba` | El catálogo de preguntas: previas, universales y del puesto | codigo, enunciado, tipo, puesto_id, revela |
| `pregunta_version_plantilla` | Cuáles eligió esta plantilla | version_plantilla_prueba_id, pregunta_prueba_id, orden |
| `entregable_requerido` | Qué cosas distintas hay que entregar, cada una con su regla | version_plantilla_prueba_id, nombre, detalle, formato, es_obligatorio, orden |
| `intento_prueba` | Cuando un candidato rinde | postulacion_id, version_plantilla_prueba_id, iniciado_en, vence_en, entregado_en, es_entrega_automatica, variante_cambio_id, minuto_cambio, cambio_mostrado_en |
| `entregable` | Lo que sube o el enlace que pega, y **cuál de los pedidos es** | intento_prueba_id, entregable_requerido_id, archivo_id, enlace, version, subido_en |
| `respuesta_prueba` | Sus respuestas a las preguntas de la prueba | intento_prueba_id, pregunta_prueba_id, texto, respondida_en |

**La prueba nueva es cronometrada, y eso está decidido.** Las cinco pruebas que Renaser ha
enviado —en `insumos/pruebas-tecnicas/`— son encargos de varios días sin reloj y sin cambio a
mitad. Son **anteriores**: el cronómetro y el cambio son precisamente la mejora que se quiere.
Sirven como modelo de contenido y de tono, no de formato.

`modalidad` se queda para poder cargar esas cinco y adaptarlas dentro del sistema, no como una
opción que se ofrezca en una vacante nueva.

⚠️ **Ojo con el tamaño del encargo.** Una prueba cronometrada dura de 60 a 120 minutos, y esas
cinco piden cosas que no caben ahí: un producto funcional más un video, o un documento de cinco
páginas más un plano. Ponerles reloj obliga a **encoger el encargo**, no solo a cronometrarlo.

**Los entregables son una tabla, no un texto.** Cada prueba real pide de uno a cuatro entregables
distintos, cada uno con su regla: «video, máximo 5 minutos», «documento, máximo 1 página»,
«presentación, máx. 10 diapositivas». Con un campo de texto libre el sistema no puede avisar de
que falta el video, ni un criterio de la rúbrica puede apuntar a un entregable concreto.

Cuando es cronometrada, el reloj lo lleva el servidor, no el navegador. `vence_en` se calcula al
empezar y se guarda: así el barrido que busca relojes agotados es una consulta directa y no
depende de que la plantilla siga igual. Cuando se acaba, el sistema entrega solo: **no existe
entregar tarde**.

**El cambio inesperado ya no es fijo.** La versión de la plantilla guarda un **rango** de
minutos, y al empezar el intento se sortea uno concreto y una variante. Los dos quedan guardados
en `intento_prueba`. Si el minuto fuera siempre el mismo, el segundo candidato ya sabría cuándo
llega.

Las preguntas de la prueba eran antes 17 fijas para todos. Ahora hay un catálogo con tres tipos
—las que se responden **antes** de producir, las **universales** y las **del puesto**— y cada
plantilla elige entre 8 y 10 universales más 3 a 5 específicas.

El sistema **no usa detectores de inteligencia artificial**. En vez de eso le pregunta al
candidato qué parte hizo con IA y qué verificó él, y eso vale 5 de los 100 puntos.

---

### Simulación de trabajo · 7 tablas

| Tabla | Para qué existe | Columnas que importan |
|---|---|---|
| `sesion_simulacion` | Una fecha con cupo. No hay límite de cuántas se crean | organizacion_id, fecha_hora, modalidad, lugar, enlace, cupo, estado, creada_por_usuario_id |
| `sesion_vacante` | Para qué vacantes sirve esa sesión: una, varias o todas | sesion_simulacion_id, vacante_id |
| `inscripcion_sesion` | El candidato eligió esta fecha | sesion_simulacion_id, postulacion_id, asistio, es_vigente, marcada_por_usuario_id |
| `tramo_simulacion` | Cómo se reparten los minutos de esta sesión | sesion_simulacion_id, codigo, nombre, minuto_inicio, minuto_fin |
| `informacion_critica` | Qué debería preguntar un candidato fuerte, qué es opcional y qué hay que descubrir | sesion_simulacion_id, tipo, texto |
| `marca_tiempo_simulacion` | Los momentos observables que el sistema anota solo | inscripcion_sesion_id, evento, ocurrida_en |
| `pregunta_generada` | Las 3 a 5 preguntas para la conversación final, y qué se respondió | postulacion_id, texto, alerta_id, ejecucion_ia_id, respuesta, riesgo_resuelto, registrada_por_usuario_id |

`modalidad` dice si la sesión es grupal o individual. Arranca en grupal y es configurable: antes
el modelo daba por hecho que siempre era grupal y presencial.

Los tramos eran un catálogo global de seis filas. Ahora **cada sesión guarda los suyos**, porque
el reparto de los 120 minutos es configurable. Se copian de un valor por defecto al crear la
sesión.

⚠️ **Solo se registran actos observables.** Antes había una marca para «cuándo detectó el
bloqueo», y el cliente lo prohíbe expresamente: no se puede registrar lo que alguien pensó, solo
lo que hizo. Lo que queda es cuándo apareció el bloqueo, cuándo lo abrió, cuándo preguntó, cuándo
comunicó el riesgo, la primera evidencia, la entrega y la autocrítica.

La `informacion_critica` es lo que permite evaluar la calidad de sus preguntas sin adivinar: si
no se declara de antemano qué debería haber preguntado, calificar «no preguntó lo importante» es
una opinión.

Las horas se guardan con precisión de segundos y con zona horaria, porque de esas marcas salen
las preguntas de la conversación final.

---

### Validación práctica y decisión · 7 tablas

| Tabla | Para qué existe | Columnas que importan |
|---|---|---|
| `validacion` | El periodo de trabajo | postulacion_id, modalidad, tipo_vinculacion, dias, inicio_en, fin_en, estado, habilitada_por_usuario_id |
| `etapa` | Las cinco etapas del embudo | codigo, nombre, orden |
| `nota_etapa` | La nota de cada etapa, atada a la versión de pesos con que se calculó | postulacion_id, etapa_codigo, puntaje, version_pesos_id |
| `decision` | El semáforo final | postulacion_id, semaforo, nota_global, version_pesos_id, decidida_por_usuario_id, motivo |
| `barrera_detectada` | Una barrera crítica encontrada en un candidato concreto | postulacion_id, barrera_critica_id, explicacion, ejecucion_ia_id, confirmada_por_usuario_id |
| `opinion_evaluador_estandar` | Su revisión escrita | postulacion_id, usuario_id, texto, bloquea |
| `evidencia_adicional` | Lo que se pide cuando sale ámbar | postulacion_id, numero, motivo, enunciado, solicitada_por_usuario_id, puntaje, entregada_en |

`modalidad` es nueva y tiene dos valores: simulación extendida sin trabajo productivo, o trabajo
real. La segunda **no se puede habilitar** hasta que `tipo_vinculacion` esté registrado. Y `dias`
también es nueva: ya no son siete fijos.

El semáforo tiene ahora **cinco** valores. «Sin datos» es distinto de rojo —falta evidencia, no
falla la persona— y «reserva» es distinto de los dos: la persona vale, pero para otra cosa.

Una barrera crítica la puede detectar la máquina, pero **siempre la confirma una persona antes
de que bloquee a nadie**. La decisión de contratar es del responsable del área o de Dirección.

Las rondas de evidencia adicional tienen tope —dos por defecto, configurable—. Al llegar al tope
el sistema ya no permite otra y obliga a decidir con lo que hay.

---

### Configuración · 8 tablas

Casi todo lo que el cliente cambia seguido vive aquí, no en el código.

| Tabla | Para qué existe | Columnas que importan |
|---|---|---|
| `version_pesos` | Una versión de todos los pesos, en borrador o publicada | organizacion_id, etiqueta, estado, publicada_por_usuario_id, publicada_en |
| `peso_etapa` | Cuánto pesa cada etapa. **Ya no depende del nivel** | version_pesos_id, etapa_codigo, peso |
| `peso_componente_perfil` | Cómo se reparte el 40% entre currículum, psicométrico y evaluación | version_pesos_id, componente, peso |
| `peso_dimension` | Cuánto pesa cada dimensión en cada nivel | version_pesos_id, nivel_puesto_codigo, dimension_codigo, peso |
| `peso_criterio` | Cuánto vale cada criterio en cada nivel, en las tres etapas globales | version_pesos_id, nivel_puesto_codigo, criterio_id, peso |
| `parametro` | Los valores sueltos: días sin avanzar antes de cerrar, tope de rondas de evidencia, cupo por defecto, qué datos se ocultan del currículum | organizacion_id, codigo, valor, tipo, descripcion, modificado_por_usuario_id |
| `plantilla_correo` | Los textos que se envían, versionados | organizacion_id, codigo, version, asunto, cuerpo |
| `instruccion_ia` | Los textos que se le mandan a cada agente, versionados | agente_codigo, version, texto, publicada_por_usuario_id |

**El nivel salió de la clave de `peso_etapa`.** Los pesos son 40 / 30 / 15 / 15 para todos, y lo
que cambia por vacante es a qué versión apunta. Donde el nivel **sí** sigue mandando es en el
peso de cada criterio y de cada dimensión.

El módulo psicométrico todavía no existe. Su 5% se reparte entre las otras dos partes mientras
tanto, y por eso los pesos de los componentes son datos y no números escritos en el código.

Las instrucciones que recibe cada agente son configuración versionada, igual que las preguntas.
Solo Dirección las cambia, y cada calificación guarda con qué versión se produjo.

---

### Agentes de inteligencia artificial · 4 tablas

| Tabla | Para qué existe | Columnas que importan |
|---|---|---|
| `agente` | El catálogo de los nueve, con su versión | codigo, nombre, descripcion, version, es_activo |
| `trabajo_ia` | El encargo pendiente. Se procesa en segundo plano; el candidato no espera | organizacion_id, agente_codigo, postulacion_id, referencia_tabla, referencia_id, estado, intentos, creado_en, terminado_en |
| `ejecucion_ia` | Cada intento por separado | trabajo_ia_id, organizacion_id, agente_codigo, version_agente, objetivo, modelo, proveedor, version_modelo, instruccion_ia_id, envio, respuesta, confianza, tokens_entrada, tokens_salida, costo, duracion_ms, es_exitosa |
| `tarifa_modelo` | El precio por millón de tokens de cada modelo, con vigencia por fecha | proveedor, modelo, precio_entrada_por_millon, precio_salida_por_millon, vigente_desde |

**Cada llamada al modelo tiene precio.** Al cerrar una ejecución se escribe su `costo` con la
tarifa vigente en ese momento: cuando el proveedor cambia sus precios se registra una tarifa
nueva y lo ya ejecutado conserva la suya —sin vigencia, un cambio de precios reescribiría el
pasado—. La tarifa no tiene «vigente hasta»: rige la de fecha más reciente que ya empezó, así
no hay huecos ni solapes que validar. Sin tarifa registrada el costo queda vacío y se anota
un aviso: la contabilidad nunca rompe una calificación.

Sobre ese costo trabaja el **tope mensual por organización** (parámetro `tope_mensual_ia`,
que administra la plataforma): al cruzar el 80% del mes sale un aviso único, y al 100% los
trabajos nuevos nacen `EN_ESPERA` —un estado del encargo, no un fallo— hasta que el tope suba
o empiece el mes. El candidato los ve «en curso», que es la verdad.

Los nueve agentes son: Necesidad de Talento, Cazatalentos, Evidencia de Currículum, Evaluador,
Potencial y Riesgo, Prueba del Puesto, Simulación, Desempeño y Aprendizaje. Cada ejecución guarda
cuál fue, para poder medir por separado si uno se está equivocando.

Se guarda la respuesta **completa**, no solo la nota: si alguien reclama una calificación, hay
que poder revisar en qué se basó. Y se guarda **con cuánta confianza** la dio, que es lo que
distingue una nota firme de una que el propio modelo dio con dudas.

`version_agente` importa tanto como `version_modelo`: sin ella no se puede distinguir un error
del modelo de un cambio en las instrucciones que le dimos nosotros.

Tres reglas que la máquina no puede romper:

- **Una nota sin explicación no se acepta.** La explicación es obligatoria.
- **Si falla, la calificación queda pendiente y se reintenta.** Nunca se guarda un cero por un
  problema técnico. Si el encargo lleva demasiado tiempo atascado, se avisa al equipo.
- **Si falta un dato, la máquina dice que falta.** Nunca lo inventa.

---

### Auditoría, archivos y desempeño · 5 tablas

| Tabla | Para qué existe | Columnas que importan |
|---|---|---|
| `auditoria` | Toda acción que cambia una decisión | organizacion_id, usuario_id, rol_id, accion, entidad, entidad_id, valor_anterior, valor_nuevo, motivo, ocurrida_en |
| `archivo` | Los archivos viven fuera de la base; aquí solo está su ruta | organizacion_id, ruta, nombre_original, tamano, tipo, subido_en |
| `correo_enviado` | A quién, cuándo y **qué decía** | usuario_id, plantilla_correo_codigo, version, asunto, cuerpo, canal, estado_entrega, enviado_en |
| `seguimiento_desempeno` | El corte de los 30, 90 o 180 días, con su diagnóstico | organizacion_id, postulacion_id, dias, resultado_esperado, porcentaje_logrado, obstaculo, causa, accion, registrado_en |
| `metrica_desempeno` | Cada una de las diez medidas de ese corte | seguimiento_desempeno_id, metrica, valor, origen |

La auditoría **no se puede modificar ni borrar**, y eso no es configurable: no existe la casilla
para permitirlo. También registra los cambios de permisos.

Del correo se guarda el cuerpo ya armado, no solo cuál plantilla se usó. Si mañana alguien edita
la plantilla, lo que se le envió a esa persona sigue siendo lo que dice el registro.

La base guarda la ruta del archivo, nunca el archivo. Así los entregables pesados —vídeos,
diseños, archivos de hasta 200 MB— no inflan la base de datos. **El almacén es propio de este
sistema**, no el de RENASER OS, y es privado siempre: para abrir un archivo, el backend genera
un enlace firmado que dura poco.

El seguimiento de desempeño **ya no está bloqueado**: RENASER OS existe y expone por su API los
objetivos, tareas, plazos, retrabajo y resultados. `metrica_desempeno.origen` dice si el valor
llegó solo o lo puso una persona.

---

## Lo que la base impide por sí sola, y lo que no

No todas las reglas del sistema caben en una restricción de base de datos. Conviene tener claro
cuáles sí, porque las que no, hay que probarlas en el código.

### Las hace cumplir la base

- Una postulación tiene **un solo estado a la vez**, y ese estado existe en el catálogo.
- Un usuario no puede postular dos veces a la misma vacante.
- Una persona no puede tener dos cuentas en la misma organización.
- El correo es único **dentro de una organización**, no en toda la base.
- No se puede registrar una respuesta a una pregunta que no está en la versión que le tocó.
- Los registros de auditoría y de transiciones **no se pueden actualizar ni borrar**.
- Toda transición manual y todo ajuste de nota **exigen motivo escrito**.
- El puntaje de una etapa siempre apunta a una versión de pesos concreta.
- No se puede inscribir a un candidato en una sesión que no sirve para su vacante.
- Toda vacante apunta a una solicitud de talento.

### Tienen que vivir en el código

- **Que una rúbrica publicada sume 100.** En borrador se avisa y se deja guardar; al publicar,
  no. La base no puede distinguir esos dos momentos con una restricción simple.
- **Que no se nombre Evaluador de Estándar a alguien del área que contrata.** Requiere mirar la
  vacante y el área en la misma comprobación.
- **Que nadie se quede sin permisos de administración.** Son dos reglas: nadie edita su propio
  rol, y no se puede guardar un cambio que dejaría a **cero personas** con permiso para
  administrar roles. La segunda exige contar filas de tres tablas a la vez.
- **Que un usuario tenga contraseña o identificador de RENASER OS, pero no los dos.** Es una
  restricción posible en la base, pero la regla real —los candidatos nunca tienen identificador
  externo— depende del rol, y eso ya no cabe.
- **Que la reutilización respete familia y vigencia.** Depende de comparar dos vacantes y una
  fecha.
- **Los límites configurables**, como el tope de dos rondas de evidencia adicional: el número
  está en la tabla de parámetros y puede cambiar.
- **El aislamiento por organización.** Cada consulta lo aplica. No hay forma de que la base lo
  garantice sola sin seguridad por fila, que aquí no se usa porque el dueño de la seguridad es
  Spring Boot.
- **Los permisos de cada llamada.** Ocultar un botón no es seguridad.

### Nunca existen, ni siquiera como opción

Seis cosas que no deben aparecer como permiso, porque si aparecen como casilla alguien las va a
marcar algún día:

1. Que un candidato vea a otros candidatos
2. Que las claves de puntuación lleguen al portal del candidato
3. Que se pueda borrar o modificar la auditoría
4. Que se pueda saltar el consentimiento
5. Que la máquina contrate a alguien sin que intervenga una persona
6. Que alguien vea datos de otra organización

---

## Cómo se atiende un borrado de datos

Aquí se ve por qué conviene tener `persona` separada de `usuario`: casi todo el borrado ocurre en
una sola tabla.

1. Se registra la solicitud con fecha. Solo Dirección o Administrador pueden ejecutarla.
2. Al ejecutarla se vacían los campos de `persona`: nombre, apellidos, teléfono, documento,
   fecha de nacimiento. Se marca `anonimizado_en`.
3. En `usuario` se anula el correo y se desactiva la cuenta.
4. Se borran sus archivos del almacén —currículum y entregables— y las filas quedan apuntando a
   nada.
5. Sus respuestas de texto libre se vacían, porque pueden contener datos personales.
6. Si era prospecto del Radar, deja de estar activo.
7. **Se conserva todo lo demás**: puntajes, historial de estados, auditoría, métricas.
8. Sus postulaciones abiertas pasan a cerradas, con motivo «pidió borrar sus datos».

El resultado es que el embudo de esa vacante sigue cuadrando y la auditoría sigue completa, pero
ya no hay forma de saber de quién se trataba.

Hay **tres cosas distintas** que es fácil confundir: retirar una postulación conserva todo;
retirar el consentimiento de futuros contactos solo saca a la persona del Radar; pedir el borrado
es lo de arriba.

---

## Qué trae la base el primer día

Datos que se cargan con la primera migración, no a mano:

- La **organización** Renaser, marcada como **dueña de la plataforma** (`es_plataforma`)
- Los **18 estados** de la postulación, con su etapa y su momento
- Los **71 permisos** que las migraciones siembran, con su etiqueta y su grupo — contados
  de las migraciones, no de los documentos de diseño. Cuatro son recientes: de la V37,
  `personalizar_instrumentos` para el administrador de cada empresa y
  `administrar_plataforma` solo para el de la plataforma; de la V40,
  `ver_inscritos_simulacion` y `administrar_permisos`
- Los **cinco roles** iniciales con sus permisos: candidato, equipo de talento, responsable del
  área, dirección y administrador. Es como arranca el sistema, no cómo queda para siempre
- Las **22 dimensiones**, con cuáles de ellas son obligatorias
- Las **cinco etapas**, los **tres niveles** y las **siete familias**
- Los **ocho criterios** del currículum, los **diez** de la simulación y las **nueve métricas**
  de la validación
- Las **preguntas de la prueba**: las previas, las diez universales y las del puesto
- Los **nueve agentes**, con su versión inicial
- Las **236 preguntas** del banco, como primera versión publicada del banco de la
  plataforma — desde la `V37` no hay filas «globales» sin dueño: compartir es leer las de
  la plataforma
- Las **once plantillas de prueba**, con sus tiempos y sus variantes de cambio
- Una **plantilla de evaluación** por nivel y familia
- Una primera **versión de pesos** con 40 / 30 / 15 / 15
- Una **política de conservación** con su valor por defecto
- Los **parámetros** y las **plantillas de correo**

---

## Lo que queda pendiente

**Los pares de consistencia no están enumerados.** Los documentos del cliente dicen que hay
preguntas que se comparan entre sí para detectar contradicciones, pero nunca dicen cuáles con
cuáles. La tabla está prevista y arranca vacía.

**Las familias afines no están decididas.** Hace falta que Renaser diga qué familias se parecen
lo bastante para reutilizar una evaluación, y cuántos meses dura esa vigencia. Mientras no lo
diga, la tabla arranca vacía y no se reutiliza nada, que es el comportamiento seguro.

**El catálogo de puestos.** Hay once plantillas de prueba nombradas, pero falta fijar los nombres
definitivos de los puestos antes de cargar los datos iniciales.

**Los valores por defecto de las plantillas de evaluación.** Cuántas preguntas de cada tipo y
dimensión entran por nivel y familia. Se puede arrancar con una receta razonable y ajustarla.

**La figura contractual de la validación productiva.** Bloquea esa modalidad, no el modelo: la
otra modalidad funciona desde el primer día.

**Cómo responde el sistema cuando la API de RENASER OS no está.** Está decidido qué hacer en cada
caso, pero falta el detalle de reintentos y tiempos de espera.

---

## Documentos relacionados

- [Requisitos funcionales](01-REQUISITOS-FUNCIONALES.md) — qué hace el sistema
- [Requisitos no funcionales](02-REQUISITOS-NO-FUNCIONALES.md) — tecnología y seguridad
- [Estados de la postulación](03-ESTADOS-POSTULACION.md) — los 18 estados y sus transiciones
- [Qué hace el sistema](00-QUE-HACE-EL-SISTEMA.md) — el sistema entero, sin nada técnico
- [Alcance del MVP](08-ALCANCE-DEL-MVP.md) — qué tablas entran en cada hito, y cuáles no
- [Roles y permisos](04-ROLES-Y-PERMISOS.md) — quién puede hacer qué
- [Diccionario de datos](07-DICCIONARIO-DE-DATOS.md) — cada tabla con todas sus columnas
- [Qué cambia con el documento nuevo](insumos/CAMBIOS-DEL-DOCUMENTO-NUEVO.md) — por qué el modelo
  pasó de 71 tablas a 92, y de 92 a 93 al mirar las pruebas reales
- [Diagrama del modelo](diagramas/modelo-de-datos.html) — se abre en el navegador
