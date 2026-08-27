package com.renaser.ai.ai_engine.perfilintegral.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.Instant;

// La calificación de una respuesta abierta. explicacion nunca es opcional (RF-56, RF-150):
// una nota sin explicación no se guarda.
@Entity
@Table(name = "nota_respuesta")
@Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
public class NotaRespuesta {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long respuestaId;
    private BigDecimal puntaje;
    private String explicacion;
    private String evidenciaCitada;
    private BigDecimal confianza;
    private Long ejecucionIaId;
    private Long ajustadaPorUsuarioId;
    private String motivoAjuste;
    private Instant ajustadaEn;
    private Instant creadoEn;

    // --- Banco CAZATALENTOS: los criterios que el agente vio, presentes o ausentes ---
    // El puntaje sale de contarlos en código, no de la aritmética del modelo. Y quedan
    // guardados para que las banderas del cuestionario completo sean consultas, no otra
    // pasada de IA. NULL en las notas de los bancos que no se califican así.
    private Boolean c1Episodio;
    private Boolean c2Autoria;
    private Boolean c3Dato;
    private Boolean c4Incomodidad;
    private Boolean cumpleSenalCero;
}
