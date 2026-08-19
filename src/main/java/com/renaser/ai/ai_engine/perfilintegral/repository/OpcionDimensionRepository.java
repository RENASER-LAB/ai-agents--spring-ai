package com.renaser.ai.ai_engine.perfilintegral.repository;

import com.renaser.ai.ai_engine.perfilintegral.entity.OpcionDimension;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface OpcionDimensionRepository extends JpaRepository<OpcionDimension, OpcionDimension.Clave> {

    List<OpcionDimension> findByOpcionIdIn(List<Long> opcionIds);
}
