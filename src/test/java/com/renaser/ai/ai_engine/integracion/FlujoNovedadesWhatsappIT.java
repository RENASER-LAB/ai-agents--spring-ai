package com.renaser.ai.ai_engine.integracion;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.renaser.ai.ai_engine.integracion.soporte.ImagenesDeContenedores;
import com.renaser.ai.ai_engine.postulacion.repository.PostulacionRepository;
import com.renaser.ai.ai_engine.postulacion.service.MaquinaEstados;
import com.renaser.ai.ai_engine.whatsapp.service.ClienteConversacion;
import com.renaser.ai.ai_engine.whatsapp.service.EnvioAlBot;
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
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Las novedades de la postulación por WhatsApp, de punta a punta en EX
 * (specs/novedades-por-whatsapp.md, V73), con el bot falso en memoria.
 *
 * <p>Una vacante de RENASER con la beta encendida y varias candidatas esperando en «Estamos
 * calificando tus respuestas»: el celular al crear la cuenta, la tarjeta, el botón y su anuncio,
 * la puerta firmada del bot, el aviso de la prueba que sale una sola vez, «Dejar de recibir»,
 * cambiar el celular, la baja y el borrado de datos.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@Testcontainers
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@DisplayName("Las novedades por WhatsApp, de punta a punta")
class FlujoNovedadesWhatsappIT {

