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
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * La V75 apunta cómo se puso cada nota de la simulación y la validación que ya existe, para
 * que el primer cambio de pesos parta de ahí (QA-01).
 *
 * <p>Como {@link MigracionFinDelPrestamoIT}: migra hasta la V74, siembra las notas como las
 * dejó el facilitador y solo entonces aplica la V75. Sobre una base vacía no habría nada que
 * apuntar y una regla equivocada pasaría en verde.
 */
@Testcontainers
@DisplayName("La V75 apunta la nota de cada criterio tal como se puso y sobre cuánto")
class MigracionNotaDelCriterioComoSePusoIT {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("pgvector/pgvector:pg16");

    @Test
    @DisplayName("la simulación y la validación guardan su nota y el peso de la versión de su "
            + "vacante; sin peso, sin máximo; el currículum no se toca")
    void lasNotasDeAntesQuedanApuntadas() throws SQLException {
        migrarHasta("74");

        long postulacion;
        long calidad;
        long aprendizaje;
        long resultadoValidacion;
        long personas;
        try (Connection c = fuente().getConnection()) {
            long renaser = unId(c, "select id from organizacion where es_plataforma");
            long pesos = unId(c, "insert into version_pesos (organizacion_id, etiqueta, estado, "
                    + "publicada_en) values (" + renaser + ", 'Pesos V75', 'PUBLICADA', now()) returning id");
            calidad = criterio(c, "SIM_CALIDAD");
            aprendizaje = criterio(c, "SIM_APRENDIZAJE");
            personas = criterio(c, "CV_PERSONAS");
            resultadoValidacion = unId(c, "select id from criterio where etapa_codigo = 'VALIDACION' "
                    + "and version_plantilla_prueba_id is null and puntos is null order by id limit 1");
            // Calidad vale 15 y la validación 25 en Ejecución; el aprendizaje no tiene fila
            for (long[] peso : new long[][] {{calidad, 15}, {resultadoValidacion, 25}, {personas, 20}}) {
                ejecutar(c, "insert into peso_criterio (version_pesos_id, nivel_puesto_codigo, "
                        + "criterio_id, peso) values (%d, 'EJECUCION', %d, %d)"
                        .formatted(pesos, peso[0], peso[1]));
            }
            long vacante = vacante(c, renaser, pesos);
            long persona = unId(c, "insert into persona (nombre) values ('Elena') returning id");
            long usuario = unId(c, "insert into usuario (organizacion_id, persona_id) values (%d, %d) "
                    .formatted(renaser, persona) + "returning id");
            postulacion = unId(c, """
                    insert into postulacion (organizacion_id, usuario_id, vacante_id, estado_codigo)
                    values (%d, %d, %d, 'PERFIL_POR_CONFIRMAR') returning id"""
                    .formatted(renaser, usuario, vacante));
            nota(c, postulacion, calidad, "7");
            nota(c, postulacion, aprendizaje, "6");
            nota(c, postulacion, resultadoValidacion, "18.5");
            nota(c, postulacion, personas, "64");
        }

        migrarHasta("75");

        try (Connection c = fuente().getConnection()) {
            assertThat(comoSePuso(c, postulacion, calidad)).isEqualTo("7.00/15.00");
            assertThat(comoSePuso(c, postulacion, resultadoValidacion)).isEqualTo("18.50/25.00");
            // Sin peso para el nivel: la nota se apunta y el máximo queda vacío
            assertThat(comoSePuso(c, postulacion, aprendizaje)).isEqualTo("6.00/-");
            // El currículum va de 0 a 100 y su peso no es un máximo: no se apunta nada
            assertThat(comoSePuso(c, postulacion, personas)).isEqualTo("-/-");
        }
    }

    private static String comoSePuso(Connection c, long postulacion, long criterio) throws SQLException {
        return texto(c, """
                select coalesce(puntaje_original::text, '-') || '/' || coalesce(maximo_original::text, '-')
                  from nota_criterio where postulacion_id = %d and criterio_id = %d"""
                .formatted(postulacion, criterio));
    }

    private static long criterio(Connection c, String codigo) throws SQLException {
        return unId(c, "select id from criterio where codigo = '" + codigo
                + "' and version_plantilla_prueba_id is null");
    }

    private static void nota(Connection c, long postulacion, long criterio, String puntaje)
            throws SQLException {
        ejecutar(c, """
                insert into nota_criterio (postulacion_id, criterio_id, puntaje, explicacion, origen)
                values (%d, %d, %s, 'Puesta antes de la V75', 'PERSONA')"""
                .formatted(postulacion, criterio, puntaje));
    }

    /** Una vacante publicada de Ejecución con esa versión de pesos. */
    private static long vacante(Connection c, long organizacion, long pesos) throws SQLException {
        long area = unId(c, "insert into area (organizacion_id, nombre) values (%d, 'Operaciones V75') "
                .formatted(organizacion) + "returning id");
        long persona = unId(c, "insert into persona (nombre) values ('Responsable') returning id");
        long usuario = unId(c, "insert into usuario (organizacion_id, persona_id) values (%d, %d) "
                .formatted(organizacion, persona) + "returning id");
        long puesto = unId(c, """
                insert into puesto (organizacion_id, codigo, nombre, nivel_puesto_codigo, familia_codigo)
                values (%d, 'P_V75', 'Analista', 'EJECUCION', 'OPERACIONES') returning id"""
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
                values (%d, %d, %d, 'Analista', 'Lleva la sede', 'PERMANENTE', 'PUBLICADA', %d, %d)
                returning id""".formatted(organizacion, solicitud, puesto, pesos, usuario));
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

    private static void ejecutar(Connection c, String sql) throws SQLException {
        try (Statement s = c.createStatement()) {
            s.executeUpdate(sql);
        }
    }

    private static long unId(Connection c, String sql) throws SQLException {
        try (Statement s = c.createStatement(); ResultSet r = s.executeQuery(sql)) {
            assertThat(r.next()).as("la consulta no devolvió ninguna fila: " + sql).isTrue();
            return r.getLong(1);
        }
    }

    private static String texto(Connection c, String sql) throws SQLException {
        try (Statement s = c.createStatement(); ResultSet r = s.executeQuery(sql)) {
            assertThat(r.next()).as("la consulta no devolvió ninguna fila: " + sql).isTrue();
            return r.getString(1);
        }
    }
}
