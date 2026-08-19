package com.renaser.ai.ai_engine.ai.model;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

// La instrucción vigente de cada agente del hito 2 de selección. Solo una activa por
// agente a la vez (índice parcial único en la migración V11).
@Entity
@Table(name = "instruccion_ia")
@Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
public class InstruccionIa {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String agenteCodigo;
    private Integer version;
    private String texto;
    private boolean esActiva;
    private Long publicadaPorUsuarioId;
    private Instant publicadaEn;
    private Instant creadoEn;
}
