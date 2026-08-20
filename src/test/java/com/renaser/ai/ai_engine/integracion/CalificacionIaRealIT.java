package com.renaser.ai.ai_engine.integracion;

import com.renaser.ai.ai_engine.ai.dto.RespuestaModelo;
import com.renaser.ai.ai_engine.ai.model.TrabajoIa;
import com.renaser.ai.ai_engine.ai.repository.TrabajoIaRepository;
import com.renaser.ai.ai_engine.ai.service.ClienteModelo;
import com.renaser.ai.ai_engine.ai.service.EjecutorAgenteIa;
import com.renaser.ai.ai_engine.ai.service.impl.AgenteEvaluador;
import com.renaser.ai.ai_engine.ai.service.impl.AgenteEvidenciaCv;
import com.renaser.ai.ai_engine.ai.service.impl.AgentePotencialRiesgo;
import com.renaser.ai.ai_engine.perfilintegral.dto.DtosCalificacionIa.CriterioConPeso;
import com.renaser.ai.ai_engine.perfilintegral.dto.DtosCalificacionIa.InsumoCv;
import com.renaser.ai.ai_engine.perfilintegral.dto.DtosCalificacionIa.InsumoPerfil;
import com.renaser.ai.ai_engine.perfilintegral.dto.DtosCalificacionIa.InsumoRespuestas;
import com.renaser.ai.ai_engine.perfilintegral.dto.DtosCalificacionIa.NotaCriterioIa;
import com.renaser.ai.ai_engine.perfilintegral.dto.DtosCalificacionIa.NotaRespuestaIa;
import com.renaser.ai.ai_engine.perfilintegral.dto.DtosCalificacionIa.RespuestaAbierta;
import com.renaser.ai.ai_engine.perfilintegral.dto.DtosCalificacionIa.ResultadoCv;
import com.renaser.ai.ai_engine.perfilintegral.dto.DtosCalificacionIa.ResultadoEvaluador;
import com.renaser.ai.ai_engine.perfilintegral.dto.DtosCalificacionIa.ResultadoPerfil;

import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.core.env.Environment;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.rabbitmq.RabbitMQContainer;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Los tres agentes contra DeepSeek de verdad.
 *
 * <p><b>Por qué existe, teniendo ya FlujoCalificacionIaIT.</b> Aquella prueba sustituye el
 * modelo por un doble y comprueba todo lo que decide el sistema: qué se guarda, qué se
 * descarta, qué se reintenta y qué no puede ver la IA. Lo que un doble no puede comprobar es
 * lo único que depende del proveedor: que la clave llegue, que el modelo conteste, y que lo
 * que conteste encaje en el contrato que cada agente le pide. Eso se prueba aquí.
 *
 * <p><b>Cuesta dinero, así que es pequeña.</b> Cuatro llamadas reales: una de tanteo y una
 * por agente. No monta una postulación entera —de eso ya se encarga la otra prueba— sino que
 * le da a cada agente un insumo hecho a mano y mira qué vuelve. Toda la cobertura de reglas
 * se hace sin gastar una sola llamada.
 *
 * <p><b>Necesita {@code application-secrets.yaml} en la raíz</b>, con la clave del proveedor.
 * Si falta, la primera prueba falla diciendo exactamente eso, que es mejor que saltarse la
 * prueba en silencio y creer que todo está bien.
 *
 * <p><b>No corre sola.</b> Hace falta pedirla:
 *
 * <pre>{@code
 * RENASER_IA_REAL=si ./mvnw verify -Dit.test=CalificacionIaRealIT
 * }</pre>
 *
 * <p>La bandera nació cuando el {@code pom.xml} metía los {@code *IT.java} en la misma tanda
 * que el resto y esta prueba se disparaba con cualquier {@code mvn test}: quien clonara el
 * repositorio y lanzara las pruebas gastaba dinero sin saberlo. Hoy los {@code *IT} corren
 * aparte, con failsafe en {@code mvn verify}, pero la bandera se queda: en la integración
 * continua {@code verify} corre en cada Pull Request, sin clave y sin intención de pagar
 * llamadas, y sin la guardia esta prueba saldría roja en todas.
 *
 * <p>Se apaga por defecto y no al revés a propósito: <b>olvidarse de encenderla no cuesta
 * nada; olvidarse de apagarla, sí.</b> Y lo que aquí se comprueba —que el contrato con el
 * proveedor sigue en pie— es algo que se mira antes de publicar, no en cada compilación.
 */
