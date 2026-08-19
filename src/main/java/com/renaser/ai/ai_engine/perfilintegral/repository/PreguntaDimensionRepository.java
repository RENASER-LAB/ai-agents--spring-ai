package com.renaser.ai.ai_engine.perfilintegral.repository;

import com.renaser.ai.ai_engine.perfilintegral.entity.PreguntaDimension;

import org.springframework.data.jpa.repository.JpaRepository;

public interface PreguntaDimensionRepository extends JpaRepository<PreguntaDimension, PreguntaDimension.Clave> {

    java.util.List<PreguntaDimension> findByPreguntaIdIn(java.util.List<Long> preguntaIds);
}
