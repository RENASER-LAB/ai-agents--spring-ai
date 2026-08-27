package com.renaser.ai.ai_engine.integracion;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.renaser.ai.ai_engine.integracion.soporte.ImagenesDeContenedores;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.test.web.server.LocalServerPort;
import com.renaser.ai.ai_engine.integracion.soporte.TrazaHttp;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
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
// Apunta cada peticion y respuesta cuando se corre con -Dtraza=si
@Import(TrazaHttp.class)
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
    static RabbitMQContainer rabbit = new RabbitMQContainer(ImagenesDeContenedores.RABBITMQ);

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
        // El dev-login quedo apagado por defecto en application.yaml: aqui se enciende
        // explicitamente, porque estas pruebas entran al panel por el.
        registro.add("app.seguridad.dev-login-activo", () -> "true");
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
                        .param("aceptaTratamiento", "true")
                        .param("portafolio", "https://camila.dev")
                        .param("requisitosConfirmados", String.valueOf(requisitoId))
                        .header("Authorization", "Bearer " + tokenCandidato))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        codigoPostulacion = leer(respuesta, "codigo");

        // CP-04 · quien NO confirma un requisito activo se descarta solo, con el motivo escrito.
        //
        // El caso estaba implementado desde hacia meses y sin ninguna prueba: se ejercitaba
        // solo el camino feliz —el de arriba, que si confirma— asi que nadie sabia si la
        // rama del descarte seguia funcionando. Es el punto CP-04 del checklist del Sprint 1.
        mvc.perform(post("/api/v1/portal/cuentas")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"nombre": "Bruno", "apellidos": "Quispe",
                                 "correo": "no.cumple@ejemplo.pe", "contrasena": "Demo12345!",
                                 "aceptaProceso": true, "aceptaFuturosContactos": false}"""))
                .andExpect(status().isCreated());
        String tokenSinRequisito = leer(mvc.perform(post("/api/v1/portal/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"correo\":\"no.cumple@ejemplo.pe\",\"contrasena\":\"Demo12345!\"}"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(), "token");

        mvc.perform(multipart("/api/v1/portal/postulaciones")
                        .file(new MockMultipartFile("cv", "cv.pdf", "application/pdf", "x".getBytes()))
                        .param("vacanteId", String.valueOf(vacanteId))
                        .param("resultadoOrgulloso", "Algo de lo que estoy orgulloso")
                        // Sin requisitosConfirmados: no confirma el que la vacante exige
                        .header("Authorization", "Bearer " + tokenSinRequisito))
                .andExpect(status().isCreated());

        // Se descarta sola, sin que nadie del equipo intervenga
        mvc.perform(get("/api/v1/portal/postulaciones")
                        .header("Authorization", "Bearer " + tokenSinRequisito))
                .andExpect(jsonPath("$[0].estado").value("NO_CONTINUA"));

        // Y el motivo queda escrito y es legible: «descartado» sin decir por que obliga al
        // candidato a preguntar y al equipo a reconstruirlo.
        Long idDescartada = jdbc.queryForObject("""
                select p.id from postulacion p join usuario u on u.id = p.usuario_id
                 where u.correo = 'no.cumple@ejemplo.pe'
                """, Long.class);
        assertThat(jdbc.queryForObject(
                "select motivo_cierre from postulacion where id = ?", String.class, idDescartada))
                .isEqualTo("REQUISITO_OBJETIVO");
        assertThat(jdbc.queryForObject("""
                select motivo from transicion_estado
                 where postulacion_id = ? and estado_nuevo_codigo = 'NO_CONTINUA'
                """, String.class, idDescartada))
                .contains("Requisito objetivo no cumplido");

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

    @DisplayName("El candidato entra con el enlace del correo, sin contraseña")
    @Test
    @Order(6)
    void elCandidatoEntraConElEnlaceDelCorreo() throws Exception {
        // Por que existe esto: los candidatos que llegan por una carga masiva de curriculums
        // tienen cuenta, pero con un correo inventado del nombre del archivo y una clave que
        // nadie les dijo. Por la puerta de /auth/login no pasan, y no hay pantalla de
        // recuperar contrasena. Sin el enlace, el aviso que se les manda lleva a una puerta
        // cerrada y todo lo que el portal sabe hacer les queda fuera de alcance.

        String enlace = conToken(post("/api/v1/panel/postulaciones/" + postulacionId + "/enlace-acceso"),
                tokenEquipo, null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.venceEn").isNotEmpty())
                .andReturn().getResponse().getContentAsString();
        String url = leer(enlace, "url");
        assertThat(url).contains("/acceso?token=");

        String token = java.net.URLDecoder.decode(
                url.substring(url.indexOf("token=") + 6), java.nio.charset.StandardCharsets.UTF_8);

        // El token de verdad no esta en la base: solo su hash. Si apareciera en claro,
        // guardarlo seria como guardar una contrasena sin cifrar.
        Integer enClaro = jdbc.queryForObject(
                "SELECT count(*) FROM enlace_acceso WHERE token_hash = ?", Integer.class, token);
        assertThat(enClaro).as("la base no puede tener el token tal cual").isZero();

        // Se canjea SIN autenticacion: quien lo usa todavia no tiene sesion. Ese es el punto.
        String sesion = mvc.perform(post("/api/v1/portal/auth/acceso")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"token\":\"" + token + "\"}"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        String tokenDelEnlace = leer(sesion, "token");

        // Y la sesion que devuelve sirve de verdad: ve SU postulacion, la que le toca.
        conTokenGet("/api/v1/portal/postulaciones", tokenDelEnlace)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1));

        // No es de un solo uso: la sesion dura dos horas y el plazo del candidato son dias,
        // asi que gastarlo al primer clic lo dejaria fuera al dia siguiente.
        mvc.perform(post("/api/v1/portal/auth/acceso")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"token\":\"" + token + "\"}"))
                .andExpect(status().isOk());

        // El ultimo, no «el» de la postulacion: una postulacion puede tener varios enlaces.
        // MaquinaEstados genera uno cada vez que avisa al candidato, asi que al llegar aqui
        // ya hay los de los avisos de las etapas anteriores mas el que pidio esta prueba.
        // Sin el ORDER BY esto reventaba con «expected 1, actual 3» segun por donde hubiera
        // pasado la postulacion, que es lo peor: falla o no segun el resto del recorrido.
        Integer usos = jdbc.queryForObject(
                "SELECT usos FROM enlace_acceso WHERE postulacion_id = ? ORDER BY id DESC LIMIT 1",
                Integer.class, postulacionId);
        assertThat(usos).as("se anota cada uso, para saber si el candidato llego a entrar").isEqualTo(2);

        // Un token inventado no entra, y dice lo mismo que uno vencido: distinguirlos le
        // diria a quien prueba al azar cuales existieron alguna vez.
        mvc.perform(post("/api/v1/portal/auth/acceso")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"token\":\"me-lo-acabo-de-inventar\"}"))
                .andExpect(status().isUnauthorized());

        // Y uno revocado tampoco, aunque no haya vencido.
        jdbc.update("UPDATE enlace_acceso SET revocado_en = now() WHERE postulacion_id = ?", postulacionId);
        mvc.perform(post("/api/v1/portal/auth/acceso")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"token\":\"" + token + "\"}"))
                .andExpect(status().isUnauthorized());
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
