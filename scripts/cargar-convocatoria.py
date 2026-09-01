#!/usr/bin/env python3
"""
Carga la convocatoria de Especialista en Gestión del Talento y una carpeta de currículums.

Hace tres cosas, en este orden:

  1. Deja la convocatoria montada y publicada: el puesto, la Solicitud de Talento
     aprobada, la vacante con el texto real del anuncio y su requisito indispensable.
  2. Crea una cuenta de candidato por cada archivo de la carpeta y postula con él,
     llamando a los mismos endpoints públicos que usará el portal.
  3. Pide la criba: que la IA lea cada currículum y arme el retrato con solo eso.

Después imprime la tanda ordenada. Con --esperar se queda mirando hasta que la IA
termina con todos, que es lo que hace falta para ver el resultado de una corrida.

Todo pasa por la API, nunca por SQL: así las postulaciones salen con sus transiciones,
sus correos registrados y su auditoría, igual que si hubieran entrado por el portal.

**Los currículums salen de una carpeta local, no de Drive.** Descarga la carpeta de
Drive a `cv-convocatoria/` (o pasa la tuya con --carpeta) antes de lanzarlo.

De cada archivo salen el nombre y el correo del candidato. Se espera «Nombre Apellido.pdf»;
lo que haya entre guiones, guiones bajos o puntos se toma como nombre y apellidos.

Uso:
    python scripts/cargar-convocatoria.py --uid TU_UID --esperar
    python scripts/cargar-convocatoria.py --uid TU_UID --solo-ranking
"""

import argparse
import json
import re
import sys
import time
import unicodedata
from pathlib import Path

import requests

# --------------------------------------------------------------- las convocatorias

# Ya no viven aquí: están en scripts/convocatorias.json, una por clave. Se sacaron del
# código en cuanto hubo una segunda, porque el texto del anuncio es lo que la IA lee como
# «qué busca este puesto» y quien lo escribe no tiene por qué tocar Python para cambiarlo.
ARCHIVO_CONVOCATORIAS = Path(__file__).with_name("convocatorias.json")


def cargar_convocatorias():
    with ARCHIVO_CONVOCATORIAS.open(encoding="utf-8") as f:
        datos = json.load(f)
    return {k: v for k, v in datos.items() if not k.startswith("_")}


# **La vacante se monta SIN requisitos indispensables, y es a propósito.**
#
# Un requisito indispensable es lo único que descarta solo a alguien: si quien postula no
# lo confirma, el sistema lo cierra al momento y la IA no llega a leer su currículum. Eso
# funciona cuando hay una persona delante contestando; aquí no la hay.
#
# Marcarlo por ellos sería peor: quedaría escrito que el candidato confirmó algo que nadie
# le preguntó.
#
# Lo que el puesto exige sigue en el texto de la vacante, que es lo que la IA lee.
# Simplemente no es una puerta que se cierre sola.

# El portal exige contar un resultado del que uno se siente orgulloso, y quien llega por
# esta vía no lo contestó nunca. Se dice así, y no con un texto inventado: el agente lo
# lee, y darle una frase de relleno sería puntuar a alguien por algo que no escribió.
SIN_RESULTADO = ("No respondió esta pregunta: el currículum entró por carga directa de la "
                 "convocatoria, no por el portal.")

CONTRASENA = "Convocatoria2026!"

# El almacén comprueba la extensión Y el tipo declarado: mandar octet-stream lo rechaza.
TIPOS = {
    ".pdf": "application/pdf",
    ".docx": "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
}
EXTENSIONES = set(TIPOS)

NOMBRE_PLANTILLA_PRUEBA = "Prueba de talento · convocatoria"


# ------------------------------------------------------------------------ la API

class Api:
    def __init__(self, base):
        self.base = base.rstrip("/")
        self.token = None

    def _cab(self):
        return {"Authorization": f"Bearer {self.token}"} if self.token else {}

    def pide(self, metodo, ruta, **kw):
        r = requests.request(metodo, f"{self.base}{ruta}", headers=self._cab(), timeout=60, **kw)
        if not r.ok:
            try:
                detalle = r.json().get("detail", r.text[:300])
            except Exception:
                detalle = r.text[:300]
            raise RuntimeError(f"{metodo} {ruta} → {r.status_code}: {detalle}")
        if r.status_code == 204 or not r.content:
            return None
        try:
            return r.json()
        except Exception:
            return r

    get = lambda self, ruta: self.pide("GET", ruta)
    post = lambda self, ruta, datos=None: self.pide("POST", ruta, json=datos)


