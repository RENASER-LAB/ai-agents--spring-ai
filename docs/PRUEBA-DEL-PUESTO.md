# La prueba del puesto, por dentro

Cómo se compone, se rinde, se cierra y se califica la etapa técnica. Reúne lo que se decidió
entre el 22/08 y el 02/09/2026 (migraciones V29 a V48). El diseño de la ficha del puesto y del
redactor está en [Diseño de la prueba técnica](DISENO-PRUEBA-TECNICA-FICHA-Y-REDACTOR.md); cómo
se reparten los 100 puntos, en [La rúbrica de la prueba](RUBRICA-DE-LA-PRUEBA.md); la regla de
«una vacante, una versión», en [su decisión](DECISION-UNA-VACANTE-UNA-VERSION.md).

---

## «Prueba técnica» son dos instrumentos

La etapa técnica se rinde con **uno de dos** instrumentos, y la vacante elige cuál (V43):

| Instrumento | Qué es | Quién la escribe |
|---|---|---|
| **Cuestionario técnico** | Preguntas que se contestan escribiendo, sin nada que subir; nace de la ficha del puesto y del redactor (V42). Es la etapa 2 de CAZATALENTOS | El panel, con ayuda de la IA |
| **Prueba del puesto** | Una plantilla con versiones publicadas: preguntas, entregables (archivo o enlace), rúbrica y variantes (el cambio inesperado) | La empresa, desde el panel o por guion |

La prueba de la vacante de Administrador es en realidad un cuestionario cargado como prueba del
puesto: ver «La vacante sin banco» más abajo.

---

## Componer una versión antes de publicarla (V46)

Una versión en borrador solo se podía **añadir**: cada pregunta, entregable, criterio o variante
mal puesto se quedaba ahí, y una rúbrica que se pasaba de 100 no había forma de deshacerla.
Ahora hay **doce endpoints** de corrección y borrado, más dos que no lo son:

| Qué | Ruta |
|---|---|
| Las versiones de una plantilla, borradores incluidos | `GET /plantillas-prueba/{id}/versiones` |
| Subir el ENUNCIADO en PDF o Word | `POST /plantillas-prueba/versiones/{id}/consigna` |
| Reemplazar los datos de la versión | `PUT /versiones/{id}` |
| Quitar una pregunta elegida | `DELETE /versiones/{id}/preguntas/{preguntaId}` |
| Corregir y quitar cada pieza | `PUT` y `DELETE` sobre `/entregables/{id}`, `/rubrica/{id}`, `/variantes/{id}` |
| Reordenar, la lista entera de una vez | `PUT /versiones/{id}/{preguntas,entregables,variantes,rubrica}/orden` |

- ⚠️ **Todo esto es SOLO en `BORRADOR`.** Publicada responde 409 y la salida a un error sigue
  siendo una versión nueva: no hay «despublicar» (RF-138). Lo que cambia es que **antes** de
  publicar ya no hay operaciones irreversibles.
- ⚠️ **Quitar una pregunta borra la ELECCIÓN, no la pregunta.** `pregunta_prueba` es un catálogo
  que varias versiones eligen a la vez, y hay respuestas apuntándola por clave ajena.
- **Estas sí se auditan una por una**, aunque los `agregar*` de al lado no lo hagan: lo que se
  añade se ve en la versión y se puede quitar; lo que se quita no deja rastro en ninguna parte.
- **El enunciado subido no exime de nada**: publicar sigue exigiendo preguntas, entregables y
  rúbrica, porque de ese archivo no sale ninguna nota. Su enlace **caduca a los 180 días** y solo
  se puede volver a subir en borrador: ver [Defectos conocidos](DEFECTOS-CONOCIDOS.md).
- **Una versión sin entregables es un cuestionario**: al publicarla no rige la cuota de 8-10
  universales y 3-5 específicas, basta una pregunta. Con entregables la cuota sigue.
- **Se retiró la validación de 60-120 minutos** al publicar (31/08/2026). Lo decidió Renaser:
  manda el tiempo que ponga la empresa. Queda un **piso de 5 minutos**, el mismo en los dos
  sitios: publicar la versión y fijar los minutos de la vacante.

⚠️ **Fuga conocida y abierta**: el catálogo `pregunta_prueba` se lee **sin filtrar por empresa**
y las preguntas específicas SON el texto del examen. Ver [Defectos conocidos](DEFECTOS-CONOCIDOS.md).

---

## La guía de calificación: la prueba le dice al agente qué mirar (V46)

