package com.renaser.ai.ai_engine.vacante.repository;

import com.renaser.ai.ai_engine.vacante.entity.Puesto;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface PuestoRepository extends JpaRepository<Puesto, Long> {
    List<Puesto> findByOrganizacionIdAndEsActivoTrueOrderByNombre(Long organizacionId);
    Optional<Puesto> findByIdAndOrganizacionId(Long id, Long organizacionId);
}
