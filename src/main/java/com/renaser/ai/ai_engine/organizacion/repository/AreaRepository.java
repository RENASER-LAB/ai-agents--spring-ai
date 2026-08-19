package com.renaser.ai.ai_engine.organizacion.repository;

import com.renaser.ai.ai_engine.organizacion.entity.Area;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface AreaRepository extends JpaRepository<Area, Long> {
    List<Area> findByOrganizacionIdAndEsActivaTrueOrderByNombre(Long organizacionId);
}
