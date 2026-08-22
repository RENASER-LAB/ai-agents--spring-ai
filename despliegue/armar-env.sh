#!/usr/bin/env bash
#
# Arma el .env del servidor leyendo tu application-secrets.yaml, y lo sube.
#
# Existe para que las claves no se copien a mano de un archivo a otro: cada vez que alguien
# hace eso, una se pega con un espacio delante, o se pierde el ultimo caracter, y el fallo
# aparece dos pantallas mas tarde como «credenciales invalidas» sin decir de que.
#
# Las claves NO pasan por ningun sitio raro: se leen de tu archivo local y se escriben en el
# servidor por el mismo canal cifrado de SSM. No se imprimen, y el .env temporal se borra.
#
# Uso, desde la raiz del proyecto:
#
#   bash despliegue/armar-env.sh
#
# Antes de correrlo hacen falta: el perfil «renaser» del CLI de AWS, y python con pyyaml
# (pip install pyyaml).
set -euo pipefail

RAIZ="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
SECRETOS="$RAIZ/application-secrets.yaml"
INSTANCIA="${INSTANCIA:-i-05fc037e853d07264}"
REGION="${REGION:-us-east-1}"
PERFIL="${PERFIL:-renaser}"

[ -f "$SECRETOS" ] || { echo "No encuentro $SECRETOS" >&2; exit 1; }

SALIDA="$(mktemp)"
trap 'rm -f "$SALIDA"' EXIT

python - "$SECRETOS" "$SALIDA" <<'PY'
import io, sys, secrets, base64

ruta, destino = sys.argv[1], sys.argv[2]
import yaml
d = yaml.safe_load(io.open(ruta, encoding="utf-8")) or {}

def leer(*camino):
    n = d
    for paso in camino:
        if not isinstance(n, dict) or paso not in n:
            return ""
        n = n[paso]
    return "" if n is None else str(n)

faltan = []
def exigir(valor, nombre):
    if not valor:
        faltan.append(nombre)
    return valor

# El JWT se genera nuevo a proposito: reutilizar el local haria que un token emitido en
# cualquier portatil del equipo valiera en produccion.
jwt = base64.b64encode(secrets.token_bytes(48)).decode()

valores = {
  "IMAGEN": "526338061654.dkr.ecr.us-east-1.amazonaws.com/ai-engine:latest",

  "SPRING_DATASOURCE_URL":      exigir(leer("spring","datasource","url"), "spring.datasource.url"),
  "SPRING_DATASOURCE_USERNAME": exigir(leer("spring","datasource","username"), "spring.datasource.username"),
  "SPRING_DATASOURCE_PASSWORD": exigir(leer("spring","datasource","password"), "spring.datasource.password"),

  # La cola corre como contenedor en el propio compose: el host es el nombre del servicio.
  # Las credenciales se generan aqui, como el JWT: el broker crea ese usuario la primera
  # vez que arranca con el volumen vacio, asi que en una maquina nueva siempre casan.
  "RABBITMQ_HOST": "rabbitmq",
  "RABBITMQ_USERNAME": "renaser",
  "RABBITMQ_PASSWORD": base64.b64encode(secrets.token_bytes(32)).decode(),
  "RABBITMQ_VHOST": "/",

  "DEEPSEEK_API_KEY":      exigir(leer("spring","ai","deepseek","api-key"), "spring.ai.deepseek.api-key"),
  "GOOGLE_GEMINI_API_KEY": exigir(leer("google","gemini","api-key"), "google.gemini.api-key"),

  "JWT_SECRETO": jwt,
  "SUPABASE_SERVICE_ROLE_KEY":   exigir(leer("supabase","service-role-key"), "supabase.service-role-key"),
  "APP_ARCHIVOS_SUPABASE_CLAVE": exigir(leer("app","archivos","supabase","clave"), "app.archivos.supabase.clave"),

  "CORREO_TRANSPORTE":   leer("renaser","correo","transporte") or "log",
  "CORREO_REMITENTE":    leer("renaser","correo","remitente"),
  "SPRING_MAIL_HOST":    leer("spring","mail","host"),
  "SPRING_MAIL_PORT":    leer("spring","mail","port") or "587",
  "SPRING_MAIL_USERNAME":leer("spring","mail","username"),
  "SPRING_MAIL_PASSWORD":leer("spring","mail","password"),

  "PORTAL_URL": "https://renaser-os-postulantes.vercel.app",
  # 2g y no mas: la aplicacion usa ~755 MB reales, y el broker y el sistema tambien viven
  # en los mismos 4 GB de la maquina.
  "MEMORIA_APP": "2g",
  "DOMINIO": "",
}

with io.open(destino, "w", encoding="utf-8", newline="\n") as f:
    f.write("# Generado por despliegue/armar-env.sh desde application-secrets.yaml.\n")
    f.write("# El JWT_SECRETO es nuevo, distinto del local, a proposito.\n\n")
    for k, v in valores.items():
        f.write(f"{k}={v}\n")

# Se informa de lo que falta por su NOMBRE, nunca por su valor.
if faltan:
    print("AVISO · estas claves no estan en tu yaml y quedan vacias:", file=sys.stderr)
    for c in faltan:
        print("   -", c, file=sys.stderr)
print("OK")
PY

echo "==> .env armado. Las credenciales del broker y el JWT se generaron nuevos."
echo
read -r -p "¿Abro el archivo para revisarlo? [s/N] " abrir
if [[ "${abrir:-}" =~ ^[sS]$ ]]; then
  "${EDITOR:-nano}" "$SALIDA"
fi

echo "==> Subiendo al servidor por SSM"
CONTENIDO="$(base64 -w0 "$SALIDA")"
ID=$(aws ssm send-command --region "$REGION" --profile "$PERFIL" \
  --instance-ids "$INSTANCIA" --document-name AWS-RunShellScript \
  --parameters "commands=[\"echo $CONTENIDO | base64 -d > /opt/renaser/.env\",\"chown ec2-user:ec2-user /opt/renaser/.env\",\"chmod 600 /opt/renaser/.env\",\"grep -c = /opt/renaser/.env\"]" \
  --query "Command.CommandId" --output text)

sleep 12
aws ssm get-command-invocation --region "$REGION" --profile "$PERFIL" \
  --command-id "$ID" --instance-id "$INSTANCIA" \
  --query "{estado:Status,variables:StandardOutputContent}" --output text

echo "==> Listo. Para arrancar:"
echo "    aws ssm start-session --target $INSTANCIA --region $REGION --profile $PERFIL"
echo "    sudo -u ec2-user -i && cd /opt/renaser && docker compose --env-file .env up -d"
