# Trabajar en local

Cómo se levanta el backend, con qué perfil, cómo se corren los tests, dónde están las pantallas y
qué trampas tiene montar un backend propio para una rama. Las reglas del código están en
[Reglas del código](REGLAS-DEL-CODIGO.md); la tubería de despliegue, en [CI/CD](CI-CD.md).

---

## Con qué está hecho

| | |
|---|---|
| Java | 25 |
| Spring Boot | 4.1 |
| Base de datos | PostgreSQL con pgvector (`pgvector/pgvector:pg16`), en `docker-compose.yml`, puerto 5433 |
| Cola | RabbitMQ, para calificar en segundo plano |
| IA · chat | Spring AI con **DeepSeek** (`deepseek-v4-flash`), API externa |
| IA · embeddings | **Google Gemini** (`gemini-embedding-2`, 1536 dimensiones), API externa |
| Otros | MapStruct, Lombok, hilos virtuales, Flyway, Security, JWT, Validation, Swagger |

Las claves viven en `application-secrets.yaml`, en la raíz y fuera de git (hay un
`.example`). El `application.yaml` lo carga con `spring.config.import`, así que no hacen falta
variables de entorno en tu máquina.

**Dónde salen los datos.** El chat va a DeepSeek y los embeddings a Google. Renaser aceptó que
los datos de candidatos salgan hacia esos dos (18/08/2026). El currículum se anonimiza antes de
salir: edad, sexo y estado civil quedan tapados, y se guardan las dos versiones para poder
demostrar que la regla se cumplió.

---

## Los perfiles de configuración

| Archivo | Cuándo se usa | Qué lo activa |
|---|---|---|
| `application.yaml` | siempre, es la base: Postgres local en el 5433, RabbitMQ sin TLS | nada |
| `application-secrets.yaml` | siempre que exista; son tus claves y no se versiona | lo importa `application.yaml` |
| `application-local.yaml` | trabajar en tu máquina sin tocar nada compartido: cola local y archivos en memoria | **nada: es el de por defecto** (`spring.profiles.default`) |
| `application-pruebas.yaml` | la aplicación corriendo en EC2 (Supabase + la cola de la propia máquina) | `SPRING_PROFILES_ACTIVE=pruebas`, lo pone `despliegue/docker-compose.yml` |

