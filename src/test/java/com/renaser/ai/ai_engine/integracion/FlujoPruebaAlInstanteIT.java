package com.renaser.ai.ai_engine.integracion;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.renaser.ai.ai_engine.integracion.soporte.ImagenesDeContenedores;
import com.renaser.ai.ai_engine.notificacion.service.FechaParaElCandidato;
import com.renaser.ai.ai_engine.perfilintegral.dto.DtosCalificacionIa.NotaRespuestaIa;
import com.renaser.ai.ai_engine.perfilintegral.dto.DtosCalificacionIa.ResultadoEvaluador;
import com.renaser.ai.ai_engine.perfilintegral.dto.DtosCalificacionIa.ResultadoPerfil;
import com.renaser.ai.ai_engine.perfilintegral.service.PuenteCalificacionIa;
import com.renaser.ai.ai_engine.postulacion.service.CalendarioDeRecordatorios;
import com.renaser.ai.ai_engine.postulacion.service.ServicioRecordatorios;

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

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * La prueba al instante, la campana en cada etapa, los recordatorios y el plazo del aviso, de
 * punta a punta (specs/la-prueba-al-instante-y-avisos-de-etapa.md, V70).
 *
 * <p>Cuatro vacantes, todas con la prueba del editor y la misma fecha límite:
 * <ul>
 *   <li><b>con banco</b> y pase automático, cronometrada a 90 minutos;</li>
 *   <li><b>sin banco</b> y pase automático, «Sin cronómetro»;</li>
 *   <li><b>sin pase automático</b>, con banco, cronometrada a 60 minutos;</li>
 *   <li><b>con el cuestionario técnico</b>, sin banco y con pase automático.</li>
 * </ul>
 *
 * <p>La IA está apagada: lo que su nota haría al terminar entra por su puente, igual que lo hace
 * el agente. Los recordatorios se prueban fijando la hora, sin esperar 24 horas.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@Testcontainers
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@DisplayName("La prueba al instante, la campana en cada etapa y los recordatorios")
class FlujoPruebaAlInstanteIT {

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

    private static final String AL_ENTREGAR = "Pase automático al entregar: la nota se calcula después";
    private static final String AL_POSTULAR = "Pase automático al postular: la nota se calcula después";

    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired PuenteCalificacionIa puente;
    @Autowired ServicioRecordatorios recordatorios;
    final ObjectMapper json = new ObjectMapper();

    static String tokenTalento;
    static long areaId;
    static long puestoId;
    static long conBanco;
    static long sinBanco;
    static long sinPase;
    static long conCuestionario;
    /** La fecha límite de las cuatro: dentro de diez días, sin fracciones de segundo. */
    static Instant cierre;
    static final Map<String, Long> postulacion = new HashMap<>();
    static final Map<String, String> codigo = new HashMap<>();
    static final Map<String, String> token = new HashMap<>();

    // ============ Las cuatro vacantes ============

