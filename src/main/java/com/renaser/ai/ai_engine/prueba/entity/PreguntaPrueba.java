package com.renaser.ai.ai_engine.prueba.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

// El catálogo de preguntas: previas (antes de producir), universales (8-10 elegidas por
// plantilla) y específicas del puesto (3-5). Ya no son 17 fijas para todos (RF-83).
@Entity
@Table(name = "pregunta_prueba")
@Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
public class PreguntaPrueba {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String codigo;
    private String enunciado;
    // PREVIA, UNIVERSAL o ESPECIFICA
    private String tipo;
    private Long puestoId;
    // Qué revela: "Criterio", "Tradeoffs"...
    private String revela;
    private Integer orden;
    private Instant creadoEn;
}
