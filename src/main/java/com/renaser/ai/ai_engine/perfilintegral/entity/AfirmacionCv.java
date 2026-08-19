package com.renaser.ai.ai_engine.perfilintegral.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

// Cada afirmación del currículum, clasificada (RF-45). El currículum no descarta a
// nadie por sí solo (RF-46): esto solo alimenta el Perfil Integral.
@Entity
@Table(name = "afirmacion_cv")
@Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
public class AfirmacionCv {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long cvId;
    private String texto;
    private String clasificacion;
    private String preguntaValidacion;
    private Long ejecucionIaId;
    private Instant creadoEn;
}
