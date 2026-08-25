package com.renaser.ai.ai_engine.perfil.repository;

import com.renaser.ai.ai_engine.perfil.entity.EducacionPerfil;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface EducacionPerfilRepository extends JpaRepository<EducacionPerfil, Long> {

    List<EducacionPerfil> findByPerfilCandidatoIdOrderByOrden(Long perfilCandidatoId);

    void deleteByPerfilCandidatoId(Long perfilCandidatoId);
}
