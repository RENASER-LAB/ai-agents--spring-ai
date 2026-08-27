package com.renaser.ai.ai_engine.simulacion.service;

import com.renaser.ai.ai_engine.perfilintegral.entity.Criterio;
import com.renaser.ai.ai_engine.perfilintegral.service.CalificacionPorCriterio;
import com.renaser.ai.ai_engine.postulacion.entity.Postulacion;
import com.renaser.ai.ai_engine.seguridad.dto.ContextoUsuario;
import com.renaser.ai.ai_engine.vacante.service.AlcanceSobreLaVacante;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

/**
 * Calificar la simulación de un candidato, criterio a criterio.
 *
 * <p>Los diez criterios (RF-100) son globales: valen para cualquier vacante, a diferencia de la
 * rúbrica de la prueba del puesto, que cuelga de su plantilla. Ponerlos es trabajo de una
 * persona; sumarlos, del sistema.
 */
@Service
@RequiredArgsConstructor
public class ServicioCalificacionSimulacion {

    private static final String ETAPA = "SIMULACION";

    private final AlcanceSobreLaVacante alcance;
    private final CalificacionPorCriterio calificacion;

    public List<CalificacionPorCriterio.Vista> verNotas(ContextoUsuario quien, Long postulacionId) {
        laVisible(quien, postulacionId);
        return calificacion.verNotas(postulacionId, calificacion.rubricaGlobalDe(ETAPA));
    }

    @Transactional
    public void ponerNota(ContextoUsuario quien, Long postulacionId, Long criterioId,
                          double puntaje, String explicacion) {
        laVisible(quien, postulacionId);
        List<Criterio> rubrica = calificacion.rubricaGlobalDe(ETAPA);
        calificacion.ponerNota(quien, postulacionId, rubrica, criterioId, puntaje, explicacion);
    }

    @Transactional
    public BigDecimal calcularNota(ContextoUsuario quien, Long postulacionId) {
        Postulacion postulacion = laVisible(quien, postulacionId);
        return calificacion.calcularNotaEtapa(postulacion, ETAPA, calificacion.rubricaGlobalDe(ETAPA));
    }

    /** Los tres caminos miran el mismo permiso, así que va escrito aquí y no por parámetro. */
    private Postulacion laVisible(ContextoUsuario quien, Long postulacionId) {
        return alcance.laPostulacionVisible(quien, postulacionId, "calificar_simulacion");
    }
}
