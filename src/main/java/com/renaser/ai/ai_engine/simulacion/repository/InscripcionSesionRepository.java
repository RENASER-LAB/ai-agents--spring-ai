package com.renaser.ai.ai_engine.simulacion.repository;

import com.renaser.ai.ai_engine.simulacion.entity.InscripcionSesion;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface InscripcionSesionRepository extends JpaRepository<InscripcionSesion, Long> {

    Optional<InscripcionSesion> findByPostulacionIdAndEsVigenteTrue(Long postulacionId);
    List<InscripcionSesion> findBySesionSimulacionIdAndEsVigenteTrue(Long sesionSimulacionId);
    long countBySesionSimulacionIdAndEsVigenteTrue(Long sesionSimulacionId);

    /**
     * Cuántos inscritos vigentes tiene cada sesión de un lote, en una sola consulta.
     *
     * <p>Cuenta en la base y no trae las filas a propósito: el panel solo enseña el número,
     * y traer las inscripciones de todas las sesiones para contarlas en memoria sería
     * cambiar una consulta por fila por un montón de filas que nadie mira.
     *
     * @return pares {@code [sesionSimulacionId, cuántos]}; las sesiones sin nadie no salen
     */
    @Query("""
            select i.sesionSimulacionId, count(i)
            from InscripcionSesion i
            where i.sesionSimulacionId in :sesionIds and i.esVigente = true
            group by i.sesionSimulacionId
            """)
    List<Object[]> contarVigentesPorSesion(@Param("sesionIds") List<Long> sesionIds);
}
