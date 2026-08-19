package com.renaser.ai.ai_engine.prueba.repository;

import com.renaser.ai.ai_engine.prueba.entity.IntentoPrueba;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface IntentoPruebaRepository extends JpaRepository<IntentoPrueba, Long> {

    Optional<IntentoPrueba> findByPostulacionId(Long postulacionId);

    // Los que ya vencieron y nadie entregó: el sondeo los cierra solo (RF: "no existe
    // entregar tarde").
    List<IntentoPrueba> findByEntregadoEnIsNullAndIniciadoEnIsNotNullAndVenceEnBefore(Instant momento);
}
