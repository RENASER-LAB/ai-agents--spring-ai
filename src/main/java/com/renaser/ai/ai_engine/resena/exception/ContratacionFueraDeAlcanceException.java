package com.renaser.ai.ai_engine.resena.exception;

import com.renaser.ai.ai_engine.ai.exception.ResourceNotFoundException;

/**
 * Una contratación de tu empresa que tu permiso no alcanza: un 404, no un 403.
 *
 * <p>Es un 404 como el resto del panel, pero con su propio texto, que la spec pide: a un
 * Responsable de área que abre la contratación de otra vacante de SU empresa hay que decirle
 * que está fuera de su alcance, no que no existe. Lo de otra empresa sigue saliendo con el
 * texto de siempre, que no confirma nada.
 */
public class ContratacionFueraDeAlcanceException extends ResourceNotFoundException {

    public ContratacionFueraDeAlcanceException(Long postulacionId) {
        super("Postulación", "id", postulacionId);
    }

    @Override
    public String getMessage() {
        return "Esta contratación está fuera de tu alcance: tu rol solo llega a las "
                + "contrataciones de tus vacantes";
    }
}
