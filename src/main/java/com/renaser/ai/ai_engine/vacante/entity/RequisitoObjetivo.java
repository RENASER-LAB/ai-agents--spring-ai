package com.renaser.ai.ai_engine.vacante.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

// Lo único que puede detener una postulación sin que intervenga nadie.
@Entity
@Table(name = "requisito_objetivo")
@Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
public class RequisitoObjetivo {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long vacanteId;
    private String descripcion;
    // La comprobación exacta que se aplica: queda guardada en la transición al aplicarse
    private String regla;
    private boolean esActivo;
    private Instant creadoEn;
}
