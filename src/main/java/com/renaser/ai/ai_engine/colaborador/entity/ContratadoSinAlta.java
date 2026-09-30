package com.renaser.ai.ai_engine.colaborador.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

/** Una contratación que RR.HH. decidió no convertir en ficha. Solo la saca del aviso. */
@Entity
@Table(name = "contratado_sin_alta")
@Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
public class ContratadoSinAlta {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long organizacionId;
    private Long postulacionId;
    private String motivo;
    private Long registradoPorUsuarioId;
    private Instant registradoEn;
}
