package com.renaser.ai.ai_engine.integracion;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.renaser.ai.ai_engine.integracion.soporte.ImagenesDeContenedores;
import com.renaser.ai.ai_engine.postulacion.repository.PostulacionRepository;
import com.renaser.ai.ai_engine.postulacion.service.MaquinaEstados;
import com.renaser.ai.ai_engine.whatsapp.service.ClienteConversacion;
import com.renaser.ai.ai_engine.whatsapp.service.FirmaWhatsapp;
import com.renaser.ai.ai_engine.whatsapp.service.impl.ClienteConversacionFalso;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.dao.DataIntegrityViolationException;
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
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Notificaciones del candidato, de punta a punta en EX (V92,
 * specs/Ready/notificaciones-del-candidato/spec.md), con el bot falso en memoria.
 *
 * <p>Lucía postula a dos vacantes de RENASER: el mensaje flotante, encender WhatsApp con mensaje,
 * la postulación nueva que se vincula sola, apagarlo y volver con un clic, la BAJA y la vuelta,
 * el bot caído, el correo y la campana apagados, el número de otra cuenta y el cambio de número.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@Testcontainers
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@DisplayName("Notificaciones del candidato, de punta a punta")
class FlujoNotificacionesDelCandidatoIT {

    private static final String SECRETO_DEL_BOT = "secreto-de-pruebas-desde-el-bot-de-whatsapp";
    private static final String LUCIA = "+51987640001";

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
        // El sondeo lo corre la prueba a mano: así sabe cuándo ha salido cada cosa.
        registro.add("app.whatsapp.sondeo-ms", () -> "3600000");
    }

    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired MaquinaEstados maquina;
    @Autowired PostulacionRepository postulacionesRepo;
    @Autowired TransactionTemplate enUnaTransaccion;
    @Autowired ClienteConversacion cliente;
    final ObjectMapper json = new ObjectMapper();

    static String tokenTalento;
    static long vacanteA;
    static long vacanteB;
    static final Map<String, String> token = new HashMap<>();
    static final Map<String, Long> postulacion = new HashMap<>();

    private ClienteConversacionFalso bot() {
        return (ClienteConversacionFalso) cliente;
    }

    // ============ Lo que trae la V92 ============

    @Test
    @Order(1)
    @DisplayName("La V92: el texto WHATSAPP_CUENTA 1.0 tal cual se pinta, y un celular por cuenta en la base")
    void laMigracion() throws Exception {
        assertThat(jdbc.queryForObject("""
                select t.texto from texto_consentimiento t join organizacion o on o.id = t.organizacion_id
                 where o.es_plataforma and t.tipo = 'WHATSAPP_CUENTA' and t.version = '1.0'""", String.class))
                .isEqualTo("Al activarlo aceptas recibir por WhatsApp las novedades de tus procesos. "
                        + "Escribe BAJA cuando quieras dejarlo.");
        jdbc.update("insert into persona (nombre, telefono) values ('Uno', '+51987649999')");
        assertThatThrownBy(() -> jdbc.update("insert into persona (nombre, telefono) values ('Dos', '+51987649999')"))
                .isInstanceOf(DataIntegrityViolationException.class);
        // Sin número no cuenta: las cuentas viejas y las borradas no tienen celular.
        jdbc.update("insert into persona (nombre) values ('Tres')");
        jdbc.update("insert into persona (nombre) values ('Cuatro')");

        tokenTalento = leer(mvc.perform(post("/api/v1/panel/auth/dev-login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"usuarioRenaserOsId\":\"dev-talento\"}"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString(), "token");
        vacanteA = publicarVacante("ANALISTA_DATOS_V92", "Analista de Datos");
        vacanteB = publicarVacante("ANALISTA_PLANEAMIENTO_V92", "Analista de Planeamiento");
        conToken(put("/api/v1/panel/parametros/whatsapp_novedades_activo"), tokenTalento,
                "{\"valor\":\"true\",\"motivo\":\"El número del bot ya atiende desde el servicio\"}")
                .andExpect(status().is2xxSuccessful());
    }

    // ============ Un número, una cuenta ============

    @Test
    @Order(2)
    @DisplayName("AC-11 · un celular que ya usa otra cuenta: 409 TELEFONO_EN_USO al crear la cuenta y en «Mi cuenta»")
    void unNumeroPorCuenta() throws Exception {
        crearCuenta("lucia", "Lucía", "987 640 001").andExpect(status().isCreated());
        crearCuenta("otra", "Otra", "987 640 001")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.codigo").value("TELEFONO_EN_USO"))
                .andExpect(jsonPath("$.detail").value("Ese número ya está en otra cuenta. Usa otro o entra con esa cuenta."));
        crearCuenta("otra", "Otra", "987 640 002").andExpect(status().isCreated());
        entrar("lucia");
        entrar("otra");

        conToken(put("/api/v1/portal/cuenta/telefono"), token.get("otra"), "{\"telefono\":\"987 640 001\"}")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.codigo").value("TELEFONO_EN_USO"));
        assertThat(jdbc.queryForObject("select p.telefono from persona p join usuario u on u.persona_id = p.id "
                + "where u.correo = 'otra.v92@correo.pe'", String.class)).isEqualTo("+51987640002");
    }

    // ============ El estado y el mensaje flotante ============

    @Test
    @Order(3)
    @DisplayName("Sin procesos: WhatsApp apagado, correo y campana encendidos, y el mensaje no sale")
    void sinProcesos() throws Exception {
        JsonNode estado = notificaciones("lucia");
        assertThat(estado.at("/guiaVista").asBoolean()).isFalse();
        assertThat(estado.at("/whatsapp/disponible").asBoolean()).isTrue();
        assertThat(estado.at("/whatsapp/estado").asText()).isEqualTo("APAGADO");
        assertThat(estado.at("/whatsapp/numero").asText()).isEqualTo("+51 987 640 001");
        assertThat(estado.at("/whatsapp/comprobado").asBoolean()).isFalse();
        assertThat(estado.at("/correo/activo").asBoolean()).isTrue();
        assertThat(estado.at("/correo/destino").asText()).isEqualTo("lucia.v92@correo.pe");
        assertThat(estado.at("/portal/activo").asBoolean()).isTrue();
        assertThat(estado.at("/mensaje/mostrar").asBoolean()).isFalse();
    }

    @Test
    @Order(3)
    @DisplayName("V93 · la guía de los avisos se marca vista una sola vez: 204 siempre, y la fecha de la primera se queda")
    void laGuiaVista() throws Exception {
        conToken(post("/api/v1/portal/notificaciones/guia-vista"), token.get("otra"), null)
                .andExpect(status().isNoContent());
        String sql = "select guia_avisos_vista_en::text from preferencia_aviso p join usuario u "
                + "on u.persona_id = p.persona_id where u.correo = 'otra.v92@correo.pe'";
        String primera = jdbc.queryForObject(sql, String.class);
        assertThat(primera).isNotNull();
        assertThat(notificaciones("otra").at("/guiaVista").asBoolean()).isTrue();

        conToken(post("/api/v1/portal/notificaciones/guia-vista"), token.get("otra"), null)
                .andExpect(status().isNoContent());
        assertThat(jdbc.queryForObject(sql, String.class)).isEqualTo(primera);
        // Lo de otra cuenta no se toca.
        assertThat(notificaciones("lucia").at("/guiaVista").asBoolean()).isFalse();
    }

    @Test
    @Order(4)
    @DisplayName("AC-01 · tras postular sale «Recibe tus avisos por WhatsApp»; contado al pintarse, no vuelve sin otra postulación")
    void elMensajeTrasPostular() throws Exception {
        postular("lucia", vacanteA, "a");

        JsonNode estado = notificaciones("lucia");
        assertThat(estado.at("/mensaje/mostrar").asBoolean()).isTrue();
        assertThat(estado.at("/mensaje/situacion").asText()).isEqualTo("PRIMERA_VEZ");

        conToken(post("/api/v1/portal/notificaciones/mensaje-visto"), token.get("lucia"), null)
                .andExpect(status().isNoContent());
        assertThat(contar("select mensaje_vistas from preferencia_aviso p join usuario u on u.persona_id = p.persona_id "
                + "where u.correo = 'lucia.v92@correo.pe'")).isEqualTo(1);
        assertThat(notificaciones("lucia").at("/mensaje/mostrar").asBoolean()).isFalse();
    }

    // ============ Encender con mensaje ============

    @Test
    @Order(5)
    @DisplayName("AC-02 · sin comprobar: firma WHATSAPP_CUENTA, un vínculo PENDIENTE por proceso vivo y el enlace al chat")
    void encenderConMensaje() throws Exception {
        JsonNode hecho = respuesta(conToken(post("/api/v1/portal/notificaciones/whatsapp"), token.get("lucia"), null));

        assertThat(hecho.get("resultado").asText()).isEqualTo("POR_MENSAJE");
        assertThat(hecho.get("enlace").asText()).startsWith("https://wa.me/");
        assertThat(hecho.at("/notificaciones/whatsapp/estado").asText()).isEqualTo("ESPERANDO");
        assertThat(contar("""
                select count(*) from consentimiento c join texto_consentimiento t on t.id = c.texto_consentimiento_id
                 where t.tipo = 'WHATSAPP_CUENTA' and c.postulacion_id is null and c.persona_id =
                       (select persona_id from usuario where correo = 'lucia.v92@correo.pe')""")).isEqualTo(1);
        assertThat(jdbc.queryForObject("select estado from vinculo_whatsapp where postulacion_id = ?", String.class,
                postulacion.get("a"))).isEqualTo("PENDIENTE");
        // El anuncio salió al bot con el consentimiento como lo entiende su contrato: WHATSAPP.
        assertThat(bot().registroDe(LUCIA)).filteredOn(l -> "ESPERA".equals(l.tipo())).singleElement()
                .satisfies(l -> assertThat(((Map<?, ?>) l.cuerpo().get("consentimiento")).get("tipo"))
                        .isEqualTo("WHATSAPP"));

        // Doble clic: la misma firma, el mismo vínculo renovado.
        respuesta(conToken(post("/api/v1/portal/notificaciones/whatsapp"), token.get("lucia"), null));
        assertThat(contar("select count(*) from consentimiento c join texto_consentimiento t "
                + "on t.id = c.texto_consentimiento_id where t.tipo = 'WHATSAPP_CUENTA'")).isEqualTo(1);
        assertThat(contar("select count(*) from vinculo_whatsapp where postulacion_id = " + postulacion.get("a")))
                .isEqualTo(1);
    }

    @Test
    @Order(6)
    @DisplayName("AC-03 · cuando escribe al bot, sus procesos quedan ACTIVO y el botón dice «WhatsApp activo»")
    void escribeYQuedaActivo() throws Exception {
        firmado("/api/v1/integraciones/whatsapp/vinculos",
                "{\"telefono\":\"" + LUCIA + "\",\"wamid\":\"w-lucia\",\"texto\":\"hola\"}")
                .andExpect(status().isOk());

        assertThat(jdbc.queryForObject("select estado from vinculo_whatsapp where postulacion_id = ?", String.class,
                postulacion.get("a"))).isEqualTo("ACTIVO");
        JsonNode estado = notificaciones("lucia");
        assertThat(estado.at("/whatsapp/estado").asText()).isEqualTo("ACTIVO");
        assertThat(estado.at("/whatsapp/numero").asText()).isEqualTo("+51 9•• ••• 001");
        assertThat(estado.at("/whatsapp/comprobado").asBoolean()).isTrue();
        assertThat(estado.at("/mensaje/mostrar").asBoolean()).isFalse();
    }

    @Test
    @Order(7)
    @DisplayName("AC-06 · con WhatsApp activo, la postulación nueva queda vinculada sola, con NUEVA_POSTULACION")
    void laNuevaSeVinculaSola() throws Exception {
        postular("lucia", vacanteB, "b");

        assertThat(jdbc.queryForObject("select estado from vinculo_whatsapp where postulacion_id = ?", String.class,
                postulacion.get("b"))).isEqualTo("ACTIVO");
        assertThat(bot().registroDe(LUCIA)).filteredOn(l -> "VINCULACION".equals(l.tipo())).singleElement()
                .satisfies(l -> assertThat(l.cuerpo().get("motivo")).isEqualTo("NUEVA_POSTULACION"));
    }

    // ============ Apagar y volver con un clic ============

    @Test
    @Order(8)
    @DisplayName("AC-07 · apagarlo cierra todos sus vínculos con DEJAR_DE_RECIBIR; volver a encenderlo es un clic")
    void apagarYVolver() throws Exception {
        JsonNode apagado = respuesta(conToken(delete("/api/v1/portal/notificaciones/whatsapp"), token.get("lucia"), null));
        assertThat(apagado.at("/whatsapp/estado").asText()).isEqualTo("APAGADO");
        assertThat(jdbc.queryForList("select motivo_cierre from vinculo_whatsapp where persona_id = "
                + "(select persona_id from usuario where correo = 'lucia.v92@correo.pe')", String.class))
                .containsOnly("DEJAR_DE_RECIBIR");

        JsonNode hecho = respuesta(conToken(post("/api/v1/portal/notificaciones/whatsapp"), token.get("lucia"), null));
        assertThat(hecho.get("resultado").asText()).isEqualTo("ACTIVADO");
        assertThat(hecho.get("confirmacionEnviada").asBoolean()).isTrue();
        assertThat(hecho.at("/notificaciones/whatsapp/estado").asText()).isEqualTo("ACTIVO");
        assertThat(activosDe("lucia")).isEqualTo(2);
    }

    // ============ La BAJA ============

    @Test
    @Order(9)
    @DisplayName("AC-05 · tras la BAJA: «apagado por BAJA»; encender llama con ACTIVACION, sale de la baja y sin abrir el chat")
    void laBajaYLaVuelta() throws Exception {
        firmado("/api/v1/integraciones/whatsapp/bajas", "{\"telefono\":\"" + LUCIA + "\"}").andExpect(status().isNoContent());
        JsonNode tras = notificaciones("lucia");
        assertThat(tras.at("/whatsapp/estado").asText()).isEqualTo("APAGADO");
        assertThat(tras.at("/whatsapp/porBaja").asBoolean()).isTrue();
        assertThat(tras.at("/whatsapp/bajaEn").asText()).matches("\\d{4}-\\d{2}-\\d{2}");
        // AC-10 · la BAJA reinicia el contador, y postuló a B después de verlo: sale el de volver.
        assertThat(tras.at("/mensaje/mostrar").asBoolean()).isTrue();
        assertThat(tras.at("/mensaje/situacion").asText()).isEqualTo("TRAS_BAJA");

        JsonNode hecho = respuesta(conToken(post("/api/v1/portal/notificaciones/whatsapp"), token.get("lucia"), null));

        assertThat(hecho.get("resultado").asText()).isEqualTo("ACTIVADO");
        assertThat(hecho.get("enlace").isNull()).isTrue();
        assertThat(contar("select count(*) from whatsapp_baja where telefono = '" + LUCIA + "'")).isZero();
        assertThat(activosDe("lucia")).isEqualTo(2);
        assertThat(bot().registroDe(LUCIA)).filteredOn(l -> "VINCULACION".equals(l.tipo()))
                .extracting(l -> l.cuerpo().get("motivo")).last().isEqualTo("ACTIVACION");
    }

    @Test
    @Order(10)
    @DisplayName("AC-13 · si el bot no contesta: 503, sigue en la baja y no se crea ningún vínculo")
    void elBotCaido() throws Exception {
        firmado("/api/v1/integraciones/whatsapp/bajas", "{\"telefono\":\"" + LUCIA + "\"}").andExpect(status().isNoContent());
        int vinculosAntes = contar("select count(*) from vinculo_whatsapp");
        bot().fallar(true);
        try {
            conToken(post("/api/v1/portal/notificaciones/whatsapp"), token.get("lucia"), null)
                    .andExpect(status().isServiceUnavailable())
                    .andExpect(jsonPath("$.codigo").value("NO_SE_PUDO_ACTIVAR"));
        } finally {
            bot().fallar(false);
        }
        assertThat(contar("select count(*) from whatsapp_baja where telefono = '" + LUCIA + "'")).isEqualTo(1);
        assertThat(contar("select count(*) from vinculo_whatsapp")).isEqualTo(vinculosAntes);
        assertThat(notificaciones("lucia").at("/whatsapp/estado").asText()).isEqualTo("APAGADO");

        // Ya contesta: se activa.
        respuesta(conToken(post("/api/v1/portal/notificaciones/whatsapp"), token.get("lucia"), null));
        assertThat(activosDe("lucia")).isEqualTo(2);
    }

    // ============ Correo y campana ============

    @Test
    @Order(11)
    @DisplayName("AC-08 y AC-09 · con correo y campana apagados, la etapa no manda correo ni marca la campana, pero sí WhatsApp")
    void correoYCampanaApagados() throws Exception {
        JsonNode cambiado = respuesta(conToken(patch("/api/v1/portal/notificaciones"), token.get("lucia"),
                "{\"correo\":false,\"portal\":false}"));
        assertThat(cambiado.at("/correo/activo").asBoolean()).isFalse();
        assertThat(cambiado.at("/portal/activo").asBoolean()).isFalse();

        jdbc.update("update postulacion set estado_codigo = 'PERFIL_CALIFICANDO' where id = ?", postulacion.get("a"));
        enUnaTransaccion.executeWithoutResult(t -> maquina.transicionar(
                postulacionesRepo.findById(postulacion.get("a")).orElseThrow(), "PRUEBA_TURNO_CANDIDATO", null,
                "Movida por la prueba", true, false, null, true));

        assertThat(contar("select count(*) from correo_enviado c join postulacion p on p.usuario_id = c.usuario_id "
                + "where p.id = " + postulacion.get("a") + " and c.plantilla_correo_codigo = 'PRUEBA_DISPONIBLE'"))
                .isZero();
        assertThat(contar("select count(*) from aviso_portal where postulacion_id = " + postulacion.get("a")
                + " and tipo = 'PRUEBA_DISPONIBLE'")).isZero();
        assertThat(contar("select count(*) from aviso_whatsapp where postulacion_id = " + postulacion.get("a")))
                .isEqualTo(1);
        // El proceso enseña su estado nuevo igual.
        assertThat(respuesta(conTokenGet("/api/v1/portal/postulaciones/" + uuidDe("a"), token.get("lucia")))
                .at("/resumen/estado").asText()).isEqualTo("PRUEBA_TURNO_CANDIDATO");

        // Y volver a encenderlos.
        respuesta(conToken(patch("/api/v1/portal/notificaciones"), token.get("lucia"), "{\"correo\":true,\"portal\":true}"));
        assertThat(notificaciones("lucia").at("/correo/activo").asBoolean()).isTrue();
    }

    // ============ El cambio de número ============

    @Test
    @Order(12)
    @DisplayName("AC-12 · con WhatsApp activo cambia su número: queda APAGADO con cambioDeNumero y activar abre el chat")
    void cambioDeNumero() throws Exception {
        conToken(put("/api/v1/portal/cuenta/telefono"), token.get("lucia"), "{\"telefono\":\"987 640 009\"}")
                .andExpect(status().isOk());

        JsonNode estado = notificaciones("lucia");
        assertThat(estado.at("/whatsapp/estado").asText()).isEqualTo("APAGADO");
        assertThat(estado.at("/whatsapp/cambioDeNumero").asBoolean()).isTrue();
        assertThat(estado.at("/whatsapp/comprobado").asBoolean()).isFalse();
        assertThat(estado.at("/whatsapp/numero").asText()).isEqualTo("+51 987 640 009");

        JsonNode hecho = respuesta(conToken(post("/api/v1/portal/notificaciones/whatsapp"), token.get("lucia"), null));
        assertThat(hecho.get("resultado").asText()).isEqualTo("POR_MENSAJE");
        assertThat(hecho.at("/notificaciones/whatsapp/estado").asText()).isEqualTo("ESPERANDO");
    }

    // ============ Sin procesos vivos ============

    @Test
    @Order(13)
    @DisplayName("Sin procesos vivos y sin comprobar: se anuncia el número solo; al escribir, «sin procesos activos» y queda encendido")
    void sinProcesosVivos() throws Exception {
        crearCuenta("otra2", "Otra", "987 640 003").andExpect(status().isCreated());
        entrar("otra2");

        JsonNode hecho = respuesta(conToken(post("/api/v1/portal/notificaciones/whatsapp"), token.get("otra2"), null));
        assertThat(hecho.get("resultado").asText()).isEqualTo("POR_MENSAJE");
        assertThat(contar("select count(*) from vinculo_whatsapp where telefono = '+51987640003' "
                + "and postulacion_id is null and estado = 'PENDIENTE'")).isEqualTo(1);

        firmado("/api/v1/integraciones/whatsapp/vinculos",
                "{\"telefono\":\"+51987640003\",\"wamid\":\"w-otra\",\"texto\":\"hola\"}")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.codigo").value("PROCESO_TERMINADO"));
        assertThat(notificaciones("otra2").at("/whatsapp/estado").asText()).isEqualTo("ACTIVO");
    }

    // ============ ayudas ============

    private long publicarVacante(String codigoPuesto, String titulo) throws Exception {
        jdbc.update("INSERT INTO area (organizacion_id, nombre, es_activa) VALUES (1, ?, true)", "Área " + codigoPuesto);
        long areaId = jdbc.queryForObject("SELECT id FROM area ORDER BY id DESC LIMIT 1", Long.class);
        long puestoId = Long.parseLong(leer(conToken(post("/api/v1/panel/puestos"), tokenTalento, """
                {"codigo": "%s", "nombre": "%s",
                 "nivelPuestoCodigo": "EJECUCION", "familiaCodigo": "OPERACIONES"}""".formatted(codigoPuesto, titulo))
                .andReturn().getResponse().getContentAsString(), "id"));
        long solicitudId = Long.parseLong(leer(conToken(post("/api/v1/panel/solicitudes"), tokenTalento, """
                {"areaId": %d, "puestoId": %d, "urgencia": "NORMAL",
                 "nivelPuestoCodigo": "EJECUCION", "familiaCodigo": "OPERACIONES",
                 "resultadoPrincipal": "Reportes al día", "motivo": "Se atrasan",
                 "consecuenciaNoContratar": "Decisiones a ciegas", "analisisCapacidad": "No alcanza",
                 "responsableUsuarioId": 1,
                 "resultadosEsperados": [
                   {"descripcion": "Reportes", "indicador": "semanales"},
                   {"descripcion": "Tableros", "indicador": "al día"},
                   {"descripcion": "Cierres", "indicador": "mensuales"}]}""".formatted(areaId, puestoId))
                .andReturn().getResponse().getContentAsString(), "id"));
        conToken(post("/api/v1/panel/solicitudes/" + solicitudId + "/aprobacion"), tokenTalento,
                "{\"motivo\":\"Aprobada\"}").andExpect(status().isOk());
        long vacante = Long.parseLong(leer(conToken(post("/api/v1/panel/vacantes"), tokenTalento, """
                {"solicitudTalentoId": %d, "puestoId": %d, "titulo": "%s",
                 "descripcion": "Ordena los datos", "tipoCierre": "PERMANENTE",
                 "responsableUsuarioId": 1}""".formatted(solicitudId, puestoId, titulo))
                .andReturn().getResponse().getContentAsString(), "id"));
        String preguntas = "/api/v1/panel/vacantes/" + vacante + "/preguntas-propias";
        conToken(post(preguntas + "/preguntas"), tokenTalento, """
                {"tipo":"ABIERTA","enunciado":"Cuéntanos un análisis difícil",
                 "puntos":100,"queDebeTener":"Los datos, la causa y el plazo"}""")
                .andExpect(status().isOk());
        conToken(post(preguntas + "/publicacion"), tokenTalento, null).andExpect(status().isOk());
        String prueba = "/api/v1/panel/vacantes/" + vacante + "/prueba-propia";
        conToken(put(prueba + "/borrador"), tokenTalento, """
                {"guiaCalificacion":"Mira las cifras","enunciado":"El tablero de marzo no cuadra.",
                 "modalidad":"CRONOMETRADA","duracionMinutos":90}""").andExpect(status().isOk());
        long criterio = respuesta(conToken(post(prueba + "/criterios"), tokenTalento, """
                {"nombre":"Análisis","queEvalua":"Conciliaciones","puntos":100,
                 "calificador":"IA"}""")).at("/borrador/criterios/0/id").asLong();
        conToken(post(prueba + "/preguntas"), tokenTalento, """
                {"tipo":"ABIERTA","enunciado":"¿Cómo hallarías el descuadre?","criterioId":%d,
                 "queDebeTener":"La fuente y el monto"}""".formatted(criterio)).andExpect(status().isOk());
        conToken(put(prueba + "/fecha-limite"), tokenTalento, "{\"cierraEn\":\"%s\"}".formatted(
                Instant.now().plus(Duration.ofDays(10)).truncatedTo(ChronoUnit.SECONDS)))
                .andExpect(status().isOk());
        conToken(post(prueba + "/publicacion"), tokenTalento, null).andExpect(status().isOk());
        conToken(post("/api/v1/panel/vacantes/" + vacante + "/publicacion"), tokenTalento, null)
                .andExpect(status().isOk());
        jdbc.update("update vacante set calificacion_automatica = false where id = ?", vacante);
        return vacante;
    }

    private ResultActions crearCuenta(String quien, String nombre, String telefono) throws Exception {
        return mvc.perform(post("/api/v1/portal/cuentas").contentType(MediaType.APPLICATION_JSON).content("""
                {"nombre":"%s","apellidos":"Prueba","correo":"%s.v92@correo.pe","telefono":"%s",
                 "contrasena":"unaClaveLarga123","ciudadUbigeo":"1501","aceptaPlataforma":true,
                 "aceptaFuturosContactos":false}""".formatted(nombre, quien, telefono)));
    }

    private void entrar(String quien) throws Exception {
        token.put(quien, leer(mvc.perform(post("/api/v1/portal/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"correo\":\"%s.v92@correo.pe\",\"contrasena\":\"unaClaveLarga123\"}".formatted(quien)))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString(), "token"));
    }

    private void postular(String quien, long vacante, String clave) throws Exception {
        String suCodigo = leer(mvc.perform(multipart("/api/v1/portal/postulaciones")
                        .file(new MockMultipartFile("cv", "cv.pdf", "application/pdf", "contenido".getBytes()))
                        .param("vacanteId", String.valueOf(vacante))
                        .param("resultadoOrgulloso", "Ordené los datos de tres sedes")
                        .param("aceptaTratamiento", "true")
                        .header("Authorization", "Bearer " + token.get(quien)))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString(), "codigo");
        postulacion.put(clave, jdbc.queryForObject("select id from postulacion where uuid = ?::uuid",
                Long.class, suCodigo));
    }

    private String uuidDe(String clave) {
        return jdbc.queryForObject("select uuid::text from postulacion where id = ?", String.class,
                postulacion.get(clave));
    }

    private int activosDe(String quien) {
        return contar("select count(*) from vinculo_whatsapp where estado = 'ACTIVO' and persona_id = "
                + "(select persona_id from usuario where correo = '" + quien + ".v92@correo.pe')");
    }

    private JsonNode notificaciones(String quien) throws Exception {
        return respuesta(conTokenGet("/api/v1/portal/notificaciones", token.get(quien)));
    }

    /** Un POST como lo haría el bot: firmado sobre la marca, la ruta y el cuerpo. */
    private ResultActions firmado(String ruta, String cuerpo) throws Exception {
        String marca = String.valueOf(Instant.now().getEpochSecond());
        return mvc.perform(post(ruta).contentType(MediaType.APPLICATION_JSON).content(cuerpo)
                .header(FirmaWhatsapp.CABECERA_MARCA, marca)
                .header(FirmaWhatsapp.CABECERA_FIRMA,
                        FirmaWhatsapp.firmar(SECRETO_DEL_BOT, marca, ruta, cuerpo.getBytes(StandardCharsets.UTF_8))));
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

    private ResultActions conTokenGet(String ruta, String suToken) throws Exception {
        return mvc.perform(get(ruta).header("Authorization", "Bearer " + suToken));
    }

    private String leer(String cuerpoRespuesta, String campo) throws Exception {
        return json.readTree(cuerpoRespuesta).get(campo).asText();
    }
}
