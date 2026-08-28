#!/usr/bin/env python3
"""Sube los tres Excel del banco CAZATALENTOS por el endpoint real de importacion.

POR QUE POR LA API Y NO POR psql
--------------------------------
El lector y sus validaciones viven en Java. Insertar por SQL seria duplicarlos y que las
dos copias se desvien en silencio. Esto ejercita el camino que usara el panel: el mismo
endpoint, el mismo permiso (editar_banco_preguntas), la misma auditoria.

SOLO BASE LOCAL. Los bancos quedan en BORRADOR: nadie los ve hasta publicarlos, y se
pueden tirar y reimportar cuantas veces haga falta. Cada ejecucion crea versiones NUEVAS,
no reemplaza las anteriores.

    python3 scripts/importar-banco-cazatalentos.py --usuario TU_UID
    python3 scripts/importar-banco-cazatalentos.py --usuario TU_UID --api http://localhost:8081

En local necesita el dev-login encendido (apagado por defecto desde multiempresa) y el
backend en el 8081 — el 8080 suele ser Adminer, que responde 200 a todo y esconde el fallo.
Contra produccion no hay dev-login: pasar un token Bearer real con --token o la variable
RENASER_TOKEN (sacado de la sesion del panel), y --api con la URL del backend desplegado.
"""

import argparse
import json
import os
import sys
import urllib.error
import urllib.request
import uuid
from pathlib import Path

RAIZ = Path(__file__).resolve().parent.parent
INSUMOS = RAIZ / "docs" / "insumos"

# Un archivo = un nivel. La etiqueta es lo que el equipo vera en el panel.
BANCOS = [
    ("CAZATALENTOS-DIR.xlsx", "DIRECCION", "Banco CAZATALENTOS · Directivo"),
    ("CAZATALENTOS-SUP.xlsx", "SUPERVISION", "Banco CAZATALENTOS · Coordinación y Supervisión"),
    ("CAZATALENTOS-OPE.xlsx", "EJECUCION", "Banco CAZATALENTOS · Ejecutivo y Operativo"),
]


def llamar(api, metodo, ruta, token=None, cuerpo=None, multipart=None):
    if multipart is not None:
        datos, tipo = multipart
    elif cuerpo is not None:
        datos, tipo = json.dumps(cuerpo).encode(), "application/json"
    else:
        datos, tipo = None, None
    peticion = urllib.request.Request(api + ruta, data=datos, method=metodo)
    if tipo:
        peticion.add_header("Content-Type", tipo)
    if token:
        peticion.add_header("Authorization", "Bearer " + token)
    try:
        with urllib.request.urlopen(peticion, timeout=120) as respuesta:
            texto = respuesta.read().decode()
            return respuesta.status, (json.loads(texto) if texto.strip() else None)
    except urllib.error.HTTPError as error:
        return error.code, error.read().decode()[:2000]
    except urllib.error.URLError as error:
        return 0, str(error.reason)


def formulario(campos, archivo_nombre, archivo_bytes):
    """multipart/form-data a mano: urllib no lo trae y no vale la pena una dependencia."""
    borde = uuid.uuid4().hex
    piezas = []
    for nombre, valor in campos.items():
        piezas.append(
            f'--{borde}\r\nContent-Disposition: form-data; name="{nombre}"\r\n\r\n{valor}\r\n'
            .encode())
    piezas.append(
        f'--{borde}\r\nContent-Disposition: form-data; name="archivo"; '
        f'filename="{archivo_nombre}"\r\n'
        f'Content-Type: application/vnd.openxmlformats-officedocument.spreadsheetml.sheet'
        f'\r\n\r\n'.encode())
    piezas.append(archivo_bytes)
    piezas.append(f"\r\n--{borde}--\r\n".encode())
    return b"".join(piezas), f"multipart/form-data; boundary={borde}"


def main():
    opciones = argparse.ArgumentParser(description=__doc__)
    opciones.add_argument("--usuario",
                          help="usuarioRenaserOsId para el dev-login local")
    opciones.add_argument("--api", default="http://localhost:8081",
                          help="backend local (8081; el 8080 suele ser Adminer)")
    opciones.add_argument("--token", default=os.environ.get("RENASER_TOKEN"),
                          help="token Bearer ya emitido (produccion, donde no hay "
                               "dev-login); tambien via la variable RENASER_TOKEN")
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
            sys.exit(f"No se pudo entrar como «{args.usuario}»: {sesion}")
        token = sesion["token"]
    else:
        sys.exit("Hace falta --usuario (local, dev-login) o --token / RENASER_TOKEN "
                 "(produccion).")

    fallo = False
    for archivo, nivel, etiqueta in BANCOS:
        ruta = INSUMOS / archivo
        if not ruta.exists():
            print(f"[!] {archivo}: no está en {INSUMOS}")
            fallo = True
            continue
        datos, tipo = formulario(
            {"nivelPuestoCodigo": nivel, "etiqueta": etiqueta}, archivo, ruta.read_bytes())
        codigo, resultado = llamar(args.api, "POST",
                                   "/api/v1/panel/banco-preguntas/importaciones",
                                   token, multipart=(datos, tipo))
        if codigo == 201:
            print(f"[ok] {archivo} → versión {resultado['versionBancoId']} "
                  f"({resultado['preguntas']} preguntas, BORRADOR)")
        else:
            print(f"[!] {archivo}: HTTP {codigo}\n    {resultado}")
            fallo = True
    return 1 if fallo else 0


if __name__ == "__main__":
    raise SystemExit(main())
