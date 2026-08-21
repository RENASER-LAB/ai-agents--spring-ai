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
Y una quinta que no sale del documento sino del sentido común: ningún ítem que se
responda eligiendo puede quedarse sin opciones que enseñar.

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
# El ⛔ y el ★ pueden ir DELANTE del código, no solo detrás: depende de dónde caiga el ítem
# en la página. Sin admitirlos delante, los nueve ítems eliminatorios (D52, D70, C33, C44,
# O19, O39...) no se reconocen como cabecera, su texto se pega al ítem anterior y el banco
# sale con nueve ítems de menos.
CABECERA = re.compile(
    r'^(?P<antes>[ \t\f\u2605\u26d4]*)(?P<cod>[DCO]\d{2})[ \t]*(?P<marcas>[\u2605\u26d4 \t]*)'
    r'(?:·[ \t]*(?P<fmt>EF-4|SJT-R|SEC|INV|DE|CD|V|PC)[ \t]*)?'
    r'·[ \t]*peso[ \t]*(?P<peso>\d)(?P<resto>.*)$')
BLOQUE = re.compile(r'^[ \t\f]*BLOQUE[ \t]+(?P<id>[A-C]\d?)[ \t]*·[ \t]*(?P<nombre>.*?)[ \t]*\(?$')

CIRCULOS = '\u2460\u2461\u2462\u2463\u2464\u2465\u2466\u2467\u2468\u2469'
BANDERA = '\u2691'   # ⚑ marca los elementos falsos de INV
CORRECTA, INCORRECTA = '\u2714', '\u2718'   # ✔ ✘ en DE

# --- lo que NO es enunciado, aunque pdftotext lo deje como una línea más del cuerpo ---
# El rótulo de la tabla de opciones ("Opción    Valor", "#    Afirmación") y la columna de
# la derecha cuando cae suelta. Sin esto acabaron en producción cuatro ítems preguntando
# literalmente «Opción                Valor»: eso es el rótulo del PDF, no la pregunta.
CABECERA_TABLA = re.compile(
    r'^\s*(?:#|Opci[oó]n|Rasgo|Afirmaci[oó]n|Paso|Clave|Valor|Puntaje|N[uú]mero|Condici[oó]n)'
    r'(?:\s{2,}\S.*)?\s*$')
SOLO_NUMERO = re.compile(r'^\s*[+\u2212-]?\d+(?:[.,]\d+)?\s*$')
INICIO_OPCION = re.compile(r'^\s*(?:[a-h]\)|[' + CIRCULOS + r'])')
# Dos espacios o más separan columnas en la salida de -layout: lo que va detrás es el valor
# o el rótulo de la columna derecha, no la continuación de la frase.
COLA_COLUMNA = re.compile(r'\s{2,}(?:[+\u2212-]?\d+|Valor|Clave|Puntaje|Ajuste|Orden)\s*$')

# La regla de un PC ("Contradicción: ...", «"No" = eliminatorio») dice cómo penaliza, y eso
# es para dentro: ni se le enseña al candidato ni forma parte de lo que se le pregunta.
REGLA_INTERNA = re.compile(
    r'^(?:Contradicci[oó]n|No suma puntaje|Responder\s|Cruce\s*:|"No"|\u201cNo\u201d)')

# El "Ninguno formal" / "Nada en particular" / "No aplicaban..." con el que cierran las
# listas de un INV. No es un elemento real ni uno inventado: el ítem declara cuántos hay de
# cada clase y ese no entra en ninguna de las dos cuentas. Ver elementos_inv.
ESCAPE_INV = re.compile('^(?:Nada|Ning\u00fan|Ningun|Ninguno|Ninguna|No aplica)',
                        re.IGNORECASE)

# La tabla de validación con que algunos CD cierran. Sus renglones no son campos ni
# continuación de uno: ahí terminan los campos. Ver campos_numerados.
CIERRA_CAMPOS = re.compile(r'^\s*(?:Rangos?|Validaci[oó]n)\s*:')

