package com.renaser.ai.ai_engine.ai.model;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

// La cola de trabajo del hito 2 de selección. Regla 3 del doc 03 (selección): si la IA
// falla se reintenta, nunca se inventa una nota. referenciaTabla/referenciaId NO son FK:
// cada agente escribe sobre una tabla distinta (postulacion, evaluacion, etc).
@Entity
@Table(name = "trabajo_ia")
@Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
public class TrabajoIa {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long organizacionId;
    private String agenteCodigo;
    // RAPIDA: el modelo contesta sin razonar, para ordenar la tanda entera.
    // FINA: razona, y solo se pide para los de arriba.
    private String modo;
    private Long postulacionId;
    private String referenciaTabla;
    private Long referenciaId;
    private String estado;
    @Builder.Default
    private Integer intentos = 0;
    // Cuándo lo tomó un trabajador: es lo que permite ver si un EN_CURSO se quedó colgado
    private Instant tomadoEn;
    private Instant terminadoEn;
    private Instant creadoEn;
}
