package com.renaser.ai.ai_engine.perfilintegral.service;

import com.renaser.ai.ai_engine.perfilintegral.entity.Evaluacion;
import com.renaser.ai.ai_engine.perfilintegral.repository.EvaluacionRepository;
import com.renaser.ai.ai_engine.postulacion.entity.Postulacion;
import com.renaser.ai.ai_engine.postulacion.repository.PostulacionRepository;
import com.renaser.ai.ai_engine.postulacion.service.MaquinaEstados;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Instant;
import java.util.List;

/**
 * El Perfil Integral termina cuando la vacante se cierra
 * (specs/Ready/perfil-integral-hasta-el-cierre-de-la-vacante, V78).
 *
 * <p>Hasta la V78 la evaluación vencía a los 14 días de postular, aunque la vacante siguiera
 * recibiendo gente. Ahora nace sin fecha y la vence el cierre de la vacante, a mano o por su
 * fecha límite. Lo que pasa entonces vive aquí, en un solo sitio, para que el cierre a mano, el
 * cierre por fecha y el sondeo hagan exactamente lo mismo:
 *
 * <ul>
 *   <li>cada evaluación del Perfil Integral sin terminar de la vacante —sin empezar o a
 *       medias— pasa a {@code VENCIDA}, con el momento del cierre como límite. Lo respondido a
 *       medias no se califica;</li>
 *   <li>la postulación que seguía esperándola ({@code PERFIL_TURNO_CANDIDATO}) se cierra con
 *       {@code PLAZO_VENCIDO}, como transición del sistema y con los avisos de siempre: el
 *       correo, la campana y, con WhatsApp v2, el resultado;</li>
 *   <li>la que el equipo ya movió a otra etapa, la de quien se retiró o quedó fuera, no se
 *       toca: su evaluación vence en silencio (AC-13).</li>
 * </ul>
 *
 * <p>⚠️ <b>Es la cuarta transición sin persona</b>, junto al cierre por fecha de la vacante: no
 * hay texto que pueda prometer que nada es automático (ver docs/03-ESTADOS-POSTULACION.md).
 */
@Service
@Slf4j
public class FinDelPerfilIntegral {

    /** El turno en el que la postulación espera la evaluación. Solo esa se cierra. */
    static final String ESPERA_LA_EVALUACION = "PERFIL_TURNO_CANDIDATO";
    static final String VENCIDA = "VENCIDA";
    static final String MOTIVO = "PLAZO_VENCIDO";

    private final EvaluacionRepository evaluaciones;
    private final PostulacionRepository postulaciones;
    private final MaquinaEstados maquina;
    private final TransactionTemplate enSuTransaccion;

    public FinDelPerfilIntegral(EvaluacionRepository evaluaciones,
                                PostulacionRepository postulaciones, MaquinaEstados maquina,
                                PlatformTransactionManager transacciones) {
        this.evaluaciones = evaluaciones;
        this.postulaciones = postulaciones;
        this.maquina = maquina;
        this.enSuTransaccion = new TransactionTemplate(transacciones);
        this.enSuTransaccion.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    /**
     * La vacante se acaba de cerrar: vence su Perfil Integral sin terminar.
     *
     * <p>Corre dentro de la transacción del cierre, con la vacante ya bloqueada: quien postula
     * o entrega la pide compartida, así que nadie entra ni entrega a mitad. Repetirlo no hace
     * nada: lo ya vencido no vuelve a salir de la consulta, y nadie recibe dos avisos.
     *
     * @param cerradaEn el momento del cierre, que queda como límite de cada evaluación
     * @return cuántas postulaciones se cerraron
     */
    @Transactional
    public int alCerrarseLaVacante(Long vacanteId, Instant cerradaEn) {
        Instant limite = cerradaEn == null ? Instant.now() : cerradaEn;
        int cerradas = 0;
        for (Evaluacion evaluacion : evaluaciones.sinTerminarDelPerfilDeLaVacante(vacanteId)) {
            evaluacion.setEstado(VENCIDA);
            evaluacion.setVenceEn(limite);
            evaluaciones.save(evaluacion);
            Postulacion postulacion = postulaciones.findByEvaluacionId(evaluacion.getId())
                    .orElse(null);
            if (postulacion != null && seguiaEsperandola(postulacion)) {
                maquina.transicionar(postulacion, "CERRADA", null, null, true, false, MOTIVO);
                cerradas++;
            }
        }
        return cerradas;
    }

    /**
     * Si la postulación sigue en el turno de la evaluación, leído con su fila bloqueada: si el
     * equipo la movía en ese mismo instante, manda lo que confirmó.
     */
    private boolean seguiaEsperandola(Postulacion postulacion) {
        return ESPERA_LA_EVALUACION.equals(postulacion.getEstadoCodigo())
                && ESPERA_LA_EVALUACION.equals(
                        postulaciones.estadoBloqueandoLaFila(postulacion.getId()));
    }

    /**
     * El sondeo: lo que quedó abierto en una vacante ya cerrada.
     *
     * <p>Una transacción por vacante: si una falla, las demás siguen, y la que falló se
     * reintenta en la vuelta siguiente.
     *
     * @return cuántas postulaciones se cerraron
     */
    public int enLasVacantesCerradas() {
        int cerradas = 0;
        List<Object[]> pendientes = evaluaciones.vacantesCerradasConPerfilSinTerminar();
        for (Object[] fila : pendientes) {
            Long vacanteId = (Long) fila[0];
            Instant cerradaEn = (Instant) fila[1];
            try {
                Integer suyas = enSuTransaccion.execute(t -> alCerrarseLaVacante(vacanteId, cerradaEn));
                cerradas += suyas == null ? 0 : suyas;
            } catch (RuntimeException e) {
                log.error("No se pudo vencer el Perfil Integral de la vacante cerrada {}; se "
                        + "reintenta en el próximo ciclo: {}", vacanteId, e.getMessage());
            }
        }
        return cerradas;
    }
}
