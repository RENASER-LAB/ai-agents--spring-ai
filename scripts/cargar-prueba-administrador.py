#!/usr/bin/env python3
"""
Carga el cuestionario técnico de Administrador General como LA prueba de esa vacante,
y apaga el banco de preguntas solo para ella.

Lo pidió Renaser (22/08/2026): para la vacante de Administrador no hay dos etapas de
preguntas. El cuestionario técnico de 20 preguntas vale por el banco y por la prueba
del puesto juntos, lo responde el candidato desde su enlace de acceso, y lo califica
el agente PRUEBA_PUESTO contra una rúbrica donde todos los criterios son de agente.

Hace seis cosas, en este orden, y se puede volver a lanzar sin duplicar:

  1. Crea las 20 preguntas del cuestionario en el catálogo (ADMIN_Q01..ADMIN_Q20).
  2. Crea y publica la plantilla «Cuestionario técnico · Administrador General»:
     sin entregables —las preguntas SON la prueba—, con plazo abierto y rúbrica
     de 7 criterios calificados por la IA que suman 100.
  3. La asigna a la vacante de Administrador.
  4. Crea y publica una versión de pesos donde la prueba vale los 100 puntos de la
     decisión, la asigna SOLO a esa vacante, y vuelve a publicar el reparto estándar
     para que las vacantes nuevas no hereden el especial por accidente.
  5. Escribe el texto del correo de ESTA vacante (PRUEBA_DISPONIBLE_ADMINISTRADOR) y
     se lo asigna solo a ella. El de Arquitecto e Ingeniero Civil no se toca.
  6. Apaga la evaluación del banco en esa vacante: quien postule va directo a la
     bandeja del equipo, que decide a quién invitar a la prueba.

Después de esto, el recorrido de un candidato es:
  postula (o se carga su CV) → el equipo confirma su avance → le llega el correo con
  su enlace de acceso → entra y responde el cuestionario → entrega → el equipo pide
  la calificación con IA → las notas quedan en el panel.

Todo pasa por la API, nunca por SQL: cada paso queda con su auditoría.

Uso:
    python scripts/cargar-prueba-administrador.py --uid TU_UID
    python scripts/cargar-prueba-administrador.py --uid TU_UID --api https://servidor/api/v1
"""

import argparse
import sys

import requests

# --------------------------------------------------------------- el cuestionario

TITULO_VACANTE = "Administrador"
NOMBRE_PLANTILLA = "Cuestionario técnico · Administrador General"
ETIQUETA_PESOS = "La prueba vale todo · Administrador sin banco"

# ---------------------------------------------------------------- el correo
#
# El aviso de la prueba lo escribió Ricardo: la plantilla `PRUEBA_DISPONIBLE`, que la máquina
# de estados manda al entrar en PRUEBA_TURNO_CANDIDATO, con el enunciado en PDF, el plazo
# según la modalidad y el WhatsApp del parámetro `whatsapp_evidencia`. Esa **no se toca**:
# es la que siguen usando Arquitecto e Ingeniero Civil, donde la entrega por WhatsApp es
# deliberada mientras la pantalla de entregables no esté terminada.
#
# Administrador necesita otra cosa —responder en el portal— así que tiene la suya, con código
# propio, y la vacante la elige para sí sola (V31). Antes esto no se podía: una plantilla era
# una por organización y cambiarla se la cambiaba a todas las convocatorias.
PLANTILLA_ADMINISTRADOR = {
    "codigo": "PRUEBA_DISPONIBLE_ADMINISTRADOR",
    "asunto": "Tu prueba del puesto para {{vacante}}",
    "cuerpo": """Hola {{nombre}}:

Pasaste a la prueba del puesto para «{{vacante}}». Enhorabuena.

RESPONDE AQUI, SIN CONTRASENA:
{{enlace}}

{{PLAZO_REAL}} Lo que escribas se guarda solo.

Responde con experiencias reales: cantidades, personas a cargo, volumenes,
herramientas utilizadas y resultados obtenidos.

Si prefieres hacerlo en un documento, o tu prueba pide entregar archivos, manda tu
evidencia por WhatsApp al {{whatsapp}}, con tu nombre completo y el puesto al que
postulas, para saber de quien es.

Si tienes cualquier duda, responde a este correo.

Equipo de Talento — Renaser""",
}

