# Banco RENASER v3 · estado al 19/08/2026

El cliente mandó el banco nuevo (`docs/insumos/banco-renaser-v3-completo.pdf`, 62 páginas):
**190 ítems cerrados** repartidos en tres bancos, con ocho formatos y un motor de puntuación
propio. Sustituye al banco v0.1 que hoy está en la base.

**Hecho:** la extracción, la migración de reemplazo, el examen de banco completo, las siete
fórmulas, el guardado de las respuestas v3 y el motor cableado sobre esas fórmulas.
**Pendiente:** los ítems `V`, la validación de campos de los `CD`, los multiplicadores, los
umbrales, los filtros eliminatorios y los pares de consistencia del v3.

**Estado de las pruebas:** 191 en verde (149 unitarias + 42 de integración), `BUILD SUCCESS`.

---

## Lo que ya está

`scripts/importar-banco-v3.py` lee el PDF y emite JSON revisable:

```bash
python3 scripts/importar-banco-v3.py         > /tmp/banco-v3.json   # para revisarlo
python3 scripts/importar-banco-v3.py --sql   > /tmp/banco-v3.sql    # lo que va en la migración
```

Termina en 0 y sin avisos. No toca la base: la salida se revisa y de ahí sale la migración,
igual que hizo `importar-banco-maestro.py` con el v0.1.

**Las cuatro comprobaciones** salen de la sección 0.3 del propio documento y se recalculan
desde lo parseado. Si el parser lee mal un peso o una clave, dejan de cuadrar:

| Banco | Ítems | Puntúan | Clave ★ | Máximo |
|---|---:|---:|---:|---:|
| Directivo | 85 | 81 | 15 | 288 |
| Coordinación / Supervisión | 55 | 52 | 10 | 186 |
| Ejecutivo / Operativo | 50 | 47 | 9 | 168 |

Dos invariantes comprobadas, que ahorran trabajo más adelante:

- **`peso 2` ⟺ `★`**: no hay ni un ítem clave con peso distinto de 2, ni al revés.
- **`máximo = Σ(peso × 3)`** reproduce 288 / 186 / 168 exactos. Es lo que confirma que peso 1
  vale hasta 3 puntos y peso 2 hasta 6.

**Lo que "todo cuadra" no significa:** que una persona haya leído los 190 ítems contra el PDF.
Significa que lo parseado es coherente con lo que el documento declara de sí mismo. Antes de
usar esto con candidatos reales conviene revisar a mano unas cuantas claves de `SJT-R` y `EF-4`.

---

## Las decisiones que ya se tomaron

1. **El v3 entra como `version_banco` nueva y la v0.1 se retira.** Las plantillas de evaluación
   pasan a apuntar al v3; la v0.1 queda en la base sin borrarse. Las postulaciones ya evaluadas
   conservan su versión, que es lo que exige RF-138.
2. **Se implementa también el motor de puntuación**, no solo la carga del banco.

---

## La migración · `V20__banco_v3_y_motor_de_puntuacion.sql`

Escrita y **aplicable**: `MigracionesIT` aplica las 20 migraciones en una base limpia y pasa,
y los 106 tests unitarios siguen en verde. Todo lo que hace es aditivo — no borra ni una fila
ni una columna. Pero **rompe tres pruebas de integración**, así que todavía no vale.

Lo que carga:

| Tabla | Filas |
|---|---:|
| `version_banco` | 3 (una por nivel de puesto) |
| `pregunta` | 190 (85 + 55 + 50) |
| `opcion` | 449, más 40 pasos de los ítems `SEC` |
| `rango_pregunta` | 45 tramos de los ítems `V` |
| `campo_caso` | 175 campos de los ítems `CD` |
| `par_consistencia` | 5 |
| `umbral_nivel` | 4 por banco |
| `filtro_eliminatorio` | 5 por banco |
| `multiplicador_bloque` | 20 (4 familias × 5 bloques A) |

**Cómo se retira el v0.1 sin tocarlo:** las tres versiones del v3 entran `PUBLICADA` con la
fecha de hoy, y el código pide la publicada **más reciente** de cada nivel
(`VersionBancoRepository.findFirstBy...OrderByPublicadaEnDesc`). Las evaluaciones nuevas cogen
el v3; las ya hechas siguen atadas a la suya. No hizo falta cambiar ningún estado ni saltarse
RF-138.

**Dos cosas que quedaron a medias a propósito:**

- `multiplicador_bloque.familia_codigo` va vacío. El documento nombra cuatro familias —Obras /
  Proyectos, Recursos Humanos, Marketing / Comercial, Administración / Finanzas— que no son las
  siete del catálogo `familia` (TALENTO, OPERACIONES, TECNOLOGIA...). **Falta que Renaser diga
  cuál es cuál**; hasta entonces se guarda la etiqueta del documento tal cual.
