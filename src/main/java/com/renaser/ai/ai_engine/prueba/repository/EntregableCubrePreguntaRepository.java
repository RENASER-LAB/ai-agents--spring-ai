package com.renaser.ai.ai_engine.prueba.repository;

import com.renaser.ai.ai_engine.prueba.entity.EntregableCubrePregunta;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;

/** Las preguntas que cubre cada entregable general de la prueba del editor (V68). */
public interface EntregableCubrePreguntaRepository extends JpaRepository<EntregableCubrePregunta, Long> {

    List<EntregableCubrePregunta> findByEntregableRequeridoIdIn(Collection<Long> entregableRequeridoIds);

    void deleteByEntregableRequeridoId(Long entregableRequeridoId);

    void deleteByEntregableRequeridoIdIn(Collection<Long> entregableRequeridoIds);

    void deleteByPreguntaId(Long preguntaId);
}
