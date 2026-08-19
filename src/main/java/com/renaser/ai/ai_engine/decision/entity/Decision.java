package com.renaser.ai.ai_engine.decision.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.Instant;

// El semáforo final. Cinco valores, no cuatro: "sin datos" es distinto de rojo -falta
// evidencia, no falla la persona-, y "reserva" es distinto de los dos -la persona vale,
// pero para otra cosa-.
@Entity
@Table(name = "decision")
@Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
public class Decision {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long postulacionId;
    // VERDE, AMBAR, ROJO, SIN_DATOS o RESERVA
    private String semaforo;
    // Orientativa, no decide sola
    private BigDecimal notaGlobal;
    private Long versionPesosId;
    // Vacío mientras la propone el sistema
    private Long decididaPorUsuarioId;
    private String motivo;
    private Instant decididaEn;
    private Instant creadoEn;
}
