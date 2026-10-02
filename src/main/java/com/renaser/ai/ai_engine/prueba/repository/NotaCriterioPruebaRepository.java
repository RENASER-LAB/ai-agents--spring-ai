package com.renaser.ai.ai_engine.prueba.repository;

import com.renaser.ai.ai_engine.prueba.entity.NotaCriterioPrueba;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface NotaCriterioPruebaRepository extends JpaRepository<NotaCriterioPrueba, Long> {

    List<NotaCriterioPrueba> findByIntentoPruebaId(Long intentoPruebaId);

    /** En bloque, para el ranking y el Excel: nunca una consulta por fila. */
    List<NotaCriterioPrueba> findByIntentoPruebaIdIn(Collection<Long> intentoPruebaIds);

    Optional<NotaCriterioPrueba> findByIntentoPruebaIdAndCriterioBancoId(Long intentoPruebaId,
                                                                         Long criterioBancoId);

    List<NotaCriterioPrueba> findByCriterioBancoIdIn(Collection<Long> criterioBancoIds);
}
