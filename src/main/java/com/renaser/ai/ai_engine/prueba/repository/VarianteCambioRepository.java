package com.renaser.ai.ai_engine.prueba.repository;

import com.renaser.ai.ai_engine.prueba.entity.VarianteCambio;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface VarianteCambioRepository extends JpaRepository<VarianteCambio, Long> {

    List<VarianteCambio> findByVersionPlantillaPruebaId(Long versionPlantillaPruebaId);
}
