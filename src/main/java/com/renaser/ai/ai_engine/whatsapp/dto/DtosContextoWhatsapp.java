package com.renaser.ai.ai_engine.whatsapp.dto;

import jakarta.validation.constraints.NotBlank;

import java.time.Instant;
import java.util.List;

/**
 * La consulta v2 del bot (V76): {@code POST /api/v2/integraciones/whatsapp/contexto}. Ver el
 * contrato v2, C-3, en docs/CONTRATO-CONTEXTO-Y-RESULTADOS-POR-WHATSAPP-V2.md.
 *
 * <p>Los campos ausentes viajan como {@code null}, no se omiten: el bot distingue «no hay» de
 * «no lo sé». Los límites de cada texto los corta {@code ServicioContextoWhatsappImpl}.
 */
public final class DtosContextoWhatsapp {

    private DtosContextoWhatsapp() {}

    // ---------- lo que pide el bot ----------

    /**
     * Sin {@code postulacionRef}, el listado (con {@code cursor} para la página siguiente); con
     * ella, el contexto de esa postulación. Las dos a la vez no valen (400 VALIDACION).
     */
    public record PeticionContexto(@NotBlank String telefono, String postulacionRef, String cursor) {}

    // ---------- el listado ----------

    /** {@code distintivo}: un dato público que separa dos títulos iguales, o nulo si no hay. */
    public record VacanteResumida(String titulo, String distintivo) {}

    /**
     * Una postulación consultable de ese número. {@code situacion} es {@code ACTIVA} o
     * {@code FINAL}; {@code resultado}, nulo o {@code CONTRATADO|NO_CONTINUA|CERRADA};
     * {@code convocatoria}, {@code PUBLICADA} o {@code CERRADA}.
     */
    public record Resumen(String postulacionRef, VacanteResumida vacante, String situacion,
                          String resultado, String convocatoria, Instant actualizadaEn) {}

    /** Hasta 9, las más recientes primero; {@code cursor} nulo en la última página. */
    public record Listado(List<Resumen> postulaciones, String cursor, Instant consultadoEn) {}

    // ---------- el contexto de una postulación ----------

    /** {@code PUBLICADA} o {@code CERRADA}, con su fecha y su revisión, aparte del resultado. */
    public record Convocatoria(String estado, Instant cerradaEn, int revision) {}

    /**
     * Un dato de la ficha: {@code PUBLICADO} con su texto, o {@code NO_PUBLICADO},
     * {@code NO_APLICA} o {@code POR_DEFINIR} sin texto. Un dato ausente no es una condición.
     */
    public record Campo(String estado, String texto) {

        public static final Campo NO_PUBLICADO = new Campo("NO_PUBLICADO", null);
    }

    /** La ficha pública: una lista cerrada de campos, nunca la ficha administrativa. */
    public record Ficha(Campo descripcion, Campo proposito, Campo responsabilidades, Campo requisitos,
                       Campo ubicacion, Campo modalidad, Campo horario, Campo remuneracion,
                       Campo tipoContrato, Campo beneficios) {}

    /** {@code ref} es opaca: no es el id de la vacante. */
    public record VacanteDelContexto(String ref, String titulo, String empresa, String distintivo,
                                     Convocatoria convocatoria, Ficha ficha) {}

    /** Una etapa que aplica a esta vacante: {@code HECHA}, {@code ACTUAL} o {@code PREVISTA}. */
    public record Etapa(String codigo, String nombre, String explicacion, int orden, String estado) {}

    /** En qué va, con las palabras del portal ({@code TextosDelMomento}). */
    public record Situacion(String estado, String etapaCodigo, String momento, String titulo,
                            String texto, Instant actualizadaEn) {}

    /**
     * La tarea que tiene ahora, si la hay. Plazos reales o {@code null}: nada se estima.
     * {@code enlace} es el fijo de «Mis procesos» y solo va con {@code HABILITADA} o
     * {@code EN_CURSO}.
     */
    public record Accion(String tipo, String estado, String instrucciones, String ingreso,
                         String enlace, String modalidad, Integer duracionMinutos, Integer plazoDias,
                         Instant empezarAntesDe, Instant iniciadaEn, Instant entregarAntesDe,
                         String zonaHoraria) {}

    /** El resultado confirmado. {@code explicacion} va nula mientras EX no registre una causa pública. */
    public record Resultado(String tipo, Instant fecha, int revision, String explicacion,
                            boolean esCorreccion, Instant consultableHasta) {}

    public record Contexto(String postulacionRef, Instant consultadoEn, Instant actualizadoEn,
                           long revisionContexto, VacanteDelContexto vacante,
                           List<Etapa> procedimiento, Situacion situacion, Accion accion,
                           Resultado resultado) {}
}
