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
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.rabbitmq.RabbitMQContainer;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * «Pruebas», de punta a punta (spec pruebas-indice-de-la-empresa): el índice de lo escrito en el
 * editor de las vacantes de una empresa, su vista previa, los destinos de la copia y el rechazo
 * nuevo de copiar en una vacante que no usa el editor.
 *
 * <p>Los datos son los de «Verificación» de la spec: una vacante con preguntas publicadas, otra
 * con la prueba en borrador, una cerrada con la prueba publicada, una del sistema antiguo
 * (PLANTILLA) que no sale, una con la prueba publicada solo con entregables, un rol con
 * {@code ver_vacantes} acotado a SUS_VACANTES y una segunda empresa.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@Testcontainers
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@DisplayName("«Pruebas»: el índice de la empresa, su vista previa y la copia a otra vacante")
public class FlujoIndiceDePruebasIT {

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

    private static final String INDICE = "/api/v1/panel/pruebas";

    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired PasswordEncoder codificador;
    final ObjectMapper json = new ObjectMapper();

    static String tokenTalento;
    static long areaId;
    static long puestoId;
    /** Preguntas propias publicadas. */
    static long conPreguntas;
    /** La prueba del puesto en borrador. */
    static long pruebaEnBorrador;
    /** Cerrada, con la prueba publicada. */
    static long cerrada;
    /** Del sistema antiguo: rinde una plantilla, con una prueba del editor que ya no usa. */
    static long conPlantilla;
    /** La prueba publicada solo con entregables. */
    static long soloEntregables;
    /** Pasó al banco por nivel con sus preguntas propias en borrador. */
    static long porNivel;

    // ============ Lo que sale en cada pestaña ============

