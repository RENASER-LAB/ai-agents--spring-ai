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

    /**
     * Lo mismo, pero contando solo a los de las vacantes de un responsable.
     *
     * <p>Existe para que el conteo de la lista y la lista de inscritos digan lo mismo. A quien
     * mira con alcance {@code SUS_VACANTES} se le recortan los inscritos que puede abrir; si el
     * conteo siguiera siendo el total, la sesión diría «6» y la lista enseñaría dos, y eso no se
     * lee como un permiso: se lee como que faltan cuatro.
     *
     * <p>El recorte va en la consulta y no en un filtro en memoria porque un alcance es un
     * WHERE —lo dice {@code FiltroAlcance}—, y contar filas que se van a descartar es traerlas
     * para nada.
     *
     * @return pares {@code [sesionSimulacionId, cuántos]}; las sesiones sin nadie suyo no salen
     */
    @Query("""
            select i.sesionSimulacionId, count(i)
            from InscripcionSesion i, Postulacion p, Vacante v
            where i.sesionSimulacionId in :sesionIds and i.esVigente = true
              and p.id = i.postulacionId and v.id = p.vacanteId
              and v.responsableUsuarioId = :responsableId
            group by i.sesionSimulacionId
            """)
    List<Object[]> contarVigentesPorSesionDe(@Param("sesionIds") List<Long> sesionIds,
                                             @Param("responsableId") Long responsableId);
}
