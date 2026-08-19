# Inventario de pantallas de los mockups

> ⚠️ **Desactualizado desde el 15/08/2026.** Describe los mockups tal como están, y los mockups
> todavía reflejan la versión anterior de los requisitos: pesos por nivel, zona dudosa, CV que
> descarta, cuatro roles y semáforo de cuatro valores. **Los mockups los mantiene otra persona**,
> así que este documento se actualiza cuando ellos cambien. Lo vigente está en
> [Requisitos funcionales](01-REQUISITOS-FUNCIONALES.md).
>
> **Qué pantallas de estas entran en cada hito** está en
> [Alcance del MVP](08-ALCANCE-DEL-MVP.md): 9 en el primero, 12 en el segundo, 14 en el tercero,
> de las 21 base.

**Fecha del inventario:** 14 de agosto de 2026  
**Alcance:** estado actual de los dos prototipos HTML, no una propuesta futura ni una transcripción de los requisitos.

## Fuentes revisadas

- [`mockups/renaser-os-reclutamiento.html`](mockups/renaser-os-reclutamiento.html): panel interno de RENASER OS.
- [`mockups/portal-candidato.html`](mockups/portal-candidato.html): portal web del candidato.

Los nombres, valores, opciones, textos de ayuda y validaciones de este documento se tomaron directamente de esos archivos.

---

## 1. Resumen y criterio de conteo

El prototipo contiene **21 pantallas base**: 12 del panel interno y 9 del portal del candidato.

Al contar también los estados que cambian sustancialmente el contenido, las seis pestañas del expediente y todas las ventanas modales, hay **56 superficies funcionales documentadas**.

| Tipo | Panel interno | Portal candidato | Total |
|---|---:|---:|---:|
| Pantallas base o rutas principales | 12 | 9 | **21** |
| Subpantallas o estados completos adicionales | 6 | 6 | **12** |
| Ventanas modales | 18 | 5 | **23** |
| **Total de superficies funcionales** | **36** | **20** | **56** |

### Qué cuenta y qué no cuenta

- Una **pantalla base** sustituye el área central y tiene una ruta, pestaña o sección propia.
- Una **subpantalla o estado completo** cambia la función principal de una ruta o del expediente.
- Una **ventana modal** bloquea temporalmente la pantalla para consultar o completar una acción.
- Crear y editar una vacante se cuentan como **un formulario con dos modos**. Si se cuentan como pantallas distintas, el total sería 57.
- No se cuentan como pantallas independientes los avisos, mensajes emergentes, tema claro/oscuro, variantes de datos, barras de progreso, el menú móvil ni cada paso de una guía.
- El “cambio inesperado” de la prueba se documenta como estado interno, pero no suma una pantalla porque aparece dentro de la prueba activa.
- Las variantes por rol se documentan, pero no se duplican en el total.

### Listado corto de las 21 pantallas base

| Nº | Aplicación | Pantalla | Ruta o sección |
|---:|---|---|---|
| 1 | Panel | Desempeño del equipo | `#talent/performance` |
| 2 | Panel | Necesidad de personal | `#talent/headcount` |
| 3 | Panel | Personas para futuras vacantes | `#talent/pool` |
| 4 | Panel | Selección · Inicio | `#talent/selection/summary` |
| 5 | Panel | Selección · Vacantes | `#talent/selection/vacancies` |
| 6 | Panel | Selección · Personas en proceso | `#talent/selection/candidates` |
| 7 | Panel | Selección · Fechas y sesiones | `#talent/selection/sessions` |
| 8 | Panel | Selección · Ajustes del proceso | `#talent/selection/settings` |
| 9 | Panel | Selección · Editor de prueba | `#talent/selection/test-editor` |
| 10 | Panel | Selección · Resultados | `#talent/selection/metrics` |
| 11 | Panel | Desarrollo del equipo | `#talent/development` |
| 12 | Panel | Reemplazos y continuidad | `#talent/backup` |
| 13 | Portal | Vacantes abiertas | `#jobs` |
| 14 | Portal | Detalle de vacante | `#job/:id` |
| 15 | Portal | Crear cuenta | `#register/:id` |
| 16 | Portal | Enviar postulación | `#apply/:id` |
| 17 | Portal | Mis procesos | `#dashboard` |
| 18 | Portal | Evaluación integral | `#evaluation/:applicationId` |
| 19 | Portal | Prueba del puesto | `#challenge/:applicationId` |
| 20 | Portal | Simulación | `#simulation/:applicationId` |
| 21 | Portal | Estado o resultado | `#status/:applicationId` |

---

# 2. Panel interno de RENASER OS

## 2.1 Estructura común

Todas las pantallas internas comparten:

- Marca: **RENASER OS** y subtítulo **V5 · Enterprise Intelligence**.
- Menú lateral general:
  - Comando: `Inicio` con indicador `3`.
  - Dirección: `Estrategia`, `Inteligencia` con indicador `5`.
  - Negocio: `Operación`, `Clientes` con indicador `11`, `Talento`, `Growth`, `Eventos`, `Finanzas`, `Consultoría`.
  - Infraestructura: `Sistema`.
- `Talento` aparece activo. Los demás botones del menú general son visuales y no abren pantallas implementadas en este mockup.
- Barra superior:
  - Estado: `Datos de demostración · guardado local`.
  - Botón `Buscar persona ⌘K`, que abre la sección Personas en proceso.
  - Botón `Ayuda y guías`.
  - Botón de tema claro/oscuro.
- Selector `Talento · Cambiar área`.
- Acción contextual: `Crear vacante` en Selección, `Registrar necesidad` en Necesidad de personal.
- Persistencia local en el navegador mediante la clave `renaser_recruitment_mock_v1_admin`.
- Diseño adaptable a móvil, menú lateral colapsable, foco visible, cierre con `Escape` y control de foco en modal/cajón.

### Campo global: Ver como

| Campo | Tipo | Opciones exactas | Valor inicial |
|---|---|---|---|
| Ver como | Selector | `Dirección`, `Reclutador`, `Jefe del área` | `Reclutador` |

Usuario de demostración: iniciales `KM`, nombre `Kelin` y rol inicial `Reclutador`.

### Áreas de Talento

