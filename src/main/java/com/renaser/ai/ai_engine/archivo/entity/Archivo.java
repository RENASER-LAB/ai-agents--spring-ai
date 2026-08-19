package com.renaser.ai.ai_engine.archivo.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

// Los archivos viven en disco; aquí solo su ruta. Al borrar el archivo la ruta se anula
// pero la fila se conserva: se sabe que existió sin poder recuperarlo.
@Entity
@Table(name = "archivo")
@Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
public class Archivo {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long organizacionId;
    private String ruta;
    private String nombreOriginal;
    private Long tamano;
    private String tipo;
    private Instant subidoEn;
    private Instant borradoEn;
    private Instant creadoEn;
}
