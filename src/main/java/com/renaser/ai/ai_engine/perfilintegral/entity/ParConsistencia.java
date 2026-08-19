package com.renaser.ai.ai_engine.perfilintegral.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.Instant;

// Qué dos preguntas se comparan para detectar contradicciones. Arranca vacía.
@Entity
@Table(name = "par_consistencia")
@Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
public class ParConsistencia {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long versionBancoId;

    // Dos mayúsculas seguidas ("AId"): la naming strategy no las separa sola.
    @Column(name = "pregunta_a_id")
    private Long preguntaAId;

    @Column(name = "pregunta_b_id")
    private Long preguntaBId;

    private BigDecimal diferenciaMaxima;
    private Instant creadoEn;
}
