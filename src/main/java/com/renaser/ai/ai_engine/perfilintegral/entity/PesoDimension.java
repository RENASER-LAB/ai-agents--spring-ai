package com.renaser.ai.ai_engine.perfilintegral.entity;

import jakarta.persistence.*;
import lombok.*;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.Instant;

// Cuánto pesa cada dimensión, por nivel de puesto, en esta versión de pesos.
@Entity
@Table(name = "peso_dimension")
@IdClass(PesoDimension.Clave.class)
@Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
public class PesoDimension {

    @Id
    private Long versionPesosId;

    @Id
    private String nivelPuestoCodigo;

    @Id
    private String dimensionCodigo;

    private BigDecimal peso;
    private Instant creadoEn;

    @Getter @Setter @NoArgsConstructor @AllArgsConstructor @EqualsAndHashCode
    public static class Clave implements Serializable {
        private Long versionPesosId;
        private String nivelPuestoCodigo;
        private String dimensionCodigo;
    }
}
