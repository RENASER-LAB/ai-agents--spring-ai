package com.renaser.ai.ai_engine.integracion;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.renaser.ai.ai_engine.integracion.soporte.ImagenesDeContenedores;
import com.renaser.ai.ai_engine.seguridad.service.ColaDeRecuperaciones;

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
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.rabbitmq.RabbitMQContainer;

import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * «Me olvidé mi contraseña» contra Postgres de verdad, de punta a punta.
 *
 * <p>Lo que las pruebas con dobles ({@code RecuperacionClaveTest}) no pueden ver, y esta sí:
 * que la V61 siembra lo que dice, que el índice de «solo uno vivo» y el gasto condicional se
 * portan en la base como el código supone, que el enlace se lee de {@code correo_enviado}
 * (así se prueba en local, con el transporte de log) y que después se entra con la
 * contraseña nueva y no con la vieja.
 *
 * <p>⚠️ <b>La respuesta llega antes que el trabajo.</b> Pedir el enlace responde 202 y lo
 * demás pasa en segundo plano ({@link ColaDeRecuperaciones}). Por eso cada solicitud espera
 * a que la cola quede en reposo antes de mirar la base: sin eso, «no se creó ningún enlace»
 * sería indistinguible de «todavía no se creó».
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@Testcontainers
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@DisplayName("Recuperar la contraseña, de punta a punta")
public class FlujoRecuperacionClaveIT {

    @Container
    @ServiceConnection
    static PostgreSQLContainer postgres = new PostgreSQLContainer("pgvector/pgvector:pg16");

    @Container
    @ServiceConnection
    static RabbitMQContainer rabbit = new RabbitMQContainer(ImagenesDeContenedores.RABBITMQ);

