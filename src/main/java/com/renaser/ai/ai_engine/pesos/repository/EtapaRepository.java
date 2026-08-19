package com.renaser.ai.ai_engine.pesos.repository;

import com.renaser.ai.ai_engine.pesos.entity.Etapa;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface EtapaRepository extends JpaRepository<Etapa, String> {
    List<Etapa> findAllByOrderByOrdenAsc();
}
