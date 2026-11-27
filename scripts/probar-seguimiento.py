#!/usr/bin/env python3
"""Mide el acierto del seguimiento de pendientes con la IA real.

Spec seguimiento-de-pendientes-entre-etapas (verificación, D-4): el harness prueba el
mecanismo con un modelo simulado; esto mide el criterio de la IA de verdad. No es una puerta
del harness: lo corre una persona cuando quiere, contra el backend LOCAL y su base local de
diagnóstico, nunca contra producción.

Uso rápido:

    python3 scripts/probar-seguimiento.py --ayuda
"""

import argparse
import json
import os
import sys
import textwrap
import time
import urllib.error
import urllib.parse
import urllib.request
import uuid

AQUI = os.path.dirname(os.path.abspath(__file__))
RAIZ = os.path.dirname(AQUI)
CASOS = os.path.join(AQUI, "probar-seguimiento-casos.json")
CLAVE_CANDIDATO = "seguimiento-qa-2026"
HOSTS_LOCALES = ("localhost", "127.0.0.1", "::1")

AYUDA = """\
probar-seguimiento.py · el acierto del seguimiento de pendientes con la IA real

Qué hace
  1. Crea en el backend LOCAL una vacante con evaluación (preguntas propias abiertas) y prueba
     del puesto (escrita en el editor, con un entregable).
  2. Siembra un candidato por caso de probar-seguimiento-casos.json: postula con su CV,
     responde la evaluación, la entrega, pasa a la prueba, la responde y la entrega (el caso del
     entregable ilegible sube un PDF sin texto).
  3. Deja que la plataforma los califique por el camino normal, espera a que lleguen los
     seguimientos y lee los informes de la evaluación y de la prueba por la API.
  4. Imprime una tabla por duda: caso, etapa, estado esperado, estado que dio la IA, cita y
     explicación, y al final cuántos coinciden.

Qué necesita
  - El backend local arrancado CON la calificación por IA encendida y la clave de DeepSeek:
      docker compose up -d
      ./mvnw spring-boot:test-run -Dspring-boot.run.profiles=local
    La clave va en application-secrets.yaml (deepseek > api-key) o en DEEPSEEK_API_KEY.
  - El dev-login encendido (lo enciende el perfil local).

Cómo se corre
  python3 scripts/probar-seguimiento.py [--api URL] [--uid ID] [--casos RUTA] [--esperar SEG]

    --api      la API local (por defecto http://localhost:8081/api/v1). Solo localhost.
    --uid      el id de RENASER OS del dev-login (por defecto dev-equipo)
    --casos    el archivo de casos (por defecto el que está junto a este guion)
    --esperar  segundos que espera a la IA en cada etapa (por defecto 900)

Coste: unas doce llamadas del seguimiento por pasada, más las de calificar el CV, la
evaluación y la prueba de los seis candidatos. Si la IA falla casos, se ajusta su instrucción
desde el panel (Agentes · Seguimiento de pendientes) y se vuelve a correr, sin desplegar.
"""


class Falta(Exception):
    """Algo que impide seguir, dicho en una línea."""


# ============================================================ la API

class Api:
    def __init__(self, base):
        self.base = base.rstrip("/")

    def pedir(self, ruta, metodo="GET", cuerpo=None, token=None, multipart=None, espera=60):
        datos = None
        cabeceras = {}
        if multipart is not None:
            datos, tipo = multipart
            cabeceras["Content-Type"] = tipo
        elif cuerpo is not None:
            datos = json.dumps(cuerpo).encode("utf-8")
            cabeceras["Content-Type"] = "application/json"
        if token:
            cabeceras["Authorization"] = f"Bearer {token}"
        peticion = urllib.request.Request(self.base + ruta, data=datos, method=metodo, headers=cabeceras)
        try:
            with urllib.request.urlopen(peticion, timeout=espera) as r:
                return r.status, leer_json(r.read())
        except urllib.error.HTTPError as e:
            return e.code, leer_json(e.read())

    def exigir(self, ruta, metodo="GET", cuerpo=None, token=None, multipart=None):
        estado, respuesta = self.pedir(ruta, metodo, cuerpo, token, multipart)
        if estado < 200 or estado > 299:
            raise Falta(f"{metodo} {ruta} contestó {estado}: {json.dumps(respuesta, ensure_ascii=False)[:300]}")
        return respuesta


