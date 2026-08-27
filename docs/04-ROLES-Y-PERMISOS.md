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
| **Quién eres**, si trabajas en Renaser | **RENASER OS.** Emite el token; este sistema solo lo valida. No guardamos tu contraseña |
| **Quién eres**, si eres candidato | **Este sistema.** Los candidatos no son usuarios de RENASER OS |
| **Qué puedes hacer** dentro del módulo de selección | **Este sistema**, siempre |

La razón de que los permisos sean nuestros es simple: RENASER OS no sabe qué es «publicar una
versión del banco de preguntas» ni «confirmar una barrera crítica». Son acciones que solo
existen aquí.

Cada usuario del equipo guarda el identificador que tiene en RENASER OS, como un dato suelto y
**sin clave foránea**, porque son dos servicios separados que hablan por HTTP y no comparten
base de datos.

---

## Los cinco roles

| Rol | Quién es | Dónde trabaja |
|---|---|---|
| **Candidato** | Alguien que postula | Portal de Talento |
| **Equipo de Talento** | Quien lleva el proceso día a día | Panel dentro de RENASER OS |
| **Responsable del área** | El jefe del puesto que se busca | Panel dentro de RENASER OS |
| **Dirección** | La autoridad de negocio | Panel dentro de RENASER OS |
| **Administrador** | Quien administra el sistema | Panel dentro de RENASER OS |

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
migración, y por eso este documento y la base no dicen el mismo número. Aquí se enumeran **77
permisos**, que es el sistema completo; en la base hay **71 sembrados** (ver
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
| Elegir qué prueba y qué plantilla de evaluación se aplican | ○ | ● | ◐ | ● | ○ |
| Definir los requisitos objetivos indispensables | ○ | ● | ◐ | ● | ○ |
| Definir las barreras críticas de la vacante | ○ | ● | ◐ | ● | ○ |
| Elegir qué versión de pesos rige esa vacante | ○ | ○ | ○ | ● | ○ |

El responsable del área opina sobre **sus** vacantes, pero quien fija la configuración es
Talento. La versión de pesos es de Dirección: define qué valora Renaser.

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
| **Editar parámetros del sistema** | ○ | ○ | ○ | ● | ● |
| **Ver el registro de auditoría** | ○ | ○ | ○ | ● | ● |

‡ Las dos filas de los instrumentos y del alta de empresas llegaron con el multiempresa
(`V37`), y no significan lo mismo. **Personalizar los instrumentos** lo tiene el
Administrador de cualquier empresa: es
adaptar a la suya el banco, las plantillas y los pesos que la plataforma le prestó. **Dar de
alta empresas** lo tiene **solo el Administrador de la empresa dueña de la plataforma** —hoy,
Renaser—: es la operación de Renaser como dueña del producto, no una función del panel de un
cliente. El alta de una empresa copia los roles de la plataforma y ese permiso lo excluye a
propósito, o cada cliente nuevo nacería pudiendo dar de alta a los demás.

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

Son **77 permisos**. Con una casilla por cada uno y sin agrupar, nadie entiende qué está
marcando, y la pantalla se usa mal o se deja de usar.

**Cómo se evita:**
- Agrupar los permisos por área: solicitudes, vacantes, candidatos, evaluación, sesiones,
  validación, decisión, radar, métricas y configuración.
- Escribirlos en lenguaje normal —*"cerrar una vacante"*— y nunca con nombres técnicos como
  `vacancy.close`.
- Un interruptor por grupo para marcar o desmarcar todo el bloque.

```
  ROL · Equipo de Talento              54 de 77 permisos

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

Conviene validar cuatro cosas:

| Qué decidí | Por qué | Si me equivoqué |
|---|---|---|
| Talento **no** decide la contratación | Responde por esa persona quien la tendrá en su equipo | Se le marca ese permiso |
| El responsable del área **no** ve las claves | No le hacen falta; menos gente que las conozca, menor riesgo | Se le marca ese permiso |
| Publicar el banco y cambiar pesos es de **Dirección** | Definen qué valora Renaser al contratar | Se le marcan a Talento |
| Dirección y Administrador son **dos roles**, no uno | Uno decide qué se valora, el otro maneja el sistema | Se fusionan marcando los permisos de uno en el otro |

En los cuatro casos el arreglo es marcar una casilla. Ninguno obliga a tocar código.

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
