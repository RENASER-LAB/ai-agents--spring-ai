#!/usr/bin/env python3
"""
Siembra datos de prueba en el sistema de selección, como si el portal ya existiera.

El portal del candidato todavía no está construido, así que sin esto la bandeja, la
ficha y el embudo nacen vacíos: justo las tres pantallas que más interesa probar.
Este script hace lo que hará el portal —crear cuentas, postular, subir el currículum—
llamando a los mismos endpoints públicos que usará él.

Todo pasa por la API, nunca por SQL. Así los datos salen con sus transiciones, sus
correos registrados y su auditoría, que es lo que hace que el panel se vea real.

Uso:
    python3 scripts/sembrar-datos-de-prueba.py [--api http://localhost:8080/api/v1] [--uid TU_UID]

Si se pasa --uid y la base está recién creada, ese id queda como primer usuario del
equipo con todos los roles, que es lo que necesita el panel de RENASER OS para entrar.
"""

import argparse
import io
import random
import sys
import time

import requests

# ---------------------------------------------------------------- catálogos

AREAS = ["Tecnología", "Operaciones", "Crecimiento", "Talento"]

# Los códigos salen de las semillas (V9). No se inventan: la base tiene claves
# foráneas contra `familia` y `nivel_puesto`, y un código que no existe revienta.
PUESTOS = [
    ("DEV_WEB", "Desarrollador web", "EJECUCION", "TECNOLOGIA"),
    ("LIDER_OPS", "Líder de operaciones", "SUPERVISION", "OPERACIONES"),
    ("CX_ANALISTA", "Analista de experiencia", "EJECUCION", "CRECIMIENTO"),
]

# (nombre, apellidos, cumple_requisitos, ubigeo)
#
# La ciudad se pide al crear la cuenta y es el codigo de ubigeo de la provincia. Van
# REPARTIDAS y no todas iguales porque el ranking se filtra por ciudad: con diez candidatos
# de Lima, el filtro se ve pero no se puede probar. Hay dos de Lima a proposito —para que
# haya un grupo con mas de uno— y un «EXT», que es quien postula desde fuera del Peru.
CANDIDATOS = [
    ("Camila", "Torres Rivas", True, "1501"),        # Lima — Lima
    ("Diego", "Salazar Núñez", True, "0401"),        # Arequipa — Arequipa
    ("Fernanda", "Quispe Mamani", True, "0801"),     # Cusco — Cusco
    ("Joaquín", "Vargas Ureta", True, "1301"),       # La Libertad — Trujillo
    ("Valeria", "Ríos Castro", True, "EXT"),         # fuera del Peru
    ("Mateo", "Ibáñez Flores", True, "0701"),        # Callao
    ("Lucía", "Chávez Paredes", True, "0402"),       # Arequipa — Camaná
    ("Andrés", "Molina Guzmán", False, "1501"),      # no confirma el requisito: se descarta solo
    ("Renata", "Espinoza León", True, "2101"),       # Puno — Puno
    ("Sebastián", "Cárdenas Rojo", True, "1201"),    # Junín — Huancayo
]

ORGULLOS = [
    "Rediseñé el flujo de citas de una clínica y el ausentismo bajó a la mitad en tres meses.",
    "Migré el sistema de facturación sin cortar el servicio ni un minuto.",
    "Levanté el tablero de indicadores que hoy usa Dirección todas las semanas.",
    "Automaticé el reporte mensual: pasó de dos días de trabajo a diez minutos.",
    "Acompañé a seis personas nuevas en su primer mes y las seis siguen en la empresa.",
]


def cv_falso(nombre):
    """Un PDF mínimo pero válido, para que la subida sea real."""
    texto = f"Curriculum de {nombre}"
    contenido = (
        b"%PDF-1.4\n1 0 obj<</Type/Catalog/Pages 2 0 R>>endobj\n"
        b"2 0 obj<</Type/Pages/Kids[3 0 R]/Count 1>>endobj\n"
        b"3 0 obj<</Type/Page/Parent 2 0 R/MediaBox[0 0 595 842]>>endobj\n"
        b"trailer<</Root 1 0 R>>\n%% " + texto.encode("utf-8") + b"\n%%EOF\n"
    )
    return io.BytesIO(contenido)


