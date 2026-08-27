package com.renaser.ai.ai_engine.prueba.repository;

import com.renaser.ai.ai_engine.prueba.entity.IntentoPrueba;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface IntentoPruebaRepository extends JpaRepository<IntentoPrueba, Long> {

    Optional<IntentoPrueba> findByPostulacionId(Long postulacionId);

    // Los que ya vencieron y nadie entregó: el sondeo los cierra solo (RF: "no existe
    // entregar tarde").
    List<IntentoPrueba> findByEntregadoEnIsNullAndIniciadoEnIsNotNullAndVenceEnBefore(Instant momento);

    /**
     * Los intentos sin entregar de una vacante, para moverles la fecha de cierre.
     *
     * <p>Va por la postulación porque el intento no guarda la vacante: la sabe su
     * postulación, y duplicarla aquí sería un segundo sitio donde se puede desincronizar.
     */
    @Query("""
            select i from IntentoPrueba i
            where i.entregadoEn is null
              and i.postulacionId in (select p.id from Postulacion p where p.vacanteId = :vacanteId)
            """)
    List<IntentoPrueba> abiertosDeLaVacante(@Param("vacanteId") Long vacanteId);
}
