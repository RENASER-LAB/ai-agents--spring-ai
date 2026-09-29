package com.renaser.ai.ai_engine.colaborador.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

/**
 * Dónde está alguien y en qué condiciones, desde una fecha (V64).
 *
 * <p>Nada se borra. Un cambio cierra la anterior el día antes; uno con fecha futura queda
 * programado y rige solo cuando llega el día. Anular es solo para los programados, y deja la
 * fila tachada en el historial.
 */
@Entity
@Table(name = "situacion_laboral")
@Getter @Setter @Builder(toBuilder = true) @NoArgsConstructor @AllArgsConstructor
public class SituacionLaboral {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long colaboradorId;
    private Long periodoId;
    private LocalDate vigenteDesde;
    private LocalDate vigenteHasta;
    private Long sedeId;
    private Long areaId;
    private Long puestoId;
    private Long jefeColaboradorId;
    private String tipoContrato;
    private LocalDate finContrato;
    private LocalDate finPeriodoPrueba;
    private String regimenLaboral;
    private BigDecimal sueldoBase;
    private String moneda;
    private String tipoMotivo;
    private String detalleMotivo;
    private Long registradoPorUsuarioId;
    private Instant registradoEn;
    private Instant anuladaEn;
    private Long anuladaPorUsuarioId;
    private String motivoAnulacion;

    /** Solo en las anuladas: la viva que tenía detrás al anularse (V65). */
    @Embedded
    private SituacionDeAntes antesAlAnular;

    public boolean estaAnulada() {
        return anuladaEn != null;
    }

    /** Un tramo vacío: la sustituyó otra del mismo día. Sigue en el historial y nunca rige. */
    public boolean esTramoVacio() {
        return vigenteHasta != null && vigenteHasta.isBefore(vigenteDesde);
    }
}
