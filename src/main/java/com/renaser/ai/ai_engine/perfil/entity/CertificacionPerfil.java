package com.renaser.ai.ai_engine.perfil.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.time.LocalDate;

// Una certificacion o curso. `venceEn` vacio = no caduca; muchas si lo hacen —colegiatura,
// primeros auxilios— y en salud eso decide si alguien puede trabajar o no.
@Entity
@Table(name = "certificacion_perfil")
@Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
public class CertificacionPerfil {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long perfilCandidatoId;
    private String nombre;
    private String entidad;
    private LocalDate emitidaEn;
    private LocalDate venceEn;
    private String origen;
    private Instant confirmadoEn;
    private Instant creadoEn;
}
