package com.renaser.ai.ai_engine.informe.service;

import com.renaser.ai.ai_engine.informe.dto.DtosInforme.Anexo;
import com.renaser.ai.ai_engine.informe.dto.DtosInforme.Bloque;
import com.renaser.ai.ai_engine.informe.dto.DtosInforme.Criterio;
import com.renaser.ai.ai_engine.informe.dto.DtosInforme.Dato;
import com.renaser.ai.ai_engine.informe.dto.DtosInforme.Detalle;
import com.renaser.ai.ai_engine.informe.dto.DtosInforme.Enlace;
import com.renaser.ai.ai_engine.informe.dto.DtosInforme.EntregableAnexo;
import com.renaser.ai.ai_engine.informe.dto.DtosInforme.Evaluado;
import com.renaser.ai.ai_engine.informe.dto.DtosInforme.Experiencia;
import com.renaser.ai.ai_engine.informe.dto.DtosInforme.Informe;
import com.renaser.ai.ai_engine.informe.dto.DtosInforme.Nota;
import com.renaser.ai.ai_engine.informe.dto.DtosInforme.PreguntaAnexo;
import com.renaser.ai.ai_engine.informe.dto.DtosInforme.Punto;
import com.renaser.ai.ai_engine.informe.dto.DtosInforme.Recuento;
import com.renaser.ai.ai_engine.informe.dto.DtosInforme.Resumen;
import com.renaser.ai.ai_engine.informe.dto.DtosInforme.Senal;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Informes armados a mano para las pruebas: lo que el armado sacaría de la base para un CV, una
 * evaluación y una prueba del puesto, con datos inventados.
 */
public final class InformesDePrueba {

    private InformesDePrueba() {
    }

    public static Informe cv(int demostradas) {
        List<Punto> demuestra = new ArrayList<>();
        for (int i = 1; i <= demostradas; i++) {
            demuestra.add(new Punto("Logro demostrado número " + i, null, "CV", null, null));
        }
        List<Criterio> criterios = List.of(
                criterio("Resultados demostrables", "88", null, "IA", false, null,
                        "Redujo el tiempo de contratación de 30 a 18 días con cifras verificables."),
                criterio("Habilidades del puesto", "55", null, "Persona", true,
                        "el puesto pide cierres de nómina desde el primer mes",
                        "Nombra planillas y Excel avanzado sin ningún ejemplo."),
                new Criterio("Desarrollo de personas", null, BigDecimal.valueOf(100), "—", null, null, null,
                        false, null, null, null, null, "Sin nota todavía"));
        return new Informe("CV", "Informe del currículum", "Renaser Consulting",
                "Analista de Recursos Humanos", 1L, "Ana Torres Quispe", "ana@correo.pe", Instant.now(),
                new Nota(BigDecimal.valueOf(74), "74", "de 100", "Los criterios del currículum."), null,
                List.of(new Dato("Generado", "14/11/2026 · 10:31")),
                List.of(new Recuento("Demostradas", demostradas, "BIEN"), new Recuento("No cuadran", 1, "MAL"),
                        new Recuento("Por verificar", 2, "PENDIENTE")),
                new Experiencia("4 años y 2 meses", null, "Analista de Selección", "Retail Sur S.A.C. · 2 años y 5 meses",
                        "Licenciada en Psicología", List.of("Selección masiva", "Excel"),
                        "Analista de RR. HH. con foco en selección para retail."),
                new Resumen("Lo que muestra su CV", "Cuatro años en selección para retail."),
                new Evaluado("Qué se evaluó · lo que demostró en cada criterio", "sobre 100", criterios, null),
                List.of(new Bloque("DEMUESTRA", "Lo que el CV demuestra", "BIEN", "Sin datos", demuestra),
                        new Bloque("NO_CUADRA", "No cuadra", "MAL", "Sin datos",
                                List.of(new Punto("«Jefa de área» en 2022, pero figura como practicante.", null,
                                        "CV", null, null))),
                        new Bloque("PENDIENTE", "Por verificar en la evaluación", "PENDIENTE", "Sin datos", List.of(
                                new Punto("«Manejo de planillas y nómina»",
                                        "¿Cuántos colaboradores tenía la última planilla que cerraste?", "CV",
                                        "Declarada", null),
                                new Punto("«Excel avanzado»", "Cuéntame un reporte que armaste tú.", "CV",
                                        "Declarada", null)))),
                List.of(new Enlace("Currículum (PDF)", "https://almacen.example/cv.pdf?firma=1", "CV"),
                        new Enlace("LinkedIn", "https://www.linkedin.com/in/ana", "LINKEDIN")),
                null, List.of(), null, null, null, false,
                "Informe CV - Ana Torres Quispe - Analista de Recursos Humanos.pdf");
    }

