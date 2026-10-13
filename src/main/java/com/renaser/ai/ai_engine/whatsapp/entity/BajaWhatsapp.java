package com.renaser.ai.ai_engine.whatsapp.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

/**
 * Un número que escribió «BAJA» al bot (V73). La baja es del número, no de una postulación: el
 * portal no vuelve a ofrecer el botón con él.
 */
@Entity
@Table(name = "whatsapp_baja")
@Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
public class BajaWhatsapp {

    @Id
    private String telefono;

    private Instant dadaEn;
}
