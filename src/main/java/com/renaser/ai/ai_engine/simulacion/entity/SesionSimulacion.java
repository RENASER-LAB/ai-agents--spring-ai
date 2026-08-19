package com.renaser.ai.ai_engine.simulacion.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

// Una fecha con cupo. No hay límite de cuántas se crean, y no hay que publicar exactamente
// dos: el equipo crea las que necesite (RF-91).
@Entity
@Table(name = "sesion_simulacion")
@Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
public class SesionSimulacion {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long organizacionId;
    private Instant fechaHora;
    private Integer duracionMinutos;
    // GRUPAL o INDIVIDUAL. Arranca en grupal y es configurable (RF-92)
    private String modalidad;
    private String lugar;
    // Si es a distancia: la simulación no tiene por qué ser presencial
    private String enlace;
    private Integer cupo;
    // PUBLICADA, LLENA, CANCELADA o TERMINADA
    private String estado;
    private String enunciado;
    private Long creadaPorUsuarioId;
    private Instant creadoEn;
}
