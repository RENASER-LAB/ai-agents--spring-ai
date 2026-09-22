package com.renaser.ai.ai_engine.notificacion.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

/**
 * Un aviso esperando al candidato dentro del portal: la otra mitad del correo (V56).
 *
 * <p>El correo sale y no vuelve —cae en promociones, llega a una dirección que el cargador
 * de currículums inventó, se marca leído sin abrir—. Este aviso se queda quieto en la
 * campana hasta que la persona entra y lo ve, y eso lo hace la única forma de enterarse que
 * el sistema puede dar por cumplida.
 *
 * <p>⚠️ <b>El texto va ya armado, como en {@link CorreoEnviado}.</b> Un aviso que se
 * reconstruyera al leerlo diría el sueldo de hoy en lugar del que cambió aquel día: dejaría
 * de ser la noticia para volverse un espejo del estado actual, que es justo lo contrario de
 * para qué sirve.
 */
@Entity
@Table(name = "aviso_portal")
@Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
public class AvisoPortal {

    /** El primero: el sueldo de la vacante cambió (V55). */
    public static final String REMUNERACION_ACTUALIZADA = "REMUNERACION_ACTUALIZADA";

    /**
     * La convocatoria cambió: título, texto, modalidad, horario, ubicación o remuneración.
     *
     * <p>Uno solo por guardado, aunque cambien cinco campos a la vez: quien corrige la
     * vacante hace un cambio, no cinco, y cinco campanas seguidas se leen como un fallo.
     * Lo que cambió va escrito dentro; lo arma {@code CambiosDeLaVacante}.
     */
    public static final String VACANTE_ACTUALIZADA = "VACANTE_ACTUALIZADA";

    /**
     * La empresa retiró la vacante y la postulación quedó cerrada (V60).
     *
     * <p>⚠️ <b>El único que no enlaza a ninguna parte.</b> El proceso al que llevaría ya no
     * se puede abrir —la vacante no existe para el portal—, y un aviso que termina en un 404
     * hace creer al candidato que se rompió algo suyo justo cuando acaba de perder el puesto
     * por algo que no hizo. Por eso se publica con {@code postulacionId} y {@code vacanteId}
     * vacíos, y por eso los avisos <b>anteriores</b> de esa misma vacante también dejan de
     * enlazar cuando se leen.
     */
    public static final String VACANTE_ELIMINADA = "VACANTE_ELIMINADA";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // De quien entra al portal, no de la persona: la campana cuelga de la sesión.
    private Long usuarioId;
    // La organización DE LA VACANTE, igual que la postulación: el aviso lo provoca la
    // empresa que hizo algo, no la plataforma donde vive la cuenta del candidato.
    private Long organizacionId;
    private String tipo;
    private String titulo;
    private String cuerpo;
    // A dónde lleva al pulsarlo. Los dos opcionales: habrá avisos que no cuelguen de
    // ninguna postulación («completa tu perfil» no cuelga de ninguna).
    private Long postulacionId;
    private Long vacanteId;
    // Vacío: sigue contando para el punto. Lo apaga LA PERSONA y no el hecho de abrir: el
    // aviso que pulsa, o todos con el botón de la cabecera. Abrir la campana no marca nada
    // —apagaría con ellos el punto de cada fila de «mis postulaciones» sin que nadie hubiera
    // leído nada—.
    private Instant leidoEn;
    private Instant creadoEn;
}
