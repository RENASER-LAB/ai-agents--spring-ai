# Levantar el backend en una cuenta de AWS nueva

> ✅ **Esto ya se ejecutó.** La cuenta `526338061654` quedó suspendida el 04/09/2026 y el
> **08/09/2026** el backend se mudó a `302277511407`, donde corre hoy. El documento se
> conserva porque los pasos sirven igual para la próxima mudanza, y porque lo que costó
> descubrir está anotado al final, en «Lo que enseñó hacerlo de verdad».

Los pasos en orden para dejar el servicio corriendo en una cuenta distinta, pensados para
seguirse con prisa y sin tener que reconstruir el razonamiento.

**Si la cuenta vieja todavía responde, no la des por perdida.** Aunque la consola web esté
bloqueada, las claves de acceso del CLI pueden seguir funcionando: es la única ventana para
sacar Parameter Store y las políticas IAM, y es lo primero que hay que hacer.

## Lo que NO se pierde, pase lo que pase

En AWS **no vive ni un dato de candidatos**. La base y los currículums están en Supabase, y
los dos frontends en Vercel. En la máquina solo hay:

| Qué | Al recrearla |
|---|---|
| Los tres contenedores | Se levantan del `docker-compose.yml` de esta carpeta |
| Los certificados de Caddy | Los vuelve a sacar solo, en segundos |
| El volumen de RabbitMQ | Da igual: los trabajos viven en `trabajo_ia` y `ReintentoTrabajosIa` reencola lo pendiente cada cinco minutos |

Lo único irrecuperable era el `.env`, y **ya está a salvo** (ver abajo).

---

## 0. El rescate, que ya está hecho

El 04/09/2026, con la cuenta todavía respondiendo, se sacó todo lo que no existe en ningún
otro sitio. Está **fuera del repositorio**, en el home, con permisos 600:

| Archivo | Qué contiene |
|---|---|
| `~/renaser-env-rescatado-2026-09-04.env` | Las 25 variables de Parameter Store `/renaser/pruebas`, descifradas. Incluye `JWT_SECRETO` y `APP_ARCHIVOS_SUPABASE_CLAVE`, que no estaban en ningún otro lado |
| `~/renaser-rescate-aws-2026-09-04/confianza-github-despliegue.json` | La política de confianza con el `sub` de GitHub ya escrito |
| `~/renaser-rescate-aws-2026-09-04/politica-github-despliegue-subir-y-desplegar.json` | Los permisos del rol de despliegue |
| `~/renaser-rescate-aws-2026-09-04/politica-renaser-ec2-renaser-parametros.json` | Los permisos de la instancia sobre Parameter Store y KMS |
| `~/renaser-rescate-aws-2026-09-04/instancia.json`, `grupo-seguridad.json`, `oidc.json` | La configuración de lo que había, para replicarla sin adivinar |

⚠️ **Ese `.env` lleva claves en claro.** No entra en git, no se copia a ningún chat y no se
sube a ningún sitio que no sea la máquina nueva por SSM.

**Lo que quedó sin comprobar**: el contenido de `/opt/renaser` en la máquina vieja. Debería
ser solo lo que hay en esta carpeta, pero si la cuenta todavía responde, confirmarlo cuesta
un minuto y luego es imposible:

```bash
aws ssm start-session --target i-05fc037e853d07264 --region us-east-1 --profile renaser
# dentro:  ls -la /opt/renaser && docker ps
```

---

## 1. El registro de imágenes

```bash
aws ecr create-repository --repository-name ai-engine --region us-east-1 --profile <PERFIL-NUEVO>
```

Apunta el id de cuenta nuevo: aparece en cinco sitios más adelante.

## 2. La máquina

| | |
|---|---|
| AMI | Amazon Linux 2023, **x86_64** |
| Tipo | `t3.medium` (4 GB) |
| Disco | 20 GB gp3 |
| User data | El contenido de [`user-data.sh`](user-data.sh), **con la corrección de abajo** |

⚠️ **`user-data.sh` baja el Compose de `aarch64` y la máquina es Intel.** Es un resto de
cuando se evaluó Graviton. Sin corregirlo, `docker compose` falla con un error de formato de
ejecutable que no menciona ni Docker ni la arquitectura. En el user data, esa línea va así:

```
curl -sSL "https://github.com/docker/compose/releases/latest/download/docker-compose-linux-x86_64" \
  -o /usr/local/lib/docker/cli-plugins/docker-compose
```

Si en vez de `t3` se elige Graviton (`t4g`, más barato), entonces sí va `aarch64` — y además
hay que construir la imagen con `--platform linux/arm64`. El `Dockerfile` no se toca en
ninguno de los dos casos: `eclipse-temurin` es multiarquitectura.

## 3. La IP fija

Reservar una Elastic IP y asociarla. **Anótala**: de ella salen el dominio `nip.io`, el smoke
test del CI y la URL que consumen los frontends.

## 4. El grupo de seguridad