ENUNCIADO = """CUESTIONARIO TÉCNICO — ADMINISTRADOR GENERAL · EMPRESA DE CAMBIO DE DIVISAS

Objetivo: evaluar si el postulante posee experiencia práctica y suficiente para \
administrar una empresa de cambio de divisas con múltiples sedes, personal, manejo de \
caja, control operativo, coordinación administrativa y responsabilidad sobre resultados.

Instrucción: responde utilizando experiencias reales. Cuando corresponda, indica \
cantidades, número de personas, volumen de operaciones, herramientas utilizadas y \
resultados obtenidos."""

# El texto de cada pregunta va tal cual lo mandó el cliente. Cambiarlo cambia lo que
# la IA lee, así que no se resume ni se reescribe.
PREGUNTAS = [
    ("ADMIN_Q01", "¿Cuántos años de experiencia tienes administrando empresas, negocios "
     "o unidades operativas?\n\nIndica:\n• Empresa.\n• Cargo.\n• Tiempo.\n"
     "• Número de trabajadores.\n• Número de sedes.\n• Principales responsabilidades."),
    ("ADMIN_Q02", "¿Cuál ha sido la operación más grande que has administrado "
     "directamente?\n\nIndica aproximadamente:\n• Número de trabajadores.\n"
     "• Número de sedes.\n• Volumen de ventas o dinero administrado.\n"
     "• Número de clientes u operaciones.\n• Responsabilidades que estaban bajo tu cargo."),
    ("ADMIN_Q03", "Describe cómo era un día normal en tu último puesto administrativo.\n\n"
     "Queremos conocer qué actividades realizabas personalmente y cuáles delegabas."),
    ("ADMIN_Q04", "¿Has tenido responsabilidad directa sobre cajas, efectivo, bancos o "
     "dinero de una empresa?\n\nExplica exactamente qué controlabas y cuál era el monto "
     "o volumen aproximado que manejabas."),
    ("ADMIN_Q05", "Explícanos paso a paso cómo realizabas un cierre y cuadre de caja en "
     "tu anterior trabajo.\n\nIndica:\n• Qué información revisabas.\n"
     "• Qué documentos utilizabas.\n• Cómo identificabas diferencias.\n"
     "• Qué hacías cuando existía un descuadre."),
    ("ADMIN_Q06", "Si al finalizar el día una sede presenta un faltante de dinero, "
     "¿cuál sería tu procedimiento para determinar qué ocurrió?\n\n"
     "Describe el proceso exacto que seguirías."),
    ("ADMIN_Q07", "¿Qué controles implementarías para reducir el riesgo de errores, "
     "pérdidas o irregularidades en las cajas de tres sedes?\n\nExplica los controles "
     "que implementarías diariamente, semanalmente y mensualmente."),
    ("ADMIN_Q08", "¿Has trabajado anteriormente en una casa de cambio, empresa "
     "financiera, empresa de compra y venta de divisas o negocio con manejo intensivo "
     "de efectivo?\n\nSi tu respuesta es sí, indica:\n• Empresa.\n• Cargo.\n• Tiempo.\n"
     "• Funciones.\n• Volumen aproximado de operaciones.\n\nSi tu respuesta es no, "
     "explica qué experiencia consideras transferible para asumir esta responsabilidad."),
    ("ADMIN_Q09", "Explícanos qué aspectos consideras críticos para controlar "
     "correctamente una operación de compra y venta de dólares y soles."),
    ("ADMIN_Q10", "Si detectas que una sede está realizando operaciones pero su margen "
     "o rentabilidad está disminuyendo, ¿qué información revisarías primero para "
     "identificar la causa?"),
    ("ADMIN_Q11", "¿Has administrado anteriormente más de una sede, sucursal, tienda o "
     "unidad de negocio simultáneamente?\n\nIndica:\n• Número de sedes.\n"
     "• Distancia entre ellas.\n• Número de trabajadores.\n• Cómo realizabas el control."),
    ("ADMIN_Q12", "Si tuvieras tres sedes y no pudieras estar físicamente en todas "
     "ellas todos los días, ¿cómo organizarías el sistema de supervisión?\n\nExplica:\n"
     "• Qué indicadores revisarías.\n• Con qué frecuencia.\n• Qué información "
     "exigirías.\n• Qué reuniones realizarías.\n• Cómo detectarías problemas sin "
     "estar presente."),
    ("ADMIN_Q13", "¿Cuántas personas has tenido directamente bajo tu responsabilidad?\n\n"
     "Indica el número máximo de personas y qué tipo de puestos administrabas."),
    ("ADMIN_Q14", "¿Has participado directamente en procesos de selección de personal?\n\n"
     "Describe el proceso que utilizabas desde que identificabas la necesidad de "
     "contratar hasta la incorporación de la persona."),
    ("ADMIN_Q15", "Si uno de los trabajadores de una sede tiene buenos resultados "
     "comerciales, pero constantemente presenta errores de caja o incumple "
     "procedimientos, ¿cómo actuarías?\n\nExplica qué evaluarías y qué medidas tomarías."),
    ("ADMIN_Q16", "¿Has tenido que despedir, sancionar o reemplazar a una persona por "
     "bajo rendimiento o incumplimiento?\n\nDescribe la situación y cómo la gestionaste."),
    ("ADMIN_Q17", "¿Qué experiencia tienes coordinando con contadores y revisando "
     "información contable?\n\nIndica qué documentos, reportes o indicadores revisabas "
     "personalmente."),
    ("ADMIN_Q18", "Si el contador te presenta un resultado financiero que no coincide "
     "con la información operativa de las sedes, ¿cómo investigarías la diferencia?\n\n"
     "Explica paso a paso qué información cruzarías."),
    ("ADMIN_Q19", "¿Has tenido responsabilidad directa sobre objetivos de ventas, "
     "rentabilidad o crecimiento de una empresa?\n\nIndica:\n• Qué objetivo tenías.\n"
     "• Cómo se medía.\n• Qué resultado conseguiste.\n• Qué acciones implementaste "
     "para conseguirlo."),
    ("ADMIN_Q20", "Si asumieras la administración de Corijón y te dijéramos que "
     "queremos aumentar significativamente la rentabilidad de las tres sedes durante "
     "los próximos 12 meses, ¿qué información necesitarías conocer durante tus "
     "primeros 30 días antes de proponer un plan de crecimiento?\n\nExplica qué "
     "revisarías en:\n• Ventas.\n• Márgenes.\n• Tipo de cambio.\n• Clientes.\n"
     "• Personal.\n• Competencia.\n• Gastos.\n• Operaciones.\n• Sedes.\n• Procesos."),
]

