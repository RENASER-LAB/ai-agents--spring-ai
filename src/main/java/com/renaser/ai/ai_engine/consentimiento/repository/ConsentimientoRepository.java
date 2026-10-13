package com.renaser.ai.ai_engine.consentimiento.repository;

import com.renaser.ai.ai_engine.consentimiento.entity.Consentimiento;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface ConsentimientoRepository extends JpaRepository<Consentimiento, Long> {

    List<Consentimiento> findByPersonaId(Long personaId);

    /**
     * El que esta persona ya firmó de este texto para esta postulación (V73): el de WhatsApp se
     * firma una vez por postulación y versión, aunque pulse el botón dos veces.
     */
    Optional<Consentimiento> findFirstByPersonaIdAndPostulacionIdAndTextoConsentimientoId(
            Long personaId, Long postulacionId, Long textoConsentimientoId);

    // El consentimiento de futuros contactos vigente (aceptado y no retirado)
    @Query("""
            select c from Consentimiento c, TextoConsentimiento t
            where t.id = c.textoConsentimientoId
              and c.personaId = :personaId
              and t.tipo = :tipo
              and c.retiradoEn is null
            """)
    Optional<Consentimiento> vigenteDeTipo(@Param("personaId") Long personaId, @Param("tipo") String tipo);
}
