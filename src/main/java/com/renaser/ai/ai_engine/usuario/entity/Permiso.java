package com.renaser.ai.ai_engine.usuario.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

// Una acción suelta que se concede o no. Global: no lleva organización y el catálogo
// solo crece con una migración.
@Entity
@Table(name = "permiso")
@Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
public class Permiso {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String codigo;
    private String etiqueta;
    private String grupo;
    private Integer orden;
    private Instant creadoEn;
}