    private static final String SECRETO_DEL_BOT = "secreto-de-pruebas-desde-el-bot-de-whatsapp";

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
        // El sondeo lo corre la prueba a mano: así sabe cuándo ha salido cada cosa.
        registro.add("app.whatsapp.sondeo-ms", () -> "3600000");
    }

    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired MaquinaEstados maquina;
    @Autowired PostulacionRepository postulacionesRepo;
    @Autowired TransactionTemplate enUnaTransaccion;
    @Autowired ClienteConversacion cliente;
    @Autowired EnvioAlBot envio;
    final ObjectMapper json = new ObjectMapper();

    static String tokenTalento;
    static String tokenDireccion;
    static long vacante;
    static final Map<String, String> token = new HashMap<>();
    static final Map<String, String> codigo = new HashMap<>();
    static final Map<String, Long> postulacion = new HashMap<>();

    private ClienteConversacionFalso bot() {
        return (ClienteConversacionFalso) cliente;
    }

    // ============ La vacante y la beta ============

    @Test
    @Order(1)
    @DisplayName("Una vacante de RENASER publicada, y la beta encendida")
    void laVacante() throws Exception {
        tokenTalento = leer(mvc.perform(post("/api/v1/panel/auth/dev-login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"usuarioRenaserOsId\":\"dev-talento\"}"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString(), "token");
        jdbc.update("INSERT INTO area (organizacion_id, nombre, es_activa) VALUES (1, 'Contabilidad', true)");
        long areaId = jdbc.queryForObject("SELECT id FROM area ORDER BY id DESC LIMIT 1", Long.class);
        long puestoId = Long.parseLong(leer(conToken(post("/api/v1/panel/puestos"), tokenTalento, """
                {"codigo": "ASISTENTE_CONTABLE_V73", "nombre": "Asistente Contable",
                 "nivelPuestoCodigo": "EJECUCION", "familiaCodigo": "OPERACIONES"}""")
                .andReturn().getResponse().getContentAsString(), "id"));
        long solicitudId = Long.parseLong(leer(conToken(post("/api/v1/panel/solicitudes"), tokenTalento, """
                {"areaId": %d, "puestoId": %d, "urgencia": "NORMAL",
                 "nivelPuestoCodigo": "EJECUCION", "familiaCodigo": "OPERACIONES",
                 "resultadoPrincipal": "Cierres al día", "motivo": "Se atrasan",
                 "consecuenciaNoContratar": "Multas", "analisisCapacidad": "No alcanza",
                 "responsableUsuarioId": 1,
                 "resultadosEsperados": [
                   {"descripcion": "Cierres", "indicador": "antes del día 5"},
                   {"descripcion": "Arqueos", "indicador": "semanales"},
                   {"descripcion": "Reportes", "indicador": "mensuales"}]}""".formatted(areaId, puestoId))
                .andReturn().getResponse().getContentAsString(), "id"));
        conToken(post("/api/v1/panel/solicitudes/" + solicitudId + "/aprobacion"), tokenTalento,
                "{\"motivo\":\"Aprobada\"}").andExpect(status().isOk());
        vacante = Long.parseLong(leer(conToken(post("/api/v1/panel/vacantes"), tokenTalento, """
                {"solicitudTalentoId": %d, "puestoId": %d, "titulo": "Asistente Contable",
                 "descripcion": "Lleva la contabilidad", "tipoCierre": "PERMANENTE",
                 "responsableUsuarioId": 1}""".formatted(solicitudId, puestoId))
                .andReturn().getResponse().getContentAsString(), "id"));
        // Publicar exige sus preguntas propias publicadas; con ellas, quien postula tiene una
        // evaluación y después espera en «Estamos calificando tus respuestas».
        String preguntas = "/api/v1/panel/vacantes/" + vacante + "/preguntas-propias";
        conToken(post(preguntas + "/preguntas"), tokenTalento, """
                {"tipo":"ABIERTA","enunciado":"Cuéntanos un cierre contable difícil",
                 "puntos":100,"queDebeTener":"El monto, la causa y el plazo"}""")
                .andExpect(status().isOk());
        conToken(post(preguntas + "/publicacion"), tokenTalento, null).andExpect(status().isOk());
        // Y su prueba del puesto, publicada: también la exige publicar la vacante.
        String prueba = "/api/v1/panel/vacantes/" + vacante + "/prueba-propia";
        conToken(put(prueba + "/borrador"), tokenTalento, """
                {"guiaCalificacion":"Mira las cifras","enunciado":"El cierre de marzo no cuadra.",
                 "modalidad":"CRONOMETRADA","duracionMinutos":90}""").andExpect(status().isOk());
        long criterio = respuesta(conToken(post(prueba + "/criterios"), tokenTalento, """
                {"nombre":"Cierre contable","queEvalua":"Conciliaciones","puntos":100,
                 "calificador":"IA"}""")).at("/borrador/criterios/0/id").asLong();
        conToken(post(prueba + "/preguntas"), tokenTalento, """
                {"tipo":"ABIERTA","enunciado":"¿Cómo hallarías el descuadre?","criterioId":%d,
                 "queDebeTener":"La cuenta y el monto"}""".formatted(criterio)).andExpect(status().isOk());
        conToken(put(prueba + "/fecha-limite"), tokenTalento, "{\"cierraEn\":\"%s\"}".formatted(
                Instant.now().plus(java.time.Duration.ofDays(10)).truncatedTo(java.time.temporal.ChronoUnit.SECONDS)))
                .andExpect(status().isOk());
        conToken(post(prueba + "/publicacion"), tokenTalento, null).andExpect(status().isOk());
        conToken(post("/api/v1/panel/vacantes/" + vacante + "/publicacion"), tokenTalento, null)
                .andExpect(status().isOk());
        jdbc.update("update vacante set calificacion_automatica = false where id = ?", vacante);

        // La V73 deja la beta apagada; aquí se enciende como lo haría Renaser desde el panel.
        assertThat(jdbc.queryForObject("""
                select valor from parametro p join organizacion o on o.id = p.organizacion_id
                 where o.es_plataforma and p.codigo = 'whatsapp_novedades_activo'""", String.class))
                .isEqualTo("false");
        // El primer usuario del dev-login nace con Talento, Dirección y Administración: le sirve
        // también para el parámetro y para ejecutar el borrado.
        tokenDireccion = tokenTalento;
        conToken(put("/api/v1/panel/parametros/whatsapp_novedades_activo"), tokenDireccion,
                "{\"valor\":\"true\",\"motivo\":\"El número del bot ya atiende desde el servicio\"}")
                .andExpect(status().is2xxSuccessful());
    }

    // ============ El celular de la cuenta ============

    @Test
    @Order(2)
    @DisplayName("AC-01 · sin celular o con uno que no vale no hay cuenta; con uno válido queda en E.164")
    void elCelularAlCrearLaCuenta() throws Exception {
        mvc.perform(post("/api/v1/portal/cuentas").contentType(MediaType.APPLICATION_JSON).content("""
                        {"nombre":"Sin","apellidos":"Celular","correo":"sin.celular@correo.pe",
                         "contrasena":"unaClaveLarga123","ciudadUbigeo":"1501","aceptaPlataforma":true}"""))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/api/v1/portal/cuentas").contentType(MediaType.APPLICATION_JSON).content("""
                        {"nombre":"Mal","apellidos":"Celular","correo":"mal.celular@correo.pe",
                         "telefono":"812 345 678","contrasena":"unaClaveLarga123","ciudadUbigeo":"1501",
                         "aceptaPlataforma":true}"""))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value(
                        "Escribe un celular de 9 cifras que empiece por 9, o el número completo con su prefijo"));
        assertThat(contar("select count(*) from usuario where correo in "
                + "('sin.celular@correo.pe', 'mal.celular@correo.pe')")).isZero();

        cuentaYPostula("ana", "Ana", "987 650 001");
        assertThat(jdbc.queryForObject("""
                select p.telefono from persona p join usuario u on u.persona_id = p.id
                 where u.correo = 'ana.whatsapp@correo.pe'""", String.class)).isEqualTo("+51987650001");
        conTokenGet("/api/v1/portal/auth/sesion", token.get("ana"))
                .andExpect(jsonPath("$.telefono").value("+51987650001"));
    }

    @Test
    @Order(3)
    @DisplayName("AC-16 · la cuenta nueva firma PLATAFORMA 1.2, que nombra el celular, WhatsApp y a Meta")
    void firmaLaVersionNueva() {
        Map<String, Object> firmado = jdbc.queryForMap("""
                select t.version, t.texto, t.hash, encode(digest(t.texto, 'sha256'), 'hex') as calculado
                  from consentimiento c
                  join texto_consentimiento t on t.id = c.texto_consentimiento_id
                  join persona p on p.id = c.persona_id
                  join usuario u on u.persona_id = p.id
                 where u.correo = 'ana.whatsapp@correo.pe' and t.tipo = 'PLATAFORMA'""");
        assertThat(firmado.get("version")).isEqualTo("1.2");
        assertThat((String) firmado.get("texto"))
                .contains("mi nombre, mis apellidos, mi correo, mi celular y la provincia donde vivo")
                .contains("por correo, en el portal y, si yo escribo primero, por WhatsApp desde el número de Renaser")
                // Con los proveedores, y por eso dentro del permiso del flujo transfronterizo.
                .contains("Si escribo por WhatsApp, los mensajes pasan por Meta Platforms, que puede "
                        + "tratarlos fuera del Perú. Renaser no vende mis datos")
                .doesNotContain("si decido darlos: mi teléfono");
        // El hash es el del texto que se firma, no el de un borrador anterior.
        assertThat(firmado.get("hash")).isEqualTo(firmado.get("calculado"));
    }

    // ============ La tarjeta y el botón ============

    @Test
    @Order(4)
    @DisplayName("AC-03 · esperando en una vacante de RENASER, la tarjeta sale con su número completo")
    void laTarjeta() throws Exception {
        esperando("ana");
        JsonNode suya = miPostulacion("ana");
        assertThat(suya.at("/whatsapp/disponible").asBoolean()).isTrue();
        assertThat(suya.at("/whatsapp/estado").asText()).isEqualTo("SIN_VINCULAR");
        assertThat(suya.at("/whatsapp/numero").asText()).isEqualTo("+51 987 650 001");

        // AC-04 · con la beta apagada, nada
        jdbc.update("update parametro set valor = 'false' where codigo = 'whatsapp_novedades_activo'");
        assertThat(miPostulacion("ana").at("/whatsapp/disponible").asBoolean()).isFalse();
        conToken(post("/api/v1/portal/postulaciones/" + codigo.get("ana") + "/whatsapp"),
                token.get("ana"), null)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.codigo").value("NO_DISPONIBLE"));
        jdbc.update("update parametro set valor = 'true' where codigo = 'whatsapp_novedades_activo'");
    }

    @Test
    @Order(5)
    @DisplayName("AC-06 · el botón guarda el consentimiento, anuncia el número y devuelve el enlace")
    void elBoton() throws Exception {
        JsonNode respuesta = respuesta(conToken(post("/api/v1/portal/postulaciones/" + codigo.get("ana")
                + "/whatsapp"), token.get("ana"), null));

        assertThat(respuesta.get("enlace").asText()).startsWith("https://wa.me/51928439930?text=")
                .contains(java.net.URLEncoder.encode("Hola, soy Ana Prueba. Quiero saber cómo va mi "
                        + "postulación a Asistente Contable.", StandardCharsets.UTF_8).replace("+", "%20"));
        assertThat(respuesta.at("/whatsapp/estado").asText()).isEqualTo("PENDIENTE");
        assertThat(contar("""
                select count(*) from consentimiento c join texto_consentimiento t
                  on t.id = c.texto_consentimiento_id
                 where t.tipo = 'WHATSAPP' and c.postulacion_id = %d""".formatted(postulacion.get("ana"))))
                .isEqualTo(1);
        List<ClienteConversacionFalso.Llamada> alBot = bot().registroDe("+51987650001");
        assertThat(alBot).extracting(ClienteConversacionFalso.Llamada::tipo).containsExactly("ESPERA");
        assertThat(alBot.get(0).cuerpo().get("postulacionRef").toString()).startsWith("pw_")
                .isNotEqualTo(codigo.get("ana"));
        // Lo mismo por la ruta del bot falso, que es como lo leen los E2E (firmada, y con el «+»
        // sin codificar, que llega como espacio).
        JsonNode registro = respuesta(firmadoGet("/api/v1/integraciones/whatsapp/falso/registro?telefono=51987650001"));
        assertThat(registro).hasSize(1);
        assertThat(registro.get(0).get("tipo").asText()).isEqualTo("ESPERA");
        mvc.perform(get("/api/v1/integraciones/whatsapp/falso/registro"))
                .andExpect(status().isUnauthorized());

        // Doble clic: un solo anuncio vigente y una sola firma
        conToken(post("/api/v1/portal/postulaciones/" + codigo.get("ana") + "/whatsapp"),
                token.get("ana"), null).andExpect(status().isOk());
        assertThat(contar("select count(*) from vinculo_whatsapp where postulacion_id = "
                + postulacion.get("ana"))).isEqualTo(1);
        assertThat(contar("""
                select count(*) from consentimiento c join texto_consentimiento t
                  on t.id = c.texto_consentimiento_id
                 where t.tipo = 'WHATSAPP' and c.postulacion_id = %d""".formatted(postulacion.get("ana"))))
                .isEqualTo(1);
        assertThat(miPostulacion("ana").at("/whatsapp/estado").asText()).isEqualTo("PENDIENTE");
    }

    // ============ El bot llama a EX ============

    @Test
    @Order(6)
    @DisplayName("AC-14 · sin firma, con firma mala o con una marca vieja: 401 y no vincula")
    void laPuertaFirmada() throws Exception {
        String cuerpo = "{\"telefono\":\"+51987650001\",\"wamid\":\"w1\",\"texto\":\"hola\"}";
        mvc.perform(post("/api/v1/integraciones/whatsapp/vinculos")
                        .contentType(MediaType.APPLICATION_JSON).content(cuerpo))
                .andExpect(status().isUnauthorized());
        String marca = String.valueOf(Instant.now().getEpochSecond());
        mvc.perform(post("/api/v1/integraciones/whatsapp/vinculos")
                        .contentType(MediaType.APPLICATION_JSON).content(cuerpo)
                        .header(FirmaWhatsapp.CABECERA_MARCA, marca)
                        .header(FirmaWhatsapp.CABECERA_FIRMA,
                                FirmaWhatsapp.firmar("otro-secreto-que-no-es-el-del-bot", marca,
                                        cuerpo.getBytes(StandardCharsets.UTF_8))))
                .andExpect(status().isUnauthorized());
        String vieja = String.valueOf(Instant.now().getEpochSecond() - 301);
        mvc.perform(post("/api/v1/integraciones/whatsapp/vinculos")
                        .contentType(MediaType.APPLICATION_JSON).content(cuerpo)
                        .header(FirmaWhatsapp.CABECERA_MARCA, vieja)
                        .header(FirmaWhatsapp.CABECERA_FIRMA,
                                FirmaWhatsapp.firmar(SECRETO_DEL_BOT, vieja, cuerpo.getBytes(StandardCharsets.UTF_8))))
                .andExpect(status().isUnauthorized());
        // Un token del panel tampoco abre esta puerta.
        mvc.perform(post("/api/v1/integraciones/whatsapp/vinculos")
                        .header("Authorization", "Bearer " + tokenTalento)
                        .contentType(MediaType.APPLICATION_JSON).content(cuerpo))
                .andExpect(status().isUnauthorized());

        assertThat(jdbc.queryForObject("select estado from vinculo_whatsapp where postulacion_id = ?",
                String.class, postulacion.get("ana"))).isEqualTo("PENDIENTE");
    }

    @Test
    @Order(6)
    @DisplayName("Las novedades sin número, o con uno vacío: 400 con el formato de la casa, no 500")
    void lasNovedadesSinNumero() throws Exception {
        firmadoGet("/api/v1/integraciones/whatsapp/novedades")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.type").value("https://api.renaser.com/errors/parametro-que-falta"))
                .andExpect(jsonPath("$.detail").value(org.hamcrest.Matchers.containsString("«telefono»")));
        firmadoGet("/api/v1/integraciones/whatsapp/novedades?telefono=")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.type").value("https://api.renaser.com/errors/regla-de-negocio"));
    }

    @Test
    @Order(7)
    @DisplayName("AC-07 · desde otro número: 404 SIN_ANUNCIO; desde el anunciado, diga lo que diga, queda vinculado")
    void vincular() throws Exception {
        firmado(post("/api/v1/integraciones/whatsapp/vinculos"),
                "{\"telefono\":\"+51900000000\",\"wamid\":\"w0\",\"texto\":\"hola\"}")
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.codigo").value("SIN_ANUNCIO"));

        JsonNode vinculo = respuesta(firmado(post("/api/v1/integraciones/whatsapp/vinculos"),
                "{\"telefono\":\"51987650001\",\"wamid\":\"wamid.A\",\"texto\":\"cualquier cosa\","
                        + "\"recibidoEn\":\"" + Instant.now() + "\"}"));
        assertThat(vinculo.get("nombre").asText()).isEqualTo("Ana");
        assertThat(vinculo.at("/postulaciones/0/postulacionRef").asText()).startsWith("pw_");
        assertThat(vinculo.at("/novedades/0/vacante").asText()).isEqualTo("Asistente Contable");
        // Las mismas palabras que el portal (TextosDelMomento, comparado con el contrato).
        assertThat(vinculo.at("/novedades/0/titulo").asText()).isEqualTo("Estamos calificando tus respuestas");
        assertThat(vinculo.at("/novedades/0/texto").asText())
                .isEqualTo("Se juntan tu currículum y tu evaluación en un solo perfil. Suele tardar poco.");

        // El mismo mensaje dos veces: el mismo resultado.
        firmado(post("/api/v1/integraciones/whatsapp/vinculos"),
                "{\"telefono\":\"+51987650001\",\"wamid\":\"wamid.A\",\"texto\":\"cualquier cosa\"}")
                .andExpect(status().isOk());

        JsonNode tarjeta = miPostulacion("ana").get("whatsapp");
        assertThat(tarjeta.get("estado").asText()).isEqualTo("ACTIVO");
        assertThat(tarjeta.get("numero").asText()).isEqualTo("+51 9•• ••• 001");

        JsonNode novedades = respuesta(firmadoGet("/api/v1/integraciones/whatsapp/novedades?telefono=%2B51987650001"));
        assertThat(novedades.get("novedades")).hasSize(1);
    }

    // ============ Los avisos ============

    @Test
    @Order(8)
    @DisplayName("AC-08 · al pasar a la prueba se encola un aviso y sale una sola vez, con correo y campana")
    void elAvisoDeLaPrueba() throws Exception {
        mover("ana", "PRUEBA_TURNO_CANDIDATO", true);

        assertThat(contar("select count(*) from aviso_whatsapp where postulacion_id = "
                + postulacion.get("ana"))).isEqualTo(1);
        assertThat(jdbc.queryForObject("select texto from aviso_whatsapp where postulacion_id = ?",
                String.class, postulacion.get("ana")))
                .startsWith("¡Felicidades, Ana! Tu prueba del puesto para Asistente Contable ya está disponible.");
        assertThat(contar("select count(*) from correo_enviado c join postulacion p on p.usuario_id = c.usuario_id "
                + "where p.id = " + postulacion.get("ana") + " and c.plantilla_correo_codigo = 'PRUEBA_DISPONIBLE'"))
                .isEqualTo(1);
        assertThat(contar("select count(*) from aviso_portal where postulacion_id = " + postulacion.get("ana")
                + " and tipo = 'PRUEBA_DISPONIBLE'")).isEqualTo(1);

        envio.enviarPendientes();
        envio.enviarPendientes();

        List<ClienteConversacionFalso.Llamada> avisos = bot().registroDe("+51987650001").stream()
                .filter(l -> "AVISO".equals(l.tipo())).toList();
        assertThat(avisos).hasSize(1);
        assertThat(avisos.get(0).cuerpo().get("tipo")).isEqualTo("PRUEBA_DISPONIBLE");
        assertThat(avisos.get(0).cuerpo().get("avisoId").toString())
                .isEqualTo(jdbc.queryForObject("select id::text from aviso_whatsapp where postulacion_id = ?",
                        String.class, postulacion.get("ana")));
        assertThat(jdbc.queryForObject("select estado from aviso_whatsapp where postulacion_id = ?",
                String.class, postulacion.get("ana"))).isEqualTo("ENVIADO");
    }

    @Test
    @Order(9)
    @DisplayName("AC-09 · quien pulsó pero nunca escribió no recibe nada al pasar a la prueba")
    void sinEscribirNoHayAviso() throws Exception {
        cuentaYPostula("bea", "Bea", "987 650 002");
        esperando("bea");
        conToken(post("/api/v1/portal/postulaciones/" + codigo.get("bea") + "/whatsapp"),
                token.get("bea"), null).andExpect(status().isOk());

        mover("bea", "PRUEBA_TURNO_CANDIDATO", true);
        envio.enviarPendientes();

        assertThat(contar("select count(*) from aviso_whatsapp where postulacion_id = "
                + postulacion.get("bea"))).isZero();
        assertThat(bot().registroDe("+51987650002")).extracting(ClienteConversacionFalso.Llamada::tipo)
                .containsExactly("ESPERA");
    }

    @Test
    @Order(10)
    @DisplayName("AC-02 · cambiar el celular cierra el anuncio del anterior y se lo cuenta al bot")
    void cambiarElCelular() throws Exception {
        JsonNode celular = respuesta(conToken(put("/api/v1/portal/cuenta/telefono"), token.get("bea"),
                "{\"telefono\":\"912 650 002\"}"));
        assertThat(celular.get("telefono").asText()).isEqualTo("+51912650002");
        assertThat(celular.get("paraMostrar").asText()).isEqualTo("+51 912 650 002");

        assertThat(jdbc.queryForObject("""
                select estado || ' ' || motivo_cierre from vinculo_whatsapp where postulacion_id = ?""",
                String.class, postulacion.get("bea"))).isEqualTo("REVOCADO CAMBIO_DE_CELULAR");
        List<ClienteConversacionFalso.Llamada> alBot = bot().registroDe("+51987650002");
        assertThat(alBot).extracting(ClienteConversacionFalso.Llamada::tipo).containsExactly("ESPERA", "REVOCACION");
        assertThat(alBot.get(1).cuerpo().get("motivo")).isEqualTo("CAMBIO_DE_CELULAR");

        conToken(put("/api/v1/portal/cuenta/telefono"), token.get("bea"), "{\"telefono\":\"54 123456\"}")
                .andExpect(status().isBadRequest());
    }

    @Test
    @Order(11)
    @DisplayName("AC-12 · «Dejar de recibir» cierra el vínculo y le llega la revocación al bot")
    void dejarDeRecibir() throws Exception {
        JsonNode tarjeta = respuesta(mvc.perform(delete("/api/v1/portal/postulaciones/" + codigo.get("ana")
                + "/whatsapp").header("Authorization", "Bearer " + token.get("ana"))));
        assertThat(tarjeta.get("disponible").asBoolean()).isTrue();
        assertThat(tarjeta.get("estado").asText()).isEqualTo("REVOCADO");

        assertThat(bot().registroDe("+51987650001").stream()
                .filter(l -> "REVOCACION".equals(l.tipo())).map(l -> l.cuerpo().get("motivo")).toList())
                .containsExactly("DEJAR_DE_RECIBIR");
        JsonNode novedades = respuesta(firmadoGet("/api/v1/integraciones/whatsapp/novedades?telefono=51987650001"));
        assertThat(novedades.get("novedades")).isEmpty();
    }

    @Test
    @Order(12)
    @DisplayName("AC-10/AC-12 · un proceso que termina cierra su vínculo sin avisar por WhatsApp")
    void alTerminar() throws Exception {
        cuentaYPostula("carla", "Carla", "987 650 003");
        esperando("carla");
        vincularA("carla", "+51987650003");

        mover("carla", "NO_CONTINUA", true);
        envio.enviarPendientes();

        assertThat(contar("select count(*) from aviso_whatsapp where postulacion_id = "
                + postulacion.get("carla"))).isZero();
        assertThat(jdbc.queryForObject("""
                select estado || ' ' || motivo_cierre from vinculo_whatsapp where postulacion_id = ?""",
                String.class, postulacion.get("carla"))).isEqualTo("REVOCADO PROCESO_TERMINADO");
        assertThat(bot().registroDe("+51987650003").stream().map(ClienteConversacionFalso.Llamada::tipo)
                .toList()).containsExactly("ESPERA", "REVOCACION").doesNotContain("AVISO");
    }

    @Test
    @Order(13)
    @DisplayName("AC-13 · la baja del bot desvincula todo lo del número y el portal lo dice")
    void laBaja() throws Exception {
        cuentaYPostula("dora", "Dora", "987 650 004");
        esperando("dora");
        vincularA("dora", "+51987650004");

        JsonNode baja = respuesta(firmado(post("/api/v1/integraciones/whatsapp/bajas"),
                "{\"telefono\":\"+51987650004\"}"));
        assertThat(baja.get("desvinculadas").asInt()).isEqualTo(1);

        JsonNode tarjeta = miPostulacion("dora").get("whatsapp");
        assertThat(tarjeta.get("estado").asText()).isEqualTo("SUPRIMIDO");
        conToken(post("/api/v1/portal/postulaciones/" + codigo.get("dora") + "/whatsapp"),
                token.get("dora"), null)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.codigo").value("SUPRIMIDO"));
    }

    @Test
    @Order(14)
    @DisplayName("AC-15 · el borrado de datos se lleva su celular, sus vínculos y la baja de su número")
    void elBorrado() throws Exception {
        long personaId = jdbc.queryForObject("""
                select u.persona_id from usuario u where u.correo = 'dora.whatsapp@correo.pe'""", Long.class);
        conToken(post("/api/v1/portal/solicitudes-borrado"), token.get("dora"),
                "{\"motivo\":\"Ya no quiero participar\"}").andExpect(status().isCreated());
        long solicitudId = jdbc.queryForObject("select max(id) from solicitud_borrado", Long.class);
        conToken(post("/api/v1/panel/solicitudes-borrado/" + solicitudId + "/ejecucion"), tokenDireccion,
                "{\"motivo\":\"Lo pidió el titular\"}").andExpect(status().isOk());

        assertThat(jdbc.queryForObject("select telefono from persona where id = ?", String.class, personaId))
                .isNull();
        assertThat(contar("select count(*) from vinculo_whatsapp where persona_id = " + personaId)).isZero();
        assertThat(contar("select count(*) from whatsapp_baja where telefono = '+51987650004'")).isZero();
    }

    // ============ ayudas ============

    private void cuentaYPostula(String quien, String nombre, String telefono) throws Exception {
        String correo = quien + ".whatsapp@correo.pe";
        mvc.perform(post("/api/v1/portal/cuentas").contentType(MediaType.APPLICATION_JSON).content("""
                        {"nombre":"%s","apellidos":"Prueba","correo":"%s","telefono":"%s",
                         "contrasena":"unaClaveLarga123","ciudadUbigeo":"1501","aceptaPlataforma":true,
                         "aceptaFuturosContactos":false}""".formatted(nombre, correo, telefono)))
                .andExpect(status().isCreated());
        String suToken = leer(mvc.perform(post("/api/v1/portal/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"correo\":\"%s\",\"contrasena\":\"unaClaveLarga123\"}".formatted(correo)))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString(), "token");
        token.put(quien, suToken);
        String suCodigo = leer(mvc.perform(multipart("/api/v1/portal/postulaciones")
                        .file(new MockMultipartFile("cv", "cv.pdf", "application/pdf", "contenido".getBytes()))
                        .param("vacanteId", String.valueOf(vacante))
                        .param("resultadoOrgulloso", "Cuadré la caja de tres sedes")
                        .param("aceptaTratamiento", "true")
                        .header("Authorization", "Bearer " + suToken))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString(), "codigo");
        codigo.put(quien, suCodigo);
        postulacion.put(quien, jdbc.queryForObject("select id from postulacion where uuid = ?::uuid",
                Long.class, suCodigo));
    }

    /** La deja en «Estamos calificando tus respuestas», un momento de espera. */
    private void esperando(String quien) {
        jdbc.update("update postulacion set estado_codigo = 'PERFIL_CALIFICANDO' where id = ?",
                postulacion.get(quien));
    }

    /** Pulsa el botón y escribe al bot desde ese número. */
    private void vincularA(String quien, String numero) throws Exception {
        conToken(post("/api/v1/portal/postulaciones/" + codigo.get(quien) + "/whatsapp"),
                token.get(quien), null).andExpect(status().isOk());
        firmado(post("/api/v1/integraciones/whatsapp/vinculos"),
                "{\"telefono\":\"%s\",\"wamid\":\"w-%s\",\"texto\":\"hola\"}".formatted(numero, quien))
                .andExpect(status().isOk());
    }

    /** Como lo haría el panel o el pase automático: por la máquina de estados, en su transacción. */
    private void mover(String quien, String estado, boolean avisar) {
        enUnaTransaccion.executeWithoutResult(t -> maquina.transicionar(
                postulacionesRepo.findById(postulacion.get(quien)).orElseThrow(), estado, null,
                "Movida por la prueba", true, false, "NO_CONTINUA".equals(estado) ? "DECISION_PERSONA" : null,
                avisar));
    }

    private JsonNode miPostulacion(String quien) throws Exception {
        return respuesta(conTokenGet("/api/v1/portal/postulaciones/" + codigo.get(quien), token.get(quien)))
                .get("resumen");
    }

    private ResultActions firmado(MockHttpServletRequestBuilder peticion, String cuerpo) throws Exception {
        String marca = String.valueOf(Instant.now().getEpochSecond());
        return mvc.perform(peticion.contentType(MediaType.APPLICATION_JSON).content(cuerpo)
                .header(FirmaWhatsapp.CABECERA_MARCA, marca)
                .header(FirmaWhatsapp.CABECERA_FIRMA,
                        FirmaWhatsapp.firmar(SECRETO_DEL_BOT, marca, cuerpo.getBytes(StandardCharsets.UTF_8))));
    }

    private ResultActions firmadoGet(String ruta) throws Exception {
        String marca = String.valueOf(Instant.now().getEpochSecond());
        return mvc.perform(get(java.net.URI.create(ruta))
                .header(FirmaWhatsapp.CABECERA_MARCA, marca)
                .header(FirmaWhatsapp.CABECERA_FIRMA, FirmaWhatsapp.firmar(SECRETO_DEL_BOT, marca, new byte[0])));
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
