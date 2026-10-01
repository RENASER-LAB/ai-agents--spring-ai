package com.renaser.ai.ai_engine.prueba.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

/**
 * Qué entregable mira un criterio de la prueba escrita en el editor (V67).
 *
 * <p>Un entregable puede estar en varios criterios, y todo entregable tiene que estar en al
 * menos uno: así la IA y la persona que califica saben qué leer para cada criterio, y se
 * puede impedir que un criterio de IA dependa solo de enlaces, que la IA no abre.
 */
@Entity
@Table(name = "criterio_banco_entregable")
@Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
public class CriterioBancoEntregable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long criterioBancoId;
    private Long entregableRequeridoId;
    private Instant creadoEn;
}
