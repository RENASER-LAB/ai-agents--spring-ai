package com.renaser.ai.ai_engine.colaborador.repository;

import com.renaser.ai.ai_engine.colaborador.entity.PeriodoLaboral;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface PeriodoLaboralRepository extends JpaRepository<PeriodoLaboral, Long> {

    List<PeriodoLaboral> findByColaboradorIdOrderByFechaIngresoAscIdAsc(Long colaboradorId);

    /** El periodo actual: el último que se abrió. */
    Optional<PeriodoLaboral> findFirstByColaboradorIdOrderByFechaIngresoDescIdDesc(Long colaboradorId);

    List<PeriodoLaboral> findByColaboradorIdIn(Collection<Long> colaboradorIds);

    /** El periodo que abrió una contratación, si ya se dio de alta. */
    Optional<PeriodoLaboral> findFirstByPostulacionId(Long postulacionId);
}
