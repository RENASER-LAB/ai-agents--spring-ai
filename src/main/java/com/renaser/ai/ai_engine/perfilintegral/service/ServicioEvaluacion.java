package com.renaser.ai.ai_engine.perfilintegral.service;

import com.renaser.ai.ai_engine.perfilintegral.dto.DtosEvaluacion.EntregaResponse;
import com.renaser.ai.ai_engine.perfilintegral.dto.DtosEvaluacion.EvaluacionCandidato;
import com.renaser.ai.ai_engine.perfilintegral.dto.DtosEvaluacion.Responder;
import com.renaser.ai.ai_engine.seguridad.dto.ContextoUsuario;

import java.util.UUID;

/**
 * La evaluación desde el lado del candidato.
 *
 * <p>Todo entra por el UUID de la postulación, no por el id de la evaluación: es lo que el
 * candidato conoce, y evita que pueda pedir la de otra persona probando números. Igual que
 * el resto del portal, "no es tuya" se responde con 404, no con 403.
 */
public interface ServicioEvaluacion {

    /**
     * La crea el sistema al postular, atada a la versión del banco de ese momento.
     *
     * <p>La versión se fija aquí y no después a propósito: es lo que permite reproducir el
     * examen tal como se rindió aunque el banco cambie (RF-138).
     */
    Long crearAlPostular(Long organizacionId, Long usuarioId, Long plantillaEvaluacionId,
                         String nivelPuestoCodigo);

    EvaluacionCandidato ver(ContextoUsuario quien, UUID uuidPostulacion);

    /** Marca el inicio y arma el orden de preguntas si es la primera vez. */
    EvaluacionCandidato iniciar(ContextoUsuario quien, UUID uuidPostulacion);

    void responder(ContextoUsuario quien, UUID uuidPostulacion, Long preguntaId, Responder datos);

    /** Cierra la evaluación y manda la postulación a calificarse. */
    EntregaResponse entregar(ContextoUsuario quien, UUID uuidPostulacion);

    /**
     * Llamado por el sondeo: cierra las evaluaciones cuyo plazo pasó sin que nadie las
     * entregara. La postulación se cierra con motivo PLAZO_VENCIDO (docs/03-ESTADOS-POSTULACION.md).
     */
    void cerrarVencidas();
}
