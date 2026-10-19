package com.renaser.ai.ai_engine.whatsapp.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.util.UUID;

/**
 * Un evento v2 de EX hacia el bot (V76): {@code RESULTADO} al terminar una postulación o
 * {@code CONVOCATORIA_CERRADA} al cerrar su vacante. Ver el contrato v2, C-4 a C-7.
 *
 * <p>Es la bandeja de salida: se escribe en la misma transacción que el hecho y lo manda después
 * {@code EnvioDeEventos}. {@code id} es el {@code eventoId} y la clave de idempotencia del bot;
 * todo lo que viaja se guarda al escribirlo, así que un reintento lleva exactamente lo mismo.
 */
@Entity
@Table(name = "evento_whatsapp")
@Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
public class EventoWhatsapp {

    public static final String RESULTADO = "RESULTADO";
    public static final String CONVOCATORIA_CERRADA = "CONVOCATORIA_CERRADA";

    public static final String PENDIENTE = "PENDIENTE";
    /** El bot respondió 202. Aceptado, no entregado al WhatsApp de la persona. */
    public static final String ACEPTADO = "ACEPTADO";
    public static final String FALLIDO = "FALLIDO";
    public static final String OMITIDO = "OMITIDO";

    // Los motivos de un evento que no salió, como los nombra el contrato.

    /** Pasaron las 24 horas de la autorización (C-8). */
    public static final String POR_CADUCAR = "autorizacion_caducada";
    /** El número escribió BAJA. */
    public static final String POR_BAJA = "suprimido";
    /** El acceso del vínculo se retiró: dejó de recibir, cambió el celular o el vínculo cerró. */
    public static final String POR_REVOCACION = "revocado";

    @Id
    private UUID id;

    private Long postulacionId;
    private Long vinculoWhatsappId;
    private String tipo;
    /** La revisión del resultado o la de la convocatoria, según el tipo: van separadas. */
    private int revision;
    private String estado;

    private String telefono;
    private String referencia;
    private String nombre;
    private String vacante;
    private Instant ocurridoEn;

    private String resultadoTipo;
    private Instant resultadoFecha;
    private boolean resultadoEsCorreccion;
    private Instant resultadoConsultableHasta;

    private Instant convocatoriaCerradaEn;

    private boolean avisoAutorizado;
    private Instant avisoCaducaEn;
    private boolean cierraVinculo;

    private int intentos;
    private Instant proximoIntentoEn;
    private String ultimoError;
    private String motivo;
    private Instant aceptadoEn;
    private Instant creadoEn;
    private Instant actualizadoEn;

    public boolean esResultado() {
        return RESULTADO.equals(tipo);
    }

    public boolean estaPendiente() {
        return PENDIENTE.equals(estado);
    }
}
