package com.renaser.ai.ai_engine.perfilintegral.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.Instant;

// La tabla de puntaje de un ítem V: cada fila es un tramo ("Menos de 2 días" → 3.00).
// La condición es texto para una persona, no una expresión evaluable: el motor que la
// interprete está pendiente; mientras tanto los V no se puntúan (puntuarItem → null).
@Entity
@Table(name = "rango_pregunta")
@Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
public class RangoPregunta {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long preguntaId;
    private Integer orden;
    private String condicion;
    private BigDecimal puntaje;

    /** Si caer en este tramo además levanta una bandera (p.ej. "0 puntos y bandera"). */
    private boolean generaBandera;

    private Instant creadoEn;
}
