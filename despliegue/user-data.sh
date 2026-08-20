#!/bin/bash
# Lo que la instancia hace la primera vez que arranca. Se pega en «User data» al crearla.
#
# Deja Docker listo y la carpeta esperando; NO arranca nada, porque el .env con las
# credenciales todavia no esta. Eso se sube despues, a mano.
set -euxo pipefail

dnf update -y
dnf install -y docker
systemctl enable --now docker
usermod -aG docker ec2-user

# Compose v2 como plugin del propio Docker.
# Docker 25 busca los plugins en /usr/local/lib, no en /usr/libexec. Con la ruta vieja el
# binario se descarga y «docker compose» sigue diciendo que no es un comando.
mkdir -p /usr/local/lib/docker/cli-plugins
curl -sSL "https://github.com/docker/compose/releases/latest/download/docker-compose-linux-aarch64" \
  -o /usr/local/lib/docker/cli-plugins/docker-compose
chmod +x /usr/local/lib/docker/cli-plugins/docker-compose

# Swap. La JVM en una maquina justa de memoria muere con un OOM del sistema, y ese error
# no aparece en el log de la aplicacion: el contenedor simplemente desaparece. Con swap se
# degrada en vez de morir, que se diagnostica mucho mejor.
if [ ! -f /swapfile ]; then
  dd if=/dev/zero of=/swapfile bs=1M count=2048
  chmod 600 /swapfile
  mkswap /swapfile
  swapon /swapfile
  echo '/swapfile none swap sw 0 0' >> /etc/fstab
fi

mkdir -p /opt/renaser
chown ec2-user:ec2-user /opt/renaser

# Que los contenedores vuelvan solos despues de un reinicio de la maquina.
systemctl enable docker