    public static Informe prueba(List<PreguntaAnexo> preguntas) {
        List<Criterio> criterios = List.of(
                new Criterio("Diagnóstico del problema", BigDecimal.valueOf(23), BigDecimal.valueOf(25), "23",
                        "/ 25", 92, "IA", false, null, "Ubicó la rotación en los 3 primeros meses.",
                        "Ubicó la rotación en los 3 primeros meses.", null, null),
                new Criterio("Presupuesto del plan", BigDecimal.valueOf(10), BigDecimal.valueOf(20), "10",
                        "/ 20", 50, "Persona", true, "La hoja de cálculo no abrió", "Calificó con el resumen.",
                        "Calificó con el resumen.", null, null));
        return new Informe("PRUEBA", "Informe de la prueba del puesto", "Renaser Consulting",
                "Analista de Recursos Humanos", 1L, "Ana Torres Quispe", null, Instant.now(),
                new Nota(BigDecimal.valueOf(78), "78", "de 100", "La suma de los criterios."), "ALTA",
                List.of(new Dato("Prueba", "Caso práctico"), new Dato("Generado", "14/11/2026 · 10:33")), List.of(),
                null, new Resumen("En pocas líneas", "Entregó el plan completo para las seis tiendas."),
                new Evaluado("Qué se evaluó", "puntos de cada criterio", criterios,
                        "Quién puso la nota: IA, Persona o Sistema"),
                List.of(new Bloque("FUERTES", "Puntos fuertes", "BIEN", "Sin datos",
                                List.of(new Punto("Diagnóstico del problema", null, "CRITERIO", null, null))),
                        new Bloque("FORTALECER", "Puntos a fortalecer", "NEUTRO", "Sin datos",
                                List.of(new Punto("Presupuesto del plan", "Calificó con el resumen.", "CRITERIO", null, null))),
                        new Bloque("PENDIENTE", "Por verificar en la entrevista", "PENDIENTE", "Sin datos", List.of(
                                new Punto("No entregó «Cronograma por tienda»", null, "PRUEBA", "No entregado", "De la prueba"),
                                new Punto("Desacuerdo con su jefe", null, "EVALUACION", "Demasiado ideal", "De la evaluación"),
                                new Punto("«Motivo de salida»", "¿Por qué dejaste tu último trabajo?", "CV", "Declarada", "Del CV")))),
                List.of(), "El porqué de cada nota", List.of(), null,
                new Anexo("Respuestas y entregables", preguntas.size() + " preguntas", "El caso de las tres tiendas.",
                        preguntas, "1 de 2 entregados",
                        List.of(new EntregableAnexo("Plan de reclutamiento", "Subido el 12/11 a las 17:40", true),
                                new EntregableAnexo("Cronograma por tienda", "No se subió ningún archivo", false))),
                null, false, "Informe prueba - Ana Torres Quispe - Analista de Recursos Humanos.pdf");
    }

