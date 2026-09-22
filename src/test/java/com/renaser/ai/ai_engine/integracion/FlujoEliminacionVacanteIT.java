package com.renaser.ai.ai_engine.integracion;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.renaser.ai.ai_engine.integracion.soporte.ImagenesDeContenedores;
import com.renaser.ai.ai_engine.postulacion.service.PaseAutomatico;
import com.renaser.ai.ai_engine.prueba.service.ServicioPrueba;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Eliminar una vacante por borrado lógico, contra la base de verdad.
 *
 * <p>{@code EliminarVacanteTest} ya cubre las decisiones del servicio con los repositorios
 * simulados. Lo que no puede cubrir, y por eso existe esta prueba, es todo lo que solo tiene
 * respuesta cuando hay una base, un HTTP y dos hilos de por medio:
 *
 * <ul>
 *   <li><b>Que de verdad desaparezca de todas partes.</b> Que no salga en {@code /admin} no
 *       es un filtro de pantalla: es que no sale de la base. Y lo mismo en Archivadas, en su
 *       contador, en el tablón, en el detalle público, en «Mis procesos» y en el ranking.
 *   <li><b>La transacción conjunta.</b> Si el cierre de una postulación falla, no puede
 *       quedar la vacante marcada ni la solicitud liberada. Con un mock, «la misma
 *       transacción» es una palabra; aquí se rompe a propósito y se mira la base.
 *   <li><b>Dos eliminaciones simultáneas</b>, que es el doble clic de verdad: una elimina y
 *       la otra se encuentra el trabajo hecho, sin duplicar cierres ni avisos.
 *   <li><b>Los permisos con las filas reales de {@code rol_permiso}</b>: 403 sin el permiso
 *       nuevo y 404 fuera del alcance, y no con un mapa inventado.
 *   <li><b>Los procesos automáticos</b>, que ningún navegador puede acreditar.
 * </ul>
 *
 * <p>Los pasos van en orden y comparten estado: la vacante que se publica en el primero es la
 * que se elimina en el cuarto y la que ya no existe en todos los demás.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@DisplayName("Una vacante se elimina por borrado lógico y deja de existir para todos")
public class FlujoEliminacionVacanteIT {

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
    @Autowired PaseAutomatico paseAutomatico;
    @Autowired ServicioPrueba prueba;
    final ObjectMapper json = new ObjectMapper();

    private static final String TITULO = "Coordinador de sede";
    private static final String MOTIVO = "Se creó con el puesto equivocado";
    private static final String CLAVE = "Demo12345!";

    static String tokenEquipo;
    /** Una cuenta de panel que ve las vacantes y NO tiene `eliminar_vacante`. */
    static String tokenSinEliminar;
    /** Otra que sí lo tiene, pero acotado a las vacantes que dirige (y no dirige ninguna). */
    static String tokenAcotado;

    static long areaId;
    static long puestoId;
    static long plantillaEvaluacionId;
    static long versionPruebaId;

    static long solicitudObjetivo;
    /** La que se elimina. */
    static long vacanteObjetivo;
    /** La que se queda: demuestra que la lista habitual sigue entera. */
    static long vacanteViva;
    /** La del paso de concurrencia. */
    static long vacanteDoble;

    static long postulacionA;
    static long postulacionB;
    static long postulacionNoContinua;
    static long postulacionContratada;
    static String uuidA;
    static String tokenCandidatoA;
    static long usuarioA;
    static long usuarioB;
    /** La cuenta que intenta postular cuando la vacante ya no existe. */
    static String tokenCandidatoTarde;
    static long avisoViejoDeA;

    // ============ 1. El terreno que pide la spec ============

