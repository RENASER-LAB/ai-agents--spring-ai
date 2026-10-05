package com.renaser.ai.ai_engine.integracion;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.renaser.ai.ai_engine.comun.programado.SondeoVencimientos;
import com.renaser.ai.ai_engine.integracion.soporte.ImagenesDeContenedores;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.DisplayName;
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

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Las etapas 4 y 5: simulación de trabajo y validación práctica.
 *
 * <p>Lo que más se prueba aquí son <b>las tres reglas de disponibilidad</b>, porque son el único
 * punto del sistema donde el estado de una postulación se mueve por lo que pasa en otra tabla —
 * y donde un fallo no produce ningún error: el candidato simplemente se queda parado y nadie se
 * entera hasta que alguien mira la bandeja.
 *
 * <p>También se prueban las dos reglas que el cliente puso por escrito: que faltar a la sesión
 * <b>no</b> reinscribe solo, y que no se puede poner a alguien a trabajar de verdad sin figura
 * contractual registrada.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@Testcontainers
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@DisplayName("Simulación de trabajo y validación práctica")
public class FlujoSimulacionValidacionIT {

    @Container
    @ServiceConnection
    static PostgreSQLContainer postgres = new PostgreSQLContainer("pgvector/pgvector:pg16");

    @Container
    @ServiceConnection
    static RabbitMQContainer rabbit = new RabbitMQContainer(ImagenesDeContenedores.RABBITMQ);

    @DynamicPropertySource
    static void propiedades(DynamicPropertyRegistry registro) {
        // El broker de las pruebas es el contenedor, y habla en claro. Sin esto manda lo
        // que cada uno tenga en su application-secrets.yaml —hoy, un CloudAMQP con TLS— y
        // la tanda entera falla según la máquina en la que corra, que es lo peor que le
        // puede pasar a una prueba.
        registro.add("spring.rabbitmq.ssl.enabled", () -> "false");
        registro.add("spring.rabbitmq.virtual-host", () -> "/");
        // El almacen de las pruebas vive en un mapa, no en disco: no hay ningun
        // sitio donde un curriculum pueda quedarse olvidado despues de correrlas.
        registro.add("app.archivos.tipo", () -> "memoria");
        registro.add("app.seguridad.jwt-secreto",
                () -> "clave-de-pruebas-suficientemente-larga-para-hmac-256-bits");
        // El dev-login quedo apagado por defecto en application.yaml: aqui se enciende
        // explicitamente, porque estas pruebas entran al panel por el.
        registro.add("app.seguridad.dev-login-activo", () -> "true");
        registro.add("spring.ai.deepseek.api-key", () -> "clave-de-pruebas-no-se-usa");
    }

    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired SondeoVencimientos sondeo;
    final ObjectMapper json = new ObjectMapper();

    static String tokenTalento;
    static long vacanteId;
    // Dos candidatos: uno se queda con la única plaza, el otro se queda fuera
    static long postulacionA;
    static long postulacionB;
    static String tokenA;
    static String tokenB;
    static String codigoA;
    static String codigoB;
    static long sesionId;
    static long inscripcionA;
    // La sesión del @Order(7), que se queda con B dentro: la reaprovecha el @Order(11)
    static long sesionConInscrito;

