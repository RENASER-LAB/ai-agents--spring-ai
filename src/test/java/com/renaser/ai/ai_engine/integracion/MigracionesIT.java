package com.renaser.ai.ai_engine.integracion;

import com.renaser.ai.ai_engine.postulacion.entity.EstadoPostulacion;
import com.renaser.ai.ai_engine.postulacion.repository.EstadoPostulacionRepository;
import com.renaser.ai.ai_engine.organizacion.repository.OrganizacionRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers
@TestPropertySource(properties = {
        "spring.jpa.hibernate.ddl-auto=validate",
        "spring.flyway.enabled=true"
})
public class MigracionesIT {

    @Container
    @ServiceConnection
    // La misma imagen que producción: pgvector/pg16, no un Postgres pelado
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("pgvector/pgvector:pg16");

    @DynamicPropertySource
    static void propiedades(DynamicPropertyRegistry registro) {
        registro.add("app.seguridad.jwt-secreto",
                () -> "clave-de-pruebas-suficientemente-larga-para-hmac-256-bits");
        registro.add("spring.ai.deepseek.api-key", () -> "clave-de-pruebas-no-se-usa");
    }

    @Autowired
    private EstadoPostulacionRepository estados;

    @Autowired
    private OrganizacionRepository organizaciones;

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    void lasSemillasDejanElCatalogoCompleto() {
        List<EstadoPostulacion> todos = estados.findAllByOrderByOrden();
        assertThat(todos).hasSize(18);
        assertThat(todos.stream().filter(EstadoPostulacion::isEsFinal)).hasSize(3);
        // La rejilla: cada estado no final ni de entrada tiene etapa y momento
        assertThat(todos.stream()
                .filter(e -> !e.isEsFinal() && !"POSTULADA".equals(e.getCodigo()))
                .allMatch(e -> e.getEtapaCodigo() != null && e.getMomentoCodigo() != null))
                .isTrue();
        assertThat(organizaciones.findByCodigo("RENASER")).isPresent();
    }

    /**
     * Lo que trajo la V19: la criba en dos pasadas y la ficha de datos del currículum.
     *
     * <p>Son cuatro piezas sueltas que solo sirven juntas, y ninguna falla de forma ruidosa
     * si se cae: sin la columna {@code modo} la segunda pasada no corre nunca y nadie avisa;
     * sin el parámetro, el corte se va al valor por defecto del código; sin la instrucción,
     * el agente de datos se queda sin qué decirle al modelo.
     */
    @Test
    void laV19DejaListaLaCribaEnDosPasadas() {
        // 1 · La tabla de la ficha, con una fila por postulación y ni un dato prohibido.
        // Que no estén edad, sexo ni estado civil no es un olvido: el agente lee el
        // currículum recortado y no tiene forma de pedir el original (RF-41).
        List<String> columnas = jdbc.queryForList(
                "select column_name from information_schema.columns where table_name = 'dato_cv'",
                String.class);
        assertThat(columnas).contains("postulacion_id", "nombre", "email", "telefono",
                "perfil_resumen", "habilidades", "experiencia_meses_total", "ultimo_puesto",
                "ultima_empresa", "educacion_maxima", "ejecucion_ia_id");
        assertThat(columnas).doesNotContain("edad", "sexo", "estado_civil", "foto");

        // 2 · El agente que saca esos datos, activo y con su instrucción publicada
        assertThat(jdbc.queryForObject(
                "select count(*) from agente where codigo = 'DATOS_CV' and es_activo",
                Integer.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject("""
                select count(*) from instruccion_ia
                where agente_codigo = 'DATOS_CV' and es_activa""", Integer.class)).isEqualTo(1);

        // 3 · De qué pasada es cada trabajo. Sin esta columna las dos pasadas se pisan: la
        // cola encontraría el trabajo que ya hizo la rápida y la fina no correría nunca.
        assertThat(jdbc.queryForObject("""
                select column_default from information_schema.columns
                where table_name = 'trabajo_ia' and column_name = 'modo'""", String.class))
                .contains("FINA");
        assertThat(jdbc.queryForObject("""
                select count(*) from information_schema.check_constraints c
                join information_schema.constraint_column_usage u
                  on u.constraint_name = c.constraint_name
                where u.table_name = 'trabajo_ia' and u.column_name = 'modo'
                  and c.check_clause like '%RAPIDA%'""", Integer.class)).isEqualTo(1);

        // 4 · Hasta dónde llega la segunda pasada. Es un parámetro y no una constante
        // porque el número correcto lo decide Renaser, no el código.
        assertThat(jdbc.queryForObject("""
                select valor from parametro p
                join organizacion o on o.id = p.organizacion_id
                where p.codigo = 'porcentaje_criba_fina' and o.codigo = 'RENASER'""",
                String.class)).isEqualTo("50");
    }
}
