package com.renaser.ai.ai_engine.prueba.repository;

import com.renaser.ai.ai_engine.prueba.entity.RespuestaPrueba;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface RespuestaPruebaRepository extends JpaRepository<RespuestaPrueba, Long> {

    List<RespuestaPrueba> findByIntentoPruebaId(Long intentoPruebaId);
    Optional<RespuestaPrueba> findByIntentoPruebaIdAndPreguntaPruebaId(Long intentoPruebaId, Long preguntaPruebaId);

    /** La respuesta a una pregunta de la prueba escrita en el editor (V67). */
    Optional<RespuestaPrueba> findByIntentoPruebaIdAndPreguntaId(Long intentoPruebaId, Long preguntaId);

    /** En bloque, para el ranking y el Excel. */
    List<RespuestaPrueba> findByIntentoPruebaIdIn(java.util.Collection<Long> intentoPruebaIds);
}
