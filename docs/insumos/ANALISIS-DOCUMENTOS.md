# Qué documento manda sobre cuál

Actualizado: 2026-08-14

Renaser entregó varios documentos que se solapan. Este archivo dice cuál vale para cada tema,
para no construir dos veces lo mismo ni usar una versión vieja.

---

## Actualización del 14/08 · Requisitos funcionales V1.1 FINAL

Se revisó directamente, **sin copiarlo a este repositorio**, el archivo externo:

`01-REQUISITOS-FUNCIONALES_RENASER_TALENT_INTELLIGENCE_V1.1_FINAL (1).docx`

- Título interno: **RENASER Talent Intelligence — Requisitos funcionales V1.1 FINAL**.
- Declara que reemplaza la V1.0 y que debe usarse como fuente funcional para desarrollo.
- Contiene **RF-001 a RF-138**, sin saltos ni duplicados.
- SHA-256 revisado: `ce69d7d59d15fafd859ee8af95e0d38220f95c88dfacada7f0ced5ce131918fa`.
- El archivo fuente permanece en `Descargas`; solo este resultado de análisis queda registrado.

### Nueva regla de precedencia

1. **Requisitos funcionales V1.1 FINAL** manda sobre cualquier documento anterior en
   comportamiento, alcance funcional, pesos, flujo, privacidad, roles mínimos y preparación
   multiempresa.
2. `Sistema_Completo_Talento_RENASER_Seleccion_2026_2029.docx` conserva valor para el texto
   completo de bancos, pruebas y ejemplos que la V1.1 no reproduce, siempre que no contradiga
   una regla de la V1.1 FINAL.
3. `Banco_Maestro_Preguntas...docx` conserva valor para reactivos, claves y vectores de
   dimensión, sujeto a la selección por blueprint exigida por la V1.1 FINAL.
4. Las decisiones técnicas ya confirmadas en el repositorio —Spring Boot, PostgreSQL,
   RabbitMQ, Flyway y almacenamiento privado— siguen vigentes cuando la V1.1 no las cambia.

**Consecuencia:** los documentos numerados actuales todavía describen la versión anterior en
varios puntos. No deben usarse para implementar esos puntos hasta reconciliarlos como un
conjunto; corregir solo un archivo dejaría estados, permisos, base de datos, diagramas y
mockups contando historias diferentes.

### Contradicciones que sí cambian el producto

