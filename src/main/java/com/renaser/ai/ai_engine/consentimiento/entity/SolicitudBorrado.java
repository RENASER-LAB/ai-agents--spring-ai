package com.renaser.ai.ai_engine.consentimiento.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

// Pedir el borrado y ejecutarlo son dos cosas distintas, con días de por medio.
@Entity
@Table(name = "solicitud_borrado")
@Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
public class SolicitudBorrado {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long personaId;
    private String motivo;
    private Instant solicitadoEn;
    private Instant ejecutadoEn;
    private Long ejecutadoPorUsuarioId;
    private Instant creadoEn;
}