# Cómo declara un CD "suelto" cuántos campos tiene: «(7 campos)», «(6 campos × 3 = 18
# campos):», «5 campos cada una:», «con 4 campos:». Todo lo anterior al final de esa
# declaración es el enunciado del ítem, no el primer campo; sin este corte, el primer
# campo de cada CD suelto salía con la pregunta entera pegada delante.
DECLARACION_CAMPOS = re.compile(
    r'\d+\s*campos(?:\s+cada\s+una)?(?:\s*[×x]\s*\d+)?(?:\s*=\s*\d+\s*campos)?\s*\)?\s*:?')

avisos = []


def aviso(codigo, texto):
    avisos.append(f'{codigo}: {texto}')


def texto_del_pdf():
    if not PDF.exists():
        sys.exit(f'No está {PDF}. Es el insumo del cliente y debe estar en el repositorio.')
    if not shutil.which('pdftotext'):
        sys.exit('Falta pdftotext. En Debian/Ubuntu: sudo apt install poppler-utils')
    # Un directorio temporal y no un fichero temporal: en Windows el fichero se queda
    # abierto y pdftotext no puede escribir dentro. Y '-enc UTF-8' explícito porque el
    # valor por omisión depende de la compilación de poppler, y en algunas sale latin-1:
    # el acento se convierte en basura sin que nada falle.
    with tempfile.TemporaryDirectory() as dir_tmp:
        salida = Path(dir_tmp) / 'banco.txt'
        subprocess.run(['pdftotext', '-layout', '-enc', 'UTF-8', str(PDF), str(salida)],
                       check=True)
        return salida.read_text(encoding='utf-8')


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


def enunciado_de(cuerpo):
    """El texto de la pregunta. Casi nunca cabe en una línea, y no siempre es la primera.

    Dos cosas hay que saltarse. Una: pdftotext deja el rótulo de la tabla («Opción   Valor»,
    «#   Afirmación») y la columna de valores sueltos como si fueran líneas del cuerpo. Dos:
    el enunciado viene partido por el ancho de la página, y quedarse solo con la primera
    línea lo deja a medias («...que has aplicado tú», sin el «mismo»).

    Se juntan las líneas de texto hasta que empieza otra cosa: una línea en blanco, la
    primera opción, la lista de elementos de un INV (que va separada por ' · ') o la regla
    interna de un PC.

    Devuelve '' si no encuentra ninguna. Eso no es un fallo del que haya que recuperarse
    inventando texto: es un ítem que en el documento no trae enunciado, y sale por aviso.
    """
    partes = []
    for linea in cuerpo:
        limpia = linea.strip()
        if not limpia:
            if partes:
                break
            continue
        if CABECERA_TABLA.match(linea) or SOLO_NUMERO.match(linea):
            continue                                  # rótulo o columna, no pregunta
        if INICIO_OPCION.match(linea) or REGLA_INTERNA.match(limpia):
            break
        if partes and ' · ' in limpia:
            break                                     # empezó la lista de un INV
        partes.append(COLA_COLUMNA.sub('', linea).strip())
    return ' '.join(x for x in partes if x)


def letra_por_orden(n):
    """a, b, c... y de la z en adelante z1, z2. Las listas de un INV llegan a 14 elementos."""
    return chr(ord('a') + n) if n < 26 else 'z%d' % (n - 25)


