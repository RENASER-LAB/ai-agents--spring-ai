# Cada vacante con sus etapas
Estado: TERMINADO

Escrita el 25/11/2026 contra `origin/main`: backend `8b9fa20` (#147, migraciones hasta la V89) y
frontend `315e1c4` (#111). Nada de esta spec está implementado.

## Objetivo

Hoy toda vacante recorre las mismas cinco etapas en el mismo orden: Perfil Integral, Prueba del
puesto, Simulación, Validación y Decisión. Con esta spec, cada vacante tiene **su propio
recorrido**. Talento enciende, apaga y reordena las etapas al configurarla. Así, una vacante que
necesita ir rápido puede quedarse en Perfil Integral → Decisión, y otra puede poner la Simulación
antes de la Prueba.

La segunda mitad del objetivo es para desarrollo: **dejar lista la base para que una etapa nueva
se programe una vez** y aparezca sola como opción en todas las vacantes, sin tocar el resto del
sistema.

Es para el equipo de Talento, que configura las vacantes, y para el candidato, que ve solo las
etapas de la vacante a la que postuló.

## Contexto

### Cómo funciona hoy

- **El orden es global.** `MaquinaEstados.calcularSiguiente` (`postulacion/service/`, ~L189) es
  estático y puro. Saca el orden de las etapas de `estado_postulacion.orden`, no de `etapa.orden`,
  y no recibe la vacante. `siguiente(codigoActual)` (~L276) lo llama con todo el catálogo. Lo usan
  `ServicioPostulacionesPanelImpl.confirmarAvance` (~L243) y `PaseAutomatico.pasarALaPrueba`
  (~L236). Después de DECISION devuelve vacío. A DECISION se entra por `POR_CONFIRMAR`, y es la
  única excepción en `entradaDe` (~L227).
- **Hay saltos que no pasan por `siguiente()`:**
  - `ServicioValidacionImpl.cerrar` (~L201) lleva a `"DECISION_POR_CONFIRMAR"` a fuego.
  - `PaseAutomatico` da por hecho que tras el Perfil viene la etapa técnica: crea el instrumento
    con `EntradaEtapaTecnica.crearAlEntrar`.
  - `ServicioPostulacionesPanelImpl.entrar()` (~L306-336) crea el instrumento al entrar a la
    Prueba y el periodo al entrar a Validación. Solo `confirmarAvance` lanza `recalcularVacante`
    al entrar a `SIMULACION_POR_HABILITAR` (~L256).
- **La prueba del puesto es obligatoria para publicar (RF-73).** Lo exigen
  `ServicioVacantesPanelImpl.exigirInstrumentoTecnico` (~L1291) en el backend, y en el panel
  `leFalta` (`Vacante.tsx` ~L482), `listaParaPublicar` (~L4010) y `loQueFaltaParaPublicar.ts`.
- **Pesos.** La vacante apunta a una versión de pesos de la empresa (`vacante.version_pesos_id`).
  Las versiones son globales y sus etapas suman 100 (`ServicioPesosImpl.validarSumas`). La vacante
  nueva toma la última publicada; hoy es «v4 embudo completo», con 40 / 30 / 15 / 15.
- **La nota global y el semáforo** se calculan al vuelo en `ServicioDecisionImpl.calcular`
  (~L203-282). Recorre las filas de `peso_etapa`. Toda etapa con fila y sin nota entra en
  `faltan`, aunque pese 0, y con algo en `faltan` no hay propuesta (`null`). No renormaliza. Con la
  v4, una vacante en la que nadie pasa por Simulación ni Validación se queda siempre sin propuesta.
  Hay dos precedentes de renormalizar:
  - en datos: la «v3 hito 3» de la V15 lleva 57,14 / 42,86, que es el 40 / 30 llevado a 100;
  - en código: `ServicioPerfilIntegralPanelImpl.ponderadoDeLoRendido` (~L1191) hace el ponderado
    de Perfil y Prueba que usan el ranking, el Excel y el orden de los informes.
- **El portal no conoce el recorrido de la vacante.** `MiPostulacion` (`portal/dto/DtosPortal`,
  ~L245) no trae etapas. El front lo deduce del código de estado:
  - `src/dominio/estados.ts`: `ETAPAS`, `indiceDeEtapa` y `recorridoDe` (~L406) deciden cumplida,
    en curso o pendiente por el índice en la lista fija;
  - `Seguimiento.module.css:39` usa `repeat(5, 1fr)`;
  - `Proceso.tsx:271` dice «Las cinco etapas del proceso»;
  - los rótulos «Etapa N / …» de `Evaluacion.tsx:563`, `Prueba.tsx:580`,
    `CuestionarioTecnico.tsx:287/323`, `Simulacion.tsx:96/177` y `Validacion.tsx:93` llevan el
    número a fuego.
- **WhatsApp** promete las cinco etapas a todo candidato: `ProcedimientoPublico.ETAPAS` (~L31),
  «Todas aplican a toda vacante publicada», que usa `ServicioContextoWhatsappImpl.contexto`
  (~L244).
- **En el panel**, `ETAPAS_PANEL` (`ranking.ts` ~L43) define las cinco pestañas del ranking.
  `ContratarDesdeLaFicha.tsx:179` avisa «sin pasar por las etapas que faltan» cuando la persona no
  está en DECISION. El interruptor «Calificar y avanzar sola» (`Vacante.tsx` ~L4042) y
  `NotaMinimaDelPase.tsx:41` prometen «pasa a la prueba del puesto».
- **Excel e informes** solo conocen Perfil y Prueba:
  - `ServicioExcelRankingImpl` (~L151) rechaza cualquier otra etapa;
  - `EtapaDelInforme` es CV, EVALUACION y PRUEBA;
  - el visor ordena por la nota combinada de las tres notas (`VisorDeInformes.tsx` ~L310).
- **Restricciones de base que no cambian:**
  - `transicion_estado` es inmutable por trigger (V6);
  - `estado_postulacion.etapa_codigo` y `nota_etapa.etapa_codigo` tienen FK a `etapa`;
  - los 18 estados siguen siendo los mismos. Esta spec no crea ni borra estados: cambia qué etapa
    viene después.

### Referencias

- [docs/03-ESTADOS-POSTULACION.md](../../../docs/03-ESTADOS-POSTULACION.md): la rejilla etapa ×
  momento y el recorrido normal.
- `docs/05-MODELO-DE-DATOS.md` §17 (pesos), `docs/08-ALCANCE-DEL-MVP.md` (decisión 5) y
  `docs/CONTRATO-CONTEXTO-Y-RESULTADOS-POR-WHATSAPP-V2.md`.
- [Maqueta aprobada](maqueta/etapas-por-vacante.html): copia local del Artifact.

## Alcance

### Incluye

1. **Catálogo de etapas** con una explicación corta y obligatoria por etapa. Se marca cuál es fija
   (solo el Perfil Integral) y cuáles van en el recorrido predeterminado.
2. **Recorrido por vacante**: la lista ordenada de sus etapas. Se guarda, se muestra y se edita
   desde la configuración de la vacante hasta que llega el primer postulante.
3. **El avance sigue el recorrido de la vacante**, tanto el manual como el automático, sin saltos
   a fuego.
4. **La prueba del puesto deja de ser obligatoria** para publicar cuando la vacante no la tiene
   (cambia el RF-73).
5. **Los pesos se reparten** entre las etapas del recorrido. La versión de pesos no se toca.
6. **La Decisión**: su semáforo solo cuenta las etapas del recorrido. Una vacante puede no tener
   Decisión.
7. **El portal y WhatsApp** muestran solo las etapas del recorrido, en su orden.
8. **El panel**: las pestañas del ranking, la ficha, «Contratar», el Excel y los informes respetan
   el recorrido.
9. **La base para etapas nuevas**: un contrato en el backend y un registro en el front, para que
   agregar una etapa sea una pieza nueva más una migración.
10. **Documentación**: los docs afectados y una guía corta «Cómo agregar una etapa».
11. **La portada pública** deja de prometer cinco etapas (Flujo 4b).
12. **El bot de WhatsApp del CRM** (`Renaser/crm`): queda comprobado con un test de contrato que
    pinta bien un recorrido corto y reordenado (Flujo 4c).

### Queda fuera

- Crear etapas desde el panel. Se programan.
- Plantillas de recorrido con nombre o atajos tipo «Agilizar».
- Un recorrido predeterminado distinto por empresa.
- Reglas de orden entre etapas («esta va antes que aquella»).
- Cambiar el recorrido de una vacante con postulantes.
- Etapas nuevas. Esta spec deja la base; la primera etapa nueva va en su propia spec.
- Generalizar la «Nota combinada» de Perfil y Prueba del ranking, el Excel y los informes a
  otras etapas.

## Comportamiento esperado

### Las etapas que hay

| Etapa | Se puede quitar o mover | En el predeterminado | Explicación (la de la ⓘ) |
|---|---|---|---|
| Perfil Integral | No, siempre primera | Sí | La persona sube su CV y responde unas preguntas. El sistema lo revisa y le pone una nota para ver si encaja con el puesto. |
| Prueba del puesto | Sí | Sí | La persona resuelve una tarea real del puesto, con tiempo límite. Se califica sola y tú la revisas antes de que avance. |
| Simulación de trabajo | Sí | No | La persona elige un día y hace una práctica del trabajo con el equipo. Tú la observas y le pones nota. |
| Validación práctica | Sí | No | La persona trabaja unos días a prueba en el área. El jefe del área dice si lo hizo bien. |
| Decisión | Sí | Sí | Se juntan todas las notas y el sistema sugiere contratar, pedir algo más o descartar. Una persona toma la decisión final. |

Predeterminado: **Perfil Integral → Prueba del puesto → Decisión**.

### Flujo 1: Talento crea la vacante y elige sus etapas

1. Talento crea la vacante como siempre. Nace con el recorrido predeterminado.
2. Abre la configuración de la vacante (la tuerca). En «Qué responderá quien postule», antes de
   «Cómo avanza esta vacante», aparece el bloque nuevo **«Etapas de esta vacante»**, que tiene:
   - arriba, la barra del recorrido con las etapas encendidas, en su orden;
   - debajo, una fila por cada etapa del catálogo, con su casilla, su nombre y su ícono **ⓘ**.
     Las etapas que se pueden mover llevan además las flechas **Subir** y **Bajar**. El Perfil
     Integral tiene la casilla marcada y desactivada, la marca «Siempre primera» y no lleva
     flechas.
3. Al pasar el cursor por la ⓘ, o al enfocarla o tocarla, aparece la explicación de la etapa. Se
   cierra con Escape o al salir.
4. Talento enciende, apaga o mueve etapas. **Cada cambio se guarda en el acto**, como los demás
   ajustes de la tuerca. La barra se redibuja y lo que depende del recorrido se actualiza:
   - **sin Prueba del puesto** desaparece el bloque «Prueba técnica». Tampoco se muestran
     «Estado de la prueba técnica» ni «Plazos de la prueba», y publicar deja de exigir la prueba;
   - **«Calificar y avanzar sola»** nombra la etapa que viene después del Perfil Integral en este
     recorrido: «…pasa a la simulación de trabajo sin que nadie intervenga». Si después del Perfil
     no hay nada: «…queda lista para que la contrates o descartes desde su ficha». El texto de la
     nota mínima del pase cambia igual;
   - **«Pesos de la decisión»** suma, debajo del selector de versión, la tabla **«Cómo pesa en
     esta vacante»**, con las columnas Etapa, En la versión y En esta vacante (ver Flujo 5).
5. Publica. Desde ese momento, quien postule sigue ese recorrido.

### Flujo 2: el primer postulante fija las etapas

1. En cuanto la vacante tiene al menos una postulación, de cualquier estado, las casillas y las
   flechas quedan desactivadas y aparece la marca **«Fijo desde el primer postulante»**.
2. La versión de pesos se sigue pudiendo cambiar, como hoy.
3. El backend rechaza cualquier cambio de etapas en una vacante con postulaciones, cerrada o
   archivada, aunque llegue por la API.

### Flujo 3: el candidato avanza por el recorrido de su vacante

1. Al postular, entra al Perfil Integral, como hoy.
2. Cada vez que termina una etapa, la etapa siguiente es **la que sigue en el recorrido de su
   vacante**. Se entra por el primer momento de esa etapa, salvo la Decisión, que se sigue
   entrando por «por confirmar». Lo que hoy pasa al entrar a cada etapa pasa igual, venga de
   donde venga y sea manual o automático:
   - en la Prueba se crea el instrumento;
   - en la Simulación se recalcula la disponibilidad;
   - en la Validación se abre el periodo.
3. «Calificar y avanzar sola» pasa a la persona a la siguiente etapa del recorrido, con las mismas
   guardas de hoy: interruptor, evaluación entregada, nota mínima y, si la siguiente es la Prueba,
   que haya instrumento. Si el Perfil Integral es la última etapa, no mueve a nadie.
4. Al cerrar la Validación, la persona pasa a la etapa que sigue en el recorrido, no a la Decisión
   a fuego. Si la Validación es la última, se queda en «Validación · por confirmar».
5. Los avisos por correo, campana y WhatsApp se eligen por el estado al que se entra, como hoy.
   Nunca sale un aviso de una etapa que la vacante no tiene.

### Flujo 4: lo que ve el candidato

1. En «Mis procesos» y en el detalle, la barra «Tu recorrido» tiene un tramo por etapa del
   recorrido de su vacante, en su orden. Un recorrido de 3 etapas son 3 tramos que llenan el
   ancho.
2. El texto bajo «Tu recorrido» dice cuántas etapas son: «Las tres etapas del proceso, con la
   fecha en que llegaste a cada una». Con una sola: «La etapa del proceso, con la fecha en que
   llegaste».
3. Cumplida, en curso o pendiente se decide por la posición en el recorrido de la vacante. Un
   recorrido reordenado, por ejemplo Perfil → Simulación → Prueba, se pinta en ese orden.
4. Los rótulos de las pantallas de etapa llevan el número de su posición en el recorrido de la
   vacante: en Perfil → Simulación → Prueba, la prueba dice «Etapa 3 / Prueba del puesto».
   Mientras el recorrido no ha llegado, el rótulo sale sin número («Prueba del puesto»), nunca con
   uno equivocado.
5. Por WhatsApp, el procedimiento que recibe el bot enumera solo las etapas del recorrido. El
   campo `orden` de cada etapa es su posición en el recorrido de la vacante (1, 2, 3…), no el
   orden del catálogo: el bot ordena por ese campo.

### Flujo 4b: la portada pública

En la portada del portal, el bloque de ejemplo del recorrido cambia «Cinco etapas para conocer cómo
trabajas» por **«Cada vacante arma su recorrido con estas etapas»**. Debajo siguen las etapas del
catálogo con su texto corto (`QUE_ES`), como hoy. Así no promete cinco, explica por qué una vacante
puede tener menos y sigue siendo cierto cuando se agregue una etapa.

### Flujo 4c: el bot de WhatsApp del CRM

Revisado el 25/11/2026 en `Renaser/crm` (`main` `e902dc48`):

- `conversation-service/src/renaser_conversacion/dominio/contrato_ex.py`:
  - `procedimiento: list[Etapa] = Field(max_length=8)` admite cualquier cantidad de 0 a 8;
  - `Etapa.orden` admite de 0 a 100;
  - `CodigoEtapa` es una lista cerrada con los cinco códigos actuales.
- `dominio/postulantes_chat.py`:
  - `_procedimiento` pinta las etapas que llegan, ordenadas por `orden`;
  - `_siguiente` toma la primera `PREVISTA` por `orden`;
  - ninguno asume cinco.

Conclusión: para esta spec **el bot no necesita cambios de código**, siempre que EX mande `orden`
por posición (Flujo 4, punto 5). Para que quede probado y no solo leído, el trabajo suma en
`Renaser/crm` un test de contrato en `conversation-service/tests/contract/test_cliente_ex_v2.py`.
El test valida y pinta un contexto con el procedimiento Perfil Integral → Simulación → Decisión,
con `orden` 1, 2 y 3 y la Simulación como `ACTUAL`, y comprueba:

- que «Etapas del proceso» lista exactamente esas tres, en ese orden;
- que «¿Qué sigue?» nombra la Decisión.

Si el harness no abarca el CRM, ese test va en un PR aparte en `Renaser/crm` y es requisito para
aprobar este trabajo.

Una etapa nueva sí exige tocar el CRM: su código tiene que entrar en `CodigoEtapa` antes de que EX
la mande, o el bot rechaza el contexto (Flujo 8).

### Flujo 5: los pesos y la nota global

1. Los pesos salen de la versión que rige la vacante. **La versión no se toca.**
2. Para cada vacante, el peso de cada etapa es su peso en la versión dividido por la suma de los
   pesos de la versión **de las etapas del recorrido**, por 100, con dos decimales; la última
   etapa absorbe el redondeo para que el total dé 100,00. Las etapas sin peso en la versión, como
   la Decisión, no cuentan. Ejemplo con la v4 y el recorrido Perfil → Prueba → Decisión: 57,14 y
   42,86.
3. La nota global de la Decisión usa esos pesos repartidos. En `faltan` solo entran las etapas
   **del recorrido** que tienen peso en la versión y aún no tienen nota.
4. Si la vacante tiene las cinco etapas, el resultado es idéntico al de hoy.
5. La tabla «Cómo pesa en esta vacante» muestra, por etapa del recorrido con peso, su peso en la
   versión y su peso en esta vacante. Las etapas de la versión que la vacante no tiene salen con
   «—» en la última columna.
6. El texto del diálogo de cambio de pesos (`PesosDeLaDecision.tsx` ~L191) deja de nombrar las
   etapas a fuego y nombra las del recorrido.

### Flujo 6: Talento revisa a los candidatos

1. Las pestañas del ranking son las etapas del recorrido, en su orden. Las flechas del teclado
   recorren solo esas.
2. Un enlace con `?etapa=` a una etapa que la vacante no tiene abre la pestaña del Perfil
   Integral.
3. La ficha solo muestra los bloques de las etapas del recorrido.
4. El Excel se ofrece en las pestañas que lo tienen hoy (Perfil y Prueba), si están en el
   recorrido.
5. Los informes:
   - el de la prueba solo existe si la vacante tiene Prueba;
   - sin Prueba, el visor ordena por la nota del Perfil Integral en vez de por la nota combinada.
6. «Contratar» desde la ficha no avisa «sin pasar por las etapas que faltan» cuando la persona
   está en la Decisión o en el «por confirmar» de la última etapa del recorrido.

### Flujo 6b: una vacante sin Decisión

1. La persona termina la última etapa y queda en su «por confirmar», con su nota global en el
   ranking.
2. El panel no ofrece «Avanzar», porque no hay etapa siguiente. Ofrece **Contratar** y
   **Descartar**, como hoy.
3. Sin Decisión no hay semáforo ni evidencia adicional. Nadie pasa a Contratado sin que una
   persona lo confirme.

### Flujo 7: las vacantes que ya existen

- Todas, con o sin postulantes, quedan con las cinco etapas en el orden de siempre. Nadie que esté
  en proceso nota un cambio.
- Las que no tienen postulaciones se pueden ajustar como una nueva.

### Flujo 8: agregar una etapa nueva (desarrollo)

La base deja este camino, y la guía «Cómo agregar una etapa» lo documenta:

1. Una migración registra la etapa en el catálogo con su código, su nombre y su **explicación
   corta, que es obligatoria (NOT NULL)**, si va en el predeterminado y sus estados en la rejilla
   etapa × momento.
2. En el backend, una pieza nueva cumple el contrato de etapa: por qué momento se entra y qué pasa
   al entrar. Ni la máquina de estados ni el avance automático necesitan tocarse.
3. En el front, una entrada nueva en el registro de etapas: nombre corto para el portal, pestaña
   del ranking y bloque de la ficha.
4. En `Renaser/crm`, el código nuevo se agrega a `CodigoEtapa` (`contrato_ex.py`), y ese cambio
   se despliega antes que el de EX.
5. La etapa aparece sola como casilla en todas las vacantes nuevas y sin postulantes, con su ⓘ.

## Diseño técnico

### Datos (V95)

La V95. En main están hasta la V93 (notificaciones del candidato, #148) y la V94 es del
seguimiento de pendientes, que se construye a la vez. Sin out-of-order, si esta spec se fusiona
antes que el seguimiento, el que llegue segundo sube su número al sincronizar con main. Antes de
empezar se comprueba que la V95 siga libre;
si no, se usa la siguiente.

- **`etapa`** suma tres columnas:
  - `descripcion text NOT NULL`, la explicación de la ⓘ. Se rellena con los textos de la tabla de
    arriba antes del NOT NULL;
  - `fija_primera boolean NOT NULL DEFAULT false`, true solo en PERFIL_INTEGRAL;
  - `en_predeterminado boolean NOT NULL DEFAULT false`, true en PERFIL_INTEGRAL, PRUEBA_PUESTO y
    DECISION.

  El orden del predeterminado es `etapa.orden`.
- **`vacante_etapa`**, con `vacante_id` (FK a vacante), `etapa_codigo` (FK a etapa) y `orden`
  (int > 0):
  - PK (`vacante_id`, `etapa_codigo`) y UNIQUE (`vacante_id`, `orden`);
  - un trigger o CHECK de constraint impide que una etapa `fija_primera` quede en un orden
    distinto de 1. La validación completa vive en el servicio.
- **Rellenado:** cada vacante existente recibe las cinco etapas con `orden` = `etapa.orden`.
- Al crear una vacante, el servicio inserta las etapas `en_predeterminado` en el orden de
  `etapa.orden`.

### Backend

- **Contrato de etapa:** una interfaz, por ejemplo `DefinicionDeEtapa`, con el código, el momento
  de entrada y el gancho `alEntrar(postulacion)`. Hay una implementación por cada una de las cinco
  etapas:
  - Prueba: `EntradaEtapaTecnica.crearAlEntrar`;
  - Simulación: `recalcularVacante`;
  - Validación: abrir el periodo;
  - Decisión: entra por POR_CONFIRMAR.

  Spring las reúne por código. Una etapa del catálogo sin implementación hace fallar el arranque
  con un mensaje claro.
- **`MaquinaEstados`:**
  - `siguiente(postulacion)` recibe el recorrido de la vacante;
  - `calcularSiguiente(recorrido, catalogo, actual)` sigue siendo puro. Da el momento siguiente
    de la etapa actual o la entrada de la etapa que sigue en el recorrido, y vacío al final;
  - la transición a la entrada de una etapa nueva llama a su `alEntrar`.

  Se quitan los saltos a fuego de `ServicioValidacionImpl.cerrar` y de `PaseAutomatico`. La
  lógica de `entrar()` de `ServicioPostulacionesPanelImpl` pasa a los ganchos.
- **API del panel:**
  - `PUT /api/v1/panel/vacantes/{id}/etapas` recibe `{ "etapas": ["PERFIL_INTEGRAL", …] }` en
    orden. Valida que empiece por la fija, que no haya duplicados y que los códigos existan.
    Responde 409 si hay postulaciones o la vacante está cerrada o archivada. Exige el mismo
    permiso que el resto de la configuración de la vacante y lo audita;
  - `VacantePanel` suma `etapas` (lista ordenada con código, nombre, descripción y si es fija),
    `etapasEditables` (booleano) y `pesosEnEstaVacante`;
  - el catálogo (`ServicioCatalogoImpl`) suma la descripción y las marcas de cada etapa, para
    pintar las apagadas.
- **Publicar:** `exigirInstrumentoTecnico` solo se llama si el recorrido tiene PRUEBA_PUESTO.
- **Pesos y Decisión:**
  - `ServicioDecisionImpl.calcular` filtra por el recorrido y reparte los pesos (Flujo 5);
  - `ServicioPesosDeLaVacanteImpl` calcula `pesosEnEstaVacante`;
  - la previsión de cambio de pesos lista solo las etapas del recorrido.
- **Portal:** `MiPostulacion` suma `etapas` (códigos en orden, con su nombre) y la usan la lista y
  el detalle.
- **WhatsApp:** `ProcedimientoPublico` recibe el recorrido de la vacante en vez de la lista fija.
  `enMarcha` y `terminado` usan su índice, y `orden` es la posición en el recorrido.
- **Recordatorios** (`TurnosParaRecordar`) y avisos (`avisarAlCandidato`, campana) siguen eligiendo
  por estado. No hace falta cambiarlos.

### Frontend

- **`src/dominio/estados.ts`:**
  - `recorridoDe(estado, etapas, corte)` recibe las etapas de la postulación;
  - `ETAPAS` queda como registro de etapas conocidas (nombre corto, momentos, acciones), no como
    orden.

  `Seguimiento` pinta `repeat(n, 1fr)` según el recorrido.
- **Rótulos «Etapa N /»** de las cinco pantallas (`Evaluacion`, `Prueba`, `CuestionarioTecnico`,
  `Simulacion` y `Validacion`):
  - el número es la posición de la etapa en `etapas` del detalle de la postulación
    (`/portal/postulaciones/{uuid}`, la misma consulta que ya usa «Mis procesos»), que cada
    pantalla tiene a mano por el `uuid` de su ruta;
  - mientras no llega, el rótulo va sin número.
- **Portada** (`src/paginas/vacantes/Vacantes.tsx` ~L648): el texto nuevo del Flujo 4b.
- **Panel:**
  - el bloque «Etapas de esta vacante» va en `ConfiguracionDeLaVacante`, con la casilla del
    patrón `.interruptor`, las flechas con `BotonIcono` como en `BloqueCriterio` y la mutación
    con el mismo `scope: turnoDeLaVacante(id)`;
  - la ⓘ reutiliza `Guia`, movida a `src/panel/ui/` como pieza general con un ícono de
    información, que hoy no existe;
  - `ETAPAS_PANEL` se filtra y ordena con `vacante.etapas`.

  Esta ⓘ es una excepción pedida a «sin textos de ayuda en pantallas»: no se suma ninguna línea
  explicativa fija bajo los campos.
- **`loQueFaltaParaPublicar`, `leFalta` y `listaParaPublicar`** solo exigen la etapa técnica si el
  recorrido tiene Prueba.

## Referencia visual

- **Maqueta o Artifact:** <https://claude.ai/artifact/VLFFSkVpmxFmkPHnYpMHbC>, sección «Vista
  final».
- **Versión aprobada:** versión 5 del 25/11/2026. Copia local, que vale aunque el enlace cambie:
  [maqueta/etapas-por-vacante.html](maqueta/etapas-por-vacante.html). Se abre en el navegador; las
  casillas, las flechas, la ⓘ y el botón «Simular: ya tiene postulantes» funcionan.
- **Pantallas y estados:**
  - Configuración de la vacante:
    - con el predeterminado;
    - sin Prueba;
    - con Simulación subida antes de la Prueba;
    - solo con el Perfil Integral;
    - con postulantes, todo desactivado con «Fijo desde el primer postulante»;
    - con la ⓘ abierta.
  - Portal, detalle del proceso, con 2, 3 y 5 tramos.
- **Escritorio y móvil:**
  - 1440×900 y 390×844;
  - en móvil, la fila de etapa no desborda: el nombre se ajusta y la ⓘ y las flechas no se salen;
  - la barra del portal mantiene su comportamiento móvil actual con n tramos.
- **Diseño existente:** `DESIGN.md` del frontend, la configuración actual de la vacante
  (`Vacante.tsx`, `Vacante.module.css`), `Guia` y `BotonIcono`, y la banda de `Seguimiento`.
- **Fidelidad requerida:**
  - el bloque va primero en la configuración;
  - la barra del recorrido va arriba;
  - una fila por etapa del catálogo, con casilla, nombre, ⓘ y flechas;
  - la marca «Siempre primera» en el Perfil;
  - la marca «Fijo desde el primer postulante»;
  - la tabla «Cómo pesa en esta vacante»;
  - los textos de la ⓘ, tal cual.
- **Puede adaptarse:** el espaciado, el ícono exacto de información y de las flechas, y la
  posición del globo de la ⓘ.

Los textos y reglas de esta spec prevalecen sobre la maqueta. La maqueta muestra datos de ejemplo:
«Renaser», «Asistente administrativo» y un banco «v3».

## Criterios de aceptación

- **AC-01:** Dada una vacante nueva, cuando se crea, entonces su recorrido es Perfil Integral →
  Prueba del puesto → Decisión y la configuración lo muestra así.
- **AC-02:** Dada una vacante sin postulaciones, cuando Talento apaga la Prueba del puesto,
  entonces el cambio se guarda, desaparece el bloque «Prueba técnica» y la vacante se puede
  publicar sin prueba.
- **AC-03:** Dada una vacante sin postulaciones, cuando Talento enciende la Simulación y la sube
  por encima de la Prueba, entonces el recorrido guardado es Perfil → Simulación → Prueba →
  Decisión y la barra lo pinta en ese orden.
- **AC-04:** Dada cualquier vacante, la casilla del Perfil Integral está marcada y desactivada,
  sin flechas, y ninguna petición puede guardar un recorrido que no empiece por el Perfil
  Integral (400).
- **AC-05:** Dada una vacante con al menos una postulación, entonces las casillas y las flechas
  están desactivadas con «Fijo desde el primer postulante», y el PUT de etapas responde 409.
- **AC-06:** Dada cualquier etapa, cuando el usuario pasa el cursor, enfoca o toca su ⓘ, entonces
  ve su explicación tal cual figura en la tabla de esta spec, y Escape la cierra.
- **AC-07:** Dada una vacante Perfil → Decisión con «Calificar y avanzar sola» encendido, cuando
  una persona supera la nota mínima del Perfil, entonces pasa sola a «Decisión · por confirmar» y
  no se crea ninguna prueba.
- **AC-08:** Dada una vacante Perfil → Simulación → Prueba → Decisión, cuando Talento confirma el
  avance desde el Perfil, entonces la persona entra a «Simulación · por habilitar» y se recalcula
  la disponibilidad. Al confirmar la Simulación entra a la Prueba con su instrumento creado.
- **AC-09:** Dada una vacante cuyo recorrido termina en Validación, cuando se cierra la
  Validación, entonces la persona queda en «Validación · por confirmar» y el panel le ofrece
  Contratar y Descartar, pero no Avanzar.
- **AC-10:** Dada una vacante Perfil → Validación → Prueba → Decisión, cuando se cierra la
  Validación, entonces la persona entra a la Prueba, no a la Decisión.
- **AC-11:** Dada una vacante Perfil → Prueba → Decisión con la versión v4, entonces «Cómo pesa
  en esta vacante» muestra 57,14 y 42,86. Con ambas notas, la Decisión propone un semáforo con esa
  nota global, en vez de quedarse sin propuesta.
- **AC-12:** Dada una vacante existente con las cinco etapas, entonces la nota global, el
  semáforo y los avances dan exactamente lo mismo que antes del cambio.
- **AC-13:** Dada una postulación en una vacante de 3 etapas, cuando el candidato abre su proceso
  en el portal, entonces ve 3 tramos que llenan el ancho, en el orden de la vacante, y el texto
  «Las tres etapas del proceso…».
- **AC-14:** Dada una vacante sin Prueba, entonces el candidato nunca recibe por correo, campana
  ni WhatsApp un aviso de prueba, y el contexto de WhatsApp enumera solo las etapas de su
  recorrido.
- **AC-15:** Dada una vacante sin Simulación ni Validación, cuando Talento abre el ranking,
  entonces solo ve las pestañas Perfil Integral, Prueba del puesto y Decisión, y `?etapa=SIMULACION`
  abre el Perfil Integral.
- **AC-16:** Dada una vacante sin Prueba, cuando Talento abre los informes, entonces no hay
  informe de prueba y el visor ordena por la nota del Perfil Integral.
- **AC-17:** Dado «Calificar y avanzar sola» en una vacante cuya segunda etapa es la Simulación,
  entonces su texto dice «…pasa a la simulación de trabajo…». Si no hay segunda etapa, dice
  «…queda lista para que la contrates o descartes desde su ficha».
- **AC-18:** Dado el catálogo, cuando se intenta registrar una etapa sin descripción, entonces la
  base lo rechaza. Una etapa del catálogo sin implementación del contrato impide arrancar la
  aplicación con un mensaje que la nombra.
- **AC-19:** Dado un test con una etapa ficticia registrada solo en el test, cuando una vacante la
  incluye en su recorrido, entonces la máquina de estados entra, avanza y sale de ella sin código
  específico fuera de su implementación del contrato.
- **AC-20:** Dada la configuración en 1440×900 y 390×844, entonces el bloque «Etapas de esta
  vacante» cumple la referencia visual en los estados listados, sin desbordar en móvil.
- **AC-21:** Dada una vacante Perfil → Simulación → Prueba → Decisión, cuando el candidato abre
  su prueba, entonces el rótulo dice «Etapa 3 / Prueba del puesto». En una vacante con el
  predeterminado dice «Etapa 2 / Prueba del puesto».
- **AC-22:** Dada la portada pública, entonces el bloque del recorrido dice «Cada vacante arma su
  recorrido con estas etapas» y no aparece «Cinco etapas».
- **AC-23:** Dado el contexto de WhatsApp de una postulación en una vacante Perfil → Simulación →
  Decisión que está en la Simulación, entonces EX manda tres etapas con `orden` 1, 2 y 3 y la
  Simulación `ACTUAL`. El test de contrato del CRM con ese contexto lista las tres en ese orden y
  «¿Qué sigue?» nombra la Decisión.

## Casos límite y errores

- **Datos vacíos o inválidos:**
  - recorrido vacío, sin el Perfil primero, con códigos repetidos o inexistentes: 400 con un
    mensaje que dice qué falla;
  - el Perfil solo es un recorrido válido.
- **Usuario sin permisos:** sin el permiso de configurar la vacante, el bloque se ve en lectura y
  el PUT responde 403.
- **Recarga, reintento o doble envío:**
  - la mutación va en el turno de la vacante, como las demás;
  - dos cambios seguidos se aplican en orden;
  - si llega un postulante entre la apertura de la pantalla y el cambio, el 409 se muestra como
    error y el bloque pasa a fijo al refrescar.
- **Otros casos:**
  - una vacante con recorrido sin Prueba y una versión de pesos que solo tiene Perfil y Prueba:
    el Perfil pesa 100;
  - una etapa del recorrido sin peso en la versión, como Simulación con la v3: no cuenta en la
    nota global ni en `faltan`;
  - contratar o descartar desde cualquier estado sigue funcionando como hoy;
  - se elimina o cierra la vacante: igual que hoy.

## Datos y compatibilidad

- **Información que debe guardarse:** el recorrido de cada vacante (`vacante_etapa`) y la
  explicación y las marcas de cada etapa del catálogo.
- **Registros existentes:**
  - toda vacante recibe las cinco etapas en el orden de siempre;
  - las postulaciones, el historial y las notas no se tocan.
- **Restricciones de compatibilidad:**
  - el historial de transiciones es inmutable y no se reescribe;
  - el contrato de WhatsApp mantiene su forma; cambian cuántas etapas trae y el `orden`, que pasa
    a ser la posición. El bot ya lo soporta (Flujo 4c);
  - frontend y backend van en pareja: el front nuevo necesita `etapas` en `VacantePanel` y en
    `MiPostulacion`.
- **Requisitos a actualizar:**
  - RF-73: la prueba del puesto es obligatoria solo si la vacante la tiene;
  - `docs/03-ESTADOS-POSTULACION.md`: la etapa siguiente sale del recorrido de la vacante;
  - `docs/05-MODELO-DE-DATOS.md`: `vacante_etapa` y las columnas nuevas de `etapa`;
  - `docs/08-ALCANCE-DEL-MVP.md`: el reparto de pesos;
  - el contrato de WhatsApp v2;
  - la guía nueva «Cómo agregar una etapa».

## Verificación

- **Actor/rol y datos de prueba:**
  - un usuario de Talento con permiso de configurar vacantes y la versión de pesos v4;
  - tres vacantes: una con el predeterminado, una Perfil → Decisión y una Perfil → Simulación →
    Prueba → Decisión;
  - una vacante existente con postulantes, para comprobar que no cambia;
  - candidatos sembrados por API.
- **E2E repetibles en headless:**
  - configurar etapas: apagar, encender, reordenar y publicar sin prueba;
  - el bloqueo con postulantes;
  - el recorrido completo Perfil → Decisión con «Calificar y avanzar sola»;
  - el portal con 3 tramos;
  - las pestañas del ranking según el recorrido.
- **Riesgos que QA debe explorar con Chrome visible:**
  - la ⓘ con teclado y en móvil;
  - las flechas en los extremos;
  - el texto del interruptor cambiando;
  - la barra del portal con 2 y 5 tramos en 390 px;
  - una vacante vieja con postulantes en curso: nada cambia.
- **Comparación visual de QA:** la configuración y el portal, en los estados de la referencia
  visual, contra la maqueta local en 1440×900 y 390×844.
- **Resultado que quiero comprobar personalmente:** crear una vacante sin prueba, publicarla,
  postular y ver que el candidato pasa del Perfil a la Decisión y en el portal ve dos etapas.
  Requiere entrar al panel con sesión, así que lo comprueba el usuario.

## Dependencias y dudas

- **Requiere:**
  - que la V95 siga libre al empezar;
  - frontend y backend en pareja;
  - los tests de integración que recorren el flujo completo
    (`FlujoSimulacionValidacionIT`, `FlujoNotaMinimaDelPerfilIT`, `FlujoPruebaIT`,
    `PaseAutomaticoTest`, `MaquinaEstadosTest`, `ServicioDecisionImplTest`, `LoQueLeTocaTest`)
    deben seguir en verde con las cinco etapas;
  - los e2e del front que cuentan cinco pestañas (`18-ranking-contra-api`, `08-teclado-y-consola`,
    `42-resenas-entre-empresas`) se ajustan al recorrido.
- **Tamaño:** grande. Unos 45 a 50 archivos del backend, unos 100 puntos concretos, y entre 25 y
  30 tests en cada lado. Probablemente pase de los 3 días y haya que ampliar ciclos del harness.
- **Decisiones tomadas el 25/11/2026:**
  1. Los rótulos «Etapa N /» llevan el número según el recorrido de la vacante.
  2. La portada dice «Cada vacante arma su recorrido con estas etapas».
  3. El bot del CRM se comprueba con un test de contrato dentro de este trabajo (Flujo 4c).
  4. Los textos de la ⓘ quedan como están en la tabla.
- **Decisiones pendientes:** ninguna.
