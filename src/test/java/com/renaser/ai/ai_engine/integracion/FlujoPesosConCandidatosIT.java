package com.renaser.ai.ai_engine.integracion;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.renaser.ai.ai_engine.integracion.soporte.ImagenesDeContenedores;
import com.renaser.ai.ai_engine.perfilintegral.service.PuenteCalificacionIa;
import com.renaser.ai.ai_engine.vacante.repository.VacanteRepository;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.rabbitmq.RabbitMQContainer;

import javax.sql.DataSource;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.Statement;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Cambiar la versión de pesos de una vacante con candidatos dentro, de punta a punta (V74,
 * specs/pesos-con-candidatos-y-fin-del-prestamo.md, parte A).
 *
 * <p>Las notas se siembran en la base, como las dejaría la IA: aquí no se llama a ningún
 * agente, y es justo lo que se comprueba —que el cambio rehace las cuentas con lo que ya hay—.
 * Las dos versiones de pesos son de esta prueba, para saber de antemano cada número:
 *
 * <ul>
 *   <li><b>Antes</b>: el currículum son «Resultados» (100) y «Gestión de personas» no pesa
 *       (0); la simulación vale 15 en ejecución y 15 en calidad.</li>
 *   <li><b>Después</b>: «Resultados» 50 y «Gestión de personas» 50; la simulación vale 20 en
 *       ejecución y 10 en calidad. La prueba del puesto pasa de 30 a 35.</li>
 * </ul>
 *
 * <p>Con eso, quien tiene un 100 en personas y un 40 en resultados pasa de 40 a 70 y adelanta
 * en el ranking a quien tenía 60 y 20, que baja a 40.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@DisplayName("Cambiar los pesos con candidatos dentro: todos a la vez, sin IA y sin mover a nadie")
class FlujoPesosConCandidatosIT {

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
    @Autowired DataSource fuente;
    @Autowired PlatformTransactionManager transacciones;
    @Autowired PuenteCalificacionIa puente;
    @Autowired VacanteRepository vacantes;

    final ObjectMapper json = new ObjectMapper();

    static String token;
    static long vacante;
    static long antes;
    static long despues;
    /** 100 en personas y 40 en resultados: 40 antes, 70 después. */
    static long ines;
    /** 20 en personas y 60 en resultados: 60 antes, 40 después. */
    static long bruno;
    /** 50 y 50, decidida, con prueba del puesto y simulación. */
    static long carla;
    /** Postuló y no tiene ninguna nota todavía. */
    static long dario;
    /** La simulación de Carla tal como la puso el facilitador, con los pesos de antes. */
    static BigDecimal simulacionDeCarla;

