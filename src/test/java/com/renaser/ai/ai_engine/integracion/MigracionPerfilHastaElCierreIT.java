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
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * La V78 pasa los datos de antes a la regla nueva: el Perfil Integral dura hasta que la
 * vacante se cierra, y «Cómo se cierra» tiene dos formas
 * (specs/Ready/perfil-integral-hasta-el-cierre-de-la-vacante, «Datos y compatibilidad», AC-14).
 *
 * <p>Como {@link MigracionFinDelPrestamoIT}: migra hasta la V77, siembra lo que había —vacantes
 * por plazas, fechas a la medianoche UTC, una publicada con la fecha pasada, evaluaciones con
 * sus 14 días— y solo entonces aplica la V78. Sobre una base vacía la migración no tendría nada
 * que pasar, y una regla equivocada pasaría en verde.
 *
 * <p>Lo que queda fuera de esta prueba, a propósito: las evaluaciones abiertas en vacantes ya
 * cerradas no las toca la migración —su cierre lleva los avisos de siempre y eso lo hace la
 * aplicación en su primer sondeo—; eso se prueba en {@code FlujoPerfilHastaElCierreIT}.
 */
@Testcontainers
@DisplayName("La V78 pasa los datos de antes a «el Perfil Integral dura hasta el cierre»")
class MigracionPerfilHastaElCierreIT {

    private static final ZoneId LIMA = ZoneId.of("America/Lima");

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("pgvector/pgvector:pg16");

