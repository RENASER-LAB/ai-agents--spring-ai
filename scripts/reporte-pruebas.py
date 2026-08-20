#!/usr/bin/env python3
"""
Convierte los resultados de las pruebas en una página que se puede leer.

Maven deja los resultados en XML pensados para una máquina: nombres de clase con
paquete, tiempos en milésimas y poco más. Quien no lee Java no saca nada de ahí, y
lo que estas pruebas comprueban —que la evaluación de otro candidato no se ve, que
si la IA falla no se inventa una nota— es justo lo que interesa enseñar.

Este script lee esos XML y escribe un HTML navegable: cada flujo con su nombre en
español y, debajo, la lista de lo que garantiza.

Para que salgan las frases y no los nombres pegados de los métodos hacen falta dos
cosas, ambas ya puestas: @DisplayName en las pruebas, y la configuración
statelessTestsetReporter del pom.xml, que es la que mete esas frases en el XML.

Uso:
    ./mvnw verify
    python3 scripts/reporte-pruebas.py
    # abre target/reporte-pruebas.html

No necesita instalar nada: solo la biblioteca estándar.
"""

import html
import pathlib
import sys
import xml.etree.ElementTree as ET
from datetime import datetime

RAIZ = pathlib.Path(__file__).resolve().parent.parent
SALIDA = RAIZ / "target" / "reporte-pruebas.html"

# De dónde salen los resultados y cómo se llama cada grupo en el reporte
ORIGENES = [
    ("target/failsafe-reports", "De punta a punta",
     "Levantan la aplicación entera contra un Postgres y un RabbitMQ de verdad, "
     "y la recorren por la API como lo haría el portal o el panel."),
    ("target/surefire-reports", "De una pieza suelta",
     "Comprueban una clase por separado, sin base de datos ni red."),
]


def leer_traza():
    """Lee el recorrido de peticiones que dejó TrazaHttp, si se corrió con -Dtraza=si."""
    carpeta = RAIZ / "target" / "traza-pruebas"
    if not carpeta.is_dir():
        return {}

    import json
    por_clase = {}
    for archivo in sorted(carpeta.glob("*.jsonl")):
        llamadas = []
        for linea in archivo.read_text(encoding="utf-8").splitlines():
            if linea.strip():
                try:
                    llamadas.append(json.loads(linea))
                except json.JSONDecodeError:
                    continue
        if llamadas:
            por_clase[archivo.stem] = llamadas
    return por_clase


def leer(carpeta):
    """Saca de los XML de una carpeta la lista de clases con sus pruebas."""
    clases = []
    ruta = RAIZ / carpeta
    if not ruta.is_dir():
        return clases

    for archivo in sorted(ruta.glob("TEST-*.xml")):
        try:
            raiz = ET.parse(archivo).getroot()
        except ET.ParseError:
            continue

        pruebas = []
        for caso in raiz.findall("testcase"):
            if caso.find("skipped") is not None:
                estado = "saltada"
            elif caso.find("failure") is not None or caso.find("error") is not None:
                estado = "fallo"
            else:
                estado = "bien"
            pruebas.append({
                "nombre": caso.get("name", "?"),
                "estado": estado,
                "segundos": float(caso.get("time") or 0),
            })

        if pruebas:
            clases.append({
                "nombre": raiz.get("name", archivo.stem),
                "archivo": archivo.stem.replace("TEST-", ""),
                "segundos": float(raiz.get("time") or 0),
                "pruebas": pruebas,
            })
    return clases


ICONO = {"bien": "✓", "fallo": "✗", "saltada": "○"}

