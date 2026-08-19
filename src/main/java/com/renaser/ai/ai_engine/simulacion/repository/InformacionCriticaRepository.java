package com.renaser.ai.ai_engine.simulacion.repository;

import com.renaser.ai.ai_engine.simulacion.entity.InformacionCritica;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface InformacionCriticaRepository extends JpaRepository<InformacionCritica, Long> {

    List<InformacionCritica> findBySesionSimulacionIdOrderByOrden(Long sesionSimulacionId);
}
