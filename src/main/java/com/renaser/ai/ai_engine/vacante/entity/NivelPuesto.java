package com.renaser.ai.ai_engine.vacante.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

// Catálogo cerrado con PK de texto: en logs y consultas se lee DIRECCION, no un 3.
@Entity
@Table(name = "nivel_puesto")
@Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
public class NivelPuesto {

    @Id
    private String codigo;

    private String nombre;
    private Integer preguntasBanco;
    private Integer minutosObjetivoMin;
    private Integer minutosObjetivoMax;
    private Integer orden;
    private Instant creadoEn;
}
