# Diccionario de datos

Sistema de selección de personal — Renaser Consulting
Versión 2.2 · 2026-08-26 · Puesto al día con las migraciones hasta la `V40`

Cada tabla con todas sus columnas, tipos y claves. **Este documento se consulta**, no se lee de
corrido: es la base para escribir las migraciones de Flyway.

Lo que llegó después de la versión 2.0 va marcado con su migración entre paréntesis. La única
tabla de la base que no está aquí es `agent_run`, que es del motor de agentes y no de selección.

Para entender *por qué* el modelo es así, está el [Modelo de datos](05-MODELO-DE-DATOS.md).

---

## Cómo leer esto

**Todas las tablas tienen estas dos columnas** aunque no se repitan cada vez:

| Columna | Tipo | Qué guarda |
|---|---|---|
| `id` | bigint, autogenerado | Clave primaria |
| `creado_en` | timestamptz | Cuándo se creó la fila. Por defecto, ahora |

Las excepciones están marcadas en su tabla.

**Tipos que se usan:**

| Tipo | Para qué |
|---|---|
| `bigint` | Claves y referencias |
| `text` | Cualquier texto. En PostgreSQL no cuesta más que `varchar` |
| `text` con restricción | Los códigos de catálogo y los valores cerrados |
| `boolean` | Sí o no |
| `timestamptz` | Fecha y hora **con zona horaria**. Nunca `timestamp` a secas |
| `date` | Solo fecha, sin hora |
| `numeric(5,2)` | Puntajes y pesos |
| `integer` | Cuentas, minutos, posiciones |
| `jsonb` | Solo donde la forma del dato es libre |
| `uuid` | El identificador público de una postulación |

**Convenciones de las claves foráneas:**

- Todas son `bigint` y llevan el nombre de la tabla destino más `_id`.
- Las que apuntan a un catálogo llevan `_codigo` y son `text`.
- **Ninguna borra en cascada.** Nada se borra en este sistema.

**La columna `organizacion_id`** va en la raíz de cada árbol, no en cada hoja. Las tablas que la
llevan están marcadas. Los catálogos globales no la llevan.

---

# 1 · Organización

## `organizacion`

Renaser, y mañana cada cliente de consultoría.

| Columna | Tipo | Oblig. | Qué guarda |
|---|---|---|---|
| `id` | bigint | sí | Clave |
| `codigo` | text | sí | `RENASER` |
| `nombre` | text | sí | |
| `es_activa` | boolean | sí | Por defecto verdadero |

**Clave primaria:** `id` · **Único:** `codigo`

Arranca con una sola fila. Todo lo demás la referencia, directa o indirectamente.

---

# 2 · Personas, acceso y permisos

## `persona`

Quién es alguien. Vale para el equipo de Renaser y para quien postula.

| Columna | Tipo | Oblig. | Qué guarda |
|---|---|---|---|
| `id` | bigint | sí | Clave |
| `nombre` | text | no | Se vacía al anonimizar |
| `apellidos` | text | no | Se vacía al anonimizar |
| `telefono` | text | no | Se vacía al anonimizar |
| `documento` | text | no | DNI o equivalente. Se vacía al anonimizar |
| `fecha_nacimiento` | date | no | Se vacía al anonimizar |
| `anonimizado_en` | timestamptz | no | Vacío mientras la persona conserva sus datos |

**Clave primaria:** `id`

Todas las columnas de identidad admiten vacío **porque el borrado las vacía**. Si fueran
obligatorias, anonimizar exigiría borrar la fila y con ella toda la trazabilidad.

## `usuario`

Cómo entra al sistema. Apunta a una persona.

| Columna | Tipo | Oblig. | Qué guarda |
|---|---|---|---|
| `id` | bigint | sí | Clave |
| `organizacion_id` | bigint | sí | |
| `persona_id` | bigint | sí | |
| `correo` | text | no | Se anula al borrar los datos |
| `contrasena_hash` | text | no | **Solo los candidatos.** Vacío en el equipo |
| `usuario_renaser_os_id` | text | no | **Solo el equipo.** Vacío en los candidatos |
| `area_id` | bigint | no | Vacío en los candidatos |
| `es_activo` | boolean | sí | |
| `ultimo_acceso_en` | timestamptz | no | |

**Clave primaria:** `id`
**Único:** `persona_id` + `organizacion_id` · `organizacion_id` + `lower(correo)`
**Apunta a:** `organizacion`, `persona`, `area`
**Restricción:** no puede tener `contrasena_hash` y `usuario_renaser_os_id` a la vez

`usuario_renaser_os_id` es **texto y no una clave foránea**: RENASER OS es otro servicio que
habla por HTTP y no comparte base de datos con este.

El correo es único **dentro de una organización**, no en toda la base. La misma persona puede ser
candidata en dos empresas distintas.

## `area`

El departamento que contrata.

| Columna | Tipo | Oblig. | Qué guarda |
|---|---|---|---|
| `id` | bigint | sí | Clave |
| `organizacion_id` | bigint | sí | |
| `nombre` | text | sí | |
| `es_activa` | boolean | sí | |

**Clave primaria:** `id` · **Único:** `organizacion_id` + `nombre`

Hace falta para dos cosas: saber qué ve un responsable de área, e impedir que alguien sea
Evaluador de Estándar de su propia área.

## `rol`

Un nombre y una lista de permisos.

| Columna | Tipo | Oblig. | Qué guarda |
|---|---|---|---|
| `id` | bigint | sí | Clave |
| `organizacion_id` | bigint | sí | |
| `codigo` | text | sí | `TALENTO`, `DIRECCION`… |
| `nombre` | text | sí | Como se muestra |
| `descripcion` | text | no | |
| `es_sistema` | boolean | sí | Los cinco iniciales. No se pueden borrar |

**Clave primaria:** `id` · **Único:** `organizacion_id` + `codigo`

## `permiso`

Una acción suelta que se puede conceder o no. **Sembrados hay 69**, contados sobre las
migraciones. La matriz de [Roles y permisos](04-ROLES-Y-PERMISOS.md) enumera 75: la diferencia
son los que están diseñados y todavía no existen en la base, y ese documento describe el
sistema completo mientras este sigue al código.

| Columna | Tipo | Oblig. | Qué guarda |
|---|---|---|---|
| `id` | bigint | sí | Clave |
| `codigo` | text | sí | `cerrar_vacante` |
| `etiqueta` | text | sí | «Cerrar una vacante». En lenguaje normal |
| `grupo` | text | sí | `SOLICITUDES`, `VACANTES`, `CANDIDATOS`, `EVALUACION`, `SESIONES`, `VALIDACION`, `DECISION`, `CIERRE`, `RADAR`, `METRICAS`, `CONFIGURACION` |
| `orden` | integer | sí | Dentro de su grupo |

**Clave primaria:** `id` · **Único:** `codigo`

Los grupos que existen de verdad hoy son nueve —`DECISION` y `RADAR` están solo diseñados—, y
la simulación va en `SESIONES`, no en `SIMULACION`.

**No lleva organización:** los permisos son los mismos para todos. Lo que cambia es quién los
tiene. Solo crece con una migración: los dos últimos, `ver_inscritos_simulacion` y
`administrar_permisos`, llegaron con la `V40`.

La etiqueta existe porque la pantalla donde se reparten permisos nunca debe mostrar nombres
técnicos.

## `usuario_rol`

Una persona puede tener varios roles.

| Columna | Tipo | Oblig. | Qué guarda |
|---|---|---|---|
| `usuario_id` | bigint | sí | |
| `rol_id` | bigint | sí | |
| `asignado_por_usuario_id` | bigint | no | |
| `creado_en` | timestamptz | sí | |

**Clave primaria:** `usuario_id` + `rol_id` · **Sin columna `id`**

Puede hacer lo que le permita cualquiera de sus roles.

## `rol_permiso`

Qué permisos tiene un rol y con qué alcance.

| Columna | Tipo | Oblig. | Qué guarda |
|---|---|---|---|
| `rol_id` | bigint | sí | |
| `permiso_id` | bigint | sí | |
| `alcance` | text | sí | `PROPIO`, `SUS_VACANTES` o `TODO` |
| `creado_en` | timestamptz | sí | |

**Clave primaria:** `rol_id` + `permiso_id` · **Sin columna `id`**

El alcance va aquí y no en el permiso porque es **el mismo permiso** el que tienen el responsable
del área y el Equipo de Talento: lo que cambia es hasta dónde llega.

## `enlace_acceso`

Cómo entra el candidato al portal **sin contraseña**: se le manda un enlace por correo. Llegó
con la `V24`.

| Columna | Tipo | Oblig. | Qué guarda |
|---|---|---|---|
| `id` | bigint | sí | Clave |
| `postulacion_id` | bigint | sí | |
| `token_hash` | text | sí | El SHA-256 del token en hexadecimal, 64 caracteres |
| `vence_en` | timestamptz | sí | |
| `primer_uso_en` | timestamptz | no | |
| `ultimo_uso_en` | timestamptz | no | |
| `usos` | integer | sí | Por defecto 0 |
| `revocado_en` | timestamptz | no | Se llena para invalidarlo antes de tiempo, **sin borrar la fila** |
| `creado_en` | timestamptz | sí | |

**Clave primaria:** `id` · **Único:** `token_hash` · **Apunta a:** `postulacion`

Se guarda **la huella del token, no el token**: quien lea la base no puede entrar como el
candidato.

Las dos fechas de uso no son adorno: un enlace mandado y nunca usado señala **a un candidato al
que hay que llamar**, y sin ellas no hay forma de distinguirlo de uno que entró y se fue.

---

# 3 · Consentimiento y borrado

## `texto_consentimiento`

El texto que se acepta, versionado y con su huella.

| Columna | Tipo | Oblig. | Qué guarda |
|---|---|---|---|
| `id` | bigint | sí | Clave |
| `organizacion_id` | bigint | sí | |
| `tipo` | text | sí | `PROCESO` o `FUTUROS_CONTACTOS` |
| `version` | text | sí | `1.0`, `1.1`… |
| `texto` | text | sí | El texto completo |
| `hash` | text | sí | Huella SHA-256 del texto |
| `publicado_en` | timestamptz | no | Vacío mientras es borrador |

**Clave primaria:** `id` · **Único:** `organizacion_id` + `tipo` + `version`

**Nunca se modifica una versión publicada.** Editar crea otra.

## `consentimiento`

Que esta persona aceptó esta versión concreta.

| Columna | Tipo | Oblig. | Qué guarda |
|---|---|---|---|
| `id` | bigint | sí | Clave |
| `persona_id` | bigint | sí | |
| `texto_consentimiento_id` | bigint | sí | |
| `nombre_registrado` | text | no | Cómo se llamaba al aceptar |
| `aceptado_en` | timestamptz | sí | |
| `ip` | text | no | |
| `id_sesion` | text | no | |
| `user_agent` | text | no | |
| `retirado_en` | timestamptz | no | Solo aplica al de futuros contactos |

**Clave primaria:** `id` · **Único:** `persona_id` + `texto_consentimiento_id`

Se guarda la **versión aceptada**, no un simple «sí acepté». Con eso, la huella, la sesión y el
navegador, la evidencia se puede exportar entera.

`retirado_en` permite quitar el consentimiento de futuros contactos **sin tocar** el del proceso.

## `politica_conservacion`

Cuánto se guardan los datos y qué se hace al vencer.

| Columna | Tipo | Oblig. | Qué guarda |
|---|---|---|---|
| `id` | bigint | sí | Clave |
| `organizacion_id` | bigint | sí | |
| `meses` | integer | sí | |
| `accion_al_vencer` | text | sí | `ELIMINAR`, `ANONIMIZAR` o `RENOVAR_CONSENTIMIENTO` |
| `es_activa` | boolean | sí | Solo una activa por organización |
| `definida_por_usuario_id` | bigint | no | |

**Clave primaria:** `id` · **Único parcial:** `organizacion_id` cuando `es_activa`

El plazo **es un dato, no un número en el código**. La ley obliga a fijarlo y a decirlo en el
texto de consentimiento; escribirlo en el código significa un despliegue cada vez que el abogado
cambie de opinión.

## `solicitud_borrado`

Pedir el borrado y ejecutarlo son dos cosas distintas, con días de por medio.

| Columna | Tipo | Oblig. | Qué guarda |
|---|---|---|---|
| `id` | bigint | sí | Clave |
| `persona_id` | bigint | sí | |
| `motivo` | text | no | Lo que escribió la persona |
| `solicitado_en` | timestamptz | sí | |
| `ejecutado_en` | timestamptz | no | Vacío hasta que se ejecuta |
| `ejecutado_por_usuario_id` | bigint | no | |

**Clave primaria:** `id`

Solo Dirección o Administrador pueden ejecutarla.

---

# 4 · Solicitud de Talento

## `solicitud_talento`

Por qué hace falta contratar, antes de que exista la vacante.

| Columna | Tipo | Oblig. | Qué guarda |
|---|---|---|---|
| `id` | bigint | sí | Clave |
| `organizacion_id` | bigint | sí | |
| `origen` | text | sí | `DIRECTA` o `DETECTADA` |
| `urgencia` | text | sí | `NORMAL`, `PRIORITARIA` o `URGENTE` |
| `estado` | text | sí | `BORRADOR`, `ABIERTA`, `CON_VACANTE`, `RECHAZADA`, `ARCHIVADA` |
| `area_id` | bigint | sí | |
| `nivel_puesto_codigo` | text | no | |
| `familia_codigo` | text | no | |
| `resultado_principal` | text | sí | El resultado del cargo |
| `motivo` | text | sí | Por qué se necesita la persona |
| `consecuencia_no_contratar` | text | sí | Qué pasa si no se contrata |
| `requerida_para` | date | no | Cuándo se necesita |
| `analisis_capacidad` | text | sí | Qué podría eliminarse, automatizarse o redistribuirse |
| `capacidades_indispensables` | text | no | |
| `capacidades_aprendibles` | text | no | |
| `modalidad` | text | no | |
| `horario` | text | no | |
| `compensacion` | text | no | |
| `solicitada_por_usuario_id` | bigint | no | Vacío si la detectó el sistema |
| `responsable_usuario_id` | bigint | no | Quién se hace cargo de contratar |

