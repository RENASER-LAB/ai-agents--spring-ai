#!/usr/bin/env python3
"""
Deja la vacante «Desarrollador web» con el escenario exacto que las pruebas e2e
del panel dan por sabido.

`sembrar-datos-de-prueba.py` crea las cuentas, las postulaciones y los currículums,
pero NO produce notas, ni grupos de prioridad, ni pretensiones: eso lo escribe la IA
al cerrar el perfil integral, y en local su clave está muerta desde el 25/08. El
resultado es que una suite recién sembrada sale con ~30 fallos que no son del
código: los specs afirman valores concretos que nunca llegan a existir.

El escenario está documentado en la cabecera de
`herramientas/e2e/03-orden.spec.ts` del portal, y es este:

    Lucía     (ALTA,           74, Arequipa — Camaná,      3100–3600)
    Camila    (ALTA,           55, Lima — Lima,            2500–3000)
    Sebastián (NO_PRIORIZADO,  61, Junín — Huancayo,       sin pretensión)
    Joaquín   (INCOMPATIBLE,   95, La Libertad — Trujillo, 4000–5200)

El orden que sale de ahí —Lucía, Camila, Sebastián, Joaquín— es lo que los specs
llaman «el orden del backend», y no es el de la nota: `ServicioPerfilIntegralPanelImpl`
ordena por ORDEN_GRUPO (ALTA, POTENCIAL_CON_RIESGO, NO_PRIORIZADO, INCOMPATIBLE) y
solo dentro de cada grupo por nota descendente. Por eso Joaquín, con el 95 más alto
de la tanda, sale el último: es justo lo que ese test existe para fijar.

⚠️ **Las ciudades NO se tocan.** Ya salen bien del sembrador —las cuatro personas
nacen con su ubigeo— y `06-sin-ciudad.spec.ts` se fabrica su propio escenario
escribiendo y restaurando por su cuenta. Tocarlas aquí le pisaría el suyo.

⚠️ **Y INCOMPATIBLE es un estado que el sistema real no produce.** Quien falla un
requisito indispensable se cierra como NO_CONTINUA sin llegar a tener grupo. Se
escribe aquí porque el orden que el spec fija lo exige —Joaquín tiene que caer
detrás de Sebastián, y NO_PRIORIZADO ya es suyo— y porque `ORDEN_GRUPO` del backend
lo declara. Queda dicho para que nadie lo lea como un caso alcanzable.

## Por qué una mitad va por SQL

`sembrar-datos-de-prueba.py` declara «todo pasa por la API, nunca por SQL», y este
script respeta esa regla donde puede:

- **Las pretensiones van por la API**, con el mismo `PUT /portal/perfil` que usa el
  candidato desde su portal.
- **Las notas y los grupos van por SQL**, porque no hay endpoint que los escriba: los
  produce el agente al calificar, y pedírselo cuesta una llamada al modelo por
  persona —con una clave que en local no responde— y devolvería otros números.

Uso:
    python3 scripts/sembrar-escenario-e2e.py
    python3 scripts/sembrar-escenario-e2e.py --api http://localhost:8081/api/v1 \
        --contenedor renaser-verifica --db renaser_db
"""

import argparse
import subprocess
import sys

import requests

VACANTE = "Desarrollador web"
CONTRASENA = "Demo12345!"

# (correo, nota, grupo, pretensión o None)
#
# La pretensión es (min, max, moneda). Sebastián va sin ninguna a propósito: el filtro
# de pretensión tiene que poder demostrar que quien no declaró sueldo queda FUERA del
# rango en vez de colarse como un cero, y sin un caso así ese test pasa por vacío.
# El retrato que escribe el agente al calificar el currículum: (adecuación, potencial,
# alto rendimiento, confianza). Sin él, las columnas «Adecuación» y «Potencial» no se
# pintan —solo salen si alguna fila las trae— y el spec que comprueba que existen en
# el perfil integral y NO en la prueba se queda sin las dos mitades que compara.
RETRATO = {
    "lucía.chávez@ejemplo.pe": (78, 71, 64, 82),
    "camila.torres@ejemplo.pe": (58, 66, 49, 74),
    "sebastián.cárdenas@ejemplo.pe": (62, 55, 51, 69),
    "joaquín.vargas@ejemplo.pe": (93, 88, 90, 91),
}

ESCENARIO = [
    ("lucía.chávez@ejemplo.pe", "74.00", "ALTA", (3100, 3600, "PEN")),
    ("camila.torres@ejemplo.pe", "55.00", "ALTA", (2500, 3000, "PEN")),
    ("sebastián.cárdenas@ejemplo.pe", "61.00", "NO_PRIORIZADO", None),
    ("joaquín.vargas@ejemplo.pe", "95.00", "INCOMPATIBLE", (4000, 5200, "PEN")),
]


def paso(texto):
    print(f"\n\033[1m{texto}\033[0m")


