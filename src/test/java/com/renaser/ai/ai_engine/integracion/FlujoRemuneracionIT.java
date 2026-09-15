package com.renaser.ai.ai_engine.integracion;

import com.fasterxml.jackson.databind.JsonNode;
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
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.rabbitmq.RabbitMQContainer;

import java.math.BigDecimal;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * El trato del sueldo de la V55 y la campana de la V56, de punta a punta y contra la base
 * de verdad.
 *
 * <p>Existe porque lo que estas dos migraciones prometen no se puede comprobar con dobles.
 * Las pruebas unitarias de {@code RemuneracionDeLaVacanteTest} y
 * {@code ServicioPostulacionPortalImplTest} ya cubren las decisiones del servicio con los
 * repositorios simulados, y eso deja fuera justo la mitad que se rompe sola:
 *
 * <ul>
 *   <li>Las restricciones de la V55. Un mock acepta cualquier fila; la base no. Una
 *       postulación con monto y sin moneda, o una vacante FIJA sin cifra, son estados que el
 *       código no debería poder producir — y esta prueba comprueba que, si algún día los
 *       produce, la base los para.
 *   <li>La tabla de la V56 con sus claves foráneas y sus columnas obligatorias de verdad.
 *   <li>El contrato HTTP completo: que negarse a postular sin declarar el sueldo sea un 400
 *       con la cifra de la vacante dentro, y no un 500 mudo ni un 201 silencioso.
 *   <li>Que el correo, el aviso y la auditoría salgan del MISMO gesto y queden escritos en
 *       las tres tablas, que es lo que nadie puede verificar mirando un solo servicio.
 * </ul>
 *
 * <p>Los pasos van en orden y comparten estado: la vacante que publica el primero es sobre la
 * que postula el tercero y a la que le cambian el sueldo el quinto.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@DisplayName("El sueldo se dice de los dos lados, y cambiarlo se avisa")
public class FlujoRemuneracionIT {

    @Container
    @ServiceConnection
    static PostgreSQLContainer postgres = new PostgreSQLContainer("pgvector/pgvector:pg16");

    @Container
    @ServiceConnection
    static RabbitMQContainer rabbit = new RabbitMQContainer(ImagenesDeContenedores.RABBITMQ);

    @DynamicPropertySource
    static void propiedades(DynamicPropertyRegistry registro) {
        // El broker es el contenedor y habla en claro: sin esto manda el CloudAMQP con TLS
        // que cada uno tenga en su application-secrets.yaml.
        registro.add("spring.rabbitmq.ssl.enabled", () -> "false");
        registro.add("spring.rabbitmq.virtual-host", () -> "/");
        registro.add("app.archivos.tipo", () -> "memoria");
        registro.add("app.seguridad.jwt-secreto",
                () -> "clave-de-pruebas-suficientemente-larga-para-hmac-256-bits");
        registro.add("app.seguridad.dev-login-activo", () -> "true");
        registro.add("spring.ai.deepseek.api-key", () -> "clave-de-pruebas-no-se-usa");
        // Aquí no se prueba la calificación con IA, y encendida cada postulación intentaría
        // hablar con DeepSeek con una clave de mentira.
        registro.add("renaser.ai.calificacion.habilitada", () -> "false");
    }

    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    final ObjectMapper json = new ObjectMapper();

    /** Lo que la vacante de la banda dice pagar, escrito como lo lee el candidato. */
    private static final String BANDA = "S/ 3 000 a 4 000";
    /** Y lo que dirá cuando se cambie a un monto fijo. */
    private static final String FIJO = "S/ 4 500";

    private static final String CORREO_CANDIDATO = "sueldo.candidato@ejemplo.pe";
    private static final String CLAVE = "Demo12345!";

    static String tokenEquipo;
    static String tokenCandidato;
    static long usuarioCandidatoId;
    static long vacanteConBanda;
    static long vacanteOculta;
    static long postulacionConBanda;
    static Instant marcaTrasElCambio;

    // ============ Preparar el terreno ============