def leer_json(contenido):
    if not contenido:
        return None
    try:
        return json.loads(contenido)
    except ValueError:
        return contenido.decode("utf-8", "replace")


def formulario(campos, archivo=None):
    """Un multipart/form-data: campos de texto y, si hay, un archivo (nombre_campo, nombre, tipo, bytes)."""
    frontera = uuid.uuid4().hex
    partes = []
    for nombre, valor in campos:
        partes.append(f"--{frontera}\r\nContent-Disposition: form-data; name=\"{nombre}\"\r\n\r\n"
                      f"{valor}\r\n".encode("utf-8"))
    if archivo:
        campo, nombre, tipo, contenido = archivo
        partes.append(f"--{frontera}\r\nContent-Disposition: form-data; name=\"{campo}\"; "
                      f"filename=\"{nombre}\"\r\nContent-Type: {tipo}\r\n\r\n".encode("utf-8")
                      + contenido + b"\r\n")
    partes.append(f"--{frontera}--\r\n".encode("utf-8"))
    return b"".join(partes), f"multipart/form-data; boundary={frontera}"


def pdf(texto):
    """Un PDF mínimo y válido con ese texto (vacío: un PDF del que no sale nada)."""
    lineas = []
    for parrafo in texto.split("\n"):
        lineas.extend(textwrap.wrap(parrafo, 90) or [""])
    escapado = [l.replace("\\", "\\\\").replace("(", "\\(").replace(")", "\\)") for l in lineas if texto]
    flujo = ("BT /F1 11 Tf 40 780 Td 14 TL " + "".join(f"({l}) Tj T* " for l in escapado) + "ET") if escapado else ""
    flujo_bytes = flujo.encode("latin-1", "replace")
    objetos = [
        b"<< /Type /Catalog /Pages 2 0 R >>",
        b"<< /Type /Pages /Kids [3 0 R] /Count 1 >>",
        b"<< /Type /Page /Parent 2 0 R /MediaBox [0 0 612 792] "
        b"/Resources << /Font << /F1 4 0 R >> >> /Contents 5 0 R >>",
        b"<< /Type /Font /Subtype /Type1 /BaseFont /Helvetica /Encoding /WinAnsiEncoding >>",
        f"<< /Length {len(flujo_bytes)} >>\nstream\n".encode("ascii") + flujo_bytes + b"\nendstream",
    ]
    salida = bytearray(b"%PDF-1.4\n")
    posiciones = []
    for i, objeto in enumerate(objetos, 1):
        posiciones.append(len(salida))
        salida += f"{i} 0 obj\n".encode("ascii") + objeto + b"\nendobj\n"
    xref = len(salida)
    salida += f"xref\n0 {len(objetos) + 1}\n0000000000 65535 f \n".encode("ascii")
    for p in posiciones:
        salida += f"{p:010d} 00000 n \n".encode("ascii")
    salida += (f"trailer\n<< /Size {len(objetos) + 1} /Root 1 0 R >>\nstartxref\n{xref}\n%%EOF").encode("ascii")
    return bytes(salida)


# ============================================================ lo que se comprueba antes

def hay_clave():
    if os.environ.get("DEEPSEEK_API_KEY", "").strip():
        return True
    secretos = os.path.join(RAIZ, "application-secrets.yaml")
    if not os.path.exists(secretos):
        return False
    en_deepseek = None
    with open(secretos, encoding="utf-8") as archivo:
        for linea in archivo:
            sin_comentario = linea.split("#", 1)[0].rstrip()
            if not sin_comentario.strip():
                continue
            sangria = len(sin_comentario) - len(sin_comentario.lstrip())
            clave = sin_comentario.strip()
            if en_deepseek is not None and sangria <= en_deepseek:
                en_deepseek = None
            if clave == "deepseek:":
                en_deepseek = sangria
            elif en_deepseek is not None and clave.startswith("api-key:"):
                valor = clave.split(":", 1)[1].strip().strip("\"'")
                if valor and not valor.startswith("${"):
                    return True
    return False


