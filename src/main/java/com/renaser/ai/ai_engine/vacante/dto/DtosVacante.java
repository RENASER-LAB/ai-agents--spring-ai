package com.renaser.ai.ai_engine.vacante.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.time.Instant;

// Los contratos de vacantes, puestos y requisitos.
public final class DtosVacante {

    private DtosVacante() {}

    public record GuardarVacante(
            @NotNull Long solicitudTalentoId,
            @NotNull Long puestoId,
            @NotBlank String titulo,
            @NotBlank String descripcion,
            String proposito,
            String responsabilidades,
            String requisitos,
            String modalidad,
            String horario,
            String ubicacion,
            String compensacionPublica,
            @NotBlank String tipoCierre,
            Integer plazas,
            Instant abreEn,
            Instant cierraEn,
            @NotNull Long responsableUsuarioId) {}

    // Los últimos cuatro campos son la configuración de la vacante: qué evaluación y qué
    // prueba tiene asignadas, qué pesos la rigen y si la evaluación del banco está encendida.
    // El panel los necesita para enseñar el estado real sin adivinarlo.
    public record VacantePanel(Long id, String titulo, String estado, String tipoCierre,
                               Long puestoId, Long solicitudTalentoId, Long responsableUsuarioId,
                               Instant publicadaEn, Instant cerradaEn, boolean aplicaEvaluacion,
                               Long plantillaEvaluacionId, Long versionPlantillaPruebaId,
                               Long versionPesosId) {}

    public record GuardarRequisito(@NotBlank String descripcion, @NotBlank String regla) {}

    public record RequisitoPanel(Long id, String descripcion, String regla, boolean esActivo) {}

    public record GuardarPuesto(@NotBlank String codigo, @NotBlank String nombre,
                                @NotBlank String nivelPuestoCodigo, @NotBlank String familiaCodigo) {}

    // Listar puestos NO puede devolver `GuardarPuesto`: ese es el cuerpo de entrada y no
    // lleva `id`. Sin el id, quien consulte el catálogo no puede crear una vacante, que
    // pide `puestoId`. Es un contrato de salida y por eso es un record aparte.
    public record PuestoResponse(Long id, String codigo, String nombre,
                                 String nivelPuestoCodigo, String familiaCodigo) {}

    // El cuerpo de cerrar una vacante: el mismo {"motivo": "..."} de siempre. Tiene su propio
    // record y no comparte el de solicitudes para que cada dominio sea dueño de sus contratos.
    public record CerrarVacante(@NotBlank String motivo) {}

    // Qué evaluación responderá quien postule a esta vacante
    public record AsignarPlantilla(@NotNull Long plantillaEvaluacionId) {}

    // Qué versión de la prueba del puesto rendirá quien llegue a esa etapa
    public record AsignarPlantillaPrueba(@NotNull Long versionPlantillaPruebaId) {}

    /**
     * Qué se rinde en la etapa técnica y en cuánto tiempo.
     *
     * <p>`minutos` vacío = los del instrumento elegido, que es lo que hacían todas las
     * vacantes antes de que esto existiera. El valor exacto lo valida el servicio, que es
     * quien conoce los dos instrumentos.
     */
    public record ElegirInstrumentoTecnico(
            @NotBlank String instrumento,
            @Positive(message = "los minutos de la etapa técnica son más de cero")
            Integer minutos) {}

    // Encender o apagar la evaluación del banco para esta vacante. Apagada, quien postula
    // no recibe cuestionario del banco: la prueba del puesto es su única evaluación.
    public record AplicarEvaluacion(@NotNull Boolean aplica) {}

    // Qué versión de pesos rige la decisión de esta vacante. Existe para las vacantes que
    // reparten distinto —una sin banco pone todo en la prueba— sin tocar el reparto general.
    public record AsignarVersionPesos(@NotNull Long versionPesosId) {}

    // Qué texto de correo usa ESTA vacante en lugar del que el sistema mandaría.
    // `avisoCodigo` es el que se sustituye (PRUEBA_DISPONIBLE, POSTULACION_AVANZA...) y
    // `plantillaCodigo` el que sale en su lugar.
    public record AsignarPlantillaCorreo(@NotBlank String avisoCodigo,
                                         @NotBlank String plantillaCodigo) {}

    public record PlantillaCorreoDeVacante(String avisoCodigo, String plantillaCodigo) {}

    // Cuándo cierra la prueba de esta vacante, para todos. `cierraEn` vacío la quita y se
    // vuelve a contar los días de la plantilla desde que cada uno empieza.
    public record DefinirCierrePrueba(Instant cierraEn, @NotBlank String motivo) {}

    // Cuántos intentos abiertos se movieron, y cuántos se dejaron por tener fecha propia.
    public record CierrePruebaResponse(Instant cierraEn, int intentosMovidos,
                                       int intentosConPlazoPropio) {}
}
