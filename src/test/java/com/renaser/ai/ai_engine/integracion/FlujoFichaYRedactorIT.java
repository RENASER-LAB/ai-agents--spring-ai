package com.renaser.ai.ai_engine.integracion;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.renaser.ai.ai_engine.ai.dto.RespuestaModelo;
import com.renaser.ai.ai_engine.ai.service.ClienteModelo;
import com.renaser.ai.ai_engine.integracion.soporte.ImagenesDeContenedores;
import com.renaser.ai.ai_engine.perfilintegral.dto.DtosCuestionarioTecnico.BloquePedido;
import com.renaser.ai.ai_engine.perfilintegral.service.RecetaCuestionarioTecnico;

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
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.rabbitmq.RabbitMQContainer;

import java.util.function.BooleanSupplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * El ciclo 1 de la prueba técnica, de punta a punta: el dueño llena su ficha, el REDACTOR
 * escribe el borrador por la cola de verdad (RabbitMQ y todo), el dueño corrige y publica.
 *
 * <p>El modelo está sustituido por un doble que sabe escribir un cuestionario DIRECCION
 * correcto — y uno roto a propósito, que es lo que permite comprobar que un borrador que
 * no pasa la aduana jamás se guarda a medias.
 *
 * <p>Las reglas que se comprueban aquí y en ningún otro sitio:
 * <ul>
 *   <li>Sin ficha COMPLETA no se genera nada.
 *   <li>La muestra PRESENCIAL llega marcada y sin guía: jamás viajará al candidato.
 *   <li>Editar la ficha después de generar marca el cuestionario como desactualizado.
 *   <li>La publicación re-pasa la aduana (el dueño pudo romper el borrador editando).
 *   <li>Publicar el cuestionario de una vacante NO toca los bancos por nivel.
 *   <li>Un borrador que no cuadra deja el trabajo FALLIDO y nada en el banco.
 * </ul>
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@Testcontainers
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@DisplayName("La ficha y el REDACTOR")
public class FlujoFichaYRedactorIT {

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
        // Dos intentos: la prueba del borrador roto tiene que agotarlos rápido.
        registro.add("renaser.ai.calificacion.max-intentos", () -> "2");
    }

    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    final ObjectMapper json = new ObjectMapper();

    static String token;
    static long vacanteId;

    // ============ El doble del modelo ============

    @TestConfiguration
    static class ConfiguracionDePrueba {
        @Bean
        @Primary
        ClienteModelo clienteModeloDePrueba() {
            return new ModeloDePrueba();
        }
    }

    static class ModeloDePrueba implements ClienteModelo {

        /** Con esto encendido devuelve un borrador sin guía: la aduana lo tiene que parar. */
        static volatile boolean rota = false;

        @Override
        public RespuestaModelo preguntar(String agente, String instruccion, String contenido) {
            return preguntar(agente, instruccion, contenido, true);
        }

        @Override
        public RespuestaModelo preguntar(String agente, String instruccion, String contenido,
                                         boolean razona) {
            if (!"REDACTOR".equals(agente)) {
                throw new IllegalStateException("agente inesperado: " + agente);
            }
            return new RespuestaModelo(cuestionario("DIRECCION", rota),
                    "deepseek-v4-flash", "deepseek", "prueba", 1500, 900);
        }

        /** Un cuestionario que respeta la receta del nivel — o la rompe, si se le pide. */
        private static String cuestionario(String nivel, boolean sinGuia) {
            StringBuilder filas = new StringBuilder();
            int n = 1;
            for (BloquePedido bloque : RecetaCuestionarioTecnico.estructura(nivel)) {
                for (int i = 0; i < bloque.cantidad(); i++) {
                    boolean presencial = RecetaCuestionarioTecnico.PRESENCIAL
                            .equals(bloque.bloque());
                    if (filas.length() > 0) {
                        filas.append(",");
                    }
                    filas.append("""
                            {"codigo":"T%02d","bloque":"%s","bloqueEtiqueta":"Bloque %s",
                             "enunciado":"¿Cuál es la operación más grande de cambio de divisas que has administrado?",
                             "c3Esperado":%s,"c4Esperado":%s,"senalDeCero":%s,
                             "presencial":%s}"""
                            .formatted(n++, bloque.bloque(), bloque.bloque(),
                                    presencial || sinGuia ? "null" : "\"montos y volúmenes\"",
                                    presencial ? "null" : "\"el faltante que encontró\"",
                                    presencial ? "null" : "\"respuesta genérica sin episodio\"",
                                    presencial));
                }
            }
            return "{\"preguntas\":[" + filas + "]}";
        }
    }

    // ============ Las pruebas, en orden ============

    @Test
    @Order(1)
    @DisplayName("Media ficha no genera nada: el botón exige COMPLETA")
    void mediaFichaNoGenera() throws Exception {
        token = entrar();
        vacanteId = crearVacanteDireccion();

        // La ficha a medias: solo dos respuestas.
        conToken(put(ficha()), token, """
                {"q1Resultado": "Rentabilidad de las tres sedes al alza",
                 "riesgo1": "Caja y efectivo"}""")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("BORRADOR"));

        conToken(post(cuestionario() + "/generacion"), token, null)
                .andExpect(status().is4xxClientError());
        assertThat(contar("select count(*) from trabajo_ia where agente_codigo = 'REDACTOR'"))
                .isZero();
    }

    @Test
    @Order(2)
    @DisplayName("Con la ficha COMPLETA el REDACTOR deja el borrador: 12 preguntas y la muestra marcada")
    void elRedactorDejaElBorrador() throws Exception {
        ModeloDePrueba.rota = false;
        conToken(put(ficha()), token, fichaCompleta())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("COMPLETA"))
                .andExpect(jsonPath("$.tamano").value("MICRO"));

        conToken(post(cuestionario() + "/generacion"), token, null)
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.encolada").value(true));

        esperarA(() -> contar("select count(*) from version_banco where vacante_id = "
                + vacanteId + " and estado = 'BORRADOR'") == 1, "el REDACTOR deje el borrador");

        String cuerpo = conToken(get(cuestionario()), token, null)
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        JsonNode r = json.readTree(cuerpo);
        assertThat(r.get("estado").asText()).isEqualTo("BORRADOR");
        assertThat(r.get("generacion").asText()).isEqualTo("LISTA");
        assertThat(r.get("desactualizado").asBoolean()).isFalse();
        assertThat(r.get("preguntas")).hasSize(12);
        // La muestra: una sola, la última, sin guía. Jamás viajará al candidato.
        JsonNode muestra = r.get("preguntas").get(11);
        assertThat(muestra.get("presencial").asBoolean()).isTrue();
        assertThat(muestra.get("c3Esperado").isNull()).isTrue();
        assertThat(contar("select count(*) from pregunta p join version_banco v on "
                + "p.version_banco_id = v.id where v.vacante_id = " + vacanteId
                + " and p.presencial")).isEqualTo(1);
    }

    @Test
    @Order(3)
    @DisplayName("Dos clics seguidos no pagan dos generaciones… pero regenerar después sí crea otra")
    void dosClicsUnaLlamada() throws Exception {
        // Ya hay un borrador LISTO (trabajo TERMINADO): otro clic sí regenera.
        // Lo que frena es un trabajo VIVO — y eso ya lo probó el unitario del registro.
        // Aquí se comprueba lo contrario: que lo TERMINADO no exime.
        conToken(post(cuestionario() + "/generacion"), token, null)
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.encolada").value(true));

        esperarA(() -> contar("select count(*) from trabajo_ia where agente_codigo = 'REDACTOR'"
                + " and estado = 'TERMINADO'") == 2, "la regeneración termine");

        // El borrador anterior quedó archivado: solo hay un borrador vivo.
        assertThat(contar("select count(*) from version_banco where vacante_id = " + vacanteId
                + " and estado = 'BORRADOR'")).isEqualTo(1);
        assertThat(contar("select count(*) from version_banco where vacante_id = " + vacanteId
                + " and estado = 'ARCHIVADA'")).isEqualTo(1);
    }

    @Test
    @Order(4)
    @DisplayName("El dueño corrige una pregunta con sus palabras, y editar la ficha desactualiza")
    void corregirYDesactualizar() throws Exception {
        long preguntaId = jdbc.queryForObject("select p.id from pregunta p join version_banco v"
                + " on p.version_banco_id = v.id where v.vacante_id = ? and v.estado = 'BORRADOR'"
                + " and p.orden = 3", Long.class, vacanteId);

        conToken(put(cuestionario() + "/preguntas/" + preguntaId), token, """
                {"enunciado": "¿Has cuadrado caja de casa de cambio con tres monedas a la vez?",
                 "c3Esperado": "monedas y montos por sede",
                 "c4Esperado": "el descuadre que no pudo explicar",
                 "senalDeCero": "nunca cuadró una caja"}""")
                .andExpect(status().isOk());

        String cuerpo = conToken(get(cuestionario()), token, null)
                .andReturn().getResponse().getContentAsString();
        assertThat(cuerpo).contains("tres monedas a la vez");

        // La ficha se retoca → el cuestionario queda desactualizado, pero NO cambia solo.
        conToken(put(ficha()), token, fichaCompleta()
                .replace("Rentabilidad de las tres sedes al alza",
                        "Rentabilidad al alza y una sede nueva"))
                .andExpect(status().isOk());
        conToken(get(cuestionario()), token, null)
                .andExpect(jsonPath("$.desactualizado").value(true))
                .andExpect(jsonPath("$.preguntas[2].enunciado")
                        .value("¿Has cuadrado caja de casa de cambio con tres monedas a la vez?"));
    }

    @Test
    @Order(5)
    @DisplayName("La publicación re-pasa la aduana, y al publicar no toca ni un banco por nivel")
    void publicarConAduana() throws Exception {
        int nivelesPublicados = contar(
                "select count(*) from version_banco where tipo_banco = 'NIVEL' and estado = 'PUBLICADA'");

        // El dueño rompe la guía de una pregunta al editar: la aduana tiene que pararlo.
        long preguntaId = jdbc.queryForObject("select p.id from pregunta p join version_banco v"
                + " on p.version_banco_id = v.id where v.vacante_id = ? and v.estado = 'BORRADOR'"
                + " and p.orden = 1", Long.class, vacanteId);
        conToken(put(cuestionario() + "/preguntas/" + preguntaId), token, """
                {"enunciado": "¿Cuántos años llevas administrando?"}""")
                .andExpect(status().isOk());
        conToken(post(cuestionario() + "/publicacion"), token, null)
                .andExpect(status().is4xxClientError());

        // La arregla, y ahora sí.
        conToken(put(cuestionario() + "/preguntas/" + preguntaId), token, """
                {"enunciado": "¿Cuántos años llevas administrando empresas con manejo de efectivo?",
                 "c3Esperado": "años, empresas y de qué respondía",
                 "c4Esperado": "lo que le costó al empezar",
                 "senalDeCero": "no ha administrado nunca"}""")
                .andExpect(status().isOk());
        conToken(post(cuestionario() + "/publicacion"), token, null)
                .andExpect(status().isOk());

        conToken(get(cuestionario()), token, null)
                .andExpect(jsonPath("$.estado").value("PUBLICADA"));
        // Los bancos por nivel ni se enteraron: son mundos distintos.
        assertThat(contar("select count(*) from version_banco where tipo_banco = 'NIVEL' "
                + "and estado = 'PUBLICADA'")).isEqualTo(nivelesPublicados);
    }

    @Test
    @Order(6)
    @DisplayName("Regenerar y volver a publicar archiva a la publicada anterior de ESTA vacante")
    void republicarArchivaLaSuya() throws Exception {
        conToken(post(cuestionario() + "/generacion"), token, null)
                .andExpect(status().isAccepted());
        esperarA(() -> contar("select count(*) from version_banco where vacante_id = "
                + vacanteId + " and estado = 'BORRADOR'") == 1, "el nuevo borrador aparezca");

        conToken(post(cuestionario() + "/publicacion"), token, null)
                .andExpect(status().isOk());

        assertThat(contar("select count(*) from version_banco where vacante_id = " + vacanteId
                + " and estado = 'PUBLICADA'")).isEqualTo(1);
        // Nada se borró jamás: las tres versiones de la vacante siguen ahí — la primera
        // (archivada al regenerar), la segunda (publicada y luego reemplazada) y esta.
        assertThat(contar("select count(*) from version_banco where vacante_id = " + vacanteId))
                .isEqualTo(3);
    }

    @Test
    @Order(7)
    @DisplayName("Un borrador que no pasa la aduana deja el trabajo FALLIDO y nada a medias")
    void elRotoNoDejaNada() throws Exception {
        ModeloDePrueba.rota = true;
        int fallidosAntes = contar("select count(*) from trabajo_ia where agente_codigo = "
                + "'REDACTOR' and estado = 'FALLIDO'");
        try {
            conToken(post(cuestionario() + "/generacion"), token, null)
                    .andExpect(status().isAccepted())
                    .andExpect(jsonPath("$.encolada").value(true));

            esperarA(() -> contar("select count(*) from trabajo_ia where agente_codigo = "
                    + "'REDACTOR' and estado = 'FALLIDO'") == fallidosAntes + 1,
                    "el trabajo agote sus intentos");

            conToken(get(cuestionario()), token, null)
                    .andExpect(jsonPath("$.generacion").value("FALLIDA"))
                    // Lo publicado sigue intacto: el fallo no rompió nada.
                    .andExpect(jsonPath("$.estado").value("PUBLICADA"));
            assertThat(contar("select count(*) from version_banco where vacante_id = "
                    + vacanteId + " and estado = 'BORRADOR'")).isZero();
        } finally {
            ModeloDePrueba.rota = false;
        }
    }

    // ============ Los ayudantes ============

    private String ficha() {
        return "/api/v1/panel/vacantes/" + vacanteId + "/ficha";
    }

    private String cuestionario() {
        return "/api/v1/panel/vacantes/" + vacanteId + "/cuestionario-tecnico";
    }

    private static String fichaCompleta() {
        return """
                {"q1Resultado": "Rentabilidad de las tres sedes al alza",
                 "q2Riesgo": "La caja: un faltante se nota en el día",
                 "q3DiaReal": "Abre las sedes, cuadra cajas, revisa el margen, cierra",
                 "q4EpocaDorada": "Rosa cuadraba las tres cajas sin que se le pidiera",
                 "q5Estructura": "Somos 12, tendrá 6 a cargo",
                 "q6Autonomia": "Compras chicas y horarios",
                 "q7JefeDirecto": "A mí directamente",
                 "q8LoIncomodo": "Fines de semana y feriados se trabaja",
                 "q9Requerimientos": "Coordinar con el contador y revisar reportes",
                 "genteEnEmpresa": 12, "genteACargo": 6,
                 "riesgo1": "Caja y efectivo",
                 "riesgo2": "Margen en divisas",
                 "riesgo3": "Control a distancia",
                 "riesgo4": "Personal",
                 "eliminatoria1": "Manejo de caja y efectivo",
                 "requerimiento1": "Coordinación con contadores",
                 "familias": "F4,F1"}""";
    }

    private String entrar() throws Exception {
        return leer(mvc.perform(post("/api/v1/panel/auth/dev-login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"usuarioRenaserOsId\":\"dev-1\"}"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(), "token");
    }

    /** Una vacante DIRECCION en borrador: para la ficha y el cuestionario no hace falta más. */
    private long crearVacanteDireccion() throws Exception {
        jdbc.update("INSERT INTO area (organizacion_id, nombre, es_activa) VALUES (1, 'Operaciones', true)");
        Long areaId = jdbc.queryForObject("SELECT id FROM area LIMIT 1", Long.class);

        long solicitudId = Long.parseLong(leer(conToken(post("/api/v1/panel/solicitudes"), token, """
                {"areaId": %d, "urgencia": "NORMAL",
                 "nivelPuestoCodigo": "DIRECCION", "familiaCodigo": "DIRECCION_NEGOCIO",
                 "resultadoPrincipal": "Rentabilidad de las tres sedes",
                 "motivo": "El dueño no llega a las tres sedes",
                 "consecuenciaNoContratar": "Faltantes sin control",
                 "analisisCapacidad": "Nadie del equipo puede asumirlo",
                 "responsableUsuarioId": 1,
                 "resultadosEsperados": [
                   {"descripcion": "Cajas cuadradas", "indicador": "cero faltantes"},
                   {"descripcion": "Margen sostenido", "indicador": "al presupuesto"},
                   {"descripcion": "Supervisión de sedes", "indicador": "semanal"}
                 ]}""".formatted(areaId))
                .andReturn().getResponse().getContentAsString(), "id"));
        conToken(post("/api/v1/panel/solicitudes/" + solicitudId + "/aprobacion"), token,
                "{\"motivo\":\"Hay presupuesto\"}").andExpect(status().isOk());

        long puestoId = Long.parseLong(leer(conToken(post("/api/v1/panel/puestos"), token, """
                {"codigo": "ADM_SEDES", "nombre": "Administrador de sedes",
                 "nivelPuestoCodigo": "DIRECCION", "familiaCodigo": "DIRECCION_NEGOCIO"}""")
                .andReturn().getResponse().getContentAsString(), "id"));

        return Long.parseLong(leer(conToken(post("/api/v1/panel/vacantes"), token, """
                {"solicitudTalentoId": %d, "puestoId": %d,
                 "titulo": "Administrador de casa de cambio", "descripcion": "Tres sedes",
                 "tipoCierre": "PERMANENTE", "responsableUsuarioId": 1}"""
                .formatted(solicitudId, puestoId))
                .andReturn().getResponse().getContentAsString(), "id"));
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
                + jdbc.queryForList("select id, agente_codigo, estado, intentos from trabajo_ia")
                + " · Ejecuciones: "
                + jdbc.queryForList("select agente_codigo, es_exitosa, error from ejecucion_ia"));
    }

    private int contar(String sql) {
        Integer valor = jdbc.queryForObject(sql, Integer.class);
        return valor == null ? 0 : valor;
    }

    private ResultActions conToken(
            org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder peticion,
            String token, String cuerpo) throws Exception {
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
