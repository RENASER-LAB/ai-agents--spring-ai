package com.renaser.ai.ai_engine.integracion;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.postgresql.ds.PGSimpleDataSource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * La V70 corrige «desde este correo» en el aviso de la prueba y siembra los recordatorios.
 *
 * <p>Como {@link MigracionPuntosDelCriterioIT}: migra hasta la V69, deja los textos como pueden
 * estar en producción —la v3 de PRUEBA_DISPONIBLE se editó a mano y no está en ninguna
 * migración, y una vacante usa un texto propio para ese aviso— y solo entonces aplica la V70.
 *
 * <p>Lo que se protege (AC-22, AC-19):
 * <ul>
 *   <li>ninguna versión activa de PRUEBA_DISPONIBLE, ni el texto propio de una vacante para ese
 *       aviso, dice «desde este correo»; las anteriores siguen guardadas;</li>
 *   <li>de cada texto solo cambia esa frase: la línea de cómo entrar al portal se queda;</li>
 *   <li>lo que no dice la frase, o no es de ese aviso, no se toca;</li>
 *   <li>las dos plantillas y los tres parámetros de los recordatorios existen en todas las
 *       empresas.</li>
 * </ul>
 */
@Testcontainers
@DisplayName("La V70 corrige el plazo del aviso de la prueba y siembra los recordatorios")
class MigracionPlazoDeLaPruebaIT {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("pgvector/pgvector:pg16");

    /** Como la dejó el script de textos en producción: con la línea de entrar al portal. */
    private static final String V3_DE_PRODUCCION = String.join("\n",
            "Hola {{nombre}}:",
            "",
            "Pasaste a la prueba del puesto para «{{vacante}}». ¡Enhorabuena!",
            "",
            "Tienes {{plazo}} desde este correo.",
            "",
            "Para hacerla, entra en https://renaser.example/ingresar con tu correo y tu contraseña y abre «Mis procesos».",
            "Ahí verás la prueba y podrás subir lo que te pida.",
            "",
            "Equipo de Talento — Renaser");

