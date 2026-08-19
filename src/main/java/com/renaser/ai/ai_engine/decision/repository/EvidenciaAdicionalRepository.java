package com.renaser.ai.ai_engine.decision.repository;

import com.renaser.ai.ai_engine.decision.entity.EvidenciaAdicional;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface EvidenciaAdicionalRepository extends JpaRepository<EvidenciaAdicional, Long> {

    List<EvidenciaAdicional> findByPostulacionIdOrderByNumero(Long postulacionId);
}