def elementos_inv(cuerpo, enunciado):
    """La lista de un INV: los elementos, y cuáles son inventados.

    Están entre el enunciado y la línea del total («T = 7 reales · 3 falsos»), escritos
    corridos y separados por '·', partidos por el ancho de la página. Por eso se pega todo
    primero y se corta después: cortar línea a línea parte los elementos largos por la mitad.

    El «Ninguno formal» del final NO se devuelve como elemento. El puntaje del formato es
    (reales marcados ÷ reales totales), así que guardarlo como real subiría el denominador
    de todos y nadie podría sacar el máximo; y guardarlo como inventado castigaría a quien
    dice la verdad. Quien no hace nada de la lista responde no marcando nada, que da lo mismo.

    Devuelve (elementos, declarado) — declarado es el (reales, falsos) que dice el ítem.
    """
    texto = ' '.join(l.strip() for l in cuerpo if l.strip())
    total = re.search(r'T\s*=\s*(\d+)\s*reales?\s*·\s*(\d+)\s*falsos?', texto)
    lista = texto[:total.start()] if total else texto
    if enunciado and lista.startswith(enunciado):
        lista = lista[len(enunciado):]
    elementos = []
    for trozo in lista.split('·'):
        inventado = BANDERA in trozo
        limpio = trozo.replace(BANDERA, '').strip(' .')
        if not limpio or ESCAPE_INV.match(limpio):
            continue
        # La letra la pone el orden en que están escritos, porque el documento no se las da.
        # Sin ella la fila no tiene llave: (pregunta, letra) es única en la tabla opcion.
        elementos.append({'letra': letra_por_orden(len(elementos)),
                          'texto': limpio, 'es_distractor': inventado})
    declarado = (int(total.group(1)), int(total.group(2))) if total else None
    return elementos, declarado


def afirmaciones_de(cuerpo):
    """Las ocho afirmaciones de un DE, cada una con su ✔ o su ✘.

    Van en una tabla de dos columnas: el número en círculo y la frase, con la marca al final
    de la fila. Una frase larga se parte en dos líneas y la segunda queda indentada, así que
    hay que absorberla; lo que empieza en el margen izquierdo, en cambio, ya es el comentario
    que va debajo de la tabla. Se paran a las ocho, que es el número que el formato fija.
    """
    filas = []
    for linea in cuerpo:
        if not linea.strip() or CABECERA_TABLA.match(linea):
            continue
        cabeza = re.match(r'\s*([' + CIRCULOS + r'])\s*(.*)$', linea)
        if cabeza:
            if len(filas) >= 8:
                break
            marca = next((c for c in (CORRECTA, INCORRECTA) if c in cabeza.group(2)), None)
            filas.append({'numero': CIRCULOS.index(cabeza.group(1)) + 1,
                          'textos': [cabeza.group(2)], 'marca': marca})
        elif (filas and len(filas) < 8 and linea[:1] in (' ', '\t')
                and CORRECTA not in linea and INCORRECTA not in linea):
            filas[-1]['textos'].append(linea.strip())
    for fila in filas:
        junto = ' '.join(x.strip() for x in fila.pop('textos') if x.strip())
        fila['texto'] = re.sub(r'\s+', ' ', junto.replace(CORRECTA, '')
                               .replace(INCORRECTA, '').strip())
    return filas


def campos_numerados(cuerpo):
    """Los campos de un CD escritos como lista numerada (1. 2. 3.), enteros.

    Cada campo trae sus alternativas entre paréntesis, y como son largas el PDF las parte
    en dos o tres renglones: el primero abre con «N.» y los demás siguen indentados. La
    primera versión de esta lectura se quedaba con el renglón del «N.» y tiraba el resto,
    y así llegaron a producción 24 etiquetas cortadas a media lista —«medio día / un día o»
    sin el «más)»— en seis ítems. Lo perdido eran justo las alternativas, que en un CD no
    viven en ninguna otra tabla.

    Un renglón indentado se absorbe al campo abierto. Cierran la absorción: el siguiente
    «N.», una línea en blanco (cierra el campo pero se sigue buscando el siguiente número,
    por si un salto de página se metió en medio), la tabla de «Rangos:»/«Validación:» y la
    regla interna del ítem. Los rótulos de tabla y las columnas de números sueltos se
    saltan, igual que en el resto de lecturas.
    """
    campos, abierto = [], False
    for linea in cuerpo:
        limpia = linea.strip()
        if not limpia:
            abierto = False
            continue
        if CIERRA_CAMPOS.match(linea) or REGLA_INTERNA.match(limpia):
            abierto = False
            continue
        if CABECERA_TABLA.match(linea) or SOLO_NUMERO.match(linea):
            continue
        m = re.match(r'^\s*(\d+)\.\s+(\S.*)$', linea)
        if m:
            campos.append(m.group(2).strip())
            abierto = True
        elif abierto and linea[:1] in (' ', '\t', '\f'):
            campos[-1] += ' ' + COLA_COLUMNA.sub('', linea).strip()
        else:
            abierto = False
    return campos


