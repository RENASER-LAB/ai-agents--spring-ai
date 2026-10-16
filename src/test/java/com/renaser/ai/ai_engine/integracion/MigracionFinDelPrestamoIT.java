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
 * La V74 ata cada vacante vieja a lo que ya usaba, para poder borrar el préstamo del banco y
 * de las pruebas en el mismo despliegue (specs/pesos-con-candidatos-y-fin-del-prestamo.md).
 *
 * <p>Como {@link MigracionPreguntasPropiasIT}: migra hasta la V73, siembra vacantes de antes
 * —de RENASER, de una clienta que leía el banco prestado y de otra que ya lo tenía propio— y
 * solo entonces aplica la V74. Sobre una base vacía la migración no tendría a quién atar, y
 * una regla equivocada pasaría en verde.
 *
 * <p>Lo que se protege: la vacante con postulantes queda atada a la versión de su evaluación
 * más reciente (AC-10) y la que no tenía a la publicada de RENASER en ese momento (AC-11); un
 * nivel sin banco publicado se queda sin versión fija; el borrador sin prueba pasa a la del
 * editor (AC-13); RENASER y quien ya tenía lo suyo no cambian (AC-16); y el enlace largo del
 * enunciado del editor se vacía, el de las plantillas viejas no (decisión 7).
 */
@Testcontainers
@DisplayName("La V74 ata las vacantes viejas a lo que ya usaban y deja de prestar")
class MigracionFinDelPrestamoIT {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("pgvector/pgvector:pg16");

