#!/usr/bin/env python3
"""
Compara el banco v3 cargado en la base contra el PDF del cliente, texto a texto.

Existe porque las comprobaciones del importador cuentan y esta lee. A producción llegaron
24 etiquetas de campo y una tanda de enunciados cortados por el ancho de página del PDF, y
ningún total dejó de cuadrar: siete campos cortados siguen siendo siete campos. La única
forma de ver ese fallo es comparar lo guardado contra la fuente, que es lo que se hace aquí.

    python3 scripts/comparar-banco-v3-con-base.py                # comparar; sale 0 si todo casa
    python3 scripts/comparar-banco-v3-con-base.py --db v28_check # contra otra base del contenedor
    python3 scripts/comparar-banco-v3-con-base.py --emitir-v28   # las tuplas de la migración

Qué compara, por qué así:

- **Etiquetas de campo_caso**: igualdad (normalizada en blancos) contra lo que el parser lee
  hoy del PDF. El parser plegó siempre bien los campos sueltos y desde el arreglo de 2026-08
  también los numerados, así que aquí se exige coincidencia exacta.
- **Enunciados**: igualdad contra el parser, con dos excepciones que no son fallos.
  Una: si el parser no leyó enunciado (los cuatro EF-4 que arrancan directo en su tabla:
  C40, D51, D60, O37), manda la base — su texto lo escribió a mano la V25 §4 y no está en
  el PDF. Dos: los ítems de ENUNCIADOS_A_MANO, donde el parser no sabe leer el texto entero
  (su lectura rompe en un ' · ' de la continuación) o absorbe material de la tabla de
  rangos; ahí el texto bueno vive abajo, decidido leyendo el PDF, y la V28 lo cargó.
- **El árbitro, contra el cuerpo bruto del ítem**: cada texto de la base se busca en el
  cuerpo aplanado de su ítem tal como sale del PDF. Si no está, o si lo que sigue delata un
  corte —un paréntesis abierto sin cerrar, o que la frase continúe en minúscula—, se avisa.
  Esto caza lo que la comparación contra el parser no puede ver: un texto truncado igual en
  la base y en la lectura del parser.

No toca la base: solo lee, vía psql dentro del contenedor de docker-compose. No necesita
nada instalado fuera de lo que ya pide el importador.
"""

import argparse
import json
import re
import subprocess
import sys

CONTENEDOR = 'renaser-postgres'
SEPARADOR = '\x01'   # un carácter que no aparece en ningún texto del banco

# Los enunciados que el parser no puede leer completos y alguien decidió leyendo el PDF.
# Cada uno con su porqué. Son también la fuente de la V28 §d: si se cambia uno aquí hay
# que cambiarlo allá, y este diccionario es el que lo vigila después.
ENUNCIADOS_A_MANO = {
    # La lectura del parser rompe en el ' · ' de la línea de continuación y se queda a
    # medias; el texto sigue en el PDF hasta donde empieza su tabla de tramos.
    'D08': 'De tu última semana típica: ___% dirigiendo personas · ___% ejecutando tareas '
           'propias · ___% en reuniones ajenas a tu área. (Debe sumar 100.)',
    'D84': '¿Qué estás aprendiendo actualmente? (texto ≤ 40 car.) · Formato (curso formal / '
           'autodidacta / con mentor / en el trabajo) · Horas por semana ___ · Meses que '
           'llevas ___ · Evidencia (certificado / proyecto aplicado / ninguna aún)',
    'C54': '¿Qué estás aprendiendo actualmente? (texto ≤ 40 car.) · Formato (lista) · Horas '
           'por semana ___ · Meses que llevas ___ · Evidencia (certificado / aplicado en el '
           'trabajo / ninguna aún)',
    'O48': '¿Qué estás aprendiendo actualmente? (texto ≤ 40 car.) · Cómo (curso / por mi '
           'cuenta / me enseña alguien / en el trabajo) · Horas por semana ___ · Meses que '
           'llevas ___',
    # La lectura rompe en el ' · ' de la escala «(1 = se cae · 5 = sigue igual)», que va
    # en la línea de continuación.
    'C10': 'Faltas una semana completa sin previo aviso. Califica del 1 al 5 qué tan '
           'probable es que siga funcionando (1 = se cae · 5 = sigue igual).',
    'D20': 'Te ausentas dos semanas sin previo aviso. Califica del 1 al 5 qué tan probable '
           'es que cada cosa siga funcionando (1 = se cae · 5 = sigue igual).',
    # La segunda parte del ítem («Decisiones que tomabas (multi): ...») es pregunta, no
    # tabla: su respuesta puntúa en los tramos («... y define estructura o reasigna
    # partidas»). La lectura rompe en su primer ' · '.
    'D40': 'Presupuesto anual administrado: (no he manejado / < S/ 100K / 100K–500K / '
           '500K–2M / 2M–10M / > 10M) Decisiones que tomabas (multi): aprobar gastos · '
           'reasignar partidas · negociar con proveedores · definir la estructura del '
           'presupuesto · solo ejecutaba lo aprobado',
    # El parser absorbe al enunciado el «Misma tabla que D57.», que es la referencia a la
    # tabla de tramos, no parte de la pregunta.
    'C36': 'Actividad física: (nunca / esporádica / 1–2 por semana / 3–4 / diaria) · Años '
           'sosteniéndola: ___',
    'O32': 'Actividad física: (nunca / esporádica / 1–2 por semana / 3–4 / diaria) · Años '
           'sosteniéndola: ___',
    # El parser absorbe la línea «Puntaje = ...», que es la fórmula (ya guardada en
    # formula_puntaje), no la pregunta. En O07 el enunciado de la base ya estaba bien y se
    # fija para que nadie lo «complete» con eso; en O02 la base además estaba cortada.
    'O02': 'Escribe hasta 5 herramientas, equipos o programas que dominas para este '
           'puesto. (5 campos de texto ≤ 30 car.)',
    'O07': 'Certificados, cursos o constancias del oficio: hasta 3 (nombre + institución '
           '+ año).',
}


