package com.renaser.ai.ai_engine.integracion;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.core.io.FileSystemResource;

import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.rabbitmq.RabbitMQContainer;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

// El hito 1 entero, de punta a punta y contra servicios reales: solicitud -> aprobación
// -> vacante -> publicar -> cuenta -> login -> postular con CV -> bandeja -> avance ->
// transición manual -> retiro. Y las reglas duras: motivo obligatorio, transiciones
// inmutables, alcance por vacante.
// Con puerto real (además de MockMvc) porque el tope de subida solo lo aplica el contenedor.
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@Testcontainers
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@DisplayName("Hito 1 · De la vacante a la postulación")
public class FlujoHito1IT {

    @LocalServerPort int puerto;
    final RestClient http = RestClient.create();

    @Container
    @ServiceConnection
    static PostgreSQLContainer postgres = new PostgreSQLContainer("pgvector/pgvector:pg16");

    @Container
    @ServiceConnection
    static RabbitMQContainer rabbit = new RabbitMQContainer("rabbitmq:3-management-alpine");

    // Sigue haciendo falta un sitio temporal, pero ya no para guardar curriculums: solo
    // para fabricar el archivo de 13 MB con el que se comprueba que el limite de subida
    // responde 413 y no un 500 mudo.
    @TempDir
    static Path carpetaTemporal;

    @DynamicPropertySource
    static void propiedades(DynamicPropertyRegistry registro) {
        // El broker de las pruebas es el contenedor, y habla en claro. Sin esto manda lo
        // que cada uno tenga en su application-secrets.yaml —hoy, un CloudAMQP con TLS— y
        // la tanda entera falla según la máquina en la que corra, que es lo peor que le
        // puede pasar a una prueba.
        registro.add("spring.rabbitmq.ssl.enabled", () -> "false");
        registro.add("spring.rabbitmq.virtual-host", () -> "/");
        // El almacen de las pruebas vive en un mapa, no en disco: no hay ningun
        // sitio donde un curriculum pueda quedarse olvidado despues de correrlas.
        registro.add("app.archivos.tipo", () -> "memoria");
        registro.add("app.seguridad.jwt-secreto",
                () -> "clave-de-pruebas-suficientemente-larga-para-hmac-256-bits");
        // El chat de agentes exige una clave para construir su bean. Aquí nadie llama al
        // modelo, pero sin este valor el contexto entero no arranca.
        registro.add("spring.ai.deepseek.api-key", () -> "clave-de-pruebas-no-se-usa");
        // La calificacion con IA se apaga en estas pruebas: aqui no se prueba, y si estuviera
        // encendida cada entrega intentaria hablar con DeepSeek con una clave de mentira.
        // Quien la prueba de verdad es FlujoCalificacionIaIT, con el modelo sustituido.
        registro.add("renaser.ai.calificacion.habilitada", () -> "false");
    }

    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    final ObjectMapper json = new ObjectMapper();

    static String tokenEquipo;
    static String tokenCandidato;
    static long solicitudId;
    static long vacanteId;
    static long requisitoId;
    static long postulacionId;
    static String codigoPostulacion;

