# Roles y permisos

Sistema de selección de personal — Renaser Consulting
Versión 2.1 · 2026-08-27 · Con el multiempresa y los permisos editables por API

Este documento dice **quién puede hacer qué**, acción por acción.

## El contexto en cuatro líneas

Un candidato postula en el portal de Renaser y atraviesa cinco etapas: se junta su Perfil
Integral —currículum, módulo psicométrico y evaluación, leídos juntos—, hace una prueba del
puesto cronometrada, asiste a una simulación de trabajo y trabaja un periodo de validación
práctica. La inteligencia artificial califica las dos primeras y ordena a todos por prioridad;
las dos últimas y la decisión final siempre son de una persona.

El detalle está en [Requisitos funcionales](01-REQUISITOS-FUNCIONALES.md).

---

## De dónde viene la identidad y de dónde vienen los permisos

Esto es lo primero que hay que entender, porque son dos sistemas distintos.

| | Quién manda |
|---|---|
| **Quién eres**, si trabajas en Renaser o en una empresa cliente | **Este sistema.** Correo y contraseña propios; la cuenta nace solo por invitación (`POST /panel/auth/login`) |
| **Quién eres**, si eres candidato | **Este sistema**, por otra puerta: cuenta propia o enlace de acceso de un solo uso |
| **Qué puedes hacer** dentro del módulo de selección | **Este sistema**, siempre |

Hasta el 25/08/2026 la identidad del equipo iba a venir de RENASER OS; esa integración quedó
**dormida** y hoy no emite ningún token. Cada usuario del equipo conserva el identificador que
tenga en RENASER OS como un dato suelto y **sin clave foránea**, por si algún día se conecta;
ningún flujo real lo llena.

---

## Los cinco roles

| Rol | Quién es | Dónde trabaja |
|---|---|---|
| **Candidato** | Alguien que postula | Portal de Talento |
| **Equipo de Talento** | Quien lleva el proceso día a día | Panel de la empresa |
| **Responsable del área** | El jefe del puesto que se busca | Panel de la empresa |
| **Dirección** | La autoridad de negocio | Panel de la empresa |
| **Administrador** | Quien administra el sistema | Panel de la empresa |

**Dirección decide qué valora Renaser al contratar**: los pesos, el banco de preguntas, las
instrucciones de la IA, las reglas de decisión. **Administrador maneja el sistema**: usuarios,
roles, parámetros y el registro de auditoría. Suelen ser la misma persona al principio, y por
eso están separados: el día que no lo sean, no hay que reprogramar nada.

El **Evaluador de Estándar** no es un rol: es una **función** que alguien asume para una vacante
concreta. Se explica más abajo.

---

## Regla base: los roles no están escritos en piedra

Los nombres y los permisos **se configuran desde el sistema**, no desde el código (ver
«Administración y versiones» en los requisitos funcionales). Esta tabla es cómo arranca el
sistema, no cómo queda para siempre.

El cliente cambia cosas seguido. Por eso el sistema guarda **permisos**, no roles fijos: un rol
es simplemente un conjunto de permisos con nombre.

**Ya se puede hacer desde la API**, con `administrar_permisos`: `GET /api/v1/panel/roles/{id}/permisos`,
`PUT …/{codigo}` y `POST …/{codigo}/revocacion` (ver [09-APIS.md](09-APIS.md)). Cada cambio pide
motivo escrito y deja fila de auditoría. Como los permisos se releen en cada petición, lo que
se cambie ahí vale desde la siguiente llamada de cada afectado: **no hace falta desplegar ni
volver a entrar**.

Lo que **sí** hace falta desplegar es un permiso nuevo: el catálogo solo crece con una
migración, y por eso este documento y la base no dicen el mismo número. Aquí se enumeran **81
permisos**, que es el sistema completo; en la base hay **79 sembrados** —71 hasta la `V40`,
`eliminar_vacante` de la `V60`, los tres de las reseñas de la `V63` y los cuatro de la gestión
de personas de la `V64`— (ver
[07-DICCIONARIO-DE-DATOS.md](07-DICCIONARIO-DE-DATOS.md), que sigue al código). La diferencia
son sobre todo el Radar y las métricas, todavía sin construir. En sentido contrario también
falta algo: cuatro permisos que existen en la base —`ver_banco_preguntas`,
`corregir_contacto_candidato`, `ver_perfil_candidato` y `ver_pretension`— llegaron con un
arreglo o una función concreta y nadie volvió a añadir su fila a estas tablas.

---

## Qué puede hacer cada uno

Leyenda: ● puede · ○ no puede · ◐ solo lo suyo

El ◐ no es siempre el mismo alcance: en la columna del **candidato** es `PROPIO` —lo suyo, en
el portal— y en la del **responsable del área** es `SUS_VACANTES` —lo de las vacantes que
dirige—. No son intercambiables, y la diferencia importa desde que el reparto se edita por
API: mira «Cómo se guarda esto», al final.

### Solicitud de Talento

| Acción | Candidato | Talento | Resp. área | Dirección | Admin |
|---|:--:|:--:|:--:|:--:|:--:|
| Crear una solicitud | ○ | ● | ● | ● | ○ |
| Ver las solicitudes | ○ | ● | ◐ | ● | ○ |
| Marcarla urgente | ○ | ● | ○ | ● | ○ |
| Aceptar una que detectó el sistema | ○ | ● | ○ | ● | ○ |
| Rechazar o archivar una solicitud | ○ | ● | ○ | ● | ○ |

El responsable del área puede pedir gente para **su** área y ve solo sus solicitudes. La
urgencia la fija Talento o Dirección, para que nadie se salte la cola por decisión propia.

### Vacantes

