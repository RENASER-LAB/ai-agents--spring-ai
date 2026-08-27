package com.renaser.ai.ai_engine.perfilintegral.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

// logicaInterna nunca llega al portal. esPuntuable es false en ESTILO y CONSISTENCIA.
@Entity
@Table(name = "pregunta")
@Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
public class Pregunta {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long versionBancoId;
    private String codigo;
    private String bloque;
    private String tipo;
    private String enunciado;
    private String situacion;
    private String logicaInterna;
    private boolean esPuntuable;
    private Integer orden;
    private Instant creadoEn;

    // --- Banco v3 ---
    /** 0 no suma, 1 vale hasta 3 puntos, 2 hasta 6. Vacío en el banco v0.1, que no tenía pesos. */
    private Short peso;

    /** El ítem clave (★): hay que preguntar por él en la entrevista. */
    private boolean esClave;

    /** Descarta al candidato por sí solo, con independencia del puntaje. */
    private boolean esEliminatorio;

    /** CD: cuántos campos tiene cada caso. */
    private Short casosPedidos;

    /** V: si remite a la tabla de tramos de otro ítem, su código. */
    private String rangosDePreguntaCodigo;

    /** V: si en vez de tabla trae la fórmula escrita. */
    private String formulaPuntaje;

    // --- Banco CAZATALENTOS (tipo ABIERTA) ---
    /** Qué debe aparecer para marcar C3 (dato duro) en esta pregunta. Guía del evaluador. */
    private String c3Esperado;

    /** Qué cuenta como C4 (la parte incómoda) en esta pregunta. */
    private String c4Esperado;

    /** Si la respuesta la cumple, el puntaje es 0 y se acaba el cálculo. */
    private String senalDeCero;
}
