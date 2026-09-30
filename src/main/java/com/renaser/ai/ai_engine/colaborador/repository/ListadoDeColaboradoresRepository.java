package com.renaser.ai.ai_engine.colaborador.repository;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.sql.Date;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Locale;

import com.renaser.ai.ai_engine.colaborador.dto.DtosColaborador.Filtros;

/**
 * Las consultas de la lista de colaboradores, en SQL.
 *
 * <p>SQL y no JPQL porque la fila se arma con tres «el más reciente de» encadenados —el
 * periodo actual, la situación vigente hoy y su estado, que sale de las fechas en hora de
 * Lima— y la lista tiene que aguantar 5.000 personas en páginas de 50 servidas por la base.
 * Traerlas todas para filtrar en memoria es justo lo que no escala.
 *
 * <p>⚠️ La fila de la lista no lleva el sueldo: ninguna columna lo enseña, y lo que no viaja
 * no se puede filtrar por descuido. Solo {@link #actuales} lo lee, y quien llama decide.
 */
@Repository
@RequiredArgsConstructor
public class ListadoDeColaboradoresRepository {

    private final NamedParameterJdbcTemplate jdbc;

    /**
     * La situación que se enseña de cada persona: la vigente hoy si ya entró, la primera si
     * todavía no, y la última si ya cesó. Un tramo vacío (sustituido el mismo día) nunca.
     *
     * <p>⚠️ **Cada «el más reciente de» va en un {@code lateral ... limit 1}, a propósito.**
     * Con {@code distinct on} sobre CTE encadenadas, el plan dependía de las estadísticas de
     * Postgres: justo después de cargar 5.000 filas en una empresa nueva —antes de que el
     * autoanalyze pasara— el planificador creía que la empresa tenía una fila, anidaba bucles
     * sobre las CTE y la lista tardaba minutos o acababa en 500. Un {@code lateral} con
     * {@code limit} no se puede aplanar: por fuerza es una búsqueda por persona en los índices
     * {@code periodo_laboral_colaborador_idx} y {@code situacion_laboral_periodo_idx}, y crece en
     * línea con la empresa sean cuales sean las estadísticas.
     */
    private static final String ACTUALES = """
            with base as (
                select c.id, c.tipo_documento, c.numero_documento, c.nombres, c.apellido_paterno,
                       c.apellido_materno, pa.id as periodo_id, pa.fecha_ingreso, pa.fecha_cese,
                       sa.sede_id, sa.area_id, sa.puesto_id, sa.jefe_colaborador_id,
                       sa.tipo_contrato, sa.fin_contrato, sa.fin_periodo_prueba,
                       sa.regimen_laboral, sa.sueldo_base, sa.moneda,
                       case when pa.fecha_ingreso > :hoy then 'POR_INGRESAR'
                            when pa.fecha_cese is not null and pa.fecha_cese < :hoy then 'CESADO'
                            else 'ACTIVO' end as estado,
                       translate(lower(c.apellido_paterno || ' ' || coalesce(c.apellido_materno, '')
                                 || ' ' || c.nombres), 'áéíóúüñ', 'aeiouun') as para_buscar
                  from colaborador c
                  join lateral (
                        select p.id, p.fecha_ingreso, p.fecha_cese
                          from periodo_laboral p
                         where p.colaborador_id = c.id
                         order by p.fecha_ingreso desc, p.id desc
                         limit 1
                  ) pa on true
                  left join lateral (
                        select s.sede_id, s.area_id, s.puesto_id, s.jefe_colaborador_id,
                               s.tipo_contrato, s.fin_contrato, s.fin_periodo_prueba,
                               s.regimen_laboral, s.sueldo_base, s.moneda
                          from situacion_laboral s
                         where s.periodo_id = pa.id
                           and s.anulada_en is null
                           and (s.vigente_hasta is null or s.vigente_hasta >= s.vigente_desde)
                         order by (s.vigente_desde <= :hoy) desc,
                                  case when s.vigente_desde <= :hoy then s.vigente_desde end desc nulls last,
                                  s.vigente_desde asc,
                                  s.id desc
                         limit 1
                  ) sa on true
                 where c.organizacion_id = :org
            )
            """;

    public record Fila(Long id, String tipoDocumento, String numeroDocumento, String nombres,
                       String apellidoPaterno, String apellidoMaterno, String cargo, String area,
                       String sede, Long jefeId, String jefe, LocalDate fechaIngreso,
                       LocalDate finContrato, LocalDate fechaCese, String estado) {}

    public record Pagina(List<Fila> filas, long total) {}