    @DisplayName("Dos candidatos llegan a esperar una sesión")
    @Test
    @Order(1)
    void dosCandidatosLleganAEsperarSesion() throws Exception {
        tokenTalento = leer(mvc.perform(post("/api/v1/panel/auth/dev-login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"usuarioRenaserOsId\":\"dev-sim\"}"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(), "token");

        vacanteId = prepararVacante();

        tokenA = crearCandidato("ana@correo.pe");
        tokenB = crearCandidato("bruno@correo.pe");
        codigoA = postular(tokenA);
        codigoB = postular(tokenB);
        postulacionA = idDe(codigoA);
        postulacionB = idDe(codigoB);

        // Los dos van a mano hasta SIMULACION_POR_HABILITAR: el camino previo ya está
        // probado en los otros tests, aquí interesa lo que pasa a partir de aquí.
        llevarASimulacion(postulacionA);
        llevarASimulacion(postulacionB);

        assertThat(estadoDe(postulacionA)).isEqualTo("SIMULACION_POR_HABILITAR");
        assertThat(estadoDe(postulacionB)).isEqualTo("SIMULACION_POR_HABILITAR");
    }

    @DisplayName("Publicar una sesión mueve a quien estaba esperando")
    @Test
    @Order(2)
    void publicarUnaSesionMueveAQuienEstabaEsperando() throws Exception {
        // Regla 1: publicar una sesión con cupo mueve a los que esperaban.
        // Cupo 1 a propósito: es lo que hace visible la regla 2 en el test siguiente.
        String cuerpo = """
                {"fechaHora":"%s","duracionMinutos":120,"modalidad":"GRUPAL",
                 "lugar":"Sala 2","cupo":1,"enunciado":"Organiza la jornada de un equipo de soporte",
                 "vacanteIds":[%d]}""".formatted(Instant.now().plus(3, ChronoUnit.DAYS), vacanteId);
        sesionId = Long.parseLong(leer(conToken(post("/api/v1/panel/sesiones-simulacion"), tokenTalento, cuerpo)
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString(), "id"));

        assertThat(estadoDe(postulacionA)).isEqualTo("SIMULACION_TURNO_CANDIDATO");
        assertThat(estadoDe(postulacionB)).isEqualTo("SIMULACION_TURNO_CANDIDATO");

        // Los tramos se copian del reparto por defecto al crear la sesión
        JsonNode sesion = json.readTree(
                conTokenGet("/api/v1/panel/sesiones-simulacion/" + sesionId, tokenTalento)
                        .andReturn().getResponse().getContentAsString());
        assertThat(sesion.get("tramos")).hasSize(6);
        assertThat(sesion.get("tramos").get(0).get("codigo").asText()).isEqualTo("CONTEXTO");
    }

    @DisplayName("Llenar el cupo devuelve a quien no alcanzó plaza")
    @Test
    @Order(3)
    void llenarElCupoDevuelveAQuienNoAlcanzoPlaza() throws Exception {
        // El candidato A alcanza la única plaza
        inscripcionA = Long.parseLong(leer(mvc.perform(
                        post("/api/v1/portal/simulacion/" + codigoA + "/sesiones/" + sesionId)
                                .header("Authorization", "Bearer " + tokenA))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(), "inscripcionId"));

        // Regla 2: se llenó la última sesión, así que B -que no se inscribió- vuelve a esperar.
        // Sin esta regla se quedaría intentando elegir una fecha que ya no existe.
        assertThat(estadoDe(postulacionA)).isEqualTo("SIMULACION_TURNO_CANDIDATO");
        assertThat(estadoDe(postulacionB)).isEqualTo("SIMULACION_POR_HABILITAR");

        // Y B ya no ve ninguna fecha disponible
        conTokenGet("/api/v1/portal/simulacion/" + codigoB + "/sesiones", tokenB)
                .andExpect(jsonPath("$.length()").value(0));
    }

    @DisplayName("Cancelar la sesión devuelve a los inscritos y conserva su historial")
    @Test
    @Order(4)
    void cancelarLaSesionDevuelveALosInscritosYConservaSuHistorial() throws Exception {
        conToken(post("/api/v1/panel/sesiones-simulacion/" + sesionId + "/cancelacion"), tokenTalento,
                "{\"motivo\":\"El facilitador no puede ese día\"}")
                .andExpect(status().isOk());

        // Regla 3: sin ninguna otra sesión, los dos vuelven a esperar
        assertThat(estadoDe(postulacionA)).isEqualTo("SIMULACION_POR_HABILITAR");
        assertThat(estadoDe(postulacionB)).isEqualTo("SIMULACION_POR_HABILITAR");

        // La inscripción vieja no se borra: queda como no vigente. Es lo que evita que
        // parezca que esa persona nunca eligió fecha.
        Map<String, Object> vieja = jdbc.queryForMap(
                "select es_vigente from inscripcion_sesion where id = ?", inscripcionA);
        assertThat(vieja.get("es_vigente")).isEqualTo(false);

        // Y se le avisó
        Integer avisos = jdbc.queryForObject(
                "select count(*) from correo_enviado where plantilla_correo_codigo = 'SESION_CANCELADA'",
                Integer.class);
        assertThat(avisos).isEqualTo(1);
    }

    @DisplayName("Faltar a la sesión no reinscribe solo")
    @Test
    @Order(5)
    void faltarALaSesionNoReinscribeSolo() throws Exception {
        // Una sesión nueva, ahora con cupo para los dos
        long otra = crearSesion(2);
        assertThat(estadoDe(postulacionA)).isEqualTo("SIMULACION_TURNO_CANDIDATO");

        long inscripcion = Long.parseLong(leer(mvc.perform(
                        post("/api/v1/portal/simulacion/" + codigoA + "/sesiones/" + otra)
                                .header("Authorization", "Bearer " + tokenA))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(), "inscripcionId"));

        // No asistió: vuelve a la bandeja del equipo, NO se le da otra fecha solo.
        conToken(post("/api/v1/panel/inscripciones/" + inscripcion + "/asistencia"), tokenTalento,
                "{\"asistio\":false}").andExpect(status().isOk());

        assertThat(estadoDe(postulacionA)).isEqualTo("SIMULACION_POR_HABILITAR");
        Map<String, Object> fila = jdbc.queryForMap(
                "select asistio, es_vigente from inscripcion_sesion where id = ?", inscripcion);
        assertThat(fila.get("asistio")).isEqualTo(false);
        assertThat(fila.get("es_vigente")).isEqualTo(false);

        // Una persona decide: otra fecha. Como hay sesión con cupo, vuelve a poder elegir.
        conToken(post("/api/v1/panel/postulaciones/" + postulacionA + "/ausencia-simulacion"), tokenTalento,
                "{\"decision\":\"OTRA_FECHA\",\"motivo\":\"Avisó que se le cruzó una urgencia\"}")
                .andExpect(status().isOk());
        assertThat(estadoDe(postulacionA)).isEqualTo("SIMULACION_TURNO_CANDIDATO");
    }

    @DisplayName("El facilitador marca los eventos observables")
    @Test
    @Order(6)
    void elFacilitadorMarcaLosEventosObservables() throws Exception {
        long sesion = crearSesion(2);
        long inscripcion = Long.parseLong(leer(mvc.perform(
                        post("/api/v1/portal/simulacion/" + codigoA + "/sesiones/" + sesion)
                                .header("Authorization", "Bearer " + tokenA))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(), "inscripcionId"));

        for (String evento : List.of("INICIO", "PRIMERA_PREGUNTA", "APARECE_CAMBIO", "ABRE_CAMBIO", "ENTREGA")) {
            conToken(post("/api/v1/panel/inscripciones/" + inscripcion + "/marcas"), tokenTalento,
                    "{\"evento\":\"%s\"}".formatted(evento)).andExpect(status().isOk());
        }
        conTokenGet("/api/v1/panel/inscripciones/" + inscripcion + "/marcas", tokenTalento)
                .andExpect(jsonPath("$.length()").value(5));

        // Un evento que no está entre los diez observables no pasa. Lo que se quiso registrar
        // alguna vez -«se dio cuenta del bloqueo»- ya no existe: solo actos, nunca intenciones.
        conToken(post("/api/v1/panel/inscripciones/" + inscripcion + "/marcas"), tokenTalento,
                "{\"evento\":\"DETECTO_EL_BLOQUEO\"}").andExpect(status().isBadRequest());

        // Marcar dos veces el mismo evento corrige la hora, no duplica
        conToken(post("/api/v1/panel/inscripciones/" + inscripcion + "/marcas"), tokenTalento,
                "{\"evento\":\"ENTREGA\"}").andExpect(status().isOk());
        conTokenGet("/api/v1/panel/inscripciones/" + inscripcion + "/marcas", tokenTalento)
                .andExpect(jsonPath("$.length()").value(5));

        conToken(post("/api/v1/panel/inscripciones/" + inscripcion + "/asistencia"), tokenTalento,
                "{\"asistio\":true}").andExpect(status().isOk());
        assertThat(estadoDe(postulacionA)).isEqualTo("SIMULACION_POR_CONFIRMAR");
    }

    @DisplayName("Quien no tiene rol de facilitador no puede marcar")
    @Test
    @Order(7)
    void quienNoTieneUnRolDeFacilitadorNoPuedeMarcar() throws Exception {
        long sesion = sesionConInscrito = crearSesion(2);
        long inscripcion = Long.parseLong(leer(mvc.perform(
                        post("/api/v1/portal/simulacion/" + codigoB + "/sesiones/" + sesion)
                                .header("Authorization", "Bearer " + tokenB))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(), "inscripcionId"));

        // El responsable de área tiene el permiso, pero su rol no está en la lista de
        // facilitadores que dice el parámetro. Aquí es donde se ve que «quién facilita» es
        // configuración y no código.
        String tokenArea = crearUsuarioConRol("Rosa", "Lima", "rosa.area@renaser.pe",
                "os-rosa-area", "RESPONSABLE_AREA");
        jdbc.update("update vacante set responsable_usuario_id = "
                + "(select id from usuario where usuario_renaser_os_id = 'os-rosa-area') where id = ?", vacanteId);

        conToken(post("/api/v1/panel/inscripciones/" + inscripcion + "/marcas"), tokenArea,
                "{\"evento\":\"INICIO\"}").andExpect(status().isForbidden());

        // Y una inscripción que no existe le contesta lo mismo, no un 404: el rol se comprueba
        // antes de ir a buscarla. Si no, la diferencia entre las dos respuestas le iría diciendo
        // qué ids hay dentro, sin necesidad de poder marcar nada.
        conToken(post("/api/v1/panel/inscripciones/999999/marcas"), tokenArea,
                "{\"evento\":\"INICIO\"}").andExpect(status().isForbidden());

        // Se añade su rol al parámetro -desde el panel, sin desplegar nada- y ahora sí puede
        conToken(put("/api/v1/panel/parametros/roles_facilitador_simulacion"), tokenTalento,
                "{\"valor\":\"TALENTO,DIRECCION,RESPONSABLE_AREA\",\"motivo\":\"Rosa conduce las sesiones de su área\"}")
                .andExpect(status().isOk());

        conToken(post("/api/v1/panel/inscripciones/" + inscripcion + "/marcas"), tokenArea,
                "{\"evento\":\"INICIO\"}").andExpect(status().isOk());
    }

    @DisplayName("Se califica la simulación y se pasa a validación")
    @Test
    @Order(8)
    void seCalificaLaSimulacionYSePasaAValidacion() throws Exception {
        // El máximo de un criterio global no vive en criterio.puntos sino en peso_criterio,
        // porque pesa distinto según el nivel del puesto. El endpoint ya lo resuelve; aquí se
        // lee igual para poner la nota máxima de cada uno.
        List<Map<String, Object>> criterios = jdbc.queryForList("""
                select c.id, pc.peso from criterio c
                join peso_criterio pc on pc.criterio_id = c.id
                join vacante v on v.version_pesos_id = pc.version_pesos_id
                join puesto pu on pu.id = v.puesto_id and pu.nivel_puesto_codigo = pc.nivel_puesto_codigo
                where c.etapa_codigo = 'SIMULACION' and v.id = ?
                order by c.orden""", vacanteId);
        assertThat(criterios).hasSize(10);

        // Sin todas las notas no se puede cerrar: media rúbrica no es una nota
        conToken(post("/api/v1/panel/postulaciones/" + postulacionA + "/simulacion/calificacion"),
                tokenTalento, null).andExpect(status().isConflict());

        for (Map<String, Object> c : criterios) {
            double maximo = ((Number) c.get("peso")).doubleValue();
            conToken(post("/api/v1/panel/postulaciones/" + postulacionA + "/simulacion/criterios/"
                            + c.get("id") + "/nota"), tokenTalento,
                    "{\"puntaje\":%s,\"explicacion\":\"Observado durante la sesión\"}".formatted(maximo))
                    .andExpect(status().isOk());
        }

        String cuerpo = conToken(post("/api/v1/panel/postulaciones/" + postulacionA + "/simulacion/calificacion"),
                        tokenTalento, null)
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        assertThat(json.readTree(cuerpo).get("nota").asDouble()).isEqualTo(100.0);

        // Y de aquí a validación, sin saltos
        conToken(post("/api/v1/panel/postulaciones/" + postulacionA + "/confirmacion-avance"), tokenTalento,
                "{\"motivo\":\"Simulación calificada\"}").andExpect(status().isOk());
        assertThat(estadoDe(postulacionA)).isEqualTo("VALIDACION_POR_HABILITAR");
    }

    @DisplayName("No se pone a nadie a trabajar sin figura contractual")
    @Test
    @Order(9)
    void noSePonAAlguienATrabajarSinFiguraContractual() throws Exception {
        // La regla legal: trabajo real exige figura contractual registrada
        conToken(post("/api/v1/panel/postulaciones/" + postulacionA + "/validacion/habilitacion"), tokenTalento,
                "{\"modalidad\":\"TRABAJO_REAL\"}")
                .andExpect(status().isBadRequest());

        // La otra modalidad no la necesita: se puede usar desde el primer día
        conToken(post("/api/v1/panel/postulaciones/" + postulacionA + "/validacion/habilitacion"), tokenTalento,
                "{\"modalidad\":\"SIMULACION_EXTENDIDA\",\"dias\":5}")
                .andExpect(status().isOk());

        conToken(post("/api/v1/panel/postulaciones/" + postulacionA + "/validacion/inicio"), tokenTalento, null)
                .andExpect(status().isOk());
        assertThat(estadoDe(postulacionA)).isEqualTo("VALIDACION_TURNO_CANDIDATO");

        Map<String, Object> v = jdbc.queryForMap(
                "select estado, dias, inicio_en, fin_en from validacion where postulacion_id = ?", postulacionA);
        assertThat(v.get("estado")).isEqualTo("EN_CURSO");
        assertThat(v.get("dias")).isEqualTo(5);
        assertThat(v.get("fin_en")).isNotNull();
    }

    @DisplayName("El periodo vencido termina solo y se completan las métricas")
    @Test
    @Order(10)
    void elPeriodoVencidoTerminaSoloYSeCompletanLasMetricas() throws Exception {
        // Se fuerza el vencimiento y corre el sondeo
        jdbc.update("update validacion set fin_en = now() - interval '1 hour' where postulacion_id = ?",
                postulacionA);
        sondeo.ejecutar();

        // Terminar el periodo no cierra la postulación: la pasa a esperar a una persona
        assertThat(estadoDe(postulacionA)).isEqualTo("VALIDACION_POR_CONFIRMAR");

        List<Map<String, Object>> metricas = jdbc.queryForList("""
                select c.id, pc.peso from criterio c
                join peso_criterio pc on pc.criterio_id = c.id
                join vacante v on v.version_pesos_id = pc.version_pesos_id
                join puesto pu on pu.id = v.puesto_id and pu.nivel_puesto_codigo = pc.nivel_puesto_codigo
                where c.etapa_codigo = 'VALIDACION' and v.id = ?
                order by c.orden""", vacanteId);
        assertThat(metricas).hasSize(9);

        for (Map<String, Object> m : metricas) {
            double maximo = ((Number) m.get("peso")).doubleValue();
            conToken(post("/api/v1/panel/postulaciones/" + postulacionA + "/validacion/metricas/" + m.get("id")),
                    tokenTalento,
                    "{\"puntaje\":%s,\"explicacion\":\"Observado durante el periodo\"}".formatted(maximo))
                    .andExpect(status().isOk());
        }

        // De cada valor queda registrado de dónde salió (RF-111)
        conTokenGet("/api/v1/panel/postulaciones/" + postulacionA + "/validacion/metricas", tokenTalento)
                .andExpect(jsonPath("$[0].origen").value("PERSONA"));

        conToken(post("/api/v1/panel/postulaciones/" + postulacionA + "/validacion/cierre"), tokenTalento, null)
                .andExpect(status().isOk());
        assertThat(estadoDe(postulacionA)).isEqualTo("DECISION_POR_CONFIRMAR");

        // Quedaron calificadas las dos etapas que este test recorre de verdad. Perfil Integral
        // y prueba se saltaron con transiciones directas -su camino ya está probado en los
        // otros dos tests-, así que no tienen nota, y el semáforo lo dice en vez de
        // inventarse una nota global con la mitad de la evidencia.
        List<String> calificadas = jdbc.queryForList(
                "select etapa_codigo from nota_etapa where postulacion_id = ? order by etapa_codigo",
                String.class, postulacionA);
        assertThat(calificadas).containsExactly("SIMULACION", "VALIDACION");

        JsonNode semaforo = json.readTree(
                conTokenGet("/api/v1/panel/postulaciones/" + postulacionA + "/semaforo", tokenTalento)
                        .andReturn().getResponse().getContentAsString());
        assertThat(semaforo.get("etapasQueFaltan").toString())
                .contains("PERFIL_INTEGRAL").contains("PRUEBA_PUESTO");
    }

    /**
     * Quién eligió cada fecha, y quién puede verlo.
     *
     * <p>Lo que se recorre aquí no es la lista: es que <b>quién la ve se cambia desde el
     * panel</b>. El reparto de {@code rol_permiso} se relee en cada petición, así que quitarle
     * el permiso a un rol se nota en su siguiente llamada — sin desplegar y sin que nadie
     * tenga que volver a entrar. Es el mismo argumento que el parámetro de facilitadores del
     * {@code @Order(7)}, aplicado a los permisos.
     */
    @DisplayName("Quién eligió la fecha se ve, y quién puede verlo se cambia sin desplegar")
    @Test
    @Order(11)
    void losInscritosSeVenYElPermisoSeEditaEnCaliente() throws Exception {
        // Talento tiene alcance TODO: ve al inscrito con nombre y, sobre todo, con la
        // inscripcionId, que es lo que piden las marcas y la asistencia. Antes de esto no
        // había forma de llegar a ella desde el panel.
        JsonNode inscritos = json.readTree(conTokenGet(
                "/api/v1/panel/sesiones-simulacion/" + sesionConInscrito + "/inscritos", tokenTalento)
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString());
        assertThat(inscritos).hasSize(1);
        assertThat(inscritos.get(0).get("candidato").asText()).isEqualTo("Candidata De Prueba");
        assertThat(inscritos.get(0).get("inscripcionId").asLong()).isPositive();
        assertThat(inscritos.get(0).get("asistio").isNull())
                .as("nadie ha marcado nada todavía, que no es lo mismo que «no vino»").isTrue();

        // Rosa es responsable de área -y de esta vacante, desde el @Order(7)-, así que la
        // semilla le da SUS_VACANTES: ve a los suyos.
        String tokenArea = leer(mvc.perform(post("/api/v1/panel/auth/dev-login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"usuarioRenaserOsId\":\"os-rosa-area\"}"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(), "token");
        conTokenGet("/api/v1/panel/sesiones-simulacion/" + sesionConInscrito + "/inscritos", tokenArea)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1));

        // Y llega hasta ahí por su cuenta: la lista de sesiones también le abre, aunque no
        // pueda crearlas. Sin esto tendría los inscritos de una sesión cuyo id no hay forma de
        // averiguar. Sale recortada a las sesiones que tocan una vacante suya.
        JsonNode sesionesDeRosa = json.readTree(conTokenGet(
                "/api/v1/panel/sesiones-simulacion", tokenArea)
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString());
        assertThat(sesionesDeRosa).isNotEmpty();
        // Positivo y no «no nulo»: asLong() devuelve un primitivo, así que nunca sería nulo y
        // la comprobación no diría nada. Un campo que faltara en el JSON saldría como cero.
        assertThat(sesionesDeRosa).allSatisfy(s ->
                assertThat(s.get("id").asLong()).isPositive());

        // Las tres cifras de la misma sesión concuerdan: la de la lista, la del detalle y las
        // filas de /inscritos. Aquí solo hay un inscrito, así que esto NO distingue un conteo
        // recortado de uno sin recortar —eso lo sujeta el unitario, que comprueba con qué
        // consulta se cuenta—. Lo que sí hace, y por eso está aquí, es ejecutar la consulta
        // nueva contra PostgreSQL de verdad: que la JPQL con sus dos saltos sea válida no se
        // ve con dobles.
        long enLaLista = -1;
        for (JsonNode s : sesionesDeRosa) {
            if (s.get("id").asLong() == sesionConInscrito) {
                enLaLista = s.get("inscritos").asLong();
            }
        }
        assertThat(enLaLista).as("la sesión con su inscrito tiene que estar en su lista").isOne();
        conTokenGet("/api/v1/panel/sesiones-simulacion/" + sesionConInscrito, tokenArea)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.inscritos").value(1));

        // Y una sesión que no toca ninguna vacante suya no se abre: 404, no 403, que
        // confirmaría que existe.
        long sesionAjena = jdbc.queryForObject(
                "insert into sesion_simulacion (organizacion_id, fecha_hora, duracion_minutos, "
                        + "modalidad, cupo, estado, creada_por_usuario_id, creado_en) values "
                        + "(1, now() + interval '30 days', 120, 'GRUPAL', 4, 'PUBLICADA', 1, now()) "
                        + "returning id", Long.class);
        conTokenGet("/api/v1/panel/sesiones-simulacion/" + sesionAjena, tokenArea)
                .andExpect(status().isNotFound());

        // Y ahora se le quita, desde el panel. Ni un despliegue ni un token nuevo: el mismo
        // de arriba deja de servir para esto en la siguiente llamada.
        String tokenAdmin = crearUsuarioConRol("Ada", "Vera", "ada.admin@renaser.pe",
                "os-ada-admin", "ADMINISTRADOR");
        long rolArea = jdbc.queryForObject(
                "select id from rol where codigo = 'RESPONSABLE_AREA'", Long.class);
        conToken(post("/api/v1/panel/roles/" + rolArea + "/permisos/ver_inscritos_simulacion/revocacion"),
                tokenAdmin, "{\"motivo\":\"Los nombres los lleva Talento\"}")
                .andExpect(status().isOk());

        conTokenGet("/api/v1/panel/sesiones-simulacion/" + sesionConInscrito + "/inscritos", tokenArea)
                .andExpect(status().isForbidden());

        // Se le devuelve, con otro alcance, y vuelve a ver
        conToken(put("/api/v1/panel/roles/" + rolArea + "/permisos/ver_inscritos_simulacion"),
                tokenAdmin, "{\"alcance\":\"TODO\",\"motivo\":\"Rosa conduce las sesiones\"}")
                .andExpect(status().isOk());
        conTokenGet("/api/v1/panel/sesiones-simulacion/" + sesionConInscrito + "/inscritos", tokenArea)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1));

        // El cambio no es anónimo: queda quién lo hizo y por qué escrito a mano.
        List<Map<String, Object>> rastro = jdbc.queryForList(
                "select accion, motivo from auditoria where accion in "
                        + "('conceder_permiso','revocar_permiso') and entidad_id = ? order by id",
                rolArea);
        assertThat(rastro).hasSize(2);
        assertThat(rastro.get(0).get("motivo")).isEqualTo("Los nombres los lleva Talento");
    }