    @Test
    @Order(1)
    @DisplayName("una publicada con 2 en carrera, 1 NO_CONTINUA y 1 CONTRATADO, y otra viva al lado")
    void elTerreno() throws Exception {
        tokenEquipo = leer(mvc.perform(post("/api/v1/panel/auth/dev-login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"usuarioRenaserOsId\":\"dev-eliminacion\"}"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(), "token");

        jdbc.update("INSERT INTO area (organizacion_id, nombre, es_activa) VALUES (1, 'Operaciones', true)");
        areaId = jdbc.queryForObject("SELECT id FROM area LIMIT 1", Long.class);
        puestoId = Long.parseLong(leer(conToken(post("/api/v1/panel/puestos"), tokenEquipo, """
                {"nombre": "Coordinador", "nivelPuestoCodigo": "EJECUCION",
                 "familiaCodigo": "OPERACIONES"}""")
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString(), "id"));
        plantillaEvaluacionId = jdbc.queryForObject(
                "select id from plantilla_evaluacion where nivel_puesto_codigo = 'EJECUCION'",
                Long.class);
        versionPruebaId = armarUnaPruebaValida();

        solicitudObjetivo = crearSolicitudAprobada();
        vacanteObjetivo = publicarVacante(solicitudObjetivo, TITULO);
        vacanteViva = publicarVacante(crearSolicitudAprobada(), "Analista de turno");

        usuarioA = crearCuentaDeCandidato("ana.elimina@ejemplo.pe");
        tokenCandidatoA = entrarAlPortal("ana.elimina@ejemplo.pe");
        usuarioB = crearCuentaDeCandidato("bruno.elimina@ejemplo.pe");
        long usuarioC = crearCuentaDeCandidato("carla.elimina@ejemplo.pe");
        long usuarioD = crearCuentaDeCandidato("diego.elimina@ejemplo.pe");
        crearCuentaDeCandidato("elena.elimina@ejemplo.pe");
        tokenCandidatoTarde = entrarAlPortal("elena.elimina@ejemplo.pe");

        // Las cuatro postulaciones se siembran a mano: lo que se prueba aquí es la
        // eliminación, no el alta de una candidatura —que tiene sus propias pruebas—, y así
        // los cuatro estados quedan exactamente donde los pide la spec.
        postulacionA = postulacion(usuarioA, vacanteObjetivo, "PERFIL_POR_CONFIRMAR", null);
        postulacionB = postulacion(usuarioB, vacanteObjetivo, "PRUEBA_TURNO_CANDIDATO", null);
        postulacionNoContinua = postulacion(usuarioC, vacanteObjetivo, "NO_CONTINUA",
                "DECISION_PERSONA");
        postulacionContratada = postulacion(usuarioD, vacanteObjetivo, "CONTRATADO", null);
        uuidA = jdbc.queryForObject("select uuid::text from postulacion where id = ?",
                String.class, postulacionA);

        // Y un aviso VIEJO de esa misma vacante, con sus dos enlaces puestos: el que tiene
        // que quedarse escrito y dejar de llevar a ninguna parte cuando la vacante se vaya.
        avisoViejoDeA = jdbc.queryForObject("""
                insert into aviso_portal (usuario_id, organizacion_id, tipo, titulo, cuerpo,
                                          postulacion_id, vacante_id)
                values (?, 1, 'VACANTE_ACTUALIZADA', 'Cambió la convocatoria',
                        'Se corrigió el horario', ?, ?) returning id""",
                Long.class, usuarioA, postulacionA, vacanteObjetivo);

        assertThat(jdbc.queryForObject("select estado from solicitud_talento where id = ?",
                String.class, solicitudObjetivo))
                .as("crear la vacante dejó su solicitud ocupada")
                .isEqualTo("CON_VACANTE");
    }

    // ============ 2. Sin motivo no pasa nada (AC-18, la mitad del API) ============

    @Test
    @Order(2)
    @DisplayName("con el motivo vacío o solo espacios contesta 400 y no elimina, cierra ni avisa")
    void sinMotivoNoPasaNada() throws Exception {
        long avisosAntes = contar("select count(*) from aviso_portal");

        conToken(delete("/api/v1/panel/vacantes/" + vacanteObjetivo), tokenEquipo,
                "{\"motivo\":\"\"}")
                .andExpect(status().isBadRequest());
        conToken(delete("/api/v1/panel/vacantes/" + vacanteObjetivo), tokenEquipo,
                "{\"motivo\":\"   \"}")
                .andExpect(status().isBadRequest());

        assertThat(jdbc.queryForObject("select eliminada_en from vacante where id = ?",
                Instant.class, vacanteObjetivo)).isNull();
        assertThat(jdbc.queryForObject("select estado_codigo from postulacion where id = ?",
                String.class, postulacionA)).isEqualTo("PERFIL_POR_CONFIRMAR");
        assertThat(contar("select count(*) from aviso_portal")).isEqualTo(avisosAntes);
        assertThat(contar("select count(*) from auditoria where accion = 'eliminar_vacante'"))
                .isZero();
    }

    // ============ 3. Los permisos (AC-19) ============

    @Test
    @Order(3)
    @DisplayName("sin «eliminar_vacante» la API contesta 403; fuera del alcance del rol, 404")
    void losPermisos() throws Exception {
        tokenSinEliminar = cuentaDePanelConRol("qa-sin-eliminar", "RESPONSABLE_AREA");

        conToken(delete("/api/v1/panel/vacantes/" + vacanteObjetivo), tokenSinEliminar,
                "{\"motivo\":\"" + MOTIVO + "\"}")
                .andExpect(status().isForbidden());

        // Y su lista no ofrece la papelera: el botón y el API dicen lo mismo.
        conToken(get("/api/v1/panel/vacantes"), tokenSinEliminar, null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.id == " + vacanteObjetivo + ")].puedeEliminar")
                        .value(org.hamcrest.Matchers.everyItem(
                                org.hamcrest.Matchers.is(false))));

        // Un rol con el permiso acotado a SUS vacantes, y que no dirige ninguna: lo que no
        // alcanza no es «no puedes», es «no existe» — un 403 confirmaría que la vacante está
        // ahí, y de ahí se sondea qué ids hay al otro lado.
        tokenAcotado = cuentaConPermisoAcotado();
        conToken(delete("/api/v1/panel/vacantes/" + vacanteObjetivo), tokenAcotado,
                "{\"motivo\":\"" + MOTIVO + "\"}")
                .andExpect(status().isNotFound());

        assertThat(jdbc.queryForObject("select eliminada_en from vacante where id = ?",
                Instant.class, vacanteObjetivo)).isNull();
    }

    // ============ 4. La transacción conjunta: si una parte falla, no se aplica ninguna ============

    /**
     * El rollback conjunto, roto a propósito desde la base.
     *
     * <p>Es la única forma honesta de probarlo: con todo funcionando, «la misma transacción»
     * es una afirmación que nunca se ejerce. El disparador hace fallar el cierre de la
     * postulación —que es el paso de en medio— y lo que se exige es que la marca de
     * eliminada, que se escribió ANTES, tampoco quede, y que la solicitud siga ocupada.
     */
    @Test
    @Order(4)
    @DisplayName("si el cierre de una postulación falla, ni se marca eliminada ni se libera la solicitud")
    void siFallaUnaParteNoSeAplicaNinguna() throws Exception {
        jdbc.execute("""
                create or replace function qa_rompe_el_cierre() returns trigger as $$
                begin raise exception 'QA: el cierre de la postulación falla'; end;
                $$ language plpgsql""");
        jdbc.execute("""
                create trigger qa_rompe_el_cierre before update on postulacion
                for each row when (new.estado_codigo = 'CERRADA')
                execute function qa_rompe_el_cierre()""");
        try {
            conToken(delete("/api/v1/panel/vacantes/" + vacanteObjetivo), tokenEquipo,
                    "{\"motivo\":\"" + MOTIVO + "\"}")
                    .andExpect(status().is5xxServerError());
        } finally {
            jdbc.execute("drop trigger qa_rompe_el_cierre on postulacion");
            jdbc.execute("drop function qa_rompe_el_cierre()");
        }

        assertThat(jdbc.queryForObject("select eliminada_en from vacante where id = ?",
                Instant.class, vacanteObjetivo))
                .as("la marca se escribió primero, y el fallo de después tiene que deshacerla")
                .isNull();
        assertThat(jdbc.queryForObject("select estado from solicitud_talento where id = ?",
                String.class, solicitudObjetivo))
                .as("la solicitud no puede quedar libre respaldando una vacante que sigue ahí")
                .isEqualTo("CON_VACANTE");
        assertThat(jdbc.queryForObject("select estado_codigo from postulacion where id = ?",
                String.class, postulacionA)).isEqualTo("PERFIL_POR_CONFIRMAR");
        assertThat(contar("select count(*) from auditoria where accion = 'eliminar_vacante'"))
                .isZero();
        // Y a nadie le llegó una campana de una eliminación que no ocurrió. Es lo que obliga
        // a bajar todo a la base antes de publicar el primer aviso: los avisos van en
        // transacción propia y confirman aunque esta se deshaga.
        assertThat(contar("select count(*) from aviso_portal where tipo = 'VACANTE_ELIMINADA'"))
                .as("la eliminación no ocurrió: nadie puede haberse enterado de ella")
                .isZero();
        // Y la vacante sigue apareciendo en el panel, que es lo que ve quien lo intentó.
        conToken(get("/api/v1/panel/vacantes"), tokenEquipo, null)
                .andExpect(jsonPath("$[?(@.id == " + vacanteObjetivo + ")]").isNotEmpty());
    }

