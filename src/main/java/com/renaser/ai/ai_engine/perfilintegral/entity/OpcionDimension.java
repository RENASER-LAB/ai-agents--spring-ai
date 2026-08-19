package com.renaser.ai.ai_engine.perfilintegral.entity;

import jakarta.persistence.*;
import lombok.*;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.Instant;

// Cuánto suma una opción a una dimensión (típico: 2 la principal, 1 una secundaria).
@Entity
@Table(name = "opcion_dimension")
@IdClass(OpcionDimension.Clave.class)
@Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
public class OpcionDimension {

    @Id
    private Long opcionId;

    @Id
    private String dimensionCodigo;

    private BigDecimal incremento;
    private Instant creadoEn;

    @Getter @Setter @NoArgsConstructor @AllArgsConstructor @EqualsAndHashCode
    public static class Clave implements Serializable {
        private Long opcionId;
        private String dimensionCodigo;
    }
}
