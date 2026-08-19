package com.renaser.ai.ai_engine.parametro.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

// Los valores sueltos que Renaser cambia sin programar.
@Entity
@Table(name = "parametro")
@Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
public class Parametro {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long organizacionId;
    private String codigo;
    private String valor;
    private String tipo;
    private String descripcion;
    private Long modificadoPorUsuarioId;
    private Instant modificadoEn;
    private Instant creadoEn;
}