    private static final String PORTAL = "http://portal.ejemplo.test";
    // Sin el /admin a propósito: el enlace del equipo tiene que caer en el panel igual
    private static final String PANEL = "http://panel.ejemplo.test";
    private static final String ENLACE_NO_SIRVE = "Este enlace ya no sirve. Pide uno nuevo.";
    private static final Pattern TOKEN = Pattern.compile("token=([A-Za-z0-9_-]+)");
    /** Dos puntos seguidos que no son unos puntos suspensivos: «S.A.C..». */
    private static final String PUNTO_REPETIDO = "[^.]\\.\\.(?!\\.)";
    private static final String DEMASIADO_LARGA = "La contraseña es demasiado larga. Usa como máximo "
            + "72 caracteres; las letras con tilde, la ñ y los emojis cuentan por más de uno.";

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
        registro.add("renaser.correo.transporte", () -> "log");
        registro.add("renaser.portal.url", () -> PORTAL);
        registro.add("renaser.panel.url", () -> PANEL);
    }

    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired ColaDeRecuperaciones cola;
    final ObjectMapper json = new ObjectMapper();

    static final String CAMILA = "camila@ejemplo.pe";
    static final String CLAVE_DE_CAMILA = "Demo12345!";
    static final String CLAVE_NUEVA_DE_CAMILA = "OtraClave2026";
    // En constantes y no tras el argumento del enlace: gitleaks toma por secreto cualquier
    // cadena que siga a una variable con «token» en el nombre
    static final String TERCERA_CLAVE_DE_CAMILA = "UnaTerceraClave2026";
    static final String CLAVE_CORTA = "corta";
    static final String CLAVE_CON_ESPACIO_AL_BORDE = " con-espacio-al-borde";
    static final String NOMBRE_ACME = "Acme S.A.C.";
    static final String NOMBRE_BETA = "Beta Logística";
    static final String CLAVE_NUEVA_DE_ANA_EN_ACME = "nueva-clave-de-acme-2026";
    static final String CLAVE_NUEVA_DE_ANA_EN_BETA = "nueva-clave-de-beta-2026";
    static final String ONCE_LETRAS = "once-letras";
    static long camilaId;
    static String primerToken;
    static String segundoToken;

    // ========================================================================
    // Lo que siembra la V61
    // ========================================================================

    @Test
    @Order(1)
    @DisplayName("La V61 siembra los dos correos y los tres parámetros en la plataforma")
    void laMigracionSiembraLoQueDice() throws Exception {
        assertThat(contar("""
                select count(*) from plantilla_correo pc join organizacion o on o.id = pc.organizacion_id
                 where o.es_plataforma and pc.es_activa
                   and pc.codigo in ('RECUPERAR_CLAVE_CANDIDATO', 'RECUPERAR_CLAVE_EQUIPO')""")).isEqualTo(2);
        assertThat(jdbc.queryForObject("""
                select cuerpo from plantilla_correo pc join organizacion o on o.id = pc.organizacion_id
                 where o.es_plataforma and pc.codigo = 'RECUPERAR_CLAVE_EQUIPO'""", String.class))
                .contains("{{enlace}}").contains("{{vence}}").contains("{{nombre_empresa}}");
        // F-02 · la plataforma se llama «… S.A.C.», con punto: ninguna frase acaba en el nombre
        assertThat(jdbc.queryForList("""
                select pc.asunto || ' ' || pc.cuerpo from plantilla_correo pc
                  join organizacion o on o.id = pc.organizacion_id
                 where o.es_plataforma
                   and pc.codigo in ('RECUPERAR_CLAVE_CANDIDATO', 'RECUPERAR_CLAVE_EQUIPO')""", String.class))
                .hasSize(2)
                .allSatisfy(texto -> assertThat(texto).doesNotContain("{{nombre_empresa}}."));
        assertThat(jdbc.queryForObject("""
                select valor from parametro p join organizacion o on o.id = p.organizacion_id
                 where o.es_plataforma and p.codigo = 'minutos_vida_recuperacion'""", String.class))
                .isEqualTo("60");

        crearCandidato("Camila", CAMILA, CLAVE_DE_CAMILA);
        camilaId = idDe(CAMILA);
    }

    // ========================================================================
    // Candidato: pedir, elegir, entrar
    // ========================================================================

    @Test
    @Order(2)
    @DisplayName("AC-01 · AC-19 · pedir el enlace responde 202 vacío; el correo queda NO_ENVIADO "
            + "con el texto entero y un enlace a /restablecer del portal")
    void unCandidatoPideElEnlace() throws Exception {
        pedir("/api/v1/portal/auth/recuperacion", CAMILA);

        assertThat(contar("select count(*) from recuperacion_clave where usuario_id = " + camilaId))
                .isEqualTo(1);
        String cuerpo = ultimoCorreo(camilaId, "RECUPERAR_CLAVE_CANDIDATO");
        assertThat(cuerpo).contains(PORTAL + "/restablecer?token=").doesNotContain("/admin/")
                .contains("hora de Lima").doesNotContain("{{");
        // F-02 · con el nombre de verdad de la plataforma, sin «S.A.C..»
        String plataforma = jdbc.queryForObject(
                "select nombre from organizacion where es_plataforma", String.class);
        assertThat(cuerpo).contains(plataforma).doesNotContain(plataforma + ".")
                .doesNotContainPattern(PUNTO_REPETIDO);
        assertThat(jdbc.queryForObject("""
                select estado_entrega from correo_enviado
                 where usuario_id = ? and plantilla_correo_codigo = 'RECUPERAR_CLAVE_CANDIDATO'
                 order by id desc limit 1""", String.class, camilaId)).isEqualTo("NO_ENVIADO");

        primerToken = tokenDe(cuerpo);
        assertThat(jdbc.queryForObject("select token_hash from recuperacion_clave where usuario_id = ?",
                String.class, camilaId)).hasSize(64).isNotEqualTo(primerToken);
    }

    @Test
    @Order(3)
    @DisplayName("AC-11 · AC-13 · una contraseña corta, con espacios en el borde o igual a la "
            + "actual se rechaza, y el enlace sigue sirviendo")
    void lasReglasNoGastanElEnlace() throws Exception {
        restablecer("/api/v1/portal/auth/restablecer", primerToken, CLAVE_CORTA)
                .andExpect(status().isBadRequest());
        restablecer("/api/v1/portal/auth/restablecer", primerToken, CLAVE_CON_ESPACIO_AL_BORDE)
                .andExpect(status().isBadRequest());
        restablecer("/api/v1/portal/auth/restablecer", primerToken, CLAVE_DE_CAMILA)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("Elige una contraseña distinta a la anterior"));
        // F-01 · más de 72 bytes: el error del campo, en español, y no el de BCrypt
        restablecer("/api/v1/portal/auth/restablecer", primerToken, "x".repeat(73))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.contrasena").value(DEMASIADO_LARGA))
                .andExpect(content().string(org.hamcrest.Matchers.not(
                        org.hamcrest.Matchers.containsString("bytes"))));
        restablecer("/api/v1/portal/auth/restablecer", primerToken, "ñ".repeat(40))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.contrasena").value(DEMASIADO_LARGA));

        assertThat(contar("select count(*) from recuperacion_clave where usuario_id = " + camilaId
                + " and usado_en is null and invalidado_en is null")).isEqualTo(1);
    }

    @Test
    @Order(4)
    @DisplayName("AC-10 · pedir otro deja sin efecto el primero: su enlace dice que no sirve")
    void elSegundoApagaAlPrimero() throws Exception {
        pedir("/api/v1/portal/auth/recuperacion", CAMILA);

        segundoToken = tokenDe(ultimoCorreo(camilaId, "RECUPERAR_CLAVE_CANDIDATO"));
        assertThat(segundoToken).isNotEqualTo(primerToken);
        assertThat(contar("select count(*) from recuperacion_clave where usuario_id = " + camilaId
                + " and usado_en is null and invalidado_en is null"))
                .as("solo uno vivo").isEqualTo(1);

        restablecer("/api/v1/portal/auth/restablecer", primerToken, CLAVE_NUEVA_DE_CAMILA)
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.detail").value(ENLACE_NO_SIRVE));
    }

    @Test
    @Order(5)
    @DisplayName("AC-06 · AC-07 · AC-14 · bloqueada por cinco intentos, cambia la contraseña con el "
            + "segundo enlace y entra al momento con la nueva; con la vieja, no")
    void cambiaYEntraConLaNueva() throws Exception {
        for (int i = 0; i < 5; i++) {
            login("/api/v1/portal/auth/login", CAMILA, "no-es-esta-" + i);
        }
        login("/api/v1/portal/auth/login", CAMILA, CLAVE_DE_CAMILA)
                .andExpect(status().isTooManyRequests());

        restablecer("/api/v1/portal/auth/restablecer", segundoToken, CLAVE_NUEVA_DE_CAMILA)
                .andExpect(status().isNoContent())
                .andExpect(content().string(""));

        login("/api/v1/portal/auth/login", CAMILA, CLAVE_NUEVA_DE_CAMILA)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").isNotEmpty());
        login("/api/v1/portal/auth/login", CAMILA, CLAVE_DE_CAMILA)
                .andExpect(status().isUnauthorized());
    }

    @Test
    @Order(6)
    @DisplayName("AC-08 · el mismo enlace otra vez: no sirve y la contraseña no cambia")
    void elEnlaceUsadoNoVuelveASerir() throws Exception {
        restablecer("/api/v1/portal/auth/restablecer", segundoToken, TERCERA_CLAVE_DE_CAMILA)
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.detail").value(ENLACE_NO_SIRVE));

        login("/api/v1/portal/auth/login", CAMILA, CLAVE_NUEVA_DE_CAMILA).andExpect(status().isOk());
    }

    @Test
    @Order(7)
    @DisplayName("AC-18 · cada solicitud y cada cambio quedan en la auditoría con la cuenta, y "
            + "ningún token aparece en ella")
    void laAuditoriaTieneCuentaYMomentoYNingunToken() throws Exception {
        assertThat(contar("""
                select count(*) from auditoria
                 where accion = 'solicitar_recuperacion_clave' and entidad = 'usuario'
                   and entidad_id = %d and ocurrida_en is not null""".formatted(camilaId))).isEqualTo(2);
        assertThat(contar("""
                select count(*) from auditoria
                 where accion = 'restablecer_clave' and entidad = 'usuario' and entidad_id = %d"""
                .formatted(camilaId))).isEqualTo(1);
        List<String> textos = jdbc.queryForList("""
                select coalesce(valor_nuevo::text, '') || coalesce(valor_anterior::text, '')
                  || coalesce(motivo, '') from auditoria""", String.class);
        assertThat(textos).noneMatch(t -> t.contains(primerToken) || t.contains(segundoToken));
    }

    @Test
    @Order(8)
    @DisplayName("AC-09 · un enlace vencido dice lo mismo que uno usado")
    void unEnlaceVencidoNoSirve() throws Exception {
        pedir("/api/v1/portal/auth/recuperacion", CAMILA);
        String tercero = tokenDe(ultimoCorreo(camilaId, "RECUPERAR_CLAVE_CANDIDATO"));
        // Vencido hace una hora: se mueven las dos fechas para respetar el CHECK
        jdbc.update("""
                update recuperacion_clave
                   set creado_en = now() - interval '2 hours', vence_en = now() - interval '1 hour'
                 where usuario_id = ? and usado_en is null and invalidado_en is null""", camilaId);

        restablecer("/api/v1/portal/auth/restablecer", tercero, TERCERA_CLAVE_DE_CAMILA)
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.detail").value(ENLACE_NO_SIRVE));
    }

    @Test
    @Order(9)
    @DisplayName("AC-15 · cuatro solicitudes seguidas: tres enlaces y tres correos, la cuarta nada")
    void laCuartaDeLaHoraNoHaceNada() throws Exception {
        crearCandidato("Rosa", "rosa@ejemplo.pe", "Demo12345!");
        long rosaId = idDe("rosa@ejemplo.pe");

        for (int i = 0; i < 4; i++) {
            mvc.perform(post("/api/v1/portal/auth/recuperacion")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"correo\":\"rosa@ejemplo.pe\"}"))
                    .andExpect(status().isAccepted())
                    .andExpect(content().string(""));
        }
        esperarLaCola();

        assertThat(contar("select count(*) from recuperacion_clave where usuario_id = " + rosaId))
                .isEqualTo(3);
        assertThat(contar("""
                select count(*) from correo_enviado
                 where usuario_id = %d and plantilla_correo_codigo = 'RECUPERAR_CLAVE_CANDIDATO'"""
                .formatted(rosaId))).isEqualTo(3);
    }

    @Test
    @Order(10)
    @DisplayName("AC-02 · AC-03 · AC-05 · sin cuenta, desactivada, de carga masiva o de la otra "
            + "puerta: la misma respuesta y ningún enlace ni correo")
    void nadieMasRecibeNada() throws Exception {
        crearCandidato("Pedro", "pedro.gomez@cv-convocatoria.local", "Demo12345!");
        crearCandidato("Lucía", "lucia@ejemplo.pe", "Demo12345!");
        jdbc.update("update usuario set es_activo = false where correo = 'lucia@ejemplo.pe'");
        int enlacesAntes = contar("select count(*) from recuperacion_clave");
        int correosAntes = contar("select count(*) from correo_enviado");

        pedir("/api/v1/portal/auth/recuperacion", "nadie@ejemplo.pe");
        pedir("/api/v1/portal/auth/recuperacion", "pedro.gomez@cv-convocatoria.local");
        pedir("/api/v1/portal/auth/recuperacion", "lucia@ejemplo.pe");
        // Un candidato pedido desde el panel
        pedir("/api/v1/panel/auth/recuperacion", CAMILA);

        assertThat(contar("select count(*) from recuperacion_clave")).isEqualTo(enlacesAntes);
        assertThat(contar("select count(*) from correo_enviado")).isEqualTo(correosAntes);
    }

    // ========================================================================
    // Equipo: el mismo correo en dos empresas
    // ========================================================================

    @Test
    @Order(11)
    @DisplayName("AC-04 · AC-05 · AC-16 · AC-17 · un correo con cuenta en dos empresas recibe dos "
            + "enlaces al panel, cada uno con su empresa, y cada uno cambia solo su cuenta")
    void dosEmpresasDosEnlaces() throws Exception {
        String tokenPlataforma = leer(mvc.perform(post("/api/v1/panel/auth/dev-login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"usuarioRenaserOsId\":\"dev-recuperacion\"}"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString(), "token");
        long acme = darDeAltaConAna(tokenPlataforma, NOMBRE_ACME, "ACME", "clave-de-acme-2026");
        long beta = darDeAltaConAna(tokenPlataforma, NOMBRE_BETA, "BETA", "clave-de-beta-2026");
        long anaEnAcme = jdbc.queryForObject(
                "select id from usuario where organizacion_id = ? and correo = 'ana@dos.pe'", Long.class, acme);
        long anaEnBeta = jdbc.queryForObject(
                "select id from usuario where organizacion_id = ? and correo = 'ana@dos.pe'", Long.class, beta);
        // A Beta le falta su plantilla: el correo tiene que salir con la de la plataforma
        jdbc.update("""
                update plantilla_correo set es_activa = false
                 where organizacion_id = ? and codigo = 'RECUPERAR_CLAVE_EQUIPO'""", beta);

        // Pedido desde el portal no llega nada: Ana no es candidata
        pedir("/api/v1/portal/auth/recuperacion", "ana@dos.pe");
        assertThat(contar("select count(*) from recuperacion_clave where usuario_id in ("
                + anaEnAcme + ", " + anaEnBeta + ")")).isZero();

        pedir("/api/v1/panel/auth/recuperacion", "Ana@Dos.pe");

        String correoAcme = ultimoCorreo(anaEnAcme, "RECUPERAR_CLAVE_EQUIPO");
        String correoBeta = ultimoCorreo(anaEnBeta, "RECUPERAR_CLAVE_EQUIPO");
        assertThat(correoAcme).contains(PANEL + "/admin/restablecer?token=").contains(NOMBRE_ACME)
                .doesNotContain(NOMBRE_BETA).doesNotContainPattern(PUNTO_REPETIDO);
        assertThat(correoBeta).contains(PANEL + "/admin/restablecer?token=").contains(NOMBRE_BETA)
                .doesNotContain(NOMBRE_ACME);
        String tokenAcme = tokenDe(correoAcme);
        String tokenBeta = tokenDe(correoBeta);
        String hashAcmeAntes = hashDe(anaEnAcme);
        String hashBetaAntes = hashDe(anaEnBeta);

        // El de Acme cambia solo la de Acme
        restablecer("/api/v1/panel/auth/restablecer", tokenAcme, CLAVE_NUEVA_DE_ANA_EN_ACME)
                .andExpect(status().isNoContent());
        assertThat(hashDe(anaEnAcme)).isNotEqualTo(hashAcmeAntes);
        assertThat(hashDe(anaEnBeta)).isEqualTo(hashBetaAntes);

        // El de Beta no sirve en la puerta del portal, y esa prueba no lo gasta
        restablecer("/api/v1/portal/auth/restablecer", tokenBeta, CLAVE_NUEVA_DE_ANA_EN_BETA)
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.detail").value(ENLACE_NO_SIRVE));
        // En el panel, 12 como mínimo: 11 no pasa y tampoco lo gasta
        restablecer("/api/v1/panel/auth/restablecer", tokenBeta, ONCE_LETRAS)
                .andExpect(status().isBadRequest());
        restablecer("/api/v1/panel/auth/restablecer", tokenBeta, CLAVE_NUEVA_DE_ANA_EN_BETA)
                .andExpect(status().isNoContent());
        assertThat(hashDe(anaEnBeta)).isNotEqualTo(hashBetaAntes);

        login("/api/v1/panel/auth/login", "ana@dos.pe", CLAVE_NUEVA_DE_ANA_EN_ACME)
                .andExpect(status().isOk());
        login("/api/v1/panel/auth/login", "ana@dos.pe", "clave-de-acme-2026")
                .andExpect(status().isUnauthorized());
    }

    @Test
    @Order(12)
    @DisplayName("F-01 · en el panel, más de 72 bytes se rechaza sin gastar el enlace; 72 justos "
            + "se guardan y con ellos se entra")
    void elTopeDeBcryptEnElPanel() throws Exception {
        long anaEnAcme = jdbc.queryForObject("""
                select u.id from usuario u join organizacion o on o.id = u.organizacion_id
                 where o.codigo = 'ACME' and u.correo = 'ana@dos.pe'""", Long.class);
        pedir("/api/v1/panel/auth/recuperacion", "ana@dos.pe");
        String token = tokenDe(ultimoCorreo(anaEnAcme, "RECUPERAR_CLAVE_EQUIPO"));
        String hashAntes = hashDe(anaEnAcme);

        restablecer("/api/v1/panel/auth/restablecer", token, "ñ".repeat(40))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.contrasena").value(DEMASIADO_LARGA));
        assertThat(hashDe(anaEnAcme)).isEqualTo(hashAntes);

        // 36 «ñ»: 72 bytes justos
        String justa = "ñ".repeat(36);
        restablecer("/api/v1/panel/auth/restablecer", token, justa)
                .andExpect(status().isNoContent());
        assertThat(hashDe(anaEnAcme)).isNotEqualTo(hashAntes);
        login("/api/v1/panel/auth/login", "ana@dos.pe", justa).andExpect(status().isOk());
    }

    // ========================================================================

    /** Da de alta una empresa con Ana de administradora y canjea su invitación. */
    private long darDeAltaConAna(String tokenPlataforma, String nombre, String codigo, String clave)
            throws Exception {
        String respuesta = mvc.perform(post("/api/v1/panel/plataforma/empresas")
                        .header("Authorization", "Bearer " + tokenPlataforma)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"nombre": "%s", "codigo": "%s", "correoAdministrador": "ana@dos.pe"}"""
                                .formatted(nombre, codigo)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        long id = json.readTree(respuesta).get("id").asLong();
        String url = json.readTree(respuesta).get("urlInvitacion").asText();
        mvc.perform(post("/api/v1/panel/auth/invitacion")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"token": "%s", "nombre": "Ana", "apellidos": "Torres", "contrasena": "%s"}"""
                                .formatted(url.substring(url.indexOf("token=") + 6), clave)))
                .andExpect(status().isOk());
        return id;
    }

    private void crearCandidato(String nombre, String correo, String clave) throws Exception {
        mvc.perform(post("/api/v1/portal/cuentas")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"nombre": "%s", "apellidos": "Prueba", "correo": "%s",
                                 "contrasena": "%s", "ciudadUbigeo": "1501",
                                 "aceptaPlataforma": true, "aceptaFuturosContactos": false}"""
                                .formatted(nombre, correo, clave)))
                .andExpect(status().isCreated());
    }

    /** Pide el enlace, comprueba que la respuesta es la de siempre y espera al trabajo. */
    private void pedir(String ruta, String correo) throws Exception {
        mvc.perform(post(ruta)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"correo\":\"" + correo + "\"}"))
                .andExpect(status().isAccepted())
                .andExpect(content().string(""));
        esperarLaCola();
    }

    private void esperarLaCola() throws InterruptedException {
        long limite = System.nanoTime() + TimeUnit.SECONDS.toNanos(20);
        while (!cola.enReposo()) {
            if (System.nanoTime() > limite) {
                fail("La solicitud de contraseña nueva no terminó en 20 segundos");
            }
            Thread.sleep(20);
        }
    }

    private ResultActions restablecer(String ruta, String token, String contrasena) throws Exception {
        return mvc.perform(post(ruta)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(java.util.Map.of("token", token, "contrasena", contrasena))));
    }

    private ResultActions login(String ruta, String correo, String contrasena) throws Exception {
        return mvc.perform(post(ruta)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(java.util.Map.of("correo", correo, "contrasena", contrasena))));
    }

    private String ultimoCorreo(long usuarioId, String codigo) {
        return jdbc.queryForObject("""
                select cuerpo from correo_enviado
                 where usuario_id = ? and plantilla_correo_codigo = ?
                 order by id desc limit 1""", String.class, usuarioId, codigo);
    }

    private static String tokenDe(String cuerpo) {
        Matcher m = TOKEN.matcher(cuerpo);
        if (!m.find()) {
            fail("El correo no lleva ningún enlace con token: " + cuerpo);
        }
        return m.group(1);
    }

    private long idDe(String correo) {
        return jdbc.queryForObject("select id from usuario where correo = ?", Long.class, correo);
    }

    private String hashDe(long usuarioId) {
        return jdbc.queryForObject("select contrasena_hash from usuario where id = ?", String.class, usuarioId);
    }

    private int contar(String sql) {
        Integer valor = jdbc.queryForObject(sql, Integer.class);
        return valor == null ? 0 : valor;
    }

    private String leer(String cuerpoRespuesta, String campo) throws Exception {
        return json.readTree(cuerpoRespuesta).get(campo).asText();
    }
}
