package com.renaser.ai.ai_engine.solicitud.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

// Qué dato hizo que el sistema recomendara la solicitud. Nace vacía en el hito 1:
// sin IA no hay sugerencias, pero la tabla queda puesta para no migrar después.
@Entity
@Table(name = "evidencia_necesidad")
@Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
public class EvidenciaNecesidad {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long solicitudTalentoId;
    private String tipo;
    private String descripcion;
    private String valor;
    private Long ejecucionIaId;
    private Instant creadoEn;
}
