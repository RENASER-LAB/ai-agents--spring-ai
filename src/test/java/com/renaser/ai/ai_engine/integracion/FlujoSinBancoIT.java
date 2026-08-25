package com.renaser.ai.ai_engine.integracion;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.rabbitmq.RabbitMQContainer;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * La vacante que prescinde del banco de preguntas, de punta a punta (V30).
 *
 * <p>Es el recorrido que Renaser pidió para la vacante de Administrador: el cuestionario
 * técnico cargado como prueba del puesto vale por las dos etapas de preguntas, y el banco
 * queda apagado solo en esa vacante. Lo que este flujo asegura:
 *
 * <ul>
 *   <li>Un cuestionario —una versión con preguntas y sin entregables— se publica sin la
 *       cuota de universales y específicas, que es de las pruebas que producen algo.
 *   <li>Una vacante con la evaluación apagada se publica sin plantilla de evaluación, y
 *       quien postula cae directo en la bandeja del equipo, sin evaluación creada.
 *   <li>Confirmar su avance lo deja rindiendo el cuestionario (con su aviso por correo,
 *       que es como le llega el enlace), y entregar sin entregables es válido.
 *   <li>Con la versión de pesos que pone la prueba al 100, el semáforo propone decisión
 *       con solo la nota de la prueba: las «dos etapas» valen lo que valió el cuestionario.
 * </ul>
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@Testcontainers
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@DisplayName("La vacante sin banco: el cuestionario vale por las dos etapas")
public class FlujoSinBancoIT {

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
        // El dev-login quedo apagado por defecto en application.yaml: aqui se enciende
        // explicitamente, porque estas pruebas entran al panel por el.
        registro.add("app.seguridad.dev-login-activo", () -> "true");
        registro.add("spring.ai.deepseek.api-key", () -> "clave-de-pruebas-no-se-usa");
        // Aquí la califica una persona; el agente de la prueba lo cubre FlujoCalificacionIaIT
        registro.add("renaser.ai.calificacion.habilitada", () -> "false");
    }

    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    final ObjectMapper json = new ObjectMapper();

    static String tokenTalento;
    static String tokenCandidato;
    static String codigoPostulacion;
    static long vacanteId;
    static long postulacionId;
    static long versionPruebaId;
    static long criterioCajaId;
    static long criterioSedesId;

    @DisplayName("Un cuestionario sin entregables se publica, y la vacante sale sin banco")
    @Test
    @Order(1)
    void elCuestionarioSePublicaYLaVacanteSaleSinBanco() throws Exception {
        tokenTalento = leer(mvc.perform(post("/api/v1/panel/auth/dev-login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"usuarioRenaserOsId\":\"dev-talento\"}"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(), "token");

        // El cuestionario: tres preguntas propias, ninguna universal, ningún entregable
        long plantillaId = Long.parseLong(leer(conToken(post("/api/v1/panel/plantillas-prueba"),
                tokenTalento, "{\"nombre\":\"Cuestionario técnico de administración\"}")
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString(), "id"));
        versionPruebaId = Long.parseLong(leer(conToken(
                post("/api/v1/panel/plantillas-prueba/" + plantillaId + "/versiones"), tokenTalento, """
                {"enunciado":"Responde con experiencias reales, con cifras y resultados",
                 "modalidad":"PLAZO_ABIERTO","plazoDias":7}""")
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString(), "id"));

        for (int i = 1; i <= 3; i++) {
            long id = Long.parseLong(leer(conToken(post("/api/v1/panel/plantillas-prueba/preguntas"),
                    tokenTalento,
                    "{\"codigo\":\"CUEST_Q%d\",\"enunciado\":\"Pregunta %d del cuestionario\",\"tipo\":\"ESPECIFICA\"}"
                            .formatted(i, i))
                    .andExpect(status().isCreated())
                    .andReturn().getResponse().getContentAsString(), "id"));
            conToken(post("/api/v1/panel/plantillas-prueba/versiones/" + versionPruebaId + "/preguntas"),
                    tokenTalento, "{\"preguntaPruebaId\": %d}".formatted(id)).andExpect(status().isOk());
        }

        criterioCajaId = Long.parseLong(leer(conToken(
                post("/api/v1/panel/plantillas-prueba/versiones/" + versionPruebaId + "/rubrica"), tokenTalento, """
                {"codigo":"CAJA","nombre":"Manejo de caja","puntos":60,"metodoVerificacion":"AGENTE"}""")
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString(), "id"));
        criterioSedesId = Long.parseLong(leer(conToken(
                post("/api/v1/panel/plantillas-prueba/versiones/" + versionPruebaId + "/rubrica"), tokenTalento, """
                {"codigo":"SEDES","nombre":"Supervisión de sedes","puntos":40,"metodoVerificacion":"AGENTE"}""")
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString(), "id"));

        // Sin entregables no rige la cuota de universales y específicas: se publica
        conToken(post("/api/v1/panel/plantillas-prueba/versiones/" + versionPruebaId + "/publicacion"),
                tokenTalento, null).andExpect(status().isOk());

        // La vacante, con la evaluación apagada y sin plantilla de evaluación
        jdbc.update("INSERT INTO area (organizacion_id, nombre, es_activa) VALUES (1, 'Administración', true)");
        Long areaId = jdbc.queryForObject("SELECT id FROM area ORDER BY id DESC LIMIT 1", Long.class);
        long solicitudId = Long.parseLong(leer(conToken(post("/api/v1/panel/solicitudes"), tokenTalento, """
                {"areaId": %d, "urgencia": "PRIORITARIA",
                 "nivelPuestoCodigo": "SUPERVISION", "familiaCodigo": "OPERACIONES",
                 "resultadoPrincipal": "Una operación con procesos e indicadores",
                 "motivo": "Los errores se detectan cuando ya escalaron",
                 "consecuenciaNoContratar": "Gerencia sigue resolviendo lo operativo",
                 "analisisCapacidad": "Nadie tiene mando sobre el conjunto",
                 "responsableUsuarioId": 1,
                 "resultadosEsperados": [
                   {"descripcion": "Procesos implantados", "indicador": "cada uno con su checklist"},
                   {"descripcion": "Menos errores y reprocesos", "indicador": "reducción medible"},
                   {"descripcion": "Menos decisiones escaladas", "indicador": "suben solo las que tocan"}
                 ]}""".formatted(areaId))
                .andReturn().getResponse().getContentAsString(), "id"));
        conToken(post("/api/v1/panel/solicitudes/" + solicitudId + "/aprobacion"), tokenTalento,
                "{\"motivo\":\"Aprobada\"}").andExpect(status().isOk());
        long puestoId = Long.parseLong(leer(conToken(post("/api/v1/panel/puestos"), tokenTalento, """
                {"codigo": "ADMIN_SIN_BANCO", "nombre": "Administrador",
                 "nivelPuestoCodigo": "SUPERVISION", "familiaCodigo": "OPERACIONES"}""")
                .andReturn().getResponse().getContentAsString(), "id"));
        vacanteId = Long.parseLong(leer(conToken(post("/api/v1/panel/vacantes"), tokenTalento, """
                {"solicitudTalentoId": %d, "puestoId": %d,
                 "titulo": "Administrador", "descripcion": "Control de la operación",
                 "tipoCierre": "PERMANENTE", "responsableUsuarioId": 1}"""
                .formatted(solicitudId, puestoId))
                .andReturn().getResponse().getContentAsString(), "id"));

        conToken(post("/api/v1/panel/vacantes/" + vacanteId + "/aplicacion-evaluacion"), tokenTalento,
                "{\"aplica\": false}").andExpect(status().isOk());
        conToken(post("/api/v1/panel/vacantes/" + vacanteId + "/plantilla-prueba"), tokenTalento,
                "{\"versionPlantillaPruebaId\": %d}".formatted(versionPruebaId)).andExpect(status().isOk());

        // Publicar sin plantilla de evaluación: con la evaluación apagada, pasa
        conToken(post("/api/v1/panel/vacantes/" + vacanteId + "/publicacion"), tokenTalento, null)
                .andExpect(status().isOk());

        // Y los pesos: la prueba vale los 100 puntos de la decisión, solo aquí
        long versionPesosId = Long.parseLong(leer(conToken(post("/api/v1/panel/pesos/versiones"),
                tokenTalento, "{\"etiqueta\":\"La prueba vale todo (flujo sin banco)\"}")
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString(), "id"));
        conToken(post("/api/v1/panel/pesos/versiones/" + versionPesosId + "/etapas"), tokenTalento,
                "{\"etapaCodigo\":\"PRUEBA_PUESTO\",\"peso\":100}").andExpect(status().isCreated());
        conToken(post("/api/v1/panel/pesos/versiones/" + versionPesosId + "/publicacion"), tokenTalento, null)
                .andExpect(status().isOk());
        conToken(post("/api/v1/panel/vacantes/" + vacanteId + "/version-pesos"), tokenTalento,
                "{\"versionPesosId\": %d}".formatted(versionPesosId)).andExpect(status().isOk());
    }

    @DisplayName("Quien postula cae directo en la bandeja del equipo, sin evaluación creada")
    @Test
    @Order(2)
    void quienPostulaCaeDirectoEnLaBandeja() throws Exception {
        mvc.perform(post("/api/v1/portal/cuentas")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                        {"nombre":"Elena","apellidos":"Ramos","correo":"elena.admin@correo.pe",
                         "contrasena":"unaClaveLarga123","aceptaProceso":true,
                         "aceptaFuturosContactos":false}"""))
                .andExpect(status().isCreated());
        tokenCandidato = leer(mvc.perform(post("/api/v1/portal/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"correo\":\"elena.admin@correo.pe\",\"contrasena\":\"unaClaveLarga123\"}"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(), "token");

        MockMultipartFile cv = new MockMultipartFile("cv", "cv.pdf", "application/pdf", "contenido".getBytes());
        codigoPostulacion = leer(mvc.perform(multipart("/api/v1/portal/postulaciones")
                        .file(cv)
                        .param("vacanteId", String.valueOf(vacanteId))
                        .param("resultadoOrgulloso", "Ordené la caja de tres sedes en dos meses")
                        .header("Authorization", "Bearer " + tokenCandidato))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString(), "codigo");

        // Directo a la bandeja del equipo: el candidato no tiene ningún cuestionario del banco
        conTokenGet("/api/v1/portal/postulaciones", tokenCandidato)
                .andExpect(jsonPath("$[0].estado").value("PERFIL_POR_CONFIRMAR"));
        conTokenGet("/api/v1/portal/evaluacion/" + codigoPostulacion, tokenCandidato)
                .andExpect(status().isNotFound());

        postulacionId = jdbc.queryForObject(
                "select id from postulacion where vacante_id = ?", Long.class, vacanteId);
        Object evaluacion = jdbc.queryForMap(
                "select evaluacion_id from postulacion where id = ?", postulacionId).get("evaluacion_id");
        assertThat(evaluacion).isNull();
    }

    @DisplayName("Confirmar su avance lo deja rindiendo el cuestionario, y entrega sin entregables")
    @Test
    @Order(3)
    void confirmarElAvanceLoDejaRindiendoElCuestionario() throws Exception {
        conToken(post("/api/v1/panel/postulaciones/" + postulacionId + "/confirmacion-avance"), tokenTalento,
                "{\"motivo\":\"El currículum encaja con el puesto\"}").andExpect(status().isOk());
        conTokenGet("/api/v1/portal/postulaciones", tokenCandidato)
                .andExpect(jsonPath("$[0].estado").value("PRUEBA_TURNO_CANDIDATO"));

        JsonNode prueba = json.readTree(mvc.perform(
                        post("/api/v1/portal/prueba/" + codigoPostulacion + "/inicio")
                                .header("Authorization", "Bearer " + tokenCandidato))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estadoIntento").value("EN_CURSO"))
                .andReturn().getResponse().getContentAsString());
        assertThat(prueba.get("preguntas")).hasSize(3);
        assertThat(prueba.get("entregables")).isEmpty();

        for (JsonNode p : prueba.get("preguntas")) {
            mvc.perform(put("/api/v1/portal/prueba/" + codigoPostulacion + "/respuestas/" + p.get("id").asLong())
                            .header("Authorization", "Bearer " + tokenCandidato)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"texto\":\"Administré tres sedes con cierre diario de caja\"}"))
                    .andExpect(status().isOk());
        }

        // Sin entregables obligatorios, entregar el cuestionario respondido es válido
        mvc.perform(post("/api/v1/portal/prueba/" + codigoPostulacion + "/entrega")
                        .header("Authorization", "Bearer " + tokenCandidato))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("ENTREGADA"));
        conTokenGet("/api/v1/portal/postulaciones", tokenCandidato)
                .andExpect(jsonPath("$[0].estado").value("PRUEBA_CALIFICANDO"));
    }

    @DisplayName("El semáforo propone decisión con solo la nota del cuestionario")
    @Test
    @Order(4)
    void elSemaforoProponeConSoloElCuestionario() throws Exception {
        conToken(post("/api/v1/panel/postulaciones/" + postulacionId + "/prueba/criterios/"
                        + criterioCajaId + "/nota"), tokenTalento,
                "{\"puntaje\": 50, \"explicacion\": \"Describe el cuadre paso a paso con cifras\"}")
                .andExpect(status().isOk());
        conToken(post("/api/v1/panel/postulaciones/" + postulacionId + "/prueba/criterios/"
                        + criterioSedesId + "/nota"), tokenTalento,
                "{\"puntaje\": 35, \"explicacion\": \"Supervisión a distancia con indicadores\"}")
                .andExpect(status().isOk());
        conToken(post("/api/v1/panel/postulaciones/" + postulacionId + "/prueba/calificacion"),
                tokenTalento, null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nota").value(85.0));

        // Nada falta: la única etapa que la vacante pesa es la prueba, y con 85 propone verde
        conTokenGet("/api/v1/panel/postulaciones/" + postulacionId + "/semaforo", tokenTalento)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.etapasQueFaltan").isEmpty())
                .andExpect(jsonPath("$.notaGlobal").value(85.0))
                .andExpect(jsonPath("$.semaforo").value("VERDE"));
    }

    @DisplayName("La vacante fija cuándo cierra, y a quien tiene fecha propia no se le mueve")
    @Test
    @Order(5)
    void laVacanteFijaCuandoCierraSuPrueba() throws Exception {
        // Relativas al reloj: los dos endpoints rechazan con 400 una fecha ya pasada, así que
        // un literal futuro aguanta hasta el día que llega y luego revienta sin que nadie haya
        // tocado el código. Truncadas al segundo porque más abajo se comparan como texto
        // contra lo que devuelve la API, e Instant.toString() escribe los nanos si los hay.
        Instant base = Instant.now().truncatedTo(ChronoUnit.SECONDS);
        String domingo = base.plus(3, ChronoUnit.DAYS).toString();
        conToken(post("/api/v1/panel/vacantes/" + vacanteId + "/cierre-prueba"), tokenTalento,
                "{\"cierraEn\":\"%s\",\"motivo\":\"Cierre único de la convocatoria\"}".formatted(domingo))
                .andExpect(status().isOk());

        // Dos candidatos nuevos: uno se queda con la fecha de la vacante, al otro se le dan
        // más horas a mano.
        Cand hereda = invitarUno("hereda@correo.pe");
        Cand conLoSuyo = invitarUno("mas-horas@correo.pe");

        // Nacen con la fecha de la convocatoria, no con los siete días de la plantilla
        conTokenGet("/api/v1/portal/prueba/" + hereda.uuid(), hereda.token())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.venceEn").value(domingo));

        // La suya, más tarde que el cierre de la convocatoria: eso es «más horas a mano».
        String suya = base.plus(5, ChronoUnit.DAYS).toString();
        conToken(post("/api/v1/panel/postulaciones/" + conLoSuyo.id() + "/prueba/plazo"), tokenTalento,
                "{\"venceEn\":\"%s\",\"motivo\":\"Pidió más horas por viaje\"}".formatted(suya))
                .andExpect(status().isOk());

        // Se mueve la fecha de la convocatoria: el primero la sigue, el segundo conserva la suya
        String lunes = base.plus(4, ChronoUnit.DAYS).toString();
        conToken(post("/api/v1/panel/vacantes/" + vacanteId + "/cierre-prueba"), tokenTalento,
                "{\"cierraEn\":\"%s\",\"motivo\":\"Se amplía un día\"}".formatted(lunes))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.intentosConPlazoPropio").value(1));

        conTokenGet("/api/v1/portal/prueba/" + hereda.uuid(), hereda.token())
                .andExpect(jsonPath("$.venceEn").value(lunes));
        // Perder «más horas para esta persona» al mover la convocatoria sería silencioso
        conTokenGet("/api/v1/portal/prueba/" + conLoSuyo.uuid(), conLoSuyo.token())
                .andExpect(jsonPath("$.venceEn").value(suya));

        // Y empezar no le recalcula la fecha a ninguno
        mvc.perform(post("/api/v1/portal/prueba/" + hereda.uuid() + "/inicio")
                        .header("Authorization", "Bearer " + hereda.token()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.venceEn").value(lunes));
    }

    // ============ Apoyo ============

    private record Cand(long id, String uuid, String token) {}

    /** Crea un candidato, lo hace postular y lo deja en su turno de la prueba. */
    private Cand invitarUno(String correo) throws Exception {
        mvc.perform(post("/api/v1/portal/cuentas")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                        {"nombre":"Cand","apellidos":"Prueba","correo":"%s",
                         "contrasena":"unaClaveLarga123","aceptaProceso":true,
                         "aceptaFuturosContactos":false}""".formatted(correo)))
                .andExpect(status().isCreated());
        String token = leer(mvc.perform(post("/api/v1/portal/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"correo\":\"%s\",\"contrasena\":\"unaClaveLarga123\"}".formatted(correo)))
                .andReturn().getResponse().getContentAsString(), "token");

        MockMultipartFile cv = new MockMultipartFile("cv", "cv.pdf", "application/pdf", "x".getBytes());
        String uuid = leer(mvc.perform(multipart("/api/v1/portal/postulaciones")
                        .file(cv)
                        .param("vacanteId", String.valueOf(vacanteId))
                        .param("resultadoOrgulloso", "Cuadré la caja de tres sedes")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString(), "codigo");

        long id = jdbc.queryForObject("select p.id from postulacion p join usuario u on u.id = p.usuario_id"
                + " where u.correo = ?", Long.class, correo);
        conToken(post("/api/v1/panel/postulaciones/" + id + "/confirmacion-avance"), tokenTalento,
                "{\"motivo\":\"Pasa a la prueba\"}").andExpect(status().isOk());
        return new Cand(id, uuid, token);
    }

    private ResultActions conToken(
            org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder peticion,
            String token, String cuerpo) throws Exception {
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
