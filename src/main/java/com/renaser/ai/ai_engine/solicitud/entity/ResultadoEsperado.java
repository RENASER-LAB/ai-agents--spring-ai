package com.renaser.ai.ai_engine.solicitud.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

// Los 3 a 5 resultados del cargo, con su indicador. El rango lo comprueba el código.
@Entity
@Table(name = "resultado_esperado")
@Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
public class ResultadoEsperado {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long solicitudTalentoId;
    private String descripcion;
    private String indicador;
    private Integer orden;
    private Instant creadoEn;
}
