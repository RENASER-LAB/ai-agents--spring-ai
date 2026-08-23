package com.renaser.ai.ai_engine.perfilintegral.repository;

import com.renaser.ai.ai_engine.perfilintegral.entity.Dimension;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface DimensionRepository extends JpaRepository<Dimension, String> {

    // El catálogo en su orden de presentación, para el panel y para el importador.
    List<Dimension> findAllByOrderByOrden();
}
