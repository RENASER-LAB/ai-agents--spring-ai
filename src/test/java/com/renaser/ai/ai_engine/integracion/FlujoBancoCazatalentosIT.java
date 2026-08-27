package com.renaser.ai.ai_engine.integracion;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.renaser.ai.ai_engine.integracion.soporte.ImagenesDeContenedores;

import org.apache.poi.xssf.usermodel.XSSFRow;
import org.apache.poi.xssf.usermodel.XSSFSheet;
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
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.rabbitmq.RabbitMQContainer;

import java.io.ByteArrayOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * El banco CAZATALENTOS entra por el mismo endpoint que el v3 y sale publicable.
 *
 * <p>El importador detecta el formato por la hoja «Prueba RENASER», guarda las tres
 * declaraciones del método (C3 esperado, C4 esperado, señal de 0), marca la versión con
 * su método de calificación y respeta las dos fronteras de siempre: un archivo con
 * errores no deja nada a medias, y la aduana de publicar exige el método completo.
 *
 * <p>El primer caso importa {@code docs/insumos/CAZATALENTOS-DIR.xlsx} tal cual está en el
 * repositorio — la misma excepción deliberada que {@link FlujoImportadorBancoIT}: ese xlsx
 * no es un apaño de la prueba, es el instrumento de la clienta, y una copia en
 * {@code src/test/resources} solo se quedaría vieja.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@Testcontainers
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@DisplayName("El banco CAZATALENTOS se importa y se publica")
public class FlujoBancoCazatalentosIT {

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

    static String tokenEquipo;
    static long versionImportada;

