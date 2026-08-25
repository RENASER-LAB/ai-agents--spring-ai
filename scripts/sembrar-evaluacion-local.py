#!/usr/bin/env python3
"""Siembra una evaluación ENTREGADA y CALIFICADA en la base local.

Existe porque la base local no tenía ninguna: el desglose de la evaluación
(`GET /panel/postulaciones/{id}/evaluacion`) no se podía ver con datos de
verdad, solo con tests. Este guion recorre el camino real del candidato:
crea una cuenta, postula con un CV inventado, responde la evaluación entera
—cada pregunta según su formato— y la entrega. La entrega encola la
calificación por IA, que es la que escribe `nota_respuesta`.

⚠️ Escribe en la base LOCAL y gasta llamadas reales a DeepSeek. Nunca
apuntarlo a producción.

    python3 scripts/sembrar-evaluacion-local.py [puerto]   # 8082 por defecto
"""

import json
import sys
import time
import urllib.error
import urllib.request
import uuid as uuid_mod

PUERTO = sys.argv[1] if len(sys.argv) > 1 else "8082"
BASE = f"http://localhost:{PUERTO}/api/v1/portal"
MARCA = time.strftime("%H%M%S")
CORREO = f"siembra.evaluacion.{MARCA}@example.com"

# Cada abierta se responde según lo que PIDE su enunciado: la IA califica de
# verdad, y contestar lo mismo a todo produce ceros con razón (ya pasó).
# La última entrada, sin palabras clave, es el comodín.
RESPUESTAS_ABIERTAS = [
    (("años", "experiencia", "empresa"),
     "Cuatro años en este trabajo: dos en una casa de cambio y dos en una "
     "financiera. En dos empresas. Último puesto: analista de operaciones."),
    (("herramientas", "equipos", "programas"),
     "Excel avanzado con tablas dinámicas, Google Sheets, el ERP Concar, "
     "SQL básico para consultas, y Power BI para los tableros del cierre."),
    (("certificados", "cursos", "constancias"),
     "Diplomado en gestión de operaciones (ESAN, 2024). Curso de Excel para "
     "finanzas (Idat, 2023). Constancia de auxiliar contable (Cibertec, 2022)."),
    (("tardanzas", "faltas", "avisaste"),
     "El último mes: cero faltas y una tardanza de 15 minutos por un paro de "
     "transporte; avisé por el grupo apenas supe, una hora antes de mi turno."),
    (("física", "deporte", "sostenién"),
     "Corro tres veces por semana desde hace dos años, unos 5 km por salida. "
     "Los domingos juego fútbol con el equipo del barrio."),
    (("aprendiendo", "aprender"),
     "Estoy aprendiendo Power BI por mi cuenta, con un curso en línea y "
     "practicando con los datos del cierre. Le dedico 4 horas por semana y "
     "llevo tres meses; ya armé dos tableros que usa mi jefa."),
    ((),
     "En mi trabajo anterior detecté un descuadre de 1.200 soles por un tipo "
     "de cambio mal aplicado. Reconstruí las operaciones, corregí las cuatro "
     "afectadas y armé un control que validaba el tipo de cambio antes del "
     "cierre. No volvió a pasar en seis meses."),
]


def texto_para(enunciado):
    normal = enunciado.lower()
    for claves, texto in RESPUESTAS_ABIERTAS:
        if any(c in normal for c in claves):
            return texto
    return RESPUESTAS_ABIERTAS[-1][1]


def pedir(ruta, cuerpo=None, token=None, metodo=None):
    datos = json.dumps(cuerpo).encode() if cuerpo is not None else None
    peticion = urllib.request.Request(
        BASE + ruta, data=datos, method=metodo or ("POST" if datos else "GET"))
    peticion.add_header("Content-Type", "application/json")
    if token:
        peticion.add_header("Authorization", f"Bearer {token}")
    with urllib.request.urlopen(peticion) as r:
        contenido = r.read()
        return json.loads(contenido) if contenido else None


def postular_multipart(token, vacante_id, requisitos):
    frontera = uuid_mod.uuid4().hex
    partes = []

    def campo(nombre, valor):
        partes.append(
            f"--{frontera}\r\nContent-Disposition: form-data; "
            f'name="{nombre}"\r\n\r\n{valor}\r\n'.encode())

    campo("vacanteId", vacante_id)
    campo("resultadoOrgulloso",
          "Dejé el cierre de caja diario cuadrando solo, con un control que "
          "detecta el tipo de cambio mal aplicado antes de cerrar.")
    for r in requisitos:
        campo("requisitosConfirmados", r)
    cv = (
        "CURRICULUM DE SIEMBRA - QA LOCAL\n\n"
        "Analista de operaciones, 4 anos conciliando cajas y armando controles.\n"
        "Logro: descuadre de 1200 soles detectado y corregido; cierre de 40 a 25 min.\n"
    ).encode()
    partes.append(
        f"--{frontera}\r\nContent-Disposition: form-data; name=\"cv\"; "
        f"filename=\"cv-siembra.pdf\"\r\nContent-Type: application/pdf\r\n\r\n".encode()
        + cuerpo_pdf(cv) + b"\r\n")
    partes.append(f"--{frontera}--\r\n".encode())

    peticion = urllib.request.Request(BASE + "/postulaciones",
                                      data=b"".join(partes), method="POST")
    peticion.add_header("Content-Type", f"multipart/form-data; boundary={frontera}")
    peticion.add_header("Authorization", f"Bearer {token}")
    with urllib.request.urlopen(peticion) as r:
        return json.loads(r.read())["codigo"]


