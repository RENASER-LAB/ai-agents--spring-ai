# Levantar el backend en AWS

Una máquina, dos contenedores, y nada que administrar en ella.

**Todo lo pesado vive fuera**: la base en Supabase, los currículums en su bucket, la cola en
Amazon MQ, y el chat y los embeddings en APIs de terceros. Esta máquina se puede tirar y
volver a crear sin perder nada más que los certificados, que Caddy saca otra vez solo.

**Lo que ya está creado** (cuenta 526338061654, `us-east-1`):

| | |
|---|---|
| Instancia | `i-05fc037e853d07264` · `t3.medium` · Elastic IP `18.204.177.210` |
| Imagen | `526338061654.dkr.ecr.us-east-1.amazonaws.com/ai-engine:latest` |
| Broker | `renaser-mq` · RabbitMQ 4.2 · `amqps://…:5671` |
| Grupo de seguridad | `sg-0fe61167414449546` — solo 80 y 443 |
| Rol | `renaser-ec2` — ECR de lectura y SSM |

---

## Qué se levanta

| Contenedor | Para qué | Puertos al mundo |
|---|---|---|
| `aplicacion` | El backend | ninguno |
| `caddy` | HTTPS y reparto | 80 y 443 |

Solo Caddy mira a internet; la aplicación no publica ningún puerto. **Y el 22 está cerrado**:
se entra por SSM Session Manager, así que no hay ninguna llave que perder ni rotar.

## Lo que cuesta

| | Al mes |
|---|---:|
| EC2 `t3.medium` (4 GB) | ~$30 |
| Disco EBS 20 GB | ~$2 |
| Amazon MQ `mq.m7g.medium` | ~$57 |
| ECR (la imagen pesa ~450 MB) | ~$0,05 |
| **Total** | **~$89** |

La línea grande es el broker, y fue una decisión tomada a sabiendas. Si algún día se quiere
recortar, ahí está el margen: bajarlo a `mq.t3.micro` son ~$26, y meter RabbitMQ como un
contenedor más en esta misma máquina son $0.

### La cola: Amazon MQ

Renaser ya tenía el broker creado (`renaser-mq`, RabbitMQ 4.2, `mq.m7g.medium`), así que la
aplicación lo usa en vez de levantar uno propio. Cuesta más que el servidor, y fue una
decisión tomada a sabiendas.

**Tres cosas que no son como en local** y rompen el arranque si se copian mal:

- El puerto es **5671**, no 5672: Amazon MQ solo habla AMQPS.
- Por lo mismo, `SPRING_RABBITMQ_SSL_ENABLED` va en **`true`**.
- El `RABBITMQ_HOST` va **sin `amqps://` y sin puerto**. Spring arma la url solo; pegar el
  endpoint entero da un fallo de resolución de nombre que no menciona el esquema.

El usuario y la contraseña se pusieron al crear el broker y **no se pueden recuperar desde la
API de AWS**: los usuarios de un broker RabbitMQ se gestionan dentro del propio RabbitMQ.

### Por qué no un balanceador

Un ALB son **$16 al mes para repartir tráfico entre una sola máquina**. Caddy saca y renueva
el certificado de Let's Encrypt solo, sin cron ni recordatorios, y va en el mismo servidor.

---

## Pasos

### 1. Región

**`us-east-1`**, porque es donde ya está el broker. Supabase está en `us-west-2`, así que cada
consulta a la base cruza el país; si algún día se nota en las pantallas que hacen muchas
consultas, lo que hay que mover es el broker, no el servidor.

### 2. El repositorio de imágenes

Ya creado: `ai-engine`.

### 3. Construir y subir

```bash
aws ecr get-login-password --region us-east-1 --profile renaser   | docker login --username AWS --password-stdin 526338061654.dkr.ecr.us-east-1.amazonaws.com

docker build -t 526338061654.dkr.ecr.us-east-1.amazonaws.com/ai-engine:latest .
docker push 526338061654.dkr.ecr.us-east-1.amazonaws.com/ai-engine:latest
```

La máquina es Intel (`t3.medium`), así que se construye para la arquitectura de siempre. Si
algún día se pasa a Graviton para ahorrar, hay que añadir `--platform linux/arm64`: el
`Dockerfile` usa `eclipse-temurin`, que es multiarquitectura y no hay que tocarlo.

### 4. La máquina

