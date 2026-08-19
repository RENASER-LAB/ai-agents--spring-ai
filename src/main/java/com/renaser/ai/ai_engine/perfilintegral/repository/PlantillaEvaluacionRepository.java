package com.renaser.ai.ai_engine.perfilintegral.repository;

import com.renaser.ai.ai_engine.perfilintegral.entity.PlantillaEvaluacion;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface PlantillaEvaluacionRepository extends JpaRepository<PlantillaEvaluacion, Long> {
    List<PlantillaEvaluacion> findByOrganizacionIdOrderByCreadoEnDesc(Long organizacionId);
    Optional<PlantillaEvaluacion> findByIdAndOrganizacionId(Long id, Long organizacionId);
}
