package com.renaser.ai.ai_engine.integracion;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.renaser.ai.ai_engine.integracion.soporte.ImagenesDeContenedores;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.rabbitmq.RabbitMQContainer;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * El módulo de agentes IA ya no está abierto a cualquiera.
 *
 * <p>Corrió mucho tiempo sin seguridad propia. Lo que obligó a cerrarlo fue
 * {@code POST /api/v1/rag/ingest}: recibía una ruta del sistema de ficheros del servidor, la
 * leía, y el texto quedaba consultable por {@code GET /api/v1/rag/search}. Sin token. Un PDF
 * cualquiera de la máquina se podía sacar desde internet.
 *
 * <p>Esta prueba tiene que ser de integración y no unitaria: lo que afirma es que la
 * <b>cadena de filtros</b> está montada como se cree. Un test que llame al controlador a mano
 * pasa en verde aunque la seguridad no esté puesta — que es exactamente cómo el agujero pasó
 * desapercibido.
 *
 * <p>Lo que asegura:
 * <ul>
 *   <li>Sin token, las cuatro rutas del módulo contestan 401 y no sirven nada.
 *   <li>El contrato OpenAPI sigue abierto: el fuzzing nocturno lo lee antes de tener token, y
 *       cerrarlo rompería el propio job que encontró todo esto.
 *   <li>Con token, una ruta que se sale del directorio permitido es 400 y <b>nunca 500</b>.
 * </ul>
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@Testcontainers
@DisplayName("El módulo de agentes IA pide identidad, y la ingesta no sale de su directorio")
public class SeguridadAgentesIaIT {

    @Container
    @ServiceConnection
    static PostgreSQLContainer postgres = new PostgreSQLContainer("pgvector/pgvector:pg16");

    @Container
    @ServiceConnection
    static RabbitMQContainer rabbit = new RabbitMQContainer(ImagenesDeContenedores.RABBITMQ);

    @DynamicPropertySource
    static void propiedades(DynamicPropertyRegistry registro) {
        registro.add("spring.rabbitmq.ssl.enabled", () -> "false");
        registro.add("spring.rabbitmq.virtual-host", () -> "/");
        registro.add("app.archivos.tipo", () -> "memoria");
        registro.add("app.seguridad.jwt-secreto",
                () -> "clave-de-pruebas-suficientemente-larga-para-hmac-256-bits");
        registro.add("spring.ai.deepseek.api-key", () -> "clave-de-pruebas-no-se-usa");
        registro.add("renaser.ai.calificacion.habilitada", () -> "false");
    }

    @Autowired MockMvc mvc;
    final ObjectMapper json = new ObjectMapper();

    @Test
    @DisplayName("sin token, la ingesta del RAG no se puede ni intentar")
    void sinTokenLaIngestaNoSePuedeNiIntentar() throws Exception {
        mvc.perform(post("/api/v1/rag/ingest")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"path\":\"/etc/passwd\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("sin token tampoco se consulta lo ya indexado")
    void sinTokenTampocoSeConsultaLoIndexado() throws Exception {
        mvc.perform(get("/api/v1/rag/search").param("query", "lo que sea"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("las otras tres rutas del módulo también piden identidad")
    void lasOtrasRutasTambienPidenIdentidad() throws Exception {
        mvc.perform(get("/api/v1/agent-runs/last").param("agentType", "KNOWLEDGE"))
                .andExpect(status().isUnauthorized());
        mvc.perform(get("/api/v1/supabase/motores"))
                .andExpect(status().isUnauthorized());
        mvc.perform(post("/api/v1/flows/execute")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isUnauthorized());
    }

    /**
     * El contrato tiene que seguir abierto. El nocturno hace
     * {@code curl --fail .../v3/api-docs} como puerta de arranque y Schemathesis lee esa misma
     * URL para saber qué fuzzear, las dos cosas antes de pedir ningún token.
     */
    @Test
    @DisplayName("el contrato OpenAPI sigue siendo público: el nocturno lo lee sin token")
    void elContratoSigueSiendoPublico() throws Exception {
        mvc.perform(get("/v3/api-docs")).andExpect(status().isOk());
    }

    @Test
    @DisplayName("con token, una ruta de fuera del directorio es 400 y no una avería")
    void conTokenUnaRutaDeFueraEsCuatrocientos() throws Exception {
        String token = tokenDeEquipo();

        mvc.perform(post("/api/v1/rag/ingest")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"path\":\"../../etc/passwd\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("con token, un cuerpo sin ruta es 400 y no un NullPointerException")
    void conTokenUnCuerpoSinRutaEsCuatrocientos() throws Exception {
        String token = tokenDeEquipo();

        mvc.perform(post("/api/v1/rag/ingest")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest());
    }

    // ============ Apoyo ============

    /** La misma puerta que usa el fuzzing nocturno para entrar. */
    private String tokenDeEquipo() throws Exception {
        String cuerpo = mvc.perform(post("/api/v1/panel/auth/dev-login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"usuarioRenaserOsId\":\"equipo-de-pruebas\"}"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return json.readTree(cuerpo).get("token").asText();
    }
}
