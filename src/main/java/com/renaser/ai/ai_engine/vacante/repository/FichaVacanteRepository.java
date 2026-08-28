package com.renaser.ai.ai_engine.vacante.repository;

import com.renaser.ai.ai_engine.vacante.entity.FichaVacante;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface FichaVacanteRepository extends JpaRepository<FichaVacante, Long> {

    Optional<FichaVacante> findByVacanteId(Long vacanteId);
}
