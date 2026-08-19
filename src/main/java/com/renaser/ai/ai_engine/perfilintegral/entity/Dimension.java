package com.renaser.ai.ai_engine.perfilintegral.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

// Las 22 dimensiones que puede medir una pregunta. Catálogo cerrado.
@Entity
@Table(name = "dimension")
@Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
public class Dimension {

    @Id
    private String codigo;

    private String nombre;
    private String definicion;
    private boolean esObligatoria;
    private Integer orden;
    private Instant creadoEn;
}
