package com.renaser.ai.ai_engine.integracion;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.renaser.ai.ai_engine.integracion.soporte.ImagenesDeContenedores;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.rabbitmq.RabbitMQContainer;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Los informes por etapa contra la base de verdad: se arman con lo guardado, piden el permiso de
 * su pantalla, no enseñan lo que nunca sale (pretensión, celular, contenido de entregables) y no
 * escriben nada.
 *
 * <p>Las notas, afirmaciones y la prueba se siembran en la base: son lo que la IA y el candidato
 * habrían dejado, y la calificación con IA está apagada en estas pruebas.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
@DisplayName("Los informes por etapa: lo que dicen, a quién y sin escribir nada")
public class FlujoInformesPorEtapaIT {

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

    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;

    final ObjectMapper json = new ObjectMapper();

    static String token;
    static long vacante;
    static long ana;
    static long bruno;

    @BeforeEach
    void laVacanteYSusCandidatos() throws Exception {
        if (token != null) {
            return;
        }
        token = leer(mvc.perform(post("/api/v1/panel/auth/dev-login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"usuarioRenaserOsId\":\"dev-informes\"}"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(), "token");
        long area = jdbc.queryForObject("INSERT INTO area (organizacion_id, nombre, es_activa) "
                + "VALUES (1, 'Gestión humana', true) RETURNING id", Long.class);
        long puesto = Long.parseLong(leer(conToken(post("/api/v1/panel/puestos"), """
                {"nombre": "Analista de Recursos Humanos", "nivelPuestoCodigo": "EJECUCION",
                 "familiaCodigo": "OPERACIONES"}""", 201), "id"));
        vacante = nuevaVacante(area, puesto, "Analista de Recursos Humanos");

        ana = candidata("Ana", "Torres Quispe", "ana.torres@correo.pe");
        sembrarElCv(ana);
        sembrarLaPrueba(ana);
        bruno = candidata("Bruno", "Díaz", "bruno@correo.pe");
    }

    // =============================================================== el CV

    @Test
    @DisplayName("AC-01 · AC-02 · AC-06 · AC-11 · el informe del CV trae sus bloques en la primera página y nada de lo que nunca sale")
    void elInformeDelCv() throws Exception {
        MockHttpServletResponse respuesta = pedir("/api/v1/panel/postulaciones/" + ana + "/informes/cv/pdf", 200);

        assertThat(respuesta.getContentType()).isEqualTo("application/pdf");
        assertThat(nombreDelArchivo(respuesta))
                .isEqualTo("Informe CV - Ana Torres Quispe - Analista de Recursos Humanos.pdf");
        byte[] pdf = respuesta.getContentAsByteArray();
        String primera = texto(pdf, 1, 1);
        assertThat(primera).contains("Ana Torres Quispe", "EXPERIENCIA PROFESIONAL", "QUÉ SE EVALUÓ",
                "Lo que el CV demuestra", "No cuadra", "Por verificar en la evaluación",
                "«Manejo de planillas y nómina»", "¿Cuántos colaboradores tenía tu última planilla?",
                "«Excel avanzado»", "Cuéntame un reporte que armaste tú.",
                "Redujo el tiempo de contratación de 30 a 18 días");
        String todo = texto(pdf, 1, Integer.MAX_VALUE);
        // AC-06: la nota final, «ajustada a mano» y el motivo.
        assertThat(todo).contains("Ajustada a mano: el puesto pide nómina desde el primer mes");
        // AC-11: ni la pretensión ni el celular, que sí están guardados.
        assertThat(todo).doesNotContain("4500", "4 500", "4.500", "999888777", "999 888 777");
        // AC-01c: la entrevista solo en el informe de la prueba.
        assertThat(todo).doesNotContain("Por verificar en la entrevista");
    }

    @Test
    @DisplayName("El visor lee los mismos datos: la nota una vez, el criterio ajustado con su motivo y el texto completo")
    void elInformeComoPagina() throws Exception {
        JsonNode informe = json.readTree(pedir("/api/v1/panel/postulaciones/" + ana + "/informes/cv", 200)
                .getContentAsString());
        assertThat(informe.get("etapa").asText()).isEqualTo("CV");
        assertThat(informe.get("candidato").asText()).isEqualTo("Ana Torres Quispe");
        assertThat(informe.toString()).doesNotContain("999888777").doesNotContain("4500");
        JsonNode ajustado = null;
        for (JsonNode c : informe.get("evaluado").get("criterios")) {
            if (c.get("ajustada").asBoolean()) {
                ajustado = c;
            }
        }
        assertThat(ajustado).isNotNull();
        assertThat(ajustado.get("motivoAjuste").asText()).isEqualTo("el puesto pide nómina desde el primer mes");
        assertThat(ajustado.get("quien").asText()).isEqualTo("Persona");
    }

    // =============================================================== la prueba

    @Test
    @DisplayName("AC-01c · AC-05 · AC-06 · el de la prueba trae cada criterio con máximo, quién y su motivo, la entrevista y los entregables")
    void elInformeDeLaPrueba() throws Exception {
        MockHttpServletResponse respuesta = pedir("/api/v1/panel/postulaciones/" + ana + "/informes/prueba/pdf", 200);
        assertThat(nombreDelArchivo(respuesta))
                .isEqualTo("Informe prueba - Ana Torres Quispe - Analista de Recursos Humanos.pdf");
        String todo = texto(respuesta.getContentAsByteArray(), 1, Integer.MAX_VALUE);
        assertThat(todo).contains("Por verificar en la entrevista", "Diagnóstico del problema", "23 / 25",
                "Presupuesto del plan", "10 / 20", "Ajustada a mano: la hoja de cálculo no abrió",
                "Cronograma por tienda", "NO ENTREGADO", "DEL CV", "¿Cuántos colaboradores tenía tu última planilla?");
        assertThat(todo.indexOf("23 / 25")).isEqualTo(todo.lastIndexOf("23 / 25"));
    }

    // =============================================================== huecos y permisos

    @Test
    @DisplayName("AC-08 · sin evaluación no hay informe: el estado dice por qué y pedirlo da 409, nunca un PDF vacío")
    void sinEvaluacionNoHayInforme() throws Exception {
        JsonNode estados = json.readTree(pedir("/api/v1/panel/postulaciones/" + ana + "/informes", 200)
                .getContentAsString());
        JsonNode evaluacion = null;
        for (JsonNode e : estados.get("informes")) {
            if ("EVALUACION".equals(e.get("etapa").asText())) {
                evaluacion = e;
            }
        }
        assertThat(evaluacion).isNotNull();
        assertThat(evaluacion.get("disponible").asBoolean()).isFalse();
        assertThat(evaluacion.get("motivo").asText()).isEqualTo("Todavía no tiene calificación");
        pedir("/api/v1/panel/postulaciones/" + ana + "/informes/evaluacion/pdf", 409);
        // Bruno no tiene nada calificado: ningún informe.
        pedir("/api/v1/panel/postulaciones/" + bruno + "/informes/cv/pdf", 409);
    }

    @Test
    @DisplayName("AC-09 · sin «ver respuestas de la evaluación» no sale esa etapa, y pedirla directamente da 403")
    void sinPermisoDeLaEvaluacion() throws Exception {
        String limitado = cuentaSinRespuestasDeLaEvaluacion();
        JsonNode estados = json.readTree(mvc.perform(get("/api/v1/panel/postulaciones/" + ana + "/informes")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + limitado))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        List<String> etapas = new ArrayList<>();
        estados.get("informes").forEach(e -> etapas.add(e.get("etapa").asText()));
        assertThat(etapas).doesNotContain("EVALUACION").contains("CV", "PRUEBA");
        mvc.perform(get("/api/v1/panel/postulaciones/" + ana + "/informes/evaluacion/pdf")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + limitado))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("AC-10 · el candidato de otra empresa da 404 y no se genera ni se escribe nada")
    void otraEmpresa() throws Exception {
        long otra = jdbc.queryForObject("insert into organizacion (codigo, nombre) values ('QA_INFORMES_B', "
                + "'Otra S.A.C.') returning id", Long.class);
        long persona = jdbc.queryForObject("insert into persona (nombre, apellidos) values ('Zoe', 'Ajena') "
                + "returning id", Long.class);
        long usuario = jdbc.queryForObject("insert into usuario (organizacion_id, persona_id, es_equipo, "
                + "es_activo) values (?, ?, false, true) returning id", Long.class, otra, persona);
        long suya = jdbc.queryForObject("insert into postulacion (organizacion_id, usuario_id, vacante_id, "
                + "estado_codigo) values (?, ?, ?, 'PERFIL_POR_CONFIRMAR') returning id",
                Long.class, otra, usuario, vacante);
        String antes = foto();
        pedir("/api/v1/panel/postulaciones/" + suya + "/informes/cv/pdf", 404);
        pedir("/api/v1/panel/postulaciones/" + suya + "/informes/cv", 404);
        assertThat(foto()).isEqualTo(antes);
    }

    // =============================================================== los .zip y el visor

    @Test
    @DisplayName("Sus 3 informes: un .zip con los disponibles y un aviso con el que falta y por qué")
    void losTresDeUnPostulante() throws Exception {
        MockHttpServletResponse respuesta = pedir("/api/v1/panel/postulaciones/" + ana + "/informes/zip", 200);
        Map<String, String> entradas = entradas(respuesta.getContentAsByteArray());
        assertThat(entradas).containsKeys("1 - Informe del CV.pdf", "3 - Informe de la prueba del puesto.pdf",
                "LEEME - informes que faltan.txt");
        assertThat(entradas.get("LEEME - informes que faltan.txt"))
                .contains("Informe de la evaluación: Todavía no tiene calificación.");
    }

    @Test
    @DisplayName("AC-01f · AC-01f2 · el visor lista a todos en el orden del ranking y el .zip de la vacante lleva una carpeta por postulante visible, aunque aún no tenga informes")
    void elVisorYLaVacanteEntera() throws Exception {
        JsonNode visor = json.readTree(pedir("/api/v1/panel/vacantes/" + vacante + "/informes", 200)
                .getContentAsString());
        List<String> nombres = new ArrayList<>();
        visor.get("postulantes").forEach(p -> nombres.add(p.get("candidato").asText()));
        assertThat(nombres).contains("Ana Torres Quispe", "Bruno Díaz");
        assertThat(visor.get("etapas")).hasSize(3);

        Map<String, String> entradas = entradas(pedir("/api/v1/panel/vacantes/" + vacante + "/informes/zip", 200)
                .getContentAsByteArray());
        assertThat(entradas.keySet()).anyMatch(n -> n.endsWith("Ana Torres Quispe/1 - Informe del CV.pdf"));
        assertThat(entradas.keySet()).anyMatch(n -> n.endsWith("Ana Torres Quispe/3 - Informe de la prueba del puesto.pdf"));
        assertThat(entradas.get("LEEME - informes que faltan.txt")).contains("Bruno Díaz");
        // F-2: una carpeta por postulante visible, también para Bruno, que aún no tiene ninguno
        // (queda vacía; el aviso dice por qué). La postulación de otra empresa que siembra
        // «otraEmpresa» no se ve, y no entra.
        Set<String> carpetas = new HashSet<>();
        entradas.keySet().stream().filter(n -> n.contains("/")).forEach(n -> carpetas.add(n.substring(0, n.indexOf('/'))));
        assertThat(carpetas).anyMatch(c -> c.endsWith("Ana Torres Quispe"))
                .anyMatch(c -> c.endsWith("Bruno Díaz"))
                .noneMatch(c -> c.contains("Zoe Ajena"))
                .hasSize(2);
        assertThat(entradas.keySet()).anyMatch(n -> n.endsWith("Bruno Díaz/"))
                .noneMatch(n -> n.contains("Bruno Díaz/") && n.endsWith(".pdf"));
    }

    @Test
    @DisplayName("Pedir un informe no escribe nada: dos descargas seguidas no tocan notas ni la cola")
    void noEscribeNada() throws Exception {
        String antes = foto();
        pedir("/api/v1/panel/postulaciones/" + ana + "/informes/cv/pdf", 200);
        pedir("/api/v1/panel/postulaciones/" + ana + "/informes/prueba/pdf", 200);
        assertThat(foto()).isEqualTo(antes);
    }

    // =============================================================== la siembra

    private long candidata(String nombre, String apellidos, String correo) {
        long persona = jdbc.queryForObject("insert into persona (nombre, apellidos) values (?, ?) returning id",
                Long.class, nombre, apellidos);
        long usuario = jdbc.queryForObject("insert into usuario (organizacion_id, persona_id, correo, es_equipo, "
                + "es_activo) values (1, ?, ?, false, true) returning id", Long.class, persona, correo);
        return jdbc.queryForObject("insert into postulacion (organizacion_id, usuario_id, vacante_id, "
                + "estado_codigo, pretension_monto, pretension_moneda, pretension_declarada_en) values (1, ?, ?, "
                + "'PRUEBA_POR_CONFIRMAR', 4500, 'PEN', now()) returning id", Long.class, usuario, vacante);
    }

    private void sembrarElCv(long postulacion) {
        long cv = jdbc.queryForObject("insert into cv (postulacion_id, texto_extraido) values (?, ?) returning id",
                Long.class, postulacion, "Analista de selección con cuatro años en retail. ".repeat(10));
        jdbc.update("""
                insert into dato_cv (postulacion_id, nombre, email, telefono, perfil_resumen, habilidades,
                                     experiencia_meses_total, ultimo_puesto, ultima_empresa,
                                     ultima_meses_duracion, educacion_maxima)
                values (?, 'Ana Torres Quispe', 'ana.torres@correo.pe', '999888777',
                        'Analista de RR. HH. con foco en selección.', 'Selección masiva | Excel', 50,
                        'Analista de Selección', 'Retail Sur S.A.C.', 29, 'Licenciada en Psicología')""",
                postulacion);
        afirmacion(cv, "Redujo el tiempo de contratación de 30 a 18 días", "DEMOSTRADA", null);
        afirmacion(cv, "Jefa de área en 2022 y practicante a la vez", "CONTRADICHA", null);
        afirmacion(cv, "Manejo de planillas y nómina", "DECLARADA", "¿Cuántos colaboradores tenía tu última planilla?");
        afirmacion(cv, "Excel avanzado", "DECLARADA", "Cuéntame un reporte que armaste tú.");

        List<Map<String, Object>> criterios = jdbc.queryForList("select id from criterio where etapa_codigo = "
                + "'PERFIL_INTEGRAL' and version_plantilla_prueba_id is null order by orden");
        for (int i = 0; i < criterios.size(); i++) {
            long criterio = ((Number) criterios.get(i).get("id")).longValue();
            if (i == 0) {
                jdbc.update("insert into nota_criterio (postulacion_id, criterio_id, puntaje, explicacion, origen, "
                        + "ajustada_por_usuario_id, motivo_ajuste, ajustada_en) values (?, ?, 55, ?, 'AGENTE', 1, ?, now())",
                        postulacion, criterio, "Nombra planillas sin ningún ejemplo.",
                        "el puesto pide nómina desde el primer mes");
            } else {
                jdbc.update("insert into nota_criterio (postulacion_id, criterio_id, puntaje, explicacion, origen) "
                        + "values (?, ?, ?, ?, 'AGENTE')", postulacion, criterio, BigDecimal.valueOf(60 + i),
                        "Redujo el tiempo de contratación de 30 a 18 días con cifras.");
            }
        }
        long version = jdbc.queryForObject("select version_pesos_id from vacante where id = ?", Long.class, vacante);
        long perfil = jdbc.queryForObject("insert into perfil_talento (postulacion_id, confianza_evidencia, resumen, "
                + "version_pesos_id) values (?, 70, 'Responde con episodios concretos.', ?) returning id",
                Long.class, postulacion, version);
        jdbc.update("insert into hallazgo_perfil (perfil_talento_id, tipo, descripcion) values (?, 'FALTA_EVIDENCIA', "
                + "'No hay evidencia de manejo de nómina')", perfil);
    }

    private void afirmacion(long cv, String texto, String clase, String pregunta) {
        jdbc.update("insert into afirmacion_cv (cv_id, texto, clasificacion, pregunta_validacion) values (?, ?, ?, ?)",
                cv, texto, clase, pregunta);
    }

    private void sembrarLaPrueba(long postulacion) {
        long plantilla = jdbc.queryForObject("insert into plantilla_prueba (organizacion_id, puesto_id, nombre) "
                + "select 1, puesto_id, 'Caso de las tres tiendas' from vacante where id = ? returning id",
                Long.class, vacante);
        long version = jdbc.queryForObject("""
                insert into version_plantilla_prueba (plantilla_prueba_id, version, enunciado, modalidad,
                                                      plazo_dias, estado, publicada_en)
                values (?, 1, 'Plan de reclutamiento para tres tiendas', 'PLAZO_ABIERTO', 3, 'PUBLICADA', now())
                returning id""", Long.class, plantilla);
        long diagnostico = criterioDePrueba(version, "DIAG", "Diagnóstico del problema", 25, 1);
        long presupuesto = criterioDePrueba(version, "PRESU", "Presupuesto del plan", 20, 2);
        jdbc.update("update vacante set version_plantilla_prueba_id = ?, instrumento_etapa_tecnica = 'PLANTILLA' "
                + "where id = ?", version, vacante);
        jdbc.update("insert into intento_prueba (postulacion_id, version_plantilla_prueba_id, iniciado_en, entregado_en) "
                + "values (?, ?, now() - interval '2 days', now() - interval '1 day')", postulacion, version);
        jdbc.update("insert into nota_criterio (postulacion_id, criterio_id, puntaje, explicacion, origen) "
                + "values (?, ?, 23, 'Ubicó la rotación en los primeros meses.', 'AGENTE')", postulacion, diagnostico);
        jdbc.update("insert into nota_criterio (postulacion_id, criterio_id, puntaje, explicacion, origen, "
                + "ajustada_por_usuario_id, motivo_ajuste, ajustada_en) values (?, ?, 10, 'Calificó con el resumen.', "
                + "'PERSONA', 1, 'la hoja de cálculo no abrió', now())", postulacion, presupuesto);
        jdbc.update("insert into entregable_requerido (version_plantilla_prueba_id, nombre, detalle, formato, "
                + "es_obligatorio, orden) values (?, 'Cronograma por tienda', 'Semanas por tienda', 'ARCHIVO', true, 1)",
                version);
        jdbc.update("insert into nota_etapa (postulacion_id, etapa_codigo, puntaje, version_pesos_id) "
                + "select ?, 'PRUEBA_PUESTO', 33, version_pesos_id from vacante where id = ?", postulacion, vacante);
    }

    private long criterioDePrueba(long version, String codigo, String nombre, int puntos, int orden) {
        return jdbc.queryForObject("""
                insert into criterio (codigo, nombre, etapa_codigo, version_plantilla_prueba_id, puntos,
                                      metodo_verificacion, orden)
                values (?, ?, 'PRUEBA_PUESTO', ?, ?, 'AGENTE', ?) returning id""",
                Long.class, codigo, nombre, version, BigDecimal.valueOf(puntos), orden);
    }

    private String cuentaSinRespuestasDeLaEvaluacion() throws Exception {
        long rol = jdbc.queryForObject("insert into rol (organizacion_id, codigo, nombre) values "
                + "(1, 'QA_INFORMES', 'QA sin respuestas') returning id", Long.class);
        jdbc.update("""
                insert into rol_permiso (rol_id, permiso_id, alcance)
                select ?, rp.permiso_id, rp.alcance from rol_permiso rp
                  join rol r on r.id = rp.rol_id join permiso p on p.id = rp.permiso_id
                 where r.organizacion_id = 1 and r.codigo = 'TALENTO'
                   and p.codigo <> 'ver_respuestas_evaluacion'""", rol);
        long persona = jdbc.queryForObject("insert into persona (nombre, apellidos) values ('QA', 'Informes') "
                + "returning id", Long.class);
        long usuario = jdbc.queryForObject("""
                insert into usuario (organizacion_id, persona_id, usuario_renaser_os_id, es_equipo, es_activo)
                values (1, ?, 'dev-informes-limitado', true, true) returning id""", Long.class, persona);
        jdbc.update("insert into usuario_rol (usuario_id, rol_id) values (?, ?)", usuario, rol);
        return leer(mvc.perform(post("/api/v1/panel/auth/dev-login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"usuarioRenaserOsId\":\"dev-informes-limitado\"}"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(), "token");
    }

    private long nuevaVacante(long area, long puesto, String titulo) throws Exception {
        long solicitud = Long.parseLong(leer(conToken(post("/api/v1/panel/solicitudes"), """
                {"areaId": %d, "puestoId": %d, "urgencia": "NORMAL",
                 "nivelPuestoCodigo": "EJECUCION", "familiaCodigo": "OPERACIONES",
                 "resultadoPrincipal": "Cubrir las tiendas", "motivo": "El equipo no llega",
                 "consecuenciaNoContratar": "Se retrasa la apertura",
                 "analisisCapacidad": "Se evaluó redistribuir y no alcanza",
                 "responsableUsuarioId": 1,
                 "resultadosEsperados": [
                   {"descripcion": "Cubrir tres tiendas", "indicador": "en marcha"},
                   {"descripcion": "Bajar la rotación", "indicador": "a 90 días"},
                   {"descripcion": "Formar al equipo", "indicador": "tres personas"}
                 ]}""".formatted(area, puesto), 201), "id"));
        conToken(post("/api/v1/panel/solicitudes/" + solicitud + "/aprobacion"),
                "{\"motivo\":\"Justificada: hay presupuesto\"}", 200);
        return Long.parseLong(leer(conToken(post("/api/v1/panel/vacantes"), """
                {"solicitudTalentoId": %d, "titulo": "%s", "descripcion": "Lleva la selección",
                 "tipoCierre": "PERMANENTE", "responsableUsuarioId": 1}""".formatted(solicitud, titulo), 201), "id"));
    }

    /** Todo lo que un informe podría tocar, en una línea que se compara antes y después. */
    private String foto() {
        return jdbc.queryForObject("""
                select (select count(*) from nota_etapa) || '/' || (select count(*) from nota_criterio) || '/' ||
                       (select coalesce(sum(puntaje), 0) from nota_criterio) || '/' ||
                       (select count(*) from trabajo_ia) || '/' || (select count(*) from texto_informe) || '/' ||
                       (select count(*) from auditoria) || '/' ||
                       (select string_agg(estado_codigo, ',' order by id) from postulacion)""", String.class);
    }

    // =============================================================== HTTP y lectura

    private MockHttpServletResponse pedir(String ruta, int estado) throws Exception {
        return mvc.perform(get(ruta).header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().is(estado))
                .andReturn().getResponse();
    }

    private String conToken(org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder peticion,
                            String cuerpo, int estado) throws Exception {
        return mvc.perform(peticion.header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON).content(cuerpo))
                .andExpect(status().is(estado))
                .andReturn().getResponse().getContentAsString();
    }

    private String leer(String cuerpo, String campo) throws Exception {
        JsonNode nodo = json.readTree(cuerpo).get(campo);
        assertThat(nodo).as("campo %s en %s", campo, cuerpo).isNotNull();
        return nodo.asText();
    }

    private static String nombreDelArchivo(MockHttpServletResponse respuesta) {
        String disposicion = respuesta.getHeader(HttpHeaders.CONTENT_DISPOSITION);
        assertThat(disposicion).isNotNull();
        int i = disposicion.indexOf("filename*=UTF-8''");
        assertThat(i).isGreaterThanOrEqualTo(0);
        return URLDecoder.decode(disposicion.substring(i + "filename*=UTF-8''".length()), StandardCharsets.UTF_8);
    }

    private static String texto(byte[] pdf, int desde, int hasta) throws IOException {
        try (PDDocument documento = Loader.loadPDF(pdf)) {
            PDFTextStripper lector = new PDFTextStripper();
            lector.setStartPage(desde);
            lector.setEndPage(Math.min(hasta, documento.getNumberOfPages()));
            return lector.getText(documento).replaceAll("\\s+", " ");
        }
    }

    private static Map<String, String> entradas(byte[] zip) throws IOException {
        Map<String, String> salida = new java.util.LinkedHashMap<>();
        try (ZipInputStream entrada = new ZipInputStream(new ByteArrayInputStream(zip), StandardCharsets.UTF_8)) {
            ZipEntry e;
            while ((e = entrada.getNextEntry()) != null) {
                byte[] contenido = entrada.readAllBytes();
                salida.put(e.getName(), e.getName().endsWith(".txt")
                        ? new String(contenido, StandardCharsets.UTF_8) : "pdf:" + contenido.length);
            }
        }
        return salida;
    }
}