ESTILO = """
:root {
  --fondo: #ffffff; --texto: #1a1d21; --tenue: #6b7280; --linea: #e5e7eb;
  --tarjeta: #f9fafb; --bien: #15803d; --fallo: #b91c1c; --saltada: #a16207;
}
@media (prefers-color-scheme: dark) {
  :root {
    --fondo: #16181d; --texto: #e6e8ea; --tenue: #9aa1ab; --linea: #2b2f36;
    --tarjeta: #1d2026; --bien: #4ade80; --fallo: #f87171; --saltada: #fbbf24;
  }
}
* { box-sizing: border-box; }
body {
  margin: 0; padding: 2.5rem 1.5rem; background: var(--fondo); color: var(--texto);
  font-family: system-ui, -apple-system, "Segoe UI", sans-serif; line-height: 1.55;
}
main { max-width: 60rem; margin: 0 auto; }
h1 { font-size: 1.7rem; margin: 0 0 .3rem; }
.subtitulo { color: var(--tenue); margin: 0 0 2rem; }
.cifras { display: flex; flex-wrap: wrap; gap: .75rem; margin-bottom: 2.5rem; }
.cifra {
  flex: 1 1 8rem; background: var(--tarjeta); border: 1px solid var(--linea);
  border-radius: .6rem; padding: .9rem 1.1rem;
}
.cifra b { display: block; font-size: 1.6rem; line-height: 1.2; }
.cifra span { color: var(--tenue); font-size: .85rem; }
h2 { font-size: 1.15rem; margin: 2.5rem 0 .2rem; }
.explicacion { color: var(--tenue); margin: 0 0 1.2rem; font-size: .92rem; }
details {
  background: var(--tarjeta); border: 1px solid var(--linea);
  border-radius: .6rem; margin-bottom: .6rem; overflow: hidden;
}
summary {
  cursor: pointer; padding: .8rem 1rem; font-weight: 600;
  display: flex; align-items: center; gap: .6rem;
}
summary::-webkit-details-marker { display: none; }
summary .cuenta { margin-left: auto; color: var(--tenue); font-weight: 400; font-size: .85rem; }
ul { list-style: none; margin: 0; padding: 0 1rem 1rem 1rem; }
li { display: flex; align-items: baseline; gap: .6rem; padding: .35rem 0; border-top: 1px solid var(--linea); }
li .tiempo { margin-left: auto; color: var(--tenue); font-size: .8rem; white-space: nowrap; }
.bien { color: var(--bien); } .fallo { color: var(--fallo); } .saltada { color: var(--saltada); }
.marca { font-weight: 700; width: 1rem; flex: none; }
footer { margin-top: 3rem; color: var(--tenue); font-size: .85rem; border-top: 1px solid var(--linea); padding-top: 1rem; }
code { background: var(--tarjeta); padding: .1rem .35rem; border-radius: .25rem; font-size: .85em; }

/* El recorrido detallado de peticiones */
.aviso {
  background: var(--tarjeta); border: 1px solid var(--linea); border-left: 3px solid var(--saltada);
  border-radius: .4rem; padding: .8rem 1rem; margin-bottom: 1.2rem; font-size: .9rem; color: var(--tenue);
}
.paso { border-top: 1px solid var(--linea); padding: .7rem 1rem; }
.paso:first-child { border-top: none; }
.cabecera { display: flex; align-items: baseline; gap: .6rem; flex-wrap: wrap; font-size: .9rem; }
.verbo {
  font-family: ui-monospace, monospace; font-weight: 700; font-size: .78rem;
  padding: .1rem .45rem; border-radius: .25rem; background: var(--fondo); border: 1px solid var(--linea);
}
.ruta { font-family: ui-monospace, monospace; word-break: break-all; }
.codigo { margin-left: auto; font-weight: 700; font-family: ui-monospace, monospace; }
.c2 { color: var(--bien); } .c4 { color: var(--saltada); } .c5 { color: var(--fallo); }
.cuerpos { display: grid; gap: .5rem; margin-top: .5rem; }
@media (min-width: 46rem) { .cuerpos.dos { grid-template-columns: 1fr 1fr; } }
.cuerpo { background: var(--fondo); border: 1px solid var(--linea); border-radius: .4rem; padding: .5rem .7rem; }
.cuerpo h4 { margin: 0 0 .3rem; font-size: .72rem; text-transform: uppercase; letter-spacing: .04em; color: var(--tenue); }
.cuerpo pre {
  margin: 0; font-size: .78rem; line-height: 1.45; white-space: pre-wrap;
  word-break: break-word; max-height: 15rem; overflow: auto;
}
.candado { font-size: .75rem; color: var(--tenue); }
"""


