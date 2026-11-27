package com.renaser.ai.ai_engine.integracion;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.renaser.ai.ai_engine.integracion.soporte.CelularesDePrueba;
import com.renaser.ai.ai_engine.integracion.soporte.ImagenesDeContenedores;
import com.renaser.ai.ai_engine.perfilintegral.dto.DtosCalificacionIa.NotaCriterioIa;
import com.renaser.ai.ai_engine.perfilintegral.dto.DtosCalificacionIa.ResultadoCv;
import com.renaser.ai.ai_engine.perfilintegral.dto.DtosCalificacionIa.ResultadoPerfil;
import com.renaser.ai.ai_engine.perfilintegral.service.PuenteCalificacionIa;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.rabbitmq.RabbitMQContainer;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Cada vacante con sus etapas, de punta a punta (specs/Ready/etapas-por-vacante, V95).
 *
 * <p>Una vacante nace con Perfil Integral → Prueba del puesto → Decisión; se reordena, se deja en
 * Perfil → Decisión, se publica sin prueba y, con «Calificar y avanzar sola», quien postula pasa del
 * Perfil a la Decisión sin que se le cree ninguna prueba. Con el primer postulante, las etapas
 * quedan fijas.
 *
 * <p>La IA está apagada: lo que haría al calificar el currículum entra por su puente.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@Testcontainers
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@DisplayName("Cada vacante con sus etapas")
class FlujoEtapasPorVacanteIT {

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
        registro.add("app.seguridad.dev-login-activo", () -> "true");
        registro.add("spring.ai.deepseek.api-key", () -> "clave-de-pruebas-no-se-usa");
        registro.add("renaser.ai.calificacion.habilitada", () -> "false");
    }

    private static final List<String> CRITERIOS_CV = List.of("CV_RESULTADOS", "CV_COMPLEJIDAD",
            "CV_SISTEMAS", "CV_PERSONAS", "CV_APRENDIZAJE", "CV_INICIATIVA", "CV_HABILIDADES",
            "CV_EVIDENCIA");

    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired PuenteCalificacionIa puente;
    final ObjectMapper json = new ObjectMapper();

    static String tokenTalento;
    static long areaId;
    static long puestoId;
    static long vacante;
    static long postulacion;
    static String tokenCandidato;

    // ============ Nace con el predeterminado ============

    @Test
    @Order(1)
    @DisplayName("Una vacante nueva nace con Perfil Integral → Prueba del puesto → Decisión, editable (AC-01)")
    void naceConElPredeterminado() throws Exception {
        tokenTalento = leer(mvc.perform(post("/api/v1/panel/auth/dev-login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"usuarioRenaserOsId\":\"dev-talento\"}"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString(), "token");
        jdbc.update("INSERT INTO area (organizacion_id, nombre, es_activa) VALUES (1, 'Recepción', true)");
        areaId = jdbc.queryForObject("SELECT id FROM area ORDER BY id DESC LIMIT 1", Long.class);
        puestoId = Long.parseLong(leer(conToken(post("/api/v1/panel/puestos"), tokenTalento, """
                {"codigo": "RECEPCION_V95", "nombre": "Recepcionista",
                 "nivelPuestoCodigo": "EJECUCION", "familiaCodigo": "OPERACIONES"}""")
                .andReturn().getResponse().getContentAsString(), "id"));

        vacante = crearVacante("Recepcionista de mañana");
        JsonNode panel = detalle();

        assertThat(codigos(panel.get("etapas")))
                .containsExactly("PERFIL_INTEGRAL", "PRUEBA_PUESTO", "DECISION");
        assertThat(panel.at("/etapas/0/fija").asBoolean()).isTrue();
        assertThat(panel.at("/etapas/1/descripcion").asText())
                .isEqualTo("La persona resuelve una tarea real del puesto, con tiempo límite. Se califica "
                        + "sola y tú la revisas antes de que avance.");
        assertThat(panel.get("etapasEditables").asBoolean()).isTrue();

        // «Cómo pesa en esta vacante»: lo de su recorrido suma 100 y lo que no tiene va vacío.
        BigDecimal suma = BigDecimal.ZERO;
        for (JsonNode fila : panel.get("pesosEnEstaVacante")) {
            String codigo = fila.get("codigo").asText();
            if (List.of("PERFIL_INTEGRAL", "PRUEBA_PUESTO").contains(codigo)) {
                suma = suma.add(fila.get("enEstaVacante").decimalValue());
            } else {
                assertThat(fila.get("enEstaVacante").isNull()).as(codigo).isTrue();
            }
        }
        assertThat(suma).isEqualByComparingTo("100.00");

        // El catálogo trae la explicación y las marcas de cada etapa, también de las apagadas.
        JsonNode catalogo = respuesta(conTokenGet("/api/v1/panel/catalogos", tokenTalento)).get("etapas");
        assertThat(catalogo).hasSize(5);
        assertThat(catalogo.get(2).get("descripcion").asText()).startsWith("La persona elige un día");
        assertThat(catalogo.get(0).get("fijaPrimera").asBoolean()).isTrue();
        assertThat(catalogo.get(4).get("enPredeterminado").asBoolean()).isTrue();
    }

    // ============ Se cambia mientras no hay nadie ============

    @Test
    @Order(2)
    @DisplayName("Sin el Perfil primero, vacío, repetido o inventado es un 400; reordenado se guarda en ese orden (AC-03, AC-04)")
    void seValidaYSeReordena() throws Exception {
        for (String malo : List.of("[\"PRUEBA_PUESTO\",\"PERFIL_INTEGRAL\"]", "[]",
                "[\"PERFIL_INTEGRAL\",\"DECISION\",\"DECISION\"]", "[\"PERFIL_INTEGRAL\",\"ENTREVISTA\"]")) {
            etapas(malo).andExpect(status().isBadRequest());
        }
        assertThat(codigos(detalle().get("etapas")))
                .containsExactly("PERFIL_INTEGRAL", "PRUEBA_PUESTO", "DECISION");

        etapas("[\"PERFIL_INTEGRAL\",\"SIMULACION\",\"PRUEBA_PUESTO\",\"DECISION\"]")
                .andExpect(status().isOk());
        assertThat(codigos(detalle().get("etapas")))
                .containsExactly("PERFIL_INTEGRAL", "SIMULACION", "PRUEBA_PUESTO", "DECISION");
        assertThat(jdbc.queryForList("select etapa_codigo || ':' || orden from vacante_etapa "
                + "where vacante_id = ? order by orden", String.class, vacante))
                .containsExactly("PERFIL_INTEGRAL:1", "SIMULACION:2", "PRUEBA_PUESTO:3", "DECISION:4");
    }

    @Test
    @Order(3)
    @DisplayName("Sin el permiso de configurarla, cambiar las etapas responde 403")
    void sinPermiso403() throws Exception {
        String soloMira = cuentaConRol("os-solo-mira-v95", "SOLO_MIRA_V95", "ver_vacantes");

        conToken(put("/api/v1/panel/vacantes/" + vacante + "/etapas"), soloMira,
                "{\"etapas\":[\"PERFIL_INTEGRAL\",\"DECISION\"]}").andExpect(status().isForbidden());
    }

    @Test
    @Order(4)
    @DisplayName("Perfil → Decisión se publica sin prueba (AC-02)")
    void sinPruebaSePublica() throws Exception {
        etapas("[\"PERFIL_INTEGRAL\",\"DECISION\"]").andExpect(status().isOk());
        // Sin banco: la nota del Perfil sale del currículum.
        conToken(post("/api/v1/panel/vacantes/" + vacante + "/aplicacion-evaluacion"), tokenTalento,
                "{\"aplica\":false}").andExpect(status().isOk());

        conToken(post("/api/v1/panel/vacantes/" + vacante + "/publicacion"), tokenTalento, null)
                .andExpect(status().isOk());
        conToken(post("/api/v1/panel/vacantes/" + vacante + "/calificacion-automatica"), tokenTalento,
                "{\"activa\":true}").andExpect(status().isOk());

        assertThat(detalle().get("estado").asText()).isEqualTo("PUBLICADA");
    }

    // ============ El primer postulante ============

    @Test
    @Order(5)
    @DisplayName("Con el primer postulante las etapas quedan fijas: el PUT responde 409 (AC-05)")
    void conElPrimeroQuedanFijas() throws Exception {
        postular("pia");

        etapas("[\"PERFIL_INTEGRAL\",\"PRUEBA_PUESTO\",\"DECISION\"]").andExpect(status().isConflict());
        assertThat(detalle().get("etapasEditables").asBoolean()).isFalse();
        assertThat(codigos(detalle().get("etapas"))).containsExactly("PERFIL_INTEGRAL", "DECISION");
    }

    @Test
    @Order(6)
    @DisplayName("Con «Calificar y avanzar sola», del Perfil pasa a «Decisión · por confirmar» y no se crea ninguna prueba (AC-07, AC-14)")
    void delPerfilALaDecision() throws Exception {
        assertThat(estado()).isEqualTo("PERFIL_POR_CONFIRMAR");

        laIaCalificaElCv("80");

        assertThat(estado()).isEqualTo("DECISION_POR_CONFIRMAR");
        assertThat(jdbc.queryForObject("select count(*) from intento_prueba where postulacion_id = ?",
                Integer.class, postulacion)).isZero();
        assertThat(jdbc.queryForList("select plantilla_correo_codigo from correo_enviado where usuario_id = "
                + "(select usuario_id from postulacion where id = ?)", String.class, postulacion))
                .doesNotContain("PRUEBA_DISPONIBLE");
        assertThat(jdbc.queryForList("select tipo from aviso_portal where usuario_id = "
                + "(select usuario_id from postulacion where id = ?)", String.class, postulacion))
                .doesNotContain("PRUEBA_DISPONIBLE");

        // A una etapa que la vacante no tiene no se le mueve a mano: 409.
        conToken(post("/api/v1/panel/postulaciones/" + postulacion + "/transiciones"), tokenTalento, """
                {"estadoDestino":"PRUEBA_TURNO_CANDIDATO","motivo":"Probando"}""")
                .andExpect(status().isConflict());
        // Y desde la última etapa no hay «Avanzar».
        conToken(post("/api/v1/panel/postulaciones/" + postulacion + "/confirmacion-avance"), tokenTalento,
                "{\"motivo\":\"Avanza\"}").andExpect(status().isConflict());
    }

    @Test
    @Order(7)
    @DisplayName("El candidato ve dos etapas en su proceso, en el orden de la vacante (AC-13)")
    void elPortalVeSuRecorrido() throws Exception {
        JsonNode mias = respuesta(conTokenGet("/api/v1/portal/postulaciones", tokenCandidato));

        assertThat(codigos(mias.get(0).get("etapas"))).containsExactly("PERFIL_INTEGRAL", "DECISION");
    }

    @Test
    @Order(8)
    @DisplayName("La Decisión propone con el Perfil pesando 100: no le falta nada (AC-11)")
    void laDecisionNoEsperaEtapasQueNoTiene() throws Exception {
        JsonNode semaforo = respuesta(conTokenGet("/api/v1/panel/postulaciones/" + postulacion + "/semaforo",
                tokenTalento));

        assertThat(semaforo.get("etapasQueFaltan")).isEmpty();
        assertThat(semaforo.get("semaforo").isNull()).isFalse();
    }

    // ============ Los muros de la base ============

    @Test
    @Order(9)
    @DisplayName("La base rechaza una etapa sin explicación y el Perfil fuera del primer lugar (AC-18)")
    void losMurosDeLaBase() throws Exception {
        assertThatThrownBy(() -> jdbc.update(
                "insert into etapa (codigo, nombre, orden) values ('ENTREVISTA', 'Entrevista', 6)"))
                .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
        assertThatThrownBy(() -> jdbc.update(
                "insert into etapa (codigo, nombre, orden, descripcion) values ('ENTREVISTA', 'Entrevista', 6, '  ')"))
                .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);

        long otra = crearVacante("Recepcionista de tarde");
        jdbc.update("delete from vacante_etapa where vacante_id = ?", otra);
        assertThatThrownBy(() -> jdbc.update(
                "insert into vacante_etapa (vacante_id, etapa_codigo, orden) values (?, 'PERFIL_INTEGRAL', 2)",
                otra)).isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
    }

    // ============ ayudas ============

    private ResultActions etapas(String lista) throws Exception {
        return conToken(put("/api/v1/panel/vacantes/" + vacante + "/etapas"), tokenTalento,
                "{\"etapas\":" + lista + "}");
    }

    private JsonNode detalle() throws Exception {
        return respuesta(conTokenGet("/api/v1/panel/vacantes/" + vacante, tokenTalento));
    }

    private static List<String> codigos(JsonNode etapas) {
        List<String> codigos = new ArrayList<>();
        etapas.forEach(e -> codigos.add(e.get("codigo").asText()));
        return codigos;
    }

    private String estado() {
        return jdbc.queryForObject("select estado_codigo from postulacion where id = ?", String.class,
                postulacion);
    }

    /** Lo que deja la IA al calificar el currículum de quien postula sin banco. */
    private void laIaCalificaElCv(String nota) {
        puente.guardarEvidenciaCv(postulacion, null, new ResultadoCv(CRITERIOS_CV.stream()
                .map(c -> new NotaCriterioIa(c, new BigDecimal(nota), "Hay un resultado medido",
                        "atendió tres sedes"))
                .toList(), List.of(), new BigDecimal("70")));
        puente.cerrarPerfilIntegral(postulacion, null, new ResultadoPerfil(new BigDecimal("70"),
                new BigDecimal("65"), new BigDecimal("50"), new BigDecimal("80"),
                "Atiende con calma y resuelve", List.of(), List.of()));
    }

    private void postular(String quien) throws Exception {
        String correo = quien + ".etapas@correo.pe";
        mvc.perform(post("/api/v1/portal/cuentas").contentType(MediaType.APPLICATION_JSON).content("""
                {"nombre":"%s","apellidos":"Prueba","correo":"%s","telefono":"%s",
                 "contrasena":"unaClaveLarga123","ciudadUbigeo":"1501","aceptaPlataforma":true,
                 "aceptaFuturosContactos":false}""".formatted(quien, correo, CelularesDePrueba.otro())))
                .andExpect(status().isCreated());
        tokenCandidato = leer(mvc.perform(post("/api/v1/portal/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"correo\":\"%s\",\"contrasena\":\"unaClaveLarga123\"}".formatted(correo)))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString(), "token");
        String suCodigo = leer(mvc.perform(multipart("/api/v1/portal/postulaciones")
                        .file(new MockMultipartFile("cv", "cv.pdf", "application/pdf", "contenido".getBytes()))
                        .param("vacanteId", String.valueOf(vacante))
                        .param("resultadoOrgulloso", "Atendí la recepción de tres sedes")
                        .param("aceptaTratamiento", "true")
                        .header("Authorization", "Bearer " + tokenCandidato))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString(), "codigo");
        postulacion = jdbc.queryForObject("select id from postulacion where uuid = ?::uuid", Long.class, suCodigo);
    }

    private long crearVacante(String titulo) throws Exception {
        long solicitudId = Long.parseLong(leer(conToken(post("/api/v1/panel/solicitudes"), tokenTalento, """
                {"areaId": %d, "puestoId": %d, "urgencia": "NORMAL",
                 "nivelPuestoCodigo": "EJECUCION", "familiaCodigo": "OPERACIONES",
                 "resultadoPrincipal": "Recepción atendida a tiempo",
                 "motivo": "Hay colas en la entrada",
                 "consecuenciaNoContratar": "Se pierden clientes",
                 "analisisCapacidad": "El equipo no alcanza",
                 "responsableUsuarioId": 1,
                 "resultadosEsperados": [
                   {"descripcion": "Atención", "indicador": "menos de 5 minutos"},
                   {"descripcion": "Registro", "indicador": "diario"},
                   {"descripcion": "Reportes", "indicador": "semanales"}
                 ]}""".formatted(areaId, puestoId))
                .andReturn().getResponse().getContentAsString(), "id"));
        conToken(post("/api/v1/panel/solicitudes/" + solicitudId + "/aprobacion"), tokenTalento,
                "{\"motivo\":\"Aprobada\"}").andExpect(status().isOk());
        return Long.parseLong(leer(conToken(post("/api/v1/panel/vacantes"), tokenTalento, """
                {"solicitudTalentoId": %d, "puestoId": %d,
                 "titulo": "%s", "descripcion": "Atiende la recepción",
                 "tipoCierre": "PERMANENTE", "responsableUsuarioId": 1}"""
                .formatted(solicitudId, puestoId, titulo))
                .andReturn().getResponse().getContentAsString(), "id"));
    }

    /** Un rol propio con esos permisos (alcance TODO) y una cuenta de panel que lo tiene. */
    private String cuentaConRol(String renaserOsId, String rolCodigo, String... permisos) throws Exception {
        Long rolId = jdbc.queryForObject("""
                insert into rol (organizacion_id, codigo, nombre, descripcion)
                values (1, ?, ?, 'Sembrado por la prueba de las etapas') returning id""",
                Long.class, rolCodigo, rolCodigo);
        for (String permiso : permisos) {
            jdbc.update("""
                    insert into rol_permiso (rol_id, permiso_id, alcance)
                    select ?, id, 'TODO' from permiso where codigo = ?""", rolId, permiso);
        }
        Long personaId = jdbc.queryForObject(
                "insert into persona (nombre, apellidos) values ('QA', ?) returning id", Long.class, renaserOsId);
        Long usuarioId = jdbc.queryForObject("""
                insert into usuario (organizacion_id, persona_id, usuario_renaser_os_id, es_equipo, es_activo)
                values (1, ?, ?, true, true) returning id""", Long.class, personaId, renaserOsId);
        jdbc.update("insert into usuario_rol (usuario_id, rol_id) values (?, ?)", usuarioId, rolId);
        return leer(mvc.perform(post("/api/v1/panel/auth/dev-login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"usuarioRenaserOsId\":\"%s\"}".formatted(renaserOsId)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(), "token");
    }

    private JsonNode respuesta(ResultActions peticion) throws Exception {
        return json.readTree(peticion.andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
    }

    private ResultActions conToken(MockHttpServletRequestBuilder peticion, String suToken, String cuerpo)
            throws Exception {
        peticion.header("Authorization", "Bearer " + suToken).contentType(MediaType.APPLICATION_JSON);
        if (cuerpo != null) peticion.content(cuerpo);
        return mvc.perform(peticion);
    }

    private ResultActions conTokenGet(String ruta, String suToken) throws Exception {
        return mvc.perform(get(ruta).header("Authorization", "Bearer " + suToken));
    }

    private String leer(String cuerpoRespuesta, String campo) throws Exception {
        return json.readTree(cuerpoRespuesta).get(campo).asText();
    }
}
