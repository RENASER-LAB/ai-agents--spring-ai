package com.renaser.ai.ai_engine.perfil.repository;

import com.renaser.ai.ai_engine.perfil.entity.IdiomaPerfil;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface IdiomaPerfilRepository extends JpaRepository<IdiomaPerfil, Long> {

    List<IdiomaPerfil> findByPerfilCandidatoIdOrderByIdioma(Long perfilCandidatoId);

    void deleteByPerfilCandidatoId(Long perfilCandidatoId);
}
