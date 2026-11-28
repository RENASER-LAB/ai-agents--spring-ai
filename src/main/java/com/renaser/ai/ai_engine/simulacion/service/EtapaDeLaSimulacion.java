package com.renaser.ai.ai_engine.simulacion.service;

import com.renaser.ai.ai_engine.postulacion.entity.Postulacion;
import com.renaser.ai.ai_engine.postulacion.service.DefinicionDeEtapa;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * La simulación de trabajo como etapa del recorrido (V95).
 *
 * <p>Entrar no crea nada: la inscripción la elige el candidato. Lo que sí hace falta, una vez
 * dentro, es mirar si ya hay una sesión con cupo para su vacante, porque de eso depende si se
 * queda esperando o puede elegir ya.
 */
@Component
@RequiredArgsConstructor
public class EtapaDeLaSimulacion implements DefinicionDeEtapa {

    public static final String CODIGO = "SIMULACION";
    private static final String POR_HABILITAR = "SIMULACION_POR_HABILITAR";

    private final ServicioDisponibilidadSimulacion disponibilidad;

    @Override
    public String codigo() {
        return CODIGO;
    }

    @Override
    public void trasEntrar(Postulacion postulacion, String estado) {
        if (POR_HABILITAR.equals(estado)) {
            disponibilidad.recalcularVacante(postulacion.getOrganizacionId(), postulacion.getVacanteId());
        }
    }
}
