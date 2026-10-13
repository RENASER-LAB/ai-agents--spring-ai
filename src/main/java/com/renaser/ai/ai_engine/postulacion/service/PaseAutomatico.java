package com.renaser.ai.ai_engine.postulacion.service;

import com.renaser.ai.ai_engine.perfilintegral.service.PuenteCalificacionIa;
import com.renaser.ai.ai_engine.perfilintegral.service.UmbralDelPerfilIntegral;
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

import java.math.BigDecimal;
import java.util.Optional;
import java.util.function.BiFunction;

/**
 * Pasar una postulación a la etapa técnica sin que nadie lo pida.
 *
 * <p>Es el paso que antes daba una persona pulsando «confirmar» en el panel, y que casi
 * siempre era decir «sí» a lo que la máquina acababa de calcular. Con el interruptor de la
 * vacante encendido, el candidato recibe su prueba sin esperar a que nadie mire.
 *
 * <p><b>Solo con la nota completa del Perfil Integral, y solo si llega a la mínima (V72).</b>
 * Quien no llega, o no tiene nota completa, se queda en «por confirmar» y decide una persona:
 * ninguna nota lleva a nadie a «No continúa». Qué nota cuenta y cuándo llega lo dice
 * {@link UmbralDelPerfilIntegral}, el mismo que usa la ficha para explicar por qué alguien
 * espera.
 *
 * <p><b>Dos momentos</b>, y en los dos la postulación ya está en «por confirmar»:
 * <ul>
 *   <li>{@link #avanzarSiToca}: al terminar la calificación del Perfil Integral.</li>
 *   <li>{@link #avanzarTrasRecalcular}: cuando una persona ajusta una respuesta o recalifica y
 *       su nota cambia mientras espera. A quien ya está en la prueba no se le toca aunque la
 *       nota baje.</li>
 * </ul>
 *
 * <p>Hasta la V72 existía un tercero, el pase al instante (V70): al entregar el banco, o al
 * postular sin banco, se pasaba a la prueba sin esperar a la IA porque el pase no miraba la
 * nota. Ahora la mira, así que esperarla es obligatorio y aquel momento se retiró.
 *
 * <p><b>Está separado de quien lo dispara a propósito.</b> Quien escucha es
 * {@link PaseAutomaticoTrasCalificar}; si el método transaccional viviera allí y se llamara a
 * sí mismo, Spring se saltaría el proxy y la transacción nueva no existiría — que es justo el
 * fallo que este reparto evita.
 *
 * <p>⚠️ <b>Solo lo inyecta ese oyente.</b> Depende, dando un rodeo largo, de la propia
 * calificación con IA; mientras nadie de esa cadena lo inyecte a él, no hay círculo. Ver
 * {@link com.renaser.ai.ai_engine.perfilintegral.service.RetratoTerminado}.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class PaseAutomatico {

    /** El único estado desde el que el pase tiene sentido. */
    private static final String DE = "PERFIL_POR_CONFIRMAR";

    private final PostulacionRepository postulaciones;
    private final VacanteRepository vacantes;
    private final MaquinaEstados maquina;
    private final EntradaEtapaTecnica entradaTecnica;
    private final PuenteCalificacionIa puente;
    private final UmbralDelPerfilIntegral umbral;

    /**
     * Al terminar la nota del Perfil Integral: avanza esta postulación si le toca, y no hace
     * nada si no.
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
     *
     * @return si quedó en la prueba
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean avanzarSiToca(Long postulacionId) {
        return avanzar(postulacionId, UmbralDelPerfilIntegral::motivoAlTerminar);
    }

    /**
     * Cuando su nota se recalculó mientras esperaba —un ajuste a mano, una recalificación, un
     * cambio de puntos—: si ahora llega a la mínima, pasa con el mismo correo y la misma
     * campana que al terminar la nota, y el historial dice que fue tras recalcular.
     *
     * <p>Mismas guardas y misma transacción propia que {@link #avanzarSiToca}, por lo mismo:
     * corre después de confirmarse el recálculo.
     *
     * @return si quedó en la prueba
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean avanzarTrasRecalcular(Long postulacionId) {
        return avanzar(postulacionId, UmbralDelPerfilIntegral::motivoTrasRecalcular);
    }

    /**
     * Las guardas y el pase, comunes a los dos momentos.
     *
     * @param motivo lo que leerá el historial, a partir de la nota y la mínima
     */
    private boolean avanzar(Long postulacionId, BiFunction<BigDecimal, Integer, String> motivo) {
        Postulacion p = postulaciones.findById(postulacionId).orElse(null);
        if (p == null) {
            return false;
        }

        // Se compara el estado exacto y no «sigue en la etapa»: el siguiente estado se
        // calcula a partir de este, así que el punto de partida tiene que ser el que es. A
        // quien todavía está en su turno o calificando, a quien una persona ya movió mientras
        // la IA trabajaba, o a quien ya está en la prueba, no se le toca.
        if (!DE.equals(p.getEstadoCodigo())) {
            return false;
        }
        // Con la fila bloqueada, como «Avanzar» del panel: si una persona la está moviendo a la
        // vez —o dos recálculos seguidos llegan a la vez—, se espera a que termine el otro y se
        // mira lo que dejó. Sin esto los dos leerían el mismo estado de partida y los dos la
        // moverían.
        if (!DE.equals(postulaciones.estadoBloqueandoLaFila(postulacionId))) {
            return false;
        }

        Vacante vacante = laQueLoDejaAvanzar(p);
        if (vacante == null) {
            return false;
        }

        // La nota, leída después de bloquear la fila: es la última que se guardó.
        Optional<BigDecimal> nota = umbral.notaCompleta(postulacionId);
        int minima = UmbralDelPerfilIntegral.minimaDe(vacante);
        if (nota.isEmpty()) {
            log.info("PASE_AUTOMATICO: la postulación {} se queda en «por confirmar»: no tiene "
                    + "nota completa del Perfil Integral", postulacionId);
            return false;
        }
        if (!UmbralDelPerfilIntegral.llega(nota.get(), minima)) {
            log.info("PASE_AUTOMATICO: la postulación {} se queda en «por confirmar»: su nota {} "
                    + "no llega a la mínima {}", postulacionId, nota.get(), minima);
            return false;
        }
        // Con motivo escrito aunque sea del sistema: sin él, el historial del candidato
        // enseña un salto sin autor y sin explicación, y quien lo abra dentro de seis meses
        // no puede saber si lo movió alguien o la vacante, ni con qué nota.
        return pasarALaPrueba(p, vacante, motivo.apply(nota.get(), minima));
    }

    /**
     * La vacante, si deja avanzar a esta postulación; {@code null} si alguna guarda lo impide.
     *
     * <p>Las que no son la nota. Escritas una vez, valen para los dos momentos del pase.
     */
    private Vacante laQueLoDejaAvanzar(Postulacion p) {
        Long postulacionId = p.getId();
        Vacante vacante = vacantes.findById(p.getVacanteId()).orElse(null);
        if (vacante == null || !vacante.isCalificacionAutomatica()) {
            return null;
        }

        /*
         * ⚠️ **Una vacante archivada no mueve a nadie, tampoco sola.**
         *
         * Aquí no se lanza ningún error: esto no es una petición de nadie, es el final de un
         * trabajo de IA que ya se pagó, y reventar lo daría por fallido y volvería a pagarlo
         * al reintentar. Se deja la postulación donde está y se anota, que es lo mismo que
         * hacen las otras dos guardas de más abajo.
         *
         * Que esto salte significa que algo se salió de lo previsto —archivar exige que no
         * quede nadie en carrera—, y por eso se registra en vez de pasar en silencio.
         */
        if (vacante.getArchivadaEn() != null) {
            log.warn("PASE_AUTOMATICO: la postulación {} no se mueve: su vacante está "
                    + "archivada desde {}", postulacionId, vacante.getArchivadaEn());
            return null;
        }

        /*
         * ⚠️ **Y una eliminada no mueve a nadie, tampoco sola** (V60).
         *
         * Por la misma razón y con el mismo silencio que la de arriba: esto no es la petición
         * de nadie, es el final de un trabajo de IA que ya se pagó. Aquí además hay un motivo
         * extra para no dejarlo al azar: eliminar cierra a quien estaba en carrera, pero un
         * trabajo que empezó ANTES de la eliminación puede terminar después y llegar hasta
         * aquí con la postulación ya cerrada; entonces la máquina de estados se plantaría con
         * una excepción, que se leería como una avería del pase automático y no como lo que
         * es. Se anota y se deja donde está.
         */
        if (vacante.getEliminadaEn() != null) {
            log.warn("PASE_AUTOMATICO: la postulación {} no se mueve: su vacante se eliminó "
                    + "el {}", postulacionId, vacante.getEliminadaEn());
            return null;
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
            return null;
        }

        // Sin instrumento no se avanza, y no es un error: la vacante está a medio montar y
        // quien la lleva tiene que elegir la prueba. Reventar aquí daría el trabajo de la IA
        // por fallido y volvería a pagar el modelo al reintentarlo.
        if (!entradaTecnica.hayInstrumento(vacante)) {
            log.info("PASE_AUTOMATICO: la postulación {} se queda esperando: su vacante no "
                    + "tiene todavía con qué llenar la etapa técnica", postulacionId);
            return null;
        }
        return vacante;
    }

    /** Crea lo que va a rendir y lo pasa a la prueba, juntos. Desde «por confirmar». */
    private boolean pasarALaPrueba(Postulacion p, Vacante vacante, String motivo) {
        Optional<EstadoPostulacion> siguiente = maquina.siguiente(DE);
        if (siguiente.isEmpty()) {
            return false;
        }
        entradaTecnica.crearAlEntrar(p, vacante);
        maquina.transicionar(p, siguiente.get().getCodigo(), null, motivo, true, false, null);
        log.info("PASE_AUTOMATICO: la postulación {} pasa sola a {} ({})",
                p.getId(), siguiente.get().getCodigo(), motivo);
        return true;
    }
}
