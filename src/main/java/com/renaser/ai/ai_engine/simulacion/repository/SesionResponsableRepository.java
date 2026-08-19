package com.renaser.ai.ai_engine.simulacion.repository;

import com.renaser.ai.ai_engine.simulacion.entity.SesionResponsable;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface SesionResponsableRepository extends JpaRepository<SesionResponsable, SesionResponsable.Clave> {

    List<SesionResponsable> findBySesionSimulacionId(Long sesionSimulacionId);
    boolean existsBySesionSimulacionIdAndUsuarioId(Long sesionSimulacionId, Long usuarioId);
}
