package com.renaser.ai.ai_engine.prueba.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

// Lo que sube o el enlace que pega. Saber cuál de los pedidos es cada uno es lo que
// permite avisar de que falta uno, y que un criterio de la rúbrica apunte a un
// entregable concreto en vez de a "la entrega" en general.
@Entity
@Table(name = "entregable")
@Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
public class Entregable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long intentoPruebaId;
    private Long entregableRequeridoId;
    private Long archivoId;
    private String enlace;
    // Por si entrega varias veces antes de que se acabe el plazo
    private Integer version;
    private Instant subidoEn;
}
