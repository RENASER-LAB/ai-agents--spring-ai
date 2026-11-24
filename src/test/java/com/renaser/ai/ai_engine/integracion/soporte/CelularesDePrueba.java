package com.renaser.ai.ai_engine.integracion.soporte;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * Celulares de prueba que no se repiten (V92).
 *
 * <p>Desde que WhatsApp es por cuenta, un celular es de una sola cuenta: el alta con uno que ya
 * usa otra responde 409 TELEFONO_EN_USO. Las pruebas que crean varias cuentas en la misma base ya
 * no pueden darles a todas el mismo «987 654 321»: cada una pide el suyo aquí.
 *
 * <p>Nueve cifras que empiezan por 97, como las escribe una persona en el registro: no chocan con
 * los que algunas pruebas fijan a mano (98…, 91…).
 */
public final class CelularesDePrueba {

    private static final AtomicInteger SIGUIENTE = new AtomicInteger();

    private CelularesDePrueba() {}

    /** Uno nuevo cada vez, en esta ejecución: «97» y siete cifras. */
    public static String otro() {
        return "97%07d".formatted(SIGUIENTE.getAndIncrement() % 10_000_000);
    }
}