`instruccion_ia` es **una para todo el mundo** (esa tabla no tiene `organizacion_id`), así que
la instrucción del agente `PRUEBA_PUESTO` solo puede hablar del oficio de calificar. Quien sabe
qué distingue un buen tablero de control de uno malo en el rubro de ESTA empresa es quien
escribió la prueba, y ahora lo puede escribir: `version_plantilla_prueba.guia_calificacion`,
texto libre de hasta 2000 caracteres.

- **Vive en la VERSIÓN**, no en la organización ni en la vacante, por lo mismo que la rúbrica:
  se congela al publicar y viaja con la copia entre empresas.
- ⚠️ **Orienta, no sustituye.** La rúbrica sigue siendo la única fuente de los 100 y el agente
  sigue devolviendo nota **por criterio**, nunca sobre 100. `PuentePruebaIaImpl.guardarNotasPrueba`
  descarta códigos que no estén en la rúbrica, los reservados a una persona y la nota sin
  explicación, y acota el puntaje al máximo. **Esa es la red de verdad**, no el envoltorio del prompt.
- ⚠️ **Va en el `system`, cerrada con un rótulo que lleva una marca sorteada en cada
  calificación**, para que quien escribe la guía no pueda fingir el cierre y colar instrucciones.
  Sustituye al saneado de rayas, que degradaba en vez de borrar. **Y ya no viaja también cruda
  en el insumo**: mandarla dos veces dejaba el envoltorio decorativo. `EjecucionIa.envio` guarda
  el `system` entero, guía incluida.
- **El tope de 2000 está en cuatro sitios y ninguno sobra**: el `@Size` del contrato, el CHECK
  de la V46 (el único en los caminos que no pasan por el DTO), el mensaje del servicio, y el
  recorte al armar el prompt.

---

## Los minutos de la vacante rigen los DOS instrumentos (31/08)

`vacante.minutos_etapa_tecnica` existe desde la V43, pero cuando la vacante rendía la prueba del
puesto **no lo leía nadie**. Ahora lo leen los dos instrumentos.

- ⚠️ **Se resuelven AL EMPEZAR el examen, no al crearlo.** Corregir el número en la ficha
  alcanza a todo el que aún no haya abierto el suyo. Congelarlo al crear dejaba a media tanda
  con el valor viejo sin que nadie pudiera verlo desde el panel.
- ⚠️ **Gana el plazo más cercano, y solo un cronómetro puede acercarlo.** Entre una fecha de
  cierre ya fijada y `ahora + minutos`, manda el que caiga antes. **Los días de un plazo abierto
  NO acercan nada**: la fecha de cierre existe para decir «esta convocatoria cierra el 30» a
  todos a la vez (V32). Fue una regresión, la encontró un QA y está arreglada; el caso vive en
  `RelojDeLaEtapaTecnicaQaTest`. `plazoPropio` tampoco se toca.
- **Con minutos de vacante la prueba es cronometrada de hecho**, diga lo que diga la plantilla, y
  eso es lo que se le enseña al candidato. Viajan a la pantalla **los dos datos** (minutos y
  fecha) siempre que existan los dos.
- **La guarda de «la misma vara para todos» corrió su línea**: de «hay alguna postulación» a
  «alguien ya EMPEZÓ su etapa técnica». Se preguntan **los dos** instrumentos, no el que la
  vacante tenga puesto: una vacante que ya cambió antes puede tener gente que empezó con el otro.
- ⚠️ **`evaluacion.minutos_objetivo` es un vestigio.** Nadie la escribe ni la lee en esta etapa.
  Se conserva por las filas anteriores. **No apoyarse en ella ni como respaldo.**
- ⚠️ Lo que el rango 60-120 sostenía sin decirlo: que el cambio inesperado siempre cupiera
  dentro del reloj. Hoy no lo garantiza nada. Ver [Defectos conocidos](DEFECTOS-CONOCIDOS.md).

---

## Cerrar la convocatoria: dos niveles (V32) y el correo por vacante (V29, V31)

El plazo de la plantilla se cuenta en días y desde que cada uno empieza, así que no servía para
decir «esta convocatoria cierra el domingo».

- **Por vacante**: `POST /panel/vacantes/{id}/cierre-prueba`. Además de fijarla, **mueve los
  intentos ya abiertos**; si no, valdría solo para quien entrara después y la tanda quedaría
  partida en dos.
- **Por candidato**: `POST /panel/postulaciones/{id}/prueba/plazo`, para dar más horas a quien
  las pide. Marca `intento_prueba.plazo_propio`, y **a esos no les afecta mover la fecha de la
  vacante**.
