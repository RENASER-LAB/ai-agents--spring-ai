package com.renaser.ai.ai_engine.pesos.repository;

import com.renaser.ai.ai_engine.pesos.entity.PesoEtapa;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface PesoEtapaRepository extends JpaRepository<PesoEtapa, PesoEtapa.Clave> {
    List<PesoEtapa> findByVersionPesosId(Long versionPesosId);
}
