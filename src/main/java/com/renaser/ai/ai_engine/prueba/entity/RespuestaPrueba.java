package com.renaser.ai.ai_engine.prueba.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

// Sus respuestas a las preguntas previas y posteriores de la prueba.
@Entity
@Table(name = "respuesta_prueba")
@Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
public class RespuestaPrueba {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long intentoPruebaId;
    private Long preguntaPruebaId;
    private String texto;
    private Instant respondidaEn;
}
