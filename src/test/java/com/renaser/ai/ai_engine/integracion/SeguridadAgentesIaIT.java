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

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
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

    /**
     * El directorio permitido, creado de verdad.
     *
     * <p>Sin esto la prueba se engaña sola: con {@code directorio-base} vacío, la ingesta está
     * apagada y <b>cualquier</b> ruta devuelve 400 por esa rama, incluida una de recorrido. El
     * test pasaría en verde aunque se borrase entero el cerco de rutas. Se descubrió al
     * revisarlo, y por eso se configura un directorio real.
     */
    static Path directorioPermitido;

    static {
        try {
            directorioPermitido = Files.createTempDirectory("rag-permitido");
            directorioPermitido.toFile().deleteOnExit();
            Files.writeString(directorioPermitido.resolve("dentro.pdf"), "no es un pdf de verdad");
        } catch (IOException e) {
            throw new IllegalStateException("No se pudo preparar el directorio de la prueba", e);
        }
    }

    @DynamicPropertySource
    static void propiedades(DynamicPropertyRegistry registro) {
        registro.add("spring.rabbitmq.ssl.enabled", () -> "false");
        registro.add("spring.rabbitmq.virtual-host", () -> "/");
        registro.add("app.archivos.tipo", () -> "memoria");
        registro.add("app.seguridad.jwt-secreto",
                () -> "clave-de-pruebas-suficientemente-larga-para-hmac-256-bits");
        registro.add("spring.ai.deepseek.api-key", () -> "clave-de-pruebas-no-se-usa");
        registro.add("renaser.ai.calificacion.habilitada", () -> "false");
        registro.add("renaser.rag.directorio-base", () -> directorioPermitido.toString());
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

    /**
     * Se afirma también el {@code detail}, y no solo el 400, a propósito: es lo único que
     * distingue «lo paró el cerco de rutas» de «la ingesta estaba apagada». Sin esa aserción
     * la prueba pasaba por la rama equivocada y no probaba nada del recorrido de directorios.
     */
    @Test
    @DisplayName("con token, una ruta de fuera del directorio la para el cerco, no el interruptor")
    void conTokenUnaRutaDeFueraLaParaElCerco() throws Exception {
        String token = tokenDeEquipo();

        mvc.perform(post("/api/v1/rag/ingest")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"path\":\"../../etc/passwd\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value(containsString("no apunta a un documento")));
    }

    @Test
    @DisplayName("una ruta absoluta tampoco entra, aunque el fichero exista")
    void unaRutaAbsolutaTampocoEntra() throws Exception {
        String token = tokenDeEquipo();

        mvc.perform(post("/api/v1/rag/ingest")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"path\":\"/etc/hostname\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value(containsString("no apunta a un documento")));
    }

    /**
     * Un fichero que está donde debe pero no es un PDF legible es entrada mala del cliente, no
     * avería nuestra. Antes esto salía 500 y contradecía lo que promete el propio javadoc del
     * manejador.
     */
    @Test
    @DisplayName("un fichero de dentro que no es un PDF es 400, no una avería")
    void unFicheroDeDentroQueNoEsPdfEsCuatrocientos() throws Exception {
        String token = tokenDeEquipo();

        mvc.perform(post("/api/v1/rag/ingest")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"path\":\"dentro.pdf\"}"))
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
