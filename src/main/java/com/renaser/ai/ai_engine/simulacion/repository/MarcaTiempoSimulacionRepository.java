package com.renaser.ai.ai_engine.simulacion.repository;

import com.renaser.ai.ai_engine.simulacion.entity.MarcaTiempoSimulacion;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface MarcaTiempoSimulacionRepository extends JpaRepository<MarcaTiempoSimulacion, Long> {

    List<MarcaTiempoSimulacion> findByInscripcionSesionIdOrderByOcurridaEn(Long inscripcionSesionId);
    Optional<MarcaTiempoSimulacion> findByInscripcionSesionIdAndEvento(Long inscripcionSesionId, String evento);
}
