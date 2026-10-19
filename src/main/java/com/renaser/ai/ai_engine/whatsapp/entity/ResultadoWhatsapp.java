package com.renaser.ai.ai_engine.whatsapp.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

/**
 * El resultado de una postulación que terminó con un vínculo de WhatsApp ACTIVO (V76): lo que el
 * bot puede consultar durante 30 días desde el hecho (contrato v2, C-2).
 *
 * <p>Es la proyección pública mínima: tipo, fecha y revisión. Ni notas, ni el motivo interno del
 * cierre, ni la ficha. El acceso lo retiran la BAJA del número o el cambio del celular de la
 * cuenta ({@code accesoRevocadoEn}); el borrado de datos se lleva la fila.
 */
@Entity
@Table(name = "resultado_whatsapp")
@Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
public class ResultadoWhatsapp {

    /** Lo que dura la consulta del resultado desde el hecho. Una corrección no lo alarga. */
    public static final int DIAS_DE_CONSULTA = 30;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long postulacionId;
    private Long vinculoWhatsappId;
    /** {@code CONTRATADO}, {@code NO_CONTINUA} o {@code CERRADA}: el estado final, tal cual. */
    private String tipo;
    private Instant fecha;
    private int revision;
    private boolean esCorreccion;
    private Instant consultableHasta;
    private Instant accesoRevocadoEn;
    /** {@code BAJA}, {@code CAMBIO_CELULAR} o {@code DEJAR_DE_RECIBIR}. */
    private String accesoRevocadoMotivo;
    private Instant creadoEn;
    private Instant actualizadoEn;

    /** Se puede consultar ahora: sin revocar y dentro de sus 30 días. */
    public boolean esConsultable(Instant ahora) {
        return accesoRevocadoEn == null && consultableHasta != null && consultableHasta.isAfter(ahora);
    }
}
