package com.renaser.ai.ai_engine.organizacion.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

/** Un local de la empresa (V64). No se borra: se desactiva, y quien ya estaba la conserva. */
@Entity
@Table(name = "sede")
@Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
public class Sede {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long organizacionId;
    private String nombre;
    private String direccion;
    private String provinciaUbigeo;
    private String codigoSunat;
    private boolean esActiva;
    private Instant creadoEn;
}
