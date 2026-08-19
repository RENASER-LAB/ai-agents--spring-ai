#!/usr/bin/env python3
"""
Convierte el Banco Maestro de Preguntas del cliente (.docx) en SQL.

Por qué un script y no escribir la migración a mano: son 200 preguntas con sus
claves de puntuación, y copiarlas a mano garantiza erratas. Por qué emite SQL en
vez de tocar la base: el SQL se revisa antes de aplicarse, y queda como migración
versionada igual que todo lo demás.

    python3 scripts/importar-banco-maestro.py > /tmp/banco.sql

La salida NO es la migración final: es el cuerpo que se pega en V14, después de
leerlo. El documento es regular pero no perfecto, y este script avisa por stderr
de todo lo que no supo interpretar en vez de inventarse un valor.
"""

import glob
import html
import re
import sys
import zipfile

DOCX = "docs/insumos/Banco_Maestro*.docx"

# Las tablas del documento, por índice. El orden es estable: si alguien reordena
# el .docx hay que revisar esto, y por eso cada carga comprueba los IDs que salen.
BANCOS = {
    "DIRECCION": {
        "etiqueta": "Banco Dirección V0.1",
        "prefijo": "D",
        "tablas": [
            (4, "ESTILO"),
            (5, "SITUACION"),
            (6, "CONDUCTUAL"),
            (7, "MICROCASO"),
            (8, "DILEMA"),
            (9, "CONSISTENCIA"),
        ],
    },
    "SUPERVISION": {
        "etiqueta": "Banco Supervisión V0.1",
        "prefijo": "S",
        "tablas": [
            (10, "ESTILO"),
            (11, "SITUACION"),
            (12, "CONDUCTUAL"),
            (13, "MICROCASO"),
            (14, "DILEMA"),
        ],
    },
    "EJECUCION": {
        "etiqueta": "Banco Ejecución V0.1",
        "prefijo": "O",
        "tablas": [
            (15, "ESTILO"),
            (16, "SITUACION"),
            (17, "CONDUCTUAL"),
            (18, "MICROCASO"),
            (19, "DILEMA"),
            (20, "CONSISTENCIA"),
        ],
    },
}

# Las tablas de pesos por dimensión, y a qué nivel pertenece cada una
PESOS = {21: "DIRECCION", 22: "SUPERVISION", 23: "EJECUCION"}

# El banco maestro agrupa dimensiones con barra ("SUP/PPL: 10"). La base las tiene
# separadas y exige que sumen 100 por nivel, así que el peso se reparte por igual.
# Es una decisión nuestra: el cliente no dijo cómo repartir dentro del par.
avisos = []


def texto(nodo):
    t = re.sub(r"</w:p>", "\n", nodo)
    t = re.sub(r"<[^>]+>", "", t)
    return html.unescape(t).strip().replace("\n", " ")


def leer_tablas(ruta):
    xml = zipfile.ZipFile(ruta).read("word/document.xml").decode("utf8")
    tablas = []
    for tbl in re.findall(r"<w:tbl>.*?</w:tbl>", xml, re.S):
        filas = []
        for tr in re.findall(r"<w:tr[ >].*?</w:tr>", tbl, re.S):
            filas.append([texto(tc) for tc in re.findall(r"<w:tc[ >].*?</w:tc>", tr, re.S)])
        tablas.append(filas)
    return tablas


def sq(valor):
    """Literal SQL. None -> NULL."""
    if valor is None or valor == "":
        return "NULL"
    return "'" + str(valor).replace("'", "''") + "'"


# Las 22 dimensiones del catálogo (V10). Cualquier código fuera de esta lista no
# es una dimensión: es texto que se coló. Sin este filtro, títulos de caso como
# "SOP" o "Cash/negocio" acaban en pregunta_dimension y la migración revienta por
# clave foránea, que es un error feo de diagnosticar dentro de 4700 líneas de SQL.
DIMENSIONES = {
    "INT", "OWN", "INI", "CRI", "SER", "COM", "CTL", "SUP", "PRI", "DEC", "VEL",
    "LRN", "SYS", "QUA", "REL", "AUT", "PPL", "BUS", "POT", "SELF", "VAL", "FIT",
}


