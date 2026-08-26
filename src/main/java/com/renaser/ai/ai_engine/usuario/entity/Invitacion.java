package com.renaser.ai.ai_engine.usuario.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

// La única puerta de entrada al panel: el panel no tiene registro público. Se guarda el
// SHA-256 del token, nunca el token; y al revés que el enlace de acceso del candidato,
// esta es de UN SOLO USO — canjearla crea la cuenta, y una cuenta no se crea dos veces.
@Entity
@Table(name = "invitacion")
@Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
public class Invitacion {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long organizacionId;
    private String correo;

    // Los códigos de rol que recibirá la cuenta al nacer, separados por coma. Texto y no
    // FK: los roles son de la organización invitada y la fila nace antes que su gente.
    private String roles;

    private String tokenHash;
    private Instant venceEn;
    private Instant aceptadaEn;
    private Instant revocadaEn;
    private Long creadaPorUsuarioId;
    private Instant creadoEn;

    /** Vigente = ni canjeada, ni revocada, ni vencida. Lo demás es el mismo 401 genérico. */
    public boolean estaVigente(Instant ahora) {
        return aceptadaEn == null && revocadaEn == null && venceEn.isAfter(ahora);
    }
}