@SpringBootTest
@Testcontainers
@EnabledIfEnvironmentVariable(named = "RENASER_IA_REAL", matches = ".+",
        disabledReason = "Llama a DeepSeek de verdad y gasta saldo. "
                + "Para lanzarla: RENASER_IA_REAL=si")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@DisplayName("El contrato con el proveedor de IA · solo a petición")
public class CalificacionIaRealIT {

    @Container
    @ServiceConnection
    static PostgreSQLContainer postgres = new PostgreSQLContainer("pgvector/pgvector:pg16");

    @Container
    @ServiceConnection
    static RabbitMQContainer rabbit = new RabbitMQContainer("rabbitmq:3-management-alpine");

    /**
     * Aquí <b>no</b> se toca {@code spring.ai.deepseek.api-key}, al revés que en las demás
     * pruebas: la gracia de ésta es que la clave de verdad se resuelva sola. Una propiedad
     * puesta desde el test gana a cualquier archivo, así que ponerla aquí taparía justo el
     * fallo que esta prueba busca.
     */
    @DynamicPropertySource
    static void propiedades(DynamicPropertyRegistry registro) {
        // El almacen de las pruebas vive en un mapa, no en disco.
        registro.add("app.archivos.tipo", () -> "memoria");
        // El broker de las pruebas es el contenedor, y habla en claro. Sin esto manda lo
        // que cada uno tenga en su application-secrets.yaml —hoy, un CloudAMQP con TLS— y
        // la tanda entera falla según la máquina en la que corra, que es lo peor que le
        // puede pasar a una prueba.
        registro.add("spring.rabbitmq.ssl.enabled", () -> "false");
        registro.add("spring.rabbitmq.virtual-host", () -> "/");
        registro.add("app.seguridad.jwt-secreto",
                () -> "clave-de-pruebas-suficientemente-larga-para-hmac-256-bits");
    }

    @Autowired Environment entorno;
    @Autowired ClienteModelo clienteModelo;
    @Autowired EjecutorAgenteIa ejecutor;
    @Autowired TrabajoIaRepository trabajos;
    @Autowired JdbcTemplate jdbc;

    @DisplayName("La clave del proveedor se resuelve sola")
    @Test
    @Order(1)
    void laClaveDelProveedorSeResuelveSola() {
        String clave = entorno.getProperty("spring.ai.deepseek.api-key");

        // Nunca se imprime ni se compara con un valor: solo se comprueba que exista. El
        // application.yaml la declara como ${DEEPSEEK_API_KEY:}, con valor vacío por defecto,
        // y application-secrets.yaml la trae de verdad. Como la importación de
        // spring.config.import entra DESPUÉS, gana el secreto — pero eso es una regla de
        // precedencia fácil de romper sin querer, y de ahí esta comprobación.
        assertThat(clave)
                .withFailMessage("La clave de DeepSeek no se resolvió. Hace falta "
                        + "application-secrets.yaml en la raíz del proyecto con "
                        + "spring.ai.deepseek.api-key, o la variable de entorno DEEPSEEK_API_KEY. "
                        + "Sin ella estas pruebas responderían 401.")
                .isNotNull()
                .isNotBlank();
    }

    @DisplayName("El proveedor contesta y no devuelve vacío")
    @Test
    @Order(2)
    void elProveedorContestaYNoDevuelveVacio() {
        RespuestaModelo respuesta = clienteModelo.preguntar("EVIDENCIA_CV",
                "Responde solo con json.",
                "Devuelve un objeto json con un campo \"ok\" que valga true.");

        // Vacío sería el síntoma de las dos averías que más cuestan de diagnosticar: la clave
        // mal puesta y el presupuesto de tokens agotado por el razonamiento del modelo.
        assertThat(respuesta.texto()).isNotBlank();
        assertThat(respuesta.proveedor()).isEqualTo("deepseek");
        assertThat(respuesta.modelo()).isNotBlank();
        assertThat(respuesta.tokensSalida()).isNotNull().isPositive();
    }

