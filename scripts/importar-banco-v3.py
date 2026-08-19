#!/usr/bin/env python3
"""
Convierte el Banco RENASER v3 del cliente (PDF) en JSON revisable.

Por qué un script y no escribir la migración a mano: son 190 ítems con ocho formatos
distintos, cada uno con su clave de puntuación, y copiarlos a mano garantiza erratas.
Por qué emite JSON y no toca la base: la salida se revisa antes de convertirla en
migración, igual que hizo importar-banco-maestro.py con el banco v0.1.

    python3 scripts/importar-banco-v3.py         > /tmp/banco-v3.json   # para revisarlo
    python3 scripts/importar-banco-v3.py --sql   > /tmp/banco-v3.sql    # para la migración

Todo lo que no sabe interpretar sale por stderr con el código del ítem. **Nunca rellena
un valor**: si un ítem no cuadra, se dice y se revisa a mano.

Las comprobaciones son lo importante de este script. El documento declara sus propios
totales en la sección 0.3, y aquí se recalculan desde lo parseado: si el parser se
equivoca al leer un peso o una clave, los totales dejan de cuadrar y salta. Son cuatro
independientes: número de ítems, cuántos puntúan, cuántos son clave y el puntaje máximo.

Hace falta `pdftotext` (paquete poppler-utils). Se usa con -layout porque las claves
viven en tablas y sin esa opción las columnas se mezclan.
"""

import json
import re
import shutil
import subprocess
import sys
import tempfile
from pathlib import Path

PDF = Path('docs/insumos/banco-renaser-v3-completo.pdf')

# Lo que el documento declara de sí mismo en la sección 0.3.
BANCOS = {
    'D': {'nombre': 'DIRECTIVO', 'items': 85, 'puntuables': 81, 'clave': 15, 'maximo': 288},
    'C': {'nombre': 'COORDINACION_SUPERVISION', 'items': 55, 'puntuables': 52, 'clave': 10,
          'maximo': 186},
    'O': {'nombre': 'EJECUTIVO_OPERATIVO', 'items': 50, 'puntuables': 47, 'clave': 9,
          'maximo': 168},
}

FORMATOS = ('EF-4', 'SJT-R', 'SEC', 'INV', 'DE', 'CD', 'V', 'PC')

# \f es el salto de página: pdftotext lo pega al código del primer ítem de cada página,
# y sin contemplarlo se pierden justo esos (aquí eran cuatro).
CABECERA = re.compile(
    r'^[ \t\f]*(?P<cod>[DCO]\d{2})[ \t]*(?P<marcas>[\u2605\u26d4 \t]*)'
    r'(?:·[ \t]*(?P<fmt>EF-4|SJT-R|SEC|INV|DE|CD|V|PC)[ \t]*)?'
    r'·[ \t]*peso[ \t]*(?P<peso>\d)(?P<resto>.*)$')
BLOQUE = re.compile(r'^[ \t\f]*BLOQUE[ \t]+(?P<id>[A-C]\d?)[ \t]*·[ \t]*(?P<nombre>.*?)[ \t]*\(?$')

CIRCULOS = '\u2460\u2461\u2462\u2463\u2464\u2465\u2466\u2467\u2468\u2469'
BANDERA = '\u2691'   # ⚑ marca los elementos falsos de INV
CORRECTA, INCORRECTA = '\u2714', '\u2718'   # ✔ ✘ en DE

avisos = []


def aviso(codigo, texto):
    avisos.append(f'{codigo}: {texto}')


def texto_del_pdf():
    if not PDF.exists():
        sys.exit(f'No está {PDF}. Es el insumo del cliente y debe estar en el repositorio.')
    if not shutil.which('pdftotext'):
        sys.exit('Falta pdftotext. En Debian/Ubuntu: sudo apt install poppler-utils')
    with tempfile.NamedTemporaryFile(suffix='.txt') as tmp:
        subprocess.run(['pdftotext', '-layout', str(PDF), tmp.name], check=True)
        return Path(tmp.name).read_text(encoding='utf-8')


def trocear(lineas):
    """Parte el documento en ítems: cabecera + las líneas hasta la cabecera siguiente."""
    crudos, bloque = [], (None, None)
    for i, linea in enumerate(lineas):
        mb = BLOQUE.match(linea)
        if mb:
            bloque = (mb.group('id'), mb.group('nombre').rstrip(' ·(').strip())
            continue
        mc = CABECERA.match(linea)
        if mc:
            crudos.append((mc, bloque, i))
    for n, (mc, bloque, i) in enumerate(crudos):
        fin = crudos[n + 1][2] if n + 1 < len(crudos) else len(lineas)
        yield mc, bloque, lineas[i + 1:fin]


