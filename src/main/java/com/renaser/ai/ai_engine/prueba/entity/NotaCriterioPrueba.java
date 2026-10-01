package com.renaser.ai.ai_engine.prueba.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * La nota de la parte calificada de un criterio de la prueba escrita en el editor (V67).
 *
 * <p><b>Por el id del criterio de esa versión</b>, nunca por código ni por nombre. Solo se
 * guarda la parte calificada: la parte automática —las cerradas— se calcula al leer con los
 * puntos que tenga la versión (ver {@code CalificacionDeLaPruebaPropia}).
 *
 * <p>{@code puntaje} es la que vale. Si una persona la ajustó, {@code puntajeIa} guarda la que
 * había puesto la IA y la IA ya no la vuelve a tocar.
 */
@Entity
@Table(name = "nota_criterio_prueba")
@Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
public class NotaCriterioPrueba {

    public static final String IA = "IA";
    public static final String PERSONA = "PERSONA";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long intentoPruebaId;
    private Long criterioBancoId;
    private BigDecimal puntaje;
    private BigDecimal puntajeIa;
    private String explicacion;
    private String evidencia;
    private String origen;
    private BigDecimal confianza;
    private Long ejecucionIaId;
    private Integer versionGuia;
    private Long calificadaPorUsuarioId;
    private Long ajustadaPorUsuarioId;
    private String motivoAjuste;
    private Instant ajustadaEn;
    private Instant creadoEn;

    /** Si una persona la puso o la corrigió: la IA ya no la toca. */
    public boolean ajustadaAMano() {
        return ajustadaPorUsuarioId != null;
    }
}
