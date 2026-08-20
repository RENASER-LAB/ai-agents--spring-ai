package com.renaser.ai.ai_engine.perfilintegral.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

// Un campo de un caso descompuesto (CD): la etiqueta que ve el candidato y, si el
// documento la trae, la regla de validación. El motor de hoy solo cuenta campos con
// algo dentro (casos_pedidos es el denominador); validar contra la regla está pendiente.
@Entity
@Table(name = "campo_caso")
@Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
public class CampoCaso {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long preguntaId;
    private Integer orden;
    private String etiqueta;
    private String validacion;
    private Instant creadoEn;
}