**Clave primaria:** `id`
**Apunta a:** `organizacion`, `area`, `nivel_puesto`, `familia`, `usuario`

`analisis_capacidad` es **obligatorio en las dos entradas**. Que la necesidad la pida una persona
no la exime de responder si el trabajo se podría evitar.

Subir la urgencia **no quita ningún requisito**: solo cambia el orden en la bandeja.

## `resultado_esperado`

Los 3 a 5 resultados del cargo, con su indicador.

| Columna | Tipo | Oblig. | Qué guarda |
|---|---|---|---|
| `id` | bigint | sí | Clave |
| `solicitud_talento_id` | bigint | sí | |
| `descripcion` | text | sí | |
| `indicador` | text | no | Cómo se mide |
| `orden` | integer | sí | |

**Clave primaria:** `id` · **Único:** `solicitud_talento_id` + `orden`

Que sean entre 3 y 5 es una comprobación del código, no de la base.

## `evidencia_necesidad`

Qué dato hizo que el sistema recomendara la solicitud.

| Columna | Tipo | Oblig. | Qué guarda |
|---|---|---|---|
| `id` | bigint | sí | Clave |
| `solicitud_talento_id` | bigint | sí | |
| `tipo` | text | sí | `CARGA`, `RETRASO`, `CALIDAD`, `CAPACIDAD`, `RETRABAJO`, `CRECIMIENTO` |
| `descripcion` | text | sí | |
| `valor` | text | no | La cifra, si la hay |
| `ejecucion_ia_id` | bigint | no | Qué ejecución la produjo |

**Clave primaria:** `id`

Solo existe cuando `solicitud_talento.origen` es `DETECTADA`.

---

# 5 · Vacantes

## `nivel_puesto`

Los tres niveles. Catálogo cerrado.

| Columna | Tipo | Oblig. | Qué guarda |
|---|---|---|---|
| `codigo` | text | sí | `DIRECCION`, `SUPERVISION`, `EJECUCION` |
| `nombre` | text | sí | |
| `preguntas_banco` | integer | sí | 90, 60 o 50 |
| `minutos_objetivo_min` | integer | sí | 40, 35 o 25 |
| `minutos_objetivo_max` | integer | sí | 50, 45 o 35 |
| `orden` | integer | sí | |

**Clave primaria:** `codigo` · **Sin columna `id`**

Los minutos objetivo son lo que permite avisar cuando una plantilla de evaluación se pasa.

## `familia`

Las siete familias de trabajo. Catálogo cerrado.

| Columna | Tipo | Oblig. | Qué guarda |
|---|---|---|---|
| `codigo` | text | sí | `DIRECCION_NEGOCIO`, `OPERACIONES`, `CRECIMIENTO`, `TECNOLOGIA`, `CREATIVO`, `TALENTO`, `SEGURIDAD_CRITICA` |
| `nombre` | text | sí | |
| `descripcion` | text | no | |
| `orden` | integer | sí | |

**Clave primaria:** `codigo` · **Sin columna `id`**

La familia es lo que decide qué preguntas se seleccionan y si una evaluación se puede reutilizar.

## `familia_afin`

Qué familias se parecen lo bastante para reutilizar evaluaciones.

| Columna | Tipo | Oblig. | Qué guarda |
|---|---|---|---|
| `familia_codigo` | text | sí | |
| `familia_afin_codigo` | text | sí | |
| `creado_en` | timestamptz | sí | |

**Clave primaria:** `familia_codigo` + `familia_afin_codigo` · **Sin columna `id`**
**Restricción:** las dos no pueden ser iguales

**Arranca vacía**, porque Renaser todavía no ha dicho qué familias son afines. Vacía significa que
no se reutiliza nada, que es el comportamiento seguro.

La relación **se guarda en los dos sentidos**: si A es afín a B, hacen falta las dos filas. Es
más simple que una consulta que mire en ambas direcciones cada vez.

## `puesto`

El catálogo de puestos, con su nivel y su familia.

| Columna | Tipo | Oblig. | Qué guarda |
|---|---|---|---|
| `id` | bigint | sí | Clave |
| `organizacion_id` | bigint | sí | |
| `codigo` | text | sí | |
| `nombre` | text | sí | |
| `nivel_puesto_codigo` | text | sí | |
| `familia_codigo` | text | sí | |
| `es_activo` | boolean | sí | |

**Clave primaria:** `id` · **Único:** `organizacion_id` + `codigo`

## `vacante`

Una convocatoria concreta.

| Columna | Tipo | Oblig. | Qué guarda |
|---|---|---|---|
| `id` | bigint | sí | Clave |
| `organizacion_id` | bigint | sí | |
| `solicitud_talento_id` | bigint | sí | **Toda vacante cuelga de una solicitud** |
| `puesto_id` | bigint | sí | |
| `titulo` | text | sí | Como se publica |
| `descripcion` | text | sí | |
| `proposito` | text | no | |
| `responsabilidades` | text | no | |
| `requisitos` | text | no | Los que se publican |
| `modalidad` | text | no | |
| `horario` | text | no | |
| `ubicacion` | text | no | |
| `compensacion_publica` | text | no | Solo si Renaser decide publicarla |
| `tipo_cierre` | text | sí | `FECHA`, `PLAZAS` o `PERMANENTE` |
| `plazas` | integer | no | Solo si `tipo_cierre` es `PLAZAS` |
| `abre_en` | timestamptz | no | |
| `cierra_en` | timestamptz | no | Solo si `tipo_cierre` es `FECHA` |
| `estado` | text | sí | `BORRADOR`, `PUBLICADA`, `CERRADA` |
| `nota_minima` | numeric(5,2) | no | Interna, nunca se publica |
| `version_pesos_id` | bigint | sí | Qué versión de pesos rige |
| `version_plantilla_prueba_id` | bigint | no | |
| `plantilla_evaluacion_id` | bigint | no | |
| `aplica_evaluacion` | boolean | sí | Apagado, quien postula no recibe la evaluación del banco: va directo a la bandeja del equipo y su única evaluación es la prueba del puesto. Por defecto encendido |
| `prueba_cierra_en` | timestamptz | no | Cuándo cierra la prueba de esta vacante, para todos. Vacío: se cuentan los días de la versión de la plantilla desde que cada uno empieza |
| `responsable_usuario_id` | bigint | sí | Quién se hace cargo de contratar |
| `publicada_en` | timestamptz | no | |
| `cerrada_en` | timestamptz | no | |

**Clave primaria:** `id`
**Apunta a:** `organizacion`, `solicitud_talento`, `puesto`, `version_pesos`,
`version_plantilla_prueba`, `plantilla_evaluacion`, `usuario`

⚠️ **Hay una referencia circular** entre `vacante` y `version_plantilla_prueba`: la vacante apunta
a la versión que usa, y una versión puede ser una copia privada de una vacante. Flyway no puede
crear las dos a la vez: se crean las tablas primero y una de las dos claves foráneas se añade
después.

Cerrar una vacante **detiene las postulaciones nuevas pero no cierra las que van a mitad**.

## `plantilla_correo_vacante`

Qué texto de correo usa **esta** vacante en lugar del que el sistema mandaría. Sin fila, sale
el de siempre.

| Columna | Tipo | Oblig. | Qué guarda |
|---|---|---|---|
| `id` | bigint | sí | Clave |
| `vacante_id` | bigint | sí | |
| `aviso_codigo` | text | sí | El código que la máquina de estados iba a mandar (`PRUEBA_DISPONIBLE`, `POSTULACION_AVANZA`…) |
| `plantilla_codigo` | text | sí | El que sale en su lugar |
| `creado_en` | timestamptz | sí | |

**Único:** `vacante_id` + `aviso_codigo` · **Apunta a:** `vacante`

No hay clave foránea contra `plantilla_correo`: allí el código se repite por versión, así que
no hay a qué apuntar. Que el código exista y esté activo se comprueba al configurarlo.

## `requisito_objetivo`

Lo único que puede detener una postulación sin que intervenga nadie.

| Columna | Tipo | Oblig. | Qué guarda |
|---|---|---|---|
| `id` | bigint | sí | Clave |
| `vacante_id` | bigint | sí | |
| `descripcion` | text | sí | «Licencia de conducir vigente» |
| `regla` | text | sí | La comprobación exacta que se aplica |
| `es_activo` | boolean | sí | |

**Clave primaria:** `id`

Se guarda la **regla exacta**, no solo su descripción: hay que poder demostrar por qué se detuvo
esa postulación.

## `barrera_critica`

Lo que ningún promedio alto compensa, definido por vacante.

| Columna | Tipo | Oblig. | Qué guarda |
|---|---|---|---|
| `id` | bigint | sí | Clave |
| `vacante_id` | bigint | sí | |
| `descripcion` | text | sí | |
| `es_activa` | boolean | sí | |

**Clave primaria:** `id`

Antes era un catálogo por nivel. Ahora las define **cada vacante**, y las del nivel se cargan como
valores iniciales que se pueden cambiar.

## `evaluador_estandar`

Quién revisa que la urgencia no baje el nivel, en esta vacante.

| Columna | Tipo | Oblig. | Qué guarda |
|---|---|---|---|
| `id` | bigint | sí | Clave |
| `vacante_id` | bigint | sí | |
| `usuario_id` | bigint | sí | |
| `puede_bloquear` | boolean | sí | Por defecto falso |
| `asignado_por_usuario_id` | bigint | sí | |

**Clave primaria:** `id` · **Único:** `vacante_id` + `usuario_id`

Antes se llamaba Bar Raiser. `puede_bloquear` arranca en falso: emite una recomendación
registrada, que es lo que Renaser pide para la primera versión.

**Que no sea del área que contrata** es una comprobación del código: exige mirar la vacante y el
área en la misma consulta.

---

# 6 · Radar de Talento

⚠️ **Estas tres tablas se crean vacías.** El Radar se modela ahora y se construye después.

## `prospecto`

Alguien que interesa aunque no haya vacante.

| Columna | Tipo | Oblig. | Qué guarda |
|---|---|---|---|
| `id` | bigint | sí | Clave |
| `organizacion_id` | bigint | sí | |
| `persona_id` | bigint | sí | |
| `fuente` | text | sí | `PROCESO_ANTERIOR`, `REFERIDO`, `MANUAL`, `CONVOCATORIA_PERMANENTE` |
| `nivel_estimado_codigo` | text | no | |
| `capacidades` | text | no | |
| `evidencia` | text | no | |
| `disponibilidad` | text | no | |
| `interes` | text | no | |
| `ultima_evaluacion_id` | bigint | no | La evaluación vigente, si la hay |
| `es_activo` | boolean | sí | Falso si retiró el consentimiento |

**Clave primaria:** `id` · **Único:** `organizacion_id` + `persona_id`

Apunta a `persona`, **no duplica sus datos**. Alguien puede ser prospecto y candidato a la vez sin
tener dos fichas.

Un prospecto **solo existe si dio su consentimiento de futuros contactos**. Si lo retira,
`es_activo` pasa a falso.

## `prospecto_familia`

Para qué familias encaja, y cuánto.

| Columna | Tipo | Oblig. | Qué guarda |
|---|---|---|---|
| `prospecto_id` | bigint | sí | |
| `familia_codigo` | text | sí | |
| `compatibilidad` | numeric(5,2) | no | 0 a 100 |
| `creado_en` | timestamptz | sí | |

**Clave primaria:** `prospecto_id` + `familia_codigo` · **Sin columna `id`**

## `contacto_prospecto`

Cada vez que se habló con esa persona.

| Columna | Tipo | Oblig. | Qué guarda |
|---|---|---|---|
| `id` | bigint | sí | Clave |
| `prospecto_id` | bigint | sí | |
| `usuario_id` | bigint | sí | Quién lo contactó |
| `canal` | text | sí | |
| `resumen` | text | sí | |
| `ocurrido_en` | timestamptz | sí | |

**Clave primaria:** `id`

---

# 7 · Postulación y su historia

## `estado_postulacion`

Catálogo cerrado de los **18 estados**. Solo cambia con una migración.

| Columna | Tipo | Oblig. | Qué guarda |
|---|---|---|---|
| `codigo` | text | sí | `PERFIL_POR_CONFIRMAR` |
| `nombre` | text | sí | Como se muestra |
| `etapa_codigo` | text | no | Vacío en los estados de entrada y finales |
| `momento_codigo` | text | no | `POR_HABILITAR`, `TURNO_CANDIDATO`, `CALIFICANDO`, `POR_CONFIRMAR` |
| `espera_a` | text | sí | `CANDIDATO`, `SISTEMA`, `TALENTO`, `AREA`, `NADIE` |
| `orden` | integer | sí | Para el recorrido normal |
| `es_final` | boolean | sí | |

**Clave primaria:** `codigo` · **Sin columna `id`**

Guardar **etapa** y **momento** aparte es lo que permite calcular el siguiente estado —el
siguiente momento de esta etapa, o el primero de la siguiente— en vez de mantener a mano una
tabla de transiciones.

`espera_a` es lo que arma la bandeja de trabajo: «muéstrame todo lo que me está esperando a mí».

Los 18: `POSTULADA` · `PERFIL_TURNO_CANDIDATO` · `PERFIL_CALIFICANDO` · `PERFIL_POR_CONFIRMAR` ·
`PRUEBA_TURNO_CANDIDATO` · `PRUEBA_CALIFICANDO` · `PRUEBA_POR_CONFIRMAR` ·
`SIMULACION_POR_HABILITAR` · `SIMULACION_TURNO_CANDIDATO` · `SIMULACION_POR_CONFIRMAR` ·
`VALIDACION_POR_HABILITAR` · `VALIDACION_TURNO_CANDIDATO` · `VALIDACION_POR_CONFIRMAR` ·
`DECISION_TURNO_CANDIDATO` · `DECISION_POR_CONFIRMAR` · `CONTRATADO` · `NO_CONTINUA` · `CERRADA`

## `postulacion`