    @Test
    @DisplayName("solo la frase del plazo cambia, con versión nueva, y los recordatorios llegan a todas")
    void corrigeLaFraseYSiembra() throws SQLException {
        migrarHasta("69");

        long renaser;
        long acme;
        try (Connection c = fuente().getConnection()) {
            renaser = unId(c, "select id from organizacion where codigo = 'RENASER'");
            acme = unId(c, "insert into organizacion (codigo, nombre) values ('ACME_V70', 'Acme') returning id");

            // RENASER: la v1 de la V29 se jubiló a mano y la v3 activa es la de producción.
            ejecutar(c, "update plantilla_correo set es_activa = false "
                    + "where organizacion_id = " + renaser + " and codigo = 'PRUEBA_DISPONIBLE'");
            plantilla(c, renaser, "PRUEBA_DISPONIBLE", 2, "Tu prueba", "una v2 vieja desde este correo", false);
            plantilla(c, renaser, "PRUEBA_DISPONIBLE", 3, "Tu prueba del puesto para {{vacante}}",
                    V3_DE_PRODUCCION, true);

            // El texto propio de una vacante para ese aviso, con otra forma de decirlo.
            plantilla(c, renaser, "PRUEBA_DISPONIBLE_ADMIN", 1, "Tu prueba",
                    "Hola {{nombre}}.\nTienes 7 días Desde este correo para enviarla. Suerte.", true);
            long vacante = vacanteMinima(c, renaser);
            ejecutar(c, "insert into plantilla_correo_vacante (vacante_id, aviso_codigo, plantilla_codigo) "
                    + "values (" + vacante + ", 'PRUEBA_DISPONIBLE', 'PRUEBA_DISPONIBLE_ADMIN')");

            // Acme ya lo tiene bien: no se toca.
            plantilla(c, acme, "PRUEBA_DISPONIBLE", 1, "Tu prueba", "Tienes {{plazo}}.", true);
            // Y un texto de otro aviso que dice la frase tampoco: no es el de la prueba.
            plantilla(c, renaser, "OTRO_AVISO", 1, "Otro", "Respóndenos desde este correo.", true);
        }

        migrarHasta("70");

        try (Connection c = fuente().getConnection()) {
            // AC-22: la activa de RENASER es una v4 corregida, y la v3 sigue guardada tal cual
            assertThat(activas(c, renaser, "PRUEBA_DISPONIBLE")).containsExactly(4);
            String v4 = cuerpo(c, renaser, "PRUEBA_DISPONIBLE", 4);
            assertThat(v4).isEqualTo(V3_DE_PRODUCCION.replace(
                    "Tienes {{plazo}} desde este correo.", "Tienes {{plazo}}."));
            assertThat(cuerpo(c, renaser, "PRUEBA_DISPONIBLE", 3)).isEqualTo(V3_DE_PRODUCCION);
            assertThat(cuerpo(c, renaser, "PRUEBA_DISPONIBLE", 2)).contains("desde este correo");

            // El texto propio de la vacante: la frase entera se cambia por la de referencia
            assertThat(activas(c, renaser, "PRUEBA_DISPONIBLE_ADMIN")).containsExactly(2);
            assertThat(cuerpo(c, renaser, "PRUEBA_DISPONIBLE_ADMIN", 2))
                    .isEqualTo("Hola {{nombre}}.\nTienes {{plazo}}. Suerte.");

            // Lo que no lo decía, o no es de la prueba, no se toca
            assertThat(activas(c, acme, "PRUEBA_DISPONIBLE")).containsExactly(1);
            assertThat(activas(c, renaser, "OTRO_AVISO")).containsExactly(1);

            // Ninguna activa del aviso de la prueba dice «desde este correo»
            assertThat(unId(c, """
                    select count(*) from plantilla_correo
                     where es_activa and codigo in ('PRUEBA_DISPONIBLE', 'PRUEBA_DISPONIBLE_ADMIN')
                       and (cuerpo ilike '%desde este correo%' or asunto ilike '%desde este correo%')"""))
                    .isZero();

            // AC-19: las dos plantillas y los tres parámetros, en todas las empresas
            long empresas = unId(c, "select count(*) from organizacion");
            for (String codigo : List.of("RECORDATORIO_EVALUACION", "RECORDATORIO_PRUEBA")) {
                assertThat(unId(c, "select count(distinct organizacion_id) from plantilla_correo "
                        + "where es_activa and codigo = '" + codigo + "'")).as(codigo).isEqualTo(empresas);
            }
            for (String parametro : List.of("recordatorios_activos", "recordatorio_horas_tras_el_turno",
                    "recordatorio_horas_antes_del_plazo")) {
                assertThat(unId(c, "select count(*) from parametro where codigo = '" + parametro + "'"))
                        .as(parametro).isEqualTo(empresas);
            }
            assertThat(unId(c, "select count(*) from parametro where codigo = 'recordatorios_activos' "
                    + "and valor = 'true' and tipo = 'BOOLEANO'")).isEqualTo(empresas);

            // Las transiciones de antes no dicen cómo se avisó: no reciben recordatorios
            assertThat(unId(c, "select count(*) from transicion_estado where aviso_al_candidato is not null"))
                    .isZero();
        }
    }

    private static void plantilla(Connection c, long organizacion, String codigo, int version,
                                  String asunto, String cuerpo, boolean activa) throws SQLException {
        try (PreparedStatement s = c.prepareStatement("""
                insert into plantilla_correo (organizacion_id, codigo, version, asunto, cuerpo, es_activa)
                values (?, ?, ?, ?, ?, ?)""")) {
            s.setLong(1, organizacion);
            s.setString(2, codigo);
            s.setInt(3, version);
            s.setString(4, asunto);
            s.setString(5, cuerpo);
            s.setBoolean(6, activa);
            s.executeUpdate();
        }
    }

