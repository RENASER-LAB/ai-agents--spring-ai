package com.renaser.ai.ai_engine.postulacion.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

/**
 * Qué recordatorio salió para qué turno de una postulación (V70).
 *
 * <p>Es la memoria que impide repetirlo: el sondeo corre cada minuto y el servidor se reinicia
 * en cada despliegue, y sin esta fila el mismo «aún no has respondido» saldría una y otra vez.
 *
 * <p>El turno es la transición que lo abrió ({@code transicionEstadoId}): si el candidato vuelve
 * a entrar en el mismo estado, es otra transición y otro turno.
 */
@Entity
@Table(name = "recordatorio_enviado")
@Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
public class RecordatorioEnviado {

    /** El de las 24 horas (o las que diga el parámetro) desde que le tocó. Uno por turno. */
    public static final String TRAS_ENTRAR = "TRAS_ENTRAR";
    /** El de antes de que venza. Uno por turno y por fecha: si la fecha se mueve, otro. */
    public static final String ANTES_DEL_PLAZO = "ANTES_DEL_PLAZO";

    /** Salió por correo y campana. */
    public static final String ENVIADO = "ENVIADO";
    /**
     * Ya no sale: el del plazo salió a menos de 12 horas de él, o él caería con el turno ya
     * vencido. Que el del plazo salga antes, sin más, no lo gasta (QA-V70-03).
     */
    public static final String OMITIDO = "OMITIDO";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long postulacionId;
    private Long transicionEstadoId;
    private String tipo;
    /** La fecha que se recordó. Solo en {@link #ANTES_DEL_PLAZO}. */
    private Instant plazoEn;
    private String resultado;
    private Instant creadoEn;
}
