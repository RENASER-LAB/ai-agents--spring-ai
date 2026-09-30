package com.renaser.ai.ai_engine.colaborador.service;

import java.time.LocalDate;

/**
 * Los tres estados de un colaborador. No se guardan: salen de las fechas, así que «Por
 * ingresar» pasa a «Activo» el día del ingreso sin que nadie toque nada.
 *
 * <p>La misma regla está escrita en SQL en {@code ListadoDeColaboradoresRepository}; las dos
 * tienen que decir lo mismo.
 */
public enum EstadoLaboral {
    POR_INGRESAR, ACTIVO, CESADO;

    /**
     * @param cese el último día trabajado: ese día todavía es activo, y cesado desde el
     *             siguiente
     */
    public static EstadoLaboral de(LocalDate ingreso, LocalDate cese, LocalDate hoy) {
        if (ingreso.isAfter(hoy)) {
            return POR_INGRESAR;
        }
        if (cese != null && cese.isBefore(hoy)) {
            return CESADO;
        }
        return ACTIVO;
    }

    /** Si puede ser jefe de alguien: un cesado no, aunque quien ya lo tenía lo conserva. */
    public boolean puedeSerJefe() {
        return this != CESADO;
    }
}
