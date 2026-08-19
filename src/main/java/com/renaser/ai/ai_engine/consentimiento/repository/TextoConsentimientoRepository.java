package com.renaser.ai.ai_engine.consentimiento.repository;

import com.renaser.ai.ai_engine.consentimiento.entity.TextoConsentimiento;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface TextoConsentimientoRepository extends JpaRepository<TextoConsentimiento, Long> {

    // El texto vigente de cada tipo: el publicado más reciente
    Optional<TextoConsentimiento> findFirstByOrganizacionIdAndTipoAndPublicadoEnIsNotNullOrderByPublicadoEnDesc(
            Long organizacionId, String tipo);
}