    @Test
    @Order(1)
    @DisplayName("una vacante que publica su banda la enseña ya escrita, y dice que es un RANGO")
    void laVacantePublicaSuBanda() throws Exception {
        tokenEquipo = leer(mvc.perform(post("/api/v1/panel/auth/dev-login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"usuarioRenaserOsId\":\"dev-remuneracion\"}"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(), "token");

        jdbc.update("INSERT INTO area (organizacion_id, nombre, es_activa) VALUES (1, 'Tecnología', true)");
        Long areaId = jdbc.queryForObject("SELECT id FROM area LIMIT 1", Long.class);

        long puestoId = Long.parseLong(leer(conToken(post("/api/v1/panel/puestos"), tokenEquipo, """
                {"nombre": "Desarrollador web",
                 "nivelPuestoCodigo": "EJECUCION", "familiaCodigo": "TECNOLOGIA"}""")
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString(), "id"));

        // Los dos instrumentos que exige publicar, armados una vez y reutilizados por las dos
        // vacantes: lo que se prueba aquí es el dinero, no la evaluación.
        Long plantillaEvaluacionId = jdbc.queryForObject(
                "select id from plantilla_evaluacion where nivel_puesto_codigo = 'EJECUCION'", Long.class);
        Long versionPruebaId = armarUnaPruebaValida();

        vacanteConBanda = publicarVacante(areaId, puestoId, plantillaEvaluacionId, versionPruebaId,
                "Desarrollador web",
                """
                "remuneracion": {"tipo": "RANGO", "min": 3000, "max": 4000, "moneda": "PEN"},""");

        // Lo que ve el candidato: el tipo, los montos sueltos y la frase ya armada. El texto
        // se comprueba carácter a carácter a propósito — el separador de miles es un espacio
        // y los céntimos no se escriben cuando son cero, y las dos cosas se han decidido.
        mvc.perform(get("/api/v1/portal/vacantes/" + vacanteConBanda))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.remuneracion.tipo").value("RANGO"))
                .andExpect(jsonPath("$.remuneracion.texto").value(BANDA))
                .andExpect(jsonPath("$.remuneracion.min").value(3000))
                .andExpect(jsonPath("$.remuneracion.max").value(4000))
                .andExpect(jsonPath("$.remuneracion.moneda").value("PEN"))
                // Recién creada nadie le ha cambiado el sueldo: sin marca, el portal no pinta
                // «actualizado el …» sobre un número que siempre fue ese.
                .andExpect(jsonPath("$.remuneracion.actualizadaEn").doesNotExist());
    }

    // ============ El trato, en las dos direcciones ============

    @Test
    @Order(2)
    @DisplayName("si la vacante enseña el sueldo, postular sin declarar el propio es un 400 que dice la cifra")
    void sinDeclararElPropioNoHayPostulacion() throws Exception {
        crearLaCuentaDelCandidato();

        // El 400 y no un 500: es un dato que falta de quien llama, no una avería. Y el
        // mensaje lleva dentro lo que paga la vacante, porque «falta un campo» no le explica
        // a nadie por qué de repente se lo piden.
        mvc.perform(multipart("/api/v1/portal/postulaciones")
                        .file(unCurriculum())
                        .param("vacanteId", String.valueOf(vacanteConBanda))
                        .param("resultadoOrgulloso", "Reduje a la mitad el tiempo de carga del portal")
                        .param("aceptaTratamiento", "true")
                        .header("Authorization", "Bearer " + tokenCandidato))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value(org.hamcrest.Matchers.containsString(BANDA)));

        // Y no dejó nada a medias: sin pretensión no hay postulación, ni siquiera vacía.
        assertThat(contar("select count(*) from postulacion where vacante_id = " + vacanteConBanda))
                .isZero();
    }

    @Test
    @Order(3)
    @DisplayName("la pretensión declarada se guarda con la moneda normalizada y el día en que se dijo")
    void laPretensionSeGuardaNormalizada() throws Exception {
        // La moneda llega en minúsculas, como la escribiría cualquier cliente que no sepa la
        // convención. Lo que se guarda es «PEN»: sin normalizar, comparar dos pretensiones
        // exigiría un diccionario de grafías del mismo dinero.
        mvc.perform(multipart("/api/v1/portal/postulaciones")
                        .file(unCurriculum())
                        .param("vacanteId", String.valueOf(vacanteConBanda))
                        .param("resultadoOrgulloso", "Reduje a la mitad el tiempo de carga del portal")
                        .param("aceptaTratamiento", "true")
                        .param("pretensionMonto", "3800")
                        .param("pretensionMoneda", "pen")
                        .header("Authorization", "Bearer " + tokenCandidato))
                .andExpect(status().isCreated());

        postulacionConBanda = jdbc.queryForObject(
                "select id from postulacion where vacante_id = ?", Long.class, vacanteConBanda);

        assertThat(jdbc.queryForObject(
                "select pretension_moneda from postulacion where id = ?", String.class, postulacionConBanda))
                .isEqualTo("PEN");
        assertThat(jdbc.queryForObject(
                "select pretension_monto from postulacion where id = ?", BigDecimal.class, postulacionConBanda))
                .isEqualByComparingTo("3800");
        // Los tres van juntos o los tres van vacíos (restricción de la V55): sin la fecha, la
        // cifra sería un número del que no se sabe cuándo se dijo.
        assertThat(jdbc.queryForObject(
                "select pretension_declarada_en from postulacion where id = ?", Instant.class, postulacionConBanda))
                .isNotNull();

        // Y en el portal vuelve escrita, que es lo que el candidato relee en «Mis procesos».
        mvc.perform(get("/api/v1/portal/postulaciones")
                        .header("Authorization", "Bearer " + tokenCandidato))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].miPretension.monto").value(3800))
                .andExpect(jsonPath("$[0].miPretension.moneda").value("PEN"))
                .andExpect(jsonPath("$[0].miPretension.texto").value("S/ 3 800"));
    }