| Acción | Candidato | Talento | Resp. área | Dirección | Admin |
|---|:--:|:--:|:--:|:--:|:--:|
| Ver vacantes publicadas | ● | ● | ● | ● | ● |
| Crear una vacante | ○ | ● | ○ | ● | ○ |
| Editar una vacante | ○ | ● | ○ | ● | ○ |
| Publicarla, sin aprobación | ○ | ● | ○ | ● | ○ |
| Cerrar una vacante | ○ | ● | ○ | ● | ○ |
| Archivar o desarchivar una vacante cerrada | ○ | ● | ○ | ● | ○ |
| Eliminar una vacante | ○ | ● | ○ | ● | ○ |
| Elegir qué prueba y qué plantilla de evaluación se aplican | ○ | ● | ◐ | ● | ○ |
| Definir los requisitos objetivos indispensables | ○ | ● | ◐ | ● | ○ |
| Definir las barreras críticas de la vacante | ○ | ● | ◐ | ● | ○ |
| Elegir qué versión de pesos rige esa vacante | ○ | ○ | ○ | ● | ○ |

El responsable del área opina sobre **sus** vacantes, pero quien fija la configuración es
Talento. La versión de pesos es de Dirección: define qué valora Renaser.

**«Elegir qué prueba y qué plantilla de evaluación se aplican» decide también de dónde salen las
preguntas** (`elegir_plantilla_evaluacion`, `V66`, 30/09/2026): «Preguntas propias de esta
vacante», «El banco de la empresa para su nivel» o «Sin evaluación». **No hay permiso nuevo**, y
es deliberado: es la misma decisión de siempre —qué responde quien postula— con una opción más.
Escribir las preguntas propias es **corregir la vacante**, así que va con `editar_vacante` y su
alcance; verlas, con `ver_vacantes`. El panel no lo sabe por su cuenta: el editor le dice
`puedeEditar`. Ajustar a mano la nota de una abierta, o ponerla si la IA no pudo, es **«Ajustar una
nota de la IA»** (`ajustar_nota`), con su motivo obligatorio de siempre; la ficha se lo dice con
`puedeAjustar`.

**La prueba técnica de una vacante nueva (`V67`, 01/10/2026) sigue el mismo reparto, sin
permiso nuevo.** Armarla, publicarla, copiarla, pedir recomendaciones y fijar su fecha límite
(`V68`) es **corregir la vacante**
(`editar_vacante`, con su alcance); verla, `ver_vacantes`. En la ficha, ver la prueba de una
persona es `abrir_ficha_candidato` —el contenido de sus entregables pide además
`descargar_entregables`— y ajustar o poner la parte calificada de un criterio, `ajustar_nota`.
La lista «No completaron la prueba» va con `ver_embudo`, como el ranking, y cerrar el proceso de
quien no la completó es el descarte de siempre (`mover_postulacion`). Elegir plantilla de prueba
(`elegir_plantilla_prueba`) queda para las vacantes de antes. **El catálogo de preguntas de las
plantillas no basta con el permiso** (05/10/2026): listarlo, crear una pregunta o elegirla para
una versión es solo de la plataforma, y a cualquier otra empresa le responde 404 aunque tenga
`elegir_plantilla_prueba` o `editar_plantillas_prueba`.

**«Editar una vacante» (`editar_vacante`) es el lápiz de la lista y la tarjeta del sueldo.**
Desde el 19/09/2026 **respeta su alcance**: con alcance a sus vacantes, solo se corrigen las que
esa persona dirige; en las demás no sale el lápiz, y pedirlo por la API responde 404, no 403. Sin
el permiso, 403. No hay permiso aparte para el sueldo: quien corrige la vacante corrige lo que
paga.

**«Cerrar una vacante» (`cerrar_vacante`) archiva y desarchiva también** (21/09/2026). **No hay
permiso nuevo**, y es deliberado: archivar es el paso siguiente de cerrar —retirar de la mesa lo
que ya terminó—, no una decisión distinta, y un permiso recién creado habría que repartirlo rol
por rol, así que el día del reparto no lo tendría nadie. Respeta su alcance igual que el lápiz:
fuera de él, **404**; sin el permiso, **403**, y ni el icono de archivo ni «Desarchivar» aparecen
—lo dicen `puedeArchivar` y `puedeDesarchivar` de cada fila, no una lista de roles en el
navegador—.

⚠️ **Consultar «Vacantes archivadas» va con `ver_vacantes`, el permiso de lectura de siempre.**
Quien no puede archivar puede necesitar mirar una convocatoria vieja: el botón «Archivadas (N)»
de la cabecera, su lista y el detalle en lectura no exigen `cerrar_vacante`. Lo que sí lo exige
es la acción. El conteo se saca del mismo universo que la vista —las vacantes de la organización
de quien pregunta, igual que la lista habitual—, para que el botón no prometa siete y la pantalla
enseñe cinco.

**«Eliminar una vacante» (`eliminar_vacante`, grupo `VACANTES`) sí es un permiso nuevo**
(`V60`, 21/09/2026), al revés que el archivo. Archivar es el paso siguiente de cerrar; eliminar
cierra las postulaciones de otras personas y retira la convocatoria de todas las pantallas, y eso
no se sigue de poder cerrarla: si un día se reparte `cerrar_vacante` a un rol nuevo, no se estaría
regalando esto sin saberlo. La `V60` lo da a **Talento y Dirección con alcance `TODO` en todas
las organizaciones que ya existían**; las que se den de alta después lo reciben al copiarse la
matriz de la plataforma. Fuera del alcance, **404**; sin el permiso, **403**, y la papelera no
aparece: lo dice `puedeEliminar` de cada fila, sin endpoint de permisos. Repetirlo sobre una ya
eliminada también responde 404, porque para el panel ya no existe.

### Candidatos

