package com.renaser.ai.ai_engine.perfilintegral.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;

// Cada respuesta se guarda al momento (RF-52). Solo se puede responder una pregunta que
// de verdad le tocó a esta evaluación: lo impone la FK compuesta contra orden_pregunta.
@Entity
@Table(name = "respuesta")
@Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
public class Respuesta {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long evaluacionId;
    private Long preguntaId;
    private Long opcionId;
    private String texto;

    /**
     * La respuesta de los formatos del banco v3 que no caben en una sola opción: un SJT-R
     * califica cada opción del 1 al 5, un EF-4 marca dos, un SEC ordena cinco pasos.
     *
     * <p>Su forma depende de {@code pregunta.tipo} y <b>la valida el código al guardar, no la
     * base</b>: es jsonb, así que aquí no hay clave foránea que impida apuntar a una opción de
     * otra pregunta. Esa comprobación vive en {@code ServicioEvaluacionImpl.validarDetalle}.
     *
     * <p><b>Pendiente:</b> esto debería ser una tabla de detalle. Ver la migración V21.
     */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private String detalle;
    private Integer segundos;
    private Instant respondidaEn;
    private Instant creadoEn;
}