class Api:
    def __init__(self, base):
        self.base = base.rstrip("/")
        self.token = None

    def _cab(self):
        return {"Authorization": f"Bearer {self.token}"} if self.token else {}

    def pide(self, metodo, ruta, **kw):
        r = requests.request(metodo, f"{self.base}{ruta}", headers=self._cab(), timeout=30, **kw)
        if not r.ok:
            detalle = ""
            try:
                detalle = r.json().get("detail", r.text[:200])
            except Exception:
                detalle = r.text[:200]
            raise RuntimeError(f"{metodo} {ruta} → {r.status_code}: {detalle}")
        if r.status_code == 204 or not r.content:
            return None
        try:
            return r.json()
        except Exception:
            return r

    get = lambda self, ruta: self.pide("GET", ruta)
    post = lambda self, ruta, datos=None: self.pide("POST", ruta, json=datos)


# La consola de Windows viene en cp1252 y revienta con «», → o cualquier acento al
# imprimir. El script se cae por un print, con toda la siembra ya hecha en el servidor.
# Se fuerza UTF-8 en la salida; si la terminal no lo admite, se sustituye el carácter
# en vez de abortar.
def _salida_utf8():
    for flujo in (sys.stdout, sys.stderr):
        try:
            flujo.reconfigure(encoding="utf-8", errors="replace")
        except (AttributeError, ValueError):
            pass


def paso(texto):
    print(f"  {texto}", flush=True)


NIVEL_DE_PUESTO = {codigo: nivel for codigo, _, nivel, _ in PUESTOS}

NOMBRE_PLANTILLA_PRUEBA = "Prueba de ejecución · demo"

# Las preguntas de defensa: se hacen DESPUÉS de producir, y sirven para distinguir a
# quien hizo el trabajo de quien lo copió. Ninguna migración las siembra. RF-83 exige
# entre 8 y 10 universales y entre 3 y 5 específicas del puesto, y publicar lo verifica.
PREGUNTAS_UNIVERSALES = [
    ("U01", "¿Qué hiciste primero y por qué ese y no otro?"),
    ("U02", "¿Qué dejaste fuera a propósito, y qué pasa si nadie lo atiende?"),
    ("U03", "¿Qué parte te costó más y cómo la resolviste?"),
    ("U04", "¿Cómo supiste que ibas bien mientras lo hacías?"),
    ("U05", "¿Qué supuesto diste por bueno sin poder comprobarlo?"),
    ("U06", "¿Qué harías distinto si tuvieras el doble de tiempo?"),
    ("U07", "¿Qué le preguntarías a quien te encargó esto, si pudieras?"),
    ("U08", "¿Qué parte de tu entrega no defenderías delante de un cliente?"),
]

PREGUNTAS_ESPECIFICAS = [
    ("E01", "¿Cómo repartiste el tiempo entre dejarlo bien hecho y entregar a tiempo?"),
    ("E02", "Si tu criterio de orden choca con lo que pide tu jefe, ¿qué haces?"),
    ("E03", "Explica tu orden a alguien que no sabe nada del tema."),
]


def catalogo_preguntas(api, puesto_id):
    """Devuelve los ids de las once preguntas, creando solo las que falten.

    El código es único en la base, así que volver a crearlas daría 409. Se consulta
    el catálogo primero y se reutiliza lo que ya esté.
    """
    existentes = {p["codigo"]: p["id"] for p in api.get("/panel/plantillas-prueba/preguntas")}
    ids = []
    guion = ([(c, e, "UNIVERSAL", None) for c, e in PREGUNTAS_UNIVERSALES]
             + [(c, e, "ESPECIFICA", puesto_id) for c, e in PREGUNTAS_ESPECIFICAS])
    for codigo, enunciado, tipo, pid in guion:
        if codigo in existentes:
            ids.append(existentes[codigo])
            continue
        cuerpo = {"codigo": codigo, "enunciado": enunciado, "tipo": tipo}
        if pid is not None:
            cuerpo["puestoId"] = pid
        ids.append(api.post("/panel/plantillas-prueba/preguntas", cuerpo)["id"])
    return ids


