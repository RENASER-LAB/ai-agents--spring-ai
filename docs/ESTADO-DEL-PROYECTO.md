# Estado del proyecto (26/09/2026)

Qué está construido, qué falta y qué está pendiente del cliente. **Este documento describe el
presente**: cuando algo deja de ser cierto se borra, no se acumula. Lo que se hizo un día
concreto está en los documentos de avance (`AVANCE-*`, `REPORTE-*`).

---

## Dónde estamos

**El embudo está completo de punta a punta**, sin ningún salto manual obligado: postulación →
Perfil Integral → prueba del puesto → simulación → validación → decisión.

| Hito | Estado |
|---|---|
| **1** · Solicitud, vacante, postulación, estados, permisos | Completo |
| **2** · Perfil Integral (currículum, evaluación, Perfil de Talento) | Completo, **con IA corriendo de verdad** |
| **3** · Prueba del puesto y decisión | Completo, **con IA corriendo de verdad** |
| **Simulación y validación** (estaban fuera del MVP) | Construidas |
| **Multiempresa** (plataforma tipo Indeed, piezas A-F) | Completo (26/08/2026) |
| **Perfil del candidato** (único por persona) | Completo (25/08/2026); desde el 06/09 guarda foto, portada, currículum propio y diplomas (V51), y **nada de eso llega al panel**. Sus RF esperan la validación de Renaser |
| **Bancos CAZATALENTOS** (V41) y **ficha del puesto con redactor** (V42) | Construidos |

