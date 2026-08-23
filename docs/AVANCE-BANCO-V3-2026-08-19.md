# Banco RENASER v3 · estado al 19/08/2026

El cliente mandó el banco nuevo (`docs/insumos/banco-renaser-v3-completo.pdf`, 62 páginas):
**190 ítems cerrados** repartidos en tres bancos, con ocho formatos y un motor de puntuación
propio. Sustituye al banco v0.1 que hoy está en la base.

**Hecho:** la extracción, la migración de reemplazo, el examen de banco completo, las siete
fórmulas, el guardado de las respuestas v3, el motor cableado sobre esas fórmulas y —desde el
19/08 por la tarde— **la administración completa del banco por la API del panel**: crear una
versión con los ocho formatos, sus claves, tramos, campos y pares, publicarla (con validación
de coherencia y relevo automático de la saliente) y archivarla. El banco v4 que venga no
necesitará una migración. Ver la sección del banco en `docs/09-APIS.md`.
**Pendiente:** puntuar los ítems `V`, la validación de campos de los `CD`, los multiplicadores,
los umbrales, los filtros eliminatorios y la regla v3 de los pares de consistencia.

**Estado de las pruebas:** 238 en verde (190 unitarias + 48 de integración), `BUILD SUCCESS`.

---

## Lo que ya está

`scripts/importar-banco-v3.py` lee el PDF y emite JSON revisable:

```bash
python3 scripts/importar-banco-v3.py         > /tmp/banco-v3.json   # para revisarlo
python3 scripts/importar-banco-v3.py --sql   > /tmp/banco-v3.sql    # lo que va en la migración
```

No toca la base: la salida se revisa y de ahí sale la migración, igual que hizo
`importar-banco-maestro.py` con el v0.1. Hoy termina en 1 con una docena de avisos **sabidos y
tolerados** (los cuatro EF-4 que en el documento arrancan directo en su tabla, los PC que no
titulan su regla, y los dos enunciados que el parser no sabe leer enteros): son cosas a mirar,
no fallos nuevos. Un aviso que no esté en esa lista sí lo es.

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

Escrita y **aplicable**: `MigracionesIT` aplica las migraciones en una base limpia y pasa, y
`MigracionPorFasesIT` la aplica además sobre una base que ya venía con datos, que es donde el
despliegue del 19/08 la atrapó. En esquema todo es aditivo — no quita ni una fila ni una
columna; el banco v0.1 no se borra: se archiva. Las tres pruebas de integración que rompía por
la mañana quedaron resueltas ese mismo día (ver el final del documento).

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

**Cómo se retira el v0.1:** la V20 lo **archiva** (`estado = 'ARCHIVADA'`, un valor que esa
misma migración añade al CHECK) y el selector pide la `PUBLICADA` más reciente de cada nivel
(`VersionBancoRepository.findFirstBy...OrderByPublicadaEnDesc`), así que un banco archivado no
vuelve a asignarse. Las evaluaciones que no habían empezado se repuntan al v3; a las que sí, se
les vence el plazo para que el sondeo las cierre — el motor v3 no sabe puntuar preguntas sin
peso y les habría puesto un 0.00 de verdad. La primera versión de la V20 borraba el banco viejo
apoyándose en que no había respuestas: era cierto en local y falso en Pruebas (249 evaluaciones,
16 respuestas), y el despliegue del 19/08 murió contra `postulacion_evaluacion_fk`. RF-138
intacto: a quien ya fue evaluado no se le toca su banco.

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

## Los tres rojos de la mañana, y cómo se resolvieron (mismo día)

Con la V20 recién puesta, `./mvnw verify` dejaba tres clases en rojo. Quedó resuelto el mismo
19/08 y la suite completa está en verde; se deja el resumen porque las dos causas enseñan algo:

- **El conteo global de `pregunta`** (`hayUnBancoDePreguntasDeVerdad` esperaba 200 y había 390):
  culpa del test, que contaba la tabla entera. Hoy cuenta solo los bancos `PUBLICADA` — con el
  v0.1 archivado, cualquier consulta global sobre `pregunta` arrastra instrumentos retirados.
- **El 409 al responder y entregar**: el examen del v3 se arma con el banco entero y sus
  formatos se responden con `detalle`, así que las pruebas que respondían al estilo v0.1
  entregaban a medias. Se diagnosticó trazando el recorrido petición por petición (`TrazaHttp`)
  y las pruebas se adaptaron a los formatos v3 (`RespuestaV3`).


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