# La rúbrica sigue las secciones del cuestionario y suma 100. Todos los criterios son
# de AGENTE: aquí todo es respuesta escrita, que es justo lo que la IA sí puede leer.
# Una persona puede corregir cualquier nota después, justificando el cambio.
RUBRICA = [
    ("EXPERIENCIA", "Experiencia y magnitud de lo administrado", 15,
     "Preguntas 1 a 3. ¿Cuántos años, cuántas personas, cuántas sedes y qué volumen "
     "administró de verdad? Vale la evidencia concreta (cifras, empresas, cargos), "
     "no la etiqueta del puesto."),
    ("CAJA", "Manejo y control de caja", 20,
     "Preguntas 4 a 7. ¿Domina el cierre y cuadre de caja paso a paso, tiene un "
     "procedimiento serio ante un faltante y propone controles diarios, semanales y "
     "mensuales concretos para tres sedes?"),
    ("DIVISAS", "Conocimiento del negocio de divisas", 15,
     "Preguntas 8 a 10. Experiencia en casas de cambio o manejo intensivo de efectivo "
     "—o experiencia transferible bien argumentada—, qué controla en la compra y venta "
     "de dólares y soles, y cómo diagnostica un margen que cae."),
    ("SEDES", "Supervisión de múltiples sedes", 15,
     "Preguntas 11 y 12. ¿Ha manejado varias sedes a la vez y sabe montar una "
     "supervisión a distancia con indicadores, frecuencia y reuniones definidas?"),
    ("PERSONAL", "Gestión de personal", 15,
     "Preguntas 13 a 16. Personas a cargo, procesos de selección en los que participó, "
     "cómo maneja al comercial bueno que incumple procedimientos y cómo gestionó "
     "despidos o sanciones reales."),
    ("FINANZAS", "Coordinación contable y financiera", 10,
     "Preguntas 17 y 18. Qué revisaba personalmente con el contador y cómo cruzaría "
     "la información operativa con un resultado financiero que no cuadra."),
    ("OBJETIVOS", "Orientación a resultados y plan de crecimiento", 10,
     "Preguntas 19 y 20. Metas que tuvo a su cargo con su resultado medible, y la "
     "calidad del plan de primeros 30 días para las tres sedes de Corijón."),
]