| | |
|---|---|
| Base de datos | **Flyway es el dueño del esquema**: V1-V62 en `src/main/resources/db/migration` (la V49 y la V50 solo siembran pesos; la V51 trae `lectura_cv_perfil` y las columnas del perfil; la V52 mueve al currículum el peso del perfil integral de Administración; la V53 añade `vacante.calificacion_automatica` y el índice único que impide dos cuestionarios técnicos en la misma postulación; la V55 pone el sueldo en la vacante y la pretensión en la postulación; la V56 trae `aviso_portal`; la V57 no crea tablas, solo siembra los precios de los dos modelos de DeepSeek; la V58 tampoco: suma el aviso `VACANTE_ACTUALIZADA` y apaga el correo del cambio de sueldo sin borrarlo; la V59 añade `vacante.archivada_en`; la V60, el borrado lógico de la vacante —`vacante.eliminada_en`, el motivo de cierre y el aviso `VACANTE_ELIMINADA`, y el permiso `eliminar_vacante`—; la V61, la tabla `recuperacion_clave` de «¿Olvidaste tu contraseña?», con sus tres parámetros y sus dos correos; la V62, `vacante.ciudad_ubigeo`, la ciudad de la vacante sacada del catálogo `ubigeo`). `ddl-auto: validate`. El recuento de tablas está en [Modelo de datos](05-MODELO-DE-DATOS.md) |
| Código | Veintidós paquetes de dominio bajo `ai_engine` más el motor de agentes en `ai/`. Ver [Reglas del código](REGLAS-DEL-CODIGO.md) |
| Tests | **1276 en verde (10/09/2026)** con `./mvnw verify`, contados en una corrida completa: 1132 unitarios y 144 de integración (7 se saltan: los que gastan saldo o mandan correo de verdad). `./mvnw test` solo corre los unitarios, y son los de integración los que ven los efectos a distancia de una migración. La última entrega por el harness, el Excel de la prueba con la prueba vigente (26/09), pasó en verde su batería: **1469 unitarios y 221 de integración**. Esa batería elige las clases por nombre y aparta las que gastan saldo o mandan correo, así que **no es el mismo recuento** que el completo del 10/09 y las dos cifras no se comparan. Ver [Comprobaciones automáticas](COMPROBACIONES-AUTOMATICAS.md) |
| IA que califica | **Seis de los diez agentes del catálogo están implementados**: datos del currículum, evidencia, evaluador, potencial/riesgo, prueba del puesto y las preguntas de la conversación final. Todos corren por cola de RabbitMQ contra DeepSeek. Ver [Calificación con IA](CALIFICACION-CON-IA.md) |
| Modelos de DeepSeek | `deepseek-flash` (DeepSeek-V4.1-Flash) para calificar y razonar, `deepseek-v4-pro` solo para el orquestador, y `deepseek-chat` para la primera pasada de la criba. **El tercero es un nombre retirado que sigue atendiendo como alias**, y hoy es la única forma de pedir el modelo sin razonar: es deuda tomada a sabiendas. Ver [El modelo cambió de nombre](EL-MODELO-CAMBIO-DE-NOMBRE.md) |
| Lo que cuesta la IA | Se anota por llamada con el precio vigente del modelo, y sobre eso trabaja el tope mensual por empresa. **El importe que se muestra no es el de la factura**: los precios sembrados son los de fuera de hora punta, que es la franja donde ocurre casi todo el trabajo. Hoy no frena a nadie, porque ninguna empresa tiene tope puesto |
| Correo | **Ya sabe salir de verdad** (`EnviadorCorreoSmtp`), pero **por defecto no sale** (`renaser.correo.transporte=log`). El RNF pide que salga desde el dominio propio de Renaser: confirmar con ellos antes de encenderlo. ⚠️ **«¿Olvidaste tu contraseña?» no sirve sin esto**: con `log` la pantalla dice que el enlace salió y nadie lo recibe. En producción `CORREO_TRANSPORTE` tiene que ser `smtp`, y el envío real de ese enlace **no se ha probado todavía**. Ver [Trabajar en local](TRABAJAR-EN-LOCAL.md) |
| Acceso del candidato | Entra al portal **con un enlace, sin contraseña** (`EnlaceAcceso`). El enlace se manda por correo, así que depende de lo de arriba. Quien creó su cuenta entra con correo y contraseña y, **si la olvida, pide un enlace para elegir otra** (22/09/2026); las cuentas de carga masiva no pueden, porque su correo es inventado. Ver [APIs](09-APIS.md), «Cómo entrar» |
| Identidad del equipo | **Correo y contraseña** (`POST /panel/auth/login`), cuentas solo por invitación; quien la olvida pide un enlace por correo, uno por cada empresa en la que tenga cuenta. RENASER OS quedó dormido y el `dev-login` está apagado por defecto. Ver [APIs del multiempresa](APIS-MULTIEMPRESA.md) |
| Lo configurable | Plazo de la evaluación (`dias_plazo_evaluacion`, 14), meses de conservación del perfil (`meses_conservar_perfil`, 24), tope mensual de IA por empresa (`tope_mensual_ia`), umbrales de los grupos de prioridad (80 y 65, **sin confirmar por Renaser**), y de la contraseña olvidada: cuánto vale el enlace (`minutos_vida_recuperacion`, 60 — la pantalla lo dice escrito a mano), enlaces por cuenta y hora (`max_recuperaciones_por_hora`, 3) y solicitudes por IP y hora (`max_recuperaciones_por_ip_hora`, 30, contadas en memoria: se reinician al arrancar) |
| Frontend | **Uno, con dos caras**: `RenaserOsPostulantes` (portal del candidato y panel de la empresa). Ver [Trabajar en local](TRABAJAR-EN-LOCAL.md) |
| Despliegue | GitHub Actions → imagen en ECR → EC2, con la cola en la misma máquina. Ver [CI/CD](CI-CD.md) |
| Mockups | Los mantiene otra persona y **describen la versión anterior** |

---

## Lo que falta de código

- De los diez agentes del catálogo quedan **cuatro sin clase**, y ninguno es del MVP:
  `NECESIDAD_TALENTO`, `CAZATALENTOS`, `DESEMPENO` y `APRENDIZAJE`.
- **`alineacion` sale vacía siempre** en el desglose de la evaluación: la tabla
  `resultado_alineacion` existe, el panel la lee y **ningún agente la escribe todavía**. Vacía
  significa «no calculado», no «todo en verde».