| Tema | Documentación actual | V1.1 FINAL | Acción necesaria |
|---|---|---|---|
| Origen de una vacante | La vacante se crea directamente | Toda vacante nace de una **Solicitud de Talento**, directa o sugerida por RENASER OS (RF-001–007) | Incorporar solicitud, evidencia de necesidad y vínculo obligatorio con vacante |
| Cierre de vacante | Cierra automáticamente postulaciones activas | Detiene nuevas postulaciones; cada postulación activa se continúa, detiene o mueve al Radar mediante decisión humana (RF-011) | Cambiar transiciones, avisos y reglas de cierre |
| Tipos de convocatoria | Solo se modela abierta/cerrada | Con fechas, hasta cubrir cupos o permanente (RF-008) | Agregar modalidad y reglas de vigencia/cupo |
| Pesos finales | Pesos distintos por nivel: 5/20/30/25/20, con variaciones | Perfil Integral 40%, Prueba 30%, Simulación 15%, Validación 15%; otra distribución requiere versión aprobada (RF-039, RF-092–093) | Reemplazar pesos iniciales y sus diagramas |
| Composición del perfil | CV separado; evaluación con psicometría 30%, banco 50% y alineación 20% | Dentro del 40%: CV 10%, psicometría 5% y Evaluación RENASER 25% (RF-038–040) | Rehacer cálculo, configuración y explicación de UI |
| Psicometría | Instrumento externo no comprado; su peso se redistribuye | Módulo RENASER experimental y versionado al inicio; instrumentos externos quedan como extensión futura; no se ordena redistribuir (RF-040–041) | Eliminar redistribución automática y modelar estado experimental |
| Filtro por CV | La IA descarta por score debajo del mínimo | Solo un requisito objetivo indispensable preconfigurado puede detener automáticamente; una inferencia subjetiva no causa rechazo final sin confirmación humana (RF-034–035) | Eliminar descarte automático por score subjetivo |
| Cantidad de preguntas | Se aplica completo el banco de 90/60/50 más 36 de alineación | El banco es mayor que el examen; se selecciona por nivel, familia, capacidades y blueprint, con meta de 25–50 min y advertencia sobre 60 min (RF-042–044, RF-051) | Sustituir cantidades fijas por blueprint versionado |
| Plazo de evaluación | No hay plazo para empezar ni terminar | El creador define fecha límite para iniciar/completar (RF-046) | Agregar fechas y estados de vencimiento configurables |
| Reutilización | Se reutiliza todo por compartir nivel | Solo componentes vigentes y para la misma familia o una afín; lo específico se regenera (RF-058–060) | Cambiar identidad de evaluación y reglas de reutilización |
| Preguntas de la prueba | 17 preguntas universales obligatorias | La plantilla selecciona 8–10 universales y 3–5 específicas (RF-067–068) | Versionar selección; no precargar 17 obligatorias para todos |
| Rúbrica de prueba | Se permite guardar aunque no sume 100 | Cada rúbrica publicada suma 100 y cada criterio declara quién/cómo lo verifica (RF-069–071) | Bloquear publicación inválida y guardar método de verificación |
| Cambio inesperado | Momento fijo o calculado con un patrón | Una o varias variantes y momento dentro de un rango configurable; no crear un patrón aprendible (RF-065) | Modelar variantes y rango, no solo un minuto fijo |
| Simulación | Siempre grupal y presencial | Puede ser grupal o individual; modalidad, sede/enlace y distribución son configurables (RF-075–078) | Generalizar sesiones y UX |
| Preguntas de conversación | Siempre cinco | Entre 3 y 5, generadas desde contradicciones (RF-082–084) | Cambiar cardinalidad y plantilla |
| Observación en simulación | Incluye inferir cuándo detectó un bloqueo | Solo eventos observables; no registrar un estado mental salvo acción declarada (RF-079–081) | Ajustar eventos, rúbricas y lenguaje |
| Validación práctica | Siempre siete días de trabajo real y métricas manuales | Es configurable y admite simulación extendida no productiva o trabajo productivo bajo figura aprobada; reutiliza datos de RENASER OS cuando existan (RF-085–090) | Cambiar modalidad, duración, control legal y fuente de cada métrica |
| Decisión | Se presenta principalmente como semáforo y «no es un promedio» | Combina score global orientativo, barreras críticas, evidencia y decisión humana; agrega Reserva/Radar y posibles roles alternativos (RF-092–098) | Mantener semáforo, pero sumar score, suficiencia, opciones y Radar |
| Trabajo humano masivo | No existe confirmación por lote | Talento puede confirmar por lote candidatos no priorizados, conservando razón individual y auditoría (RF-056–057, RF-099–100) | Agregar operación por lote segura e idempotente |
| Indicador de automatización | `% de decisiones tomadas por IA` es KPI de Dirección | Expresamente no debe usarse como indicador de éxito; usar horas ahorradas, tiempo, calidad y precisión (RF-101, RF-111) | Retirar KPI actual del panel y mockup |
| Consentimiento | Se acepta al crear cuenta y se informa que los datos quedan para futuras vacantes | La aceptación del proceso ocurre antes de evaluar; el consentimiento de futuras oportunidades es separado y revocable (RF-022–031, RF-115) | Separar textos, propósitos, vigencias y acciones |
| Cuenta del candidato | Correo y contraseña obligatorios | Cuenta o mecanismo de autenticación aprobado por RENASER (RF-018) | No hardcodear un único mecanismo funcional |
| Roles | Cuatro roles propios y Dirección administra todo | Reutilizar roles/permisos de RENASER OS; capacidades mínimas equivalentes a Candidato, Equipo de Talento, Responsable del Área, Dirección y Administrador (RF-120–122) | Integrar, no duplicar, y separar Dirección de Administración |
| Agentes de IA | Un modelo local califica tareas | Nueve agentes RENASER con interfaz de proveedor abstracta y trazabilidad de objetivo, evidencia, modelo, instrucciones y salida (RF-123–128) | Modelar catálogo/versión de agente y procedencia completa |
| Organización | Modelo de una sola empresa | Todas las entidades principales deben admitir organización; V1 oculta multiempresa, pero aplica aislamiento desde el inicio (RF-129–132) | Rediseñar claves, unicidad, permisos e índices por organización |
| Desempeño | Depende de un módulo futuro de RENASER OS | Debe existir un módulo mínimo propio y reutilizar datos de OS cuando estén disponibles (RF-102–107) | Ampliar seguimiento y quitar dependencia total |

### Información importante que no estaba documentada

- **Radar de Talento independiente** de vacantes, con prospectos manuales, referidos,
  convocatorias permanentes, compatibilidad, historial de contacto y consentimiento
  (RF-013–016).
- **Ficha Real del Puesto** y aceptación exportable antes de evaluar, incluyendo versión,
  hash, sesión y datos técnicos disponibles (RF-022–028).