def _numero(txt):
    """El documento usa el menos tipográfico (−, U+2212), no el guion ASCII."""
    return int(txt.replace('\u2212', '-').replace('+', ''))


def _absorber(grupo, linea):
    """Mete una línea en su fila: o es el valor de la columna derecha, o es texto."""
    suelto = re.fullmatch(r'\s*([+\u2212-]?\d+)\s*', linea)
    pegado = re.match(r'^(.*?)\s{2,}([+\u2212-]?\d+)\s*$', linea)
    if suelto and grupo['clave'] is None:
        grupo['clave'] = _numero(suelto.group(1))
    elif pegado and grupo['clave'] is None:
        grupo['textos'].append(pegado.group(1))
        grupo['clave'] = _numero(pegado.group(2))
    else:
        grupo['textos'].append(linea)


def tabla_opciones(cuerpo):
    """Filas 'a) texto ... valor'.

    Con -layout una opción larga se parte en varias líneas y su valor queda solo en
    la suya, así que la fila no se puede leer de una línea. Y una línea en blanco no
    siempre cierra la fila: solo cuenta como cierre si la fila ya encontró su valor.
    Eso es lo que separa la tabla del párrafo que viene después de ella.
    """
    grupos, cerrado = [], False
    for l in cuerpo:
        if not l.strip():
            if grupos and grupos[-1]['clave'] is not None:
                cerrado = True
            continue
        m = re.match(r'\s*([a-h])\)\s*(.*)$', l)
        if m:
            grupos.append({'letra': m.group(1), 'textos': [], 'clave': None})
            cerrado = False
            _absorber(grupos[-1], m.group(2))
        elif grupos and not cerrado:
            _absorber(grupos[-1], l)

    for g in grupos:
        g['texto'] = ' '.join(x.strip() for x in g.pop('textos') if x.strip())
    return grupos


def opciones_en_linea(texto):
    """Variante en prosa: 'a) texto (+2) · b) texto (+1)' o 'a) texto — 5 · b) texto — 4'.

    Parte de los ítems no traen tabla: las opciones van seguidas en el párrafo, con el
    valor entre paréntesis o tras una raya. Es la misma información y hay que leerla
    igual, así que se intenta esta forma cuando la tabla no da nada.
    """
    plano = ' '.join(texto.split())
    trozos = re.split(r'(?=\b[a-h]\))', plano)
    grupos = []
    for tr in trozos:
        m = re.match(r'([a-h])\)\s*(.+)', tr)
        if not m:
            continue
        letra, resto = m.group(1), m.group(2).strip(' ·')
        val = (re.search(r'\(([+\u2212-]?\d+)\)', resto)
               or re.search(r'[\u2014\u2013]\s*([+\u2212-]?\d+)(?:\s|$)', resto)
               or re.search(r'\s-\s*([+\u2212-]?\d+)(?:\s|$)', resto))
        if not val:
            continue
        grupos.append({'letra': letra, 'clave': _numero(val.group(1)),
                       'texto': resto[:val.start()].strip(' ·—–-')})
    return grupos


def tabla_rangos(cuerpo):
    """Filas 'condición ... puntaje' de los ítems V. El puntaje admite cola: en varios
    vale '0 + bandera', y esa bandera es parte de la regla, no ruido. La cabecera no
    genera fila porque su columna derecha ('Puntaje') no es un número."""
    filas = []
    for l in cuerpo:
        m = re.match(r'^\s*(?P<cond>\S.*?\S)\s{2,}(?P<val>\d+)(?P<cola>\s*\+[^\d].*)?\s*$', l)
        if m:
            filas.append({'condicion': m.group('cond'), 'puntaje': int(m.group('val')),
                          'bandera': bool(m.group('cola'))})
    return filas