| Área visible | Propósito mostrado |
|---|---|
| Desempeño del equipo | Resultados y seguimiento después de contratar. |
| Necesidad de personal | Validación de capacidad antes de abrir un puesto. |
| Personas para futuras vacantes | Perfiles que aceptaron nuevas invitaciones. |
| Selección de personal | Vacantes, postulaciones, evaluación y decisión. |
| Desarrollo del equipo | Aprendizaje y planes posteriores a la contratación. |
| Reemplazos y continuidad | Cobertura de trabajos críticos y sucesión. |

Dentro de Selección aparecen las secciones `Inicio`, `Vacantes`, `Personas en proceso`, `Fechas y sesiones`, `Ajustes del proceso` y `Resultados`. El Jefe del área no ve Ajustes del proceso.

## 2.2 Datos de demostración compartidos

### Vacantes

| ID | Puesto | Nivel | Estado | Personas | Nota mínima | Días abierta |
|---|---|---|---|---:|---:|---:|
| `dir-ops` | Director de Operaciones | Dirección | Publicada | 18 | 75 | 12 |
| `coord-ops` | Coordinador de Operaciones | Supervisión | Publicada | 21 | 72 | 8 |
| `dev` | Desarrollador | Ejecución | Borrador | 7 | 70 | 2 |

### Personas en proceso

| ID | Persona | Vacante | Etapa | Estado técnico | Debe actuar | Días | Señal | Nota |
|---|---|---|---|---|---|---:|---|---:|
| `ana` | Ana Torres | Coordinador de Operaciones | Evaluación Integral | `EVALUACION_EN_REVISION` | Reclutador | 3 | Ámbar | 74 |
| `diego` | Diego Salas | Desarrollador | Prueba del puesto | `PRUEBA_EN_CURSO` | Candidato | 0 | Verde | 82 |
| `maria` | María Paredes | Director de Operaciones | Simulación | `SIMULACION_POR_PROGRAMAR` | Reclutador | 5 | Ámbar | 86 |
| `lucas` | Lucas Medina | Desarrollador | Validación 7 días | `VALIDACION_POR_CALIFICAR` | Jefe del área | 2 | Verde | 79 |
| `valeria` | Valeria Núñez | Coordinador de Operaciones | Decisión | `DECISION_PENDIENTE` | Reclutador | 1 | Sin datos | 88 |

### Historial inicial

- `Hoy · 09:42` — Sistema — `Ana pasó a revisión por zona dudosa.`
- `Ayer · 17:18` — Kelin — `Confirmó la nota de CV de María.`

---

## 2.3 Pantallas principales del panel

## A01. Desempeño del equipo

**Objetivo:** comparar el trabajo real posterior a la contratación con lo observado durante selección.

**Datos visibles:**

| Métrica | Valor |
|---|---:|
| Personas | 18 |
| Alto rendimiento | 7 |
| En riesgo | 2 |
| Seguimientos 30/90/180 | 9 |
| Predicción acertada | 81% |

Casos mostrados:

- `Ariana · Operaciones`: Resultado en meta; cierre diario completado, evidencia vinculada y 84% de capacidad.
- `Luis · Marketing`: requiere seguimiento; dos entregas con retrabajo y revisión a 30 días pendiente.
- `Selección vs desempeño`: enlace `Ver métricas` hacia Resultados de selección.

**Campos editables:** ninguno.

## A02. Necesidad de personal

**Objetivo:** comprobar la capacidad antes de abrir una vacante.

**Datos visibles:**

- Solicitud abierta: `Coordinador de Operaciones`, solicitada por Operaciones.
- Resultado: `ABRIR POSICIÓN`.
- Gap real: `76 h/mes después de optimización`.
- Demanda bruta: `210 h`.
- Capacidad disponible: `121 h`.
- Retrabajo eliminable: `19 h`.
- Automatizable: `14 h`.
- Resultado inferior: `76 horas al mes`.

**Acciones:** `Registrar necesidad` abre el formulario Solicitar capacidad; `Crear vacante` abre el formulario de vacante precargado como Coordinador de Operaciones.

## A03. Personas para futuras vacantes

**Objetivo:** invitar perfiles con consentimiento sin reutilizar respuestas ni notas anteriores.

**Métricas:** 38 perfiles con consentimiento, invitados dinámicos, 5 nuevas postulaciones, 100% sin reutilizar datos.

| Persona | Fortaleza | Base de contacto | Afinidad | Acción inicial |
|---|---|---|---:|---|
| Sofía Herrera | Coordinación | Postuló antes | 91% | Invitar a postular |
| Renato Cruz | Tecnología | Aceptó futuras convocatorias | 88% | Invitar a postular |
| Elena Ramos | Operaciones | Postuló antes | 84% | Invitar a postular |

Al invitar, la acción cambia a la etiqueta `Invitación enviada`. No hay campos editables.

## A04. Selección · Inicio

**Objetivo:** mostrar primero la acción que está deteniendo un proceso, luego las vacantes en marcha.

**Estado inicial de onboarding:**

- Título: `Tu primer recorrido por Selección`.
- Tres pasos explicativos.
- Acciones: `Ahora no` y `Empezar recorrido`.

**Prioridades para Reclutador:**

1. Publicar fechas para María Paredes — Director de Operaciones, espera 5 días; acción `Programar fechas`.
2. Revisar la evidencia de Ana Torres — zona dudosa, espera 3 días; acción `Revisar expediente`.
3. Preparar la recomendación de Valeria Núñez — espera 1 día; acción `Preparar recomendación`.

**Cambio para Dirección:** la tercera prioridad se convierte en `Tomar la decisión final de Valeria Núñez`.

**Cambio para Jefe del área:**

1. `Tomar la decisión final de Valeria Núñez`.
2. `Registrar las métricas de Lucas Medina`.

**Vacantes en marcha:** muestra puesto, nivel, cantidad, días abierta y cinco barras en orden `CV`, `Evaluación`, `Prueba`, `Simulación`, `Validación`.

Porcentajes de demostración:

- Director de Operaciones: `100, 72, 38, 20, 8`.
- Coordinador de Operaciones: `100, 81, 45, 24, 12`.
- Desarrollador: `100, 57, 20, 0, 0`.

