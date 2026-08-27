package com.renaser.ai.ai_engine.perfil.repository;

import com.renaser.ai.ai_engine.perfil.entity.NivelEducativo;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface NivelEducativoRepository extends JpaRepository<NivelEducativo, String> {

    List<NivelEducativo> findAllByOrderByOrden();
}
