# Reporte completo de commits — 4 de septiembre de 2026

## Alcance

Revisión de los commits disponibles en `origin/main` de backend y frontend, con hora local de Lima
(`UTC-05:00`):

- **Backend:** `/home/n4nd0/Documentos/RENASER-RECLUTAMIENTO` — `origin/main` en `06d2de4`.
- **Frontend:** `/home/n4nd0/Documentos/RenaserOsPostulantes` — `origin/main` en `1a1f552`.

Se revisaron los commits del 04/09/2026 entre `00:00` y `24:00`. No se pudo actualizar `origin/main`
con `git fetch` porque ambos repositorios tienen `.git/FETCH_HEAD` en solo lectura; el reporte usa las
referencias remotas locales disponibles. Los cambios locales existentes fueron preservados.

## Resumen ejecutivo

Hoy se integraron cuatro commits relacionados con el ranking y la ficha del candidato:

1. El backend calcula el **ponderado de lo ya rendido**: Perfil Integral y prueba del puesto, reescalados según los pesos reales de cada vacante.
2. El frontend muestra ese ponderado únicamente en la pestaña `PRUEBA_PUESTO`, permite ordenarlo y explica los datos incompletos.
3. La ficha del Perfil Integral ahora muestra las frases y evidencias de los hallazgos marcados por la IA.
4. Se evita mostrar el título “Hallazgos” cuando los datos recibidos no contienen ningún hallazgo visible.

El backend y frontend son compatibles por contrato. La compilación y el chequeo de tipos pasan; las
pruebas automatizadas no pudieron ejecutarse completamente por limitaciones del entorno local.

## Commits integrados en `main`

| Hora | Repo | Commit | Cambio |
|---|---|---|---|
| 15:07 | Frontend | `ba9980f` (#33) | La ficha del perfil integral muestra qué marcó la IA |
| 15:34 | Frontend | `09333e8` (#35) | El titular “Hallazgos” no aparece sobre una lista vacía |
| 15:52 | Backend | `06d2de4` (#63) | El ranking enseña el ponderado de lo ya rendido |
| 16:04 | Frontend | `1a1f552` (#34) | La tabla muestra el ponderado de lo ya rendido |

---

## Backend — `RENASER-RECLUTAMIENTO`

### `06d2de4` — PR #63

- Se añadió `Ponderado` dentro de cada `FilaRanking`, con `sobre100`, `cv`, `perfil` y `prueba`.
- La fórmula combina la nota del Perfil Integral y la de la prueba, usando la suma de sus pesos y no un valor fijo como 70.
- Los pesos se leen desde la versión de pesos de la vacante, manteniendo el comportamiento correcto para vacantes antiguas.
- Si falta alguna de las dos notas, o alguno de los pesos es nulo o cero, no se inventa un resultado.
- El cálculo se realiza por tanda, evitando consultas individuales por candidato.
- El dato se conserva cuando el ranking renumera las filas después de ordenar.
- El Excel de la prueba añade el ponderado y su desglose. El Excel del Perfil Integral no lo exporta porque allí todavía no representa una comparación válida.
- Las celdas sin cifra indican “falta una nota de etapa”, no “rúbrica incompleta”.
- No se modifica la Puntuación Global, el semáforo ni los estados de las postulaciones; el ponderado no se persiste.
- Se actualizó la documentación funcional y de API con el nuevo campo y sus reglas.

## Frontend — `RenaserOsPostulantes`

### `ba9980f` — PR #33 — Hallazgos legibles

- Se corrigió el contrato para usar `descripcion`, `evidencia`, `esCanalizable` y `sugerencia` del backend, en lugar del campo inexistente `texto`.
- La ficha muestra ahora la frase del hallazgo y la evidencia que la respalda.
- Los hallazgos se agrupan por tipo: fortalezas, riesgos críticos, riesgos desarrollables y faltas de evidencia.
- `PREFERENCIA` y las alertas se conservan en la respuesta, pero no se muestran en este bloque porque no son un veredicto.
- Los hallazgos no se muestran en la ficha de la prueba del puesto para evitar mezclar fuentes.

### `09333e8` — PR #35 — Sin titular vacío

- Se centralizó el filtro de hallazgos visibles.
- El título “Hallazgos” solo aparece cuando hay al menos un hallazgo que la pantalla enseña.
- Se cubrió el caso en que la IA devuelve únicamente una `PREFERENCIA`.

### `1a1f552` — PR #34 — Ponderado en la tabla

- Se añadió la columna “Ponderado” exclusivamente en `PRUEBA_PUESTO`.
- El título de la columna explica que combina Perfil Integral y prueba y que no es la nota final.
- El desglose muestra currículum, Perfil Integral y prueba; no calcula artificialmente una nota separada del banco.
- Cuando falta una nota, se muestra un guion y se explica cuál falta.
- La columna es ordenable, deja los vacíos al final y puede ocultarse sin desalinear la tabla.
- El tipo `Ponderado` es opcional para mantener compatibilidad con backends anteriores.
- Se actualizaron la documentación y las pruebas del ranking y de la ficha.

## Compatibilidad backend–frontend

| Flujo | Backend | Frontend | Estado |
|---|---|---|---|
| Ponderado | `FilaRanking.ponderado` con cuatro valores | Columna solo en `PRUEBA_PUESTO` | Compatible por contrato |
| Datos incompletos | `sobre100` queda nulo | Guion con explicación | Compatible por contrato |
| Pesos históricos | Se toman de la vacante | El frontend muestra el resultado | Compatible por contrato |
| Hallazgos | `descripcion` y `evidencia` | La ficha consume esos campos | Compatible por contrato |
| Lista filtrada vacía | Puede no quedar nada visible | No se muestra el encabezado | Compatible por contrato |

## Validación

- `./mvnw -q -DskipTests compile` — pasa.
- `npm run typecheck` — pasa.
- `git diff --check` de los commits revisados — limpio en ambos repositorios.
- Las pruebas unitarias del frontend no iniciaron porque Vite no puede escribir en `node_modules/.vite-temp`, que está en solo lectura.
- `npm run typecheck:e2e` no pudo completarse porque falta `@playwright/test`.
- Las pruebas dirigidas del backend alcanzaron 77 casos, con 0 fallos de aserción y 77 errores de inicialización por Mockito/Byte Buddy al usar JDK 25.
- El commit del backend incluye un reporte de CI que registra la suite completa en verde; esa ejecución no pudo reproducirse localmente por las limitaciones anteriores.

## Commits visibles fuera de `main`

Hay commits del mismo día en ramas de trabajo, pero no forman parte del estado integrado:

- **Backend:** `ce9f240`, `7df14d4`, `7e6a5dd`, `eb6e11b` y `102d6b5`, relacionados con ponderado sin pesos, semáforo y exportación.
- **Frontend:** `bc0ec7b`, `026b4bc`, `6721369`, `7972441`, `3624baa` y `2ffb482`, relacionados con documentación, hallazgos y ponderado.

## Conclusión

Los cuatro commits integrados están alineados: el backend expone el ponderado y el frontend lo
presenta en la pestaña correcta; la ficha también corrige la lectura de los hallazgos de la IA.
El estado queda **apto para QA funcional**, pendiente de ejecutar las pruebas con las dependencias y
permisos completos del entorno.
