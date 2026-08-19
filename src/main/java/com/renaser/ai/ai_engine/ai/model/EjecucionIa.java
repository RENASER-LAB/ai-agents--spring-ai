package com.renaser.ai.ai_engine.ai.model;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.Instant;

// Cada llamada real al modelo para el hito 2 de selección, auditable. Hermana de AgentRun
// (que cubre los agentes genéricos de RENASER OS): esta es la traza fina por trabajo_ia.
@Entity
@Table(name = "ejecucion_ia")
@Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
public class EjecucionIa {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long trabajoIaId;
    private Long organizacionId;
    private String agenteCodigo;
    private Integer versionAgente;
    private String objetivo;
    private String modelo;
    private String proveedor;
    private String versionModelo;
    private Long instruccionIaId;
    private String envio;
    private String respuesta;
    private BigDecimal confianza;
    private Integer tokensEntrada;
    private Integer tokensSalida;
    private BigDecimal costo;
    private Integer duracionMs;
    private boolean esExitosa;
    private String error;
    private Instant creadoEn;
}
