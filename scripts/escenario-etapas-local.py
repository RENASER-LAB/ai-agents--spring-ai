#!/usr/bin/env python3
"""Reparte candidatos por las cinco etapas, para poder MIRAR el panel.

Existe porque la base local tenía a casi todo el mundo amontonado en la
preselección: las pestañas de Prueba, Simulación, Validación y Decisión se
veían siempre vacías y no había forma de probar el ranking por etapas con
ojos de usuario.

Toma los candidatos de una vacante que estén en PERFIL_POR_CONFIRMAR y:

  - deja a uno donde está                       → se ve en Perfil integral
  - avanza a otro hasta la prueba               → «aquí ahora» en Prueba
  - lleva a otro hasta Decisión, calificando a mano la prueba, la
    simulación y las métricas de validación por el camino → tiene nota
    en TODAS las etapas

Las notas manuales van con explicación, como exige el backend, y quedan
auditadas como ajuste de andy-dev. No usa la IA para nada.

⚠️ Escribe en la base LOCAL. Nunca contra producción.

    python3 scripts/escenario-etapas-local.py [puerto] [vacanteId]
"""

import json
import sys
import urllib.error
import urllib.request

PUERTO = sys.argv[1] if len(sys.argv) > 1 else "8082"
VACANTE = int(sys.argv[2]) if len(sys.argv) > 2 else 3
BASE = f"http://localhost:{PUERTO}/api/v1/panel"

# Fracciones del maximo de cada criterio: cada uno tiene su propio tope
# (uno de descarte vale 40, otro 100) y una nota fija los revienta.
# Variadas a proposito: un panel con todos en lo mismo no enseña nada.
FRACCIONES = [0.78, 0.62, 0.85, 0.70, 0.74, 0.68, 0.80, 0.58, 0.75, 0.88]


def pedir(ruta, cuerpo=None, metodo=None, token=[None]):
    if token[0] is None and ruta != "/auth/dev-login":
        token[0] = pedir("/auth/dev-login",
                         {"usuarioRenaserOsId": "andy-dev"})["token"]
    datos = json.dumps(cuerpo).encode() if cuerpo is not None else None
    peticion = urllib.request.Request(
        BASE + ruta, data=datos, method=metodo or ("POST" if datos else "GET"))
    peticion.add_header("Content-Type", "application/json")
    if token[0]:
        peticion.add_header("Authorization", f"Bearer {token[0]}")
    try:
        with urllib.request.urlopen(peticion) as r:
            contenido = r.read()
            return json.loads(contenido) if contenido else None
    except urllib.error.HTTPError as e:
        detalle = e.read().decode()[:300]
        raise SystemExit(f"HTTP {e.code} en {ruta}\n{detalle}")


def avanzar(postulacion_id, motivo):
    pedir(f"/postulaciones/{postulacion_id}/confirmacion-avance",
          {"motivo": motivo})
    estado = pedir(f"/postulaciones/{postulacion_id}")["estado"]
    print(f"   → {estado}")
    return estado


def calificar(criterios, ruta_de, ruta_cierre, explicacion):
    """Pone una nota por criterio —como fraccion de su maximo— y cierra."""
    for i, c in enumerate(criterios):
        maximo = c.get("puntosMaximos") or 100
        nota = round(maximo * FRACCIONES[i % len(FRACCIONES)], 1)
        pedir(ruta_de(c["criterioId"]),
              {"puntaje": nota,
               "explicacion": f"{explicacion} · {c['nombre'].lower()}"})
    if ruta_cierre:
        resultado = pedir(ruta_cierre, {})
        print(f"   nota de la etapa: {resultado}")


def estado_de(pid):
    return pedir(f"/postulaciones/{pid}")["estado"]


