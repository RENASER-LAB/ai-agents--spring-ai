package com.renaser.ai.ai_engine.vacante.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Min;

import java.math.BigDecimal;
import java.time.Instant;

// Los contratos de vacantes, puestos y requisitos.
public final class DtosVacante {

    private DtosVacante() {}

    public record GuardarVacante(
            @NotNull Long solicitudTalentoId,
            Long puestoId,
            @NotBlank String titulo,
            @NotBlank String descripcion,
            String proposito,
            String responsabilidades,
            String requisitos,
            String modalidad,
            String horario,
            String ubicacion,
            /**
             * Lo que paga, si lo dice (V55).
             *
             * <p>Vacío = {@code OCULTA}, que es como nacen todas las vacantes que no digan lo
             * contrario. No es {@code @NotNull} a propósito: obligar a declarar el sueldo
             * para poder crear un borrador pondría la decisión más delicada del proceso en el
             * primer minuto, cuando todavía no está tomada.
             */
            RemuneracionDeLaVacante remuneracion,
            @NotBlank String tipoCierre,
            /**
             * Cuántas plazas, cuando la forma de cierre es {@code PLAZAS}.
             *
             * <p>Al editar solo se lee si esa es la forma elegida, que es cuando el
             * formulario la enseña. Con cualquier otra forma, lo que haya guardado se
             * conserva —el formulario no lo mostraba— salvo que la forma de cierre acabe de
             * cambiar, y entonces se limpia porque ya no rige nada.
             */
            Integer plazas,
            /**
             * Cuándo abre la convocatoria.
             *
             * <p>⚠️ <b>Solo se lee al CREAR.</b> El formulario de edición no la enseña, así
             * que al editar se conserva la que tenga: tratar su ausencia como un nulo la
             * vaciaba en silencio cada vez que alguien guardaba sin tocar nada.
             */
            Instant abreEn,
            /** Cuándo cierra. Misma regla que {@code plazas}, con la forma {@code FECHA}. */
            Instant cierraEn,
            @NotNull Long responsableUsuarioId,
            /**
             * Por qué cambia el sueldo, cuando este guardado lo cambia.
             *
             * <p>Solo se lee al editar, y solo si la remuneración de verdad cambia: crear una
             * vacante declara lo que paga, no lo cambia, y no hay nadie a quien explicárselo.
             * En una vacante publicada es obligatorio —a cada persona en carrera le va a
             * llegar la noticia, y la auditoría tiene que poder contestar por qué—, y queda
             * como motivo de la fila de auditoría, nunca en el aviso del candidato.
             */
            String motivoRemuneracion) {}