    /**
     * Lo actual de cada persona, entero: para comprobar círculos de jefes y para comparar lo
     * que trae una fila del Excel con lo vigente. Aquí sí va el sueldo; quien llama decide.
     */
    public record Actual(Long id, String tipoDocumento, String numeroDocumento, Long periodoId,
                         LocalDate fechaIngreso, LocalDate fechaCese, String estado, Long sedeId,
                         Long areaId, Long cargoId, Long jefeId, String tipoContrato,
                         LocalDate finContrato, LocalDate finPeriodoPrueba, String regimenLaboral,
                         BigDecimal sueldoBase, String moneda) {}

    public record Pendiente(Long postulacionId, String nombre, String apellidos,
                            boolean anonimizado, Long vacanteId, String vacante,
                            Instant contratadoEn) {}

    public Pagina pagina(Long organizacionId, LocalDate hoy, Filtros filtros, int pagina, int tamano) {
        MapSqlParameterSource parametros = new MapSqlParameterSource()
                .addValue("org", organizacionId)
                .addValue("hoy", Date.valueOf(hoy));
        String donde = condiciones(filtros, hoy, parametros);

        Long total = jdbc.queryForObject(consultaDelTotal(donde), parametros, Long.class);

        parametros.addValue("limite", tamano).addValue("desde", (long) pagina * tamano);
        List<Fila> filas = jdbc.query(consultaDeLaPagina(donde), parametros, (rs, i) -> new Fila(
                rs.getLong("id"), rs.getString("tipo_documento"), rs.getString("numero_documento"),
                rs.getString("nombres"), rs.getString("apellido_paterno"),
                rs.getString("apellido_materno"), rs.getString("cargo"), rs.getString("area"),
                rs.getString("sede"), (Long) rs.getObject("jefe_colaborador_id"),
                nombreDelJefe(rs), fecha(rs, "fecha_ingreso"), fecha(rs, "fin_contrato"),
                fecha(rs, "fecha_cese"), rs.getString("estado")));
        return new Pagina(filas, total == null ? 0 : total);
    }

    /** Lo actual de toda la empresa. 5.000 filas cortas: una sola consulta. */
    public List<Actual> actuales(Long organizacionId, LocalDate hoy) {
        MapSqlParameterSource parametros = new MapSqlParameterSource()
                .addValue("org", organizacionId)
                .addValue("hoy", Date.valueOf(hoy));
        return jdbc.query(consultaDeActuales(), parametros, (rs, i) -> new Actual(
                rs.getLong("id"), rs.getString("tipo_documento"), rs.getString("numero_documento"),
                rs.getLong("periodo_id"), fecha(rs, "fecha_ingreso"), fecha(rs, "fecha_cese"),
                rs.getString("estado"), (Long) rs.getObject("sede_id"),
                (Long) rs.getObject("area_id"), (Long) rs.getObject("puesto_id"),
                (Long) rs.getObject("jefe_colaborador_id"), rs.getString("tipo_contrato"),
                fecha(rs, "fin_contrato"), fecha(rs, "fin_periodo_prueba"),
                rs.getString("regimen_laboral"), rs.getBigDecimal("sueldo_base"),
                rs.getString("moneda")));
    }

    // ============ Las consultas ============
    //
    // Públicas solo para que la prueba de volumen mida su plan con EXPLAIN ANALYZE: con :org y
    // :hoy, y la de la página además con :limite y :desde.

    public static String consultaDelTotal(String donde) {
        return ACTUALES + "select count(*) from base b" + donde;
    }

    public static String consultaDeLaPagina(String donde) {
        return ACTUALES + """
                select b.id, b.tipo_documento, b.numero_documento, b.nombres, b.apellido_paterno,
                       b.apellido_materno, pu.nombre as cargo, ar.nombre as area, se.nombre as sede,
                       b.jefe_colaborador_id, j.apellido_paterno as jefe_paterno,
                       j.apellido_materno as jefe_materno, j.nombres as jefe_nombres,
                       b.fecha_ingreso, b.fin_contrato, b.fecha_cese, b.estado
                  from base b
                  left join sede se on se.id = b.sede_id
                  left join area ar on ar.id = b.area_id
                  left join puesto pu on pu.id = b.puesto_id
                  left join colaborador j on j.id = b.jefe_colaborador_id
                """ + donde + """
                 order by b.apellido_paterno, b.apellido_materno nulls first, b.nombres, b.id
                 limit :limite offset :desde
                """;
    }

    public static String consultaDeActuales() {
        return ACTUALES + "select * from base b";
    }