| Acción | Candidato | Talento | Resp. área | Dirección | Admin |
|---|:--:|:--:|:--:|:--:|:--:|
| Ver la lista de candidatos | ○ | ● | ◐ | ● | ○ |
| Abrir la ficha completa | ◐ | ● | ◐ | ● | ○ |
| Ver el currículum sin ocultar datos | ◐ | ● | ◐ | ● | ○ |
| Ver respuesta por respuesta | ○ | ● | ◐ | ● | ○ |

⚠️ **«Ver respuesta por respuesta» tiene por fin un endpoint que la comprueba.**
`GET /panel/postulaciones/{id}/evaluacion`, desde el 25/08, la guarda con
`ver_respuestas_evaluacion`. Ese permiso estaba en el catálogo desde `V12` y **no lo miraba
nadie**: quitárselo a un rol no le quitaba nada. Ahora sí, incluido el alcance —a un
`RESPONSABLE_AREA` acotado a sus vacantes, el desglose de una postulación ajena le sale como
un 404, no como un 403 que confirmaría que existe.
| Ver por qué la IA puso cada nota | ○ | ● | ◐ | ● | ○ |
| **Ver las claves de puntuación** | ○ | ● | ○ | ● | ○ |
| Descargar entregables | ○ | ● | ◐ | ● | ○ |

⚠️ **«Descargar entregables» decide también lo que lleva la columna «CV» del Excel del ranking**
(16/09/2026). Con el permiso, esa columna trae **un enlace que abre el currículum sin pedir
sesión** y que vive unas horas: quien reciba la hoja dentro de ese plazo puede abrirlo aunque no
tenga cuenta —la hoja lo avisa en su pie—. Sin el permiso va solo el nombre del archivo. Es el
mismo permiso que la descarga de siempre, y a propósito: si una de las dos formas de entregar el
mismo currículum no comprobara nada, sería la puerta de atrás. Ver
[Los currículums dejan de vivir en el backend](ARCHIVOS-EN-BUCKET.md).

**Las claves de puntuación las ven el Equipo de Talento y Dirección.** Lo que dice el documento
del cliente es esto, textual:

> *"La clave de puntuación es INTERNA. **El candidato** nunca debe recibir este documento."*

La restricción es **contra el candidato**, no contra el equipo. Talento es quien diseña las
evaluaciones, redacta preguntas nuevas y necesita entender por qué alguien sacó 2 en vez de 4.

El **responsable del área no las ve**: no le hacen falta para decidir una contratación, y
mientras menos gente las conozca, menor el riesgo de que se filtren. El **Administrador tampoco**:
administra el sistema, no evalúa personas.

⚠️ **Lo que sí es absoluto**: las claves nunca viajan al portal del candidato, ni siquiera
escondidas en el código de la página (ver «Seguridad» en los no funcionales). Si viajan al
navegador, cualquiera las lee y el banco entero queda inutilizado.

#### Ver cuánto pide un candidato necesita **dos** llaves

`ver_pretension` —hoy, Dirección— **ya no basta**. Desde el 15/09 la pretensión solo se enseña si
además **esta vacante publica lo que paga**. Es la misma reciprocidad que rige el formulario de
postular, aplicada del otro lado: una vacante que esconde su sueldo no le exige la cifra a nadie,
y tampoco puede leer la que esa persona escribió en su perfil para otra convocatoria.

El agujero que cierra era real y lo ensanchó la propia función: el perfil de la persona es de la
plataforma, no de ninguna empresa, así que la misma vacante que no preguntaba nada **veía** lo que
esa persona había declarado ante otra empresa, la que sí puso su presupuesto sobre la mesa. Cobrar
por un lado lo que no se paga por el otro, dando un rodeo.

Las dos llaves rigen en **los tres sitios** que leen la pretensión: el ranking de la tanda, la
ficha del candidato y el perfil visto desde el panel. Y la segunda llave, igual que el permiso,
**no es «no pintarlo»**: sin ella la consulta ni se lanza, y el dato no llega a existir en la
memoria de la petición.

⚠️ **Al panel le viaja el permiso a secas, no la conjunción de las dos llaves**, y es a propósito:
son dos motivos distintos para una casilla vacía y la pantalla tiene que poder decir cuál es. Con
un solo sí/no para los dos, a Dirección le diría «tu rol no puede verla» —que es falso, y además
la manda a pedir un permiso que ya tiene—.

De regalo, esto vuelve **verdad** el aviso que el panel ya pintaba en esas vacantes: «esta vacante
no publica su remuneración, así que a nadie se le pidió la suya». Hasta ahora esa frase convivía
con una columna que sí traía cifras. Ver [El sueldo, de los dos
lados](EL-SUELDO-DE-LOS-DOS-LADOS.md).

### Reseñas de empresas

Desde el 28/09/2026 (`V63`, RF-171 a RF-179 de los requisitos funcionales).

| Acción | Candidato | Talento | Resp. área | Dirección | Admin |
|---|:--:|:--:|:--:|:--:|:--:|
| Escribir, editar o borrar la reseña de una persona contratada, y reportar la respuesta a esa reseña (`resenar_contratado`) | ○ | ● | ◐ | ● | ○ |
| Ver las reseñas de empresas de un postulante, en su ficha y en la columna de la tabla (`ver_resenas_candidato`) | ○ | ● | ◐ | ● | ○ |
| Leer sus reseñas, responderlas y reportarlas | ◐ | ○ | ○ | ○ | ○ |

La `V63` los concedió **en todas las organizaciones que ya existían**, no solo en la plataforma:
Talento y Dirección con alcance `TODO` y el responsable del área con `SUS_VACANTES`. Las empresas
que se den de alta después los reciben al copiarse la matriz. Una contratación de otra vacante de
su empresa que su alcance no cubre es **404**, con un texto que dice que queda fuera de su
alcance; la de otra empresa, el 404 de siempre, que no confirma nada. Sin el permiso, **403**.

