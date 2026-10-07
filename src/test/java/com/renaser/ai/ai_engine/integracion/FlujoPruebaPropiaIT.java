package com.renaser.ai.ai_engine.integracion;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.renaser.ai.ai_engine.ai.service.ColaCalificacionIa;
import com.renaser.ai.ai_engine.integracion.soporte.ImagenesDeContenedores;
import com.renaser.ai.ai_engine.perfilintegral.dto.DtosRecomendador.CasoPropuesto;
import com.renaser.ai.ai_engine.perfilintegral.dto.DtosRecomendador.CriterioPropuesto;
import com.renaser.ai.ai_engine.perfilintegral.dto.DtosRecomendador.EntregablePropuesto;
import com.renaser.ai.ai_engine.perfilintegral.dto.DtosRecomendador.PreguntaPropuesta;
import com.renaser.ai.ai_engine.perfilintegral.dto.DtosRecomendador.ResultadoRecomendadorPrueba;
import com.renaser.ai.ai_engine.perfilintegral.entity.PropuestaPreguntas;
import com.renaser.ai.ai_engine.perfilintegral.repository.PropuestaPreguntasRepository;
import com.renaser.ai.ai_engine.perfilintegral.service.PuenteRecomendador;
import com.renaser.ai.ai_engine.prueba.dto.DtosPruebaIa.InsumoPruebaPropia;
import com.renaser.ai.ai_engine.prueba.dto.DtosPruebaIa.NotaCriterioPropiaIa;
import com.renaser.ai.ai_engine.prueba.dto.DtosPruebaIa.ResultadoPruebaPropia;
import com.renaser.ai.ai_engine.prueba.dto.DtosPruebaIa.TandaCalificada;
import com.renaser.ai.ai_engine.prueba.service.PuentePruebaIa;
import com.renaser.ai.ai_engine.prueba.service.ServicioPrueba;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
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
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.rabbitmq.RabbitMQContainer;

import java.io.ByteArrayInputStream;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * La prueba técnica escrita en el editor, de punta a punta (V67, fase 2).
 *
 * <p>Una vacante nueva nace con su prueba por escribir y no se publica sin ella. Publicar con
 * cuatro faltas las devuelve todas; con todo en su sitio se publica, y hasta la primera
 * rendición se puede sustituir. Una candidata rinde los cuatro tipos sin ver un punto, no
 * puede entregar con huecos, y al entregar sus cerradas cuentan solas. La IA califica la
 * parte calificada por id (red de seguridad incluida), una persona califica lo suyo y con eso
 * sale la nota de la etapa. Con candidatos dentro se cambian los puntos y la guía; quien deja
 * vencer el tiempo con huecos no sale en el ranking; y la prueba se copia a otra vacante.
 *
 * <p>La IA está apagada: lo que el agente entregaría se mete por el mismo puente que usa el
 * agente, que es donde vive la red de seguridad.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@Testcontainers
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@DisplayName("La prueba técnica con el editor: escribir, rendir, calificar y copiar")
public class FlujoPruebaPropiaIT {

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

    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired PuentePruebaIa puente;
    @Autowired ServicioPrueba servicioPrueba;
    @Autowired PuenteRecomendador puenteRecomendador;
    @Autowired PropuestaPreguntasRepository propuestas;
    @MockitoSpyBean ColaCalificacionIa cola;
    final ObjectMapper json = new ObjectMapper();

    static String tokenTalento;
    static String tokenArea;
    static long areaId;
    static long puestoId;
    static long vacanteId;
    static long criterioConocimiento;
    static long criterioExcel;
    static long criterioComunicacion;
    static long entregableTablero;
    static long entregableVideo;
    static final Map<String, Long> pregunta = new HashMap<>();
    static final Map<String, Long> opcion = new HashMap<>();
    static final Map<String, Long> postulacion = new HashMap<>();
    static final Map<String, String> codigo = new HashMap<>();
    static final Map<String, String> tokenCandidata = new HashMap<>();

    // ============ La vacante nueva ============

