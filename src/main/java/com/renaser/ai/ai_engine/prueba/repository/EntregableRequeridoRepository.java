package com.renaser.ai.ai_engine.prueba.repository;

import com.renaser.ai.ai_engine.prueba.entity.EntregableRequerido;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface EntregableRequeridoRepository extends JpaRepository<EntregableRequerido, Long> {

    List<EntregableRequerido> findByVersionPlantillaPruebaIdOrderByOrden(Long versionPlantillaPruebaId);
}
