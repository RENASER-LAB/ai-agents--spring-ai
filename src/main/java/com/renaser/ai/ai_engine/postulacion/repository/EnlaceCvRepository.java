package com.renaser.ai.ai_engine.postulacion.repository;

import com.renaser.ai.ai_engine.postulacion.entity.EnlaceCv;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface EnlaceCvRepository extends JpaRepository<EnlaceCv, Long> {
    List<EnlaceCv> findByCvId(Long cvId);
}
