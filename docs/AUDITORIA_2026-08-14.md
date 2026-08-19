# Auditoría técnica — AI Engine (RENASER OS)

**Fecha:** 14 de agosto de 2026
**Alcance:** código completo del motor de agentes, con foco en seguridad de datos sensibles, escalabilidad, y mantenibilidad.

---

## Resumen

El sistema funciona hoy, pero funcionaba **por el tamaño de los datos, no por el diseño**. Varias consultas no tenían límite ni orden: con 5 prospectos daba igual, con 50.000 desbordan la ventana de contexto del modelo y producen el mismo fallo silencioso que ya vimos (JSON truncado). Eso ya está corregido.

Lo que **no** está resuelto y es la decisión más importante pendiente: **el servicio no tiene autenticación**. Cualquiera con acceso al puerto lee deudas de clientes y nombres de empleados. Mientras corra en localhost no hay exposición; el día que se despliegue, sí.

---

## 1. Seguridad

### 1.1 Sin autenticación en ningún endpoint — **CRÍTICO, sin resolver**

Todos los endpoints son públicos. `GET /api/v1/supabase/cobros?cliente=X` devuelve deuda real de un cliente sin pedir credenciales. `GET /api/v1/agent-runs/history/{id}` expone el razonamiento completo con datos sensibles embebidos.

**Riesgo:** desplegar esto en un VPS con IP pública sin firewall = fuga total de datos financieros y de RR.HH. de la empresa.

**Qué hacer:** Spring Security con API key o JWT antes de cualquier despliegue. Si el consumidor va a ser RENASER OS (el frontend), lo natural es validar el mismo JWT de Supabase que ya emite el login — así el motor hereda la identidad del usuario y se puede auditar quién preguntó qué.

### 1.2 `service_role` bypasea RLS por completo — **ALTO, sin resolver**

La key que usamos salta todas las políticas de Row Level Security. Un fallo en el motor = acceso total de lectura/escritura a la base de producción.

**Qué hacer:** crear en Postgres un rol dedicado de solo lectura con `GRANT SELECT` limitado a las 7 tablas que realmente consultamos, y usar ese en vez de `service_role`. Principio de mínimo privilegio.

**Adicional:** esa key se compartió por chat durante el desarrollo; conviene rotarla (Dashboard → Settings → API Keys → regenerar JWT secret). Ojo: eso también invalida la `anon` key en producción, hay que actualizar Vercel a la vez.

### 1.3 Credenciales en repositorio público — **MEDIO, sin resolver**

`application.yaml` está commiteado en un repo **público** con `postgrespassword` y `guest/guest`. Son de localhost, así que el riesgo directo es bajo, pero es el patrón que después se copia a producción.

**Qué hacer:** mover a variables de entorno con defaults locales, igual que ya hicimos con la key de Supabase.

### 1.4 Datos personales enviados al modelo — **MEDIO, decisión de negocio**

Los prompts incluyen nombres completos de empleados y montos de deuda de clientes identificados. Hoy el modelo corre **local**, así que el dato no sale de la empresa — correcto.

**El día que se migre a una API hosteada (OpenAI, Qwen Max, Groq), esos datos salen de la empresa.** Eso deja de ser una decisión técnica: requiere aval explícito y probablemente seudonimizar antes de enviar.

### 1.5 Otros hallazgos

| Hallazgo | Severidad | Nota |
|---|---|---|
| `ddl-auto: update` | Medio | Hibernate puede alterar el esquema en producción. Debe ser `validate` + Flyway. |
| Sin rate limiting | Medio | Una corrida cuesta ~1 min de CPU; sin límite, cualquiera satura el servidor. |
| Swagger público | Bajo | Mapa completo de la API para un atacante. Deshabilitar en el perfil de producción. |
| `agent_run.outputJson` sin retención | Medio | Guarda datos sensibles en claro, para siempre. Necesita política de borrado/archivado. |

---

## 2. Escalabilidad

### 2.1 Corregido hoy

