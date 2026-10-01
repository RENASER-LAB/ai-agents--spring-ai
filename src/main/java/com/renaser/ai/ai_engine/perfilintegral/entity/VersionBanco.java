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

    /**
     * Cuánto se espera que dure responder este banco. NULL = usar el de la plantilla de
     * evaluación, que es de donde salía antes de la V44: los bancos archivados no lo tienen
     * y sus evaluaciones ya rendidas siguen leyendo el suyo.
     */
    private Integer minutosObjetivo;

    /** NULL = banco por nivel (plataforma) · con valor = un banco de esa vacante. */
    private Long vacanteId;

    /**
     * Solo en los bancos de una vacante: {@code PERFIL_INTEGRAL} (sus preguntas propias) o
     * {@code CUESTIONARIO_TECNICO} (su etapa técnica). Nulo en los bancos por nivel (V66).
     *
     * <p>⚠️ Toda búsqueda del banco de una vacante filtra por aquí: sin eso, el cuestionario
     * técnico podría tomar las preguntas del Perfil Integral.
     */
    private String proposito;

    /** La guía que la empresa escribe para que la IA califique lo abierto (método PUNTOS). */
    private String guiaCalificacion;

    /**
     * Cuántas veces se han corregido las instrucciones de la IA de esta versión, empezando
     * en 1. Cada nota de una abierta guarda con cuál se calculó (V66).
     */
    @Builder.Default
    private Integer versionGuia = 1;

    @PrePersist
    void antesDeGuardar() {
        if (versionGuia == null) {
            versionGuia = 1;
        }
    }
}
