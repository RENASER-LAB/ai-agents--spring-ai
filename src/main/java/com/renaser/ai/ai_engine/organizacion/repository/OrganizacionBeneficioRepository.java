package com.renaser.ai.ai_engine.organizacion.repository;

import com.renaser.ai.ai_engine.organizacion.entity.OrganizacionBeneficio;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface OrganizacionBeneficioRepository extends JpaRepository<OrganizacionBeneficio, Long> {

    List<OrganizacionBeneficio> findByOrganizacionIdOrderByOrden(Long organizacionId);

    /**
     * Borra los de la empresa en el acto.
     *
     * <p>⚠️ Con una consulta y no con {@code deleteAll}: Hibernate inserta antes de borrar
     * dentro de la misma transacción, y el índice único (organización, orden) reventaría al
     * escribir los nuevos sobre los viejos todavía vivos.
     */
    @Modifying(flushAutomatically = true)
    @Query("delete from OrganizacionBeneficio b where b.organizacionId = :organizacionId")
    void borrarLosDe(@Param("organizacionId") Long organizacionId);
}
