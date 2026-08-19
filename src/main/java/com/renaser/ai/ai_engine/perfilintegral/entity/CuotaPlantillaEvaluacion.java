package com.renaser.ai.ai_engine.perfilintegral.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

// Cuántas preguntas de cada tipo/dimensión le tocan a una plantilla.
@Entity
@Table(name = "cuota_plantilla_evaluacion")
@Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
public class CuotaPlantillaEvaluacion {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long plantillaEvaluacionId;
    private String tipoBanco;
    private String tipoPregunta;
    private String dimensionCodigo;
    private Integer cantidadMin;
    private Integer cantidadMax;
    private Instant creadoEn;
}