    @DisplayName("Evidencia del CV puntúa los ocho criterios de verdad")
    @Test
    @Order(3)
    void evidenciaCvPuntuaLosOchoCriteriosDeVerdad() {
        InsumoCv insumo = new InsumoCv(
                "Analista de procesos", "EJECUCION",
                "Sostener y mejorar el cierre contable mensual",
                """
                Analista de procesos con experiencia en cierre contable.
                [DATO NO UTILIZABLE]
                Automaticé el cierre mensual y pasó de 3 días a 4 horas.
                Documenté el proceso y quedó funcionando sin mí.
                Aprendí SQL en dos meses para poder medir el resultado.
                Formé a dos compañeras en el procedimiento nuevo.
                """,
                "Automaticé el cierre mensual y pasó de 3 días a 4 horas",
                List.of("REPOSITORIO: https://github.com/ejemplo"),
                List.of(
                        new CriterioConPeso("CV_RESULTADOS", "Resultados demostrables",
                                "Logros con evidencia verificable", new BigDecimal("20")),
                        new CriterioConPeso("CV_COMPLEJIDAD", "Complejidad y alcance",
                                "Tamaño del problema que ha manejado", new BigDecimal("5")),
                        new CriterioConPeso("CV_SISTEMAS", "Sistemas o procesos creados",
                                "Dejó algo que sigue funcionando sin él", new BigDecimal("10")),
                        new CriterioConPeso("CV_PERSONAS", "Desarrollo de personas",
                                "Formó o hizo crecer a otros", new BigDecimal("0")),
                        new CriterioConPeso("CV_APRENDIZAJE", "Velocidad de aprendizaje",
                                "Incorpora lo nuevo y mejora rápido", new BigDecimal("15")),
                        new CriterioConPeso("CV_INICIATIVA", "Iniciativa o creación",
                                "Puso en marcha algo por decisión propia", new BigDecimal("10")),
                        new CriterioConPeso("CV_HABILIDADES", "Habilidades del puesto",
                                "Lo que el puesto exige saber hacer", new BigDecimal("25")),
                        new CriterioConPeso("CV_EVIDENCIA", "Calidad de la evidencia",
                                "Lo que dice se puede comprobar", new BigDecimal("15"))));

        TrabajoIa trabajo = trabajoDePrueba(AgenteEvidenciaCv.CODIGO_AGENTE);
        ResultadoCv resultado = ejecutor.ejecutar(trabajo, "Prueba real de EVIDENCIA_CV",
                AgenteEvidenciaCv.FORMATO, insumo, ResultadoCv.class).resultado();

        assertThat(resultado.criterios()).isNotEmpty();
        // Se comprueba la forma, no los números: la nota que ponga el modelo es cosa suya, y
        // una prueba que exigiera un valor concreto fallaría cada vez que cambie de versión.
        assertThat(resultado.criterios()).allSatisfy(nota -> {
            assertThat(nota.codigo()).startsWith("CV_");
            assertThat(nota.puntaje()).isNotNull();
            assertThat(nota.explicacion()).isNotBlank();
        });
        assertThat(resultado.criterios().stream().map(NotaCriterioIa::codigo))
                .contains("CV_RESULTADOS");

        comprobarBitacora(trabajo, AgenteEvidenciaCv.CODIGO_AGENTE);
    }

    @DisplayName("El evaluador califica respuestas abiertas de verdad")
    @Test
    @Order(4)
    void elEvaluadorCalificaRespuestasAbiertasDeVerdad() {
        InsumoRespuestas insumo = new InsumoRespuestas("Analista de procesos", "EJECUCION",
                List.of(
                        new RespuestaAbierta(4001L, "CONDUCTUAL",
                                "Cuenta un error tuyo cuyo reporte te perjudicaba. "
                                        + "¿Cuándo informaste y qué cambió después?",
                                null, List.of("OWN"),
                                "Detecté que había cargado mal un tipo de cambio y el cierre "
                                        + "salía con 12 mil soles de más. Lo avisé el mismo día, "
                                        + "rehicimos el asiento y desde entonces el archivo tiene "
                                        + "una validación que compara contra la tasa oficial."),
                        new RespuestaAbierta(4002L, "CONDUCTUAL",
                                "Cuenta algo que aprendiste rápido porque lo necesitabas.",
                                null, List.of("APR"),
                                "Aprendí lo que hacía falta y salió bien.")));

        TrabajoIa trabajo = trabajoDePrueba(AgenteEvaluador.CODIGO_AGENTE);
        ResultadoEvaluador resultado = ejecutor.ejecutar(trabajo, "Prueba real de EVALUADOR",
                AgenteEvaluador.FORMATO, insumo, ResultadoEvaluador.class).resultado();

        assertThat(resultado.notas()).isNotEmpty();
        assertThat(resultado.notas()).allSatisfy(nota -> {
            assertThat(nota.respuestaId()).isIn(4001L, 4002L);
            assertThat(nota.puntaje()).isNotNull();
            assertThat(nota.puntaje().doubleValue()).isBetween(0.0, 4.0);
            assertThat(nota.explicacion()).isNotBlank();
        });

        comprobarBitacora(trabajo, AgenteEvaluador.CODIGO_AGENTE);
    }

