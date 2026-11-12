package com.renaser.ai.ai_engine.pesos.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.Instant;

// Una versión de todos los pesos. Publicada nunca se modifica: editar crea otra.
// Cada nota queda atada a la versión con que se calculó.
@Entity
@Table(name = "version_pesos")
@Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
public class VersionPesos {

    /** El peso de «Qué buscar en el CV» cuando nadie dice otro (V80). */
    public static final BigDecimal PESO_BUSQUEDA_CV_POR_DEFECTO = BigDecimal.valueOf(25);

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long organizacionId;
    private String etiqueta;
    private String estado;
    private Long publicadaPorUsuarioId;
    private Instant publicadaEn;
    private Instant creadoEn;

    // De qué versión de la plataforma salió esta copia, si salió de una (pieza A).
    private Long copiadaDeVersionId;

    /**
     * Cuánto pesa «Qué buscar en el CV» dentro de la nota del CV, en %, de 0 a 50 (V80). Igual
     * para todos los niveles; 25 si nadie dice otra cosa. Publicada, no se edita: para cambiarlo
     * se crea otra versión.
     */
    @Builder.Default
    private BigDecimal pesoBusquedaCv = PESO_BUSQUEDA_CV_POR_DEFECTO;
}
