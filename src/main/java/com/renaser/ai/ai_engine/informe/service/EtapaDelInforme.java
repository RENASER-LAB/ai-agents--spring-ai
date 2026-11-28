package com.renaser.ai.ai_engine.informe.service;

import java.util.List;
import java.util.Locale;
import java.util.Map;

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

    /** La etapa fija del recorrido (V95): lo que deja por verificar pasa a la que le sigue. */
    private static final String PERFIL_INTEGRAL = "PERFIL_INTEGRAL";

    /** Lo pendiente según la etapa que sigue al Perfil Integral en el recorrido (V95). */
    private static final Map<String, String> POR_VERIFICAR_EN = Map.of(
            "PRUEBA_PUESTO", "Por verificar en la prueba del puesto",
            "SIMULACION", "Por verificar en la simulación de trabajo",
            "VALIDACION", "Por verificar en la validación práctica",
            "DECISION", "Por verificar en la decisión");

    /** Una etapa nueva que todavía no tiene su nombre en {@link #POR_VERIFICAR_EN}. */
    private static final String EN_LA_SIGUIENTE = "Por verificar en la etapa siguiente";

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

    /**
     * Cómo se llama lo pendiente en este informe: lo que pasa a la etapa siguiente. El de la
     * evaluación es el del recorrido predeterminado; el informe lo nombra por el recorrido de su
     * vacante con {@link #pendienteTrasElPerfil}.
     */
    public String pendiente() {
        return pendiente;
    }

    /**
     * Cómo se llama lo que el Perfil Integral deja por verificar en una vacante (V95): nombra la
     * etapa que le sigue en su recorrido —«Por verificar en la prueba del puesto» con el
     * predeterminado, «…en la simulación de trabajo», «…en la validación práctica», «…en la
     * decisión»— y, si no le sigue ninguna, la entrevista, como el último informe. Es el título de
     * lo pendiente en el informe de la evaluación y, en una vacante sin evaluación, en el del CV.
     *
     * <p>Una etapa nueva sale como «la etapa siguiente» hasta que se le pone su nombre aquí (ver la
     * guía «Cómo agregar una etapa»).
     *
     * @param recorrido los códigos de las etapas de la vacante, en su orden
     */
    public static String pendienteTrasElPerfil(List<String> recorrido) {
        int donde = recorrido == null ? -1 : recorrido.indexOf(PERFIL_INTEGRAL);
        if (donde < 0 || donde == recorrido.size() - 1) {
            return PRUEBA.pendiente();
        }
        return POR_VERIFICAR_EN.getOrDefault(recorrido.get(donde + 1), EN_LA_SIGUIENTE);
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