    @DisplayName("Potencial y riesgo arma el perfil de verdad")
    @Test
    @Order(5)
    void potencialRiesgoArmaElPerfilDeVerdad() {
        InsumoPerfil insumo = new InsumoPerfil(
                "Analista de procesos", "EJECUCION",
                "Sostener y mejorar el cierre contable mensual",
                new BigDecimal("74.50"),
                List.of(new NotaCriterioIa("CV_RESULTADOS", new BigDecimal("80"),
                                "Automatizó el cierre y midió el antes y el después", null),
                        new NotaCriterioIa("CV_PERSONAS", new BigDecimal("40"),
                                "Solo menciona haber formado a dos compañeras, sin detalle", null)),
                new BigDecimal("68.00"), 18,
                new BigDecimal("75.00"),
                List.of(new NotaRespuestaIa(4001L, new BigDecimal("3"),
                        "Avisó a tiempo y dejó una validación puesta",
                        "lo avisé el mismo día", new BigDecimal("70"))),
                List.of("Respondió de forma muy distinta a dos preguntas que miden lo mismo"));

        TrabajoIa trabajo = trabajoDePrueba(AgentePotencialRiesgo.CODIGO_AGENTE);
        ResultadoPerfil resultado = ejecutor.ejecutar(trabajo, "Prueba real de POTENCIAL_RIESGO",
                AgentePotencialRiesgo.FORMATO, insumo, ResultadoPerfil.class).resultado();

        // La confianza de la evidencia es la única obligatoria: sin ella el perfil no se guarda
        assertThat(resultado.confianzaEvidencia()).isNotNull();
        assertThat(resultado.resumen()).isNotBlank();
        assertThat(resultado.hallazgos()).isNotEmpty();
        assertThat(resultado.hallazgos()).allSatisfy(hallazgo ->
                assertThat(hallazgo.tipo()).isIn("FORTALEZA", "RIESGO_CRITICO",
                        "RIESGO_DESARROLLABLE", "PREFERENCIA", "FALTA_EVIDENCIA"));

        comprobarBitacora(trabajo, AgentePotencialRiesgo.CODIGO_AGENTE);
    }

    // ============ Apoyo ============

    /**
     * Una fila de {@code trabajo_ia} sin postulación detrás.
     *
     * <p>La columna admite vacío a propósito, y aquí viene bien: lo que se prueba es la
     * conversación con el modelo, no lo que se guarda después. Montar una postulación entera
     * por cada llamada haría la prueba diez veces más lenta sin comprobar nada más.
     */
    private TrabajoIa trabajoDePrueba(String agenteCodigo) {
        Long organizacionId = jdbc.queryForObject(
                "select id from organizacion where codigo = 'RENASER'", Long.class);
        return trabajos.save(TrabajoIa.builder()
                .organizacionId(organizacionId)
                .agenteCodigo(agenteCodigo)
                .estado("EN_CURSO")
                .intentos(1)
                .tomadoEn(Instant.now())
                .creadoEn(Instant.now())
                .build());
    }

    /** Toda llamada al modelo queda escrita, con su instrucción y su versión (RF-146). */
    private void comprobarBitacora(TrabajoIa trabajo, String agenteCodigo) {
        Map<String, Object> fila = jdbc.queryForMap("""
                select agente_codigo, version_agente, instruccion_ia_id, es_exitosa, modelo,
                       proveedor, tokens_entrada, tokens_salida, duracion_ms, envio, respuesta
                from ejecucion_ia where trabajo_ia_id = ?""", trabajo.getId());

        assertThat(fila.get("agente_codigo")).isEqualTo(agenteCodigo);
        assertThat(fila.get("es_exitosa")).isEqualTo(true);
        assertThat(fila.get("instruccion_ia_id")).isNotNull();
        assertThat(fila.get("version_agente")).isNotNull();
        assertThat(fila.get("proveedor")).isEqualTo("deepseek");
        assertThat(((Number) fila.get("tokens_entrada")).intValue()).isPositive();
        assertThat(((Number) fila.get("tokens_salida")).intValue()).isPositive();
        assertThat((String) fila.get("respuesta")).isNotBlank();

        // El envío lleva la instrucción que Dirección administra desde el panel, no una
        // copia escrita en el código: si alguien la cambia, cambia lo que se manda.
        String instruccionActiva = jdbc.queryForObject(
                "select texto from instruccion_ia where agente_codigo = ? and es_activa",
                String.class, agenteCodigo);
        assertThat((String) fila.get("envio")).contains(instruccionActiva.substring(0, 60));
    }
}