def opciones_pc(cuerpo):
    """Lo que el candidato elige en un par de consistencia.

    El documento lo escribe de tres maneras y las tres se leen aquí, porque en las tres hay
    algo que marcar: la lista «a) ... · b) ...», la autorización «Sí / No» y las alternativas
    entre paréntesis «(nunca / una vez / algunas veces)». Hay un cuarto caso que no tiene
    nada que elegir —el que pide escribir un número en un hueco «___»— y para ese se devuelve
    la lista vacía con forma 'escrita': es correcto que no tenga opciones.

    Solo se mira la parte de arriba, la que ve el candidato. Debajo va la regla («Contradicción:
    opción (d) + frecuencia...»), y leerla como si fuera contenido fue lo que metió en la base
    una opción «d2» con el texto de la penalización dentro.
    """
    utiles = []
    for linea in cuerpo:
        limpia = linea.strip()
        if not limpia:
            continue
        if REGLA_INTERNA.match(limpia):
            break
        if CABECERA_TABLA.match(linea) or SOLO_NUMERO.match(linea):
            continue
        utiles.append(COLA_COLUMNA.sub('', linea).strip())
    texto = ' '.join(utiles)

    letras = re.findall(r'(?:^|\s)([a-h])\)\s*([^·\n]+)', texto)
    if letras and letras[0][0] == 'a':
        return [{'letra': l, 'texto': t.strip(' .')} for l, t in letras], 'lista'
    if re.search(r'\bS[ií]\s*/\s*No\b', texto):
        return [{'letra': 'a', 'texto': 'Sí'}, {'letra': 'b', 'texto': 'No'}], 'si/no'
    entre_parentesis = re.search(r'\(([^()]*\s/\s[^()]*)\)', texto)
    if entre_parentesis:
        trozos = [t.strip() for t in entre_parentesis.group(1).split('/') if t.strip()]
        if len(trozos) >= 2:
            return ([{'letra': chr(ord('a') + n), 'texto': t}
                     for n, t in enumerate(trozos)], 'parentesis')
    return [], 'escrita'


