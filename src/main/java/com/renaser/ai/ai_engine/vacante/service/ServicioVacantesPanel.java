package com.renaser.ai.ai_engine.vacante.service;

import com.renaser.ai.ai_engine.vacante.dto.DtosVacante.*;
import com.renaser.ai.ai_engine.seguridad.dto.ContextoUsuario;

import java.util.List;

public interface ServicioVacantesPanel {

    Long crearPuesto(ContextoUsuario quien, GuardarPuesto datos);

    List<PuestoResponse> listarPuestos(ContextoUsuario quien);

    // Crear exige una solicitud ABIERTA (aprobada por Dirección). Nace en BORRADOR.
    Long crear(ContextoUsuario quien, GuardarVacante datos);

    /**
     * Guardar el formulario de una vacante que ya existe.
     *
     * <p>Compara campo a campo, audita lo que cambió y —si está publicada y cambió algo que
     * el candidato ve— deja <b>un único aviso</b> en la campana de cada postulación en
     * carrera. El sueldo entra en esta comparación: es lo que evita que cambiarlo junto al
     * horario mande dos noticias por un solo cambio.
     *
     * <p>Sin cambios no se guarda ni se avisa nada, y la respuesta lo dice.
     */
    VacanteActualizadaResponse editar(ContextoUsuario quien, Long id, GuardarVacante datos);

    /**
     * Las vacantes de la empresa, en una de sus dos listas.
     *
     * <p>{@code archivadas} falso —lo que pide {@code /admin}— trae solo las que nadie ha
     * archivado; cierto trae solo las archivadas, la última arriba. <b>El corte lo hace la
     * consulta</b>, no la pantalla: filtrado en el navegador, una archivada reaparecería en
     * cuanto alguien escribiera en el buscador o pasara de página.
     */
    List<VacantePanel> listar(ContextoUsuario quien, boolean archivadas);

    /** Cuántas archivadas hay, para el botón «Archivadas (N)» de la cabecera. */
    ConteoDeArchivadas contarArchivadas(ContextoUsuario quien);

    /**
     * Retira de la lista habitual una vacante cerrada, conservando su proceso.
     *
     * <p>Solo una {@code CERRADA} y <b>solo si no queda nadie en carrera</b>: archivarla con
     * gente esperando una decisión sería esconder de la mesa de trabajo a quien todavía
     * depende de ella. No cierra la vacante por su cuenta —eso es una decisión aparte, con su
     * motivo— ni toca ninguna postulación.
     *
     * <p>El estado y el conteo se vuelven a mirar aquí aunque el panel ya los haya enseñado:
     * entre abrir el modal y confirmarlo cabe una postulación nueva y cabe otro usuario.
     */
    void archivar(ContextoUsuario quien, Long id);

    /**
     * La devuelve a la lista habitual, {@code CERRADA} y con sus postulaciones intactas.
     *
     * <p>Desarchivar no reabre nada: el candidato ve su proceso exactamente igual antes y
     * después, y por eso no genera ningún aviso.
     */
    void desarchivar(ContextoUsuario quien, Long id);

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
     * Encender o apagar el recorrido automático de esta vacante.
     *
     * <p>Encendido, la postulación viaja sola: se le califica el currículum al postular —si
     * la vacante no lleva banco—, pasa sola a la etapa técnica cuando la calificación
     * termina, y su prueba se califica sola al entregarse. La primera persona que hace falta
     * es la que decide quién va a la simulación.
     *
     * <p>Solo cambia lo que pase de aquí en adelante: a quien ya está parado en una bandeja
     * no lo mueve, y para eso está el botón de calificar la tanda.
     */
    void activarCalificacionAutomatica(ContextoUsuario quien, Long id, boolean activa);

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

    /**
     * Cambia lo que esta vacante dice que paga, y avisa a quien ya postuló (V55).
     *
     * <p>Tiene verbo propio porque tiene pantalla propia: la tarjeta del detalle, para
     * cambiar SOLO el sueldo sin abrir el formulario entero. Quien abre el formulario lo
     * cambia con {@link #editar}, y entonces sale un único aviso con todo lo que tocó.
     *
     * <p>Cambiarlo en una vacante viva le deja un aviso en la campana del portal a cada
     * persona con una postulación abierta. Desde la V58 no sale ningún correo.
     *
     * <p>Se puede en BORRADOR y en PUBLICADA. En borrador no hay a quién avisar y es
     * simplemente rellenar el dato; en publicada es la noticia. Una vacante CERRADA no se
     * toca, como con todo lo demás.
     *
     * <p>⚠️ <b>Cambiar de OCULTA a FIJA/RANGO no va hacia atrás a pedir pretensiones.</b> El
     * trato se juzga con las reglas que había el día que cada uno postuló: quien envió su
     * candidatura cuando el sueldo estaba escondido no declaró el suyo, y eso es correcto —
     * el panel lo dice con esas palabras en lugar de dejar un hueco.
     */
    RemuneracionActualizadaResponse actualizarRemuneracion(ContextoUsuario quien, Long id,
                                                           ActualizarRemuneracion datos);
}
