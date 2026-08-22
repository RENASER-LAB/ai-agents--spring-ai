package com.renaser.ai.ai_engine.integracion;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.renaser.ai.ai_engine.integracion.soporte.ImagenesDeContenedores;
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
 * La evaluación del candidato, de punta a punta.
 *
 * <p>Esto es lo que hasta ahora no existía: quien postulaba quedaba en
 * {@code PERFIL_TURNO_CANDIDATO} esperando algo que no tenía forma de hacer. Aquí se
 * comprueba el camino entero — postular, ver su examen, responderlo, entregarlo — y las
 * cuatro reglas que lo sostienen:
 *
 * <ul>
 *   <li>La clave nunca viaja al portal (RF-53).
 *   <li>Cada candidato ve sus preguntas en su propio orden, y ese orden se guarda (RF-51).
 *   <li>Una respuesta se guarda al momento y se puede retomar (RF-52).
 *   <li>No se entrega a medias.
 * </ul>
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@Testcontainers
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@DisplayName("Hito 2 · La evaluación del candidato")
public class FlujoEvaluacionIT {

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
        registro.add("spring.ai.deepseek.api-key", () -> "clave-de-pruebas-no-se-usa");
        // La calificacion con IA se apaga en estas pruebas: aqui no se prueba, y si estuviera
        // encendida cada entrega intentaria hablar con DeepSeek con una clave de mentira.
        // Quien la prueba de verdad es FlujoCalificacionIaIT, con el modelo sustituido.
        registro.add("renaser.ai.calificacion.habilitada", () -> "false");
    }

    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    final ObjectMapper json = new ObjectMapper();

    static String tokenEquipo;
    static String tokenCandidato;
    static String codigoPostulacion;
    static long vacanteId;

    @DisplayName("Hay un banco de preguntas de verdad")
    @Test
    @Order(1)
    void hayUnBancoDePreguntasDeVerdad() throws Exception {
        // Los 190 ítems del Banco RENASER v3, no un banco vacío: son 85 del directivo, 55 del
        // de coordinación y 50 del operativo, y esos tres números los declara el propio
        // documento del cliente.
        //
        // Se cuenta sobre los bancos PUBLICADA, no sobre la tabla entera. La V20 ya no borra
        // el v0.1 —lo archiva, para no arrancarle su banco a quien ya fue evaluado con él
        // (RF-138)—, así que sus 200 preguntas siguen en la tabla y contarlas todas daría 390.
        // Lo que importa aquí es qué se le puede poner delante a un candidato de hoy.
        assertThat(jdbc.queryForObject("""
                select count(*) from pregunta p
                  join version_banco vb on vb.id = p.version_banco_id
                 where vb.estado = 'PUBLICADA'""", Integer.class)).isEqualTo(190);
        assertThat(jdbc.queryForObject("select count(*) from plantilla_evaluacion", Integer.class)).isEqualTo(3);

        // Lo que no debe sumar, no suma. En el v3 son los pares de consistencia y los ítems
        // puramente eliminatorios: llevan peso 0 y no pueden aportar nota (RF-54).
        //
        // Igual que arriba, solo sobre lo PUBLICADA: la columna `peso` la estrena la V20 con
        // el motor de puntuación del v3, así que las preguntas del v0.1 archivado la tienen
        // nula. Es correcto que sea así —ese banco se calificaba de otra forma— y exigirles
        // el peso del v3 sería pedirle a un instrumento retirado que cumpla reglas que nunca
        // tuvo.
        assertThat(jdbc.queryForObject("""
                select count(*) from pregunta p
                  join version_banco vb on vb.id = p.version_banco_id
                 where vb.estado = 'PUBLICADA' and p.peso = 0 and p.es_puntuable""",
                Integer.class)).isZero();
        // Y al revés: todo lo que puntúa tiene un peso de verdad
        assertThat(jdbc.queryForObject("""
                select count(*) from pregunta p
                  join version_banco vb on vb.id = p.version_banco_id
                 where vb.estado = 'PUBLICADA' and p.es_puntuable
                   and (p.peso is null or p.peso = 0)""",
                Integer.class)).isZero();

        // Y los pesos suman 100 por nivel DENTRO DE CADA VERSION, que es lo que exige publicar
        // una version. Sin agrupar tambien por version esto sumaba todas las versiones juntas
        // y daba 100 solo mientras la v2 fuese la unica con pesos de dimension: en cuanto la
        // v3 y la v4 los tuvieron, dio 200 y luego 300 sin que nada estuviera mal.
        jdbc.queryForList("""
                select version_pesos_id, nivel_puesto_codigo, sum(peso) suma from peso_dimension
                group by 1, 2""").forEach(fila ->
                assertThat(((Number) fila.get("suma")).doubleValue()).isEqualTo(100.0));
    }

    @DisplayName("El candidato recibe su evaluación al postular")
    @Test
    @Order(2)
    void elCandidatoRecibeSuEvaluacionAlPostular() throws Exception {
        tokenEquipo = leer(mvc.perform(post("/api/v1/panel/auth/dev-login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"usuarioRenaserOsId\":\"dev-1\"}"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(), "token");

        vacanteId = prepararVacantePublicada();

        tokenCandidato = crearCandidatoYEntrar();

        MockMultipartFile cv = new MockMultipartFile("cv", "cv.pdf",
                "application/pdf", "contenido de prueba".getBytes());
        codigoPostulacion = leer(mvc.perform(multipart("/api/v1/portal/postulaciones")
                        .file(cv)
                        .param("vacanteId", String.valueOf(vacanteId))
                        .param("resultadoOrgulloso", "Automaticé el cierre mensual y pasó de 3 días a 4 horas")
                        .header("Authorization", "Bearer " + tokenCandidato))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString(), "codigo");

        // Ya no queda en un callejón sin salida: tiene evaluación esperándole
        mvc.perform(get("/api/v1/portal/postulaciones")
                        .header("Authorization", "Bearer " + tokenCandidato))
                .andExpect(jsonPath("$[0].estado").value("PERFIL_TURNO_CANDIDATO"));

        conTokenGet("/api/v1/portal/evaluacion/" + codigoPostulacion, tokenCandidato)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("PENDIENTE"))
                .andExpect(jsonPath("$.total").value(0));   // todavía sin empezar
    }

    @DisplayName("Al empezar se le arma su examen, y la clave de respuestas no viaja")
    @Test
    @Order(3)
    void alEmpezarSeLeArmaSuExamenYLaClaveNoViaja() throws Exception {
        String cuerpo = mvc.perform(post("/api/v1/portal/evaluacion/" + codigoPostulacion + "/inicio")
                        .header("Authorization", "Bearer " + tokenCandidato))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("EN_CURSO"))
                .andReturn().getResponse().getContentAsString();

        JsonNode evaluacion = json.readTree(cuerpo);
        int total = evaluacion.get("total").asInt();

        // ANTES: la plantilla de Ejecución pedía entre 20 y 27 preguntas, la suma de sus cuotas.
        // AHORA el banco v3 se aplica entero —son sus 50 ítems los que forman el examen—, porque
        // el máximo de 168 que declara el documento solo se alcanza respondiéndolos todos.
        // Comentario viejo, conservado para entender el cambio: pedía entre 20 y 27 preguntas, no
        // las 50 del banco: el banco es un repositorio, no el examen (RF-47)
        assertThat(total).isEqualTo(50);

        // RF-53: ni puntajes, ni lógica interna, ni códigos de dimensión. Se comprueba sobre
        // el JSON crudo porque es exactamente lo que llegaría al navegador.
        assertThat(cuerpo).doesNotContain("puntaje", "logicaInterna", "esPuntuable", "dimension");
        // El v3 añadió más clave a la opción, y ninguna puede llegar al candidato: "valor" es
        // la puntuación oculta de los EF-4, "esDistractor" delataría qué elementos de un
        // inventario están inventados, y "ordenCorrecto" es la respuesta de los ordenamientos.
        assertThat(cuerpo).doesNotContain("valor", "esDistractor", "ordenCorrecto");

        // Y en particular ninguna de las 22 dimensiones se cuela por un campo de texto. Las
        // de estilo traían su tradeoff ("VEL vs CRI") en un campo que sí viajaba: es
        // exactamente el nombre interno que el candidato no debe ver.
        for (String dimension : List.of("VEL vs", "CRI vs", "CTL vs", "INT+", "OWN+", "→VEL", "→CRI")) {
            assertThat(cuerpo).doesNotContain(dimension);
        }

        // Cada pregunta llega con su posición y, si es de opciones, con sus letras
        JsonNode primera = evaluacion.get("preguntas").get(0);
        assertThat(primera.get("posicion").asInt()).isEqualTo(1);
        assertThat(primera.get("enunciado").asText()).isNotBlank();

        // El orden quedó guardado: es lo que permite reproducir el examen tal como lo vio
        Long evaluacionId = evaluacion.get("id").asLong();
        assertThat(jdbc.queryForObject(
                "select count(*) from orden_pregunta where evaluacion_id = ?", Integer.class, evaluacionId))
                .isEqualTo(total);

        // Volver a entrar no le rehace el examen: sigue siendo el suyo
        mvc.perform(post("/api/v1/portal/evaluacion/" + codigoPostulacion + "/inicio")
                        .header("Authorization", "Bearer " + tokenCandidato))
                .andExpect(jsonPath("$.total").value(total));
    }

    @DisplayName("Responde, y no puede entregar a medias")
    @Test
    @Order(4)
    void respondeYNoPuedeEntregarAMedias() throws Exception {
        JsonNode evaluacion = json.readTree(
                conTokenGet("/api/v1/portal/evaluacion/" + codigoPostulacion, tokenCandidato)
                        .andReturn().getResponse().getContentAsString());
        JsonNode preguntas = evaluacion.get("preguntas");
        int total = evaluacion.get("total").asInt();

        // Responde la primera, y se guarda al momento (RF-52)
        long primeraId = preguntas.get(0).get("id").asLong();
        responder(primeraId, preguntas.get(0));
        conTokenGet("/api/v1/portal/evaluacion/" + codigoPostulacion, tokenCandidato)
                .andExpect(jsonPath("$.respondidas").value(1));

        // Con una sola respondida no puede entregar
        mvc.perform(post("/api/v1/portal/evaluacion/" + codigoPostulacion + "/entrega")
                        .header("Authorization", "Bearer " + tokenCandidato))
                .andExpect(status().isBadRequest());

        // Una pregunta que no le tocó no se puede responder aunque exista en el banco
        Long ajena = jdbc.queryForObject("""
                select p.id from pregunta p
                where p.id not in (select pregunta_id from orden_pregunta) limit 1""", Long.class);
        mvc.perform(put("/api/v1/portal/evaluacion/" + codigoPostulacion + "/respuestas/" + ajena)
                        .header("Authorization", "Bearer " + tokenCandidato)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"texto\":\"intento\"}"))
                .andExpect(status().isNotFound());

        // Responde el resto y ahora sí entrega
        for (int i = 1; i < preguntas.size(); i++) {
            responder(preguntas.get(i).get("id").asLong(), preguntas.get(i));
        }
        mvc.perform(post("/api/v1/portal/evaluacion/" + codigoPostulacion + "/entrega")
                        .header("Authorization", "Bearer " + tokenCandidato))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("TERMINADA"))
                .andExpect(jsonPath("$.respondidas").value(total));

        // Y la postulación pasó sola a calificarse: transición del sistema, sin motivo escrito
        mvc.perform(get("/api/v1/portal/postulaciones")
                        .header("Authorization", "Bearer " + tokenCandidato))
                .andExpect(jsonPath("$[0].estado").value("PERFIL_CALIFICANDO"));

        // Entregada, ya no se toca
        mvc.perform(post("/api/v1/portal/evaluacion/" + codigoPostulacion + "/entrega")
                        .header("Authorization", "Bearer " + tokenCandidato))
                .andExpect(status().isConflict());

        // Y lo cerrado quedó puntuado sin que interviniera nadie ni ninguna IA: es
        // aritmética contra la clave versionada (RF-147). La nota va atada a la versión de
        // pesos con la que se calculó, que es lo que impide que cambie sola después.
        Map<String, Object> nota = jdbc.queryForMap("""
                select puntaje, version_pesos_id from nota_etapa
                where etapa_codigo = 'PERFIL_INTEGRAL'""");
        // Estrictamente mayor que cero: respondió con opciones reales y algunas puntúan. Si
        // saliera 0 sería señal de que la clave no se está leyendo.
        assertThat(((Number) nota.get("puntaje")).doubleValue()).isBetween(0.01, 100.0);
        assertThat(nota.get("version_pesos_id")).isNotNull();
    }

    @DisplayName("La evaluación de otro candidato no se ve")
    @Test
    @Order(5)
    void laEvaluacionDeOtroNoSeVe() throws Exception {
        // Otro candidato, con su propia cuenta, pidiendo el código ajeno. Es 404 y no 403:
        // un 403 ya confirmaría que esa postulación existe.
        String tokenAjeno = crearCandidatoYEntrar("otro@correo.pe");
        conTokenGet("/api/v1/portal/evaluacion/" + codigoPostulacion, tokenAjeno)
                .andExpect(status().isNotFound());
    }

    // ============ Apoyo ============

    private void responder(long preguntaId, JsonNode pregunta) throws Exception {
        // Cada formato del banco v3 tiene su propia forma de respuesta —un SJT-R califica cada
        // opción, un EF-4 marca dos, un SEC las ordena— y el validador rechaza las demás.
        String cuerpo = RespuestaV3.para(pregunta);
        mvc.perform(put("/api/v1/portal/evaluacion/" + codigoPostulacion + "/respuestas/" + preguntaId)
                        .header("Authorization", "Bearer " + tokenCandidato)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(cuerpo))
                .andExpect(status().isOk());
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

    private String crearCandidatoYEntrar() throws Exception {
        return crearCandidatoYEntrar("camila@correo.pe");
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

    // La mínima prueba del puesto publicable: 8 universales + 3 específicas (RF-83), y una
    // rúbrica de un solo criterio que ya suma 100. La prueba a fondo la hace FlujoPruebaIT.
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
            String codigo = "UNIV_EV_" + i;
            long id = Long.parseLong(leer(conToken(post("/api/v1/panel/plantillas-prueba/preguntas"), token,
                    "{\"codigo\":\"%s\",\"enunciado\":\"Pregunta universal %d\",\"tipo\":\"UNIVERSAL\"}"
                            .formatted(codigo, i))
                    .andReturn().getResponse().getContentAsString(), "id"));
            conToken(post("/api/v1/panel/plantillas-prueba/versiones/" + versionId + "/preguntas"), token,
                    "{\"preguntaPruebaId\": %d}".formatted(id)).andExpect(status().isOk());
        }
        for (int i = 0; i < 3; i++) {
            String codigo = "ESP_EV_" + i;
            long id = Long.parseLong(leer(conToken(post("/api/v1/panel/plantillas-prueba/preguntas"), token,
                    "{\"codigo\":\"%s\",\"enunciado\":\"Pregunta específica %d\",\"tipo\":\"ESPECIFICA\"}"
                            .formatted(codigo, i))
                    .andReturn().getResponse().getContentAsString(), "id"));
            conToken(post("/api/v1/panel/plantillas-prueba/versiones/" + versionId + "/preguntas"), token,
                    "{\"preguntaPruebaId\": %d}".formatted(id)).andExpect(status().isOk());
        }
        conToken(post("/api/v1/panel/plantillas-prueba/versiones/" + versionId + "/rubrica"), token, """
                {"codigo":"RESULTADO_EV","nombre":"Resultado","puntos":100,"metodoVerificacion":"PERSONA"}""")
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

    private ResultActions conTokenGet(String ruta, String token) throws Exception {
        return mvc.perform(get(ruta).header("Authorization", "Bearer " + token));
    }

    private String leer(String cuerpoRespuesta, String campo) throws Exception {
        return json.readTree(cuerpoRespuesta).get(campo).asText();
    }
}
