package com.renaser.ai.ai_engine.integracion;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.renaser.ai.ai_engine.perfilintegral.dto.DtosCalificacionIa.EducacionLeida;
import com.renaser.ai.ai_engine.perfilintegral.dto.DtosCalificacionIa.ExperienciaLeida;
import com.renaser.ai.ai_engine.perfilintegral.dto.DtosCalificacionIa.IdiomaLeido;
import com.renaser.ai.ai_engine.perfilintegral.dto.DtosCalificacionIa.ResultadoDatos;
import com.renaser.ai.ai_engine.perfilintegral.service.PuenteCalificacionIa;
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

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// El perfil del candidato de punta a punta: se crea vacío, se llena con el currículum SIN
// pisar lo que la persona escribió, no se paga dos veces la lectura del mismo archivo, la
// pretensión no viaja al panel sin su permiso, y el borrado de la ley 29733 se lo lleva
// entero. La calificación con IA va apagada: aquí no se califica, se llena una ficha.
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@Testcontainers
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@DisplayName("El perfil del candidato")
public class FlujoPerfilIT {

    @Container
    @ServiceConnection
    static PostgreSQLContainer postgres = new PostgreSQLContainer("pgvector/pgvector:pg16");

    @Container
    @ServiceConnection
    static RabbitMQContainer rabbit = new RabbitMQContainer("rabbitmq:3-management-alpine");

    @DynamicPropertySource
    static void propiedades(DynamicPropertyRegistry registro) {
        registro.add("spring.rabbitmq.ssl.enabled", () -> "false");
        registro.add("spring.rabbitmq.virtual-host", () -> "/");
        registro.add("app.archivos.tipo", () -> "memoria");
        registro.add("app.seguridad.jwt-secreto",
                () -> "clave-de-pruebas-suficientemente-larga-para-hmac-256-bits");
        registro.add("spring.ai.deepseek.api-key", () -> "clave-de-pruebas-no-se-usa");
        registro.add("renaser.ai.calificacion.habilitada", () -> "false");
    }

    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired PuenteCalificacionIa puente;
    final ObjectMapper json = new ObjectMapper();

    static String tokenEquipo;
    static String tokenCandidato;
    static long vacanteId;
    static long postulacionId;
    static long perfilId;
    static long experienciaPropuestaId;
    static long educacionPropuestaId;
    static long versionPruebaId;

    private static final byte[] PDF = "el mismo curriculum de siempre".getBytes();

    @DisplayName("Antes de llenar nada, el perfil responde vacío: nunca un 404")
    @Test
    @Order(1)
    void elPerfilVacioRespondeDoscientos() throws Exception {
        tokenEquipo = leer(mvc.perform(post("/api/v1/panel/auth/dev-login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"usuarioRenaserOsId\":\"dev-1\"}"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(), "token");
        tokenCandidato = crearCandidatoYEntrar("camila@correo.pe");

        conTokenGet("/api/v1/portal/perfil", tokenCandidato)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.titular").isEmpty())
                .andExpect(jsonPath("$.experiencia").isEmpty())
                .andExpect(jsonPath("$.lecturaCv.estado").value("SIN_CV"));

        // Y los catalogos estan servidos, para que nadie los escriba a mano en la pantalla
        conTokenGet("/api/v1/portal/catalogos/niveles-idioma", tokenCandidato)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].codigo").value("A1"));
        conTokenGet("/api/v1/portal/catalogos/niveles-educativos", tokenCandidato)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].codigo").value("SECUNDARIA"));
    }

