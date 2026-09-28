package com.renaser.ai.ai_engine.resena.repository;

import com.renaser.ai.ai_engine.resena.entity.Resena;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

/**
 * Las reseñas. Es un agregado con dueño —la empresa autora— y figura en la lista de la regla
 * de arquitectura que prohíbe buscarlo por id suelto desde un servicio con usuario: el panel
 * entra por la contratación de su empresa, el portal por la persona que pregunta.
 */
public interface ResenaRepository extends JpaRepository<Resena, Long> {

    /** La viva de una contratación: como mucho una, lo garantiza un índice parcial. */
    Optional<Resena> findByPostulacionIdAndBorradaEnIsNull(Long postulacionId);

    /** Una reseña de esta persona, viva. La puerta del portal: lo ajeno no se encuentra. */
    Optional<Resena> findByIdAndPersonaIdAndBorradaEnIsNull(Long id, Long personaId);

    /** Las vivas de una persona, visibles u ocultadas. */
    List<Resena> findByPersonaIdAndBorradaEnIsNull(Long personaId);

    /** Todas las de una persona, también las borradas: el borrado de datos se las lleva. */
    List<Resena> findByPersonaId(Long personaId);

    /**
     * Suma de estrellas y cuántas, de las VISIBLES de cada persona, en una sola consulta.
     *
     * <p>La columna «Reseñas» de la tabla pinta el promedio de toda la tanda: preguntarlo
     * fila por fila sería una consulta por candidato en la pantalla que existe para mirarlos
     * a todos a la vez. Se devuelve la suma y no el promedio para redondear en un solo sitio.
     *
     * @return filas {@code [personaId, sumaDeEstrellas, cantidad]}
     */
    @Query("""
            select r.personaId, sum(r.estrellas), count(r)
              from Resena r
             where r.personaId in :personaIds
               and r.borradaEn is null
               and r.ocultadaEn is null
             group by r.personaId
            """)
    List<Object[]> sumasVisiblesDePersonas(@Param("personaIds") Collection<Long> personaIds);

    /**
     * El título de la vacante de cada reseña: «Contratado como …».
     *
     * <p>Por la postulación de la propia reseña y no por id de vacante suelto: la vacante
     * puede ser de otra empresa —es la lectura entre empresas— y lo que la ata es la
     * contratación reseñada.
     *
     * @return filas {@code [resenaId, tituloDeLaVacante]}
     */
    @Query("""
            select r.id, v.titulo
              from Resena r,
                   com.renaser.ai.ai_engine.postulacion.entity.Postulacion p,
                   com.renaser.ai.ai_engine.vacante.entity.Vacante v
             where p.id = r.postulacionId
               and v.id = p.vacanteId
               and r.id in :ids
            """)
    List<Object[]> puestosDeLasResenas(@Param("ids") Collection<Long> ids);
}