def dimensiones_de(celda):
    """'CRI/PRI/BUS' -> ['CRI','PRI','BUS']. Devuelve [] si no hay nada usable."""
    if not celda:
        return []
    partes = re.split(r"[/,\s]+", celda.strip().upper())
    return [d for d in partes if d in DIMENSIONES]


def opciones_forced_choice(pregunta, clave):
    """
    Forced-choice: 'A: texto...B: texto...' con clave 'A→VEL+2, INI+1 | B→CRI+2, DEC+1'.
    No hay respuesta correcta: ninguna opción lleva puntaje, solo incrementos de dimensión.
    """
    partes = re.split(r"(?:^|(?<=[.\s]))([AB]):\s*", pregunta)
    textos = {}
    for i in range(1, len(partes) - 1, 2):
        textos[partes[i]] = partes[i + 1].strip()

    incrementos = {}
    for tramo in clave.split("|"):
        m = re.match(r"\s*([AB])\s*[→\->]+\s*(.+)", tramo.strip())
        if not m:
            continue
        pares = []
        for dim, inc in re.findall(r"([A-Z]{3,4})\s*\+\s*(\d)", m.group(2)):
            pares.append((dim, int(inc)))
        incrementos[m.group(1)] = pares

    salida = []
    for letra in sorted(textos):
        salida.append({"letra": letra, "texto": textos[letra],
                       "puntaje": None, "dimensiones": incrementos.get(letra, [])})
    return salida


def opciones_con_score(celda_opciones, celda_score):
    """
    SJT y dilemas: 'A Duplicar Ads | B Exigir... | C ...' con score 'C=4,D=1,A=1,B=0'.
    Aquí sí hay puntaje por opción: es la clave versionada que el candidato nunca ve.
    """
    puntajes = {}
    for letra, punto in re.findall(r"\b([A-E])\s*=\s*(\d)", celda_score or ""):
        puntajes[letra] = int(punto)

    textos = {}
    for tramo in re.split(r"\s*\|\s*", celda_opciones or ""):
        m = re.match(r"\s*([A-E])[\s:.\-]+(.+)", tramo.strip())
        if m:
            textos[m.group(1)] = m.group(2).strip()

    salida = []
    for letra in sorted(set(textos) | set(puntajes)):
        salida.append({"letra": letra, "texto": textos.get(letra, ""),
                       "puntaje": puntajes.get(letra), "dimensiones": []})
    return salida


def alinear(cabecera, fila, codigo):
    """
    Empareja cada celda con su columna.

    No basta con `zip(cabecera, fila)`: a partir de S12 el documento se salta la
    columna «Situación» y todo lo demás se corre un puesto, dejando la última
    celda vacía. Emparejar por posición mete las opciones en «Situación» y la
    clave de puntaje en «Opciones», y el resultado es una pregunta sin clave.

    Así que las columnas que se reconocen por su forma se buscan por contenido:
    la de puntajes parece 'B=4,C=1,A=0', la de dimensiones 'SUP/CTL', y la de
    opciones trae 'A ... | B ...'. Lo que sobra se reparte por orden.
    """
    celdas = list(fila[1:])  # el ID ya está fuera
    columnas = list(cabecera[1:])
    col = {}

    def sacar(nombre, prueba):
        if nombre not in columnas:
            return
        for i, c in enumerate(celdas):
            if c and prueba(c):
                col[nombre] = c
                del celdas[i]
                columnas.remove(nombre)
                return

    def es_dimensiones(c):
        partes = [p for p in re.split(r"\s*/\s*", c.strip()) if p]
        return bool(partes) and all(p in DIMENSIONES for p in partes)

    sacar("score", lambda c: re.search(r"\b[A-E]\s*=\s*\d", c))
    sacar("evalúa", es_dimensiones)
    sacar("dimensión", es_dimensiones)
    sacar("opciones", lambda c: re.search(r"\|", c) and re.match(r"\s*[A-E][\s:.\-]", c))

    for nombre, celda in zip(columnas, celdas):
        col[nombre] = celda

    faltan = [c for c in cabecera[1:] if c not in col or not col[c]]
    if "score" in faltan or "clave" in faltan:
        avisos.append(f"{codigo}: no encontré la clave de puntaje")
    return col


