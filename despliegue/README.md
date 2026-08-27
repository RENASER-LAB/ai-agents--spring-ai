# Levantar el backend en AWS

Una máquina, tres contenedores, y nada que administrar en ella.

**Lo pesado vive fuera**: la base en Supabase, los currículums en su bucket, y el chat y los
embeddings en APIs de terceros. La cola sí corre aquí —RabbitMQ como un contenedor más—
pero no guarda nada que importe: los trabajos se apuntan en la tabla `trabajo_ia` antes de
publicarse y un sondeo los reencola si se pierden. Esta máquina se puede tirar y volver a
crear sin perder nada más que los certificados, que Caddy saca otra vez solo.

**Lo que ya está creado** (cuenta 526338061654, `us-east-1`):

| | |
|---|---|
| Instancia | `i-05fc037e853d07264` · `t3.medium` · Elastic IP `18.204.177.210` |
| Imagen | `526338061654.dkr.ecr.us-east-1.amazonaws.com/ai-engine:latest` |
| Grupo de seguridad | `sg-0fe61167414449546` — solo 80 y 443 |
| Rol | `renaser-ec2` — ECR de lectura y SSM |
| Rol que asume GitHub | `github-despliegue` — ECR de escritura y `SendCommand`; su política de confianza solo acepta `main`, y el `sub` va con identificadores numéricos (ver [CI-CD.md](../docs/CI-CD.md)) |

---

## Qué se levanta

| Contenedor | Para qué | Puertos al mundo |
|---|---|---|
| `aplicacion` | El backend | ninguno — el 8080 solo en `127.0.0.1` |
| `rabbitmq` | La cola de la calificación con IA | ninguno — solo la red interna |
| `caddy` | HTTPS y reparto | 80 y 443 |

Solo Caddy mira a internet. La aplicación publica su 8080 **únicamente en loopback**, que es
por donde le pregunta el chequeo de `desplegar.sh`; desde fuera de la máquina ese puerto no
existe, y el grupo de seguridad tampoco lo dejaría pasar. **Y el 22 está cerrado**:
se entra por SSM Session Manager, así que no hay ninguna llave que perder ni rotar.

## Lo que cuesta

| | Al mes |
|---|---:|
| EC2 `t3.medium` (4 GB) | ~$30 |
| Disco EBS 20 GB | ~$2 |
| ECR (la imagen pesa ~450 MB) | ~$0,05 |
| **Total** | **~$32** |

Hasta agosto de 2026 aquí había una línea más: Amazon MQ (`mq.m7g.medium`), **~$119 al mes
medidos** — el 77% de la factura, y el doble de lo que se estimó al contratarlo. Se
sustituyó por el contenedor `rabbitmq` de este mismo compose, que hace lo mismo por $0.

### La cola: un contenedor propio

RabbitMQ 4.2 corre en esta misma máquina, con 768 MB de techo y sin ningún puerto
publicado: solo la aplicación, por la red interna del compose, puede hablarle.

**Por qué no hace falta un broker gestionado.** La durabilidad de los trabajos no descansa
en la cola: cada trabajo se guarda en la tabla `trabajo_ia` antes de publicarse el aviso, y
`ReintentoTrabajosIa` sondea cada cinco minutos lo pendiente o colgado y lo reencola. Si el
broker pierde mensajes —un reinicio, un volumen borrado, la máquina recreada— lo peor que
pasa es que una calificación espere unos veinte minutos. Pagar un gestionado compra
disponibilidad que este flujo no necesita.

**Su configuración vive en [`rabbitmq.conf`](rabbitmq.conf)**, montado por el compose. Lo
importante ahí es el `consumer_timeout` de 60 minutos: una calificación real puede pasar de
la media hora, y con el valor por defecto (30 min) el broker reencolaba el trabajo a medias
y la misma inferencia se pagaba dos y tres veces.

**Las credenciales las eliges tú** (o las genera `armar-env.sh`): el broker crea ese usuario
la primera vez que arranca con el volumen vacío, leyendo las mismas variables del `.env`
que usa la aplicación. Si algún día se cambia la contraseña en Parameter Store, al broker
hay que cambiársela por dentro (`rabbitmqctl change_password`): no le llega sola.

**Si algún día hay que volver a un broker externo**: `RABBITMQ_HOST` con el endpoint (sin
esquema y sin puerto — Spring arma la url solo), el puerto y el TLS en el compose (Amazon
MQ solo habla AMQPS: 5671 y `SPRING_RABBITMQ_SSL_ENABLED=true`), y fuera el servicio
`rabbitmq`.

### Por qué no un balanceador

Un ALB son **$16 al mes para repartir tráfico entre una sola máquina**. Caddy saca y renueva
el certificado de Let's Encrypt solo, sin cron ni recordatorios, y va en el mismo servidor.

---

## Pasos

### 1. Región

**`us-east-1`**, que es donde están la instancia y el registro de imágenes. Supabase está en
`us-west-2`, así que cada consulta a la base cruza el país; si algún día se nota en las
pantallas que hacen muchas consultas, lo que tocaría mover es el servidor hacia la base —
la cola ya viaja con él.

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
`.env.example` dice de qué clave sale cada uno. **Tres hay que inventarlos** —el
`JWT_SECRETO` y las credenciales del broker— y los tres los genera `armar-env.sh` solo. A
mano:

```bash
openssl rand -base64 48   # JWT_SECRETO
openssl rand -base64 32   # RABBITMQ_PASSWORD (el usuario, el que quieras)
```

El broker crea ese usuario en su primer arranque con el volumen vacío, así que en una
máquina nueva las credenciales siempre casan solas.

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
de Supabase. Con volumen propio están los certificados de Caddy —se vuelven a sacar solos— y
la cola del broker, que tampoco duele perder: los trabajos viven en `trabajo_ia` y el sondeo
de la aplicación los reencola.
