package com.renaser.ai.ai_engine.vacante.entity;

import jakarta.persistence.*;
import lombok.*;

import java.io.Serializable;
import java.time.Instant;

// Una etapa del recorrido de una vacante, con su lugar (V95). La etapa siguiente de una
// postulación sale de aquí y no del orden del catálogo. Solo cambia mientras la vacante no tiene
// postulaciones: ver ServicioEtapasDeLaVacante.
@Entity
@Table(name = "vacante_etapa")
@IdClass(VacanteEtapa.Clave.class)
@Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
public class VacanteEtapa {

    @Id
    private Long vacanteId;

    @Id
    private String etapaCodigo;

    /** El lugar en el recorrido, desde 1. El Perfil Integral siempre es el 1 (trigger de la V95). */
    private Integer orden;

    private Instant creadoEn;

    @Getter @Setter @NoArgsConstructor @AllArgsConstructor @EqualsAndHashCode
    public static class Clave implements Serializable {
        private Long vacanteId;
        private String etapaCodigo;
    }
}
