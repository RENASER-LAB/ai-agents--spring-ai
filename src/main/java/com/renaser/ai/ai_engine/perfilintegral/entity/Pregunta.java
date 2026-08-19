package com.renaser.ai.ai_engine.perfilintegral.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

// logicaInterna nunca llega al portal. esPuntuable es false en ESTILO y CONSISTENCIA.
@Entity
@Table(name = "pregunta")
@Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
public class Pregunta {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long versionBancoId;
    private String codigo;
    private String bloque;
    private String tipo;
    private String enunciado;
    private String situacion;
    private String logicaInterna;
    private boolean esPuntuable;
    private Integer orden;
    private Instant creadoEn;
}