def comprobar(api_url):
    host = urllib.parse.urlparse(api_url).hostname or ""
    if host not in HOSTS_LOCALES:
        raise Falta(f"Solo corre contra un backend local (localhost), y «{host or api_url}» no lo es.")
    if not hay_clave():
        raise Falta("Falta la clave de la IA: pon DEEPSEEK_API_KEY o la de deepseek en application-secrets.yaml.")
    try:
        with urllib.request.urlopen(api_url.rstrip("/") + "/portal/vacantes", timeout=5):
            pass
    except urllib.error.HTTPError:
        pass  # contesta: está arrancado
    except (urllib.error.URLError, OSError, ValueError):
        raise Falta(f"No hay backend local en {api_url}: arráncalo con "
                    "«./mvnw spring-boot:test-run -Dspring-boot.run.profiles=local».") from None


# ============================================================ la siembra

def crear_vacante(api, token, conf, marca):
    puestos = api.exigir("/panel/puestos", token=token)
    areas = api.exigir("/panel/areas", token=token)
    puesto = next((p for p in puestos if p.get("nivelPuestoCodigo") == "EJECUCION"), None)
    area = next((a for a in areas if a.get("esActiva")), None)
    if not puesto or not area:
        raise Falta("La base local no tiene un puesto de nivel EJECUCION y un área activa: siémbrala antes.")
    responsable = api.exigir("/panel/usuarios", token=token)[0]["id"]
    motivo = f"probar-seguimiento {marca}"
    solicitud = api.exigir("/panel/solicitudes", "POST", {
        "areaId": area["id"], "puestoId": puesto["id"], "urgencia": "NORMAL",
        "resultadoPrincipal": "Cubrir las tiendas a tiempo", "motivo": motivo,
        "consecuenciaNoContratar": "Las tiendas siguen sin personal.",
        "analisisCapacidad": "El área ya trabaja con horas extra.",
        "resultadosEsperados": [
            {"descripcion": "Puestos cubiertos a tiempo", "indicador": "Puestos cubiertos por mes"},
            {"descripcion": "Planilla al día", "indicador": "Cierres sin descuadre"},
            {"descripcion": "Rotación baja", "indicador": "Salidas en 90 días"}]}, token)["id"]
    api.exigir(f"/panel/solicitudes/{solicitud}/aprobacion", "POST", {"motivo": motivo}, token)
    vacante = api.exigir("/panel/vacantes", "POST", {
        "solicitudTalentoId": solicitud, "titulo": f"{conf['titulo']} {marca}",
        "descripcion": "Llevas la selección y la planilla de tres tiendas.",
        "tipoCierre": "PERMANENTE", "responsableUsuarioId": responsable}, token)["id"]

    propias = f"/panel/vacantes/{vacante}/preguntas-propias"
    preguntas = conf["evaluacion"]
    for i, p in enumerate(preguntas):
        editor = api.exigir(f"{propias}/criterios", "POST", {"nombre": p["criterio"], "queEvalua": p["queDebeTener"]}, token)
        criterio = next(c["id"] for c in editor["borrador"]["criterios"] if c["nombre"] == p["criterio"])
        puntos = 100 // len(preguntas) + (100 % len(preguntas) if i == 0 else 0)
        api.exigir(f"{propias}/preguntas", "POST", {"tipo": "ABIERTA", "enunciado": p["enunciado"], "puntos": puntos,
                                                   "queDebeTener": p["queDebeTener"], "criterioId": criterio}, token)
    api.exigir(f"{propias}/publicacion", "POST", token=token)

    prueba = conf["prueba"]
    editor_prueba = f"/panel/vacantes/{vacante}/prueba-propia"
    editor = api.exigir(f"{editor_prueba}/criterios", "POST", {
        "nombre": prueba["criterio"], "queEvalua": "Que el plan use las cifras del caso.", "puntos": 100,
        "calificador": "IA"}, token)
    criterio = next(c["id"] for c in editor["borrador"]["criterios"] if c["nombre"] == prueba["criterio"])
    for enunciado in prueba["preguntas"]:
        api.exigir(f"{editor_prueba}/preguntas", "POST", {"tipo": "ABIERTA", "enunciado": enunciado,
                                                         "queDebeTener": "Cifras del caso.", "criterioId": criterio}, token)
    api.exigir(f"{editor_prueba}/entregables", "POST", {
        "nombre": prueba["entregable"], "formato": "ARCHIVO", "obligatorio": False,
        "preguntaId": None, "todaLaPrueba": True, "cubre": []}, token)
    api.exigir(f"{editor_prueba}/borrador", "PUT", {"enunciado": prueba["caso"], "modalidad": "CRONOMETRADA",
                                                   "duracionMinutos": 120}, token)
    cierre = time.strftime("%Y-%m-%dT%H:%M:%SZ", time.gmtime(time.time() + 20 * 24 * 3600))
    api.exigir(f"{editor_prueba}/fecha-limite", "PUT", {"cierraEn": cierre, "motivo": None}, token)
    api.exigir(f"{editor_prueba}/publicacion", "POST", token=token)
    api.exigir(f"/panel/vacantes/{vacante}/publicacion", "POST", token=token)
    api.exigir(f"/panel/vacantes/{vacante}/calificacion-automatica", "POST", {"activa": True}, token)
    return vacante


