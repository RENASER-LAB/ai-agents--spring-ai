package com.renaser.ai.ai_engine.postulacion.repository;

import com.renaser.ai.ai_engine.postulacion.entity.Cv;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

import java.util.List;

public interface CvRepository extends JpaRepository<Cv, Long> {
    Optional<Cv> findByPostulacionId(Long postulacionId);

    // En bloque, para el ranking. Pedirlo de una en una son once consultas por
    // candidato, y esa pantalla existe justamente para ver la tanda entera.
    List<Cv> findByPostulacionIdIn(List<Long> postulacionIds);
}
