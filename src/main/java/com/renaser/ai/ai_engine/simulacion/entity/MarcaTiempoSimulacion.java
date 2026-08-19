package com.renaser.ai.ai_engine.simulacion.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

/**
 * Los momentos <b>observables</b> de una sesión, anotados mientras ocurren.
 *
 * <p>⚠️ Aquí hay una regla que el cliente puso por escrito y que conviene no perder de vista:
 * <b>solo se registra lo que alguien hizo, nunca lo que se supone que pensó</b> (RF-98). El
 * modelo tenía una marca para «cuándo detectó el bloqueo» y se eliminó a propósito. Lo que
 * queda es {@code APARECE_CAMBIO} y {@code ABRE_CAMBIO}: dos actos que se pueden ver.
 *
 * <p>Cada evento ocurre como máximo una vez por inscripción — lo garantiza la base.
 */
@Entity
@Table(name = "marca_tiempo_simulacion")
@Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
public class MarcaTiempoSimulacion {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long inscripcionSesionId;
    private String evento;
    // Con precisión de segundos: de estas marcas salen las preguntas de la conversación final
    private Instant ocurridaEn;
    private Instant creadoEn;
}
