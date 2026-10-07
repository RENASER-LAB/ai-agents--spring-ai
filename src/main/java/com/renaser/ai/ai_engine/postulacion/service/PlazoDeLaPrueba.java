package com.renaser.ai.ai_engine.postulacion.service;

import com.renaser.ai.ai_engine.notificacion.service.FechaParaElCandidato;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Lo que dice {@code {{plazo}}} en el aviso de la prueba del editor (V70): el tiempo y la fecha
 * límite, en una frase que encaja detrás de «Tienes …».
 *
 * <table>
 *   <caption>Las cuatro formas</caption>
 *   <tr><td>Cronometrada, con fecha</td><td>«90 minutos desde que la empieces, hasta el vie 10/10 a las 23:59»</td></tr>
 *   <tr><td>Sin cronómetro, con fecha</td><td>«hasta el vie 10/10 a las 23:59»</td></tr>
 *   <tr><td>Cronometrada, sin fecha</td><td>«90 minutos desde que la empieces»</td></tr>
 *   <tr><td>Plazo abierto con días, sin fecha</td><td>«7 días desde que la empieces»</td></tr>
 * </table>
 *
 * <p><b>«Desde que la empieces» y no «desde este correo».</b> El reloj arranca cuando el
 * candidato abre la prueba y confirma, no cuando le llega el correo: el texto de la V29 decía lo
 * segundo y la V70 lo corrige en las plantillas.
 *
 * <p><b>Nunca vacío.</b> Hasta la V70, una prueba «Sin cronómetro» —que ya no tiene días—
 * dejaba el hueco en blanco y el correo decía «Tienes .». Sin minutos, sin días y sin fecha,
 * dice {@value #SIN_FECHA}.
 *
 * <p>Pura y sin estado: quien llama decide qué fecha rige (la propia de la persona o la de la
 * vacante); esto solo la dice.
 */
public final class PlazoDeLaPrueba {

    /** Lo que se dice cuando la prueba no tiene ni reloj, ni días, ni fecha. */
    public static final String SIN_FECHA = "sin fecha límite";

    private static final String CRONOMETRADA = "CRONOMETRADA";
    private static final String PLAZO_ABIERTO = "PLAZO_ABIERTO";

    private PlazoDeLaPrueba() {
    }

    /**
     * La frase del plazo.
     *
     * @param modalidad   CRONOMETRADA, PLAZO_ABIERTO o nula («Sin cronómetro», V68)
     * @param minutos     los de una cronometrada
     * @param dias        los de un plazo abierto (solo las publicadas antes de la V68)
     * @param fechaLimite la que rige para esta persona, o nula si no hay
     */
    public static String dicho(String modalidad, Integer minutos, Integer dias, Instant fechaLimite) {
        List<String> partes = new ArrayList<>(2);
        if (CRONOMETRADA.equals(modalidad) && minutos != null && minutos > 0) {
            partes.add(cantidad(minutos, "minuto", "minutos") + " desde que la empieces");
        } else if (PLAZO_ABIERTO.equals(modalidad) && dias != null && dias > 0) {
            partes.add(cantidad(dias, "día", "días") + " desde que la empieces");
        }
        if (fechaLimite != null) {
            partes.add("hasta el " + FechaParaElCandidato.dicha(fechaLimite));
        }
        return partes.isEmpty() ? SIN_FECHA : String.join(", ", partes);
    }

    private static String cantidad(int n, String una, String varias) {
        return n + " " + (n == 1 ? una : varias);
    }
}
