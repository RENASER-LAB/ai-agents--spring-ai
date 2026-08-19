package com.renaser.ai.ai_engine.simulacion.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

// Cómo se reparten los minutos de ESTA sesión. Antes era un catálogo global de seis filas;
// ahora cada sesión guarda los suyos, porque RF-96 dice que el reparto es configurable.
@Entity
@Table(name = "tramo_simulacion")
@Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
public class TramoSimulacion {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long sesionSimulacionId;
    // CONTEXTO, PREGUNTAS, EJECUCION, CAMBIO, ENTREGA o CONVERSACION
    private String codigo;
    private String nombre;
    private Integer minutoInicio;
    private Integer minutoFin;
    private Instant creadoEn;
}
