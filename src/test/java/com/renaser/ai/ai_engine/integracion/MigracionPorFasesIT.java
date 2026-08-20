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
 * MigracionesIT corre todas las migraciones de golpe sobre una base vacía, y por eso no vio
 * venir el fallo del 19/08: la V20 borraba el banco v0.1, y sobre una base recién creada no
 * hay nada que borrar, así que la FK nunca llegaba a ejercitarse. En Pruebas sí había datos
 * —249 evaluaciones y 16 respuestas— y el despliegue murió contra postulacion_evaluacion_fk.
 *
 * <p>Esta prueba migra <b>en dos tramos</b>: hasta la V19, siembra el estado que existía en
 * Pruebas, y solo entonces aplica la V20 y la V21. Es la única forma de que una migración se
 * encuentre con datos anteriores, que es cuando este tipo de fallo aparece.
 *
 * <p>Se siembra hasta {@code evaluacion} y no hasta {@code postulacion}: colgar una
 * postulación exige levantar antes área, solicitud, puesto y vacante enteras, y no compra
 * nada: la V20 vieja ya borraba esta evaluación, así que esta prueba la habría cazado igual.
 */
@Testcontainers
@DisplayName("Las migraciones sobre una base que ya venía con datos")
class MigracionPorFasesIT {

    // La misma imagen que producción, igual que en el resto de las de integración.
    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("pgvector/pgvector:pg16");

