package com.renaser.ai.ai_engine.perfilintegral.repository;

import com.renaser.ai.ai_engine.perfilintegral.entity.NotaRespuesta;

import org.springframework.data.jpa.repository.JpaRepository;

public interface NotaRespuestaRepository extends JpaRepository<NotaRespuesta, Long> {

    java.util.List<NotaRespuesta> findByRespuestaIdIn(java.util.List<Long> respuestaIds);

    java.util.Optional<NotaRespuesta> findByRespuestaId(Long respuestaId);
}
