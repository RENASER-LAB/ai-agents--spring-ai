#!/bin/bash
# Publica una version nueva. Se corre DENTRO del servidor, en /opt/renaser.
#
#   ./desplegar.sh
#
# No hace magia: baja la imagen nueva, reinicia y comprueba que contesta. Si no contesta,
# lo dice y para, en vez de dejar el servicio caido sin que nadie se entere.
set -euo pipefail

# us-east-1 porque es donde estan el registro de imagenes y el broker. Si esto no coincide
# con la region real, el login «funciona» —ECR da un token para cualquier region— y el
# fallo aparece dos lineas mas abajo como «no basic auth credentials», que no menciona
# la region por ningun lado.
REGION="${REGION:-us-east-1}"
CUENTA="$(aws sts get-caller-identity --query Account --output text)"

# La configuracion se baja de Parameter Store en cada despliegue: es donde se edita ahora,
# desde la consola de AWS. Si el script no esta —una maquina recien creada, por ejemplo— se
# sigue con el .env que ya haya, que es como funcionaba antes y arranca igual.
if [ -f traer-env.sh ]; then
  echo "==> Trayendo la configuracion de Parameter Store"
  bash traer-env.sh
fi

echo "==> Entrando al registro"
aws ecr get-login-password --region "$REGION" \
  | docker login --username AWS --password-stdin "$CUENTA.dkr.ecr.$REGION.amazonaws.com"

echo "==> Bajando la imagen"
docker compose --env-file .env pull aplicacion

# El modulo de Talento solo si esta encendido en el .env (COMPOSE_PROFILES=talento).
# Se pregunta antes de bajar nada porque con `set -e` un pull de un servicio que no
# esta en ningun perfil activo aborta el despliegue entero — y se llevaria por delante
# un motor que funcionaba.
if docker compose --env-file .env config --services | grep -qx talento; then
  echo "==> Bajando la imagen de talento"
  docker compose --env-file .env pull talento
else
  echo "==> Talento apagado (sin COMPOSE_PROFILES=talento en .env): no se toca"
fi

echo "==> Reiniciando"
docker compose --env-file .env up -d

echo "==> Esperando a que conteste"
for i in $(seq 1 30); do
  if curl -fsS -o /dev/null http://localhost:8080/api/v1/portal/vacantes; then
    echo "    arriba"
    docker image prune -f >/dev/null 2>&1 || true
    exit 0
  fi
  sleep 5
done

echo "!! No contesto en 150 segundos. Los ultimos registros:" >&2
docker compose --env-file .env logs --tail 60 aplicacion >&2
exit 1
