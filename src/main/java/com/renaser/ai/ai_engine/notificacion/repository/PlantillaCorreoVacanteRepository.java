package com.renaser.ai.ai_engine.notificacion.repository;

import com.renaser.ai.ai_engine.notificacion.entity.PlantillaCorreoVacante;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface PlantillaCorreoVacanteRepository extends JpaRepository<PlantillaCorreoVacante, Long> {

    List<PlantillaCorreoVacante> findByVacanteIdOrderByAvisoCodigo(Long vacanteId);

    Optional<PlantillaCorreoVacante> findByVacanteIdAndAvisoCodigo(Long vacanteId, String avisoCodigo);
}
