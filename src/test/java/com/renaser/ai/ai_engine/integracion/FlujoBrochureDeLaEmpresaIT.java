package com.renaser.ai.ai_engine.integracion;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.renaser.ai.ai_engine.integracion.soporte.ImagenesDeContenedores;
import com.renaser.ai.ai_engine.seguridad.service.ServicioToken;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.cos.COSName;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDResources;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;
import org.apache.pdfbox.text.PDFTextStripper;
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
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.AbstractMockHttpServletRequestBuilder;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.rabbitmq.RabbitMQContainer;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * El brochure de la empresa contra Postgres de verdad (V86): la migración, misión y visión, la
 * plantilla y el color, el PDF del panel y el del perfil público, lo mínimo para descargarlo, el
 * permiso, que siempre esté al día y que cada empresa vea solo lo suyo.
 *
 * <p>Dos empresas: la plataforma (A, con Talento y un Responsable de área sin el permiso, dos
 * sedes activas y una desactivada) y ACME (B, dada de alta por el endpoint real, sin logo, ni
 * portada, ni «Quiénes somos» al empezar).
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@Testcontainers
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@DisplayName("El brochure de la empresa, de punta a punta")
public class FlujoBrochureDeLaEmpresaIT {

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
        registro.add("renaser.portal.url", () -> "https://ex.renaser.pe");
    }

    private static final String PRESENTACION = "/api/v1/panel/organizacion/presentacion";
    private static final String BROCHURE_PANEL = PRESENTACION + "/brochure";
    private static final String QUIENES_A = "Molemos trigo en Arequipa desde 1987.\nSomos «Molinos» y "
            + "hacemos pan <b>rico</b> con la cigüeña & el ñandú.";
    private static final String PROPOSITO_A = "Que ninguna familia se quede sin pan.";
    private static final String MISION_A = "Moler el mejor trigo del sur y llevarlo fresco a cada mesa.";

    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired ServicioToken tokens;
    final ObjectMapper json = new ObjectMapper();

    static long plataforma;
    static long acme;
    static String tokenTalento;
    static String tokenResponsable;
    static String tokenAcme;
    static long sedeLima;

    // ============ 1. El terreno ============

    @Test
    @Order(1)
    @DisplayName("dos empresas activas, el equipo de A y sus sedes: dos activas y una desactivada")
    void elTerreno() throws Exception {
        String tokenPlataforma = leer(mvc.perform(post("/api/v1/panel/auth/dev-login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"usuarioRenaserOsId\":\"dev-brochure\"}"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString(), "token");
        plataforma = jdbc.queryForObject("select id from organizacion where es_plataforma", Long.class);
        acme = Long.parseLong(leer(conToken(post("/api/v1/panel/plataforma/empresas"), tokenPlataforma,
                """
                {"nombre": "Acme S.A.C.", "codigo": "ACME", "correoAdministrador": "ana@acme.pe"}""")
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString(), "id"));

        tokenTalento = tokens.emitir(equipo(plataforma, "TALENTO", "Tania"), plataforma, "EQUIPO");
        tokenResponsable = tokens.emitir(equipo(plataforma, "RESPONSABLE_AREA", "Rosa"), plataforma,
                "EQUIPO");
        tokenAcme = tokens.emitir(equipo(acme, "TALENTO", "Tito"), acme, "EQUIPO");

        jdbc.update("insert into sede (organizacion_id, nombre, direccion, provincia_ubigeo, es_activa) "
                + "values (?, 'Planta', 'Calle Mercaderes 101', '0401', true)", plataforma);
        sedeLima = jdbc.queryForObject("insert into sede (organizacion_id, nombre, direccion, "
                + "provincia_ubigeo, es_activa) values (?, 'Oficina', 'Av. Arequipa 2000', '1501', true) "
                + "returning id", Long.class, plataforma);
        jdbc.update("insert into sede (organizacion_id, nombre, direccion, provincia_ubigeo, es_activa) "
                + "values (?, 'Almacén cerrado', 'Jr. Cerrado 5', '0801', false)", plataforma);
    }

    @Test
    @Order(2)
    @DisplayName("V86: todas las empresas quedan sin misión ni visión, con «Clásica» y «Azul»")
    void laMigracion() throws Exception {
        assertThat(jdbc.queryForList("select distinct plantilla_brochure || '/' || color_brochure "
                + "from organizacion", String.class)).containsExactly("CLASICA/AZUL");
        assertThat(contar("select count(*) from organizacion where mision is not null "
                + "or vision is not null")).isZero();
        conToken(get(PRESENTACION), tokenTalento, null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.plantillaBrochure").value("CLASICA"))
                .andExpect(jsonPath("$.colorBrochure").value("AZUL"))
                .andExpect(jsonPath("$.mision").doesNotExist())
                .andExpect(jsonPath("$.vision").doesNotExist());
    }

    // ============ 2. Lo mínimo y el permiso ============

    @Test
    @Order(3)
    @DisplayName("AC-07: sin «Quiénes somos», 409 en el panel, sin botón en el perfil y 404 en su dirección")
    void sinLoMinimo() throws Exception {
        conToken(get(BROCHURE_PANEL), tokenTalento, null)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail", containsString("Quiénes somos")));
        mvc.perform(get("/api/v1/portal/empresas/renaser"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.brochure").doesNotExist());
        mvc.perform(get("/api/v1/portal/empresas/renaser/brochure")).andExpect(status().isNotFound());
    }

    @Test
    @Order(4)
    @DisplayName("AC-15: Responsable del área recibe 403 al guardar y al descargar desde el panel, y no cambia nada")
    void sinPermiso() throws Exception {
        conToken(put(PRESENTACION), tokenResponsable, """
                {"quienesSomos": "Me lo invento", "mision": "Mía", "plantillaBrochure": "PORTADA"}""")
                .andExpect(status().isForbidden());
        conToken(get(BROCHURE_PANEL), tokenResponsable, null).andExpect(status().isForbidden());

        assertThat(jdbc.queryForObject("select coalesce(mision, '') || '/' || plantilla_brochure "
                + "from organizacion where id = ?", String.class, plataforma)).isEqualTo("/CLASICA");
    }

    @Test
    @Order(5)
    @DisplayName("400: misión o visión de 401, o una plantilla o un color fuera del catálogo, y no se guarda nada")
    void lasReglas() throws Exception {
        conToken(put(PRESENTACION), tokenTalento,
                json.writeValueAsString(Map.of("quienesSomos", "Algo", "mision", "m".repeat(401))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("La misión admite hasta 400 caracteres"))
                .andExpect(jsonPath("$.errores.mision").value("La misión admite hasta 400 caracteres"));
        conToken(put(PRESENTACION), tokenTalento,
                json.writeValueAsString(Map.of("vision", "v".repeat(401))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errores.vision").value("La visión admite hasta 400 caracteres"));
        conToken(put(PRESENTACION), tokenTalento, """
                {"quienesSomos": "Algo", "plantillaBrochure": "MOSAICO", "colorBrochure": "FUCSIA"}""")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errores.plantillaBrochure").exists())
                .andExpect(jsonPath("$.errores.colorBrochure").exists());

        assertThat(jdbc.queryForObject("select coalesce(quienes_somos, '') || '/' || plantilla_brochure "
                + "|| '/' || color_brochure from organizacion where id = ?", String.class, plataforma))
                .isEqualTo("/CLASICA/AZUL");
    }

    // ============ 3. Guardar y descargar ============

    @Test
    @Order(6)
    @DisplayName("AC-03 · AC-05: guarda misión sin visión, «Columnas» y «Petróleo», y la descarga lo trae así")
    void guardaYDescarga() throws Exception {
        Map<String, Object> cuerpo = new LinkedHashMap<>();
        cuerpo.put("quienesSomos", QUIENES_A);
        cuerpo.put("propositoEmpresa", PROPOSITO_A);
        cuerpo.put("mision", "  " + MISION_A + "  ");
        cuerpo.put("vision", "   ");
        cuerpo.put("paginaWeb", "molinos.pe");
        cuerpo.put("sector", "Industria alimentaria");
        cuerpo.put("plantillaBrochure", "COLUMNAS");
        cuerpo.put("colorBrochure", "PETROLEO");
        conToken(put(PRESENTACION), tokenTalento, json.writeValueAsString(cuerpo))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.mision").value(MISION_A))
                .andExpect(jsonPath("$.vision").doesNotExist())
                .andExpect(jsonPath("$.plantillaBrochure").value("COLUMNAS"))
                .andExpect(jsonPath("$.colorBrochure").value("PETROLEO"));
        assertThat(jdbc.queryForObject("select valor_nuevo->>'plantillaBrochure' from auditoria where "
                + "accion = 'editar_presentacion_empresa' order by id desc limit 1", String.class))
                .isEqualTo("COLUMNAS");

        // El logo PNG con transparencia y la portada.
        conToken(multipart(PRESENTACION + "/imagenes/logo").file(logoTransparente()), tokenTalento, null)
                .andExpect(status().isOk());
        conToken(multipart(PRESENTACION + "/imagenes/portada").file(portada()), tokenTalento, null)
                .andExpect(status().isOk());

        MvcResult respuesta = conToken(get(BROCHURE_PANEL), tokenTalento, null)
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", "application/pdf"))
                .andExpect(header().string("Content-Disposition", containsString("inline")))
                .andExpect(header().string("Content-Disposition", containsString("Brochure")))
                .andExpect(header().string("Cache-Control", containsString("no-store")))
                .andReturn();
        byte[] archivo = respuesta.getResponse().getContentAsByteArray();
        String texto = texto(archivo);

        assertThat(texto).contains(normalizado(QUIENES_A), PROPOSITO_A, "MISIÓN", MISION_A,
                "molinos.pe", "ex.renaser.pe/empresas/renaser", "SECTOR", "Industria alimentaria")
                .doesNotContain("VISIÓN", "Visión", "BENEFICIOS");
        // AC-12: las provincias de las dos activas, sin direcciones ni la desactivada.
        assertThat(texto).contains("DÓNDE ESTAMOS", "Arequipa", "Lima")
                .doesNotContain("Cusco", "Mercaderes", "Av. Arequipa 2000", "Jr. Cerrado");
        // AC-10: «Columnas» lleva el logo y la portada en la primera hoja.
        assertThat(imagenes(archivo, 1)).hasSize(2);
    }

    @Test
    @Order(7)
    @DisplayName("AC-04 · AC-07: el perfil público trae la misión, sin visión, y la dirección del brochure, que se abre sin cuenta")
    void elPerfilPublico() throws Exception {
        String cuerpo = mvc.perform(get("/api/v1/portal/empresas/renaser"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.mision").value(MISION_A))
                .andExpect(jsonPath("$.vision").doesNotExist())
                .andExpect(jsonPath("$.brochure").value("/api/v1/portal/empresas/renaser/brochure"))
                .andExpect(jsonPath("$.quienesSomos").value(QUIENES_A))
                .andReturn().getResponse().getContentAsString();
        assertThat(cuerpo).doesNotContain("plantillaBrochure");

        byte[] archivo = mvc.perform(get("/api/v1/portal/empresas/RENASER/brochure"))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", "application/pdf"))
                .andExpect(header().string("Content-Disposition", containsString("inline")))
                .andExpect(header().string("Cache-Control", containsString("no-store")))
                .andReturn().getResponse().getContentAsByteArray();
        // AC-13: todo se lee tal cual se escribió.
        assertThat(texto(archivo)).contains(normalizado(QUIENES_A), MISION_A);
    }

    @Test
    @Order(8)
    @DisplayName("AC-08: tras guardar otro «Quiénes somos» o desactivar una sede, la siguiente descarga lo trae")
    void siempreAlDia() throws Exception {
        conToken(put(PRESENTACION), tokenTalento, json.writeValueAsString(Map.of(
                "quienesSomos", "Ahora también hacemos fideos.", "mision", MISION_A,
                "plantillaBrochure", "COLUMNAS", "colorBrochure", "PETROLEO")))
                .andExpect(status().isOk());
        assertThat(texto(descargarPanel())).contains("Ahora también hacemos fideos.")
                .doesNotContain("Molemos trigo");
        assertThat(texto(descargarPublico("renaser"))).contains("Ahora también hacemos fideos.");

        // Configuración → Sedes: la de Lima se desactiva.
        jdbc.update("update sede set es_activa = false where id = ?", sedeLima);
        assertThat(texto(descargarPublico("renaser"))).contains("Arequipa").doesNotContain("Lima");

        // Sin plantilla ni color en el cuerpo, se quedan los elegidos.
        conToken(put(PRESENTACION), tokenTalento, json.writeValueAsString(Map.of(
                "quienesSomos", "Ahora también hacemos fideos.")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.plantillaBrochure").value("COLUMNAS"))
                .andExpect(jsonPath("$.mision").doesNotExist());
    }

    // ============ 4. Cada empresa, lo suyo ============

    @Test
    @Order(9)
    @DisplayName("AC-14 · AC-09: B guarda y descarga lo suyo, sin logo ni portada; A sigue igual y no enseña nada de B")
    void cadaEmpresaLoSuyo() throws Exception {
        conToken(get(BROCHURE_PANEL), tokenAcme, null).andExpect(status().isConflict());
        conToken(put(PRESENTACION), tokenAcme, """
                {"quienesSomos": "Construimos casas que duran.", "vision": "Ser la constructora del sur.",
                 "plantillaBrochure": "PORTADA", "colorBrochure": "CIRUELA"}""")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.codigo").value("ACME"));

        byte[] deB = conToken(get(BROCHURE_PANEL), tokenAcme, null)
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsByteArray();
        String textoB = texto(deB);
        assertThat(textoB).contains("Construimos casas que duran.", "VISIÓN", "Acme S.A.C.")
                .doesNotContain("fideos", MISION_A, "molinos.pe", "MISIÓN");
        for (int pagina = 1; pagina <= paginas(deB); pagina++) {
            assertThat(imagenes(deB, pagina)).as("hoja %d de B", pagina).isEmpty();
        }

        String textoA = texto(descargarPanel());
        assertThat(textoA).contains("Ahora también hacemos fideos.")
                .doesNotContain("Construimos casas", "Ser la constructora");
        assertThat(jdbc.queryForObject("select plantilla_brochure from organizacion where id = ?",
                String.class, plataforma)).isEqualTo("COLUMNAS");
    }

    @Test
    @Order(10)
    @DisplayName("una empresa suspendida o un código que no existe: su brochure responde 404")
    void suspendidaONoExiste() throws Exception {
        mvc.perform(get("/api/v1/portal/empresas/acme/brochure")).andExpect(status().isOk());
        jdbc.update("update organizacion set es_activa = false where id = ?", acme);
        mvc.perform(get("/api/v1/portal/empresas/acme/brochure")).andExpect(status().isNotFound());
        mvc.perform(get("/api/v1/portal/empresas/acme")).andExpect(status().isNotFound());
        jdbc.update("update organizacion set es_activa = true where id = ?", acme);
        mvc.perform(get("/api/v1/portal/empresas/no-existe/brochure")).andExpect(status().isNotFound());
    }

    // ============ 5. La pestaña «Brochure»: se guarda al elegir ============

    @Test
    @Order(11)
    @DisplayName("PUT /brochure: sin el permiso 403 y fuera del catálogo 400, sin cambiar nada")
    void laPestanaSinPermisoNiCatalogo() throws Exception {
        conToken(put(BROCHURE_PANEL), tokenResponsable, """
                {"plantillaBrochure": "PORTADA", "colorBrochure": "ROJO"}""")
                .andExpect(status().isForbidden());
        conToken(put(BROCHURE_PANEL), tokenTalento, """
                {"plantillaBrochure": "MOSAICO", "colorBrochure": "FUCSIA"}""")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errores.plantillaBrochure")
                        .value("Esa plantilla no está entre las que se ofrecen"))
                .andExpect(jsonPath("$.errores.colorBrochure")
                        .value("Ese color no está entre los que se ofrecen"));
        // Aquí los dos son obligatorios: ausente no es «lo de antes».
        conToken(put(BROCHURE_PANEL), tokenTalento, """
                {"plantillaBrochure": "CLASICA"}""")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errores.colorBrochure").exists())
                .andExpect(jsonPath("$.errores.plantillaBrochure").doesNotExist());

        assertThat(eleccionDe(plataforma)).isEqualTo("COLUMNAS/PETROLEO");
        assertThat(contar("select count(*) from auditoria where accion = 'elegir_brochure_empresa'"))
                .isZero();
    }

    @Test
    @Order(12)
    @DisplayName("PUT /brochure guarda solo la plantilla y el color, lo audita y la siguiente descarga ya sale así")
    void laPestanaGuardaYLaDescargaCambia() throws Exception {
        byte[] columnas = descargarPanel();
        // Sin cambios, el PDF recordado: los mismos bytes.
        assertThat(descargarPanel()).isEqualTo(columnas);
        assertThat(imagenes(columnas, 1)).as("«Columnas»: logo y portada en la primera hoja")
                .hasSize(2);

        conToken(put(BROCHURE_PANEL), tokenTalento, """
                {"plantillaBrochure": "CLASICA", "colorBrochure": "ROJO"}""")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.plantillaBrochure").value("CLASICA"))
                .andExpect(jsonPath("$.colorBrochure").value("ROJO"))
                .andExpect(jsonPath("$.quienesSomos").value("Ahora también hacemos fideos."))
                .andExpect(jsonPath("$.paginaWeb").doesNotExist());
        assertThat(eleccionDe(plataforma)).isEqualTo("CLASICA/ROJO");
        assertThat(jdbc.queryForObject("select valor_anterior->>'plantillaBrochure' || '>' || "
                + "(valor_nuevo->>'plantillaBrochure') || '/' || (valor_nuevo->>'colorBrochure') "
                + "from auditoria where accion = 'elegir_brochure_empresa' and organizacion_id = ? "
                + "order by id desc limit 1", String.class, plataforma))
                .isEqualTo("COLUMNAS>CLASICA/ROJO");

        // El PDF recordado ya no vale: sale en «Clásica», que solo lleva el logo.
        byte[] clasica = descargarPanel();
        assertThat(clasica).isNotEqualTo(columnas);
        assertThat(imagenes(clasica, 1)).as("«Clásica»: solo el logo").hasSize(1);
        assertThat(imagenes(descargarPublico("renaser"), 1)).hasSize(1);

        // Solo el color también cuenta.
        conToken(put(BROCHURE_PANEL), tokenTalento, """
                {"plantillaBrochure": "CLASICA", "colorBrochure": "NOCHE"}""")
                .andExpect(status().isOk());
        assertThat(descargarPanel()).isNotEqualTo(clasica);

        // El PUT de «Guardar» sin plantilla ni color no pisa lo elegido en la pestaña.
        conToken(put(PRESENTACION), tokenTalento, json.writeValueAsString(Map.of(
                "quienesSomos", "Ahora también hacemos fideos.")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.plantillaBrochure").value("CLASICA"))
                .andExpect(jsonPath("$.colorBrochure").value("NOCHE"));
    }

    @Test
    @Order(13)
    @DisplayName("AC-14: lo que B elige en su pestaña es suyo; A sigue con lo suyo")
    void laPestanaCadaEmpresaLoSuyo() throws Exception {
        conToken(put(BROCHURE_PANEL), tokenAcme, """
                {"plantillaBrochure": "COLUMNAS", "colorBrochure": "VERDE"}""")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.codigo").value("ACME"))
                .andExpect(jsonPath("$.plantillaBrochure").value("COLUMNAS"))
                .andExpect(jsonPath("$.quienesSomos").value("Construimos casas que duran."));

        assertThat(eleccionDe(acme)).isEqualTo("COLUMNAS/VERDE");
        assertThat(eleccionDe(plataforma)).isEqualTo("CLASICA/NOCHE");
        assertThat(texto(conToken(get(BROCHURE_PANEL), tokenAcme, null)
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsByteArray()))
                .contains("Construimos casas que duran.").doesNotContain("fideos");
    }

    // ============ Ayudas ============

    private String eleccionDe(long organizacion) {
        return jdbc.queryForObject("select plantilla_brochure || '/' || color_brochure "
                + "from organizacion where id = ?", String.class, organizacion);
    }

    private byte[] descargarPanel() throws Exception {
        return conToken(get(BROCHURE_PANEL), tokenTalento, null)
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsByteArray();
    }

    private byte[] descargarPublico(String codigo) throws Exception {
        return mvc.perform(get("/api/v1/portal/empresas/" + codigo + "/brochure"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsByteArray();
    }

    private static String normalizado(String texto) {
        return texto.replaceAll("\\s+", " ").strip();
    }

    private static String texto(byte[] archivo) throws IOException {
        try (PDDocument documento = Loader.loadPDF(archivo)) {
            return new PDFTextStripper().getText(documento).replaceAll("\\s+", " ");
        }
    }

    private static int paginas(byte[] archivo) throws IOException {
        try (PDDocument documento = Loader.loadPDF(archivo)) {
            return documento.getNumberOfPages();
        }
    }

    private static List<String> imagenes(byte[] archivo, int pagina) throws IOException {
        List<String> salida = new ArrayList<>();
        try (PDDocument documento = Loader.loadPDF(archivo)) {
            PDResources recursos = documento.getPage(pagina - 1).getResources();
            for (COSName nombre : recursos.getXObjectNames()) {
                if (recursos.getXObject(nombre) instanceof PDImageXObject imagen) {
                    salida.add(imagen.getWidth() + "x" + imagen.getHeight());
                }
            }
        }
        return salida;
    }

    private static MockMultipartFile logoTransparente() throws IOException {
        BufferedImage imagen = new BufferedImage(400, 400, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = imagen.createGraphics();
        g.setColor(new Color(0x0F4C3A));
        g.fillOval(100, 100, 200, 200);
        g.dispose();
        ByteArrayOutputStream salida = new ByteArrayOutputStream();
        ImageIO.write(imagen, "png", salida);
        return new MockMultipartFile("archivo", "logo.png", "image/png", salida.toByteArray());
    }

    private static MockMultipartFile portada() throws IOException {
        BufferedImage imagen = new BufferedImage(1200, 300, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = imagen.createGraphics();
        g.setColor(new Color(0xBCD3EA));
        g.fillRect(0, 0, 1200, 300);
        g.dispose();
        ByteArrayOutputStream salida = new ByteArrayOutputStream();
        ImageIO.write(imagen, "jpeg", salida);
        return new MockMultipartFile("archivo", "portada.jpg", "image/jpeg", salida.toByteArray());
    }

    /** Una persona del equipo con un rol, sembrada a mano: su token se emite aparte. */
    private long equipo(long organizacion, String rol, String nombre) {
        Long persona = jdbc.queryForObject("insert into persona (nombre, apellidos) values (?, "
                + "'Equipo') returning id", Long.class, nombre);
        Long usuario = jdbc.queryForObject("insert into usuario (organizacion_id, persona_id, "
                + "correo, es_equipo, es_activo) values (?, ?, ?, true, true) returning id",
                Long.class, organizacion, persona, nombre.toLowerCase() + "@brochure.pe");
        jdbc.update("insert into usuario_rol (usuario_id, rol_id) select ?, id from rol "
                + "where organizacion_id = ? and codigo = ?", usuario, organizacion, rol);
        return usuario;
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
