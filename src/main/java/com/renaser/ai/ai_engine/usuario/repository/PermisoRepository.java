package com.renaser.ai.ai_engine.usuario.repository;

import com.renaser.ai.ai_engine.usuario.entity.Permiso;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface PermisoRepository extends JpaRepository<Permiso, Long> {

    Optional<Permiso> findByCodigo(String codigo);

    /**
     * El catálogo entero, en el orden en que se pinta.
     *
     * <p>Por grupo y luego por orden porque las dos columnas existen justo para esto: la
     * matriz de permisos del panel se lee por bloques —SESIONES, CONFIGURACION…— y dentro de
     * cada bloque en el orden que fijó la migración, no alfabético.
     */
    List<Permiso> findAllByOrderByGrupoAscOrdenAsc();
}