    @DisplayName("El equipo prepara y publica una vacante")
    @Test
    @Order(1)
    void elEquipoPreparaYPublicaUnaVacante() throws Exception {
        // Bootstrap de desarrollo: el primer id crea al primer usuario del equipo
        tokenEquipo = leer(mvc.perform(post("/api/v1/panel/auth/dev-login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"usuarioRenaserOsId\":\"dev-1\"}"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(), "token");

        // Hace falta un área para la solicitud
        jdbc.update("INSERT INTO area (organizacion_id, nombre, es_activa) VALUES (1, 'Tecnología', true)");
        Long areaId = jdbc.queryForObject("SELECT id FROM area LIMIT 1", Long.class);

        String solicitud = """
                {"areaId": %d, "urgencia": "NORMAL",
                 "nivelPuestoCodigo": "EJECUCION", "familiaCodigo": "TECNOLOGIA",
                 "resultadoPrincipal": "Sostener el desarrollo del portal",
                 "motivo": "El equipo actual no llega a los plazos",
                 "consecuenciaNoContratar": "Se retrasa el MVP",
                 "analisisCapacidad": "Se evaluó automatizar y no alcanza: el trabajo es de diseño",
                 "responsableUsuarioId": 1,
                 "resultadosEsperados": [
                   {"descripcion": "Publicar el portal", "indicador": "en producción"},
                   {"descripcion": "Reducir bugs", "indicador": "la mitad de errores"},
                   {"descripcion": "Documentar el módulo", "indicador": "docs al día"}
                 ]}""".formatted(areaId);
        solicitudId = Long.parseLong(leer(conToken(post("/api/v1/panel/solicitudes"), tokenEquipo, solicitud)
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString(), "id"));

        // Sin aprobación de Dirección, la vacante no se puede crear
        conToken(post("/api/v1/panel/solicitudes/" + solicitudId + "/aprobacion"), tokenEquipo,
                "{\"motivo\":\"Justificada: hay presupuesto\"}")
                .andExpect(status().isOk());

        // Los catálogos se sirven: sin esto, cualquier formulario tendría que llevar los
        // códigos escritos a mano, que es justo lo que ya se desincronizó una vez.
        conTokenGet("/api/v1/panel/catalogos", tokenEquipo)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estados.length()").value(18))
                .andExpect(jsonPath("$.familias.length()").value(7))
                .andExpect(jsonPath("$.nivelesPuesto.length()").value(3))
                .andExpect(jsonPath("$.urgencias.length()").value(3));

        // Un código que no existe es un dato malo de quien llama, no una avería: 400 con el
        // valor culpable dentro. Antes reventaba con un 500 mudo.
        conToken(post("/api/v1/panel/puestos"), tokenEquipo, """
                {"codigo": "NO_VALE", "nombre": "Puesto imposible",
                 "nivelPuestoCodigo": "EJECUCION", "familiaCodigo": "NO_EXISTE"}""")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value(org.hamcrest.Matchers.containsString("NO_EXISTE")));

        long puestoId = Long.parseLong(leer(conToken(post("/api/v1/panel/puestos"), tokenEquipo,
                """
                {"codigo": "DEV_WEB", "nombre": "Desarrollador web",
                 "nivelPuestoCodigo": "EJECUCION", "familiaCodigo": "TECNOLOGIA"}""")
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString(), "id"));

        vacanteId = Long.parseLong(leer(conToken(post("/api/v1/panel/vacantes"), tokenEquipo,
                """
                {"solicitudTalentoId": %d, "puestoId": %d,
                 "titulo": "Desarrollador web", "descripcion": "Portal de talento",
                 "tipoCierre": "PERMANENTE", "responsableUsuarioId": 1}""".formatted(solicitudId, puestoId))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString(), "id"));

        requisitoId = Long.parseLong(leer(conToken(
                post("/api/v1/panel/vacantes/" + vacanteId + "/requisitos"), tokenEquipo,
                "{\"descripcion\":\"Disponibilidad en Arequipa\",\"regla\":\"Reside o puede trasladarse a Arequipa\"}")
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString(), "id"));

        // Sin plantilla de evaluación no se puede publicar: quien postulara quedaría esperando
        // un examen que no existe. El error sale aquí, no en la cara del candidato.
        // (Crear una vacante no asigna plantilla; se quita por si acaso para probar la regla.)
        jdbc.update("update vacante set plantilla_evaluacion_id = null where id = ?", vacanteId);
        conToken(post("/api/v1/panel/vacantes/" + vacanteId + "/publicacion"), tokenEquipo, null)
                .andExpect(status().isConflict());

        Long plantillaId = jdbc.queryForObject(
                "select id from plantilla_evaluacion where nivel_puesto_codigo = 'EJECUCION'", Long.class);
        conToken(post("/api/v1/panel/vacantes/" + vacanteId + "/plantilla-evaluacion"), tokenEquipo,
                "{\"plantillaEvaluacionId\": %d}".formatted(plantillaId))
                .andExpect(status().isOk());

        // La prueba del puesto también es obligatoria antes de publicar (RF-73). Se arma la
        // mínima válida: una plantilla, una versión, sus 8+3 preguntas y una rúbrica que suma 100.
        Long versionPruebaId = armarUnaPruebaValida(tokenEquipo);
        conToken(post("/api/v1/panel/vacantes/" + vacanteId + "/plantilla-prueba"), tokenEquipo,
                "{\"versionPlantillaPruebaId\": %d}".formatted(versionPruebaId))
                .andExpect(status().isOk());

        conToken(post("/api/v1/panel/vacantes/" + vacanteId + "/publicacion"), tokenEquipo, null)
                .andExpect(status().isOk());

        // La vacante publicada se ve sin token
        mvc.perform(get("/api/v1/portal/vacantes"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].titulo").value("Desarrollador web"));
    }

    @DisplayName("El candidato crea su cuenta y postula")
    @Test
    @Order(2)
    void elCandidatoCreaSuCuentaYPostula() throws Exception {
        mvc.perform(post("/api/v1/portal/cuentas")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"nombre": "Camila", "apellidos": "Torres",
                                 "correo": "camila@ejemplo.pe", "contrasena": "Demo12345!",
                                 "aceptaProceso": true, "aceptaFuturosContactos": true}"""))
                .andExpect(status().isCreated());

        // Con la contraseña mal es 401, no 400: la petición está bien escrita y lo que
        // falla es la identidad. Y el mensaje no dice si el correo existe o no.
        mvc.perform(post("/api/v1/portal/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"correo\":\"camila@ejemplo.pe\",\"contrasena\":\"no-es-esta\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.detail").value("Correo o contraseña incorrectos"));
        mvc.perform(post("/api/v1/portal/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"correo\":\"no-existe@ejemplo.pe\",\"contrasena\":\"Demo12345!\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.detail").value("Correo o contraseña incorrectos"));

        // Al insistir, la entrada se bloquea: 429 con Retry-After, no un 409. El tope
        // sembrado es 5, así que el sexto intento seguido ya cae bloqueado.
        for (int i = 0; i < 4; i++) {
            mvc.perform(post("/api/v1/portal/auth/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"correo\":\"bloqueo@ejemplo.pe\",\"contrasena\":\"mala\"}"))
                    .andExpect(status().isUnauthorized());
        }
        mvc.perform(post("/api/v1/portal/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"correo\":\"bloqueo@ejemplo.pe\",\"contrasena\":\"mala\"}"))
                .andExpect(status().isUnauthorized());
        mvc.perform(post("/api/v1/portal/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"correo\":\"bloqueo@ejemplo.pe\",\"contrasena\":\"mala\"}"))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().exists("Retry-After"))
                .andExpect(jsonPath("$.segundosDeEspera").exists());

        tokenCandidato = leer(mvc.perform(post("/api/v1/portal/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"correo\":\"camila@ejemplo.pe\",\"contrasena\":\"Demo12345!\"}"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(), "token");

        MockMultipartFile cv = new MockMultipartFile("cv", "cv-camila.pdf",
                "application/pdf", "contenido de prueba".getBytes());
        String respuesta = mvc.perform(multipart("/api/v1/portal/postulaciones")
                        .file(cv)
                        .param("vacanteId", String.valueOf(vacanteId))
                        .param("resultadoOrgulloso", "Rediseñé el flujo de citas y bajó el ausentismo 30%")
                        .param("portafolio", "https://camila.dev")
                        .param("requisitosConfirmados", String.valueOf(requisitoId))
                        .header("Authorization", "Bearer " + tokenCandidato))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        codigoPostulacion = leer(respuesta, "codigo");

        // Cumplió el requisito: pasó de POSTULADA a PERFIL_TURNO_CANDIDATO (dos
        // transiciones del sistema) y el CV quedó en disco
        mvc.perform(get("/api/v1/portal/postulaciones")
                        .header("Authorization", "Bearer " + tokenCandidato))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].estado").value("PERFIL_TURNO_CANDIDATO"));
        // El curriculum quedo guardado, con su ruta. Antes esto se comprobaba mirando la
        // carpeta del disco; ya no hay carpeta, asi que se comprueba donde de verdad importa:
        // que la fila exista y sepa donde esta el archivo.
        assertThat(jdbc.queryForObject(
                "select count(*) from archivo where ruta is not null", Integer.class))
                .isEqualTo(1);

        // Un CV de más de 10 MB responde 413 con explicación, no un 500 mudo. Va por HTTP real
        // y no por MockMvc a propósito: el tope lo aplica el contenedor al leer el multipart,
        // antes de que se sepa qué método atiende la llamada, y MockMvc no tiene contenedor.
        // Por lo mismo su manejador no puede ir limitado a un paquete (si lo está, sale 500).
        Path enorme = carpetaTemporal.resolve("enorme.pdf");
        Files.write(enorme, new byte[13 * 1024 * 1024]);
        MultiValueMap<String, Object> cuerpo = new LinkedMultiValueMap<>();
        cuerpo.add("cv", new FileSystemResource(enorme));
        cuerpo.add("vacanteId", vacanteId);
        cuerpo.add("resultadoOrgulloso", "algo");

        HttpStatusCode estado = http.post()
                .uri("http://localhost:" + puerto + "/api/v1/portal/postulaciones")
                .header("Authorization", "Bearer " + tokenCandidato)
                .contentType(MediaType.MULTIPART_FORM_DATA)
                .body(cuerpo)
                .exchange((peticionEnviada, respuestaRecibida) -> respuestaRecibida.getStatusCode(), false);
        assertThat(estado.value()).isEqualTo(413);
    }

    @DisplayName("El equipo mueve la postulación de etapa")
    @Test
    @Order(3)
    void elEquipoMueveLaPostulacion() throws Exception {
        // La bandeja dice a quién se espera: ahora mismo, al candidato
        String bandeja = conTokenGet("/api/v1/panel/bandeja?espera_a=CANDIDATO", tokenEquipo)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].estado").value("PERFIL_TURNO_CANDIDATO"))
                .andReturn().getResponse().getContentAsString();
        postulacionId = json.readTree(bandeja).get(0).get("postulacionId").asLong();

        // El avance calculado: PERFIL_TURNO_CANDIDATO -> PERFIL_CALIFICANDO
        conToken(post("/api/v1/panel/postulaciones/" + postulacionId + "/confirmacion-avance"),
                tokenEquipo, "{\"motivo\":\"El candidato completó su parte\"}")
                .andExpect(status().isOk());

        // Una transición manual SIN motivo no pasa
        conToken(post("/api/v1/panel/postulaciones/" + postulacionId + "/transiciones"),
                tokenEquipo, "{\"estadoDestino\":\"PERFIL_POR_CONFIRMAR\",\"motivo\":\"\"}")
                .andExpect(status().isBadRequest());

        // Con motivo, sí
        conToken(post("/api/v1/panel/postulaciones/" + postulacionId + "/transiciones"),
                tokenEquipo, "{\"estadoDestino\":\"PERFIL_POR_CONFIRMAR\",\"motivo\":\"Revisión manual del hito 1\"}")
                .andExpect(status().isOk());

        conTokenGet("/api/v1/panel/postulaciones/" + postulacionId + "/historial", tokenEquipo)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(4));
    }

    @DisplayName("El candidato se retira y todo queda registrado")
    @Test
    @Order(4)
    void elCandidatoSeRetiraYTodoQuedaRegistrado() throws Exception {
        mvc.perform(post("/api/v1/portal/postulaciones/" + codigoPostulacion + "/retiro")
                        .header("Authorization", "Bearer " + tokenCandidato))
                .andExpect(status().isOk());

        // El registro inmutable: la base rechaza cualquier UPDATE sobre las transiciones
        assertThatThrownBy(() ->
                jdbc.update("UPDATE transicion_estado SET motivo = 'adulterado' WHERE id = 1"))
                .hasMessageContaining("no admite UPDATE ni DELETE");

        // Quedó el rastro completo: correos con su texto exacto y auditoría
        Integer correos = jdbc.queryForObject("SELECT count(*) FROM correo_enviado", Integer.class);
        assertThat(correos).isGreaterThanOrEqualTo(3); // cuenta creada, recibida, retiro
        Integer auditorias = jdbc.queryForObject("SELECT count(*) FROM auditoria", Integer.class);
        assertThat(auditorias).isGreaterThan(0);
    }

    @DisplayName("El responsable de área solo ve lo suyo")
    @Test
    @Order(5)
    void elResponsableDeAreaSoloVeLoSuyo() throws Exception {
        // Un responsable de área cuyo alcance es SUS_VACANTES, sin ninguna vacante a su cargo
        conToken(post("/api/v1/panel/usuarios"), tokenEquipo, """
                {"nombre": "Marco", "apellidos": "Quispe", "correo": "marco@renaser.pe",
                 "usuarioRenaserOsId": "os-77", "roles": ["RESPONSABLE_AREA"]}""")
                .andExpect(status().isCreated());
        String tokenArea = leer(mvc.perform(post("/api/v1/panel/auth/dev-login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"usuarioRenaserOsId\":\"os-77\"}"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString(), "token");

        // La bandeja existe para él, pero solo con las postulaciones de SUS vacantes: vacía
        conTokenGet("/api/v1/panel/bandeja?espera_a=CANDIDATO", tokenArea)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));

        // Y lo que su rol no tiene, es un 403 con explicación, no un error opaco
        conTokenGet("/api/v1/panel/parametros", tokenArea)
                .andExpect(status().isForbidden());
    }

    // ============ ayudas ============

    // La mínima prueba del puesto publicable: 8 universales + 3 específicas (RF-83), y una
    // rúbrica de un solo criterio que ya suma 100. No prueba el hito 3 a fondo -eso lo hace
    // FlujoPruebaIT-, solo lo que hace falta para que una vacante de este flujo pueda publicarse.
    private Long armarUnaPruebaValida(String token) throws Exception {
        long plantillaId = Long.parseLong(leer(conToken(post("/api/v1/panel/plantillas-prueba"), token,
                "{\"nombre\":\"Prueba genérica\"}")
                .andReturn().getResponse().getContentAsString(), "id"));
        long versionId = Long.parseLong(leer(conToken(
                post("/api/v1/panel/plantillas-prueba/" + plantillaId + "/versiones"), token, """
                {"enunciado":"Resuelve el caso propuesto","modalidad":"CRONOMETRADA",
                 "duracionMinutos":90,"minutoCambioMin":30,"minutoCambioMax":50,"minutosExtra":10}""")
                .andReturn().getResponse().getContentAsString(), "id"));

        for (int i = 0; i < 8; i++) {
            String codigo = "UNIV_H1_" + i;
            long id = Long.parseLong(leer(conToken(post("/api/v1/panel/plantillas-prueba/preguntas"), token,
                    "{\"codigo\":\"%s\",\"enunciado\":\"Pregunta universal %d\",\"tipo\":\"UNIVERSAL\"}"
                            .formatted(codigo, i))
                    .andReturn().getResponse().getContentAsString(), "id"));
            conToken(post("/api/v1/panel/plantillas-prueba/versiones/" + versionId + "/preguntas"), token,
                    "{\"preguntaPruebaId\": %d}".formatted(id)).andExpect(status().isOk());
        }
        for (int i = 0; i < 3; i++) {
            String codigo = "ESP_H1_" + i;
            long id = Long.parseLong(leer(conToken(post("/api/v1/panel/plantillas-prueba/preguntas"), token,
                    "{\"codigo\":\"%s\",\"enunciado\":\"Pregunta específica %d\",\"tipo\":\"ESPECIFICA\"}"
                            .formatted(codigo, i))
                    .andReturn().getResponse().getContentAsString(), "id"));
            conToken(post("/api/v1/panel/plantillas-prueba/versiones/" + versionId + "/preguntas"), token,
                    "{\"preguntaPruebaId\": %d}".formatted(id)).andExpect(status().isOk());
        }
        conToken(post("/api/v1/panel/plantillas-prueba/versiones/" + versionId + "/rubrica"), token, """
                {"codigo":"RESULTADO_H1","nombre":"Resultado","puntos":100,"metodoVerificacion":"PERSONA"}""")
                .andExpect(status().isCreated());

        conToken(post("/api/v1/panel/plantillas-prueba/versiones/" + versionId + "/publicacion"), token, null)
                .andExpect(status().isOk());
        return versionId;
    }

    private org.springframework.test.web.servlet.ResultActions conToken(
            org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder peticion,
            String token, String cuerpo) throws Exception {
        peticion.header("Authorization", "Bearer " + token);
        if (cuerpo != null) {
            peticion.contentType(MediaType.APPLICATION_JSON).content(cuerpo);
        }
        return mvc.perform(peticion);
    }

    private org.springframework.test.web.servlet.ResultActions conTokenGet(String ruta, String token)
            throws Exception {
        return mvc.perform(get(ruta).header("Authorization", "Bearer " + token));
    }

    private String leer(String cuerpoRespuesta, String campo) throws Exception {
        JsonNode nodo = json.readTree(cuerpoRespuesta).get(campo);
        assertThat(nodo).as("campo %s en %s", campo, cuerpoRespuesta).isNotNull();
        return nodo.asText();
    }
}
