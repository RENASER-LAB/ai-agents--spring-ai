package com.renaser.ai.ai_engine.integracion;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.renaser.ai.ai_engine.integracion.soporte.ImagenesDeContenedores;
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
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.rabbitmq.RabbitMQContainer;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * La ciudad de la vacante (V62), de punta a punta y contra la base de verdad.
 *
 * <p>{@code CambiosDeLaVacanteTest} ya cubre cómo se cuenta el cambio de ciudad con dobles.
 * Lo que solo tiene respuesta con una base y un HTTP de por medio, y por eso existe esta
 * prueba:
 *
 * <ul>
 *   <li><b>El 400 de la spec</b> (AC-33): un departamento («04»), un distrito («040101») y un
 *       código inventado («999999») contestan «Esa ciudad no está en el catálogo» y <b>no
 *       dejan ninguna fila</b>; sin ciudad se guarda, y con una provincia también.
 *   <li><b>Que editar solo el horario no toque la modalidad ni la ciudad</b> (AC-29): el
 *       formulario devuelve «PRESENCIAL» y «1501» tal cual, y el aviso nombra solo el
 *       horario.
 *   <li><b>Que ponerle ciudad a una publicada avise una sola vez por persona en carrera</b>,
 *       con «Ciudad: — → Arequipa» y sin nombrar la zona, que no cambió (AC-30).
 *   <li><b>Que el tablón público lleve la fecha de publicación y la ciudad con nombre y
 *       departamento</b>, que es lo que la pantalla de búsqueda filtra.
 * </ul>
 *
 * <p>Los pasos van en orden y comparten estado: las dos vacantes que crea el segundo son las
 * que publica, edita y lee el resto.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@DisplayName("La vacante tiene una ciudad del catálogo, y el portal la lee")
public class FlujoCiudadDeLaVacanteIT {

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
    final ObjectMapper json = new ObjectMapper();

    private static final String RECHAZO = "Esa ciudad no está en el catálogo";
    private static final String LIMA = "1501";
    private static final String AREQUIPA = "0401";

    static String tokenEquipo;
    static Long areaId;
    static long puestoId;
    /** La que nace sin ciudad y con zona «Selva Alegre»: la de la AC-30. */
    static long solicitudSinCiudad;
    static long vacanteSinCiudad;
    /** La que nace en Lima con «PRESENCIAL»: la de la AC-29. */
    static long solicitudEnLima;
    static long vacanteEnLima;

    // ============ 1. El terreno ============

