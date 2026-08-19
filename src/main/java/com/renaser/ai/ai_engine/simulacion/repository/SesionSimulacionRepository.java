package com.renaser.ai.ai_engine.simulacion.repository;

import com.renaser.ai.ai_engine.simulacion.entity.SesionSimulacion;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface SesionSimulacionRepository extends JpaRepository<SesionSimulacion, Long> {

    List<SesionSimulacion> findByOrganizacionIdOrderByFechaHora(Long organizacionId);
    Optional<SesionSimulacion> findByIdAndOrganizacionId(Long id, Long organizacionId);

    /**
     * Las sesiones que un candidato puede elegir: de su vacante, publicadas, con cupo libre y
     * todavía por venir. Contar las inscripciones vigentes aquí -y no en Java- es lo que evita
     * que dos candidatos ocupen la última plaza a la vez.
     */
    @Query("""
            select s from SesionSimulacion s
            where s.organizacionId = :organizacionId
              and s.estado = 'PUBLICADA'
              and s.fechaHora > current_timestamp
              and exists (select 1 from SesionVacante sv
                          where sv.sesionSimulacionId = s.id and sv.vacanteId = :vacanteId)
              and s.cupo > (select count(i) from InscripcionSesion i
                            where i.sesionSimulacionId = s.id and i.esVigente = true)
            order by s.fechaHora asc
            """)
    List<SesionSimulacion> disponiblesPara(@Param("organizacionId") Long organizacionId,
                                           @Param("vacanteId") Long vacanteId);

    /** Si hay al menos una, el candidato puede avanzar a elegir; si no, se queda esperando. */
    default boolean hayAlgunaDisponiblePara(Long organizacionId, Long vacanteId) {
        return !disponiblesPara(organizacionId, vacanteId).isEmpty();
    }
}