# ------------------------------------------------------------------------ la API

class Api:
    def __init__(self, base):
        self.base = base.rstrip("/")
        self.token = None

    def pide(self, metodo, ruta, **kw):
        cab = {"Authorization": f"Bearer {self.token}"} if self.token else {}
        r = requests.request(metodo, f"{self.base}{ruta}", headers=cab, timeout=60, **kw)
        if not r.ok:
            try:
                detalle = r.json().get("detail", r.text[:300])
            except Exception:
                detalle = r.text[:300]
            raise RuntimeError(f"{metodo} {ruta} → {r.status_code}: {detalle}")
        if r.status_code == 204 or not r.content:
            return None
        return r.json()

    get = lambda self, ruta: self.pide("GET", ruta)
    post = lambda self, ruta, datos=None: self.pide("POST", ruta, json=datos)


def _salida_utf8():
    for flujo in (sys.stdout, sys.stderr):
        try:
            flujo.reconfigure(encoding="utf-8", errors="replace")
        except (AttributeError, ValueError):
            pass


def paso(texto):
    print(f"  {texto}", flush=True)


# ------------------------------------------------------------------- los pasos

def la_vacante(api):
    vacantes = [v for v in api.get("/panel/vacantes")
                if v["titulo"] == TITULO_VACANTE and v["estado"] == "PUBLICADA"]
    if not vacantes:
        raise RuntimeError(
            f"No hay una vacante «{TITULO_VACANTE}» publicada. Se monta con: "
            "python scripts/cargar-convocatoria.py --convocatoria administrador "
            "--solo-convocatoria")
    if len(vacantes) > 1:
        raise RuntimeError(f"Hay {len(vacantes)} vacantes «{TITULO_VACANTE}» publicadas: "
                           "decide a cuál va el cuestionario y cierra la otra")
    return vacantes[0]


def crear_preguntas(api, puesto_id):
    existentes = {p["codigo"]: p["id"] for p in api.get("/panel/plantillas-prueba/preguntas")}
    ids, nuevas = [], 0
    for codigo, enunciado in PREGUNTAS:
        if codigo in existentes:
            ids.append(existentes[codigo])
            continue
        creada = api.post("/panel/plantillas-prueba/preguntas", {
            "codigo": codigo, "enunciado": enunciado,
            "tipo": "ESPECIFICA", "puestoId": puesto_id,
        })
        ids.append(creada["id"])
        nuevas += 1
    paso(f"{len(ids)} preguntas listas ({nuevas} creadas, {len(ids) - nuevas} ya estaban)")
    return ids


def crear_y_publicar_prueba(api, vacante, puesto_id, plazo_dias):
    plantillas = {p["nombre"]: p for p in api.get("/panel/plantillas-prueba")}
    plantilla = plantillas.get(NOMBRE_PLANTILLA)
    if plantilla is None:
        plantilla = {"id": api.post("/panel/plantillas-prueba", {
            "nombre": NOMBRE_PLANTILLA, "puestoId": puesto_id})["id"]}
        paso(f"Plantilla «{NOMBRE_PLANTILLA}» creada")

    # Si la vacante ya apunta a una versión publicada de ESTA plantilla, no se duplica:
    # una versión nueva se crea solo cuando cambie el cuestionario o la rúbrica.
    version_asignada = vacante.get("versionPlantillaPruebaId")
    if version_asignada:
        actual = api.get(f"/panel/plantillas-prueba/versiones/{version_asignada}")
        if actual["version"]["plantillaPruebaId"] == plantilla["id"]:
            paso(f"La vacante ya tiene el cuestionario (versión {version_asignada}): se reutiliza")
            return version_asignada

    preguntas = crear_preguntas(api, puesto_id)

    vid = api.post(f"/panel/plantillas-prueba/{plantilla['id']}/versiones", {
        "enunciado": ENUNCIADO,
        "modalidad": "PLAZO_ABIERTO",
        "plazoDias": plazo_dias,
    })["id"]

    for pid in preguntas:
        api.post(f"/panel/plantillas-prueba/versiones/{vid}/preguntas",
                 {"preguntaPruebaId": pid})

    for codigo, nombre, puntos, descripcion in RUBRICA:
        api.post(f"/panel/plantillas-prueba/versiones/{vid}/rubrica", {
            "codigo": codigo, "nombre": nombre, "descripcion": descripcion,
            "puntos": puntos, "metodoVerificacion": "AGENTE",
        })

    # Sin entregables a propósito: las 20 preguntas SON la prueba, y así la cuota de
    # universales y específicas no rige (es un cuestionario).
    api.post(f"/panel/plantillas-prueba/versiones/{vid}/publicacion")
    api.post(f"/panel/vacantes/{vacante['id']}/plantilla-prueba",
             {"versionPlantillaPruebaId": vid})
    paso(f"Cuestionario publicado (versión {vid}) y asignado a la vacante {vacante['id']}")
    return vid


