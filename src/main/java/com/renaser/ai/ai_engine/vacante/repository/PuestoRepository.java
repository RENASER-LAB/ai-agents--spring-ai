package com.renaser.ai.ai_engine.vacante.repository;

import com.renaser.ai.ai_engine.vacante.entity.Puesto;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface PuestoRepository extends JpaRepository<Puesto, Long> {
    List<Puesto> findByOrganizacionIdAndEsActivoTrueOrderByNombre(Long organizacionId);
    Optional<Puesto> findByIdAndOrganizacionId(Long id, Long organizacionId);
    boolean existsByOrganizacionIdAndCodigo(Long organizacionId, String codigo);

    /** Todos, vivos y desactivados: el catálogo de cargos de Configuración (V64). */
    List<Puesto> findByOrganizacionIdOrderByNombre(Long organizacionId);

    /** Otro cargo con ese nombre, sin distinguir mayúsculas: la carga por Excel los busca así. */
    @Query("select count(p) > 0 from Puesto p where p.organizacionId = :organizacionId "
            + "and lower(trim(p.nombre)) = lower(trim(:nombre)) and (:excepto is null or p.id <> :excepto)")
    boolean existeConElNombre(@Param("organizacionId") Long organizacionId,
                              @Param("nombre") String nombre,
                              @Param("excepto") Long excepto);
}
