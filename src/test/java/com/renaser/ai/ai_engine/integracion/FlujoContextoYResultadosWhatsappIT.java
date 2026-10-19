package com.renaser.ai.ai_engine.integracion;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.renaser.ai.ai_engine.integracion.soporte.ImagenesDeContenedores;
import com.renaser.ai.ai_engine.postulacion.repository.PostulacionRepository;
import com.renaser.ai.ai_engine.postulacion.service.EntradaEtapaTecnica;
import com.renaser.ai.ai_engine.postulacion.service.MaquinaEstados;
import com.renaser.ai.ai_engine.vacante.repository.VacanteRepository;
import com.renaser.ai.ai_engine.whatsapp.service.ClienteConversacion;
import com.renaser.ai.ai_engine.whatsapp.service.EnvioAlBot;
import com.renaser.ai.ai_engine.whatsapp.service.EnvioDeEventos;
import com.renaser.ai.ai_engine.whatsapp.service.FirmaWhatsapp;
import com.renaser.ai.ai_engine.whatsapp.service.PropiedadesWhatsapp;
import com.renaser.ai.ai_engine.whatsapp.service.impl.ClienteConversacionFalso;

import org.junit.jupiter.api.AfterEach;
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
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * El contexto y los resultados por WhatsApp (contrato v2, V76), de punta a punta en EX con el bot
 * falso: la consulta firmada, la ficha pública, la acción con sus plazos, la convocatoria
 * cerrada, el resultado con su bandeja de salida, el bot en v1, el productor apagado, la BAJA,
 * la caducidad y el borrado.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@Testcontainers
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@DisplayName("El contexto y los resultados por WhatsApp (v2), de punta a punta")
class FlujoContextoYResultadosWhatsappIT {