- **El motor de puntuación del banco v3** no está completo: los ítems `V` no se puntúan, en los
  `CD` solo se mira que el campo venga lleno, faltan los multiplicadores por familia, los
  umbrales de nivel, los cinco filtros eliminatorios y las banderas; `detectarContradicciones`
  sigue con la regla del v0.1. Ver [Banco v3](AVANCE-BANCO-V3-2026-08-19.md).
- **El detalle de las respuestas tiene que pasar a tabla**: hoy es `respuesta.detalle` (`jsonb`,
  V21) y la base no comprueba nada; lo único entre una respuesta mal formada y una nota es
  `ValidadorDetalleV3`. Deuda tomada a sabiendas.
- **Una ruta que no existe devuelve 500, no 404.** Lo contesta el `@ExceptionHandler` genérico
  del motor de agentes. Importa porque el backend en IP pública lo escanean bots todo el rato y
  un 500 de verdad se pierde entre el ruido. Cómo se arregla: un
  `@ExceptionHandler(NoResourceFoundException.class)` en `ManejadorErrores` que devuelva 404.
- **Publicar una versión de pesos la convierte en la que hereda toda vacante nueva y toda empresa
  nueva** («la última publicada»). Por eso las dos versiones del cazatalentos siguen en BORRADOR
  con sus pesos ya puestos (V49) y la de Administración se corrigió en sitio (V50). Hay que
  cambiar ese criterio antes de publicar ninguna. Ver [Prueba del puesto](PRUEBA-DEL-PUESTO.md).
- Lo que está roto, se sabe y sigue abierto está en [Defectos conocidos](DEFECTOS-CONOCIDOS.md).
  **Mirarlo antes de invitar a una tanda de candidatos.**
