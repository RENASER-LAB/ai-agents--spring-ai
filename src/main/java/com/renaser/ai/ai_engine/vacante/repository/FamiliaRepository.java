package com.renaser.ai.ai_engine.vacante.repository;

import com.renaser.ai.ai_engine.vacante.entity.Familia;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface FamiliaRepository extends JpaRepository<Familia, String> {
    List<Familia> findAllByOrderByNombreAsc();
}
