package com.renaser.ai.ai_engine.simulacion.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

// El candidato eligió esta fecha.
//
// Dos detalles que no son obvios:
//
// esVigente permite guardar el historial. Si su sesión se cancela, esa inscripción se queda
// como estaba y se crea otra: nada se borra ni se modifica. El único parcial de la base
// garantiza que solo haya una activa.
//
// asistio es TRI-ESTADO. Vacío significa «nadie lo ha marcado todavía», que es distinto de
// «no asistió» — y la diferencia importa, porque de una sale la bandeja de pendientes y de la
// otra sale la vuelta a POR_HABILITAR.
@Entity
@Table(name = "inscripcion_sesion")
@Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
public class InscripcionSesion {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long sesionSimulacionId;
    private Long postulacionId;
    private Instant inscritaEn;
    private Boolean asistio;
    private Long marcadaPorUsuarioId;
    private boolean esVigente;
    private Instant creadoEn;
}
