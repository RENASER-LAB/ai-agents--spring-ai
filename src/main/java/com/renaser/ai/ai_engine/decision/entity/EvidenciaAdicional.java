package com.renaser.ai.ai_engine.decision.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.Instant;

// Lo que se pide cuando sale ámbar. El tope está en parametro (2 por defecto): al
// llegar, el sistema ya no permite otra y obliga a decidir con lo que hay.
@Entity
@Table(name = "evidencia_adicional")
@Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
public class EvidenciaAdicional {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long postulacionId;
    // 1 o 2
    private Integer numero;
    private String motivo;
    private String enunciado;
    private Long solicitadaPorUsuarioId;
    private Instant entregadaEn;
    private BigDecimal puntaje;
    private String explicacion;
    private Instant creadoEn;
}
