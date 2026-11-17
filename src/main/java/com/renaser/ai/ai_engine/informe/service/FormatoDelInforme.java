package com.renaser.ai.ai_engine.informe.service;

import com.renaser.ai.ai_engine.postulacion.service.impl.AnonimizadorCv;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

/**
 * Cómo se escriben las cifras y las fechas en un informe: en español del Perú y a la hora de
 * Lima, que es donde se leen. Una nota que falta se escribe «—», nunca 0.
 */
public final class FormatoDelInforme {

    public static final String HUECO = "—";
    public static final ZoneId LIMA = ZoneId.of("America/Lima");
    private static final Locale ES = Locale.forLanguageTag("es-PE");
    private static final DateTimeFormatter FECHA_HORA =
            DateTimeFormatter.ofPattern("dd/MM/yyyy · HH:mm", ES).withZone(LIMA);
    private static final DateTimeFormatter FECHA = DateTimeFormatter.ofPattern("dd/MM/yyyy", ES)
            .withZone(LIMA);
    private static final DateTimeFormatter DIA_Y_HORA =
            DateTimeFormatter.ofPattern("dd/MM 'a las' HH:mm", ES).withZone(LIMA);

    private FormatoDelInforme() {
    }

    /** «74», «3,5», «82,25»: sin ceros de más y con coma decimal. Nula → «—». */
    public static String nota(BigDecimal valor) {
        if (valor == null) {
            return HUECO;
        }
        BigDecimal redondeada = valor.setScale(2, RoundingMode.HALF_UP).stripTrailingZeros();
        DecimalFormat formato = new DecimalFormat("0.##", DecimalFormatSymbols.getInstance(ES));
        formato.setRoundingMode(RoundingMode.HALF_UP);
        return formato.format(redondeada);
    }

    /** La nota de la etapa en el anillo: entera si es sobre 100 (74,4 → 74). */
    public static String notaRedonda(BigDecimal valor) {
        if (valor == null) {
            return HUECO;
        }
        return valor.setScale(0, RoundingMode.HALF_UP).toPlainString();
    }

    /** Qué parte de la barra se llena, de 0 a 100. */
    public static Integer porcentaje(BigDecimal nota, BigDecimal maximo) {
        if (nota == null || maximo == null || maximo.signum() <= 0) {
            return null;
        }
        int pct = nota.multiply(BigDecimal.valueOf(100))
                .divide(maximo, 0, RoundingMode.HALF_UP).intValue();
        return Math.max(0, Math.min(100, pct));
    }

    public static String fechaHora(Instant instante) {
        return instante == null ? HUECO : FECHA_HORA.format(instante);
    }

    public static String fecha(Instant instante) {
        return instante == null ? HUECO : FECHA.format(instante);
    }

    public static String diaYHora(Instant instante) {
        return instante == null ? HUECO : DIA_Y_HORA.format(instante);
    }

    /** «4 años y 2 meses», «8 meses», «1 año». Nulo → nulo (quien lo pinta dice «sin dato»). */
    public static String duracion(Integer meses) {
        if (meses == null) {
            return null;
        }
        int anios = meses / 12;
        int resto = meses % 12;
        String deAnios = anios == 1 ? "1 año" : anios + " años";
        String deMeses = resto == 1 ? "1 mes" : resto + " meses";
        if (anios == 0) {
            return deMeses;
        }
        return resto == 0 ? deAnios : deAnios + " y " + deMeses;
    }

    /** Vacío o en blanco cuenta como que no hay. */
    public static boolean hay(String texto) {
        return texto != null && !texto.isBlank();
    }

    /** El texto sin espacios de más a los lados, o nulo si no hay. */
    public static String limpio(String texto) {
        return hay(texto) ? texto.strip() : null;
    }

    /**
     * Un texto de la IA para enseñar, o nulo si trae la marca del anonimizador.
     *
     * <p>«Tiene más de [DATO NO UTILIZABLE] de experiencia» habla de un dato que la IA no vio:
     * enseñarlo tal cual pone la marca en el panel y en el PDF. Va solo en lo que escribe la IA
     * a partir del CV (justificaciones, el perfil, el resumen del retrato); las respuestas del
     * candidato no pasan por aquí.
     */
    public static String sinMarca(String texto) {
        return AnonimizadorCv.traeLaMarca(texto) ? null : limpio(texto);
    }

    /** Lo que se escribe en el nombre de un archivo: sin barras ni signos que el disco rechace. */
    public static String paraArchivo(String texto) {
        if (texto == null) {
            return "";
        }
        StringBuilder salida = new StringBuilder(texto.length());
        for (char c : texto.toCharArray()) {
            if ("\\/:*?\"<>|".indexOf(c) >= 0 || Character.isISOControl(c)) {
                salida.append(' ');
            } else {
                salida.append(c);
            }
        }
        return salida.toString().replaceAll(" {2,}", " ").strip();
    }
}