    @DisplayName("Postular guarda la huella del archivo y propone los enlaces al perfil")
    @Test
    @Order(2)
    void postularDejaHuellaYEnlaces() throws Exception {
        vacanteId = new PreparadorDeVacante().publicada();

        MockMultipartFile cv = new MockMultipartFile("cv", "cv.pdf", "application/pdf", PDF);
        mvc.perform(multipart("/api/v1/portal/postulaciones")
                        .file(cv)
                        .param("vacanteId", String.valueOf(vacanteId))
                        .param("resultadoOrgulloso", "Ordené el archivo de una clínica entera")
                        .param("linkedin", "https://www.linkedin.com/in/camila-rojas")
                        .param("github", "https://github.com/camila")
                        .header("Authorization", "Bearer " + tokenCandidato))
                .andExpect(status().isCreated());
        postulacionId = jdbc.queryForObject(
                "select max(id) from postulacion", Long.class);

        // La huella quedo escrita: es lo que impide pagar dos lecturas del mismo archivo
        String hash = jdbc.queryForObject("""
                select a.contenido_hash from archivo a
                  join cv c on c.archivo_original_id = a.id
                 where c.postulacion_id = ?""", String.class, postulacionId);
        assertThat(hash).hasSize(64);

        // Hay archivo pero nadie lo ha leido (la calificacion va apagada en esta prueba):
        // NO_LEGIBLE, no «leyendo». Antes este caso dejaba a la pantalla dando vueltas para
        // siempre, porque el estado se sacaba del retrato entero y no de la lectura.
        conTokenGet("/api/v1/portal/perfil", tokenCandidato)
                .andExpect(jsonPath("$.lecturaCv.estado").value("NO_LEGIBLE"));

        // Los enlaces del formulario ya estan en el perfil, con su tipo de verdad
        conTokenGet("/api/v1/portal/perfil", tokenCandidato)
                .andExpect(jsonPath("$.enlaces[?(@.tipo=='LINKEDIN')]").exists())
                .andExpect(jsonPath("$.enlaces[?(@.tipo=='GITHUB')]").exists());
    }

    @DisplayName("La lectura del currículum propone al perfil, marcado como del currículum")
    @Test
    @Order(3)
    void laLecturaProponeSinConfirmar() throws Exception {
        // La cola va apagada: se simula que el agente DATOS_CV termino, que es exactamente
        // lo que hace el listener al recibir la respuesta del modelo.
        puente.guardarDatos(postulacionId, null, new ResultadoDatos(
                "Camila Rojas", "camila@correo.pe", "999888777",
                "Ingeniera industrial con ocho años ordenando operaciones",
                List.of("Excel", "Power BI"), 96, "Analista senior", "Clínica San Juan", 30,
                "Ingeniería industrial",
                List.of(new ExperienciaLeida("Analista senior", "Clínica San Juan",
                                "2022-03", null, "Procesos y archivo"),
                        new ExperienciaLeida("Asistente", "Fábrica Sur", "2018-01", "2022-02",
                                null)),
                List.of(new EducacionLeida("Ingeniería Industrial", "UNSA", "TITULADO",
                        "2013-03", "2018-12")),
                List.of(new IdiomaLeido("Inglés", "B2")),
                List.of()));

        String cuerpo = conTokenGet("/api/v1/portal/perfil", tokenCandidato)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.titular").value("Analista senior"))
                .andExpect(jsonPath("$.lecturaCv.estado").value("LISTA"))
                .andExpect(jsonPath("$.experiencia.length()").value(2))
                .andExpect(jsonPath("$.experiencia[0].origen").value("CURRICULUM"))
                .andExpect(jsonPath("$.experiencia[0].confirmado").value(false))
                .andExpect(jsonPath("$.idiomas[0].nivelCodigo").value("B2"))
                .andReturn().getResponse().getContentAsString();

        perfilId = jdbc.queryForObject("select id from perfil_candidato", Long.class);
        experienciaPropuestaId = json.readTree(cuerpo).get("experiencia").get(0).get("id").asLong();
        educacionPropuestaId = json.readTree(cuerpo).get("educacion").get(0).get("id").asLong();
    }

    @DisplayName("El candidato corrige, confirma y borra; lo mal formado no entra")
    @Test
    @Order(4)
    void elCandidatoManda() throws Exception {
        // Editar convierte el dato en suyo
        conToken(put("/api/v1/portal/perfil/experiencia/" + experienciaPropuestaId),
                tokenCandidato, """
                {"puesto":"Analista senior de procesos","empresa":"Clínica San Juan",
                 "desde":"2022-03-01","descripcion":"Lo que de verdad hice"}""")
                .andExpect(status().isOk());
        // Confirmar valida conservando que salio del curriculum
        conToken(post("/api/v1/portal/perfil/educacion/" + educacionPropuestaId
                + "/confirmacion"), tokenCandidato, null).andExpect(status().isOk());

        conTokenGet("/api/v1/portal/perfil", tokenCandidato)
                .andExpect(jsonPath("$.experiencia[0].origen").value("PERSONA"))
                .andExpect(jsonPath("$.experiencia[0].confirmado").value(true))
                .andExpect(jsonPath("$.educacion[0].origen").value("CURRICULUM"))
                .andExpect(jsonPath("$.educacion[0].confirmado").value(true));

        // La cabecera con pretension completa
        conToken(put("/api/v1/portal/perfil"), tokenCandidato, """
                {"titular":"Analista de procesos","resumen":"Mi resumen","habilidades":["Excel"],
                 "experienciaMeses":96,"ubicacion":"Arequipa","disponibilidad":"Inmediata",
                 "pretension":{"min":3500,"max":4200,"moneda":"PEN"}}""")
                .andExpect(status().isOk());

        // Y lo mal formado no entra: pretension a medias, LinkedIn falso, enlace repetido
        conToken(put("/api/v1/portal/perfil"), tokenCandidato,
                "{\"pretension\":{\"min\":3500}}").andExpect(status().isBadRequest());
        conToken(post("/api/v1/portal/perfil/enlaces"), tokenCandidato,
                "{\"tipo\":\"LINKEDIN\",\"url\":\"https://misitio.com/yo\"}")
                .andExpect(status().isBadRequest());
        conToken(post("/api/v1/portal/perfil/enlaces"), tokenCandidato,
                "{\"tipo\":\"GITHUB\",\"url\":\"https://github.com/camila\"}")
                .andExpect(status().isConflict());
    }

