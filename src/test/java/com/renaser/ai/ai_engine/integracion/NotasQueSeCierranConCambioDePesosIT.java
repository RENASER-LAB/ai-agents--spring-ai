package com.renaser.ai.ai_engine.integracion;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.renaser.ai.ai_engine.integracion.soporte.ImagenesDeContenedores;
import com.renaser.ai.ai_engine.integracion.soporte.RespuestaV3;

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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Las notas que se escriben al cerrar una prueba o una evaluación, con un cambio de pesos a
 * mitad (V74, specs/pesos-con-candidatos-y-fin-del-prestamo.md, AC-01 y AC-05; hallazgo
 * QA-03).
 *
 * <p>La nota de la prueba del editor y la de lo cerrado de la evaluación no dependen de los
 * pesos, pero dicen con qué versión se calcularon. Las escribía quien cerraba la prueba —el
 * ajuste a mano, la entrega, la IA— leyendo la vacante sin esperar: en mitad de un cambio
 * salían con la versión de antes (80 con la 13 en una vacante ya en la 14) y así se quedaban.
 *
 * <p>Cada carrera se monta igual que la de QA-02 en {@code FlujoPesosConCandidatosIT}: otra
 * sesión deja la vacante bloqueada y ya en otra versión, sin confirmar, y en ese hueco llega la
 * escritura. Además, mientras dura, el cambio toma la fila de la postulación, como el recálculo
 * al reescribir su grupo: si la escritura hubiera movido la postulación antes de esperar a la
 * vacante —lo que hacen la entrega de la prueba y la de la evaluación—, los dos se esperarían
 * el uno al otro y la base mataría a uno. Que las dos terminen bien es la prueba de que no hay
 * interbloqueo.
 *
 * <p>Y le cambia el grupo de verdad, como el recálculo cuando la nota nueva lo cambia (QA-04):
 * la escritura había leído la postulación antes de esperar, con el grupo de antes, y al
 * moverla la guardaba entera, devolviéndoselo. Que el grupo del cambio siga ahí al final es la
 * prueba de que la escritura parte de la fila releída y solo escribe lo suyo.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@DisplayName("QA-03 y QA-04 · las notas que cierran una prueba o una evaluación con el cambio de "
        + "pesos a mitad salen con la versión nueva, sin interbloqueo y sin devolver el grupo de antes")
class NotasQueSeCierranConCambioDePesosIT {

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

    /** Lo que dura el cambio de pesos a mitad después de que llegue la escritura. */
    private static final long CAMBIO_MS = 1_500;

    /**
     * El grupo que el cambio deja. Nadie lo tiene antes: con la IA apagada, ninguna de las tres
     * tiene grupo hasta que el cambio se lo pone.
     */
    private static final String GRUPO_DEL_CAMBIO = "POTENCIAL_CON_RIESGO";

    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired DataSource fuente;

    final ObjectMapper json = new ObjectMapper();

    static String token;
    static long areaId;
    static long puestoId;
    /** Su prueba: un criterio de persona con una abierta. Se completa con el ajuste a mano. */
    static long conPersona;
    static long criterioDePersona;
    /** Su prueba: solo una cerrada, entera al entregar. Y rinde la evaluación del nivel. */
    static long soloCerradas;
    static final Map<String, Long> pregunta = new HashMap<>();
    static final Map<String, Long> opcion = new HashMap<>();
    static final Map<String, Long> postulacion = new HashMap<>();
    static final Map<String, String> codigo = new HashMap<>();
    static final Map<String, String> tokenDe = new HashMap<>();

