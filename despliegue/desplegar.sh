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

echo "==> Entrando al registro"
aws ecr get-login-password --region "$REGION" \
  | docker login --username AWS --password-stdin "$CUENTA.dkr.ecr.$REGION.amazonaws.com"

echo "==> Bajando la imagen"
docker compose --env-file .env pull aplicacion

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
