# CI/CD: qué se comprueba solo, y cómo se despliega

Desde ahora cada cambio pasa por una tubería automática antes de llegar a nadie.
La regla es una sola: **si algo sale rojo, el cambio no se fusiona.**

## Qué corre en cada Pull Request

| Paso | Qué comprueba | Dónde está |
|---|---|---|
| Compilar y probar | Los tests unitarios y los de integración (levanta Postgres y RabbitMQ de verdad con Testcontainers) | `mvn verify` |
| Reglas de arquitectura | Que las fronteras acordadas se respeten: controladores sin repositorios, la frontera con el módulo de agentes, la lista del candado de Swagger | `ArquitecturaTest` |
| Patrones prohibidos | Que la IA no lea el currículum sin anonimizar, que no aparezcan rutas públicas nuevas fuera de `ConfiguracionSeguridad`, que ningún error se trague por consola | `.semgrep/` |
| Secretos filtrados | Que ninguna clave quede en el código ni en la historia de git | Gitleaks |
| Cobertura de lo nuevo | Que las líneas que **este PR** añade o cambia tengan tests (~80%). No pide nada sobre el código viejo | SonarCloud |

La prueba que llama a DeepSeek de verdad (`CalificacionIaRealIT`) **no corre aquí**:
solo se enciende a mano con la variable `RENASER_IA_REAL`:

```bash
RENASER_IA_REAL=si ./mvnw verify -Dit.test=CalificacionIaRealIT
```

## Qué pasa al fusionar a main

1. La misma tubería corre otra vez.
2. Si todo queda en verde, se construye la imagen, se sube a ECR y se le dice a la
   máquina de EC2 que se actualice. Antes esto era un aviso a Render; ahora Render
   construye la imagen con el `Dockerfile` y la publica en **Pruebas**.
3. Un *smoke test* espera hasta 10 minutos (el plan gratuito arranca en frío) a que
   la aplicación conteste. Si no contesta, el despliegue queda marcado en rojo.

Render **no** despliega solo: su auto-deploy está apagado a propósito. Si desplegara
con cada push, lo haría también con la tubería en rojo, y todo esto no serviría de nada.

## Qué corre de noche (no bloquea a nadie)

| Trabajo | Qué hace |
|---|---|
| Fuzzing | Schemathesis lee el contrato OpenAPI y bombardea los endpoints con entradas raras. Un 500 es un bug: a la basura se contesta 400. Corre contra una aplicación local del runner con la IA apagada y claves de mentira — nunca contra Render |
| PIT | Mutation testing: mete fallos a propósito en los cuatro paquetes de reglas (`postulacion`, `perfilintegral`, `pesos`, `seguridad`) y comprueba que algún test los cace. Tarda ~40 segundos. El reporte queda como artefacto de la corrida |

Si el nocturno sale rojo, alguien lo revisa por la mañana. No bloquea PRs.

## Los secretos y variables (en GitHub → Settings → Secrets and variables → Actions)

| Nombre | Tipo | Qué es |
|---|---|---|
| `SONAR_TOKEN` | Secreto | Lo genera SonarCloud al importar el repositorio |
| ~~`RENDER_DEPLOY_HOOK`~~ | — | **Ya no hace falta.** El despliegue va a AWS |

**Y no hay ninguna clave de AWS guardada en GitHub**, a propósito. Se usa OIDC: GitHub firma
un token de un solo uso, AWS lo verifica y devuelve credenciales que caducan en minutos. Una
clave de acceso guardada como secreto no caduca nunca — si se filtra, sigue valiendo.

Lo que lo hace posible, ya creado en la cuenta:

| Qué | Cuál |
|---|---|
| Proveedor OIDC | `token.actions.githubusercontent.com` |
| Rol que asume GitHub | `github-despliegue` |
| Quién puede asumirlo | **Solo este repositorio**, por la condición `sub` del token |
| Qué puede hacer | Subir a `ai-engine` en ECR, y `SendCommand` **solo** sobre `i-05fc037e853d07264` |

La imagen se sube con **dos etiquetas**: `latest`, que es la que despliega, y el SHA del
commit. Esa segunda es la que permite volver atrás: sin ella «la versión anterior» no tiene
nombre y revertir obliga a reconstruir desde el código.
| `PRUEBAS_URL` | Variable | La URL pública de la aplicación en Render (ej. `https://ai-engine-xxxx.onrender.com`) |

