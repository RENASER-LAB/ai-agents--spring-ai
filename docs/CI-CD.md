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
2. Si todo queda en verde, se construye la imagen con el `Dockerfile`, se sube a ECR y
   se le dice a la máquina de EC2 que se actualice. El servidor baja la imagen él solo,
   con su propio rol: desde GitHub no sale ninguna credencial de la aplicación.
3. Un *smoke test* comprueba que se llega desde internet. Si no contesta, el despliegue
   queda marcado en rojo.

**Nada se despliega sin pasar la tubería**: el trabajo de despliegue lleva
`needs: [build, estatico]`, así que con cualquiera de los dos en rojo no llega a correr.

## Qué corre de noche (no bloquea a nadie)

| Trabajo | Qué hace |
|---|---|
| Fuzzing | Schemathesis lee el contrato OpenAPI y bombardea los endpoints con entradas raras. Un 500 es un bug: a la basura se contesta 400. Corre contra una aplicación local del runner con la IA apagada y claves de mentira — nunca contra el servidor de Pruebas |
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
| ~~`PRUEBAS_URL`~~ | — | **Ya no hace falta.** La dirección de Pruebas está escrita en el propio `ci.yml` |

## Las variables de entorno de la aplicación

**Ya no se cargan en ninguna pantalla web**: viven en el archivo `.env` de la máquina, en
`/opt/renaser`, y no salen de ahí. GitHub nunca las ve — el runner solo sube una imagen y
lanza un comando; quien lee el `.env` es el propio servidor.

La lista completa, con de qué clave sale cada valor, está en
[`despliegue/.env.example`](../despliegue/.env.example). Las tres que más se copian mal:

| Variable | Cuidado |
|---|---|
| `SPRING_DATASOURCE_URL` | La cadena **directa** de Supabase, puerto **5432** — la del pooler de transacciones (6543) rompe las migraciones de Flyway |
| `RABBITMQ_HOST` | El host de Amazon MQ **sin `amqps://` y sin puerto**. Y el puerto es el **5671**, con `SPRING_RABBITMQ_SSL_ENABLED=true` |
| `JWT_SECRETO` | Una clave nueva de 32+ caracteres, **distinta** de la local: si se reutiliza, un token emitido en la máquina de cualquiera vale en producción |

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
3. **La cola**: ya está creada en **Amazon MQ** (`renaser-mq`, RabbitMQ 4.2). El usuario y
   la contraseña se pusieron al crear el broker y **no se pueden recuperar desde AWS**: los
   guarda el propio RabbitMQ.
4. **AWS**: la instancia, el repositorio de imágenes y el rol `github-despliegue` ya existen.
   El paso a paso, con lo que cuesta al mes y por qué se eligió cada cosa, está en
   [`despliegue/README.md`](../despliegue/README.md).
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

- **El disco de la máquina no guarda nada que importe**: los currículums viven en el
  bucket privado de Supabase (ver `docs/ARCHIVOS-EN-BUCKET.md`), así que un despliegue
  no pierde nada. Lo único que muere al recrear el contenedor es lo que nadie debería
  guardar en su disco.
- **Supabase gratuito se pausa a los 7 días sin uso** y despierta en ~30 segundos.
  El smoke test ya lo tolera. En cuanto entre el primer candidato real, Producción
  necesita plan de pago.
- **El correo sigue sin salir** (`EnviadorCorreoLog`): se registra en la base, no se envía.
