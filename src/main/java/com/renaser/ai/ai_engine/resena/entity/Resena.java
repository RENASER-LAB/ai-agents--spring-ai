package com.renaser.ai.ai_engine.resena.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

/**
 * La opinión de una empresa sobre quien contrató por EX (V63).
 *
 * <p>La firma es de la empresa ({@code organizacionId}); quién la escribió se guarda solo
 * para auditoría. Es de la persona ({@code personaId}) y no de la cuenta: es su reputación.
 *
 * <p>⚠️ <b>No puntúa.</b> Nada de lo que calcula notas, ordena el ranking, decide el pase
 * automático o habla con la IA la lee.
 */
@Entity
@Table(name = "resena")
@Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
public class Resena {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long postulacionId;
    private Long organizacionId;
    private Long personaId;
    private Integer estrellas;
    private String texto;
    private Long escritaPorUsuarioId;
    private Instant publicadaEn;
    private Instant editadaEn;
    private Instant ocultadaEn;
    private String notaOcultacion;
    private Instant borradaEn;

    /** Visible para la persona y para las demás empresas: ni borrada ni ocultada. */
    public boolean esVisible() {
        return borradaEn == null && ocultadaEn == null;
    }
}
