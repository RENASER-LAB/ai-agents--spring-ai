package com.renaser.ai.ai_engine.perfilintegral.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.Instant;

// Qué dos preguntas se comparan para detectar contradicciones. Arranca vacía.
@Entity
@Table(name = "par_consistencia")
@Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
public class ParConsistencia {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long versionBancoId;

    // Dos mayúsculas seguidas ("AId"): la naming strategy no las separa sola.
    @Column(name = "pregunta_a_id")
    private Long preguntaAId;

    @Column(name = "pregunta_b_id")
    private Long preguntaBId;

    // La regla del v0.1: dos respuestas que deberían parecerse se separan más de lo tolerado.
    // Los pares del v3 la traen NULL — su regla son los tres campos de abajo.
    private BigDecimal diferenciaMaxima;

    // --- Banco v3 (V20) ---
    /** Cuánto descuenta del puntaje global si el par se contradice (el documento fija 5%). */
    private BigDecimal penalizacionPorcentaje;

    /** Cuántos ítems tienen que separar a las dos preguntas al armar el examen. */
    private Short separacionMinimaItems;

    /** La regla de contradicción escrita, tal como la trae el documento. */
    private String condicion;

    private Instant creadoEn;
}
