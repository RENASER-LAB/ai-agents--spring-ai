package com.renaser.ai.ai_engine.postulacion.repository;

import com.renaser.ai.ai_engine.postulacion.entity.TransicionEstado;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface TransicionEstadoRepository extends JpaRepository<TransicionEstado, Long> {
    List<TransicionEstado> findByPostulacionIdOrderByOcurridaEnAsc(Long postulacionId);
}
