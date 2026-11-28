package com.renaser.ai.ai_engine.postulacion.service;

import com.renaser.ai.ai_engine.vacante.entity.Vacante;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * La prueba del puesto como etapa del recorrido (V95).
 *
 * <p>Al entrar se crea lo que la vacante rinde —el intento de la prueba o el cuestionario técnico—
 * o se reutiliza el que ya tenía, con las reglas de {@link EntradaEtapaTecnica}. Sin prueba lista,
 * 409 y nada se mueve ni se avisa. El pase automático no mueve a nadie a una prueba vacía.
 */
@Component
@RequiredArgsConstructor
public class EtapaDeLaPrueba implements DefinicionDeEtapa {

    public static final String CODIGO = "PRUEBA_PUESTO";

    private final EntradaEtapaTecnica entradaTecnica;

    @Override
    public String codigo() {
        return CODIGO;
    }

    @Override
    public boolean listaParaEntrar(Vacante vacante) {
        return entradaTecnica.hayInstrumento(vacante);
    }

    /** Crea el intento: dos entradas a la vez no deben crear dos. */
    @Override
    public boolean bloqueaLaFila() {
        return true;
    }

    @Override
    public Destino alEntrar(Entrada entrada) {
        entradaTecnica.crearAlEntrar(entrada.postulacion(), entrada.vacante());
        return new Destino(entrada.destino(), entrada.motivo());
    }
}