El candidato no tiene casilla: lo decide que la reseña sea **suya**, y la ajena es 404, como el
resto del portal. Revisar lo reportado (`moderar_resenas`) es de la plataforma: ver
«Configuración».

⚠️ **Leer el bloque de la ficha pide cualquiera de los dos permisos, pero los datos de las demás
empresas solo viajan con `ver_resenas_candidato`.** Quien solo puede escribir ve su propio bloque
y nada más. El panel no sabe sus permisos, así que la respuesta lleva `puedeResenar` y
`puedeVerResenas`, y el ranking `puedeVerResenas`: son pistas para pintar, y quien decide es el
backend en cada llamada.

### Evaluación y notas

| Acción | Candidato | Talento | Resp. área | Dirección | Admin |
|---|:--:|:--:|:--:|:--:|:--:|
| Responder su evaluación | ● | ○ | ○ | ○ | ○ |
| Confirmar que un candidato avanza | ○ | ● | ○ | ● | ○ |
| **Confirmar por lote a los no priorizados** | ○ | ● | ○ | ● | ○ |
| Ajustar una nota de la IA | ○ | ● | ○ | ● | ○ |
| Confirmar una barrera crítica | ○ | ● | ○ | ● | ○ |
| Ver las alertas de contradicción | ○ | ● | ◐ | ● | ○ |

Ajustar una nota, confirmar una barrera crítica y confirmar por lote **siempre exigen motivo
escrito**, y quedan registrados con quién y cuándo (ver «Auditoría» en los no funcionales).

Confirmar por lote no borra las razones: **cada candidato conserva la suya**, individual, aunque
se hayan despachado cien de una vez.

### Simulación de trabajo

| Acción | Candidato | Talento | Resp. área | Dirección | Admin |
|---|:--:|:--:|:--:|:--:|:--:|
| Crear sesiones con fecha y cupo | ○ | ● | ○ | ● | ○ |
| Elegir su fecha | ● | ○ | ○ | ○ | ○ |
| **Ver las sesiones y sus fechas, desde el panel** | ○ | ● | ◐ | ● | ○ |
| **Ver quién eligió cada fecha** | ○ | ● | ◐ | ● | ○ |
| Definir la matriz de información crítica | ○ | ● | ◐ | ● | ○ |
| Calificar la simulación | ○ | ● | ◐ | ● | ○ |
| Hacer la conversación final | ○ | ● | ◐ | ● | ○ |
| **Ver las marcas de los eventos observables** | ○ | ● | ◐ | ● | ○ |
| **Marcar los eventos observables, en vivo** | ○ | ● | ○ † | ● | ○ |
| Marcar quién asistió | ○ | ● | ● | ● | ○ |
| Decidir qué hacer con un ausente | ○ | ● | ○ | ● | ○ |

† Las dos filas de las marcas son el mismo permiso, `marcar_eventos_simulacion`, y al
responsable del área **la V18 se lo siembra en `SUS_VACANTES`** —eso es el ◐ de la fila de
arriba—: con él **lee** las marcas de los candidatos de sus vacantes, y una inscripción que no lo
sea responde 404. El ○ de esta fila no sale del reparto de permisos sino de un parámetro:
**marcar** exige además un rol de los que admite `roles_facilitador_simulacion`, que arranca en
`TALENTO, DIRECCION` y se edita desde el panel sin desplegar. Si se añade su rol ahí, marca, y
siempre dentro de sus vacantes. Y si la sesión tiene responsables asignados, solo ellos la
conducen, sea cual sea su rol.

Marcar quién asistió es la única acción de simulación donde el responsable del área tiene
alcance pleno (●) y no «solo lo suyo»: puede estar en la sala sin dirigir esa vacante.

### Validación práctica

| Acción | Candidato | Talento | Resp. área | Dirección | Admin |
|---|:--:|:--:|:--:|:--:|:--:|
| Registrar la figura contractual y habilitarla | ○ | ● | ○ | ● | ○ |
| Iniciar el periodo | ○ | ● | ○ | ● | ○ |
| **Completar las métricas que faltan** | ○ | ● | ● | ● | ○ |
| Cerrar el periodo | ○ | ● | ● | ● | ○ |

Las métricas que RENASER OS ya conoce se alimentan solas y **nadie las carga a mano**. Lo que se
completa es lo que no se puede observar con datos, y de cada valor se ve de dónde salió.

Quién completa es **configurable**: puede ser solo el responsable del área, solo Talento, o
ambos. Arranca con ambos habilitados.

### La decisión final

| Acción | Candidato | Talento | Resp. área | Dirección | Admin |
|---|:--:|:--:|:--:|:--:|:--:|
| Ver el resultado del semáforo | ○ | ● | ◐ | ● | ○ |
| **Tomar la decisión final** | ○ | ○ | ● | ● | ○ |
| Pedir evidencia adicional cuando sale ámbar | ○ | ● | ● | ● | ○ |
| Pasar a un candidato a reserva | ○ | ● | ● | ● | ○ |
| Cambiar una decisión del sistema | ○ | ● | ● | ● | ○ |
| Opinar como Evaluador de Estándar | ○ | ● | ● | ● | ○ |

⚠️ **La decisión de contratar es del responsable del área o de Dirección, no de Talento.**
Talento lleva el proceso, prepara la evidencia y recomienda; pero quien se hace cargo del
resultado de esa persona es quien la va a tener en su equipo.

**Desde el 29/09/2026 (`V64`) se contrata también desde el panel**, con «Contratar» en la ficha
del postulante: es la misma decisión en verde, con los mismos permisos —`decidir_contratacion`
la primera vez, `cambiar_decision` si ya hubo una— y desde cualquier etapa no final. Talento
sigue sin contratar; lo que sí hace es dar de alta al contratado como colaborador (ver
«Personas»).

