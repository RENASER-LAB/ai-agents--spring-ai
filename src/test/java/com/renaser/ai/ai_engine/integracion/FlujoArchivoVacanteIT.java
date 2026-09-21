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
import org.springframework.dao.DataIntegrityViolationException;
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

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Archivar una vacante cerrada, consultarla y devolverla a la lista, contra la base de verdad.
 *
 * <p>{@code ArchivarVacanteTest} ya cubre las decisiones del servicio con los repositorios
 * simulados. Lo que no puede cubrir, y por eso existe esta prueba, es todo lo que solo tiene
 * respuesta cuando hay una base y un HTTP de por medio:
 *
 * <ul>
 *   <li><b>Las dos listas son dos consultas.</b> Que {@code /vacantes} no traiga las
 *       archivadas no es un filtro de pantalla: es que no salen de la base. Con un mock, la
 *       diferencia entre las dos cosas no se ve.
 *   <li><b>El contrato HTTP de cada entrada de mutación</b>: que editar, cambiar el sueldo,
 *       publicar, reconfigurar o mover una postulación de una archivada contesten 409 y
 *       <b>no dejen nada escrito</b>. Son nueve puertas; probarlas de una en una en el
 *       servicio no demuestra que el controlador las exponga todas.
 *   <li><b>Los permisos</b>: 403 sin {@code cerrar_vacante}, con el rol real y sus filas de
 *       {@code rol_permiso}, y no con un mapa inventado.
 *   <li><b>La restricción de la V59</b>: un {@code update} a mano no puede archivar una
 *       publicada. Un mock acepta cualquier fila; la base no.
 * </ul>
 *
 * <p>Los pasos van en orden y comparten estado: la vacante que cierra el primero es la que
 * archiva el tercero y la que desarchiva el último.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@DisplayName("Una vacante cerrada se archiva, se consulta y vuelve")
public class FlujoArchivoVacanteIT {

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

    private static final String TITULO = "Coordinador de sede";

    static String tokenEquipo;
    /** Una cuenta de panel que ve las vacantes y no puede cerrarlas: RESPONSABLE_AREA. */
    static String tokenSinArchivo;
    static long solicitudId;
    static long vacanteId;
    /** Otra vacante que se queda viva: es la que demuestra que la lista habitual sigue ahí. */
    static long vacanteViva;
    static long postulacionEnCarrera;

    // ============ 1. El terreno: una vacante cerrada y otra viva ============

