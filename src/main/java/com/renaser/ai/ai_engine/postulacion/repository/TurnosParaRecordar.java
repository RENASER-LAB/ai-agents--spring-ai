package com.renaser.ai.ai_engine.postulacion.repository;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Los turnos del candidato a los que un recordatorio podría tocarles ahora (V70).
 *
 * <p>Una sola consulta para todos, porque corre cada minuto: el banco sin entregar
 * («Perfil Integral · turno del candidato») y la prueba sin empezar («Prueba · turno del
 * candidato»), con todo lo que hace falta para decidir sin volver a la base.
 *
 * <p>Lo que la consulta ya descarta, que es casi todo el punto 18 de la spec:
 * <ul>
 *   <li>la vacante archivada o eliminada;</li>
 *   <li>el turno que no se abrió avisando: movido «sin avisar» (NINGUNO) o abierto antes de
 *       la V70 (vacío). Lo segundo es lo que evita la ráfaga del primer día;</li>
 *   <li>quien ya entregó el banco o ya empezó la prueba (o se le cerró sin empezarla);</li>
 *   <li>quien cambió de estado: solo cuenta el turno en el que está, y solo si la última
 *       transición es la que lo metió en él.</li>
 * </ul>
 * Los parámetros de la empresa, las horas y lo que ya salió los decide quien llama.
 */
@Repository
@RequiredArgsConstructor
public class TurnosParaRecordar {

    private static final String CONSULTA = """
            SELECT p.id                AS postulacion_id,
                   p.uuid              AS postulacion_uuid,
                   p.organizacion_id   AS organizacion_id,
                   p.usuario_id        AS usuario_id,
                   p.vacante_id        AS vacante_id,
                   v.titulo            AS vacante_titulo,
                   p.estado_codigo     AS estado,
                   t.id                AS turno_id,
                   t.ocurrida_en       AS turno_desde,
                   CASE WHEN p.estado_codigo = 'PERFIL_TURNO_CANDIDATO' THEN ev.vence_en
                        WHEN v.instrumento_etapa_tecnica = 'CUESTIONARIO_TECNICO' THEN et.vence_en
                        ELSE ip.vence_en
                   END                 AS vence_en
              FROM postulacion p
              JOIN vacante v ON v.id = p.vacante_id
              JOIN LATERAL (SELECT te.id, te.ocurrida_en, te.estado_nuevo_codigo,
                                   te.aviso_al_candidato
                              FROM transicion_estado te
                             WHERE te.postulacion_id = p.id
                             ORDER BY te.id DESC
                             LIMIT 1) t ON true
              LEFT JOIN evaluacion ev ON ev.id = p.evaluacion_id
              LEFT JOIN evaluacion et ON et.id = p.evaluacion_tecnica_id
              LEFT JOIN intento_prueba ip ON ip.postulacion_id = p.id
             WHERE p.estado_codigo IN ('PERFIL_TURNO_CANDIDATO', 'PRUEBA_TURNO_CANDIDATO')
               AND v.archivada_en IS NULL
               AND v.eliminada_en IS NULL
               AND t.estado_nuevo_codigo = p.estado_codigo
               AND t.aviso_al_candidato = 'CORREO'
               AND CASE WHEN p.estado_codigo = 'PERFIL_TURNO_CANDIDATO'
                             THEN ev.id IS NOT NULL AND ev.estado IN ('PENDIENTE', 'EN_CURSO')
                        WHEN v.instrumento_etapa_tecnica = 'CUESTIONARIO_TECNICO'
                             THEN et.id IS NOT NULL AND et.estado = 'PENDIENTE'
                                  AND et.iniciada_en IS NULL
                        ELSE ip.id IS NOT NULL AND ip.iniciado_en IS NULL
                             AND ip.entregado_en IS NULL AND NOT ip.no_completada
                   END
             ORDER BY p.id
            """;

    private final JdbcTemplate jdbc;

    /** Un turno abierto, con lo que el recordatorio necesita. */
    public record Turno(Long postulacionId, UUID postulacionUuid, Long organizacionId,
                        Long usuarioId, Long vacanteId, String vacanteTitulo, String estado,
                        Long turnoId, Instant turnoDesde, Instant venceEn) {

        /** Si es el turno del banco; si no, el de la prueba. */
        public boolean esDelBanco() {
            return "PERFIL_TURNO_CANDIDATO".equals(estado);
        }
    }

    public List<Turno> abiertos() {
        return jdbc.query(CONSULTA, (rs, fila) -> new Turno(
                rs.getLong("postulacion_id"),
                rs.getObject("postulacion_uuid", UUID.class),
                rs.getLong("organizacion_id"),
                rs.getLong("usuario_id"),
                rs.getLong("vacante_id"),
                rs.getString("vacante_titulo"),
                rs.getString("estado"),
                rs.getLong("turno_id"),
                instante(rs, "turno_desde"),
                instante(rs, "vence_en")));
    }

    private static Instant instante(ResultSet rs, String columna) throws SQLException {
        OffsetDateTime valor = rs.getObject(columna, OffsetDateTime.class);
        return valor == null ? null : valor.toInstant();
    }
}