class Portal:
    """El portal del candidato, con la sesión de quien esté dentro."""

    def __init__(self, base):
        self.base = base.rstrip("/")
        self.token = None

    def _cab(self):
        return {"Authorization": f"Bearer {self.token}"} if self.token else {}

    def entrar(self, correo):
        r = requests.post(
            f"{self.base}/portal/auth/login",
            json={"correo": correo, "contrasena": CONTRASENA},
            timeout=30,
        )
        if not r.ok:
            raise SystemExit(f"no se pudo entrar como {correo}: {r.status_code} {r.text}")
        self.token = r.json()["token"]

    def perfil(self):
        r = requests.get(f"{self.base}/portal/perfil", headers=self._cab(), timeout=30)
        r.raise_for_status()
        return r.json()

    def guardar_cabecera(self, cuerpo):
        r = requests.put(
            f"{self.base}/portal/perfil", json=cuerpo, headers=self._cab(), timeout=30
        )
        if not r.ok:
            raise SystemExit(f"PUT /portal/perfil falló: {r.status_code} {r.text}")


def sql(contenedor, db, consulta):
    """Una consulta contra el Postgres del contenedor.

    Se va por `docker exec` y no por un driver porque el repositorio no depende de
    psycopg y añadirlo por esto sería caro; es además lo que ya hace
    `06-sin-ciudad.spec.ts` del portal para fabricarse su escenario.
    """
    hecho = subprocess.run(
        ["docker", "exec", contenedor, "psql", "-U", "postgres", "-d", db, "-tAc", consulta],
        capture_output=True,
        text=True,
    )
    if hecho.returncode != 0:
        raise SystemExit(f"psql falló: {hecho.stderr.strip()}")
    return hecho.stdout.strip()


def pretensiones(api):
    paso("Las pretensiones, por la API del portal")
    for correo, _, _, pretension in ESCENARIO:
        if pretension is None:
            print(f"  · {correo}: sin pretensión, a propósito")
            continue
        minimo, maximo, moneda = pretension
        portal = Portal(api)
        portal.entrar(correo)
        actual = portal.perfil()
        # ⚠️ `PUT /portal/perfil` REEMPLAZA la cabecera entera: lo que no viaje se
        # guarda en nulo. Se siembra de lo que hay y solo se cambia la pretensión.
        portal.guardar_cabecera(
            {
                "titular": actual.get("titular"),
                "resumen": actual.get("resumen"),
                "habilidades": actual.get("habilidades") or [],
                "experienciaMeses": actual.get("experienciaMeses"),
                "ubicacion": actual.get("ubicacion"),
                "disponibilidad": actual.get("disponibilidad"),
                "pretension": {"min": minimo, "max": maximo, "moneda": moneda},
            }
        )
        print(f"  · {correo}: {minimo}–{maximo} {moneda}")


def notas_y_grupos(contenedor, db):
    paso("Las notas y los grupos, por SQL")
    for correo, nota, grupo, _ in ESCENARIO:
        # Se resuelve la postulación desde el correo: los ids no son estables entre
        # siembras, y fijarlos es lo que ya dejó specs apuntando a filas que se movieron.
        suya = (
            "select p.id from postulacion p "
            "join usuario u on u.id = p.usuario_id "
            "join vacante v on v.id = p.vacante_id "
            f"where u.correo = '{correo}' and v.titulo = '{VACANTE}'"
        )
        sql(
            contenedor,
            db,
            f"update postulacion set grupo_prioridad = '{grupo}' where id in ({suya});",
        )
        sql(
            contenedor,
            db,
            f"update nota_etapa set puntaje = {nota} "
            f"where etapa_codigo = 'PERFIL_INTEGRAL' and postulacion_id in ({suya});",
        )
        print(f"  · {correo}: nota {nota}, grupo {grupo}")


