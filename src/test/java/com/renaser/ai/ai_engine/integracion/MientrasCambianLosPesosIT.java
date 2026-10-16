package com.renaser.ai.ai_engine.integracion;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.renaser.ai.ai_engine.integracion.soporte.ImagenesDeContenedores;
import com.renaser.ai.ai_engine.perfilintegral.service.PuenteCalificacionIa;
import com.renaser.ai.ai_engine.postulacion.repository.PostulacionRepository;

import org.junit.jupiter.api.AfterEach;
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
import org.springframework.mock.web.MockMultipartFile;
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
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Lo que el equipo y los candidatos hacen mientras un cambio de pesos de verdad está a mitad
 * (V74, specs/pesos-con-candidatos-y-fin-del-prestamo.md, AC-01, AC-04 y AC-05; hallazgos QA-04
 * y QA-05).
 *
 * <p>El cambio es el de siempre, {@code POST /vacantes/{id}/version-pesos}, y no una imitación:
 * bloquea la vacante, lee todas sus postulaciones al empezar, las recorre de la más reciente a
 * la más antigua y le rehace a cada una la nota del Perfil Integral y el grupo, en una sola
 * transacción. Para que algo llegue con él a mitad, otra sesión retiene una fila —la vacante, o
 * la postulación a la que el cambio va a llegar— hasta que lo que se prueba está en su sitio.
 *
 * <p>Todas las personas salen con el mismo Perfil Integral sembrado, como lo dejaría la IA: 40
 * en resultados y 100 en gestión de personas. Con los pesos de antes (solo cuentan los
 * resultados) eso es un 40 y «no priorizado»; con los de después (mitad y mitad), un 70 y
 * «potencial con riesgo». El grupo cambia, y por eso el cambio les reescribe la fila.
 *
 * <p>La causa de los dos hallazgos era la misma: la postulación se guardaba entera, con lo que
 * cada uno había leído antes. La entrega que esperaba al cambio le devolvía a la persona el
 * grupo de la versión vieja (QA-04); el cambio devolvía a su estado de antes a quien el equipo
 * movía o descartaba mientras corría (QA-05).
 */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@DisplayName("QA-04 y QA-05 · con un cambio de pesos real a mitad, nadie pisa lo que no cambió: "
        + "ni el grupo nuevo ni el estado en que el equipo dejó a cada persona")
class MientrasCambianLosPesosIT {

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

    private static final String ANTES = "NO_PRIORIZADO";
    private static final String DESPUES = "POTENCIAL_CON_RIESGO";
    /** Lo más que se espera a que una sesión llegue a su cerrojo, o a que algo termine. */
    private static final long ESPERA_S = 30;

    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired DataSource fuente;
    @Autowired PlatformTransactionManager transacciones;
    @Autowired PuenteCalificacionIa puente;
    @Autowired PostulacionRepository postulaciones;

    final ObjectMapper json = new ObjectMapper();

    static String token;
    static long areaId;
    static long puestoId;
    static long antes;
    static long despues;
    static final Map<String, Long> pregunta = new HashMap<>();
    static final Map<String, Long> opcion = new HashMap<>();
    static final Map<String, Long> criterioDePersona = new HashMap<>();
    static final Map<String, Long> postulacion = new HashMap<>();
    static final Map<String, String> codigo = new HashMap<>();
    static final Map<String, String> tokenDe = new HashMap<>();

