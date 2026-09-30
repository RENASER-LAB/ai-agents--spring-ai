package com.renaser.ai.ai_engine.colaborador.repository;

import com.renaser.ai.ai_engine.colaborador.entity.CeseAnulado;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;

public interface CeseAnuladoRepository extends JpaRepository<CeseAnulado, Long> {

    List<CeseAnulado> findByPeriodoIdIn(Collection<Long> periodoIds);
}
