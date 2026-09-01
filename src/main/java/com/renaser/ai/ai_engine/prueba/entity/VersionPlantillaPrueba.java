package com.renaser.ai.ai_engine.prueba.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

// Una versión concreta de la prueba. Si tiene vacanteId es una copia privada de esa
// vacante. El cambio inesperado no tiene minuto fijo: hay un rango
// (minutoCambioMin..Max) y se sortea uno concreto al empezar el intento, para que el
// segundo candidato no lo sepa de antemano.
@Entity
@Table(name = "version_plantilla_prueba")
@Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
public class VersionPlantillaPrueba {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long plantillaPruebaId;
    private Long vacanteId;
    private Integer version;
    private String enunciado;
    private String materiales;
    private String herramientasPermitidas;

    /**
     * El PDF del enunciado, para el correo que avisa de la prueba.
     *
     * <p>Columna propia y no sacado del texto de {@code materiales} con una expresion
     * regular: asi quien reescriba la consigna no rompe el aviso sin enterarse.
     */
    private String urlConsigna;

    /**
     * Lo que esta prueba le dice al agente que la califica: qué mirar, qué pesa en este
     * oficio, qué error descarta.
     *
     * <p><b>Orienta, no sustituye.</b> La rúbrica sigue siendo la fuente del 100 y el agente
     * sigue devolviendo una nota por criterio: las notas se guardan en {@code nota_criterio}
     * por código, así que una guía que pidiera «califica sobre 100» no tendría dónde
     * escribirse. Tampoco cambia los puntos máximos ni qué criterios son de agente.
     *
     * <p>Vive en la versión —y no en la organización ni en la vacante— porque es parte del
     * instrumento: se congela al publicar y viaja con la prueba cuando una empresa se lleva
     * una copia de la plataforma. Ver la V46.
     */
    private String guiaCalificacion;

    // CRONOMETRADA (lo normal) o PLAZO_ABIERTO (solo para las pruebas viejas cargadas tal cual)
    private String modalidad;
    private Integer duracionMinutos;
    private Integer plazoDias;
    private Integer minutoCambioMin;
    private Integer minutoCambioMax;
    private Integer minutosExtra;
    private String estado;
    private Long publicadaPorUsuarioId;
    private Instant publicadaEn;
    private Instant creadoEn;
}
