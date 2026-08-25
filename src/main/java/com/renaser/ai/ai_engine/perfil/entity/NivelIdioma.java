package com.renaser.ai.ai_engine.perfil.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

// El marco europeo de idiomas (A1..C2) mas NATIVO. La pantalla explica cada nivel en una
// linea, o la mitad de la gente elegira al azar.
@Entity
@Table(name = "nivel_idioma")
@Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
public class NivelIdioma {

    @Id
    private String codigo;
    private String nombre;
    private Integer orden;
    private Instant creadoEn;
}
