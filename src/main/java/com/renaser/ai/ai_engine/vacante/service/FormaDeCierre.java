package com.renaser.ai.ai_engine.vacante.service;

import com.renaser.ai.ai_engine.notificacion.service.FechaParaElCandidato;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneOffset;

/**
 * Cómo se cierra una vacante, y qué fecha guarda cuando se cierra sola
 * (specs/Ready/perfil-integral-hasta-el-cierre-de-la-vacante, V78).
 *
 * <p>Dos formas: <b>Permanente</b> —la cierra alguien del equipo— y <b>Fecha límite</b> —se
 * cierra sola ese día a las 23:59:59 de Lima—. «Por plazas» desapareció: la restricción de la
 * base todavía la admite por las filas de antes, pero ninguna vacante nueva la guarda y la V78
 * pasó las que había a Permanente.
 *
 * <p>⚠️ <b>El día es el de Lima, y la hora se pone aquí.</b> El panel elige un día; si viajara
 * como la medianoche UTC de ese día, en Lima serían las 19:00 del día anterior y la vacante se
 * cerraría cinco horas antes de lo que eligió el equipo. Por eso el servidor no se fía de la hora
 * que llega: toma el día de Lima del instante recibido y guarda las 23:59:59 de ese día. Lima no
 * cambia de hora en el año, pero se calcula con la zona y no con un desfase fijo.
 *
 * <p>⚠️ <b>La medianoche UTC exacta se lee como el día UTC.</b> Es como mandaba el día el panel
 * hasta este cambio ({@code new Date('AAAA-MM-DD').toISOString()}), y como lo puede seguir
 * mandando una pestaña abierta desde antes del despliegue: leída en Lima sería el día anterior.
 * Nadie elige las 19:00:00.000 de Lima en un selector de días, así que no se confunde con nada.
 * Es la misma regla con la que la V78 pasó las fechas guardadas.
 *
 * <p>Pura y sin estado: el reloj lo pone quien llama.
 */
public final class FormaDeCierre {

    /** La cierra alguien del equipo, a mano. Es la de por defecto. */
    public static final String PERMANENTE = "PERMANENTE";
    /** Se cierra sola en {@code cierra_en}. */
    public static final String FECHA = "FECHA";

    /** Sin día con «Fecha límite». */
    public static final String SIN_DIA = "Elige el día en que cierra la vacante.";
    /** Un día anterior a hoy, en Lima. */
    public static final String DIA_PASADO = "Esa fecha ya pasó: elige un día a partir de hoy.";
    /** Cualquier forma que no sea una de las dos, por ejemplo «PLAZAS». */
    public static final String FORMA_DESCONOCIDA =
            "Elige cómo se cierra la vacante: Permanente o Fecha límite.";

    /** La última hora del día en que se cierra. */
    private static final LocalTime ULTIMO_SEGUNDO = LocalTime.of(23, 59, 59);

    private FormaDeCierre() {
    }

    /** Si cierra sola por su fecha. */
    public static boolean esPorFecha(String tipoCierre) {
        return FECHA.equals(limpia(tipoCierre));
    }

    /**
     * Las 23:59:59 de Lima del día de Lima en que cae {@code instante}; nulo si no hay instante.
     *
     * <p>Idempotente: aplicada a lo que ya devolvió, devuelve lo mismo. Es lo que hace que
     * guardar dos veces la misma fecha no sea un cambio.
     */
    public static Instant alFinalDelDia(Instant instante) {
        if (instante == null) {
            return null;
        }
        return diaDe(instante).atTime(ULTIMO_SEGUNDO).atZone(FechaParaElCandidato.LIMA).toInstant();
    }

    /**
     * El día que el equipo eligió: el de Lima, salvo la medianoche UTC exacta, que es el día
     * UTC (ver el encabezado).
     */
    static LocalDate diaDe(Instant instante) {
        if (instante.atOffset(ZoneOffset.UTC).toLocalTime().equals(LocalTime.MIDNIGHT)) {
            return instante.atOffset(ZoneOffset.UTC).toLocalDate();
        }
        return instante.atZone(FechaParaElCandidato.LIMA).toLocalDate();
    }

    /**
     * Lo que impide guardar esta forma de cierre, o nulo si se puede.
     *
     * <p>Con «Fecha límite» hace falta un día, y no anterior a hoy en Lima: hoy vale, porque se
     * cierra a las 23:59:59. Permanente no pide nada.
     *
     * @param ahora el reloj de quien pregunta
     */
    public static String falta(String tipoCierre, Instant cierraEn, Instant ahora) {
        String forma = limpia(tipoCierre);
        if (PERMANENTE.equals(forma)) {
            return null;
        }
        if (!FECHA.equals(forma)) {
            return FORMA_DESCONOCIDA;
        }
        return faltaDeLaFecha(cierraEn, ahora);
    }

    /** Lo mismo, solo para el día: vacío o anterior a hoy en Lima. */
    public static String faltaDeLaFecha(Instant cierraEn, Instant ahora) {
        if (cierraEn == null) {
            return SIN_DIA;
        }
        LocalDate hoy = ahora.atZone(FechaParaElCandidato.LIMA).toLocalDate();
        return diaDe(cierraEn).isBefore(hoy) ? DIA_PASADO : null;
    }

    private static String limpia(String valor) {
        return valor == null ? "" : valor.trim();
    }
}
