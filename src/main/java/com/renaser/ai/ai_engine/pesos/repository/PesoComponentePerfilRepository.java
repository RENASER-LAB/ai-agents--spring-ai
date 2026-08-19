package com.renaser.ai.ai_engine.pesos.repository;

import com.renaser.ai.ai_engine.pesos.entity.PesoComponentePerfil;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface PesoComponentePerfilRepository extends JpaRepository<PesoComponentePerfil, PesoComponentePerfil.Clave> {
    List<PesoComponentePerfil> findByVersionPesosId(Long versionPesosId);
}
