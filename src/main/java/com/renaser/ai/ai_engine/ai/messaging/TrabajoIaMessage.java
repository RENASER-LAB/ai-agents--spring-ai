package com.renaser.ai.ai_engine.ai.messaging;

/**
 * Lo que viaja por RabbitMQ para pedir que se ejecute un trabajo de calificación.
 *
 * <p>Solo el id, a propósito. La fila de {@code trabajo_ia} ya existe en la base cuando se
 * publica el mensaje, así que todo lo demás —qué agente, qué postulación, cuántos intentos
 * lleva— se lee de ahí y no puede quedar desincronizado con el mensaje. Si el mensaje se
 * pierde, el sondeo de reintentos vuelve a publicarlo desde la misma fila.
 */
public record TrabajoIaMessage(Long trabajoIaId) {
}
