package com.renaser.ai.ai_engine.postulacion.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

// Catálogo cerrado de los 18 estados. Guarda etapa y momento como columnas aparte:
// es lo que permite CALCULAR el siguiente estado en vez de mantener una tabla de
// transiciones a mano. Ver docs/03-ESTADOS-POSTULACION.md.
@Entity
@Table(name = "estado_postulacion")
@Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
public class EstadoPostulacion {

    @Id
    private String codigo;

    private String nombre;
    private String etapaCodigo;
    private String momentoCodigo;
    // CANDIDATO, SISTEMA, TALENTO, AREA o NADIE: es lo que arma la bandeja de trabajo.
    // Nombre explícito: la mayúscula suelta del final confunde a la estrategia de nombres.
    @Column(name = "espera_a")
    private String esperaA;
    private Integer orden;
    private boolean esFinal;
    private Instant creadoEn;
}