def crear_plantilla_prueba(api, puesto_id):
    """Arma una plantilla de prueba entera y la publica. Devuelve el id de la versión.

    Es lo único del hito 3 que ninguna migración siembra, y sin ella no se puede
    publicar una vacante. La prueba es corta a propósito: 90 minutos, un solo
    entregable y una rúbrica de cuatro criterios que suman 100. No pretende ser la
    prueba real de Renaser —esa la tiene que escribir Renaser— sino algo válido con
    que probar el sistema de punta a punta.

    Se puede volver a llamar sobre una base ya usada: reutiliza la plantilla si ya
    existe y le añade una versión nueva. Las versiones a medias de intentos
    anteriores se quedan en borrador y no estorban: nadie las puede asignar.
    """
    ya = next((p for p in api.get("/panel/plantillas-prueba")
               if p["nombre"] == NOMBRE_PLANTILLA_PRUEBA), None)
    plantilla = ya or api.post("/panel/plantillas-prueba", {
        "nombre": NOMBRE_PLANTILLA_PRUEBA,
    })

    version = api.post(f"/panel/plantillas-prueba/{plantilla['id']}/versiones", {
        "enunciado":
            "Tienes una lista de tareas atrasadas y un equipo que no se pone de acuerdo "
            "en cuál va primero. Ordénalas, explica con qué criterio lo hiciste y di qué "
            "harías con las que decidas no atender.",
        "materiales": "La lista de tareas se entrega al empezar.",
        "herramientasPermitidas": "Cualquiera, siempre que digas cuál usaste.",
        "modalidad": "CRONOMETRADA",
        "duracionMinutos": 90,
        # El cambio inesperado aparece entre el minuto 30 y el 50, sorteado al
        # empezar para que el segundo candidato no sepa cuándo llega.
        "minutoCambioMin": 30,
        "minutoCambioMax": 50,
        "minutosExtra": 10,
    })
    vid = version["id"]

    # El orden lo pone el servicio, no el cuerpo: la variante solo lleva su texto.
    api.post(f"/panel/plantillas-prueba/versiones/{vid}/variantes", {
        "texto": "Acaba de entrar una tarea urgente del cliente más grande. Reordena.",
    })
    api.post(f"/panel/plantillas-prueba/versiones/{vid}/variantes", {
        "texto": "La persona que iba a hacer la tarea más pesada se reporta enferma.",
    })

    api.post(f"/panel/plantillas-prueba/versiones/{vid}/entregables", {
        "nombre": "La lista ordenada",
        "detalle": "Un documento con el orden, el criterio y lo que dejas fuera.",
        "formato": "CUALQUIERA",
        "esObligatorio": True,
    })

    # Los puntos suman 100: es lo que el cálculo de la nota de etapa da por hecho.
    rubrica = [
        ("CRITERIO", "Criterio de priorización", "¿Se entiende por qué ese orden y no otro?", 40),
        ("DESCARTE", "Qué deja fuera", "¿Dice qué no atiende y asume la consecuencia?", 25),
        ("CLARIDAD", "Claridad", "¿Se entiende sin tener que preguntarle?", 20),
        ("REACCION", "Reacción al cambio", "¿Rehace el orden o defiende el anterior con motivo?", 15),
    ]
    for codigo, nombre, descripcion, puntos in rubrica:
        api.post(f"/panel/plantillas-prueba/versiones/{vid}/rubrica", {
            "codigo": codigo, "nombre": nombre, "descripcion": descripcion,
            # Solo admite SISTEMA, AGENTE o PERSONA. Va PERSONA porque el agente
            # PRUEBA_PUESTO todavía no tiene instrucción escrita: no puede calificar.
            "puntos": puntos, "metodoVerificacion": "PERSONA",
        })

    # Sin las once preguntas enganchadas a ESTA versión, publicar devuelve 400.
    for pregunta_id in catalogo_preguntas(api, puesto_id):
        api.post(f"/panel/plantillas-prueba/versiones/{vid}/preguntas",
                 {"preguntaPruebaId": pregunta_id})

    api.post(f"/panel/plantillas-prueba/versiones/{vid}/publicacion")
    return vid


