package com.renaser.ai.ai_engine.colaborador.repository;

import com.renaser.ai.ai_engine.colaborador.entity.Colaborador;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface ColaboradorRepository extends JpaRepository<Colaborador, Long> {

    /** La ficha de quien pregunta, o nada. Lo ajeno es un 404, no un 403. */
    Optional<Colaborador> findByIdAndOrganizacionId(Long id, Long organizacionId);

    /**
     * La ficha de quien pregunta, con su fila bloqueada hasta que acabe la transacción.
     *
     * <p>Lo usa todo lo que escribe en una ficha —perfil, cambios, cese, reingreso y sus
     * anulaciones—. Dos peticiones a la vez sobre la misma ficha, como el doble clic en «Anular»,
     * pasan de una en una: la segunda espera y lee lo que dejó la primera, así que se encuentra
     * el cambio ya anulado o el cese ya deshecho y responde 409 en vez de repetirlo.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from Colaborador c where c.id = :id and c.organizacionId = :organizacionId")
    Optional<Colaborador> bloquear(@Param("id") Long id, @Param("organizacionId") Long organizacionId);

    Optional<Colaborador> findByOrganizacionIdAndTipoDocumentoAndNumeroDocumento(
            Long organizacionId, String tipoDocumento, String numeroDocumento);

    List<Colaborador> findByOrganizacionId(Long organizacionId);

    List<Colaborador> findByOrganizacionIdAndIdIn(Long organizacionId, Collection<Long> ids);

    boolean existsByOrganizacionId(Long organizacionId);

    /**
     * Pone en fila a quien da de alta el mismo documento en la misma empresa.
     *
     * <p>El índice único de la V64 ya impide dos fichas, pero el segundo se enteraría con un
     * error de integridad que no dice «ya es colaborador» ni enlaza la ficha. Con el candado,
     * el segundo espera a que el primero termine y ve la ficha que acaba de nacer. Es de
     * transacción: se suelta solo al confirmar o deshacer.
     */
    @Query(value = "select 1 from (select pg_advisory_xact_lock(:clave)) candado", nativeQuery = true)
    Integer esperarTurno(@Param("clave") long clave);
}
