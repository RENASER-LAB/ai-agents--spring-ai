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

**La base ya está construida.** Las migraciones `V1` a `V70` viven en
`src/main/resources/db/migration` —**120 tablas de este módulo**, 123 en la base contando la de
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

La `V51` (05/09/2026) le da al perfil **foto, portada, currículum propio y diplomas**: cinco
columnas en `perfil_candidato`, una en `certificacion_perfil` y la tabla `lectura_cv_perfil`,
que sigue la lectura del currículum subido al perfil sin colgar de ninguna postulación. **Nada de
eso llega al panel ni a la IA**: la foto la ve solo el candidato (RF-41, decidido el 05/09/2026).

La `V55` (14/09/2026) convierte el sueldo en **un trato entre los dos lados**: cinco columnas de
remuneración en `vacante` y tres de pretensión en `postulacion`. Hasta entonces la vacante decía
el dinero en un texto libre (`compensacion_publica`, que queda retirada) y el candidato decía el
suyo en su perfil, opcional y sin mirar ninguna vacante concreta: las dos mitades del mismo dato
vivían separadas y ninguna comprometía a nadie. Ver [El sueldo, de los dos
lados](EL-SUELDO-DE-LOS-DOS-LADOS.md).

La `V56` (14/09/2026) le da al portal **una campana**: la tabla `aviso_portal` guarda lo que pasó
mientras el candidato no estaba, con su estado de leído. Nace con un solo tipo de aviso —el cambio
de sueldo de la V55— y está hecha para los que vengan; la V58 suma el segundo, la V60 el tercero,
la V63 los cuatro de las reseñas de empresas y la V70 los cinco de los cambios de etapa y los dos
de los recordatorios.

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
todas las organizaciones. La `V39` es de una línea: siembra la tarifa del modelo rápido, que
a la `V38` se le quedó fuera y dejaba invisible el gasto de leer currículums.

La `V57` (17/09/2026) **no crea ninguna tabla: siembra dos precios que faltaban**, y es el mismo
agujero de la `V39` entrando por otra puerta. El 10/09 DeepSeek renombró su modelo y empezó a
contestar un nombre que la tabla de precios no tenía, así que durante una semana todas las
calificaciones y todas las lecturas de currículum se anotaron sin costo y el tope mensual no vio
nada. La segunda fila es del modelo del orquestador, que nunca tuvo precio desde que existe el
control del gasto. Por qué un cambio de nombre llega hasta aquí, y por qué se sembró el precio de
fuera de punta: [El modelo cambió de nombre](EL-MODELO-CAMBIO-DE-NOMBRE.md).

La `V58` (19/09/2026) **no crea tablas ni columnas**. Acompaña a la edición de la vacante desde
el panel: la campana suma el tipo de aviso `VACANTE_ACTUALIZADA` —`aviso_portal.tipo` ya era
texto libre, así que solo cambia su comentario— y **el correo del cambio de sueldo se apaga sin
borrarse**: las versiones de `REMUNERACION_ACTUALIZADA` en `plantilla_correo` quedan con
`es_activa = false`. Lo que cambia en una vacante se cuenta desde entonces solo por la campana.
Ver [El sueldo, de los dos lados](EL-SUELDO-DE-LOS-DOS-LADOS.md).

La `V59` (21/09/2026) **añade una sola columna**, `vacante.archivada_en`, para retirar de la
lista de todos los días lo que ya terminó. No inventa un estado —una archivada sigue `CERRADA`,
y meterlo en `estado` habría obligado a la máquina de estados, al portal y al ranking a aprender
un estado que no les dice nada, perdiendo además cómo terminó la vacante— y no borra nada:
quitar la fecha la devuelve a la lista sin reabrir ninguna postulación. Trae su CHECK (solo se
archiva una `CERRADA`) y un índice parcial para la lista habitual; ninguna vacante existente
queda archivada. Ver `vacante` en el [diccionario de datos](07-DICCIONARIO-DE-DATOS.md).

La `V60` (21/09/2026) es el **borrado lógico de la vacante**, y tampoco borra filas: añade
`vacante.eliminada_en`, una fecha como la del archivo. Con ella puesta, la vacante sale del panel,
del portal, de los rankings, de las exportaciones, de la simulación y de los procesos automáticos,
pero sus postulaciones, transiciones, notas, currículums y correos siguen enteros —y el borrado de
datos personales las sigue alcanzando—, y quién la eliminó y por qué queda en `auditoria` (acción
`eliminar_vacante`). **Sin CHECK a propósito**: se elimina
en cualquier estado, archivada incluida, y el CHECK de la `V59` sigue igual. Sustituye el índice
parcial de la lista habitual por `ix_vacante_lista_habitual` (sin archivar **y** sin eliminar) y
añade `ix_vacante_archivadas_vivas` para Archivadas. Suma el motivo de cierre
`VACANTE_ELIMINADA` al CHECK de `postulacion.motivo_cierre`, el tipo de aviso
`VACANTE_ELIMINADA` —solo en el comentario de `aviso_portal.tipo`, que es texto libre— y el
permiso `eliminar_vacante`. Ninguna vacante existente queda eliminada. Restaurar una es quitar
la fecha a mano en la base: no hay vuelta desde el panel.

La `V61` (22/09/2026) trae **«¿Olvidaste tu contraseña?»**, en el portal y en el panel. Crea la
tabla `recuperacion_clave`: cada fila es un enlace para elegir una contraseña nueva, y guarda la
huella del token (nunca el token), cuándo vence, cuándo se usó y cuándo quedó sin efecto porque
se pidió otro. Cuelga de la **cuenta** (`usuario`) y no del correo, porque un mismo correo puede
tener cuenta de equipo en dos empresas y cada una tiene su contraseña; y se borra con ella
(`ON DELETE CASCADE`), que en la práctica solo alcanza a las cuentas de prueba —las reales no se
borran, se anonimizan—. Siembra además tres parámetros de la plataforma
(`minutos_vida_recuperacion`, 60; `max_recuperaciones_por_hora`, 3 por cuenta;
`max_recuperaciones_por_ip_hora`, 30) y los dos correos, `RECUPERAR_CLAVE_CANDIDATO` y
`RECUPERAR_CLAVE_EQUIPO`, solo para la plataforma: las empresas que ya existían no tienen copia y
el envío usa el de la plataforma. Ver `recuperacion_clave` en el
[diccionario de datos](07-DICCIONARIO-DE-DATOS.md).

La `V62` (25/09/2026) le da **ciudad a la vacante**: una columna, `vacante.ciudad_ubigeo`, que
apunta al mismo catálogo `ubigeo` que la ciudad del candidato, con su índice parcial. Es lo que
deja al portal filtrar por ciudad. El texto libre `ubicacion` no cambia de nombre ni de
contenido: pasa a ser la **zona o referencia** (barrio, distrito o dirección). La migración solo
rescata la ciudad cuando ese texto es exactamente el nombre de una provincia, sin mirar
mayúsculas ni tildes; las demás quedan sin ciudad para que el equipo la elija. Ver `vacante` en el
[diccionario de datos](07-DICCIONARIO-DE-DATOS.md).

La `V63` (28/09/2026) trae **las reseñas de empresas a quien contrataron**: tres tablas nuevas
—`resena`, `respuesta_resena` y `reporte_resena`— y tres permisos, `resenar_contratado` y
`ver_resenas_candidato` para Talento, responsable del área y Dirección de **todas** las
organizaciones que ya existían, y `moderar_resenas` solo para el Administrador de la plataforma.
La reseña cuelga de la **postulación** (una viva por contratación) y apunta también a la
**persona**, porque es su reputación y la leen todas las empresas donde postula: es la única
información de un proceso que cruza de una empresa a otra, y **no puntúa**. Los cuatro avisos
nuevos de la campana van solo en el comentario de `aviso_portal.tipo`, que es texto libre. Ver
«Reseñas de empresas» más abajo y en el [diccionario de datos](07-DICCIONARIO-DE-DATOS.md).

La `V64` (29/09/2026) abre **la gestión de personas**, el primer apartado de la
[ampliación de RR.HH.](AMPLIACION-RRHH.md): hasta ella el sistema terminaba en `CONTRATADO` y la
persona que entraba a trabajar no existía. Seis tablas nuevas —`sede`, `colaborador`,
`periodo_laboral`, `situacion_laboral`, `cese_anulado` y `contratado_sin_alta`—, ninguna columna
nueva en las viejas y cuatro permisos del grupo `PERSONAS` (`ver_colaboradores`,
`editar_colaboradores`, `ver_sueldos` y `editar_estructura`) para Talento y Dirección de
**todas** las organizaciones, `ver_sueldos` solo para Dirección. **Ninguna FK apunta a
`persona`**, a propósito: la ficha de quien trabaja se conserva aunque la cuenta del portal se
borre. Los cargos son el `puesto` que ya existía, que desde aquí se renombra y se desactiva
(`es_activo` existía desde la `V5` y ninguna pantalla lo cambiaba). La `V65`, el mismo día, añade a
`situacion_laboral` diez columnas `antes_*`: la situación viva que un cambio tenía detrás en el
momento de anularlo, para que el historial lo compare siempre con eso. Ver «Gestión de personas»
más abajo y en el [diccionario de datos](07-DICCIONARIO-DE-DATOS.md).

