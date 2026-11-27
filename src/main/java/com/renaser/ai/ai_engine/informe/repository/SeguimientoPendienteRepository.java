package com.renaser.ai.ai_engine.informe.repository;

import com.renaser.ai.ai_engine.informe.entity.SeguimientoPendiente;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface SeguimientoPendienteRepository extends JpaRepository<SeguimientoPendiente, Long> {

    Optional<SeguimientoPendiente> findByPostulacionIdAndEtapa(Long postulacionId, String etapa);
}
