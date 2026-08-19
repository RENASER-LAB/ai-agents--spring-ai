package com.renaser.ai.ai_engine.usuario.repository;

import com.renaser.ai.ai_engine.usuario.entity.UsuarioRol;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface UsuarioRolRepository extends JpaRepository<UsuarioRol, UsuarioRol.Clave> {
    List<UsuarioRol> findByUsuarioId(Long usuarioId);
    long countByRolId(Long rolId);
}