    /**
     * Las contrataciones de la empresa que esperan su alta.
     *
     * <p>Todas las que están en CONTRATADO, vengan del panel o de la API, de antes o de
     * después de la V64, salvo las de vacantes eliminadas, las que ya tienen ficha y las que
     * RR.HH. decidió no dar de alta.
     */
    public List<Pendiente> pendientes(Long organizacionId) {
        return jdbc.query("""
                select p.id as postulacion_id, per.nombre, per.apellidos,
                       per.anonimizado_en is not null as anonimizado, v.id as vacante_id,
                       v.titulo,
                       (select max(t.ocurrida_en) from transicion_estado t
                         where t.postulacion_id = p.id
                           and t.estado_nuevo_codigo = 'CONTRATADO') as contratado_en
                  from postulacion p
                  join vacante v on v.id = p.vacante_id and v.eliminada_en is null
                  join usuario u on u.id = p.usuario_id
                  left join persona per on per.id = u.persona_id
                 where p.organizacion_id = :org
                   and p.estado_codigo = 'CONTRATADO'
                   and not exists (select 1 from periodo_laboral pl where pl.postulacion_id = p.id)
                   and not exists (select 1 from colaborador c where c.postulacion_id = p.id)
                   and not exists (select 1 from contratado_sin_alta x where x.postulacion_id = p.id)
                 order by contratado_en desc nulls last, p.id desc
                """, new MapSqlParameterSource("org", organizacionId), (rs, i) -> {
            Timestamp cuando = rs.getTimestamp("contratado_en");
            return new Pendiente(rs.getLong("postulacion_id"), rs.getString("nombre"),
                    rs.getString("apellidos"), rs.getBoolean("anonimizado"),
                    rs.getLong("vacante_id"), rs.getString("titulo"),
                    cuando == null ? null : cuando.toInstant());
        });
    }

    // ============ Apoyo ============

    private static String condiciones(Filtros filtros, LocalDate hoy, MapSqlParameterSource parametros) {
        List<String> partes = new ArrayList<>();
        if (filtros.estados() != null && !filtros.estados().isEmpty()) {
            partes.add("b.estado in (:estados)");
            parametros.addValue("estados", filtros.estados());
        }
        if (filtros.sedeId() != null) {
            partes.add("b.sede_id = :sede");
            parametros.addValue("sede", filtros.sedeId());
        }
        if (filtros.areaId() != null) {
            partes.add("b.area_id = :area");
            parametros.addValue("area", filtros.areaId());
        }
        if (filtros.cargoId() != null) {
            partes.add("b.puesto_id = :cargo");
            parametros.addValue("cargo", filtros.cargoId());
        }
        if (filtros.porVencer()) {
            // Los que vencen en 30 días o menos y los ya vencidos de quien sigue dentro: los dos
            // piden que alguien haga algo con ese contrato.
            partes.add("b.fin_contrato is not null and b.fin_contrato <= :limiteVence "
                    + "and b.estado <> 'CESADO'");
            parametros.addValue("limiteVence", Date.valueOf(hoy.plusDays(30)));
        }
        if (filtros.busqueda() != null && !filtros.busqueda().isBlank()) {
            List<String> palabras = Arrays.stream(normalizar(filtros.busqueda()).split("\\s+"))
                    .filter(p -> !p.isBlank()).limit(6).toList();
            for (int i = 0; i < palabras.size(); i++) {
                String nombre = "palabra" + i;
                partes.add("(b.para_buscar like :" + nombre + " or lower(b.numero_documento) like :"
                        + nombre + ")");
                parametros.addValue(nombre, "%" + escaparLike(palabras.get(i)) + "%");
            }
        }
        return partes.isEmpty() ? "" : " where " + String.join(" and ", partes);
    }

    /** Minúsculas y sin tildes, igual que la columna con la que se compara. */
    private static String normalizar(String texto) {
        String bajo = texto.toLowerCase(Locale.ROOT).trim();
        StringBuilder limpio = new StringBuilder(bajo.length());
        for (char c : bajo.toCharArray()) {
            limpio.append(switch (c) {
                case 'á' -> 'a';
                case 'é' -> 'e';
                case 'í' -> 'i';
                case 'ó' -> 'o';
                case 'ú', 'ü' -> 'u';
                case 'ñ' -> 'n';
                default -> c;
            });
        }
        return limpio.toString();
    }

    private static String escaparLike(String palabra) {
        return palabra.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }

    private static String nombreDelJefe(ResultSet rs) throws SQLException {
        String paterno = rs.getString("jefe_paterno");
        if (paterno == null) return null;
        String materno = rs.getString("jefe_materno");
        String apellidos = materno == null || materno.isBlank() ? paterno : paterno + " " + materno;
        return apellidos + ", " + rs.getString("jefe_nombres");
    }

    private static LocalDate fecha(ResultSet rs, String columna) throws SQLException {
        Date valor = rs.getDate(columna);
        return valor == null ? null : valor.toLocalDate();
    }
}
