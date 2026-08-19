package com.renaser.ai.ai_engine.perfilintegral.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

// Los cuatro tipos de hallazgo, que la Regla 1 del doc 03 prohíbe mezclar.
@Entity
@Table(name = "hallazgo_perfil")
@Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
public class HallazgoPerfil {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long perfilTalentoId;
    private String tipo;
    private String descripcion;
    private String evidencia;
    private boolean esCanalizable;
    private String sugerencia;
    private Instant creadoEn;
}
