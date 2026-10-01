package com.renaser.ai.ai_engine.perfilintegral.repository;

import com.renaser.ai.ai_engine.perfilintegral.entity.CriterioBanco;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface CriterioBancoRepository extends JpaRepository<CriterioBanco, Long> {

    List<CriterioBanco> findByVersionBancoIdOrderByOrdenAscIdAsc(Long versionBancoId);

    List<CriterioBanco> findByVersionBancoIdIn(List<Long> versionBancoIds);

    /** Al descartar un borrador entero, después de soltar sus preguntas. */
    void deleteByVersionBancoId(Long versionBancoId);
}
