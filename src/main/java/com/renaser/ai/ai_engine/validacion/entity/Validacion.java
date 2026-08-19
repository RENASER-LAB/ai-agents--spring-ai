package com.renaser.ai.ai_engine.validacion.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

/**
 * El periodo de trabajo antes de la decisión final.
 *
 * <p>Dos modalidades, y la diferencia entre ellas no es técnica sino legal:
 *
 * <ul>
 *   <li>{@code SIMULACION_EXTENDIDA} — sin trabajo productivo. No necesita nada especial y se
 *       puede usar desde el primer día (RF-106).
 *   <li>{@code TRABAJO_REAL} — trabajo de verdad. Exige {@code tipoVinculacion} registrado, y
 *       la base lo impide con un CHECK. Es lo que evita que una aceptación digital sustituya
 *       una obligación legal.
 * </ul>
 *
 * <p>{@code finEn} se guarda como fecha concreta y no se calcula, para que el barrido que
 * cierra periodos vencidos sea una consulta directa.
 *
 * <p>Ojo: {@code estado} aquí es el del periodo, distinto del estado de la postulación
 * ({@code VALIDACION_POR_HABILITAR} y compañía). Son dos máquinas paralelas.
 */
@Entity
@Table(name = "validacion")
@Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
public class Validacion {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long postulacionId;
    // SIMULACION_EXTENDIDA o TRABAJO_REAL
    private String modalidad;
    private String tipoVinculacion;
    private Integer dias;
    private Instant inicioEn;
    private Instant finEn;
    // POR_HABILITAR, EN_CURSO o TERMINADA
    private String estado;
    private Long habilitadaPorUsuarioId;
    private Long responsableUsuarioId;
    private Instant creadoEn;
}
