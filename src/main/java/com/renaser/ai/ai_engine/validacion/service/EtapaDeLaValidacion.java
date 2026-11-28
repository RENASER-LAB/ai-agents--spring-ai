package com.renaser.ai.ai_engine.validacion.service;

import com.renaser.ai.ai_engine.postulacion.service.DefinicionDeEtapa;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * La validación práctica como etapa del recorrido (V95).
 *
 * <p>Al entrar se crea el periodo «por habilitar» o se reutiliza el que tenía, sin tocarlo. Si se
 * entra por «por habilitar», la persona va al paso que corresponde a ese periodo, con una
 * coletilla en el motivo que lo explica; con un movimiento a mano a otro paso de la Validación, ese
 * paso se respeta.
 */
@Component
@RequiredArgsConstructor
public class EtapaDeLaValidacion implements DefinicionDeEtapa {

    public static final String CODIGO = "VALIDACION";
    private static final String POR_HABILITAR = "VALIDACION_POR_HABILITAR";

    private final ServicioValidacion validacion;

    @Override
    public String codigo() {
        return CODIGO;
    }

    /** Crea el periodo: dos entradas a la vez no deben crear ni mover dos veces. */
    @Override
    public boolean bloqueaLaFila() {
        return true;
    }

    @Override
    public Destino alEntrar(Entrada entrada) {
        // El periodo nace en POR_HABILITAR: alguien tiene que decidir la modalidad —y, si es
        // trabajo real, registrar la figura contractual— antes de que empiece a correr.
        ServicioValidacion.Entrada periodo = validacion.crearAlEntrar(
                entrada.postulacion().getId(), entrada.postulacion().getOrganizacionId());
        if (!POR_HABILITAR.equals(entrada.destino())) {
            return new Destino(entrada.destino(), entrada.motivo());
        }
        return new Destino(periodo.paso(), periodo.motivoCon(entrada.motivo()));
    }
}
