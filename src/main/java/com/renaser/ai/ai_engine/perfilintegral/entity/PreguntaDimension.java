package com.renaser.ai.ai_engine.perfilintegral.entity;

import jakarta.persistence.*;
import lombok.*;

import java.io.Serializable;
import java.time.Instant;

// Qué dimensión mide una pregunta abierta, que no tiene opciones que puntuar.
@Entity
@Table(name = "pregunta_dimension")
@IdClass(PreguntaDimension.Clave.class)
@Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
public class PreguntaDimension {

    @Id
    private Long preguntaId;

    @Id
    private String dimensionCodigo;

    private Instant creadoEn;

    @Getter @Setter @NoArgsConstructor @AllArgsConstructor @EqualsAndHashCode
    public static class Clave implements Serializable {
        private Long preguntaId;
        private String dimensionCodigo;
    }
}
