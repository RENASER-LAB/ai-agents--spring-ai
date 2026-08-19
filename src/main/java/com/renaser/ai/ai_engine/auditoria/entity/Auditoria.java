package com.renaser.ai.ai_engine.auditoria.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;

// Toda acción que cambia una decisión. La base impide UPDATE y DELETE con un trigger,
// y eso no es configurable: no existe la casilla para permitirlo.
@Entity
@Table(name = "auditoria")
@Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
public class Auditoria {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long organizacionId;
    // Null si fue el sistema
    private Long usuarioId;
    private Long rolId;
    private String accion;
    private String entidad;
    private Long entidadId;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private String valorAnterior;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private String valorNuevo;

    private String motivo;
    private Instant ocurridaEn;
    private Instant creadoEn;
}
