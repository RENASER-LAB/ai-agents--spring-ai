# Levantar el backend en AWS

Una máquina, tres contenedores, sin base de datos que administrar.

Esto sale barato por un motivo concreto: **todo lo pesado ya vive fuera**. La base de datos
está en Supabase, los currículums en su bucket, y el chat y los embeddings son APIs de
terceros. Lo único que hay que hospedar es la aplicación.

---

## Qué se levanta

| Contenedor | Para qué | Puertos al mundo |
|---|---|---|
| `aplicacion` | El backend | ninguno |
| `rabbitmq` | La cola de calificación | ninguno |
| `caddy` | HTTPS y reparto | 80 y 443 |

Solo Caddy mira a internet. Ni la aplicación ni la cola publican un puerto: se hablan por la
red interna. **El panel de RabbitMQ tampoco se publica** — ese panel con su contraseña por
defecto es una de las formas más comunes de que entren a un servidor.

## Lo que cuesta

| | Al mes |
|---|---:|
| EC2 `t4g.medium` (4 GB, ARM) | ~$24 |
| Disco EBS 20 GB | ~$2 |
| ECR (la imagen pesa ~400 MB) | ~$0,04 |
| Tráfico de salida | los primeros 100 GB son gratis |
| **Total** | **~$26** |

Con `t4g.small` (2 GB) son ~$14, pero va justo: la JVM pide el 70% y la cola necesita su
parte. Si eliges esa, pon `MEMORIA_APP=1500m` en el `.env`.

### Por qué no Amazon MQ

El broker gestionado más pequeño (`mq.t3.micro`) cuesta **unos $26 al mes: más que el propio
servidor**, y duplicaría la factura. Esta cola es interna —solo la usa la aplicación para
calificar en segundo plano, nadie se conecta desde fuera—, así que no hay nada que lo
justifique.

El día que haga falta —varias instancias de la aplicación, o alta disponibilidad de verdad—
se cambian tres variables del `.env` por las del broker y se borra el servicio `rabbitmq` del
compose. **La aplicación no se entera**: para ella es la misma cola.

### Por qué no un balanceador

Un ALB son **$16 al mes para repartir tráfico entre una sola máquina**. Caddy saca y renueva
el certificado de Let's Encrypt solo, sin cron ni recordatorios, y va en el mismo servidor.

---

## Pasos

### 1. Región

Ponlo en **`us-west-2`**, que es donde está Supabase. En `us-east-1` es unos céntimos más
barato pero cada consulta cruza el país: hay pantallas que hacen veinte consultas y se nota.

### 2. El repositorio de imágenes

```bash
aws ecr create-repository --repository-name ai-engine --region us-west-2
```

### 3. Construir y subir

El `Dockerfile` usa `eclipse-temurin`, que es multiarquitectura, así que ARM funciona sin
tocar nada. Pero hay que **construir para ARM**, no para la arquitectura de tu portátil:

```bash
CUENTA=$(aws sts get-caller-identity --query Account --output text)
aws ecr get-login-password --region us-west-2 \
  | docker login --username AWS --password-stdin $CUENTA.dkr.ecr.us-west-2.amazonaws.com

docker buildx build --platform linux/arm64 \
  -t $CUENTA.dkr.ecr.us-west-2.amazonaws.com/ai-engine:latest --push .
```

### 4. La máquina

- **AMI**: Amazon Linux 2023, **arm64**
- **Tipo**: `t4g.medium`
- **Disco**: 20 GB gp3
- **User data**: el contenido de [`user-data.sh`](user-data.sh)
- **Rol IAM**: uno con `AmazonEC2ContainerRegistryReadOnly`, para bajar la imagen sin claves
- **Grupo de seguridad**: abrir **solo 80 y 443**. El 22 solo desde tu IP, o mejor nada y
  entrar por SSM Session Manager

### 5. Las credenciales

Copia los tres archivos y crea el `.env`:

```bash
scp despliegue/{docker-compose.yml,Caddyfile,desplegar.sh,.env.example} ec2-user@<IP>:/opt/renaser/
ssh ec2-user@<IP>
cd /opt/renaser && cp .env.example .env && nano .env
```

Casi todos los valores están ya en tu `application-secrets.yaml` local; el propio
`.env.example` dice de qué clave sale cada uno. **Dos hay que inventarlos:**

```bash
openssl rand -base64 24   # RABBITMQ_PASSWORD
openssl rand -base64 48   # JWT_SECRETO
```

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

## Lo que hay que saber antes de tocar esto

**Supabase corta por número de conexiones, no por carga.** Su pooler admite 30 en total y
`application-pruebas.yaml` pide 5. Si algún día corren dos instancias de la aplicación, son
10, y hay que mirar cuántas quedan para las demás personas del equipo.

**Las migraciones corren al arrancar.** Un despliegue con una migración nueva la aplica sobre
la base de verdad. Si falla, la aplicación no arranca — que es lo correcto, pero conviene
saber por qué se cayó.

**El disco de la máquina es prescindible.** Lo único con volumen propio es la cola, y solo
para sobrevivir a un reinicio. No hay ningún currículum en esta máquina: viven en el bucket.
