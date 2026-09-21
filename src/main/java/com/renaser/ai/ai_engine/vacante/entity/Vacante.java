package com.renaser.ai.ai_engine.vacante.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.Instant;

// Una convocatoria concreta. Toda vacante cuelga de una solicitud aprobada.
// versionPlantillaPruebaId y plantillaEvaluacionId son columnas sueltas sin FK:
// sus tablas llegan en los hitos 2 y 3.
@Entity
@Table(name = "vacante")
@Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
public class Vacante {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long organizacionId;
    private Long solicitudTalentoId;
    private Long puestoId;
    private String titulo;
    private String descripcion;
    private String proposito;
    private String responsabilidades;
    private String requisitos;
    private String modalidad;
    private String horario;
    private String ubicacion;
    // RETIRADA (V55): el sueldo vive en los campos de remuneración de abajo. Se mantiene
    // mapeada porque la columna sigue existiendo con los datos de las vacantes viejas, pero
    // ninguna pantalla la lee ni la escribe.
    @Deprecated
    private String compensacionPublica;
    /**
     * Qué dice esta vacante sobre el dinero: {@code OCULTA}, {@code FIJA} o {@code RANGO}.
     *
     * <p>⚠️ <b>Es lo que decide si al candidato se le exige declarar su pretensión.</b>
     * Enseñar el sueldo obliga a quien postula a decir el suyo; esconderlo lo libera de
     * hacerlo. Las dos mitades del trato son la misma columna, y cambiarla cambia las reglas
     * de las postulaciones que vengan a partir de ese momento —nunca las de las que ya están
     * hechas—.
     */
    @Builder.Default
    private String remuneracionTipo = "OCULTA";
    // FIJA guarda aquí su único monto y deja max vacío. La base lo hace cumplir (V55).
    private BigDecimal remuneracionMin;
    private BigDecimal remuneracionMax;
    private String remuneracionMoneda;
    // Cuándo se tocó el sueldo por última vez. Vacío: nunca desde que se creó. Es lo que el
    // portal pinta como «actualizado el …» junto al monto.
    private Instant remuneracionActualizadaEn;
    private String tipoCierre;
    private Integer plazas;
    private Instant abreEn;
    private Instant cierraEn;
    private String estado;
    // Interna, nunca se publica
    private BigDecimal notaMinima;
    private Long versionPesosId;
    private Long versionPlantillaPruebaId;
    private Long plantillaEvaluacionId;
    // Apagado, quien postula no recibe la evaluación del banco: va directo a la bandeja
    // del equipo y la prueba del puesto es su única evaluación (V30)
    @Builder.Default
    private boolean aplicaEvaluacion = true;
    // Cuándo cierra la prueba de esta vacante, para todos. Vacío: se cuentan los días de la
    // versión de la plantilla desde que cada uno empieza, como siempre (V32)
    private Instant pruebaCierraEn;
    // Qué se rinde en la etapa técnica: PLANTILLA (la prueba del puesto de siempre) o
    // CUESTIONARIO_TECNICO (el banco CAZATALENTOS de esta vacante). Uno de los dos, nunca
    // los dos, y se declara: preparar un cuestionario no cambia solo lo que rinde nadie (V43)
    @Builder.Default
    private String instrumentoEtapaTecnica = "PLANTILLA";
    // Minutos del candidato en la etapa técnica. Vacío: los del instrumento elegido (V43)
    private Integer minutosEtapaTecnica;
    // Encendido, la postulación viaja sola hasta que termina la prueba del puesto: se
    // califica el currículum al postular (si la vacante no lleva banco), se pasa sola a la
    // etapa técnica al terminar la calificación, y la prueba se califica sola al
    // entregarse. La primera persona que hace falta decide quién va a la simulación (V53)
    @Builder.Default
    private boolean calificacionAutomatica = false;
    private Long responsableUsuarioId;
    private Instant publicadaEn;
    private Instant cerradaEn;
    /**
     * Cuándo se retiró de la lista habitual del panel. Vacío: no archivada (V59).
     *
     * <p>⚠️ <b>No es un estado.</b> Una archivada sigue {@code CERRADA} y conserva sus
     * postulaciones tal como quedaron: lo único que cambia es que deja de salir en
     * {@code /admin} y pasa a consultarse en «Archivadas». Desarchivar la vacía y la
     * devuelve a la lista sin reabrir nada.
     */
    private Instant archivadaEn;
    private Instant creadoEn;
}
