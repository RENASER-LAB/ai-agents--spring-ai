package com.renaser.ai.ai_engine.solicitud.repository;

import com.renaser.ai.ai_engine.solicitud.entity.ResultadoEsperado;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ResultadoEsperadoRepository extends JpaRepository<ResultadoEsperado, Long> {
    List<ResultadoEsperado> findBySolicitudTalentoIdOrderByOrden(Long solicitudTalentoId);
}