def copiar_pesos(api, desde_id, hacia_id, con_etapas, con_componentes):
    if con_etapas:
        for e in api.get(f"/panel/pesos/versiones/{desde_id}/etapas"):
            api.post(f"/panel/pesos/versiones/{hacia_id}/etapas",
                     {"etapaCodigo": e["etapaCodigo"], "peso": e["peso"]})
    if con_componentes:
        for c in api.get(f"/panel/pesos/versiones/{desde_id}/componentes"):
            api.post(f"/panel/pesos/versiones/{hacia_id}/componentes",
                     {"componente": c["componente"], "peso": c["peso"]})
    for d in api.get(f"/panel/pesos/versiones/{desde_id}/dimensiones"):
        api.post(f"/panel/pesos/versiones/{hacia_id}/dimensiones",
                 {"nivelPuestoCodigo": d["nivelPuestoCodigo"],
                  "dimensionCodigo": d["dimensionCodigo"], "peso": d["peso"]})
    for c in api.get(f"/panel/pesos/versiones/{desde_id}/criterios"):
        api.post(f"/panel/pesos/versiones/{hacia_id}/criterios",
                 {"nivelPuestoCodigo": c["nivelPuestoCodigo"],
                  "criterioId": c["criterioId"], "peso": c["peso"]})


def asignar_pesos(api, vacante):
    """La prueba vale los 100 puntos de la decisión, solo para esta vacante.

    La versión especial lleva una sola etapa (PRUEBA_PUESTO al 100) y ningún componente
    de perfil, pero copia las dimensiones y los criterios del reparto vigente: la criba
    de currículums los sigue usando para ordenar la tanda.

    Después vuelve a publicar el reparto estándar, porque una vacante nueva toma la
    última versión publicada: sin esa re-publicación, la siguiente convocatoria
    heredaría el reparto especial sin que nadie lo pidiera.
    """
    versiones = api.get("/panel/pesos/versiones")
    publicadas = [v for v in versiones if v["estado"] == "PUBLICADA"]
    if not publicadas:
        raise RuntimeError("No hay ninguna versión de pesos publicada: falta la semilla")
    publicadas.sort(key=lambda v: v["publicadaEn"])

    especial = next((v for v in publicadas if v["etiqueta"] == ETIQUETA_PESOS), None)
    estandar = next((v for v in reversed(publicadas) if v["etiqueta"] != ETIQUETA_PESOS), None)
    if especial is None:
        fuente = publicadas[-1]
        vid = api.post("/panel/pesos/versiones", {"etiqueta": ETIQUETA_PESOS})["id"]
        api.post(f"/panel/pesos/versiones/{vid}/etapas",
                 {"etapaCodigo": "PRUEBA_PUESTO", "peso": 100})
        copiar_pesos(api, fuente["id"], vid, con_etapas=False, con_componentes=False)
        api.post(f"/panel/pesos/versiones/{vid}/publicacion")
        especial = {"id": vid, "etiqueta": ETIQUETA_PESOS}
        paso(f"Versión de pesos «{ETIQUETA_PESOS}» publicada (id {vid})")
    else:
        paso(f"Versión de pesos «{ETIQUETA_PESOS}» ya existía (id {especial['id']}): se reutiliza")

    api.post(f"/panel/vacantes/{vacante['id']}/version-pesos",
             {"versionPesosId": especial["id"]})
    paso(f"La vacante {vacante['id']} ahora decide con «{ETIQUETA_PESOS}»")

    # ¿La última publicada quedó siendo la especial? Entonces re-publicar la estándar.
    versiones = [v for v in api.get("/panel/pesos/versiones") if v["estado"] == "PUBLICADA"]
    versiones.sort(key=lambda v: v["publicadaEn"])
    if versiones[-1]["etiqueta"] == ETIQUETA_PESOS:
        if estandar is None:
            raise RuntimeError("No hay un reparto estándar que re-publicar: revisa las "
                               "versiones de pesos en el panel")
        etiqueta = f"{estandar['etiqueta']} · sigue vigente por defecto"
        vid = api.post("/panel/pesos/versiones", {"etiqueta": etiqueta})["id"]
        copiar_pesos(api, estandar["id"], vid, con_etapas=True, con_componentes=True)
        api.post(f"/panel/pesos/versiones/{vid}/publicacion")
        paso(f"Reparto estándar re-publicado como «{etiqueta}»: las vacantes nuevas "
             "siguen tomando el reparto de siempre")