Acciones secundarias: `Ver todas las vacantes`, `Aprender otro flujo` y `Consultar resultados del proceso`.

## A05. Selección · Vacantes

**Objetivo:** administrar puestos, publicación y acceso a postulantes.

Cada tarjeta muestra:

- Estado.
- Días abierta.
- Nombre del puesto.
- Nivel.
- Nota mínima sobre 100.
- Cantidad de personas.

**Acciones de Dirección y Reclutador:** `Crear vacante`, `Ver personas`, `Editar vacante`, `Cerrar` si está publicada o `Publicar` si está en borrador.

**Jefe del área:** solo ve `Coordinador de Operaciones`; puede `Ver personas` y `Opinar sobre la prueba`. No puede crear, editar, publicar ni cerrar.

## A06. Selección · Personas en proceso

**Objetivo:** encontrar una persona, conocer su etapa y saber quién debe actuar.

**Campos exactos:**

| Campo | Tipo | Opciones o texto |
|---|---|---|
| Buscar persona o vacante | Búsqueda de texto | Placeholder `Buscar persona o vacante` |
| Filtrar por responsable | Selector | `Quién debe actuar: todos`, `Reclutador`, `Jefe del área`, `Candidato` |

**Columnas exactas de la tabla:** `Persona`, `Vacante`, `Etapa actual`, `Estado del proceso`, `Quién debe actuar`, `Tiempo sin avanzar`, `Señal`, `Acción`.

La acción es `Expediente`. Debajo se repiten las personas en un tablero por `CV`, `Evaluación Integral`, `Prueba del puesto`, `Simulación` y `Validación 7 días`.

El Jefe del área solo ve personas de Coordinador de Operaciones.

## A07. Selección · Fechas y sesiones

**Objetivo:** programar la simulación de dos horas y controlar cupos y asistencia.

**Sesiones visibles:**

| Estado | Fecha | Sala | Ocupación |
|---|---|---|---|
| Confirmada | 22 de agosto · 09:00 | Sala principal | 6 confirmadas, 2 lugares disponibles de 8 |
| Por confirmar | 24 de agosto · 15:00 | Sala principal | 3 confirmadas, 5 lugares disponibles de 8 |

Para Dirección y Reclutador aparece una tercera tarjeta: `Faltan fechas`, `2 personas están esperando`; María lleva 5 días sin poder elegir.

**Acciones:** `Programar simulación`, `Ver participantes y asistencia`, `Publicar dos fechas`.

**Momentos exactos:** Entender contexto 10 min, Hacer preguntas 10 min, Resolver 65 min, Adaptar al cambio 20 min, Entregar 10 min, Conversar 5 min.

## A08. Selección · Ajustes del proceso

**Objetivo:** concentrar contenido de evaluación, reglas, automatizaciones, mensajes, límites y permisos.

**Qué se evalúa:**

| Código | Ajuste | Dato visible | Acceso |
|---|---|---|---|
| 01 | Preguntas de la evaluación | 236 preguntas · versionadas | Dirección y Reclutador |
| 02 | Pruebas de cada puesto | 12 plantillas · versionadas | Dirección y Reclutador |
| 03 | Reglas para aprobar y revisar | Mínimo 70 · margen ±5 | Solo Dirección edita |

**Cómo funciona el proceso:**

| Código | Ajuste | Dato visible | Acceso |
|---|---|---|---|
| 04 | Instrucciones que usa la IA | Versión activa 4.2 | Solo Dirección administra |
| 05 | Mensajes por correo | 8 mensajes activos | Dirección y Reclutador |
| 06 | Plazos y pruebas adicionales | 60 días · máximo 2 pruebas | Dirección y Reclutador |

**Acceso y control:**

| Código | Ajuste | Dato visible | Acceso |
|---|---|---|---|
| 07 | Personas, roles y permisos | 3 roles configurados | Solo Dirección administra |
| 08 | Historial de cambios y decisiones | Registro completo | Solo Dirección consulta |

Acción: `Iniciar guía de configuración`. Para el Jefe del área esta pantalla no es accesible; si se intenta abrir, la interfaz lo devuelve a Inicio.

## A09. Selección · Editor de prueba del puesto

**Plantilla:** `Prueba del puesto · Desarrollador`, estado `Borrador v4`.

**Campos exactos:**

| Sección | Campo | Tipo | Valor inicial |
|---|---|---|---|
| Reto y entrega | Enunciado | Texto largo | `Diseña una solución para registrar bloqueos operativos, priorizarlos y asignar una próxima acción.` |
| Reto y entrega | Entregable esperado | Texto largo | `Propuesta funcional o visual, explicación de decisiones y evidencia de verificaciones.` |
| Tiempos | Duración total · min | Número, mínimo 1 | 120 |
| Tiempos | Cambio inesperado · min | Número, mínimo 1 | 84 |
| Tiempos | Tiempo adicional · min | Número, mínimo 1 | 20 |
| Cambio inesperado | Instrucción | Texto largo | `Ahora cada responsable puede tener un máximo de cinco bloqueos activos. Adapta tu propuesta sin perder información.` |
| Puntuación | Calidad del entregable | Número, mínimo 0 | 35 |
| Puntuación | Adaptación al cambio | Número, mínimo 0 | 25 |
| Puntuación | Comunicación preventiva | Número, mínimo 0 | 20 |
| Puntuación | Autocrítica y verificación | Número, mínimo 0 | 20 |

Descripciones de criterios: `Resultado verificable`, `Decide y ajusta con criterio`, `Hace visibles riesgos e impacto`, `Reconoce límites y comprueba`.

La suma inicial es `100 puntos`. Se permite guardar si no suma 100, pero aparece el aviso `La puntuación no suma 100`.

**Versiones visibles:**

- v4 · Borrador — Ahora · Kelin · Reclutador — Actual.
- v3 · Publicada — 12 ago · Dirección — En uso.
- v2 · Archivada — 02 ago · Dirección — acción `Ver`.

**Acciones:** `Volver a Ajustes del proceso`, `Guardar borrador`; Dirección recibe además `Publicar versión`.

## A10. Selección · Resultados

**Métricas comunes:**

- Personas que llegan a contratación: `4.3%` — 2 contratadas de 46 postulantes.
- Duración del proceso: `23 d` — referencia de demostración 20 días.

