package com.renaser.ai.ai_engine.prueba.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.List;

// Los contratos de administración de la prueba del puesto. Misma convención que el
// resto: sufijo Response, MapStruct, y las validaciones de formato en el propio record
// para que un valor fuera de catálogo salga en 400, no en un 500 de la base.
public final class DtosPlantillaPrueba {

    private DtosPlantillaPrueba() {}

    /**
     * Hasta dónde llega la guía de calificación de una prueba.
     *
     * <p>Dos mil caracteres son unas trescientas palabras: de sobra para decir qué mirar en
     * una prueba, y poco para esconder dentro una instrucción larga que compita con la del
     * agente. El mismo número está en el CHECK de la V46, que es el que cubre los caminos
     * que no pasan por este contrato —la copia entre organizaciones y las cargas por SQL—,
     * y en {@code ServicioPlantillaPruebaImpl}, que es el que da el mensaje entendible.
     */
    public static final int MAXIMO_GUIA_CALIFICACION = 2000;

    public record CrearPlantilla(@NotBlank String nombre, Long puestoId) {}

    public record PlantillaResponse(Long id, String nombre, Long puestoId, boolean esActiva) {}

    public record CrearVersion(
            @NotBlank String enunciado,
            String materiales,
            String herramientasPermitidas,
            @NotBlank @Pattern(regexp = "CRONOMETRADA|PLAZO_ABIERTO",
                    message = "modalidad debe ser CRONOMETRADA o PLAZO_ABIERTO")
            String modalidad,
            // Vacío en una de plazo abierto, que se mide en días. Si se dice, cinco minutos
            // es el suelo: por debajo la prueba se entrega sola antes de que dé tiempo a
            // leer el enunciado. El techo se retiró — lo que dure lo decide quien la escribe.
            @Min(value = 5, message = "una prueba cronometrada dura al menos 5 minutos")
            Integer duracionMinutos,
            Integer plazoDias,
            Integer minutoCambioMin,
            Integer minutoCambioMax,
            Integer minutosExtra,
            /*
             * Lo que esta prueba le dice al agente que la califica. Opcional: sin guía, el
             * agente califica con su instrucción de siempre, que es lo que hace hoy.
             *
             * ⚠️ Orienta, no sustituye. La rúbrica manda y la nota sigue siendo por
             * criterio; ver el javadoc de VersionPlantillaPrueba.guiaCalificacion.
             */
            @Size(max = MAXIMO_GUIA_CALIFICACION,
                    message = "La guía de calificación no puede pasar de "
                            + MAXIMO_GUIA_CALIFICACION + " caracteres")
            String guiaCalificacion) {}

    /**
     * La versión tal como sale por la API.
     *
     * <p>⚠️ <b>Devuelve todo lo que {@code CrearVersion} escribe, y esa simetría es
     * obligatoria.</b> {@code actualizarVersion} es un PUT que <b>reemplaza</b> la versión
     * entera: lo que no viaje en la petición se guarda en nulo. Un campo que se pudiera
     * escribir pero no leer sería un campo que cualquier edición desde un panel borra sin
     * que nadie lo toque —se carga el formulario con lo que la API da, se guarda, y
     * {@code materiales} desaparece—. {@code materiales}, {@code herramientasPermitidas} y
     * {@code minutosExtra} faltaban aquí exactamente por eso, y por eso están.
     */
    public record VersionResponse(
            Long id, Long plantillaPruebaId, Integer version, String enunciado,
            String materiales, String herramientasPermitidas,
            String modalidad, Integer duracionMinutos, Integer plazoDias,
            Integer minutoCambioMin, Integer minutoCambioMax, Integer minutosExtra,
            String estado, Instant publicadaEn,
            String guiaCalificacion,
            /* El enunciado subido como archivo, el mismo enlace que sale en el correo */
            String urlConsigna) {}

    /**
     * Lo que devuelve subir el enunciado de una versión.
     *
     * <p>{@code expira} viaja a propósito: el enlace lo firma el almacén y caduca, así que
     * el panel puede avisar antes de que un correo salga con un enlace muerto. No lo decide
     * quien sube, lo sabe quien firma.
     */
    public record ConsignaResponse(Long archivoId, String urlConsigna, Instant expira) {}

    public record CrearVariante(@NotBlank String texto) {}

    public record VarianteResponse(Long id, String texto, Integer orden) {}

    public record CrearPreguntaPrueba(
            @NotBlank String codigo, @NotBlank String enunciado,
            @NotBlank @Pattern(regexp = "PREVIA|UNIVERSAL|ESPECIFICA",
                    message = "tipo debe ser PREVIA, UNIVERSAL o ESPECIFICA")
            String tipo,
            Long puestoId, String revela) {}

    public record PreguntaPruebaResponse(
            Long id, String codigo, String enunciado, String tipo, Long puestoId) {}

    public record ElegirPregunta(@NotNull Long preguntaPruebaId) {}

    public record CrearEntregableRequerido(
            @NotBlank String nombre, @NotBlank String detalle,
            @NotBlank @Pattern(regexp = "ARCHIVO|ENLACE|CUALQUIERA",
                    message = "formato debe ser ARCHIVO, ENLACE o CUALQUIERA")
            String formato,
            @NotNull Boolean esObligatorio) {}

    public record EntregableRequeridoResponse(
            Long id, String nombre, String detalle, String formato, boolean esObligatorio) {}

    // La rúbrica reutiliza `criterio`: cada fila declara sus puntos y cómo se verifica.
    // Que sumen 100 se comprueba al publicar (RF-89), no al guardar el borrador.
    public record CrearCriterioRubrica(
            @NotBlank String codigo, @NotBlank String nombre, String descripcion,
            @NotNull Double puntos,
            @NotBlank @Pattern(regexp = "SISTEMA|AGENTE|PERSONA",
                    message = "metodoVerificacion debe ser SISTEMA, AGENTE o PERSONA")
            String metodoVerificacion) {}

    /**
     * Un criterio de la rúbrica, tal como sale por la API.
     *
     * <p>⚠️ {@code descripcion} viaja <b>porque se escribe</b>. Corregir un criterio es un
     * PUT que lo reemplaza entero ({@code CrearCriterioRubrica}), así que un panel que no
     * pudiera leerla no tendría con qué rellenar el formulario: se abriría en blanco y
     * guardar la borraría sin que nadie la tocara. Lo mismo que ya pasó con
     * {@code materiales} y {@code herramientasPermitidas} en {@link VersionResponse}.
     */
    public record CriterioRubricaResponse(
            Long id, String codigo, String nombre, String descripcion, Double puntos,
            String metodoVerificacion) {}

    /**
     * El orden nuevo de una lista de la versión: la lista entera, de una vez.
     *
     * <p>Se manda el todo y no «sube este uno»: así el resultado queda escrito en la propia
     * petición y dos personas ordenando a la vez no dejan la lista a medio mover. Los ids
     * son los de las filas que se ordenan; en las preguntas elegidas, los del catálogo.
     */
    public record ReordenarElementos(@NotEmpty List<Long> idsEnOrden) {}

    public record VersionCompleta(
            VersionResponse version, List<VarianteResponse> variantes,
            List<PreguntaPruebaResponse> preguntas, List<EntregableRequeridoResponse> entregables,
            List<CriterioRubricaResponse> rubrica) {}
}
