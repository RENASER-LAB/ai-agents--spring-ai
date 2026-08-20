package com.renaser.ai.ai_engine.simulacion.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

// Las 3 a 5 preguntas de la conversación final, y qué se respondió.
//
// Salen de las contradicciones entre currículum, evaluación, prueba y simulación —«dijiste que
// avisas los riesgos temprano; aquí lo detectaste a las 10:41 y lo informaste a las 10:49»—.
// Las genera un agente que todavía no existe, así que por ahora se registran a mano; cuando
// exista, rellena esta misma tabla.
//
// riesgoResuelto es lo que evita un módulo de entrevista aparte: con eso y la observación basta.
@Entity
@Table(name = "pregunta_generada")
@Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
public class PreguntaGenerada {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long postulacionId;
    private String texto;
    // El hecho concreto del que sale, en una oración. Lo escribe el agente SIMULACION;
    // las que se registran a mano lo dejan vacío. Sin esto el facilitador recibe una
    // pregunta sin saber por qué se la dan, y no puede repreguntar bien.
    private String motivo;
    // De qué contradicción salió, si salió de una
    private Long alertaId;
    private Long ejecucionIaId;
    private String respuesta;
    private Boolean riesgoResuelto;
    private String observacion;
    private Long registradaPorUsuarioId;
    private Integer orden;
    private Instant creadoEn;
}
