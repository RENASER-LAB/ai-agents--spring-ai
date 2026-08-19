package com.renaser.ai.ai_engine.ai.messaging;

import com.renaser.ai.ai_engine.ai.config.RabbitMQConfig;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * Pone un trabajo en la cola.
 *
 * <p><b>El mensaje sale cuando la fila ya está guardada, nunca antes.</b> Quien encola casi
 * siempre está dentro de una transacción —el panel pide la criba de una tanda entera y todo
 * eso es una sola— así que la fila de {@code trabajo_ia} todavía no existe para nadie más
 * cuando se manda el aviso. Y del otro lado hay ocho consumidores esperando: uno agarra el
 * mensaje en milisegundos, va a la base, no encuentra el trabajo pendiente y lo suelta.
 *
 * <p>El resultado era un trabajo que se quedaba en PENDIENTE para siempre, sin error y sin
 * nadie mirándolo, hasta que el vigilante de atascados lo reencolaba quince minutos después.
 * Con un solo consumidor la carrera se ganaba casi siempre por casualidad; con ocho se
 * perdía casi siempre.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class TrabajoIaPublisher {

    private final RabbitTemplate rabbitTemplate;

    public void publicar(Long trabajoIaId) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    enviar(trabajoIaId);
                }
            });
            return;
        }
        // Sin transacción abierta la fila ya está guardada: se manda al momento.
        enviar(trabajoIaId);
    }

    private void enviar(Long trabajoIaId) {
        log.info("Encolando el trabajo de calificación {}", trabajoIaId);
        rabbitTemplate.convertAndSend(RabbitMQConfig.AGENT_EXCHANGE,
                RabbitMQConfig.SELECCION_CALIFICACION_ROUTING_KEY,
                new TrabajoIaMessage(trabajoIaId));
    }
}
