package com.renaser.ai.ai_engine.perfilintegral.repository;

import com.renaser.ai.ai_engine.perfilintegral.entity.Evaluacion;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;

public interface EvaluacionRepository extends JpaRepository<Evaluacion, Long> {

    // Las que se pasaron de plazo y siguen abiertas. El sondeo las cierra: si nadie lo hace,
    // una evaluación abandonada deja la postulación esperando para siempre.
    List<Evaluacion> findByEstadoInAndVenceEnBefore(List<String> estados, Instant momento);
}
