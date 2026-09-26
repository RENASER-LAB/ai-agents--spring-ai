package com.renaser.ai.ai_engine.integracion;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.renaser.ai.ai_engine.integracion.soporte.ImagenesDeContenedores;
import com.renaser.ai.ai_engine.perfilintegral.repository.CriterioRepository;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.util.PaneInformation;
import org.apache.poi.xssf.usermodel.XSSFRow;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.rabbitmq.RabbitMQContainer;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doThrow;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * El Excel del ranking contra la base de verdad: la prueba vigente decide las columnas de
 * criterio, y la cabecera de los dos Excel se abre con su altura escrita.
 *
 * <p>⚠️ <b>La mezcla de versiones se siembra en la base, no se fabrica por la API.</b> Desde
 * el 27/08 una vacante con postulantes no puede cambiar de prueba, así que el caso de la
 * vacante 13 —candidatos que abrieron la demo, y la vacante que después pasó a la de
 * Administrador— solo existe porque llegó antes de esa regla. Aquí se reproduce igual que
 * llegó: la vacante apunta a una versión, dos candidatos la abren, y la vacante pasa a otra.
 *
 * <p>Cada prueba arma su propia vacante, así que se pueden leer y correr sueltas. Lo que
 * comparten es la cuenta del equipo, el área y el puesto.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
@ExtendWith(OutputCaptureExtension.class)
@DisplayName("El Excel del ranking: la prueba vigente manda y la cabecera se lee entera")
public class FlujoExcelDelRankingIT {

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

    /**
     * El repositorio de verdad, envuelto: delega en él en todo salvo en lo que una prueba
     * diga. Solo lo usa el caso en que la prueba vigente no se puede leer.
     */
    @MockitoSpyBean CriterioRepository criterios;

    final ObjectMapper json = new ObjectMapper();

    static String token;
    static long areaId;
    static long puestoId;

    /** La prueba de Administrador de la vacante 13, tal como está en producción. */
    static final String[][] ADMINISTRADOR = {
            {"EXPERIENCIA", "Experiencia y magnitud de lo administrado", "15"},
            {"CAJA", "Manejo y control de caja", "20"},
            {"DIVISAS", "Conocimiento del negocio de divisas", "15"},
            {"SEDES", "Supervisión de múltiples sedes", "15"},
            {"PERSONAL", "Gestión de personal", "15"},
            {"FINANZAS", "Coordinación contable y financiera", "10"},
            {"OBJETIVOS", "Orientación a resultados y plan de crecimiento", "10"}};

    /** La «Prueba de ejecución · demo» que siembra scripts/sembrar-datos-de-prueba.py. */
    static final String[][] DEMO = {
            {"CRITERIO", "Criterio de priorización", "40"},
            {"DESCARTE", "Qué deja fuera", "25"},
            {"CLARIDAD", "Claridad", "20"},
            {"REACCION", "Reacción al cambio", "15"}};

    static final List<String> FIJAS_ANTES = List.of("#", "Candidato", "Correo", "CV", "Teléfono",
            "Nota Examen Técnico /100", "Nota Perfil Integral /100");
    static final List<String> FIJAS_DESPUES = List.of("Nota Combinada /100",
            "Justificación resumida", "Justificación detallada");

    /** Lo que mide una línea de la cabecera: la de Calibri 11. */
    static final float LINEA = 15f;

