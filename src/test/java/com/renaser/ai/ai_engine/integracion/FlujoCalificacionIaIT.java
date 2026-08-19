package com.renaser.ai.ai_engine.integracion;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.renaser.ai.ai_engine.ai.dto.RespuestaModelo;
import com.renaser.ai.ai_engine.ai.service.ClienteModelo;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
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

import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.BooleanSupplier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * La calificación con inteligencia artificial, de punta a punta.
 *
 * <p>Es lo último que le faltaba al hito 2: hasta ahora el candidato entregaba su evaluación,
 * el código puntuaba lo cerrado y la postulación se quedaba en {@code PERFIL_CALIFICANDO}
 * para siempre, porque nadie ejecutaba a los tres agentes.
 *
 * <p><b>El modelo está sustituido por un doble, y su hermana no.</b> Aquí se comprueban las
 * reglas del sistema —la cola, los reintentos, la bitácora, qué se guarda y qué se descarta—,
 * y para eso hace falta que la respuesta del modelo sea siempre la misma: con respuestas que
 * cambian cada vez no se puede afirmar nada. Además incluye a propósito respuestas
 * <b>malas</b> —un criterio que no existe, una nota sin explicación, una respuesta ajena— que
 * un modelo de verdad no devolvería a demanda, y que son justo las que hay que descartar.
 *
 * <p>Que el proveedor conteste de verdad y que lo que conteste encaje en el contrato lo
 * comprueba {@code CalificacionIaRealIT}, que sí llama a DeepSeek.
 *
 * <p>Se comprueban las cuatro que sostienen esta parte:
 * <ul>
 *   <li>La IA nunca ve foto, edad, sexo ni estado civil (RF-41).
 *   <li>Toda llamada al modelo queda en {@code ejecucion_ia}, salga bien o salga mal (RF-146).
 *   <li>Una nota sin explicación no se guarda (RF-150).
 *   <li>Si la IA falla se reintenta y <b>no se inventa una nota</b> (Regla 3 del doc 03).
 * </ul>
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@Testcontainers
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@DisplayName("La calificación con inteligencia artificial")
public class FlujoCalificacionIaIT {

    @Container
    @ServiceConnection
    static PostgreSQLContainer postgres = new PostgreSQLContainer("pgvector/pgvector:pg16");

    @Container
    @ServiceConnection
    static RabbitMQContainer rabbit = new RabbitMQContainer("rabbitmq:3-management-alpine");

    @DynamicPropertySource
    static void propiedades(DynamicPropertyRegistry registro) {
        // El broker de las pruebas es el contenedor, y habla en claro. Sin esto manda lo
        // que cada uno tenga en su application-secrets.yaml —hoy, un CloudAMQP con TLS— y
        // la tanda entera falla según la máquina en la que corra, que es lo peor que le
        // puede pasar a una prueba.
        registro.add("spring.rabbitmq.ssl.enabled", () -> "false");
        registro.add("spring.rabbitmq.virtual-host", () -> "/");
        // El almacen de las pruebas vive en un mapa, no en disco: no hay ningun
        // sitio donde un curriculum pueda quedarse olvidado despues de correrlas.
        registro.add("app.archivos.tipo", () -> "memoria");
        registro.add("app.seguridad.jwt-secreto",
                () -> "clave-de-pruebas-suficientemente-larga-para-hmac-256-bits");
        registro.add("spring.ai.deepseek.api-key", () -> "clave-de-pruebas-no-se-usa");
        // Dos intentos y no tres: la prueba del fallo tiene que agotarlos, y cada intento
        // vuelve a publicar el mensaje al momento.
        registro.add("renaser.ai.calificacion.max-intentos", () -> "2");
    }

    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    final ObjectMapper json = new ObjectMapper();

    static String tokenEquipo;
    static long vacanteId;
    static String codigoBueno;
    static String codigoFallido;

    // ============ El doble del modelo ============

    /**
     * Sustituye a DeepSeek. Devuelve una respuesta creíble por agente y sabe fallar a
     * propósito, que es lo que permite comprobar la regla del reintento.
     */
    @TestConfiguration
    static class ConfiguracionDePrueba {
        @Bean
        @Primary
        ClienteModelo clienteModeloDePrueba() {
            return new ModeloDePrueba();
        }
    }

    static class ModeloDePrueba implements ClienteModelo {

        static volatile boolean falla = false;
        static final List<String> enviosRecibidos = new CopyOnWriteArrayList<>();

        /**
         * Si el modelo razonó o no en cada llamada, como «EVIDENCIA_CV:false».
         *
         * <p>Es lo único que distingue de verdad a las dos pasadas. Desde fuera las dos
         * guardan notas y las dos mueven la postulación, así que sin mirar esta bandera no
         * hay forma de afirmar que la rápida es rápida: quedaría probado el recorrido y sin
         * probar la razón de que exista.
         */
        static final List<String> razonoPorAgente = new CopyOnWriteArrayList<>();

        private static final Pattern RESPUESTA_ID = Pattern.compile("\"respuestaId\"\\s*:\\s*(\\d+)");

        @Override
        public RespuestaModelo preguntar(String agenteCodigo, String instruccion, String contenido) {
            // Quien no dice nada, razona: es lo que hacía el sistema antes de las dos pasadas
            return preguntar(agenteCodigo, instruccion, contenido, true);
        }

        @Override
        public RespuestaModelo preguntar(String agenteCodigo, String instruccion, String contenido,
                                         boolean razona) {
            enviosRecibidos.add(instruccion + "\n" + contenido);
            razonoPorAgente.add(agenteCodigo + ":" + razona);
            if (falla) {
                throw new IllegalStateException("el proveedor del modelo no responde");
            }
            return new RespuestaModelo(respuestaDe(agenteCodigo, contenido),
                    "deepseek-v4-flash", "deepseek", "prueba", 1200, 340);
        }

        private String respuestaDe(String agenteCodigo, String contenido) {
            return switch (agenteCodigo) {
                case "DATOS_CV" -> datosCv();
                case "EVIDENCIA_CV" -> evidenciaCv();
                case "EVALUADOR" -> evaluador(contenido);
                case "POTENCIAL_RIESGO" -> potencialRiesgo();
                default -> throw new IllegalStateException("agente inesperado: " + agenteCodigo);
            };
        }

        /**
         * La ficha del candidato. Devuelve ocho habilidades a propósito, cuando el contrato
         * dice cinco como mucho: recortarlas es del sistema, no del modelo, y un modelo de
         * verdad se pasa de la raya en cuanto el currículum trae una lista larga.
         */
        private String datosCv() {
            return """
                    {"nombre":"Camila Rojas","email":"camila@correo.pe","telefono":"999888777",
                     "perfilResumen":"Analista de procesos que automatiza y mide lo que hace.",
                     "habilidades":["SQL","Automatizacion","Documentacion","Excel","Procesos",
                                    "Python","Power BI","Scrum"],
                     "experienciaMesesTotal":72,"ultimoPuesto":"Analista de procesos",
                     "ultimaEmpresa":"Consultora Andina","ultimaMesesDuracion":30,
                     "educacionMaxima":"Universitaria completa"}""";
        }

