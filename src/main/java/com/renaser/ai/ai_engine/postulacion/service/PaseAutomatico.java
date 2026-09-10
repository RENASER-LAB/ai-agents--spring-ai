package com.renaser.ai.ai_engine.postulacion.service;

import com.renaser.ai.ai_engine.perfilintegral.service.PuenteCalificacionIa;
import com.renaser.ai.ai_engine.postulacion.entity.EstadoPostulacion;
import com.renaser.ai.ai_engine.postulacion.entity.Postulacion;
import com.renaser.ai.ai_engine.postulacion.repository.PostulacionRepository;
import com.renaser.ai.ai_engine.vacante.entity.Vacante;
import com.renaser.ai.ai_engine.vacante.repository.VacanteRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

/**
 * Pasar una postulación a la etapa técnica sin que nadie lo pida.
 *
 * <p>Es el paso que antes daba una persona pulsando «confirmar» en el panel, y que casi
 * siempre era decir «sí» a lo que la máquina acababa de calcular. Con el interruptor de la
 * vacante encendido, el candidato recibe su prueba sin esperar a que nadie mire.
 *
 * <p><b>Está separado de quien lo dispara a propósito.</b> Quien escucha el final de la
 * calificación es {@link PaseAutomaticoTrasCalificar}; si el método transaccional viviera
 * allí y se llamara a sí mismo, Spring se saltaría el proxy y la transacción nueva no
 * existiría — que es justo el fallo que este reparto evita.
 *
 * <p>⚠️ <b>Solo lo inyecta ese oyente.</b> Depende, dando un rodeo largo, de la propia
 * calificación con IA; mientras nadie de esa cadena lo inyecte a él, no hay círculo. Ver
 * {@link com.renaser.ai.ai_engine.perfilintegral.service.RetratoTerminado}.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class PaseAutomatico {

    /** El único estado desde el que este pase tiene sentido. */
    private static final String DE = "PERFIL_POR_CONFIRMAR";

    private final PostulacionRepository postulaciones;
    private final VacanteRepository vacantes;
    private final MaquinaEstados maquina;
    private final EntradaEtapaTecnica entradaTecnica;
    private final PuenteCalificacionIa puente;

    /**
     * Avanza esta postulación si le toca, y no hace nada si no.
     *
     * <p>⚠️ <b>{@code REQUIRES_NEW}, y sin él esto no escribiría nada.</b> Se llama justo
     * después de que la calificación confirme sus cambios, y en ese momento la transacción
     * de fuera está cerrada pero <b>todavía atada al hilo</b>. Un método transaccional
     * normal se uniría a ella —a una transacción ya confirmada—, y todo lo que escribiera
     * se perdería sin un solo error: la postulación se quedaría donde estaba y nadie sabría
     * por qué.
     *
     * <p>Y tiene que envolver <b>las dos cosas a la vez</b>, crear el instrumento y mover la
     * postulación. Cada una por su lado se confirma sola, y un fallo entre medias deja el
     * examen creado con el candidato fuera de la etapa: desde ahí, volver a avanzarlo choca
     * contra la clave única y el panel solo sabe decir «ya existe un registro».
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void avanzarSiToca(Long postulacionId) {
        Postulacion p = postulaciones.findById(postulacionId).orElse(null);
        if (p == null) {
            return;
        }

        // Se compara el estado exacto y no «sigue en la etapa»: el siguiente estado se
        // calcula a partir de este, así que el punto de partida tiene que ser el que es. A
        // quien todavía está en su turno, o a quien una persona ya movió mientras la IA
        // trabajaba, no se le toca.
        if (!DE.equals(p.getEstadoCodigo())) {
            return;
        }

        Vacante vacante = vacantes.findById(p.getVacanteId()).orElse(null);
        if (vacante == null || !vacante.isCalificacionAutomatica()) {
            return;
        }

        /*
         * ⚠️ **La guarda que impide mandar a la prueba a quien no ha contestado el banco.**
         *
         * Hay cuatro caminos que terminan en un retrato: el botón de calificar la tanda, la
         * criba de una persona, el botón de la ficha y el recalibrado del evaluador. Los tres
         * primeros alcanzan a gente que está en su turno y todavía no ha respondido nada: se
         * le arma el retrato con el currículum a solas —el evaluador se salta porque no hay
         * respuestas que puntuar— y sin esta comprobación se le empujaría a la prueba del
         * puesto sin haber contestado nunca su evaluación.
         *
         * Va aquí y no en cada uno de los cuatro botones justamente por eso: escrito una vez,
         * vale para todos, y para el que se invente mañana.
         */
        if (vacante.isAplicaEvaluacion() && !puente.tieneEvaluacionEntregada(postulacionId)) {
            log.info("PASE_AUTOMATICO: la postulación {} no avanza todavía: su vacante lleva "
                    + "banco de preguntas y aún no lo ha entregado", postulacionId);
            return;
        }

        // Sin instrumento no se avanza, y no es un error: la vacante está a medio montar y
        // quien la lleva tiene que elegir la prueba. Reventar aquí daría el trabajo de la IA
        // por fallido y volvería a pagar el modelo al reintentarlo.
        if (!entradaTecnica.hayInstrumento(vacante)) {
            log.info("PASE_AUTOMATICO: la postulación {} se queda esperando: su vacante no "
                    + "tiene todavía con qué llenar la etapa técnica", postulacionId);
            return;
        }

        Optional<EstadoPostulacion> siguiente = maquina.siguiente(p.getEstadoCodigo());
        if (siguiente.isEmpty()) {
            return;
        }

        entradaTecnica.crearAlEntrar(p, vacante);
        // Con motivo escrito aunque sea del sistema: sin él, el historial del candidato
        // enseña un salto sin autor y sin explicación, y quien lo abra dentro de seis meses
        // no puede saber si lo movió alguien o la vacante.
        maquina.transicionar(p, siguiente.get().getCodigo(), null,
                "Pase automático: esta vacante califica y avanza sola", true, false, null);
        log.info("PASE_AUTOMATICO: la postulación {} pasa sola a {}",
                postulacionId, siguiente.get().getCodigo());
    }
}