def escribir(grupos, traza, nombres):
    e = html.escape
    total = bien = fallo = saltada = 0
    segundos = 0.0
    for _, _, clases in grupos:
        for c in clases:
            segundos += c["segundos"]
            for p in c["pruebas"]:
                total += 1
                bien += p["estado"] == "bien"
                fallo += p["estado"] == "fallo"
                saltada += p["estado"] == "saltada"

    partes = [
        "<!doctype html><html lang='es'><head><meta charset='utf-8'>",
        "<meta name='viewport' content='width=device-width, initial-scale=1'>",
        "<title>Lo que el sistema garantiza</title>",
        f"<style>{ESTILO}</style></head><body><main>",
        "<h1>Lo que el sistema garantiza</h1>",
        "<p class='subtitulo'>Cada línea es una comprobación que se repite en cada cambio del código. "
        f"Generado el {datetime.now().strftime('%d/%m/%Y a las %H:%M')}.</p>",
        "<div class='cifras'>",
        f"<div class='cifra'><b>{total}</b><span>comprobaciones</span></div>",
        f"<div class='cifra'><b class='bien'>{bien}</b><span>en verde</span></div>",
    ]
    if fallo:
        partes.append(f"<div class='cifra'><b class='fallo'>{fallo}</b><span>fallando</span></div>")
    if saltada:
        partes.append(f"<div class='cifra'><b class='saltada'>{saltada}</b><span>saltadas</span></div>")
    partes.append(f"<div class='cifra'><b>{segundos / 60:.1f} min</b><span>en correr</span></div>")
    partes.append("</div>")

    for titulo, explicacion, clases in grupos:
        if not clases:
            continue
        partes.append(f"<h2>{e(titulo)}</h2><p class='explicacion'>{e(explicacion)}</p>")
        for c in clases:
            hay_fallo = any(p["estado"] == "fallo" for p in c["pruebas"])
            abierto = " open" if hay_fallo else ""
            marca = "fallo" if hay_fallo else "bien"
            partes.append(
                f"<details{abierto}><summary><span class='marca {marca}'>"
                f"{ICONO['fallo'] if hay_fallo else ICONO['bien']}</span>{e(c['nombre'])}"
                f"<span class='cuenta'>{len(c['pruebas'])} · {c['segundos']:.0f}s</span></summary><ul>")
            for p in c["pruebas"]:
                partes.append(
                    f"<li><span class='marca {p['estado']}'>{ICONO[p['estado']]}</span>"
                    f"<span>{e(p['nombre'])}</span>"
                    f"<span class='tiempo'>{p['segundos']:.1f}s</span></li>")
            partes.append("</ul></details>")

    # El recorrido paso a paso, si se pidió con -Dtraza=si
    if traza:
        partes.append(
            "<h2>El recorrido, paso a paso</h2>"
            "<p class='explicacion'>Cada petición que la prueba le hizo al sistema y lo que "
            "el sistema contestó, en orden. Es para mirarlo a mano y comprobar que hace lo "
            "que se cree que hace.</p>"
            "<div class='aviso'>Los tokens salen recortados a propósito, y los archivos "
            "subidos —un currículum en PDF— se anotan pero no se vuelcan. Los cuerpos muy "
            "largos se cortan a 4 KB.</div>")

        for clase, llamadas in sorted(traza.items()):
            # Se agrupa por la prueba que provocó cada llamada, conservando el orden
            por_prueba = {}
            for ll in llamadas:
                por_prueba.setdefault(ll.get("prueba", "?"), []).append(ll)

            partes.append(f"<h3 style='margin:2rem 0 .8rem'>{e(clase)}</h3>")
            for prueba, pasos in por_prueba.items():
                partes.append(
                    f"<details><summary><span class='marca bien'>›</span>{e(nombres.get(prueba, prueba))}"
                    f"<span class='cuenta'>{len(pasos)} llamadas</span></summary><div>")
                for p in pasos:
                    estado = p.get("estado", 0)
                    clase_codigo = "c5" if estado >= 500 else "c4" if estado >= 400 else "c2"
                    partes.append(
                        f"<div class='paso'><div class='cabecera'>"
                        f"<span class='verbo'>{e(p.get('metodo', '?'))}</span>"
                        f"<span class='ruta'>{e(p.get('uri', ''))}</span>"
                        + ("<span class='candado'>🔒 con token</span>" if p.get("autorizada") else "")
                        + f"<span class='codigo {clase_codigo}'>{estado}</span></div>")

                    pide, vuelve = p.get("peticion", ""), p.get("respuesta", "")
                    if pide or vuelve:
                        dos = " dos" if pide and vuelve else ""
                        partes.append(f"<div class='cuerpos{dos}'>")
                        if pide:
                            partes.append(
                                f"<div class='cuerpo'><h4>Lo que se manda</h4><pre>{e(pide)}</pre></div>")
                        if vuelve:
                            partes.append(
                                f"<div class='cuerpo'><h4>Lo que contesta</h4><pre>{e(vuelve)}</pre></div>")
                        partes.append("</div>")
                    partes.append("</div>")
                partes.append("</div></details>")

    partes.append(
        "<footer>Sale de los XML que deja Maven en <code>target/*-reports</code>. "
        "Para regenerarlo: <code>./mvnw verify</code> y luego "
        "<code>python3 scripts/reporte-pruebas.py</code>.<br>"
        "Las saltadas son las que solo corren cuando se piden, como la que llama al "
        "proveedor de IA de verdad y gasta saldo.</footer></main></body></html>")

    SALIDA.parent.mkdir(parents=True, exist_ok=True)
    SALIDA.write_text("".join(partes), encoding="utf-8")
    return total, fallo


def main():
    grupos = [(titulo, explicacion, leer(carpeta)) for carpeta, titulo, explicacion in ORIGENES]
    if not any(clases for _, _, clases in grupos):
        print("No hay resultados que leer. Corre primero:  ./mvnw verify", file=sys.stderr)
        return 1

    traza = leer_traza()
    # Para enseñar «El equipo prepara y publica una vacante» en vez del nombre del método.
    # El XML ya trae las frases; aquí solo se relacionan por posición dentro de su clase.
    nombres = {}
    for _, _, clases in grupos:
        for c in clases:
            corto = c["archivo"].split(".")[-1]
            if corto in traza:
                vistos = list(dict.fromkeys(ll.get("prueba") for ll in traza[corto]))
                for metodo, prueba in zip(vistos, c["pruebas"]):
                    nombres[metodo] = prueba["nombre"]

    total, fallo = escribir(grupos, traza, nombres)
    extra = f", {len(traza)} flujo(s) con recorrido detallado" if traza else ""
    print(f"{SALIDA}  ·  {total} comprobaciones{extra}" + (f", {fallo} fallando" if fallo else ""))
    return 0


if __name__ == "__main__":
    sys.exit(main())
