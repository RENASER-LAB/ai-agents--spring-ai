package com.renaser.ai.ai_engine.perfilintegral.entity;

import jakarta.persistence.*;
import lombok.*;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.Instant;

// Cuánto pesa cada criterio del CV, por nivel de puesto (tabla RF-43).
@Entity
@Table(name = "peso_criterio")
@IdClass(PesoCriterio.Clave.class)
@Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
public class PesoCriterio {

    @Id
    private Long versionPesosId;

    @Id
    private String nivelPuestoCodigo;

    @Id
    private Long criterioId;

    private BigDecimal peso;
    private Instant creadoEn;

    @Getter @Setter @NoArgsConstructor @AllArgsConstructor @EqualsAndHashCode
    public static class Clave implements Serializable {
        private Long versionPesosId;
        private String nivelPuestoCodigo;
        private Long criterioId;
    }
}
