package com.renaser.ai.ai_engine.usuario.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

// Cómo entra alguien. Candidatos y equipo entran con contraseña propia (BCrypt);
// usuario_renaser_os_id queda como integración dormida para el día que RENASER OS
// vuelva. Lo que separa los dos mundos es es_equipo: sin él, el login del panel
// autenticaría candidatos como equipo, porque ambos tienen correo y contraseña.
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
    private boolean esEquipo;
    private boolean esActivo;
    private Instant ultimoAccesoEn;
    private Instant creadoEn;
}