    @Test
    @DisplayName("cada regla de «Datos y compatibilidad» se cumple sobre los datos de antes")
    void losDatosDeAntesPasanALaReglaNueva() throws SQLException {
        migrarHasta("77");

        LocalDate hoy = LocalDate.now(LIMA);
        LocalDate enDiezDias = hoy.plusDays(10);
        LocalDate haceTresDias = hoy.minusDays(3);

        long porPlazas;
        long conFechaFutura;
        long conFechaPasada;
        long publicadaSinFecha;
        long borradorConFechaPasada;
        long borradorAMediaTarde;
        long cerrada;
        long abiertaConSusDias;
        long empezadaVencidaSinBarrer;
        long terminada;
        long cuestionarioTecnico;
        long yaCerradaPorPlazo;
        long abiertaEnCerrada;
        String descripcionAntes;
        try (Connection c = fuente().getConnection()) {
            long renaser = unId(c, "select id from organizacion where es_plataforma");
            long pesos = unId(c, "select id from version_pesos where estado = 'PUBLICADA' order by id limit 1");
            long banco = unId(c, "select id from version_banco order by id limit 1");
            long plantilla = unId(c, "select id from plantilla_evaluacion order by id limit 1");

            porPlazas = vacante(c, renaser, pesos, "Por plazas", "PUBLICADA", "PLAZAS", null);
            ejecutar(c, "update vacante set plazas = 3 where id = " + porPlazas);
            conFechaFutura = vacante(c, renaser, pesos, "Con fecha futura", "PUBLICADA", "FECHA",
                    medianocheUtc(enDiezDias));
            conFechaPasada = vacante(c, renaser, pesos, "Con fecha pasada", "PUBLICADA", "FECHA",
                    medianocheUtc(haceTresDias));
            publicadaSinFecha = vacante(c, renaser, pesos, "Sin fecha", "PUBLICADA", "FECHA", null);
            borradorConFechaPasada = vacante(c, renaser, pesos, "Borrador vencido", "BORRADOR", "FECHA",
                    medianocheUtc(haceTresDias));
            // Una hora que no es la medianoche UTC: la escribió la API, y manda el día de Lima
            borradorAMediaTarde = vacante(c, renaser, pesos, "A media tarde", "BORRADOR", "FECHA",
                    "2026-12-01 15:30:00+00");
            cerrada = vacante(c, renaser, pesos, "Cerrada", "CERRADA", "PERMANENTE", null);

            // El Perfil Integral con sus 14 días, en la publicada por plazas
            abiertaConSusDias = postulante(c, renaser, porPlazas, banco, plantilla, "PERFIL_INTEGRAL",
                    "PENDIENTE", "now() + interval '5 days'", "PERFIL_TURNO_CANDIDATO", null);
            // Vencida por días un instante antes de desplegar, sin que el barrido llegara a cerrarla
            empezadaVencidaSinBarrer = postulante(c, renaser, porPlazas, banco, plantilla,
                    "PERFIL_INTEGRAL", "EN_CURSO", "now() - interval '1 hour'", "PERFIL_TURNO_CANDIDATO",
                    null);
            terminada = postulante(c, renaser, porPlazas, banco, plantilla, "PERFIL_INTEGRAL",
                    "TERMINADA", "now() + interval '3 days'", "PERFIL_CALIFICANDO", null);
            yaCerradaPorPlazo = postulante(c, renaser, porPlazas, banco, plantilla, "PERFIL_INTEGRAL",
                    "VENCIDA", "now() - interval '2 days'", "CERRADA", "PLAZO_VENCIDO");
            cuestionarioTecnico = tecnico(c, renaser, conFechaFutura, banco);
            abiertaEnCerrada = postulante(c, renaser, cerrada, banco, plantilla, "PERFIL_INTEGRAL",
                    "PENDIENTE", "now() + interval '4 days'", "PERFIL_TURNO_CANDIDATO", null);
            descripcionAntes = texto(c, "select descripcion from parametro "
                    + "where codigo = 'dias_plazo_evaluacion' order by id limit 1");
        }

        migrarHasta("78");

        try (Connection c = fuente().getConnection()) {
            // Vacantes «Por plazas»: pasan a Permanente, y sus plazas se quedan en su columna
            assertThat(texto(c, "select tipo_cierre from vacante where id = " + porPlazas))
                    .isEqualTo("PERMANENTE");
            assertThat(texto(c, "select plazas::text from vacante where id = " + porPlazas)).isEqualTo("3");
            assertThat(cuantas(c, "select count(*) from vacante where tipo_cierre = 'PLAZAS'")).isZero();

            // Fechas a las 00:00 UTC: a las 23:59:59 de Lima de ese día
            assertThat(texto(c, "select tipo_cierre from vacante where id = " + conFechaFutura))
                    .isEqualTo("FECHA");
            assertThat(instante(c, conFechaFutura)).isEqualTo(finDelDiaEnLima(enDiezDias));
            // Otra hora: el día de Lima en que cae
            assertThat(instante(c, borradorAMediaTarde))
                    .isEqualTo(finDelDiaEnLima(LocalDate.of(2026, 12, 1)));

            // Publicadas con la fecha ya pasada, o sin fecha: a Permanente, para no cerrarse solas
            assertThat(texto(c, "select tipo_cierre from vacante where id = " + conFechaPasada))
                    .isEqualTo("PERMANENTE");
            assertThat(instante(c, conFechaPasada)).isEqualTo(finDelDiaEnLima(haceTresDias));
            assertThat(texto(c, "select tipo_cierre from vacante where id = " + publicadaSinFecha))
                    .isEqualTo("PERMANENTE");
            // Un borrador no se cierra solo: se queda con su forma, y al publicar se le exige otra fecha
            assertThat(texto(c, "select tipo_cierre from vacante where id = " + borradorConFechaPasada))
                    .isEqualTo("FECHA");
            assertThat(texto(c, "select estado from vacante where id = " + conFechaPasada))
                    .isEqualTo("PUBLICADA");

            // El Perfil Integral abierto en vacantes publicadas pierde sus días, sin cerrarse
            for (long postulacion : new long[] {abiertaConSusDias, empezadaVencidaSinBarrer}) {
                assertThat(texto(c, "select e.vence_en::text from evaluacion e join postulacion p "
                        + "on p.evaluacion_id = e.id where p.id = " + postulacion)).isNull();
                assertThat(texto(c, "select estado_codigo from postulacion where id = " + postulacion))
                        .isEqualTo("PERFIL_TURNO_CANDIDATO");
            }
            assertThat(texto(c, "select e.estado from evaluacion e join postulacion p "
                    + "on p.evaluacion_id = e.id where p.id = " + empezadaVencidaSinBarrer))
                    .isEqualTo("EN_CURSO");
            // Lo terminado, lo ya cerrado por plazo (no se reabre) y el cuestionario técnico, igual
            assertThat(texto(c, "select e.vence_en::text from evaluacion e join postulacion p "
                    + "on p.evaluacion_id = e.id where p.id = " + terminada)).isNotNull();
            assertThat(texto(c, "select estado_codigo || ' ' || motivo_cierre from postulacion where id = "
                    + yaCerradaPorPlazo)).isEqualTo("CERRADA PLAZO_VENCIDO");
            assertThat(texto(c, "select e.estado || ' ' || (e.vence_en is not null) from evaluacion e "
                    + "join postulacion p on p.evaluacion_id = e.id where p.id = " + yaCerradaPorPlazo))
                    .isEqualTo("VENCIDA true");
            assertThat(texto(c, "select e.vence_en::text from evaluacion e join postulacion p "
                    + "on p.evaluacion_tecnica_id = e.id where p.id = " + cuestionarioTecnico)).isNotNull();
            // En una vacante ya cerrada no la toca la migración: la vence el primer sondeo, con avisos
            assertThat(texto(c, "select e.estado || ' ' || (e.vence_en is not null) from evaluacion e "
                    + "join postulacion p on p.evaluacion_id = e.id where p.id = " + abiertaEnCerrada))
                    .isEqualTo("PENDIENTE true");

            // El parámetro se queda para el cuestionario técnico, y su descripción lo dice
            String descripcion = texto(c, "select descripcion from parametro "
                    + "where codigo = 'dias_plazo_evaluacion' order by id limit 1");
            assertThat(descripcion).isNotEqualTo(descripcionAntes).contains("cuestionario tecnico")
                    .contains("El Perfil Integral no lo usa");
            assertThat(cuantas(c, "select count(*) from parametro where codigo = 'dias_plazo_evaluacion' "
                    + "and valor = '14'")).isPositive();
        }
    }