### Cierre de postulaciones

| Acción | Candidato | Talento | Resp. área | Dirección | Admin |
|---|:--:|:--:|:--:|:--:|:--:|
| Retirar su propia postulación | ● | ○ | ○ | ○ | ○ |
| Retirar su consentimiento de futuros contactos | ● | ○ | ○ | ○ | ○ |
| Cerrar una postulación a mano | ○ | ● | ○ | ● | ○ |
| Reabrir una postulación cerrada | ○ | ● | ○ | ● | ○ |
| Decidir qué pasa con las que quedan a mitad al cerrar la vacante | ○ | ● | ◐ | ● | ○ |
| Pedir borrado de sus datos | ● | ○ | ○ | ○ | ○ |
| Ejecutar el borrado de datos | ○ | ○ | ○ | ● | ● |

**«Cerrar una postulación a mano» ya tiene pantalla (11/09/2026).** Es el botón «Descartar» de
la ficha del panel, y solo lo ve quien puede mover postulaciones —Talento y Dirección, tal como
dice esta tabla—. Pide el motivo escrito y manda a «no continúa».

⚠️ **Tener el permiso no es tenerlo sobre todos.** El alcance se guarda por permiso, así que un
rol puede abrir la ficha de cualquiera y poder mover solo las de sus vacantes. En esa franja el
botón se ve y la acción se rechaza: quien reparte los permisos tiene que mirar los dos alcances
—el de abrir la ficha y el de moverla— y no solo quién tiene cada punto.

### Radar de Talento

| Acción | Candidato | Talento | Resp. área | Dirección | Admin |
|---|:--:|:--:|:--:|:--:|:--:|
| Ver el Radar | ○ | ● | ○ | ● | ○ |
| Añadir un prospecto a mano | ○ | ● | ○ | ● | ○ |
| Registrar un contacto con un prospecto | ○ | ● | ○ | ● | ○ |

### Métricas

| Acción | Candidato | Talento | Resp. área | Dirección | Admin |
|---|:--:|:--:|:--:|:--:|:--:|
| Ver el embudo de sus vacantes | ○ | ● | ◐ | ● | ○ |
| Ver métricas de toda la empresa | ○ | ○ | ○ | ● | ○ |
| Ver predicción contra desempeño real | ○ | ○ | ○ | ● | ○ |
| Ver horas humanas ahorradas y tiempo hasta finalista | ○ | ● | ○ | ● | ○ |
| Exportar datos | ○ | ● | ○ | ● | ● |

### Personas

Desde el 29/09/2026 (`V64`): la ficha de quien trabaja en la empresa, el primer apartado de la
[ampliación de RR.HH.](AMPLIACION-RRHH.md). Grupo `PERSONAS` en la matriz de Configuración.

| Acción | Candidato | Talento | Resp. área | Dirección | Admin |
|---|:--:|:--:|:--:|:--:|:--:|
| Ver los colaboradores y sus fichas (`ver_colaboradores`) | ○ | ● | ○ | ● | ○ |
| Dar de alta, editar, cesar y reingresar colaboradores, y cargarlos por Excel (`editar_colaboradores`) | ○ | ● | ○ | ● | ○ |
| **Ver y editar el sueldo de los colaboradores** (`ver_sueldos`) | ○ | ○ | ○ | ● | ○ |
| Editar las sedes y los cargos (`editar_estructura`) | ○ | ● | ○ | ● | ○ |

La `V64` los concedió con alcance `TODO` **en todas las organizaciones que ya existían**, y las
que se den de alta después los reciben al copiarse la matriz. **En esta versión solo cuenta
`TODO`**: con cualquier otro alcance el usuario no alcanza a ningún colaborador —la lista sale
vacía y una ficha responde 404—, y el menú no le enseña «Colaboradores». Que un RR.HH. alcance
solo su sede o su área, o que un jefe vea a su equipo, queda para después.

⚠️ **Sin `ver_sueldos` el sueldo no existe**, no solo se oculta: la API no lo envía en la lista,
la ficha, el historial, la plantilla ni los errores de la carga, y un ajuste que solo toca el
sueldo no aparece ni se puede anular. La auditoría de un cambio dice qué campos cambiaron, nunca
el importe, porque `ver_auditoria` no es `ver_sueldos`. Que solo lo tenga Dirección está por
confirmar con la clienta (ver «Lo que hay que confirmar con el cliente»).

Leer las sedes y los cargos no pide permiso: Configuración se los enseña a todo el equipo, sin
acciones a quien no tiene `editar_estructura`.

**El menú lateral del panel (`V64`) enseña solo lo que cada uno puede usar**, leyendo los
permisos de `GET /panel/sesion`, y sigue los permisos que ya exigía cada pantalla: «Vacantes»
con `ver_vacantes`, «Simulación» con crear sesiones o ver sus inscritos, «Colaboradores» con
`ver_colaboradores` en `TODO`, y «Configuración» siempre. **El Administrador no ve «Vacantes»**:
no tiene `ver_vacantes` y esas pantallas ya le respondían 403. Si la sesión no carga, el menú
enseña las tres entradas de siempre: Vacantes, Simulación y Configuración. **«Pruebas» ya no sale
para nadie**, RENASER incluida: la sección se retiró el 05/10/2026 y su dirección lleva a la
lista de vacantes.

### Configuración

