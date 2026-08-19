package com.renaser.ai.ai_engine.decision.repository;

import com.renaser.ai.ai_engine.decision.entity.Decision;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface DecisionRepository extends JpaRepository<Decision, Long> {

    Optional<Decision> findByPostulacionId(Long postulacionId);
}