    private static String medianocheUtc(LocalDate dia) {
        return dia.atStartOfDay(ZoneOffset.UTC).toInstant().toString();
    }

    private static Instant finDelDiaEnLima(LocalDate dia) {
        return dia.atTime(23, 59, 59).atZone(LIMA).toInstant();
    }

    private static Instant instante(Connection c, long vacante) throws SQLException {
        try (Statement s = c.createStatement();
             ResultSet r = s.executeQuery("select cierra_en from vacante where id = " + vacante)) {
            assertThat(r.next()).isTrue();
            return r.getObject(1, java.time.OffsetDateTime.class).toInstant();
        }
    }

    /** Una postulación con su evaluación del propósito, el estado y el vencimiento dados. */
    private static long postulante(Connection c, long organizacion, long vacante, long banco,
                                   long plantilla, String proposito, String estadoEvaluacion,
                                   String venceEn, String estadoPostulacion, String motivoCierre)
            throws SQLException {
        long usuario = usuario(c, organizacion);
        long evaluacion = unId(c, """
                insert into evaluacion (organizacion_id, usuario_id, plantilla_evaluacion_id,
                                        version_banco_nivel_id, proposito, estado, vence_en, creado_en)
                values (%d, %d, %d, %d, '%s', '%s', %s, now() - interval '10 days') returning id"""
                .formatted(organizacion, usuario, plantilla, banco, proposito, estadoEvaluacion, venceEn));
        return unId(c, """
                insert into postulacion (organizacion_id, usuario_id, vacante_id, estado_codigo,
                                         evaluacion_id, motivo_cierre)
                values (%d, %d, %d, '%s', %d, %s) returning id"""
                .formatted(organizacion, usuario, vacante, estadoPostulacion, evaluacion,
                        motivoCierre == null ? "null" : "'" + motivoCierre + "'"));
    }

    /** Una postulación en la etapa técnica con su cuestionario CAZATALENTOS abierto. */
    private static long tecnico(Connection c, long organizacion, long vacante, long banco)
            throws SQLException {
        long usuario = usuario(c, organizacion);
        long evaluacion = unId(c, """
                insert into evaluacion (organizacion_id, usuario_id, version_banco_nivel_id, proposito,
                                        estado, vence_en, creado_en)
                values (%d, %d, %d, 'CUESTIONARIO_TECNICO', 'PENDIENTE', now() + interval '6 days', now())
                returning id""".formatted(organizacion, usuario, banco));
        return unId(c, """
                insert into postulacion (organizacion_id, usuario_id, vacante_id, estado_codigo,
                                         evaluacion_tecnica_id)
                values (%d, %d, %d, 'PRUEBA_TURNO_CANDIDATO', %d) returning id"""
                .formatted(organizacion, usuario, vacante, evaluacion));
    }

    private static long usuario(Connection c, long organizacion) throws SQLException {
        long persona = unId(c, "insert into persona (nombre) values ('Candidata') returning id");
        return unId(c, "insert into usuario (organizacion_id, persona_id) values (%d, %d) returning id"
                .formatted(organizacion, persona));
    }

    private static long vacante(Connection c, long organizacion, long pesos, String titulo,
                                String estado, String tipoCierre, String cierraEn) throws SQLException {
        long area = unId(c, "insert into area (organizacion_id, nombre) values (%d, 'Área de %s') returning id"
                .formatted(organizacion, titulo));
        long responsable = usuario(c, organizacion);
        long puesto = unId(c, """
                insert into puesto (organizacion_id, codigo, nombre, nivel_puesto_codigo, familia_codigo)
                values (%d, 'P_V78_%d', '%s', 'EJECUCION', 'OPERACIONES') returning id"""
                .formatted(organizacion, area, titulo));
        long solicitud = unId(c, """
                insert into solicitud_talento
                    (organizacion_id, origen, urgencia, estado, area_id, resultado_principal,
                     motivo, consecuencia_no_contratar, analisis_capacidad)
                values (%d, 'DIRECTA', 'NORMAL', 'CON_VACANTE', %d, 'Sostener la caja',
                        'El equipo no llega', 'Se retrasa', 'Se evaluó redistribuir')
                returning id""".formatted(organizacion, area));
        return unId(c, """
                insert into vacante
                    (organizacion_id, solicitud_talento_id, puesto_id, titulo, descripcion,
                     tipo_cierre, cierra_en, estado, version_pesos_id, responsable_usuario_id)
                values (%d, %d, %d, '%s', 'Lleva la caja', '%s', %s, '%s', %d, %d)
                returning id""".formatted(organizacion, solicitud, puesto, titulo, tipoCierre,
                cierraEn == null ? "null" : "'" + cierraEn + "'::timestamptz", estado, pesos,
                responsable));
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

    private static long cuantas(Connection c, String sql) throws SQLException {
        return unId(c, sql);
    }

    private static String texto(Connection c, String sql) throws SQLException {
        try (Statement s = c.createStatement(); ResultSet r = s.executeQuery(sql)) {
            assertThat(r.next()).as("la consulta no devolvió ninguna fila: " + sql).isTrue();
            return r.getString(1);
        }
    }
}
