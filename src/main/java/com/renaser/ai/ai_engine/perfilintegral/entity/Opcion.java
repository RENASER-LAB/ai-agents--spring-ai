package com.renaser.ai.ai_engine.perfilintegral.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.Instant;

// puntaje nullable a propósito: en ESTILO no hay clave.
@Entity
@Table(name = "opcion")
@Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
public class Opcion {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long preguntaId;
    private String letra;
    private String texto;
    private BigDecimal puntaje;
    private Instant creadoEn;
}
