package com.renaser.ai.ai_engine.informe.service;

import com.renaser.ai.ai_engine.informe.service.LoComunDelInforme.Base;

/**
 * Las frases que comparten los tres informes: por qué un informe todavía no se puede descargar,
 * quién puso una nota y cómo se llama el archivo.
 */
public final class MotivosDelInforme {

    public static final String SIN_CALIFICAR = "Todavía no tiene calificación";
    public static final String CALIFICANDO = "La IA está calificando";
    public static final String FALLO = "La calificación falló";

    private MotivosDelInforme() {
    }

    /** Quién puso la nota: la IA, una persona o el sistema (las cerradas). */
    public static String quien(String origen, boolean ajustadaAMano) {
        if (ajustadaAMano) {
            return "Persona";
        }
        if (origen == null) {
            return null;
        }
        return switch (origen.toUpperCase(java.util.Locale.ROOT)) {
            case "AGENTE", "IA" -> "IA";
            case "SISTEMA", "AUTOMATICA", "AUTOMATICO" -> "Sistema";
            default -> "Persona";
        };
    }

    /** «Informe CV - Ana Torres Quispe - Analista de Recursos Humanos.pdf». */
    public static String nombreDeArchivo(EtapaDelInforme etapa, Base base) {
        String vacante = base.vacanteTitulo() == null ? "" : " - " + base.vacanteTitulo();
        return FormatoDelInforme.paraArchivo(etapa.archivo() + " - " + base.candidato() + vacante)
                + ".pdf";
    }
}
