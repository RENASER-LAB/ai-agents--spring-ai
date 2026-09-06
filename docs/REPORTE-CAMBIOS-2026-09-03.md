# Reporte de commits — 3 de septiembre de 2026

## Alcance

Revisión de los commits disponibles en `origin/main` de ambos repositorios, usando la hora local de Lima (`UTC-05:00`):

- **Backend:** `/home/n4nd0/Documentos/RENASER-RECLUTAMIENTO` — `origin/main` en `8e479ef`.
- **Frontend:** `/home/n4nd0/Documentos/RenaserOsPostulantes` — `origin/main` en `8387fdf`.

Las referencias remotas ya presentes fueron revisadas entre `00:00` y `24:00` del 03/09/2026. No se pudo ejecutar `git fetch` porque ambos `.git/FETCH_HEAD` están en solo lectura; por tanto, el reporte refleja las referencias remotas locales disponibles.

También se consultó el grafo estructural del backend con Graphify, según las instrucciones del proyecto. No se modificó código ni se limpiaron cambios locales existentes.

## Resumen ejecutivo

El día concentra dos funcionalidades coordinadas de backend y frontend:

1. **La ficha del candidato ahora muestra sus entregables de la prueba.** Incluye entregables entregados y faltantes, enlaces, archivos, versiones, permisos y mensajes diferenciados. También se corrigió la organización propietaria de los archivos para que una empresa distinta de la plataforma pueda verlos.
2. **El ranking de “Prueba del puesto” ahora usa la rúbrica real de la prueba.** Dejó de mostrar por error los ocho criterios del currículum; toma los criterios y pesos de la plantilla con la que se evaluó cada candidato.

El cambio es coherente de punta a punta: el backend expone los datos y el frontend los consume en la ficha y en la tabla. La compilación principal pasa, pero las pruebas automatizadas no pudieron completarse por problemas del entorno local.

## Orden de integración