**Solo Dirección:**

- Predicción confirmada en el trabajo: `81%`, seguimientos a 30, 90 y 180 días.
- IA sin corrección humana: `72%`; decisiones críticas siempre confirmadas.
- Conclusiones: la prueba del puesto es la señal más útil y dos preguntas no ayudan a distinguir resultados.

**Otros roles:** tarjeta `Datos disponibles para este rol · Resultados operativos`. El Reclutador puede exportar datos operativos permitidos; el Jefe del área solo consulta.

**Conversión visible:** CV 46 (100%), evaluación integral 31 (67%), prueba 14 (30%), simulación 7 (15%).

## A11. Desarrollo del equipo

**Objetivo:** aprendizaje y seguimiento posterior a la contratación.

| Métrica | Valor |
|---|---:|
| Gold Standards | 14/18 |
| Planes activos | 12 |
| Promocionables | 3 |
| Brechas críticas | 7 |

Programas: `Atención Premium RENASER` 78% certificado, `Operación de eventos` 50% en progreso, `Uso seguro de agentes IA` 22% nuevo.

**Campos editables:** ninguno.

## A12. Reemplazos y continuidad

**Objetivo:** sucesión y cobertura de trabajos críticos.

| Métrica | Valor |
|---|---:|
| Procesos críticos | 47 |
| Con backup | 31 |
| Sin backup | 16 |
| Prueba de ausencia | 66% |

| Rol | Backup | Preparación | Prueba | Próxima acción |
|---|---|---:|---|---|
| Operaciones | Parcial | 64% | 2/4 procesos | Certificar backup |
| Marketing | No | 31% | 0/3 procesos | Documentar SOP crítico |
| Mentoría | Sí | 82% | 4/5 procesos | Prueba de ausencia |

**Campos editables:** ninguno.

---

## 2.4 Subpantallas del expediente de una persona

El expediente es un cajón lateral con **seis subpantallas**. El encabezado usa la persona seleccionada y muestra su nota dinámica sobre 100, vacante y estado. El resto del contenido de demostración es común a todos los expedientes.

### A13. Expediente · Resumen

- Evidencia: `Alta`, `4 de 5 etapas`.
- Fortaleza: `Ejecución rápida`.
- Riesgo: `Avisa tarde`.
- Próxima acción: `Revisión humana`.
- Recomendación IA: evidencia consistente en ejecución, contradicción sobre aviso preventivo y recomendación de aclararla; la IA no descarta sola.
- Actividad reciente: historial de auditoría.

### A14. Expediente · CV

- `CV anonimizado · 78/100`.
- Oculta foto, edad, sexo y estado civil.
- Resultados demostrables: `22/25`.
- Calidad de evidencia: `7/10`.

### A15. Expediente · Evaluación

- Alerta: dijo que comunica riesgos inmediatamente, pero en un microcaso esperó una solución completa; no descarta.
- Clave interna para Dirección/Reclutador: la opción de 4 puntos avisa el riesgo, explica impacto y propone una acción.
- Alineación personal:
  - Dinero y trabajo: Verde.
  - Madurez en conflicto: Ámbar.
  - Autogestión: Verde.
- El Jefe del área no ve la clave interna.

### A16. Expediente · Prueba

- `Prueba del puesto · 84/100`.
- Entregó a tiempo, explicó decisiones, declaró uso de IA y verificaciones.
- Cambio inesperado: reacción a `2 min 18 s`; comunicación del riesgo 8 minutos después.

### A17. Expediente · Simulación

- Estado: `Conversación humana pendiente`.
- El sistema generó cinco preguntas desde contradicciones.
- Pregunta sugerida exacta: `Dijiste que avisas los riesgos temprano. Detectaste el bloqueo a las 10:41 y lo informaste a las 10:49. ¿Qué ocurrió?`

### A18. Expediente · Decisión

- Recomendación: `Ámbar` porque falta resolver una contradicción.
- Reclutador: aviso `El reclutador no contrata`; acciones `Pedir prueba adicional` y `Registrar recomendación`.
- Dirección/Jefe del área: `Contratar`, `Prueba adicional`, `No continúa`.
- Toda decisión abre un campo de justificación obligatoria.

---

## 2.5 Ventanas modales del panel interno

## AM01. Cambiar área de Talento

Lista las seis áreas de Talento con su descripción. Cada una tiene `Abrir`; la activa muestra `Área actual`. Sin campos editables.

## AM02. Ayuda y guías

Muestra las guías permitidas por rol, duración, número de momentos y estado completado. Acciones: `Iniciar guía` o `Repetir guía`, `Restablecer demostración`, `Cerrar`.

## AM03. Crear/editar vacante

| Campo | Tipo | Opciones/valor |
|---|---|---|
| Puesto | Texto | Vacío al crear; nombre actual al editar |
| Nivel | Selector | `Dirección`, `Supervisión`, `Ejecución` |
| Nota mínima | Número | 72 al crear |
| Descripción y resultado esperado | Texto largo | Placeholder `Describe el puesto en palabras simples` |
| Plantilla de prueba | Selector | `Coordinación de Operaciones`, `Dirección`, `Desarrollador` |
| Publicación | Selector | `Borrador`, `Publicada` |

Validación implementada: Puesto es obligatorio. Acciones: `Cancelar` y `Guardar vacante` o `Guardar cambios`.

**Límite actual del prototipo:** al guardar, solo persiste puesto, nivel, nota mínima y publicación. Descripción y plantilla están dibujadas, pero no se almacenan en el estado local.

## AM04. Solicitar capacidad

| Campo | Tipo | Valor inicial |
|---|---|---|
| Necesidad | Texto | Coordinación de Operaciones |
| Demanda mensual | Número | 210 |
| Capacidad disponible | Número | 121 |
| Retrabajo eliminable | Número | 19 |
| Automatizable | Número | 14 |

Acciones: `Cancelar`, `Analizar capacidad`. El prototipo lleva a Necesidad de personal y muestra la recomendación de abrir una posición.

## AM05. Preguntas de la evaluación