    @Test
    @Order(1)
    @DisplayName("una vacante en borrador se cierra, y otra se queda viva a su lado")
    void elTerreno() throws Exception {
        tokenEquipo = leer(mvc.perform(post("/api/v1/panel/auth/dev-login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"usuarioRenaserOsId\":\"dev-archivo\"}"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(), "token");

        jdbc.update("INSERT INTO area (organizacion_id, nombre, es_activa) VALUES (1, 'Operaciones', true)");
        Long areaId = jdbc.queryForObject("SELECT id FROM area LIMIT 1", Long.class);

        long puestoId = Long.parseLong(leer(conToken(post("/api/v1/panel/puestos"), tokenEquipo, """
                {"nombre": "Coordinador", "nivelPuestoCodigo": "EJECUCION",
                 "familiaCodigo": "OPERACIONES"}""")
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString(), "id"));

        solicitudId = crearSolicitudAprobada(areaId, puestoId);
        vacanteId = crearVacante(solicitudId, TITULO);
        vacanteViva = crearVacante(crearSolicitudAprobada(areaId, puestoId), "Analista de turno");

        // Cerrar y archivar son dos gestos, no uno: cerrar lleva su motivo y su aviso a quien
        // esté dentro, y archivar no puede dispararlo de rebote.
        conToken(post("/api/v1/panel/vacantes/" + vacanteId + "/cierre"), tokenEquipo,
                "{\"motivo\":\"Se cubrió con un traslado interno\"}")
                .andExpect(status().isOk());

        assertThat(jdbc.queryForObject("select estado from vacante where id = ?", String.class, vacanteId))
                .isEqualTo("CERRADA");
        assertThat(jdbc.queryForObject(
                "select archivada_en from vacante where id = ?", Instant.class, vacanteId))
                .as("cerrar no archiva: son dos decisiones")
                .isNull();
    }

    // ============ 2. Con alguien en carrera no se archiva ============

    @Test
    @Order(2)
    @DisplayName("con una postulación en carrera se rechaza diciendo cuántas quedan, y no archiva nada")
    void conAlguienEnCarreraNoSeArchiva() throws Exception {
        // Cerrar la vacante NO cierra sus postulaciones (RF-14): por eso una cerrada puede
        // tener gente esperando una decisión, y por eso archivarla sería esconderla.
        Long usuarioId = jdbc.queryForObject(
                "select id from usuario where usuario_renaser_os_id = 'dev-archivo'", Long.class);
        postulacionEnCarrera = jdbc.queryForObject("""
                insert into postulacion (organizacion_id, usuario_id, vacante_id, estado_codigo)
                values (1, ?, ?, 'PERFIL_POR_CONFIRMAR') returning id""",
                Long.class, usuarioId, vacanteId);

        conToken(post("/api/v1/panel/vacantes/" + vacanteId + "/archivo"), tokenEquipo, null)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value(org.hamcrest.Matchers.containsString(
                        "Quedan 1 postulantes en carrera")));

        assertThat(jdbc.queryForObject(
                "select archivada_en from vacante where id = ?", Instant.class, vacanteId))
                .isNull();
        assertThat(contar("select count(*) from auditoria where accion = 'archivar_vacante'"))
                .as("un intento rechazado no deja rastro de archivo")
                .isZero();
    }

    // ============ 3. Sin nadie en carrera, se archiva ============

    @Test
    @Order(3)
    @DisplayName("decidida la última postulación, archivar guarda la fecha, audita y no avisa a nadie")
    void sinNadieEnCarreraSeArchiva() throws Exception {
        long avisosAntes = contar("select count(*) from aviso_portal");
        // La postulación se decide: deja de estar en carrera. Se escribe a mano porque lo que
        // se prueba aquí es el archivo, no la máquina de estados.
        jdbc.update("update postulacion set estado_codigo = 'NO_CONTINUA', "
                + "motivo_cierre = 'DECISION_PERSONA' where id = ?", postulacionEnCarrera);

        conToken(post("/api/v1/panel/vacantes/" + vacanteId + "/archivo"), tokenEquipo, null)
                .andExpect(status().isOk());

        assertThat(jdbc.queryForObject(
                "select archivada_en from vacante where id = ?", Instant.class, vacanteId))
                .isNotNull();
        assertThat(jdbc.queryForObject("select estado from vacante where id = ?", String.class, vacanteId))
                .as("archivar no es un estado: sigue CERRADA")
                .isEqualTo("CERRADA");
        assertThat(contar("""
                select count(*) from auditoria
                 where accion = 'archivar_vacante' and entidad = 'vacante' and entidad_id = %d"""
                .formatted(vacanteId)))
                .isEqualTo(1);
        assertThat(contar("select count(*) from aviso_portal"))
                .as("archivar no es una noticia: en el proceso del candidato no cambió nada")
                .isEqualTo(avisosAntes);
        // Y la postulación sigue donde estaba, con su motivo de cierre intacto.
        assertThat(jdbc.queryForObject("select estado_codigo from postulacion where id = ?",
                String.class, postulacionEnCarrera)).isEqualTo("NO_CONTINUA");
    }

    // ============ 4. Las dos listas, y el contador ============

    @Test
    @Order(4)
    @DisplayName("la lista habitual deja de traerla; Archivadas la trae con su fecha y el contador la cuenta")
    void lasDosListas() throws Exception {
        conToken(get("/api/v1/panel/vacantes"), tokenEquipo, null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.id == " + vacanteId + ")]").isEmpty())
                .andExpect(jsonPath("$[?(@.id == " + vacanteViva + ")]").isNotEmpty());

        conToken(get("/api/v1/panel/vacantes?archivadas=true"), tokenEquipo, null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.id == " + vacanteId + ")]").isNotEmpty())
                .andExpect(jsonPath("$[?(@.id == " + vacanteViva + ")]").isEmpty())
                .andExpect(jsonPath("$[0].archivadaEn").isNotEmpty())
                .andExpect(jsonPath("$[0].estado").value("CERRADA"))
                .andExpect(jsonPath("$[0].puedeEditar").value(false))
                .andExpect(jsonPath("$[0].puedeArchivar").value(false))
                .andExpect(jsonPath("$[0].puedeDesarchivar").value(true));