MESES = ["enero", "febrero", "marzo", "abril", "mayo", "junio", "julio", "agosto",
         "septiembre", "octubre", "noviembre", "diciembre"]
DIAS = ["lunes", "martes", "miercoles", "jueves", "viernes", "sabado", "domingo"]


def texto_del_plazo(cierra_en):
    """«Tienes hasta el lunes 24 de agosto a las 11:59 p. m.», en hora de Lima.

    Se calcula aqui y se escribe DENTRO del texto en vez de usar la variable {{plazo}} del
    sistema: esa dice los dias que trae la plantilla —siete—, no la fecha en que cierra la
    vacante. Con las dos cosas puestas, el correo decia «tienes 7 dias» y el sistema cerraba
    al dia siguiente.
    """
    if not cierra_en:
        return "Tienes {{plazo}} desde este correo."
    from datetime import datetime, timedelta
    utc = datetime.strptime(cierra_en.replace("Z", "+0000"), "%Y-%m-%dT%H:%M:%S%z")
    lima = utc - timedelta(hours=5)          # Lima es UTC-5 todo el año
    hora = lima.strftime("%I:%M %p").lstrip("0").replace("AM", "a. m.").replace("PM", "p. m.")
    return (f"Tienes hasta el {DIAS[lima.weekday()]} {lima.day} de {MESES[lima.month - 1]} "
            f"a las {hora}")


def cargar_plantilla_correo(api, vacante_id, cierra_en):
    """El texto propio de esta vacante, sin tocar el de las demás."""
    plantilla = dict(PLANTILLA_ADMINISTRADOR)
    plantilla["cuerpo"] = plantilla["cuerpo"].replace("{{PLAZO_REAL}}",
                                                      texto_del_plazo(cierra_en))
    activas = [p for p in api.get("/panel/plantillas-correo")
               if p["codigo"] == plantilla["codigo"] and p["esActiva"]]
    if activas and activas[0]["cuerpo"] == plantilla["cuerpo"]:
        paso(f"El texto «{PLANTILLA_ADMINISTRADOR['codigo']}» ya estaba escrito igual")
    else:
        api.post("/panel/plantillas-correo", plantilla)
        paso(f"Texto «{plantilla['codigo']}» "
             f"{'actualizado' if activas else 'creado'}: responde en el portal, con el "
             f"WhatsApp de alternativa")

    ya = [p for p in api.get(f"/panel/vacantes/{vacante_id}/plantillas-correo")
          if p["avisoCodigo"] == "PRUEBA_DISPONIBLE"]
    if ya and ya[0]["plantillaCodigo"] == PLANTILLA_ADMINISTRADOR["codigo"]:
        paso("La vacante ya usaba ese texto: se deja como está")
        return
    api.post(f"/panel/vacantes/{vacante_id}/plantillas-correo", {
        "avisoCodigo": "PRUEBA_DISPONIBLE",
        "plantillaCodigo": plantilla["codigo"],
    })
    paso(f"Solo la vacante {vacante_id} manda ese texto. Arquitecto e Ingeniero Civil "
         f"siguen con el suyo, intacto")


# -------------------------------------------------------------------------- main