def parsear_contenido(codigo, formato, cuerpo, enunciado):
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
        elementos, declarado = elementos_inv(cuerpo, enunciado)
        if not declarado:
            return {'texto': texto.strip()}, False
        reales = [e for e in elementos if not e['es_distractor']]
        falsos = [e for e in elementos if e['es_distractor']]
        # La comprobación de verdad: el ítem declara cuántos reales y cuántos inventados
        # tiene, así que si el troceo se comió uno o partió una frase en dos, las cuentas
        # no cuadran y salta. Sin esto no habría forma de saber si la lista se leyó entera.
        return ({'opciones': elementos, 'reales': len(reales), 'falsos': len(falsos)},
                (len(reales), len(falsos)) == declarado)

    if formato == 'DE':
        filas = afirmaciones_de(cuerpo)
        correctas = [f for f in filas if f['marca'] == CORRECTA]
        distractores = [f for f in filas if f['marca'] == INCORRECTA]
        opciones = [{'letra': chr(ord('a') + n), 'texto': f['texto'],
                     'es_distractor': f['marca'] == INCORRECTA}
                    for n, f in enumerate(filas)]
        return ({'opciones': opciones, 'correctas': len(correctas),
                 'distractores': len(distractores)},
                len(filas) == 8 and len(correctas) == 4 and len(distractores) == 4)

    if formato == 'CD':
        # Dos formas en el documento: lista numerada (1. 2. 3.) o los campos descritos
        # en una línea separados por '·'. Las dos son válidas.
        # Campos por caso. El documento lo declara de cuatro formas: "(7 campos)",
        # "(5 campos × 3)", "(6 campos × 3 = 18 campos)" y "5 campos cada una". En todas,
        # el número que interesa es el primero: los demás son la multiplicación por caso.
        declarados = re.search(r'(\d+)\s*campos?', texto)
        numerados = campos_numerados(cuerpo)
        esperados = int(declarados.group(1)) if declarados else None
        rangos = re.search(r'^\s*(?:Rangos?|Validaci[oó]n):\s*(.+)$', texto, re.M)
        preambulo = None
        if numerados:
            campos, cuadra = numerados, len(numerados) == esperados
        else:
            sueltos = [c.strip() for c in re.split(r'\s·\s', texto.replace('\n', ' '))
                       if c.strip()]
            # El primer trozo llega con el enunciado pegado delante («Tu día típico.
            # (5 campos) Hora en que despiertas ___»): la pregunta y el primer campo van
            # corridos en el documento y el '·' solo separa campos entre sí. El corte es
            # el final de la declaración de campos, que en todos los ítems sueltos existe.
            m = None
            for m in DECLARACION_CAMPOS.finditer(sueltos[0] if sueltos else ''):
                pass                                  # interesa la última aparición
            if m and sueltos[0][m.end():].strip():
                preambulo = sueltos[0][:m.end()].strip()
                sueltos[0] = sueltos[0][m.end():].strip()
            elif sueltos:
                aviso(codigo, 'CD suelto sin declaración de campos reconocible: el primer '
                              'campo puede llevar el enunciado pegado')
            campos, cuadra = sueltos, esperados is not None and len(sueltos) >= 2
        return ({'campos_esperados': esperados, 'campos': campos, 'preambulo': preambulo,
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
        opciones, forma = opciones_pc(cuerpo)
        return ({'opciones': opciones, 'forma_respuesta': forma,
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
        '-- al v0.1, que esta misma migración archiva, así que son las únicas que se asignan. El',
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

    # Las opciones de todos los formatos que se responden eligiendo. EF-4 guarda su valor
    # oculto y SJT-R la calificación esperada; INV y DE no esconden número pero sí cuáles de
    # sus elementos son inventados, y PC solo guarda el texto porque no puntúa.
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
            letra = o.get('letra') or letra_por_orden(len(vistas))
            vistas[letra] = vistas.get(letra, 0) + 1
            if vistas[letra] > 1:
                letra = f'{letra}{vistas[letra]}'
            columna = 'valor' if it['formato'] == 'EF-4' else 'puntaje'
            opciones.append((it['codigo'], letra, o.get('texto', ''),
                             o.get('clave'), columna, bool(o.get('es_distractor'))))
    if opciones:
        out += ['', '-- Opciones. EF-4 esconde un valor de −2 a +2 (columna valor); en SJT-R el',
                '-- número es la calificación correcta de 1 a 5 (columna puntaje). En INV y DE',
                '-- lo que se esconde es es_distractor: cuál de los elementos no existe.']
        filas = [f'    ({lit(c)}, {lit(l)}, {lit(txt)}, '
                 f'{lit(cl) if col == "valor" else "NULL"}, '
                 f'{lit(cl) if col == "puntaje" else "NULL"}, {lit(dis)})'
                 for c, l, txt, cl, col, dis in opciones]
        out.append(
            'INSERT INTO opcion (pregunta_id, letra, texto, valor, puntaje, es_distractor)\n'
            'SELECT p.id, v.letra, v.texto, v.valor, v.puntaje, v.es_distractor\n'
            '  FROM pregunta p\n'
            '  JOIN version_banco vb ON vb.id = p.version_banco_id, (VALUES\n'
            + ',\n'.join(filas) + '\n'
            '  ) AS v(codigo, letra, texto, valor, puntaje, es_distractor)\n'
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
        # El recorte a 500 existe por prudencia y nunca debería actuar: si actúa es que el
        # plegado de continuaciones absorbió algo que no era del campo, y callarlo sería
        # repetir el fallo de los campos cortados, solo que al revés.
        for c, n, txt in campos:
            if len(txt) > 500:
                aviso(c, f'el campo {n} pasa de 500 caracteres y la migración lo recortaría')
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
        antes = mc.group('antes') or ''
        formato = mc.group('fmt')
        # El ⛔ puede ir antes del código, detrás, o dentro del texto de la derecha.
        eliminatorio = '\u26d4' in antes or '\u26d4' in marcas or '\u26d4' in resto
        # Un ítem eliminatorio no escribe su formato: el ⛔ ocupa ese sitio. Es un par de
        # consistencia, que es además como se guardaba ya en la base.
        if not formato and eliminatorio:
            formato = 'PC'

        enunciado = enunciado_de(cuerpo)
        contenido, ok = ({}, True) if not formato else parsear_contenido(
            codigo, formato, cuerpo, enunciado)
        # En los CD sueltos el preámbulo separado del primer campo ES el enunciado. Hoy los
        # dos caminos leen lo mismo, pero si una edición futura del PDF parte la declaración
        # de campos de forma que enunciado_de rompa antes (su parada en ' · '), el preámbulo
        # trae la versión entera. Solo se adopta si extiende (o iguala) lo ya leído: si
        # difieren de raíz, manda lo conservador.
        preambulo = contenido.get('preambulo')
        if preambulo:
            preambulo = re.sub(r'\s+', ' ', preambulo)
            if preambulo.startswith(enunciado):
                enunciado = preambulo
        if not enunciado:
            aviso(codigo, "sin enunciado: en el documento el ítem arranca directo en su tabla")
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

    # --- las comprobaciones: cuatro contra lo que el documento declara de sí mismo, y
    # una quinta contra lo que hace falta para poder contestar ---
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

    # --- la quinta: nadie se queda sin nada que marcar ---
    # Las cuatro de arriba cuentan ítems y puntos, y por eso dejaron pasar el fallo que
    # llegó a producción: 28 ítems cargados, contados y publicados, y sin una sola opción
    # que enseñar. Un INV que dice "marca lo que haces siempre" y no trae lista no se puede
    # responder, y como la pantalla exige la evaluación completa, el candidato se atasca.
    # El único formato que puede quedarse sin opciones es el PC que se contesta escribiendo
    # (un número en un hueco), y eso lo dice el propio parseo, no una lista de códigos.
    CON_OPCIONES = ('EF-4', 'SJT-R', 'SEC', 'INV', 'DE', 'PC')
    mudos = []
    for x in items:
        if x['formato'] not in CON_OPCIONES:
            continue
        if x['formato'] == 'SEC':
            tiene = bool(x['contenido'].get('pasos'))
        else:
            tiene = bool(x['contenido'].get('opciones'))
        if not tiene and x['contenido'].get('forma_respuesta') != 'escrita':
            mudos.append(f'{x["codigo"]} ({x["formato"]})')
    if mudos:
        aviso('varios', 'se quedan sin opciones y así no se pueden responder: '
                        + ', '.join(mudos))

    # --- la sexta: ningún texto cortado a media frase ---
    # Las cinco de arriba cuentan; ninguna lee. Así llegaron a producción 24 etiquetas de
    # campo y una tanda de enunciados cortados por el ancho de página del PDF: siete campos
    # cortados siguen siendo siete campos y todos los totales cuadran. Un paréntesis que se
    # abre y no se cierra es la firma de ese corte, porque las alternativas viven entre
    # paréntesis. Lo que este heurístico no ve: un corte fuera de paréntesis (un enunciado
    # que termina en «los últimos 3» pasa limpio); para eso está
    # scripts/comparar-banco-v3-con-base.py, que compara contra la base ya cargada.
    desparejados = []
    for x in items:
        textos = [('enunciado', x['enunciado'])] + [
            (f'campo {n}', c)
            for n, c in enumerate(x['contenido'].get('campos') or [], 1)]
        for donde, t in textos:
            if t and t.count('(') != t.count(')'):
                desparejados.append(f'{x["codigo"]} ({donde})')
    if desparejados:
        aviso('varios', 'paréntesis sin pareja, huele a texto cortado: '
                        + ', '.join(desparejados))

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
