package com.renaser.ai.ai_engine.pesos.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

// Una versión de todos los pesos. Publicada nunca se modifica: editar crea otra.
// Cada nota queda atada a la versión con que se calculó.
@Entity
@Table(name = "version_pesos")
@Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
public class VersionPesos {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long organizacionId;
    private String etiqueta;
    private String estado;
    private Long publicadaPorUsuarioId;
    private Instant publicadaEn;
    private Instant creadoEn;

    // De qué versión de la plataforma salió esta copia, si salió de una (pieza A).
    private Long copiadaDeVersionId;
}