    @Test
    @DisplayName("la V20 archiva el banco viejo en vez de borrarlo, y respeta lo que ya lo usaba")
    void laV20RespetaLoQueYaUsabaElBancoViejo() throws SQLException {
        migrarHasta("19");

        long evaluacionId;
        long evaluacionEmpezadaId;
        long bancoViejoId;
        long preguntaViejaId;
        try (Connection c = fuente().getConnection()) {
            long organizacion = unId(c, "select id from organizacion where codigo = 'RENASER'");
            bancoViejoId = unId(c, """
                    select id from version_banco
                     where etiqueta like '%V0.1' and nivel_puesto_codigo = 'DIRECCION'""");

            long persona = insertar(c, """
                    insert into persona (nombre)
                    values ('Candidata evaluada con el banco viejo') returning id""");
            long usuario = insertar(c, """
                    insert into usuario (organizacion_id, persona_id)
                    values (%d, %d) returning id""".formatted(organizacion, persona));
            long plantilla = insertar(c, """
                    insert into plantilla_evaluacion
                        (organizacion_id, nombre, nivel_puesto_codigo, version, estado,
                         minutos_objetivo, vigencia_meses)
                    values (%d, 'Plantilla del banco viejo', 'DIRECCION', 1, 'PUBLICADA', 60, 12)
                    returning id""".formatted(organizacion));

            // La evaluación que el 19/08 hizo estallar el despliegue: apunta al banco v0.1.
            evaluacionId = insertar(c, """
                    insert into evaluacion
                        (organizacion_id, usuario_id, plantilla_evaluacion_id,
                         version_banco_nivel_id, estado)
                    values (%d, %d, %d, %d, 'PENDIENTE') returning id"""
                    .formatted(organizacion, usuario, plantilla, bancoViejoId));

            // Y otra que sí llegó a empezar: tiene su examen armado con preguntas del v0.1.
            // A esta no se le puede cambiar el banco debajo.
            long otraPersona = insertar(c, """
                    insert into persona (nombre)
                    values ('Candidato que ya empezó el examen viejo') returning id""");
            long otroUsuario = insertar(c, """
                    insert into usuario (organizacion_id, persona_id)
                    values (%d, %d) returning id""".formatted(organizacion, otraPersona));
            evaluacionEmpezadaId = insertar(c, """
                    insert into evaluacion
                        (organizacion_id, usuario_id, plantilla_evaluacion_id,
                         version_banco_nivel_id, estado, iniciada_en)
                    values (%d, %d, %d, %d, 'EN_CURSO', now()) returning id"""
                    .formatted(organizacion, otroUsuario, plantilla, bancoViejoId));
            preguntaViejaId = unId(c, """
                    select id from pregunta where version_banco_id = %d order by orden limit 1"""
                    .formatted(bancoViejoId));
            ejecutar(c, """
                    insert into orden_pregunta
                        (evaluacion_id, pregunta_id, posicion, orden_opciones)
                    values (%d, %d, 1, 'A,B,C,D')"""
                    .formatted(evaluacionEmpezadaId, preguntaViejaId));
        }

        // Aquí es donde antes reventaba.
        migrarHasta("21");

        try (Connection c = fuente().getConnection()) {
            assertThat(cuenta(c, "select count(*) from evaluacion where id = " + evaluacionId))
                    .as("la evaluación de quien ya fue evaluado no se borra (RF-138)")
                    .isEqualTo(1);

            assertThat(cuenta(c, "select count(*) from version_banco where id = " + bancoViejoId))
                    .as("su banco sigue ahí: sin él no se puede reproducir el examen que vio")
                    .isEqualTo(1);

            assertThat(texto(c, "select estado from version_banco where id = " + bancoViejoId))
                    .as("pero queda retirado de la circulación")
                    .isEqualTo("ARCHIVADA");

            // El selector de ServicioEvaluacionImpl: la PUBLICADA más reciente de ese nivel.
            assertThat(texto(c, """
                    select etiqueta from version_banco
                     where tipo_banco = 'NIVEL' and nivel_puesto_codigo = 'DIRECCION'
                       and estado = 'PUBLICADA'
                     order by publicada_en desc limit 1"""))
                    .as("a quien postule ahora le toca el v3, no el viejo")
                    .contains("v3");

            // Quien no había empezado se pasa al banco nuevo: al entrar se le armará el
            // examen del v3, y no uno cuyas preguntas no tienen peso con qué puntuar.
            assertThat(texto(c, """
                    select vb.etiqueta from evaluacion e
                      join version_banco vb on vb.id = e.version_banco_nivel_id
                     where e.id = """ + evaluacionId))
                    .as("la evaluación que no había empezado pasa al v3")
                    .contains("v3");

            // Quien ya lo había empezado se queda con el suyo, intacto.
            assertThat(cuenta(c, """
                    select count(*) from evaluacion
                     where id = %d and version_banco_nivel_id = %d"""
                    .formatted(evaluacionEmpezadaId, bancoViejoId)))
                    .as("a quien ya empezó no se le cambia el banco debajo")
                    .isEqualTo(1);

            assertThat(cuenta(c, """
                    select count(*) from orden_pregunta
                     where evaluacion_id = %d and pregunta_id = %d"""
                    .formatted(evaluacionEmpezadaId, preguntaViejaId)))
                    .as("y su examen armado sigue en pie, con la pregunta del v0.1 que le tocó")
                    .isEqualTo(1);

            // Pero no puede seguir: el motor v3 no sabe puntuar preguntas sin peso y al
            // entregar le pondría un 0.00 de verdad. La V20 le vence el plazo y deja que el
            // sondeo (SondeoVencimientos) la pase a VENCIDA y cierre su postulación — por eso
            // aquí el estado sigue siendo EN_CURSO: vencerla del todo es trabajo del sondeo.
            assertThat(cuenta(c, """
                    select count(*) from evaluacion
                     where id = %d and estado = 'EN_CURSO' and vence_en <= now()"""
                    .formatted(evaluacionEmpezadaId)))
                    .as("a quien ya empezó el examen viejo se le vence el plazo, no se le inventa una nota")
                    .isEqualTo(1);
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

    private static long insertar(Connection c, String sql) throws SQLException {
        return unId(c, sql);
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

    private static long cuenta(Connection c, String sql) throws SQLException {
        return unId(c, sql);
    }

    private static String texto(Connection c, String sql) throws SQLException {
        try (Statement s = c.createStatement(); ResultSet r = s.executeQuery(sql)) {
            assertThat(r.next()).as("la consulta no devolvió ninguna fila: " + sql).isTrue();
            return r.getString(1);
        }
    }
}
