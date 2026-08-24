#!/usr/bin/env python3
"""
Pide que la IA califique las pruebas del puesto ya entregadas de una vacante.

POR QUE EXISTE
--------------
Calificar se pide de una en una desde el panel, y eso esta bien con tres candidatos.
Con noventa y nueve son noventa y nueve clics, y el numero cuarenta se da sobre la fila
equivocada. Esto hace lo mismo que haria una persona ahi —el mismo endpoint, el mismo
permiso, la misma auditoria— pero sobre una lista que se puede leer entera antes de tocar
nada, y termina enseñando las notas que salieron.

CUESTA DINERO
-------------
Cada calificacion es una llamada a DeepSeek. Por eso, igual que `invitar.py`, **sin
--de-verdad no se pide nada**: se enseña a quien se le pediria y se para. Mira la lista
antes, que despues no se deshace.

LO QUE HAY QUE SABER
--------------------
- Solo entra quien esta en PRUEBA_CALIFICANDO, que es donde queda una prueba **entregada**.
  A quien todavia la esta haciendo no se le toca: calificar a medias no da una nota baja,
  da una nota que no significa nada.

- **Una prueba puede salir sin ninguna nota y no es un fallo.** El agente solo puntua los
  criterios que la rubrica le marca como suyos, y solo lo que puede leer: una entrega en
  video o un enlace no da texto. Esos se quedan para una persona, y aqui salen como
  «0 de 7». Ver docs/RUBRICA-DE-LA-PRUEBA.md.

- **Volver a lanzarlo no cobra dos veces.** El backend contesta SIN_CAMBIOS si esa prueba
  ya la califico el agente o si hay un trabajo suyo en marcha.

- La nota de la etapa (el numero sobre 100) solo se puede calcular cuando **todos** los
  criterios de la rubrica tienen nota. Si alguno se quedo para una persona, aqui sale
  «pendiente» y el numero aparecera cuando alguien lo complete desde el panel.

USO
---
    python scripts/calificar-pruebas.py --vacante 16                  # solo mira
    python scripts/calificar-pruebas.py --vacante 16 --de-verdad      # lo pide
    python scripts/calificar-pruebas.py --vacante 16 --solo-notas     # ya calificadas
"""
import argparse
import json
import sys
import time
import urllib.error
import urllib.request

BACKEND = "https://18-204-177-210.nip.io"

# Donde queda una prueba entregada, esperando a que alguien pida la calificacion.
ENTREGADA = "PRUEBA_CALIFICANDO"
# Donde la deja el agente al terminar: le toca a una persona confirmar.
CALIFICADA = "PRUEBA_POR_CONFIRMAR"


def llamar(metodo, ruta, token=None, cuerpo=None):
    cuerpo_json = json.dumps(cuerpo).encode() if cuerpo is not None else None
    peticion = urllib.request.Request(BACKEND + ruta, data=cuerpo_json, method=metodo)
    if cuerpo_json is not None:
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


def _salida_utf8():
    """La consola de Windows viene en cp1252 y se cae al imprimir « o cualquier acento."""
    for flujo in (sys.stdout, sys.stderr):
        try:
            flujo.reconfigure(encoding="utf-8", errors="replace")
        except (AttributeError, ValueError):
            pass


def tanda(vacante, token):
    """Los candidatos de la vacante, con su estado. Una sola llamada."""
    codigo, respuesta = llamar("GET", f"/api/v1/panel/vacantes/{vacante}/ranking", token)
    if codigo != 200:
        sys.exit(f"No se pudo leer la vacante {vacante}: {respuesta}")
    return respuesta["filas"]


def notas_de(identificador, token):
    """Las notas de la rubrica: cuantas tienen valor, sobre cuantas hay, y su suma."""
    codigo, filas = llamar(
        "GET", f"/api/v1/panel/postulaciones/{identificador}/prueba/notas", token)
    if codigo != 200 or not isinstance(filas, list):
        return None
    puestas = [f for f in filas if f.get("puntaje") is not None]
    return {
        "total": len(filas),
        "puestas": len(puestas),
        "suma": sum(f["puntaje"] for f in puestas),
        "maximo": sum(f["puntosMaximos"] for f in filas if f.get("puntosMaximos")),
        "detalle": filas,
    }


def nota_de_etapa(identificador, token):
    """El numero sobre 100, si la rubrica esta completa. Si falta alguna, None."""
    codigo, respuesta = llamar(
        "POST", f"/api/v1/panel/postulaciones/{identificador}/prueba/calificacion", token)
    if codigo in (200, 201) and isinstance(respuesta, dict):
        return respuesta.get("nota")
    return None


def esperar(vacante, token, pedidas, minutos):
    """Sondea hasta que ninguna de las que se pidieron siga en calificando."""
    limite = time.time() + minutos * 60
    while time.time() < limite:
        siguen = [f for f in tanda(vacante, token)
                  if f["postulacionId"] in pedidas and f["estado"] == ENTREGADA]
        if not siguen:
            return True
        print(f"  …{len(siguen)} en curso", flush=True)
        time.sleep(15)
    print("  Se agoto la espera: quedan trabajos en curso. Las notas de abajo van a medias.")
    return False