Un usuario en una vacante.

| Columna | Tipo | Oblig. | Qué guarda |
|---|---|---|---|
| `id` | bigint | sí | Clave |
| `organizacion_id` | bigint | sí | |
| `uuid` | uuid | sí | El identificador que se muestra al candidato |
| `usuario_id` | bigint | sí | |
| `vacante_id` | bigint | sí | |
| `estado_codigo` | text | sí | Uno solo a la vez, nunca dos |
| `grupo_prioridad` | text | no | `ALTA`, `POTENCIAL_CON_RIESGO`, `NO_PRIORIZADO`, `INCOMPATIBLE` |
| `motivo_cierre` | text | no | Solo en los estados finales de cierre |
| `evaluacion_id` | bigint | no | Cuál evaluación le corresponde |
| `rondas_evidencia_usadas` | integer | sí | Por defecto 0. Tope configurable |
| `movido_en` | timestamptz | sí | Cuándo cambió de estado por última vez |

**Clave primaria:** `id` · **Único:** `usuario_id` + `vacante_id` · `uuid`
**Apunta a:** `organizacion`, `usuario`, `vacante`, `estado_postulacion`, `evaluacion`

`uuid` existe para no exponer un número correlativo: con un `id` secuencial cualquiera puede
adivinar cuántas postulaciones hay.

`grupo_prioridad` es **una columna, no un estado**, y cambia cada vez que se recalifica una etapa.

`motivo_cierre` toma valores distintos según el estado:

| Estado | Motivos |
|---|---|
| `NO_CONTINUA` | `REQUISITO_OBJETIVO`, `BARRERA_CRITICA`, `DECISION_ROJA`, `DECISION_PERSONA`, `PASA_A_RESERVA` |
| `CERRADA` | `INACTIVIDAD`, `CIERRE_MANUAL`, `RETIRO_CANDIDATO`, `PLAZO_VENCIDO`, `BORRADO_DATOS` |

`movido_en` es lo que alimenta «cuántos días lleva sin avanzar» y el cierre por inactividad.

## `transicion_estado`

Cada cambio de estado. **No se modifica ni se borra nunca.**

| Columna | Tipo | Oblig. | Qué guarda |
|---|---|---|---|
| `id` | bigint | sí | Clave |
| `postulacion_id` | bigint | sí | |
| `estado_anterior_codigo` | text | no | Vacío en la primera |
| `estado_nuevo_codigo` | text | sí | |
| `usuario_id` | bigint | no | Vacío si lo hizo el sistema |
| `rol_id` | bigint | no | Con qué rol lo hizo |
| `es_sistema` | boolean | sí | |
| `es_por_lote` | boolean | sí | Si se despachó en bloque |
| `motivo` | text | no | **Obligatorio si no es del sistema** |
| `ocurrida_en` | timestamptz | sí | |

**Clave primaria:** `id`
**Restricción:** `motivo` no puede estar vacío cuando `es_sistema` es falso
**No admite UPDATE ni DELETE**

`es_por_lote` marca las transiciones hechas en bloque. Aunque se despachen cien de una vez,
**cada una guarda su propio motivo**.

Se guarda el rol además del usuario porque una persona puede tener varios, y la auditoría necesita
saber con cuál actuó.

---

# 8 · Criterios y notas

## `criterio`

Cualquier cosa que se puntúa, de la etapa que sea.

| Columna | Tipo | Oblig. | Qué guarda |
|---|---|---|---|
| `id` | bigint | sí | Clave |
| `codigo` | text | sí | `RESULTADOS_DEMOSTRABLES` |
| `nombre` | text | sí | |
| `descripcion` | text | no | |
| `etapa_codigo` | text | sí | A qué etapa pertenece |
| `version_plantilla_prueba_id` | bigint | no | **Solo los de la prueba del puesto** |
| `puntos` | numeric(5,2) | no | Solo los de la prueba. Los globales van en `peso_criterio` |
| `metodo_verificacion` | text | sí | `SISTEMA`, `AGENTE` o `PERSONA` |
| `orden` | integer | sí | |

**Clave primaria:** `id`
**Único:** `codigo` + `version_plantilla_prueba_id`, **declarado `NULLS NOT DISTINCT`**

⚠️ **Sin `NULLS NOT DISTINCT` esta restricción no sirve para los criterios globales.** En
PostgreSQL dos NULL no chocan en un índice único, así que los ocho criterios del currículum
—que tienen `version_plantilla_prueba_id` vacío— se podrían insertar repetidos con el mismo
código y nada se quejaría. La alternativa es un índice único parcial sobre `codigo` cuando la
columna es nula.

`metodo_verificacion` dice **quién puede comprobar ese criterio**. El tiempo lo mide el sistema;
la argumentación la califica un agente; el criterio visual lo califica un agente y lo revisa una
persona. Sin esta columna se asume que la IA puede observarlo todo con la misma fiabilidad.

Hay dos clases. **Los globales** —currículum, simulación, validación— tienen
`version_plantilla_prueba_id` vacío y su peso vive en la versión de pesos. **Los de la prueba**
pertenecen a una versión congelada y sus puntos van en la propia fila.

## `nota_criterio`

El puntaje de un criterio para una postulación, y por qué.

| Columna | Tipo | Oblig. | Qué guarda |
|---|---|---|---|
| `id` | bigint | sí | Clave |
| `postulacion_id` | bigint | sí | |
| `criterio_id` | bigint | sí | |
| `puntaje` | numeric(5,2) | sí | |
| `explicacion` | text | sí | **Obligatoria siempre** |
| `origen` | text | sí | `AUTOMATICO`, `AGENTE` o `PERSONA` |
| `confianza` | numeric(5,2) | no | Solo si la puso un agente |
| `ejecucion_ia_id` | bigint | no | Qué ejecución la produjo |
| `calificada_por_usuario_id` | bigint | no | Si la puso una persona |
| `ajustada_por_usuario_id` | bigint | no | Si alguien la cambió después |
| `motivo_ajuste` | text | no | **Obligatorio si hay ajuste** |
| `ajustada_en` | timestamptz | no | |

**Clave primaria:** `id` · **Único:** `postulacion_id`

`resultado_orgulloso` es «cuéntanos qué cambió gracias a tu trabajo y cómo lo comprobaste», que
el formulario exige. Vive aquí y no en la postulación porque es evidencia del candidato, como el
CV, y se vacía junto con él al anonimizar. + `criterio_id`
**Restricción:** `motivo_ajuste` no puede estar vacío si `ajustada_por_usuario_id` no lo está

`origen` dice de dónde salió el valor. En la validación práctica es lo que permite mostrar si la
métrica llegó sola de RENASER OS o la puso el responsable.

**Una nota sin explicación no se guarda.** No es una convención: es una restricción.

---

# 9 · El currículum

## `cv`

El currículum de una postulación, en sus dos versiones.

| Columna | Tipo | Oblig. | Qué guarda |
|---|---|---|---|
| `id` | bigint | sí | Clave |
| `postulacion_id` | bigint | sí | |
| `archivo_original_id` | bigint | no | Lo que subió. Se borra al anonimizar |
| `archivo_anonimizado_id` | bigint | no | Lo único que ve la máquina |
| `texto_extraido` | text | no | Ya sin los datos ocultos |
| `resultado_orgulloso` | text | no | El texto obligatorio del formulario de postular. Se vacía al anonimizar |
| `texto_anonimizado` | text | no | El texto que **de verdad** viaja al proveedor, sin edad, sexo ni estado civil (`V16`) |
| `anonimizado_en` | timestamptz | no | Cuándo se anonimizó. Vacío significa que todavía no ha pasado por ahí |

**Clave primaria:** `id` · **Único:** `postulacion_id`

Son **dos archivos, no uno**. Antes de que la máquina lea un currículum se le quitan foto, edad,
sexo y estado civil, y esa versión recortada es la única que se le envía. Se guarda cuál se mandó
para poder demostrar que la regla se cumplió.

## `enlace_cv`

Portafolio, repositorio, proyectos.

| Columna | Tipo | Oblig. | Qué guarda |
|---|---|---|---|
| `id` | bigint | sí | Clave |
| `cv_id` | bigint | sí | |
| `url` | text | sí | |
| `tipo` | text | no | `PORTAFOLIO`, `REPOSITORIO`, `PUBLICACION`, `PRODUCTO`, `OTRO` |

**Clave primaria:** `id`

## `afirmacion_cv`

Algo que el currículum dice, con su clasificación.

| Columna | Tipo | Oblig. | Qué guarda |
|---|---|---|---|
| `id` | bigint | sí | Clave |
| `cv_id` | bigint | sí | |
| `texto` | text | sí | |
| `clasificacion` | text | sí | `DEMOSTRADA`, `DECLARADA`, `CONTRADICHA`, `FALTA_INFO` |
| `pregunta_validacion` | text | no | Qué habría que repreguntar |
| `ejecucion_ia_id` | bigint | no | |

**Clave primaria:** `id`

Cuatro valores y no dos. **«Declarada» nunca equivale a mentira**: es algo que hace falta
repreguntar, y por eso la columna de al lado guarda la pregunta.

## `dato_cv`

La ficha que la IA saca del currículum: los datos sueltos, ya ordenados. Llegó con la `V19`.

| Columna | Tipo | Oblig. | Qué guarda |
|---|---|---|---|
| `id` | bigint | sí | Clave |
| `postulacion_id` | bigint | sí | |
| `nombre` | text | no | |
| `email` | text | no | |
| `telefono` | text | no | |
| `perfil_resumen` | text | no | |
| `habilidades` | text | no | Separadas por `\|`, **no en su propia tabla**: no se consultan por habilidad, se enseñan. Una tabla aparte solo añadiría un JOIN |
| `experiencia_meses_total` | integer | no | |
| `ultimo_puesto` | text | no | |
| `ultima_empresa` | text | no | |
| `ultima_meses_duracion` | integer | no | |
| `educacion_maxima` | text | no | |
| `ejecucion_ia_id` | bigint | no | De qué llamada salió. Es lo que permite **abrir una ficha de hace seis meses y ver qué se le mandó al modelo y qué contestó** |
| `actualizado_en` | timestamptz | sí | |
| `creado_en` | timestamptz | sí | |

**Clave primaria:** `id` · **Único:** `postulacion_id` · **Apunta a:** `postulacion`, `ejecucion_ia`