| Acción | Candidato | Talento | Resp. área | Dirección | Admin |
|---|:--:|:--:|:--:|:--:|:--:|
| Editar el banco de preguntas | ○ | ● | ○ | ● | ○ |
| **Publicar una versión del banco** | ○ | ○ | ○ | ● | ○ |
| Crear o editar plantillas de prueba | ○ | ● | ○ | ● | ○ |
| Crear o editar plantillas de evaluación por nivel y familia | ○ | ● | ○ | ● | ○ |
| Editar textos de correo | ○ | ● | ○ | ● | ○ |
| **Cambiar los pesos y publicar una versión** | ○ | ○ | ○ | ● | ○ |
| **Definir qué familias son afines y la vigencia de cada componente** | ○ | ○ | ○ | ● | ○ |
| **Editar instrucciones de la IA** | ○ | ○ | ○ | ● | ○ |
| **Fijar el periodo de conservación y qué se hace al vencer** | ○ | ○ | ○ | ● | ○ |
| **Decidir si el Evaluador de Estándar puede bloquear** | ○ | ○ | ○ | ● | ○ |
| **Crear usuarios y asignar roles** | ○ | ○ | ○ | ○ | ● |
| **Crear roles nuevos** | ○ | ○ | ○ | ○ | ● |
| **Cambiar qué puede cada rol y con qué alcance** | ○ | ○ | ○ | ○ | ● |
| **Personalizar los instrumentos de evaluación** | ○ | ○ | ○ | ○ | ● |
| **Dar de alta y administrar empresas** | ○ | ○ | ○ | ○ | ● ‡ |
| **Revisar las reseñas y respuestas reportadas** (`moderar_resenas`) | ○ | ○ | ○ | ○ | ● ‡ |
| **Editar parámetros del sistema** | ○ | ○ | ○ | ● | ● |
| **Ver el registro de auditoría** | ○ | ○ | ○ | ● | ● |

‡ Las dos filas de los instrumentos y del alta de empresas llegaron con el multiempresa
(`V37`), y no significan lo mismo. **Personalizar los instrumentos** lo tiene el
Administrador de cualquier empresa: es
adaptar a la suya los pesos y las plantillas de evaluación que la plataforma le prestó. **El
banco de preguntas y las pruebas ya no** (`V66` el banco, `V67` las pruebas): encenderlos o
apagarlos, lo pida la empresa o la plataforma, responde 400 «Esta personalización ya no existe»,
porque cada empresa escribe sus preguntas y su prueba técnica en cada vacante. Quien los copió
antes conserva lo suyo en sus vacantes de antes. **Dar de
alta empresas** lo tiene **solo el Administrador de la empresa dueña de la plataforma** —hoy,
Renaser—: es la operación de Renaser como dueña del producto, no una función del panel de un
cliente. El alta de una empresa copia los roles de la plataforma y ese permiso lo excluye a
propósito, o cada cliente nuevo nacería pudiendo dar de alta a los demás.

**Revisar las reseñas reportadas** (`moderar_resenas`, `V63`) sigue la misma regla: lo tiene
**solo el Administrador de la plataforma** y **el alta de empresas no lo copia**. Tiene además
una segunda llave en el servicio: hay que ser la plataforma. Una empresa que se lo concediera a
sí misma en «Permisos» seguiría recibiendo **403**, porque moderar es juzgar lo que escribieron
otras empresas.

La división es simple: **Talento prepara, Dirección aprueba, Administrador administra.**

Talento escribe preguntas, arma pruebas y ajusta los textos de correo — es su trabajo diario.
Pero **publicar una versión** es de Dirección, porque a partir de ese momento todos los
candidatos se evalúan con ella.

Igual con los pesos, la vigencia de los componentes y las instrucciones de la IA: definen **qué
valora Renaser al contratar**. Eso es decisión de negocio, no configuración operativa.

Trabajar en un borrador sí puede hacerlo Talento cuantas veces quiera. Lo que no puede es
ponerlo en producción solo.

---

## Qué significa "solo lo suyo" (◐)

**Candidato**: solo sus propias postulaciones, sus respuestas y su currículum. Nunca ve a otros
candidatos ni sabe cuántos hay.

**Responsable del área**: solo las solicitudes, vacantes y candidatos de **su** área. No ve las
de otras áreas.

Esto no es una decisión de diseño, es un requisito de seguridad (ver «Seguridad» en los no
funcionales): el backend lo verifica en cada llamada, no basta con esconder botones.

---

## El aislamiento entre empresas

Encima de todos los permisos hay una regla que **no se puede desactivar**: nadie ve datos de una
empresa que no sea la suya.

Se escribió como regla de seguridad desde la primera versión, cuando la única empresa era
Renaser, precisamente para no tener que añadirla el día que llegara el primer cliente. **Ese
día ya llegó** (`V37`, 25/08/2026): cada empresa crea sus vacantes y ve solo a sus candidatos,
y Renaser es además la **dueña de la plataforma** —la que da de alta a las demás y les presta
los instrumentos—. La regla no aparece como casilla en ningún rol: no se concede ni se revoca.

En la base la tabla sigue llamándose `organizacion`, así que en el código y en el diccionario
de datos se lee esa palabra; en el producto y en las pantallas, «empresa». Es lo mismo.

⚠️ **Un alcance no sustituye al aislamiento.** `TODO` significa «todo lo de mi empresa», nunca
«todo». Un permiso concedido en `TODO` a un rol de ACME no le enseña ni una fila de Renaser, y
por eso el candado del último `administrar_permisos` cuenta dentro de cada empresa: si contara
en toda la base, la primera en quedarse sin él dependería de que otra lo conservara.

⚠️ **Una excepción escrita: las reseñas de empresas** (`V63`, 28/09/2026). La empresa B lee las
reseñas que la empresa A dejó a la persona que A contrató, con el nombre de A, cuando esa persona
postula a una vacante de B. Entra **por la persona de una postulación que B ya puede ver**, nunca
por un id suelto, y no trae nada más de aquel proceso: ni notas, ni decisión, ni alertas. La
plataforma, al moderar, lee también las reportadas de todas las empresas. Lo decidió el usuario y
está escrito en el
[diseño del candidato ante varias empresas](superpowers/specs/2026-08-25-el-candidato-ante-varias-empresas-design.md).