| Hora | Repo | Commit | Cambio |
|---|---|---|---|
| 09:49 | Backend | `074eb03` (#61) | El panel puede ver lo que entregó el candidato |
| 09:57 | Backend | `1b4873e` | Actualización de documentación de workflow |
| 09:58 | Backend | `97b028f` | Merge/sincronización de `main`; sin comportamiento nuevo adicional |
| 09:59 | Frontend | `c7ade58` (#31) | La ficha enseña lo que entregó el candidato |
| 10:02 | Frontend | `da85b39` | Se deja de versionar `launch.json` específico de cada máquina |
| 14:54 | Backend | `8e479ef` (#62) | El ranking usa los criterios de la rúbrica de la prueba |
| 15:04 | Frontend | `8387fdf` (#32) | La tabla de la prueba se lee como el banco y ocupa menos ancho |

---

## Backend — `RENASER-RECLUTAMIENTO`

### `074eb03` — PR #61 — Entregables visibles en el panel

- Se añadió `EntregaDeLaPrueba`, que devuelve todos los entregables requeridos, tanto entregados como faltantes.
- Para cada entregable se informa nombre, detalle, formato, obligatoriedad, versión, fecha y contenido disponible.
- Se expuso la consulta de entregables de la prueba bajo el permiso `abrir_ficha_candidato`.
- El enlace y el identificador de archivo solo se envían con `descargar_entregables`; sin ese permiso se conserva la metadata y se explica por qué no se muestra el contenido.
- Se contemplan archivos borrados, sin ruta o inexistentes sin ofrecer una descarga que terminaría en 404.
- Se devuelve únicamente la última versión entregada de cada requisito.
- El cuestionario técnico devuelve una lista vacía, porque no solicita archivos ni enlaces.
- Se corrigió `ServicioPruebaImpl`: los archivos se guardan con la organización de la vacante, no con la organización de la cuenta del candidato.
- La migración `V48__el_entregable_es_de_la_empresa_de_la_vacante.sql` corrige registros históricos de entregables sin tocar currículums ni materiales de plantillas.

### `8e479ef` — PR #62 — Rúbrica propia en el ranking

- En `PRUEBA_PUESTO`, el ranking obtiene los criterios de la versión de plantilla que realmente abrió cada candidato.
- Las consultas se hacen por tanda: una para los intentos y otra para las rúbricas, evitando consultar la base una vez por fila.
- El cuestionario técnico no trae columnas de rúbrica; una prueba aún no iniciada o una plantilla sin criterios dejan la fila sin criterios. En ningún caso se sustituyen silenciosamente por los criterios del currículum.
- El peso de cada criterio técnico sale de `puntos` de la rúbrica, que es la misma escala usada para construir la nota de la prueba.
- La rúbrica se devuelve ordenada por `orden`, evitando que las columnas cambien entre peticiones.
- Leer el desglose de una nota pasó a pedir `abrir_ficha_candidato`, mientras que modificar la nota continúa protegido por `ajustar_nota`.
- El Excel usa el mismo permiso de lectura del desglose que la pantalla, evitando que una persona pueda ver el detalle en el panel pero no exportarlo.
- Se añadieron pruebas unitarias para criterios de la prueba, candidatos sin intento, cuestionario técnico, aislamiento de la nota del currículum y permisos.

### `1b4873e` y `97b028f`

- `1b4873e` actualiza instrucciones de workflow en `CLAUDE.MD`; no contiene cambios funcionales.
- `97b028f` es un merge de sincronización y repite en su diff parte del cambio de entregables; no debe contarse como una segunda implementación.

---

## Frontend — `RenaserOsPostulantes`

### `c7ade58` — PR #31 — Entregables en la ficha

- Se creó `EntregablesDePrueba` y su hoja de estilos.
- La ficha muestra “Lo que entregó” entre la nota de la prueba y las respuestas escritas.
- Se muestran todos los requisitos, incluidos los obligatorios que faltan, con contador de entregados.
- Los enlaces solo se vuelven pulsables si usan `http` o `https`; otros esquemas quedan como texto visible.
- Los archivos intentan abrirse mediante enlace firmado y, si la URL no es utilizable en el navegador o el entorno es local, usan la descarga de bytes del backend.
- Se diferencia un `403` por falta de permiso de un `404` ambiguo, sin afirmar que el candidato no rindió cuando podría tratarse de una vacante fuera de alcance.
- Se conserva la información existente durante un error de refresco de fondo.
- Se añadieron tipos, endpoints y una fixture del panel con casos de enlace, archivo, faltante y archivo no disponible.

### `8387fdf` — PR #32 — Tabla más estrecha y rúbrica técnica

- La pestaña `PRUEBA_PUESTO` ahora puede pintar las columnas de la rúbrica que entrega el backend.
- Los encabezados usan rótulos cortos, conservando nombre completo y peso en el título y en la leyenda.
- Los candidatos sin intento o sin rúbrica no reciben columnas falsas del currículum.
- Los motivos de nota ausente se muestran de forma corta en la celda y completa en el título.
- Los veredictos también usan rótulos compactos, con leyenda dinámica solo para los estados presentes en la tanda.
- Se ajustaron las columnas estrechas y el ancho de la tabla para que la información principal no quede dominada por textos largos.
- Las pruebas de `ranking` y `Vacante` cubren la nueva selección de criterios, rótulos y estados.

### `da85b39` — Configuración local

`launch.json` deja de versionarse como configuración de una máquina concreta; queda `launch.json.ejemplo` y se agrega la exclusión correspondiente en `.gitignore`.

---

## Compatibilidad backend–frontend

| Flujo | Backend | Frontend | Estado |
|---|---|---|---|
| Entregables de la prueba | Lista completa, faltantes, última versión y permisos | `verEntregablesDePrueba` + `EntregablesDePrueba` | Compatible por contrato |
| Enlaces entregados | Contenido condicionado por `descargar_entregables` | Solo abre enlaces web seguros | Compatible por contrato |
| Archivos entregados | Enlace firmado y descarga protegidos por el mismo permiso | Enlace firmado con respaldo por descarga | Compatible por contrato |
| Criterios de `PRUEBA_PUESTO` | Rúbrica de la versión realmente utilizada | Columnas dinámicas desde `notasCriterio` | Compatible por contrato |
| Detalle y Excel | Ambos leen con `abrir_ficha_candidato` | La ficha y la exportación usan el mismo modelo de permisos | Compatible por contrato |

## Validación realizada

- **Backend:** `./mvnw -q -DskipTests compile` — pasa.
- **Frontend:** `npm run typecheck` — pasa.
- **Frontend unitario:** no ejecutable en este entorno; Vite no puede escribir su archivo temporal en `node_modules/.vite-temp` porque el sistema de archivos está en solo lectura.
- **Frontend e2e:** `npm run typecheck:e2e` no pasa porque falta la dependencia `@playwright/test`; los errores derivados son de tipos no resueltos.
- **Backend unitario dirigido:** 87 tests, 0 fallos de aserción y 87 errores de inicialización por Mockito/Byte Buddy: el JDK 25 no permite el auto-attach requerido por el mock maker inline.
- **`git diff --check`:** frontend limpio. En backend solo aparecen espacios finales en `CLAUDE.MD` en las líneas 5, 7 y 1158; no en código funcional.

## Riesgos y pendientes de QA

1. Ejecutar la migración V48 en una base con al menos dos organizaciones y comprobar que un archivo entregado por un candidato sea visible para la empresa de su vacante.
2. Probar con almacenamiento real la apertura mediante URL firmada y la descarga de respaldo; el entorno local usa URLs `memoria://`.
3. Validar la matriz de permisos: ver ficha, ver metadata de entregables, abrir enlaces y descargar archivos.
4. Restaurar las dependencias de Playwright y ejecutar la suite e2e completa.
5. Ejecutar las pruebas Maven con una configuración de Mockito/Byte Buddy compatible con el JDK utilizado.

## Commits de hoy visibles fuera de `main`

No forman parte del estado integrado revisado, pero aparecen en referencias de trabajo:

- **Backend:** `89a2caf` (rúbrica técnica) y `a16df7a` (permiso del Excel), en `feat/rankingDeLaPrueba`.
- **Frontend:** `a43db7f` (tabla de la prueba), `161a840` (leyenda del veredicto) y `c4af6f6` (riesgo en la ficha), en ramas de trabajo.

## Conclusión

Los cambios de hoy están alineados entre backend y frontend y la compilación base pasa. El estado es **apto para QA controlado**, pero no conviene darlo por validado en producción hasta ejecutar las pruebas funcionales con dependencias completas, verificar la migración multiempresa y comprobar el almacenamiento real.
