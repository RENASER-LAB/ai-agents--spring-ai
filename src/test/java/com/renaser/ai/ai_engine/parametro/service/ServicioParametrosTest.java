package com.renaser.ai.ai_engine.parametro.service;

import com.renaser.ai.ai_engine.parametro.entity.Parametro;
import com.renaser.ai.ai_engine.parametro.repository.ParametroRepository;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

/** El interruptor de un parámetro BOOLEANO (V70): solo «true» y «false» mandan. */
@ExtendWith(MockitoExtension.class)
@DisplayName("Los parámetros que son un interruptor")
class ServicioParametrosTest {

    @Mock private ParametroRepository repositorio;
    @InjectMocks private ServicioParametros parametros;

    private void vale(String valor) {
        when(repositorio.findByOrganizacionIdAndCodigo(1L, "recordatorios_activos"))
                .thenReturn(Optional.of(Parametro.builder().valor(valor).build()));
    }

    @Test
    @DisplayName("«false», con o sin mayúsculas y espacios, lo apaga")
    void falseApaga() {
        vale(" FALSE ");
        assertThat(parametros.booleano(1L, "recordatorios_activos", true)).isFalse();
    }

    @Test
    @DisplayName("«true» lo enciende")
    void trueEnciende() {
        vale("true");
        assertThat(parametros.booleano(1L, "recordatorios_activos", false)).isTrue();
    }

    @Test
    @DisplayName("un valor mal escrito o vacío vale lo de siempre, no apaga nada por su cuenta")
    void malEscritoValeLoDeSiempre() {
        vale("sí");
        assertThat(parametros.booleano(1L, "recordatorios_activos", true)).isTrue();
        vale(null);
        assertThat(parametros.booleano(1L, "recordatorios_activos", true)).isTrue();
    }

    @Test
    @DisplayName("sin la fila, lo de siempre")
    void sinFila() {
        when(repositorio.findByOrganizacionIdAndCodigo(1L, "recordatorios_activos"))
                .thenReturn(Optional.empty());
        assertThat(parametros.booleano(1L, "recordatorios_activos", true)).isTrue();
    }
}
