package com.renaser.ai.ai_engine.perfil.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

// Catalogo cerrado de niveles de estudio. En tabla y no en CHECK: lo amplia el negocio.
// No confundir con nivel_puesto, que es el nivel del PUESTO, no el de estudios.
@Entity
@Table(name = "nivel_educativo")
@Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
public class NivelEducativo {

    @Id
    private String codigo;
    private String nombre;
    private Integer orden;
    private Instant creadoEn;
}
