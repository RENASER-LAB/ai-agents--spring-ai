package com.renaser.ai.ai_engine.perfilintegral.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

// La "receta": qué combinación de preguntas arma la evaluación de un nivel y familia.
@Entity
@Table(name = "plantilla_evaluacion")
@Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
public class PlantillaEvaluacion {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long organizacionId;
    private String nombre;
    private String nivelPuestoCodigo;
    private String familiaCodigo;
    private Integer version;
    private String estado;
    private Integer minutosObjetivo;
    private Integer vigenciaMeses;
    private Long publicadaPorUsuarioId;
    private Instant publicadaEn;
    private Instant creadoEn;
}
