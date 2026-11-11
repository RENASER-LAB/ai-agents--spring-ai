package com.renaser.ai.ai_engine.integracion;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.renaser.ai.ai_engine.comun.programado.SondeoVencimientos;
import com.renaser.ai.ai_engine.integracion.soporte.ImagenesDeContenedores;
import com.renaser.ai.ai_engine.postulacion.repository.PostulacionRepository;
import com.renaser.ai.ai_engine.postulacion.service.MaquinaEstados;
import com.renaser.ai.ai_engine.postulacion.service.ServicioRecordatorios;
import com.renaser.ai.ai_engine.whatsapp.service.FirmaWhatsapp;

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
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.rabbitmq.RabbitMQContainer;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * El Perfil Integral se responde mientras la vacante esté abierta, de punta a punta
 * (specs/Ready/perfil-integral-hasta-el-cierre-de-la-vacante, V78).
 *
 * <p>Dos vacantes de RENASER con sus preguntas propias y la prueba del editor:
 * <ul>
 *   <li><b>Permanente</b>, que el equipo cierra a mano con cuatro personas dentro: Ana sin
 *       empezar, Beto a medias, Carla que ya entregó y Dora, a quien el equipo movió a otra
 *       etapa sin que terminara (AC-09, AC-11, AC-12, AC-13);</li>
 *   <li><b>con fecha límite</b>, que se cierra sola al pasar su día (AC-10).</li>
 * </ul>
 * Y una tercera, cerrada desde «antes del despliegue» por SQL con alguien dentro, para lo que
 * recoge el sondeo la primera vez (AC-14, la mitad que necesita la aplicación).
 *
 * <p>WhatsApp v2 va encendido, como en producción: a quien se le cierra la postulación le
 * llega el resultado y no además «la convocatoria cerró y tu postulación sigue».
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@Testcontainers
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@DisplayName("El Perfil Integral dura hasta que la vacante se cierra")
class FlujoPerfilHastaElCierreIT {