def candidato(api, vacante, caso, marca, numero):
    correo = f"seguimiento.{marca}.{numero}@example.com"
    api.exigir("/portal/cuentas", "POST", {
        "nombre": "Caso", "apellidos": f"{numero} {marca}", "correo": correo,
        "telefono": f"+519{marca}{numero:02d}", "contrasena": CLAVE_CANDIDATO, "ciudadUbigeo": "1501",
        "aceptaPlataforma": True, "aceptaFuturosContactos": False})
    token = api.exigir("/portal/auth/login", "POST", {"correo": correo, "contrasena": CLAVE_CANDIDATO})["token"]
    requisitos = [r["id"] for r in (api.exigir(f"/portal/vacantes/{vacante}").get("requisitosObjetivos") or [])]
    campos = [("vacanteId", vacante), ("resultadoOrgulloso", "Cubrí los puestos de temporada a tiempo."),
              ("aceptaTratamiento", "true")] + [("requisitosConfirmados", r) for r in requisitos]
    codigo = api.exigir("/portal/postulaciones", "POST", token=token, multipart=formulario(
        campos, ("cv", "cv.pdf", "application/pdf", pdf(caso["cv"]))))["codigo"]

    evaluacion = api.exigir(f"/portal/evaluacion/{codigo}/inicio", "POST", {}, token)
    abiertas = [p for p in evaluacion["preguntas"] if p.get("tipo") == "ABIERTA"]
    if len(abiertas) != len(evaluacion["preguntas"]):
        raise Falta("La evaluación de la vacante trae preguntas que no son abiertas: revisa los casos.")
    for i, p in enumerate(abiertas):
        texto = caso["evaluacion"][min(i, len(caso["evaluacion"]) - 1)]
        api.exigir(f"/portal/evaluacion/{codigo}/respuestas/{p['id']}", "PUT", {"texto": texto, "segundos": 120}, token)
    api.exigir(f"/portal/evaluacion/{codigo}/entrega", "POST", {}, token)
    return {"caso": caso, "codigo": codigo, "token": token, "apellidos": f"{numero} {marca}"}


def ids_del_visor(api, token, vacante, sembrados):
    visor = api.exigir(f"/panel/vacantes/{vacante}/informes", token=token)
    for s in sembrados:
        fila = next((p for p in visor["postulantes"] if p["candidato"].endswith(s["apellidos"])), None)
        if not fila:
            raise Falta(f"El visor no trae a «{s['caso']['nombre']}»: ¿la vacante es de otra empresa?")
        s["id"] = fila["postulacionId"]


