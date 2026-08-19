package com.renaser.ai.ai_engine.vacante.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

// Las siete familias de trabajo. Catálogo cerrado.
@Entity
@Table(name = "familia")
@Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
public class Familia {

    @Id
    private String codigo;

    private String nombre;
    private String descripcion;
    private Integer orden;
    private Instant creadoEn;
}