- Los filtros `SENTIDO_CRITICO` e `INFLACION` no apuntan a ítems concretos porque miran el
  formulario entero. Los aplica el motor.

### Si hubiera que hacer otra migración

**Antes de numerarla, mirar cuál es la más alta que existe** (`ls src/main/resources/db/migration
| sort -V | tail -1`). Hay más de una persona creando migraciones y Flyway no arranca con dos
del mismo número.

Cambios aditivos sobre lo que ya hay:

- `pregunta`: añadir `peso` (0, 1 o 2) y `es_clave`. Hoy solo existe `es_puntuable` sí/no, que
  no distingue el peso 1 del 2. Para las filas del v3, `es_puntuable` se deriva como `peso > 0`,
  así las consultas del motor viejo siguen funcionando.
- `pregunta.tipo`: **el CHECK se borra y se vuelve a crear con los seis de antes MÁS los ocho
  nuevos** (`EF-4`, `SJT-R`, `SEC`, `INV`, `DE`, `CD`, `V`, `PC`). Quitar los seis viejos dejaría
  huérfanas las 200 filas ya publicadas, y `ddl-auto: validate` no avisa de eso: solo mira que
  las entidades cuadren con sus columnas.
- `opcion`: `valor` (EF-4 va de −2 a +2), `es_distractor` (los elementos falsos de INV) y
  `orden_correcto` (la clave de SEC).
- Tablas nuevas: las tablas de rango de `V`, los campos de `CD` con sus validaciones, los
  multiplicadores por familia de puesto, los umbrales de nivel (I, II, III) y los cinco filtros
  eliminatorios.
- Los filtros eliminatorios **van como datos**, no como constantes en Java: apuntan a códigos de
  ítem concretos (D70 · C44 · O39, D52 · C33 · O19, D65 · C42 · O38) y en este proyecto esa clase
  de cosa vive en la base.
- `par_consistencia` **no sirve tal cual** para los `PC` del v3: tiene `diferencia_maxima`, y el
  v3 dice otra cosa (−5% del puntaje global + bandera roja, y el par se muestra al menos 15 ítems
  después de su pareja). Necesita columnas nuevas o una tabla aparte.

### Motor

**Se reemplaza el motor viejo, no se conserva junto al nuevo.** Durante un rato el plan fue
tener dos motores y elegir por versión de banco; eso valía mientras el banco v0.1 siguiera en
la base. Al decidirse el reemplazo completo, dejó de tener sentido: no queda ni una pregunta
del v0.1 ni una sola respuesta guardada, así que el motor viejo no tendría nada que calificar.

Y dejarlo sería peor que inútil: `puntuar` suma `opcion.puntaje`, y en el v3 esa columna
guarda la **calificación esperada de 1 a 5** de los SJT-R. Sumarla da un número creíble y
equivocado, que es la peor clase de error: no falla, miente.

La interfaz `ServicioCalificacion` se queda como está —sus tres llamadas siguen compilando
igual— y lo que cambia es la implementación.

---

## Nota sobre el parser

Seis ítems fallaron al principio y los seis resultaron ser **variantes reales del documento**,
no erratas: opciones escritas en el párrafo en vez de en tabla, un `V` que da la fórmula en
lugar de la tabla, otro que remite a la tabla de otro ítem, y un `PC` que describe la regla sin
usar la palabra «Contradicción». Por eso el script comprueba totales en vez de confiar en que
el documento sea regular.


---

## Lo que falta por arreglar antes de seguir

Con la V20 puesta, `./mvnw verify` deja tres clases en rojo. Sin ella pasaban las 148.

| Prueba | Qué pasa |
|---|---|
| `FlujoEvaluacionIT` | `hayUnBancoDePreguntasDeVerdad` cuenta `select count(*) from pregunta` y espera **200**; ahora hay **390**, porque el v3 suma 190 |
| `FlujoEvaluacionIT`, `FlujoCalificacionIaIT`, `FlujoPruebaIT` | Al responder y entregar la evaluación devuelven **409** donde esperaban 200. Salta en `responderYEntregar` |

**El primero es fácil y es culpa del test, no de la migración**: cuenta el total de preguntas
de la base entera en vez de las del banco que esa prueba usa. Añadir un banco nuevo cambia ese
número legítimamente. Se arregla contando las del v0.1.

**El segundo no está diagnosticado.** Lo que ya se descartó:

- No es por publicar el banco. Las tres versiones del v3 se pasaron a `BORRADOR` —de modo que
  el selector sigue devolviendo el v0.1— y el 409 sigue igual.
