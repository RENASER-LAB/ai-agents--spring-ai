package com.renaser.ai.ai_engine.perfil.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

// Un idioma con su nivel del marco europeo (catalogo nivel_idioma: A1..C2, NATIVO).
// Un idioma por perfil: lo impone la UNIQUE (perfil, idioma).
@Entity
@Table(name = "idioma_perfil")
@Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
public class IdiomaPerfil {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long perfilCandidatoId;
    private String idioma;
    private String nivelCodigo;
    private String origen;
    private Instant confirmadoEn;
    private Instant creadoEn;
}
