package com.renaser.ai.ai_engine.usuario.repository;

import com.renaser.ai.ai_engine.usuario.entity.Invitacion;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface InvitacionRepository extends JpaRepository<Invitacion, Long> {

    // Al canjear se busca siempre por el hash: es la consulta caliente.
    Optional<Invitacion> findByTokenHash(String tokenHash);

    List<Invitacion> findByOrganizacionIdOrderByCreadoEnDesc(Long organizacionId);

    Optional<Invitacion> findByIdAndOrganizacionId(Long id, Long organizacionId);
}