    // ============ 5. Se elimina (AC-15) ============

    @Test
    @Order(5)
    @DisplayName("cierra solo a quienes seguían en carrera, les avisa sin enlace y libera la solicitud")
    void seElimina() throws Exception {
        long correosAntes = contar("select count(*) from correo_enviado");

        conToken(delete("/api/v1/panel/vacantes/" + vacanteObjetivo), tokenEquipo,
                "{\"motivo\":\"" + MOTIVO + "\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.postulacionesCerradas").value(2))
                .andExpect(jsonPath("$.postulantesAvisados").value(2));

        assertThat(jdbc.queryForObject("select eliminada_en from vacante where id = ?",
                Instant.class, vacanteObjetivo)).isNotNull();
        assertThat(jdbc.queryForObject("select estado from vacante where id = ?",
                String.class, vacanteObjetivo))
                .as("eliminar no es un estado: cómo iba la convocatoria es parte de lo que se conserva")
                .isEqualTo("PUBLICADA");

        // Las dos que seguían dentro, cerradas con el motivo nuevo.
        for (long id : List.of(postulacionA, postulacionB)) {
            assertThat(jdbc.queryForObject("select estado_codigo from postulacion where id = ?",
                    String.class, id)).isEqualTo("CERRADA");
            assertThat(jdbc.queryForObject("select motivo_cierre from postulacion where id = ?",
                    String.class, id)).isEqualTo("VACANTE_ELIMINADA");
        }
        // Las dos que ya habían terminado, intactas: su proceso acabó por otra razón.
        assertThat(jdbc.queryForObject("select estado_codigo from postulacion where id = ?",
                String.class, postulacionNoContinua)).isEqualTo("NO_CONTINUA");
        assertThat(jdbc.queryForObject("select motivo_cierre from postulacion where id = ?",
                String.class, postulacionNoContinua)).isEqualTo("DECISION_PERSONA");
        assertThat(jdbc.queryForObject("select estado_codigo from postulacion where id = ?",
                String.class, postulacionContratada)).isEqualTo("CONTRATADO");

        // Es una decisión de PERSONA, no del sistema, y su motivo no lleva la coletilla
        // automática: el correo se calló porque la noticia sale por la campana, no porque
        // alguien decidiera no contárselo.
        assertThat(jdbc.queryForObject("""
                select es_sistema from transicion_estado
                 where postulacion_id = ? and estado_nuevo_codigo = 'CERRADA'""",
                Boolean.class, postulacionA)).isFalse();
        assertThat(jdbc.queryForObject("""
                select motivo from transicion_estado
                 where postulacion_id = ? and estado_nuevo_codigo = 'CERRADA'""",
                String.class, postulacionA))
                .contains(MOTIVO)
                .doesNotContain("sin avisar al candidato");

        // Dos avisos, uno por persona en carrera, sin enlace a ninguna parte.
        assertThat(contar("""
                select count(*) from aviso_portal
                 where tipo = 'VACANTE_ELIMINADA' and postulacion_id is null
                   and vacante_id is null""")).isEqualTo(2);
        assertThat(jdbc.queryForObject("""
                select titulo from aviso_portal where tipo = 'VACANTE_ELIMINADA'
                 and usuario_id = ?""", String.class, usuarioA))
                .isEqualTo("Se retiró la vacante «" + TITULO + "»");
        assertThat(contar("select count(*) from aviso_portal where tipo = 'VACANTE_ELIMINADA' "
                + "and usuario_id in (select usuario_id from postulacion where id in ("
                + postulacionNoContinua + ", " + postulacionContratada + "))"))
                .as("a quien ya había terminado no se le cuenta nada")
                .isZero();

        assertThat(contar("select count(*) from correo_enviado"))
                .as("ningún correo nuevo: la noticia sale solo por la campana")
                .isEqualTo(correosAntes);

