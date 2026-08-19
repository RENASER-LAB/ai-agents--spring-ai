package com.renaser.ai.ai_engine.usuario.entity;

import jakarta.persistence.*;
import lombok.*;

import java.io.Serializable;
import java.time.Instant;

@Entity
@Table(name = "usuario_rol")
@IdClass(UsuarioRol.Clave.class)
@Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
public class UsuarioRol {

    @Id
    private Long usuarioId;

    @Id
    private Long rolId;

    private Long asignadoPorUsuarioId;
    private Instant creadoEn;

    @Getter @Setter @NoArgsConstructor @AllArgsConstructor @EqualsAndHashCode
    public static class Clave implements Serializable {
        private Long usuarioId;
        private Long rolId;
    }
}
