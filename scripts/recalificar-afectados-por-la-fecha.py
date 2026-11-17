#!/usr/bin/env python3
"""Recalifica, de una sola vez, a quienes la IA califico sin saber la fecha de hoy o sin ver sus
anos de experiencia (spec la-ia-sabe-que-dia-es-hoy).

PARA QUE EXISTE
---------------
Hasta noviembre de 2026 ningun agente recibia la fecha del dia, y el anonimizador tapaba
"5 anos de experiencia" como si fuera una edad. El arreglo vale para lo que se califique desde
el despliegue; esto rehace lo de antes, pero SOLO de quien sigue en curso en una vacante abierta
(ni contratadas, ni descartadas, ni cerradas; las vacantes cerradas quedan como historia).

QUE HACE
--------
- Sin --confirmar (modo de prueba): lista los afectados, de que vacantes, por cual de los dos
  motivos y cuantas llamadas a la IA costaria. No llama a la IA ni cambia nada.
- Con --confirmar: reencola el CV y el retrato de cada uno con el mecanismo de siempre. Nadie
  cambia de etapa por esto, lo ajustado a mano no se pisa y queda en la auditoria quien lo pidio.
  Un fallo con uno no frena a los demas; vuelve a salir en la lista para reintentarlo.

Lo pide la plataforma (permiso administrar_plataforma y ser de la organizacion plataforma).

    # produccion (como los demas scripts): correo y contrasena de una cuenta de la plataforma
    export RENASER_PANEL_CORREO='tu-cuenta@renaser.pe'
    export RENASER_PANEL_CLAVE='...'
    python3 scripts/recalificar-afectados-por-la-fecha.py
    python3 scripts/recalificar-afectados-por-la-fecha.py --confirmar

    # solo algunas vacantes (ids del panel, separados por comas)
    python3 scripts/recalificar-afectados-por-la-fecha.py --vacantes 29,27,26,25,24

    # otra API: export RENASER_BACKEND=http://localhost:8081
    # local con el dev-login: --api http://localhost:8081 --usuario UID_DE_PLATAFORMA
    # o un token Bearer ya hecho: RENASER_TOKEN=...
"""

import argparse
import json
import os
import sys
import urllib.error
import urllib.request

RUTA = "/api/v1/panel/plataforma/recalificacion-por-la-fecha"
MOTIVOS = {(True, True): "fecha y anos", (True, False): "fecha", (False, True): "anos"}


def llamar(api, metodo, ruta, token=None, cuerpo=None):
    datos = json.dumps(cuerpo).encode() if cuerpo is not None else None
    peticion = urllib.request.Request(api + ruta, data=datos, method=metodo)
    if datos is not None:
        peticion.add_header("Content-Type", "application/json")
    if token:
        peticion.add_header("Authorization", "Bearer " + token)
    try:
        with urllib.request.urlopen(peticion, timeout=300) as respuesta:
            texto = respuesta.read().decode()
            return respuesta.status, (json.loads(texto) if texto.strip() else None)
    except urllib.error.HTTPError as error:
        return error.code, error.read().decode()[:500]
    except urllib.error.URLError as error:
        return 0, str(error.reason)


def entrar(args):
    if args.token:
        return args.token
    if not args.usuario:
        correo = os.environ.get("RENASER_PANEL_CORREO")
        clave = os.environ.get("RENASER_PANEL_CLAVE")
        if not (correo and clave):
            sys.exit("El panel pide correo y contrasena. Exportalas antes de correr esto:\n"
                     "    export RENASER_PANEL_CORREO='tu-cuenta@renaser.pe'\n"
                     "    export RENASER_PANEL_CLAVE='...'")
        codigo, sesion = llamar(args.api, "POST", "/api/v1/panel/auth/login", None,
                                {"correo": correo, "contrasena": clave})
        if codigo != 200:
            sys.exit(f"No se pudo entrar como «{correo}»: HTTP {codigo} {sesion}")
        return sesion["token"]
    codigo, sesion = llamar(args.api, "POST", "/api/v1/panel/auth/dev-login", None,
                            {"usuarioRenaserOsId": args.usuario})
    if codigo != 200:
        sys.exit(f"No se pudo entrar como «{args.usuario}»: {sesion}")
    return sesion["token"]


