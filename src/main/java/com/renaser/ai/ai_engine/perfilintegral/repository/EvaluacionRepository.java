package com.renaser.ai.ai_engine.perfilintegral.repository;

import com.renaser.ai.ai_engine.perfilintegral.entity.Evaluacion;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;

public interface EvaluacionRepository extends JpaRepository<Evaluacion, Long> {

    // Las que se pasaron de plazo y siguen abiertas. El sondeo las cierra: si nadie lo hace,
    // una evaluación abandonada deja la postulación esperando para siempre.
    List<Evaluacion> findByEstadoInAndVenceEnBefore(List<String> estados, Instant momento);

    // Las que aún no empezaron sobre una versión del banco. Al archivarla se repuntan a la
    // publicada vigente: iniciada_en es la frontera exacta que mira iniciar() para armar el
    // examen, así que antes de eso el candidato no nota el cambio (mismo criterio que la V20).
    List<Evaluacion> findByVersionBancoNivelIdAndIniciadaEnIsNull(Long versionBancoNivelId);
}