    @DisplayName("Cada pestaña lista lo escrito en su editor, con su estado y su resumen; lo antiguo no sale (AC-02, AC-03)")
    @Test
    @Order(1)
    void cadaPestanaListaLoSuyo() throws Exception {
        tokenTalento = leer(mvc.perform(post("/api/v1/panel/auth/dev-login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"usuarioRenaserOsId\":\"dev-talento\"}"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString(), "token");
        jdbc.update("INSERT INTO area (organizacion_id, nombre, es_activa) VALUES (1, 'Operaciones', true)");
        areaId = jdbc.queryForObject("SELECT id FROM area ORDER BY id DESC LIMIT 1", Long.class);
        puestoId = Long.parseLong(leer(conToken(post("/api/v1/panel/puestos"), tokenTalento, """
                {"codigo": "QA_INDICE", "nombre": "Asistente de operaciones",
                 "nivelPuestoCodigo": "EJECUCION", "familiaCodigo": "OPERACIONES"}""")
                .andReturn().getResponse().getContentAsString(), "id"));

        conPreguntas = crearVacante("Asistente contable");
        publicarPreguntas(conPreguntas);

        pruebaEnBorrador = crearVacante("Cajero");
        conToken(post(prueba(pruebaEnBorrador) + "/criterios"), tokenTalento,
                "{\"nombre\":\"Cálculo\",\"puntos\":100,\"calificador\":\"IA\"}").andExpect(status().isOk());

        cerrada = crearVacante("Jefe de almacén");
        publicarPrueba(cerrada);
        jdbc.update("update vacante set estado = 'CERRADA', cerrada_en = now() where id = ?", cerrada);

        conPlantilla = crearVacante("Supervisor con plantilla");
        conToken(post(prueba(conPlantilla) + "/criterios"), tokenTalento,
                "{\"nombre\":\"Antes\",\"puntos\":100,\"calificador\":\"IA\"}").andExpect(status().isOk());
        jdbc.update("update vacante set instrumento_etapa_tecnica = 'PLANTILLA' where id = ?", conPlantilla);

        soloEntregables = crearVacante("Diseñador gráfico");
        publicarPrueba(soloEntregables);

        porNivel = crearVacante("Analista por nivel");
        conToken(post(preguntas(porNivel) + "/preguntas"), tokenTalento, """
                {"tipo":"ABIERTA","enunciado":"¿Algo?","puntos":100,"queDebeTener":"Algo"}""")
                .andExpect(status().isOk());
        conToken(post("/api/v1/panel/vacantes/" + porNivel + "/origen-preguntas"), tokenTalento,
                "{\"origen\":\"NIVEL\"}").andExpect(status().isOk());

        // Preguntas del Perfil Integral: la publicada, y no la que pasó al banco por nivel
        List<JsonNode> deLasPreguntas = filas("PERFIL_INTEGRAL", tokenTalento);
        assertThat(ids(deLasPreguntas)).contains(conPreguntas).doesNotContain(porNivel);
        JsonNode fila = fila(deLasPreguntas, conPreguntas);
        assertThat(fila.get("estado").asText()).isEqualTo("PUBLICADA");
        assertThat(fila.get("criterios").asInt()).isEqualTo(1);
        assertThat(fila.get("preguntas").asInt()).isEqualTo(1);
        assertThat(fila.get("nivel").asText()).isEqualTo("EJECUCION");
        assertThat(fila.get("situacion").asText()).isEqualTo("SIN_PUBLICAR");
        assertThat(fila.get("laRindeAlguien").asBoolean()).isFalse();
        assertThat(fila.get("hayBorradorAbierto").asBoolean()).isFalse();
        assertThat(fila.get("publicadaEn").isNull()).isFalse();
        assertThat(fila.get("puedeUsarEnOtra").asBoolean()).isTrue();
        assertThat(fila.get("entregables").isNull()).isTrue();

        // Prueba del puesto: la del borrador, la cerrada y la de solo entregables; nunca la de
        // plantilla. Las publicadas primero (la más reciente arriba) y el borrador al final.
        List<JsonNode> deLaPrueba = filas("PRUEBA_PUESTO", tokenTalento);
        assertThat(ids(deLaPrueba)).doesNotContain(conPlantilla, conPreguntas)
                .containsSubsequence(soloEntregables, cerrada, pruebaEnBorrador);
        JsonNode borrador = fila(deLaPrueba, pruebaEnBorrador);
        assertThat(borrador.get("estado").asText()).isEqualTo("BORRADOR");
        assertThat(borrador.get("publicadaEn").isNull()).isTrue();
        assertThat(borrador.get("puedeUsarEnOtra").asBoolean()).isFalse();
        JsonNode deCerrada = fila(deLaPrueba, cerrada);
        assertThat(deCerrada.get("situacion").asText()).isEqualTo("CERRADA");
        JsonNode deEntregables = fila(deLaPrueba, soloEntregables);
        assertThat(deEntregables.get("estado").asText()).isEqualTo("PUBLICADA");
        assertThat(deEntregables.get("preguntas").asInt()).isZero();
        assertThat(deEntregables.get("entregables").asInt()).isEqualTo(1);
        assertThat(deEntregables.get("modalidad").asText()).isEqualTo("CRONOMETRADA");
        assertThat(deEntregables.get("minutos").asInt()).isEqualTo(90);
        assertThat(deEntregables.get("dias").isNull()).isTrue();
    }

    @DisplayName("Los filtros se combinan en el servidor; un tipo que no existe es 400 (AC-06)")
    @Test
    @Order(2)
    void losFiltrosSeCombinan() throws Exception {
        assertThat(ids(lista(INDICE + "?tipo=PRUEBA_PUESTO&nivel=EJECUCION&estado=PUBLICADA&situacion=CERRADA",
                tokenTalento))).containsExactly(cerrada);
        assertThat(ids(lista(INDICE + "?tipo=PRUEBA_PUESTO&buscar=DISENADOR", tokenTalento)))
                .containsExactly(soloEntregables);
        assertThat(ids(lista(INDICE + "?tipo=PRUEBA_PUESTO&estado=BORRADOR,PUBLICADA&situacion=SIN_PUBLICAR",
                tokenTalento))).containsExactlyInAnyOrder(pruebaEnBorrador, soloEntregables);
        assertThat(lista(INDICE + "?tipo=PRUEBA_PUESTO&nivel=DIRECCION", tokenTalento)).isEmpty();

        conTokenGet(INDICE + "?tipo=OTRA_COSA", tokenTalento).andExpect(status().isBadRequest());
        conTokenGet(INDICE, tokenTalento).andExpect(status().isBadRequest());
    }

    @DisplayName("Con un borrador abierto, la fila y la vista previa son de la publicada; solo borrador, el borrador sin avisos; las archivadas no salen (AC-07, AC-08)")
    @Test
    @Order(3)
    void laPublicadaMandaSobreElBorrador() throws Exception {
        conToken(post(preguntas(conPreguntas) + "/borrador"), tokenTalento, null).andExpect(status().isOk());
        JsonNode conBorrador = fila(filas("PERFIL_INTEGRAL", tokenTalento), conPreguntas);
        assertThat(conBorrador.get("estado").asText()).isEqualTo("PUBLICADA");
        assertThat(conBorrador.get("hayBorradorAbierto").asBoolean()).isTrue();
        long publicadaId = jdbc.queryForObject("select id from version_banco where vacante_id = ? and "
                + "proposito = 'PERFIL_INTEGRAL' and estado = 'PUBLICADA'", Long.class, conPreguntas);
        conTokenGet(INDICE + "/" + conPreguntas + "?tipo=PERFIL_INTEGRAL", tokenTalento)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(publicadaId))
                .andExpect(jsonPath("$.estado").value("PUBLICADA"));

        // Solo borrador: el borrador, en lectura y sin lo que frena publicarlo (le falta la fecha)
        conTokenGet(INDICE + "/" + pruebaEnBorrador + "?tipo=PRUEBA_PUESTO", tokenTalento)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("BORRADOR"))
                .andExpect(jsonPath("$.avisos.length()").value(0))
                .andExpect(jsonPath("$.criterios[0].nombre").value("Cálculo"));
        // En el editor, ese mismo borrador sí trae sus avisos: son de allí
        conTokenGet(prueba(pruebaEnBorrador), tokenTalento)
                .andExpect(jsonPath("$.borrador.avisos.length()").value(org.hamcrest.Matchers.greaterThan(0)));

        // Publicar el borrador archiva la anterior, que no sale: sigue habiendo una sola fila
        conToken(post(preguntas(conPreguntas) + "/publicacion"), tokenTalento, null).andExpect(status().isOk());
        List<JsonNode> despues = filas("PERFIL_INTEGRAL", tokenTalento);
        assertThat(ids(despues).stream().filter(id -> id == conPreguntas)).hasSize(1);
        assertThat(fila(despues, conPreguntas).get("hayBorradorAbierto").asBoolean()).isFalse();

        // Lo que no está en el índice tampoco se mira desde él
        conTokenGet(INDICE + "/" + conPlantilla + "?tipo=PRUEBA_PUESTO", tokenTalento)
                .andExpect(status().isNotFound());
        conTokenGet(INDICE + "/" + porNivel + "?tipo=PERFIL_INTEGRAL", tokenTalento)
                .andExpect(status().isNotFound());
    }

