package com.renaser.ai.ai_engine.vacante.service;

import com.renaser.ai.ai_engine.vacante.dto.DtosVacante.*;
import com.renaser.ai.ai_engine.seguridad.dto.ContextoUsuario;

import java.util.List;

public interface ServicioVacantesPanel {

    Long crearPuesto(ContextoUsuario quien, GuardarPuesto datos);

    List<PuestoResponse> listarPuestos(ContextoUsuario quien);

    // Crear exige una solicitud ABIERTA (aprobada por Dirección). Nace en BORRADOR.
    Long crear(ContextoUsuario quien, GuardarVacante datos);

    void editar(ContextoUsuario quien, Long id, GuardarVacante datos);

    List<VacantePanel> listar(ContextoUsuario quien);

    VacantePanel detalle(ContextoUsuario quien, Long id);

    List<RequisitoPanel> requisitos(ContextoUsuario quien, Long vacanteId);

    Long agregarRequisito(ContextoUsuario quien, Long vacanteId, GuardarRequisito datos);

    void desactivarRequisito(ContextoUsuario quien, Long vacanteId, Long requisitoId);

    void publicar(ContextoUsuario quien, Long id);

    /** Qué evaluación responderá quien postule a esta vacante. Obligatorio antes de publicar. */
    void asignarPlantillaEvaluacion(ContextoUsuario quien, Long id, Long plantillaEvaluacionId);

    /** Qué prueba del puesto rendirá quien llegue a esa etapa. Obligatorio antes de publicar. */
    void asignarPlantillaPrueba(ContextoUsuario quien, Long id, Long versionPlantillaPruebaId);

    /**
     * Qué se rinde en la etapa técnica de esta vacante, y en cuántos minutos.
     *
     * <p>{@code PLANTILLA} = la prueba del puesto de siempre · {@code CUESTIONARIO_TECNICO} =
     * el cuestionario CAZATALENTOS aprobado para esta vacante. Uno de los dos, nunca los dos:
     * publicar exige tener listo el que se haya elegido. Con {@code minutos} vacío rige el
     * tiempo del instrumento elegido.
     */
    void elegirInstrumentoTecnico(ContextoUsuario quien, Long id, String instrumento,
                                  Integer minutos);

    /**
     * Encender o apagar la evaluación del banco para esta vacante.
     *
     * <p>Apagada, quien postule no recibe cuestionario: su postulación va directa a la
     * bandeja del equipo y la prueba del puesto es su única evaluación. Solo afecta a las
     * postulaciones futuras: quien ya tiene su evaluación creada la conserva.
     */
    void definirAplicacionEvaluacion(ContextoUsuario quien, Long id, boolean aplica);

    /**
     * Qué versión de pesos rige la decisión de esta vacante.
     *
     * <p>Al crearse, la vacante toma la última publicada; esto permite apuntarla a otra
     * —una vacante sin banco pone todo el peso en la prueba—. No recalcula nada hacia
     * atrás: las notas ya guardadas conservan la versión con la que se calcularon.
     */
    void asignarVersionPesos(ContextoUsuario quien, Long id, Long versionPesosId);

    /**
     * Los textos de correo propios de esta vacante. Vacío quiere decir «los de siempre».
     */
    List<PlantillaCorreoDeVacante> plantillasCorreo(ContextoUsuario quien, Long vacanteId);

    /**
     * Hace que ESTA vacante mande otro texto en lugar del aviso que le tocaba.
     *
     * <p>Una plantilla de correo es una por organización: hasta que existió esto, cambiar el
     * texto de una convocatoria se lo cambiaba a todas. Se vuelve al de siempre con
     * {@link #quitarPlantillaCorreo}.
     */
    void asignarPlantillaCorreo(ContextoUsuario quien, Long vacanteId, AsignarPlantillaCorreo datos);

    /** Devuelve ese aviso al texto por defecto. */
    void quitarPlantillaCorreo(ContextoUsuario quien, Long vacanteId, String avisoCodigo);

    /**
     * Fija cuándo cierra la prueba de esta vacante, para todos.
     *
     * <p>El plazo de la plantilla se cuenta en días y desde que cada candidato entra, así que
     * no sirve para decir «esta convocatoria cierra el domingo». Esto lo dice, y además
     * <b>mueve los intentos que ya estén abiertos</b>: si no, la fecha solo valdría para
     * quien entrara después y la tanda quedaría partida en dos sin que nada lo explicara.
     *
     * <p><b>A quien tenga fecha propia no se le toca.</b> Es la persona a la que alguien le
     * dio más horas a mano; perdérsela al mover la de la convocatoria sería silencioso.
     *
     * <p>Con {@code cierraEn} vacío se quita la fecha: los intentos que aún no han empezado
     * vuelven a contar los días de la plantilla.
     */
    CierrePruebaResponse definirCierrePrueba(ContextoUsuario quien, Long vacanteId,
                                             DefinirCierrePrueba datos);

    // Cerrar detiene postulaciones nuevas; las que están en marcha se deciden una a una
    void cerrar(ContextoUsuario quien, Long id, String motivo);
}