---

## El Evaluador de Estándar

Antes se llamaba *Bar Raiser*. **No es un rol, es una función.** Se asigna por vacante: alguien
que trabaja en Renaser pero **no en el área que contrata**, para que no lo presione la urgencia
de llenar el puesto.

Existe para una sola cosa: **que la urgencia no baje el nivel de contratación.**

| Qué hace | Qué NO hace |
|---|---|
| Revisa el expediente completo | No decide la contratación |
| Deja su opinión registrada | No aprueba por urgencia |
| Señala qué falta validar | — |

**Su poder es configurable.** Puede quedarse en recomendación registrada, o poder bloquear una
contratación hasta que se resuelva lo que señaló. **Arranca en recomendación**, que es lo que
Renaser pide para la primera versión interna.

**Regla al asignarlo:** el sistema no permite nombrar Evaluador de Estándar al responsable del
área que contrata. Perdería todo el sentido.

---

## Una persona, varios roles

En una empresa del tamaño de Renaser es normal que alguien tenga más de un rol. Quien está en
Dirección puede ser también el responsable del área que contrata.

El sistema lo permite. Reglas:

1. Si una persona tiene dos roles, **puede hacer lo que le permita cualquiera** de los dos.
2. Pero **no puede ser Evaluador de Estándar de una vacante donde es el responsable del área**.
3. Al registrar una acción se guarda **con qué rol la hizo**, para que la auditoría tenga
   sentido.

---

## Cómo se guarda esto

**Un rol es un conjunto de permisos con nombre.** No está escrito en el código.

```
PERMISO           un permiso suelto
                  ej: "puede_cerrar_vacante"

ROL               un nombre + una lista de permisos, con alcance
                  ej: "Equipo de Talento" = [crear_vacante,
                                             cerrar_vacante,
                                             confirmar_avance, ...]

USUARIO           una persona + uno o varios roles
                  + su identificador en RENASER OS, si es del equipo
```

El alcance tiene tres valores —**propio**, **sus vacantes** y **todo**— y va en la relación entre
el rol y el permiso, no en el permiso. Es el mismo permiso «ver candidatos» el que tiene el
responsable del área y el que tiene Talento: lo que cambia es hasta dónde llega.

Convertirlo en un simple sí o no le abriría al responsable del área los datos de todos los
candidatos de la empresa.

**Qué alcanza cada uno**, que es lo que hay que saber antes de tocar la matriz de un rol:

| Alcance | Hasta dónde llega |
|---|---|
| **todo** (`TODO`) | Todas las filas **de su empresa**. Nunca de otra: el aislamiento no es un alcance, va por encima |
| **sus vacantes** (`SUS_VACANTES`) | Solo lo que cuelga de una vacante donde esa persona es la responsable. La comparación es contra `vacante.responsable_usuario_id` |
| **propio** (`PROPIO`) | Solo lo que es de quien mira. Es el alcance **del portal**: sus postulaciones, su ficha, su currículum |

⚠️ **`PROPIO` en un permiso que se acota por vacante no alcanza ninguna fila**, y eso es a
propósito. En el panel nada es de quien mira —son candidatos, y `/panel/**` exige un token de
equipo—, así que la lista sale vacía y abrir un `{id}` responde 404. Poner `PROPIO` en
`ver_candidatos` no acota el permiso: lo apaga.

Dos cosas del panel no se acotan por vacante y llevan su propia regla. La **solicitud de
talento** sí tiene dueño dentro del equipo: ahí «lo suyo» son las que esa persona pidió, el
vínculo es `solicitud_talento.responsable_usuario_id`, y con `PROPIO` ve las suyas. Y la
**sesión de simulación** sirve a varias vacantes a la vez, así que «es tuya» es que toque
alguna vacante suya; con `PROPIO` tampoco ve ninguna. Además hay permisos cuyo alcance el
servicio pide solo para que quien no lo tenga reciba un 403, y luego no filtra por él: ahí
cambiar el alcance no cambia nada. **Que un alcance esté sembrado no quiere decir que se
aplique** — esta matriz dice lo que se pretende, y el servicio dice lo que ocurre.

Hasta la V40 esto era una discusión de laboratorio, porque el reparto solo se tocaba entrando
a la base. Desde que `administrar_permisos` edita `rol_permiso` por API, **un solo `PUT` deja
puesto cualquiera de los tres**, sin desplegar. Por eso el código no pregunta «¿es sus
vacantes?» sino que trata los tres casos, y en un único sitio: `AlcanceSobreLaVacante`.

**El backend verifica el permiso en cada llamada** (ver «Seguridad» en los no funcionales).
Ocultar un botón no es seguridad: si el permiso no se valida en el servidor, cualquiera puede
llamar a la API directamente.

---

## El panel de roles

El Administrador maneja los roles desde una pantalla: crea roles, les marca permisos y se los
asigna a las personas. Sin esa pantalla, cada cambio que pida el cliente obliga a programar.

Tres cosas pueden arruinar este panel. Las tres son fallos conocidos de este tipo de pantalla y
hay que evitarlas desde el diseño.

### 1 · Que el último administrador se deje fuera

Alguien desmarca por error el permiso de administrar roles, guarda, y ya nadie puede volver a
entrar a esa pantalla. **El sistema queda bloqueado** y solo se arregla tocando la base de datos
a mano.

**Cómo se evita:**
- Nadie **puede editar su propio rol**. Si quiere cambiarlo, lo hace otra persona.
- El sistema **impide guardar** si el cambio deja a cero las personas con permiso de administrar
  roles.
- El mensaje debe decir por qué: *"no puedes guardar: nadie quedaría con permiso para
  administrar roles"*.

