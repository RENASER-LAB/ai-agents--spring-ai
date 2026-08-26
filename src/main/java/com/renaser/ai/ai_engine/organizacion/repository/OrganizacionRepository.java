package com.renaser.ai.ai_engine.organizacion.repository;

import com.renaser.ai.ai_engine.organizacion.entity.Organizacion;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface OrganizacionRepository extends JpaRepository<Organizacion, Long> {
    Optional<Organizacion> findByCodigo(String codigo);

    // La dueña de la plataforma. Solo una puede serlo (índice único parcial, V37);
    // reemplaza al findByCodigo("RENASER") que ataba el código a un nombre.
    Optional<Organizacion> findByEsPlataformaTrue();
}