def parsear_contenido(codigo, formato, cuerpo):
    """Lo propio de cada formato. Devuelve (dato, cuadra) — cuadra=False saca un aviso."""
    texto = '\n'.join(cuerpo)
    circulados = [l.strip() for l in cuerpo if l.strip() and l.strip()[0] in CIRCULOS]

    if formato in ('SJT-R', 'EF-4'):
        opciones = tabla_opciones(cuerpo)
        con_clave = [o for o in opciones if o['clave'] is not None]
        if len(con_clave) != len(opciones) or not opciones:
            en_linea = opciones_en_linea(texto)
            if len(en_linea) >= len(con_clave):
                opciones, con_clave = en_linea, en_linea
        if formato == 'EF-4':
            cuadra = len(opciones) == 4 and len(con_clave) == 4 and all(
                -2 <= o['clave'] <= 2 for o in con_clave)
        else:
            cuadra = len(opciones) >= 2 and len(con_clave) == len(opciones) and all(
                1 <= o['clave'] <= 5 for o in con_clave)
        return {'opciones': opciones}, cuadra

    if formato == 'SEC':
        m = re.search(r'Clave:\s*([' + CIRCULOS + r'\s\u2192]+)', texto)
        clave = [CIRCULOS.index(c) + 1 for c in m.group(1) if c in CIRCULOS] if m else []
        return ({'pasos': circulados, 'clave': clave},
                len(circulados) == 5 and sorted(clave) == [1, 2, 3, 4, 5])

    if formato == 'INV':
        m = re.search(r'T\s*=\s*(\d+)\s*reales?\s*·\s*(\d+)\s*falsos?', texto)
        if not m:
            return {'texto': texto.strip()}, False
        # La propia línea del total lleva una bandera de leyenda: no es un elemento.
        elementos = texto[:m.start()]
        marcados = elementos.count(BANDERA)
        falsos = int(m.group(2))
        return ({'reales': int(m.group(1)), 'falsos': falsos, 'falsos_marcados': marcados},
                marcados == falsos)

    if formato == 'DE':
        return ({'correctas': texto.count(CORRECTA), 'distractores': texto.count(INCORRECTA),
                 'afirmaciones': circulados},
                texto.count(CORRECTA) == 4 and texto.count(INCORRECTA) == 4)

    if formato == 'CD':
        # Dos formas en el documento: lista numerada (1. 2. 3.) o los campos descritos
        # en una línea separados por '·'. Las dos son válidas.
        # Campos por caso. El documento lo declara de cuatro formas: "(7 campos)",
        # "(5 campos × 3)", "(6 campos × 3 = 18 campos)" y "5 campos cada una". En todas,
        # el número que interesa es el primero: los demás son la multiplicación por caso.
        declarados = re.search(r'(\d+)\s*campos?', texto)
        numerados = re.findall(r'^\s*(\d+)\.\s+(\S.*)$', texto, re.M)
        esperados = int(declarados.group(1)) if declarados else None
        rangos = re.search(r'^\s*(?:Rangos?|Validaci[oó]n):\s*(.+)$', texto, re.M)
        if numerados:
            campos, cuadra = [c[1].strip() for c in numerados], len(numerados) == esperados
        else:
            sueltos = [c.strip() for c in re.split(r'\s·\s', texto.replace('\n', ' '))
                       if c.strip()]
            campos, cuadra = sueltos, esperados is not None and len(sueltos) >= 2
        return ({'campos_esperados': esperados, 'campos': campos,
                 'rangos': rangos.group(1).strip() if rangos else None}, cuadra)

    if formato == 'V':
        filas = tabla_rangos(cuerpo)
        # Varios ítems no repiten la tabla: remiten a la de otro ("Misma tabla que D57").
        ref = re.search(r'[Mm]isma tabla que\s+([DCO]\d{2})', texto)
        if ref:
            return {'rangos': [], 'misma_tabla_que': ref.group(1)}, True
        formula = re.search(r'Puntaje\s*=\s*(.+?)(?:\.|$)', texto, re.M)
        if not filas and formula:
            return {'rangos': [], 'formula': formula.group(1).strip()}, True
        return ({'rangos': filas},
                len(filas) >= 2 and all(0 <= f['puntaje'] <= 3 for f in filas))

    if formato == 'PC':
        # No todos los pares titulan la regla "Contradicción": algunos la escriben
        # directamente ('Responder "nunca" habiendo descrito un caso en O16 → −5%...').
        # Lo que siempre está es su efecto, y por ahí se reconoce.
        m = (re.search(r'Contradicci[oó]n\s*:?\s*(.+)$', texto, re.M | re.S)
             or re.search(r'^(.*\u2212\s*5\s*%.*?bandera.*?)$', texto, re.M | re.S))
        opciones = re.findall(r'([a-h])\)\s*([^·\n]+)', texto)
        return ({'opciones': [{'letra': l, 'texto': t.strip()} for l, t in opciones],
                 'contradiccion': m.group(1).strip() if m else None},
                m is not None)

    return {'texto': texto.strip()}, True