    @Test
    @DisplayName("cada vacante vieja queda atada a su banco y a su prueba; RENASER y quien ya "
            + "tenía lo suyo no cambian; el enlace largo del editor se vacía")
    void lasViejasQuedanAtadas() throws SQLException {
        migrarHasta("73");

        long conPostulantes;
        long sinPostulantes;
        long nivelSinBanco;
        long conPreguntasPropias;
        long deRenaser;
        long conBancoPropio;
        long bancoMasReciente;
        long publicadaDeRenaser;
        long borradorSinPrueba;
        long borradorConPrueba;
        long publicadaSinPrueba;
        long borradorDeRenaser;
        long borradorConPruebasPropias;
        long consignaDelEditor;
        long plantillaVieja;
        try (Connection c = fuente().getConnection()) {
            long renaser = unId(c, "select id from organizacion where es_plataforma");
            long acme = unId(c, "insert into organizacion (codigo, nombre) values ('ACME_V74', 'Acme') "
                    + "returning id");
            long propia = unId(c, "insert into organizacion (codigo, nombre, banco_propio, "
                    + "pruebas_puesto_propias) values ('PROPIA_V74', 'Propia', true, true) returning id");
            long pesos = unId(c, "select id from version_pesos where estado = 'PUBLICADA' order by id limit 1");
            long plantillaEvaluacion = unId(c, "select id from plantilla_evaluacion order by id limit 1");

            // Lo que el código de antes le habría dado al siguiente candidato: la publicada de
            // RENASER para Ejecución, con el mismo orden que `laPublicadaDelNivel`.
            publicadaDeRenaser = unId(c, """
                    select id from version_banco
                     where organizacion_id = %d and tipo_banco = 'NIVEL' and vacante_id is null
                       and nivel_puesto_codigo = 'EJECUCION' and estado = 'PUBLICADA'
                     order by publicada_en desc, id desc limit 1""".formatted(renaser));
            // Dos versiones de RENASER con las que rindieron los de antes: la vieja y la última
            long bancoViejo = banco(c, renaser, "EJECUCION", "ARCHIVADA");
            bancoMasReciente = banco(c, renaser, "EJECUCION", "ARCHIVADA");

            conPostulantes = vacante(c, acme, pesos, "Operario con candidatos", "EJECUCION", "NIVEL");
            postulante(c, acme, conPostulantes, plantillaEvaluacion, bancoViejo, "2026-08-01");
            postulante(c, acme, conPostulantes, plantillaEvaluacion, bancoMasReciente, "2026-09-01");
            sinPostulantes = vacante(c, acme, pesos, "Operario sin candidatos", "EJECUCION", "NIVEL");
            // Un nivel para el que RENASER no tiene banco publicado: no había examen posible
            ejecutar(c, "update version_banco set estado = 'ARCHIVADA' where organizacion_id = "
                    + renaser + " and tipo_banco = 'NIVEL' and nivel_puesto_codigo = 'DIRECCION'");
            nivelSinBanco = vacante(c, acme, pesos, "Gerente", "DIRECCION", "NIVEL");
            conPreguntasPropias = vacante(c, acme, pesos, "Con sus preguntas", "EJECUCION", "VACANTE");
            deRenaser = vacante(c, renaser, pesos, "De RENASER", "EJECUCION", "NIVEL");
            conBancoPropio = vacante(c, propia, pesos, "Con banco propio", "EJECUCION", "NIVEL");

            // La etapa técnica de las que estaban en borrador
            // Una prueba de RENASER, como las que se prestaban a las vacantes de otras empresas
            long plantillaPrueba = unId(c, "insert into plantilla_prueba (organizacion_id, nombre) "
                    + "values (" + renaser + ", 'Prueba de Administrador') returning id");
            long versionPrueba = unId(c, """
                    insert into version_plantilla_prueba (plantilla_prueba_id, version, enunciado,
                                                          modalidad, plazo_dias, estado, publicada_en)
                    values (%d, 1, 'Ordena la semana del equipo', 'PLAZO_ABIERTO', 3, 'PUBLICADA', now())
                    returning id""".formatted(plantillaPrueba));
            borradorSinPrueba = borrador(c, acme, pesos, "Borrador sin prueba", null, 45);
            borradorConPrueba = borrador(c, acme, pesos, "Borrador con prueba", versionPrueba, null);
            publicadaSinPrueba = vacante(c, acme, pesos, "Publicada sin prueba", "EJECUCION", "VACANTE");
            borradorDeRenaser = borrador(c, renaser, pesos, "Borrador de RENASER", null, null);
            borradorConPruebasPropias = borrador(c, propia, pesos, "Borrador con pruebas propias", null, null);

            // El enlace largo del enunciado: el del editor y el de una plantilla vieja
            consignaDelEditor = unId(c, """
                    insert into version_banco (organizacion_id, tipo_banco, vacante_id, etiqueta, estado,
                                               proposito, metodo_calificacion, url_consigna)
                    values (%d, 'VACANTE', %d, 'Prueba del editor', 'PUBLICADA', 'PRUEBA_PUESTO',
                            'PUNTOS', 'https://almacen.test/firmado.pdf?dura=180d')
                    returning id""".formatted(acme, conPreguntasPropias));
            plantillaVieja = versionPrueba;
            ejecutar(c, "update version_plantilla_prueba set url_consigna = "
                    + "'https://drive.test/enunciado.pdf' where id = " + plantillaVieja);
        }

        migrarHasta("74");

        try (Connection c = fuente().getConnection()) {
            // AC-10: la de su evaluación más reciente, aunque RENASER la haya archivado
            assertThat(fija(c, conPostulantes)).isEqualTo(bancoMasReciente);
            // AC-11: sin postulantes, la publicada de RENASER en ese momento
            assertThat(fija(c, sinPostulantes)).isEqualTo(publicadaDeRenaser);
            // Un nivel sin banco publicado no tenía examen: se queda como hoy
            assertThat(fija(c, nivelSinBanco)).isNull();
            assertThat(fija(c, conPreguntasPropias)).isNull();
            // AC-16: RENASER y quien ya tenía su banco no leían nada prestado
            assertThat(fija(c, deRenaser)).isNull();
            assertThat(fija(c, conBancoPropio)).isNull();

            // AC-13: el borrador sin prueba pasa a la del editor, sin minutos propios
            assertThat(texto(c, "select instrumento_etapa_tecnica from vacante where id = "
                    + borradorSinPrueba)).isEqualTo("PRUEBA_PROPIA");
            assertThat(texto(c, "select minutos_etapa_tecnica::text from vacante where id = "
                    + borradorSinPrueba)).isNull();
            // AC-12: la que ya tenía una asignada la conserva; y nada fuera del borrador cambia
            for (long id : new long[] {borradorConPrueba, publicadaSinPrueba, borradorDeRenaser,
                    borradorConPruebasPropias}) {
                assertThat(texto(c, "select instrumento_etapa_tecnica from vacante where id = " + id))
                        .as("la vacante %d", id).isEqualTo("PLANTILLA");
            }

            // Decisión 7: el del editor se vacía; el de la plantilla vieja es lo único que
            // apunta a su PDF, y se queda
            assertThat(texto(c, "select url_consigna from version_banco where id = "
                    + consignaDelEditor)).isNull();
            assertThat(texto(c, "select url_consigna from version_plantilla_prueba where id = "
                    + plantillaVieja)).isEqualTo("https://drive.test/enunciado.pdf");
        }
    }