- No es la carga en sí: la migración se aplica sin error y los 106 unitarios pasan.
- **No es de otra rama ni de otra sesión.** Se comprobó apartando el archivo de la V20 y
  volviendo a correr `FlujoCalificacionIaIT`: con la migración fuera pasan sus 7 pruebas; con
  ella puesta fallan 5. La causa está en la V20, no en el código de alrededor.

Por dónde seguir: sacar el cuerpo del 409 (`MockMvc` solo enseña el código), mirando
`FlujoCalificacionIaIT:923` y el servicio que atiende la entrega. La hipótesis a comprobar
primero es que el examen se arma con más preguntas de las que la prueba responde, o que alguna
consulta que antes veía un solo banco ahora ve dos.

**Hasta que eso esté resuelto, la V20 no debe llegar a `main`**: la tubería la rechazaría, y
con razón.


---

## La deuda del jsonb

**Decisión tomada el 19/08/2026: el detalle de las respuestas se guarda en una columna `jsonb`
(`respuesta.detalle`, migración V21), y hay que pasarlo a tabla más adelante.**

Se eligió así para avanzar. La forma correcta es
`respuesta_detalle(respuesta_id, opcion_id, valor, orden)`: una fila por cada valor que compone
la respuesta. Lo que cuesta la decisión, en concreto:

| | Con `jsonb` (hoy) | Con tabla (lo que toca) |
|---|---|---|
| Apuntar a una opción de otra pregunta | Nada lo impide en la base | La clave foránea lo impide |
| Forma equivocada para el formato | Entra igual | No cabe |
| Informes que cruzan respuestas | Sintaxis JSON de Postgres | SQL normal |

Esa integridad la sostiene ahora **`ValidadorDetalleV3`**, con 21 pruebas dedicadas casi todas
a lo que debe rechazar. Mientras el detalle no sea una tabla, esa clase es lo único que separa
una respuesta mal formada de una nota calculada sobre ella: si se toca, mirar sus pruebas antes.

**Al migrar a tabla:** pasar lo que haya en `detalle`, borrar la columna y quitar la validación
que la base ya haría sola.

### La forma que se guarda hoy

```
EF-4    {"mas": 12, "menos": 15}                  ids de opción, distintos
SJT-R   {"calificaciones": {"12": 5, "13": 2}}    id de opción -> 1..5
SEC     {"orden": [14, 12, 13, 15, 16]}           ids en el orden elegido
INV/DE  {"marcadas": [12, 14, 17]}                ids de lo marcado
CD      {"campos": {"1": "...", "2": "..."}}      nº de campo -> lo que puso
```

Los ítems `V` y `PC` no usan detalle: siguen respondiéndose con `opcion_id` o `texto`.

---

## El obstáculo que había · cómo se guardan las respuestas

Las siete fórmulas están implementadas y probadas (`FormulasBancoV3` y su test), pero **todavía
no se pueden conectar al flujo**, y no es cuestión de escribir más código: falta esquema.

La tabla `respuesta` guarda **una opción por pregunta** (`opcion_id`) más un texto. Al banco
v0.1 le bastaba: se elegía una opción y ya. Los formatos del v3 no caben ahí:

| Formato | Lo que el candidato contesta | ¿Cabe en `respuesta`? |
|---|---|---|
| `SJT-R` | Una calificación de 1 a 5 **por cada opción** | No |
| `EF-4` | **Dos** opciones: la más parecida y la menos | No |
| `INV`, `DE` | Un **conjunto** de elementos marcados | No |
| `SEC` | Los cinco pasos **ordenados** | No |
| `CD` | Un **valor por cada campo** del caso | No |
| `V` | Un dato suelto | Sí, en `texto` |

Hace falta una tabla de detalle —algo como `respuesta_detalle(respuesta_id, opcion_id, valor)`—
y con ella cambian también el endpoint por el que el candidato responde y su DTO. Es decir:
**toca la API del portal**, no solo el motor. Por eso se paró aquí en vez de improvisarlo.

Mientras tanto el sistema sigue funcionando: se arma el examen v3 completo, se responde y se
califica con el motor viejo. Lo que no está es la puntuación v3 de verdad.

## Lo que queda, en orden

1. Migración con `respuesta_detalle` y el endpoint de responder adaptado.
2. Reescribir `ServicioCalificacionImpl` sobre `FormulasBancoV3`: sumar con pesos, aplicar el
   multiplicador de bloque según la familia, sacar el porcentaje sobre el máximo del banco y
   traducirlo a nivel I/II/III con `umbral_nivel`.
3. Los cinco filtros eliminatorios y las banderas (inflación, rango implausible).
4. Reescribir `detectarContradicciones` a la regla v3: −5% del global y bandera roja, en vez de
   la `diferencia_maxima` del v0.1, que los pares nuevos no usan.
