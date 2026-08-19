package com.renaser.ai.ai_engine.consentimiento.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

@Entity
@Table(name = "consentimiento")
@Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
public class Consentimiento {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long personaId;
    private Long textoConsentimientoId;
    // Cómo se llamaba la persona al aceptar. Es dato personal: se vacía al anonimizar.
    private String nombreRegistrado;
    private Instant aceptadoEn;
    private String ip;
    private String idSesion;
    private String userAgent;
    // Solo aplica al de futuros contactos
    private Instant retiradoEn;
    private Instant creadoEn;
}
