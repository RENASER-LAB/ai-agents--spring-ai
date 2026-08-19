package com.renaser.ai.ai_engine.perfilintegral.repository;

import com.renaser.ai.ai_engine.perfilintegral.entity.HallazgoPerfil;

import org.springframework.data.jpa.repository.JpaRepository;

public interface HallazgoPerfilRepository extends JpaRepository<HallazgoPerfil, Long> {

    java.util.List<HallazgoPerfil> findByPerfilTalentoId(Long perfilTalentoId);

    void deleteByPerfilTalentoId(Long perfilTalentoId);

    // En bloque, para el ranking. Pedirlo de una en una son once consultas por
    // candidato, y esa pantalla existe justamente para ver la tanda entera.
    java.util.List<HallazgoPerfil> findByPerfilTalentoIdIn(java.util.List<Long> perfilIds);
}
