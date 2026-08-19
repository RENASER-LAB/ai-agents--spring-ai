package com.renaser.ai.ai_engine.prueba.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

// Una forma posible del cambio inesperado. Varias variantes por versión evitan que el
// candidato aprenda el patrón hablando con quien ya rindió.
@Entity
@Table(name = "variante_cambio")
@Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
public class VarianteCambio {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long versionPlantillaPruebaId;
    private String texto;
    private Integer orden;
    private Instant creadoEn;
}
