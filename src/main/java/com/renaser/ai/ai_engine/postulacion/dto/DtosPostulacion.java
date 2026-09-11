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

    /**
     * La ficha de una postulación, con lo que quien la abre puede hacer con ella.
     *
     * <p>{@code puedeMoverPostulacion} dice si esta petición tiene {@code mover_postulacion}.
     * No es un dato de la postulación: es una facultad de quien pregunta, y viaja aquí por
     * la misma razón que {@code puedeVerPretension} viaja en el ranking —el panel no tiene
     * ninguna otra forma de saber qué permisos trae la sesión, porque el login solo devuelve
     * el token y el id—. Sin él, la única manera de averiguar si se puede descartar a alguien
     * sería intentarlo y leer el 403: enseñar el botón a todo el mundo y que la mitad choque.
     *
     * <p>Es una pista para pintar, NUNCA la defensa: quien decide sigue siendo el
     * {@code @PreAuthorize} de la transición. Un navegador que se invente el {@code true} no
     * gana nada.
     */
    public record FichaPostulacion(Long id, String uuid, String candidato, String correo,
                                   String vacante, String estado, String estadoNombre,
                                   String grupoPrioridad, String motivoCierre,
                                   String resultadoOrgulloso, List<String> enlaces,
                                   Long archivoCvId, Instant creadoEn, Instant movidoEn,
                                   boolean puedeMoverPostulacion) {}

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

    /**
     * Mover una postulación a mano.
     *
     * <p>{@code avisar} nulo significa avisar: es el comportamiento de siempre y el que tiene
     * que salir cuando quien llama no dijo nada. Solo un {@code false} explícito calla el
     * correo, y entonces queda escrito que no se avisó —en el motivo y en la auditoría—, porque
     * si no, un descarte silencioso y uno normal se leen igual seis meses después.
     */
    public record Transicionar(@NotBlank String estadoDestino,
                               @NotBlank String motivo,
                               String motivoCierre,
                               Boolean avisar) {}

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