| Campo | Tipo | Opciones/valor inicial |
|---|---|---|
| Nivel del puesto | Selector | Dirección, **Supervisión**, Ejecución |
| Estado | Selector | **Borrador v7**, Publicada v6 |
| Pregunta | Texto largo | `Cuéntanos una ocasión en la que detectaste un riesgo antes de que afectara el resultado. ¿Qué hiciste?` |
| Qué debe contener una respuesta sólida | Texto largo | `Aviso preventivo, impacto explicado, acción propuesta y evidencia verificable.` |

Acciones: `Cancelar`, `Guardar borrador`.

## AM06. Reglas para aprobar y revisar

| Campo | Tipo | Valor inicial |
|---|---|---:|
| Nota mínima | Número | 70 |
| Margen de revisión humana | Número | 5 |
| Peso de CV · % | Número | 15 |
| Peso de evaluación integral · % | Número | 25 |
| Peso de prueba del puesto · % | Número | 35 |
| Peso de simulación · % | Número | 25 |

Acciones: `Cancelar`, `Guardar borrador`. Solo Dirección puede abrir esta ventana desde la interfaz.

## AM07. Instrucciones que usa la IA

| Campo | Tipo | Valor inicial |
|---|---|---|
| Instrucción activa | Texto largo | `Analiza únicamente la evidencia permitida. Explica cada criterio. No uses datos personales protegidos y no tomes la decisión final.` |

Datos: uso de agosto `S/ 184.20`, `1,284 evaluaciones`, `72% sin corrección de nota`, versión `4.2 activa`. Solo Dirección administra.

## AM08. Mensajes por correo

| Campo | Tipo | Opciones/valor |
|---|---|---|
| Momento del proceso | Selector | `Invitación a evaluación`, `Recordatorio`, `Avance de etapa`, `Cierre` |
| Mensaje | Texto largo | `Hola {{nombre}}, tu siguiente paso está listo. Ingresa a tu postulación antes del {{fecha_limite}}.` |

Acciones: `Cancelar`, `Guardar borrador`. Los correos no deben revelar preguntas ni claves.

## AM09. Plazos y pruebas adicionales

| Campo | Tipo | Valor inicial |
|---|---|---:|
| Cerrar por inactividad después de | Número | 60 |
| Máximo de pruebas adicionales | Número | 2 |

Al llegar al límite, debe registrarse Verde o Rojo con justificación y no puede pedirse otra prueba Ámbar.

## AM10. Personas, roles y permisos

| Persona | Descripción | Rol inicial | Opciones del selector |
|---|---|---|---|
| Kelin Morales | Prepara evaluaciones, resuelve bloqueos y arma recomendaciones | Reclutador | Reclutador, Dirección, Jefe del área |
| Andrea Ríos | Publica versiones, ve todos los procesos y toma decisiones | Dirección | Reclutador, Dirección, Jefe del área |
| Luis Castro | Ve únicamente su área y registra la decisión final | Jefe del área | Reclutador, Dirección, Jefe del área |

Acción `Agregar persona` inserta:

- `Persona`: texto, placeholder `Nombre y apellido`.
- `Rol`: selector con Reclutador, Dirección, Jefe del área.

Solo Dirección administra.

## AM11. Historial de cambios y decisiones

Ventana de solo consulta. Muestra cada registro con acción, persona y fecha. Acción: `Cerrar`.

## AM12. Cerrar vacante

| Campo | Tipo | Condición |
|---|---|---|
| Motivo | Texto largo | Obligatorio; placeholder `Explica por qué se cierra` |

Advierte que también se cerrarán las postulaciones en curso y se avisará a las personas. Acciones: `Cancelar`, `Cerrar vacante`.

## AM13. Justificar decisión

| Campo | Tipo | Condición |
|---|---|---|
| Justificación obligatoria | Texto largo | Obligatorio; placeholder `Explica la evidencia y el criterio aplicado` |

Muestra la decisión seleccionada (`Verde`, `Ámbar` o `Rojo`). Acciones: `Cancelar`, `Registrar decisión`.

## AM14. Registrar recomendación

| Campo | Tipo | Condición |
|---|---|---|
| Criterio y evidencia | Texto largo | Obligatorio; placeholder `Explica qué recomiendas y en qué evidencia te basas` |

Aclara que la recomendación del Reclutador no contrata ni rechaza. Acciones: `Cancelar`, `Guardar recomendación`.

## AM15. Programar simulación

| Campo | Tipo | Valor inicial |
|---|---|---|
| Primera fecha | Fecha y hora | `2026-08-22T09:00` |
| Segunda fecha | Fecha y hora | `2026-08-24T15:00` |
| Cupo por sesión | Número | 8 |
| Sala | Texto | Sala principal |

Las dos fechas son obligatorias. Acciones: `Cancelar`, `Publicar dos fechas`.

## AM16. Participantes y asistencia

| Persona | Vacante/estado | Campo |
|---|---|---|
| María Paredes | Director de Operaciones · confirmada | Asistió, marcado |
| Valeria Núñez | Coordinador de Operaciones · confirmada | Asistió, marcado |
| Ana Torres | Coordinador de Operaciones · pendiente | Asistió, sin marcar |

Acciones: `Cancelar`, `Guardar asistencia`.

## AM17. Métricas de siete días

Cada métrica es un número entre 0 y 100.

| Campo | Peso | Valor inicial |
|---|---:|---:|
| Resultado logrado | 25% | 78 |
| Calidad al primer intento | 15% | 79 |
| Velocidad | 10% | 80 |
| Confiabilidad | 10% | 81 |
| Autonomía | 10% | 82 |
| Aviso preventivo | 10% | 83 |
| Aprendizaje entre la primera y segunda vez | 10% | 84 |
| Servicio | 5% | 85 |
| Aporte al sistema | 5% | 86 |

Campo adicional `Evidencia y observaciones`, texto largo, valor: `Resultados revisados con el jefe del área y evidencia vinculada al expediente.`

Acciones: `Cancelar`, `Guardar nueve métricas`. Tras guardar, el estado pasa a `DECISION_PENDIENTE` y debe actuar el Jefe del área.

## AM18. Opinar sobre la prueba

| Campo | Tipo | Condición |
|---|---|---|
| Observación sobre el reto o los criterios | Texto largo | Obligatorio; placeholder `Explica qué debería reflejar mejor el trabajo real del área` |

