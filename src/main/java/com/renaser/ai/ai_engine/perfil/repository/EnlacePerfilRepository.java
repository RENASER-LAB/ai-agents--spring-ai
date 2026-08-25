package com.renaser.ai.ai_engine.perfil.repository;

import com.renaser.ai.ai_engine.perfil.entity.EnlacePerfil;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface EnlacePerfilRepository extends JpaRepository<EnlacePerfil, Long> {

    List<EnlacePerfil> findByPerfilCandidatoIdOrderByTipo(Long perfilCandidatoId);

    boolean existsByPerfilCandidatoIdAndTipoAndUrl(Long perfilCandidatoId, String tipo, String url);

    void deleteByPerfilCandidatoId(Long perfilCandidatoId);
}
