# Reporte de los worktrees — 31 de agosto de 2026

## Alcance y método

Recorrido de **solo lectura** sobre los árboles de trabajo paralelos de los dos repositorios:

- **Backend:** `RENASER-RECLUTAMIENTO` — 12 worktrees en `.claude/worktrees/` más uno en `.worktrees/`.
- **Panel y portal:** `RenaserOsPostulantes` — 7 worktrees en `.claude/worktrees/`.

No se modificó ningún archivo del código, ni se ejecutaron pruebas, ni se tocó ninguna rama. Lo único
que se escribió es este documento.

Cada rama se comparó con `main` de **dos formas distintas**, porque una sola engaña. `git log main..rama`
dice cuántos commits tiene la rama por delante, pero después de un *squash-merge* los commits originales
siguen contando como «por delante» aunque su contenido lleve días dentro de `main`. Por eso cada rama se
verificó además con `git diff main..rama`: si esa diferencia está vacía o solo resta líneas, la rama **no
aporta nada** que `main` no tenga ya. Las cifras de líneas de este reporte son de la primera medición
(lo que la rama construyó); la clasificación entre «pendiente» e «integrado» es de la segunda.

---

## La foto de un vistazo

| Worktree | Rama | Estado | Qué contiene |
|---|---|---|---|
| `elated-cannon-dcaad1` | `claude/elated-cannon-dcaad1` | **Pendiente, con commits** | Descargar un currículum deja de dar acceso a toda la empresa |
| `moduloVacantes` | `feat/moduloVacantes` | **Pendiente, con commits** | Rama paraguas: contiene las tres de abajo + 2 arreglos de QA |
| `crudAreas` | `feat/crudAreas` | Pendiente (dentro de moduloVacantes) | Administrar las áreas de la organización |
| `editarPlantillasPrueba` | `feat/editarPlantillasPrueba` | Pendiente (dentro de moduloVacantes) | Componer y editar una prueba desde el panel |
| `tiemposDeLaVacante` | `feat/tiemposDeLaVacante` | Pendiente (dentro de moduloVacantes) | El reloj de la etapa técnica y su suelo de 5 minutos |
| `rankingOrdenExcel` | `feat/ranking-orden-y-excel` | **Sin un solo commit** | Ciudad del candidato, filtro y Excel del ranking |
| `sembradorRoto` | `fix/sembrador-datos-de-prueba` | **Sin un solo commit** | El sembrador de datos de prueba, roto |
| `mensajeAgradecimiento-backend` | `fix/mensajeAgradecimiento` | **Sin commit, y desfasado** | El cierre se explica por su motivo (V37 contra un main en V45) |
| `banco-cazatalentos` | `feat/banco-cazatalentos` | Ya en `main` (PR #45) | Worktree obsoleto |
| `prueba-tecnica` | `feat/prueba-tecnica` | Ya en `main` (PR #48) | Worktree obsoleto |
| `puestoEnSolicitud` | `codex/puesto-en-solicitud` | Ya en `main` (PR #53) | Worktree obsoleto |
| `ponderarPrueba` | `fix/ponderarAlTerminarLaPrueba` | Ya en `main` (PR #47) | Worktree obsoleto |
| `reload-skills-a6ac6d` | (sin rama) | Vacío | Cabeza suelta en `main`, sin trabajo |

**En una frase:** de trece worktrees del backend, **cuatro llevan trabajo real todavía fuera de `main`**,
**tres más guardan trabajo que ni siquiera tiene commit**, y **cinco son cáscaras** de ramas ya
incorporadas que sobreviven porque el *squash-merge* no las borra.

---

## 1 · Trabajo pendiente, con commits

### 1.1 Descargar un currículum deja de dar acceso a toda la empresa

**Rama:** `claude/elated-cannon-dcaad1` · 2 commits del 31/08 · 16 archivos, +529 / −59 · solo backend.

**El agujero:** un responsable de área podía bajarse el currículum de **cualquier** candidato de la
empresa, de la convocatoria que fuera. Los dos caminos que entregan un archivo —el enlace firmado y la
descarga de siempre— comprobaban que la persona *tuviera* el permiso de descargar entregables, pero
tiraban a la basura hasta dónde llega ese permiso. El permiso está sembrado como «solo lo suyo»; daba
igual, con tenerlo puesto bastaba.

**Por qué no se cerró en el PR #46:** aquel PR arregló este mismo patrón en otros nueve servicios, pero
allí siempre se partía de una postulación o de una vacante. Aquí no se podía: la fila de un archivo no
dice de quién es —solo su ruta, su tamaño y la organización—, así que faltaba el paso intermedio que
averigua a qué postulación pertenece. Eso es lo que añade la pieza nueva, `PostulacionDelArchivo`, por
los dos caminos: el currículum por sus **dos** columnas (la original y la anonimizada) y lo que se sube
en la prueba por su entregable y su intento.

**Detalles que también quedan cerrados:**

- Un archivo sin postulación detrás tampoco se entrega: sin dueño no hay alcance que comprobar.
- El «no existe» vuelve a hablar del archivo. Antes, quien pedía `/archivos/807` recibía un «Postulación
  4213 no existe» que confirmaba que el archivo existía y de regalo soltaba un identificador ajeno.
- El mismo patrón en **decidir**: el alcance sale ahora del permiso que se ejerce (decidir la contratación
  o cambiar la decisión) y no del de mirar el semáforo. Ahí no era solo un valor tirado — era el rechazo
  por no tener el permiso, porque es el único endpoint sin verificación fija.
- Se dejan fuera a propósito ver el banco de preguntas y elegir plantilla: listan instrumentos de la
  organización, que no cuelgan de ninguna vacante, así que no hay «vacante suya» que comprobar.

Las pruebas recorren los cuatro alcances por **las dos puertas** y contra el guardián de verdad, no
contra un doble que podría estar diciendo que sí cuando el cálculo real diría que no.

### 1.2 El módulo de vacantes (rama paraguas)

**Rama:** `feat/moduloVacantes` · backend 45 archivos, +5.088 / −117 · panel 28 archivos, +6.712 / −98.

⚠️ **No es una quinta funcionalidad.** Esta rama **contiene literalmente** los mismos commits de las tres
de abajo (`0198270`, `f92372c`, `599d4ed`, `2016575`) y les suma dos. En el panel es aún más literal: sus
dos commits de cabeza son mezclas de las otras ramas, sin una línea propia. Sus cifras **no se suman** a las de
las tres ramas: son las mismas líneas contadas otra vez.

Lo que aporta por su cuenta:

- **La fecha de cierre de la convocatoria ya no se acorta sola** (`2016575`). Con 260 líneas de pruebas
  nuevas sobre el reloj de la etapa técnica.
- **Dos defectos reales quedan escritos y desactivados** (`cbf9044`), a la espera de decisión, en vez de
  quedarse en la cabeza de alguien:
  1. Con fecha de cierre **y** minutos, el portal escribe «N minutos» y esconde la fecha: quien abre la
     prueba cerca del cierre lee más tiempo del que realmente tiene.
  2. El minuto en que se revela el cambio inesperado se sortea sin mirar cuánto dura de verdad la prueba,
     así que con pocos minutos **no se revela nunca** — y la instrucción del agente calificador dice que
     cómo se reaccionó a ese cambio forma parte de lo que se mide.

### 1.3 Administrar las áreas de la organización

**Rama:** `feat/crudAreas` · un commit `wip` · backend 10 archivos, +1.269 / −7 · panel 9 archivos, +1.442 / −4.

Hasta hoy las áreas se sembraban y ahí se quedaban. La rama abre el ciclo completo desde el panel:
listarlas todas (activas e inactivas), renombrarlas, desactivarlas, reactivarlas y borrarlas — pero antes
de borrar hay un paso de **impacto**, que responde a quién se lleva por delante ese borrado (solicitudes
y usuarios colgados del área) para que nadie lo descubra después. Pantalla nueva `Areas.tsx` dentro de
Configuración.

Todavía tiene cambios sin commitear encima (7 archivos en el backend, 2 en el panel).

### 1.4 Componer y editar una prueba desde el panel

**Rama:** `feat/editarPlantillasPrueba` · un commit `wip` · backend 22 archivos, +2.476 / −24 · panel 20 archivos, +5.100 / −86.

Es la rama más grande de las pendientes. Dos cosas distintas:

**Editar el instrumento.** Listar las versiones de una plantilla, modificar una, subirle la consigna,
quitarle preguntas y editar o quitar sus entregables. En el panel entra una pantalla de composición
entera (`ComponerPrueba`, `PlantillasDePrueba`, con borrador y cuotas) y su ruta propia.

**La guía de calificación (migración `V46`).** Cada prueba puede ahora decirle al agente que la califica
qué mirar. El texto con el que se habla al modelo vive en una tabla **sin organización**: hay una sola
instrucción activa por agente y la comparten todas las empresas. Esa instrucción global habla del oficio
de calificar —cita la evidencia, no supongas lo que no dijo—, y está bien que así sea, porque es lo único
que vale para todo el mundo: no puede decir qué distingue un buen tablero de control de uno malo en el
rubro de **esta** empresa. Quien sabe eso es quien escribió la prueba, y hasta hoy solo podía escribirlo
en el enunciado —que lo lee el candidato, no el modelo— o en la descripción de un criterio. La guía nueva
vive en la **versión** de la plantilla por la misma razón que la rúbrica: se congela al publicar y viaja
con la prueba cuando una empresa se lleva su copia. Una prueba publicada califica igual hoy que dentro
de un año, y eso incluye con qué guía se calificó.

Tiene 3 archivos sin commitear encima, entre ellos una prueba nueva contra una guía hostil.

### 1.5 El reloj de la etapa técnica

**Rama:** `feat/tiemposDeLaVacante` · 2 commits · backend 18 archivos, +1.436 / −90 · panel 5 archivos, +173 / −9.

El techo de duración se retiró —lo que dure la prueba lo decide quien la escribe— pero se puso un **suelo
de cinco minutos**, tanto al publicar una versión de plantilla como al fijar los minutos de la etapa en
la vacante. La razón es concreta: esos minutos convierten cualquier prueba en cronometrada, así que un
«1» ahí es una prueba que el barrido entrega sola sesenta segundos después de que el candidato la abra,
antes de que le dé tiempo a leer el enunciado. En el portal cambia la pantalla de evaluación.

---

## 2 · Trabajo sin un solo commit

Esto es lo más frágil del inventario: existe solo en el disco. Un `git checkout` mal dado se lo lleva.

### 2.1 La ciudad del candidato, el filtro y el Excel del ranking

**Worktree:** `rankingOrdenExcel` · **cero commits** · backend 23 archivos modificados + 10 nuevos (+488 / −28
en lo modificado) · panel 17 archivos modificados + carpeta `e2e/` y `playwright.config.ts` nuevos (+3.152 / −65).

Tres piezas encadenadas:

- **De dónde es quien postula.** Se pide al crear la cuenta, como código de ubigeo, con catálogo cerrado
  en lugar de texto libre. La razón está escrita en la propia migración: «Lima», «lima» y «Lima Cercado»
  son la misma ciudad y tres filtros distintos — y el campo de texto libre que ya existe lo demuestra,
  con seis filas que dicen «Arequipa, Perú» escrito seis veces a mano. El árbol es auto-referenciado a
  propósito: hoy entran los 25 departamentos y las 196 provincias, y el día que haga falta el distrito se
  insertan 1.874 filas más sin migrar la tabla de personas. La columna vieja **no se borra**: son datos
  que el candidato escribió sobre sí mismo, y una migración no es sitio para destruirlos.
- **El filtro del ranking por ciudad**, que es para lo que se pide el dato.
- **El Excel del ranking**, descargable desde el panel por vacante.

Además arrastra la infraestructura de pruebas end-to-end del panel (Playwright), que hoy no está
versionada.

### 2.2 El sembrador de datos de prueba

**Worktree:** `sembradorRoto` · **cero commits** · 1 archivo, +26 / −14.

El sembrador dejó de funcionar cuando el puesto pasó a nacer con la solicitud (PR #53): seguía mandando
el nivel y la familia por separado, y el backend los comparaba con los del puesto y rechazaba la solicitud
con un «el nivel enviado no coincide». El arreglo manda el puesto y calla los otros dos **a propósito**,
y lo deja escrito para que nadie los devuelva pensando que se habían olvidado.

De paso reparte los diez candidatos por ciudades distintas —dos de Lima para que haya un grupo con más de
uno, y uno de fuera del Perú— porque con diez candidatos limeños el filtro por ciudad se ve pero no se
puede probar.

⚠️ **Ese reparto depende de la ciudad, que solo existe en el trabajo sin commitear de 2.1.** El sembrador
no corre hasta que esa rama entre.

### 2.3 El cierre se explica por su motivo

**Worktrees:** `mensajeAgradecimiento-backend` y `mensajeAgradecimiento` · **cero commits en ninguno de los dos**
· backend 4 archivos (+47 / −3) más una migración nueva · panel 8 archivos (+199 / −52) más una prueba nueva.

Al candidato descartado se le cuenta por qué se cerró su proceso, en vez de dejarlo en un estado mudo.
Toca la máquina de estados y los datos que el portal entrega, y en el panel del candidato las dos
pantallas de procesos.

⚠️ **Este trabajo está desfasado.** La rama en sí ya está dentro de `main` (no tiene ni un commit por
delante), y lo que queda encima es una migración **`V37`** contra un `main` que va por la **`V45`**. Hay
que renumerarla y revalidarla antes de que sirva de algo.

---

## 3 · Worktrees obsoletos

Estas cinco ramas se comprobaron con `git diff main..rama`: **ninguna añade una sola línea que `main` no
tenga ya**. Sus commits siguen apareciendo como «por delante» porque el `squash-merge` reescribe el
contenido en un commit nuevo y deja los originales colgando de una historia que ya nadie mira.

| Rama | Entró como | Prueba de que está dentro |
|---|---|---|
| `feat/banco-cazatalentos` | PR #45 (27/08) | `V41__banco_cazatalentos.sql` está en `main` |
| `fix/ponderarAlTerminarLaPrueba` | PR #47 (28/08) | `PuentePruebaIaPonderaTest` está en `main` |
| `feat/prueba-tecnica` | PR #48 (28/08) | `V42__prueba_tecnica_ficha_y_redactor.sql` está en `main` |
| `codex/puesto-en-solicitud` | PR #53 backend, #24 panel (30/08) | diferencia con `main` **vacía** en los dos repositorios |
| `fix/mensajeAgradecimiento` (rama) | antecesor directo de `main` | `git log main..rama` vacío |

Lo que sí conservan es basura sin versionar: `scripts/espera-checks-pr.sh` sin seguimiento en dos de ellos.

Se pueden borrar sin perder nada — **salvo `mensajeAgradecimiento-backend`**, cuya rama está integrada
pero cuyo directorio guarda el trabajo sin commitear de 2.3.

---

## 4 · Riesgos que este recorrido dejó a la vista

### 4.1 Dos migraciones `V46` distintas, a la vez

`main` va por la `V45`. Y hay **dos** ramas reclamando el número siguiente:

- `feat/editarPlantillasPrueba` tiene **commiteada** `V46__la_prueba_orienta_a_quien_la_califica.sql`
  (y por tanto también `feat/moduloVacantes`, que la contiene).
- `rankingOrdenExcel` tiene **sin commitear** `V46__la_ciudad_del_candidato.sql`.

La primera que entre a `main` deja a la otra rota. Es exactamente el mismo choque de numeración que ya
se vivió con la `V37`. Hay que decidir el orden **antes** de mezclar, no después.

### 4.2 Una migración `V37` ocho números por detrás

La de 2.3, contra un `main` en `V45`. No es «trabajo sin commitear»: es trabajo que ya no encaja donde lo
dejaron.

### 4.3 El sembrador no corre hasta que entre la ciudad

Cadena de 2.2 → 2.1. Si entra el sembrador solo, falla; si entra la ciudad sin el sembrador, el filtro
nuevo no tiene datos con los que enseñarse.

### 4.4 Tres worktrees guardan trabajo que solo existe en el disco

`rankingOrdenExcel` —más de 3.600 líneas entre los dos repositorios **sin contar los archivos nuevos**,
que son la mayor parte: la migración de la ciudad, el servicio del Excel, el catálogo de ubigeo y toda
la carpeta `e2e/` del panel—, `sembradorRoto` y los dos de `mensajeAgradecimiento`. Nada de eso está en
ninguna rama publicada.

### 4.5 Dos defectos del reloj, conocidos y sin decidir

Los de 1.2. Están escritos como pruebas desactivadas con su explicación dentro, que es la forma correcta
de no perderlos, pero siguen abiertos: el portal promete más tiempo del que hay, y el cambio inesperado
puede no revelarse nunca en una prueba corta.

---

## Nota sobre los conteos de pruebas

Los mensajes de commit de las ramas ya integradas citan totales —794 en `feat/prueba-tecnica` el 28/08,
877 en `fix/ponderarAlTerminarLaPrueba` el 28/08, 982 en `claude/elated-cannon-dcaad1` el 31/08— que eran
ciertos **en su rama y en su fecha**. No son el total de hoy y no se deben citar como tal: en este
proyecto los conteos que se copian sin volver a medir es como se acumulan documentos que mienten. El
único de los tres que se midió sobre `main` ya rebasado es el de 982, contando los casos de surefire y
failsafe después del rebase.
