package com.renaser.ai.ai_engine.validacion.service;

import com.renaser.ai.ai_engine.seguridad.dto.ContextoUsuario;
import com.renaser.ai.ai_engine.validacion.dto.DtosValidacion.*;

import java.util.List;

/**
 * La validación práctica: el último paso de evidencia antes de decidir.
 *
 * <p>Dos modalidades con una diferencia que no es técnica sino legal: la simulación extendida
 * se puede usar desde el primer día, y el trabajo real exige figura contractual registrada.
 */
public interface ServicioValidacion {

    /** La crea el sistema al entrar a la etapa, en POR_HABILITAR y sin modalidad todavía. */
    Long crearAlEntrar(Long postulacionId, Long organizacionId);

    ValidacionResponse ver(ContextoUsuario quien, Long postulacionId);

    /** Fija modalidad, días y responsable. Con TRABAJO_REAL exige la figura contractual. */
    void habilitar(ContextoUsuario quien, Long postulacionId, HabilitarValidacion datos);

    /** Arranca el periodo: fija inicio y fin, y el candidato pasa a su turno. */
    void iniciar(ContextoUsuario quien, Long postulacionId);

    List<MetricaResponse> verMetricas(ContextoUsuario quien, Long postulacionId);

    /** Completa una métrica que no se alimentó sola. La explicación es obligatoria. */
    void completarMetrica(ContextoUsuario quien, Long postulacionId, Long criterioId, CompletarMetrica datos);

    /** Cierra el periodo: pondera las métricas y manda la postulación a la decisión. */
    void cerrar(ContextoUsuario quien, Long postulacionId);

    /** Llamado por el sondeo: los periodos cuya fecha de fin ya pasó terminan solos. */
    void terminarVencidos();
}