    // ============ Volver a entrar en Validación (y en la prueba) ============
    //
    // Cada una con su postulación propia, y después del recorrido de arriba: lo que aquí se
    // mueve no toca a A ni a B, ni los conteos que el @Order(11) da por hechos.

    /**
     * Retrocedida y vuelta a avanzar con su periodo en curso. Antes, el segundo «Avanzar»
     * chocaba con la clave única de la V18 y la persona se quedaba en Simulación.
     */
    @DisplayName("Volver con el periodo en curso entra a su turno con el mismo periodo, y al vencer pasa a por confirmar")
    @Test
    @Order(12)
    void volverConElPeriodoEnCursoEntraASuTurno() throws Exception {
        Candidata c = nuevaCandidata("carla@correo.pe");
        llevarAValidacionEIniciar(c.id());
        Map<String, Object> periodoAntes = periodoDe(c.id());
        assertThat(periodoAntes.get("estado")).isEqualTo("EN_CURSO");

        moverAMano(c.id(), "SIMULACION_POR_CONFIRMAR", "Retrocede para revisar su simulación")
                .andExpect(status().isOk());
        avanzar(c.id(), "Vuelve tras revisar su simulación").andExpect(status().isOk());

        assertThat(estadoDe(c.id())).isEqualTo("VALIDACION_TURNO_CANDIDATO");
        // El mismo periodo, fila idéntica: ni el id, ni las fechas, ni lo habilitado cambian.
        assertThat(periodoDe(c.id())).isEqualTo(periodoAntes);
        assertThat(periodosDe(c.id())).isOne();
        Map<String, Object> ultima = ultimaTransicion(c.id());
        assertThat(ultima.get("estado_anterior_codigo")).isEqualTo("SIMULACION_POR_CONFIRMAR");
        assertThat(ultima.get("estado_nuevo_codigo")).isEqualTo("VALIDACION_TURNO_CANDIDATO");
        assertThat(ultima.get("motivo")).isEqualTo(
                "Vuelve tras revisar su simulación · su periodo de validación ya estaba en curso");

        // El reloj siguió corriendo: al vencer, el sondeo la trata como a cualquier otra.
        jdbc.update("update validacion set fin_en = now() - interval '1 hour' where postulacion_id = ?",
                c.id());
        sondeo.ejecutar();
        assertThat(estadoDe(c.id())).isEqualTo("VALIDACION_POR_CONFIRMAR");
    }

