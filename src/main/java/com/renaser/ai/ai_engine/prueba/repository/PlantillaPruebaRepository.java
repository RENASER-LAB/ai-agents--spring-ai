package com.renaser.ai.ai_engine.prueba.repository;

import com.renaser.ai.ai_engine.prueba.entity.PlantillaPrueba;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface PlantillaPruebaRepository extends JpaRepository<PlantillaPrueba, Long> {

    List<PlantillaPrueba> findByOrganizacionIdOrderByCreadoEnDesc(Long organizacionId);
    Optional<PlantillaPrueba> findByIdAndOrganizacionId(Long id, Long organizacionId);
}
