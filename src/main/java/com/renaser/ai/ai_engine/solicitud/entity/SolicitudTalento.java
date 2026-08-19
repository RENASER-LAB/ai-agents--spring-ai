package com.renaser.ai.ai_engine.solicitud.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.time.LocalDate;

// Por qué hace falta contratar, antes de que exista la vacante.
@Entity
@Table(name = "solicitud_talento")
@Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
public class SolicitudTalento {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long organizacionId;
    private String origen;
    private String urgencia;
    private String estado;
    private Long areaId;
    private String nivelPuestoCodigo;
    private String familiaCodigo;
    private String resultadoPrincipal;
    private String motivo;
    private String consecuenciaNoContratar;
    private LocalDate requeridaPara;
    // Obligatorio siempre: ¿qué parte del trabajo se podría eliminar, automatizar o
    // redistribuir en vez de contratar?
    private String analisisCapacidad;
    private String capacidadesIndispensables;
    private String capacidadesAprendibles;
    private String modalidad;
    private String horario;
    private String compensacion;
    private Long solicitadaPorUsuarioId;
    private Long responsableUsuarioId;
    private Instant creadoEn;
}
