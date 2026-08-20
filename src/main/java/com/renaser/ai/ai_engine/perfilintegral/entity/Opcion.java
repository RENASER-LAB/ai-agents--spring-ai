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

    // --- Banco v3 ---
    /** EF-4: el valor oculto de −2 a +2 que hay detrás de la afirmación. Nunca viaja al portal. */
    private BigDecimal valor;

    /** INV y DE: si el elemento es de los inventados. El candidato no puede distinguirlo. */
    private boolean esDistractor;

    /** SEC: el lugar que le toca a este paso en el orden correcto. */
    private Short ordenCorrecto;
}
