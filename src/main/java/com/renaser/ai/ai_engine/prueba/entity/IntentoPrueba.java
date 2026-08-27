package com.renaser.ai.ai_engine.prueba.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

// Cuando un candidato rinde. El reloj lo lleva el servidor: venceEn se calcula y se
// guarda al empezar, no se recalcula cada vez. No hay pausas —cerrar la página no para
// el reloj—, y cuando se acaba el sistema entrega solo (esEntregaAutomatica).
@Entity
@Table(name = "intento_prueba")
@Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
public class IntentoPrueba {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long postulacionId;
    private Long versionPlantillaPruebaId;
    private Instant iniciadoEn;
    private Instant venceEn;
    // A esta persona se le fijó su propia fecha de cierre: mover la de la vacante no se la
    // toca, para que «más horas para este candidato» no se pierda (V32)
    private boolean plazoPropio;
    private Instant entregadoEn;
    private boolean esEntregaAutomatica;
    private Long varianteCambioId;
    private Integer minutoCambio;
    private Instant cambioMostradoEn;
    private Instant creadoEn;
}
