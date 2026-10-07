package com.renaser.ai.ai_engine.postulacion.service;

import com.renaser.ai.ai_engine.notificacion.service.FechaParaElCandidato;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalTime;
import java.time.ZonedDateTime;
import java.util.Optional;

/**
 * Cuándo le toca a un turno su recordatorio (V70). Pura: el reloj lo pone quien llama.
 *
 * <p>Las reglas de la spec, todas aquí y en este orden:
 * <ol>
 *   <li><b>Dos como máximo por turno</b>: uno a las N horas de entrar (24 por defecto) y otro N
 *       horas antes de que venza (24 por defecto).</li>
 *   <li><b>Nunca de noche</b>: solo entre las 8:00 y las 21:00 de Lima. Lo que tocaba fuera de
 *       esa franja sale a las 8:00.</li>
 *   <li><b>Si caen a menos de 12 horas</b> el uno del otro, solo sale el del plazo. A 12 horas
 *       o más salen los dos, en el orden en que toquen: que el del plazo salga primero no gasta
 *       el de las 24 horas.</li>
 *   <li><b>Sin plazo que recordar</b> si al entrar ya quedaban menos de esas horas —acaba de
 *       recibir el aviso con la fecha— o si ya venció: eso lo cierra el sondeo.</li>
 * </ol>
 *
 * <p>⚠️ <b>Las horas son de Lima</b>, no del servidor: comparar «8:00» en UTC desplazaría la
 * franja cinco horas y los correos saldrían a las 3 de la madrugada.
 */
public final class CalendarioDeRecordatorios {

    /** Desde qué hora de Lima salen. */
    public static final LocalTime ABRE = LocalTime.of(8, 0);
    /** Hasta qué hora de Lima salen (sin incluirla). */
    public static final LocalTime CIERRA = LocalTime.of(21, 0);
    /** A menos de esto el uno del otro, solo sale el del plazo. */
    public static final Duration DEMASIADO_CERCA = Duration.ofHours(12);

    /** Cuál de los dos. */
    public enum Cual { TRAS_ENTRAR, ANTES_DEL_PLAZO }

    /**
     * Lo que toca mandar ahora.
     *
     * @param cual          el recordatorio que sale
     * @param omitirElOtro  si, al salir el del plazo, el de las 24 horas queda gastado sin salir:
     *                      solo cuando ese ya no podría salir (a menos de 12 horas del del plazo,
     *                      o después de que venza)
     */
    public record Toca(Cual cual, boolean omitirElOtro) {
    }

    private CalendarioDeRecordatorios() {
    }

    /** Si a esta hora pueden salir recordatorios: de 8:00 a 21:00 de Lima. */
    public static boolean enHorario(Instant ahora) {
        LocalTime hora = ahora.atZone(FechaParaElCandidato.LIMA).toLocalTime();
        return !hora.isBefore(ABRE) && hora.isBefore(CIERRA);
    }

    /** Lo mismo, llevado a la franja: lo de la noche, a las 8:00 de la mañana siguiente. */
    public static Instant enFranja(Instant cuando) {
        ZonedDateTime enLima = cuando.atZone(FechaParaElCandidato.LIMA);
        LocalTime hora = enLima.toLocalTime();
        if (hora.isBefore(ABRE)) {
            return enLima.with(ABRE).toInstant();
        }
        if (!hora.isBefore(CIERRA)) {
            return enLima.plusDays(1).with(ABRE).toInstant();
        }
        return cuando;
    }

    /**
     * Qué recordatorio le toca a este turno ahora, si alguno.
     *
     * @param turnoDesde      cuándo se abrió el turno
     * @param venceEn         la fecha que rige, o {@code null} si no tiene
     * @param ahora           el momento del sondeo
     * @param trasEntrar      a cuántas horas de entrar sale el primero
     * @param antesDelPlazo   cuántas horas antes de vencer sale el segundo
     * @param yaSalioElPrimero  si el de las 24 horas ya salió o se omitió en este turno
     * @param yaSalioElDelPlazo si el del plazo ya salió para ESTA fecha
     */
    public static Optional<Toca> queToca(Instant turnoDesde, Instant venceEn, Instant ahora,
                                         Duration trasEntrar, Duration antesDelPlazo,
                                         boolean yaSalioElPrimero, boolean yaSalioElDelPlazo) {
        if (!enHorario(ahora) || (venceEn != null && !ahora.isBefore(venceEn))) {
            return Optional.empty();
        }
        Instant delPlazo = momentoDelPlazo(turnoDesde, venceEn, antesDelPlazo);
        Instant delPrimero = enFranja(turnoDesde.plus(trasEntrar));
        boolean primeroPendiente = !yaSalioElPrimero
                && primeroPuedeSalir(delPrimero, delPlazo, venceEn);

        if (delPlazo != null && !yaSalioElDelPlazo && !ahora.isBefore(delPlazo)) {
            return Optional.of(new Toca(Cual.ANTES_DEL_PLAZO, !yaSalioElPrimero && !primeroPendiente));
        }
        if (primeroPendiente && !ahora.isBefore(delPrimero)) {
            return Optional.of(new Toca(Cual.TRAS_ENTRAR, false));
        }
        return Optional.empty();
    }

    /**
     * Si el de las 24 horas tiene sitio: antes de que venza y a 12 horas o más del del plazo.
     * Justo a 12 horas cuenta como lejos: la spec solo deja uno «a menos de 12 h».
     */
    private static boolean primeroPuedeSalir(Instant delPrimero, Instant delPlazo, Instant venceEn) {
        boolean antesDeVencer = venceEn == null || delPrimero.isBefore(venceEn);
        boolean demasiadoCerca = delPlazo != null
                && Duration.between(delPrimero, delPlazo).abs().compareTo(DEMASIADO_CERCA) < 0;
        return antesDeVencer && !demasiadoCerca;
    }

    /**
     * Cuándo sale el del plazo, ya en la franja; {@code null} si no hay plazo que recordar.
     *
     * <p>No lo hay sin fecha, ni cuando al entrar ya quedaba menos de ese margen (el aviso de
     * entrada acaba de decirle la fecha), ni cuando llevarlo a la franja lo empujaría más allá
     * del vencimiento.
     */
    static Instant momentoDelPlazo(Instant turnoDesde, Instant venceEn, Duration antesDelPlazo) {
        if (venceEn == null) {
            return null;
        }
        Instant nominal = venceEn.minus(antesDelPlazo);
        if (!nominal.isAfter(turnoDesde)) {
            return null;
        }
        Instant efectivo = enFranja(nominal);
        return efectivo.isBefore(venceEn) ? efectivo : null;
    }
}
