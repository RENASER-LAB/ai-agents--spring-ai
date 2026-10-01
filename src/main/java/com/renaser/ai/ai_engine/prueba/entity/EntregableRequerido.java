package com.renaser.ai.ai_engine.prueba.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

// Qué cosas distintas tiene que entregar, cada una con su propia regla. Antes era texto
// libre en la plantilla y el sistema no podía decir «falta el video».
@Entity
@Table(name = "entregable_requerido")
@Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
public class EntregableRequerido {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** De una plantilla de prueba (las de siempre) o, desde la V67, de una versión del editor. */
    private Long versionPlantillaPruebaId;
    private Long versionBancoId;
    private String nombre;
    // La regla: «máximo 5 minutos», «máx. 10 diapositivas»
    private String detalle;
    // ARCHIVO, ENLACE o CUALQUIERA
    private String formato;
    private boolean esObligatorio;
    private Integer orden;
    private Instant creadoEn;
    /** «Qué debe tener una buena entrega» (V67): llega a la IA, nunca al portal. */
    private String queDebeTener;
}
