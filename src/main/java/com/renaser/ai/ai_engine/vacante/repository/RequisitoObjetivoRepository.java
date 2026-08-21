package com.renaser.ai.ai_engine.vacante.repository;

import com.renaser.ai.ai_engine.vacante.entity.RequisitoObjetivo;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface RequisitoObjetivoRepository extends JpaRepository<RequisitoObjetivo, Long> {
    List<RequisitoObjetivo> findByVacanteIdAndEsActivoTrue(Long vacanteId);
    List<RequisitoObjetivo> findByVacanteId(Long vacanteId);

    // Los de un lote de vacantes, para el tablón público: una consulta por vacante en la
    // página que ve todo el que pasa es lo que más se paga de todo el portal.
    List<RequisitoObjetivo> findByVacanteIdInAndEsActivoTrue(List<Long> vacanteIds);
}
