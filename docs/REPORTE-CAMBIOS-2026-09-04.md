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
