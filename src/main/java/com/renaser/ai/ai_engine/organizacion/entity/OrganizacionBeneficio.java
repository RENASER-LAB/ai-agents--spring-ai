package com.renaser.ai.ai_engine.organizacion.entity;

import jakarta.persistence.*;
import lombok.*;

/**
 * Un beneficio que la empresa ofrece (V85): un chip de hasta 40 caracteres, hasta 12 por
 * empresa y en su orden. Guardar la presentación los reemplaza todos.
 */
@Entity
@Table(name = "organizacion_beneficio")
@Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
public class OrganizacionBeneficio {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long organizacionId;
    private String texto;
    private int orden;
}
