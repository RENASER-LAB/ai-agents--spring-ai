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
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.rabbitmq.RabbitMQContainer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * El ciclo 2 de la prueba técnica: la vacante elige su instrumento y el candidato lo rinde.
 *
 * <p>Esto es lo que no existía: el ciclo 1 dejó el cuestionario escrito y aprobado, y quien
 * llegaba a la etapa de la prueba no encontraba nada. Aquí se comprueba el camino entero y
 * las reglas que lo sostienen:
 *
 * <ul>
 *   <li>Uno de los dos instrumentos, y publicar exige tener listo el que se eligió.
 *   <li>El candidato recibe SU cuestionario, sin la pregunta PRESENCIAL.
 *   <li>La guía de calificación (C3, C4, señal de 0) no viaja nunca al portal.
 *   <li>No se entrega a medias, y el examen de otro responde 404.
 *   <li>Al entregar, la postulación pasa a calificarse y su nota es la del índice técnico.
 * </ul>
 *
 * <p>⚠️ La vacante lleva la evaluación del banco <b>apagada</b>: es la combinación más
 * probable con CAZATALENTOS —quien usa este método no quiere además el examen por nivel— y
 * la que más fácil se rompe, porque varios atajos del sistema dan por hecho que hay una
 * evaluación de perfil integral entregada.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@Testcontainers
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@DisplayName("Ciclo 2 · El candidato rinde el cuestionario técnico")
public class FlujoCuestionarioTecnicoIT {

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
        // Sin IA: aquí se prueba el camino, no la calificación. La nota la pone el test
        // escribiendo las notas de respuesta a mano, que es lo que haría el agente.
        registro.add("renaser.ai.calificacion.habilitada", () -> "false");
    }

    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    final ObjectMapper json = new ObjectMapper();

    static String tokenEquipo;
    static String tokenCandidato;
    static String codigoPostulacion;
    static long vacanteId;
    static long postulacionId;
    static long cuestionarioId;

    @Test
    @Order(1)
    @DisplayName("Una vacante nace con la prueba del puesto, y publicar exige la que eligió")
    void publicarExigeElInstrumentoElegido() throws Exception {
        tokenEquipo = leer(mvc.perform(post("/api/v1/panel/auth/dev-login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"usuarioRenaserOsId\":\"dev-1\"}"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(), "token");

        vacanteId = crearVacanteEnBorrador();

        // Por defecto, lo de siempre: ninguna vacante cambia de comportamiento por la V43.
        assertThat(jdbc.queryForObject(
                "select instrumento_etapa_tecnica from vacante where id = ?", String.class, vacanteId))
                .isEqualTo("PLANTILLA");

        // Con la evaluación apagada, la única puerta que queda es la del instrumento técnico.
        conToken(post("/api/v1/panel/vacantes/" + vacanteId + "/aplicacion-evaluacion"), tokenEquipo,
                "{\"aplica\": false}").andExpect(status().isOk());
        conToken(post("/api/v1/panel/vacantes/" + vacanteId + "/publicacion"), tokenEquipo, null)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value(
                        org.hamcrest.Matchers.containsString("la prueba del puesto")));

        // Al cambiar de instrumento, el mensaje cambia con él: pedir «elige la prueba del
        // puesto» a quien eligió el cuestionario mandaría a la pantalla equivocada.
        conToken(post("/api/v1/panel/vacantes/" + vacanteId + "/instrumento-tecnico"), tokenEquipo,
                "{\"instrumento\": \"CUESTIONARIO_TECNICO\", \"minutos\": 45}")
                .andExpect(status().isOk());
        conToken(post("/api/v1/panel/vacantes/" + vacanteId + "/publicacion"), tokenEquipo, null)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value(
                        org.hamcrest.Matchers.containsString("cuestionario técnico")));

        // Un valor que no es ninguno de los dos no se guarda «por si acaso».
        conToken(post("/api/v1/panel/vacantes/" + vacanteId + "/instrumento-tecnico"), tokenEquipo,
                "{\"instrumento\": \"LO_QUE_SEA\"}").andExpect(status().isBadRequest());
    }

    @Test
    @Order(2)
    @DisplayName("Con su cuestionario publicado, la vacante ya se puede publicar")
    void conElCuestionarioPublicadoSePuedePublicar() throws Exception {
        cuestionarioId = publicarUnCuestionarioTecnico();

        conToken(post("/api/v1/panel/vacantes/" + vacanteId + "/publicacion"), tokenEquipo, null)
                .andExpect(status().isOk());
        assertThat(jdbc.queryForObject("select estado from vacante where id = ?", String.class,
                vacanteId)).isEqualTo("PUBLICADA");
    }

    @Test
    @Order(3)
    @DisplayName("Al avanzarlo a la prueba se le crea su examen, y el del perfil integral no se toca")
    void alAvanzarloSeLeCreaSuExamen() throws Exception {
        tokenCandidato = crearCandidatoYEntrar();
        MockMultipartFile cv = new MockMultipartFile("cv", "cv.pdf",
                "application/pdf", "contenido de prueba".getBytes());
        codigoPostulacion = leer(mvc.perform(multipart("/api/v1/portal/postulaciones")
                        .file(cv)
                        .param("vacanteId", String.valueOf(vacanteId))
                        .param("resultadoOrgulloso", "Cuadré tres cajas que llevaban meses sin cuadrar")
                        .param("aceptaTratamiento", "true")
                        .header("Authorization", "Bearer " + tokenCandidato))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString(), "codigo");
        postulacionId = jdbc.queryForObject("select id from postulacion where uuid = ?::uuid",
                Long.class, codigoPostulacion);

        // Con la evaluación del banco apagada no hay examen de etapa 1: va directo a la
        // bandeja del equipo (V30). Se le avanza a mano hasta su turno en la prueba.
        avanzarHasta("PRUEBA_TURNO_CANDIDATO");

        Long tecnica = jdbc.queryForObject(
                "select evaluacion_tecnica_id from postulacion where id = ?", Long.class, postulacionId);
        assertThat(tecnica).as("se le creó su cuestionario al entrar en la etapa").isNotNull();
        // La columna del perfil integral sigue siendo suya: son dos exámenes distintos y no
        // se pisan. Aquí está vacía porque esta vacante no aplica evaluación.
        assertThat(jdbc.queryForObject("select evaluacion_id from postulacion where id = ?",
                Long.class, postulacionId)).isNull();
        // Y no se le creó un intento de la prueba del puesto: uno de los dos, nunca los dos.
        assertThat(jdbc.queryForObject("select count(*) from intento_prueba where postulacion_id = ?",
                Integer.class, postulacionId)).isZero();

        // Sin plantilla, y con los minutos que dijo la vacante.
        assertThat(jdbc.queryForMap("select plantilla_evaluacion_id, proposito, minutos_objetivo "
                + "from evaluacion where id = " + tecnica))
                .containsEntry("plantilla_evaluacion_id", null)
                .containsEntry("proposito", "CUESTIONARIO_TECNICO")
                .containsEntry("minutos_objetivo", 45);
    }

    @Test
    @Order(4)
    @DisplayName("Lo ve, lo empieza sin la presencial, y la guía de calificación no viaja")
    void loVeYLoEmpiezaSinLaPresencial() throws Exception {
        conTokenGet("/api/v1/portal/cuestionario-tecnico/" + codigoPostulacion, tokenCandidato)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("PENDIENTE"))
                .andExpect(jsonPath("$.total").value(0));

        String cuerpo = mvc.perform(post("/api/v1/portal/cuestionario-tecnico/"
                        + codigoPostulacion + "/inicio")
                        .header("Authorization", "Bearer " + tokenCandidato))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("EN_CURSO"))
                .andReturn().getResponse().getContentAsString();

        JsonNode visto = json.readTree(cuerpo);
        // Tres puntuables de las cuatro del cuestionario: la PRESENCIAL es la muestra de
        // trabajo y regala el diagnóstico del negocio a todo el que postule.
        assertThat(visto.get("total").asInt()).isEqualTo(3);
        assertThat(cuerpo).doesNotContain("PRESENCIAL", "Revisa este arqueo");
        // Y la guía con la que se le va a calificar tampoco: es del dueño, no suya.
        assertThat(cuerpo).doesNotContain("c3Esperado", "c4Esperado", "senalDeCero",
                "número de sedes", "No da ninguna cifra");

        // El reloj arrancó al empezar, no al crearse: 45 minutos desde ahora.
        assertThat(jdbc.queryForObject("""
                select vence_en < now() + interval '46 minutes'
                  from evaluacion where id = (select evaluacion_tecnica_id from postulacion where id = ?)""",
                Boolean.class, postulacionId)).isTrue();
    }

    @Test
    @Order(5)
    @DisplayName("El cuestionario de otro responde 404, y no se entrega a medias")
    void elDeOtroNoSeVeYNoSeEntregaAMedias() throws Exception {
        String otro = crearCandidatoYEntrar("otro@correo.pe");
        conTokenGet("/api/v1/portal/cuestionario-tecnico/" + codigoPostulacion, otro)
                .andExpect(status().isNotFound());

        // Una sola respuesta de las tres: entregar así dejaría una nota calculada sobre un
        // examen a medias, y el candidato no sabría que faltaba.
        responderLaPrimera();
        conToken(post("/api/v1/portal/cuestionario-tecnico/" + codigoPostulacion + "/entrega"),
                tokenCandidato, null)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value(
                        org.hamcrest.Matchers.containsString("Faltan 2")));
    }

    @Test
    @Order(6)
    @DisplayName("Lo entrega entero y pasa a calificarse")
    void loEntregaYPasaACalificarse() throws Exception {
        for (Long preguntaId : jdbc.queryForList("""
                select o.pregunta_id from orden_pregunta o
                 where o.evaluacion_id = (select evaluacion_tecnica_id from postulacion where id = ?)
                 order by o.posicion""", Long.class, postulacionId)) {
            conToken(put("/api/v1/portal/cuestionario-tecnico/" + codigoPostulacion
                            + "/respuestas/" + preguntaId), tokenCandidato,
                    "{\"texto\": \"En la sede de San Isidro llevaba una caja de 40 mil soles al "
                            + "día; el faltante de marzo lo encontré cuadrando vouchers uno a uno.\"}")
                    .andExpect(status().isOk());
        }

        conToken(post("/api/v1/portal/cuestionario-tecnico/" + codigoPostulacion + "/entrega"),
                tokenCandidato, null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("TERMINADA"))
                .andExpect(jsonPath("$.respondidas").value(3));

        assertThat(jdbc.queryForObject("select estado_codigo from postulacion where id = ?",
                String.class, postulacionId)).isEqualTo("PRUEBA_CALIFICANDO");
        // Ya no se puede tocar: el examen está entregado.
        conToken(put("/api/v1/portal/cuestionario-tecnico/" + codigoPostulacion + "/respuestas/1"),
                tokenCandidato, "{\"texto\": \"me lo repienso\"}")
                .andExpect(status().isConflict());
    }

    @Test
    @Order(7)
    @DisplayName("Con sus notas puestas, la etapa recibe el índice técnico")
    void laEtapaRecibeElIndiceTecnico() throws Exception {
        // Lo que haría el agente: por cada respuesta, los cuatro criterios contados. Aquí se
        // escriben a mano —4, 3 y 2— porque lo que se prueba es la nota de etapa, no el modelo.
        var respuestas = jdbc.queryForList("""
                select r.id from respuesta r
                 where r.evaluacion_id = (select evaluacion_tecnica_id from postulacion where id = ?)
                 order by r.id""", Long.class, postulacionId);
        int[] puntajes = {4, 3, 2};
        for (int i = 0; i < respuestas.size(); i++) {
            jdbc.update("""
                    insert into nota_respuesta (respuesta_id, puntaje, explicacion, evidencia_citada,
                                                confianza, creado_en)
                    values (?, ?, 'lo declara con cifras', 'una caja de 40 mil soles', 90, now())""",
                    respuestas.get(i), puntajes[i]);
        }

        // La calificación de la etapa es la misma que corre al terminar el agente.
        calificarLaEtapa();

        // 4 + 3 + 2 = 9 sobre 4 × 3 = 12 → 75,00. La PRESENCIAL no entra en el denominador:
        // si entrara, serían 9 de 16 y el candidato saldría con 56,25 por una pregunta que
        // nunca vio.
        assertThat(jdbc.queryForObject("""
                select puntaje from nota_etapa
                 where postulacion_id = ? and etapa_codigo = 'PRUEBA_PUESTO'""",
                java.math.BigDecimal.class, postulacionId)).isEqualByComparingTo("75.00");
    }

    @Test
    @Order(8)
    @DisplayName("El panel puede leer lo que escribió, y recalcular su nota")
    void elPanelLeeYRecalcula() throws Exception {
        // ⚠️ Estas pantallas respondían 404 sobre un `intento_prueba` que no existe: el
        // equipo se quedaba sin poder leer ni recalificar a su propio candidato.
        conTokenGet("/api/v1/panel/postulaciones/" + postulacionId + "/prueba/respuestas", tokenEquipo)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(3))
                .andExpect(jsonPath("$[0].respuesta").value(
                        org.hamcrest.Matchers.containsString("San Isidro")));

        // La rúbrica no aplica —esto no se puntúa por apartados— pero la pantalla se abre.
        conTokenGet("/api/v1/panel/postulaciones/" + postulacionId + "/prueba/notas", tokenEquipo)
                .andExpect(status().isOk());

        // Y el recálculo es la palanca del equipo cuando la nota no salió sola.
        conToken(post("/api/v1/panel/postulaciones/" + postulacionId + "/prueba/calificacion"),
                tokenEquipo, null)
                .andExpect(status().isOk());
    }

    @Test
    @Order(9)
    @DisplayName("A quien nunca lo abre se le acaba el plazo, y no queda colgado")
    void alQueNuncaLoAbreNoSeLeDejaColgado() throws Exception {
        // Otro candidato llega a la etapa y no entra nunca. Su examen nace PENDIENTE con el
        // plazo puesto; al vencer, `exigirAbierta` ya no le deja empezarlo.
        String otroToken = crearCandidatoYEntrar("tercero@correo.pe");
        MockMultipartFile cv = new MockMultipartFile("cv", "cv.pdf",
                "application/pdf", "contenido".getBytes());
        String codigo = leer(mvc.perform(multipart("/api/v1/portal/postulaciones")
                        .file(cv)
                        .param("vacanteId", String.valueOf(vacanteId))
                        .param("resultadoOrgulloso", "Llevé la caja chica de una obra dos años")
                        .param("aceptaTratamiento", "true")
                        .header("Authorization", "Bearer " + otroToken))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString(), "codigo");
        long otraId = jdbc.queryForObject("select id from postulacion where uuid = ?::uuid",
                Long.class, codigo);
        avanzarHasta(otraId, "PRUEBA_TURNO_CANDIDATO");

        Long suExamen = jdbc.queryForObject(
                "select evaluacion_tecnica_id from postulacion where id = ?", Long.class, otraId);
        assertThat(jdbc.queryForObject("select estado from evaluacion where id = ?",
                String.class, suExamen)).isEqualTo("PENDIENTE");

        // Se le pasa el plazo sin haberlo abierto.
        jdbc.update("update evaluacion set vence_en = now() - interval '1 day' where id = ?", suExamen);
        contexto.getBean(com.renaser.ai.ai_engine.perfilintegral.service.ServicioEvaluacion.class)
                .entregarTecnicasVencidas();

        // Se entrega en blanco y sigue su camino: sin esto se quedaba en su turno para
        // siempre, sin nota y sin cierre, y ningún barrido volvía a mirarlo.
        assertThat(jdbc.queryForObject("select estado from evaluacion where id = ?",
                String.class, suExamen)).isEqualTo("TERMINADA");
        assertThat(jdbc.queryForObject("select estado_codigo from postulacion where id = ?",
                String.class, otraId)).isEqualTo("PRUEBA_CALIFICANDO");

        // Y su nota es un cero de verdad, no un hueco: no contestó nada.
        contexto.getBean(com.renaser.ai.ai_engine.perfilintegral.service.impl
                        .CalificacionCuestionarioTecnico.class)
                .calificarEtapa(contexto.getBean(
                        com.renaser.ai.ai_engine.postulacion.repository.PostulacionRepository.class)
                        .findById(otraId).orElseThrow());
        assertThat(jdbc.queryForObject(
                "select puntaje from nota_etapa where postulacion_id = ? and etapa_codigo = ?",
                java.math.BigDecimal.class, otraId, "PRUEBA_PUESTO")).isEqualByComparingTo("0.00");
    }

    // ============ Apoyo ============

    /** Corre la calificación de la etapa igual que al terminar el agente. */
    private void calificarLaEtapa() {
        var postulaciones = contexto.getBean(
                com.renaser.ai.ai_engine.postulacion.repository.PostulacionRepository.class);
        contexto.getBean(com.renaser.ai.ai_engine.perfilintegral.service.impl
                        .CalificacionCuestionarioTecnico.class)
                .calificarEtapa(postulaciones.findById(postulacionId).orElseThrow());
    }

    @Autowired org.springframework.context.ApplicationContext contexto;

    /** Un cuestionario de vacante como el que deja el REDACTOR: tres puntuables y la presencial. */
    private long publicarUnCuestionarioTecnico() {
        Long organizacionId = jdbc.queryForObject(
                "select organizacion_id from vacante where id = ?", Long.class, vacanteId);
        jdbc.update("""
                insert into version_banco (organizacion_id, tipo_banco, nivel_puesto_codigo,
                                           etiqueta, estado, metodo_calificacion, vacante_id,
                                           publicada_en, creado_en)
                values (?, 'VACANTE', 'EJECUCION', 'Cuestionario técnico', 'PUBLICADA',
                        'CRITERIOS', ?, now(), now())""", organizacionId, vacanteId);
        long id = jdbc.queryForObject(
                "select id from version_banco where vacante_id = ? and estado = 'PUBLICADA'",
                Long.class, vacanteId);

        String[][] preguntas = {
                {"T01", "¿Cuántas cajas has tenido a cargo y de qué monto?", "false"},
                {"T02", "Si una sede presenta un faltante, ¿cuál es tu procedimiento exacto?", "false"},
                {"T03", "Un cajero vende mucho y descuadra siempre: ¿qué haces?", "false"},
                {"T04", "Revisa este arqueo y di qué está mal.", "true"},
        };
        int orden = 1;
        for (String[] p : preguntas) {
            boolean presencial = Boolean.parseBoolean(p[2]);
            jdbc.update("""
                    insert into pregunta (version_banco_id, codigo, enunciado, tipo, peso,
                                          es_puntuable, es_eliminatorio, presencial, orden,
                                          c3_esperado, c4_esperado, senal_de_cero, creado_en)
                    values (?, ?, ?, 'ABIERTA', 1, ?, false, ?, ?,
                            'número de sedes y montos', 'el faltante que no encontró',
                            'No da ninguna cifra', now())""",
                    id, p[0], p[1], !presencial, presencial, orden++);
        }
        return id;
    }

    private void responderLaPrimera() throws Exception {
        Long preguntaId = jdbc.queryForObject("""
                select o.pregunta_id from orden_pregunta o
                 where o.evaluacion_id = (select evaluacion_tecnica_id from postulacion where id = ?)
                 order by o.posicion limit 1""", Long.class, postulacionId);
        conToken(put("/api/v1/portal/cuestionario-tecnico/" + codigoPostulacion
                        + "/respuestas/" + preguntaId), tokenCandidato,
                "{\"texto\": \"Tres cajas, de 40 mil soles al día cada una.\"}")
                .andExpect(status().isOk());
    }

    /** Avanza la postulación a mano hasta el estado pedido, como haría el equipo. */
    private void avanzarHasta(String estado) throws Exception {
        avanzarHasta(postulacionId, estado);
    }

    private void avanzarHasta(long cual, String estado) throws Exception {
        for (int vuelta = 0; vuelta < 12; vuelta++) {
            String actual = jdbc.queryForObject("select estado_codigo from postulacion where id = ?",
                    String.class, cual);
            if (estado.equals(actual)) {
                return;
            }
            conToken(post("/api/v1/panel/postulaciones/" + cual + "/confirmacion-avance"),
                    tokenEquipo, "{\"motivo\": \"sigue en carrera\"}")
                    .andExpect(status().isOk());
        }
        throw new AssertionError("No se llegó a " + estado + " en doce avances");
    }

    private long crearVacanteEnBorrador() throws Exception {
        jdbc.update("INSERT INTO area (organizacion_id, nombre, es_activa) VALUES (1, 'Administración', true)");
        Long areaId = jdbc.queryForObject("SELECT id FROM area LIMIT 1", Long.class);

        long solicitudId = Long.parseLong(leer(conToken(post("/api/v1/panel/solicitudes"), tokenEquipo, """
                {"areaId": %d, "urgencia": "NORMAL",
                 "nivelPuestoCodigo": "EJECUCION", "familiaCodigo": "TECNOLOGIA",
                 "resultadoPrincipal": "Que la caja cuadre todos los días",
                 "motivo": "Los arqueos salen con faltantes y nadie responde",
                 "consecuenciaNoContratar": "Se sigue perdiendo plata sin saber dónde",
                 "analisisCapacidad": "Las dos personas de administración ya están al límite",
                 "responsableUsuarioId": 1,
                 "resultadosEsperados": [
                   {"descripcion": "Arqueo diario sin faltantes", "indicador": "faltantes por mes"},
                   {"descripcion": "Cuadre contra sistema", "indicador": "cierres cuadrados"},
                   {"descripcion": "Informe mensual", "indicador": "informe entregado"}
                 ]}""".formatted(areaId))
                .andReturn().getResponse().getContentAsString(), "id"));

        conToken(post("/api/v1/panel/solicitudes/" + solicitudId + "/aprobacion"), tokenEquipo,
                "{\"motivo\":\"Hay presupuesto\"}").andExpect(status().isOk());

        long puestoId = Long.parseLong(leer(conToken(post("/api/v1/panel/puestos"), tokenEquipo, """
                {"codigo": "ADM_SEDES", "nombre": "Administrador de sedes",
                 "nivelPuestoCodigo": "EJECUCION", "familiaCodigo": "TECNOLOGIA"}""")
                .andReturn().getResponse().getContentAsString(), "id"));

        return Long.parseLong(leer(conToken(post("/api/v1/panel/vacantes"), tokenEquipo, """
                {"solicitudTalentoId": %d, "puestoId": %d,
                 "titulo": "Administrador de sedes", "descripcion": "Caja y personal de tres sedes",
                 "tipoCierre": "PERMANENTE", "responsableUsuarioId": 1}"""
                .formatted(solicitudId, puestoId))
                .andReturn().getResponse().getContentAsString(), "id"));
    }

    private String crearCandidatoYEntrar() throws Exception {
        return crearCandidatoYEntrar("camila@correo.pe");
    }

    private String crearCandidatoYEntrar(String correo) throws Exception {
        mvc.perform(post("/api/v1/portal/cuentas")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                {"correo":"%s","contrasena":"unaClaveLarga123","nombre":"Camila",
                 "apellidos":"Reyes","aceptaProceso":true}""".formatted(correo)))
                .andExpect(status().isCreated());

        return leer(mvc.perform(post("/api/v1/portal/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"correo\":\"%s\",\"contrasena\":\"unaClaveLarga123\"}".formatted(correo)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(), "token");
    }

    private ResultActions conToken(
            org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder peticion,
            String token, String cuerpo) throws Exception {
        peticion.header("Authorization", "Bearer " + token).contentType(MediaType.APPLICATION_JSON);
        if (cuerpo != null) {
            peticion.content(cuerpo);
        }
        return mvc.perform(peticion);
    }

    private ResultActions conTokenGet(String ruta, String token) throws Exception {
        return mvc.perform(get(ruta).header("Authorization", "Bearer " + token));
    }

    private String leer(String cuerpo, String campo) throws Exception {
        return json.readTree(cuerpo).get(campo).asText();
    }
}