        // La solicitud vuelve a estar libre, y queda anotado por qué.
        assertThat(jdbc.queryForObject("select estado from solicitud_talento where id = ?",
                String.class, solicitudObjetivo)).isEqualTo("ABIERTA");
        assertThat(contar("""
                select count(*) from auditoria
                 where accion = 'eliminar_vacante' and entidad = 'vacante' and entidad_id = %d
                   and motivo = '%s'""".formatted(vacanteObjetivo, MOTIVO))).isEqualTo(1);
    }

    // ============ 6. Ya no aparece en ninguna parte (AC-16) ============

    @Test
    @Order(6)
    @DisplayName("desaparece del panel, de Archivadas y su contador, del tablón, del detalle "
            + "público, de «Mis procesos» y del ranking")
    void desapareceDeTodasPartes() throws Exception {
        conToken(get("/api/v1/panel/vacantes"), tokenEquipo, null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.id == " + vacanteObjetivo + ")]").isEmpty())
                .andExpect(jsonPath("$[?(@.id == " + vacanteViva + ")]").isNotEmpty());

        conToken(get("/api/v1/panel/vacantes?archivadas=true"), tokenEquipo, null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.id == " + vacanteObjetivo + ")]").isEmpty());
        conToken(get("/api/v1/panel/vacantes/archivadas/conteo"), tokenEquipo, null)
                .andExpect(jsonPath("$.archivadas").value(0));

        conToken(get("/api/v1/panel/vacantes/" + vacanteObjetivo), tokenEquipo, null)
                .andExpect(status().isNotFound());
        conToken(get("/api/v1/panel/vacantes/" + vacanteObjetivo
                + "/ranking?etapa=PERFIL_INTEGRAL"), tokenEquipo, null)
                .andExpect(status().isNotFound());
        conToken(get("/api/v1/panel/vacantes/" + vacanteObjetivo + "/requisitos"), tokenEquipo, null)
                .andExpect(status().isNotFound());
        conToken(get("/api/v1/panel/vacantes/" + vacanteObjetivo + "/ficha"), tokenEquipo, null)
                .andExpect(status().isNotFound());

        // El tablón público y el detalle, sin sesión de por medio.
        mvc.perform(get("/api/v1/portal/vacantes"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.id == " + vacanteObjetivo + ")]").isEmpty())
                .andExpect(jsonPath("$[?(@.id == " + vacanteViva + ")]").isNotEmpty());
        mvc.perform(get("/api/v1/portal/vacantes/" + vacanteObjetivo))
                .andExpect(status().isNotFound());
        mvc.perform(get("/api/v1/portal/vacantes/" + vacanteObjetivo + "/consentimiento"))
                .andExpect(status().isNotFound());

        // Y el proceso de quien postuló: ni en su lista ni por su enlace directo.
        mvc.perform(get("/api/v1/portal/postulaciones")
                        .header("Authorization", "Bearer " + tokenCandidatoA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.uuid == '" + uuidA + "')]").isEmpty());
        mvc.perform(get("/api/v1/portal/postulaciones/" + uuidA)
                        .header("Authorization", "Bearer " + tokenCandidatoA))
                .andExpect(status().isNotFound());
    }

    // ============ 7. No se puede postular a una eliminada (AC-17) ============

    @Test
    @Order(7)
    @DisplayName("el formulario abierto desde antes no crea la postulación: se rechaza")
    void noSeAdmitenPostulacionesNuevas() throws Exception {
        long postulacionesAntes = contar(
                "select count(*) from postulacion where vacante_id = " + vacanteObjetivo);

        mvc.perform(multipart("/api/v1/portal/postulaciones")
                        .file(unCurriculum())
                        .param("vacanteId", String.valueOf(vacanteObjetivo))
                        .param("resultadoOrgulloso", "Abrí dos sedes en un trimestre")
                        .param("aceptaTratamiento", "true")
                        .header("Authorization", "Bearer " + tokenCandidatoTarde))
                .andExpect(status().isNotFound());

        assertThat(contar("select count(*) from postulacion where vacante_id = "
                + vacanteObjetivo)).isEqualTo(postulacionesAntes);
    }

    // ============ 8. Los guardas de las entregas anteriores se extienden (punto 9) ============

    @Test
    @Order(8)
    @DisplayName("editar, cambiar el sueldo, publicar, reconfigurar, archivar o mover su gente: 404")
    void lasMutacionesYaNoEncuentranLaVacante() throws Exception {
        String tituloAntes = jdbc.queryForObject("select titulo from vacante where id = ?",
                String.class, vacanteObjetivo);

        conToken(put("/api/v1/panel/vacantes/" + vacanteObjetivo), tokenEquipo, """
                {"solicitudTalentoId": %d, "titulo": "Título que no debería quedarse",
                 "descripcion": "Otra descripción", "tipoCierre": "PERMANENTE",
                 "responsableUsuarioId": 1}""".formatted(solicitudObjetivo))
                .andExpect(status().isNotFound());
        conToken(post("/api/v1/panel/vacantes/" + vacanteObjetivo + "/remuneracion"), tokenEquipo, """
                {"remuneracion": {"tipo": "FIJA", "min": 4500, "moneda": "PEN"},
                 "motivo": "No debería aplicarse"}""")
                .andExpect(status().isNotFound());
        conToken(post("/api/v1/panel/vacantes/" + vacanteObjetivo + "/publicacion"),
                tokenEquipo, null).andExpect(status().isNotFound());
        conToken(post("/api/v1/panel/vacantes/" + vacanteObjetivo + "/aplicacion-evaluacion"),
                tokenEquipo, "{\"aplica\": false}").andExpect(status().isNotFound());
        conToken(post("/api/v1/panel/vacantes/" + vacanteObjetivo + "/cierre"), tokenEquipo,
                "{\"motivo\":\"Otra vez\"}").andExpect(status().isNotFound());
        conToken(post("/api/v1/panel/vacantes/" + vacanteObjetivo + "/archivo"),
                tokenEquipo, null).andExpect(status().isNotFound());
        conToken(delete("/api/v1/panel/vacantes/" + vacanteObjetivo + "/archivo"),
                tokenEquipo, null).andExpect(status().isNotFound());
        conToken(put("/api/v1/panel/vacantes/" + vacanteObjetivo + "/ficha"), tokenEquipo,
                "{\"genteEnEmpresa\": 40}").andExpect(status().isNotFound());
        conToken(post("/api/v1/panel/postulaciones/" + postulacionContratada + "/transiciones"),
                tokenEquipo, """
                {"estadoDestino": "CERRADA", "motivo": "No debería poder moverse"}""")
                .andExpect(status().isNotFound());

        assertThat(jdbc.queryForObject("select titulo from vacante where id = ?",
                String.class, vacanteObjetivo))
                .as("ninguna de las puertas dejó nada escrito")
                .isEqualTo(tituloAntes);
        assertThat(jdbc.queryForObject("select estado_codigo from postulacion where id = ?",
                String.class, postulacionContratada)).isEqualTo("CONTRATADO");

        // Y una sesión de simulación no puede colgarse de ella.
        conToken(post("/api/v1/panel/sesiones-simulacion"), tokenEquipo, """
                {"fechaHora": "2030-01-15T15:00:00Z", "duracionMinutos": 90, "cupo": 6,
                 "modalidad": "GRUPAL", "vacanteIds": [%d]}""".formatted(vacanteObjetivo))
                .andExpect(status().isNotFound());
    }

    // ============ 9. Repetirla no vuelve a hacer nada (AC-20) ============

    @Test
    @Order(9)
    @DisplayName("repetir la eliminación contesta 404 y no vuelve a cerrar, auditar ni avisar")
    void repetirlaNoHaceNada() throws Exception {
        long avisosAntes = contar("select count(*) from aviso_portal");
        long auditoriasAntes = contar(
                "select count(*) from auditoria where accion = 'eliminar_vacante'");
        long transicionesAntes = contar("select count(*) from transicion_estado "
                + "where postulacion_id in (" + postulacionA + ", " + postulacionB + ")");

        conToken(delete("/api/v1/panel/vacantes/" + vacanteObjetivo), tokenEquipo,
                "{\"motivo\":\"" + MOTIVO + " (otra vez)\"}")
                .andExpect(status().isNotFound());

        assertThat(contar("select count(*) from aviso_portal")).isEqualTo(avisosAntes);
        assertThat(contar("select count(*) from auditoria where accion = 'eliminar_vacante'"))
                .isEqualTo(auditoriasAntes);
        assertThat(contar("select count(*) from transicion_estado where postulacion_id in ("
                + postulacionA + ", " + postulacionB + ")")).isEqualTo(transicionesAntes);
    }

    // ============ 10. La solicitud liberada respalda otra vacante (AC-21) ============

    @Test
    @Order(10)
    @DisplayName("la solicitud vuelve a ofrecerse en Crear vacante, y el título se puede reutilizar")
    void laSolicitudLiberadaRespaldaOtra() throws Exception {
        conToken(get("/api/v1/panel/solicitudes"), tokenEquipo, null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.id == " + solicitudObjetivo + ")].estado")
                        .value(org.hamcrest.Matchers.hasItem("ABIERTA")));

        // Y crea de verdad, con el MISMO título: el borrado lógico no reserva nombres.
        long nueva = Long.parseLong(leer(conToken(post("/api/v1/panel/vacantes"), tokenEquipo, """
                {"solicitudTalentoId": %d, "titulo": "%s", "descripcion": "La correcta",
                 "tipoCierre": "PERMANENTE", "responsableUsuarioId": 1}"""
                .formatted(solicitudObjetivo, TITULO))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString(), "id"));

        assertThat(nueva).isNotEqualTo(vacanteObjetivo);
        conToken(get("/api/v1/panel/vacantes"), tokenEquipo, null)
                .andExpect(jsonPath("$[?(@.id == " + nueva + ")]").isNotEmpty());
        assertThat(jdbc.queryForObject("select estado from solicitud_talento where id = ?",
                String.class, solicitudObjetivo)).isEqualTo("CON_VACANTE");
    }

    // ============ 11. Los avisos viejos se quedan, y sin enlace (punto 6) ============

    @Test
    @Order(11)
    @DisplayName("el aviso anterior sigue en la campana y deja de llevar a ninguna parte")
    void losAvisosViejosSeQuedanSinEnlace() throws Exception {
        // La fila no se borra: lo que se le dijo a esa persona ocurrió, y tiene que seguir
        // pudiendo leerse. Lo que se apaga es el enlace, que llevaría a un 404.
        assertThat(contar("select count(*) from aviso_portal where id = " + avisoViejoDeA))
                .isEqualTo(1);

        mvc.perform(get("/api/v1/portal/avisos")
                        .header("Authorization", "Bearer " + tokenCandidatoA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.avisos[?(@.id == " + avisoViejoDeA + ")]").isNotEmpty())
                .andExpect(jsonPath("$.avisos[?(@.id == " + avisoViejoDeA + ")].vacanteId")
                        .value(org.hamcrest.Matchers.everyItem(
                                org.hamcrest.Matchers.nullValue())))
                .andExpect(jsonPath("$.avisos[?(@.id == " + avisoViejoDeA + ")].postulacionUuid")
                        .value(org.hamcrest.Matchers.everyItem(
                                org.hamcrest.Matchers.nullValue())))
                .andExpect(jsonPath("$.avisos[?(@.tipo == 'VACANTE_ELIMINADA')]").isNotEmpty());
    }

    // ============ 12. Los procesos automáticos la ignoran ============

    /**
     * Lo que ningún navegador puede acreditar: que los procesos que corren solos no la toquen.
     *
     * <p>El pase automático se prueba con una postulación puesta a mano en el estado del que
     * parte. No es un caso inventado: un trabajo de IA que empezó antes de la eliminación
     * puede terminar después y llegar hasta ahí, y sin la guarda la máquina de estados
     * reventaría —o peor, movería a alguien dentro de una convocatoria que ya no existe—.
     */
    @Test
    @Order(12)
    @DisplayName("el pase automático y la criba no procesan una vacante eliminada")
    void losProcesosAutomaticosLaIgnoran() throws Exception {
        jdbc.update("update vacante set calificacion_automatica = true where id = ?",
                vacanteObjetivo);
        long rezagada = postulacion(crearCuentaDeCandidato("fabio.elimina@ejemplo.pe"),
                vacanteObjetivo, "PERFIL_POR_CONFIRMAR", null);
        long transicionesAntes = contar(
                "select count(*) from transicion_estado where postulacion_id = " + rezagada);

        paseAutomatico.avanzarSiToca(rezagada);

        assertThat(jdbc.queryForObject("select estado_codigo from postulacion where id = ?",
                String.class, rezagada))
                .as("no se mueve, y tampoco revienta: se anota y se deja donde está")
                .isEqualTo("PERFIL_POR_CONFIRMAR");
        assertThat(contar("select count(*) from transicion_estado where postulacion_id = "
                + rezagada)).isEqualTo(transicionesAntes);

        // Y la criba del panel ni siquiera encuentra la vacante que tendría que calificar.
        conToken(post("/api/v1/panel/vacantes/" + vacanteObjetivo + "/calificar-tanda"), tokenEquipo, null)
                .andExpect(status().isNotFound());
    }

    /**
     * El barrido de pruebas vencidas, con una prueba abierta que se quedó a medias.
     *
     * <p>Es el caso que la eliminación crea sin querer: alguien estaba rindiendo su prueba
     * cuando el equipo retiró la vacante, así que su postulación se cerró y su intento se
     * quedó abierto. Ese intento vence igual, y el barrido lo ve cada minuto. Sin la guarda,
     * intentaría moverlo a «calificando», la máquina de estados se plantaría —de un estado
     * final no se sale— y la excepción se llevaría por delante a todos los demás intentos de
     * la misma tanda, cada minuto y sin más señal que un log.
     */
    @Test
    @Order(13)
    @DisplayName("una prueba vencida de una postulación ya cerrada no tumba el barrido")
    void elBarridoDeVencidasNoSeCaeConLaEliminada() {
        Long versionPrueba = versionPruebaId;
        jdbc.update("""
                insert into intento_prueba (postulacion_id, version_plantilla_prueba_id,
                                            iniciado_en, vence_en)
                values (?, ?, now() - interval '3 hours', now() - interval '1 hour')""",
                postulacionB, versionPrueba);

        // No lanza, que es la mitad de lo que se prueba: si lanzara, el sondeo lo anotaría y
        // los intentos vivos de otras vacantes se quedarían sin entregar.
        prueba.entregarVencidos();

        assertThat(jdbc.queryForObject(
                "select entregado_en from intento_prueba where postulacion_id = ?",
                Instant.class, postulacionB))
                .as("no se entrega la prueba de quien ya no está en carrera")
                .isNull();
        assertThat(jdbc.queryForObject("select estado_codigo from postulacion where id = ?",
                String.class, postulacionB)).isEqualTo("CERRADA");
    }

    // ============ 14. Dos eliminaciones simultáneas ============

    /**
     * El doble clic de verdad, con dos hilos alineados por la misma barrera.
     *
     * <p>Aquí duplicar no sería solo una fila de auditoría de más: serían dos campanas por el
     * mismo hecho en el portal de la misma persona. La condición dentro del UPDATE es lo que
     * ordena a las dos peticiones, y por eso se escribe ANTES de cerrar a nadie.
     */
    @Test
    @Order(14)
    @DisplayName("dos eliminaciones simultáneas eliminan una vez: un cierre y un aviso por persona")
    void dosEliminacionesSimultaneas() throws Exception {
        vacanteDoble = publicarVacante(crearSolicitudAprobada(), "Supervisor de turno");
        long uno = postulacion(crearCuentaDeCandidato("gina.elimina@ejemplo.pe"), vacanteDoble,
                "PERFIL_POR_CONFIRMAR", null);
        long dos = postulacion(crearCuentaDeCandidato("hugo.elimina@ejemplo.pe"), vacanteDoble,
                "PERFIL_POR_CONFIRMAR", null);
        long avisosAntes = contar(
                "select count(*) from aviso_portal where tipo = 'VACANTE_ELIMINADA'");

        CountDownLatch alaVez = new CountDownLatch(1);
        ExecutorService hilos = Executors.newFixedThreadPool(2);
        List<Integer> estados = new ArrayList<>();
        try {
            List<Future<Integer>> respuestas = new ArrayList<>();
            for (int i = 0; i < 2; i++) {
                respuestas.add(hilos.submit(() -> {
                    alaVez.await();
                    return conToken(delete("/api/v1/panel/vacantes/" + vacanteDoble),
                            tokenEquipo, "{\"motivo\":\"Duplicada por error\"}")
                            .andReturn().getResponse().getStatus();
                }));
            }
            alaVez.countDown();
            for (Future<Integer> r : respuestas) {
                estados.add(r.get(60, TimeUnit.SECONDS));
            }
        } finally {
            hilos.shutdownNow();
        }

        assertThat(estados).as("una elimina y la otra se encuentra el trabajo hecho")
                .containsExactlyInAnyOrder(200, 404);
        assertThat(contar("select count(*) from auditoria where accion = 'eliminar_vacante' "
                + "and entidad_id = " + vacanteDoble)).isEqualTo(1);
        assertThat(contar("select count(*) from aviso_portal where tipo = 'VACANTE_ELIMINADA'"))
                .as("dos personas dentro, dos avisos: ni uno más")
                .isEqualTo(avisosAntes + 2);
        for (long id : List.of(uno, dos)) {
            assertThat(contar("select count(*) from transicion_estado where postulacion_id = "
                    + id + " and estado_nuevo_codigo = 'CERRADA'")).isEqualTo(1);
        }
    }

    // ============ 15. Postular y eliminar a la vez ============

    /**
     * La otra carrera de AC-17, y la que de verdad dejaba basura: postular contra eliminar.
     *
     * <p>Dos eliminaciones a la vez solo se estorban entre ellas. Esta es peor: la
     * transacción de postular es LARGA —sube el currículum, firma el consentimiento, crea la
     * evaluación— y la eliminación cabe entera dentro de ese hueco. Cuando eso pasaba,
     * quedaba una postulación abierta sobre una convocatoria retirada: sin cierre, sin
     * aviso, fuera de «Mis procesos» de la persona y con 404 en el ranking del panel. Un
     * proceso que nadie puede ver y nadie puede cerrar, y una persona con su 201 de
     * confirmación en la mano.
     *
     * <p><b>Por qué la eliminación sale un instante después.</b> Con las dos peticiones
     * saliendo a la vez, quién llega antes lo decide el planificador y la carrera se
     * reproduce unas veces sí y otras no. El retraso mínimo pone a la de postular DENTRO de
     * su transacción —ya pasada la lectura de la vacante— cuando la eliminación empieza, que
     * es exactamente el hueco que había que cerrar.
     *
     * <p><b>Los dos desenlaces válidos</b>, y no uno: si la postulación entró, tiene que
     * salir cerrada y avisada como las demás; si no entró, el portal contesta 404 y no deja
     * rastro. Lo que no puede quedar, de ninguna de las dos formas, es una postulación
     * abierta colgando de una vacante eliminada.
     */
    @Test
    @Order(15)
    @DisplayName("postular y eliminar a la vez no deja una postulación abierta sobre la eliminada")
    void postularYEliminarALaVez() throws Exception {
        for (int intento = 0; intento < 3; intento++) {
            long vacante = publicarVacante(crearSolicitudAprobada(), "Supervisor de carrera " + intento);
            String correo = "carrera" + intento + ".elimina@ejemplo.pe";
            long usuario = crearCuentaDeCandidato(correo);
            String tokenCandidato = entrarAlPortal(correo);

            CountDownLatch alaVez = new CountDownLatch(1);
            ExecutorService hilos = Executors.newFixedThreadPool(2);
            int postular;
            int eliminar;
            try {
                Future<Integer> laPostulacion = hilos.submit(() -> {
                    alaVez.await();
                    return mvc.perform(multipart("/api/v1/portal/postulaciones")
                                    .file(unCurriculum())
                                    .param("vacanteId", String.valueOf(vacante))
                                    .param("resultadoOrgulloso", "Abrí dos sedes en un trimestre")
                                    .param("aceptaTratamiento", "true")
                                    .header("Authorization", "Bearer " + tokenCandidato))
                            .andReturn().getResponse().getStatus();
                });
                Future<Integer> laEliminacion = hilos.submit(() -> {
                    alaVez.await();
                    // El instante que mete a la eliminación dentro de la operación de
                    // postular en vez de antes de que empiece. Ver el comentario de arriba.
                    Thread.sleep(120);
                    return conToken(delete("/api/v1/panel/vacantes/" + vacante),
                            tokenEquipo, "{\"motivo\":\"Se creó con el puesto equivocado\"}")
                            .andReturn().getResponse().getStatus();
                });
                alaVez.countDown();
                postular = laPostulacion.get(60, TimeUnit.SECONDS);
                eliminar = laEliminacion.get(60, TimeUnit.SECONDS);
            } finally {
                hilos.shutdownNow();
            }

            assertThat(eliminar).as("la eliminación se ordena con la postulación, no falla por ella")
                    .isEqualTo(200);
            assertThat(postular).as("o entra y se cierra, o no existe: nada más")
                    .isIn(201, 404);
            assertThat(jdbc.queryForObject("select eliminada_en from vacante where id = ?",
                    Instant.class, vacante)).isNotNull();

            // El corazón del caso: sobre una vacante eliminada no puede quedar ni un proceso
            // abierto. Estado final o ninguna postulación; lo de en medio es el huérfano.
            assertThat(contar("""
                    select count(*) from postulacion p
                      join estado_postulacion e on e.codigo = p.estado_codigo
                     where p.vacante_id = %d and not e.es_final""".formatted(vacante)))
                    .as("una postulación abierta sobre una vacante que ya no existe: "
                            + "ni el candidato ni el panel pueden verla, y nadie puede cerrarla")
                    .isZero();

            if (postular == 201) {
                assertThat(jdbc.queryForObject(
                        "select motivo_cierre from postulacion where vacante_id = ?",
                        String.class, vacante))
                        .as("se cierra por lo mismo que las demás de esa vacante")
                        .isEqualTo("VACANTE_ELIMINADA");
                assertThat(contar("select count(*) from aviso_portal where usuario_id = " + usuario
                        + " and tipo = 'VACANTE_ELIMINADA'"))
                        .as("y se le cuenta en su campana, como a quien ya estaba dentro")
                        .isEqualTo(1);
            } else {
                assertThat(contar("select count(*) from postulacion where vacante_id = " + vacante))
                        .as("rechazada es rechazada: no queda media postulación")
                        .isZero();
            }
        }
    }

    // ---------- ayudas ----------

    private long contar(String sql) {
        Long cuantos = jdbc.queryForObject(sql, Long.class);
        return cuantos == null ? 0 : cuantos;
    }

    private long postulacion(long usuarioId, long vacanteId, String estado, String motivoCierre) {
        return jdbc.queryForObject("""
                insert into postulacion (organizacion_id, usuario_id, vacante_id, estado_codigo,
                                         motivo_cierre)
                values (1, ?, ?, ?, ?) returning id""",
                Long.class, usuarioId, vacanteId, estado, motivoCierre);
    }

    private long crearCuentaDeCandidato(String correo) throws Exception {
        mvc.perform(post("/api/v1/portal/cuentas")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"nombre": "QA", "apellidos": "Candidato",
                                 "correo": "%s", "contrasena": "%s",
                                 "ciudadUbigeo": "1501", "aceptaPlataforma": true,
                                 "aceptaFuturosContactos": true}""".formatted(correo, CLAVE)))
                .andExpect(status().isCreated());
        return jdbc.queryForObject("select id from usuario where correo = ?", Long.class, correo);
    }

    private String entrarAlPortal(String correo) throws Exception {
        return leer(mvc.perform(post("/api/v1/portal/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"correo\":\"%s\",\"contrasena\":\"%s\"}"
                                .formatted(correo, CLAVE)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(), "token");
    }

    private MockMultipartFile unCurriculum() {
        return new MockMultipartFile("cv", "cv.pdf", "application/pdf",
                "contenido de prueba del curriculum".getBytes());
    }

    private long crearSolicitudAprobada() throws Exception {
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

    private long publicarVacante(long solicitud, String titulo) throws Exception {
        long id = Long.parseLong(leer(conToken(post("/api/v1/panel/vacantes"), tokenEquipo, """
                {"solicitudTalentoId": %d, "titulo": "%s", "descripcion": "Lleva la sede",
                 "tipoCierre": "PERMANENTE", "responsableUsuarioId": 1}"""
                .formatted(solicitud, titulo))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString(), "id"));
        conToken(post("/api/v1/panel/vacantes/" + id + "/plantilla-evaluacion"), tokenEquipo,
                "{\"plantillaEvaluacionId\": %d}".formatted(plantillaEvaluacionId))
                .andExpect(status().isOk());
        conToken(post("/api/v1/panel/vacantes/" + id + "/plantilla-prueba"), tokenEquipo,
                "{\"versionPlantillaPruebaId\": %d}".formatted(versionPruebaId))
                .andExpect(status().isOk());
        conToken(post("/api/v1/panel/vacantes/" + id + "/publicacion"), tokenEquipo, null)
                .andExpect(status().isOk());
        return id;
    }

    /** La prueba mínima válida que exige publicar: 8 universales, 3 específicas y rúbrica de 100. */
    private long armarUnaPruebaValida() throws Exception {
        long plantillaId = Long.parseLong(leer(conToken(post("/api/v1/panel/plantillas-prueba"),
                tokenEquipo, "{\"nombre\":\"Prueba de la eliminación\"}")
                .andReturn().getResponse().getContentAsString(), "id"));
        long versionId = Long.parseLong(leer(conToken(
                post("/api/v1/panel/plantillas-prueba/" + plantillaId + "/versiones"), tokenEquipo, """
                {"enunciado":"Resuelve el caso propuesto","modalidad":"CRONOMETRADA",
                 "duracionMinutos":90,"minutoCambioMin":30,"minutoCambioMax":50,"minutosExtra":10}""")
                .andReturn().getResponse().getContentAsString(), "id"));

        for (int i = 0; i < 8; i++) {
            long id = Long.parseLong(leer(conToken(
                    post("/api/v1/panel/plantillas-prueba/preguntas"), tokenEquipo,
                    "{\"codigo\":\"UNIV_ELI_%d\",\"enunciado\":\"Pregunta universal %d\",\"tipo\":\"UNIVERSAL\"}"
                            .formatted(i, i))
                    .andReturn().getResponse().getContentAsString(), "id"));
            conToken(post("/api/v1/panel/plantillas-prueba/versiones/" + versionId + "/preguntas"),
                    tokenEquipo, "{\"preguntaPruebaId\": %d}".formatted(id))
                    .andExpect(status().isOk());
        }
        for (int i = 0; i < 3; i++) {
            long id = Long.parseLong(leer(conToken(
                    post("/api/v1/panel/plantillas-prueba/preguntas"), tokenEquipo,
                    "{\"codigo\":\"ESP_ELI_%d\",\"enunciado\":\"Pregunta específica %d\",\"tipo\":\"ESPECIFICA\"}"
                            .formatted(i, i))
                    .andReturn().getResponse().getContentAsString(), "id"));
            conToken(post("/api/v1/panel/plantillas-prueba/versiones/" + versionId + "/preguntas"),
                    tokenEquipo, "{\"preguntaPruebaId\": %d}".formatted(id))
                    .andExpect(status().isOk());
        }
        conToken(post("/api/v1/panel/plantillas-prueba/versiones/" + versionId + "/rubrica"),
                tokenEquipo, """
                {"codigo":"RESULTADO_ELI","nombre":"Resultado","puntos":100,
                 "metodoVerificacion":"PERSONA"}""")
                .andExpect(status().isCreated());
        conToken(post("/api/v1/panel/plantillas-prueba/versiones/" + versionId + "/publicacion"),
                tokenEquipo, null).andExpect(status().isOk());
        return versionId;
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

    /** Un rol propio con «eliminar_vacante» acotado a SUS vacantes, y que no dirige ninguna. */
    private String cuentaConPermisoAcotado() throws Exception {
        Long rolId = jdbc.queryForObject("""
                insert into rol (organizacion_id, codigo, nombre, descripcion)
                values (1, 'QA_ELIMINA_LAS_SUYAS', 'QA · elimina solo las suyas',
                        'Sembrado por la prueba de eliminación') returning id""", Long.class);
        jdbc.update("""
                insert into rol_permiso (rol_id, permiso_id, alcance)
                select ?, id, 'SUS_VACANTES' from permiso where codigo = 'eliminar_vacante'""",
                rolId);
        jdbc.update("""
                insert into rol_permiso (rol_id, permiso_id, alcance)
                select ?, id, 'TODO' from permiso where codigo = 'ver_vacantes'""", rolId);
        Long personaId = jdbc.queryForObject(
                "insert into persona (nombre, apellidos) values ('QA', 'acotado') returning id",
                Long.class);
        Long usuarioId = jdbc.queryForObject("""
                insert into usuario (organizacion_id, persona_id, usuario_renaser_os_id,
                                     es_equipo, es_activo)
                values (1, ?, 'qa-elimina-acotado', true, true) returning id""",
                Long.class, personaId);
        jdbc.update("insert into usuario_rol (usuario_id, rol_id) values (?, ?)",
                usuarioId, rolId);
        return leer(mvc.perform(post("/api/v1/panel/auth/dev-login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"usuarioRenaserOsId\":\"qa-elimina-acotado\"}"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(), "token");
    }

    private ResultActions conToken(MockHttpServletRequestBuilder peticion, String token,
                                   String cuerpo) throws Exception {
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
