# Product

<!-- impeccable:product-schema 1 -->

## Platform

web

## Users

- **Reclutador:** opera el proceso día a día dentro de RENASER OS. Crea vacantes, resuelve revisiones, programa simulaciones y prepara evidencia.
- **Jefe del área:** ve únicamente sus vacantes y candidatos. Califica trabajo real y toma decisiones finales.
- **Dirección:** ve y configura todo, publica versiones, administra permisos y toma decisiones finales.
- **Candidato:** postula y completa las cinco etapas desde un portal público separado.

El panel debe ser comprensible para una persona que entra por primera vez. La experiencia interna es mixta: hay usuarios nuevos y usuarios recurrentes con menos de veinte cuentas internas en total.

## Product Purpose

Renaser selecciona por evidencia de trabajo, no por antigüedad ni solo por currículum. El sistema ordena el recorrido desde la vacante hasta la decisión y después compara la predicción con el desempeño real.

El primer valor del panel administrativo ocurre cuando una persona entiende qué está esperando su acción y completa el siguiente paso correcto sin tener que conocer toda la configuración del sistema.

## Positioning

La inteligencia artificial lee, puntúa, ordena y avisa en las primeras etapas; una persona revisa las zonas dudosas y siempre decide lo costoso. El trabajo real prevalece sobre el examen.

## Operating Context

- El panel vive dentro de la pestaña **Talento** de RENASER OS.
- El proceso tiene cinco etapas: CV, Evaluación Integral, Prueba del puesto, Simulación de dos horas y Validación de siete días.
- Cada estado responde de quién se espera algo: candidato, sistema, reclutador, jefe del área o nadie.
- El reclutador trabaja principalmente con excepciones: zona dudosa, fallos graves, sesiones sin programar, ausencias y decisiones ámbar.
- Configuración opera por borradores y versiones: el Reclutador prepara y Dirección publica.

## Capabilities and Constraints

- Crear, editar, publicar y cerrar vacantes.
- Revisar candidatos, explicaciones de la IA, contradicciones, entregables y decisiones.
- Programar simulaciones, controlar asistencia y cargar métricas de siete días.
- Administrar preguntas, pruebas, tiempos, puntajes, pesos, zona dudosa, correos, instrucciones de IA, roles y permisos.
- Mostrar métricas globales solo a Dirección; el resto ve únicamente lo necesario para operar su alcance.
- Los permisos son configurables y el backend debe verificarlos en cada llamada.
- Las claves de puntuación nunca llegan al portal del candidato.
- Toda intervención humana que cambia una decisión requiere justificación y auditoría.
- Decisiones pendientes del cliente: proveedor de IA, prueba psicométrica, figura legal de los siete días y algunos valores iniciales configurables.

## Brand Commitments

- Nombre: **RENASER OS**.
- El módulo se integra con la navegación y la identidad monocromática del panel existente.
- El texto visible usa español simple y evita términos internos cuando no aportan a la tarea.
- La vista de Vacantes actual se considera una referencia positiva de claridad y debe conservar su comprensión.

## Evidence on Hand

- Requisitos funcionales: `01-REQUISITOS-FUNCIONALES.md`.
- Requisitos no funcionales: `02-REQUISITOS-NO-FUNCIONALES.md`.
- Estados y transiciones: `03-ESTADOS-POSTULACION.md`.
- Roles y permisos: `04-ROLES-Y-PERMISOS.md`.
- Mockup administrativo: `mockups/renaser-os-reclutamiento.html`.
- Mockup del candidato: `mockups/portal-candidato.html`.
- No hay métricas reales de uso ni pruebas con usuarios todavía; los datos del prototipo son demostrativos.

## Product Principles

1. Mostrar primero lo que espera una persona; el sistema y las métricas quedan en segundo plano.
2. Revelar complejidad en el momento de usarla, no toda al entrar.
3. Guiar con tareas reales y datos de demostración, sin crear un tutorial separado del producto.
4. Conservar todas las capacidades mediante rutas claras, búsqueda y ayuda contextual.
5. Hacer visible quién puede actuar, quién decide y qué queda auditado.

## Accessibility & Inclusion

- El portal funciona en celular, tableta y computadora; el panel debe responder correctamente en esas dimensiones.
- Controles con nombre claro, navegación por teclado, foco visible y mensajes de error que expliquen cómo recuperarse.
- El sistema no puntúa características protegidas ni usa datos personales sensibles para predecir desempeño.

