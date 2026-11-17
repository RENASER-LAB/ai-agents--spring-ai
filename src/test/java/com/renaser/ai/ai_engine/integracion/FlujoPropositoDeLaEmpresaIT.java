package com.renaser.ai.ai_engine.integracion;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.renaser.ai.ai_engine.integracion.soporte.ImagenesDeContenedores;
import com.renaser.ai.ai_engine.seguridad.service.ServicioToken;
import jakarta.persistence.EntityManagerFactory;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
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
import org.springframework.test.web.servlet.request.AbstractMockHttpServletRequestBuilder;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.rabbitmq.RabbitMQContainer;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * El propósito de la empresa contra Postgres de verdad (V85): el permiso, el aislamiento entre
 * empresas, las reglas de los enlaces, el tablón con y sin datos —sin una consulta por
 * vacante—, el perfil público y sus imágenes.
 *
 * <p>Tres empresas: la plataforma (A, con Talento, Dirección y un Responsable de área sin el
 * permiso), ACME (B, dada de alta por el endpoint real para ver que el permiso viaja con la
 * copia de roles) y una tercera sin datos (C). Cada una con una vacante publicada.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@Testcontainers
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@DisplayName("El propósito de la empresa, de punta a punta")
public class FlujoPropositoDeLaEmpresaIT {

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
        // Para contar las sentencias del tablón: con 2 vacantes o con 5, las mismas.
        registro.add("spring.jpa.properties.hibernate.generate_statistics", () -> "true");
    }

    private static final String PRESENTACION = "/api/v1/panel/organizacion/presentacion";
    private static final String PROPOSITO_A = "Que ninguna familia se quede sin pan.\nCreemos que comer bien no depende de dónde vives.";

    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired ServicioToken tokens;
    @Autowired EntityManagerFactory emf;
    final ObjectMapper json = new ObjectMapper();

    static long plataforma;
    static long acme;
    static long vacia;
    static String tokenTalento;
    static String tokenDireccion;
    static String tokenResponsable;
    static String tokenAcme;
    static long responsableId;
    /** El responsable de las vacantes sembradas cuando no se dice otro: la columna es NOT NULL. */
    static long talentoId;
    static long vacanteA;
    static long vacanteB;
    static long vacanteC;

    // ============ 1. El terreno ============

    @Test
    @Order(1)
    @DisplayName("tres empresas activas con una vacante publicada cada una, y el equipo de A")
    void elTerreno() throws Exception {
        String tokenPlataforma = leer(mvc.perform(post("/api/v1/panel/auth/dev-login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"usuarioRenaserOsId\":\"dev-proposito\"}"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString(), "token");
        plataforma = jdbc.queryForObject("select id from organizacion where es_plataforma", Long.class);
        acme = Long.parseLong(leer(conToken(post("/api/v1/panel/plataforma/empresas"), tokenPlataforma,
                """
                {"nombre": "Acme S.A.C.", "codigo": "ACME", "correoAdministrador": "ana@acme.pe"}""")
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString(), "id"));
        vacia = jdbc.queryForObject("insert into organizacion (codigo, nombre) values "
                + "('VACIA', 'Vacía S.A.C.') returning id", Long.class);

        long talento = equipo(plataforma, "TALENTO", "Tania");
        talentoId = talento;
        long direccion = equipo(plataforma, "DIRECCION", "Diego");
        responsableId = equipo(plataforma, "RESPONSABLE_AREA", "Rosa");
        long talentoAcme = equipo(acme, "TALENTO", "Tito");
        tokenTalento = tokens.emitir(talento, plataforma, "EQUIPO");
        tokenDireccion = tokens.emitir(direccion, plataforma, "EQUIPO");
        tokenResponsable = tokens.emitir(responsableId, plataforma, "EQUIPO");
        tokenAcme = tokens.emitir(talentoAcme, acme, "EQUIPO");

        vacanteA = vacante(plataforma, talento, "Supervisor de producción");
        vacanteB = vacante(acme, talentoAcme, "Coordinador de obra");
        vacanteC = vacante(vacia, null, "Cajero");
    }

    @Test
    @Order(2)
    @DisplayName("V85: Talento, Dirección y Administrador lo tienen en todas las empresas; Responsable no")
    void elPermisoQuedaSembrado() {
        for (String rol : new String[] {"TALENTO", "DIRECCION", "ADMINISTRADOR"}) {
            assertThat(alcance(plataforma, rol)).as(rol).isEqualTo("TODO");
            // ACME se dio de alta después: lo recibe con la copia de roles de la plataforma.
            assertThat(alcance(acme, rol)).as("ACME " + rol).isEqualTo("TODO");
        }
        assertThat(contar("""
                select count(*) from rol_permiso rp join rol r on r.id = rp.rol_id
                  join permiso p on p.id = rp.permiso_id and p.codigo = 'editar_presentacion_empresa'
                 where r.codigo = 'RESPONSABLE_AREA'""")).isZero();
        assertThat(jdbc.queryForObject("select grupo from permiso where codigo = "
                + "'editar_presentacion_empresa'", String.class)).isEqualTo("CONFIGURACION");
    }

    // ============ 2. Sin permiso ============

    @Test
    @Order(3)
    @DisplayName("AC-07: Responsable del área recibe 403 al ver y al guardar, y no cambia nada")
    void sinPermiso() throws Exception {
        conToken(get(PRESENTACION), tokenResponsable, null).andExpect(status().isForbidden());
        conToken(put(PRESENTACION), tokenResponsable, """
                {"propositoEmpresa": "Me lo invento"}""").andExpect(status().isForbidden());
        conToken(multipart(PRESENTACION + "/imagenes/logo").file(logo(400)), tokenResponsable, null)
                .andExpect(status().isForbidden());

        assertThat(jdbc.queryForObject("select proposito_empresa from organizacion where id = ?",
                String.class, plataforma)).isNull();
        assertThat(contar("select count(*) from auditoria where accion = 'editar_presentacion_empresa'"))
                .isZero();
    }

    // ============ 3. Guardar y las reglas ============

    @Test
    @Order(4)
    @DisplayName("AC-01: la primera vez está vacía, con el nombre y el código")
    void laPrimeraVezEstaVacia() throws Exception {
        conToken(get(PRESENTACION), tokenTalento, null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.codigo").value("RENASER"))
                .andExpect(jsonPath("$.nombre").isNotEmpty())
                .andExpect(jsonPath("$.propositoEmpresa").doesNotExist())
                .andExpect(jsonPath("$.beneficios").isEmpty())
                .andExpect(jsonPath("$.logo").doesNotExist());
    }

    @Test
    @Order(5)
    @DisplayName("AC-02: guarda propósito, video, «renaser.pe» como https://renaser.pe e Instagram, y lo audita")
    void guarda() throws Exception {
        String cuerpo = json.writeValueAsString(java.util.Map.of(
                "propositoEmpresa", PROPOSITO_A,
                "videoPresentacion", "https://www.youtube.com/watch?v=abc123DEF45",
                "paginaWeb", "renaser.pe",
                "sector", "  Industria alimentaria ",
                "tamano", "DE_201_A_500",
                "anioFundacion", 1987,
                "beneficios", java.util.List.of("Seguro EPS", "Comedor"),
                "redes", java.util.Map.of("INSTAGRAM", "https://www.instagram.com/renaser")));
        conToken(put(PRESENTACION), tokenTalento, cuerpo)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.propositoEmpresa").value(PROPOSITO_A))
                .andExpect(jsonPath("$.paginaWeb").value("https://renaser.pe"))
                .andExpect(jsonPath("$.sector").value("Industria alimentaria"))
                .andExpect(jsonPath("$.tamanoTexto").value("201–500 colaboradores"))
                .andExpect(jsonPath("$.redes.INSTAGRAM").value("https://www.instagram.com/renaser"))
                .andExpect(jsonPath("$.beneficios[1]").value("Comedor"));

        assertThat(contar("select count(*) from auditoria where accion = 'editar_presentacion_empresa' "
                + "and organizacion_id = " + plataforma)).isEqualTo(1);
        assertThat(jdbc.queryForObject("select valor_nuevo->>'paginaWeb' from auditoria where accion = "
                + "'editar_presentacion_empresa'", String.class)).isEqualTo("https://renaser.pe");
        assertThat(jdbc.queryForObject("select valor_anterior->>'propositoEmpresa' from auditoria "
                + "where accion = 'editar_presentacion_empresa'", String.class)).isNull();

        // Dos guardados seguidos con lo mismo dejan lo mismo, y no vuelven a auditar.
        conToken(put(PRESENTACION), tokenDireccion, cuerpo).andExpect(status().isOk());
        assertThat(contar("select count(*) from auditoria where accion = 'editar_presentacion_empresa'"))
                .isEqualTo(1);
        assertThat(contar("select count(*) from organizacion_beneficio where organizacion_id = "
                + plataforma)).isEqualTo(2);
    }

    @Test
    @Order(6)
    @DisplayName("AC-03/04/05/06: cada enlace que no vale dice lo suyo y no se guarda ningún campo")
    void lasReglas() throws Exception {
        conToken(put(PRESENTACION), tokenTalento, """
                {"propositoEmpresa": "Otro propósito", "paginaWeb": "javascript:alert(1)"}""")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errores.paginaWeb")
                        .value("Escribe una dirección web que empiece por https://"));
        conToken(put(PRESENTACION), tokenTalento, """
                {"videoPresentacion": "https://www.facebook.com/renaser/videos/123"}""")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errores.videoPresentacion")
                        .value("Pega el enlace de un video de YouTube o de Vimeo"));
        conToken(put(PRESENTACION), tokenTalento, """
                {"redes": {"INSTAGRAM": "https://www.facebook.com/renaser"}}""")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errores.INSTAGRAM").value("Este enlace no es de Instagram"));
        conToken(put(PRESENTACION), tokenTalento,
                json.writeValueAsString(java.util.Map.of("propositoEmpresa", "a".repeat(1501))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("El propósito admite hasta 1500 caracteres"))
                .andExpect(jsonPath("$.errores.propositoEmpresa")
                        .value("El propósito admite hasta 1500 caracteres"));
        // Con varios a la vez, todos juntos.
        conToken(put(PRESENTACION), tokenTalento, """
                {"paginaWeb": "ftp://renaser.pe", "videoPresentacion": "https://vimeo.com/renaser",
                 "anioFundacion": 1700}""")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("No se guardó nada. Corrige los campos marcados."))
                .andExpect(jsonPath("$.errores.paginaWeb").exists())
                .andExpect(jsonPath("$.errores.videoPresentacion").exists())
                .andExpect(jsonPath("$.errores.anioFundacion").exists());

        // Nada de lo anterior tocó lo guardado.
        assertThat(jdbc.queryForObject("select proposito_empresa from organizacion where id = ?",
                String.class, plataforma)).isEqualTo(PROPOSITO_A);
        assertThat(jdbc.queryForObject("select pagina_web from organizacion where id = ?",
                String.class, plataforma)).isEqualTo("https://renaser.pe");
    }

    // ============ 4. Cada empresa, lo suyo ============

    @Test
    @Order(7)
    @DisplayName("AC-08: B guarda lo suyo y A sigue igual; cada vacante enseña lo de su empresa")
    void cadaEmpresaLoSuyo() throws Exception {
        conToken(put(PRESENTACION), tokenAcme, """
                {"propositoEmpresa": "Construimos casas que duran.",
                 "redes": {"FACEBOOK": "facebook.com/acme", "YOUTUBE": "https://www.youtube.com/@acme"}}""")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.codigo").value("ACME"));

        conToken(get(PRESENTACION), tokenTalento, null)
                .andExpect(jsonPath("$.propositoEmpresa").value(PROPOSITO_A));

        JsonNode tablon = json.readTree(mvc.perform(get("/api/v1/portal/vacantes"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        JsonNode a = laVacante(tablon, vacanteA);
        JsonNode b = laVacante(tablon, vacanteB);
        JsonNode c = laVacante(tablon, vacanteC);
        // AC-09: la vacante se publicó antes de guardar, y ya lo enseña sin volver a publicarla.
        assertThat(a.at("/empresa/proposito").asText()).isEqualTo(PROPOSITO_A);
        assertThat(a.at("/empresa/video/servicio").asText()).isEqualTo("YOUTUBE");
        assertThat(a.at("/empresa/video/identificador").asText()).isEqualTo("abc123DEF45");
        assertThat(a.at("/empresa/paginaWeb").asText()).isEqualTo("https://renaser.pe");
        assertThat(a.at("/codigoEmpresa").asText()).isEqualTo("RENASER");
        assertThat(b.at("/empresa/proposito").asText()).isEqualTo("Construimos casas que duran.");
        assertThat(b.at("/empresa/redes/0/red").asText()).isEqualTo("FACEBOOK");
        assertThat(b.at("/empresa/redes/0/enlace").asText()).isEqualTo("https://facebook.com/acme");
        // AC-15: la empresa sin datos sale sin ellos, y lo de siempre no cambia.
        assertThat(c.get("empresa").isNull()).isTrue();
        assertThat(c.get("nombreEmpresa").asText()).isEqualTo("Vacía S.A.C.");
        assertThat(c.get("titulo").asText()).isEqualTo("Cajero");
        assertThat(c.get("remuneracion").get("tipo").asText()).isEqualTo("OCULTA");
    }

    @Test
    @Order(8)
    @DisplayName("AC-14 y AC-15: el detalle sin token trae lo de su empresa y el propósito del puesto aparte")
    void elDetalle() throws Exception {
        jdbc.update("update vacante set proposito = 'Reducir los tiempos de despacho' where id = ?", vacanteA);
        mvc.perform(get("/api/v1/portal/vacantes/" + vacanteA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.proposito").value("Reducir los tiempos de despacho"))
                .andExpect(jsonPath("$.empresa.proposito").value(PROPOSITO_A))
                .andExpect(jsonPath("$.nombreEmpresa").isNotEmpty());
        mvc.perform(get("/api/v1/portal/vacantes/" + vacanteC))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.empresa").isEmpty())
                .andExpect(jsonPath("$.codigoEmpresa").value("VACIA"));
    }

    @Test
    @Order(9)
    @DisplayName("el tablón no hace una consulta por vacante: con 3 vacantes o con 6, las mismas sentencias")
    void sinConsultasPorVacante() throws Exception {
        long conTres = sentenciasDelTablon();
        vacante(plataforma, null, "Analista de calidad");
        vacante(plataforma, null, "Asistente de almacén");
        vacante(acme, null, "Maestro de obra");
        long conSeis = sentenciasDelTablon();

        assertThat(conSeis).as("sentencias con 6 vacantes frente a con 3").isEqualTo(conTres);
    }

    // ============ 5. El video del puesto ============

    @Test
    @Order(10)
    @DisplayName("AC-24 y AC-23: el video del puesto sigue las reglas del de la empresa y se puede cambiar publicada")
    void elVideoDelPuesto() throws Exception {
        long solicitud = jdbc.queryForObject("select solicitud_talento_id from vacante where id = ?",
                Long.class, vacanteA);
        long responsable = jdbc.queryForObject("select responsable_usuario_id from vacante where id = ?",
                Long.class, vacanteA);
        String base = """
                {"solicitudTalentoId": %d, "titulo": "Supervisor de producción",
                 "descripcion": "Descripción", "proposito": "Reducir los tiempos de despacho",
                 "tipoCierre": "PERMANENTE", "responsableUsuarioId": %d, "videoPresentacion": "%s"}""";

        conToken(put("/api/v1/panel/vacantes/" + vacanteA), tokenTalento,
                base.formatted(solicitud, responsable, "https://www.facebook.com/renaser/videos/123"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("Pega el enlace de un video de YouTube o de Vimeo"));
        assertThat(jdbc.queryForObject("select video_presentacion from vacante where id = ?",
                String.class, vacanteA)).isNull();

        // Sin video del puesto, la empresa tiene el suyo: el panel lo dice con su chip.
        conToken(get(PRESENTACION + "/video"), tokenTalento, null)
                .andExpect(status().isOk()).andExpect(jsonPath("$.tieneVideo").value(true));

        conToken(put("/api/v1/panel/vacantes/" + vacanteA), tokenTalento,
                base.formatted(solicitud, responsable, "vimeo.com/123456789/abcdef1234"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.postulantesAvisados").value(0));
        mvc.perform(get("/api/v1/portal/vacantes/" + vacanteA))
                .andExpect(jsonPath("$.videoPuesto.servicio").value("VIMEO"))
                .andExpect(jsonPath("$.videoPuesto.identificador").value("123456789"))
                .andExpect(jsonPath("$.videoPuesto.clave").value("abcdef1234"))
                .andExpect(jsonPath("$.empresa.video.servicio").value("YOUTUBE"));
        conToken(get("/api/v1/panel/vacantes/" + vacanteA), tokenTalento, null)
                .andExpect(jsonPath("$.videoPresentacion").value("https://vimeo.com/123456789/abcdef1234"));
    }

    // ============ 6. El perfil público ============

    @Test
    @Order(11)
    @DisplayName("AC-17 y AC-21: el perfil público, sin token, con lo público y sus vacantes publicadas")
    void elPerfilPublico() throws Exception {
        jdbc.update("insert into sede (organizacion_id, nombre, provincia_ubigeo, es_activa) "
                + "values (?, 'Planta', '0401', true)", plataforma);
        long cerrada = vacante(plataforma, null, "Puesto ya cerrado");
        jdbc.update("update vacante set estado = 'CERRADA' where id = ?", cerrada);

        String cuerpo = mvc.perform(get("/api/v1/portal/empresas/renaser"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.codigo").value("RENASER"))
                .andExpect(jsonPath("$.proposito").value(PROPOSITO_A))
                .andExpect(jsonPath("$.video.identificador").value("abc123DEF45"))
                .andExpect(jsonPath("$.paginaWeb").value("https://renaser.pe"))
                .andExpect(jsonPath("$.redes[0].red").value("INSTAGRAM"))
                .andExpect(jsonPath("$.beneficios[0]").value("Seguro EPS"))
                .andExpect(jsonPath("$.anioFundacion").value(1987))
                .andExpect(jsonPath("$.ciudad").isNotEmpty())
                .andReturn().getResponse().getContentAsString();
        JsonNode perfil = json.readTree(cuerpo);
        // Las publicadas de A: la del terreno y las dos del conteo; la cerrada no.
        assertThat(perfil.get("vacantes")).hasSize(3);
        for (JsonNode v : perfil.get("vacantes")) {
            assertThat(v.get("titulo").asText()).isNotEqualTo("Puesto ya cerrado");
        }
        // Nada de candidatos, pretensiones ni datos internos.
        assertThat(cuerpo).doesNotContain("pretension").doesNotContain("postulacion")
                .doesNotContain("responsable").doesNotContain("organizacionId");
    }

    @Test
    @Order(12)
    @DisplayName("AC-19 y AC-20: sin presentación sale el nombre; un código que no existe o una inactiva, 404")
    void elPerfilSinDatosYElQueNoExiste() throws Exception {
        mvc.perform(get("/api/v1/portal/empresas/vacia"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nombre").value("Vacía S.A.C."))
                .andExpect(jsonPath("$.proposito").doesNotExist())
                .andExpect(jsonPath("$.vacantes.length()").value(1));
        mvc.perform(get("/api/v1/portal/empresas/no-existe")).andExpect(status().isNotFound());

        jdbc.update("update organizacion set es_activa = false where id = ?", vacia);
        mvc.perform(get("/api/v1/portal/empresas/vacia")).andExpect(status().isNotFound());
        mvc.perform(get("/api/v1/portal/vacantes/" + vacanteC)).andExpect(status().isNotFound());
        jdbc.update("update organizacion set es_activa = true where id = ?", vacia);
    }

    // ============ 7. Las imágenes ============

    @Test
    @Order(13)
    @DisplayName("AC-26 y AC-28: el logo se sube, se sirve sin token con caché pública, y nada más")
    void lasImagenes() throws Exception {
        conToken(multipart(PRESENTACION + "/imagenes/logo").file(logo(100)), tokenTalento, null)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("El logo tiene que medir al menos 200 × 200 píxeles."));

        String panel = conToken(multipart(PRESENTACION + "/imagenes/logo").file(logo(400)),
                tokenTalento, null)
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        String direccion = leer(panel, "logo");
        assertThat(direccion).startsWith("/api/v1/portal/empresas/renaser/imagenes/logo?v=");
        assertThat(contar("select count(*) from auditoria where accion = 'cambiar_logo_empresa'"))
                .isEqualTo(1);

        mvc.perform(get(direccion))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", "image/png"))
                .andExpect(header().string("Cache-Control", containsString("max-age=86400")))
                .andExpect(header().string("Cache-Control", containsString("public")));
        // La vacante lo lleva junto al nombre.
        mvc.perform(get("/api/v1/portal/vacantes/" + vacanteA))
                .andExpect(jsonPath("$.empresa.logo").value(direccion));

        mvc.perform(get("/api/v1/portal/empresas/acme/imagenes/logo")).andExpect(status().isNotFound());
        mvc.perform(get("/api/v1/portal/empresas/renaser/imagenes/curriculum"))
                .andExpect(status().isNotFound());
        mvc.perform(get("/api/v1/portal/empresas/renaser/imagenes/portada"))
                .andExpect(status().isNotFound());

        conToken(delete(PRESENTACION + "/imagenes/logo"), tokenTalento, null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.logo").doesNotExist());
        mvc.perform(get("/api/v1/portal/empresas/renaser/imagenes/logo")).andExpect(status().isNotFound());
    }

    // ============ Ayudas ============

    /** El menor de tres conteos: un barrido programado que corra a la vez solo puede sumar. */
    private long sentenciasDelTablon() throws Exception {
        Statistics estadisticas = emf.unwrap(SessionFactory.class).getStatistics();
        long menor = Long.MAX_VALUE;
        for (int i = 0; i < 3; i++) {
            estadisticas.clear();
            mvc.perform(get("/api/v1/portal/vacantes")).andExpect(status().isOk());
            menor = Math.min(menor, estadisticas.getPrepareStatementCount());
        }
        return menor;
    }

    private static JsonNode laVacante(JsonNode tablon, long id) {
        for (JsonNode v : tablon) {
            if (v.get("id").asLong() == id) {
                return v;
            }
        }
        throw new AssertionError("La vacante " + id + " no está en el tablón");
    }

    private static MockMultipartFile logo(int lado) throws IOException {
        ByteArrayOutputStream salida = new ByteArrayOutputStream();
        ImageIO.write(new BufferedImage(lado, lado, BufferedImage.TYPE_INT_ARGB), "png", salida);
        return new MockMultipartFile("archivo", "logo.png", "image/png", salida.toByteArray());
    }

    private String alcance(long organizacion, String rol) {
        return jdbc.queryForObject("""
                select rp.alcance from rol_permiso rp
                  join rol r on r.id = rp.rol_id
                  join permiso p on p.id = rp.permiso_id
                 where r.organizacion_id = ? and r.codigo = ? and p.codigo = 'editar_presentacion_empresa'""",
                String.class, organizacion, rol);
    }

    /** Una persona del equipo con un rol, sembrada a mano: su token se emite aparte. */
    private long equipo(long organizacion, String rol, String nombre) {
        Long persona = jdbc.queryForObject("insert into persona (nombre, apellidos) values (?, "
                + "'Equipo') returning id", Long.class, nombre);
        Long usuario = jdbc.queryForObject("insert into usuario (organizacion_id, persona_id, "
                + "correo, es_equipo, es_activo) values (?, ?, ?, true, true) returning id",
                Long.class, organizacion, persona, nombre.toLowerCase() + "@equipo.pe");
        jdbc.update("insert into usuario_rol (usuario_id, rol_id) select ?, id from rol "
                + "where organizacion_id = ? and codigo = ?", usuario, organizacion, rol);
        return usuario;
    }

    /** Una vacante publicada, sembrada por la base: lo que se prueba aquí no es publicarla. */
    private long vacante(long organizacion, Long responsable, String titulo) {
        Long area = jdbc.queryForObject("insert into area (organizacion_id, nombre) values (?, ?) "
                + "returning id", Long.class, organizacion, "Área " + titulo);
        Long puesto = jdbc.queryForObject("insert into puesto (organizacion_id, codigo, nombre, "
                + "nivel_puesto_codigo, familia_codigo) values (?, ?, ?, 'EJECUCION', "
                + "'OPERACIONES') returning id", Long.class, organizacion,
                "P_" + titulo.replace(' ', '_').toUpperCase(), titulo);
        Long solicitud = jdbc.queryForObject("insert into solicitud_talento (organizacion_id, "
                + "origen, urgencia, estado, area_id, resultado_principal, motivo, "
                + "consecuencia_no_contratar, analisis_capacidad, puesto_id) values (?, "
                + "'DIRECTA', 'NORMAL', 'CON_VACANTE', ?, 'r', 'm', 'c', 'a', ?) returning id",
                Long.class, organizacion, area, puesto);
        Long pesos = jdbc.queryForObject("select min(id) from version_pesos", Long.class);
        return jdbc.queryForObject("insert into vacante (organizacion_id, solicitud_talento_id, "
                + "puesto_id, titulo, descripcion, tipo_cierre, estado, version_pesos_id, "
                + "responsable_usuario_id, publicada_en) values (?, ?, ?, ?, 'Descripción', "
                + "'PERMANENTE', 'PUBLICADA', ?, ?, now()) returning id", Long.class, organizacion,
                solicitud, puesto, titulo, pesos, responsable == null ? talentoId : responsable);
    }

    private int contar(String sql) {
        Integer valor = jdbc.queryForObject(sql, Integer.class);
        return valor == null ? 0 : valor;
    }

    private ResultActions conToken(AbstractMockHttpServletRequestBuilder<?> peticion, String token,
                                   String cuerpo) throws Exception {
        peticion.header("Authorization", "Bearer " + token);
        if (cuerpo != null) {
            peticion.contentType(MediaType.APPLICATION_JSON).content(cuerpo);
        }
        return mvc.perform(peticion);
    }

    private String leer(String cuerpoRespuesta, String campo) throws Exception {
        return json.readTree(cuerpoRespuesta).get(campo).asText();
    }
}
