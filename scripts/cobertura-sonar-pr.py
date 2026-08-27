#!/usr/bin/env python3
"""Cobertura del codigo nuevo de un PR, archivo por archivo, desde SonarCloud.

Solo lee la API publica. Sirve para saber DONDE faltan lineas cuando la puerta
de calidad tumba el PR por new_coverage, sin ir clic a clic por el dashboard.

    python3 scripts/cobertura-sonar-pr.py 45
"""

import json
import sys
import urllib.request

PROYECTO = "RENASER-LAB_ai-agents--spring-ai"


def api(ruta):
    with urllib.request.urlopen("https://sonarcloud.io/api/" + ruta, timeout=60) as r:
        return json.load(r)


def main():
    pr = sys.argv[1] if len(sys.argv) > 1 else "45"
    datos = api(f"measures/component_tree?component={PROYECTO}&pullRequest={pr}"
                "&metricKeys=new_coverage,new_uncovered_lines,new_uncovered_conditions"
                "&qualifiers=FIL&ps=100")
    filas = []
    for comp in datos.get("components", []):
        # La API devuelve el valor del período en "periods" (lista) o en "period", según
        # la versión: se acepta cualquiera de los dos.
        medidas = {}
        for m in comp.get("measures", []):
            periodo = m.get("period") or (m.get("periods") or [{}])[0]
            medidas[m["metric"]] = periodo.get("value")
        if medidas.get("new_coverage") is None:
            continue
        filas.append((float(medidas["new_coverage"]),
                      int(medidas.get("new_uncovered_lines") or 0)
                      + int(medidas.get("new_uncovered_conditions") or 0),
                      comp["path"]))
    filas.sort(key=lambda f: -f[1])
    print(f"{'cobertura':>9}  {'sin cubrir':>10}  archivo")
    for cobertura, sin_cubrir, ruta in filas:
        print(f"{cobertura:8.1f}%  {sin_cubrir:10d}  {ruta}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
