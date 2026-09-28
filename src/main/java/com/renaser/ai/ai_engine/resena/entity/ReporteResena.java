package com.renaser.ai.ai_engine.resena.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

/**
 * Un reporte que revisa la plataforma (V63): de la reseña, que hace la persona, o de la
 * respuesta, que hace la empresa autora.
 */
@Entity
@Table(name = "reporte_resena")
@Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
public class ReporteResena {

    public static final String RESENA = "RESENA";
    public static final String RESPUESTA = "RESPUESTA";

    public static final String PENDIENTE = "PENDIENTE";
    public static final String MANTENIDA = "MANTENIDA";
    public static final String OCULTADA = "OCULTADA";
    public static final String RETIRADA = "RETIRADA";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long resenaId;
    private Long respuestaId;
    private String objeto;
    private Long reportadoPorUsuarioId;
    private Long organizacionReportanteId;
    private String motivo;
    private String comentario;
    private Instant reportadoEn;
    private String estado;
    private Long resueltoPorUsuarioId;
    private Instant resueltoEn;
    private String notaRevision;
}
