package com.renaser.ai.ai_engine.perfilintegral.repository;

import com.renaser.ai.ai_engine.perfilintegral.entity.PropuestaPreguntas;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface PropuestaPreguntasRepository extends JpaRepository<PropuestaPreguntas, Long> {

    /** La última propuesta pedida para una vacante: la que el panel enseña. */
    Optional<PropuestaPreguntas> findFirstByVacanteIdOrderByIdDesc(Long vacanteId);

    Optional<PropuestaPreguntas> findByIdAndVacanteId(Long id, Long vacanteId);
}