    @Test
    @Order(1)
    @DisplayName("dos vacantes con su prueba del editor publicada, y tres candidatas a punto de cerrar")
    void lasVacantesYSusCandidatas() throws Exception {
        token = leer(mvc.perform(post("/api/v1/panel/auth/dev-login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"usuarioRenaserOsId\":\"dev-qa03\"}"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(), "token");
        areaId = jdbc.queryForObject("insert into area (organizacion_id, nombre, es_activa) "
                + "values (1, 'Tesorería', true) returning id", Long.class);
        puestoId = Long.parseLong(leer(con(post("/api/v1/panel/puestos"), """
                {"codigo": "CAJERA_QA03", "nombre": "Cajera", "nivelPuestoCodigo": "EJECUCION",
                 "familiaCodigo": "OPERACIONES"}""", 201), "id"));

        // La del ajuste a mano: sin evaluación, quien postula va directo a la bandeja
        conPersona = crearVacante("Cajera con prueba de persona");
        con(post("/api/v1/panel/vacantes/" + conPersona + "/aplicacion-evaluacion"),
                "{\"aplica\":false}", 200);
        con(put(base(conPersona) + "/borrador"), """
                {"guiaCalificacion":"Mira el monto","enunciado":"La caja de marzo no cuadra.",
                 "modalidad":"CRONOMETRADA","duracionMinutos":90}""", 200);
        criterioDePersona = idDelCriterio(con(post(base(conPersona) + "/criterios"),
                "{\"nombre\":\"Caja\",\"puntos\":100,\"calificador\":\"PERSONA\"}", 200), "Caja");
        agregar(conPersona, "abierta", """
                {"tipo":"ABIERTA","enunciado":"¿Cómo cuadraste la caja?","criterioId":%d,
                 "queDebeTener":"El monto"}""".formatted(criterioDePersona));
        publicar(conPersona);

        // La de la entrega: una cerrada que llena su criterio, y la evaluación del nivel
        soloCerradas = crearVacante("Cajera con prueba de cerradas");
        con(post("/api/v1/panel/vacantes/" + soloCerradas + "/origen-preguntas"),
                "{\"origen\":\"NIVEL\"}", 200);
        long plantilla = jdbc.queryForObject(
                "select id from plantilla_evaluacion where nivel_puesto_codigo = 'EJECUCION'", Long.class);
        con(post("/api/v1/panel/vacantes/" + soloCerradas + "/plantilla-evaluacion"),
                "{\"plantillaEvaluacionId\": %d}".formatted(plantilla), 200);
        con(put(base(soloCerradas) + "/borrador"), """
                {"guiaCalificacion":"Mira el monto","enunciado":"La caja de abril no cuadra.",
                 "modalidad":"CRONOMETRADA","duracionMinutos":90}""", 200);
        long caja = idDelCriterio(con(post(base(soloCerradas) + "/criterios"),
                "{\"nombre\":\"Caja\",\"puntos\":100,\"calificador\":\"IA\"}", 200), "Caja");
        agregar(soloCerradas, "cerrada", """
                {"tipo":"OPCION_UNICA","enunciado":"¿Cuánto falta en la caja de abril?",
                 "puntos":100,"criterioId":%d,
                 "opciones":[{"texto":"Diez soles","puntos":100},{"texto":"Nada","puntos":0}]}"""
                .formatted(caja));
        publicar(soloCerradas);

        // Pía entrega la abierta y espera a que una persona la califique
        postular("pia", conPersona);
        assertThat(estado("pia")).isEqualTo("PERFIL_POR_CONFIRMAR");
        aLaPrueba("pia");
        responder("pia", "abierta", "{\"texto\":\"Faltaban 10 soles del vuelto\"}");
        portal(post("/api/v1/portal/prueba/" + codigo.get("pia") + "/entrega"), "pia", null);
        assertThat(estado("pia")).isEqualTo("PRUEBA_CALIFICANDO");
        assertThat(notaDeEtapa("pia", "PRUEBA_PUESTO")).isNull();

        // Lía ya respondió su cerrada y le falta entregar. Su evaluación del nivel no es lo que
        // se mira aquí: se la salta a mano, como si el equipo la hubiera dado por buena.
        postular("lia", soloCerradas);
        jdbc.update("update postulacion set estado_codigo = 'PERFIL_POR_CONFIRMAR' where id = ?",
                postulacion.get("lia"));
        aLaPrueba("lia");
        responder("lia", "cerrada", "{\"opcionId\":%d}".formatted(opcion.get("cerrada:Diez soles")));

        // Rosa respondió entera la evaluación del nivel y le falta entregar
        postular("rosa", soloCerradas);
        assertThat(estado("rosa")).isEqualTo("PERFIL_TURNO_CANDIDATO");
        portal(post("/api/v1/portal/evaluacion/" + codigo.get("rosa") + "/inicio"), "rosa", null);
        JsonNode evaluacion = json.readTree(portal(get("/api/v1/portal/evaluacion/" + codigo.get("rosa")),
                "rosa", null));
        for (JsonNode p : evaluacion.get("preguntas")) {
            portal(put("/api/v1/portal/evaluacion/" + codigo.get("rosa") + "/respuestas/"
                    + p.get("id").asLong()), "rosa", RespuestaV3.para(p));
        }
    }

    @Test
    @Order(2)
    @DisplayName("el ajuste a mano que completa la prueba espera al cambio y su nota dice la "
            + "versión nueva (la reproducción de QA-03)")
    void elAjusteAMano() throws Exception {
        assertThat(grupo("pia")).isNull();
        long nueva = otraVersion("QA-03 · después del ajuste");
        Duration espera = conUnCambioDePesosAMitad(conPersona, nueva, postulacion.get("pia"),
                () -> con(put("/api/v1/panel/postulaciones/" + postulacion.get("pia")
                                + "/prueba-propia/criterios/" + criterioDePersona + "/nota"),
                        "{\"puntaje\":80,\"motivo\":\"Encontró el vuelto\"}", 200));

        assertThat(espera).isGreaterThan(Duration.ofMillis(1_000));
        assertThat(versionDeLaVacante(conPersona)).isEqualTo(nueva);
        assertThat(notaDeEtapa("pia", "PRUEBA_PUESTO")).isEqualByComparingTo("80");
        assertThat(versionDeLaNota("pia", "PRUEBA_PUESTO")).isEqualTo(nueva);
        assertThat(estado("pia")).isEqualTo("PRUEBA_POR_CONFIRMAR");
        assertThat(grupo("pia")).isEqualTo(GRUPO_DEL_CAMBIO);
    }

    @Test
    @Order(3)
    @DisplayName("la entrega que deja la prueba entera espera al cambio antes de mover la "
            + "postulación, y su nota dice la versión nueva")
    void laEntregaDeLaPrueba() throws Exception {
        assertThat(grupo("lia")).isNull();
        long nueva = otraVersion("QA-03 · después de la entrega");
        Duration espera = conUnCambioDePesosAMitad(soloCerradas, nueva, postulacion.get("lia"),
                () -> portal(post("/api/v1/portal/prueba/" + codigo.get("lia") + "/entrega"), "lia", null));

        assertThat(espera).isGreaterThan(Duration.ofMillis(1_000));
        assertThat(versionDeLaVacante(soloCerradas)).isEqualTo(nueva);
        assertThat(notaDeEtapa("lia", "PRUEBA_PUESTO")).isEqualByComparingTo("100");
        assertThat(versionDeLaNota("lia", "PRUEBA_PUESTO")).isEqualTo(nueva);
        assertThat(estado("lia")).isEqualTo("PRUEBA_POR_CONFIRMAR");
        assertThat(grupo("lia")).isEqualTo(GRUPO_DEL_CAMBIO);
    }

    @Test
    @Order(4)
    @DisplayName("la entrega de la evaluación espera al cambio antes de moverla, y la nota de lo "
            + "cerrado dice la versión nueva")
    void laEntregaDeLaEvaluacion() throws Exception {
        assertThat(grupo("rosa")).isNull();
        long nueva = otraVersion("QA-03 · después de la evaluación");
        Duration espera = conUnCambioDePesosAMitad(soloCerradas, nueva, postulacion.get("rosa"),
                () -> portal(post("/api/v1/portal/evaluacion/" + codigo.get("rosa") + "/entrega"),
                        "rosa", null));

        assertThat(espera).isGreaterThan(Duration.ofMillis(1_000));
        assertThat(versionDeLaVacante(soloCerradas)).isEqualTo(nueva);
        assertThat(notaDeEtapa("rosa", "PERFIL_INTEGRAL")).isNotNull();
        assertThat(versionDeLaNota("rosa", "PERFIL_INTEGRAL")).isEqualTo(nueva);
        assertThat(estado("rosa")).isEqualTo("PERFIL_CALIFICANDO");
        assertThat(grupo("rosa")).isEqualTo(GRUPO_DEL_CAMBIO);
    }

    // ---- La carrera ----

    /**
     * El cambio de pesos a mitad, como lo deja {@code ServicioPesosDeLaVacanteImpl#cambiar}
     * mientras recalcula: la vacante bloqueada y ya en {@code version}, sin confirmar. En ese
     * hueco corre {@code escritura}; el cambio, mientras tanto, le reescribe el grupo a la
     * postulación —con lo que toma su fila— y confirma.
     *
     * @return lo que tardó la escritura
     */
    private Duration conUnCambioDePesosAMitad(long vacante, long version, long suPostulacion,
                                              Callable<?> escritura) throws Exception {
        CountDownLatch aMitad = new CountDownLatch(1);
        ExecutorService otraSesion = Executors.newSingleThreadExecutor();
        Future<?> cambio = otraSesion.submit(() -> {
            try (Connection c = fuente.getConnection()) {
                c.setAutoCommit(false);
                try (Statement s = c.createStatement()) {
                    s.execute("select id from vacante where id = " + vacante + " for no key update");
                    s.executeUpdate("update vacante set version_pesos_id = " + version
                            + " where id = " + vacante);
                    aMitad.countDown();
                    Thread.sleep(CAMBIO_MS);
                    // Como el recálculo al reescribir el grupo: toma la fila de la postulación.
                    // Si la escritura ya la tuviera y esperase a la vacante, aquí se cruzarían.
                    // Y el grupo cambia de verdad: la escritura, que leyó el de antes, no debe
                    // devolvérselo al guardar (QA-04)
                    s.executeUpdate("update postulacion set grupo_prioridad = '" + GRUPO_DEL_CAMBIO
                            + "' where id = " + suPostulacion);
                }
                c.commit();
            }
            return null;
        });
        try {
            assertThat(aMitad.await(10, TimeUnit.SECONDS)).isTrue();
            long desde = System.nanoTime();
            escritura.call();
            Duration tardo = Duration.ofNanos(System.nanoTime() - desde);
            // El cambio también confirmó: ninguno de los dos se quedó por el camino
            cambio.get(10, TimeUnit.SECONDS);
            return tardo;
        } finally {
            otraSesion.shutdownNow();
        }
    }

    /** Una versión de pesos más, publicada: a la que se cambia en la carrera. */
    private long otraVersion(String etiqueta) {
        return jdbc.queryForObject("insert into version_pesos (organizacion_id, etiqueta, estado, "
                + "publicada_en) values (1, ?, 'PUBLICADA', now()) returning id", Long.class, etiqueta);
    }

    // ---- La siembra, por la API ----

    private long crearVacante(String titulo) throws Exception {
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
        return Long.parseLong(leer(con(post("/api/v1/panel/vacantes"), """
                {"solicitudTalentoId": %d, "puestoId": %d, "titulo": "%s",
                 "descripcion": "Caja y depósitos", "tipoCierre": "PERMANENTE",
                 "responsableUsuarioId": 1}""".formatted(solicitud, puestoId, titulo), 201), "id"));
    }

    private void publicar(long vacante) throws Exception {
        Instant cierre = Instant.now().plus(Duration.ofDays(20)).truncatedTo(ChronoUnit.SECONDS);
        con(put(base(vacante) + "/fecha-limite"), "{\"cierraEn\":\"%s\"}".formatted(cierre), 200);
        con(post(base(vacante) + "/publicacion"), null, 200);
        con(post("/api/v1/panel/vacantes/" + vacante + "/publicacion"), null, 200);
    }

    private void agregar(long vacante, String cual, String cuerpo) throws Exception {
        JsonNode editor = json.readTree(con(post(base(vacante) + "/preguntas"), cuerpo, 200));
        String enunciado = json.readTree(cuerpo).get("enunciado").asText();
        for (JsonNode c : editor.at("/borrador/criterios")) {
            for (JsonNode p : c.get("preguntas")) {
                if (p.get("enunciado").asText().equals(enunciado)) {
                    pregunta.put(cual, p.get("id").asLong());
                    p.get("opciones").forEach(o -> opcion.put(cual + ":" + o.get("texto").asText(),
                            o.get("id").asLong()));
                }
            }
        }
        assertThat(pregunta).containsKey(cual);
    }

    private long idDelCriterio(String editor, String nombre) throws Exception {
        for (JsonNode c : json.readTree(editor).at("/borrador/criterios")) {
            if (nombre.equals(c.get("nombre").asText())) {
                return c.get("id").asLong();
            }
        }
        throw new AssertionError("No está el criterio " + nombre);
    }

    private void postular(String quien, long vacante) throws Exception {
        String correo = quien + ".qa03@correo.pe";
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
        postulacion.put(quien, jdbc.queryForObject("select id from postulacion where uuid = ?::uuid",
                Long.class, suCodigo));
    }

    /** El equipo la pasa a la prueba, y ella la empieza. */
    private void aLaPrueba(String quien) throws Exception {
        con(post("/api/v1/panel/postulaciones/" + postulacion.get(quien) + "/confirmacion-avance"),
                "{\"motivo\":\"Pasa a la prueba\"}", 200);
        assertThat(estado(quien)).isEqualTo("PRUEBA_TURNO_CANDIDATO");
        portal(post("/api/v1/portal/prueba/" + codigo.get(quien) + "/inicio"), quien, null);
    }

    private void responder(String quien, String cual, String cuerpo) throws Exception {
        portal(put("/api/v1/portal/prueba/" + codigo.get(quien) + "/respuestas/" + pregunta.get(cual)),
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

    private String grupo(String quien) {
        return jdbc.queryForObject("select grupo_prioridad from postulacion where id = ?",
                String.class, postulacion.get(quien));
    }

    private String estado(String quien) {
        return jdbc.queryForObject("select estado_codigo from postulacion where id = ?", String.class,
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
