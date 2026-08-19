package com.renaser.ai.ai_engine.perfilintegral.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

// Cuelga del usuario, no de la postulación: se puede reutilizar en otra vacante mientras
// siga vigente (RF-70, fuera del MVP, pero el modelo ya lo admite).
@Entity
@Table(name = "evaluacion")
@Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
public class Evaluacion {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long organizacionId;
    private Long usuarioId;
    private Long plantillaEvaluacionId;
    private Long versionBancoNivelId;
    private Long versionBancoAlineacionId;
    private Long reutilizaDeEvaluacionId;
    private String estado;
    private Instant venceEn;
    private Instant iniciadaEn;
    private Instant terminadaEn;
    private Instant vigenteHasta;
    private Instant creadoEn;
}
