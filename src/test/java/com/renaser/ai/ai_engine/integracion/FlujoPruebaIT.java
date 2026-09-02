package com.renaser.ai.ai_engine.integracion;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.renaser.ai.ai_engine.comun.programado.SondeoVencimientos;
import com.renaser.ai.ai_engine.integracion.soporte.ImagenesDeContenedores;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.DisplayName;
import com.renaser.ai.ai_engine.integracion.soporte.RespuestaV3;
import com.renaser.ai.ai_engine.prueba.dto.DtosPruebaIa.NotaCriterioPruebaIa;
import com.renaser.ai.ai_engine.prueba.dto.DtosPruebaIa.ResultadoPrueba;
import com.renaser.ai.ai_engine.prueba.service.PuentePruebaIa;
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

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * El hito 3 de punta a punta: la prueba del puesto y la decisión final.
 *
 * <p>Cubre lo que hasta ahora no existía: armar una prueba con su rúbrica, que un candidato la
 * rinda con su cronómetro real, calificarla criterio a criterio, y que una persona tome la
 * decisión final — con las tres reglas que lo sostienen:
 *
 * <ul>
 *   <li>Sin Simulación ni Validación construidas todavía, "confirmar avance" desde
 *       {@code PRUEBA_POR_CONFIRMAR} debe fallar con un mensaje claro, no dejar a nadie en un
 *       estado fantasma (el mismo callejón sin salida que ya se corrigió una vez para el
 *       portal, aquí evitado antes de que llegara a existir).
 *   <li>La decisión final no es de Talento (RF-119): solo el responsable del área o Dirección.
 *   <li>El reloj de la prueba lo lleva el servidor: un intento vencido se entrega solo.
 * </ul>
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@Testcontainers
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@DisplayName("Hito 3 · La prueba del puesto y la decisión")
public class FlujoPruebaIT {

    @Container
    @ServiceConnection
    static PostgreSQLContainer postgres = new PostgreSQLContainer("pgvector/pgvector:pg16");