La opinión queda visible al Reclutador y no cambia la prueba hasta que se incorpore. Acciones: `Cancelar`, `Enviar opinión`.

---

## 2.6 Tutoriales guiados

Las guías aparecen como una franja contextual dentro de las pantallas; no agregan pantallas al total.

| Guía | Duración | Roles | Pasos |
|---|---:|---|---:|
| Conocer el panel | 2 min | Todos | 4 |
| Crear y publicar una vacante | 3 min | Dirección, Reclutador | 3 |
| Configurar preguntas y pruebas | 4 min | Dirección, Reclutador | 4 |
| Revisar una persona en zona dudosa | 2 min | Dirección, Reclutador | 2 |
| Preparar una simulación | 2 min | Todos | 2 |
| Preparar una decisión final | 2 min | Todos | 2 |

**Total:** 6 flujos y 17 pasos.

Cada franja muestra título, explicación, avance `n de total`, nombre de la guía y acciones `Salir de la guía`, `Anterior`, `Siguiente` o `Terminar`. El elemento correspondiente se resalta y se desplaza al centro de la pantalla.

---

# 3. Portal del candidato

## 3.1 Estructura común

- Marca `RENASER` y subtítulo `Oportunidades profesionales`.
- Navegación: `Vacantes`, `Mis procesos`, tema claro/oscuro y `Ingresar` o el primer nombre de la cuenta.
- Pie: `© 2026 Renaser Consulting · Portal de empleo` y `Privacidad · Tratamiento de datos · Ayuda`.
- Persistencia local mediante `renaser_recruitment_mock_v1_candidate`.
- Interfaz adaptable a móvil.
- La evaluación guarda respuestas al seleccionar/escribir.
- Los cronómetros usan la hora real del navegador y siguen contando aunque se cambie de vista.
- Es una demostración: no envía archivos, correos, contraseñas ni solicitudes reales.

## 3.2 Datos de demostración compartidos

### Vacantes

| ID | Puesto | Nivel | Ubicación | Preguntas | Prueba | Cambio | Extra |
|---|---|---|---|---:|---:|---:|---:|
| `dir-ops` | Director de Operaciones | Dirección | Arequipa · Híbrido | 90 | 120 min | min 84 | 15 min |
| `coord-ops` | Coordinador de Operaciones | Supervisión | Arequipa · Presencial | 60 | 90 min | min 63 | 15 min |
| `dev` | Desarrollador | Ejecución | Remoto · Perú | 50 | 120 min | min 84 | 20 min |

**Resúmenes exactos:**

- Director de Operaciones: `Lidera el sistema operativo de Renaser, convierte objetivos en resultados y desarrolla capacidad en los equipos.`
- Coordinador de Operaciones: `Coordina entregas, elimina bloqueos y asegura calidad, velocidad y comunicación preventiva.`
- Desarrollador: `Construye y mantiene soluciones digitales confiables, medibles y fáciles de operar.`

### Cuenta y postulaciones iniciales

- Nombre: `Camila Torres`.
- Correo: `camila@ejemplo.pe`.
- Cuenta inicialmente no autenticada.

| ID | Vacante | Etapa | Estado | Días |
|---|---|---:|---|---:|
| `app-coord` | Coordinador de Operaciones | 1 · Evaluación | `EVALUACION_EN_CURSO` | 1 |
| `app-dev` | Desarrollador | 3 · Simulación | `SIMULACION_POR_CONFIRMAR` | 0 |
| `app-dir` | Director de Operaciones | Finalizada | `NO_CONTINUA` | 0 |

Etapas visibles: `CV`, `Evaluación`, `Prueba`, `Simulación`, `Validación`.

---

## 3.3 Pantallas principales del portal

## C01. Vacantes abiertas

**Objetivo:** presentar las oportunidades y explicar el proceso antes de pedir datos.

Explica tres expectativas:

1. Postular con CV y evidencia de trabajo.
2. Responder una evaluación y una prueba del puesto.
3. Ver siempre la etapa y siguiente acción.

Muestra `3 oportunidades`. Cada tarjeta incluye nivel, ubicación, nombre, resumen, `Proceso de 5 etapas` y `Ver vacante`.

**Campos editables:** ninguno.

## C02. Detalle de vacante

**Contenido común:**

- Resultado esperado: organizar el trabajo, hacer visibles los riesgos y entregar resultados verificables.
- Requisitos:
  - Experiencia demostrable resolviendo problemas del puesto.
  - Capacidad para explicar decisiones, supuestos y aprendizajes.
  - Comunicación preventiva cuando aparece un riesgo.
  - Disponibilidad para una simulación presencial de dos horas.
- IA: participa en algunas calificaciones y explica su criterio; las decisiones sensibles pueden revisarse por una persona.

**Etapas mostradas:**

1. CV — `Sube tu CV y enlaces`.
2. Evaluación — `Responde {90|60|50} preguntas` según puesto.
3. Prueba — `Demuestra el trabajo en {120|90} minutos`.
4. Simulación — `Trabaja en una sesión grupal`.
5. Validación — `Evidencia durante siete días`.

Acción: `Postular`. Si no hay sesión iniciada va a Crear cuenta; si existe, va a Enviar postulación.

## C03. Crear cuenta

| Campo | Tipo | Valor inicial | Regla |
|---|---|---|---|
| Nombre y apellidos | Texto | Camila Torres | Obligatorio |
| Correo | Correo | camila@ejemplo.pe | Obligatorio y debe contener `@` |
| Contraseña | Contraseña | Demo12345! | Mínimo 8 caracteres |
| Repite la contraseña | Contraseña | Demo12345! | Debe coincidir |
| Acepto el tratamiento de mis datos personales | Checkbox | Sin marcar | Obligatorio |

Consentimiento visible: los datos se usan para la postulación, participa una IA y pueden almacenarse fuera del Perú. Acción secundaria `Leer política completa`.

Acción principal: `Crear cuenta y continuar`. La pantalla también muestra el puesto al que se postula.

## C04. Enviar postulación

