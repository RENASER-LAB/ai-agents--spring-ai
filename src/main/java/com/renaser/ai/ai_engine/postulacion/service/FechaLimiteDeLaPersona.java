package com.renaser.ai.ai_engine.postulacion.service;

import com.renaser.ai.ai_engine.perfilintegral.entity.Evaluacion;
import com.renaser.ai.ai_engine.perfilintegral.repository.EvaluacionRepository;
import com.renaser.ai.ai_engine.postulacion.entity.Postulacion;
import com.renaser.ai.ai_engine.prueba.entity.IntentoPrueba;
import com.renaser.ai.ai_engine.prueba.repository.IntentoPruebaRepository;
import com.renaser.ai.ai_engine.vacante.entity.Vacante;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.Instant;

/**
 * Hasta cuándo tiene ESTA persona para su prueba: la fecha que rige para ella (V70).
 *
 * <p>No es la de la vacante sin más. Si el equipo le concedió un plazo propio, manda el suyo; y
 * cuando sale el aviso de la prueba su intento ya existe, así que la fecha que rige es su
 * {@code vence_en}, que ya lleva una u otra. Decir la de la vacante a quien tiene más tiempo le
 * haría creer que tiene menos.
 *
 * <p>El cuestionario técnico lleva la suya en su propia evaluación: nace con sus días de plazo.
 */
@Component
@RequiredArgsConstructor
public class FechaLimiteDeLaPersona {

    private final IntentoPruebaRepository intentos;
    private final EvaluacionRepository evaluaciones;

    /** La fecha límite de su prueba, o {@code null} si no tiene ninguna. */
    public Instant deSuPrueba(Postulacion postulacion, Vacante vacante) {
        if (vacante != null
                && EntradaEtapaTecnica.CUESTIONARIO_TECNICO.equals(vacante.getInstrumentoEtapaTecnica())) {
            return postulacion.getEvaluacionTecnicaId() == null ? null
                    : evaluaciones.findById(postulacion.getEvaluacionTecnicaId())
                            .map(Evaluacion::getVenceEn).orElse(null);
        }
        Instant deLaVacante = vacante == null ? null : vacante.getPruebaCierraEn();
        return intentos.findByPostulacionId(postulacion.getId())
                .map(IntentoPrueba::getVenceEn)
                .orElse(deLaVacante);
    }
}
