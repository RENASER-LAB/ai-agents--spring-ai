package com.renaser.ai.ai_engine.resena.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/**
 * Los contratos de las reseñas de empresas (V63): lo que ve la persona, lo que ven las
 * empresas y lo que revisa la plataforma.
 *
 * <p>⚠️ Ninguno de estos datos entra en notas, ranking, pase automático ni IA. Viajan para
 * leerse, y nada más.
 */
public final class DtosResena {

    private DtosResena() {
    }

    // ============ Lo que se comparte: el resumen y la reseña visible ============

    /**
     * El promedio y el reparto de las reseñas VISIBLES: sin las ocultadas por la plataforma.
     *
     * @param promedio con un decimal, redondeado a la mitad hacia arriba (5, 5 y 4 → 4,7);
     *                 nulo si no hay ninguna
     * @param cantidad cuántas reseñas visibles
     * @param reparto  las cinco barras, de 5★ a 1★, con las que tienen 0 incluidas
     */
    public record ResumenResenas(BigDecimal promedio, int cantidad, List<Barra> reparto) {
    }

    /** Una barra del reparto: cuántas reseñas tienen estas estrellas. */
    public record Barra(int estrellas, int cantidad) {
    }

    /**
     * El promedio de una fila del ranking: lo justo para «★ 4,5 (3)» y para ordenar.
     * La fila lo trae nulo cuando la persona no tiene reseñas visibles.
     */
    public record PromedioResenas(BigDecimal promedio, int cantidad) {
    }

    /**
     * Una reseña visible, tal como la lee cualquier empresa en la ficha.
     *
     * @param empresa el nombre ACTUAL de la empresa autora: si cambia de nombre, sus reseñas
     *                dicen el nuevo
     * @param puesto  el título de la vacante de la contratación reseñada
     */
    public record ResenaVisible(Long id, int estrellas, String empresa, String puesto,
                                String texto, Instant publicadaEn, boolean editada,
                                RespuestaVisible respuesta) {
    }

    /** La respuesta de la persona, cuando se ve. Una ocultada no viaja en la lectura. */
    public record RespuestaVisible(String texto, Instant publicadaEn, boolean editada) {
    }

    // ============ El panel: la ficha del postulante ============

    /**
     * Las reseñas de la persona de una postulación, y la de mi empresa si toca.
     *
     * <p>{@code puedeVerResenas} y {@code puedeResenar} existen porque el panel no tiene un
     * endpoint de «mis permisos»: sin ellos el bloque no sabría si pintarse. Son una pista
     * para pintar; quien decide es el backend en cada escritura.
     *
     * @param persona        cómo se llama la persona, para «Respuesta de …»
     * @param resumen        nulo sin {@code ver_resenas_candidato}: sin permiso, los datos no
     *                       viajan
     * @param resenas        las visibles de TODAS las empresas, también la propia, las más
     *                       recientes arriba; nulo sin permiso
     * @param puedeResenar   con {@code resenar_contratado} sobre esta contratación y la
     *                       postulación en {@code CONTRATADO}
     * @param miResena       el bloque de la empresa autora; nulo si {@code puedeResenar} es
     *                       falso
     */
    public record ResenasDeLaPostulacion(
            String persona,
            boolean puedeVerResenas,
            @JsonInclude(JsonInclude.Include.NON_NULL) ResumenResenas resumen,
            @JsonInclude(JsonInclude.Include.NON_NULL) List<ResenaVisible> resenas,
            boolean puedeResenar,
            @JsonInclude(JsonInclude.Include.NON_NULL) LaResenaDeMiEmpresa miResena) {
    }

    /**
     * El bloque «La reseña de [Empresa]» en uno de sus estados.
     *
     * @param estado       {@code AUN_NO_TOCA}, {@code SE_PUEDE_ESCRIBIR}, {@code EDITABLE},
     *                     {@code FIJA} u {@code OCULTADA}
     * @param contratadoEn la transición a {@code CONTRATADO}
     * @param abreEn       desde cuándo se puede escribir: la contratación más 30 días
     * @param resena       la publicada; nula mientras no hay ninguna viva
     */
    public record LaResenaDeMiEmpresa(String estado, String empresa, String puesto,
                                      Instant contratadoEn, Instant abreEn,
                                      ResenaDeMiEmpresa resena) {

        public static final String AUN_NO_TOCA = "AUN_NO_TOCA";
        public static final String SE_PUEDE_ESCRIBIR = "SE_PUEDE_ESCRIBIR";
        public static final String EDITABLE = "EDITABLE";
        public static final String FIJA = "FIJA";
        public static final String OCULTADA = "OCULTADA";
    }

    /**
     * La reseña de mi empresa, con lo que solo la autora ve.
     *
     * @param editableHasta   la primera publicación más 30 días; editarla no lo alarga
     * @param notaOcultacion  la nota de la plataforma si la ocultó
     */
    public record ResenaDeMiEmpresa(Long id, int estrellas, String texto, Instant publicadaEn,
                                    boolean editada, Instant editableHasta,
                                    String notaOcultacion,
                                    RespuestaParaLaAutora respuesta) {
    }