| Campo | Tipo | Opciones/placeholder | Regla |
|---|---|---|---|
| CV | Archivo | `.pdf`, `.doc`, `.docx`; texto `PDF o Word · máximo 10 MB` | Obligatorio |
| Portafolio o sitio personal | Texto | `https://` | Opcional |
| LinkedIn | Texto | `https://linkedin.com/in/...` | Opcional |
| GitHub u otro proyecto | Texto | `https://` | Opcional |
| Resultado del que te sientas orgulloso | Texto largo | `Cuéntanos qué cambió gracias a tu trabajo y cómo lo comprobaste.` | Obligatorio |

Acciones: `Seleccionar archivo`, `Enviar postulación`. Aclara que la postulación puede retirarse desde el panel.

**Límite actual del prototipo:** la interfaz dice máximo 10 MB, pero el JavaScript no verifica tamaño ni contenido; solo comprueba que exista un archivo.

## C05. Mis procesos

**Objetivo:** poner primero la próxima acción y después el historial completo.

Muestra saludo con el primer nombre, `Ver más vacantes`, un panel de próxima acción y todas las postulaciones.

**Reglas de acción por etapa:**

| Etapa | Botón | Título | Ayuda |
|---:|---|---|---|
| 1 | Continuar evaluación | Tienes una evaluación pendiente | Tus respuestas se guardan automáticamente. |
| 2 | Abrir prueba | Prueba del puesto habilitada | El cronómetro empezará cuando confirmes. |
| 3 | Elegir fecha | Confirma tu simulación | Hay dos fechas disponibles. |
| 4 | Ver estado | Validación en curso | El responsable del área registrará las métricas. |
| Final | Ver resultado | Proceso finalizado | La información puede considerarse para futuras convocatorias si vuelve a postular. |

Cada tarjeta muestra estado, nivel, puesto, etapa, días sin cambios, progreso de cinco etapas, próxima acción y `Ver detalle`.

Incluye `Privacidad y control` con acción `Abrir opciones`.

## C06. Evaluación integral

**Características:**

- Barra de avance, `Pregunta n de total` y porcentaje.
- Estado `Respuesta guardada` / `Guardando…`.
- No permite volver a preguntas anteriores.
- Exige respuesta antes de continuar.
- Acción `Guardar y continuar`.
- Ayuda de reconexión: permite retomar desde la última respuesta guardada.
- En la demo empieza en la pregunta 47 y, tras tres preguntas de muestra, pasa a la prueba del puesto.

**Preguntas exactas de la demostración:**

1. Selección única: `Un entregable importante se retrasará por un bloqueo externo. ¿Qué haces primero?`
   - Espero a tener una solución completa antes de avisar.
   - Aviso el riesgo, explico el impacto y propongo una siguiente acción.
   - Trabajo más rápido sin involucrar a nadie.
   - Cambio el plazo sin comunicarlo.
2. Texto largo: `Cuéntanos una situación real en la que detectaste un riesgo antes de que afectara un resultado.`
   - Campo `Tu respuesta`.
   - Placeholder: `Describe el contexto, qué hiciste, qué resultado obtuviste y qué aprendiste.`
3. Selección única: `Tu equipo repite un error que ya fue corregido. ¿Cómo respondes?`
   - Corrijo personalmente cada entrega.
   - Aclaro el estándar, verifico la causa y establezco un control que evite repetirlo.
   - Espero a que el equipo lo resuelva solo.
   - Reasigno el trabajo sin explicar el motivo.

## C07. Prueba del puesto

Esta ruta tiene dos estados principales.

### Estado 1: antes de iniciar

- Recomienda usar computadora.
- Reto: registrar bloqueos, priorizarlos y asegurar responsable y próxima acción.
- Entrega:
  - Propuesta funcional o visual.
  - Explicación breve de decisiones.
  - Evidencia de verificaciones.
- Informa minuto del cambio y tiempo adicional según la vacante.
- Muestra duración total y advierte que no puede pausarse.
- Acción `Empezar prueba`, seguida de confirmación.

### Estado 2: prueba activa

- Cronómetro real restante.
- Instrucción: registrar bloqueos con impacto, responsable, plazo y próxima acción.
- Campos:

| Campo | Tipo | Valor inicial |
|---|---|---|
| Enlace a Figma, GitHub o Drive | Texto | `https://figma.com/file/demostracion` |
| Archivo | Archivo | Vacío |
| ¿Qué parte hiciste con IA y qué verificaste tú? | Texto largo | `Usé IA para explorar alternativas. Verifiqué el flujo, los estados y los casos límite antes de entregar.` |

Acción `Entregar prueba`. Usar IA está permitido, pero se evalúa comprensión y verificación.

### Estado interno: cambio inesperado

Texto exacto: `Ahora cada responsable puede tener un máximo de cinco bloqueos activos.` Debe adaptar la propuesta sin perder información y explicar la decisión. Aparece un segundo cronómetro con el tiempo adicional.

## C08. Simulación

Esta ruta tiene dos estados principales.

### Estado 1: elegir fecha

| Opción | Valor enviado | Detalle |
|---|---|---|
| Viernes 22 de agosto · 09:00 | `22 de agosto · 09:00` | Sala principal · Renaser Consulting · 6 de 8 cupos ocupados |
| Domingo 24 de agosto · 15:00 | `24 de agosto · 15:00` | Sala principal · Renaser Consulting · 3 de 8 cupos ocupados |

Campo de selección única obligatorio. Acción `Confirmar asistencia`. Aclara que el correo de confirmación no incluye preguntas.

### Estado 2: asistencia confirmada

- Muestra fecha elegida, Sala principal, Renaser Consulting y llegada 15 minutos antes.
- Estado `Asistencia confirmada`.
- Fases: Contexto 10 min, Preguntas 10 min, Ejecución 65 min, Cambio 20 min, Entrega 10 min, Conversación 5 min.
- Debe llevar documento de identidad, computadora con cargador y acceso a herramientas habituales.
- Control de demo: `Simular avance a validación`.

## C09. Estado o resultado

Esta ruta tiene cuatro variantes completas.

### Variante 1: postulación retirada (`CERRADA`)

Informa que dejó de recibir avisos y que retirar no elimina los datos. Acciones: `Ver otras vacantes`, `Gestionar mis datos`.

### Variante 2: no continúa (`NO_CONTINUA`)

Mensaje `Gracias por participar`. Explica futuras convocatorias y que debe postular nuevamente. Acciones: `Ver otras vacantes`, `Gestionar mis datos`.