        private String evidenciaCv() {
            List<String> criterios = List.of("CV_RESULTADOS", "CV_COMPLEJIDAD", "CV_SISTEMAS",
                    "CV_PERSONAS", "CV_APRENDIZAJE", "CV_INICIATIVA", "CV_HABILIDADES",
                    "CV_EVIDENCIA");
            List<String> filas = new ArrayList<>();
            for (String codigo : criterios) {
                filas.add(("""
                        {"codigo":"%s","puntaje":75,"explicacion":"Hay un resultado medido y \
                        atribuible.","evidencia":"pasó de 3 días a 4 horas"}""").formatted(codigo));
            }
            // Una novena nota, de un criterio que no existe: tiene que descartarse sola.
            filas.add("""
                    {"codigo":"CV_INVENTADO","puntaje":99,"explicacion":"no existe","evidencia":null}""");
            // Y una décima sin explicación: RF-150 dice que no se guarda.
            filas.add("""
                    {"codigo":"CV_RESULTADOS","puntaje":10,"explicacion":"","evidencia":null}""");
            return """
                    {"criterios":[%s],
                     "afirmaciones":[
                       {"texto":"Automatizó el cierre mensual","clasificacion":"DEMOSTRADA",
                        "preguntaValidacion":null},
                       {"texto":"Lideró un equipo","clasificacion":"DECLARADA",
                        "preguntaValidacion":"¿A cuántas personas y durante cuánto tiempo?"},
                       {"texto":"Algo raro","clasificacion":"NO_EXISTE","preguntaValidacion":null}],
                     "confianza":72}""".formatted(String.join(",", filas));
        }

        private String evaluador(String contenido) {
            List<String> notas = new ArrayList<>();
            Matcher ids = RESPUESTA_ID.matcher(contenido);
            while (ids.find()) {
                notas.add(("""
                        {"respuestaId":%s,"puntaje":3,"explicacion":"Actuó por iniciativa y midió \
                        el resultado.","evidenciaCitada":"reduje el tiempo de cierre","confianza":68}""")
                        .formatted(ids.group(1)));
            }
            // Una nota para una respuesta de otra persona: no puede colarse.
            notas.add("""
                    {"respuestaId":999999,"puntaje":4,"explicacion":"ajena","evidenciaCitada":"x",
                     "confianza":90}""");
            return "{\"notas\":[%s]}".formatted(String.join(",", notas));
        }

        private String potencialRiesgo() {
            return """
                    {"adecuacion":78,"potencial":81,"altoRendimiento":70,"confianzaEvidencia":66,
                     "resumen":"Perfil sólido en ejecución, con resultados medidos y poca evidencia \
                     sobre trabajo con personas.",
                     "hallazgos":[
                       {"tipo":"FORTALEZA","descripcion":"Mide lo que hace",
                        "evidencia":"pasó de 3 días a 4 horas","esCanalizable":false,"sugerencia":null},
                       {"tipo":"FALTA_EVIDENCIA","descripcion":"No hay ejemplos de haber formado a nadie",
                        "evidencia":null,"esCanalizable":false,
                        "sugerencia":"Preguntárselo en la conversación final"},
                       {"tipo":"INVENTADO","descripcion":"tipo que no existe","evidencia":null,
                        "esCanalizable":false,"sugerencia":null}],
                     "alertas":[
                       {"tipo":"DEMASIADO_IDEAL",
                        "descripcion":"Ninguna respuesta reconoce un límite propio"}]}""";
        }
    }

    // ============ Las pruebas ============