        conToken(get("/api/v1/panel/vacantes/archivadas/conteo"), tokenEquipo, null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.archivadas").value(1));

        // Su detalle sigue leyéndose entero, con la fecha del archivo dentro: es lo que la
        // pantalla pinta como «Archivada el …».
        conToken(get("/api/v1/panel/vacantes/" + vacanteId), tokenEquipo, null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.titulo").value(TITULO))
                .andExpect(jsonPath("$.archivadaEn").isNotEmpty())
                .andExpect(jsonPath("$.puedeDesarchivar").value(true));

        // Y el ranking de su proceso, que es la mitad que archivar promete conservar.
        conToken(get("/api/v1/panel/vacantes/" + vacanteId + "/ranking?etapa=PERFIL_INTEGRAL"),
                tokenEquipo, null)
                .andExpect(status().isOk());
    }

    // ============ 5. Lo que una archivada ya no admite ============

    @Test
    @Order(5)
    @DisplayName("editar, cambiar el sueldo, publicar, reconfigurar, cerrar o mover su gente: 409 y sin efectos")
    void todasLasMutacionesSeRechazan() throws Exception {
        String tituloAntes = jdbc.queryForObject(
                "select titulo from vacante where id = ?", String.class, vacanteId);
        long auditoriasAntes = contar("select count(*) from auditoria where entidad = 'vacante' "
                + "and entidad_id = " + vacanteId);

        // El formulario entero, tal como lo manda el panel: es el caso del lápiz que alguien
        // dejó abierto antes de que otro archivara la vacante.
        conToken(put("/api/v1/panel/vacantes/" + vacanteId), tokenEquipo, """
                {"solicitudTalentoId": %d, "titulo": "Título que no debería quedarse",
                 "descripcion": "Otra descripción", "tipoCierre": "PERMANENTE",
                 "responsableUsuarioId": 1}""".formatted(solicitudId))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail")
                        .value(org.hamcrest.Matchers.containsString("archivada")));

        conToken(post("/api/v1/panel/vacantes/" + vacanteId + "/remuneracion"), tokenEquipo, """
                {"remuneracion": {"tipo": "FIJA", "min": 4500, "moneda": "PEN"},
                 "motivo": "No debería aplicarse"}""")
                .andExpect(status().isConflict());

        conToken(post("/api/v1/panel/vacantes/" + vacanteId + "/publicacion"), tokenEquipo, null)
                .andExpect(status().isConflict());

        conToken(post("/api/v1/panel/vacantes/" + vacanteId + "/aplicacion-evaluacion"),
                tokenEquipo, "{\"aplica\": false}")
                .andExpect(status().isConflict());

        conToken(post("/api/v1/panel/vacantes/" + vacanteId + "/calificacion-automatica"),
                tokenEquipo, "{\"activa\": true}")
                .andExpect(status().isConflict());

        conToken(post("/api/v1/panel/vacantes/" + vacanteId + "/cierre"), tokenEquipo,
                "{\"motivo\":\"Otra vez\"}")
                .andExpect(status().isConflict());

        conToken(post("/api/v1/panel/vacantes/" + vacanteId + "/requisitos"), tokenEquipo, """
                {"descripcion": "Título profesional", "regla": "TIENE_TITULO"}""")
                .andExpect(status().isConflict());

        conToken(put("/api/v1/panel/vacantes/" + vacanteId + "/ficha"), tokenEquipo,
                "{\"genteEnEmpresa\": 40}")
                .andExpect(status().isConflict());

        /*
         * Las dos que cuelgan de la vacante y viven fuera de ServicioVacantesPanelImpl.
         *
         * Se llegó a ellas tarde —QA las encontró con el navegador abierto— y por eso están
         * nombradas una por una: la guarda del archivo se escribe en quince sitios y el que
         * se olvide no da ninguna señal. `calificar-tanda` es la peor de las dos porque no
         * rompe nada visible: en una archivada no queda nadie a quien calificar, así que la
         * pasada salía vacía y lo único que dejaba era una fila de auditoría sobre una
         * vacante que la pantalla declara de solo lectura.
         */
        conToken(post("/api/v1/panel/vacantes/" + vacanteId + "/calificar-tanda"),
                tokenEquipo, null)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail")
                        .value(org.hamcrest.Matchers.containsString("archivada")));

