package com.renaser.ai.ai_engine.prueba.repository;

import com.renaser.ai.ai_engine.prueba.entity.VersionPlantillaPrueba;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface VersionPlantillaPruebaRepository extends JpaRepository<VersionPlantillaPrueba, Long> {

    List<VersionPlantillaPrueba> findByPlantillaPruebaIdOrderByVersionDesc(Long plantillaPruebaId);

    // La versión publicada más reciente de la plantilla que rige la vacante: es la que
    // se le fija al candidato al llegar a su turno, y a la que queda atado (RF-90).
    Optional<VersionPlantillaPrueba> findFirstByPlantillaPruebaIdAndEstadoOrderByPublicadaEnDesc(
            Long plantillaPruebaId, String estado);
}
