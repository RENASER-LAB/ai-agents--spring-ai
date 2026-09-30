package com.renaser.ai.ai_engine.colaborador.repository;

import com.renaser.ai.ai_engine.colaborador.entity.ContratadoSinAlta;

import org.springframework.data.jpa.repository.JpaRepository;

public interface ContratadoSinAltaRepository extends JpaRepository<ContratadoSinAlta, Long> {

    boolean existsByPostulacionId(Long postulacionId);
}