| Problema | Antes | Ahora |
|---|---|---|
| `getProspectos()` sin límite | Traía la tabla entera al prompt | Límite + **agregado por etapa** (Growth razona sobre el embudo, no sobre 50.000 filas) |
| `getActividadesBloqueadas()` con `limit=15` **sin `ORDER BY`** | Devolvía 15 filas arbitrarias de 700 — el agente opinaba sobre una muestra al azar | Ordenado por prioridad y antigüedad: las 15 que importan |
| `getCobrosByCliente()` sin límite | Un cliente con historial largo desbordaba | Límite + orden por vencimiento |
| Avisos sin orden por severidad | Los críticos podían quedar fuera del corte | `crit` primero |
| Prompt sin presupuesto | Concatenación libre → desbordaba la ventana | Techo de 12.000 caracteres, configurable, con aviso en log |

### 2.2 Pendiente

- **Bucle de reintentos en RabbitMQ — CORREGIDO, tras confirmarse con evidencia.** La llamada al modelo ocurre *dentro* del consumidor de la cola, y RabbitMQ reencola el mensaje si el consumidor tarda más que `consumer_timeout`. **No fue hipotético:** en la primera prueba del endpoint de flujo, COLLECTIONS tardó 1.875 s (31,25 min), cruzó el umbral de 30 min y el broker reencoló el mensaje — se encontró en la cola con `redelivered: true`, apuntando a una corrida ya completada y guardada. Al reiniciar la aplicación se habría vuelto a ejecutar entera. **Corregido con cuatro capas:** colas *quorum* con `x-delivery-limit=3` (las clásicas no soportan el límite), *dead letter queue*, `defaultRequeueRejected=false`, y `read-timeout` de 10 min en la llamada al modelo. Verificado publicando un mensaje corrupto a propósito: aterrizó en `agent.dlq` sin tocar la cola de ejecución.
- **`agent_run` crece sin límite.** ~3KB por corrida. A 1.000 corridas/día son ~1 GB/año. Necesita retención (archivar > 90 días) y, más adelante, particionado por fecha.
- **`findByAgentType` sin paginación.** Con miles de corridas carga todo a memoria.
- **Agregación en Java, no en SQL.** Growth agrega el embudo en memoria con tope de 1.000 filas. A mayor escala corresponde una vista SQL (`v_embudo`), igual que la `v_cobranza` que ya existe en el esquema de RenaserOs.

---

## 3. Clean Code / SOLID

### 3.1 Corregido hoy: la violación de OCP que más iba a doler

`buildUserMessage()` era un `switch` con un `case` por agente dentro de `AgentExecutionServiceImpl`. Cada agente nuevo obligaba a **editar la clase central del sistema** — la que orquesta todo. Eso es exactamente lo que el principio Open/Closed busca evitar, y a 15 agentes ya era un método de 90 líneas.

Ahora es una interfaz `AgentContextProvider` con una implementación por agente (8 clases pequeñas, testeables por separado). **Conectar un agente nuevo = crear una clase nueva, sin tocar ninguna existente.** Spring las descubre solo.

Beneficio adicional: es la base natural para *function calling* más adelante — cada provider ya es, conceptualmente, una tool.

### 3.2 Corregido hoy

- **`AgentExecutionServiceImpl` bajó de 11 a 10 dependencias y de ~240 a ~155 líneas**, y dejó de conocer Supabase y RAG. Ahora sólo orquesta.
- **Degradación elegante:** si Supabase cae, el agente ya no revienta con 500 — corre sin contexto y declara el dato faltante. Antes, una caída de Supabase tumbaba la corrida entera.
- **Timeouts** (5 s conexión / 10 s lectura) en el cliente de Supabase. Antes, un cuelgue de Supabase dejaba el hilo esperando indefinidamente.
- **Corridas fallidas quedan marcadas como fallidas.** Antes, un error del modelo dejaba la fila pendiente para siempre, indistinguible de "todavía procesando".
- **`@Transactional`** en los caminos de escritura.
- **Paquete inconsistente** `service.Impl` vs `service.impl` (dos paquetes distintos declarados desde la misma carpeta). Unificado.
- **Evidencia trazable:** ahora se inyecta `evidence_id` junto a cada dato, para que el agente cite el registro real en vez de `"N/A"`.