### Variante 3: validación en curso (etapa 4)

- Validación de siete días.
- Nueve métricas cargadas por el responsable del área.
- Prevalece el trabajo real si contradice una predicción.
- Línea de tiempo:
  - Inicio registrado — Día 1 · expediente abierto.
  - Trabajo en curso — Día 4 · esperando métricas del jefe del área.
  - Decisión final — pendiente al finalizar el día 7.

### Variante 4: proceso activo genérico

Muestra etapa, texto `Tu proceso sigue activo` y acción `Volver al panel`.

## C10. Estado compartido: acceso necesario

Este estado reemplaza Dashboard, Evaluación, Prueba, Simulación o Estado cuando no existe sesión.

- Título: `Ingresa para ver tu proceso.`
- Explica que evaluaciones y estados solo están disponibles dentro de la cuenta.
- Acción: `Ingresar a la demostración`.

No es una ruta adicional; suma un estado completo al inventario.

---

## 3.4 Ventanas modales del portal

## CM01. Ingresar

| Campo | Tipo | Valor inicial |
|---|---|---|
| Correo | Correo | camila@ejemplo.pe |
| Contraseña | Contraseña | Demo12345! |

Aclara que es acceso de demostración y no valida una contraseña real. Acciones: `Cancelar`, `Entrar`.

## CM02. Tratamiento de datos

Sin campos. Explica:

- Finalidad: evaluar las postulaciones enviadas.
- Participación de IA: ayuda a calificar y conserva explicación.
- Almacenamiento: puede ser fuera del Perú y puede solicitarse eliminación.

## CM03. Confirmar inicio de la prueba

Sin campos. Título `¿Empezar ahora?`; advierte que el tiempo no se detiene y se entrega lo guardado al terminar. Acciones: `Aún no`, `Sí, empezar`.

## CM04. Entregar prueba

| Campo | Tipo | Regla |
|---|---|---|
| ¿Dónde podría fallar tu solución? | Texto largo | Obligatorio; placeholder `Explica un riesgo y cómo lo verificarías.` |

Advierte que después no podrá modificar archivos ni enlaces. Acciones: `Seguir revisando`, `Entregar`.

## CM05. Privacidad y control

Muestra postulaciones activas con acción `Retirar` y explica que retirar no elimina los datos. También permite `Solicitar eliminación`; se eliminarían datos personales y respuestas, mientras la auditoría permanecería sin identificar.

Acciones: `Restablecer demo`, `Cerrar`, `Solicitar eliminación` y un `Retirar` por cada proceso activo.

---

# 4. Reglas y transiciones simuladas

## Panel interno

- Crear/editar vacante actualiza datos locales.
- Publicar cambia la vacante a `Publicada`.
- Cerrar exige motivo y cambia a `Cerrada`.
- Invitar guarda el índice de la persona e impide mostrar de nuevo la misma acción.
- Publicar fechas cambia a María a `SIMULACION_POR_ELEGIR`, responsable `Candidato`, 0 días.
- Guardar nueve métricas cambia a `DECISION_PENDIENTE`, responsable `Jefe del área`, 0 días.
- Decisiones y publicaciones de pruebas agregan registros al historial.
- Restablecer demostración devuelve todos los valores iniciales.

## Portal del candidato

- Crear cuenta guarda nombre, correo y sesión local.
- Enviar una postulación nueva crea `CV_CALIFICANDO` en etapa 0.
- Completar las tres preguntas demo cambia a etapa 2 y `PRUEBA_PENDIENTE`.
- Entregar la prueba cambia a etapa 3 y `SIMULACION_POR_CONFIRMAR`.
- Confirmar fecha conserva la fecha seleccionada.
- Simular validación cambia a etapa 4 y `VALIDACION_EN_CURSO`.
- Retirar cambia a etapa final y `CERRADA`.
- Restablecer demo devuelve cuenta y procesos al estado inicial.

---

# 5. Observaciones importantes para implementación

1. **El inventario describe lo que existe en el mockup.** Cuando un valor difiera de los requisitos, el requisito vigente debe decidir la implementación.
2. El mockup del portal usa `90`, `60` y `50` preguntas por nivel. La documentación funcional también menciona en otros lugares `126 a 138` y el modelo de datos especifica `126`, `96` y `86`; esta cantidad debe cerrarse antes de construir la evaluación real.
3. La regla de pesos del modal suma CV 15%, evaluación 25%, prueba 35% y simulación 25%, sin incluir Validación. Los pesos funcionales varían por nivel y sí consideran Validación; no debe copiarse esta distribución sin validación del cliente.
4. El formulario de vacante dibuja descripción y plantilla, pero el prototipo actual no las persiste.
5. El portal muestra un límite de CV de 10 MB, pero no lo valida técnicamente.
6. Los archivos HTML trabajan solo con datos locales y temporizadores del navegador. No implementan autenticación, correo, carga real de archivos, evaluación de IA, base de datos ni permisos de servidor.
7. Los permisos visuales del mockup sirven para presentar el flujo. En el producto real deben comprobarse en el servidor.
8. El panel incluye áreas de Talento posteriores a la contratación para conservar el contexto de RENASER OS; el nuevo módulo de reclutamiento se concentra en `Selección de personal`.

---

# 6. Matriz final de cobertura

| Área funcional | Pantalla o superficie donde aparece |
|---|---|
| Validar necesidad de contratación | A02, AM04 |
| Crear/publicar/cerrar vacante | A05, AM03, AM12 |
| Banco de talento | A03 |
| Seguimiento de personas | A04, A06, A13–A18 |
| Preguntas y claves internas | A08, AM05, A15 |
| Prueba del puesto | A09, A16, C07, CM03, CM04 |
| Simulación y asistencia | A07, AM15, AM16, C08 |
| Validación de siete días | AM17, C09 |
| Decisión y recomendación | A18, AM13, AM14 |
| Resultados y predicción | A01, A10 |
| Roles y permisos | Selector global, A08, AM10 |
| Auditoría | A13, AM11 |
| Privacidad y consentimiento | C03, C05, C09, CM02, CM05 |
| Tutorial guiado | A04, AM02 y franjas contextuales |