    @DisplayName("Volver con el periodo cerrado entra a por confirmar con sus métricas, y cerrar recalcula con ellas")
    @Test
    @Order(13)
    void volverConElPeriodoCerradoConservaLasMetricas() throws Exception {
        Candidata d = nuevaCandidata("dora@correo.pe");
        llevarAValidacionEIniciar(d.id());
        jdbc.update("update validacion set fin_en = now() - interval '1 hour' where postulacion_id = ?",
                d.id());
        sondeo.ejecutar();
        assertThat(estadoDe(d.id())).isEqualTo("VALIDACION_POR_CONFIRMAR");
        completarMetricasYCerrar(d.id());
        assertThat(estadoDe(d.id())).isEqualTo("DECISION_POR_CONFIRMAR");

        Map<String, Object> periodoAntes = periodoDe(d.id());
        assertThat(periodoAntes.get("estado")).isEqualTo("TERMINADA");
        String metricasAntes = conTokenGet(
                "/api/v1/panel/postulaciones/" + d.id() + "/validacion/metricas", tokenTalento)
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        Object notaAntes = notaDeValidacion(d.id());

        moverAMano(d.id(), "SIMULACION_POR_CONFIRMAR", "Retrocede para repasar el proceso")
                .andExpect(status().isOk());
        avanzar(d.id(), "Vuelve para revisar su validación").andExpect(status().isOk());

        assertThat(estadoDe(d.id())).isEqualTo("VALIDACION_POR_CONFIRMAR");
        assertThat(periodoDe(d.id())).isEqualTo(periodoAntes);
        assertThat(periodosDe(d.id())).isOne();
        assertThat(ultimaTransicion(d.id()).get("motivo")).isEqualTo(
                "Vuelve para revisar su validación · su periodo de validación ya estaba cerrado");
        conTokenGet("/api/v1/panel/postulaciones/" + d.id() + "/validacion/metricas", tokenTalento)
                .andExpect(status().isOk())
                .andExpect(content().json(metricasAntes));

        // Cerrar otra vez: la nota sale de las mismas métricas y pasa a la decisión.
        conToken(post("/api/v1/panel/postulaciones/" + d.id() + "/validacion/cierre"), tokenTalento, null)
                .andExpect(status().isOk());
        assertThat(estadoDe(d.id())).isEqualTo("DECISION_POR_CONFIRMAR");
        assertThat(notaDeValidacion(d.id())).isEqualTo(notaAntes);
    }

