package com.renaser.ai.ai_engine.perfilintegral.repository;

import com.renaser.ai.ai_engine.perfilintegral.entity.ResultadoAlineacion;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ResultadoAlineacionRepository extends JpaRepository<ResultadoAlineacion, Long> {

    List<ResultadoAlineacion> findByEvaluacionId(Long evaluacionId);
}
