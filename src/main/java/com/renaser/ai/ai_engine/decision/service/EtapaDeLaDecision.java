package com.renaser.ai.ai_engine.decision.service;

import com.renaser.ai.ai_engine.postulacion.service.DefinicionDeEtapa;

import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * La decisión como etapa del recorrido (V95). Es opcional y se puede mover como las demás.
 *
 * <p>Se entra por «por confirmar» y no por su primer momento: su turno del candidato existe solo
 * para el ámbar, cuando se le pide evidencia adicional. Al entrar no hay nada que crear.
 */
@Component
public class EtapaDeLaDecision implements DefinicionDeEtapa {

    public static final String CODIGO = "DECISION";

    @Override
    public String codigo() {
        return CODIGO;
    }

    @Override
    public Optional<String> momentoDeEntrada() {
        return Optional.of("POR_CONFIRMAR");
    }
}
