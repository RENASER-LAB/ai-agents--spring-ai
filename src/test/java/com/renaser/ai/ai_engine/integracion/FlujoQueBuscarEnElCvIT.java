package com.renaser.ai.ai_engine.integracion;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.renaser.ai.ai_engine.ai.dto.RespuestaModelo;
import com.renaser.ai.ai_engine.ai.service.ClienteModelo;
import com.renaser.ai.ai_engine.integracion.soporte.ImagenesDeContenedores;
import com.renaser.ai.ai_engine.perfilintegral.service.DatosDeLaVacanteParaIa;
import com.renaser.ai.ai_engine.perfilintegral.service.PuenteRecomendador;
import com.renaser.ai.ai_engine.postulacion.entity.Postulacion;
import com.renaser.ai.ai_engine.postulacion.repository.PostulacionRepository;
import com.renaser.ai.ai_engine.vacante.repository.VacanteRepository;
import com.renaser.ai.ai_engine.vacante.service.ContextoDeLaVacante;
import com.renaser.ai.ai_engine.whatsapp.service.FirmaWhatsapp;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.rabbitmq.RabbitMQContainer;

import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.BooleanSupplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.fail;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * «Qué buscar en el CV» de punta a punta (V80), con el modelo sustituido por un doble.
 *
 * <p>Se comprueba lo que sostiene la spec: la casilla se guarda y rechaza lo prohibido; la IA
 * busca junto a EVIDENCIA_CV y el Perfil Integral la espera; «lo tiene» exige una frase del CV;
 * la nota del CV es la misma en el ranking y en lo que recibe POTENCIAL_RIESGO; un CV sin texto
 * no llama a la IA; editar las líneas pide confirmación y vuelve a buscar sin mover a nadie; y
 * nada de esto llega al candidato.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@Testcontainers
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@DisplayName("Qué buscar en el CV")
public class FlujoQueBuscarEnElCvIT {

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
        registro.add("renaser.perfil.lectura-al-postular", () -> "false");
        registro.add("app.archivos.tipo", () -> "memoria");
        registro.add("app.seguridad.jwt-secreto",
                () -> "clave-de-pruebas-suficientemente-larga-para-hmac-256-bits");
        registro.add("app.seguridad.dev-login-activo", () -> "true");
        registro.add("spring.ai.deepseek.api-key", () -> "clave-de-pruebas-no-se-usa");
        registro.add("renaser.ai.calificacion.max-intentos", () -> "2");
        // El bot de WhatsApp falso, para pedir el contexto como lo pide el de verdad (AC-05)
        registro.add("app.whatsapp.modo", () -> "falso");
        registro.add("app.whatsapp.secreto-desde-el-bot", () -> SECRETO_DEL_BOT);
        registro.add("app.whatsapp.sondeo-ms", () -> "3600000");
    }

    /** El texto de una línea que no puede aparecer en nada de lo que ve el candidato. */
    private static final String LINEA_SECRETA = "Controla costos y mermas XQ7";

    /**
     * Lo que no puede salir de la ficha del panel: las tres líneas de la vacante y lo que la
     * búsqueda dejó de Lucía (la frase, las notas y la pregunta para la entrevista).
     */
    private static final List<String> LO_BUSCADO = List.of("XQ7", "Ha dirigido un equipo de cocina",
            "Certificado BPM vigente", "merma de verduras", "Bajo la merma a menos de la mitad",
            "Dice que dirigio un equipo", "Tienes el certificado BPM");

    private static final String SECRETO_DEL_BOT = "secreto-de-pruebas-del-bot-que-busca-en-el-cv";
    private static final String TELEFONO_DE_LUCIA = "+51987654321";

    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired PostulacionRepository postulaciones;
    @Autowired VacanteRepository vacantes;
    @Autowired ContextoDeLaVacante contextoDeLaVacante;
    @Autowired DatosDeLaVacanteParaIa datosDeLaVacanteParaIa;
    @Autowired PuenteRecomendador recomendador;
    final ObjectMapper json = new ObjectMapper();

    static String tokenEquipo;
    static long vacanteId;
    static long solicitudId;
    static long puestoId;
    static long lucia;
    static long carla;
    static String tokenLucia;

    @TestConfiguration
    static class ConfiguracionDePrueba {
        @Bean
        @Primary
        ClienteModelo clienteModeloDePrueba() {
            return new ModeloDePrueba();
        }
    }

    static class ModeloDePrueba implements ClienteModelo {

        static final List<String> agentes = new CopyOnWriteArrayList<>();

        @Override
        public RespuestaModelo preguntar(String agenteCodigo, String instruccion, String contenido) {
            return preguntar(agenteCodigo, instruccion, contenido, true);
        }

        @Override
        public RespuestaModelo preguntar(String agenteCodigo, String instruccion, String contenido,
                                         boolean razona) {
            agentes.add(agenteCodigo);
            return new RespuestaModelo(respuestaDe(agenteCodigo), "deepseek-flash", "deepseek",
                    "prueba", 1200, 340);
        }

        private String respuestaDe(String agenteCodigo) {
            return switch (agenteCodigo) {
                case "DATOS_CV" -> """
                        {"nombre":"Lucia Ramos","email":"lucia@correo.pe","telefono":"999888777",
                         "perfilResumen":"Cocinera","habilidades":["Cocina"],
                         "experienciaMesesTotal":72,"ultimoPuesto":"Cocinera principal",
                         "ultimaEmpresa":"Verde Bowl","ultimaMesesDuracion":30,
                         "educacionMaxima":"Tecnica"}""";
                case "EVIDENCIA_CV" -> evidenciaCv();
                case "BUSQUEDA_CV" -> """
                        {"lineas":[
                          {"numero":1,"estado":"TIENE",
                           "frase":"Baje la merma de verduras del 9 % al 4 % en un ano",
                           "nota":"Bajo la merma a menos de la mitad.","pregunta":null},
                          {"numero":2,"estado":"TIENE","frase":"Dirigi a 40 cocineros en Paris",
                           "nota":"Dice que dirigio un equipo.","pregunta":null},
                          {"numero":3,"estado":"NO_APARECE","frase":null,
                           "nota":"No lo menciona.","pregunta":"¿Tienes el certificado BPM?"},
                          {"numero":9,"estado":"TIENE","frase":"sobra","nota":"sobra","pregunta":null}]}""";
                case "POTENCIAL_RIESGO" -> """
                        {"adecuacion":78,"potencial":70,"altoRendimiento":70,"confianzaEvidencia":66,
                         "resumen":"Cocinera con resultados medidos.","hallazgos":[],"alertas":[]}""";
                default -> throw new IllegalStateException("agente inesperado: " + agenteCodigo);
            };
        }

        private String evidenciaCv() {
            StringBuilder filas = new StringBuilder();
            for (String codigo : List.of("CV_RESULTADOS", "CV_COMPLEJIDAD", "CV_SISTEMAS",
                    "CV_PERSONAS", "CV_APRENDIZAJE", "CV_INICIATIVA", "CV_HABILIDADES", "CV_EVIDENCIA")) {
                if (!filas.isEmpty()) filas.append(',');
                filas.append("""
                        {"codigo":"%s","puntaje":80,"explicacion":"Hay resultados.","evidencia":"merma"}"""
                        .formatted(codigo));
            }
            return "{\"criterios\":[" + filas + "],\"afirmaciones\":[],\"confianza\":70}";
        }
    }

    // ============ La casilla ============

    @Test
    @Order(1)
    @DisplayName("AC-02: una línea prohibida por la API es un 400 y no se guarda nada")
    void laLineaProhibidaEsUn400() throws Exception {
        tokenEquipo = entrarComoEquipo();
        prepararSolicitudYPuesto();
        int vacantesAntes = contar("select count(*) from vacante");

        conToken(post("/api/v1/panel/vacantes"), tokenEquipo, cuerpoDeVacante(
                List.of("Experiencia en cocina", "Menor de 35 años")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value(org.hamcrest.Matchers.containsString(
                        "no sirven para elegir a nadie")));

        assertThat(contar("select count(*) from vacante")).isEqualTo(vacantesAntes);
        assertThat(contar("select count(*) from linea_busqueda_cv")).isZero();
    }

    @Test
    @Order(2)
    @DisplayName("AC-01: se guardan las líneas válidas, sin vacías ni repetidas, y salen en el panel")
    void seGuardanLasLineas() throws Exception {
        vacanteId = Long.parseLong(leer(conToken(post("/api/v1/panel/vacantes"), tokenEquipo,
                cuerpoDeVacante(List.of(LINEA_SECRETA, "", "Ha dirigido un equipo de cocina",
                        "ha dirigido un  equipo de cocina", "Certificado BPM vigente")))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString(), "id"));

        conToken(get("/api/v1/panel/vacantes/" + vacanteId), tokenEquipo, null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.queBuscarEnElCv.length()").value(3))
                .andExpect(jsonPath("$.queBuscarEnElCv[0]").value(LINEA_SECRETA));

        // Ni en los requisitos públicos ni en ninguna columna de la vacante
        assertThat(jdbc.queryForObject("select coalesce(requisitos, '') from vacante where id = ?",
                String.class, vacanteId)).doesNotContain("XQ7");
        publicar(vacanteId);
    }

    // ============ La búsqueda y la nota ============

    @Test
    @Order(3)
    @DisplayName("AC-06/07/08/10: busca con la criba, el retrato la espera y la nota del CV es una sola")
    void laIaBuscaYLaNotaEsUnaSola() throws Exception {
        tokenLucia = crearCandidatoYEntrar("lucia@correo.pe");
        lucia = idDe(postular(tokenLucia, curriculumEnPdf(List.of(
                "Lucia Ramos - Cocinera",
                "2021-2024 Cocinera principal en Verde Bowl",
                "Baje la merma de verduras del 9 % al 4 % en un ano.",
                "Disene la carta de bowls bajos en sodio."))));

        conToken(post("/api/v1/panel/postulaciones/" + lucia + "/criba-cv"), tokenEquipo, null)
                .andExpect(status().isOk());
        esperarA(() -> "PERFIL_POR_CONFIRMAR".equals(estadoDe(lucia)), "termine la criba de Lucía");

        // AC-10: el retrato se armó después de la búsqueda
        long buscada = jdbc.queryForObject("""
                select max(id) from trabajo_ia where postulacion_id = ? and agente_codigo = 'BUSQUEDA_CV'
                and estado = 'TERMINADO'""", Long.class, lucia);
        long retrato = jdbc.queryForObject("""
                select min(id) from trabajo_ia where postulacion_id = ? and agente_codigo = 'POTENCIAL_RIESGO'""",
                Long.class, lucia);
        assertThat(retrato).isGreaterThan(buscada);

        List<Map<String, Object>> filas = jdbc.queryForList("""
                select l.orden, r.estado, r.frase_cv from resultado_busqueda_cv r
                join linea_busqueda_cv l on l.id = r.linea_busqueda_cv_id
                where r.postulacion_id = ? and r.archivado_en is null order by l.orden""", lucia);
        // AC-06: «lo tiene» con la frase; AC-07: la frase inventada baja a «lo menciona»
        assertThat(filas).extracting(f -> f.get("estado")).containsExactly("TIENE", "MENCIONA", "NO_APARECE");
        assertThat(filas.get(0).get("frase_cv")).asString().contains("merma de verduras");
        assertThat(filas.get(1).get("frase_cv")).isNull();

        // AC-08: 80 en los criterios, 50 en lo buscado y p = 25 → 72,5, en todas partes
        JsonNode fila = filaDelRanking(lucia, "PERFIL_INTEGRAL");
        assertThat(fila.get("notaCurriculum").decimalValue()).isEqualByComparingTo("72.5");
        assertThat(fila.get("busqueda").get("estado").asText()).isEqualTo("TERMINADA");
        assertThat(fila.get("busqueda").get("tiene").asInt()).isEqualTo(1);
        assertThat(fila.get("busqueda").get("menciona").asInt()).isEqualTo(1);
        assertThat(fila.get("busqueda").get("notaCriteriosGenerales").decimalValue()).isEqualByComparingTo("80");
        assertThat(fila.get("busqueda").get("lineas").get(2).get("pregunta").asText())
                .isEqualTo("¿Tienes el certificado BPM?");
        String alRetrato = jdbc.queryForObject("""
                select envio from ejecucion_ia where agente_codigo = 'POTENCIAL_RIESGO'
                and trabajo_ia_id = ?""", String.class, retrato);
        assertThat(alRetrato).contains("\"notaCurriculum\":72.5");

        // En las demás pestañas también viajan los puntos
        assertThat(filaDelRanking(lucia, "PRUEBA_PUESTO").get("busqueda").get("estado").asText())
                .isEqualTo("TERMINADA");
    }

    @Test
    @Order(4)
    @DisplayName("AC-11: un CV escaneado queda «CV sin texto» sin llamar a la IA ni reintentar")
    void unCvSinTexto() throws Exception {
        String token = crearCandidatoYEntrar("carla@correo.pe");
        carla = idDe(postular(token, curriculumEnPdf(List.of())));
        ModeloDePrueba.agentes.clear();

        conToken(post("/api/v1/panel/postulaciones/" + carla + "/criba-cv"), tokenEquipo, null)
                .andExpect(status().isOk());
        esperarA(() -> contar("select count(*) from busqueda_cv where postulacion_id = " + carla
                + " and estado = 'SIN_TEXTO' and archivada_en is null") == 1, "se marque el CV sin texto");

        assertThat(ModeloDePrueba.agentes).doesNotContain("BUSQUEDA_CV");
        assertThat(jdbc.queryForObject("""
                select intentos from trabajo_ia where postulacion_id = ? and agente_codigo = 'BUSQUEDA_CV'""",
                Integer.class, carla)).isEqualTo(1);
        assertThat(jdbc.queryForObject("select motivo from busqueda_cv where postulacion_id = ?",
                String.class, carla)).contains("texto");
        assertThat(filaDelRanking(carla, "PERFIL_INTEGRAL").get("busqueda").get("estado").asText())
                .isEqualTo("SIN_TEXTO");
    }

    // ============ Privacidad ============

    @Test
    @Order(5)
    @DisplayName("AC-05: ni las líneas ni los resultados llegan al candidato ni a los otros agentes")
    void nadaLlegaAlCandidato() throws Exception {
        String tablon = mvc.perform(get("/api/v1/portal/vacantes")).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        String detalle = mvc.perform(get("/api/v1/portal/vacantes/" + vacanteId))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        String misProcesos = mvc.perform(get("/api/v1/portal/postulaciones")
                        .header("Authorization", "Bearer " + tokenLucia))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertThat(tablon).doesNotContain("XQ7").doesNotContain("Certificado BPM vigente");
        assertThat(detalle).doesNotContain("XQ7").doesNotContain("Certificado BPM vigente");
        assertThat(misProcesos).doesNotContain("XQ7").doesNotContain("merma de verduras");

        // Lo que se mandó a los demás agentes no las lleva: solo BUSQUEDA_CV las ve.
        assertThat(jdbc.queryForList("""
                select envio from ejecucion_ia where agente_codigo <> 'BUSQUEDA_CV'""", String.class))
                .isNotEmpty()
                .allSatisfy(envio -> assertThat(envio).doesNotContain("XQ7"));
        // Ni los avisos de la campana ni los correos.
        assertThat(contar("select count(*) from aviso_portal where cast(aviso_portal as text) like '%XQ7%'"))
                .isZero();
        assertThat(contar("select count(*) from correo_enviado where cast(correo_enviado as text) like '%XQ7%'"))
                .isZero();

        // El contexto de WhatsApp de Lucía, pedido como lo pide el bot: el listado y el detalle.
        String listado = contextoDeWhatsappDeLucia(null);
        String referencia = json.readTree(listado).at("/postulaciones/0/postulacionRef").asText();
        String contextoWhatsapp = contextoDeWhatsappDeLucia(referencia);
        assertThat(json.readTree(contextoWhatsapp).at("/vacante/titulo").asText()).isEqualTo("Chef de cocina");
        sinNadaDeLoBuscado("el listado de WhatsApp", listado);
        sinNadaDeLoBuscado("el contexto de WhatsApp", contextoWhatsapp);

        // Lo que reciben los generadores. La prueba del puesto y la simulación leen la vacante de
        // ContextoDeLaVacante; las preguntas recomendadas (las dos) y la prueba propia, de
        // DatosDeLaVacanteParaIa.
        Postulacion deLucia = postulaciones.findById(lucia).orElseThrow();
        sinNadaDeLoBuscado("la vacante para la prueba del puesto y la simulación",
                contextoDeLaVacante.queBuscaLaVacanteDe(deLucia));
        sinNadaDeLoBuscado("la vacante para las preguntas recomendadas y la prueba propia",
                json.writeValueAsString(datosDeLaVacanteParaIa.de(vacantes.findById(vacanteId).orElseThrow())));
        for (String proposito : List.of("PERFIL_INTEGRAL", "PRUEBA_PUESTO")) {
            jdbc.update("""
                    insert into propuesta_preguntas (organizacion_id, vacante_id, puntos_que_faltan, proposito)
                    select organizacion_id, id, 40, ? from vacante where id = ?""", proposito, vacanteId);
        }
        try {
            Object perfil = recomendador.insumo(vacanteId);
            Object prueba = recomendador.insumoPrueba(vacanteId);
            assertThat(perfil).isNotNull();
            assertThat(prueba).isNotNull();
            sinNadaDeLoBuscado("lo que recibe el recomendador de preguntas", json.writeValueAsString(perfil));
            sinNadaDeLoBuscado("lo que recibe el recomendador de la prueba", json.writeValueAsString(prueba));
        } finally {
            jdbc.update("delete from propuesta_preguntas where vacante_id = ?", vacanteId);
        }
    }

    private void sinNadaDeLoBuscado(String donde, String texto) {
        assertThat(texto).as(donde).isNotBlank();
        for (String buscado : LO_BUSCADO) {
            assertThat(texto).as(donde).doesNotContain(buscado);
        }
    }

    /**
     * La consulta del bot sobre el número de Lucía. La primera vez enciende WhatsApp, Lucía pulsa
     * el botón del portal y escribe al bot desde su número, como en producción.
     */
    private String contextoDeWhatsappDeLucia(String referencia) throws Exception {
        if (contar("select count(*) from vinculo_whatsapp where estado = 'ACTIVO'") == 0) {
            conToken(put("/api/v1/panel/parametros/whatsapp_novedades_activo"), tokenEquipo,
                    "{\"valor\":\"true\",\"motivo\":\"Para pedir el contexto como el bot\"}")
                    .andExpect(status().is2xxSuccessful());
            String codigo = jdbc.queryForObject("select uuid::text from postulacion where id = ?",
                    String.class, lucia);
            conToken(post("/api/v1/portal/postulaciones/" + codigo + "/whatsapp"), tokenLucia, null)
                    .andExpect(status().isOk());
            firmadoPorElBot("/api/v1/integraciones/whatsapp/vinculos",
                    "{\"telefono\":\"%s\",\"wamid\":\"w-lucia\",\"texto\":\"hola\"}".formatted(TELEFONO_DE_LUCIA))
                    .andExpect(status().isOk());
        }
        String cuerpo = referencia == null
                ? "{\"telefono\":\"%s\"}".formatted(TELEFONO_DE_LUCIA)
                : "{\"telefono\":\"%s\",\"postulacionRef\":\"%s\"}".formatted(TELEFONO_DE_LUCIA, referencia);
        return firmadoPorElBot("/api/v2/integraciones/whatsapp/contexto", cuerpo)
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
    }

    /** Un POST como el del bot: firmado sobre la marca, la ruta y el cuerpo. */
    private ResultActions firmadoPorElBot(String ruta, String cuerpo) throws Exception {
        String marca = String.valueOf(Instant.now().getEpochSecond());
        return mvc.perform(post(ruta).contentType(MediaType.APPLICATION_JSON).content(cuerpo)
                .header(FirmaWhatsapp.CABECERA_MARCA, marca)
                .header(FirmaWhatsapp.CABECERA_FIRMA, FirmaWhatsapp.firmar(SECRETO_DEL_BOT, marca, ruta,
                        cuerpo.getBytes(StandardCharsets.UTF_8))));
    }

    // ============ Editar las líneas ============

    @Test
    @Order(6)
    @DisplayName("AC-13/15: cambiarlas pide confirmar; al confirmar vuelve a buscar sin mover a nadie")
    void editarConCandidatosDentro() throws Exception {
        conToken(get("/api/v1/panel/vacantes/" + vacanteId + "/que-buscar-en-el-cv"), tokenEquipo, null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.cvsParaVolverABuscar").value(1));

        List<String> nuevas = List.of("Experiencia en cocina saludable", "Dirige equipos",
                "Carne de salud vigente");
        // Sin confirmar: 409 y no cambia nada
        conToken(put("/api/v1/panel/vacantes/" + vacanteId), tokenEquipo, cuerpoDeEdicion(nuevas, false))
                .andExpect(status().isConflict());
        assertThat(contar("select count(*) from linea_busqueda_cv where vacante_id = " + vacanteId
                + " and archivada_en is null and texto = '" + LINEA_SECRETA + "'")).isEqualTo(1);

        conToken(put("/api/v1/panel/vacantes/" + vacanteId), tokenEquipo, cuerpoDeEdicion(nuevas, true))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.huboCambios").value(true))
                .andExpect(jsonPath("$.postulantesAvisados").value(0))
                .andExpect(jsonPath("$.cvsABuscar").value(1));

        // Lo de antes se archivó, no se borró
        assertThat(contar("select count(*) from linea_busqueda_cv where vacante_id = " + vacanteId
                + " and archivada_en is not null")).isEqualTo(3);
        assertThat(contar("select count(*) from resultado_busqueda_cv where postulacion_id = " + lucia
                + " and archivado_en is not null")).isEqualTo(3);
        // El CV sin texto sigue sin texto con las líneas nuevas, sin volver a mirarlo
        assertThat(contar("select count(*) from busqueda_cv where postulacion_id = " + carla
                + " and estado = 'SIN_TEXTO' and archivada_en is null and version_lineas = 2")).isEqualTo(1);

        esperarA(() -> contar("""
                select count(*) from trabajo_ia where postulacion_id = %d and agente_codigo = 'BUSQUEDA_CV'
                and modo = 'REBUSCA' and estado = 'TERMINADO'""".formatted(lucia)) == 1,
                "termine la rebúsqueda de Lucía");
        assertThat(contar("select count(*) from resultado_busqueda_cv where postulacion_id = " + lucia
                + " and archivado_en is null and version_lineas = 2")).isEqualTo(3);
        // Nadie cambió de etapa por esto
        assertThat(estadoDe(lucia)).isEqualTo("PERFIL_POR_CONFIRMAR");
        // Y la auditoría dice quién, qué había y qué hay
        assertThat(jdbc.queryForObject("""
                select count(*) from auditoria where accion = 'editar_que_buscar_en_el_cv'
                and entidad_id = ?""", Integer.class, vacanteId)).isEqualTo(1);
    }

    @Test
    @Order(7)
    @DisplayName("AC-19: «Volver a buscar» pide ajustar_nota (403 sin él) y encola solo esa búsqueda")
    void volverABuscar() throws Exception {
        String sinPermiso = cuentaDePanelConRol("dev-sin-ajuste", "RESPONSABLE_AREA");
        conToken(post("/api/v1/panel/postulaciones/" + lucia + "/busqueda-cv"), sinPermiso, null)
                .andExpect(status().isForbidden());
        // Con el permiso, el ranking le dice al panel que pinte el botón
        assertThat(json.readTree(conToken(get("/api/v1/panel/vacantes/" + vacanteId + "/ranking"),
                tokenEquipo, null).andReturn().getResponse().getContentAsString())
                .get("puedeVolverABuscar").asBoolean()).isTrue();

        int antes = contar("select count(*) from trabajo_ia where agente_codigo = 'BUSQUEDA_CV'");
        conToken(post("/api/v1/panel/postulaciones/" + lucia + "/busqueda-cv"), tokenEquipo, null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("ENCOLADA"));
        assertThat(contar("select count(*) from trabajo_ia where agente_codigo = 'BUSQUEDA_CV'"))
                .isEqualTo(antes + 1);
        esperarA(() -> contar("""
                select count(*) from trabajo_ia where postulacion_id = %d and agente_codigo = 'BUSQUEDA_CV'
                and estado in ('PENDIENTE', 'EN_CURSO')""".formatted(lucia)) == 0, "termine la búsqueda");
    }

    // ============ El peso ============

    @Test
    @Order(8)
    @DisplayName("AC-21: todas las versiones quedan en 25, una nueva sin el campo también, y fuera de 0 a 50 no")
    void elPesoEnLaVersion() throws Exception {
        assertThat(contar("select count(*) from version_pesos where peso_busqueda_cv <> 25")).isZero();

        long nueva = Long.parseLong(leer(conToken(post("/api/v1/panel/pesos/versiones"), tokenEquipo,
                "{\"etiqueta\":\"Sin el campo\"}").andReturn().getResponse().getContentAsString(), "id"));
        assertThat(jdbc.queryForObject("select peso_busqueda_cv from version_pesos where id = ?",
                BigDecimal.class, nueva)).isEqualByComparingTo("25");

        assertThatThrownBy(() -> jdbc.update("update version_pesos set peso_busqueda_cv = 60 where id = ?", nueva))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @Order(9)
    @DisplayName("AC-22: dos versiones que solo difieren en el peso: la confirmación lo dice y se recalcula")
    void cambiarDeVersion() throws Exception {
        long actual = jdbc.queryForObject("select version_pesos_id from vacante where id = ?",
                Long.class, vacanteId);
        long conTreinta = jdbc.queryForObject("""
                insert into version_pesos (organizacion_id, etiqueta, estado, publicada_en, creado_en,
                                           peso_busqueda_cv)
                select organizacion_id, 'Igual pero con 30', 'PUBLICADA', now(), now(), 30
                from version_pesos where id = ? returning id""", Long.class, actual);
        for (String tabla : List.of("peso_etapa (version_pesos_id, etapa_codigo, peso, creado_en) select ?, etapa_codigo, peso, now() from peso_etapa",
                "peso_componente_perfil (version_pesos_id, componente, peso, creado_en) select ?, componente, peso, now() from peso_componente_perfil",
                "peso_dimension (version_pesos_id, nivel_puesto_codigo, dimension_codigo, peso, creado_en) select ?, nivel_puesto_codigo, dimension_codigo, peso, now() from peso_dimension",
                "peso_criterio (version_pesos_id, nivel_puesto_codigo, criterio_id, peso, creado_en) select ?, nivel_puesto_codigo, criterio_id, peso, now() from peso_criterio")) {
            jdbc.update("insert into " + tabla + " where version_pesos_id = ?", conTreinta, actual);
        }

        conToken(get("/api/v1/panel/vacantes/" + vacanteId + "/version-pesos/cambio?versionPesosId="
                + conTreinta), tokenEquipo, null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.cambia").value(true))
                .andExpect(jsonPath("$.queBuscarEnElCv.nombre").value("Qué buscar en el CV"))
                .andExpect(jsonPath("$.queBuscarEnElCv.antes").value(25))
                .andExpect(jsonPath("$.queBuscarEnElCv.despues").value(30));

        conToken(post("/api/v1/panel/vacantes/" + vacanteId + "/version-pesos"), tokenEquipo,
                "{\"versionPesosId\": %d}".formatted(conTreinta)).andExpect(status().isOk());

        // 80 × 0,7 + 50 × 0,3 = 71, sin IA y sin mover a nadie
        assertThat(filaDelRanking(lucia, "PERFIL_INTEGRAL").get("notaCurriculum").decimalValue())
                .isEqualByComparingTo("71");
        assertThat(estadoDe(lucia)).isEqualTo("PERFIL_POR_CONFIRMAR");
    }

    // ============ Apoyo ============

    private String cuerpoDeVacante(List<String> lineas) throws Exception {
        return """
                {"solicitudTalentoId": %d, "puestoId": %d,
                 "titulo": "Chef de cocina", "descripcion": "Cocina de un restaurante saludable",
                 "tipoCierre": "PERMANENTE", "responsableUsuarioId": 1,
                 "queBuscarEnElCv": %s}""".formatted(solicitudId, puestoId, json.writeValueAsString(lineas));
    }

    private String cuerpoDeEdicion(List<String> lineas, boolean confirmado) throws Exception {
        return """
                {"solicitudTalentoId": %d, "puestoId": %d,
                 "titulo": "Chef de cocina", "descripcion": "Cocina de un restaurante saludable",
                 "tipoCierre": "PERMANENTE", "responsableUsuarioId": 1,
                 "queBuscarEnElCv": %s, "confirmarNuevaBusqueda": %s}"""
                .formatted(solicitudId, puestoId, json.writeValueAsString(lineas), confirmado);
    }

    private JsonNode filaDelRanking(long postulacionId, String etapa) throws Exception {
        JsonNode ranking = json.readTree(conToken(get("/api/v1/panel/vacantes/" + vacanteId
                + "/ranking?etapa=" + etapa), tokenEquipo, null)
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        for (JsonNode fila : ranking.get("filas")) {
            if (fila.get("postulacionId").asLong() == postulacionId) {
                return fila;
            }
        }
        throw new AssertionError("La postulación " + postulacionId + " no está en el ranking");
    }

    private void prepararSolicitudYPuesto() throws Exception {
        jdbc.update("INSERT INTO area (organizacion_id, nombre, es_activa) VALUES (1, 'Cocina', true)");
        Long areaId = jdbc.queryForObject("SELECT id FROM area LIMIT 1", Long.class);
        puestoId = Long.parseLong(leer(conToken(post("/api/v1/panel/puestos"), tokenEquipo, """
                {"codigo": "CHEF", "nombre": "Chef de cocina",
                 "nivelPuestoCodigo": "EJECUCION", "familiaCodigo": "TECNOLOGIA"}""")
                .andReturn().getResponse().getContentAsString(), "id"));
        solicitudId = Long.parseLong(leer(conToken(post("/api/v1/panel/solicitudes"), tokenEquipo, """
                {"areaId": %d, "puestoId": %d, "urgencia": "NORMAL",
                 "nivelPuestoCodigo": "EJECUCION", "familiaCodigo": "TECNOLOGIA",
                 "resultadoPrincipal": "Llevar la cocina",
                 "motivo": "Se abre un local",
                 "consecuenciaNoContratar": "No abre",
                 "analisisCapacidad": "No alcanza el equipo",
                 "responsableUsuarioId": 1,
                 "resultadosEsperados": [
                   {"descripcion": "Carta nueva", "indicador": "publicada"},
                   {"descripcion": "Menos merma", "indicador": "la mitad"},
                   {"descripcion": "Equipo formado", "indicador": "seis"}
                 ]}""".formatted(areaId, puestoId))
                .andReturn().getResponse().getContentAsString(), "id"));
        conToken(post("/api/v1/panel/solicitudes/" + solicitudId + "/aprobacion"), tokenEquipo,
                "{\"motivo\":\"Hay presupuesto\"}").andExpect(status().isOk());
    }

    private void publicar(long id) throws Exception {
        conToken(post("/api/v1/panel/vacantes/" + id + "/origen-preguntas"), tokenEquipo,
                "{\"origen\":\"NIVEL\"}").andExpect(status().isOk());
        Long plantillaId = jdbc.queryForObject(
                "select id from plantilla_evaluacion where nivel_puesto_codigo = 'EJECUCION'", Long.class);
        conToken(post("/api/v1/panel/vacantes/" + id + "/plantilla-evaluacion"), tokenEquipo,
                "{\"plantillaEvaluacionId\": %d}".formatted(plantillaId)).andExpect(status().isOk());
        Long versionPruebaId = armarUnaPruebaValida(tokenEquipo);
        conToken(post("/api/v1/panel/vacantes/" + id + "/plantilla-prueba"), tokenEquipo,
                "{\"versionPlantillaPruebaId\": %d}".formatted(versionPruebaId)).andExpect(status().isOk());
        conToken(post("/api/v1/panel/vacantes/" + id + "/publicacion"), tokenEquipo, null)
                .andExpect(status().isOk());
    }

    private Long armarUnaPruebaValida(String token) throws Exception {
        long plantillaId = Long.parseLong(leer(conToken(post("/api/v1/panel/plantillas-prueba"), token,
                "{\"nombre\":\"Prueba de cocina\"}").andReturn().getResponse().getContentAsString(), "id"));
        long versionId = Long.parseLong(leer(conToken(
                post("/api/v1/panel/plantillas-prueba/" + plantillaId + "/versiones"), token, """
                {"enunciado":"Arma la carta","modalidad":"CRONOMETRADA",
                 "duracionMinutos":90,"minutoCambioMin":30,"minutoCambioMax":50,"minutosExtra":10}""")
                .andReturn().getResponse().getContentAsString(), "id"));
        for (int i = 0; i < 8; i++) {
            long id = Long.parseLong(leer(conToken(post("/api/v1/panel/plantillas-prueba/preguntas"), token,
                    "{\"codigo\":\"UNIV_QB_%d\",\"enunciado\":\"Pregunta universal %d\",\"tipo\":\"UNIVERSAL\"}"
                            .formatted(i, i)).andReturn().getResponse().getContentAsString(), "id"));
            conToken(post("/api/v1/panel/plantillas-prueba/versiones/" + versionId + "/preguntas"), token,
                    "{\"preguntaPruebaId\": %d}".formatted(id)).andExpect(status().isOk());
        }
        for (int i = 0; i < 3; i++) {
            long id = Long.parseLong(leer(conToken(post("/api/v1/panel/plantillas-prueba/preguntas"), token,
                    "{\"codigo\":\"ESP_QB_%d\",\"enunciado\":\"Pregunta específica %d\",\"tipo\":\"ESPECIFICA\"}"
                            .formatted(i, i)).andReturn().getResponse().getContentAsString(), "id"));
            conToken(post("/api/v1/panel/plantillas-prueba/versiones/" + versionId + "/preguntas"), token,
                    "{\"preguntaPruebaId\": %d}".formatted(id)).andExpect(status().isOk());
        }
        conToken(post("/api/v1/panel/plantillas-prueba/versiones/" + versionId + "/rubrica"), token, """
                {"codigo":"RESULTADO_QB","nombre":"Resultado","puntos":100,"metodoVerificacion":"PERSONA"}""")
                .andExpect(status().isCreated());
        conToken(post("/api/v1/panel/plantillas-prueba/versiones/" + versionId + "/publicacion"), token, null)
                .andExpect(status().isOk());
        return versionId;
    }

    private byte[] curriculumEnPdf(List<String> lineas) throws Exception {
        try (PDDocument documento = new PDDocument(); ByteArrayOutputStream salida = new ByteArrayOutputStream()) {
            PDPage pagina = new PDPage();
            documento.addPage(pagina);
            if (!lineas.isEmpty()) {
                try (PDPageContentStream lienzo = new PDPageContentStream(documento, pagina)) {
                    lienzo.beginText();
                    lienzo.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 11);
                    lienzo.setLeading(16f);
                    lienzo.newLineAtOffset(50, 720);
                    for (String linea : lineas) {
                        lienzo.showText(linea);
                        lienzo.newLine();
                    }
                    lienzo.endText();
                }
            }
            documento.save(salida);
            return salida.toByteArray();
        }
    }

    private String postular(String tokenCandidato, byte[] pdf) throws Exception {
        MockMultipartFile cv = new MockMultipartFile("cv", "cv.pdf", "application/pdf", pdf);
        return leer(mvc.perform(multipart("/api/v1/portal/postulaciones")
                        .file(cv)
                        .param("vacanteId", String.valueOf(vacanteId))
                        .param("resultadoOrgulloso", "Baje la merma a la mitad")
                        .param("aceptaTratamiento", "true")
                        .header("Authorization", "Bearer " + tokenCandidato))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString(), "codigo");
    }

    private String crearCandidatoYEntrar(String correo) throws Exception {
        mvc.perform(post("/api/v1/portal/cuentas")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                        {"nombre":"Lucia","apellidos":"Ramos","correo":"%s",
                         "contrasena":"unaClaveLarga123","ciudadUbigeo":"1501","telefono": "987654321",
                         "aceptaPlataforma":true, "aceptaFuturosContactos":false}""".formatted(correo)))
                .andExpect(status().isCreated());
        return leer(mvc.perform(post("/api/v1/portal/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"correo\":\"%s\",\"contrasena\":\"unaClaveLarga123\"}".formatted(correo)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(), "token");
    }

    private String cuentaDePanelConRol(String renaserOsId, String rolCodigo) throws Exception {
        Long personaId = jdbc.queryForObject(
                "insert into persona (nombre, apellidos) values ('QA', ?) returning id",
                Long.class, renaserOsId);
        Long usuarioId = jdbc.queryForObject("""
                insert into usuario (organizacion_id, persona_id, usuario_renaser_os_id, es_equipo, es_activo)
                values (1, ?, ?, true, true) returning id""", Long.class, personaId, renaserOsId);
        jdbc.update("""
                insert into usuario_rol (usuario_id, rol_id)
                select ?, id from rol where organizacion_id = 1 and codigo = ?""", usuarioId, rolCodigo);
        return leer(mvc.perform(post("/api/v1/panel/auth/dev-login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"usuarioRenaserOsId\":\"%s\"}".formatted(renaserOsId)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(), "token");
    }

    private String estadoDe(long id) {
        return jdbc.queryForObject("select estado_codigo from postulacion where id = ?", String.class, id);
    }

    private void esperarA(BooleanSupplier condicion, String que) {
        long limite = System.currentTimeMillis() + 60_000;
        while (System.currentTimeMillis() < limite) {
            if (condicion.getAsBoolean()) {
                return;
            }
            try {
                Thread.sleep(250);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
        fail("Se agotó la espera a que " + que + ". Trabajos: "
                + jdbc.queryForList("select id, postulacion_id, agente_codigo, modo, estado, intentos from trabajo_ia")
                + " · Ejecuciones: "
                + jdbc.queryForList("select agente_codigo, es_exitosa, error from ejecucion_ia"));
    }

    private int contar(String sql) {
        Integer valor = jdbc.queryForObject(sql, Integer.class);
        return valor == null ? 0 : valor;
    }

    private long idDe(String codigo) {
        return jdbc.queryForObject("select id from postulacion where uuid = ?::uuid", Long.class, codigo);
    }

    private String entrarComoEquipo() throws Exception {
        return leer(mvc.perform(post("/api/v1/panel/auth/dev-login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"usuarioRenaserOsId\":\"dev-1\"}"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(), "token");
    }

    private ResultActions conToken(
            org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder peticion,
            String token, String cuerpo) throws Exception {
        peticion.header("Authorization", "Bearer " + token).contentType(MediaType.APPLICATION_JSON);
        if (cuerpo != null) peticion.content(cuerpo);
        return mvc.perform(peticion);
    }

    private String leer(String cuerpoRespuesta, String campo) throws Exception {
        return json.readTree(cuerpoRespuesta).get(campo).asText();
    }
}
