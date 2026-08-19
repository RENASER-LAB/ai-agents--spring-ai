#!/usr/bin/env python3
"""
Genera currículums en PDF para probar la calificación con IA.

Los del script de siembra no sirven para esto: son PDF sin texto extraíble, así que el
sistema los rechaza antes de llamar al modelo —correctamente, porque sin texto no hay
nota—. Estos llevan texto de verdad, seleccionable.

Son **cuatro perfiles deliberadamente distintos**, para poder comprobar que la IA
distingue y no le pone lo mismo a todo el mundo:

  fuerte    Resultados con números, sistemas que dejó montados, gente que formó.
  flojo     Diez años de antigüedad y ni un resultado medible. Sirve para verificar
            que «la antigüedad no da puntos por sí sola».
  medio     Hizo cosas, pero casi nada medido. Debería quedar en el centro.
  humo      Lenguaje impecable y cero evidencia verificable. Sirve para verificar que
            eso baja la confianza en vez de subir la nota.

El «fuerte» y el «humo» son los dos interesantes: si la IA los puntúa parecido, algo va
mal en las instrucciones del agente.

Uso:
    python3 scripts/generar-cv-de-prueba.py [--salida carpeta]

Necesita reportlab:  pip install reportlab
"""

import argparse
import sys
import textwrap
from pathlib import Path

PERFILES = {
    "fuerte": [
        "ANDREA MENDOZA SILVA",
        "Desarrolladora de software · Arequipa",
        "",
        "EXPERIENCIA",
        "",
        "Coordinadora técnica · Logística Andina · 2023 a hoy",
        "Reescribí el cálculo de rutas de reparto, que corría a mano en una hoja de "
        "cálculo. El tiempo de armado de rutas bajó de cuatro horas a veinte minutos y "
        "los repartos fuera de plazo pasaron del 18% al 4% en seis meses.",
        "Monté el primer conjunto de pruebas automáticas del equipo: 340 pruebas que "
        "corren en cada cambio. Los errores que llegaban a producción bajaron de nueve "
        "al mes a uno o dos.",
        "Formé a dos personas que entraron sin experiencia. Las dos llevan hoy sus "
        "propios módulos sin supervisión.",
        "",
        "Desarrolladora · Cooperativa San Martín · 2021 a 2023",
        "Automaticé la conciliación de pagos, que ocupaba a dos personas tres días al "
        "mes. Quedó en un proceso de veinte minutos y esas dos personas pasaron a "
        "atención al socio.",
        "Documenté el despliegue, que solo sabía hacer una persona. Después de eso "
        "cualquiera del equipo podía desplegar; antes, si esa persona faltaba, nadie.",
        "",
        "FORMACIÓN",
        "Ingeniería de Sistemas · Universidad Nacional de San Agustín · 2020",
        "",
        "HERRAMIENTAS",
        "Java, Spring Boot, PostgreSQL, Docker, Git",
    ],
    "flojo": [
        "ROBERTO CACERES PINTO",
        "Analista de sistemas · Lima",
        "",
        "EXPERIENCIA",
        "",
        "Analista de sistemas · Corporación Delta · 2016 a hoy",
        "Responsable del mantenimiento de los sistemas del área. Diez años de "
        "experiencia en el rubro.",
        "Participación en reuniones de coordinación con las distintas áreas de la "
        "empresa.",
        "Apoyo en la elaboración de reportes mensuales para la gerencia.",
        "Atención de incidencias reportadas por los usuarios.",
        "",
        "Asistente de soporte · Servicios Integrales del Sur · 2014 a 2016",
        "Soporte a usuarios. Instalación de equipos. Mantenimiento preventivo.",
        "",
        "FORMACIÓN",
        "Ingeniería de Sistemas · Universidad Privada · 2014",
        "",
        "HERRAMIENTAS",
        "Windows, Office, SQL básico",
    ],
    "medio": [
        "PAOLA HUAMANI ROJAS",
        "Desarrolladora web · Cusco",
        "",
        "EXPERIENCIA",
        "",
        "Desarrolladora · Turismo Digital SAC · 2022 a hoy",
        "Desarrollé el módulo de reservas del portal de la empresa, que antes se "
        "llevaba por correo. Ahora las reservas entran solas, aunque no tengo el número "
        "exacto de cuántas.",
        "Migré la base de datos a PostgreSQL. Fue un trabajo de dos meses y no se perdió "
        "información.",
        "Ayudé a una compañera nueva a entender el código del portal.",
        "",
        "Practicante de desarrollo · Estudio Contable Andes · 2021 a 2022",
        "Hice pantallas de consulta para el sistema interno. Corregí errores que "
        "reportaban los contadores.",
        "",
        "FORMACIÓN",
        "Ingeniería Informática · Universidad Nacional San Antonio Abad · 2021",
        "",
        "HERRAMIENTAS",
        "JavaScript, React, Node, PostgreSQL",
    ],
    "humo": [
        "MAXIMILIANO VILLARREAL DEL AGUILA",
        "Líder de transformación digital · Lima",
        "",
        "PERFIL",
        "Profesional de alto impacto con visión estratégica y orientación a resultados. "
        "Apasionado por la innovación disruptiva y la generación de valor sostenible. "
        "Liderazgo transformacional y pensamiento sistémico aplicado a entornos de alta "
        "complejidad.",
        "",
        "EXPERIENCIA",
        "",
        "Líder de transformación digital · Grupo Empresarial Continental · 2023 a hoy",
        "Lidero la transformación digital de la organización, impulsando sinergias "
        "transversales y potenciando el capital humano hacia una cultura de excelencia.",
        "Responsable de la definición estratégica del roadmap tecnológico y de la "
        "articulación con los stakeholders clave del ecosistema.",
        "Promuevo la agilidad organizacional y el empoderamiento de los equipos "
        "autogestionados de alto desempeño.",
        "",
        "Gerente de innovación · Consultora Horizonte · 2020 a 2023",
        "Diseñé e implementé el marco de innovación abierta, generando valor "
        "diferencial y ventaja competitiva sostenible para la compañía.",
        "",
        "FORMACIÓN",
        "MBA · Escuela de Negocios · 2020",
        "Administración de Empresas · Universidad de Lima · 2016",
        "",
        "COMPETENCIAS",
        "Liderazgo, visión estratégica, innovación, comunicación efectiva, resiliencia",
    ],
}


