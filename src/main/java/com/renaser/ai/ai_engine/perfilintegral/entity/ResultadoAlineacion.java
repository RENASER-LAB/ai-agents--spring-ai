package com.renaser.ai.ai_engine.perfilintegral.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

// El semáforo de los tres bloques de alineación personal (RF-59 a RF-62). Un rojo no
// descarta a nadie por sí solo.
@Entity
@Table(name = "resultado_alineacion")
@Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
public class ResultadoAlineacion {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long evaluacionId;
    private String bloque;
    private String semaforo;
    private String explicacion;
    private Instant creadoEn;
}