def _salida_utf8():
    """La consola de Windows viene en cp1252 y se cae al imprimir «» o cualquier acento."""
    for flujo in (sys.stdout, sys.stderr):
        try:
            flujo.reconfigure(encoding="utf-8", errors="replace")
        except (AttributeError, ValueError):
            pass


def paso(texto):
    print(f"  {texto}", flush=True)


# ------------------------------------------------------- de archivo a candidato

def sin_tildes(texto):
    return "".join(c for c in unicodedata.normalize("NFD", texto)
                   if unicodedata.category(c) != "Mn")


# Lo que aparece en el nombre de un archivo y no es parte del nombre de nadie.
RUIDO = {"cv", "curriculum", "currículum", "curriculumvitae", "vitae", "hoja", "de", "vida",
         "resume", "resumen", "actualizado", "final", "copia", "documento", "doc", "pdf",
         "postulacion", "postulación", "renaser", "talento"}


def palabras_de(nombre_archivo):
    """Las palabras que parecen nombre propio, en orden.

    Separa también los pegados en mayúscula —«JolausAndyMeza» son tres palabras, no una—
    y tira lo que es ruido: la palabra «CV», un año suelto, «copia», «final».
    """
    # El «CV» pegado al principio, sin separador: «CVALEXISPACHECO», «CVAndreaMuñoz».
    # Se quita antes de nada porque no hay forma de separarlo después: en mayúsculas
    # queda soldado al nombre y sale un candidato llamado «Cvalexispacheco».
    crudo = re.sub(r"^\s*(cv|curriculum|currículum)\s*", " ", nombre_archivo,
                   flags=re.IGNORECASE)
    crudo = re.sub(r"[_\-.]+", " ", crudo)
    # «JolausAndyMeza» → «Jolaus Andy Meza». Sin esto el nombre entero sale como uno solo.
    crudo = re.sub(r"(?<=[a-záéíóúñ])(?=[A-ZÁÉÍÓÚÑ])", " ", crudo)
    palabras = []
    for palabra in crudo.split():
        limpia = palabra.strip()
        if not limpia or limpia.isdigit():
            continue
        if sin_tildes(limpia).lower() in RUIDO:
            continue
        palabras.append(limpia)
    return palabras


def candidato_de(archivo: Path):
    """Saca nombre, apellidos y correo del nombre del archivo.

    No se lee el PDF para esto a propósito: el nombre no está siempre en el mismo sitio
    y equivocarse aquí significa mezclar el currículum de una persona con el de otra.
    El nombre del archivo lo controla quien descarga la carpeta, y se puede corregir.

    Si del archivo no sale nada aprovechable, el candidato entra con el nombre del
    archivo tal cual. Vale más una fila fea que perder un currículum.
    """
    partes = palabras_de(archivo.stem)
    if not partes:
        partes = ["Sin", "nombre", archivo.stem[:20] or "vacio"]
    nombre = partes[0].capitalize()
    apellidos = " ".join(p.capitalize() for p in partes[1:]) or "(sin apellido)"

    slug = re.sub(r"[^a-z0-9]+", ".", sin_tildes(" ".join(partes)).lower()).strip(".")
    return nombre, apellidos, f"{slug}@cv-convocatoria.local"

# ------------------------------------------------------ montar la convocatoria

