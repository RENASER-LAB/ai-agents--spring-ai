package com.renaser.ai.ai_engine.perfilintegral.repository;

import com.renaser.ai.ai_engine.perfilintegral.entity.PropuestaPreguntas;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface PropuestaPreguntasRepository extends JpaRepository<PropuestaPreguntas, Long> {

    /**
     * La última propuesta pedida para las preguntas del Perfil Integral de una vacante: la que
     * el panel enseña.
     *
     * <p>⚠️ <b>Solo las del Perfil Integral, aunque el nombre no lo diga.</b> Desde la V67 una
     * vacante puede pedir también recomendaciones para su prueba técnica, y sin el filtro la
     * última de la prueba le taparía a la fase 1 la suya (o la de la fase 1 se agregaría a la
     * prueba). El nombre se conserva porque es el contrato de la fase 1.
     */
    @Query("""
            select p from PropuestaPreguntas p
             where p.vacanteId = :vacanteId and p.proposito = 'PERFIL_INTEGRAL'
             order by p.id desc limit 1""")
    Optional<PropuestaPreguntas> findFirstByVacanteIdOrderByIdDesc(@Param("vacanteId") Long vacanteId);

    /** Una propuesta del Perfil Integral de esa vacante. Ver {@link #findFirstByVacanteIdOrderByIdDesc}. */
    @Query("""
            select p from PropuestaPreguntas p
             where p.id = :id and p.vacanteId = :vacanteId and p.proposito = 'PERFIL_INTEGRAL'""")
    Optional<PropuestaPreguntas> findByIdAndVacanteId(@Param("id") Long id,
                                                      @Param("vacanteId") Long vacanteId);

    /** La última propuesta de un propósito para una vacante (V67). */
    Optional<PropuestaPreguntas> findFirstByVacanteIdAndPropositoOrderByIdDesc(Long vacanteId,
                                                                               String proposito);

    Optional<PropuestaPreguntas> findByIdAndVacanteIdAndProposito(Long id, Long vacanteId,
                                                                  String proposito);
}