    @Test
    @Order(4)
    @DisplayName("con el sueldo oculto no se exige nada, y la cifra que llegue igualmente se ignora")
    void laVacanteOcultaIgnoraLoQueLlegue() throws Exception {
        Long areaId = jdbc.queryForObject("SELECT id FROM area LIMIT 1", Long.class);
        Long puestoId = jdbc.queryForObject(
                "select id from puesto where nombre = 'Desarrollador web'", Long.class);
        Long plantillaEvaluacionId = jdbc.queryForObject(
                "select id from plantilla_evaluacion where nivel_puesto_codigo = 'EJECUCION'", Long.class);
        Long versionPruebaId = jdbc.queryForObject(
                "select max(id) from version_plantilla_prueba", Long.class);

        vacanteOculta = publicarVacante(areaId, puestoId, plantillaEvaluacionId, versionPruebaId,
                "Analista de datos", "");

        // Lo dice en voz alta y no con un hueco: el hueco se lee como un fallo de carga, y
        // además es el dato que explica por qué no le van a pedir la suya.
        mvc.perform(get("/api/v1/portal/vacantes/" + vacanteOculta))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.remuneracion.tipo").value("OCULTA"))
                .andExpect(jsonPath("$.remuneracion.texto").value("No la publica"))
                .andExpect(jsonPath("$.remuneracion.min").doesNotExist());

        // Manda una cifra de todas formas —un cliente viejo, un formulario que no se enteró—
        // y la postulación entra, pero la cifra NO se guarda. Aceptarla mientras la empresa
        // calla la suya es justo el desequilibrio que la V55 viene a romper.
        mvc.perform(multipart("/api/v1/portal/postulaciones")
                        .file(unCurriculum())
                        .param("vacanteId", String.valueOf(vacanteOculta))
                        .param("resultadoOrgulloso", "Monté el tablero de indicadores del área")
                        .param("aceptaTratamiento", "true")
                        .param("pretensionMonto", "5000")
                        .param("pretensionMoneda", "PEN")
                        .header("Authorization", "Bearer " + tokenCandidato))
                .andExpect(status().isCreated());

        Long postulacionOculta = jdbc.queryForObject(
                "select id from postulacion where vacante_id = ?", Long.class, vacanteOculta);
        assertThat(jdbc.queryForObject(
                "select pretension_monto from postulacion where id = ?", BigDecimal.class, postulacionOculta))
                .as("la vacante ocultaba el sueldo: lo que mandó se ignora")
                .isNull();
        assertThat(jdbc.queryForObject(
                "select pretension_moneda from postulacion where id = ?", String.class, postulacionOculta))
                .isNull();
        assertThat(jdbc.queryForObject(
                "select pretension_declarada_en from postulacion where id = ?", Instant.class, postulacionOculta))
                .isNull();
    }

    // ============ Cambiar el sueldo se cuenta ============

