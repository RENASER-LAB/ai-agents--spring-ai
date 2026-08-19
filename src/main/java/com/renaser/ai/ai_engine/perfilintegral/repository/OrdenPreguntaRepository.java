package com.renaser.ai.ai_engine.perfilintegral.repository;

import com.renaser.ai.ai_engine.perfilintegral.entity.OrdenPregunta;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface OrdenPreguntaRepository extends JpaRepository<OrdenPregunta, OrdenPregunta.Clave> {

    // El orden es por evaluación: cada candidato ve las suyas en su propia secuencia, y esta
    // tabla es lo que permite reproducir el examen exacto meses después (RNF-30).
    List<OrdenPregunta> findByEvaluacionIdOrderByPosicion(Long evaluacionId);
}