    /** Una versión del banco de nivel, con su fecha de publicación. */
    private static long banco(Connection c, long organizacion, String nivel, String estado)
            throws SQLException {
        return unId(c, """
                insert into version_banco (organizacion_id, tipo_banco, nivel_puesto_codigo, etiqueta,
                                           estado, publicada_en)
                values (%d, 'NIVEL', '%s', 'Banco de antes', '%s', now() - interval '30 days')
                returning id""".formatted(organizacion, nivel, estado));
    }

    /** Una postulación con su evaluación atada a esa versión del banco, creada ese día. */
    private static void postulante(Connection c, long organizacion, long vacante, long plantilla,
                                   long banco, String dia) throws SQLException {
        long persona = unId(c, "insert into persona (nombre) values ('Candidata') returning id");
        long usuario = unId(c, "insert into usuario (organizacion_id, persona_id) values (%d, %d) returning id"
                .formatted(organizacion, persona));
        long evaluacion = unId(c, """
                insert into evaluacion (organizacion_id, usuario_id, plantilla_evaluacion_id,
                                        version_banco_nivel_id, estado, creado_en)
                values (%d, %d, %d, %d, 'TERMINADA', '%s'::timestamptz) returning id"""
                .formatted(organizacion, usuario, plantilla, banco, dia));
        unId(c, """
                insert into postulacion (organizacion_id, usuario_id, vacante_id, estado_codigo,
                                         evaluacion_id)
                values (%d, %d, %d, 'PERFIL_TURNO_CANDIDATO', %d) returning id"""
                .formatted(organizacion, usuario, vacante, evaluacion));
    }

    /** Una vacante publicada de antes, del nivel y con el origen de preguntas dados. */
    private static long vacante(Connection c, long organizacion, long pesos, String titulo,
                                String nivel, String origen) throws SQLException {
        long id = base(c, organizacion, pesos, titulo, nivel, "PUBLICADA");
        ejecutar(c, "update vacante set origen_preguntas = '" + origen + "' where id = " + id);
        return id;
    }

    /** Una vacante en borrador que rinde la prueba del puesto, con o sin una asignada. */
    private static long borrador(Connection c, long organizacion, long pesos, String titulo,
                                 Long versionPrueba, Integer minutos) throws SQLException {
        long id = base(c, organizacion, pesos, titulo, "EJECUCION", "BORRADOR");
        ejecutar(c, "update vacante set instrumento_etapa_tecnica = 'PLANTILLA', "
                + "version_plantilla_prueba_id = " + versionPrueba
                + ", minutos_etapa_tecnica = " + minutos + " where id = " + id);
        return id;
    }

    private static long base(Connection c, long organizacion, long pesos, String titulo,
                             String nivel, String estado) throws SQLException {
        long area = unId(c, "insert into area (organizacion_id, nombre) values (%d, 'Área de %s') returning id"
                .formatted(organizacion, titulo));
        long persona = unId(c, "insert into persona (nombre) values ('Responsable') returning id");
        long usuario = unId(c, "insert into usuario (organizacion_id, persona_id) values (%d, %d) returning id"
                .formatted(organizacion, persona));
        long puesto = unId(c, """
                insert into puesto (organizacion_id, codigo, nombre, nivel_puesto_codigo, familia_codigo)
                values (%d, 'P_%d', '%s', '%s', 'OPERACIONES') returning id"""
                .formatted(organizacion, area, titulo, nivel));
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
                values (%d, %d, %d, '%s', 'Lleva la sede', 'PERMANENTE', '%s', %d, %d)
                returning id""".formatted(organizacion, solicitud, puesto, titulo, estado, pesos,
                usuario));
    }

    private static Long fija(Connection c, long vacante) throws SQLException {
        String valor = texto(c, "select version_banco_fija_id::text from vacante where id = " + vacante);
        return valor == null ? null : Long.valueOf(valor);
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
