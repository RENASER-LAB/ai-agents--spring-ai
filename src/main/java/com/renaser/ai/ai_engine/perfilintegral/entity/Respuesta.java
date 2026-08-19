package com.renaser.ai.ai_engine.perfilintegral.entity;

import jakarta.persistence.*;
import lombok.*;

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
    private Integer segundos;
    private Instant respondidaEn;
    private Instant creadoEn;
}
