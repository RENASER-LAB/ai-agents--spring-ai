package com.renaser.ai.ai_engine.perfilintegral.repository;

import com.renaser.ai.ai_engine.perfilintegral.entity.ResultadoBusquedaCv;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Collection;
import java.util.List;

public interface ResultadoBusquedaCvRepository extends JpaRepository<ResultadoBusquedaCv, Long> {

    List<ResultadoBusquedaCv> findByPostulacionIdInAndArchivadoEnIsNull(Collection<Long> postulacionIds);

    /** Archiva los vivos de esas personas, antes de escribir los nuevos (índice de uno vivo). */
    @Modifying(flushAutomatically = true)
    @Query("update ResultadoBusquedaCv r set r.archivadoEn = :cuando "
            + "where r.postulacionId in :postulacionIds and r.archivadoEn is null")
    int archivarLosVivos(@Param("postulacionIds") Collection<Long> postulacionIds,
                         @Param("cuando") Instant cuando);
}
