package com.renaser.ai.ai_engine.organizacion.repository;

import com.renaser.ai.ai_engine.organizacion.entity.Organizacion;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface OrganizacionRepository extends JpaRepository<Organizacion, Long> {
    Optional<Organizacion> findByCodigo(String codigo);
}
