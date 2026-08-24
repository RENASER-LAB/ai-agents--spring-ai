package com.renaser.ai.ai_engine.integracion;

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

import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Lo que NO se puede tocar sin identificarse.
 *
 * <p>Esta prueba existe por lo que encontró la auditoría del Sprint 1: contra el despliegue,
 * y sin ningún token, {@code /api/v1/agent-runs/history/1} y {@code pending-approvals}
 * devolvían 200, y {@code /v3/api-docs} servía 122 KB con el mapa entero del sistema. En la
 * misma cadena abierta vivía {@code PATCH /api/v1/agent-runs/&#123;id&#125;/approve}, que resuelve un
 * Human Gate: cualquiera en internet podía aprobar uno.
 *
 * <p>Las tres puertas se cerraron el 21/08/2026 en {@code ConfiguracionSeguridad}. Esta clase
 * es lo que impide que se vuelvan a abrir sin que nadie se entere: una cadena de seguridad se
 * relaja en una línea, y sin una prueba en contra el fallo no se ve hasta que alguien lo busca.
 *
 * <p>Corre con las dos llaves apagadas, que es como está el código por defecto y como llega a
 * cualquier entorno remoto. {@code application-local.yaml} las enciende para el desarrollo y
 * para el resto de la suite, así que aquí hay que apagarlas a mano.
 */
@SpringBootTest(properties = {
        "app.seguridad.dev-login-activo=false",
        "app.seguridad.documentacion-publica=false"
})
@AutoConfigureMockMvc
@Testcontainers
@DisplayName("Seguridad · las puertas que tienen que estar cerradas")
class PuertasCerradasIT {

    @Container
    @ServiceConnection
    static PostgreSQLContainer postgres = new PostgreSQLContainer("pgvector/pgvector:pg16");

    @Container
    @ServiceConnection
    static RabbitMQContainer rabbit = new RabbitMQContainer("rabbitmq:3-management-alpine");

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

    @Test
    @DisplayName("El módulo de agentes no se lee sin token")
    void elModuloDeAgentesNoSeLeeSinToken() throws Exception {
        mvc.perform(get("/api/v1/agent-runs/history/1")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/v1/agent-runs/pending-approvals")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/v1/agent-runs/" + UUID.randomUUID())).andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("Y nadie aprueba un Human Gate sin token: es lo que más costaba")
    void nadieApruebaUnHumanGateSinToken() throws Exception {
        mvc.perform(patch("/api/v1/agent-runs/" + UUID.randomUUID() + "/approve"))
                .andExpect(status().isUnauthorized());
        mvc.perform(post("/api/v1/agent-runs/execute")
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("Las otras tres rutas del módulo van por la misma puerta")
    void lasOtrasRutasDelModuloVanPorLaMismaPuerta() throws Exception {
        mvc.perform(get("/api/v1/flows/" + UUID.randomUUID())).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/v1/rag/search").param("q", "lo que sea"))
                .andExpect(status().isUnauthorized());
        mvc.perform(get("/api/v1/supabase/motores")).andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("El esquema de la API no se publica: es el mapa para encontrar lo demás")
    void elEsquemaDeLaApiNoSePublica() throws Exception {
        mvc.perform(get("/v3/api-docs")).andExpect(status().isUnauthorized());
        mvc.perform(get("/swagger-ui/index.html")).andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("El dev-login apagado no emite tokens de equipo")
    void elDevLoginApagadoNoEmiteTokens() throws Exception {
        mvc.perform(post("/api/v1/panel/auth/dev-login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"usuarioRenaserOsId\":\"andy-dev\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("CP-11 · Y el panel sigue cerrado, que es lo que ya funcionaba")
    void elPanelSigueCerrado() throws Exception {
        mvc.perform(get("/api/v1/panel/bandeja").param("espera_a", "TALENTO"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("Lo público del portal sigue siendo público: cerrar no puede tapar la puerta de entrada")
    void loPublicoDelPortalSigueSiendoPublico() throws Exception {
        mvc.perform(get("/api/v1/portal/vacantes")).andExpect(status().isOk());
        mvc.perform(get("/api/v1/portal/consentimientos/textos")).andExpect(status().isOk());
    }
}