    @Test
    @Order(5)
    @DisplayName("cambiar el sueldo publicado deja aviso, correo y auditoría con el motivo, y mueve la marca")
    void cambiarElSueldoLeLlegaAQuienPostulo() throws Exception {
        assertThat(jdbc.queryForObject(
                "select remuneracion_actualizada_en from vacante where id = ?", Instant.class, vacanteConBanda))
                .as("nadie le ha cambiado el sueldo todavía")
                .isNull();

        conToken(post("/api/v1/panel/vacantes/" + vacanteConBanda + "/remuneracion"), tokenEquipo, """
                {"remuneracion": {"tipo": "FIJA", "min": 4500, "moneda": "PEN"},
                 "motivo": "Se cerró el presupuesto del puesto con Dirección"}""")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.antes").value(BANDA))
                .andExpect(jsonPath("$.ahora").value(FIJO))
                .andExpect(jsonPath("$.candidatosAvisados").value(1));

        // 1. El aviso dentro del portal, que es lo que la V56 vino a traer.
        assertThat(contar("""
                select count(*) from aviso_portal
                where usuario_id = %d and vacante_id = %d
                  and tipo = 'REMUNERACION_ACTUALIZADA' and leido_en is null
                """.formatted(usuarioCandidatoId, vacanteConBanda)))
                .isEqualTo(1);
        // Y el texto queda ESCRITO, no reconstruido: un aviso que se rearmara al leerlo diría
        // el sueldo de hoy y dejaría de ser la noticia de aquel día.
        String cuerpo = jdbc.queryForObject(
                "select cuerpo from aviso_portal where vacante_id = ?", String.class, vacanteConBanda);
        assertThat(cuerpo).contains(BANDA).contains(FIJO);

        // 2. El correo, que es la otra mitad del mismo hecho.
        assertThat(contar("""
                select count(*) from correo_enviado
                where usuario_id = %d and plantilla_correo_codigo = 'REMUNERACION_ACTUALIZADA'
                """.formatted(usuarioCandidatoId)))
                .isEqualTo(1);

        // 3. La auditoría, con el porqué. Sin el motivo nadie puede contestar «¿por qué le
        // dijimos a esta gente que el sueldo cambió?» con algo más que una marca de tiempo.
        assertThat(contar("""
                select count(*) from auditoria
                where accion = 'actualizar_remuneracion' and entidad = 'vacante' and entidad_id = %d
                  and motivo = 'Se cerró el presupuesto del puesto con Dirección'
                """.formatted(vacanteConBanda)))
                .isEqualTo(1);

        // 4. Y la marca de cuándo, que es lo que el portal pinta sobre el monto nuevo.
        marcaTrasElCambio = jdbc.queryForObject(
                "select remuneracion_actualizada_en from vacante where id = ?", Instant.class, vacanteConBanda);
        assertThat(marcaTrasElCambio).isNotNull();
    }

