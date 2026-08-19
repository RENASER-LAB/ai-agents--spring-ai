package com.renaser.ai.ai_engine.perfilintegral.repository;

import com.renaser.ai.ai_engine.perfilintegral.entity.NotaEtapa;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface NotaEtapaRepository extends JpaRepository<NotaEtapa, Long> {

    Optional<NotaEtapa> findByPostulacionIdAndEtapaCodigo(Long postulacionId, String etapaCodigo);
    List<NotaEtapa> findByPostulacionId(Long postulacionId);

    // En bloque, para el ranking. Pedirlo de una en una son once consultas por
    // candidato, y esa pantalla existe justamente para ver la tanda entera.
    List<NotaEtapa> findByPostulacionIdInAndEtapaCodigo(List<Long> postulacionIds, String etapaCodigo);
}