    @DisplayName("La IA califica y la postulación avanza sola")
    @Test
    @Order(1)
    void laIaCalificaYLaPostulacionAvanzaSola() throws Exception {
        ModeloDePrueba.falla = false;
        ModeloDePrueba.enviosRecibidos.clear();

        tokenEquipo = entrarComoEquipo();
        vacanteId = prepararVacantePublicada();
        codigoBueno = postularConCurriculumReal("camila@correo.pe");
        responderYEntregar(codigoBueno);

        // La cola arrancó sola al entregar: nadie tuvo que pulsar nada
        // Cuatro y no tres: la fila normal empieza sacando la ficha de datos del candidato.
        // Antes solo la sacaba la pasada rápida, y quien se calificaba por aquí salía sin
        // teléfono, sin experiencia y sin último puesto, sin manera de rellenarlo después.
        esperarA(() -> contar("select count(*) from trabajo_ia where estado = 'TERMINADO'") == 4,
                "los cuatro agentes terminen");

        long postulacionId = idDe(codigoBueno);

        // 1 · La postulación avanzó a donde decide una persona, y tiene grupo de prioridad
        Map<String, Object> postulacion = jdbc.queryForMap(
                "select estado_codigo, grupo_prioridad from postulacion where id = ?", postulacionId);
        assertThat(postulacion.get("estado_codigo")).isEqualTo("PERFIL_POR_CONFIRMAR");
        assertThat(postulacion.get("grupo_prioridad"))
                .isIn("ALTA", "POTENCIAL_CON_RIESGO", "NO_PRIORIZADO");

        // 2 · La IA leyó un currículum SIN edad, sexo ni estado civil (RF-41). Se comprueba
        // sobre el envío literal que quedó en la bitácora, que es lo que de verdad salió.
        String envio = jdbc.queryForObject(
                "select envio from ejecucion_ia where agente_codigo = 'EVIDENCIA_CV'", String.class);
        assertThat(envio)
                .doesNotContain("34 años")
                .doesNotContain("Femenino")
                .doesNotContain("Casada")
                .doesNotContain("1992");
        // Pero sí llevaba lo que hay que puntuar: si no, la nota no valdría nada
        assertThat(envio).contains("cierre mensual");

        // Y las dos versiones quedaron guardadas, que es lo que permite demostrarlo después
        Map<String, Object> cv = jdbc.queryForMap(
                "select texto_extraido, texto_anonimizado from cv where postulacion_id = ?", postulacionId);
        assertThat((String) cv.get("texto_extraido")).contains("34");
        assertThat((String) cv.get("texto_anonimizado")).doesNotContain("34 años");

        // 3 · Toda llamada quedó en la bitácora, con su instrucción y su versión (RF-146)
        List<Map<String, Object>> ejecuciones = jdbc.queryForList("""
                select agente_codigo, version_agente, instruccion_ia_id, es_exitosa,
                       tokens_entrada, duracion_ms
                from ejecucion_ia order by id""");
        assertThat(ejecuciones).hasSize(4);
        assertThat(ejecuciones).allSatisfy(fila -> {
            assertThat(fila.get("instruccion_ia_id")).isNotNull();
            assertThat(fila.get("version_agente")).isNotNull();
            assertThat(fila.get("es_exitosa")).isEqualTo(true);
            assertThat(fila.get("tokens_entrada")).isNotNull();
        });
        assertThat(ejecuciones.stream().map(f -> f.get("agente_codigo")).toList())
                .containsExactly("DATOS_CV", "EVIDENCIA_CV", "EVALUADOR", "POTENCIAL_RIESGO");

        // 4 · Los ocho criterios del currículum, ni uno más. El noveno que devolvió el modelo
        // no existe y el décimo venía sin explicación: los dos se descartan (RF-150).
        assertThat(contar("select count(*) from nota_criterio where postulacion_id = " + postulacionId))
                .isEqualTo(8);
        assertThat(contar("""
                select count(*) from nota_criterio
                where postulacion_id = %d and origen = 'AGENTE'
                  and explicacion is not null and btrim(explicacion) <> ''
                  and ejecucion_ia_id is not null""".formatted(postulacionId))).isEqualTo(8);

        // Las afirmaciones clasificadas: la tercera traía una clasificación inventada
        assertThat(contar("select count(*) from afirmacion_cv")).isEqualTo(2);

        // 5 · Las respuestas abiertas calificadas de 0 a 4, y ninguna ajena
        int abiertas = contar("""
                select count(*) from respuesta r
                join pregunta p on p.id = r.pregunta_id
                join evaluacion e on e.id = r.evaluacion_id
                join postulacion po on po.evaluacion_id = e.id
                where po.id = %d and r.opcion_id is null and p.es_puntuable""".formatted(postulacionId));
        assertThat(abiertas)
                .withFailMessage("La plantilla de Ejecución tiene que traer preguntas abiertas; "
                        + "sin ellas esta prueba no comprueba nada del EVALUADOR")
                .isGreaterThan(0);
        assertThat(contar("select count(*) from nota_respuesta")).isEqualTo(abiertas);
        assertThat(contar("select count(*) from nota_respuesta where puntaje between 0 and 4"))
                .isEqualTo(abiertas);

        // 6 · El Perfil de Talento, que no es una nota sino un retrato (RF-65)
        Map<String, Object> perfil = jdbc.queryForMap("""
                select adecuacion, potencial, confianza_evidencia, resumen, version_pesos_id,
                       ejecucion_ia_id
                from perfil_talento where postulacion_id = ?""", postulacionId);
        assertThat(perfil.get("confianza_evidencia")).isNotNull();
        assertThat(perfil.get("version_pesos_id")).isNotNull();
        assertThat((String) perfil.get("resumen")).isNotBlank();

        // Los hallazgos no se mezclan, y el tipo inventado se descartó
        List<String> tipos = jdbc.queryForList("select tipo from hallazgo_perfil", String.class);
        assertThat(tipos).containsExactlyInAnyOrder("FORTALEZA", "FALTA_EVIDENCIA");

        // La alerta de «demasiado ideal» quedó, y no descarta a nadie: la postulación sigue viva
        assertThat(contar("select count(*) from alerta where tipo = 'DEMASIADO_IDEAL'")).isEqualTo(1);

        // 7 · La nota de la etapa se rehízo con currículum + evaluación, atada a la versión de
        // pesos de la vacante (no a la última publicada)
        Map<String, Object> nota = jdbc.queryForMap("""
                select puntaje, version_pesos_id from nota_etapa
                where postulacion_id = ? and etapa_codigo = 'PERFIL_INTEGRAL'""", postulacionId);
        assertThat(((Number) nota.get("puntaje")).doubleValue()).isBetween(0.01, 100.0);
        Long versionDeLaVacante = jdbc.queryForObject(
                "select version_pesos_id from vacante where id = ?", Long.class, vacanteId);
        assertThat(nota.get("version_pesos_id")).isEqualTo(versionDeLaVacante);
    }

    @DisplayName("Si la IA falla se reintenta, y no se inventa ninguna nota")
    @Test
    @Order(2)
    void siLaIaFallaSeReintentaYNoSeInventaNingunaNota() throws Exception {
        ModeloDePrueba.falla = true;

        codigoFallido = postularConCurriculumReal("luis@correo.pe");
        responderYEntregar(codigoFallido);
        long postulacionId = idDe(codigoFallido);

        esperarA(() -> contar("""
                select count(*) from trabajo_ia
                where postulacion_id = %d and estado = 'FALLIDO'""".formatted(postulacionId)) == 1,
                "el trabajo se dé por fallido tras agotar los intentos");

        // Se intentó dos veces, que es el tope configurado
        assertThat(contar("select intentos from trabajo_ia where postulacion_id = " + postulacionId))
                .isEqualTo(2);

        // Los dos intentos quedaron en la bitácora, con su error. Un fallo sin rastro es lo
        // que impide después saber si la IA está rota o si el candidato no dio para más.
        List<Map<String, Object>> fallos = jdbc.queryForList("""
                select es_exitosa, error from ejecucion_ia
                where trabajo_ia_id in (select id from trabajo_ia where postulacion_id = %d)"""
                .formatted(postulacionId));
        assertThat(fallos).hasSize(2);
        assertThat(fallos).allSatisfy(fila -> {
            assertThat(fila.get("es_exitosa")).isEqualTo(false);
            assertThat((String) fila.get("error")).contains("no responde");
        });

        // Y lo que importa: NO se inventó ninguna nota, y la postulación sigue esperando
        assertThat(contar("select count(*) from nota_criterio where postulacion_id = " + postulacionId))
                .isZero();
        assertThat(contar("select count(*) from perfil_talento where postulacion_id = " + postulacionId))
                .isZero();
        assertThat(jdbc.queryForObject(
                "select estado_codigo from postulacion where id = ?", String.class, postulacionId))
                .isEqualTo("PERFIL_CALIFICANDO");
        assertThat(jdbc.queryForObject(
                "select grupo_prioridad from postulacion where id = ?", String.class, postulacionId))
                .isNull();

        // El segundo y el tercer agente ni se crearon: la fila se corta donde falla
        assertThat(contar("select count(*) from trabajo_ia where postulacion_id = " + postulacionId))
                .isEqualTo(1);

        ModeloDePrueba.falla = false;
    }

