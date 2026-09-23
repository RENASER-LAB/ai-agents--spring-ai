package com.renaser.ai.ai_engine.seguridad.service;

import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * El tope por dirección IP de las solicitudes de contraseña nueva.
 *
 * <p>El tope por cuenta (tres enlaces por hora) no frena a quien prueba miles de correos
 * distintos desde la misma máquina: ninguno llega a su tope y la plataforma acaba mandando
 * correos a desconocidos en su nombre. Esto sí.
 *
 * <p>En memoria, como {@link IntentosLogin}: si el proceso se reinicia la cuenta vuelve a
 * cero, y para este umbral es aceptable. Ventana fija de una hora por IP; las ventanas ya
 * cumplidas se barren cuando el mapa crece, para que no acumule direcciones sin límite.
 */
@Component
public class SolicitudesPorIp {

    private static final Duration VENTANA = Duration.ofHours(1);
    /** A partir de cuántas direcciones se barren las ventanas ya cumplidas. */
    private static final int BARRER_DESDE = 10_000;

    private record Ventana(Instant desde, int cuenta) {}

    private final Map<String, Ventana> porIp = new ConcurrentHashMap<>();
    private final Clock reloj;

    public SolicitudesPorIp() {
        this(Clock.systemUTC());
    }

    SolicitudesPorIp(Clock reloj) {
        this.reloj = reloj;
    }

    /**
     * Si esta solicitud cabe en el tope de su IP, y la cuenta. La que no cabe no suma: el
     * tope es de solicitudes atendidas, no de insistencia.
     */
    public boolean admitir(String ip, int maximo) {
        Instant ahora = reloj.instant();
        if (porIp.size() > BARRER_DESDE) {
            porIp.values().removeIf(v -> !ahora.isBefore(v.desde().plus(VENTANA)));
        }
        boolean[] cabe = {false};
        porIp.compute(ip == null ? "" : ip, (k, actual) -> {
            if (actual == null || !ahora.isBefore(actual.desde().plus(VENTANA))) {
                cabe[0] = maximo > 0;
                return new Ventana(ahora, cabe[0] ? 1 : 0);
            }
            if (actual.cuenta() >= maximo) {
                return actual;
            }
            cabe[0] = true;
            return new Ventana(actual.desde(), actual.cuenta() + 1);
        });
        return cabe[0];
    }
}
