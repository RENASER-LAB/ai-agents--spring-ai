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

    // De qué versión de la plataforma salió esta copia, si salió de una (pieza A).
    private Long copiadaDeVersionId;

    /** NULL = motor de claves versionadas (v0.1 y v3) · CRITERIOS = conteo C1..C4 (CAZATALENTOS). */
    private String metodoCalificacion;

    /** NULL = banco por nivel (plataforma) · con valor = cuestionario técnico de esa vacante. */
    private Long vacanteId;
}
