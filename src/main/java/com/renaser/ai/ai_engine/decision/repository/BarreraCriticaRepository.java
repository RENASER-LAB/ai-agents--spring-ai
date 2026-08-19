package com.renaser.ai.ai_engine.decision.repository;

import com.renaser.ai.ai_engine.decision.entity.BarreraCritica;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface BarreraCriticaRepository extends JpaRepository<BarreraCritica, Long> {

    List<BarreraCritica> findByVacanteIdAndEsActivaTrue(Long vacanteId);
}
