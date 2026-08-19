package com.renaser.ai.ai_engine.pesos.service;

import com.renaser.ai.ai_engine.pesos.dto.DtosPesos.*;
import com.renaser.ai.ai_engine.seguridad.dto.ContextoUsuario;

import java.util.List;

// Una versión de pesos junta cuatro tipos de fila (etapa, componente del Perfil Integral,
// dimensión y criterio del currículum) y se publica entera. Que cada grupo sume lo que
// debe sumar lo comprueba el código al publicar, no la base (ver docs/05 §17).
public interface ServicioPesos {

    Long crearVersion(ContextoUsuario quien, CrearVersionPesos datos);
    List<VersionPesosResponse> listar(ContextoUsuario quien);
    void publicarVersion(ContextoUsuario quien, Long id);

    void agregarPesoEtapa(ContextoUsuario quien, Long versionId, CrearPesoEtapa datos);
    void agregarPesoComponente(ContextoUsuario quien, Long versionId, CrearPesoComponente datos);
    void agregarPesoDimension(ContextoUsuario quien, Long versionId, CrearPesoDimension datos);
    void agregarPesoCriterio(ContextoUsuario quien, Long versionId, CrearPesoCriterio datos);

    List<PesoEtapaResponse> listarPesosEtapa(ContextoUsuario quien, Long versionId);
    List<PesoComponenteResponse> listarPesosComponente(ContextoUsuario quien, Long versionId);
    List<PesoDimensionResponse> listarPesosDimension(ContextoUsuario quien, Long versionId);
    List<PesoCriterioResponse> listarPesosCriterio(ContextoUsuario quien, Long versionId);
}
