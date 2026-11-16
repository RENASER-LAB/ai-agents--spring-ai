package com.renaser.ai.ai_engine.informe.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

/**
 * Los textos que escribió el redactor del informe para una etapa de una postulación (V84): el
 * resumen, una línea por criterio y una por respuesta.
 *
 * <p><b>Solo valen para los datos con los que se escribieron.</b> {@code huella} resume lo que
 * recibió el redactor; si una nota cambia —una recalificación, un ajuste a mano—, la huella de
 * hoy ya no es esa y el informe vuelve a la explicación de la IA hasta que el redactor escriba
 * los nuevos. Así un texto viejo nunca acompaña a una nota nueva.
 *
 * <p>Lo que el código descartó al comprobarlo (una cifra que no estaba en los datos, un «…»)
 * se guarda como ausente: el informe usa ahí la explicación.
 */
@Entity
@Table(name = "texto_informe")
@Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
public class TextoInforme {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long postulacionId;
    private String etapa;
    /** La huella de los datos con los que se escribieron los textos guardados; nula si aún no. */
    private String huella;
    private String resumen;
    /** JSON: nombre exacto del criterio → línea. */
    private String criterios;
    /** JSON: una línea por respuesta, en orden; null donde se descartó. */
    private String preguntas;
    private Integer descartados;
    private Long ejecucionIaId;
    /** La huella de lo último que se le pidió al redactor: no se paga dos veces lo mismo. */
    private String huellaPedida;
    private Instant pedidaEn;
    private Instant escritaEn;
    private Instant creadoEn;
}