- **Hay una auditoría de código muerto y sin usar pendiente de decisión** (03/09/2026, puesta al
  día el 05 y el 06/09 con los commits hasta el #66 y el #37): `AUDITORIA-CODIGO-2026-09-03.xlsx`,
  con una fila por pieza (backend y frontend), qué pasa en palabras del cliente, y una columna
  DECISIÓN para elegir qué se borra, qué se conecta a una pantalla y qué se queda. Quedan 232
  filas vigentes (20 salieron del código del 04/09 y 34 del perfil del 05-06/09); 4 las resolvió
  el panel el 04/09 (#33) y 7 se corrigieron en la propia rama de la auditoría el 06/09 (backend
  y portal): borrar el perfil ya suelta su foto, portada, currículum y diplomas del almacén (lo
  cubre `FlujoPerfilIT` contra Postgres real); un currículum del que no salió nada se cierra como
  «no legible» aunque el perfil tuviera datos; la foto y la portada ya no se ven rotas al volver a
  «Mi perfil» y cerrar sesión vacía la caché de la pestaña; el diploma respeta los topes del
  servidor (imagen 2 MB, PDF 10 MB); y el código muerto de reordenar se borró. **Queda pendiente y
  pide migración**: guardar en `lectura_cv_perfil` el resultado crudo de la lectura para no pagar
  dos veces el mismo currículum al pasar del perfil a una postulación. Siguen abiertos, a la espera
  de la columna DECISIÓN: el CV del candidato no se puede abrir desde su ficha del panel; el Excel
  de la prueba escribe «falta una nota de etapa» donde lo que falta es la nota del currículum o el
  reparto de pesos; y el párrafo que explica la columna Ponderado se cuela como nombre de la
  columna en el menú «Columnas».

---

## Lo que falta y no es código

Son decisiones de Renaser que los documentos difieren a propósito, no trabajo olvidado:

| Qué falta | Qué bloquea |
|---|---|
| ⚠️ **Que un abogado firme los textos de consentimiento** | **Usar el sistema con gente real.** El 14/09/2026 (`V54`) se cargaron textos que sí nombran a los cinco proveedores de fuera, dicen que los datos salen del Perú, declaran el plazo y enumeran lo que pasa sin que intervenga una persona. **Ya no falta información: falta la firma.** Lo primero que hay que poner delante del abogado es **que en la pantalla de postular se retiró la casilla** (15/09): enviar la candidatura es ahora el acto, y eso es otra figura legal, aunque la constancia se siga guardando igual. Después, decidir qué se hace con quien creó su cuenta antes — la V54 no toca las aceptaciones ya firmadas y no hay mecanismo de re-aceptación, así que esa gente sigue amparada por un texto que no nombra a nadie. Ver [BORRADOR-CONSENTIMIENTO-v1.1.md](BORRADOR-CONSENTIMIENTO-v1.1.md) |
| **El contenido real de una prueba del puesto** | Renaser tiene que reescribir una pensada para rendirse de una sentada, **y decidir cuántos minutos**, desde que el rango 60-120 se retiró (31/08/2026). Ver [Prueba del puesto](PRUEBA-DEL-PUESTO.md) |
| Una **rúbrica que sume 100** | Hoy tienen una lista de 10 a 12 criterios sin números. Frena el paso 0 |
| Un **facilitador designado** para la simulación | La persona que observa la sesión presencial y marca los eventos. El sistema ya sabe qué *roles* pueden hacerlo, y es cambiable desde el panel |
| La **matriz de información crítica** de una sesión real | La mecánica está construida; el contenido lo escribe Renaser |
| La **figura contractual** de la validación productiva | Hoy hay un placeholder: `CONTRATO_TEMPORAL_EN_REVISION` |
| Los **umbrales de los grupos de prioridad** (80 y 65) | Son parámetro editable y Renaser no los ha confirmado |
| Cuatro ítems `EF-4` del banco v3 (`D51`, `D60`, `C40`, `O37`) sin enunciado propio | Llevan la instrucción del formato; si Renaser quiere una pregunta propia, se cambia desde el panel |
| Validar los RF del perfil del candidato | Los RF-155..170 no entran en el documento de requisitos hasta que Renaser los valide |
| Medir cómo lo hacen hoy (horas por vacante, postulaciones, tasa de finalización) y currículums ya corregidos a mano | No bloquean programar: bloquean **saber si el MVP funcionó** |

---

## Convocatorias en marcha

⚠️ Antes de tocar nada de una vacante, mirar en qué estado está la gente. La vacante de
**Administrador** (sin banco, cuestionario de 20 preguntas como prueba) salió el 23/08/2026 con
93 invitaciones; en septiembre se están cargando las pruebas de **Marketing digital** y
**Talento y selección** con los guiones sin versionar de `scripts/`. Las notas ya calculadas no
se recalculan nunca hacia atrás.

⚠️ **Tras desplegar la V62, hay vacantes publicadas sin ciudad.** La migración solo la puso donde
la ubicación era exactamente el nombre de una provincia. Las demás —por ejemplo las dos de «Selva
Alegre», probablemente Arequipa— salen en el portal como «Sin indicar» hasta que el equipo les
elija ciudad desde el panel. **Cada una de esas correcciones avisa en la campana a quien siga en
carrera en esa vacante.** Además, «Desarrollador web», «Líder de operaciones» e «Ingeniero/a de
Infraestructura» parecen de prueba: habría que archivarlas para que no salgan en la búsqueda.

---

## Historial

| Fecha | Qué pasó | Dónde se cuenta |
|---|---|---|
| 14/08 | Auditoría inicial y documento nuevo del cliente | [AUDITORIA_2026-08-14](AUDITORIA_2026-08-14.md), [Qué cambia con el documento nuevo](insumos/CAMBIOS-DEL-DOCUMENTO-NUEVO.md) |
| 17/08 | Hitos 2 y 3 | [AVANCE-HITO2](AVANCE-HITO2-2026-08-17.md), [AVANCE-HITO3](AVANCE-HITO3-2026-08-17.md) |
| 18/08 | Simulación y validación; la criba; la IA corre de verdad; siete fallos de la auditoría | [AVANCE-SIMULACION-VALIDACION](AVANCE-SIMULACION-VALIDACION-2026-08-18.md), [Criba](CRIBA-DE-CURRICULUMS.md), [Fallos corregidos](FALLOS-CORREGIDOS-AUDITORIA.md) |
| 19/08 | Banco v3 y la tubería CI/CD | [AVANCE-BANCO-V3](AVANCE-BANCO-V3-2026-08-19.md), [CI-CD](CI-CD.md) |
| 22-23/08 | La vacante sin banco; los archivos al bucket; el importador del banco; la cola en la EC2 | [Prueba del puesto](PRUEBA-DEL-PUESTO.md), [Archivos en bucket](ARCHIVOS-EN-BUCKET.md), [AVANCE-IMPORTADOR-BANCO](AVANCE-IMPORTADOR-BANCO-2026-08-23.md) |
| 25-26/08 | El perfil del candidato; el multiempresa; el ranking por etapa | [Propuesta del perfil](PROPUESTA-PERFIL-DEL-CANDIDATO.md), [APIs del multiempresa](APIS-MULTIEMPRESA.md), [AVANCE-2026-08-26](AVANCE-2026-08-26.md) |
| 27-28/08 | Inscritos y permisos editables (V40); la nota de la prueba; bancos CAZATALENTOS | [REPORTE-TRABAJO-2026-08-27](REPORTE-TRABAJO-2026-08-27.md), [REPORTE-CAMBIOS-2026-08-28](REPORTE-CAMBIOS-2026-08-28.md), [CAZATALENTOS](CAZATALENTOS-BANCO-RENASER.md) |
| 31/08 | Composición de la versión (V46), guía de calificación, minutos de la vacante, áreas desde el panel | [REPORTE-WORKTREES-2026-08-31](REPORTE-WORKTREES-2026-08-31.md), [Prueba del puesto](PRUEBA-DEL-PUESTO.md) |
| 01-02/09 | La ciudad del candidato (V47); el panel ve los entregables (V48) | [REPORTE-CAMBIOS-2026-09-01](REPORTE-CAMBIOS-2026-09-01.md), [Prueba del puesto](PRUEBA-DEL-PUESTO.md) |
| 03/09 | El ranking de la prueba enseña los criterios de su rúbrica; leer el desglose pide el permiso de la ficha (#62). Auditoría de código muerto y reescritura de CLAUDE.MD y docs. En el panel, la ficha enseña lo que el candidato entregó | [REPORTE-CAMBIOS-2026-09-03](REPORTE-CAMBIOS-2026-09-03.md), [Criba](CRIBA-DE-CURRICULUMS.md), [Prueba del puesto](PRUEBA-DEL-PUESTO.md), `AUDITORIA-CODIGO-2026-09-03.xlsx` |
| 04/09 | El ranking de la prueba trae el ponderado de lo ya rendido (#63); sin pesos de etapa no hay semáforo, y el cazatalentos recibe los suyos (V49, #64); Administración pondera su perfil 45/55 (V50, #65). En el panel: la ficha del Perfil Integral dice qué marcó la IA (#33, #35) y la tabla de la prueba trae la columna Ponderado (#34, #36). Auditoría de RNF y agentes | [REPORTE-CAMBIOS-2026-09-04](REPORTE-CAMBIOS-2026-09-04.md), [REPORTE-COMPLETO-CAMBIOS-2026-09-04](REPORTE-COMPLETO-CAMBIOS-2026-09-04.md), [Prueba del puesto](PRUEBA-DEL-PUESTO.md), [AUDITORIA-RNF-Y-AGENTES-2026-09-04](AUDITORIA-RNF-Y-AGENTES-2026-09-04.md) |
| 05/09 | La auditoría de código y los docs, puestos al día con los commits del 04/09 | `AUDITORIA-CODIGO-2026-09-03.xlsx` |
| 06/09 | El perfil guarda foto, portada, currículum y diplomas y lee el currículum al subirlo (V51, #66); postular reutiliza el del perfil; `GET /portal/auth/sesion`. El portal estrena «Mi perfil» al estilo portal de empleo (#37). La auditoría y los docs, al día con los dos; se quitaron las menciones a un desarrollador que dejó el proyecto; y en la rama de la auditoría se corrigieron siete fallas del perfil (borrado de archivos, lectura vacía, foto rota al volver, tope del diploma, código muerto de reordenar) | [APIs del perfil](APIS-PERFIL-DEL-CANDIDATO.md), [Propuesta del perfil](PROPUESTA-PERFIL-DEL-CANDIDATO.md), `AUDITORIA-CODIGO-2026-09-03.xlsx` |
| 11/09 | El panel ya puede **descartar a un candidato**: desde su ficha, y **a la tanda marcada de una vez** desde la mesa de la tabla, con una ventana que escribe los nombres antes de confirmar. Motivo escrito obligatorio, y una casilla para **descartar sin avisar** cuando ya se habló con esa persona —queda escrito que no se le avisó, en el historial y en la auditoría—. El verbo existía desde el principio y ninguna pantalla lo llamaba; del backend solo cambia que `MaquinaEstados.transicionar` gana una sobrecarga con `avisar` (la firma de siempre delega avisando, así que sus 46 llamadores no se tocan) y que la ficha y el ranking dicen si quien pregunta puede mover postulaciones. De paso se arregló el `Modal` compartido, que reenfocaba en cada render y dejaba escribir una sola letra en cualquiera de sus campos | [Estados de la postulación](03-ESTADOS-POSTULACION.md), [APIs](09-APIS.md) |
| 14-15/09 | **El sueldo se dice de los dos lados** (V55): la vacante declara OCULTA, FIJA o RANGO, y si lo publica, quien postula está obligado a declarar su pretensión; si lo esconde, no se le pide nada **y la empresa tampoco ve ninguna** —ni la del perfil—. Publicar el sueldo o no **se congela al publicar la vacante**; el monto sí se cambia, con motivo, y le llega por correo y por la campana a cada candidato vivo (el correo se retiró el 19/09). Los sueldos van en cifras enteras (100 a 1 000 000) y `compensacion_publica` queda retirada. **El portal estrena campana** (V56, `aviso_portal`), y el borrado de la ley 29733 se lleva la pretensión declarada y los avisos | [El sueldo, de los dos lados](EL-SUELDO-DE-LOS-DOS-LADOS.md), [APIs](09-APIS.md), [Defectos conocidos](DEFECTOS-CONOCIDOS.md) |
| 16/09 | **El Excel del ranking pasa a una sola hoja, «Datos»**, con el formato corto que pidió el cliente: una fila por candidato, **una columna por cada criterio** de la rúbrica, el resumen de la IA y todas las explicaciones juntas en la última columna. Se va la hoja «Detalle» —cada criterio es ahora una columna— y se van del archivo Pretensión, Veredicto, Estado, Ciudad y las cifras del retrato, que siguen en la tabla del panel. ⚠️ La columna «CV» estrena un **enlace firmado que abre el currículum sin pedir sesión** durante ocho horas (`horas-enlace-volcado`, y la hoja dice el plazo en su pie); pide `descargar_entregables` y sin él va solo el nombre del archivo. Sin migraciones ni cambios en la base. De paso se anotó un defecto que no se tocó: el peso del psicométrico no lo lee ningún cálculo | [APIs](09-APIS.md), [Archivos en bucket](ARCHIVOS-EN-BUCKET.md), [Criba](CRIBA-DE-CURRICULUMS.md), [Defectos conocidos](DEFECTOS-CONOCIDOS.md) |
| 17/09 | **El modelo cambió de nombre y el gasto dejó de contarse** (V57): DeepSeek retiró V4 Flash el 10/09, publicó V4.1 Flash con el nombre `deepseek-flash` y **dejó los nombres viejos atendiendo como alias**, así que nada falló y nadie se enteró. Pero el gasto se anota con el nombre que el proveedor RESPONDE, y ese nombre no tenía precio: durante una semana **todas las calificaciones y todas las lecturas de currículum se anotaron sin costo**, el gasto del mes dejó de sumar y el tope mensual quedó ciego. La V57 siembra su precio y el del modelo del orquestador, que **no lo tuvo nunca** desde que existe el control del gasto. La primera pasada de la criba sigue pidiendo un nombre retirado a propósito, y los precios sembrados son los de fuera de hora punta, que es la franja donde ocurre casi todo el trabajo | [El modelo cambió de nombre](EL-MODELO-CAMBIO-DE-NOMBRE.md), [Modelo de datos](05-MODELO-DE-DATOS.md), [Comprobaciones automáticas](COMPROBACIONES-AUTOMATICAS.md) |
| 19-20/09 | **Una vacante se corrige desde la lista del panel** (V58), con el mismo formulario del alta: un lápiz en cada fila en borrador o publicada. Si está publicada y cambió algo que ve el candidato, **cada postulante en carrera recibe un solo aviso en la campana**, con lo que cambió; lo interno se guarda sin avisar, y cada campo cambiado queda en la auditoría. **El sueldo deja de avisar por correo**, desde el formulario y desde su tarjeta: queda solo la campana, y el texto de correo del sueldo se apaga sin borrarse. «En carrera» se decide en un solo sitio para las entregas siguientes (archivar y eliminar). La edición respeta ahora el alcance del rol | [Requisitos](01-REQUISITOS-FUNCIONALES.md) (RF-14b, RF-159), [APIs](09-APIS.md), [El sueldo, de los dos lados](EL-SUELDO-DE-LOS-DOS-LADOS.md), [Modelo de datos](05-MODELO-DE-DATOS.md) |
| 21-22/09 | **Una vacante que no debió existir se elimina** (V60), por borrado lógico y con motivo obligatorio: una papelera en cada fila, con permiso propio (`eliminar_vacante`) para Talento y Dirección. Cierra a quienes siguen en carrera —como decisión de una persona, sin correo— y les deja un aviso en la campana sin enlace; devuelve su solicitud a `ABIERTA` para crear la vacante correcta, y la retira del panel, del portal, de los rankings, de las exportaciones, de la simulación y de los procesos automáticos. No se borra ninguna fila ni hay vuelta desde el panel. Heredó un defecto del cierre manual: la plaza de simulación ya reservada no se suelta | [Requisitos](01-REQUISITOS-FUNCIONALES.md) (RF-14d), [APIs](09-APIS.md), [Roles y permisos](04-ROLES-Y-PERMISOS.md), [Modelo de datos](05-MODELO-DE-DATOS.md), [Defectos conocidos](DEFECTOS-CONOCIDOS.md) |
| 22-23/09 | **«¿Olvidaste tu contraseña?» funciona de verdad** (V61), en el portal y en el panel: se pide un enlace con el correo, llega por correo, vale 60 minutos y una sola vez, y con él se elige una contraseña nueva; después se vuelve a entrar, sin sesión abierta. La respuesta es la misma exista o no la cuenta, y llega al instante porque el correo sale después (en vez del señuelo de tiempo del login). Topes de 3 enlaces por cuenta y hora y 30 por IP y hora, este en memoria. La contraseña nueva admite como mucho 72 bytes, el límite de BCrypt. Quedan fuera las cuentas de carga masiva, y quedan anotados dos defectos vecinos: crear cuenta y aceptar invitación no comprueban esos 72 bytes, y un texto de consentimiento puede decir «S.A.C..». **Falta probar el envío con SMTP real.** De paso se reforzó la espera de una prueba de integración de la calificación con IA (`FlujoCalificacionIaIT`) que fallaba a ratos | [APIs](09-APIS.md), [Modelo de datos](05-MODELO-DE-DATOS.md), [Diccionario de datos](07-DICCIONARIO-DE-DATOS.md), [Defectos conocidos](DEFECTOS-CONOCIDOS.md) |
| 22-23/09 | **El ranking se filtra por fecha de postulación y se decide en lote sin bajar al final.** Del backend solo cambia que cada fila del ranking dice cuándo se postuló (`postuladoEn`), sin migración. En el panel, un botón «Filtros» reúne fecha, calificación con IA, ciudad, nota y pretensión, con una etiqueta por cada filtro puesto; los filtros se conservan al cambiar de etapa; «marcar todo» marca solo lo que se ve; y avanzar o descartar están en una barra pegada abajo. El Excel sigue bajando lo que se ve. QA dejó anotado un defecto que ya venía de antes: la ficha le dice a Dirección que su rol no puede ver la pretensión cuando lo que pasa es que la vacante no publica sueldo | [APIs](09-APIS.md), [Criba](CRIBA-DE-CURRICULUMS.md), [Defectos conocidos](DEFECTOS-CONOCIDOS.md) |
| 25/09 | **Quien busca trabajo puede buscar vacantes** en una pantalla nueva del portal, `/vacantes`: escribiendo lo que busca, y filtrando por ciudad, modalidad, fecha de publicación y empresa. Para que la ciudad se pueda filtrar, **la vacante tiene ciudad del catálogo** (V62): la ubicación escrita a mano pasa a ser «Zona o referencia», y la migración rescató la ciudad solo donde ese texto era exactamente una provincia. El portal recibe la ciudad y la fecha de publicación de cada vacante; el panel elige la modalidad y la ciudad de un desplegable, y un código fuera del catálogo es un 400. Cambiar la ciudad avisa a quien sigue en carrera como cualquier otro dato visible. De paso, `scripts/cargar-convocatoria.py` vuelve a funcionar —se cortaba desde el PR #80 porque no mandaba el puesto— y `convocatorias.json` lleva modalidad y ciudad. Queda para el equipo poner ciudad a las vacantes que la migración no rescató (ver «Convocatorias en marcha») | [APIs](09-APIS.md), [Modelo de datos](05-MODELO-DE-DATOS.md), [Diccionario de datos](07-DICCIONARIO-DE-DATOS.md), [Requisitos](01-REQUISITOS-FUNCIONALES.md) (RF-12, RF-14b, RF-21), [Criba](CRIBA-DE-CURRICULUMS.md) |
| 25/09 | **Se retiró `scripts/sembrar-escenario-e2e.py`.** Las pruebas de extremo a extremo del frontend ahora ponen en el navegador los datos de «Desarrollador web» que ese guion escribía en la base, y así son más rápidas. Nada cambia en el backend ni en la base. Esas pruebas se recortaron de 329 a 258 (302 con las de la búsqueda de vacantes) y ya no salen con fallos de fondo: la última corrida dio 294 pasan y 0 fallan, así que ese defecto salió de [Defectos conocidos](DEFECTOS-CONOCIDOS.md) | `~/Documentos/RenaserOsPostulantes/docs/SUITE-E2E-CLASIFICACION-2026-09-25.md`, [Trabajar en local](TRABAJAR-EN-LOCAL.md) |
| 26/09 | **El Excel de la prueba del puesto saca solo las columnas de la prueba que la vacante tiene puesta hoy.** La vacante 13 cambió de prueba cuando ya había gente dentro, y su Excel arrastraba las 4 columnas de la demo, vacías; ahora trae solo las 7 de Administrador, salen aunque nadie tenga nota, y las notas de quien rindió la demo siguen en «Justificación detallada». Sin prueba puesta o con el cuestionario técnico no hay columnas de criterio. **En los dos Excel del ranking la fila de cabeceras se abre con altura para leerse entera**, calculada con los rótulos de cada archivo y con un tope de 8 líneas. Sin migración y sin tocar datos; descargar no escribe nada. **La tabla del panel no cambió**: en la vacante 13 sigue enseñando las columnas de la demo, así que pantalla y hoja ya no coinciden | [APIs](09-APIS.md), [Prueba del puesto](PRUEBA-DEL-PUESTO.md), [Criba](CRIBA-DE-CURRICULUMS.md), [Defectos conocidos](DEFECTOS-CONOCIDOS.md) |