def cuerpo_pdf(texto):
    """Un PDF mínimo válido: el backend exige PDF o Word de verdad."""
    contenido = texto.decode().replace("(", "").replace(")", "")
    lineas = contenido.split("\n")
    flujo = "BT /F1 11 Tf 40 780 Td 14 TL " + "".join(
        f"({l}) Tj T* " for l in lineas) + "ET"
    objetos = [
        "<< /Type /Catalog /Pages 2 0 R >>",
        "<< /Type /Pages /Kids [3 0 R] /Count 1 >>",
        "<< /Type /Page /Parent 2 0 R /MediaBox [0 0 612 792] "
        "/Resources << /Font << /F1 4 0 R >> >> /Contents 5 0 R >>",
        "<< /Type /Font /Subtype /Type1 /BaseFont /Helvetica >>",
        f"<< /Length {len(flujo)} >>\nstream\n{flujo}\nendstream",
    ]
    pdf, posiciones = "%PDF-1.4\n", []
    for i, obj in enumerate(objetos, 1):
        posiciones.append(len(pdf))
        pdf += f"{i} 0 obj\n{obj}\nendobj\n"
    xref = len(pdf)
    pdf += f"xref\n0 {len(objetos) + 1}\n0000000000 65535 f \n"
    for p in posiciones:
        pdf += f"{p:010d} 00000 n \n"
    pdf += (f"trailer\n<< /Size {len(objetos) + 1} /Root 1 0 R >>\n"
            f"startxref\n{xref}\n%%EOF")
    return pdf.encode()


def responder(pregunta):
    """La respuesta según el formato, con las formas que valida el backend."""
    tipo = pregunta["tipo"]
    opciones = [o["id"] for o in (pregunta.get("opciones") or [])]
    if tipo in ("PC",):
        return {"opcionId": opciones[0]}
    if tipo in ("V", "ABIERTA"):
        return {"texto": texto_para(pregunta["enunciado"]), "segundos": 240}
    if tipo == "EF-4":
        return {"detalle": {"mas": opciones[0], "menos": opciones[-1]}}
    if tipo == "SJT-R":
        return {"detalle": {"calificaciones": {
            str(o): (i % 5) + 1 for i, o in enumerate(opciones)}}}
    if tipo == "SEC":
        return {"detalle": {"orden": opciones}}
    if tipo in ("INV", "DE"):
        return {"detalle": {"marcadas": opciones[:2]}}
    if tipo == "CD":
        campos = pregunta.get("campos") or []
        return {"detalle": {"campos": {
            str(c["orden"]): f"Respuesta al campo {c['orden']}: lo llevaba en una "
                             f"hoja de control diaria." for c in campos}}}
    # Un formato que no conocemos: mejor pararse aquí que entregar a medias.
    raise SystemExit(f"Formato sin respuesta programada: {tipo}")


def main():
    print(f"1. Cuenta nueva: {CORREO}")
    pedir("/cuentas", {"nombre": "Siembra", "apellidos": f"QA {MARCA}",
                       "correo": CORREO, "contrasena": "siembra-qa-2026",
                       "aceptaProceso": True, "aceptaFuturosContactos": False})
    token = pedir("/auth/login", {"correo": CORREO,
                                  "contrasena": "siembra-qa-2026"})["token"]

    vacantes = pedir("/vacantes")
    con_evaluacion = [v for v in vacantes if v.get("aplicaEvaluacion", True)]
    vacante = con_evaluacion[0]
    # «requisitosObjetivos», no «requisitos»: los segundos son el texto libre de la
    # ficha. Confirmar cero deja la postulación en NO_CONTINUA al instante.
    requisitos = [r["id"] for r in (pedir(f"/vacantes/{vacante['id']}")
                                    .get("requisitosObjetivos") or [])]
    print(f"2. Postulando a «{vacante['titulo']}» "
          f"({len(requisitos)} requisitos confirmados)")
    codigo = postular_multipart(token, vacante["id"], requisitos)
    print(f"   postulación {codigo}")

    evaluacion = pedir(f"/evaluacion/{codigo}/inicio", cuerpo={}, token=token)
    preguntas = evaluacion["preguntas"]
    print(f"3. Evaluación iniciada: {len(preguntas)} preguntas")

    formatos = {}
    for p in preguntas:
        pedir(f"/evaluacion/{codigo}/respuestas/{p['id']}", responder(p),
              token=token, metodo="PUT")
        formatos[p["tipo"]] = formatos.get(p["tipo"], 0) + 1
    print(f"4. Respondidas todas: {formatos}")

    entrega = pedir(f"/evaluacion/{codigo}/entrega", cuerpo={}, token=token)
    print(f"5. Entregada. La calificación quedó encolada: {entrega}")
    print("\nAhora la IA tarda decenas de segundos por respuesta abierta.")
    print("Comprueba con:  docker exec renaser-postgres psql -U postgres -d "
          "renaser_db -c 'select count(*) from nota_respuesta;'")
    print(f"Postulación sembrada: {codigo}")


if __name__ == "__main__":
    try:
        main()
    except urllib.error.HTTPError as e:
        print(f"HTTP {e.code} en {e.url}\n{e.read().decode()[:600]}")
        raise SystemExit(1)
