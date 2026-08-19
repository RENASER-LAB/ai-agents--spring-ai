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

    // Cerrar detiene postulaciones nuevas; las que están en marcha se deciden una a una
    void cerrar(ContextoUsuario quien, Long id, String motivo);
}
