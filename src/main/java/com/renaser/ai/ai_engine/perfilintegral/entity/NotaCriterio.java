package com.renaser.ai.ai_engine.perfilintegral.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.Instant;

// La nota de cada criterio genérico para esta postulación (RF-43: los 8 del currículum
// en el hito 2). explicacion obligatoria, igual que nota_respuesta.
@Entity
@Table(name = "nota_criterio")
@Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
public class NotaCriterio {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long postulacionId;
    private Long criterioId;
    private BigDecimal puntaje;
    private String explicacion;
    private String origen;
    private BigDecimal confianza;
    private Long ejecucionIaId;
    private Long calificadaPorUsuarioId;
    private Long ajustadaPorUsuarioId;
    private String motivoAjuste;
    private Instant ajustadaEn;
    private Instant creadoEn;
}
