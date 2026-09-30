package com.renaser.ai.ai_engine.integracion;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.renaser.ai.ai_engine.colaborador.dto.DtosColaborador.Filtros;
import com.renaser.ai.ai_engine.colaborador.repository.ListadoDeColaboradoresRepository;
import com.renaser.ai.ai_engine.integracion.soporte.ImagenesDeContenedores;
import com.renaser.ai.ai_engine.seguridad.service.ServicioToken;

import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
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
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.rabbitmq.RabbitMQContainer;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * La gestión de personas (V64), de punta a punta contra Postgres y HTTP.
 *
 * <p>Recorre los criterios de la spec {@code rrhh-01-gestion-de-personas} que se pueden probar
 * por la API: la migración y sus permisos, la sesión del menú, sedes y cargos, el alta, el
 * documento repetido, los círculos de jefes, contratar desde la ficha, el alta de un contratado,
 * el aviso de pendientes, la carga por Excel, los cambios, el cese, el reingreso, el sueldo
 * oculto y el aislamiento entre empresas.
 *
 * <p>Fechas relativas a hoy en Lima, sin fechas quemadas. Los pasos van en orden y comparten
 * estado.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@DisplayName("Gestión de personas: la ficha del colaborador")
public class FlujoColaboradoresIT {

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

    private static final String COLABORADORES = "/api/v1/panel/colaboradores";
    private static final LocalDate HOY = LocalDate.now(ZoneId.of("America/Lima"));

    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired ServicioToken tokens;
    @Autowired ListadoDeColaboradoresRepository listado;
    @Autowired PlatformTransactionManager transacciones;
    final ObjectMapper json = new ObjectMapper();

    static long plataforma;
    static long acme;
    static String tokenDev;
    static String tokenTalento;
    static String tokenDireccion;
    static String tokenResponsable;
    static String tokenAcme;
    static long areaFinanzas;
    static long areaOperaciones;
    static long areaVentas;
    static long sedeLima;
    static long sedeVentanilla;
    static long cargoAnalista;
    static long cargoJefe;
    static long cargoAsistente;
    static long cargoGerente;
    static long vacante;
    static long enPrueba;
    static long enDecision;
    static long contratadoAntes;
    static long usuarioEnPrueba;
    static long colaboradorA;
    static long colaboradorB;
    static long colaboradorC;
    static long colaboradorE;
    static long cambioProgramado;

    // ============ 1. El terreno ============

    @Test
    @Order(1)
    @DisplayName("la plataforma, ACME dada de alta después, tres roles, áreas, una vacante y tres postulaciones")
    void elTerreno() throws Exception {
        tokenDev = leer(mvc.perform(post("/api/v1/panel/auth/dev-login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"usuarioRenaserOsId\":\"dev-personas\"}"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString(), "token");
        plataforma = jdbc.queryForObject("select id from organizacion where es_plataforma", Long.class);
        acme = Long.parseLong(leer(conToken(post("/api/v1/panel/plataforma/empresas"), tokenDev, """
                {"nombre": "Acme S.A.C.", "codigo": "ACME", "correoAdministrador": "ana@acme.pe"}""")
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString(), "id"));

        long talento = equipo(plataforma, "TALENTO", "Tania");
        long direccion = equipo(plataforma, "DIRECCION", "Diego");
        long responsable = equipo(plataforma, "RESPONSABLE_AREA", "Rosa");
        long talentoAcme = equipo(acme, "TALENTO", "Tomas");
        tokenTalento = tokens.emitir(talento, plataforma, "EQUIPO");
        tokenDireccion = tokens.emitir(direccion, plataforma, "EQUIPO");
        tokenResponsable = tokens.emitir(responsable, plataforma, "EQUIPO");
        tokenAcme = tokens.emitir(talentoAcme, acme, "EQUIPO");

        areaFinanzas = area(plataforma, "Finanzas");
        areaOperaciones = area(plataforma, "Operaciones");
        areaVentas = area(plataforma, "Ventas");

        usuarioEnPrueba = candidata("carla@correo.pe", "Carla", "De la Cruz Pérez");
        candidata("dante@correo.pe", "Dante", "Salas");
        candidata("ana.antes@correo.pe", "Ana", "Antes");
    }

    // ============ 2. La migración ============

    @Test
    @Order(2)
    @DisplayName("V64: los cuatro permisos en el grupo Personas; Talento sin ver_sueldos; y ACME, dada de alta después, igual (AC-25)")
    void losPermisos() {
        for (long org : List.of(plataforma, acme)) {
            assertThat(alcance(org, "TALENTO", "ver_colaboradores")).isEqualTo("TODO");
            assertThat(alcance(org, "TALENTO", "editar_colaboradores")).isEqualTo("TODO");
            assertThat(alcance(org, "TALENTO", "editar_estructura")).isEqualTo("TODO");
            assertThat(alcance(org, "TALENTO", "ver_sueldos")).isNull();
            assertThat(alcance(org, "DIRECCION", "ver_sueldos")).isEqualTo("TODO");
            assertThat(alcance(org, "DIRECCION", "editar_colaboradores")).isEqualTo("TODO");
            assertThat(alcance(org, "RESPONSABLE_AREA", "ver_colaboradores")).isNull();
        }
        assertThat(contar("select count(*) from permiso where grupo = 'PERSONAS'")).isEqualTo(4);
    }

    // ============ 3. La sesión del menú ============

    @Test
    @Order(3)
    @DisplayName("la sesión dice nombre, empresa y permisos: Talento ve colaboradores y el Responsable no (AC-01, AC-02)")
    void laSesion() throws Exception {
        JsonNode talento = cuerpo(conToken(get("/api/v1/panel/sesion"), tokenTalento, null)
                .andExpect(status().isOk()));
        assertThat(talento.get("nombre").asText()).isEqualTo("Tania Equipo");
        assertThat(talento.get("empresa").asText()).isNotBlank();
        assertThat(permisosDe(talento)).containsEntry("ver_colaboradores", "TODO")
                .containsEntry("ver_vacantes", "TODO").doesNotContainKey("ver_sueldos");

        JsonNode responsable = cuerpo(conToken(get("/api/v1/panel/sesion"), tokenResponsable, null)
                .andExpect(status().isOk()));
        assertThat(permisosDe(responsable)).doesNotContainKey("ver_colaboradores")
                .containsEntry("elegir_plantilla_prueba", "SUS_VACANTES");
    }

    // ============ 4. Sedes ============

    @Test
    @Order(4)
    @DisplayName("una sede nueva con provincia y código SUNAT; nombre único; y el Responsable la ve sin poder tocarla (AC-05)")
    void lasSedes() throws Exception {
        sedeVentanilla = id(conToken(post("/api/v1/panel/sedes"), tokenTalento, """
                {"nombre": "Planta Ventanilla", "direccion": "Av. Néstor Gambetta 123",
                 "provinciaUbigeo": "0701", "codigoSunat": "0003"}""").andExpect(status().isCreated()));
        sedeLima = id(conToken(post("/api/v1/panel/sedes"), tokenTalento, """
                {"nombre": "Sede Lima", "provinciaUbigeo": "1501"}""").andExpect(status().isCreated()));

        conToken(post("/api/v1/panel/sedes"), tokenTalento, "{\"nombre\": \"planta ventanilla\"}")
                .andExpect(status().isConflict());
        conToken(post("/api/v1/panel/sedes"), tokenTalento, "{\"nombre\": \"Otra\", \"codigoSunat\": \"12\"}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value(containsString("4 dígitos")));

        conToken(get("/api/v1/panel/sedes"), tokenResponsable, null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.puedeEditar").value(false))
                .andExpect(jsonPath("$.sedes[?(@.nombre == 'Planta Ventanilla')].codigoSunat").value("0003"));
        conToken(post("/api/v1/panel/sedes"), tokenResponsable, "{\"nombre\": \"No\"}")
                .andExpect(status().isForbidden());
        // Una sede de la plataforma no se toca desde ACME: 404.
        conToken(put("/api/v1/panel/sedes/" + sedeLima), tokenAcme, "{\"nombre\": \"Mía\"}")
                .andExpect(status().isNotFound());
    }

    // ============ 5. Cargos ============

    @Test
    @Order(5)
    @DisplayName("los cargos son el catálogo de puestos: se añaden, y renombrar cambia el nombre también en la vacante (AC-06)")
    void losCargos() throws Exception {
        cargoAnalista = cargo(tokenTalento, "Analista");
        cargoJefe = cargo(tokenTalento, "Jefe de finanzas");
        cargoAsistente = cargo(tokenTalento, "Asistente");
        cargoGerente = cargo(tokenTalento, "Gerente");
        conToken(post("/api/v1/panel/cargos"), tokenTalento, """
                {"nombre": "ANALISTA", "nivelPuestoCodigo": "EJECUCION", "familiaCodigo": "OPERACIONES"}""")
                .andExpect(status().isConflict());

        // Una vacante que usa el cargo Asistente, con dos postulaciones en carrera.
        vacante = vacante(plataforma, cargoAsistente, areaFinanzas,
                jdbc.queryForObject("select id from usuario where correo = 'rosa@equipo.pe'", Long.class),
                "Asistente contable");
        long carla = jdbc.queryForObject("select id from usuario where correo = 'carla@correo.pe'", Long.class);
        long dante = jdbc.queryForObject("select id from usuario where correo = 'dante@correo.pe'", Long.class);
        long ana = jdbc.queryForObject("select id from usuario where correo = 'ana.antes@correo.pe'", Long.class);
        enPrueba = postulacion(vacante, carla, "PRUEBA_TURNO_CANDIDATO");
        enDecision = postulacion(vacante, dante, "DECISION_POR_CONFIRMAR");
        // Una contratación anterior a esta spec, como si viniera de la API.
        contratadoAntes = postulacion(vacante, ana, "CONTRATADO");
        jdbc.update("insert into transicion_estado (postulacion_id, estado_nuevo_codigo, es_sistema, "
                + "ocurrida_en) values (?, 'CONTRATADO', true, now() - make_interval(days => 40))", contratadoAntes);
        jdbc.update("insert into dato_cv (postulacion_id, telefono) values (?, '987654321')", enPrueba);

        JsonNode cargos = cuerpo(conToken(get("/api/v1/panel/cargos"), tokenTalento, null).andExpect(status().isOk()));
        JsonNode asistente = buscar(cargos.get("cargos"), "id", cargoAsistente);
        assertThat(asistente.get("vacantes").asLong()).isEqualTo(1);

        conToken(put("/api/v1/panel/cargos/" + cargoAsistente), tokenTalento, "{\"nombre\": \"Asistente contable\"}")
                .andExpect(status().isNoContent());
        conToken(get("/api/v1/panel/puestos"), tokenTalento, null)
                .andExpect(jsonPath("$[?(@.id == " + cargoAsistente + ")].nombre").value("Asistente contable"));
        assertThat(contar("select count(*) from auditoria where accion = 'renombrar_puesto'")).isEqualTo(1);

        // Desactivar lo saca de las opciones del alta; reactivar lo devuelve.
        conToken(post("/api/v1/panel/cargos/" + cargoGerente + "/desactivacion"), tokenTalento, null)
                .andExpect(status().isNoContent());
        JsonNode opciones = cuerpo(conToken(get(COLABORADORES + "/opciones"), tokenTalento, null));
        assertThat(ids(opciones.get("cargos"))).doesNotContain(cargoGerente).contains(cargoAnalista);
        assertThat(opciones.get("puedeVerSueldos").asBoolean()).isFalse();
        conToken(post("/api/v1/panel/cargos/" + cargoGerente + "/reactivacion"), tokenTalento, null)
                .andExpect(status().isNoContent());
    }

    // ============ 6. El alta manual ============

    @Test
    @Order(6)
    @DisplayName("un alta de hoy sale Activa con cargo, área, sede y jefe; su historial abre con «Ingreso» y la auditoría la registra (AC-07)")
    void elAltaManual() throws Exception {
        colaboradorA = id(conToken(post(COLABORADORES), tokenTalento,
                alta("01", "40111222", "Luis", "Torres", HOY.minusYears(3), sedeLima, areaFinanzas,
                        cargoJefe, null, "01", null, null)).andExpect(status().isCreated()));
        // Talento manda un sueldo: no lo puede ver, así que ni se guarda.
        colaboradorB = id(conToken(post(COLABORADORES), tokenTalento,
                alta("01", " 45.123.456 ", "Ana María", "Rivas Sánchez", HOY, sedeLima, areaFinanzas,
                        cargoAnalista, colaboradorA, "03", HOY.plusMonths(6), 4500)).andExpect(status().isCreated()));
        assertThat(jdbc.queryForObject("select sueldo_base from situacion_laboral where colaborador_id = ?",
                java.math.BigDecimal.class, colaboradorB)).isNull();
        // Dirección sí lo guarda.
        colaboradorC = id(conToken(post(COLABORADORES), tokenDireccion,
                alta("01", "41222333", "Carlos", "Quispe", HOY.minusMonths(2), sedeVentanilla, areaOperaciones,
                        cargoAsistente, colaboradorA, "01", null, 3200)).andExpect(status().isCreated()));

        JsonNode lista = cuerpo(conToken(get(COLABORADORES), tokenTalento, null).andExpect(status().isOk()));
        JsonNode filaB = buscar(lista.get("filas"), "id", colaboradorB);
        assertThat(filaB.get("estado").asText()).isEqualTo("ACTIVO");
        assertThat(filaB.get("numeroDocumento").asText()).isEqualTo("45123456");
        assertThat(filaB.get("nombreCompleto").asText()).isEqualTo("Rivas Sánchez, Ana María");
        assertThat(filaB.get("cargo").asText()).isEqualTo("Analista");
        assertThat(filaB.get("area").asText()).isEqualTo("Finanzas");
        assertThat(filaB.get("sede").asText()).isEqualTo("Sede Lima");
        assertThat(filaB.get("jefe").asText()).isEqualTo("Torres, Luis");
        assertThat(lista.get("total").asLong()).isEqualTo(3);
        // Ordenada por apellidos.
        assertThat(nombres(lista.get("filas"))).containsExactly("Quispe, Carlos", "Rivas Sánchez, Ana María",
                "Torres, Luis");

        JsonNode historial = cuerpo(conToken(get(COLABORADORES + "/" + colaboradorB + "/historial"), tokenTalento, null));
        assertThat(historial).hasSize(1);
        assertThat(historial.get(0).get("tipo").asText()).isEqualTo("INGRESO");
        assertThat(historial.get(0).get("motivo").asText()).isEqualTo("Ingreso");
        assertThat(historial.get(0).get("registradoPor").asText()).isEqualTo("Tania Equipo");
        assertThat(contar("select count(*) from auditoria where accion = 'alta_colaborador'")).isEqualTo(3);
    }

    @Test
    @Order(7)
    @DisplayName("un alta con ingreso futuro sale «Por ingresar»; y las validaciones del alta se explican (AC-08)")
    void porIngresarYValidaciones() throws Exception {
        long futura = id(conToken(post(COLABORADORES), tokenTalento,
                alta("07", "PA12345", "Irene", "Vega", HOY.plusDays(15), sedeLima, areaVentas,
                        cargoAnalista, null, "01", null, null)).andExpect(status().isCreated()));
        conToken(get(COLABORADORES + "/" + futura), tokenTalento, null)
                .andExpect(jsonPath("$.estado").value("POR_INGRESAR"));

        conToken(post(COLABORADORES), tokenTalento, alta("01", "4512345", "X", "Y", HOY, sedeLima,
                areaFinanzas, cargoAnalista, null, "01", null, null))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("El DNI tiene que tener 8 dígitos"));
        conToken(post(COLABORADORES), tokenTalento, alta("01", "49999999", "X", "Y", HOY, sedeLima,
                areaFinanzas, cargoAnalista, null, "03", null, null))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value(containsString("necesita la fecha de fin")));
        Map<String, Object> menor = altaComoMapa("01", "48888888", "X", "Y", HOY, sedeLima, areaFinanzas,
                cargoAnalista, null, "01", null, null);
        ((Map<String, Object>) menor.get("persona")).put("fechaNacimiento", HOY.minusYears(13).toString());
        conToken(post(COLABORADORES), tokenTalento, json.writeValueAsString(menor))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value(containsString("14 años")));
    }

    // ============ 7. El documento repetido ============

    @Test
    @Order(8)
    @DisplayName("el mismo DNI en la empresa se rechaza con «Ya es colaborador» y su ficha; en otra empresa, sí se puede (AC-09)")
    void elDocumentoRepetido() throws Exception {
        conToken(post(COLABORADORES), tokenTalento, alta("01", "45123456", "Otra", "Persona", HOY,
                sedeLima, areaFinanzas, cargoAnalista, null, "01", null, null))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("Ya es colaborador"))
                .andExpect(jsonPath("$.colaboradorId").value(colaboradorB))
                .andExpect(jsonPath("$.cesado").value(false));

        long sedeAcme = id(conToken(post("/api/v1/panel/sedes"), tokenAcme, "{\"nombre\": \"Central\"}")
                .andExpect(status().isCreated()));
        long areaAcme = area(acme, "Obras");
        long cargoAcme = id(conToken(post("/api/v1/panel/cargos"), tokenAcme, """
                {"nombre": "Capataz", "nivelPuestoCodigo": "EJECUCION", "familiaCodigo": "OPERACIONES"}""")
                .andExpect(status().isCreated()));
        conToken(post(COLABORADORES), tokenAcme, alta("01", "45123456", "Ana María", "Rivas", HOY,
                sedeAcme, areaAcme, cargoAcme, null, "01", null, null))
                .andExpect(status().isCreated());
    }

