package com.renaser.ai.ai_engine.prueba.repository;

import com.renaser.ai.ai_engine.prueba.entity.Entregable;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface EntregableRepository extends JpaRepository<Entregable, Long> {

    List<Entregable> findByIntentoPruebaId(Long intentoPruebaId);

    /** En bloque: lo que subió cada intento de una tanda (V67, «qué le faltó»). */
    List<Entregable> findByIntentoPruebaIdIn(java.util.Collection<Long> intentoPruebaIds);

    List<Entregable> findByIntentoPruebaIdAndEntregableRequeridoIdOrderByVersionDesc(
            Long intentoPruebaId, Long entregableRequeridoId);
}
