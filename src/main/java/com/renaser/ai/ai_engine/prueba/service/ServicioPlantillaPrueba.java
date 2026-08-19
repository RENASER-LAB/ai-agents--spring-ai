package com.renaser.ai.ai_engine.prueba.service;

import com.renaser.ai.ai_engine.prueba.dto.DtosPlantillaPrueba.*;
import com.renaser.ai.ai_engine.seguridad.dto.ContextoUsuario;

import java.util.List;

/**
 * La administración de la prueba del puesto: plantillas, versiones, variantes del
 * cambio inesperado, el catálogo de preguntas, los entregables que se piden y la
 * rúbrica.
 *
 * <p>Mismo patrón que {@code ServicioBancoPreguntas}: una versión nace en {@code BORRADOR},
 * se arma con los métodos {@code agregar*}, y {@code publicarVersion} la congela — desde ese
 * momento es inmutable y quien ya la esté rindiendo queda atado a ella (RF-90).
 */
public interface ServicioPlantillaPrueba {

    Long crearPlantilla(ContextoUsuario quien, CrearPlantilla datos);
    List<PlantillaResponse> listarPlantillas(ContextoUsuario quien);

    Long crearVersion(ContextoUsuario quien, Long plantillaId, CrearVersion datos);
    void publicarVersion(ContextoUsuario quien, Long versionId);
    VersionCompleta verVersion(ContextoUsuario quien, Long versionId);

    Long agregarVariante(ContextoUsuario quien, Long versionId, CrearVariante datos);

    Long crearPreguntaCatalogo(ContextoUsuario quien, CrearPreguntaPrueba datos);
    List<PreguntaPruebaResponse> listarPreguntasCatalogo(String tipo);
    void elegirPregunta(ContextoUsuario quien, Long versionId, ElegirPregunta datos);

    Long agregarEntregableRequerido(ContextoUsuario quien, Long versionId, CrearEntregableRequerido datos);

    Long agregarCriterioRubrica(ContextoUsuario quien, Long versionId, CrearCriterioRubrica datos);
}
