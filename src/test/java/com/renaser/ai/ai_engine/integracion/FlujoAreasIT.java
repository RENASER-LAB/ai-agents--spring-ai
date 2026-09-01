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
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.rabbitmq.RabbitMQContainer;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Las áreas contra Postgres de verdad: la red que hay debajo del guardián.
 *
 * <p>Las pruebas con dobles ({@code AreasDeLaOrganizacionTest}) comprueban que el servicio corta
 * antes de llegar al borrado, que es lo que hace que nadie vea un error crudo de clave ajena. Lo
 * que no pueden ver es <b>la clave ajena misma</b>: con dobles no hay base que se niegue. Esto sí,
 * y por eso existe: si alguien mueve, afloja o borra ese guardián, aquí tiene que seguir
 * apareciendo un rechazo — el día que la base deje de negarse, un borrado descuidado convierte
 * solicitudes de candidatos reales en filas huérfanas.
 *
 * <p>Los dos hechos que fija, y que son el motivo de todo el diseño de esta función:
 *
 * <ul>
 *   <li>{@code solicitud_talento.area_id} es NOT NULL y {@code usuario.area_id} admite NULL, pero
 *       <b>ninguna de las dos declara {@code ON DELETE}</b>: Postgres aplica NO ACTION y rechaza
 *       el borrado por las dos. Que la nullable también estorbe es lo contraintuitivo, y por eso
 *       tiene prueba propia.
 *   <li>La reasignación y el borrado van en la misma transacción, y acaban en el orden correcto:
 *       primero los UPDATE, después el DELETE. <b>Lo que sostiene ese orden NO es el
 *       {@code flush()} del servicio</b> — se probó quitándolo y esta clase siguió en verde,
 *       porque Hibernate ya vuelca los UPDATE antes que los DELETE. Quien impide el error de
 *       clave ajena es la guarda que rechaza el borrado mientras quede algo por mover; el flush
 *       es explicitud, no la red.
 * </ul>
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@Testcontainers
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@DisplayName("Las áreas contra la base de verdad")
public class FlujoAreasIT {

    @Container
    @ServiceConnection
    static PostgreSQLContainer postgres = new PostgreSQLContainer("pgvector/pgvector:pg16");

    @Container
    @ServiceConnection
    static RabbitMQContainer rabbit = new RabbitMQContainer(ImagenesDeContenedores.RABBITMQ);