- **Política de conservación configurable**, con eliminación, anonimización o renovación al
  vencer; retiro independiente del consentimiento de recontacto (RF-029–031).
- **Clasificación de claims del CV** en demostrado, no verificado, contradicho o faltante
  (RF-037), no solo «afirmación no verificada».
- **Blueprint de evaluación** por nivel, familia, capacidades críticas y vacante; debe
  congelar selección y orden por candidato (RF-043–046).
- **Repreguntas limitadas del agente** para respuestas superficiales, guardando rúbrica,
  evidencia citada, score, explicación, confianza, agente y versión (RF-048–050).
- **Perfil de Talento** con adecuación, potencial, alto rendimiento RENASER, fortalezas,
  riesgos, talento canalizable, faltantes y confianza; además puede sugerir otro puesto sin
  mover la postulación automáticamente (RF-053–055).
- **Matriz de información crítica de la simulación** para evaluar la calidad de las preguntas
  sin adivinar lo que el candidato pensó (RF-081).
- **Procedencia de cada dato** de validación: RENASER OS, responsable, agente u otra evidencia
  (RF-089–090).
- **Agente de Necesidad, Cazatalentos, Evidencia de CV, Evaluador, Potencial y Riesgo, Prueba,
  Simulación, Desempeño y Aprendizaje**, sin que ninguno cambie reglas activas por sí mismo
  (RF-123–128).
- **Protecciones explícitas** contra inferir salud, diagnósticos, vida familiar o atributos
  protegidos desde foto, video, voz, apariencia o respuestas (RF-133–138).

### Lo que sigue siendo compatible

Se conservan: las dos caras del producto; las cinco etapas visibles; la decisión humana
final; los ocho criterios del CV; las anclas 0–4; el guardado incremental y orden reproducible;
Comprende → Produce → Explica → Se adapta; el cronómetro controlado por servidor; archivos y
enlaces como entregables; versionado inmutable; auditoría; notificaciones sin preguntas ni
claves; seguimiento 30/90/180; y la prohibición de detectores de uso de IA.

### Pendientes que la V1.1 FINAL no elimina

- Texto legal final y periodo exacto de conservación.
- Figura laboral/contractual para habilitar validación productiva.
- Proveedor/modelo operativo inicial y controles aplicables si los datos salen de la
  infraestructura propia.
- Poder final del Evaluador de Estándar por organización; para V1 interna la recomendación
  registrada es válida.
- Valores concretos de familias afines, vigencia de componentes, blueprints, barreras y
  límites de repregunta.

### Impacto documental · estado al 15/08

Las decisiones que resuelven cada contradicción están en
[Qué cambia con el documento nuevo](CAMBIOS-DEL-DOCUMENTO-NUEVO.md). Ese documento es el
registro de lo decidido; este es el de qué manda sobre qué.

| Documento | Estado |
|---|---|
| `01-REQUISITOS-FUNCIONALES.md` | ✅ Reescrito · versión 2.0 |
| `02-REQUISITOS-NO-FUNCIONALES.md` | ✅ Corregido · versión 2.0 |
| `03-ESTADOS-POSTULACION.md` | ✅ Reescrito · versión 2.0, de 25 estados a 18 |
| `04-ROLES-Y-PERMISOS.md` | ✅ Reescrito · versión 2.0, cinco roles y 73 permisos |
| `05-MODELO-DE-DATOS.md` | ⏳ Pendiente |
| `07-DICCIONARIO-DE-DATOS.md` | ⏳ Pendiente |
| `README.md` | ⏳ Pendiente |
| `docs/diagramas/` | ⏳ Pendiente |
| Mockups y su inventario | ⏳ Pendiente · **los mantiene otra persona** |

No se debe «sumar» estas reglas a las anteriores: en los temas de la tabla son un reemplazo.

---

## Análisis histórico de los tres insumos anteriores

> Esta sección conserva la comparación realizada el 13/08. Desde el 14/08, cualquier dato
> que choque con la V1.1 FINAL queda reemplazado por la nueva regla de precedencia.

| Archivo | Qué es | Vigencia |
|---|---|---|
| `Sistema_RENASER_Talent_Intelligence_2026_2029.docx` | Primer borrador. Marco teórico + baterías propias | **Descartado** salvo lo indicado abajo |
| `Banco_Maestro_Preguntas...docx` | Las 200 preguntas con sus claves | **Vigente** para las preguntas |
| `Sistema_Completo_Talento_RENASER_Seleccion_2026_2029.docx` | Versión maestra anterior. Contiene todo lo anterior + lo nuevo | **Fuente de detalle solo cuando la V1.1 FINAL no lo contradice** |

