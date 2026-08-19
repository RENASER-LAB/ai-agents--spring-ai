package com.renaser.ai.ai_engine.archivo.repository;

import com.renaser.ai.ai_engine.archivo.entity.Archivo;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface ArchivoRepository extends JpaRepository<Archivo, Long> {
    Optional<Archivo> findByIdAndOrganizacionId(Long id, Long organizacionId);
}
