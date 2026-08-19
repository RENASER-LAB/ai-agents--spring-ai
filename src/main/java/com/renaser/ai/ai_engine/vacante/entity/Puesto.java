package com.renaser.ai.ai_engine.vacante.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

@Entity
@Table(name = "puesto")
@Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
public class Puesto {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long organizacionId;
    private String codigo;
    private String nombre;
    private String nivelPuestoCodigo;
    private String familiaCodigo;
    private boolean esActivo;
    private Instant creadoEn;
}