    /**
     * La primera pasada sobre la tanda entera, y la lista ordenada que sale de ella.
     *
     * <p>Es el recorrido que antes no existía: hasta ahora la IA solo entraba cuando el
     * candidato ya había entregado su evaluación, así que una tanda recién llegada —cien
     * currículums y nadie que haya respondido nada— no se podía mirar sin abrir los PDF uno
     * por uno.
     *
     * <p>Se comprueban las cuatro cosas que la sostienen:
     * <ul>
     *   <li>El modelo <b>no razona</b> en esta pasada. Es su única razón de ser.
     *   <li>El evaluador no se llama: no hay respuestas que puntuar y la llamada se ahorra.
     *   <li>Quien ya tiene retrato no se vuelve a calificar, aunque se pulse el botón.
     *   <li>La ficha de datos sale del currículum recortado, así que no trae edad ni sexo.
     * </ul>
     *
     * <p><b>Y de paso comprueba que el aviso a la cola sale después del commit.</b> Aquí se
     * encolan tres candidatos dentro de una sola transacción, y del otro lado hay ocho
     * consumidores. Si el mensaje saliera antes de guardar la fila, uno lo agarraría en
     * milisegundos, no encontraría el trabajo y lo soltaría: los tres se quedarían en
     * PENDIENTE para siempre y la espera de aquí abajo se agotaría sin que nada diera error.
     */
    @DisplayName("La criba rápida lee la tanda sin razonar y salta al evaluador")
    @Test
    @Order(3)
    void laCribaRapidaLeeLaTandaSinRazonarYSaltaAlEvaluador() throws Exception {
        ModeloDePrueba.falla = false;
        ModeloDePrueba.razonoPorAgente.clear();

        // Dos recién llegados: subieron su currículum y nada más. Nadie ha respondido nada,
        // que es justo la situación en la que se pide una criba.
        String reciénLlegadoA = postularConCurriculumReal("ana@correo.pe");
        String reciénLlegadoB = postularConCurriculumReal("beto@correo.pe");

        String respuesta = conToken(post("/api/v1/panel/vacantes/" + vacanteId + "/criba-rapida"),
                tokenEquipo, null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("ENCOLADA"))
                .andReturn().getResponse().getContentAsString();

        // Tres y no cuatro. Camila ya tiene su retrato de la pasada fina y se salta sola;
        // Luis no lo tiene —su intento falló y no se le inventó ninguna nota— así que
        // vuelve a entrar. Eso es la regla de «no calificar dos veces», por los dos lados.
        assertThat(json.readTree(respuesta).get("candidatos").asInt()).isEqualTo(3);

        long idA = idDe(reciénLlegadoA);
        long idB = idDe(reciénLlegadoB);

        esperarA(() -> contar("""
                select count(*) from trabajo_ia
                where modo = 'RAPIDA' and estado = 'TERMINADO'""") == 9,
                "los tres agentes de la pasada rápida terminen con los tres candidatos");

        // 1 · Ni una sola llamada razonó. Si esto se rompe, la pasada rápida tarda lo mismo
        // que la cuidadosa y las dos dejan de tener sentido por separado.
        assertThat(ModeloDePrueba.razonoPorAgente)
                .isNotEmpty()
                .allSatisfy(llamada -> assertThat(llamada).endsWith(":false"));

        // 2 · El evaluador no se llamó: sin respuestas entregadas no tenía nada que puntuar
        assertThat(ModeloDePrueba.razonoPorAgente)
                .noneMatch(llamada -> llamada.startsWith("EVALUADOR"));
        assertThat(contar("""
                select count(*) from trabajo_ia
                where modo = 'RAPIDA' and agente_codigo = 'EVALUADOR'""")).isZero();

        // Y sí corrieron los tres que sí tocaban, en su orden
        assertThat(jdbc.queryForList("""
                select distinct agente_codigo from trabajo_ia where modo = 'RAPIDA'
                order by agente_codigo""", String.class))
                .containsExactly("DATOS_CV", "EVIDENCIA_CV", "POTENCIAL_RIESGO");

        // 3 · La ficha de datos, que es lo que hace legible la tabla sin abrir un PDF
        Map<String, Object> ficha = jdbc.queryForMap(
                "select * from dato_cv where postulacion_id = ?", idA);
        assertThat(ficha.get("nombre")).isEqualTo("Camila Rojas");
        assertThat(ficha.get("ultimo_puesto")).isEqualTo("Analista de procesos");
        assertThat(ficha.get("experiencia_meses_total")).isEqualTo(72);
        assertThat(ficha.get("ejecucion_ia_id")).isNotNull();

        // Cinco habilidades como mucho: el modelo devolvió ocho y el sistema recorta
        assertThat((String) ficha.get("habilidades")).isNotNull();
        assertThat(((String) ficha.get("habilidades")).split("\\|")).hasSize(5);

        // 4 · La ficha salió del currículum recortado: la edad, el sexo y el estado civil
        // no llegaron al agente y por eso no hay dónde guardarlos (RF-41)
        String envioDatos = jdbc.queryForObject("""
                select envio from ejecucion_ia
                where agente_codigo = 'DATOS_CV' order by id limit 1""", String.class);
        assertThat(envioDatos)
                .doesNotContain("34 años")
                .doesNotContain("Femenino")
                .doesNotContain("Casada");

        // 5 · Los dos quedaron esperando a una persona, con su grupo de prioridad puesto
        for (long id : List.of(idA, idB)) {
            Map<String, Object> postulacion = jdbc.queryForMap(
                    "select estado_codigo, grupo_prioridad from postulacion where id = ?", id);
            assertThat(postulacion.get("estado_codigo")).isEqualTo("PERFIL_POR_CONFIRMAR");
            assertThat(postulacion.get("grupo_prioridad")).isNotNull();
        }

        // 6 · Pedirla otra vez no cuesta nada: ya no queda nadie sin calificar
        String segunda = conToken(post("/api/v1/panel/vacantes/" + vacanteId + "/criba-rapida"),
                tokenEquipo, null)
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        assertThat(json.readTree(segunda).get("candidatos").asInt()).isZero();
    }