    // ============ 8. Los círculos de jefes ============

    @Test
    @Order(9)
    @DisplayName("un jefe que cerraría un círculo, o uno mismo como jefe, se rechaza explicándolo (AC-10)")
    void losCirculos() throws Exception {
        // B reporta a A: poner a B como jefe de A cierra el círculo.
        conToken(post(COLABORADORES + "/" + colaboradorA + "/cambios"), tokenTalento,
                cambio(HOY, "CAMBIO_JEFE", null, sedeLima, areaFinanzas, cargoJefe, colaboradorB, "01", null))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value(containsString("círculo")));
        conToken(post(COLABORADORES + "/" + colaboradorA + "/cambios"), tokenTalento,
                cambio(HOY, "CAMBIO_JEFE", null, sedeLima, areaFinanzas, cargoJefe, colaboradorA, "01", null))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value(containsString("su propio jefe")));
    }

    // ============ 9. Contratar desde la ficha ============

    @Test
    @Order(10)
    @DisplayName("Dirección contrata desde la etapa Prueba con motivo: decisión en verde, Contratado y sin correo; Talento no puede (AC-11, AC-12)")
    void contratar() throws Exception {
        conToken(get("/api/v1/panel/postulaciones/" + enPrueba), tokenDireccion, null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.puedeContratar").value(true));
        conToken(get("/api/v1/panel/postulaciones/" + enPrueba), tokenTalento, null)
                .andExpect(jsonPath("$.puedeContratar").value(false));
        conToken(post("/api/v1/panel/postulaciones/" + enPrueba + "/decision"), tokenTalento,
                "{\"semaforo\": \"VERDE\", \"motivo\": \"Nos convence\"}")
                .andExpect(status().isForbidden());

        int correosAntes = contar("select count(*) from correo_enviado where usuario_id = " + usuarioEnPrueba);
        conToken(post("/api/v1/panel/postulaciones/" + enPrueba + "/decision"), tokenDireccion,
                "{\"semaforo\": \"VERDE\", \"motivo\": \"Resolvió el caso mejor que nadie\"}")
                .andExpect(status().isOk());
        assertThat(jdbc.queryForObject("select estado_codigo from postulacion where id = ?", String.class, enPrueba))
                .isEqualTo("CONTRATADO");
        assertThat(jdbc.queryForObject("select semaforo || '|' || motivo from decision where postulacion_id = ?",
                String.class, enPrueba)).isEqualTo("VERDE|Resolvió el caso mejor que nadie");
        assertThat(contar("select count(*) from correo_enviado where usuario_id = " + usuarioEnPrueba))
                .isEqualTo(correosAntes);

        // Contratar dos veces no crea dos decisiones.
        conToken(post("/api/v1/panel/postulaciones/" + enPrueba + "/decision"), tokenDireccion,
                "{\"semaforo\": \"VERDE\", \"motivo\": \"Otra vez\"}")
                .andExpect(status().isConflict());
        assertThat(contar("select count(*) from decision where postulacion_id = " + enPrueba)).isEqualTo(1);

        conToken(get("/api/v1/panel/postulaciones/" + enPrueba), tokenDireccion, null)
                .andExpect(jsonPath("$.puedeContratar").value(false))
                .andExpect(jsonPath("$.puedeDarDeAlta").value(true));
    }

    // ============ 10. Dar de alta a un contratado ============

    @Test
    @Order(11)
    @DisplayName("el alta de un contratado sale precargada y queda enlazada; un segundo alta desde ella no es posible (AC-13)")
    void elAltaDelContratado() throws Exception {
        JsonNode precarga = cuerpo(conToken(get(COLABORADORES + "/precarga?postulacion=" + enPrueba),
                tokenTalento, null).andExpect(status().isOk()));
        assertThat(precarga.get("nombres").asText()).isEqualTo("Carla");
        assertThat(precarga.get("apellidoPaterno").asText()).isEqualTo("De la Cruz Pérez");
        assertThat(precarga.get("correoPersonal").asText()).isEqualTo("carla@correo.pe");
        assertThat(precarga.get("celular").asText()).isEqualTo("987654321");
        assertThat(precarga.get("cargoId").asLong()).isEqualTo(cargoAsistente);
        assertThat(precarga.get("areaId").asLong()).isEqualTo(areaFinanzas);
        assertThat(precarga.has("sueldoBase")).isFalse();

        Map<String, Object> datos = altaComoMapa("01", "47000111", "Carla", "De la Cruz", HOY.plusDays(7),
                sedeLima, areaFinanzas, cargoAsistente, colaboradorA, "01", null, null);
        datos.put("postulacionId", enPrueba);
        long carla = id(conToken(post(COLABORADORES), tokenTalento, json.writeValueAsString(datos))
                .andExpect(status().isCreated()));
        assertThat(jdbc.queryForObject("select postulacion_id from colaborador where id = ?", Long.class, carla))
                .isEqualTo(enPrueba);
        conToken(get(COLABORADORES + "/" + carla), tokenTalento, null)
                .andExpect(jsonPath("$.vacante").value("Asistente contable"))
                .andExpect(jsonPath("$.vacanteId").value(vacante));
        conToken(get("/api/v1/panel/postulaciones/" + enPrueba), tokenDireccion, null)
                .andExpect(jsonPath("$.puedeDarDeAlta").value(false))
                .andExpect(jsonPath("$.puedeVerColaborador").value(true))
                .andExpect(jsonPath("$.colaboradorId").value(carla));

        datos.put("persona", ((Map<?, ?>) altaComoMapa("01", "47000222", "Carla", "Otra", HOY, sedeLima,
                areaFinanzas, cargoAsistente, null, "01", null, null).get("persona")));
        conToken(post(COLABORADORES), tokenTalento, json.writeValueAsString(datos))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value(containsString("ya tiene su ficha")));
    }

    // ============ 11. El aviso de pendientes ============

    @Test
    @Order(12)
    @DisplayName("dos contratados sin ficha, uno de antes de la spec: el aviso dice 2, y «No dar de alta» lo deja en 1 y se audita (AC-14)")
    void losPendientes() throws Exception {
        conToken(post("/api/v1/panel/postulaciones/" + enDecision + "/decision"), tokenDireccion,
                "{\"semaforo\": \"VERDE\", \"motivo\": \"Entra en noviembre\"}")
                .andExpect(status().isOk());
        JsonNode pendientes = cuerpo(conToken(get(COLABORADORES + "/pendientes"), tokenTalento, null)
                .andExpect(status().isOk()));
        assertThat(pendientes).hasSize(2);
        assertThat(ids(pendientes, "postulacionId")).containsExactlyInAnyOrder(enDecision, contratadoAntes);

        conToken(post(COLABORADORES + "/pendientes/" + contratadoAntes + "/descarte"), tokenTalento,
                "{\"motivo\": \"Ya no trabaja aquí\"}").andExpect(status().isNoContent());
        assertThat(cuerpo(conToken(get(COLABORADORES + "/pendientes"), tokenTalento, null))).hasSize(1);
        assertThat(contar("select count(*) from auditoria where accion = 'no_dar_de_alta_contratado' "
                + "and motivo = 'Ya no trabaja aquí'")).isEqualTo(1);
    }

    // ============ 12. La carga por Excel ============

    @Test
    @Order(13)
    @DisplayName("un Excel de 20 filas válidas con 3 jefes dentro del mismo archivo da de alta 20 y actualiza 0 (AC-15)")
    void laCargaValida() throws Exception {
        List<List<Object>> filas = new ArrayList<>();
        for (int i = 0; i < 20; i++) {
            String jefe = i >= 1 && i <= 3 ? "50000000" : "";
            filas.add(fila(String.valueOf(50_000_000 + i), "Persona" + i, "Carga", "Sede Lima", "Finanzas",
                    "Analista", jefe.isEmpty() ? "" : "DNI", jefe, "A plazo indeterminado", "", "General"));
        }
        JsonNode resultado = cuerpo(cargar(tokenTalento, CABECERAS, filas).andExpect(status().isOk()));
        assertThat(resultado.get("altas").asInt()).isEqualTo(20);
        assertThat(resultado.get("actualizados").asInt()).isEqualTo(0);
        long jefe = jdbc.queryForObject("select id from colaborador where numero_documento = '50000000' "
                + "and organizacion_id = ?", Long.class, plataforma);
        assertThat(contar("select count(*) from situacion_laboral where jefe_colaborador_id = " + jefe)).isEqualTo(3);
        assertThat(jdbc.queryForObject("select tipo_motivo from situacion_laboral s join colaborador c "
                + "on c.id = s.colaborador_id where c.numero_documento = '50000005'", String.class))
                .isEqualTo("CARGA_INICIAL");
        assertThat(contar("select count(*) from auditoria where accion = 'cargar_colaboradores_excel'")).isEqualTo(1);
    }

    @Test
    @Order(14)
    @DisplayName("un Excel con una sede inexistente, un DNI de 7 dígitos, un documento repetido y un círculo no guarda nada y enseña todos los errores (AC-16)")
    void laCargaConErrores() throws Exception {
        int antes = contar("select count(*) from colaborador");
        List<List<Object>> filas = List.of(
                fila("60000001", "Uno", "Malo", "Arequipa", "Finanzas", "Analista", "", "", "A plazo indeterminado", "", "General"),
                fila("4512345", "Dos", "Malo", "Sede Lima", "Finanzas", "Analista", "", "", "A plazo indeterminado", "", "General"),
                fila("60000003", "Tres", "Repe", "Sede Lima", "Finanzas", "Analista", "", "", "A plazo indeterminado", "", "General"),
                fila("60000003", "Tres", "Otra", "Sede Lima", "Finanzas", "Analista", "", "", "A plazo indeterminado", "", "General"),
                fila("60000005", "Cinco", "Circulo", "Sede Lima", "Finanzas", "Analista", "DNI", "60000006", "A plazo indeterminado", "", "General"),
                fila("60000006", "Seis", "Circulo", "Sede Lima", "Finanzas", "Analista", "DNI", "60000005", "A plazo indeterminado", "", "General"));
        JsonNode problema = cuerpo(cargar(tokenTalento, CABECERAS, filas).andExpect(status().isBadRequest()));
        List<String> errores = new ArrayList<>();
        problema.get("errores").forEach(e -> errores.add(e.get("fila").asInt() + "|" + e.get("columna").asText()
                + "|" + e.get("valor").asText() + "|" + e.get("mensaje").asText()));
        assertThat(errores).contains(
                "2|Sede|Arequipa|No existe la sede «Arequipa»",
                "3|Número de documento|4512345|El DNI tiene que tener 8 dígitos",
                "4|Número de documento|60000003|El documento se repite en las filas 4, 5",
                "5|Número de documento|60000003|El documento se repite en las filas 4, 5",
                "6|Número de documento del jefe|60000006|Crea un círculo con la fila 7",
                "7|Número de documento del jefe|60000005|Crea un círculo con la fila 6");
        assertThat(contar("select count(*) from colaborador")).isEqualTo(antes);
    }

    @Test
    @Order(15)
    @DisplayName("un Excel con un activo cuyo celular cambió y su cargo es el mismo solo actualiza el celular; con otro cargo, da error (AC-17)")
    void laCargaQueActualiza() throws Exception {
        List<String> cabeceras = List.of("Tipo de documento *", "Número de documento *", "Celular", "Cargo *");
        JsonNode resultado = cuerpo(cargar(tokenTalento, cabeceras,
                List.of(List.of("DNI", "40111222", "999888777", "Jefe de finanzas"))).andExpect(status().isOk()));
        assertThat(resultado.get("altas").asInt()).isZero();
        assertThat(resultado.get("actualizados").asInt()).isEqualTo(1);
        assertThat(jdbc.queryForObject("select celular from colaborador where id = ?", String.class, colaboradorA))
                .isEqualTo("999888777");
        assertThat(jdbc.queryForObject("select nombres from colaborador where id = ?", String.class, colaboradorA))
                .isEqualTo("Luis");

        cargar(tokenTalento, cabeceras, List.of(List.of("DNI", "40111222", "911111111", "Analista")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errores[0].columna").value("Cargo"))
                .andExpect(jsonPath("$.errores[0].mensaje").value("Los cambios de puesto y contrato se registran en su ficha"));
        assertThat(jdbc.queryForObject("select celular from colaborador where id = ?", String.class, colaboradorA))
                .isEqualTo("999888777");
    }

    // ============ 13. Los cambios de la situación laboral ============

    @Test
    @Order(16)
    @DisplayName("un cambio de cargo de hoy por promoción se ve en la lista, la ficha y el historial con antes → después (AC-18)")
    void unCambioDeHoy() throws Exception {
        conToken(post(COLABORADORES + "/" + colaboradorB + "/cambios"), tokenTalento,
                cambio(HOY, "PROMOCION", "Asume la jefatura", sedeLima, areaFinanzas, cargoJefe, colaboradorA,
                        "03", HOY.plusMonths(6))).andExpect(status().isCreated());
        conToken(get(COLABORADORES + "/" + colaboradorB), tokenTalento, null)
                .andExpect(jsonPath("$.situacion.cargo").value("Jefe de finanzas"));
        JsonNode lista = cuerpo(conToken(get(COLABORADORES + "?q=rivas"), tokenTalento, null));
        assertThat(lista.get("filas").get(0).get("cargo").asText()).isEqualTo("Jefe de finanzas");

        JsonNode historial = cuerpo(conToken(get(COLABORADORES + "/" + colaboradorB + "/historial"), tokenTalento, null));
        JsonNode cambio = historial.get(0);
        assertThat(cambio.get("tipo").asText()).isEqualTo("CAMBIO");
        assertThat(cambio.get("fecha").asText()).isEqualTo(HOY.toString());
        assertThat(cambio.get("motivo").asText()).isEqualTo("Promoción");
        assertThat(cambio.get("detalle").asText()).isEqualTo("Asume la jefatura");
        assertThat(cambio.get("registradoPor").asText()).isEqualTo("Tania Equipo");
        assertThat(cambio.get("registradoEn").asText()).isNotBlank();
        assertThat(cambio.get("cambios")).anySatisfy(c -> {
            assertThat(c.get("campo").asText()).isEqualTo("Cargo");
            assertThat(c.get("antes").asText()).isEqualTo("Analista");
            assertThat(c.get("despues").asText()).isEqualTo("Jefe de finanzas");
        });
    }

    @Test
    @Order(17)
    @DisplayName("un cambio con fecha futura queda programado y la situación vigente no cambia; «Anular» con motivo lo tacha (AC-19)")
    void unCambioProgramado() throws Exception {
        cambioProgramado = id(conToken(post(COLABORADORES + "/" + colaboradorB + "/cambios"), tokenTalento,
                cambio(HOY.plusDays(10), "RENOVACION_CONTRATO", null, sedeLima, areaFinanzas, cargoJefe,
                        colaboradorA, "03", HOY.plusYears(1))).andExpect(status().isCreated()));
        conToken(get(COLABORADORES + "/" + colaboradorB), tokenTalento, null)
                .andExpect(jsonPath("$.situacion.finContrato").value(HOY.plusMonths(6).toString()))
                .andExpect(jsonPath("$.programados[0].id").value(cambioProgramado));

        conToken(post(COLABORADORES + "/" + colaboradorB + "/cambios/" + cambioProgramado + "/anulacion"),
                tokenTalento, "{\"motivo\": \"Se renueva por menos tiempo\"}").andExpect(status().isNoContent());
        conToken(get(COLABORADORES + "/" + colaboradorB), tokenTalento, null)
                .andExpect(jsonPath("$.programados").isEmpty());
        JsonNode historial = cuerpo(conToken(get(COLABORADORES + "/" + colaboradorB + "/historial"), tokenTalento, null));
        assertThat(historial).anySatisfy(e -> {
            assertThat(e.get("anulado").asBoolean()).isTrue();
            assertThat(e.get("motivoAnulacion").asText()).isEqualTo("Se renueva por menos tiempo");
            assertThat(e.get("anuladoPor").asText()).isEqualTo("Tania Equipo");
        });
        // Uno vigente no se anula: se corrige.
        long vigente = jdbc.queryForObject("select id from situacion_laboral where colaborador_id = ? "
                + "and tipo_motivo = 'PROMOCION'", Long.class, colaboradorB);
        conToken(post(COLABORADORES + "/" + colaboradorB + "/cambios/" + vigente + "/anulacion"), tokenTalento,
                "{\"motivo\": \"No\"}").andExpect(status().isConflict());
    }

    @Test
    @Order(18)
    @DisplayName("un cambio con fecha anterior al último cambio del periodo se rechaza y se explica (AC-20)")
    void unCambioAnterior() throws Exception {
        conToken(post(COLABORADORES + "/" + colaboradorB + "/cambios"), tokenTalento,
                cambio(HOY.minusDays(1), "TRASLADO", null, sedeVentanilla, areaFinanzas, cargoJefe, colaboradorA,
                        "03", HOY.plusMonths(6)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value(containsString("antes de la fecha de ingreso")));
        conToken(post(COLABORADORES + "/" + colaboradorC + "/cambios"), tokenTalento,
                cambio(HOY.minusMonths(1), "TRASLADO", null, sedeLima, areaOperaciones, cargoAsistente, colaboradorA,
                        "01", null)).andExpect(status().isCreated());
        conToken(post(COLABORADORES + "/" + colaboradorC + "/cambios"), tokenTalento,
                cambio(HOY.minusMonths(1).minusDays(1), "TRASLADO", null, sedeVentanilla, areaOperaciones,
                        cargoAsistente, colaboradorA, "01", null))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value(containsString("antes del último cambio")));
        // Sin cambiar nada tampoco.
        conToken(post(COLABORADORES + "/" + colaboradorC + "/cambios"), tokenTalento,
                cambio(HOY, "OTRO", "Nada", sedeLima, areaOperaciones, cargoAsistente, colaboradorA, "01", null))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value(containsString("al menos un dato")));
    }

    // ============ 14. El cese y el reingreso ============

    @Test
    @Order(19)
    @DisplayName("el cese de un jefe nombra a quienes le reportan; con fecha de hoy sigue Activo, y un cese pasado sale en Cesados (AC-21)")
    void elCese() throws Exception {
        JsonNode ficha = cuerpo(conToken(get(COLABORADORES + "/" + colaboradorA), tokenTalento, null));
        List<String> reportes = nombres(ficha.get("reportes"), "nombre");
        assertThat(reportes).contains("Rivas Sánchez, Ana María", "Quispe, Carlos");

        conToken(post(COLABORADORES + "/" + colaboradorA + "/cese"), tokenTalento,
                "{\"fechaCese\": \"" + HOY + "\", \"motivoCodigo\": \"01\", \"observacion\": \"Se muda\"}")
                .andExpect(status().isNoContent());
        conToken(get(COLABORADORES + "/" + colaboradorA), tokenTalento, null)
                .andExpect(jsonPath("$.estado").value("ACTIVO"))
                .andExpect(jsonPath("$.periodo.fechaCese").value(HOY.toString()))
                .andExpect(jsonPath("$.periodo.motivoCeseNombre").value("Renuncia"));

        // Un cese de ayer: desde hoy sale Cesado, no desaparece si se filtra por Cesados y
        // conserva su historial.
        colaboradorE = id(conToken(post(COLABORADORES), tokenTalento,
                alta("01", "42333444", "Elena", "Paredes", HOY.minusYears(1), sedeLima, areaVentas,
                        cargoAnalista, null, "01", null, null)).andExpect(status().isCreated()));
        conToken(post(COLABORADORES + "/" + colaboradorE + "/cese"), tokenTalento,
                "{\"fechaCese\": \"" + HOY.minusDays(1) + "\", \"motivoCodigo\": \"07\"}")
                .andExpect(status().isNoContent());
        JsonNode porDefecto = cuerpo(conToken(get(COLABORADORES + "?q=paredes"), tokenTalento, null));
        assertThat(porDefecto.get("total").asLong()).isZero();
        JsonNode cesados = cuerpo(conToken(get(COLABORADORES + "?q=paredes&estado=CESADO"), tokenTalento, null));
        assertThat(cesados.get("filas").get(0).get("estado").asText()).isEqualTo("CESADO");
        assertThat(cuerpo(conToken(get(COLABORADORES + "/" + colaboradorE + "/historial"), tokenTalento, null)))
                .extracting(e -> e.get("tipo").asText()).containsExactly("CESE", "INGRESO");

        // Un cesado no recibe cambios: se reingresa.
        conToken(post(COLABORADORES + "/" + colaboradorE + "/cambios"), tokenTalento,
                cambio(HOY, "TRASLADO", null, sedeVentanilla, areaVentas, cargoAnalista, null, "01", null))
                .andExpect(status().isConflict());
        // Y un cesado no puede ser jefe nuevo de nadie.
        conToken(post(COLABORADORES + "/" + colaboradorC + "/cambios"), tokenTalento,
                cambio(HOY, "CAMBIO_JEFE", null, sedeLima, areaOperaciones, cargoAsistente, colaboradorE, "01", null))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value(containsString("activo o por ingresar")));
    }

    @Test
    @Order(20)
    @DisplayName("un cesado se reingresa después de su cese: sale Activo con un periodo nuevo y el historial enseña los dos (AC-22)")
    void elReingreso() throws Exception {
        String situacion = situacionComoJson(sedeLima, areaVentas, cargoAnalista, null, "01", null);
        conToken(post(COLABORADORES + "/" + colaboradorE + "/reingreso"), tokenTalento,
                "{\"fechaIngreso\": \"" + HOY.minusDays(1) + "\", \"situacion\": " + situacion + "}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value(containsString("posterior al último cese")));
        conToken(post(COLABORADORES + "/" + colaboradorE + "/reingreso"), tokenTalento,
                "{\"fechaIngreso\": \"" + HOY + "\", \"situacion\": " + situacion + "}")
                .andExpect(status().isNoContent());
        conToken(get(COLABORADORES + "/" + colaboradorE), tokenTalento, null)
                .andExpect(jsonPath("$.estado").value("ACTIVO"))
                .andExpect(jsonPath("$.periodo.fechaIngreso").value(HOY.toString()));
        assertThat(cuerpo(conToken(get(COLABORADORES + "/" + colaboradorE + "/historial"), tokenTalento, null)))
                .extracting(e -> e.get("tipo").asText()).containsExactly("REINGRESO", "CESE", "INGRESO");

        // El alta con el documento de un cesado lleva al reingreso.
        jdbc.update("update periodo_laboral set fecha_cese = current_date - 2, motivo_cese = '01' "
                + "where colaborador_id = (select id from colaborador where numero_documento = '50000019')");
        conToken(post(COLABORADORES), tokenTalento, alta("01", "50000019", "Persona19", "Carga", HOY,
                sedeLima, areaFinanzas, cargoAnalista, null, "01", null, null))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("Ya trabajó aquí. Reingrésalo desde su ficha"))
                .andExpect(jsonPath("$.cesado").value(true));

        // Anular el cese de A deja su periodo abierto, y el historial lo enseña tachado.
        conToken(post(COLABORADORES + "/" + colaboradorA + "/cese/anulacion"), tokenTalento,
                "{\"motivo\": \"Se queda\"}").andExpect(status().isNoContent());
        conToken(get(COLABORADORES + "/" + colaboradorA), tokenTalento, null)
                .andExpect(jsonPath("$.periodo.fechaCese").isEmpty());
        assertThat(cuerpo(conToken(get(COLABORADORES + "/" + colaboradorA + "/historial"), tokenTalento, null)))
                .anySatisfy(e -> {
                    assertThat(e.get("tipo").asText()).isEqualTo("CESE");
                    assertThat(e.get("anulado").asBoolean()).isTrue();
                });
    }

    // ============ 15. El sueldo ============

    @Test
    @Order(21)
    @DisplayName("sin ver_sueldos el sueldo no viaja en la lista, la ficha, el historial ni la plantilla, y un Excel con sueldo se rechaza (AC-23)")
    void elSueldoOculto() throws Exception {
        JsonNode conPermiso = cuerpo(conToken(get(COLABORADORES + "/" + colaboradorC), tokenDireccion, null));
        assertThat(conPermiso.at("/situacion/sueldoBase").decimalValue())
                .isEqualByComparingTo(new java.math.BigDecimal("3200"));
        assertThat(conPermiso.at("/situacion/moneda").asText()).isEqualTo("PEN");
        for (String ruta : List.of(COLABORADORES + "?estado=ACTIVO,POR_INGRESAR,CESADO", COLABORADORES + "/" + colaboradorC,
                COLABORADORES + "/" + colaboradorC + "/historial", COLABORADORES + "/opciones")) {
            String respuesta = conToken(get(ruta), tokenTalento, null).andExpect(status().isOk())
                    .andReturn().getResponse().getContentAsString();
            assertThat(respuesta).as(ruta).doesNotContain("sueldo").doesNotContain("3200");
        }
        byte[] plantilla = conToken(get(COLABORADORES + "/plantilla"), tokenTalento, null)
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsByteArray();
        try (XSSFWorkbook libro = new XSSFWorkbook(new ByteArrayInputStream(plantilla))) {
            List<String> titulos = new ArrayList<>();
            libro.getSheet("Colaboradores").getRow(0).forEach(c -> titulos.add(c.getStringCellValue()));
            assertThat(titulos).noneMatch(t -> t.toLowerCase().contains("sueldo"));
        }
        List<String> conSueldo = new ArrayList<>(List.of("Tipo de documento *", "Número de documento *", "Sueldo base"));
        cargar(tokenTalento, conSueldo, List.of(List.of("DNI", "41222333", "9999")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errores[0].mensaje").value("No tienes permiso para cargar sueldos"))
                .andExpect(jsonPath("$.errores[0].valor").value(""));
    }

    // ============ 16. El aislamiento y el alcance ============

    @Test
    @Order(22)
    @DisplayName("otra empresa recibe 404 y no ve a nadie; sin permiso, 403; con un alcance que no es TODO, nadie (AC-24)")
    void elAislamiento() throws Exception {
        conToken(get(COLABORADORES + "/" + colaboradorB), tokenAcme, null).andExpect(status().isNotFound());
        JsonNode deAcme = cuerpo(conToken(get(COLABORADORES), tokenAcme, null));
        assertThat(ids(deAcme.get("filas"))).doesNotContain(colaboradorB);
        conToken(get(COLABORADORES), tokenResponsable, null).andExpect(status().isForbidden());
        conToken(post(COLABORADORES + "/" + colaboradorB + "/cese"), tokenResponsable,
                "{\"fechaCese\": \"" + HOY + "\", \"motivoCodigo\": \"01\"}").andExpect(status().isForbidden());

        jdbc.update("update rol_permiso set alcance = 'SUS_VACANTES' where permiso_id = (select id from permiso "
                + "where codigo = 'ver_colaboradores') and rol_id = (select id from rol where codigo = 'TALENTO' "
                + "and organizacion_id = ?)", plataforma);
        try {
            JsonNode vacia = cuerpo(conToken(get(COLABORADORES), tokenTalento, null).andExpect(status().isOk()));
            assertThat(vacia.get("filas")).isEmpty();
            conToken(get(COLABORADORES + "/" + colaboradorB), tokenTalento, null).andExpect(status().isNotFound());
        } finally {
            jdbc.update("update rol_permiso set alcance = 'TODO' where permiso_id = (select id from permiso "
                    + "where codigo = 'ver_colaboradores') and rol_id = (select id from rol where codigo = 'TALENTO' "
                    + "and organizacion_id = ?)", plataforma);
        }
    }

    // ============ 17. El ajuste de sueldo programado ============

    @Test
    @Order(23)
    @DisplayName("sin ver_sueldos, un ajuste de solo sueldo programado no sale, no se anula ni cierra fechas; un cambio anterior lo arrastra y anularlo se lo quita (AC-23)")
    void elAjusteDeSueldoProgramado() throws Exception {
        long id = id(conToken(post(COLABORADORES), tokenDireccion,
                alta("01", "43999111", "Sara", "Programada", HOY.minusDays(60), sedeLima, areaFinanzas,
                        cargoAnalista, null, "01", null, 4000)).andExpect(status().isCreated()));
        long ajuste = id(conToken(post(COLABORADORES + "/" + id + "/cambios"), tokenDireccion,
                cambio(HOY.plusDays(20), "AJUSTE_REMUNERACION", null, sedeLima, areaFinanzas, cargoAnalista,
                        null, "01", null, 4600)).andExpect(status().isCreated()));

        // Dirección lo tiene programado; a Talento no le llega, ni su fecha como tope de un cambio.
        conToken(get(COLABORADORES + "/" + id), tokenDireccion, null)
                .andExpect(jsonPath("$.programados[0].id").value(ajuste));
        String paraTalento = conToken(get(COLABORADORES + "/" + id), tokenTalento, null)
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        JsonNode fichaTalento = json.readTree(paraTalento);
        assertThat(fichaTalento.get("programados")).isEmpty();
        assertThat(fichaTalento.at("/base/vigenteDesde").asText()).isEqualTo(HOY.minusDays(60).toString());
        assertThat(paraTalento).doesNotContain(HOY.plusDays(20).toString()).doesNotContain("4600")
                .doesNotContain("sueldo");

        // Anularlo sería editar el sueldo: para Talento ese cambio no existe.
        conToken(post(COLABORADORES + "/" + id + "/cambios/" + ajuste + "/anulacion"), tokenTalento,
                "{\"motivo\": \"Talento no edita sueldos\"}").andExpect(status().isNotFound());
        assertThat(contar("select count(*) from situacion_laboral where id = " + ajuste
                + " and anulada_en is not null")).isZero();

        // Un traslado anterior al ajuste se admite, y el ajuste lo hereda sin dejar de tocar solo
        // el sueldo: al llegar su día no deshace el traslado.
        long traslado = id(conToken(post(COLABORADORES + "/" + id + "/cambios"), tokenTalento,
                cambio(HOY.plusDays(10), "TRASLADO", null, sedeVentanilla, areaFinanzas, cargoAnalista, null,
                        "01", null)).andExpect(status().isCreated()));
        JsonNode deDireccion = cuerpo(conToken(get(COLABORADORES + "/" + id), tokenDireccion, null));
        assertThat(ids(deDireccion.get("programados"))).containsExactly(traslado, ajuste);
        assertThat(deDireccion.at("/programados/0/sueldoBase").decimalValue())
                .isEqualByComparingTo(new java.math.BigDecimal("4000"));
        assertThat(deDireccion.at("/programados/1/sedeId").asLong()).isEqualTo(sedeVentanilla);
        assertThat(deDireccion.at("/programados/1/sueldoBase").decimalValue())
                .isEqualByComparingTo(new java.math.BigDecimal("4600"));
        assertThat(ids(cuerpo(conToken(get(COLABORADORES + "/" + id), tokenTalento, null)).get("programados")))
                .containsExactly(traslado);
        String historial = conToken(get(COLABORADORES + "/" + id + "/historial"), tokenTalento, null)
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertThat(historial).contains("Planta Ventanilla").doesNotContain("Ajuste de remuneración")
                .doesNotContain("4600");

        // Anular el traslado se lo quita también al ajuste, que vuelve a partir de lo de antes.
        conToken(post(COLABORADORES + "/" + id + "/cambios/" + traslado + "/anulacion"), tokenTalento,
                "{\"motivo\": \"Al final se queda\"}").andExpect(status().isNoContent());
        deDireccion = cuerpo(conToken(get(COLABORADORES + "/" + id), tokenDireccion, null));
        assertThat(ids(deDireccion.get("programados"))).containsExactly(ajuste);
        assertThat(deDireccion.at("/programados/0/sedeId").asLong()).isEqualTo(sedeLima);
        assertThat(deDireccion.at("/programados/0/sueldoBase").decimalValue())
                .isEqualByComparingTo(new java.math.BigDecimal("4600"));

        // Un cambio el mismo día del ajuste lo sustituye y el sueldo nuevo sigue llegando ese día.
        long mismoDia = id(conToken(post(COLABORADORES + "/" + id + "/cambios"), tokenTalento,
                cambio(HOY.plusDays(20), "PROMOCION", null, sedeLima, areaFinanzas, cargoJefe, null, "01", null))
                .andExpect(status().isCreated()));
        deDireccion = cuerpo(conToken(get(COLABORADORES + "/" + id), tokenDireccion, null));
        assertThat(ids(deDireccion.get("programados"))).containsExactly(mismoDia);
        assertThat(deDireccion.at("/programados/0/cargoId").asLong()).isEqualTo(cargoJefe);
        assertThat(deDireccion.at("/programados/0/sueldoBase").decimalValue())
                .isEqualByComparingTo(new java.math.BigDecimal("4600"));
    }

    // ============ 18. El volumen ============

    @Test
    @Order(24)
    @DisplayName("la lista de una empresa nueva recién cargada con 5.000 personas responde aunque Postgres no haya puesto al día sus estadísticas")
    void laListaTrasUnaCargaGrande() throws Exception {
        long nueva = Long.parseLong(leer(conToken(post("/api/v1/panel/plataforma/empresas"), tokenDev, """
                {"nombre": "Volumen S.A.C.", "codigo": "VOLUMEN", "correoAdministrador": "admin@volumen.pe"}""")
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString(), "id"));
        String tokenNueva = tokens.emitir(equipo(nueva, "TALENTO", "Vilma"), nueva, "EQUIPO");

        // Las estadísticas se quedan como antes de la carga, igual que justo después de una carga
        // real y antes de que pase el autoanalyze: para el planificador, la empresa no tiene a nadie.
        List<String> tablas = List.of("colaborador", "periodo_laboral", "situacion_laboral");
        tablas.forEach(t -> {
            jdbc.execute("alter table " + t + " set (autovacuum_enabled = false)");
            jdbc.execute("analyze " + t);
        });
        try {
            long sede = jdbc.queryForObject("insert into sede (organizacion_id, nombre) values (?, 'Planta') "
                    + "returning id", Long.class, nueva);
            long area = area(nueva, "Producción");
            long puesto = jdbc.queryForObject("insert into puesto (organizacion_id, codigo, nombre, "
                    + "nivel_puesto_codigo, familia_codigo) values (?, 'VOLUMEN_OPERARIO', 'Operario', 'EJECUCION', "
                    + "'OPERACIONES') returning id", Long.class, nueva);
            jdbc.update("insert into colaborador (organizacion_id, tipo_documento, numero_documento, nombres, "
                    + "apellido_paterno, fecha_nacimiento, sexo) select ?, '01', (70000000 + g)::text, "
                    + "'Persona ' || g, 'Volumen ' || g, ?, 'F' from generate_series(1, 5000) g",
                    nueva, java.sql.Date.valueOf(HOY.minusYears(30)));
            jdbc.update("insert into periodo_laboral (colaborador_id, fecha_ingreso) select id, ? from colaborador "
                    + "where organizacion_id = ?", java.sql.Date.valueOf(HOY.minusMonths(2)), nueva);
            Long primero = jdbc.queryForObject("select id from colaborador where organizacion_id = ? "
                    + "and numero_documento = '70000001'", Long.class, nueva);
            // Todos menos el primero le reportan a él, como en una carga con un solo jefe.
            jdbc.update("insert into situacion_laboral (colaborador_id, periodo_id, vigente_desde, sede_id, area_id, "
                    + "puesto_id, jefe_colaborador_id, tipo_contrato, regimen_laboral, tipo_motivo) "
                    + "select p.colaborador_id, p.id, p.fecha_ingreso, ?, ?, ?, "
                    + "case when p.colaborador_id <> ? then ? end, '01', '01', 'CARGA_INICIAL' "
                    + "from periodo_laboral p join colaborador c on c.id = p.colaborador_id "
                    + "where c.organizacion_id = ?", sede, area, puesto, primero, primero, nueva);

            // Lo que mueve la base crece en línea con la empresa —unas pocas filas por persona—
            // sea cual sea el plan. Con bucles anidados sobre las CTE eran 5.000 por persona.
            Map<String, Object> parametros = Map.of("org", nueva, "hoy", java.sql.Date.valueOf(HOY),
                    "limite", 50, "desde", 0L);
            assertThat(filasMovidas(ListadoDeColaboradoresRepository.consultaDelTotal(""), parametros))
                    .isLessThan(100L * 5000);
            assertThat(filasMovidas(ListadoDeColaboradoresRepository.consultaDeLaPagina(""), parametros))
                    .isLessThan(100L * 5000);
            assertThat(filasMovidas(ListadoDeColaboradoresRepository.consultaDeActuales(), parametros))
                    .isLessThan(100L * 5000);

            // Con un tope en la base: si el plan vuelve a depender de las estadísticas, la consulta
            // se corta aquí en vez de colgar la suite durante minutos.
            ListadoDeColaboradoresRepository.Pagina pagina = conTope(() -> listado.pagina(nueva, HOY,
                    new Filtros(List.of("ACTIVO", "POR_INGRESAR"), null, null, null, false, null), 0, 50));
            assertThat(pagina.total()).isEqualTo(5000);
            assertThat(pagina.filas()).hasSize(50);
            assertThat(conTope(() -> listado.pagina(nueva, HOY,
                    new Filtros(List.of("ACTIVO"), sede, area, puesto, false, "volumen 4999"), 0, 50)).total())
                    .isEqualTo(1);
            assertThat(conTope(() -> listado.actuales(nueva, HOY))).hasSize(5000);

            // Y la pantalla entera, como la pide el panel justo después de «Validar y cargar».
            long inicio = System.nanoTime();
            JsonNode lista = cuerpo(conToken(get(COLABORADORES), tokenNueva, null).andExpect(status().isOk()));
            assertThat(java.time.Duration.ofNanos(System.nanoTime() - inicio))
                    .isLessThan(java.time.Duration.ofSeconds(10));
            assertThat(lista.get("total").asLong()).isEqualTo(5000);
            assertThat(lista.get("filas")).hasSize(50);
            // Por apellidos: «Volumen 1», «Volumen 10»…; el segundo ya tiene jefe.
            assertThat(lista.get("filas").get(1).get("jefe").asText()).isEqualTo("Volumen 1, Persona 1");
        } finally {
            tablas.forEach(t -> {
                jdbc.execute("alter table " + t + " reset (autovacuum_enabled)");
                jdbc.execute("analyze " + t);
            });
        }
    }

    /**
     * Cuántas filas mueve de verdad una consulta: filas × vueltas de cada nodo del plan, más las
     * que descartan sus filtros. Mide el trabajo sin depender de lo rápida que sea la máquina.
     */
    private long filasMovidas(String sql, Map<String, Object> parametros) {
        String plan = conTope(() -> new NamedParameterJdbcTemplate(jdbc)
                .queryForObject("explain (analyze, format json) " + sql, parametros, String.class));
        try {
            return Math.round(filasMovidas(json.readTree(plan).get(0).get("Plan")));
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    private static double filasMovidas(JsonNode nodo) {
        double vueltas = nodo.path("Actual Loops").asDouble();
        double total = vueltas * (nodo.path("Actual Rows").asDouble() + nodo.path("Rows Removed by Filter").asDouble()
                + nodo.path("Rows Removed by Join Filter").asDouble());
        for (JsonNode hijo : nodo.path("Plans")) {
            total += filasMovidas(hijo);
        }
        return total;
    }

    /** Ejecuta en una transacción con {@code statement_timeout}: una consulta que se cuelga, falla. */
    private <T> T conTope(java.util.function.Supplier<T> consulta) {
        return new TransactionTemplate(transacciones).execute(estado -> {
            jdbc.execute("set local statement_timeout = '20s'");
            return consulta.get();
        });
    }

    // ============ 19. Dos peticiones a la vez sobre la misma ficha ============

    @Test
    @Order(25)
    @DisplayName("dos «Anular» a la vez —un doble clic— dejan una sola anulación del cambio y del cese; igual el cese y el reingreso (AC-19, AC-21, AC-22)")
    void dosPeticionesALaVez() throws Exception {
        long id = id(conToken(post(COLABORADORES), tokenTalento,
                alta("01", "43777888", "Olga", "Simultánea", HOY.minusDays(60), sedeLima, areaFinanzas,
                        cargoAnalista, null, "01", null, null)).andExpect(status().isCreated()));
        long traslado = id(conToken(post(COLABORADORES + "/" + id + "/cambios"), tokenTalento,
                cambio(HOY.plusDays(15), "TRASLADO", null, sedeVentanilla, areaFinanzas, cargoAnalista, null,
                        "01", null)).andExpect(status().isCreated()));

        assertThat(aLaVez(4, () -> conToken(post(COLABORADORES + "/" + id + "/cambios/" + traslado + "/anulacion"),
                tokenTalento, "{\"motivo\": \"Doble clic\"}")))
                .as("uno anula; los demás se encuentran el cambio ya anulado")
                .containsExactlyInAnyOrder(204, 409, 409, 409);
        assertThat(contar("select count(*) from auditoria where accion = 'anular_cambio_colaborador' "
                + "and entidad_id = " + id)).isEqualTo(1);

        String cese = "{\"fechaCese\": \"" + HOY.plusDays(30) + "\", \"motivoCodigo\": \"01\"}";
        assertThat(aLaVez(3, () -> conToken(post(COLABORADORES + "/" + id + "/cese"), tokenTalento, cese)))
                .as("uno registra el cese; los demás se lo encuentran registrado")
                .containsExactlyInAnyOrder(204, 409, 409);
        assertThat(contar("select count(*) from auditoria where accion = 'registrar_cese_colaborador' "
                + "and entidad_id = " + id)).isEqualTo(1);

        assertThat(aLaVez(4, () -> conToken(post(COLABORADORES + "/" + id + "/cese/anulacion"), tokenTalento,
                "{\"motivo\": \"Doble clic\"}")))
                .as("uno anula el cese; los demás ya no tienen cese que anular")
                .containsExactlyInAnyOrder(204, 409, 409, 409);
        assertThat(contar("select count(*) from cese_anulado ca join periodo_laboral p on p.id = ca.periodo_id "
                + "where p.colaborador_id = " + id)).isEqualTo(1);
        assertThat(contar("select count(*) from auditoria where accion = 'anular_cese_colaborador' "
                + "and entidad_id = " + id)).isEqualTo(1);
        JsonNode historial = cuerpo(conToken(get(COLABORADORES + "/" + id + "/historial"), tokenTalento, null));
        assertThat(historial).filteredOn(e -> e.get("tipo").asText().equals("CESE")).hasSize(1);

        // Cesado de ayer, dos reingresos a la vez: un solo periodo nuevo.
        conToken(post(COLABORADORES + "/" + id + "/cese"), tokenTalento,
                "{\"fechaCese\": \"" + HOY.minusDays(1) + "\", \"motivoCodigo\": \"01\"}")
                .andExpect(status().isNoContent());
        String reingreso = "{\"fechaIngreso\": \"" + HOY + "\", \"situacion\": "
                + situacionComoJson(sedeLima, areaFinanzas, cargoAnalista, null, "01", null) + "}";
        assertThat(aLaVez(3, () -> conToken(post(COLABORADORES + "/" + id + "/reingreso"), tokenTalento, reingreso)))
                .as("uno reingresa; los demás ya no lo encuentran cesado")
                .containsExactlyInAnyOrder(204, 409, 409);
        assertThat(contar("select count(*) from periodo_laboral where colaborador_id = " + id)).isEqualTo(2);
    }

    // ============ 20. El ajuste de sueldo que ya rige ============

    @Test
    @Order(26)
    @DisplayName("sin ver_sueldos, un ajuste de solo sueldo que ya rige no se delata: la vigente enseña la fecha y el motivo del último cambio que se ve (§D.16)")
    void elAjusteQueYaRige() throws Exception {
        long id = id(conToken(post(COLABORADORES), tokenDireccion,
                alta("01", "43666555", "Nora", "Ajustada", HOY.minusDays(60), sedeLima, areaFinanzas,
                        cargoAnalista, null, "01", null, 4000)).andExpect(status().isCreated()));
        conToken(post(COLABORADORES + "/" + id + "/cambios"), tokenDireccion,
                cambio(HOY.minusDays(10), "AJUSTE_REMUNERACION", "Revisión anual", sedeLima, areaFinanzas,
                        cargoAnalista, null, "01", null, 4600)).andExpect(status().isCreated());
        conToken(post(COLABORADORES + "/" + id + "/cambios"), tokenDireccion,
                cambio(HOY.plusDays(20), "AJUSTE_REMUNERACION", null, sedeLima, areaFinanzas,
                        cargoAnalista, null, "01", null, 4800)).andExpect(status().isCreated());

        // Dirección ve el ajuste que rige, con su fecha y su motivo.
        conToken(get(COLABORADORES + "/" + id), tokenDireccion, null)
                .andExpect(jsonPath("$.situacion.vigenteDesde").value(HOY.minusDays(10).toString()))
                .andExpect(jsonPath("$.situacion.tipoMotivo").value("AJUSTE_REMUNERACION"))
                .andExpect(jsonPath("$.situacion.vigenteHasta").value(HOY.plusDays(19).toString()));

        // Talento ve el ingreso, que para él sigue rigiendo sin fin: ni la fecha del ajuste, ni su
        // motivo, ni el día antes del ajuste programado.
        String paraTalento = conToken(get(COLABORADORES + "/" + id), tokenTalento, null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.situacion.vigenteDesde").value(HOY.minusDays(60).toString()))
                .andExpect(jsonPath("$.situacion.tipoMotivo").value("INGRESO"))
                .andExpect(jsonPath("$.situacion.vigenteHasta").isEmpty())
                .andExpect(jsonPath("$.base.vigenteDesde").value(HOY.minusDays(60).toString()))
                .andExpect(jsonPath("$.programados").isEmpty())
                .andReturn().getResponse().getContentAsString();
        assertThat(paraTalento).doesNotContain(HOY.minusDays(10).toString()).doesNotContain(HOY.minusDays(11).toString())
                .doesNotContain(HOY.plusDays(19).toString()).doesNotContain(HOY.plusDays(20).toString())
                .doesNotContain("AJUSTE_REMUNERACION").doesNotContain("Ajuste de remuneración")
                .doesNotContain("Revisión anual").doesNotContain("sueldo");

        // Tampoco le cierra las fechas: un traslado anterior al ajuste se admite, sin nombrarlo, y el
        // ajuste lo hereda sin perder su sueldo.
        long traslado = id(conToken(post(COLABORADORES + "/" + id + "/cambios"), tokenTalento,
                cambio(HOY.minusDays(20), "TRASLADO", null, sedeVentanilla, areaFinanzas, cargoAnalista, null,
                        "01", null)).andExpect(status().isCreated()));
        conToken(get(COLABORADORES + "/" + id), tokenTalento, null)
                .andExpect(jsonPath("$.situacion.id").value(traslado))
                .andExpect(jsonPath("$.situacion.vigenteDesde").value(HOY.minusDays(20).toString()))
                .andExpect(jsonPath("$.situacion.sedeId").value(sedeVentanilla))
                .andExpect(jsonPath("$.situacion.vigenteHasta").isEmpty());
        JsonNode deDireccion = cuerpo(conToken(get(COLABORADORES + "/" + id), tokenDireccion, null));
        assertThat(deDireccion.at("/situacion/vigenteDesde").asText()).isEqualTo(HOY.minusDays(10).toString());
        assertThat(deDireccion.at("/situacion/sedeId").asLong()).isEqualTo(sedeVentanilla);
        assertThat(deDireccion.at("/situacion/sueldoBase").decimalValue())
                .isEqualByComparingTo(new java.math.BigDecimal("4600"));
    }

    // ============ 21. Lo que cambiaba un cambio anulado ============

    @Test
    @Order(27)
    @DisplayName("un cambio anulado sigue enseñando lo que cambiaba aunque después se registre otro, para Dirección y para Talento (AC-19, §D.16)")
    void elAnuladoNoSeReescribe() throws Exception {
        long id = altaEnOperaciones("44100001", "Hilda", 4100);
        long traslado = id(conToken(post(COLABORADORES + "/" + id + "/cambios"), tokenDireccion,
                cambio(HOY.plusDays(15), "TRASLADO", null, sedeLima, areaFinanzas, cargoAnalista, null, "01", null,
                        4100)).andExpect(status().isCreated()));
        anularCambio(id, traslado, tokenDireccion);
        assertThat(lineasDelAnulado(id, tokenDireccion)).containsExactly("Área: Operaciones → Finanzas");

        // Lo que sugiere el aviso de fechas: anulado el programado, una promoción de hoy con sueldo.
        conToken(post(COLABORADORES + "/" + id + "/cambios"), tokenDireccion,
                cambio(HOY, "PROMOCION", null, sedeLima, areaOperaciones, cargoJefe, null, "01", null, 5000))
                .andExpect(status().isCreated());
        assertThat(lineasDelAnulado(id, tokenDireccion)).containsExactly("Área: Operaciones → Finanzas");
        assertThat(lineasDelAnulado(id, tokenTalento)).containsExactly("Área: Operaciones → Finanzas");
        JsonNode historial = cuerpo(conToken(get(COLABORADORES + "/" + id + "/historial"), tokenDireccion, null));
        assertThat(historial).filteredOn(e -> e.get("motivo").asText().equals("Promoción")).singleElement()
                .satisfies(e -> assertThat(lineas(e)).containsExactly("Cargo: Analista → Jefe de finanzas",
                        "Sueldo base: S/ 4,100.00 → S/ 5,000.00"));

        // Si lo posterior deja el mismo dato que el anulado, el anulado no se queda en blanco.
        long otra = altaEnOperaciones("44100002", "Irene", 4100);
        long otroTraslado = id(conToken(post(COLABORADORES + "/" + otra + "/cambios"), tokenDireccion,
                cambio(HOY.plusDays(15), "TRASLADO", null, sedeLima, areaFinanzas, cargoAnalista, null, "01", null,
                        4100)).andExpect(status().isCreated()));
        anularCambio(otra, otroTraslado, tokenDireccion);
        conToken(post(COLABORADORES + "/" + otra + "/cambios"), tokenDireccion,
                cambio(HOY, "TRASLADO", null, sedeLima, areaFinanzas, cargoAnalista, null, "01", null, 4100))
                .andExpect(status().isCreated());
        assertThat(lineasDelAnulado(otra, tokenDireccion)).containsExactly("Área: Operaciones → Finanzas");
        assertThat(lineasDelAnulado(otra, tokenTalento)).containsExactly("Área: Operaciones → Finanzas");

        // Un ajuste de solo sueldo anulado lo sigue siendo: Dirección lo ve con su importe y para
        // Talento no existe, aunque lo posterior toque el área.
        long ajustada = altaEnOperaciones("44100003", "Julia", 4000);
        long ajuste = id(conToken(post(COLABORADORES + "/" + ajustada + "/cambios"), tokenDireccion,
                cambio(HOY.plusDays(20), "AJUSTE_REMUNERACION", null, sedeLima, areaOperaciones, cargoAnalista,
                        null, "01", null, 4600)).andExpect(status().isCreated()));
        anularCambio(ajustada, ajuste, tokenDireccion);
        conToken(post(COLABORADORES + "/" + ajustada + "/cambios"), tokenDireccion,
                cambio(HOY, "TRASLADO", null, sedeLima, areaFinanzas, cargoAnalista, null, "01", null, 4600))
                .andExpect(status().isCreated());
        assertThat(lineasDelAnulado(ajustada, tokenDireccion)).containsExactly("Sueldo base: S/ 4,000.00 → S/ 4,600.00");
        String paraTalento = conToken(get(COLABORADORES + "/" + ajustada + "/historial"), tokenTalento, null)
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertThat(json.readTree(paraTalento)).noneSatisfy(e -> assertThat(e.get("anulado").asBoolean()).isTrue());
        assertThat(paraTalento).doesNotContain("Ajuste de remuneración").doesNotContain("S/ ")
                .doesNotContain("Sueldo");
        // Y anularlo de nuevo le contesta igual que a un cambio que no está.
        conToken(post(COLABORADORES + "/" + ajustada + "/cambios/" + ajuste + "/anulacion"), tokenTalento,
                "{\"motivo\": \"Otra vez\"}").andExpect(status().isNotFound());
    }

    @Test
    @Order(28)
    @DisplayName("lo que cambiaba un anulado no se mueve tras anular el cese que lo anuló, ni cuando se arrastra la situación que tenía detrás (AC-19)")
    void elAnuladoPorUnCeseYElArrastre() throws Exception {
        // El cese anula el traslado programado detrás de él; anulado el cese, una promoción de hoy.
        long id = altaEnOperaciones("44100004", "Karen", 4000);
        long traslado = id(conToken(post(COLABORADORES + "/" + id + "/cambios"), tokenDireccion,
                cambio(HOY.plusDays(30), "TRASLADO", null, sedeLima, areaFinanzas, cargoAnalista, null, "01", null,
                        4000)).andExpect(status().isCreated()));
        conToken(post(COLABORADORES + "/" + id + "/cese"), tokenDireccion,
                "{\"fechaCese\": \"" + HOY.plusDays(20) + "\", \"motivoCodigo\": \"01\"}")
                .andExpect(status().isNoContent());
        conToken(post(COLABORADORES + "/" + id + "/cese/anulacion"), tokenDireccion,
                "{\"motivo\": \"Se queda\"}").andExpect(status().isNoContent());
        conToken(post(COLABORADORES + "/" + id + "/cambios"), tokenDireccion,
                cambio(HOY, "PROMOCION", null, sedeLima, areaOperaciones, cargoJefe, null, "01", null, 4000))
                .andExpect(status().isCreated());
        assertThat(contar("select count(*) from situacion_laboral where id = " + traslado
                + " and anulada_en is not null")).isOne();
        assertThat(lineasDelAnulado(id, tokenDireccion)).containsExactly("Área: Operaciones → Finanzas");
        assertThat(lineasDelAnulado(id, tokenTalento)).containsExactly("Área: Operaciones → Finanzas");

        // Detrás del traslado anulado queda un ajuste de sueldo; un cambio de Talento anterior al
        // ajuste lo arrastra y reescribe su cargo. El traslado sigue cambiando solo el área.
        long otra = altaEnOperaciones("44100005", "Lucía", 4000);
        conToken(post(COLABORADORES + "/" + otra + "/cambios"), tokenDireccion,
                cambio(HOY.plusDays(10), "AJUSTE_REMUNERACION", null, sedeLima, areaOperaciones, cargoAnalista,
                        null, "01", null, 4600)).andExpect(status().isCreated());
        long otroTraslado = id(conToken(post(COLABORADORES + "/" + otra + "/cambios"), tokenDireccion,
                cambio(HOY.plusDays(20), "TRASLADO", null, sedeLima, areaFinanzas, cargoAnalista, null, "01", null,
                        4600)).andExpect(status().isCreated()));
        anularCambio(otra, otroTraslado, tokenDireccion);
        conToken(post(COLABORADORES + "/" + otra + "/cambios"), tokenTalento,
                cambio(HOY.plusDays(5), "PROMOCION", null, sedeLima, areaOperaciones, cargoJefe, null, "01", null))
                .andExpect(status().isCreated());
        assertThat(contar("select count(*) from situacion_laboral where colaborador_id = " + otra
                + " and tipo_motivo = 'AJUSTE_REMUNERACION' and puesto_id = " + cargoJefe)).isOne();
        assertThat(lineasDelAnulado(otra, tokenDireccion)).containsExactly("Área: Operaciones → Finanzas");
        assertThat(lineasDelAnulado(otra, tokenTalento)).containsExactly("Área: Operaciones → Finanzas");
    }

    // ============ Ayudantes ============

    /** Un alta de Dirección de hace 60 días en Lima, Operaciones, como Analista. */
    private long altaEnOperaciones(String dni, String nombres, int sueldo) throws Exception {
        return id(conToken(post(COLABORADORES), tokenDireccion,
                alta("01", dni, nombres, "Anulada", HOY.minusDays(60), sedeLima, areaOperaciones, cargoAnalista,
                        null, "01", null, sueldo)).andExpect(status().isCreated()));
    }

    private void anularCambio(long colaborador, long cambio, String token) throws Exception {
        conToken(post(COLABORADORES + "/" + colaborador + "/cambios/" + cambio + "/anulacion"), token,
                "{\"motivo\": \"Al final no\"}").andExpect(status().isNoContent());
    }

    /** Las líneas «campo: antes → después» del único cambio anulado del historial. */
    private List<String> lineasDelAnulado(long colaborador, String token) throws Exception {
        JsonNode historial = cuerpo(conToken(get(COLABORADORES + "/" + colaborador + "/historial"), token, null)
                .andExpect(status().isOk()));
        List<JsonNode> anulados = new ArrayList<>();
        historial.forEach(e -> {
            if (e.get("tipo").asText().equals("CAMBIO") && e.get("anulado").asBoolean()) anulados.add(e);
        });
        assertThat(anulados).hasSize(1);
        return lineas(anulados.get(0));
    }

    private static List<String> lineas(JsonNode entrada) {
        List<String> lineas = new ArrayList<>();
        entrada.get("cambios").forEach(c -> lineas.add(c.get("campo").asText() + ": " + c.get("antes").asText()
                + " → " + c.get("despues").asText()));
        return lineas;
    }

    /**
     * La misma petición {@code veces} veces a la vez, soltadas juntas: lo que llega de un doble
     * clic o de dos pestañas. Devuelve los estados HTTP, en cualquier orden.
     */
    private List<Integer> aLaVez(int veces, Callable<ResultActions> peticion) throws Exception {
        CountDownLatch salida = new CountDownLatch(1);
        ExecutorService hilos = Executors.newFixedThreadPool(veces);
        try {
            List<Future<Integer>> respuestas = new ArrayList<>();
            for (int i = 0; i < veces; i++) {
                respuestas.add(hilos.submit(() -> {
                    salida.await();
                    return peticion.call().andReturn().getResponse().getStatus();
                }));
            }
            salida.countDown();
            List<Integer> estados = new ArrayList<>();
            for (Future<Integer> respuesta : respuestas) {
                estados.add(respuesta.get(30, TimeUnit.SECONDS));
            }
            return estados;
        } finally {
            hilos.shutdownNow();
        }
    }

    private static final List<String> CABECERAS = List.of("Tipo de documento *", "Número de documento *",
            "Nombres *", "Apellido paterno *", "Fecha de nacimiento *", "Sexo *", "Fecha de ingreso *", "Sede *",
            "Área *", "Cargo *", "Tipo de documento del jefe", "Número de documento del jefe",
            "Tipo de contrato *", "Fin del contrato", "Régimen laboral *");

    private static List<Object> fila(String dni, String nombres, String paterno, String sede, String area,
                                     String cargo, String tipoJefe, String jefe, String contrato, String fin,
                                     String regimen) {
        return List.of("DNI", dni, nombres, paterno, HOY.minusYears(30).toString(), "Femenino",
                HOY.minusMonths(1).toString(), sede, area, cargo, tipoJefe, jefe, contrato, fin, regimen);
    }

    private ResultActions cargar(String token, List<String> cabeceras, List<? extends List<?>> filas) throws Exception {
        byte[] contenido;
        try (XSSFWorkbook libro = new XSSFWorkbook(); ByteArrayOutputStream salida = new ByteArrayOutputStream()) {
            Sheet hoja = libro.createSheet("Colaboradores");
            Row r = hoja.createRow(0);
            for (int i = 0; i < cabeceras.size(); i++) r.createCell(i).setCellValue(cabeceras.get(i));
            for (int f = 0; f < filas.size(); f++) {
                Row fila = hoja.createRow(f + 1);
                for (int i = 0; i < filas.get(f).size(); i++) {
                    fila.createCell(i).setCellValue(String.valueOf(filas.get(f).get(i)));
                }
            }
            libro.write(salida);
            contenido = salida.toByteArray();
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
        return mvc.perform(multipart(COLABORADORES + "/carga")
                .file(new MockMultipartFile("archivo", "carga.xlsx",
                        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", contenido))
                .header("Authorization", "Bearer " + token));
    }

    private String alta(String tipo, String numero, String nombres, String paterno, LocalDate ingreso,
                        long sede, long area, long cargo, Long jefe, String contrato, LocalDate fin,
                        Integer sueldo) throws Exception {
        return json.writeValueAsString(altaComoMapa(tipo, numero, nombres, paterno, ingreso, sede, area, cargo,
                jefe, contrato, fin, sueldo));
    }

    private Map<String, Object> altaComoMapa(String tipo, String numero, String nombres, String paterno,
                                             LocalDate ingreso, long sede, long area, long cargo, Long jefe,
                                             String contrato, LocalDate fin, Integer sueldo) {
        Map<String, Object> persona = new LinkedHashMap<>();
        persona.put("tipoDocumento", tipo);
        persona.put("numeroDocumento", numero);
        persona.put("nombres", nombres);
        persona.put("apellidoPaterno", paterno);
        persona.put("fechaNacimiento", HOY.minusYears(30).toString());
        persona.put("sexo", "F");
        Map<String, Object> alta = new LinkedHashMap<>();
        alta.put("persona", persona);
        alta.put("fechaIngreso", ingreso.toString());
        alta.put("situacion", situacionComoMapa(sede, area, cargo, jefe, contrato, fin, sueldo));
        return alta;
    }

    private Map<String, Object> situacionComoMapa(long sede, long area, long cargo, Long jefe, String contrato,
                                                  LocalDate fin, Integer sueldo) {
        Map<String, Object> situacion = new LinkedHashMap<>();
        situacion.put("sedeId", sede);
        situacion.put("areaId", area);
        situacion.put("cargoId", cargo);
        situacion.put("jefeId", jefe);
        situacion.put("tipoContrato", contrato);
        situacion.put("finContrato", fin == null ? null : fin.toString());
        situacion.put("regimenLaboral", "01");
        if (sueldo != null) {
            situacion.put("sueldoBase", sueldo);
            situacion.put("moneda", "PEN");
        }
        return situacion;
    }

    private String situacionComoJson(long sede, long area, long cargo, Long jefe, String contrato, LocalDate fin)
            throws Exception {
        return json.writeValueAsString(situacionComoMapa(sede, area, cargo, jefe, contrato, fin, null));
    }

    private String cambio(LocalDate desde, String motivo, String detalle, long sede, long area, long cargo,
                          Long jefe, String contrato, LocalDate fin) throws Exception {
        return cambio(desde, motivo, detalle, sede, area, cargo, jefe, contrato, fin, null);
    }

    private String cambio(LocalDate desde, String motivo, String detalle, long sede, long area, long cargo,
                          Long jefe, String contrato, LocalDate fin, Integer sueldo) throws Exception {
        Map<String, Object> cambio = new LinkedHashMap<>();
        cambio.put("vigenteDesde", desde.toString());
        cambio.put("tipoMotivo", motivo);
        cambio.put("detalle", detalle);
        cambio.put("situacion", situacionComoMapa(sede, area, cargo, jefe, contrato, fin, sueldo));
        return json.writeValueAsString(cambio);
    }

    private long cargo(String token, String nombre) throws Exception {
        return id(conToken(post("/api/v1/panel/cargos"), token, "{\"nombre\": \"" + nombre
                + "\", \"nivelPuestoCodigo\": \"EJECUCION\", \"familiaCodigo\": \"OPERACIONES\"}")
                .andExpect(status().isCreated()));
    }

    private long area(long organizacion, String nombre) {
        return jdbc.queryForObject("insert into area (organizacion_id, nombre) values (?, ?) returning id",
                Long.class, organizacion, nombre);
    }

    private long vacante(long organizacion, long puesto, long area, long responsable, String titulo) {
        Long solicitud = jdbc.queryForObject("insert into solicitud_talento (organizacion_id, origen, urgencia, "
                + "estado, area_id, resultado_principal, motivo, consecuencia_no_contratar, analisis_capacidad, "
                + "puesto_id) values (?, 'DIRECTA', 'NORMAL', 'CON_VACANTE', ?, 'r', 'm', 'c', 'a', ?) returning id",
                Long.class, organizacion, area, puesto);
        Long pesos = jdbc.queryForObject("select min(id) from version_pesos", Long.class);
        return jdbc.queryForObject("insert into vacante (organizacion_id, solicitud_talento_id, puesto_id, titulo, "
                + "descripcion, tipo_cierre, estado, version_pesos_id, responsable_usuario_id) values (?, ?, ?, ?, "
                + "'Descripción', 'PERMANENTE', 'PUBLICADA', ?, ?) returning id", Long.class, organizacion,
                solicitud, puesto, titulo, pesos, responsable);
    }

    private long postulacion(long vacante, long usuario, String estado) {
        return jdbc.queryForObject("insert into postulacion (organizacion_id, usuario_id, vacante_id, estado_codigo) "
                + "values (?, ?, ?, ?) returning id", Long.class, plataforma, usuario, vacante, estado);
    }

    private long equipo(long organizacion, String rol, String nombre) {
        Long persona = jdbc.queryForObject("insert into persona (nombre, apellidos) values (?, 'Equipo') "
                + "returning id", Long.class, nombre);
        Long usuario = jdbc.queryForObject("insert into usuario (organizacion_id, persona_id, correo, es_equipo, "
                + "es_activo) values (?, ?, ?, true, true) returning id", Long.class, organizacion, persona,
                nombre.toLowerCase() + "@equipo.pe");
        jdbc.update("insert into usuario_rol (usuario_id, rol_id) select ?, id from rol where organizacion_id = ? "
                + "and codigo = ?", usuario, organizacion, rol);
        return usuario;
    }

    private long candidata(String correo, String nombre, String apellidos) throws Exception {
        mvc.perform(post("/api/v1/portal/cuentas").contentType(MediaType.APPLICATION_JSON).content("""
                {"nombre":"%s","apellidos":"%s","correo":"%s","contrasena":"unaClaveLarga123",
                 "ciudadUbigeo":"1501","aceptaPlataforma":true,"aceptaFuturosContactos":false}"""
                .formatted(nombre, apellidos, correo))).andExpect(status().isCreated());
        return jdbc.queryForObject("select id from usuario where lower(correo) = ?", Long.class, correo);
    }

    private String alcance(long organizacion, String rol, String permiso) {
        List<String> alcances = jdbc.queryForList("""
                select rp.alcance from rol_permiso rp join rol r on r.id = rp.rol_id
                  join permiso p on p.id = rp.permiso_id
                 where r.organizacion_id = ? and r.codigo = ? and p.codigo = ?""",
                String.class, organizacion, rol, permiso);
        return alcances.isEmpty() ? null : alcances.get(0);
    }

    private static Map<String, String> permisosDe(JsonNode sesion) {
        Map<String, String> permisos = new LinkedHashMap<>();
        sesion.get("permisos").forEach(p -> permisos.put(p.get("codigo").asText(), p.get("alcance").asText()));
        return permisos;
    }

    private static JsonNode buscar(JsonNode lista, String campo, long valor) {
        for (JsonNode n : lista) {
            if (n.get(campo).asLong() == valor) return n;
        }
        throw new AssertionError("No está " + campo + "=" + valor + " en " + lista);
    }

    private static List<Long> ids(JsonNode lista) {
        return ids(lista, "id");
    }

    private static List<Long> ids(JsonNode lista, String campo) {
        List<Long> ids = new ArrayList<>();
        lista.forEach(n -> ids.add(n.get(campo).asLong()));
        return ids;
    }

    private static List<String> nombres(JsonNode lista) {
        return nombres(lista, "nombreCompleto");
    }

    private static List<String> nombres(JsonNode lista, String campo) {
        List<String> nombres = new ArrayList<>();
        lista.forEach(n -> nombres.add(n.get(campo).asText()));
        return nombres;
    }

    private int contar(String sql) {
        Integer valor = jdbc.queryForObject(sql, Integer.class);
        return valor == null ? 0 : valor;
    }

    private long id(ResultActions resultado) throws Exception {
        return cuerpo(resultado).get("id").asLong();
    }

    private JsonNode cuerpo(ResultActions resultado) throws Exception {
        return json.readTree(resultado.andReturn().getResponse().getContentAsString());
    }

    private ResultActions conToken(MockHttpServletRequestBuilder peticion, String token, String cuerpo)
            throws Exception {
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
