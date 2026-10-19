package com.renaser.ai.ai_engine.vacante.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.DynamicUpdate;

import java.math.BigDecimal;
import java.time.Instant;

// Una convocatoria concreta. Toda vacante cuelga de una solicitud aprobada.
// versionPlantillaPruebaId y plantillaEvaluacionId son columnas sueltas sin FK:
// sus tablas llegan en los hitos 2 y 3.
//
// ⚠️ @DynamicUpdate: cada guardado escribe SOLO las columnas que cambió (QA-F05). La vacante
// se configura a trozos —el interruptor del pase, su nota mínima, de dónde salen las
// preguntas, el instrumento, los pesos, la fecha de la prueba, la edición, el sueldo— y cada
// trozo es una petición. Con el UPDATE de la fila entera, dos guardados de columnas distintas
// que coincidían se pisaban: el último en confirmar reescribía la columna del otro con lo
// que leyó antes, y los dos contestaban 200 y quedaban auditados. Así, la mínima recién
// guardada volvía a 60 al elegir «Sin evaluación» en el mismo gesto, y el pase seguía
// encendido tras apagarlo. Las escrituras del panel además bloquean la fila (ver
// VacanteRepository#bloquear); esto cubre a las que no pasan por ahí, vengan de donde vengan.
// Nada depende de reescribir la fila entera: el único trigger sobre vacante (V77) solo fecha
// contextoActualizadoEn cuando cambia de verdad una columna pública, y las únicas escrituras
// por consulta (archivar, desarchivar, eliminar) limpian el contexto al terminar.
@Entity
@DynamicUpdate
@Table(name = "vacante")
@Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
public class Vacante {

    /** Las preguntas salen del banco de la empresa para el nivel del puesto. */
    public static final String ORIGEN_NIVEL = "NIVEL";
    /** Las preguntas son propias de la vacante (V66). */
    public static final String ORIGEN_VACANTE = "VACANTE";

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
    /**
     * La zona o referencia del puesto: el barrio, el distrito o la dirección (V62).
     *
     * <p>Hasta la V62 era «la ubicación» a secas y texto libre —«Lima», «LIMA», «Selva
     * Alegre»—. La ciudad de verdad vive ahora en {@link #ciudadUbigeo}; esta columna no
     * cambia de nombre ni de contenido, solo de papel: es lo que se ve en la ficha detrás de
     * la ciudad, y no se filtra.
     */
    private String ubicacion;
    /**
     * La ciudad del puesto: un código del catálogo {@code ubigeo} —una provincia o
     * {@code EXT}—, el mismo tipo que {@code persona.ciudad_ubigeo} (V62). Vacío = sin
     * ciudad, que es como quedan las vacantes viejas cuyo texto no la nombraba exactamente.
     */
    private String ciudadUbigeo;
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
    /**
     * La nota del Perfil Integral que hace falta para pasar sola a la prueba del puesto, con
     * el interruptor de {@link #calificacionAutomatica} encendido (V72). Entera, de 0 a 100,
     * 60 por defecto. Interna: nunca se publica. Ver
     * {@code perfilintegral.service.UmbralDelPerfilIntegral}.
     */
    @Builder.Default
    private BigDecimal notaMinima = BigDecimal.valueOf(60);
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
    /**
     * De dónde salen las preguntas del Perfil Integral (V66): {@code NIVEL} = el banco de la
     * empresa para el nivel del puesto, {@code VACANTE} = sus preguntas propias.
     * {@code aplicaEvaluacion} sigue siendo el interruptor: apagado, no hay preguntas.
     */
    @Builder.Default
    private String origenPreguntas = ORIGEN_NIVEL;
    /**
     * La versión del banco de RENASER que rinde esta vacante, fija (V74). Solo la tiene una
     * vacante vieja de una empresa sin banco propio que leía el de RENASER: el préstamo se
     * borró y sus candidatos nuevos rinden la misma versión que los de antes. Vacía en el
     * resto. Se suelta si la vacante deja el banco del nivel.
     */
    @Column(name = "version_banco_fija_id")
    private Long versionBancoFijaId;
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
    /**
     * Cuándo se eliminó por borrado lógico. Vacío: la vacante existe (V60).
     *
     * <p>⚠️ <b>Tampoco es un estado, y no es lo mismo que archivar.</b> Una archivada se
     * consulta entera desde «Archivadas»; una eliminada se retira de todas las pantallas —el
     * panel, el tablón, «Mis procesos», los rankings, las exportaciones y los procesos
     * automáticos—, sus postulaciones en carrera se cierran con aviso y su solicitud vuelve a
     * estar libre. Lo que no se pierde es ni una fila: quién la eliminó, cuándo y por qué
     * están en la auditoría, y las postulaciones conservan todo su rastro.
     *
     * <p>No se deshace desde el panel. Quitar esta fecha es cosa de soporte, directamente en
     * la base.
     */
    private Instant eliminadaEn;
    private Instant creadoEn;
    /**
     * Cuándo cambió por última vez lo que la vacante cuenta a quien postula (V77): su ficha
     * pública, sus requisitos indispensables, la empresa, la convocatoria y el procedimiento
     * y los plazos de su prueba. Es una de las fuentes de la vigencia del contexto que consulta
     * el bot de WhatsApp.
     *
     * <p>⚠️ <b>La fecha la base, nunca la aplicación</b>: un trigger la mueve solo cuando
     * cambia de verdad una de esas columnas, venga el cambio de donde venga. Por eso aquí es
     * de solo lectura. Vacía si no cambió nada desde la V77.
     */
    @Column(insertable = false, updatable = false)
    private Instant contextoActualizadoEn;

    @PrePersist
    void antesDeGuardar() {
        if (origenPreguntas == null) {
            origenPreguntas = ORIGEN_NIVEL;
        }
    }

    /** Si sus preguntas son las propias de la vacante (y no el banco del nivel). */
    public boolean tienePreguntasPropias() {
        return ORIGEN_VACANTE.equals(origenPreguntas);
    }
}