def crear_plantilla_prueba(api, puesto_id):
    """Una prueba del puesto mínima, porque publicar una vacante la exige (RF-73).

    Aquí no se usa: la criba se queda en el currículum. Existe solo para que la vacante
    se pueda publicar, y por eso es corta. La prueba real la tiene que escribir Renaser.

    Reutiliza la versión ya publicada si el script se lanzó antes.
    """
    ya = next((p for p in api.get("/panel/plantillas-prueba")
               if p["nombre"] == NOMBRE_PLANTILLA_PRUEBA), None)
    plantilla = ya or api.post("/panel/plantillas-prueba", {"nombre": NOMBRE_PLANTILLA_PRUEBA})

    version = api.post(f"/panel/plantillas-prueba/{plantilla['id']}/versiones", {
        "enunciado":
            "Tienes veinte currículums para una vacante y dos días. Di en qué orden los "
            "revisarías, con qué criterio descartas y qué le preguntarías a los tres "
            "primeros antes de invitarlos.",
        "materiales": "Los veinte currículums se entregan al empezar.",
        "herramientasPermitidas": "Cualquiera, siempre que digas cuál usaste.",
        "modalidad": "CRONOMETRADA",
        "duracionMinutos": 90,
        "minutoCambioMin": 30,
        "minutoCambioMax": 50,
        "minutosExtra": 10,
    })
    vid = version["id"]

    api.post(f"/panel/plantillas-prueba/versiones/{vid}/variantes",
             {"texto": "Cinco de los veinte retiran su candidatura. Rehaz el orden."})
    api.post(f"/panel/plantillas-prueba/versiones/{vid}/variantes",
             {"texto": "El plazo se recorta a un día. ¿Qué dejas fuera?"})

    api.post(f"/panel/plantillas-prueba/versiones/{vid}/entregables", {
        "nombre": "El orden y su criterio",
        "detalle": "Un documento con el orden, el criterio de descarte y las preguntas.",
        "formato": "CUALQUIERA",
        "esObligatorio": True,
    })

    for codigo, nombre, descripcion, puntos in [
        ("CRITERIO", "Criterio de descarte", "¿Se entiende por qué descarta y no es un capricho?", 40),
        ("EVIDENCIA", "Uso de la evidencia", "¿Distingue lo demostrado de lo declarado?", 30),
        ("PREGUNTAS", "Calidad de las preguntas", "¿Sirven para comprobar algo o son de relleno?", 20),
        ("CLARIDAD", "Claridad", "¿Se entiende sin tener que preguntarle?", 10),
    ]:
        api.post(f"/panel/plantillas-prueba/versiones/{vid}/rubrica", {
            "codigo": codigo, "nombre": nombre, "descripcion": descripcion,
            "puntos": puntos, "metodoVerificacion": "PERSONA",
        })

    existentes = {p["codigo"]: p["id"] for p in api.get("/panel/plantillas-prueba/preguntas")}
    universales = [
        ("U01", "¿Qué hiciste primero y por qué ese y no otro?"),
        ("U02", "¿Qué dejaste fuera a propósito, y qué pasa si nadie lo atiende?"),
        ("U03", "¿Qué parte te costó más y cómo la resolviste?"),
        ("U04", "¿Cómo supiste que ibas bien mientras lo hacías?"),
        ("U05", "¿Qué supuesto diste por bueno sin poder comprobarlo?"),
        ("U06", "¿Qué harías distinto si tuvieras el doble de tiempo?"),
        ("U07", "¿Qué le preguntarías a quien te encargó esto, si pudieras?"),
        ("U08", "¿Qué parte de tu entrega no defenderías delante de un cliente?"),
    ]
    especificas = [
        ("T01", "¿Cómo distingues un logro real de uno inflado en un currículum?"),
        ("T02", "¿Qué haces cuando el mejor candidato no encaja con la cultura?"),
        ("T03", "¿Cómo mides si una contratación salió bien, seis meses después?"),
    ]
    for codigo, enunciado in universales + especificas:
        if codigo in existentes:
            pid = existentes[codigo]
        else:
            cuerpo = {"codigo": codigo, "enunciado": enunciado,
                      "tipo": "UNIVERSAL" if codigo.startswith("U") else "ESPECIFICA"}
            if codigo.startswith("T"):
                cuerpo["puestoId"] = puesto_id
            pid = api.post("/panel/plantillas-prueba/preguntas", cuerpo)["id"]
        api.post(f"/panel/plantillas-prueba/versiones/{vid}/preguntas", {"preguntaPruebaId": pid})

    api.post(f"/panel/plantillas-prueba/versiones/{vid}/publicacion")
    return vid


