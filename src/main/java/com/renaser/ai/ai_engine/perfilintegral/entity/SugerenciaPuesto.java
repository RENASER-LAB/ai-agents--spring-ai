package com.renaser.ai.ai_engine.perfilintegral.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

// Puede sugerir otro puesto o familia, pero la sugerencia no mueve la postulación (RF-67).
@Entity
@Table(name = "sugerencia_puesto")
@Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
public class SugerenciaPuesto {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long perfilTalentoId;
    private Long puestoId;
    private String familiaCodigo;
    private String motivo;
    private Long ejecucionIaId;
    private Instant creadoEn;
}