    private static final String SECRETO_DEL_BOT = "secreto-de-pruebas-desde-el-bot-de-whatsapp-v2";
    private static final String CONTEXTO = "/api/v2/integraciones/whatsapp/contexto";
    private static final String MIS_PROCESOS = "https://portal.pruebas.renaser.pe/procesos";

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
        registro.add("app.whatsapp.enlace-mis-procesos", () -> MIS_PROCESOS);
        registro.add("app.whatsapp.productor-v2", () -> "true");
        // El sondeo lo corre la prueba a mano: así sabe cuándo ha salido cada cosa.
        registro.add("app.whatsapp.sondeo-ms", () -> "3600000");
    }

    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired MaquinaEstados maquina;
    @Autowired PostulacionRepository postulacionesRepo;
    @Autowired VacanteRepository vacantesRepo;
    @Autowired EntradaEtapaTecnica entradaEtapaTecnica;
    @Autowired TransactionTemplate enUnaTransaccion;
    @Autowired ClienteConversacion cliente;
    @Autowired EnvioAlBot envio;
    @Autowired EnvioDeEventos eventos;
    @Autowired PropiedadesWhatsapp propiedades;
    final ObjectMapper json = new ObjectMapper();

    static String tokenTalento;
    static long vacante;
    static final Map<String, String> token = new HashMap<>();
    static final Map<String, String> codigo = new HashMap<>();
    static final Map<String, Long> postulacion = new HashMap<>();
    static final Map<String, String> telefono = new HashMap<>();
    static final Map<String, String> referencia = new HashMap<>();

    private ClienteConversacionFalso bot() {
        return (ClienteConversacionFalso) cliente;
    }

    @AfterEach
    void comoSiempre() {
        // Cada prueba deja el productor encendido y el bot en v2, como las encontró.
        propiedades.setProductorV2(true);
        bot().responderComoVersion(2);
    }

    // ============ la vacante y las candidatas ============

    @Test
    @Order(1)
    @DisplayName("Una vacante de RENASER con su prueba cronometrada, la beta encendida y seis candidatas vinculadas")
    void laVacanteYLasCandidatas() throws Exception {
        tokenTalento = leer(mvc.perform(post("/api/v1/panel/auth/dev-login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"usuarioRenaserOsId\":\"dev-talento\"}"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString(), "token");
        jdbc.update("INSERT INTO area (organizacion_id, nombre, es_activa) VALUES (1, 'Contabilidad', true)");
        long areaId = jdbc.queryForObject("SELECT id FROM area ORDER BY id DESC LIMIT 1", Long.class);
        long puestoId = Long.parseLong(leer(conToken(post("/api/v1/panel/puestos"), tokenTalento, """
                {"codigo": "CONTADOR_V76", "nombre": "Contador",
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
                {"solicitudTalentoId": %d, "puestoId": %d, "titulo": "Contador General",
                 "descripcion": "Lleva la contabilidad de tres sedes", "tipoCierre": "PERMANENTE",
                 "responsableUsuarioId": 1}""".formatted(solicitudId, puestoId))
                .andReturn().getResponse().getContentAsString(), "id"));
        String preguntas = "/api/v1/panel/vacantes/" + vacante + "/preguntas-propias";
        conToken(post(preguntas + "/preguntas"), tokenTalento, """
                {"tipo":"ABIERTA","enunciado":"Cuéntanos un cierre contable difícil",
                 "puntos":100,"queDebeTener":"El monto, la causa y el plazo"}""")
                .andExpect(status().isOk());
        conToken(post(preguntas + "/publicacion"), tokenTalento, null).andExpect(status().isOk());
        String prueba = "/api/v1/panel/vacantes/" + vacante + "/prueba-propia";
        conToken(put(prueba + "/borrador"), tokenTalento, """
                {"guiaCalificacion":"Mira las cifras","enunciado":"EL CASO SECRETO DEL CIERRE DE MARZO",
                 "modalidad":"CRONOMETRADA","duracionMinutos":90}""").andExpect(status().isOk());
        long criterio = respuesta(conToken(post(prueba + "/criterios"), tokenTalento, """
                {"nombre":"Cierre contable","queEvalua":"Conciliaciones","puntos":100,
                 "calificador":"IA"}""")).at("/borrador/criterios/0/id").asLong();
        conToken(post(prueba + "/preguntas"), tokenTalento, """
                {"tipo":"ABIERTA","enunciado":"¿Cómo hallarías el descuadre?","criterioId":%d,
                 "queDebeTener":"La cuenta y el monto"}""".formatted(criterio)).andExpect(status().isOk());
        conToken(put(prueba + "/fecha-limite"), tokenTalento, "{\"cierraEn\":\"%s\"}".formatted(
                Instant.now().plus(Duration.ofDays(10)).truncatedTo(ChronoUnit.SECONDS)))
                .andExpect(status().isOk());
        conToken(post(prueba + "/publicacion"), tokenTalento, null).andExpect(status().isOk());
        conToken(post("/api/v1/panel/vacantes/" + vacante + "/publicacion"), tokenTalento, null)
                .andExpect(status().isOk());
        // Lo publicado de la ficha, y un sueldo antiguo en la columna que ya no se usa.
        jdbc.update("""
                update vacante set calificacion_automatica = false, horario = 'Lunes a viernes de 8:00 a 17:00',
                       ubicacion = 'Selva Alegre', responsabilidades = 'Conciliar bancos',
                       compensacion_publica = 'S/ 3 500 a 4 200', remuneracion_tipo = 'OCULTA'
                 where id = ?""", vacante);
        conToken(put("/api/v1/panel/parametros/whatsapp_novedades_activo"), tokenTalento,
                "{\"valor\":\"true\",\"motivo\":\"El número del bot ya atiende desde el servicio\"}")
                .andExpect(status().is2xxSuccessful());

        int n = 1;
        for (String quien : List.of("ana", "carla", "dora", "eva", "fer", "gina")) {
            cuentaYPostula(quien, "987 760 00" + n++);
            jdbc.update("update postulacion set estado_codigo = 'PERFIL_CALIFICANDO' where id = ?",
                    postulacion.get(quien));
            vincularA(quien);
        }
        assertThat(contar("select count(*) from vinculo_whatsapp where estado = 'ACTIVO'")).isEqualTo(6);
    }

    // ============ la consulta ============

    @Test
    @Order(2)
    @DisplayName("AC-19 · sin firma, firmada para otra ruta, con otro secreto o fuera de plazo: 401 y sin efectos")
    void laFirma() throws Exception {
        String cuerpo = "{\"telefono\":\"%s\"}".formatted(telefono.get("ana"));
        byte[] bytes = cuerpo.getBytes(StandardCharsets.UTF_8);
        String marca = String.valueOf(Instant.now().getEpochSecond());
        mvc.perform(post(CONTEXTO).contentType(MediaType.APPLICATION_JSON).content(cuerpo))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.codigo").value("FIRMA_INVALIDA"));
        // La firma de la ruta v1 de novedades no abre la v2, aunque el cuerpo sea el mismo.
        mvc.perform(post(CONTEXTO).contentType(MediaType.APPLICATION_JSON).content(cuerpo)
                        .header(FirmaWhatsapp.CABECERA_MARCA, marca)
                        .header(FirmaWhatsapp.CABECERA_FIRMA, FirmaWhatsapp.firmar(SECRETO_DEL_BOT, marca,
                                "/api/v1/integraciones/whatsapp/novedades", bytes)))
                .andExpect(status().isUnauthorized());
        mvc.perform(post(CONTEXTO).contentType(MediaType.APPLICATION_JSON).content(cuerpo)
                        .header(FirmaWhatsapp.CABECERA_MARCA, marca)
                        .header(FirmaWhatsapp.CABECERA_FIRMA,
                                FirmaWhatsapp.firmar("otro-secreto-que-no-es-el-del-bot", marca, CONTEXTO, bytes)))
                .andExpect(status().isUnauthorized());
        String vieja = String.valueOf(Instant.now().getEpochSecond() - 301);
        mvc.perform(post(CONTEXTO).contentType(MediaType.APPLICATION_JSON).content(cuerpo)
                        .header(FirmaWhatsapp.CABECERA_MARCA, vieja)
                        .header(FirmaWhatsapp.CABECERA_FIRMA, FirmaWhatsapp.firmar(SECRETO_DEL_BOT, vieja, CONTEXTO, bytes)))
                .andExpect(status().isUnauthorized());
        // Un token del panel tampoco abre esta puerta.
        mvc.perform(post(CONTEXTO).header("Authorization", "Bearer " + tokenTalento)
                        .contentType(MediaType.APPLICATION_JSON).content(cuerpo))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @Order(3)
    @DisplayName("AC-01/AC-03/AC-04/AC-05/AC-06 · el listado y el contexto de Ana: su ficha pública, sin importes ocultos")
    void elContextoDeAna() throws Exception {
        JsonNode listado = consultar(telefono.get("ana"), null, null);
        assertThat(listado.get("postulaciones")).hasSize(1);
        JsonNode resumen = listado.at("/postulaciones/0");
        referencia.put("ana", resumen.get("postulacionRef").asText());
        assertThat(referencia.get("ana")).startsWith("pw_").isNotEqualTo(codigo.get("ana"));
        assertThat(resumen.at("/vacante/titulo").asText()).isEqualTo("Contador General");
        assertThat(resumen.get("situacion").asText()).isEqualTo("ACTIVA");
        assertThat(resumen.get("resultado").isNull()).isTrue();
        assertThat(resumen.get("convocatoria").asText()).isEqualTo("PUBLICADA");
        assertThat(listado.get("cursor").isNull()).isTrue();

        JsonNode contexto = consultar(telefono.get("ana"), referencia.get("ana"), null);
        String texto = contexto.toString();
        assertThat(contexto.get("postulacionRef").asText()).isEqualTo(referencia.get("ana"));
        assertThat(contexto.at("/vacante/ref").asText()).matches("vc_[0-9a-f]{32}");
        assertThat(contexto.at("/vacante/ficha/horario/estado").asText()).isEqualTo("PUBLICADO");
        assertThat(contexto.at("/vacante/ficha/horario/texto").asText()).isEqualTo("Lunes a viernes de 8:00 a 17:00");
        assertThat(contexto.at("/vacante/ficha/responsabilidades/texto").asText()).isEqualTo("Conciliar bancos");
        assertThat(contexto.at("/vacante/ficha/ubicacion/texto").asText()).contains("Selva Alegre");
        // Oculto: ni el importe de la columna antigua, ni ningún otro.
        assertThat(contexto.at("/vacante/ficha/remuneracion/estado").asText()).isEqualTo("NO_PUBLICADO");
        assertThat(contexto.at("/vacante/ficha/remuneracion/texto").isNull()).isTrue();
        assertThat(texto).doesNotContain("3 500").doesNotContain("4 200");
        assertThat(contexto.at("/vacante/ficha/beneficios/estado").asText()).isEqualTo("NO_PUBLICADO");
        assertThat(contexto.at("/vacante/ficha/tipoContrato/estado").asText()).isEqualTo("NO_PUBLICADO");
        // Sin identificadores internos ni datos de la prueba.
        assertThat(texto).doesNotContain(codigo.get("ana")).doesNotContain("EL CASO SECRETO")
                .doesNotContain("\"vacanteId\"").doesNotContain("notaMinima");
        assertThat(contexto.get("procedimiento")).hasSize(5);
        assertThat(contexto.at("/procedimiento/0/codigo").asText()).isEqualTo("PERFIL_INTEGRAL");
        assertThat(contexto.at("/procedimiento/0/estado").asText()).isEqualTo("ACTUAL");
        assertThat(contexto.at("/procedimiento/1/explicacion").asText()).contains("tiempo medido");
        assertThat(contexto.at("/situacion/estado").asText()).isEqualTo("ACTIVA");
        assertThat(contexto.at("/situacion/momento").asText()).isEqualTo("CALIFICANDO");
        assertThat(contexto.at("/situacion/titulo").asText()).isEqualTo("Estamos calificando tus respuestas");
        // AC-07 · calificando no hay tarea.
        assertThat(contexto.get("accion").isNull()).isTrue();
        assertThat(contexto.get("resultado").isNull()).isTrue();
        assertThat(contexto.get("revisionContexto").asLong()).isPositive();
        assertThat(contexto.get("consultadoEn").asText()).isNotEqualTo(contexto.get("actualizadoEn").asText());

        // Otra postulación del mismo número no se mezcla, y una de otra persona no se ve.
        assertThat(consultar(telefono.get("carla"), null, null).get("postulaciones")).hasSize(1);
        firmado(CONTEXTO, "{\"telefono\":\"%s\",\"postulacionRef\":\"%s\"}"
                .formatted(telefono.get("carla"), referencia.get("ana")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.codigo").value("SIN_ACCESO"));
    }

    @Test
    @Order(4)
    @DisplayName("AC-04 · cambiar la visibilidad del sueldo se respeta en la siguiente consulta")
    void elSueldoVisibleYOculto() throws Exception {
        jdbc.update("""
                update vacante set remuneracion_tipo = 'RANGO', remuneracion_min = 3500, remuneracion_max = 4200,
                       remuneracion_moneda = 'PEN' where id = ?""", vacante);
        JsonNode visible = consultar(telefono.get("ana"), referencia.get("ana"), null);
        assertThat(visible.at("/vacante/ficha/remuneracion/estado").asText()).isEqualTo("PUBLICADO");
        assertThat(visible.at("/vacante/ficha/remuneracion/texto").asText()).contains(" a ");

        jdbc.update("""
                update vacante set remuneracion_tipo = 'OCULTA', remuneracion_min = null, remuneracion_max = null,
                       remuneracion_moneda = null where id = ?""", vacante);
        JsonNode oculto = consultar(telefono.get("ana"), referencia.get("ana"), null);
        assertThat(oculto.at("/vacante/ficha/remuneracion/estado").asText()).isEqualTo("NO_PUBLICADO");
        assertThat(oculto.toString()).doesNotContain("3 500").doesNotContain("3500");
    }

    @Test
    @Order(5)
    @DisplayName("QA-C0-F1 · editar la ficha, un requisito o la fecha límite de la prueba mueve la vigencia; guardar lo mismo o consultar, no")
    void laVigenciaDelContexto() throws Exception {
        String ana = telefono.get("ana");
        String suRef = referencia.get("ana");
        JsonNode antes = consultar(ana, suRef, null);
        String huella = "select contexto_actualizado_en::text from vacante where id = " + vacante;
        String marcaAntes = jdbc.queryForObject(huella, String.class);
        // Consultar no es cambiar: la misma revisión, y la base sin tocar.
        assertThat(revision(consultar(ana, suRef, null))).isEqualTo(revision(antes));
        assertThat(revision(consultar(ana, null, null).at("/postulaciones/0"), "actualizadaEn"))
                .isEqualTo(revision(antes));
        assertThat(jdbc.queryForObject(huella, String.class)).isEqualTo(marcaAntes);

        // El horario, desde el panel y con el formulario entero, como lo guarda el equipo.
        String horario = "Turno noche, de 22:00 a 6:00";
        String formulario = formularioDeLaVacante(horario);
        assertThat(respuesta(conToken(put("/api/v1/panel/vacantes/" + vacante), tokenTalento, formulario))
                .get("huboCambios").asBoolean()).isTrue();
        JsonNode conHorario = consultar(ana, suRef, null);
        assertThat(conHorario.at("/vacante/ficha/horario/texto").asText()).isEqualTo(horario);
        assertThat(Instant.parse(conHorario.get("actualizadoEn").asText()))
                .isAfter(Instant.parse(antes.get("actualizadoEn").asText()))
                .isBeforeOrEqualTo(Instant.parse(conHorario.get("consultadoEn").asText()));
        assertThat(revision(conHorario)).isGreaterThan(revision(antes));
        // El listado lleva la misma fecha.
        assertThat(revision(consultar(ana, null, null).at("/postulaciones/0"), "actualizadaEn"))
                .isEqualTo(revision(conHorario));

        // Guardar otra vez lo mismo no es un cambio: ni la fecha ni la revisión se mueven.
        conToken(put("/api/v1/panel/vacantes/" + vacante), tokenTalento, formulario).andExpect(status().isOk());
        assertThat(revision(consultar(ana, suRef, null))).isEqualTo(revision(conHorario));

        // Un requisito indispensable sale dentro de «requisitos»: añadirlo y quitarlo también cuenta.
        long requisito = Long.parseLong(leer(conToken(post("/api/v1/panel/vacantes/" + vacante + "/requisitos"),
                        tokenTalento, "{\"descripcion\":\"Colegiatura vigente\",\"regla\":\"Está colegiado y habilitado\"}")
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString(), "id"));
        JsonNode conRequisito = consultar(ana, suRef, null);
        assertThat(conRequisito.at("/vacante/ficha/requisitos/texto").asText()).contains("Colegiatura vigente");
        assertThat(revision(conRequisito)).isGreaterThan(revision(conHorario));
        conToken(delete("/api/v1/panel/vacantes/" + vacante + "/requisitos/" + requisito), tokenTalento, null)
                .andExpect(status().is2xxSuccessful());
        JsonNode sinRequisito = consultar(ana, suRef, null);
        assertThat(sinRequisito.toString()).doesNotContain("Colegiatura vigente");
        assertThat(revision(sinRequisito)).isGreaterThan(revision(conRequisito));

        // La fecha límite de la prueba es un plazo de la acción: moverla también cuenta.
        conToken(put("/api/v1/panel/vacantes/" + vacante + "/prueba-propia/fecha-limite"), tokenTalento,
                "{\"cierraEn\":\"%s\"}".formatted(Instant.now().plus(Duration.ofDays(12)).truncatedTo(ChronoUnit.SECONDS)))
                .andExpect(status().isOk());
        JsonNode conFecha = consultar(ana, suRef, null);
        assertThat(revision(conFecha)).isGreaterThan(revision(sinRequisito));

        // El nombre de la empresa también viaja: si soporte lo corrige en la base, se fecha.
        String nombre = jdbc.queryForObject("select nombre from organizacion where id = 1", String.class);
        jdbc.update("update organizacion set nombre = ? where id = 1", nombre + " Perú");
        JsonNode conEmpresa = consultar(ana, suRef, null);
        assertThat(conEmpresa.at("/vacante/empresa").asText()).isEqualTo(nombre + " Perú");
        assertThat(revision(conEmpresa)).isGreaterThan(revision(conFecha));
        jdbc.update("update organizacion set nombre = ? where id = 1", nombre);
    }

    /** La revisión del contexto, o la fecha de un resumen del listado en milisegundos. */
    private static long revision(JsonNode contexto) {
        return contexto.get("revisionContexto").asLong();
    }

    private static long revision(JsonNode resumen, String campo) {
        return Instant.parse(resumen.get(campo).asText()).toEpochMilli();
    }

    /** El formulario entero de la vacante, como lo reenvía el panel, con otro horario. */
    private String formularioDeLaVacante(String horario) throws Exception {
        JsonNode actual = respuesta(conToken(get("/api/v1/panel/vacantes/" + vacante), tokenTalento, null));
        com.fasterxml.jackson.databind.node.ObjectNode formulario = json.createObjectNode();
        for (String campo : List.of("solicitudTalentoId", "puestoId", "responsableUsuarioId", "titulo", "descripcion",
                "proposito", "responsabilidades", "requisitos", "modalidad", "ubicacion", "ciudadUbigeo",
                "tipoCierre", "plazas", "cierraEn", "remuneracion")) {
            if (actual.hasNonNull(campo)) {
                formulario.set(campo, actual.get(campo));
            }
        }
        formulario.put("horario", horario);
        return formulario.toString();
    }

    @Test
    @Order(6)
    @DisplayName("AC-19 · 400 VALIDACION con un cuerpo que no vale; 404 SIN_ACCESO con una referencia o un cursor ajenos")
    void losErrores() throws Exception {
        firmado(CONTEXTO, "{\"telefono\":\"%s\",\"postulacionRef\":\"%s\",\"cursor\":\"c1.x.y\"}"
                .formatted(telefono.get("ana"), referencia.get("ana")))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.codigo").value("VALIDACION"));
        for (String cuerpo : List.of("{}", "{\"telefono\":\"hola\"}", "no es json")) {
            firmado(CONTEXTO, cuerpo).andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.codigo").value("VALIDACION"));
        }
        firmado(CONTEXTO, "{\"telefono\":\"%s\",\"postulacionRef\":\"pw_%s\"}"
                .formatted(telefono.get("ana"), "0".repeat(32)))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.codigo").value("SIN_ACCESO"));
        firmado(CONTEXTO, "{\"telefono\":\"%s\",\"cursor\":\"c1.1.inventado\"}".formatted(telefono.get("ana")))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.codigo").value("SIN_ACCESO"));
        // Un número sin procesos: lista vacía, que no es lo mismo que un error.
        assertThat(consultar("+51900000077", null, null).get("postulaciones")).isEmpty();
    }

    @Test
    @Order(7)
    @DisplayName("AC-08/AC-22 · con la prueba habilitada: plazos reales, enlace fijo, y consultar no la empieza")
    void laPruebaHabilitada() throws Exception {
        mover("ana", "PRUEBA_TURNO_CANDIDATO", true);
        Instant cierre = jdbc.queryForObject("select prueba_cierra_en from vacante where id = ?",
                java.time.OffsetDateTime.class, vacante).toInstant();

        JsonNode accion = consultar(telefono.get("ana"), referencia.get("ana"), null).get("accion");
        consultar(telefono.get("ana"), referencia.get("ana"), null);

        assertThat(accion.get("tipo").asText()).isEqualTo("PRUEBA_PUESTO");
        assertThat(accion.get("estado").asText()).isEqualTo("HABILITADA");
        assertThat(accion.get("modalidad").asText()).isEqualTo("CRONOMETRADA");
        assertThat(accion.get("duracionMinutos").asInt()).isEqualTo(90);
        assertThat(Instant.parse(accion.get("empezarAntesDe").asText())).isEqualTo(cierre.truncatedTo(ChronoUnit.MILLIS));
        assertThat(accion.get("iniciadaEn").isNull()).isTrue();
        assertThat(accion.get("entregarAntesDe").isNull()).isTrue();
        assertThat(accion.get("enlace").asText()).isEqualTo(MIS_PROCESOS);
        assertThat(accion.get("zonaHoraria").asText()).isEqualTo("America/Lima");
        assertThat(accion.toString()).doesNotContain("EL CASO SECRETO");
        // Dos consultas después no hay ningún intento empezado: el chat no arranca el reloj.
        assertThat(jdbc.queryForObject("select count(*) from intento_prueba where postulacion_id = ? "
                + "and iniciado_en is not null", Integer.class, postulacion.get("ana"))).isZero();
        assertThat(jdbc.queryForObject("select estado_codigo from postulacion where id = ?", String.class,
                postulacion.get("ana"))).isEqualTo("PRUEBA_TURNO_CANDIDATO");
        // Y el aviso de la prueba lleva el formato de la regla 19 con el plazo para empezar.
        assertThat(jdbc.queryForObject("select texto from aviso_whatsapp where postulacion_id = ?",
                String.class, postulacion.get("ana")))
                .contains("📌 *Contador General*").contains("⏰ Puedes empezarla hasta el *")
                .contains("👉 " + MIS_PROCESOS);

        // QA-C0-F1 · al entrar en la etapa desde el panel le nace su intento sin empezar, con el
        // cierre de la vacante. Darle un día más para empezar cambia su acción: la vigencia se mueve.
        enUnaTransaccion.executeWithoutResult(t -> entradaEtapaTecnica.crearAlEntrar(
                postulacionesRepo.findById(postulacion.get("ana")).orElseThrow(),
                vacantesRepo.findById(vacante).orElseThrow()));
        JsonNode antes = consultar(telefono.get("ana"), referencia.get("ana"), null);
        assertThat(Instant.parse(antes.at("/accion/empezarAntesDe").asText())).isEqualTo(cierre.truncatedTo(ChronoUnit.MILLIS));
        Instant masPlazo = cierre.plus(Duration.ofDays(1)).truncatedTo(ChronoUnit.SECONDS);
        conToken(post("/api/v1/panel/postulaciones/" + postulacion.get("ana") + "/prueba/plazo"), tokenTalento,
                "{\"venceEn\":\"%s\",\"motivo\":\"Pidió un día más\"}".formatted(masPlazo))
                .andExpect(status().isOk());
        JsonNode despues = consultar(telefono.get("ana"), referencia.get("ana"), null);
        assertThat(Instant.parse(despues.at("/accion/empezarAntesDe").asText())).isEqualTo(masPlazo);
        assertThat(revision(despues)).isGreaterThan(revision(antes));
    }

    @Test
    @Order(8)
    @DisplayName("AC-10 · cerrar la convocatoria deja su evento y no cierra ni la postulación ni el vínculo")
    void laConvocatoriaCerrada() throws Exception {
        conToken(post("/api/v1/panel/vacantes/" + vacante + "/cierre"), tokenTalento,
                "{\"motivo\":\"Ya hay suficientes postulantes\"}").andExpect(status().is2xxSuccessful());

        assertThat(contar("select count(*) from evento_whatsapp where tipo = 'CONVOCATORIA_CERRADA' "
                + "and estado = 'PENDIENTE'")).isEqualTo(6);
        assertThat(jdbc.queryForObject("select estado_codigo from postulacion where id = ?", String.class,
                postulacion.get("ana"))).isEqualTo("PRUEBA_TURNO_CANDIDATO");
        assertThat(contar("select count(*) from vinculo_whatsapp where estado = 'ACTIVO'")).isEqualTo(6);

        JsonNode contexto = consultar(telefono.get("ana"), referencia.get("ana"), null);
        assertThat(contexto.at("/vacante/convocatoria/estado").asText()).isEqualTo("CERRADA");
        assertThat(contexto.at("/vacante/convocatoria/revision").asInt()).isEqualTo(1);
        assertThat(contexto.at("/vacante/convocatoria/cerradaEn").isNull()).isFalse();
        assertThat(contexto.at("/situacion/estado").asText()).isEqualTo("ACTIVA");
        assertThat(contexto.at("/accion/estado").asText()).isEqualTo("HABILITADA");

        sondeo();
        List<ClienteConversacionFalso.Llamada> deAna = eventosDe("ana");
        assertThat(deAna).hasSize(1);
        assertThat(deAna.get(0).cuerpo().get("tipo")).isEqualTo("CONVOCATORIA_CERRADA");
        assertThat(deAna.get(0).cuerpo().get("cierraVinculo")).isEqualTo(false);
        assertThat(deAna.get(0).cuerpo()).containsKey("convocatoria").doesNotContainKey("resultado");
        assertThat(contar("select count(*) from evento_whatsapp where tipo = 'CONVOCATORIA_CERRADA' "
                + "and estado = 'ACEPTADO'")).isEqualTo(6);
    }

    @Test
    @Order(9)
    @DisplayName("AC-12/AC-13/AC-09 · el resultado queda con el hecho, sale una vez y cierra el vínculo sin revocación aparte")
    void elResultadoDeAna() throws Exception {
        mover("ana", "NO_CONTINUA", true);

        // Ya confirmado y sin haber llamado al bot: el resultado y su pendiente están en la base.
        assertThat(contar("select count(*) from resultado_whatsapp where postulacion_id = "
                + postulacion.get("ana") + " and tipo = 'NO_CONTINUA'")).isEqualTo(1);
        assertThat(contar("select count(*) from evento_whatsapp where postulacion_id = "
                + postulacion.get("ana") + " and tipo = 'RESULTADO' and estado = 'PENDIENTE'")).isEqualTo(1);
        assertThat(jdbc.queryForObject("""
                select estado || ' ' || motivo_cierre || ' ' || (revocacion_proximo_intento_en is null)
                  from vinculo_whatsapp where postulacion_id = ?""", String.class, postulacion.get("ana")))
                .isEqualTo("REVOCADO PROCESO_TERMINADO true");

        sondeo();
        sondeo();

        List<ClienteConversacionFalso.Llamada> resultados = eventosDe("ana").stream()
                .filter(l -> "RESULTADO".equals(l.cuerpo().get("tipo"))).toList();
        assertThat(resultados).hasSize(1);
        Map<String, Object> cuerpo = resultados.get(0).cuerpo();
        assertThat(cuerpo.get("cierraVinculo")).isEqualTo(true);
        assertThat(((Map<?, ?>) cuerpo.get("resultado")).get("tipo")).isEqualTo("NO_CONTINUA");
        assertThat(((Map<?, ?>) cuerpo.get("resultado")).get("explicacion")).isNull();
        assertThat(((Map<?, ?>) cuerpo.get("aviso")).get("autorizado")).isEqualTo(true);
        assertThat(Duration.between(Instant.parse(cuerpo.get("ocurridoEn").toString()),
                Instant.parse(((Map<?, ?>) cuerpo.get("aviso")).get("caducaEn").toString())))
                .isEqualTo(Duration.ofHours(24));
        // C-7 · el evento cierra el vínculo: no hay revocación PROCESO_TERMINADO aparte.
        assertThat(bot().registroDe(telefono.get("ana"))).extracting(ClienteConversacionFalso.Llamada::tipo)
                .doesNotContain("REVOCACION");
        assertThat(contar("select count(*) from vinculo_whatsapp where postulacion_id = "
                + postulacion.get("ana") + " and revocacion_enviada_en is not null")).isEqualTo(1);

        // AC-09/AC-11 · terminado, el resultado se consulta con su fecha.
        JsonNode resumen = consultar(telefono.get("ana"), null, null).at("/postulaciones/0");
        assertThat(resumen.get("situacion").asText()).isEqualTo("FINAL");
        assertThat(resumen.get("resultado").asText()).isEqualTo("NO_CONTINUA");
        JsonNode contexto = consultar(telefono.get("ana"), referencia.get("ana"), null);
        assertThat(contexto.at("/resultado/tipo").asText()).isEqualTo("NO_CONTINUA");
        assertThat(contexto.at("/resultado/explicacion").isNull()).isTrue();
        assertThat(contexto.at("/resultado/esCorreccion").asBoolean()).isFalse();
        assertThat(contexto.get("accion").isNull()).isTrue();
        assertThat(contexto.at("/situacion/estado").asText()).isEqualTo("FINAL");
        assertThat(contexto.at("/procedimiento/0/estado").asText()).isEqualTo("HECHA");
    }

    @Test
    @Order(10)
    @DisplayName("C-10 · con el bot en v1 el evento falla una sola vez y el vínculo se cierra con la revocación de siempre")
    void elBotEnV1() throws Exception {
        bot().responderComoVersion(1);
        mover("carla", "CERRADA", true);

        sondeo();
        sondeo();
        sondeo();

        assertThat(jdbc.queryForMap("""
                select estado, motivo, intentos from evento_whatsapp
                 where postulacion_id = ? and tipo = 'RESULTADO'""", postulacion.get("carla")))
                .containsEntry("estado", "FALLIDO").containsEntry("motivo", "ruta_no_disponible")
                .containsEntry("intentos", 1);
        assertThat(bot().registroDe(telefono.get("carla")).stream()
                .filter(l -> "REVOCACION".equals(l.tipo())).map(l -> l.cuerpo().get("motivo")).toList())
                .containsExactly("PROCESO_TERMINADO");
        // El resultado se sigue consultando en EX, aunque el bot no lo recibiera.
        assertThat(consultar(telefono.get("carla"), null, null).at("/postulaciones/0/resultado").asText())
                .isEqualTo("CERRADA");
    }

    @Test
    @Order(11)
    @DisplayName("AC-18 · con el productor apagado EX se comporta como v1, y al encenderlo no salen hechos anteriores")
    void elProductorApagado() throws Exception {
        propiedades.setProductorV2(false);
        mover("dora", "NO_CONTINUA", true);
        sondeo();

        assertThat(contar("select count(*) from evento_whatsapp where postulacion_id = "
                + postulacion.get("dora") + " and tipo = 'RESULTADO'")).isZero();
        assertThat(bot().registroDe(telefono.get("dora")).stream()
                .filter(l -> "REVOCACION".equals(l.tipo())).map(l -> l.cuerpo().get("motivo")).toList())
                .containsExactly("PROCESO_TERMINADO");

        propiedades.setProductorV2(true);
        sondeo();
        assertThat(eventosDe("dora").stream().filter(l -> "RESULTADO".equals(l.cuerpo().get("tipo")))).isEmpty();
    }

    @Test
    @Order(12)
    @DisplayName("AC-15 · una BAJA antes del despacho cancela el aviso final y retira la consulta")
    void laBaja() throws Exception {
        mover("eva", "CONTRATADO", true);
        firmado("/api/v1/integraciones/whatsapp/bajas", "{\"telefono\":\"%s\"}".formatted(telefono.get("eva")))
                .andExpect(status().isNoContent());
        sondeo();

        assertThat(jdbc.queryForMap("""
                select estado, motivo from evento_whatsapp where postulacion_id = ? and tipo = 'RESULTADO'""",
                postulacion.get("eva"))).containsEntry("estado", "OMITIDO").containsEntry("motivo", "suprimido");
        assertThat(eventosDe("eva").stream().filter(l -> "RESULTADO".equals(l.cuerpo().get("tipo")))).isEmpty();
        assertThat(jdbc.queryForObject("select acceso_revocado_motivo from resultado_whatsapp where postulacion_id = ?",
                String.class, postulacion.get("eva"))).isEqualTo("BAJA");
        assertThat(consultar(telefono.get("eva"), null, null).get("postulaciones")).isEmpty();
    }

    @Test
    @Order(13)
    @DisplayName("AC-16/AC-11 · pasadas las 24 horas no sale; pasados los 30 días ya no se consulta")
    void caducidades() throws Exception {
        mover("gina", "NO_CONTINUA", true);
        jdbc.update("update evento_whatsapp set aviso_caduca_en = now() - interval '1 minute' where postulacion_id = ?",
                postulacion.get("gina"));
        sondeo();
        sondeo();

        assertThat(jdbc.queryForMap("""
                select estado, motivo from evento_whatsapp where postulacion_id = ? and tipo = 'RESULTADO'""",
                postulacion.get("gina"))).containsEntry("estado", "OMITIDO")
                .containsEntry("motivo", "autorizacion_caducada");
        assertThat(eventosDe("gina").stream().filter(l -> "RESULTADO".equals(l.cuerpo().get("tipo")))).isEmpty();
        // El vínculo recupera su revocación de siempre, y el bot lo cierra.
        assertThat(bot().registroDe(telefono.get("gina")).stream()
                .filter(l -> "REVOCACION".equals(l.tipo())).map(l -> l.cuerpo().get("motivo")).toList())
                .containsExactly("PROCESO_TERMINADO");
        String suya = consultar(telefono.get("gina"), null, null).at("/postulaciones/0/postulacionRef").asText();
        assertThat(consultar(telefono.get("gina"), suya, null).at("/resultado/tipo").asText()).isEqualTo("NO_CONTINUA");

        jdbc.update("""
                update resultado_whatsapp set fecha = now() - interval '31 days', consultable_hasta = now() - interval '1 day'
                 where postulacion_id = ?""", postulacion.get("gina"));
        assertThat(consultar(telefono.get("gina"), null, null).get("postulaciones")).isEmpty();
        firmado(CONTEXTO, "{\"telefono\":\"%s\",\"postulacionRef\":\"%s\"}".formatted(telefono.get("gina"), suya))
                .andExpect(status().isNotFound());
    }

    @Test
    @Order(14)
    @DisplayName("AC-21 · el borrado de datos se lleva resultados y eventos, sin un aviso final nuevo")
    void elBorrado() throws Exception {
        mover("fer", "NO_CONTINUA", true);
        long personaId = jdbc.queryForObject("select u.persona_id from usuario u where u.correo = 'fer.v76@correo.pe'",
                Long.class);
        conToken(post("/api/v1/portal/solicitudes-borrado"), token.get("fer"),
                "{\"motivo\":\"Ya no quiero participar\"}").andExpect(status().isCreated());
        long solicitudId = jdbc.queryForObject("select max(id) from solicitud_borrado", Long.class);
        conToken(post("/api/v1/panel/solicitudes-borrado/" + solicitudId + "/ejecucion"), tokenTalento,
                "{\"motivo\":\"Lo pidió el titular\"}").andExpect(status().isOk());
        sondeo();

        assertThat(contar("select count(*) from evento_whatsapp where postulacion_id = " + postulacion.get("fer"))).isZero();
        assertThat(contar("select count(*) from resultado_whatsapp where postulacion_id = " + postulacion.get("fer"))).isZero();
        assertThat(contar("select count(*) from vinculo_whatsapp where persona_id = " + personaId)).isZero();
        assertThat(eventosDe("fer").stream().filter(l -> "RESULTADO".equals(l.cuerpo().get("tipo")))).isEmpty();
        assertThat(consultar(telefono.get("fer"), null, null).get("postulaciones")).isEmpty();
    }

    // ============ ayudas ============

    private void cuentaYPostula(String quien, String celular) throws Exception {
        String correo = quien + ".v76@correo.pe";
        mvc.perform(post("/api/v1/portal/cuentas").contentType(MediaType.APPLICATION_JSON).content("""
                        {"nombre":"%s","apellidos":"Prueba","correo":"%s","telefono":"%s",
                         "contrasena":"unaClaveLarga123","ciudadUbigeo":"1501","aceptaPlataforma":true,
                         "aceptaFuturosContactos":false}""".formatted(quien, correo, celular)))
                .andExpect(status().isCreated());
        String suToken = leer(mvc.perform(post("/api/v1/portal/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"correo\":\"%s\",\"contrasena\":\"unaClaveLarga123\"}".formatted(correo)))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString(), "token");
        token.put(quien, suToken);
        telefono.put(quien, "+51" + celular.replace(" ", ""));
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

    /** Pulsa el botón y escribe al bot desde su número. */
    private void vincularA(String quien) throws Exception {
        conToken(post("/api/v1/portal/postulaciones/" + codigo.get(quien) + "/whatsapp"),
                token.get(quien), null).andExpect(status().isOk());
        firmado("/api/v1/integraciones/whatsapp/vinculos",
                "{\"telefono\":\"%s\",\"wamid\":\"w-%s\",\"texto\":\"hola\"}".formatted(telefono.get(quien), quien))
                .andExpect(status().isOk());
    }

    /** Como lo haría el panel o el pase automático: por la máquina de estados, en su transacción. */
    private void mover(String quien, String estado, boolean avisar) {
        boolean esFinal = List.of("CONTRATADO", "NO_CONTINUA", "CERRADA").contains(estado);
        enUnaTransaccion.executeWithoutResult(t -> maquina.transicionar(
                postulacionesRepo.findById(postulacion.get(quien)).orElseThrow(), estado, null,
                "Movida por la prueba", true, false, esFinal ? "DECISION_PERSONA" : null, avisar));
    }

    /** Una vuelta del sondeo de WhatsApp, como el programado: avisos y revocaciones, y eventos. */
    private void sondeo() {
        envio.enviarPendientes();
        eventos.enviarPendientes();
    }

    private List<ClienteConversacionFalso.Llamada> eventosDe(String quien) {
        return bot().registroDe(telefono.get(quien)).stream()
                .filter(l -> ClienteConversacionFalso.EVENTO.equals(l.tipo())).toList();
    }

    /** La consulta v2, como la hace el bot: firmada y en POST. */
    private JsonNode consultar(String suTelefono, String ref, String cursor) throws Exception {
        StringBuilder cuerpo = new StringBuilder("{\"telefono\":\"").append(suTelefono).append('"');
        if (ref != null) {
            cuerpo.append(",\"postulacionRef\":\"").append(ref).append('"');
        }
        if (cursor != null) {
            cuerpo.append(",\"cursor\":\"").append(cursor).append('"');
        }
        return respuesta(firmado(CONTEXTO, cuerpo.append('}').toString()));
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

    private String leer(String cuerpoRespuesta, String campo) throws Exception {
        return json.readTree(cuerpoRespuesta).get(campo).asText();
    }
}
