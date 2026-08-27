#!/usr/bin/env python3
"""Vuelve a calificar el banco de una vacante entera, postulacion por postulacion.

PARA QUE EXISTE
---------------
Es la herramienta de calibracion del banco CAZATALENTOS. Cuando la clienta ajusta una
señal de 0, un C3 esperado o un peso, las respuestas ya guardadas no cambian: lo que hay
que rehacer son las calificaciones. Esto reencola al EVALUADOR para cada postulacion de la
vacante —el mismo endpoint que usa el panel, el mismo permiso (ajustar_nota), la misma
auditoria— y la nota de etapa se recalcula sola al terminar.

LA REGLA QUE NO SE SALTA
------------------------
Sobre una vacante donde ya se contrato a alguien no se recalifica nada: esa nota sustento
una decision tomada. El script salta los estados finales y lo dice.

CUESTA DINERO
-------------
Cada calificacion es una llamada al modelo y hay tope mensual. Por eso, igual que
calificar-pruebas.py, **sin --de-verdad no se pide nada**: se enseña a quien se le
pediria y se para.

    python3 scripts/recalificar-banco.py --vacante 13 --usuario TU_UID
    python3 scripts/recalificar-banco.py --vacante 13 --usuario TU_UID --de-verdad
"""

import argparse
import json
import sys
import urllib.error
import urllib.request

# Estados donde recalificar es reescribir una decision ya tomada. No se tocan.
FINALES = {"CONTRATADO", "NO_CONTINUA", "CERRADA"}
# Sin evaluacion entregada no hay nada que recalificar; el servidor lo rechaza igual,
# pero saltarlo aqui deja el resumen limpio.
SIN_EVALUACION = {"POSTULADA", "PERFIL_TURNO_CANDIDATO"}


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
        return error.code, error.read().decode()[:300]
    except urllib.error.URLError as error:
        return 0, str(error.reason)


def main():
    opciones = argparse.ArgumentParser(description=__doc__)
    opciones.add_argument("--vacante", type=int, required=True)
    opciones.add_argument("--usuario", required=True,
                          help="usuarioRenaserOsId para el dev-login local")
    opciones.add_argument("--api", default="http://localhost:8081",
                          help="backend local (8081; el 8080 suele ser Adminer)")
    opciones.add_argument("--de-verdad", action="store_true",
                          help="reencolar de verdad. Sin esto solo enseña a quién le tocaría")
    args = opciones.parse_args()

    codigo, sesion = llamar(args.api, "POST", "/api/v1/panel/auth/dev-login", None,
                            {"usuarioRenaserOsId": args.usuario})
    if codigo != 200:
        sys.exit(f"No se pudo entrar como «{args.usuario}»: {sesion}")
    token = sesion["token"]

    codigo, filas = llamar(args.api, "GET",
                           f"/api/v1/panel/vacantes/{args.vacante}/ranking", token)
    if codigo != 200:
        sys.exit(f"No se pudo leer el ranking de la vacante {args.vacante}: {filas}")

    contratada = [f for f in filas if f["estado"] == "CONTRATADO"]
    if contratada:
        sys.exit(f"En la vacante {args.vacante} ya se contrató "
                 f"({len(contratada)} postulación/es en CONTRATADO): sus notas sustentaron "
                 "una decisión tomada y no se recalifican. Calibra en la siguiente vacante.")

    candidatas = [f for f in filas
                  if f["estado"] not in FINALES and f["estado"] not in SIN_EVALUACION]
    saltadas = len(filas) - len(candidatas)

    print(f"Vacante {args.vacante}: {len(candidatas)} por recalificar"
          + (f" · {saltadas} saltadas (finales o sin evaluación)" if saltadas else ""))
    for fila in candidatas:
        print(f"  - postulación {fila['postulacionId']} ({fila['estado']})")

    if not args.de_verdad:
        print("\nNada pedido. Con --de-verdad se reencola al EVALUADOR de cada una: "
              "cada calificación es una llamada al modelo y hay tope mensual.")
        return 0

    fallo = False
    for fila in candidatas:
        codigo, r = llamar(args.api, "POST",
                           f"/api/v1/panel/postulaciones/{fila['postulacionId']}"
                           "/calificacion-perfil-integral", token)
        # Un 200 no basta: el endpoint contesta 200 tambien cuando no encolo nada
        # (SIN_CAMBIOS, hay un trabajo vivo). Encolada de verdad es estado ENCOLADA.
        if codigo == 200 and isinstance(r, dict) and r.get("estado") == "ENCOLADA":
            print(f"  [ok] {fila['postulacionId']} encolada")
        elif codigo == 200:
            print(f"  [~] {fila['postulacionId']} sin encolar: "
                  f"{r.get('mensaje') if isinstance(r, dict) else r}")
            fallo = True
        else:
            print(f"  [!] {fila['postulacionId']}: HTTP {codigo} {r}")
            fallo = True
    print("\nLa cola hace el resto: al terminar el EVALUADOR de cada postulación, la nota "
          "de etapa se recalcula sola. Las notas ajustadas a mano no se pisan.")
    return 1 if fallo else 0


if __name__ == "__main__":
    raise SystemExit(main())