Una fila por postulación. Cuando el candidato postula a tres vacantes su currículum se lee tres
veces y quedan tres filas sin relación entre sí: eso es lo que vino a resolver el
[perfil del candidato](#20--el-perfil-del-candidato), que cuelga de la persona.

---

# 10 · Banco de preguntas

## `dimension`

Las 22 cosas que se miden. Catálogo cerrado.

| Columna | Tipo | Oblig. | Qué guarda |
|---|---|---|---|
| `codigo` | text | sí | `INT`, `OWN`, `INI`, `CRI`, `SER`, `COM`, `CTL`, `SUP`, `PRI`, `DEC`, `VEL`, `LRN`, `SYS`, `QUA`, `REL`, `AUT`, `PPL`, `BUS`, `POT`, `SELF`, `VAL`, `FIT` |
| `nombre` | text | sí | |
| `definicion` | text | sí | Qué debe observar el sistema |
| `es_obligatoria` | boolean | sí | Trece de las veintidós |
| `orden` | integer | sí | |

**Clave primaria:** `codigo` · **Sin columna `id`**

`es_obligatoria` marca las trece que el sistema tiene que observar siempre. Las otras nueve se
usan en preguntas concretas.

Cuatro de los códigos —`POT`, `SELF`, `VAL`, `FIT`— no están en el banco maestro pero se usan en
preguntas reales. Sin ellos, esas preguntas apuntarían a dimensiones que no existen.

## `version_banco`

Una versión del banco, en borrador o publicada.

| Columna | Tipo | Oblig. | Qué guarda |
|---|---|---|---|
| `id` | bigint | sí | Clave |
| `organizacion_id` | bigint | no | **Vacío en la biblioteca global de Renaser** |
| `tipo_banco` | text | sí | `NIVEL` o `ALINEACION` |
| `nivel_puesto_codigo` | text | no | Solo si `tipo_banco` es `NIVEL` |
| `etiqueta` | text | sí | |
| `estado` | text | sí | `BORRADOR` o `PUBLICADA` |
| `publicada_por_usuario_id` | bigint | no | |
| `publicada_en` | timestamptz | no | |

**Clave primaria:** `id`

`organizacion_id` **vacío** significa biblioteca global de Renaser. Una organización que quiera su
propio banco crea una versión con su identificador puesto, sin tocar el original.

**Una versión publicada no se modifica.** El borrador lo prepara Talento; publicar es de
Dirección.

`tipo_banco` existe porque no se puede deducir del código de la pregunta: las de autogestión se
llaman C01 a C12, pero el «banco C» es el de Ejecución, cuyas preguntas son O01 a O50.

## `pregunta`

Una pregunta dentro de una versión.

| Columna | Tipo | Oblig. | Qué guarda |
|---|---|---|---|
| `id` | bigint | sí | Clave |
| `version_banco_id` | bigint | sí | |
| `codigo` | text | sí | `D01`, `S14`, `O33`, `C07` |
| `bloque` | text | no | Para las de alineación personal |
| `tipo` | text | sí | `ESTILO`, `SITUACION`, `CONDUCTUAL`, `MICROCASO`, `DILEMA`, `CONSISTENCIA` |
| `enunciado` | text | sí | |
| `situacion` | text | no | El contexto, cuando lo hay |
| `logica_interna` | text | no | Qué se espera. **Nunca llega al portal** |
| `es_puntuable` | boolean | sí | Falso en estilo y consistencia |
| `orden` | integer | sí | |
| `peso` | smallint | no | De 0 a 2 (`V20`). **Vacío en las 200 preguntas del v0.1, y eso es lo correcto**: aquel banco no tenía pesos, y ponerle uno inventado cambiaría notas ya dadas |
| `es_clave` | boolean | sí | El ítem clave (★ del documento). En el v3 siempre coincide con peso 2, pero se guarda aparte porque son dos cosas: una dice cuánto vale y la otra que hay que preguntar por ella en la entrevista |
| `es_eliminatorio` | boolean | sí | Descarta aunque el puntaje sea alto. Marcarla **no basta para aplicar el filtro** —la condición vive en `filtro_eliminatorio`—, pero evita mostrarla como si sumara |
| `formula_puntaje` | text | no | La fórmula escrita, en los ítems V que no dan tramos |
| `rangos_de_pregunta_codigo` | text | no | Remite a los tramos de otra pregunta («Misma tabla que D57»). Se guarda la remisión, no una copia: si cambia la de origen, no quedan dos versiones |
| `casos_pedidos` | smallint | no | Cuántos casos se piden. Un «5 campos x 3» son 15 casillas pero **cinco preguntas repetidas tres veces** |

**Clave primaria:** `id` · **Único:** `version_banco_id` + `codigo`

Las de **estilo no suman nota**: solo dibujan el perfil, y el cliente prohíbe expresamente usarlas
como filtro. Las de **consistencia** tampoco: generan alertas. Eso es lo que dice `es_puntuable`.

## `opcion`

Las opciones de respuesta.

| Columna | Tipo | Oblig. | Qué guarda |
|---|---|---|---|
| `id` | bigint | sí | Clave |
| `pregunta_id` | bigint | sí | |
| `letra` | text | sí | `A`, `B`, `C`, `D` |
| `texto` | text | sí | |
| `puntaje` | numeric(5,2) | no | **Admite vacío** |
| `valor` | numeric(5,2) | no | El valor de −2 a +2 que EF-4 esconde detrás de cada afirmación (`V20`) |
| `es_distractor` | boolean | sí | Los elementos inventados que INV y DE mezclan, y que el candidato no distingue |
| `orden_correcto` | smallint | no | En SEC, que pide ordenar cinco pasos: el lugar que le toca a este |

**Clave primaria:** `id` · **Único:** `pregunta_id` + `letra`

`puntaje` admite vacío porque en las preguntas de estilo no hay respuesta correcta, y en algunas
de situación solo se define la opción buena.

## `opcion_dimension`

Cuánto suma cada opción a cada dimensión.

| Columna | Tipo | Oblig. | Qué guarda |
|---|---|---|---|
| `opcion_id` | bigint | sí | |
| `dimension_codigo` | text | sí | |
| `incremento` | numeric(5,2) | sí | Normalmente 2 en la principal y 1 en la secundaria |
| `creado_en` | timestamptz | sí | |

**Clave primaria:** `opcion_id` + `dimension_codigo` · **Sin columna `id`**

## `pregunta_dimension`

Qué dimensiones evalúa una pregunta abierta, que no tiene opciones.

| Columna | Tipo | Oblig. | Qué guarda |
|---|---|---|---|
| `pregunta_id` | bigint | sí | |
| `dimension_codigo` | text | sí | |
| `creado_en` | timestamptz | sí | |

**Clave primaria:** `pregunta_id` + `dimension_codigo` · **Sin columna `id`**

## `par_consistencia`

Dos preguntas que miden lo mismo y deberían responderse parecido.

| Columna | Tipo | Oblig. | Qué guarda |
|---|---|---|---|
| `id` | bigint | sí | Clave |
| `version_banco_id` | bigint | sí | |
| `pregunta_a_id` | bigint | sí | |
| `pregunta_b_id` | bigint | sí | |
| `diferencia_maxima` | numeric(5,2) | no | A partir de aquí se genera alerta. **Dejó de ser obligatoria en la `V20`**: los pares del v3 no la usan |
| `penalizacion_porcentaje` | numeric(5,2) | no | Cuánto descuenta la contradicción, en los pares del v3 (`V20`) |
| `separacion_minima_items` | smallint | no | Cuántos ítems tienen que mediar entre las dos preguntas para que el candidato no las vea juntas |
| `condicion` | text | no | Cuándo cuenta como contradicción, cuando no basta con la diferencia |

**Clave primaria:** `id` · **Único:** `pregunta_a_id` + `pregunta_b_id`

**Arranca vacía.** Los documentos del cliente dicen que hay preguntas que se comparan entre sí,
pero nunca dicen cuáles con cuáles.

## `campo_caso`

Los campos que hay que llenar en un caso descompuesto (CD). Llegó con la `V20`.

| Columna | Tipo | Oblig. | Qué guarda |
|---|---|---|---|
| `id` | bigint | sí | Clave |
| `pregunta_id` | bigint | sí | |
| `orden` | integer | sí | |
| `etiqueta` | text | sí | El rótulo de la casilla |
| `validacion` | text | no | Qué se acepta: la lista de valores, el rango o el límite de caracteres |
| `creado_en` | timestamptz | sí | |

**Clave primaria:** `id` · **Único:** `pregunta_id` + `orden` · **Apunta a:** `pregunta`

## `rango_pregunta`

Los tramos de puntaje de los ítems V: según en qué tramo caiga la respuesta, ese es el puntaje.

| Columna | Tipo | Oblig. | Qué guarda |
|---|---|---|---|
| `id` | bigint | sí | Clave |
| `pregunta_id` | bigint | sí | |
| `orden` | integer | sí | |
| `condicion` | text | sí | El tramo, tal como lo escribe el documento |
| `puntaje` | numeric(5,2) | sí | Lo que vale caer ahí |
| `genera_bandera` | boolean | sí | Algunos tramos no solo dan 0: además **levantan una bandera para la entrevista** |
| `creado_en` | timestamptz | sí | |

**Clave primaria:** `id` · **Único:** `pregunta_id` + `orden` · **Apunta a:** `pregunta`

## `multiplicador_bloque`

Cuánto pesa cada bloque según la familia del puesto: el mismo bloque no vale igual para un
cargo de dirección que para uno de ejecución.

| Columna | Tipo | Oblig. | Qué guarda |
|---|---|---|---|
| `id` | bigint | sí | Clave |
| `version_banco_id` | bigint | sí | |
| `familia_documento` | text | sí | El nombre de familia **tal como lo escribe el documento del cliente** |
| `familia_codigo` | text | no | La familia del catálogo, cuando se ha podido casar |
| `bloque` | text | sí | |
| `multiplicador` | numeric(4,2) | sí | |
| `creado_en` | timestamptz | sí | |

**Clave primaria:** `id` · **Único:** `version_banco_id` + `familia_documento` + `bloque`
**Apunta a:** `version_banco`, `familia`

Se guardan **las dos** formas de nombrar la familia a propósito: la del documento no siempre
coincide con el catálogo, y perder la original impediría rastrear de dónde salió el número.

## `umbral_nivel`

A partir de qué porcentaje se considera cada resultado.

| Columna | Tipo | Oblig. | Qué guarda |
|---|---|---|---|
| `id` | bigint | sí | Clave |
| `version_banco_id` | bigint | sí | |
| `porcentaje_min` | numeric(5,2) | sí | Desde cuánto aplica |
| `resultado` | text | sí | Lo que se concluye |
| `nivel` | text | no | La etiqueta del nivel, si la hay |
| `creado_en` | timestamptz | sí | |

**Clave primaria:** `id` · **Único:** `version_banco_id` + `porcentaje_min`
**Apunta a:** `version_banco`

Cuelgan de la **versión** y no de la configuración general: cambiar un umbral no puede mover la
nota de quien ya se evaluó.

## `filtro_eliminatorio`

Los cinco filtros de la sección 0.4 del documento: descartan al candidato **aunque su puntaje
sea alto**.

| Columna | Tipo | Oblig. | Qué guarda |
|---|---|---|---|
| `id` | bigint | sí | Clave |
| `version_banco_id` | bigint | sí | |
| `codigo` | text | sí | |
| `descripcion` | text | sí | |
| `preguntas` | text | sí | Los códigos a los que mira, separados por coma. Son varios porque **la misma regla aplica al ítem equivalente de cada banco** (D70, C44, O39) |
| `creado_en` | timestamptz | sí | |

**Clave primaria:** `id` · **Único:** `version_banco_id` + `codigo` · **Apunta a:** `version_banco`

Aquí vive la condición; `pregunta.es_eliminatorio` solo sirve para no mostrar la pregunta como
si sumara.

---

# 11 · Plantilla de evaluación

Es lo que decide **qué preguntas del banco le tocan a cada vacante**. Sin esto, el banco entero se
aplicaría a todos, que es justo lo que el cliente pide evitar.

## `plantilla_evaluacion`

Una receta de selección de preguntas, versionada.

| Columna | Tipo | Oblig. | Qué guarda |
|---|---|---|---|
| `id` | bigint | sí | Clave |
| `organizacion_id` | bigint | sí | |
| `nombre` | text | sí | |
| `nivel_puesto_codigo` | text | sí | |
| `familia_codigo` | text | no | Vacío si vale para todas |
| `version` | integer | sí | |
| `estado` | text | sí | `BORRADOR` o `PUBLICADA` |
| `minutos_objetivo` | integer | sí | |
| `vigencia_meses` | integer | sí | Cuánto se puede reutilizar lo respondido |
| `publicada_por_usuario_id` | bigint | no | |
| `publicada_en` | timestamptz | no | |

**Clave primaria:** `id` · **Único:** `organizacion_id` + `nombre` + `version`

`minutos_objetivo` es lo que permite avisar al creador cuando la configuración se pasa de 60
minutos. `vigencia_meses` decide cuánto tiempo se puede reutilizar lo respondido en otra vacante.

## `cuota_plantilla_evaluacion`

Cuántas preguntas de cada tipo y dimensión entran.

| Columna | Tipo | Oblig. | Qué guarda |
|---|---|---|---|
| `id` | bigint | sí | Clave |
| `plantilla_evaluacion_id` | bigint | sí | |
| `tipo_banco` | text | sí | `NIVEL` o `ALINEACION` |
| `tipo_pregunta` | text | no | Vacío si no importa el tipo |
| `dimension_codigo` | text | no | Vacío si no importa la dimensión |
| `cantidad_min` | integer | sí | |
| `cantidad_max` | integer | sí | |

**Clave primaria:** `id`
**Restricción:** `cantidad_min` no puede ser mayor que `cantidad_max`

---

# 12 · Evaluación

Esta área cuelga del **usuario**, no de la postulación. Es lo que permite no hacerle repetir lo
que ya contestó.

## `evaluacion`

Las respuestas de un usuario a una plantilla concreta.

| Columna | Tipo | Oblig. | Qué guarda |
|---|---|---|---|
| `id` | bigint | sí | Clave |
| `organizacion_id` | bigint | sí | |
| `usuario_id` | bigint | sí | |
| `plantilla_evaluacion_id` | bigint | sí | |
| `version_banco_nivel_id` | bigint | sí | |
| `version_banco_alineacion_id` | bigint | no | |
| `reutiliza_de_evaluacion_id` | bigint | no | De cuál sacó el núcleo ya respondido |
| `estado` | text | sí | `PENDIENTE`, `EN_CURSO`, `TERMINADA`, `VENCIDA` |
| `vence_en` | timestamptz | no | Plazo para completarla |
| `iniciada_en` | timestamptz | no | |
| `terminada_en` | timestamptz | no | |
| `vigente_hasta` | timestamptz | no | Hasta cuándo se puede reutilizar |

**Clave primaria:** `id`
**Apunta a:** `organizacion`, `usuario`, `plantilla_evaluacion`, `version_banco`, `evaluacion`

`vence_en` y `vigente_hasta` son **dos cosas distintas**: el primero es el plazo para terminarla;
el segundo, hasta cuándo sirve para otra vacante.

**`reutiliza_de_evaluacion_id` es lo que hace posible la reutilización parcial.** La regla no es
todo o nada: el núcleo común se reutiliza y las preguntas propias del puesto se vuelven a
generar. Así que cuando alguien postula a un puesto de familia afín se crea una evaluación
**nueva** —que contiene solo lo regenerado— y esta columna apunta a la anterior, de donde sale el
núcleo.

Para calcular la nota se leen las dos: las respuestas propias y las de la cadena. Con una sola
referencia desde la postulación no se podría, porque habría que elegir entre reutilizarlo todo o
nada.

La cadena tiene **un solo eslabón**: una evaluación nueva reutiliza de una anterior, y esa
anterior ya no reutiliza de ninguna. Encadenar más haría imposible saber qué versión del banco
respondió cada cosa.

Que haya empezado lo dice `iniciada_en`, **no un estado de la postulación**. Esa es la razón de
que los estados bajaran de 25 a 18.

## `orden_pregunta`

En qué orden se le mostró cada pregunta y sus opciones.

| Columna | Tipo | Oblig. | Qué guarda |
|---|---|---|---|
| `evaluacion_id` | bigint | sí | |
| `pregunta_id` | bigint | sí | |
| `posicion` | integer | sí | |
| `orden_opciones` | text | sí | `C,A,D,B` |
| `creado_en` | timestamptz | sí | |

**Clave primaria:** `evaluacion_id` + `pregunta_id` · **Sin columna `id`**
**Único:** `evaluacion_id` + `posicion`

Sin esta tabla **no se puede reproducir el examen tal como lo vio**. Es la única forma de mostrar
meses después exactamente lo que rindió.

También es lo que congela qué preguntas le tocaron de todo el banco: la plantilla decide las
cuotas, y aquí queda la selección concreta.

## `respuesta`

Lo que contestó.

| Columna | Tipo | Oblig. | Qué guarda |
|---|---|---|---|
| `id` | bigint | sí | Clave |
| `evaluacion_id` | bigint | sí | |
| `pregunta_id` | bigint | sí | |
| `opcion_id` | bigint | no | En las cerradas |
| `texto` | text | no | En las abiertas. Se vacía al anonimizar |
| `segundos` | integer | no | Cuánto tardó |
| `respondida_en` | timestamptz | sí | |
| `detalle` | jsonb | no | La respuesta de los formatos del banco v3, que no caben en una opción ni en un texto: el orden de una secuencia, los campos de un caso, la matriz (`V21`). Los ítems V siguen usando `texto` |

**Clave primaria:** `id` · **Único:** `evaluacion_id` + `pregunta_id`
**Restricción:** la pregunta tiene que estar en `orden_pregunta` de esa evaluación

Se guarda al momento, así que si se corta la luz retoma donde quedó. Una vez enviada no puede
volver atrás a cambiarla.

## `nota_respuesta`

El puntaje de esa respuesta y por qué.

| Columna | Tipo | Oblig. | Qué guarda |
|---|---|---|---|
| `id` | bigint | sí | Clave |
| `respuesta_id` | bigint | sí | |
| `puntaje` | numeric(5,2) | sí | De 0 a 4 |
| `explicacion` | text | sí | **Obligatoria siempre** |
| `evidencia_citada` | text | no | Qué parte de la respuesta usó |
| `confianza` | numeric(5,2) | no | |
| `ejecucion_ia_id` | bigint | no | |
| `ajustada_por_usuario_id` | bigint | no | |
| `motivo_ajuste` | text | no | **Obligatorio si hay ajuste** |
| `ajustada_en` | timestamptz | no | |

**Clave primaria:** `id` · **Único:** `respuesta_id`

`evidencia_citada` guarda qué parte de la propia respuesta usó el agente para justificar la nota.
Es lo que permite discutir una calificación sin releerlo todo.

## `repregunta`

Lo que el agente vuelve a preguntar cuando la respuesta es superficial.

| Columna | Tipo | Oblig. | Qué guarda |
|---|---|---|---|
| `id` | bigint | sí | Clave |
| `respuesta_id` | bigint | sí | |
| `texto` | text | sí | |
| `busca` | text | no | `NUMERO`, `CONTEXTO`, `ACCION_PROPIA`, `HERRAMIENTA`, `RESULTADO`, `APRENDIZAJE` |
| `orden` | integer | sí | |
| `ejecucion_ia_id` | bigint | no | |

**Clave primaria:** `id` · **Único:** `respuesta_id` + `orden`

Son dos tablas y no columnas sueltas porque puede haber varias por respuesta. El tope está en
`parametro`: el cliente avisa de no convertir cada pregunta en una entrevista interminable.

## `respuesta_repregunta`

Lo que contestó a esa repregunta.

| Columna | Tipo | Oblig. | Qué guarda |
|---|---|---|---|
| `id` | bigint | sí | Clave |
| `repregunta_id` | bigint | sí | |
| `texto` | text | sí | Se vacía al anonimizar |
| `respondida_en` | timestamptz | sí | |

**Clave primaria:** `id` · **Único:** `repregunta_id`

## `resultado_alineacion`

El semáforo de cada uno de los tres bloques.

| Columna | Tipo | Oblig. | Qué guarda |
|---|---|---|---|
| `id` | bigint | sí | Clave |
| `evaluacion_id` | bigint | sí | |
| `bloque` | text | sí | `DINERO`, `MADUREZ`, `AUTOGOBIERNO` |
| `semaforo` | text | sí | `VERDE`, `AMBAR`, `ROJO` |
| `explicacion` | text | no | |

**Clave primaria:** `id` · **Único:** `evaluacion_id` + `bloque`

**Un rojo no descarta a nadie.** Genera hipótesis y preguntas de validación.

⚠️ **Esta tabla está vacía y nadie la llena todavía.** Ningún agente escribe los semáforos: el
catálogo de agentes tiene el de alineación pendiente. Desde el 25/08 el panel **sí la lee** —
`GET /panel/postulaciones/{id}/evaluacion` devuelve `alineacion`—, así que hoy esa lista sale
vacía siempre. Vacía significa «no calculado», no «todo en verde».

## `alerta`

Contradicciones y respuestas demasiado ideales.

| Columna | Tipo | Oblig. | Qué guarda |
|---|---|---|---|
| `id` | bigint | sí | Clave |
| `postulacion_id` | bigint | sí | |
| `tipo` | text | sí | `CONTRADICCION` o `DEMASIADO_IDEAL` |
| `descripcion` | text | sí | |
| `pregunta_a_id` | bigint | no | |
| `pregunta_b_id` | bigint | no | |
| `ejecucion_ia_id` | bigint | no | |
| `confirmada_por_usuario_id` | bigint | no | |

**Clave primaria:** `id`

Cuelga de la **postulación** y no de la evaluación, porque una contradicción puede salir de cruzar
la evaluación con la prueba o con la simulación.

Una alerta **nunca descarta a nadie**. Queda visible en la ficha y se convierte en preguntas para
la conversación final.

---

# 13 · Perfil de Talento

## `perfil_talento`

El retrato consolidado de una postulación. No es una nota.

| Columna | Tipo | Oblig. | Qué guarda |
|---|---|---|---|
| `id` | bigint | sí | Clave |
| `postulacion_id` | bigint | sí | |
| `adecuacion` | numeric(5,2) | no | Qué tan compatible es con el cargo |
| `potencial` | numeric(5,2) | no | |
| `alto_rendimiento` | numeric(5,2) | no | |
| `confianza_evidencia` | numeric(5,2) | sí | Cuánta evidencia respalda esto |
| `resumen` | text | no | |
| `version_pesos_id` | bigint | sí | |
| `ejecucion_ia_id` | bigint | no | |
| `actualizado_en` | timestamptz | sí | |

**Clave primaria:** `id` · **Único:** `postulacion_id`

`confianza_evidencia` distingue a quien fue evaluado a fondo de quien apenas dejó rastro. Sin
ella, un perfil con dos etapas hechas y otro con cinco se leen igual.

## `hallazgo_perfil`

Cada fortaleza o riesgo, con su tipo y su evidencia.

| Columna | Tipo | Oblig. | Qué guarda |
|---|---|---|---|
| `id` | bigint | sí | Clave |
| `perfil_talento_id` | bigint | sí | |
| `tipo` | text | sí | `FORTALEZA`, `RIESGO_CRITICO`, `RIESGO_DESARROLLABLE`, `PREFERENCIA`, `FALTA_EVIDENCIA` |
| `descripcion` | text | sí | |
| `evidencia` | text | no | En qué se basa |
| `es_canalizable` | boolean | sí | Si se puede aprovechar en otro rol |
| `sugerencia` | text | no | Cómo desarrollarlo o mitigarlo |

**Clave primaria:** `id`

Los cinco tipos **no se pueden mezclar**, que es una regla explícita del cliente. Un riesgo
desarrollable y una falta de evidencia parecen lo mismo en una lista y significan cosas opuestas:
uno es algo que la persona hace mal y se puede corregir; el otro es algo que no sabemos.

## `sugerencia_puesto`

«Encajaría mejor en otro sitio».

| Columna | Tipo | Oblig. | Qué guarda |
|---|---|---|---|
| `id` | bigint | sí | Clave |
| `perfil_talento_id` | bigint | sí | |
| `puesto_id` | bigint | no | |
| `familia_codigo` | text | no | Si no hay un puesto concreto |
| `motivo` | text | sí | |
| `ejecucion_ia_id` | bigint | no | |

**Clave primaria:** `id`

**No mueve nada sola**: es información para que una persona decida.

---

# 14 · Prueba del puesto

## `plantilla_prueba`

La prueba de un puesto.

| Columna | Tipo | Oblig. | Qué guarda |
|---|---|---|---|
| `id` | bigint | sí | Clave |
| `organizacion_id` | bigint | sí | |
| `puesto_id` | bigint | no | Vacío si vale para varios |
| `nombre` | text | sí | |
| `es_activa` | boolean | sí | |

**Clave primaria:** `id`

Arranca con **once** plantillas cargadas.

## `version_plantilla_prueba`

Una versión concreta. Si tiene vacante, es una copia privada de esa vacante.

| Columna | Tipo | Oblig. | Qué guarda |
|---|---|---|---|
| `id` | bigint | sí | Clave |
| `plantilla_prueba_id` | bigint | sí | |
| `vacante_id` | bigint | no | Si está, es una variante de esa vacante |
| `version` | integer | sí | |
| `enunciado` | text | sí | |
| `url_consigna` | text | no | Dónde vive el enunciado completo, para que **el aviso al candidato lo lleve** (`V29`) |
| `materiales` | text | no | |
| `herramientas_permitidas` | text | no | De dónde puede sacar información, **incluida la IA** |
| `modalidad` | text | sí | `CRONOMETRADA` (lo normal) o `PLAZO_ABIERTO` (solo para cargar las viejas) |
| `duracion_minutos` | integer | no | **Solo si es cronometrada.** De 60 a 120 |
| `plazo_dias` | integer | no | **Solo si es de plazo abierto.** Días para entregar |
| `minuto_cambio_min` | integer | no | Inicio del rango |
| `minuto_cambio_max` | integer | no | Fin del rango |
| `minutos_extra` | integer | no | Cuánto tiene para adaptarse tras el cambio |
| `estado` | text | sí | `BORRADOR` o `PUBLICADA` |
| `publicada_en` | timestamptz | no | |

**Clave primaria:** `id` · **Único:** `plantilla_prueba_id` + `version`
**Restricción:** si `modalidad` es `CRONOMETRADA`, `duracion_minutos` es obligatorio; si es
`PLAZO_ABIERTO`, lo es `plazo_dias`
**Restricción:** `minuto_cambio_min` no puede ser mayor que `minuto_cambio_max`, y los dos tienen
que caber dentro de `duracion_minutos`

**Decidido el 15/08: las pruebas nuevas son `CRONOMETRADA`.** El cronómetro y el cambio a mitad
son justo la mejora que Renaser quiere; las cinco pruebas de `insumos/pruebas-tecnicas/` son
anteriores y valen como **modelo de contenido y de tono, no de formato**.

`PLAZO_ABIERTO` se queda por una sola razón: poder cargar esas cinco tal como están y adaptarlas
sin reescribirlas fuera del sistema. No es una modalidad que se ofrezca en una vacante nueva. Si
se decide que nunca se usará, quitar `modalidad` y `plazo_dias` es un cambio de una línea
**mientras no exista la migración**.

**El cambio inesperado ya no tiene minuto fijo**: hay un rango, y al empezar el intento se sortea
uno concreto. Si fuera siempre el mismo, el segundo candidato ya sabría cuándo llega. Solo aplica
a las cronometradas.

Que los puntos de la rúbrica sumen 100 **se comprueba al publicar**, no al guardar el borrador.
Eso vive en el código: la base no distingue esos dos momentos con una restricción simple.

## `variante_cambio`

Las distintas formas que puede tomar el cambio inesperado.

| Columna | Tipo | Oblig. | Qué guarda |
|---|---|---|---|
| `id` | bigint | sí | Clave |
| `version_plantilla_prueba_id` | bigint | sí | |
| `texto` | text | sí | |
| `orden` | integer | sí | |

**Clave primaria:** `id` · **Único:** `version_plantilla_prueba_id` + `orden`

## `pregunta_prueba`

El catálogo de preguntas de la prueba: previas, universales y del puesto.

| Columna | Tipo | Oblig. | Qué guarda |
|---|---|---|---|
| `id` | bigint | sí | Clave |
| `codigo` | text | sí | |
| `enunciado` | text | sí | |
| `tipo` | text | sí | `PREVIA`, `UNIVERSAL` o `ESPECIFICA` |
| `puesto_id` | bigint | no | Solo en las específicas |
| `revela` | text | no | «Criterio», «Tradeoffs»… |
| `orden` | integer | sí | |

**Clave primaria:** `id` · **Único:** `codigo`

Antes eran 17 fijas para todos. Ahora hay tres tipos: las **previas** se responden antes de
producir, y cada plantilla elige entre 8 y 10 **universales** más 3 a 5 **específicas**.

## `pregunta_version_plantilla`

Cuáles eligió esta plantilla.

| Columna | Tipo | Oblig. | Qué guarda |
|---|---|---|---|
| `version_plantilla_prueba_id` | bigint | sí | |
| `pregunta_prueba_id` | bigint | sí | |
| `orden` | integer | sí | |
| `creado_en` | timestamptz | sí | |

**Clave primaria:** `version_plantilla_prueba_id` + `pregunta_prueba_id` · **Sin columna `id`**

Que sean entre 8 y 10 universales y 3 a 5 específicas se comprueba al publicar, en el código.

## `entregable_requerido`

Qué cosas distintas tiene que entregar, cada una con su regla.

| Columna | Tipo | Oblig. | Qué guarda |
|---|---|---|---|
| `id` | bigint | sí | Clave |
| `version_plantilla_prueba_id` | bigint | sí | |
| `nombre` | text | sí | `MVP funcional`, `Video explicativo`, `Plano o esquema` |
| `detalle` | text | sí | La regla: «máximo 5 minutos», «máx. 10 diapositivas» |
| `formato` | text | sí | `ARCHIVO`, `ENLACE` o `CUALQUIERA` |
| `es_obligatorio` | boolean | sí | |
| `orden` | integer | sí | |

**Clave primaria:** `id` · **Único:** `version_plantilla_prueba_id` + `orden`

Antes esto era una columna de texto libre en la versión de la plantilla, y no servía: **el
sistema no podía decir «falta el video»**, ni la rúbrica podía puntuar un entregable concreto.

Las cinco pruebas reales de `insumos/pruebas-tecnicas/` piden entre uno y cuatro entregables
distintos, cada uno con su propia regla: producto funcional más video de 5 minutos más documento
de 1 página; o documento de 5 páginas más plano más imágenes más video. Una presentación de 10
diapositivas cuenta como uno solo. Por eso es una tabla y no una columna.

## `intento_prueba`

Cuando un candidato rinde.

| Columna | Tipo | Oblig. | Qué guarda |
|---|---|---|---|
| `id` | bigint | sí | Clave |
| `postulacion_id` | bigint | sí | |
| `version_plantilla_prueba_id` | bigint | sí | La versión congelada con que rindió |
| `iniciado_en` | timestamptz | sí | Desde aquí corre el reloj |
| `vence_en` | timestamptz | sí | Cuándo se le cierra. Sale de la fecha de la vacante si la tiene; si no, **se calcula al empezar y se guarda** |
| `plazo_propio` | boolean | sí | A esta persona se le fijó su fecha a mano. Mover la de la vacante no se la toca |
| `entregado_en` | timestamptz | no | |
| `es_entrega_automatica` | boolean | sí | Si lo entregó el reloj por él |
| `variante_cambio_id` | bigint | no | Cuál le tocó |
| `minuto_cambio` | integer | no | El sorteado dentro del rango |
| `cambio_mostrado_en` | timestamptz | no | |

**Clave primaria:** `id` · **Único:** `postulacion_id`

El reloj lo lleva el servidor. Si cierra la página, el tiempo sigue corriendo. **No hay
acumulador de pausas porque no se puede pausar.**

`vence_en` se guarda en vez de calcularse cada vez: así el barrido que busca relojes agotados es
una consulta sobre una columna indexada, y no depende de que la plantilla siga igual.

Cuando se acaba, el sistema entrega solo. **No existe entregar tarde**, y por eso hay una marca en
vez de un estado.

## `entregable`

Lo que sube o el enlace que pega.

| Columna | Tipo | Oblig. | Qué guarda |
|---|---|---|---|
| `id` | bigint | sí | Clave |
| `intento_prueba_id` | bigint | sí | |
| `entregable_requerido_id` | bigint | sí | **Cuál de los que se le pidieron es** |
| `archivo_id` | bigint | no | |
| `enlace` | text | no | |
| `version` | integer | sí | Por si entrega varias veces |
| `subido_en` | timestamptz | sí | |

**Clave primaria:** `id` · **Único:** `intento_prueba_id` + `entregable_requerido_id` + `version`
**Restricción:** tiene que tener archivo o enlace, no puede estar vacío de los dos

Saber cuál de los pedidos es cada archivo es lo que permite avisar de que falta uno antes de que
se acabe el plazo, y lo que permite que un criterio de la rúbrica apunte a un entregable
concreto: «claridad del video» no se puntúa mirando el documento.

## `respuesta_prueba`

Sus respuestas a las preguntas de la prueba.

| Columna | Tipo | Oblig. | Qué guarda |
|---|---|---|---|
| `id` | bigint | sí | Clave |
| `intento_prueba_id` | bigint | sí | |
| `pregunta_prueba_id` | bigint | sí | |
| `texto` | text | sí | Se vacía al anonimizar |
| `respondida_en` | timestamptz | sí | |

**Clave primaria:** `id` · **Único:** `intento_prueba_id` + `pregunta_prueba_id`

---

# 15 · Simulación de trabajo

## `sesion_simulacion`

Una fecha con cupo. No hay límite de cuántas se crean.

| Columna | Tipo | Oblig. | Qué guarda |
|---|---|---|---|
| `id` | bigint | sí | Clave |
| `organizacion_id` | bigint | sí | |
| `fecha_hora` | timestamptz | sí | |
| `duracion_minutos` | integer | sí | Hasta 120 |
| `modalidad` | text | sí | `GRUPAL` o `INDIVIDUAL` |
| `lugar` | text | no | |
| `enlace` | text | no | Si es a distancia |
| `cupo` | integer | sí | |
| `estado` | text | sí | `PUBLICADA`, `LLENA`, `CANCELADA`, `TERMINADA` |
| `enunciado` | text | no | El ejercicio de la sesión |
| `creada_por_usuario_id` | bigint | sí | |

**Clave primaria:** `id`

`modalidad` arranca en grupal y es configurable: antes el modelo daba por hecho que siempre era
grupal y presencial.

## `sesion_responsable`

Quién conduce cada sesión. Son varios, así que va en su propia tabla.

| Columna | Tipo | Oblig. | Qué guarda |
|---|---|---|---|
| `sesion_simulacion_id` | bigint | sí | |
| `usuario_id` | bigint | sí | |
| `creado_en` | timestamptz | sí | |

**Clave primaria:** `sesion_simulacion_id` + `usuario_id` · **Sin columna `id`**
**Apunta a:** `sesion_simulacion`, `usuario`

## `sesion_vacante`

Para qué vacantes sirve esa sesión: una, varias o todas.

| Columna | Tipo | Oblig. | Qué guarda |
|---|---|---|---|
| `sesion_simulacion_id` | bigint | sí | |
| `vacante_id` | bigint | sí | |
| `creado_en` | timestamptz | sí | |

**Clave primaria:** `sesion_simulacion_id` + `vacante_id` · **Sin columna `id`**

El candidato solo ve las sesiones de su vacante.

## `inscripcion_sesion`

El candidato eligió esta fecha.

| Columna | Tipo | Oblig. | Qué guarda |
|---|---|---|---|
| `id` | bigint | sí | Clave |
| `sesion_simulacion_id` | bigint | sí | |
| `postulacion_id` | bigint | sí | |
| `inscrita_en` | timestamptz | sí | |
| `asistio` | boolean | no | Vacío hasta que alguien lo marca |
| `marcada_por_usuario_id` | bigint | no | |
| `es_vigente` | boolean | sí | Falso si la sesión se canceló y eligió otra |

**Clave primaria:** `id`
**Único parcial:** `postulacion_id` cuando `es_vigente`

`es_vigente` permite guardar el historial: si su primera sesión se canceló, esa inscripción se
queda como estaba y se crea otra.

Esta tabla es lo que permite que los tres momentos que antes eran estados —elegir fecha, esperar
el día, estar en la sesión— sean uno solo: la bandeja los distingue cruzando la inscripción con
la fecha de la sesión.

## `tramo_simulacion`

Cómo se reparten los minutos de esta sesión.

| Columna | Tipo | Oblig. | Qué guarda |
|---|---|---|---|
| `id` | bigint | sí | Clave |
| `sesion_simulacion_id` | bigint | sí | |
| `codigo` | text | sí | `CONTEXTO`, `PREGUNTAS`, `EJECUCION`, `CAMBIO`, `ENTREGA`, `CONVERSACION` |
| `nombre` | text | sí | |
| `minuto_inicio` | integer | sí | |
| `minuto_fin` | integer | sí | |

**Clave primaria:** `id` · **Único:** `sesion_simulacion_id` + `codigo`

Era un catálogo global de seis filas. Ahora **cada sesión guarda los suyos**, porque el reparto es
configurable. Se copian de un valor por defecto al crear la sesión: 0–10, 10–20, 20–80, 80–100,
100–105, 105–120.

## `informacion_critica`

Qué debería preguntar un candidato fuerte, qué es opcional y qué hay que descubrir.

| Columna | Tipo | Oblig. | Qué guarda |
|---|---|---|---|
| `id` | bigint | sí | Clave |
| `sesion_simulacion_id` | bigint | sí | |
| `tipo` | text | sí | `DEBE_PREGUNTAR`, `OPCIONAL` o `DEBE_DESCUBRIR` |
| `texto` | text | sí | |
| `orden` | integer | sí | |

**Clave primaria:** `id`

Es lo que permite evaluar la calidad de sus preguntas sin adivinar. Si no se declara de antemano
qué debería haber preguntado, calificar «no preguntó lo importante» es una opinión.

## `marca_tiempo_simulacion`

Los momentos **observables** que el sistema anota solo.

| Columna | Tipo | Oblig. | Qué guarda |
|---|---|---|---|
| `id` | bigint | sí | Clave |
| `inscripcion_sesion_id` | bigint | sí | |
| `evento` | text | sí | Ver abajo |
| `ocurrida_en` | timestamptz | sí | Con precisión de segundos |

**Clave primaria:** `id` · **Único:** `inscripcion_sesion_id` + `evento`

Los eventos son: `INICIO`, `PRIMERA_PREGUNTA`, `INICIO_TRABAJO`, `PRIMERA_EVIDENCIA`,
`APARECE_CAMBIO`, `ABRE_CAMBIO`, `PRIMERA_REACCION_CAMBIO`, `COMUNICA_RIESGO`, `ENTREGA`,
`AUTOCRITICA`.

⚠️ **Antes había una marca para «cuándo detectó el bloqueo», y ya no existe.** El cliente lo
prohíbe expresamente: no se puede registrar lo que alguien pensó, solo lo que hizo. Lo que queda
es cuándo apareció el bloqueo y cuándo el candidato lo abrió, que son dos actos observables.

## `pregunta_generada`

Las 3 a 5 preguntas para la conversación final, y qué se respondió.

| Columna | Tipo | Oblig. | Qué guarda |
|---|---|---|---|
| `id` | bigint | sí | Clave |
| `postulacion_id` | bigint | sí | |
| `texto` | text | sí | |
| `alerta_id` | bigint | no | De qué contradicción salió |
| `ejecucion_ia_id` | bigint | no | |
| `respuesta` | text | no | Lo que contestó, en breve |
| `riesgo_resuelto` | boolean | no | Vacío hasta la conversación |
| `observacion` | text | no | |
| `registrada_por_usuario_id` | bigint | no | |
| `orden` | integer | sí | |
| `motivo` | text | no | Por qué se le hace esa pregunta (`V23`). **Una pregunta cuyo motivo no se ve no se puede repreguntar bien.** Las que se registran a mano entran sin él |

**Clave primaria:** `id`

Las genera un agente a partir de las contradicciones entre currículum, evaluación, prueba y
simulación. La conversación dura unos 15 minutos y **no se vuelve a preguntar lo que ya está
demostrado**.

`riesgo_resuelto` es lo que evita un módulo de entrevista aparte: con eso y la observación basta.

---

# 16 · Validación práctica y decisión

## `validacion`

El periodo de trabajo.

| Columna | Tipo | Oblig. | Qué guarda |
|---|---|---|---|
| `id` | bigint | sí | Clave |
| `postulacion_id` | bigint | sí | |
| `modalidad` | text | sí | `SIMULACION_EXTENDIDA` o `TRABAJO_REAL` |
| `tipo_vinculacion` | text | no | **Obligatorio si la modalidad es trabajo real** |
| `dias` | integer | sí | Configurable. Arranca en 7 |
| `inicio_en` | timestamptz | no | |
| `fin_en` | timestamptz | no | Fecha concreta, no calculada |
| `estado` | text | sí | `POR_HABILITAR`, `EN_CURSO`, `TERMINADA` |
| `habilitada_por_usuario_id` | bigint | no | Quién dio el visto bueno |
| `responsable_usuario_id` | bigint | no | |

**Clave primaria:** `id` · **Único:** `postulacion_id`
**Restricción:** `tipo_vinculacion` no puede estar vacío si `modalidad` es `TRABAJO_REAL`

La modalidad de trabajo real **no se puede habilitar** hasta que `tipo_vinculacion` esté
registrado. Es lo que impide que una aceptación digital sustituya una obligación legal.

`fin_en` se guarda como fecha concreta para que el barrido que cierra periodos vencidos sea una
consulta directa.

## `etapa`

Las cinco etapas del embudo. Catálogo cerrado.

| Columna | Tipo | Oblig. | Qué guarda |
|---|---|---|---|
| `codigo` | text | sí | `PERFIL_INTEGRAL`, `PRUEBA_PUESTO`, `SIMULACION`, `VALIDACION`, `DECISION` |
| `nombre` | text | sí | |
| `orden` | integer | sí | |

**Clave primaria:** `codigo` · **Sin columna `id`**

## `nota_etapa`

La nota de cada etapa, atada a la versión de pesos con que se calculó.

| Columna | Tipo | Oblig. | Qué guarda |
|---|---|---|---|
| `id` | bigint | sí | Clave |
| `postulacion_id` | bigint | sí | |
| `etapa_codigo` | text | sí | |
| `puntaje` | numeric(5,2) | sí | Sobre 100 |
| `version_pesos_id` | bigint | sí | **Nunca vacío** |
| `calculada_en` | timestamptz | sí | |

**Clave primaria:** `id` · **Único:** `postulacion_id` + `etapa_codigo`

Apuntar a la versión de pesos es lo que permite reproducir la decisión tal como se tomó. **Las
notas históricas no se recalculan.**

## `decision`

El semáforo final.

| Columna | Tipo | Oblig. | Qué guarda |
|---|---|---|---|
| `id` | bigint | sí | Clave |
| `postulacion_id` | bigint | sí | |
| `semaforo` | text | sí | `VERDE`, `AMBAR`, `ROJO`, `SIN_DATOS`, `RESERVA` |
| `nota_global` | numeric(5,2) | no | Orientativa, no decide sola |
| `version_pesos_id` | bigint | sí | |
| `decidida_por_usuario_id` | bigint | no | Vacío mientras la propone el sistema |
| `motivo` | text | no | **Obligatorio cuando decide una persona** |
| `decidida_en` | timestamptz | no | |

**Clave primaria:** `id` · **Único:** `postulacion_id`

**Cinco valores, no cuatro.** «Sin datos» es distinto de rojo —falta evidencia, no falla la
persona— y «reserva» es distinto de los dos: la persona vale, pero para otra cosa.

## `barrera_detectada`

Una barrera crítica encontrada en un candidato concreto.

| Columna | Tipo | Oblig. | Qué guarda |
|---|---|---|---|
| `id` | bigint | sí | Clave |
| `postulacion_id` | bigint | sí | |
| `barrera_critica_id` | bigint | sí | |
| `explicacion` | text | sí | En qué se basa |
| `ejecucion_ia_id` | bigint | no | |
| `confirmada_por_usuario_id` | bigint | no | **Vacío hasta que una persona la confirma** |
| `confirmada_en` | timestamptz | no | |
| `descartada_en` | timestamptz | no | Si la persona dice que no aplica |

**Clave primaria:** `id` · **Único:** `postulacion_id` + `barrera_critica_id`

La puede detectar la máquina, pero **mientras `confirmada_por_usuario_id` esté vacío no bloquea a
nadie**.

## `opinion_evaluador_estandar`

Su revisión escrita.

| Columna | Tipo | Oblig. | Qué guarda |
|---|---|---|---|
| `id` | bigint | sí | Clave |
| `postulacion_id` | bigint | sí | |
| `usuario_id` | bigint | sí | |
| `texto` | text | sí | |
| `bloquea` | boolean | sí | Solo si su asignación se lo permite |
| `resuelta_en` | timestamptz | no | Si bloqueaba y ya se resolvió |

**Clave primaria:** `id` · **Único:** `postulacion_id` + `usuario_id`

`bloquea` solo puede ser verdadero si `evaluador_estandar.puede_bloquear` lo era. En la primera
versión arranca en falso: emite una recomendación registrada.

## `evidencia_adicional`

Lo que se pide cuando sale ámbar.

| Columna | Tipo | Oblig. | Qué guarda |
|---|---|---|---|
| `id` | bigint | sí | Clave |
| `postulacion_id` | bigint | sí | |
| `numero` | integer | sí | 1 o 2. Tope configurable |
| `motivo` | text | sí | Qué duda hay que resolver |
| `enunciado` | text | sí | |
| `solicitada_por_usuario_id` | bigint | sí | |
| `entregada_en` | timestamptz | no | |
| `puntaje` | numeric(5,2) | no | |
| `explicacion` | text | no | |

**Clave primaria:** `id` · **Único:** `postulacion_id` + `numero`

El tope está en `parametro`, con 2 por defecto. Al llegar, el sistema ya no permite otra y obliga
a decidir con lo que hay. Sin tope, una postulación puede quedar dando vueltas para siempre.

---

# 17 · Configuración

## `version_pesos`

Una versión de todos los pesos.

| Columna | Tipo | Oblig. | Qué guarda |
|---|---|---|---|
| `id` | bigint | sí | Clave |
| `organizacion_id` | bigint | sí | |
| `etiqueta` | text | sí | |
| `estado` | text | sí | `BORRADOR` o `PUBLICADA` |
| `publicada_por_usuario_id` | bigint | no | |
| `publicada_en` | timestamptz | no | |

**Clave primaria:** `id` · **Único:** `organizacion_id` + `etiqueta`

## `peso_etapa`

Cuánto pesa cada etapa. **Ya no depende del nivel.**

| Columna | Tipo | Oblig. | Qué guarda |
|---|---|---|---|
| `version_pesos_id` | bigint | sí | |
| `etapa_codigo` | text | sí | |
| `peso` | numeric(5,2) | sí | |
| `creado_en` | timestamptz | sí | |

**Clave primaria:** `version_pesos_id` + `etapa_codigo` · **Sin columna `id`**

El nivel **salió de la clave**. Los pesos son 40 / 30 / 15 / 15 para todos, y lo que cambia por
vacante es a qué versión apunta.

Que sumen 100 se comprueba al publicar, en el código.

## `peso_componente_perfil`

Cómo se reparte el 40% del Perfil Integral.

| Columna | Tipo | Oblig. | Qué guarda |
|---|---|---|---|
| `version_pesos_id` | bigint | sí | |
| `componente` | text | sí | `CV`, `PSICOMETRICO`, `EVALUACION` |
| `peso` | numeric(5,2) | sí | 10, 5 y 25 |
| `creado_en` | timestamptz | sí | |

**Clave primaria:** `version_pesos_id` + `componente` · **Sin columna `id`**

El módulo psicométrico todavía no existe. Su 5% se reparte entre los otros dos mientras tanto, y
por eso esto son datos y no números escritos en el código.

## `peso_dimension`

Cuánto pesa cada dimensión en cada nivel.

| Columna | Tipo | Oblig. | Qué guarda |
|---|---|---|---|
| `version_pesos_id` | bigint | sí | |
| `nivel_puesto_codigo` | text | sí | |
| `dimension_codigo` | text | sí | |
| `peso` | numeric(5,2) | sí | |
| `creado_en` | timestamptz | sí | |

**Clave primaria:** los tres primeros · **Sin columna `id`**

Aquí el nivel **sí** sigue mandando: el criterio de negocio pesa en Dirección y casi nada en
Ejecución.

## `peso_criterio`

Cuánto vale cada criterio en cada nivel, en las tres etapas globales.

| Columna | Tipo | Oblig. | Qué guarda |
|---|---|---|---|
| `version_pesos_id` | bigint | sí | |
| `nivel_puesto_codigo` | text | sí | |
| `criterio_id` | bigint | sí | |
| `peso` | numeric(5,2) | sí | |
| `creado_en` | timestamptz | sí | |

**Clave primaria:** los tres primeros · **Sin columna `id`**

Es donde viven los ocho criterios del currículum con su reparto por nivel, los diez de la
simulación y las nueve métricas de la validación.

## `parametro`

Los valores sueltos.

| Columna | Tipo | Oblig. | Qué guarda |
|---|---|---|---|
| `id` | bigint | sí | Clave |
| `organizacion_id` | bigint | sí | |
| `codigo` | text | sí | |
| `valor` | text | sí | |
| `tipo` | text | sí | `ENTERO`, `TEXTO`, `BOOLEANO`, `LISTA` |
| `descripcion` | text | sí | En lenguaje normal |
| `modificado_por_usuario_id` | bigint | no | |
| `modificado_en` | timestamptz | no | |

**Clave primaria:** `id` · **Único:** `organizacion_id` + `codigo`

Arranca con: días sin avanzar antes de cerrar (60), tope de rondas de evidencia adicional (2),
cupo por defecto de una sesión, tope de repreguntas por respuesta, y **qué datos se ocultan del
currículum** antes de mandárselo a la máquina.

## `plantilla_correo`

Los textos que se envían, versionados.

| Columna | Tipo | Oblig. | Qué guarda |
|---|---|---|---|
| `id` | bigint | sí | Clave |
| `organizacion_id` | bigint | sí | |
| `codigo` | text | sí | |
| `version` | integer | sí | |
| `asunto` | text | sí | |
| `cuerpo` | text | sí | |
| `es_activa` | boolean | sí | |

**Clave primaria:** `id` · **Único:** `organizacion_id` + `codigo` + `version`

## `instruccion_ia`

Los textos que se le mandan a cada agente, versionados.

| Columna | Tipo | Oblig. | Qué guarda |
|---|---|---|---|
| `id` | bigint | sí | Clave |
| `agente_codigo` | text | sí | |
| `version` | integer | sí | |
| `texto` | text | sí | |
| `es_activa` | boolean | sí | Solo una activa por agente |
| `publicada_por_usuario_id` | bigint | no | |
| `publicada_en` | timestamptz | no | |

**Clave primaria:** `id` · **Único:** `agente_codigo` + `version`

Son configuración versionada, igual que las preguntas. Solo Dirección las cambia, y cada
calificación guarda con qué versión se produjo.

---

# 18 · Agentes de inteligencia artificial

## `agente`

El catálogo de los nueve, con su versión.

| Columna | Tipo | Oblig. | Qué guarda |
|---|---|---|---|
| `codigo` | text | sí | Ver abajo |
| `nombre` | text | sí | |
| `descripcion` | text | sí | De qué se encarga |
| `version` | integer | sí | Sube cuando cambia su forma de trabajar |
| `es_activo` | boolean | sí | |

**Clave primaria:** `codigo` · **Sin columna `id`**

Los nueve: `NECESIDAD_TALENTO`, `CAZATALENTOS`, `EVIDENCIA_CV`, `EVALUADOR`, `POTENCIAL_RIESGO`,
`PRUEBA_PUESTO`, `SIMULACION`, `DESEMPENO`, `APRENDIZAJE`.

El de Aprendizaje propone qué preguntas y pesos parecen útiles, pero **nunca cambia una regla por
sí solo**: cualquier recalibración crea una versión nueva aprobada.

## `trabajo_ia`

El encargo pendiente. Se procesa en segundo plano; el candidato no espera.

| Columna | Tipo | Oblig. | Qué guarda |
|---|---|---|---|
| `id` | bigint | sí | Clave |
| `organizacion_id` | bigint | sí | |
| `agente_codigo` | text | sí | |
| `postulacion_id` | bigint | no | Vacío en los que no cuelgan de una |
| `referencia_tabla` | text | no | Sobre qué trabaja |
| `referencia_id` | bigint | no | |
| `estado` | text | sí | `PENDIENTE`, `EN_CURSO`, `TERMINADO`, `FALLIDO` |
| `intentos` | integer | sí | Por defecto 0 |
| `terminado_en` | timestamptz | no | |
| `tomado_en` | timestamptz | no | Cuándo lo tomó un trabajador (`V16`). Sin esto, un trabajo que murió a mitad se queda `EN_CURSO` **para siempre**, sin que nadie lo reintente |
| `modo` | text | sí | `RAPIDA` o `FINA`, por defecto `FINA` (`V19`). Decide si el modelo razona, y es lo que hace que pedir la criba dos veces no cueste el doble |

**Clave primaria:** `id`

`referencia_tabla` y `referencia_id` apuntan a la fila concreta sobre la que se trabaja —una
respuesta, un intento de prueba, un currículum—. **No es una clave foránea** porque apunta a
tablas distintas según el agente.

## `ejecucion_ia`

Cada intento por separado.

| Columna | Tipo | Oblig. | Qué guarda |
|---|---|---|---|
| `id` | bigint | sí | Clave |
| `trabajo_ia_id` | bigint | sí | |
| `organizacion_id` | bigint | sí | |
| `agente_codigo` | text | sí | |
| `version_agente` | integer | sí | |
| `objetivo` | text | sí | Qué se le pidió |
| `modelo` | text | sí | |
| `proveedor` | text | sí | `DEEPSEEK` para calificar, `GOOGLE` para buscar por significado |
| `version_modelo` | text | no | Cuando el proveedor la da |
| `instruccion_ia_id` | bigint | no | |
| `envio` | text | sí | Lo que se le mandó, entero |
| `respuesta` | text | no | Lo que respondió, entero |
| `confianza` | numeric(5,2) | no | Qué tan segura es su salida |
| `tokens_entrada` | integer | no | |
| `tokens_salida` | integer | no | |
| `costo` | numeric(10,4) | no | |
| `duracion_ms` | integer | no | |
| `es_exitosa` | boolean | sí | |
| `error` | text | no | Si falló |

**Clave primaria:** `id`

Un encargo reintentado tres veces tiene **tres ejecuciones, y las tres quedan**. Con una sola
tabla habría que sobrescribir el intento anterior, que es justo lo que hay que mirar cuando un
candidato reclama su nota.

Se guarda la respuesta **completa**, no solo la nota.

`version_agente` importa tanto como `version_modelo`: sin ella no se puede distinguir un error del
modelo de un cambio en las instrucciones que le dimos nosotros.

⚠️ **Es la tabla que más crece.** Con el volumen previsto, del orden de un gigabyte y medio al
año. Conviene vigilarla y decidir a qué plazo se archivan las ejecuciones viejas.

---

# 19 · Auditoría, archivos y desempeño

## `auditoria`

Toda acción que cambia una decisión. **No se modifica ni se borra.**

| Columna | Tipo | Oblig. | Qué guarda |
|---|---|---|---|
| `id` | bigint | sí | Clave |
| `organizacion_id` | bigint | sí | |
| `usuario_id` | bigint | no | Vacío si fue el sistema |
| `rol_id` | bigint | no | Con qué rol actuó |
| `accion` | text | sí | |
| `entidad` | text | sí | Qué tabla |
| `entidad_id` | bigint | no | Qué fila |
| `valor_anterior` | jsonb | no | |
| `valor_nuevo` | jsonb | no | |
| `motivo` | text | no | |
| `ocurrida_en` | timestamptz | sí | |

**Clave primaria:** `id` · **No admite UPDATE ni DELETE**

Que no se pueda borrar **no es configurable**: no existe la casilla para permitirlo. Solo Dirección
y Administrador la consultan.

También registra los cambios de permisos: qué permiso, sobre qué rol, y qué valor tenía antes.

`valor_anterior` y `valor_nuevo` son `jsonb` porque cada entidad tiene columnas distintas y no
tendría sentido una tabla por tipo de cambio.

## `archivo`

Los archivos viven fuera de la base; aquí solo está su ruta.

| Columna | Tipo | Oblig. | Qué guarda |
|---|---|---|---|
| `id` | bigint | sí | Clave |
| `organizacion_id` | bigint | sí | |
| `ruta` | text | no | Se anula al borrar el archivo |
| `nombre_original` | text | no | |
| `tamano` | bigint | no | En bytes |
| `tipo` | text | no | |
| `subido_en` | timestamptz | sí | |
| `borrado_en` | timestamptz | no | |
| `contenido_hash` | text | no | La huella del contenido, que **evita pagar dos veces la lectura del mismo currículum** (`V36`). Vacío en los archivos ya subidos: significa «leer de nuevo», que es lo de siempre |

**Clave primaria:** `id`

La base guarda la ruta, **nunca el archivo**. Así los entregables pesados —vídeos, diseños,
archivos de hasta 200 MB— no la inflan.

**El almacén es propio de este sistema**, no el de RENASER OS, y es privado siempre: para abrir un
archivo el backend genera un enlace firmado que dura poco.

Al borrar los datos de alguien, el archivo se borra del almacén y la fila queda con `ruta` vacía y
`borrado_en` puesto. Así las referencias no se rompen.

## `correo_enviado`

A quién, cuándo y **qué decía**.

| Columna | Tipo | Oblig. | Qué guarda |
|---|---|---|---|
| `id` | bigint | sí | Clave |
| `usuario_id` | bigint | sí | |
| `plantilla_correo_codigo` | text | sí | |
| `version_plantilla` | integer | sí | |
| `asunto` | text | sí | |
| `cuerpo` | text | sí | Ya armado |
| `canal` | text | sí | `CORREO` por ahora |
| `estado_entrega` | text | no | Cuando el proveedor lo informa |
| `enviado_en` | timestamptz | sí | |

**Clave primaria:** `id`

Se guarda el cuerpo **ya armado**, no solo cuál plantilla se usó. Si mañana alguien edita la
plantilla, lo que se le envió a esa persona sigue siendo lo que dice el registro.

⚠️ **Al ejecutar un borrado de datos, `asunto` y `cuerpo` se sobrescriben** con «[eliminado por
solicitud de borrado]»: el cuerpo armado contiene el nombre y el correo de la persona. La fila se
conserva, así que se sigue sabiendo qué plantilla, qué versión y cuándo.

## `seguimiento_desempeno`

El corte de los 30, 90 o 180 días, con su diagnóstico.

| Columna | Tipo | Oblig. | Qué guarda |
|---|---|---|---|
| `id` | bigint | sí | Clave |
| `organizacion_id` | bigint | sí | |
| `postulacion_id` | bigint | sí | |
| `dias` | integer | sí | 30, 90 o 180 |
| `resultado_esperado` | text | no | |
| `porcentaje_logrado` | numeric(5,2) | no | |
| `obstaculo` | text | no | Solo si hubo desviación |
| `causa` | text | no | `CLARIDAD`, `CAPACIDAD`, `HABILIDAD`, `PROCESO`, `DEPENDENCIA`, `DECISION`, `EXTERNO` |
| `accion` | text | no | Qué hizo |
| `apoyo_requerido` | text | no | |
| `cambio_previsto` | text | no | Qué cambiará para que no se repita |
| `registrado_en` | timestamptz | sí | |

**Clave primaria:** `id` · **Único:** `postulacion_id` + `dias`

El diagnóstico va en columnas y no en tabla aparte porque es **uno por corte** y solo aparece
cuando hay desviación relevante.

**Ya no está bloqueado:** RENASER OS existe y expone por su API los objetivos, tareas, plazos,
retrabajo y resultados.

## `metrica_desempeno`

Cada una de las diez medidas de ese corte.

| Columna | Tipo | Oblig. | Qué guarda |
|---|---|---|---|
| `seguimiento_desempeno_id` | bigint | sí | |
| `metrica` | text | sí | `RESULTADO`, `CALIDAD`, `VELOCIDAD`, `CONFIABILIDAD`, `AUTONOMIA`, `COMUNICACION`, `APRENDIZAJE`, `SERVICIO`, `APORTE_SISTEMA` |
| `valor` | numeric(5,2) | sí | |
| `origen` | text | sí | `RENASER_OS`, `PERSONA` o `AGENTE` |
| `creado_en` | timestamptz | sí | |

**Clave primaria:** `seguimiento_desempeno_id` + `metrica` · **Sin columna `id`**

`origen` es lo que permite mostrar de dónde salió cada dato, que el cliente pide expresamente.

---

# 20 · El perfil del candidato

Llegó con la `V36`. Hasta entonces todo lo que se sabía de una persona colgaba de **una**
postulación: quien postulaba a tres vacantes veía su currículum leído —y pagado— tres veces, y
sus datos quedaban en tres filas de `dato_cv` sin relación entre sí.

Cuelga de `persona` y **no de `usuario`**, y es una decisión de fondo: el usuario existe una vez
por organización, así que un perfil por usuario obligaría al candidato a llenarlo una vez por
empresa. Colgando de la persona sale el modelo de los portales de empleo: una persona, un
perfil, muchas empresas.

Tres reglas que el esquema hace cumplir:

- **Cada dato sabe de dónde vino** (`origen`) y si la persona lo confirmó (`confirmado_en`). Lo
  que la IA lee del currículum se **propone**; nunca pisa lo que puso o confirmó la persona.
- **El perfil no puntúa.** Ninguna de estas tablas participa en notas ni en rankings.
- **La pretensión salarial es un rango con moneda, o nada.** Un mínimo suelto no significa nada
  y una cifra exacta se lee como ultimátum.

El diseño completo está en [PROPUESTA-PERFIL-DEL-CANDIDATO.md](PROPUESTA-PERFIL-DEL-CANDIDATO.md).

## `nivel_educativo`

| Columna | Tipo | Oblig. | Qué guarda |
|---|---|---|---|
| `codigo` | text | sí | Clave |
| `nombre` | text | sí | |
| `orden` | integer | sí | |
| `creado_en` | timestamptz | sí | |

**Clave primaria:** `codigo` · **Sin columna `id`**

⚠️ **No confundir con `nivel_puesto`**, que es el nivel del *puesto* —dirección, supervisión,
ejecución—, no el de estudios.

Es un catálogo cerrado **en tabla y no en un `CHECK`**: lo amplía el negocio, no una migración.

## `nivel_idioma`

| Columna | Tipo | Oblig. | Qué guarda |
|---|---|---|---|
| `codigo` | text | sí | Clave |
| `nombre` | text | sí | |
| `orden` | integer | sí | |
| `creado_en` | timestamptz | sí | |

**Clave primaria:** `codigo` · **Sin columna `id`**

## `perfil_candidato`

La ficha de la persona. Una por persona, para siempre.

| Columna | Tipo | Oblig. | Qué guarda |
|---|---|---|---|
| `id` | bigint | sí | Clave |
| `persona_id` | bigint | sí | **De la persona, no del usuario** |
| `titular` | text | no | Cómo se presenta en una línea |
| `resumen` | text | no | |
| `habilidades` | text | no | Separadas por `\|`, como en `dato_cv`. El día que haya que buscar «quién sabe Excel», esto pasa a su propia tabla |
| `experiencia_meses` | integer | no | **Lo declara la persona**; no se calcula sumando la experiencia de abajo, porque los periodos se solapan y restarlos bien no paga la pena |
| `ubicacion` | text | no | |
| `disponibilidad` | text | no | |
| `pretension_min` | numeric(12,2) | no | |
| `pretension_max` | numeric(12,2) | no | |
| `pretension_moneda` | text | no | `PEN` o `USD` |
| `actualizado_en` | timestamptz | sí | |
| `creado_en` | timestamptz | sí | |

**Clave primaria:** `id` · **Único:** `persona_id` · **Apunta a:** `persona`
**Restricciones:** `pretension_max` ≥ `pretension_min`; y de los tres campos de pretensión, **o
los tres o ninguno**

La pretensión solo la ve quien tenga el permiso `ver_pretension`, y **nunca viaja en listas ni
en rankings**.

## `experiencia_perfil`

| Columna | Tipo | Oblig. | Qué guarda |
|---|---|---|---|
| `id` | bigint | sí | Clave |
| `perfil_candidato_id` | bigint | sí | |
| `puesto` | text | sí | |
| `empresa` | text | sí | |
| `desde` | date | sí | |
| `hasta` | date | no | **Vacío significa «sigo aquí»**, sin una bandera aparte que pueda contradecir a la fecha |
| `descripcion` | text | no | |
| `origen` | text | sí | `PERSONA` o `CURRICULUM` |
| `confirmado_en` | timestamptz | no | |
| `orden` | integer | sí | |
| `creado_en` | timestamptz | sí | |

**Clave primaria:** `id` · **Apunta a:** `perfil_candidato` · **Restricción:** `hasta` ≥ `desde`

## `educacion_perfil`

| Columna | Tipo | Oblig. | Qué guarda |
|---|---|---|---|
| `id` | bigint | sí | Clave |
| `perfil_candidato_id` | bigint | sí | |
| `titulo` | text | sí | |
| `institucion` | text | sí | |
| `nivel_codigo` | text | no | |
| `desde` | date | no | |
| `hasta` | date | no | |
| `en_curso` | boolean | sí | |
| `origen` | text | sí | `PERSONA` o `CURRICULUM` |
| `confirmado_en` | timestamptz | no | |
| `orden` | integer | sí | |
| `creado_en` | timestamptz | sí | |

**Clave primaria:** `id` · **Apunta a:** `perfil_candidato`, `nivel_educativo`

## `idioma_perfil`

| Columna | Tipo | Oblig. | Qué guarda |
|---|---|---|---|
| `id` | bigint | sí | Clave |
| `perfil_candidato_id` | bigint | sí | |
| `idioma` | text | sí | |
| `nivel_codigo` | text | sí | |
| `origen` | text | sí | `PERSONA` o `CURRICULUM` |
| `confirmado_en` | timestamptz | no | |
| `creado_en` | timestamptz | sí | |

**Clave primaria:** `id` · **Único:** `perfil_candidato_id` + `idioma`
**Apunta a:** `perfil_candidato`, `nivel_idioma`

El único se apoya en que los nombres se normalizan antes de comparar —sin mayúsculas, sin tildes
y sin espacios de más—, que es lo que impide que «Inglés» e «ingles» entren como dos idiomas.

## `certificacion_perfil`

| Columna | Tipo | Oblig. | Qué guarda |
|---|---|---|---|
| `id` | bigint | sí | Clave |
| `perfil_candidato_id` | bigint | sí | |
| `nombre` | text | sí | |
| `entidad` | text | no | |
| `emitida_en` | date | no | |
| `vence_en` | date | no | **Vacío = no caduca.** Muchas sí lo hacen —colegiatura, primeros auxilios, seguridad— y en salud eso decide si alguien puede trabajar o no |
| `origen` | text | sí | `PERSONA` o `CURRICULUM` |
| `confirmado_en` | timestamptz | no | |
| `creado_en` | timestamptz | sí | |

**Clave primaria:** `id` · **Apunta a:** `perfil_candidato`
**Restricción:** `vence_en` ≥ `emitida_en`

## `enlace_perfil`

| Columna | Tipo | Oblig. | Qué guarda |
|---|---|---|---|
| `id` | bigint | sí | Clave |
| `perfil_candidato_id` | bigint | sí | |
| `tipo` | text | sí | `LINKEDIN`, `GITHUB`, `PORTAFOLIO`, `PUBLICACION`, `PRODUCTO`, `OTRO` |
| `url` | text | sí | |
| `creado_en` | timestamptz | sí | |

**Clave primaria:** `id` · **Único:** `perfil_candidato_id` + `tipo` + `url`
**Apunta a:** `perfil_candidato`

No lleva `origen` ni `confirmado_en`: los enlaces los pone siempre la persona.

---

# Índices

PostgreSQL crea un índice para cada clave primaria y cada restricción de unicidad. Estos hay que
crearlos a mano.

⚠️ **PostgreSQL no crea índice sobre una clave foránea.** Es el olvido más común y el que más
caro sale: cada consulta que filtra por el padre acaba leyendo la tabla entera.

| Índice | Para qué |
|---|---|
| `postulacion (vacante_id, estado_codigo)` | El embudo de una vacante, que es la consulta más frecuente |
| `postulacion (estado_codigo)` | La bandeja de trabajo: todo lo que espera a alguien |
| `postulacion (usuario_id)` | Las postulaciones de un candidato |
| `postulacion (organizacion_id, movido_en)` | Cuántos días lleva sin avanzar y el cierre por inactividad |
| `postulacion (grupo_prioridad, vacante_id)` | Los grupos de prioridad, y la confirmación por lote |
| `transicion_estado (postulacion_id, ocurrida_en)` | Reconstruir el recorrido de un candidato |
| `respuesta (evaluacion_id)` | Cargar un examen entero |
| `nota_criterio (postulacion_id)` | Armar la ficha del candidato |
| `nota_etapa (postulacion_id)` | Lo mismo |
| `hallazgo_perfil (perfil_talento_id)` | El informe final |
| `trabajo_ia (creado_en) WHERE estado = 'PENDIENTE'` | **Índice parcial.** La cola lee solo los pendientes, que son pocos frente al total |
| `intento_prueba (vence_en) WHERE entregado_en IS NULL` | **Índice parcial.** El barrido que busca relojes agotados |
| `validacion (fin_en) WHERE estado = 'EN_CURSO'` | **Índice parcial.** El barrido que cierra periodos vencidos |
| `ejecucion_ia (trabajo_ia_id)` | Ver los intentos de un encargo |
| `ejecucion_ia (creado_en)` | Medir gasto y tiempos por periodo |
| `auditoria (entidad, entidad_id)` | Qué le pasó a una fila concreta |
| `auditoria (organizacion_id, ocurrida_en)` | Recorrer el registro por fechas |
| `inscripcion_sesion (sesion_simulacion_id)` | Cuánto cupo queda en una sesión |
| `marca_tiempo_simulacion (inscripcion_sesion_id)` | Las marcas de un candidato |
| `vacante (organizacion_id, estado)` | Las vacantes publicadas |
| `evaluacion (usuario_id, vigente_hasta)` | Qué se le puede reutilizar a alguien |
| `prospecto_familia (familia_codigo)` | Buscar prospectos compatibles con una vacante nueva |

Los índices de `organizacion_id` sueltos **no hacen falta**: van dentro de los compuestos de
arriba, porque toda consulta filtra primero por organización.

---

# Lo que la base impide por sí sola

Resumen de las restricciones que están repartidas por el documento:

- Una postulación tiene un solo estado a la vez, y ese estado existe en el catálogo.
- Un usuario no puede postular dos veces a la misma vacante.
- Una persona no puede tener dos cuentas en la misma organización.
- El correo es único **dentro de una organización**.
- Un usuario tiene contraseña o identificador de RENASER OS, nunca los dos.
- No se puede responder a una pregunta que no está en el orden de esa evaluación.
- `auditoria` y `transicion_estado` no admiten UPDATE ni DELETE.
- Toda transición manual y todo ajuste de nota exigen motivo escrito.
- Toda nota exige explicación.
- Todo puntaje de etapa apunta a una versión de pesos.
- Toda vacante apunta a una solicitud de talento.
- La validación de trabajo real exige tipo de vinculación registrado.
- Un entregable tiene archivo o enlace.
- El rango del cambio inesperado cabe dentro de la duración de la prueba.

Lo que **no** cabe en una restricción y hay que probar en el código está en
[Modelo de datos](05-MODELO-DE-DATOS.md), en «Lo que la base impide por sí sola, y lo que no».

---

# Documentos relacionados

- [Qué hace el sistema](00-QUE-HACE-EL-SISTEMA.md) — el sistema entero, sin nada técnico
- [Modelo de datos](05-MODELO-DE-DATOS.md) — por qué el modelo es así. **Léelo primero**
- [Alcance del MVP](08-ALCANCE-DEL-MVP.md) — qué tablas entran en cada hito
- [Requisitos funcionales](01-REQUISITOS-FUNCIONALES.md) — qué hace el sistema
- [Estados de la postulación](03-ESTADOS-POSTULACION.md) — los 18 estados y sus transiciones
- [Roles y permisos](04-ROLES-Y-PERMISOS.md) — los 75 permisos
- [Diagrama del modelo](diagramas/modelo-de-datos.html) — se abre en el navegador
