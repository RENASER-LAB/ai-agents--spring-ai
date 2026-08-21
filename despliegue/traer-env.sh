#!/bin/bash
# Escribe /opt/renaser/.env con lo que hay en Parameter Store. Lo llama desplegar.sh.
#
# POR QUE EXISTE
# --------------
# Las variables se editan desde la consola de AWS —Systems Manager, Parameter Store— y la
# aplicacion sigue leyendo un archivo. Este script es el puente: cada despliegue baja lo que
# haya y reescribe el .env, asi que lo que se cambia en la pantalla es lo que acaba corriendo.
#
# EL EFECTO SECUNDARIO QUE HAY QUE SABER: cambiar un valor en la consola NO hace nada por si
# solo. Hay que redesplegar. Es a proposito —una aplicacion que cambia de configuracion sin
# avisar es peor que una que exige un paso— pero si alguien edita y no ve el cambio, es esto.
#
# NO PISA NADA A CIEGAS. Si Parameter Store no devuelve nada, se para y deja el .env como
# estaba: mejor desplegar con la configuracion de ayer que arrancar sin base de datos.
#
# USO:
#   bash traer-env.sh            # escribe /opt/renaser/.env
#   DESTINO=/tmp/x traer-env.sh  # para mirarlo sin tocar el bueno
set -euo pipefail

RUTA="${RUTA:-/renaser/pruebas}"
REGION="${REGION:-us-east-1}"
DESTINO="${DESTINO:-/opt/renaser/.env}"

nuevo="$(mktemp)"
trap 'rm -f "$nuevo"' EXIT

{
  echo "# Generado por traer-env.sh desde Parameter Store ($RUTA)."
  echo "# NO editar a mano: el proximo despliegue lo reescribe."
  echo "# Para cambiar un valor: consola de AWS -> Systems Manager -> Parameter Store."
  echo
} > "$nuevo"

# --with-decryption descifra los SecureString. El permiso de KMS del rol solo vale a traves
# de SSM, asi que este token no sirve para descifrar ninguna otra cosa de la cuenta.
aws ssm get-parameters-by-path \
    --region "$REGION" --path "$RUTA" --with-decryption --recursive \
    --query "Parameters[].[Name,Value]" --output text \
  | while IFS=$'\t' read -r nombre valor; do
      printf '%s=%s\n' "${nombre##*/}" "$valor"
    done >> "$nuevo"

cuantas=$(grep -c '=' "$nuevo" || true)
if [ "$cuantas" -lt 10 ]; then
  echo "!! Parameter Store devolvio solo $cuantas variables en $RUTA." >&2
  echo "   Son pocas para arrancar, asi que NO se toca el .env que ya hay." >&2
  echo "   Mira que la ruta sea la correcta y que el rol tenga permiso de lectura." >&2
  exit 1
fi

cp "$nuevo" "$DESTINO"
chmod 600 "$DESTINO"
echo "==> $DESTINO escrito con $cuantas variables de $RUTA"
