package com.renaser.ai.ai_engine.notificacion.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

/**
 * Qué texto de correo usa ESTA vacante en lugar del que el sistema mandaría por defecto.
 *
 * <p>{@code avisoCodigo} es el código que la máquina de estados iba a usar —{@code
 * PRUEBA_DISPONIBLE}, {@code POSTULACION_AVANZA}...— y {@code plantillaCodigo} el que se
 * manda en su lugar. Sin fila, sale el de siempre.
 */
@Entity
@Table(name = "plantilla_correo_vacante")
@Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
public class PlantillaCorreoVacante {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long vacanteId;
    private String avisoCodigo;
    private String plantillaCodigo;
    private Instant creadoEn;
}
