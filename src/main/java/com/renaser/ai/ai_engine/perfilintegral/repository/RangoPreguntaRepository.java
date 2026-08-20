package com.renaser.ai.ai_engine.perfilintegral.repository;

import com.renaser.ai.ai_engine.perfilintegral.entity.RangoPregunta;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface RangoPreguntaRepository extends JpaRepository<RangoPregunta, Long> {

    // Los tramos de un ítem V, en el orden en que el documento los declara.
    List<RangoPregunta> findByPreguntaIdOrderByOrden(Long preguntaId);

    // Publicar exige que un V tenga con qué puntuarse: tramos, referencia o fórmula.
    long countByPreguntaId(Long preguntaId);
}
