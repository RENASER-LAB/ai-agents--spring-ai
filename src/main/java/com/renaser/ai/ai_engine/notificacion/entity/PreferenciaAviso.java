package com.renaser.ai.ai_engine.notificacion.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

/**
 * Cómo quiere un candidato enterarse de sus procesos (V92): el botón «Notificaciones» de «Mis
 * procesos», con sus tres interruptores y el contador del mensaje flotante.
 *
 * <p>Sin fila vale todo por defecto —ver {@link #porDefecto(Long)}—: correo y campana encendidos,
 * WhatsApp apagado y el mensaje sin mostrar. La fila nace la primera vez que algo cambia.
 */
@Entity
@Table(name = "preferencia_aviso")
@Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
public class PreferenciaAviso {

    /** Las veces que sale como mucho el mensaje flotante que invita a activar WhatsApp. */
    public static final int MAXIMO_DE_VISTAS = 2;

    @Id
    private Long personaId;

    /** Los correos de sus procesos. Los de la cuenta salen siempre. */
    private boolean correo;

    /** Los avisos de sus procesos en la campana. */
    private boolean portal;

    /** El interruptor de WhatsApp: vale para todos sus procesos, los de hoy y los que vengan. */
    private boolean whatsappActivo;

    /** Cuántas veces se pintó el mensaje flotante. La BAJA lo devuelve a cero. */
    private short mensajeVistas;

    /** La última vez que se pintó: vuelve a salir solo tras otra postulación. */
    private Instant mensajeVistoEn;

    private Instant actualizadoEn;

    /** Lo que vale para quien no tiene fila: lo de la migración, sin guardarlo. */
    public static PreferenciaAviso porDefecto(Long personaId) {
        return PreferenciaAviso.builder()
                .personaId(personaId)
                .correo(true)
                .portal(true)
                .whatsappActivo(false)
                .mensajeVistas((short) 0)
                .build();
    }
}
