package com.renaser.ai.ai_engine.postulacion.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

@Entity
@Table(name = "enlace_cv")
@Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
public class EnlaceCv {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long cvId;
    private String url;
    private String tipo;
    private Instant creadoEn;
}
