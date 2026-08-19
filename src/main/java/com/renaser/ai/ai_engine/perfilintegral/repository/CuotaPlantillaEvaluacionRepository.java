package com.renaser.ai.ai_engine.perfilintegral.repository;

import com.renaser.ai.ai_engine.perfilintegral.entity.CuotaPlantillaEvaluacion;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface CuotaPlantillaEvaluacionRepository extends JpaRepository<CuotaPlantillaEvaluacion, Long> {
    List<CuotaPlantillaEvaluacion> findByPlantillaEvaluacionId(Long plantillaEvaluacionId);
}
