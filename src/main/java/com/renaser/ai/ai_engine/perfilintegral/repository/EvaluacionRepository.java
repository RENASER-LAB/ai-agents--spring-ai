package com.renaser.ai.ai_engine.perfilintegral.repository;

import com.renaser.ai.ai_engine.perfilintegral.entity.Evaluacion;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
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

    /**
     * El estado guardado de la evaluación, <b>bloqueando su fila</b> hasta que acabe la
     * transacción.
     *
     * <p>Para entregar. Dos pestañas que entregaban a la vez leían las dos la evaluación
     * abierta y las dos la cerraban: dos pasos a «calificando» en el historial, dos avisos de
     * entrega y dos encargos a la IA (V70). Con el bloqueo la segunda espera, lee lo que dejó
     * la primera y falla como si hubiera llegado después.
     *
     * <p>Consulta nativa a propósito, como {@code PostulacionRepository.estadoBloqueandoLaFila}:
     * lo que se quiere es el valor de la base después de la espera, no la entidad que ya está
     * cargada en memoria con el estado de antes.
     */
    @Query(value = "select estado from evaluacion where id = :id for update", nativeQuery = true)
    String estadoBloqueandoLaFila(@Param("id") Long id);

    /**
     * Las evaluaciones del Perfil Integral sin terminar de una vacante —sin empezar o a
     * medias—, <b>bloqueando sus filas</b> hasta que acabe la transacción (V78).
     *
     * <p>Para vencerlas cuando la vacante se cierra. El bloqueo es lo que ordena el cierre con
     * una entrega que llega en el mismo instante: si la entrega confirma antes, al conseguir
     * la fila Postgres vuelve a mirar el {@code where}, la encuentra {@code TERMINADA} y no la
     * devuelve; si llega después, espera y la encuentra {@code VENCIDA}.
     *
     * <p>Como la de {@link #algunaTecnicaEmpezadaDeLaVacante}, va por la postulación: la
     * evaluación cuelga del usuario, no de la vacante.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select e from Evaluacion e
            where e.proposito = 'PERFIL_INTEGRAL'
              and e.estado in ('PENDIENTE', 'EN_CURSO')
              and e.id in (select p.evaluacionId from Postulacion p
                           where p.vacanteId = :vacanteId and p.evaluacionId is not null)
            order by e.id
            """)
    List<Evaluacion> sinTerminarDelPerfilDeLaVacante(@Param("vacanteId") Long vacanteId);

    /**
     * Las vacantes cerradas que todavía tienen alguna evaluación del Perfil Integral sin
     * terminar, con el momento en que se cerraron: {@code [vacanteId, cerradaEn]} (V78).
     *
     * <p>Lo que el sondeo recoge cada minuto. Casi siempre no devuelve nada, porque cerrar la
     * vacante vence las suyas en la misma transacción; quedan las de una postulación que
     * entró en el mismo instante del cierre y, el primer minuto tras desplegar la V78, las
     * que seguían abiertas en vacantes cerradas desde antes. Una vacante eliminada sigue su
     * propio camino (V60) y no sale aquí.
     */
    @Query("""
            select distinct v.id, v.cerradaEn from Evaluacion e, Postulacion p, Vacante v
            where p.evaluacionId = e.id and v.id = p.vacanteId
              and e.proposito = 'PERFIL_INTEGRAL'
              and e.estado in ('PENDIENTE', 'EN_CURSO')
              and v.estado = 'CERRADA' and v.eliminadaEn is null
            """)
    List<Object[]> vacantesCerradasConPerfilSinTerminar();
}