def cargar_preguntas(tablas):
    """Recorre las tablas de cada banco y devuelve las preguntas normalizadas."""
    bancos = {}
    for nivel, cfg in BANCOS.items():
        preguntas = []
        for indice, tipo in cfg["tablas"]:
            filas = tablas[indice]
            cabecera = [c.lower() for c in filas[0]]
            for fila in filas[1:]:
                if not fila or not re.fullmatch(r"[DSO]\d{2}", fila[0].strip()):
                    continue
                codigo = fila[0].strip()
                if not codigo.startswith(cfg["prefijo"]):
                    avisos.append(f"{codigo} no empieza por {cfg['prefijo']} (tabla {indice})")

                col = alinear(cabecera, fila, codigo)
                dims = dimensiones_de(col.get("dimensión") or col.get("evalúa") or "")

                if tipo == "ESTILO":
                    crudo = col.get("pregunta", "")
                    opciones = opciones_forced_choice(crudo, col.get("clave interna", ""))
                    # La celda del .docx mete las dos opciones seguidas ("A: …B: …").
                    # Si se dejan también como enunciado, el candidato las ve dos veces.
                    enunciado = ("Elige la opción con la que más te identificas."
                                 if opciones else crudo)
                    # El "tradeoff" del documento es la clave del par que se compara
                    # ("VEL vs CRI"): son códigos de dimensión y el candidato no puede verlos
                    # (RF-53). Va a logica_interna, que nunca sale al portal.
                    situacion = None
                    interna = " · ".join(filter(None, [col.get("clave interna"),
                                                       col.get("tradeoff")]))
                elif tipo in ("SITUACION", "DILEMA"):
                    enunciado = (col.get("escenario") or col.get("dilema") or "").strip()
                    situacion = col.get("situación") or col.get("opciones / contexto")
                    clave = col.get("score") or col.get("clave provisional") or col.get("clave") or ""
                    opciones = opciones_con_score(col.get("opciones") or situacion or "", clave)
                    interna = clave
                    # S60 está en la tabla de dilemas pero dice "No hay opción; responder
                    # en 3 líneas": es abierta. Se corrige por lo que es, no por dónde está.
                    if not opciones:
                        tipo = "CONDUCTUAL"
                        interna = clave or situacion
                else:  # CONDUCTUAL, MICROCASO, CONSISTENCIA: abiertas, sin opciones
                    enunciado = (col.get("pregunta") or col.get("caso") or "").strip()
                    situacion = col.get("consigna") or col.get("situación")
                    interna = (col.get("lógica interna") or col.get("lógica")
                               or col.get("qué observar") or col.get("clave interna"))
                    opciones = []

                if not enunciado:
                    avisos.append(f"{codigo}: sin enunciado, se omite")
                    continue
                if tipo in ("SITUACION", "DILEMA") and not any(o["puntaje"] is not None for o in opciones):
                    avisos.append(f"{codigo}: {tipo} sin ninguna clave de puntaje")

                preguntas.append({
                    "codigo": codigo, "tipo": tipo, "enunciado": enunciado,
                    "situacion": situacion, "logica": interna, "dimensiones": dims,
                    # Estilo y consistencia no suman nota: son perfil y alerta (RF-54)
                    "puntuable": tipo not in ("ESTILO", "CONSISTENCIA"),
                    "opciones": opciones,
                })
        bancos[nivel] = preguntas
    return bancos


def cargar_pesos(tablas):
    """Los pesos por dimensión y nivel, repartiendo los pares agrupados."""
    pesos = {}
    for indice, nivel in PESOS.items():
        fila_peso = {}
        for fila in tablas[indice][1:]:
            if len(fila) < 2:
                continue
            grupo, valor = fila[0].strip().upper(), fila[1].strip()
            if not re.fullmatch(r"\d+([.,]\d+)?", valor):
                continue
            dims = [d for d in grupo.split("/") if re.fullmatch(r"[A-Z]{3,4}", d)]
            if not dims:
                avisos.append(f"{nivel}: no entiendo la dimensión '{grupo}'")
                continue
            cada = float(valor.replace(",", ".")) / len(dims)
            for d in dims:
                fila_peso[d] = fila_peso.get(d, 0) + cada
        suma = sum(fila_peso.values())
        if abs(suma - 100) > 0.01:
            avisos.append(f"{nivel}: los pesos de dimensión suman {suma:.2f}, no 100")
        pesos[nivel] = fila_peso
    return pesos