1. Migración con `respuesta_detalle` y el endpoint de responder adaptado (la más alta hoy es V21).
2. Reescribir `ServicioCalificacionImpl` sobre `FormulasBancoV3`: sumar con pesos, aplicar el
   multiplicador de bloque según la familia, sacar el porcentaje sobre el máximo del banco y
   traducirlo a nivel I/II/III con `umbral_nivel`.
3. Los cinco filtros eliminatorios y las banderas (inflación, rango implausible).
4. Reescribir `detectarContradicciones` a la regla v3: −5% del global y bandera roja, en vez de
   la `diferencia_maxima` del v0.1, que los pares nuevos no usan.

---

## Los textos cortados · añadido el 21/08/2026

**Qué se encontró.** La importación del PDF dejó texto cortado por el ancho de página en tres
sitios, y ninguna comprobación lo vio: **24 etiquetas de `campo_caso`** a media lista de
alternativas («medio día / un día o» sin el «más)», en C03, D05, D14, D22, D34 y O03),
**29 enunciados de `pregunta`** a media frase —texto que el candidato lee tal cual—, y el
defecto inverso: en los **31 CD "sueltos"** el primer campo se guardó con la pregunta entera
pegada delante. En un CD las alternativas viven solo en la etiqueta (no hay filas en
`opcion`), así que lo cortado no estaba en ninguna otra parte del esquema.

**Por qué no lo vio nadie.** Las cinco comprobaciones del importador cuentan y no leen: siete
campos cortados siguen siendo siete campos, y todos los totales cuadraban. El mismo agujero
por el que pasaron las opciones ausentes que arregló la V25.

**Qué lo arregla.**

- `scripts/importar-banco-v3.py`: la rama numerada de los CD pliega las líneas de
  continuación (`campos_numerados`), la rama suelta separa el preámbulo del primer campo, y
  una **sexta comprobación** avisa de cualquier paréntesis sin pareja.
- **`V28__etiquetas_y_enunciados_cortados_del_banco_v3.sql`** repara las bases ya cargadas:
  completa con `starts_with` (nunca pisa una edición del panel) y recorta con igualdad
  exacta del texto viejo.
- `scripts/comparar-banco-v3-con-base.py` compara desde ahora lo guardado contra el PDF,
  texto a texto, y es la red que las comprobaciones de conteo no pueden ser:

```bash
python3 scripts/comparar-banco-v3-con-base.py            # sale 0 si todo casa con el PDF
python3 scripts/comparar-banco-v3-con-base.py --emitir-v28   # las tuplas, si hiciera falta otra vez
```

- `MigracionesIT` asevera desde CI que ningún texto del banco v3 queda con paréntesis sin
  pareja, que es la firma del corte.

Diez enunciados no los sabe leer enteros el parser (rompen en un «·» de la línea de
continuación, o absorberían la fórmula): su texto se decidió leyendo el PDF y vive por
duplicado en la V28 §3 y en `ENUNCIADOS_A_MANO` del comparador, que vigila que no se separen.

## El salto de página invisible · añadido el 22/08/2026

Al volcar el banco a Excel, la librería se negó a escribir cinco etiquetas de campo: dentro
del texto viajaba el salto de página del PDF (`\f`, 0x0c), invisible en pantalla desde la
V20. Eran D41.3, D74.4, C48.3, O21.2 y O41.2 —campos de la rama suelta que cruzaban de
página a media frase— y con ellos, diez campos con un espacio doble dejado por el mismo
cruce (el salto de línea más la sangría de la página siguiente).

Tres piezas, mismas del arreglo anterior:

- `scripts/importar-banco-v3.py` vuelve el `\f` un espacio al leer el PDF (un solo punto,
  no en cada lector) y aplana toda corrida de blancos en la rama suelta. Además avisa si
  algún carácter de control llegara a un texto: no debería saltar nunca, está para el día
  en que alguien cambie la extracción.
- La **V33** limpia lo que ya estaba en la base: 14 filas de `campo_caso`, con un WHERE que
  solo alcanza filas aún sucias (re-ejecutable, y respeta ediciones del panel).
- `MigracionesIT` asevera desde CI que ningún texto del banco v3 trae caracteres de
  control ni espacios dobles.

Tras la V33, los 175 campos del importador y los de la base son idénticos carácter a
carácter — ya no solo con la comparación normalizada del comparador.
