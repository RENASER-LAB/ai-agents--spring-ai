package com.renaser.ai.ai_engine.ai.service.impl;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

/**
 * La fecha de hoy que se le dice a cada agente, en un bloque fijo.
 *
 * <p><b>Por qué existe.</b> Hasta noviembre de 2026 ningún agente recibía la fecha del día: el
 * modelo razonaba con la de su entrenamiento, anterior a 2026, y marcaba como «futuras» o
 * «imposibles» experiencias de febrero o julio de 2026 que ya habían ocurrido. Eso bajaba notas
 * del CV, creaba hallazgos falsos y, si el hallazgo era un riesgo crítico, cambiaba el grupo de
 * prioridad.
 *
 * <p><b>Va aparte de la instrucción a propósito.</b> La instrucción la edita Dirección desde el
 * panel ({@code instruccion_ia}); si la fecha viviera ahí, una edición podría quitarla. Este
 * bloque lo pone el ejecutor común entre la instrucción y el formato, igual para todos los
 * agentes, y queda dentro del {@code envio} que se guarda de cada llamada: meses después se
 * puede saber con qué fecha se calificó.
 *
 * <p><b>La fecha sale del reloj del sistema, en la zona de Lima</b>, nunca del modelo. El
 * {@link Clock} es inyectable para que las pruebas fijen el día sin fechas quemadas. Una llamada
 * que cruza la medianoche usa la del momento en que se arma el mensaje.
 */
@Component
public class FechaDeHoyParaLaIa {

    public static final ZoneId LIMA = ZoneId.of("America/Lima");

    /**
     * La regla que va en todos los bloques, tal cual. Es lo que se busca en el {@code envio} de
     * una ejecución para saber si ya se calificó con la fecha de hoy.
     */
    public static final String REGLA = "Toda fecha igual o anterior a hoy ya ocurrió: no es futura ni imposible.";

    private static final DateTimeFormatter LARGA =
            DateTimeFormatter.ofPattern("d 'de' MMMM 'de' yyyy", Locale.forLanguageTag("es-PE"));

    private final Clock reloj;

    @Autowired
    public FechaDeHoyParaLaIa() {
        this(Clock.system(LIMA));
    }

    public FechaDeHoyParaLaIa(Clock reloj) {
        this.reloj = reloj;
    }

    /** El día de hoy en Lima. */
    public LocalDate hoy() {
        return LocalDate.now(reloj.withZone(LIMA));
    }

    /**
     * El bloque tal cual va en el mensaje: «Fecha de hoy: 16 de noviembre de 2026 (2026-11-16,
     * hora de Lima)…» y la regla breve sobre fechas, la misma para todos los agentes.
     */
    public String bloque() {
        LocalDate hoy = hoy();
        return "Fecha de hoy: " + LARGA.format(hoy) + " (" + hoy + ", hora de Lima). "
                + REGLA + " "
                + "«Actualidad» o «a la fecha» en un CV significa hasta hoy. "
                + "Calcula duraciones y antigüedades con esta fecha.";
    }
}
