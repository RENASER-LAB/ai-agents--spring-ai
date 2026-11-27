package com.renaser.ai.ai_engine.informe.service;

import com.renaser.ai.ai_engine.informe.entity.PedidoConHuella;

import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.function.BooleanSupplier;

/**
 * Pedirle a la IA lo de unos datos una sola vez: los textos del redactor (V84) y el seguimiento de
 * pendientes (V94) se piden igual.
 *
 * <p>Si esa huella ya se pidió, no se vuelve a pagar: se contesta si viene en camino. Si la IA
 * está apagada o no se pudo encolar, no se anota nada, para pedirlo la próxima vez.
 */
@Slf4j
final class PedidoSinRepetir {

    private PedidoSinRepetir() {
    }

    /**
     * @param fila     la fila de esa postulación y etapa, nueva o ya guardada
     * @param encolar  pone el trabajo en la cola; true si quedó
     * @param enCamino si hay un trabajo vivo para esa etapa
     * @return si viene en camino
     */
    static <T extends PedidoConHuella> boolean pedir(T fila, String huella, BooleanSupplier encolar,
                                                    BooleanSupplier enCamino, JpaRepository<T, Long> filas,
                                                    String que) {
        if (huella.equals(fila.getHuellaPedida())) {
            return enCamino.getAsBoolean();
        }
        boolean encolado = encolar.getAsBoolean();
        if (!encolado && !enCamino.getAsBoolean()) {
            return false;
        }
        fila.setHuellaPedida(huella);
        fila.setPedidaEn(Instant.now());
        try {
            filas.saveAndFlush(fila);
        } catch (DataIntegrityViolationException carrera) {
            // Otra petición la creó a la vez: lo pedido ya está pedido.
            log.info("{}: ya se estaba pidiendo", que);
        }
        return true;
    }
}
