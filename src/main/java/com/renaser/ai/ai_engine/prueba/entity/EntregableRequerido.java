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
    /**
     * Su alcance en la prueba del editor (V68): {@link #PREGUNTA}, {@link #TODA_LA_PRUEBA} o
     * {@link #PREGUNTAS}. Nulo en los de antes (con «Mira» marcado a mano) y en las plantillas.
     */
    private String alcance;
    /** La pregunta que pide este archivo, si su alcance es {@link #PREGUNTA}. */
    private Long preguntaId;

    public static final String PREGUNTA = "PREGUNTA";
    public static final String TODA_LA_PRUEBA = "TODA_LA_PRUEBA";
    public static final String PREGUNTAS = "PREGUNTAS";

    /** Un entregable general (de toda la prueba o de unas preguntas), no el de una pregunta. */
    public boolean esGeneral() {
        return TODA_LA_PRUEBA.equals(alcance) || PREGUNTAS.equals(alcance);
    }
}
