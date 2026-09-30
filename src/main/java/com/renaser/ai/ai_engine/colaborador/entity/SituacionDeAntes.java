package com.renaser.ai.ai_engine.colaborador.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Lo que un cambio tenía detrás cuando se anuló (V65): la situación contra la que el historial
 * lo mide para siempre.
 *
 * <p>Se copian los valores y no la fila: los ajustes de solo sueldo se reescriben cuando un
 * cambio anterior los arrastra, y la fila de detrás podría dejar de decir lo que decía.
 *
 * <p>⚠️ Lleva el sueldo: la API no lo envía a quien no tiene {@code ver_sueldos}.
 */
@Embeddable
@Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
public class SituacionDeAntes {

    @Column(name = "antes_sede_id")
    private Long sedeId;
    @Column(name = "antes_area_id")
    private Long areaId;
    @Column(name = "antes_puesto_id")
    private Long puestoId;
    @Column(name = "antes_jefe_colaborador_id")
    private Long jefeColaboradorId;
    @Column(name = "antes_tipo_contrato")
    private String tipoContrato;
    @Column(name = "antes_fin_contrato")
    private LocalDate finContrato;
    @Column(name = "antes_fin_periodo_prueba")
    private LocalDate finPeriodoPrueba;
    @Column(name = "antes_regimen_laboral")
    private String regimenLaboral;
    @Column(name = "antes_sueldo_base")
    private BigDecimal sueldoBase;
    @Column(name = "antes_moneda")
    private String moneda;

    /** La copia de {@code s}; null si no hay nada detrás. */
    public static SituacionDeAntes de(SituacionLaboral s) {
        if (s == null) {
            return null;
        }
        return SituacionDeAntes.builder()
                .sedeId(s.getSedeId()).areaId(s.getAreaId()).puestoId(s.getPuestoId())
                .jefeColaboradorId(s.getJefeColaboradorId()).tipoContrato(s.getTipoContrato())
                .finContrato(s.getFinContrato()).finPeriodoPrueba(s.getFinPeriodoPrueba())
                .regimenLaboral(s.getRegimenLaboral()).sueldoBase(s.getSueldoBase()).moneda(s.getMoneda())
                .build();
    }

    /** Una situación suelta con estos valores, para medir contra ella. No se guarda. */
    public SituacionLaboral comoSituacion() {
        return SituacionLaboral.builder()
                .sedeId(sedeId).areaId(areaId).puestoId(puestoId).jefeColaboradorId(jefeColaboradorId)
                .tipoContrato(tipoContrato).finContrato(finContrato).finPeriodoPrueba(finPeriodoPrueba)
                .regimenLaboral(regimenLaboral).sueldoBase(sueldoBase).moneda(moneda)
                .build();
    }
}