    @Container
    @ServiceConnection
    static RabbitMQContainer rabbit = new RabbitMQContainer(ImagenesDeContenedores.RABBITMQ);

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
        // El dev-login quedo apagado por defecto en application.yaml: aqui se enciende
        // explicitamente, porque estas pruebas entran al panel por el.
        registro.add("app.seguridad.dev-login-activo", () -> "true");
        registro.add("spring.ai.deepseek.api-key", () -> "clave-de-pruebas-no-se-usa");
        // La calificacion con IA se apaga en estas pruebas: aqui no se prueba, y si estuviera
        // encendida cada entrega intentaria hablar con DeepSeek con una clave de mentira.
        // Quien la prueba de verdad es FlujoCalificacionIaIT, con el modelo sustituido.
        registro.add("renaser.ai.calificacion.habilitada", () -> "false");
    }

    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired SondeoVencimientos sondeo;
    @Autowired PuentePruebaIa puente;
    final ObjectMapper json = new ObjectMapper();

    static String tokenTalento;
    static String tokenArea;
    static String tokenCandidato;
    static String codigoPostulacion;
    static long vacanteId;
    static long postulacionId;
    static long versionPruebaId;
    static long criterioId;
    static long entregableObligatorioId;

    @DisplayName("Talento arma una prueba con su rúbrica")
    @Test
    @Order(1)
    void talentoArmaUnaPruebaConSuRubrica() throws Exception {
        tokenTalento = leer(mvc.perform(post("/api/v1/panel/auth/dev-login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"usuarioRenaserOsId\":\"dev-talento\"}"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(), "token");

        long plantillaId = Long.parseLong(leer(conToken(post("/api/v1/panel/plantillas-prueba"), tokenTalento,
                "{\"nombre\":\"Prueba de desarrollo web\"}")
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString(), "id"));

        versionPruebaId = Long.parseLong(leer(conToken(
                post("/api/v1/panel/plantillas-prueba/" + plantillaId + "/versiones"), tokenTalento, """
                {"enunciado":"Construye un pequeño buscador de recetas",
                 "materiales":"Un dataset de 200 recetas en JSON",
                 "herramientasPermitidas":"Cualquier framework, incluida IA",
                 "modalidad":"CRONOMETRADA","duracionMinutos":90,
                 "minutoCambioMin":30,"minutoCambioMax":50,"minutosExtra":10}""")
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString(), "id"));

        conToken(post("/api/v1/panel/plantillas-prueba/versiones/" + versionPruebaId + "/variantes"),
                tokenTalento, "{\"texto\":\"Un usuario reporta que la búsqueda no filtra por ingrediente\"}")
                .andExpect(status().isCreated());

        for (int i = 0; i < 8; i++) {
            long id = Long.parseLong(leer(conToken(post("/api/v1/panel/plantillas-prueba/preguntas"), tokenTalento,
                    "{\"codigo\":\"UNIV_P_%d\",\"enunciado\":\"Pregunta universal %d\",\"tipo\":\"UNIVERSAL\"}"
                            .formatted(i, i))
                    .andExpect(status().isCreated())
                    .andReturn().getResponse().getContentAsString(), "id"));
            conToken(post("/api/v1/panel/plantillas-prueba/versiones/" + versionPruebaId + "/preguntas"),
                    tokenTalento, "{\"preguntaPruebaId\": %d}".formatted(id)).andExpect(status().isOk());
        }
        for (int i = 0; i < 3; i++) {
            long id = Long.parseLong(leer(conToken(post("/api/v1/panel/plantillas-prueba/preguntas"), tokenTalento,
                    "{\"codigo\":\"ESP_P_%d\",\"enunciado\":\"Pregunta específica %d\",\"tipo\":\"ESPECIFICA\"}"
                            .formatted(i, i))
                    .andExpect(status().isCreated())
                    .andReturn().getResponse().getContentAsString(), "id"));
            conToken(post("/api/v1/panel/plantillas-prueba/versiones/" + versionPruebaId + "/preguntas"),
                    tokenTalento, "{\"preguntaPruebaId\": %d}".formatted(id)).andExpect(status().isOk());
        }

        entregableObligatorioId = Long.parseLong(leer(conToken(
                post("/api/v1/panel/plantillas-prueba/versiones/" + versionPruebaId + "/entregables"), tokenTalento, """
                {"nombre":"MVP funcional","detalle":"Enlace a un repositorio o despliegue",
                 "formato":"ENLACE","esObligatorio":true}""")
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString(), "id"));
        conToken(post("/api/v1/panel/plantillas-prueba/versiones/" + versionPruebaId + "/entregables"), tokenTalento, """
                {"nombre":"Video explicativo","detalle":"Máximo 5 minutos",
                 "formato":"ENLACE","esObligatorio":false}""")
                .andExpect(status().isCreated());

        // Publicar sin que la rúbrica sume 100 no pasa (RF-89)
        long criterioIncompleto = Long.parseLong(leer(conToken(
                post("/api/v1/panel/plantillas-prueba/versiones/" + versionPruebaId + "/rubrica"), tokenTalento, """
                {"codigo":"RESULTADO","nombre":"Resultado producido","puntos":60,"metodoVerificacion":"AGENTE"}""")
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString(), "id"));
        conToken(post("/api/v1/panel/plantillas-prueba/versiones/" + versionPruebaId + "/publicacion"),
                tokenTalento, null).andExpect(status().isBadRequest());

        criterioId = Long.parseLong(leer(conToken(
                post("/api/v1/panel/plantillas-prueba/versiones/" + versionPruebaId + "/rubrica"), tokenTalento, """
                {"codigo":"CALIDAD","nombre":"Calidad","puntos":40,"metodoVerificacion":"AGENTE"}""")
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString(), "id"));

        conToken(post("/api/v1/panel/plantillas-prueba/versiones/" + versionPruebaId + "/publicacion"),
                tokenTalento, null).andExpect(status().isOk());

        // El rango 60-120 se retiro: una de 45 minutos ya es una decision legitima de quien
        // escribe la prueba, y se crea sin protestar.
        conToken(post("/api/v1/panel/plantillas-prueba/" + plantillaId + "/versiones"),
                tokenTalento, """
                {"enunciado":"x","modalidad":"CRONOMETRADA","duracionMinutos":45}""")
                .andExpect(status().isCreated());

        // Lo que si queda es el suelo de cinco minutos, y se frena antes de guardar nada:
        // por debajo de ahi el barrido entrega la prueba sola antes de que dé tiempo a leer
        // el enunciado. Es el mismo suelo que valida la ficha de la vacante.
        conToken(post("/api/v1/panel/plantillas-prueba/" + plantillaId + "/versiones"),
                tokenTalento, """
                {"enunciado":"x","modalidad":"CRONOMETRADA","duracionMinutos":1}""")
                .andExpect(status().isBadRequest());

        // Y una cronometrada SIN duracion no llega ni a crearse: la V15 ya lo prohibe con un
        // CHECK, asi que la guarda de `publicarVersion` es la segunda linea y no la primera.
        conToken(post("/api/v1/panel/plantillas-prueba/" + plantillaId + "/versiones"),
                tokenTalento, """
                {"enunciado":"x","modalidad":"CRONOMETRADA"}""")
                .andExpect(status().isBadRequest());

        criterioId = criterioIncompleto;   // se usa el primero para poner nota más adelante junto al segundo
    }

    @DisplayName("Una vacante recorre todo el camino hasta la prueba del puesto")
    @Test
    @Order(2)
    void unaVacanteConTodoElCaminoHastaPruebaPuesto() throws Exception {
        vacanteId = prepararVacantePublicada();
        tokenCandidato = crearCandidatoYEntrar();

        MockMultipartFile cv = new MockMultipartFile("cv", "cv.pdf", "application/pdf", "contenido".getBytes());
        codigoPostulacion = leer(mvc.perform(multipart("/api/v1/portal/postulaciones")
                        .file(cv)
                        .param("vacanteId", String.valueOf(vacanteId))
                        .param("resultadoOrgulloso", "Reduje el tiempo de build de 8 a 2 minutos")
                        .param("aceptaTratamiento", "true")
                        .header("Authorization", "Bearer " + tokenCandidato))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString(), "codigo");

        // Recorre el Perfil Integral (hito 2) para llegar a PERFIL_POR_CONFIRMAR
        JsonNode evaluacion = json.readTree(mvc.perform(
                        post("/api/v1/portal/evaluacion/" + codigoPostulacion + "/inicio")
                                .header("Authorization", "Bearer " + tokenCandidato))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString());
        for (JsonNode p : evaluacion.get("preguntas")) {
            // Cada formato del banco v3 se responde a su manera; RespuestaV3 arma la que toque.
            String cuerpo = RespuestaV3.para(p);
            mvc.perform(put("/api/v1/portal/evaluacion/" + codigoPostulacion + "/respuestas/" + p.get("id").asLong())
                            .header("Authorization", "Bearer " + tokenCandidato)
                            .contentType(MediaType.APPLICATION_JSON).content(cuerpo))
                    .andExpect(status().isOk());
        }
        mvc.perform(post("/api/v1/portal/evaluacion/" + codigoPostulacion + "/entrega")
                        .header("Authorization", "Bearer " + tokenCandidato))
                .andExpect(status().isOk());

        String bandeja = conTokenGet("/api/v1/panel/bandeja?espera_a=SISTEMA", tokenTalento)
                .andReturn().getResponse().getContentAsString();
        postulacionId = json.readTree(bandeja).get(0).get("postulacionId").asLong();

        conToken(post("/api/v1/panel/postulaciones/" + postulacionId + "/confirmacion-avance"), tokenTalento,
                "{\"motivo\":\"Perfil Integral calificado\"}").andExpect(status().isOk());

        // Aquí es donde antes se habría caído en un callejón: confirmar avance desde
        // PERFIL_POR_CONFIRMAR crea el intento de prueba y transiciona a PRUEBA_TURNO_CANDIDATO.
        conToken(post("/api/v1/panel/postulaciones/" + postulacionId + "/confirmacion-avance"), tokenTalento,
                "{\"motivo\":\"No priorizado descartado a mano en esta prueba\"}").andExpect(status().isOk());

        conTokenGet("/api/v1/portal/postulaciones", tokenCandidato)
                .andExpect(jsonPath("$[0].estado").value("PRUEBA_TURNO_CANDIDATO"));

        // La vara se puede mover MIENTRAS NADIE HAYA EMPEZADO, y esta es la comprobacion
        // contra la base de verdad: hay una postulacion dentro, con su intento ya creado, y
        // aun asi la vacante se deja reconfigurar. Con la linea vieja —«ninguna
        // postulacion»— esta llamada era un 409, y una vacante con un solo curriculum dentro
        // quedaba congelada hasta la siguiente convocatoria.
        //
        // Se bajan los 90 de la plantilla a 45: en el @Order(3) se comprueba que son esos 45
        // los que le llegan al candidato.
        conToken(post("/api/v1/panel/vacantes/" + vacanteId + "/instrumento-tecnico"), tokenTalento,
                "{\"instrumento\": \"PLANTILLA\", \"minutos\": 45}")
                .andExpect(status().isOk());

        // Y el suelo de cinco minutos tambien rige aqui, no solo en la plantilla.
        conToken(post("/api/v1/panel/vacantes/" + vacanteId + "/instrumento-tecnico"), tokenTalento,
                "{\"instrumento\": \"PLANTILLA\", \"minutos\": 1}")
                .andExpect(status().isBadRequest());
    }

    @DisplayName("El candidato rinde la prueba con su cronómetro")
    @Test
    @Order(3)
    void elCandidatoRindeLaPruebaConSuCronometro() throws Exception {
        JsonNode prueba = json.readTree(mvc.perform(post("/api/v1/portal/prueba/" + codigoPostulacion + "/inicio")
                        .header("Authorization", "Bearer " + tokenCandidato))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estadoIntento").value("EN_CURSO"))
                // ⚠️ 45, no los 90 de la plantilla: los minutos de la vacante mandan, y el
                // numero que viaja a la pantalla es el que el servidor va a aplicar de
                // verdad. Antes este campo salia de la version y la vacante no lo tocaba:
                // la pantalla decia «90 minutos desde que empieces» y era mentira.
                .andExpect(jsonPath("$.duracionMinutos").value(45))
                .andReturn().getResponse().getContentAsString());

        // Y en cuanto ESTA persona empezo, la vara se queda quieta: mover ahora los minutos
        // le cambiaria el examen debajo mientras lo hace.
        conToken(post("/api/v1/panel/vacantes/" + vacanteId + "/instrumento-tecnico"), tokenTalento,
                "{\"instrumento\": \"PLANTILLA\", \"minutos\": 60}")
                .andExpect(status().isConflict());

        // El cambio inesperado no viaja de antemano (RF-77)
        assertThat(prueba.get("cambioTexto").isNull()).isTrue();

        for (JsonNode p : prueba.get("preguntas")) {
            mvc.perform(put("/api/v1/portal/prueba/" + codigoPostulacion + "/respuestas/" + p.get("id").asLong())
                            .header("Authorization", "Bearer " + tokenCandidato)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"texto\":\"Respuesta concreta y verificable\"}"))
                    .andExpect(status().isOk());
        }

        // Entregar sin el obligatorio no pasa
        mvc.perform(post("/api/v1/portal/prueba/" + codigoPostulacion + "/entrega")
                        .header("Authorization", "Bearer " + tokenCandidato))
                .andExpect(status().isBadRequest());

        mvc.perform(post("/api/v1/portal/prueba/" + codigoPostulacion + "/entregables/"
                        + entregableObligatorioId + "/enlace")
                        .header("Authorization", "Bearer " + tokenCandidato)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"enlace\":\"https://github.com/candidata/buscador-recetas\"}"))
                .andExpect(status().isOk());

        mvc.perform(post("/api/v1/portal/prueba/" + codigoPostulacion + "/entrega")
                        .header("Authorization", "Bearer " + tokenCandidato))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("ENTREGADA"));

        conTokenGet("/api/v1/portal/postulaciones", tokenCandidato)
                .andExpect(jsonPath("$[0].estado").value("PRUEBA_CALIFICANDO"));
    }

    @DisplayName("El panel ve lo que entregó: el enlace que pegó, y el que falta")
    @Test
    @Order(4)
    void elPanelVeLoQueEntrego() throws Exception {
        /*
         * ⚠️ **Esto es lo que hacía falta para poder calificar a mano.** La rúbrica
         * reserva criterios a una persona justo cuando la IA no puede leer el entregable
         * —un vídeo, un enlace—, y hasta ahora el panel enseñaba la rúbrica y no lo que
         * se juzgaba con ella. Se le pedía a alguien un puntaje sobre algo que la
         * pantalla no le enseñaba.
         *
         * La fixtura del @Order(3) ya deja el escenario: el obligatorio entregado por
         * enlace, y el segundo pedido sin entregar.
         */
        conTokenGet("/api/v1/panel/postulaciones/" + postulacionId + "/prueba/entregables", tokenTalento)
                .andExpect(status().isOk())
                // Salen los DOS, no solo el entregado: un hueco se leería como una lista
                // completa, y lo que falta es justo lo que hay que ver antes de poner una
                // nota. El segundo de esta plantilla es opcional, así que su motivo lo dice
                // sin la coletilla del obligatorio — son dos frases distintas a propósito.
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].loEntrego").value(true))
                .andExpect(jsonPath("$[0].esObligatorio").value(true))
                .andExpect(jsonPath("$[0].enlace").value("https://github.com/candidata/buscador-recetas"))
                .andExpect(jsonPath("$[1].loEntrego").value(false))
                .andExpect(jsonPath("$[1].esObligatorio").value(false))
                .andExpect(jsonPath("$[1].porQueNoSeVe").value("No lo entregó"));
    }

    @DisplayName("El recorrido sigue sin saltos manuales")
    @Test
    @Order(5)
    void elRecorridoSigueSinSaltosManuales() throws Exception {
        // Entregada la prueba: PRUEBA_CALIFICANDO -> PRUEBA_POR_CONFIRMAR
        conToken(post("/api/v1/panel/postulaciones/" + postulacionId + "/confirmacion-avance"), tokenTalento,
                "{\"motivo\":\"Prueba calificada\"}").andExpect(status().isOk());
        conTokenGet("/api/v1/portal/postulaciones", tokenCandidato)
                .andExpect(jsonPath("$[0].estado").value("PRUEBA_POR_CONFIRMAR"));

        // Y de aquí a simulación, sin saltos ni transiciones manuales. Antes esto fallaba a
        // propósito -la etapa no existía y había un guardia que lo impedía-; ahora el
        // recorrido sigue solo, que es justo lo que este trabajo vino a arreglar.
        conToken(post("/api/v1/panel/postulaciones/" + postulacionId + "/confirmacion-avance"), tokenTalento,
                "{\"motivo\":\"Avanza a la simulación\"}").andExpect(status().isOk());
        conTokenGet("/api/v1/portal/postulaciones", tokenCandidato)
                .andExpect(jsonPath("$[0].estado").value("SIMULACION_POR_HABILITAR"));
    }

    @DisplayName("Talento califica la prueba criterio a criterio")
    @Test
    @Order(6)
    void talentoCalificaLaPruebaCriterioACriterio() throws Exception {
        // Sin todos los criterios puestos, no se puede cerrar la nota de la etapa
        conToken(post("/api/v1/panel/postulaciones/" + postulacionId + "/prueba/calificacion"), tokenTalento, null)
                .andExpect(status().isConflict());

        conToken(post("/api/v1/panel/postulaciones/" + postulacionId + "/prueba/criterios/" + criterioId + "/nota"),
                tokenTalento, "{\"puntaje\":50,\"explicacion\":\"El buscador funciona y filtra bien\"}")
                .andExpect(status().isOk());

        List<JsonNode> notas = new java.util.ArrayList<>();
        json.readTree(conTokenGet("/api/v1/panel/postulaciones/" + postulacionId + "/prueba/notas", tokenTalento)
                        .andReturn().getResponse().getContentAsString())
                .forEach(notas::add);
        long segundoCriterioId = notas.stream()
                .filter(n -> n.get("puntaje").isNull())
                .findFirst().orElseThrow().get("criterioId").asLong();

        conToken(post("/api/v1/panel/postulaciones/" + postulacionId + "/prueba/criterios/"
                        + segundoCriterioId + "/nota"), tokenTalento,
                "{\"puntaje\":35,\"explicacion\":\"Código limpio, faltó cubrir un caso borde\"}")
                .andExpect(status().isOk());

        // Una nota de criterio que NO es de esta rúbrica, como la que deja la criba o el
        // perfil integral: `nota_criterio` es una sola tabla para las tres etapas que
        // puntúan por criterio. La nota de la prueba tiene que seguir siendo 85.
        //
        // Sin esto el fallo no se veía: aquí se sumaban TODAS las notas de la postulación,
        // y como en las pruebas de antes solo existían las dos de la rúbrica, el total
        // coincidía por casualidad. En producción no: un candidato de 50 sobre 100 salió
        // con 675 porque se le pegaron las siete del perfil.
        Long criterioAjeno = jdbc.queryForObject(
                "insert into criterio (codigo, nombre, etapa_codigo, puntos, metodo_verificacion, orden) "
                        + "values ('DE_OTRA_ETAPA', 'Criterio de otra etapa', 'PERFIL_INTEGRAL', "
                        + "590, 'AGENTE', 99) returning id", Long.class);
        jdbc.update("insert into nota_criterio (postulacion_id, criterio_id, puntaje, explicacion, origen) "
                + "values (?, ?, 590, 'De otra etapa', 'AGENTE')", postulacionId, criterioAjeno);

        String cuerpo = conToken(post("/api/v1/panel/postulaciones/" + postulacionId + "/prueba/calificacion"),
                        tokenTalento, null)
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        assertThat(json.readTree(cuerpo).get("nota").asDouble()).isEqualTo(85.0);

        Map<String, Object> nota = jdbc.queryForMap(
                "select puntaje, version_pesos_id from nota_etapa where postulacion_id = ? and etapa_codigo = 'PRUEBA_PUESTO'",
                postulacionId);
        assertThat(((Number) nota.get("puntaje")).doubleValue()).isEqualTo(85.0);
        assertThat(nota.get("version_pesos_id")).isNotNull();
    }

    @DisplayName("Se salta lo que no aplica y se toma la decisión")
    @Test
    @Order(7)
    void seSaltaLoQueNoAplicaYSeDecide() throws Exception {
        // Simulación y validación se pueden saltar cuando el puesto no las necesita: es una
        // transición manual con motivo, que RF-121 permite para cualquier salto. Lo que ya no
        // hace falta es saltárselas por obligación, que era el parche de antes.
        conToken(post("/api/v1/panel/postulaciones/" + postulacionId + "/transiciones"), tokenTalento, """
                {"estadoDestino":"DECISION_POR_CONFIRMAR",
                 "motivo":"Este puesto no requiere simulación ni periodo de validación"}""")
                .andExpect(status().isOk());

        JsonNode semaforo = json.readTree(
                conTokenGet("/api/v1/panel/postulaciones/" + postulacionId + "/semaforo", tokenTalento)
                        .andExpect(status().isOk())
                        .andReturn().getResponse().getContentAsString());
        // Ahora la vacante pesa las cuatro etapas, así que faltan las dos que se saltaron:
        // el semáforo lo dice en vez de inventarse una nota con la mitad de la evidencia.
        assertThat(semaforo.get("etapasQueFaltan")).isNotEmpty();

        // Talento no decide: RF-119 dice que es del responsable del área o de Dirección.
        // tokenTalento es el usuario de bootstrap del primer dev-login -tiene TALENTO,
        // DIRECCION y ADMINISTRADOR a la vez-, así que no sirve para probar la exclusión;
        // hace falta un Talento "puro", sin los roles extra.
        String tokenSoloTalento = crearUsuarioConUnSoloRol("Elena", "Vidal", "elena.talento@renaser.pe",
                "os-elena-talento", "TALENTO");
        conToken(post("/api/v1/panel/postulaciones/" + postulacionId + "/decision"), tokenSoloTalento,
                "{\"semaforo\":\"VERDE\",\"motivo\":\"Buen desempeño\"}")
                .andExpect(status().isForbidden());

        tokenArea = crearResponsableDeArea(vacanteId);
        conToken(post("/api/v1/panel/postulaciones/" + postulacionId + "/decision"), tokenArea,
                "{\"semaforo\":\"VERDE\",\"motivo\":\"Prueba sólida y Perfil Integral consistente\"}")
                .andExpect(status().isOk());

        conTokenGet("/api/v1/portal/postulaciones", tokenCandidato)
                .andExpect(jsonPath("$[0].estado").value("CONTRATADO"));

        Map<String, Object> decision = jdbc.queryForMap(
                "select semaforo, decidida_por_usuario_id from decision where postulacion_id = ?", postulacionId);
        assertThat(decision.get("semaforo")).isEqualTo("VERDE");
        assertThat(decision.get("decidida_por_usuario_id")).isNotNull();
    }

    @DisplayName("Un intento vencido se entrega solo")
    @Test
    @Order(8)
    void unIntentoVencidoSeEntregaSolo() throws Exception {
        // Otra postulación, esta vez dejada vencer: el sondeo debe cerrarla sin que nadie actúe.
        String correo = "vence@correo.pe";
        String token = crearCandidatoYEntrar(correo);
        MockMultipartFile cv = new MockMultipartFile("cv", "cv.pdf", "application/pdf", "x".getBytes());
        String codigo = leer(mvc.perform(multipart("/api/v1/portal/postulaciones")
                        .file(cv)
                        .param("vacanteId", String.valueOf(vacanteId))
                        .param("resultadoOrgulloso", "Otro resultado del que me siento orgullosa")
                        .param("aceptaTratamiento", "true")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString(), "codigo");

        long otraPostulacionId = jdbc.queryForObject(
                "select id from postulacion where uuid = ?::uuid", Long.class, codigo);

        // Salta directo al Perfil Integral calificado y a su turno de prueba, a mano por SQL:
        // lo que se prueba aquí es el vencimiento, no el camino hasta la prueba otra vez.
        jdbc.update("update postulacion set estado_codigo = 'PRUEBA_TURNO_CANDIDATO' where id = ?", otraPostulacionId);
        jdbc.update("""
                insert into intento_prueba (postulacion_id, version_plantilla_prueba_id, iniciado_en, vence_en)
                values (?, ?, now() - interval '2 hours', now() - interval '1 hour')""",
                otraPostulacionId, versionPruebaId);

        sondeo.ejecutar();

        String estado = jdbc.queryForObject("select estado_codigo from postulacion where id = ?",
                String.class, otraPostulacionId);
        assertThat(estado).isEqualTo("PRUEBA_CALIFICANDO");

        Map<String, Object> intento = jdbc.queryForMap(
                "select entregado_en, es_entrega_automatica from intento_prueba where postulacion_id = ?",
                otraPostulacionId);
        assertThat(intento.get("entregado_en")).isNotNull();
        assertThat(intento.get("es_entrega_automatica")).isEqualTo(true);
    }

    /**
     * Que la nota de la etapa salga sola cuando el agente deja la rúbrica entera.
     *
     * <p>Contra la base de verdad, y no con dobles, porque lo que hay que comprobar es
     * justo lo que un doble da por supuesto: que la comprobación de «¿está entera?» —que
     * lee de la base— ve las notas que ese mismo trabajo acaba de guardar y todavía no ha
     * confirmado. Si no las viera, la rúbrica parecería incompleta siempre y no se sumaría
     * nunca, que es el fallo que esto vino a arreglar.
     *
     * <p>Y comprueba lo otro que un doble no puede: que la transacción llega entera al
     * final. La suma lanza cuando le falta un criterio, y si esa excepción escapara se
     * perderían las notas del modelo recién guardadas.
     */
    @DisplayName("Al terminar el agente, la nota de la etapa sale sola si la rúbrica quedó entera")
    @Test
    @Order(9)
    void alTerminarElAgenteLaNotaDeEtapaSaleSolaSiLaRubricaQuedoEntera() throws Exception {
        long id = otraPostulacionEnCalificando("califica-ia@correo.pe");

        // 1 · El modelo solo pudo juzgar uno de los dos criterios, que es lo normal. Se
        // guarda lo que trajo y la etapa se queda sin nota: media rúbrica no es un juicio.
        puente.guardarNotasPrueba(id, null, new ResultadoPrueba(
                List.of(new NotaCriterioPruebaIa("RESULTADO", new BigDecimal("60"),
                        "El buscador filtra por ingrediente", "El README lo demuestra")),
                new BigDecimal("80")));

        assertThat(cuantasNotasDeCriterio(id)).isEqualTo(1);
        assertThat(laNotaDeEtapa(id)).isNull();
        // Aun sin nota, la prueba pasa a manos de una persona: esconderla sería peor.
        assertThat(jdbc.queryForObject("select estado_codigo from postulacion where id = ?",
                String.class, id)).isEqualTo("PRUEBA_POR_CONFIRMAR");

        // 2 · Llega la segunda nota y la rúbrica queda entera. La de CALIDAD es una fila
        // nueva y la de RESULTADO una que se reescribe: las dos tienen que estar a la vista
        // de la comprobación dentro de la misma transacción que las escribió.
        puente.guardarNotasPrueba(id, null, new ResultadoPrueba(
                List.of(new NotaCriterioPruebaIa("RESULTADO", new BigDecimal("60"),
                                "El buscador filtra por ingrediente", null),
                        new NotaCriterioPruebaIa("CALIDAD", new BigDecimal("30"),
                                "Código legible, sin pruebas", null)),
                new BigDecimal("80")));

        assertThat(cuantasNotasDeCriterio(id)).isEqualTo(2);
        assertThat(laNotaDeEtapa(id)).isEqualTo(90.0);
        assertThat(jdbc.queryForMap("select version_pesos_id from nota_etapa where postulacion_id = ?", id)
                .get("version_pesos_id")).isNotNull();

        // 3 · El mismo trabajo otra vez, que es lo que hace la cola al reintentar. Ni una
        // fila de más ni una excepción que se lleve por delante lo ya guardado.
        puente.guardarNotasPrueba(id, null, new ResultadoPrueba(
                List.of(new NotaCriterioPruebaIa("RESULTADO", new BigDecimal("60"),
                                "El buscador filtra por ingrediente", null),
                        new NotaCriterioPruebaIa("CALIDAD", new BigDecimal("30"),
                                "Código legible, sin pruebas", null)),
                new BigDecimal("80")));

        assertThat(jdbc.queryForObject(
                "select count(*) from nota_etapa where postulacion_id = ? and etapa_codigo = 'PRUEBA_PUESTO'",
                Integer.class, id)).isEqualTo(1);
        assertThat(cuantasNotasDeCriterio(id)).isEqualTo(2);
        assertThat(laNotaDeEtapa(id)).isEqualTo(90.0);
    }

    /**
     * Componer un borrador equivocándose, contra la base de verdad.
     *
     * <p>Esto <b>no</b> lo pueden cubrir las pruebas de dobles del paquete, y por eso está
     * aquí: lo que se comprueba es el UNIQUE (versión, orden) de la V15, que un repositorio
     * simulado nunca levanta, y el {@code flush} entre las dos tandas del renumerado, que en
     * un doble es una llamada vacía. Con {@code size()+1} el paso 2 de aquí abajo devolvería
     * un 500, y un renumerado de una sola tanda fallaría en el paso 3 según en qué orden
     * ejecutara la base los UPDATE. <b>No lo borres pensando que los unitarios lo cubren.</b>
     */
    @DisplayName("Un borrador se corrige y se recompone sin quedarse sin salida")
    @Test
    @Order(10)
    void unBorradorSeCorrigeYSeRecompone() throws Exception {
        long plantillaId = Long.parseLong(leer(conToken(post("/api/v1/panel/plantillas-prueba"),
                tokenTalento, "{\"nombre\":\"Prueba que se compone a mano\"}")
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString(), "id"));
        long borradorId = Long.parseLong(leer(conToken(
                post("/api/v1/panel/plantillas-prueba/" + plantillaId + "/versiones"), tokenTalento, """
                {"enunciado":"Primer intento de enunciado","modalidad":"CRONOMETRADA",
                 "duracionMinutos":90}""")
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString(), "id"));
        String base = "/api/v1/panel/plantillas-prueba/versiones/" + borradorId;

        // 1 · Tres entregables, y se quita el del medio
        long a = agregarEntregable(borradorId, "Primero");
        long b = agregarEntregable(borradorId, "Segundo");
        long c = agregarEntregable(borradorId, "Tercero");
        conToken(delete("/api/v1/panel/plantillas-prueba/entregables/" + b), tokenTalento, null)
                .andExpect(status().isNoContent());

        // 2 · El siguiente que se agrega NO reclama el hueco del 2: pide el 4
        long d = agregarEntregable(borradorId, "Cuarto");

        // 3 · Y la lista se recompone entera, contra el UNIQUE de verdad
        conToken(put(base + "/entregables/orden"), tokenTalento,
                "{\"idsEnOrden\": [%d, %d, %d]}".formatted(d, c, a))
                .andExpect(status().isOk());
        assertThat(nombresDeEntregables(borradorId)).containsExactly("Cuarto", "Tercero", "Primero");

        // 3b · `variante_cambio` es la otra tabla con UNIQUE (versión, orden): mismo camino
        long v1 = agregarVariante(borradorId, "Se cae el sistema");
        long v2 = agregarVariante(borradorId, "Llega un pedido urgente");
        conToken(delete("/api/v1/panel/plantillas-prueba/variantes/" + v1), tokenTalento, null)
                .andExpect(status().isNoContent());
        long v3 = agregarVariante(borradorId, "El cliente cambia de idea");
        conToken(put(base + "/variantes/orden"), tokenTalento,
                "{\"idsEnOrden\": [%d, %d]}".formatted(v3, v2)).andExpect(status().isOk());
        assertThat(jdbc.queryForList(
                "select texto from variante_cambio where version_plantilla_prueba_id = ? order by orden",
                String.class, borradorId))
                .containsExactly("El cliente cambia de idea", "Llega un pedido urgente");

        // 4 · Este borrador elige las MISMAS preguntas del catálogo que la versión ya
        // publicada más arriba: es lo normal, el catálogo es global. Una universal de más
        // para poder quitarla después sin bajarse de la cuota de RF-83.
        long compartida = Long.parseLong(leer(conToken(
                post("/api/v1/panel/plantillas-prueba/preguntas"), tokenTalento, """
                {"codigo":"UNIV_COMPARTIDA","enunciado":"¿Qué harías distinto?","tipo":"UNIVERSAL"}""")
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString(), "id"));
        elegir(base, compartida);
        for (int i = 0; i < 8; i++) {
            elegir(base, laPreguntaDelCatalogo("UNIV_P_" + i));
        }
        for (int i = 0; i < 3; i++) {
            elegir(base, laPreguntaDelCatalogo("ESP_P_" + i));
        }

        // ⚠️ Quitar una pregunta de ESTA versión no la borra del catálogo ni se la quita a la
        // otra versión que la tiene elegida. Si algún día esto empieza a fallar, el examen que
        // se le sirve a alguien se habrá vaciado desde otra plantilla.
        long compartidaConLaPublicada = laPreguntaDelCatalogo("UNIV_P_0");
        conToken(delete(base + "/preguntas/" + compartidaConLaPublicada), tokenTalento, null)
                .andExpect(status().isNoContent());
        assertThat(jdbc.queryForObject("select count(*) from pregunta_prueba where id = ?",
                Integer.class, compartidaConLaPublicada)).isEqualTo(1);
        assertThat(jdbc.queryForObject("""
                select count(*) from pregunta_version_plantilla
                where version_plantilla_prueba_id = ? and pregunta_prueba_id = ?""",
                Integer.class, versionPruebaId, compartidaConLaPublicada)).isEqualTo(1);

        // 5 · El callejón sin salida de la rúbrica: 60 + 40 + 40 = 140, y se deshace
        agregarCriterio(borradorId, "RES", 60);
        agregarCriterio(borradorId, "CAL", 40);
        long deMas = agregarCriterio(borradorId, "SOBRA", 40);
        conToken(post(base + "/publicacion"), tokenTalento, null).andExpect(status().isBadRequest());
        conToken(delete("/api/v1/panel/plantillas-prueba/rubrica/" + deMas), tokenTalento, null)
                .andExpect(status().isNoContent());

        // 6 · Y los datos de la versión también se corrigen antes de publicar
        conToken(put("/api/v1/panel/plantillas-prueba/versiones/" + borradorId), tokenTalento, """
                {"enunciado":"El enunciado que de verdad se quería","modalidad":"CRONOMETRADA",
                 "duracionMinutos":100,"minutoCambioMin":30,"minutoCambioMax":50}""")
                .andExpect(status().isOk());
        conToken(post(base + "/publicacion"), tokenTalento, null).andExpect(status().isOk());
        conTokenGet(base, tokenTalento)
                .andExpect(jsonPath("$.version.enunciado").value("El enunciado que de verdad se quería"))
                .andExpect(jsonPath("$.version.duracionMinutos").value(100));

        // 7 · Publicada, la puerta se cierra: 409 y no hay forma de despublicar
        conToken(delete("/api/v1/panel/plantillas-prueba/entregables/" + d), tokenTalento, null)
                .andExpect(status().isConflict());
        conToken(put("/api/v1/panel/plantillas-prueba/versiones/" + borradorId), tokenTalento,
                "{\"enunciado\":\"Otra cosa\",\"modalidad\":\"CRONOMETRADA\",\"duracionMinutos\":90}")
                .andExpect(status().isConflict());
        // Y la que ya estaba publicada desde el principio, igual
        conToken(delete("/api/v1/panel/plantillas-prueba/entregables/" + entregableObligatorioId),
                tokenTalento, null).andExpect(status().isConflict());
    }

    @DisplayName("Una prueba puede orientar a quien la califica y llevar su enunciado en un archivo")
    @Test
    @Order(11)
    void laGuiaYElEnunciadoViajanConLaVersion() throws Exception {
        /*
         * Contra la base de verdad, que es donde vive lo que los dobles no pueden levantar:
         * la columna `guia_calificacion` de la V46 y su CHECK de longitud. Los unitarios
         * fijan las decisiones; esto comprueba que la columna existe, que el texto va y
         * vuelve entero, y que el enunciado subido queda enlazado en la versión.
         *
         * ⚠️ El archivo es el ENUNCIADO, no la prueba. Aquí se ve: se sube y la versión
         * sigue sin poder publicarse, porque no tiene ni preguntas ni rúbrica. Lo que exige
         * `publicarVersion` no cambió ni una línea.
         */
        long plantillaId = Long.parseLong(leer(conToken(post("/api/v1/panel/plantillas-prueba"),
                tokenTalento, "{\"nombre\":\"Prueba con guía de calificación\"}")
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString(), "id"));

        String guia = "En este rubro un cierre de caja que no cuadra al céntimo es un cero, "
                + "por muy bien explicado que esté el método.";
        long versionId = Long.parseLong(leer(conToken(
                post("/api/v1/panel/plantillas-prueba/" + plantillaId + "/versiones"), tokenTalento, """
                {"enunciado":"Cierra la caja del día","modalidad":"CRONOMETRADA",
                 "duracionMinutos":90,"guiaCalificacion":"%s"}""".formatted(guia))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString(), "id"));

        String base = "/api/v1/panel/plantillas-prueba/versiones/" + versionId;

        // Va y vuelve entera por la API, que es lo que el panel necesita para poder editarla.
        String vista = conTokenGet(base, tokenTalento)
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        assertThat(json.readTree(vista).get("version").get("guiaCalificacion").asText())
                .isEqualTo(guia);

        // Y el tope: dos mil y uno no entran. Sin este 400, la fila reventaría contra el
        // CHECK de la V46 con un mensaje que no nombra ningún campo.
        conToken(put(base), tokenTalento, """
                {"enunciado":"Cierra la caja del día","modalidad":"CRONOMETRADA",
                 "duracionMinutos":90,"guiaCalificacion":"%s"}""".formatted("a".repeat(2001)))
                .andExpect(status().isBadRequest());

        // El enunciado como archivo: PDF, y queda enlazado en la versión.
        MockMultipartFile enunciado = new MockMultipartFile(
                "archivo", "enunciado.pdf", "application/pdf", "El caso completo".getBytes());
        mvc.perform(multipart(base + "/consigna").file(enunciado)
                        .header("Authorization", "Bearer " + tokenTalento))
                .andExpect(status().isOk());

        assertThat(jdbc.queryForObject(
                "select url_consigna from version_plantilla_prueba where id = ?",
                String.class, versionId))
                .as("el enlace que después pega el correo PRUEBA_DISPONIBLE")
                .isNotBlank();

        // Un .txt no es un enunciado: PDF o Word, lo mismo que en el resto del sistema.
        mvc.perform(multipart(base + "/consigna")
                        .file(new MockMultipartFile("archivo", "enunciado.txt", "text/plain",
                                "x".getBytes()))
                        .header("Authorization", "Bearer " + tokenTalento))
                .andExpect(status().isBadRequest());

        // ⚠️ Y con el archivo subido la prueba SIGUE sin poder publicarse: de un PDF no sale
        // ninguna rúbrica. Quien crea que subiendo el enunciado ya tiene la prueba montada se
        // topa aquí con la realidad.
        conToken(post(base + "/publicacion"), tokenTalento, null)
                .andExpect(status().isBadRequest());

        // Publicada, el enunciado ya no se cambia: es parte del examen.
        agregarCriterio(versionId, "TODO_ES_UNO", 100);
        long pregunta = laPreguntaDelCatalogo("UNIV_P_0");
        elegir(base, pregunta);
        conToken(post(base + "/publicacion"), tokenTalento, null).andExpect(status().isOk());

        mvc.perform(multipart(base + "/consigna").file(enunciado)
                        .header("Authorization", "Bearer " + tokenTalento))
                .andExpect(status().isConflict());
    }

    // ============ Apoyo ============

    private long agregarEntregable(long versionId, String nombre) throws Exception {
        return Long.parseLong(leer(conToken(
                post("/api/v1/panel/plantillas-prueba/versiones/" + versionId + "/entregables"),
                tokenTalento, """
                {"nombre":"%s","detalle":"Lo que sea","formato":"ENLACE","esObligatorio":false}"""
                        .formatted(nombre))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString(), "id"));
    }

    private long agregarVariante(long versionId, String texto) throws Exception {
        return Long.parseLong(leer(conToken(
                post("/api/v1/panel/plantillas-prueba/versiones/" + versionId + "/variantes"),
                tokenTalento, "{\"texto\":\"%s\"}".formatted(texto))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString(), "id"));
    }

    private long agregarCriterio(long versionId, String codigo, int puntos) throws Exception {
        return Long.parseLong(leer(conToken(
                post("/api/v1/panel/plantillas-prueba/versiones/" + versionId + "/rubrica"),
                tokenTalento, """
                {"codigo":"%s","nombre":"Criterio %s","puntos":%d,"metodoVerificacion":"PERSONA"}"""
                        .formatted(codigo, codigo, puntos))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString(), "id"));
    }

    private void elegir(String base, long preguntaId) throws Exception {
        conToken(post(base + "/preguntas"), tokenTalento,
                "{\"preguntaPruebaId\": %d}".formatted(preguntaId)).andExpect(status().isOk());
    }

    private long laPreguntaDelCatalogo(String codigo) {
        return jdbc.queryForObject("select id from pregunta_prueba where codigo = ?", Long.class, codigo);
    }

    private List<String> nombresDeEntregables(long versionId) throws Exception {
        String cuerpo = conTokenGet("/api/v1/panel/plantillas-prueba/versiones/" + versionId,
                tokenTalento).andReturn().getResponse().getContentAsString();
        return json.readTree(cuerpo).get("entregables").findValuesAsText("nombre");
    }

    /** La nota de la etapa de la prueba, o null si todavía no hay ninguna. */
    private Double laNotaDeEtapa(long postulacionId) {
        return jdbc.queryForList(
                        "select puntaje from nota_etapa where postulacion_id = ? "
                                + "and etapa_codigo = 'PRUEBA_PUESTO'", Double.class, postulacionId)
                .stream().findFirst().orElse(null);
    }

    private int cuantasNotasDeCriterio(long postulacionId) {
        return jdbc.queryForObject("select count(*) from nota_criterio where postulacion_id = ?",
                Integer.class, postulacionId);
    }

    /**
     * Otra candidata en el punto exacto donde arranca el agente: la prueba entregada y la
     * postulación en {@code PRUEBA_CALIFICANDO}. El camino hasta ahí ya lo recorre la prueba
     * de más arriba, así que aquí se pone el punto de partida y no se vuelve a andar.
     */
    private long otraPostulacionEnCalificando(String correo) throws Exception {
        String token = crearCandidatoYEntrar(correo);
        MockMultipartFile cv = new MockMultipartFile("cv", "cv.pdf", "application/pdf", "x".getBytes());
        String codigo = leer(mvc.perform(multipart("/api/v1/portal/postulaciones")
                        .file(cv)
                        .param("vacanteId", String.valueOf(vacanteId))
                        .param("resultadoOrgulloso", "Un resultado del que me siento orgullosa")
                        .param("aceptaTratamiento", "true")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString(), "codigo");

        long id = jdbc.queryForObject("select id from postulacion where uuid = ?::uuid", Long.class, codigo);
        jdbc.update("update postulacion set estado_codigo = 'PRUEBA_CALIFICANDO' where id = ?", id);
        jdbc.update("""
                insert into intento_prueba (postulacion_id, version_plantilla_prueba_id, iniciado_en,
                                            vence_en, entregado_en)
                values (?, ?, now() - interval '3 hours', now() - interval '1 hour',
                        now() - interval '2 hours')""", id, versionPruebaId);
        return id;
    }

    private long prepararVacantePublicada() throws Exception {
        jdbc.update("INSERT INTO area (organizacion_id, nombre, es_activa) VALUES (1, 'Ingeniería', true)");
        Long areaId = jdbc.queryForObject("SELECT id FROM area ORDER BY id DESC LIMIT 1", Long.class);
        long puestoId = Long.parseLong(leer(conToken(post("/api/v1/panel/puestos"), tokenTalento, """
                {"codigo": "DEV_BUSCADOR", "nombre": "Desarrollador buscador",
                 "nivelPuestoCodigo": "EJECUCION", "familiaCodigo": "TECNOLOGIA"}""")
                .andReturn().getResponse().getContentAsString(), "id"));

        long solicitudId = Long.parseLong(leer(conToken(post("/api/v1/panel/solicitudes"), tokenTalento, """
                {"areaId": %d, "puestoId": %d, "urgencia": "NORMAL",
                 "nivelPuestoCodigo": "EJECUCION", "familiaCodigo": "TECNOLOGIA",
                 "resultadoPrincipal": "Sostener el desarrollo del buscador",
                 "motivo": "El equipo no llega a los plazos",
                 "consecuenciaNoContratar": "Se retrasa el lanzamiento",
                 "analisisCapacidad": "Se evaluó automatizar y no alcanza",
                 "responsableUsuarioId": 1,
                 "resultadosEsperados": [
                   {"descripcion": "Publicar el buscador", "indicador": "en producción"},
                   {"descripcion": "Reducir bugs", "indicador": "la mitad"},
                   {"descripcion": "Documentar", "indicador": "docs al día"}
                 ]}""".formatted(areaId, puestoId))
                .andReturn().getResponse().getContentAsString(), "id"));

        conToken(post("/api/v1/panel/solicitudes/" + solicitudId + "/aprobacion"), tokenTalento,
                "{\"motivo\":\"Hay presupuesto\"}").andExpect(status().isOk());

        long id = Long.parseLong(leer(conToken(post("/api/v1/panel/vacantes"), tokenTalento, """
                {"solicitudTalentoId": %d, "puestoId": %d,
                 "titulo": "Desarrollador buscador", "descripcion": "Buscador de recetas",
                 "tipoCierre": "PERMANENTE", "responsableUsuarioId": 1}"""
                .formatted(solicitudId, puestoId))
                .andReturn().getResponse().getContentAsString(), "id"));

        Long plantillaEvaluacionId = jdbc.queryForObject(
                "select id from plantilla_evaluacion where nivel_puesto_codigo = 'EJECUCION'", Long.class);
        conToken(post("/api/v1/panel/vacantes/" + id + "/plantilla-evaluacion"), tokenTalento,
                "{\"plantillaEvaluacionId\": %d}".formatted(plantillaEvaluacionId)).andExpect(status().isOk());

        conToken(post("/api/v1/panel/vacantes/" + id + "/plantilla-prueba"), tokenTalento,
                "{\"versionPlantillaPruebaId\": %d}".formatted(versionPruebaId)).andExpect(status().isOk());

        conToken(post("/api/v1/panel/vacantes/" + id + "/publicacion"), tokenTalento, null)
                .andExpect(status().isOk());
        return id;
    }

    private String crearCandidatoYEntrar() throws Exception {
        return crearCandidatoYEntrar("candidata-prueba@correo.pe");
    }

    private String crearCandidatoYEntrar(String correo) throws Exception {
        mvc.perform(post("/api/v1/portal/cuentas")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                        {"nombre":"Valeria","apellidos":"Torres","correo":"%s",
                         "contrasena":"unaClaveLarga123","ciudadUbigeo":"1501","aceptaProceso":true,
                         "aceptaFuturosContactos":false}""".formatted(correo)))
                .andExpect(status().isCreated());
        return leer(mvc.perform(post("/api/v1/portal/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"correo\":\"%s\",\"contrasena\":\"unaClaveLarga123\"}".formatted(correo)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(), "token");
    }

    // Un responsable de área, asignado como responsable de la vacante que ya existe.
    private String crearResponsableDeArea(long vacanteId) throws Exception {
        String token = crearUsuarioConUnSoloRol("Marco", "Quispe", "marco.area@renaser.pe",
                "os-marco-area", "RESPONSABLE_AREA");
        long usuarioId = jdbc.queryForObject(
                "select id from usuario where usuario_renaser_os_id = ?", Long.class, "os-marco-area");
        jdbc.update("update vacante set responsable_usuario_id = ? where id = ?", usuarioId, vacanteId);
        return token;
    }

    // Un usuario con exactamente un rol, distinto del usuario de bootstrap del primer
    // dev-login -ese tiene TALENTO, DIRECCION y ADMINISTRADOR a la vez y no sirve para
    // probar qué permisos excluye cada rol.
    private String crearUsuarioConUnSoloRol(String nombre, String apellidos, String correo,
                                            String usuarioRenaserOsId, String rol) throws Exception {
        conToken(post("/api/v1/panel/usuarios"), tokenTalento, """
                {"nombre": "%s", "apellidos": "%s", "correo": "%s",
                 "usuarioRenaserOsId": "%s", "roles": ["%s"]}"""
                .formatted(nombre, apellidos, correo, usuarioRenaserOsId, rol))
                .andExpect(status().isCreated());
        return leer(mvc.perform(post("/api/v1/panel/auth/dev-login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"usuarioRenaserOsId\":\"%s\"}".formatted(usuarioRenaserOsId)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(), "token");
    }

    private ResultActions conToken(
            org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder peticion,
            String token, String cuerpo) throws Exception {
        peticion.header("Authorization", "Bearer " + token).contentType(MediaType.APPLICATION_JSON);
        if (cuerpo != null) peticion.content(cuerpo);
        return mvc.perform(peticion);
    }

    private ResultActions conTokenGet(String ruta, String token) throws Exception {
        return mvc.perform(get(ruta).header("Authorization", "Bearer " + token));
    }

    private String leer(String cuerpoRespuesta, String campo) throws Exception {
        return json.readTree(cuerpoRespuesta).get(campo).asText();
    }
}
