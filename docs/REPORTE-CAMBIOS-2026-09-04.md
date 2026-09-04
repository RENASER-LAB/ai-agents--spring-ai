# Reporte de cambios — 4 de septiembre de 2026

## Alcance

Un cambio, coordinado en los dos repositorios, entregado en dos ramas `feat/ponderadoParcial`:

- **Backend:** `RENASER-RECLUTAMIENTO`, desde `8e479ef`.
- **Frontend:** `RenaserOsPostulantes`, desde `8387fdf`.

No hay migración de base de datos: la cifra que se añade es derivada y no toca el esquema.

## Qué se pidió

Renaser pedía ver, **antes de terminar el embudo**, cómo va un candidato con lo que ya rindió:
currículum, banco de preguntas y prueba del puesto. Hoy la única cifra que suma etapas vive en el
semáforo de la Decisión y exige las cuatro: si falta una, no enseña nada. Como esas tres piezas son
exactamente 70 de los 100 del embudo, la cifra pedida es un reescalado de lo ya calculado.

## Qué se hizo

**El ranking de la pestaña «Prueba del puesto» trae una columna nueva: Ponderado.** Es
`(nota del perfil × su peso + nota de la prueba × su peso) ÷ la suma de esos dos pesos`. Con el
reparto de la v4 son 70 puntos estirados a 100; en una vacante que siga en la v3, el divisor es
otro, y por eso los pesos se leen de la versión **de la vacante** y no hay ningún 70 escrito en el
código.

- Solo en esa pestaña. En la del perfil, la mitad de la cuenta todavía no puede existir para nadie;
  en simulación y validación ya hay notas posteriores que la cifra ignoraría.
- Ocupa una columna de cifra, ordenable y apagable. **El desglose vive en el título**: las tres
  notas que hay detrás de la cifra.
- Sin alguna de las dos notas no hay cifra, sino un guion que dice **cuál falta**.
- Va también al Excel del ranking, en las dos hojas de Resumen.

## La decisión que cambió por el camino

Se pidió que el desglose enseñara la nota del **banco de preguntas** por separado. **No se hizo, y
la razón es que esa nota no existe como dato recuperable.** Lo guardado es su mezcla con el
currículum, en `nota_etapa`. Despejarla restando parecía gratis y da un número falso en dos casos
reales:

1. Quien no tiene evaluación asignada: su Perfil Integral **es** su nota de currículum, y la resta
   devolvería esa misma nota disfrazada de nota del banco.
2. Las vacantes que califica `CalificacionCriterios`: escriben en esa misma casilla un índice de
   pilares que no es CV + banco, y la resta sobre eso es basura con pinta de cifra.

Ninguno de los dos se detecta comprobando que el resultado caiga entre 0 y 100. Calcularla de
verdad exige mover aritmética de calificación que hoy es privada y leer del orden de 19.000 filas
por tanda: es otro trabajo. Así que el desglose enseña **currículum, perfil integral y prueba**,
que son exactas y con las que la cuenta se rehace igual.

## Lo que NO se tocó, a propósito

- **No hay `version_pesos` nueva.** El precedente de la V15 —que creó la v3 con 57.14/42.86— era
  correcto entonces porque simulación y validación no existían; repetirlo hoy reescribiría el
  global real 40/30/15/15 y rompería la trazabilidad del RF-139.
- **`ServicioDecisionImpl.calcular()` está intacto.** La Puntuación Global se sigue calculando y
  persistiendo igual, y los umbrales 75/50 del semáforo comparan lo mismo que antes. El ponderado
  no se guarda, no se compara con umbrales y no mueve a nadie de estado.

## Compatibilidad backend–frontend

| | Estado |
|---|---|
| Backend sin frontend | El campo viaja y nadie lo pinta. Sin efecto. |
| Frontend sin backend | El campo llega `undefined`; la columna sale con guion y su título dice que faltan las dos notas. Se declaró opcional y se consume con `!= null` justo para esto. |

El backend se integra primero.

## Validación realizada

| Comprobación | Resultado |
|---|---|
| `./mvnw test` (backend, suite completa) | En verde |
| `npm run typecheck` (panel) | Sin errores |
| `npm test` (panel) | 561 pruebas en verde |

Pruebas nuevas: que el divisor sale de los pesos de la vacante y cambia con el reparto; que sin una
de las dos notas no hay cifra; que una versión de pesos sin la prueba no revienta ni divide entre
cero; que los pesos se piden **una vez por tanda** y no una vez por fila; que la cifra sobrevive al
renumerado de las filas —el fallo más fácil de este cambio, porque el ranking reconstruye cada fila
campo a campo después de ordenar—; que la columna no aparece en las otras cuatro pestañas; y los
cuatro casos del texto del desglose.

## Documentación actualizada

