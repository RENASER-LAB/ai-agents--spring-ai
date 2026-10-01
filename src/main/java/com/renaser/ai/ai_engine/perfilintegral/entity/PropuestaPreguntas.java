package com.renaser.ai.ai_engine.perfilintegral.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;

/**
 * Lo que propuso el RECOMENDADOR para las preguntas propias de una vacante (V66).
 *
 * <p><b>No toca el borrador.</b> Se guarda aparte y una persona agrega lo que quiera; nada se
 * reemplaza ni se publica solo. {@code puntosQueFaltan} es lo que le faltaba al borrador al
 * pedirla, y es exactamente lo que la propuesta tiene que sumar.
 */
@Entity
@Table(name = "propuesta_preguntas")
@Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
public class PropuestaPreguntas {

    public static final String PEDIDA = "PEDIDA";
    public static final String LISTA = "LISTA";
    public static final String FALLIDA = "FALLIDA";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long organizacionId;
    private Long vacanteId;
    private String indicacion;
    private Integer puntosQueFaltan;
    private String estado;

    /** La propuesta validada, tal como la leerá el panel. Nula hasta que llega. */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private String contenido;

    private String motivoFallo;
    private Long pedidaPorUsuarioId;
    private Instant creadoEn;
    private Instant terminadaEn;
}