    @DisplayName("El mismo archivo no se lee dos veces, y lo corregido queda intacto")
    @Test
    @Order(5)
    void elMismoArchivoNoSePagaDosVeces() throws Exception {
        long vacante2 = new PreparadorDeVacante().publicada();
        MockMultipartFile cv = new MockMultipartFile("cv", "cv.pdf", "application/pdf", PDF);
        mvc.perform(multipart("/api/v1/portal/postulaciones")
                        .file(cv)
                        .param("vacanteId", String.valueOf(vacante2))
                        .param("resultadoOrgulloso", "El mismo resultado de siempre")
                        .header("Authorization", "Bearer " + tokenCandidato))
                .andExpect(status().isCreated());
        long postulacion2 = jdbc.queryForObject("select max(id) from postulacion", Long.class);

        // La ficha se copio a la postulacion nueva sin llamar a ningun modelo
        assertThat(jdbc.queryForObject(
                "select count(*) from dato_cv where postulacion_id = ?", Integer.class,
                postulacion2)).isEqualTo(1);
        assertThat(jdbc.queryForObject(
                "select nombre from dato_cv where postulacion_id = ?", String.class,
                postulacion2)).isEqualTo("Camila Rojas");
        // Con la calificacion apagada, ninguna de las dos postulaciones encolo trabajos:
        // la copia vino de la huella, no de una lectura nueva.
        assertThat(jdbc.queryForObject(
                "select count(*) from trabajo_ia where agente_codigo = 'DATOS_CV'",
                Integer.class)).isZero();

        // Y lo que el candidato corrigio en (4) sigue exactamente igual (RF-159)
        conTokenGet("/api/v1/portal/perfil", tokenCandidato)
                .andExpect(jsonPath("$.experiencia[0].puesto")
                        .value("Analista senior de procesos"))
                .andExpect(jsonPath("$.experiencia[0].origen").value("PERSONA"));
    }

