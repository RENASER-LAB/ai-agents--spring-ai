package com.renaser.ai.ai_engine.vacante.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

// La ficha de vacante del método CAZATALENTOS: las 10 preguntas al dueño y sus salidas.
// Una por vacante. Casi todo admite NULL porque BORRADOR es «a medias mientras el dueño
// la piensa»; qué la vuelve COMPLETA lo decide el servicio.
// Las familias aquí son F1..F7 (diccionarios de textura del método), no el catálogo
// familia de puestos: son taxonomías distintas.
@Entity
@Table(name = "ficha_vacante")
@Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
public class FichaVacante {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long vacanteId;
    private Long organizacionId;

    // Las 10 respuestas del dueño, con sus palabras. Q10 (espejo) solo aplica si ya
    // contrató antes para el puesto.
    private String q1Resultado;
    private String q2Riesgo;
    private String q3DiaReal;
    private String q4EpocaDorada;
    private String q5Estructura;
    private String q6Autonomia;
    private String q7JefeDirecto;
    private String q8LoIncomodo;
    private String q9Requerimientos;
    private String q10Espejo;

    // Lo estructurado que no se parsea de texto libre.
    private Integer genteEnEmpresa;
    private Integer genteACargo;
    // El orden ES la velocidad de daño; lo decide el dueño y manda en el cuestionario.
    private String riesgo1;
    private String riesgo2;
    private String riesgo3;
    private String riesgo4;
    private String eliminatoria1;
    private String eliminatoria2;
    private String requerimiento1;
    private String requerimiento2;
    private String requerimiento3;

    /** F1..F7 en orden de importancia, separadas por coma: 'F4' o 'F4,F1'. */
    private String familias;
    /** Derivado de genteEnEmpresa: ≤30 MICRO · 31–200 MEDIA · 200+ GRANDE. */
    private String tamano;

    private String estado;
    private Instant creadoEn;
    // Contra esto se computa «el cuestionario quedó desactualizado».
    private Instant actualizadoEn;
}