La `V66` (30/09/2026) trae **las preguntas propias de cada vacante**: hasta ella, para que un
candidato respondiera preguntas alguien tenía que llenar un Excel con los 15 formatos del método
de RENASER. Desde aquí cualquier empresa escribe, desde el panel y para cada vacante, las
preguntas de su Perfil Integral: cuatro tipos, agrupados en criterios con nombre que suman 100
puntos. **Vive al lado del banco por nivel, sin tocarlo.** Dos tablas nuevas —`criterio_banco`,
los criterios de un banco de vacante, y `propuesta_preguntas`, lo que propone la IA— y columnas
nuevas en cinco viejas: `vacante.origen_preguntas` dice si la vacante rinde el banco del nivel
(`NIVEL`) o sus propias preguntas (`VACANTE`); `version_banco` gana `proposito` —un banco de
vacante es su cuestionario técnico o sus preguntas propias, y cada uno tiene su borrador y su
publicada—, el método `PUNTOS`, su guía de calificación y el número de esa guía; `pregunta` gana
tres tipos, sus puntos, su criterio y «qué debe tener una buena respuesta»; `opcion`, un orden
explícito; y `nota_respuesta` pasa de 0–4 a 0–100 y guarda aparte la nota que puso la IA. Siembra
el agente `RECOMENDADOR` con su instrucción, publica una instrucción del `EVALUADOR` que vale para
los tres métodos, y abre en `trabajo_ia` el modo `RECALIFICA`. **Las vacantes que ya existían
siguen con el banco del nivel**; las nuevas nacen con preguntas propias. Ver «Banco de preguntas»
más abajo y en el [diccionario de datos](07-DICCIONARIO-DE-DATOS.md).

La `V67` (01/10/2026) trae **la prueba técnica escrita en el mismo editor**: hasta ella, la etapa
técnica salía de una plantilla cargada por guiones o del cuestionario CAZATALENTOS, y ninguno
servía a una empresa cliente. `vacante.instrumento_etapa_tecnica` gana un tercer valor,
`PRUEBA_PROPIA`, que es el de toda vacante nueva; `version_banco` gana un tercer propósito,
`PRUEBA_PUESTO`, con **el caso** (enunciado, adjunto, materiales, herramientas) y **el tiempo**
(modalidad, minutos o días), sin cambio inesperado; `criterio_banco` gana **la parte calificada**
—sus puntos y quién la califica, la IA o una persona—. Se reutilizan `entregable_requerido`,
`intento_prueba` y `respuesta_prueba`, que dejan de exigir una plantilla: cuelgan de una
plantilla **o** de una versión del editor, nunca de las dos. Dos tablas nuevas:
`criterio_banco_entregable`, qué entregables mira cada criterio, y `nota_criterio_prueba`, la nota
de la parte calificada **por id de criterio** —no se usa `nota_criterio`, que apunta a `criterio`
y mezcla las tres etapas—. El intento distingue además **la prueba no completada** (venció con
algo sin responder) y las propuestas de la IA llevan propósito, para que las del banco y las de la
prueba de una misma vacante no se pisen. **Las vacantes que ya existían siguen con su
instrumento y sus datos.** Ver «Prueba del puesto» más abajo y en el
[diccionario de datos](07-DICCIONARIO-DE-DATOS.md).

La `V68` (05/10/2026) **simplifica esa prueba**: los archivos se piden donde se usan y el sistema
deduce qué mira cada criterio. `entregable_requerido` gana `alcance` —el archivo de una pregunta
(`pregunta_id`, como mucho uno por pregunta), un general de toda la prueba o uno de unas preguntas—
y una tabla nueva, `entregable_cubre_pregunta`, guarda cuáles. `criterio_banco_entregable` deja de
escribirse: solo la leen las versiones publicadas antes, que conservan su «Mira» marcado a mano y
sus notas. Los borradores pasaron al modelo nuevo y perdieron los días: «Sin cronómetro» es
`PLAZO_ABIERTO` sin días, hasta la fecha límite, que sigue en `vacante.prueba_cierra_en`. La `V69`
(06/10/2026) añade `criterio_banco.puntos_del_criterio`: se escribe lo que vale el criterio entero y
la parte calificada se deduce restándole las cerradas. Las publicadas de antes lo tienen vacío.

La `V70` (07/10/2026) acompaña **la prueba al instante, la campana en cada etapa y los
recordatorios**. `transicion_estado` gana `aviso_al_candidato` —cómo se enteró el candidato de
cada paso: `CORREO`, `NINGUNO` o `POR_LA_CAMPANA`—, vacía en todo lo anterior, y es lo que impide
que un recordatorio le cuente a alguien lo que el equipo decidió callar. Una tabla nueva,
`recordatorio_enviado`, guarda qué recordatorio salió (o se dio por omitido) para qué turno, y dos
índices únicos parciales impiden repetirlo. Siembra en **todas** las organizaciones los tres
parámetros de los recordatorios y los textos `RECORDATORIO_EVALUACION` y `RECORDATORIO_PRUEBA`, y
publica una versión nueva de los textos de `PRUEBA_DISPONIBLE` que decían «desde este correo»,
conservando las viejas. Abrir la prueba al instante no toca el esquema. Ver
[estados de la postulación](03-ESTADOS-POSTULACION.md) y el
[diccionario de datos](07-DICCIONARIO-DE-DATOS.md).

La `V54` (14/09/2026) **no añade ninguna tabla y cambia quién firma qué**. Hasta ella había dos
tipos de texto —`PROCESO` y `FUTUROS_CONTACTOS`— y el de la cuenta usaba el primero, que habla de
«esta vacante» cuando al registrarse todavía no hay ninguna: quien luego postulaba a una vacante
de Renaser firmaba dos veces el mismo texto. La migración suma el tipo **`PLATAFORMA`** al CHECK
de `texto_consentimiento.tipo` y publica ese texto para la organización plataforma, junto con una
`1.1` del opcional. Además renombra la organización plataforma a su razón social, «RENASER
CONSULTING S.A.C.», porque ese nombre viaja dentro del texto que firma cada candidato.

**Y el texto de `PROCESO` pasó a ser uno solo para todas las empresas.** Antes había una fila por
empresa y lo único que cambiaba entre ellas eran las tres palabras de su razón social: corregir
una coma obligaba a republicar empresa por empresa, y una empresa recién dada de alta no podía
recibir candidatos hasta publicar la suya. La V54 publica **una sola** `1.1`, de la plataforma,
con un hueco donde va el nombre de quien publica la vacante, y **retira** —les quita la fecha de
publicación, sin borrar nada— todas las filas que seguían siendo el texto provisional de la v1.0:
las copias que el alta había repartido y las dos de la propia plataforma, ya relevadas. Nadie las
lee, pero el panel de textos legales las listaría como vigentes.

De ahí salen tres consecuencias que se ven desde fuera: **ninguna empresa puede publicar textos**
(el panel responde 400 a los tres tipos), **el alta de una empresa no le copia ninguno**, y
**publicar una vacante ya no exige tener texto propio** — ese freno se retiró con la migración.

Lo que sí añade la V54 es **una columna**: `consentimiento.texto_firmado`, donde se guarda el
texto tal como se le pintó a la persona, con el nombre de la empresa ya puesto. Con el hueco,
apuntar a la fila dejó de bastar para saber qué leyó cada quien.

⚠️ **La V54 no toca las filas ya existentes de `consentimiento`.** Quien ya tenía cuenta sigue
ligado al texto que firmó, y no hay mecanismo para pedirle el nuevo. Ver
[BORRADOR-CONSENTIMIENTO-v1.1.md](BORRADOR-CONSENTIMIENTO-v1.1.md).

La `V40` **no añade ninguna tabla**, y eso es lo interesante: lo que hacía falta —saber quién
eligió cada fecha de una sesión— ya estaba en `inscripcion_sesion`, solo que no salía por
ningún endpoint. Lo único que trae son dos permisos, `ver_inscritos_simulacion` y
`administrar_permisos`, con su reparto inicial en `rol_permiso`. Y ese reparto **es un punto de
partida, no una decisión grabada en el código**: desde este mismo hito se edita por la API, y
`FiltroIdentidad` lo relee en cada petición. Ver [Roles y permisos](04-ROLES-Y-PERMISOS.md).