- Empezar la prueba no recalcula una fecha ya puesta.
- **El correo de la prueba** (`PRUEBA_DISPONIBLE`, V29) lleva el PDF del enunciado por vacante,
  el plazo según la modalidad y el WhatsApp como parámetro.
- **Cada vacante puede elegir sus textos de correo** (V31, tabla `plantilla_correo_vacante`):
  «para esta vacante, donde ibas a mandar el aviso X manda la plantilla Y»; sin fila, sale el de
  siempre. Vale para cualquier aviso. `POST /panel/vacantes/{id}/plantillas-correo`.

---

## La vacante sin banco de preguntas (V30)

Renaser pidió que en la vacante de **Administrador** el cuestionario técnico de 20 preguntas
valga por las dos etapas de preguntas: se carga como prueba del puesto, lo califica el agente
`PRUEBA_PUESTO`, y el banco queda apagado **solo en esa vacante**.

- `vacante.aplica_evaluacion`. Apagado, quien postula **no recibe evaluación** y cae directo en
  `PERFIL_POR_CONFIRMAR`: el equipo criba los currículums y confirma el avance, que crea el
  intento y manda el correo con el enlace de acceso.
- `POST /panel/vacantes/{id}/aplicacion-evaluacion` y `POST /panel/vacantes/{id}/version-pesos`
  (los pesos de UNA vacante; no recalcula nada).
- El guion `scripts/cargar-prueba-administrador.py` deja todo montado por la API. Invitar en
  lote: `scripts/invitar.py --a prueba`. Calificar en lote: `scripts/calificar-pruebas.py`.
- **Un cuestionario sin entregables SÍ se puede calificar** (comprobado el 23/08/2026 contra
  DeepSeek): el agente puntuó los siete criterios leyendo solo las respuestas.

- **Desde la V50 (04/09/2026) Administrador y Asistente Administrativo ponderan su Perfil
  Integral.** Su versión de pesos daba el 100 a la prueba del puesto y nada al Perfil Integral,
  así que la prueba RENASER que sí rindieron no contaba. Ahora es **45 el Perfil Integral (la
  prueba RENASER) y 55 la prueba técnica**, el reparto que el cliente escribió para el
  cazatalentos. Se corrigió esa versión en sitio, sin crear otra: nadie había sido contratado
  ni descartado con el reparto viejo, y publicar una versión nueva la convertiría en la que
  hereda toda vacante nueva (ver [Una vacante, una versión](DECISION-UNA-VACANTE-UNA-VERSION.md)).
  Las notas ya calculadas no se recalculan; cambia cómo se combinan de aquí en adelante, y el
  orden del ranking cambia con ello. La migración localiza la versión por lo que la define (la
  que usan esas vacantes y tiene la prueba al 100), nunca por su número: en otra base no toca nada.

El flujo entero está cubierto por `FlujoSinBancoIT`.

---

## Quién califica y cómo

Los dos agentes del hito 3 se **piden a mano** desde el panel, igual que la criba: cada llamada
al modelo cuesta dinero y a quién se califica lo decide quien lleva la vacante.

| Agente | Qué hace | Por dónde se pide |
|---|---|---|
| `PRUEBA_PUESTO` | Puntúa los criterios de la rúbrica **marcados como `AGENTE`**; los de `PERSONA` los sigue poniendo alguien. Al terminar mueve la postulación a `PRUEBA_POR_CONFIRMAR` y, si la rúbrica quedó entera, deja también la nota de la etapa | `POST /panel/postulaciones/{id}/prueba/calificacion-ia` |
| `SIMULACION` | Escribe entre tres y cinco preguntas para la conversación final, sacadas de las contradicciones entre lo que dijo y lo que se le vio hacer. **No puntúa nada** | `POST /panel/postulaciones/{id}/conversacion-final/generar` |

- **Lo que no se puede leer no se puntúa.** Una entrega en video, en imagen o en un enlace no da
  texto, y esos criterios se quedan sin nota para que los mire una persona. Es a propósito: un
  modelo al que se le exige una nota siempre da una nota.
- **Una pregunta ya contestada no se borra.** Volver a pedir las preguntas rehace solo las que
  nadie llegó a hacer.
- **Leer el desglose de la rúbrica pide el permiso de abrir la ficha, no el de corregirla**
  (03/09/2026). `GET /prueba/notas` y el Excel del ranking pedían `ajustar_nota`, que solo
  tienen Talento y Dirección: Responsable de Área veía la nota en el embudo de su vacante y
  recibía 403 al abrir su desglose. Ahora piden `abrir_ficha_candidato`, el mismo con el que ya
  ve lo que el candidato entregó; escribir sigue pidiendo `ajustar_nota`. ⚠️ Los dos sitios
  tienen que pedir el mismo: cuando divergieron, el Excel negaba un detalle que la pantalla sí
  enseñaba.
