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
    // SHA-256 del contenido en hexadecimal. Es lo que permite saber que un curriculum
    // identico ya fue leido por la IA y no volver a pagar la lectura. Nullable: los
    // archivos anteriores a la columna no lo tienen, y eso solo significa «leer de nuevo».
    private String contenidoHash;
    private Instant subidoEn;
    private Instant borradoEn;
    private Instant creadoEn;
}
