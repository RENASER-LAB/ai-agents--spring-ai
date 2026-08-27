# RENASER OS · AI Engine

Backend de orquestación de agentes IA para RENASER OS. Implementa los 15 agentes del contrato `RENASER_AGENT_CONSTITUTION_V2`: cada uno recibe un `objective`, razona con un modelo de lenguaje sobre datos reales (cuando existen) y devuelve un envelope estructurado (`severity`, `facts`, `missingData`, `confidence`, `humanGate`, `nextActions`, `routing`, `payload`).

Este servicio cubre la parte de razonamiento con IA. Los 8 motores determinísticos y las 16 tools/herramientas del contrato V2 son responsabilidad de otro servicio (fuera de este repo).

En el mismo repositorio vive el **módulo de selección de personal** (`/api/v1/panel` y
`/api/v1/portal`), que hoy es la mayor parte del código: vacantes, postulaciones, criba de
currículums con IA, banco de preguntas y evaluación. Su documentación está en
[`docs/`](docs/README.md) — empieza por [Qué hace el sistema](docs/00-QUE-HACE-EL-SISTEMA.md).

## Stack

- Java 25 · Spring Boot 4.1 · Spring AI 2.0.0
- PostgreSQL + pgvector (persistencia de corridas + vector store del RAG)
- RabbitMQ (ejecución async y fan-out de agentes vía `routing[]`)
- DeepSeek y Google Gemini, por API (no hay modelos locales)
- Supabase (bucket de currículums, y fuente de datos reales para algunos agentes)

## Modelos usados

Los dos son de pago y se llaman por API: **no hace falta instalar nada** para usarlos, solo
tener las claves en `application-secrets.yaml`.

| Modelo | Uso | Notas |
|---|---|---|
| `deepseek-v4-flash` | El que decide: califica currículums y razona en los 15 agentes | Temperatura 0 en todo lo que pone una nota, para poder repetir una corrida si un candidato reclama |
| `deepseek-chat` | La primera pasada de la criba | Contesta sin razonar: ~19 s por currículum en vez de 48. Sirve para ordenar la tanda, no para decidir a quién se contrata |
| `gemini-embedding-2` | Embeddings del vector store (RAG) | 1536 dimensiones. Solo en la ingesta y búsqueda de documentos |

Hubo una etapa con modelos locales por Ollama (`gemma4:e4b`, `qwen3-embedding:0.6b`) y se
abandonó. Si encuentras una referencia a Ollama en algún documento, está desfasada.

## Requisitos previos

- Java 25 (basta con tenerlo en el `PATH`; `mvnw` lo encuentra)
- Docker (para Postgres + RabbitMQ vía `docker-compose.yml`)

## Configuración

La mayoría de la config vive en `src/main/resources/application.yaml` con valores por defecto que calzan con `docker-compose.yml` (uso local, no son secretos de producción):

- Postgres: `localhost:5433`, db `renaser_db`, user `postgres`
- RabbitMQ: `localhost:5672`, user `guest`

Los secretos van en `application-secrets.yaml`, que está en `.gitignore` y nunca se sube:

```bash
cp application-secrets.yaml.example application-secrets.yaml
```

Ese archivo de ejemplo explica cada valor y de dónde sacarlo. No todos pesan igual:

| Secreto | Si falta |
|---|---|
| `google.gemini.api-key` | No arranca |
| `app.seguridad.jwt-secreto` | No arranca (mínimo 32 bytes) |
| `app.archivos.supabase.clave` | No arranca, **salvo con el perfil `local`**, que no usa el bucket |
| `spring.ai.deepseek.api-key` | Arranca, pero la primera calificación devuelve 401 |

## Levantar el proyecto en tu máquina

```bash
docker-compose up -d
./mvnw spring-boot:test-run
```