def imprimir(filas, token):
    """La tabla final: quien tiene que nota, y de quien falta algo.

    Solo entra quien ya entrego. A quien todavia la esta haciendo se le veria «0 de 7»,
    que aqui no significa «el agente no pudo» sino «todavia no le toca»: dos cosas
    distintas que en la misma columna se leen igual.
    """
    filas = [f for f in filas if f["estado"] in (ENTREGADA, CALIFICADA)]
    if not filas:
        print("\nNadie ha entregado todavia su prueba.")
        return

    print(f"\n{'Candidato':32}  {'Nota':>6}  {'Rubrica':>9}  Estado")
    print("-" * 78)
    resumen = []
    for fila in filas:
        notas = notas_de(fila["postulacionId"], token)
        if notas is None:
            print(f"{(fila['candidato'] or '')[:32]:32}  {'—':>6}  {'—':>9}  sin prueba")
            continue
        completa = notas["puestas"] == notas["total"] and notas["total"] > 0
        nota = nota_de_etapa(fila["postulacionId"], token) if completa else None
        resumen.append((fila, notas))
        print(f"{(fila['candidato'] or '')[:32]:32}  "
              f"{(f'{float(nota):6.2f}' if nota is not None else '     —')}  "
              f"{notas['puestas']:>4}/{notas['total']:<4}  "
              f"{fila['estadoNombre']}")

    # Solo cuenta a quien el agente YA miro: en los que siguen en cola, cero notas
    # significa «todavia no ha corrido», y decir lo contrario asustaria sin motivo.
    sin_nada = [f for f, n in resumen if f["estado"] == CALIFICADA and n["puestas"] == 0]
    if sin_nada:
        print(f"\n{len(sin_nada)} prueba(s) que el agente no pudo puntuar. No es un fallo: lo "
              f"que no se puede leer no se puntua, y esas las mira una persona:")
        for fila in sin_nada:
            print(f"  · {fila['candidato']}")

    a_medias = [f for f, n in resumen if 0 < n["puestas"] < n["total"]]
    if a_medias:
        print(f"\n{len(a_medias)} con la rubrica a medias: su nota sobre 100 saldra cuando "
              f"alguien complete los criterios que faltan desde el panel.")


def main():
    _salida_utf8()
    opciones = argparse.ArgumentParser(description=__doc__,
                                       formatter_class=argparse.RawDescriptionHelpFormatter)
    opciones.add_argument("--vacante", type=int, required=True)
    opciones.add_argument("--usuario", default="andy-dev",
                          help="el id de RENASER OS con que entra el equipo")
    opciones.add_argument("--cuantos", type=int, default=0,
                          help="calificar solo las N primeras. 0 = todas. Sirve para probar "
                               "con una antes de lanzar la tanda entera")
    opciones.add_argument("--de-verdad", action="store_true",
                          help="pedir la calificacion. Sin esto solo enseña a quien se le pediria")
    opciones.add_argument("--solo-notas", action="store_true",
                          help="no pedir nada: solo enseñar las notas que ya hay")
    opciones.add_argument("--esperar", action="store_true",
                          help="quedarse mirando hasta que el agente termine con todos")
    opciones.add_argument("--minutos", type=int, default=30, help="tope de espera")
    args = opciones.parse_args()

    codigo, sesion = llamar("POST", "/api/v1/panel/auth/dev-login", None,
                            {"usuarioRenaserOsId": args.usuario})
    if codigo != 200:
        sys.exit(f"No se pudo entrar como «{args.usuario}»: {sesion}")
    token = sesion["token"]

    filas = tanda(args.vacante, token)
    por_calificar = [f for f in filas if f["estado"] == ENTREGADA]
    ya_calificadas = [f for f in filas if f["estado"] == CALIFICADA]

    print(f"\nVacante {args.vacante} · {len(filas)} candidatos")
    print(f"  {len(por_calificar)} con la prueba entregada, esperando calificacion")
    print(f"  {len(ya_calificadas)} ya calificadas por el agente")

    if args.solo_notas:
        imprimir(ya_calificadas + por_calificar, token)
        return

    if not por_calificar:
        print("\nNo hay ninguna prueba entregada esperando calificacion.")
        imprimir(ya_calificadas, token)
        return

    if args.cuantos > 0:
        por_calificar = por_calificar[:args.cuantos]
        print(f"  --cuantos {args.cuantos}: solo se calificaran las primeras {len(por_calificar)}")

    if not args.de_verdad:
        print("\nSe le pediria la calificacion a estos (cada uno cuesta una llamada al modelo):")
        for fila in por_calificar:
            print(f"  · {fila['candidato']}  ({fila['correo']})")
        print(f"\nSon {len(por_calificar)}. Para pedirlo de verdad, repite con --de-verdad\n")
        return

    print(f"\nPidiendo la calificacion de {len(por_calificar)}…")
    pedidas, sin_cambios, fallidas = set(), 0, 0
    for fila in por_calificar:
        identificador = fila["postulacionId"]
        codigo, respuesta = llamar(
            "POST", f"/api/v1/panel/postulaciones/{identificador}/prueba/calificacion-ia", token)
        if codigo not in (200, 201):
            fallidas += 1
            print(f"  ✗ {fila['candidato']}: {respuesta}")
        elif isinstance(respuesta, dict) and respuesta.get("estado") == "ENCOLADA":
            pedidas.add(identificador)
            print(f"  ✓ {fila['candidato']}")
        else:
            sin_cambios += 1
            mensaje = respuesta.get("mensaje") if isinstance(respuesta, dict) else respuesta
            print(f"  – {fila['candidato']}: {mensaje}")

    print(f"\n{len(pedidas)} en cola · {sin_cambios} sin cambios · {fallidas} fallidas")
    if pedidas:
        print("Cada una tarda decenas de segundos.")

    if args.esperar and pedidas:
        print("\nEsperando al agente…")
        esperar(args.vacante, token, pedidas, args.minutos)

    imprimir(tanda(args.vacante, token), token)
    print(f"\nPara volver a mirar sin pedir nada:")
    print(f"  python scripts/calificar-pruebas.py --vacante {args.vacante} --solo-notas\n")


if __name__ == "__main__":
    try:
        main()
    except KeyboardInterrupt:
        sys.exit(130)
