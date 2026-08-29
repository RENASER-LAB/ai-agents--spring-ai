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

    /**
     * El examen de la etapa técnica, cuando la vacante rinde el cuestionario CAZATALENTOS.
     *
     * <p>Se crea al ENTRAR en la etapa —no al postular— porque hasta que el equipo no lo
     * avanza no hay nada que rendir, igual que el intento de la prueba del puesto. Sin
     * plantilla y contra el cuestionario publicado de esa vacante.
     */
    Long crearTecnicaAlEntrar(Long organizacionId, Long usuarioId, Long vacanteId,
                              Integer minutosDeLaVacante);

    EvaluacionCandidato ver(ContextoUsuario quien, UUID uuidPostulacion);

    /** Marca el inicio y arma el orden de preguntas si es la primera vez. */
    EvaluacionCandidato iniciar(ContextoUsuario quien, UUID uuidPostulacion);

    void responder(ContextoUsuario quien, UUID uuidPostulacion, Long preguntaId, Responder datos);

    /** Cierra la evaluación y manda la postulación a calificarse. */
    EntregaResponse entregar(ContextoUsuario quien, UUID uuidPostulacion);

    /**
     * Llamado por el sondeo: cierra las evaluaciones cuyo plazo pasó sin que nadie las
     * entregara. La postulación se cierra con motivo PLAZO_VENCIDO (docs/03-ESTADOS-POSTULACION.md).
     *
     * <p>Solo las del perfil integral: los cuestionarios técnicos tienen su propio barrido,
     * que los entrega en vez de darlos por vencidos.
     */
    void cerrarVencidas();

    // ---------- El cuestionario técnico de la vacante (etapa 2) ----------
    // Los mismos cuatro verbos, contra el otro examen de la misma postulación.

    EvaluacionCandidato verTecnico(ContextoUsuario quien, UUID uuidPostulacion);

    /** Arma el orden la primera vez y, si la vacante fijó minutos, arranca el reloj. */
    EvaluacionCandidato iniciarTecnico(ContextoUsuario quien, UUID uuidPostulacion);

    void responderTecnico(ContextoUsuario quien, UUID uuidPostulacion, Long preguntaId,
                          Responder datos);

    /** Cierra el cuestionario y lo manda a calificar por el evaluador técnico. */
    EntregaResponse entregarTecnico(ContextoUsuario quien, UUID uuidPostulacion);

    /**
     * Llamado por el sondeo: los cuestionarios técnicos sin tiempo se entregan como estén.
     *
     * <p>No se dan por vencidos como los del banco: lo que el candidato alcanzó a escribir se
     * califica, y lo que dejó en blanco cuenta cero. Es lo que hace la prueba del puesto con
     * su {@code entregarVencidos}, y la razón es la misma — hay trabajo hecho que medir.
     */
    void entregarTecnicasVencidas();
}
