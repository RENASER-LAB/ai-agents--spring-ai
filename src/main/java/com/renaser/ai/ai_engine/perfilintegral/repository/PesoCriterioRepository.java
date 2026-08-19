package com.renaser.ai.ai_engine.perfilintegral.repository;

import com.renaser.ai.ai_engine.perfilintegral.entity.PesoCriterio;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface PesoCriterioRepository extends JpaRepository<PesoCriterio, PesoCriterio.Clave> {
    List<PesoCriterio> findByVersionPesosId(Long versionPesosId);

    // Cuánto vale un criterio global para un nivel concreto: los ocho del currículum, los
    // diez de simulación y las nueve métricas de validación pesan distinto según el nivel.
    Optional<PesoCriterio> findByVersionPesosIdAndNivelPuestoCodigoAndCriterioId(
            Long versionPesosId, String nivelPuestoCodigo, Long criterioId);

    // Todos los pesos de un nivel de golpe: lo que necesita la IA para puntuar los ocho
    // criterios del currículum en una sola pasada, sin una consulta por criterio.
    List<PesoCriterio> findByVersionPesosIdAndNivelPuestoCodigo(Long versionPesosId,
                                                                String nivelPuestoCodigo);
}
