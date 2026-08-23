#!/usr/bin/env python3
"""Genera el PDF de la prueba técnica de Administrador General (RENASER).

El texto del cuestionario va TAL CUAL está en
scripts/cargar-prueba-administrador.py (constantes ENUNCIADO y PREGUNTAS):
no se resume ni se reescribe. Solo se cambia el código ADMIN_Qxx por la
numeración 1..20.

Para regenerar con otro plazo o número de WhatsApp, edita las constantes
PLAZO_ENTREGA / WHATSAPP de aquí abajo y vuelve a ejecutar:

    python3 genera-prueba-tecnica-administrador.py [ruta-salida.pdf]
"""

import sys
from xml.sax.saxutils import escape

from reportlab.lib.colors import HexColor, white
from reportlab.lib.pagesizes import A4
from reportlab.lib.styles import ParagraphStyle
from reportlab.lib.units import mm
from reportlab.pdfgen import canvas as lienzo
from reportlab.platypus import (BaseDocTemplate, Frame, HRFlowable,
                                KeepTogether, PageTemplate, Paragraph, Spacer)

# ------------------------------------------------------------------ entrega

PLAZO_ENTREGA = ("Plazo de entrega: domingo 23 de agosto de 2026, "
                 "hasta las 11:59 p. m.")
WHATSAPP = "982 255 360"

INSTRUCCIONES_ENTREGA = [
    "Responde en un solo documento (Word o PDF), numerando cada respuesta "
    "igual que este cuestionario.",
    "Incluye tu nombre completo y tu número de contacto al inicio del documento.",
    f"Envía tu documento por WhatsApp al {WHATSAPP}.",
    PLAZO_ENTREGA,
]

# ------------------------------------------- el cuestionario, tal cual llegó

TITULO = ("CUESTIONARIO TÉCNICO — ADMINISTRADOR GENERAL · "
          "EMPRESA DE CAMBIO DE DIVISAS")

OBJETIVO = ("evaluar si el postulante posee experiencia práctica y suficiente "
            "para administrar una empresa de cambio de divisas con múltiples "
            "sedes, personal, manejo de caja, control operativo, coordinación "
            "administrativa y responsabilidad sobre resultados.")

INSTRUCCION = ("responde utilizando experiencias reales. Cuando corresponda, "
               "indica cantidades, número de personas, volumen de operaciones, "
               "herramientas utilizadas y resultados obtenidos.")

