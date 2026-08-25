package com.renaser.ai.ai_engine.perfil.repository;

import com.renaser.ai.ai_engine.perfil.entity.CertificacionPerfil;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface CertificacionPerfilRepository extends JpaRepository<CertificacionPerfil, Long> {

    List<CertificacionPerfil> findByPerfilCandidatoIdOrderByNombre(Long perfilCandidatoId);

    void deleteByPerfilCandidatoId(Long perfilCandidatoId);
}
