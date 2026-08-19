package com.renaser.ai.ai_engine.prueba.repository;

import com.renaser.ai.ai_engine.prueba.entity.PreguntaPrueba;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface PreguntaPruebaRepository extends JpaRepository<PreguntaPrueba, Long> {

    List<PreguntaPrueba> findByTipo(String tipo);
    List<PreguntaPrueba> findByIdIn(List<Long> ids);
}
