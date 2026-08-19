package com.renaser.ai.ai_engine.perfilintegral.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.Instant;

// Los criterios genéricos "para cualquier etapa" que deja preparados el hito 1. Los del
// CV (hito 2) no llevan versionPlantillaPruebaId; los de la prueba (hito 3) sí, pero esa
// tabla todavía no existe, así que la columna va sin FK por ahora.
@Entity
@Table(name = "criterio")
@Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
public class Criterio {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String codigo;
    private String nombre;
    private String descripcion;
    private String etapaCodigo;
    private Long versionPlantillaPruebaId;
    private BigDecimal puntos;
    private String metodoVerificacion;
    private Integer orden;
    private Instant creadoEn;
}
