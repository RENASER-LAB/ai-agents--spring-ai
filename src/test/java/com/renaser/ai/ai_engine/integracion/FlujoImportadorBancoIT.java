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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * El banco entra por Excel y sale por el portal, sin que nadie escriba una migración.
 *
 * <p>El relato completo del importador: el administrador sube la plantilla, revisa el
 * borrador que sale, lo corrige, lo publica, y el candidato responde ese banco. Por el
 * camino se comprueban las dos fronteras que sostienen todo lo demás: un archivo con
 * errores no deja nada a medias en la base, y una versión publicada admite corregir una
 * errata pero no tocar una clave (RF-138).
 *
 * <p>El último caso importa {@code docs/insumos/banco-v3-directivo.xlsx} tal cual está en
 * el repositorio. Es la excepción deliberada a «los tests no leen archivos de disco»: ese
 * xlsx no es un apaño de la prueba sino el artefacto que ES la especificación del
 * importador —el banco del cliente volcado en la plantilla—, y copiarlo a
 * {@code src/test/resources} solo crearía una copia que se quedaría vieja.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@Testcontainers
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@DisplayName("El banco de preguntas se importa desde un Excel")
public class FlujoImportadorBancoIT {

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
        registro.add("spring.ai.deepseek.api-key", () -> "clave-de-pruebas-no-se-usa");
        registro.add("renaser.ai.calificacion.habilitada", () -> "false");
    }

    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    final ObjectMapper json = new ObjectMapper();

    static String tokenEquipo;
    static long versionImportada;
    static long preguntaCd;

    @Test
    @Order(1)
    @DisplayName("Subir la plantilla llena crea un borrador con todo lo que traía el archivo")
    void subirLaPlantillaCreaUnBorrador() throws Exception {
        tokenEquipo = leer(mvc.perform(post("/api/v1/panel/auth/dev-login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"usuarioRenaserOsId\":\"dev-1\"}"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(), "token");

        String cuerpo = importar(bancoDePrueba(), "banco-ejecutivo.xlsx", "EJECUCION",
                "Banco importado v1 · Ejecutivo")
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        JsonNode resultado = json.readTree(cuerpo);
        versionImportada = resultado.get("versionBancoId").asLong();
        assertThat(resultado.get("preguntas").asInt()).isEqualTo(5);
        assertThat(resultado.get("opciones").asInt()).isEqualTo(7);
        assertThat(resultado.get("camposCaso").asInt()).isEqualTo(2);
        assertThat(resultado.get("rangos").asInt()).isEqualTo(2);
        assertThat(resultado.get("pares").asInt()).isEqualTo(1);

        // Nace en borrador: subir no publica, ni siquiera un archivo perfecto
        assertThat(jdbc.queryForObject(
                "select estado from version_banco where id = ?", String.class, versionImportada))
                .isEqualTo("BORRADOR");
        // Y lo que el Excel no pregunta se derivó: el peso manda sobre esPuntuable, las
        // letras salen del orden de las filas y el orden es el del archivo.
        assertThat(jdbc.queryForObject("""
                select es_puntuable from pregunta
                where version_banco_id = ? and codigo = 'X04'""",
                Boolean.class, versionImportada)).isFalse();
        assertThat(jdbc.queryForList("""
                select o.letra from opcion o join pregunta p on p.id = o.pregunta_id
                where p.version_banco_id = ? and p.codigo = 'X01' order by o.letra""",
                String.class, versionImportada)).containsExactly("a", "b");

        preguntaCd = jdbc.queryForObject("""
                select id from pregunta where version_banco_id = ? and codigo = 'X03'""",
                Long.class, versionImportada);
    }

    @Test
    @Order(2)
    @DisplayName("Un archivo con errores los devuelve todos y no deja ni una fila en la base")
    void unArchivoConErroresNoDejaNada() throws Exception {
        int versionesAntes = contar("select count(*) from version_banco");
        int preguntasAntes = contar("select count(*) from pregunta");

        String cuerpo = importar(bancoRoto(), "roto.xlsx", "EJECUCION", "Banco roto")
                .andExpect(status().isBadRequest())
                .andReturn().getResponse().getContentAsString();

        JsonNode errores = json.readTree(cuerpo).get("errores");
        assertThat(errores).isNotNull();
        assertThat(errores.size()).isGreaterThanOrEqualTo(2);
        assertThat(errores.get(0).get("hoja").asText()).isNotBlank();
        assertThat(errores.get(0).get("fila").asInt()).isPositive();
        // Ni versión a medias ni preguntas huérfanas: el lector corre antes de la base
        assertThat(contar("select count(*) from version_banco")).isEqualTo(versionesAntes);
        assertThat(contar("select count(*) from pregunta")).isEqualTo(preguntasAntes);
    }

    @Test
    @Order(3)
    @DisplayName("El borrador se corrige antes de publicar: se reemplaza y se borra")
    void elBorradorSeCorrigeAntesDePublicar() throws Exception {
        long opcionId = jdbc.queryForObject("""
                select o.id from opcion o join pregunta p on p.id = o.pregunta_id
                where p.version_banco_id = ? and p.codigo = 'X02' order by o.letra desc limit 1""",
                Long.class, versionImportada);

        mvc.perform(delete("/api/v1/panel/banco-preguntas/opciones/" + opcionId)
                        .header("Authorization", "Bearer " + tokenEquipo))
                .andExpect(status().isNoContent());
        assertThat(contar("select count(*) from opcion where id = " + opcionId)).isZero();

        // Y la pregunta entera se reemplaza, con las mismas guardas que al crearla
        mvc.perform(put("/api/v1/panel/banco-preguntas/preguntas/" + preguntaCd)
                        .header("Authorization", "Bearer " + tokenEquipo)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"codigo":"X03","tipo":"CD","enunciado":"Tu caso, mejor contado.",
                                 "esPuntuable":true,"orden":3,"peso":1,"casosPedidos":1}"""))
                .andExpect(status().isOk());
        assertThat(jdbc.queryForObject("select enunciado from pregunta where id = ?",
                String.class, preguntaCd)).isEqualTo("Tu caso, mejor contado.");
    }

    @Test
    @Order(4)
    @DisplayName("En el borrador se corrige cada pieza: opciones, campos, tramos y pares")
    void enElBorradorSeCorrigeCadaPieza() throws Exception {
        // Una opción: se reemplaza entera, clave incluida — sigue siendo un borrador
        long opcionId = jdbc.queryForObject("""
                select o.id from opcion o join pregunta p on p.id = o.pregunta_id
                where p.version_banco_id = ? and p.codigo = 'X01' order by o.letra limit 1""",
                Long.class, versionImportada);
        conToken(put("/api/v1/panel/banco-preguntas/opciones/" + opcionId), """
                {"letra":"a","texto":"Digo lo que hay que decir, aunque incomode","valor":2}""")
                .andExpect(status().isOk());
        assertThat(jdbc.queryForObject("select texto from opcion where id = ?",
                String.class, opcionId)).endsWith("aunque incomode");

        // Un campo de caso: se reemplaza y el sobrante se quita
        long campoId = jdbc.queryForObject("""
                select cc.id from campo_caso cc join pregunta p on p.id = cc.pregunta_id
                where p.version_banco_id = ? and p.codigo = 'X03' order by cc.orden limit 1""",
                Long.class, versionImportada);
        conToken(put("/api/v1/panel/banco-preguntas/campos-caso/" + campoId),
                "{\"orden\":1,\"etiqueta\":\"Nombre de la tarea (texto ≤ 40 car.)\"}")
                .andExpect(status().isOk());
        long campoSobrante = jdbc.queryForObject("""
                select cc.id from campo_caso cc join pregunta p on p.id = cc.pregunta_id
                where p.version_banco_id = ? and p.codigo = 'X03' order by cc.orden desc limit 1""",
                Long.class, versionImportada);
        mvc.perform(delete("/api/v1/panel/banco-preguntas/campos-caso/" + campoSobrante)
                        .header("Authorization", "Bearer " + tokenEquipo))
                .andExpect(status().isNoContent());
        assertThat(contar("""
                select count(*) from campo_caso cc join pregunta p on p.id = cc.pregunta_id
                where p.version_banco_id = %d and p.codigo = 'X03'""".formatted(versionImportada)))
                .isEqualTo(1);

        // Un tramo de un ítem V: se reemplaza uno y se quita otro, y al ítem le queda tabla
        long rangoId = jdbc.queryForObject("""
                select r.id from rango_pregunta r join pregunta p on p.id = r.pregunta_id
                where p.version_banco_id = ? and p.codigo = 'X05' order by r.orden limit 1""",
                Long.class, versionImportada);
        conToken(put("/api/v1/panel/banco-preguntas/rangos/" + rangoId),
                "{\"orden\":1,\"condicion\":\"6 o más\",\"puntaje\":3,\"generaBandera\":false}")
                .andExpect(status().isOk());
        assertThat(jdbc.queryForObject("select condicion from rango_pregunta where id = ?",
                String.class, rangoId)).isEqualTo("6 o más");
        long rangoSobrante = jdbc.queryForObject("""
                select r.id from rango_pregunta r join pregunta p on p.id = r.pregunta_id
                where p.version_banco_id = ? and p.codigo = 'X05' order by r.orden desc limit 1""",
                Long.class, versionImportada);
        mvc.perform(delete("/api/v1/panel/banco-preguntas/rangos/" + rangoSobrante)
                        .header("Authorization", "Bearer " + tokenEquipo))
                .andExpect(status().isNoContent());

        // Y el par: se reemplaza y luego se quita del todo
        long parId = jdbc.queryForObject(
                "select id from par_consistencia where version_banco_id = ?",
                Long.class, versionImportada);
        long a = jdbc.queryForObject("""
                select id from pregunta where version_banco_id = ? and codigo = 'X01'""",
                Long.class, versionImportada);
        long b = jdbc.queryForObject("""
                select id from pregunta where version_banco_id = ? and codigo = 'X05'""",
                Long.class, versionImportada);
        conToken(put("/api/v1/panel/banco-preguntas/pares-consistencia/" + parId), """
                {"preguntaAId":%d,"preguntaBId":%d,"penalizacionPorcentaje":10,
                 "separacionMinimaItems":2,"condicion":"se contradicen en el número"}"""
                .formatted(a, b)).andExpect(status().isOk());
        assertThat(jdbc.queryForObject(
                "select pregunta_b_id from par_consistencia where id = ?", Long.class, parId))
                .isEqualTo(b);
        mvc.perform(delete("/api/v1/panel/banco-preguntas/pares-consistencia/" + parId)
                        .header("Authorization", "Bearer " + tokenEquipo))
                .andExpect(status().isNoContent());
        assertThat(contar("select count(*) from par_consistencia where version_banco_id = "
                + versionImportada)).isZero();

        // De paso, lo que el panel lee para pintar todo esto
        mvc.perform(get("/api/v1/panel/banco-preguntas/versiones")
                        .header("Authorization", "Bearer " + tokenEquipo))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].estado").exists());
        mvc.perform(get("/api/v1/panel/banco-preguntas/versiones/" + versionImportada + "/preguntas")
                        .header("Authorization", "Bearer " + tokenEquipo))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(5))
                // La lógica interna entra por el Excel y no sale nunca (RF-53)
                .andExpect(content().string(org.hamcrest.Matchers.not(
                        org.hamcrest.Matchers.containsString("logicaInterna"))));
        mvc.perform(get("/api/v1/panel/banco-preguntas/preguntas/" + preguntaCd + "/campos-caso")
                        .header("Authorization", "Bearer " + tokenEquipo))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1));
        mvc.perform(get("/api/v1/panel/banco-preguntas/preguntas/"
                        + jdbc.queryForObject("""
                                select id from pregunta where version_banco_id = ? and codigo = 'X01'""",
                                Long.class, versionImportada) + "/opciones")
                        .header("Authorization", "Bearer " + tokenEquipo))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2));
        // El catálogo con que el panel llenará la columna «Qué mide»
        mvc.perform(get("/api/v1/panel/banco-preguntas/dimensiones")
                        .header("Authorization", "Bearer " + tokenEquipo))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(22))
                .andExpect(jsonPath("$[0].codigo").exists());
    }

    @Test
    @Order(5)
    @DisplayName("Publicar el borrador importado pasa la aduana de coherencia de siempre")
    void publicarElBorradorImportado() throws Exception {
        mvc.perform(post("/api/v1/panel/banco-preguntas/versiones/" + versionImportada + "/publicacion")
                        .header("Authorization", "Bearer " + tokenEquipo))
                .andExpect(status().isOk());

        assertThat(jdbc.queryForObject(
                "select estado from version_banco where id = ?", String.class, versionImportada))
                .isEqualTo("PUBLICADA");
        // Una sola publicada por nivel: la del banco v3 que sembró la V20 quedó archivada
        assertThat(contar("""
                select count(*) from version_banco
                where tipo_banco = 'NIVEL' and nivel_puesto_codigo = 'EJECUCION'
                  and estado = 'PUBLICADA'""")).isEqualTo(1);
    }

    @Test
    @Order(6)
    @DisplayName("Sobre lo publicado se corrige una errata, pero la clave no se toca")
    void sobreLoPublicadoSoloElTexto() throws Exception {
        long preguntaId = jdbc.queryForObject("""
                select id from pregunta where version_banco_id = ? and codigo = 'X01'""",
                Long.class, versionImportada);

        mvc.perform(patch("/api/v1/panel/banco-preguntas/preguntas/" + preguntaId + "/textos")
                        .header("Authorization", "Bearer " + tokenEquipo)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"enunciado\":\"Elige la frase que MÁS te describe.\"}"))
                .andExpect(status().isOk());
        assertThat(jdbc.queryForObject("select enunciado from pregunta where id = ?",
                String.class, preguntaId)).isEqualTo("Elige la frase que MÁS te describe.");

        // Lo demás sigue cerrado: ni añadir opciones ni reemplazar la pregunta entera
        mvc.perform(post("/api/v1/panel/banco-preguntas/preguntas/" + preguntaId + "/opciones")
                        .header("Authorization", "Bearer " + tokenEquipo)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"letra\":\"z\",\"texto\":\"Una de contrabando\",\"valor\":2}"))
                .andExpect(status().isConflict());
        mvc.perform(put("/api/v1/panel/banco-preguntas/preguntas/" + preguntaId)
                        .header("Authorization", "Bearer " + tokenEquipo)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"codigo":"X01","tipo":"EF-4","enunciado":"Otra cosa",
                                 "esPuntuable":true,"orden":1,"peso":2}"""))
                .andExpect(status().isConflict());
        // El peso no se movió: por el PATCH no hay manera de tocarlo
        assertThat(jdbc.queryForObject("select peso from pregunta where id = ?",
                Short.class, preguntaId)).isEqualTo((short) 1);

        // La misma corrección, pieza por pieza, sin que ninguna clave se mueva
        long opcionId = jdbc.queryForObject("""
                select o.id from opcion o join pregunta p on p.id = o.pregunta_id
                where p.id = ? order by o.letra limit 1""", Long.class, preguntaId);
        conToken(patch("/api/v1/panel/banco-preguntas/opciones/" + opcionId + "/textos"),
                "{\"texto\":\"Digo lo que hay que decir\"}").andExpect(status().isOk());
        assertThat(jdbc.queryForObject("select texto from opcion where id = ?",
                String.class, opcionId)).isEqualTo("Digo lo que hay que decir");
        assertThat(jdbc.queryForObject("select valor from opcion where id = ?",
                java.math.BigDecimal.class, opcionId)).isEqualByComparingTo("2");

        long campoId = jdbc.queryForObject("""
                select cc.id from campo_caso cc join pregunta p on p.id = cc.pregunta_id
                where p.version_banco_id = ? and p.codigo = 'X03' limit 1""",
                Long.class, versionImportada);
        conToken(patch("/api/v1/panel/banco-preguntas/campos-caso/" + campoId + "/textos"),
                "{\"etiqueta\":\"Nombre de la tarea (texto ≤ 40 caracteres)\"}")
                .andExpect(status().isOk());
        assertThat(jdbc.queryForObject("select etiqueta from campo_caso where id = ?",
                String.class, campoId)).endsWith("(texto ≤ 40 caracteres)");

        long rangoId = jdbc.queryForObject("""
                select r.id from rango_pregunta r join pregunta p on p.id = r.pregunta_id
                where p.version_banco_id = ? and p.codigo = 'X05' limit 1""",
                Long.class, versionImportada);
        conToken(patch("/api/v1/panel/banco-preguntas/rangos/" + rangoId + "/textos"),
                "{\"condicion\":\"Seis o más personas a cargo\"}").andExpect(status().isOk());
        assertThat(jdbc.queryForObject("select condicion from rango_pregunta where id = ?",
                String.class, rangoId)).isEqualTo("Seis o más personas a cargo");
        // El puntaje del tramo, que sí puntúa, sigue donde estaba
        assertThat(jdbc.queryForObject("select puntaje from rango_pregunta where id = ?",
                java.math.BigDecimal.class, rangoId)).isEqualByComparingTo("3");

        // Y renombrar la versión: lo único que se corrige de la versión misma
        conToken(patch("/api/v1/panel/banco-preguntas/versiones/" + versionImportada + "/etiqueta"),
                "{\"etiqueta\":\"Banco importado v1 · Ejecutivo y Operativo\"}")
                .andExpect(status().isOk());
        assertThat(jdbc.queryForObject("select etiqueta from version_banco where id = ?",
                String.class, versionImportada)).endsWith("Ejecutivo y Operativo");
    }

    @Test
    @Order(7)
    @DisplayName("Un borrador que no sirve se descarta entero y no deja huérfanas")
    void unBorradorQueNoSirveSeDescarta() throws Exception {
        long paraTirar = json.readTree(importar(bancoDePrueba(), "otro.xlsx", "EJECUCION",
                "Banco para descartar").andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString()).get("versionBancoId").asLong();

        mvc.perform(delete("/api/v1/panel/banco-preguntas/versiones/" + paraTirar)
                        .header("Authorization", "Bearer " + tokenEquipo))
                .andExpect(status().isNoContent());

        assertThat(contar("select count(*) from version_banco where id = " + paraTirar)).isZero();
        assertThat(contar("select count(*) from pregunta where version_banco_id = " + paraTirar)).isZero();
        assertThat(contar("select count(*) from par_consistencia where version_banco_id = " + paraTirar)).isZero();

        // Y la publicada no se descarta: para retirarla está archivar
        mvc.perform(delete("/api/v1/panel/banco-preguntas/versiones/" + versionImportada)
                        .header("Authorization", "Bearer " + tokenEquipo))
                .andExpect(status().isConflict());
    }

    @Test
    @Order(8)
    @DisplayName("El banco v3 del cliente entra entero desde su Excel, con sus 85 preguntas")
    void elBancoRealEntraEntero() throws Exception {
        Path archivo = Path.of("docs/insumos/banco-v3-directivo.xlsx");
        assertThat(archivo).as("el volcado del banco v3 vive en el repositorio").exists();

        String cuerpo = importar(Files.readAllBytes(archivo), archivo.getFileName().toString(),
                "DIRECCION", "Banco RENASER v3 desde Excel · Directivo")
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        JsonNode resultado = json.readTree(cuerpo);
        long version = resultado.get("versionBancoId").asLong();
        assertThat(resultado.get("preguntas").asInt()).isEqualTo(85);
        assertThat(resultado.get("opciones").asInt()).isEqualTo(347);
        assertThat(resultado.get("camposCaso").asInt()).isEqualTo(119);
        assertThat(resultado.get("rangos").asInt()).isEqualTo(32);
        assertThat(resultado.get("pares").asInt()).isEqualTo(3);

        // Y los textos son los mismos que sembró la V20, letra por letra: el viaje
        // Excel → base no pierde ni un carácter
        assertThat(jdbc.queryForObject("""
                select p.enunciado from pregunta p
                where p.version_banco_id = ? and p.codigo = 'D01'""", String.class, version))
                .isEqualTo(jdbc.queryForObject("""
                        select p.enunciado from pregunta p
                        join version_banco vb on vb.id = p.version_banco_id
                        where vb.etiqueta = 'Banco RENASER v3 · Directivo' and p.codigo = 'D01'""",
                        String.class));
        // Incluido lo que costó reparar: un caso multiplicado con su grupo delante
        assertThat(jdbc.queryForObject("""
                select cc.etiqueta from campo_caso cc join pregunta p on p.id = cc.pregunta_id
                where p.version_banco_id = ? and p.codigo = 'D11' and cc.orden = 1""",
                String.class, version))
                .isEqualTo("Indicador 1 · Nombre (texto ≤ 40 car.)");
    }

    /**
     * El banco Ejecutivo tiene cuatro ítems V que no se puntúan por tramos: dos con la
     * fórmula escrita (O02, O07) y dos que usan la tabla de otro ítem (O32→D57,
     * O48→D84). Sin columnas para eso en la plantilla, el archivo se importaba pero
     * `validarCoherencia` lo frenaba al publicar y el viaje se quedaba a medias.
     */
    @Test
    @Order(9)
    @DisplayName("El banco Ejecutivo, con sus fórmulas y sus tablas prestadas, llega a publicarse")
    void elBancoEjecutivoLlegaAPublicarse() throws Exception {
        Path archivo = Path.of("docs/insumos/banco-v3-ejecutivo-y-operativo.xlsx");
        long version = json.readTree(importar(Files.readAllBytes(archivo),
                        archivo.getFileName().toString(), "EJECUCION",
                        "Banco RENASER v3 desde Excel · Ejecutivo")
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString())
                .get("versionBancoId").asLong();

        assertThat(jdbc.queryForObject("""
                select formula_puntaje from pregunta
                where version_banco_id = ? and codigo = 'O02'""", String.class, version))
                .isEqualTo("(campos llenos ÷ 5) × 3");
        assertThat(jdbc.queryForObject("""
                select rangos_de_pregunta_codigo from pregunta
                where version_banco_id = ? and codigo = 'O32'""", String.class, version))
                .isEqualTo("D57");

        mvc.perform(post("/api/v1/panel/banco-preguntas/versiones/" + version + "/publicacion")
                        .header("Authorization", "Bearer " + tokenEquipo))
                .andExpect(status().isOk());
        assertThat(jdbc.queryForObject("select estado from version_banco where id = ?",
                String.class, version)).isEqualTo("PUBLICADA");
    }

    // ============ Helpers ============

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

    private org.springframework.test.web.servlet.ResultActions conToken(
            org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder peticion,
            String cuerpo) throws Exception {
        return mvc.perform(peticion
                .header("Authorization", "Bearer " + tokenEquipo)
                .contentType(MediaType.APPLICATION_JSON)
                .content(cuerpo));
    }

    private String leer(String cuerpoRespuesta, String campo) throws Exception {
        return json.readTree(cuerpoRespuesta).get(campo).asText();
    }

    // ============ Los xlsx de la prueba, fabricados al vuelo ============

    private static void hoja(XSSFWorkbook libro, String nombre, String ancla, String[][] filas) {
        XSSFSheet hoja = libro.createSheet(nombre);
        hoja.createRow(0).createCell(0).setCellValue(nombre);
        hoja.createRow(2).createCell(0).setCellValue(ancla);
        hoja.createRow(3).createCell(0).setCellValue("guía de la columna");
        int n = 4;
        for (String[] fila : filas) {
            XSSFRow r = hoja.createRow(n++);
            for (int c = 0; c < fila.length; c++) {
                if (fila[c] != null) {
                    r.createCell(c).setCellValue(fila[c]);
                }
            }
        }
    }

    private static byte[] bytesDe(XSSFWorkbook libro) throws Exception {
        try (libro; ByteArrayOutputStream salida = new ByteArrayOutputStream()) {
            libro.write(salida);
            return salida.toByteArray();
        }
    }

    /** Un banco chico pero completo: cuatro formatos con todo lo que cada uno necesita. */
    private static byte[] bancoDePrueba() throws Exception {
        XSSFWorkbook libro = new XSSFWorkbook();
        hoja(libro, "Preguntas", "Código", new String[][]{
                {"X01", "EF-4", "Elige la frase que MAS te describe.", null, "1", "no"},
                {"X02", "SJT-R", "Una tarea no se cumplió.", "En tu turno.", "1", "no"},
                {"X03", "CD", "Tu caso. (2 campos)", null, "1", "no", null, "2"},
                {"X04", "PC", "Autorizo la verificación.", null, "0", "sí"},
                {"X05", "V", "Personas a tu cargo:", null, "1", "no"},
        });
        hoja(libro, "Opciones", "Código de la pregunta", new String[][]{
                {"X01", "Digo lo que hay que decir", null, "2"},
                {"X01", "Evito el conflicto", null, "-2"},
                {"X02", "Revisar antes de reclamar", "5"},
                {"X02", "Escalarlo de inmediato", "1"},
                // La tercera se borra en el paso 3: publicar exige que queden dos
                {"X02", "Anotarlo y seguir", "3"},
                {"X04", "Sí"},
                {"X04", "No"},
        });
        hoja(libro, "Campos de caso (CD)", "Código de la pregunta", new String[][]{
                {"X03", "Nombre de la tarea (texto ≤ 40 car.)"},
                {"X03", "Cuánto te toma (menos de 1 h / más)"},
        });
        hoja(libro, "Rangos (V)", "Código de la pregunta", new String[][]{
                {"X05", "5 o más", "3", "no"},
                {"X05", "Ninguna", "0", "sí"},
        });
        hoja(libro, "Pares", "Pregunta A", new String[][]{
                {"X01", "X02", "5", "3", "dicen cosas distintas"},
        });
        return bytesDe(libro);
    }

    /** El mismo archivo con dos defectos a la vez, para ver que los cuenta todos. */
    private static byte[] bancoRoto() throws Exception {
        XSSFWorkbook libro = new XSSFWorkbook();
        hoja(libro, "Preguntas", "Código", new String[][]{
                {"X01", "EF-4", "Con peso imposible.", null, "9", "no"},
                {"X01", "SJT-R", "Con el código repetido.", null, "1", "no"},
        });
        hoja(libro, "Opciones", "Código de la pregunta", new String[][]{
                {"X99", "De una pregunta que no existe"},
        });
        return bytesDe(libro);
    }
}
