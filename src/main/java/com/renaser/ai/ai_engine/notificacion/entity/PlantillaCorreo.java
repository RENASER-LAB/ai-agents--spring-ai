package com.renaser.ai.ai_engine.notificacion.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

// Los textos que se envían, versionados. Editar crea una versión nueva.
@Entity
@Table(name = "plantilla_correo")
@Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
public class PlantillaCorreo {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long organizacionId;
    private String codigo;
    private Integer version;
    private String asunto;
    private String cuerpo;
    private boolean esActiva;
    private Instant creadoEn;
}
