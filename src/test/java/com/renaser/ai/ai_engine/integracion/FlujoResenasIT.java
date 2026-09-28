package com.renaser.ai.ai_engine.integracion;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.renaser.ai.ai_engine.integracion.soporte.ImagenesDeContenedores;
import com.renaser.ai.ai_engine.seguridad.service.ServicioToken;

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
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.rabbitmq.RabbitMQContainer;

import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Las reseñas de empresas (V63), de punta a punta y contra la base de verdad.
 *
 * <p>Los servicios ya se prueban con dobles. Lo que solo tiene respuesta con Postgres y HTTP
 * de por medio, y por eso existe esto: que la migración siembre los permisos donde toca, que
 * los índices parciales hagan de verdad «una viva», que las consultas JPQL cuadren con el
 * esquema, que las dos puertas (portal y panel) no se crucen, y que el borrado de datos no
 * deje nada.
 *
 * <p>Las contrataciones se siembran como pide la spec: insertando la transición a
 * {@code CONTRATADO} con una fecha pasada, relativa a hoy —sin fechas quemadas—.
 *
 * <p>Los pasos van en orden y comparten estado.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@DisplayName("Reseñas de empresas a quien contrataron")
public class FlujoResenasIT {

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
    @Autowired ServicioToken tokens;
    final ObjectMapper json = new ObjectMapper();

    private static final String OPINION =
            "Muy responsable con los plazos y con el equipo de obra en todo momento.";
    private static final String RESPUESTA =
            "Gracias por la oportunidad, aprendí mucho con todo el equipo de obra.";

    static long plataforma;
    static long acme;
    /** Dirección, Talento y Administrador de la plataforma: el primero del dev-login. */
    static String tokenDireccion;
    static String tokenResponsable;
    static String tokenOtroResponsable;
    static String tokenAcme;
    static String tokenCandidata;
    static String tokenOtraCandidata;
    static long usuarioCandidata;
    static long personaCandidata;
    /** Contratada hace 31 días por la plataforma, en la vacante del Responsable. */
    static long contratadaHace31;
    /** Contratada hace 10 días, en otra vacante del mismo Responsable. */
    static long contratadaHace10;
    /** Contratada por ACME hace 40 días. */
    static long contratadaPorAcme;
    static long vacanteAcme;
    static long resenaDeLaPlataforma;
    static long resenaDeAcme;

    // ============ 1. El terreno ============