- **La rúbrica se lee siempre ordenada** (`findByVersionPlantillaPruebaIdOrderByOrden`): acaba
  en una pantalla y en las columnas del ranking, y sin `orden` salía como la devolviera la base.
  La pestaña «Prueba del puesto» del ranking enseña esos criterios por candidato: ver
  [Criba de currículums](CRIBA-DE-CURRICULUMS.md).

### La nota de la etapa se pone en cuanto la rúbrica está entera (28/08)

El agente guardaba la nota de **cada criterio**, y la de la **etapa** (la suma ponderada, la del
ranking) **no se calculaba nunca**. En producción eso dejó **19 pruebas corregidas sin nota de
etapa**, que en el panel se ven igual que las que nadie ha corregido.

Ahora `ponderarSiLaRubricaEstaEntera` suma **solo si todos los criterios tienen puntaje**; si
falta alguno no suma y **nombra cuál** en el registro.

- ⚠️ **Se comprueba antes de sumar, no se intenta y se atrapa.** `calcularNotaEtapa` lanza
  cuando falta un criterio, y esto corre dentro de la transacción que acaba de guardar las notas
  del modelo: dejar salir esa excepción marcaría rollback y **se perderían esas notas**.
- ⚠️ **Un cero es una nota puesta, y una fila de nota sin puntaje cuenta como que falta.**
- ⚠️ **Hay una carrera conocida y se reporta, no se arregla.** El agente y el botón del panel
  (`POST /postulaciones/{id}/prueba/calificacion`) pueden insertar la nota a la vez; uno choca con
  la clave única y su transacción cae. **Se cura sola**: el trabajo se republica y el reintento
  las vuelve a guardar.
- ⚠️ **La rama que suma la guarda `FlujoPruebaIT` (`@Order(8)`), no los unitarios.** Los 39 tests
  del puente nunca preparaban `notasCriterio.findByPostulacionId`, y los cinco unitarios nuevos
  llaman al método por reflexión. **No borrar ese IT pensando que los unitarios lo cubren.**
- Las 19 ya atascadas siguen necesitando que alguien las pondere una vez desde el panel.
- ⚠️ `nota_criterio` es de las tres etapas: sumar sin filtrar por rúbrica dio un 675 sobre 100.
  Hay que delegar en `CalificacionPorCriterio`.

---

## El panel ve lo que el candidato entregó (V48)

`GET /panel/postulaciones/{id}/prueba/entregables`. Hasta el 02/09 los entregables los leían
**dos**: el candidato en su portal y el agente al armar su insumo. Quien tenía que poner a mano
la nota de un criterio de `PERSONA` (justo los que el modelo no puede leer: un video, un enlace)
**no tenía dónde ver el video**.

- **Salen todos los pedidos de su versión, entregados o no**, y de cada uno **la última**: se
  puede entregar tres veces antes de que cierre el plazo. Que falte un obligatorio es justo lo
  que hay que ver antes de poner una nota.
- ⚠️ **Son DOS permisos, no uno.** Listar pide `abrir_ficha_candidato`; el `enlace` y el
  `archivoId` viajan **solo con `descargar_entregables`**. Sin ese permiso se sigue diciendo QUÉ
  entregó y CUÁNDO, y `porQueNoSeVe` explica el hueco con palabras.
- **Con el cuestionario técnico devuelve lista vacía, no 404.**
- **Repite las tres guardas de `PuentePruebaIaImpl.loQueEntrego`** (archivo borrado, sin ruta, o
  que ya no existe). Un archivo que no está se dice; no se ofrece una descarga que va a dar 404.
- `ServicioCalificacionPruebaImpl#pintarEntrega` está en `LLAMADAS_SIN_DUENO_ACORDADAS` de
  `ArquitecturaTest`: el dueño lo fija la cadena, no el `findById`.

### ⚠️ La V48: el archivo del entregable era de la plataforma, no de la empresa

`subirEntregableArchivo` sellaba el archivo con la organización de **quien lo sube**, y quien lo
sube es el candidato: todas las cuentas del portal nacen en la organización plataforma. El panel
lo busca con la organización **de la empresa de la vacante**. Para cualquier empresa que no sea
la plataforma, abrir un entregable respondía **404 sobre un archivo que el candidato sí subió**.
Es la fuga que solo aparece con la segunda empresa: RENASER es a la vez la plataforma y la única
empresa con vacantes, así que los dos caminos coincidían por casualidad. Su test usa DOS
organizaciones distintas a propósito (`ServicioPruebaImplTest`). La migración reetiqueta solo
los archivos que cuelgan de un `entregable`, y solo cuando difieren; no toca currículums ni
materiales de plantillas.