    private static final String SECRETO_DEL_BOT = "secreto-de-pruebas-del-bot-perfil-hasta-el-cierre";
    private static final ZoneId LIMA = ZoneId.of("America/Lima");
    private static final String VACANTE_CERRADA =
            "La vacante se cerró y esta evaluación ya no se puede responder.";

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
        registro.add("app.whatsapp.modo", () -> "falso");
        registro.add("app.whatsapp.secreto-desde-el-bot", () -> SECRETO_DEL_BOT);
        registro.add("app.whatsapp.enlace-mis-procesos", () -> "https://portal.pruebas.renaser.pe/procesos");
        registro.add("app.whatsapp.productor-v2", () -> "true");
        registro.add("app.whatsapp.sondeo-ms", () -> "3600000");
        // Los sondeos los corre la prueba a mano: así sabe cuándo pasó cada cosa.
        registro.add("app.sondeo.periodo-ms", () -> "3600000");
    }

    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired SondeoVencimientos sondeo;
    @Autowired ServicioRecordatorios recordatorios;
    @Autowired MaquinaEstados maquina;
    @Autowired PostulacionRepository postulacionesRepo;
    @Autowired TransactionTemplate enUnaTransaccion;
    final ObjectMapper json = new ObjectMapper();

    static String tokenTalento;
    static long areaId;
    static long puestoId;
    static long permanente;
    static long conFecha;
    static long deAntes;
    static final Map<String, Long> postulacion = new HashMap<>();
    static final Map<String, String> codigo = new HashMap<>();
    static final Map<String, String> token = new HashMap<>();
    static final Map<String, String> telefono = new HashMap<>();
    static int celular = 100;

    // ============ Las vacantes y quién está dentro ============

    @Test
    @Order(1)
    @DisplayName("«Cómo se cierra» ofrece Permanente y Fecha límite; la API rechaza sin día, un día "
            + "pasado y «Por plazas»; la fecha se guarda a las 23:59:59 de Lima (AC-01, AC-02, AC-03)")
    void lasVacantes() throws Exception {
        tokenTalento = leer(mvc.perform(post("/api/v1/panel/auth/dev-login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"usuarioRenaserOsId\":\"dev-talento\"}"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString(), "token");
        jdbc.update("INSERT INTO area (organizacion_id, nombre, es_activa) VALUES (1, 'Caja', true)");
        areaId = jdbc.queryForObject("SELECT id FROM area ORDER BY id DESC LIMIT 1", Long.class);
        puestoId = Long.parseLong(leer(conToken(post("/api/v1/panel/puestos"), tokenTalento, """
                {"codigo": "CAJERO_V78", "nombre": "Cajero",
                 "nivelPuestoCodigo": "EJECUCION", "familiaCodigo": "OPERACIONES"}""")
                .andReturn().getResponse().getContentAsString(), "id"));
        conToken(put("/api/v1/panel/parametros/whatsapp_novedades_activo"), tokenTalento,
                "{\"valor\":\"true\",\"motivo\":\"El número del bot ya atiende\"}")
                .andExpect(status().is2xxSuccessful());

        JsonNode formas = respuesta(mvc.perform(get("/api/v1/panel/catalogos")
                .header("Authorization", "Bearer " + tokenTalento))).get("tiposCierre");
        assertThat(formas.findValuesAsText("codigo")).containsExactly("PERMANENTE", "FECHA");
        assertThat(formas.findValuesAsText("nombre")).containsExactly("Permanente", "Fecha límite");

        // AC-02 por la API, sin pasar por el formulario
        long solicitud = solicitudAprobada();
        crearVacante(solicitud, "Cajero sin día", "\"tipoCierre\": \"FECHA\"")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("Elige el día en que cierra la vacante."));
        crearVacante(solicitud, "Cajero de ayer", "\"tipoCierre\": \"FECHA\", \"cierraEn\": \"%sT00:00:00Z\""
                .formatted(LocalDate.now(LIMA).minusDays(1)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("Esa fecha ya pasó: elige un día a partir de hoy."));
        crearVacante(solicitud, "Cajero por plazas", "\"tipoCierre\": \"PLAZAS\", \"plazas\": 2")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value(
                        "Elige cómo se cierra la vacante: Permanente o Fecha límite."));

        permanente = idDe(crearVacante(solicitud, "Cajero principal", "\"tipoCierre\": \"PERMANENTE\""));
        LocalDate dia = LocalDate.now(LIMA).plusDays(3);
        conFecha = idDe(crearVacante(solicitudAprobada(), "Cajero de turno",
                "\"tipoCierre\": \"FECHA\", \"cierraEn\": \"%sT00:00:00Z\"".formatted(dia)));
        deAntes = idDe(crearVacante(solicitudAprobada(), "Cajero de antes", "\"tipoCierre\": \"PERMANENTE\""));
        for (long vacante : List.of(permanente, conFecha, deAntes)) {
            preguntasPropias(vacante);
            pruebaPropia(vacante);
            conToken(post("/api/v1/panel/vacantes/" + vacante + "/publicacion"), tokenTalento, null)
                    .andExpect(status().isOk());
        }

        // AC-03: el día elegido a las 23:59:59 de Lima, en la base y en el panel
        Instant finDelDia = dia.atTime(23, 59, 59).atZone(LIMA).toInstant();
        assertThat(jdbc.queryForObject("select cierra_en from vacante where id = ?", Instant.class, conFecha))
                .isEqualTo(finDelDia);
        assertThat(Instant.parse(respuesta(mvc.perform(get("/api/v1/panel/vacantes/" + conFecha)
                .header("Authorization", "Bearer " + tokenTalento))).get("cierraEn").asText()))
                .isEqualTo(finDelDia);
    }

    @Test
    @Order(2)
    @DisplayName("Cada evaluación nace sin fecha y el portal no dice ningún plazo (AC-04, AC-05)")
    void nacenSinFecha() throws Exception {
        postular("ana", permanente);
        postular("beto", permanente);
        postular("carla", permanente);
        postular("dora", permanente);
        postular("eva", conFecha);
        postular("fer", conFecha);
        postular("gina", conFecha);
        postular("hugo", deAntes);

        assertThat(contar("""
                select count(*) from evaluacion e join postulacion p on p.evaluacion_id = e.id
                 where e.vence_en is not null""")).isZero();
        assertThat(contar("select count(*) from postulacion where estado_codigo = 'PERFIL_TURNO_CANDIDATO'"))
                .isEqualTo(8);
        JsonNode portada = respuesta(mvc.perform(get("/api/v1/portal/evaluacion/" + codigo.get("ana"))
                .header("Authorization", "Bearer " + token.get("ana"))));
        assertThat(portada.path("venceEn").isNull() || portada.path("venceEn").isMissingNode()).isTrue();

        // Beto y Fer a medias, Carla y Gina entregan, a Dora el equipo la mueve sin terminar
        empezar("beto");
        empezar("fer");
        responderYEntregar("carla");
        responderYEntregar("gina");
        enUnaTransaccion.executeWithoutResult(t -> maquina.transicionar(
                postulacionesRepo.findById(postulacion.get("dora")).orElseThrow(),
                "SIMULACION_POR_HABILITAR", null, "La movió el equipo", true, false, null, false));
        for (String quien : List.of("ana", "beto", "carla", "eva", "fer", "gina")) {
            vincularA(quien);
        }
    }

    @Test
    @Order(3)
    @DisplayName("Creada hace más de 14 días en una vacante publicada, el sondeo no la cierra (AC-07)")
    void catorceDiasNoCierranNada() {
        jdbc.update("update evaluacion set creado_en = now() - interval '20 days' where id = "
                + "(select evaluacion_id from postulacion where id = ?)", postulacion.get("ana"));

        sondeo.ejecutar();

        assertThat(estado("ana")).isEqualTo("PERFIL_TURNO_CANDIDATO");
        assertThat(estadoDeSuEvaluacion("ana")).isEqualTo("PENDIENTE");
    }

    @Test
    @Order(4)
    @DisplayName("Sin fecha, sale solo el primer recordatorio y nunca uno que diga «vence el» (AC-08)")
    void soloElPrimerRecordatorio() {
        Instant entro = jdbc.queryForObject("""
                select ocurrida_en from transicion_estado
                 where postulacion_id = ? and estado_nuevo_codigo = 'PERFIL_TURNO_CANDIDATO'
                 order by id desc limit 1""", Instant.class, postulacion.get("ana"));
        Instant alDiaSiguiente = entro.plus(2, ChronoUnit.DAYS).atZone(LIMA)
                .withHour(12).withMinute(0).toInstant();

        recordatorios.enviarPendientes(alDiaSiguiente);
        recordatorios.enviarPendientes(alDiaSiguiente.plus(10, ChronoUnit.DAYS));

        assertThat(jdbc.queryForList("select tipo from recordatorio_enviado where postulacion_id = ?",
                String.class, postulacion.get("ana"))).containsExactly("TRAS_ENTRAR");
        assertThat(jdbc.queryForList("select titulo from aviso_portal where usuario_id = ? "
                + "and tipo = 'RECORDATORIO_EVALUACION'", String.class, usuarioDe("ana")))
                .containsExactly("Aún no has respondido tu evaluación para Cajero principal");
        assertThat(contar("select count(*) from aviso_portal where titulo like '%vence el%'")).isZero();
    }

    // ============ El cierre a mano ============

    @Test
    @Order(5)
    @DisplayName("Cerrar a mano vence las dos sin terminar con el cierre como límite y cierra sus "
            + "postulaciones con PLAZO_VENCIDO, con correo y campana; Carla sigue (AC-09); Dora "
            + "pierde la evaluación y sigue en su etapa (AC-13)")
    void cerrarAMano() throws Exception {
        conToken(post("/api/v1/panel/vacantes/" + permanente + "/cierre"), tokenTalento,
                "{\"motivo\":\"Ya contratamos\"}").andExpect(status().isOk());

        Instant cerradaEn = jdbc.queryForObject("select cerrada_en from vacante where id = ?",
                Instant.class, permanente);
        for (String quien : List.of("ana", "beto")) {
            assertThat(estadoDeSuEvaluacion(quien)).as(quien).isEqualTo("VENCIDA");
            assertThat(jdbc.queryForObject("select e.vence_en from evaluacion e join postulacion p "
                    + "on p.evaluacion_id = e.id where p.id = ?", Instant.class, postulacion.get(quien)))
                    .as(quien).isEqualTo(cerradaEn);
            assertThat(estado(quien)).as(quien).isEqualTo("CERRADA");
            assertThat(jdbc.queryForObject("select motivo_cierre from postulacion where id = ?",
                    String.class, postulacion.get(quien))).isEqualTo("PLAZO_VENCIDO");
            assertThat(jdbc.queryForObject("""
                    select es_sistema from transicion_estado
                     where postulacion_id = ? and estado_nuevo_codigo = 'CERRADA'""",
                    Boolean.class, postulacion.get(quien))).isTrue();
            assertThat(correos(quien)).containsOnlyOnce("POSTULACION_CERRADA");
            assertThat(avisos(quien)).contains("POSTULACION_CERRADA");
        }
        assertThat(estado("carla")).isEqualTo("PERFIL_CALIFICANDO");
        assertThat(correos("carla")).doesNotContain("POSTULACION_CERRADA");
        // AC-13
        assertThat(estadoDeSuEvaluacion("dora")).isEqualTo("VENCIDA");
        assertThat(estado("dora")).isEqualTo("SIMULACION_POR_HABILITAR");
        assertThat(correos("dora")).doesNotContain("POSTULACION_CERRADA");
    }

    @Test
    @Order(6)
    @DisplayName("Con WhatsApp v2, las dos cerradas reciben su resultado y no «la convocatoria cerró»; "
            + "Carla recibe esto último (AC-11)")
    void whatsappDiceLoQueEsCierto() {
        for (String quien : List.of("ana", "beto")) {
            assertThat(jdbc.queryForList("select tipo || ':' || coalesce(resultado_tipo, '') "
                    + "from evento_whatsapp where postulacion_id = ?", String.class, postulacion.get(quien)))
                    .as(quien).containsExactly("RESULTADO:CERRADA");
        }
        assertThat(jdbc.queryForList("select tipo from evento_whatsapp where postulacion_id = ?",
                String.class, postulacion.get("carla"))).containsExactly("CONVOCATORIA_CERRADA");
    }

    @Test
    @Order(7)
    @DisplayName("Con la evaluación abierta en el portal, guardar o entregar dice que la vacante se cerró (AC-12)")
    void elPortalDiceQueSeCerro() throws Exception {
        String base = "/api/v1/portal/evaluacion/" + codigo.get("beto");
        long pregunta = jdbc.queryForObject("""
                select o.pregunta_id from orden_pregunta o join postulacion p on p.evaluacion_id = o.evaluacion_id
                 where p.id = ? order by o.posicion limit 1""", Long.class, postulacion.get("beto"));
        mvc.perform(put(base + "/respuestas/" + pregunta).header("Authorization", "Bearer " + token.get("beto"))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"texto\":\"Ya casi la tenía\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value(VACANTE_CERRADA));
        mvc.perform(post(base + "/entrega").header("Authorization", "Bearer " + token.get("beto")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value(VACANTE_CERRADA));
        assertThat(contar("select count(*) from respuesta r join postulacion p on p.evaluacion_id = r.evaluacion_id "
                + "where p.id = " + postulacion.get("beto"))).isZero();
    }

    // ============ El cierre por su fecha ============

    @Test
    @Order(8)
    @DisplayName("Pasada su fecha, el sondeo la cierra (autor: sistema), sale del tablón, rechaza "
            + "postulaciones y deja a las tres como en el cierre a mano; dos vueltas no avisan dos veces (AC-10)")
    void cerrarPorSuFecha() throws Exception {
        jdbc.update("update vacante set cierra_en = now() - interval '1 minute' where id = ?", conFecha);

        sondeo.ejecutar();

        assertThat(jdbc.queryForObject("select estado from vacante where id = ?", String.class, conFecha))
                .isEqualTo("CERRADA");
        assertThat(jdbc.queryForList("""
                select coalesce(usuario_id::text, 'sistema') || ' | ' || motivo from auditoria
                 where accion = 'cerrar_vacante' and entidad_id = ?""", String.class, conFecha))
                .containsExactly("sistema | Llegó la fecha de cierre");
        assertThat(respuesta(mvc.perform(get("/api/v1/portal/vacantes"))).toString())
                .doesNotContain("Cajero de turno");
        for (String quien : List.of("eva", "fer")) {
            assertThat(estado(quien)).as(quien).isEqualTo("CERRADA");
            assertThat(estadoDeSuEvaluacion(quien)).as(quien).isEqualTo("VENCIDA");
            assertThat(correos(quien)).as(quien).containsOnlyOnce("POSTULACION_CERRADA");
        }
        assertThat(estado("gina")).isEqualTo("PERFIL_CALIFICANDO");
        assertThat(jdbc.queryForList("select tipo from evento_whatsapp where postulacion_id = ?",
                String.class, postulacion.get("gina"))).containsExactly("CONVOCATORIA_CERRADA");

        // Una postulación nueva ya no entra
        cuenta("ivan");
        mvc.perform(multipart("/api/v1/portal/postulaciones")
                        .file(new MockMultipartFile("cv", "cv.pdf", "application/pdf", "contenido".getBytes()))
                        .param("vacanteId", String.valueOf(conFecha))
                        .param("resultadoOrgulloso", "Cuadré la caja de tres sedes")
                        .param("aceptaTratamiento", "true")
                        .header("Authorization", "Bearer " + token.get("ivan")))
                .andExpect(status().isNotFound());

        // Otra vuelta: nada se cierra dos veces ni avisa dos veces
        int correosAntes = contar("select count(*) from correo_enviado");
        int eventosAntes = contar("select count(*) from evento_whatsapp");
        sondeo.ejecutar();
        assertThat(contar("select count(*) from auditoria where accion = 'cerrar_vacante' "
                + "and entidad_id = " + conFecha)).isEqualTo(1);
        assertThat(contar("select count(*) from correo_enviado")).isEqualTo(correosAntes);
        assertThat(contar("select count(*) from evento_whatsapp")).isEqualTo(eventosAntes);
    }

    // ============ Reabrir por la API del panel ============

    @Test
    @Order(9)
    @DisplayName("Reabrir con la vacante publicada la deja abierta sin fecha y sin días; con la "
            + "vacante cerrada se rechaza (AC-15)")
    void reabrir() throws Exception {
        jdbc.update("update evaluacion set estado = 'VENCIDA', vence_en = now() - interval '1 day' "
                + "where id = (select evaluacion_id from postulacion where id = ?)", postulacion.get("hugo"));

        JsonNode reabierta = respuesta(conToken(post("/api/v1/panel/postulaciones/" + postulacion.get("hugo")
                + "/reapertura-evaluacion"), tokenTalento, "{\"motivo\":\"No le llegó el correo\"}"));
        assertThat(reabierta.path("venceEn").isNull() || reabierta.path("venceEn").isMissingNode()).isTrue();
        assertThat(reabierta.has("diasDePlazo")).isFalse();
        assertThat(estadoDeSuEvaluacion("hugo")).isEqualTo("PENDIENTE");
        assertThat(jdbc.queryForObject("select vence_en from evaluacion where id = "
                + "(select evaluacion_id from postulacion where id = ?)", Instant.class,
                postulacion.get("hugo"))).isNull();

        conToken(post("/api/v1/panel/postulaciones/" + postulacion.get("dora") + "/reapertura-evaluacion"),
                tokenTalento, "{\"motivo\":\"Otra oportunidad\"}")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value(
                        "La vacante está cerrada: su evaluación ya no se puede reabrir"));
        assertThat(estadoDeSuEvaluacion("dora")).isEqualTo("VENCIDA");
    }

    // ============ Lo que seguía abierto en una vacante cerrada desde antes ============

    @Test
    @Order(10)
    @DisplayName("Una vacante cerrada antes del despliegue con alguien a medias: el primer sondeo le "
            + "vence la evaluación con el cierre de la vacante y le cierra la postulación con su "
            + "correo (AC-14)")
    void loDeAntesLoRecogeElSondeo() {
        Instant cierreDeAntes = Instant.now().minus(Duration.ofDays(2)).truncatedTo(ChronoUnit.MICROS);
        jdbc.update("update vacante set estado = 'CERRADA', cerrada_en = ? where id = ?",
                java.sql.Timestamp.from(cierreDeAntes), deAntes);

        sondeo.ejecutar();

        assertThat(estadoDeSuEvaluacion("hugo")).isEqualTo("VENCIDA");
        assertThat(jdbc.queryForObject("select vence_en from evaluacion where id = "
                + "(select evaluacion_id from postulacion where id = ?)", Instant.class,
                postulacion.get("hugo"))).isEqualTo(cierreDeAntes);
        assertThat(estado("hugo")).isEqualTo("CERRADA");
        assertThat(correos("hugo")).containsOnlyOnce("POSTULACION_CERRADA");

        // Y una segunda vuelta no vuelve a avisar
        sondeo.ejecutar();
        assertThat(correos("hugo")).containsOnlyOnce("POSTULACION_CERRADA");
    }

    // ============ Andamios ============

    private long solicitudAprobada() throws Exception {
        long solicitudId = Long.parseLong(leer(conToken(post("/api/v1/panel/solicitudes"), tokenTalento, """
                {"areaId": %d, "puestoId": %d, "urgencia": "NORMAL",
                 "nivelPuestoCodigo": "EJECUCION", "familiaCodigo": "OPERACIONES",
                 "resultadoPrincipal": "Cajas cuadradas", "motivo": "Los cierres se atrasan",
                 "consecuenciaNoContratar": "Se pierde el control", "analisisCapacidad": "No alcanza",
                 "responsableUsuarioId": 1,
                 "resultadosEsperados": [
                   {"descripcion": "Cierres al día", "indicador": "antes del día 5"},
                   {"descripcion": "Arqueos", "indicador": "semanales"},
                   {"descripcion": "Reportes", "indicador": "mensuales"}]}""".formatted(areaId, puestoId))
                .andReturn().getResponse().getContentAsString(), "id"));
        conToken(post("/api/v1/panel/solicitudes/" + solicitudId + "/aprobacion"), tokenTalento,
                "{\"motivo\":\"Aprobada\"}").andExpect(status().isOk());
        return solicitudId;
    }

    private ResultActions crearVacante(long solicitudId, String titulo, String cierre) throws Exception {
        return conToken(post("/api/v1/panel/vacantes"), tokenTalento, """
                {"solicitudTalentoId": %d, "puestoId": %d, "titulo": "%s",
                 "descripcion": "Lleva la caja de la tienda", %s, "responsableUsuarioId": 1}"""
                .formatted(solicitudId, puestoId, titulo, cierre));
    }

    private long idDe(ResultActions creada) throws Exception {
        return Long.parseLong(leer(creada.andExpect(status().is2xxSuccessful())
                .andReturn().getResponse().getContentAsString(), "id"));
    }

    private void preguntasPropias(long vacante) throws Exception {
        String base = "/api/v1/panel/vacantes/" + vacante + "/preguntas-propias";
        conToken(post(base + "/preguntas"), tokenTalento, """
                {"tipo":"ABIERTA","enunciado":"Cuéntanos un cierre de caja difícil",
                 "puntos":100,"queDebeTener":"El monto y la causa"}""").andExpect(status().isOk());
        conToken(post(base + "/publicacion"), tokenTalento, null).andExpect(status().isOk());
    }

    private void pruebaPropia(long vacante) throws Exception {
        String base = "/api/v1/panel/vacantes/" + vacante + "/prueba-propia";
        conToken(put(base + "/borrador"), tokenTalento, """
                {"guiaCalificacion":"Mira las cifras","enunciado":"La caja de marzo no cuadra.",
                 "modalidad":"CRONOMETRADA","duracionMinutos":90}""").andExpect(status().isOk());
        long criterio = respuesta(conToken(post(base + "/criterios"), tokenTalento, """
                {"nombre":"Caja","queEvalua":"Arqueo","puntos":100,"calificador":"IA"}"""))
                .at("/borrador/criterios/0/id").asLong();
        conToken(post(base + "/preguntas"), tokenTalento, """
                {"tipo":"ABIERTA","enunciado":"¿Cómo hallarías el descuadre?","criterioId":%d,
                 "queDebeTener":"La cuenta"}""".formatted(criterio)).andExpect(status().isOk());
        conToken(put(base + "/fecha-limite"), tokenTalento, "{\"cierraEn\":\"%s\"}".formatted(
                Instant.now().plus(Duration.ofDays(20)).truncatedTo(ChronoUnit.SECONDS)))
                .andExpect(status().isOk());
        conToken(post(base + "/publicacion"), tokenTalento, null).andExpect(status().isOk());
    }

    private void cuenta(String quien) throws Exception {
        String correo = quien + ".v78@correo.pe";
        int suCelular = celular++;
        telefono.put(quien, "+51987761" + suCelular);
        mvc.perform(post("/api/v1/portal/cuentas").contentType(MediaType.APPLICATION_JSON).content("""
                {"nombre":"%s","apellidos":"Prueba","correo":"%s","telefono":"987 761 %d",
                 "contrasena":"unaClaveLarga123","ciudadUbigeo":"1501","aceptaPlataforma":true,
                 "aceptaFuturosContactos":false}""".formatted(quien, correo, suCelular)))
                .andExpect(status().isCreated());
        token.put(quien, leer(mvc.perform(post("/api/v1/portal/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"correo\":\"%s\",\"contrasena\":\"unaClaveLarga123\"}".formatted(correo)))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString(), "token"));
    }

    private void postular(String quien, long vacante) throws Exception {
        cuenta(quien);
        String suCodigo = leer(mvc.perform(multipart("/api/v1/portal/postulaciones")
                        .file(new MockMultipartFile("cv", "cv.pdf", "application/pdf", "contenido".getBytes()))
                        .param("vacanteId", String.valueOf(vacante))
                        .param("resultadoOrgulloso", "Cuadré la caja de tres sedes")
                        .param("aceptaTratamiento", "true")
                        .header("Authorization", "Bearer " + token.get(quien)))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString(), "codigo");
        codigo.put(quien, suCodigo);
        postulacion.put(quien, jdbc.queryForObject("select id from postulacion where uuid = ?::uuid",
                Long.class, suCodigo));
    }

    private JsonNode empezar(String quien) throws Exception {
        return respuesta(mvc.perform(post("/api/v1/portal/evaluacion/" + codigo.get(quien) + "/inicio")
                .header("Authorization", "Bearer " + token.get(quien))));
    }

    private void responderYEntregar(String quien) throws Exception {
        String base = "/api/v1/portal/evaluacion/" + codigo.get(quien);
        for (JsonNode p : empezar(quien).get("preguntas")) {
            mvc.perform(put(base + "/respuestas/" + p.get("id").asLong())
                            .header("Authorization", "Bearer " + token.get(quien))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"texto\":\"En marzo cuadré un descuadre de 1.200 soles\"}"))
                    .andExpect(status().isOk());
        }
        mvc.perform(post(base + "/entrega").header("Authorization", "Bearer " + token.get(quien)))
                .andExpect(status().isOk());
    }

    /** Pulsa el botón de WhatsApp del portal y escribe al bot desde su número. */
    private void vincularA(String quien) throws Exception {
        conToken(post("/api/v1/portal/postulaciones/" + codigo.get(quien) + "/whatsapp"),
                token.get(quien), null).andExpect(status().isOk());
        String cuerpo = "{\"telefono\":\"%s\",\"wamid\":\"w-%s\",\"texto\":\"hola\"}"
                .formatted(telefono.get(quien), quien);
        String ruta = "/api/v1/integraciones/whatsapp/vinculos";
        String marca = String.valueOf(Instant.now().getEpochSecond());
        mvc.perform(post(ruta).contentType(MediaType.APPLICATION_JSON).content(cuerpo)
                        .header(FirmaWhatsapp.CABECERA_MARCA, marca)
                        .header(FirmaWhatsapp.CABECERA_FIRMA, FirmaWhatsapp.firmar(SECRETO_DEL_BOT, marca,
                                ruta, cuerpo.getBytes(StandardCharsets.UTF_8))))
                .andExpect(status().isOk());
    }

    private String estado(String quien) {
        return jdbc.queryForObject("select estado_codigo from postulacion where id = ?", String.class,
                postulacion.get(quien));
    }

    private String estadoDeSuEvaluacion(String quien) {
        return jdbc.queryForObject("select e.estado from evaluacion e join postulacion p "
                + "on p.evaluacion_id = e.id where p.id = ?", String.class, postulacion.get(quien));
    }

    private long usuarioDe(String quien) {
        return jdbc.queryForObject("select usuario_id from postulacion where id = ?", Long.class,
                postulacion.get(quien));
    }

    private List<String> correos(String quien) {
        return jdbc.queryForList("select plantilla_correo_codigo from correo_enviado where usuario_id = ? "
                + "order by id", String.class, usuarioDe(quien));
    }

    private List<String> avisos(String quien) {
        return jdbc.queryForList("select tipo from aviso_portal where usuario_id = ? order by id",
                String.class, usuarioDe(quien));
    }

    private int contar(String sql) {
        return jdbc.queryForObject(sql, Integer.class);
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

    private String leer(String cuerpoRespuesta, String campo) throws Exception {
        return json.readTree(cuerpoRespuesta).get(campo).asText();
    }
}
