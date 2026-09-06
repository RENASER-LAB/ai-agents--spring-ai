# Reporte de cambios — 28 de agosto de 2026

## Alcance

Este reporte resume los cambios registrados hoy en:

- **Backend:** `RENASER-RECLUTAMIENTO` — rama `main`.
- **Frontend:** `RenaserOsPostulantes` — rama `main`, ubicado en `../RenaserOsPostulantes`.

Se distinguen los cambios confirmados en Git de los cambios locales que todavía no tienen commit.

## Resumen ejecutivo

Durante el día se avanzó principalmente en el ciclo 1 de la prueba técnica:

- El backend incorporó la generación de fichas del puesto y cuestionarios técnicos mediante IA, además de completar la ponderación de pruebas cuya rúbrica está completa.
- El frontend incorporó las pantallas para calificar tandas completas, la ficha del puesto y el cuestionario técnico generado por IA.
- El frontend también avanzó en su instalación como aplicación Android, identidad visual, visibilidad de contraseña y navegación de salida.
- Se añadieron pruebas unitarias, de integración y recorridos end-to-end asociados a estas funcionalidades.

## Backend

### Commits realizados hoy

#### `181aa481` — 09:21

**Mensaje:** La prueba queda con nota en cuanto su rúbrica está entera (#47)

- Se ajustó el puente de evaluación con IA para que la prueba reciba una nota cuando su rúbrica esté completa.
- Se agregó cobertura para la ponderación de la prueba y el flujo de integración correspondiente.
- Se actualizaron la documentación de APIs, calificación con IA, comprobaciones automáticas y material del curso backend.

**Cambio registrado:** 10 archivos, `+422 / -30` líneas.

#### `ea797f25` — 14:11

**Mensaje:** Prueba técnica, ciclo 1: la ficha del dueño y el agente REDACTOR (#48)

- Se creó la ficha del puesto, con sus DTOs, entidad, repositorio, servicio, implementación y endpoints para el panel.
- Se incorporó el agente **REDACTOR** para apoyar la elaboración del cuestionario técnico.
- Se agregó el flujo de cuestionarios técnicos: receta, servicio, DTOs, controlador y puente con el agente redactor.
- Se extendió el seguimiento de trabajos de IA y la cola de calificación.
- Se añadió la migración `V42__prueba_tecnica_ficha_y_redactor.sql`.
- Se ajustó el manejo de bancos de preguntas y la copia de instrumentos.
- Se añadieron pruebas unitarias y de integración para el agente, el cuestionario, la ficha del puesto y el flujo completo.
- Se actualizaron la documentación funcional, de APIs, de calificación con IA y los planes de la prueba técnica.

**Cambio registrado:** 41 archivos, `+3.658 / -27` líneas.

### Cambios locales pendientes de commit

El backend conserva cambios que todavía no forman parte de un commit:

- Modificados: `scripts/calificar-pruebas.py`, `scripts/excel-de-la-prueba.py` y `scripts/invitar.py` (`+104 / -17` líneas).
- Nuevos scripts: `scripts/excel-de-curriculums.py` y `scripts/preparar-vacante-prueba.py`.
- Nuevo archivo de datos: `curriculums-vacante-13.xlsx`.
- Nuevos documentos: `docs/AVANCE-2026-08-26.md` y `docs/REPORTE-TRABAJO-2026-08-27.md`.
- Directorio no versionado: `.worktrees/`.

Estos elementos deben revisarse antes de decidir si se incorporan al próximo commit o se excluyen del repositorio.

## Frontend — `RenaserOsPostulantes`

### Commits realizados hoy

#### `a2749b76` — 09:37

**Mensaje:** Calificar y ponderar la prueba de la tanda entera (#16)

- Se agregó la vista para calificar y ponderar una tanda completa de pruebas.
- Se actualizaron el ranking, la nota de la prueba y la vista de la vacante.
- Se incorporaron pruebas unitarias y recorridos end-to-end para el flujo de ranking y empresas.

**Cambio registrado:** 12 archivos, `+1.088 / -26` líneas.

#### `b743ecf8` — 14:17

**Mensaje:** El portal se instala como aplicación de Android (#17)

- Se incorporó la configuración de Capacitor y el proyecto Android.
- Se agregaron manifiestos, configuración Gradle, actividad principal, splash screen e íconos.
- Se añadió almacenamiento nativo y configuración para los entornos móvil y emulador.
- Se incorporaron enlaces de asociación para Android y la política pública de privacidad.
- Se añadieron recorridos end-to-end y herramientas para probar el portal en Android.

**Cambio registrado:** 83 archivos, `+4.259 / -35` líneas, incluyendo recursos binarios.

#### `ee857079` — 16:10

**Mensaje:** La app lleva la hormiga de EX, no el logotipo de Capacitor (#18)

- Se reemplazaron los íconos de la aplicación Android por la identidad visual de la hormiga de EX.
- Se agregó el recurso `android/icono-play-512.png`.

**Cambio registrado:** 17 archivos, principalmente recursos binarios de íconos.

#### `929fe76e` — 16:10

**Mensaje:** La contraseña se puede mirar, y salir dejó de estar escondido (#19)

- Se agregó la posibilidad de mostrar u ocultar la contraseña en el ingreso.
- Se hizo más visible la acción para salir desde el perfil.
- Se ajustaron estilos y componentes de campos de entrada.
- Se agregaron pruebas para ingreso, perfil y campos reutilizables.
- Se simplificó contenido relacionado con la página de privacidad anterior.

**Cambio registrado:** 13 archivos, `+566 / -150` líneas.

#### `2df6821e` — 16:43

**Mensaje:** La prueba técnica del puesto: la ficha del dueño y el cuestionario que escribe la IA (#20)

- Se conectaron en el panel las APIs y tipos de la ficha del puesto y del cuestionario técnico.
- Se implementó la pantalla de la ficha del puesto.
- Se implementó la pantalla del cuestionario técnico generado por IA.
- Se incorporó la pantalla de estado de la prueba técnica y su navegación desde vacantes.
- Se añadieron utilidades para bloques y guiones del cuestionario.
- Se agregaron estilos, pruebas unitarias y un recorrido end-to-end para la prueba técnica.

**Cambio registrado:** 27 archivos, `+3.839 / -55` líneas.

### Cambios locales pendientes de commit

- Directorio no versionado: `graphify-out/`, que contiene artefactos generados de análisis del código (`graph.html`, `graph.json`, `GRAPH_REPORT.md`, caché y metadatos).

## Estado al cierre del reporte

- Los commits indicados están registrados en la rama `main` de cada repositorio.
- El backend y el frontend tienen archivos locales sin confirmar.
- No se ejecutaron pruebas durante la elaboración de este reporte; el resumen se basa en el historial y el estado de Git del día.
- Este documento es un nuevo reporte generado en `docs/REPORTE-CAMBIOS-2026-08-28.md`.
