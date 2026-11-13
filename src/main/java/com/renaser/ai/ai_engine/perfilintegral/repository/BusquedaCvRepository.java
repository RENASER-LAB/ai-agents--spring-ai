package com.renaser.ai.ai_engine.perfilintegral.repository;

import com.renaser.ai.ai_engine.perfilintegral.entity.BusquedaCv;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface BusquedaCvRepository extends JpaRepository<BusquedaCv, Long> {

    Optional<BusquedaCv> findByPostulacionIdAndArchivadaEnIsNull(Long postulacionId);

    List<BusquedaCv> findByPostulacionIdInAndArchivadaEnIsNull(Collection<Long> postulacionIds);

    /** Archiva la viva de esas personas: sus líneas cambiaron o se volvió a buscar. */
    @Modifying(flushAutomatically = true)
    @Query("update BusquedaCv b set b.archivadaEn = :cuando "
            + "where b.postulacionId in :postulacionIds and b.archivadaEn is null")
    int archivarLasVivas(@Param("postulacionIds") Collection<Long> postulacionIds,
                         @Param("cuando") Instant cuando);
}
