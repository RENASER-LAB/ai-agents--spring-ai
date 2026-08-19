package com.renaser.ai.ai_engine.solicitud.repository;

import com.renaser.ai.ai_engine.solicitud.entity.SolicitudTalento;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface SolicitudTalentoRepository extends JpaRepository<SolicitudTalento, Long> {
    List<SolicitudTalento> findByOrganizacionIdOrderByCreadoEnDesc(Long organizacionId);
    List<SolicitudTalento> findByOrganizacionIdAndResponsableUsuarioIdOrderByCreadoEnDesc(
            Long organizacionId, Long responsableUsuarioId);
    Optional<SolicitudTalento> findByIdAndOrganizacionId(Long id, Long organizacionId);
}
