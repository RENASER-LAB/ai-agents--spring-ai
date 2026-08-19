package com.renaser.ai.ai_engine.perfilintegral.repository;

import com.renaser.ai.ai_engine.perfilintegral.entity.NotaCriterio;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface NotaCriterioRepository extends JpaRepository<NotaCriterio, Long> {

    List<NotaCriterio> findByPostulacionId(Long postulacionId);
    Optional<NotaCriterio> findByPostulacionIdAndCriterioId(Long postulacionId, Long criterioId);

    // En bloque, para el ranking. Pedirlo de una en una son once consultas por
    // candidato, y esa pantalla existe justamente para ver la tanda entera.
    List<NotaCriterio> findByPostulacionIdIn(List<Long> postulacionIds);
}