⚠️ Esta migración nació como `V37` y hubo que renumerarla: `main` reclamó ese número para el
multiempresa mientras la rama estaba abierta. Git no ve ese choque —dos archivos con nombres
distintos se fusionan sin conflicto— y quien se entera es Flyway al arrancar, así que ahora hay
una prueba que lo vigila (`MigracionesSinChoqueTest`). Si un comentario del código todavía dice
«V37» hablando de este reparto, es de antes de la renumeración.

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
ubigeo.codigo             = '1501'
```

Así, al mirar la tabla de postulaciones se entiende en qué estado está cada una sin cruzarla
con nada. Con un número habría que ir a buscar qué significa el 7 cada vez.

**`ubigeo` es el mismo patrón con una vuelta de tuerca.** Su código tampoco es un número
autogenerado, pero no lo escogió este proyecto: es el del INEI, y su ancho dice a qué nivel
pertenece —dos cifras el departamento, cuatro la provincia, seis el distrito—, así que `'1501'`
es la provincia de Lima y se sabe que cuelga del departamento `'15'` sin consultar nada. Es el
único catálogo con **padre**: la tabla se apunta a sí misma, y esa es la razón de que añadir el
distrito mañana no obligue a tocar ninguna otra tabla. La fila `'EXT'` rompe el formato a
propósito, porque «fuera del Perú» no tiene código INEI.

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
| Equipo de Renaser o de una empresa cliente | La suya, cifrada (desde el 25/08/2026: login propio, cuentas por invitación) | Vacío salvo que algún día se conecte RENASER OS |
| Candidato | La suya, cifrada; o entra con un enlace de un solo uso | Vacío. No es usuario de RENASER OS |

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

⚠️ **Esto es sobre lo ya rendido, no sobre lo que alguien está escribiendo.** Desde el
16/09/2026, un candidato que vacía el recuadro de una pregunta **borra esa respuesta**, y así
tiene que ser: mientras el examen sigue abierto, lo escrito todavía no es historia de nadie —lo
es en cuanto se entrega, y desde ahí no se toca—.

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
     ubigeo (donde vive, y
       donde esta el puesto)
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

   POSTULACION CONTRATADO
         |  (alta opcional; una por contratacion)
         v
   GESTION DE PERSONAS (V64)        ninguna FK a persona: la ficha
  colaborador · periodo_laboral     sobrevive al borrado del portal
  situacion_laboral (desde-hasta):
    sede · area · puesto (= cargo) · jefe (otro colaborador)
  cese_anulado · contratado_sin_alta


  AGENTES DE IA                  AUDITORIA
 catalogo · encargo        quien, cuando, que cambio, por que
 ejecucion                            ^
       |                              |
       +-- toda nota automatica ------+
           apunta a su ejecucion


  RENASER OS  < - - HTTP - -   tareas y tiempos        (integracion futura,
  (otro servicio)              desempeno 30/90/180      hoy dormida)
```

Tres cosas que el mapa deja ver y conviene subrayar: **la evaluación cuelga del usuario**, no de
la postulación, por lo dicho en la tercera decisión; **todo lo que califica una máquina apunta a
la ejecución concreta** que produjo esa nota; y **RENASER OS es un servicio aparte**, no una
tabla más, y hoy no está conectado: la identidad del equipo es propia y las métricas de la
validación las pone una persona.

Hay una versión dibujada de este mismo mapa en
[diagramas/modelo-de-datos.html](diagramas/modelo-de-datos.html), que se abre en el navegador.

---

## Las tablas

Ciento veinte en total, agrupadas por área para poder leerlas de a poco. En cada una se nombran
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

### Personas, acceso y permisos · 9 tablas

Un rol es un conjunto de permisos con nombre guardado en la base de datos, no algo fijo en el
código. El Administrador puede crear roles nuevos y repartir permisos sin que nadie programe.

| Tabla | Para qué existe | Columnas que importan |
|---|---|---|
| `persona` | Quién es alguien. Vale para el equipo y para quien postula | nombre, apellidos, telefono, documento, fecha_nacimiento, ciudad_ubigeo, anonimizado_en |
| `ubigeo` | El catálogo geográfico del Perú (INEI): dónde se puede decir que uno vive y, desde la `V62`, dónde está el puesto de una vacante. Es el único catálogo con padre, porque el país es un árbol | codigo, nivel, padre, nombre, activo |
| `usuario` | Cómo entra al sistema | organizacion_id, persona_id, correo, contrasena_hash, usuario_renaser_os_id, area_id, es_activo |
| `area` | El departamento que contrata. Hace falta para saber qué ve un responsable y para impedir que alguien sea Evaluador de Estándar de su propia área | organizacion_id, nombre |
| `rol` | Un nombre y una lista de permisos | organizacion_id, codigo, nombre, descripcion |
| `permiso` | Una acción suelta que se puede conceder o no. Son 81 diseñados, 79 sembrados | codigo, etiqueta, grupo |
| `usuario_rol` | Una persona puede tener varios roles. Puede hacer lo que le permita cualquiera de ellos | usuario_id, rol_id |
| `rol_permiso` | Qué permisos tiene un rol y **con qué alcance** | rol_id, permiso_id, alcance |
| `recuperacion_clave` | El enlace de «¿Olvidaste tu contraseña?»: un solo uso, vida corta, y solo vale el último que se pidió (`V61`) | usuario_id, token_hash, vence_en, usado_en, invalidado_en |

`contrasena_hash` y `usuario_renaser_os_id` son **excluyentes**: quien tiene uno no tiene el
otro. Desde el 25/08/2026 el equipo entra también con contraseña (cuentas por invitación) y
`usuario_renaser_os_id` queda reservado para una integración futura que hoy está dormida.

El `area_id` queda vacío en los candidatos, que no pertenecen a ningún departamento.

**La ciudad cuelga de `persona` y no del perfil del candidato.** El perfil se crea perezosamente
—solo cuando el agente propone datos sacados del currículum— y la ciudad se pide al crear la
cuenta, cuando la única fila que existe de esa persona es esta. Guardarla en el perfil habría
significado que quien todavía no subió currículum no tiene ciudad, justo lo que hace falta para
filtrar una tanda. `perfil_candidato.ubicacion`, el texto libre que el candidato escribe sobre sí
mismo, **sigue existiendo y sigue siendo suyo**: lo que ya no hace es decidir dónde vive nadie a
efectos del panel, porque «Lima», «lima» y «Lima Cercado» son la misma ciudad y tres filtros
distintos. La migración lo leyó una vez para rellenar lo que pudo y lo dejó intacto.

El permiso guarda una etiqueta en lenguaje normal —«puede cerrar una vacante»— porque la
pantalla donde se reparten permisos nunca debe mostrar nombres técnicos. Los permisos **no
llevan organización**: son los mismos para todos, y lo que cambia es quién los tiene.

El alcance tiene tres valores: **propio**, **sus vacantes** y **todo**. Qué alcanza cada uno
—y por qué **propio** es el alcance del portal y en el panel no llega a ninguna fila— está en
[Roles y permisos](04-ROLES-Y-PERMISOS.md).

---

### Consentimiento y borrado · 4 tablas

| Tabla | Para qué existe | Columnas que importan |
|---|---|---|
| `texto_consentimiento` | El texto que se acepta, versionado y con su huella | organizacion_id, tipo, version, texto, hash, publicado_en |
| `consentimiento` | Que esta persona aceptó esta versión concreta | persona_id, texto_consentimiento_id, postulacion_id, texto_firmado, aceptado_en, ip, id_sesion, user_agent, retirado_en |
| `politica_conservacion` | Cuánto se guardan los datos y qué se hace al vencer | organizacion_id, meses, accion_al_vencer, es_activa |
| `solicitud_borrado` | Pedir el borrado y ejecutarlo son dos cosas distintas, con días de por medio | persona_id, solicitado_en, ejecutado_en, ejecutado_por_usuario_id |

**Son tres consentimientos, no uno** (desde la `V54`, 14/09/2026). El `tipo` los distingue, y lo
que los separa es **con quién se firma cada uno**:

| `tipo` | Con quién se firma | Qué cubre | De quién es la fila |
|---|---|---|---|
| `PLATAFORMA` | Renaser | La cuenta, el perfil, la inteligencia artificial, los proveedores, la salida de datos del país, el plazo y los derechos | De la organización plataforma |
| `PROCESO` | La empresa de la vacante | Que ella decida sobre esa postulación | De la organización plataforma: **es una sola fila para todas las empresas** |
| `FUTUROS_CONTACTOS` | Renaser | Conservar el perfil y avisarle de otras vacantes | De la organización plataforma |

El de futuros contactos nunca se da por supuesto, y `retirado_en` permite quitarlo sin tocar
ninguno de los otros dos. **Los tres son de la plataforma, y solo ella los publica**: el panel
responde 400 a una empresa que intente publicar cualquiera de los tres.

**Con quién se firma y de quién es la fila son dos cosas distintas, desde la V54.** El texto de
`PROCESO` lleva un hueco donde va el nombre de quien publica la vacante, y ese nombre se pone al
leerlo: una sola fila sirve a todas las empresas, y cada candidato lee el nombre de la suya. Por
eso la fila es siempre de la plataforma aunque el permiso se firme con la empresa.

**Y por eso se guarda lo leído, no solo a qué fila apunta.** `texto_firmado` guarda el texto ya
compuesto, tal como se le pintó a la persona: dos candidatos de dos empresas firman la misma fila
y han leído cosas distintas. Vacío significa «lo firmado es el texto literal de su fila», que es
el caso de los dos de la cuenta y de todo lo anterior a la V54. **Saber con qué empresa se firmó
se responde con la postulación y con ese texto, nunca con el dueño de la fila.**

