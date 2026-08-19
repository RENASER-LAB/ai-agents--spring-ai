package com.renaser.ai.ai_engine.usuario.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

@Entity
@Table(name = "rol")
@Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
public class Rol {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long organizacionId;
    private String codigo;
    private String nombre;
    private String descripcion;
    // Los cinco iniciales. No se pueden borrar ni renombrar desde la interfaz.
    private boolean esSistema;
    private Instant creadoEn;
}