def retratos(contenedor, db):
    """El retrato de la IA por persona: adecuación, potencial, alto rendimiento, confianza.

    `columnasDelRanking` solo pinta «Adecuación» y «Potencial» si alguna fila las trae,
    y con `perfil_talento` vacío nunca las trae. `20-prueba-y-empresas` compara justo
    eso —que están en el perfil integral y desaparecen en la prueba— y sin una sola
    fila la comparación no tiene dos mitades.

    Los números no los fija ningún spec: solo se afirma que las columnas existen. Van
    en la línea de la nota de cada uno para que la pantalla se lea coherente.
    """
    paso("El retrato de la IA, por SQL")
    for correo, (adecuacion, potencial, alto, confianza) in RETRATO.items():
        sql(
            contenedor,
            db,
            f"""
            insert into perfil_talento
              (postulacion_id, adecuacion, potencial, alto_rendimiento,
               confianza_evidencia, resumen, version_pesos_id)
            select p.id, {adecuacion}, {potencial}, {alto}, {confianza},
                   'Retrato sembrado para las pruebas e2e del panel.',
                   n.version_pesos_id
            from postulacion p
            join usuario u on u.id = p.usuario_id
            join vacante v on v.id = p.vacante_id
            join nota_etapa n
              on n.postulacion_id = p.id and n.etapa_codigo = 'PERFIL_INTEGRAL'
            where u.correo = '{correo}' and v.titulo = '{VACANTE}'
            on conflict (postulacion_id) do update
              set adecuacion = excluded.adecuacion,
                  potencial = excluded.potencial,
                  alto_rendimiento = excluded.alto_rendimiento,
                  confianza_evidencia = excluded.confianza_evidencia;
            """,
        )
        print(f"  · {correo}: adecuación {adecuacion}, potencial {potencial}")

    # Y el resto de la base: quien tenga nota del perfil tiene retrato.
    #
    # ⚠️ No es relleno. Las dos las escribe el MISMO paso del agente al calificar el
    # currículum, así que una nota sin retrato es un estado que el sistema real no
    # produce — y `20-prueba-y-empresas` elige su vacante sola, entre las que tengan
    # una prueba entregada, así que sembrar solo la nuestra lo deja a merced de cuál
    # le toque. Los números se derivan de la propia nota para que nada se contradiga.
    otros = sql(
        contenedor,
        db,
        """
        insert into perfil_talento
          (postulacion_id, adecuacion, potencial, alto_rendimiento,
           confianza_evidencia, resumen, version_pesos_id)
        select n.postulacion_id, n.puntaje, n.puntaje, n.puntaje, 75,
               'Retrato sembrado para las pruebas e2e del panel.', n.version_pesos_id
        from nota_etapa n
        where n.etapa_codigo = 'PERFIL_INTEGRAL'
        on conflict (postulacion_id) do nothing
        returning postulacion_id;
        """,
    )
    cuantos = len([x for x in otros.splitlines() if x.strip()])
    print(f"  · {cuantos} retrato(s) más, derivados de su nota del perfil")


def prueba_sin_notas(contenedor, db):
    """Deja la pestaña «Prueba del puesto» de esta vacante con la columna vacía.

    Dos specs la usan como el caso «una columna ENTERA sin un solo dato»
    —`03-orden` comprueba que ordenarla no descoloca nada, y `05-excel` que el
    botón de descarga se apaga diciendo «Nada que descargar»— y su premisa está
    escrita en el propio test: «En Prueba del puesto nadie tiene nota».

    La siembra dejaba dos notas sueltas ahí (Joaquín 94, Lucía 82) que no vienen
    de ninguna prueba rendida: nadie de esta vacante ha entregado la del puesto.
    Se van, y con ellas vuelve el caso que esos dos tests existen para medir.
    """
    paso("La prueba del puesto, sin notas")
    borradas = sql(
        contenedor,
        db,
        f"""
        delete from nota_etapa n
        using postulacion p, vacante v
        where n.postulacion_id = p.id and p.vacante_id = v.id
          and v.titulo = '{VACANTE}' and n.etapa_codigo = 'PRUEBA_PUESTO'
        returning n.id;
        """,
    )
    cuantas = len([x for x in borradas.splitlines() if x.strip()])
    print(f"  · {cuantas} nota(s) de la prueba retiradas: la columna queda toda en guiones")


def comprobar(contenedor, db):
    """Lo escrito, leído de vuelta. Sin esto el script afirma y no demuestra."""
    paso("Cómo queda la tanda")
    filas = sql(
        contenedor,
        db,
        f"""
        select pe.nombre, p.grupo_prioridad, n.puntaje,
               coalesce(pc.pretension_min::text, 'sin pretensión')
        from postulacion p
        join vacante v on v.id = p.vacante_id
        join usuario u on u.id = p.usuario_id
        join persona pe on pe.id = u.persona_id
        left join nota_etapa n
          on n.postulacion_id = p.id and n.etapa_codigo = 'PERFIL_INTEGRAL'
        left join perfil_candidato pc on pc.persona_id = pe.id
        where v.titulo = '{VACANTE}'
        order by array_position(
                   array['ALTA','POTENCIAL_CON_RIESGO','NO_PRIORIZADO','INCOMPATIBLE'],
                   p.grupo_prioridad),
                 n.puntaje desc;
        """,
    )
    for fila in filas.splitlines():
        print("  " + " · ".join(c.strip() for c in fila.split("|")))


def main():
    partes = argparse.ArgumentParser(description=__doc__)
    partes.add_argument("--api", default="http://localhost:8081/api/v1")
    partes.add_argument("--contenedor", default="renaser-verifica")
    partes.add_argument("--db", default="renaser_db")
    args = partes.parse_args()

    pretensiones(args.api)
    notas_y_grupos(args.contenedor, args.db)
    retratos(args.contenedor, args.db)
    prueba_sin_notas(args.contenedor, args.db)
    comprobar(args.contenedor, args.db)
    print("\nListo. Las pruebas e2e del panel ya tienen su escenario.")


if __name__ == "__main__":
    sys.exit(main())