def montar_convocatoria(api, yo, c):
    """Deja la vacante publicada y devuelve su id. Se puede volver a lanzar sin duplicar."""
    titulo = c["vacante"]["titulo"]
    ya = next((v for v in api.get("/panel/vacantes")
               if v["titulo"] == titulo and v["estado"] == "PUBLICADA"), None)
    if ya:
        paso(f"La vacante ya estaba publicada (id {ya['id']}): se reutiliza")
        activos = [r for r in api.get(f"/panel/vacantes/{ya['id']}/requisitos") if r["esActivo"]]
        if activos:
            paso(f"⚠ Arrastra {len(activos)} requisito(s) indispensable(s). Todo lo que se "
                 f"cargue se cerrará solo sin que la IA lo lea:")
            for r in activos:
                paso(f"    · {r['descripcion']}")
            paso("  Quítalos desde el panel, o con: "
                 f"DELETE /panel/vacantes/{ya['id']}/requisitos/<id>")
        return ya["id"]

    areas = {a["nombre"]: a["id"] for a in api.get("/panel/areas")}
    area_id = areas.get(c["area"]) or api.post("/panel/areas", {"nombre": c["area"]})["id"]

    puestos = {p["codigo"]: p["id"] for p in api.get("/panel/puestos")}
    puesto = c["puesto"]
    puesto_id = puestos.get(puesto["codigo"]) or api.post("/panel/puestos", puesto)["id"]
    paso(f"Puesto «{puesto['nombre']}» ({puesto['nivelPuestoCodigo']}, familia "
         f"{puesto['familiaCodigo']}) listo")

    sol = c["solicitud"]
    solicitud = api.post("/panel/solicitudes", {
        "areaId": area_id,
        "urgencia": sol["urgencia"],
        "nivelPuestoCodigo": puesto["nivelPuestoCodigo"],
        "familiaCodigo": puesto["familiaCodigo"],
        "resultadoPrincipal": sol["resultadoPrincipal"],
        "motivo": sol["motivo"],
        "consecuenciaNoContratar": sol["consecuenciaNoContratar"],
        "analisisCapacidad": sol["analisisCapacidad"],
        "responsableUsuarioId": yo,
        "resultadosEsperados": sol["resultadosEsperados"],
    })
    api.post(f"/panel/solicitudes/{solicitud['id']}/aprobacion",
             {"motivo": "Aprobada para poder cargar la tanda de currículums de esta convocatoria"})
    paso("Solicitud de Talento creada y aprobada")

    plantillas_eval = {p["nivelPuestoCodigo"]: p["id"]
                       for p in api.get("/panel/plantillas-evaluacion")
                       if p["estado"] == "PUBLICADA"}
    nivel = puesto["nivelPuestoCodigo"]
    if nivel not in plantillas_eval:
        raise RuntimeError(f"No hay plantilla de evaluación publicada para {nivel}. "
                           "¿Se aplicó la migración V14?")

    version_prueba = crear_plantilla_prueba(api, puesto_id)
    paso(f"Plantilla de prueba lista (versión {version_prueba}); publicar la vacante la exige")

    cuerpo = dict(c["vacante"])
    cuerpo.update({"solicitudTalentoId": solicitud["id"], "puestoId": puesto_id,
                   "responsableUsuarioId": yo})
    vacante = api.post("/panel/vacantes", cuerpo)
    api.post(f"/panel/vacantes/{vacante['id']}/plantilla-evaluacion",
             {"plantillaEvaluacionId": plantillas_eval[nivel]})
    api.post(f"/panel/vacantes/{vacante['id']}/plantilla-prueba",
             {"versionPlantillaPruebaId": version_prueba})
    api.post(f"/panel/vacantes/{vacante['id']}/publicacion")
    paso(f"Vacante publicada (id {vacante['id']}) sin requisitos que descarten solos")
    return vacante["id"]


# ------------------------------------------------------------ cargar currículums

def cargar_cv(api, token_equipo, vacante_id, archivo: Path):
    """Crea la cuenta, postula con el archivo y devuelve el id de la postulación."""
    nombre, apellidos, correo = candidato_de(archivo)

    api.token = None  # el portal es público
    try:
        api.post("/portal/cuentas", {
            "nombre": nombre, "apellidos": apellidos, "correo": correo,
            "contrasena": CONTRASENA, "aceptaProceso": True,
            "aceptaFuturosContactos": False,
            # La ciudad es obligatoria al crear cuenta y no se puede sacar del nombre del
            # archivo, que es lo unico que hay aqui. Se pone «fuera del Peru» a proposito y
            # no una provincia inventada: quien carga una convocatoria no sabe donde vive
            # esta gente, y adivinarselo llenaria el filtro del ranking de datos falsos.
            # Cuando entren al portal la pueden corregir; un dato ausente se ve, uno
            # inventado no.
            "ciudadUbigeo": "EXT",
        })
    except RuntimeError as fallo:
        # Que la cuenta ya exista es lo normal al recargar una convocatoria, y se sigue con
        # ella. Cualquier OTRO fallo se propaga: antes se tragaba todo, y cuando el portal
        # empezo a exigir un campo nuevo el guion no reventaba aqui sino despues, en el
        # login, con un «correo o contrasena incorrectos» que no decia nada del problema.
        if "ya existe una cuenta" not in str(fallo).lower():
            raise

    api.token = api.post("/portal/auth/login",
                         {"correo": correo, "contrasena": CONTRASENA})["token"]

    with archivo.open("rb") as f:
        api.pide("POST", "/portal/postulaciones", files={
            "vacanteId": (None, str(vacante_id)),
            "resultadoOrgulloso": (None, SIN_RESULTADO),
            "cv": (archivo.name, f, TIPOS[archivo.suffix.lower()]),
        })

    api.token = token_equipo
    return nombre, apellidos, correo