    /**
     * La lista ordenada de la convocatoria.
     *
     * <p>Es la pantalla que contesta «¿a quién llamo primero?». Lo que se comprueba aquí es
     * el orden —manda el grupo de prioridad y no la nota— y que cada fila traiga lo que
     * hace falta para decidir sin abrir la ficha: quién es, de qué pasada viene su nota,
     * cómo se llama su archivo y cuánto pesa cada criterio.
     */
    @DisplayName("El ranking ordena por grupo y deja al final a quien no tiene nota")
    @Test
    @Order(4)
    void elRankingOrdenaPorGrupoYDejaAlSinCalificarAlFinal() throws Exception {
        // Uno más que solo subió su currículum y al que nadie ha calificado todavía. Es el
        // caso que importa: un candidato sin nota no puede desaparecer de la lista, ni
        // colarse arriba porque su casilla esté vacía.
        String sinCalificar = postularConCurriculumReal("dana@correo.pe");

        String cuerpo = conToken(get("/api/v1/panel/vacantes/" + vacanteId + "/ranking"),
                tokenEquipo, null)
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        JsonNode ranking = json.readTree(cuerpo);
        JsonNode filas = ranking.get("filas");

        // Están todos los de la convocatoria, calificados o no
        assertThat(ranking.get("total").asInt()).isEqualTo(5);
        assertThat(filas.size()).isEqualTo(5);

        // La numeración se pone después de ordenar: es la posición en la tanda
        List<Integer> puestos = new ArrayList<>();
        List<String> grupos = new ArrayList<>();
        for (JsonNode fila : filas) {
            puestos.add(fila.get("puesto").asInt());
            grupos.add(fila.get("grupoPrioridad").isNull() ? null
                    : fila.get("grupoPrioridad").asText());
        }
        assertThat(puestos).containsExactly(1, 2, 3, 4, 5);

        // El recién llegado cierra la lista: si el nulo se ordenara como un cero alto, o
        // como el primero de la lista de grupos, encabezaría la tanda sin haber sido leído.
        JsonNode ultima = filas.get(filas.size() - 1);
        assertThat(ultima.get("grupoPrioridad").isNull()).isTrue();
        assertThat(ultima.get("notaEtapa").isNull()).isTrue();
        assertThat(ultima.get("uuid").asText()).isEqualTo(sinCalificar);
        // Y aun así se le ve el nombre del archivo: es lo que permite ir a por su currículum
        assertThat(ultima.get("archivoNombre").asText()).isEqualTo("cv.pdf");
        assertThat(ranking.get("calificados").asInt()).isEqualTo(4);

        // El orden lo manda el grupo, no el número: alguien con 92 y un riesgo crítico no
        // va por delante de alguien con 88 y ninguno.
        List<String> ordenGrupos = List.of("ALTA", "POTENCIAL_CON_RIESGO", "NO_PRIORIZADO");
        int anterior = -1;
        for (String grupo : grupos) {
            int donde = grupo == null ? ordenGrupos.size() : ordenGrupos.indexOf(grupo);
            assertThat(donde).isGreaterThanOrEqualTo(anterior);
            anterior = donde;
        }

        // Y dentro del mismo grupo, la nota baja
        BigDecimal notaAnterior = null;
        String grupoAnterior = null;
        for (JsonNode fila : filas) {
            if (fila.get("notaEtapa").isNull()) continue;
            String grupo = fila.get("grupoPrioridad").asText();
            BigDecimal nota = fila.get("notaEtapa").decimalValue();
            if (grupo.equals(grupoAnterior)) {
                assertThat(nota).isLessThanOrEqualTo(notaAnterior);
            }
            grupoAnterior = grupo;
            notaAnterior = nota;
        }

        // Cada fila calificada trae lo que la pantalla necesita para explicarse sola
        JsonNode calificada = null;
        for (JsonNode fila : filas) {
            if (!fila.get("notaEtapa").isNull()) {
                calificada = fila;
                break;
            }
        }
        assertThat(calificada).isNotNull();
        // De qué pasada viene su nota: una de la rápida es provisional y hay que saberlo
        assertThat(calificada.get("pasada").asText()).isIn("RAPIDA", "FINA");
        // Con qué archivo se le puede encontrar en la carpeta del equipo
        assertThat(calificada.get("archivoNombre").asText()).isEqualTo("cv.pdf");
        // Y quién es, sin abrir el PDF
        assertThat(calificada.get("datos").get("nombre").asText()).isEqualTo("Camila Rojas");

        // Los ocho criterios del currículum, cada uno con su peso: un 95 en un criterio que
        // pesa 20 y un 95 en uno que pesa 5 se leen igual en pantalla y no valen lo mismo.
        JsonNode notas = calificada.get("notasCriterio");
        assertThat(notas.size()).isEqualTo(8);
        for (JsonNode nota : notas) {
            assertThat(nota.get("peso").isNull())
                    .withFailMessage("Sin el peso, la pantalla no puede explicar la cuenta: "
                            + "un 95 en un criterio que pesa 20 y otro en uno que pesa 5 se "
                            + "leen igual y no valen lo mismo")
                    .isFalse();
            assertThat(nota.get("criterio").asText()).isNotBlank();
        }

        // Y los pesos suman lo que tiene que sumar el currículum en este nivel de puesto:
        // si un día se cambian desde el panel, la cuenta de la pantalla sigue cuadrando.
        BigDecimal sumaPesos = BigDecimal.ZERO;
        for (JsonNode nota : notas) {
            sumaPesos = sumaPesos.add(nota.get("peso").decimalValue());
        }
        assertThat(sumaPesos).isPositive();
    }

