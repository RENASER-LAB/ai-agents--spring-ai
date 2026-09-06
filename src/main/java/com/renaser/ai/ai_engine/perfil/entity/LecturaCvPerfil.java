package com.renaser.ai.ai_engine.perfil.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

/**
 * En qué punto está la lectura del currículum que el candidato subió a <b>su perfil</b>.
 *
 * <p><b>No confundir con {@code cv} ni con {@code dato_cv}</b>, que son de una postulación.
 * Esta lectura no tiene postulación detrás y puede que nunca la tenga: la persona sube su
 * currículum al registrarse y decide postular tres semanas después.
 *
 * <p>Sin fila para esa persona, el estado es {@code SIN_CV}: la ausencia no se guarda.
 *
 * <p>⚠️ <b>Solo puede haber una {@code EN_CURSO} por persona</b>, y lo impone un índice
 * parcial. Cerrar la anterior y crear la nueva en la misma transacción <b>necesita un
 * flush</b> entre medias: Hibernate ordena los INSERT antes que los UPDATE, así que sin él
 * el insert llega cuando la vieja todavía dice {@code EN_CURSO} y revienta.
 */
@Entity
@Table(name = "lectura_cv_perfil")
@Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
public class LecturaCvPerfil {

    /** Los tres que se guardan. El cuarto, {@code SIN_CV}, es no tener fila. */
    public static final String EN_CURSO = "EN_CURSO";
    public static final String LISTA = "LISTA";
    public static final String NO_LEGIBLE = "NO_LEGIBLE";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long personaId;
    private Long archivoId;
    private String estado;
    private Integer intentos;
    /** Por qué no salió nada, para poder contarlo sin que parezca culpa del candidato. */
    private String motivo;
    private Instant creadoEn;
    private Instant terminadoEn;
}