**Desde el multiempresa, el del proceso se firma con cada empresa.** `postulacion_id` vacío
es un consentimiento de cuenta con la plataforma (`PLATAFORMA` al registrarse, y
`FUTUROS_CONTACTOS` si lo marcó); lleno, el de la empresa de esa vacante, aceptado al postular.
Postular a tres empresas son tres filas, cada una a nombre de la suya — lo que la ley 29733
espera: cada quien que trata datos, nombrado y consentido. Por eso la unicidad por persona y
texto rige solo en las filas de cuenta: re-postular a la misma empresa con el mismo texto
vigente vuelve a firmarse.

**Cuál es el texto vigente lo responde un solo sitio.** Vigente es **el publicado más reciente**
de esa organización y ese tipo, no «el marcado como activo». Antes lo preguntaban por su cuenta
los cuatro sitios que lo necesitan —el tablón antes de postular, la postulación al firmar, el
registro de la cuenta y la política de privacidad pública—, y cuatro copias de la misma búsqueda
acaban contestando distinto.

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
| `puesto` | El catálogo de puestos, con su nivel y su familia. Desde la `V64` es también el catálogo de **cargos** de la gestión de personas, y se renombra y desactiva desde Configuración | organizacion_id, codigo, nombre, nivel_puesto_codigo, familia_codigo, es_activo |
| `vacante` | Una convocatoria concreta | organizacion_id, solicitud_talento_id, puesto_id, titulo, descripcion, modalidad, ciudad_ubigeo, ubicacion, tipo_cierre, plazas, cierra_en, estado, version_pesos_id, version_plantilla_prueba_id, plantilla_evaluacion_id, responsable_usuario_id, remuneracion_tipo, remuneracion_min, remuneracion_max, remuneracion_moneda, remuneracion_actualizada_en, archivada_en, eliminada_en, aplica_evaluacion, origen_preguntas, instrumento_etapa_tecnica |
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

**La vacante dice lo que paga de una de tres formas** (`V55`): no publicarlo, un monto fijo, o un
rango. No es un detalle de presentación: **es lo que decide si quien postula está obligado a
declarar cuánto quiere ganar**. Si la empresa enseña su cifra, se le exige la suya; si la esconde,
no se le pide nada. El detalle del trato está en [El sueldo, de los dos
lados](EL-SUELDO-DE-LOS-DOS-LADOS.md).

`compensacion_publica` —el sueldo en prosa— **queda retirada**: los datos se conservan por las
vacantes viejas, pero ninguna pantalla la lee ni la escribe. Dos sitios donde decir el sueldo son
dos sitios donde contradecirse, y el trato necesita un número comparable, no una frase.

**Qué responde quien postula lo deciden dos columnas** (`V66`): `aplica_evaluacion`, el
interruptor de siempre, y `origen_preguntas` —el banco de la empresa para el nivel del puesto, o
las preguntas propias de la vacante—. Toda vacante nueva nace con las propias, también en
RENASER; el banco del nivel se elige a mano y solo si la empresa tiene uno **suyo**. **Desde la
primera postulación, el origen no cambia**: todos sus candidatos se miden con la misma vara.

**Qué se rinde en la etapa técnica lo dice `instrumento_etapa_tecnica`** (`V43`, `V67`):
`PLANTILLA`, `CUESTIONARIO_TECNICO` o `PRUEBA_PROPIA`, la prueba escrita en el editor. Toda
vacante nueva nace con `PRUEBA_PROPIA`, también en RENASER; las de antes conservan el suyo. El
valor por defecto de la columna sigue siendo `PLANTILLA`, que es lo que reciben las filas
insertadas a mano: el tercero lo pone el servicio al crear.

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

### Postulación y su historia · 4 tablas

| Tabla | Para qué existe | Columnas que importan |
|---|---|---|
| `estado_postulacion` | Catálogo cerrado de los 18 estados | codigo, nombre, etapa_codigo, momento_codigo, espera_a, orden, es_final |
| `postulacion` | Un usuario en una vacante. Tiene un solo estado a la vez, nunca dos | organizacion_id, uuid, usuario_id, vacante_id, estado_codigo, grupo_prioridad, motivo_cierre, evaluacion_id, rondas_evidencia_usadas, movido_en, pretension_monto, pretension_moneda, pretension_declarada_en |
| `transicion_estado` | Cada cambio de estado, guardado aparte. **No se modifica ni se borra nunca**. Desde la `V70`, con cómo se enteró el candidato | postulacion_id, estado_anterior_codigo, estado_nuevo_codigo, usuario_id, rol_id, es_sistema, es_por_lote, motivo, ocurrida_en, aviso_al_candidato |
| `recordatorio_enviado` | Qué recordatorio salió, o se dio por omitido, para qué turno: el turno es la transición que lo abrió (`V70`) | postulacion_id, transicion_estado_id, tipo, plazo_en, resultado |

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

**La pretensión vive en la postulación y no solo en el perfil** (`V55`). El perfil guarda la banda
general de la persona y es la que prellena el formulario —con el centro, no con el borde bajo—;
la postulación guarda **el número que confirmó delante del sueldo de este puesto**. Son dos datos
distintos a propósito: uno se escribió quizá hace meses sin mirar ninguna vacante, el otro se
escribió sabiendo lo que esta paga. Lo declarado vuelve al perfil solo si el perfil estaba vacío:
propone, nunca pisa.

⚠️ **Una pretensión vacía tiene tres motivos distintos** —la postulación es anterior a la `V55`, la
vacante no publicaba su sueldo, o quien mira no tiene permiso— y el panel tiene que decir cuál es.
Solo uno de los tres habla del candidato.

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

⚠️ **La prueba escrita en el editor (`V67`) no usa ninguna de estas dos tablas.** Sus criterios
son `criterio_banco` y su nota, `nota_criterio_prueba`. `nota_criterio` mezcla las tres etapas y
su clave ajena apunta a `criterio`: sumarla sin filtrar ya dio un 675 sobre 100.

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

### Banco de preguntas · 10 tablas

| Tabla | Para qué existe | Columnas que importan |
|---|---|---|
| `dimension` | Las 22 cosas que se miden: integridad, priorización, calidad, autonomía… | codigo, nombre, definicion, es_obligatoria |
| `version_banco` | Una versión del banco, en borrador o publicada. Desde la `V66`, también las preguntas propias de una vacante, y desde la `V67`, su prueba técnica | organizacion_id, tipo_banco, nivel_puesto_codigo, vacante_id, proposito, metodo_calificacion, guia_calificacion, version_guia, etiqueta, estado, publicada_por_usuario_id, publicada_en, enunciado, consigna_archivo_id, materiales, herramientas_permitidas, modalidad, duracion_minutos, plazo_dias |
| `pregunta` | Una pregunta dentro de una versión | version_banco_id, codigo, bloque, tipo, enunciado, situacion, logica_interna, es_puntuable, puntos, criterio_banco_id, que_debe_tener |
| `opcion` | Las opciones de respuesta | pregunta_id, letra, texto, puntaje, orden |
| `opcion_dimension` | Cuánto suma cada opción a cada dimensión | opcion_id, dimension_codigo, incremento |
| `pregunta_dimension` | Qué dimensiones evalúa una pregunta abierta, que no tiene opciones | pregunta_id, dimension_codigo |
| `par_consistencia` | Dos preguntas que miden lo mismo y deberían responderse parecido | version_banco_id, pregunta_a_id, pregunta_b_id, diferencia_maxima |
| `criterio_banco` | Los criterios de las preguntas propias de una vacante y de su prueba técnica: lo que se califica y lo que se ve como columna (`V66`; la parte calificada, `V67`; lo que vale entero, `V69`) | version_banco_id, nombre, que_evalua, orden, puntos_calificados, calificador, puntos_del_criterio |
| `criterio_banco_entregable` | Qué entregables mira cada criterio de la prueba técnica, marcado a mano (`V67`). Desde la `V68` solo en las versiones publicadas antes: en las demás se deduce del alcance | criterio_banco_id, entregable_requerido_id |
| `propuesta_preguntas` | Lo que propone la IA para completar el borrador de una vacante; no toca el borrador hasta que una persona lo agrega (`V66`). Desde la `V67`, con su propósito | organizacion_id, vacante_id, proposito, indicacion, puntos_que_faltan, estado, contenido, motivo_fallo, pedida_por_usuario_id |

**Las preguntas propias de una vacante son un banco más** (`V66`), del tipo `VACANTE` y con
propósito `PERFIL_INTEGRAL`, al lado de su cuestionario técnico (propósito
`CUESTIONARIO_TECNICO`). Se califican con el método `PUNTOS`: cada pregunta vale sus puntos y
todas suman 100. **Los puntos de un criterio no se guardan**: son la suma de sus preguntas, así
que no hay dos cifras que puedan contradecirse. Y **no se reutiliza la tabla `criterio`**, que ya
mezcla los ocho del currículum con los de la rúbrica de la prueba: una lectura de «los criterios
del Perfil Integral» juntaría estos con los del currículum. Los criterios se identifican por id,
nunca por nombre: con criterios por vacante habrá muchos llamados igual.

⚠️ **`puntos` y `peso` son dos columnas a propósito.** `peso` es el multiplicador 0–2 del banco
v3; `puntos`, lo que vale una pregunta propia de 0 a 100. En el método `PUNTOS` decide `puntos`, y
`es_puntuable` se guarda como `puntos > 0` solo para que no quede incoherente.

