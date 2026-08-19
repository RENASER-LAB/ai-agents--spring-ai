#!/usr/bin/env python3
"""
Congela lo que hay en la base en un archivo que el frontend puede leer sin backend.

Sirve para enseñar el resultado sin levantar nada: se compila el React con ese archivo
dentro y queda un sitio estático que se puede subir a cualquier sitio.

**Lo que sale es una foto, no el sistema.** No se puede pedir una calificación nueva ni
cambiar nada; solo mirar lo que ya estaba calificado el día que se exportó.

**Datos personales.** Por defecto NO salen el correo ni el teléfono de los candidatos: un
sitio estático publicado es público, y para ver el ranking no hacen falta. Con --con-contacto
se incluyen, y entonces conviene poner contraseña al despliegue.

Uso:
    python scripts/exportar-para-demo.py --api http://localhost:8081/api/v1 --uid tester-001
"""

import argparse
import json
import re
import sys
import unicodedata
from pathlib import Path

import requests

SALIDA = Path("frontend/src/datos-demo.json")


def aplanar(nombre):
    """El nombre sin nada que dependa de cómo se escribió: solo letras y números.

    Se le quita además el «CV» del principio, que es lo que más se pega al nombre en las
    carpetas de verdad. Sirve para reconocer el mismo archivo cuando alguien lo renombró
    por el camino.
    """
    if not nombre:
        return ""
    # Las tildes fuera antes de nada: «Málaga» y «Malaga» son el mismo apellido, y si se
    # borran como si fueran un signo cualquiera dejan «mlaga» y ya no se parecen a nada.
    sin_tildes = "".join(c for c in unicodedata.normalize("NFD", nombre)
                         if unicodedata.category(c) != "Mn")
    plano = re.sub(r"[^a-z0-9]", "", sin_tildes.lower())
    return re.sub(r"^(cv|curriculum)", "", plano)


def main():
    sys.stdout.reconfigure(encoding="utf-8", errors="replace")
    p = argparse.ArgumentParser(description=__doc__,
                                formatter_class=argparse.RawDescriptionHelpFormatter)
    p.add_argument("--api", default="http://localhost:8081/api/v1")
    p.add_argument("--uid", default="tester-001")
    p.add_argument("--con-contacto", action="store_true",
                   help="Incluir correo y teléfono de los candidatos. El sitio es público")
    args = p.parse_args()

    sesion = requests.post(f"{args.api}/panel/auth/dev-login",
                           json={"usuarioRenaserOsId": args.uid}, timeout=30)
    sesion.raise_for_status()
    cab = {"Authorization": f"Bearer {sesion.json()['token']}"}

    vacantes = [v for v in requests.get(f"{args.api}/panel/vacantes", headers=cab,
                                        timeout=60).json()
                if v["estado"] == "PUBLICADA"]
    print(f"{len(vacantes)} vacantes publicadas")

    # Dónde vive la carpeta de currículums de cada una. No está en la base porque no es del
    # sistema: es un sitio de la empresa, y quien carga la tanda es quien sabe cuál es.
    carpetas = {}
    enlaces_por_titulo = {}
    archivo = Path("scripts/convocatorias.json")
    lista = Path("scripts/enlaces-cv.json")
    enlaces = json.loads(lista.read_text(encoding="utf-8")) if lista.exists() else {}
    if archivo.exists():
        with archivo.open(encoding="utf-8") as f:
            for clave, c in json.load(f).items():
                if clave.startswith("_"):
                    continue
                titulo = c["vacante"]["titulo"]
                if c.get("carpetaDrive"):
                    carpetas[titulo] = c["carpetaDrive"]
                # Los enlaces de esta convocatoria y no los de todas: dos carpetas pueden
                # tener un archivo con el mismo nombre.
                if clave in enlaces:
                    enlaces_por_titulo[titulo] = enlaces[clave]

    salida = {"vacantes": [], "rankings": {}, "perfiles": {}, "embudos": {}}
    tapados = 0

    for v in vacantes:
        vid = v["id"]
        r = requests.get(f"{args.api}/panel/vacantes/{vid}/ranking", headers=cab,
                         timeout=120).json()

        # El enlace al currículum de cada uno, buscado por el nombre de su archivo.
        suyos = enlaces_por_titulo.get(v["titulo"], {})
        # Y por el nombre normalizado, para los que se renombraron al cargarlos:
        # «CVKenyoMaicolMaqueAyma.pdf» y «Kenyo Maicol Maque Ayma.pdf» son el mismo archivo.
        sueltos = {aplanar(n): u for n, u in suyos.items()}
        sin_enlace = []
        for fila in r["filas"]:
            nombre = fila.get("archivoNombre")
            fila["cvUrl"] = suyos.get(nombre) or sueltos.get(aplanar(nombre))
            if not fila["cvUrl"]:
                sin_enlace.append(nombre)

        if not args.con_contacto:
            for fila in r["filas"]:
                if fila.get("correo"):
                    fila["correo"] = None
                    tapados += 1
                if fila.get("datos"):
                    fila["datos"]["email"] = None
                    fila["datos"]["telefono"] = None

        salida["vacantes"].append({"id": vid, "titulo": v["titulo"], "estado": v["estado"],
                                   "carpetaCv": carpetas.get(v["titulo"])})
        salida["rankings"][str(vid)] = r
        salida["embudos"][str(vid)] = requests.get(
            f"{args.api}/panel/vacantes/{vid}/embudo", headers=cab, timeout=60).json()

        # El retrato de cada candidato, que es lo que se abre al hacer clic. Se traen todos
        # ahora porque después no habrá a quién preguntárselos.
        for fila in r["filas"]:
            pid = fila["postulacionId"]
            salida["perfiles"][str(pid)] = requests.get(
                f"{args.api}/panel/postulaciones/{pid}/perfil-integral",
                headers=cab, timeout=60).json()
        print(f"  {v['titulo']}: {len(r['filas'])} candidatos")
        if sin_enlace:
            print(f"    ⚠ {len(sin_enlace)} sin enlace al currículum. El primero: "
                  f"{sin_enlace[0]}")

    SALIDA.parent.mkdir(parents=True, exist_ok=True)
    with SALIDA.open("w", encoding="utf-8") as f:
        json.dump(salida, f, ensure_ascii=False)

    tam = SALIDA.stat().st_size / 1024
    print(f"\n{SALIDA} · {tam:.0f} KB · {len(salida['perfiles'])} retratos")
    if not args.con_contacto:
        print(f"Se taparon {tapados} correos y sus teléfonos. Con --con-contacto salen.")
    print("\nPara compilar la versión que no necesita backend:")
    print("  cd frontend && npm run build:demo")


if __name__ == "__main__":
    main()
