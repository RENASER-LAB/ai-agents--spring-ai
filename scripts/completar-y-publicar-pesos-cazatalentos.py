#!/usr/bin/env python3
"""Completa las dos version_pesos CAZATALENTOS y las publica.

La V41 las sembro en BORRADOR solo con los pesos de pilar (peso_dimension): es lo unico
que el instrumento define. Pero la aduana de publicacion exige que los pesos de etapa
sumen 100 y los de componente cuadren con la etapa Perfil Integral, y una vacante solo
acepta pesos PUBLICADOS. Este script copia etapas, componentes y criterios de la version
publicada vigente (la v3) como punto de partida —la calibracion los ajustara— y publica.

Las dimensiones NO se copian: las versiones CAZATALENTOS ya tienen sus pilares sumando
100 por nivel; sumarles las dimensiones del v3 romperia la aduana.

Sin --de-verdad solo cuenta lo que haria. Con --usuario va por dev-login (local);
con --token / RENASER_TOKEN va con un Bearer real (produccion).

    python3 scripts/completar-y-publicar-pesos-cazatalentos.py --usuario UID --api http://localhost:8081
    RENASER_TOKEN=... python3 scripts/completar-y-publicar-pesos-cazatalentos.py --api https://API --de-verdad
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


def exigir(codigo, r, que):
    if not 200 <= codigo < 300:
        sys.exit(f"[!] {que}: HTTP {codigo} {r}")
    return r


def main():
    opciones = argparse.ArgumentParser(description=__doc__)
    opciones.add_argument("--usuario")
    opciones.add_argument("--api", default="http://localhost:8081")
    opciones.add_argument("--token", default=os.environ.get("RENASER_TOKEN"))
    opciones.add_argument("--de-verdad", action="store_true",
                          help="sin esto, solo cuenta lo que haria")
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

    base = "/api/v1/panel/pesos/versiones"
    codigo, versiones = llamar(args.api, "GET", base, token)
    exigir(codigo, versiones, "listar versiones")

    destinos = [v for v in versiones
                if v["estado"] == "BORRADOR" and "CAZATALENTOS" in (v.get("etiqueta") or "")]
    fuentes = [v for v in versiones
               if v["estado"] == "PUBLICADA" and "CAZATALENTOS" not in (v.get("etiqueta") or "")]
    if not destinos:
        print("No hay version_pesos CAZATALENTOS en borrador. Nada que hacer.")
        return 0
    if not fuentes:
        sys.exit("[!] No hay ninguna version publicada de la que copiar etapas y criterios.")
    # La vigente: la publicada mas reciente.
    fuente = sorted(fuentes, key=lambda v: v.get("publicadaEn") or "", reverse=True)[0]
    print(f"Fuente: versión {fuente['id']} «{fuente.get('etiqueta')}»")

    codigo, etapas = llamar(args.api, "GET", f"{base}/{fuente['id']}/etapas", token)
    exigir(codigo, etapas, "leer etapas de la fuente")
    codigo, componentes = llamar(args.api, "GET", f"{base}/{fuente['id']}/componentes", token)
    exigir(codigo, componentes, "leer componentes de la fuente")
    codigo, criterios = llamar(args.api, "GET", f"{base}/{fuente['id']}/criterios", token)
    exigir(codigo, criterios, "leer criterios de la fuente")

    fallo = False
    for destino in destinos:
        d = destino["id"]
        codigo, ya = llamar(args.api, "GET", f"{base}/{d}/etapas", token)
        exigir(codigo, ya, f"leer etapas de la versión {d}")
        if ya:
            print(f"— versión {d} «{destino.get('etiqueta')}»: ya tiene etapas, solo se publica")
        else:
            print(f"— versión {d} «{destino.get('etiqueta')}»: copiar "
                  f"{len(etapas)} etapas, {len(componentes)} componentes, "
                  f"{len(criterios)} criterios")
            if args.de_verdad:
                for e in etapas:
                    exigir(*llamar(args.api, "POST", f"{base}/{d}/etapas", token,
                                   {"etapaCodigo": e["etapaCodigo"], "peso": e["peso"]}),
                           f"copiar etapa {e['etapaCodigo']} a {d}")
                for c in componentes:
                    exigir(*llamar(args.api, "POST", f"{base}/{d}/componentes", token,
                                   {"componente": c["componente"], "peso": c["peso"]}),
                           f"copiar componente {c['componente']} a {d}")
                for c in criterios:
                    exigir(*llamar(args.api, "POST", f"{base}/{d}/criterios", token,
                                   {"nivelPuestoCodigo": c["nivelPuestoCodigo"],
                                    "criterioId": c["criterioId"], "peso": c["peso"]}),
                           f"copiar criterio {c['criterioId']} a {d}")
        if args.de_verdad:
            codigo, r = llamar(args.api, "POST", f"{base}/{d}/publicacion", token)
            if 200 <= codigo < 300:
                print(f"  [ok] versión {d} publicada")
            else:
                print(f"  [!] versión {d} no se pudo publicar: HTTP {codigo} {r}")
                fallo = True

    if not args.de_verdad:
        print("\n(ensayo: nada se escribió; repite con --de-verdad)")
    return 1 if fallo else 0


if __name__ == "__main__":
    raise SystemExit(main())
