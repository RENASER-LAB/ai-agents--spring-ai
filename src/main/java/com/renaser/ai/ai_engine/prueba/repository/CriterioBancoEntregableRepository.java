package com.renaser.ai.ai_engine.prueba.repository;

import com.renaser.ai.ai_engine.prueba.entity.CriterioBancoEntregable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;

public interface CriterioBancoEntregableRepository extends JpaRepository<CriterioBancoEntregable, Long> {

    List<CriterioBancoEntregable> findByCriterioBancoIdIn(Collection<Long> criterioBancoIds);

    List<CriterioBancoEntregable> findByEntregableRequeridoId(Long entregableRequeridoId);

    void deleteByCriterioBancoId(Long criterioBancoId);

    void deleteByEntregableRequeridoId(Long entregableRequeridoId);

    void deleteByCriterioBancoIdIn(Collection<Long> criterioBancoIds);
}
