package com.renaser.ai.ai_engine.prueba.service;

import com.renaser.ai.ai_engine.perfilintegral.entity.NotaEtapa;
import com.renaser.ai.ai_engine.perfilintegral.repository.NotaEtapaRepository;
import com.renaser.ai.ai_engine.postulacion.entity.Postulacion;
import com.renaser.ai.ai_engine.postulacion.repository.PostulacionRepository;
import com.renaser.ai.ai_engine.postulacion.service.MaquinaEstados;
import com.renaser.ai.ai_engine.prueba.entity.IntentoPrueba;
import com.renaser.ai.ai_engine.prueba.service.CalificacionDeLaPruebaPropia.Resultado;
import com.renaser.ai.ai_engine.vacante.entity.Vacante;
import com.renaser.ai.ai_engine.vacante.repository.VacanteRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;

/**
 * Lo que pasa con la nota de la etapa de una prueba escrita en el editor (V67), en un solo
 * sitio: al entregar, cuando la IA termina, cuando una persona ajusta y cuando cambian los
 * puntos o la guía.
 *
 * <p><b>La nota de la etapa solo existe con todos los criterios enteros</b> (punto 8). Al
 * completarse, la postulación pasa a «por confirmar» como hoy —la complete la IA o una
 * persona—, pero solo si sigue en «calificando»: a quien ya se movió no se le devuelve hacia
 * atrás, y recalcular por un cambio de puntos o de guía nunca mueve a nadie.
 *
 * <p>Es el paso que la plantilla daba en el agente aunque no hubiera criterios de IA (un
 * comentario de {@code ServicioPruebaImpl} lo prometía y el agente salía antes sin darlo).
 * Aquí lo da quien completa la rúbrica, sea quien sea.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class CierreDeLaPruebaPropia {

    public static final String ETAPA = "PRUEBA_PUESTO";
    public static final String CALIFICANDO = "PRUEBA_CALIFICANDO";
    public static final String POR_CONFIRMAR = "PRUEBA_POR_CONFIRMAR";

    private final CalificacionDeLaPruebaPropia calculo;
    private final NotaEtapaRepository notasEtapa;
    private final PostulacionRepository postulaciones;
    private final VacanteRepository vacantes;
    private final MaquinaEstados maquina;

    /**
     * Recién entregada (a mano, o sola al vencer con todo respondido) y ya en «calificando».
     *
     * <p>Las cerradas las cuenta el sistema ahora mismo (se calculan al leer). Si con eso ya
     * está entera —una prueba solo de cerradas— la nota sale aquí y pasa a «por confirmar».
     *
     * <p>⚠️ <b>No encola a la IA: lo dice.</b> Quien llama decide si la vacante califica sola.
     * Esta clase la usa también el puente del agente, y depender de la cola cerraría un
     * círculo de dependencias (cola → agentes → puente → esto → cola).
     *
     * @return si faltan criterios de IA por calificar
     */
    public boolean alEntregar(IntentoPrueba intento, Postulacion postulacion) {
        Resultado r = calculo.calcular(intento);
        if (r.completo()) {
            cerrar(postulacion, r, true);
            return false;
        }
        boolean faltaLaIa = r.criterios().stream().anyMatch(c -> c.pendiente() && c.esDeIa());
        if (!faltaLaIa) {
            log.info("La prueba de la postulación {} espera a una persona: {}",
                    postulacion.getId(), String.join(", ", r.pendientes()));
        }
        return faltaLaIa;
    }

    /**
     * Vuelve a contar la prueba de una persona y escribe la nota de la etapa si ya está
     * entera.
     *
     * @param moverSiSeCompleta si se completa y la postulación sigue en «calificando», pasa a
     *                          «por confirmar». Falso al recalcular por puntos o por guía.
     * @return la nota de la etapa, o nula si todavía falta algún criterio
     */
    public BigDecimal recalcular(IntentoPrueba intento, boolean moverSiSeCompleta) {
        if (intento.getEntregadoEn() == null || intento.isNoCompletada()) {
            return null;
        }
        Postulacion postulacion = postulaciones.findById(intento.getPostulacionId()).orElse(null);
        if (postulacion == null) {
            return null;
        }
        Resultado r = calculo.calcular(intento);
        if (!r.completo()) {
            log.info("La prueba de la postulación {} sigue sin nota de etapa: falta(n) {}",
                    postulacion.getId(), String.join(", ", r.pendientes()));
            return null;
        }
        return cerrar(postulacion, r, moverSiSeCompleta);
    }

    private BigDecimal cerrar(Postulacion postulacion, Resultado r, boolean mover) {
        BigDecimal nota = r.nota().setScale(2, RoundingMode.HALF_UP);
        Long versionPesosId = vacantes.findById(postulacion.getVacanteId())
                .map(Vacante::getVersionPesosId).orElse(null);
        NotaEtapa fila = notasEtapa.findByPostulacionIdAndEtapaCodigo(postulacion.getId(), ETAPA)
                .orElseGet(() -> NotaEtapa.builder()
                        .postulacionId(postulacion.getId())
                        .etapaCodigo(ETAPA)
                        .creadoEn(Instant.now())
                        .build());
        fila.setPuntaje(nota);
        fila.setVersionPesosId(versionPesosId);
        fila.setCalculadaEn(Instant.now());
        notasEtapa.save(fila);
        if (mover && CALIFICANDO.equals(postulacion.getEstadoCodigo())) {
            maquina.transicionar(postulacion, POR_CONFIRMAR, null, null, true, false, null);
        }
        return nota;
    }
}
