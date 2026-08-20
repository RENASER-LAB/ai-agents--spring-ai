package com.renaser.ai.ai_engine.perfilintegral.repository;

import com.renaser.ai.ai_engine.perfilintegral.entity.CampoCaso;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface CampoCasoRepository extends JpaRepository<CampoCaso, Long> {

    // Los campos de un caso descompuesto, en el orden en que se le muestran al candidato.
    List<CampoCaso> findByPreguntaIdOrderByOrden(Long preguntaId);
}
