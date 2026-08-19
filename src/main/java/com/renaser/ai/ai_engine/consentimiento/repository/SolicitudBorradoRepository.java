package com.renaser.ai.ai_engine.consentimiento.repository;

import com.renaser.ai.ai_engine.consentimiento.entity.SolicitudBorrado;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface SolicitudBorradoRepository extends JpaRepository<SolicitudBorrado, Long> {
    List<SolicitudBorrado> findByEjecutadoEnIsNullOrderBySolicitadoEnAsc();
    boolean existsByPersonaIdAndEjecutadoEnIsNull(Long personaId);
}