    /**
     * La segunda pasada, solo sobre la parte alta de la tanda.
     *
     * <p>Aquí el modelo sí razona, y sus notas pisan las provisionales. Lo que importa es
     * que no se gaste en la tanda entera: si volviera a mirar a todos, la primera pasada no
     * habría ahorrado nada.
     */
    @DisplayName("La criba fina vuelve solo sobre los de arriba, y esa sí razona")
    @Test
    @Order(5)
    void laCribaFinaVuelveSoloSobreLosDeArribaYEsaSiRazona() throws Exception {
        ModeloDePrueba.razonoPorAgente.clear();

        // Todos los candidatos reciben la misma respuesta del doble, así que sus notas
        // empatan y el orden entre ellos no se puede predecir desde aquí. Lo que sí se
        // puede es leer la lista y calcular el corte con la misma cuenta que hace el
        // sistema: se busca al primero que todavía viene de la pasada rápida y se pone el
        // porcentaje justo para que el corte llegue hasta él y no más allá.
        JsonNode antes = json.readTree(conToken(
                get("/api/v1/panel/vacantes/" + vacanteId + "/ranking"), tokenEquipo, null)
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString());

        List<String> pasadas = new ArrayList<>();
        for (JsonNode fila : antes.get("filas")) {
            if (!fila.get("notaEtapa").isNull()) {
                pasadas.add(fila.get("pasada").asText());
            }
        }
        int conNota = pasadas.size();
        int hastaDonde = pasadas.indexOf("RAPIDA") + 1;
        assertThat(hastaDonde)
                .withFailMessage("Para esta prueba tiene que quedar alguien con nota "
                        + "provisional de la pasada rápida")
                .isPositive();
        int porcentaje = (int) Math.ceil(hastaDonde * 100.0 / conNota);

        // Un corte de verdad: mira a los de arriba y deja fuera al resto. Si esto dejara de
        // ser cierto, la primera pasada no habría ahorrado nada.
        assertThat(hastaDonde).isLessThan(conNota);

        jdbc.update("update parametro set valor = ? where codigo = 'porcentaje_criba_fina'",
                String.valueOf(porcentaje));

        int conNotaAntes = contar(
                "select count(*) from nota_etapa where etapa_codigo = 'PERFIL_INTEGRAL'");
        int finasAntes = contar("""
                select count(*) from trabajo_ia
                where modo = 'FINA' and agente_codigo = 'POTENCIAL_RIESGO'
                  and estado = 'TERMINADO'""");

        String respuesta = conToken(post("/api/v1/panel/vacantes/" + vacanteId + "/criba-fina"),
                tokenEquipo, null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("ENCOLADA"))
                .andReturn().getResponse().getContentAsString();

        // Solo los que entraron en el corte y todavía no habían pasado por la fina: quien
        // ya la tiene no repite, porque la suya es la definitiva.
        long esperados = pasadas.subList(0, hastaDonde).stream()
                .filter(p -> !"FINA".equals(p)).count();
        int encolados = json.readTree(respuesta).get("candidatos").asInt();
        assertThat(encolados)
                .withFailMessage("La segunda pasada tiene que mirar a los de arriba y solo a "
                        + "ellos, saltándose a quien ya pasó por ella")
                .isEqualTo((int) esperados);

        esperarA(() -> contar("""
                select count(*) from trabajo_ia
                where modo = 'FINA' and agente_codigo = 'POTENCIAL_RIESGO'
                  and estado = 'TERMINADO'""") == finasAntes + encolados,
                "la segunda pasada termine con los de arriba");

        // 1 · Esta sí razona: es lo que la hace lenta y lo que la hace fiable
        assertThat(ModeloDePrueba.razonoPorAgente)
                .isNotEmpty()
                .allSatisfy(llamada -> assertThat(llamada).endsWith(":true"));

        // 2 · Y a nadie se le sacó la ficha de datos dos veces. Ese paso está en las dos
        // filas, pero se salta solo cuando la ficha ya existe: son datos copiados del
        // currículum, no notas, y no cambian salvo que cambie el archivo.
        assertThat(contar("""
                select count(*) from (
                    select postulacion_id from trabajo_ia
                    where agente_codigo = 'DATOS_CV' and estado = 'TERMINADO'
                    group by postulacion_id having count(*) > 1) repetidos"""))
                .withFailMessage("Alguien pagó su ficha de datos dos veces: la segunda pasada "
                        + "tiene que saltarse ese paso cuando la ficha ya está sacada")
                .isZero();

        // 3 · Nadie perdió su nota por volver a pasar: se pisan, no se borran
        assertThat(contar("select count(*) from nota_etapa where etapa_codigo = 'PERFIL_INTEGRAL'"))
                .isEqualTo(conNotaAntes);

        // 4 · Y su nota deja de ser provisional: el ranking ya los cuenta como pasada fina
        JsonNode despues = json.readTree(conToken(
                get("/api/v1/panel/vacantes/" + vacanteId + "/ranking"), tokenEquipo, null)
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString());
        assertThat(despues.get("conPasadaFina").asInt())
                .isEqualTo(antes.get("conPasadaFina").asInt() + encolados);

        jdbc.update("update parametro set valor = '50' where codigo = 'porcentaje_criba_fina'");
    }

    /**
     * Calificar a uno solo con su currículum, sin que haya entregado nada.
     *
     * <p>Antes era imposible: el sistema exigía una evaluación entregada y respondía que no
     * había nada que calificar. Es el botón de la ficha, el que se usa cuando alguien quiere
     * volver a mirar a un candidato concreto de la tanda.
     */
    @DisplayName("Se criba un currículum suelto aunque nadie haya respondido nada")
    @Test
    @Order(6)
    void seCribaUnCurriculumSueltoAunqueNadieHayaRespondidoNada() throws Exception {
        String codigo = postularConCurriculumReal("carla@correo.pe");
        long postulacionId = idDe(codigo);

        // No respondió absolutamente nada. La evaluación existe desde que se postuló —se
        // crea vacía al llegar— así que lo que dice si hay algo que puntuar no es esa fila,
        // son las respuestas. Sin ellas, el evaluador no tiene trabajo.
        assertThat(contar("""
                select count(*) from respuesta
                where evaluacion_id = (select evaluacion_id from postulacion where id = %d)"""
                .formatted(postulacionId))).isZero();

        conToken(post("/api/v1/panel/postulaciones/" + postulacionId + "/criba-cv"),
                tokenEquipo, null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("ENCOLADA"));

        esperarA(() -> "PERFIL_POR_CONFIRMAR".equals(jdbc.queryForObject(
                "select estado_codigo from postulacion where id = ?", String.class, postulacionId)),
                "la criba de un currículum suelto termine");

        // Se armó el retrato con lo único que había: el currículum
        assertThat(contar("select count(*) from perfil_talento where postulacion_id = "
                + postulacionId)).isEqualTo(1);
        assertThat(contar("select count(*) from nota_criterio where postulacion_id = "
                + postulacionId)).isEqualTo(8);

        // El evaluador se saltó solo, sin gastar una llamada al modelo para no puntuar nada
        assertThat(contar("""
                select count(*) from trabajo_ia
                where postulacion_id = %d and agente_codigo = 'EVALUADOR'"""
                .formatted(postulacionId))).isZero();

        // Y quedó con nota y con grupo, listo para que alguien decida
        assertThat(jdbc.queryForObject(
                "select grupo_prioridad from postulacion where id = ?", String.class, postulacionId))
                .isNotNull();
    }

    /**
     * Pedir la segunda pasada antes de que exista una primera.
     *
     * <p>Sin ninguna nota, «los de arriba» no existen y la lista sale por orden alfabético:
     * la pasada cuidadosa se gastaría en quien tocó por la letra de su apellido. Es un error
     * fácil de cometer —basta pulsar el botón mientras la tanda se está cargando— y caro de
     * descubrir, porque no falla: califica, y califica a quien no era.
     */
    @DisplayName("La segunda pasada se niega si todavía no hay ninguna nota")
    @Test
    @Order(7)
    void laSegundaPasadaSeNiegaSiTodaviaNoHayNingunaNota() throws Exception {
        long vacanteVacia = crearVacanteSinCandidatos();
        int trabajosAntes = contar("select count(*) from trabajo_ia");

        conToken(post("/api/v1/panel/vacantes/" + vacanteVacia + "/criba-fina"), tokenEquipo, null)
                .andExpect(status().isConflict());

        // Y no encoló nada: negarse tiene que ser gratis
        assertThat(contar("select count(*) from trabajo_ia")).isEqualTo(trabajosAntes);
    }

