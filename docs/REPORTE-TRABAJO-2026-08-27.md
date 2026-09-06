# Reporte de trabajo — 27 de agosto de 2026

## Alcance de la revisión

Se revisaron los repositorios backend y frontend usando la ventana del **27/08/2026 00:00–23:59, hora de Lima (UTC-05:00)**. Se consultaron los commits de todas las referencias (`git log --all`) y el estado actual de cada checkout.

En todas las referencias visibles se encontraron **17 commits del backend** y **18 commits del frontend**. Para evitar mezclar trabajo de ramas distintas, el reporte distingue la rama local, `origin/main` y las demás referencias.

> Los resultados de pruebas mencionados aquí provienen de los mensajes de commit; no se volvieron a ejecutar las suites durante esta revisión.

## Resumen ejecutivo

| Repositorio | Rama local revisada | Commits de hoy en la rama local | Estado relevante |
|---|---|---:|---|
| Backend | `main` | 2 | Está 1 commit detrás de `origin/main`; hay 7 cambios locales/no confirmados. |
| Frontend | `rediseno/maquetado-panel` | 0 | El trabajo de hoy está en referencias remotas; el checkout local tiene 44 entradas sin confirmar. |

## Backend

### Trabajo confirmado en `main`

#### `6748070` — 10:37 — inscritos, permisos y alcance de simulaciones

- Se añadió la consulta de inscritos de una sesión con nombre e `inscripcionId`.
- Se separó el permiso `ver_inscritos_simulacion` del permiso para crear sesiones.
- Se habilitó la edición de permisos por rol desde el panel, con motivo y auditoría, sin desplegar.
- Se corrigió el filtrado por organización y por alcance de vacante en marcas, asistencia, sesiones e inscritos.
- Se endureció el comportamiento de `PROPIO` para que no exponga información fuera de alcance.
- Se centralizó la resolución de nombres y el conteo de inscritos.
- Se ampliaron pruebas unitarias y de integración para permisos, simulaciones y alcance.

#### `64aa2ed` — 15:05 — banco CAZATALENTOS y calificación por criterios

- Se incorporó el banco CAZATALENTOS para preguntas abiertas por nivel.
- La calificación de preguntas abiertas cuenta cuatro criterios: episodio, autoría, dato duro y parte incómoda.
- Se añadió la regla de señal cero por pregunta, agregación en siete pilares y pesos para empresas micro, medianas y grandes.
- Se agregó la migración `V41`, el lector específico de Excel, comparadores, scripts de importación/publicación y recalificación.
- Se ajustó el flujo de importación para crear una versión en borrador y publicar después de validar la coherencia completa.
- Se añadieron pruebas de lector, fórmulas, calificación, importación, publicación, recalificación y flujo de integración.

### Commit adicional en `origin/main`

#### `94bbd63` — 17:06 — centralización del alcance por vacante

Este commit está en `origin/main`, pero todavía no está en la rama local `main`.

- Se creó `AlcanceSobreLaVacante` como punto común para decidir qué vacantes puede ver cada usuario.
- Se corrigió `PROPIO` para que no caiga en el alcance completo de la organización.
- Se migraron a la regla común los servicios de decisión, perfil, desglose, postulaciones, calificación, simulación y validación.
- Se agregaron pruebas específicas del alcance y de los tres tipos de acceso.

La rama local muestra `main...origin/main [behind 1]`; por tanto, el commit `94bbd63` debe integrarse localmente antes de considerar el checkout actualizado.

### Trabajo visible en otras referencias del backend

Además de los 3 commits de la línea principal remota, hay commits de hoy en las ramas `refactor/alcanceSusVacantes` y `feat/banco-cazatalentos`, principalmente de refactorización de guardianes, cobertura de pruebas y preparación del banco CAZATALENTOS. En total son 17 commits visibles en todas las referencias; no forman parte de la rama local `main` salvo cuando se indique expresamente.

## Frontend

### Commits de hoy en `origin/main`

El checkout local `rediseno/maquetado-panel` no tiene commits de hoy; su último commit local es `064eebe` del 21/08. Sin embargo, `origin/main` contiene 17 commits de hoy, principalmente de la rama `andy` (y las referencias visibles suman 18 al incluir una rama adicional):

- **Control Tower:** Health Score, cobertura, decisiones por impacto, metas con pronóstico y drivers de objetivos.
- **Selección y acceso:** entrada al panel, fecha de emisión de cobros y posterior reversión de la modificación de la puerta del panel.
- **Calidad y seguridad:** pruebas SQL de regresión, correcciones RLS para colaboradores y protección del motivo del interruptor.
- **Bloqueos de equipo:** el bloqueo pasó de ser un campo a una entidad, con hooks, migraciones y pantalla de gestión.
- **Documentación:** planos del sistema verificados contra el código y documentación de apoyo.
- El último commit remoto es `ba761f6` a las 17:38, un merge de `andy`.

### Cambios locales sin confirmar del frontend

El checkout actual tiene **44 entradas sin confirmar**: 21 archivos modificados/eliminados y 23 archivos nuevos. Entre ellos está el trabajo del panel de bancos:

- `src/views/seleccion/ImportarBanco.jsx`: carga el Excel y crea una versión en borrador.
- `src/services/seleccionApi.js`: contiene `importarBanco`, `versionesBanco`, `crearVersionBanco` y `publicarVersionBanco`.
- `src/views/seleccion/VistaEvaluacionPlantillas.jsx`: lista versiones, permite seleccionar un borrador y muestra `Publicar versión`.
- También hay cambios amplios de navegación, vistas de selección, componentes visuales, pruebas E2E, documentación y maquetación.

Estos cambios están presentes en el working tree, pero todavía no constituyen commits del branch local y no se puede afirmar solo con Git que todos hayan sido realizados hoy.

## Conclusión

El backend avanzó hoy con dos entregas funcionales importantes —seguridad/alcance y banco CAZATALENTOS— y tiene una corrección adicional ya publicada en `origin/main`. El frontend tiene una línea de trabajo amplia publicada en `origin/main`, mientras que el rediseño de selección y la gestión visual de bancos permanecen sin confirmar en el checkout local.

Antes de continuar desarrollando, conviene decidir si los cambios locales del frontend deben conservarse como una nueva rama/commit o si deben sincronizarse con `origin/main`; no se recomienda mezclar o descartar esos cambios sin una revisión explícita.
