#!/usr/bin/env python3
"""
Invita a los mejores de una vacante a la etapa que toque.

POR QUE EXISTE
--------------
Mover a un candidato se hace de uno en uno desde el panel, y eso esta bien cuando son tres.
Con veinte por vacante y dos vacantes son cuarenta clics, y el clic cuarenta se da sobre la
fila equivocada. Ademas cada movimiento manda un correo de verdad: equivocarse no se deshace
pidiendo perdon, porque el candidato ya lo leyo.

Esto hace lo mismo que haria una persona en el panel —el mismo endpoint, el mismo permiso, la
misma auditoria— pero sobre una lista que se puede leer entera antes de tocar nada.

LAS DOS INVITACIONES
--------------------
El orden del embudo lo fija el documento «Que hace el sistema»: primero la evaluacion, y la
prueba del puesto es para «quien sigue». Por eso hay dos destinos y no uno:

  --a examen   Le devuelve su evaluacion (las 50 preguntas del banco v3, segun el nivel del
               puesto) y lo deja en PERFIL_TURNO_CANDIDATO. Es lo primero.

  --a prueba   Lo lleva a la prueba del puesto y le crea el intento. Es DESPUES de que la IA
               califique sus respuestas y una persona confirme.

USO
---
    python scripts/invitar.py --vacante 16 --a examen                  # solo mira
    python scripts/invitar.py --vacante 16 --a examen --de-verdad      # lo hace

TRES AVISOS QUE CUESTAN DINERO O DISGUSTOS
------------------------------------------
- El orden es el del panel: primero el grupo de prioridad, y dentro de el la nota. NO es la
  nota pura. Mira la columna «grupo» antes de extranarte de que el noveno tenga mas nota que
  el octavo.
- A quien no tiene correo no se le invita: el aviso con el enlace al portal es su unica forma
  de enterarse. Salen listados aparte.
- Para «prueba» se usa «confirmacion-avance» y NO «transiciones». Parecen lo mismo y no lo
  son: solo el primero crea el intento al entrar en el turno del candidato. Con el otro el
  estado cambia igual, el correo sale igual, y el candidato abre el enlace y encuentra una
  pantalla vacia.
"""
import argparse
import json
import sys
import urllib.error
import urllib.request

BACKEND = "https://18-204-177-210.nip.io"

MOTIVOS = {
    "examen": "Invitacion a responder la evaluacion",
    "prueba": "Invitacion a la prueba del puesto",
}


def llamar(metodo, ruta, token=None, cuerpo=None):
    cuerpo_json = json.dumps(cuerpo).encode() if cuerpo is not None else None
    peticion = urllib.request.Request(BACKEND + ruta, data=cuerpo_json, method=metodo)
    if cuerpo_json is not None:
        peticion.add_header("Content-Type", "application/json")
    if token:
        peticion.add_header("Authorization", "Bearer " + token)
    try:
        with urllib.request.urlopen(peticion, timeout=60) as respuesta:
            texto = respuesta.read().decode()
            return respuesta.status, (json.loads(texto) if texto.strip() else None)
    except urllib.error.HTTPError as error:
        return error.code, error.read().decode()[:300]


def direccion(fila):
    """
    La direccion a la que se le puede escribir.

    NO es `correo`: esa es la de la cuenta, y en esta convocatoria se la invento el cargador
    («@cv-convocatoria.local», un dominio que no existe). La de verdad la saco el agente del
    propio curriculum y viaja dentro de `datos`.
    """
    return ((fila.get("datos") or {}).get("email") or "").strip()


def estado_de(identificador, token):
    ficha = llamar("GET", f"/api/v1/panel/postulaciones/{identificador}", token)[1]
    return ficha.get("estado") if isinstance(ficha, dict) else None


def al_examen(identificador, token, motivo):
    """
    Le devuelve la evaluacion y lo deja en su turno.

    Va hacia atras en el embudo —la criba los habia adelantado con solo el curriculum— y por
    eso no sirve «confirmacion-avance», que solo avanza. La reapertura ademas le pone plazo
    nuevo, tomado del parametro `dias_plazo_evaluacion`, que se edita desde el panel.
    """
    codigo, respuesta = llamar(
        "POST", f"/api/v1/panel/postulaciones/{identificador}/reapertura-evaluacion",
        token, {"motivo": motivo})
    if codigo not in (200, 201, 204):
        return False, estado_de(identificador, token), respuesta
    donde = respuesta.get("estado") if isinstance(respuesta, dict) else None
    dias = respuesta.get("diasDePlazo") if isinstance(respuesta, dict) else None
    return donde == "PERFIL_TURNO_CANDIDATO", donde, f"{dias} dias de plazo"