def rendir_la_prueba(api, equipo, s, espera_hasta):
    inicio = None
    while inicio is None:
        estado, respuesta = api.pedir(f"/portal/prueba/{s['codigo']}/inicio", "POST", {}, s["token"])
        if 200 <= estado <= 299:
            inicio = respuesta
            break
        api.pedir(f"/panel/postulaciones/{s['id']}/confirmacion-avance", "POST",
                  {"motivo": "probar-seguimiento: pasa a la prueba"}, equipo)
        if time.time() > espera_hasta:
            raise Falta(f"«{s['caso']['nombre']}» no llegó a la prueba: {json.dumps(respuesta, ensure_ascii=False)[:200]}")
        time.sleep(5)
    textos = s["caso"]["prueba"]
    for i, p in enumerate(inicio["preguntas"]):
        api.exigir(f"/portal/prueba/{s['codigo']}/respuestas/{p['id']}", "PUT",
                   {"texto": textos[min(i, len(textos) - 1)]}, s["token"])
    if s["caso"].get("entregableIlegible") and inicio.get("entregables"):
        entregable = inicio["entregables"][0]["id"]
        api.exigir(f"/portal/prueba/{s['codigo']}/entregables/{entregable}/archivo", "POST", token=s["token"],
                   multipart=formulario([], ("archivo", "plan.pdf", "application/pdf", pdf(""))))
    api.exigir(f"/portal/prueba/{s['codigo']}/entrega", "POST", {}, s["token"])


# ============================================================ la espera y la tabla

def bloque(informe, codigo):
    return next((b for b in informe.get("bloques", []) if b.get("codigo") == codigo), {"puntos": []})


def con_seguimiento(informe, etapa):
    if informe.get("textosEnCamino"):
        return False
    if etapa == "evaluacion":
        return all(p.get("etiqueta") != "Por contrastar" for p in bloque(informe, "PIDIO_EL_CV")["puntos"])
    del_cv = [p for p in bloque(informe, "PENDIENTE")["puntos"] if p.get("grupo") == "Del CV"]
    return all(p.get("etiqueta") not in ("Declarada", "Sin información") for p in del_cv)


def esperar(api, token, sembrados, etapa, segundos):
    hasta = time.time() + segundos
    pendientes = list(sembrados)
    while pendientes and time.time() < hasta:
        for s in list(pendientes):
            estado, informe = api.pedir(f"/panel/postulaciones/{s['id']}/informes/{etapa}", token=token)
            if estado == 200 and isinstance(informe, dict) and con_seguimiento(informe, etapa):
                s[etapa] = informe
                pendientes.remove(s)
        if pendientes:
            time.sleep(10)
    for s in pendientes:
        estado, informe = api.pedir(f"/panel/postulaciones/{s['id']}/informes/{etapa}", token=token)
        s[etapa] = informe if estado == 200 and isinstance(informe, dict) else {}
        print(f"  ⚠ «{s['caso']['nombre']}»: el seguimiento de la {etapa} no llegó en {segundos} s; "
              "se lee lo que haya. ¿Está encendida la calificación por IA?")


def buscar(puntos, palabra):
    palabra = palabra.casefold()
    return next((p for p in puntos if palabra in (p.get("texto") or "").casefold()), None)


def obtenido(s, esperado):
    palabra = esperado["duda"]
    evaluacion = s.get("evaluacion") or {}
    en_la_evaluacion = buscar(bloque(evaluacion, "PIDIO_EL_CV")["puntos"], palabra)
    if esperado["etapa"] == "EVALUACION":
        return (en_la_evaluacion.get("etiqueta"), en_la_evaluacion.get("detalle")) if en_la_evaluacion \
            else ("no salió como duda", None)
    prueba = s.get("prueba") or {}
    aclarado = buscar(bloque(prueba, "ACLARO_LA_PRUEBA")["puntos"], palabra)
    if aclarado:
        return "Resuelto", aclarado.get("detalle")
    pendiente = buscar(bloque(prueba, "PENDIENTE")["puntos"], palabra)
    if pendiente:
        return pendiente.get("etiqueta"), pendiente.get("detalle")
    if en_la_evaluacion and en_la_evaluacion.get("etiqueta") == "Resuelto":
        return "Resuelto en la evaluación", en_la_evaluacion.get("detalle")
    return "no salió como duda", None


