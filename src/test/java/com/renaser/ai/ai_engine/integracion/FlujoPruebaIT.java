package com.renaser.ai.ai_engine.integracion;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.renaser.ai.ai_engine.comun.programado.SondeoVencimientos;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.DisplayName;
import com.renaser.ai.ai_engine.integracion.soporte.RespuestaV3;
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
        // La calificacion con IA se apaga en estas pruebas: aqui no se prueba, y si estuviera
        // encendida cada entrega intentaria hablar con DeepSeek con una clave de mentira.
        // Quien la prueba de verdad es FlujoCalificacionIaIT, con el modelo sustituido.
        registro.add("renaser.ai.calificacion.habilitada", () -> "false");
    }

    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired SondeoVencimientos sondeo;
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

        // Una plantilla de 45 minutos no pasa: RF-76 exige entre 60 y 120
        long versionCorta = Long.parseLong(leer(conToken(
                post("/api/v1/panel/plantillas-prueba/" + plantillaId + "/versiones"), tokenTalento, """
                {"enunciado":"x","modalidad":"CRONOMETRADA","duracionMinutos":45}""")
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString(), "id"));
        conToken(post("/api/v1/panel/plantillas-prueba/versiones/" + versionCorta + "/publicacion"),
                tokenTalento, null).andExpect(status().isBadRequest());

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
    }

    @DisplayName("El candidato rinde la prueba con su cronómetro")
    @Test
    @Order(3)
    void elCandidatoRindeLaPruebaConSuCronometro() throws Exception {
        JsonNode prueba = json.readTree(mvc.perform(post("/api/v1/portal/prueba/" + codigoPostulacion + "/inicio")
                        .header("Authorization", "Bearer " + tokenCandidato))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estadoIntento").value("EN_CURSO"))
                .andExpect(jsonPath("$.duracionMinutos").value(90))
                .andReturn().getResponse().getContentAsString());

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

    @DisplayName("El recorrido sigue sin saltos manuales")
    @Test
    @Order(4)
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
    @Order(5)
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
    @Order(6)
    void seSaltaLoQueNoAplicaYSeDecide() throws Exception {
        // Antes de llegar a la decisión no se decide. Hasta el 22/08/2026 bastaba el permiso:
        // se podía contratar a quien todavía estaba en la prueba del puesto.
        conToken(post("/api/v1/panel/postulaciones/" + postulacionId + "/decision"), tokenTalento,
                "{\"semaforo\":\"VERDE\",\"motivo\":\"Me adelanto a la etapa\"}")
                .andExpect(status().isConflict());

        // Mover la ficha nunca contrata. Con `mover_postulacion` se llegaba a CONTRATADO desde
        // cualquier etapa, saltándose de un golpe la decisión entera y todo lo que comprueba.
        String saltoACONTRATADO = conToken(
                        post("/api/v1/panel/postulaciones/" + postulacionId + "/transiciones"), tokenTalento,
                        "{\"estadoDestino\":\"CONTRATADO\",\"motivo\":\"Me lo llevo ya\"}")
                .andExpect(status().isConflict())
                .andReturn().getResponse().getContentAsString();
        assertThat(saltoACONTRATADO).contains("/decision");

        // Simulación y validación sí se pueden saltar cuando el puesto no las necesita: es una
        // transición manual con motivo, que RF-121 permite para cualquier salto. Saltar hacia
        // adelante sigue siendo legítimo; lo que se cerró es la puerta trasera a contratar.
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

        // Contratar con etapas sin nota ya no pasa en silencio. El mensaje nombra cuáles, que
        // es la diferencia entre poder arreglarlo y tener que adivinar.
        List<String> faltan = new java.util.ArrayList<>();
        semaforo.get("etapasQueFaltan").forEach(e -> faltan.add(e.asText()));
        String rechazo = conToken(post("/api/v1/panel/postulaciones/" + postulacionId + "/decision"), tokenArea,
                        "{\"semaforo\":\"VERDE\",\"motivo\":\"Prueba sólida y Perfil Integral consistente\"}")
                .andExpect(status().isConflict())
                .andReturn().getResponse().getContentAsString();
        assertThat(rechazo).contains(faltan.toArray(new String[0]));

        // Y una barrera crítica confirmada no la levanta ni reconociendo lo que falta:
        // "ningún promedio alto la tapa" (RF-115). Manda sobre todo lo demás.
        long barreraId = json.readTree(
                conToken(post("/api/v1/panel/vacantes/" + vacanteId + "/barreras-criticas"), tokenTalento,
                                "{\"descripcion\":\"No puede viajar y el puesto lo exige\"}")
                        .andExpect(status().isCreated())
                        .andReturn().getResponse().getContentAsString()).get("id").asLong();
        long detectadaId = json.readTree(
                conToken(post("/api/v1/panel/postulaciones/" + postulacionId + "/barreras-detectadas"), tokenTalento,
                                "{\"barreraCriticaId\":" + barreraId + ",\"explicacion\":\"Lo dijo en la entrevista\"}")
                        .andExpect(status().isCreated())
                        .andReturn().getResponse().getContentAsString()).get("id").asLong();

        String porLaBarrera = conToken(post("/api/v1/panel/postulaciones/" + postulacionId + "/decision"), tokenArea,
                        "{\"semaforo\":\"VERDE\",\"motivo\":\"Aun así lo quiero\",\"aunqueFaltenEtapas\":true}")
                .andExpect(status().isConflict())
                .andReturn().getResponse().getContentAsString();
        assertThat(porLaBarrera).contains("barrera");

        // Se puso por error y se deshace: sin motivo no se puede, y descartar dos veces tampoco.
        String descarte = "/api/v1/panel/postulaciones/" + postulacionId
                + "/barreras-detectadas/" + detectadaId + "/descarte";
        conToken(post(descarte), tokenTalento, "{\"motivo\":\"\"}")
                .andExpect(status().isBadRequest());
        conToken(post(descarte), tokenTalento,
                "{\"motivo\":\"Se confundió de candidato: quien no puede viajar es otro\"}")
                .andExpect(status().isOk());
        conToken(post(descarte), tokenTalento, "{\"motivo\":\"Otra vez\"}")
                .andExpect(status().isConflict());

        // Y queda quién lo hizo y por qué: una barrera que desaparece sin firma es
        // indistinguible de una que nunca existió.
        Map<String, Object> descartada = jdbc.queryForMap(
                "select descartada_por_usuario_id, motivo_descarte from barrera_detectada where id = ?",
                detectadaId);
        assertThat(descartada.get("descartada_por_usuario_id")).isNotNull();
        assertThat((String) descartada.get("motivo_descarte")).contains("Se confundió de candidato");

        // Ahora sí: reconociendo que faltan notas, se contrata.
        conToken(post("/api/v1/panel/postulaciones/" + postulacionId + "/decision"), tokenArea,
                "{\"semaforo\":\"VERDE\",\"motivo\":\"Prueba sólida y Perfil Integral consistente\",\"aunqueFaltenEtapas\":true}")
                .andExpect(status().isOk());

        conTokenGet("/api/v1/portal/postulaciones", tokenCandidato)
                .andExpect(jsonPath("$[0].estado").value("CONTRATADO"));

        Map<String, Object> decision = jdbc.queryForMap(
                "select semaforo, decidida_por_usuario_id from decision where postulacion_id = ?", postulacionId);
        assertThat(decision.get("semaforo")).isEqualTo("VERDE");
        assertThat(decision.get("decidida_por_usuario_id")).isNotNull();

        // Y el registro guarda las dos cosas: lo que propuso el servidor y qué faltaba. Sin
        // eso, dentro de seis meses nadie puede responder por qué se contrató así.
        String registro = jdbc.queryForObject(
                "select valor_nuevo::text from auditoria where accion = 'decidir_postulacion' order by id desc limit 1",
                String.class);
        assertThat(registro).contains("propuestaDelServidor", "etapasSinNota");

        // Al contratado también se le avisa. Era el único candidato que no recibía nada: no
        // es NO_CONTINUA, no es CERRADA, y no espera nada de él, así que se quedaba fuera de
        // los tres casos que generan correo.
        Long usuarioContratado = jdbc.queryForObject(
                "select usuario_id from postulacion where id = ?", Long.class, postulacionId);
        Integer avisos = jdbc.queryForObject("""
                select count(*) from correo_enviado
                 where usuario_id = ? and plantilla_correo_codigo = 'POSTULACION_CONTRATADA'
                """, Integer.class, usuarioContratado);
        assertThat(avisos).as("al contratado se le avisa una vez, ni cero ni dos").isEqualTo(1);

        // Y el cuerpo lleva su nombre y la vacante resueltos, no los marcadores en crudo:
        // una plantilla sin sustituir se envía igual y nadie se entera hasta que la lee él.
        String cuerpo = jdbc.queryForObject("""
                select cuerpo from correo_enviado
                 where usuario_id = ? and plantilla_correo_codigo = 'POSTULACION_CONTRATADA'
                """, String.class, usuarioContratado);
        assertThat(cuerpo).doesNotContain("{{").contains("Renaser");
    }

    @DisplayName("Una dirección que no existe es un 404, no una avería")
    @Test
    @Order(8)
    void unaDireccionQueNoExisteEsUn404() throws Exception {
        // El catch-all de GlobalControllerAdvice se quedaba con NoResourceFoundException y
        // devolvía «Ha ocurrido un error inesperado» con 500. ManejadorErrores ya lo hacía
        // bien, pero está acotado a los controladores del portal y del panel: una ruta sin
        // controlador no cae en ninguno y llegaba al catch-all.
        //
        // Con token, porque sin él la cadena responde 401 antes de llegar al enrutado — y eso
        // también es lo correcto: a quien no se ha identificado no se le dice qué rutas hay.
        String cuerpo = conTokenGet("/api/v1/panel/esta-ruta-no-existe", tokenTalento)
                .andExpect(status().isNotFound())
                .andReturn().getResponse().getContentAsString();
        assertThat(cuerpo).contains("ruta-inexistente");
        assertThat(cuerpo).doesNotContain("error inesperado");
    }

    @DisplayName("Un intento vencido se entrega solo")
    @Test
    @Order(7)
    void unIntentoVencidoSeEntregaSolo() throws Exception {
        // Otra postulación, esta vez dejada vencer: el sondeo debe cerrarla sin que nadie actúe.
        String correo = "vence@correo.pe";
        String token = crearCandidatoYEntrar(correo);
        MockMultipartFile cv = new MockMultipartFile("cv", "cv.pdf", "application/pdf", "x".getBytes());
        String codigo = leer(mvc.perform(multipart("/api/v1/portal/postulaciones")
                        .file(cv)
                        .param("vacanteId", String.valueOf(vacanteId))
                        .param("resultadoOrgulloso", "Otro resultado del que me siento orgullosa")
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

    // ============ Apoyo ============

    private long prepararVacantePublicada() throws Exception {
        jdbc.update("INSERT INTO area (organizacion_id, nombre, es_activa) VALUES (1, 'Ingeniería', true)");
        Long areaId = jdbc.queryForObject("SELECT id FROM area ORDER BY id DESC LIMIT 1", Long.class);

        long solicitudId = Long.parseLong(leer(conToken(post("/api/v1/panel/solicitudes"), tokenTalento, """
                {"areaId": %d, "urgencia": "NORMAL",
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
                 ]}""".formatted(areaId))
                .andReturn().getResponse().getContentAsString(), "id"));

        conToken(post("/api/v1/panel/solicitudes/" + solicitudId + "/aprobacion"), tokenTalento,
                "{\"motivo\":\"Hay presupuesto\"}").andExpect(status().isOk());

        long puestoId = Long.parseLong(leer(conToken(post("/api/v1/panel/puestos"), tokenTalento, """
                {"codigo": "DEV_BUSCADOR", "nombre": "Desarrollador buscador",
                 "nivelPuestoCodigo": "EJECUCION", "familiaCodigo": "TECNOLOGIA"}""")
                .andReturn().getResponse().getContentAsString(), "id"));

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
                         "contrasena":"unaClaveLarga123","aceptaProceso":true,
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
