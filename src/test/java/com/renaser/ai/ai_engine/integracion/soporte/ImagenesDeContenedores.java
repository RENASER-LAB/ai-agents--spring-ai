package com.renaser.ai.ai_engine.integracion.soporte;

/**
 * Las imágenes que levantan los tests de integración, en un solo sitio.
 *
 * <p>La de RabbitMQ debe seguir a la de producción (despliegue/docker-compose.yml), en su
 * variante con panel: RabbitMQContainer de Testcontainers exige la imagen management.
 */
public final class ImagenesDeContenedores {

    public static final String RABBITMQ = "rabbitmq:4.2-management-alpine";

    private ImagenesDeContenedores() {}
}
