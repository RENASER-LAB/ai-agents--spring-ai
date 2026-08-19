package com.renaser.ai.ai_engine.ai.messaging;

import com.renaser.ai.ai_engine.ai.service.ColaCalificacionIa;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

import static com.renaser.ai.ai_engine.ai.config.RabbitMQConfig.SELECCION_CALIFICACION_QUEUE;

/**
 * Quien de verdad ejecuta la calificación del Perfil Integral.
 *
 * <p><b>No relanza la excepción.</b> El estado del trabajo ya se guardó en {@code trabajo_ia}
 * antes de volver aquí, con su cuenta de intentos, y el reintento lo decide la cola —no
 * RabbitMQ reencolando a ciegas—. Dejar subir el error solo conseguiría mandar el mensaje a
 * la cola de descartes por un fallo que ya está registrado y que ya se va a reintentar.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class TrabajoIaListener {

    private final ColaCalificacionIa cola;

    @RabbitListener(queues = SELECCION_CALIFICACION_QUEUE)
    public void onTrabajo(TrabajoIaMessage mensaje) {
        log.info("Recibido el trabajo de calificación {}", mensaje.trabajoIaId());
        cola.ejecutar(mensaje.trabajoIaId());
    }
}
