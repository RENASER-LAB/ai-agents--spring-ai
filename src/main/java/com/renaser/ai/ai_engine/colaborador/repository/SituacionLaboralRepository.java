package com.renaser.ai.ai_engine.colaborador.repository;

import com.renaser.ai.ai_engine.colaborador.entity.SituacionLaboral;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;

public interface SituacionLaboralRepository extends JpaRepository<SituacionLaboral, Long> {

    List<SituacionLaboral> findByPeriodoIdOrderByVigenteDesdeAscIdAsc(Long periodoId);

    List<SituacionLaboral> findByColaboradorIdOrderByVigenteDesdeAscIdAsc(Long colaboradorId);

    List<SituacionLaboral> findByPeriodoIdIn(Collection<Long> periodoIds);

    /** Cuántas personas tienen o tuvieron este cargo: el aviso de renombrar lo dice. */
    @Query("select count(distinct s.colaboradorId) from SituacionLaboral s "
            + "where s.puestoId = :puestoId and s.anuladaEn is null")
    long personasConElCargo(@Param("puestoId") Long puestoId);

    /** Cuántas personas tienen o tuvieron cada cargo, de una tanda de cargos. */
    @Query("select s.puestoId, count(distinct s.colaboradorId) from SituacionLaboral s "
            + "where s.puestoId in :puestoIds and s.anuladaEn is null group by s.puestoId")
    List<Object[]> personasPorCargo(@Param("puestoIds") Collection<Long> puestoIds);
}
