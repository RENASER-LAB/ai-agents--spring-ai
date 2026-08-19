package com.renaser.ai.ai_engine.decision.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

// Lo que ningún promedio alto compensa, definido por vacante. Antes era un catálogo por
// nivel; ahora cada vacante lo define, cargando las del nivel como valores iniciales.
@Entity
@Table(name = "barrera_critica")
@Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
public class BarreraCritica {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long vacanteId;
    private String descripcion;
    private boolean esActiva;
    private Instant creadoEn;
}
