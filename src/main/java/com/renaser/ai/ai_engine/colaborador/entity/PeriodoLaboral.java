package com.renaser.ai.ai_engine.colaborador.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.time.LocalDate;

/** Un tramo entre un ingreso y un cese. Un reingreso abre otro; los anteriores se quedan. */
@Entity
@Table(name = "periodo_laboral")
@Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
public class PeriodoLaboral {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long colaboradorId;
    private LocalDate fechaIngreso;
    /** El último día trabajado. Hasta ese día inclusive la persona sigue activa. */
    private LocalDate fechaCese;
    private String motivoCese;
    private String observacionCese;
    private Long ceseRegistradoPorUsuarioId;
    private Instant ceseRegistradoEn;
    private Long postulacionId;
    private Long creadoPorUsuarioId;
    private Instant creadoEn;
}
