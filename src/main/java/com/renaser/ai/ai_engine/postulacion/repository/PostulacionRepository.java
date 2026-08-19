package com.renaser.ai.ai_engine.postulacion.repository;

import com.renaser.ai.ai_engine.postulacion.entity.Postulacion;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PostulacionRepository extends JpaRepository<Postulacion, Long> {

    Optional<Postulacion> findByUuid(UUID uuid);
    List<Postulacion> findByUsuarioIdOrderByCreadoEnDesc(Long usuarioId);
    boolean existsByUsuarioIdAndVacanteId(Long usuarioId, Long vacanteId);
    Optional<Postulacion> findByIdAndOrganizacionId(Long id, Long organizacionId);

    // La bandeja del equipo: todo lo que espera a alguien, con la vacante a mano.
    // El filtro por responsable es el alcance SUS_VACANTES; con null no filtra.
    @Query("""
            select p from Postulacion p, EstadoPostulacion e, com.renaser.ai.ai_engine.vacante.entity.Vacante v
            where e.codigo = p.estadoCodigo
              and v.id = p.vacanteId
              and p.organizacionId = :organizacionId
              and e.esperaA = :esperaA
              and (:responsableUsuarioId is null or v.responsableUsuarioId = :responsableUsuarioId)
            order by p.movidoEn asc
            """)
    List<Postulacion> bandeja(@Param("organizacionId") Long organizacionId,
                              @Param("esperaA") String esperaA,
                              @Param("responsableUsuarioId") Long responsableUsuarioId);

    // El embudo de una vacante: cuántas postulaciones hay en cada estado
    @Query("""
            select p.estadoCodigo, count(p) from Postulacion p
            where p.vacanteId = :vacanteId
            group by p.estadoCodigo
            """)
    List<Object[]> embudo(@Param("vacanteId") Long vacanteId);

    List<Postulacion> findByVacanteIdOrderByCreadoEnDesc(Long vacanteId);

    // El sondeo cierra desde aquí las evaluaciones y las pruebas vencidas: la
    // postulación se conoce por el intento, no al revés.
    Optional<Postulacion> findByEvaluacionId(Long evaluacionId);
}
