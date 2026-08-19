package com.renaser.ai.ai_engine.organizacion.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

@Entity
@Table(name = "organizacion")
@Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
public class Organizacion {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String codigo;
    private String nombre;
    private boolean esActiva;
    private Instant creadoEn;
}