    // ============ Empresas y alcance ============

    @DisplayName("Dos empresas no se ven: ni en la lista ni en la vista previa, que es 404 (AC-04)")
    @Test
    @Order(4)
    void dosEmpresasNoSeVen() throws Exception {
        long deLaOtra = crearVacante("Contador de la otra empresa");
        publicarPreguntas(deLaOtra);
        long otraEmpresa = jdbc.queryForObject(
                "insert into organizacion (codigo, nombre) values ('QA_INDICE_B', 'Otra S.A.C.') returning id",
                Long.class);
        jdbc.update("update vacante set organizacion_id = ? where id = ?", otraEmpresa, deLaOtra);
        jdbc.update("update version_banco set organizacion_id = ? where vacante_id = ?", otraEmpresa, deLaOtra);
        String tokenOtra = cuentaCon(otraEmpresa, "qa-indice-otra", "QA_INDICE_OTRA",
                List.of("ver_vacantes:TODO", "editar_vacante:TODO"));

        assertThat(ids(filas("PERFIL_INTEGRAL", tokenTalento))).doesNotContain(deLaOtra);
        conTokenGet(INDICE + "/" + deLaOtra + "?tipo=PERFIL_INTEGRAL", tokenTalento)
                .andExpect(status().isNotFound());

        assertThat(ids(filas("PERFIL_INTEGRAL", tokenOtra))).containsExactly(deLaOtra);
        assertThat(filas("PRUEBA_PUESTO", tokenOtra)).isEmpty();
        conTokenGet(INDICE + "/" + conPreguntas + "?tipo=PERFIL_INTEGRAL", tokenOtra)
                .andExpect(status().isNotFound());
        assertThat(ids(lista(INDICE + "/destinos?tipo=PERFIL_INTEGRAL", tokenOtra))).doesNotContain(conPreguntas);
    }

