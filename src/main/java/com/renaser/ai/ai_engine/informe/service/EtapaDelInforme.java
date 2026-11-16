package com.renaser.ai.ai_engine.informe.service;

import java.util.Locale;

/**
 * Los tres informes, cada uno de su etapa, con el permiso que pide.
 *
 * <p><b>El permiso es el de la pantalla que enseña esos datos</b>: el del CV es el del Perfil
 * Integral, el de la evaluación el de ver sus respuestas y el de la prueba el de abrir la ficha.
 * Sin él no hay botón, y la petición directa recibe 403.
 */
public enum EtapaDelInforme {

    CV("cv", "ver_perfil_integral", "Informe del currículum", "Informe CV",
            "1 - Informe del CV.pdf", "Por verificar en la evaluación"),
    EVALUACION("evaluacion", "ver_respuestas_evaluacion", "Informe de la evaluación",
            "Informe evaluación", "2 - Informe de la evaluación.pdf",
            "Por verificar en la prueba del puesto"),
    PRUEBA("prueba", "abrir_ficha_candidato", "Informe de la prueba del puesto", "Informe prueba",
            "3 - Informe de la prueba del puesto.pdf", "Por verificar en la entrevista");

    private final String ruta;
    private final String permiso;
    private final String titulo;
    private final String archivo;
    private final String enElZip;
    private final String pendiente;

    EtapaDelInforme(String ruta, String permiso, String titulo, String archivo, String enElZip,
                    String pendiente) {
        this.ruta = ruta;
        this.permiso = permiso;
        this.titulo = titulo;
        this.archivo = archivo;
        this.enElZip = enElZip;
        this.pendiente = pendiente;
    }

    public String ruta() {
        return ruta;
    }

    public String permiso() {
        return permiso;
    }

    public String titulo() {
        return titulo;
    }

    /** «Informe CV», el principio del nombre del archivo. */
    public String archivo() {
        return archivo;
    }

    /** El nombre dentro del .zip: «1 - Informe del CV.pdf». */
    public String enElZip() {
        return enElZip;
    }

    /** Cómo se llama lo pendiente en este informe: lo que pasa a la etapa siguiente. */
    public String pendiente() {
        return pendiente;
    }

    /** La de la ruta ({@code cv}, {@code evaluacion}, {@code prueba}) o su nombre. */
    public static EtapaDelInforme de(String valor) {
        String limpio = valor == null ? "" : valor.trim().toLowerCase(Locale.ROOT);
        for (EtapaDelInforme etapa : values()) {
            if (etapa.ruta.equals(limpio) || etapa.name().equalsIgnoreCase(limpio)) {
                return etapa;
            }
        }
        throw new IllegalArgumentException("No hay informe de «" + valor
                + "». Los que hay: cv, evaluacion y prueba.");
    }
}
