package com.renaser.ai.ai_engine.postulacion.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

/**
 * Un enlace que abre el portal sin contraseña, atado a una postulación.
 *
 * <p>Existe porque los candidatos que entran por una carga masiva nunca eligieron una
 * contraseña: el cargador les crea la cuenta con un correo inventado y una clave que no
 * conocen. Sin esto, el aviso que se les manda lleva a una puerta cerrada.
 *
 * <p><b>Aquí no está el token.</b> Solo su SHA-256. El token de verdad existe una sola vez,
 * en el correo que sale, y vale lo mismo que una contraseña: quien lo tenga entra como ese
 * candidato. Ver el porqué en la migración {@code V24__enlace_de_acceso.sql}.
 */
@Entity
@Table(name = "enlace_acceso")
@Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
public class EnlaceAcceso {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long postulacionId;

    private String tokenHash;

    private Instant venceEn;

    private Instant primerUsoEn;

    private Instant ultimoUsoEn;

    private int usos;

    /** Se llena para invalidarlo antes de tiempo, sin borrar la fila. */
    private Instant revocadoEn;

    private Instant creadoEn;

    /**
     * Si ahora mismo se puede usar.
     *
     * <p>Vive en la entidad y no en el servicio porque es la definición de «vigente» y no
     * debe poder contestarse de dos maneras distintas en dos sitios.
     */
    public boolean estaVigente(Instant momento) {
        return revocadoEn == null && venceEn != null && momento.isBefore(venceEn);
    }
}
