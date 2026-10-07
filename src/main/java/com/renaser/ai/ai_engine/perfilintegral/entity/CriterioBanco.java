package com.renaser.ai.ai_engine.perfilintegral.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

/**
 * Un criterio de las preguntas propias de una vacante (V66): lo que se califica y lo que se
 * ve como columna.
 *
 * <p><b>Sus puntos no se guardan</b>: son la suma de sus preguntas. Tampoco se elige quién lo
 * califica: el sistema sus cerradas y la IA sus abiertas.
 *
 * <p>Se identifica por su id y nunca por el nombre: con criterios por vacante habrá muchos
 * llamados igual. No es la tabla {@code criterio}, que mezcla los del currículum con los de
 * la rúbrica de la prueba.
 */
@Entity
@Table(name = "criterio_banco")
@Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
public class CriterioBanco {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long versionBancoId;
    private String nombre;
    /** Qué evalúa: opcional, hasta 1000 caracteres, y llega a la IA. */
    private String queEvalua;
    private Integer orden;
    private Instant creadoEn;

    /**
     * Solo en la prueba técnica (V67): los puntos de su parte calificada —lo que la IA o una
     * persona califica del criterio entero mirando sus abiertas y sus entregables—. Nulo o 0
     * = el criterio solo tiene cerradas. En el Perfil Integral no se usa.
     */
    private Integer puntosCalificados;

    /** Quién califica la parte calificada: IA o PERSONA. Nulo sin parte calificada. */
    private String calificador;

    /**
     * Solo en la prueba técnica (V69): lo que vale el criterio entero, que es lo que escribe
     * quien la arma. Su parte calificada se deduce —este total menos lo que suman sus
     * cerradas— y por eso el total se mantiene aunque cambien sus cerradas. Nulo en las
     * versiones publicadas antes de la V69, que leen {@link #puntosCalificados} tal cual.
     */
    private Integer puntosDelCriterio;
}