def sembrar(api, uid_equipo):
    # ---------------------------------------------------------- 1. el equipo
    print("\n1 · El equipo entra")
    sesion = api.post("/panel/auth/dev-login", {"usuarioRenaserOsId": uid_equipo})
    api.token = sesion["token"]
    # La vacante exige un responsable: es quien acaba de entrar
    yo = sesion["usuarioId"]
    paso(f"{uid_equipo} entra con todos los roles (bootstrap de la base vacía)")

    # ---------------------------------------------------------- 2. estructura
    print("\n2 · La estructura de la empresa")
    # Se reutiliza lo que ya exista: así el script se puede volver a lanzar sobre una
    # base con datos, sin tener que borrarla. El código del puesto es único en la
    # base, y el área repetida solo ensuciaría el panel con duplicados.
    areas = {a["nombre"]: a["id"] for a in api.get("/panel/areas")}
    nuevas = 0
    for nombre in AREAS:
        if nombre not in areas:
            areas[nombre] = api.post("/panel/areas", {"nombre": nombre})["id"]
            nuevas += 1
    paso(f"{len(AREAS)} áreas ({nuevas} nuevas, {len(AREAS) - nuevas} ya estaban)")

    puestos = {p["codigo"]: p["id"] for p in api.get("/panel/puestos")}
    nuevos = 0
    for codigo, nombre, nivel, familia in PUESTOS:
        if codigo not in puestos:
            puestos[codigo] = api.post("/panel/puestos", {
                "codigo": codigo, "nombre": nombre,
                "nivelPuestoCodigo": nivel, "familiaCodigo": familia,
            })["id"]
            nuevos += 1
    paso(f"{len(PUESTOS)} puestos ({nuevos} nuevos, {len(PUESTOS) - nuevos} ya estaban)")

    # ------------------------------------------- 2b. lo que exige publicar
    # Una vacante no se publica sin plantilla de evaluación Y de prueba. La de
    # evaluación ya viene sembrada por la migración V14, una por nivel: se busca,
    # no se crea. La de prueba no la siembra nadie, así que se arma aquí.
    print("\n2b · Las plantillas que exige publicar")

    plantillas_eval = {
        p["nivelPuestoCodigo"]: p["id"]
        for p in api.get("/panel/plantillas-evaluacion")
        if p["estado"] == "PUBLICADA"
    }
    if "EJECUCION" not in plantillas_eval:
        raise RuntimeError(
            "No hay plantilla de evaluación publicada para EJECUCION. "
            "¿Se aplicó la migración V14?")
    paso(f"{len(plantillas_eval)} plantillas de evaluación ya publicadas, se reutilizan")

    version_prueba = crear_plantilla_prueba(api, puestos["DEV_WEB"])
    paso(f"1 plantilla de prueba creada y publicada (versión {version_prueba})")

    # ------------------------------------------------- 3. solicitudes de talento
    print("\n3 · Las Solicitudes de Talento")
    guion = [
        ("Tecnología", "DEV_WEB", "aprobar", "NORMAL",
         "Sostener el desarrollo del portal de talento",
         "El equipo actual no llega a los plazos comprometidos",
         "Se retrasa el MVP y con él las inscripciones de setiembre",
         "Se evaluó automatizar parte del trabajo y no alcanza: lo que falta es diseño, no repetición."),
        ("Operaciones", "LIDER_OPS", "aprobar", "PRIORITARIA",
         "Cubrir los proyectos de obra que hoy no tienen responsable",
         "Dos proyectos entran en agosto y no hay quien los lleve",
         "Se posponen los dos proyectos y se pierde el trimestre",
         "Se revisó redistribuir entre los arquitectos actuales: ya están al 95% de su capacidad."),
        ("Crecimiento", "CX_ANALISTA", "aprobar", "NORMAL",
         "Medir de verdad la experiencia del cliente",
         "Hoy nadie mira las encuestas y se responden tarde",
         "Seguimos sin saber por qué se van los clientes",
         "Se evaluó darle esto a Operaciones y no hay horas: se probó dos meses y no se sostuvo."),
        ("Operaciones", "DEV_WEB", "rechazar", "NORMAL",
         "Un segundo analista de soporte",
         "El volumen de tickets subió en junio",
         "Los tiempos de respuesta se alargan",
         "Se evaluó y el pico fue estacional: en julio ya bajó."),
        ("Tecnología", "DEV_WEB", "pendiente", "URGENTE",
         "Alguien que sostenga la infraestructura",
         "Solo una persona conoce el despliegue",
         "Si esa persona falta, nadie puede desplegar",
         "No hay forma de automatizar esto sin alguien que lo diseñe primero."),
    ]

    solicitudes = []
    for area, puesto_cod, destino, urgencia, resultado, motivo, consecuencia, analisis in guion:
        # El puesto es obligatorio desde que la solicitud nace con él, y de el salen el
        # nivel y la familia: mandarlos aparte hacia que el backend los comparase con los
        # del puesto y rechazase la solicitud —«el nivel enviado no coincide»—. Se callan
        # a proposito, no se olvidaron.
        s = api.post("/panel/solicitudes", {
            "areaId": areas[area], "puestoId": puestos[puesto_cod], "urgencia": urgencia,
            "resultadoPrincipal": resultado, "motivo": motivo,
            "consecuenciaNoContratar": consecuencia, "analisisCapacidad": analisis,
            "responsableUsuarioId": yo,
            "resultadosEsperados": [
                {"descripcion": "Entregar lo comprometido en el plazo", "indicador": "sin retrasos en el trimestre"},
                {"descripcion": "Dejar el trabajo documentado", "indicador": "documentación al día"},
                {"descripcion": "Que otra persona pueda continuarlo", "indicador": "al menos un relevo formado"},
            ],
        })
        if destino == "aprobar":
            api.post(f"/panel/solicitudes/{s['id']}/aprobacion", {"motivo": "Justificada: hay presupuesto y el análisis de capacidad es sólido"})
            solicitudes.append((s["id"], puesto_cod, resultado))
        elif destino == "rechazar":
            api.post(f"/panel/solicitudes/{s['id']}/rechazo", {"motivo": "El pico de tickets fue estacional; se revisa en octubre"})
    paso(f"{len(guion)} solicitudes: {len(solicitudes)} aprobadas, 1 rechazada, 1 esperando a Dirección")

    # ---------------------------------------------------------- 4. vacantes
    print("\n4 · Las vacantes")
    vacantes = []
    titulos = ["Desarrollador web", "Líder de operaciones", "Analista de experiencia del cliente"]
    for i, (solicitud_id, puesto_cod, _) in enumerate(solicitudes):
        v = api.post("/panel/vacantes", {
            "solicitudTalentoId": solicitud_id, "puestoId": puestos[puesto_cod],
            "titulo": titulos[i], "descripcion":
                "Trabajo con gente, no solo con herramientas. Buscamos a alguien que deje el trabajo "
                "mejor documentado de lo que lo encontró.",
            "tipoCierre": "PERMANENTE", "responsableUsuarioId": yo,
        })
        req = api.post(f"/panel/vacantes/{v['id']}/requisitos", {
            "descripcion": "Disponibilidad en Arequipa",
            "regla": "Reside en Arequipa o puede trasladarse antes de empezar",
        })
        # Sin estas dos, publicar devuelve 409. Y la evaluación tiene que ser del
        # mismo nivel que el puesto: «Líder de operaciones» es SUPERVISION y los
        # otros dos EJECUCION, así que la plantilla se elige por el nivel de cada uno.
        nivel = NIVEL_DE_PUESTO[puesto_cod]
        if nivel not in plantillas_eval:
            raise RuntimeError(f"No hay plantilla de evaluación publicada para {nivel}")
        api.post(f"/panel/vacantes/{v['id']}/plantilla-evaluacion",
                 {"plantillaEvaluacionId": plantillas_eval[nivel]})
        api.post(f"/panel/vacantes/{v['id']}/plantilla-prueba",
                 {"versionPlantillaPruebaId": version_prueba})
        api.post(f"/panel/vacantes/{v['id']}/publicacion")
        vacantes.append({"id": v["id"], "titulo": titulos[i], "requisito": req["id"]})
    paso(f"{len(vacantes)} vacantes publicadas, cada una con su requisito indispensable")

    # ------------------------------------------- 5. los candidatos (el portal)
    print("\n5 · Los candidatos, como si el portal existiera")
    token_equipo = api.token
    postulaciones = []

    for i, (nombre, apellidos, cumple, ubigeo) in enumerate(CANDIDATOS):
        correo = f"{nombre.lower()}.{apellidos.split()[0].lower()}@ejemplo.pe"
        vacante = vacantes[i % len(vacantes)]

        api.token = None  # el portal es público
        try:
            api.post("/portal/cuentas", {
                "nombre": nombre, "apellidos": apellidos, "correo": correo,
                "contrasena": "Demo12345!", "aceptaProceso": True,
                "ciudadUbigeo": ubigeo,
                "aceptaFuturosContactos": i % 3 != 0,
            })
        except RuntimeError:
            # Ya existe de una siembra anterior: se entra con ella y se postula otra
            # vez. Postular dos veces a la misma vacante sí lo rechaza el backend, y
            # eso es correcto: nadie se postula dos veces al mismo puesto.
            pass
        api.token = api.post("/portal/auth/login", {"correo": correo, "contrasena": "Demo12345!"})["token"]

        campos = {
            "vacanteId": (None, str(vacante["id"])),
            "resultadoOrgulloso": (None, random.choice(ORGULLOS)),
            "portafolio": (None, f"https://{nombre.lower()}.dev"),
            # Aceptar el texto de la empresa de ESTA vacante es condicion para postular, y
            # es distinto del consentimiento de la cuenta: son dos responsables de datos.
            "aceptaTratamiento": (None, "true"),
        }
        # Quien no confirma el requisito queda descartado en el acto: es el único
        # descarte automático del sistema, y conviene que se vea en los datos.
        if cumple:
            campos["requisitosConfirmados"] = (None, str(vacante["requisito"]))
        campos["cv"] = (f"cv-{nombre.lower()}.pdf", cv_falso(nombre), "application/pdf")

        r = requests.post(f"{api.base}/portal/postulaciones",
                          headers={"Authorization": f"Bearer {api.token}"},
                          files=campos, timeout=30)
        if not r.ok:
            raise RuntimeError(f"postular {nombre}: {r.status_code} {r.text[:200]}")
        postulaciones.append({"nombre": nombre, "uuid": r.json()["codigo"],
                              "cumple": cumple, "token": api.token, "vacante": vacante["titulo"]})
        paso(f"{nombre} postuló a «{vacante['titulo']}»" + ("" if cumple else "  → descartado: no cumple el requisito"))

    # ----------------------------------------------- 6. el equipo mueve la cola
    print("\n6 · El equipo mueve la cola")
    api.token = token_equipo
    bandeja = api.get("/panel/bandeja?espera_a=CANDIDATO")
    por_uuid = {f["uuid"]: f["postulacionId"] for f in bandeja}

    # A dónde llevar a cada uno, para que la bandeja tenga trabajo en los cuatro lados
    reparto = [
        ("PERFIL_POR_CONFIRMAR", "Perfil revisado a mano: la experiencia encaja con lo que pide el puesto"),
        ("PERFIL_POR_CONFIRMAR", "Perfil revisado a mano: buen recorrido, falta confirmar disponibilidad"),
        ("PRUEBA_TURNO_CANDIDATO", "Avanza a la prueba del puesto; se le envía el encargo por correo"),
        ("PRUEBA_POR_CONFIRMAR", "Prueba entregada y revisada por el equipo"),
        ("SIMULACION_POR_HABILITAR", "Pasa a simulación: falta abrir las fechas"),
        ("VALIDACION_POR_CONFIRMAR", "Terminó el periodo de validación; espera la lectura del área"),
        ("DECISION_POR_CONFIRMAR", "Todo el recorrido completo; queda la decisión del área"),
    ]

    movidos = 0
    for p, (destino, motivo) in zip([x for x in postulaciones if x["cumple"]], reparto):
        pid = por_uuid.get(p["uuid"])
        if not pid:
            continue
        api.post(f"/panel/postulaciones/{pid}/transiciones",
                 {"estadoDestino": destino, "motivo": motivo, "motivoCierre": None})
        movidos += 1
    paso(f"{movidos} postulaciones repartidas por las etapas")

    # Alguien se retira: es un final distinto del descarte, y el embudo lo distingue
    ultimo = [x for x in postulaciones if x["cumple"]][-1]
    api.token = ultimo["token"]
    api.post(f"/portal/postulaciones/{ultimo['uuid']}/retiro")
    paso(f"{ultimo['nombre']} se retiró del proceso por su cuenta")

    # Y alguien pide que le borren los datos, para que Administración tenga qué enseñar
    api.post("/portal/solicitudes-borrado", {"motivo": "Ya encontré trabajo, prefiero que borren mis datos"})
    paso(f"{ultimo['nombre']} pidió el borrado de sus datos")

    api.token = token_equipo
    return vacantes


