package com.renaser.ai.ai_engine.simulacion.repository;

import com.renaser.ai.ai_engine.simulacion.entity.SesionVacante;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface SesionVacanteRepository extends JpaRepository<SesionVacante, SesionVacante.Clave> {

    List<SesionVacante> findBySesionSimulacionId(Long sesionSimulacionId);

    // Las de un lote de sesiones, para el listado del panel.
    List<SesionVacante> findBySesionSimulacionIdIn(List<Long> sesionIds);
    List<SesionVacante> findByVacanteId(Long vacanteId);
}
