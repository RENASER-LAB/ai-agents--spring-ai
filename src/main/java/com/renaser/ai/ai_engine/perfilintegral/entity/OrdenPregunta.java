package com.renaser.ai.ai_engine.perfilintegral.entity;

import jakarta.persistence.*;
import lombok.*;

import java.io.Serializable;
import java.time.Instant;

// Congela qué preguntas le tocaron a esta evaluación y en qué orden (RF-51), para poder
// reproducir el examen exacto que vio el candidato.
@Entity
@Table(name = "orden_pregunta")
@IdClass(OrdenPregunta.Clave.class)
@Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
public class OrdenPregunta {

    @Id
    private Long evaluacionId;

    @Id
    private Long preguntaId;

    private Integer posicion;
    private String ordenOpciones;
    private Instant creadoEn;

    @Getter @Setter @NoArgsConstructor @AllArgsConstructor @EqualsAndHashCode
    public static class Clave implements Serializable {
        private Long evaluacionId;
        private Long preguntaId;
    }
}
