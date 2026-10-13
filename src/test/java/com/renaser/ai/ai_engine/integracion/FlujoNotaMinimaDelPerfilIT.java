package com.renaser.ai.ai_engine.integracion;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.renaser.ai.ai_engine.integracion.soporte.ImagenesDeContenedores;
import com.renaser.ai.ai_engine.perfilintegral.dto.DtosCalificacionIa.NotaCriterioIa;
import com.renaser.ai.ai_engine.perfilintegral.dto.DtosCalificacionIa.NotaRespuestaIa;
import com.renaser.ai.ai_engine.perfilintegral.dto.DtosCalificacionIa.ResultadoCv;
import com.renaser.ai.ai_engine.perfilintegral.dto.DtosCalificacionIa.ResultadoEvaluador;
import com.renaser.ai.ai_engine.perfilintegral.dto.DtosCalificacionIa.ResultadoPerfil;
import com.renaser.ai.ai_engine.perfilintegral.service.PuenteCalificacionIa;
import com.renaser.ai.ai_engine.postulacion.service.PaseAutomatico;
import com.renaser.ai.ai_engine.seguridad.dto.ContextoUsuario;
import com.renaser.ai.ai_engine.vacante.service.ServicioVacantesPanel;

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
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.rabbitmq.RabbitMQContainer;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * La nota mínima del Perfil Integral para pasar sola a la prueba, de punta a punta
 * (specs/umbral-del-perfil-integral.md, V72).
 *
 * <p>Tres vacantes, todas con la prueba del editor:
 * <ul>
 *   <li><b>con banco</b> —preguntas propias, una abierta de 100 puntos— y «Calificar y avanzar
 *       sola» encendido;</li>
 *   <li><b>sin banco</b>, con el interruptor encendido: la nota sale del currículum;</li>
 *   <li><b>sin pase</b>: con banco y el interruptor apagado.</li>
 * </ul>
 *
 * <p>La IA está apagada: lo que haría al terminar entra por su puente, igual que lo hace el
 * agente. Con el currículum sin notar, la nota del Perfil Integral de la vacante con banco es
 * exactamente la de su abierta: así se fijan 72,40, 59,99 o ninguna.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@Testcontainers
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@DisplayName("La nota mínima del Perfil Integral para pasar sola a la prueba")
class FlujoNotaMinimaDelPerfilIT {

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

    private static final List<String> CRITERIOS_CV = List.of("CV_RESULTADOS", "CV_COMPLEJIDAD",
            "CV_SISTEMAS", "CV_PERSONAS", "CV_APRENDIZAJE", "CV_INICIATIVA", "CV_HABILIDADES",
            "CV_EVIDENCIA");

    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired PuenteCalificacionIa puente;
    @Autowired PaseAutomatico paseAutomatico;
    @Autowired ServicioVacantesPanel servicioVacantes;
    @Autowired PlatformTransactionManager transacciones;
    final ObjectMapper json = new ObjectMapper();

    static String tokenTalento;
    static long areaId;
    static long puestoId;
    static long conBanco;
    static long sinBanco;
    static long sinPase;
    static Instant cierre;
    static final Map<String, Long> postulacion = new HashMap<>();
    static final Map<String, String> codigo = new HashMap<>();
    static final Map<String, String> token = new HashMap<>();

    // ============ Las tres vacantes ============