def norma(s):
    """Blancos colapsados: el PDF parte renglones donde quiere y eso no es una diferencia."""
    return ' '.join((s or '').split())


def lit(v):
    """Un literal SQL, comillas simples dobladas. Copiado de importar-banco-v3.py:
    seis líneas repetidas a propósito antes que hacer que este script importe aquel."""
    return "'" + str(v).replace("'", "''") + "'"


def del_parser():
    """La lectura de hoy del PDF, pidiéndosela al importador (su JSON es el contrato)."""
    r = subprocess.run([sys.executable, 'scripts/importar-banco-v3.py'],
                       capture_output=True, text=True)
    if not r.stdout.strip():
        sys.exit(f'El importador no devolvió nada:\n{r.stderr}')
    # El importador sale con 1 si tiene avisos, y varios son sabidos (los EF-4 sin
    # enunciado, los PC sin regla titulada). Aquí importa su lectura, no su veredicto.
    return {x['codigo']: x for x in json.loads(r.stdout)}


def de_la_base(db):
    def consulta(sql):
        r = subprocess.run(['docker', 'exec', CONTENEDOR, 'psql', '-U', 'postgres',
                            '-d', db, '-At', '-F', SEPARADOR, '-c', sql],
                           capture_output=True, text=True)
        if r.returncode != 0:
            sys.exit(f'psql falló: {r.stderr}')
        return [l.split(SEPARADOR) for l in r.stdout.split('\n') if l]

    enunciados = {c: e for c, e in consulta(
        "SELECT p.codigo, p.enunciado FROM pregunta p"
        " JOIN version_banco vb ON vb.id = p.version_banco_id"
        " WHERE vb.etiqueta LIKE 'Banco RENASER v3%'")}
    campos = {(c, int(o)): e for c, o, e in consulta(
        "SELECT p.codigo, cc.orden, cc.etiqueta FROM campo_caso cc"
        " JOIN pregunta p ON p.id = cc.pregunta_id"
        " JOIN version_banco vb ON vb.id = p.version_banco_id"
        " WHERE vb.etiqueta LIKE 'Banco RENASER v3%'")}
    return enunciados, campos


def parece_cortado(texto, bruto):
    """El corte deja firma: o el texto no está en el cuerpo del ítem, o lo que le sigue
    lo delata — un paréntesis quedó abierto, o la frase continúa en minúscula."""
    t = norma(texto)
    if len(t) < 8:
        return None
    i = bruto.find(t)
    if i < 0:
        return 'no está así en el cuerpo del ítem'
    resto = bruto[i + len(t):].lstrip()
    if t.count('(') > t.count(')'):
        return f'paréntesis sin cerrar; sigue «{resto[:60]}»'
    # Que detrás venga la lista de opciones («a) ...») o una pregunta nueva («¿Cuántas...»)
    # no es señal de corte: es que el enunciado terminó y empieza otra cosa. La minúscula
    # sí lo es: ninguna frase del banco arranca en minúscula.
    if re.match(r'^[a-h]\)|^¿', resto):
        return None
    if re.match(r'^[a-záéíóúñ(]', resto):
        return f'la frase sigue «{resto[:60]}»'
    return None


