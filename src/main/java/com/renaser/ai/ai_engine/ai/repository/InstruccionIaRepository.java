package com.renaser.ai.ai_engine.ai.repository;

import com.renaser.ai.ai_engine.ai.model.InstruccionIa;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface InstruccionIaRepository extends JpaRepository<InstruccionIa, Long> {
    List<InstruccionIa> findByAgenteCodigoOrderByVersionDesc(String agenteCodigo);
    Optional<InstruccionIa> findFirstByAgenteCodigoAndEsActivaTrue(String agenteCodigo);
}