def emitir(bancos, pesos):
    p = print
    p("-- Generado por scripts/importar-banco-maestro.py — revisar antes de aplicar.")
    p("-- Fuente: docs/insumos/Banco_Maestro_Preguntas_RENASER_*.docx\n")

    for nivel, preguntas in bancos.items():
        etiqueta = BANCOS[nivel]["etiqueta"]
        p(f"-- ================= {nivel}: {len(preguntas)} preguntas =================")
        p("INSERT INTO version_banco (organizacion_id, tipo_banco, nivel_puesto_codigo, etiqueta, estado, publicada_en)")
        p(f"SELECT id, 'NIVEL', {sq(nivel)}, {sq(etiqueta)}, 'PUBLICADA', now()")
        p("FROM organizacion WHERE codigo = 'RENASER';\n")

        for orden, q in enumerate(preguntas, start=1):
            p("INSERT INTO pregunta (version_banco_id, codigo, bloque, tipo, enunciado, situacion, logica_interna, es_puntuable, orden)")
            p(f"SELECT id, {sq(q['codigo'])}, {sq(q['tipo'])}, {sq(q['tipo'])},")
            p(f"       {sq(q['enunciado'])}, {sq(q['situacion'])}, {sq(q['logica'])},")
            p(f"       {str(q['puntuable']).lower()}, {orden}")
            p(f"FROM version_banco WHERE etiqueta = {sq(etiqueta)};")

            for op in q["opciones"]:
                punt = "NULL" if op["puntaje"] is None else op["puntaje"]
                p("INSERT INTO opcion (pregunta_id, letra, texto, puntaje)")
                p(f"SELECT p.id, {sq(op['letra'])}, {sq(op['texto'])}, {punt}")
                p(f"FROM pregunta p JOIN version_banco v ON v.id = p.version_banco_id")
                p(f"WHERE v.etiqueta = {sq(etiqueta)} AND p.codigo = {sq(q['codigo'])};")
                for dim, inc in op["dimensiones"]:
                    p("INSERT INTO opcion_dimension (opcion_id, dimension_codigo, incremento)")
                    p(f"SELECT o.id, {sq(dim)}, {inc}")
                    p("FROM opcion o JOIN pregunta p ON p.id = o.pregunta_id")
                    p("JOIN version_banco v ON v.id = p.version_banco_id")
                    p(f"WHERE v.etiqueta = {sq(etiqueta)} AND p.codigo = {sq(q['codigo'])}")
                    p(f"  AND o.letra = {sq(op['letra'])};")

            for dim in q["dimensiones"]:
                p("INSERT INTO pregunta_dimension (pregunta_id, dimension_codigo)")
                p(f"SELECT p.id, {sq(dim)}")
                p("FROM pregunta p JOIN version_banco v ON v.id = p.version_banco_id")
                p(f"WHERE v.etiqueta = {sq(etiqueta)} AND p.codigo = {sq(q['codigo'])};")
            p("")

    p("-- ================= Pesos por dimensión =================")
    for nivel, filas in pesos.items():
        for dim, peso in sorted(filas.items()):
            p("INSERT INTO peso_dimension (version_pesos_id, nivel_puesto_codigo, dimension_codigo, peso)")
            p(f"SELECT id, {sq(nivel)}, {sq(dim)}, {peso:.2f} FROM version_pesos WHERE etiqueta = 'v2 hito 2';")
    p("")


def main():
    rutas = glob.glob(DOCX)
    if not rutas:
        sys.exit(f"No encuentro {DOCX}. Ejecuta desde la raíz del proyecto.")
    tablas = leer_tablas(rutas[0])
    bancos = cargar_preguntas(tablas)
    pesos = cargar_pesos(tablas)
    emitir(bancos, pesos)

    total = sum(len(v) for v in bancos.values())
    print(f"\n-- Total: {total} preguntas", file=sys.stderr)
    for nivel, qs in bancos.items():
        con_opciones = sum(1 for q in qs if q["opciones"])
        print(f"--   {nivel}: {len(qs)} ({con_opciones} con opciones)", file=sys.stderr)
    if avisos:
        print(f"\n-- {len(avisos)} avisos:", file=sys.stderr)
        for a in avisos:
            print(f"--   {a}", file=sys.stderr)


if __name__ == "__main__":
    main()
