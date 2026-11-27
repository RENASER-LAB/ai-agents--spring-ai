package com.renaser.ai.ai_engine.perfilintegral.service;

import com.renaser.ai.ai_engine.postulacion.service.DefinicionDeEtapa;

import org.springframework.stereotype.Component;

/**
 * El Perfil Integral como etapa del recorrido (V95). Va siempre primera ({@code etapa.fija_primera})
 * y se entra al postular, desde el portal: al entrar no hay nada que crear.
 */
@Component
public class EtapaDelPerfilIntegral implements DefinicionDeEtapa {

    public static final String CODIGO = "PERFIL_INTEGRAL";

    @Override
    public String codigo() {
        return CODIGO;
    }
}
