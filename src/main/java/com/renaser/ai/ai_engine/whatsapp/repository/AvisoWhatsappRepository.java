package com.renaser.ai.ai_engine.whatsapp.repository;

import com.renaser.ai.ai_engine.whatsapp.entity.AvisoWhatsapp;

import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.QueryHints;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AvisoWhatsappRepository extends JpaRepository<AvisoWhatsapp, UUID> {

    /** ¿Esta transición ya dejó un aviso de este tipo? La base también lo impide. */
    boolean existsByTransicionEstadoIdAndTipo(Long transicionEstadoId, String tipo);

    /** Los que toca mandar ya, los más viejos primero. */
    @Query("""
            select a.id from AvisoWhatsapp a
            where a.estado = 'PENDIENTE' and a.proximoIntentoEn <= :ahora
            order by a.proximoIntentoEn
            """)
    List<UUID> pendientesPorEnviar(@Param("ahora") Instant ahora, Pageable tanda);

    /** Uno, bloqueado y sin esperar: si otro sondeo lo tiene, este lo salta. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @QueryHints(@jakarta.persistence.QueryHint(name = "jakarta.persistence.lock.timeout", value = "-2"))
    @Query("select a from AvisoWhatsapp a where a.id = :id")
    Optional<AvisoWhatsapp> paraElSondeo(@Param("id") UUID id);

    List<AvisoWhatsapp> findByPostulacionIdOrderByCreadoEnAsc(Long postulacionId);

    /** Los que aún no salieron de estos vínculos pasan a OMITIDO: el vínculo se cerró antes. */
    @Modifying
    @Query("""
            update AvisoWhatsapp a set a.estado = 'OMITIDO', a.proximoIntentoEn = null,
                   a.actualizadoEn = :ahora
            where a.vinculoWhatsappId in :vinculos and a.estado = 'PENDIENTE'
            """)
    int omitirPendientesDe(@Param("vinculos") Collection<Long> vinculos, @Param("ahora") Instant ahora);

    /** El borrado de datos: llevan el número y el nombre, así que se van enteros. */
    @Modifying
    @Query("delete from AvisoWhatsapp a where a.vinculoWhatsappId in :vinculos")
    int borrarDe(@Param("vinculos") Collection<Long> vinculos);
}
