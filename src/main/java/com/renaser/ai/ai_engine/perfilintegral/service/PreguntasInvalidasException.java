package com.renaser.ai.ai_engine.perfilintegral.service;

import java.util.List;

/**
 * Las preguntas propias de una vacante no se pueden publicar (o guardar) así, y aquí está la
 * lista ENTERA de lo que falta (V66).
 *
 * <p>No es una {@code IllegalArgumentException} más porque su valor está en la lista: quien
 * publica necesita todos los problemas de una vez —«faltan 5 puntos», «el criterio X no
 * tiene preguntas», «ninguna opción da el máximo»— y no descubrirlos de uno en uno a rechazo
 * por intento. El manejador de errores la convierte en un 400 con la lista en
 * {@code faltas}, y el panel la pinta como lista.
 */
public class PreguntasInvalidasException extends RuntimeException {

    private final transient List<String> faltas;

    public PreguntasInvalidasException(String resumen, List<String> faltas) {
        super(resumen);
        this.faltas = List.copyOf(faltas);
    }

    public List<String> getFaltas() {
        return faltas;
    }
}
