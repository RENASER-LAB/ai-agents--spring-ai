package com.renaser.ai.ai_engine.pesos.repository;

import com.renaser.ai.ai_engine.pesos.entity.VersionPesos;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface VersionPesosRepository extends JpaRepository<VersionPesos, Long> {
    // La versión publicada más reciente: la que rige una vacante nueva si nadie elige otra
    Optional<VersionPesos> findFirstByOrganizacionIdAndEstadoOrderByPublicadaEnDesc(
            Long organizacionId, String estado);
    List<VersionPesos> findByOrganizacionIdOrderByCreadoEnDesc(Long organizacionId);
    Optional<VersionPesos> findByIdAndOrganizacionId(Long id, Long organizacionId);
}