    public static Informe evaluacion() {
        return new Informe("EVALUACION", "Informe de la evaluación", "Renaser Consulting",
                "Analista de Recursos Humanos", 1L, "Ana Torres Quispe", null, Instant.now(),
                new Nota(BigDecimal.valueOf(82), "82", "de 100", "CV 30 % · Evaluación 70 %"), "ALTA",
                List.of(new Dato("Cómo se calcula", "CV 30 % · Evaluación 70 %"), new Dato("Preguntas", "1 cerrada · 2 abiertas")),
                List.of(), null, new Resumen("En pocas líneas", "Responde con episodios concretos."),
                new Evaluado("Qué se evaluó", "sobre 100", List.of(new Criterio("Iniciativa", BigDecimal.valueOf(90),
                        BigDecimal.valueOf(100), "90", null, 90, "IA", false, null, null, null,
                        "0 cerradas · 1 abiertas", null)), null),
                List.of(new Bloque("FUERTES", "Puntos fuertes", "BIEN", "Sin datos",
                                List.of(new Punto("Cerró 3 procesos en 10 días.", null, null, null, null))),
                        new Bloque("FORTALECER", "Puntos a fortalecer", "NEUTRO", "Sin datos",
                                List.of(new Punto("Habla poco de lo que salió mal.", "Pedirle un error propio.", null, null, null))),
                        new Bloque("DEBILIDADES", "Debilidades a tener en cuenta", "MAL", "Sin datos", List.of()),
                        new Bloque("PIDIO_EL_CV", "Lo que pidió el CV", "CV", "Sin datos",
                                List.of(new Punto("«Excel avanzado»", "Cuéntame un reporte.", "CV", "Por contrastar", null))),
                        new Bloque("PENDIENTE", "Por verificar en la prueba del puesto", "PENDIENTE", "Sin datos",
                                List.of(new Punto("Respuesta «demasiado ideal»", null, "EVALUACION", "Demasiado ideal", null)))),
                List.of(), "El porqué de cada nota",
                List.of(new Detalle(1, "Cuéntanos una vez en la que mejoraste algo", "Abierta · Iniciativa", "3,5 / 4",
                        false, null, "Detectó un problema por su cuenta.", "Armé por mi cuenta un tablero",
                        List.of(new Senal("Episodio real", true), new Senal("Fue suyo", true),
                                new Senal("Dato duro", false), new Senal("Incomodidad", false)), null)),
                "Lo cerrado: 80 de 100 en 1 pregunta.",
                new Anexo("Preguntas y respuestas", "3 preguntas", null, List.of(
                        new PreguntaAnexo(1, "Cuéntanos una vez en la que mejoraste algo", "Abierta · Iniciativa",
                                "Armé por mi cuenta un tablero.", "Detectó un problema.", "Detectó un problema."),
                        new PreguntaAnexo(2, "Un proceso se retrasa porque el área no responde", "Opción única",
                                "«Pido una fecha concreta»", null, null),
                        new PreguntaAnexo(3, "¿Qué error tuyo te enseñó más?", "Abierta", null, null, null)),
                        null, List.of()),
                null, false, "Informe evaluación - Ana Torres Quispe - Analista de Recursos Humanos.pdf");
    }

    /** Una explicación de criterio de largo corriente: unos 240 caracteres, tres renglones bajo la barra. */
    public static final String EXPLICACION_CORRIENTE =
            "Muestra resultados con cifras en sus dos últimos puestos: redujo el tiempo de contratación de 30 a 18 "
                    + "días y cubrió 45 vacantes de temporada en seis semanas; falta evidencia de su papel exacto en el equipo.";

    /**
     * Un CV con los ocho criterios del currículum, cada uno con su explicación completa bajo la barra
     * (sin redactor, D-1), y las listas del tamaño pedido. Cada punto lleva su número para poder buscarlo
     * entero en el PDF: «Logro demostrado número 3», «Contradicción número 1», «Duda número 2» y su
     * «¿Pregunta número 2?».
     */
    public static Informe cvCon(String explicacion, int demostradas, int contradichas, int dudas) {
        String[] nombres = {"Resultados demostrables", "Complejidad y alcance", "Sistemas o procesos creados",
                "Desarrollo de personas", "Velocidad de aprendizaje", "Iniciativa o creación",
                "Habilidades del puesto", "Calidad de la evidencia"};
        List<Criterio> criterios = new ArrayList<>();
        for (int i = 0; i < nombres.length; i++) {
            boolean ajustada = i == 2;
            criterios.add(criterio(nombres[i], String.valueOf(50 + i * 5), null, ajustada ? "Persona" : "IA",
                    ajustada, ajustada ? "el puesto pide nómina desde el primer mes" : null, explicacion));
        }
        List<Punto> demuestra = new ArrayList<>();
        for (int i = 1; i <= demostradas; i++) {
            demuestra.add(new Punto("Logro demostrado número " + i, null, "CV", null, null));
        }
        List<Punto> noCuadra = new ArrayList<>();
        for (int i = 1; i <= contradichas; i++) {
            noCuadra.add(new Punto("Contradicción número " + i, null, "CV", null, null));
        }
        List<Punto> porVerificar = new ArrayList<>();
        for (int i = 1; i <= dudas; i++) {
            porVerificar.add(new Punto("«Duda número " + i + "»", "¿Pregunta número " + i + "?", "CV",
                    i % 3 == 0 ? "Sin información" : "Declarada", null));
        }
        return new Informe("CV", "Informe del currículum", "Renaser Consulting S.A.C.",
                "Analista de Recursos Humanos", 1L, "Carla Solo CV", null, Instant.now(),
                new Nota(BigDecimal.valueOf(68), "68", "de 100", "Los criterios del currículum."), null,
                List.of(new Dato("Generado", "16/11/2026 · 13:09")),
                List.of(new Recuento("Demostradas", demostradas, "BIEN"),
                        new Recuento("No cuadran", contradichas, "MAL"),
                        new Recuento("Por verificar", dudas, "PENDIENTE")),
                new Experiencia("5 años y 2 meses", null, "Analista de Selección Senior",
                        "Tiendas del Sur S.A. · 2 años y 6 meses", "Bachiller en Psicología",
                        List.of("Selección masiva", "Excel", "Entrevistas", "Onboarding", "Clima"),
                        "Analista de selección con foco en retail y campañas masivas."),
                null,
                new Evaluado("Qué se evaluó · lo que demostró en cada criterio", "sobre 100", criterios, null),
                List.of(new Bloque("DEMUESTRA", "Lo que el CV demuestra", "BIEN", "Sin datos", demuestra),
                        new Bloque("NO_CUADRA", "No cuadra", "MAL", "Sin datos", noCuadra),
                        new Bloque("PENDIENTE", "Por verificar en la evaluación", "PENDIENTE", "Sin datos",
                                porVerificar)),
                List.of(), null, List.of(), null, null, null, false,
                "Informe CV - Carla Solo CV - Analista de Recursos Humanos.pdf");
    }

