package com.renaser.ai.ai_engine.simulacion.repository;

import com.renaser.ai.ai_engine.simulacion.entity.SesionVacante;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface SesionVacanteRepository extends JpaRepository<SesionVacante, SesionVacante.Clave> {

    List<SesionVacante> findBySesionSimulacionId(Long sesionSimulacionId);
    List<SesionVacante> findByVacanteId(Long vacanteId);
}