def main():
    _salida_utf8()
    p = argparse.ArgumentParser(description=__doc__,
                                formatter_class=argparse.RawDescriptionHelpFormatter)
    p.add_argument("--api", default="http://localhost:8080/api/v1")
    p.add_argument("--uid", default="dev-talento",
                   help="El id de RENASER OS con que entra el equipo")
    p.add_argument("--cierra-en", default=None,
                   help="Cuándo cierra la prueba para TODA la vacante, en UTC "
                        "(p. ej. 2026-08-24T04:59:00Z = domingo 23 a las 11:59 p. m. de Lima). "
                        "Sin esto se cuentan los días de la plantilla desde que cada uno "
                        "empieza. A quien tenga fecha propia no se le mueve")
    p.add_argument("--plazo-dias", type=int, default=7,
                   help="Días para responder desde que se empieza (7). Es el tope de "
                        "seguridad: la fecha de cierre de cada candidato se fija aparte, "
                        "con POST /panel/postulaciones/{id}/prueba/plazo")
    args = p.parse_args()

    api = Api(args.api)

    print("\n1 · El equipo entra")
    sesion = api.post("/panel/auth/dev-login", {"usuarioRenaserOsId": args.uid})
    api.token = sesion["token"]
    paso(f"{args.uid} entra con todos los roles")

    print(f"\n2 · La vacante «{TITULO_VACANTE}»")
    vacante = la_vacante(api)
    paso(f"Vacante {vacante['id']} encontrada")

    print("\n3 · El cuestionario como prueba del puesto")
    crear_y_publicar_prueba(api, vacante, vacante["puestoId"], args.plazo_dias)

    print("\n4 · Los pesos: la prueba vale todo")
    asignar_pesos(api, vacante)

    print("\n5 · El texto del correo con que se invita a la prueba")
    cargar_plantilla_correo(api, vacante['id'], args.cierra_en)

    if args.cierra_en:
        print("\n5b · La fecha en que cierra la prueba, para toda la vacante")
        salida = api.post(f"/panel/vacantes/{vacante['id']}/cierre-prueba", {
            "cierraEn": args.cierra_en,
            "motivo": "Cierre único de la convocatoria de Administrador",
        })
        paso(f"Cierra el {args.cierra_en}. Intentos ya abiertos que se movieron: "
             f"{salida['intentosMovidos']}; con fecha propia (no se tocaron): "
             f"{salida['intentosConPlazoPropio']}")

    print("\n6 · Apagar el banco de preguntas en esta vacante")
    api.post(f"/panel/vacantes/{vacante['id']}/aplicacion-evaluacion", {"aplica": False})
    paso("Quien postule ya no recibe la evaluación del banco: va directo a la "
         "bandeja del equipo")

    print(f"""
Listo. Cómo se opera a partir de aquí:

  · Los candidatos se cargan como siempre (portal o cargar-convocatoria.py) y caen
    en la bandeja «por confirmar» del Perfil Integral, sin cuestionario del banco.
  · La criba de currículums sigue disponible para ordenar la tanda antes de invitar.
    Para los cargados desde una carpeta conviene pedirla primero: además de la nota,
    saca el correo real del CV, que es adonde se manda el enlace de acceso.
  · Confirmar el avance de un candidato
    (POST /panel/postulaciones/{{id}}/confirmacion-avance) crea su intento y el
    backend le manda el correo de la prueba —con su enlace de acceso y la opción de
    WhatsApp— desde la cuenta de la empresa. En lote:
    python scripts/invitar.py --vacante <id> --a prueba --de-verdad
  · Para que todos cierren el MISMO día —y no siete días desde que cada uno entra—:
    POST /panel/postulaciones/{{id}}/prueba/plazo
    {{"venceEn": "2026-08-24T04:59:00Z", "motivo": "Cierre único de la convocatoria"}}
    Se puede antes o después de que empiece, y admite darle más horas a quien las pida.

  · Cuando entrega, se pide la calificación:
    POST /panel/postulaciones/{{id}}/prueba/calificacion-ia
    y las notas quedan en GET /panel/postulaciones/{{id}}/prueba/notas.
""")


if __name__ == "__main__":
    try:
        main()
    except KeyboardInterrupt:
        sys.exit(130)
    except RuntimeError as e:
        print(f"\nSe cortó: {e}\n", file=sys.stderr)
        sys.exit(1)