def a_la_prueba(identificador, token, motivo):
    """
    Lo lleva hasta el turno del candidato en la prueba.

    Son DOS saltos, porque entre el POR_CONFIRMAR del perfil y el turno esta POR_HABILITAR.
    Nunca da mas de tres pasos: si hicieran falta mas, esa postulacion no estaba donde se
    creia y hay que mirarla a mano.
    """
    ultimo, fallo = None, None
    for _ in range(3):
        codigo, respuesta = llamar(
            "POST", f"/api/v1/panel/postulaciones/{identificador}/confirmacion-avance",
            token, {"motivo": motivo})
        if codigo not in (200, 201, 204):
            fallo = respuesta
            break
        ultimo = estado_de(identificador, token)
        if ultimo == "PRUEBA_TURNO_CANDIDATO":
            break
    return ultimo == "PRUEBA_TURNO_CANDIDATO", ultimo, fallo


def main():
    opciones = argparse.ArgumentParser(description=__doc__,
                                       formatter_class=argparse.RawDescriptionHelpFormatter)
    opciones.add_argument("--vacante", type=int, required=True)
    opciones.add_argument("--a", choices=("examen", "prueba"), required=True,
                          help="a que etapa se les invita")
    opciones.add_argument("--cuantos", type=int, default=20)
    opciones.add_argument("--saltar", default="",
                          help="ids de postulacion que NO se invitan, separados por comas. "
                               "Para dejar fuera una carga duplicada —dos veces el mismo CV "
                               "es la misma persona y recibiria dos enlaces— o un correo mal "
                               "escrito en el curriculum, que solo serviria para que rebote")
    opciones.add_argument("--usuario", default="andy-dev",
                          help="quien queda como responsable en la auditoria")
    opciones.add_argument("--motivo")
    opciones.add_argument("--de-verdad", action="store_true",
                          help="sin esto solo enseña a quien invitaria")
    args = opciones.parse_args()
    motivo = args.motivo or MOTIVOS[args.a]
    mover = al_examen if args.a == "examen" else a_la_prueba

    estado, sesion = llamar("POST", "/api/v1/panel/auth/dev-login",
                            cuerpo={"usuarioRenaserOsId": args.usuario})
    if estado != 200:
        print(f"No se pudo entrar al panel: {estado} {sesion}", file=sys.stderr)
        return 1
    token = sesion["token"]

    estado, ranking = llamar("GET", f"/api/v1/panel/vacantes/{args.vacante}/ranking", token)
    if estado != 200:
        print(f"No se pudo leer el ranking: {estado} {ranking}", file=sys.stderr)
        return 1

    filas = ranking["filas"] if isinstance(ranking, dict) and "filas" in ranking else ranking

    saltar = {int(x) for x in args.saltar.replace(" ", "").split(",") if x}
    saltados = [f for f in filas if f["postulacionId"] in saltar]
    filas = [f for f in filas if f["postulacionId"] not in saltar]
    elegidos = filas[:args.cuantos]
    con_correo = [f for f in elegidos if direccion(f)]
    sin_correo = [f for f in elegidos if not direccion(f)]

    print(f"\nVacante {args.vacante} · {len(elegidos)} primeros · invitacion AL {args.a.upper()}\n")
    print(f"{'#':>3}  {'id':>5}  {'nota':>6}  {'grupo':<22} {'estado':<24} correo")
    for numero, fila in enumerate(elegidos, 1):
        correo = direccion(fila)
        print(f"{numero:>3}  {fila['postulacionId']:>5}  {str(fila.get('notaEtapa')):>6}  "
              f"{str(fila.get('grupoPrioridad')):<22} {str(fila.get('estado')):<24} "
              f"{correo if correo else '· SIN CORREO ·'}")

    if saltados:
        print("\nFUERA a proposito (--saltar):")
        for f in saltados:
            print(f"   {f['postulacionId']:>5}  {f.get('candidato')}  {direccion(f) or '· sin correo ·'}")

    if sin_correo:
        print("\nQuedan fuera por no tener correo: "
              + ", ".join(str(f["postulacionId"]) for f in sin_correo))

    if not args.de_verdad:
        print("\nSimulacro. No se ha tocado nada ni se ha mandado ningun correo.")
        print("Para hacerlo de verdad, repite con --de-verdad")
        return 0

    print(f"\nMoviendo {len(con_correo)} postulaciones. Cada una manda un correo.\n")
    bien, mal = 0, []
    for fila in con_correo:
        identificador = fila["postulacionId"]
        llego, donde, detalle = mover(identificador, token, motivo)
        if llego:
            bien += 1
            print(f"  {identificador}: {donde} · {detalle} · {direccion(fila)}")
        else:
            mal.append(identificador)
            print(f"  {identificador}: FALLO · quedo en {donde} · {detalle}")

    print(f"\nMovidas {bien} de {len(con_correo)}.")
    if mal:
        print("Fallaron: " + ", ".join(str(m) for m in mal))
    return 0


if __name__ == "__main__":
    sys.exit(main())
