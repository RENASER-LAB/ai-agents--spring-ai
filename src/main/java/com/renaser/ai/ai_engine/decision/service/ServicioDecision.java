package com.renaser.ai.ai_engine.decision.service;

import com.renaser.ai.ai_engine.decision.dto.DtosDecision.*;
import com.renaser.ai.ai_engine.seguridad.dto.ContextoUsuario;

import java.util.List;

/**
 * La decisión final: RF-113 a RF-121.
 *
 * <p>La Puntuación Global sí es una suma ponderada determinística de las notas de etapa que
 * existan. El <b>semáforo no lo es</b>: Verde/Ámbar/Rojo/Sin datos/Reserva dependen también de
 * barreras críticas confirmadas y de si faltan etapas por calificar, y la decisión final
 * siempre la toma una persona (RF-119) — este servicio calcula una <b>propuesta</b>, nunca
 * decide solo.
 */
public interface ServicioDecision {

    List<BarreraResponse> listarBarrerasDeVacante(ContextoUsuario quien, Long vacanteId);
    Long definirBarrera(ContextoUsuario quien, Long vacanteId, CrearBarrera datos);

    /** Una persona la reporta y confirma en el mismo paso: no hay agente todavía que la detecte antes. */
    Long registrarBarreraDetectada(ContextoUsuario quien, Long postulacionId, RegistrarBarrera datos);

    /**
     * Deshacer una barrera confirmada por error. Misma audiencia que registrarla: quien puede
     * poner el bloqueo puede quitarlo, y el registro guarda quién y con qué argumento.
     *
     * <p>Existe porque desde que contratar exige que no haya barreras confirmadas, una puesta
     * por equivocación deja al candidato sin salida: no era un hueco molesto, era un candidato
     * bloqueado sin forma de desbloquearlo.
     */
    void descartarBarreraDetectada(ContextoUsuario quien, Long postulacionId,
                                   Long barreraDetectadaId, DescartarBarrera datos);

    SemaforoResponse verSemaforo(ContextoUsuario quien, Long postulacionId);

    /** RF-119: toda decisión guarda quién, cuándo y por qué. */
    void decidir(ContextoUsuario quien, Long postulacionId, Decidir datos);

    /** RF-69/117 (ámbar): pide evidencia adicional, con tope configurable. */
    void pedirEvidenciaAdicional(ContextoUsuario quien, Long postulacionId, PedirEvidencia datos);
}
