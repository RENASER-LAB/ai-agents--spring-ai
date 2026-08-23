package com.renaser.ai.ai_engine.perfilintegral.repository;

import com.renaser.ai.ai_engine.perfilintegral.entity.Opcion;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface OpcionRepository extends JpaRepository<Opcion, Long> {
    List<Opcion> findByPreguntaIdOrderByLetra(Long preguntaId);

    // Todas las opciones de un lote de preguntas, para no hacer una consulta por pregunta
    // al pintar una evaluación de 30.
    List<Opcion> findByPreguntaIdIn(List<Long> preguntaIds);

    // Al eliminar preguntas de un borrador, o al descartarlo entero.
    void deleteByPreguntaIdIn(List<Long> preguntaIds);
}