    // Los últimos campos son la configuración de la vacante: qué evaluación y qué prueba
    // tiene asignadas, qué pesos la rigen, si la evaluación del banco está encendida, y qué
    // se rinde en su etapa técnica. El panel los necesita para enseñar el estado real sin
    // adivinarlo — sin `instrumentoEtapaTecnica` no puede pintar qué eligió esta vacante, y
    // tendría que deducirlo mirando si hay un cuestionario publicado, que no es lo mismo.
    public record VacantePanel(Long id, String titulo, String estado, String tipoCierre,
                               Long puestoId, Long solicitudTalentoId, Long responsableUsuarioId,
                               Instant publicadaEn, Instant cerradaEn, boolean aplicaEvaluacion,
                               Long plantillaEvaluacionId, Long versionPlantillaPruebaId,
                               Long versionPesosId, String instrumentoEtapaTecnica,
                               Integer minutosEtapaTecnica, boolean calificacionAutomatica,
                               // La remuneración viaja entera —con su marca de cuándo se
                               // tocó— para que la pantalla de configuración pinte el estado
                               // real sin pedir nada más.
                               RemuneracionDeLaVacante remuneracion,
                               Instant remuneracionActualizadaEn,
                               // El texto de la convocatoria, entero. Sin él, el lápiz de la
                               // lista abriría un formulario a medio llenar y guardar borraría
                               // lo que no viajó: el cuerpo del PUT es el formulario completo.
                               String descripcion, String proposito, String responsabilidades,
                               String requisitos, String modalidad, String horario,
                               String ubicacion, Integer plazas, Instant abreEn,
                               Instant cierraEn,
                               /**
                                * Cuánta gente sigue en carrera en esta vacante.
                                *
                                * <p>Es el número que el panel dice en voz alta antes de
                                * guardar —«avisaremos a N postulantes»—. Cuenta las
                                * postulaciones que no terminaron; ver
                                * {@code PostulacionesEnCarrera}.
                                */
                               int postulantesEnCarrera,
                               /**
                                * Cuándo se archivó, o vacío si sigue en la lista habitual.
                                *
                                * <p>Es lo que la vista de Archivadas pinta en cada fila y lo
                                * que el detalle enseña como «Archivada el …». No es un estado:
                                * la vacante sigue {@code CERRADA} (V59).
                                */
                               Instant archivadaEn,
                               /**
                                * Si quien pregunta puede editar ESTA vacante.
                                *
                                * <p>Viaja en la fila y no en un endpoint de permisos aparte: el
                                * alcance se decide por vacante —con {@code SUS_VACANTES} solo
                                * alcanzas las que diriges— y una respuesta general no podría
                                * contestarlo sin repetir aquí la regla del backend.
                                */
                               boolean puedeEditar,
                               /**
                                * Si quien pregunta puede archivar ESTA vacante.
                                *
                                * <p>Cierto con {@code cerrar_vacante}, alcance que llegue,
                                * estado {@code CERRADA} y sin archivar todavía. <b>No mira
                                * cuánta gente sigue en carrera</b>, y es deliberado: el icono
                                * tiene que aparecer para que el modal pueda explicar por qué
                                * no se puede confirmar. Esconderlo dejaría la pregunta sin
                                * respuesta y a los postulantes sin decidir.
                                */
                               boolean puedeArchivar,
                               /** Si quien pregunta puede devolverla a la lista habitual. */
                               boolean puedeDesarchivar,
                               /**
                                * Si quien pregunta puede eliminar ESTA vacante (V60).
                                *
                                * <p>Cierto con {@code eliminar_vacante} y alcance que llegue,
                                * <b>sea cual sea su estado</b>: un borrador mal creado, una
                                * publicada, una cerrada y una archivada se eliminan igual.
                                * Por eso no mira ni el estado ni cuánta gente sigue dentro —
                                * eso lo cuenta el modal, que para eso tiene
                                * {@code postulantesEnCarrera}.
                                *
                                * <p>Viaja en la fila como los otros dos y no en un endpoint
                                * de permisos aparte: el alcance se decide por vacante.
                                */
                               boolean puedeEliminar) {}

    /**
     * Cuántas vacantes archivadas hay, para el botón «Archivadas (N)» de la cabecera.
     *
     * <p>Se cuenta en el servidor y no contando filas en el navegador: el número tiene que
     * poder decirse sin traerse la lista entera, y las dos vistas —la habitual y la de
     * archivadas— se filtran también aquí.
     */
    public record ConteoDeArchivadas(long archivadas) {}

    /**
     * Cómo acabó un guardado de la vacante.
     *
     * <p>Las dos cosas que el panel necesita decir y no puede deducir: si de verdad cambió
     * algo —guardar el formulario sin tocar nada es el camino más normal del mundo, y
     * merece «no había cambios que guardar» y no un «guardado» que no guardó nada— y a
     * cuánta gente le llegó el aviso, que es el mismo contador que ya da el sueldo.
     */
    public record VacanteActualizadaResponse(boolean huboCambios, int postulantesAvisados) {}

    /**
     * Lo que la vacante dice sobre el dinero.
     *
     * <p>{@code tipo} es {@code OCULTA}, {@code FIJA} o {@code RANGO}. Con {@code FIJA} el
     * monto va en {@code min} y {@code max} se queda vacío; con {@code OCULTA} los tres
     * campos van vacíos. Las reglas las hace cumplir {@code Remuneracion}, y también la base
     * (V55), porque un formulario no es la única forma de escribir en una tabla.
     *
     * <p>⚠️ <b>Esconder el sueldo no es un detalle de presentación.</b> Decide si quien
     * postula está obligado a declarar cuánto quiere ganar: es el trato de la V55, y las dos
     * mitades se guardan en este mismo campo.
     */
    public record RemuneracionDeLaVacante(@NotBlank String tipo, BigDecimal min,
                                          BigDecimal max, String moneda) {

        public static final RemuneracionDeLaVacante OCULTA =
                new RemuneracionDeLaVacante("OCULTA", null, null, null);
    }

