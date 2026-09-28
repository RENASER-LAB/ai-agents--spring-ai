package com.renaser.ai.ai_engine.resena.service;

import com.renaser.ai.ai_engine.resena.dto.DtosResena.Barra;
import com.renaser.ai.ai_engine.resena.dto.DtosResena.ResumenResenas;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Las reglas de las reseñas que no dependen de nadie: plazos, límites y cuentas.
 *
 * <p>Viven juntas para que el panel, el portal y la moderación no escriban cada uno su
 * versión del «30 días» o del «de 30 a 1000 caracteres». El reloj no está aquí: lo recibe
 * cada servicio, y estas funciones reciben el instante ya leído.
 */
public final class ReglasDeLaResena {

    /** Escribir la reseña, editarla o borrarla, y editar o borrar la respuesta. */
    public static final Duration UN_MES = Duration.ofDays(30);

    public static final int MIN_RESENA = 30;
    public static final int MAX_RESENA = 1000;
    public static final int MIN_RESPUESTA = 30;
    public static final int MAX_RESPUESTA = 500;
    public static final int MAX_COMENTARIO = 500;
    public static final int MAX_NOTA_REVISION = 1000;

    public static final String RESENAR = "resenar_contratado";
    public static final String VER = "ver_resenas_candidato";
    public static final String MODERAR = "moderar_resenas";

    public static final String CONTRATADO = "CONTRATADO";

    public static final String OTRO = "OTRO";
    public static final Set<String> MOTIVOS =
            Set.of("OFENSIVA", "DATOS_PERSONALES", "DISCRIMINATORIA", "FALSA", OTRO);

    private static final ZoneId LIMA = ZoneId.of("America/Lima");
    private static final DateTimeFormatter FECHA =
            DateTimeFormatter.ofPattern("dd/MM/yyyy", Locale.forLanguageTag("es-PE"));

    private ReglasDeLaResena() {
    }

    /** Desde cuándo se puede escribir: la contratación más un mes. */
    public static Instant abreEn(Instant contratadoEn) {
        return contratadoEn.plus(UN_MES);
    }

    /** Hasta cuándo la empresa puede cambiarla: la PRIMERA publicación más un mes. */
    public static Instant editableHasta(Instant publicadaEn) {
        return publicadaEn.plus(UN_MES);
    }

    /** Si ya llegó el momento. Manda el reloj del servidor. */
    public static boolean yaLlego(Instant momento, Instant ahora) {
        return !ahora.isBefore(momento);
    }

    /**
     * Las estrellas, que tienen que ser un entero de 1 a 5.
     *
     * <p>Llegan como número para poder rechazar un 4,5: como {@code Integer}, Jackson lo
     * habría truncado a 4 sin avisar.
     */
    public static int estrellasValidas(BigDecimal estrellas) {
        if (estrellas == null) {
            throw new IllegalArgumentException("Elige cuántas estrellas le das, de 1 a 5");
        }
        BigDecimal limpio = estrellas.stripTrailingZeros();
        if (limpio.scale() > 0 || limpio.compareTo(BigDecimal.ONE) < 0
                || limpio.compareTo(BigDecimal.valueOf(5)) > 0) {
            throw new IllegalArgumentException("Las estrellas son un número entero de 1 a 5");
        }
        return limpio.intValueExact();
    }

    /**
     * Un texto obligatorio, sin contar los espacios de los extremos. Un texto que solo tiene
     * espacios cuenta como vacío.
     *
     * <p>Se cuentan caracteres de verdad (puntos de código) y no unidades de Java: es lo mismo
     * que cuenta la base con {@code char_length}, y un emoji no gasta dos.
     *
     * @return el texto sin los espacios de los extremos, que es como se guarda
     */
    public static String textoValido(String texto, int minimo, int maximo, String que) {
        String limpio = texto == null ? "" : texto.strip();
        int largo = limpio.codePointCount(0, limpio.length());
        if (largo == 0) {
            throw new IllegalArgumentException(que + " es obligatoria");
        }
        if (largo < minimo) {
            throw new IllegalArgumentException(que + " necesita al menos " + minimo
                    + " caracteres; lleva " + largo);
        }
        if (largo > maximo) {
            throw new IllegalArgumentException(que + " admite hasta " + maximo
                    + " caracteres; lleva " + largo);
        }
        return limpio;
    }

    /** El motivo de un reporte y su comentario, obligatorio con «Otro motivo». */
    public static String motivoValido(String motivo) {
        if (motivo == null || !MOTIVOS.contains(motivo)) {
            throw new IllegalArgumentException("Elige el motivo del reporte");
        }
        return motivo;
    }

    /** @return el comentario sin los espacios de los extremos, o nulo si no hay */
    public static String comentarioValido(String motivo, String comentario) {
        String limpio = comentario == null ? "" : comentario.strip();
        if (OTRO.equals(motivo) && limpio.isEmpty()) {
            throw new IllegalArgumentException("Con «Otro motivo», cuéntanos qué problema tiene");
        }
        if (limpio.codePointCount(0, limpio.length()) > MAX_COMENTARIO) {
            throw new IllegalArgumentException("El comentario admite hasta " + MAX_COMENTARIO
                    + " caracteres");
        }
        return limpio.isEmpty() ? null : limpio;
    }

    /**
     * El promedio con un decimal, redondeado a la mitad hacia arriba: 5, 5 y 4 son 4,7.
     *
     * <p>Se redondea aquí, una vez, y la tabla ordena por lo mismo que enseña: dos filas que
     * dicen «4,7» empatan, y a igualdad decide el número de reseñas.
     */
    public static BigDecimal promedio(long suma, long cantidad) {
        if (cantidad <= 0) {
            return null;
        }
        return BigDecimal.valueOf(suma)
                .divide(BigDecimal.valueOf(cantidad), 1, RoundingMode.HALF_UP);
    }

    /** El resumen de unas estrellas: promedio, cantidad y las cinco barras de 5★ a 1★. */
    public static ResumenResenas resumen(Collection<Integer> estrellas) {
        int[] porEstrella = new int[6];
        long suma = 0;
        for (Integer e : estrellas) {
            porEstrella[e]++;
            suma += e;
        }
        List<Barra> reparto = new ArrayList<>(5);
        for (int e = 5; e >= 1; e--) {
            reparto.add(new Barra(e, porEstrella[e]));
        }
        return new ResumenResenas(promedio(suma, estrellas.size()), estrellas.size(), reparto);
    }

    /** Una fecha para un aviso o un mensaje: «28/10/2026», en la hora de Lima. */
    public static String fecha(Instant momento) {
        return FECHA.format(momento.atZone(LIMA).toLocalDate());
    }
}
