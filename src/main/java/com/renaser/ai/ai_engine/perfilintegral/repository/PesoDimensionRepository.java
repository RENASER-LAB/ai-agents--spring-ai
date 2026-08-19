package com.renaser.ai.ai_engine.perfilintegral.repository;

import com.renaser.ai.ai_engine.perfilintegral.entity.PesoDimension;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface PesoDimensionRepository extends JpaRepository<PesoDimension, PesoDimension.Clave> {
    List<PesoDimension> findByVersionPesosId(Long versionPesosId);
}
