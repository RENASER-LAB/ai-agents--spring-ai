package com.renaser.ai.ai_engine.ai.service.impl;

import com.renaser.ai.ai_engine.ai.model.TrabajoIa;
import com.renaser.ai.ai_engine.ai.repository.TrabajoIaRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Optional;

/**
 * Los cambios de estado de la cola, cada uno en su propia transacción.
 *
 * <p><b>Por qué es una clase aparte.</b> La llamada al modelo tarda decenas de segundos, y
 * durante ese rato no puede haber una transacción abierta reteniendo una conexión de la
 * base. Así que quien orquesta —{@code ColaCalificacionIaImpl}— no es transaccional, y marca
 * los estados llamando aquí. Si estos métodos vivieran en la misma clase, Spring no los
 * envolvería (una llamada a un método propio no pasa por el proxy) y no habría transacción
 * ninguna.
 *
 * <p>{@code REQUIRES_NEW} en marcar el fallo: se escribe aunque lo que estuviera corriendo
 * haya dejado la transacción del negocio para deshacer. Un fallo que no queda escrito es un
 * trabajo que nadie vuelve a mirar.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class RegistroTrabajosIa {

    private final TrabajoIaRepository trabajos;

    /**
     * Crea el trabajo de un agente si no lo hay ya vivo o terminado.
     *
     * @return vacío si ya está hecho o ya hay uno en marcha
     */
    @Transactional
    public Optional<TrabajoIa> crearSiHaceFalta(Long organizacionId, Long postulacionId,
                                                String agenteCodigo, String modo) {
        // La búsqueda incluye el modo: sin eso la pasada fina encontraría el trabajo que ya
        // hizo la rápida y no correría nunca, que es justo lo contrario de lo que se pide.
        Optional<TrabajoIa> existente = trabajos
                .findFirstByPostulacionIdAndAgenteCodigoAndModoOrderByIdDesc(
                        postulacionId, agenteCodigo, modo);
        if (existente.isPresent() && !"FALLIDO".equals(existente.get().getEstado())) {
            return Optional.empty();
        }
        // Un FALLIDO sí se puede volver a intentar: es lo que permite reencolar a mano una
        // postulación que se quedó colgada por un problema del proveedor.
        return Optional.of(trabajos.save(TrabajoIa.builder()
                .organizacionId(organizacionId)
                .agenteCodigo(agenteCodigo)
                .modo(modo)
                .postulacionId(postulacionId)
                .referenciaTabla("postulacion")
                .referenciaId(postulacionId)
                .estado("PENDIENTE")
                .intentos(0)
                .creadoEn(Instant.now())
                .build()));
    }

    /**
     * Lo pasa a EN_CURSO y suma un intento.
     *
     * @return vacío si otro ya lo tomó, o si ya estaba terminado. Es lo que hace que un
     *         mensaje entregado dos veces no califique dos veces.
     */
    @Transactional
    public Optional<TrabajoIa> tomar(Long trabajoIaId) {
        // El cambio de estado y la condición van juntos en una sola sentencia: es lo que
        // impide que dos consumidores llamen al modelo por el mismo candidato.
        if (trabajos.tomarSiEstaPendiente(trabajoIaId, Instant.now()) == 0) {
            log.info("El trabajo {} ya no estaba pendiente: lo tomó otro, o ya terminó",
                    trabajoIaId);
            return Optional.empty();
        }
        return trabajos.findById(trabajoIaId);
    }

    @Transactional
    public void terminar(Long trabajoIaId) {
        trabajos.findById(trabajoIaId).ifPresent(trabajo -> {
            trabajo.setEstado("TERMINADO");
            trabajo.setTerminadoEn(Instant.now());
            trabajos.save(trabajo);
        });
    }

    /**
     * Marca el fallo y decide si se vuelve a intentar.
     *
     * @return true si queda otro intento, y entonces el trabajo vuelve a PENDIENTE
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean fallar(Long trabajoIaId, int maxIntentos, String motivo) {
        TrabajoIa trabajo = trabajos.findById(trabajoIaId).orElse(null);
        if (trabajo == null) {
            return false;
        }
        int intentos = trabajo.getIntentos() == null ? 1 : trabajo.getIntentos();
        boolean reintentar = intentos < maxIntentos;
        trabajo.setEstado(reintentar ? "PENDIENTE" : "FALLIDO");
        trabajo.setTomadoEn(null);
        if (!reintentar) {
            trabajo.setTerminadoEn(Instant.now());
            // Nadie se entera solo de que una postulación se quedó sin calificar: por eso
            // este mensaje es de error y nombra la postulación (Regla 3 del doc 03).
            log.error("El trabajo {} ({}) de la postulación {} se agotó en {} intentos. La "
                            + "postulación se queda en PERFIL_CALIFICANDO y NO se le inventa una "
                            + "nota. Último motivo: {}",
                    trabajoIaId, trabajo.getAgenteCodigo(), trabajo.getPostulacionId(),
                    intentos, motivo);
        } else {
            log.warn("El trabajo {} ({}) falló en el intento {}/{}, se reintenta. Motivo: {}",
                    trabajoIaId, trabajo.getAgenteCodigo(), intentos, maxIntentos, motivo);
        }
        trabajos.save(trabajo);
        return reintentar;
    }

    /** Devuelve un EN_CURSO colgado a PENDIENTE para que alguien lo vuelva a tomar. */
    @Transactional
    public void devolverAPendiente(Long trabajoIaId) {
        trabajos.findById(trabajoIaId).ifPresent(trabajo -> {
            if (!"EN_CURSO".equals(trabajo.getEstado())) {
                return;
            }
            trabajo.setEstado("PENDIENTE");
            trabajo.setTomadoEn(null);
            trabajos.save(trabajo);
        });
    }
}