    /**
     * La respuesta de la persona vista por la empresa autora, con el estado de su reporte.
     *
     * @param reporte     {@code EN_REVISION}, {@code MANTENIDA}, {@code OCULTADA} o nulo si
     *                    no hay reporte que contar
     * @param notaReporte la nota de la revisión, cuando la plataforma ya decidió
     * @param puedeReportar si esta empresa puede reportarla ahora: una vez, y otra más si la
     *                      plataforma la mantuvo y la persona la editó después
     */
    public record RespuestaParaLaAutora(String texto, Instant publicadaEn, boolean editada,
                                        boolean ocultada, String reporte, String notaReporte,
                                        boolean puedeReportar) {
    }

    /**
     * Lo que se manda al publicar o editar una reseña.
     *
     * <p>Las estrellas llegan como número y no como entero para poder RECHAZAR un 4,5: un
     * {@code Integer} lo recibiría truncado a 4 sin decir nada.
     */
    public record EscribirResena(BigDecimal estrellas, String texto) {
    }

    // ============ El portal: el perfil de la persona ============

    /** La sección «Reseñas de empresas» del perfil, y la ventana «Ver todas». */
    public record MisResenas(ResumenResenas resumen, List<ResenaMia> resenas) {
    }

    /**
     * Una reseña visible de la persona, con lo que ella puede hacer.
     *
     * @param puedeResponder      si aún no respondió, o borró su respuesta; nunca si la
     *                            plataforma ocultó la suya
     * @param puedeReportar       una vez; otra más si la plataforma la mantuvo y la empresa
     *                            la editó después
     * @param reportadaEnRevision su reporte sigue pendiente: la tarjeta dice «Reportada · en
     *                            revisión»
     */
    public record ResenaMia(Long id, int estrellas, String empresa, String puesto, String texto,
                            Instant publicadaEn, boolean editada, MiRespuesta respuesta,
                            boolean puedeResponder, boolean puedeReportar,
                            boolean reportadaEnRevision) {
    }

    /**
     * Su respuesta, también si la plataforma la ocultó: ella la ve atenuada, con la nota.
     *
     * @param editable      dentro de su plazo y sin ocultar
     * @param editableHasta hasta cuándo; se mueve si la empresa edita la reseña
     */
    public record MiRespuesta(String texto, Instant publicadaEn, boolean editada,
                              Instant editableHasta, boolean editable, boolean ocultada,
                              String notaOcultacion) {
    }

    /** Lo que se manda al responder o editar la respuesta. */
    public record EscribirRespuesta(String texto) {
    }

    /**
     * Un reporte, de la persona o de la empresa autora.
     *
     * @param motivo     {@code OFENSIVA}, {@code DATOS_PERSONALES}, {@code DISCRIMINATORIA},
     *                   {@code FALSA} u {@code OTRO}
     * @param comentario obligatorio con {@code OTRO}; hasta 500 caracteres
     */
    public record Reportar(String motivo, String comentario) {
    }

    // ============ La descarga de datos de la persona ============

    /**
     * Lo que la descarga de datos lleva de las reseñas: las visibles, sus respuestas y sus
     * reportes con el resultado.
     *
     * <p>Una respuesta a una reseña que la plataforma ocultó también va: deja de verse, pero
     * no se borra, y es suya. La reseña ocultada viaja sin su texto, solo para dar contexto.
     */
    public record ResenasEnLaDescarga(List<ResenaDescargada> resenas,
                                      List<ReporteDescargado> reportes) {
    }

    public record ResenaDescargada(String empresa, String puesto, Integer estrellas,
                                   String texto, Instant publicadaEn, Instant editadaEn,
                                   boolean ocultadaPorLaPlataforma,
                                   RespuestaDescargada respuesta) {
    }

    public record RespuestaDescargada(String texto, Instant publicadaEn, Instant editadaEn,
                                      boolean ocultadaPorLaPlataforma, String notaOcultacion) {
    }

    public record ReporteDescargado(String empresa, String motivo, String comentario,
                                    Instant reportadoEn, String estado, Instant resueltoEn,
                                    String notaRevision) {
    }

    // ============ La plataforma: la moderación ============

    /**
     * Una tarjeta de «Reseñas reportadas».
     *
     * @param objeto    {@code RESENA} o {@code RESPUESTA}: qué se juzga
     * @param respuesta la respuesta reportada, cuando el objeto es ella; la reseña va encima
     *                  de contexto
     * @param estado    {@code PENDIENTE}, {@code MANTENIDA}, {@code OCULTADA} o
     *                  {@code RETIRADA}
     */
    public record ReporteParaModerar(Long id, String objeto, String empresa, String persona,
                                     int estrellas, String textoResena, String textoRespuesta,
                                     String motivo, String comentario, Instant reportadoEn,
                                     String estado, Instant resueltoEn, String notaRevision) {
    }

    /** Lo que decide la plataforma: {@code MANTENER} u {@code OCULTAR}, con su nota. */
    public record ResolverReporte(String decision, String nota) {
    }
}
