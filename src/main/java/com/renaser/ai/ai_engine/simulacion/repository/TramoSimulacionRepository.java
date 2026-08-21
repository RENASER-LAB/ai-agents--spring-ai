package com.renaser.ai.ai_engine.simulacion.repository;

import com.renaser.ai.ai_engine.simulacion.entity.TramoSimulacion;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface TramoSimulacionRepository extends JpaRepository<TramoSimulacion, Long> {

    // Los de un lote de sesiones, para el listado del panel: cada sesión tiene seis tramos
    // y pedirlos de una en una era una consulta por fila de la tabla.
    List<TramoSimulacion> findBySesionSimulacionIdInOrderByMinutoInicio(List<Long> sesionIds);

    List<TramoSimulacion> findBySesionSimulacionIdOrderByMinutoInicio(Long sesionSimulacionId);
}
