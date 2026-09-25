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
import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * La V62 rescata la ciudad de las vacantes cuyo texto de ubicación ya era el nombre exacto
 * de una provincia, y de ninguna otra.
 *
 * <p>Como {@link MigracionPorFasesIT}, migra en dos tramos —hasta la V61, siembra las
 * vacantes con los textos de la spec, y solo entonces aplica la V62— porque sobre una base
 * vacía el rescate no tiene nada que rescatar y una regla equivocada pasaría en verde.
 *
 * <p>Clase aparte y no un método más de {@code MigracionPorFasesIT}: aquella deja su base
 * en la V21 para probar la V20 con datos delante, y Flyway no baja de versión. Dos pruebas
 * que necesitan la base en dos puntos distintos no pueden compartir contenedor.
 *
 * <p>Lo que se protege, en una frase: <b>«Lima» y «LIMA» quedan en Lima; «Selva Alegre»,
 * «test», «Arequipa, Perú» y el vacío se quedan sin ciudad; y el texto de todas sigue
 * intacto</b> (AC-25). Y lo que la spec añade para el día en que dos provincias se llamen
 * igual: si el nombre casa con varias, no se elige ninguna.
 */
@Testcontainers
@DisplayName("La V62 rescata la ciudad de la vacante solo cuando el texto es exactamente una provincia")
class MigracionCiudadDeLaVacanteIT {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("pgvector/pgvector:pg16");

    @Test
    @DisplayName("rescata el nombre exacto sin mirar mayúsculas, tildes ni espacios, y no toca el texto")
    void rescataSoloElNombreExacto() throws SQLException {
        migrarHasta("61");

        // Los textos de la AC-25, más tres vecinos: espacios de sobra, una tilde perdida y
        // un nombre que casa con dos provincias.
        Map<String, String> textos = new LinkedHashMap<>();
        textos.put("lima-exacta", "Lima");
        textos.put("lima-mayusculas", "LIMA");
        textos.put("lima-espacios", "  lima ");
        textos.put("huanuco-sin-tilde", "HUANUCO");
        textos.put("barrio", "Selva Alegre");
        textos.put("basura", "test");
        textos.put("con-pais", "Arequipa, Perú");
        textos.put("vacia", null);
        textos.put("homonima", "Cusco");

        Map<String, Long> ids = new LinkedHashMap<>();
        String lima;
        String huanuco;
        try (Connection c = fuente().getConnection()) {
            long organizacion = unId(c, "select id from organizacion where codigo = 'RENASER'");
            long area = insertar(c, """
                    insert into area (organizacion_id, nombre)
                    values (%d, 'Operaciones') returning id""".formatted(organizacion));
            long persona = insertar(c, "insert into persona (nombre) values ('Responsable') returning id");
            long usuario = insertar(c, """
                    insert into usuario (organizacion_id, persona_id)
                    values (%d, %d) returning id""".formatted(organizacion, persona));
            long puesto = insertar(c, """
                    insert into puesto (organizacion_id, codigo, nombre, nivel_puesto_codigo, familia_codigo)
                    values (%d, 'COORD', 'Coordinador', 'EJECUCION', 'OPERACIONES') returning id"""
                    .formatted(organizacion));
            long pesos = unId(c, "select id from version_pesos where estado = 'PUBLICADA' order by id limit 1");

            // Una provincia homónima de Cusco, para el caso de «casa con varias». Hoy las 196
            // son distintas; la regla se escribe para el día en que no lo sean.
            ejecutar(c, "insert into ubigeo (codigo, nivel, padre, nombre) values ('99', 1, null, 'Prueba')");
            ejecutar(c, "insert into ubigeo (codigo, nivel, padre, nombre) values ('9901', 2, '99', 'Cusco')");

            lima = texto(c, "select codigo from ubigeo where nivel = 2 and nombre = 'Lima'");
            huanuco = texto(c, "select codigo from ubigeo where nivel = 2 and nombre = 'Huánuco'");

            for (Map.Entry<String, String> entrada : textos.entrySet()) {
                long solicitud = insertar(c, """
                        insert into solicitud_talento
                            (organizacion_id, origen, urgencia, estado, area_id, resultado_principal,
                             motivo, consecuencia_no_contratar, analisis_capacidad)
                        values (%d, 'DIRECTA', 'NORMAL', 'CON_VACANTE', %d, 'Sostener la sede',
                                'El equipo no llega', 'Se retrasa', 'Se evaluó redistribuir')
                        returning id""".formatted(organizacion, area));
                String ubicacion = entrada.getValue() == null
                        ? "null" : "'" + entrada.getValue().replace("'", "''") + "'";
                ids.put(entrada.getKey(), insertar(c, """
                        insert into vacante
                            (organizacion_id, solicitud_talento_id, puesto_id, titulo, descripcion,
                             ubicacion, tipo_cierre, estado, version_pesos_id, responsable_usuario_id)
                        values (%d, %d, %d, 'Vacante %s', 'Lleva la sede', %s, 'PERMANENTE',
                                'PUBLICADA', %d, %d)
                        returning id""".formatted(organizacion, solicitud, puesto,
                        entrada.getKey(), ubicacion, pesos, usuario)));
            }
        }

        migrarHasta("62");

        try (Connection c = fuente().getConnection()) {
            assertThat(ciudadDe(c, ids.get("lima-exacta"))).as("«Lima»").isEqualTo(lima);
            assertThat(ciudadDe(c, ids.get("lima-mayusculas"))).as("«LIMA»").isEqualTo(lima);
            assertThat(ciudadDe(c, ids.get("lima-espacios"))).as("«  lima »").isEqualTo(lima);
            assertThat(ciudadDe(c, ids.get("huanuco-sin-tilde"))).as("«HUANUCO»").isEqualTo(huanuco);

            assertThat(ciudadDe(c, ids.get("barrio"))).as("un barrio no es una ciudad").isNull();
            assertThat(ciudadDe(c, ids.get("basura"))).as("«test»").isNull();
            assertThat(ciudadDe(c, ids.get("con-pais")))
                    .as("«Arequipa, Perú» no es exactamente «Arequipa»: no se adivina")
                    .isNull();
            assertThat(ciudadDe(c, ids.get("vacia"))).as("sin texto").isNull();
            assertThat(ciudadDe(c, ids.get("homonima")))
                    .as("un nombre que casa con dos provincias no elige ninguna")
                    .isNull();

            // El texto de todas sigue intacto: es la zona o referencia desde ahora.
            for (Map.Entry<String, String> entrada : textos.entrySet()) {
                assertThat(texto(c, "select ubicacion from vacante where id = " + ids.get(entrada.getKey())))
                        .as("el texto de «%s» no se toca", entrada.getKey())
                        .isEqualTo(entrada.getValue());
            }

            // Y la clave foránea existe: un código inventado no entra por SQL.
            assertThat(cuenta(c, """
                    select count(*) from information_schema.table_constraints
                     where table_name = 'vacante' and constraint_type = 'FOREIGN KEY'
                       and constraint_name like '%ciudad_ubigeo%'"""))
                    .isEqualTo(1);
        }
    }

    private static String ciudadDe(Connection c, long vacanteId) throws SQLException {
        return texto(c, "select ciudad_ubigeo from vacante where id = " + vacanteId);
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
