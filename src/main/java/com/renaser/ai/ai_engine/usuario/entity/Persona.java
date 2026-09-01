package com.renaser.ai.ai_engine.usuario.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.time.LocalDate;

// Quién es alguien: vale para el equipo y para quien postula. Las columnas de identidad
// admiten null porque el borrado de datos las vacía (anonimización, nunca DELETE).
@Entity
@Table(name = "persona")
@Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
public class Persona {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String nombre;
    private String apellidos;
    private String telefono;
    private String documento;
    private LocalDate fechaNacimiento;

    // Dónde vive, como código del catálogo ubigeo. Vive aquí y no en perfil_candidato
    // porque el perfil se crea perezosamente —solo cuando el agente propone datos del
    // currículum— y la ciudad se pide en el registro, cuando esta es la única fila que hay.
    private String ciudadUbigeo;

    private Instant anonimizadoEn;
    private Instant creadoEn;
}