    @DynamicPropertySource
    static void propiedades(DynamicPropertyRegistry registro) {
        // Mismo bloque que el resto de los flujos: el broker del contenedor habla en claro, y
        // sin esto manda lo que cada uno tenga en su application-secrets.yaml.
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
    /** Tiene una solicitud Y una persona: el caso completo. */
    static long areaConTodo;
    /** Solo una persona, ninguna solicitud: la clave ajena que admite NULL, sola. */
    static long areaSoloGente;
    /** La que recibe lo que se mueve. */
    static long areaDestino;
    static long solicitudId;
    static long usuarioEnAreaConTodo;

    @Test
    @Order(1)
    @DisplayName("Se preparan tres áreas, con una solicitud y dos personas repartidas")
    void sePreparaElEscenario() throws Exception {
        token = leer(mvc.perform(post("/api/v1/panel/auth/dev-login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"usuarioRenaserOsId\":\"dev-areas\"}"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(), "token");

        areaConTodo = crearArea("Operaciones");
        areaSoloGente = crearArea("Logística de campo");
        areaDestino = crearArea("Operaciones y Logística");

        // El puesto nace antes que la solicitud, y el servidor deriva su código del nombre.
        long puestoId = Long.parseLong(leer(conToken(post("/api/v1/panel/puestos"), """
                {"nombre": "Coordinador de operaciones",
                 "nivelPuestoCodigo": "SUPERVISION", "familiaCodigo": "OPERACIONES"}""")
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString(), "id"));

        solicitudId = Long.parseLong(leer(conToken(post("/api/v1/panel/solicitudes"), """
                {"areaId": %d, "puestoId": %d, "urgencia": "NORMAL",
                 "nivelPuestoCodigo": "SUPERVISION", "familiaCodigo": "OPERACIONES",
                 "resultadoPrincipal": "Sostener la operación diaria",
                 "motivo": "El área no llega a los plazos",
                 "consecuenciaNoContratar": "Se retrasa la operación",
                 "analisisCapacidad": "Se evaluó redistribuir y no alcanza",
                 "responsableUsuarioId": 1,
                 "resultadosEsperados": [
                   {"descripcion": "Cerrar el mes a tiempo", "indicador": "sin retrasos"},
                   {"descripcion": "Reducir incidencias", "indicador": "la mitad"},
                   {"descripcion": "Documentar el proceso", "indicador": "manual al día"}
                 ]}""".formatted(areaConTodo, puestoId))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString(), "id"));

        usuarioEnAreaConTodo = crearUsuario("ana@ejemplo.test", areaConTodo);
        crearUsuario("beto@ejemplo.test", areaSoloGente);

        // El escenario, comprobado contra la base y no supuesto: lo que sigue depende de
        // estos números y de nada más.
        assertThat(cuantasSolicitudesEn(areaConTodo)).isEqualTo(1);
        assertThat(cuantosUsuariosEn(areaConTodo)).isEqualTo(1);
        assertThat(cuantasSolicitudesEn(areaSoloGente)).isZero();
        assertThat(cuantosUsuariosEn(areaSoloGente)).isEqualTo(1);
    }

    @Test
    @Order(2)
    @DisplayName("El impacto cuenta lo que hay de verdad, antes de tocar nada")
    void elImpactoCuentaLoQueHay() throws Exception {
        conTokenGet("/api/v1/panel/areas/" + areaConTodo + "/impacto")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nombre").value("Operaciones"))
                .andExpect(jsonPath("$.solicitudes").value(1))
                .andExpect(jsonPath("$.usuarios").value(1));
    }

    @Test
    @Order(3)
    @DisplayName("Sin área de destino y con cosas colgando: 409 con los dos recuentos, y el área sigue")
    void sinDestinoNoSeBorraYSeExplica() throws Exception {
        conToken(post("/api/v1/panel/areas/" + areaConTodo + "/borrado"),
                "{\"areaDestinoId\": null, \"motivo\": \"sobra\"}")
                .andExpect(status().isConflict())
                // Los números van dentro del mensaje porque son la respuesta a «¿y ahora qué
                // hago?». Un error de clave ajena en la cara diría menos, y además parecería
                // una avería del sistema en vez de una decisión que falta tomar.
                .andExpect(jsonPath("$.detail").value(containsString("Operaciones")))
                .andExpect(jsonPath("$.detail").value(containsString("1 solicitud(es)")))
                .andExpect(jsonPath("$.detail").value(containsString("1 persona(s)")));

        assertThat(existeArea(areaConTodo))
                .as("un borrado rechazado no deja nada a medias: el área sigue entera")
                .isTrue();
        assertThat(cuantasSolicitudesEn(areaConTodo)).isEqualTo(1);
        assertThat(cuantosUsuariosEn(areaConTodo)).isEqualTo(1);
    }

    @Test
    @Order(4)
    @DisplayName("Basta UNA persona para bloquearlo, aunque esa columna admita nulo")
    void unaPersonaSolaTambienLoBloquea() throws Exception {
        // Este es el caso contraintuitivo, y el que justifica que el borrado pida destino
        // siempre: `usuario.area_id` admite NULL, así que parece que no estorba. Estorba,
        // porque lo que decide no es si la columna acepta nulo sino que su clave ajena no
        // declara ON DELETE.
        conToken(post("/api/v1/panel/areas/" + areaSoloGente + "/borrado"),
                "{\"areaDestinoId\": null, \"motivo\": \"sobra\"}")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value(containsString("0 solicitud(es)")))
                .andExpect(jsonPath("$.detail").value(containsString("1 persona(s)")));

        assertThat(existeArea(areaSoloGente)).isTrue();
    }

    @Test
    @Order(5)
    @DisplayName("La red de debajo: sin pasar por el servicio, la base se niega igual")
    void laBaseSeNiegaAunqueNadieLaGuarde() {
        /*
         * Lo anterior comprueba el guardián; esto comprueba que hay algo debajo de él. Se
         * borra por SQL directo, saltándose el servicio entero, y la base tiene que rechazarlo
         * por las dos claves ajenas.
         *
         * El día que alguien mueva la guarda de sitio, la afloje o la borre, este caso sigue en
         * verde y sigue habiendo red: lo que NO puede pasar es que estas dos restricciones se
         * relajen sin que nadie se entere, porque entonces un borrado descuidado deja
         * solicitudes de candidatos reales apuntando a un área que ya no existe.
         */
        assertThatThrownBy(() -> jdbc.update("DELETE FROM area WHERE id = ?", areaConTodo))
                .as("solicitud_talento.area_id es NOT NULL y su clave ajena no declara ON DELETE")
                .isInstanceOf(DataIntegrityViolationException.class);

        assertThatThrownBy(() -> jdbc.update("DELETE FROM area WHERE id = ?", areaSoloGente))
                .as("usuario.area_id admite NULL, y aun así su clave ajena es NO ACTION")
                .isInstanceOf(DataIntegrityViolationException.class);

        assertThat(existeArea(areaConTodo)).isTrue();
        assertThat(existeArea(areaSoloGente)).isTrue();
    }

    @Test
    @Order(6)
    @DisplayName("Retirar la saca de la lista de elegir, y solo la de todas la puede recuperar")
    void retirarNoEsUnViajeSinRetorno() throws Exception {
        conToken(post("/api/v1/panel/areas/" + areaSoloGente + "/desactivacion"), null)
                .andExpect(status().isOk());

        // La lista que llena el desplegable de la solicitud deja de ofrecerla...
        conTokenGet("/api/v1/panel/areas")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.nombre == 'Logística de campo')]").isEmpty());

        // ...y la de todas es la ÚNICA que sigue sabiendo que existe. Sin ella, retirar un
        // área sería perderla: desaparecería de la única pantalla que hay.
        conTokenGet("/api/v1/panel/areas/todas")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.nombre == 'Logística de campo')].esActiva").value(false));

        // Y nada de lo que colgaba se movió: retirar no es borrar.
        assertThat(cuantosUsuariosEn(areaSoloGente)).isEqualTo(1);

        conToken(post("/api/v1/panel/areas/" + areaSoloGente + "/reactivacion"), null)
                .andExpect(status().isOk());
        conTokenGet("/api/v1/panel/areas")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.nombre == 'Logística de campo')]").isNotEmpty());
    }

    @Test
    @Order(7)
    @DisplayName("Renombrar no puede pisar a otra área")
    void elNombreRepetidoSeRechaza() throws Exception {
        conToken(put("/api/v1/panel/areas/" + areaSoloGente), "{\"nombre\": \"Operaciones\"}")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value(containsString("Ya existe un área")));

        // El UNIQUE (organizacion_id, nombre) de la V2 nunca llegó a saltar: si llegara, el
        // mensaje traería el nombre de una restricción dentro.
        assertThat(nombreDe(areaSoloGente)).isEqualTo("Logística de campo");
    }

    @Test
    @Order(8)
    @DisplayName("Con destino: la solicitud y la persona acaban en la otra área, y la vieja desaparece")
    void elBorradoConReasignacionMueveTodo() throws Exception {
        conToken(post("/api/v1/panel/areas/" + areaConTodo + "/borrado"),
                "{\"areaDestinoId\": %d, \"motivo\": \"se fusionan las dos áreas\"}"
                        .formatted(areaDestino))
                .andExpect(status().isOk());

        /*
         * Que esto pase significa que los UPDATE bajaron antes que el DELETE dentro de la misma
         * transacción. Dicho con precisión, porque es fácil atribuírselo a quien no toca: el
         * orden lo pone Hibernate, que vuelca los UPDATE antes que los DELETE por tipo de
         * operación. Se comprobó quitando los tres `flush()` del servicio y volviendo a correr
         * esta clase: siguió en verde. El flush documenta el orden, no lo crea.
         *
         * Lo que sí se rompería aquí es cambiar la reasignación por un borrado directo o por un
         * `@Modifying` en lote sin cuidar el orden: entonces esta comprobación se pondría roja
         * con un error de integridad, que es exactamente para lo que sirve tenerla.
         */
        assertThat(existeArea(areaConTodo))
                .as("el área borrada ya no está")
                .isFalse();

        Long areaDeLaSolicitud = jdbc.queryForObject(
                "SELECT area_id FROM solicitud_talento WHERE id = ?", Long.class, solicitudId);
        assertThat(areaDeLaSolicitud)
                .as("la solicitud no se pierde ni se queda huérfana: cambia de área")
                .isEqualTo(areaDestino);

        Long areaDeLaPersona = jdbc.queryForObject(
                "SELECT area_id FROM usuario WHERE id = ?", Long.class, usuarioEnAreaConTodo);
        assertThat(areaDeLaPersona)
                .as("y la persona tampoco se queda sin área: vaciar la columna contentaría a "
                        + "Postgres y perdería el dato")
                .isEqualTo(areaDestino);
    }

    @Test
    @Order(9)
    @DisplayName("El borrado deja escrito el nombre perdido y los dos recuentos")
    void laAuditoriaGuardaLoQueYaNoSePuedeReconstruir() throws Exception {
        // Después del DELETE, la fila del área no existe y las solicitudes guardan el id, no el
        // texto: esta fila de auditoría es lo único que puede contestar de dónde venían.
        String valorAnterior = jdbc.queryForObject(
                "SELECT valor_anterior FROM auditoria WHERE accion = 'borrar_area' "
                        + "AND entidad_id = ? ORDER BY id DESC LIMIT 1",
                String.class, areaConTodo);

        /*
         * ⚠️ Se compara el JSON YA LEÍDO, no su texto. La columna es `jsonb`, y jsonb no guarda
         * lo que se le escribió: normaliza los espacios y reordena las claves a su gusto. Este
         * caso empezó buscando la subcadena «"solicitudes":1» y falló contra un valor
         * perfectamente correcto —Postgres lo había guardado como «"solicitudes": 1», con
         * espacio, y con las claves en otro orden—. Buscar subcadenas dentro de un jsonb es
         * comprobar el formateador, no el dato.
         */
        JsonNode anterior = json.readTree(valorAnterior);
        assertThat(anterior.get("nombre").asText())
                .as("el nombre del área ya no existe en ninguna otra parte de la base")
                .isEqualTo("Operaciones");
        assertThat(anterior.get("solicitudes").asLong()).isEqualTo(1);
        assertThat(anterior.get("usuarios").asLong()).isEqualTo(1);

        String motivo = jdbc.queryForObject(
                "SELECT motivo FROM auditoria WHERE accion = 'borrar_area' AND entidad_id = ? "
                        + "ORDER BY id DESC LIMIT 1",
                String.class, areaConTodo);
        assertThat(motivo).isEqualTo("se fusionan las dos áreas");
    }

    @Test
    @Order(10)
    @DisplayName("Un área vacía sí se borra sin destino, que es el caso de la creada por error")
    void laVaciaSeBorraSinDestino() throws Exception {
        long sobrante = crearArea("Área creada por error");

        conToken(post("/api/v1/panel/areas/" + sobrante + "/borrado"),
                "{\"areaDestinoId\": null, \"motivo\": \"se creó dos veces\"}")
                .andExpect(status().isOk());

        assertThat(existeArea(sobrante)).isFalse();
    }

    /* =====================================================================================
     * Revisión adversarial (31/08/2026). Nada de lo que sigue repite los diez casos de
     * arriba: todos apuntan al mismo hueco visto desde sitios distintos —el guardián cuenta
     * filtrando por organización y la clave ajena no filtra nada— más la falta de la guarda
     * del «último» que este mismo servicio sí aplica a los administradores.
     * ===================================================================================== */

    @Test
    @Order(11)
    @DisplayName("El impacto tiene que ver TODO lo que apunta al área, no solo lo de su empresa")
    void elImpactoNoPuedeContarMenosDeLoQueHay() throws Exception {
        long area = crearArea("Área con un inquilino de otra empresa");
        long ajena = crearOrganizacionAjena("QA-AJENA-1");
        insertarSolicitudDe(ajena, area);

        /*
         * `impactoDeBorrar` cuenta con `countByOrganizacionIdAndAreaId(quien.organizacionId(), …)`
         * y la clave ajena `solicitud_talento.area_id -> area(id)` no sabe nada de
         * organizaciones. Una fila que la clave ajena SÍ ve y el recuento NO es exactamente
         * la que convierte «se borra sin mover nada» en un error de integridad.
         */
        conTokenGet("/api/v1/panel/areas/" + area + "/impacto")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.solicitudes").value(1));
    }

    @Test
    @Order(12)
    @DisplayName("Un área que parece vacía y no lo está: 409 explicado, nunca un error crudo de la base")
    void elBorradoNuncaSaleConUnErrorCrudoDeIntegridad() throws Exception {
        long area = crearArea("Área que parece vacía");
        long ajena = crearOrganizacionAjena("QA-AJENA-2");
        insertarSolicitudDe(ajena, area);

        /*
         * La promesa escrita en CLAUDE.MD es literal: «responde 409 con los dos recuentos
         * dentro, NUNCA un error crudo de clave ajena». Aquí la guarda ve cero, deja pasar,
         * y el DELETE choca contra la clave ajena: `ManejadorErrores` lo traduce a un 400
         * «Hay un dato que no cuadra / Alguno de los datos enviados no es válido», que ni es
         * el código correcto ni dice nada de lo que pasa.
         */
        conToken(post("/api/v1/panel/areas/" + area + "/borrado"),
                "{\"areaDestinoId\": null, \"motivo\": \"parece que no cuelga nada\"}")
                .andExpect(status().isConflict());

        assertThat(existeArea(area))
                .as("un borrado rechazado no deja nada a medias")
                .isTrue();
    }

    @Test
    @Order(13)
    @DisplayName("Reasignar tiene que mover TODO lo que apunta al área, o el borrado no puede seguir")
    void reasignarNoPuedeDejarseFilasDetras() throws Exception {
        long area = crearArea("Área que se fusiona");
        long destino = crearArea("Área que recibe la fusión");
        long ajena = crearOrganizacionAjena("QA-AJENA-3");
        long solicitudAjena = insertarSolicitudDe(ajena, area);

        /*
         * Con destino, el bucle recorre `findByOrganizacionIdAndAreaId(...)`: la solicitud de
         * la otra empresa no sale de ahí, así que no se mueve, y el DELETE la encuentra la
         * clave ajena. Si algún día se decide moverla igualmente, este caso avisa de lo otro:
         * mover trabajo de una empresa a un área de otra sería peor que el error.
         */
        conToken(post("/api/v1/panel/areas/" + area + "/borrado"),
                "{\"areaDestinoId\": %d, \"motivo\": \"se fusionan\"}".formatted(destino))
                .andExpect(status().isConflict());

        Long dondeQuedo = jdbc.queryForObject(
                "SELECT area_id FROM solicitud_talento WHERE id = ?", Long.class, solicitudAjena);
        assertThat(dondeQuedo)
                .as("lo de la otra empresa jamás puede acabar en un área de esta")
                .isNotEqualTo(destino);
    }

    @Test
    @Order(14)
    @DisplayName("Registrar una solicitud con el área de OTRA empresa no puede admitirse")
    void elAreaAjenaNoSirveParaRegistrarUnaSolicitud() throws Exception {
        long ajena = crearOrganizacionAjena("QA-AJENA-4");
        long areaAjena = insertarArea(ajena, "Finanzas de la otra empresa");

        /*
         * Este es el camino por el que la fila invisible de los tres casos de arriba llega a
         * existir sin tocar la base a mano: `ServicioSolicitudesImpl.crear` valida el puesto
         * con `findByIdAndOrganizacionId` y mete `datos.areaId()` tal cual, sin mirarlo. La
         * clave ajena lo acepta porque solo exige que el área exista.
         */
        long puestoId = Long.parseLong(leer(conToken(post("/api/v1/panel/puestos"), """
                {"nombre": "Analista de la otra empresa",
                 "nivelPuestoCodigo": "SUPERVISION", "familiaCodigo": "OPERACIONES"}""")
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString(), "id"));

        conToken(post("/api/v1/panel/solicitudes"), """
                {"areaId": %d, "puestoId": %d, "urgencia": "NORMAL",
                 "nivelPuestoCodigo": "SUPERVISION", "familiaCodigo": "OPERACIONES",
                 "resultadoPrincipal": "Da igual", "motivo": "Da igual",
                 "consecuenciaNoContratar": "Da igual", "analisisCapacidad": "Da igual",
                 "resultadosEsperados": [
                   {"descripcion": "Uno", "indicador": "uno"},
                   {"descripcion": "Dos", "indicador": "dos"},
                   {"descripcion": "Tres", "indicador": "tres"}
                 ]}""".formatted(areaAjena, puestoId))
                .andExpect(status().isNotFound());

        assertThat(cuantasSolicitudesEn(areaAjena))
                .as("una solicitud de esta empresa colgada de un área de otra")
                .isZero();
    }

    @Test
    @Order(15)
    @DisplayName("Dar de alta a alguien en el área de OTRA empresa no puede admitirse")
    void elAreaAjenaNoSirveParaDarDeAltaAAlguien() throws Exception {
        long ajena = crearOrganizacionAjena("QA-AJENA-5");
        long areaAjena = insertarArea(ajena, "Sistemas de la otra empresa");

        // `crearUsuarioEquipo` tampoco mira el área: `.areaId(datos.areaId())` y a la base.
        conToken(post("/api/v1/panel/usuarios"), """
                {"nombre": "Alguien", "apellidos": "Del Equipo", "correo": "ajeno@ejemplo.test",
                 "areaId": %d, "roles": ["TALENTO"]}""".formatted(areaAjena))
                .andExpect(status().isNotFound());

        assertThat(cuantosUsuariosEn(areaAjena)).isZero();
    }

    @Test
    @Order(16)
    @DisplayName("Retirar la última área activa dejaría a la empresa sin poder registrar solicitudes")
    void noSePuedeRetirarLaUltimaAreaActiva() throws Exception {
        /*
         * Sin ningún área activa, `GET /areas` devuelve la lista vacía, el desplegable de la
         * solicitud se queda sin nada que ofrecer y nadie puede registrar una: la empresa
         * queda parada en el primer paso del proceso entero. Se deshace desde `/areas/todas`,
         * pero solo si quien lo mira sabe que esa pantalla existe.
         *
         * Este servicio ya conoce la forma de la guarda que falta: en `asignarRoles` está
         * «No se puede quitar el último administrador del sistema». Aquí no está.
         */
        List<Long> activas = jdbc.queryForList(
                "SELECT id FROM area WHERE organizacion_id = ? AND es_activa ORDER BY id",
                Long.class, organizacionDelToken());
        assertThat(activas).as("el escenario necesita al menos un área activa").isNotEmpty();

        try {
            for (int i = 0; i < activas.size() - 1; i++) {
                conToken(post("/api/v1/panel/areas/" + activas.get(i) + "/desactivacion"), null)
                        .andExpect(status().isOk());
            }
            conToken(post("/api/v1/panel/areas/" + activas.getLast() + "/desactivacion"), null)
                    .andExpect(status().isConflict());
        } finally {
            for (Long id : activas) {
                conToken(post("/api/v1/panel/areas/" + id + "/reactivacion"), null);
            }
        }
    }

    // ---------- Ayudas ----------

    private long organizacionDelToken() {
        return jdbc.queryForObject(
                "SELECT organizacion_id FROM area WHERE id = ?", Long.class, areaDestino);
    }

    private long crearOrganizacionAjena(String codigo) {
        return jdbc.queryForObject(
                "INSERT INTO organizacion (codigo, nombre) VALUES (?, ?) RETURNING id",
                Long.class, codigo, "Empresa " + codigo);
    }

    private long insertarArea(long organizacionId, String nombre) {
        return jdbc.queryForObject(
                "INSERT INTO area (organizacion_id, nombre, es_activa) VALUES (?, ?, true) "
                        + "RETURNING id",
                Long.class, organizacionId, nombre);
    }

    /**
     * Una solicitud de OTRA empresa colgada de esta área, puesta a mano.
     *
     * <p>A mano y no por el endpoint porque el endpoint necesitaría el token de la otra
     * empresa; el estado que deja en la base es el mismo que produce el agujero que comprueba
     * {@link #elAreaAjenaNoSirveParaRegistrarUnaSolicitud()}.
     */
    private long insertarSolicitudDe(long organizacionId, long areaId) {
        return jdbc.queryForObject("""
                INSERT INTO solicitud_talento
                    (organizacion_id, origen, urgencia, estado, area_id,
                     resultado_principal, motivo, consecuencia_no_contratar, analisis_capacidad)
                VALUES (?, 'DIRECTA', 'NORMAL', 'BORRADOR', ?, 'x', 'x', 'x', 'x')
                RETURNING id""", Long.class, organizacionId, areaId);
    }

    private long crearArea(String nombre) throws Exception {
        return Long.parseLong(leer(conToken(post("/api/v1/panel/areas"),
                "{\"nombre\": \"%s\"}".formatted(nombre))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString(), "id"));
    }

    private long crearUsuario(String correo, long areaId) throws Exception {
        return Long.parseLong(leer(conToken(post("/api/v1/panel/usuarios"), """
                {"nombre": "Alguien", "apellidos": "Del Equipo", "correo": "%s",
                 "areaId": %d, "roles": ["TALENTO"]}""".formatted(correo, areaId))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString(), "id"));
    }

    private boolean existeArea(long areaId) {
        Integer cuantas = jdbc.queryForObject(
                "SELECT count(*) FROM area WHERE id = ?", Integer.class, areaId);
        return cuantas != null && cuantas > 0;
    }

    private String nombreDe(long areaId) {
        return jdbc.queryForObject("SELECT nombre FROM area WHERE id = ?", String.class, areaId);
    }

    private int cuantasSolicitudesEn(long areaId) {
        Integer n = jdbc.queryForObject(
                "SELECT count(*) FROM solicitud_talento WHERE area_id = ?", Integer.class, areaId);
        return n == null ? 0 : n;
    }

    private int cuantosUsuariosEn(long areaId) {
        Integer n = jdbc.queryForObject(
                "SELECT count(*) FROM usuario WHERE area_id = ?", Integer.class, areaId);
        return n == null ? 0 : n;
    }

    private ResultActions conToken(MockHttpServletRequestBuilder peticion, String cuerpo)
            throws Exception {
        peticion.header("Authorization", "Bearer " + token);
        if (cuerpo != null) {
            peticion.contentType(MediaType.APPLICATION_JSON).content(cuerpo);
        }
        return mvc.perform(peticion);
    }

    private ResultActions conTokenGet(String ruta) throws Exception {
        return mvc.perform(get(ruta).header("Authorization", "Bearer " + token));
    }

    private String leer(String cuerpoRespuesta, String campo) throws Exception {
        JsonNode nodo = json.readTree(cuerpoRespuesta).get(campo);
        assertThat(nodo).as("campo %s en %s", campo, cuerpoRespuesta).isNotNull();
        return nodo.asText();
    }
}
