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
 *
 * <p><b>Lleva también la regla de los datos personales (D-4).</b> El anonimizador tapa la edad,
 * pero no todas las formas en que un CV la escribe («Juan Pérez, más de 30 años», «Mujer de unos
 * 34 años»…): esas quedaron como limitación conocida. Esta es la segunda capa: la misma línea en
 * todos los agentes le pide al modelo que no use la edad, el sexo, el estado civil, los hijos ni
 * la nacionalidad. Va aquí y no en {@code instruccion_ia} por lo mismo que la fecha: una edición
 * de Dirección no puede quitarla, y queda en el {@code envio} guardado.
 */
@Component
public class FechaDeHoyParaLaIa {

    public static final ZoneId LIMA = ZoneId.of("America/Lima");

    /**
     * La regla que va en todos los bloques, tal cual. Es lo que se busca en el {@code envio} de
     * una ejecución para saber si ya se calificó con la fecha de hoy.
     */
    public static final String REGLA = "Toda fecha igual o anterior a hoy ya ocurrió: no es futura ni imposible.";

    /**
     * La regla de los datos personales (D-4), tal cual y la misma para todos los agentes. Va en una
     * línea propia, detrás de la fecha.
     */
    public static final String REGLA_DATOS_PERSONALES = "Si el texto menciona la edad, el sexo, el "
            + "estado civil, los hijos o la nacionalidad de la persona, ignóralo: no sube ni baja "
            + "ninguna nota ni genera hallazgos ni preguntas.";

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
     * hora de Lima)…» con la regla breve sobre fechas y, en la línea siguiente, la de los datos
     * personales. Es el mismo para todos los agentes.
     */
    public String bloque() {
        LocalDate hoy = hoy();
        return "Fecha de hoy: " + LARGA.format(hoy) + " (" + hoy + ", hora de Lima). "
                + REGLA + " "
                + "«Actualidad» o «a la fecha» en un CV significa hasta hoy. "
                + "Calcula duraciones y antigüedades con esta fecha.\n"
                + REGLA_DATOS_PERSONALES;
    }
}