⚠️ **Pendiente: con formato `CUALQUIERA` se quedan dos entregas y solo viaja una.** Quien pega un
enlace y luego sube un archivo deja dos filas del mismo pedido, y solo sale la última.

---

## El ponderado de lo ya rendido, y el semáforo sin pesos (04/09)

**La pestaña «Prueba del puesto» del ranking trae la columna Ponderado** (RF-155): la nota del
Perfil Integral y la de la prueba, cada una por su peso, divididas entre la suma de esos dos
pesos. Con el reparto de la v4 son 70 puntos estirados a 100; en una vacante que siga en la v3
el divisor es otro, porque los pesos se leen de la versión **de la vacante** y no hay ningún 70
escrito en el código. Existe porque hasta que terminan las cuatro etapas no hay ninguna cifra
que compare la tanda, y el equipo decide a quién seguir mucho antes.

- **Sin alguna de las dos notas no hay cifra**, sino un guion que dice cuál falta. Media cuenta
  no es una cuenta.
- El desglose (currículum, Perfil Integral, prueba) vive en el título de la columna. **No hay
  nota del banco de preguntas suelta**: lo guardado es su mezcla con el currículum, y despejarla
  restando da un número falso a quien no tiene evaluación asignada y a las vacantes que califica
  `CalificacionCriterios`.
- Va también al Excel del ranking de esa pestaña, con su desglose. Solo en esa pestaña: en la del
  perfil la mitad de la cuenta no existe para nadie, y en simulación y validación ya hay notas
  posteriores que la cifra ignoraría.
- ⚠️ **No es la Puntuación Global.** No se guarda, no se compara con los umbrales del semáforo y
  no mueve a nadie de estado.
- Se calcula por tanda, con una sola lectura de los pesos, nunca una consulta por candidato.

**Sin pesos de etapa no hay semáforo.** La Puntuación Global de la Decisión suma las etapas
recorriendo los pesos de la versión de la vacante. Las dos versiones del cazatalentos («MICRO» y
«MEDIA/GRANDE», V41) no tenían ningún peso de etapa: con la lista vacía la nota se quedaba en
cero, el código concluía que no faltaba nada y **proponía ROJO a candidatos con todas sus notas**.
Desde el 04/09 esa situación se trata como «no se puede calcular todavía», igual que cuando falta
una nota, y deja un error en el log para quien configure los pesos. La V49 les dio a las dos
versiones el reparto que el cliente escribió: **45 el Perfil Integral (su prueba RENASER) y 55
la prueba del puesto (la técnica)**, con todo el peso del perfil en la evaluación porque en ese
instrumento el currículum no se puntúa.

⚠️ **Las dos siguen en BORRADOR, a propósito.** Publicar cualquier versión hoy la convierte en «la
última publicada», que es la que hereda toda vacante nueva y la que se copia a cada empresa que
se da de alta: el reparto de un instrumento concreto pasaría a regir a todo el mundo. Antes hay
que cambiar ese criterio; está apuntado en [Estado del proyecto](ESTADO-DEL-PROYECTO.md).

---

## Lo que Renaser hace hoy, y lo que nunca ha hecho

El proceso **ya existe y lo llevan a mano**: se está digitalizando un método que usan. Con
**tres excepciones en la prueba del puesto**, que el modelo soporta y la realidad no usa todavía:

| Lo que pide el documento del cliente | Lo que Renaser hace hoy | Estado |
|---|---|---|
| Prueba cronometrada, con reloj en el servidor | Encargo de varios días, sin reloj | **Se hará.** Es la mejora que quieren |
| Un cambio inesperado a mitad de la prueba | No existe | **Se hará** |
| Rúbrica publicada que sume 100 puntos | Una lista de 10 a 12 criterios **sin números** | **Pendiente de Renaser.** Frena el paso 0 |

Las cinco pruebas reales están en `docs/insumos/pruebas-tecnicas/` (ARQ, BIO, CIVIL, CX, SIS).
Son **anteriores**: valen como modelo de contenido y de tono, no de formato. De mirarlas salió
que los entregables sean una tabla y no un texto, y que la versión tenga `modalidad`.

⚠️ **Lo que piden esas cinco no cabe en una sesión con reloj.** Ponerles reloj obliga a
**encoger el encargo**, y eso lo tiene que reescribir Renaser, **y decidir cuántos minutos**
desde que el rango 60-120 se retiró.