### 3.3 Pendiente

- **Cero tests.** Es el hallazgo más serio de mantenibilidad: no hay una sola prueba sobre lógica que consulta datos sensibles de producción. Los providers de contexto que se crearon hoy son ahora fáciles de testear de forma aislada — es el punto natural por donde empezar.
- **`SupabaseDataService` es una interfaz que crece con cada agente** (7 métodos). Viola segregación de interfaces. Se puede partir por dominio cuando llegue a ~10.
- **Textos de error con erratas** de cara al usuario: "A ocurrido un error inesaperado", "Interal Server Error", "Timestap".

---

## 4. Memoria y contexto (RAM)

**Pregunta: ¿cada cuándo se limpia la memoria?**

**Respuesta corta: no hay nada que limpiar, por diseño.** Los agentes son *stateless*: cada corrida es prompt → respuesta → base de datos. No existe memoria conversacional acumulándose entre corridas. Esa es una propiedad buena para escalar (permite correr N instancias del motor sin coordinación entre ellas).

Lo que sí ocupa RAM:

| Componente | Consumo | Liberación |
|---|---|---|
| Modelo en Ollama | ~10 GB | Automática: Ollama descarga el modelo tras ~5 min sin uso (`keep_alive`) |
| Heap de la JVM | Transitorio por request | Garbage collector |
| Pool de conexiones (Hikari) | Fijo, pequeño | — |
| Contexto del prompt | Ahora acotado a 12.000 caracteres | Por request |

Lo que **sí crece sin límite** es la tabla `agent_run` en disco (no en RAM) — ver §2.2.

---

## 5. RAG a escala de 1.000.000 de documentos

Esta es la pregunta técnicamente más importante, y la respuesta honesta es: **el cuello de botella no será el agente, será el recuperador.**

Hoy, con ~100 fragmentos, la búsqueda ya falló una vez (la consulta sobre "Human Gate" devolvió fragmentos irrelevantes). A 1M de documentos, una búsqueda vectorial ingenua *top-4* empeora, no mejora.

**Latencia no es el problema:** HNSW resuelve 1M de vectores en milisegundos. **La precisión sí lo es.**

Lo que hay que construir, en orden de impacto:

1. **Filtrado por metadatos antes de la búsqueda vectorial.** Nunca buscar sobre 1M: filtrar primero por tipo/área/proyecto/fecha y buscar dentro del subconjunto. Es el mayor salto de calidad y el más barato. Spring AI ya lo soporta vía `FilterExpressionBuilder`.
2. **Búsqueda híbrida (palabra clave + vectorial).** Postgres ya trae full-text search (`tsvector`). Los embeddings fallan justamente en términos exactos como "Human Gate"; BM25 los encuentra. Fusionar ambos resultados.
3. **Reranking.** Recuperar 50 candidatos y reordenar con un modelo cross-encoder, quedándose con 5. Es la mayor ganancia de precisión por unidad de esfuerzo.
4. **Mejor troceado (chunking).** El `PagePdfDocumentReader` + `TokenTextSplitter` actual destroza tablas — literalmente la causa del fallo que vimos. Hace falta troceo consciente de la estructura del documento.
5. **Dimensionar RAM.** 1M × 1024 dimensiones ≈ 4 GB sólo de vectores, más el índice. Si no entra en RAM, el índice va a disco y ahí sí se cae la latencia.

**Sobre el grafo:** si de verdad es un grafo con relaciones entre documentos (estilo Obsidian), pgvector por sí solo no hace recorrido de aristas. Opciones: guardar las aristas en Postgres y recorrerlas con CTEs recursivos (suficiente para 1-2 saltos), o GraphRAG completo (mucho más caro de construir y mantener). **Recomendación: los puntos 1-4 primero.** Ahí está el 90% del valor; el grafo completo es la última milla.

