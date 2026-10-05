package com.renaser.ai.ai_engine.integracion;

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
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.rabbitmq.RabbitMQContainer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * El catálogo de preguntas de la prueba del puesto es solo de la plataforma (spec
 * {@code fuga-del-catalogo-de-preguntas}, AC-03 a AC-06), con dos empresas de verdad.
 *
 * <p>El catálogo no tiene dueño y su código es único en toda la tabla. Abierto a cualquier
 * empresa, ACME recibía el examen que escribió la plataforma (o cualquier otra empresa),
 * podía ir eligiendo ids correlativos para leer textos ajenos dentro de su propio borrador,
 * y el primer código que usara quedaba ocupado para todas y para siempre. Aquí:
 *
 * <ul>
 *   <li>a ACME, con el permiso, el catálogo le contesta 404 al listar, crear y elegir, y no
 *       se escribe nada —ni la pregunta, ni la elección, ni la auditoría—;
 *   <li>el orden es permiso → empresa → cuerpo: sin permiso sigue el 403, y a ACME le llega
 *       el 404 aunque el cuerpo venga vacío o mal;
 *   <li>la plataforma lista, crea y elige como siempre, con el 400 de validación de antes;
 *   <li>y lo que no lee el catálogo no cambia: ACME sigue viendo su versión con el texto de
 *       sus preguntas ya elegidas, y puede quitarlas de su borrador.
 * </ul>
 *
 * <p>Va en una clase propia y no en {@code FlujoPruebaIT} ni en {@code FlujoPruebaPropiaIT}
 * para no chocar con otras specs que tocan esas clases en paralelo. Las vacantes antiguas que
 * ya rinden una plantilla leen sus preguntas por dentro y no pasan por estos endpoints: eso
 * lo siguen cubriendo {@code FlujoPruebaIT} y {@code FlujoDosEmpresasIT}, que arman la prueba
 * como plataforma.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@Testcontainers
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@DisplayName("El catálogo de preguntas de prueba es solo de la plataforma")
public class CatalogoDePreguntasSoloPlataformaIT {

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
        // Solo para que la plataforma arranque su primer usuario.
        registro.add("app.seguridad.dev-login-activo", () -> "true");
        registro.add("spring.ai.deepseek.api-key", () -> "clave-de-pruebas-no-se-usa");
        registro.add("renaser.ai.calificacion.habilitada", () -> "false");
    }

    private static final String BASE = "/api/v1/panel/plantillas-prueba";
    private static final String CATALOGO = BASE + "/preguntas";
    /** El examen de la plataforma: lo que ACME no debe ver nunca. */
    private static final String SECRETO = "Describe cómo cerraste la caja el mes que faltó dinero";
    private static final String CLAVE = "una-clave-de-panel-larga";

    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    final ObjectMapper json = new ObjectMapper();

    static String tokenPlataforma;
    /** Ana, administradora de ACME: solo ADMINISTRADOR, sin ningún permiso de pruebas. */
    static String tokenAnaAdministradora;
    /** Tomás, de Talento de ACME: tiene los dos permisos de pruebas. */
    static String tokenTalentoAcme;
    /** Rita, responsable del área en ACME: puede elegir prueba pero no editarlas. */
    static String tokenResponsableAcme;
    static long acmeId;
    static long preguntaSecretaId;
    static long borradorAcmeId;

    // ============ Las dos empresas ============

    @Test
    @Order(1)
    @DisplayName("La plataforma escribe su pregunta y ACME tiene su equipo y su borrador propio")
    void prepararLasDosEmpresas() throws Exception {
        tokenPlataforma = leer(mvc.perform(post("/api/v1/panel/auth/dev-login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"usuarioRenaserOsId\":\"dev-catalogo\"}"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(), "token");

        preguntaSecretaId = Long.parseLong(leer(conToken(post(CATALOGO), tokenPlataforma, """
                {"codigo":"SECRETO_PLAT","enunciado":"%s","tipo":"UNIVERSAL"}""".formatted(SECRETO))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString(), "id"));

        String alta = conToken(post("/api/v1/panel/plataforma/empresas"), tokenPlataforma, """
                {"nombre": "Acme S.A.C.", "codigo": "ACME", "correoAdministrador": "ana@acme.pe"}""")
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        acmeId = json.readTree(alta).get("id").asLong();
        tokenAnaAdministradora = canjear(json.readTree(alta).get("urlInvitacion").asText(), "Ana");

        tokenTalentoAcme = invitarYCanjear("tomas@acme.pe", "TALENTO", "Tomás");
        tokenResponsableAcme = invitarYCanjear("rita@acme.pe", "RESPONSABLE_AREA", "Rita");

        // Un borrador PROPIO de ACME: es donde antes se podían ir eligiendo ids ajenos.
        long plantillaAcme = Long.parseLong(leer(conToken(post(BASE), tokenTalentoAcme,
                "{\"nombre\":\"La prueba de ACME\"}")
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString(), "id"));
        borradorAcmeId = Long.parseLong(leer(conToken(
                post(BASE + "/" + plantillaAcme + "/versiones"), tokenTalentoAcme, """
                {"enunciado":"Resuelve el caso de ACME","modalidad":"CRONOMETRADA",
                 "duracionMinutos":90,"minutoCambioMin":30,"minutoCambioMax":50,"minutosExtra":10}""")
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString(), "id"));
        assertThat(contar("select count(*) from plantilla_prueba where id = " + plantillaAcme
                + " and organizacion_id = " + acmeId)).isEqualTo(1);
    }

    // ============ AC-03 · Listar ============

    @Test
    @Order(2)
    @DisplayName("AC-03 · ACME pide el catálogo, con tipo o sin él: 404 y ningún texto")
    void acmeNoListaElCatalogo() throws Exception {
        for (String ruta : new String[] {CATALOGO, CATALOGO + "?tipo=UNIVERSAL", CATALOGO + "?tipo=ESPECIFICA"}) {
            for (String token : new String[] {tokenTalentoAcme, tokenResponsableAcme}) {
                String cuerpo = conTokenGet(ruta, token)
                        .andExpect(status().isNotFound())
                        .andReturn().getResponse().getContentAsString();
                assertThat(cuerpo).doesNotContain(SECRETO).doesNotContain("SECRETO_PLAT");
            }
        }
    }

    // ============ AC-04 · Crear ============

    @Test
    @Order(3)
    @DisplayName("AC-04 · ACME crea una pregunta: 404, no se crea nada y el código queda libre")
    void acmeNoCreaEnElCatalogo() throws Exception {
        int preguntasAntes = contar("select count(*) from pregunta_prueba");
        String pregunta = """
                {"codigo":"T01","enunciado":"¿Cómo cerraste la caja?","tipo":"UNIVERSAL"}""";

        // El doble envío da lo mismo: un POST rechazado no escribe nada.
        for (int intento = 0; intento < 2; intento++) {
            conToken(post(CATALOGO), tokenTalentoAcme, pregunta).andExpect(status().isNotFound());
        }
        // El 404 llega antes de validar el cuerpo: vacío, a medias o con un tipo que no existe.
        for (String malo : new String[] {"{}", "{\"codigo\":\"T01\"}",
                "{\"codigo\":\"T02\",\"enunciado\":\"x\",\"tipo\":\"OTRO\"}"}) {
            conToken(post(CATALOGO), tokenTalentoAcme, malo).andExpect(status().isNotFound());
        }
        // Ni siquiera reutilizar el código que ya existe le dice nada: sigue siendo 404, no 400.
        conToken(post(CATALOGO), tokenTalentoAcme, """
                {"codigo":"SECRETO_PLAT","enunciado":"Otra cosa","tipo":"UNIVERSAL"}""")
                .andExpect(status().isNotFound());

        assertThat(contar("select count(*) from pregunta_prueba")).isEqualTo(preguntasAntes);
        assertThat(contar("select count(*) from pregunta_prueba where codigo in ('T01', 'T02')")).isZero();
        assertThat(contar("select count(*) from auditoria where accion = 'crear_pregunta_prueba'"
                + " and organizacion_id = " + acmeId)).isZero();

        // El código quedó libre: la plataforma lo usa sin chocar con nada.
        conToken(post(CATALOGO), tokenPlataforma, pregunta).andExpect(status().isCreated());
        assertThat(contar("select count(*) from pregunta_prueba where codigo = 'T01'")).isEqualTo(1);
    }

    // ============ AC-05 · Elegir ============

    @Test
    @Order(4)
    @DisplayName("AC-05 · ACME elige una pregunta que existe para su borrador: 404 y la versión no la gana")
    void acmeNoEligeDelCatalogo() throws Exception {
        String ruta = BASE + "/versiones/" + borradorAcmeId + "/preguntas";

        for (int intento = 0; intento < 2; intento++) {
            conToken(post(ruta), tokenTalentoAcme, "{\"preguntaPruebaId\": %d}".formatted(preguntaSecretaId))
                    .andExpect(status().isNotFound());
        }
        // Antes de validar el cuerpo, y sin distinguir si la pregunta o la versión existen:
        // contestar distinto según el id era justo la forma de ir leyendo el catálogo.
        conToken(post(ruta), tokenTalentoAcme, "{}").andExpect(status().isNotFound());
        conToken(post(ruta), tokenTalentoAcme, "{\"preguntaPruebaId\": 999999}")
                .andExpect(status().isNotFound());
        conToken(post(BASE + "/versiones/999999/preguntas"), tokenTalentoAcme,
                "{\"preguntaPruebaId\": %d}".formatted(preguntaSecretaId))
                .andExpect(status().isNotFound());

        assertThat(contar("select count(*) from pregunta_version_plantilla"
                + " where version_plantilla_prueba_id = " + borradorAcmeId)).isZero();
        String version = conTokenGet(BASE + "/versiones/" + borradorAcmeId, tokenTalentoAcme)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.preguntas.length()").value(0))
                .andReturn().getResponse().getContentAsString();
        assertThat(version).doesNotContain(SECRETO);
    }

    // ============ El permiso va antes que la empresa ============

    @Test
    @Order(5)
    @DisplayName("Sin el permiso sigue el 403 de hoy, también en ACME y aunque el cuerpo venga mal")
    void sinPermisoSigueEl403() throws Exception {
        // Ana es solo ADMINISTRADOR: ni elegir ni editar pruebas.
        conTokenGet(CATALOGO, tokenAnaAdministradora).andExpect(status().isForbidden());
        conToken(post(CATALOGO), tokenAnaAdministradora, """
                {"codigo":"T03","enunciado":"x","tipo":"UNIVERSAL"}""").andExpect(status().isForbidden());
        conToken(post(CATALOGO), tokenAnaAdministradora, "{}").andExpect(status().isForbidden());

        // Rita puede elegir prueba para sus vacantes pero no editarlas: listar le da el 404 de
        // la empresa, y crear o elegir el 403 del permiso.
        conTokenGet(CATALOGO, tokenResponsableAcme).andExpect(status().isNotFound());
        conToken(post(CATALOGO), tokenResponsableAcme, "{}").andExpect(status().isForbidden());
        conToken(post(BASE + "/versiones/" + borradorAcmeId + "/preguntas"), tokenResponsableAcme,
                "{\"preguntaPruebaId\": %d}".formatted(preguntaSecretaId))
                .andExpect(status().isForbidden());

        assertThat(contar("select count(*) from pregunta_prueba where codigo = 'T03'")).isZero();
    }

    // ============ Lo que no lee el catálogo no cambia ============

    @Test
    @Order(6)
    @DisplayName("Un borrador de ACME con una pregunta elegida antes se sigue viendo, y se le puede quitar")
    void verYQuitarSiguenIgual() throws Exception {
        // Una empresa que personalizó su prueba antes de cerrar el catálogo ya tenía preguntas
        // elegidas. Ya no puede añadir, pero lo suyo se sigue leyendo y se puede quitar.
        jdbc.update("insert into pregunta_version_plantilla"
                        + " (version_plantilla_prueba_id, pregunta_prueba_id, orden) values (?, ?, 1)",
                borradorAcmeId, preguntaSecretaId);

        conTokenGet(BASE + "/versiones/" + borradorAcmeId, tokenTalentoAcme)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.preguntas.length()").value(1))
                .andExpect(jsonPath("$.preguntas[0].enunciado").value(SECRETO));

        conToken(delete(BASE + "/versiones/" + borradorAcmeId + "/preguntas/" + preguntaSecretaId),
                tokenTalentoAcme, null).andExpect(status().isNoContent());
        assertThat(contar("select count(*) from pregunta_version_plantilla"
                + " where version_plantilla_prueba_id = " + borradorAcmeId)).isZero();
        // Se quitó la elección, no la pregunta: el catálogo sigue entero.
        assertThat(contar("select count(*) from pregunta_prueba where id = " + preguntaSecretaId))
                .isEqualTo(1);
    }

    // ============ AC-06 · La plataforma, como siempre ============

    @Test
    @Order(7)
    @DisplayName("AC-06 · La plataforma lista, crea y elige como hoy, con las mismas validaciones")
    void laPlataformaSigueIgual() throws Exception {
        conTokenGet(CATALOGO, tokenPlataforma)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.codigo == 'SECRETO_PLAT')].enunciado").value(SECRETO));
        conTokenGet(CATALOGO + "?tipo=UNIVERSAL", tokenPlataforma)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.codigo == 'SECRETO_PLAT')]").exists());
        conTokenGet(CATALOGO + "?tipo=ESPECIFICA", tokenPlataforma)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.codigo == 'SECRETO_PLAT')]").doesNotExist());

        // El 400 de validación de antes, con su mapa de campos.
        conToken(post(CATALOGO), tokenPlataforma, "{}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.codigo").exists())
                .andExpect(jsonPath("$.errors.enunciado").exists());
        conToken(post(CATALOGO), tokenPlataforma, """
                {"codigo":"MAL_TIPO","enunciado":"x","tipo":"OTRO"}""")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.tipo").value("tipo debe ser PREVIA, UNIVERSAL o ESPECIFICA"));

        long plantilla = Long.parseLong(leer(conToken(post(BASE), tokenPlataforma,
                "{\"nombre\":\"Prueba de la plataforma\"}")
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString(), "id"));
        long borrador = Long.parseLong(leer(conToken(post(BASE + "/" + plantilla + "/versiones"),
                tokenPlataforma, """
                {"enunciado":"Resuelve el caso","modalidad":"CRONOMETRADA",
                 "duracionMinutos":90,"minutoCambioMin":30,"minutoCambioMax":50,"minutosExtra":10}""")
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString(), "id"));
        String elegir = BASE + "/versiones/" + borrador + "/preguntas";

        conToken(post(elegir), tokenPlataforma, "{}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.preguntaPruebaId").exists());
        conToken(post(elegir), tokenPlataforma, "{\"preguntaPruebaId\": 999999}")
                .andExpect(status().isNotFound());
        conToken(post(elegir), tokenPlataforma, "{\"preguntaPruebaId\": %d}".formatted(preguntaSecretaId))
                .andExpect(status().isOk());

        conTokenGet(BASE + "/versiones/" + borrador, tokenPlataforma)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.preguntas[0].enunciado").value(SECRETO));
        // ACME sigue sin poder elegir en el borrador de la plataforma, ni con el id bueno.
        conToken(post(elegir), tokenTalentoAcme, "{\"preguntaPruebaId\": %d}".formatted(preguntaSecretaId))
                .andExpect(status().isNotFound());
    }

    // ============ Apoyo ============

    private String invitarYCanjear(String correo, String rol, String nombre) throws Exception {
        String invitacion = conToken(post("/api/v1/panel/usuarios/invitaciones"), tokenAnaAdministradora,
                "{\"correo\":\"%s\",\"roles\":[\"%s\"]}".formatted(correo, rol))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return canjear(json.readTree(invitacion).get("url").asText(), nombre);
    }

    private String canjear(String url, String nombre) throws Exception {
        String token = url.substring(url.indexOf("token=") + 6);
        return leer(mvc.perform(post("/api/v1/panel/auth/invitacion")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"token": "%s", "nombre": "%s", "apellidos": "De Acme",
                                 "contrasena": "%s"}""".formatted(token, nombre, CLAVE)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(), "token");
    }

    private int contar(String sql) {
        Integer valor = jdbc.queryForObject(sql, Integer.class);
        return valor == null ? 0 : valor;
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
