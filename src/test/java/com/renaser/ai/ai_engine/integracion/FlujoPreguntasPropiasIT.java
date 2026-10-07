package com.renaser.ai.ai_engine.integracion;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.renaser.ai.ai_engine.integracion.soporte.ImagenesDeContenedores;
import com.renaser.ai.ai_engine.ai.service.ColaCalificacionIa;
import com.renaser.ai.ai_engine.perfilintegral.dto.DtosRecomendador.CriterioPropuesto;
import com.renaser.ai.ai_engine.perfilintegral.dto.DtosRecomendador.OpcionPropuesta;
import com.renaser.ai.ai_engine.perfilintegral.dto.DtosRecomendador.PreguntaPropuesta;
import com.renaser.ai.ai_engine.perfilintegral.dto.DtosRecomendador.ResultadoRecomendador;
import com.renaser.ai.ai_engine.perfilintegral.entity.PropuestaPreguntas;
import com.renaser.ai.ai_engine.perfilintegral.repository.PropuestaPreguntasRepository;
import com.renaser.ai.ai_engine.perfilintegral.service.PuenteRecomendador;
import com.renaser.ai.ai_engine.perfilintegral.dto.DtosCalificacionIa.NotaRespuestaIa;
import com.renaser.ai.ai_engine.perfilintegral.dto.DtosCalificacionIa.ResultadoEvaluador;
import com.renaser.ai.ai_engine.perfilintegral.repository.VersionBancoRepository;
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
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.rabbitmq.RabbitMQContainer;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * Las preguntas propias de una vacante, de punta a punta (V66, fase 1).
 *
 * <p>Una vacante elige sus preguntas propias, se escriben agrupadas por criterios (con el
 * «General» que nace solo), publicar con 95 puntos devuelve la lista entera de lo que falta,
 * y con 100 se publica. Alguien postula, responde los cuatro tipos sin ver un solo punto, y
 * al entregar no hay nota del Perfil Integral hasta que la última abierta tiene la suya. Una
 * persona ajusta y califica a mano, se cambian los puntos con candidatos dentro sin IA, y se
 * copia a otra vacante de la misma empresa.
 *
 * <p>La IA está apagada: lo que el evaluador entregaría se mete por el mismo puente que usa
 * el agente, que es donde vive la red de seguridad.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@Testcontainers
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@DisplayName("Preguntas propias de la vacante: escribir, rendir, calificar y copiar")
public class FlujoPreguntasPropiasIT {

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
    @Autowired PuenteCalificacionIa puente;
    @Autowired VersionBancoRepository versionesBanco;
    @Autowired PuenteRecomendador puenteRecomendador;
    @Autowired PropuestaPreguntasRepository propuestas;
    @MockitoSpyBean ColaCalificacionIa cola;
    final ObjectMapper json = new ObjectMapper();

    static String tokenTalento;
    static String tokenCandidato;
    static String codigoPostulacion;
    static long puestoId;
    static long areaId;
    static long vacanteId;
    static long postulacionId;
    static long criterioGeneralId;
    static long criterioExcelId;
    static final Map<String, Long> pregunta = new HashMap<>();
    static final Map<String, Long> respuesta = new HashMap<>();

    // ============ Escribir y publicar ============