    @DisplayName("Con ver_vacantes acotado a SUS_VACANTES solo salen las suyas, y sin editar_vacante no hay copia (AC-05, AC-13)")
    @Test
    @Order(5)
    void elAlcanceAcotaLaLista() throws Exception {
        String tokenAcotado = cuentaCon(1L, "qa-indice-acotado", "QA_INDICE_SUS_VACANTES",
                List.of("ver_vacantes:SUS_VACANTES"));
        long suyoId = jdbc.queryForObject("select id from usuario where usuario_renaser_os_id = 'qa-indice-acotado'",
                Long.class);
        jdbc.update("update vacante set responsable_usuario_id = ? where id = ?", suyoId, conPreguntas);

        List<JsonNode> suyas = filas("PERFIL_INTEGRAL", tokenAcotado);
        assertThat(ids(suyas)).containsExactly(conPreguntas);
        assertThat(suyas.get(0).get("puedeUsarEnOtra").asBoolean()).isFalse();
        assertThat(filas("PRUEBA_PUESTO", tokenAcotado)).isEmpty();
        conTokenGet(INDICE + "/" + pruebaEnBorrador + "?tipo=PRUEBA_PUESTO", tokenAcotado)
                .andExpect(status().isNotFound());
        conTokenGet(INDICE + "/" + conPreguntas + "?tipo=PERFIL_INTEGRAL", tokenAcotado)
                .andExpect(status().isOk());
        // Sin editar_vacante, ni destinos ni copia: tampoco por API
        conTokenGet(INDICE + "/destinos?tipo=PERFIL_INTEGRAL", tokenAcotado).andExpect(status().isForbidden());
        long nueva = crearVacante("Asistente nuevo");
        conToken(post(preguntas(nueva) + "/copia"), tokenAcotado,
                "{\"vacanteOrigenId\":%d}".formatted(conPreguntas)).andExpect(status().isForbidden());
        jdbc.update("update vacante set responsable_usuario_id = 1 where id = ?", conPreguntas);
    }

    // ============ Usar en otra vacante ============