## Las variables de entorno del servicio en Render

| Variable | Valor |
|---|---|
| `SPRING_PROFILES_ACTIVE` | `pruebas` |
| `SPRING_DATASOURCE_URL` | La cadena **directa** de Supabase, puerto **5432** — no la del pooler (6543), que rompe las migraciones de Flyway |
| `SPRING_DATASOURCE_USERNAME` | `postgres` |
| `SPRING_DATASOURCE_PASSWORD` | La contraseña de la base de Supabase |
| `RABBITMQ_HOST` | El host de CloudAMQP |
| `RABBITMQ_VHOST` | El vhost de CloudAMQP (lleva el nombre del usuario) |
| `RABBITMQ_USERNAME` | El usuario de CloudAMQP |
| `RABBITMQ_PASSWORD` | La contraseña de CloudAMQP |
| `DEEPSEEK_API_KEY` | La clave de DeepSeek |
| `GOOGLE_GEMINI_API_KEY` | La clave de Gemini (embeddings) |
| `JWT_SECRETO` | Una clave nueva de 32+ caracteres, **distinta** de la local |

## Configuración que se hace una sola vez

1. **SonarCloud**: entrar a sonarcloud.io con la cuenta de GitHub, importar el
   repositorio, apagar «Automatic Analysis» (el análisis lo manda el CI), generar un
   token y guardarlo como secreto `SONAR_TOKEN`. La organización y la clave del
   proyecto ya están en el `pom.xml`, y **son dos cosas distintas que se confunden
   muy fácil**:

   | Propiedad | Valor | Ojo |
   |---|---|---|
   | `sonar.organization` | `renaser-lab-1` | En minúsculas. «RENASER-LAB» es el nombre que se ve en pantalla, no la clave. Lleva `-1` porque existen dos organizaciones con ese nombre y el proyecto está en la segunda |
   | `sonar.projectKey` | `RENASER-LAB_ai-agents--spring-ai` | Esta sí conserva las mayúsculas |

   Si algún día hay que confirmarlos, se preguntan a SonarCloud en vez de adivinarlos:

   ```bash
   curl -s "https://sonarcloud.io/api/components/search_projects?organization=renaser-lab-1"
   ```
2. **Supabase** (el proyecto de Pruebas ya existe): en el SQL Editor, ejecutar
   `create extension if not exists vector;`. Copiar la cadena de conexión directa.
3. **CloudAMQP**: crear una instancia gratuita y copiar host, vhost, usuario y contraseña.
4. **Render**: crear un Web Service desde el repositorio con runtime **Docker**,
   apagar el auto-deploy, cargar las variables de la tabla de arriba, y copiar la URL
   del Deploy Hook (Settings → Deploy Hook) al secreto `RENDER_DEPLOY_HOOK` y la URL
   pública a la variable `PRUEBAS_URL`.
5. **Protección de la rama main** (solo puede hacerlo el dueño del repositorio, con
   permisos de administrador): Settings → Branches → Add branch protection rule →
   rama `main`, marcar «Require a pull request before merging» y «Require status
   checks to pass» eligiendo `compilar y probar` y `análisis estático`. El check de
   SonarCloud se agrega como obligatorio dos semanas después, cuando el equipo ya
   conozca sus números.

## Después del primer despliegue, comprobar una cosa

En Supabase, correr `select version, type from flyway_schema_history order by installed_rank;`
y confirmar que la **V1 aparece ejecutada (tipo SQL), no como baseline**. Si aparece
como baseline, la tabla `agent_run` no existe y la aplicación no va a arrancar: borrar
el esquema y volver a desplegar.

## Limitaciones conocidas de esta fase

- **El disco de Render es efímero, y ya no importa**: los currículums viven en el
  bucket privado de Supabase (ver `docs/ARCHIVOS-EN-BUCKET.md`), así que un despliegue
  no pierde nada. Lo único que muere con el contenedor es lo que nadie debería guardar
  en su disco.
- **Supabase gratuito se pausa a los 7 días sin uso** y despierta en ~30 segundos.
  El smoke test ya lo tolera. En cuanto entre el primer candidato real, Producción
  necesita plan de pago.
- **El correo sigue sin salir** (`EnviadorCorreoLog`): se registra en la base, no se envía.
