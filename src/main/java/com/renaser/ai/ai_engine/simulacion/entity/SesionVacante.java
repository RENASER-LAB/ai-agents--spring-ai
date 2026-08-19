package com.renaser.ai.ai_engine.simulacion.entity;

import jakarta.persistence.*;
import lombok.*;

import java.io.Serializable;
import java.time.Instant;

// Para qué vacantes sirve una sesión: una, varias o todas (RF-93). Es lo que permite que el
// candidato solo vea las suyas.
@Entity
@Table(name = "sesion_vacante")
@Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
@IdClass(SesionVacante.Clave.class)
public class SesionVacante {

    @Id
    private Long sesionSimulacionId;
    @Id
    private Long vacanteId;

    private Instant creadoEn;

    @Getter @Setter @NoArgsConstructor @AllArgsConstructor @EqualsAndHashCode
    public static class Clave implements Serializable {
        private Long sesionSimulacionId;
        private Long vacanteId;
    }
}
