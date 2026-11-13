package com.renaser.ai.ai_engine.perfilintegral.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

// Lo que la IA encontró de una línea de «Qué buscar en el CV» en el CV de una persona (V80):
// TIENE (con la frase del CV, que el servidor comprobó), MENCIONA o NO_APARECE. Como mucho uno
// vivo por persona y línea.
@Entity
@Table(name = "resultado_busqueda_cv")
@Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
public class ResultadoBusquedaCv {

    public static final String TIENE = "TIENE";
    public static final String MENCIONA = "MENCIONA";
    public static final String NO_APARECE = "NO_APARECE";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long busquedaCvId;
    private Long postulacionId;
    private Long lineaBusquedaCvId;
    private Integer versionLineas;
    private String estado;
    private String fraseCv;
    private String nota;
    private String pregunta;
    private Long ejecucionIaId;
    private Instant creadoEn;
    private Instant archivadoEn;
}
