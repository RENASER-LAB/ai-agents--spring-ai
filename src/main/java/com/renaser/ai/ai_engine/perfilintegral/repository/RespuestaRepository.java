package com.renaser.ai.ai_engine.perfilintegral.repository;

import com.renaser.ai.ai_engine.perfilintegral.entity.Respuesta;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface RespuestaRepository extends JpaRepository<Respuesta, Long> {

    List<Respuesta> findByEvaluacionId(Long evaluacionId);

    // Una pregunta se responde una sola vez: la base lo garantiza con un único
    // (evaluacion_id, pregunta_id). Esto permite reescribir mientras no se haya entregado.
    Optional<Respuesta> findByEvaluacionIdAndPreguntaId(Long evaluacionId, Long preguntaId);
}
