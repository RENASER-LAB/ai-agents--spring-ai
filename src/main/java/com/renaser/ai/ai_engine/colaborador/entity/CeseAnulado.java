package com.renaser.ai.ai_engine.colaborador.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.time.LocalDate;

/** Un cese que se deshizo: se copia aquí antes de dejar el periodo abierto otra vez. */
@Entity
@Table(name = "cese_anulado")
@Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
public class CeseAnulado {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long periodoId;
    private LocalDate fechaCese;
    private String motivoCese;
    private String observacionCese;
    private Long registradoPorUsuarioId;
    private Instant registradoEn;
    private Long anuladoPorUsuarioId;
    private Instant anuladoEn;
    private String motivoAnulacion;
}
