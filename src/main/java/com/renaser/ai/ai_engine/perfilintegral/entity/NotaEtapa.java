package com.renaser.ai.ai_engine.perfilintegral.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.Instant;

// El peso de cada etapa aplicado a esta postulación en concreto, atado a la versión de
// pesos con que se calculó (nunca se recalcula con una versión nueva, RF-139).
@Entity
@Table(name = "nota_etapa")
@Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
public class NotaEtapa {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long postulacionId;
    private String etapaCodigo;
    private BigDecimal puntaje;
    private Long versionPesosId;
    private Instant calculadaEn;
    private Instant creadoEn;
}