    @Test
    @Order(6)
    @DisplayName("la campana cuenta lo que no ha visto, lo dice en cada fila, y se apaga al abrirla")
    void laCampanaCuentaYSeApaga() throws Exception {
        mvc.perform(get("/api/v1/portal/avisos")
                        .header("Authorization", "Bearer " + tokenCandidato))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sinLeer").value(1))
                .andExpect(jsonPath("$.avisos[0].tipo").value("REMUNERACION_ACTUALIZADA"))
                .andExpect(jsonPath("$.avisos[0].leidoEn").doesNotExist());

        // El punto de la fila de «Mis procesos»: el mismo hecho, contado donde se está
        // mirando. Y la fila lleva ya el sueldo de hoy, para no esconder la noticia tras un clic.
        mvc.perform(get("/api/v1/portal/postulaciones")
                        .header("Authorization", "Bearer " + tokenCandidato))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.avisosSinLeer == 1)]").exists());

        // Se apaga al ABRIR LA CAMPANA: enterarse de que hay algo es lo que lo apaga.
        mvc.perform(post("/api/v1/portal/avisos/lectura")
                        .header("Authorization", "Bearer " + tokenCandidato))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.marcados").value(1));

        mvc.perform(get("/api/v1/portal/avisos")
                        .header("Authorization", "Bearer " + tokenCandidato))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sinLeer").value(0))
                // Sigue ahí para releerlo: deja de contar, no desaparece.
                .andExpect(jsonPath("$.avisos.length()").value(1))
                .andExpect(jsonPath("$.avisos[0].leidoEn").exists());
    }

    @Test
    @Order(7)
    @DisplayName("guardar el MISMO sueldo otra vez no avisa a nadie ni mueve la marca de actualizado")
    void guardarLoMismoNoEsUnCambio() throws Exception {
        long avisosAntes = contar("select count(*) from aviso_portal");
        long correosAntes = contar(
                "select count(*) from correo_enviado where plantilla_correo_codigo = 'REMUNERACION_ACTUALIZADA'");

        // El panel manda el formulario entero cada vez: quien entra a mirar y pulsa guardar
        // sin tocar nada llega exactamente aquí, y no puede costarle una noticia vacía a
        // cuarenta personas.
        conToken(post("/api/v1/panel/vacantes/" + vacanteConBanda + "/remuneracion"), tokenEquipo, """
                {"remuneracion": {"tipo": "FIJA", "min": 4500, "moneda": "PEN"},
                 "motivo": "Se cerró el presupuesto del puesto con Dirección"}""")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.antes").value(FIJO))
                .andExpect(jsonPath("$.ahora").value(FIJO))
                .andExpect(jsonPath("$.candidatosAvisados").value(0));

        assertThat(contar("select count(*) from aviso_portal")).isEqualTo(avisosAntes);
        assertThat(contar(
                "select count(*) from correo_enviado where plantilla_correo_codigo = 'REMUNERACION_ACTUALIZADA'"))
                .isEqualTo(correosAntes);
        // Y la marca se queda donde estaba: moverla haría que el portal pintara «actualizado
        // hoy» sobre un número que no cambió.
        assertThat(jdbc.queryForObject(
                "select remuneracion_actualizada_en from vacante where id = ?", Instant.class, vacanteConBanda))
                .isEqualTo(marcaTrasElCambio);
    }

    @Test
    @Order(8)
    @DisplayName("a quien ya no continúa no se le avisa: la noticia no le afecta")
    void aQuienYaNoContinuaNoSeLeAvisa() throws Exception {
        conToken(post("/api/v1/panel/postulaciones/" + postulacionConBanda + "/transiciones"), tokenEquipo,
                "{\"estadoDestino\":\"NO_CONTINUA\",\"motivo\":\"No alcanza el perfil técnico del puesto\"}")
                .andExpect(status().isOk());

        long avisosAntes = contar("select count(*) from aviso_portal");
        long correosAntes = contar(
                "select count(*) from correo_enviado where plantilla_correo_codigo = 'REMUNERACION_ACTUALIZADA'");

        conToken(post("/api/v1/panel/vacantes/" + vacanteConBanda + "/remuneracion"), tokenEquipo, """
                {"remuneracion": {"tipo": "FIJA", "min": 5200, "moneda": "PEN"},
                 "motivo": "Se subió la banda para reabrir la búsqueda"}""")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ahora").value("S/ 5 200"))
                .andExpect(jsonPath("$.candidatosAvisados").value(0));

        // El sueldo SÍ cambió —la auditoría lo recoge— pero a esta persona no se le escribe:
        // recibir un correo sobre el sueldo de un puesto que ya perdió es cruel sin ganar nada.
        assertThat(contar("select count(*) from aviso_portal")).isEqualTo(avisosAntes);
        assertThat(contar(
                "select count(*) from correo_enviado where plantilla_correo_codigo = 'REMUNERACION_ACTUALIZADA'"))
                .isEqualTo(correosAntes);
        assertThat(contar("""
                select count(*) from auditoria
                where accion = 'actualizar_remuneracion' and entidad_id = %d
                """.formatted(vacanteConBanda)))
                .as("el cambio se audita aunque no haya a quién contárselo")
                .isEqualTo(2);
    }

    // ============ Lo que la base no admite ============

    @Test
    @Order(9)
    @DisplayName("la restricción de la V55 para la remuneración de la vacante no admite estados imposibles")
    void laBaseParaLasRemuneracionesIncoherentes() {
        // Las cuatro formas de mentir con los mismos cuatro campos. El código las valida antes
        // (Remuneracion.validar), pero un formulario no es la única forma de escribir en una
        // tabla: una carga masiva o un parche a mano llegan aquí directamente.
        seRechaza("OCULTA con un monto escondido debajo",
                "update vacante set remuneracion_tipo = 'OCULTA', remuneracion_min = 3000,"
                        + " remuneracion_max = null, remuneracion_moneda = 'PEN' where id = " + vacanteOculta);

        seRechaza("FIJA sin moneda: un número del que no se sabe de qué dinero habla",
                "update vacante set remuneracion_tipo = 'FIJA', remuneracion_min = 3000,"
                        + " remuneracion_max = null, remuneracion_moneda = null where id = " + vacanteOculta);

        seRechaza("FIJA con máximo: un sueldo fijo lleva un solo monto",
                "update vacante set remuneracion_tipo = 'FIJA', remuneracion_min = 3000,"
                        + " remuneracion_max = 4000, remuneracion_moneda = 'PEN' where id = " + vacanteOculta);

        seRechaza("RANGO al revés, con el máximo por debajo del mínimo",
                "update vacante set remuneracion_tipo = 'RANGO', remuneracion_min = 4000,"
                        + " remuneracion_max = 3000, remuneracion_moneda = 'PEN' where id = " + vacanteOculta);

        seRechaza("un sueldo de cero, que no es publicar un sueldo",
                "update vacante set remuneracion_tipo = 'FIJA', remuneracion_min = 0,"
                        + " remuneracion_max = null, remuneracion_moneda = 'PEN' where id = " + vacanteOculta);

        // Y la vacante sigue como estaba: ninguna de las cinco entró.
        assertThat(jdbc.queryForObject(
                "select remuneracion_tipo from vacante where id = ?", String.class, vacanteOculta))
                .isEqualTo("OCULTA");
    }

    @Test
    @Order(10)
    @DisplayName("la restricción de la V55 para la pretensión exige los tres campos juntos o los tres vacíos")
    void laBaseParaLasPretensionesAMedias() {
        seRechaza("un monto suelto, sin moneda ni fecha",
                "update postulacion set pretension_monto = 3000, pretension_moneda = null,"
                        + " pretension_declarada_en = null where id = " + postulacionConBanda);

        seRechaza("un monto con moneda pero sin el día en que se dijo",
                "update postulacion set pretension_monto = 3000, pretension_moneda = 'PEN',"
                        + " pretension_declarada_en = null where id = " + postulacionConBanda);

        seRechaza("una moneda sin monto: no dice nada y ocupa sitio",
                "update postulacion set pretension_monto = null, pretension_moneda = 'PEN',"
                        + " pretension_declarada_en = now() where id = " + postulacionConBanda);

        seRechaza("una pretensión de cero",
                "update postulacion set pretension_monto = 0, pretension_moneda = 'PEN',"
                        + " pretension_declarada_en = now() where id = " + postulacionConBanda);

        // Lo que declaró sigue intacto después de los cuatro intentos.
        assertThat(jdbc.queryForObject(
                "select pretension_monto from postulacion where id = ?", BigDecimal.class, postulacionConBanda))
                .isEqualByComparingTo("3800");
    }

    @Test
    @Order(11)
    @DisplayName("la tabla de avisos de la V56 no admite un aviso de nadie, de ninguna empresa ni sin texto")
    void laBaseParaLosAvisosHuerfanos() {
        // La V56 no lleva CHECK: lo que la protege son sus claves foráneas y sus columnas
        // obligatorias. Un aviso sin dueño no se lo puede enseñar a nadie, y uno sin texto
        // sería un punto rojo que al pulsarlo no dice nada.
        seRechaza("un aviso de un usuario que no existe",
                "insert into aviso_portal (usuario_id, organizacion_id, tipo, titulo, cuerpo)"
                        + " values (999999, 1, 'REMUNERACION_ACTUALIZADA', 'Título', 'Cuerpo')");

        seRechaza("un aviso de una organización que no existe",
                ("insert into aviso_portal (usuario_id, organizacion_id, tipo, titulo, cuerpo)"
                        + " values (%d, 999999, 'REMUNERACION_ACTUALIZADA', 'Título', 'Cuerpo')")
                        .formatted(usuarioCandidatoId));

        seRechaza("un aviso sin dueño",
                "insert into aviso_portal (usuario_id, organizacion_id, tipo, titulo, cuerpo)"
                        + " values (null, 1, 'REMUNERACION_ACTUALIZADA', 'Título', 'Cuerpo')");

        seRechaza("un aviso sin cuerpo: un punto rojo que no cuenta nada",
                ("insert into aviso_portal (usuario_id, organizacion_id, tipo, titulo, cuerpo)"
                        + " values (%d, 1, 'REMUNERACION_ACTUALIZADA', 'Título', null)")
                        .formatted(usuarioCandidatoId));

        seRechaza("un aviso colgado de una vacante que no existe",
                ("insert into aviso_portal (usuario_id, organizacion_id, tipo, titulo, cuerpo, vacante_id)"
                        + " values (%d, 1, 'REMUNERACION_ACTUALIZADA', 'Título', 'Cuerpo', 999999)")
                        .formatted(usuarioCandidatoId));
    }

    // ============ Andamios ============

    /** Comprueba que la base rechaza una fila imposible, diciendo cuál se intentó. */
    private void seRechaza(String queSeIntento, String sql) {
        assertThatThrownBy(() -> jdbc.update(sql))
                .as("la base tiene que rechazar: %s", queSeIntento)
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    private long contar(String sql) {
        Long cuantos = jdbc.queryForObject(sql, Long.class);
        return cuantos == null ? 0 : cuantos;
    }

    private MockMultipartFile unCurriculum() {
        return new MockMultipartFile("cv", "cv.pdf", "application/pdf",
                "contenido de prueba del curriculum".getBytes());
    }

    private void crearLaCuentaDelCandidato() throws Exception {
        mvc.perform(post("/api/v1/portal/cuentas")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"nombre": "Lucía", "apellidos": "Mendoza",
                                 "correo": "%s", "contrasena": "%s",
                                 "ciudadUbigeo": "1501", "aceptaPlataforma": true,
                                 "aceptaFuturosContactos": true}""".formatted(CORREO_CANDIDATO, CLAVE)))
                .andExpect(status().isCreated());

        tokenCandidato = leer(mvc.perform(post("/api/v1/portal/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"correo\":\"%s\",\"contrasena\":\"%s\"}".formatted(CORREO_CANDIDATO, CLAVE)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(), "token");

        usuarioCandidatoId = jdbc.queryForObject(
                "select id from usuario where correo = ?", Long.class, CORREO_CANDIDATO);
    }

    /**
     * Una vacante entera hasta publicada, que es el único estado desde el que se puede
     * postular. El bloque {@code sueldo} entra tal cual en el cuerpo: vacío significa que
     * nace OCULTA, que es como nacen todas las que no digan lo contrario.
     */
    private long publicarVacante(Long areaId, long puestoId, Long plantillaEvaluacionId,
                                 Long versionPruebaId, String titulo, String sueldo) throws Exception {
        long solicitudId = Long.parseLong(leer(conToken(post("/api/v1/panel/solicitudes"), tokenEquipo, """
                {"areaId": %d, "puestoId": %d, "urgencia": "NORMAL",
                 "nivelPuestoCodigo": "EJECUCION", "familiaCodigo": "TECNOLOGIA",
                 "resultadoPrincipal": "Sostener el desarrollo del portal",
                 "motivo": "El equipo actual no llega a los plazos",
                 "consecuenciaNoContratar": "Se retrasa el MVP",
                 "analisisCapacidad": "Se evaluó automatizar y no alcanza: el trabajo es de diseño",
                 "responsableUsuarioId": 1,
                 "resultadosEsperados": [
                   {"descripcion": "Publicar el portal", "indicador": "en producción"},
                   {"descripcion": "Reducir bugs", "indicador": "la mitad de errores"},
                   {"descripcion": "Documentar el módulo", "indicador": "docs al día"}
                 ]}""".formatted(areaId, puestoId))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString(), "id"));

        conToken(post("/api/v1/panel/solicitudes/" + solicitudId + "/aprobacion"), tokenEquipo,
                "{\"motivo\":\"Justificada: hay presupuesto\"}")
                .andExpect(status().isOk());

        long vacanteId = Long.parseLong(leer(conToken(post("/api/v1/panel/vacantes"), tokenEquipo, """
                {"solicitudTalentoId": %d, "titulo": "%s", "descripcion": "Portal de talento",
                 %s "tipoCierre": "PERMANENTE", "responsableUsuarioId": 1}"""
                .formatted(solicitudId, titulo, sueldo))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString(), "id"));

        conToken(post("/api/v1/panel/vacantes/" + vacanteId + "/plantilla-evaluacion"), tokenEquipo,
                "{\"plantillaEvaluacionId\": %d}".formatted(plantillaEvaluacionId))
                .andExpect(status().isOk());
        conToken(post("/api/v1/panel/vacantes/" + vacanteId + "/plantilla-prueba"), tokenEquipo,
                "{\"versionPlantillaPruebaId\": %d}".formatted(versionPruebaId))
                .andExpect(status().isOk());
        conToken(post("/api/v1/panel/vacantes/" + vacanteId + "/publicacion"), tokenEquipo, null)
                .andExpect(status().isOk());
        return vacanteId;
    }

    /** La prueba mínima válida que exige publicar: 8 universales, 3 específicas y una rúbrica de 100. */
    private Long armarUnaPruebaValida() throws Exception {
        long plantillaId = Long.parseLong(leer(conToken(post("/api/v1/panel/plantillas-prueba"), tokenEquipo,
                "{\"nombre\":\"Prueba de la remuneración\"}")
                .andReturn().getResponse().getContentAsString(), "id"));
        long versionId = Long.parseLong(leer(conToken(
                post("/api/v1/panel/plantillas-prueba/" + plantillaId + "/versiones"), tokenEquipo, """
                {"enunciado":"Resuelve el caso propuesto","modalidad":"CRONOMETRADA",
                 "duracionMinutos":90,"minutoCambioMin":30,"minutoCambioMax":50,"minutosExtra":10}""")
                .andReturn().getResponse().getContentAsString(), "id"));

        for (int i = 0; i < 8; i++) {
            long id = Long.parseLong(leer(conToken(post("/api/v1/panel/plantillas-prueba/preguntas"), tokenEquipo,
                    "{\"codigo\":\"UNIV_REM_%d\",\"enunciado\":\"Pregunta universal %d\",\"tipo\":\"UNIVERSAL\"}"
                            .formatted(i, i))
                    .andReturn().getResponse().getContentAsString(), "id"));
            conToken(post("/api/v1/panel/plantillas-prueba/versiones/" + versionId + "/preguntas"), tokenEquipo,
                    "{\"preguntaPruebaId\": %d}".formatted(id)).andExpect(status().isOk());
        }
        for (int i = 0; i < 3; i++) {
            long id = Long.parseLong(leer(conToken(post("/api/v1/panel/plantillas-prueba/preguntas"), tokenEquipo,
                    "{\"codigo\":\"ESP_REM_%d\",\"enunciado\":\"Pregunta específica %d\",\"tipo\":\"ESPECIFICA\"}"
                            .formatted(i, i))
                    .andReturn().getResponse().getContentAsString(), "id"));
            conToken(post("/api/v1/panel/plantillas-prueba/versiones/" + versionId + "/preguntas"), tokenEquipo,
                    "{\"preguntaPruebaId\": %d}".formatted(id)).andExpect(status().isOk());
        }
        conToken(post("/api/v1/panel/plantillas-prueba/versiones/" + versionId + "/rubrica"), tokenEquipo, """
                {"codigo":"RESULTADO_REM","nombre":"Resultado","puntos":100,"metodoVerificacion":"PERSONA"}""")
                .andExpect(status().isCreated());
        conToken(post("/api/v1/panel/plantillas-prueba/versiones/" + versionId + "/publicacion"),
                tokenEquipo, null).andExpect(status().isOk());
        return versionId;
    }

    private ResultActions conToken(MockHttpServletRequestBuilder peticion, String token, String cuerpo)
            throws Exception {
        peticion.header("Authorization", "Bearer " + token);
        if (cuerpo != null) {
            peticion.contentType(MediaType.APPLICATION_JSON).content(cuerpo);
        }
        return mvc.perform(peticion);
    }

    private String leer(String cuerpoRespuesta, String campo) throws Exception {
        JsonNode nodo = json.readTree(cuerpoRespuesta).get(campo);
        assertThat(nodo).as("campo %s en %s", campo, cuerpoRespuesta).isNotNull();
        return nodo.asText();
    }
}
