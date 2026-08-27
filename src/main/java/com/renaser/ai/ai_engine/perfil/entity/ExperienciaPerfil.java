package com.renaser.ai.ai_engine.perfil.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.time.LocalDate;

// Un empleo de la trayectoria. `hasta` vacio significa «sigo aqui».
//
// `origen` y `confirmadoEn` son la regla que mas importa del perfil: lo que la IA leyo del
// curriculum entra como CURRICULUM sin confirmar, y NUNCA pisa una fila PERSONA o confirmada.
@Entity
@Table(name = "experiencia_perfil")
@Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
public class ExperienciaPerfil {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long perfilCandidatoId;
    private String puesto;
    private String empresa;
    private LocalDate desde;
    private LocalDate hasta;
    private String descripcion;
    private String origen;
    private Instant confirmadoEn;
    private Integer orden;
    private Instant creadoEn;
}