PREGUNTAS = [
    "¿Cuántos años de experiencia tienes administrando empresas, negocios "
    "o unidades operativas?\n\nIndica:\n• Empresa.\n• Cargo.\n• Tiempo.\n"
    "• Número de trabajadores.\n• Número de sedes.\n• Principales responsabilidades.",

    "¿Cuál ha sido la operación más grande que has administrado "
    "directamente?\n\nIndica aproximadamente:\n• Número de trabajadores.\n"
    "• Número de sedes.\n• Volumen de ventas o dinero administrado.\n"
    "• Número de clientes u operaciones.\n• Responsabilidades que estaban bajo tu cargo.",

    "Describe cómo era un día normal en tu último puesto administrativo.\n\n"
    "Queremos conocer qué actividades realizabas personalmente y cuáles delegabas.",

    "¿Has tenido responsabilidad directa sobre cajas, efectivo, bancos o "
    "dinero de una empresa?\n\nExplica exactamente qué controlabas y cuál era el monto "
    "o volumen aproximado que manejabas.",

    "Explícanos paso a paso cómo realizabas un cierre y cuadre de caja en "
    "tu anterior trabajo.\n\nIndica:\n• Qué información revisabas.\n"
    "• Qué documentos utilizabas.\n• Cómo identificabas diferencias.\n"
    "• Qué hacías cuando existía un descuadre.",

    "Si al finalizar el día una sede presenta un faltante de dinero, "
    "¿cuál sería tu procedimiento para determinar qué ocurrió?\n\n"
    "Describe el proceso exacto que seguirías.",

    "¿Qué controles implementarías para reducir el riesgo de errores, "
    "pérdidas o irregularidades en las cajas de tres sedes?\n\nExplica los controles "
    "que implementarías diariamente, semanalmente y mensualmente.",

    "¿Has trabajado anteriormente en una casa de cambio, empresa "
    "financiera, empresa de compra y venta de divisas o negocio con manejo intensivo "
    "de efectivo?\n\nSi tu respuesta es sí, indica:\n• Empresa.\n• Cargo.\n• Tiempo.\n"
    "• Funciones.\n• Volumen aproximado de operaciones.\n\nSi tu respuesta es no, "
    "explica qué experiencia consideras transferible para asumir esta responsabilidad.",

    "Explícanos qué aspectos consideras críticos para controlar "
    "correctamente una operación de compra y venta de dólares y soles.",

    "Si detectas que una sede está realizando operaciones pero su margen "
    "o rentabilidad está disminuyendo, ¿qué información revisarías primero para "
    "identificar la causa?",

    "¿Has administrado anteriormente más de una sede, sucursal, tienda o "
    "unidad de negocio simultáneamente?\n\nIndica:\n• Número de sedes.\n"
    "• Distancia entre ellas.\n• Número de trabajadores.\n• Cómo realizabas el control.",

    "Si tuvieras tres sedes y no pudieras estar físicamente en todas "
    "ellas todos los días, ¿cómo organizarías el sistema de supervisión?\n\nExplica:\n"
    "• Qué indicadores revisarías.\n• Con qué frecuencia.\n• Qué información "
    "exigirías.\n• Qué reuniones realizarías.\n• Cómo detectarías problemas sin "
    "estar presente.",

    "¿Cuántas personas has tenido directamente bajo tu responsabilidad?\n\n"
    "Indica el número máximo de personas y qué tipo de puestos administrabas.",

    "¿Has participado directamente en procesos de selección de personal?\n\n"
    "Describe el proceso que utilizabas desde que identificabas la necesidad de "
    "contratar hasta la incorporación de la persona.",

    "Si uno de los trabajadores de una sede tiene buenos resultados "
    "comerciales, pero constantemente presenta errores de caja o incumple "
    "procedimientos, ¿cómo actuarías?\n\nExplica qué evaluarías y qué medidas tomarías.",

    "¿Has tenido que despedir, sancionar o reemplazar a una persona por "
    "bajo rendimiento o incumplimiento?\n\nDescribe la situación y cómo la gestionaste.",

    "¿Qué experiencia tienes coordinando con contadores y revisando "
    "información contable?\n\nIndica qué documentos, reportes o indicadores revisabas "
    "personalmente.",

    "Si el contador te presenta un resultado financiero que no coincide "
    "con la información operativa de las sedes, ¿cómo investigarías la diferencia?\n\n"
    "Explica paso a paso qué información cruzarías.",

    "¿Has tenido responsabilidad directa sobre objetivos de ventas, "
    "rentabilidad o crecimiento de una empresa?\n\nIndica:\n• Qué objetivo tenías.\n"
    "• Cómo se medía.\n• Qué resultado conseguiste.\n• Qué acciones implementaste "
    "para conseguirlo.",

    "Si asumieras la administración de Corijón y te dijéramos que "
    "queremos aumentar significativamente la rentabilidad de las tres sedes durante "
    "los próximos 12 meses, ¿qué información necesitarías conocer durante tus "
    "primeros 30 días antes de proponer un plan de crecimiento?\n\nExplica qué "
    "revisarías en:\n• Ventas.\n• Márgenes.\n• Tipo de cambio.\n• Clientes.\n"
    "• Personal.\n• Competencia.\n• Gastos.\n• Operaciones.\n• Sedes.\n• Procesos.",
]

