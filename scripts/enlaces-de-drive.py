#!/usr/bin/env python3
"""
Saca el enlace directo de cada currículum a partir del enlace de su carpeta de Drive.

Es el paso que le faltaba a la pantalla: hasta ahora enseñaba el nombre del archivo y un
botón a la carpeta, y quien revisaba tenía que buscarlo dentro. Con esto cada candidato
lleva el enlace a SU currículum.

**No copia ningún archivo.** Solo lee el listado de la carpeta y anota, por cada nombre, el
enlace que Drive ya le había dado. El control de quién puede abrirlo sigue siendo el de
Drive: si la carpeta deja de estar compartida, los enlaces dejan de abrirse.

Las carpetas salen de scripts/convocatorias.json, del campo 'carpetaDrive'.

Uso:
    python scripts/enlaces-de-drive.py
    python scripts/enlaces-de-drive.py --salida scripts/enlaces-cv.json
"""

import argparse
import json
import re
import sys
from pathlib import Path

import requests

CONVOCATORIAS = Path("scripts/convocatorias.json")
SALIDA = Path("scripts/enlaces-cv.json")

# Se lee la vista incrustada de la carpeta y no la pantalla normal de Drive.
#
# La normal solo trae los primeros cincuenta archivos: el resto lo carga el navegador
# según bajas, y desde aquí no hay navegador que baje. La incrustada devuelve la carpeta
# entera de una vez, que es justo lo que hace falta con tandas de cien.
#
# Cada archivo viene como un bloque con su id delante y su nombre dentro. Se leen los dos
# juntos y no por separado: sueltos no hay forma de saber cuál va con cuál.
PAR = re.compile(
    r'id="entry-([A-Za-z0-9_-]+)".*?<div class="flip-entry-title">([^<]+)</div>',
    re.S)


def id_de_carpeta(url):
    m = re.search(r"/folders/([A-Za-z0-9_-]+)", url or "")
    return m.group(1) if m else None


def archivos_de(carpeta_id):
    """Nombre -> enlace, leyendo el listado público de la carpeta."""
    r = requests.get("https://drive.google.com/embeddedfolderview",
                     params={"id": carpeta_id}, timeout=60,
                     headers={"User-Agent": "Mozilla/5.0"})
    r.raise_for_status()

    encontrados = {}
    for archivo_id, nombre in PAR.findall(r.text):
        nombre = nombre.strip()
        if not nombre or nombre in encontrados:
            continue
        encontrados[nombre] = f"https://drive.google.com/file/d/{archivo_id}/view"
    return encontrados


def main():
    sys.stdout.reconfigure(encoding="utf-8", errors="replace")
    p = argparse.ArgumentParser(description=__doc__,
                                formatter_class=argparse.RawDescriptionHelpFormatter)
    p.add_argument("--salida", default=str(SALIDA))
    args = p.parse_args()

    with CONVOCATORIAS.open(encoding="utf-8") as f:
        convocatorias = {k: v for k, v in json.load(f).items() if not k.startswith("_")}

    todo = {}
    for clave, c in convocatorias.items():
        carpeta = id_de_carpeta(c.get("carpetaDrive"))
        if not carpeta:
            print(f"  {clave}: sin carpeta configurada, se salta")
            continue
        try:
            encontrados = archivos_de(carpeta)
        except requests.RequestException as e:
            # Una carpeta que no se puede leer no debe tumbar las demás: casi siempre es
            # que dejó de estar compartida, y eso se arregla en Drive, no aquí.
            print(f"  {clave}: no se pudo leer la carpeta ({e})")
            continue
        print(f"  {clave}: {len(encontrados)} archivos")
        # Cada convocatoria en su propia caja. Con todas juntas, dos archivos que se
        # llamen igual en carpetas distintas se pisan, y un candidato acaba apuntando al
        # currículum de otro: es el único error de aquí que no se ve al mirar la pantalla.
        todo[clave] = encontrados

    Path(args.salida).write_text(
        json.dumps(todo, ensure_ascii=False, indent=1), encoding="utf-8")
    print(f"\n{sum(len(v) for v in todo.values())} enlaces en {args.salida}")
    print("Ahora vuelve a exportar la foto: python scripts/exportar-para-demo.py")


if __name__ == "__main__":
    main()
