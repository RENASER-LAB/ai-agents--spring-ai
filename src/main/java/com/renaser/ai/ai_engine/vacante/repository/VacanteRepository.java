package com.renaser.ai.ai_engine.vacante.repository;

import com.renaser.ai.ai_engine.vacante.entity.Vacante;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface VacanteRepository extends JpaRepository<Vacante, Long> {
    List<Vacante> findByOrganizacionIdAndEstadoOrderByPublicadaEnDesc(Long organizacionId, String estado);

    // El tablón público del portal: las publicadas de TODAS las empresas juntas. Es la
    // única consulta deliberadamente transversal — eso es ser plataforma tipo Indeed.
    List<Vacante> findByEstadoOrderByPublicadaEnDesc(String estado);
    List<Vacante> findByOrganizacionIdOrderByCreadoEnDesc(Long organizacionId);
    List<Vacante> findByOrganizacionIdAndResponsableUsuarioIdOrderByCreadoEnDesc(
            Long organizacionId, Long responsableUsuarioId);
    Optional<Vacante> findByIdAndOrganizacionId(Long id, Long organizacionId);
    boolean existsBySolicitudTalentoId(Long solicitudTalentoId);
}
