package com.renaser.ai.ai_engine.notificacion.repository;

import com.renaser.ai.ai_engine.notificacion.entity.PreferenciaAviso;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface PreferenciaAvisoRepository extends JpaRepository<PreferenciaAviso, Long> {

    /**
     * Crea la fila con los valores por defecto si todavía no existe (V92). Sin chocar: dos
     * peticiones a la vez no revientan contra la clave primaria, la segunda no hace nada.
     */
    @Modifying(flushAutomatically = true)
    @Query(value = """
            insert into preferencia_aviso (persona_id) values (:personaId)
            on conflict (persona_id) do nothing
            """, nativeQuery = true)
    int asegurar(@Param("personaId") Long personaId);

    /**
     * La fila, bloqueada hasta el final de la transacción: activar WhatsApp la sujeta mientras
     * habla con el bot, y así el doble clic pasa de uno en uno.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from PreferenciaAviso p where p.personaId = :personaId")
    Optional<PreferenciaAviso> paraEscribir(@Param("personaId") Long personaId);
}
