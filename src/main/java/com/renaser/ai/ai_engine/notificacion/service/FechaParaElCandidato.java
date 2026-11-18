package com.renaser.ai.ai_engine.notificacion.service;

import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;

/**
 * Una fecha dicha como la lee el candidato: «vie 10/10 a las 23:59», en hora de Lima.
 *
 * <p>La usan los dos textos que le dicen hasta cuándo tiene: {@code {{plazo}}} del aviso de la
 * prueba y {@code {{vence}}} de los recordatorios (V70). Tienen que decirlo igual: el correo de
 * la prueba y el recordatorio del día anterior hablan de la misma fecha, y si una dijera
 * «10/10 23:59» y la otra «viernes 10 de octubre» parecerían dos plazos distintos.
 *
 * <p>⚠️ <b>Siempre en Lima, nunca en la zona del servidor.</b> El servidor corre en UTC: una
 * fecha límite a las 23:59 de Lima es las 04:59 del día siguiente en UTC, y decirla sin
 * convertir le quitaría al candidato un día entero de su plazo.
 *
 * <p>⚠️ <b>Los días van escritos a mano</b> y no con el {@code Locale}: según la versión de
 * Java, «es» abrevia el viernes «vie» o «vie.», y el mismo correo no puede cambiar de forma al
 * actualizar el servidor.
 */
public final class FechaParaElCandidato {

    public static final ZoneId LIMA = ZoneId.of("America/Lima");

    private static final String[] DIAS = {"lun", "mar", "mié", "jue", "vie", "sáb", "dom"};
    private static final String[] MESES =
            {"ene", "feb", "mar", "abr", "may", "jun", "jul", "ago", "sep", "oct", "nov", "dic"};
    private static final DateTimeFormatter DIA_Y_MES = DateTimeFormatter.ofPattern("dd/MM");
    private static final DateTimeFormatter HORA = DateTimeFormatter.ofPattern("HH:mm");

    private FechaParaElCandidato() {
    }

    /** «vie 10/10 a las 23:59». */
    public static String dicha(Instant instante) {
        ZonedDateTime enLima = instante.atZone(LIMA);
        return DIAS[enLima.getDayOfWeek().getValue() - 1] + " " + DIA_Y_MES.format(enLima)
                + " a las " + HORA.format(enLima);
    }

    /**
     * «jue 20 nov, 18:00», con el año solo si no es el de {@code ahora}: «jue 20 nov 2027, 18:00».
     *
     * <p>Es como lo dicen los tres avisos de «más tiempo» (V89) —correo, campana y WhatsApp— y
     * el panel: los tres tienen que decir la misma fecha con las mismas palabras.
     */
    public static String conDiaYMes(Instant instante, Instant ahora) {
        ZonedDateTime enLima = instante.atZone(LIMA);
        boolean otroAno = enLima.getYear() != ahora.atZone(LIMA).getYear();
        return DIAS[enLima.getDayOfWeek().getValue() - 1] + " " + enLima.getDayOfMonth() + " "
                + MESES[enLima.getMonthValue() - 1] + (otroAno ? " " + enLima.getYear() : "")
                + ", " + HORA.format(enLima);
    }
}