    /**
     * Un movimiento manual a Validación no creaba el periodo: la ficha decía «todavía no hay»
     * y habilitar, iniciar, métricas y cerrar contestaban 404.
     */
    @DisplayName("Mover a mano a Validación sin periodo lo crea, e iniciar lo pone en curso sin moverla")
    @Test
    @Order(14)
    void moverAManoAValidacionCreaElPeriodo() throws Exception {
        Candidata e = nuevaCandidata("elena@correo.pe");
        llevarAPorConfirmarPrueba(e.id());

        moverAMano(e.id(), "VALIDACION_TURNO_CANDIDATO", "Pasa directo a su periodo")
                .andExpect(status().isOk());
        assertThat(estadoDe(e.id())).isEqualTo("VALIDACION_TURNO_CANDIDATO");
        assertThat(periodoDe(e.id()).get("estado")).isEqualTo("POR_HABILITAR");

        conToken(post("/api/v1/panel/postulaciones/" + e.id() + "/validacion/habilitacion"), tokenTalento,
                "{\"modalidad\":\"SIMULACION_EXTENDIDA\",\"dias\":5}").andExpect(status().isOk());
        conToken(post("/api/v1/panel/postulaciones/" + e.id() + "/validacion/inicio"), tokenTalento, null)
                .andExpect(status().isOk());
        assertThat(periodoDe(e.id()).get("estado")).isEqualTo("EN_CURSO");
        assertThat(estadoDe(e.id())).isEqualTo("VALIDACION_TURNO_CANDIDATO");

        // Y a «por confirmar»: tiene periodo, así que verlo y sus métricas ya no dan 404.
        Candidata f = nuevaCandidata("fabiola@correo.pe");
        llevarAPorConfirmarPrueba(f.id());
        moverAMano(f.id(), "VALIDACION_POR_CONFIRMAR", "Ya la observamos en el área")
                .andExpect(status().isOk());
        assertThat(estadoDe(f.id())).isEqualTo("VALIDACION_POR_CONFIRMAR");
        conTokenGet("/api/v1/panel/postulaciones/" + f.id() + "/validacion", tokenTalento)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("POR_HABILITAR"));
        completarMetricasYCerrar(f.id());
        assertThat(estadoDe(f.id())).isEqualTo("DECISION_POR_CONFIRMAR");
    }

    /**
     * Mover a mano a la prueba mandaba «tu prueba está disponible» sin crearla: el candidato
     * abría el enlace y encontraba la pantalla vacía. Ahora se crea, y sin prueba lista el
     * movimiento se rechaza como «Avanzar».
     */
    @DisplayName("Mover a mano a la prueba crea el intento; sin prueba lista da 409 y no se mueve")
    @Test
    @Order(15)
    void moverAManoALaPruebaCreaElIntento() throws Exception {
        Candidata g = nuevaCandidata("gabriel@correo.pe");
        for (String destino : List.of("PERFIL_CALIFICANDO", "PERFIL_POR_CONFIRMAR")) {
            moverAMano(g.id(), destino, "Avance de prueba").andExpect(status().isOk());
        }
        Long version = jdbc.queryForObject(
                "select version_plantilla_prueba_id from vacante where id = ?", Long.class, vacanteId);
        int transicionesAntes = transicionesDe(g.id());
        int correosAntes = correosDe(g.id());

        // Sin prueba lista: la vacante pierde su plantilla un momento y la recupera al final.
        try {
            jdbc.update("update vacante set version_plantilla_prueba_id = null where id = ?", vacanteId);
            moverAMano(g.id(), "PRUEBA_TURNO_CANDIDATO", "Pasa a la prueba")
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.detail").value(
                            "Esta vacante no tiene plantilla de prueba asignada: no se puede avanzar"));
        } finally {
            jdbc.update("update vacante set version_plantilla_prueba_id = ? where id = ?", version, vacanteId);
        }
        assertThat(estadoDe(g.id())).isEqualTo("PERFIL_POR_CONFIRMAR");
        assertThat(transicionesDe(g.id())).isEqualTo(transicionesAntes);
        assertThat(correosDe(g.id())).isEqualTo(correosAntes);
        assertThat(jdbc.queryForObject("select count(*) from intento_prueba where postulacion_id = ?",
                Integer.class, g.id())).isZero();

        // Con la prueba lista: se crea su intento, y el enlace del correo abre esa prueba.
        moverAMano(g.id(), "PRUEBA_TURNO_CANDIDATO", "Pasa a la prueba").andExpect(status().isOk());
        assertThat(estadoDe(g.id())).isEqualTo("PRUEBA_TURNO_CANDIDATO");
        assertThat(jdbc.queryForObject(
                "select version_plantilla_prueba_id from intento_prueba where postulacion_id = ?",
                Long.class, g.id())).isEqualTo(version);
        mvc.perform(get("/api/v1/portal/prueba/" + g.codigo())
                        .header("Authorization", "Bearer " + g.token()))
                .andExpect(status().isOk());
    }

    // ============ Apoyo ============

    /** Una candidata nueva con su postulación a la vacante de este flujo. */
    private record Candidata(String token, String codigo, long id) {}

    private Candidata nuevaCandidata(String correo) throws Exception {
        String token = crearCandidato(correo);
        String codigo = postular(token);
        return new Candidata(token, codigo, idDe(codigo));
    }

    /** A mano hasta la prueba ya calificada, sin pasar por la simulación. */
    private void llevarAPorConfirmarPrueba(long postulacionId) throws Exception {
        for (String destino : List.of("PERFIL_CALIFICANDO", "PERFIL_POR_CONFIRMAR",
                "PRUEBA_TURNO_CANDIDATO", "PRUEBA_CALIFICANDO", "PRUEBA_POR_CONFIRMAR")) {
            moverAMano(postulacionId, destino, "Avance de prueba").andExpect(status().isOk());
        }
    }

    /**
     * A Validación con «Avanzar», habilitada e iniciada: su periodo queda en curso. Llega a
     * «Simulación · por confirmar» a mano para no recalcular las sesiones de A y B.
     */
    private void llevarAValidacionEIniciar(long postulacionId) throws Exception {
        llevarAPorConfirmarPrueba(postulacionId);
        moverAMano(postulacionId, "SIMULACION_POR_CONFIRMAR", "Simulación observada")
                .andExpect(status().isOk());
        avanzar(postulacionId, "Simulación calificada").andExpect(status().isOk());
        assertThat(estadoDe(postulacionId)).isEqualTo("VALIDACION_POR_HABILITAR");
        conToken(post("/api/v1/panel/postulaciones/" + postulacionId + "/validacion/habilitacion"),
                tokenTalento, "{\"modalidad\":\"SIMULACION_EXTENDIDA\",\"dias\":5}")
                .andExpect(status().isOk());
        conToken(post("/api/v1/panel/postulaciones/" + postulacionId + "/validacion/inicio"),
                tokenTalento, null).andExpect(status().isOk());
        assertThat(estadoDe(postulacionId)).isEqualTo("VALIDACION_TURNO_CANDIDATO");
    }

    private void completarMetricasYCerrar(long postulacionId) throws Exception {
        List<Map<String, Object>> metricas = jdbc.queryForList("""
                select c.id, pc.peso from criterio c
                join peso_criterio pc on pc.criterio_id = c.id
                join vacante v on v.version_pesos_id = pc.version_pesos_id
                join puesto pu on pu.id = v.puesto_id and pu.nivel_puesto_codigo = pc.nivel_puesto_codigo
                where c.etapa_codigo = 'VALIDACION' and v.id = ?
                order by c.orden""", vacanteId);
        for (Map<String, Object> m : metricas) {
            // Algo por debajo del máximo, para que la nota no sea la de una rúbrica perfecta.
            double puntaje = ((Number) m.get("peso")).doubleValue() / 2;
            conToken(post("/api/v1/panel/postulaciones/" + postulacionId + "/validacion/metricas/"
                            + m.get("id")), tokenTalento,
                    "{\"puntaje\":%s,\"explicacion\":\"Observado durante el periodo\"}".formatted(puntaje))
                    .andExpect(status().isOk());
        }
        conToken(post("/api/v1/panel/postulaciones/" + postulacionId + "/validacion/cierre"),
                tokenTalento, null).andExpect(status().isOk());
    }

    private ResultActions moverAMano(long postulacionId, String destino, String motivo) throws Exception {
        return conToken(post("/api/v1/panel/postulaciones/" + postulacionId + "/transiciones"),
                tokenTalento, "{\"estadoDestino\":\"%s\",\"motivo\":\"%s\"}".formatted(destino, motivo));
    }

    private ResultActions avanzar(long postulacionId, String motivo) throws Exception {
        return conToken(post("/api/v1/panel/postulaciones/" + postulacionId + "/confirmacion-avance"),
                tokenTalento, "{\"motivo\":\"%s\"}".formatted(motivo));
    }

    private Map<String, Object> periodoDe(long postulacionId) {
        return jdbc.queryForMap("select * from validacion where postulacion_id = ?", postulacionId);
    }

    private int periodosDe(long postulacionId) {
        return jdbc.queryForObject("select count(*) from validacion where postulacion_id = ?",
                Integer.class, postulacionId);
    }

    private Map<String, Object> ultimaTransicion(long postulacionId) {
        return jdbc.queryForMap("""
                select estado_anterior_codigo, estado_nuevo_codigo, motivo from transicion_estado
                where postulacion_id = ? order by id desc limit 1""", postulacionId);
    }

    private int transicionesDe(long postulacionId) {
        return jdbc.queryForObject("select count(*) from transicion_estado where postulacion_id = ?",
                Integer.class, postulacionId);
    }

    private int correosDe(long postulacionId) {
        return jdbc.queryForObject("""
                select count(*) from correo_enviado
                where usuario_id = (select usuario_id from postulacion where id = ?)""",
                Integer.class, postulacionId);
    }

    private Object notaDeValidacion(long postulacionId) {
        return jdbc.queryForObject("""
                select puntaje from nota_etapa where postulacion_id = ? and etapa_codigo = 'VALIDACION'""",
                Object.class, postulacionId);
    }

    private long crearSesion(int cupo) throws Exception {
        String cuerpo = """
                {"fechaHora":"%s","duracionMinutos":120,"modalidad":"GRUPAL",
                 "lugar":"Sala 2","cupo":%d,"enunciado":"Ejercicio de la sesión","vacanteIds":[%d]}"""
                .formatted(Instant.now().plus(5, ChronoUnit.DAYS), cupo, vacanteId);
        return Long.parseLong(leer(conToken(post("/api/v1/panel/sesiones-simulacion"), tokenTalento, cuerpo)
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString(), "id"));
    }

    /** Lleva una postulación desde POSTULADA hasta SIMULACION_POR_HABILITAR. */
    private void llevarASimulacion(long postulacionId) throws Exception {
        for (String destino : List.of("PERFIL_CALIFICANDO", "PERFIL_POR_CONFIRMAR",
                "PRUEBA_TURNO_CANDIDATO", "PRUEBA_CALIFICANDO", "PRUEBA_POR_CONFIRMAR")) {
            conToken(post("/api/v1/panel/postulaciones/" + postulacionId + "/transiciones"), tokenTalento,
                    "{\"estadoDestino\":\"%s\",\"motivo\":\"Avance de prueba\"}".formatted(destino))
                    .andExpect(status().isOk());
        }
        conToken(post("/api/v1/panel/postulaciones/" + postulacionId + "/confirmacion-avance"), tokenTalento,
                "{\"motivo\":\"Pasa a la simulación\"}").andExpect(status().isOk());
    }

    private String estadoDe(long postulacionId) {
        return jdbc.queryForObject("select estado_codigo from postulacion where id = ?",
                String.class, postulacionId);
    }

    private long idDe(String codigo) {
        return jdbc.queryForObject("select id from postulacion where uuid = ?::uuid", Long.class, codigo);
    }

    private String postular(String token) throws Exception {
        var cv = new org.springframework.mock.web.MockMultipartFile("cv", "cv.pdf",
                "application/pdf", "contenido".getBytes());
        return leer(mvc.perform(multipart("/api/v1/portal/postulaciones")
                        .file(cv)
                        .param("vacanteId", String.valueOf(vacanteId))
                        .param("resultadoOrgulloso", "Reduje a la mitad el tiempo de respuesta")
                        .param("aceptaTratamiento", "true")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString(), "codigo");
    }

    private long prepararVacante() throws Exception {
        jdbc.update("INSERT INTO area (organizacion_id, nombre, es_activa) VALUES (1, 'Soporte', true)");
        Long areaId = jdbc.queryForObject("SELECT id FROM area ORDER BY id DESC LIMIT 1", Long.class);
        long puestoId = Long.parseLong(leer(conToken(post("/api/v1/panel/puestos"), tokenTalento, """
                {"codigo": "SOPORTE_L1", "nombre": "Soporte nivel 1",
                 "nivelPuestoCodigo": "EJECUCION", "familiaCodigo": "OPERACIONES"}""")
                .andReturn().getResponse().getContentAsString(), "id"));

        long solicitudId = Long.parseLong(leer(conToken(post("/api/v1/panel/solicitudes"), tokenTalento, """
                {"areaId": %d, "puestoId": %d, "urgencia": "NORMAL",
                 "nivelPuestoCodigo": "EJECUCION", "familiaCodigo": "OPERACIONES",
                 "resultadoPrincipal": "Sostener la atención de soporte",
                 "motivo": "El equipo no cubre los turnos",
                 "consecuenciaNoContratar": "Se acumulan los tickets",
                 "analisisCapacidad": "Se evaluó redistribuir y no alcanza",
                 "responsableUsuarioId": 1,
                 "resultadosEsperados": [
                   {"descripcion": "Bajar el tiempo de respuesta", "indicador": "menos de 2 horas"},
                   {"descripcion": "Cubrir los turnos", "indicador": "sin huecos"},
                   {"descripcion": "Documentar casos", "indicador": "base al día"}
                 ]}""".formatted(areaId, puestoId))
                .andReturn().getResponse().getContentAsString(), "id"));

        conToken(post("/api/v1/panel/solicitudes/" + solicitudId + "/aprobacion"), tokenTalento,
                "{\"motivo\":\"Hay presupuesto\"}").andExpect(status().isOk());

        long id = Long.parseLong(leer(conToken(post("/api/v1/panel/vacantes"), tokenTalento, """
                {"solicitudTalentoId": %d, "puestoId": %d,
                 "titulo": "Soporte nivel 1", "descripcion": "Atención a clientes",
                 "tipoCierre": "PERMANENTE", "responsableUsuarioId": 1}"""
                .formatted(solicitudId, puestoId))
                .andReturn().getResponse().getContentAsString(), "id"));
        // Rinde el banco del nivel: desde el 30/09/2026 toda vacante nueva nace con sus
        // preguntas propias, y el banco se elige a mano.
        conToken(post("/api/v1/panel/vacantes/" + id + "/origen-preguntas"), tokenTalento,
                "{\"origen\":\"NIVEL\"}").andExpect(status().isOk());

        Long plantillaEvaluacionId = jdbc.queryForObject(
                "select id from plantilla_evaluacion where nivel_puesto_codigo = 'EJECUCION'", Long.class);
        conToken(post("/api/v1/panel/vacantes/" + id + "/plantilla-evaluacion"), tokenTalento,
                "{\"plantillaEvaluacionId\": %d}".formatted(plantillaEvaluacionId)).andExpect(status().isOk());
        conToken(post("/api/v1/panel/vacantes/" + id + "/plantilla-prueba"), tokenTalento,
                "{\"versionPlantillaPruebaId\": %d}".formatted(armarUnaPruebaValida())).andExpect(status().isOk());
        conToken(post("/api/v1/panel/vacantes/" + id + "/publicacion"), tokenTalento, null)
                .andExpect(status().isOk());
        return id;
    }

    private Long armarUnaPruebaValida() throws Exception {
        long plantillaId = Long.parseLong(leer(conToken(post("/api/v1/panel/plantillas-prueba"), tokenTalento,
                "{\"nombre\":\"Prueba de soporte\"}")
                .andReturn().getResponse().getContentAsString(), "id"));
        long versionId = Long.parseLong(leer(conToken(
                post("/api/v1/panel/plantillas-prueba/" + plantillaId + "/versiones"), tokenTalento, """
                {"enunciado":"Resuelve tres tickets","modalidad":"CRONOMETRADA","duracionMinutos":90}""")
                .andReturn().getResponse().getContentAsString(), "id"));

        for (int i = 0; i < 8; i++) {
            long id = Long.parseLong(leer(conToken(post("/api/v1/panel/plantillas-prueba/preguntas"), tokenTalento,
                    "{\"codigo\":\"UNIV_SV_%d\",\"enunciado\":\"Universal %d\",\"tipo\":\"UNIVERSAL\"}"
                            .formatted(i, i))
                    .andReturn().getResponse().getContentAsString(), "id"));
            conToken(post("/api/v1/panel/plantillas-prueba/versiones/" + versionId + "/preguntas"), tokenTalento,
                    "{\"preguntaPruebaId\": %d}".formatted(id)).andExpect(status().isOk());
        }
        for (int i = 0; i < 3; i++) {
            long id = Long.parseLong(leer(conToken(post("/api/v1/panel/plantillas-prueba/preguntas"), tokenTalento,
                    "{\"codigo\":\"ESP_SV_%d\",\"enunciado\":\"Específica %d\",\"tipo\":\"ESPECIFICA\"}"
                            .formatted(i, i))
                    .andReturn().getResponse().getContentAsString(), "id"));
            conToken(post("/api/v1/panel/plantillas-prueba/versiones/" + versionId + "/preguntas"), tokenTalento,
                    "{\"preguntaPruebaId\": %d}".formatted(id)).andExpect(status().isOk());
        }
        conToken(post("/api/v1/panel/plantillas-prueba/versiones/" + versionId + "/rubrica"), tokenTalento, """
                {"codigo":"RESULTADO_SV","nombre":"Resultado","puntos":100,"metodoVerificacion":"PERSONA"}""")
                .andExpect(status().isCreated());
        conToken(post("/api/v1/panel/plantillas-prueba/versiones/" + versionId + "/publicacion"), tokenTalento, null)
                .andExpect(status().isOk());
        return versionId;
    }

    private String crearCandidato(String correo) throws Exception {
        mvc.perform(post("/api/v1/portal/cuentas")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                        {"nombre":"Candidata","apellidos":"De Prueba","correo":"%s",
                         "contrasena":"unaClaveLarga123","ciudadUbigeo":"1501","aceptaPlataforma":true,
                         "aceptaFuturosContactos":false}""".formatted(correo)))
                .andExpect(status().isCreated());
        return leer(mvc.perform(post("/api/v1/portal/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"correo\":\"%s\",\"contrasena\":\"unaClaveLarga123\"}".formatted(correo)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(), "token");
    }

    private String crearUsuarioConRol(String nombre, String apellidos, String correo,
                                      String osId, String rol) throws Exception {
        conToken(post("/api/v1/panel/usuarios"), tokenTalento, """
                {"nombre": "%s", "apellidos": "%s", "correo": "%s",
                 "usuarioRenaserOsId": "%s", "roles": ["%s"]}"""
                .formatted(nombre, apellidos, correo, osId, rol))
                .andExpect(status().isCreated());
        return leer(mvc.perform(post("/api/v1/panel/auth/dev-login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"usuarioRenaserOsId\":\"%s\"}".formatted(osId)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(), "token");
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