def tabla(sembrados):
    filas = []
    for s in sembrados:
        for esperado in s["caso"].get("esperado", []):
            estado, detalle = obtenido(s, esperado)
            filas.append((s["caso"]["nombre"], esperado["etapa"], esperado["duda"], esperado["estado"],
                          estado or "—", detalle or "", estado == esperado["estado"]))
    print()
    print(f"{'Caso':<28} {'Etapa':<11} {'Duda':<14} {'Esperado':<16} {'Dio la IA':<26} Cita y explicación")
    print("-" * 140)
    for caso, etapa, duda, esperado, estado, detalle, ok in filas:
        marca = "✓" if ok else "✗"
        print(f"{caso[:28]:<28} {etapa:<11} {duda[:14]:<14} {esperado:<16} {marca} {estado[:24]:<24} {detalle[:120]}")
    coinciden = sum(1 for f in filas if f[-1])
    print("-" * 140)
    print(f"Coinciden {coinciden} de {len(filas)}.")


# ============================================================ el recorrido

def argumentos(argv):
    lector = argparse.ArgumentParser(add_help=False)
    lector.add_argument("--api", default="http://localhost:8081/api/v1")
    lector.add_argument("--uid", default="dev-equipo")
    lector.add_argument("--casos", default=CASOS)
    lector.add_argument("--esperar", type=int, default=900)
    lector.add_argument("--ayuda", "-h", "--help", action="store_true")
    return lector.parse_args(argv)


def principal(argv):
    args = argumentos(argv)
    if args.ayuda:
        print(AYUDA)
        return 0
    comprobar(args.api)
    try:
        with open(args.casos, encoding="utf-8") as archivo:
            conf = json.load(archivo)
    except (OSError, ValueError) as e:
        raise Falta(f"No se pudo leer el archivo de casos {args.casos}: {e}") from None

    api = Api(args.api)
    marca = time.strftime("%H%M%S")
    print(f"probar-seguimiento {marca}: gasta llamadas reales a la IA del backend local.")
    equipo = api.exigir("/panel/auth/dev-login", "POST", {"usuarioRenaserOsId": args.uid})["token"]
    vacante = crear_vacante(api, equipo, conf["vacante"], marca)
    print(f"1. Vacante {vacante} creada y publicada.")
    sembrados = [candidato(api, vacante, caso, marca, i) for i, caso in enumerate(conf["casos"], 1)]
    ids_del_visor(api, equipo, vacante, sembrados)
    print(f"2. {len(sembrados)} candidatos entregaron la evaluación: "
          + ", ".join(f"{s['caso']['nombre']} (postulación {s['id']})" for s in sembrados))
    print("3. Esperando la calificación y el seguimiento de la evaluación…")
    esperar(api, equipo, sembrados, "evaluacion", args.esperar)
    hasta = time.time() + args.esperar
    for s in sembrados:
        rendir_la_prueba(api, equipo, s, hasta)
    print("4. Rindieron la prueba. Esperando su calificación y su seguimiento…")
    esperar(api, equipo, sembrados, "prueba", args.esperar)
    tabla(sembrados)
    print(f"\nVacante de la pasada: {vacante}. Los candidatos se llaman «Caso N {marca}».")
    return 0


if __name__ == "__main__":
    try:
        sys.exit(principal(sys.argv[1:]))
    except Falta as falta:
        print(falta)
        sys.exit(1)
    except KeyboardInterrupt:
        print("Interrumpido.")
        sys.exit(1)
    except (urllib.error.URLError, OSError) as e:
        print(f"Se perdió la conexión con el backend local: {e}")
        sys.exit(1)
