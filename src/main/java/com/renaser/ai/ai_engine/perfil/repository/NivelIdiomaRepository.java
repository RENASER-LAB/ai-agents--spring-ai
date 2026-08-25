package com.renaser.ai.ai_engine.perfil.repository;

import com.renaser.ai.ai_engine.perfil.entity.NivelIdioma;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface NivelIdiomaRepository extends JpaRepository<NivelIdioma, String> {

    List<NivelIdioma> findAllByOrderByOrden();
}
