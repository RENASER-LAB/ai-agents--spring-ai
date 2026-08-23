# El importador del banco desde Excel · 23/08/2026

El banco de preguntas entra ahora por el panel. El administrador llena la plantilla
(`docs/insumos/plantilla-banco-de-preguntas.xlsx`), la sube eligiendo el rol, y sale una
versión en **borrador** que revisa y publica con el ciclo de siempre. Hasta hoy ese viaje
solo lo hacía un script de desarrollo (`scripts/cargar-banco-desde-excel.py`), que se
queda como está: sirve para cargar un banco en la base local sin levantar el panel.

## Cómo se reparte el trabajo

**`LectorPlantillaBanco`** lee el archivo y no toca la base. Devuelve las filas planas o
la lista completa de problemas —hoja, fila y mensaje—, porque a quien sube 190 preguntas
le sirve el parte entero y no un rechazo por entrega. Nunca lanza por un error de
contenido: hasta un `.docx` renombrado sale como un problema más de la lista, jamás un 500.

**`ServicioImportacionBanco`** inserta por lotes en una transacción, con una sola fila de
auditoría de resumen. No pasa por `crearPregunta`/`agregarOpcion` a propósito: fila a fila
serían cientos de lecturas de guarda y cientos de filas de auditoría para un banco de 190
preguntas, y lo que esas guardas comprueban ya lo comprobó el lector sobre el archivo
completo.

**`validarCoherencia` no se tocó.** Sigue siendo la única aduana de completitud por
formato y corre al publicar. El reparto es deliberado: el lector cuida lo que rompería un
INSERT o es estructural del archivo (duplicados, referencias entre hojas, rangos
numéricos, columnas que no tocan a ese formato, el N° de campos de un CD contra su hoja);
la completitud de cada formato se comprueba cuando el banco está entero, que es al
publicar. Un borrador puede quedar a medias a propósito.

## El archivo tiene dos trajes

Verificado leyendo los dos: la **plantilla vacía** trae los encabezados en la fila 4, una
fila de guía, ejemplos grises y una fila centinela «⬇ Escribe aquí lo tuyo»; los **bancos
que volcamos** traen los encabezados en la 3 y los datos desde la 5. El lector no asume
ninguna fila: busca el encabezado por su primera columna, salta siempre la guía que le
sigue, y si hay centinela empieza después de ella —lo de en medio son los ejemplos—.

Límite conocido: si alguien borra la centinela pero deja los ejemplos grises, entran como
datos. Los códigos duplicados los delatan casi siempre.

## Lo que la plantilla no pregunta y se deriva

| Columna del Excel | Campo | Cómo |
|---|---|---|
| Peso | `peso` y `es_puntuable` | `esPuntuable = peso > 0`: es la única lectura que `validarCoherencia` no rechaza |
| (ninguna) | `orden` | La posición de la fila |
| (ninguna) | `opcion.letra` | `a`, `b`, `c`… por orden de fila dentro de cada pregunta |
| (ninguna) | `campo_caso.orden`, `rango.orden` | Igual: la posición dentro de su pregunta |
| Nota interna | `logica_interna` | Entra y nunca sale (RF-53) |
| Qué mide | `pregunta_dimension` | Códigos o nombres del catálogo, sin distinguir mayúsculas ni acentos |
| Pares (códigos) | `pregunta_a_id`, `pregunta_b_id` | Segunda pasada, cuando las preguntas ya tienen id |

`bloque`, `es_clave`, `rangos_de_pregunta_codigo` y `formula_puntaje` no viajan en el
Excel: quedan vacíos y se completan desde el panel si hacen falta.

## La edición, y dónde está la frontera

- **BORRADOR**: se edita entero. PUT y DELETE de preguntas, opciones, campos, rangos y
  pares, y DELETE de la versión completa. El borrado del borrador es físico —la única
  cosa del banco que se borra— y puede serlo porque un borrador nunca se le asignó a
  nadie: lo que el append-only protege es lo que alguien pudo ver.
- **PUBLICADA**: solo `PATCH .../textos`. Corregir la errata de un enunciado que el
  candidato está leyendo es legítimo; mover una clave bajo un examen en curso, no
  (RF-138). Esa frontera no la vigila un `if`: los DTOs `Corregir*` **no llevan ni un solo
  campo de clave**, así que por esa puerta no entra. Es de Dirección, que es quien publica.
- **ARCHIVADA**: nada. Es la historia de quien ya respondió.

El candado que ya existía sigue igual: añadir una opción a una versión publicada devuelve
409, y su prueba de regresión no se tocó.

## Qué lo vigila

- `LectorPlantillaBancoTest` y `ServicioImportacionBancoImplTest`: los xlsx se fabrican
  con POI en el propio test, uno por defecto que se quiere ver rechazado.
- `ServicioBancoPreguntasImplTest`: los 18 casos que ya había siguen intactos, más los de
  la edición y la corrección editorial con sus 409.
- `FlujoImportadorBancoIT`: el viaje entero por la API, y una ida y vuelta con
  `docs/insumos/banco-v3-directivo.xlsx` —el archivo real del cliente— comprobando sus
  85/347/119/32/3 y que los textos son los mismos que sembró la V20.

## Lo que queda fuera

La pantalla del panel (esto es solo backend), y la puntuación fina que describen las
reglas de `logica_interna` (índice de apagaincendios, campo válido doble). Cero
migraciones: el importador no cambió el esquema.