    @DisplayName("Los destinos de las preguntas: con postulantes, deshabilitada; sin el editor, fuera; y el servidor rechaza las dos (AC-11, AC-15)")
    @Test
    @Order(6)
    void losDestinosDeLasPreguntas() throws Exception {
        long conPostulantes = crearVacante("Asistente con postulantes");
        publicarPreguntas(conPostulantes);
        postular(conPostulantes);
        long libre = crearVacante("Asistente libre");
        conToken(post(preguntas(libre) + "/preguntas"), tokenTalento, """
                {"tipo":"ABIERTA","enunciado":"Borrador que se reemplaza","puntos":10,"queDebeTener":"x"}""")
                .andExpect(status().isOk());

        List<JsonNode> destinos = lista(INDICE + "/destinos?tipo=PERFIL_INTEGRAL&excluir=" + conPreguntas,
                tokenTalento);
        assertThat(ids(destinos)).contains(conPostulantes, libre)
                .doesNotContain(conPreguntas, porNivel, cerrada);
        JsonNode bloqueada = fila(destinos, conPostulantes);
        assertThat(bloqueada.get("puedeRecibir").asBoolean()).isFalse();
        assertThat(bloqueada.get("motivo").asText()).isEqualTo("Ya tiene postulantes: sus preguntas no se reemplazan");
        JsonNode conBorrador = fila(destinos, libre);
        assertThat(conBorrador.get("puedeRecibir").asBoolean()).isTrue();
        assertThat(conBorrador.get("tieneBorrador").asBoolean()).isTrue();
        // Las que pueden recibirla van antes que las deshabilitadas
        assertThat(ids(destinos).indexOf(libre)).isLessThan(ids(destinos).indexOf(conPostulantes));
        // Sin excluir, la de origen también se ofrece (el bloque de las pruebas modelo no la tiene)
        assertThat(ids(lista(INDICE + "/destinos?tipo=PERFIL_INTEGRAL", tokenTalento))).contains(conPreguntas);

        // La fila de la que ya se rinde lo dice
        assertThat(fila(filas("PERFIL_INTEGRAL", tokenTalento), conPostulantes).get("laRindeAlguien").asBoolean())
                .isTrue();

        // El servidor rechaza las dos con su texto
        conToken(post(preguntas(conPostulantes) + "/copia"), tokenTalento,
                "{\"vacanteOrigenId\":%d}".formatted(conPreguntas))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value(org.hamcrest.Matchers.startsWith("Esta vacante ya tiene postulantes")));
        conToken(post(preguntas(porNivel) + "/copia"), tokenTalento,
                "{\"vacanteOrigenId\":%d}".formatted(conPreguntas))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value(org.hamcrest.Matchers.containsString("no responde preguntas propias")));
        long borradoresDePorNivel = jdbc.queryForObject("select count(*) from version_banco where vacante_id = ? "
                + "and proposito = 'PERFIL_INTEGRAL' and estado = 'BORRADOR'", Long.class, porNivel);
        assertThat(borradoresDePorNivel).isEqualTo(1);

        // Copiar en la libre reemplaza su borrador; la original no cambia (AC-10)
        long publicadaOrigen = jdbc.queryForObject("select id from version_banco where vacante_id = ? and "
                + "proposito = 'PERFIL_INTEGRAL' and estado = 'PUBLICADA'", Long.class, conPreguntas);
        JsonNode copiado = json.readTree(conToken(post(preguntas(libre) + "/copia"), tokenTalento,
                        "{\"vacanteOrigenId\":%d}".formatted(conPreguntas))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        assertThat(copiado.at("/borrador/total").asInt()).isEqualTo(100);
        assertThat(copiado.at("/borrador/criterios/0/preguntas/0/enunciado").asText())
                .isEqualTo("¿Cómo cuadras una caja con un faltante?");
        assertThat(jdbc.queryForObject("select id from version_banco where vacante_id = ? and "
                + "proposito = 'PERFIL_INTEGRAL' and estado = 'PUBLICADA'", Long.class, conPreguntas))
                .isEqualTo(publicadaOrigen);
    }

    @DisplayName("La prueba se usa en otra vacante sin candidatos: la copia es idéntica, sin fecha; la que ya se rinde y la de plantilla se rechazan (AC-10, AC-11, AC-15)")
    @Test
    @Order(7)
    void laPruebaEnOtraVacante() throws Exception {
        long enUso = crearVacante("Diseñador en uso");
        long versionEnUso = publicarPrueba(enUso);
        long postulacion = postular(enUso);
        jdbc.update("insert into intento_prueba (postulacion_id, version_banco_id, iniciado_en) values (?, ?, now())",
                postulacion, versionEnUso);
        long nueva = crearVacante("Diseñador nuevo");

        JsonNode filaEnUso = fila(filas("PRUEBA_PUESTO", tokenTalento), enUso);
        assertThat(filaEnUso.get("laRindeAlguien").asBoolean()).isTrue();
        assertThat(ids(lista(INDICE + "?tipo=PRUEBA_PUESTO&estado=EN_USO", tokenTalento))).containsExactly(enUso);

        List<JsonNode> destinos = lista(INDICE + "/destinos?tipo=PRUEBA_PUESTO&excluir=" + soloEntregables,
                tokenTalento);
        assertThat(ids(destinos)).contains(enUso, nueva).doesNotContain(conPlantilla, soloEntregables, cerrada);
        assertThat(fila(destinos, enUso).get("motivo").asText())
                .isEqualTo("Ya la rinden candidatos: su prueba no se reemplaza");
        assertThat(fila(destinos, nueva).get("puedeRecibir").asBoolean()).isTrue();
        assertThat(fila(destinos, nueva).get("tieneBorrador").asBoolean()).isFalse();

        conToken(post(prueba(enUso) + "/copia"), tokenTalento, "{\"vacanteOrigenId\":%d}".formatted(soloEntregables))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value(org.hamcrest.Matchers.startsWith("Alguien ya empezó a rendir esta prueba")));
        conToken(post(prueba(conPlantilla) + "/copia"), tokenTalento, "{\"vacanteOrigenId\":%d}".formatted(soloEntregables))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value(org.hamcrest.Matchers.containsString("no rinde la prueba del editor")));

        JsonNode copiado = json.readTree(conToken(post(prueba(nueva) + "/copia"), tokenTalento,
                        "{\"vacanteOrigenId\":%d}".formatted(soloEntregables))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        assertThat(copiado.at("/borrador/estado").asText()).isEqualTo("BORRADOR");
        assertThat(copiado.at("/borrador/total").asInt()).isEqualTo(100);
        assertThat(copiado.at("/borrador/prueba/entregables").size()).isEqualTo(1);
        assertThat(copiado.at("/borrador/prueba/duracionMinutos").asInt()).isEqualTo(90);
        assertThat(copiado.at("/fechaLimite/cierraEn").isMissingNode()
                || copiado.at("/fechaLimite/cierraEn").isNull()).isTrue();
        // La nueva sale en el índice como borrador
        assertThat(fila(filas("PRUEBA_PUESTO", tokenTalento), nueva).get("estado").asText()).isEqualTo("BORRADOR");
    }

    @DisplayName("Una vacante eliminada sale de la lista y su vista previa es 404")
    @Test
    @Order(8)
    void laEliminadaNoSale() throws Exception {
        jdbc.update("update vacante set eliminada_en = now() where id = ?", pruebaEnBorrador);
        assertThat(ids(filas("PRUEBA_PUESTO", tokenTalento))).doesNotContain(pruebaEnBorrador);
        conTokenGet(INDICE + "/" + pruebaEnBorrador + "?tipo=PRUEBA_PUESTO", tokenTalento)
                .andExpect(status().isNotFound());
    }

    // ============ ayudas ============

    private void publicarPreguntas(long vacante) throws Exception {
        conToken(post(preguntas(vacante) + "/preguntas"), tokenTalento, """
                {"tipo":"ABIERTA","enunciado":"¿Cómo cuadras una caja con un faltante?","puntos":100,
                 "queDebeTener":"El monto y la cuenta"}""").andExpect(status().isOk());
        conToken(post(preguntas(vacante) + "/publicacion"), tokenTalento, null).andExpect(status().isOk());
    }

    /** Una prueba de 90 minutos con un solo entregable y ninguna pregunta; devuelve su versión. */
    private long publicarPrueba(long vacante) throws Exception {
        conToken(put(prueba(vacante) + "/borrador"), tokenTalento,
                "{\"modalidad\":\"CRONOMETRADA\",\"duracionMinutos\":90}").andExpect(status().isOk());
        conToken(post(prueba(vacante) + "/criterios"), tokenTalento,
                "{\"nombre\":\"Calidad de las piezas\",\"puntos\":100,\"calificador\":\"IA\"}")
                .andExpect(status().isOk());
        conToken(post(prueba(vacante) + "/entregables"), tokenTalento, """
                {"nombre":"Logotipo adaptado","formato":"ARCHIVO","obligatorio":true,"todaLaPrueba":true}""")
                .andExpect(status().isOk());
        conToken(put(prueba(vacante) + "/fecha-limite"), tokenTalento, "{\"cierraEn\":\"%s\"}"
                .formatted(Instant.now().plus(Duration.ofDays(20)).truncatedTo(ChronoUnit.SECONDS)))
                .andExpect(status().isOk());
        return json.readTree(conToken(post(prueba(vacante) + "/publicacion"), tokenTalento, null)
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString())
                .at("/publicada/id").asLong();
    }

    /** Una postulación sembrada a mano: basta para que la vara de las preguntas ya no se mueva. */
    private long postular(long vacante) {
        Long personaId = jdbc.queryForObject(
                "insert into persona (nombre, apellidos) values ('QA', 'Candidata') returning id", Long.class);
        Long usuarioId = jdbc.queryForObject("""
                insert into usuario (organizacion_id, persona_id, es_equipo, es_activo)
                values (1, ?, false, true) returning id""", Long.class, personaId);
        return jdbc.queryForObject("""
                insert into postulacion (organizacion_id, usuario_id, vacante_id, estado_codigo)
                values (1, ?, ?, 'POSTULADA') returning id""", Long.class, usuarioId, vacante);
    }

    /**
     * Una cuenta del equipo con un rol propio: permisos como «codigo:ALCANCE». Entra con correo y
     * contraseña, porque el dev-login solo conoce a la plataforma.
     */
    private String cuentaCon(long organizacionId, String renaserOsId, String rolCodigo, List<String> permisos)
            throws Exception {
        Long rolId = jdbc.queryForObject("""
                insert into rol (organizacion_id, codigo, nombre, descripcion)
                values (?, ?, ?, 'Sembrado por la prueba de «Pruebas»') returning id""",
                Long.class, organizacionId, rolCodigo, rolCodigo);
        for (String permiso : permisos) {
            String[] partes = permiso.split(":");
            jdbc.update("""
                    insert into rol_permiso (rol_id, permiso_id, alcance)
                    select ?, id, ? from permiso where codigo = ?""", rolId, partes[1], partes[0]);
        }
        Long personaId = jdbc.queryForObject(
                "insert into persona (nombre, apellidos) values ('QA', ?) returning id", Long.class, renaserOsId);
        String correo = renaserOsId + "@indice.test";
        String clave = "clave-larga-de-la-prueba-del-indice";
        Long usuarioId = jdbc.queryForObject("""
                insert into usuario (organizacion_id, persona_id, usuario_renaser_os_id, correo,
                                     contrasena_hash, es_equipo, es_activo)
                values (?, ?, ?, ?, ?, true, true) returning id""", Long.class, organizacionId, personaId,
                renaserOsId, correo, codificador.encode(clave));
        jdbc.update("insert into usuario_rol (usuario_id, rol_id) values (?, ?)", usuarioId, rolId);
        return leer(mvc.perform(post("/api/v1/panel/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"correo\":\"%s\",\"contrasena\":\"%s\"}".formatted(correo, clave)))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString(), "token");
    }

    private long crearVacante(String titulo) throws Exception {
        long solicitudId = Long.parseLong(leer(conToken(post("/api/v1/panel/solicitudes"), tokenTalento, """
                {"areaId": %d, "puestoId": %d, "urgencia": "NORMAL",
                 "nivelPuestoCodigo": "EJECUCION", "familiaCodigo": "OPERACIONES",
                 "resultadoPrincipal": "Operación al día",
                 "motivo": "Falta gente",
                 "consecuenciaNoContratar": "Se atrasa la operación",
                 "analisisCapacidad": "El equipo no alcanza",
                 "responsableUsuarioId": 1,
                 "resultadosEsperados": [
                   {"descripcion": "Cierres al día", "indicador": "antes del día 5"},
                   {"descripcion": "Conciliaciones", "indicador": "semanales"},
                   {"descripcion": "Reportes", "indicador": "mensuales"}
                 ]}""".formatted(areaId, puestoId))
                .andReturn().getResponse().getContentAsString(), "id"));
        conToken(post("/api/v1/panel/solicitudes/" + solicitudId + "/aprobacion"), tokenTalento,
                "{\"motivo\":\"Aprobada\"}").andExpect(status().isOk());
        return Long.parseLong(leer(conToken(post("/api/v1/panel/vacantes"), tokenTalento, """
                {"solicitudTalentoId": %d, "puestoId": %d,
                 "titulo": "%s", "descripcion": "La vacante de la prueba",
                 "tipoCierre": "PERMANENTE", "responsableUsuarioId": 1}"""
                .formatted(solicitudId, puestoId, titulo))
                .andReturn().getResponse().getContentAsString(), "id"));
    }

    private static String preguntas(long vacante) {
        return "/api/v1/panel/vacantes/" + vacante + "/preguntas-propias";
    }

    private static String prueba(long vacante) {
        return "/api/v1/panel/vacantes/" + vacante + "/prueba-propia";
    }

    private List<JsonNode> filas(String tipo, String token) throws Exception {
        return lista(INDICE + "?tipo=" + tipo, token);
    }

    private List<JsonNode> lista(String ruta, String token) throws Exception {
        JsonNode arreglo = json.readTree(conTokenGet(ruta, token).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString());
        List<JsonNode> salida = new ArrayList<>();
        arreglo.forEach(salida::add);
        return salida;
    }

    private static List<Long> ids(List<JsonNode> filas) {
        return filas.stream().map(f -> f.get("vacanteId").asLong()).toList();
    }

    private static JsonNode fila(List<JsonNode> filas, long vacanteId) {
        return filas.stream().filter(f -> f.get("vacanteId").asLong() == vacanteId).findFirst()
                .orElseThrow(() -> new AssertionError("La vacante " + vacanteId + " no está: " + filas));
    }

    private ResultActions conToken(MockHttpServletRequestBuilder peticion, String token, String cuerpo)
            throws Exception {
        peticion.header("Authorization", "Bearer " + token).contentType(MediaType.APPLICATION_JSON);
        if (cuerpo != null) peticion.content(cuerpo);
        return mvc.perform(peticion);
    }

    private ResultActions conTokenGet(String ruta, String token) throws Exception {
        return mvc.perform(get(ruta).header("Authorization", "Bearer " + token));
    }

    private String leer(String cuerpoRespuesta, String campo) throws Exception {
        JsonNode nodo = json.readTree(cuerpoRespuesta).get(campo);
        assertThat(nodo).as("campo %s en %s", campo, cuerpoRespuesta).isNotNull();
        return nodo.asText();
    }
}
