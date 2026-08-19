package com.renaser.ai.ai_engine.perfilintegral.repository;

import com.renaser.ai.ai_engine.perfilintegral.entity.AfirmacionCv;

import org.springframework.data.jpa.repository.JpaRepository;

public interface AfirmacionCvRepository extends JpaRepository<AfirmacionCv, Long> {

    java.util.List<AfirmacionCv> findByCvId(Long cvId);

    void deleteByCvId(Long cvId);
}
