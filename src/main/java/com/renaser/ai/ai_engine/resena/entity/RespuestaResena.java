package com.renaser.ai.ai_engine.resena.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

/**
 * La versión de la persona reseñada, debajo de la reseña (V63). Una viva por reseña.
 *
 * <p>{@code editableHasta} se guarda porque se mueve: cuando la empresa edita la reseña ya
 * respondida, la persona recibe otros 30 días desde esa edición.
 */
@Entity
@Table(name = "respuesta_resena")
@Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
public class RespuestaResena {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long resenaId;
    private Long usuarioId;
    private String texto;
    private Instant publicadaEn;
    private Instant editadaEn;
    private Instant editableHasta;
    private Instant ocultadaEn;
    private String notaOcultacion;
    private Instant borradaEn;
}
