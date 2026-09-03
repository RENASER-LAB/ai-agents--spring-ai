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

    /**
     * Los intentos de una tanda entera, de una sola consulta.
     *
     * <p>Es lo que deja que el ranking sepa con qué versión de la plantilla se midió cada
     * candidato sin preguntarlo fila a fila. Quien todavía no ha llegado a la etapa técnica
     * simplemente no sale en la lista: <b>no</b> es un error, es lo normal en una tanda a
     * medio recorrer, y por eso esto devuelve una lista y no revienta como
     * {@link #findByPostulacionId}.
     */
    List<IntentoPrueba> findByPostulacionIdIn(java.util.Collection<Long> postulacionIds);

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

    /**
     * ¿Alguien de esta vacante ya abrió su prueba del puesto?
     *
     * <p>Es la línea que decide si la etapa técnica todavía se puede reconfigurar. La
     * frontera es {@code iniciadoEn}, no la postulación: postular no es rendir, y quien no
     * ha abierto la prueba no ha visto ningún reloj que se le pueda mover.
     *
     * <p>⚠️ <b>No sirve {@link #abiertosDeLaVacante}</b>, aunque lo parezca: aquella filtra
     * {@code entregadoEn is null} para moverles la fecha, y quien ya entregó es justamente
     * el caso más claro de «ya se midió con esta vara».
     */
    @Query("""
            select count(i) > 0 from IntentoPrueba i
            where i.iniciadoEn is not null
              and i.postulacionId in (select p.id from Postulacion p where p.vacanteId = :vacanteId)
            """)
    boolean algunoEmpezadoDeLaVacante(@Param("vacanteId") Long vacanteId);
}
