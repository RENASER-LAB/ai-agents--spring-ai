package com.renaser.ai.ai_engine.usuario.entity;

import jakarta.persistence.*;
import lombok.*;

import java.io.Serializable;
import java.time.Instant;

// El alcance va aquí y no en el permiso: el mismo permiso puede ser TODO para Talento
// y SUS_VACANTES para el responsable del área.
@Entity
@Table(name = "rol_permiso")
@IdClass(RolPermiso.Clave.class)
@Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
public class RolPermiso {

    @Id
    private Long rolId;

    @Id
    private Long permisoId;

    private String alcance;
    private Instant creadoEn;

    @Getter @Setter @NoArgsConstructor @AllArgsConstructor @EqualsAndHashCode
    public static class Clave implements Serializable {
        private Long rolId;
        private Long permisoId;
    }
}
