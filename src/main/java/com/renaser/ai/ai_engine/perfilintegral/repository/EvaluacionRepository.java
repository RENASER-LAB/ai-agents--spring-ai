package com.renaser.ai.ai_engine.perfilintegral.repository;

import com.renaser.ai.ai_engine.perfilintegral.entity.Evaluacion;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;

public interface EvaluacionRepository extends JpaRepository<Evaluacion, Long> {

    // Las que se pasaron de plazo y siguen abiertas. El sondeo las cierra: si nadie lo hace,
    // una evaluación abandonada deja la postulación esperando para siempre.
    List<Evaluacion> findByEstadoInAndVenceEnBefore(List<String> estados, Instant momento);

    /**
     * Las vencidas de un propósito.
     *
     * <p>Los dos exámenes vencen distinto: el del perfil integral se da por VENCIDA y cierra
     * la postulación; el cuestionario técnico se ENTREGA como esté, y lo que no contestó
     * cuenta cero. Sin filtrar por propósito, cada barrido se llevaría por delante los del
     * otro (V43).
     */
    List<Evaluacion> findByPropositoAndEstadoInAndVenceEnBefore(
            String proposito, List<String> estados, Instant momento);

    // Las que aún no empezaron sobre una versión del banco. Al archivarla se repuntan a la
    // publicada vigente: iniciada_en es la frontera exacta que mira iniciar() para armar el
    // examen, así que antes de eso el candidato no nota el cambio (mismo criterio que la V20).
    List<Evaluacion> findByVersionBancoNivelIdAndIniciadaEnIsNull(Long versionBancoNivelId);

    /**
     * ¿Alguien de esta vacante ya abrió su cuestionario técnico?
     *
     * <p>Gemela de {@code IntentoPruebaRepository.algunoEmpezadoDeLaVacante}: la misma
     * pregunta para el otro instrumento de la etapa (V43).
     *
     * <p>⚠️ <b>Va por la postulación y no puede ir de otra forma.</b> La evaluación cuelga
     * del usuario, no de la vacante —no tiene columna que mirar—, así que el camino es
     * {@code postulacion.vacanteId} → {@code postulacion.evaluacionTecnicaId} → aquí.
     */
    @Query("""
            select count(e) > 0 from Evaluacion e
            where e.iniciadaEn is not null
              and e.id in (select p.evaluacionTecnicaId from Postulacion p
                           where p.vacanteId = :vacanteId and p.evaluacionTecnicaId is not null)
            """)
    boolean algunaTecnicaEmpezadaDeLaVacante(@Param("vacanteId") Long vacanteId);
}