    /**
     * Cambiar el sueldo de una vacante ya creada, con el motivo de por qué.
     *
     * <p>El motivo es obligatorio y no es burocracia: este cambio le deja un aviso en el
     * portal a cada persona que tiene una postulación viva, y la auditoría tiene que poder
     * contestar «¿por qué le dijimos a cuarenta candidatos que el sueldo bajó?» con algo más
     * que una marca de tiempo. Desde la V58 no sale ningún correo.
     */
    public record ActualizarRemuneracion(@NotNull RemuneracionDeLaVacante remuneracion,
                                         @NotBlank(message = "Di por qué cambia el sueldo: "
                                                 + "se les avisa a los candidatos")
                                         String motivo) {}

    /** A cuánta gente le llegó el cambio, para que el panel lo diga en voz alta. */
    public record RemuneracionActualizadaResponse(String antes, String ahora,
                                                  int candidatosAvisados) {}

    /**
     * Retirar una vacante que no debió existir, con el porqué (V60).
     *
     * <p>El motivo es obligatorio y es lo único que se pide. No es burocracia: esta acción
     * cierra las postulaciones de otras personas, les deja un aviso y no se deshace desde el
     * panel. Quien abra la auditoría dentro de un año tiene que poder contestar «¿por qué
     * desapareció esta convocatoria y por qué se cerraron sus catorce procesos?» con algo más
     * que una marca de tiempo.
     *
     * <p>El mensaje del {@code @NotBlank} es el que ve quien manda el formulario vacío: la
     * validación contesta 400 aunque el botón del panel esté apagado, porque el panel es un
     * cliente más del API.
     */
    public record EliminarVacante(
            @NotBlank(message = "Di por qué se elimina la vacante: se cierran las "
                    + "postulaciones en carrera y queda en la auditoría")
            String motivo) {}

    /**
     * Cómo acabó la eliminación, en los dos números que el panel no puede deducir.
     *
     * <p><b>Cerradas y avisadas se cuentan por separado a propósito.</b> Casi siempre son el
     * mismo número, y entonces el panel dice «se cerraron N postulaciones y se les avisó». El
     * día que un aviso falle no son el mismo, y decir que a todos se les avisó sería mentir
     * sobre lo único que el candidato puede comprobar. Un aviso que falla no deshace la
     * eliminación: se anota y se sigue.
     */
    public record VacanteEliminadaResponse(int postulacionesCerradas, int postulantesAvisados) {}

    public record GuardarRequisito(@NotBlank String descripcion, @NotBlank String regla) {}

    public record RequisitoPanel(Long id, String descripcion, String regla, boolean esActivo) {}

    public record GuardarPuesto(String codigo, @NotBlank String nombre,
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
     *
     * <p>⚠️ <b>El suelo es de cinco minutos, no de uno.</b> Estos minutos convierten
     * cualquier prueba en cronometrada, así que un uno aquí es una prueba que el barrido
     * entrega sola sesenta segundos después de que el candidato la abra. Es el mismo suelo
     * que exige publicar una versión de plantilla.
     */
    public record ElegirInstrumentoTecnico(
            @NotBlank String instrumento,
            @Min(value = 5, message = "la etapa técnica dura al menos 5 minutos")
            Integer minutos) {}

    // Encender o apagar la evaluación del banco para esta vacante. Apagada, quien postula
    // no recibe cuestionario del banco: la prueba del puesto es su única evaluación.
    public record AplicarEvaluacion(@NotNull Boolean aplica) {}

    /**
     * Encender o apagar el recorrido automático de esta vacante.
     *
     * <p>Encendido, la postulación viaja sola hasta que termina la prueba del puesto y solo
     * entonces espera a una persona. Apagado, cada paso lo pide alguien, que es como
     * funcionaron todas las vacantes hasta la V53.
     */
    public record ActivarCalificacionAutomatica(@NotNull Boolean activa) {}

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
