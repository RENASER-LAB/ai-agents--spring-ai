package com.renaser.ai.ai_engine.notificacion.repository;

import com.renaser.ai.ai_engine.notificacion.entity.PlantillaCorreo;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface PlantillaCorreoRepository extends JpaRepository<PlantillaCorreo, Long> {
    // La versión activa más reciente de un texto
    Optional<PlantillaCorreo> findFirstByOrganizacionIdAndCodigoAndEsActivaTrueOrderByVersionDesc(
            Long organizacionId, String codigo);
    List<PlantillaCorreo> findByOrganizacionIdOrderByCodigoAscVersionDesc(Long organizacionId);
    Optional<PlantillaCorreo> findFirstByOrganizacionIdAndCodigoOrderByVersionDesc(
            Long organizacionId, String codigo);
}
