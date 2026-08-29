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
    // Apagado, quien postula no recibe la evaluación del banco: va directo a la bandeja
    // del equipo y la prueba del puesto es su única evaluación (V30)
    @Builder.Default
    private boolean aplicaEvaluacion = true;
    // Cuándo cierra la prueba de esta vacante, para todos. Vacío: se cuentan los días de la
    // versión de la plantilla desde que cada uno empieza, como siempre (V32)
    private Instant pruebaCierraEn;
    // Qué se rinde en la etapa técnica: PLANTILLA (la prueba del puesto de siempre) o
    // CUESTIONARIO_TECNICO (el banco CAZATALENTOS de esta vacante). Uno de los dos, nunca
    // los dos, y se declara: preparar un cuestionario no cambia solo lo que rinde nadie (V44)
    @Builder.Default
    private String instrumentoEtapaTecnica = "PLANTILLA";
    // Minutos del candidato en la etapa técnica. Vacío: los del instrumento elegido (V44)
    private Integer minutosEtapaTecnica;
    private Long responsableUsuarioId;
    private Instant publicadaEn;
    private Instant cerradaEn;
    private Instant creadoEn;
}