def postulacion_de(api, vacante_id, correo):
    """El id que el panel le dio a esa postulación. El portal solo devuelve el uuid."""
    for fila in api.get(f"/panel/vacantes/{vacante_id}/ranking")["filas"]:
        if fila["correo"] == correo:
            return fila["postulacionId"]
    return None


# --------------------------------------------------------------------- el ranking

GRUPOS = {
    "ALTA": "Alta prioridad",
    "POTENCIAL_CON_RIESGO": "Alto potencial con riesgo",
    "NO_PRIORIZADO": "No priorizado",
    "INCOMPATIBLE": "Incompatibilidad objetiva",
}


def numero(valor):
    return "  —  " if valor is None else f"{float(valor):5.1f}"


def imprimir_ranking(api, vacante_id):
    r = api.get(f"/panel/vacantes/{vacante_id}/ranking")
    print(f"\n{r['vacante']} · {r['puesto']} ({r['nivelPuesto']})")
    print(f"{r['total']} candidatos · {r['calificados']} calificados · "
          f"{r['enCurso']} en curso · {r['fallidos']} fallidos\n")
    print(f"{'#':>3}  {'Candidato':28}  {'Nota':>5}  {'CV':>5}  {'Adec':>5}  {'Pot':>5}  "
          f"{'Conf':>5}  Grupo")
    print("-" * 105)
    for f in r["filas"]:
        riesgo = f"  ⚠ {f['riesgosCriticos']} riesgo(s)" if f["riesgosCriticos"] else ""
        print(f"{f['puesto']:>3}  {(f['candidato'] or '')[:28]:28}  {numero(f['notaEtapa'])}  "
              f"{numero(f['notaCurriculum'])}  {numero(f['adecuacion'])}  "
              f"{numero(f['potencial'])}  {numero(f['confianzaEvidencia'])}  "
              f"{GRUPOS.get(f['grupoPrioridad'], f['estadoCalificacion'])}{riesgo}")
    print()
    for f in r["filas"][:3]:
        if f["resumen"]:
            print(f"  {f['puesto']}. {f['candidato']}: {f['resumen']}\n")
    return r


def esperar(api, vacante_id, minutos):
    """Sondea hasta que no quede nadie en curso, o hasta agotar el tiempo."""
    limite = time.time() + minutos * 60
    while time.time() < limite:
        r = api.get(f"/panel/vacantes/{vacante_id}/ranking")
        if r["enCurso"] == 0:
            return r
        print(f"  …{r['enCurso']} en curso, {r['calificados']} listos, "
              f"{r['fallidos']} fallidos", flush=True)
        time.sleep(10)
    print("  Se agotó la espera: quedan trabajos en curso. El ranking sale igual, a medias.")
    return None


# -------------------------------------------------------------------------- main

