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
    @Column(name = "q1_resultado")
    private String q1Resultado;
    @Column(name = "q2_riesgo")
    private String q2Riesgo;
    @Column(name = "q3_dia_real")
    private String q3DiaReal;
    @Column(name = "q4_epoca_dorada")
    private String q4EpocaDorada;
    @Column(name = "q5_estructura")
    private String q5Estructura;
    @Column(name = "q6_autonomia")
    private String q6Autonomia;
    @Column(name = "q7_jefe_directo")
    private String q7JefeDirecto;
    @Column(name = "q8_lo_incomodo")
    private String q8LoIncomodo;
    @Column(name = "q9_requerimientos")
    private String q9Requerimientos;
    @Column(name = "q10_espejo")
    private String q10Espejo;

    // Lo estructurado que no se parsea de texto libre.
    @Column(name = "gente_en_empresa")
    private Integer genteEnEmpresa;
    @Column(name = "gente_a_cargo")
    private Integer genteACargo;
    // El orden ES la velocidad de daño; lo decide el dueño y manda en el cuestionario.
    // Con @Column explícito: la estrategia de nombres no separa la cifra final
    // (riesgo1 → riesgo1, no riesgo_1) y la validación del esquema revienta sin esto.
    @Column(name = "riesgo_1")
    private String riesgo1;
    @Column(name = "riesgo_2")
    private String riesgo2;
    @Column(name = "riesgo_3")
    private String riesgo3;
    @Column(name = "riesgo_4")
    private String riesgo4;
    @Column(name = "eliminatoria_1")
    private String eliminatoria1;
    @Column(name = "eliminatoria_2")
    private String eliminatoria2;
    @Column(name = "requerimiento_1")
    private String requerimiento1;
    @Column(name = "requerimiento_2")
    private String requerimiento2;
    @Column(name = "requerimiento_3")
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