def resumen(api):
    print("\n" + "=" * 58)
    print("  Lo que va a ver el panel")
    print("=" * 58)
    for etiqueta, ruta in [
        ("Solicitudes", "/panel/solicitudes"),
        ("Vacantes", "/panel/vacantes"),
        ("Áreas", "/panel/areas"),
    ]:
        print(f"  {etiqueta:<34} {len(api.get(ruta))}")
    for espera in ("TALENTO", "AREA", "CANDIDATO", "SISTEMA"):
        n = len(api.get(f"/panel/bandeja?espera_a={espera}"))
        print(f"  Bandeja · espera a {espera:<15} {n}")
    print(f"  Solicitudes de borrado             {len(api.get('/panel/solicitudes-borrado'))}")
    print("=" * 58)


def main():
    _salida_utf8()
    p = argparse.ArgumentParser(description=__doc__)
    p.add_argument("--api", default="http://localhost:8080/api/v1")
    p.add_argument("--uid", default="dev-equipo",
                   help="El id de RENASER OS que quedará como primer usuario del equipo")
    args = p.parse_args()

    api = Api(args.api)
    try:
        api.get("/portal/vacantes")
    except Exception as e:
        print(f"No se pudo contactar con el backend en {args.api}\n  {e}", file=sys.stderr)
        print("\n¿Está levantado?  ./mvnw spring-boot:run", file=sys.stderr)
        return 1

    inicio = time.time()
    try:
        sembrar(api, args.uid)
    except RuntimeError as e:
        print(f"\nFalló la siembra: {e}", file=sys.stderr)
        return 1

    resumen(api)
    print(f"\nListo en {time.time() - inicio:.1f} s. Todas las contraseñas: Demo12345!")
    return 0


if __name__ == "__main__":
    sys.exit(main())
