package com.renaser.ai.ai_engine.perfilintegral.repository;

import com.renaser.ai.ai_engine.perfilintegral.entity.PerfilTalento;

import org.springframework.data.jpa.repository.JpaRepository;

public interface PerfilTalentoRepository extends JpaRepository<PerfilTalento, Long> {

    java.util.Optional<PerfilTalento> findByPostulacionId(Long postulacionId);

    // En bloque, para el ranking. Pedirlo de una en una son once consultas por
    // candidato, y esa pantalla existe justamente para ver la tanda entera.
    java.util.List<PerfilTalento> findByPostulacionIdIn(java.util.List<Long> postulacionIds);
}
