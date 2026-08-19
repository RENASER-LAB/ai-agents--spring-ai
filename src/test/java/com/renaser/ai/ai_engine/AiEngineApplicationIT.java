package com.renaser.ai.ai_engine;

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
class AiEngineApplicationIT {

	@Container
	@ServiceConnection
	static PostgreSQLContainer postgres = new PostgreSQLContainer("pgvector/pgvector:pg16");

	@Container
	@ServiceConnection
	static RabbitMQContainer rabbit = new RabbitMQContainer("rabbitmq:3-management-alpine");

	@DynamicPropertySource
	static void propiedades(DynamicPropertyRegistry registro) {
		registro.add("app.seguridad.jwt-secreto",
				() -> "clave-de-pruebas-suficientemente-larga-para-hmac-256-bits");
		// El chat de agentes exige una clave para construir su bean. Aquí nadie llama al
		// modelo, pero sin este valor el contexto entero no arranca.
		registro.add("spring.ai.deepseek.api-key", () -> "clave-de-pruebas-no-se-usa");
	}

	@Test
	void contextLoads() {
	}

}