# (título de sección, índice de la primera pregunta, índice de la última) — base 1
SECCIONES = [
    ("1. EXPERIENCIA GENERAL EN ADMINISTRACIÓN", 1, 3),
    ("2. MANEJO Y CONTROL DE CAJA", 4, 7),
    ("3. EXPERIENCIA EN CASAS DE CAMBIO / DIVISAS", 8, 10),
    ("4. GESTIÓN DE MÚLTIPLES SEDES", 11, 12),
    ("5. GESTIÓN DE PERSONAL", 13, 16),
    ("6. CONTABILIDAD Y CONTROL FINANCIERO", 17, 18),
    ("7. OBJETIVOS Y CRECIMIENTO", 19, 20),
]

# ------------------------------------------------------------------- diseño

TINTA = HexColor("#0d0d0d")
GRIS = HexColor("#70706b")
LINEA = HexColor("#e3e3df")

ANCHO, ALTO = A4
M_IZQ = M_DER = 24 * mm
M_SUP = 22 * mm
M_INF = 24 * mm

PIE_TEXTO = "RENASER · Cuestionario técnico — Administrador General"


class LienzoNumerado(lienzo.Canvas):
    """Dibuja el pie «Página X de Y» conociendo el total al final."""

    def __init__(self, *args, **kwargs):
        super().__init__(*args, **kwargs)
        self._estados = []

    def showPage(self):
        self._estados.append(dict(self.__dict__))
        self._startPage()

    def save(self):
        total = len(self._estados)
        for estado in self._estados:
            self.__dict__.update(estado)
            self._pie(total)
            super().showPage()
        super().save()

    def _pie(self, total):
        self.saveState()
        self.setStrokeColor(LINEA)
        self.setLineWidth(0.7)
        self.line(M_IZQ, 16 * mm, ANCHO - M_DER, 16 * mm)
        self.setFont("Helvetica", 7.5)
        self.setFillColor(GRIS)
        self.drawString(M_IZQ, 11.5 * mm, PIE_TEXTO)
        self.drawRightString(ANCHO - M_DER, 11.5 * mm,
                             f"Página {self._pageNumber} de {total}")
        self.restoreState()


def estilos():
    base = dict(textColor=TINTA)
    return {
        "kicker": ParagraphStyle(
            "kicker", fontName="Helvetica-Bold", fontSize=9, leading=12,
            textColor=GRIS),
        "titulo": ParagraphStyle(
            "titulo", fontName="Times-Bold", fontSize=16.5, leading=21.5,
            spaceBefore=7, **base),
        "etiqueta": ParagraphStyle(
            "etiqueta", fontName="Helvetica-Bold", fontSize=8.5, leading=11,
            spaceBefore=15, spaceAfter=5, **base),
        "cuerpo": ParagraphStyle(
            "cuerpo", fontName="Times-Roman", fontSize=10.5, leading=15.5,
            **base),
        "entrega": ParagraphStyle(
            "entrega", fontName="Times-Roman", fontSize=10.5, leading=15.5,
            leftIndent=14, bulletIndent=3, spaceBefore=4, **base),
        "seccion": ParagraphStyle(
            "seccion", fontName="Helvetica-Bold", fontSize=9.5, leading=13,
            spaceBefore=21, **base),
        "pregunta": ParagraphStyle(
            "pregunta", fontName="Times-Roman", fontSize=10.5, leading=15.5,
            leftIndent=19, firstLineIndent=-19, spaceBefore=13, **base),
        "sigue": ParagraphStyle(
            "sigue", fontName="Times-Roman", fontSize=10.5, leading=15.5,
            leftIndent=19, spaceBefore=5, **base),
        "vineta": ParagraphStyle(
            "vineta", fontName="Times-Roman", fontSize=10.5, leading=15,
            leftIndent=33, bulletIndent=22, spaceBefore=2.5, **base),
    }


def parrafos_de(texto):
    """Divide el texto de una pregunta en párrafos y viñetas, en orden."""
    bloques, actual = [], []
    for linea in texto.split("\n"):
        if not linea.strip():
            if actual:
                bloques.append(("p", " ".join(actual)))
                actual = []
        elif linea.startswith("• "):
            if actual:
                bloques.append(("p", " ".join(actual)))
                actual = []
            bloques.append(("v", linea[2:]))
        else:
            actual.append(linea)
    if actual:
        bloques.append(("p", " ".join(actual)))
    return bloques


