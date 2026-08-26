package com.renaser.ai.ai_engine.portal.service.impl;

import com.renaser.ai.ai_engine.consentimiento.entity.TextoConsentimiento;
import com.renaser.ai.ai_engine.consentimiento.repository.TextoConsentimientoRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * El texto PROCESO publicado de una empresa. No debería faltar nunca —publicar una
 * vacante lo exige—, pero si falta es un 409 que dice qué pasa, no un vacío que deja
 * al candidato postulando sin saber quién trata sus datos.
 *
 * <p>Lo comparten el tablón (enseñar el texto antes de postular) y la postulación
 * (firmarlo al postular): es la misma búsqueda con la misma consecuencia, y dos copias
 * acabarían contestando distinto.
 */
@Service
@RequiredArgsConstructor
public class TextoProcesoPublicado {

    private final TextoConsentimientoRepository textosConsentimiento;

    /** El vigente de esa organización, o el 409 que explica por qué no se puede seguir. */
    public TextoConsentimiento de(Long organizacionId) {
        return textosConsentimiento
                .findFirstByOrganizacionIdAndTipoAndPublicadoEnIsNotNullOrderByPublicadoEnDesc(
                        organizacionId, "PROCESO")
                .orElseThrow(() -> new IllegalStateException("Esta empresa todavía no publicó su "
                        + "texto de consentimiento: no puede recibir postulaciones"));
    }
}