No hace falta nombrar el perfil: `local` es el que se aplica cuando nadie dice lo contrario.
Deja en tu máquina **todo lo que guarda estado**: la base es el contenedor del compose, la
cola es el RabbitMQ del compose y los currículums viven en memoria. Nada escribe en Supabase
ni en la cola compartida del servidor. Los modelos siguen siendo APIs externas: eso ningún
perfil lo cambia.

Es `spring-boot:test-run` y no `spring-boot:run` por una razón concreta: el único almacén de
archivos del código de producción es el bucket de Supabase, y el doble en memoria vive en
`src/test` a propósito, para que nadie pueda encenderlo por descuido en un entorno de verdad.
`test-run` arranca la misma aplicación con el classpath de pruebas, que es donde ese doble está.

Por eso **`./mvnw spring-boot:run` ya no sirve para el día a día**: con el perfil `local` pide
el almacén en memoria, que en ese classpath no existe, y se queda sin un bean que inyectar.
Si necesitas ese comando, tienes que nombrar otro perfil y tener la clave del bucket.

La API queda en `http://localhost:8081` (el perfil mueve el puerto: el 8080 suele estar
ocupado). Documentación interactiva (Swagger):

```
http://localhost:8081/swagger-ui/index.html
```

### Los perfiles

`application.yaml` se lee siempre, y con él `application-secrets.yaml`. Un perfil solo añade
encima lo que cambia respecto de eso, y gana sobre los dos. Quien manda, de menos a más
fuerza: `application.yaml` → `application-secrets.yaml` → el perfil activo → los argumentos
del comando.

**Si no nombras ninguno, se usa `local`** (`spring.profiles.default`). Antes «ninguno»
significaba una mezcla: base local, pero cola compartida y currículums en el bucket de
verdad. Arrancar sin pensar te metía a medias en un entorno compartido; ahora no.

| Perfil | Base de datos | Cola | Currículums |
|---|---|---|---|
| `local` — **el de por defecto** | Contenedor del compose | Contenedor del compose | En memoria |
| `pruebas` | Supabase | Contenedor en la EC2 | Bucket de Supabase |

`pruebas` no es «para probar en tu máquina»: es **el servidor desplegado**, y lo activa
`despliegue/docker-compose.yml` con `SPRING_PROFILES_ACTIVE=pruebas`. Nunca lo arranques tú.

Un perfil activo explícito gana sobre el de por defecto, así que el servidor no se ve
afectado por nada de lo de arriba.

## Flujos multi-agente (paso a paso)

Un flujo arranca con un agente y se va encadenando solo: cada agente devuelve `routing[]` y eso
dispara a los siguientes (fan-out en paralelo vía RabbitMQ, con tope de profundidad y de número
total de corridas para que la cadena no se vaya de las manos).

```bash
# Arranca el flujo, responde de inmediato con el flowId
POST /api/v1/flows/execute
{"agentType":"ORCHESTRATOR","entityId":"caso-1","objective":"..."}
→ 202 {"flowId":"..."}

# Traza paso a paso — se puede consultar mientras avanza
GET /api/v1/flows/{flowId}
```

La traza devuelve, por cada paso: qué agente actuó, **quién lo disparó** (`parentRunId`), su
profundidad en el árbol, cuánto tardó, su `severity`, los hechos que citó, los datos que declaró
faltantes, y a quién enrutó. El `status` global es `EN_CURSO` hasta que terminan todos.

## Datos reales por agente

Algunos agentes leen datos reales desde Supabase (ver `AgentExecutionServiceImpl.buildUserMessage`). Los agentes company-wide (CONSULTING, OPERATIONS, GROWTH, AUDITOR, NARRATIVE_MESSAGE) ignoran `entityId`; COLLECTIONS y EVENT lo usan como filtro (nombre exacto de cliente/evento en la tabla correspondiente). CEO, CLIENT_SUCCESS, QA_GOVERNANCE y DIAGNOSTIC todavía no tienen tabla con datos reales que consultar. FINANCE y TALENT_INTELLIGENCE quedan fuera a propósito: esas partes de la app (pagos, postulaciones) siguen en desarrollo.
