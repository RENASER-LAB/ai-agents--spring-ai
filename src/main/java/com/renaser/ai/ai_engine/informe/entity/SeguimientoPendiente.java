package com.renaser.ai.ai_engine.informe.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

/**
 * Cómo quedó cada duda abierta tras una etapa de una postulación (V94): el seguimiento de
 * pendientes de la evaluación o de la prueba del puesto.
 *
 * <p><b>Solo vale para los datos con los que se hizo.</b> {@code huella} resume lo que recibió el
 * agente; si cambian —un CV recalificado, una respuesta nueva—, la huella de hoy ya no es esa y
 * el informe vuelve a lo de siempre hasta que llegue el seguimiento nuevo. Así nunca se enseñan
 * estados viejos.
 *
 * <p>{@code dudas} guarda una copia de cada duda (las afirmaciones del CV se borran al
 * recalificarlo) con su estado, su cita y su explicación. Lo que el código descartó al
 * comprobarlo queda como «sigue pendiente», contado en {@code descartados} y explicado en
 * {@code motivos}.
 */
@Entity
@Table(name = "seguimiento_pendiente")
@Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
public class SeguimientoPendiente implements PedidoConHuella {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long postulacionId;
    private String etapa;
    /** La huella de los datos con los que se hizo el seguimiento guardado; nula si aún no. */
    private String huella;
    /** JSON: una entrada por duda seguida, en orden. */
    private String dudas;
    private Integer descartados;
    /** JSON: por qué se descartó cada cosa que devolvió el agente y no pasó la comprobación. */
    private String motivos;
    private Long ejecucionIaId;
    /** La huella de lo último que se pidió: no se paga dos veces lo mismo. */
    private String huellaPedida;
    private Instant pedidaEn;
    private Instant escritaEn;
    private Instant creadoEn;
}