    @DisplayName("Descargar mis datos: el derecho de acceso con un archivo de verdad")
    @Test
    @Order(6)
    void descargarMisDatos() throws Exception {
        conTokenGet("/api/v1/portal/perfil/descarga", tokenCandidato)
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Disposition",
                        org.hamcrest.Matchers.containsString("attachment")))
                .andExpect(jsonPath("$.titular").value("Analista de procesos"));
    }

    @DisplayName("La pretensión no viaja al panel sin su permiso — ni como nombre de campo")
    @Test
    @Order(7)
    void laPretensionNoViajaSinPermiso() throws Exception {
        // El primer usuario del equipo entra con todos los roles, asi que para probar el
        // caso SIN permiso se le quita a todos los roles y se restaura despues: los permisos
        // se leen de la base en cada peticion, no viven en el token.
        jdbc.update("""
                delete from rol_permiso where permiso_id =
                    (select id from permiso where codigo = 'ver_pretension')""");

        String sinPermiso = conTokenGet("/api/v1/panel/postulaciones/" + postulacionId
                + "/perfil", tokenEquipo)
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        // Sobre el JSON crudo, que es lo que llegaria al navegador. No se busca "PEN" a
        // secas: es subcadena de PENDIENTE. Se buscan el campo y los valores con comilla.
        assertThat(sinPermiso).doesNotContain("pretension", "3500", "4200");
        assertThat(sinPermiso).contains("Analista de procesos");   // el resto si viaja

        jdbc.update("""
                insert into rol_permiso (rol_id, permiso_id, alcance)
                select r.id, p.id, 'TODO' from rol r
                  join permiso p on p.codigo = 'ver_pretension'
                 where r.codigo = 'DIRECCION'""");

        String conPermiso = conTokenGet("/api/v1/panel/postulaciones/" + postulacionId
                + "/perfil", tokenEquipo)
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        assertThat(conPermiso).contains("pretension", "3500", "4200");
    }

    @DisplayName("Sin ver_perfil_candidato, la sección entera es un 403")
    @Test
    @Order(8)
    void sinPermisoDePerfilEs403() throws Exception {
        jdbc.update("""
                delete from rol_permiso where permiso_id =
                    (select id from permiso where codigo = 'ver_perfil_candidato')""");

        conTokenGet("/api/v1/panel/postulaciones/" + postulacionId + "/perfil", tokenEquipo)
                .andExpect(status().isForbidden());

        jdbc.update("""
                insert into rol_permiso (rol_id, permiso_id, alcance)
                select r.id, p.id, 'TODO' from rol r
                  join permiso p on p.codigo = 'ver_perfil_candidato'
                 where r.codigo in ('TALENTO', 'DIRECCION')""");
    }

    @DisplayName("El borrado de la ley 29733 se lleva el perfil entero; dato_cv se queda")
    @Test
    @Order(9)
    void elBorradoSeLlevaElPerfil() throws Exception {
        conToken(post("/api/v1/portal/solicitudes-borrado"), tokenCandidato,
                "{\"motivo\":\"Ya no quiero participar\"}").andExpect(status().isCreated());
        Long solicitudId = jdbc.queryForObject(
                "select max(id) from solicitud_borrado", Long.class);

        conToken(post("/api/v1/panel/solicitudes-borrado/" + solicitudId + "/ejecucion"),
                tokenEquipo, "{\"motivo\":\"Lo pidió el titular\"}")
                .andExpect(status().isOk());

        // Las seis tablas del perfil, a cero para esa persona
        for (String tabla : List.of("experiencia_perfil", "educacion_perfil", "idioma_perfil",
                "certificacion_perfil", "enlace_perfil")) {
            assertThat(jdbc.queryForObject(
                    "select count(*) from " + tabla + " where perfil_candidato_id = ?",
                    Integer.class, perfilId))
                    .as(tabla).isZero();
        }
        assertThat(jdbc.queryForObject(
                "select count(*) from perfil_candidato where id = ?", Integer.class, perfilId))
                .isZero();

        // dato_cv NO se toca aqui: sostiene la criba y el ranking de lo ya evaluado
        assertThat(jdbc.queryForObject(
                "select count(*) from dato_cv where postulacion_id = ?", Integer.class,
                postulacionId)).isEqualTo(1);
    }

    // ==================== Apoyo ====================

    /** La vacante publicable minima, calcada de FlujoEvaluacionIT. */
    private class PreparadorDeVacante {

        long publicada() throws Exception {
            jdbc.update("insert into area (organizacion_id, nombre, es_activa) "
                    + "select 1, 'Área ' || (random()*100000)::int, true");
            Long areaId = jdbc.queryForObject("select max(id) from area", Long.class);
            long solicitudId = Long.parseLong(leer(conToken(post("/api/v1/panel/solicitudes"),
                    tokenEquipo, """
                    {"areaId": %d, "urgencia": "NORMAL",
                     "nivelPuestoCodigo": "EJECUCION", "familiaCodigo": "TECNOLOGIA",
                     "resultadoPrincipal": "Sostener el portal",
                     "motivo": "Falta gente", "consecuenciaNoContratar": "Retraso",
                     "analisisCapacidad": "No alcanza con el equipo actual",
                     "responsableUsuarioId": 1,
                     "resultadosEsperados": [
                       {"descripcion": "Publicar", "indicador": "en producción"},
                       {"descripcion": "Reducir bugs", "indicador": "la mitad"},
                       {"descripcion": "Documentar", "indicador": "docs al día"}
                     ]}""".formatted(areaId))
                    .andReturn().getResponse().getContentAsString(), "id"));
            conToken(post("/api/v1/panel/solicitudes/" + solicitudId + "/aprobacion"),
                    tokenEquipo, "{\"motivo\":\"Hay presupuesto\"}").andExpect(status().isOk());

            long puestoId = Long.parseLong(leer(conToken(post("/api/v1/panel/puestos"),
                    tokenEquipo, """
                    {"codigo": "DEV_%d", "nombre": "Desarrollador web",
                     "nivelPuestoCodigo": "EJECUCION", "familiaCodigo": "TECNOLOGIA"}"""
                    .formatted(System.nanoTime()))
                    .andReturn().getResponse().getContentAsString(), "id"));
            long id = Long.parseLong(leer(conToken(post("/api/v1/panel/vacantes"), tokenEquipo, """
                    {"solicitudTalentoId": %d, "puestoId": %d,
                     "titulo": "Desarrollador web", "descripcion": "Portal",
                     "tipoCierre": "PERMANENTE", "responsableUsuarioId": 1}"""
                    .formatted(solicitudId, puestoId))
                    .andReturn().getResponse().getContentAsString(), "id"));

            Long plantillaId = jdbc.queryForObject("select id from plantilla_evaluacion "
                    + "where nivel_puesto_codigo = 'EJECUCION'", Long.class);
            conToken(post("/api/v1/panel/vacantes/" + id + "/plantilla-evaluacion"),
                    tokenEquipo, "{\"plantillaEvaluacionId\": %d}".formatted(plantillaId))
                    .andExpect(status().isOk());
            conToken(post("/api/v1/panel/vacantes/" + id + "/plantilla-prueba"), tokenEquipo,
                    "{\"versionPlantillaPruebaId\": %d}".formatted(versionDePrueba()))
                    .andExpect(status().isOk());
            conToken(post("/api/v1/panel/vacantes/" + id + "/publicacion"), tokenEquipo, null)
                    .andExpect(status().isOk());
            return id;
        }

        /** La prueba minima publicable, una vez por tanda: las dos vacantes la comparten. */
        private long versionDePrueba() throws Exception {
            if (versionPruebaId != 0) {
                return versionPruebaId;
            }
            long plantillaId = Long.parseLong(leer(conToken(
                    post("/api/v1/panel/plantillas-prueba"), tokenEquipo,
                    "{\"nombre\":\"Prueba genérica\"}")
                    .andReturn().getResponse().getContentAsString(), "id"));
            long versionId = Long.parseLong(leer(conToken(
                    post("/api/v1/panel/plantillas-prueba/" + plantillaId + "/versiones"),
                    tokenEquipo, """
                    {"enunciado":"Resuelve el caso","modalidad":"CRONOMETRADA",
                     "duracionMinutos":90,"minutoCambioMin":30,"minutoCambioMax":50,
                     "minutosExtra":10}""")
                    .andReturn().getResponse().getContentAsString(), "id"));
            for (int i = 0; i < 8; i++) {
                agregarPregunta(versionId, "UNIV_PF_" + i, "UNIVERSAL", i);
            }
            for (int i = 0; i < 3; i++) {
                agregarPregunta(versionId, "ESP_PF_" + i, "ESPECIFICA", i);
            }
            conToken(post("/api/v1/panel/plantillas-prueba/versiones/" + versionId
                    + "/rubrica"), tokenEquipo, """
                    {"codigo":"RESULTADO_PF","nombre":"Resultado","puntos":100,
                     "metodoVerificacion":"PERSONA"}""")
                    .andExpect(status().isCreated());
            conToken(post("/api/v1/panel/plantillas-prueba/versiones/" + versionId
                    + "/publicacion"), tokenEquipo, null).andExpect(status().isOk());
            versionPruebaId = versionId;
            return versionId;
        }

        private void agregarPregunta(long versionId, String codigo, String tipo, int i)
                throws Exception {
            long id = Long.parseLong(leer(conToken(
                    post("/api/v1/panel/plantillas-prueba/preguntas"), tokenEquipo,
                    "{\"codigo\":\"%s\",\"enunciado\":\"Pregunta %d\",\"tipo\":\"%s\"}"
                            .formatted(codigo, i, tipo))
                    .andReturn().getResponse().getContentAsString(), "id"));
            conToken(post("/api/v1/panel/plantillas-prueba/versiones/" + versionId
                    + "/preguntas"), tokenEquipo,
                    "{\"preguntaPruebaId\": %d}".formatted(id)).andExpect(status().isOk());
        }
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
                        .content("{\"correo\":\"%s\",\"contrasena\":\"unaClaveLarga123\"}"
                                .formatted(correo)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(), "token");
    }

    private ResultActions conToken(
            org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder peticion,
            String token, String cuerpo) throws Exception {
        peticion.header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON);
        if (cuerpo != null) {
            peticion.content(cuerpo);
        }
        return mvc.perform(peticion);
    }

    private ResultActions conTokenGet(String ruta, String token) throws Exception {
        return mvc.perform(get(ruta).header("Authorization", "Bearer " + token));
    }

    private String leer(String cuerpoRespuesta, String campo) throws Exception {
        return json.readTree(cuerpoRespuesta).get(campo).asText();
    }
}
