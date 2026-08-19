package com.renaser.ai.ai_engine.pesos.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

// Las cinco etapas del embudo. Catálogo cerrado.
@Entity
@Table(name = "etapa")
@Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
public class Etapa {

    @Id
    private String codigo;

    private String nombre;
    private Integer orden;
    private Instant creadoEn;
}
