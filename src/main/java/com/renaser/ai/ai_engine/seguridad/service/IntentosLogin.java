package com.renaser.ai.ai_engine.seguridad.service;

import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

// Bloqueo temporal tras varios intentos fallidos seguidos. BCrypt solo no frena la
// fuerza bruta. En memoria: si el proceso se reinicia, el contador vuelve a cero,
// lo cual es aceptable para este umbral.
@Component
public class IntentosLogin {

    private record Estado(int fallos, Instant bloqueadoHasta) {}

    private final Map<String, Estado> porCorreo = new ConcurrentHashMap<>();

    /**
     * Segundos que faltan para poder reintentar, o 0 si no está bloqueado.
     *
     * <p>Devuelve el tiempo y no un simple sí/no porque la respuesta 429 lleva una cabecera
     * {@code Retry-After}: sin ella el frontend solo puede adivinar cuándo volver a probar.
     */
    public long segundosDeBloqueo(String correo) {
        Estado estado = porCorreo.get(clave(correo));
        if (estado == null || estado.bloqueadoHasta() == null) {
            return 0;
        }
        long faltan = Duration.between(Instant.now(), estado.bloqueadoHasta()).toSeconds();
        return Math.max(faltan, 0);
    }

    public void registrarFallo(String correo, int maximo, int minutosBloqueo) {
        porCorreo.compute(clave(correo), (k, actual) -> {
            int fallos = (actual == null ? 0 : actual.fallos()) + 1;
            Instant hasta = fallos >= maximo
                    ? Instant.now().plusSeconds(minutosBloqueo * 60L)
                    : null;
            // Al bloquear, el contador se reinicia: cumplida la espera, vuelve a empezar
            return new Estado(hasta != null ? 0 : fallos, hasta);
        });
    }

    public void registrarExito(String correo) {
        porCorreo.remove(clave(correo));
    }

    private String clave(String correo) {
        return correo == null ? "" : correo.trim().toLowerCase();
    }
}