- **AMI**: Amazon Linux 2023, x86_64
- **Tipo**: `t3.medium`
- **Disco**: 20 GB gp3
- **User data**: el contenido de [`user-data.sh`](user-data.sh)
- **Rol IAM**: uno con `AmazonEC2ContainerRegistryReadOnly`, para bajar la imagen sin claves
- **Grupo de seguridad**: abrir **solo 80 y 443**. El 22 solo desde tu IP, o mejor nada y
  entrar por SSM Session Manager

### 5. Las credenciales

Los archivos **ya están** en `/opt/renaser`. Falta el `.env`. Como el 22 está cerrado, se entra
sin llave:

```bash
aws ssm start-session --target i-05fc037e853d07264 --region us-east-1 --profile renaser
sudo -u ec2-user -i
cd /opt/renaser && cp .env.example .env && nano .env
```

Casi todos los valores están ya en tu `application-secrets.yaml` local; el propio
`.env.example` dice de qué clave sale cada uno. **Dos hay que inventarlos:**

```bash
openssl rand -base64 48   # JWT_SECRETO
```

Y el usuario y la contraseña del broker son los que pusiste al crearlo: **no se pueden sacar de
AWS**, los guarda el propio RabbitMQ.

⚠️ **El `JWT_SECRETO` tiene que ser distinto del local.** Si se reutiliza, un token emitido
en la máquina de cualquier desarrollador vale en producción.

⚠️ **La cadena de Supabase es la DIRECTA, puerto 5432.** La del pooler de transacciones
(6543) no soporta las consultas preparadas que Flyway necesita, y la aplicación muere al
arrancar con un error que no explica por qué.

### 6. Arrancar

```bash
chmod +x desplegar.sh
docker compose --env-file .env up -d
docker compose logs -f aplicacion
```

Busca `Started AiEngineApplication`. La primera vez tarda más: Flyway comprueba las
migraciones contra Supabase.

### 7. El dominio

Apunta un registro A a la IP de la máquina, ponlo en `DOMINIO` del `.env` y reinicia Caddy.
El certificado sale solo en unos segundos.

Mientras no haya dominio, deja `DOMINIO` vacío: sirve por HTTP contra la IP, que vale para
comprobar que todo está en pie.

---

## Publicar una versión nueva

Desde tu máquina, construyes y subes la imagen (paso 3). Y en el servidor:

```bash
cd /opt/renaser && ./desplegar.sh
```

Baja la imagen, reinicia y **comprueba que contesta**. Si no contesta en 150 segundos, para y
enseña los registros en vez de dejar el servicio caído sin que nadie se entere.

---

## El dominio y el HTTPS

Va por **`https://18-204-177-210.nip.io`**, con certificado de Let's Encrypt válido y gratis.

`nip.io` resuelve cualquier `IP.nip.io` a esa IP, así que Let's Encrypt puede verificar el
dominio sin que haya que registrar ni pagar nada. El certificado lo saca y lo renueva Caddy
solo, y HTTP redirige a HTTPS con un 308.

La IP es **fija** (Elastic IP `18.204.177.210`), así que no cambia si la instancia se reinicia.

⚠️ **Es provisional a propósito.** `nip.io` es un servicio de terceros: si se cae, el dominio
se cae con él. Cuando Renaser tenga uno propio, se apunta un registro A a esa misma IP, se
cambia `DOMINIO` en el `.env` y se reinicia Caddy. Nada más.

## Lo que hay que saber antes de tocar esto

**Supabase corta por número de conexiones, no por carga.** Su pooler admite 30 en total y
`application-pruebas.yaml` pide 5. Si algún día corren dos instancias de la aplicación, son
10, y hay que mirar cuántas quedan para las demás personas del equipo.

**Las migraciones corren al arrancar.** Un despliegue con una migración nueva la aplica sobre
la base de verdad. Si falla, la aplicación no arranca — que es lo correcto, pero conviene
saber por qué se cayó.

**Te van a escanear desde el primer minuto.** A los pocos minutos de levantarlo ya había bots
probando `/trace.axd`, `/.vscode/sftp.json` y rutas de WordPress. Es normal en cualquier IP
pública. Lo que no es normal es que cada intento devuelva **500 en vez de 404** — está anotado
como pendiente en el `CLAUDE.MD`, y mientras siga así el registro se llena de ruido en el que
un 500 de verdad pasa desapercibido.

**El disco de la máquina es prescindible.** No hay ningún currículum aquí: viven en el bucket
de Supabase. Lo único con volumen propio son los certificados de Caddy, y se vuelven a sacar
solos.