ETIQUETAS = {
    'D': ('Banco RENASER v3 · Directivo', 'DIRECCION'),
    'C': ('Banco RENASER v3 · Coordinación y Supervisión', 'SUPERVISION'),
    'O': ('Banco RENASER v3 · Ejecutivo y Operativo', 'EJECUCION'),
}


def lit(v):
    """Un literal SQL. Las comillas simples se doblan; no se concatena nada sin pasar de aquí."""
    if v is None or v == '':
        return 'NULL'
    if isinstance(v, bool):
        return 'true' if v else 'false'
    if isinstance(v, (int, float)):
        return str(v)
    return "'" + str(v).replace("'", "''") + "'"


def emitir_sql(items):
    """El cuerpo de la migración de datos. Se revisa antes de pegarlo, como el del v0.1."""
    out = [
        '-- Generado por scripts/importar-banco-v3.py — no editar a mano: si hay que cambiar',
        '-- algo, se corrige el script y se vuelve a generar, o la próxima carga lo pisa.',
        '',
        '-- Las tres versiones del banco, una por nivel de puesto. Publicadas: el v3 reemplaza',
        '-- al v0.1, que esta misma migración borra, así que son las únicas que quedan. El',
        '-- documento del cliente viene cerrado y él lo declara definitivo.',
    ]
    for pref, (etiqueta, nivel) in ETIQUETAS.items():
        out.append(
            'INSERT INTO version_banco (organizacion_id, tipo_banco, nivel_puesto_codigo, '
            'etiqueta, estado, publicada_en)\n'
            f'SELECT id, \'NIVEL\', {lit(nivel)}, {lit(etiqueta)}, \'PUBLICADA\', now()\n'
            '  FROM organizacion WHERE codigo = \'RENASER\';')

    for pref, (etiqueta, _) in ETIQUETAS.items():
        del_banco = [x for x in items if x['codigo'][0] == pref]
        out += ['', f'-- {etiqueta} · {len(del_banco)} ítems']
        filas = []
        for n, it in enumerate(del_banco, 1):
            c = it['contenido']
            filas.append('    (' + ', '.join([
                lit(it['codigo']), lit(it['bloque']), lit(it['formato'] or 'PC'),
                lit(it['enunciado']), lit(it['peso'] > 0), str(it['peso']),
                lit(it['es_clave']), lit(it['es_eliminatorio']), str(n),
                lit(c.get('campos_esperados')), lit(c.get('misma_tabla_que')),
                lit(c.get('formula')),
            ]) + ')')
        out.append(
            'INSERT INTO pregunta (version_banco_id, codigo, bloque, tipo, enunciado,\n'
            '                      es_puntuable, peso, es_clave, es_eliminatorio, orden,\n'
            '                      casos_pedidos, rangos_de_pregunta_codigo, formula_puntaje)\n'
            'SELECT vb.id, v.codigo, v.bloque, v.tipo, v.enunciado, v.es_puntuable, v.peso,\n'
            '       v.es_clave, v.es_eliminatorio, v.orden, v.casos_pedidos,\n'
            '       v.rangos_de, v.formula\n'
            '  FROM version_banco vb, (VALUES\n' + ',\n'.join(filas) + '\n'
            '  ) AS v(codigo, bloque, tipo, enunciado, es_puntuable, peso, es_clave,\n'
            '         es_eliminatorio, orden, casos_pedidos, rangos_de, formula)\n'
            f' WHERE vb.etiqueta = {lit(etiqueta)};')

    # Las opciones con clave: EF-4 guarda su valor oculto, SJT-R la calificación esperada.
    opciones = []
    for it in items:
        # Varios SJT-R plantean el mismo escenario para tres personas distintas (08a, 08b,
        # 08c), y cada serie repite a) b) c) d). Como (pregunta, letra) es única en la tabla,
        # a la segunda serie en adelante se le pega el número: a2, b2, a3... Así no se pierde
        # ninguna opción ni se mezcla con la serie de al lado.
        vistas = {}
        for o in it['contenido'].get('opciones', []):
            if o.get('clave') is None and o.get('texto') is None:
                continue
            letra = o.get('letra')
            vistas[letra] = vistas.get(letra, 0) + 1
            if vistas[letra] > 1:
                letra = f'{letra}{vistas[letra]}'
            columna = 'valor' if it['formato'] == 'EF-4' else 'puntaje'
            opciones.append((it['codigo'], letra, o.get('texto', ''),
                             o.get('clave'), columna))
    if opciones:
        out += ['', '-- Opciones. EF-4 esconde un valor de −2 a +2 (columna valor); en SJT-R el',
                '-- número es la calificación correcta de 1 a 5 (columna puntaje).']
        filas = [f'    ({lit(c)}, {lit(l)}, {lit(txt)}, '
                 f'{lit(cl) if col == "valor" else "NULL"}, '
                 f'{lit(cl) if col == "puntaje" else "NULL"})'
                 for c, l, txt, cl, col in opciones]
        out.append(
            'INSERT INTO opcion (pregunta_id, letra, texto, valor, puntaje)\n'
            'SELECT p.id, v.letra, v.texto, v.valor, v.puntaje\n'
            '  FROM pregunta p\n'
            '  JOIN version_banco vb ON vb.id = p.version_banco_id, (VALUES\n'
            + ',\n'.join(filas) + '\n'
            '  ) AS v(codigo, letra, texto, valor, puntaje)\n'
            " WHERE p.codigo = v.codigo AND vb.etiqueta LIKE 'Banco RENASER v3%';")

    # Los pasos de SEC van como opciones también, pero lo que importa es su orden correcto.
    pasos = [(it['codigo'], n, p, k)
             for it in items if it['formato'] == 'SEC'
             for n, (p, k) in enumerate(zip(it['contenido']['pasos'],
                                            it['contenido']['clave']), 1)]
    if pasos:
        out += ['', '-- SEC · los cinco pasos y el lugar que le toca a cada uno']
        filas = [f'    ({lit(c)}, {lit(str(n))}, {lit(txt)}, {k})' for c, n, txt, k in pasos]
        out.append(
            'INSERT INTO opcion (pregunta_id, letra, texto, orden_correcto)\n'
            'SELECT p.id, v.letra, v.texto, v.orden\n'
            '  FROM pregunta p\n'
            '  JOIN version_banco vb ON vb.id = p.version_banco_id, (VALUES\n'
            + ',\n'.join(filas) + '\n'
            '  ) AS v(codigo, letra, texto, orden)\n'
            " WHERE p.codigo = v.codigo AND vb.etiqueta LIKE 'Banco RENASER v3%';")

    # Las tablas de puntaje de los ítems V.
    rangos = [(it['codigo'], n, r['condicion'], r['puntaje'], r['bandera'])
              for it in items if it['formato'] == 'V'
              for n, r in enumerate(it['contenido'].get('rangos', []), 1)]
    if rangos:
        out += ['', '-- V · la tabla de tramos propia de cada ítem']
        filas = [f'    ({lit(c)}, {n}, {lit(cond)}, {p}, {lit(b)})'
                 for c, n, cond, p, b in rangos]
        out.append(
            'INSERT INTO rango_pregunta (pregunta_id, orden, condicion, puntaje, genera_bandera)\n'
            'SELECT p.id, v.orden, v.condicion, v.puntaje, v.bandera\n'
            '  FROM pregunta p\n'
            '  JOIN version_banco vb ON vb.id = p.version_banco_id, (VALUES\n'
            + ',\n'.join(filas) + '\n'
            '  ) AS v(codigo, orden, condicion, puntaje, bandera)\n'
            " WHERE p.codigo = v.codigo AND vb.etiqueta LIKE 'Banco RENASER v3%';")

    # Los campos de los casos descompuestos.
    campos = [(it['codigo'], n, campo)
              for it in items if it['formato'] == 'CD'
              for n, campo in enumerate(it['contenido'].get('campos', []), 1)]
    if campos:
        out += ['', '-- CD · los campos de cada caso, en orden']
        filas = [f'    ({lit(c)}, {n}, {lit(txt[:500])})' for c, n, txt in campos]
        out.append(
            'INSERT INTO campo_caso (pregunta_id, orden, etiqueta)\n'
            'SELECT p.id, v.orden, v.etiqueta\n'
            '  FROM pregunta p\n'
            '  JOIN version_banco vb ON vb.id = p.version_banco_id, (VALUES\n'
            + ',\n'.join(filas) + '\n'
            '  ) AS v(codigo, orden, etiqueta)\n'
            " WHERE p.codigo = v.codigo AND vb.etiqueta LIKE 'Banco RENASER v3%';")

    # Los pares de consistencia: no suman, penalizan.
    pares = [(it['codigo'], otro, it['contenido'].get('contradiccion'))
             for it in items if it['formato'] == 'PC'
             for otro in it['pares_consistencia']]
    if pares:
        out += ['', '-- PC · cada par con su condición. Penalizan un 5% del global y levantan',
                '-- bandera roja; se muestran al menos 15 ítems después de su pareja.']
        filas = [f'    ({lit(a)}, {lit(b)}, {lit((cond or "")[:500])})' for a, b, cond in pares]
        out.append(
            'INSERT INTO par_consistencia (version_banco_id, pregunta_a_id, pregunta_b_id,\n'
            '                              penalizacion_porcentaje, separacion_minima_items,\n'
            '                              condicion)\n'
            'SELECT pa.version_banco_id, pa.id, pb.id, 5.00, 15, v.condicion\n'
            '  FROM (VALUES\n' + ',\n'.join(filas) + '\n'
            '  ) AS v(cod_a, cod_b, condicion)\n'
            '  JOIN pregunta pa ON pa.codigo = v.cod_a\n'
            '  JOIN pregunta pb ON pb.codigo = v.cod_b\n'
            '  JOIN version_banco vb ON vb.id = pa.version_banco_id\n'
            "                       AND vb.etiqueta LIKE 'Banco RENASER v3%'\n"
            ' WHERE pb.version_banco_id = pa.version_banco_id;')

    return '\n'.join(out) + '\n'


