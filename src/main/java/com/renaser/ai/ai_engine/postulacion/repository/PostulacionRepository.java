package com.renaser.ai.ai_engine.postulacion.repository;

import com.renaser.ai.ai_engine.postulacion.entity.Postulacion;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PostulacionRepository extends JpaRepository<Postulacion, Long> {

    /** Las postulaciones de una persona, en cualquiera de sus cuentas. Para el perfil. */
    @org.springframework.data.jpa.repository.Query("""
            select p from Postulacion p
              join Usuario u on u.id = p.usuarioId
             where u.personaId = :personaId
             order by p.creadoEn desc
            """)
    java.util.List<Postulacion> deLaPersona(
            @org.springframework.data.repository.query.Param("personaId") Long personaId);


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

    /**
     * Las postulaciones de una vacante que siguen en carrera, las nuevas arriba.
     *
     * <p>Quiénes son «en carrera» lo decide {@code PostulacionesEnCarrera} y no cada
     * llamador: aquí solo entra la lista de estados que esa clase pasa. Ver su javadoc.
     */
    @Query("""
            select p from Postulacion p
             where p.vacanteId = :vacanteId
               and p.estadoCodigo not in :terminados
             order by p.creadoEn desc
            """)
    List<Postulacion> enCarreraDeLaVacante(@Param("vacanteId") Long vacanteId,
                                           @Param("terminados") Collection<String> terminados);

    /**
     * Cuántas siguen en carrera en cada vacante de una empresa, en una sola consulta.
     *
     * <p>La lista del panel pinta ese número en cada fila. Preguntarlo vacante a vacante
     * convertiría una pantalla en tantas consultas como convocatorias tenga la empresa.
     */
    @Query("""
            select p.vacanteId, count(p) from Postulacion p
             where p.organizacionId = :organizacionId
               and p.estadoCodigo not in :terminados
             group by p.vacanteId
            """)
    List<Object[]> enCarreraPorVacante(@Param("organizacionId") Long organizacionId,
                                       @Param("terminados") Collection<String> terminados);

    long countByVacanteIdAndEstadoCodigoNotIn(Long vacanteId, Collection<String> estadoCodigos);

    // La guarda de «una vacante, una versión» pregunta si ya hay alguien midiéndose:
    // desde la primera postulación, los instrumentos de la vacante no se cambian.
    long countByVacanteId(Long vacanteId);

    // El sondeo cierra desde aquí las evaluaciones y las pruebas vencidas: la
    // postulación se conoce por el intento, no al revés.
    Optional<Postulacion> findByEvaluacionId(Long evaluacionId);

    /** La dueña del cuestionario técnico: cuelga de otra columna que la del perfil integral (V43). */
    Optional<Postulacion> findByEvaluacionTecnicaId(Long evaluacionTecnicaId);
}
