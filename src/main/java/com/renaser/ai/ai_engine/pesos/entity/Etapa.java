package com.renaser.ai.ai_engine.pesos.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

// El catálogo de etapas del embudo. Una etapa nueva se registra con una migración y una pieza que
// cumpla DefinicionDeEtapa (V95): ver docs, «Cómo agregar una etapa».
@Entity
@Table(name = "etapa")
@Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
public class Etapa {

    @Id
    private String codigo;

    private String nombre;
    private Integer orden;
    private Instant creadoEn;

    /** La explicación de la ⓘ (V95). Obligatoria: la base rechaza una etapa sin ella. */
    private String descripcion;

    /** Va siempre primera y no se quita ni se mueve (V95). Solo el Perfil Integral. */
    private boolean fijaPrimera;

    /** Forma parte del recorrido con el que nace una vacante, en el orden de {@code orden} (V95). */
    private boolean enPredeterminado;
}