Lo que la IA propone se guarda **aparte, en `propuesta_preguntas`**, y el borrador no cambia
hasta que alguien agrega lo que quiere: criterios enteros o preguntas sueltas.

**La prueba técnica de una vacante es otro banco de vacante** (`V67`), con propósito
`PRUEBA_PUESTO` y método `PUNTOS`, al lado de sus preguntas propias. Se reutilizan los mismos
criterios, preguntas, opciones y guía numerada, para no tener dos editores, con dos diferencias.
**Las abiertas no llevan puntos**: cada criterio tiene una parte automática —la suma de sus
cerradas, que no se guarda— y una **parte calificada** (`puntos_calificados` y `calificador`, `IA`
o `PERSONA`) que califica el criterio entero mirando sus abiertas y los entregables que le tocan.
Desde la `V69` se guarda lo que vale el criterio entero (`puntos_del_criterio`) y la parte
calificada es eso menos sus cerradas; desde la `V68`, lo que mira se deduce del alcance de los
entregables (ver «Prueba del puesto»). Y la versión guarda **el caso y el tiempo**, que una
pregunta suelta no tiene. **No hay que unificar este camino con el del Perfil Integral**, que
califica pregunta a pregunta.

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
| `nota_respuesta` | El puntaje de esa respuesta y **por qué** | respuesta_id, puntaje, explicacion, evidencia_citada, confianza, ejecucion_ia_id, ajustada_por_usuario_id, motivo_ajuste, puntaje_ia, version_guia |
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

**En las preguntas propias (`V66`) la nota de una abierta va de 0 a los puntos de la
pregunta**, y por eso `puntaje` pasó de 0–4 a 0–100; los bancos de siempre siguen en 0–4, y eso
lo exige ahora el código. Cuando una persona la ajusta, `puntaje` es la que vale y **`puntaje_ia`
guarda la que había puesto la IA**, para que la ficha enseñe las dos. `version_guia` dice con qué
guía se calificó: si la empresa corrige la guía con candidatos dentro, las notas de la guía
anterior se reconocen y se vuelven a pedir. La evaluación de las preguntas propias **no lleva
plantilla** ni vigencia: su tiempo lo dice la versión y no se reutiliza en otra vacante.

Cada respuesta se guarda al momento, así que si se corta la luz el candidato retoma donde quedó.
Las preguntas y las opciones se muestran en orden aleatorio, distinto para cada persona, y ese
orden se guarda: es la única forma de mostrar meses después exactamente el examen que rindió.

