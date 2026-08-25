package com.renaser.ai.ai_engine.perfil.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.time.LocalDate;

// Un titulo o estudio. El nivel apunta al catalogo nivel_educativo — que no es nivel_puesto:
// aquel es el nivel del PUESTO, este el de estudios.
@Entity
@Table(name = "educacion_perfil")
@Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
public class EducacionPerfil {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long perfilCandidatoId;
    private String titulo;
    private String institucion;
    private String nivelCodigo;
    private LocalDate desde;
    private LocalDate hasta;
    private boolean enCurso;
    private String origen;
    private Instant confirmadoEn;
    private Integer orden;
    private Instant creadoEn;
}
