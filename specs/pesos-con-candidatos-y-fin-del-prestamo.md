# Cambiar los pesos con candidatos dentro y dejar de prestar el banco y las pruebas

Estado: lista para implementar.

Revisada el 12/10/2026 contra main (V71, hasta el #118) y contra la rama de
`specs/prueba-una-pregunta-por-pantalla.md`, que estaba en QA y cambia el correo y la consigna
(decisión 8).

## Objetivo

Tres cambios que cierran el trabajo de las preguntas propias (fase 1, PR #108/#69, y fase 2,
PR #110/#71, en producción desde el 01 y el 02/10/2026):

- **Parte A — los pesos.** Hoy, desde que alguien postula, una vacante ya no puede cambiar su
  versión de pesos («Pesos de la decisión»). Si la empresa se da cuenta de que el currículum pesa
  demasiado frente a las preguntas, no puede corregirlo hasta la siguiente convocatoria. Esta
  spec deja cambiarla con candidatos dentro y **recalcula a todos a la vez, sin IA y sin mover a
  nadie de etapa**, con el mismo principio que ya se aplicó a los puntos y a las instrucciones de
  la IA: la vara cambia, pero para todos.
- **Parte B — el fin del préstamo.** Hoy una empresa sin banco propio, o sin pruebas propias, lee
  los de RENASER. Las vacantes nuevas ya no lo usan (decisiones 19 de la fase 1 y 13 de la fase 2),
  pero el mecanismo sigue en el código para las vacantes viejas. Esta spec **deja cada vacante
  vieja atada a lo que ya usaba y borra el préstamo del banco y de las pruebas**, sin esperar a
  que esas vacantes cierren.
- **Parte C — el enlace del enunciado que nadie usa.** Al subir el PDF del enunciado en el editor,
  el sistema fabrica y guarda un enlace que caduca a los 180 días, pensado para el correo de la
  prueba. Ese correo ya no lo lleva (comprobado el 03/10/2026; desde la decisión 8,
  `{{enlacePrueba}}` va vacía siempre), y el portal y el panel abren el PDF con un enlace fresco
  cada vez. Esta spec **deja de fabricar ese enlace en el editor** y quita el último sitio que lo
  lee, para que no quede un enlace con fecha de caducidad esperando a que alguien lo vuelva a
  usar.

## Contexto

### Comportamiento actual

**La versión de pesos** (`version_pesos`, elegida por vacante en `vacante.version_pesos_id`)
trae tres cosas juntas:

| Qué decide | Tabla | Ejemplo |
|---|---|---|
| Cuánto pesa cada etapa en la nota final | `peso_etapa` | Perfil Integral 40, Prueba 30, Simulación 15, Validación 15 |
| Cómo se reparte el Perfil Integral | `peso_componente_perfil` | CV 12, evaluación 28 |
| Cuánto pesa cada parte del CV y cada dimensión, por nivel | `peso_criterio`, `peso_dimension` | «Gestión de personas» 15 en Dirección, 0 en Ejecución |

- Las versiones publicadas no se editan: para cambiar un peso se crea otra versión y se elige en
  la vacante. Toda vacante nace con los pesos generales y no puede volver a ellos desde el panel.
- `ServicioVacantesPanelImpl.asignarVersionPesos` pasa por `exigirVaraQuieta`: desde la primera
  postulación contesta 409 (`docs/DECISION-UNA-VACANTE-UNA-VERSION.md`, del 27/08/2026). El motivo
  era que cambiarla solo movía las notas futuras y dejaba a unos medidos con una vara y a otros
  con otra.
- **La IA nunca hace la cuenta con los pesos.** El agente del CV (`AgenteEvidenciaCv`) pone a cada
  uno de los ocho criterios una nota de 0 a 100, y el código la pondera. Lo mismo pasa con las
  dimensiones del método `CRITERIOS`, con las preguntas propias y con la nota final.
- Dependen de la versión, entre otros: `CalificacionPorCriterio.calcularNotaEtapa` y `maximoDe`,
  `CalificacionCriterios` (155), `CalificacionCuestionarioTecnico` (216),
  `PuenteCalificacionIaImpl` (811 y 909, el perfil y la nota del currículum),
  `ServicioCalificacionImpl` (466), `CierreDeLaPruebaPropia` (102, la nota de la prueba del
  editor), `ServicioDecisionImpl` (160 y 206), y el ranking y su Excel
  (`ServicioPerfilIntegralPanelImpl`, `ServicioExcelRankingImpl`). `nota_etapa`,
  `perfil_talento` y `decision` guardan con qué versión se calcularon.
- **Qué etapas cambian de valor con los pesos:**
  - el currículum (`peso_criterio` de sus ocho criterios por nivel);
  - la evaluación con el banco `CRITERIOS` (`peso_dimension`);
  - el Perfil Integral (`peso_componente_perfil`);
  - **la simulación y la validación**: ponderan criterios globales, sin puntos propios, con el
    `peso_criterio` del nivel (`CalificacionPorCriterio.maximoDe`, llamado desde
    `ServicioCalificacionSimulacion` y `ServicioValidacionImpl`);
  - la nota final, que se calcula al vuelo con `peso_etapa` (`ServicioDecisionImpl.calcular`).
- **Qué no cambia de valor:** la prueba técnica. La del editor se nota por puntos (V69), la de
  plantilla trae los puntos en sus criterios y el cuestionario técnico no lee pesos. Solo guarda
  la versión con que se calculó.

**El préstamo** lo decide `DuenoDelInstrumento`: sin la bandera de personalización, la empresa lee
las filas de la plataforma (RENASER). Para el banco y las pruebas se usa aquí:

- **Banco**: `ServicioEvaluacionImpl.crearAlPostular` (158) busca **en cada postulación** la última
  versión publicada del nivel del dueño; `ServicioVacantesPanelImpl` (1172) comprueba que haya
  banco al elegir el origen; `ServicioBancoPreguntasImpl` (94, 103, 942) lo usa en la configuración.
- **Pruebas**: `ServicioPlantillaPruebaImpl` (102, 616) lista y lee las plantillas;
  `ServicioVacantesPanelImpl` (676, 874) las usa para los plazos y para asignar una a la vacante.
  Desde el #112 (`specs/Done/fuga-del-catalogo-de-preguntas.md`) no hay pantalla «Pruebas»: lo
  único del panel que enseña plantillas prestadas es el desplegable de versiones de una vacante
  vieja.
  Una vez asignada, la etapa técnica lee la versión por su id (`vacante.version_plantilla_prueba_id`),
  sin préstamo.
- Personalizar el banco y las pruebas ya no existe (fase 2). Personalizar los **pesos** y las
  **plantillas de evaluación** sigue, y sus préstamos también.

**El enlace del enunciado** (el PDF o Word que se adjunta a la prueba):

- **Editor (fase 2).** `ServicioPruebaPropiaImpl.subirConsigna` guarda el archivo
  (`version_banco.consigna_archivo_id`) y además firma un enlace de 180 días
  (`AlmacenArchivos.urlDeConsigna`) y lo guarda en `version_banco.url_consigna`. «Copiar de otra
  vacante» (`copiarContenido`) copia ese enlace con la caducidad del original.
- **Quién lo lee.** Con la decisión 8 fusionada, solo el plan B del portal si el almacén no sabe
  firmar (`ServicioPruebaImpl.consignaDe`, 812-820), y solo después de pulsar «Empezar». El
  portal y el panel lo abren por el archivo, con un enlace fresco de 5 minutos.
- **El correo ya no lo usa.** La versión activa de `PRUEBA_DISPONIBLE` (v3) manda a entrar al
  portal y no lleva `{{enlacePrueba}}`; ninguna vacante tiene un texto propio. Y desde la
  decisión 8, `MaquinaEstados.loDeLaPrueba` rellena `{{enlacePrueba}}` vacía en todos los
  caminos, sin leer `url_consigna` de ninguna tabla.
- **Plantillas viejas** (`version_plantilla_prueba.url_consigna`, V29/V46). Ahí no se guardó el
  archivo, solo el enlace: es la única pista de dónde está el PDF (en las primeras convocatorias,
  un enlace de Drive). Ninguna pantalla lo enseña ya: la pestaña «Pruebas» se retiró en el #112.

### Decisiones tomadas con el usuario (02/10/2026)

1. **Se puede cambiar la versión de pesos con candidatos dentro**, y se recalcula a todos a la vez.
   Cubre las tres capas de la versión, no solo el Perfil Integral.
2. **Sin IA y sin mover a nadie de etapa.** Las decisiones ya tomadas no se deshacen.
3. **La limpieza del préstamo va en esta misma spec.**

### Decisiones de diseño de esta spec

4. **Una vacante vieja que leía el banco de RENASER queda atada a la versión que ya usaba**, que se
   guarda en la propia vacante. Sus candidatos nuevos rinden exactamente lo mismo que los de
   antes, aunque RENASER publique otra versión para ese nivel. Así el préstamo se puede borrar
   ya, sin esperar a que cierren.
5. **Una vacante vieja que ya tiene asignada una prueba de RENASER la conserva por su id.** Una
   vacante vieja en borrador con «prueba del puesto» sin prueba asignada pasa a la prueba propia
   del editor.
6. **Los préstamos de pesos y de plantillas de evaluación se quedan.** Son números y duraciones,
   no un método, y sin ellos una empresa nueva no podría calcular ninguna nota.

### Decisión tomada con el usuario (03/10/2026)

7. **El editor deja de fabricar el enlace largo del enunciado.** Las plantillas
   viejas no se tocan: su enlace es lo único que apunta a su PDF y se irá con ellas.

### Decisión tomada con el usuario (10/10/2026, en `specs/prueba-una-pregunta-por-pantalla.md`)

8. **El caso se lee dentro del reloj.** Antes de pulsar «Empezar», el portal no recibe ni las
   preguntas, ni el enunciado, ni los materiales, ni la consigna. Y el correo `PRUEBA_DISPONIBLE`
   no reparte el enlace del enunciado en ningún camino: `{{enlacePrueba}}` va siempre vacía, con
   prueba del editor o con plantilla vieja. Lo implementa esa rama, no esta spec.

### Pantallas o procesos afectados

- **Panel**: «Pesos de la decisión» en la vacante (`Vacante.tsx`, hacia la 3990), con su
  confirmación; la configuración del banco y el desplegable de pruebas de una vacante vieja de una
  empresa cliente; el ranking y su Excel.
- **Backend**: asignar la versión de pesos, el recálculo, la creación de la evaluación al
  postular, el resolutor del dueño, la configuración del banco y de las plantillas.

### Referencias

- `specs/Done/preguntas-propias-de-la-vacante.md` (decisiones 17, 19, 20) y
  `specs/Done/prueba-del-puesto-con-el-editor.md` (decisión 13).
- `docs/DECISION-UNA-VACANTE-UNA-VERSION.md`: esta spec le añade una tercera excepción.
- `specs/Done/fuga-del-catalogo-de-preguntas.md` (#112): retiró `/admin/pruebas` y deja las
  plantillas y sus endpoints para «la limpieza del préstamo».
- `specs/prueba-una-pregunta-por-pantalla.md`: la decisión 8.
- `specs/pruebas-indice-de-la-empresa.md` (borrador): vuelve a ocupar `/admin/pruebas`, solo con
  versiones del editor de la propia empresa.

## Alcance

**Incluye:**

- Parte A:
  - cambiar la versión de pesos de una vacante con candidatos dentro, con confirmación;
  - recalcular a todos sus candidatos: CV, evaluación (preguntas propias o banco `CRITERIOS`),
    Perfil Integral, simulación, validación, nota final y grupo de prioridad;
  - reescribir la versión en las notas que no cambian de valor (la prueba técnica);
  - registrar cada cambio;
  - actualizar el texto de `docs/DECISION-UNA-VACANTE-UNA-VERSION.md`.
- Parte B:
  - atar a su versión de banco las vacantes viejas que leían el de RENASER;
  - pasar a la prueba propia las vacantes viejas en borrador sin prueba asignada;
  - borrar el préstamo del banco y de las pruebas del resolutor y de todo lo que lo usa;
  - que una empresa cliente no vea el banco ni las plantillas de RENASER en ninguna pantalla.
- Parte C:
  - dejar de firmar y guardar el enlace de 180 días al subir el enunciado en el editor, y de
    copiarlo al copiar de otra vacante;
  - quitar el plan B del portal que lee ese enlace guardado.

**Queda fuera:**

- Editar una versión de pesos publicada. Se sigue creando otra y eligiéndola.
- Volver a «los pesos generales» desde la vacante (sigue como hoy).
- El préstamo de pesos y de plantillas de evaluación (decisión 6).
- Las vacantes de RENASER y las de empresas que ya personalizaron su banco o sus pruebas: no leen
  nada prestado y no cambian.
- Reabrir decisiones ya tomadas o mover candidatos por el cambio de nota.
- Borrar las plantillas o los bancos de RENASER: siguen siendo suyos y los usan sus vacantes.
- El enlace guardado de las plantillas viejas (`version_plantilla_prueba.url_consigna`): se
  queda (decisión 7).
- `{{enlacePrueba}}` en el correo: ya va vacía en todos los caminos por la decisión 8.
- Cambiar los textos de correo: ya no llevan el enlace.

## Comportamiento esperado

### Parte A — los pesos

1. **Elegir otra versión con candidatos dentro.**
   - En «Pesos de la decisión», el desplegable sigue activo aunque la vacante tenga postulantes,
     mientras no esté cerrada, archivada ni eliminada.
   - Si hay candidatos con alguna nota, antes de guardar sale una confirmación:
     «Se recalcularán las notas de 23 personas con los pesos nuevos: currículum, Perfil Integral,
     simulación, validación y nota final. No se llama a la IA y nadie cambia de etapa. Las
     decisiones ya tomadas se mantienen». Debajo, qué cambia: los pesos de cada etapa y del Perfil
     Integral, antes y después.
   - Sin candidatos con nota, se guarda sin preguntar, como hoy antes de la primera postulación.
2. **Al confirmar, en una sola operación:**
   - la vacante pasa a la versión nueva;
   - para cada postulación se recalcula, con las notas que ya existen:
     - la nota del currículum, con los pesos de sus criterios para el nivel del puesto;
     - la de la evaluación: las preguntas propias no dependen de los pesos; el banco `CRITERIOS`
       se repondera con sus dimensiones;
     - la del Perfil Integral, con el reparto entre currículum y evaluación;
     - las de simulación y validación, con el peso de sus criterios para el nivel del puesto;
     - la de la prueba técnica no cambia de valor (lleva sus propios puntos): solo pasa a decir
       la versión nueva;
     - la nota final y el grupo de prioridad;
   - todo queda calculado con la versión nueva y lo dice así (`version_pesos_id`);
   - la fila de `decision` de quien ya fue decidido **no se toca**: guarda la nota y la versión
     con que se decidió;
   - **no se encola ningún trabajo de IA, no se llama a la máquina de estados y no se deshace
     ninguna decisión.**
3. **Lo que llega después** (una calificación de la IA en curso, alguien que postula o rinde) se
   calcula ya con la versión nueva, porque se lee de la vacante en el momento de calcular.
4. **Cada cambio queda registrado** con quién, cuándo, la versión anterior y la nueva, y cuántas
   personas se recalcularon.
5. **Permisos**: los de hoy para asignar la versión de pesos.
6. **Si algo falla** a mitad, no se guarda nada: la vacante sigue con la versión anterior y todas
   las notas como estaban. Se dice qué pasó.

### Parte B — el fin del préstamo

7. **Las vacantes viejas que leían el banco de RENASER** (de una empresa que no es la plataforma,
   sin banco propio, con el origen del banco por nivel; en cualquier estado):
   - quedan atadas a una versión fija, guardada en la vacante: la de su evaluación más reciente
     si ya tenía postulantes, o la publicada de RENASER para su nivel si no;
   - al postular, se usa esa versión fija y no la última publicada;
   - los candidatos que ya estaban siguen atados a la suya, como siempre (RF-138);
   - el panel sigue llamándola «El banco de RENASER para su nivel».
8. **Las vacantes viejas con una prueba de RENASER asignada** la conservan: la etapa técnica la
   sigue leyendo por su id y nada cambia para sus candidatos.
9. **Las vacantes viejas en borrador con «prueba del puesto» y ninguna asignada** pasan a la prueba
   propia: el bloque «Prueba técnica» enseña «Armar la prueba», como en una vacante nueva.
10. **Borrar el préstamo del banco y de las pruebas.**
    - El resolutor solo resuelve pesos y plantillas de evaluación. El banco y las pruebas de una
      empresa son siempre los suyos.
    - Una empresa cliente ya no ve el banco de RENASER en la configuración del banco, ni sus
      plantillas en el desplegable de pruebas de una vacante vieja ni en ningún otro sitio.
      (`/admin/pruebas` ya no existe; si vuelve con `specs/pruebas-indice-de-la-empresa.md`,
      solo enseña versiones del editor de la propia empresa.)
    - Desaparecen las banderas de personalización del banco y de las pruebas: nada las lee.
      Quitar las columnas o dejarlas lo decide quien implemente.
    - RENASER no nota nada: sus vacantes, su banco y sus plantillas son suyos.

### Parte C — el enlace del enunciado

11. **Al subir el enunciado en el editor** se guarda el archivo y nada más: no se firma ni se
    guarda ningún enlace. El panel lo sigue abriendo igual, con un enlace fresco, y el portal
    también, después de pulsar «Empezar» (decisión 8).
12. **Al copiar de otra vacante** se copia el archivo, no un enlace.
13. **El correo de la prueba** no cambia en esta spec: por la decisión 8, `{{enlacePrueba}}` ya
    va vacía en todos los caminos.
14. **Si el almacén no sabe firmar** (el de disco, en local), el portal enseña el nombre del
    adjunto sin enlace, como ya pasaba en ese caso: el plan B del enlace guardado desaparece.

## Referencia visual

Solo cambia la vacante. El resto son pantallas existentes que dejan de enseñar contenido de
RENASER a otras empresas.

```
Pesos de la decisión
  [ CAZATALENTOS · MICRO                ▾ ]

┌ ¿Cambiar a «CAZATALENTOS · PYME»? ──────────────────────────────────────┐
│ Se recalcularán las notas de 23 personas con los pesos nuevos:           │
│ currículum, Perfil Integral, simulación, validación y nota final.        │
│ No se llama a la IA y nadie cambia de etapa.                             │
│ Las decisiones ya tomadas se mantienen.                                  │
│                                                                          │
│                     Antes   Después                                      │
│ Perfil Integral       40      40     · CV 12 → 20 · Preguntas 28 → 20    │
│ Prueba del puesto     30      35                                         │
│ Simulación            15      15                                         │
│ Validación            15      10                                         │
│                                                                          │
│ [ Cancelar ]                                   [ Cambiar y recalcular ]  │
└──────────────────────────────────────────────────────────────────────────┘
```

- **Estados**: guardando («Recalculando 23 personas…»), hecho («Pesos cambiados: se recalcularon
  23 personas»), error (la vacante sigue como estaba) y vacante cerrada (desplegable en solo
  lectura).
- **Móvil**: la tabla de antes y después se apila, una etapa por línea.
- **Reglas**: las del `DESIGN.md` del panel; «Cambiar y recalcular» es la acción principal del
  diálogo.

## Criterios de aceptación

### Parte A

- **AC-01**: Dada una vacante con 3 candidatos calificados (CV, preguntas y prueba), cuando se
  elige otra versión de pesos y se confirma, entonces la nota del currículum, la del Perfil
  Integral, la nota final y el grupo de los 3 se recalculan con la versión nueva, la de la etapa
  técnica (que no depende de los pesos) queda igual, todas sus notas dicen esa versión, no se encola ningún trabajo de IA y nadie cambia de
  etapa.
- **AC-02**: Dada una versión donde «Gestión de personas» pasa de 0 a 15 en el nivel de la vacante,
  cuando se cambia, entonces la nota del currículum usa la nota de 0 a 100 que la IA ya había
  puesto a ese criterio, sin volver a llamarla.
- **AC-03**: Dada una vacante con el banco `CRITERIOS` de RENASER, cuando se cambian los pesos,
  entonces su nota de evaluación se repondera con las dimensiones nuevas sin IA. Y dada una
  persona con nota de simulación o de validación, esa nota se repondera con el peso de sus
  criterios para el nivel en la versión nueva.
- **AC-04**: Dada una postulación ya decidida (contratada o que no continúa), cuando se cambian los
  pesos, entonces su nota se recalcula pero su estado y su decisión no cambian, y su fila de
  `decision` conserva la nota y la versión con que se decidió.
- **AC-05**: Dada una calificación de la IA en curso al cambiar los pesos, cuando llega, entonces se
  calcula con la versión nueva.
- **AC-06**: Dada una vacante sin candidatos con nota, cuando se cambia la versión, entonces se
  guarda sin confirmación. Dada una vacante cerrada, archivada o eliminada, entonces contesta 409.
  Sin permiso, 403.
- **AC-07**: Dado un fallo a mitad del recálculo, entonces la vacante sigue con la versión anterior
  y ninguna nota cambia.
- **AC-08**: Dado un cambio, entonces queda registrado con quién, cuándo, las dos versiones y
  cuántas personas se recalcularon.
- **AC-09**: Dados el ranking y su Excel después del cambio, entonces enseñan las notas nuevas y
  el orden nuevo.

### Parte B

- **AC-10**: Dada una empresa cliente sin banco propio con una vacante vieja que leía el banco de
  RENASER y dos candidatos con la versión X, cuando RENASER publica la versión Y para ese nivel y
  postula un tercero, entonces el tercero rinde la X.
- **AC-11**: Dada esa misma vacante sin postulantes al desplegar, entonces queda atada a la versión
  de RENASER publicada en ese momento, y el primero que postule rinde esa.
- **AC-12**: Dada una vacante vieja de una empresa cliente con una prueba de RENASER asignada y
  candidatos a medio rendir, entonces la etapa técnica sigue igual para todos.
- **AC-13**: Dada una vacante vieja en borrador con «prueba del puesto» sin prueba asignada,
  entonces pasa a la prueba propia y su bloque «Prueba técnica» enseña «Armar la prueba».
- **AC-14**: Dada una empresa cliente, cuando abre la configuración del banco o el desplegable de
  pruebas de una vacante vieja, entonces no ve nada de RENASER, y pedir por la API una versión
  de RENASER contesta 404.
- **AC-15**: Dada una empresa cliente sin pesos propios, entonces sigue viendo y usando las
  versiones de pesos de RENASER, y personalizar los pesos y las plantillas de evaluación sigue
  funcionando.
- **AC-16**: Dadas RENASER y una empresa que ya había personalizado su banco y sus pruebas, cuando se
  despliega, entonces sus vacantes, bancos y pruebas funcionan como antes.

### Parte C

- **AC-17**: Dada una vacante con prueba del editor, cuando se sube un PDF de enunciado, entonces
  se guarda el archivo, la versión no guarda ningún enlace, y el candidato lo abre desde el portal
  después de pulsar «Empezar» y la empresa desde el panel.
- **AC-18**: Dada una vacante que copia la prueba de otra que tenía adjunto, entonces la copia
  enseña el mismo adjunto, se abre desde el portal después de empezar, y no guarda ningún enlace.
- **AC-19**: Dado el almacén de disco (que no sabe firmar) y una prueba del editor con adjunto
  cuya versión aún guarda un `url_consigna` de antes, cuando el candidato empieza la prueba,
  entonces ve el nombre del adjunto sin enlace y ese `url_consigna` no aparece en la respuesta.

### Toda la spec

- **AC-20**: Dada la suite completa, cuando corre `verify`, entonces las pruebas existentes pasan;
  las que daban por hecho el préstamo del banco o de las pruebas, o el enlace guardado del
  editor, se reescriben a la regla nueva.

## Casos límite y errores

- **Una versión de pesos de otra empresa** (por id): 404, como hoy.
- **Elegir la misma versión que ya tiene**: no recalcula ni registra nada.
- **Dos cambios seguidos**: el segundo espera a que termine el primero o contesta 409; nunca se
  mezclan.
- **Un candidato sin nota todavía**: no cuenta en la confirmación; su nota saldrá con la versión
  nueva.
- **Un criterio del CV con peso 0 en las dos versiones**: no cambia nada.
- **Vacante vieja que leía el banco de RENASER para un nivel sin banco publicado**: no tenía examen
  posible; queda sin versión fija y se comporta como hoy (no se puede postular con evaluación hasta
  que alguien cambie su origen).
- **Una vacante vieja archivada que se desarchiva**: usa su versión fija.
- **Reabrir una evaluación** (`ServicioPerfilIntegralPanelImpl`) sigue con la versión de esa
  evaluación.

## Datos y compatibilidad

### Qué hay que guardar

Sin fijar el número de migración: main va por la V71 (12/10/2026), y hay sesiones paralelas.

- **Parte A**: nada nuevo en el esquema. El registro del cambio va en la auditoría, como hicieron
  las fases 1 y 2.
- **Parte B**:
  - en la vacante, la versión de banco fija (nula salvo en las vacantes viejas del punto 7);
  - una migración que la rellena para esas vacantes y pasa a la prueba propia las del punto 9;
  - la migración corre en el mismo despliegue que el código que deja de resolver el préstamo.
- **Parte C**: nada nuevo. `version_banco.url_consigna` (V67) deja de escribirse; vaciarla o
  quitarla lo decide quien implemente. Sin el plan B y con la decisión 8, los enlaces que ya
  tiene no los lee nadie.

### Registros existentes

- Ninguna nota cambia al desplegar. Solo cambian cuando alguien cambia los pesos de una vacante.
- Las evaluaciones, intentos y notas existentes siguen apuntando a lo que apuntaban.

### Trampas conocidas

- **Muchos sitios calculan con la versión de pesos** (lista en el contexto). El recálculo tiene que
  pasar por un solo camino que llame a cada uno sin la máquina de estados: el de la fase 1,
  `recalcularSinMover`, no cierra el Perfil Integral ni mueve a nadie. `cerrarPerfilIntegral` sí
  mueve.
- **El ranking puede filtrar `nota_etapa` por la versión de la vacante.** Si la vacante cambia de
  versión antes de que estén reescritas, las filas desaparecen. Va todo en una transacción.
- **`perfil_talento` también guarda la versión.**
- **Simulación y validación también dependen de los pesos**, por `CalificacionPorCriterio.maximoDe`
  con los criterios globales. `recalcularSinMover` solo rehace el Perfil Integral y el grupo: esas
  dos etapas hay que recalcularlas aparte, con `calcularNotaEtapa`, que solo escribe la nota y no
  mueve a nadie.
- **`decision` guarda `version_pesos_id` y la nota con que se decidió.** No se reescribe: es lo
  que se decidió con la vara de entonces. La nota final del ranking se calcula al vuelo con la
  versión de la vacante.
- **El grupo de prioridad necesita los hallazgos guardados**, no el resultado de la IA (trampa ya
  resuelta en la fase 1).
- **`crearAlPostular` resuelve el banco en cada postulación.** Sin la versión fija, quitar el
  préstamo deja sin examen a las vacantes viejas de las clientas.
- **Las pruebas existentes de `DuenoDelInstrumento`, `ServicioPersonalizacionImpl`,
  `CopiadorDeInstrumentosImpl` y la configuración del banco** dan por hecho el préstamo.
- **`docs/DECISION-UNA-VACANTE-UNA-VERSION.md`** dice que la versión de pesos no cambia jamás: se
  actualiza con las tres excepciones (instrucciones de la IA, puntos y pesos).
- **`url_consigna` vive en dos tablas.** La de `version_banco` (editor) se deja de escribir; la de
  `version_plantilla_prueba` (plantillas viejas) **no se toca**: allí no hay id de archivo y
  borrarla pierde el PDF. Con la decisión 8, el correo no lee ninguna de las dos, y
  `MaquinaEstadosAvisoTest` ya comprueba que `enlacePrueba` va vacía en los dos caminos.
- **La rama de la decisión 8 toca los mismos archivos que la Parte C**: `MaquinaEstados`,
  `ServicioPruebaImpl` (`consignaDe` solo se llama después de empezar), `DtosPrueba`,
  `MaquinaEstadosAvisoTest` y `FlujoPruebaPropiaIT`. Se parte de main con esa rama ya fusionada.

### Restricciones de compatibilidad

- **RF-138**: el candidato queda atado a la versión de banco y de prueba con que empezó.
- **Una vacante, una versión de pesos**: sí, pero puede cambiar para todos a la vez.
- **RF-147**: la IA no toca las cerradas ni la cuenta con los pesos.

## Verificación

- **Actor, rol y datos**: un usuario de RENASER y otro de una empresa cliente sin banco ni pruebas
  propias; dos versiones de pesos publicadas con repartos distintos; una vacante con candidatos
  calificados en CV, preguntas y prueba, y alguno con nota de simulación o de validación; una
  vacante vieja de la clienta con el banco de RENASER y
  candidatos; otra con una prueba de RENASER asignada; otra en borrador sin prueba.
- **Recorridos E2E repetibles**:
  1. Cambiar los pesos de la vacante con candidatos, leer la confirmación, confirmar y ver el
     ranking reordenado y nadie movido de etapa.
  2. Como la clienta: abrir la configuración del banco y el desplegable de pruebas de una vacante
     vieja y no ver nada de RENASER; postular a la vacante vieja y rendir la misma versión que los
     anteriores.
  3. Subir un PDF de enunciado en el editor, copiar esa prueba a otra vacante y abrir el adjunto
     desde el panel y, tras pulsar «Empezar», desde el portal en las dos. Empezar arranca el
     reloj: hace falta un candidato por vacante.
- **Riesgos que QA explora**: el cambio con una calificación de la IA en curso; un fallo a mitad;
  el ranking y el Excel justo después; la vacante vieja que desarchiva.
- **Comprobación personal**: la confirmación y el ranking después del cambio. Hay inicio de sesión
  de por medio y **esa comprobación la hace el usuario**; se avisa ahora y no al entregar.
- **Avisos**: `verify` corre las pruebas de integración y `test` no; la suite E2E trae fallos de
  fondo por datos y el rojo se compara contra una corrida de referencia.

## Dependencias y dudas

- **Requiere**:
  - las fases 1 y 2 en producción (lo están);
  - la rama de `specs/prueba-una-pregunta-por-pantalla.md` fusionada (en QA el 12/10/2026): trae
    la decisión 8 y toca los mismos archivos que la Parte C. Empezar después, o sincronizar antes
    de verificar.
- **No choca con** `specs/pruebas-indice-de-la-empresa.md` ni `specs/pruebas-modelo.md`
  (borradores): dejan fuera las vacantes del sistema antiguo y el banco de RENASER. Si el índice
  llega antes, el AC-14 se comprueba también en su `/admin/pruebas`. `pruebas-modelo` dice que la
  copia lleva `consignaArchivoId` y `urlConsigna`: tras esta spec, `copiarContenido` solo lleva el
  archivo, y esa línea habrá que corregirla allí.
- **Decisiones pendientes**: ninguna.
