package com.renaser.ai.ai_engine.perfil.repository;

import com.renaser.ai.ai_engine.perfil.entity.PerfilCandidato;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface PerfilCandidatoRepository extends JpaRepository<PerfilCandidato, Long> {

    Optional<PerfilCandidato> findByPersonaId(Long personaId);
}