    @Test
    @Order(1)
    @DisplayName("las dos versiones de pesos y el puesto")
    void lasVersionesYElPuesto() throws Exception {
        token = leer(mvc.perform(post("/api/v1/panel/auth/dev-login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"usuarioRenaserOsId\":\"dev-qa04-qa05\"}"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(), "token");
        areaId = jdbc.queryForObject("insert into area (organizacion_id, nombre, es_activa) "
                + "values (1, 'Tesorería', true) returning id", Long.class);
        puestoId = Long.parseLong(leer(con(post("/api/v1/panel/puestos"), """
                {"codigo": "CAJERA_QA0405", "nombre": "Cajera", "nivelPuestoCodigo": "EJECUCION",
                 "familiaCodigo": "OPERACIONES"}""", 201), "id"));
        antes = version("QA-04/05 · solo resultados", "100", "0");
        despues = version("QA-04/05 · resultados y personas", "50", "50");
    }

    @Test
    @Order(2)
    @DisplayName("QA-04 · la entrega que espera a un cambio de pesos real no le devuelve a la "
            + "persona el grupo de antes (AC-01, AC-05)")
    void laEntregaQueEsperaAlCambio() throws Exception {
        long vacante = vacanteConCerrada("Cajera · entrega a mitad del cambio", "entrega");
        candidataCalificada("ana", vacante);
        aLaPrueba("ana");
        responder("ana", "entrega", "{\"opcionId\":%d}".formatted(opcion.get("entrega:Diez soles")));
        assertThat(grupo("ana")).isEqualTo(ANTES);

        // La entrega llega con el cambio en cola delante de ella: ya leyó la postulación, con
        // el grupo de antes, y espera a la vacante detrás del cambio
        try (Retenida vacanteRetenida = new Retenida(
                "select id from vacante where id = " + vacante + " for no key update")) {
            Future<String> cambio = enParalelo(() -> cambiarA(vacante, despues));
            hastaQueEsperen(1);
            Future<String> entrega = enParalelo(() -> portal(
                    post("/api/v1/portal/prueba/" + codigo.get("ana") + "/entrega"), "ana", null));
            hastaQueEsperen(2);
            vacanteRetenida.soltar();
            assertThat(json.readTree(cambio.get(ESPERA_S, TimeUnit.SECONDS))
                    .get("personasRecalculadas").asInt()).isEqualTo(1);
            entrega.get(ESPERA_S, TimeUnit.SECONDS);
        }

        assertThat(versionDeLaVacante(vacante)).isEqualTo(despues);
        assertThat(estado("ana")).isEqualTo("PRUEBA_POR_CONFIRMAR");
        assertThat(notaDeEtapa("ana", "PRUEBA_PUESTO")).isEqualByComparingTo("100");
        assertThat(versionDeLaNota("ana", "PRUEBA_PUESTO")).isEqualTo(despues);
        assertThat(notaDeEtapa("ana", "PERFIL_INTEGRAL")).isEqualByComparingTo("70");
        assertThat(versionDeLaNota("ana", "PERFIL_INTEGRAL")).isEqualTo(despues);
        // El grupo que el cambio calculó con la nota nueva, el mismo que sin carrera
        assertThat(grupo("ana")).isEqualTo(DESPUES);
    }

    @Test
    @Order(3)
    @DisplayName("QA-04 · la nota a mano que completa la prueba, con un cambio de pesos real a "
            + "mitad, tampoco le devuelve el grupo de antes (AC-01, AC-05)")
    void laNotaAManoQueCompletaLaPrueba() throws Exception {
        long vacante = vacanteConAbierta("Cajera · nota a mano a mitad del cambio", "ajuste");
        candidataCalificada("bruno", vacante);
        aLaPrueba("bruno");
        responder("bruno", "ajuste", "{\"texto\":\"Faltaban 10 soles del vuelto\"}");
        portal(post("/api/v1/portal/prueba/" + codigo.get("bruno") + "/entrega"), "bruno", null);
        assertThat(estado("bruno")).isEqualTo("PRUEBA_CALIFICANDO");
        assertThat(grupo("bruno")).isEqualTo(ANTES);

        try (Retenida vacanteRetenida = new Retenida(
                "select id from vacante where id = " + vacante + " for no key update")) {
            Future<String> cambio = enParalelo(() -> cambiarA(vacante, despues));
            hastaQueEsperen(1);
            Future<String> ajuste = enParalelo(() -> con(put("/api/v1/panel/postulaciones/"
                            + postulacion.get("bruno") + "/prueba-propia/criterios/"
                            + criterioDePersona.get("ajuste") + "/nota"),
                    "{\"puntaje\":80,\"motivo\":\"Encontró el vuelto\"}", 200));
            hastaQueEsperen(2);
            vacanteRetenida.soltar();
            cambio.get(ESPERA_S, TimeUnit.SECONDS);
            ajuste.get(ESPERA_S, TimeUnit.SECONDS);
        }

        assertThat(estado("bruno")).isEqualTo("PRUEBA_POR_CONFIRMAR");
        assertThat(notaDeEtapa("bruno", "PRUEBA_PUESTO")).isEqualByComparingTo("80");
        assertThat(versionDeLaNota("bruno", "PRUEBA_PUESTO")).isEqualTo(despues);
        assertThat(notaDeEtapa("bruno", "PERFIL_INTEGRAL")).isEqualByComparingTo("70");
        assertThat(grupo("bruno")).isEqualTo(DESPUES);
    }

    @Test
    @Order(4)
    @DisplayName("QA-04 · un recálculo del Perfil Integral que esperó al cambio escribe su grupo "
            + "aunque coincida con el que leyó antes de esperar (AC-05)")
    void unRecalculoQueEsperoEscribeSuGrupo() throws Exception {
        long vacante = vacanteConCerrada("Cajera · recálculo a mitad del cambio", "recalculo");
        candidataCalificada("fede", vacante);
        long suya = postulacion.get("fede");
        long personas = criterio("CV_PERSONAS");

        /*
          Como un ajuste a mano de una abierta: la transacción carga la postulación (el guardián
          del panel), guarda su nota y recalcula sin moverla, esperando al cambio. El cambio la
          deja en 70 y «con riesgo»; el ajuste, con su nota nueva (20 en personas), la deja en 30
          y «no priorizado», que es lo mismo que la postulación decía cuando la leyó. Con la foto
          de antes de esperar ese grupo no se escribía, y se quedaba el del cambio.
        */
        try (Retenida vacanteRetenida = new Retenida(
                "select id from vacante where id = " + vacante + " for no key update")) {
            Future<String> cambio = enParalelo(() -> cambiarA(vacante, despues));
            hastaQueEsperen(1);
            Future<Object> ajuste = enParalelo(() -> {
                new TransactionTemplate(transacciones).executeWithoutResult(t -> {
                    assertThat(postulaciones.findById(suya).orElseThrow().getGrupoPrioridad())
                            .isEqualTo(ANTES);
                    jdbc.update("update nota_criterio set puntaje = 20 where postulacion_id = ? "
                            + "and criterio_id = ?", suya, personas);
                    puente.recalcularSinMover(suya);
                });
                return null;
            });
            hastaQueEsperen(2);
            vacanteRetenida.soltar();
            cambio.get(ESPERA_S, TimeUnit.SECONDS);
            ajuste.get(ESPERA_S, TimeUnit.SECONDS);
        }

        assertThat(notaDeEtapa("fede", "PERFIL_INTEGRAL")).isEqualByComparingTo("30");
        assertThat(versionDeLaNota("fede", "PERFIL_INTEGRAL")).isEqualTo(despues);
        assertThat(grupo("fede")).isEqualTo(ANTES);
        assertThat(estado("fede")).isEqualTo("PERFIL_POR_CONFIRMAR");
    }

    @Test
    @Order(5)
    @DisplayName("QA-05 · el cambio de pesos a mitad no devuelve a su estado de antes a quien el "
            + "equipo avanzó o descartó en ese rato (AC-01, AC-04)")
    void elCambioNoDeshaceLoQueHizoElEquipo() throws Exception {
        long vacante = vacanteConCerrada("Cajera · el equipo trabaja a mitad del cambio", "equipo");
        candidataCalificada("ines", vacante);
        candidataCalificada("ceci", vacante);
        candidataCalificada("beto", vacante);
        // El cambio las recorre de la más reciente a la más antigua: Beto primero
        recienteAAntigua("beto", "ceci", "ines");

        String antesDelEquipo;
        String despuesDelEquipo;
        try (Retenida betoRetenido = new Retenida("select id from postulacion where id = "
                + postulacion.get("beto") + " for update")) {
            // El cambio ya leyó a las tres y se queda a mitad al ir a reescribir a Beto
            Future<String> cambio = enParalelo(() -> cambiarA(vacante, despues));
            hastaQueEsperen(1);
            antesDelEquipo = filaDe("ines") + " | " + filaDe("ceci");

            // En ese rato el equipo pasa a Inés a la prueba y descarta a Ceci, sin esperar a nadie
            con(post("/api/v1/panel/postulaciones/" + postulacion.get("ines") + "/confirmacion-avance"),
                    "{\"motivo\":\"Pasa a la prueba mientras cambian los pesos\"}", 200);
            con(post("/api/v1/panel/postulaciones/" + postulacion.get("ceci") + "/transiciones"),
                    "{\"estadoDestino\":\"NO_CONTINUA\",\"motivo\":\"No sigue mientras cambian los pesos\"}",
                    200);
            assertThat(cambio.isDone()).as("el cambio sigue a mitad").isFalse();
            despuesDelEquipo = filaDe("ines") + " | " + filaDe("ceci");

            betoRetenido.soltar();
            assertThat(json.readTree(cambio.get(ESPERA_S, TimeUnit.SECONDS))
                    .get("personasRecalculadas").asInt()).isEqualTo(3);
        }

        assertThat(despuesDelEquipo).isNotEqualTo(antesDelEquipo);
        assertThat(estado("ines")).isEqualTo("PRUEBA_TURNO_CANDIDATO");
        assertThat(estado("ceci")).isEqualTo("NO_CONTINUA");
        // Ni el estado, ni el motivo del cierre, ni cuándo se movió: lo del equipo, tal cual
        assertThat(filaDe("ines") + " | " + filaDe("ceci")).isEqualTo(despuesDelEquipo);
        // Y el historial dice lo mismo que la postulación
        assertThat(ultimaTransicion("ines")).isEqualTo("PRUEBA_TURNO_CANDIDATO");
        assertThat(ultimaTransicion("ceci")).isEqualTo("NO_CONTINUA");
        // El cambio hizo lo suyo con las tres
        for (String quien : new String[] {"ines", "ceci", "beto"}) {
            assertThat(notaDeEtapa(quien, "PERFIL_INTEGRAL")).as(quien).isEqualByComparingTo("70");
            assertThat(versionDeLaNota(quien, "PERFIL_INTEGRAL")).as(quien).isEqualTo(despues);
            assertThat(grupo(quien)).as(quien).isEqualTo(DESPUES);
        }
    }

    @Test
    @Order(6)
    @DisplayName("QA-04 · avanzar a quien el cambio ya reescribió espera a que confirme y no le "
            + "devuelve el grupo de antes (AC-01, AC-05)")
    void avanzarAQuienElCambioYaReescribio() throws Exception {
        long vacante = vacanteConCerrada("Cajera · avance tras la reescritura", "reescrita");
        candidataCalificada("eli", vacante);
        candidataCalificada("dani", vacante);
        // Dani primero: cuando el cambio se queda a mitad, ya le reescribió el grupo sin confirmar
        recienteAAntigua("dani", "eli");

        try (Retenida eliRetenida = new Retenida("select id from postulacion where id = "
                + postulacion.get("eli") + " for update")) {
            Future<String> cambio = enParalelo(() -> cambiarA(vacante, despues));
            hastaQueEsperen(1);
            // El equipo avanza a Dani: lee la fila confirmada (el grupo de antes) y, al moverla,
            // espera a que el cambio suelte la suya
            Future<String> avance = enParalelo(() -> con(post("/api/v1/panel/postulaciones/"
                            + postulacion.get("dani") + "/confirmacion-avance"),
                    "{\"motivo\":\"Pasa a la prueba mientras cambian los pesos\"}", 200));
            hastaQueEsperen(2);
            eliRetenida.soltar();
            cambio.get(ESPERA_S, TimeUnit.SECONDS);
            avance.get(ESPERA_S, TimeUnit.SECONDS);
        }

        assertThat(estado("dani")).isEqualTo("PRUEBA_TURNO_CANDIDATO");
        assertThat(notaDeEtapa("dani", "PERFIL_INTEGRAL")).isEqualByComparingTo("70");
        assertThat(grupo("dani")).isEqualTo(DESPUES);
        assertThat(grupo("eli")).isEqualTo(DESPUES);
    }

    // ---- La carrera ----

    /**
     * Otra sesión que retiene una fila hasta que la prueba la suelta. Si algo se queda colgado
     * esperándola, la suelta sola al cabo de {@link #ESPERA_S} segundos: la prueba falla, pero
     * no se queda parada.
     */
    private final class Retenida implements AutoCloseable {
        private final CountDownLatch tomada = new CountDownLatch(1);
        private final CountDownLatch soltar = new CountDownLatch(1);
        private final ExecutorService hilo = Executors.newSingleThreadExecutor();
        private final Future<?> fin;

        Retenida(String bloqueo) throws Exception {
            fin = hilo.submit(() -> {
                try (Connection c = fuente.getConnection()) {
                    c.setAutoCommit(false);
                    try (Statement s = c.createStatement()) {
                        s.execute(bloqueo);
                    }
                    tomada.countDown();
                    soltar.await(ESPERA_S, TimeUnit.SECONDS);
                    c.rollback();
                }
                return null;
            });
            assertThat(tomada.await(ESPERA_S, TimeUnit.SECONDS)).isTrue();
        }

        void soltar() throws Exception {
            soltar.countDown();
            fin.get(ESPERA_S, TimeUnit.SECONDS);
        }

        @Override
        public void close() {
            soltar.countDown();
            hilo.shutdownNow();
        }
    }

    private final ExecutorService peticiones = Executors.newCachedThreadPool();

    @AfterEach
    void sinHilosSueltos() {
        peticiones.shutdownNow();
    }

    private <T> Future<T> enParalelo(Callable<T> tarea) {
        return peticiones.submit(tarea);
    }

    /** Hasta que haya {@code cuantas} sesiones esperando a un cerrojo. */
    private void hastaQueEsperen(int cuantas) throws InterruptedException {
        long limite = System.nanoTime() + TimeUnit.SECONDS.toNanos(ESPERA_S);
        while (esperando() < cuantas) {
            if (System.nanoTime() > limite) {
                throw new AssertionError("Nunca hubo " + cuantas + " sesiones esperando a un "
                        + "cerrojo: la carrera no se montó");
            }
            Thread.sleep(50);
        }
    }

    private long esperando() {
        return jdbc.queryForObject("select count(*) from pg_stat_activity where datname = "
                + "current_database() and wait_event_type = 'Lock' and pid <> pg_backend_pid()",
                Long.class);
    }

    /** El orden de llegada, para que el cambio las recorra como se quiere. */
    private void recienteAAntigua(String... quienes) {
        for (int i = 0; i < quienes.length; i++) {
            jdbc.update("update postulacion set creado_en = now() - make_interval(mins => ?) "
                    + "where id = ?", i, postulacion.get(quienes[i]));
        }
    }

    private String cambiarA(long vacante, long version) throws Exception {
        return con(post("/api/v1/panel/vacantes/" + vacante + "/version-pesos"),
                "{\"versionPesosId\": %d}".formatted(version), 200);
    }

    // ---- La siembra ----

    /**
     * Una versión publicada con el Perfil Integral entero en el currículum, y el currículum
     * repartido entre resultados y gestión de personas.
     */
    private long version(String etiqueta, String resultados, String personas) {
        long id = jdbc.queryForObject("insert into version_pesos (organizacion_id, etiqueta, estado, "
                + "publicada_en) values (1, ?, 'PUBLICADA', now() - interval '1 year') returning id",
                Long.class, etiqueta);
        jdbc.update("insert into peso_etapa (version_pesos_id, etapa_codigo, peso) values "
                + "(?, 'PERFIL_INTEGRAL', 40), (?, 'PRUEBA_PUESTO', 30), (?, 'SIMULACION', 15), "
                + "(?, 'VALIDACION', 15)", id, id, id, id);
        jdbc.update("insert into peso_componente_perfil (version_pesos_id, componente, peso) "
                + "values (?, 'CV', 100), (?, 'EVALUACION', 0)", id, id);
        for (String[] criterio : new String[][] {{"CV_RESULTADOS", resultados}, {"CV_PERSONAS", personas}}) {
            jdbc.update("""
                    insert into peso_criterio (version_pesos_id, nivel_puesto_codigo, criterio_id, peso)
                    select ?, 'EJECUCION', c.id, ? from criterio c
                     where c.codigo = ? and c.version_plantilla_prueba_id is null""",
                    id, new BigDecimal(criterio[1]), criterio[0]);
        }
        return id;
    }

    /** Sin evaluación y con una prueba de una cerrada, que queda entera al entregarla. */
    private long vacanteConCerrada(String titulo, String clave) throws Exception {
        long vacante = vacanteSinEvaluacion(titulo);
        con(put(base(vacante) + "/borrador"), """
                {"guiaCalificacion":"Mira el monto","enunciado":"La caja de abril no cuadra.",
                 "modalidad":"CRONOMETRADA","duracionMinutos":90}""", 200);
        long caja = idDelCriterio(con(post(base(vacante) + "/criterios"),
                "{\"nombre\":\"Caja\",\"puntos\":100,\"calificador\":\"IA\"}", 200), "Caja");
        agregar(vacante, clave, """
                {"tipo":"OPCION_UNICA","enunciado":"¿Cuánto falta en la caja de abril?",
                 "puntos":100,"criterioId":%d,
                 "opciones":[{"texto":"Diez soles","puntos":100},{"texto":"Nada","puntos":0}]}"""
                .formatted(caja));
        return publicarConLosPesosDeAntes(vacante);
    }

    /** Sin evaluación y con una prueba de una abierta que califica una persona. */
    private long vacanteConAbierta(String titulo, String clave) throws Exception {
        long vacante = vacanteSinEvaluacion(titulo);
        con(put(base(vacante) + "/borrador"), """
                {"guiaCalificacion":"Mira el monto","enunciado":"La caja de marzo no cuadra.",
                 "modalidad":"CRONOMETRADA","duracionMinutos":90}""", 200);
        long caja = idDelCriterio(con(post(base(vacante) + "/criterios"),
                "{\"nombre\":\"Caja\",\"puntos\":100,\"calificador\":\"PERSONA\"}", 200), "Caja");
        criterioDePersona.put(clave, caja);
        agregar(vacante, clave, """
                {"tipo":"ABIERTA","enunciado":"¿Cómo cuadraste la caja?","criterioId":%d,
                 "queDebeTener":"El monto"}""".formatted(caja));
        return publicarConLosPesosDeAntes(vacante);
    }

    private long vacanteSinEvaluacion(String titulo) throws Exception {
        long solicitud = Long.parseLong(leer(con(post("/api/v1/panel/solicitudes"), """
                {"areaId": %d, "puestoId": %d, "urgencia": "NORMAL",
                 "nivelPuestoCodigo": "EJECUCION", "familiaCodigo": "OPERACIONES",
                 "resultadoPrincipal": "Caja cuadrada cada día", "motivo": "No llegamos",
                 "consecuenciaNoContratar": "Se descuadra", "analisisCapacidad": "No alcanza",
                 "responsableUsuarioId": 1,
                 "resultadosEsperados": [
                   {"descripcion": "Cuadrar", "indicador": "diario"},
                   {"descripcion": "Reportar", "indicador": "semanal"},
                   {"descripcion": "Depositar", "indicador": "diario"}
                 ]}""".formatted(areaId, puestoId), 201), "id"));
        con(post("/api/v1/panel/solicitudes/" + solicitud + "/aprobacion"),
                "{\"motivo\":\"Hay presupuesto\"}", 200);
        long vacante = Long.parseLong(leer(con(post("/api/v1/panel/vacantes"), """
                {"solicitudTalentoId": %d, "puestoId": %d, "titulo": "%s",
                 "descripcion": "Caja y depósitos", "tipoCierre": "PERMANENTE",
                 "responsableUsuarioId": 1}""".formatted(solicitud, puestoId, titulo), 201), "id"));
        // Quien postula va directo a la bandeja: el Perfil Integral se siembra a mano
        con(post("/api/v1/panel/vacantes/" + vacante + "/aplicacion-evaluacion"),
                "{\"aplica\":false}", 200);
        return vacante;
    }

    private long publicarConLosPesosDeAntes(long vacante) throws Exception {
        Instant cierre = Instant.now().plus(Duration.ofDays(20)).truncatedTo(ChronoUnit.SECONDS);
        con(put(base(vacante) + "/fecha-limite"), "{\"cierraEn\":\"%s\"}".formatted(cierre), 200);
        con(post(base(vacante) + "/publicacion"), null, 200);
        con(post("/api/v1/panel/vacantes/" + vacante + "/publicacion"), null, 200);
        jdbc.update("update vacante set version_pesos_id = ? where id = ?", antes, vacante);
        return vacante;
    }

    private void agregar(long vacante, String clave, String cuerpo) throws Exception {
        JsonNode editor = json.readTree(con(post(base(vacante) + "/preguntas"), cuerpo, 200));
        String enunciado = json.readTree(cuerpo).get("enunciado").asText();
        for (JsonNode c : editor.at("/borrador/criterios")) {
            for (JsonNode p : c.get("preguntas")) {
                if (p.get("enunciado").asText().equals(enunciado)) {
                    pregunta.put(clave, p.get("id").asLong());
                    p.get("opciones").forEach(o -> opcion.put(clave + ":" + o.get("texto").asText(),
                            o.get("id").asLong()));
                }
            }
        }
        assertThat(pregunta).containsKey(clave);
    }

    private long idDelCriterio(String editor, String nombre) throws Exception {
        for (JsonNode c : json.readTree(editor).at("/borrador/criterios")) {
            if (nombre.equals(c.get("nombre").asText())) {
                return c.get("id").asLong();
            }
        }
        throw new AssertionError("No está el criterio " + nombre);
    }

    /**
     * Postula por el portal y se le siembra el Perfil Integral como lo dejaría la IA: 40 en
     * resultados, 100 en personas, el retrato, y la nota de la etapa con los pesos de antes.
     */
    private void candidataCalificada(String quien, long vacante) throws Exception {
        String correo = quien + ".qa0405@correo.pe";
        mvc.perform(post("/api/v1/portal/cuentas").contentType(MediaType.APPLICATION_JSON).content("""
                        {"nombre":"%s","apellidos":"Prueba","correo":"%s",
                         "contrasena":"unaClaveLarga123","ciudadUbigeo":"1501","telefono": "987654321",
                         "aceptaPlataforma":true, "aceptaFuturosContactos":false}"""
                        .formatted(Character.toUpperCase(quien.charAt(0)) + quien.substring(1), correo)))
                .andExpect(status().isCreated());
        tokenDe.put(quien, leer(mvc.perform(post("/api/v1/portal/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"correo\":\"%s\",\"contrasena\":\"unaClaveLarga123\"}".formatted(correo)))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString(), "token"));
        MockMultipartFile cv = new MockMultipartFile("cv", "cv.pdf", "application/pdf", "contenido".getBytes());
        String suCodigo = leer(mvc.perform(multipart("/api/v1/portal/postulaciones").file(cv)
                        .param("vacanteId", String.valueOf(vacante))
                        .param("resultadoOrgulloso", "Cuadré la caja tres años seguidos")
                        .param("aceptaTratamiento", "true")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenDe.get(quien)))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString(), "codigo");
        codigo.put(quien, suCodigo);
        long id = jdbc.queryForObject("select id from postulacion where uuid = ?::uuid", Long.class, suCodigo);
        postulacion.put(quien, id);
        assertThat(estado(quien)).isEqualTo("PERFIL_POR_CONFIRMAR");

        notaDeCriterio(id, criterio("CV_RESULTADOS"), "40");
        notaDeCriterio(id, criterio("CV_PERSONAS"), "100");
        jdbc.update("insert into perfil_talento (postulacion_id, adecuacion, potencial, alto_rendimiento, "
                + "confianza_evidencia, resumen, version_pesos_id) values (?, 50, 10, 50, 70, "
                + "'Retrato de prueba', ?)", id, antes);
        jdbc.update("insert into nota_etapa (postulacion_id, etapa_codigo, puntaje, version_pesos_id, "
                + "calculada_en) values (?, 'PERFIL_INTEGRAL', 40, ?, now())", id, antes);
        jdbc.update("update postulacion set grupo_prioridad = ? where id = ?", ANTES, id);
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

    /** El equipo la pasa a la prueba, y ella la empieza. */
    private void aLaPrueba(String quien) throws Exception {
        con(post("/api/v1/panel/postulaciones/" + postulacion.get(quien) + "/confirmacion-avance"),
                "{\"motivo\":\"Pasa a la prueba\"}", 200);
        assertThat(estado(quien)).isEqualTo("PRUEBA_TURNO_CANDIDATO");
        portal(post("/api/v1/portal/prueba/" + codigo.get(quien) + "/inicio"), quien, null);
    }

    private void responder(String quien, String clave, String cuerpo) throws Exception {
        portal(put("/api/v1/portal/prueba/" + codigo.get(quien) + "/respuestas/" + pregunta.get(clave)),
                quien, cuerpo);
    }

    // ---- Lo que se lee ----

    private BigDecimal notaDeEtapa(String quien, String etapa) {
        return jdbc.queryForList("select puntaje from nota_etapa where postulacion_id = ? and "
                        + "etapa_codigo = ?", BigDecimal.class, postulacion.get(quien), etapa).stream()
                .findFirst().orElse(null);
    }

    private Long versionDeLaNota(String quien, String etapa) {
        return jdbc.queryForObject("select version_pesos_id from nota_etapa where postulacion_id = ? "
                + "and etapa_codigo = ?", Long.class, postulacion.get(quien), etapa);
    }

    private long versionDeLaVacante(long vacante) {
        return jdbc.queryForObject("select version_pesos_id from vacante where id = ?", Long.class, vacante);
    }

    private String estado(String quien) {
        return jdbc.queryForObject("select estado_codigo from postulacion where id = ?", String.class,
                postulacion.get(quien));
    }

    private String grupo(String quien) {
        return jdbc.queryForObject("select grupo_prioridad from postulacion where id = ?",
                String.class, postulacion.get(quien));
    }

    /** Lo que el equipo escribe al moverla: el estado, el motivo del cierre y cuándo. */
    private String filaDe(String quien) {
        return jdbc.queryForObject("select estado_codigo || '/' || coalesce(motivo_cierre, '-') || '/' "
                + "|| coalesce(movido_en::text, '-') from postulacion where id = ?", String.class,
                postulacion.get(quien));
    }

    private String ultimaTransicion(String quien) {
        return jdbc.queryForObject("select estado_nuevo_codigo from transicion_estado where "
                + "postulacion_id = ? order by ocurrida_en desc, id desc limit 1", String.class,
                postulacion.get(quien));
    }

    // ---- HTTP ----

    private static String base(long vacante) {
        return "/api/v1/panel/vacantes/" + vacante + "/prueba-propia";
    }

    private String con(MockHttpServletRequestBuilder peticion, String cuerpo, int estado) throws Exception {
        return pedir(peticion, token, cuerpo, estado);
    }

    private String portal(MockHttpServletRequestBuilder peticion, String quien, String cuerpo) throws Exception {
        return pedir(peticion, tokenDe.get(quien), cuerpo, 200);
    }

    private String pedir(MockHttpServletRequestBuilder peticion, String suToken, String cuerpo, int estado)
            throws Exception {
        peticion.header(HttpHeaders.AUTHORIZATION, "Bearer " + suToken);
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
