package com.renaser.ai.ai_engine.perfilintegral.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.Instant;

// El resultado no es una nota: es un retrato (RF-65). confianzaEvidencia siempre
// obligatoria — le dice al equipo cuánto fiarse del resto del perfil.
@Entity
@Table(name = "perfil_talento")
@Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
public class PerfilTalento {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long postulacionId;
    private BigDecimal adecuacion;
    private BigDecimal potencial;
    private BigDecimal altoRendimiento;
    private BigDecimal confianzaEvidencia;
    private String resumen;
    private Long versionPesosId;
    private Long ejecucionIaId;
    private Instant actualizadoEn;
    private Instant creadoEn;
}
