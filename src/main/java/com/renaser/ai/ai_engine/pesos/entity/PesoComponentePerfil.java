package com.renaser.ai.ai_engine.pesos.entity;

import jakarta.persistence.*;
import lombok.*;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.Instant;

// Cómo se reparte el 40% del Perfil Integral: CV 10, psicométrico 5, evaluación 25.
// Son datos y no números en código porque el psicométrico aún no existe y su 5% se reparte.
@Entity
@Table(name = "peso_componente_perfil")
@IdClass(PesoComponentePerfil.Clave.class)
@Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
public class PesoComponentePerfil {

    @Id
    private Long versionPesosId;

    @Id
    private String componente;

    private BigDecimal peso;
    private Instant creadoEn;

    @Getter @Setter @NoArgsConstructor @AllArgsConstructor @EqualsAndHashCode
    public static class Clave implements Serializable {
        private Long versionPesosId;
        private String componente;
    }
}
