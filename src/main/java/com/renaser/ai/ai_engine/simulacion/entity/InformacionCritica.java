package com.renaser.ai.ai_engine.simulacion.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

// Qué debería preguntar un candidato fuerte, qué es opcional y qué tiene que descubrir solo.
//
// Declararlo de antemano es lo que permite calificar la calidad de sus preguntas sin adivinar:
// si no se declara, decir «no preguntó lo importante» es una opinión, no una evidencia (RF-99).
@Entity
@Table(name = "informacion_critica")
@Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
public class InformacionCritica {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long sesionSimulacionId;
    // DEBE_PREGUNTAR, OPCIONAL o DEBE_DESCUBRIR
    private String tipo;
    private String texto;
    private Integer orden;
    private Instant creadoEn;
}
