package com.renaser.ai.ai_engine.ai.model;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

// Los 9 agentes de IA del hito 2 de selección (docs/07-DICCIONARIO-DE-DATOS.md §18).
// Catálogo cerrado, sin id: el código es la llave. Vive aquí, junto a AgentRun/AgentType,
// porque son los mismos mecanismos de ejecución de IA aunque el dominio dueño de los datos
// que califican (postulación, evaluación) sea el de selección de personal.
@Entity
@Table(name = "agente")
@Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
public class Agente {

    @Id
    private String codigo;

    private String nombre;
    private String descripcion;
    private Integer version;
    private boolean esActivo;
    private Instant creadoEn;
}