Regla histórica: entre esos tres documentos ganaba `Sistema_Completo`. La V1.1 FINAL ahora
manda sobre los tres.

---

## Verificación hecha (no es opinión, se comparó ítem por ítem)

Se compararon las 200 preguntas entre `Banco_Maestro` y `Sistema_Completo`:

- Mismos 200 IDs en ambos: D01–D90, S01–S60, O01–O50. Ninguno sobra ni falta.
- **0 diferencias en claves de puntuación** (los `A=1, B=0, C=4, D=1`).
- **0 diferencias en vectores de dimensión** (los `VEL+2, INI+1`).
- 123 ítems tienen diferencias, pero **todas son de vocabulario**: `vs`→`frente a`,
  `owner`→`responsable`, `CEO`→`director general`, `expertise`→`conocimiento experto`,
  `challenge`→`prueba`, `cash`→`caja`.

**Conclusión: el banco de preguntas no cambió.** Solo se tradujeron los anglicismos.
Se cargan desde `Sistema_Completo` porque tiene la redacción final.

También se comparó la tabla de criterios de CV entre `Talent_Intelligence` y
`Sistema_Completo`: los 8 criterios y sus pesos son idénticos. Solo cambia el nombre de dos
(`Role skill match`→`Habilidades del puesto`, `Complejidad / alcance`→`Complejidad y alcance`).

---

## Lo que aportó Sistema_Completo antes de la V1.1 FINAL

### 1. Los pesos finales, que antes estaban pendientes

| Componente | Dirección | Supervisión | Ejecución |
|---|---|---|---|
| CV y evidencia | 5% | 5% | 5% |
| Evaluación Integral RENASER | 20% | 20% | 15% |
| Prueba del puesto | 30% | 30% | 40% |
| Simulación 2 h | 25% | 25% | 20% |
| Validación 7 días | 20% | 20% | 20% |
| **Total** | **100%** | **100%** | **100%** |

Los tres suman 100. Queda resuelto el error del documento anterior, donde Ejecución sumaba 105.

### 2. La Evaluación Integral tiene tres partes, no una

Las 200 preguntas son solo la mitad de esa etapa:

| Motor | Peso dentro de la etapa | Estado |
|---|---|---|
| Psicometría | 30% | **No existe.** Es una prueba comprada a un tercero |
| Banco RENASER por nivel | 50% | Las 200 preguntas |
| Alineación Personal | 20% | 36 preguntas nuevas |

### 3. Alineación Personal: 36 preguntas que no estaban en ningún documento previo

| Bloque | IDs | Peso interno | Tema |
|---|---|---|---|
| Relación con dinero y trabajo | M01–M12 | 35% | Incentivos, largo plazo, integridad económica |
| Madurez relacional | R01–R12 | 35% | Conflicto, límites, reparación, retroalimentación |
| Autogestión y sostenibilidad | C01–C12 | 30% | Sostener calidad, avisar antes de fallar |

Resultado: semáforo VERDE / ÁMBAR / ROJO. **No bloquea a nadie.** Sirve para generar las
preguntas de la conversación final.

### 4. Doce pruebas de puesto ya redactadas

Director · Coordinador de Operaciones · Talento/RR.HH. · Crecimiento/Marketing ·
Compra de medios · Desarrollador web · Diseñador gráfico · Editor de video · Ventas ·
Seguimiento/Experiencia del cliente · Producto/UX.

Cada una trae: enunciado completo, qué debe entregar, **cambio inesperado** y puntuación
propia. Ya no hay que inventarlas.

Estructura común de toda prueba: **Comprende → Produce → Explica → Se adapta.**
Al terminar cualquier prueba se hacen 17 preguntas universales (qué problema entendiste,
qué parte hiciste con IA, dónde puede fallar tu solución, etc.).

Puntuación universal sobre 100: Comprensión 10, Resultado 25, Calidad 15, Velocidad 10,
Criterio 10, Uso de IA 5, Explicar 10, Métricas 5, Adaptación 5, Aprendizaje 5.

### 5. La entrevista ya no es una etapa

Antes era un componente aparte con su propio peso. Ahora es la **conversación final de
15–20 minutos** al terminar la simulación. El sistema genera 5 preguntas a partir de las
contradicciones que detectó.

### 6. La decisión final es un semáforo, no una nota

| Resultado | Significado | Acción |
|---|---|---|
| VERDE | Evidencia consistente, sin barrera crítica | Avanza |
| ÁMBAR | Buen potencial pero hay una contradicción | Diseñar prueba específica |
| ROJO | Falla algo indispensable del puesto | No avanza en ese puesto |
| SIN DATOS | No hay evidencia suficiente | Pedir prueba, no asumir que falla |

