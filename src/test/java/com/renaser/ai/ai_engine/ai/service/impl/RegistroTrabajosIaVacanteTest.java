package com.renaser.ai.ai_engine.ai.service.impl;

import com.renaser.ai.ai_engine.ai.model.TrabajoIa;
import com.renaser.ai.ai_engine.ai.repository.TrabajoIaRepository;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Los trabajos que cuelgan de una vacante (el REDACTOR): un VIVO frena el doble clic,
 * pero lo TERMINADO no exime — regenerar es un trabajo nuevo, a propósito.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("El registro de trabajos por vacante")
class RegistroTrabajosIaVacanteTest {

    @Mock private TrabajoIaRepository trabajos;

    @InjectMocks
    private RegistroTrabajosIa registro;

    private void conUltimo(String estado) {
        when(trabajos.findFirstByReferenciaTablaAndReferenciaIdAndAgenteCodigoOrderByIdDesc(
                "vacante", 50L, "REDACTOR"))
                .thenReturn(estado == null ? Optional.empty()
                        : Optional.of(TrabajoIa.builder().id(9L).estado(estado).build()));
    }

    @Test
    @DisplayName("Uno vivo (incluido EN_ESPERA por tope) frena el siguiente: un clic, una llamada")
    void unVivoFrena() {
        for (String estado : new String[]{"PENDIENTE", "EN_CURSO", "EN_ESPERA"}) {
            conUltimo(estado);
            assertThat(registro.crearParaVacante(1L, "REDACTOR", 50L, "FINA"))
                    .as(estado).isEmpty();
        }
        verify(trabajos, never()).saveAndFlush(any());
    }

    @Test
    @DisplayName("Lo TERMINADO no exime: regenerar crea un trabajo nuevo")
    void loTerminadoNoExime() {
        conUltimo("TERMINADO");
        when(trabajos.saveAndFlush(any())).thenAnswer(i -> i.getArgument(0));

        Optional<TrabajoIa> creado = registro.crearParaVacante(1L, "REDACTOR", 50L, "FINA");

        assertThat(creado).isPresent();
        assertThat(creado.get().getReferenciaTabla()).isEqualTo("vacante");
        assertThat(creado.get().getReferenciaId()).isEqualTo(50L);
        assertThat(creado.get().getPostulacionId()).isNull();
        assertThat(creado.get().getEstado()).isEqualTo("PENDIENTE");
    }

    @Test
    @DisplayName("El primero de todos también se crea")
    void elPrimeroSeCrea() {
        conUltimo(null);
        when(trabajos.saveAndFlush(any())).thenAnswer(i -> i.getArgument(0));

        assertThat(registro.crearParaVacante(1L, "REDACTOR", 50L, "FINA")).isPresent();
    }
}
