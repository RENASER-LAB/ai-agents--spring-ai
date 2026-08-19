package com.renaser.ai.ai_engine.simulacion.repository;

import com.renaser.ai.ai_engine.simulacion.entity.PreguntaGenerada;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface PreguntaGeneradaRepository extends JpaRepository<PreguntaGenerada, Long> {

    List<PreguntaGenerada> findByPostulacionIdOrderByOrden(Long postulacionId);
}
