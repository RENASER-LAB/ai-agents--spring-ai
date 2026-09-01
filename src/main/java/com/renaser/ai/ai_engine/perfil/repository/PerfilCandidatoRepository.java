package com.renaser.ai.ai_engine.perfil.repository;

import com.renaser.ai.ai_engine.perfil.entity.PerfilCandidato;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface PerfilCandidatoRepository extends JpaRepository<PerfilCandidato, Long> {

    Optional<PerfilCandidato> findByPersonaId(Long personaId);

    // Los perfiles de una tanda entera de una vez. Existe para que el ranking no pida uno
    // por candidato: esa pantalla se abre para mirar a cien personas a la vez.
    List<PerfilCandidato> findByPersonaIdIn(List<Long> personaIds);
}