    /**
     * Una convocatoria más, sin candidatos y sin publicar.
     *
     * <p>No reutiliza {@code prepararVacantePublicada}: aquella crea el puesto DEV_WEB y las
     * once preguntas de la prueba, y los códigos son únicos por organización, así que
     * llamarla dos veces revienta. Aquí basta con una vacante que exista.
     */
    private long crearVacanteSinCandidatos() throws Exception {
        Long areaId = jdbc.queryForObject("select id from area limit 1", Long.class);
        Long puestoId = jdbc.queryForObject("select id from puesto limit 1", Long.class);

        long solicitudId = Long.parseLong(leer(conToken(post("/api/v1/panel/solicitudes"), tokenEquipo, """
                {"areaId": %d, "urgencia": "NORMAL",
                 "nivelPuestoCodigo": "EJECUCION", "familiaCodigo": "TECNOLOGIA",
                 "resultadoPrincipal": "Sostener el portal",
                 "motivo": "No se llega a los plazos",
                 "consecuenciaNoContratar": "Se retrasa el MVP",
                 "analisisCapacidad": "Se evaluó automatizar y no alcanza",
                 "responsableUsuarioId": 1,
                 "resultadosEsperados": [
                   {"descripcion": "Publicar el portal", "indicador": "en producción"},
                   {"descripcion": "Reducir bugs", "indicador": "la mitad"},
                   {"descripcion": "Documentar", "indicador": "docs al día"}
                 ]}""".formatted(areaId))
                .andReturn().getResponse().getContentAsString(), "id"));

        conToken(post("/api/v1/panel/solicitudes/" + solicitudId + "/aprobacion"), tokenEquipo,
                "{\"motivo\":\"Hay presupuesto\"}").andExpect(status().isOk());

        return Long.parseLong(leer(conToken(post("/api/v1/panel/vacantes"), tokenEquipo, """
                {"solicitudTalentoId": %d, "puestoId": %d,
                 "titulo": "Otra convocatoria", "descripcion": "Sin candidatos todavía",
                 "tipoCierre": "PERMANENTE", "responsableUsuarioId": 1}"""
                .formatted(solicitudId, puestoId))
                .andReturn().getResponse().getContentAsString(), "id"));
    }

    // ============ Apoyo ============