    /** La evaluación con muchos puntos en cada bloque, para ver que los cinco empiezan en la primera hoja. */
    public static Informe evaluacionCon(int puntosPorBloque) {
        Informe base = evaluacion();
        List<Bloque> bloques = base.bloques().stream().map(b -> {
            List<Punto> puntos = new ArrayList<>();
            for (int i = 1; i <= puntosPorBloque; i++) {
                puntos.add(new Punto(b.titulo() + " · punto número " + i
                        + ": una frase de largo normal sobre lo que dejó ver en sus respuestas.",
                        "PIDIO_EL_CV".equals(b.codigo()) || "FORTALECER".equals(b.codigo())
                                ? "Lo que conviene preguntarle sobre el punto " + i + "." : null,
                        "PIDIO_EL_CV".equals(b.codigo()) ? "CV" : null,
                        "PIDIO_EL_CV".equals(b.codigo()) ? "Por contrastar" : null, null));
            }
            return new Bloque(b.codigo(), b.titulo(), b.tono(), b.vacio(), puntos);
        }).toList();
        return new Informe(base.etapa(), base.titulo(), base.empresa(), base.vacante(), base.postulacionId(),
                base.candidato(), base.correo(), base.generadoEn(), base.nota(), base.grupo(), base.datosClave(),
                base.recuentos(), base.experiencia(), base.resumen(), base.evaluado(), bloques, base.enlaces(),
                base.detalleTitulo(), base.detalle(), base.detallePie(), base.anexo(), base.aviso(),
                base.textosEnCamino(), base.nombreArchivo());
    }

    /** El mismo informe con un bloque más al final, como el «Qué buscar en el CV» del CV. */
    public static Informe conBloque(Informe i, Bloque extra) {
        List<Bloque> bloques = new ArrayList<>(i.bloques());
        bloques.add(extra);
        return new Informe(i.etapa(), i.titulo(), i.empresa(), i.vacante(), i.postulacionId(), i.candidato(),
                i.correo(), i.generadoEn(), i.nota(), i.grupo(), i.datosClave(), i.recuentos(), i.experiencia(),
                i.resumen(), i.evaluado(), bloques, i.enlaces(), i.detalleTitulo(), i.detalle(), i.detallePie(),
                i.anexo(), i.aviso(), i.textosEnCamino(), i.nombreArchivo());
    }

    private static Criterio criterio(String nombre, String nota, String maximo, String quien, boolean ajustada,
                                     String motivo, String texto) {
        BigDecimal valor = new BigDecimal(nota);
        BigDecimal max = maximo == null ? BigDecimal.valueOf(100) : new BigDecimal(maximo);
        return new Criterio(nombre, valor, max, nota, null, FormatoDelInforme.porcentaje(valor, max), quien,
                ajustada, motivo, texto, texto, null, null);
    }

}