**Desde el 16/09/2026 una respuesta puede desaparecer: vaciar el recuadro la borra.** Es la única
forma de que la pantalla y lo guardado digan lo mismo — antes se rechazaba el vacío y el texto
anterior se quedaba puesto, así que el candidato veía una pregunta en blanco que el sistema
contaba como respondida. Que la fila desaparezca tiene dos consecuencias que antes no existían:
dos guardados simultáneos de la misma pregunta pueden encontrarse con que la fila ya no está —se
vuelve a crear, no es un error—, y **borrar solo es seguro mientras el examen esté abierto**,
porque la nota de una respuesta apunta a la respuesta. Hoy no se puede llegar ahí: no se
califica hasta que se entrega, y entregado ya no se toca. Ver
[Lo que queda pendiente](#lo-que-queda-pendiente).

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

### Prueba del puesto · 11 tablas

| Tabla | Para qué existe | Columnas que importan |
|---|---|---|
| `plantilla_prueba` | La prueba de un puesto | organizacion_id, puesto_id, nombre |
| `version_plantilla_prueba` | Una versión concreta. Si tiene vacante, es una copia privada de esa vacante | plantilla_prueba_id, vacante_id, enunciado, modalidad, duracion_minutos, plazo_dias, minuto_cambio_min, minuto_cambio_max, minutos_extra, estado |
| `variante_cambio` | Las distintas formas que puede tomar el cambio inesperado | version_plantilla_prueba_id, texto, orden |
| `pregunta_prueba` | El catálogo de preguntas: previas, universales y del puesto. Sin dueño; desde el 05/10/2026 solo la plataforma lo lee y escribe por la API | codigo, enunciado, tipo, puesto_id, revela |
| `pregunta_version_plantilla` | Cuáles eligió esta plantilla | version_plantilla_prueba_id, pregunta_prueba_id, orden |
| `entregable_requerido` | Qué cosas distintas hay que entregar, cada una con su regla. Desde la `V67`, de una plantilla **o** de la prueba del editor; desde la `V68`, en el editor, con su alcance: de una pregunta o general | version_plantilla_prueba_id, version_banco_id, nombre, detalle, formato, es_obligatorio, orden, que_debe_tener, alcance, pregunta_id |
| `entregable_cubre_pregunta` | Las preguntas que reúne un entregable general de la prueba del editor (`V68`) | entregable_requerido_id, pregunta_id |
| `intento_prueba` | Cuando un candidato rinde. Desde la `V67`, una plantilla **o** la prueba del editor | postulacion_id, version_plantilla_prueba_id, version_banco_id, iniciado_en, vence_en, entregado_en, es_entrega_automatica, no_completada, variante_cambio_id, minuto_cambio, cambio_mostrado_en |
| `entregable` | Lo que sube o el enlace que pega, y **cuál de los pedidos es** | intento_prueba_id, entregable_requerido_id, archivo_id, enlace, version, subido_en |
| `respuesta_prueba` | Sus respuestas a las preguntas de la prueba. Desde la `V67`, también a las del editor, cerradas incluidas | intento_prueba_id, pregunta_prueba_id, pregunta_id, texto, opcion_id, detalle, respondida_en |
| `nota_criterio_prueba` | La nota de la parte calificada de un criterio de la prueba del editor, por id de criterio (`V67`) | intento_prueba_id, criterio_banco_id, puntaje, puntaje_ia, explicacion, origen, version_guia, ajustada_por_usuario_id, motivo_ajuste |

**Desde la `V67` hay dos clases de prueba en estas tablas.** Las de antes cuelgan de una
`version_plantilla_prueba`; la de toda vacante nueva, de un `version_banco` con propósito
`PRUEBA_PUESTO` (ver «Banco de preguntas»). `entregable_requerido`, `intento_prueba` y
`respuesta_prueba` apuntan a **una de las dos, nunca a las dos**, y lo exige la base. Así se
reutiliza toda la maquinaria de la rendición —el reloj, las entregas que se reemplazan, el cierre
por plazo y el plazo propio— sin un segundo intento. **El cambio inesperado no se sortea** para
la prueba del editor, y sus preguntas son de la vacante, así que **no pasan por el catálogo
`pregunta_prueba`**.

**En el editor, lo que se guarda es el alcance de cada entregable, no lo que mira cada
criterio** (`V68`). Un criterio mira el archivo de cada una de sus preguntas y los generales que
cubren toda la prueba o alguna de sus preguntas, y eso se calcula al leer: así no hay dos datos
que puedan contradecirse al mover o quitar una pregunta. Las versiones publicadas antes de la
`V68` no tienen alcance y siguen leyendo su «Mira» de `criterio_banco_entregable`, para que sus
notas no cambien.

**`pregunta_prueba` no tiene `organizacion_id`**, y su `codigo` es único en toda la plataforma.
Para que una empresa no lea el examen de otra, desde el 05/10/2026 listarlo, crear preguntas y
elegirlas es solo de la plataforma: a las demás la API les responde 404. No hubo migración.

**`nota_criterio_prueba` guarda solo la parte calificada.** La automática —las cerradas— se
calcula al leer con los puntos que tenga la versión, así que corregir una clave mal puesta mueve
la nota de todos sin reescribir nada. Va por el id del criterio de esa versión, nunca por código
ni por nombre, y guarda aparte la nota de la IA cuando una persona la ajusta y el número de guía
con que se calculó.

**`no_completada` distingue «venció con algo sin responder» de «entregada».** Ese intento se
cierra sin entregar: no se califica, no sale en el ranking y la postulación no cambia de etapa
sola. Guarda en `entregado_en` cuándo se cerró, para que el barrido no lo vuelva a mirar.

**La prueba nueva es cronometrada, y eso está decidido.** Las cinco pruebas que Renaser ha
enviado —en `insumos/pruebas-tecnicas/`— son encargos de varios días sin reloj y sin cambio a
mitad. Son **anteriores**: el cronómetro y el cambio son precisamente la mejora que se quiere.
Sirven como modelo de contenido y de tono, no de formato.

`modalidad` se queda para poder cargar esas cinco y adaptarlas dentro del sistema, no como una
opción que se ofrezca en una vacante nueva.

⚠️ **Ojo con el tamaño del encargo.** Esas cinco piden cosas que no caben en una sesión con
reloj: un producto funcional más un video, o un documento de cinco páginas más un plano. Ponerles
reloj obliga a **encoger el encargo**, no solo a cronometrarlo, y eso lo tiene que reescribir
Renaser.

Antes esta advertencia se apoyaba en que el sistema imponía de 60 a 120 minutos. **Ese rango se
retiró el 31/08/2026** —decisión de Renaser: manda el tiempo que ponga la empresa, y solo queda
un piso de 5 minutos—, así que hoy nada impide publicar una prueba de cuatro horas. **La
advertencia no se va con él**: el problema nunca fue el límite, es que un encargo de varios días
no se convierte en una prueba con reloj alargando el reloj.

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

⚠️ **Nada comprueba que ese rango quepa dentro de la prueba**, ni la base ni el código. Lo hacía
cierto en la práctica el rango de 60 a 120 minutos que se exigía al publicar, y ese rango se
retiró. Con pocos minutos el reloj cierra antes y el cambio no se revela nunca. Defecto abierto:
[Defectos conocidos](DEFECTOS-CONOCIDOS.md).

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
| `parametro` | Los valores sueltos: días sin avanzar antes de cerrar, tope de rondas de evidencia, cupo por defecto, qué datos se ocultan del currículum, si se mandan recordatorios y a qué horas (`V70`) | organizacion_id, codigo, valor, tipo, descripcion, modificado_por_usuario_id |
| `plantilla_correo` | Los textos que se envían, versionados. Un código sin ninguna versión activa ya no se manda (desde la `V58`, `REMUNERACION_ACTUALIZADA`) | organizacion_id, codigo, version, asunto, cuerpo, es_activa |
| `instruccion_ia` | Los textos que se le mandan a cada agente, versionados | agente_codigo, version, texto, publicada_por_usuario_id |

**El nivel salió de la clave de `peso_etapa`.** Los pesos son 40 / 30 / 15 / 15 para todos, y lo
que cambia por vacante es a qué versión apunta. Donde el nivel **sí** sigue mandando es en el
peso de cada criterio y de cada dimensión.

El módulo psicométrico todavía no existe. De la tercera versión de pesos en adelante su 5% está
repartido entre las otras dos partes, y por eso los pesos de los componentes son datos y no
números escritos en el código. ⚠️ **La versión inicial sembrada todavía le da 5, y ninguna cuenta
lee ese peso**: la nota del Perfil Integral se arma solo con el currículum y la evaluación. Está
anotado en [Defectos conocidos](DEFECTOS-CONOCIDOS.md), en «Cinco puntos del Perfil Integral
están reservados a algo que nadie calcula».

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

⚠️ **El modelo por el que se busca el precio es el que el proveedor dice haber usado, no el que
se pidió.** Los dos nombres pueden no ser el mismo —el proveedor puede renombrar su catálogo y
dejar el nombre viejo atendiendo—, y cuando dejan de coincidir el costo sale vacío sin que nada
falle. Pasó entre el 10 y el 17/09/2026: [El modelo cambió de
nombre](EL-MODELO-CAMBIO-DE-NOMBRE.md).

⚠️ **Lo que esta tabla dice que se gastó no es el importe de la factura.** No distingue franja
horaria ni acierto de caché. Los precios sembrados son los de fuera de punta, que es la franja
donde ocurre casi todo el trabajo real, y la entrada es la de consulta nueva, que es la cara. El
número sirve para frenar, no para cobrar; se queda corto en lo que se llame de madrugada.

Sobre ese costo trabaja el **tope mensual por organización** (parámetro `tope_mensual_ia`,
que administra la plataforma): al cruzar el 80% del mes sale un aviso único, y al 100% los
trabajos nuevos nacen `EN_ESPERA` —un estado del encargo, no un fallo— hasta que el tope suba
o empiece el mes. El candidato los ve «en curso», que es la verdad.

Los nueve agentes son: Necesidad de Talento, Cazatalentos, Evidencia de Currículum, Evaluador,
Potencial y Riesgo, Prueba del Puesto, Simulación, Desempeño y Aprendizaje. Cada ejecución guarda
cuál fue, para poder medir por separado si uno se está equivocando.

La `V66` suma el **Recomendador** (`RECOMENDADOR`), que como el Redactor no evalúa a nadie:
propone preguntas para una vacante y una persona decide qué agrega. Y abre en `trabajo_ia` un
tercer modo, **`RECALIFICA`**: volver a calificar las abiertas de las preguntas propias cuando la
empresa corrige su guía. Va por su propio carril a propósito: por la pasada normal, al terminar,
se rehace el retrato y la persona se mueve de etapa; así solo cambian sus notas.

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

### Auditoría, archivos y desempeño · 6 tablas

| Tabla | Para qué existe | Columnas que importan |
|---|---|---|
| `auditoria` | Toda acción que cambia una decisión | organizacion_id, usuario_id, rol_id, accion, entidad, entidad_id, valor_anterior, valor_nuevo, motivo, ocurrida_en |
| `archivo` | Los archivos viven fuera de la base; aquí solo está su ruta | organizacion_id, ruta, nombre_original, tamano, tipo, subido_en |
| `correo_enviado` | A quién, cuándo y **qué decía** | usuario_id, plantilla_correo_codigo, version, asunto, cuerpo, canal, estado_entrega, enviado_en |
| `aviso_portal` | La campana del candidato: lo que pasó mientras no estaba, con leído/no leído | usuario_id, organizacion_id, tipo, titulo, cuerpo, postulacion_id, vacante_id, leido_en, creado_en |
| `seguimiento_desempeno` | El corte de los 30, 90 o 180 días, con su diagnóstico | organizacion_id, postulacion_id, dias, resultado_esperado, porcentaje_logrado, obstaculo, causa, accion, registrado_en |
| `metrica_desempeno` | Cada una de las diez medidas de ese corte | seguimiento_desempeno_id, metrica, valor, origen |

La auditoría **no se puede modificar ni borrar**, y eso no es configurable: no existe la casilla
para permitirlo. También registra los cambios de permisos.

Del correo se guarda el cuerpo ya armado, no solo cuál plantilla se usó. Si mañana alguien edita
la plantilla, lo que se le envió a esa persona sigue siendo lo que dice el registro.

`aviso_portal` (`V56`) nació como **la otra mitad del correo, no su reemplazo**. El correo sale y
no vuelve —cae en promociones, llega a una dirección que el cargador de currículums inventó—, y
hasta la V56 lo que pasaba mientras el candidato no estaba no quedaba en ninguna parte: su lista
de postulaciones se veía igual el día que todo seguía igual y el día que le cambiaron el sueldo.
Guarda el texto ya armado por la misma razón que el correo. **Desde la `V58` es el único canal de
lo que cambia en una vacante**, con dos tipos: `REMUNERACION_ACTUALIZADA` (el sueldo cambiado
desde su tarjeta) y `VACANTE_ACTUALIZADA` (la vacante corregida con el formulario, un solo aviso
por guardado). La `V60` suma `VACANTE_ELIMINADA`: la empresa retiró la vacante y la postulación
quedó cerrada; es el único que **no enlaza** a ninguna parte, y los avisos anteriores de una
vacante eliminada se siguen enseñando, sin enlace. La `V63` suma los cuatro de las reseñas
—`RESENA_PUBLICADA`, `RESENA_EDITADA`, `REPORTE_RESENA_RESUELTO` y `RESPUESTA_RESENA_OCULTADA`—,
que no cuelgan de ninguna postulación y llevan a la sección de reseñas del perfil. La tabla está hecha para los que vengan —«avanzaste de etapa», «tienes una prueba
por rendir», «te queda un día»—, que hoy existen solo como correos que salen y no vuelven.

La base guarda la ruta del archivo, nunca el archivo. Así los entregables pesados —vídeos,
diseños, archivos de hasta 200 MB— no inflan la base de datos. **El almacén es propio de este
sistema**, no el de RENASER OS, y es privado siempre: para abrir un archivo, el backend genera
un enlace firmado que dura poco.

El seguimiento de desempeño **sigue sin alimentarse solo**: la integración con RENASER OS que
traería objetivos, tareas, plazos, retrabajo y resultados no está construida. Hoy los valores los
pone una persona, y `metrica_desempeno.origen` deja dicho si llegó solo o lo puso alguien, para
que el día que se conecte no haga falta cambiar el modelo.

---

### Reseñas de empresas · 3 tablas

Desde la `V63` (28/09/2026). La empresa que contrató a alguien le deja de 1 a 5 estrellas y una
opinión; la persona puede responder, y las dos partes pueden reportar lo de la otra.

| Tabla | Para qué existe | Columnas que importan |
|---|---|---|
| `resena` | La opinión de la empresa sobre quien contrató. Una viva por contratación | postulacion_id, organizacion_id, persona_id, estrellas, texto, escrita_por_usuario_id, publicada_en, editada_en, ocultada_en, nota_ocultacion, borrada_en |
| `respuesta_resena` | La versión de la persona, debajo de la reseña. Una viva por reseña | resena_id, usuario_id, texto, publicada_en, editada_en, editable_hasta, ocultada_en, nota_ocultacion, borrada_en |
| `reporte_resena` | Lo que la persona reporta de una reseña, o la empresa autora de una respuesta, y lo que decidió la plataforma | resena_id, respuesta_id, objeto, reportado_por_usuario_id, organizacion_reportante_id, motivo, comentario, estado, resuelto_por_usuario_id, resuelto_en, nota_revision |

**La reseña cuelga de la postulación y apunta a la persona.** De la postulación, porque se reseña
una contratación y no a alguien en abstracto: dos contrataciones son dos reseñas. De la persona,
porque es su reputación, la misma en todas sus cuentas y en todas las empresas donde postula; es
lo que deja leerlas desde la ficha de otra empresa sin buscar por ids ajenos. La firma es
`organizacion_id`: quién la escribió queda en `escrita_por_usuario_id`, solo para la auditoría.

**Borrar la reseña o la respuesta no borra la fila**: pone `borrada_en`, que deja libre la
contratación —o la reseña— para otra y permite cerrar como «retirado» el reporte que tuviera
pendiente. La unicidad es de las vivas, con índices parciales. **Ocultar tampoco borra**:
`ocultada_en` con su nota, y es definitivo.

`editable_hasta` de la respuesta **se guarda y no se calcula** porque se mueve: cada vez que la
empresa edita una reseña ya respondida, vuelve a 30 días desde esa edición. El plazo de la
reseña sí se calcula —publicación más 30 días— porque editarla no lo alarga.

⚠️ **Ninguna de las tres entra en notas, ranking, pase automático ni IA.** Se leen, y nada más.

---

### Gestión de personas · 6 tablas

Desde la `V64` (29/09/2026). La ficha de quien trabaja en la empresa y el mapa de la empresa:
sedes, áreas, cargos y quién es jefe de quién, todo con su historia.

| Tabla | Para qué existe | Columnas que importan |
|---|---|---|
| `sede` | Dónde se trabaja. No se borra: se desactiva, y quien ya estaba en ella la conserva | organizacion_id, nombre, direccion, provincia_ubigeo, codigo_sunat, es_activa |
| `colaborador` | Quién es: identidad, contacto, domicilio y formación. Una ficha por documento y empresa | organizacion_id, tipo_documento, numero_documento, nombres, apellido_paterno, apellido_materno, fecha_nacimiento, sexo, estado_civil, nacionalidad, celular, correo_personal, correo_corporativo, direccion, provincia_ubigeo, nivel_educativo_codigo, postulacion_id |
| `periodo_laboral` | Cada tramo entre un ingreso y un cese. Un reingreso abre otro | colaborador_id, fecha_ingreso, fecha_cese, motivo_cese, observacion_cese, postulacion_id |
| `situacion_laboral` | Dónde está y en qué condiciones, con vigencia desde-hasta. Cada cambio es una fila | colaborador_id, periodo_id, vigente_desde, vigente_hasta, sede_id, area_id, puesto_id, jefe_colaborador_id, tipo_contrato, fin_contrato, fin_periodo_prueba, regimen_laboral, sueldo_base, moneda, tipo_motivo, anulada_en, antes_* |
| `cese_anulado` | Los ceses que se deshicieron, copiados antes de reabrir el periodo, para que el historial los enseñe tachados | periodo_id, fecha_cese, motivo_cese, anulado_por_usuario_id, motivo_anulacion |
| `contratado_sin_alta` | La contratación que RR.HH. decidió no convertir en ficha. Solo la saca del aviso de pendientes | organizacion_id, postulacion_id, motivo |

**La ficha no depende de `persona`.** La cuenta del candidato vive en la organización plataforma
y se borra si lo pide (Ley 29733); la ficha de quien trabaja se conserva porque la ley laboral
obliga a guardar esos registros (Ley 29733, art. 14.5). Por eso ninguna FK de estas tablas
apunta a `persona`, editar una no cambia la otra, y el borrado del portal no toca la ficha.
**Está pendiente de validar con el abogado**: si dice otra cosa, cambia el alcance del borrado, no
esta estructura. El vínculo con la selección es la postulación, opcional: la del alta en
`colaborador` y la de cada periodo en `periodo_laboral`, porque un reingreso puede venir de otra.

**Identidad, periodo y situación van separados** porque cambian a ritmos distintos. Corregir el
perfil no deja historial —una errata no es un cambio de vida— y queda en la auditoría. La
situación es una **foto completa** con vigencia: un cambio cierra la anterior el día antes y rige
desde su fecha, que puede ser futura (programado). Nada se borra: un cambio ya vigente se corrige
con otro de tipo `CORRECCION`, uno programado se anula con motivo, y al anularlo se copian en sus
columnas `antes_*` (`V65`) los valores de la situación viva que tenía detrás, para que el
historial lo compare con eso aunque después se registren otros. No basta con apuntar a esa fila:
los ajustes de solo sueldo se reescriben cuando un cambio anterior los arrastra.

**Los estados no se guardan**: «Por ingresar», «Activo» y «Cesado» salen de las fechas del
periodo en hora de Lima, y «Cesado» empieza el día siguiente a la fecha de cese.

**Los códigos son los de SUNAT desde el primer día** —tabla 3 para el documento, 12 para el
contrato, 17 para el motivo de cese y 33 para el régimen—, para que Planillas los lea sin migrar
valores. Dos códigos son propios: el contrato `PRACTICAS` (convenio de prácticas) y el motivo de
cese `99` «otro», porque la tabla 17 no tiene un cajón genérico. «No se inició la relación
laboral» sí es de la tabla, con el `17`. En pantalla se enseña el texto, nunca el código.

⚠️ **`sueldo_base` y `antes_sueldo_base` solo los ve quien tiene `ver_sueldos`.** La base guarda la
cifra; que no viaje a nadie más lo hace el servicio.

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
- **Las tres formas de decir el sueldo son coherentes**: sin publicar no hay montos ni moneda;
  fija tiene monto y moneda y ningún máximo; rango tiene los dos montos, con el máximo no menor
  que el mínimo. Una vacante que promete un sueldo que nadie escribió no se puede guardar, ni
  desde el código ni desde una carga masiva.
- **La pretensión de una postulación va entera o no va**: monto, moneda y fecha de declaración,
  los tres o ninguno.
- **Una cuenta tiene como mucho un enlace de contraseña nueva vivo** (`V61`): si dos solicitudes
  llegaran a la vez, la base rechaza la segunda en vez de dejar dos enlaces que sirvan.
- **Una reseña viva por contratación y una respuesta viva por reseña** (`V63`), y un solo
  reporte pendiente de cada una: dos personas de la misma empresa pulsando «Publicar» a la vez no
  dejan dos reseñas. Las estrellas van de 1 a 5, los textos tienen sus largos (30 a 1000 la
  reseña, 30 a 500 la respuesta, hasta 500 el comentario del reporte), «Otro motivo» exige
  comentario, y resolver un reporte exige quién lo resolvió y una nota.
- **Nunca dos fichas del mismo documento en la misma empresa**, y cada postulación contratada da
  como mucho un alta (`V64`): dos altas simultáneas dejan una sola ficha. El cese va con su motivo
  y nunca antes del ingreso; un cambio con motivo «otro», una anulación y un «No dar de alta»
  exigen texto; un sueldo lleva moneda; y nadie es su propio jefe.
- **Una situación con `antes_*` está anulada** (`V65`).
- **Un banco de vacante siempre dice su propósito, y un banco por nivel nunca** (`V66`), y una
  vacante tiene como mucho un borrador y una publicada **de cada propósito**. Las preguntas
  propias se califican siempre por puntos; una pregunta vale de 0 a 100, la nota de una respuesta
  también, y la guía de calificación no pasa de 2000 caracteres. Un banco de vacante que alguien
  cree sin decir su propósito —un guion viejo, una prueba— se toma por cuestionario técnico: lo
  pone un trigger, que es lo que eran todos hasta la `V66`.
- **Una pieza de la prueba cuelga de una plantilla o de la prueba del editor, nunca de las dos**
  (`V67`): `entregable_requerido`, `intento_prueba` y `respuesta_prueba`. La prueba del editor se
  califica siempre por puntos; su enunciado no pasa de 10 000 caracteres, sus minutos son al
  menos 5 y sus días al menos 1; una parte calificada va de 0 a 100 y la califica `IA` o
  `PERSONA`; un intento no completado está cerrado; y un ajuste de la parte calificada lleva
  motivo.
- **El alcance de un entregable es solo del editor y es coherente** (`V68`): el de una pregunta
  lleva su pregunta y los demás no, una pregunta pide como mucho un archivo y un general no cubre
  dos veces la misma. Lo que vale un criterio no es negativo (`V69`).
- **Un recordatorio no sale dos veces** (`V70`): el primero, una vez por turno; el del plazo, una
  vez por turno y por fecha. Reiniciar el servidor o dos sondeos a la vez no lo repiten. Y cómo se
  enteró el candidato de una transición es `CORREO`, `NINGUNO`, `POR_LA_CAMPANA` o nada.

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
- **Que un sueldo sea una cifra creíble.** Entero, mínimo 100, máximo 1 000 000. La base solo
  exige que sea mayor que cero, y con eso pasaban tanto el S/ 3.50 de un «3,500» mal interpretado
  como el sueldo con un dedo de más.
- **Que publicar el sueldo, o no publicarlo, no cambie después de publicar la vacante.** La base
  ve una columna que pasa de un texto a otro; lo que se está revocando —o concediendo tarde— es un
  trato con gente que ya postuló.
- **Que una vacante que esconde su sueldo no vea ninguna pretensión.** Son dos llaves distintas
  —el permiso y lo que publica esta vacante— y ninguna de las dos cabe en una restricción.
- **Los plazos de las reseñas** (`V63`): que solo se reseñe una postulación `CONTRATADO` a partir
  de los 30 días de su paso a ese estado, y que la reseña y la respuesta solo se cambien dentro de
  sus 30 días. Dependen de la hora, que manda el servidor, y de la fecha de una transición.
- **Que solo modere la plataforma.** Además del permiso `moderar_resenas`, el servicio exige ser
  la organización plataforma; es la misma doble llave del alta de empresas.
- **La línea de tiempo de la situación laboral** (`V64`): que los tramos de un periodo no se
  solapen, que un cambio no rija antes del último ni después del cese, que el jefe sea un
  colaborador activo o por ingresar de la misma empresa sin formar un círculo, y que una sede o
  un cargo desactivados no se elijan. Dependen de otras filas y de la fecha de hoy en Lima. Cada
  escritura sobre una ficha bloquea antes su fila, para que dos anulaciones o dos ceses a la vez
  pasen de uno en uno.
- **Que el sueldo no viaje a quien no tiene `ver_sueldos`** (`V64`), ni en la auditoría: es un
  permiso, y cambia sin migrar.
- **Las reglas de las preguntas propias** (`V66`): que una versión publicada sume exactamente 100
  —en borrador se escribe a medias—, que los puntos de las opciones sean enteros —`opcion.puntaje`
  es `numeric` y lo comparte el banco v3—, que las notas de los bancos de siempre sigan en 0–4, y
  que la evaluación del Perfil Integral lleve plantilla salvo cuando sale de las preguntas propias
  —la `V66` quitó ese CHECK de la `V43` porque la base no sabe de dónde sale cada una—.
- **De dónde salen las preguntas de una vacante** (`V66`): que el banco del nivel solo se elija
  si la empresa tiene uno **propio** publicado para ese nivel, y que el origen no cambie desde la
  primera postulación. Dependen de otras filas y del momento.
- **Las reglas de la prueba del editor** (`V67`): que la publicada sume 100 con la parte
  automática y la calificada de cada criterio; que las cerradas no pasen de lo que vale su
  criterio (`V69`); que todo entregable lo mire algún criterio con parte calificada, que un general
  cubra algo y que un criterio de IA no mire solo enlaces (`V68`); que haya fecha límite futura
  para publicar, aunque sea de la vacante (`V68`); que no se entregue sin todo respondido; que la
  vara se congele **en la primera rendición** y no en la primera postulación; y que una vacante no
  entre ni salga de `PRUEBA_PROPIA` por `/instrumento-tecnico`. Dependen de otras filas y del
  momento. **El enunciado ya no se exige** (`V68`).

### Nunca existen, ni siquiera como opción

Seis cosas que no deben aparecer como permiso, porque si aparecen como casilla alguien las va a
marcar algún día:

1. Que un candidato vea a otros candidatos
2. Que las claves de puntuación lleguen al portal del candidato
3. Que se pueda borrar o modificar la auditoría
4. Que se pueda saltar el consentimiento
5. Que la máquina contrate a alguien sin que intervenga una persona
6. Que alguien vea datos de otra organización

⚠️ **La sexta tiene una excepción escrita, y no es una casilla**: las reseñas de empresas
(`V63`). Una empresa lee las que otras dejaron a la persona que se postula a su vacante, y la
plataforma lee las reportadas para moderarlas. Lo decidió el usuario el 28/09/2026; fuera de
eso, la regla sigue igual.

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
6. **Se vacía la pretensión declarada en cada una de sus postulaciones**, y se borra su banda del
   perfil. Es el mismo dato con el mismo tratamiento declarado: dejar viva la cifra exacta en el
   ranking de cada empresa mientras desaparece la del perfil no tiene defensa.
7. **Se borran enteros sus avisos del portal**, no se vacían. A diferencia del correo, que conserva
   su fila porque demuestra que se avisó, un aviso del portal no es prueba de nada frente a nadie.
8. **Se borran enteras sus reseñas de empresas** (`V63`), con sus respuestas y sus reportes. Son
   opiniones sobre una persona concreta y, con su nombre fuera, el texto seguiría señalándola. La
   auditoría conserva que existieron —el texto nunca se copió allí— y una fila con cuántas había.
9. Si era prospecto del Radar, deja de estar activo.
10. **Se conserva todo lo demás**: puntajes, historial de estados, auditoría, métricas. **Y la
    ficha de colaborador, si llegó a trabajar en alguna empresa** (`V64`): no cuelga de
    `persona` y tiene su propia base legal (ver «Gestión de personas»).
11. Sus postulaciones abiertas pasan a cerradas, con motivo «pidió borrar sus datos».

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
- Los **79 permisos** que las migraciones siembran, con su etiqueta y su grupo — contados
  de las migraciones, no de los documentos de diseño. Los más recientes: de la V37,
  `personalizar_instrumentos` para el administrador de cada empresa y
  `administrar_plataforma` solo para el de la plataforma; de la V40,
  `ver_inscritos_simulacion` y `administrar_permisos`; de la V60, `eliminar_vacante`; de la
  V63, `resenar_contratado`, `ver_resenas_candidato` y `moderar_resenas`, este último solo para
  el administrador de la plataforma; y de la V64, `ver_colaboradores`, `editar_colaboradores`,
  `ver_sueldos` —solo Dirección— y `editar_estructura`
- Los **cinco roles** iniciales con sus permisos: candidato, equipo de talento, responsable del
  área, dirección y administrador. Es como arranca el sistema, no cómo queda para siempre
- Las **22 dimensiones**, con cuáles de ellas son obligatorias
- Las **cinco etapas**, los **tres niveles** y las **siete familias**
- El **ubigeo**: los **25 departamentos**, las **196 provincias** y la fila `EXT`, «Fuera del
  Perú». **Los distritos no están sembrados** —serían el nivel 3, y son otras 1874 filas—: el
  árbol los admite el día que hagan falta sin migrar nada
- Los **ocho criterios** del currículum, los **diez** de la simulación y las **nueve métricas**
  de la validación
- Las **preguntas de la prueba**: las previas, las diez universales y las del puesto
- Los **nueve agentes**, con su versión inicial. Las migraciones siguientes suman otros: el
  último, el `RECOMENDADOR` de la `V66`, que nace con su instrucción activa —sin ella el ejecutor
  no llama al modelo—. La misma `V66` publica una instrucción nueva del `EVALUADOR`, que vale para
  sus tres escalas, **solo si nadie había reescrito la suya** desde el panel
- Las **236 preguntas** del banco, como primera versión publicada del banco de la
  plataforma — desde la `V37` no hay filas «globales» sin dueño: compartir es leer las de
  la plataforma
- Una **plantilla de evaluación** por nivel y familia
- Una primera **versión de pesos** con 40 / 30 / 15 / 15
- Una **política de conservación** con su valor por defecto
- Los **parámetros** y las **plantillas de correo**

⚠️ **Las plantillas de prueba NO están en esta lista, y aquí decía que sí.** Ninguna migración
inserta una sola fila en `plantilla_prueba` ni en `version_plantilla_prueba`: una base recién
creada arranca con las dos tablas **vacías**. Este documento se desmentía a sí mismo unas líneas
más abajo —«falta fijar los nombres definitivos de los puestos antes de cargar los datos
iniciales»— y la de abajo era la frase verdadera.

Las pruebas que existen se han cargado **por la API, con los guiones de `scripts/`**, que crean
la plantilla, su versión en borrador, las preguntas, los entregables y la rúbrica, y la publican.
Es a propósito: el contenido de una prueba lo escribe Renaser y sembrar once cáscaras con sus
nombres no habría dejado ninguna que se pueda rendir. Corregido el 01/09/2026.

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

**La integración con RENASER OS.** No está construida ni la usa ningún flujo; la identidad del
equipo es propia desde el 25/08/2026. Si algún día se conecta, falta decidir reintentos y
tiempos de espera.

**Qué pasa con la nota de una respuesta que su dueño borra.** Hoy la pregunta no se puede hacer:
un candidato solo borra mientras el examen está abierto, y ahí todavía no hay nota que borrar.
Pero la nota apunta a la respuesta, así que **el día que se califique con el examen abierto, o
que se pueda reabrir uno entregado, vaciar un recuadro pasa a ser un error** —y el candidato
leería «No se pudo guardar» sin saber por qué—. La salida entonces no es dejar que la nota se
vaya con la respuesta sin más: es decidir antes qué significa una nota puesta sobre algo que ya
no existe.

**La ciudad sobrevive al anonimizado.** El borrado vacía nombre, apellidos, teléfono, documento y
fecha de nacimiento, y deja puesto `persona.ciudad_ubigeo`. Nadie ha decidido todavía si una
provincia —que agrupa a miles de personas y por sí sola no señala a ninguna— debe vaciarse
también. Hasta que se decida, la columna se queda, y conviene saberlo antes de enseñar una tanda
que mezcle anonimizados con quien no lo está.

**La ficha de colaborador ante el borrado de datos.** Que la ficha sobreviva al borrado del portal
(`V64`) descansa en el art. 14.5 de la Ley 29733 y en la obligación laboral de conservar
registros, y **falta que lo valide el abogado**. Si dice otra cosa, cambia el alcance del
borrado, no la estructura.

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