    private static List<Integer> activas(Connection c, long organizacion, String codigo) throws SQLException {
        List<Integer> versiones = new ArrayList<>();
        try (Statement s = c.createStatement(); ResultSet r = s.executeQuery(
                "select version from plantilla_correo where es_activa and organizacion_id = " + organizacion
                        + " and codigo = '" + codigo + "' order by version")) {
            while (r.next()) {
                versiones.add(r.getInt(1));
            }
        }
        return versiones;
    }

    private static String cuerpo(Connection c, long organizacion, String codigo, int version)
            throws SQLException {
        try (Statement s = c.createStatement(); ResultSet r = s.executeQuery(
                "select cuerpo from plantilla_correo where organizacion_id = " + organizacion
                        + " and codigo = '" + codigo + "' and version = " + version)) {
            assertThat(r.next()).as(codigo + " v" + version).isTrue();
            return r.getString(1);
        }
    }

    /** Una vacante con lo mínimo que exige la base: área, puesto, solicitud y pesos. */
    private static long vacanteMinima(Connection c, long organizacion) throws SQLException {
        long pesos = unId(c, "select id from version_pesos where estado = 'PUBLICADA' order by id limit 1");
        long area = unId(c, "insert into area (organizacion_id, nombre) values (%d, 'Área V70') returning id"
                .formatted(organizacion));
        long persona = unId(c, "insert into persona (nombre) values ('Responsable') returning id");
        long usuario = unId(c, "insert into usuario (organizacion_id, persona_id) values (%d, %d) returning id"
                .formatted(organizacion, persona));
        long puesto = unId(c, """
                insert into puesto (organizacion_id, codigo, nombre, nivel_puesto_codigo, familia_codigo)
                values (%d, 'P_V70', 'Administrador', 'EJECUCION', 'OPERACIONES') returning id"""
                .formatted(organizacion));
        long solicitud = unId(c, """
                insert into solicitud_talento
                    (organizacion_id, origen, urgencia, estado, area_id, resultado_principal,
                     motivo, consecuencia_no_contratar, analisis_capacidad)
                values (%d, 'DIRECTA', 'NORMAL', 'CON_VACANTE', %d, 'Sostener la sede',
                        'El equipo no llega', 'Se retrasa', 'Se evaluó redistribuir')
                returning id""".formatted(organizacion, area));
        return unId(c, """
                insert into vacante
                    (organizacion_id, solicitud_talento_id, puesto_id, titulo, descripcion,
                     tipo_cierre, estado, version_pesos_id, responsable_usuario_id)
                values (%d, %d, %d, 'Administrador', 'Lleva la sede', 'PERMANENTE', 'PUBLICADA', %d, %d)
                returning id""".formatted(organizacion, solicitud, puesto, pesos, usuario));
    }

    private static void ejecutar(Connection c, String sql) throws SQLException {
        try (Statement s = c.createStatement()) {
            s.executeUpdate(sql);
        }
    }

    private static void migrarHasta(String version) {
        Flyway.configure()
                .dataSource(fuente())
                .locations("classpath:db/migration")
                .target(version)
                .load()
                .migrate();
    }

    private static DataSource fuente() {
        PGSimpleDataSource fuente = new PGSimpleDataSource();
        fuente.setUrl(postgres.getJdbcUrl());
        fuente.setUser(postgres.getUsername());
        fuente.setPassword(postgres.getPassword());
        return fuente;
    }

    private static long unId(Connection c, String sql) throws SQLException {
        try (Statement s = c.createStatement(); ResultSet r = s.executeQuery(sql)) {
            assertThat(r.next()).as("la consulta no devolvió ninguna fila: " + sql).isTrue();
            return r.getLong(1);
        }
    }
}