def pintar_lista(lista):
    print(f"Por recalificar: {lista['pendientes']} "
          f"(por la fecha: {lista['porLaFecha']} · por los anos: {lista['porLosAnios']})")
    if lista["fallidas"]:
        print(f"  de ellas, {lista['fallidas']} fallaron la vez anterior y se reintentan")
    if lista["enMarcha"]:
        print(f"Ya en la cola, no se vuelven a encolar: {lista['enMarcha']}")
    print(f"Coste estimado: {lista['llamadasEstimadas']} llamadas a la IA (sin contar reintentos)")
    if lista["vacantes"]:
        print("\nPor vacante:")
        for v in lista["vacantes"]:
            print(f"  - {v['vacante']} (vacante {v['vacanteId']}): {v['afectados']}")
    if lista["afectados"]:
        print("\nAfectados:")
        for a in lista["afectados"]:
            motivo = MOTIVOS.get((a["porLaFecha"], a["porLosAnios"]), "?")
            estado = {"PENDIENTE": "", "EN_CURSO": " · ya en la cola",
                      "DETENIDA": " · fallo antes: " + (a.get("motivoDelFallo") or "")}[a["estado"]]
            print(f"  - postulacion {a['postulacionId']} · {a['candidato']} · {a['vacante']} · "
                  f"{motivo} · {a['llamadas']} llamadas{estado}")


def main():
    opciones = argparse.ArgumentParser(description=__doc__,
                                       formatter_class=argparse.RawDescriptionHelpFormatter)
    opciones.add_argument("--api", default=os.environ.get("RENASER_BACKEND",
                                                          "https://34-196-100-108.nip.io"),
                          help="el backend (por defecto produccion; local: 8081, el 8080 es Adminer)")
    opciones.add_argument("--usuario", help="usuarioRenaserOsId para el dev-login local")
    opciones.add_argument("--token", default=os.environ.get("RENASER_TOKEN"),
                          help="token Bearer de un usuario de la plataforma (produccion)")
    opciones.add_argument("--vacantes",
                          help="solo estas vacantes: ids separados por comas (p. ej. 29,27,26). "
                               "Sin esto, todas las abiertas")
    opciones.add_argument("--confirmar", action="store_true",
                          help="recalificar de verdad. Sin esto solo se lista y se estima el coste")
    args = opciones.parse_args()
    ruta = RUTA
    if args.vacantes:
        ids = [v.strip() for v in args.vacantes.split(",") if v.strip()]
        if not ids or not all(v.isdigit() for v in ids):
            sys.exit(f"--vacantes son ids separados por comas, no «{args.vacantes}»")
        ruta = f"{RUTA}?vacantes={','.join(ids)}"
    token = entrar(args)

    codigo, lista = llamar(args.api, "GET", ruta, token)
    if codigo != 200:
        sys.exit(f"No se pudo leer la lista de afectados: HTTP {codigo} {lista}")
    pintar_lista(lista)

    if not args.confirmar:
        print("\nNada pedido. Con --confirmar se recalifica a los de la lista: cada uno cuesta "
              "las llamadas indicadas y cuenta para el tope mensual de IA de su empresa.")
        return 0
    if lista["pendientes"] == 0:
        print("\nNo hay nadie por recalificar.")
        return 0

    codigo, tanda = llamar(args.api, "POST", ruta, token)
    if codigo != 200:
        sys.exit(f"No se pudo pedir la recalificacion: HTTP {codigo} {tanda}")
    print(f"\nEncoladas: {tanda['encoladas']} · ya en la cola: {tanda['yaEnMarcha']} · "
          f"sin encolar: {len(tanda['fallidas'])} · {tanda['llamadasEstimadas']} llamadas estimadas")
    for f in tanda["fallidas"]:
        print(f"  [!] postulacion {f['postulacionId']} · {f['candidato']}: {f['motivo']}")
    print("\nLa cola hace el resto en los proximos minutos. Vuelve a lanzar este comando sin "
          "--confirmar para ver quien sigue en la cola y quien fallo (sale para reintentarlo).")
    return 1 if tanda["fallidas"] else 0


if __name__ == "__main__":
    raise SystemExit(main())
