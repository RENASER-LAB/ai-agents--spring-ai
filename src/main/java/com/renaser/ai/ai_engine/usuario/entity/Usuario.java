package com.renaser.ai.ai_engine.usuario.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

// Cómo entra alguien. Candidatos: contraseña propia (BCrypt). Equipo: id de RENASER OS,
// texto suelto sin FK porque son dos servicios separados. Nunca las dos cosas a la vez
// (lo impide un CHECK en la base).
@Entity
@Table(name = "usuario")
@Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
public class Usuario {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long organizacionId;
    private Long personaId;
    private String correo;
    private String contrasenaHash;
    private String usuarioRenaserOsId;
    private Long areaId;
    private boolean esActivo;
    private Instant ultimoAccesoEn;
    private Instant creadoEn;
}
