package com.renaser.ai.ai_engine.simulacion.repository;

import com.renaser.ai.ai_engine.simulacion.entity.InscripcionSesion;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface InscripcionSesionRepository extends JpaRepository<InscripcionSesion, Long> {

    Optional<InscripcionSesion> findByPostulacionIdAndEsVigenteTrue(Long postulacionId);
    List<InscripcionSesion> findBySesionSimulacionIdAndEsVigenteTrue(Long sesionSimulacionId);
    long countBySesionSimulacionIdAndEsVigenteTrue(Long sesionSimulacionId);
}