def main():
    _salida_utf8()
    p = argparse.ArgumentParser(description=__doc__,
                                formatter_class=argparse.RawDescriptionHelpFormatter)
    p.add_argument("--api", default="http://localhost:8080/api/v1")
    p.add_argument("--uid", default="dev-talento",
                   help="El id de RENASER OS con que entra el equipo")
    p.add_argument("--convocatoria", default="talento",
                   help="Cuál de scripts/convocatorias.json se carga")
    p.add_argument("--carpeta", default="cv-convocatoria",
                   help="Dónde están los currículums descargados de Drive")
    p.add_argument("--solo-convocatoria", action="store_true",
                   help="Montar la vacante y parar. Para cuando los currículums llegan después")
    p.add_argument("--esperar", action="store_true",
                   help="Quedarse mirando hasta que la IA termine con todos")
    p.add_argument("--minutos", type=int, default=20, help="Tope de espera")
    p.add_argument("--solo-ranking", action="store_true",
                   help="No cargar nada: solo imprimir la tanda de la vacante")
    p.add_argument("--sin-criba", action="store_true",
                   help="Cargar los currículums pero no llamar a la IA. Sirve para "
                        "comprobar la carga sin gastar llamadas al modelo")
    args = p.parse_args()

    convocatorias = cargar_convocatorias()
    if args.convocatoria not in convocatorias:
        print(f"\nNo existe la convocatoria «{args.convocatoria}».")
        print(f"Las que hay: {', '.join(sorted(convocatorias))}")
        print(f"Se añaden en {ARCHIVO_CONVOCATORIAS.name}, sin tocar código.")
        sys.exit(1)
    c = convocatorias[args.convocatoria]

    api = Api(args.api)

    print("\n1 · El equipo entra")
    sesion = api.post("/panel/auth/dev-login", {"usuarioRenaserOsId": args.uid})
    api.token = sesion["token"]
    token_equipo = api.token
    yo = sesion["usuarioId"]
    paso(f"{args.uid} entra con todos los roles")

    print(f"\n2 · La convocatoria «{c['vacante']['titulo']}»")
    vacante_id = montar_convocatoria(api, yo, c)

    if args.solo_convocatoria:
        paso("--solo-convocatoria: la vacante queda lista y esperando currículums")
        print(f"\nVacante {vacante_id}. Cuando tengas los CV:")
        print(f"  python scripts/cargar-convocatoria.py --convocatoria "
              f"{args.convocatoria} --uid {args.uid} --carpeta <carpeta> --esperar\n")
        return

    if args.solo_ranking:
        imprimir_ranking(api, vacante_id)
        return

    print("\n3 · Los currículums")
    carpeta = Path(args.carpeta)
    if not carpeta.is_dir():
        print(f"\nNo existe la carpeta «{carpeta}».")
        print("Descarga ahí la carpeta de Drive, o pásame la tuya con --carpeta.")
        print("Con dos convocatorias en marcha, conviene una carpeta por cada una.")
        print("Con dos convocatorias en marcha, conviene una carpeta por cada una.")
        sys.exit(1)

    archivos = sorted(f for f in carpeta.iterdir()
                      if f.is_file() and f.suffix.lower() in EXTENSIONES)
    if not archivos:
        print(f"\nEn «{carpeta}» no hay ningún .pdf ni .docx.")
        print("El .doc antiguo de Word no se puede leer: hace falta el PDF.")
        sys.exit(1)
    paso(f"{len(archivos)} archivos en «{carpeta}»")

    cargados, repetidos = [], 0
    for archivo in archivos:
        try:
            nombre, apellidos, correo = cargar_cv(api, token_equipo, vacante_id, archivo)
            cargados.append((archivo.name, correo))
            paso(f"{archivo.name} → {nombre} {apellidos}")
        except RuntimeError as e:
            api.token = token_equipo
            if "Ya postulaste" in str(e):
                repetidos += 1
                _, _, correo = candidato_de(archivo)
                cargados.append((archivo.name, correo))
                paso(f"{archivo.name} → ya estaba cargado")
            else:
                paso(f"{archivo.name} → NO se pudo cargar: {e}")
    paso(f"{len(cargados)} postulaciones ({repetidos} ya estaban de una carga anterior)")

    if args.sin_criba:
        paso("--sin-criba: no se llama a la IA")
        imprimir_ranking(api, vacante_id)
        return

    print("\n4 · La criba: la IA lee cada currículum")
    pedidas = 0
    for archivo, correo in cargados:
        pid = postulacion_de(api, vacante_id, correo)
        if pid is None:
            paso(f"{archivo} → no se encontró su postulación en el ranking")
            continue
        try:
            api.post(f"/panel/postulaciones/{pid}/criba-cv")
            pedidas += 1
        except RuntimeError as e:
            paso(f"{archivo} → no se pudo encolar: {e}")
    paso(f"{pedidas} cribas en cola. Cada una tarda decenas de segundos")

    if args.esperar:
        print("\n5 · Esperando a la IA")
        esperar(api, vacante_id, args.minutos)

    imprimir_ranking(api, vacante_id)
    print(f"Vacante {vacante_id}. Para volver a mirar sin cargar nada:")
    print(f"  python scripts/cargar-convocatoria.py --uid {args.uid} --solo-ranking\n")


if __name__ == "__main__":
    try:
        main()
    except KeyboardInterrupt:
        sys.exit(130)
    except RuntimeError as e:
        print(f"\nSe cortó: {e}\n", file=sys.stderr)
        sys.exit(1)