def flujo_pregunta(numero, texto, e):
    partes, primera = [], True
    for tipo, contenido in parrafos_de(texto):
        seguro = escape(contenido)
        if tipo == "v":
            partes.append(Paragraph(seguro, e["vineta"], bulletText="•"))
        elif primera:
            partes.append(Paragraph(f"<b>{numero}.</b>&nbsp;&nbsp;{seguro}",
                                    e["pregunta"]))
            primera = False
        else:
            partes.append(Paragraph(seguro, e["sigue"]))
    return partes


def construir(ruta_pdf):
    e = estilos()
    historia = []

    # --- cabecera
    historia.append(Paragraph("RENASER · Proceso de selección", e["kicker"]))
    historia.append(Paragraph(escape(TITULO), e["titulo"]))
    historia.append(Spacer(1, 9))
    historia.append(HRFlowable(width="100%", thickness=1.1, color=TINTA,
                               spaceAfter=0))

    # --- objetivo
    historia.append(Paragraph("OBJETIVO", e["etiqueta"]))
    historia.append(Paragraph(escape("Objetivo: " + OBJETIVO), e["cuerpo"]))

    # --- instrucciones (del enunciado) + entrega
    historia.append(Paragraph("INSTRUCCIONES", e["etiqueta"]))
    historia.append(Paragraph(escape("Instrucción: " + INSTRUCCION), e["cuerpo"]))
    historia.append(Spacer(1, 3))
    for linea in INSTRUCCIONES_ENTREGA:
        seguro = escape(linea)
        if seguro.startswith("Plazo de entrega:"):
            seguro = seguro.replace("Plazo de entrega:",
                                    "<b>Plazo de entrega:</b>", 1)
        historia.append(Paragraph(seguro, e["entrega"], bulletText="–"))
    historia.append(Spacer(1, 6))

    # --- las 20 preguntas por sección
    numero = 0
    for titulo_seccion, desde, hasta in SECCIONES:
        cabeza = [
            Paragraph(escape(titulo_seccion), e["seccion"]),
            Spacer(1, 4),
            HRFlowable(width="100%", thickness=0.8, color=LINEA),
        ]
        for i in range(desde, hasta + 1):
            numero += 1
            assert numero == i
            bloque = flujo_pregunta(numero, PREGUNTAS[numero - 1], e)
            if cabeza:
                historia.append(KeepTogether(cabeza + bloque))
                cabeza = None
            else:
                historia.append(KeepTogether(bloque))

    doc = BaseDocTemplate(
        ruta_pdf, pagesize=A4,
        leftMargin=M_IZQ, rightMargin=M_DER,
        topMargin=M_SUP, bottomMargin=M_INF,
        title="Cuestionario técnico — Administrador General · RENASER",
        author="RENASER", subject="Prueba técnica · Proceso de selección",
        creator="RENASER")
    marco = Frame(M_IZQ, M_INF, ANCHO - M_IZQ - M_DER, ALTO - M_SUP - M_INF,
                  leftPadding=0, rightPadding=0, topPadding=0, bottomPadding=0)

    def fondo(canv, _doc):
        canv.saveState()
        canv.setFillColor(white)
        canv.rect(0, 0, ANCHO, ALTO, stroke=0, fill=1)
        canv.restoreState()

    doc.addPageTemplates([PageTemplate(id="pagina", frames=[marco],
                                       onPage=fondo)])
    doc.build(historia, canvasmaker=LienzoNumerado)
    assert numero == 20


if __name__ == "__main__":
    salida = sys.argv[1] if len(sys.argv) > 1 else (
        "docs/insumos/pruebas-tecnicas/ADMIN - Cuestionario Tecnico Administrador General.pdf")
    construir(salida)
    print(f"PDF generado: {salida}")
