package com.renaser.ai.ai_engine.consentimiento.repository;

import com.renaser.ai.ai_engine.consentimiento.entity.TextoConsentimiento;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface TextoConsentimientoRepository extends JpaRepository<TextoConsentimiento, Long> {

    // El texto vigente de cada tipo: el publicado más reciente
    Optional<TextoConsentimiento> findFirstByOrganizacionIdAndTipoAndPublicadoEnIsNotNullOrderByPublicadoEnDesc(
            Long organizacionId, String tipo);

    // El panel de textos legales: todas las versiones de la organización, historia incluida
    List<TextoConsentimiento> findByOrganizacionIdOrderByTipoAscCreadoEnDesc(Long organizacionId);

    // Para numerar la versión siguiente cuando quien publica no pone una
    long countByOrganizacionIdAndTipo(Long organizacionId, String tipo);
}
