package com.renaser.ai.ai_engine.integracion;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.renaser.ai.ai_engine.ai.dto.RespuestaModelo;
import com.renaser.ai.ai_engine.ai.service.ClienteModelo;
import com.renaser.ai.ai_engine.ai.service.ColaCalificacionIa;
import com.renaser.ai.ai_engine.integracion.soporte.ImagenesDeContenedores;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
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

import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.List;
import java.util.function.BooleanSupplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * El coste de la IA por empresa, de punta a punta (pieza E).
 *
 * <p>Aquí la calificación está ENCENDIDA —al revés que en {@code FlujoDosEmpresasIT}— y el
 * modelo es un doble que contesta la lectura del currículum con tokens fijos: es lo que
 * permite afirmar un costo exacto. Se recorre la vida completa del tope de una empresa:
 *
 * <ol>
 *   <li>Nace con tope desde el alta, y cada ejecución escribe su costo con la tarifa.
 *   <li>Al cruzar el 80% suena la campana: un correo, una sola vez por mes.
 *   <li>Al 100% lo nuevo queda EN_ESPERA — no falla, no se publica, y el candidato ni se
 *       entera: su postulación sigue como siempre.
 *   <li>Renaser sube el tope por el endpoint y el barrido despierta lo que esperaba.
 * </ol>
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@Testcontainers
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@DisplayName("Plataforma · El tope de IA de una empresa, de punta a punta")
public class FlujoPlataformaIT {

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
        // La calificación queda ENCENDIDA (su valor por defecto): el tope se comprueba al
        // encolar, y con la cola apagada no habría nada que frenar ni que despertar.
    }

    /** El doble del modelo: solo la lectura de datos, con tokens fijos para poder cobrarla. */
    @TestConfiguration
    static class ConfiguracionDePrueba {
        @Bean
        @Primary
        ClienteModelo clienteModeloDePrueba() {
            return new ClienteModelo() {
                @Override
                public RespuestaModelo preguntar(String agenteCodigo, String instruccion,
                                                 String contenido) {
                    return preguntar(agenteCodigo, instruccion, contenido, true);
                }

                @Override
                public RespuestaModelo preguntar(String agenteCodigo, String instruccion,
                                                 String contenido, boolean razona) {
                    if (!"DATOS_CV".equals(agenteCodigo)) {
                        throw new IllegalStateException("agente inesperado: " + agenteCodigo);
                    }
                    // 1200 de entrada y 340 de salida: con la tarifa provisional de la
                    // V38 (0.27/1.10 por millón) son exactamente 0.0007 USD.
                    return new RespuestaModelo("""
                            {"nombre":"Se Lee Del Curriculum","email":"x@correo.pe",
                             "telefono":"999888777","perfilResumen":"Analista.",
                             "habilidades":["SQL"],"experienciaMesesTotal":48,
                             "ultimoPuesto":"Analista","ultimaEmpresa":"Andina",
                             "ultimaMesesDuracion":24,"educacionMaxima":"Universitaria completa"}""",
                            "deepseek-v4-flash", "deepseek", "prueba", 1200, 340);
                }
            };
        }
    }

    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired ColaCalificacionIa cola;
    final ObjectMapper json = new ObjectMapper();

    static final String MES = YearMonth.now(ZoneId.of("America/Lima")).toString();

    static String tokenPlataforma;
    static String tokenAcme;
    static long plataformaId;
    static long acmeId;
    static long anaId;
    static long vacanteAcmeId;
    static long postulacionEnEsperaId;

    // ============ El alta con tope ============

    @DisplayName("ACME nace con su tope de IA sembrado, publica su texto legal y su vacante")
    @Test
    @Order(1)
    void acmeNaceConTope() throws Exception {
        tokenPlataforma = leer(mvc.perform(post("/api/v1/panel/auth/dev-login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"usuarioRenaserOsId\":\"dev-1\"}"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(), "token");
        plataformaId = jdbc.queryForObject(
                "select id from organizacion where es_plataforma", Long.class);

        // El alta acepta el tope: 10 USD al mes, a propósito bajito para esta historia
        String respuesta = conToken(post("/api/v1/panel/plataforma/empresas"), tokenPlataforma, """
                {"nombre": "Acme S.A.C.", "codigo": "ACME",
                 "correoAdministrador": "ana@acme.pe", "topeMensualIa": "10"}""")
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        acmeId = json.readTree(respuesta).get("id").asLong();
        assertThat(jdbc.queryForObject("""
                select valor from parametro
                 where organizacion_id = %d and codigo = 'tope_mensual_ia'"""
                .formatted(acmeId), String.class)).isEqualTo("10");

        // Ana canjea, se completa los roles y deja la vacante publicada (con su texto
        // legal antes, que publicar lo exige desde la pieza D)
        String urlInvitacion = json.readTree(respuesta).get("urlInvitacion").asText();
        String token = urlInvitacion.substring(urlInvitacion.indexOf("token=") + 6);
        String sesion = mvc.perform(post("/api/v1/panel/auth/invitacion")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"token": "%s", "nombre": "Ana", "apellidos": "Torres",
                                 "contrasena": "una-clave-de-panel-larga"}""".formatted(token)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        anaId = json.readTree(sesion).get("usuarioId").asLong();
        tokenAcme = leer(sesion, "token");
        conToken(post("/api/v1/panel/usuarios/" + anaId + "/roles"), tokenAcme,
                "{\"roles\":[\"ADMINISTRADOR\",\"DIRECCION\",\"TALENTO\"]}")
                .andExpect(status().isOk());
        conToken(post("/api/v1/panel/textos-consentimiento"), tokenAcme, """
                {"tipo": "PROCESO", "texto": "Acme S.A.C. tratará tus datos para evaluar tu \
                postulación."}""")
                .andExpect(status().isCreated());

        conToken(post("/api/v1/panel/areas"), tokenAcme, "{\"nombre\":\"Operaciones\"}")
                .andExpect(status().isCreated());
        Long areaId = jdbc.queryForObject(
                "select id from area where organizacion_id = " + acmeId, Long.class);
        long solicitudId = Long.parseLong(leer(conToken(post("/api/v1/panel/solicitudes"), tokenAcme, """
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
        conToken(post("/api/v1/panel/solicitudes/" + solicitudId + "/aprobacion"), tokenAcme,
                "{\"motivo\":\"Hay presupuesto\"}").andExpect(status().isOk());
        long puestoId = Long.parseLong(leer(conToken(post("/api/v1/panel/puestos"), tokenAcme, """
                {"codigo": "OPS_WEB", "nombre": "Analista de operaciones",
                 "nivelPuestoCodigo": "EJECUCION", "familiaCodigo": "TECNOLOGIA"}""")
                .andReturn().getResponse().getContentAsString(), "id"));
        vacanteAcmeId = Long.parseLong(leer(conToken(post("/api/v1/panel/vacantes"), tokenAcme, """
                {"solicitudTalentoId": %d, "puestoId": %d,
                 "titulo": "Analista de operaciones", "descripcion": "Turno completo",
                 "tipoCierre": "PERMANENTE", "responsableUsuarioId": %d}"""
                .formatted(solicitudId, puestoId, anaId))
                .andReturn().getResponse().getContentAsString(), "id"));
        Long plantillaId = jdbc.queryForObject("""
                select id from plantilla_evaluacion
                 where organizacion_id = %d and nivel_puesto_codigo = 'EJECUCION'"""
                .formatted(plataformaId), Long.class);
        conToken(post("/api/v1/panel/vacantes/" + vacanteAcmeId + "/plantilla-evaluacion"), tokenAcme,
                "{\"plantillaEvaluacionId\": %d}".formatted(plantillaId)).andExpect(status().isOk());
        Long versionPruebaId = armarUnaPruebaValida(tokenPlataforma);
        conToken(post("/api/v1/panel/vacantes/" + vacanteAcmeId + "/plantilla-prueba"), tokenAcme,
                "{\"versionPlantillaPruebaId\": %d}".formatted(versionPruebaId)).andExpect(status().isOk());
        conToken(post("/api/v1/panel/vacantes/" + vacanteAcmeId + "/publicacion"), tokenAcme, null)
                .andExpect(status().isOk());
    }

    // ============ El costo de cada ejecución ============

    @DisplayName("Cada lectura de currículum escribe su costo con la tarifa vigente")
    @Test
    @Order(2)
    void cadaEjecucionEscribeSuCosto() throws Exception {
        postular("carmen@correo.pe", "Carmen");
        esperarLecturaDe("carmen@correo.pe");

        // 1200 tokens a 0.27 + 340 a 1.10, entre un millón: 0.0007 USD, escala de la columna
        assertThat(jdbc.queryForObject("""
                select costo from ejecucion_ia
                 where organizacion_id = %d and agente_codigo = 'DATOS_CV' and es_exitosa"""
                .formatted(acmeId), BigDecimal.class))
                .isEqualByComparingTo("0.0007");
        // Lejos del 80% del tope de 10: ninguna campana suena todavía
        assertThat(contar("select count(*) from correo_enviado where "
                + "plantilla_correo_codigo = 'TOPE_IA_AVISO'")).isZero();
    }

    // ============ La campana del 80% ============

    @DisplayName("Al cruzar el 80% del tope sale UN aviso, y el mes no lo repite")
    @Test
    @Order(3)
    void alCruzarEl80SaleUnAviso() throws Exception {
        // Se simula el consumo del mes con una ejecución sembrada: 8.50 de 10 es el 85%
        sembrarConsumo("8.50");

        postular("elena@correo.pe", "Elena");
        esperarLecturaDe("elena@correo.pe");

        // El aviso salió al administrador de ACME con la plantilla de ACME. (El de la
        // plataforma no: su único usuario es el bootstrap de dev-login, sin correo.)
        assertThat(contar("select count(*) from correo_enviado where "
                + "plantilla_correo_codigo = 'TOPE_IA_AVISO'")).isEqualTo(1);
        assertThat(jdbc.queryForObject("""
                select valor from parametro
                 where organizacion_id = %d and codigo = 'aviso_tope_enviado_mes'"""
                .formatted(acmeId), String.class)).isEqualTo(MES);

        // Otra candidata en el mismo mes: la campana no vuelve a sonar
        postular("fabiola@correo.pe", "Fabiola");
        esperarLecturaDe("fabiola@correo.pe");
        assertThat(contar("select count(*) from correo_enviado where "
                + "plantilla_correo_codigo = 'TOPE_IA_AVISO'")).isEqualTo(1);
    }

    // ============ El freno del 100% ============

    @DisplayName("Al 100% el trabajo nuevo queda EN_ESPERA y la candidata ni se entera")
    @Test
    @Order(4)
    void al100LoNuevoEsperaYLaCandidataNiSeEntera() throws Exception {
        // Otra ejecución sembrada deja el mes en ~12.50 de 10: el cupo está agotado
        sembrarConsumo("4.00");
        int ejecucionesAntes = contar("select count(*) from ejecucion_ia");

        String tokenGloria = postular("gloria@correo.pe", "Gloria");
        postulacionEnEsperaId = jdbc.queryForObject("""
                select p.id from postulacion p join usuario u on u.id = p.usuario_id
                 where u.correo = 'gloria@correo.pe'""", Long.class);

        // El trabajo nació EN_ESPERA: no se publicó, nadie lo ejecuta y no falla
        assertThat(jdbc.queryForObject("""
                select estado from trabajo_ia
                 where postulacion_id = %d and agente_codigo = 'DATOS_CV'"""
                .formatted(postulacionEnEsperaId), String.class)).isEqualTo("EN_ESPERA");
        assertThat(contar("select count(*) from ejecucion_ia")).isEqualTo(ejecucionesAntes);

        // Y a Gloria el tope no le existe: postuló, su postulación avanza como siempre —
        // lo que espera es la calificación, no la puerta (spec E §2)
        mvc.perform(get("/api/v1/portal/postulaciones")
                        .header("Authorization", "Bearer " + tokenGloria))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].estado").value("PERFIL_TURNO_CANDIDATO"));
    }

    // ============ Subir el tope y despertar ============

    @DisplayName("Renaser sube el tope por el endpoint y el barrido despierta lo que esperaba")
    @Test
    @Order(5)
    void subirElTopeDespiertaLoQueEsperaba() throws Exception {
        conToken(put("/api/v1/panel/plataforma/empresas/" + acmeId + "/tope-ia"),
                tokenPlataforma, "{\"tope\":\"100\"}")
                .andExpect(status().isOk());
        assertThat(jdbc.queryForObject("""
                select valor from parametro
                 where organizacion_id = %d and codigo = 'tope_mensual_ia'"""
                .formatted(acmeId), String.class)).isEqualTo("100");

        // El mismo barrido de los atascados: en producción corre solo cada cinco
        // minutos; aquí se le llama para no esperarlo.
        cola.reintentarAtascados();

        esperarA(() -> "TERMINADO".equals(jdbc.queryForObject("""
                select estado from trabajo_ia
                 where postulacion_id = %d and agente_codigo = 'DATOS_CV'"""
                .formatted(postulacionEnEsperaId), String.class)),
                "el trabajo dormido despierte y termine");
        assertThat(contar("select count(*) from trabajo_ia where estado = 'EN_ESPERA'")).isZero();

        // La mirada de la dueña: el consumo del mes por empresa y por agente, y la ficha
        // del continente con su tope y su gasto
        conTokenGet("/api/v1/panel/plataforma/consumo?mes=" + MES, tokenPlataforma)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.organizacionId == %d)].costoTotal".formatted(acmeId))
                        .exists())
                .andExpect(jsonPath("$[?(@.organizacionId == %d)].porAgente[0].agenteCodigo"
                        .formatted(acmeId)).exists());
        conTokenGet("/api/v1/panel/plataforma/empresas", tokenPlataforma)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].codigo").value("ACME"))
                .andExpect(jsonPath("$[0].esActiva").value(true))
                .andExpect(jsonPath("$[0].topeMensualIa").value("100"))
                .andExpect(jsonPath("$[0].personalizacion.bancoPropio").value(false));
        // El total del mes lleva las dos siembras y las lecturas de verdad: más de 12.50
        String consumo = conTokenGet("/api/v1/panel/plataforma/consumo?mes=" + MES, tokenPlataforma)
                .andReturn().getResponse().getContentAsString();
        BigDecimal totalAcme = null;
        for (var empresa : json.readTree(consumo)) {
            if (empresa.get("organizacionId").asLong() == acmeId) {
                totalAcme = new BigDecimal(empresa.get("costoTotal").asText());
            }
        }
        assertThat(totalAcme).isNotNull().isGreaterThan(new BigDecimal("12.50"));
    }

    // ============ Apoyo ============

    /** Crea la cuenta, entra y postula a la vacante de ACME con un PDF legible propio. */
    private String postular(String correo, String nombre) throws Exception {
        mvc.perform(post("/api/v1/portal/cuentas")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                        {"nombre":"%s","apellidos":"Quispe","correo":"%s",
                         "contrasena":"unaClaveLarga123","aceptaProceso":true,
                         "aceptaFuturosContactos":false}""".formatted(nombre, correo)))
                .andExpect(status().isCreated());
        String token = leer(mvc.perform(post("/api/v1/portal/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"correo\":\"%s\",\"contrasena\":\"unaClaveLarga123\"}"
                                .formatted(correo)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(), "token");

        // Cada candidata con SU pdf: dos archivos idénticos compartirían la lectura
        // (RF-161) y esta prueba necesita una llamada al modelo por candidata.
        MockMultipartFile cv = new MockMultipartFile("cv", "cv-" + nombre + ".pdf",
                "application/pdf", curriculumEnPdf(nombre));
        mvc.perform(multipart("/api/v1/portal/postulaciones")
                        .file(cv)
                        .param("vacanteId", String.valueOf(vacanteAcmeId))
                        .param("resultadoOrgulloso", "Ordené un almacén a ciegas")
                        .param("aceptaTratamiento", "true")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isCreated());
        return token;
    }

    /** Un PDF de verdad: el lector extrae el texto y un byte inventado lo dejaría fallido. */
    private byte[] curriculumEnPdf(String nombre) throws Exception {
        try (PDDocument documento = new PDDocument();
             ByteArrayOutputStream salida = new ByteArrayOutputStream()) {
            PDPage pagina = new PDPage();
            documento.addPage(pagina);
            try (PDPageContentStream lienzo = new PDPageContentStream(documento, pagina)) {
                lienzo.beginText();
                lienzo.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 11);
                lienzo.setLeading(16f);
                lienzo.newLineAtOffset(50, 720);
                for (String linea : List.of(nombre + " Quispe - Analista de operaciones",
                        "EXPERIENCIA", "Ordene un almacen que llevaba anos a ciegas.")) {
                    lienzo.showText(linea);
                    lienzo.newLine();
                }
                lienzo.endText();
            }
            documento.save(salida);
            return salida.toByteArray();
        }
    }

    /**
     * Simula gasto del mes: una ejecución con costo, colgada de un trabajo terminado.
     * Por jdbc y no por el doble del modelo, porque llegar a 8.50 USD a 0.0007 por
     * lectura serían doce mil postulaciones.
     */
    private void sembrarConsumo(String costo) {
        Long trabajoId = jdbc.queryForObject("""
                insert into trabajo_ia (organizacion_id, agente_codigo, estado, intentos, creado_en)
                values (%d, 'DATOS_CV', 'TERMINADO', 1, now()) returning id""".formatted(acmeId),
                Long.class);
        jdbc.update("""
                insert into ejecucion_ia (trabajo_ia_id, organizacion_id, agente_codigo,
                        version_agente, objetivo, modelo, proveedor, envio, tokens_entrada,
                        tokens_salida, costo, es_exitosa, creado_en)
                values (?, ?, 'DATOS_CV', 1, 'consumo sembrado para la prueba',
                        'deepseek-v4-flash', 'deepseek', 'siembra', 1000000, 100000, ?, true, now())""",
                trabajoId, acmeId, new BigDecimal(costo));
    }

    /**
     * Espera a que la lectura del currículum de ESA candidata termine. Por postulación y
     * no con un conteo global: las siembras de consumo también dejan trabajos TERMINADO
     * de DATOS_CV, y un conteo suelto daría la espera por cumplida con la lectura de
     * verdad todavía en el aire.
     */
    private void esperarLecturaDe(String correo) {
        esperarA(() -> contar(("""
                select count(*) from trabajo_ia t
                  join postulacion p on p.id = t.postulacion_id
                  join usuario u on u.id = p.usuario_id
                 where u.correo = '%s' and t.agente_codigo = 'DATOS_CV'
                   and t.estado = 'TERMINADO'""").formatted(correo)) == 1,
                "la lectura del currículum de " + correo + " termine");
    }

    private void esperarA(BooleanSupplier condicion, String que) {
        long limite = System.currentTimeMillis() + 60_000;
        while (System.currentTimeMillis() < limite) {
            if (condicion.getAsBoolean()) {
                return;
            }
            try {
                Thread.sleep(250);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
        fail("Se agotó la espera a que " + que + ". Trabajos: "
                + jdbc.queryForList("select id, agente_codigo, estado, intentos from trabajo_ia"));
    }

    // La mínima prueba del puesto publicable, calcada de FlujoDosEmpresasIT.
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
                    "{\"codigo\":\"UNIV_PL_%d\",\"enunciado\":\"Pregunta universal %d\",\"tipo\":\"UNIVERSAL\"}"
                            .formatted(i, i))
                    .andReturn().getResponse().getContentAsString(), "id"));
            conToken(post("/api/v1/panel/plantillas-prueba/versiones/" + versionId + "/preguntas"), token,
                    "{\"preguntaPruebaId\": %d}".formatted(id)).andExpect(status().isOk());
        }
        for (int i = 0; i < 3; i++) {
            long id = Long.parseLong(leer(conToken(post("/api/v1/panel/plantillas-prueba/preguntas"), token,
                    "{\"codigo\":\"ESP_PL_%d\",\"enunciado\":\"Pregunta específica %d\",\"tipo\":\"ESPECIFICA\"}"
                            .formatted(i, i))
                    .andReturn().getResponse().getContentAsString(), "id"));
            conToken(post("/api/v1/panel/plantillas-prueba/versiones/" + versionId + "/preguntas"), token,
                    "{\"preguntaPruebaId\": %d}".formatted(id)).andExpect(status().isOk());
        }
        conToken(post("/api/v1/panel/plantillas-prueba/versiones/" + versionId + "/rubrica"), token, """
                {"codigo":"RESULTADO_PL","nombre":"Resultado","puntos":100,"metodoVerificacion":"PERSONA"}""")
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