    /**
     * Un PDF de verdad, con los datos que la IA no puede ver.
     *
     * <p>Tiene que ser un PDF real y no unos bytes cualesquiera: lo que se está probando es
     * justamente que del archivo se saque texto y que ese texto salga recortado.
     */
    private byte[] curriculumEnPdf() throws Exception {
        try (PDDocument documento = new PDDocument(); ByteArrayOutputStream salida = new ByteArrayOutputStream()) {
            PDPage pagina = new PDPage();
            documento.addPage(pagina);
            List<String> lineas = List.of(
                    "Camila Rojas - Analista de procesos",
                    "Edad: 34 años",
                    "Sexo: Femenino",
                    "Estado civil: Casada",
                    "Fecha de nacimiento: 12/03/1992",
                    "",
                    "EXPERIENCIA",
                    "Automatice el cierre mensual y paso de 3 dias a 4 horas.",
                    "Documente el proceso y lo dejo funcionando sin mi.",
                    "Aprendi SQL en dos meses para poder medir el resultado.");
            try (PDPageContentStream lienzo = new PDPageContentStream(documento, pagina)) {
                lienzo.beginText();
                lienzo.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 11);
                lienzo.setLeading(16f);
                lienzo.newLineAtOffset(50, 720);
                for (String linea : lineas) {
                    lienzo.showText(linea);
                    lienzo.newLine();
                }
                lienzo.endText();
            }
            documento.save(salida);
            return salida.toByteArray();
        }
    }

    private String postularConCurriculumReal(String correo) throws Exception {
        String tokenCandidato = crearCandidatoYEntrar(correo);
        MockMultipartFile cv = new MockMultipartFile("cv", "cv.pdf", "application/pdf",
                curriculumEnPdf());
        String codigo = leer(mvc.perform(multipart("/api/v1/portal/postulaciones")
                        .file(cv)
                        .param("vacanteId", String.valueOf(vacanteId))
                        .param("resultadoOrgulloso",
                                "Automaticé el cierre mensual y pasó de 3 días a 4 horas")
                        .header("Authorization", "Bearer " + tokenCandidato))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString(), "codigo");
        tokensPorCodigo.put(codigo, tokenCandidato);
        return codigo;
    }

    private final Map<String, String> tokensPorCodigo = new java.util.HashMap<>();

    private void responderYEntregar(String codigo) throws Exception {
        String token = tokensPorCodigo.get(codigo);
        String cuerpo = mvc.perform(post("/api/v1/portal/evaluacion/" + codigo + "/inicio")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        JsonNode preguntas = json.readTree(cuerpo).get("preguntas");
        for (JsonNode pregunta : preguntas) {
            JsonNode opciones = pregunta.get("opciones");
            String respuesta = opciones != null && !opciones.isEmpty()
                    ? "{\"opcionId\":%d,\"segundos\":30}".formatted(opciones.get(0).get("id").asLong())
                    : "{\"texto\":\"Un caso concreto: reduje el tiempo de cierre midiendo antes y "
                            + "después, y dejé el proceso documentado\",\"segundos\":90}";
            mvc.perform(put("/api/v1/portal/evaluacion/" + codigo + "/respuestas/"
                            + pregunta.get("id").asLong())
                            .header("Authorization", "Bearer " + token)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(respuesta))
                    .andExpect(status().isOk());
        }
        mvc.perform(post("/api/v1/portal/evaluacion/" + codigo + "/entrega")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());
    }

    /** La cola es asíncrona: hay que esperarla, no adivinar cuánto tarda. */
    private void esperarA(BooleanSupplier condicion, String que) {
        long limite = System.currentTimeMillis() + 60_000;
        while (System.currentTimeMillis() < limite) {
            if (condicion.getAsBoolean()) {
                return;
            }
            try {
                Thread.sleep(250);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
        fail("Se agotó la espera a que " + que + ". Trabajos: "
                + jdbc.queryForList("select id, agente_codigo, estado, intentos from trabajo_ia")
                + " · Ejecuciones: "
                + jdbc.queryForList("select agente_codigo, es_exitosa, error from ejecucion_ia"));
    }

    private int contar(String sql) {
        Integer valor = jdbc.queryForObject(sql, Integer.class);
        return valor == null ? 0 : valor;
    }

    private long idDe(String codigo) {
        return jdbc.queryForObject("select id from postulacion where uuid = ?::uuid", Long.class, codigo);
    }

    private String entrarComoEquipo() throws Exception {
        return leer(mvc.perform(post("/api/v1/panel/auth/dev-login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"usuarioRenaserOsId\":\"dev-1\"}"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(), "token");
    }

    private long prepararVacantePublicada() throws Exception {
        jdbc.update("INSERT INTO area (organizacion_id, nombre, es_activa) VALUES (1, 'Tecnología', true)");
        Long areaId = jdbc.queryForObject("SELECT id FROM area LIMIT 1", Long.class);

        long solicitudId = Long.parseLong(leer(conToken(post("/api/v1/panel/solicitudes"), tokenEquipo, """
                {"areaId": %d, "urgencia": "NORMAL",
                 "nivelPuestoCodigo": "EJECUCION", "familiaCodigo": "TECNOLOGIA",
                 "resultadoPrincipal": "Sostener el portal",
                 "motivo": "No se llega a los plazos",
                 "consecuenciaNoContratar": "Se retrasa el MVP",
                 "analisisCapacidad": "Se evaluó automatizar y no alcanza",
                 "responsableUsuarioId": 1,
                 "resultadosEsperados": [
                   {"descripcion": "Publicar el portal", "indicador": "en producción"},
                   {"descripcion": "Reducir bugs", "indicador": "la mitad"},
                   {"descripcion": "Documentar", "indicador": "docs al día"}
                 ]}""".formatted(areaId))
                .andReturn().getResponse().getContentAsString(), "id"));

        conToken(post("/api/v1/panel/solicitudes/" + solicitudId + "/aprobacion"), tokenEquipo,
                "{\"motivo\":\"Hay presupuesto\"}").andExpect(status().isOk());

        long puestoId = Long.parseLong(leer(conToken(post("/api/v1/panel/puestos"), tokenEquipo, """
                {"codigo": "DEV_WEB", "nombre": "Desarrollador web",
                 "nivelPuestoCodigo": "EJECUCION", "familiaCodigo": "TECNOLOGIA"}""")
                .andReturn().getResponse().getContentAsString(), "id"));

        long id = Long.parseLong(leer(conToken(post("/api/v1/panel/vacantes"), tokenEquipo, """
                {"solicitudTalentoId": %d, "puestoId": %d,
                 "titulo": "Desarrollador web", "descripcion": "Portal de talento",
                 "tipoCierre": "PERMANENTE", "responsableUsuarioId": 1}"""
                .formatted(solicitudId, puestoId))
                .andReturn().getResponse().getContentAsString(), "id"));

        Long plantillaId = jdbc.queryForObject(
                "select id from plantilla_evaluacion where nivel_puesto_codigo = 'EJECUCION'", Long.class);
        conToken(post("/api/v1/panel/vacantes/" + id + "/plantilla-evaluacion"), tokenEquipo,
                "{\"plantillaEvaluacionId\": %d}".formatted(plantillaId)).andExpect(status().isOk());

        Long versionPruebaId = armarUnaPruebaValida(tokenEquipo);
        conToken(post("/api/v1/panel/vacantes/" + id + "/plantilla-prueba"), tokenEquipo,
                "{\"versionPlantillaPruebaId\": %d}".formatted(versionPruebaId)).andExpect(status().isOk());

        conToken(post("/api/v1/panel/vacantes/" + id + "/publicacion"), tokenEquipo, null)
                .andExpect(status().isOk());
        return id;
    }

    private String crearCandidatoYEntrar(String correo) throws Exception {
        mvc.perform(post("/api/v1/portal/cuentas")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                        {"nombre":"Camila","apellidos":"Rojas","correo":"%s",
                         "contrasena":"unaClaveLarga123","aceptaProceso":true,
                         "aceptaFuturosContactos":false}""".formatted(correo)))
                .andExpect(status().isCreated());
        return leer(mvc.perform(post("/api/v1/portal/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"correo\":\"%s\",\"contrasena\":\"unaClaveLarga123\"}".formatted(correo)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(), "token");
    }

    // La mínima prueba del puesto publicable, que la vacante exige para publicarse
    private Long armarUnaPruebaValida(String token) throws Exception {
        long plantillaId = Long.parseLong(leer(conToken(post("/api/v1/panel/plantillas-prueba"), token,
                "{\"nombre\":\"Prueba genérica\"}")
                .andReturn().getResponse().getContentAsString(), "id"));
        long versionId = Long.parseLong(leer(conToken(
                post("/api/v1/panel/plantillas-prueba/" + plantillaId + "/versiones"), token, """
                {"enunciado":"Resuelve el caso propuesto","modalidad":"CRONOMETRADA",
                 "duracionMinutos":90,"minutoCambioMin":30,"minutoCambioMax":50,"minutosExtra":10}""")
                .andReturn().getResponse().getContentAsString(), "id"));

        for (int i = 0; i < 8; i++) {
            long id = Long.parseLong(leer(conToken(post("/api/v1/panel/plantillas-prueba/preguntas"), token,
                    "{\"codigo\":\"UNIV_IA_%d\",\"enunciado\":\"Pregunta universal %d\",\"tipo\":\"UNIVERSAL\"}"
                            .formatted(i, i))
                    .andReturn().getResponse().getContentAsString(), "id"));
            conToken(post("/api/v1/panel/plantillas-prueba/versiones/" + versionId + "/preguntas"), token,
                    "{\"preguntaPruebaId\": %d}".formatted(id)).andExpect(status().isOk());
        }
        for (int i = 0; i < 3; i++) {
            long id = Long.parseLong(leer(conToken(post("/api/v1/panel/plantillas-prueba/preguntas"), token,
                    "{\"codigo\":\"ESP_IA_%d\",\"enunciado\":\"Pregunta específica %d\",\"tipo\":\"ESPECIFICA\"}"
                            .formatted(i, i))
                    .andReturn().getResponse().getContentAsString(), "id"));
            conToken(post("/api/v1/panel/plantillas-prueba/versiones/" + versionId + "/preguntas"), token,
                    "{\"preguntaPruebaId\": %d}".formatted(id)).andExpect(status().isOk());
        }
        conToken(post("/api/v1/panel/plantillas-prueba/versiones/" + versionId + "/rubrica"), token, """
                {"codigo":"RESULTADO_IA","nombre":"Resultado","puntos":100,"metodoVerificacion":"PERSONA"}""")
                .andExpect(status().isCreated());

        conToken(post("/api/v1/panel/plantillas-prueba/versiones/" + versionId + "/publicacion"), token, null)
                .andExpect(status().isOk());
        return versionId;
    }

    private ResultActions conToken(
            org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder peticion,
            String token, String cuerpo) throws Exception {
        peticion.header("Authorization", "Bearer " + token).contentType(MediaType.APPLICATION_JSON);
        if (cuerpo != null) peticion.content(cuerpo);
        return mvc.perform(peticion);
    }

    private String leer(String cuerpoRespuesta, String campo) throws Exception {
        return json.readTree(cuerpoRespuesta).get(campo).asText();
    }
}
