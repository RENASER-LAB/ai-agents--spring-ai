package com.renaser.ai.ai_engine.prueba.repository;

import com.renaser.ai.ai_engine.prueba.entity.RespuestaPrueba;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface RespuestaPruebaRepository extends JpaRepository<RespuestaPrueba, Long> {

    List<RespuestaPrueba> findByIntentoPruebaId(Long intentoPruebaId);
    Optional<RespuestaPrueba> findByIntentoPruebaIdAndPreguntaPruebaId(Long intentoPruebaId, Long preguntaPruebaId);
}
