#!/bin/bash
# Guarda el .env de esta maquina en Parameter Store. Se corre UNA VEZ, dentro del servidor.
#
# POR QUE EXISTE
# --------------
# Las variables vivian solo en /opt/renaser/.env, un archivo dentro del disco de la instancia.
# Eso tiene dos problemas y ninguno se ve hasta que duele:
#
#   - Si la maquina se pierde, se pierden con ella. Dentro hay claves que NO se pueden
#     recuperar de ningun sitio: el JWT_SECRETO se genero al desplegar y no esta en ningun
#     otro lado, y la contraseña del broker solo la conocen el .env y el propio RabbitMQ.
#   - No hay forma de mirarlas ni cambiarlas sin entrar por terminal. La consola de AWS no
#     muestra archivos de dentro de una instancia, asi que no hay ninguna pantalla que abrir.
#
# Parameter Store resuelve las dos: queda fuera de la maquina y tiene pantalla propia.
#
# LAS CLAVES NO SALEN DE AQUI. El script se ejecuta en el servidor, lee el archivo local y
# habla con AWS por su rol. No pasan por el portatil de nadie ni por ningun registro.
#
# USO, dentro de la maquina:
#   sudo -u ec2-user -i
#   cd /opt/renaser && bash guardar-env.sh
set -euo pipefail

RUTA="${RUTA:-/renaser/pruebas}"
REGION="${REGION:-us-east-1}"
ARCHIVO="${ARCHIVO:-/opt/renaser/.env}"

[ -f "$ARCHIVO" ] || { echo "No encuentro $ARCHIVO" >&2; exit 1; }

# Lo que NO es secreto va como texto plano, para que en la consola se lea de un vistazo sin
# tener que pulsar «mostrar». El resto va cifrado. La regla es por el nombre: si suena a
# clave, se cifra. Ante la duda, se cifra.
es_secreto() {
  case "$1" in
    *PASSWORD*|*SECRETO*|*CLAVE*|*KEY*|*TOKEN*|*USERNAME*|*DATASOURCE_URL*) return 0 ;;
    *) return 1 ;;
  esac
}

guardados=0
while IFS= read -r linea; do
  case "$linea" in ''|'#'*) continue ;; esac
  nombre="${linea%%=*}"
  valor="${linea#*=}"
  [ -n "$nombre" ] || continue
  [ -n "$valor" ] || { echo "  $nombre: vacia, no se guarda"; continue; }

  if es_secreto "$nombre"; then tipo="SecureString"; else tipo="String"; fi

  # El valor va por un archivo temporal y no por la linea de comandos: los argumentos de un
  # proceso los puede leer cualquiera con `ps`, y una contraseña no tiene por que asomarse
  # ahi ni un segundo.
  peticion="$(mktemp)"
  python3 - "$RUTA/$nombre" "$valor" "$tipo" > "$peticion" <<'PY'
import json, sys
print(json.dumps({"Name": sys.argv[1], "Value": sys.argv[2],
                  "Type": sys.argv[3], "Overwrite": True}))
PY
  aws ssm put-parameter --region "$REGION" --cli-input-json "file://$peticion" >/dev/null
  rm -f "$peticion"
  echo "  $nombre -> $tipo"
  guardados=$((guardados + 1))
done < "$ARCHIVO"

echo
echo "Guardadas $guardados en $RUTA"
echo "Se ven en: Systems Manager -> Parameter Store, filtrando por «renaser»"
