package com.renaser.ai.ai_engine.organizacion.entity;

import jakarta.persistence.*;
import lombok.*;

/**
 * El enlace de la empresa en una red (V81). Una fila por empresa y red; una red vacía es una
 * fila que no existe. La red es uno de {@link com.renaser.ai.ai_engine.organizacion.service.RedSocial}.
 */
@Entity
@Table(name = "organizacion_red_social")
@Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
public class OrganizacionRedSocial {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long organizacionId;
    private String red;
    private String enlace;
}
