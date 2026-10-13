package com.renaser.ai.ai_engine.whatsapp.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

/**
 * El vínculo entre el celular de un candidato y una de sus postulaciones (V73).
 *
 * <p>Nace PENDIENTE al pulsar «Escribir por WhatsApp» (EX anuncia el número al bot), pasa a
 * ACTIVO cuando el candidato escribe desde ese número, y se cierra REVOCADO o SUPRIMIDO. Hay
 * como mucho uno vivo por postulación: volver a pulsar renueva el anuncio de la misma fila.
 *
 * <p>{@code referencia} es lo que viaja al bot como {@code postulacionRef}: opaca, ni el id
 * interno ni el uuid del portal.
 */
@Entity
@Table(name = "vinculo_whatsapp")
@Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
public class VinculoWhatsapp {

    public static final String PENDIENTE = "PENDIENTE";
    public static final String ACTIVO = "ACTIVO";
    public static final String REVOCADO = "REVOCADO";
    public static final String SUPRIMIDO = "SUPRIMIDO";

    // Los motivos de cierre son los de /revocaciones en el contrato con el bot, tal cual: el
    // que se guarda es el que viaja.

    /** El candidato pulsó «Dejar de recibir» en el portal. */
    public static final String POR_DEJAR_DE_RECIBIR = "DEJAR_DE_RECIBIR";
    /** Cambió el celular de su cuenta: lo del número anterior se cierra. */
    public static final String POR_CAMBIO_DE_CELULAR = "CAMBIO_CELULAR";
    /** La postulación llegó a un estado final. */
    public static final String POR_PROCESO_TERMINADO = "PROCESO_TERMINADO";
    /** Escribió «BAJA» al bot: el número queda suprimido. No viaja: el bot es quien lo contó. */
    public static final String POR_BAJA = "BAJA";
    /**
     * Se borraron sus datos. No llega a guardarse —las filas se borran—: es el motivo que se le
     * cuenta al bot en la revocación.
     */
    public static final String POR_BORRADO_DE_DATOS = "BORRADO_DATOS";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long postulacionId;
    private Long personaId;
    private String referencia;
    private String telefono;
    private String estado;
    private Long consentimientoId;

    private Instant anunciadoEn;
    private Instant anuncioCaducaEn;
    private Instant anuncioConfirmadoEn;
    private int anuncioIntentos;
    private Instant anuncioProximoIntentoEn;

    private String primerWamid;
    private Instant vinculadoEn;

    private Instant cerradoEn;
    private String motivoCierre;
    private Instant revocacionEnviadaEn;
    private int revocacionIntentos;
    private Instant revocacionProximoIntentoEn;

    private Instant creadoEn;

    /** PENDIENTE o ACTIVO: lo que el índice de «uno vivo por postulación» cuenta. */
    public boolean estaVivo() {
        return PENDIENTE.equals(estado) || ACTIVO.equals(estado);
    }

    public boolean estaActivo() {
        return ACTIVO.equals(estado);
    }

    /** Pendiente y con el anuncio todavía vigente: si escribe ahora, se vincula. */
    public boolean esperaSuMensaje(Instant ahora) {
        return PENDIENTE.equals(estado) && anuncioCaducaEn != null && anuncioCaducaEn.isAfter(ahora);
    }

    /** Lo cierra con su motivo y deja la revocación para el sondeo (salvo la baja: el bot ya lo sabe). */
    public void cerrar(String nuevoEstado, String motivo, Instant ahora) {
        this.estado = nuevoEstado;
        this.motivoCierre = motivo;
        this.cerradoEn = ahora;
        this.anuncioProximoIntentoEn = null;
        if (POR_BAJA.equals(motivo)) {
            // El bot es quien nos lo contó: no hace falta revocarle nada.
            this.revocacionEnviadaEn = ahora;
            this.revocacionProximoIntentoEn = null;
        } else {
            this.revocacionProximoIntentoEn = ahora;
        }
    }
}
