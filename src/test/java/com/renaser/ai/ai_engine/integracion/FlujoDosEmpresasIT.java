package com.renaser.ai.ai_engine.integracion;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.renaser.ai.ai_engine.integracion.soporte.ImagenesDeContenedores;
import com.renaser.ai.ai_engine.integracion.soporte.RespuestaV3;

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
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * La prueba de fuego del multiempresa: dos empresas de verdad, de punta a punta.
 *
 * <p>La plataforma da de alta a ACME por el endpoint real; su administradora entra por la
 * invitación; ACME publica una vacante evaluando con el método de Renaser (banderas
 * apagadas); una candidata de la plataforma le postula y su postulación es de ACME; la
 * candidata rinde el examen entero del banco de Renaser y ACME ve su nota, su bandeja y
 * su embudo —y la plataforma no—; ACME personaliza sus pesos sin llevarse el banco; y el
 * borrado de la ley 29733 solo funciona desde la plataforma. Entre medias, lo ajeno
 * responde «no existe» — en los dos sentidos.
 *
 * <p>La regla de arquitectura ({@code ArquitecturaTest}) vigila la forma; esta prueba
 * vigila la verdad: aquí las dos organizaciones existen con datos, y una consulta sin
 * filtrar devolvería filas de la otra en vez de un 404.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@Testcontainers
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@DisplayName("Multiempresa · Dos empresas en la misma plataforma")
public class FlujoDosEmpresasIT {

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
        // El dev-login quedo apagado por defecto en application.yaml: aqui se enciende
        // explicitamente, solo para que la plataforma arranque su primer usuario.
        registro.add("app.seguridad.dev-login-activo", () -> "true");
        registro.add("spring.ai.deepseek.api-key", () -> "clave-de-pruebas-no-se-usa");
        registro.add("renaser.ai.calificacion.habilitada", () -> "false");
    }

    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    final ObjectMapper json = new ObjectMapper();

    static final String CONTRASENA_ANA = "una-clave-de-panel-larga";

    static String tokenPlataforma;
    static String tokenAcme;
    static String tokenCandidata;
    static long plataformaId;
    static long acmeId;
    static long anaId;
    static long vacanteAcmeId;
    static long solicitudAcmeId;
    static long postulacionAcmeId;

    // ============ El alta ============

    @DisplayName("La plataforma da de alta a ACME: nace sembrada y con su invitación en camino")
    @Test
    @Order(1)
    void laPlataformaDaDeAltaAAcme() throws Exception {
        tokenPlataforma = leer(mvc.perform(post("/api/v1/panel/auth/dev-login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"usuarioRenaserOsId\":\"dev-1\"}"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(), "token");
        plataformaId = jdbc.queryForObject(
                "select id from organizacion where es_plataforma", Long.class);

        String respuesta = conToken(post("/api/v1/panel/plataforma/empresas"), tokenPlataforma, """
                {"nombre": "Acme S.A.C.", "codigo": "ACME", "correoAdministrador": "Ana@Acme.pe"}""")
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        acmeId = json.readTree(respuesta).get("id").asLong();
        String urlInvitacion = json.readTree(respuesta).get("urlInvitacion").asText();

        // La siembra completa del día uno: sin ella la empresa nace coja y nada avisa.
        // Roles con su matriz (menos administrar_plataforma), parámetros editables,
        // textos legales en borrador y correos activos.
        assertThat(contar("select count(*) from rol where organizacion_id = " + acmeId)).isEqualTo(5);
        assertThat(contar("""
                select count(*) from rol_permiso rp
                  join rol r on r.id = rp.rol_id and r.organizacion_id = %d
                  join permiso p on p.id = rp.permiso_id and p.codigo = 'administrar_plataforma'"""
                .formatted(acmeId))).isZero();
        assertThat(contar("select count(*) from parametro where organizacion_id = " + acmeId))
                .isEqualTo(contar("select count(*) from parametro where organizacion_id = " + plataformaId));
        assertThat(contar("select count(*) from texto_consentimiento where organizacion_id = "
                + acmeId + " and publicado_en is null")).isEqualTo(2);
        assertThat(contar("select count(*) from texto_consentimiento where organizacion_id = "
                + acmeId + " and publicado_en is not null")).isZero();
        assertThat(contar("select count(*) from plantilla_correo where organizacion_id = "
                + acmeId + " and es_activa")).isGreaterThanOrEqualTo(7);

        // El correo de la invitación quedó registrado, sin usuario porque Ana aún no existe
        assertThat(contar("select count(*) from correo_enviado where "
                + "plantilla_correo_codigo = 'INVITACION_EQUIPO' and usuario_id is null")).isEqualTo(1);

        // En la base vive solo el hash del token; el token de verdad va en el enlace
        String token = urlInvitacion.substring(urlInvitacion.indexOf("token=") + 6);
        assertThat(contar("select count(*) from invitacion where token_hash = '" + token + "'")).isZero();

        // Ana canjea la invitación: pone su nombre y su contraseña, y entra
        String sesion = mvc.perform(post("/api/v1/panel/auth/invitacion")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"token": "%s", "nombre": "Ana", "apellidos": "Torres",
                                 "contrasena": "%s"}""".formatted(token, CONTRASENA_ANA)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        anaId = json.readTree(sesion).get("usuarioId").asLong();

        // De un solo uso: canjearla otra vez no crea otra cuenta, y el error no distingue
        mvc.perform(post("/api/v1/panel/auth/invitacion")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"token": "%s", "nombre": "Otra", "apellidos": "Persona",
                                 "contrasena": "otra-clave-igual-de-larga"}"""
                                .formatted(token)))
                .andExpect(status().isUnauthorized());

        // Y desde entonces entra por el login normal del panel, a SU organización
        tokenAcme = leer(mvc.perform(post("/api/v1/panel/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"correo\":\"ana@acme.pe\",\"contrasena\":\"%s\"}"
                                .formatted(CONTRASENA_ANA)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(), "token");
        conTokenGet("/api/v1/panel/usuarios", tokenAcme)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].correo").value("ana@acme.pe"));
    }

    @DisplayName("Un candidato del portal no entra al panel, ni con su contraseña correcta")
    @Test
    @Order(2)
    void unCandidatoDelPortalNoEntraAlPanel() throws Exception {
        tokenCandidata = crearCandidataYEntrar("camila@correo.pe");

        // La misma contraseña que en el portal le funciona: aquí no. Su cuenta no es de
        // equipo, y el error es el mismo que el de un correo inexistente.
        mvc.perform(post("/api/v1/panel/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"correo\":\"camila@correo.pe\",\"contrasena\":\"unaClaveLarga123\"}"))
                .andExpect(status().isUnauthorized());
    }

    // ============ ACME publica con el método de Renaser ============

    @DisplayName("ACME publica una vacante evaluando con el banco y los pesos de Renaser")
    @Test
    @Order(3)
    void acmePublicaConElMetodoDeRenaser() throws Exception {
        // Ana se completa los roles operativos: el alta solo le dio ADMINISTRADOR, y para
        // abrir una vacante hacen falta Dirección y Talento. Puede, porque administra.
        conToken(post("/api/v1/panel/usuarios/" + anaId + "/roles"), tokenAcme,
                "{\"roles\":[\"ADMINISTRADOR\",\"DIRECCION\",\"TALENTO\"]}")
                .andExpect(status().isOk());

        conToken(post("/api/v1/panel/areas"), tokenAcme, "{\"nombre\":\"Operaciones\"}")
                .andExpect(status().isCreated());
        Long areaId = jdbc.queryForObject(
                "select id from area where organizacion_id = " + acmeId, Long.class);

        solicitudAcmeId = Long.parseLong(leer(conToken(post("/api/v1/panel/solicitudes"), tokenAcme, """
                {"areaId": %d, "urgencia": "NORMAL",
                 "nivelPuestoCodigo": "EJECUCION", "familiaCodigo": "TECNOLOGIA",
                 "resultadoPrincipal": "Sostener la operación",
                 "motivo": "El equipo no da abasto",
                 "consecuenciaNoContratar": "Se caen los plazos",
                 "analisisCapacidad": "Se evaluó redistribuir y no alcanza",
                 "responsableUsuarioId": %d,
                 "resultadosEsperados": [
                   {"descripcion": "Cubrir la demanda", "indicador": "sin atrasos"},
                   {"descripcion": "Documentar procesos", "indicador": "al día"},
                   {"descripcion": "Reducir errores", "indicador": "a la mitad"}
                 ]}""".formatted(areaId, anaId))
                .andReturn().getResponse().getContentAsString(), "id"));
        conToken(post("/api/v1/panel/solicitudes/" + solicitudAcmeId + "/aprobacion"), tokenAcme,
                "{\"motivo\":\"Hay presupuesto\"}").andExpect(status().isOk());

        long puestoId = Long.parseLong(leer(conToken(post("/api/v1/panel/puestos"), tokenAcme, """
                {"codigo": "OPS_WEB", "nombre": "Analista de operaciones",
                 "nivelPuestoCodigo": "EJECUCION", "familiaCodigo": "TECNOLOGIA"}""")
                .andReturn().getResponse().getContentAsString(), "id"));

        // Crear la vacante ya usa el método compartido: los pesos por defecto que le
        // tocan son los publicados de la PLATAFORMA, porque ACME no personalizó nada.
        vacanteAcmeId = Long.parseLong(leer(conToken(post("/api/v1/panel/vacantes"), tokenAcme, """
                {"solicitudTalentoId": %d, "puestoId": %d,
                 "titulo": "Analista de operaciones", "descripcion": "Turno completo",
                 "tipoCierre": "PERMANENTE", "responsableUsuarioId": %d}"""
                .formatted(solicitudAcmeId, puestoId, anaId))
                .andReturn().getResponse().getContentAsString(), "id"));
        assertThat(jdbc.queryForObject("""
                select vp.organizacion_id from vacante v
                  join version_pesos vp on vp.id = v.version_pesos_id
                 where v.id = %d""".formatted(vacanteAcmeId), Long.class))
                .isEqualTo(plataformaId);

        // La plantilla de evaluación que ACME ve y elige es la de la plataforma
        Long plantillaId = jdbc.queryForObject("""
                select id from plantilla_evaluacion
                 where organizacion_id = %d and nivel_puesto_codigo = 'EJECUCION'"""
                .formatted(plataformaId), Long.class);
        conToken(post("/api/v1/panel/vacantes/" + vacanteAcmeId + "/plantilla-evaluacion"), tokenAcme,
                "{\"plantillaEvaluacionId\": %d}".formatted(plantillaId)).andExpect(status().isOk());

        // Y la prueba del puesto también: la arma la plataforma, ACME la usa tal cual
        Long versionPruebaId = armarUnaPruebaValida(tokenPlataforma);
        conToken(post("/api/v1/panel/vacantes/" + vacanteAcmeId + "/plantilla-prueba"), tokenAcme,
                "{\"versionPlantillaPruebaId\": %d}".formatted(versionPruebaId)).andExpect(status().isOk());

        conToken(post("/api/v1/panel/vacantes/" + vacanteAcmeId + "/publicacion"), tokenAcme, null)
                .andExpect(status().isOk());
    }

    // ============ La candidata ============

    @DisplayName("Una candidata de la plataforma postula a ACME, y su postulación es de ACME")
    @Test
    @Order(4)
    void unaCandidataPostulaAAcme() throws Exception {
        // El tablón es de todas las empresas, y cada vacante dice de quién es
        conTokenGet("/api/v1/portal/vacantes", tokenCandidata)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].nombreEmpresa").value("Acme S.A.C."));

        MockMultipartFile cv = new MockMultipartFile("cv", "cv.pdf",
                "application/pdf", "curriculum de camila".getBytes());
        mvc.perform(multipart("/api/v1/portal/postulaciones")
                        .file(cv)
                        .param("vacanteId", String.valueOf(vacanteAcmeId))
                        .param("resultadoOrgulloso", "Ordené un almacén que llevaba años a ciegas")
                        .header("Authorization", "Bearer " + tokenCandidata))
                .andExpect(status().isCreated());

        // La postulación nace en ACME —la empresa de la vacante—, aunque la cuenta de la
        // candidata cuelgue de la plataforma. Y su evaluación quedó atada al banco de la
        // plataforma: bandera apagada = leer el banco de Renaser.
        postulacionAcmeId = jdbc.queryForObject(
                "select id from postulacion where vacante_id = " + vacanteAcmeId, Long.class);
        assertThat(jdbc.queryForObject("select organizacion_id from postulacion where id = "
                + postulacionAcmeId, Long.class)).isEqualTo(acmeId);
        assertThat(jdbc.queryForObject("""
                select vb.organizacion_id from postulacion p
                  join evaluacion e on e.id = p.evaluacion_id
                  join version_banco vb on vb.id = e.version_banco_nivel_id
                 where p.id = %d""".formatted(postulacionAcmeId), Long.class))
                .isEqualTo(plataformaId);

        // ACME la ve en su panel; la plataforma no — no es su candidata en este proceso
        conTokenGet("/api/v1/panel/postulaciones/" + postulacionAcmeId, tokenAcme)
                .andExpect(status().isOk());
        conTokenGet("/api/v1/panel/postulaciones/" + postulacionAcmeId, tokenPlataforma)
                .andExpect(status().isNotFound());
    }

    // ============ El viaje completo de la candidata ============

    @DisplayName("La candidata rinde el examen de Renaser y ACME ve la nota en su bandeja")
    @Test
    @Order(5)
    void laCandidataRindeElExamenYAcmeVeLaNota() throws Exception {
        String codigo = jdbc.queryForObject(
                "select uuid::text from postulacion where id = " + postulacionAcmeId, String.class);

        // El examen se arma con el banco de la plataforma: los 50 ítems del nivel de
        // Ejecución, aunque la vacante sea de ACME. La bandera apagada no es solo una
        // columna: es este examen existiendo sin que ACME haya escrito una pregunta.
        JsonNode evaluacion = json.readTree(mvc.perform(
                        post("/api/v1/portal/evaluacion/" + codigo + "/inicio")
                                .header("Authorization", "Bearer " + tokenCandidata))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("EN_CURSO"))
                .andReturn().getResponse().getContentAsString());
        assertThat(evaluacion.get("total").asInt()).isEqualTo(50);

        // Los responde todos —cada formato del v3 con su forma— y entrega
        for (JsonNode pregunta : evaluacion.get("preguntas")) {
            mvc.perform(put("/api/v1/portal/evaluacion/" + codigo + "/respuestas/"
                            + pregunta.get("id").asLong())
                            .header("Authorization", "Bearer " + tokenCandidata)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(RespuestaV3.para(pregunta)))
                    .andExpect(status().isOk());
        }
        mvc.perform(post("/api/v1/portal/evaluacion/" + codigo + "/entrega")
                        .header("Authorization", "Bearer " + tokenCandidata))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("TERMINADA"));

        // Lo cerrado se puntuó al entregar —aritmética contra la clave, sin IA— y la
        // nota quedó atada a los pesos de la PLATAFORMA: los que la vacante fijó al
        // nacer. Que ACME personalice los suyos después no la mueve.
        assertThat(jdbc.queryForObject("""
                select vp.organizacion_id from nota_etapa ne
                  join version_pesos vp on vp.id = ne.version_pesos_id
                 where ne.postulacion_id = %d and ne.etapa_codigo = 'PERFIL_INTEGRAL'"""
                .formatted(postulacionAcmeId), Long.class)).isEqualTo(plataformaId);

        // ACME ve el viaje entero desde su panel: la ficha con el estado real, la nota
        // de la evaluación en el perfil integral, la candidata en su bandeja de «lo
        // trabaja el sistema» (con la IA apagada ahí se queda) y su embudo contándola.
        conTokenGet("/api/v1/panel/postulaciones/" + postulacionAcmeId, tokenAcme)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("PERFIL_CALIFICANDO"));
        conTokenGet("/api/v1/panel/postulaciones/" + postulacionAcmeId + "/perfil-integral",
                tokenAcme)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.notaEtapa").isNotEmpty());
        conTokenGet("/api/v1/panel/bandeja?espera_a=SISTEMA", tokenAcme)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].postulacionId").value(postulacionAcmeId));
        conTokenGet("/api/v1/panel/vacantes/" + vacanteAcmeId + "/embudo", tokenAcme)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.porEstado.PERFIL_CALIFICANDO").value(1));

        // Y la plataforma sigue sin ver nada de esto: ni la nota ni la bandeja
        conTokenGet("/api/v1/panel/postulaciones/" + postulacionAcmeId + "/perfil-integral",
                tokenPlataforma)
                .andExpect(status().isNotFound());
        conTokenGet("/api/v1/panel/bandeja?espera_a=SISTEMA", tokenPlataforma)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }

    // ============ El aislamiento ============

    @DisplayName("Lo ajeno responde «no existe», en los dos sentidos")
    @Test
    @Order(6)
    void loAjenoRespondeNoExiste() throws Exception {
        // La plataforma no ve lo operativo de ACME…
        conTokenGet("/api/v1/panel/vacantes/" + vacanteAcmeId, tokenPlataforma)
                .andExpect(status().isNotFound());
        conTokenGet("/api/v1/panel/solicitudes/" + solicitudAcmeId, tokenPlataforma)
                .andExpect(status().isNotFound());

        // …ni ACME lo de la plataforma
        jdbc.update("INSERT INTO area (organizacion_id, nombre, es_activa) VALUES (?, 'Talento', true)",
                plataformaId);
        Long areaPlataforma = jdbc.queryForObject(
                "select id from area where organizacion_id = " + plataformaId, Long.class);
        long solicitudPlataforma = Long.parseLong(leer(
                conToken(post("/api/v1/panel/solicitudes"), tokenPlataforma, """
                {"areaId": %d, "urgencia": "NORMAL",
                 "nivelPuestoCodigo": "EJECUCION", "familiaCodigo": "TECNOLOGIA",
                 "resultadoPrincipal": "Cubrir el puesto",
                 "motivo": "Rotación", "consecuenciaNoContratar": "Sobrecarga",
                 "analisisCapacidad": "No alcanza", "responsableUsuarioId": 1,
                 "resultadosEsperados": [
                   {"descripcion": "Cubrir", "indicador": "listo"},
                   {"descripcion": "Formar", "indicador": "listo"},
                   {"descripcion": "Entregar", "indicador": "listo"}
                 ]}""".formatted(areaPlataforma))
                .andReturn().getResponse().getContentAsString(), "id"));
        conTokenGet("/api/v1/panel/solicitudes/" + solicitudPlataforma, tokenAcme)
                .andExpect(status().isNotFound());

        // Una versión de prueba en borrador de ACME: la rúbrica completa de un examen.
        // La plataforma no la ve por id suelto — era la fuga de verVersion.
        long plantillaAcme = Long.parseLong(leer(
                conToken(post("/api/v1/panel/plantillas-prueba"), tokenAcme,
                        "{\"nombre\":\"Prueba propia de ACME\"}")
                .andReturn().getResponse().getContentAsString(), "id"));
        long versionAcme = Long.parseLong(leer(
                conToken(post("/api/v1/panel/plantillas-prueba/" + plantillaAcme + "/versiones"),
                        tokenAcme, """
                        {"enunciado":"Caso interno","modalidad":"PLAZO_ABIERTO","plazoDias":5}""")
                .andReturn().getResponse().getContentAsString(), "id"));
        conTokenGet("/api/v1/panel/plantillas-prueba/versiones/" + versionAcme, tokenPlataforma)
                .andExpect(status().isNotFound());

        // La excepción deliberada, comprobada como tal: ACME SÍ lee las pruebas de la
        // plataforma —bandera apagada = leerlas en solo lectura—, pero no las edita.
        Long versionPlataforma = jdbc.queryForObject("""
                select vpp.id from version_plantilla_prueba vpp
                  join plantilla_prueba pp on pp.id = vpp.plantilla_prueba_id
                 where pp.organizacion_id = %d and vpp.estado = 'PUBLICADA'"""
                .formatted(plataformaId), Long.class);
        conTokenGet("/api/v1/panel/plantillas-prueba/versiones/" + versionPlataforma, tokenAcme)
                .andExpect(status().isOk());
    }

    // ============ La personalización ============

    @DisplayName("ACME personaliza sus pesos: copia con origen, y el banco sigue compartido")
    @Test
    @Order(7)
    void acmePersonalizaSusPesos() throws Exception {
        conToken(post("/api/v1/panel/organizacion/personalizacion"), tokenAcme,
                "{\"instrumento\":\"PESOS\"}").andExpect(status().isOk());

        // La copia existe, publicada, y sabe de qué versión de la plataforma salió
        Long copiadaDe = jdbc.queryForObject("""
                select copiada_de_version_id from version_pesos
                 where organizacion_id = %d and estado = 'PUBLICADA'""".formatted(acmeId), Long.class);
        assertThat(jdbc.queryForObject("select organizacion_id from version_pesos where id = "
                + copiadaDe, Long.class)).isEqualTo(plataformaId);
        // Con sus cuatro repartos completos: tantas filas hijas como el original
        for (String tabla : List.of("peso_etapa", "peso_componente_perfil",
                "peso_dimension", "peso_criterio")) {
            assertThat(contar(("select count(*) from %s h join version_pesos vp on "
                    + "vp.id = h.version_pesos_id where vp.organizacion_id = %d").formatted(tabla, acmeId)))
                    .as(tabla)
                    .isEqualTo(contar(("select count(*) from %s where version_pesos_id = %d")
                            .formatted(tabla, copiadaDe)));
        }

        // Y las banderas son independientes: los pesos son suyos, el banco sigue siendo
        // el de Renaser — ni una fila de banco nació para ACME
        assertThat(contar("select count(*) from version_banco where organizacion_id = " + acmeId))
                .isZero();
        conTokenGet("/api/v1/panel/organizacion/personalizacion", tokenAcme)
                .andExpect(jsonPath("$.pesosPropios").value(true))
                .andExpect(jsonPath("$.bancoPropio").value(false));
    }

    // ============ El borrado ============

    @DisplayName("El borrado de la ley 29733 es de la plataforma: desde ACME ni se lista")
    @Test
    @Order(8)
    void elBorradoEsDeLaPlataforma() throws Exception {
        mvc.perform(post("/api/v1/portal/solicitudes-borrado")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"motivo\":\"Ya no busco trabajo\"}")
                        .header("Authorization", "Bearer " + tokenCandidata))
                .andExpect(status().isCreated());
        Long solicitudId = jdbc.queryForObject(
                "select id from solicitud_borrado where ejecutado_en is null", Long.class);

        // ACME tiene el permiso copiado, pero el borrado no es una función de su panel:
        // los candidatos son de la plataforma y la anonimización cruza empresas
        conTokenGet("/api/v1/panel/solicitudes-borrado", tokenAcme)
                .andExpect(status().isForbidden());
        conToken(post("/api/v1/panel/solicitudes-borrado/" + solicitudId + "/ejecucion"),
                tokenAcme, null).andExpect(status().isForbidden());

        // Desde la plataforma funciona, y se lleva el nombre de la persona de verdad
        conTokenGet("/api/v1/panel/solicitudes-borrado", tokenPlataforma)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1));
        conToken(post("/api/v1/panel/solicitudes-borrado/" + solicitudId + "/ejecucion"),
                tokenPlataforma, null).andExpect(status().isOk());
        assertThat(jdbc.queryForObject("""
                select count(*) from persona p
                  join solicitud_borrado sb on sb.persona_id = p.id
                 where sb.id = %d and p.anonimizado_en is not null and p.nombre is null"""
                .formatted(solicitudId), Integer.class)).isEqualTo(1);
    }

    // ============ Apoyo ============

    private String crearCandidataYEntrar(String correo) throws Exception {
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

    // La mínima prueba del puesto publicable, calcada de FlujoEvaluacionIT: 8 universales
    // + 3 específicas y una rúbrica de un criterio que suma 100.
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
                    "{\"codigo\":\"UNIV_2E_%d\",\"enunciado\":\"Pregunta universal %d\",\"tipo\":\"UNIVERSAL\"}"
                            .formatted(i, i))
                    .andReturn().getResponse().getContentAsString(), "id"));
            conToken(post("/api/v1/panel/plantillas-prueba/versiones/" + versionId + "/preguntas"), token,
                    "{\"preguntaPruebaId\": %d}".formatted(id)).andExpect(status().isOk());
        }
        for (int i = 0; i < 3; i++) {
            long id = Long.parseLong(leer(conToken(post("/api/v1/panel/plantillas-prueba/preguntas"), token,
                    "{\"codigo\":\"ESP_2E_%d\",\"enunciado\":\"Pregunta específica %d\",\"tipo\":\"ESPECIFICA\"}"
                            .formatted(i, i))
                    .andReturn().getResponse().getContentAsString(), "id"));
            conToken(post("/api/v1/panel/plantillas-prueba/versiones/" + versionId + "/preguntas"), token,
                    "{\"preguntaPruebaId\": %d}".formatted(id)).andExpect(status().isOk());
        }
        conToken(post("/api/v1/panel/plantillas-prueba/versiones/" + versionId + "/rubrica"), token, """
                {"codigo":"RESULTADO_2E","nombre":"Resultado","puntos":100,"metodoVerificacion":"PERSONA"}""")
                .andExpect(status().isCreated());
        conToken(post("/api/v1/panel/plantillas-prueba/versiones/" + versionId + "/publicacion"), token, null)
                .andExpect(status().isOk());
        return versionId;
    }

    private int contar(String sql) {
        Integer valor = jdbc.queryForObject(sql, Integer.class);
        return valor == null ? 0 : valor;
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
