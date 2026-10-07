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
 * La V69 le pone a cada criterio de un borrador de la prueba lo que vale, y no toca nada más.
 *
 * <p>Como {@link MigracionPreguntasPropiasIT}: migra hasta la V68, siembra una prueba en borrador
 * y otra publicada como las dejaba el editor —con la parte calificada escrita a mano— y solo
 * entonces aplica la V69. Sobre una base vacía la migración no tendría a quién convertir.
 *
 * <p>Lo que se protege: <b>el borrador vale lo mismo que valía</b> —sus cerradas más su parte
 * calificada—, así que su balance no se mueve; <b>la publicada queda sin total</b> y sigue
 * leyendo su parte calificada tal cual (AC-22); y las preguntas propias del Perfil Integral, que
 * no tienen parte calificada, no se tocan.
 */
@Testcontainers
@DisplayName("La V69 da a los criterios del borrador de la prueba lo que valían")
class MigracionPuntosDelCriterioIT {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("pgvector/pgvector:pg16");

    @Test
    @DisplayName("los borradores valen sus cerradas más su parte calificada; la publicada y el Perfil Integral, sin total")
    void losBorradoresValenLoQueValian() throws SQLException {
        migrarHasta("68");

        long mixto;
        long soloCerradas;
        long vacio;
        long publicado;
        long delPerfil;
        try (Connection c = fuente().getConnection()) {
            long renaser = unId(c, "select id from organizacion where codigo = 'RENASER'");
            long pesos = unId(c, "select id from version_pesos where estado = 'PUBLICADA' order by id limit 1");
            long vacante = vacante(c, renaser, pesos, "Asistente contable V69");

            long borrador = version(c, renaser, vacante, "PRUEBA_PUESTO", "BORRADOR");
            mixto = criterio(c, borrador, "Mixto", 20, "IA");
            pregunta(c, borrador, mixto, "P1", "OPCION_UNICA", 10);
            pregunta(c, borrador, mixto, "P2", "ABIERTA", 0);
            pregunta(c, borrador, mixto, "P3", "OPCION_MULTIPLE", 15);
            soloCerradas = criterio(c, borrador, "Solo cerradas", null, null);
            pregunta(c, borrador, soloCerradas, "P4", "ESCALA", 25);
            vacio = criterio(c, borrador, "Sin preguntas", 30, "PERSONA");

            long publicada = version(c, renaser, vacante, "PRUEBA_PUESTO", "PUBLICADA");
            publicado = criterio(c, publicada, "Publicado", 40, "IA");
            pregunta(c, publicada, publicado, "P1", "OPCION_UNICA", 60);

            long perfil = version(c, renaser, vacante, "PERFIL_INTEGRAL", "BORRADOR");
            delPerfil = criterio(c, perfil, "Del perfil", null, null);
            pregunta(c, perfil, delPerfil, "P1", "OPCION_UNICA", 100);
        }

        migrarHasta("69");

        try (Connection c = fuente().getConnection()) {
            assertThat(total(c, mixto)).as("10 + 15 de cerradas y 20 calificados").isEqualTo(45);
            assertThat(total(c, soloCerradas)).as("sin parte calificada, sus cerradas").isEqualTo(25);
            assertThat(total(c, vacio)).as("sin preguntas, su parte calificada").isEqualTo(30);
            // La parte calificada no se reescribe: sale igual de la cuenta nueva (45 − 25 = 20)
            assertThat(unId(c, "select puntos_calificados from criterio_banco where id = " + mixto)).isEqualTo(20);
            assertThat(total(c, publicado)).as("la publicada no se toca").isNull();
            assertThat(unId(c, "select puntos_calificados from criterio_banco where id = " + publicado))
                    .isEqualTo(40);
            assertThat(total(c, delPerfil)).as("el Perfil Integral no tiene parte calificada").isNull();
        }
    }

    private static long version(Connection c, long organizacion, long vacante, String proposito, String estado)
            throws SQLException {
        return unId(c, """
                insert into version_banco (organizacion_id, tipo_banco, vacante_id, etiqueta, estado,
                                           proposito, metodo_calificacion)
                values (%d, 'VACANTE', %d, '%s', '%s', '%s', 'PUNTOS')
                returning id""".formatted(organizacion, vacante, proposito + " " + estado, estado, proposito));
    }

    private static long criterio(Connection c, long version, String nombre, Integer calificada, String calificador)
            throws SQLException {
        return unId(c, """
                insert into criterio_banco (version_banco_id, nombre, orden, puntos_calificados, calificador)
                values (%d, '%s', 1, %s, %s)
                returning id""".formatted(version, nombre, calificada,
                calificador == null ? "null" : "'" + calificador + "'"));
    }

    private static void pregunta(Connection c, long version, long criterio, String codigo, String tipo, int puntos)
            throws SQLException {
        unId(c, """
                insert into pregunta (version_banco_id, codigo, tipo, enunciado, es_puntuable, orden, puntos,
                                      criterio_banco_id)
                values (%d, '%s', '%s', '¿%s?', %s, 1, %d, %d)
                returning id""".formatted(version, codigo, tipo, codigo, puntos > 0, puntos, criterio));
    }

    private static Integer total(Connection c, long criterio) throws SQLException {
        try (Statement s = c.createStatement();
             ResultSet r = s.executeQuery("select puntos_del_criterio from criterio_banco where id = " + criterio)) {
            assertThat(r.next()).isTrue();
            int valor = r.getInt(1);
            return r.wasNull() ? null : valor;
        }
    }

    /** Una vacante publicada, con su área, puesto, solicitud y responsable. */
    private static long vacante(Connection c, long organizacion, long pesos, String titulo) throws SQLException {
        long area = unId(c, "insert into area (organizacion_id, nombre) values (%d, 'Área de %s') returning id"
                .formatted(organizacion, titulo));
        long persona = unId(c, "insert into persona (nombre) values ('Responsable') returning id");
        long usuario = unId(c, "insert into usuario (organizacion_id, persona_id) values (%d, %d) returning id"
                .formatted(organizacion, persona));
        long puesto = unId(c, """
                insert into puesto (organizacion_id, codigo, nombre, nivel_puesto_codigo, familia_codigo)
                values (%d, 'P_%d', '%s', 'EJECUCION', 'OPERACIONES') returning id"""
                .formatted(organizacion, area, titulo));
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
                values (%d, %d, %d, '%s', 'Lleva la sede', 'PERMANENTE', 'PUBLICADA', %d, %d)
                returning id""".formatted(organizacion, solicitud, puesto, titulo, pesos, usuario));
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