    @DisplayName("Una vacante nueva rinde la prueba del editor y sin ella no se publica (AC-01, AC-03)")
    @Test
    @Order(1)
    void laVacanteNuevaRindeLaPruebaDelEditor() throws Exception {
        tokenTalento = leer(mvc.perform(post("/api/v1/panel/auth/dev-login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"usuarioRenaserOsId\":\"dev-talento\"}"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString(), "token");
        jdbc.update("INSERT INTO area (organizacion_id, nombre, es_activa) VALUES (1, 'Contabilidad', true)");
        areaId = jdbc.queryForObject("SELECT id FROM area ORDER BY id DESC LIMIT 1", Long.class);
        puestoId = Long.parseLong(leer(conToken(post("/api/v1/panel/puestos"), tokenTalento, """
                {"codigo": "ASIST_CONT_P", "nombre": "Asistente contable",
                 "nivelPuestoCodigo": "EJECUCION", "familiaCodigo": "OPERACIONES"}""")
                .andReturn().getResponse().getContentAsString(), "id"));
        vacanteId = crearVacante("Asistente contable");

        conTokenGet("/api/v1/panel/vacantes/" + vacanteId, tokenTalento)
                .andExpect(jsonPath("$.instrumentoEtapaTecnica").value("PRUEBA_PROPIA"));
        // La evaluación del banco apagada: quien postule va directo a la bandeja, y su única
        // evaluación es la prueba.
        conToken(post("/api/v1/panel/vacantes/" + vacanteId + "/aplicacion-evaluacion"), tokenTalento,
                "{\"aplica\":false}").andExpect(status().isOk());

        conTokenGet(base(vacanteId), tokenTalento)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.proposito").value("PRUEBA_PUESTO"))
                .andExpect(jsonPath("$.resumen.estado").value("SIN_PRUEBA"))
                .andExpect(jsonPath("$.puedeEditar").value(true));

        // AC-03: sin prueba publicada, el servidor no publica la vacante
        conToken(post("/api/v1/panel/vacantes/" + vacanteId + "/publicacion"), tokenTalento, null)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value(org.hamcrest.Matchers.containsString("prueba técnica")));
        // La fecha límite (V68) es de la vacante y todavía no hay; una pasada no se acepta
        conTokenGet(base(vacanteId), tokenTalento)
                .andExpect(jsonPath("$.fechaLimite.cierraEn").doesNotExist())
                .andExpect(jsonPath("$.fechaLimite.pideMotivo").value(false));
        conToken(put(base(vacanteId) + "/fecha-limite"), tokenTalento,
                "{\"cierraEn\":\"%s\"}".formatted(java.time.Instant.now().minus(java.time.Duration.ofDays(1))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.faltas[0]").value(org.hamcrest.Matchers.containsString("ya pasó")));
    }

    @DisplayName("Lo que no se puede guardar: 400 con la lista entera (casos límite)")
    @Test
    @Order(2)
    void loQueNoSePuedeGuardar() throws Exception {
        conToken(post(base(vacanteId) + "/criterios"), tokenTalento, """
                {"nombre":"Mal","puntos":2.5,"calificador":"IA"}""")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.faltas[0]").value(org.hamcrest.Matchers.containsString("entero")));
        conToken(post(base(vacanteId) + "/criterios"), tokenTalento, """
                {"nombre":"Mal","puntos":-3}""")
                .andExpect(status().isBadRequest());
        conToken(post(base(vacanteId) + "/criterios"), tokenTalento, """
                {"nombre":"Mal","puntos":20}""")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.faltas[0]").value(org.hamcrest.Matchers.containsString("quién califica")));
        // Lo que vale el criterio se escribe siempre (V69)
        conToken(post(base(vacanteId) + "/criterios"), tokenTalento, "{\"nombre\":\"Mal\"}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.faltas[0]").value(org.hamcrest.Matchers.startsWith("Faltan los puntos del criterio")));
        conToken(post(base(vacanteId) + "/entregables"), tokenTalento, """
                {"nombre":"Sin formato","obligatorio":true}""")
                .andExpect(status().isBadRequest());
        conToken(put(base(vacanteId) + "/borrador"), tokenTalento, """
                {"modalidad":"CRONOMETRADA","duracionMinutos":3}""")
                .andExpect(status().isBadRequest());
        MockMultipartFile texto = new MockMultipartFile("archivo", "caso.txt", "text/plain", "x".getBytes());
        mvc.perform(multipart(base(vacanteId) + "/borrador/consigna").file(texto)
                        .header("Authorization", "Bearer " + tokenTalento))
                .andExpect(status().isBadRequest());
        // Una abierta no lleva puntos
        conToken(post(base(vacanteId) + "/preguntas"), tokenTalento, """
                {"tipo":"ABIERTA","enunciado":"¿Algo?","puntos":5}""")
                .andExpect(status().isBadRequest());
        // Nada de lo rechazado quedó guardado
        JsonNode editor = editor(vacanteId);
        assertThat(editor.at("/borrador/criterios").size()).isZero();
        assertThat(editor.at("/borrador/prueba/entregables").size()).isZero();
    }

    @DisplayName("Sin enunciado no es falta; sí lo son los puntos, la fecha, un criterio de IA que solo mira un enlace y un general que nadie califica (AC-02, AC-05, AC-06, AC-08)")
    @Test
    @Order(3)
    void publicarConCuatroFaltasLasDiceTodas() throws Exception {
        conToken(put(base(vacanteId) + "/borrador"), tokenTalento, """
                {"guiaCalificacion":"Mira las cifras","modalidad":"CRONOMETRADA","duracionMinutos":90}""")
                .andExpect(status().isOk());
        // Se escribe lo que vale el criterio entero (V69): la cerrada que llegue después saldrá
        // de esos 70, no se sumará a ellos.
        criterioConocimiento = idDelCriterio(conToken(post(base(vacanteId) + "/criterios"), tokenTalento, """
                {"nombre":"Conocimiento contable","queEvalua":"Registro y cierre mensual",
                 "puntos":70,"calificador":"IA"}"""), "Conocimiento contable");
        criterioComunicacion = idDelCriterio(conToken(post(base(vacanteId) + "/criterios"), tokenTalento, """
                {"nombre":"Comunicación","puntos":20,"calificador":"IA"}"""), "Comunicación");
        // Recién creado no tiene cerradas: quién califica se dice ahora y deja de contar cuando
        // su cerrada de 25 llena lo que vale.
        criterioExcel = idDelCriterio(conToken(post(base(vacanteId) + "/criterios"), tokenTalento,
                "{\"nombre\":\"Manejo de Excel\",\"puntos\":25,\"calificador\":\"IA\"}"), "Manejo de Excel");
        agregar("unica", """
                {"tipo":"OPCION_UNICA","enunciado":"¿Qué libro registra primero una venta al crédito?",
                 "puntos":10,"criterioId":%d,
                 "opciones":[{"texto":"Libro diario","puntos":10},{"texto":"Caja","puntos":0}]}"""
                .formatted(criterioConocimiento));
        agregar("abiertaA", """
                {"tipo":"ABIERTA","enunciado":"¿Cómo hallaste el descuadre?","criterioId":%d,
                 "queDebeTener":"La cuenta y el monto"}""".formatted(criterioConocimiento));
        agregar("multiple", """
                {"tipo":"OPCION_MULTIPLE","enunciado":"¿Qué funciones buscan un valor?","puntos":25,"criterioId":%d,
                 "opciones":[{"texto":"BUSCARV","puntos":15},{"texto":"XLOOKUP","puntos":10},
                             {"texto":"SUMA","puntos":-5}]}""".formatted(criterioExcel));

        // El archivo se pide desde su pregunta (AC-04): lo mira su criterio, sin marcar nada
        entregableTablero = idDelEntregable(conToken(post(base(vacanteId) + "/entregables"), tokenTalento, """
                {"nombre":"Tablero.xlsx","detalle":"La conciliación de marzo","formato":"ARCHIVO",
                 "obligatorio":true,"queDebeTener":"El descuadre ubicado y corregido","preguntaId":%d}"""
                .formatted(pregunta.get("abiertaA"))), "Tablero.xlsx");
        // Pedirlo dos veces en la misma pregunta no crea dos
        conToken(post(base(vacanteId) + "/entregables"), tokenTalento, """
                {"nombre":"Otro","formato":"ARCHIVO","obligatorio":true,"preguntaId":%d}"""
                .formatted(pregunta.get("abiertaA"))).andExpect(status().isBadRequest());
        // Un general de toda la prueba lo miran todos; uno de las cerradas de Excel, nadie lo califica
        entregableVideo = idDelEntregable(conToken(post(base(vacanteId) + "/entregables"), tokenTalento, """
                {"nombre":"Video","detalle":"Dos minutos explicando","formato":"ENLACE",
                 "obligatorio":false,"todaLaPrueba":true}"""), "Video");
        long notas = idDelEntregable(conToken(post(base(vacanteId) + "/entregables"), tokenTalento, """
                {"nombre":"Notas.docx","formato":"ARCHIVO","obligatorio":false,"cubre":[%d]}"""
                .formatted(pregunta.get("multiple"))), "Notas.docx");
        // Un general sin «Cubre» no se guarda
        conToken(post(base(vacanteId) + "/entregables"), tokenTalento, """
                {"nombre":"Suelto","formato":"ARCHIVO","obligatorio":true}""")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.faltas[0]").value(org.hamcrest.Matchers.containsString("Elige qué cubre")));

        JsonNode borrador = editor(vacanteId).get("borrador");
        JsonNode criterio = buscarCriterio(borrador, criterioConocimiento);
        // AC-05: la cabecera cuenta sistema + parte calificada, y la abierta no tiene puntos. El
        // criterio sigue valiendo 70 con su cerrada dentro: su parte calificada bajó a 60 (V69).
        assertThat(criterio.get("puntos").asInt()).isEqualTo(70);
        assertThat(criterio.get("puntosSistema").asInt()).isEqualTo(10);
        assertThat(criterio.get("puntosCalificados").asInt()).isEqualTo(60);
        assertThat(criterio.get("calificador").asText()).isEqualTo("IA");
        assertThat(buscarPregunta(borrador, "abiertaA").get("puntos").asInt()).isZero();
        // «Mira» se deduce: el archivo de su pregunta y el general de toda la prueba
        assertThat(idsDe(criterio.get("entregables"))).containsExactlyInAnyOrder(entregableTablero, entregableVideo);
        assertThat(idsDe(buscarCriterio(borrador, criterioComunicacion).get("entregables")))
                .containsExactly(entregableVideo);
        assertThat(idsDe(buscarCriterio(borrador, criterioExcel).get("entregables")))
                .containsExactlyInAnyOrder(entregableVideo, notas);

        JsonNode error = json.readTree(conToken(post(base(vacanteId) + "/publicacion"), tokenTalento, null)
                .andExpect(status().isBadRequest()).andReturn().getResponse().getContentAsString());
        List<String> faltas = new ArrayList<>();
        error.get("faltas").forEach(f -> faltas.add(f.asText()));
        assertThat(faltas).hasSize(4);
        assertThat(faltas).anyMatch(f -> f.contains("suman 115 de 100"));
        assertThat(faltas).anyMatch(f -> f.contains("Falta la fecha límite"));
        assertThat(faltas).anyMatch(f -> f.contains("solo mira enlaces"));
        assertThat(faltas).anyMatch(f -> f.contains("«Notas.docx»: nadie lo califica"));
        assertThat(faltas).noneMatch(f -> f.contains("enunciado"));
        assertThat(jdbc.queryForObject("select count(*) from version_banco where vacante_id = ? "
                + "and proposito = 'PRUEBA_PUESTO' and estado = 'PUBLICADA'", Integer.class, vacanteId)).isZero();

        conToken(delete(base(vacanteId) + "/entregables/" + notas), tokenTalento, null).andExpect(status().isOk());
    }

    @DisplayName("Con todo en su sitio y la fecha puesta sin motivo se publica, y la vacante también (AC-03, AC-05, AC-09)")
    @Test
    @Order(4)
    void conTodoSePublica() throws Exception {
        conToken(put(base(vacanteId) + "/borrador"), tokenTalento, """
                {"guiaCalificacion":"Mira las cifras","enunciado":"La empresa cerró marzo con un descuadre de 1200 soles.",
                 "materiales":"El libro mayor de marzo","herramientasPermitidas":"Excel",
                 "modalidad":"CRONOMETRADA","duracionMinutos":90}""").andExpect(status().isOk());
        // Conocimiento vale 30: 10 de cerradas + 20 de IA, mira el tablero (un criterio mixto)
        conToken(put(base(vacanteId) + "/criterios/" + criterioConocimiento), tokenTalento, """
                {"nombre":"Conocimiento contable","queEvalua":"Registro y cierre mensual",
                 "puntos":30,"calificador":"IA"}""").andExpect(status().isOk());
        // Comunicación: de una persona, con una abierta; el video pasa a cubrir solo esa abierta
        conToken(put(base(vacanteId) + "/criterios/" + criterioComunicacion), tokenTalento, """
                {"nombre":"Comunicación","puntos":20,"calificador":"PERSONA"}""")
                .andExpect(status().isOk());
        agregar("abiertaB", """
                {"tipo":"ABIERTA","enunciado":"Explica el ajuste a tu jefe en tres líneas","criterioId":%d,
                 "queDebeTener":"Claridad"}""".formatted(criterioComunicacion));
        conToken(put(base(vacanteId) + "/entregables/" + entregableVideo), tokenTalento, """
                {"nombre":"Video","detalle":"Dos minutos explicando","formato":"ENLACE",
                 "obligatorio":false,"cubre":[%d]}""".formatted(pregunta.get("abiertaB")))
                .andExpect(status().isOk());
        // Manejo de Excel: solo cerradas. Una cerrada más no lo hace valer más: sigue en 25 y
        // sus cerradas pasan de ahí, una falta también al publicar (V69).
        agregar("escala", """
                {"tipo":"ESCALA","enunciado":"¿Cuánto dominas las tablas dinámicas?","puntos":25,"criterioId":%d,
                 "opciones":[{"texto":"Nada","puntos":0},{"texto":"","puntos":12},{"texto":"Mucho","puntos":25}]}"""
                .formatted(criterioExcel));
        JsonNode conExcelPorEncima = editor(vacanteId).get("borrador");
        JsonNode excel = buscarCriterio(conExcelPorEncima, criterioExcel);
        assertThat(excel.get("puntos").asInt()).isEqualTo(25);
        assertThat(excel.get("puntosSistema").asInt()).isEqualTo(50);
        assertThat(excel.get("puntosCalificados").asInt()).isZero();
        assertThat(conExcelPorEncima.get("total").asInt()).isEqualTo(75);
        assertThat(conExcelPorEncima.get("avisos").toString())
                .contains("Las cerradas de «Manejo de Excel» suman 50 y el criterio vale 25.");
        // Se arregla diciendo lo que vale ahora: 50, todo del sistema, sin preguntar quién califica
        conToken(put(base(vacanteId) + "/criterios/" + criterioExcel), tokenTalento,
                "{\"nombre\":\"Manejo de Excel\",\"puntos\":50}").andExpect(status().isOk());

        JsonNode borrador = editor(vacanteId).get("borrador");
        assertThat(borrador.get("total").asInt()).isEqualTo(100);
        assertThat(borrador.get("avisos").toString()).contains("Falta la fecha límite");
        assertThat(idsDe(buscarCriterio(borrador, criterioConocimiento).get("entregables")))
                .containsExactly(entregableTablero);
        assertThat(idsDe(buscarCriterio(borrador, criterioComunicacion).get("entregables")))
                .containsExactly(entregableVideo);

        // AC-09: la fecha se pone antes de publicar, sin motivo, y queda en la vacante
        java.time.Instant cierre = java.time.Instant.now().plus(java.time.Duration.ofDays(20))
                .truncatedTo(java.time.temporal.ChronoUnit.SECONDS);
        JsonNode conFecha = respuesta(conToken(put(base(vacanteId) + "/fecha-limite"), tokenTalento,
                "{\"cierraEn\":\"%s\"}".formatted(cierre)));
        assertThat(conFecha.at("/fechaLimite/cierraEn").isNull()
                || conFecha.at("/fechaLimite/cierraEn").isMissingNode()).isFalse();
        assertThat(conFecha.at("/borrador/avisos").size()).as(conFecha.at("/borrador/avisos").toString()).isZero();
        assertThat(jdbc.queryForObject("select prueba_cierra_en from vacante where id = ?",
                java.sql.Timestamp.class, vacanteId).toInstant()).isEqualTo(cierre);

        JsonNode publicado = json.readTree(conToken(post(base(vacanteId) + "/publicacion"), tokenTalento, null)
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        assertThat(publicado.get("borrador").isNull()).isTrue();
        assertThat(publicado.at("/resumen/estado").asText()).isEqualTo("PUBLICADA");
        assertThat(publicado.at("/resumen/criterios").asInt()).isEqualTo(3);
        assertThat(publicado.at("/resumen/entregables").asInt()).isEqualTo(2);
        assertThat(publicado.at("/resumen/minutos").asInt()).isEqualTo(90);

        conToken(post("/api/v1/panel/vacantes/" + vacanteId + "/publicacion"), tokenTalento, null)
                .andExpect(status().isOk());

        // Publicada y sin nadie dentro, cambiarla tampoco pide motivo; la de siempre (la de
        // «Plazos de la prueba») sigue moviéndola igual.
        conToken(post("/api/v1/panel/vacantes/" + vacanteId + "/cierre-prueba"), tokenTalento,
                "{\"cierraEn\":\"%s\",\"motivo\":\"Cierre de la convocatoria\"}"
                        .formatted(java.time.Instant.now().plus(java.time.Duration.ofDays(21))))
                .andExpect(status().isOk());
        respuesta(conToken(put(base(vacanteId) + "/fecha-limite"), tokenTalento,
                "{\"cierraEn\":\"%s\"}".formatted(cierre)));
        conTokenGet("/api/v1/panel/vacantes/" + vacanteId, tokenTalento)
                .andExpect(jsonPath("$.modalidadPrueba").value("CRONOMETRADA"))
                .andExpect(jsonPath("$.minutosPruebaVigentes").value(90));
    }

    @DisplayName("Hasta la primera rendición, una publicación nueva sustituye a la anterior (AC-07)")
    @Test
    @Order(5)
    void antesDeRendirSeSustituye() throws Exception {
        long primera = publicadaId(vacanteId);
        conToken(post(base(vacanteId) + "/borrador"), tokenTalento, null).andExpect(status().isOk());
        conToken(put(base(vacanteId) + "/borrador"), tokenTalento, """
                {"guiaCalificacion":"Mira las cifras y el criterio","enunciado":"La empresa cerró marzo con un descuadre de 1200 soles.",
                 "materiales":"El libro mayor de marzo","herramientasPermitidas":"Excel",
                 "modalidad":"CRONOMETRADA","duracionMinutos":90}""").andExpect(status().isOk());
        JsonNode editor = json.readTree(conToken(post(base(vacanteId) + "/publicacion"), tokenTalento, null)
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        long segunda = editor.at("/publicada/id").asLong();
        assertThat(segunda).isNotEqualTo(primera);
        assertThat(jdbc.queryForObject("select estado from version_banco where id = ?", String.class, primera))
                .isEqualTo("ARCHIVADA");
        // Los ids cambiaron con la copia: se leen de la publicada nueva
        recordarIds(editor.get("publicada"));
    }

    @DisplayName("Sin editar_vacante el editor se ve en lectura y escribir es 403 (AC-28)")
    @Test
    @Order(6)
    void sinPermisoSeVeEnLectura() throws Exception {
        tokenArea = crearUsuarioConUnSoloRol("Marco", "Quispe", "marco.area.pp@renaser.pe",
                "os-marco-area-pp", "RESPONSABLE_AREA");
        conTokenGet(base(vacanteId), tokenArea)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.puedeEditar").value(false));
        conToken(post(base(vacanteId) + "/criterios"), tokenArea, "{\"nombre\":\"X\"}")
                .andExpect(status().isForbidden());
        conToken(post(base(vacanteId) + "/publicacion"), tokenArea, null)
                .andExpect(status().isForbidden());
    }

    // ============ Rendir ============

    @DisplayName("La candidata ve el caso, el reloj y los cuatro tipos sin un solo punto (AC-08, AC-09)")
    @Test
    @Order(7)
    void laCandidataVeElCasoSinPuntos() throws Exception {
        entrarALaPrueba("ana", "ana.propia@correo.pe");
        String crudo = conTokenGet("/api/v1/portal/prueba/" + codigo.get("ana"), tokenCandidata.get("ana"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertThat(crudo).doesNotContain("puntos", "calificador", "criterio", "queDebeTener",
                "Mira las cifras", "La cuenta y el monto");
        JsonNode prueba = json.readTree(crudo);
        assertThat(prueba.get("estadoIntento").asText()).isEqualTo("PENDIENTE");
        assertThat(prueba.get("delEditor").asBoolean()).isTrue();
        assertThat(prueba.get("cuestionario").asBoolean()).isFalse();
        assertThat(prueba.get("enunciado").asText()).contains("descuadre");
        assertThat(prueba.get("preguntas").size()).isEqualTo(5);
        List<String> tipos = new ArrayList<>();
        prueba.get("preguntas").forEach(p -> tipos.add(p.get("tipo").asText()));
        assertThat(tipos).contains("ABIERTA", "OPCION_UNICA", "OPCION_MULTIPLE", "ESCALA");
        // La pantalla previa sabe la fecha límite, y el archivo de una pregunta dice cuál es
        assertThat(prueba.get("fechaLimite").isNull()).isFalse();
        for (JsonNode e : prueba.get("entregables")) {
            if ("Tablero.xlsx".equals(e.get("nombre").asText())) {
                assertThat(e.get("preguntaId").asLong()).isEqualTo(pregunta.get("abiertaA"));
            } else {
                assertThat(e.get("preguntaId").isNull()).isTrue();
            }
        }

        JsonNode iniciada = json.readTree(mvc.perform(post("/api/v1/portal/prueba/" + codigo.get("ana") + "/inicio")
                        .header("Authorization", "Bearer " + tokenCandidata.get("ana")))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        assertThat(iniciada.get("estadoIntento").asText()).isEqualTo("EN_CURSO");
        assertThat(iniciada.get("duracionMinutos").asInt()).isEqualTo(90);
        // AC-09: sin cambio inesperado
        assertThat(iniciada.get("cambioTexto").isNull()).isTrue();
        assertThat(jdbc.queryForObject("select variante_cambio_id is null and minuto_cambio is null "
                + "from intento_prueba where postulacion_id = ?", Boolean.class, postulacion.get("ana"))).isTrue();
    }

    @DisplayName("Con preguntas sin responder no se entrega, ni llamando a la API (AC-10)")
    @Test
    @Order(8)
    void conHuecosNoSeEntrega() throws Exception {
        String ana = "ana";
        responder(ana, "unica", "{\"opcionId\":%d}".formatted(opcion.get("unica:Libro diario")));
        responder(ana, "abiertaA", "{\"texto\":\"La cuenta 1041, 1200 soles\"}");
        // Un texto con solo espacios no cuenta: deja la pregunta sin responder
        responder(ana, "abiertaB", "{\"texto\":\"    \"}");

        JsonNode error = json.readTree(mvc.perform(post("/api/v1/portal/prueba/" + codigo.get(ana) + "/entrega")
                        .header("Authorization", "Bearer " + tokenCandidata.get(ana)))
                .andExpect(status().isBadRequest()).andReturn().getResponse().getContentAsString());
        assertThat(error.get("detail").asText()).contains("Para entregar te falta responder 3 preguntas");
        assertThat(error.get("faltas").toString()).contains("Tablero.xlsx");

        responder(ana, "abiertaB", "{\"texto\":\"Encontré el error y lo corregí\"}");
        responder(ana, "multiple", "{\"marcadas\":[%d,%d]}".formatted(
                opcion.get("multiple:BUSCARV"), opcion.get("multiple:XLOOKUP")));
        responder(ana, "escala", "{\"opcionId\":%d}".formatted(opcion.get("escala:Mucho")));
        // Todo respondido pero sin el entregable obligatorio: tampoco
        mvc.perform(post("/api/v1/portal/prueba/" + codigo.get(ana) + "/entrega")
                        .header("Authorization", "Bearer " + tokenCandidata.get(ana)))
                .andExpect(status().isBadRequest());

        subirTablero(ana);
        mvc.perform(post("/api/v1/portal/prueba/" + codigo.get(ana) + "/entregables/" + entregableVideo + "/enlace")
                        .header("Authorization", "Bearer " + tokenCandidata.get(ana))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"enlace\":\"https://video.ejemplo/ana\"}"))
                .andExpect(status().isOk());
        mvc.perform(post("/api/v1/portal/prueba/" + codigo.get(ana) + "/entrega")
                        .header("Authorization", "Bearer " + tokenCandidata.get(ana)))
                .andExpect(status().isOk());
        assertThat(estado(ana)).isEqualTo("PRUEBA_CALIFICANDO");
    }

    @DisplayName("Al entregar, las cerradas cuentan solas; el criterio mixto queda pendiente y no hay nota (AC-12, AC-15)")
    @Test
    @Order(9)
    void lasCerradasCuentanYElMixtoQuedaPendiente() throws Exception {
        JsonNode prueba = pruebaDe("ana");
        assertThat(prueba.get("estado").asText()).isEqualTo("ENTREGADA");
        assertThat(prueba.get("nota").isNull()).isTrue();
        JsonNode conocimiento = criterioDe(prueba, criterioConocimiento);
        assertThat(conocimiento.get("estado").asText()).isEqualTo("PENDIENTE");
        assertThat(conocimiento.get("sistema").decimalValue()).isEqualByComparingTo("10");
        assertThat(conocimiento.get("nota").isNull()).isTrue();
        JsonNode excel = criterioDe(prueba, criterioExcel);
        assertThat(excel.get("estado").asText()).isEqualTo("SOLO_SISTEMA");
        assertThat(excel.get("nota").decimalValue()).isEqualByComparingTo("50");
        assertThat(notaDeLaEtapa("ana")).isNull();

        // El ranking dice «pendiente» en el mixto y deja en blanco el de persona, por id
        JsonNode fila = filaDelRanking(vacanteId, "PRUEBA_PUESTO", postulacion.get("ana"));
        JsonNode notaConocimiento = notaDeLaFila(fila, "prueba:" + criterioConocimiento);
        assertThat(notaConocimiento.get("estado").asText()).isEqualTo("PENDIENTE");
        assertThat(notaConocimiento.get("puntaje").isNull()).isTrue();
        assertThat(notaDeLaFila(fila, "prueba:" + criterioComunicacion).get("estado").asText()).isEqualTo("EN_BLANCO");

        // AC-12: la IA recibe solo el criterio de IA, su máximo de parte calificada, sus
        // abiertas y lo que mira, los datos de la vacante y los minutos efectivos
        InsumoPruebaPropia insumo = puente.insumoPruebaPropia(postulacion.get("ana"));
        assertThat(insumo.criterios()).hasSize(1);
        assertThat(insumo.criterios().get(0).criterioId()).isEqualTo(criterioConocimiento);
        assertThat(insumo.criterios().get(0).puntosMaximos()).isEqualTo(20);
        assertThat(insumo.criterios().get(0).abiertas()).hasSize(1);
        assertThat(insumo.criterios().get(0).abiertas().get(0).queDebeTener()).isEqualTo("La cuenta y el monto");
        assertThat(insumo.criterios().get(0).entregables()).hasSize(1);
        assertThat(insumo.criterios().get(0).entregables().get(0).nombre()).isEqualTo("Tablero.xlsx");
        assertThat(insumo.minutosEfectivos()).isEqualTo(90);
        assertThat(insumo.vacante().titulo()).isEqualTo("Asistente contable");
        assertThat(json.writeValueAsString(insumo)).doesNotContain("remuneracion", "horario", "ubicacion",
                "ciudad");
    }

    @DisplayName("La red de seguridad, por id: acota al máximo, descarta lo ajeno, lo de persona y lo mudo (AC-13, AC-14)")
    @Test
    @Order(10)
    void laRedDeSeguridadPorId() throws Exception {
        long anaId = postulacion.get("ana");
        // Calculado con una guía que ya no rige: no se guarda nada y se pide otra vez
        assertThatThrownBy(() -> puente.guardarNotasPruebaPropia(anaId, 99, List.of(tanda(
                nota(criterioConocimiento, "15", "Bien")))))
                .isInstanceOf(IllegalStateException.class);
        assertThat(contar("select count(*) from nota_criterio_prueba")).isZero();

        int guia = jdbc.queryForObject("select version_guia from version_banco where id = ?", Integer.class,
                publicadaId(vacanteId));
        puente.guardarNotasPruebaPropia(anaId, guia, List.of(
                tanda(nota(criterioConocimiento, "28", "Ubicó la cuenta y el monto")),
                tanda(nota(criterioComunicacion, "18", "Se explica bien"),   // de persona
                        nota(999_999L, "10", "Inventado"),                     // desconocido
                        nota(criterioExcel, "10", "Sin parte calificada"))));   // solo sistema
        // AC-13: 28 sobre 20 se guarda como 20; el criterio vale cerradas + 20
        assertThat(jdbc.queryForObject("select puntaje from nota_criterio_prueba where criterio_banco_id = ?",
                BigDecimal.class, criterioConocimiento)).isEqualByComparingTo("20");
        assertThat(contar("select count(*) from nota_criterio_prueba")).isEqualTo(1);
        JsonNode prueba = pruebaDe("ana");
        assertThat(criterioDe(prueba, criterioConocimiento).get("nota").decimalValue()).isEqualByComparingTo("30");
        // Comunicación es de persona: sigue sin nota, así que tampoco hay nota de la etapa
        assertThat(criterioDe(prueba, criterioComunicacion).get("estado").asText()).isEqualTo("PENDIENTE");
        assertThat(notaDeLaEtapa("ana")).isNull();
        assertThat(estado("ana")).isEqualTo("PRUEBA_CALIFICANDO");
    }

    @DisplayName("Ajustar a mano: límites, motivo y permiso; la parte automática no se ajusta (AC-17)")
    @Test
    @Order(11)
    void ajustarAManoTieneSusLimites() throws Exception {
        String ruta = ajuste("ana", criterioComunicacion);
        conToken(put(ruta), tokenTalento, "{\"puntaje\":21,\"motivo\":\"Muy claro\"}")
                .andExpect(status().isBadRequest());
        conToken(put(ruta), tokenTalento, "{\"puntaje\":15,\"motivo\":\"  \"}")
                .andExpect(status().isBadRequest());
        conToken(put(ajuste("ana", criterioExcel)), tokenTalento, "{\"puntaje\":5,\"motivo\":\"x\"}")
                .andExpect(status().isBadRequest());
        conToken(put(ruta), tokenArea, "{\"puntaje\":15,\"motivo\":\"Muy claro\"}")
                .andExpect(status().isForbidden());
    }

    @DisplayName("La persona pone lo último que faltaba: nota de la etapa y «por confirmar» (AC-16, AC-25)")
    @Test
    @Order(12)
    void laPersonaCompletaYPasaAPorConfirmar() throws Exception {
        JsonNode prueba = json.readTree(conToken(put(ajuste("ana", criterioComunicacion)), tokenTalento,
                        "{\"puntaje\":15,\"motivo\":\"Clara, le faltó cerrar con la cifra\"}")
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        // 30 (10 + 20) + 50 + 15
        assertThat(notaDeLaEtapa("ana")).isEqualByComparingTo("95");
        assertThat(estado("ana")).isEqualTo("PRUEBA_POR_CONFIRMAR");
        // AC-25: la suma de los criterios cuadra con la nota, y cada uno dice de dónde sale
        BigDecimal suma = BigDecimal.ZERO;
        for (JsonNode c : prueba.get("criterios")) {
            suma = suma.add(c.get("nota").decimalValue());
        }
        assertThat(suma).isEqualByComparingTo(prueba.get("nota").decimalValue());
        JsonNode comunicacion = criterioDe(prueba, criterioComunicacion);
        assertThat(comunicacion.get("ajustadaPor").asText()).isNotBlank();
        assertThat(comunicacion.get("motivoAjuste").asText()).contains("cifra");
        JsonNode excel = criterioDe(prueba, criterioExcel);
        JsonNode multiple = null;
        for (JsonNode p : excel.get("preguntas")) {
            if ("OPCION_MULTIPLE".equals(p.get("tipo").asText())) multiple = p;
        }
        assertThat(multiple).isNotNull();
        long marcadas = 0;
        for (JsonNode o : multiple.get("opciones")) {
            if (o.get("marcada").asBoolean()) marcadas++;
        }
        assertThat(marcadas).isEqualTo(2);
    }

    @DisplayName("Un ajuste a mano no lo pisa la IA, y la de la IA queda a la vista (AC-17)")
    @Test
    @Order(13)
    void elAjusteNoLoPisaLaIa() throws Exception {
        conToken(put(ajuste("ana", criterioConocimiento)), tokenTalento,
                "{\"puntaje\":12,\"motivo\":\"No corrigió el asiento\"}").andExpect(status().isOk());
        int guia = jdbc.queryForObject("select version_guia from version_banco where id = ?", Integer.class,
                publicadaId(vacanteId));
        puente.guardarNotasPruebaPropia(postulacion.get("ana"), guia,
                List.of(tanda(nota(criterioConocimiento, "19", "Otra vez"))));
        Map<String, Object> fila = jdbc.queryForMap("select puntaje, puntaje_ia from nota_criterio_prueba "
                + "where criterio_banco_id = ?", criterioConocimiento);
        assertThat((BigDecimal) fila.get("puntaje")).isEqualByComparingTo("12");
        assertThat((BigDecimal) fila.get("puntaje_ia")).isEqualByComparingTo("20");
        // Recalcula la etapa sin mover a nadie: 22 + 50 + 15
        assertThat(notaDeLaEtapa("ana")).isEqualByComparingTo("87");
        assertThat(estado("ana")).isEqualTo("PRUEBA_POR_CONFIRMAR");
    }

    @DisplayName("Al vencer con todo respondido se entrega sola (AC-11)")
    @Test
    @Order(14)
    void alVencerConTodoSeEntregaSola() throws Exception {
        entrarALaPrueba("beto", "beto.propia@correo.pe");
        iniciar("beto");
        responder("beto", "unica", "{\"opcionId\":%d}".formatted(opcion.get("unica:Caja")));
        responder("beto", "abiertaA", "{\"texto\":\"No lo encontré\"}");
        responder("beto", "abiertaB", "{\"texto\":\"Le diría que revisé\"}");
        responder("beto", "multiple", "{\"marcadas\":[%d]}".formatted(opcion.get("multiple:SUMA")));
        responder("beto", "escala", "{\"opcionId\":%d}".formatted(opcion.get("escala:Nada")));
        subirTablero("beto");
        vencer("beto");
        reset(cola);
        servicioPrueba.entregarVencidos();
        assertThat(estado("beto")).isEqualTo("PRUEBA_CALIFICANDO");
        assertThat(jdbc.queryForObject("select es_entrega_automatica and not no_completada from intento_prueba "
                + "where postulacion_id = ?", Boolean.class, postulacion.get("beto"))).isTrue();
        // Entregada no es «sin completar»: su proceso no lo dice
        conTokenGet("/api/v1/portal/postulaciones/" + codigo.get("beto"), tokenCandidata.get("beto"))
                .andExpect(jsonPath("$.resumen.pruebaSinCompletar").value(false));
        int guia = jdbc.queryForObject("select version_guia from version_banco where id = ?", Integer.class,
                publicadaId(vacanteId));
        puente.guardarNotasPruebaPropia(postulacion.get("beto"), guia,
                List.of(tanda(nota(criterioConocimiento, "4", "No lo ubicó"))));
    }

    @DisplayName("Al vencer con huecos: «no completada», sin IA, sin ranking, y se cierra desde la lista (AC-11)")
    @Test
    @Order(15)
    void alVencerConHuecosNoSeCompleta() throws Exception {
        entrarALaPrueba("caro", "caro.propia@correo.pe");
        iniciar("caro");
        responder("caro", "unica", "{\"opcionId\":%d}".formatted(opcion.get("unica:Libro diario")));
        vencer("caro");
        reset(cola);
        servicioPrueba.entregarVencidos();

        assertThat(jdbc.queryForObject("select no_completada from intento_prueba where postulacion_id = ?",
                Boolean.class, postulacion.get("caro"))).isTrue();
        assertThat(estado("caro")).isEqualTo("PRUEBA_TURNO_CANDIDATO");
        verify(cola, never()).encolarPruebaPuesto(postulacion.get("caro"));
        assertThat(notaDeLaEtapa("caro")).isNull();
        // El portal lo dice
        conTokenGet("/api/v1/portal/prueba/" + codigo.get("caro"), tokenCandidata.get("caro"))
                .andExpect(jsonPath("$.estadoIntento").value("NO_COMPLETADA"));
        mvc.perform(post("/api/v1/portal/prueba/" + codigo.get("caro") + "/entrega")
                        .header("Authorization", "Bearer " + tokenCandidata.get("caro")))
                .andExpect(status().isConflict());
        // Y su proceso, que sigue en la etapa, ya no le ofrece abrirla: el resumen lo dice
        // (en el detalle y en la lista) sin llevar puntos ni criterios del intento (RF-53)
        conTokenGet("/api/v1/portal/postulaciones/" + codigo.get("caro"), tokenCandidata.get("caro"))
                .andExpect(jsonPath("$.resumen.estado").value("PRUEBA_TURNO_CANDIDATO"))
                .andExpect(jsonPath("$.resumen.pruebaSinCompletar").value(true))
                .andExpect(jsonPath("$.resumen.puntos").doesNotExist())
                .andExpect(jsonPath("$.resumen.criterios").doesNotExist());
        conTokenGet("/api/v1/portal/postulaciones", tokenCandidata.get("caro"))
                .andExpect(jsonPath("$[0].uuid").value(codigo.get("caro")))
                .andExpect(jsonPath("$[0].pruebaSinCompletar").value(true));

        // No sale en el ranking de la prueba; sí en «No completaron», con lo que le faltó
        JsonNode ranking = ranking(vacanteId, "PRUEBA_PUESTO");
        for (JsonNode f : ranking.get("filas")) {
            assertThat(f.get("postulacionId").asLong()).isNotEqualTo(postulacion.get("caro"));
        }
        // El contrato del ranking no cambia de campos: la lista va por su lado
        assertThat(ranking.has("noCompletaron")).isFalse();
        JsonNode lista = noCompletaron();
        assertThat(lista.size()).isEqualTo(1);
        JsonNode noCompleto = lista.get(0);
        assertThat(noCompleto.get("postulacionId").asLong()).isEqualTo(postulacion.get("caro"));
        assertThat(noCompleto.get("queFalto").asText()).isEqualTo("le faltaron 4 preguntas y «Tablero.xlsx»");
        assertThat(noCompleto.get("procesoCerrado").asBoolean()).isFalse();
        // Ni en su Excel
        byte[] libro = excel(vacanteId, "PRUEBA_PUESTO", List.of(postulacion.get("ana"), postulacion.get("caro")));
        assertThat(nombresDelExcel(libro)).noneMatch(n -> n.contains("Caro"));

        // Alguien del equipo cierra su proceso
        conToken(post("/api/v1/panel/postulaciones/" + postulacion.get("caro") + "/transiciones"), tokenTalento,
                "{\"estadoDestino\":\"NO_CONTINUA\",\"motivo\":\"No completó la prueba\",\"avisar\":false}")
                .andExpect(status().isOk());
        assertThat(noCompletaron().get(0).get("procesoCerrado").asBoolean()).isTrue();

        // La ficha dice lo mismo que la lista: qué le faltó, sin nota ni ajuste
        conTokenGet("/api/v1/panel/postulaciones/" + postulacion.get("caro") + "/prueba-propia", tokenTalento)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("NO_COMPLETADA"))
                .andExpect(jsonPath("$.nota").doesNotExist())
                .andExpect(jsonPath("$.puedeAjustar").value(false))
                .andExpect(jsonPath("$.loQueFalta[0]").value("le faltaron 4 preguntas y «Tablero.xlsx»"));
    }

    // ============ Con candidatos dentro ============

    @DisplayName("Desde la primera rendición, el contenido se congela: 409 (AC-07)")
    @Test
    @Order(16)
    void desdeLaPrimeraRendicionSeCongela() throws Exception {
        conToken(post(base(vacanteId) + "/borrador"), tokenTalento, null).andExpect(status().isConflict());
        conToken(post(base(vacanteId) + "/criterios"), tokenTalento, "{\"nombre\":\"Nuevo\",\"puntos\":0}")
                .andExpect(status().isConflict());
    }

    @DisplayName("Cambiar los puntos: la parte calificada se escala, las cerradas se recalculan, sin IA ni etapas (AC-19)")
    @Test
    @Order(17)
    void cambiarLosPuntos() throws Exception {
        reset(cola);
        JsonNode publicada = editor(vacanteId).get("publicada");
        JsonNode multiple = buscarPregunta(publicada, "multiple");
        String opciones = "[{\"id\":%d,\"puntos\":20},{\"id\":%d,\"puntos\":10},{\"id\":%d,\"puntos\":-5}]".formatted(
                opcion.get("multiple:BUSCARV"), opcion.get("multiple:XLOOKUP"), opcion.get("multiple:SUMA"));
        conToken(put(base(vacanteId) + "/publicada/puntos"), tokenTalento, """
                {"preguntas":[{"id":%d,"puntos":30,"opciones":%s}],
                 "criterios":[{"id":%d,"puntos":25}]}"""
                .formatted(multiple.get("id").asLong(), opciones, criterioConocimiento))
                .andExpect(status().isOk());
        // Beto tenía 4 de 20 en Conocimiento: ahora 3 de 15. Ana, ajustada a 12, pasa a 9.
        assertThat(jdbc.queryForObject("select n.puntaje from nota_criterio_prueba n join intento_prueba i "
                        + "on i.id = n.intento_prueba_id where i.postulacion_id = ? and n.criterio_banco_id = ?",
                BigDecimal.class, postulacion.get("ana"), criterioConocimiento)).isEqualByComparingTo("9");
        assertThat(jdbc.queryForObject("select n.puntaje from nota_criterio_prueba n join intento_prueba i "
                        + "on i.id = n.intento_prueba_id where i.postulacion_id = ? and n.criterio_banco_id = ?",
                BigDecimal.class, postulacion.get("beto"), criterioConocimiento)).isEqualByComparingTo("3");
        // Ana: Conocimiento 10 + 9, Excel 30 + 25 (marcó BUSCARV y XLOOKUP), Comunicación 15
        assertThat(notaDeLaEtapa("ana")).isEqualByComparingTo("89");
        assertThat(estado("ana")).isEqualTo("PRUEBA_POR_CONFIRMAR");
        assertThat(estado("beto")).isEqualTo("PRUEBA_CALIFICANDO");
        verify(cola, never()).encolarPruebaPuesto(anyLong());
        verify(cola, never()).recalificarPrueba(anyLong());

        // El total sigue en 100: si no, 400 con la lista y no se cambia nada
        conToken(put(base(vacanteId) + "/publicada/puntos"), tokenTalento, """
                {"criterios":[{"id":%d,"puntos":26}]}""".formatted(criterioConocimiento))
                .andExpect(status().isBadRequest());

        // QA-11: un total mal escrito dice su falta; la suma, si se da, es la de lo escrito
        // (150 + los 75 de los demás), nunca una que lo deja fuera.
        List<String> fueraDeRango = faltasAlCambiarLosPuntos("""
                {"criterios":[{"id":%d,"puntos":150}]}""".formatted(criterioConocimiento));
        assertThat(fueraDeRango).hasSize(2);
        assertThat(fueraDeRango).contains("Los puntos suman 225 de 100: sobran 125.");
        assertThat(fueraDeRango).anyMatch(f -> f.endsWith(": Los puntos del criterio van de 0 a 100."));
        List<String> conDecimales = faltasAlCambiarLosPuntos("""
                {"criterios":[{"id":%d,"puntos":25.5}]}""".formatted(criterioConocimiento));
        assertThat(conDecimales).singleElement().asString()
                .endsWith(": Los puntos del criterio tienen que ser un número entero, sin decimales.");
    }

    private List<String> faltasAlCambiarLosPuntos(String cuerpo) throws Exception {
        JsonNode error = json.readTree(conToken(put(base(vacanteId) + "/publicada/puntos"), tokenTalento, cuerpo)
                .andExpect(status().isBadRequest()).andReturn().getResponse().getContentAsString());
        List<String> faltas = new ArrayList<>();
        error.get("faltas").forEach(f -> faltas.add(f.asText()));
        return faltas;
    }

    @DisplayName("Corregir la guía: sin saldo no se guarda; con IA, recalifica solo a quien tiene nota de la IA (AC-18, AC-24)")
    @Test
    @Order(18)
    void corregirLaGuia() throws Exception {
        long publicada = publicadaId(vacanteId);
        int antes = jdbc.queryForObject("select version_guia from version_banco where id = ?", Integer.class, publicada);
        // La IA está apagada: 409 y no se guarda nada
        conToken(put(base(vacanteId) + "/publicada/instrucciones"), tokenTalento,
                "{\"guiaCalificacion\":\"Mira sobre todo el asiento de ajuste\"}")
                .andExpect(status().isConflict());
        assertThat(jdbc.queryForObject("select version_guia from version_banco where id = ?", Integer.class, publicada))
                .isEqualTo(antes);

        doReturn(null).when(cola).porQueNoSePuedeUsarLaIa(anyLong());
        doReturn(true).when(cola).recalificarPrueba(anyLong());
        JsonNode cambio = json.readTree(conToken(put(base(vacanteId) + "/publicada/instrucciones"), tokenTalento,
                        "{\"guiaCalificacion\":\"Mira sobre todo el asiento de ajuste\"}")
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        // Solo Beto: la de Ana en el criterio de IA está ajustada a mano
        assertThat(cambio.get("personas").asInt()).isEqualTo(1);
        verify(cola).recalificarPrueba(postulacion.get("beto"));
        verify(cola, never()).recalificarPrueba(postulacion.get("ana"));
        assertThat(jdbc.queryForObject("select version_guia from version_banco where id = ?", Integer.class, publicada))
                .isEqualTo(antes + 1);
        assertThat(estado("beto")).isEqualTo("PRUEBA_CALIFICANDO");

        // Mientras dura, el ranking marca «Recalificando» en su fila
        doReturn(Map.of(postulacion.get("beto"), new ColaCalificacionIa.Seguimiento("EN_CURSO", null)))
                .when(cola).recalificacionDePrueba(anyList());
        JsonNode filaBeto = filaDelRanking(vacanteId, "PRUEBA_PUESTO", postulacion.get("beto"));
        assertThat(filaBeto.get("recalificando").asBoolean()).isTrue();
        assertThat(filaDelRanking(vacanteId, "PRUEBA_PUESTO", postulacion.get("ana"))
                .get("recalificando").asBoolean()).isFalse();
        reset(cola);
    }

    // ============ Copiar ============

    @DisplayName("Otra vacante copia la prueba con su caso, tiempo y entregables con su alcance, y no la fecha (AC-14, AC-20)")
    @Test
    @Order(19)
    void otraVacanteCopiaLaPrueba() throws Exception {
        long otra = crearVacante("Asistente contable de sede");
        JsonNode copiables = json.readTree(conTokenGet(base(otra) + "/copiables", tokenTalento)
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        assertThat(copiables.toString()).contains("\"vacanteId\":" + vacanteId);
        JsonNode vista = json.readTree(conTokenGet(base(otra) + "/copiables/" + vacanteId, tokenTalento)
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        assertThat(vista.at("/prueba/enunciado").asText()).contains("descuadre");
        assertThat(vista.at("/prueba/entregables").size()).isEqualTo(2);
        // La vista previa enseña el alcance de cada entregable
        List<String> alcances = new ArrayList<>();
        vista.at("/prueba/entregables").forEach(e -> alcances.add(e.get("alcance").asText()));
        assertThat(alcances).containsExactlyInAnyOrder("PREGUNTA", "PREGUNTAS");

        JsonNode copiado = json.readTree(conToken(post(base(otra) + "/copia"), tokenTalento,
                        "{\"vacanteOrigenId\":%d}".formatted(vacanteId))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        JsonNode borrador = copiado.get("borrador");
        assertThat(borrador.at("/prueba/enunciado").asText()).contains("descuadre");
        assertThat(borrador.at("/prueba/modalidad").asText()).isEqualTo("CRONOMETRADA");
        assertThat(borrador.at("/prueba/duracionMinutos").asInt()).isEqualTo(90);
        assertThat(borrador.at("/prueba/entregables").size()).isEqualTo(2);
        assertThat(borrador.get("total").asInt()).isEqualTo(100);
        JsonNode comunicacionCopia = null;
        for (JsonNode c : borrador.get("criterios")) {
            if ("Comunicación".equals(c.get("nombre").asText())) comunicacionCopia = c;
        }
        assertThat(comunicacionCopia).isNotNull();
        assertThat(comunicacionCopia.get("id").asLong()).isNotEqualTo(criterioComunicacion);
        assertThat(comunicacionCopia.get("entregables").size()).isEqualTo(1);
        assertThat(comunicacionCopia.get("calificador").asText()).isEqualTo("PERSONA");
        // El alcance viaja con las preguntas de la copia; la fecha límite es de la vacante y no
        assertThat(copiado.at("/fechaLimite/cierraEn").isMissingNode()
                || copiado.at("/fechaLimite/cierraEn").isNull()).isTrue();
        for (JsonNode e : borrador.at("/prueba/entregables")) {
            if ("PREGUNTA".equals(e.get("alcance").asText())) {
                assertThat(e.get("preguntaId").asLong()).isNotEqualTo(pregunta.get("abiertaA"));
            } else {
                assertThat(e.get("cubre").size()).isEqualTo(1);
            }
        }
        long criterioCopiado = comunicacionCopia.get("id").asLong();

        // AC-14 (dos versiones, ids distintos): la nota de un criterio de la otra vacante no
        // entra en la prueba de Ana
        int guia = jdbc.queryForObject("select version_guia from version_banco where id = ?", Integer.class,
                publicadaId(vacanteId));
        int antes = contar("select count(*) from nota_criterio_prueba");
        puente.guardarNotasPruebaPropia(postulacion.get("beto"), guia,
                List.of(tanda(nota(criterioCopiado, "5", "De otra vacante"))));
        assertThat(contar("select count(*) from nota_criterio_prueba")).isEqualTo(antes);

        // Copiar desde otra empresa: 404, y no se crea nada
        jdbc.update("insert into organizacion (codigo, nombre) values ('OTRA_PP', 'Otra')");
        long otraEmpresa = jdbc.queryForObject("select id from organizacion where codigo = 'OTRA_PP'", Long.class);
        jdbc.update("update vacante set organizacion_id = ? where id = ?", otraEmpresa, vacanteId);
        try {
            conToken(post(base(otra) + "/copia"), tokenTalento, "{\"vacanteOrigenId\":%d}".formatted(vacanteId))
                    .andExpect(status().isNotFound());
            conTokenGet(base(otra) + "/copiables/" + vacanteId, tokenTalento).andExpect(status().isNotFound());
        } finally {
            jdbc.update("update vacante set organizacion_id = 1 where id = ?", vacanteId);
        }
    }

    @DisplayName("Dos vacantes con un «Comunicación» cada una: columnas por id que no se juntan (AC-22)")
    @Test
    @Order(20)
    void columnasPorIdNoSeJuntan() throws Exception {
        JsonNode fila = filaDelRanking(vacanteId, "PRUEBA_PUESTO", postulacion.get("ana"));
        List<String> claves = new ArrayList<>();
        fila.get("notasCriterio").forEach(n -> claves.add(n.get("clave").asText()));
        assertThat(claves).containsExactlyInAnyOrder("prueba:" + criterioConocimiento,
                "prueba:" + criterioExcel, "prueba:" + criterioComunicacion);
        // El Excel empareja por id: la columna de Comunicación lleva el 15 de Ana
        byte[] libro = excel(vacanteId, "PRUEBA_PUESTO", List.of(postulacion.get("ana"), postulacion.get("beto")));
        try (XSSFWorkbook hoja = new XSSFWorkbook(new ByteArrayInputStream(libro))) {
            Row cabecera = hoja.getSheetAt(0).getRow(0);
            int columna = -1;
            for (int c = 0; c < cabecera.getLastCellNum(); c++) {
                if (cabecera.getCell(c).getStringCellValue().startsWith("Comunicación")) columna = c;
            }
            assertThat(columna).isPositive();
            assertThat(hoja.getSheetAt(0).getRow(1).getCell(columna).getNumericCellValue()).isEqualTo(15.0);
        }
    }

    // ============ Recomendaciones ============

    @DisplayName("Las recomendaciones de la prueba y las del banco no se mezclan (AC-21)")
    @Test
    @Order(21)
    void lasRecomendacionesNoSeMezclan() throws Exception {
        long otra = crearVacante("Contador junior");
        // Con la IA apagada no se encola, y se dice
        conToken(post(base(otra) + "/recomendaciones"), tokenTalento, "{}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.encolada").value(false));

        // Una del banco pedida y otra de la prueba pedida en la misma vacante
        propuestas.save(PropuestaPreguntas.builder().organizacionId(1L).vacanteId(otra)
                .puntosQueFaltan(100).estado(PropuestaPreguntas.PEDIDA).creadoEn(java.time.Instant.now()).build());
        propuestas.save(PropuestaPreguntas.builder().organizacionId(1L).vacanteId(otra)
                .proposito(PropuestaPreguntas.PRUEBA_PUESTO).puntosQueFaltan(100)
                .estado(PropuestaPreguntas.PEDIDA).creadoEn(java.time.Instant.now()).build());
        assertThat(puenteRecomendador.insumo(otra)).isNotNull();
        assertThat(puenteRecomendador.insumoPrueba(otra)).isNotNull();
        assertThat(puenteRecomendador.insumoPrueba(otra).puntosQueFaltan()).isEqualTo(100);

        puenteRecomendador.guardarPropuestaPrueba(otra, new ResultadoRecomendadorPrueba(
                new CasoPropuesto("Un cierre con descuadre", null, "Excel"),
                List.of(new EntregablePropuesto("Conciliación", "La hoja", "ARCHIVO", true, "El descuadre")),
                List.of(new CriterioPropuesto(null, "Cierre", "Cierra el mes",
                        List.of(new PreguntaPropuesta("ABIERTA", "¿Qué revisas primero?", BigDecimal.ZERO,
                                "La cuenta", List.of())),
                        null, "IA", List.of(0), List.of(), new BigDecimal("100")))));
        // La de la prueba está lista; la del banco sigue pedida, sin tocar
        conTokenGet(base(otra) + "/recomendaciones", tokenTalento)
                .andExpect(jsonPath("$.estado").value("LISTA"))
                .andExpect(jsonPath("$.caso.enunciado").value("Un cierre con descuadre"))
                .andExpect(jsonPath("$.entregables[0].nombre").value("Conciliación"));
        conTokenGet("/api/v1/panel/vacantes/" + otra + "/preguntas-propias/recomendaciones", tokenTalento)
                .andExpect(jsonPath("$.estado").value("FALLIDA"));
        assertThat(puenteRecomendador.insumo(otra)).isNotNull();

        // Agregar el criterio entero trae su entregable, su parte calificada y lo que mira
        long propuestaId = jdbc.queryForObject("select id from propuesta_preguntas where vacante_id = ? "
                + "and proposito = 'PRUEBA_PUESTO'", Long.class, otra);
        JsonNode editor = json.readTree(conToken(post(base(otra) + "/recomendaciones/" + propuestaId + "/agregados"),
                        tokenTalento, "{\"caso\":true,\"criterios\":[0]}")
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        JsonNode borrador = editor.get("borrador");
        assertThat(borrador.at("/prueba/enunciado").asText()).isEqualTo("Un cierre con descuadre");
        assertThat(borrador.at("/prueba/entregables").size()).isEqualTo(1);
        assertThat(borrador.at("/criterios/0/puntos").asInt()).isEqualTo(100);
        assertThat(borrador.at("/criterios/0/puntosCalificados").asInt()).isEqualTo(100);
        assertThat(borrador.at("/criterios/0/entregables").size()).isEqualTo(1);
        // Lo guardado dice lo que vale cada criterio nuevo (V69)
        assertThat(jdbc.queryForObject("select puntos_del_criterio from criterio_banco where id = ?",
                Integer.class, borrador.at("/criterios/0/id").asLong())).isEqualTo(100);
    }

    // ============ Lo de siempre ============

    @DisplayName("El instrumento no saca a una vacante nueva del editor (400); asignarle una plantilla sí la pasa a PLANTILLA, que sigue igual (AC-02, AC-27)")
    @Test
    @Order(22)
    void laPlantillaSigueIgual() throws Exception {
        long otra = crearVacante("Cajero de sede");
        String instrumento = "/api/v1/panel/vacantes/" + otra + "/instrumento-tecnico";

        // AC-02: una vacante nueva no acaba rindiendo una plantilla ni el cuestionario
        // CAZATALENTOS por la API, y la bandera no se mueve.
        for (String viejo : List.of("PLANTILLA", "CUESTIONARIO_TECNICO")) {
            conToken(post(instrumento), tokenTalento,
                    "{\"instrumento\": \"%s\", \"minutos\": 45}".formatted(viejo))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.detail").value(
                            org.hamcrest.Matchers.containsString("«Armar la prueba»")));
        }
        // Pedir la suya otra vez es inocuo.
        conToken(post(instrumento), tokenTalento, "{\"instrumento\": \"PRUEBA_PROPIA\"}")
                .andExpect(status().isOk());
        conTokenGet("/api/v1/panel/vacantes/" + otra, tokenTalento)
                .andExpect(jsonPath("$.instrumentoEtapaTecnica").value("PRUEBA_PROPIA"))
                .andExpect(jsonPath("$.minutosEtapaTecnica").doesNotExist());

        // La salida que queda, para los guiones de las vacantes que ya existen: asignarle una
        // plantilla mientras nadie haya rendido.
        long version = unaPruebaPublicada();
        conToken(post("/api/v1/panel/vacantes/" + otra + "/plantilla-prueba"), tokenTalento,
                "{\"versionPlantillaPruebaId\": %d}".formatted(version)).andExpect(status().isOk());
        conTokenGet("/api/v1/panel/vacantes/" + otra, tokenTalento)
                .andExpect(jsonPath("$.instrumentoEtapaTecnica").value("PLANTILLA"));

        // Ya como una de las de antes, sigue entre sus dos instrumentos de siempre, y no
        // vuelve a la prueba del editor por aquí.
        conToken(post(instrumento), tokenTalento, "{\"instrumento\": \"PRUEBA_PROPIA\"}")
                .andExpect(status().isBadRequest());
        conToken(post(instrumento), tokenTalento,
                "{\"instrumento\": \"CUESTIONARIO_TECNICO\", \"minutos\": 45}").andExpect(status().isOk());
        conToken(post(instrumento), tokenTalento, "{\"instrumento\": \"PLANTILLA\"}")
                .andExpect(status().isOk());
        conTokenGet("/api/v1/panel/vacantes/" + otra, tokenTalento)
                .andExpect(jsonPath("$.instrumentoEtapaTecnica").value("PLANTILLA"));
    }

    @DisplayName("Perfil Integral: una columna por criterio del banco, «pendiente» si falta una abierta, también en el Excel (AC-23)")
    @Test
    @Order(23)
    void columnasDelBancoEnElPerfilIntegral() throws Exception {
        long otra = crearVacante("Analista de caja");
        String propias = "/api/v1/panel/vacantes/" + otra + "/preguntas-propias";
        JsonNode conCriterio = json.readTree(conToken(post(propias + "/criterios"), tokenTalento,
                        "{\"nombre\":\"Caja\"}")
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        long caja = conCriterio.at("/borrador/criterios/0/id").asLong();
        conToken(post(propias + "/preguntas"), tokenTalento, """
                {"tipo":"OPCION_UNICA","enunciado":"¿Qué haces si falta plata?","puntos":60,"criterioId":%d,
                 "opciones":[{"texto":"Lo reporto","puntos":60},{"texto":"Lo pongo yo","puntos":0}]}"""
                .formatted(caja)).andExpect(status().isOk());
        conToken(post(propias + "/preguntas"), tokenTalento, """
                {"tipo":"ABIERTA","enunciado":"Cuéntanos un arqueo difícil","puntos":40,"criterioId":%d,
                 "queDebeTener":"El monto"}""".formatted(caja)).andExpect(status().isOk());
        conToken(post(propias + "/publicacion"), tokenTalento, null).andExpect(status().isOk());
        // Y su prueba técnica, copiada de la otra vacante
        conToken(post(base(otra) + "/copia"), tokenTalento, "{\"vacanteOrigenId\":%d}".formatted(vacanteId))
                .andExpect(status().isOk());
        // La fecha límite no viaja con la copia (AC-14): sin ella no se publica, y se pone aquí
        conToken(post(base(otra) + "/publicacion"), tokenTalento, null)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.faltas.length()").value(1))
                .andExpect(jsonPath("$.faltas[0]").value(org.hamcrest.Matchers.containsString("Falta la fecha límite")));
        respuesta(conToken(put(base(otra) + "/fecha-limite"), tokenTalento, "{\"cierraEn\":\"%s\"}".formatted(
                java.time.Instant.now().plus(java.time.Duration.ofDays(20))
                        .truncatedTo(java.time.temporal.ChronoUnit.SECONDS))));
        conToken(post(base(otra) + "/publicacion"), tokenTalento, null).andExpect(status().isOk());
        conToken(post("/api/v1/panel/vacantes/" + otra + "/publicacion"), tokenTalento, null)
                .andExpect(status().isOk());

        mvc.perform(post("/api/v1/portal/cuentas").contentType(MediaType.APPLICATION_JSON).content("""
                {"nombre":"Dora","apellidos":"Prueba","correo":"dora.propia@correo.pe",
                 "contrasena":"unaClaveLarga123","ciudadUbigeo":"1501","aceptaPlataforma":true,
                 "aceptaFuturosContactos":false}""")).andExpect(status().isCreated());
        String token = leer(mvc.perform(post("/api/v1/portal/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"correo\":\"dora.propia@correo.pe\",\"contrasena\":\"unaClaveLarga123\"}"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString(), "token");
        String suCodigo = leer(mvc.perform(multipart("/api/v1/portal/postulaciones")
                        .file(new MockMultipartFile("cv", "cv.pdf", "application/pdf", "x".getBytes()))
                        .param("vacanteId", String.valueOf(otra))
                        .param("resultadoOrgulloso", "Cuadré la caja de tres sedes")
                        .param("aceptaTratamiento", "true")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString(), "codigo");
        long dora = jdbc.queryForObject("select id from postulacion where uuid = ?::uuid", Long.class, suCodigo);
        JsonNode examen = json.readTree(mvc.perform(post("/api/v1/portal/evaluacion/" + suCodigo + "/inicio")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        for (JsonNode p : examen.get("preguntas")) {
            String cuerpo = "ABIERTA".equals(p.get("tipo").asText())
                    ? "{\"texto\":\"Faltaban 300 soles y lo hallé\"}"
                    : "{\"opcionId\":%d}".formatted(p.get("opciones").get(0).get("id").asLong());
            mvc.perform(put("/api/v1/portal/evaluacion/" + suCodigo + "/respuestas/" + p.get("id").asLong())
                            .header("Authorization", "Bearer " + token)
                            .contentType(MediaType.APPLICATION_JSON).content(cuerpo))
                    .andExpect(status().isOk());
        }
        mvc.perform(post("/api/v1/portal/evaluacion/" + suCodigo + "/entrega")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());

        JsonNode fila = filaDelRanking(otra, "PERFIL_INTEGRAL", dora);
        JsonNode columna = notaDeLaFila(fila, "banco:" + caja);
        assertThat(columna.get("criterio").asText()).isEqualTo("Caja");
        assertThat(columna.get("estado").asText()).isEqualTo("PENDIENTE");
        assertThat(columna.get("puntaje").isNull()).isTrue();
        assertThat(columna.get("maximo").asInt()).isEqualTo(100);

        byte[] libro = excel(otra, "PERFIL_INTEGRAL", List.of(dora));
        try (XSSFWorkbook hoja = new XSSFWorkbook(new ByteArrayInputStream(libro))) {
            Row cabecera = hoja.getSheetAt(0).getRow(0);
            int col = -1;
            for (int c = 0; c < cabecera.getLastCellNum(); c++) {
                if (cabecera.getCell(c).getStringCellValue().startsWith("Caja")) col = c;
            }
            assertThat(col).isPositive();
            assertThat(hoja.getSheetAt(0).getRow(1).getCell(col).getStringCellValue()).isEqualTo("pendiente");
        }
    }

    // ============ Apoyo ============

    private void entrarALaPrueba(String quien, String correo) throws Exception {
        mvc.perform(post("/api/v1/portal/cuentas").contentType(MediaType.APPLICATION_JSON).content("""
                {"nombre":"%s","apellidos":"Prueba","correo":"%s",
                 "contrasena":"unaClaveLarga123","ciudadUbigeo":"1501","aceptaPlataforma":true,
                 "aceptaFuturosContactos":false}""".formatted(capital(quien), correo)))
                .andExpect(status().isCreated());
        String token = leer(mvc.perform(post("/api/v1/portal/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"correo\":\"%s\",\"contrasena\":\"unaClaveLarga123\"}".formatted(correo)))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString(), "token");
        tokenCandidata.put(quien, token);
        MockMultipartFile cv = new MockMultipartFile("cv", "cv.pdf", "application/pdf", "contenido".getBytes());
        String suCodigo = leer(mvc.perform(multipart("/api/v1/portal/postulaciones").file(cv)
                        .param("vacanteId", String.valueOf(vacanteId))
                        .param("resultadoOrgulloso", "Cerré tres años sin descuadres")
                        .param("aceptaTratamiento", "true")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString(), "codigo");
        codigo.put(quien, suCodigo);
        long id = jdbc.queryForObject("select id from postulacion where uuid = ?::uuid", Long.class, suCodigo);
        postulacion.put(quien, id);
        assertThat(estado(quien)).isEqualTo("PERFIL_POR_CONFIRMAR");
        conToken(post("/api/v1/panel/postulaciones/" + id + "/confirmacion-avance"), tokenTalento,
                "{\"motivo\":\"Pasa a la prueba\"}").andExpect(status().isOk());
        assertThat(estado(quien)).isEqualTo("PRUEBA_TURNO_CANDIDATO");
        assertThat(jdbc.queryForObject("select version_banco_id from intento_prueba where postulacion_id = ?",
                Long.class, id)).isEqualTo(publicadaId(vacanteId));
    }

    private void iniciar(String quien) throws Exception {
        mvc.perform(post("/api/v1/portal/prueba/" + codigo.get(quien) + "/inicio")
                        .header("Authorization", "Bearer " + tokenCandidata.get(quien)))
                .andExpect(status().isOk());
    }

    private void responder(String quien, String cual, String cuerpo) throws Exception {
        mvc.perform(put("/api/v1/portal/prueba/" + codigo.get(quien) + "/respuestas/" + pregunta.get(cual))
                        .header("Authorization", "Bearer " + tokenCandidata.get(quien))
                        .contentType(MediaType.APPLICATION_JSON).content(cuerpo))
                .andExpect(status().isOk());
    }

    private void subirTablero(String quien) throws Exception {
        MockMultipartFile tablero = new MockMultipartFile("archivo", "tablero.pdf", "application/pdf",
                "la conciliacion".getBytes());
        mvc.perform(multipart("/api/v1/portal/prueba/" + codigo.get(quien) + "/entregables/" + entregableTablero
                        + "/archivo").file(tablero).header("Authorization", "Bearer " + tokenCandidata.get(quien)))
                .andExpect(status().isOk());
    }

    private void vencer(String quien) {
        jdbc.update("update intento_prueba set vence_en = now() - interval '1 minute' where postulacion_id = ?",
                postulacion.get(quien));
    }

    private JsonNode pruebaDe(String quien) throws Exception {
        return json.readTree(conTokenGet("/api/v1/panel/postulaciones/" + postulacion.get(quien) + "/prueba-propia",
                tokenTalento).andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
    }

    private static JsonNode criterioDe(JsonNode prueba, long criterioId) {
        for (JsonNode c : prueba.get("criterios")) {
            if (c.get("criterioId").asLong() == criterioId) return c;
        }
        throw new AssertionError("No está el criterio " + criterioId);
    }

    private JsonNode noCompletaron() throws Exception {
        return json.readTree(conTokenGet(base(vacanteId) + "/no-completaron", tokenTalento)
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
    }

    private JsonNode ranking(long vacante, String etapa) throws Exception {
        return json.readTree(conTokenGet("/api/v1/panel/vacantes/" + vacante + "/ranking?etapa=" + etapa,
                tokenTalento).andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
    }

    private JsonNode filaDelRanking(long vacante, String etapa, long postulacionId) throws Exception {
        for (JsonNode f : ranking(vacante, etapa).get("filas")) {
            if (f.get("postulacionId").asLong() == postulacionId) return f;
        }
        throw new AssertionError("La postulación " + postulacionId + " no está en el ranking");
    }

    private static JsonNode notaDeLaFila(JsonNode fila, String clave) {
        for (JsonNode n : fila.get("notasCriterio")) {
            if (clave.equals(n.get("clave").asText())) return n;
        }
        throw new AssertionError("La fila no tiene la columna " + clave);
    }

    private byte[] excel(long vacante, String etapa, List<Long> ids) throws Exception {
        return conToken(post("/api/v1/panel/vacantes/" + vacante + "/ranking/excel"), tokenTalento,
                json.writeValueAsString(Map.of("etapa", etapa, "postulacionIds", ids)))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsByteArray();
    }

    private static List<String> nombresDelExcel(byte[] libro) throws Exception {
        List<String> nombres = new ArrayList<>();
        try (XSSFWorkbook hoja = new XSSFWorkbook(new ByteArrayInputStream(libro))) {
            for (int r = 1; r <= hoja.getSheetAt(0).getLastRowNum(); r++) {
                Row fila = hoja.getSheetAt(0).getRow(r);
                if (fila != null && fila.getCell(1) != null) nombres.add(fila.getCell(1).toString());
            }
        }
        return nombres;
    }

    private String ajuste(String quien, long criterioId) {
        return "/api/v1/panel/postulaciones/" + postulacion.get(quien) + "/prueba-propia/criterios/"
                + criterioId + "/nota";
    }

    private BigDecimal notaDeLaEtapa(String quien) {
        List<BigDecimal> filas = jdbc.queryForList("select puntaje from nota_etapa where postulacion_id = ? "
                + "and etapa_codigo = 'PRUEBA_PUESTO'", BigDecimal.class, postulacion.get(quien));
        return filas.isEmpty() ? null : filas.get(0);
    }

    private String estado(String quien) {
        return jdbc.queryForObject("select estado_codigo from postulacion where id = ?", String.class,
                postulacion.get(quien));
    }

    private long publicadaId(long vacante) {
        return jdbc.queryForObject("select id from version_banco where vacante_id = ? and proposito = "
                + "'PRUEBA_PUESTO' and estado = 'PUBLICADA'", Long.class, vacante);
    }

    private int contar(String sql) {
        Integer valor = jdbc.queryForObject(sql, Integer.class);
        return valor == null ? 0 : valor;
    }

    private static TandaCalificada tanda(NotaCriterioPropiaIa... notas) {
        return new TandaCalificada(null, new ResultadoPruebaPropia(List.of(notas), new BigDecimal("80")));
    }

    private static NotaCriterioPropiaIa nota(Long criterioId, String puntaje, String explicacion) {
        return new NotaCriterioPropiaIa(criterioId, new BigDecimal(puntaje), explicacion, "cita");
    }

    @DisplayName("El taller del borrador: el adjunto, los entregables con su alcance, los criterios y las preguntas se cambian, se mueven y se quitan")
    @Test
    @Order(24)
    void elTallerDelBorrador() throws Exception {
        long taller = crearVacante("Auxiliar de caja");
        // «Sin cronómetro»: sin días (AC-12)
        JsonNode editor = respuesta(conToken(put(base(taller) + "/borrador"), tokenTalento, """
                {"guiaCalificacion":"Mide el cuadre","enunciado":"Cuadra la caja del día",
                 "modalidad":"PLAZO_ABIERTO"}"""));
        assertThat(editor.at("/borrador/prueba/modalidad").asText()).isEqualTo("PLAZO_ABIERTO");
        assertThat(editor.at("/borrador/prueba/plazoDias").isNull()).isTrue();
        assertThat(editor.at("/resumen/estado").asText()).isEqualTo("BORRADOR");
        assertThat(editor.at("/resumen/dias").isNull()).isTrue();

        // El enunciado en PDF entra y sale del borrador
        MockMultipartFile pdf = new MockMultipartFile("archivo", "caso.pdf", "application/pdf",
                "%PDF-1.4".getBytes());
        editor = respuesta(mvc.perform(multipart(base(taller) + "/borrador/consigna").file(pdf)
                .header("Authorization", "Bearer " + tokenTalento)));
        assertThat(editor.at("/borrador/prueba/consigna/nombre").asText()).isEqualTo("caso.pdf");
        editor = respuesta(conToken(delete(base(taller) + "/borrador/consigna"), tokenTalento, null));
        assertThat(editor.at("/borrador/prueba/consigna").isNull()
                || editor.at("/borrador/prueba/consigna").isMissingNode()).isTrue();

        // Dos generales de toda la prueba: el segundo se cambia y sube al primer puesto
        long tablero = idDelEntregable(conToken(post(base(taller) + "/entregables"), tokenTalento, """
                {"nombre":"Tablero","detalle":"La hoja del cuadre","formato":"ARCHIVO","obligatorio":true,
                 "todaLaPrueba":true}"""), "Tablero");
        long video = idDelEntregable(conToken(post(base(taller) + "/entregables"), tokenTalento, """
                {"nombre":"Video","formato":"ENLACE","obligatorio":true,"todaLaPrueba":true}"""), "Video");
        editor = respuesta(conToken(put(base(taller) + "/entregables/" + video), tokenTalento, """
                {"nombre":"Video corto","formato":"ENLACE","obligatorio":false,"queDebeTener":"Que se oiga",
                 "todaLaPrueba":true}"""));
        assertThat(editor.at("/borrador/prueba/entregables/1/nombre").asText()).isEqualTo("Video corto");
        assertThat(editor.at("/borrador/prueba/entregables/1/obligatorio").asBoolean()).isFalse();
        assertThat(editor.at("/borrador/prueba/entregables/1/alcance").asText()).isEqualTo("TODA_LA_PRUEBA");
        editor = respuesta(conToken(post(base(taller) + "/entregables/" + video + "/movimiento"), tokenTalento,
                "{\"direccion\":\"ARRIBA\"}"));
        assertThat(editor.at("/borrador/prueba/entregables/0/id").asLong()).isEqualTo(video);

        // Dos criterios; «Orden» sube al primer puesto. Los dos miran los generales de toda la prueba
        long caja = idDelCriterio(conToken(post(base(taller) + "/criterios"), tokenTalento, """
                {"nombre":"Caja","puntos":40,"calificador":"IA"}"""), "Caja");
        long orden = idDelCriterio(conToken(post(base(taller) + "/criterios"), tokenTalento,
                "{\"nombre\":\"Orden\",\"puntos\":0}"), "Orden");
        editor = respuesta(conToken(post(base(taller) + "/criterios/" + orden + "/movimiento"), tokenTalento,
                "{\"direccion\":\"ARRIBA\"}"));
        assertThat(editor.at("/borrador/criterios/0/id").asLong()).isEqualTo(orden);
        assertThat(buscarCriterio(editor.get("borrador"), orden).get("entregables").size()).isEqualTo(2);

        // Dos cerradas en «Caja»: la segunda sube; la primera pasa a «Orden»
        String cerrada = """
                {"tipo":"OPCION_UNICA","enunciado":"%s","puntos":30,"criterioId":%d,
                 "opciones":[{"texto":"Sí","puntos":30},{"texto":"No","puntos":0}]}""";
        respuesta(conToken(post(base(taller) + "/preguntas"), tokenTalento, cerrada.formatted("¿Arqueo?", caja)));
        editor = respuesta(conToken(post(base(taller) + "/preguntas"), tokenTalento,
                cerrada.formatted("¿Vuelto?", caja)));
        JsonNode deCaja = buscarCriterio(editor.get("borrador"), caja).get("preguntas");
        long arqueo = deCaja.get(0).get("id").asLong();
        long vuelto = deCaja.get(1).get("id").asLong();
        // El total se mantiene (V69): «Caja» sigue valiendo 40 con 60 de cerradas dentro; su parte
        // calificada queda en 0 y es una falta, también al intentar publicar.
        assertThat(buscarCriterio(editor.get("borrador"), caja).get("puntos").asInt()).isEqualTo(40);
        assertThat(buscarCriterio(editor.get("borrador"), caja).get("puntosSistema").asInt()).isEqualTo(60);
        assertThat(buscarCriterio(editor.get("borrador"), caja).get("puntosCalificados").asInt()).isZero();
        String porEncima = "Las cerradas de «Caja» suman 60 y el criterio vale 40.";
        assertThat(editor.at("/borrador/avisos").toString()).contains(porEncima);
        JsonNode alPublicar = json.readTree(conToken(post(base(taller) + "/publicacion"), tokenTalento, null)
                .andExpect(status().isBadRequest()).andReturn().getResponse().getContentAsString());
        assertThat(alPublicar.get("faltas").toString()).contains(porEncima);
        editor = respuesta(conToken(post(base(taller) + "/preguntas/" + vuelto + "/movimiento"), tokenTalento,
                "{\"direccion\":\"ARRIBA\"}"));
        assertThat(buscarCriterio(editor.get("borrador"), caja).at("/preguntas/0/id").asLong()).isEqualTo(vuelto);

        // Un archivo pedido desde «¿Vuelto?»: lo mira «Caja», sin marcar nada
        long recibo = idDelEntregable(conToken(post(base(taller) + "/entregables"), tokenTalento, """
                {"nombre":"Recibo","formato":"ARCHIVO","obligatorio":true,"preguntaId":%d}""".formatted(vuelto)),
                "Recibo");
        // El tablero pasa a cubrir solo «¿Arqueo?»
        respuesta(conToken(put(base(taller) + "/entregables/" + tablero), tokenTalento, """
                {"nombre":"Tablero","formato":"ARCHIVO","obligatorio":true,"cubre":[%d]}""".formatted(arqueo)));
        // Mover «¿Arqueo?» de criterio mueve con ella lo que mira cada uno
        editor = respuesta(conToken(put(base(taller) + "/preguntas/" + arqueo), tokenTalento, """
                {"tipo":"OPCION_UNICA","enunciado":"¿Arqueo diario?","puntos":20,"criterioId":%d,
                 "opciones":[{"texto":"Sí","puntos":20},{"texto":"No","puntos":0}]}""".formatted(orden)));
        assertThat(buscarCriterio(editor.get("borrador"), orden).at("/preguntas/0/enunciado").asText())
                .isEqualTo("¿Arqueo diario?");
        assertThat(idsDe(buscarCriterio(editor.get("borrador"), caja).get("entregables")))
                .containsExactlyInAnyOrder(video, recibo);
        assertThat(idsDe(buscarCriterio(editor.get("borrador"), orden).get("entregables")))
                .containsExactlyInAnyOrder(video, tablero);
        // Cambiarle los puntos y de criterio no mueve lo que vale cada uno: «Caja» sigue en 40, ahora
        // con 10 de parte calificada; «Orden» sigue en 0 y su cerrada de 20 pasa de ahí.
        assertThat(buscarCriterio(editor.get("borrador"), caja).get("puntos").asInt()).isEqualTo(40);
        assertThat(buscarCriterio(editor.get("borrador"), caja).get("puntosCalificados").asInt()).isEqualTo(10);
        assertThat(buscarCriterio(editor.get("borrador"), orden).get("puntos").asInt()).isZero();
        assertThat(editor.at("/borrador/avisos").toString())
                .contains("Las cerradas de «Orden» suman 20 y el criterio vale 0.")
                .doesNotContain("Las cerradas de «Caja»");

        // Quitar «¿Vuelto?» quita también su archivo; «Caja» sigue valiendo 40, todo calificado
        editor = respuesta(conToken(delete(base(taller) + "/preguntas/" + vuelto), tokenTalento, null));
        assertThat(buscarCriterio(editor.get("borrador"), caja).get("preguntas").size()).isZero();
        assertThat(buscarCriterio(editor.get("borrador"), caja).get("puntos").asInt()).isEqualTo(40);
        assertThat(buscarCriterio(editor.get("borrador"), caja).get("puntosCalificados").asInt()).isEqualTo(40);
        assertThat(editor.at("/borrador/prueba/entregables").size()).isEqualTo(2);

        // Quitar «Orden» deja su pregunta sin criterio; quitar esa pregunta deja el tablero sin cubrir
        editor = respuesta(conToken(delete(base(taller) + "/criterios/" + orden), tokenTalento, null));
        assertThat(editor.at("/borrador/sinCriterio/0/id").asLong()).isEqualTo(arqueo);
        editor = respuesta(conToken(delete(base(taller) + "/preguntas/" + arqueo), tokenTalento, null));
        assertThat(editor.at("/borrador/avisos").toString()).contains("«Tablero» no cubre ninguna pregunta");
        editor = respuesta(conToken(delete(base(taller) + "/entregables/" + tablero), tokenTalento, null));
        assertThat(editor.at("/borrador/prueba/entregables").size()).isEqualTo(1);
        assertThat(idsDe(buscarCriterio(editor.get("borrador"), caja).get("entregables"))).containsExactly(video);

        // Sin publicada no hay recalificación que reintentar; lo copiable se busca sin tildes
        conToken(post(base(taller) + "/publicada/recalificacion"), tokenTalento, null)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("Esta vacante todavía no tiene la prueba publicada"));
        JsonNode copiables = json.readTree(conTokenGet(base(taller) + "/copiables?buscar=ASISTENTE",
                tokenTalento).andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        assertThat(copiables.toString()).contains("\"vacanteId\":" + vacanteId);
        assertThat(json.readTree(conTokenGet(base(taller) + "/copiables?buscar=pasteleria", tokenTalento)
                .andReturn().getResponse().getContentAsString()).size()).isZero();

        // Descartar el borrador lo borra entero
        editor = respuesta(conToken(delete(base(taller) + "/borrador"), tokenTalento, null));
        assertThat(editor.at("/borrador").isNull() || editor.at("/borrador").isMissingNode()).isTrue();
        assertThat(editor.at("/resumen/estado").asText()).isEqualTo("SIN_PRUEBA");
    }

    @DisplayName("Con la prueba publicada y gente en la etapa técnica, cambiar la fecha pide motivo, se audita y mueve a los abiertos sin plazo propio (AC-10)")
    @Test
    @Order(25)
    void laFechaConGenteDentroPideMotivo() throws Exception {
        // Dentro y sin plazo propio: Fabi ya abrió la cronometrada de 90 minutos; Gael todavía no.
        entrarALaPrueba("fabi", "fabi.propia@correo.pe");
        iniciar("fabi");
        entrarALaPrueba("gael", "gael.propia@correo.pe");
        assertThat(venceASusMinutos("fabi")).isTrue();
        JsonNode editor = editor(vacanteId);
        assertThat(editor.at("/fechaLimite/pideMotivo").asBoolean()).isTrue();
        java.time.Instant nueva = java.time.Instant.now().plus(java.time.Duration.ofDays(30))
                .truncatedTo(java.time.temporal.ChronoUnit.SECONDS);

        conToken(put(base(vacanteId) + "/fecha-limite"), tokenTalento, "{\"cierraEn\":\"%s\"}".formatted(nueva))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.faltas[0]").value(org.hamcrest.Matchers.containsString("pide un motivo")));
        // Sin editar_vacante, 403
        conToken(put(base(vacanteId) + "/fecha-limite"), tokenArea,
                "{\"cierraEn\":\"%s\",\"motivo\":\"x\"}".formatted(nueva)).andExpect(status().isForbidden());

        respuesta(conToken(put(base(vacanteId) + "/fecha-limite"), tokenTalento,
                "{\"cierraEn\":\"%s\",\"motivo\":\"Se amplía la convocatoria\"}".formatted(nueva)));

        assertThat(jdbc.queryForObject("select motivo from auditoria where accion = "
                + "'fijar_fecha_limite_prueba_propia' and entidad_id = ? order by id desc limit 1",
                String.class, vacanteId)).isEqualTo("Se amplía la convocatoria");
        // La nueva fecha es para quien no ha empezado; a Fabi no le alarga el reloj: sigue
        // venciendo a los 90 minutos de abrirla, que llegan antes.
        assertThat(venceEn("gael")).isEqualTo(nueva);
        assertThat(venceASusMinutos("fabi")).isTrue();

        // Adelantarla por debajo de su reloj sí la corta, a ella y a quien no ha empezado.
        java.time.Instant pronto = java.time.Instant.now().plus(java.time.Duration.ofMinutes(20))
                .truncatedTo(java.time.temporal.ChronoUnit.SECONDS);
        respuesta(conToken(put(base(vacanteId) + "/fecha-limite"), tokenTalento,
                "{\"cierraEn\":\"%s\",\"motivo\":\"Se adelanta el cierre\"}".formatted(pronto)));
        assertThat(venceEn("fabi")).isEqualTo(pronto);
        assertThat(venceEn("gael")).isEqualTo(pronto);

        // «Sin cronómetro» pasa a la nueva fecha. La publicada está congelada desde la primera
        // rendición: para no montar otra vacante entera se la pasa a «Sin cronómetro» en la
        // base, y se le devuelve el reloj al terminar.
        long publicada = publicadaId(vacanteId);
        jdbc.update("update version_banco set modalidad = 'PLAZO_ABIERTO', duracion_minutos = null where id = ?",
                publicada);
        try {
            respuesta(conToken(put(base(vacanteId) + "/fecha-limite"), tokenTalento,
                    "{\"cierraEn\":\"%s\",\"motivo\":\"Se amplía otra vez\"}".formatted(nueva)));
            assertThat(venceEn("fabi")).isEqualTo(nueva);
            assertThat(venceEn("gael")).isEqualTo(nueva);
        } finally {
            jdbc.update("update version_banco set modalidad = 'CRONOMETRADA', duracion_minutos = 90 where id = ?",
                    publicada);
        }
    }

    private java.time.Instant venceEn(String quien) {
        return jdbc.queryForObject("select vence_en from intento_prueba where postulacion_id = ?",
                java.sql.Timestamp.class, postulacion.get(quien)).toInstant();
    }

    /** Si vence justo a los 90 minutos de haberla abierto (se compara en la base, sin redondeos). */
    private boolean venceASusMinutos(String quien) {
        return Boolean.TRUE.equals(jdbc.queryForObject("select vence_en = iniciado_en + interval '90 minutes' "
                + "from intento_prueba where postulacion_id = ?", Boolean.class, postulacion.get(quien)));
    }

    @DisplayName("Una prueba publicada antes de la V68, con «Mira» marcado a mano, conserva lo que miraba y sus notas (AC-22)")
    @Test
    @Order(26)
    void laDeAntesConservaSuMira() throws Exception {
        JsonNode antes = pruebaDe("ana");
        // Como la dejó la V67: el tablero sin alcance y mirado a mano por «Comunicación»
        jdbc.update("update entregable_requerido set alcance = null, pregunta_id = null where id = ?",
                entregableTablero);
        jdbc.update("insert into criterio_banco_entregable (criterio_banco_id, entregable_requerido_id) "
                + "values (?, ?)", criterioComunicacion, entregableTablero);

        JsonNode despues = pruebaDe("ana");
        assertThat(idsDe(criterioDe(despues, criterioComunicacion).get("entregables"))).contains(entregableTablero);
        assertThat(idsDe(criterioDe(despues, criterioConocimiento).get("entregables")))
                .doesNotContain(entregableTablero);
        assertThat(despues.get("nota").toString()).isEqualTo(antes.get("nota").toString());
        assertThat(criterioDe(despues, criterioComunicacion).get("nota").toString())
                .isEqualTo(criterioDe(antes, criterioComunicacion).get("nota").toString());

        // Y como la dejó la V68, sin lo que vale cada criterio (V69 no toca las publicadas): lee la
        // parte calificada guardada, que publicar y cambiar los puntos dejaron escrita, y nada cambia.
        long publicada = publicadaId(vacanteId);
        assertThat(jdbc.queryForObject("select puntos_calificados from criterio_banco where id = ?",
                Integer.class, criterioConocimiento)).isEqualTo(15);
        jdbc.update("update criterio_banco set puntos_del_criterio = null where version_banco_id = ?", publicada);
        JsonNode sinTotal = pruebaDe("ana");
        assertThat(sinTotal.get("nota").toString()).isEqualTo(antes.get("nota").toString());
        for (long criterio : List.of(criterioConocimiento, criterioExcel, criterioComunicacion)) {
            assertThat(criterioDe(sinTotal, criterio).get("maximo").asInt())
                    .isEqualTo(criterioDe(antes, criterio).get("maximo").asInt());
            assertThat(criterioDe(sinTotal, criterio).get("nota").toString())
                    .isEqualTo(criterioDe(antes, criterio).get("nota").toString());
        }
        assertThat(editor(vacanteId).at("/publicada/total").asInt()).isEqualTo(100);
    }

    private JsonNode respuesta(ResultActions peticion) throws Exception {
        return json.readTree(peticion.andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
    }

    private JsonNode editor(long vacante) throws Exception {
        return json.readTree(conTokenGet(base(vacante), tokenTalento).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString());
    }

    private long idDelCriterio(ResultActions respuesta, String nombre) throws Exception {
        JsonNode editor = json.readTree(respuesta.andExpect(status().isOk()).andReturn().getResponse()
                .getContentAsString());
        for (JsonNode c : editor.at("/borrador/criterios")) {
            if (nombre.equals(c.get("nombre").asText())) return c.get("id").asLong();
        }
        throw new AssertionError("No está el criterio " + nombre);
    }

    private long idDelEntregable(ResultActions respuesta, String nombre) throws Exception {
        JsonNode editor = json.readTree(respuesta.andExpect(status().isOk()).andReturn().getResponse()
                .getContentAsString());
        for (JsonNode e : editor.at("/borrador/prueba/entregables")) {
            if (nombre.equals(e.get("nombre").asText())) return e.get("id").asLong();
        }
        throw new AssertionError("No está el entregable " + nombre);
    }

    private void agregar(String cual, String cuerpo) throws Exception {
        JsonNode editor = json.readTree(conToken(post(base(vacanteId) + "/preguntas"), tokenTalento, cuerpo)
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        String enunciado = json.readTree(cuerpo).get("enunciado").asText();
        for (JsonNode c : editor.at("/borrador/criterios")) {
            for (JsonNode p : c.get("preguntas")) {
                if (p.get("enunciado").asText().equals(enunciado)) {
                    pregunta.put(cual, p.get("id").asLong());
                    p.get("opciones").forEach(o -> opcion.put(cual + ":" + o.get("texto").asText(),
                            o.get("id").asLong()));
                }
            }
        }
        assertThat(pregunta).containsKey(cual);
    }

    /** Tras publicar una copia, los ids de criterios, preguntas, opciones y entregables cambian. */
    private void recordarIds(JsonNode version) {
        Map<String, String> enunciadoDe = new HashMap<>();
        enunciadoDe.put("¿Qué libro registra primero una venta al crédito?", "unica");
        enunciadoDe.put("¿Cómo hallaste el descuadre?", "abiertaA");
        enunciadoDe.put("Explica el ajuste a tu jefe en tres líneas", "abiertaB");
        enunciadoDe.put("¿Qué funciones buscan un valor?", "multiple");
        enunciadoDe.put("¿Cuánto dominas las tablas dinámicas?", "escala");
        for (JsonNode c : version.get("criterios")) {
            switch (c.get("nombre").asText()) {
                case "Conocimiento contable" -> criterioConocimiento = c.get("id").asLong();
                case "Comunicación" -> criterioComunicacion = c.get("id").asLong();
                case "Manejo de Excel" -> criterioExcel = c.get("id").asLong();
                default -> { }
            }
            for (JsonNode p : c.get("preguntas")) {
                String cual = enunciadoDe.get(p.get("enunciado").asText());
                pregunta.put(cual, p.get("id").asLong());
                p.get("opciones").forEach(o -> opcion.put(cual + ":" + o.get("texto").asText(),
                        o.get("id").asLong()));
            }
        }
        for (JsonNode e : version.at("/prueba/entregables")) {
            if ("Tablero.xlsx".equals(e.get("nombre").asText())) entregableTablero = e.get("id").asLong();
            if ("Video".equals(e.get("nombre").asText())) entregableVideo = e.get("id").asLong();
        }
    }

    private static List<Long> idsDe(JsonNode lista) {
        List<Long> ids = new ArrayList<>();
        lista.forEach(n -> ids.add(n.asLong()));
        return ids;
    }

    private static JsonNode buscarCriterio(JsonNode version, long id) {
        for (JsonNode c : version.get("criterios")) {
            if (c.get("id").asLong() == id) return c;
        }
        throw new AssertionError("No está el criterio " + id);
    }

    private static JsonNode buscarPregunta(JsonNode version, String cual) {
        for (JsonNode c : version.get("criterios")) {
            for (JsonNode p : c.get("preguntas")) {
                if (p.get("id").asLong() == pregunta.get(cual)) return p;
            }
        }
        throw new AssertionError("No está la pregunta " + cual);
    }

    private long crearVacante(String titulo) throws Exception {
        long solicitudId = Long.parseLong(leer(conToken(post("/api/v1/panel/solicitudes"), tokenTalento, """
                {"areaId": %d, "puestoId": %d, "urgencia": "NORMAL",
                 "nivelPuestoCodigo": "EJECUCION", "familiaCodigo": "OPERACIONES",
                 "resultadoPrincipal": "Cierres mensuales a tiempo",
                 "motivo": "Los cierres se atrasan",
                 "consecuenciaNoContratar": "Se pierde el control de caja",
                 "analisisCapacidad": "El equipo no alcanza",
                 "responsableUsuarioId": 1,
                 "resultadosEsperados": [
                   {"descripcion": "Cierres al día", "indicador": "antes del día 5"},
                   {"descripcion": "Conciliaciones", "indicador": "semanales"},
                   {"descripcion": "Reportes", "indicador": "mensuales"}
                 ]}""".formatted(areaId, puestoId))
                .andReturn().getResponse().getContentAsString(), "id"));
        conToken(post("/api/v1/panel/solicitudes/" + solicitudId + "/aprobacion"), tokenTalento,
                "{\"motivo\":\"Aprobada\"}").andExpect(status().isOk());
        return Long.parseLong(leer(conToken(post("/api/v1/panel/vacantes"), tokenTalento, """
                {"solicitudTalentoId": %d, "puestoId": %d,
                 "titulo": "%s", "descripcion": "Registro y cierre contable",
                 "tipoCierre": "PERMANENTE", "responsableUsuarioId": 1}"""
                .formatted(solicitudId, puestoId, titulo))
                .andReturn().getResponse().getContentAsString(), "id"));
    }

    private long unaPruebaPublicada() throws Exception {
        long plantillaId = Long.parseLong(leer(conToken(post("/api/v1/panel/plantillas-prueba"),
                tokenTalento, "{\"nombre\":\"Prueba de caja\"}")
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString(), "id"));
        long versionId = Long.parseLong(leer(conToken(
                post("/api/v1/panel/plantillas-prueba/" + plantillaId + "/versiones"), tokenTalento, """
                {"enunciado":"Cuadra la caja","modalidad":"PLAZO_ABIERTO","plazoDias":7}""")
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString(), "id"));
        long preguntaId = Long.parseLong(leer(conToken(post("/api/v1/panel/plantillas-prueba/preguntas"),
                tokenTalento, "{\"codigo\":\"PP_CAJA_P\",\"enunciado\":\"Cuadra\",\"tipo\":\"ESPECIFICA\"}")
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString(), "id"));
        conToken(post("/api/v1/panel/plantillas-prueba/versiones/" + versionId + "/preguntas"),
                tokenTalento, "{\"preguntaPruebaId\": %d}".formatted(preguntaId)).andExpect(status().isOk());
        conToken(post("/api/v1/panel/plantillas-prueba/versiones/" + versionId + "/rubrica"), tokenTalento, """
                {"codigo":"CAJA_P","nombre":"Caja","puntos":100,"metodoVerificacion":"AGENTE"}""")
                .andExpect(status().isCreated());
        conToken(post("/api/v1/panel/plantillas-prueba/versiones/" + versionId + "/publicacion"),
                tokenTalento, null).andExpect(status().isOk());
        return versionId;
    }

    private String crearUsuarioConUnSoloRol(String nombre, String apellidos, String correo,
                                            String usuarioRenaserOsId, String rol) throws Exception {
        conToken(post("/api/v1/panel/usuarios"), tokenTalento, """
                {"nombre": "%s", "apellidos": "%s", "correo": "%s",
                 "usuarioRenaserOsId": "%s", "roles": ["%s"]}"""
                .formatted(nombre, apellidos, correo, usuarioRenaserOsId, rol))
                .andExpect(status().isCreated());
        return leer(mvc.perform(post("/api/v1/panel/auth/dev-login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"usuarioRenaserOsId\":\"%s\"}".formatted(usuarioRenaserOsId)))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString(), "token");
    }

    private static String capital(String s) {
        return Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }

    private static String base(long vacante) {
        return "/api/v1/panel/vacantes/" + vacante + "/prueba-propia";
    }

    private ResultActions conToken(MockHttpServletRequestBuilder peticion, String token, String cuerpo)
            throws Exception {
        peticion.header("Authorization", "Bearer " + token).contentType(MediaType.APPLICATION_JSON);
        if (cuerpo != null) peticion.content(cuerpo);
        return mvc.perform(peticion);
    }

    private ResultActions conTokenGet(String ruta, String token) throws Exception {
        return mvc.perform(get(ruta).header("Authorization", "Bearer " + token));
    }

    private String leer(String cuerpoRespuesta, String campo) throws Exception {
        return json.readTree(cuerpoRespuesta).get(campo).asText();
    }
}
