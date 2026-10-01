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
    /** La pregunta del catálogo global (plantillas) o la de la versión del editor (V67). */
    private Long preguntaPruebaId;
    private Long preguntaId;
    private String texto;
    /** La opción marcada en una de opción única o en una escala (V67). */
    private Long opcionId;
    /** Las marcadas en una de opción múltiple: {@code {"marcadas": [12, 14]}} (V67). */
    @org.hibernate.annotations.JdbcTypeCode(org.hibernate.type.SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private String detalle;
    private Instant respondidaEn;
}
