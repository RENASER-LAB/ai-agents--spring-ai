package com.renaser.ai.ai_engine.notificacion.repository;

import com.renaser.ai.ai_engine.notificacion.entity.CorreoEnviado;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface CorreoEnviadoRepository extends JpaRepository<CorreoEnviado, Long> {
    List<CorreoEnviado> findByUsuarioIdOrderByEnviadoEnDesc(Long usuarioId);
}
