package com.renaser.ai.ai_engine.vacante.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.Instant;

// Una convocatoria concreta. Toda vacante cuelga de una solicitud aprobada.
// versionPlantillaPruebaId y plantillaEvaluacionId son columnas sueltas sin FK:
// sus tablas llegan en los hitos 2 y 3.
@Entity
@Table(name = "vacante")
@Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
public class Vacante {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long organizacionId;
    private Long solicitudTalentoId;
    private Long puestoId;
    private String titulo;
    private String descripcion;
    private String proposito;
    private String responsabilidades;
    private String requisitos;
    private String modalidad;
    private String horario;
    private String ubicacion;
    // Solo si Renaser decide publicarla
    private String compensacionPublica;
    private String tipoCierre;
    private Integer plazas;
    private Instant abreEn;
    private Instant cierraEn;
    private String estado;
    // Interna, nunca se publica
    private BigDecimal notaMinima;
    private Long versionPesosId;
    private Long versionPlantillaPruebaId;
    private Long plantillaEvaluacionId;
    private Long responsableUsuarioId;
    private Instant publicadaEn;
    private Instant cerradaEn;
    private Instant creadoEn;
}