    @BeforeEach
    void laCuentaElAreaYElPuesto() throws Exception {
        if (token != null) {
            return;
        }
        token = leer(mvc.perform(post("/api/v1/panel/auth/dev-login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"usuarioRenaserOsId\":\"dev-excel\"}"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(), "token");
        areaId = jdbc.queryForObject("INSERT INTO area (organizacion_id, nombre, es_activa) "
                + "VALUES (1, 'Administración', true) RETURNING id", Long.class);
        puestoId = Long.parseLong(leer(mvc.perform(post("/api/v1/panel/puestos")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"nombre": "Administrador", "nivelPuestoCodigo": "EJECUCION",
                                 "familiaCodigo": "OPERACIONES"}"""))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString(), "id"));
    }

    // ============ La prueba vigente manda las columnas ============

    @Test
    @DisplayName("AC-01 · AC-03 · AC-10 · la vacante mezclada saca solo las 7 columnas de la vigente; lo de la demo va a la justificación")
    void laVacanteMezcladaSacaSoloLaVigente() throws Exception {
        Mezcla m = vacanteMezclada("Administrador de sede · mezclada");

        byte[] libro = excel(m.vacante(), "PRUEBA_PUESTO", m.ana(), m.bruno(), m.carla());

        assertThat(cabeceras(libro)).containsExactlyElementsOf(
                concat(FIJAS_ANTES, rotulos(ADMINISTRADOR), FIJAS_DESPUES));
        assertThat(cabeceras(libro)).doesNotContainAnyElementsOf(rotulos(DEMO));

        // Ana rindió la vigente: sus siete notas, cada una bajo su criterio.
        assertThat(fila(libro, 1).get(1)).isEqualTo("Ana Quispe");
        assertThat(fila(libro, 1).subList(7, 14)).containsExactly(NOTAS_DE_ANA);
        // Bruno rindió la demo: celdas en blanco, porque sus criterios son otros, y sus
        // cuatro notas enteras en «Justificación detallada».
        assertThat(fila(libro, 2).get(1)).isEqualTo("Bruno Díaz");
        assertThat(fila(libro, 2).subList(7, 14)).allMatch(String::isEmpty);
        assertThat(fila(libro, 2).get(16)).contains(
                "Criterio de priorización (30/40): Ordena por impacto y dice por qué",
                "Qué deja fuera (20/25): Nombra lo que no atiende",
                "Claridad (0/20): Se pierde en el detalle",
                "Reacción al cambio (10/15): Rehace el orden");
        // Carla abrió la demo y no tiene nota: su fila sale entera, con los huecos.
        assertThat(fila(libro, 3).get(1)).isEqualTo("Carla Núñez");
        assertThat(fila(libro, 3).subList(7, 14)).allMatch(String::isEmpty);

        // AC-10: el rótulo que más líneas pide —«Orientación a resultados y plan de
        // crecimiento (pts /10)» en 14 caracteres— son unas cinco, y la altura va ESCRITA.
        XSSFRow cabecera = laCabecera(libro);
        assertThat(cabecera.getCTRow().getCustomHeight()).isTrue();
        assertThat(cabecera.getHeightInPoints()).isBetween(5 * LINEA, 8 * LINEA);
        assertThat(anchos(libro)).containsExactlyElementsOf(concat(
                List.of(5, 34, 32, 30, 18, 15, 16), repetido(14, 7), List.of(17, 48, 90)));
        assertThat(laCabeceraSigueFija(libro)).isTrue();
    }

    @Test
    @DisplayName("AC-09 · el ranking del panel sigue igual: cada fila con la rúbrica que rindió y sin campos nuevos")
    void elRankingDelPanelNoCambia() throws Exception {
        Mezcla m = vacanteMezclada("Administrador de sede · el panel");

        JsonNode ranking = json.readTree(mvc.perform(
                        get("/api/v1/panel/vacantes/" + m.vacante() + "/ranking?etapa=PRUEBA_PUESTO")
                                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString());

        assertThat(nombresDeCriterio(ranking, m.bruno())).containsExactlyElementsOf(nombres(DEMO));
        assertThat(nombresDeCriterio(ranking, m.ana())).containsExactlyElementsOf(nombres(ADMINISTRADOR));
        // La rúbrica vigente no viaja en la respuesta del panel: la pide solo el Excel.
        List<String> campos = new ArrayList<>();
        ranking.fieldNames().forEachRemaining(campos::add);
        assertThat(campos).containsExactlyInAnyOrder("vacanteId", "vacante", "puesto",
                "nivelPuesto", "total", "conPasadaFina", "calificados", "enCurso", "fallidos",
                "puedeVerPretension", "puedeMoverPostulacion", "vacanteMuestraSueldo", "filas");
    }

    @Test
    @DisplayName("AC-02 · con el filtro dejando fuera a todos los de la vigente, las 7 columnas siguen, en blanco")
    void elFiltroNoSeComeLasColumnas() throws Exception {
        Mezcla m = vacanteMezclada("Administrador de sede · filtrada");

        // Solo los que rindieron la demo: Ana, la única con notas de la vigente, fuera.
        byte[] libro = excel(m.vacante(), "PRUEBA_PUESTO", m.bruno(), m.carla());

        assertThat(cabeceras(libro)).containsExactlyElementsOf(
                concat(FIJAS_ANTES, rotulos(ADMINISTRADOR), FIJAS_DESPUES));
        assertThat(fila(libro, 1).subList(7, 14)).allMatch(String::isEmpty);
        assertThat(fila(libro, 2).subList(7, 14)).allMatch(String::isEmpty);
        assertThat(fila(libro, 1).get(16)).contains("Criterio de priorización (30/40)");
    }

    /*
     * AC-04. Con todos en la vigente, las columnas de la rúbrica y las que salían de juntar
     * las de las filas son las mismas: el archivo tiene que salir celda por celda como hoy.
     * Se comprueba entero, fila a fila, contra lo escrito aquí.
     */
    @Test
    @DisplayName("AC-04 · con todos en la vigente el archivo es el de siempre: columnas, celdas y pie")
    void conUnaSolaVersionElArchivoNoCambia() throws Exception {
        long vacante = nuevaVacante("Cajero · una sola versión");
        Version caja = version("Prueba de cajero", new String[][]{
                {"CAJA", "Manejo y control de caja", "60"},
                {"DIVISAS", "Conocimiento del negocio de divisas", "40"}});
        ponerLaPrueba(vacante, caja.id());
        long ana = candidato(vacante, "Ana", "Quispe");
        rindio(ana, caja.id());
        nota(ana, caja.criterio("CAJA"), "50", "Cuadra la caja sin diferencias");
        nota(ana, caja.criterio("DIVISAS"), "30", "Conoce el tipo de cambio");
        long bruno = candidato(vacante, "Bruno", "Díaz");
        rindio(bruno, caja.id());
        nota(bruno, caja.criterio("CAJA"), "45", "Cuadra con una diferencia");

        byte[] libro = excel(vacante, "PRUEBA_PUESTO", bruno, ana);

        assertThat(cabeceras(libro)).containsExactly("#", "Candidato", "Correo", "CV", "Teléfono",
                "Nota Examen Técnico /100", "Nota Perfil Integral /100",
                "Manejo y control de caja (pts /60)", "Conocimiento del negocio de divisas (pts /40)",
                "Nota Combinada /100", "Justificación resumida", "Justificación detallada");
        // El «#» es el puesto en el ranking —sin notas de etapa, por nombre—, y las filas
        // van en el orden pedido.
        assertThat(fila(libro, 1)).containsExactly("2", "Bruno Díaz", "", "", "",
                "rúbrica incompleta", "falta una nota de etapa", "45", "",
                "falta una nota de etapa", "",
                "Manejo y control de caja (45/60): Cuadra con una diferencia");
        assertThat(fila(libro, 2)).containsExactly("1", "Ana Quispe", "", "", "",
                "rúbrica incompleta", "falta una nota de etapa", "50", "30",
                "falta una nota de etapa", "",
                "Manejo y control de caja (50/60): Cuadra la caja sin diferencias\n\n"
                        + "Conocimiento del negocio de divisas (30/40): Conoce el tipo de cambio");
        assertThat(pie(libro)).anyMatch(t -> t.startsWith("Filtro aplicado: Todos")
                && t.endsWith("· 2 candidatos"));
        assertThat(pie(libro)).anyMatch(t -> t.startsWith("«Nota Perfil Integral» es la nota"));
        assertThat(anchos(libro)).containsExactly(5, 34, 32, 30, 18, 15, 16, 14, 14, 17, 48, 90);
        assertThat(laCabeceraSigueFija(libro)).isTrue();
    }

    @Test
    @DisplayName("AC-05 · con prueba vigente y nadie en la etapa todavía, salen sus columnas vacías")
    void sinNadieEnLaEtapaSalenLasColumnas() throws Exception {
        long vacante = nuevaVacante("Administrador de sede · recién abierta");
        Version administrador = version("Prueba de Administrador", ADMINISTRADOR);
        ponerLaPrueba(vacante, administrador.id());
        long ana = candidato(vacante, "Ana", "Quispe");

        byte[] libro = excel(vacante, "PRUEBA_PUESTO", ana);

        assertThat(cabeceras(libro)).containsExactlyElementsOf(
                concat(FIJAS_ANTES, rotulos(ADMINISTRADOR), FIJAS_DESPUES));
        assertThat(fila(libro, 1).subList(7, 14)).allMatch(String::isEmpty);
        assertThat(fila(libro, 1).get(16)).isEmpty();
    }

    @Test
    @DisplayName("AC-06 · la vacante que rinde el cuestionario técnico no lleva columnas de criterio")
    void elCuestionarioNoLlevaColumnas() throws Exception {
        long vacante = nuevaVacante("Analista · cuestionario técnico");
        // Se deja puesta una versión, como las vacantes que tuvieron prueba antes de pasarse
        // al cuestionario: el instrumento manda, no la columna suelta.
        Version administrador = version("Prueba de Administrador", ADMINISTRADOR);
        ponerLaPrueba(vacante, administrador.id());
        jdbc.update("update vacante set instrumento_etapa_tecnica = 'CUESTIONARIO_TECNICO' "
                + "where id = ?", vacante);
        long ana = candidato(vacante, "Ana", "Quispe");

        byte[] libro = excel(vacante, "PRUEBA_PUESTO", ana);

        assertThat(cabeceras(libro)).containsExactlyElementsOf(concat(FIJAS_ANTES, FIJAS_DESPUES));
    }

    /*
     * AC-07. El fallo se provoca DENTRO de la lectura de la vigente, y no en el Excel, a
     * propósito: así se comprueba también que su transacción no arrastra a la de la descarga.
     * Si la compartieran, la excepción la marcaría para deshacer y la descarga acabaría en un
     * 500 aunque el Excel se hubiera tragado el fallo.
     */
    @Test
    @DisplayName("AC-07 · si la prueba vigente no se puede leer, el Excel se descarga sin columnas de criterio y el fallo queda en el registro")
    void siLaVigenteNoSePuedeLeerLaDescargaSigue(CapturedOutput salida) throws Exception {
        long vacante = nuevaVacante("Administrador de sede · rúbrica ilegible");
        Version demo = version("Prueba de ejecución · demo", DEMO);
        Version rota = version("Prueba de Administrador", ADMINISTRADOR);
        ponerLaPrueba(vacante, demo.id());
        long bruno = candidato(vacante, "Bruno", "Díaz");
        rindio(bruno, demo.id());
        nota(bruno, demo.criterio("CRITERIO"), "30", "Ordena por impacto y dice por qué");
        ponerLaPrueba(vacante, rota.id());
        doThrow(new DataAccessResourceFailureException("la base no contestó"))
                .when(criterios).findByVersionPlantillaPruebaIdOrderByOrden(rota.id());

        byte[] libro = excel(vacante, "PRUEBA_PUESTO", bruno);

        assertThat(cabeceras(libro)).containsExactlyElementsOf(concat(FIJAS_ANTES, FIJAS_DESPUES));
        assertThat(fila(libro, 1).get(9))
                .contains("Criterio de priorización (30/40): Ordena por impacto y dice por qué");
        assertThat(salida.getOut() + salida.getErr())
                .contains("No se pudo leer la prueba vigente de la vacante " + vacante);
    }

    // ============ La cabecera se lee entera ============

    @Test
    @DisplayName("AC-08 · AC-11 · AC-13 · el Excel de perfil integral no cambia salvo la altura de su cabecera")
    void elPerfilIntegralSoloCambiaLaAltura() throws Exception {
        Mezcla m = vacanteMezclada("Administrador de sede · perfil");

        byte[] libro = excel(m.vacante(), "PERFIL_INTEGRAL", m.ana(), m.bruno());

        // Sus columnas de criterio son los criterios globales del currículum, como siempre:
        // la prueba vigente no le toca nada.
        List<String> delCurriculum = jdbc.query("""
                select nombre, puntos from criterio
                 where etapa_codigo = 'PERFIL_INTEGRAL' and version_plantilla_prueba_id is null
                 order by orden""",
                (r, i) -> r.getBigDecimal("puntos") == null ? r.getString("nombre")
                        : r.getString("nombre") + " (pts /"
                        + r.getBigDecimal("puntos").stripTrailingZeros().toPlainString() + ")");
        assertThat(delCurriculum).isNotEmpty();
        assertThat(cabeceras(libro)).containsExactlyElementsOf(concat(
                List.of("#", "Candidato", "Correo", "CV", "Teléfono", "Nota Perfil Integral /100"),
                delCurriculum,
                List.of("Justificación resumida", "Justificación detallada")));
        assertThat(anchos(libro)).containsExactlyElementsOf(concat(
                List.of(5, 34, 32, 30, 18, 15), repetido(14, delCurriculum.size()), List.of(48, 90)));
        assertThat(laCabeceraSigueFija(libro)).isTrue();

        XSSFRow cabecera = laCabecera(libro);
        assertThat(cabecera.getCTRow().getCustomHeight()).isTrue();
        // «Nota Perfil Integral /100» en 15 de ancho ya son dos líneas, y los criterios del
        // currículum piden alguna más.
        assertThat(cabecera.getHeightInPoints()).isBetween(2 * LINEA, 8 * LINEA);
    }

    @Test
    @DisplayName("AC-11 · la altura sale de los rótulos: una rúbrica de nombres cortos da una cabecera más baja")
    void laAlturaSaleDeLosRotulos() throws Exception {
        long corta = nuevaVacante("Cajero · nombres cortos");
        Version breve = version("Prueba breve", new String[][]{
                {"CAJA", "Caja", "50"}, {"TRATO", "Trato", "50"}});
        ponerLaPrueba(corta, breve.id());
        long ana = candidato(corta, "Ana", "Quispe");

        long larga = nuevaVacante("Administrador de sede · nombres largos");
        Version administrador = version("Prueba de Administrador", ADMINISTRADOR);
        ponerLaPrueba(larga, administrador.id());
        long bruno = candidato(larga, "Bruno", "Díaz");

        float alturaCorta = laCabecera(excel(corta, "PRUEBA_PUESTO", ana)).getHeightInPoints();
        float alturaLarga = laCabecera(excel(larga, "PRUEBA_PUESTO", bruno)).getHeightInPoints();

        assertThat(alturaCorta).isLessThan(alturaLarga);
    }

    @Test
    @DisplayName("AC-12 · un rótulo de más de 8 líneas deja la cabecera en 8, con el texto entero en la celda")
    void elTopeDeOchoLineas() throws Exception {
        String desmesurado = "Capacidad demostrada para sostener la operación ".repeat(12).trim();
        long vacante = nuevaVacante("Administrador de sede · rótulo desmesurado");
        Version rara = version("Prueba con un nombre desmesurado", new String[][]{
                {"LARGO", desmesurado, "60"}, {"CAJA", "Manejo y control de caja", "40"}});
        ponerLaPrueba(vacante, rara.id());
        long ana = candidato(vacante, "Ana", "Quispe");

        byte[] libro = excel(vacante, "PRUEBA_PUESTO", ana);

        assertThat(laCabecera(libro).getHeightInPoints()).isEqualTo(8 * LINEA);
        assertThat(cabeceras(libro).get(7)).isEqualTo(desmesurado + " (pts /60)");
        assertThat(laCabeceraSigueFija(libro)).isTrue();
    }

    // ============ Descargar no escribe nada ============

    @Test
    @DisplayName("descargar los dos Excel no escribe nada: ni notas, ni intentos, ni estados, ni auditoría")
    void descargarNoEscribeNada() throws Exception {
        Mezcla m = vacanteMezclada("Administrador de sede · solo lectura");
        String antes = fotoDeLaBase(m.vacante());

        excel(m.vacante(), "PRUEBA_PUESTO", m.ana(), m.bruno(), m.carla());
        excel(m.vacante(), "PERFIL_INTEGRAL", m.ana(), m.bruno(), m.carla());
        excel(m.vacante(), "PRUEBA_PUESTO", m.bruno());

        assertThat(fotoDeLaBase(m.vacante())).isEqualTo(antes);
    }

    // ========================================================================
    // Andamio: la vacante 13 en pequeño
    // ========================================================================

    static final String[] NOTAS_DE_ANA = {"12", "18", "14", "11", "13", "8", "9"};

    record Mezcla(long vacante, long ana, long bruno, long carla) {}

    /**
     * Una vacante como la 13: tuvo puesta la demo, Bruno y Carla la abrieron —Bruno con sus
     * cuatro notas, una de ellas un 0—, y después pasó a la de Administrador, que Ana rindió
     * entera.
     */
    private Mezcla vacanteMezclada(String titulo) throws Exception {
        long vacante = nuevaVacante(titulo);
        Version demo = version("Prueba de ejecución · demo", DEMO);
        Version administrador = version("Prueba de Administrador", ADMINISTRADOR);

        ponerLaPrueba(vacante, demo.id());
        long bruno = candidato(vacante, "Bruno", "Díaz");
        rindio(bruno, demo.id());
        nota(bruno, demo.criterio("CRITERIO"), "30", "Ordena por impacto y dice por qué");
        nota(bruno, demo.criterio("DESCARTE"), "20", "Nombra lo que no atiende");
        nota(bruno, demo.criterio("CLARIDAD"), "0", "Se pierde en el detalle");
        nota(bruno, demo.criterio("REACCION"), "10", "Rehace el orden");
        long carla = candidato(vacante, "Carla", "Núñez");
        rindio(carla, demo.id());

        ponerLaPrueba(vacante, administrador.id());
        long ana = candidato(vacante, "Ana", "Quispe");
        rindio(ana, administrador.id());
        for (int i = 0; i < ADMINISTRADOR.length; i++) {
            nota(ana, administrador.criterio(ADMINISTRADOR[i][0]), NOTAS_DE_ANA[i],
                    "Lo sostiene con ejemplos " + i);
        }
        return new Mezcla(vacante, ana, bruno, carla);
    }

    record Version(long id, Map<String, Long> criterios) {
        long criterio(String codigo) {
            return criterios.get(codigo);
        }
    }

    /**
     * Una versión publicada con su rúbrica.
     *
     * <p>Los criterios se insertan al revés y con su {@code orden} bien puesto: así sus ids
     * van en sentido contrario a la rúbrica, y unas columnas que salieran por id y no por
     * orden se notarían.
     */
    private Version version(String plantilla, String[][] rubrica) {
        Long plantillaId = jdbc.queryForObject("insert into plantilla_prueba (organizacion_id, "
                + "puesto_id, nombre) values (1, ?, ?) returning id", Long.class, puestoId, plantilla);
        Long versionId = jdbc.queryForObject("""
                insert into version_plantilla_prueba (plantilla_prueba_id, version, enunciado,
                                                      modalidad, plazo_dias, estado, publicada_en)
                values (?, 1, 'Ordena la semana del equipo y justifica el orden',
                        'PLAZO_ABIERTO', 3, 'PUBLICADA', now()) returning id""",
                Long.class, plantillaId);
        Map<String, Long> ids = new LinkedHashMap<>();
        for (int i = rubrica.length - 1; i >= 0; i--) {
            ids.put(rubrica[i][0], jdbc.queryForObject("""
                    insert into criterio (codigo, nombre, etapa_codigo, version_plantilla_prueba_id,
                                          puntos, metodo_verificacion, orden)
                    values (?, ?, 'PRUEBA_PUESTO', ?, ?, 'AGENTE', ?) returning id""",
                    Long.class, rubrica[i][0], rubrica[i][1], versionId,
                    new BigDecimal(rubrica[i][2]), i + 1));
        }
        return new Version(versionId, ids);
    }

    /** Lo que la ficha de la vacante dice hoy: se escribe a mano, como llegó en producción. */
    private void ponerLaPrueba(long vacante, long version) {
        jdbc.update("update vacante set version_plantilla_prueba_id = ?, "
                + "instrumento_etapa_tecnica = 'PLANTILLA' where id = ?", version, vacante);
    }

    private long candidato(long vacante, String nombre, String apellidos) {
        Long persona = jdbc.queryForObject("insert into persona (nombre, apellidos) values (?, ?) "
                + "returning id", Long.class, nombre, apellidos);
        Long usuario = jdbc.queryForObject("insert into usuario (organizacion_id, persona_id, "
                + "es_equipo, es_activo) values (1, ?, false, true) returning id", Long.class, persona);
        return jdbc.queryForObject("insert into postulacion (organizacion_id, usuario_id, vacante_id, "
                + "estado_codigo) values (1, ?, ?, 'PRUEBA_POR_CONFIRMAR') returning id",
                Long.class, usuario, vacante);
    }

    /** Abrió su prueba con esa versión, y la entregó: queda atado a ella (RF-90). */
    private void rindio(long postulacion, long version) {
        jdbc.update("""
                insert into intento_prueba (postulacion_id, version_plantilla_prueba_id,
                                            iniciado_en, entregado_en)
                values (?, ?, now() - interval '2 days', now() - interval '1 day')""",
                postulacion, version);
    }

    private void nota(long postulacion, long criterio, String puntaje, String explicacion) {
        jdbc.update("insert into nota_criterio (postulacion_id, criterio_id, puntaje, explicacion, "
                + "origen) values (?, ?, ?, ?, 'AGENTE')",
                postulacion, criterio, new BigDecimal(puntaje), explicacion);
    }

    private long nuevaVacante(String titulo) throws Exception {
        long solicitud = Long.parseLong(leer(conToken(post("/api/v1/panel/solicitudes"), """
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
                 ]}""".formatted(areaId, puestoId), 201), "id"));
        conToken(post("/api/v1/panel/solicitudes/" + solicitud + "/aprobacion"),
                "{\"motivo\":\"Justificada: hay presupuesto\"}", 200);
        return Long.parseLong(leer(conToken(post("/api/v1/panel/vacantes"), """
                {"solicitudTalentoId": %d, "titulo": "%s", "descripcion": "Lleva la sede",
                 "tipoCierre": "PERMANENTE", "responsableUsuarioId": 1}"""
                .formatted(solicitud, titulo), 201), "id"));
    }

    /** Todo lo que una descarga podría tocar, en una línea que se compara antes y después. */
    private String fotoDeLaBase(long vacante) {
        return jdbc.queryForObject("""
                select (select count(*) from nota_etapa) || '/' ||
                       (select count(*) from nota_criterio) || '/' ||
                       (select coalesce(sum(puntaje), 0) from nota_criterio) || '/' ||
                       (select count(*) from intento_prueba) || '/' ||
                       (select count(*) from auditoria) || '/' ||
                       (select count(*) from transicion_estado) || '/' ||
                       (select count(*) from trabajo_ia) || '/' ||
                       (select count(*) from criterio) || '/' ||
                       (select string_agg(estado_codigo || movido_en, ',' order by id)
                          from postulacion where vacante_id = ?) || '/' ||
                       (select coalesce(version_plantilla_prueba_id, 0) || instrumento_etapa_tecnica
                          from vacante where id = ?)""", String.class, vacante, vacante);
    }

    // ---- HTTP ----

    private byte[] excel(long vacante, String etapa, long... postulaciones) throws Exception {
        String cuerpo = json.writeValueAsString(Map.of("etapa", etapa,
                "postulacionIds", Arrays.stream(postulaciones).boxed().toList(),
                "filtroDescrito", "Todos"));
        return mvc.perform(post("/api/v1/panel/vacantes/" + vacante + "/ranking/excel")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(cuerpo))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.CONTENT_TYPE,
                        org.hamcrest.Matchers.startsWith("application/vnd.openxmlformats")))
                .andReturn().getResponse().getContentAsByteArray();
    }

    private String conToken(
            org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder peticion,
            String cuerpo, int estado) throws Exception {
        return mvc.perform(peticion.header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON).content(cuerpo))
                .andExpect(status().is(estado))
                .andReturn().getResponse().getContentAsString();
    }

    private String leer(String cuerpoRespuesta, String campo) throws Exception {
        JsonNode nodo = json.readTree(cuerpoRespuesta).get(campo);
        assertThat(nodo).as("campo %s en %s", campo, cuerpoRespuesta).isNotNull();
        return nodo.asText();
    }

    private static List<String> nombresDeCriterio(JsonNode ranking, long postulacion) {
        for (JsonNode fila : ranking.get("filas")) {
            if (fila.get("postulacionId").asLong() == postulacion) {
                List<String> suyos = new ArrayList<>();
                fila.get("notasCriterio").forEach(n -> suyos.add(n.get("criterio").asText()));
                return suyos;
            }
        }
        throw new AssertionError("La postulación " + postulacion + " no está en el ranking");
    }

    // ---- Leer el libro descargado ----

    private static <T> T leyendo(byte[] contenido, Function<XSSFWorkbook, T> lector) {
        try (XSSFWorkbook libro = new XSSFWorkbook(new ByteArrayInputStream(contenido))) {
            assertThat(libro.getNumberOfSheets()).isEqualTo(1);
            assertThat(libro.getSheetAt(0).getSheetName()).isEqualTo("Datos");
            return lector.apply(libro);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static List<List<String>> celdas(byte[] contenido) {
        return leyendo(contenido, libro -> {
            DataFormatter formateador = new DataFormatter(Locale.US);
            Sheet hoja = libro.getSheet("Datos");
            List<List<String>> filas = new ArrayList<>();
            for (int i = 0; i <= hoja.getLastRowNum(); i++) {
                Row fila = hoja.getRow(i);
                List<String> suyas = new ArrayList<>();
                if (fila != null) {
                    for (int c = 0; c < fila.getLastCellNum(); c++) {
                        suyas.add(formateador.formatCellValue(fila.getCell(c)));
                    }
                }
                filas.add(suyas);
            }
            return filas;
        });
    }

    private static List<String> cabeceras(byte[] contenido) {
        return celdas(contenido).get(0);
    }

    private static List<String> fila(byte[] contenido, int cual) {
        return celdas(contenido).get(cual);
    }

    /** Las líneas sueltas del pie: la columna A por debajo de las filas de candidatos. */
    private static List<String> pie(byte[] contenido) {
        return celdas(contenido).stream()
                .filter(f -> f.size() == 1 && f.get(0).length() > 12)
                .map(f -> f.get(0))
                .toList();
    }

    private static XSSFRow laCabecera(byte[] contenido) {
        return leyendo(contenido, libro -> libro.getSheet("Datos").getRow(0));
    }

    private static boolean laCabeceraSigueFija(byte[] contenido) {
        return leyendo(contenido, libro -> {
            PaneInformation panel = libro.getSheet("Datos").getPaneInformation();
            return panel != null && panel.isFreezePane()
                    && panel.getHorizontalSplitPosition() == 1
                    && panel.getVerticalSplitPosition() == 0;
        });
    }

    private static List<Integer> anchos(byte[] contenido) {
        int columnas = cabeceras(contenido).size();
        return leyendo(contenido, libro -> {
            Sheet hoja = libro.getSheet("Datos");
            List<Integer> suyos = new ArrayList<>();
            for (int c = 0; c < columnas; c++) {
                suyos.add(hoja.getColumnWidth(c) / 256);
            }
            return suyos;
        });
    }

    // ---- Listas ----

    private static List<String> rotulos(String[][] rubrica) {
        return Stream.of(rubrica).map(c -> c[1] + " (pts /" + c[2] + ")").toList();
    }

    private static List<String> nombres(String[][] rubrica) {
        return Stream.of(rubrica).map(c -> c[1]).toList();
    }

    private static List<Integer> repetido(int valor, int veces) {
        return java.util.Collections.nCopies(veces, valor);
    }

    @SafeVarargs
    private static <T> List<T> concat(List<? extends T>... partes) {
        List<T> todo = new ArrayList<>();
        for (List<? extends T> parte : partes) {
            todo.addAll(parte);
        }
        return todo;
    }
}
