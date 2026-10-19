package com.renaser.ai.ai_engine.whatsapp.repository;

import com.renaser.ai.ai_engine.whatsapp.entity.ResultadoWhatsapp;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Collection;
import java.util.List;

public interface ResultadoWhatsappRepository extends JpaRepository<ResultadoWhatsapp, Long> {

    boolean existsByPostulacionId(Long postulacionId);

    /** El de una postulación: hay como mucho uno (la base lo impide). */
    java.util.Optional<ResultadoWhatsapp> findByPostulacionId(Long postulacionId);

    List<ResultadoWhatsapp> findByVinculoWhatsappIdIn(Collection<Long> vinculos);

    /**
     * Retira el acceso de los resultados de estos vínculos que todavía lo tenían: BAJA o cambio
     * del celular. Lo ya revocado conserva su fecha y su motivo.
     */
    @Modifying
    @Query("""
            update ResultadoWhatsapp r set r.accesoRevocadoEn = :ahora, r.accesoRevocadoMotivo = :motivo,
                   r.actualizadoEn = :ahora
            where r.vinculoWhatsappId in :vinculos and r.accesoRevocadoEn is null
            """)
    int revocarDe(@Param("vinculos") Collection<Long> vinculos, @Param("motivo") String motivo,
                  @Param("ahora") Instant ahora);

    /** El borrado de datos: los resultados de esos vínculos se van enteros. */
    @Modifying
    @Query("delete from ResultadoWhatsapp r where r.vinculoWhatsappId in :vinculos")
    int borrarDe(@Param("vinculos") Collection<Long> vinculos);

    /** Pasados sus 30 días ya no sirven para nada: se retiran (el expediente sigue intacto). */
    @Modifying
    @Query("delete from ResultadoWhatsapp r where r.consultableHasta < :ahora")
    int borrarVencidos(@Param("ahora") Instant ahora);
}
