package com.renaser.ai.ai_engine.resena.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Instant;

/**
 * La hora de las reseñas: manda el reloj del servidor.
 *
 * <p>Existe para que los plazos de 30 días se puedan probar sin fechas quemadas —las fechas
 * quemadas caducan—: la prueba monta los servicios con un {@link Clock} fijo y mueve el
 * reloj en lugar de esperar un mes.
 */
@Component
public class RelojDeResenas {

    private final Clock reloj;

    @Autowired
    public RelojDeResenas() {
        this(Clock.systemUTC());
    }

    public RelojDeResenas(Clock reloj) {
        this.reloj = reloj;
    }

    public Instant ahora() {
        return reloj.instant();
    }
}
