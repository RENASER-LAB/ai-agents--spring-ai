package com.renaser.ai.ai_engine.decision.service;

import com.renaser.ai.ai_engine.decision.entity.Decision;
import com.renaser.ai.ai_engine.decision.repository.DecisionRepository;
import com.renaser.ai.ai_engine.postulacion.entity.Postulacion;
import com.renaser.ai.ai_engine.seguridad.dto.ContextoUsuario;
import com.renaser.ai.ai_engine.seguridad.dto.FiltroAlcance;
import com.renaser.ai.ai_engine.vacante.service.AlcanceSobreLaVacante;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Si quien mira puede contratar a esta persona desde su ficha: la decisión en verde.
 *
 * <p>Con las mismas reglas que {@code ServicioDecisionImpl#decidir}, que es el único camino a
 * CONTRATADO que usa el botón: la primera decisión pide {@code decidir_contratacion} y cambiar
 * una ya tomada pide {@code cambiar_decision}; y la postulación tiene que ser visible con
 * {@code ver_semaforo_decision}. Es una pista para pintar el botón, no la defensa: el endpoint
 * de la decisión vuelve a comprobarlo todo.
 */
@Component
@RequiredArgsConstructor
public class QuienPuedeContratar {

    private final DecisionRepository decisiones;
    private final AlcanceSobreLaVacante alcanceVacante;

    public boolean puede(ContextoUsuario quien, Postulacion postulacion, boolean estadoFinal) {
        if (estadoFinal) {
            return false;
        }
        Decision existente = decisiones.findByPostulacionId(postulacion.getId()).orElse(null);
        String permiso = existente != null && existente.getDecididaPorUsuarioId() != null
                ? "cambiar_decision" : "decidir_contratacion";
        return alcanza(quien, permiso, postulacion) && alcanza(quien, "ver_semaforo_decision", postulacion);
    }

    private boolean alcanza(ContextoUsuario quien, String permiso, Postulacion postulacion) {
        String alcance = quien.alcance(permiso);
        return alcance != null
                && alcanceVacante.alcanzaA(quien, FiltroAlcance.desde(alcance, quien.usuarioId()), postulacion);
    }
}