    @Test
    @Order(1)
    @DisplayName("Cuatro vacantes con la prueba del editor: con banco, sin banco, sin pase y con cuestionario")
    void lasCuatroVacantes() throws Exception {
        tokenTalento = leer(mvc.perform(post("/api/v1/panel/auth/dev-login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"usuarioRenaserOsId\":\"dev-talento\"}"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString(), "token");
        jdbc.update("INSERT INTO area (organizacion_id, nombre, es_activa) VALUES (1, 'Tiendas', true)");
        areaId = jdbc.queryForObject("SELECT id FROM area ORDER BY id DESC LIMIT 1", Long.class);
        puestoId = Long.parseLong(leer(conToken(post("/api/v1/panel/puestos"), tokenTalento, """
                {"codigo": "ADMIN_TIENDA_V70", "nombre": "Administrador de tienda",
                 "nivelPuestoCodigo": "EJECUCION", "familiaCodigo": "OPERACIONES"}""")
                .andReturn().getResponse().getContentAsString(), "id"));
        cierre = Instant.now().plus(Duration.ofDays(10)).truncatedTo(ChronoUnit.SECONDS);

        conBanco = crearVacante("Administrador de tienda");
        preguntasPropias(conBanco);
        pruebaPropia(conBanco, "\"modalidad\":\"CRONOMETRADA\",\"duracionMinutos\":90");
        publicar(conBanco, true);

        sinBanco = crearVacante("Cajero de sede");
        conToken(post("/api/v1/panel/vacantes/" + sinBanco + "/aplicacion-evaluacion"), tokenTalento,
                "{\"aplica\":false}").andExpect(status().isOk());
        // «Sin cronómetro» (V68): plazo abierto sin días, hasta la fecha límite
        pruebaPropia(sinBanco, "\"modalidad\":\"PLAZO_ABIERTO\"");
        publicar(sinBanco, true);

        sinPase = crearVacante("Supervisor de turno");
        preguntasPropias(sinPase);
        pruebaPropia(sinPase, "\"modalidad\":\"CRONOMETRADA\",\"duracionMinutos\":60");
        publicar(sinPase, false);

        conCuestionario = crearVacante("Analista de caja");
        conToken(post("/api/v1/panel/vacantes/" + conCuestionario + "/aplicacion-evaluacion"), tokenTalento,
                "{\"aplica\":false}").andExpect(status().isOk());
        // Una vacante nueva ya no puede elegir el cuestionario (rinde la prueba del editor): se
        // deja como las de antes de la V67, que todavía lo rinden.
        jdbc.update("update vacante set instrumento_etapa_tecnica = 'CUESTIONARIO_TECNICO', "
                + "minutos_etapa_tecnica = 45 where id = ?", conCuestionario);
        publicarUnCuestionario(conCuestionario);
        publicar(conCuestionario, true);
    }

    // ============ Parte A: la prueba al instante ============

    @Test
    @Order(2)
    @DisplayName("Con banco y pase automático: al entregar, en la misma respuesta queda en la prueba sin reloj (AC-1, AC-8, AC-20, AC-21)")
    void alEntregarQuedaEnLaPrueba() throws Exception {
        postular("ana", conBanco);
        assertThat(estado("ana")).isEqualTo("PERFIL_TURNO_CANDIDATO");

        responderYEntregar("ana");

        // AC-1: en la misma respuesta, en la prueba, con su intento y el reloj sin arrancar
        assertThat(estado("ana")).isEqualTo("PRUEBA_TURNO_CANDIDATO");
        assertThat(jdbc.queryForObject("select count(*) from intento_prueba where postulacion_id = ? "
                + "and iniciado_en is null", Integer.class, postulacion.get("ana"))).isEqualTo(1);
        JsonNode prueba = respuesta(conTokenGet("/api/v1/portal/prueba/" + codigo.get("ana"), token.get("ana")));
        assertThat(prueba.get("estadoIntento").asText()).isEqualTo("PENDIENTE");

        // AC-8: el historial dice el pase con su motivo, de uno en uno
        assertThat(pasos("ana")).containsExactly(
                "POSTULADA | null",
                "PERFIL_TURNO_CANDIDATO | null",
                "PERFIL_CALIFICANDO | null",
                "PERFIL_POR_CONFIRMAR | " + AL_ENTREGAR,
                "PRUEBA_TURNO_CANDIDATO | " + AL_ENTREGAR);
        // Y un solo correo por el pase: el de la prueba
        assertThat(correos("ana")).containsExactly(
                "POSTULACION_RECIBIDA", "POSTULACION_AVANZA", "PRUEBA_DISPONIBLE");

        // AC-20/21: con nombre, vacante y el plazo de la cronometrada con fecha, en hora de Lima
        String cuerpo = cuerpoDe("ana", "PRUEBA_DISPONIBLE");
        assertThat(cuerpo).contains("Ana")
                .contains("Administrador de tienda")
                .contains("Tienes 90 minutos desde que la empieces, hasta el "
                        + FechaParaElCandidato.dicha(cierre) + ".")
                .doesNotContain("{{").doesNotContain("desde este correo");
    }

    @Test
    @Order(3)
    @DisplayName("La campana tiene el aviso de la prueba ligado al proceso, y «Mis procesos» lo cuenta (AC-10)")
    void laCampanaTieneElAviso() throws Exception {
        assertThat(avisos("ana")).containsExactly("POSTULACION_AVANZA", "PRUEBA_DISPONIBLE");
        assertThat(jdbc.queryForObject("""
                select titulo from aviso_portal where usuario_id = ? and tipo = 'PRUEBA_DISPONIBLE'""",
                String.class, usuarioDe("ana")))
                .isEqualTo("Tu prueba del puesto está disponible · Administrador de tienda");

        JsonNode campana = respuesta(conTokenGet("/api/v1/portal/avisos", token.get("ana")));
        assertThat(campana.get("sinLeer").asInt()).isEqualTo(2);
        assertThat(campana.at("/avisos/0/tipo").asText()).isEqualTo("PRUEBA_DISPONIBLE");
        assertThat(campana.at("/avisos/0/postulacionUuid").asText()).isEqualTo(codigo.get("ana"));

        JsonNode mias = respuesta(conTokenGet("/api/v1/portal/postulaciones", token.get("ana")));
        assertThat(mias.get(0).get("avisosSinLeer").asInt()).isEqualTo(2);
    }

    @Test
    @Order(4)
    @DisplayName("Con la nota pendiente el ranking lo sabe; al terminar la IA, nota y grupo sin mover a nadie (AC-4, AC-5)")
    void laNotaLlegaSinMoverANadie() throws Exception {
        long ana = postulacion.get("ana");
        // La IA trabajando: lo que el panel pinta como «en camino»
        jdbc.update("""
                insert into trabajo_ia (organizacion_id, agente_codigo, postulacion_id, estado, modo)
                values (1, 'POTENCIAL_RIESGO', ?, 'PENDIENTE', 'FINA')""", ana);
        JsonNode fila = filaDelRanking(conBanco, ana);
        assertThat(fila.get("estado").asText()).isEqualTo("PRUEBA_TURNO_CANDIDATO");
        assertThat(fila.get("estadoCalificacion").asText()).isEqualTo("EN_CURSO");
        assertThat(fila.get("notaEtapa").isNull()).isTrue();

        // La IA termina con el candidato ya en la prueba: califica su abierta, arma el retrato,
        // se guarda todo y no se le mueve
        int transicionesAntes = contar("select count(*) from transicion_estado where postulacion_id = " + ana);
        long respuestaId = jdbc.queryForObject("""
                select r.id from respuesta r join postulacion p on p.evaluacion_id = r.evaluacion_id
                 where p.id = ?""", Long.class, ana);
        puente.guardarNotasPorPuntos(ana, null, new ResultadoEvaluador(List.of(new NotaRespuestaIa(
                respuestaId, new BigDecimal("85"), "Da el monto, la causa y el plazo", "cita",
                new BigDecimal("80"), null, null, null, null, null))), 1, false);
        puente.cerrarPerfilIntegral(ana, null, new ResultadoPerfil(new BigDecimal("72"),
                new BigDecimal("65"), new BigDecimal("50"), new BigDecimal("80"),
                "Cuadra cajas y explica sus cifras", List.of(), List.of()));
        jdbc.update("update trabajo_ia set estado = 'TERMINADO', terminado_en = now() where postulacion_id = ?", ana);

        assertThat(estado("ana")).isEqualTo("PRUEBA_TURNO_CANDIDATO");
        assertThat(contar("select count(*) from transicion_estado where postulacion_id = " + ana))
                .isEqualTo(transicionesAntes);
        assertThat(contar("select count(*) from perfil_talento where postulacion_id = " + ana)).isEqualTo(1);
        assertThat(jdbc.queryForObject("select grupo_prioridad from postulacion where id = ?",
                String.class, ana)).isNotBlank();
        JsonNode despues = filaDelRanking(conBanco, ana);
        assertThat(despues.get("notaEtapa").isNull()).as("la nota del Perfil Integral llegó").isFalse();
        assertThat(despues.get("grupoPrioridad").asText()).isNotBlank();
        assertThat(despues.get("adecuacion").asInt()).isEqualTo(72);
        assertThat(despues.get("estadoCalificacion").asText()).isEqualTo("TERMINADA");
        // Y no sale otro correo de la prueba
        assertThat(correos("ana")).containsOnlyOnce("PRUEBA_DISPONIBLE");
    }

    @Test
    @Order(5)
    @DisplayName("Sin banco y con pase automático: al postular queda en la prueba «Sin cronómetro» (AC-2, AC-21)")
    void alPostularQuedaEnLaPrueba() throws Exception {
        postular("beto", sinBanco);

        assertThat(estado("beto")).isEqualTo("PRUEBA_TURNO_CANDIDATO");
        assertThat(contar("select count(*) from intento_prueba where postulacion_id = "
                + postulacion.get("beto"))).isEqualTo(1);
        assertThat(pasos("beto")).containsExactly(
                "POSTULADA | null",
                "PERFIL_POR_CONFIRMAR | null",
                "PRUEBA_TURNO_CANDIDATO | " + AL_POSTULAR);
        assertThat(correos("beto")).containsExactly("POSTULACION_RECIBIDA", "PRUEBA_DISPONIBLE");
        assertThat(cuerpoDe("beto", "PRUEBA_DISPONIBLE"))
                .contains("Tienes hasta el " + FechaParaElCandidato.dicha(cierre) + ".")
                .doesNotContain("{{");
        assertThat(avisos("beto")).containsExactly("PRUEBA_DISPONIBLE");
    }

    @Test
    @Order(6)
    @DisplayName("Si la IA agota sus intentos, sigue en su prueba y el panel lo dice como hoy (AC-6)")
    void siLaIaFallaSigueEnLaPrueba() throws Exception {
        long beto = postulacion.get("beto");
        jdbc.update("""
                insert into trabajo_ia (organizacion_id, agente_codigo, postulacion_id, estado, modo,
                                        intentos, terminado_en)
                values (1, 'POTENCIAL_RIESGO', ?, 'FALLIDO', 'FINA', 3, now())""", beto);

        JsonNode fila = filaDelRanking(sinBanco, beto);
        assertThat(fila.get("estadoCalificacion").asText()).isEqualTo("FALLIDA");
        assertThat(estado("beto")).isEqualTo("PRUEBA_TURNO_CANDIDATO");
    }

    @Test
    @Order(7)
    @DisplayName("Un requisito incumplido sigue llevando a «no continúa», sin prueba, y la campana lo cuenta (AC-9, AC-10)")
    void elRequisitoIncumplidoNoContinua() throws Exception {
        jdbc.update("""
                insert into requisito_objetivo (vacante_id, descripcion, regla, es_activo)
                values (?, 'Licencia', 'Licencia A-IIb vigente', true)""", sinBanco);
        try {
            postular("carla", sinBanco);
        } finally {
            jdbc.update("update requisito_objetivo set es_activo = false where vacante_id = ?", sinBanco);
        }

        assertThat(estado("carla")).isEqualTo("NO_CONTINUA");
        assertThat(contar("select count(*) from intento_prueba where postulacion_id = "
                + postulacion.get("carla"))).isZero();
        assertThat(correos("carla")).containsExactly("POSTULACION_RECIBIDA", "POSTULACION_NO_CONTINUA");
        // Nace y se cierra en la misma transacción: el aviso se escribe al confirmarse, y existe
        assertThat(avisos("carla")).containsExactly("POSTULACION_NO_CONTINUA");
    }

    @Test
    @Order(8)
    @DisplayName("Con el cuestionario técnico también se abre al instante, con su plazo (AC-3, AC-21)")
    void elCuestionarioTambien() throws Exception {
        postular("dani", conCuestionario);

        assertThat(estado("dani")).isEqualTo("PRUEBA_TURNO_CANDIDATO");
        assertThat(jdbc.queryForObject("select evaluacion_tecnica_id from postulacion where id = ?",
                Long.class, postulacion.get("dani"))).isNotNull();
        conTokenGet("/api/v1/portal/cuestionario-tecnico/" + codigo.get("dani"), token.get("dani"))
                .andExpect(status().isOk());
        // Nunca vacío: el cuestionario dice hasta cuándo, en hora de Lima
        assertThat(cuerpoDe("dani", "PRUEBA_DISPONIBLE")).contains("Tienes hasta el ")
                .doesNotContain("Tienes .");
    }

    @Test
    @Order(9)
    @DisplayName("Sin pase automático, como hoy: espera; y cuando el equipo confirma sale el correo y el aviso (AC-7, AC-20)")
    void sinPaseEsperaAlEquipo() throws Exception {
        postular("diego", sinPase);
        responderYEntregar("diego");

        assertThat(estado("diego")).isEqualTo("PERFIL_CALIFICANDO");
        assertThat(contar("select count(*) from intento_prueba where postulacion_id = "
                + postulacion.get("diego"))).isZero();
        assertThat(correos("diego")).doesNotContain("PRUEBA_DISPONIBLE");

        // El equipo confirma: primero el perfil, después la prueba
        long diego = postulacion.get("diego");
        conToken(post("/api/v1/panel/postulaciones/" + diego + "/confirmacion-avance"), tokenTalento,
                "{\"motivo\":\"Perfil revisado\"}").andExpect(status().isOk());
        conToken(post("/api/v1/panel/postulaciones/" + diego + "/confirmacion-avance"), tokenTalento,
                "{\"motivo\":\"Pasa a la prueba\"}").andExpect(status().isOk());

        assertThat(estado("diego")).isEqualTo("PRUEBA_TURNO_CANDIDATO");
        assertThat(cuerpoDe("diego", "PRUEBA_DISPONIBLE"))
                .contains("Tienes 60 minutos desde que la empieces, hasta el "
                        + FechaParaElCandidato.dicha(cierre) + ".");
        assertThat(avisos("diego")).containsExactly("POSTULACION_AVANZA", "PRUEBA_DISPONIBLE");
    }

    // ============ Parte B: movido «sin avisar» ============

    @Test
    @Order(10)
    @DisplayName("Movido «sin avisar» a la prueba: ni correo ni aviso (AC-11)")
    void sinAvisarNiCorreoNiAviso() throws Exception {
        postular("fabi", sinPase);
        conToken(post("/api/v1/panel/postulaciones/" + postulacion.get("fabi") + "/transiciones"),
                tokenTalento, """
                {"estadoDestino":"PRUEBA_TURNO_CANDIDATO","motivo":"Ya hablamos por teléfono",
                 "avisar":false}""").andExpect(status().isOk());

        assertThat(estado("fabi")).isEqualTo("PRUEBA_TURNO_CANDIDATO");
        assertThat(correos("fabi")).doesNotContain("PRUEBA_DISPONIBLE");
        assertThat(avisos("fabi")).doesNotContain("PRUEBA_DISPONIBLE");
        assertThat(jdbc.queryForObject("""
                select aviso_al_candidato from transicion_estado
                 where postulacion_id = ? order by id desc limit 1""", String.class,
                postulacion.get("fabi"))).isEqualTo("NINGUNO");
    }

    // ============ Parte C: los recordatorios ============

    @Test
    @Order(11)
    @DisplayName("A las 24 horas: banco sin entregar y prueba sin empezar reciben uno; quien empezó o se movió sin avisar, ninguno (AC-13, AC-15, AC-16)")
    void alas24Horas() throws Exception {
        postular("elena", conBanco);
        // Beto empieza su prueba: ya está dentro y no se le recuerda nada
        mvc.perform(post("/api/v1/portal/prueba/" + codigo.get("beto") + "/inicio")
                        .header("Authorization", "Bearer " + token.get("beto")))
                .andExpect(status().isOk());

        Instant ahora = diurno(ultimoTurno().plus(Duration.ofHours(25)));
        recordatorios.enviarPendientes(ahora);

        assertThat(correos("elena")).endsWith("RECORDATORIO_EVALUACION");
        assertThat(asuntoDe("elena", "RECORDATORIO_EVALUACION"))
                .isEqualTo("Aún no has respondido tu evaluación para Administrador de tienda");
        assertThat(avisos("elena")).endsWith("RECORDATORIO_EVALUACION");
        for (String quien : List.of("ana", "dani", "diego")) {
            assertThat(correos(quien)).as(quien).endsWith("RECORDATORIO_PRUEBA");
            assertThat(avisos(quien)).as(quien).endsWith("RECORDATORIO_PRUEBA");
        }
        assertThat(asuntoDe("ana", "RECORDATORIO_PRUEBA"))
                .isEqualTo("Aún no has empezado tu prueba del puesto para Administrador de tienda");
        // Ninguno para quien ya empezó, se movió sin avisar o terminó
        for (String quien : List.of("beto", "fabi", "carla")) {
            assertThat(correos(quien)).as(quien).doesNotContain("RECORDATORIO_PRUEBA", "RECORDATORIO_EVALUACION");
        }
        assertThat(contar("select count(*) from recordatorio_enviado where tipo = 'TRAS_ENTRAR' "
                + "and resultado = 'ENVIADO'")).isEqualTo(4);
    }

    @Test
    @Order(12)
    @DisplayName("Reiniciar no repite nada, y de noche no sale nada (AC-17, AC-18)")
    void noSeRepite() {
        Instant ahora = diurno(ultimoTurno().plus(Duration.ofHours(26)));
        int correosAntes = contar("select count(*) from correo_enviado");

        assertThat(recordatorios.enviarPendientes(ahora)).isZero();
        Instant noche = CalendarioDeRecordatorios.enFranja(ahora).atZone(FechaParaElCandidato.LIMA)
                .withHour(22).toInstant();
        assertThat(recordatorios.enviarPendientes(noche)).isZero();

        assertThat(contar("select count(*) from correo_enviado")).isEqualTo(correosAntes);
    }

    @Test
    @Order(13)
    @DisplayName("24 horas antes del plazo sale el del plazo, con la fecha en Lima; una vez por fecha (AC-14, AC-15)")
    void antesDelPlazo() {
        Instant ahora = CalendarioDeRecordatorios.enFranja(cierre.minus(Duration.ofHours(24)));
        recordatorios.enviarPendientes(ahora);

        assertThat(asuntoDe("ana", "RECORDATORIO_PRUEBA"))
                .isEqualTo("Tu prueba del puesto para Administrador de tienda vence el "
                        + FechaParaElCandidato.dicha(cierre));
        assertThat(contar("select count(*) from recordatorio_enviado where postulacion_id = "
                + postulacion.get("ana") + " and tipo = 'ANTES_DEL_PLAZO'")).isEqualTo(1);

        // Si la fecha se mueve después del recordatorio, sale una vez para la nueva
        Instant nueva = cierre.plus(Duration.ofDays(2));
        jdbc.update("update intento_prueba set vence_en = ?, plazo_propio = true where postulacion_id = ?",
                Timestamp.from(nueva), postulacion.get("ana"));
        Instant despues = CalendarioDeRecordatorios.enFranja(nueva.minus(Duration.ofHours(24)));
        recordatorios.enviarPendientes(despues);
        recordatorios.enviarPendientes(despues);
        assertThat(contar("select count(*) from recordatorio_enviado where postulacion_id = "
                + postulacion.get("ana") + " and tipo = 'ANTES_DEL_PLAZO'")).isEqualTo(2);
        assertThat(asuntoDe("ana", "RECORDATORIO_PRUEBA")).endsWith(FechaParaElCandidato.dicha(nueva));
    }

    @Test
    @Order(14)
    @DisplayName("Con la empresa apagada o la vacante archivada no sale; encendida, sale el del banco (AC-14, AC-16)")
    void apagadosOArchivadaNada() {
        long elena = postulacion.get("elena");
        Instant vence = jdbc.queryForObject("""
                select e.vence_en from evaluacion e join postulacion p on p.evaluacion_id = e.id
                 where p.id = ?""", Timestamp.class, elena).toInstant();
        Instant ahora = CalendarioDeRecordatorios.enFranja(vence.minus(Duration.ofHours(24)));

        jdbc.update("update parametro set valor = 'false' where organizacion_id = 1 "
                + "and codigo = 'recordatorios_activos'");
        recordatorios.enviarPendientes(ahora);
        jdbc.update("update parametro set valor = 'true' where organizacion_id = 1 "
                + "and codigo = 'recordatorios_activos'");
        jdbc.update("update vacante set estado = 'CERRADA', archivada_en = now() where id = ?", conBanco);
        recordatorios.enviarPendientes(ahora);
        jdbc.update("update vacante set estado = 'PUBLICADA', archivada_en = null where id = ?", conBanco);
        assertThat(contar("select count(*) from recordatorio_enviado where postulacion_id = " + elena
                + " and tipo = 'ANTES_DEL_PLAZO'")).isZero();

        recordatorios.enviarPendientes(ahora);
        assertThat(asuntoDe("elena", "RECORDATORIO_EVALUACION"))
                .isEqualTo("Tu evaluación para Administrador de tienda vence el "
                        + FechaParaElCandidato.dicha(vence));
    }

    @Test
    @Order(15)
    @DisplayName("Los textos de los recordatorios se editan como los demás, y el siguiente sale con el nuevo (AC-19)")
    void losTextosSeEditan() throws Exception {
        conToken(post("/api/v1/panel/plantillas-correo"), tokenTalento, """
                {"codigo":"RECORDATORIO_EVALUACION","asunto":"Te esperamos: {{aviso}}",
                 "cuerpo":"Hola {{nombre}}. {{aviso}}. Entra: {{enlace}}"}""").andExpect(status().isCreated());
        assertThat(contar("select count(*) from plantilla_correo where organizacion_id = 1 "
                + "and codigo = 'RECORDATORIO_EVALUACION' and es_activa and version = 2")).isEqualTo(1);

        // Un turno nuevo para Elena —se le reabre la evaluación— y su recordatorio sale con el texto nuevo
        long elena = postulacion.get("elena");
        jdbc.update("""
                insert into transicion_estado (postulacion_id, estado_anterior_codigo, estado_nuevo_codigo,
                                               es_sistema, aviso_al_candidato, ocurrida_en)
                values (?, 'PERFIL_TURNO_CANDIDATO', 'PERFIL_TURNO_CANDIDATO', true, 'CORREO', now())""",
                elena);
        recordatorios.enviarPendientes(diurno(Instant.now().plus(Duration.ofHours(25))));

        assertThat(asuntoDe("elena", "RECORDATORIO_EVALUACION"))
                .isEqualTo("Te esperamos: Aún no has respondido tu evaluación para Administrador de tienda");
    }

    // ============ Casos límite ============

    @Test
    @Order(16)
    @DisplayName("Dos pestañas entregan a la vez: la segunda falla, un solo paso a «calificando», una prueba y un correo")
    void dosEntregasALaVez() throws Exception {
        postular("fede", conBanco);
        String entrega = responderSinEntregar("fede");

        CountDownLatch alaVez = new CountDownLatch(1);
        ExecutorService hilos = Executors.newFixedThreadPool(2);
        List<Integer> estados = new ArrayList<>();
        try {
            List<Future<Integer>> respuestas = new ArrayList<>();
            for (int i = 0; i < 2; i++) {
                respuestas.add(hilos.submit(() -> {
                    alaVez.await();
                    return mvc.perform(post(entrega).header("Authorization", "Bearer " + token.get("fede")))
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

        assertThat(estados).as("una entrega y la otra se encuentra la evaluación ya entregada")
                .containsExactlyInAnyOrder(200, 409);
        long fede = postulacion.get("fede");
        assertThat(contar("select count(*) from transicion_estado where postulacion_id = " + fede
                + " and estado_nuevo_codigo = 'PERFIL_CALIFICANDO'")).isEqualTo(1);
        assertThat(estado("fede")).isEqualTo("PRUEBA_TURNO_CANDIDATO");
        assertThat(contar("select count(*) from intento_prueba where postulacion_id = " + fede)).isEqualTo(1);
        assertThat(correos("fede")).containsOnlyOnce("PRUEBA_DISPONIBLE");
        assertThat(avisos("fede")).containsOnlyOnce("PRUEBA_DISPONIBLE");
    }

    // ============ Ayudas ============

    /** Lo que el sondeo haría a esa hora, llevado a la franja de Lima. */
    private static Instant diurno(Instant cuando) {
        return CalendarioDeRecordatorios.enFranja(cuando);
    }

    private Instant ultimoTurno() {
        return jdbc.queryForObject("select max(ocurrida_en) from transicion_estado", Timestamp.class).toInstant();
    }

    private void postular(String quien, long vacante) throws Exception {
        String correo = quien + ".instante@correo.pe";
        mvc.perform(post("/api/v1/portal/cuentas").contentType(MediaType.APPLICATION_JSON).content("""
                {"nombre":"%s","apellidos":"Prueba","correo":"%s",
                 "contrasena":"unaClaveLarga123","ciudadUbigeo":"1501","aceptaPlataforma":true,
                 "aceptaFuturosContactos":false}""".formatted(capital(quien), correo)))
                .andExpect(status().isCreated());
        String suToken = leer(mvc.perform(post("/api/v1/portal/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"correo\":\"%s\",\"contrasena\":\"unaClaveLarga123\"}".formatted(correo)))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString(), "token");
        token.put(quien, suToken);
        String suCodigo = leer(mvc.perform(multipart("/api/v1/portal/postulaciones")
                        .file(new MockMultipartFile("cv", "cv.pdf", "application/pdf", "contenido".getBytes()))
                        .param("vacanteId", String.valueOf(vacante))
                        .param("resultadoOrgulloso", "Cuadré la caja de tres sedes")
                        .param("aceptaTratamiento", "true")
                        .header("Authorization", "Bearer " + suToken))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString(), "codigo");
        codigo.put(quien, suCodigo);
        postulacion.put(quien, jdbc.queryForObject("select id from postulacion where uuid = ?::uuid",
                Long.class, suCodigo));
    }

    private void responderYEntregar(String quien) throws Exception {
        mvc.perform(post(responderSinEntregar(quien)).header("Authorization", "Bearer " + token.get(quien)))
                .andExpect(status().isOk());
    }

    /** Empieza la evaluación y responde todo; devuelve la ruta de la entrega. */
    private String responderSinEntregar(String quien) throws Exception {
        String base = "/api/v1/portal/evaluacion/" + codigo.get(quien);
        JsonNode examen = json.readTree(mvc.perform(post(base + "/inicio")
                        .header("Authorization", "Bearer " + token.get(quien)))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        for (JsonNode p : examen.get("preguntas")) {
            mvc.perform(put(base + "/respuestas/" + p.get("id").asLong())
                            .header("Authorization", "Bearer " + token.get(quien))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"texto\":\"En marzo cuadré un descuadre de 1.200 soles en dos días\"}"))
                    .andExpect(status().isOk());
        }
        return base + "/entrega";
    }

    private String estado(String quien) {
        return jdbc.queryForObject("select estado_codigo from postulacion where id = ?", String.class,
                postulacion.get(quien));
    }

    private long usuarioDe(String quien) {
        return jdbc.queryForObject("select usuario_id from postulacion where id = ?", Long.class,
                postulacion.get(quien));
    }

    /** El historial: «estado | motivo», en orden. */
    private List<String> pasos(String quien) {
        return jdbc.queryForList("""
                select estado_nuevo_codigo || ' | ' || coalesce(motivo, 'null') from transicion_estado
                 where postulacion_id = ? order by id""", String.class, postulacion.get(quien));
    }

    private List<String> correos(String quien) {
        return jdbc.queryForList("select plantilla_correo_codigo from correo_enviado where usuario_id = ? "
                + "and plantilla_correo_codigo <> 'CUENTA_CREADA' order by id", String.class, usuarioDe(quien));
    }

    private String cuerpoDe(String quien, String plantilla) {
        return jdbc.queryForObject("select cuerpo from correo_enviado where usuario_id = ? "
                + "and plantilla_correo_codigo = ? order by id desc limit 1", String.class,
                usuarioDe(quien), plantilla);
    }

    private String asuntoDe(String quien, String plantilla) {
        return jdbc.queryForObject("select asunto from correo_enviado where usuario_id = ? "
                + "and plantilla_correo_codigo = ? order by id desc limit 1", String.class,
                usuarioDe(quien), plantilla);
    }

    private List<String> avisos(String quien) {
        return jdbc.queryForList("select tipo from aviso_portal where usuario_id = ? order by id",
                String.class, usuarioDe(quien));
    }

    private JsonNode filaDelRanking(long vacante, long postulacionId) throws Exception {
        JsonNode ranking = respuesta(conTokenGet("/api/v1/panel/vacantes/" + vacante
                + "/ranking?etapa=PERFIL_INTEGRAL", tokenTalento));
        for (JsonNode f : ranking.get("filas")) {
            if (f.get("postulacionId").asLong() == postulacionId) return f;
        }
        throw new AssertionError("La postulación " + postulacionId + " no está en el ranking: " + ranking);
    }

    private void preguntasPropias(long vacante) throws Exception {
        String base = "/api/v1/panel/vacantes/" + vacante + "/preguntas-propias";
        conToken(post(base + "/preguntas"), tokenTalento, """
                {"tipo":"ABIERTA","enunciado":"Cuéntanos un cierre de caja difícil y cómo lo resolviste",
                 "puntos":100,"queDebeTener":"El monto, la causa y en cuánto tiempo lo cerró"}""")
                .andExpect(status().isOk());
        conToken(post(base + "/publicacion"), tokenTalento, null).andExpect(status().isOk());
    }

    private void pruebaPropia(long vacante, String tiempo) throws Exception {
        String base = "/api/v1/panel/vacantes/" + vacante + "/prueba-propia";
        conToken(put(base + "/borrador"), tokenTalento, """
                {"guiaCalificacion":"Mira las cifras","enunciado":"La caja de marzo no cuadra por 1.200 soles.",
                 %s}""".formatted(tiempo)).andExpect(status().isOk());
        JsonNode editor = respuesta(conToken(post(base + "/criterios"), tokenTalento, """
                {"nombre":"Conocimiento de caja","queEvalua":"Arqueo y cierre","puntos":100,
                 "calificador":"IA"}"""));
        long criterio = editor.at("/borrador/criterios/0/id").asLong();
        conToken(post(base + "/preguntas"), tokenTalento, """
                {"tipo":"ABIERTA","enunciado":"¿Cómo hallarías el descuadre?","criterioId":%d,
                 "queDebeTener":"La cuenta y el monto"}""".formatted(criterio)).andExpect(status().isOk());
        conToken(put(base + "/fecha-limite"), tokenTalento, "{\"cierraEn\":\"%s\"}".formatted(cierre))
                .andExpect(status().isOk());
        conToken(post(base + "/publicacion"), tokenTalento, null).andExpect(status().isOk());
    }

    private void publicar(long vacante, boolean paseAutomatico) throws Exception {
        conToken(post("/api/v1/panel/vacantes/" + vacante + "/publicacion"), tokenTalento, null)
                .andExpect(status().isOk());
        jdbc.update("update vacante set calificacion_automatica = ? where id = ?", paseAutomatico, vacante);
    }

    /** El cuestionario técnico publicado, como lo deja FlujoCuestionarioTecnicoIT. */
    private void publicarUnCuestionario(long vacante) {
        jdbc.update("""
                insert into version_banco (organizacion_id, tipo_banco, nivel_puesto_codigo,
                                           etiqueta, estado, metodo_calificacion, vacante_id,
                                           publicada_en, creado_en)
                values (1, 'VACANTE', 'EJECUCION', 'Cuestionario técnico', 'PUBLICADA',
                        'CRITERIOS', ?, now(), now())""", vacante);
        long id = jdbc.queryForObject("""
                select id from version_banco where vacante_id = ? and estado = 'PUBLICADA'
                 order by id desc limit 1""", Long.class, vacante);
        jdbc.update("""
                insert into pregunta (version_banco_id, codigo, enunciado, tipo, peso, es_puntuable,
                                      es_eliminatorio, presencial, orden, c3_esperado, c4_esperado,
                                      senal_de_cero, creado_en)
                values (?, 'T01', '¿Cuántas cajas has tenido a cargo?', 'ABIERTA', 1, true, false,
                        false, 1, 'número de sedes', 'el faltante', 'No da ninguna cifra', now())""", id);
    }

    private long crearVacante(String titulo) throws Exception {
        long solicitudId = Long.parseLong(leer(conToken(post("/api/v1/panel/solicitudes"), tokenTalento, """
                {"areaId": %d, "puestoId": %d, "urgencia": "NORMAL",
                 "nivelPuestoCodigo": "EJECUCION", "familiaCodigo": "OPERACIONES",
                 "resultadoPrincipal": "Tiendas con la caja cuadrada",
                 "motivo": "Los cierres se atrasan",
                 "consecuenciaNoContratar": "Se pierde el control de caja",
                 "analisisCapacidad": "El equipo no alcanza",
                 "responsableUsuarioId": 1,
                 "resultadosEsperados": [
                   {"descripcion": "Cierres al día", "indicador": "antes del día 5"},
                   {"descripcion": "Arqueos", "indicador": "semanales"},
                   {"descripcion": "Reportes", "indicador": "mensuales"}
                 ]}""".formatted(areaId, puestoId))
                .andReturn().getResponse().getContentAsString(), "id"));
        conToken(post("/api/v1/panel/solicitudes/" + solicitudId + "/aprobacion"), tokenTalento,
                "{\"motivo\":\"Aprobada\"}").andExpect(status().isOk());
        return Long.parseLong(leer(conToken(post("/api/v1/panel/vacantes"), tokenTalento, """
                {"solicitudTalentoId": %d, "puestoId": %d,
                 "titulo": "%s", "descripcion": "Lleva la caja y el equipo de la tienda",
                 "tipoCierre": "PERMANENTE", "responsableUsuarioId": 1}"""
                .formatted(solicitudId, puestoId, titulo))
                .andReturn().getResponse().getContentAsString(), "id"));
    }

    private int contar(String sql) {
        return jdbc.queryForObject(sql, Integer.class);
    }

    private static String capital(String s) {
        return Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }

    private JsonNode respuesta(ResultActions peticion) throws Exception {
        return json.readTree(peticion.andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
    }

    private ResultActions conToken(MockHttpServletRequestBuilder peticion, String suToken, String cuerpo)
            throws Exception {
        peticion.header("Authorization", "Bearer " + suToken).contentType(MediaType.APPLICATION_JSON);
        if (cuerpo != null) peticion.content(cuerpo);
        return mvc.perform(peticion);
    }

    private ResultActions conTokenGet(String ruta, String suToken) throws Exception {
        return mvc.perform(get(ruta).header("Authorization", "Bearer " + suToken));
    }

    private String leer(String cuerpoRespuesta, String campo) throws Exception {
        return json.readTree(cuerpoRespuesta).get(campo).asText();
    }
}
