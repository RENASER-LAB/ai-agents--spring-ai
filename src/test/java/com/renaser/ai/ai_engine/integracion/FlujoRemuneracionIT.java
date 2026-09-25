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
 *   <li>Que el aviso y la auditoría salgan del MISMO gesto y queden escritos en las dos
 *       tablas —y que el correo ya NO salga (V58)—, que es lo que nadie puede verificar
 *       mirando un solo servicio.
 *   <li>Que editar la vacante publicada deje <b>un solo aviso</b> por persona en carrera,
 *       con lo que cambió dentro, y ninguno para quien ya no continúa.
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
    static String tokenSegundo;
    static String tokenTercero;
    static Long solicitudDeLaVacante;

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
    @DisplayName("cambiar el sueldo publicado deja aviso y auditoría con el motivo, mueve la marca y NO manda correo")
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

        // 2. Y NINGÚN correo. Hasta la V58 salían los dos; el correo se retiró porque se
        // pierde —promociones, direcciones inventadas por el cargador de currículums— y
        // mandarlo hacía que el panel prometiera una entrega que nadie podía confirmar.
        assertThat(contar("""
                select count(*) from correo_enviado
                where usuario_id = %d and plantilla_correo_codigo = 'REMUNERACION_ACTUALIZADA'
                """.formatted(usuarioCandidatoId)))
                .as("el cambio de sueldo ya no sale por correo: solo por la campana")
                .isZero();

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

    // ============ Editar la vacante se cuenta una sola vez ============

    @Test
    @Order(12)
    @DisplayName("la lista del panel trae el texto entero, cuánta gente sigue en carrera y si se puede editar")
    void laListaTraeLoQueElLapizNecesita() throws Exception {
        // Dos candidatos más, los dos en carrera. El de arriba quedó NO_CONTINUA en el paso 8
        // y se queda ahí a propósito: es el testigo de a quién NO hay que avisarle.
        tokenSegundo = crearCuentaYPostular("segundo.editar@ejemplo.pe", "3600");
        tokenTercero = crearCuentaYPostular("tercero.editar@ejemplo.pe", "3700");

        solicitudDeLaVacante = jdbc.queryForObject(
                "select solicitud_talento_id from vacante where id = ?", Long.class, vacanteConBanda);

        // El lápiz necesita las tres cosas en la MISMA respuesta: el texto para abrir el
        // formulario lleno, el número para decir a cuánta gente le va a llegar, y si quien
        // mira puede tocarla. Un endpoint de permisos aparte no podría contestar lo último:
        // el alcance se decide vacante a vacante.
        mvc.perform(get("/api/v1/panel/vacantes").header("Authorization", "Bearer " + tokenEquipo))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.id == %d)].postulantesEnCarrera".formatted(vacanteConBanda))
                        .value(2))
                .andExpect(jsonPath("$[?(@.id == %d)].puedeEditar".formatted(vacanteConBanda))
                        .value(true))
                .andExpect(jsonPath("$[?(@.id == %d)].descripcion".formatted(vacanteConBanda))
                        .value("Portal de talento"));
    }

    @Test
    @Order(13)
    @DisplayName("cambiar el horario y la descripción deja UN aviso a cada persona en carrera, y ningún correo")
    void editarLaVacanteAvisaUnaVezACadaUno() throws Exception {
        long correosAntes = contar("select count(*) from correo_enviado");

        guardarLaVacante("""
                "horario": "L-V de 9 a 6", "descripcion": "Portal de talento, con foco en accesibilidad",\
                """)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.huboCambios").value(true))
                .andExpect(jsonPath("$.postulantesAvisados").value(2));

        // Uno por persona en carrera, ni dos ni ninguno: el guardado es uno solo aunque
        // cambien dos campos.
        assertThat(contar("""
                select count(*) from aviso_portal
                 where vacante_id = %d and tipo = 'VACANTE_ACTUALIZADA'
                """.formatted(vacanteConBanda)))
                .isEqualTo(2);
        // Y a quien ya no continúa, nada. Lo suyo terminó; la noticia no le afecta.
        assertThat(contar("""
                select count(*) from aviso_portal
                 where vacante_id = %d and tipo = 'VACANTE_ACTUALIZADA' and usuario_id = %d
                """.formatted(vacanteConBanda, usuarioCandidatoId)))
                .isZero();

        // El texto: lo corto con su antes y su ahora, lo largo solo nombrado —pegar dos
        // párrafos en una campana tapa en vez de informar— y el cierre de que no hay nada
        // que hacer.
        String cuerpo = jdbc.queryForObject("""
                select cuerpo from aviso_portal
                 where vacante_id = ? and tipo = 'VACANTE_ACTUALIZADA' limit 1
                """, String.class, vacanteConBanda);
        assertThat(cuerpo)
                .contains("Horario: sin indicar → L-V de 9 a 6")
                .contains("Se actualizó la descripción")
                .doesNotContain("accesibilidad")
                .endsWith("Tu postulación sigue su curso y no tienes que hacer nada.");

        String titulo = jdbc.queryForObject("""
                select titulo from aviso_portal
                 where vacante_id = ? and tipo = 'VACANTE_ACTUALIZADA' limit 1
                """, String.class, vacanteConBanda);
        assertThat(titulo).isEqualTo("Se actualizó la vacante «Desarrollador web»");

        assertThat(contar("select count(*) from correo_enviado"))
                .as("editar la vacante no manda correos a nadie")
                .isEqualTo(correosAntes);

        // Y el aviso lleva al proceso: el candidato lo pulsa y llega a su postulación.
        mvc.perform(get("/api/v1/portal/avisos")
                        .header("Authorization", "Bearer " + tokenSegundo))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sinLeer").value(1))
                .andExpect(jsonPath("$.avisos[0].tipo").value("VACANTE_ACTUALIZADA"))
                .andExpect(jsonPath("$.avisos[0].postulacionUuid").isNotEmpty());
    }

    @Test
    @Order(14)
    @DisplayName("volver a guardar lo mismo, con espacios de más, no audita ni avisa a nadie")
    void guardarSinCambiosNoHaceNada() throws Exception {
        long avisosAntes = contar("select count(*) from aviso_portal");
        long auditoriasAntes = contar(
                "select count(*) from auditoria where accion = 'editar_vacante'");

        // Reenviar la misma edición —el doble clic, el reintento de una red lenta— y con
        // espacios alrededor, que es lo que deja copiar y pegar.
        guardarLaVacante("""
                "horario": "  L-V de 9 a 6 ", "descripcion": " Portal de talento, con foco en accesibilidad  ",\
                """)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.huboCambios").value(false))
                .andExpect(jsonPath("$.postulantesAvisados").value(0));

        assertThat(contar("select count(*) from aviso_portal")).isEqualTo(avisosAntes);
        assertThat(contar("select count(*) from auditoria where accion = 'editar_vacante'"))
                .isEqualTo(auditoriasAntes);
    }

    @Test
    @Order(15)
    @DisplayName("lo interno se guarda y se audita, y no sale de la empresa")
    void loInternoNoSeAvisa() throws Exception {
        long avisosAntes = contar("select count(*) from aviso_portal");

        // Cuántas plazas hay es cómo se organiza la empresa por dentro: al candidato no le
        // dice nada sobre lo que aceptó al postular.
        //
        // Va con su forma de cierre —«por plazas»— y no suelto: las plazas solo se escriben
        // cuando esa es la forma elegida, que es cuando el formulario las enseña. Mandarlas
        // con una vacante PERMANENTE no cambia nada, y así no puede volver a colarse un
        // campo que se borra sin que nadie lo haya tocado. Por eso este paso no usa
        // `guardarLaVacante`, que siempre manda PERMANENTE.
        conToken(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .put("/api/v1/panel/vacantes/" + vacanteConBanda), tokenEquipo, """
                {"solicitudTalentoId": %d, "titulo": "Desarrollador web",
                 "descripcion": "Portal de talento, con foco en accesibilidad",
                 "horario": "L-V de 9 a 6",
                 "remuneracion": {"tipo": "FIJA", "min": 5200, "moneda": "PEN"},
                 "tipoCierre": "PLAZAS", "plazas": 3, "responsableUsuarioId": 1}"""
                .formatted(solicitudDeLaVacante))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.huboCambios").value(true))
                .andExpect(jsonPath("$.postulantesAvisados").value(0));

        assertThat(contar("select count(*) from aviso_portal")).isEqualTo(avisosAntes);
        assertThat(jdbc.queryForObject("select plazas from vacante where id = ?",
                Integer.class, vacanteConBanda)).isEqualTo(3);
        assertThat(contar("""
                select count(*) from auditoria
                 where accion = 'editar_vacante' and entidad_id = %d
                   and valor_nuevo::text like '%%plazas%%'
                """.formatted(vacanteConBanda)))
                .as("lo interno no se avisa, pero sí se audita")
                .isEqualTo(1);
    }

    @Test
    @Order(16)
    @DisplayName("el sueldo y la ubicación en el mismo guardado salen en un solo aviso")
    void elSueldoYElTextoSalenJuntos() throws Exception {
        long avisosAntes = contar("select count(*) from aviso_portal");
        long correosAntes = contar("select count(*) from correo_enviado");

        guardarLaVacante("""
                "horario": "L-V de 9 a 6", "descripcion": "Portal de talento, con foco en accesibilidad",
                "plazas": 3, "ubicacion": "Lima, San Isidro",
                "remuneracion": {"tipo": "FIJA", "min": 5600, "moneda": "PEN"},
                "motivoRemuneracion": "Se ajustó la banda del puesto",\
                """)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.postulantesAvisados").value(2));

        // Dos avisos nuevos, uno por persona. No cuatro: cambiar el sueldo y la ubicación de
        // una tacada es UN cambio, y dos campanas seguidas se leen como un fallo.
        assertThat(contar("select count(*) from aviso_portal")).isEqualTo(avisosAntes + 2);

        String cuerpo = jdbc.queryForObject("""
                select cuerpo from aviso_portal
                 where vacante_id = ? and tipo = 'VACANTE_ACTUALIZADA'
                 order by id desc limit 1
                """, String.class, vacanteConBanda);
        assertThat(cuerpo)
                .contains("Zona o referencia: sin indicar → Lima, San Isidro")
                .contains("Remuneración: S/ 5 200 → S/ 5 600");

        assertThat(contar("select count(*) from correo_enviado")).isEqualTo(correosAntes);
        assertThat(contar("""
                select count(*) from auditoria
                 where accion = 'editar_vacante' and entidad_id = %d
                   and motivo = 'Se ajustó la banda del puesto'
                """.formatted(vacanteConBanda)))
                .isEqualTo(1);
        // Y la marca del sueldo se movió: es lo que el portal pinta sobre el monto nuevo.
        assertThat(jdbc.queryForObject("select remuneracion_actualizada_en from vacante where id = ?",
                Instant.class, vacanteConBanda)).isAfter(marcaTrasElCambio);
    }

    @Test
    @Order(17)
    @DisplayName("un sueldo imposible, o sin decir por qué, no deja medio formulario guardado")
    void unaValidacionFallidaNoGuardaNada() throws Exception {
        long avisosAntes = contar("select count(*) from aviso_portal");

        // El rango al revés llega con un título nuevo al lado: si el servicio escribiera
        // antes de validar, el título se quedaría cambiado y el sueldo no.
        guardarLaVacante("""
                "titulo": "Desarrollador web senior", "horario": "L-V de 9 a 6",
                "descripcion": "Portal de talento, con foco en accesibilidad", "plazas": 3,
                "ubicacion": "Lima, San Isidro",
                "remuneracion": {"tipo": "RANGO", "min": 6000, "max": 4000, "moneda": "PEN"},
                "motivoRemuneracion": "Se abre la banda",\
                """)
                .andExpect(status().isBadRequest());

        assertThat(jdbc.queryForObject("select titulo from vacante where id = ?",
                String.class, vacanteConBanda))
                .as("la validación del sueldo no puede dejar el título ya cambiado")
                .isEqualTo("Desarrollador web");

        // Y cambiar el sueldo de una publicada sin decir por qué tampoco pasa: a cada persona
        // en carrera le va a llegar, y la auditoría tiene que poder contestar por qué.
        guardarLaVacante("""
                "horario": "L-V de 9 a 6", "descripcion": "Portal de talento, con foco en accesibilidad",
                "plazas": 3, "ubicacion": "Lima, San Isidro",
                "remuneracion": {"tipo": "FIJA", "min": 5900, "moneda": "PEN"},\
                """)
                .andExpect(status().isBadRequest());

        assertThat(jdbc.queryForObject("select remuneracion_min from vacante where id = ?",
                BigDecimal.class, vacanteConBanda)).isEqualByComparingTo("5600");
        assertThat(contar("select count(*) from aviso_portal")).isEqualTo(avisosAntes);
    }

    @Test
    @Order(18)
    @DisplayName("sin el permiso es 403; fuera del alcance del rol, 404")
    void elPermisoYElAlcanceDeLaEdicion() throws Exception {
        var alcancesOriginales = jdbc.queryForList("""
                select rol_id, alcance from rol_permiso
                 where permiso_id = (select id from permiso where codigo = 'editar_vacante')
                """);

        jdbc.update("""
                delete from rol_permiso where permiso_id =
                    (select id from permiso where codigo = 'editar_vacante')""");
        guardarLaVacante("""
                "horario": "L-V de 9 a 6", "descripcion": "Portal de talento, con foco en accesibilidad",\
                """)
                .andExpect(status().isForbidden());

        // Con el permiso acotado a «sus vacantes» y otro responsable al frente, la vacante no
        // existe para quien pregunta: 404 y no 403, porque un 403 confirmaría que está ahí.
        for (var fila : alcancesOriginales) {
            jdbc.update("""
                    insert into rol_permiso (rol_id, permiso_id, alcance)
                    values (?, (select id from permiso where codigo = 'editar_vacante'), 'SUS_VACANTES')
                    """, fila.get("rol_id"));
        }
        jdbc.update("update vacante set responsable_usuario_id = ? where id = ?",
                usuarioCandidatoId, vacanteConBanda);

        guardarLaVacante("""
                "horario": "L-V de 9 a 6", "descripcion": "Portal de talento, con foco en accesibilidad",\
                """)
                .andExpect(status().isNotFound());

        // Y la lista lo dice sin que haya que pulsar nada: el lápiz no se pinta.
        mvc.perform(get("/api/v1/panel/vacantes").header("Authorization", "Bearer " + tokenEquipo))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.id == %d)].puedeEditar".formatted(vacanteConBanda))
                        .value(false));

        // Se devuelve todo a su sitio: los pasos siguientes no heredan un reparto tocado.
        jdbc.update("update vacante set responsable_usuario_id = 1 where id = ?", vacanteConBanda);
        jdbc.update("""
                delete from rol_permiso where permiso_id =
                    (select id from permiso where codigo = 'editar_vacante')""");
        for (var fila : alcancesOriginales) {
            jdbc.update("""
                    insert into rol_permiso (rol_id, permiso_id, alcance) values (?,
                        (select id from permiso where codigo = 'editar_vacante'), ?)
                    """, fila.get("rol_id"), fila.get("alcance"));
        }
    }

    @Test
    @Order(19)
    @DisplayName("el texto de correo del sueldo ya no se ofrece para editar, y sus filas siguen ahí")
    void elTextoDelSueldoSeRetiraSinBorrarse() throws Exception {
        // Un texto que el sistema ya no manda, puesto en la pantalla de configuración, hace
        // trabajar en balde: alguien lo reescribe con cuidado y ese correo no sale nunca.
        String plantillas = mvc.perform(get("/api/v1/panel/plantillas-correo")
                        .header("Authorization", "Bearer " + tokenEquipo))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        assertThat(plantillas).doesNotContain("REMUNERACION_ACTUALIZADA");
        assertThat(plantillas).contains("PRUEBA_DISPONIBLE");

        // Y tampoco entra por la API, que es la otra puerta.
        conToken(post("/api/v1/panel/plantillas-correo"), tokenEquipo, """
                {"codigo": "REMUNERACION_ACTUALIZADA", "asunto": "Cambió el sueldo",
                 "cuerpo": "Hola"}""")
                .andExpect(status().isBadRequest());

        // Lo que SÍ se conserva: las filas. Cada correo ya enviado guarda el código y la
        // versión del texto con el que salió, y sin ellas no habría forma de explicar lo que
        // se le dijo a cada persona.
        assertThat(contar("""
                select count(*) from plantilla_correo where codigo = 'REMUNERACION_ACTUALIZADA'
                """))
                .isPositive();
        assertThat(contar("""
                select count(*) from plantilla_correo
                 where codigo = 'REMUNERACION_ACTUALIZADA' and es_activa
                """))
                .as("ninguna versión activa: el aviso ya no sale por correo")
                .isZero();
    }

    /**
     * Un aviso que no se puede escribir no deshace la edición ni deja sin enterarse a los
     * demás.
     *
     * <p><b>Por qué esto no lo puede contestar una prueba con dobles.</b> Con el repositorio
     * simulado, «atrapar la excepción y seguir» funciona siempre: no hay transacción que
     * ensuciar. Contra la base de verdad, un INSERT que revienta dentro de la transacción del
     * llamador la deja marcada {@code rollback-only}, y entonces el {@code catch} no rescata
     * nada — el commit explota después y se lleva por delante el cambio y su auditoría. Lo
     * único que lo evita es que {@code ServicioAvisosPortal.publicar} vaya en transacción
     * propia, y eso solo se ve aquí.
     *
     * <p>El fallo se provoca con un disparador que rechaza los avisos de UNA persona: es la
     * forma más parecida a lo que pasaría de verdad —una fila que la base no admite— sin
     * tocar el código de producción.
     */
    @Test
    @Order(20)
    @DisplayName("si el aviso de una persona falla, el cambio se queda y los demás sí se enteran")
    void unAvisoQueFallaNoTumbaLaEdicion() throws Exception {
        Long usuarioQueFalla = jdbc.queryForObject("""
                select usuario_id from postulacion
                 where vacante_id = ? and estado_codigo not in ('CONTRATADO','NO_CONTINUA','CERRADA')
                 order by id limit 1
                """, Long.class, vacanteConBanda);
        long avisosAntes = contar("select count(*) from aviso_portal");
        long auditoriasAntes = contar(
                "select count(*) from auditoria where accion = 'editar_vacante'");

        jdbc.execute("""
                create or replace function qa_rechaza_un_aviso() returns trigger as $$
                begin
                  if new.usuario_id = %d then
                    raise exception 'QA: este aviso no se puede escribir';
                  end if;
                  return new;
                end $$ language plpgsql;
                """.formatted(usuarioQueFalla));
        jdbc.execute("""
                create trigger qa_rechaza_un_aviso before insert on aviso_portal
                for each row execute function qa_rechaza_un_aviso();
                """);
        try {
            guardarLaVacante("""
                    "horario": "L-V de 7 a 4", "descripcion": "Portal de talento, con foco en accesibilidad",
                    "plazas": 3, "ubicacion": "Lima, San Isidro",\
                    """)
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.huboCambios").value(true))
                    // El contador dice los avisos que DE VERDAD se publicaron, no los que se
                    // intentaron: es la cifra que el panel repite en voz alta.
                    .andExpect(jsonPath("$.postulantesAvisados").value(1));
        } finally {
            jdbc.execute("drop trigger if exists qa_rechaza_un_aviso on aviso_portal");
            jdbc.execute("drop function if exists qa_rechaza_un_aviso()");
        }

        // 1. El cambio está hecho: no se deshizo por no poder contarlo.
        assertThat(jdbc.queryForObject("select horario from vacante where id = ?",
                String.class, vacanteConBanda))
                .as("un aviso que falla no puede revertir lo que el panel decidió")
                .isEqualTo("L-V de 7 a 4");
        assertThat(contar("select count(*) from auditoria where accion = 'editar_vacante'"))
                .isEqualTo(auditoriasAntes + 1);

        // 2. Y a los demás sí les llegó: uno solo cayó.
        assertThat(contar("select count(*) from aviso_portal")).isEqualTo(avisosAntes + 1);
        assertThat(contar("""
                select count(*) from aviso_portal
                 where vacante_id = %d and usuario_id = %d and tipo = 'VACANTE_ACTUALIZADA'
                   and cuerpo like '%%L-V de 7 a 4%%'
                """.formatted(vacanteConBanda, usuarioQueFalla)))
                .as("a quien falló el aviso no le queda ninguno de este guardado")
                .isZero();
    }

    @Test
    @Order(21)
    @DisplayName("si la vacante se cierra mientras se edita, el guardado se rechaza entero")
    void cerrarlaAMitadDeUnaEdicionRechazaElGuardado() throws Exception {
        long avisosAntes = contar("select count(*) from aviso_portal");

        conToken(post("/api/v1/panel/vacantes/" + vacanteConBanda + "/cierre"), tokenEquipo,
                "{\"motivo\":\"Se cubrió el puesto\"}")
                .andExpect(status().isOk());

        // El formulario ya estaba abierto cuando alguien cerró la vacante: llega tarde.
        guardarLaVacante("""
                "titulo": "Desarrollador web senior", "horario": "L-V de 9 a 6",
                "descripcion": "Portal de talento, con foco en accesibilidad", "plazas": 3,
                "ubicacion": "Lima, San Isidro",\
                """)
                .andExpect(status().isConflict());

        assertThat(jdbc.queryForObject("select titulo from vacante where id = ?",
                String.class, vacanteConBanda))
                .as("una cerrada no se edita, y el rechazo es entero")
                .isEqualTo("Desarrollador web");
        assertThat(contar("select count(*) from aviso_portal")).isEqualTo(avisosAntes);
    }

    /**
     * La regresión del hallazgo QA-01, contra la base de verdad.
     *
     * <p>Va sobre la vacante OCULTA y no sobre la de la banda porque el paso anterior la
     * cerró. Se siembran por SQL los dos campos que el formulario NO enseña —la fecha de
     * apertura, y unas plazas con una forma de cierre que no las usa— porque ninguna vacante
     * llega aquí con ellos: es justo el dato que hacía invisible el fallo.
     */
    @Test
    @Order(22)
    @DisplayName("guardar sin tocar nada no borra la fecha de apertura ni las plazas que no se enseñan")
    void guardarSinTocarNadaNoBorraLoQueElFormularioNoEnsena() throws Exception {
        jdbc.update("""
                update vacante set abre_en = timestamptz '2026-09-23 03:35:54+00', plazas = 5
                 where id = ?""", vacanteOculta);
        Long solicitud = jdbc.queryForObject(
                "select solicitud_talento_id from vacante where id = ?", Long.class, vacanteOculta);
        long avisosAntes = contar("select count(*) from aviso_portal");
        long auditoriasAntes = contar(
                "select count(*) from auditoria where accion = 'editar_vacante'");

        // El cuerpo EXACTO que manda el panel al abrir el lápiz y pulsar guardar: sin
        // `abreEn` y sin `plazas`, porque su forma de cierre no las enseña.
        String comoLoMandaElPanel = """
                {"solicitudTalentoId": %d, "titulo": "Analista de datos",
                 "descripcion": "Portal de talento",
                 "remuneracion": {"tipo": "OCULTA", "min": null, "max": null, "moneda": null},
                 "tipoCierre": "PERMANENTE", "responsableUsuarioId": 1}"""
                .formatted(solicitud);

        conToken(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .put("/api/v1/panel/vacantes/" + vacanteOculta), tokenEquipo,
                        comoLoMandaElPanel)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.huboCambios").value(false))
                .andExpect(jsonPath("$.postulantesAvisados").value(0));

        assertThat(jdbc.queryForObject("select abre_en from vacante where id = ?",
                Instant.class, vacanteOculta))
                .as("el formulario no enseña la apertura: guardar no puede borrarla")
                .isEqualTo(Instant.parse("2026-09-23T03:35:54Z"));
        assertThat(jdbc.queryForObject("select plazas from vacante where id = ?",
                Integer.class, vacanteOculta))
                .as("tampoco las plazas, que su forma de cierre no muestra")
                .isEqualTo(5);
        assertThat(contar("select count(*) from auditoria where accion = 'editar_vacante'"))
                .as("sin cambios no se audita nada")
                .isEqualTo(auditoriasAntes);
        assertThat(contar("select count(*) from aviso_portal")).isEqualTo(avisosAntes);

        // Y cambiar la forma de cierre SÍ limpia lo que ya no rige: eso alguien lo decidió
        // en la pantalla, se guarda y se audita, y la apertura sigue donde estaba.
        conToken(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .put("/api/v1/panel/vacantes/" + vacanteOculta), tokenEquipo, """
                {"solicitudTalentoId": %d, "titulo": "Analista de datos",
                 "descripcion": "Portal de talento",
                 "remuneracion": {"tipo": "OCULTA", "min": null, "max": null, "moneda": null},
                 "tipoCierre": "FECHA", "cierraEn": "2026-12-01T00:00:00Z",
                 "responsableUsuarioId": 1}""".formatted(solicitud))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.huboCambios").value(true))
                .andExpect(jsonPath("$.postulantesAvisados").value(0));

        assertThat(jdbc.queryForObject("select plazas from vacante where id = ?",
                Integer.class, vacanteOculta)).isNull();
        assertThat(jdbc.queryForObject("select abre_en from vacante where id = ?",
                Instant.class, vacanteOculta))
                .isEqualTo(Instant.parse("2026-09-23T03:35:54Z"));
        assertThat(contar("select count(*) from aviso_portal"))
                .as("la forma de cierre es interna: no sale del equipo")
                .isEqualTo(avisosAntes);
    }

    /**
     * La regresión del hallazgo QA-02, contra la base de verdad.
     *
     * <p>El paso anterior dejó esta vacante cerrando por FECHA el 1 de diciembre. Se le pone
     * una hora por SQL —ninguna vacante nacida en el panel la tiene, porque el alta escribe
     * siempre la medianoche UTC del día elegido— y se guarda el formulario tal cual, que es
     * lo que hace quien abre el lápiz y pulsa guardar.
     */
    @Test
    @Order(23)
    @DisplayName("guardar sin tocar la fecha de cierre no le quita la hora a la que cerraba")
    void guardarSinTocarLaFechaNoLeQuitaLaHora() throws Exception {
        jdbc.update("update vacante set cierra_en = timestamptz '2026-12-01 18:30:00+00'"
                + " where id = ?", vacanteOculta);
        Long solicitud = jdbc.queryForObject(
                "select solicitud_talento_id from vacante where id = ?", Long.class, vacanteOculta);
        long avisosAntes = contar("select count(*) from aviso_portal");
        long auditoriasAntes = contar(
                "select count(*) from auditoria where accion = 'editar_vacante'");

        // El formulario solo enseña el día, así que devuelve su medianoche UTC.
        String comoLoMandaElPanel = """
                {"solicitudTalentoId": %d, "titulo": "Analista de datos",
                 "descripcion": "Portal de talento",
                 "remuneracion": {"tipo": "OCULTA", "min": null, "max": null, "moneda": null},
                 "tipoCierre": "FECHA", "cierraEn": "%s", "responsableUsuarioId": 1}""";

        conToken(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .put("/api/v1/panel/vacantes/" + vacanteOculta), tokenEquipo,
                        comoLoMandaElPanel.formatted(solicitud, "2026-12-01T00:00:00.000Z"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.huboCambios").value(false))
                .andExpect(jsonPath("$.postulantesAvisados").value(0));

        assertThat(jdbc.queryForObject("select cierra_en from vacante where id = ?",
                Instant.class, vacanteOculta))
                .as("el formulario enseña el día: guardar no puede mover la hora")
                .isEqualTo(Instant.parse("2026-12-01T18:30:00Z"));
        assertThat(contar("select count(*) from auditoria where accion = 'editar_vacante'"))
                .isEqualTo(auditoriasAntes);
        assertThat(contar("select count(*) from aviso_portal")).isEqualTo(avisosAntes);

        // Y cambiar el DÍA sí se guarda y se audita: lo que se conserva es la hora de un día
        // que nadie tocó, no la fecha entera.
        conToken(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .put("/api/v1/panel/vacantes/" + vacanteOculta), tokenEquipo,
                        comoLoMandaElPanel.formatted(solicitud, "2026-12-15T00:00:00.000Z"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.huboCambios").value(true))
                .andExpect(jsonPath("$.postulantesAvisados").value(0));

        assertThat(jdbc.queryForObject("select cierra_en from vacante where id = ?",
                Instant.class, vacanteOculta))
                .isEqualTo(Instant.parse("2026-12-15T00:00:00Z"));
        assertThat(contar("select count(*) from auditoria where accion = 'editar_vacante'"))
                .isEqualTo(auditoriasAntes + 1);
        assertThat(contar("select count(*) from aviso_portal"))
                .as("la fecha de cierre es interna: no sale del equipo")
                .isEqualTo(avisosAntes);
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

    /**
     * Guarda el formulario entero de la vacante de la banda con los campos que se le pasen.
     *
     * <p>El cuerpo del PUT es el formulario COMPLETO, como lo manda el panel: lo que no viaja
     * se borra. Por eso cada paso repite lo que ya estaba —y por eso la remuneración va
     * siempre, salvo cuando el propio paso la cambia: sin ella, el servicio leería «no
     * publicar el sueldo» y una vacante publicada no puede esconderlo.
     */
    private ResultActions guardarLaVacante(String campos) throws Exception {
        String cuerpo = """
                {"solicitudTalentoId": %d, "titulo": "Desarrollador web",
                 %s
                 "tipoCierre": "PERMANENTE", "responsableUsuarioId": 1}"""
                .formatted(solicitudDeLaVacante, campos);
        if (!campos.contains("remuneracion")) {
            cuerpo = cuerpo.replace("\"tipoCierre\"",
                    "\"remuneracion\": {\"tipo\": \"FIJA\", \"min\": %s, \"moneda\": \"PEN\"}, \"tipoCierre\""
                            .formatted(jdbc.queryForObject(
                                    "select remuneracion_min from vacante where id = ?",
                                    BigDecimal.class, vacanteConBanda).toPlainString()));
        }
        return conToken(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                .put("/api/v1/panel/vacantes/" + vacanteConBanda), tokenEquipo, cuerpo);
    }

    /** Una cuenta de candidato que postula a la vacante de la banda y se queda en carrera. */
    private String crearCuentaYPostular(String correo, String pretension) throws Exception {
        mvc.perform(post("/api/v1/portal/cuentas")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"nombre": "Cande", "apellidos": "De Prueba",
                                 "correo": "%s", "contrasena": "%s",
                                 "ciudadUbigeo": "1501", "aceptaPlataforma": true,
                                 "aceptaFuturosContactos": true}""".formatted(correo, CLAVE)))
                .andExpect(status().isCreated());

        String token = leer(mvc.perform(post("/api/v1/portal/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"correo\":\"%s\",\"contrasena\":\"%s\"}".formatted(correo, CLAVE)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(), "token");

        mvc.perform(multipart("/api/v1/portal/postulaciones")
                        .file(unCurriculum())
                        .param("vacanteId", String.valueOf(vacanteConBanda))
                        .param("resultadoOrgulloso", "Saqué adelante el portal de mi anterior empresa")
                        .param("aceptaTratamiento", "true")
                        .param("pretensionMonto", pretension)
                        .param("pretensionMoneda", "PEN")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isCreated());
        return token;
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