    @Test
    @Order(1)
    @DisplayName("la plataforma, ACME dada de alta, dos Responsables, dos candidatas y tres contrataciones")
    void elTerreno() throws Exception {
        tokenDireccion = leer(mvc.perform(post("/api/v1/panel/auth/dev-login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"usuarioRenaserOsId\":\"dev-resenas\"}"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString(),
                "token");
        plataforma = jdbc.queryForObject("select id from organizacion where es_plataforma",
                Long.class);
        acme = Long.parseLong(leer(conToken(post("/api/v1/panel/plataforma/empresas"),
                tokenDireccion, """
                {"nombre": "Acme S.A.C.", "codigo": "ACME", "correoAdministrador": "ana@acme.pe"}""")
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString(),
                "id"));

        long responsable = equipo(plataforma, "RESPONSABLE_AREA", "Rosa");
        long otroResponsable = equipo(plataforma, "RESPONSABLE_AREA", "Omar");
        long talentoAcme = equipo(acme, "TALENTO", "Tania");
        tokenResponsable = tokens.emitir(responsable, plataforma, "EQUIPO");
        tokenOtroResponsable = tokens.emitir(otroResponsable, plataforma, "EQUIPO");
        tokenAcme = tokens.emitir(talentoAcme, acme, "EQUIPO");

        tokenCandidata = crearCandidata("camila@correo.pe", "Camila");
        tokenOtraCandidata = crearCandidata("bruno@correo.pe", "Bruno");
        usuarioCandidata = jdbc.queryForObject(
                "select id from usuario where lower(correo) = 'camila@correo.pe'", Long.class);
        personaCandidata = jdbc.queryForObject(
                "select persona_id from usuario where id = ?", Long.class, usuarioCandidata);
        long usuarioOtra = jdbc.queryForObject(
                "select id from usuario where lower(correo) = 'bruno@correo.pe'", Long.class);

        long vacanteA1 = vacante(plataforma, responsable, "Desarrollador Backend");
        long vacanteA2 = vacante(plataforma, responsable, "Analista de datos");
        vacanteAcme = vacante(acme, talentoAcme, "Coordinador de obra");
        contratadaHace31 = contratada(vacanteA1, plataforma, usuarioCandidata, 31);
        contratadaHace10 = contratada(vacanteA2, plataforma, usuarioCandidata, 10);
        contratadaPorAcme = contratada(vacanteAcme, acme, usuarioCandidata, 40);
        // La otra candidata, en la misma tanda de ACME y sin reseñas: su fila dice «—».
        postulacion(vacanteAcme, acme, usuarioOtra, "PERFIL_POR_CONFIRMAR");
    }

    // ============ 2. La migración ============

    @Test
    @Order(2)
    @DisplayName("V63: escribir y leer para Talento, Responsable (sus vacantes) y Dirección en todas las empresas; moderar solo el Administrador de la plataforma")
    void losPermisosQuedanSembrados() {
        assertThat(alcance(plataforma, "RESPONSABLE_AREA", "resenar_contratado"))
                .isEqualTo("SUS_VACANTES");
        assertThat(alcance(plataforma, "TALENTO", "ver_resenas_candidato")).isEqualTo("TODO");
        assertThat(alcance(plataforma, "DIRECCION", "resenar_contratado")).isEqualTo("TODO");
        assertThat(alcance(plataforma, "ADMINISTRADOR", "moderar_resenas")).isEqualTo("TODO");
        // AC-22: la empresa dada de alta después recibe escribir y leer, pero no moderar.
        assertThat(alcance(acme, "RESPONSABLE_AREA", "ver_resenas_candidato"))
                .isEqualTo("SUS_VACANTES");
        assertThat(contar("""
                select count(*) from rol_permiso rp
                  join rol r on r.id = rp.rol_id and r.organizacion_id = %d
                  join permiso p on p.id = rp.permiso_id and p.codigo = 'moderar_resenas'"""
                .formatted(acme))).isZero();
    }

    // ============ 3. Los rechazos por la API ============

    @Test
    @Order(3)
    @DisplayName("AC-02, AC-04 y el alcance: 10 días, empresa ajena y Responsable de otra vacante")
    void losRechazos() throws Exception {
        // AC-02: a los 10 días el bloque dice cuándo, y la API rechaza con 409.
        String bloque = conToken(get(resenas(contratadaHace10)), tokenResponsable, null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.puedeResenar").value(true))
                .andExpect(jsonPath("$.miResena.estado").value("AUN_NO_TOCA"))
                .andReturn().getResponse().getContentAsString();
        Instant abre = Instant.parse(json.readTree(bloque).at("/miResena/abreEn").asText());
        assertThat(abre).isBetween(Instant.now().plus(Duration.ofDays(19)),
                Instant.now().plus(Duration.ofDays(21)));
        conToken(post(resena(contratadaHace10)), tokenResponsable, cuerpo(5, OPINION))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value(containsString("Todavía no")));

        // AC-04: ACME recibe el 404 de siempre, que no confirma nada.
        conToken(post(resena(contratadaHace31)), tokenAcme, cuerpo(5, OPINION))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.detail").value(not(containsString("alcance"))));

        // El Responsable de OTRA vacante de la misma empresa: 404, pero dice por qué.
        conToken(post(resena(contratadaHace31)), tokenOtroResponsable, cuerpo(5, OPINION))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.detail").value(containsString("fuera de tu alcance")));

        // AC-35: un token del portal no abre el panel.
        conToken(post(resena(contratadaHace31)), tokenCandidata, cuerpo(5, OPINION))
                .andExpect(status().is4xxClientError());

        // AC-06: la validación también la aplica el backend.
        conToken(post(resena(contratadaHace31)), tokenResponsable,
                "{\"estrellas\": 4.5, \"texto\": \"" + OPINION + "\"}")
                .andExpect(status().isBadRequest());
        conToken(post(resena(contratadaHace31)), tokenResponsable, cuerpo(4, "Muy bien."))
                .andExpect(status().isBadRequest());
        assertThat(contar("select count(*) from resena")).isZero();
    }

    // ============ 4. Publicar, borrar y volver a publicar ============

    @Test
    @Order(4)
    @DisplayName("AC-01, AC-07, AC-10: publica, la segunda se rechaza, borrarla libera la contratación")
    void publicarYBorrar() throws Exception {
        conToken(post(resena(contratadaHace31)), tokenResponsable, cuerpo(4, OPINION))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.estado").value("EDITABLE"))
                .andExpect(jsonPath("$.puesto").value("Desarrollador Backend"));
        // AC-07: otra persona de la misma empresa, la misma contratación: 409.
        conToken(post(resena(contratadaHace31)), tokenDireccion, cuerpo(2, OPINION))
                .andExpect(status().isConflict());

        // AC-10: borrada, la contratación vuelve a quedar libre y la base lo permite.
        conToken(delete(resena(contratadaHace31)), tokenResponsable, null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("SE_PUEDE_ESCRIBIR"));
        conToken(post(resena(contratadaHace31)), tokenResponsable, cuerpo(5, OPINION))
                .andExpect(status().isCreated());
        resenaDeLaPlataforma = jdbc.queryForObject(
                "select id from resena where postulacion_id = ? and borrada_en is null",
                Long.class, contratadaHace31);

        // AC-11: el aviso en su campana, con enlace a su perfil (no a un proceso).
        conToken(get("/api/v1/portal/avisos"), tokenCandidata, null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.avisos[0].tipo").value("RESENA_PUBLICADA"))
                .andExpect(jsonPath("$.avisos[0].postulacionUuid").doesNotExist());

        // ACME reseña su propia contratación.
        conToken(post(resena(contratadaPorAcme)), tokenAcme, cuerpo(2, OPINION))
                .andExpect(status().isCreated());
        resenaDeAcme = jdbc.queryForObject(
                "select id from resena where postulacion_id = ? and borrada_en is null",
                Long.class, contratadaPorAcme);

        // La auditoría sabe que hubo reseñas, sin copiar el texto.
        assertThat(contar("select count(*) from auditoria where accion = 'publicar_resena'"))
                .isEqualTo(3);
        assertThat(contar("select count(*) from auditoria where valor_nuevo::text like '%"
                + OPINION.substring(0, 20) + "%'")).isZero();
    }

    // ============ 5. Lo que ve la persona y lo que ven las empresas ============

    @Test
    @Order(5)
    @DisplayName("AC-12 y AC-23: el perfil trae el resumen, y la tabla de ACME la columna con el promedio")
    void loQueSeVe() throws Exception {
        conToken(get("/api/v1/portal/perfil"), tokenCandidata, null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.resenas.cantidad").value(2))
                .andExpect(jsonPath("$.resenas.promedio").value(3.5));
        conToken(get("/api/v1/portal/perfil"), tokenOtraCandidata, null)
                .andExpect(jsonPath("$.resenas.cantidad").value(0));
        conToken(get("/api/v1/portal/resenas"), tokenCandidata, null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.resenas.length()").value(2))
                .andExpect(jsonPath("$.resenas[?(@.empresa == 'Acme S.A.C.')].puesto")
                        .value(org.hamcrest.Matchers.contains("Coordinador de obra")));

        // AC-23: la tabla de ACME ofrece la columna, y cada fila su promedio o nada.
        conToken(get("/api/v1/panel/vacantes/" + vacanteAcme + "/ranking"), tokenAcme, null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.puedeVerResenas").value(true))
                .andExpect(jsonPath("$.filas[?(@.postulacionId == " + contratadaPorAcme
                        + ")].resenas.cantidad").value(org.hamcrest.Matchers.contains(2)))
                .andExpect(jsonPath("$.filas[?(@.postulacionId != " + contratadaPorAcme
                        + ")].resenas").value(org.hamcrest.Matchers.contains((Object) null)));

        // AC-26: la ficha de ACME lee también la de la plataforma, con su nombre.
        conToken(get(resenas(contratadaPorAcme)), tokenAcme, null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.resenas.length()").value(2))
                .andExpect(jsonPath("$.miResena.estado").value("EDITABLE"));
    }

    // ============ 6. Responder, y la empresa edita ============

    @Test
    @Order(6)
    @DisplayName("AC-33, AC-34 y AC-37: responde, la segunda se rechaza, y editar la reseña le avisa y le mueve el plazo")
    void responderYEditar() throws Exception {
        conToken(post("/api/v1/portal/resenas/" + resenaDeLaPlataforma + "/respuesta"),
                tokenCandidata, "{\"texto\": \"" + RESPUESTA + "\"}")
                .andExpect(status().isCreated());
        conToken(post("/api/v1/portal/resenas/" + resenaDeLaPlataforma + "/respuesta"),
                tokenCandidata, "{\"texto\": \"" + RESPUESTA + "\"}")
                .andExpect(status().isConflict());
        // AC-35: otra cuenta del portal no la encuentra.
        conToken(post("/api/v1/portal/resenas/" + resenaDeLaPlataforma + "/respuesta"),
                tokenOtraCandidata, "{\"texto\": \"" + RESPUESTA + "\"}")
                .andExpect(status().isNotFound());

        // La empresa B la ve debajo de la reseña.
        conToken(get(resenas(contratadaPorAcme)), tokenAcme, null)
                .andExpect(jsonPath("$.resenas[?(@.id == " + resenaDeLaPlataforma
                        + ")].respuesta.texto").value(org.hamcrest.Matchers.contains(RESPUESTA)));

        // Llevamos su plazo al borde para ver que la edición lo reabre entero.
        jdbc.update("update respuesta_resena set editable_hasta = now() + interval '3 days' "
                + "where resena_id = ?", resenaDeLaPlataforma);
        conToken(put(resena(contratadaHace31)), tokenResponsable, cuerpo(3, OPINION + " Mejoró."))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.resena.editada").value(true));
        Timestamp hasta = jdbc.queryForObject("select editable_hasta from respuesta_resena "
                + "where resena_id = ?", Timestamp.class, resenaDeLaPlataforma);
        assertThat(hasta.toInstant()).isAfter(Instant.now().plus(Duration.ofDays(29)));
        assertThat(contar("select count(*) from aviso_portal where tipo = 'RESENA_EDITADA'"))
                .isEqualTo(1);
    }

    // ============ 7. Reportar y moderar ============

    @Test
    @Order(7)
    @DisplayName("AC-18, AC-20, AC-22 y AC-39: la persona reporta, ACME reporta la respuesta, y la plataforma oculta")
    void reportarYModerar() throws Exception {
        conToken(post("/api/v1/portal/resenas/" + resenaDeAcme + "/reporte"), tokenCandidata,
                "{\"motivo\": \"OTRO\", \"comentario\": \"\"}")
                .andExpect(status().isBadRequest());
        conToken(post("/api/v1/portal/resenas/" + resenaDeAcme + "/reporte"), tokenCandidata,
                "{\"motivo\": \"FALSA\"}")
                .andExpect(status().isNoContent());
        conToken(post("/api/v1/portal/resenas/" + resenaDeAcme + "/reporte"), tokenCandidata,
                "{\"motivo\": \"FALSA\"}")
                .andExpect(status().isConflict());
        // Sigue contando mientras se revisa.
        conToken(get("/api/v1/portal/perfil"), tokenCandidata, null)
                .andExpect(jsonPath("$.resenas.cantidad").value(2));

        // La empresa autora reporta la respuesta a SU reseña (AC-39). ACME no la escribió y no
        // podría: para ella esa contratación no existe (AC-40, cubierto en el servicio).
        conToken(post(resena(contratadaHace31) + "/respuesta/reporte"), tokenResponsable,
                "{\"motivo\": \"OFENSIVA\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.resena.respuesta.reporte").value("EN_REVISION"));

        // AC-22: ACME no modera, ni con el permiso concedido a mano.
        conToken(get("/api/v1/panel/resenas-reportadas"), tokenAcme, null)
                .andExpect(status().isForbidden());

        String pendientes = conToken(get("/api/v1/panel/resenas-reportadas"), tokenDireccion, null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andReturn().getResponse().getContentAsString();
        for (JsonNode tarjeta : json.readTree(pendientes)) {
            conToken(post("/api/v1/panel/resenas-reportadas/" + tarjeta.get("id").asLong()
                    + "/resolucion"), tokenDireccion,
                    "{\"decision\": \"OCULTAR\", \"nota\": \"Incumple las normas\"}")
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.estado").value("OCULTADA"));
        }

        // AC-20: la reseña de ACME deja de verse y de contar; ACME la ve atenuada.
        conToken(get("/api/v1/portal/perfil"), tokenCandidata, null)
                .andExpect(jsonPath("$.resenas.cantidad").value(1))
                .andExpect(jsonPath("$.resenas.promedio").value(3.0));
        conToken(get(resenas(contratadaPorAcme)), tokenAcme, null)
                .andExpect(jsonPath("$.miResena.estado").value("OCULTADA"))
                .andExpect(jsonPath("$.miResena.resena.notaOcultacion").value("Incumple las normas"))
                .andExpect(jsonPath("$.resenas.length()").value(1))
                // AC-39: la respuesta ocultada deja de verse para las demás empresas.
                .andExpect(jsonPath("$.resenas[0].respuesta").doesNotExist());
        conToken(get("/api/v1/portal/resenas"), tokenCandidata, null)
                .andExpect(jsonPath("$.resenas[0].respuesta.ocultada").value(true))
                .andExpect(jsonPath("$.resenas[0].puedeResponder").value(false));
        assertThat(contar("select count(*) from aviso_portal where tipo = "
                + "'REPORTE_RESENA_RESUELTO'")).isEqualTo(1);
        assertThat(contar("select count(*) from aviso_portal where tipo = "
                + "'RESPUESTA_RESENA_OCULTADA'")).isEqualTo(1);
    }

    // ============ 8. La descarga y el borrado de datos ============

    @Test
    @Order(8)
    @DisplayName("AC-29 y AC-30: la descarga lleva sus reseñas; el borrado se las lleva y la auditoría no guarda el texto")
    void descargaYBorrado() throws Exception {
        conToken(get("/api/v1/portal/perfil/descarga"), tokenCandidata, null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.misResenas.resenas.length()").value(1))
                .andExpect(jsonPath("$.misResenas.resenas[0].respuesta.ocultadaPorLaPlataforma")
                        .value(true))
                .andExpect(jsonPath("$.misResenas.reportes[0].estado").value("OCULTADA"));

        conToken(post("/api/v1/portal/solicitudes-borrado"), tokenCandidata,
                "{\"motivo\":\"Ya no quiero participar\"}").andExpect(status().isCreated());
        Long solicitud = jdbc.queryForObject("select max(id) from solicitud_borrado", Long.class);
        conToken(post("/api/v1/panel/solicitudes-borrado/" + solicitud + "/ejecucion"),
                tokenDireccion, "{\"motivo\":\"Lo pidió el titular\"}")
                .andExpect(status().isOk());

        assertThat(contar("select count(*) from resena where persona_id = " + personaCandidata))
                .isZero();
        assertThat(contar("select count(*) from respuesta_resena")).isZero();
        assertThat(contar("select count(*) from reporte_resena")).isZero();
        assertThat(contar("select count(*) from auditoria where accion = "
                + "'borrar_resenas_por_borrado_datos'")).isEqualTo(1);
        assertThat(contar("select count(*) from auditoria where coalesce(valor_nuevo::text, '') "
                + "|| coalesce(valor_anterior::text, '') like '%Gracias por la oportunidad%'"))
                .isZero();
    }

    // ============ Ayudantes ============

    private static String resenas(long postulacion) {
        return "/api/v1/panel/postulaciones/" + postulacion + "/resenas";
    }

    private static String resena(long postulacion) {
        return "/api/v1/panel/postulaciones/" + postulacion + "/resena";
    }

    private static String cuerpo(int estrellas, String texto) {
        return "{\"estrellas\": " + estrellas + ", \"texto\": \"" + texto + "\"}";
    }

    private String alcance(long organizacion, String rol, String permiso) {
        return jdbc.queryForObject("""
                select rp.alcance from rol_permiso rp
                  join rol r on r.id = rp.rol_id
                  join permiso p on p.id = rp.permiso_id
                 where r.organizacion_id = ? and r.codigo = ? and p.codigo = ?""",
                String.class, organizacion, rol, permiso);
    }

    /** Una persona del equipo con un rol, sembrada a mano: su token se emite aparte. */
    private long equipo(long organizacion, String rol, String nombre) {
        Long persona = jdbc.queryForObject("insert into persona (nombre, apellidos) values (?, "
                + "'Equipo') returning id", Long.class, nombre);
        Long usuario = jdbc.queryForObject("insert into usuario (organizacion_id, persona_id, "
                + "correo, es_equipo, es_activo) values (?, ?, ?, true, true) returning id",
                Long.class, organizacion, persona, nombre.toLowerCase() + "@equipo.pe");
        jdbc.update("insert into usuario_rol (usuario_id, rol_id) select ?, id from rol "
                + "where organizacion_id = ? and codigo = ?", usuario, organizacion, rol);
        return usuario;
    }

    private String crearCandidata(String correo, String nombre) throws Exception {
        mvc.perform(post("/api/v1/portal/cuentas")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                        {"nombre":"%s","apellidos":"Rojas","correo":"%s",
                         "contrasena":"unaClaveLarga123","ciudadUbigeo":"1501","aceptaPlataforma":true,
                         "aceptaFuturosContactos":false}""".formatted(nombre, correo)))
                .andExpect(status().isCreated());
        return leer(mvc.perform(post("/api/v1/portal/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"correo\":\"%s\",\"contrasena\":\"unaClaveLarga123\"}"
                                .formatted(correo)))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString(),
                "token");
    }

    /** Una vacante publicada, sembrada por la base: lo que se prueba aquí no es publicarla. */
    private long vacante(long organizacion, long responsable, String titulo) {
        Long area = jdbc.queryForObject("insert into area (organizacion_id, nombre) values (?, ?) "
                + "returning id", Long.class, organizacion, "Área " + titulo);
        Long puesto = jdbc.queryForObject("insert into puesto (organizacion_id, codigo, nombre, "
                + "nivel_puesto_codigo, familia_codigo) values (?, ?, ?, 'EJECUCION', "
                + "'OPERACIONES') returning id", Long.class, organizacion,
                "P_" + titulo.replace(' ', '_').toUpperCase(), titulo);
        Long solicitud = jdbc.queryForObject("insert into solicitud_talento (organizacion_id, "
                + "origen, urgencia, estado, area_id, resultado_principal, motivo, "
                + "consecuencia_no_contratar, analisis_capacidad, puesto_id) values (?, "
                + "'DIRECTA', 'NORMAL', 'CON_VACANTE', ?, 'r', 'm', 'c', 'a', ?) returning id",
                Long.class, organizacion, area, puesto);
        Long pesos = jdbc.queryForObject("select min(id) from version_pesos", Long.class);
        return jdbc.queryForObject("insert into vacante (organizacion_id, solicitud_talento_id, "
                + "puesto_id, titulo, descripcion, tipo_cierre, estado, version_pesos_id, "
                + "responsable_usuario_id) values (?, ?, ?, ?, 'Descripción', 'PERMANENTE', "
                + "'PUBLICADA', ?, ?) returning id", Long.class, organizacion, solicitud, puesto,
                titulo, pesos, responsable);
    }

    private long postulacion(long vacante, long organizacion, long usuario, String estado) {
        return jdbc.queryForObject("insert into postulacion (organizacion_id, usuario_id, "
                + "vacante_id, estado_codigo) values (?, ?, ?, ?) returning id", Long.class,
                organizacion, usuario, vacante, estado);
    }

    /**
     * Una contratación de hace tantos días: la transición a CONTRATADO se INSERTA con esa
     * fecha. La tabla no deja modificar una transición, pero sí insertarla.
     */
    private long contratada(long vacante, long organizacion, long usuario, int haceDias) {
        long id = postulacion(vacante, organizacion, usuario, "CONTRATADO");
        jdbc.update("insert into transicion_estado (postulacion_id, estado_nuevo_codigo, "
                + "es_sistema, ocurrida_en) values (?, 'CONTRATADO', true, "
                + "now() - make_interval(days => ?))", id, haceDias);
        return id;
    }

    private int contar(String sql) {
        Integer valor = jdbc.queryForObject(sql, Integer.class);
        return valor == null ? 0 : valor;
    }

    private ResultActions conToken(MockHttpServletRequestBuilder peticion, String token,
                                   String cuerpo) throws Exception {
        peticion.header("Authorization", "Bearer " + token).contentType(MediaType.APPLICATION_JSON);
        if (cuerpo != null) {
            peticion.content(cuerpo);
        }
        return mvc.perform(peticion);
    }

    private String leer(String cuerpoRespuesta, String campo) throws Exception {
        return json.readTree(cuerpoRespuesta).get(campo).asText();
    }
}