def main():
    argumentos = argparse.ArgumentParser()
    argumentos.add_argument('--db', default='renaser_db')
    argumentos.add_argument('--emitir-v28', action='store_true')
    opciones = argumentos.parse_args()

    items = del_parser()
    enunciados_db, campos_db = de_la_base(opciones.db)
    if not enunciados_db:
        sys.exit(f'La base {opciones.db} no tiene el banco v3 cargado.')

    diferencias = []

    # --- enunciados ---
    filas_enunciado = []   # (codigo, viejo, nuevo, es_prefijo)
    for codigo, en_db in sorted(enunciados_db.items()):
        leido = ENUNCIADOS_A_MANO.get(codigo) or items[codigo]['enunciado']
        if not leido:
            continue   # los cuatro EF-4: su enunciado lo escribió la V25, no el PDF
        if norma(en_db) != norma(leido):
            diferencias.append(f'enunciado {codigo}: base «{en_db[:60]}…» ≠ PDF «{leido[:60]}…»')
            filas_enunciado.append((codigo, en_db, leido, leido.startswith(en_db)))

    # --- etiquetas de campo_caso ---
    filas_campo = []   # (codigo, orden, viejo, nuevo, es_prefijo)
    for (codigo, orden), en_db in sorted(campos_db.items()):
        campos = items[codigo]['contenido'].get('campos') or []
        if orden > len(campos):
            diferencias.append(f'campo {codigo}.{orden}: está en la base y el parser no lo lee')
            continue
        leido = campos[orden - 1]
        if norma(en_db) != norma(leido):
            diferencias.append(f'campo {codigo}.{orden}: base «{en_db[:60]}…» ≠ PDF «{leido[:60]}…»')
            filas_campo.append((codigo, orden, en_db, leido, leido.startswith(en_db)))

    # --- el árbitro: nada de lo guardado puede oler a cortado ---
    for codigo, en_db in sorted(enunciados_db.items()):
        if not items[codigo]['enunciado'] and codigo not in ENUNCIADOS_A_MANO:
            continue   # texto escrito a mano en la V25: no viene del PDF y no se busca en él
        bruto = norma(items[codigo]['texto_bruto'])
        senal = parece_cortado(en_db, bruto)
        if senal:
            diferencias.append(f'enunciado {codigo} huele a cortado: {senal}')
    for (codigo, orden), en_db in sorted(campos_db.items()):
        senal = parece_cortado(en_db, norma(items[codigo]['texto_bruto']))
        if senal:
            diferencias.append(f'campo {codigo}.{orden} huele a cortado: {senal}')

    if opciones.emitir_v28:
        emitir_v28(filas_enunciado, filas_campo)
        return 0

    for d in diferencias:
        print('DIFERENCIA ·', d)
    print(f'\n{len(enunciados_db)} enunciados y {len(campos_db)} campos comparados · '
          f'{"todo casa con el PDF" if not diferencias else f"{len(diferencias)} diferencias"}')
    return 0 if not diferencias else 1


def emitir_v28(filas_enunciado, filas_campo):
    """Las tuplas VALUES de la V28, clasificadas por la guarda que les toca.

    Prefijo exacto (lo guardado es el principio del texto bueno) → guarda starts_with,
    que completa sin pisar. Lo demás → guarda de igualdad con el texto viejo exacto,
    que tampoco pisa una edición hecha a mano desde el panel.
    """
    a = [(c, o, n) for c, o, _, n, pref in filas_campo if pref]
    b = [(c, o, v, n) for c, o, v, n, pref in filas_campo if not pref]
    cc = [(c, n) for c, _, n, pref in filas_enunciado if pref]
    d = [(c, v, n) for c, v, n, pref in filas_enunciado if not pref]

    print(f'-- §a · {len(a)} etiquetas truncadas (guarda starts_with)')
    for c, o, n in a:
        print(f'    ({lit(c)}, {o}, {lit(n)}),')
    print(f'\n-- §b · {len(b)} etiquetas con el enunciado pegado delante (guarda de igualdad)')
    for c, o, v, n in b:
        print(f'    ({lit(c)}, {o}, {lit(v)}, {lit(n)}),')
    print(f'\n-- §c · {len(cc)} enunciados cortados (guarda starts_with)')
    for c, n in cc:
        print(f'    ({lit(c)}, {lit(n)}),')
    print(f'\n-- §d · {len(d)} enunciados donde lo guardado no es prefijo del bueno '
          f'(guarda de igualdad)')
    for c, v, n in d:
        print(f'    ({lit(c)}, {lit(v)}, {lit(n)}),')


if __name__ == '__main__':
    sys.exit(main())