    @Test
    @Order(1)
    @DisplayName("el equipo entra y tiene un puesto y dos solicitudes aprobadas")
    void elTerreno() throws Exception {
        tokenEquipo = leer(mvc.perform(post("/api/v1/panel/auth/dev-login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"usuarioRenaserOsId\":\"dev-ciudad\"}"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(), "token");

        jdbc.update("INSERT INTO area (organizacion_id, nombre, es_activa) VALUES (1, 'Operaciones', true)");
        areaId = jdbc.queryForObject("SELECT id FROM area LIMIT 1", Long.class);
        puestoId = Long.parseLong(leer(conToken(post("/api/v1/panel/puestos"), tokenEquipo, """
                {"nombre": "Coordinador", "nivelPuestoCodigo": "EJECUCION",
                 "familiaCodigo": "OPERACIONES"}""")
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString(), "id"));
        solicitudSinCiudad = crearSolicitudAprobada();
        solicitudEnLima = crearSolicitudAprobada();
    }

    // ============ 2. AC-33: lo que el catálogo no ofrece, se rechaza ============

    @Test
    @Order(2)
    @DisplayName("un departamento, un distrito o un código inventado contestan 400 y no guardan nada; sin ciudad y con provincia, sí")
    void loQueElCatalogoNoOfreceSeRechaza() throws Exception {
        for (String codigo : List.of("04", "040101", "999999")) {
            conToken(post("/api/v1/panel/vacantes"), tokenEquipo,
                    cuerpoDeVacante(solicitudSinCiudad, "Especialista en Marketing Digital",
                            "\"modalidad\": \"Presencial\", \"ubicacion\": \"Selva Alegre\", "
                                    + "\"ciudadUbigeo\": \"" + codigo + "\""))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.detail").value(containsString(RECHAZO)));
        }
        assertThat(contar("select count(*) from vacante"))
                .as("un rechazo no deja ni media vacante")
                .isZero();
        assertThat(jdbc.queryForObject("select estado from solicitud_talento where id = ?",
                String.class, solicitudSinCiudad))
                .as("ni marca la solicitud como usada")
                .isEqualTo("ABIERTA");

        // Sin ciudad se acepta: la exigencia es del formulario del panel, no de la API.
        vacanteSinCiudad = Long.parseLong(leer(conToken(post("/api/v1/panel/vacantes"), tokenEquipo,
                cuerpoDeVacante(solicitudSinCiudad, "Especialista en Marketing Digital",
                        "\"modalidad\": \"Presencial\", \"ubicacion\": \"Selva Alegre\""))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString(), "id"));
        assertThat(jdbc.queryForObject("select ciudad_ubigeo from vacante where id = ?",
                String.class, vacanteSinCiudad)).isNull();

        // Y con una provincia, se guarda su código. Con la modalidad como la escribe el
        // script: en mayúsculas, que es lo que la AC-29 necesita que se conserve.
        vacanteEnLima = Long.parseLong(leer(conToken(post("/api/v1/panel/vacantes"), tokenEquipo,
                cuerpoDeVacante(solicitudEnLima, "Administrador",
                        "\"modalidad\": \"PRESENCIAL\", \"ciudadUbigeo\": \"" + LIMA + "\""))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString(), "id"));
        assertThat(jdbc.queryForObject("select ciudad_ubigeo from vacante where id = ?",
                String.class, vacanteEnLima)).isEqualTo(LIMA);
    }

    // ============ 3. El panel devuelve la ciudad con nombre ============

    @Test
    @Order(3)
    @DisplayName("la lista y el detalle del panel llevan la ciudad con su código y su nombre, o vacía")
    void elPanelLaDevuelveConNombre() throws Exception {
        conToken(get("/api/v1/panel/vacantes/" + vacanteEnLima), tokenEquipo, null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ciudad.codigo").value(LIMA))
                .andExpect(jsonPath("$.ciudad.nombre").value("Lima"))
                .andExpect(jsonPath("$.modalidad").value("PRESENCIAL"));
        conToken(get("/api/v1/panel/vacantes"), tokenEquipo, null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.id == " + vacanteEnLima + ")].ciudad.nombre").value("Lima"))
                .andExpect(jsonPath("$[?(@.id == " + vacanteSinCiudad + ")].ciudad").value(
                        org.hamcrest.Matchers.contains((Object) null)));
    }

    // ============ 4. AC-29: editar solo el horario no toca la modalidad ni la ciudad ============

    @Test
    @Order(4)
    @DisplayName("con «PRESENCIAL» y Lima guardados, cambiar solo el horario deja los dos igual y el aviso nombra solo el horario")
    void editarSoloElHorario() throws Exception {
        publicarConAlguienDentro(vacanteEnLima);
        long avisosAntes = contar("select count(*) from aviso_portal");

        conToken(put("/api/v1/panel/vacantes/" + vacanteEnLima), tokenEquipo,
                cuerpoDeVacante(solicitudEnLima, "Administrador",
                        "\"modalidad\": \"PRESENCIAL\", \"ciudadUbigeo\": \"" + LIMA + "\", "
                                + "\"horario\": \"Tiempo completo\""))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.huboCambios").value(true))
                .andExpect(jsonPath("$.postulantesAvisados").value(1));

        assertThat(jdbc.queryForObject("select modalidad from vacante where id = ?",
                String.class, vacanteEnLima))
                .as("la modalidad se guarda letra por letra: «PRESENCIAL» no se corrige sola")
                .isEqualTo("PRESENCIAL");
        assertThat(jdbc.queryForObject("select ciudad_ubigeo from vacante where id = ?",
                String.class, vacanteEnLima)).isEqualTo(LIMA);
        assertThat(contar("select count(*) from aviso_portal")).isEqualTo(avisosAntes + 1);
        String cuerpo = ultimoAviso();
        assertThat(cuerpo).contains("Horario: sin indicar → Tiempo completo");
        assertThat(cuerpo).doesNotContain("Ciudad").doesNotContain("Modalidad")
                .doesNotContain("Zona");
    }

    // ============ 5. AC-30: ponerle ciudad a una publicada avisa «Ciudad: — → Arequipa» ============

    @Test
    @Order(5)
    @DisplayName("elegir Arequipa en una publicada sin ciudad deja un solo aviso por persona con «Ciudad: — → Arequipa», y la zona no cambia")
    void ponerleCiudadAvisa() throws Exception {
        publicarConAlguienDentro(vacanteSinCiudad);
        long avisosAntes = contar("select count(*) from aviso_portal");

        conToken(put("/api/v1/panel/vacantes/" + vacanteSinCiudad), tokenEquipo,
                cuerpoDeVacante(solicitudSinCiudad, "Especialista en Marketing Digital",
                        "\"modalidad\": \"Presencial\", \"ubicacion\": \"Selva Alegre\", "
                                + "\"ciudadUbigeo\": \"" + AREQUIPA + "\""))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.postulantesAvisados").value(1));

        assertThat(contar("select count(*) from aviso_portal")).isEqualTo(avisosAntes + 1);
        String cuerpo = ultimoAviso();
        assertThat(cuerpo).contains("Ciudad: — → Arequipa");
        assertThat(cuerpo).as("la zona no cambió y no se nombra").doesNotContain("Zona");
        assertThat(jdbc.queryForObject("select ubicacion from vacante where id = ?",
                String.class, vacanteSinCiudad)).isEqualTo("Selva Alegre");
        assertThat(jdbc.queryForObject("select ciudad_ubigeo from vacante where id = ?",
                String.class, vacanteSinCiudad)).isEqualTo(AREQUIPA);
        // Y queda en la auditoría de la vacante.
        assertThat(contar("""
                select count(*) from auditoria
                 where accion = 'editar_vacante' and entidad = 'vacante' and entidad_id = %d
                   and valor_nuevo::text like '%%ciudadUbigeo%%'""".formatted(vacanteSinCiudad)))
                .isEqualTo(1);
    }

    // ============ 6. Al editar tampoco entra un código de fuera del catálogo ============

    @Test
    @Order(6)
    @DisplayName("al editar, un código de fuera del catálogo contesta 400 y no cambia nada; vaciar la ciudad sí se puede")
    void alEditarTampocoEntra() throws Exception {
        long avisosAntes = contar("select count(*) from aviso_portal");
        conToken(put("/api/v1/panel/vacantes/" + vacanteSinCiudad), tokenEquipo,
                cuerpoDeVacante(solicitudSinCiudad, "Especialista en Marketing Digital",
                        "\"modalidad\": \"Presencial\", \"ubicacion\": \"Selva Alegre\", "
                                + "\"horario\": \"9am-6pm\", \"ciudadUbigeo\": \"04\""))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value(containsString(RECHAZO)));
        assertThat(jdbc.queryForObject("select horario from vacante where id = ?",
                String.class, vacanteSinCiudad))
                .as("un rechazo no guarda ni el resto del formulario")
                .isNull();
        assertThat(contar("select count(*) from aviso_portal")).isEqualTo(avisosAntes);

        // Quitar la ciudad se acepta —la API no la exige— y se cuenta.
        conToken(put("/api/v1/panel/vacantes/" + vacanteSinCiudad), tokenEquipo,
                cuerpoDeVacante(solicitudSinCiudad, "Especialista en Marketing Digital",
                        "\"modalidad\": \"Presencial\", \"ubicacion\": \"Selva Alegre\""))
                .andExpect(status().isOk());
        assertThat(jdbc.queryForObject("select ciudad_ubigeo from vacante where id = ?",
                String.class, vacanteSinCiudad)).isNull();
        assertThat(ultimoAviso()).contains("Ciudad: Arequipa → —");

        // Y se vuelve a poner, para que el tablón tenga qué enseñar.
        conToken(put("/api/v1/panel/vacantes/" + vacanteSinCiudad), tokenEquipo,
                cuerpoDeVacante(solicitudSinCiudad, "Especialista en Marketing Digital",
                        "\"modalidad\": \"Presencial\", \"ubicacion\": \"Selva Alegre\", "
                                + "\"ciudadUbigeo\": \"" + AREQUIPA + "\""))
                .andExpect(status().isOk());
    }

    // ============ 7. El tablón público ============

    @Test
    @Order(7)
    @DisplayName("el tablón público lleva la fecha de publicación y la ciudad con nombre y departamento")
    void elTablonLaEnsena() throws Exception {
        mvc.perform(get("/api/v1/portal/vacantes"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.id == " + vacanteSinCiudad + ")].ciudad.codigo").value(AREQUIPA))
                .andExpect(jsonPath("$[?(@.id == " + vacanteSinCiudad + ")].ciudad.nombre").value("Arequipa"))
                .andExpect(jsonPath("$[?(@.id == " + vacanteSinCiudad + ")].ciudad.departamento").value("Arequipa"))
                .andExpect(jsonPath("$[?(@.id == " + vacanteSinCiudad + ")].ubicacion").value("Selva Alegre"))
                .andExpect(jsonPath("$[?(@.id == " + vacanteEnLima + ")].ciudad.nombre").value("Lima"))
                .andExpect(jsonPath("$[?(@.id == " + vacanteEnLima + ")].publicadaEn").isNotEmpty());
        mvc.perform(get("/api/v1/portal/vacantes/" + vacanteEnLima))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ciudad.codigo").value(LIMA))
                .andExpect(jsonPath("$.ciudad.departamento").value("Lima"))
                .andExpect(jsonPath("$.publicadaEn").isNotEmpty());
    }

    // ---------- ayudas ----------

    /** La vacante pasa a PUBLICADA con una persona en carrera dentro. */
    private void publicarConAlguienDentro(long vacanteId) {
        // Publicar exige plantilla de prueba, banco y demás; aquí se prueba la ciudad, no la
        // publicación, así que el estado se escribe a mano. Lo que no se escribe a mano es el
        // estado de la postulación: nace en PERFIL_POR_CONFIRMAR, que es en carrera.
        jdbc.update("update vacante set estado = 'PUBLICADA', publicada_en = now() where id = ?",
                vacanteId);
        Long usuarioId = jdbc.queryForObject(
                "select id from usuario where usuario_renaser_os_id = 'dev-ciudad'", Long.class);
        jdbc.update("""
                insert into postulacion (organizacion_id, usuario_id, vacante_id, estado_codigo)
                values (1, ?, ?, 'PERFIL_POR_CONFIRMAR')""", usuarioId, vacanteId);
    }

    private String ultimoAviso() {
        return jdbc.queryForObject("select cuerpo from aviso_portal order by id desc limit 1",
                String.class);
    }

    private String cuerpoDeVacante(long solicitud, String titulo, String camposExtra) {
        return """
                {"solicitudTalentoId": %d, "titulo": "%s", "descripcion": "Lleva la sede",
                 "tipoCierre": "PERMANENTE", "responsableUsuarioId": 1,
                 "remuneracion": {"tipo": "OCULTA"}, %s}""".formatted(solicitud, titulo, camposExtra);
    }

    private long contar(String sql) {
        Long cuantos = jdbc.queryForObject(sql, Long.class);
        return cuantos == null ? 0 : cuantos;
    }

    private long crearSolicitudAprobada() throws Exception {
        long id = Long.parseLong(leer(conToken(post("/api/v1/panel/solicitudes"), tokenEquipo, """
                {"areaId": %d, "puestoId": %d, "urgencia": "NORMAL",
                 "nivelPuestoCodigo": "EJECUCION", "familiaCodigo": "OPERACIONES",
                 "resultadoPrincipal": "Sostener la operación de la sede",
                 "motivo": "El equipo actual no llega",
                 "consecuenciaNoContratar": "Se retrasa la apertura",
                 "analisisCapacidad": "Se evaluó redistribuir y no alcanza",
                 "responsableUsuarioId": 1,
                 "resultadosEsperados": [
                   {"descripcion": "Abrir la sede", "indicador": "en marcha"},
                   {"descripcion": "Formar al equipo", "indicador": "tres personas"},
                   {"descripcion": "Dejar el turno cubierto", "indicador": "sin huecos"}
                 ]}""".formatted(areaId, puestoId))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString(), "id"));
        conToken(post("/api/v1/panel/solicitudes/" + id + "/aprobacion"), tokenEquipo,
                "{\"motivo\":\"Aprobada para la prueba\"}")
                .andExpect(status().isOk());
        return id;
    }

    private ResultActions conToken(MockHttpServletRequestBuilder peticion, String token, String cuerpo)
            throws Exception {
        peticion.header("Authorization", "Bearer " + token);
        if (cuerpo != null) {
            peticion.contentType(MediaType.APPLICATION_JSON).content(cuerpo);
        }
        return mvc.perform(peticion);
    }

    private String leer(String cuerpoRespuesta, String campo) throws Exception {
        JsonNode nodo = json.readTree(cuerpoRespuesta).get(campo);
        assertThat(nodo).as("campo %s en %s", campo, cuerpoRespuesta).isNotNull();
        return nodo.asText();
    }
}
