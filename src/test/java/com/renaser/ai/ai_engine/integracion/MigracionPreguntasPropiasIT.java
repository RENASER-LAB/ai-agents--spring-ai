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
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * La V66 no le cambia nada a las vacantes que ya existían (AC-02, AC-01c).
 *
 * <p>Como {@link MigracionCiudadDeLaVacanteIT}: migra hasta la V63 (la V64 y la V65 son de
 * otro trabajo y aquí no existen), siembra vacantes de antes y solo entonces aplica la V66.
 * Sobre una base vacía la migración no tendría a quién cambiarle nada, y una regla
 * equivocada —las existentes a {@code VACANTE}, por ejemplo— pasaría en verde.
 *
 * <p>Lo que se protege: <b>toda vacante de antes sigue con el banco del nivel</b>, sea de la
 * empresa dueña del banco o de otra que lo tiene prestado, y la que no aplicaba evaluación
 * sigue sin aplicarla; el cuestionario técnico que ya tenía una vacante queda marcado como
 * tal, y lo que se inserte a la antigua (sin propósito) sigue siendo un cuestionario técnico.
 */
@Testcontainers
@DisplayName("La V66 deja las vacantes de antes con el banco del nivel")
class MigracionPreguntasPropiasIT {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("pgvector/pgvector:pg16");

    @Test
    @DisplayName("las vacantes de antes rinden el banco del nivel, la prestada también, y el cuestionario técnico sigue siéndolo")
    void lasDeAntesSiguenConElBancoDelNivel() throws SQLException {
        migrarHasta("63");

        long renaserNivel;
        long sinEvaluacion;
        long conCuestionario;
        long acmePrestada;
        long cuestionario;
        long bancosDelNivel;
        try (Connection c = fuente().getConnection()) {
            long renaser = unId(c, "select id from organizacion where codigo = 'RENASER'");
            long acme = unId(c, "insert into organizacion (codigo, nombre) values ('ACME_V66', 'Acme') returning id");
            long pesos = unId(c, "select id from version_pesos where estado = 'PUBLICADA' order by id limit 1");

            renaserNivel = vacante(c, renaser, pesos, "Asistente contable", true);
            sinEvaluacion = vacante(c, renaser, pesos, "Chofer", false);
            conCuestionario = vacante(c, renaser, pesos, "Analista con prueba técnica", true);
            acmePrestada = vacante(c, acme, pesos, "Operario de Acme", true);

            cuestionario = unId(c, """
                    insert into version_banco (organizacion_id, tipo_banco, vacante_id, etiqueta, estado)
                    values (%d, 'VACANTE', %d, 'Cuestionario técnico', 'PUBLICADA')
                    returning id""".formatted(renaser, conCuestionario));
            bancosDelNivel = unId(c, "select count(*) from version_banco where vacante_id is null");
            assertThat(bancosDelNivel).as("hay bancos del nivel sembrados").isPositive();
        }

        migrarHasta("66");

        try (Connection c = fuente().getConnection()) {
            // Todas las de antes, al banco del nivel; aplicar o no la evaluación no se toca
            for (long id : new long[] {renaserNivel, sinEvaluacion, conCuestionario, acmePrestada}) {
                assertThat(texto(c, "select origen_preguntas from vacante where id = " + id))
                        .as("la vacante %d", id).isEqualTo("NIVEL");
            }
            assertThat(texto(c, "select aplica_evaluacion::text from vacante where id = " + renaserNivel))
                    .isEqualTo("true");
            assertThat(texto(c, "select aplica_evaluacion::text from vacante where id = " + sinEvaluacion))
                    .isEqualTo("false");
            assertThat(texto(c, "select aplica_evaluacion::text from vacante where id = " + acmePrestada))
                    .as("la de otra empresa sigue rindiendo el banco prestado").isEqualTo("true");

            // El cuestionario técnico de antes queda marcado como tal; los bancos del nivel,
            // sin propósito y con su método de siempre
            assertThat(texto(c, "select proposito from version_banco where id = " + cuestionario))
                    .isEqualTo("CUESTIONARIO_TECNICO");
            assertThat(unId(c, "select count(*) from version_banco where vacante_id is null"))
                    .isEqualTo(bancosDelNivel);
            assertThat(unId(c, "select count(*) from version_banco where vacante_id is null "
                    + "and (proposito is not null or metodo_calificacion = 'PUNTOS')")).isZero();

            // Lo que se inserte a la antigua, sin decir su propósito, es un cuestionario técnico
            long renaser = unId(c, "select id from organizacion where codigo = 'RENASER'");
            long borrador = unId(c, """
                    insert into version_banco (organizacion_id, tipo_banco, vacante_id, etiqueta, estado)
                    values (%d, 'VACANTE', %d, 'Cuestionario técnico v2', 'BORRADOR')
                    returning id""".formatted(renaser, conCuestionario));
            assertThat(texto(c, "select proposito from version_banco where id = " + borrador))
                    .isEqualTo("CUESTIONARIO_TECNICO");

            // Y la misma vacante puede tener, además, sus preguntas propias publicadas: una de
            // cada propósito, nunca dos del mismo
            unId(c, """
                    insert into version_banco (organizacion_id, tipo_banco, vacante_id, etiqueta, estado,
                                               proposito, metodo_calificacion)
                    values (%d, 'VACANTE', %d, 'Preguntas propias', 'PUBLICADA', 'PERFIL_INTEGRAL', 'PUNTOS')
                    returning id""".formatted(renaser, conCuestionario));
            assertThatThrownBy(() -> unId(c, """
                    insert into version_banco (organizacion_id, tipo_banco, vacante_id, etiqueta, estado)
                    values (%d, 'VACANTE', %d, 'Otro cuestionario', 'PUBLICADA')
                    returning id""".formatted(renaser, conCuestionario)))
                    .isInstanceOf(SQLException.class)
                    .hasMessageContaining("version_banco_publicada_por_vacante_idx");
        }
    }

    /** Una vacante publicada de antes de la V66, con su área, puesto, solicitud y responsable. */
    private static long vacante(Connection c, long organizacion, long pesos, String titulo,
                                boolean aplicaEvaluacion) throws SQLException {
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
                     tipo_cierre, estado, version_pesos_id, responsable_usuario_id, aplica_evaluacion)
                values (%d, %d, %d, '%s', 'Lleva la sede', 'PERMANENTE', 'PUBLICADA', %d, %d, %s)
                returning id""".formatted(organizacion, solicitud, puesto, titulo, pesos, usuario,
                aplicaEvaluacion));
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

    private static String texto(Connection c, String sql) throws SQLException {
        try (Statement s = c.createStatement(); ResultSet r = s.executeQuery(sql)) {
            assertThat(r.next()).as("la consulta no devolvió ninguna fila: " + sql).isTrue();
            return r.getString(1);
        }
    }
}
