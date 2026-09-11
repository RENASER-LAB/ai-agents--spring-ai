# Estado del proyecto (03/09/2026)

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
| Base de datos | **Flyway es el dueño del esquema**: V1-V53 en `src/main/resources/db/migration` (la V49 y la V50 solo siembran pesos; la V51 trae `lectura_cv_perfil` y las columnas del perfil; la V52 mueve al currículum el peso del perfil integral de Administración; la V53 añade `vacante.calificacion_automatica` y el índice único que impide dos cuestionarios técnicos en la misma postulación). `ddl-auto: validate`. El recuento de tablas está en [Modelo de datos](05-MODELO-DE-DATOS.md) |
| Código | Veintidós paquetes de dominio bajo `ai_engine` más el motor de agentes en `ai/`. Ver [Reglas del código](REGLAS-DEL-CODIGO.md) |
| Tests | **1276 en verde (10/09/2026)** con `./mvnw verify`, contados en una corrida completa: 1132 unitarios y 144 de integración (7 se saltan: los que gastan saldo o mandan correo de verdad). `./mvnw test` solo corre los unitarios, y son los de integración los que ven los efectos a distancia de una migración. Ver [Comprobaciones automáticas](COMPROBACIONES-AUTOMATICAS.md) |
| IA que califica | **Seis de los diez agentes del catálogo están implementados**: datos del currículum, evidencia, evaluador, potencial/riesgo, prueba del puesto y las preguntas de la conversación final. Todos corren por cola de RabbitMQ contra DeepSeek. Ver [Calificación con IA](CALIFICACION-CON-IA.md) |
| Correo | **Ya sabe salir de verdad** (`EnviadorCorreoSmtp`), pero **por defecto no sale** (`renaser.correo.transporte=log`). El RNF pide que salga desde el dominio propio de Renaser: confirmar con ellos antes de encenderlo. Ver [Trabajar en local](TRABAJAR-EN-LOCAL.md) |
| Acceso del candidato | Entra al portal **con un enlace, sin contraseña** (`EnlaceAcceso`). El enlace se manda por correo, así que depende de lo de arriba |
| Identidad del equipo | **Correo y contraseña** (`POST /panel/auth/login`), cuentas solo por invitación; RENASER OS quedó dormido y el `dev-login` está apagado por defecto. Ver [APIs del multiempresa](APIS-MULTIEMPRESA.md) |
| Lo configurable | Plazo de la evaluación (`dias_plazo_evaluacion`, 14), meses de conservación del perfil (`meses_conservar_perfil`, 24), tope mensual de IA por empresa (`tope_mensual_ia`), umbrales de los grupos de prioridad (80 y 65, **sin confirmar por Renaser**) |
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
| ⚠️ **Un texto de consentimiento que nombre a DeepSeek y a Google** | **Usar el sistema con gente real.** Desde el 18/08/2026 el currículum sale hacia DeepSeek (anonimizado) y los textos vigentes no nombran a ningún tercero. Hay un borrador en [BORRADOR-CONSENTIMIENTO-v1.1.md](BORRADOR-CONSENTIMIENTO-v1.1.md). El del perfil del candidato tampoco cubre un perfil que se conserva entre convocatorias |
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
