package com.renaser.ai.ai_engine.prueba.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

// La prueba de un puesto. Arranca con once plantillas cargadas (RF-78); puestoId vacío
// significa que vale para varios.
@Entity
@Table(name = "plantilla_prueba")
@Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
public class PlantillaPrueba {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long organizacionId;
    private Long puestoId;
    private String nombre;
    private boolean esActiva;
    private Instant creadoEn;
}