    @DisplayName("La vacante elige sus preguntas propias y la primera pregunta crea «General»")
    @Test
    @Order(1)
    void laVacanteEligeSusPreguntasYNaceGeneral() throws Exception {
        tokenTalento = leer(mvc.perform(post("/api/v1/panel/auth/dev-login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"usuarioRenaserOsId\":\"dev-talento\"}"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(), "token");

        jdbc.update("INSERT INTO area (organizacion_id, nombre, es_activa) VALUES (1, 'Finanzas', true)");
        areaId = jdbc.queryForObject("SELECT id FROM area ORDER BY id DESC LIMIT 1", Long.class);
        puestoId = Long.parseLong(leer(conToken(post("/api/v1/panel/puestos"), tokenTalento, """
                {"codigo": "ASIST_CONTABLE", "nombre": "Asistente contable",
                 "nivelPuestoCodigo": "EJECUCION", "familiaCodigo": "OPERACIONES"}""")
                .andReturn().getResponse().getContentAsString(), "id"));
        vacanteId = crearVacante("Asistente contable");

        // RENASER tiene banco propio publicado para el nivel, y aun así la vacante nace con sus
        // preguntas propias (AC-01b, decisión del 30/09/2026). El banco sigue ofreciéndose:
        // se elige, se confirma y se vuelve a las propias.
        conTokenGet("/api/v1/panel/vacantes/" + vacanteId, tokenTalento)
                .andExpect(jsonPath("$.origenPreguntas").value("VACANTE"))
                .andExpect(jsonPath("$.aplicaEvaluacion").value(true))
                .andExpect(jsonPath("$.bancoDelNivelPropio").value(true))
                .andExpect(jsonPath("$.bancoPrestado").value(false));
        conToken(post("/api/v1/panel/vacantes/" + vacanteId + "/origen-preguntas"), tokenTalento,
                "{\"origen\":\"NIVEL\"}").andExpect(status().isOk());
        conTokenGet("/api/v1/panel/vacantes/" + vacanteId, tokenTalento)
                .andExpect(jsonPath("$.origenPreguntas").value("NIVEL"));
        // La lista también dice de dónde salen: el banco de la empresa, sin estado de propias
        JsonNode conElBanco = filaDeLaLista(vacanteId);
        assertThat(conElBanco.get("origenPreguntas").asText()).isEqualTo("NIVEL");
        assertThat(conElBanco.get("bancoDelNivelPropio").asBoolean()).isTrue();
        assertThat(conElBanco.get("bancoPrestado").asBoolean()).isFalse();
        assertThat(conElBanco.get("estadoPreguntasPropias").isNull()).isTrue();
        conToken(post("/api/v1/panel/vacantes/" + vacanteId + "/origen-preguntas"), tokenTalento,
                "{\"origen\":\"VACANTE\"}").andExpect(status().isOk());
        assertThat(filaDeLaLista(vacanteId).get("estadoPreguntasPropias").asText())
                .isEqualTo("SIN_PREGUNTAS");

        JsonNode editor = json.readTree(conToken(post(base() + "/preguntas"), tokenTalento, """
                {"tipo":"ABIERTA","enunciado":"Cuéntanos un cierre con un descuadre. ¿Cómo lo hallaste?",
                 "puntos":20,"queDebeTener":"El monto, la cuenta y en cuántos días lo cerró"}""")
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString());
        // Sin criterios creados, la pregunta nace dentro de «General» (AC-03)
        assertThat(editor.at("/borrador/criterios")).hasSize(1);
        assertThat(editor.at("/borrador/criterios/0/nombre").asText()).isEqualTo("General");
        assertThat(editor.at("/borrador/criterios/0/puntos").asInt()).isEqualTo(20);
        criterioGeneralId = editor.at("/borrador/criterios/0/id").asLong();
        pregunta.put("abiertaA", editor.at("/borrador/criterios/0/preguntas/0/id").asLong());
        assertThat(filaDeLaLista(vacanteId).get("estadoPreguntasPropias").asText()).isEqualTo("BORRADOR");
    }

    @DisplayName("Con 95 puntos, un criterio vacío y una clave sin máximo: 400 con las tres faltas")
    @Test
    @Order(2)
    void publicarConTresFaltasLasListaTodas() throws Exception {
        criterioExcelId = json.readTree(conToken(post(base() + "/criterios"), tokenTalento,
                        "{\"nombre\":\"Manejo de Excel\",\"queEvalua\":\"Tablas dinámicas y fórmulas\"}")
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString())
                .at("/borrador/criterios/1/id").asLong();

        agregar("unica", """
                {"tipo":"OPCION_UNICA","enunciado":"¿Qué libro registra primero una venta al crédito?",
                 "puntos":4,"criterioId":%d,
                 "opciones":[{"texto":"Libro diario","puntos":3},{"texto":"Caja","puntos":0}]}"""
                .formatted(criterioGeneralId));
        agregar("multiple", """
                {"tipo":"OPCION_MULTIPLE","enunciado":"¿Qué cuentas son de activo?","puntos":10,
                 "criterioId":%d,
                 "opciones":[{"texto":"Caja","puntos":6},{"texto":"Clientes","puntos":6},
                             {"texto":"Proveedores","puntos":-4}]}""".formatted(criterioGeneralId));
        StringBuilder niveles = new StringBuilder();
        for (int n = 1; n <= 10; n++) {
            String rotulo = n == 1 ? "Nada" : n == 10 ? "Mucho" : "";
            niveles.append(n == 1 ? "" : ",")
                    .append("{\"texto\":\"%s\",\"puntos\":%d}".formatted(rotulo, n - 1));
        }
        agregar("escala", """
                {"tipo":"ESCALA","enunciado":"¿Cuánto dominas los cierres mensuales?","puntos":9,
                 "criterioId":%d,"opciones":[%s]}""".formatted(criterioGeneralId, niveles));
        agregar("cero", """
                {"tipo":"ABIERTA","enunciado":"¿Qué te gustaría aprender aquí?","puntos":0,
                 "criterioId":%d}""".formatted(criterioGeneralId));
        agregar("abiertaB", """
                {"tipo":"ABIERTA","enunciado":"¿Cómo armarías el control de cuentas por cobrar?",
                 "puntos":52,"criterioId":%d}""".formatted(criterioGeneralId));

        // 20 + 4 + 10 + 9 + 0 + 52 = 95; «Manejo de Excel» vacío; la única no da sus 4 (AC-05)
        JsonNode error = json.readTree(conToken(post(base() + "/publicacion"), tokenTalento, null)
                .andExpect(status().isBadRequest())
                .andReturn().getResponse().getContentAsString());
        assertThat(error.get("faltas")).hasSize(3);
        assertThat(error.get("faltas").toString())
                .contains("faltan 5").contains("Manejo de Excel").contains("4 puntos");
        assertThat(versionesBanco.preguntasPropiasDe(vacanteId, "PUBLICADA")).isEmpty();
    }

    @DisplayName("Con 100 se publica; la vacante no se publicaba sin ellas")
    @Test
    @Order(3)
    void conCienSePublicaYLaVacanteTambien() throws Exception {
        // La vacante con «Preguntas propias» y sin versión publicada no se publica (AC-17)
        conToken(post("/api/v1/panel/vacantes/" + vacanteId + "/publicacion"), tokenTalento, null)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value(org.hamcrest.Matchers.containsString("preguntas propias")));

        conToken(put(base() + "/preguntas/" + pregunta.get("unica")), tokenTalento, """
                {"tipo":"OPCION_UNICA","enunciado":"¿Qué libro registra primero una venta al crédito?",
                 "puntos":4,"criterioId":%d,
                 "opciones":[{"texto":"Libro diario","puntos":4},{"texto":"Caja","puntos":0}]}"""
                .formatted(criterioGeneralId)).andExpect(status().isOk());
        JsonNode editor = json.readTree(conToken(put(base() + "/preguntas/" + pregunta.get("abiertaB")),
                        tokenTalento, """
                {"tipo":"ABIERTA","enunciado":"¿Cómo armarías el control de cuentas por cobrar?",
                 "puntos":57,"criterioId":%d}""".formatted(criterioExcelId))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        // El balance y los criterios los cuadra el servidor (AC-04): 43 + 57
        assertThat(editor.at("/borrador/total").asInt()).isEqualTo(100);
        assertThat(editor.at("/borrador/criterios/0/puntos").asInt()).isEqualTo(43);
        assertThat(editor.at("/borrador/criterios/0/puntosSistema").asInt()).isEqualTo(23);
        assertThat(editor.at("/borrador/criterios/0/puntosIa").asInt()).isEqualTo(20);
        assertThat(editor.at("/borrador/criterios/1/puntos").asInt()).isEqualTo(57);
        assertThat(editor.at("/borrador/avisos")).isEmpty();

        conToken(post(base() + "/publicacion"), tokenTalento, null).andExpect(status().isOk())
                .andExpect(jsonPath("$.publicada.estado").value("PUBLICADA"))
                .andExpect(jsonPath("$.resumen.estado").value("PUBLICADAS"))
                .andExpect(jsonPath("$.resumen.criterios").value(2))
                .andExpect(jsonPath("$.resumen.preguntas").value(6));

        // El cuestionario técnico no toma las preguntas del Perfil Integral (AC-18)
        assertThat(versionesBanco.cuestionarioTecnicoDe(vacanteId, "PUBLICADA")).isEmpty();
        assertThat(versionesBanco.preguntasPropiasDe(vacanteId, "PUBLICADA")).isPresent();
        assertThat(filaDeLaLista(vacanteId).get("estadoPreguntasPropias").asText()).isEqualTo("PUBLICADAS");

        conToken(post("/api/v1/panel/vacantes/" + vacanteId + "/plantilla-prueba"), tokenTalento,
                "{\"versionPlantillaPruebaId\": %d}".formatted(unaPruebaPublicada())).andExpect(status().isOk());
        conToken(post("/api/v1/panel/vacantes/" + vacanteId + "/publicacion"), tokenTalento, null)
                .andExpect(status().isOk());
    }

    // ============ Rendir ============

    @DisplayName("Quien postula responde los cuatro tipos sin ver puntos, clave ni criterios")
    @Test
    @Order(4)
    void elCandidatoRespondeSinVerLaClave() throws Exception {
        mvc.perform(post("/api/v1/portal/cuentas")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                        {"nombre":"Rosa","apellidos":"Quispe","correo":"rosa.contable@correo.pe",
                         "contrasena":"unaClaveLarga123","ciudadUbigeo":"1501","aceptaPlataforma":true,
                         "aceptaFuturosContactos":false}"""))
                .andExpect(status().isCreated());
        tokenCandidato = leer(mvc.perform(post("/api/v1/portal/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"correo\":\"rosa.contable@correo.pe\",\"contrasena\":\"unaClaveLarga123\"}"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(), "token");
        codigoPostulacion = leer(mvc.perform(multipart("/api/v1/portal/postulaciones")
                        .file(new MockMultipartFile("cv", "cv.pdf", "application/pdf", "x".getBytes()))
                        .param("vacanteId", String.valueOf(vacanteId))
                        .param("resultadoOrgulloso", "Cuadré el cierre de tres meses atrasados")
                        .param("aceptaTratamiento", "true")
                        .header("Authorization", "Bearer " + tokenCandidato))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString(), "codigo");
        postulacionId = jdbc.queryForObject("select id from postulacion where vacante_id = ?",
                Long.class, vacanteId);

        String cuerpo = mvc.perform(post("/api/v1/portal/evaluacion/" + codigoPostulacion + "/inicio")
                        .header("Authorization", "Bearer " + tokenCandidato))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        // Ningún campo lleva puntos, clave ni criterios (AC-08, RF-53)
        assertThat(cuerpo).doesNotContain("\"puntos\"").doesNotContain("puntaje")
                .doesNotContain("criterio").doesNotContain("queDebeTener");
        JsonNode examen = json.readTree(cuerpo);
        assertThat(examen.get("preguntas")).hasSize(6);

        for (JsonNode p : examen.get("preguntas")) {
            long id = p.get("id").asLong();
            String cual = pregunta.entrySet().stream().filter(e -> e.getValue() == id)
                    .map(Map.Entry::getKey).findFirst().orElseThrow();
            List<JsonNode> opciones = new ArrayList<>();
            p.get("opciones").forEach(opciones::add);
            String respuestaJson = switch (cual) {
                case "unica" -> "{\"opcionId\":%d}".formatted(opcionConTexto(opciones, "Libro diario"));
                case "multiple" -> "{\"detalle\":{\"marcadas\":[%d,%d,%d]}}".formatted(
                        opcionConTexto(opciones, "Caja"), opcionConTexto(opciones, "Clientes"),
                        opcionConTexto(opciones, "Proveedores"));
                case "escala" -> {
                    // Del 1 al 10 en ese orden, no 1, 10, 2… (AC-22)
                    assertThat(opciones.stream().map(o -> o.get("letra").asText()).toList())
                            .containsExactly("1", "2", "3", "4", "5", "6", "7", "8", "9", "10");
                    assertThat(opciones.get(0).get("texto").asText()).isEqualTo("Nada");
                    yield "{\"opcionId\":%d}".formatted(opciones.get(7).get("id").asLong());
                }
                default -> "{\"texto\":\"En marzo el mayor no cuadraba por 1.200 soles y lo hallé en dos días\"}";
            };
            mvc.perform(put("/api/v1/portal/evaluacion/" + codigoPostulacion + "/respuestas/" + id)
                            .header("Authorization", "Bearer " + tokenCandidato)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(respuestaJson))
                    .andExpect(status().isOk());
        }
        mvc.perform(post("/api/v1/portal/evaluacion/" + codigoPostulacion + "/entrega")
                        .header("Authorization", "Bearer " + tokenCandidato))
                .andExpect(status().isOk());

        for (Map<String, Object> fila : jdbc.queryForList("""
                select r.id, r.pregunta_id from respuesta r
                  join postulacion p on p.evaluacion_id = r.evaluacion_id where p.id = ?""", postulacionId)) {
            long preguntaId = ((Number) fila.get("pregunta_id")).longValue();
            pregunta.forEach((k, v) -> {
                if (v == preguntaId) respuesta.put(k, ((Number) fila.get("id")).longValue());
            });
        }

        // Con las abiertas sin calificar, NO hay nota del Perfil Integral (AC-09)
        assertThat(notaDelPerfil()).isNull();
        JsonNode desglose = desglose();
        assertThat(desglose.at("/porPuntos/completo").asBoolean()).isFalse();
        // Lo cerrado ya está: única 4 + múltiple 6+6−4 = 8 + escala nivel 8 = 7 (AC-06)
        JsonNode general = desglose.at("/porPuntos/criterios/0");
        assertThat(general.get("pendiente").asBoolean()).isTrue();
        assertThat(general.get("nota").isNull()).isTrue();
        assertThat(new BigDecimal(general.get("sistema").asText())).isEqualByComparingTo("19");
        assertThat(general.get("sistemaMaximo").asInt()).isEqualTo(23);
        // La de 0 puntos se guarda y se ve, pero no suma ni espera a la IA (AC-07)
        JsonNode cero = buscar(general.get("preguntas"), "¿Qué te gustaría aprender aquí?");
        assertThat(cero.get("sinPuntos").asBoolean()).isTrue();
        assertThat(cero.get("pendiente").asBoolean()).isFalse();
        assertThat(cero.get("respuesta").asText()).isNotBlank();
    }

    // ============ Calificar ============

    @DisplayName("La IA califica con red de seguridad; una persona completa y ajusta sin mover etapa")
    @Test
    @Order(5)
    void laIaCalificaYUnaPersonaCompletaYAjusta() throws Exception {
        String estadoAntes = estadoDeLaPostulacion();

        // La red (AC-10): la B llega sin explicación y se descarta; una ajena, también
        puente.guardarNotasPorPuntos(postulacionId, null, new ResultadoEvaluador(List.of(
                nota(respuesta.get("abiertaA"), "12", "Da el monto y la cuenta, no los días"),
                nota(respuesta.get("abiertaB"), "40", " "),
                nota(999_999L, "20", "No es de esta evaluación"))), 1, false);
        assertThat(notaIaDe(respuesta.get("abiertaA"))).isEqualByComparingTo("12");
        assertThat(jdbc.queryForObject("select count(*) from nota_respuesta where respuesta_id = ?",
                Integer.class, respuesta.get("abiertaB"))).isZero();
        // Falta la B: sigue sin nota del Perfil Integral
        assertThat(notaDelPerfil()).isNull();

        // La A tiene nota de la IA y la IA está apagada: cambiar la guía abriría una
        // recalificación que no puede correr, así que se rechaza y no se guarda nada (AC-29)
        conToken(put(base() + "/publicada/instrucciones"), tokenTalento,
                "{\"guiaCalificacion\":\"Una guía que no llega a guardarse\"}")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value(org.hamcrest.Matchers.containsString("apagada")));
        assertThat(jdbc.queryForMap("select guia_calificacion, version_guia from version_banco"
                + " where vacante_id = ? and proposito = 'PERFIL_INTEGRAL' and estado = 'PUBLICADA'", vacanteId))
                .containsEntry("guia_calificacion", null).containsEntry("version_guia", 1);
        assertThat(jdbc.queryForObject("select count(*) from auditoria where accion = "
                + "'corregir_instrucciones_ia'", Integer.class)).isZero();

        // Calificar a mano la que la IA no pudo escribe la nota del Perfil Integral (AC-27)
        conToken(put(ajuste("abiertaB")), tokenTalento,
                "{\"puntaje\": 50, \"motivo\": \"La IA no la leyó: describe el control completo\"}")
                .andExpect(status().isOk());
        assertThat(notaDelPerfil()).isNotNull();
        assertThat(estadoDeLaPostulacion()).isEqualTo(estadoAntes);

        // Ajustar la A: 12 → 16. La de la IA queda a la vista (AC-25)
        conToken(put(ajuste("abiertaA")), tokenTalento,
                "{\"puntaje\": 16, \"motivo\": \"Sí dio el dato; la IA no leyó el anexo\"}")
                .andExpect(status().isOk());
        JsonNode a = buscar(desglose().at("/porPuntos/criterios/0/preguntas"),
                "Cuéntanos un cierre con un descuadre. ¿Cómo lo hallaste?");
        assertThat(new BigDecimal(a.get("obtenido").asText())).isEqualByComparingTo("16");
        assertThat(new BigDecimal(a.get("puntajeIa").asText())).isEqualByComparingTo("12");
        assertThat(a.get("ajustada").asBoolean()).isTrue();
        assertThat(a.get("motivoAjuste").asText()).contains("anexo");
        // 16 + 4 + 8 + 7 + 0 + 50: la suma de los criterios (AC-19)
        assertThat(new BigDecimal(desglose().at("/porPuntos/total").asText())).isEqualByComparingTo("85");
        assertThat(desglose().at("/porPuntos/completo").asBoolean()).isTrue();
        // Sin nota del currículum (la IA está apagada), la del Perfil Integral es la del banco
        // tal cual: la suma de los criterios, sin ponderar cerradas y abiertas por cantidad
        // (AC-11). La mezcla con el currículum la prueba PuenteCalificacionIaPorPuntosTest.
        assertThat(notaDelPerfil()).isEqualByComparingTo("85");
        // Los criterios del banco no viven en `criterio` ni escriben `nota_criterio`: la nota
        // del currículum no puede mezclarlos (AC-24)
        assertThat(jdbc.queryForObject("select count(*) from criterio where nombre in "
                + "('General', 'Manejo de Excel')", Integer.class)).isZero();
        assertThat(jdbc.queryForObject("select count(*) from nota_criterio where postulacion_id = ?",
                Integer.class, postulacionId)).isZero();

        // Si la IA vuelve a calificar, la ajustada no se toca
        puente.guardarNotasPorPuntos(postulacionId, null, new ResultadoEvaluador(List.of(
                nota(respuesta.get("abiertaA"), "5", "Otra pasada"))), 1, false);
        assertThat(jdbc.queryForObject("select puntaje from nota_respuesta where respuesta_id = ?",
                BigDecimal.class, respuesta.get("abiertaA"))).isEqualByComparingTo("16");

        // Lo que el ajuste no admite (AC-26)
        conToken(put(ajuste("abiertaA")), tokenTalento, "{\"puntaje\": 21, \"motivo\": \"x\"}")
                .andExpect(status().isBadRequest());
        conToken(put(ajuste("abiertaA")), tokenTalento, "{\"puntaje\": -1, \"motivo\": \"x\"}")
                .andExpect(status().isBadRequest());
        conToken(put(ajuste("abiertaA")), tokenTalento, "{\"puntaje\": 10}")
                .andExpect(status().isBadRequest());
        conToken(put(ajuste("unica")), tokenTalento, "{\"puntaje\": 1, \"motivo\": \"x\"}")
                .andExpect(status().isBadRequest());
        assertThat(estadoDeLaPostulacion()).isEqualTo(estadoAntes);
    }

    // ============ Con candidatos dentro ============

    @DisplayName("Con postulantes, la vara no se mueve: ni otra versión, ni textos, ni el origen")
    @Test
    @Order(6)
    void conPostulantesLaVaraNoSeMueve() throws Exception {
        conToken(put(base() + "/preguntas/" + pregunta.get("unica")), tokenTalento, """
                {"tipo":"OPCION_UNICA","enunciado":"Otro texto","puntos":4,"criterioId":%d,
                 "opciones":[{"texto":"Libro diario","puntos":4},{"texto":"Caja","puntos":0}]}"""
                .formatted(criterioGeneralId)).andExpect(status().isConflict());
        conToken(post(base() + "/borrador"), tokenTalento, null).andExpect(status().isConflict());
        conToken(post("/api/v1/panel/vacantes/" + vacanteId + "/origen-preguntas"), tokenTalento,
                "{\"origen\":\"SIN_EVALUACION\"}").andExpect(status().isConflict());

        // Las instrucciones de la IA sí se corrigen (aquí nadie tiene notas vivas de la IA:
        // las dos abiertas las tocó una persona), y queda el rastro (AC-30)
        conToken(put(base() + "/publicada/instrucciones"), tokenTalento,
                "{\"guiaCalificacion\":\"Premia el dato concreto\"}").andExpect(status().isOk());
        assertThat(jdbc.queryForObject("select version_guia from version_banco where vacante_id = ?"
                + " and proposito = 'PERFIL_INTEGRAL' and estado = 'PUBLICADA'", Integer.class, vacanteId))
                .isEqualTo(2);
        assertThat(jdbc.queryForObject("select count(*) from auditoria where accion = "
                + "'corregir_instrucciones_ia'", Integer.class)).isEqualTo(1);
    }

    @DisplayName("Cambiar los puntos recalcula a todos al instante, sin IA y sin mover a nadie")
    @Test
    @Order(7)
    void cambiarLosPuntosRecalculaSinIa() throws Exception {
        String estadoAntes = estadoDeLaPostulacion();
        int trabajosAntes = jdbc.queryForObject("select count(*) from trabajo_ia", Integer.class);
        JsonNode publicada = json.readTree(conTokenGet(base(), tokenTalento)
                .andReturn().getResponse().getContentAsString()).get("publicada");
        long libroDiario = opcionConTexto(listaDe(buscarPregunta(publicada, "unica").get("opciones")), "Libro diario");
        long caja = opcionConTexto(listaDe(buscarPregunta(publicada, "unica").get("opciones")), "Caja");

        // Si el total no queda en 100: 400 y no cambia nada (AC-32)
        conToken(put(base() + "/publicada/puntos"), tokenTalento, """
                {"preguntas":[{"id":%d,"puntos":15}]}""".formatted(pregunta.get("abiertaA")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.faltas[0]").value(org.hamcrest.Matchers.containsString("faltan 5")));
        // Con decimales no hay ninguna suma, solo la falta de ese campo; con 150, la suma de lo
        // escrito (AC-10)
        conToken(put(base() + "/publicada/puntos"), tokenTalento, """
                {"preguntas":[{"id":%d,"puntos":25.5}]}""".formatted(pregunta.get("abiertaA")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.faltas.length()").value(1))
                .andExpect(jsonPath("$.faltas[0]").value(org.hamcrest.Matchers.containsString(
                        "los puntos tienen que ser enteros, sin decimales.")));
        conToken(put(base() + "/publicada/puntos"), tokenTalento, """
                {"preguntas":[{"id":%d,"puntos":150}]}""".formatted(pregunta.get("abiertaA")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.faltas[0]").value("Los puntos suman 230 de 100: sobran 130."));

        // La clave estaba al revés y la abierta A pasa de 20 a 15; la B sube 5 para seguir en 100
        conToken(put(base() + "/publicada/puntos"), tokenTalento, """
                {"preguntas":[
                  {"id":%d,"puntos":4,"opciones":[{"id":%d,"puntos":0},{"id":%d,"puntos":4}]},
                  {"id":%d,"puntos":15},
                  {"id":%d,"puntos":62}]}"""
                .formatted(pregunta.get("unica"), libroDiario, caja, pregunta.get("abiertaA"),
                        pregunta.get("abiertaB")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.personas").value(1));

        // 16/20 pasa a 12/15, y la de la IA 12/20 a 9/15; quien marcó «Libro diario» pierde 4
        assertThat(jdbc.queryForObject("select puntaje from nota_respuesta where respuesta_id = ?",
                BigDecimal.class, respuesta.get("abiertaA"))).isEqualByComparingTo("12");
        assertThat(notaIaDe(respuesta.get("abiertaA"))).isEqualByComparingTo("9");
        JsonNode general = desglose().at("/porPuntos/criterios/0");
        assertThat(new BigDecimal(general.get("sistema").asText())).isEqualByComparingTo("15");
        assertThat(estadoDeLaPostulacion()).isEqualTo(estadoAntes);
        assertThat(jdbc.queryForObject("select count(*) from trabajo_ia", Integer.class))
                .isEqualTo(trabajosAntes);
        assertThat(jdbc.queryForObject("select count(*) from auditoria where accion = "
                + "'cambiar_puntos_preguntas_propias'", Integer.class)).isEqualTo(1);

        // La IA lee la descripción de la vacante al calificar, pero editarla después no
        // recalifica a nadie (AC-12b): ni un trabajo de IA nuevo
        long solicitudId = jdbc.queryForObject("select solicitud_talento_id from vacante where id = ?",
                Long.class, vacanteId);
        clearInvocations(cola);
        conToken(put("/api/v1/panel/vacantes/" + vacanteId), tokenTalento, """
                {"solicitudTalentoId": %d, "puestoId": %d, "titulo": "Asistente contable",
                 "descripcion": "Registro, cierre contable y conciliaciones bancarias",
                 "tipoCierre": "PERMANENTE", "responsableUsuarioId": 1}"""
                .formatted(solicitudId, puestoId)).andExpect(status().isOk());
        assertThat(jdbc.queryForObject("select descripcion from vacante where id = ?", String.class,
                vacanteId)).contains("conciliaciones");
        verify(cola, never()).recalificar(anyLong());
        verify(cola, never()).reencolarEvaluador(anyLong());
        verify(cola, never()).encolarPerfilIntegral(anyLong());
        assertThat(jdbc.queryForObject("select count(*) from trabajo_ia", Integer.class))
                .isEqualTo(trabajosAntes);
    }

    // ============ Copiar ============

    @DisplayName("Otra vacante copia las preguntas; la copia es independiente del original")
    @Test
    @Order(8)
    void otraVacanteCopiaLasPreguntas() throws Exception {
        // Una vacante de Dirección con sus propias preguntas publicadas, para el filtro de nivel
        long puestoDireccion = Long.parseLong(leer(conToken(post("/api/v1/panel/puestos"), tokenTalento, """
                {"codigo": "JEFE_CONT", "nombre": "Jefe de contabilidad",
                 "nivelPuestoCodigo": "DIRECCION", "familiaCodigo": "DIRECCION_NEGOCIO"}""")
                .andReturn().getResponse().getContentAsString(), "id"));
        long direccion = crearVacante("Jefe de contabilidad", puestoDireccion, "DIRECCION", "DIRECCION_NEGOCIO");
        String baseDireccion = "/api/v1/panel/vacantes/" + direccion + "/preguntas-propias";
        conToken(post("/api/v1/panel/vacantes/" + direccion + "/origen-preguntas"), tokenTalento,
                "{\"origen\":\"VACANTE\"}").andExpect(status().isOk());
        conToken(post(baseDireccion + "/preguntas"), tokenTalento, """
                {"tipo":"ABIERTA","enunciado":"¿Cómo cerrarías un ejercicio fiscal con auditoría externa?",
                 "puntos":100,"queDebeTener":"Plazos, responsables y controles"}""").andExpect(status().isOk());
        conToken(post(baseDireccion + "/publicacion"), tokenTalento, null).andExpect(status().isOk());

        long otra = crearVacante("Asistente contable II");
        JsonNode copiables = json.readTree(conTokenGet(
                "/api/v1/panel/vacantes/" + otra + "/preguntas-propias/copiables?buscar=contable",
                tokenTalento).andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        assertThat(copiables).hasSize(1);
        assertThat(copiables.get(0).get("vacanteId").asLong()).isEqualTo(vacanteId);
        assertThat(copiables.get(0).get("estado").asText()).isEqualTo("ACTIVA");
        assertThat(copiables.get(0).get("criterios").asInt()).isEqualTo(2);
        assertThat(copiables.get(0).get("preguntas").asInt()).isEqualTo(6);

        // La vista previa no toca nada (AC-15c)
        conTokenGet("/api/v1/panel/vacantes/" + otra + "/preguntas-propias/copiables/" + vacanteId,
                tokenTalento).andExpect(status().isOk()).andExpect(jsonPath("$.total").value(100));
        conTokenGet("/api/v1/panel/vacantes/" + otra + "/preguntas-propias", tokenTalento)
                .andExpect(jsonPath("$.borrador").doesNotExist());

        // El nivel solo filtra: con Ejecución no sale la de Dirección, y sin filtro sí, y se
        // puede copiar en esta vacante de Ejecución (AC-15d)
        String listado = "/api/v1/panel/vacantes/" + otra + "/preguntas-propias/copiables";
        assertThat(listaDe(json.readTree(conTokenGet(listado + "?nivel=EJECUCION", tokenTalento)
                .andReturn().getResponse().getContentAsString()))).extracting(c -> c.get("vacanteId").asLong())
                .containsExactly(vacanteId);
        assertThat(listaDe(json.readTree(conTokenGet(listado + "?nivel=DIRECCION", tokenTalento)
                .andReturn().getResponse().getContentAsString()))).extracting(c -> c.get("vacanteId").asLong())
                .containsExactly(direccion);
        assertThat(listaDe(json.readTree(conTokenGet(listado, tokenTalento)
                .andReturn().getResponse().getContentAsString()))).extracting(c -> c.get("vacanteId").asLong())
                .containsExactlyInAnyOrder(vacanteId, direccion);
        JsonNode deDireccion = json.readTree(conToken(post("/api/v1/panel/vacantes/" + otra + "/preguntas-propias/copia"),
                        tokenTalento, "{\"vacanteOrigenId\": %d}".formatted(direccion))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        assertThat(deDireccion.at("/borrador/total").asInt()).isEqualTo(100);
        assertThat(deDireccion.at("/borrador/criterios/0/preguntas/0/enunciado").asText())
                .startsWith("¿Cómo cerrarías un ejercicio fiscal");

        // Copiar otra vez reemplaza el borrador (el panel ya pidió confirmarlo)
        JsonNode copia = json.readTree(conToken(post("/api/v1/panel/vacantes/" + otra + "/preguntas-propias/copia"),
                        tokenTalento, "{\"vacanteOrigenId\": %d}".formatted(vacanteId))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        assertThat(copia.at("/borrador/total").asInt()).isEqualTo(100);
        assertThat(copia.at("/borrador/guiaCalificacion").asText()).isEqualTo("Premia el dato concreto");
        long criterioCopiado = copia.at("/borrador/criterios/0/id").asLong();
        assertThat(criterioCopiado).isNotEqualTo(criterioGeneralId);

        // Cambiar la copia no toca el original (AC-15b)
        conToken(put("/api/v1/panel/vacantes/" + otra + "/preguntas-propias/criterios/" + criterioCopiado),
                tokenTalento, "{\"nombre\":\"Contabilidad\"}").andExpect(status().isOk());
        assertThat(jdbc.queryForObject("select nombre from criterio_banco where id = ?", String.class,
                criterioGeneralId)).isEqualTo("General");
        long abiertaCopiada = buscar(copia.at("/borrador/criterios/0/preguntas"),
                "Cuéntanos un cierre con un descuadre. ¿Cómo lo hallaste?").get("id").asLong();
        assertThat(abiertaCopiada).isNotEqualTo(pregunta.get("abiertaA"));
        conToken(put("/api/v1/panel/vacantes/" + otra + "/preguntas-propias/preguntas/" + abiertaCopiada),
                tokenTalento, """
                {"tipo":"ABIERTA","enunciado":"Cuéntanos el cierre más difícil que te tocó","puntos":15,
                 "criterioId":%d}""".formatted(criterioCopiado)).andExpect(status().isOk());
        assertThat(jdbc.queryForObject("select enunciado from pregunta where id = ?", String.class,
                pregunta.get("abiertaA"))).isEqualTo("Cuéntanos un cierre con un descuadre. ¿Cómo lo hallaste?");

        // Una vacante que no existe (o de otra empresa) contesta 404 y no crea nada (AC-15)
        conTokenGet("/api/v1/panel/vacantes/" + otra + "/preguntas-propias/copiables/999999", tokenTalento)
                .andExpect(status().isNotFound());
    }

    // ============ Recomendaciones por IA ============

    @DisplayName("Lo que propone la IA queda aparte: si falla se dice, y el borrador solo cambia al agregar")
    @Test
    @Order(9)
    void laPropuestaNoTocaElBorradorHastaAgregarla() throws Exception {
        long nueva = crearVacante("Asistente contable III");
        String baseNueva = "/api/v1/panel/vacantes/" + nueva + "/preguntas-propias";
        conToken(post("/api/v1/panel/vacantes/" + nueva + "/origen-preguntas"), tokenTalento,
                "{\"origen\":\"VACANTE\"}").andExpect(status().isOk());
        conToken(post(baseNueva + "/preguntas"), tokenTalento, """
                {"tipo":"ABIERTA","enunciado":"Cuéntanos un cierre difícil","puntos":50}""")
                .andExpect(status().isOk());
        long excel = json.readTree(conToken(post(baseNueva + "/criterios"), tokenTalento,
                        "{\"nombre\":\"Manejo de Excel\"}")
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString())
                .at("/borrador/criterios/1/id").asLong();

        // Con la IA apagada se dice tal cual y no se pide nada (ni una propuesta guardada)
        conToken(post(baseNueva + "/recomendaciones"), tokenTalento, "{\"indicacion\":\"énfasis en Excel\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.encolada").value(false))
                .andExpect(jsonPath("$.mensaje").value(org.hamcrest.Matchers.containsString("apagada")));
        assertThat(propuestas.findFirstByVacanteIdOrderByIdDesc(nueva)).isEmpty();

        // Lo que entregaría el agente entra por su puente. Dos veces mal: queda FALLIDA, se
        // dice por qué y no se agrega nada (AC-14)
        pedida(nueva);
        puenteRecomendador.marcarFallida(nueva, List.of("las preguntas suman 30 y faltaban exactamente 50"));
        conTokenGet(baseNueva + "/recomendaciones", tokenTalento).andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("FALLIDA"))
                .andExpect(jsonPath("$.motivo").value(org.hamcrest.Matchers.containsString("suman 30")));
        conTokenGet(baseNueva, tokenTalento).andExpect(jsonPath("$.borrador.total").value(50));

        // Una buena: llena «Manejo de Excel» y trae un criterio nuevo, por los 50 que faltan
        pedida(nueva);
        puenteRecomendador.guardarPropuesta(nueva, new ResultadoRecomendador(List.of(
                new CriterioPropuesto(excel, null, null, List.of(
                        new PreguntaPropuesta("OPCION_UNICA", "¿Qué función busca un valor en otra tabla?",
                                BigDecimal.TEN, null, List.of(new OpcionPropuesta("BUSCARV", BigDecimal.TEN),
                                        new OpcionPropuesta("SUMA", BigDecimal.ZERO))),
                        new PreguntaPropuesta("ABIERTA", "¿Cómo armas una tabla dinámica de ventas?",
                                BigDecimal.valueOf(20), "Filas, columnas y el filtro por mes", List.of()))),
                new CriterioPropuesto(null, "Orden", "Cómo organiza su trabajo", List.of(
                        new PreguntaPropuesta("ABIERTA", "¿Cómo priorizas los cierres de fin de mes?",
                                BigDecimal.valueOf(20), null, List.of()))))));

        // Aparece la propuesta, y el borrador sigue como estaba (AC-13)
        JsonNode estado = json.readTree(conTokenGet(baseNueva + "/recomendaciones", tokenTalento)
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        assertThat(estado.get("estado").asText()).isEqualTo("LISTA");
        assertThat(estado.get("puntosQueFaltan").asInt()).isEqualTo(50);
        assertThat(estado.get("propuesta")).hasSize(2);
        JsonNode borrador = json.readTree(conTokenGet(baseNueva, tokenTalento)
                .andReturn().getResponse().getContentAsString()).get("borrador");
        assertThat(borrador.get("total").asInt()).isEqualTo(50);
        assertThat(borrador.at("/criterios/1/preguntas")).isEmpty();

        // Agregarla entera deja el balance en 100 (AC-14b)
        JsonNode agregado = json.readTree(conToken(post(baseNueva + "/recomendaciones/"
                        + estado.get("propuestaId").asLong() + "/agregados"), tokenTalento,
                "{\"criterios\":[0,1],\"preguntas\":[]}")
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        assertThat(agregado.at("/borrador/total").asInt()).isEqualTo(100);
        assertThat(agregado.at("/borrador/criterios")).hasSize(3);
        assertThat(agregado.at("/borrador/criterios/1/nombre").asText()).isEqualTo("Manejo de Excel");
        assertThat(agregado.at("/borrador/criterios/1/puntos").asInt()).isEqualTo(30);
        assertThat(agregado.at("/borrador/criterios/2/nombre").asText()).isEqualTo("Orden");
        assertThat(agregado.at("/borrador/avisos")).isEmpty();
    }

    // ============ Apoyo ============

    /** Una recomendación pedida y esperando al agente, como la deja «Pedir recomendaciones». */
    private void pedida(long vacante) {
        propuestas.save(PropuestaPreguntas.builder().organizacionId(1L).vacanteId(vacante)
                .puntosQueFaltan(50).estado(PropuestaPreguntas.PEDIDA)
                .creadoEn(java.time.Instant.now()).build());
    }

    private long crearVacante(String titulo) throws Exception {
        return crearVacante(titulo, puestoId, "EJECUCION", "OPERACIONES");
    }

    private long crearVacante(String titulo, long suPuesto, String nivel, String familia) throws Exception {
        long solicitudId = Long.parseLong(leer(conToken(post("/api/v1/panel/solicitudes"), tokenTalento, """
                {"areaId": %d, "puestoId": %d, "urgencia": "NORMAL",
                 "nivelPuestoCodigo": "%s", "familiaCodigo": "%s",
                 "resultadoPrincipal": "Cierres mensuales a tiempo",
                 "motivo": "Los cierres se atrasan",
                 "consecuenciaNoContratar": "Se pierde el control de caja",
                 "analisisCapacidad": "El equipo no alcanza",
                 "responsableUsuarioId": 1,
                 "resultadosEsperados": [
                   {"descripcion": "Cierres al día", "indicador": "antes del día 5"},
                   {"descripcion": "Conciliaciones", "indicador": "semanales"},
                   {"descripcion": "Reportes", "indicador": "mensuales"}
                 ]}""".formatted(areaId, suPuesto, nivel, familia))
                .andReturn().getResponse().getContentAsString(), "id"));
        conToken(post("/api/v1/panel/solicitudes/" + solicitudId + "/aprobacion"), tokenTalento,
                "{\"motivo\":\"Aprobada\"}").andExpect(status().isOk());
        return Long.parseLong(leer(conToken(post("/api/v1/panel/vacantes"), tokenTalento, """
                {"solicitudTalentoId": %d, "puestoId": %d,
                 "titulo": "%s", "descripcion": "Registro y cierre contable",
                 "tipoCierre": "PERMANENTE", "responsableUsuarioId": 1}"""
                .formatted(solicitudId, suPuesto, titulo))
                .andReturn().getResponse().getContentAsString(), "id"));
    }

    private long unaPruebaPublicada() throws Exception {
        long plantillaId = Long.parseLong(leer(conToken(post("/api/v1/panel/plantillas-prueba"),
                tokenTalento, "{\"nombre\":\"Prueba contable\"}")
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString(), "id"));
        long versionId = Long.parseLong(leer(conToken(
                post("/api/v1/panel/plantillas-prueba/" + plantillaId + "/versiones"), tokenTalento, """
                {"enunciado":"Resuelve el caso con cifras","modalidad":"PLAZO_ABIERTO","plazoDias":7}""")
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString(), "id"));
        long preguntaId = Long.parseLong(leer(conToken(post("/api/v1/panel/plantillas-prueba/preguntas"),
                tokenTalento, "{\"codigo\":\"PP_CONT\",\"enunciado\":\"Arma el cierre\",\"tipo\":\"ESPECIFICA\"}")
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString(), "id"));
        conToken(post("/api/v1/panel/plantillas-prueba/versiones/" + versionId + "/preguntas"),
                tokenTalento, "{\"preguntaPruebaId\": %d}".formatted(preguntaId)).andExpect(status().isOk());
        conToken(post("/api/v1/panel/plantillas-prueba/versiones/" + versionId + "/rubrica"), tokenTalento, """
                {"codigo":"CIERRE","nombre":"Cierre","puntos":100,"metodoVerificacion":"AGENTE"}""")
                .andExpect(status().isCreated());
        conToken(post("/api/v1/panel/plantillas-prueba/versiones/" + versionId + "/publicacion"),
                tokenTalento, null).andExpect(status().isOk());
        return versionId;
    }

    private void agregar(String cual, String cuerpo) throws Exception {
        JsonNode editor = json.readTree(conToken(post(base() + "/preguntas"), tokenTalento, cuerpo)
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        String enunciado = json.readTree(cuerpo).get("enunciado").asText();
        for (JsonNode c : editor.at("/borrador/criterios")) {
            for (JsonNode p : c.get("preguntas")) {
                if (p.get("enunciado").asText().equals(enunciado)) {
                    pregunta.put(cual, p.get("id").asLong());
                }
            }
        }
        assertThat(pregunta).containsKey(cual);
    }

    private JsonNode buscarPregunta(JsonNode version, String cual) {
        for (JsonNode c : version.get("criterios")) {
            for (JsonNode p : c.get("preguntas")) {
                if (p.get("id").asLong() == pregunta.get(cual)) return p;
            }
        }
        throw new AssertionError("No está la pregunta " + cual);
    }

    private static JsonNode buscar(JsonNode preguntas, String enunciado) {
        for (JsonNode p : preguntas) {
            if (p.get("enunciado").asText().equals(enunciado)) return p;
        }
        throw new AssertionError("No está: " + enunciado);
    }

    /** La fila de una vacante en la lista del panel, tal como la recibe la pantalla. */
    private JsonNode filaDeLaLista(long id) throws Exception {
        JsonNode lista = json.readTree(conTokenGet("/api/v1/panel/vacantes", tokenTalento)
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        for (JsonNode fila : lista) {
            if (fila.get("id").asLong() == id) return fila;
        }
        throw new AssertionError("La vacante " + id + " no está en la lista");
    }

    private static List<JsonNode> listaDe(JsonNode arreglo) {
        List<JsonNode> salida = new ArrayList<>();
        arreglo.forEach(salida::add);
        return salida;
    }

    private static long opcionConTexto(List<JsonNode> opciones, String texto) {
        return opciones.stream().filter(o -> o.get("texto").asText().equals(texto))
                .findFirst().orElseThrow().get("id").asLong();
    }

    private static NotaRespuestaIa nota(Long respuestaId, String puntaje, String explicacion) {
        return new NotaRespuestaIa(respuestaId, new BigDecimal(puntaje), explicacion, "cita",
                new BigDecimal("80"), null, null, null, null, null);
    }

    private BigDecimal notaIaDe(Long respuestaId) {
        return jdbc.queryForObject("select coalesce(puntaje_ia, puntaje) from nota_respuesta "
                + "where respuesta_id = ?", BigDecimal.class, respuestaId);
    }

    private BigDecimal notaDelPerfil() {
        List<BigDecimal> filas = jdbc.queryForList("select puntaje from nota_etapa where postulacion_id = ?"
                + " and etapa_codigo = 'PERFIL_INTEGRAL'", BigDecimal.class, postulacionId);
        return filas.isEmpty() ? null : filas.get(0);
    }

    private String estadoDeLaPostulacion() {
        return jdbc.queryForObject("select estado_codigo from postulacion where id = ?", String.class,
                postulacionId);
    }

    private JsonNode desglose() throws Exception {
        return json.readTree(conTokenGet("/api/v1/panel/postulaciones/" + postulacionId + "/evaluacion",
                tokenTalento).andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
    }

    private String ajuste(String cual) {
        return "/api/v1/panel/postulaciones/" + postulacionId + "/evaluacion/respuestas/"
                + respuesta.get(cual) + "/nota";
    }

    private static String base() {
        return "/api/v1/panel/vacantes/" + vacanteId + "/preguntas-propias";
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
