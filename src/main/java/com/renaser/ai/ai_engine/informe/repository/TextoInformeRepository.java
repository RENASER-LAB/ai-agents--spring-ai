package com.renaser.ai.ai_engine.informe.repository;

import com.renaser.ai.ai_engine.informe.entity.TextoInforme;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface TextoInformeRepository extends JpaRepository<TextoInforme, Long> {

    Optional<TextoInforme> findByPostulacionIdAndEtapa(Long postulacionId, String etapa);
}
