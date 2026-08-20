package com.renaser.ai.ai_engine;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.rabbitmq.RabbitMQContainer;

// Antes exigía Postgres y RabbitMQ levantados a mano (docker compose); ahora levanta
// los suyos con Testcontainers y pasa en cualquier máquina con Docker.
@SpringBootTest
@Testcontainers
@DisplayName("El arranque de la aplicación")
class AiEngineApplicationIT {

	@Container
	@ServiceConnection
	static PostgreSQLContainer postgres = new PostgreSQLContainer("pgvector/pgvector:pg16");

	@Container
	@ServiceConnection
	static RabbitMQContainer rabbit = new RabbitMQContainer("rabbitmq:3-management-alpine");

	@DynamicPropertySource
	static void propiedades(DynamicPropertyRegistry registro) {
        // El almacen de las pruebas vive en un mapa, no en disco.
        registro.add("app.archivos.tipo", () -> "memoria");
        // El broker de las pruebas es el contenedor, y habla en claro. Sin esto manda lo
        // que cada uno tenga en su application-secrets.yaml —hoy, un CloudAMQP con TLS— y
        // la tanda entera falla según la máquina en la que corra, que es lo peor que le
        // puede pasar a una prueba.
        registro.add("spring.rabbitmq.ssl.enabled", () -> "false");
        registro.add("spring.rabbitmq.virtual-host", () -> "/");
		registro.add("app.seguridad.jwt-secreto",
				() -> "clave-de-pruebas-suficientemente-larga-para-hmac-256-bits");
		// El chat de agentes exige una clave para construir su bean. Aquí nadie llama al
		// modelo, pero sin este valor el contexto entero no arranca.
		registro.add("spring.ai.deepseek.api-key", () -> "clave-de-pruebas-no-se-usa");
	}

	@DisplayName("La aplicación arranca con todo su contexto")
	@Test
	void contextLoads() {
	}

}