def escribir_pdf(destino, lineas):
    from reportlab.lib.pagesizes import A4
    from reportlab.pdfgen import canvas

    ancho, alto = A4
    margen = 56
    lienzo = canvas.Canvas(str(destino), pagesize=A4)
    y = alto - margen

    for i, parrafo in enumerate(lineas):
        if not parrafo:
            y -= 10
            continue
        # La primera línea es el nombre; la segunda, el puesto. El resto va en cuerpo, y
        # los títulos de sección se reconocen porque van en mayúsculas.
        if i == 0:
            lienzo.setFont("Helvetica-Bold", 15)
        elif i == 1:
            lienzo.setFont("Helvetica", 10.5)
        elif parrafo.isupper():
            y -= 6
            lienzo.setFont("Helvetica-Bold", 11)
        else:
            lienzo.setFont("Helvetica", 10)

        for linea in textwrap.wrap(parrafo, width=95) or [""]:
            if y < margen:
                lienzo.showPage()
                y = alto - margen
            lienzo.drawString(margen, y, linea)
            y -= 14

    lienzo.save()


def main():
    p = argparse.ArgumentParser(description=__doc__)
    p.add_argument("--salida", default="cv-de-prueba",
                   help="Carpeta donde dejar los PDF (por defecto ./cv-de-prueba)")
    args = p.parse_args()

    try:
        import reportlab  # noqa: F401
    except ImportError:
        print("Falta reportlab. Instálalo con:\n  pip install reportlab", file=sys.stderr)
        return 1

    carpeta = Path(args.salida)
    carpeta.mkdir(parents=True, exist_ok=True)

    for nombre, lineas in PERFILES.items():
        destino = carpeta / f"cv-{nombre}.pdf"
        escribir_pdf(destino, lineas)
        print(f"  {destino}")

    print(f"\n{len(PERFILES)} currículums en {carpeta.resolve()}")
    print("Súbelos desde el demo. Compara «fuerte» con «humo»: si la IA los puntúa")
    print("parecido, el problema está en las instrucciones del agente, no en el código.")
    return 0


if __name__ == "__main__":
    sys.exit(main())