**De los tres, la API trae hoy el segundo**, y contando roles en vez de personas: no deja
revocar el último rol de la organización que conserva `administrar_permisos`. Que nadie pueda
editar su propio rol **todavía no está puesto**, así que de momento es una regla escrita y no
una que el servidor haga cumplir.

### 2 · Que la pantalla se vuelva ilegible

Son **81 permisos**. Con una casilla por cada uno y sin agrupar, nadie entiende qué está
marcando, y la pantalla se usa mal o se deja de usar.

**Cómo se evita:**
- Agrupar los permisos por área: solicitudes, vacantes, candidatos, evaluación, sesiones,
  validación, decisión, radar, métricas, configuración y, desde la `V64`, personas.
- Escribirlos en lenguaje normal —*"cerrar una vacante"*— y nunca con nombres técnicos como
  `vacancy.close`.
- Un interruptor por grupo para marcar o desmarcar todo el bloque.

```
  ROL · Equipo de Talento              57 de 81 permisos

  Vacantes                                    [8/9] v
     [x] Ver vacantes
     [x] Crear una vacante
     [x] Cerrar una vacante
     [ ] Elegir la version de pesos
     ...

  Configuracion                               [4/17] >
  Candidatos                                  [7/7]  >

                            [ Ver el sistema como este rol ]
```

Los denominadores de este boceto son los del **catálogo diseñado**, que es lo que enumera este
documento. La pantalla de verdad los sacará de la tabla `permiso`, así que dirá otros números:
en `Configuracion`, 15 casillas y no 17 —de las diseñadas faltan por construir la vigencia de
los componentes, el periodo de conservación y el poder de bloqueo del Evaluador de Estándar, y
en cambio hay una que esta tabla no enumera, `ver_banco_preguntas`—. No es un descuadre del
boceto: es la misma diferencia entre lo diseñado y lo construido que explica el principio del
documento.

**Vista previa.** El botón *"ver el sistema como este rol"* muestra qué pantallas y botones
tendría esa persona. Sin eso se marcan casillas a ciegas y no se sabe qué cambió hasta que
alguien se queja.

### 3 · Que un cambio rompa el trabajo de alguien que está en medio

A alguien de Talento le quitan el permiso de calificar mientras tiene una pantalla abierta.
Cuando guarda, le falla sin explicación.

**Cómo se evita:**
- Cuando el backend rechaza algo por permisos, el mensaje lo dice claro: *"ya no tienes permiso
  para calificar. Tus cambios no se guardaron"*.
- Nunca un error genérico ni una pantalla en blanco.
- El trabajo sin guardar **no se pierde de la pantalla**: la persona puede copiarlo antes de
  salir.

### Lo que nunca debe ser configurable

Hay cosas que no son permisos, son reglas del sistema. **Si aparecen como casillas, alguien las
va a marcar algún día.** Mejor que no existan como opción:

- Que un candidato vea a otros candidatos
- Que las claves de puntuación lleguen al portal del candidato
- Que se pueda borrar o modificar el registro de auditoría
- Que se pueda saltar el consentimiento de datos
- Que la inteligencia artificial decida una contratación sin una persona
- Que alguien vea datos de otra organización

### Todo cambio de permisos queda registrado

Quién lo hizo, cuándo, qué permiso, sobre qué rol y cuál era el valor anterior (ver «Auditoría»
en los no funcionales). Es el tipo de cambio que después nadie recuerda haber hecho.

---

## Lo que hay que confirmar con el cliente

⚠️ **Los documentos de Renaser no definen roles ni permisos acción por acción.** El documento
nuevo solo dice que hacen falta capacidades equivalentes a Candidato, Equipo de Talento,
Responsable del Área, Dirección y Administrador, y que no se duplique el sistema de RENASER OS.
Lo demás se dedujo del trabajo que hace cada uno.

Conviene validar cinco cosas:

| Qué decidí | Por qué | Si me equivoqué |
|---|---|---|
| Talento **no** decide la contratación | Responde por esa persona quien la tendrá en su equipo | Se le marca ese permiso |
| El responsable del área **no** ve las claves | No le hacen falta; menos gente que las conozca, menor riesgo | Se le marca ese permiso |
| Publicar el banco y cambiar pesos es de **Dirección** | Definen qué valora Renaser al contratar | Se le marcan a Talento |
| Dirección y Administrador son **dos roles**, no uno | Uno decide qué se valora, el otro maneja el sistema | Se fusionan marcando los permisos de uno en el otro |
| Solo **Dirección** ve y edita los sueldos de los colaboradores (`V64`) | El sueldo es un dato sensible (Ley 29733) y la pretensión salarial ya era solo suya | Se marca `ver_sueldos` a quien toque |

En los cinco casos el arreglo es marcar una casilla. Ninguno obliga a tocar código.

---

# Documentos relacionados

| Documento | Qué contiene |
|---|---|
| [Qué hace el sistema](00-QUE-HACE-EL-SISTEMA.md) | El sistema entero, sin nada técnico. **Empieza por aquí** |
| [Requisitos funcionales](01-REQUISITOS-FUNCIONALES.md) | Qué hace el sistema, etapa por etapa |
| [Requisitos no funcionales](02-REQUISITOS-NO-FUNCIONALES.md) | Tecnología, seguridad, rendimiento |
| [Estados de la postulación](03-ESTADOS-POSTULACION.md) | Los 18 estados y cómo se pasa de uno a otro |
| [Modelo de datos](05-MODELO-DE-DATOS.md) | Las tablas por área y por qué existe cada una |
| [Etapas y pesos](diagramas/embudo-seleccion.html) | Los cien puntos repartidos, en un dibujo |
| [Alcance del MVP](08-ALCANCE-DEL-MVP.md) | Qué se construye primero, en qué orden y por qué |
