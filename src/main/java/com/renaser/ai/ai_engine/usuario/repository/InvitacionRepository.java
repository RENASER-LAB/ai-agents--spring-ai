package com.renaser.ai.ai_engine.usuario.repository;

import com.renaser.ai.ai_engine.usuario.entity.Invitacion;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface InvitacionRepository extends JpaRepository<Invitacion, Long> {

    // Al canjear se busca siempre por el hash: es la consulta caliente.
    Optional<Invitacion> findByTokenHash(String tokenHash);

    // El gasto atómico del canje: leer la invitación y marcarla usada en dos pasos deja
    // una rendija —dos canjes simultáneos leen los dos «vigente» y crean dos cuentas—.
    // Este UPDATE condicional decide en la base quién llegó primero: al segundo le
    // devuelve 0 filas, porque cuando su UPDATE se ejecuta la fila ya tiene aceptada_en.
    @Modifying
    @Query("""
            update Invitacion i set i.aceptadaEn = :ahora
             where i.id = :id and i.aceptadaEn is null and i.revocadaEn is null""")
    int gastar(@Param("id") Long id, @Param("ahora") Instant ahora);

    List<Invitacion> findByOrganizacionIdOrderByCreadoEnDesc(Long organizacionId);

    Optional<Invitacion> findByIdAndOrganizacionId(Long id, Long organizacionId);
}
