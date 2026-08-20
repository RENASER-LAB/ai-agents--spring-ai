package com.renaser.ai.ai_engine.perfilintegral.repository;

import com.renaser.ai.ai_engine.perfilintegral.entity.Pregunta;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface PreguntaRepository extends JpaRepository<Pregunta, Long> {
    List<Pregunta> findByVersionBancoIdOrderByOrden(Long versionBancoId);

    List<Pregunta> findByIdIn(List<Long> ids);
}