        conToken(post("/api/v1/panel/vacantes/" + vacanteId + "/barreras-criticas"),
                tokenEquipo, "{\"descripcion\": \"No debería poder definirse\"}")
                .andExpect(status().isConflict());

        // Y mover una postulación suya: la otra mitad de «no se puede reabrir el proceso».
        conToken(post("/api/v1/panel/postulaciones/" + postulacionEnCarrera + "/transiciones"),
                tokenEquipo, """
                {"estadoDestino": "PERFIL_POR_CONFIRMAR", "motivo": "No debería poder"}""")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail")
                        .value(org.hamcrest.Matchers.containsString("archivada")));

        // Nada de todo eso dejó rastro: ni un campo cambiado ni una fila de auditoría.
        assertThat(jdbc.queryForObject("select titulo from vacante where id = ?", String.class, vacanteId))
                .isEqualTo(tituloAntes);
        assertThat(jdbc.queryForObject("select estado from vacante where id = ?", String.class, vacanteId))
                .isEqualTo("CERRADA");
        assertThat(jdbc.queryForObject("select estado_codigo from postulacion where id = ?",
                String.class, postulacionEnCarrera)).isEqualTo("NO_CONTINUA");
        assertThat(contar("select count(*) from auditoria where entidad = 'vacante' and entidad_id = "
                + vacanteId))
                .as("nueve rechazos y ninguna auditoría nueva")
                .isEqualTo(auditoriasAntes);
        assertThat(contar("select count(*) from requisito_objetivo where vacante_id = " + vacanteId))
                .isZero();
        assertThat(contar("select count(*) from auditoria where accion = 'calificar_tanda' "
                + "and entidad_id = " + vacanteId))
                .as("una pasada rechazada no deja auditoría sobre una archivada")
                .isZero();
        assertThat(contar("select count(*) from barrera_critica where vacante_id = " + vacanteId))
                .isZero();
    }

    @Test
    @Order(6)
    @DisplayName("archivarla otra vez se rechaza sin duplicar la fecha")
    void archivarDosVeces() throws Exception {
        Instant primera = jdbc.queryForObject(
                "select archivada_en from vacante where id = ?", Instant.class, vacanteId);

        conToken(post("/api/v1/panel/vacantes/" + vacanteId + "/archivo"), tokenEquipo, null)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail")
                        .value(org.hamcrest.Matchers.containsString("ya está archivada")));

        assertThat(jdbc.queryForObject(
                "select archivada_en from vacante where id = ?", Instant.class, vacanteId))
                .isEqualTo(primera);
    }

    // ============ 7. Los permisos ============

    @Test
    @Order(7)
    @DisplayName("sin «cerrar_vacante» no se archiva ni se desarchiva, pero Archivadas sí se consulta")
    void sinPermisoNoSeArchiva() throws Exception {
        tokenSinArchivo = cuentaDePanelConRol("dev-archivo-sin-permiso", "RESPONSABLE_AREA");

        conToken(post("/api/v1/panel/vacantes/" + vacanteViva + "/archivo"), tokenSinArchivo, null)
                .andExpect(status().isForbidden());
        conToken(delete("/api/v1/panel/vacantes/" + vacanteId + "/archivo"), tokenSinArchivo, null)
                .andExpect(status().isForbidden());

        // Consultar Archivadas usa el permiso de lectura de siempre, y las filas llegan sin
        // los botones que no le tocan.
        conToken(get("/api/v1/panel/vacantes?archivadas=true"), tokenSinArchivo, null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(vacanteId))
                .andExpect(jsonPath("$[0].puedeArchivar").value(false))
                .andExpect(jsonPath("$[0].puedeDesarchivar").value(false));
        conToken(get("/api/v1/panel/vacantes/archivadas/conteo"), tokenSinArchivo, null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.archivadas").value(1));

        assertThat(jdbc.queryForObject(
                "select archivada_en from vacante where id = ?", Instant.class, vacanteViva))
                .isNull();
    }

    // ============ 8. Desarchivar ============

    @Test
    @Order(8)
    @DisplayName("desarchivar la devuelve a la lista habitual, CERRADA y con su proceso intacto")
    void desarchivar() throws Exception {
        conToken(delete("/api/v1/panel/vacantes/" + vacanteId + "/archivo"), tokenEquipo, null)
                .andExpect(status().isOk());

        assertThat(jdbc.queryForObject(
                "select archivada_en from vacante where id = ?", Instant.class, vacanteId))
                .isNull();
        assertThat(jdbc.queryForObject("select estado from vacante where id = ?", String.class, vacanteId))
                .as("vuelve CERRADA: desarchivar no reabre la convocatoria")
                .isEqualTo("CERRADA");
        assertThat(jdbc.queryForObject("select estado_codigo from postulacion where id = ?",
                String.class, postulacionEnCarrera))
                .as("y el candidato ve su proceso exactamente igual que antes")
                .isEqualTo("NO_CONTINUA");

        conToken(get("/api/v1/panel/vacantes"), tokenEquipo, null)
                .andExpect(jsonPath("$[?(@.id == " + vacanteId + ")]").isNotEmpty());
        conToken(get("/api/v1/panel/vacantes/archivadas/conteo"), tokenEquipo, null)
                .andExpect(jsonPath("$.archivadas").value(0));

        conToken(delete("/api/v1/panel/vacantes/" + vacanteId + "/archivo"), tokenEquipo, null)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail")
                        .value(org.hamcrest.Matchers.containsString("no está archivada")));

        assertThat(contar("""
                select count(*) from auditoria
                 where accion = 'desarchivar_vacante' and entidad_id = %d""".formatted(vacanteId)))
                .as("un desarchivo, no dos")
                .isEqualTo(1);
    }

    // ============ 9. El doble clic: dos peticiones, un solo archivo ============

    /**
     * Dos confirmaciones a la vez archivan una sola vez, y lo anotan una sola vez.
     *
     * <p><b>Por qué hace falta la base de verdad.</b> Con repositorios simulados se puede
     * comprobar que el servicio traduce «cero filas» en un rechazo, pero no que el
     * {@code update … where archivada_en is null} sea de verdad atómico: eso lo decide
     * Postgres, bloqueando la fila hasta que la primera transacción confirma y reevaluando
     * después la condición. Es justo la mitad que el doble clic rompía.
     *
     * <p>Las dos peticiones salen de dos hilos alineados con la misma barrera. Cómo se
     * interleaven da igual —puede que la segunda llegue a leer la fila ya archivada y se
     * plante antes, o que las dos lleguen al UPDATE—: el invariante que se exige es el mismo
     * en los dos caminos, <b>un éxito y una sola fila de auditoría</b>.
     */
    @Test
    @Order(9)
    @DisplayName("dos confirmaciones simultáneas archivan una vez y dejan una sola fila de auditoría")
    void elDobleClicArchivaUnaSolaVez() throws Exception {
        long auditoriasAntes = contar("select count(*) from auditoria "
                + "where accion = 'archivar_vacante' and entidad_id = " + vacanteId);
        assertThat(jdbc.queryForObject(
                "select archivada_en from vacante where id = ?", Instant.class, vacanteId))
                .as("el paso anterior la dejó en la lista habitual")
                .isNull();

        CountDownLatch alaVez = new CountDownLatch(1);
        ExecutorService hilos = Executors.newFixedThreadPool(2);
        try {
            List<Future<Integer>> respuestas = new ArrayList<>();
            for (int i = 0; i < 2; i++) {
                respuestas.add(hilos.submit(() -> {
                    alaVez.await();
                    return conToken(post("/api/v1/panel/vacantes/" + vacanteId + "/archivo"),
                            tokenEquipo, null)
                            .andReturn().getResponse().getStatus();
                }));
            }
            alaVez.countDown();

            List<Integer> estados = new ArrayList<>();
            for (Future<Integer> r : respuestas) {
                estados.add(r.get(30, TimeUnit.SECONDS));
            }

            assertThat(estados).as("una archiva y la otra se encuentra el trabajo hecho")
                    .containsExactlyInAnyOrder(200, 409);
        } finally {
            hilos.shutdownNow();
        }

        assertThat(contar("select count(*) from auditoria "
                + "where accion = 'archivar_vacante' and entidad_id = " + vacanteId))
                .as("un archivo, una fila: con dos, la traza no puede decir cuándo se archivó")
                .isEqualTo(auditoriasAntes + 1);
        assertThat(jdbc.queryForObject(
                "select archivada_en from vacante where id = ?", Instant.class, vacanteId))
                .isNotNull();

        // Y se deja como estaba para el paso siguiente.
        conToken(delete("/api/v1/panel/vacantes/" + vacanteId + "/archivo"), tokenEquipo, null)
                .andExpect(status().isOk());
    }

    // ============ 10. La red de debajo: la restricción de la V59 ============

    @Test
    @Order(10)
    @DisplayName("la base impide archivar una vacante que no esté cerrada, aunque se intente a mano")
    void laBaseNoDejaArchivarUnaViva() {
        // `vacanteViva` está en BORRADOR. Un script, una carga o un update a mano se saltan
        // el servicio; la restricción de la V59 no. Sin ella, una vacante publicada podría
        // salir de la lista del panel y seguir recibiendo postulaciones que nadie mira.
        assertThatThrownBy(() -> jdbc.update(
                "update vacante set archivada_en = now() where id = ?", vacanteViva))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("vacante_archivada_solo_si_cerrada");
    }

    // ---------- ayudas ----------

    private long contar(String sql) {
        Long cuantos = jdbc.queryForObject(sql, Long.class);
        return cuantos == null ? 0 : cuantos;
    }

    private long crearSolicitudAprobada(Long areaId, long puestoId) throws Exception {
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
                "{\"motivo\":\"Justificada: hay presupuesto\"}")
                .andExpect(status().isOk());
        return id;
    }

    private long crearVacante(long solicitud, String titulo) throws Exception {
        return Long.parseLong(leer(conToken(post("/api/v1/panel/vacantes"), tokenEquipo, """
                {"solicitudTalentoId": %d, "titulo": "%s", "descripcion": "Lleva la sede",
                 "tipoCierre": "PERMANENTE", "responsableUsuarioId": 1}"""
                .formatted(solicitud, titulo))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString(), "id"));
    }

    /**
     * Una cuenta de panel con un rol concreto, sembrada a mano.
     *
     * <p>El {@code dev-login} solo crea sola la primera cuenta que entra (bootstrap), así que
     * la segunda se inserta con su rol real: lo que se prueba es el reparto de permisos de la
     * base, y un mapa inventado no demostraría nada.
     */
    private String cuentaDePanelConRol(String renaserOsId, String rolCodigo) throws Exception {
        Long personaId = jdbc.queryForObject(
                "insert into persona (nombre, apellidos) values ('QA', ?) returning id",
                Long.class, renaserOsId);
        Long usuarioId = jdbc.queryForObject("""
                insert into usuario (organizacion_id, persona_id, usuario_renaser_os_id,
                                     es_equipo, es_activo)
                values (1, ?, ?, true, true) returning id""",
                Long.class, personaId, renaserOsId);
        jdbc.update("""
                insert into usuario_rol (usuario_id, rol_id)
                select ?, id from rol where organizacion_id = 1 and codigo = ?""",
                usuarioId, rolCodigo);

        return leer(mvc.perform(post("/api/v1/panel/auth/dev-login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"usuarioRenaserOsId\":\"%s\"}".formatted(renaserOsId)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(), "token");
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
