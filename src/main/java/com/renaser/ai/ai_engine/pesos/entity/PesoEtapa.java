package com.renaser.ai.ai_engine.pesos.entity;

import jakarta.persistence.*;
import lombok.*;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.Instant;

// Cuánto pesa cada etapa. El nivel salió de la clave: los pesos son iguales para todos.
@Entity
@Table(name = "peso_etapa")
@IdClass(PesoEtapa.Clave.class)
@Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
public class PesoEtapa {

    @Id
    private Long versionPesosId;

    @Id
    private String etapaCodigo;

    private BigDecimal peso;
    private Instant creadoEn;

    @Getter @Setter @NoArgsConstructor @AllArgsConstructor @EqualsAndHashCode
    public static class Clave implements Serializable {
        private Long versionPesosId;
        private String etapaCodigo;
    }
}
