package com.renaser.ai.ai_engine.postulacion.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.List;
import java.util.Map;

// Los contratos con que el equipo mira y mueve una postulación.
public final class DtosPostulacion {

    private DtosPostulacion() {}

    public record FilaBandeja(Long postulacionId, String uuid, String candidato, String vacante,
                              String estado, String estadoNombre, String esperaA,
                              String grupoPrioridad, long diasSinCambio) {}

    public record FichaPostulacion(Long id, String uuid, String candidato, String correo,
                                   String vacante, String estado, String estadoNombre,
                                   String grupoPrioridad, String motivoCierre,
                                   String resultadoOrgulloso, List<String> enlaces,
                                   Long archivoCvId, Instant creadoEn, Instant movidoEn) {}

    public record PasoHistorial(String estadoAnterior, String estadoNuevo, Long usuarioId,
                                boolean fueElSistema, boolean fuePorLote, String motivo,
                                Instant ocurridaEn) {}

    /**
     * Corregir el correo o el telefono que la IA leyo mal del curriculum.
     *
     * <p>Los dos van opcionales y se cambia solo lo que llegue: casi siempre falla uno de los
     * dos, y obligar a reescribir el bueno es una invitacion a estropearlo.
     *
     * <p>El motivo es obligatorio y no es burocracia: esto pisa un dato que vino del
     * curriculum de una persona. Si alguien pregunta despues por que su correo dice otra cosa,
     * la respuesta tiene que estar escrita.
     */
    /** El contacto de una ficha, tal como queda tras corregirlo. */
    public record ContactoDelCandidato(Long postulacionId, String nombre,
                                       String email, String telefono) {}

    public record CorregirContacto(
            @Email(message = "Eso no parece un correo")
            @Size(max = 320, message = "El correo es demasiado largo")
            String email,
            @Size(max = 40, message = "El telefono es demasiado largo")
            String telefono,
            @NotBlank(message = "Corregir un dato del curriculum exige un motivo escrito")
            String motivo) {}

    public record Transicionar(@NotBlank String estadoDestino,
                               @NotBlank String motivo,
                               String motivoCierre) {}

    public record ConfirmarAvance(@NotBlank String motivo) {}

    /**
     * Reabrir una evaluación. Los días son opcionales: sin ellos manda el parámetro
     * {@code dias_plazo_evaluacion}, que es donde Renaser lo cambia sin desplegar.
     */
    public record ReabrirEvaluacion(Integer dias, @NotBlank String motivo) {}

    public record ConteoEmbudo(Map<String, Long> porEstado) {}

    /**
     * Por donde bajarse un archivo sin pasar por el backend.
     *
     * @param url      enlace firmado: sirve para este archivo y nada mas
     * @param expiraEn a partir de aqui deja de funcionar. Es corto a proposito: el enlace
     *                 no vuelve a preguntar quien eres, asi que es tan secreto como el
     *                 propio curriculum
     * @param nombre   como llamarlo al guardarlo
     */
    public record EnlaceArchivo(String url, Instant expiraEn, String nombre) {}
}
