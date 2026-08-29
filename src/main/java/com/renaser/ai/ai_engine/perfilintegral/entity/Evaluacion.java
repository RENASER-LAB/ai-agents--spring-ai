package com.renaser.ai.ai_engine.perfilintegral.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

// Cuelga del usuario, no de la postulación: se puede reutilizar en otra vacante mientras
// siga vigente (RF-70, fuera del MVP, pero el modelo ya lo admite).
@Entity
@Table(name = "evaluacion")
@Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
public class Evaluacion {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long organizacionId;
    private Long usuarioId;
    /** NULL solo en el cuestionario técnico de una vacante, que no tiene plantilla (V43). */
    private Long plantillaEvaluacionId;
    /**
     * La versión del banco con la que se mide este examen.
     *
     * <p>⚠️ El nombre se quedó corto en la V43: con el cuestionario técnico aquí vive un banco
     * de tipo VACANTE, no uno por nivel. Se dejó así a propósito — renombrarlo tocaría los
     * ocho sitios que ya lo leen sin que ninguno cambie de comportamiento, y el CHECK de la
     * V42 impide confundir los dos tipos.
     */
    private Long versionBancoNivelId;
    /** PERFIL_INTEGRAL (etapa 1) o CUESTIONARIO_TECNICO (etapa 2). Decide de qué columna de
     *  postulación cuelga y qué barrido lo cierra (V43). */
    @Builder.Default
    private String proposito = "PERFIL_INTEGRAL";
    /** Los minutos con los que nació, congelados al crearlo. NULL = los del instrumento (V43). */
    private Integer minutosObjetivo;
    private Long versionBancoAlineacionId;
    private Long reutilizaDeEvaluacionId;
    private String estado;
    private Instant venceEn;
    private Instant iniciadaEn;
    private Instant terminadaEn;
    private Instant vigenteHasta;
    private Instant creadoEn;
}