**¿El agente podrá con 1M de documentos?** Sí — porque nunca va a leer 1M, va a leer los 5-10 fragmentos correctos. El límite es la calidad del recuperador, no el agente. Por eso el presupuesto de tokens que se puso hoy es la pieza que hace esto sostenible.

---

## 6. Modelos y despliegue

### 6.1 El modelo nuevo: Qwen 3.8 Max

Salió el 2-3 de agosto de 2026. **2,4 billones de parámetros, 1M de contexto, $2 / $6 por millón de tokens (entrada/salida).**

**No se puede correr local** — a esa escala es exclusivamente API. Es una opción de *hosting*, no de infraestructura propia.

La alternativa local interesante es **Qwen 3.6 27B**: 256k de contexto, entra en 24 GB de VRAM en cuantización Q4, y trae un parser de *tool calling* dedicado en Ollama — relevante si se va hacia function calling.

### 6.2 Estrategia multi-modelo

La infraestructura ya existe (`AgentModelSelector`); sólo hay que elegir mejor. No tiene sentido usar el mismo modelo para redactar un mensaje que para decidir el enrutamiento de toda la empresa:

| Nivel | Agentes | Modelo sugerido |
|---|---|---|
| Razonamiento | ORCHESTRATOR, CEO, CONSULTING, DIAGNOSTIC, AUDITOR | El más capaz disponible |
| Operativo | COLLECTIONS, OPERATIONS, GROWTH, EVENT, CLIENT_SUCCESS | Modelo medio |
| Redacción/formato | NARRATIVE_MESSAGE, QA_GOVERNANCE | Modelo pequeño |

**Advertencia validada en esta sesión:** el modelo pequeño (`qwen3:0.6b`) falló en los dos casos donde se probó — con contexto real desbordaba su ventana de 4.096 tokens y producía JSON truncado. Cualquier modelo del nivel "pequeño" debe validarse **con contexto real inyectado**, no con prompts de juguete.

### 6.3 Velocidad: el objetivo de <30 s

**En CPU no se alcanza.** Hoy estamos en 30 s – 2,5 min. Los caminos reales:

| Opción | Latencia esperada | Datos salen de la empresa |
|---|---|---|
| GPU propia (L4 24GB / RTX 4090) | 2–8 s | No |
| API hosteada (Groq, Qwen Max, etc.) | 1–5 s | **Sí** |
| Modelo más pequeño en CPU | 15–40 s, con pérdida de calidad | No |

**Aclaración importante:** un modelo *más grande* no es más rápido. `gemma3:12b` denso sería **más lento** que el actual `gemma3n:e4b` (que activa ~4B parámetros efectivos). El salto de velocidad lo da la GPU, no el tamaño del modelo.

### 6.4 Costos aproximados

Una corrida típica del sistema consume ~2.000 tokens de entrada y ~800 de salida.

| Opción | Costo estimado |
|---|---|
| Qwen 3.8 Max API | ~$0,009 por corrida → ~$0,036 por flujo de 4 agentes → **~$270/mes** a 1.000 corridas/día |
| GPU en cloud (L4 24 GB) | **~$500–850/mes** 24/7 |
| GPU dedicada (RTX 4090 alojada) | **~$300–500/mes** |

**Punto de equilibrio:** por debajo de ~1.500 corridas/día la API sale más barata que la GPU dedicada, y sin mantener infraestructura. Por encima, conviene hardware propio. **Pero el criterio decisivo no es el costo, es §1.4: si los datos pueden salir de la empresa o no.**

---

## 7. Prioridades sugeridas

1. **Autenticación antes de cualquier despliegue.** (§1.1) — bloqueante.
2. Rol de Postgres con permisos mínimos en vez de `service_role`. (§1.2)
3. Decidir la postura sobre datos sensibles y modelos hosteados. (§1.4) — condiciona toda la estrategia de infraestructura.
4. Tests sobre los providers de contexto. (§3.3)
5. Retención de `agent_run`. (§2.2)
6. Mejoras de RAG en el orden 1→4 de §5, antes de cargar volumen grande.
