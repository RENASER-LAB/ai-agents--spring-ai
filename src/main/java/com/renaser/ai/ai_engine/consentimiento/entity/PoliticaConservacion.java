package com.renaser.ai.ai_engine.consentimiento.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

@Entity
@Table(name = "politica_conservacion")
@Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
public class PoliticaConservacion {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long organizacionId;
    private Integer meses;
    private String accionAlVencer;
    private boolean esActiva;
    private Long definidaPorUsuarioId;
    private Instant creadoEn;
}