def viajar_hasta_decision(pid):
    """Lleva una postulación hasta la decisión, calificando por el camino.

    Va mirando el estado y actuando, en vez de asumir una secuencia: así el
    guion se puede relanzar si se cortó a medias, que es exactamente lo que
    pasa cuando una nota rebota por el tope de su criterio.
    """
    for _ in range(20):
        estado = estado_de(pid)
        if estado.startswith("DECISION"):
            print(f"   se queda en {estado}: la decisión es de una persona, no del guion")
            return
        if estado == "PRUEBA_CALIFICANDO":
            rubrica = pedir(f"/postulaciones/{pid}/prueba/notas")
            if any(c["puntaje"] is None for c in rubrica):
                calificar(
                    rubrica,
                    lambda c: f"/postulaciones/{pid}/prueba/criterios/{c}/nota",
                    f"/postulaciones/{pid}/prueba/calificacion",
                    "Calificación del escenario local",
                )
            avanzar(pid, "Prueba calificada y revisada")
        elif estado in ("SIMULACION_CALIFICANDO", "SIMULACION_POR_CONFIRMAR"):
            notas_sim = pedir(f"/postulaciones/{pid}/simulacion/notas")
            if any(c["puntaje"] is None for c in notas_sim):
                calificar(
                    notas_sim,
                    lambda c: f"/postulaciones/{pid}/simulacion/criterios/{c}/nota",
                    f"/postulaciones/{pid}/simulacion/calificacion",
                    "Observado en la sesión del escenario local",
                )
            avanzar(pid, "Simulación calificada")
        elif estado == "VALIDACION_POR_HABILITAR":
            pedir(f"/postulaciones/{pid}/validacion/habilitacion",
                  {"modalidad": "SIMULACION_EXTENDIDA", "dias": 15})
            pedir(f"/postulaciones/{pid}/validacion/inicio", {})
            print("   validación habilitada y en marcha: 15 días")
        elif estado in ("VALIDACION_TURNO_CANDIDATO", "VALIDACION_POR_CONFIRMAR"):
            metricas = pedir(f"/postulaciones/{pid}/validacion/metricas")
            if any(m["puntaje"] is None for m in metricas):
                calificar(
                    metricas,
                    lambda c: f"/postulaciones/{pid}/validacion/metricas/{c}",
                    None,
                    "Medido durante el periodo del escenario local",
                )
                pedir(f"/postulaciones/{pid}/validacion/cierre", {})
                print("   métricas completadas y periodo cerrado")
            else:
                avanzar(pid, "Validación cerrada; pasa a la decisión")
        else:
            avanzar(pid, "Avanza el escenario local")
    raise SystemExit("Veinte vueltas y no llegó a la decisión: algo no cuadra")


def main():
    # Sin plantilla de prueba asignada, el avance a la prueba lo rechaza el
    # backend. Asignarla no exige borrador: solo que la version este publicada.
    vacante = pedir(f"/vacantes/{VACANTE}")
    if vacante.get("versionPlantillaPruebaId") is None:
        pedir(f"/vacantes/{VACANTE}/plantilla-prueba",
              {"versionPlantillaPruebaId": 1})
        print(f"Vacante {VACANTE}: asignada la version 1 de la prueba")

    ranking = pedir(f"/vacantes/{VACANTE}/ranking")
    filas = ranking["filas"]
    print(f"Vacante {VACANTE} · «{ranking['vacante']}»")

    def con_estado(*estados):
        return [f for f in filas if f["estado"] in estados]

    # Reanudable: si una corrida anterior ya movió gente, se respeta donde
    # está cada uno y solo se completa lo que falte.
    en_camino = con_estado("PRUEBA_CALIFICANDO", "PRUEBA_POR_CONFIRMAR",
                           "SIMULACION_POR_HABILITAR", "SIMULACION_TURNO_CANDIDATO",
                           "SIMULACION_CALIFICANDO", "SIMULACION_POR_CONFIRMAR",
                           "VALIDACION_POR_HABILITAR", "VALIDACION_TURNO_CANDIDATO",
                           "VALIDACION_POR_CONFIRMAR")
    quietos = con_estado("PERFIL_POR_CONFIRMAR")
    en_prueba = con_estado("PRUEBA_TURNO_CANDIDATO")

    if en_camino:
        viajero = en_camino[0]
    elif len(quietos) >= 1:
        viajero = quietos.pop(0)
        avanzar(viajero["postulacionId"], "Preselección confirmada por el equipo")
    else:
        raise SystemExit("No hay a quién llevar de viaje en esta vacante.")

    if not en_prueba and quietos:
        candidato = quietos.pop(0)
        print(f"· Hasta la prueba: {candidato['candidato']}")
        avanzar(candidato["postulacionId"], "Preselección confirmada por el equipo")
        en_prueba = [candidato]

    if quietos:
        print(f"· Se queda en preselección: {quietos[0]['candidato']}")

    pid = viajero["postulacionId"]
    print(f"· El viaje completo: {viajero['candidato']} (postulación {pid})")
    viajar_hasta_decision(pid)

    print("\nListo. Qué mirar en el panel:")
    print("  · Perfil integral: todos, con la nota de preselección")
    print(f"  · Prueba: {en_prueba[0]['candidato'] if en_prueba else 'nadie'} está aquí ahora; "
          f"{viajero['candidato']} pasó con nota")
    print(f"  · Simulación, Validación y Decisión: {viajero['candidato']}, "
          f"con sus notas y el porqué de cada criterio")
    print("  · El filtro «Solo quienes están aquí ahora» cambia la foto en cada pestaña")


if __name__ == "__main__":
    main()