- `docs/01-REQUISITOS-FUNCIONALES.md` — RF-155.
- `docs/09-APIS.md` — el campo en la respuesta del ranking y en el Excel, y la corrección de la nota
  que afirmaba que ninguna cifra mezclaba etapas.
- `docs/06-FLUJO-COMPLETO.md` (frontend) — la columna en «El ranking, etapa por etapa».

---

# Segunda entrega del día · el hueco que destapó el ponderado

La columna nueva no funcionaba en la vacante de Administración: enseñaba las dos notas y el
ponderado vacío. Investigándolo apareció algo bastante peor.

## La causa

La `V41` creó las dos versiones de pesos del cazatalentos —«CAZATALENTOS · MICRO» y
«CAZATALENTOS · MEDIA/GRANDE»— con sus pesos de pilar, pero **sin una sola fila de
`peso_etapa`**, y ninguna migración se la dio después. Una vacante apuntada a cualquiera de las
dos no tiene con qué mezclar la nota del Perfil Integral con la de la prueba.

## Lo que no se veía

La Puntuación Global de la Decisión suma las etapas **recorriendo esos mismos pesos**. Con la
tabla vacía el bucle no itera: la nota se queda en cero y la lista de etapas que faltan también,
o sea que el código concluye que no falta nada. Con nada pendiente compara ese cero contra los
umbrales y propone **ROJO**.

Un candidato con 81.75 en el perfil y 62 en la prueba salía descartado con un cero que nadie
calculó. No es una excepción que alguien vea: es un descarte con pinta de decisión fundada.
Confirmado con un test antes de tocar nada.

## Los dos arreglos

| | Qué |
|---|---|
| **PR #64** | Sin `peso_etapa` no hay semáforo ni nota, igual que cuando falta una nota, y queda un `log.error` con la vacante y la versión. |
| **V49** | Les da su reparto: **45 / 55**. |

## De dónde salen el 45 y el 55

Del cliente, textualmente y repetido cuatro veces —en `CAZATALENTOS-sistema-de-filtro.md` y en
la hoja «Cálculo» de los libros de DIR, SUP y OPE—:

> `Índice combinado = (Índice RENASER × 0.45) + (Índice técnico × 0.55)`
> «La técnica pesa más porque mide si sabe hacer el trabajo.»

Su prueba RENASER se guarda como `PERFIL_INTEGRAL` y la técnica como `PRUEBA_PUESTO`, así que
el reparto se traduce directo. Solo esas dos etapas: su material describe un embudo de dos fases
y no menciona simulación ni validación en ningún punto.

**No se reescaló el 40/30 del embudo genérico**, que habría dado 57.14/42.86 como hizo la V15
para la v3. Apunta al revés —ahí el perfil pesa más que la prueba— y el cliente pidió lo
contrario para su instrumento, con su justificación escrita.

El `peso_componente_perfil` va entero a `EVALUACION`: en el cazatalentos el Perfil Integral **es**
la prueba RENASER, no hay currículum que ponderar. Sin esas filas la versión no pasaría su propia
validación al publicarse.

## Validación

Migración aplicada sobre una base limpia, datos sembrados por la API y una vacante apuntada a
«CAZATALENTOS · MICRO»:

| | Antes | Después |
|---|---|---|
| Ponderado del ranking | — | **70.89** |
| Semáforo de la Decisión | ROJO · nota 0 | **AMBAR · nota 70.89** |

`./mvnw test`: 1033 en verde.

## Lo que NO se hizo, y por qué

**Las dos versiones se quedan en BORRADOR.** La intención era publicarlas —en borrador no se les
puede asignar una vacante— y los tests de integración lo impidieron con razón.

`findFirstByOrganizacionIdAndEstadoOrderByPublicadaEnDesc` es «la versión publicada más reciente:
la que rige una vacante nueva si nadie elige otra», y también la que el copiador replica a cada
empresa nueva. Publicadas hoy, estas dos pasan a ser las más recientes: **cualquier vacante nueva
heredaría 45/55 sin simulación ni validación**, en silencio. `FlujoPruebaIT` lo detectó al
instante — su vacante dejó de pesar las cuatro etapas y el semáforo dejó de avisar de las que
faltaban.

El criterio «la última publicada» da por supuesto que todas las versiones publicadas son
intercambiables, y con el cazatalentos deja de serlo: hay versiones que solo sirven para un
instrumento. Arreglarlo toca cómo se elige la versión de una vacante nueva **y** de una empresa
nueva, así que va en su propio cambio. Los pesos ya están, que era el hueco que dejaba a estas
vacantes sin ponderado y sin semáforo; publicarlas es el paso siguiente.

## Nota de proceso

`./mvnw test` no basta en este repositorio: el CI corre `verify`, que añade los 143 tests de
integración, y son justo los que ven un efecto a distancia como este. El PR salió en rojo por
haberlo comprobado solo con los unitarios.
