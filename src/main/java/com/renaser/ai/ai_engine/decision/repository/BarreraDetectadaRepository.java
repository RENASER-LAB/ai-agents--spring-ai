package com.renaser.ai.ai_engine.decision.repository;

import com.renaser.ai.ai_engine.decision.entity.BarreraDetectada;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface BarreraDetectadaRepository extends JpaRepository<BarreraDetectada, Long> {

    List<BarreraDetectada> findByPostulacionId(Long postulacionId);

    // Las confirmadas y no descartadas: las únicas que de verdad bloquean (RF-116).
    List<BarreraDetectada> findByPostulacionIdAndConfirmadaPorUsuarioIdIsNotNullAndDescartadaEnIsNull(
            Long postulacionId);
}
