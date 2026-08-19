package com.renaser.ai.ai_engine.perfilintegral.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

// Una versión publicada del banco. organizacionId vacío = biblioteca global de Renaser.
@Entity
@Table(name = "version_banco")
@Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
public class VersionBanco {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long organizacionId;
    private String tipoBanco;
    private String nivelPuestoCodigo;
    private String etiqueta;
    private String estado;
    private Long publicadaPorUsuarioId;
    private Instant publicadaEn;
    private Instant creadoEn;
}