    @Test
    @Order(1)
    @DisplayName("Tres vacantes con la prueba del editor; la mínima nace en 60 (AC-09)")
    void lasTresVacantes() throws Exception {
        tokenTalento = leer(mvc.perform(post("/api/v1/panel/auth/dev-login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"usuarioRenaserOsId\":\"dev-talento\"}"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString(), "token");
        jdbc.update("INSERT INTO area (organizacion_id, nombre, es_activa) VALUES (1, 'Cajas', true)");
        areaId = jdbc.queryForObject("SELECT id FROM area ORDER BY id DESC LIMIT 1", Long.class);
        puestoId = Long.parseLong(leer(conToken(post("/api/v1/panel/puestos"), tokenTalento, """
                {"codigo": "CAJERO_V72", "nombre": "Cajero principal",
                 "nivelPuestoCodigo": "EJECUCION", "familiaCodigo": "OPERACIONES"}""")
                .andReturn().getResponse().getContentAsString(), "id"));
        cierre = Instant.now().plus(Duration.ofDays(10)).truncatedTo(ChronoUnit.SECONDS);

        conBanco = crearVacante("Cajero principal");
        preguntasPropias(conBanco);
        pruebaPropia(conBanco);
        publicar(conBanco, true);

        sinBanco = crearVacante("Cajero de apoyo");
        conToken(post("/api/v1/panel/vacantes/" + sinBanco + "/aplicacion-evaluacion"), tokenTalento,
                "{\"aplica\":false}").andExpect(status().isOk());
        pruebaPropia(sinBanco);
        publicar(sinBanco, true);

        sinPase = crearVacante("Cajero de turno");
        preguntasPropias(sinPase);
        pruebaPropia(sinPase);
        publicar(sinPase, false);

        // La mínima nace en 60 y viaja en la vacante del panel
        for (long vacante : List.of(conBanco, sinBanco, sinPase)) {
            assertThat(respuesta(conTokenGet("/api/v1/panel/vacantes/" + vacante, tokenTalento))
                    .get("notaMinima").asInt()).isEqualTo(60);
        }
    }

    // ============ El recorrido con banco ============

    @Test
    @Order(2)
    @DisplayName("Al entregar el banco queda «calificando», sin prueba, sin correo ni campana de la prueba (AC-01)")
    void alEntregarEsperaLaNota() throws Exception {
        postular("ana", conBanco);
        responderYEntregar("ana");

        assertThat(estado("ana")).isEqualTo("PERFIL_CALIFICANDO");
        sinPrueba("ana");
    }

    @Test
    @Order(3)
    @DisplayName("La IA cierra con 72,40: pasa a la prueba, con un correo, un aviso y el motivo con la nota y la mínima (AC-02)")
    void conLaNotaPasa() throws Exception {
        laIaCierraConElBanco("ana", "72.40");

        assertThat(estado("ana")).isEqualTo("PRUEBA_TURNO_CANDIDATO");
        assertThat(contar("select count(*) from intento_prueba where postulacion_id = "
                + postulacion.get("ana"))).isEqualTo(1);
        assertThat(correos("ana")).containsOnlyOnce("PRUEBA_DISPONIBLE");
        assertThat(avisos("ana")).containsOnlyOnce("PRUEBA_DISPONIBLE");
        assertThat(pasos("ana")).endsWith(
                "PERFIL_POR_CONFIRMAR | null",
                "PRUEBA_TURNO_CANDIDATO | Pase automático: nota del Perfil Integral 72,40 (mínima 60)");
        // La ficha no tiene nada que explicar: ya está en la prueba
        assertThat(ficha("ana").get("porQueEsperaElPase").isNull()).isTrue();
    }

    @Test
    @Order(4)
    @DisplayName("La IA cierra con 59,99: se queda en «por confirmar», sin prueba ni avisos, y la ficha dice que no llega (AC-03)")
    void sinLlegarSeQueda() throws Exception {
        postular("beto", conBanco);
        responderYEntregar("beto");

        laIaCierraConElBanco("beto", "59.99");

        assertThat(estado("beto")).isEqualTo("PERFIL_POR_CONFIRMAR");
        sinPrueba("beto");
        assertThat(ficha("beto").get("porQueEsperaElPase").asText())
                .isEqualTo("Nota 59,99: no llega a la mínima de la vacante (60)");
    }

    @Test
    @Order(5)
    @DisplayName("Sin nota completa (abierta sin nota) se queda en revisión; sin cierre (IA fallida), en «calificando» (AC-04)")
    void sinNotaCompletaSeQueda() throws Exception {
        postular("caro", conBanco);
        responderYEntregar("caro");
        // La IA cierra el retrato, pero la abierta se quedó sin nota: no hay nota del banco
        puente.cerrarPerfilIntegral(postulacion.get("caro"), null, retrato());

        assertThat(estado("caro")).isEqualTo("PERFIL_POR_CONFIRMAR");
        sinPrueba("caro");
        assertThat(ficha("caro").get("porQueEsperaElPase").asText())
                .isEqualTo("Sin nota completa: revisa el Perfil Integral");

        // Y si la IA ni siquiera cierra, sigue calificando, sin prueba
        postular("dani", conBanco);
        responderYEntregar("dani");
        jdbc.update("""
                insert into trabajo_ia (organizacion_id, agente_codigo, postulacion_id, estado, modo,
                                        intentos, terminado_en)
                values (1, 'POTENCIAL_RIESGO', ?, 'FALLIDO', 'FINA', 3, now())""", postulacion.get("dani"));
        assertThat(estado("dani")).isEqualTo("PERFIL_CALIFICANDO");
        sinPrueba("dani");
    }

    @Test
    @Order(6)
    @DisplayName("Una nota provisional de 90, anterior al retrato, no da el pase (AC-05)")
    void laProvisionalNoCuenta() throws Exception {
        // La provisional de lo cerrado de un banco clásico se escribe al entregar, antes del
        // retrato. Se fabrica así sobre una postulación entregada y con su retrato, en «por
        // confirmar»: si el cierre no la recalculó, ninguna vuelta del pase la toma por buena.
        postular("eva", conBanco);
        responderYEntregar("eva");
        long eva = postulacion.get("eva");
        jdbc.update("""
                insert into perfil_talento (postulacion_id, confianza_evidencia, version_pesos_id,
                                            actualizado_en, creado_en)
                values (?, 70, (select version_pesos_id from vacante where id = ?), now(), now())""",
                eva, conBanco);
        jdbc.update("""
                insert into nota_etapa (postulacion_id, etapa_codigo, puntaje, version_pesos_id,
                                        calculada_en, creado_en)
                values (?, 'PERFIL_INTEGRAL', 90, (select version_pesos_id from vacante where id = ?),
                        now() - interval '10 minutes', now())""", eva, conBanco);
        jdbc.update("update postulacion set estado_codigo = 'PERFIL_POR_CONFIRMAR' where id = ?", eva);

        assertThat(paseAutomatico.avanzarSiToca(eva)).isFalse();

        assertThat(estado("eva")).isEqualTo("PERFIL_POR_CONFIRMAR");
        sinPrueba("eva");
    }

    @Test
    @Order(7)
    @DisplayName("Un ajuste a mano sube a 63,10 a quien esperaba: pasa con su correo y su campana; si ya está en la prueba y baja, no se le toca (AC-06)")
    void unAjusteQueSubeLaNotaPasa() throws Exception {
        long beto = postulacion.get("beto");
        int correosAntes = correos("beto").size();

        ajustarLaAbierta("beto", "63.10");

        assertThat(estado("beto")).isEqualTo("PRUEBA_TURNO_CANDIDATO");
        assertThat(contar("select count(*) from intento_prueba where postulacion_id = " + beto)).isEqualTo(1);
        assertThat(correos("beto")).hasSize(correosAntes + 1).endsWith("PRUEBA_DISPONIBLE");
        assertThat(avisos("beto")).containsOnlyOnce("PRUEBA_DISPONIBLE");
        assertThat(ultimoPaso("beto"))
                .isEqualTo("PRUEBA_TURNO_CANDIDATO | Pase automático tras recalcular: nota 63,10 (mínima 60)");

        // Ya en la prueba, un ajuste que la baja no la mueve
        int transiciones = contar("select count(*) from transicion_estado where postulacion_id = " + beto);
        ajustarLaAbierta("beto", "20");
        assertThat(estado("beto")).isEqualTo("PRUEBA_TURNO_CANDIDATO");
        assertThat(contar("select count(*) from transicion_estado where postulacion_id = " + beto))
                .isEqualTo(transiciones);

        // Y un ajuste que se queda corto deja a quien espera donde está, con la ficha al día
        ajustarLaAbierta("caro", "55");
        assertThat(estado("caro")).isEqualTo("PERFIL_POR_CONFIRMAR");
        sinPrueba("caro");
        assertThat(ficha("caro").get("porQueEsperaElPase").asText())
                .isEqualTo("Nota 55,00: no llega a la mínima de la vacante (60)");
    }

    // ============ Sin banco ============

    @Test
    @Order(8)
    @DisplayName("Sin banco: al postular espera la nota del CV en «por confirmar», y con ella pasa (AC-07)")
    void sinBancoEsperaLaNotaDelCv() throws Exception {
        postular("fede", sinBanco);

        assertThat(estado("fede")).isEqualTo("PERFIL_POR_CONFIRMAR");
        sinPrueba("fede");

        laIaCalificaElCv("fede", "75");

        assertThat(estado("fede")).isEqualTo("PRUEBA_TURNO_CANDIDATO");
        assertThat(correos("fede")).containsOnlyOnce("PRUEBA_DISPONIBLE");
        assertThat(ultimoPaso("fede"))
                .isEqualTo("PRUEBA_TURNO_CANDIDATO | Pase automático: nota del Perfil Integral 75,00 (mínima 60)");
    }

    // ============ Sin el interruptor ============

    @Test
    @Order(9)
    @DisplayName("Con el interruptor apagado nada cambia: espera al equipo y la mínima no bloquea «Avanzar» (AC-08)")
    void sinPaseNadaCambia() throws Exception {
        postular("gabi", sinPase);
        responderYEntregar("gabi");
        laIaCierraConElBanco("gabi", "30");

        assertThat(estado("gabi")).isEqualTo("PERFIL_POR_CONFIRMAR");
        sinPrueba("gabi");
        // Sin interruptor, la mínima no aplica y la ficha no dice nada de ella
        assertThat(ficha("gabi").get("porQueEsperaElPase").isNull()).isTrue();

        conToken(post("/api/v1/panel/postulaciones/" + postulacion.get("gabi") + "/confirmacion-avance"),
                tokenTalento, "{\"motivo\":\"Pasa a la prueba\"}").andExpect(status().isOk());
        assertThat(estado("gabi")).isEqualTo("PRUEBA_TURNO_CANDIDATO");
        assertThat(correos("gabi")).containsOnlyOnce("PRUEBA_DISPONIBLE");
    }

    @Test
    @Order(10)
    @DisplayName("Encender el interruptor no mueve a quien ya esperaba; dos recálculos a la vez lo pasan una sola vez")
    void encenderNoMueveYDosRecalculosPasanUnaVez() throws Exception {
        postular("hugo", sinPase);
        responderYEntregar("hugo");
        laIaCierraConElBanco("hugo", "80");
        assertThat(estado("hugo")).isEqualTo("PERFIL_POR_CONFIRMAR");

        interruptor(sinPase, true);
        assertThat(estado("hugo")).isEqualTo("PERFIL_POR_CONFIRMAR");
        sinPrueba("hugo");

        // Dos recálculos que llegan a la vez: la fila bloqueada deja pasar a uno solo
        long hugo = postulacion.get("hugo");
        CountDownLatch alaVez = new CountDownLatch(1);
        ExecutorService hilos = Executors.newFixedThreadPool(2);
        List<Boolean> pasaron = new ArrayList<>();
        try {
            List<Future<Boolean>> intentos = new ArrayList<>();
            for (int i = 0; i < 2; i++) {
                intentos.add(hilos.submit(() -> {
                    alaVez.await();
                    return paseAutomatico.avanzarTrasRecalcular(hugo);
                }));
            }
            alaVez.countDown();
            for (Future<Boolean> f : intentos) {
                pasaron.add(f.get(60, TimeUnit.SECONDS));
            }
        } finally {
            hilos.shutdownNow();
        }

        assertThat(pasaron).containsExactlyInAnyOrder(true, false);
        assertThat(estado("hugo")).isEqualTo("PRUEBA_TURNO_CANDIDATO");
        assertThat(contar("select count(*) from intento_prueba where postulacion_id = " + hugo)).isEqualTo(1);
        assertThat(contar("select count(*) from transicion_estado where postulacion_id = " + hugo
                + " and estado_nuevo_codigo = 'PRUEBA_TURNO_CANDIDATO'")).isEqualTo(1);
        assertThat(correos("hugo")).containsOnlyOnce("PRUEBA_DISPONIBLE");

        interruptor(sinPase, false);
    }

    // ============ La tuerca ============

    @Test
    @Order(11)
    @DisplayName("La mínima se cambia de 0 a 100 con el interruptor encendido, rechaza lo demás y queda auditada (AC-09)")
    void laMinimaSeConfigura() throws Exception {
        String ruta = "/api/v1/panel/vacantes/" + conBanco + "/nota-minima";
        conToken(put(ruta), tokenTalento, "{\"notaMinima\":70}").andExpect(status().isOk());
        assertThat(respuesta(conTokenGet("/api/v1/panel/vacantes/" + conBanco, tokenTalento))
                .get("notaMinima").asInt()).isEqualTo(70);
        Map<String, Object> auditada = jdbc.queryForMap("""
                select valor_anterior::text as antes, valor_nuevo::text as despues, usuario_id
                  from auditoria where accion = 'fijar_nota_minima' and entidad_id = ?
                 order by id desc limit 1""", conBanco);
        assertThat(json.readTree((String) auditada.get("antes")).get("notaMinima").asInt()).isEqualTo(60);
        assertThat(json.readTree((String) auditada.get("despues")).get("notaMinima").asInt()).isEqualTo(70);
        assertThat(auditada.get("usuario_id")).isNotNull();

        for (String mala : List.of("70.5", "101", "-1", "null")) {
            conToken(put(ruta), tokenTalento, "{\"notaMinima\":" + mala + "}")
                    .andExpect(status().isBadRequest());
        }
        // Con el interruptor apagado no se toca, como dice la tuerca
        conToken(put("/api/v1/panel/vacantes/" + sinPase + "/nota-minima"), tokenTalento,
                "{\"notaMinima\":70}").andExpect(status().isConflict());
        assertThat(respuesta(conTokenGet("/api/v1/panel/vacantes/" + conBanco, tokenTalento))
                .get("notaMinima").asInt()).isEqualTo(70);
    }

    @Test
    @Order(12)
    @DisplayName("Con 65 esperando y mínima 70, bajarla a 60 no mueve a nadie (AC-10)")
    void bajarLaMinimaNoMueveANadie() throws Exception {
        for (String quien : List.of("ines", "jose")) {
            postular(quien, conBanco);
            responderYEntregar(quien);
            laIaCierraConElBanco(quien, "65");
            assertThat(estado(quien)).isEqualTo("PERFIL_POR_CONFIRMAR");
        }
        int transiciones = contar("select count(*) from transicion_estado where postulacion_id in ("
                + postulacion.get("ines") + "," + postulacion.get("jose") + ")");

        conToken(put("/api/v1/panel/vacantes/" + conBanco + "/nota-minima"), tokenTalento,
                "{\"notaMinima\":60}").andExpect(status().isOk());

        for (String quien : List.of("ines", "jose")) {
            assertThat(estado(quien)).isEqualTo("PERFIL_POR_CONFIRMAR");
            sinPrueba(quien);
        }
        assertThat(contar("select count(*) from transicion_estado where postulacion_id in ("
                + postulacion.get("ines") + "," + postulacion.get("jose") + ")")).isEqualTo(transiciones);
    }

    @Test
    @Order(13)
    @DisplayName("Con mínima 0, cualquier nota completa pasa —también un 0—; sin nota completa, no (AC-11)")
    void conMinimaCero() throws Exception {
        conToken(put("/api/v1/panel/vacantes/" + conBanco + "/nota-minima"), tokenTalento,
                "{\"notaMinima\":0}").andExpect(status().isOk());

        postular("kiko", conBanco);
        responderYEntregar("kiko");
        laIaCierraConElBanco("kiko", "0");
        assertThat(estado("kiko")).isEqualTo("PRUEBA_TURNO_CANDIDATO");
        assertThat(ultimoPaso("kiko"))
                .isEqualTo("PRUEBA_TURNO_CANDIDATO | Pase automático: nota del Perfil Integral 0,00 (mínima 0)");

        postular("lia", conBanco);
        responderYEntregar("lia");
        puente.cerrarPerfilIntegral(postulacion.get("lia"), null, retrato());
        assertThat(estado("lia")).isEqualTo("PERFIL_POR_CONFIRMAR");
        sinPrueba("lia");

        conToken(put("/api/v1/panel/vacantes/" + conBanco + "/nota-minima"), tokenTalento,
                "{\"notaMinima\":60}").andExpect(status().isOk());
    }

    // ============ El texto legal ============

    @Test
    @Order(14)
    @DisplayName("Las cuentas nuevas firman PLATAFORMA 1.1, que dice que solo el pase depende de una nota; la 1.0 sigue intacta (AC-12)")
    void elTextoLegalNuevo() throws Exception {
        assertThat(jdbc.queryForList("""
                select distinct t.version from consentimiento c
                  join texto_consentimiento t on t.id = c.texto_consentimiento_id
                  join usuario u on u.persona_id = c.persona_id
                 where t.tipo = 'PLATAFORMA' and u.correo like '%.umbral@correo.pe'""", String.class))
                .containsExactly("1.1");

        Map<String, Object> nuevo = jdbc.queryForMap("""
                select texto, hash from texto_consentimiento t join organizacion o on o.id = t.organizacion_id
                 where o.es_plataforma and t.tipo = 'PLATAFORMA' and t.version = '1.1'""");
        String texto = (String) nuevo.get("texto");
        assertThat(texto)
                .contains("El sistema sí hace tres cosas solo, y solo la tercera depende de una nota, "
                        + "y nunca para descartarme:")
                .contains("(3) me pasa a la etapa siguiente sin esperar a nadie cuando la empresa "
                        + "configuró su vacante para avanzar sola y mi nota del Perfil Integral llega a "
                        + "la mínima que fijó; si no llega, no me descarta: una persona revisa mi caso.")
                .doesNotContain("ninguna de las tres depende de una nota");
        assertThat(nuevo.get("hash")).isEqualTo(sha256(texto));

        // La 1.0 no se toca: quien la aceptó sigue ligado a ese texto exacto
        Map<String, Object> viejo = jdbc.queryForMap("""
                select texto, hash from texto_consentimiento t join organizacion o on o.id = t.organizacion_id
                 where o.es_plataforma and t.tipo = 'PLATAFORMA' and t.version = '1.0'""");
        assertThat((String) viejo.get("texto")).contains("ninguna de las tres depende de una nota");
        assertThat(viejo.get("hash")).isEqualTo(sha256((String) viejo.get("texto")));
        // Y, salvo ese párrafo, los dos textos son el mismo
        assertThat(texto.replaceAll("El sistema sí hace tres cosas solo.*?lo revise\\.", ""))
                .isEqualTo(((String) viejo.get("texto"))
                        .replaceAll("El sistema sí hace tres cosas solo.*?lo revise\\.", ""));
    }

    @Test
    @Order(15)
    @DisplayName("Ninguna nota llevó a nadie a «No continúa» (AC-13)")
    void ningunaNotaDescarta() {
        assertThat(contar("select count(*) from transicion_estado where estado_nuevo_codigo = 'NO_CONTINUA'"))
                .isZero();
    }

    @Test
    @Order(16)
    @DisplayName("La vacante dice quién puede configurar el pase, igual que la API: RESPONSABLE_AREA sí, solo ver_vacantes no (casos límite: sin permiso)")
    void quienPuedeConfigurarElPase() throws Exception {
        String detalle = "/api/v1/panel/vacantes/" + conBanco;
        String ruta = detalle + "/nota-minima";
        JsonNode comoTalento = respuesta(conTokenGet(detalle, tokenTalento));
        int minima = comoTalento.get("notaMinima").asInt();
        assertThat(comoTalento.get("puedeConfigurarElPase").asBoolean()).isTrue();

        // Ve la vacante y nada más: la tuerca le enseña la mínima desactivada, y la API la rechaza
        String soloVe = cuentaConRol("qa-umbral-solo-ve", "QA_UMBRAL_SOLO_VE", "ver_vacantes");
        JsonNode comoSoloVe = respuesta(conTokenGet(detalle, soloVe));
        assertThat(comoSoloVe.get("puedeConfigurarElPase").asBoolean()).isFalse();
        assertThat(comoSoloVe.get("notaMinima").asInt()).isEqualTo(minima);
        int auditadas = contar("select count(*) from auditoria where accion = 'fijar_nota_minima'");
        conToken(put(ruta), soloVe, "{\"notaMinima\":50}").andExpect(status().isForbidden());
        conToken(post(detalle + "/calificacion-automatica"), soloVe, "{\"activa\":false}")
                .andExpect(status().isForbidden());

        // RESPONSABLE_AREA no edita la vacante pero sí fija la mínima: el booleano no es puedeEditar
        String responsable = cuentaConRolSembrado("qa-umbral-responsable", "RESPONSABLE_AREA");
        JsonNode comoResponsable = respuesta(conTokenGet(detalle, responsable));
        assertThat(comoResponsable.get("puedeConfigurarElPase").asBoolean()).isTrue();
        assertThat(comoResponsable.get("puedeEditar").asBoolean()).isFalse();
        conToken(put(ruta), responsable, "{\"notaMinima\":" + minima + "}").andExpect(status().isOk());
        assertThat(contar("select count(*) from auditoria where accion = 'fijar_nota_minima'"))
                .as("solo la de RESPONSABLE_AREA: el 403 no deja rastro")
                .isEqualTo(auditadas + 1);
    }

    @Test
    @Order(17)
    @DisplayName("La mínima y el interruptor guardados a la vez no se pisan: lo que se audita es lo que queda (AC-09, casos límite: se apaga el interruptor, QA-F04)")
    void laMinimaYElInterruptorALaVezNoSePisan() throws Exception {
        // Quien guarda, como lo haría el equipo desde la tuerca: el permiso lo mira el controlador
        ContextoUsuario equipo = new ContextoUsuario(1L, null, 1L, "EQUIPO", List.of(),
                Map.of("elegir_plantilla_evaluacion", "TODO"));
        long vacante = sinBanco;

        // 1. La mínima va primero y el apagado llega mientras su transacción sigue abierta.
        //    Antes, el apagado leía la fila sin esperar, confirmaba, y al confirmar la mínima
        //    reescribía la fila entera con el pase encendido: los dos 200 y auditados, y el
        //    pase seguía encendido tras «apagarlo».
        interruptor(vacante, true);
        conToken(put("/api/v1/panel/vacantes/" + vacante + "/nota-minima"), tokenTalento,
                "{\"notaMinima\":60}").andExpect(status().isOk());
        Carrera minimaPrimero = conLaPrimeraAbierta(
                () -> servicioVacantes.fijarNotaMinima(equipo, vacante, new BigDecimal("75")),
                () -> servicioVacantes.activarCalificacionAutomatica(equipo, vacante, false));

        assertThat(minimaPrimero.fallo()).isNull();
        Map<String, Object> fila = jdbc.queryForMap(
                "select calificacion_automatica, nota_minima from vacante where id = ?", vacante);
        assertThat(fila.get("calificacion_automatica")).isEqualTo(false);
        assertThat((BigDecimal) fila.get("nota_minima")).isEqualByComparingTo("75");
        assertThat(ultimaAuditada("fijar_nota_minima", vacante, "notaMinima")).containsExactly("60", "75");
        assertThat(ultimaAuditada("activar_calificacion_automatica", vacante, "calificacionAutomatica"))
                .containsExactly("true", "false");
        assertThat(minimaPrimero.esperoALaPrimera())
                .as("el apagado espera a que la mínima confirme").isTrue();

        // 2. Al revés: el apagado va primero y la mínima llega mientras sigue abierto. Antes la
        //    mínima leía el pase encendido, guardaba y auditaba 75 → 80, y el apagado la
        //    reescribía con 75. Ahora espera, ve el pase apagado y se rechaza sin auditar.
        interruptor(vacante, true);
        int minimasAuditadas = contar("select count(*) from auditoria where accion = 'fijar_nota_minima' "
                + "and entidad_id = " + vacante);
        Carrera apagadoPrimero = conLaPrimeraAbierta(
                () -> servicioVacantes.activarCalificacionAutomatica(equipo, vacante, false),
                () -> servicioVacantes.fijarNotaMinima(equipo, vacante, new BigDecimal("80")));

        fila = jdbc.queryForMap(
                "select calificacion_automatica, nota_minima from vacante where id = ?", vacante);
        assertThat(fila.get("calificacion_automatica")).isEqualTo(false);
        assertThat((BigDecimal) fila.get("nota_minima")).isEqualByComparingTo("75");
        assertThat(contar("select count(*) from auditoria where accion = 'fijar_nota_minima' "
                + "and entidad_id = " + vacante)).as("la rechazada no deja rastro").isEqualTo(minimasAuditadas);
        assertThat(apagadoPrimero.fallo())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Calificar y avanzar sola");
        assertThat(ultimaAuditada("activar_calificacion_automatica", vacante, "calificacionAutomatica"))
                .containsExactly("true", "false");
        assertThat(apagadoPrimero.esperoALaPrimera())
                .as("la mínima espera a que el apagado confirme").isTrue();
    }

    /** La vacante en borrador y sin postulantes de las carreras con el resto de la tuerca. */
    static long deLaTuerca;

    @Test
    @Order(18)
    @DisplayName("La mínima y «De dónde salen sus preguntas», guardadas a la vez, no se pisan: lo auditado es lo que queda (AC-09, QA-F05)")
    void laMinimaYElOrigenALaVezNoSePisan() throws Exception {
        ContextoUsuario equipo = new ContextoUsuario(1L, null, 1L, "EQUIPO", List.of(),
                Map.of("elegir_plantilla_evaluacion", "TODO"));
        // En borrador y sin nadie dentro: el origen de las preguntas solo se cambia así
        deLaTuerca = crearVacante("Cajero de la tuerca");
        long vacante = deLaTuerca;
        interruptor(vacante, true);

        // 1. La mínima va primero y el origen llega mientras su transacción sigue abierta. Es
        //    el gesto de la tuerca: escribir 70 y pulsar «Sin evaluación» justo debajo. Antes el
        //    origen leía la vacante sin esperar y, al confirmar, reescribía la fila entera con
        //    la mínima de antes: los dos 200 y auditados, y la vacante seguía en 60.
        Carrera minimaPrimero = conLaPrimeraAbierta(
                () -> servicioVacantes.fijarNotaMinima(equipo, vacante, new BigDecimal("70")),
                () -> servicioVacantes.elegirOrigenDePreguntas(equipo, vacante, "SIN_EVALUACION"));

        assertThat(minimaPrimero.fallo()).isNull();
        Map<String, Object> fila = configuracion(vacante);
        assertThat((BigDecimal) fila.get("nota_minima")).as("la mínima auditada se queda")
                .isEqualByComparingTo("70");
        assertThat(fila.get("aplica_evaluacion")).isEqualTo(false);
        assertThat(fila.get("calificacion_automatica")).isEqualTo(true);
        assertThat(ultimaAuditada("fijar_nota_minima", vacante, "notaMinima")).containsExactly("60", "70");
        assertThat(ultimaAuditada("elegir_origen_preguntas", vacante, "origen"))
                .containsExactly("VACANTE", "SIN_EVALUACION");
        assertThat(minimaPrimero.esperoALaPrimera())
                .as("el origen espera a que la mínima confirme").isTrue();

        // 2. Al revés: el origen va primero y la mínima llega mientras sigue abierto. Antes la
        //    mínima no esperaba —el origen aún no había escrito— y el origen, al confirmar, la
        //    devolvía a 70. Ahora espera, parte de lo que el origen dejó y lo audita así.
        Carrera origenPrimero = conLaPrimeraAbierta(
                () -> servicioVacantes.elegirOrigenDePreguntas(equipo, vacante, "VACANTE"),
                () -> servicioVacantes.fijarNotaMinima(equipo, vacante, new BigDecimal("75")));

        assertThat(origenPrimero.fallo()).isNull();
        fila = configuracion(vacante);
        assertThat((BigDecimal) fila.get("nota_minima")).isEqualByComparingTo("75");
        assertThat(fila.get("aplica_evaluacion")).isEqualTo(true);
        assertThat(fila.get("origen_preguntas")).isEqualTo("VACANTE");
        assertThat(ultimaAuditada("fijar_nota_minima", vacante, "notaMinima")).containsExactly("70", "75");
        assertThat(ultimaAuditada("elegir_origen_preguntas", vacante, "origen"))
                .containsExactly("SIN_EVALUACION", "VACANTE");
        assertThat(origenPrimero.esperoALaPrimera())
                .as("la mínima espera a que el origen confirme").isTrue();
    }

    @Test
    @Order(19)
    @DisplayName("Apagar el pase a la vez que se elige el origen de las preguntas: queda apagado, como dice la auditoría (casos límite: se apaga el interruptor, QA-F05)")
    void elApagadoYElOrigenALaVezNoSePisan() throws Exception {
        ContextoUsuario equipo = new ContextoUsuario(1L, null, 1L, "EQUIPO", List.of(),
                Map.of("elegir_plantilla_evaluacion", "TODO"));
        long vacante = deLaTuerca;
        // Parte de sus preguntas propias y el pase encendido, acabe como acabe la anterior
        servicioVacantes.elegirOrigenDePreguntas(equipo, vacante, "VACANTE");
        interruptor(vacante, true);

        // 1. El apagado primero, el origen mientras sigue abierto. Antes el origen leía el
        //    pase encendido y, al confirmar, lo volvía a encender.
        Carrera apagadoPrimero = conLaPrimeraAbierta(
                () -> servicioVacantes.activarCalificacionAutomatica(equipo, vacante, false),
                () -> servicioVacantes.elegirOrigenDePreguntas(equipo, vacante, "SIN_EVALUACION"));

        assertThat(apagadoPrimero.fallo()).isNull();
        Map<String, Object> fila = configuracion(vacante);
        assertThat(fila.get("calificacion_automatica")).as("nadie pasa solo desde el apagado")
                .isEqualTo(false);
        assertThat(fila.get("aplica_evaluacion")).isEqualTo(false);
        assertThat(ultimaAuditada("activar_calificacion_automatica", vacante, "calificacionAutomatica"))
                .containsExactly("true", "false");
        assertThat(ultimaAuditada("elegir_origen_preguntas", vacante, "origen"))
                .containsExactly("VACANTE", "SIN_EVALUACION");
        assertThat(apagadoPrimero.esperoALaPrimera())
                .as("el origen espera a que el apagado confirme").isTrue();

        // 2. El origen primero, el apagado mientras sigue abierto. Antes el apagado confirmaba
        //    sin esperar y el origen, al confirmar después, volvía a encender el pase.
        interruptor(vacante, true);
        Carrera origenPrimero = conLaPrimeraAbierta(
                () -> servicioVacantes.elegirOrigenDePreguntas(equipo, vacante, "VACANTE"),
                () -> servicioVacantes.activarCalificacionAutomatica(equipo, vacante, false));

        assertThat(origenPrimero.fallo()).isNull();
        fila = configuracion(vacante);
        assertThat(fila.get("calificacion_automatica")).isEqualTo(false);
        assertThat(fila.get("aplica_evaluacion")).isEqualTo(true);
        assertThat(ultimaAuditada("activar_calificacion_automatica", vacante, "calificacionAutomatica"))
                .containsExactly("true", "false");
        assertThat(ultimaAuditada("elegir_origen_preguntas", vacante, "origen"))
                .containsExactly("SIN_EVALUACION", "VACANTE");
        assertThat(origenPrimero.esperoALaPrimera())
                .as("el apagado espera a que el origen confirme").isTrue();
    }

    @Test
    @Order(20)
    @DisplayName("Editar la vacante a la vez que se fija la mínima no la pisa, aunque la edición no bloquee: cada guardado escribe solo lo suyo (QA-F05)")
    void editarALaVezQueLaMinimaNoLaPisa() throws Exception {
        ContextoUsuario equipo = new ContextoUsuario(1L, null, 1L, "EQUIPO", List.of(),
                Map.of("elegir_plantilla_evaluacion", "TODO"));
        long vacante = deLaTuerca;
        // Parte del pase encendido y una mínima de 75, acaben como acaben las anteriores
        interruptor(vacante, true);
        servicioVacantes.fijarNotaMinima(equipo, vacante, new BigDecimal("75"));

        // 1. La mínima primero, la edición mientras sigue abierta: la edición leía la mínima de
        //    antes y, al confirmar, la reescribía con la fila entera.
        conLaPrimeraAbierta(
                () -> servicioVacantes.fijarNotaMinima(equipo, vacante, new BigDecimal("80")),
                editarElTitulo(vacante, "Cajero de la tuerca, turno tarde"));

        Map<String, Object> fila = configuracion(vacante);
        assertThat((BigDecimal) fila.get("nota_minima")).isEqualByComparingTo("80");
        assertThat(fila.get("titulo")).isEqualTo("Cajero de la tuerca, turno tarde");
        assertThat(ultimaAuditada("fijar_nota_minima", vacante, "notaMinima")).containsExactly("75", "80");

        // 2. La edición primero: no bloquea, así que la mínima no la espera y confirma antes
        //    que ella. Al confirmar después, la edición tampoco la toca.
        conLaPrimeraAbierta(
                editarElTitulo(vacante, "Cajero de la tuerca, turno noche"),
                () -> servicioVacantes.fijarNotaMinima(equipo, vacante, new BigDecimal("85")));

        fila = configuracion(vacante);
        assertThat((BigDecimal) fila.get("nota_minima")).isEqualByComparingTo("85");
        assertThat(fila.get("titulo")).isEqualTo("Cajero de la tuerca, turno noche");
        assertThat(fila.get("calificacion_automatica")).isEqualTo(true);
        assertThat(ultimaAuditada("fijar_nota_minima", vacante, "notaMinima")).containsExactly("80", "85");
    }

    // ============ Ayudas ============

    /** Lo que la tuerca configura de la vacante, leído de la base. */
    private Map<String, Object> configuracion(long vacante) {
        return jdbc.queryForMap("""
                select nota_minima, calificacion_automatica, aplica_evaluacion, origen_preguntas,
                       titulo
                  from vacante where id = ?""", vacante);
    }

    /** El lápiz de la vacante cambiando solo el título: la edición no pasa por el bloqueo. */
    private Runnable editarElTitulo(long vacante, String titulo) {
        Map<String, Object> suya = jdbc.queryForMap(
                "select solicitud_talento_id, puesto_id from vacante where id = ?", vacante);
        String cuerpo = """
                {"solicitudTalentoId": %d, "puestoId": %d, "titulo": "%s",
                 "descripcion": "Lleva la caja de la tienda", "tipoCierre": "PERMANENTE",
                 "responsableUsuarioId": 1}""".formatted(suya.get("solicitud_talento_id"),
                suya.get("puesto_id"), titulo);
        return () -> {
            try {
                conToken(put("/api/v1/panel/vacantes/" + vacante), tokenTalento, cuerpo)
                        .andExpect(status().isOk());
            } catch (Exception e) {
                throw new IllegalStateException(e);
            }
        };
    }

    /** Cómo acabó la segunda escritura: si tuvo que esperar a la primera y qué lanzó, si algo. */
    private record Carrera(boolean esperoALaPrimera, Throwable fallo) {}

    /**
     * Dos escrituras sobre la misma vacante, con la primera retenida a propósito: su
     * transacción queda abierta, con el cambio hecho y sin confirmar, mientras llega la
     * segunda. Es el hueco que la tuerca abre al mandar la mínima y el interruptor en el mismo
     * milisegundo, pero ancho y fijo, así que la carrera se reproduce siempre y no por suerte.
     *
     * <p>Se le dan dos segundos a la segunda para terminar sola: con la fila bloqueada no
     * puede, y eso es {@code esperoALaPrimera}. Después se suelta la primera.
     */
    private Carrera conLaPrimeraAbierta(Runnable primera, Runnable segunda) throws Exception {
        TransactionTemplate transaccion = new TransactionTemplate(transacciones);
        CountDownLatch hecha = new CountDownLatch(1);
        CountDownLatch soltar = new CountDownLatch(1);
        ExecutorService hilos = Executors.newFixedThreadPool(2);
        try {
            Future<?> laPrimera = hilos.submit(() -> transaccion.executeWithoutResult(estado -> {
                primera.run();
                hecha.countDown();
                try {
                    soltar.await(30, TimeUnit.SECONDS);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }));
            assertThat(hecha.await(30, TimeUnit.SECONDS)).as("la primera escribió").isTrue();
            Future<Throwable> laSegunda = hilos.submit(() -> {
                try {
                    segunda.run();
                    return null;
                } catch (RuntimeException e) {
                    return e;
                }
            });
            boolean espero;
            try {
                laSegunda.get(2, TimeUnit.SECONDS);
                espero = false;
            } catch (TimeoutException sigueEsperando) {
                espero = true;
            }
            soltar.countDown();
            laPrimera.get(30, TimeUnit.SECONDS);
            return new Carrera(espero, laSegunda.get(30, TimeUnit.SECONDS));
        } finally {
            soltar.countDown();
            hilos.shutdownNow();
        }
    }

    /** De cuánto a cuánto dice la última auditoría de esa acción sobre la vacante. */
    private List<String> ultimaAuditada(String accion, long vacante, String campo) throws Exception {
        Map<String, Object> fila = jdbc.queryForMap("""
                select valor_anterior::text as antes, valor_nuevo::text as despues
                  from auditoria where accion = ? and entidad_id = ?
                 order by id desc limit 1""", accion, vacante);
        return List.of(json.readTree((String) fila.get("antes")).get(campo).asText(),
                json.readTree((String) fila.get("despues")).get(campo).asText());
    }

    /** Lo que deja la IA al terminar: la abierta con esa nota y el retrato cerrado. */
    private void laIaCierraConElBanco(String quien, String nota) {
        long id = postulacion.get(quien);
        long respuestaId = respuestaDe(id);
        puente.guardarNotasPorPuntos(id, null, new ResultadoEvaluador(List.of(new NotaRespuestaIa(
                respuestaId, new BigDecimal(nota), "Da el monto y la causa", "cita",
                new BigDecimal("80"), null, null, null, null, null))), 1, false);
        puente.cerrarPerfilIntegral(id, null, retrato());
    }

    /** Lo que deja la IA al calificar el currículum de quien postula sin banco. */
    private void laIaCalificaElCv(String quien, String nota) {
        long id = postulacion.get(quien);
        puente.guardarEvidenciaCv(id, null, new ResultadoCv(CRITERIOS_CV.stream()
                .map(c -> new NotaCriterioIa(c, new BigDecimal(nota), "Hay un resultado medido",
                        "cuadró tres sedes"))
                .toList(), List.of(), new BigDecimal("70")));
        puente.cerrarPerfilIntegral(id, null, retrato());
    }

    private static ResultadoPerfil retrato() {
        return new ResultadoPerfil(new BigDecimal("70"), new BigDecimal("65"), new BigDecimal("50"),
                new BigDecimal("80"), "Cuadra cajas y explica sus cifras", List.of(), List.of());
    }

    private void ajustarLaAbierta(String quien, String nota) throws Exception {
        long id = postulacion.get(quien);
        conToken(put("/api/v1/panel/postulaciones/" + id + "/evaluacion/respuestas/" + respuestaDe(id)
                + "/nota"), tokenTalento, "{\"puntaje\":" + nota + ",\"motivo\":\"Revisado a mano\"}")
                .andExpect(status().isOk());
    }

    private long respuestaDe(long postulacionId) {
        return jdbc.queryForObject("""
                select r.id from respuesta r join postulacion p on p.evaluacion_id = r.evaluacion_id
                 where p.id = ?""", Long.class, postulacionId);
    }

    private void interruptor(long vacante, boolean activa) throws Exception {
        conToken(post("/api/v1/panel/vacantes/" + vacante + "/calificacion-automatica"), tokenTalento,
                "{\"activa\":" + activa + "}").andExpect(status().isOk());
    }

    /** Ni intento, ni correo, ni aviso de la prueba. */
    private void sinPrueba(String quien) {
        assertThat(contar("select count(*) from intento_prueba where postulacion_id = "
                + postulacion.get(quien))).as(quien).isZero();
        assertThat(correos(quien)).as(quien).doesNotContain("PRUEBA_DISPONIBLE");
        assertThat(avisos(quien)).as(quien).doesNotContain("PRUEBA_DISPONIBLE");
    }

    private JsonNode ficha(String quien) throws Exception {
        return respuesta(conTokenGet("/api/v1/panel/postulaciones/" + postulacion.get(quien), tokenTalento));
    }

    private void postular(String quien, long vacante) throws Exception {
        String correo = quien + ".umbral@correo.pe";
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

    /** Empieza la evaluación, responde todo y la entrega. */
    private void responderYEntregar(String quien) throws Exception {
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
        mvc.perform(post(base + "/entrega").header("Authorization", "Bearer " + token.get(quien)))
                .andExpect(status().isOk());
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

    private String ultimoPaso(String quien) {
        List<String> suyos = pasos(quien);
        return suyos.get(suyos.size() - 1);
    }

    private List<String> correos(String quien) {
        return jdbc.queryForList("select plantilla_correo_codigo from correo_enviado where usuario_id = ? "
                + "and plantilla_correo_codigo <> 'CUENTA_CREADA' order by id", String.class, usuarioDe(quien));
    }

    private List<String> avisos(String quien) {
        return jdbc.queryForList("select tipo from aviso_portal where usuario_id = ? order by id",
                String.class, usuarioDe(quien));
    }

    private void preguntasPropias(long vacante) throws Exception {
        String base = "/api/v1/panel/vacantes/" + vacante + "/preguntas-propias";
        conToken(post(base + "/preguntas"), tokenTalento, """
                {"tipo":"ABIERTA","enunciado":"Cuéntanos un cierre de caja difícil y cómo lo resolviste",
                 "puntos":100,"queDebeTener":"El monto, la causa y en cuánto tiempo lo cerró"}""")
                .andExpect(status().isOk());
        conToken(post(base + "/publicacion"), tokenTalento, null).andExpect(status().isOk());
    }

    private void pruebaPropia(long vacante) throws Exception {
        String base = "/api/v1/panel/vacantes/" + vacante + "/prueba-propia";
        conToken(put(base + "/borrador"), tokenTalento, """
                {"guiaCalificacion":"Mira las cifras","enunciado":"La caja de marzo no cuadra por 1.200 soles.",
                 "modalidad":"CRONOMETRADA","duracionMinutos":90}""").andExpect(status().isOk());
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
        interruptor(vacante, paseAutomatico);
    }

    private long crearVacante(String titulo) throws Exception {
        long solicitudId = Long.parseLong(leer(conToken(post("/api/v1/panel/solicitudes"), tokenTalento, """
                {"areaId": %d, "puestoId": %d, "urgencia": "NORMAL",
                 "nivelPuestoCodigo": "EJECUCION", "familiaCodigo": "OPERACIONES",
                 "resultadoPrincipal": "Cajas cuadradas cada día",
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
                 "titulo": "%s", "descripcion": "Lleva la caja de la tienda",
                 "tipoCierre": "PERMANENTE", "responsableUsuarioId": 1}"""
                .formatted(solicitudId, puestoId, titulo))
                .andReturn().getResponse().getContentAsString(), "id"));
    }

    private int contar(String sql) {
        return jdbc.queryForObject(sql, Integer.class);
    }

    /** Un rol propio con esos permisos (alcance TODO) y una cuenta de panel que lo tiene. */
    private String cuentaConRol(String renaserOsId, String rolCodigo, String... permisos)
            throws Exception {
        Long rolId = jdbc.queryForObject("""
                insert into rol (organizacion_id, codigo, nombre, descripcion)
                values (1, ?, ?, 'Sembrado por la prueba de la nota mínima') returning id""",
                Long.class, rolCodigo, rolCodigo);
        for (String permiso : permisos) {
            jdbc.update("""
                    insert into rol_permiso (rol_id, permiso_id, alcance)
                    select ?, id, 'TODO' from permiso where codigo = ?""", rolId, permiso);
        }
        return cuentaConRolSembrado(renaserOsId, rolCodigo);
    }

    /**
     * Una cuenta de panel con un rol de la organización 1, insertada a mano: el dev-login solo
     * crea sola la primera cuenta que entra.
     */
    private String cuentaConRolSembrado(String renaserOsId, String rolCodigo) throws Exception {
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

    private static String sha256(String texto) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(texto.getBytes(StandardCharsets.UTF_8)));
        } catch (java.security.NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
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
