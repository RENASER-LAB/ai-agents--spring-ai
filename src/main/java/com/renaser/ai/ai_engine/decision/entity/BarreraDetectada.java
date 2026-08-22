package com.renaser.ai.ai_engine.decision.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

// Una barrera crítica encontrada en un candidato concreto. La puede detectar la
// máquina, pero mientras confirmadaPorUsuarioId esté vacío no bloquea a nadie (RF-116).
@Entity
@Table(name = "barrera_detectada")
@Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
public class BarreraDetectada {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long postulacionId;
    private Long barreraCriticaId;
    private String explicacion;
    private Long ejecucionIaId;
    private Long confirmadaPorUsuarioId;
    private Instant confirmadaEn;

    // Si la persona dice que no aplica. Los tres van juntos o ninguno: descartar levanta el
    // único bloqueo que el sistema no deja saltarse, así que sin autor y motivo la barrera
    // desaparecida es indistinguible de una que nunca existió. La base lo exige con un CHECK.
    private Instant descartadaEn;
    private Long descartadaPorUsuarioId;
    private String motivoDescarte;

    private Instant creadoEn;
}
