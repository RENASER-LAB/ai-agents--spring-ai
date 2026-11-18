package com.renaser.ai.ai_engine.prueba.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

// Cuando un candidato rinde. El reloj lo lleva el servidor: venceEn se calcula y se
// guarda al empezar, no se recalcula cada vez. No hay pausas —cerrar la página no para
// el reloj—, y cuando se acaba el sistema entrega solo (esEntregaAutomatica).
@Entity
@Table(name = "intento_prueba")
@Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
public class IntentoPrueba {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long postulacionId;
    /**
     * Una de las dos: la plantilla de siempre, o la versión de la prueba escrita en el editor
     * (V67). Con la segunda no se sortea cambio inesperado.
     */
    private Long versionPlantillaPruebaId;
    private Long versionBancoId;
    private Instant iniciadoEn;
    private Instant venceEn;
    // A esta persona se le fijó su propia fecha de cierre: mover la de la vacante no se la
    // toca, para que «más horas para este candidato» no se pierda (V32)
    private boolean plazoPropio;
    private Instant entregadoEn;
    private boolean esEntregaAutomatica;
    private Long varianteCambioId;
    private Integer minutoCambio;
    private Instant cambioMostradoEn;
    private Instant creadoEn;
    /**
     * Venció con algo sin responder (V67, solo la prueba del editor): se cerró sin entregar.
     * {@code entregadoEn} guarda cuándo se cerró. No se califica; desde la V89 sale en el
     * ranking como «No la completó», solo en «Toda la tanda».
     */
    private boolean noCompletada;
    /**
     * La última vez que el equipo le dio más tiempo (V89): cuándo, quién y por qué. Vacías si
     * nunca se le dio. Es lo que la ficha cuenta («más tiempo dado el 18 nov por Ana: …») y la
     * marca de reapertura que mira el recordatorio previo al plazo.
     */
    private Instant masTiempoEn;
    private Long masTiempoPorUsuarioId;
    private String masTiempoMotivo;
    /**
     * Cuándo cambió por última vez lo que el intento cuenta a quien postula: su inicio, su
     * plazo, su entrega o su cierre sin completar (V77). La fechan la base (un DEFAULT al
     * nacer y un trigger al cambiar); aquí solo se lee. Vacía en los intentos anteriores.
     */
    @Column(insertable = false, updatable = false)
    private Instant contextoActualizadoEn;

    /** Si es una prueba escrita en el editor y no una plantilla. */
    public boolean esDelEditor() {
        return versionBancoId != null;
    }

    /**
     * Se le pasó la fecha sin haberla abierto (V89): nunca la empezó, no hay entrega y su fecha
     * ya pasó. El barrido no la cierra —solo cierra las empezadas—, así que se calcula al leer.
     */
    public boolean vencidaSinAbrir(Instant ahora) {
        return iniciadoEn == null && entregadoEn == null && venceEn != null
                && !venceEn.isAfter(ahora);
    }

    /**
     * «No la completó» (V89): se le acabó el tiempo con algo sin responder o sin subir, o se le
     * pasó la fecha sin abrirla. No tiene nota ni la tendrá mientras nadie le dé más tiempo.
     */
    public boolean quedoSinCompletar(Instant ahora) {
        return noCompletada || vencidaSinAbrir(ahora);
    }

    /**
     * Si «Darle más tiempo» tiene algo que reabrir (V89): la que no completó y, además, la que
     * venció empezada y el barrido aún no cerró. Lo segundo es la carrera con el barrido de cada
     * minuto: llegue antes o después que él, el plazo nuevo se aplica igual.
     */
    public boolean seLePuedeDarMasTiempo(Instant ahora) {
        return quedoSinCompletar(ahora)
                || (entregadoEn == null && venceEn != null && !venceEn.isAfter(ahora));
    }
}
