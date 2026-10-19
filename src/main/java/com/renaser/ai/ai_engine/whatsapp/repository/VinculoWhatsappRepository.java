package com.renaser.ai.ai_engine.whatsapp.repository;

import com.renaser.ai.ai_engine.whatsapp.entity.VinculoWhatsapp;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface VinculoWhatsappRepository extends JpaRepository<VinculoWhatsapp, Long> {

    /** El vivo (PENDIENTE o ACTIVO) de una postulación, si lo hay: como mucho uno. */
    @Query("""
            select v from VinculoWhatsapp v
            where v.postulacionId = :postulacionId and v.estado in ('PENDIENTE', 'ACTIVO')
            """)
    Optional<VinculoWhatsapp> vivoDe(@Param("postulacionId") Long postulacionId);

    /** El mismo, bloqueando la fila: lo usan el botón y el cierre, que escriben sobre ella. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select v from VinculoWhatsapp v
            where v.postulacionId = :postulacionId and v.estado in ('PENDIENTE', 'ACTIVO')
            """)
    Optional<VinculoWhatsapp> vivoParaEscribir(@Param("postulacionId") Long postulacionId);

    /** El último de cada postulación, vivo o cerrado: lo que el portal pinta en cada proceso. */
    @Query("""
            select v from VinculoWhatsapp v
            where v.postulacionId in :postulaciones
              and v.id = (select max(o.id) from VinculoWhatsapp o
                           where o.postulacionId = v.postulacionId)
            """)
    List<VinculoWhatsapp> ultimosDe(@Param("postulaciones") Collection<Long> postulaciones);

    /** Los vivos de un número, bloqueados: vincular y dar de baja escriben sobre todos a la vez. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select v from VinculoWhatsapp v
            where v.telefono = :telefono and v.estado in ('PENDIENTE', 'ACTIVO')
            order by v.id desc
            """)
    List<VinculoWhatsapp> vivosDelNumeroParaEscribir(@Param("telefono") String telefono);

    /** Los activos de un número: de los que se cuentan novedades. */
    @Query("""
            select v from VinculoWhatsapp v
            where v.telefono = :telefono and v.estado = 'ACTIVO'
            order by v.vinculadoEn desc, v.id desc
            """)
    List<VinculoWhatsapp> activosDelNumero(@Param("telefono") String telefono);

    /**
     * Los que se cerraron por fin del proceso con el anuncio todavía vigente: si ese número
     * escribe ahora, el bot tiene que contestar «no tienes procesos activos», no «no te conozco».
     */
    @Query("""
            select count(v) > 0 from VinculoWhatsapp v
            where v.telefono = :telefono and v.motivoCierre = 'PROCESO_TERMINADO'
              and v.anuncioCaducaEn > :ahora and v.vinculadoEn is null
            """)
    boolean terminoConElAnuncioVigente(@Param("telefono") String telefono,
                                       @Param("ahora") Instant ahora);

    /** Los vivos de una persona, bloqueados: cambiar el celular los cierra todos. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select v from VinculoWhatsapp v
            where v.personaId = :personaId and v.estado in ('PENDIENTE', 'ACTIVO')
            """)
    List<VinculoWhatsapp> vivosDeLaPersonaParaEscribir(@Param("personaId") Long personaId);

    /** Todos los de una persona, vivos o cerrados: el borrado de datos se los lleva. */
    List<VinculoWhatsapp> findByPersonaId(Long personaId);

    /** Los anuncios que el bot todavía no confirmó y que vale la pena reintentar. */
    @Query("""
            select v.id from VinculoWhatsapp v
            where v.estado = 'PENDIENTE' and v.anuncioConfirmadoEn is null
              and v.anuncioCaducaEn > :ahora
              and v.anuncioProximoIntentoEn is not null and v.anuncioProximoIntentoEn <= :ahora
            order by v.anuncioProximoIntentoEn
            """)
    List<Long> anunciosPorReintentar(@Param("ahora") Instant ahora,
                                     org.springframework.data.domain.Pageable tanda);

    /** Las revocaciones que el bot todavía no recibió. */
    @Query("""
            select v.id from VinculoWhatsapp v
            where v.cerradoEn is not null and v.revocacionEnviadaEn is null
              and v.revocacionProximoIntentoEn is not null and v.revocacionProximoIntentoEn <= :ahora
            order by v.revocacionProximoIntentoEn
            """)
    List<Long> revocacionesPorEnviar(@Param("ahora") Instant ahora,
                                     org.springframework.data.domain.Pageable tanda);

    /** Todos los de un número, vivos o cerrados: la consulta v2 y la BAJA miran también los cerrados. */
    List<VinculoWhatsapp> findByTelefono(String telefono);

    /**
     * Los ACTIVOS de las postulaciones de una vacante que siguen en carrera (V76): a quién contarle
     * que la convocatoria cerró. Una postulación ya terminada no recibe ese aviso: su resultado
     * manda.
     */
    @Query("""
            select v from VinculoWhatsapp v, Postulacion p, EstadoPostulacion e
            where p.id = v.postulacionId and e.codigo = p.estadoCodigo
              and p.vacanteId = :vacanteId and v.estado = 'ACTIVO' and e.esFinal = false
            order by v.id
            """)
    List<VinculoWhatsapp> activosEnCarreraDeLaVacante(@Param("vacanteId") Long vacanteId);

    /**
     * Uno, bloqueando su fila y esperando a quien la tenga (V76): el cierre del vínculo y el
     * envío de su evento final se ponen de acuerdo sobre la revocación, uno detrás del otro.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select v from VinculoWhatsapp v where v.id = :id")
    Optional<VinculoWhatsapp> paraEscribir(@Param("id") Long id);

    /** Una fila para el sondeo, bloqueada y sin esperar: si otro la tiene, se salta. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @org.springframework.data.jpa.repository.QueryHints(
            @jakarta.persistence.QueryHint(name = "jakarta.persistence.lock.timeout", value = "-2"))
    @Query("select v from VinculoWhatsapp v where v.id = :id")
    Optional<VinculoWhatsapp> paraElSondeo(@Param("id") Long id);
}
