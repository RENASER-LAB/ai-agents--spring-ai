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

    /**
     * El que esta persona ya firmó de este texto sin postulación (V92): el de WhatsApp para toda
     * la cuenta se firma una vez por persona y versión, aunque encienda el interruptor dos veces.
     */
    Optional<Consentimiento> findFirstByPersonaIdAndPostulacionIdIsNullAndTextoConsentimientoId(
            Long personaId, Long textoConsentimientoId);

    /**
     * Los suyos de un tipo sin retirar, el más reciente primero (V92): con qué consentimiento de
     * WhatsApp se vincula una postulación nueva cuando el interruptor ya estaba encendido.
     */
    @Query("""
            select c from Consentimiento c, TextoConsentimiento t
            where t.id = c.textoConsentimientoId
              and c.personaId = :personaId
              and t.tipo = :tipo
              and c.retiradoEn is null
            order by c.aceptadoEn desc, c.id desc
            """)
    List<Consentimiento> deTipoMasRecientes(@Param("personaId") Long personaId, @Param("tipo") String tipo);

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
