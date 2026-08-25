package com.renaser.ai.ai_engine.perfil.repository;

import com.renaser.ai.ai_engine.perfil.entity.ExperienciaPerfil;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ExperienciaPerfilRepository extends JpaRepository<ExperienciaPerfil, Long> {

    List<ExperienciaPerfil> findByPerfilCandidatoIdOrderByOrden(Long perfilCandidatoId);

    void deleteByPerfilCandidatoId(Long perfilCandidatoId);
}
