package com.renaser.ai.ai_engine.colaborador.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;

/**
 * El día de hoy en Lima, que es el que manda en la ficha del colaborador.
 *
 * <p>Los estados salen de las fechas y la empresa está en Lima: a las 20:00 de Lima ya es
 * mañana en UTC, y con el día del servidor un cese «de hoy» dejaría de estar activo cinco
 * horas antes de tiempo. Con un {@link Clock} inyectable para que las pruebas muevan el día
 * sin fechas quemadas.
 */
@Component
public class HoyEnLima {

    public static final ZoneId LIMA = ZoneId.of("America/Lima");

    private final Clock reloj;

    @Autowired
    public HoyEnLima() {
        this(Clock.system(LIMA));
    }

    public HoyEnLima(Clock reloj) {
        this.reloj = reloj;
    }

    public LocalDate hoy() {
        return LocalDate.now(reloj.withZone(LIMA));
    }
}
