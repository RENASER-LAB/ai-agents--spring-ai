package com.renaser.ai.ai_engine.organizacion.service;

import com.renaser.ai.ai_engine.organizacion.entity.Organizacion;

import java.util.Arrays;
import java.util.Locale;
import java.util.Set;

/**
 * Los instrumentos de evaluación que una empresa puede leer prestados de la plataforma, o
 * personalizar: los pesos y las plantillas de evaluación.
 *
 * <p>Cada uno sabe leer y escribir su bandera en {@link Organizacion}: así ni el
 * resolutor ni la personalización tienen un switch que se olvide de actualizar cuando
 * aparezca un instrumento nuevo.
 *
 * <p>⚠️ <b>El banco y las pruebas ya no están aquí</b> (V74). Dejaron de personalizarse en la
 * V67 y de prestarse en la V74: el banco y las pruebas de una empresa son siempre los suyos,
 * y se piden por su propia organización, sin resolutor. Una vacante vieja que leía el banco
 * de RENASER lo tiene fijado en {@code vacante.version_banco_fija_id}. Que no existan como
 * valor hace imposible volver a preguntarle al resolutor por ellos.
 */
public enum Instrumento {

    PESOS {
        @Override public boolean esPropio(Organizacion organizacion) {
            return organizacion.isPesosPropios();
        }
        @Override public void poner(Organizacion organizacion, boolean propio) {
            organizacion.setPesosPropios(propio);
        }
    },
    PLANTILLA_EVALUACION {
        @Override public boolean esPropio(Organizacion organizacion) {
            return organizacion.isPlantillasEvaluacionPropias();
        }
        @Override public void poner(Organizacion organizacion, boolean propio) {
            organizacion.setPlantillasEvaluacionPropias(propio);
        }
    };

    /**
     * Lo que contesta la API a quien pide personalizar el banco o las pruebas (V67, decisión
     * 13): un 400 que dice por qué, y no el «no existe el instrumento» de una errata.
     */
    public static final String YA_NO_EXISTE = "Esta personalización ya no existe: cada empresa "
            + "escribe sus preguntas y su prueba técnica en cada vacante.";

    /** Los nombres que tuvieron el banco y las pruebas en la API. */
    private static final Set<String> RETIRADOS = Set.of("BANCO", "PRUEBA");

    public abstract boolean esPropio(Organizacion organizacion);

    public abstract void poner(Organizacion organizacion, boolean propio);

    /**
     * El instrumento que nombra un texto de la API, sin distinguir mayúsculas.
     *
     * @throws IllegalArgumentException con {@link #YA_NO_EXISTE} si es el banco o las pruebas,
     *                                  o con la lista de los válidos si no es ninguno
     */
    public static Instrumento deTexto(String crudo) {
        String nombre = crudo == null ? "" : crudo.strip().toUpperCase(Locale.ROOT);
        if (RETIRADOS.contains(nombre)) {
            throw new IllegalArgumentException(YA_NO_EXISTE);
        }
        try {
            return valueOf(nombre);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("No existe el instrumento «" + crudo
                    + "»; los válidos son " + Arrays.toString(values()), e);
        }
    }
}
