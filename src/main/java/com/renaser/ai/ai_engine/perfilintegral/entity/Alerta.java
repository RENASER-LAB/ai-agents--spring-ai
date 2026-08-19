package com.renaser.ai.ai_engine.perfilintegral.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

// Contradicciones detectadas (RF-63, RF-64). Nunca descartan solas: quedan en la ficha
// como pregunta para la conversación final.
@Entity
@Table(name = "alerta")
@Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
public class Alerta {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long postulacionId;
    private String tipo;
    private String descripcion;

    // Dos mayúsculas seguidas ("AId"): la naming strategy no las separa sola.
    @Column(name = "pregunta_a_id")
    private Long preguntaAId;

    @Column(name = "pregunta_b_id")
    private Long preguntaBId;

    private Long ejecucionIaId;
    private Long confirmadaPorUsuarioId;
    private Instant creadoEn;
}