    @Test
    @Order(1)
    @DisplayName("la vacante con tres calificados, una decidida, y alguien sin nota")
    void laVacanteYSusCandidatos() throws Exception {
        token = leer(mvc.perform(post("/api/v1/panel/auth/dev-login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"usuarioRenaserOsId\":\"dev-pesos\"}"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(), "token");
        long area = jdbc.queryForObject("INSERT INTO area (organizacion_id, nombre, es_activa) "
                + "VALUES (1, 'Operaciones', true) RETURNING id", Long.class);
        long puesto = Long.parseLong(leer(con(post("/api/v1/panel/puestos"), """
                {"nombre": "Analista de operaciones", "nivelPuestoCodigo": "EJECUCION",
                 "familiaCodigo": "OPERACIONES"}""", 201), "id"));
        long solicitud = Long.parseLong(leer(con(post("/api/v1/panel/solicitudes"), """
                {"areaId": %d, "puestoId": %d, "urgencia": "NORMAL",
                 "nivelPuestoCodigo": "EJECUCION", "familiaCodigo": "OPERACIONES",
                 "resultadoPrincipal": "Sostener la operación", "motivo": "No llegamos",
                 "consecuenciaNoContratar": "Se retrasa", "analisisCapacidad": "No alcanza",
                 "responsableUsuarioId": 1,
                 "resultadosEsperados": [
                   {"descripcion": "Cubrir", "indicador": "listo"},
                   {"descripcion": "Formar", "indicador": "listo"},
                   {"descripcion": "Entregar", "indicador": "listo"}
                 ]}""".formatted(area, puesto), 201), "id"));
        con(post("/api/v1/panel/solicitudes/" + solicitud + "/aprobacion"),
                "{\"motivo\":\"Hay presupuesto\"}", 200);
        vacante = Long.parseLong(leer(con(post("/api/v1/panel/vacantes"), """
                {"solicitudTalentoId": %d, "titulo": "Analista de operaciones",
                 "descripcion": "Turno completo", "tipoCierre": "PERMANENTE",
                 "responsableUsuarioId": 1}""".formatted(solicitud), 201), "id"));

        antes = version("Pesos de antes (V74)", "30", "15",
                new String[][] {{"CV_RESULTADOS", "100"}, {"CV_PERSONAS", "0"},
                        {"SIM_EJECUCION", "15"}, {"SIM_CALIDAD", "15"}});
        despues = version("Pesos de después (V74)", "35", "10",
                new String[][] {{"CV_RESULTADOS", "50"}, {"CV_PERSONAS", "50"},
                        {"SIM_EJECUCION", "20"}, {"SIM_CALIDAD", "10"}});
        // Publicada y con candidatos dentro: lo que hasta la V74 contestaba 409
        jdbc.update("update vacante set estado = 'PUBLICADA', publicada_en = now(), "
                + "version_pesos_id = ? where id = ?", antes, vacante);

        ines = candidato("Inés", "PERFIL_POR_CONFIRMAR", "40", "100", "40");
        bruno = candidato("Bruno", "PERFIL_POR_CONFIRMAR", "60", "20", "60");
        carla = candidato("Carla", "CONTRATADO", "50", "50", "50");
        dario = candidato("Darío", "PERFIL_TURNO_CANDIDATO", null, null, null);

        // Carla rindió la prueba del puesto (72, con sus puntos) y la simulación: ejecución
        // completa, calidad en cero y el resto a la mitad
        notaDeEtapa(carla, "PRUEBA_PUESTO", "72");
        for (var criterio : jdbc.queryForList("""
                select id, codigo from criterio
                 where etapa_codigo = 'SIMULACION' and version_plantilla_prueba_id is null""")) {
            String codigo = (String) criterio.get("codigo");
            String puntaje = "SIM_EJECUCION".equals(codigo) ? "15"
                    : "SIM_CALIDAD".equals(codigo) ? "0" : "5";
            notaDeCriterio(carla, ((Number) criterio.get("id")).longValue(), puntaje);
        }
        simulacionDeCarla = sumaDeLaSimulacion(carla);
        notaDeEtapa(carla, "SIMULACION", simulacionDeCarla.toPlainString());
        // Y ya se decidió con la vara de entonces
        jdbc.update("insert into decision (postulacion_id, semaforo, nota_global, version_pesos_id, "
                + "decidida_por_usuario_id, motivo, decidida_en) values (?, 'VERDE', 55.00, ?, 1, "
                + "'Encaja', now())", carla, antes);
    }

    @Test
    @Order(2)
    @DisplayName("la confirmación cuenta a los tres con nota, no a quien aún no tiene, y compara los pesos")
    void laConfirmacion() throws Exception {
        JsonNode previsto = json.readTree(con(get("/api/v1/panel/vacantes/" + vacante
                + "/version-pesos/cambio?versionPesosId=" + despues), null, 200));

        assertThat(previsto.get("personasConNota").asInt()).isEqualTo(3);
        assertThat(previsto.get("cambia").asBoolean()).isTrue();
        assertThat(previsto.get("etiquetaAnterior").asText()).isEqualTo("Pesos de antes (V74)");
        List<String> etapas = new ArrayList<>();
        previsto.get("etapas").forEach(e -> etapas.add(e.get("codigo").asText()
                + ":" + e.get("antes").decimalValue().intValue()
                + ">" + e.get("despues").decimalValue().intValue()));
        assertThat(etapas).containsExactly("PERFIL_INTEGRAL:40>40", "PRUEBA_PUESTO:30>35",
                "SIMULACION:15>15", "VALIDACION:15>10");
        // Y nada se escribió por preguntar
        assertThat(versionDeLaVacante()).isEqualTo(antes);
    }

    @Test
    @Order(3)
    @DisplayName("si una nota no se puede recalcular a mitad, no se guarda nada: la vacante y "
            + "todas las notas como estaban (AC-07)")
    void unFalloAMitadNoGuardaNada() throws Exception {
        // Darío tiene una nota de simulación sin su rúbrica: no se puede volver a sumar
        notaDeEtapa(dario, "SIMULACION", "30");
        String foto = fotoDeLasNotas();

        String error = con(post("/api/v1/panel/vacantes/" + vacante + "/version-pesos"),
                "{\"versionPesosId\": %d}".formatted(despues), 409);

        assertThat(error).contains("SIMULACION");
        assertThat(versionDeLaVacante()).isEqualTo(antes);
        assertThat(fotoDeLasNotas()).isEqualTo(foto);
        assertThat(jdbc.queryForObject("select count(*) from auditoria where accion = "
                + "'asignar_version_pesos' and entidad_id = ?", Long.class, vacante)).isZero();
        jdbc.update("delete from nota_etapa where postulacion_id = ?", dario);
    }

    @Test
    @Order(4)
    @DisplayName("al cambiar se recalcula a los tres sin IA y sin moverlos; la prueba vale lo "
            + "mismo; la decisión no se toca; y queda registrado (AC-01..AC-04, AC-08)")
    void alCambiarSeRecalculaATodos() throws Exception {
        long trabajosIa = contar("select count(*) from trabajo_ia");
        long transiciones = contar("select count(*) from transicion_estado");
        String estados = jdbc.queryForObject("select string_agg(id || estado_codigo, ',' order by id) "
                + "from postulacion where vacante_id = ?", String.class, vacante);
        BigDecimal simulacionAntes = nota(carla, "SIMULACION");

        JsonNode hecho = json.readTree(con(post("/api/v1/panel/vacantes/" + vacante + "/version-pesos"),
                "{\"versionPesosId\": %d}".formatted(despues), 200));

        assertThat(hecho.get("personasRecalculadas").asInt()).isEqualTo(3);
        assertThat(versionDeLaVacante()).isEqualTo(despues);
        // AC-02: la nota de 0 a 100 que la IA puso a «Gestión de personas» ahora pesa
        assertThat(nota(ines, "PERFIL_INTEGRAL")).isEqualByComparingTo("70");
        assertThat(nota(bruno, "PERFIL_INTEGRAL")).isEqualByComparingTo("40");
        assertThat(nota(carla, "PERFIL_INTEGRAL")).isEqualByComparingTo("50");
        assertThat(grupo(ines)).isEqualTo("POTENCIAL_CON_RIESGO");
        assertThat(grupo(bruno)).isEqualTo("NO_PRIORIZADO");
        // La prueba técnica lleva sus puntos: vale lo mismo
        assertThat(nota(carla, "PRUEBA_PUESTO")).isEqualByComparingTo("72");
        // AC-03: la simulación se repondera con los pesos nuevos (ejecución 15→20 completa,
        // calidad 15→10 en cero): cinco puntos más
        assertThat(nota(carla, "SIMULACION")).isEqualByComparingTo(simulacionAntes.add(BigDecimal.valueOf(5)));
        // Todo lo recalculado dice la versión nueva, también el retrato
        assertThat(contar("select count(*) from nota_etapa ne join postulacion p on p.id = "
                + "ne.postulacion_id where p.vacante_id = " + vacante + " and ne.version_pesos_id <> "
                + despues)).isZero();
        assertThat(contar("select count(*) from perfil_talento pt join postulacion p on p.id = "
                + "pt.postulacion_id where p.vacante_id = " + vacante + " and pt.version_pesos_id <> "
                + despues)).isZero();
        // AC-04: la decisión conserva la nota y la versión con que se tomó
        assertThat(jdbc.queryForObject("select nota_global from decision where postulacion_id = ?",
                BigDecimal.class, carla)).isEqualByComparingTo("55");
        assertThat(jdbc.queryForObject("select version_pesos_id from decision where postulacion_id = ?",
                Long.class, carla)).isEqualTo(antes);
        // Sin IA, sin máquina de estados, nadie se mueve
        assertThat(contar("select count(*) from trabajo_ia")).isEqualTo(trabajosIa);
        assertThat(contar("select count(*) from transicion_estado")).isEqualTo(transiciones);
        assertThat(jdbc.queryForObject("select string_agg(id || estado_codigo, ',' order by id) "
                + "from postulacion where vacante_id = ?", String.class, vacante)).isEqualTo(estados);
        // AC-08: quién, cuándo, las dos versiones y a cuántos
        var registro = jdbc.queryForMap("select usuario_id, creado_en, valor_anterior::text as antes, "
                + "valor_nuevo::text as despues from auditoria where accion = 'asignar_version_pesos' "
                + "and entidad_id = ?", vacante);
        assertThat(registro.get("usuario_id")).isNotNull();
        assertThat(registro.get("creado_en")).isNotNull();
        assertThat((String) registro.get("antes")).contains(String.valueOf(antes));
        assertThat((String) registro.get("despues")).contains(String.valueOf(despues))
                .contains("\"personasRecalculadas\": 3");
    }

    @Test
    @Order(5)
    @DisplayName("el ranking enseña las notas nuevas y el orden nuevo (AC-09)")
    void elRankingEnsenaLoNuevo() throws Exception {
        JsonNode ranking = json.readTree(con(get("/api/v1/panel/vacantes/" + vacante + "/ranking"),
                null, 200));
        List<Long> orden = new ArrayList<>();
        for (JsonNode fila : ranking.get("filas")) {
            long id = fila.get("postulacionId").asLong();
            if (id == ines || id == bruno) {
                orden.add(id);
            }
            if (id == ines) {
                assertThat(fila.get("notaEtapa").decimalValue()).isEqualByComparingTo("70");
                assertThat(fila.get("notaCurriculum").decimalValue()).isEqualByComparingTo("70");
            }
        }
        // Antes Bruno (60) iba delante de Inés (40); ahora Inés (70) delante de Bruno (40)
        assertThat(orden).containsExactly(ines, bruno);
    }

    @Test
    @Order(6)
    @DisplayName("QA-01 · ir a otros pesos y volver deja la simulación como la puso el facilitador, "
            + "aunque se redondee y un criterio pase por peso 0 (AC-03)")
    void idaYVueltaDejaLaSimulacionComoEstaba() throws Exception {
        // Carla vuelve a la vara con que la calificaron: sus notas y su suma, tal cual
        cambiarA(antes);
        assertThat(notaDeSimulacion(carla, "SIM_EJECUCION")).isEqualByComparingTo("15");
        assertThat(notaDeSimulacion(carla, "SIM_CALIDAD")).isEqualByComparingTo("0");
        assertThat(notaDeSimulacion(carla, "SIM_APRENDIZAJE")).isEqualByComparingTo("5");
        assertThat(nota(carla, "SIMULACION")).isEqualByComparingTo(simulacionDeCarla);

        // Elena, calificada con los pesos de antes: calidad 7 de 15 y aprendizaje 6 de 10
        long elena = candidato("Elena", "PERFIL_POR_CONFIRMAR", "50", "50", "50");
        for (var criterio : jdbc.queryForList("""
                select id, codigo from criterio
                 where etapa_codigo = 'SIMULACION' and version_plantilla_prueba_id is null""")) {
            String codigo = (String) criterio.get("codigo");
            String puntaje = switch (codigo) {
                case "SIM_EJECUCION" -> "15";
                case "SIM_CALIDAD" -> "7";
                case "SIM_APRENDIZAJE" -> "6";
                default -> "5";
            };
            notaDeCriterio(elena, ((Number) criterio.get("id")).longValue(), puntaje);
        }
        BigDecimal puesta = sumaDeLaSimulacion(elena);
        notaDeEtapa(elena, "SIMULACION", puesta.toPlainString());
        long sinAprendizaje = version("Pesos sin aprendizaje (V75)", "35", "10",
                new String[][] {{"CV_RESULTADOS", "50"}, {"CV_PERSONAS", "50"},
                        {"SIM_EJECUCION", "20"}, {"SIM_CALIDAD", "10"}, {"SIM_APRENDIZAJE", "0"}});

        // Ida: 7 de 15 son 4,67 de 10, y el aprendizaje no pesa
        cambiarA(sinAprendizaje);
        assertThat(notaDeSimulacion(elena, "SIM_EJECUCION")).isEqualByComparingTo("20");
        assertThat(notaDeSimulacion(elena, "SIM_CALIDAD")).isEqualByComparingTo("4.67");
        assertThat(notaDeSimulacion(elena, "SIM_APRENDIZAJE")).isEqualByComparingTo("0");
        assertThat(nota(elena, "SIMULACION")).isEqualByComparingTo(sumaDeLaSimulacion(elena));

        // Vuelta: ni un 7,01 ni el aprendizaje perdido
        cambiarA(antes);
        assertThat(notaDeSimulacion(elena, "SIM_EJECUCION")).isEqualByComparingTo("15");
        assertThat(notaDeSimulacion(elena, "SIM_CALIDAD")).isEqualByComparingTo("7");
        assertThat(notaDeSimulacion(elena, "SIM_APRENDIZAJE")).isEqualByComparingTo("6");
        assertThat(nota(elena, "SIMULACION")).isEqualByComparingTo(puesta);
        // Lo que se puso no se movió en ningún momento
        assertThat(jdbc.queryForObject("""
                select nc.puntaje_original || '/' || nc.maximo_original
                  from nota_criterio nc join criterio c on c.id = nc.criterio_id
                 where nc.postulacion_id = ? and c.codigo = 'SIM_CALIDAD'
                   and c.version_plantilla_prueba_id is null""", String.class, elena))
                .isEqualTo("7.00/15.00");

        // Y de nuevo a la de después, donde seguía la vacante: lo mismo que la primera vez
        cambiarA(despues);
        assertThat(notaDeSimulacion(elena, "SIM_CALIDAD")).isEqualByComparingTo("4.67");
        assertThat(notaDeSimulacion(elena, "SIM_APRENDIZAJE")).isEqualByComparingTo("6");
        assertThat(nota(carla, "SIMULACION")).isEqualByComparingTo(simulacionDeCarla.add(BigDecimal.valueOf(5)));
    }

    @Test
    @Order(7)
    @DisplayName("QA-02 · una nota que se escribe con el cambio de pesos a mitad espera a que "
            + "confirme y sale con la versión nueva, aunque la vacante ya se hubiera leído (AC-05)")
    void unaNotaAMitadDelCambioSaleConLaVersionNueva() throws Exception {
        assertThat(versionDeLaVacante()).isEqualTo(despues);
        assertThat(nota(ines, "PERFIL_INTEGRAL")).isEqualByComparingTo("70");

        // El cambio a mitad, como lo deja ServicioPesosDeLaVacanteImpl#cambiar mientras
        // recalcula: la vacante bloqueada y ya en la de antes, y la nota de Inés recalculada
        // con ella (40), sin confirmar todavía
        CountDownLatch aMitad = new CountDownLatch(1);
        ExecutorService otraSesion = Executors.newSingleThreadExecutor();
        Future<?> cambio = otraSesion.submit(() -> {
            try (Connection c = fuente.getConnection()) {
                c.setAutoCommit(false);
                try (Statement s = c.createStatement()) {
                    s.execute("select id from vacante where id = " + vacante + " for update");
                    s.executeUpdate("update vacante set version_pesos_id = " + antes
                            + " where id = " + vacante);
                    s.executeUpdate("update nota_etapa set puntaje = 40.00, version_pesos_id = "
                            + antes + " where postulacion_id = " + ines
                            + " and etapa_codigo = 'PERFIL_INTEGRAL'");
                }
                aMitad.countDown();
                Thread.sleep(1_500);
                c.commit();
            }
            return null;
        });
        try {
            assertThat(aMitad.await(10, TimeUnit.SECONDS)).isTrue();
            long desde = System.nanoTime();
            // En ese hueco llega una nota de Inés —un ajuste a mano, la IA que estaba en
            // curso—, en una transacción que ya había leído la vacante, como el guardián del
            // panel antes de un ajuste
            new TransactionTemplate(transacciones).executeWithoutResult(t -> {
                assertThat(vacantes.findById(vacante).orElseThrow().getVersionPesosId())
                        .isEqualTo(despues);
                puente.recalcularSinMover(ines);
            });
            cambio.get(10, TimeUnit.SECONDS);
            // Esperó al cambio en vez de calcular con lo que había leído
            assertThat(Duration.ofNanos(System.nanoTime() - desde)).isGreaterThan(Duration.ofMillis(1_000));
        } finally {
            otraSesion.shutdownNow();
        }

        assertThat(versionDeLaVacante()).isEqualTo(antes);
        assertThat(jdbc.queryForObject("select version_pesos_id from nota_etapa where postulacion_id = ? "
                + "and etapa_codigo = 'PERFIL_INTEGRAL'", Long.class, ines)).isEqualTo(antes);
        assertThat(nota(ines, "PERFIL_INTEGRAL")).isEqualByComparingTo("40");
        assertThat(grupo(ines)).isEqualTo("NO_PRIORIZADO");

        // La vacante vuelve a la de después por la puerta de siempre
        cambiarA(despues);
        assertThat(nota(ines, "PERFIL_INTEGRAL")).isEqualByComparingTo("70");
        assertThat(grupo(ines)).isEqualTo("POTENCIAL_CON_RIESGO");
    }

    @Test
    @Order(8)
    @DisplayName("elegir la misma no hace nada; una vacante cerrada contesta 409 (AC-06)")
    void laMismaYLaCerrada() throws Exception {
        long registros = contar("select count(*) from auditoria where accion = 'asignar_version_pesos'");
        JsonNode igual = json.readTree(con(post("/api/v1/panel/vacantes/" + vacante + "/version-pesos"),
                "{\"versionPesosId\": %d}".formatted(despues), 200));
        assertThat(igual.get("personasRecalculadas").asInt()).isZero();
        assertThat(contar("select count(*) from auditoria where accion = 'asignar_version_pesos'"))
                .isEqualTo(registros);

        jdbc.update("update vacante set estado = 'CERRADA', cerrada_en = now() where id = ?", vacante);
        con(post("/api/v1/panel/vacantes/" + vacante + "/version-pesos"),
                "{\"versionPesosId\": %d}".formatted(antes), 409);
        assertThat(versionDeLaVacante()).isEqualTo(despues);
    }

    // ---- La siembra ----

    /** Una versión publicada con sus cuatro repartos, para el nivel de Ejecución. */
    private long version(String etiqueta, String prueba, String validacion, String[][] criterios) {
        long id = jdbc.queryForObject("insert into version_pesos (organizacion_id, etiqueta, estado, "
                + "publicada_en) values (1, ?, 'PUBLICADA', now() - interval '1 year') returning id",
                Long.class, etiqueta);
        jdbc.update("insert into peso_etapa (version_pesos_id, etapa_codigo, peso) values "
                + "(?, 'PERFIL_INTEGRAL', 40), (?, 'PRUEBA_PUESTO', " + prueba + "), "
                + "(?, 'SIMULACION', 15), (?, 'VALIDACION', " + validacion + ")", id, id, id, id);
        jdbc.update("insert into peso_componente_perfil (version_pesos_id, componente, peso) "
                + "values (?, 'CV', 100), (?, 'EVALUACION', 0)", id, id);
        // Los criterios de la simulación que no se nombran valen 10 en todas las versiones
        String nombrados = Arrays.stream(criterios).map(c -> "'" + c[0] + "'")
                .collect(Collectors.joining(", "));
        jdbc.update("""
                insert into peso_criterio (version_pesos_id, nivel_puesto_codigo, criterio_id, peso)
                select ?, 'EJECUCION', c.id, 10 from criterio c
                 where c.etapa_codigo = 'SIMULACION' and c.version_plantilla_prueba_id is null
                   and c.codigo not in (%s)""".formatted(nombrados), id);
        for (String[] criterio : criterios) {
            jdbc.update("""
                    insert into peso_criterio (version_pesos_id, nivel_puesto_codigo, criterio_id, peso)
                    select ?, 'EJECUCION', c.id, ? from criterio c
                     where c.codigo = ? and c.version_plantilla_prueba_id is null""",
                    id, new BigDecimal(criterio[1]), criterio[0]);
        }
        return id;
    }

    /**
     * Una postulación con su retrato, sus dos notas del currículum (de 0 a 100, como las deja
     * la IA) y la nota del Perfil Integral que salió con los pesos de antes. Sin nota, nada.
     */
    private long candidato(String nombre, String estado, String resultados, String personas,
                           String perfilIntegral) {
        long persona = jdbc.queryForObject("insert into persona (nombre, apellidos) values (?, 'Prueba') "
                + "returning id", Long.class, nombre);
        long usuario = jdbc.queryForObject("insert into usuario (organizacion_id, persona_id, es_equipo, "
                + "es_activo) values (1, ?, false, true) returning id", Long.class, persona);
        long id = jdbc.queryForObject("insert into postulacion (organizacion_id, usuario_id, vacante_id, "
                + "estado_codigo, grupo_prioridad) values (1, ?, ?, ?, ?) returning id", Long.class,
                usuario, vacante, estado, perfilIntegral == null ? null : "NO_PRIORIZADO");
        if (perfilIntegral == null) {
            return id;
        }
        notaDeCriterio(id, criterio("CV_RESULTADOS"), resultados);
        notaDeCriterio(id, criterio("CV_PERSONAS"), personas);
        jdbc.update("insert into perfil_talento (postulacion_id, adecuacion, potencial, alto_rendimiento, "
                + "confianza_evidencia, resumen, version_pesos_id) values (?, 50, 10, 50, 70, "
                + "'Retrato de prueba', ?)", id, antes);
        notaDeEtapa(id, "PERFIL_INTEGRAL", perfilIntegral);
        return id;
    }

    private long criterio(String codigo) {
        return jdbc.queryForObject("select id from criterio where codigo = ? and "
                + "version_plantilla_prueba_id is null", Long.class, codigo);
    }

    private void notaDeCriterio(long postulacion, long criterio, String puntaje) {
        jdbc.update("insert into nota_criterio (postulacion_id, criterio_id, puntaje, explicacion, origen) "
                + "values (?, ?, ?, 'Puesta antes del cambio', 'AGENTE')",
                postulacion, criterio, new BigDecimal(puntaje));
    }

    private void notaDeEtapa(long postulacion, String etapa, String puntaje) {
        jdbc.update("insert into nota_etapa (postulacion_id, etapa_codigo, puntaje, version_pesos_id, "
                + "calculada_en) values (?, ?, ?, ?, now())", postulacion, etapa,
                new BigDecimal(puntaje), antes);
    }

    private BigDecimal sumaDeLaSimulacion(long postulacion) {
        return jdbc.queryForObject("""
                select sum(nc.puntaje) from nota_criterio nc join criterio c on c.id = nc.criterio_id
                 where nc.postulacion_id = ? and c.etapa_codigo = 'SIMULACION'""",
                BigDecimal.class, postulacion);
    }

    private void cambiarA(long version) throws Exception {
        con(post("/api/v1/panel/vacantes/" + vacante + "/version-pesos"),
                "{\"versionPesosId\": %d}".formatted(version), 200);
    }

    // ---- Lo que se lee ----

    private BigDecimal notaDeSimulacion(long postulacion, String codigo) {
        return jdbc.queryForObject("""
                select nc.puntaje from nota_criterio nc join criterio c on c.id = nc.criterio_id
                 where nc.postulacion_id = ? and c.codigo = ? and c.version_plantilla_prueba_id is null""",
                BigDecimal.class, postulacion, codigo);
    }

    private BigDecimal nota(long postulacion, String etapa) {
        return jdbc.queryForObject("select puntaje from nota_etapa where postulacion_id = ? and "
                + "etapa_codigo = ?", BigDecimal.class, postulacion, etapa);
    }

    private String grupo(long postulacion) {
        return jdbc.queryForObject("select grupo_prioridad from postulacion where id = ?",
                String.class, postulacion);
    }

    private long versionDeLaVacante() {
        return jdbc.queryForObject("select version_pesos_id from vacante where id = ?", Long.class, vacante);
    }

    /** Todas las notas de la vacante, con su versión, en una línea. */
    private String fotoDeLasNotas() {
        return jdbc.queryForObject("""
                select coalesce(string_agg(x, ',' order by x), '') from (
                  select ne.postulacion_id || ne.etapa_codigo || ne.puntaje || '@' || ne.version_pesos_id as x
                    from nota_etapa ne join postulacion p on p.id = ne.postulacion_id
                   where p.vacante_id = ?
                  union all
                  select nc.postulacion_id || '#' || nc.criterio_id || '=' || nc.puntaje
                    from nota_criterio nc join postulacion p on p.id = nc.postulacion_id
                   where p.vacante_id = ?
                  union all
                  select p.id || 'grupo' || coalesce(p.grupo_prioridad, '-')
                    from postulacion p where p.vacante_id = ?) t""",
                String.class, vacante, vacante, vacante);
    }

    private long contar(String sql) {
        return jdbc.queryForObject(sql, Long.class);
    }

    // ---- HTTP ----

    private String con(MockHttpServletRequestBuilder peticion, String cuerpo, int estado) throws Exception {
        peticion.header(HttpHeaders.AUTHORIZATION, "Bearer " + token);
        if (cuerpo != null) {
            peticion.contentType(MediaType.APPLICATION_JSON).content(cuerpo);
        }
        return mvc.perform(peticion)
                .andExpect(status().is(estado))
                .andReturn().getResponse().getContentAsString();
    }

    private String leer(String cuerpoRespuesta, String campo) throws Exception {
        JsonNode nodo = json.readTree(cuerpoRespuesta).get(campo);
        assertThat(nodo).as("campo %s en %s", campo, cuerpoRespuesta).isNotNull();
        return nodo.asText();
    }
}
