package com.renaser.ai.ai_engine.organizacion.repository;

import com.renaser.ai.ai_engine.organizacion.entity.OrganizacionRedSocial;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;

public interface OrganizacionRedSocialRepository extends JpaRepository<OrganizacionRedSocial, Long> {

    List<OrganizacionRedSocial> findByOrganizacionId(Long organizacionId);

    /**
     * Las de todas las empresas de un lote, en UNA consulta: el tablón es la pantalla más
     * pedida y se sirve sin cuenta, así que nunca una consulta por vacante.
     */
    List<OrganizacionRedSocial> findByOrganizacionIdIn(Collection<Long> organizacionIds);

    /** Ver {@link OrganizacionBeneficioRepository#borrarLosDe}: lo mismo con su índice único. */
    @Modifying(flushAutomatically = true)
    @Query("delete from OrganizacionRedSocial r where r.organizacionId = :organizacionId")
    void borrarLasDe(@Param("organizacionId") Long organizacionId);
}
