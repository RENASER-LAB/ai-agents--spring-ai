package com.renaser.ai.ai_engine.whatsapp.repository;

import com.renaser.ai.ai_engine.whatsapp.entity.EventoWhatsapp;

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

public interface EventoWhatsappRepository extends JpaRepository<EventoWhatsapp, UUID> {

    /** ¿Este hecho ya dejó su evento? La base también lo impide (uno por postulación, tipo y revisión). */
    boolean existsByPostulacionIdAndTipoAndRevision(Long postulacionId, String tipo, int revision);

    /** Los que toca mandar ya, los más viejos primero. */
    @Query("""
            select e.id from EventoWhatsapp e
            where e.estado = 'PENDIENTE' and e.proximoIntentoEn <= :ahora
            order by e.proximoIntentoEn
            """)
    List<UUID> pendientesPorEnviar(@Param("ahora") Instant ahora, Pageable tanda);

    /** Con el productor apagado no sale nada: solo se cierran los que ya caducaron. */
    @Query("""
            select e.id from EventoWhatsapp e
            where e.estado = 'PENDIENTE' and e.avisoCaducaEn <= :ahora
            order by e.avisoCaducaEn
            """)
    List<UUID> caducadosPorCerrar(@Param("ahora") Instant ahora, Pageable tanda);

    /** Uno, bloqueado y sin esperar: si otro sondeo lo tiene, este lo salta. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @QueryHints(@jakarta.persistence.QueryHint(name = "jakarta.persistence.lock.timeout", value = "-2"))
    @Query("select e from EventoWhatsapp e where e.id = :id")
    Optional<EventoWhatsapp> paraElSondeo(@Param("id") UUID id);

    List<EventoWhatsapp> findByVinculoWhatsappIdAndTipo(Long vinculoWhatsappId, String tipo);

    List<EventoWhatsapp> findByPostulacionIdOrderByCreadoEnAsc(Long postulacionId);

    /**
     * Los RESULTADO de estos vínculos cuyo aviso final sigue vigente: pendientes, o aceptados por
     * el bot y todavía dentro de su autorización. Son los que una revocación tiene que cancelar.
     */
    @Query("""
            select e from EventoWhatsapp e
            where e.vinculoWhatsappId in :vinculos and e.tipo = 'RESULTADO'
              and e.avisoAutorizado = true and e.avisoCaducaEn > :ahora
              and e.estado in ('PENDIENTE', 'ACEPTADO')
            """)
    List<EventoWhatsapp> avisosFinalesVigentesDe(@Param("vinculos") Collection<Long> vinculos,
                                                 @Param("ahora") Instant ahora);

    /** Los pendientes de estos vínculos pasan a OMITIDO con su motivo: ya no tocan. */
    @Modifying
    @Query("""
            update EventoWhatsapp e set e.estado = 'OMITIDO', e.motivo = :motivo,
                   e.proximoIntentoEn = null, e.actualizadoEn = :ahora
            where e.vinculoWhatsappId in :vinculos and e.estado = 'PENDIENTE'
            """)
    int omitirPendientesDe(@Param("vinculos") Collection<Long> vinculos, @Param("motivo") String motivo,
                           @Param("ahora") Instant ahora);

    /** El borrado de datos: llevan el número y el nombre, así que se van enteros. */
    @Modifying
    @Query("delete from EventoWhatsapp e where e.vinculoWhatsappId in :vinculos")
    int borrarDe(@Param("vinculos") Collection<Long> vinculos);
}
