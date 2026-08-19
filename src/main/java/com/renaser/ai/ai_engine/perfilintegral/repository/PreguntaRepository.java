package com.renaser.ai.ai_engine.perfilintegral.repository;

import com.renaser.ai.ai_engine.perfilintegral.entity.Pregunta;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface PreguntaRepository extends JpaRepository<Pregunta, Long> {
    List<Pregunta> findByVersionBancoIdOrderByOrden(Long versionBancoId);

    // Para armar la evaluación: la plantilla pide "entre 8 y 10 de SITUACION" y de aquí
    // salen las candidatas de las que se eligen al azar.
    List<Pregunta> findByVersionBancoIdAndTipo(Long versionBancoId, String tipo);

    List<Pregunta> findByIdIn(List<Long> ids);
}
