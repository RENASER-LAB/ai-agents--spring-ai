package com.renaser.ai.ai_engine.vacante.repository;

import com.renaser.ai.ai_engine.vacante.entity.LineaBusquedaCv;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Collection;
import java.util.List;

public interface LineaBusquedaCvRepository extends JpaRepository<LineaBusquedaCv, Long> {

    /** Las vivas de una vacante, en su orden. Vacía si la vacante no busca nada. */
    List<LineaBusquedaCv> findByVacanteIdAndArchivadaEnIsNullOrderByOrdenAsc(Long vacanteId);

    /** Las vivas de varias vacantes de una vez, para la lista del panel. */
    List<LineaBusquedaCv> findByVacanteIdInAndArchivadaEnIsNullOrderByVacanteIdAscOrdenAsc(
            Collection<Long> vacanteIds);

    /**
     * La versión más alta que tuvo la vacante, viva o archivada (0 si nunca tuvo). La siguiente
     * es esta más uno, también después de quitar todas: así un resultado de antes de quitarlas
     * nunca coincide con las que se escriban después.
     */
    @Query("select coalesce(max(l.versionLineas), 0) from LineaBusquedaCv l "
            + "where l.vacanteId = :vacanteId")
    int ultimaVersion(@Param("vacanteId") Long vacanteId);

    /** Archiva las vivas. Se escribe antes de crear las nuevas: el índice admite seis vivas. */
    @Modifying(flushAutomatically = true)
    @Query("update LineaBusquedaCv l set l.archivadaEn = :cuando "
            + "where l.vacanteId = :vacanteId and l.archivadaEn is null")
    int archivarLasVivas(@Param("vacanteId") Long vacanteId, @Param("cuando") Instant cuando);
}