    @Test
    @Order(1)
    @DisplayName("El Excel real de la clienta crea un borrador con el método a cuestas")
    void elExcelRealCreaUnBorrador() throws Exception {
        tokenEquipo = json.readTree(mvc.perform(post("/api/v1/panel/auth/dev-login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"usuarioRenaserOsId\":\"dev-1\"}"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString()).get("token").asText();

        byte[] archivo = Files.readAllBytes(Path.of("docs", "insumos", "CAZATALENTOS-DIR.xlsx"));
        String cuerpo = importar(archivo, "CAZATALENTOS-DIR.xlsx", "DIRECCION",
                "Banco CAZATALENTOS · Directivo")
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        JsonNode resultado = json.readTree(cuerpo);
        versionImportada = resultado.get("versionBancoId").asLong();
        // 18 puntuables + 3 de cierre. Sin opciones, campos, rangos ni pares: es todo abierto.
        assertThat(resultado.get("preguntas").asInt()).isEqualTo(21);
        assertThat(resultado.get("opciones").asInt()).isZero();
        assertThat(resultado.get("dimensionesAsignadas").asInt()).isEqualTo(18);

        assertThat(jdbc.queryForObject(
                "select estado from version_banco where id = ?", String.class, versionImportada))
                .isEqualTo("BORRADOR");
        assertThat(jdbc.queryForObject(
                "select metodo_calificacion from version_banco where id = ?",
                String.class, versionImportada)).isEqualTo("CRITERIOS");

        // Las tres declaraciones del método viajaron del Excel a la base.
        assertThat(jdbc.queryForObject("""
                select senal_de_cero from pregunta
                where version_banco_id = ? and codigo = 'R18'""",
                String.class, versionImportada)).isEqualTo("Acepta dejarlo pasar.");
        assertThat(jdbc.queryForObject("""
                select c3_esperado from pregunta
                where version_banco_id = ? and codigo = 'R11'""",
                String.class, versionImportada)).isEqualTo("Cifra antes y cifra después.");
        // La regla dura de R11 quedó como marcador legible por el motor.
        assertThat(jdbc.queryForObject("""
                select logica_interna from pregunta
                where version_banco_id = ? and codigo = 'R11'""",
                String.class, versionImportada)).startsWith("[TOPE_SIN_DATO=2]");
        // R18 eliminatoria con su pilar; Z03 eliminatoria sin puntuar.
        assertThat(jdbc.queryForObject("""
                select pd.dimension_codigo from pregunta_dimension pd
                join pregunta p on p.id = pd.pregunta_id
                where p.version_banco_id = ? and p.codigo = 'R18'""",
                String.class, versionImportada)).isEqualTo("PIL_INTEGRIDAD");
        assertThat(jdbc.queryForObject("""
                select es_eliminatorio and not es_puntuable from pregunta
                where version_banco_id = ? and codigo = 'Z03'""",
                Boolean.class, versionImportada)).isTrue();
    }

    @Test
    @Order(2)
    @DisplayName("El borrador completo pasa la aduana y se publica")
    void elBorradorSePublica() throws Exception {
        mvc.perform(post("/api/v1/panel/banco-preguntas/versiones/{id}/publicacion",
                        versionImportada)
                        .header("Authorization", "Bearer " + tokenEquipo))
                .andExpect(status().is2xxSuccessful());

        assertThat(jdbc.queryForObject(
                "select estado from version_banco where id = ?", String.class, versionImportada))
                .isEqualTo("PUBLICADA");
    }

    @Test
    @Order(3)
    @DisplayName("Una ABIERTA que puntúa sin su señal de 0 no entra: todos los errores, ninguna fila")
    void sinSenalNoEntraNada() throws Exception {
        int versionesAntes = contar("select count(*) from version_banco");
        int preguntasAntes = contar("select count(*) from pregunta");

        String cuerpo = importar(cazatalentosRoto(), "roto.xlsx", "DIRECCION", "Banco roto")
                .andExpect(status().isBadRequest())
                .andReturn().getResponse().getContentAsString();

        JsonNode errores = json.readTree(cuerpo).get("errores");
        assertThat(errores).isNotNull();
        assertThat(errores.toString()).contains("SEÑAL DE 0").contains("C3");
        assertThat(contar("select count(*) from version_banco")).isEqualTo(versionesAntes);
        assertThat(contar("select count(*) from pregunta")).isEqualTo(preguntasAntes);
    }

    // ============ ayudas ============

    private org.springframework.test.web.servlet.ResultActions importar(
            byte[] archivo, String nombre, String nivel, String etiqueta) throws Exception {
        return mvc.perform(multipart("/api/v1/panel/banco-preguntas/importaciones")
                .file(new MockMultipartFile("archivo", nombre,
                        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                        archivo))
                .param("nivelPuestoCodigo", nivel)
                .param("etiqueta", etiqueta)
                .header("Authorization", "Bearer " + tokenEquipo));
    }

    private int contar(String consulta) {
        return jdbc.queryForObject(consulta, Integer.class);
    }

    /** Un libro con la hoja correcta y una pregunta que puntúa sin C3 ni señal de 0. */
    private static byte[] cazatalentosRoto() throws Exception {
        try (XSSFWorkbook libro = new XSSFWorkbook();
             ByteArrayOutputStream salida = new ByteArrayOutputStream()) {
            XSSFSheet hoja = libro.createSheet("Prueba RENASER");
            hoja.createRow(0).createCell(0).setCellValue("Prueba RENASER — rota");
            hoja.createRow(2).createCell(0).setCellValue("Código");
            hoja.createRow(3).createCell(0).setCellValue("guía de la columna");
            XSSFRow fila = hoja.createRow(4);
            fila.createCell(0).setCellValue("R01");
            fila.createCell(1).setCellValue("1 Iniciativa");
            fila.createCell(2).setCellValue("¿Qué mejoraste sin que nadie te lo pidiera?");
            // Sin C3 (col D) ni señal (col F): puntúa, así que las dos faltas son error.
            fila.createCell(4).setCellValue("Quién se opuso.");
            fila.createCell(6).setCellValue("1");
            fila.createCell(7).setCellValue("no");
            libro.write(salida);
            return salida.toByteArray();
        }
    }
}
