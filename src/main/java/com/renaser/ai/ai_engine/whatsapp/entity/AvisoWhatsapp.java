package com.renaser.ai.ai_engine.whatsapp.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.util.UUID;

/**
 * Un aviso «te toca» hacia el bot de WhatsApp (V73): la prueba disponible o un avance en que le
 * toca al candidato.
 *
 * <p>{@code id} es el {@code avisoId} que viaja al bot y su clave de idempotencia: los
 * reintentos mandan el mismo, con el mismo contenido, que se guarda tal cual al encolar.
 * Uno por transición y tipo: la base no deja escribir otro. El de más tiempo (V89) no cuelga de
 * una transición sino de la prueba y la fecha nueva, y también es uno solo por pareja.
 */
@Entity
@Table(name = "aviso_whatsapp")
@Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
public class AvisoWhatsapp {

    public static final String PENDIENTE = "PENDIENTE";
    public static final String ENVIADO = "ENVIADO";
    public static final String FALLIDO = "FALLIDO";
    public static final String OMITIDO = "OMITIDO";

    @Id
    private UUID id;

    private Long postulacionId;
    private Long vinculoWhatsappId;
    /** La transición que lo provocó; vacía solo en {@code PRUEBA_MAS_TIEMPO} (V89). */
    private Long transicionEstadoId;
    /** La prueba a la que se le dio más tiempo: solo en {@code PRUEBA_MAS_TIEMPO} (V89). */
    private Long intentoPruebaId;
    /** La fecha nueva que se le avisa: solo en {@code PRUEBA_MAS_TIEMPO} (V89). */
    private Instant plazoEn;
    private String tipo;
    private String estado;

    private String telefono;
    private String referencia;
    private String nombre;
    private String vacante;
    private String texto;

    private int intentos;
    private Instant proximoIntentoEn;
    private String ultimoError;
    private Instant enviadoEn;
    private Instant creadoEn;
    private Instant actualizadoEn;
}