Los perfiles son aditivos: cada uno solo escribe encima lo que cambia. Quien manda, de menos a
más fuerza: `application.yaml` → `application-secrets.yaml` → el perfil activo → los argumentos
del comando. La tabla completa con lo que cambia cada uno está en el
[README](../README.md#los-perfiles).

**Si nadie nombra un perfil se aplica `local`.** Antes «ninguno» era una mezcla peligrosa: base
local, pero la cola la ponía `application-secrets.yaml` (que entonces apuntaba a un broker
compartido) y los currículums iban al bucket de verdad.

Eso incluye a los **tests**, que no nombran perfil: corren con `local` encima. Por eso
`application-local.yaml` solo puede cambiar DÓNDE se guardan las cosas, nunca lo que el sistema
HACE. Apagar ahí `renaser.ai.calificacion.habilitada` rompe `FlujoCalificacionIaIT` y
`CalificacionIaRealIT`, que dan por hecho que está encendida.

**Cuidado con el nombre**: el perfil `pruebas` es el entorno de Pruebas, **no** los tests. Cada
test se inyecta además sus propios valores con `@DynamicPropertySource`, que gana sobre todo.

En el servidor **no existe `application-secrets.yaml`**: el `Dockerfile` no lo copia y el import
es `optional:`, así que allí todo llega por variable de entorno. La lista está en [CI/CD](CI-CD.md).

El perfil `supabase` **se borró** el 21/08/2026: apuntaba tu máquina a la única base que hay, la
de los candidatos reales, con Flyway encendido. Ver [Conexión a Supabase](CONEXION-SUPABASE.md).

---

## Arrancar el backend

```
docker compose up -d                       # Postgres (5433) y RabbitMQ
./mvnw spring-boot:test-run -Dspring-boot.run.profiles=local
```

- Es `spring-boot:test-run` y no `run`: el almacén de archivos en memoria que usa el perfil
  `local` es un doble que vive en el classpath de pruebas, y `run` no lo ve. El [README](../README.md)
  lo explica; `.claude/launch.example.json` trae la configuración de ejemplo con el puerto (el `launch.json` real es de cada máquina y no se versiona desde el #67).
- Un mapper de MapStruct que «no existe» al arrancar suele ser un `.class` corrupto del IDE en
  `target/`: borrar `target/` y volver a compilar.
- **El `dev-login` está apagado por defecto** (`app.seguridad.dev-login-activo`); lo encienden
  `application-local.yaml` y las pruebas. **Solo crea al PRIMER usuario del equipo**: mira si ya
  hay alguien de equipo en la organización plataforma y, si lo hay, cualquier otro id recibe «Ese
  id de RENASER OS no está registrado». En una base recién migrada el primero que entre se queda
  el cupo, y el guion de siembra entra el primero: después hay que usar su mismo id.
- **El correo por defecto no sale**: `renaser.correo.transporte` vale `log` salvo que se ponga
  `smtp`, y entonces solo se registra en `correo_enviado`. Para encenderlo hacen falta
  `CORREO_TRANSPORTE=smtp`, `CORREO_REMITENTE` y una cuenta que autentique, y `starttls` en
  `application.yaml`: sin eso Gmail rechaza todo con «530-5.7.0 Must issue a STARTTLS command
  first», que no nombra ni el TLS ni la propiedad que falta.
- **El enlace firmado no falla en local.** `AlmacenArchivosEnMemoria.urlDeDescarga` devuelve
  `memoria://...`, así que `GET /archivos/{id}/enlace` responde 200 con una url que nadie abre.
  Quien lo llame tiene que mirar el esquema de la url y caer a `/archivos/{id}/descarga` cuando no
  sea `http`/`https`.

---

## Un backend propio para una rama, y sus cuatro trampas

Los tests levantan lo suyo con Testcontainers, pero **comprobar una rama a mano (el panel contra
esta API) escribe en la base**, y la del 5433 es la que comparte todo el mundo entre worktrees:
la primera rama que arranca reserva el número de migración para todas. Por eso cada rama que se
prueba a mano levanta **una base Docker temporal propia**, y no toca la principal.

- ⚠️ **La imagen es `pgvector/pgvector:pg16`, no `postgres:16`.** `vector_store` no la crea
  Flyway a propósito: la escribe Spring AI al arrancar (`initialize-schema: true`) con la
  extensión `vector`, y sin ella la aplicación no llega a levantar.
- ⚠️ **Un worktree no tiene `application-secrets.yaml`.** Se importa por ruta **relativa**
  (`spring.config.import: optional:file:./application-secrets.yaml`) y no se versiona, así que
  desde un worktree el archivo no existe: `api-key: ${google.gemini.api-key}` se queda sin
  resolver, la aplicación no arranca y la base se queda sin migrar. Se apunta al del repositorio
  principal **por ruta absoluta**.
- ⚠️ **El vhost de RabbitMQ se fija en la línea de comandos**, que es lo único que gana sobre el
  perfil. `application-local.yaml` clava `virtual-host: /`, y **dos backends de dos ramas en el
  mismo vhost se roban los mensajes**: el trabajo de IA de uno se lo come el otro proceso y la
  calificación no vuelve nunca. La base también va por argumento, no editando el perfil.
- ⚠️ **El `dev-login` solo crea al primer usuario** (ver arriba).

---

## Correr los tests

Son dos tandas y el comando decide cuál corre:

| Comando | Qué corre | Necesita |
|---|---|---|
| `./mvnw test` | unitarias con dobles y las de arquitectura | nada |
| `./mvnw verify` | lo anterior más las de integración | Docker |

El número vigente está en [Estado del proyecto](ESTADO-DEL-PROYECTO.md) y el desglose en
[Comprobaciones automáticas](COMPROBACIONES-AUTOMATICAS.md).

- **El número es el que Maven imprime al final, no el que sale de sumar los informes.** Con `-q`
  esa línea no aparece y hay que ir a `target/*-reports/`, donde las clases anidadas y las
  parametrizadas no se cuentan igual. Correrlo sin `-q` es más barato que reconstruirlo.
- Las de integración levantan su propio PostgreSQL y su propio RabbitMQ con Testcontainers: hace
  falta el demonio de Docker encendido.
- **El test que gasta dinero no corre solo**: `CalificacionIaRealIT` está detrás de
  `@EnabledIfEnvironmentVariable`. Ni `test` ni `verify` llaman a DeepSeek. Para pedirlo:
  `RENASER_IA_REAL=si ./mvnw verify -Dit.test=CalificacionIaRealIT`
- **Ruido esperado que NO es un fallo**: `Failed to check/redeclare auto-delete queue(s)` de
  RabbitMQ, y los tests saltados de `CalificacionIaRealIT`. Si termina en `BUILD SUCCESS`, está bien.
- ⚠️ **Hay UNA unitaria saltada a propósito, y no es ruido: es un defecto abierto.**
  `elCambioInesperadoCabeDentroDelReloj` en `RelojDeLaEtapaTecnicaQaTest`. Cuando se decida qué
  hacer se le quita el `@Disabled` y tiene que pasar. Ver [Defectos conocidos](DEFECTOS-CONOCIDOS.md).
- **Tests con fechas quemadas caducan.** Ya pasó el 24/08/2026; se pasaron a fechas relativas y
  el patrón sigue siendo el riesgo.

---

## Dónde están las pantallas

Ninguna vive en este repositorio. Hay **un** frontend real, con dos caras:

| | Dónde | Qué hace | Cómo se levanta |
|---|---|---|---|
| **`RenaserOsPostulantes`** | `~/Documentos/RenaserOsPostulantes` (repositorio propio, React con Vite) | **Las dos caras**: el portal del candidato (`src/paginas`: postular, evaluación, prueba, seguimiento, perfil) y **el panel de la empresa** (`src/panel`: solicitudes, vacantes, bandeja, ficha, ranking, pruebas, configuración) | `npm run dev`; el proxy de Vite apunta a `API_URL` (por defecto `localhost:8080`, que en esta máquina es Adminer y contesta 200 a todo: poner `API_URL=http://localhost:8081` o el puerto del backend) |
| `RenaserOs` | `~/Documentos/RenaserOs` | Un rediseño del panel **abandonado** (último cambio 21/08/2026). No consultar como referencia de lo que hay | — |

Pese al nombre, el panel está en `RenaserOsPostulantes`. Sus worktrees van en `.claude/worktrees`
con `npm ci` propio. Los mockups de `docs/mockups/` los mantiene otra persona y describen la
versión anterior.

---

## Guiones útiles (`scripts/`)

Todos van por la API, con la cuenta que esté en las variables de entorno.

Cada guion explica en su cabecera por qué existe; esta tabla es solo el mapa.

| Para qué | Guion |
|---|---|
| **Bancos de preguntas** | `importar-banco-v3.py` convierte el PDF del cliente en JSON revisable y comprueba cinco totales (**no transcribir ítems a mano**; sale con ~11 avisos conocidos y eso es lo normal). `comparar-banco-v3-con-base.py` lee texto a texto lo que quedó en la base. `cargar-banco-desde-excel.py` es el ensayo del importador desde Excel. `importar-banco-maestro.py` es del banco v0.1, ya borrado |
| **Bancos CAZATALENTOS** | `revisa-excel-cazatalentos.py` (solo lee), `importar-banco-cazatalentos.py` (sube los tres Excel por el endpoint real), `publicar-bancos-cazatalentos.py`, `completar-y-publicar-pesos-cazatalentos.py`, `comparar-cazatalentos-con-base.py`, `ajusta-sup-r14-r15.py` |
| **Una convocatoria con currículums** | `cargar-convocatoria.py` (vacante + carpeta de CV + criba), `enlaces-de-drive.py`, `listar-cv-de-drive.gs`, `generar-cv-de-prueba.py` (PDF con texto extraíble; los de la siembra no sirven para la IA) |
| **La vacante sin banco (Administrador)** | `cargar-prueba-administrador.py` deja todo montado por la API; `genera-prueba-tecnica-administrador.py` hace el PDF del enunciado |
| **Invitar y calificar en lote** | `invitar.py --a prueba` (`--saltar 1,2,3` deja fuera a alguien); `calificar-pruebas.py --vacante N --de-verdad` (sin `--de-verdad` solo dice a quién se le pediría; cada una cuesta una llamada al modelo); `recalificar-banco.py` |
| **Mirar una tanda** | `lista-para-rrhh.py`, `quienes-respondieron.py`, `excel-de-la-prueba.py`, `exportar-para-demo.py` |
| **Llenar la base local para ver el panel** | `sembrar-datos-de-prueba.py`, `sembrar-evaluacion-local.py` (una evaluación entregada y calificada), `escenario-etapas-local.py` (candidatos en las cinco etapas) |
| **La tubería** | `cobertura-sonar-pr.py` (dónde faltan líneas cuando SonarCloud tumba el PR), `reporte-pruebas.py` (los XML de Maven en una página legible), `prueba-v41-con-rollback.sh` |

⚠️ **Consultar la nota por el endpoint de calificación la guarda**; los guiones que solo miran
calculan la nota por su cuenta. Y en la máquina de trabajo hay guiones más nuevos **sin
versionar** (`cargar-prueba-de-la-empresa.py`, `asignar-prueba-a-vacante.py`,
`preparar-vacante-prueba.py`, `invitar-a-registrarse.py`, `excel-de-curriculums.py`,
`correos-de-los-cv.py`, `textos-de-correo.py`, `crear-cuenta-panel.py`, `diagnosticar-notas-prueba.py`,
`recalcular-nota-prueba.py` y los `prueba-*.json`): hasta que se suban, no
existen para nadie más.
