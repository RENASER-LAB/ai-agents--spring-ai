package com.renaser.ai.ai_engine.integracion;

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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * El banco de preguntas se cambia entero desde el panel, sin migración.
 *
 * <p>Esto es lo que el 19/08 no existía: el banco v3 solo pudo entrar con una migración
 * (V20), y cambiarlo otra vez habría exigido otra. Aquí un administrador construye un banco
 * nuevo por la API —una pregunta de cada formato v3, con sus claves, tramos, campos y su par
 * de consistencia—, lo publica y el sistema hace el relevo completo:
 *
 * <ul>
 *   <li>Publicar valida la coherencia de cada formato y rechaza con el código del ítem que falla.
 *   <li>Publicar archiva a la versión que reemplaza: una sola PUBLICADA por nivel.
 *   <li>Quien tenía una evaluación sin empezar pasa al banco nuevo sin notarlo (RF-138).
 *   <li>Una versión publicada no admite más opciones: la clave no se altera por debajo
 *       de un examen (la regresión del candado que faltaba).
 * </ul>
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@Testcontainers
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@DisplayName("El banco de preguntas se administra desde el panel")
public class FlujoBancoPreguntasIT {

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
        // El almacen de las pruebas vive en un mapa, no en disco.
        registro.add("app.archivos.tipo", () -> "memoria");
        registro.add("app.seguridad.jwt-secreto",
                () -> "clave-de-pruebas-suficientemente-larga-para-hmac-256-bits");
        // El dev-login quedo apagado por defecto en application.yaml: aqui se enciende
        // explicitamente, porque estas pruebas entran al panel por el.
        registro.add("app.seguridad.dev-login-activo", () -> "true");
        registro.add("spring.ai.deepseek.api-key", () -> "clave-de-pruebas-no-se-usa");
        registro.add("renaser.ai.calificacion.habilitada", () -> "false");
    }

    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    final ObjectMapper json = new ObjectMapper();

    static String tokenEquipo;
    static String tokenCandidato;
    static String codigoPostulacion;
    static long versionV4;
    static long versionV5;
    static long preguntaIncompleta;

    @DisplayName("El administrador construye un banco con los ocho formatos v3, por la API")
    @Test
    @Order(1)
    void elAdminConstruyeUnBancoV3PorLaApi() throws Exception {
        tokenEquipo = leer(mvc.perform(post("/api/v1/panel/auth/dev-login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"usuarioRenaserOsId\":\"dev-1\"}"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(), "token");

        versionV4 = crearVersion("Banco de práctica v4 · Ejecutivo");

        // EF-4 · elección forzada: cuatro afirmaciones, cada una con su valor oculto
        long ef4 = crearPregunta(versionV4, """
                {"codigo":"X01","bloque":"A1","tipo":"EF-4","enunciado":"¿Cuál se te parece más y cuál menos?",
                 "logicaInterna":"mide autonomía","esPuntuable":true,"orden":1,"peso":2,"esClave":true}""");
        opcion(ef4, "{\"letra\":\"a\",\"texto\":\"Decido y aviso\",\"valor\":2}");
        opcion(ef4, "{\"letra\":\"b\",\"texto\":\"Consulto antes\",\"valor\":1}");
        opcion(ef4, "{\"letra\":\"c\",\"texto\":\"Espero instrucciones\",\"valor\":-1}");
        opcion(ef4, "{\"letra\":\"d\",\"texto\":\"Prefiero no involucrarme\",\"valor\":-2}");

        // SJT-R · situacional: cada opción se califica del 1 al 5
        long sjt = crearPregunta(versionV4, """
                {"codigo":"X02","bloque":"A1","tipo":"SJT-R","enunciado":"Un cliente reclama a gritos...",
                 "esPuntuable":true,"orden":2,"peso":1}""");
        opcion(sjt, "{\"letra\":\"a\",\"texto\":\"Escucho y bajo el tono\",\"puntaje\":5}");
        opcion(sjt, "{\"letra\":\"b\",\"texto\":\"Lo derivo a mi jefe\",\"puntaje\":3}");
        opcion(sjt, "{\"letra\":\"c\",\"texto\":\"Le respondo igual\",\"puntaje\":1}");

        // SEC · ordenamiento: tres pasos con su lugar correcto
        long sec = crearPregunta(versionV4, """
                {"codigo":"X03","bloque":"A2","tipo":"SEC","enunciado":"Ordena los pasos del cierre",
                 "esPuntuable":true,"orden":3,"peso":1}""");
        opcion(sec, "{\"letra\":\"1\",\"texto\":\"Conciliar\",\"ordenCorrecto\":2}");
        opcion(sec, "{\"letra\":\"2\",\"texto\":\"Registrar\",\"ordenCorrecto\":1}");
        opcion(sec, "{\"letra\":\"3\",\"texto\":\"Reportar\",\"ordenCorrecto\":3}");

        // INV · inventario: dos reales y un distractor que el candidato no distingue
        long inv = crearPregunta(versionV4, """
                {"codigo":"X04","bloque":"A2","tipo":"INV","enunciado":"Marca las normas que conoces",
                 "esPuntuable":true,"orden":4,"peso":1}""");
        opcion(inv, "{\"letra\":\"a\",\"texto\":\"Norma real 1\"}");
        opcion(inv, "{\"letra\":\"b\",\"texto\":\"Norma real 2\"}");
        opcion(inv, "{\"letra\":\"c\",\"texto\":\"Norma inventada\",\"esDistractor\":true}");

        // DE · detección de error
        long de = crearPregunta(versionV4, """
                {"codigo":"X05","bloque":"A3","tipo":"DE","enunciado":"Señala las afirmaciones falsas",
                 "esPuntuable":true,"orden":5,"peso":1}""");
        opcion(de, "{\"letra\":\"a\",\"texto\":\"Afirmación cierta\"}");
        opcion(de, "{\"letra\":\"b\",\"texto\":\"Afirmación falsa\",\"esDistractor\":true}");

        // CD · caso descompuesto: el denominador y sus campos
        long cd = crearPregunta(versionV4, """
                {"codigo":"X06","bloque":"A3","tipo":"CD","enunciado":"Descompón el caso",
                 "esPuntuable":true,"orden":6,"peso":1,"casosPedidos":3}""");
        mvc.perform(post("/api/v1/panel/banco-preguntas/preguntas/" + cd + "/campos-caso")
                        .header("Authorization", "Bearer " + tokenEquipo)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"orden\":1,\"etiqueta\":\"Monto\",\"validacion\":\"número > 0\"}"))
                .andExpect(status().isCreated());
        mvc.perform(post("/api/v1/panel/banco-preguntas/preguntas/" + cd + "/campos-caso")
                        .header("Authorization", "Bearer " + tokenEquipo)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"orden\":2,\"etiqueta\":\"Plazo\"}"))
                .andExpect(status().isCreated());

        // V · dato verificable: su tabla de tramos
        long v = crearPregunta(versionV4, """
                {"codigo":"X07","bloque":"A4","tipo":"V","enunciado":"¿Cuánto demoras un cierre?",
                 "esPuntuable":true,"orden":7,"peso":1}""");
        mvc.perform(post("/api/v1/panel/banco-preguntas/preguntas/" + v + "/rangos")
                        .header("Authorization", "Bearer " + tokenEquipo)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"orden\":1,\"condicion\":\"Menos de 2 días\",\"puntaje\":3}"))
                .andExpect(status().isCreated());
        mvc.perform(post("/api/v1/panel/banco-preguntas/preguntas/" + v + "/rangos")
                        .header("Authorization", "Bearer " + tokenEquipo)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"orden\":2,\"condicion\":\"Más de una semana\",\"puntaje\":0,\"generaBandera\":true}"))
                .andExpect(status().isCreated());

        // PC · el par de consistencia: dos preguntas que no suman y se vigilan entre sí
        long pcA = crearPregunta(versionV4, """
                {"codigo":"X08","bloque":"A5","tipo":"PC","enunciado":"¿Prefieres trabajar solo?",
                 "esPuntuable":false,"orden":8}""");
        opcion(pcA, "{\"letra\":\"a\",\"texto\":\"Sí\"}");
        long pcB = crearPregunta(versionV4, """
                {"codigo":"X09","bloque":"A5","tipo":"PC","enunciado":"¿Disfrutas el trabajo en equipo?",
                 "esPuntuable":false,"orden":9}""");
        opcion(pcB, "{\"letra\":\"a\",\"texto\":\"Sí\"}");
        mvc.perform(post("/api/v1/panel/banco-preguntas/versiones/" + versionV4 + "/pares-consistencia")
                        .header("Authorization", "Bearer " + tokenEquipo)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(("{\"preguntaAId\":%d,\"preguntaBId\":%d,\"penalizacionPorcentaje\":5," +
                                "\"separacionMinimaItems\":15,\"condicion\":\"a(Sí) contradice b(Sí)\"}")
                                .formatted(pcA, pcB)))
                .andExpect(status().isCreated());

        // Lo guardado se puede leer, con su clave — este es el panel, no el portal
        mvc.perform(get("/api/v1/panel/banco-preguntas/preguntas/" + v + "/rangos")
                        .header("Authorization", "Bearer " + tokenEquipo))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[1].generaBandera").value(true));
        mvc.perform(get("/api/v1/panel/banco-preguntas/versiones/" + versionV4 + "/pares-consistencia")
                        .header("Authorization", "Bearer " + tokenEquipo))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].separacionMinimaItems").value(15));

        // Un par entre preguntas de otra versión no mide nada: se rechaza
        Long preguntaDeOtroBanco = jdbc.queryForObject(
                "select id from pregunta where version_banco_id <> " + versionV4 + " limit 1", Long.class);
        mvc.perform(post("/api/v1/panel/banco-preguntas/versiones/" + versionV4 + "/pares-consistencia")
                        .header("Authorization", "Bearer " + tokenEquipo)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"preguntaAId\":%d,\"preguntaBId\":%d}".formatted(pcA, preguntaDeOtroBanco)))
                .andExpect(status().isBadRequest());
    }

    @DisplayName("Publicar valida la coherencia y hace el relevo: una sola PUBLICADA por nivel")
    @Test
    @Order(2)
    void publicarValidaLaCoherenciaYHaceElRelevo() throws Exception {
        // Una elección forzada sin opciones no tiene con qué puntuarse: publicar la nombra
        preguntaIncompleta = crearPregunta(versionV4, """
                {"codigo":"X10","bloque":"A1","tipo":"EF-4","enunciado":"Pregunta a medias",
                 "esPuntuable":true,"orden":10,"peso":1}""");
        mvc.perform(post("/api/v1/panel/banco-preguntas/versiones/" + versionV4 + "/publicacion")
                        .header("Authorization", "Bearer " + tokenEquipo))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value(org.hamcrest.Matchers.containsString("X10")));

        // Completada la clave, el banco pasa la aduana
        opcion(preguntaIncompleta, "{\"letra\":\"a\",\"texto\":\"Opción con valor\",\"valor\":1}");
        opcion(preguntaIncompleta, "{\"letra\":\"b\",\"texto\":\"La otra\",\"valor\":-1}");
        mvc.perform(post("/api/v1/panel/banco-preguntas/versiones/" + versionV4 + "/publicacion")
                        .header("Authorization", "Bearer " + tokenEquipo))
                .andExpect(status().isOk());

        // El relevo: el banco del nivel que estaba publicado (el v3 de la V20) quedó archivado
        assertThat(jdbc.queryForObject("""
                select count(*) from version_banco
                 where tipo_banco = 'NIVEL' and nivel_puesto_codigo = 'EJECUCION'
                   and estado = 'PUBLICADA'""", Integer.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject("""
                select estado from version_banco where etiqueta like '%v3 · Ejecutivo%'""",
                String.class)).isEqualTo("ARCHIVADA");
    }

    @DisplayName("El candidato que postula recibe el banco publicado por la API")
    @Test
    @Order(3)
    void elCandidatoRecibeElBancoNuevo() throws Exception {
        long vacanteId = prepararVacantePublicada();
        tokenCandidato = crearCandidatoYEntrar();

        MockMultipartFile cv = new MockMultipartFile("cv", "cv.pdf",
                "application/pdf", "contenido de prueba".getBytes());
        codigoPostulacion = leer(mvc.perform(multipart("/api/v1/portal/postulaciones")
                        .file(cv)
                        .param("vacanteId", String.valueOf(vacanteId))
                        .param("resultadoOrgulloso", "Automaticé el cierre mensual y pasó de 3 días a 4 horas")
                        .param("aceptaTratamiento", "true")
                        .header("Authorization", "Bearer " + tokenCandidato))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString(), "codigo");

        // La evaluación quedó fijada al banco construido por la API (RF-138: y ahí se queda)
        assertThat(jdbc.queryForObject("""
                select e.version_banco_nivel_id from evaluacion e
                  join postulacion p on p.evaluacion_id = e.id
                 where p.uuid = cast(? as uuid)""", Long.class, codigoPostulacion))
                .isEqualTo(versionV4);
    }

    @DisplayName("Publicar otra versión repunta a quien no empezó, y el examen que ve es el nuevo")
    @Test
    @Order(4)
    void publicarOtraVersionRepuntaAQuienNoEmpezo() throws Exception {
        versionV5 = crearVersion("Banco de práctica v5 · Ejecutivo");
        long unica = crearPregunta(versionV5, """
                {"codigo":"Y01","bloque":"A1","tipo":"EF-4","enunciado":"La única del v5",
                 "esPuntuable":true,"orden":1,"peso":1}""");
        opcion(unica, "{\"letra\":\"a\",\"texto\":\"Más yo\",\"valor\":2}");
        opcion(unica, "{\"letra\":\"b\",\"texto\":\"Menos yo\",\"valor\":-2}");
        mvc.perform(post("/api/v1/panel/banco-preguntas/versiones/" + versionV5 + "/publicacion")
                        .header("Authorization", "Bearer " + tokenEquipo))
                .andExpect(status().isOk());

        // El v4 salió de circulación y la evaluación sin empezar viaja al v5 sin que nadie la toque
        assertThat(jdbc.queryForObject(
                "select estado from version_banco where id = " + versionV4, String.class))
                .isEqualTo("ARCHIVADA");
        assertThat(jdbc.queryForObject("""
                select e.version_banco_nivel_id from evaluacion e
                  join postulacion p on p.evaluacion_id = e.id
                 where p.uuid = cast(? as uuid)""", Long.class, codigoPostulacion))
                .isEqualTo(versionV5);

        // Y al entrar, su examen es el del v5 — con la clave donde debe estar: en ninguna parte
        String cuerpo = mvc.perform(post("/api/v1/portal/evaluacion/" + codigoPostulacion + "/inicio")
                        .header("Authorization", "Bearer " + tokenCandidato))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(1))
                .andReturn().getResponse().getContentAsString();
        assertThat(cuerpo).doesNotContain("valor", "esDistractor", "ordenCorrecto", "logicaInterna");
    }

    @DisplayName("Una versión publicada no admite más opciones, y archivar respeta a quien ya empezó")
    @Test
    @Order(5)
    void unaPublicadaNoAdmiteMasOpcionesYArchivarRespetaAlQueEmpezo() throws Exception {
        // La regresión del candado: antes esto pasaba en silencio y alteraba la clave
        // de un examen en curso. Ahora es un 409.
        Long preguntaDelV5 = jdbc.queryForObject(
                "select id from pregunta where version_banco_id = " + versionV5, Long.class);
        mvc.perform(post("/api/v1/panel/banco-preguntas/preguntas/" + preguntaDelV5 + "/opciones")
                        .header("Authorization", "Bearer " + tokenEquipo)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"letra\":\"z\",\"texto\":\"colada\",\"valor\":0}"))
                .andExpect(status().isConflict());

        // Archivar por endpoint: el candidato ya empezó, así que nadie queda esperando y
        // el nivel puede quedarse sin banco. Su examen sigue en pie (RF-138).
        mvc.perform(post("/api/v1/panel/banco-preguntas/versiones/" + versionV5 + "/archivado")
                        .header("Authorization", "Bearer " + tokenEquipo))
                .andExpect(status().isOk());
        assertThat(jdbc.queryForObject(
                "select estado from version_banco where id = " + versionV5, String.class))
                .isEqualTo("ARCHIVADA");
        assertThat(jdbc.queryForObject("""
                select count(*) from evaluacion e
                  join postulacion p on p.evaluacion_id = e.id
                 where p.uuid = cast(? as uuid) and e.version_banco_nivel_id = %d"""
                .formatted(versionV5), Integer.class, codigoPostulacion))
                .isEqualTo(1);

        // Y archivar dos veces no tiene sentido: ya no está publicada
        mvc.perform(post("/api/v1/panel/banco-preguntas/versiones/" + versionV5 + "/archivado")
                        .header("Authorization", "Bearer " + tokenEquipo))
                .andExpect(status().isConflict());
    }

    // ---------- Los ladrillos del flujo ----------

    private long crearVersion(String etiqueta) throws Exception {
        return Long.parseLong(leer(mvc.perform(post("/api/v1/panel/banco-preguntas/versiones")
                        .header("Authorization", "Bearer " + tokenEquipo)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(("{\"tipoBanco\":\"NIVEL\",\"nivelPuestoCodigo\":\"EJECUCION\"," +
                                "\"etiqueta\":\"%s\"}").formatted(etiqueta)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString(), "id"));
    }

    private long crearPregunta(long versionId, String cuerpo) throws Exception {
        return Long.parseLong(leer(mvc.perform(
                        post("/api/v1/panel/banco-preguntas/versiones/" + versionId + "/preguntas")
                                .header("Authorization", "Bearer " + tokenEquipo)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(cuerpo))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString(), "id"));
    }

    private void opcion(long preguntaId, String cuerpo) throws Exception {
        mvc.perform(post("/api/v1/panel/banco-preguntas/preguntas/" + preguntaId + "/opciones")
                        .header("Authorization", "Bearer " + tokenEquipo)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(cuerpo))
                .andExpect(status().isCreated());
    }

    // Lo mínimo para que exista una vacante publicada de EJECUCION a la que postular.
    // Copiado de FlujoEvaluacionIT: cada IT es autocontenido a propósito.
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
            String codigo = "UNIV_BP_" + i;
            long id = Long.parseLong(leer(conToken(post("/api/v1/panel/plantillas-prueba/preguntas"), token,
                    "{\"codigo\":\"%s\",\"enunciado\":\"Pregunta universal %d\",\"tipo\":\"UNIVERSAL\"}"
                            .formatted(codigo, i))
                    .andReturn().getResponse().getContentAsString(), "id"));
            conToken(post("/api/v1/panel/plantillas-prueba/versiones/" + versionId + "/preguntas"), token,
                    "{\"preguntaPruebaId\": %d}".formatted(id)).andExpect(status().isOk());
        }
        for (int i = 0; i < 3; i++) {
            String codigo = "ESP_BP_" + i;
            long id = Long.parseLong(leer(conToken(post("/api/v1/panel/plantillas-prueba/preguntas"), token,
                    "{\"codigo\":\"%s\",\"enunciado\":\"Pregunta específica %d\",\"tipo\":\"ESPECIFICA\"}"
                            .formatted(codigo, i))
                    .andReturn().getResponse().getContentAsString(), "id"));
            conToken(post("/api/v1/panel/plantillas-prueba/versiones/" + versionId + "/preguntas"), token,
                    "{\"preguntaPruebaId\": %d}".formatted(id)).andExpect(status().isOk());
        }
        conToken(post("/api/v1/panel/plantillas-prueba/versiones/" + versionId + "/rubrica"), token, """
                {"codigo":"RESULTADO_BP","nombre":"Resultado","puntos":100,"metodoVerificacion":"PERSONA"}""")
                .andExpect(status().isCreated());

        conToken(post("/api/v1/panel/plantillas-prueba/versiones/" + versionId + "/publicacion"), token, null)
                .andExpect(status().isOk());
        return versionId;
    }

    private String crearCandidatoYEntrar() throws Exception {
        mvc.perform(post("/api/v1/portal/cuentas")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                        {"nombre":"Bruno","apellidos":"Salas","correo":"bruno@correo.pe",
                         "contrasena":"unaClaveLarga123","aceptaProceso":true,
                         "aceptaFuturosContactos":false}"""))
                .andExpect(status().isCreated());
        return leer(mvc.perform(post("/api/v1/portal/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"correo\":\"bruno@correo.pe\",\"contrasena\":\"unaClaveLarga123\"}"))
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

    private String leer(String cuerpoRespuesta, String campo) throws Exception {
        return json.readTree(cuerpoRespuesta).get(campo).asText();
    }
}