def main():
    lineas = texto_del_pdf().split('\n')
    items = []

    for mc, bloque, cuerpo in trocear(lineas):
        codigo = mc.group('cod')
        marcas, resto = mc.group('marcas'), mc.group('resto') or ''
        formato = mc.group('fmt')
        eliminatorio = '\u26d4' in marcas or '\u26d4' in resto

        enunciado = next((l.strip() for l in cuerpo if l.strip()), '')
        contenido, ok = ({}, True) if not formato else parsear_contenido(codigo, formato, cuerpo)
        if not ok:
            aviso(codigo, f'el contenido de {formato} no cuadra con su forma esperada')
        if not formato and not eliminatorio:
            aviso(codigo, 'sin formato reconocido y no está marcado como eliminatorio')

        items.append({
            'codigo': codigo,
            'banco': BANCOS[codigo[0]]['nombre'],
            'bloque': bloque[0],
            'bloque_nombre': bloque[1],
            'formato': formato,
            'peso': int(mc.group('peso')),
            'es_clave': '\u2605' in marcas,
            'es_eliminatorio': eliminatorio,
            'pares_consistencia': re.findall(r'([DCO]\d{2})', resto),
            'enunciado': enunciado,
            'contenido': contenido,
            'texto_bruto': '\n'.join(cuerpo).strip(),
        })

    # --- las cuatro comprobaciones contra lo que el documento declara de sí mismo ---
    print(f'{"banco":26}{"ítems":>7}{"puntúan":>9}{"clave ★":>9}{"máximo":>8}', file=sys.stderr)
    todo_cuadra = True
    for pref, esp in BANCOS.items():
        b = [x for x in items if x['codigo'][0] == pref]
        real = (len(b), sum(1 for x in b if x['peso'] > 0), sum(1 for x in b if x['es_clave']),
                sum(x['peso'] * 3 for x in b))
        quiere = (esp['items'], esp['puntuables'], esp['clave'], esp['maximo'])
        cuadra = real == quiere
        todo_cuadra &= cuadra
        detalle = ''.join(f'{r:>{a}}' for r, a in zip(real, (7, 9, 9, 8)))
        print(f'{esp["nombre"]:26}{detalle}'
              f'{"" if cuadra else "   <-- NO CUADRA, esperado " + str(quiere)}', file=sys.stderr)

    repes = {c for c in (x['codigo'] for x in items)
             if [y['codigo'] for y in items].count(c) > 1}
    if repes:
        aviso('varios', f'códigos repetidos: {sorted(repes)}')

    for a in avisos:
        print('AVISO ·', a, file=sys.stderr)
    print(f'\n{len(items)} ítems · '
          f'{"todo cuadra" if todo_cuadra and not avisos else "REVISAR lo de arriba"}',
          file=sys.stderr)

    if '--sql' in sys.argv:
        sys.stdout.write(emitir_sql(items))
    else:
        json.dump(items, sys.stdout, ensure_ascii=False, indent=1)
    return 0 if todo_cuadra and not avisos else 1


if __name__ == '__main__':
    sys.exit(main())
