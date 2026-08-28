#!/usr/bin/env python3
"""Publica las versiones CAZATALENTOS mas recientes que esten en BORRADOR.

El mismo endpoint que usa el panel, con su aduana de coherencia delante: una version
incompleta no pasa. En local con --usuario (dev-login); en produccion con --token / RENASER_TOKEN.

    python3 scripts/publicar-bancos-cazatalentos.py --usuario dev-1 --api http://localhost:8082
"""

import argparse
import json
import os
import sys
import urllib.error
import urllib.request


def llamar(api, metodo, ruta, token=None, cuerpo=None):
    datos = json.dumps(cuerpo).encode() if cuerpo is not None else None
    peticion = urllib.request.Request(api + ruta, data=datos, method=metodo)
    if datos is not None:
        peticion.add_header("Content-Type", "application/json")
    if token:
        peticion.add_header("Authorization", "Bearer " + token)
    try:
        with urllib.request.urlopen(peticion, timeout=120) as respuesta:
            texto = respuesta.read().decode()
            return respuesta.status, (json.loads(texto) if texto.strip() else None)
    except urllib.error.HTTPError as error:
        return error.code, error.read().decode()[:500]
    except urllib.error.URLError as error:
        return 0, str(error.reason)


def main():
    opciones = argparse.ArgumentParser(description=__doc__)
    opciones.add_argument("--usuario")
    opciones.add_argument("--api", default="http://localhost:8082")
    opciones.add_argument("--token", default=os.environ.get("RENASER_TOKEN"),
                          help="token Bearer ya emitido (produccion); "
                               "tambien via la variable RENASER_TOKEN")
    opciones.add_argument("--correo", default=os.environ.get("RENASER_PANEL_CORREO"),
                          help="login real con correo y contrasena de cuenta de equipo; "
                               "la clave va en la variable RENASER_PANEL_CLAVE, nunca como argumento")
    args = opciones.parse_args()

    if args.token:
        token = args.token
    elif args.correo:
        clave = os.environ.get("RENASER_PANEL_CLAVE")
        if not clave:
            sys.exit("Falta la contrasena: ponla en la variable RENASER_PANEL_CLAVE "
                     "(read -s RENASER_PANEL_CLAVE && export RENASER_PANEL_CLAVE) para no dejarla "
                     "en el historial de la terminal.")
        codigo, sesion = llamar(args.api, "POST", "/api/v1/panel/auth/login", None,
                                {"correo": args.correo, "contrasena": clave})
        if codigo != 200:
            sys.exit(f"No se pudo entrar como «{args.correo}»: {sesion}")
        token = sesion["token"]
    elif args.usuario:
        codigo, sesion = llamar(args.api, "POST", "/api/v1/panel/auth/dev-login", None,
                                {"usuarioRenaserOsId": args.usuario})
        if codigo != 200:
            sys.exit(f"No se pudo entrar: {sesion}")
        token = sesion["token"]
    else:
        sys.exit("Hace falta --usuario (local) o --token / RENASER_TOKEN (produccion).")

    codigo, versiones = llamar(args.api, "GET", "/api/v1/panel/banco-preguntas/versiones", token)
    if codigo != 200:
        sys.exit(f"No se pudieron listar las versiones: {versiones}")

    borradores = [v for v in versiones
                  if v.get("estado") == "BORRADOR" and "CAZATALENTOS" in (v.get("etiqueta") or "")]
    if not borradores:
        print("No hay ningún banco CAZATALENTOS en borrador.")
        return 0

    fallo = False
    for v in borradores:
        codigo, r = llamar(args.api, "POST",
                           f"/api/v1/panel/banco-preguntas/versiones/{v['id']}/publicacion", token)
        if 200 <= codigo < 300:
            print(f"[ok] versión {v['id']} ({v.get('etiqueta')}) publicada")
        else:
            print(f"[!] versión {v['id']}: HTTP {codigo} {r}")
            fallo = True
    return 1 if fallo else 0


if __name__ == "__main__":
    raise SystemExit(main())
