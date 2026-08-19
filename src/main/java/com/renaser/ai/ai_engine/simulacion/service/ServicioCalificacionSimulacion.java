package com.renaser.ai.ai_engine.simulacion.service;

import com.renaser.ai.ai_engine.ai.exception.ResourceNotFoundException;
import com.renaser.ai.ai_engine.perfilintegral.entity.Criterio;
import com.renaser.ai.ai_engine.perfilintegral.service.CalificacionPorCriterio;
import com.renaser.ai.ai_engine.postulacion.entity.Postulacion;
import com.renaser.ai.ai_engine.postulacion.repository.PostulacionRepository;
import com.renaser.ai.ai_engine.seguridad.dto.ContextoUsuario;
import com.renaser.ai.ai_engine.seguridad.dto.FiltroAlcance;
import com.renaser.ai.ai_engine.seguridad.service.Permisos;
import com.renaser.ai.ai_engine.simulacion.dto.DtosSimulacion.MarcaResponse;
import com.renaser.ai.ai_engine.vacante.repository.VacanteRepository;

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

    private final PostulacionRepository postulaciones;
    private final VacanteRepository vacantes;
    private final CalificacionPorCriterio calificacion;
    private final Permisos permisos;

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

    private Postulacion laVisible(ContextoUsuario quien, Long postulacionId) {
        Postulacion p = postulaciones.findByIdAndOrganizacionId(postulacionId, quien.organizacionId())
                .orElseThrow(() -> new ResourceNotFoundException("Postulación", "id", postulacionId));
        FiltroAlcance alcance = permisos.alcanceDe("calificar_simulacion");
        if (alcance.tipo() == FiltroAlcance.Tipo.SUS_VACANTES) {
            boolean esSuya = vacantes.findById(p.getVacanteId())
                    .map(v -> quien.usuarioId().equals(v.getResponsableUsuarioId()))
                    .orElse(false);
            if (!esSuya) {
                throw new ResourceNotFoundException("Postulación", "id", postulacionId);
            }
        }
        return p;
    }
}
