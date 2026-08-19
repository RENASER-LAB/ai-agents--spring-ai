package com.renaser.ai.ai_engine.simulacion.repository;

import com.renaser.ai.ai_engine.simulacion.entity.TramoSimulacion;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface TramoSimulacionRepository extends JpaRepository<TramoSimulacion, Long> {

    List<TramoSimulacion> findBySesionSimulacionIdOrderByMinutoInicio(Long sesionSimulacionId);
}
