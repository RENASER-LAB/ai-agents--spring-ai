package com.renaser.ai.ai_engine.prueba.repository;

import com.renaser.ai.ai_engine.prueba.entity.PreguntaVersionPlantilla;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface PreguntaVersionPlantillaRepository
        extends JpaRepository<PreguntaVersionPlantilla, PreguntaVersionPlantilla.Clave> {

    List<PreguntaVersionPlantilla> findByVersionPlantillaPruebaIdOrderByOrden(Long versionPlantillaPruebaId);
}
