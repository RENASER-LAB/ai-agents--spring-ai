package com.renaser.ai.ai_engine.prueba.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

/**
 * Una pregunta que cubre un entregable general de alcance {@code PREGUNTAS} (V68).
 *
 * <p>Un entregable general reúne las respuestas de varias preguntas —«Informe final» cubre
 * la 2 y la 4— y lo miran los criterios de esas preguntas. Es la mitad de lo que deduce
 * «Mira»: la otra mitad es el archivo de cada pregunta ({@link EntregableRequerido#getPreguntaId()}).
 */
@Entity
@Table(name = "entregable_cubre_pregunta")
@Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
public class EntregableCubrePregunta {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long entregableRequeridoId;
    private Long preguntaId;
    private Instant creadoEn;
}
