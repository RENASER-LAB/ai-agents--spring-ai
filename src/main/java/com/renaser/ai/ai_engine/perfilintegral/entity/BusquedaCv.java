package com.renaser.ai.ai_engine.perfilintegral.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

// Cómo acabó la búsqueda de «Qué buscar en el CV» en el CV de una persona (V80), con la versión
// de las líneas que se buscó. TERMINADA trae sus resultados; SIN_TEXTO es el CV del que no sale
// texto, con su motivo. Pendiente, en curso y fallida se leen del trabajo de la cola.
@Entity
@Table(name = "busqueda_cv")
@Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
public class BusquedaCv {

    public static final String TERMINADA = "TERMINADA";
    public static final String SIN_TEXTO = "SIN_TEXTO";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long postulacionId;
    private Integer versionLineas;
    private String estado;
    private String motivo;
    private Long ejecucionIaId;
    private Instant creadoEn;
    private Instant archivadaEn;
}