Solo dos reglas de entrada, `0.0.0.0/0`: **80 y 443**. El 22 no se abre — se entra por SSM
Session Manager, así que no hay ninguna llave que guardar ni rotar.

## 5. El rol de la instancia

Un rol `renaser-ec2` con su instance profile, y tres cosas:

- La política gestionada `AmazonSSMManagedInstanceCore`
- La política gestionada `AmazonEC2ContainerRegistryReadOnly`
- Una política propia, la de `~/renaser-rescate-aws-2026-09-04/politica-renaser-ec2-renaser-parametros.json`,
  cambiándole el id de cuenta. Da lectura y escritura sobre `/renaser/*` y descifrado de KMS
  **solo a través de SSM**: ese permiso no sirve para descifrar ninguna otra cosa de la cuenta.

## 6. El proveedor OIDC y el rol de despliegue

Sin esto el CI no puede desplegar. Y es donde se pierde la tarde si se hace a ojo.

**El proveedor**: `token.actions.githubusercontent.com`, cliente `sts.amazonaws.com`, huella
`6938fd4d98bab03faadb97b34396831e3780aea1`.

**El rol `github-despliegue`**: la confianza y los permisos salen tal cual de los dos JSON
rescatados, cambiando el id de cuenta y **el id de la instancia nueva** en el `Sid`
`DesplegarSoloEnEsaMaquina`.

⚠️ **El `sub` se copia literal, no se deduce.** Es de GitHub, no de AWS, así que no cambia
al mudarse de cuenta:

```
repo:RENASER-LAB@306311957/ai-agents--spring-ai@1339792194:ref:refs/heads/main
```

Esta organización emite *immutable subject claims*, con los identificadores numéricos pegados
al nombre. Una condición escrita con el formato clásico (`repo:RENASER-LAB/ai-agents--spring-ai:*`)
parece correcta y **no casa nunca**; el síntoma es que el CI muere en «Pedirle permiso a AWS»
con *Not authorized to perform sts:AssumeRoleWithWebIdentity*. El porqué está en
[CI-CD.md](../docs/CI-CD.md).

## 7. Los archivos en la máquina

A `/opt/renaser`, que el user data ya deja creada: `docker-compose.yml`, `Caddyfile`,
`rabbitmq.conf`, `desplegar.sh`, `traer-env.sh` y `guardar-env.sh`.

## 8. El `.env`

**No uses `armar-env.sh`.** Ese script arma el `.env` leyendo `application-secrets.yaml`, y
ahí no están `spring.datasource.url/username/password` (lo que hay es `renaser.supabase.db.*`,
otro camino): saldría con las tres variables de la base vacías. El `.env` rescatado es mejor
que cualquier cosa que ese script pueda generar hoy.

Se parte de `~/renaser-env-rescatado-2026-09-04.env` y se cambian **dos líneas**:

| Variable | A qué |
|---|---|
| `IMAGEN` | `<CUENTA-NUEVA>.dkr.ecr.us-east-1.amazonaws.com/ai-engine:latest` |
| `DOMINIO` | El `nip.io` de la IP nueva (`<IP-con-guiones>.nip.io`), o el dominio propio si ya lo hay |

Se sube por SSM, sin que las claves pasen por ningún otro sitio:

```bash
CONTENIDO="$(base64 -w0 ~/renaser-env-rescatado-2026-09-04.env)"
aws ssm send-command --region us-east-1 --profile <PERFIL-NUEVO> \
  --instance-ids <INSTANCIA-NUEVA> --document-name AWS-RunShellScript \
  --parameters "commands=[\"echo $CONTENIDO | base64 -d > /opt/renaser/.env\",\"chown ec2-user:ec2-user /opt/renaser/.env\",\"chmod 600 /opt/renaser/.env\"]"
```

## 9. Arrancar

```bash
cd /opt/renaser && docker compose --env-file .env up -d
docker compose logs -f aplicacion
```

Se espera `Started AiEngineApplication`. La primera vez tarda más: Flyway comprueba las
migraciones contra Supabase.

## 10. Volver a poblar Parameter Store

Con el `.env` ya bueno en la máquina:

```bash
cd /opt/renaser && bash guardar-env.sh
```

Sin esto, `desplegar.sh` **falla en el siguiente despliegue**: llama a `traer-env.sh`, que se
planta si Parameter Store devuelve menos de 10 variables (a propósito: mejor desplegar con la
configuración de ayer que arrancar sin base de datos).

---

## 11. Lo que hay que cambiar en el repositorio

En la mudanza del 08/09/2026 esto ya se cambió; queda la lista de **dónde** mirar, porque es
lo que se olvida:

| Dónde | Qué |
|---|---|
| [`ci.yml:109`](../.github/workflows/ci.yml) | `REGISTRO` — el id de cuenta |
| [`ci.yml:111`](../.github/workflows/ci.yml) | `INSTANCIA` — `i-05fc037e853d07264` |
| [`ci.yml:112`](../.github/workflows/ci.yml) | `URL` del smoke test — `https://18-204-177-210.nip.io` |
| [`ci.yml:128`](../.github/workflows/ci.yml) | `role-to-assume` — el ARN del rol |
| [`.env.example`](.env.example) | La línea `IMAGEN` |
| [`armar-env.sh`](armar-env.sh) | El `INSTANCIA` por defecto **y** la cadena `IMAGEN` del bloque Python: son dos sitios. Solo si algún día se arreglan sus rutas `leer()` — en esta recuperación el script no se usa (paso 8) |
| [`user-data.sh`](user-data.sh) | La arquitectura del Compose (ver paso 2) |
| [`README.md`](README.md) | La tabla «lo que ya está creado» entera |

## 12. Lo que hay que cambiar fuera de AWS

**Los dos frontends de Vercel**: la URL base de la API apunta al `nip.io` viejo. Se cambia la
variable de entorno y **se redespliegan** — sin esto el servicio está en pie y nadie lo nota,
porque quien entra sigue llamando a una IP que ya no contesta.

El portal del candidato y el panel de empresa viven los dos en el repositorio
`RenaserOsPostulantes`.

💡 **Ya que la dirección cambia igual, este es el momento de estrenar el dominio propio** en
vez de otro `nip.io`. Se apunta un registro A a la IP nueva, se pone en `DOMINIO` y se
reinicia Caddy; el certificado sale solo. Y a partir de ahí, mudarse de máquina deja de
obligar a redesplegar los frontends.

---

## 13. Comprobar que quedó bien

1. **Desde internet**: `curl -I https://<DOMINIO>/api/v1/portal/vacantes` → 200 y certificado válido.
2. **En Supabase**: `select version, type from flyway_schema_history order by installed_rank;`
   La **V1 tiene que aparecer ejecutada (tipo SQL), no como baseline**. Si sale como baseline,
   la tabla `agent_run` no existe y la aplicación no arranca: se borra el esquema y se
   despliega otra vez.
3. **Un despliegue de verdad**: un push a `main` y que el job «desplegar a AWS» acabe en verde.
   Es lo que prueba que el OIDC quedó bien.

## Lo que cuesta

Lo mismo que antes: **~$32 al mes** — la EC2 son ~$30, el disco ~$2 y ECR unos céntimos.
Ninguna pieza gestionada. Si alguien propone Amazon MQ para la cola o un ALB para repartir
tráfico entre una sola máquina, la respuesta y sus números están en el [README](README.md):
eran $119 y $16 al mes por lo que aquí hacen un contenedor y Caddy.


---

## Lo que enseñó hacerlo de verdad (08/09/2026)

Cinco cosas que no estaban en el plan y costaron tiempo.

**1. La cuenta suspendida siguió respondiendo por CLI durante días.** La consola web estaba
bloqueada, pero `aws sts get-caller-identity` contestaba y la instancia seguía `running`
sirviendo tráfico. Eso permitió sacar Parameter Store, las políticas de los roles y las
plantillas de CloudFormation con calma. **No des la cuenta por muerta hasta comprobarlo.**

**2. Poblar Parameter Store va ANTES del primer despliegue.** `desplegar.sh` llama a
`traer-env.sh`, que aborta si la ruta devuelve menos de diez variables. En una cuenta recién
creada está vacía, así que el despliegue del CI habría fallado. El orden bueno es: subir el
`.env` a mano a `/opt/renaser`, correr `guardar-env.sh` dentro de la máquina, y solo entonces
dejar que el CI despliegue.

**3. Subir la imagen desde un portátil puede ser inviable.** Son ~240 MB y con una subida
doméstica lenta el `docker push` no termina —midiendo, iba a 37 kbps— y encima Docker Desktop
cortó con un error 500 a mitad. Lo que funcionó: cambiar `ci.yml` y **dejar que GitHub Actions
construya y suba**, que va de datacenter a datacenter. Es además el paso que hay que dar de
todas formas.

**4. La cuenta nueva puede no estar vacía.** En esta ya vivía `Renaser-90-dias-backend`, con
una instancia, una IP y un repositorio ECR llamado `renaser-backend` — un nombre que parece
el nuestro y no lo es. Antes de crear nada, mira qué hay y comprueba los nombres que vas a
usar. El proveedor OIDC de GitHub, si ya existe, **se reutiliza**: solo puede haber uno.

**5. Dos backends contra la misma base se pisan.** Mientras la máquina vieja y la nueva
convivieron, el sondeo de trabajos atascados de cada una reencolaba los trabajos de la otra:
misma tabla `trabajo_ia`, dos colas. Cada candidato se habría calificado dos veces, pagando
dos veces la inferencia, y con `CORREO_TRANSPORTE=smtp` los avisos habrían salido duplicados
a gente real. Para eso está `IA_HABILITADA` en el compose: la máquina nueva arranca con
`false` y solo se pone a `true` cuando la vieja ya está apagada.