**Barreras críticas**: fallos que ninguna nota alta compensa.
- Dirección: falta grave de integridad, no sabe priorizar, no controla, no decide.
- Supervisión: no sigue ni controla, comunica tarde, no diferencia desempeño.
- Ejecución: prueba deficiente, repite errores tras corrección, oculta bloqueos.

### 7. La simulación se registra con marcas de tiempo

El sistema debe anotar: hora de inicio, primera pregunta, inicio de ejecución, primera
evidencia, cuándo se introdujo el cambio, primera reacción al cambio, bloqueo detectado,
bloqueo comunicado, entrega, autocrítica.

Fases: Contexto 0–10 · Preguntas 10–20 · Ejecución 20–85 · Cambio inesperado 85–105 ·
Entrega 105–115 · Conversación final 115–120.

---

## Qué se conserva de Talent_Intelligence (el descartado)

Solo esto, y porque no aparece en los otros:

- Los principios de gobernanza (no puntuar edad, sexo, embarazo, raza, religión,
  discapacidad, orientación sexual; no usar familia ni salud como predictor).
- La idea del Bar Raiser (en este proyecto: **solo opina, no bloquea**).
- La lista de riesgos con sus señales válidas e inválidas.

**Sus baterías D01–D90 / S01–S60 / O01–O50 quedan descartadas.** Eran otro instrumento:
usaban escala Likert 1–5 con recodificación N/R. Reutilizaban los mismos IDs para preguntas
distintas, lo que causa confusión al leer los documentos en desorden.

---

## Contradicciones históricas y resolución de la V1.1 FINAL

### CD1 · ¿El CV filtra o no filtra?

- **Audio del 08/08:** el cliente dijo textualmente *"a todos, a todos"*. Todos los que
  postulan reciben las preguntas y la prueba. El descarte es al final.
- **Sistema_Completo:** la Etapa 1 responde *"¿vale la pena invertir una evaluación
  completa?"* con 90–95% de automatización. Eso es un filtro por CV antes de todo.

**Resuelto por RF-034–035:** solo un requisito objetivo indispensable, configurado antes de
evaluar, puede detener automáticamente. El score subjetivo de CV no produce rechazo final sin
confirmación humana.

### CD2 · Simulación: ¿grupal o individual?

- **Audio:** *"más que entrevista es un kickoff, o sea una sesión grupal"*.
- **Sistema_Completo:** trabajo cronometrado con registro individual de tiempos.

**Resuelto por RF-075–078:** la sesión puede ser grupal o individual y presencial o remota.
La sesión grupal puede seguir como valor inicial recomendado, pero no como regla fija.

### CD3 · Validación de 7 días

La V1.1 FINAL conserva el control legal y aclara dos modalidades: simulación extendida no
productiva o trabajo productivo bajo figura aprobada. La duración es configurable; siete días
es un valor inicial, no una obligación universal. **La figura productiva sigue pendiente.**

---

## Lo que sigue pendiente de Renaser

1. Texto legal final y periodo exacto de conservación de datos.
2. Figura laboral/contractual para habilitar la Validación Práctica productiva.
3. Proveedor/modelo operativo inicial y capacidad de infraestructura; funcionalmente debe ser
   intercambiable.
4. Familias afines, vigencia de componentes, blueprints y límites de repregunta iniciales.
5. Poder definitivo del Evaluador de Estándar por organización; V1 interna puede iniciar con
   recomendación registrada.
6. Si al facilitador de la simulación le sirve que el sistema conozca cómo se reparten los
   minutos de la sesión, o la lleva él por su cuenta (ver «Hasta dónde vale esta comprobación»
   en [COMPROBACION-SIN-TECNICA.md](COMPROBACION-SIN-TECNICA.md)).

---

## Comprobación del 15/08 · ¿sobra algo en el modelo?

Se escribió el sistema entero en dos páginas sin una sola palabra técnica y se rastrearon las 93
tablas contra ese texto. **87 se amarran a algo que Renaser pidió por escrito**, 3 a una idea
nuestra y 3 al Radar de Talento, que ya estaba aplazado. No hay grasa que quitar: rehacer la parte
técnica de cero no simplificaría el sistema.

Lo que sí salió es por dónde conviene empezar: recibir postulaciones necesita 33 tablas, y
añadirle el Perfil Integral calificado sube a 67. El detalle está en
[COMPROBACION-SIN-TECNICA.md](COMPROBACION-SIN-TECNICA.md).
