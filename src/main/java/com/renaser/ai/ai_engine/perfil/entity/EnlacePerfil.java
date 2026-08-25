package com.renaser.ai.ai_engine.perfil.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

// Un enlace del candidato (LinkedIn, GitHub, portafolio...). El tipo esta en un CHECK de la
// base, y la URL se valida en el servicio: que tenga forma de URL y que la de LinkedIn sea
// de linkedin.com (RF-166) — la base no sabe de dominios.
@Entity
@Table(name = "enlace_perfil")
@Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
public class EnlacePerfil {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long perfilCandidatoId;
    private String tipo;
    private String url;
    private Instant creadoEn;
}
